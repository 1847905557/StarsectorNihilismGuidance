# 编排 Rules 对话

本节从一条独立舰队通讯选项开始。选项 ID 进入 `$option`，回流规则再调用自定义 trigger；FireAll 负责追加多条结果，FireBest 负责从竞争规则中选出一条。S1 的 greeting 不参与这组样例，原版场景的精确派发时机由 [C16-S3] 解释。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 让显示文本、选项 ID 与 `$option` 回流保持一致；
- 用默认分数和 `score:N` 建立候选层级；
- 使用 `FireBest` 选择单一结果，使用 `FireAll` 合并多个结果；
- 组合菜单填充、叙述、回流和一次性 flag；
- 为原版 trigger 选择正确的应用方式。

## 前置知识

- [C16-S1] 的 `rules.csv` 七列、换行单元格与加载结果；
- [C15-S1] 的选项显示文本与稳定 `optionData`；
- 能够在游戏中打开一支非敌对独立舰队的通讯菜单。

## 让选项回到规则

### 显示文本和选项 ID

Rules 选项同时保存显示文本和 ID。显示文本面向玩家，ID 面向程序；修改文字不会改变分支，修改 ID 会改变后续匹配。

下面这条 `PopulateOptions` 规则只在非敌对独立舰队的通讯主菜单中添加入口：

```csv
GearIn_rules_apply_menu,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent
!$entity.isHostile",,,"GearIn_rules_apply_open:检查通讯记录",独立通讯菜单入口
```

玩家看到 "检查通讯记录"，选项面板保存的 ID 是 `GearIn_rules_apply_open`。这个 ID 不需要与规则的 `id` 相同，但必须与负责回流的条件逐字一致。

### `$option` 保存本次选择

`RuleBasedInteractionDialogPluginImpl.optionSelected(...)` 收到选项数据后，把它写进 local Memory 的 `$option`，过期时间为 0，然后调用 `DialogOptionSelected`。因此选项分支写成一条普通条件：

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

选择 "检查通讯记录" 后，`$option` 等于 `GearIn_rules_apply_open`，脚本依次调用两个自定义 trigger。显示文本仍可改成 "读取记录"，回流条件不需要修改。

::: warning 选项 ID 必须逐字一致

`options` 中的 ID 与 `$option == <id>` 比较大小写敏感；多一个空格、改变前缀或改变字母大小写，都会让可见选项失去回流规则。
:::

## 读懂条件和脚本表达式

### token 与操作符

`Misc.tokenize` 把 `$` 开头的内容识别为变量，把引号包围的内容保留为字面量，并让反斜杠转义下一个字符。`=`、`==`、`!=`、`>`、`>=`、`<`、`<=`、`!`、`++` 和 `--` 会成为独立 token。

比较写在 conditions，赋值写在 script。下面的条件读取势力、敌对状态和一次性 flag；脚本在命中后写入这个 flag：

```csv
GearIn_rules_apply_once,GearInRulesApplyDetail,"$faction.id == independent
!$entity.isHostile
!$player.GearIn_rules_apply_seen score:100","$player.GearIn_rules_apply_seen = true 0",第一次读取到完整通讯记录。,"GearIn_rules_apply_back:返回舰队菜单",
```

`!` 对变量判真结果取反。第一次进入时 flag 不存在，条件通过；脚本写入后再次进入，这条规则不再匹配。

### 字符串、数值和纯变量判真

字符串比较区分大小写。数值比较通过 `Memory.parseFloat` 解析，因此 `$count >= 2` 比较的是数值；无法解析成数值的内容不能承担数值条件。

纯变量表达式只把 `Boolean.TRUE` 和忽略大小写的字符串 `"true"` 判为真。`null`、`"1"`、`"false"` 和 `"yes"` 都不会通过纯变量条件。

```csv
GearIn_rules_apply_truth,GearInRulesApplyAdd,"$player.GearIn_rules_apply_enabled
$player.GearIn_rules_apply_count >= 2",,通讯记录已达到读取条件。,"GearIn_rules_apply_back:返回舰队菜单",
```

