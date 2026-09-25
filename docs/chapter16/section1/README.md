# 组织 Rules 规则表

`data/campaign/rules.csv` 是 Rules 系统的数据入口。加载器先按表头把一行拆成七个字段，再把字段内部的换行解释为条件、脚本、文本变体或选项。本节沿这条读取顺序讲清每一列，随后处理合并与加载错误，最后用两条 `OpenCommLink` greeting 验证数据已经进入引擎。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说明 `rules.csv` 七列各自生成什么运行时数据；
- 在多行单元格中正确使用引号、换行和字段注释；
- 判断同路径合并、`replace` 和重复 ID 对加载结果的影响；
- 从日志区分数据未加载、规则未注册和表达式语法错误；
- 用两条独立势力 `OpenCommLink` 规则验证加载结果。

## 前置知识

- [C14-S3] 的交互对话框生命周期；
- [C15-S1] 的 `InteractionDialogAPI` 与选项分支；
- [C17-S3] 的 CSV 表头、单元格和合并读取。

## 从表头建立规则对象

Rules 从 `data/campaign/rules.csv` 的表头识别字段。每个有效数据行生成一个规则对象，列名决定单元格进入哪个字段。列的物理顺序可以随表头调整；一旦表头确定，下面每行都要保持相同的列边界。

```csv
id,trigger,conditions,script,text,options,notes
```

### id 保存规则身份

`id` 是加载器识别规则的稳定键，也会出现在 `Loading rule: <id>` 日志中。ID 为空时不创建规则；去掉首尾空白后以 `#` 开头时，加载器跳过整行。

```csv
#GearIn_rules_comm_disabled,OpenCommLink,,,,,暂时停用
GearIn_rules_comm_friendly,OpenCommLink,,,,,独立势力的非敌对问候
```

第一行不会进入规则集合，第二行会注册为 `GearIn_rules_comm_friendly`。ID 应带 MOD 前缀，使日志归属和跨文件唯一性都能直接检查。

### trigger 建立候选集合

`trigger` 是候选集合的名称。使用方调用 `OpenCommLink` 时，引擎只检查 trigger 与这个字符串相同的规则；同一行不会因为写入一个新名字而自动产生调用入口。

```csv
GearIn_rules_comm_friendly,OpenCommLink,,,,,独立势力的非敌对问候
```

这条规则加入 `OpenCommLink` 候选集合。trigger 拼写和大小写需要与调用方传入的字符串一致；写成 `OpenCommlink` 会得到一条已经加载、但原版通讯入口从不查询的规则。

### conditions 保存命中条件

`conditions` 使用物理换行保存多条表达式。加载器去掉每行首尾空白，跳过空行和以 `#` 开头的子行；规则参与匹配时按顺序求值，任意一行失败都会淘汰当前候选。

```csv
GearIn_rules_comm_friendly,OpenCommLink,"# 只处理独立势力
$faction.id == independent
!$entity.isHostile score:100",,,,独立势力的非敌对问候
```

注释行不生成表达式。剩余两行分别检查势力 ID 和敌对状态；`score:100` 仍属于第二条条件。条件如何判真与计分由 [C16-S2] 展开，本节只要求比较留在 `conditions`，赋值留在 `script`。

### script 保存命中结果

`script` 同样按物理换行拆分，但每行表示赋值或 Rules 命令。规则选中后，脚本从上到下执行，因此前一行写入的状态可以被后一行命令读取。

```csv
GearIn_rules_comm_friendly,OpenCommLink,,"$menuState = fleetConvMain 0
FireAll PopulateOptions",,,独立势力的非敌对问候
```

第一行把菜单状态写为 `fleetConvMain`，第二行调用 `PopulateOptions`。命令名由 `data/config/settings.json` 的 `ruleCommandPackages` 解析；默认包为 `com.fs.starfarer.api.impl.campaign.rulecmd`，未知命令会在加载阶段报告错误。

命令参数需要字面双引号时，CSV 使用两个连续双引号转义。例如 `SetShortcut optionId "ESCAPE"` 在单元格中写成 `SetShortcut optionId ""ESCAPE""`。

### text 生成对话文本

`text` 保存规则命中后写入文本面板的内容。规则执行器先替换其中的 token，再把结果交给当前对话；文本中的 `#` 保持为普通显示字符。

