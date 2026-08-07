# 使用 Rules 制作对话

本节使用 `data/campaign/rules.csv` 制作一个由规则表驱动的对话框。规则会完成触发匹配、条件判断与结果执行，并为 [C14-S3] 的自定义命令和 [C14-S4] 的内存状态提供基础。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

* 解释 `rules.csv` 七列各自的归属与内部格式，并区分哪些列被加载器读取 (`notes` 不读)；
* 解释规则的注册与合并方式：路径固定为 `data/campaign/rules.csv`，同文件重复 id 在加载期崩溃、跨文件重复 id 先到先得静默跳过、`#` 开头的行是注释行；
* 解释匹配流程：按 `trigger` 分组 → 逐条求值条件 (首个失败短路)→ `FireBest` 分数竞争 (同分随机、`score:` 控制权重、跳过当前规则 id)→`FireAll` 执行全部命中规则 (可被 `FireAllIntercept` 整体拦截)；
* 解释条件表达式的语法与判真规则：`$memkey.name` 变量解析 (无记忆键回落 `local`)、比较操作符、`=` 赋值 (只能出现在脚本列)、`!` 取反、行尾 `score:`、命令调用 (按 `ruleCommandPackages` 找类)，以及 `null` 为假、仅 `"true"` (忽略大小写) 与 `Boolean.TRUE` 为真的规则；
* 解释结果三件套的执行方式：`script` 按顺序执行 (选项型命令由 `FireBest` / `FireAll` 提前执行)、`text` 按 `\nOR\n` 拆成变体随机取一条、`options` 的 `id:text` 与 `order:id:text` 两种格式，以及 `performTokenReplacement` 的文本替换链；
* 创建含 `GearIn_rules_intro_*` 规则的 `data/campaign/rules.csv` 与规则驱动对话入口 `GearInRulesIntro`，并验证对话完整流程；
* 从症状定位故障，沿数据文件 → 注册入口 → 运行使用方 → 可观察结果四个位置复核。

## 前置知识

* [C6-S2] 的 `onGameLoad` 生命周期与注册时机；
* [C9-S1] 的 `addScript` 与持久数据一次性判断；
* [C9-S6] 的交互对话框生命周期与 `showInteractionDialog`；
* [C14-S3] 的 `BaseCommandPlugin` 职责与原版命令；
* [C14-S4] 的 `MemoryAPI` 过期、持久化与记忆键分组解析。

## 术语

本节先固定规则系统的六个核心词；API 类名保留源码大小写。其中规则命令的实现见 [C14-S3]，Rules 内存的完整语义见 [C14-S4]：

* **规则 (rule)**：API 名保留 `RuleAPI`。`rules.csv` 中的一行就是一条规则：id、trigger、条件列表、脚本列表、文本变体列表、选项列表。
* **触发器 (trigger)**：规则的字符串分组 key (`trigger` 列)。同一触发器的规则构成一个候选池；对话插件以触发器名为入口发起匹配。
* **条件 (condition)**：API 名保留 `ExpressionAPI`。规则命中前必须全部为真的表达式行 (`conditions` 列)；条件也在匹配阶段被求值。
* **结果 (result)**：规则命中后发生的事，由三列共同描述 —— `script` (脚本表达式)、`text` (对话文本)、`options` (对话选项)。
* **规则命令 (rule command)**：类名保留 `BaseCommandPlugin`；本节把它们当作内置命令使用，自定义命令的编写与注册见 [C14-S3]。
* **Rules 内存 (memory)**：API 名保留 `MemoryAPI`；本节只使用记忆键语法，内存生命周期见 [C14-S4]。

## 梳理 Rules 数据关系

### 数据入口与列语义

词义固定后，从数据入口开始，沿加载、匹配、执行三条链看机制。规则表只有一份来源：**所有** `data/campaign/rules.csv`(原版 + 全部启用 MOD 各自一份，加载时合并)。原版表头为七列 (`Starsector\starsector-core\data\campaign\rules.csv` 首行；加载器按列名读取，列顺序即表头顺序)：

列

加载方式

语义

`id`

`getString("id")`

规则唯一标识；id 以 `#` 开头 → 整行跳过；空 id → 行跳过

`trigger`

`getString("trigger")`

规则所属触发器名 (分组 key)

`conditions`

`getString("conditions")`：去 `\r`、trim、按 `\n` 拆行，每行一条条件表达式

匹配阶段必须全部为真

`script`

`getString("script")`：去 `\r`、trim、按 `\n` 拆行，每行一条脚本表达式

命中后按顺序执行的结果

`text`

`optString("text", null)`：去尾部空白、按 `\nOR\n` 拆段

文本变体列表；`pickText()` 随机取一条展示

