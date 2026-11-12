# 编排 Rules 对话

本节在独立舰队通讯中加入 "检查通讯记录"：选择后追加一段信号记录，再按首次或重复读取给出详情，最后返回舰队菜单。规则之间通过选项 ID、Memory 与 trigger 连接，`FireAll` 和 `FireBest` 分别承担追加与选择，形成一段能进入也能结束的对话。

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 学习目标

完成后，您将能够：

- 让显示文本、选项 ID 与 `$option` 回流保持一致；
- 用默认分数和 `score:N` 建立候选层级；
- 使用 `FireBest` 选择单一结果，使用 `FireAll` 合并多个结果；
- 组合菜单填充、叙述、回流和一次性 `flag`；
- 为原版 `trigger` 选择正确的应用方式。

## 前置知识

- [C15-S1] 的 `rules.csv` 七列、换行单元格与加载结果；
- [C16-S1] 的选项显示文本与稳定 `optionData`；
- 能够在游戏中打开一支非敌对独立舰队的通讯菜单。

## 在通讯菜单添加入口

向 MOD 的 `data/campaign/rules.csv` 追加本节七条成品，保留现有问候和表头。首先在 `PopulateOptions` 中限定 `fleetConvMain`、独立势力与非敌对，添加入口，局部记录如下：

```csv
GearIn_rules_apply_menu,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent
!$entity.isHostile",,,"GearIn_rules_apply_open:检查通讯记录",独立通讯菜单入口
```

玩家看到 "检查通讯记录"，实际选项 ID 为 `GearIn_rules_apply_open`。它与当前规则 ID 分别提供身份，后续处理按选项 ID 查询。

玩家选择后，规则对话插件将数据写入 LOCAL 的 `$option`，期限为 0，再触发 `DialogOptionSelected`。回流规则用这个值进入结果处理，局部记录如下：

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

当前选项命中后，先调用 `GearInRulesApplyAdd`，再调用 `GearInRulesApplyDetail`。显示文本可以改为 "读取记录"，业务键与回流条件继续对应。

::: warning 选项 ID 必须逐字一致

`options` 中的 ID 与 `$option == <id>` 比较大小写敏感；多一个空格、改变前缀或改变字母大小写，都会让可见选项失去回流规则。
:::

## 让首次读取与重复结果使用不同条件

详情根据玩家是否已经读取选择。条件用 `!$player.GearIn_rules_apply_seen`，命中后的脚本向同一玩家 Memory 写入 `true`，局部记录如下：

```csv
GearIn_rules_apply_once,GearInRulesApplyDetail,"$faction.id == independent
!$entity.isHostile
!$player.GearIn_rules_apply_seen score:100","$player.GearIn_rules_apply_seen = true",第一次读取到完整通讯记录。,"GearIn_rules_apply_back:返回舰队菜单",
```

`!` 取反，缺失标记时首次条件通过，写入后重复进入则不再成立。**条件与结果使用同一个对象上的键。** 这里显式用 `player`，关闭通讯或换目标后，已读状态仍属于玩家。

### 字面量、变量与比较

表达式经过 `Misc.tokenize(...)` 拆为 token。`$` 开头的内容识别为变量，引号将文本保留为字面量，反斜杠转义下一字符；`=`、`==`、`!=`、`>`、`>=`、`<`、`<=`、`!`、`++` 与 `--` 分别形成操作符。

### 字符串、数值和纯变量判真

字符串比较区分大小写。数值比较通过 `Memory.parseFloat` 解析，因此 `$count >= 2` 比较的是数值；无法解析成数值的内容不能承担数值条件。

纯变量条件接受 `Boolean.TRUE`，或去掉首尾空白、忽略大小写后为 `"true"` 的字符串。计数与是否启用分别使用数值比较和布尔判断，下面的局部记录要求两者同时成立：

```csv
GearIn_rules_apply_truth,GearInRulesApplyAdd,"$player.GearIn_rules_apply_enabled
$player.GearIn_rules_apply_count >= 2",,通讯记录已达到读取条件。,"GearIn_rules_apply_back:返回舰队菜单",
```

数值字符串按 `Memory.parseFloat(...)` 解析，字符串比较区分大小写。条件返回的是当前判断，不是简单的非空检查；每条条件通过后，当前规则才继续参与匹配。

### 赋值、自增和自减

脚本用 `=` 写值，值后可提供生涯日期限；`++` 与 `--` 增减数值。下面是局部计数操作：

```csv
GearIn_rules_apply_count,DialogOptionSelected,$option == GearIn_rules_apply_open,"$GearIn_rules_apply_count = 1 0
$GearIn_rules_apply_count++
$GearIn_rules_apply_budget--",,,
```