```csv
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
```

第五列的普通字符串会成为通讯建立后的问候文本。条件、脚本和 notes 不会拼进这段正文；若 text 写成 `# 通讯接通了。`，玩家看到的文本也会保留开头的井号。

::: tip 用独占一行的 OR 准备候选文本

同一条规则需要随机采用多种措辞时，可以用独占一行的 `OR` 分隔候选文本，并用一对双引号包住整个多行单元格：

```csv
GearIn_rules_text_variants,OpenCommLink,,,"对方很快回应了通讯请求。
OR
# 通讯接通了。",,两种问候文本
```

加载器按精确的换行序列 `\nOR\n` 拆分内容，执行规则时再随机选择其中一段。第二个候选开头的井号会正常显示；`OR` 与其他文字共处一行时属于正文。
:::

### options 生成对话选项

`options` 的每个有效子行采用 `id:text` 或 `order:id:text`。两段形式使用默认顺序，三段形式把第一段解析为排序数值；ID 进入程序分支，text 进入选项面板。

```csv
GearIn_rules_options,PopulateOptions,,,,"GearIn_rules_apply_open:检查通讯记录
50:cutCommLink:切断通讯",两个选项
```

玩家看到 "检查通讯记录" 和 "切断通讯"，程序收到 `GearIn_rules_apply_open` 或 `cutCommLink`。读取器按半角冒号切分，显示文本中的额外冒号会改变字段数量；以 `$` 开头的选项 ID 会在加载阶段被拒绝。

和条件、脚本一样，options 中以 `#` 开头的子行是注释。选项 ID 与 `$option` 的回流关系由 [C16-S2] 负责。

### notes 只保存维护说明

`notes` 不生成条件、脚本、文本或选项。它适合记录规则用途、数据所有者和与其他规则的关系，加载器不会把内容显示给玩家。

```csv
GearIn_rules_comm_friendly,OpenCommLink,,,,,GearIn 独立势力通讯问候
```

修改 notes 不会改变候选集合和游戏画面。需要暂时停用一行时应修改 `id`，不能把 notes 当作开关。

## 保持 CSV 列边界

### 用双引号包住多行单元格

`conditions`、`script`、`text` 和 `options` 都可能包含物理换行。CSV 必须用一对双引号包住整个多行单元格；换行发生在引号内部时仍属于当前列，发生在引号外部时才结束当前记录。

```csv
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
```

这条记录虽然占用四个物理行，仍只有七列和一条规则。少掉 conditions 左侧或右侧的双引号后，后续换行会开始新记录，日志通常会把错误定位在看似无关的下一行。

### 转义逗号和双引号

含半角逗号的单元格也要放进双引号。单元格内部的一个字面双引号写成 `""`；反斜杠不会替代 CSV 的双引号转义。

```csv
GearIn_rules_quoted,OpenCommLink,,"AddText ""信号稳定，继续通讯。""",,,含逗号和引号
```

加载器交给命令的参数是 `信号稳定，继续通讯。`。若逗号没有被双引号保护，它会开始下一列，使 script、text、options 和 notes 整体错位。

### 区分整行注释与字段注释

`#` 的效果取决于所在字段：`id` 以 `#` 开头时跳过整行；`conditions`、`script`、`options` 中以 `#` 开头的子行作为注释；`text` 中的 `#` 保持为显示文本。notes 不参与运行，本身也不需要注释语法。

```csv
#GearIn_rules_comm_disabled,OpenCommLink,,,,,整行停用
GearIn_rules_comm_note,OpenCommLink,"# 条件注释
$faction.id == independent","# 脚本注释
$menuState = fleetConvMain 0","# 这里的井号会显示",,字段内注释
```

第二条规则仍会注册。它只有一个有效条件和一条有效脚本，而文本面板会完整显示 "# 这里的井号会显示"。

::: warning 井号只注释所在的数据层

停用整条规则需要让 `id` 以 `#` 开头；conditions、script 或 options 中的井号只跳过当前子行，text 中的井号会显示给玩家。
:::

### 保存为 UTF-8 无 BOM

规则表使用 UTF-8 无 BOM。BOM 位于文件首字节时可能进入首个表头或键的解析结果；智能引号替换只处理弯引号等字符，不负责移除错误编码标记。