`options`

`getString("options")`：去 `\r`、按 `\n` 拆行

选项列表：每行 `id:text` 或 `order:id:text`

`notes`

**不读取** (加载流程没有该列的取值入口)

备注，供人阅读

`conditions` / `script` / `options` 中 `#` 开头的行是注释行；空行跳过。

加载期还会对只出现一次的变量向**标准输出**打印 `WARNING: variable [...] only used once; rule id: [...]`(`System.out.println`，不进 `starsector.log`，且 `tag:` / `$market.mc:` 变量排除)。

> 规则系统是纯数据驱动的：MOD 侧不需要任何注册文件，把行写进自己的 `data/campaign/rules.csv` 就会被加载器自动合并 —— 原版大量对话、选项与事件文本都是同一套机制的产物。

> 这也是为什么确认规则是否真的被读进来，要看加载日志而不是找报错。

### 加载链与合并语义

行与列的含义定了，下一步是这些行如何进入游戏。

1. **加载时机**：CSV spec 加载期，`loadStarmap` 末尾调用 `Rules.loadRules`—— 一次游戏启动只加载一次，规则表不属于存档数据。
2. **合并读取**：文本读取工具的 `readCSV(["id"], "data/campaign/rules.csv", true, true)`：
    *   打开原版与所有启用 MOD 的同路径资源；某个 MOD 把该路径列入 `fullOverrides` 时整文件替换；
    *   **同文件**内重复 id → `RuntimeException("Duplicate key [...] while loading [...]")`，加载崩溃；
    *   **跨文件**重复 id → 按资源处理顺序先到先得，后处理文件中的同 id 行**静默跳过** (无任何报错)；
    *   每份文件读取前过 `replaceSmartQuotes`(`readCSV` 第 4 参为 true，spec 加载期执行 `replaceSmartQuotes`)。
3. **逐行解析与严格校验** (规则文件加载流程的校验，错误均为加载期 `RuntimeException`，汇总见规则强度节)。
4. **注册入口**：MOD 侧不需要任何注册文件 —— 把行写进自己的 `data/campaign/rules.csv`，加载器自动合并；`starsector.log` 逐条打印 `Loading rule: <id>` 与 `Loading CSV data from [...]`，是确认规则是否真的被读进来的第一手证明。

### 匹配链

行被加载后，下一步是匹配：对话插件发起一次触发时，哪些规则会被选中。匹配入口有两个公开面：`RulesAPI.getAllMatching(currentRule, trigger, dialog, memoryMap)` 与 `RulesAPI.getBestMatching(...)`，都在 `com.fs.starfarer.api.campaign.rules.RulesAPI` 上；`SectorAPI.getRules()` 可以拿到 `RulesAPI`，生涯引擎内是包装实现。

规则引擎的匹配流程如下：

1. `getRulesForTrigger(trigger)` 按触发器名取候选池 (未知触发器用到时才建空列表)。
2. 逐条规则：跳过 id 等于当前规则 id(currentRule) 的规则，防止自再入；逐条求值条件 (表达式判真)，首个失败即短路。
3. `getRulesFor`：返回全部条件通过者 —— 这是 `FireAll` 的候选集。
4. `getBestMatchingRule`：通过条件分数求和 (每条条件默认 1 分，`score:` 可改) + 规则 `scoreBonus` (CSV 无此列，恒 0) → 只保留最高分候选 → 同分用 `WeightedRandomPicker` 随机取一条 → 返回单条或 `null` (无匹配)。
5. `RulesAPI.setRandomForNextRulePick(random)` 只影响下一次 pick，用后即清 —— 跨多次匹配复用随机源会改变文本选择语义。

### 条件表达式语法

匹配靠逐条求值条件，条件与结果都写成表达式。每条条件/脚本行是一个表达式。