两行都通过时这条规则才进入 FireAll 结果。布尔 flag 与计数值承担不同类型的检查，不能用 "非空就是真" 的方式混用。

### 赋值、自增和自减

脚本赋值使用 `=`。值后可以附加过期天数；`0` 表示写入后供当前规则流程使用，并由 Memory 的推进机制处理。数值还可以用 `++` 和 `--` 改变。

```csv
GearIn_rules_apply_count,DialogOptionSelected,$option == GearIn_rules_apply_open,"$GearIn_rules_apply_count = 1 0
$GearIn_rules_apply_count++
$GearIn_rules_apply_budget--",,,
```

三行按顺序运行：先写 1，再增为 2，最后让 budget 减 1。后续 FireBest 或 FireAll 能读到这些已经更新的值。

### Memory 槽位决定变量来源

`$memkey.name` 先从 `memoryMap` 找到 memkey 槽位，再读取其中的 `$name`。常见前缀包括 `$global.`、`$player.`、`$market.`、`$faction.` 和 `$entity.`；没有点号的 `$option`、`$menuState` 与自定义 local 变量回落到 local Memory。

同名键写在不同槽位时是不同状态。一次性玩家标记应在条件和脚本两侧都使用完整的 `$player.GearIn_rules_apply_seen`，不能一侧省略为 local。

## 用 score 建立竞争顺序

每条普通条件默认贡献 1 分。`score:N` 只改变它所在条件行的分值，规则总分是所有通过条件分数之和。

```csv
GearIn_rules_apply_detail_low,GearInRulesApplyDetail,$faction.id == independent,,通讯记录只有一行摘要。,"GearIn_rules_apply_back:返回舰队菜单",
GearIn_rules_apply_detail_high,GearInRulesApplyDetail,"$faction.id == independent
!$entity.isHostile score:100",,通讯记录包含完整航行数据。,"GearIn_rules_apply_back:返回舰队菜单",
```

low 通过一条普通条件，总分 1。high 通过一条普通条件和一条 `score:100` 条件，总分 101；在非敌对独立舰队上，FireBest 选择 high。

多个最高分候选并列时，引擎使用 `WeightedRandomPicker` 随机选择。`RulesAPI.setRandomForNextRulePick(random)` 只影响下一次规则选择，完成后清除。需要确定结果时应给候选建立不同总分。

原版普通回退常处于 1–3 分，MOD 的 100 级规则可以压过这类回退；剧情规则常用 1000 级分数，仍能优先接管特殊流程。

## 调用自定义 trigger

自定义 trigger 是 MOD 自己约定的候选集合名称，例如 `GearInRulesApplyAdd` 和 `GearInRulesApplyDetail`。把名字写进 trigger 列只会建立候选集合；它需要由另一条规则的 `FireAll` 或 `FireBest` 主动调用。

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

这条回流规则先完成追加，再选择详情。两个 trigger 没有原版固定派发点，也不需要 Java 入口，因为它们已经位于一条可到达的 Rules 脚本中。

## 用 FireAll 追加全部结果

`FireAll` 调用 `RulesAPI.getAllMatching(...)`，保留目标 trigger 下全部通过条件的规则。它会先在目标实体 Memory 写入 `$fireAllTrigger`，再触发 `FireAllIntercept`；拦截规则命中时，目标 trigger 的整次执行被跳过。

```csv
GearIn_rules_apply_add_a,GearInRulesApplyAdd,,,附加记录：信号来源已经确认。,"GearIn_rules_apply_back:返回舰队菜单",
GearIn_rules_apply_add_b,GearInRulesApplyAdd,RollProbability 0.0,,这段记录不会显示。,"GearIn_rules_apply_back:返回舰队菜单",
```

`FireAll GearInRulesApplyAdd` 执行全部匹配项。add_a 没有条件，会写入文本；add_b 的 `RollProbability 0.0` 返回 false，不进入结果。