三行依次写 1、增为 2、将 budget 减 1，后面的调用可以立即读取结果。无期限的自增、自减会重新写入永久值，因此这段计数的最初 0 日期限也会被后面的自增清除；需要期限时，增减表达式另提供对应天数。

### Memory 槽位决定变量来源

`$memkey.name` 先从 `memoryMap` 找到 `memkey` 槽位，再读取其中的 `$name`。常见前缀包括 `$global.`、`$player.`、`$market.`、`$faction.` 和 `$entity.`；没有点号的 `$option`、`$menuState` 与自定义 `local` 变量回落到 `local` Memory。

没有对应前缀槽位时，变量解析会把完整变量名交给 LOCAL。因此当前一次性标记在条件与脚本两侧都使用完整 `player` 前缀，[C15-S5] 再展开这些对象的寿命与槽位。

## 从通过条件的详情中选一条

`FireBest` 先淘汰条件失败的规则，再比较总分。普通条件各贡献 1，`score:N` 改变所在行的分值。以下局部候选说明高低两种结果：

```csv
GearIn_rules_apply_detail_low,GearInRulesApplyDetail,$faction.id == independent,,通讯记录只有一行摘要。,"GearIn_rules_apply_back:返回舰队菜单",
GearIn_rules_apply_detail_high,GearInRulesApplyDetail,"$faction.id == independent
!$entity.isHostile score:100",,通讯记录包含完整航行数据。,"GearIn_rules_apply_back:返回舰队菜单",
```

low 通过一条条件，总分 1；high 通过一条普通条件与一条 100 分条件，总分 101。**分数只在条件已经通过的候选之间竞争。** 目标非敌对且属于独立势力时，high 获选；高分也不能使失败条件通过。

多个最高分候选并列时，引擎使用 `WeightedRandomPicker` 随机选择。`RulesAPI.setRandomForNextRulePick(random)` 只影响下一次规则选择，完成后清除。需要确定结果时应给候选建立不同总分。

本例较高分数可以覆盖 1 到 3 分的普通回退，1000 分的剧情候选仍然优先。分值由规则约定，具体胜者同时取决于当前对象与命中条件。

### 自有 trigger 由当前回流调用

`GearInRulesApplyAdd` 与 `GearInRulesApplyDetail` 都是自有候选集合，需要调用才能运行。当前回流中的两个命令已经提供实际入口，局部记录如下：

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

入口来自玩家选择，内层处理继续使用当前对话与 Memory。两个集合分别承担追加与详情，完成后留给返回选项处理，整段对话仍在同一会话中。

## 用 `FireAll` 追加全部结果

先用 `FireAll` 加入共同的信号记录。它取得目标 trigger 下全部匹配规则，以下局部候选一条无条件，另一条概率为 0：

```csv
GearIn_rules_apply_add_a,GearInRulesApplyAdd,,,附加记录：信号来源已经确认。,"GearIn_rules_apply_back:返回舰队菜单",
GearIn_rules_apply_add_b,GearInRulesApplyAdd,RollProbability 0.0,,这段记录不会显示。,"GearIn_rules_apply_back:返回舰队菜单",
```

`add_a` 写入文本，`RollProbability 0.0` 始终返回 `false`，`add_b` 不进入结果。执行前，`FireAll` 还将目标 trigger 写入 `$fireAllTrigger` 并查询 `FireAllIntercept`；有拦截命中时，当前整次目标调用被接管。

通过匹配后，先汇总所有 `options` 和会添加选项的命令。集合非空时清理旧选项，按顺序写入新菜单，再依规则追加文字并执行普通脚本；没有选项结果时，当前菜单保留。`FireAll` 没有 `keepOptions` 参数。

## 用 `FireBest` 选择一条结果

详情用 `RulesAPI.getBestMatching(...)` 取得最高分的一条。获选后先处理选项，再追加替换 token 后的文字，最后执行普通脚本。没有匹配则返回 `false`，当前例中也就没有详情结果。

前面的 `low` 与 `high` 同属 `GearInRulesApplyDetail`。非敌对独立舰队命中 `high`，文本面板只出现 "通讯记录包含完整航行数据。"，`low` 文本不会同时追加。

获选规则有选项时，默认重建列表；第二参数 `true` 表示保留原选项并追加，候选与分数仍按原规则判断。本节成品的详情提供返回按钮，因此玩家读完后有明确下一步。

::: warning 覆盖规则需要承接原有结果

`FireBest` 只执行一个胜出规则；覆盖原版回退时，原规则承担的菜单状态、视觉、文本和后续 `fire` 调用都要由新规则继续完成。
:::

## 保留当前结果，并让调用结束

内层 fire 在当前规则执行中立即匹配，沿用外层 `dialog` 与 `memoryMap`。内层写入的状态供后面的脚本读取，文字与选项也直接写入同一面板。当前顺序是追加完成后再选择详情，局部回流如下：