token 化规则 (`Misc.tokenize`，`com.fs.starfarer.api.util.Misc`)：以 `$` 开头 = 变量；引号包裹 = 字面量 (含空格/特殊字符)；`\` 转义；操作符独立成 token。

操作符全集：`=`(赋值)、`==`、`!=`、`>`、`>=`、`<`、`<=`、`!`(取反)、`++`、`--`。

形式

示例

语义与归属

纯变量 (判真)

`$tag:warning_beacon`

`IS` 语义：记忆值 null → false；字符串 `"true"`(去空白、忽略大小写) 或 `Boolean.TRUE` → true；其余 (含 `"1"`、`"false"`)→ false

比较

`$option == GearIn_rules_intro_leave`、`$entity.relativeStrength >= 0`、`$faction.id == independent`

`==`/`!=`/`<`/`<=`/`>`/`>=`；字符串比较大小写敏感；数值经 `Memory.parseFloat`；`==` 双侧都 null → true、null 与字面量 `"null"` 互等、`0` 与 `"false"`/`"null"` 互等

取反

`!$var`、`!Command args`

变量判反 / 命令结果取反；`!` 的运算对象必须是变量或命令

赋值

`$menuState = fleetConvMain 0`

`=` 只能出现在**脚本列** (条件列出现 → 加载崩溃)；第三 token 为过期天数 (可选，`0` = 立即过期语义；过期天数的完整语义属 [C14-S4])

增减

`$GearIn_rules_intro_visits++`、`$x--`

浮点自增/自减；第三 token 为过期天数 (原版脚本列含 595 处 `++`、16 处 `--`)

命令

`RollProbability 0.5`、`FireAll PopulateOptions`、`SetShortcut beaconLeave "ESCAPE"`

首 token 非变量即命令：按 `ruleCommandPackages`(`data/config/settings.json`，5 个包，默认回退 `com.fs.starfarer.api.impl.campaign.rulecmd`) 逐个 `Class.forName` 定位；找不到 → 加载崩溃 `Command [...] not found in packages`；命令执行异常在对话内显示红字段落 (`<异常类>: <消息>`)，dialog 为 null 时重抛

分数

`$temporarilyIgnoreYou score:10`

行尾字面量 `score:N` 改写该条件分数 (默认 1)，参与 `FireBest` 竞争

> 判真很严格：只有字符串 `"true"`(忽略大小写) 与 `Boolean.TRUE` 为真，null、`"1"`、`"false"` 一律为假；比较操作符对字符串大小写敏感。

> 把 `"1"` / `"yes"` 当真值、把记忆键拼错 (如少了 `$tag:` 前缀)，是条件恒假最常见的两个原因。

**变量解析** (`Misc.Token.getVarNameAndMemory`)：`$memkey.name` → 从 `memoryMap` 取 `memkey` 记忆，再读记忆中的 `$name` 键；token 无 `.`(如 `$option`、`$tag:x`)→ 记忆键为 null → 回落 `local` 记忆；两者都找不到 → `RuleException("No memory found for keys: ...")`。

对话插件向 `memoryMap` 装入哪些记忆，见下文使用方完整流程的表。`$tag:`、`$mc:`、`$friendlyToPlayer` 等 facts 键由 `CoreCampaignPluginImpl` 的 `updateEntityFacts` / `updateMarketFacts` / `updatePersonFacts` / `updateFactionFacts` 钩子写入 (`com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`)；使用 `$tag:xxx` 条件的前提是目标实体确实带该 tag。

### 结果执行链

规则命中后，结果由内置命令 `FireBest` / `FireAll` (`com.fs.starfarer.api.impl.campaign.rulecmd` 包) 执行，两条路径的对比如下：

步骤

`FireBest`

`FireAll`

触发名

`params[0]`(变量则取记忆值)；`params[1]` = keepOptions

同左；另在实体记忆写 `$fireAllTrigger`(expire 0)，并先 `FireBest` 触发 `FireAllIntercept`—— 有命中规则即整体拦截

选规则

`getBestMatching`(分数竞争，null → 返回 `false`)

`getAllMatching`(全部通过者，空 → 返回 `false`)

选项

收集 `rule.getOptions()` + 脚本中 `doesCommandAddOptions()==true` 的表达式 → 按 `order`(选项 `order` 字段 / `getOptionOrder`) 排序 → 非 keepOptions 时清空选项面板 → 逐个添加 (选项文本先做 token 替换)

全部命中规则的选项合并、排序、清空后添加

文本

`addText`：`rule.pickText()` 随机取一条文本变体 → `performTokenReplacement` → `textPanel.addParagraph`

每条命中规则各执行一次 `addText`

脚本

`rule.runScript(dialog, memoryMap)`

每条命中规则各执行一次

`Rule.runScript` 按声明顺序执行脚本表达式，跳过选项型命令 (`doesCommandAddOptions()==true` 的由 `FireBest` / `FireAll` 提前执行)—— 所以选项型命令即使写在脚本列里，也不会在脚本阶段重复执行。

**文本 token 替换** (`SectorAPI.getRules().performTokenReplacement`)：先汇总所有注册的 `RuleTokenReplacementGeneratorPlugin` 生成的替换表 (默认注册 `CoreRuleTokenReplacementGeneratorImpl`，提供 `$playerName` / `$Playername` / `$marketName` / `$entityName` / `$faction` / `$heOrShe` / `$personRank` / `$personName` 等，`com.fs.starfarer.api.impl.campaign.CoreRuleTokenReplacementGeneratorImpl`)，替换键按长度降序 `replaceAll`；再对 `memoryMap` 各记忆的键做 `$memkey.key` / `$key` 替换 (`Misc.replaceTokensFromMemory`，仅 String/Boolean/Float/Integer 值)。

rulecmd 参数内需要替换时用 `Misc.Token.getStringWithTokenReplacement`。

### 使用方完整流程

机制到这里已经齐全，最后看实际的使用方 —— 对话插件如何把规则变成对话。

使用方

位置

触发方式

规则驱动对话插件 `RuleBasedInteractionDialogPluginImpl`

`com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`

构造器指定初始 trigger(默认 `OpenInteractionDialog`)；`init` 组装 `memoryMap`(`local` = 交互目标记忆或活动人物记忆、`faction`、`global`、`player`、`market`、`sourceMarket`、活动人物时的 `personFaction`/`entity`)→ `fireBest(initialTrigger)` → 无任何选项时加 failsafe `Leave`；玩家点选项 → 本地记忆写 `$option`(expire 0)→ `fireBest("DialogOptionSelected")` → 无匹配规则时文本报 `ERROR: no rule found for option [...]` 并加 failsafe `Exit dialog`

舰队遭遇对话 `FleetInteractionDialogPluginImpl`

`com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`

`reinit` 用 `getBestMatching(null, "BeginFleetEncounter", ...)` 决定是否给 "Continue" 选项；开通讯 (`OPEN_COMM`) 时内嵌 `RuleBasedInteractionDialogPluginImpl` 并 `fireBest("OpenCommLink")`，无匹配则报 "only get static"

非对话面板 (规则记忆组装)

非对话文本面板 (内部实现)

组装简化 memoryMap 后 `FireBest.fire(...)` 面板文本 trigger(使用例：已选阵营信息面板的 `RelationshipLevelDesc`)

原版 `rules.csv`：7 列、9749 条非注释规则、800 个不同 trigger，大族为：

* `DialogOptionSelected`(6050)；
* `PopulateOptions` (334)；
* `PickGreeting`(199)；
* `OpenCommLink`(183)；
* `OpenInteractionDialog`(121)；
* `BeginFleetEncounter`(61)；
* `MarketPostDock`(32)；
* `TradePanelFlavorText`(23)。

`DialogOptionSelected` 全部 6050 条都带条件 (无空条件行)：原版选项必须回流 `$option ==` 条件。

## 规则强度

把引擎的行为边界汇总成一张核对表：必须项违反后加载中止，事实项描述运行时的真实行为；每行都附上违反后的可观察结果。

规则

强度

违反后的可观察结果

`conditions` 列禁止出现 `=`(赋值) 操作符 (报错文案："rule \[...\] has = ('assignment') operator in its conditions, this is most likely an error")

必须

资源加载期 `RuntimeException`，游戏启动中止

`script` 列禁止出现 `==` 操作符 (加载期校验)

必须

资源加载期 `RuntimeException`，游戏启动中止

选项 id 禁止以 `$` 开头 (报错文案："Option id starts with $ for rule \[...\]")

必须

资源加载期 `RuntimeException`，游戏启动中止

条件/脚本引用的命令类必须能在 `ruleCommandPackages`(`data/config/settings.json`) 中找到

必须

资源加载期 `RuntimeException`(`Command [...] not found in packages:`)，游戏启动中止

同一份 `rules.csv` 内规则 id 不得重复 (文本读取工具同文件去重逻辑："Duplicate key \[...\]")

必须

资源加载期 `RuntimeException`，游戏启动中止

跨文件重复规则 id：后处理文件的同 id 行静默跳过 (先到先得)

事实

规则不触发且无任何报错；`starsector.log` 中看不到该 id 的 `Loading rule:` 行

文本变体分隔符必须独占一行，且严格为 `\nOR\n`(变体按该分隔符切分)

必须

分隔符不匹配时多段文本被当作单条变体整段展示

`options` 每行必须是 `id:text`(2 段) 或 `order:id:text`(3 段)，按**所有**冒号切分；文本内不得再有冒号

必须

3 段且首段非数字 → `NumberFormatException` 加载崩溃；3 段以上 → 文本静默截断 (只保留第 3 段)

选项 id 必须与 `DialogOptionSelected` 规则里的 `$option == <id>` 逐字一致 (含大小写；表达式比较大小写敏感)

必须

点选后无规则匹配：文本面板出现 `ERROR: no rule found for option <id>` + failsafe 选项 (`RuleBasedInteractionDialogPluginImpl`)

`FireAll` 之前会先匹配 `FireAllIntercept` 触发器 (`FireAll`)

事实

拦截规则命中时本次 `FireAll` 被整体跳过 (原版仅 1 条拦截规则，条件是 `$fireAllTrigger == PopulateOptions` 等，不影响自定义 trigger)

条件列会执行命令：带副作用的 rulecmd 在**匹配阶段**就写状态 (条件求值路径会直接执行命令)

事实

每次匹配 (包括候选失败前已通过的条件) 都执行副作用；写状态应放脚本列

同分规则随机挑选

事实

期望确定性结果时必须用 `score:` 拉开分数差

`setRandomForNextRulePick` 只影响下一次 pick

事实

跨多次匹配复用随机源会改变文本选择语义

## 组装 Rules 对话实现

机制与边界都清楚了，现在把它们组合成最小可运行的一套：一份规则表加一个使用入口，覆盖从加载到对话的完整流程。在既有 MOD 工程布局之上新增：一份 `data/campaign/rules.csv`(含本节拥有的 `GearIn_rules_intro_*` 规则，与其它 MOD 的同路径文件自动合并) 与一个 Java 使用入口类。

不新增其它资源、不修改任何既有文件：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── rules.csv                     # 本节新增：GearIn_rules_intro_* 规则（本节示例所有权）
├── src\
│   └── data\scripts\gearin\rules\
│       └── GearInRulesIntro             # 本节新增：Java 消费入口（归本节所有）
└── jars\
```