FireAll 先收集全部匹配规则的 options，以及脚本中会添加选项的命令。只要收集结果非空，它就清空选项面板、排序并写入新选项；随后才按规则顺序追加文本并运行普通脚本。FireAll 没有 `keepOptions` 参数。

## 用 FireBest 选择一条结果

`FireBest` 调用 `RulesAPI.getBestMatching(...)`，只运行总分最高的一条规则。命中后先收集并排序该规则的 options 与会添加选项的命令，再追加经过 token 替换的文本，最后执行普通脚本；没有匹配时返回 false。

前面的 low 与 high 同属 `GearInRulesApplyDetail`。非敌对独立舰队命中 high，文本面板只出现 "通讯记录包含完整航行数据。"，low 文本不会同时追加。

FireBest 在胜出规则产生选项时默认重建选项列表。第二个参数 `true` 表示 `keepOptions`，旧选项会和新结果共存；这个参数不改变候选分数和胜出规则。

::: warning 覆盖规则需要承接原有结果

FireBest 只执行一个胜出规则；覆盖原版回退时，原规则承担的菜单状态、视觉、文本和后续 fire 调用都要由新规则继续完成。
:::

## 理解 fire 的再入

规则脚本执行 `FireBest` 或 `FireAll` 时，规则引擎会在当前执行过程里再次匹配目标 trigger。内层匹配继续使用外层的 `dialog` 与 `memoryMap`，所以内层脚本写入的 Memory 会立即被外层后续脚本读取，内层改动的文本和选项也会直接出现在同一对话面板。

本页的回流规则就是一条单向再入路径：

```csv
GearIn_rules_apply_open,DialogOptionSelected,$option == GearIn_rules_apply_open,"FireAll GearInRulesApplyAdd
FireBest GearInRulesApplyDetail",,,
```

`DialogOptionSelected` 规则先进入 `GearInRulesApplyAdd`，执行完成后再进入 `GearInRulesApplyDetail`。两个目标 trigger 都不会重新调用 `DialogOptionSelected`，执行方向在详情规则结束，因此这条路径有明确终点。

### 跳过当前规则

`FireBest` 与 `FireAll` 把当前 `ruleId` 传给下一次匹配。规则引擎会跳过同 ID 的当前规则，但目标 trigger 中其他 ID 的规则仍可命中。这个判断只能阻止一条规则立即选中自己，无法阻止两条不同规则互相调用。

::: caution 再入关系必须有终点

规则 A 调用能够选中规则 B 的 trigger，而规则 B 又调用能够选中规则 A 的 trigger 时，两个不同 ID 会持续递归，表现为游戏无响应或栈溢出。自定义 trigger 应按单一方向组织，并让最后一组规则结束脚本执行或返回菜单。
:::

### 共享选项与 Memory

`FireBest` 的目标规则产生选项时，默认会清空当前选项面板再写入新选项。第二个参数 `true` 只改变这个步骤，让外层选项继续保留；它不会复制 `dialog` 或 `memoryMap`，也不会改变候选分数。

```csv
GearIn_rules_apply_keep,DialogOptionSelected,$option == GearIn_rules_apply_keep,FireBest GearInRulesApplyDetail true,,,
```

这条写法让 `GearInRulesApplyDetail` 的结果与已有选项共存。省略 `true` 后，胜出规则只要产生选项，就会用自己的选项替换当前列表。

`FireAll` 会先写入 `$fireAllTrigger`，再调用 `FireBest FireAllIntercept`。拦截规则命中后，目标 trigger 不再执行；没有拦截时，全部匹配规则继续共享当前 Memory 和面板。`FireAll` 本身没有 `keepOptions` 参数，目标规则产生选项时会统一重建选项列表。

## 常见 trigger 速查表