当前回流按追加、详情两个方向依次调用：

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

这两个结果集合处理结束后返回当前调用，等待玩家选择返回按钮。它们没有再调用外层选项 trigger，当前执行也就有明确终点。

### 跳过当前规则

fire 将当前规则 ID 交给下一次匹配，引擎跳过这个 ID。其他规则仍可获选，所以不同 ID 的两条规则互相调用时，仍可能形成递归。

::: caution 再入关系必须有终点

规则 `A` 调用能够选中规则 `B` 的 `trigger`，而规则 `B` 又调用能够选中规则 `A` 的 `trigger` 时，两个不同 ID 会持续递归，表现为游戏无响应或栈溢出。自定义 `trigger` 应按单一方向组织，并让最后一组规则结束脚本执行或返回菜单。
:::

### 在同一会话保留结果

下面的局部回流使用 `FireBest` 的第二参数保留原选项：

```csv
GearIn_rules_apply_keep,DialogOptionSelected,$option == GearIn_rules_apply_keep,FireBest GearInRulesApplyDetail true,,,
```

这个局部片段保留当前选项，内层文字和状态仍写入原会话。它改变菜单合并方式，实际选择继续由条件和分数决定。

## 常见 `trigger` 速查表

| `trigger`               | 场景     | 主要用途                     |
| ----------------------- | -------- | ---------------------------- |
| `OpenInteractionDialog` | 通用交互 | 建立默认视觉、描述和初始选项 |
| `PopulateOptions`       | 通用菜单 | 按当前状态批量添加选项       |
| `DialogOptionSelected`  | 生涯对话 | 根据 `$option` 处理选择      |
| `PickGreeting`          | 人物通讯 | 选择人物问候                 |
| `OpenCommLink`          | 舰队通讯 | 建立通讯文本与菜单           |
| `MarketPostOpen`        | 市场     | 处理停靠许可与贸易状态       |
| `MarketPostDock`        | 市场     | 建立市场主菜单               |
| `TradePanelFlavorText`  | 交易面板 | 生成交易环境文本             |
| `BeginFleetEncounter`   | 舰队遭遇 | 生成遭遇开场与通讯请求       |
| `AddBarEvents`          | 酒吧     | 添加本次酒吧菜单的事件       |
| `BarPrintDesc`          | 酒吧     | 生成酒吧环境描述             |
| `ReturnFromBar`         | 酒吧     | 返回市场菜单                 |
| `MHOpenText`            | 任务列表 | 生成任务列表前文本           |
| `AddMHCloseOption`      | 任务列表 | 添加关闭任务列表的选项       |
| `AddNewGameChoices`     | 新游戏   | 填充开局选择                 |
| `NewGameOptionSelected` | 新游戏   | 处理新游戏选项回流           |

表格只用于判断规则应挂在哪类入口。每个 `trigger` 的原版调用类、精确派发顺序、规则记录数和特殊状态由 [C15-S3] 逐场景解释。

## 返回菜单，并保留玩家已读状态

`$menuState` 和 `$tradeMode` 表示当前界面状态。舰队通讯菜单通常使用 `fleetConvMain`，市场主菜单使用 `main`；菜单规则根据这些值决定是否加入选项。

本节进入通讯时使用 `fleetConvMain`，结果中的 `GearIn_rules_apply_back` 再通过 `DialogOptionSelected` 调用 `EndConversation`，回到舰队主菜单。市场与酒吧也按各自返回规则恢复状态，菜单选项继续使用对应场景。

已读标记采用无期限写入，关闭通讯后保留，再次建立通讯选择重复结果；菜单状态与 `$option` 的 0 日期限则服务当前流程。需要重演时，通过 `unset $player.GearIn_rules_apply_seen` 删除该键，`unsetAll` 按前缀清理，开发模式中的 `DumpMemory` 可核对槽位与值。

**入口、处理结果与返回都对应实际调用。** `PopulateOptions` 提供检查，选项回流进入两个结果集合，返回数据再结束通讯。状态与规则 ID 各自对应，玩家才能沿这段对话完成一次操作。

## 完整最小例