::: warning 多行错误会移动后续列

每条数据记录都要解析出七列；缺失包围引号、未转义双引号和裸逗号会让后续字段进入错误列，必须先修复 CSV 结构再检查 Rules 条件。
:::

## 让规则进入运行时

### 读取并合并同路径资源

游戏在 CSV spec 加载阶段调用 `Rules.loadRules`。读取器执行 `readCSV(["id"], "data/campaign/rules.csv", true, true)`，扫描原版和所有已启用 MOD 的同路径文件。`["id"]` 提供跨来源去重键，第三个参数控制合并顺序，第四个参数让每份文件在 CSV 解析前经过 `replaceSmartQuotes`。

这项加载发生在启动阶段。规则对象由当前启用的数据文件重建，不作为单独对象写进存档。

加载日志会出现 `Loading CSV data from [...]`，随后为每条成功注册的规则输出 `Loading rule: <id>`。确认数据进入引擎时，先找这些稳定 ID，再判断对话是否有匹配；只有出现 `Loading rule:` 的行，规则才进入后续候选池。

::: warning 重复 ID 有两种结果

同一文件中的重复 ID 会抛出 `RuntimeException("Duplicate key [...] while loading [...]")` 并停止加载；跨文件重复时先处理的规则保留，后处理的同 ID 行静默跳过。
:::

某个 MOD 在 `mod_info.json` 的 `replace` 数组中声明 `data/campaign/rules.csv` 时，读取器只采用替换文件，同路径的原版和其他合并来源不会进入结果。

::: caution replace 会切断原版 Rules 数据

普通扩展应让 `data/campaign/rules.csv` 参与同路径合并；声明 `replace` 后，MOD 文件需要独自承担原版整张规则表的职责。
:::

加载器还会统计只出现一次的变量，并向标准输出打印 `WARNING: variable [...] only used once; rule id: [...]`。`$tag:` 和 `$market.mc:` 不参与这项统计；WARNING 不会改变规则匹配。

## 识别加载期错误

加载器把 conditions 与 script 交给同一套表达式解析器，但两列允许的操作不同。`=` 只用于脚本赋值，`==`、`!=`、`>`、`>=`、`<` 和 `<=` 用于条件比较。解析器先验证操作符位置，再加载命令类，因此这些错误在进入生涯之前就会暴露。

条件列出现赋值、脚本列出现比较、未知命令、选项 ID 以 `$` 开头、三段选项的 order 不是数值，以及同一文件出现重复 ID，都会阻止对应数据正常进入运行时。堆栈中的规则 ID 和 CSV 路径是修复入口。

跨文件重复 ID 的表现不同。按同路径合并时，先处理的规则保留，后处理的同 ID 行静默跳过，因此日志中只会看到一条 `Loading rule:`。定位 "文件被读取但某条规则没有注册" 时，需要同时检查 ID 唯一性和日志中的具体规则 ID。

## 七列速查表

| 字段 | 读取结果 | 内部拆分 | 主要错误 |
|---|---|---|---|
| `id` | 规则稳定身份 | 不拆分 | 空值、`#` 跳过、重复 ID |
| `trigger` | 候选集合名称 | 不拆分 | 拼写或大小写与调用方不一致 |
| `conditions` | 候选匹配条件 | 按换行拆分，跳过空行和 `#` 子行 | 写入赋值或错误比较 |
| `script` | 命中后的赋值与命令 | 按换行顺序拆分，跳过空行和 `#` 子行 | 未知命令、比较进入脚本 |
| `text` | 文本面板内容 | 整个单元格作为文本内容读取 | 把 `#` 误认为字段注释 |
| `options` | 选项 ID、文本与顺序 | 按换行拆分，再按冒号分成 2 或 3 段 | 裸冒号、非法 order、`$` 开头 ID |
| `notes` | 维护说明 | 不参与运行时解析 | 把 notes 当作停用开关 |

## 用 OpenCommLink 验证规则表

`OpenCommLink` 是原版舰队通讯建立连接时使用的 trigger。本页只借它验证加载结果：两条规则都限定 `independent`，friendly 再限定非敌对；脚本把菜单切回 `fleetConvMain`，再调用 `FireAll PopulateOptions` 取得原版通讯选项。分数竞争和 FireAll 的执行机制由 [C16-S2] 解释。