| trigger | 场景 | 主要用途 |
|---|---|---|
| `OpenInteractionDialog` | 通用交互 | 建立默认视觉、描述和初始选项 |
| `PopulateOptions` | 通用菜单 | 按当前状态批量添加选项 |
| `DialogOptionSelected` | 生涯对话 | 根据 `$option` 处理选择 |
| `PickGreeting` | 人物通讯 | 选择人物问候 |
| `OpenCommLink` | 舰队通讯 | 建立通讯文本与菜单 |
| `MarketPostOpen` | 市场 | 处理停靠许可与贸易状态 |
| `MarketPostDock` | 市场 | 建立市场主菜单 |
| `TradePanelFlavorText` | 交易面板 | 生成交易环境文本 |
| `BeginFleetEncounter` | 舰队遭遇 | 生成遭遇开场与通讯请求 |
| `AddBarEvents` | 酒吧 | 添加本次酒吧菜单的事件 |
| `BarPrintDesc` | 酒吧 | 生成酒吧环境描述 |
| `ReturnFromBar` | 酒吧 | 返回市场菜单 |
| `MHOpenText` | 任务列表 | 生成任务列表前文本 |
| `AddMHCloseOption` | 任务列表 | 添加关闭任务列表的选项 |
| `AddNewGameChoices` | 新游戏 | 填充开局选择 |
| `NewGameOptionSelected` | 新游戏 | 处理新游戏选项回流 |

表格只用于判断规则应挂在哪类入口。每个 trigger 的原版调用类、精确派发顺序、规则记录数和特殊状态由 [C16-S3] 逐场景解释。

## 组合一段完整对话

### 用状态控制菜单

`$menuState` 和 `$tradeMode` 表示当前界面状态。舰队通讯菜单通常使用 `fleetConvMain`，市场主菜单使用 `main`；菜单规则根据这些值决定是否加入选项。

进入和退出要成对维护。进入通讯时写 `$menuState = fleetConvMain 0`，结束通讯时由 `EndConversation` 恢复舰队主菜单；市场与酒吧则在各自返回规则中复位状态。

### 用不同 fire 方法分工

FireAll 适合填充菜单和追加多段结果，因为所有匹配规则都参与；FireBest 适合问候、叙述和转场，因为同一次调用只需要一个胜出结果。

本页的回流脚本先 FireAll 追加通讯记录，再 FireBest 选择记录详情。两次调用使用不同自定义 trigger，职责不会混在同一个候选集合中。

### 用一次性 flag 限制重复结果

条件写 `!$player.GearIn_rules_apply_seen`，脚本写 `$player.GearIn_rules_apply_seen = true 0`，第一次命中后再次进入会改走回退规则。调试时可以用 `unset $player.GearIn_rules_apply_seen` 删除单个键，`unsetAll` 按前缀清理，devMode 下用 `DumpMemory` 查看实际槽位和值。

### 保持回流链闭合

一条可返回的 Rules 对话包含四段关系：`PopulateOptions` 提供入口，`DialogOptionSelected` 消费 `$option`，自定义 trigger 生成结果，返回选项再由 `DialogOptionSelected` 调用 `EndConversation`。任何一段 ID 断开，都会留下有文本但没有下一步的面板。

## 完整最小例