### 配置 Rules 数据

新建 MOD 侧的 `data\campaign\rules.csv`，只写本节拥有的行。命名沿用语义约定：规则 id 为 `GearIn_rules_intro_*`、自定义触发器为 `GearInRulesIntro*`、Java 类 `GearInRulesIntro` 位于包 `data.scripts.gearin.rules`：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_intro_welcome,GearInRulesIntro,,"ShowDefaultVisual
SetShortcut GearIn_rules_intro_leave ""ESCAPE""","欢迎，$playerName。这个对话框完全由 data/campaign/rules.csv 驱动：列、匹配、条件、结果与文本。
OR
再次见到您，$playerName。规则系统按独占一行的 OR 把 text 列拆成多条变体，每次随机取一条。","GearIn_rules_intro_open:查看详情
5:GearIn_rules_intro_leave:结束","本节示例所有权 GearIn_rules_intro_*"
GearIn_rules_intro_add_a,GearInRulesIntroAdd,,,FireAll 附加文本：条件为空，必然追加。,,
GearIn_rules_intro_add_b,GearInRulesIntroAdd,RollProbability 0.0,,FireAll 附加文本：命令条件恒假（RollProbability 0.0），永不追加。,,
GearIn_rules_intro_open,DialogOptionSelected,$option == GearIn_rules_intro_open,"$GearIn_rules_intro_opens = 1 0
FireAll GearInRulesIntroAdd
FireBest GearInRulesIntroDetail",,,
GearIn_rules_intro_detail_low,GearInRulesIntroDetail,$GearIn_rules_intro_opens >= 1,,低分规则：条件总分 1，FireBest 竞争失败。,,
GearIn_rules_intro_detail_high,GearInRulesIntroDetail,"$GearIn_rules_intro_opens >= 1
RollProbability 1.0
!$GearIn_rules_intro_skip score:100","$GearIn_rules_intro_last = detail 0",高分规则：条件总分 102（1 + 1 + 100），FireBest 必然选中。,GearIn_rules_intro_leave:结束,
GearIn_rules_intro_leave,DialogOptionSelected,$option == GearIn_rules_intro_leave,DismissDialog,,,
```

> 这是跨单元共享文件：本节只拥有 `GearIn_rules_intro_*` 前缀。禁止改写原版或其它 MOD 的既有规则行，禁止与既有 id 重复 —— 跨文件重复 id 先到先得，本 MOD 的行会被静默跳过且无任何报错。

对应关系：

* **welcome** (trigger `GearInRulesIntro`)：初始对话。
    *   `text` 两段变体用独占一行的 `OR` 分隔，`pickText()` 每次随机取一条；文本里的 `$playerName` 由 token 替换链换成本档人物名。
    *   `script` 先 `ShowDefaultVisual`(显示交互目标默认视觉)，再 `SetShortcut` 把 ESC 绑定到 `GearIn_rules_intro_leave` 选项。
    *   `options` 演示两种格式：`id:text`(order 0) 与 `5:id:text`(order 5)，`FireBest` 按 order 升序排。
* **add\_a / add\_b** (trigger `GearInRulesIntroAdd`)：演示 `FireAll` 与命令条件。`RollProbability 0.0` 恒假 (`f < 0.0` 不可能为真)，`RollProbability 1.0` 恒真 —— 不引入随机性。
* **open** (trigger `DialogOptionSelected`)：演示 `$option` 回流。脚本做三件事：`$GearIn_rules_intro_opens = 1 0`(赋值 + 0 天过期)、`FireAll GearInRulesIntroAdd`(追加 add\_a 文本)、`FireBest GearInRulesIntroDetail`(进入分数竞争)。
* **detail\_low / detail\_high** (trigger `GearInRulesIntroDetail`)：演示 `FireBest` 分数竞争。
    *   两规则条件都成立，但 high 总分 102(两条 1 分 + `!$GearIn_rules_intro_skip score:100`) 恒大于 low 的 1 分，结果确定性选中 high。
    *   high 的 `script` 演示赋值并携带选项。
* **leave** (trigger `DialogOptionSelected`)：`$option == GearIn_rules_intro_leave` → 内置命令 `DismissDialog` 关闭对话。
* 内置命令 (`ShowDefaultVisual` / `SetShortcut` / `DismissDialog` / `FireBest` / `FireAll` / `RollProbability`) 都是 `BaseCommandPlugin` 子类 (`com.fs.starfarer.api.impl.campaign.rulecmd`)，本节只使用不实现；自定义规则命令见 [C14-S3]。
* 记忆键 `$option` / `$GearIn_rules_intro_opens` / `$GearIn_rules_intro_skip` / `$GearIn_rules_intro_last` 的过期、持久化与维护语义见 [C14-S4]；本节只保证演示流程内的取值逻辑。
* `rules.csv` 是跨单元共享文件：本节只拥有 `GearIn_rules_intro_*` 前缀；其他页面使用各自前缀追加行，同前缀内 id 唯一，跨文件重复 id 先到先得的纪律见规则强度节。

### 编写 Rules 命令

```java
package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 完整最小例：rules.csv 数据驱动对话的 Java 消费入口。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK
 * 一次性门控（沿用 C9-S1 / C9-S5 的既有模式，每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 用 RuleBasedInteractionDialogPluginImpl 打开一个初始
 * trigger 为 GearInRulesIntro 的对话；对话的文本、脚本与选项全部来自 data/campaign/rules.csv
 * 中本节拥有的 GearIn_rules_intro_* 规则 ——Java 侧不写任何文本与选项。
 * 验证闭环：注册入口（本类 + onGameLoad 门控）→ 数据文件（rules.csv）→ 运行消费者
 * （RuleBasedInteractionDialogPluginImpl → Rules 引擎）→ 可观察结果（对话文本与日志）。
 */
public class GearInRulesIntro implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_RulesIntro";

    private static final Logger LOG = Global.getLogger(GearInRulesIntro.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInRulesIntro() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl("GearInRulesIntro"),
                Global.getSector().getPlayerFleet());
        LOG.info("GearIn rules intro dialog shown=" + shown
                + " (initial trigger GearInRulesIntro; 对话内容全部来自 rules.csv)");
    }
}
```

对应关系：

* `implements EveryFrameScript`：沿用一次性演示模式 ——`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除。
* `REGISTER_MARK`：persistent data 注册判断键 (`"GearIn_RulesIntro"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
* `new RuleBasedInteractionDialogPluginImpl("GearInRulesIntro")`：自定义初始 trigger(原版默认 `OpenInteractionDialog` 有 121 条规则，会与演示规则混战；自定义 trigger 保证候选池只有本节规则，`FleetInteractionDialogPluginImpl` / 非对话面板文本触发同样使用自定义 trigger)。
* `showInteractionDialog(plugin, playerFleet)`：交互目标非 null(`init` 里 `getInteractionTarget().getMemory()` 依赖它)；返回值记入日志 (对话框打开失败时用于定位的防御写法)。
* 无匹配规则时插件会加 failsafe `Leave` (`RuleBasedInteractionDialogPluginImpl`) —— 整套规则都没加载时，对话框依然恭候您离开，除此之外什么也不显示。它是规则没加载或 trigger 拼错时最直接的可观察信号 (见 "对话只显示 failsafe `Leave`" 故障条目)。

调用点 (工程片段 —— 该方法属于插件生命周期主题所持有的类，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin（C6-S2 所有）onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInRulesIntro.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInRulesIntro.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInRulesIntro());
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/rules/GearInRulesIntro.class`(可用 `jar tf` 核对)；`data\campaign\rules.csv` 是 MOD 数据文件，随 MOD 目录被加载器读取 (无需进 jar，规则行放 jar 外与放 jar 内等效 —— 资源加载器都读)。

注意示例路径 `Starsector\mods\GearIndustry` 是教程虚构路径，页面内路径互相自洽即可。

## 验证方案

前置数据：既有 MOD 工程布局与插件生命周期注册回调 ([C6-S1]、[C6-S2] 建立)、本节的 `data\campaign\rules.csv` 规则表与 `GearInRulesIntro` 使用入口。验证入口为启动器与新建生涯，观察位置是 `starsector.log`、启动器控制台标准输出与对话界面。

1. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/rules/GearInRulesIntro.class`。
2. **规则注册**：启动游戏，查看 `starsector.log` 中的 `Loading CSV data from [...]` 与本节 7 条 `Loading rule: GearIn_rules_intro_*` 行。
3. 查看启动器控制台标准输出中的 `$GearIn_rules_intro_skip`、`$GearIn_rules_intro_last` 两条单次使用变量 WARNING(正常现象，原版同样大量存在)。
4. **对话完整流程**：新建生涯，日志出现 `GearIn rules intro dialog shown=true`，对话弹出；文本为两条欢迎变体之一且含玩家名；选项为 "查看详情" / "结束"；按 ESC 等效点选 "结束"。
5. **结果链**：点 "查看详情"→ 追加 `FireAll 附加文本` 与 `高分规则` 两段文本，选项只剩 "结束"；点 "结束" 关闭对话。
6. **读档**：保存后读档，不再出现 `shown=` 日志、不再弹对话 (`REGISTER_MARK` 门已关)。

预期结果：

* **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/rules/GearInRulesIntro.class`。
* **规则注册 (加载期)**：
    *   启动日志 `starsector.log` 出现 `Loading CSV data from [...data/campaign/rules.csv]` 与逐条 `Loading rule: GearIn_rules_intro_welcome` / `..._add_a` / `..._add_b` / `..._open` / `..._detail_low` / `..._detail_high` / `..._leave`—— 注册入口完整流程的静态证明。
    *   标准输出 (启动器控制台) 出现两条变量警告 (`System.out.println`，非日志)：`WARNING: variable [$GearIn_rules_intro_skip] only used once; rule id: [GearIn_rules_intro_detail_high]` 与 `...[$GearIn_rules_intro_last] only used once; rule id: [GearIn_rules_intro_detail_high]`—— 单次使用变量属正常现象 (原版同样大量存在)，不是错误。
* **对话弹出 (新档首次进入生涯后第一帧)**：`starsector.log` 出现 `GearIn rules intro dialog shown=true`；游戏暂停并弹出对话：
    *   文本面板显示两条欢迎变体之一 (每次随机)：含玩家名的句子 (`$playerName` 已替换)；
    *   选项面板按 order 显示 "查看详情"(order 0) 与 "结束"(order 5)；
    *   按 ESC 等于点选 "结束"(`SetShortcut` 生效)。
* **点 "查看详情"**：文本面板按顺序追加三段：
    *   `FireAll 附加文本：条件为空，必然追加。`(`add_a` 命中；`add_b` 的 `RollProbability 0.0` 恒假不触发)；
    *   `高分规则：条件总分 102（1 + 1 + 100），FireBest 必然选中。`(`detail_high` 总分 102 恒胜 `detail_low` 的 1 分，确定性)；
    *   选项面板被替换为单个 "结束"(`detail_high` 的选项；`FireBest` 非 keepOptions 时先清空再添加)。
* **点 "结束"(或按 ESC)**：`DismissDialog` 关闭对话，游戏恢复。
* **读档**：不出现 `GearIn rules intro dialog shown=` 日志、不再弹对话 (`REGISTER_MARK` 门已关)；对话本身不随存档保存。
* **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志；加载期无 `Duplicate key` / `Duplicate rule id` / `Command [...] not found` / `has = ('assignment')` / `has == ('equals')` / `Option id starts with $` 等规则加载异常。

## 故障分析

- **对话只显示 failsafe `Leave`**：初始 trigger 与规则表不一致，或 welcome 规则没有载入。核对 `Loading rule:` 日志和构造器中的 trigger，修正后重新打开对话确认正文与选项出现。
- **规则加载时启动中止**：conditions 中出现赋值、script 中出现比较、选项 ID 以 `$` 开头、命令类缺失或同文件 ID 重复。按堆栈定位 CSV 行并把表达式放回正确列，重启后确认规则载入完成。
- **规则没有触发且日志无异常**：本规则 ID 与其他文件重复，后加载项被静默跳过。改用带 MOD 前缀的唯一 ID，再确认日志出现对应 `Loading rule:`。
- **点击选项后显示 `no rule found`**：`$option` 比较值与 options 列的 ID 不完全一致。统一大小写和空格，重新点击后确认目标规则命中。
- **条件始终为 false**：memory 键、作用域前缀或 tag 写错，也可能把 `"1"`、`"yes"` 当作真值。用 `DumpMemory` 核对实际键和值，改为 `Boolean.TRUE` 或字符串 `"true"` 后再次触发。
- **文本变体没有随机切换**：分隔符 `OR` 没有独占一行。修正 CSV 单元格内换行并保持 UTF-8 无 BOM，重复触发确认候选文本能够切换。
- **选项文本在冒号后被截断**：加载器把冒号解释为选项字段分隔。改写文本或使用全角冒号，重启后确认完整文本显示。
- **选项加载时报 `NumberFormatException`**：三段选项的首段不是可解析的排序数值。删除多余冒号或提供合法 order，重启后确认规则表载入。
- **`FireBest` 每次选中不同规则**：多个候选得分相同，picker 会随机选择。为需要确定结果的规则设置不同 `score`，重复触发后确认结果稳定。
- **`FireAll` 没有追加任何内容**：目标 trigger 无匹配项，或 `FireAllIntercept` 拦截了调用。核对目标 trigger 和拦截条件，再执行一次确认所有目标规则运行。
- **求值条件时状态被重复修改**：有副作用的命令被放进 conditions。将写状态操作移到 script，重复匹配后确认状态只改变一次。
- **对话出现红色异常文字**：规则命令执行时抛出异常。按红字中的类名和消息修正参数；非对话调用还要处理 null dialog，再触发确认异常段落消失。
- **日志显示 `shown=false`**：现有对话或新手引导占用了交互界面。只在 CAMPAIGN 状态且界面空闲时调用，等待当前流程结束后确认对话能够打开。
- **对话结束后 memory 值仍然保留**：该键设置了持久或正数天数的过期时间。根据用途显式设置期限，推进到该时间后用 memory 读取复验。

## 总结

本节把规则系统走通成一条从数据到对话的流程：`data/campaign/rules.csv` 的七列决定一条规则能写什么，加载与合并决定它是否真的进入游戏，匹配与条件决定它何时被选中，`FireBest` / `FireAll` 与 `$option` 回流把它变成对话文本和选项。完整最小例把这条流程全部接通：规则表只负责数据，`GearInRulesIntro` 只负责打开对话，文本与选项全部来自 CSV。

排错判断可以浓缩成几条：注册看 `Loading rule:` 日志而不是找报错；加载期致命错误全部是启动中止级 (条件列 `=`、脚本列 `==`、`$` 开头选项 id、命令找不到、同文件重复 id)；跨文件重复 id 静默跳过是规则不触发且无报错的首要原因；`FireBest` 只取一条而 `FireAll` 执行全部；判真只有 `"true"` 与 `Boolean.TRUE` 为真，比较大小写敏感；文本变体靠独占一行的 `OR`，选项文本里不能有冒号，选项 id 与 `$option ==` 必须逐字一致。

对话框生命周期与脚本注册判断是本节的两个底座。[C14-S3] 和 [C14-S4] 会继续使用这份 `GearIn_rules_intro_*` 规则表与 `$option` 回流约定。

## 建议参考项

* `data/campaign/rules.csv`：本节示例规则表 (`GearIn_rules_intro_*` 前缀) 与原版规则表的存放路径。
* `data/config/settings.json`：`ruleCommandPackages` 命令包配置。
* `com.fs.starfarer.api.campaign.rules.RulesAPI`：规则匹配入口 (`getAllMatching` / `getBestMatching` / `performTokenReplacement` / `setRandomForNextRulePick`)。
* `com.fs.starfarer.api.campaign.rules.RuleAPI`：单条规则的文本、选项与脚本访问。
* `com.fs.starfarer.api.campaign.rules.MemoryAPI` / `MemKeys`：规则记忆键读写与内存分组键。
* `com.fs.starfarer.api.campaign.rules.Option`：对话选项的 `id` / `text` / `order`。
* `com.fs.starfarer.api.campaign.rules.CommandPlugin` / `ExpressionAPI`：规则命令与条件表达式的约定接口。
* `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：内置规则命令基类 (`FireBest` / `FireAll` / `DismissDialog` / `RollProbability` / `SetShortcut` / `ShowDefaultVisual` 等均为其子类)。
* `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：规则驱动对话插件，本节使用方完整流程的核心。
* `com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`：舰队遭遇对话对规则引擎的调用。
* `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：`$tag:` / `$mc:` / `$friendlyToPlayer` 等 facts 记忆键的来源。
* `com.fs.starfarer.api.impl.campaign.CoreRuleTokenReplacementGeneratorImpl`：文本 token 替换表的默认实现。
* `com.fs.starfarer.api.util.Misc`：规则表达式 token 化、变量解析与 token 替换工具。
* `com.fs.starfarer.api.campaign.SectorAPI`：`getRules()` 获取 `RulesAPI`。