## 完整最小例

把下面三行放入 MOD 的 `data/campaign/rules.csv`。两条数据行共享 `OpenCommLink`，并各自保持完整的七列结构。

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
GearIn_rules_comm_fallback,OpenCommLink,$faction.id == independent score:50,"$menuState = fleetConvMain 0
FireAll PopulateOptions",独立舰队接通了通讯。,,独立势力的回退问候
```

## 验证方案

前置数据：已启用包含本页 `data/campaign/rules.csv` 的 MOD。观察位置包括启动日志、启动器标准输出和独立舰队通讯界面。

1. 启动游戏，确认日志出现 `Loading CSV data from [...]`，并找到 `GearIn_rules_comm_friendly` 与 `GearIn_rules_comm_fallback` 的 `Loading rule:` 行。
2. 向非敌对的独立舰队打开通讯，确认显示 friendly 文本，并出现原版舰队通讯选项。
3. 让同一势力舰队进入敌对状态后再次通讯，确认 fallback 文本接替 friendly 文本。
4. 在启动器标准输出中核对单次使用变量 WARNING，并把变量名与规则 ID 对应起来。
5. 分别制造同文件重复 ID 和跨文件重复 ID，确认前者中止加载、后者只注册先处理的规则；恢复两个唯一 ID。
6. 在 `conditions` 中写赋值、在 `script` 中写比较、把命令名写错，逐项确认加载期错误后恢复正确表达式。
7. 在 options 文本中加入半角冒号，确认选项解析错误；改用全角冒号或改写句子。
8. 给 MOD 声明同路径 `replace`，确认原版规则未参与合并；移除该声明后确认原版和 MOD 规则共同加载。

预期结果：

- 日志同时出现规则文件路径和两个稳定 ID；数据结构错误在进入生涯前暴露。
- 非敌对独立舰队使用 friendly 文本，敌对独立舰队使用 fallback 文本。
- 两条脚本都恢复 `fleetConvMain` 并追加原版 `PopulateOptions` 结果。

## 故障分析

- **规则加载时启动中止**：`conditions` 中出现赋值、`script` 中出现比较、选项 ID 以 `$` 开头、命令类缺失或同文件 ID 重复。
    - **解决方案**：按堆栈定位 CSV 行并把表达式放回正确列，重启。

- **规则没有触发且日志无异常**：本规则 ID 与其他文件重复，后加载项被静默跳过。
    - **解决方案**：改用带 MOD 前缀的唯一 ID。

- **选项文本在冒号后被截断**：加载器把冒号解释为选项字段分隔。
    - **解决方案**：改写文本或使用全角冒号，重启。

- **选项加载时报 `NumberFormatException`**：三段选项的首段不是可解析的排序数值。
    - **解决方案**：删除多余冒号或提供合法 order，重启。

- **整份原版规则消失**：MOD 对 `data/campaign/rules.csv` 声明了 `replace`。
    - **解决方案**：移除 `replace`，让同路径 CSV 参与合并。

## 总结

`rules.csv` 的表头决定字段，CSV 引号保护多行单元格，字段读取器再按换行、`#` 和冒号解释内部结构。加载阶段负责同路径合并、智能引号替换、重复 ID 处理、表达式解析和规则注册；`Loading rule:` 是单条规则进入候选池的直接证据。

本页的两条 `OpenCommLink` 规则只承担数据接入：friendly 用额外条件和更高分数覆盖非敌对独立舰队，fallback 接住剩余独立舰队。选项回流、分数竞争和 fire 方法从 [C16-S2] 开始组织成完整对话。

## 建议参考项

- `data/campaign/rules.csv`：本节 `GearIn_rules_comm_*` 数据与原版规则表的存放路径。
- `data/config/settings.json`：`ruleCommandPackages` 命令包配置。
- `com.fs.starfarer.api.campaign.rules.RuleAPI`：加载后单条规则的字段对象。
- `com.fs.starfarer.api.campaign.rules.Option`：对话选项的 `id` / `text` / `order`。
- `com.fs.starfarer.api.campaign.rules.ExpressionAPI`：条件和脚本表达式的加载结果。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：示例脚本调用的原版菜单填充命令。