下面七条数据组成一段独立舰队通讯分支。它依赖原版通讯菜单已经处于 `fleetConvMain`，不依赖 S1 的 greeting。

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
!$player.GearIn_rules_apply_seen score:100","$player.GearIn_rules_apply_seen = true 0",通讯记录包含完整航行数据。,"GearIn_rules_apply_back:返回舰队菜单",首次高分结果
GearIn_rules_apply_detail_repeat,GearInRulesApplyDetail,$faction.id == independent,,这份通讯记录已经读取过。,"GearIn_rules_apply_back:返回舰队菜单",重复读取回退
GearIn_rules_apply_back,DialogOptionSelected,$option == GearIn_rules_apply_back,EndConversation,,,返回舰队菜单
```

## 验证方案

前置数据：MOD 的 `data/campaign/rules.csv` 已加入本页七条规则，玩家能够与一支非敌对独立舰队建立通讯。

1. 启动游戏，在日志中确认七个 `GearIn_rules_apply_*` ID 都出现 `Loading rule:`。
2. 打开非敌对独立舰队通讯，确认菜单出现 "检查通讯记录"。
3. 把显示文本改成 "读取记录" 并保持 ID 不变，重新进入后确认分支仍能执行。
4. 选择该选项，确认文本面板先出现 FireAll 的 "信号来源已经确认"，再出现 FireBest 的 "完整航行数据"。
5. 确认 `GearIn_rules_apply_add_b` 的文本没有出现。
6. 返回舰队菜单后再次进入并选择同一选项，确认一次性 flag 使 FireBest 改选 "已经读取过"。
7. 选择 "返回舰队菜单"，确认 `EndConversation` 恢复舰队主菜单。
8. 临时让两个详情规则同分，确认结果可在并列候选间变化；恢复 `score:100`。
9. 临时加入匹配 `$fireAllTrigger == GearInRulesApplyAdd` 的 `FireAllIntercept`，确认附加记录整段被拦截；随后移除拦截规则。
10. 给 FireBest 分别传入和省略 `keepOptions`，确认前者保留旧选项；再让 FireAll 目标规则产生选项，确认它清空并重建选项面板。

预期结果：

- 菜单入口只出现在非敌对独立舰队的 `fleetConvMain` 状态。
- 显示文本可以独立修改，稳定 ID 继续让 `$option` 命中回流规则。
- FireAll 只追加条件通过的 add_a，FireBest 首次选择 101 分的 detail_first。
- 一次性 flag 写入后，第二次由 1 分的 detail_repeat 接管。
- `GearIn_rules_apply_back → DialogOptionSelected → EndConversation` 把控制权交回舰队菜单。

## 故障分析

- **选项可见但选择后出现 `no rule found`**：options 中的 ID 与 `$option` 条件没有逐字对应。

    - **解决方案**：统一选项 ID 的前缀、大小写和空格。

- **选择记录后只有附加文本**：`GearInRulesApplyDetail` 没有任何候选通过条件。

    - **解决方案**：检查势力槽位、敌对状态和一次性 flag。

- **FireBest 在两个结果间变化**：最高总分相同，规则进入同分随机选择。

    - **解决方案**：用 `score:N` 建立确定的总分差。

- **FireAll 没有追加记录**：目标 trigger 被 `FireAllIntercept` 拦截或全部条件失败。

    - **解决方案**：检查 `$fireAllTrigger`、拦截条件和目标规则条件。

- **返回选项留下空白对话**：返回 ID 没有对应 `DialogOptionSelected` 规则。

    - **解决方案**：让 `GearIn_rules_apply_back` 命中执行 `EndConversation` 的处理器。

- **一次性结果反复出现**：条件与脚本使用了不同 Memory 槽位。

    - **解决方案**：两侧统一使用 `$player.GearIn_rules_apply_seen`。

## 总结

Rules 对话由几条明确关系组成：options 提供显示文本和稳定 ID，对话插件把 ID 写入 `$option`，`DialogOptionSelected` 调用自定义 trigger，FireAll 合并全部匹配结果，FireBest 用总分选出一个结果。状态键决定菜单处于哪一层，一次性 flag 决定同一分支是否继续使用首次结果，`EndConversation` 负责把控制权交回舰队菜单。

这组 `GearIn_rules_apply_*` 数据独立完成入口、回流、追加、竞争和返回。接入市场、舰队遭遇、酒吧、任务与新游戏时，继续使用相同的组合方法；原版每个 trigger 提供哪些对象、何时派发以及返回到哪里，由 [C16-S3] 逐场景说明。

## 建议参考项

- `data/campaign/rules.csv`：本节 `GearIn_rules_apply_*` 规则的数据文件。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：`$option` 写入和 `DialogOptionSelected` 回流。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：最高分选择和 `keepOptions`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：全部匹配、`$fireAllTrigger` 与 `FireAllIntercept`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.EndConversation`：通讯结果返回舰队菜单。
- `com.fs.starfarer.api.campaign.rules.RulesAPI`：匹配集合、最佳规则与下一次随机源。
- `com.fs.starfarer.api.util.Misc`：表达式 token 化和 Memory 变量解析。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：状态、过期和一次性 flag。