下面七条记录接在已经建立的 `fleetConvMain` 通讯菜单。它们分别提供入口、追加、详情与返回，沿用已有规则文件。

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_apply_menu,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent
!$entity.isHostile",,,"GearIn_rules_apply_open:检查通讯记录",添加独立舰队通讯入口
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,依次追加记录并选择详情
GearIn_rules_apply_add_a,GearInRulesApplyAdd,,,附加记录：信号来源已经确认。,,FireAll 的稳定结果
GearIn_rules_apply_add_b,GearInRulesApplyAdd,RollProbability 0.0,,这段记录不会显示。,,FireAll 的失败条件
GearIn_rules_apply_detail_first,GearInRulesApplyDetail,"$faction.id == independent
!$player.GearIn_rules_apply_seen score:100","$player.GearIn_rules_apply_seen = true",通讯记录包含完整航行数据。,"GearIn_rules_apply_back:返回舰队菜单",首次高分结果
GearIn_rules_apply_detail_repeat,GearInRulesApplyDetail,$faction.id == independent,,这份通讯记录已经读取过。,"GearIn_rules_apply_back:返回舰队菜单",重复读取回退
GearIn_rules_apply_back,DialogOptionSelected,$option == GearIn_rules_apply_back,EndConversation,,,返回舰队菜单
```

## 验证方案

前置数据：MOD 的 `data/campaign/rules.csv` 已加入本页七条规则，玩家能够与一支非敌对独立舰队建立通讯。

1. 启动游戏，在日志中确认七个 `GearIn_rules_apply_*` ID 都出现 `Loading rule:`。
2. 打开非敌对独立舰队通讯，确认菜单出现 "检查通讯记录"。
3. 把显示文本改成 "读取记录" 并保持 ID 不变，重新进入后确认分支仍能执行。
4. 选择该选项，确认文本面板先出现 `FireAll` 的 "信号来源已经确认"，再出现 `FireBest` 的 "完整航行数据"。
5. 确认 `GearIn_rules_apply_add_b` 的文本没有出现。
6. 返回舰队主菜单，再次选择建立通讯并检查记录，查看永久玩家标记使详情改选 "已经读取过"。
7. 选择 "返回舰队菜单"，确认 `EndConversation` 恢复舰队主菜单。
8. 临时让两个详情规则同分，确认结果可在并列候选间变化；恢复 `score:100`。
9. 临时加入匹配 `$fireAllTrigger == GearInRulesApplyAdd` 的 `FireAllIntercept`，确认附加记录整段被拦截；随后移除拦截规则。
10. 给 `FireBest` 分别传入和省略 `keepOptions`，确认前者保留旧选项；再让 `FireAll` 目标规则产生选项，确认它清空并重建选项面板。

预期结果：

- 菜单入口只出现在非敌对独立舰队的 `fleetConvMain` 状态。
- 显示文本可以独立修改，稳定 ID 继续让 `$option` 命中回流规则。
- `FireAll` 只追加条件通过的 `add_a`，`FireBest` 首次选择 101 分的 `detail_first`。
- 一次性 `flag` 写入后，第二次由 1 分的 `detail_repeat` 接管。
- `GearIn_rules_apply_back → DialogOptionSelected → EndConversation` 把控制权交回舰队菜单。

## 故障分析

- **选项可见但选择后出现 `no rule found`**：`options` 中的 ID 与 `$option` 条件没有逐字对应。
    - **解决方案**：统一选项 ID 的前缀、大小写和空格。

- **选择记录后只有附加文本**：`GearInRulesApplyDetail` 没有任何候选通过条件。
    - **解决方案**：检查势力槽位、敌对状态和一次性 `flag`。

- **`FireBest` 在两个结果间变化**：最高总分相同，规则进入同分随机选择。
    - **解决方案**：用 `score:N` 建立确定的总分差。

- **`FireAll` 没有追加记录**：目标 `trigger` 被 `FireAllIntercept` 拦截或全部条件失败。
    - **解决方案**：检查 `$fireAllTrigger`、拦截条件和目标规则条件。

- **返回选项留下空白对话**：返回 ID 没有对应 `DialogOptionSelected` 规则。
    - **解决方案**：让 `GearIn_rules_apply_back` 命中执行 `EndConversation` 的处理器。

- **一次性结果反复出现**：条件与脚本使用了不同 Memory 槽位。
    - **解决方案**：两侧统一使用 `$player.GearIn_rules_apply_seen`。

## 总结

通讯检查通过稳定选项回流，先追加信号记录，再选择一条详情，最后返回舰队菜单。玩家 Memory 保留已读结果，下一次通讯继续按这个状态选择。

条件决定候选，总分决定竞争，fire 在当前会话中执行结果。选项、状态与返回各有实际入口，规则也就能组成一段完整对话。

## 建议参考项

- `data/campaign/rules.csv`：本节 `GearIn_rules_apply_*` 规则的数据文件。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：`$option` 写入和 `DialogOptionSelected` 回流。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：最高分选择和 `keepOptions`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：全部匹配、`$fireAllTrigger` 与 `FireAllIntercept`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.EndConversation`：通讯结果返回舰队菜单。
- `com.fs.starfarer.api.campaign.rules.RulesAPI`：匹配集合、最佳规则与下一次随机源。
- `com.fs.starfarer.api.util.Misc`：表达式 `token` 化和 Memory 变量解析。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：状态、过期和一次性 `flag`。
