# 接入 Rules 生涯场景

本节沿原版调用顺序解释 Rules trigger 在对话、市场、舰队、酒吧、任务和新游戏流程中的行为。选项回流、分数和 FireBest/FireAll 的基础语义见 [C16-S2]；这里把使用方、状态键、规则结果和界面变化放回各自场景。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 为对话、市场、舰队、酒吧、任务和新游戏流程选择对应的 trigger；
- 使用场景记忆与 `$option` 组织菜单和选项回流；
- 配合 `FireAll`、`FireBest` 与分数控制场景规则；
- 覆盖原版规则时保留原有结果职责；
- 根据场景状态和界面结果定位规则故障。

## 前置知识

- [C7-S1] 的 MOD 生命周期与规则文件加载边界；
- [C10-S1] 的 `addScript` 与持久数据判断；
- [C14-S3] 的 `showInteractionDialog` 与 `InteractionDialogPlugin` 生命周期；
- [C16-S1] 的 `rules.csv` 字段、加载和合并；
- [C16-S2] 的 `$option`、score、FireBest、FireAll 与组合写法；
- [C16-S4] 的自定义 Rules 命令；
- [C16-S5] 的 Memory 过期、持久化与槽位。

原版场景 trigger 由具体 Java 使用方在固定时机发起匹配，规则行再按这个字符串进入候选集合。`CoreCampaignPluginImpl` 写入的 facts 与规则脚本维护的 `$menuState`、`$tradeMode`、`$hailing` 等状态共同决定候选是否成立；酒吧临时选项、任务前缀和新游戏回流分别属于各自场景。

## 场景 trigger 关系

先看总览表确认各 trigger 的派发点与时机，再按对话、市场、舰队、酒吧、任务的顺序逐个展开，最后归纳跨场景的组合写法。

### 场景 trigger 总览速查表

下表只用于定位 trigger 所属场景和主要用途，具体调用方、触发顺序和界面结果放在各场景正文。

| 场景 | trigger | 主要用途 |
|---|---|---|
| 对话 | `OpenInteractionDialog`、`PopulateOptions`、`DialogOptionSelected` | 建立交互对话、填充选项、接收选项回流 |
| 对话 | `PickGreeting`、`OpenCommLink` | 选择人物问候和舰队通讯开场 |
| 市场 | `MarketPostOpen`、`MarketPostDock`、`TradePanelFlavorText` | 处理停靠、市场菜单和交易面板文本 |
| 舰队 | `BeginFleetEncounter`、`BeginFleetEncounter2` | 建立遭遇、Continue 和再次匹配 |
| 酒吧 | `BarShuttleDownPrintDesc`、`BarPrintDesc`、`AddBarEvents`、`ReturnFromBar`、`BarHighRepCrewGain` | 进入酒吧、重建临时选项、返回和奖励 |
| 任务 | `MHOpenText`、任务前缀 trigger、`AddMHCloseOption`、`MHPostMissionListText` | 展示任务列表、任务选项和酒吧任务 |
| 新游戏 | `BeginNewGameCreation`、`InitNewGameChoices`、`AddNewGameChoices`、`NewGameOptionSelected` | 建立新游戏选项并处理起点回流 |

联系人任务还会使用 `ContactPreMissionListText`、`AsebPreMissionListText`、`ContactPostAccept`、`ContactPrintAcceptText` 和 `ContactPrintDeclineText`。这些 trigger 不负责建立入口，而是在任务列表或接受、拒绝动作前后补充对应文本。

### 场景事实键的写入方

规则条件里最常见的场景事实键由 `CoreCampaignPluginImpl` 的 `update*Facts` 钩子在规则匹配前写入实体、市场、人物和势力 Memory：

- `updateEntityFacts`：`$tag:<tag>`、`$isHostile`(按声望档位)、`$hasMarket`(非纯条件市场)、`$hasStation`、`$marketSize`、`$stability`、`$planetType`；
- `updateMarketFacts`：`$tag:<tag>`、`$mc:<条件id>`、`$ind:<产业id>`、`$id`(市场 id) 等；
- `updatePersonFacts`：`$id`(人物 id)、`$isPerson`、`$isContact`、`$name` / `$personName`、`$rel` / `$relName`、`$rank` / `$post`、`$importance*`、`$level`、`$personality`、`$tag:<tag>` 等；
- `updateFactionFacts`：`$id`、`$friendlyToPlayer` / `$hostileToPlayer` / `$neutralToPlayer`、`$isHostile`。

`$menuState`、`$tradeMode` 和 `$hailing` 等状态键由规则脚本与原版 rulecmd 维护。`$tag:` 类条件则要求目标实体、市场或人物实际带有对应 tag；两类数据共同决定规则能否命中。

## 对话与通讯

对话场景是其余场景的底座，进入、填菜单、回收选项都在这里发生，市场、舰队与酒吧复用同一套机制。

### 建立对话并填充菜单

`RuleBasedInteractionDialogPluginImpl` 默认用 `OpenInteractionDialog` 初始化对话。无条件规则 `defaultOpenDialog` 负责 `ShowDefaultVisual`、`PrintDescription 3`、将 ESC 绑定到 `defaultLeave`，并添加 `defaultLeave:Leave`；实体规则则继续按目标事实分流。`beaconOpenDialog` 检查 `$tag:warning_beacon`，`marketOpen` 检查 `$hasMarket`，随后显示目标、打印描述并调用 `FireBest MarketPostOpen`。

菜单不会随着对话初始化自动出现。原版脚本调用 `FireAll PopulateOptions`，所有匹配规则再把选项合并进面板。市场规则通常检查 `$menuState == main`、`$hasMarket` 和 `$tradeMode != NONE`，舰队通讯检查 `$menuState == fleetConvMain`，人物对话则使用 `$isPerson`。`marketCommDir` 和 `marketAddOptionLeave` 没有 `$tradeMode != NONE` 条件，因此贸易关闭时仍可提供通讯目录与离开入口。

`PopulateOptions` 规则必须用场景状态限定。条件过宽时，选项会进入每一个调用该 trigger 的菜单；条件不可能成立时，选项则始终缺席。

### 接收选项回流

`RuleBasedInteractionDialogPluginImpl.optionSelected` 把选项 ID 写入本地 Memory 的 `$option`，设置 `expire 0`，再调用 `fireBest("DialogOptionSelected")`。大多数回流规则直接用 `$option == <id>` 绑定选项；少数规则使用状态旗标接管下一次选择。显示文本可以变化，ID 与条件中的值必须逐字一致。

酒吧字符串选项也由 `BarCMD.optionSelected` 写入 `$option` 并触发 `DialogOptionSelected`。主菜单的新游戏对话使用独立的 `NewGameOptionSelected`，同一个 `$option` 条件放错回流 trigger 后不会命中。

### 选择人物问候与舰队问候

通讯目录选择人物后，通讯面板先触发 `FireBest OpenCDE`。`convDefault` 再调用 `FireBest PickGreeting`，问候规则按人物 ID、`$voice`、`$postId` 和剧情 flag 选择人物视觉、文本与选项。NPC 主动请求通讯时，`NPCCommsRequest` 的 `npcCommsAccept` 规则也会把流程送入 `PickGreeting`。自定义入口可以直接把 `PickGreeting` 设为初始 trigger，也可以用带 score 的子 trigger 继续分派。

舰队通讯由 `FleetInteractionDialogPluginImpl` 创建 embedded `RuleBasedInteractionDialogPluginImpl`，设置 embedded mode 和对方指挥官，再触发 `OpenCommLink`。`greetingCGRweirdHM` 这类特殊问候规则也在这个 trigger 上参与匹配；没有规则匹配时，文本面板显示 "You try to establish a comm link, but get only static."。

原版问候会检查 `$faction.friendlyToPlayer`、`$faction.neutralToPlayer`、`$entity.isHostile`、`$entity.relativeStrength` 和 `$player.transponderOn`。友好分支把 `$menuState` 设为 `fleetConvMain` 并调用 `FireAll PopulateOptions`；应答器关闭分支提供 "Turn your transponder on"。`$entity.ignorePlayerCommRequests score:10000` 负责拒绝通讯，无条件的 `greetingFallthrough` 接住其余目标。

下面三条局部数据把舰队问候、菜单和返回动作连在一起：

```csv
GearIn_rules_scene_comm,OpenCommLink,"$faction.id == independent score:100
$player.transponderOn
!$entity.isHostile
!$player.gearInSceneCommed","$player.gearInSceneCommed = true 0
$menuState = fleetConvMain 0
FireAll PopulateOptions",GearIn 对话场景 (OpenCommLink)：对独立势力舰队的问候语。脚本把菜单状态置为 fleetConvMain 并 FireAll PopulateOptions 填充对话菜单。,,
GearIn_rules_scene_conv,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent",,GearIn 对话场景 (PopulateOptions)：舰队对话菜单选项，点选后经 DialogOptionSelected 回流执行 EndConversation 回到舰队菜单。,"GearIn_rules_scene_conv_end:结束通讯",
GearIn_rules_scene_conv_end,DialogOptionSelected,$option == GearIn_rules_scene_conv_end,EndConversation,,,
```

`GearIn_rules_scene_comm` 只在非敌对、开启应答器的独立舰队上首次命中。它写入 `fleetConvMain` 后，`GearIn_rules_scene_conv` 才会添加 "结束通讯"；该选项回流到 `_conv_end` 并执行 `EndConversation`。拒绝通讯规则仍以更高分优先，原版 `convOptionLeave` 也会按 `$isPerson` 提供 `cutCommLink`。

通讯问候仍使用原版文本时，应先检查拒绝通讯条件和应答器状态，再比较候选分数。`$option` 条件始终为 false 时，应确认写入它的插件触发了当前回流 trigger。

## 市场停靠与交易

市场对话完整经过进入、许可、菜单和退出。每个阶段都把状态交给下一个 trigger，因此覆盖其中一条规则时，后续菜单依赖的脚本结果也要继续产生。

### 从实体对话进入市场

停靠市场实体后，默认对话触发 `OpenInteractionDialog`。条件 `$hasMarket` 让 `marketOpen` 胜过普通实体规则，它依次执行 `ShowDefaultVisual`、`PrintDescription 3` 和 `FireBest MarketPostOpen`。

`MarketPostOpen` 先决定停靠许可和贸易模式。`marketPostOpenDefault` 要求开启应答器，打印许可文本，将 `$tradeMode` 设为 `OPEN`，再调用 `FireBest MarketPostDock`。应答器关闭时，规则族按市场规则继续分流：

- `marketPostOpenToOffIsFine` 将 `$tradeMode` 设为 `OPEN`；
- `marketPostOpenToOffSneak` 还检查 `!$faction.c:allowsTransponderOffTrade` 与 `!$market.mc:free_market`，将 `$tradeMode` 设为 `SNEAK`；
- `marketPostOpenToOffPatrols` 通过 `IsSeenByPatrols $faction.id true` 判断巡逻发现，将 `$tradeMode` 设为 `NONE`；
- `marketPostOpenNoTradeHostile` 用 `$market.playerHostileTimeout score:100` 处理敌对超时；
- `marketPostOpenDefaultNeutral` 只检查 `$hasMarket` 与 `$faction.isNeutralFaction score:100`，让中立势力继续停靠流程而不打印许可文本。

这些分支仍会调用 `MarketPostDock`。应答器状态改变的是贸易模式，不会截断市场菜单的建立。

### 建立市场菜单并复位状态

`MarketPostDock` 的无条件规则 `marketDock` 把 `$menuState` 设为 `main`，再调用 `FireAll PopulateOptions`。任务交付使用 `DeliveryMission checkCompletion score:100`，债务与雇佣兵检查分别使用 `MarketCMD checkDebtEffect score:1000` 和 `MarketCMD checkMercsLeaving score:500`；特殊势力规则也在这里竞争，命中后需要继续承担菜单初始化职责。

`PopulateOptions` 根据 `$menuState == main` 与 `$hasMarket` 添加市场菜单。`marketAddOptionCommDir` 提供 `marketCommDir`，`marketOptBar` 提供 `marketVisitBar`，`marketOptTradeMulti` 提供 `marketOpenCoreUI`，`marketAddOptionRepair1/2/3` 提供 `marketRepair`，`marketOptHostile` 提供 `marketConsiderHostile`，`marketAddOptionLeave` 提供离开选项。酒吧与交易还会检查 `$tradeMode != NONE`，中立势力和 `$market.noBar` 继续限制酒吧入口。

`EndConversation` 在普通市场对话中检查 `$hasMarket` 与本地 `$menuState`。缺少菜单状态时，它重新触发 `MarketPostOpen`；已有状态时，它调用 `FireAll PopulateOptions`。弃档使用 `AbandonMarketPluginImpl` 将 `$menuState` 设为 `main`、将 `$tradeMode` 设为 `NONE`、移除 `$hasMarket`，随后 `FireAll OpenInteractionDialog` 重建界面。

下面的局部规则只在首次停靠 Jangala 时插入一段文本，同时复刻 `marketDock` 的菜单职责：

```csv
GearIn_rules_scene_market,MarketPostDock,"$market.id == jangala score:100
!$player.gearInSceneMarketSeen","$player.gearInSceneMarketSeen = true 0
$menuState = main 0
FireAll PopulateOptions",GearIn 市场场景：首次停靠 jangala 时本规则以 101 分胜过原版 marketDock (0 分)。FireBest 只执行一条规则，所以脚本必须复刻原版职责 ($menuState + FireAll PopulateOptions)，否则市场菜单不会出现。,,
```

`!$player.gearInSceneMarketSeen` 与脚本中的 `$player.gearInSceneMarketSeen = true 0` 让规则只命中一次。第二次停靠时，原版规则重新接管。需要重演时可用 `unset $player.gearInSceneMarketSeen` 删除该键，devMode 下也可以用 `DumpMemory` 查看当前值。

::: caution 覆盖 FireBest 规则必须复刻结果职责

`FireBest` 只执行一条规则。覆盖 `marketDock` 时必须继续设置 `$menuState = main 0` 并调用 `FireAll PopulateOptions`；覆盖 `marketOpen` 时必须继续执行 `ShowDefaultVisual`、`PrintDescription 3` 和 `FireBest MarketPostOpen`。遗漏这些结果后，市场菜单会消失，停靠流程会停在空白对话中。
:::

### 限制交易面板命令

交易面板每次打开时，用自建的 memoryMap 和假 dialog 触发 `FireBest TradePanelFlavorText`。槽位包括 `local`、`faction`、`global`、`player`、`market` 和 `sourceMarket`；`$tradePanelMode` 由面板按 `MARKET`、`CARGO` 或 `SALVAGE` 写入 sector Memory，与停靠流程的 `$tradeMode` 是两个不同状态。规则再结合 `$global.tradePanelMode`、`$marketSize` 和 `$stability` 选择文本。

::: warning TradePanelFlavorText 接收假 dialog

交易面板文本只适合 `AddText` 等不依赖真实对话上下文的命令。视觉面板与 `DismissDialog` 需要真实对话对象，在这里会异常或无效。
:::

## 舰队遭遇与通讯

舰队遭遇与通讯共用同一套对话底座：`BeginFleetEncounter` 负责遭遇开局，`OpenCommLink` 与 `PickGreeting` 负责通讯与问候。

### 预检遭遇规则

`FleetInteractionDialogPluginImpl.reinit(withContinueOnRuleFound)` 先用 `getBestMatching(null, "BeginFleetEncounter", ...)` 预检候选。找到匹配项时，面板只显示 `REINIT_CONTINUE` 对应的 "Continue"；玩家继续后，`reinitPostContinue` 才真正调用 `fireBest("BeginFleetEncounter")`。本地 `$fidpi_addContinue` 为真时，第二次匹配改用 `BeginFleetEncounter2`。

初次遭遇也会创建 embedded 对话并触发 `BeginFleetEncounter`。规则脚本写入 `$fidpi_addContinue` 后，插件清空当前选项，只留下 "Continue"，让文本先显示完再重建舰队主菜单。

原版规则通常选择两种结果。`LPTitheCheck`、`mpm_pirateEncounter` 等规则用 `AddText` 写呼叫文本，并将 `$hailing` 设为 true；`customsInspectionScan` 只写文本，`tOffPatrolBegin` 则通过 `OpenComms` 直接进入通讯。普通回退规则 `initial_AnyFleet` 使用 `FleetDesc`，再把 `$shownFleetDescAlready` 设为 true，避免重复输出舰队描述。

`BeginFleetEncounter` 的 options 列不会控制最终菜单。规则执行后，舰队插件会在 INIT 路径清空并重建选项，因此这个 trigger 应通过 `$hailing`、`OpenComms` 或状态键影响随后菜单。

### 从呼叫进入通讯

`$hailing` 会把舰队主菜单中的 "Open a comm link" 高亮并改名为 "Accept the comm request"，随后该状态被移除。`HailPlayer` 是写入 `$hailing` 的 rulecmd；`OpenComms` 则把 `FleetInteractionDialogPluginImpl.directToComms` 设为 true，跳过主菜单直接建立通讯。

通讯规则把 `$menuState` 设为 `fleetConvMain` 后调用 `FireAll PopulateOptions`。`shrineFleetConvOption`、`academyFleetConvOption` 等规则读取 `$entity.<flag>` 添加专用选项，`convOptionLeave` 根据 `$isPerson` 添加 `cutCommLink`。`DialogOptionSelected` 中的 `convEnd` 再执行 `ShowDefaultVisual` 与 `EndConversation`，返回舰队主菜单。人物问候仍通过通讯目录或接触人入口进入 `PickGreeting`。

舰队规则可以读取 `$entity.relativeStrength`、`$entity.isHostile`、`$faction.id`、`$faction.friendlyToPlayer`、`$player.transponderOn`、`$entity.<自定义 flag>`、`$isPatrol` 和 `$sourceMarket.mc:<条件>`。`$entity.relativeStrength` 为正时表示对方更强；`$isPatrol` 与 source market 条件常用于应答器检查。

下面的局部规则按普通回退规则的模式写出舰队描述，再发起通讯请求：

```csv
GearIn_rules_scene_fleet_hail,BeginFleetEncounter,"$faction.id == independent score:100
!$entity.isHostile
!$player.gearInSceneHailed","$player.gearInSceneHailed = true 0
FleetDesc
$shownFleetDescAlready = true 0
AddText ""GearIn 舰队场景：一支独立势力舰队呼叫了您；请在菜单中选择接受通讯请求。""
$hailing = true 0",,,
```

规则命中后，文本面板先显示舰队描述与呼叫内容，舰队主菜单再把通讯入口改成 "Accept the comm request"。一次性 flag 让后续遭遇恢复原版行为；剧情规则仍可用更高分取得优先级。

没有出现呼叫文本时，应同时检查势力、敌对状态、一次性 flag 和候选分数。问候已经出现但无法返回主菜单时，应检查 `EndConversation` 对应的回流 ID。

## 酒吧选项与事件

酒吧使用市场菜单、Rules 临时选项和 Java 事件共同组成流程。`BarCMD` 同时是 rulecmd 与对话插件，负责重建酒吧菜单、判断选项类型并把控制权交给对应处理器。

### 进入酒吧并重建选项

市场菜单中的 `marketVisitBar` 由 `marketOptBar` 以 order 5 添加。该选项回流到 `bar_optionSel` 后，脚本依次调用 `FireBest BarShuttleDownPrintDesc`、`FireBest BarPrintDesc` 和 `BarCMD showOptions`。`BarPrintDesc` 规则使用 `ShowImageVisual` 与描述文本，部分势力提供专属图像。

`BarCMD.showOptions` 先清空选项面板，再调用 `AddBarEvent.clearTempEvents(market)` 清除上一轮临时数据，并执行 `FireAll AddBarEvents`。`AddBarEvent <optionId> <选项文本> <描述文本> [颜色]` 把结果写入市场 Memory 的 `$core_tempBarEvents`；选项与描述先完成 token 替换，blurb 进入文本面板，option 进入选项面板。

临时选项之后，`BarCMD` 追加 `PortsideBarData` 中的 active 事件，包括 Java `BaseBarEvent` 与 `HubMissionBarEventWrapper`。末尾的 `barLeave` 显示为 "Leave the bar" 并绑定 ESC，随后 `dialog.setPlugin(this)` 让 `BarCMD` 接管选择。

原版 `AddBarEvents` 规则通常用 `$market.id == <市场>` 与剧情 flag 限定事件。`gaMeetHegLieutenantOnCoatl` 依赖市场和剧情状态，`goToTheGABarEventOption` 还使用 `RollProbability 0.2`。每次展示酒吧菜单都会先清空再重建，因此临时选项不承担跨次进入的长期状态。

### 区分事件、离开和字符串选项

`BarCMD.optionSelected` 根据 optionData 的类型和 ID 选择后续行为。`PortsideBarEvent` 进入 `BarEventDialogPlugin`；`barLeave` 调用 `leaveBar()`；`barContinue` 调用 `showOptions(true)`。普通字符串选项与任务包装选项会先用 `dialog.setPlugin(originalPlugin)` 还原规则对话，再执行 `mem.set("$option", optionData, 0)` 和 `FireBest DialogOptionSelected`。

下面三条局部规则添加临时字符串选项，接收 `$option`，并通过 `BarCMD showOptions` 返回吧台：

```csv
GearIn_rules_scene_bar_add,AddBarEvents,$market.id == jangala,,AddBarEvent GearIn_rules_scene_bar "和 GearIn 演示员聊聊" "一个穿着测试连体服的 GearIn 演示员朝您招手，示意您过去坐坐。",,
GearIn_rules_scene_bar_pick,DialogOptionSelected,$option == GearIn_rules_scene_bar,,GearIn 酒吧场景：临时 bar 选项被点选后，BarCMD 写入 $option 并触发 DialogOptionSelected。选项与描述来自 AddBarEvent，存储在市场记忆 $core_tempBarEvents；每次进入酒吧 BarCMD 先清空再 FireAll AddBarEvents 重建。,GearIn_rules_scene_bar_back:回吧台,
GearIn_rules_scene_bar_back,DialogOptionSelected,$option == GearIn_rules_scene_bar_back,"BarCMD showOptions",,,
```

进入 Jangala 酒吧后，`_bar_add` 生成 "和 GearIn 演示员聊聊"。选择它会命中 `_bar_pick`，显示说明并提供 "回吧台"；`_bar_back` 再让 `BarCMD` 重建菜单。没有临时选项时，应检查 `$market.id` 和 `BarCMD.showOptions` 是否进入了 `AddBarEvents`；选择后面板空白时，应为回流规则提供后续 options 或菜单填充命令。

### 返回市场并处理奖励

事件对话通过 `BarCMD.returnFromEvent [true|false]` 返回酒吧，参数为 true 时先提供 "Continue"。离开酒吧会停止环境音、恢复 originalPlugin、执行 `ShowDefaultVisual`，再触发 `ReturnFromBar`。`marketReturnFromBar` 将 `$menuState` 设为 `main`，调用 `FireAll PopulateOptions`，并显示 "You leave the bar and take a shuttle back to your $shipOrFleet."。

高声望酒吧事件的 `BarHRESpeechB` 会调用 `FireAll BarHighRepCrewGain`。匹配规则按 `$player.fleetPoints`、`RollProbability` 和 `score:2` 分档，再通过 `AddCommodity crew N` 发放船员。

::: warning 临时选项与任务前缀属于不同识别路径

`RemoveBarEvent` 只删除 `$core_tempBarEvents` 中的临时选项，不影响 `PortsideBarData` 的 active 事件。`HubMissionBarEventWrapper` 的 option ID 以任务前缀开头，`getWrapperFor` 用 `startsWith` 识别它，`abortMissions` 会中止其他任务包装；自定义临时选项不得复用已注册任务的前缀。
:::

## 联系人任务

联系人任务由 Rules 菜单与 Java 任务对象共同完成。规则负责展示任务、接收选择和调用任务对象；`BaseMissionHub` 与 `HubMission` 保存任务内容和接受结果。

### 打开并填充任务列表

人物对话菜单中的 `addMHubOption` 检查 `$isPerson`、`MissionHubCMD hasHub`，再通过 `Call $mHub setMHOptionText` 写入 `$mh_openOptionText`，添加 `5:mh_open:$mh_openOptionText`。`$mHub` 指向 `BaseMissionHub.KEY`，玩家看到的默认文本是 "Do you have any work for me?"。

`mh_open` 回流到 `openMhubOptionSel` 后，脚本依次执行 `Call $mHub prepare`、`FireBest MHOpenText`、`Call $mHub listMissions` 和 `SetEnabled mh_open false`。`contactMHOpenText` 会继续调用 `ContactPreMissionListText`，根据 `$mh_count`、`$mh_firstInlineBlurb` 和 `$mHub_contactSuspended` 生成列表前文本；`asebMHOpenText` 使用 `AsebPreMissionListText` 处理 Sebestyen 的专用文本。

`BaseMissionHub.listMissions` 为每份 offered mission 依次调用 `FireBest <前缀>_blurb true` 和 `FireBest <前缀>_option true`，例如 `hijack_blurb` 与 `hijack_option`。列表末尾再调用 `FireBest AddMHCloseOption true`，添加 `mh_close` 并用 `SetShortcut mh_close "ESCAPE"` 绑定 ESC；启用 blurbs 时，`FireBest MHPostMissionListText` 还会补充 "So, how about it?" 等收尾文本。第二个参数 true 表示 keepOptions，使每份任务的选项能够留在同一个菜单中。

### 接受、拒绝并返回列表

任务选项通过 `DialogOptionSelected` 回流。`contactMissionAcceptGeneric` 检查 `$option == contact_accept`，再执行 `FireBest ContactPrintAcceptText`、`Call $mHub accept $missionId`、`FireBest ContactPostAccept` 和 `Call $mHub returnToList`。拒绝分支使用 `ContactPrintDeclineText` 写对应文本。

`BaseMissionHub.callEvent` 处理 `prepare`、`listMissions`、`returnToList`、`accept` 和 `doCleanup`；accept 最终调用 `mission.accept(dialog, memoryMap)`。规则通过 `$missionId` 与 `$mission` 读取当前任务，`SetActiveMission`、`ClearActiveMission` 或对话插件的 `setActiveMission` 负责切换当前任务对象。

任务 trigger 必须由 `mission.getTriggerPrefix()` 加 `_blurb`、`_option`、`_blurbBar` 或 `_optionBar` 组成。前缀不一致时，`BaseMissionHub.listMissions` 会显示 `No blurb found for <prefix>`，`FireBest` 返回 false，任务选项也不会出现。

### 在酒吧包装任务

`HubMissionBarEventWrapper.addPromptAndOption` 会暂时把 LOCAL 换成任务人物的 Memory，同时替换 ENTITY 与 PERSON_FACTION。它先调用 `mission.updateInteractionData(dialog, memoryMap)`，再执行 `FireBest <前缀>_blurbBar true` 和 `FireBest <前缀>_optionBar true`，最后恢复原有槽位。原版 `hijack_blurbBar` 与 `hijack_optionBar` 就使用这条路径。

玩家选择包装任务后，`BarCMD.optionSelected` 进入任务对话，`abortMissions` 中止没有被选择的其他包装。通讯面板的 missions 页签使用 Java `MissionBoardAPI` 与 `MissionAvailability.playerAccept`，属于另一套任务板流程。

## 新游戏流程

`NewGameDialogPluginImpl` 使用自己的 Memory 和回流 trigger。它不复用生涯对话的 `DialogOptionSelected`，因此新游戏选项需要单独接入。

### 建立起点选项

`init` 创建 memoryMap，LOCAL 指向角色数据实体的 Memory，同时提供 GLOBAL；Dev Mode 会写入 `$global.isDevMode`。进入 `CONTINUE_CHOICES` 后，插件触发 `BeginNewGameCreation`。`onNewGameCreationStart` 先执行 `NGCAddCredits 2000`，再调用 `FireAll InitNewGameChoices`；`initNewGameChoices` 随后调用 `FireAll AddNewGameChoices`，加入 `ngcWolf`、`ngcWayfarer` 与受 `$global.isDevMode` 限制的开发选项。

### 处理专属选项回流

玩家选择起点后，插件把 ID 写入 `$option`，设置 `expire 0`，再触发 `NewGameOptionSelected`。对应规则检查 `$option == ngcX`，通过 `NGCAddShip`、`NGCAddCredits`、`NGCSetDifficulty` 和 `NGCDone` 组合起始舰船、信用点、难度与完成动作。

下面两条局部规则只在 Dev Mode 添加 GearIn 起点，选择后额外增加 5000 信用点：

```csv
GearIn_rules_scene_ngc_add,AddNewGameChoices,$global.isDevMode,,,"6:GearIn_rules_scene_ngc:GearIn 演示起点：额外 5000 星币 (dev)",
GearIn_rules_scene_ngc_pick,NewGameOptionSelected,"$option == GearIn_rules_scene_ngc
$global.isDevMode",NGCAddCredits 5000,,,
```

`NGCAddCredits` 位于 `_pick` 回流规则，而不是 `_add` 菜单规则。`FireAll AddNewGameChoices` 会执行所有命中脚本，把奖励写在 `_add` 中会在选项出现时直接加钱。

::: warning 新游戏使用独立回流 trigger

`NewGameOptionSelected` 只由主菜单的新游戏对话触发，生涯对话使用 `DialogOptionSelected`。回流名称写错后，选项仍能显示，但选择时不会命中处理规则。
:::

## 组合场景状态

不同场景反复使用同一组组织方式。`$menuState` 与 `$tradeMode` 保存菜单状态，进入时赋值，退出时复位；`PopulateOptions`、`AddBarEvents` 和 `AddNewGameChoices` 使用 `FireAll` 合并所有匹配选项，`MarketPostOpen`、`MarketPostDock`、`BarPrintDesc`、`MHOpenText` 与 `PickGreeting` 使用 `FireBest` 选择叙述或转场。

options 列的 ID 必须与 `DialogOptionSelected` 或 `NewGameOptionSelected` 中的 `$option == <id>` 逐字一致。默认条件提供基础分数，MOD 示例使用 100 级分数胜过普通回退规则，同时让 1000 级剧情规则继续优先。一次性行为则在 conditions 中检查 `!$player.<flag>`，在 script 中写 `$player.<flag> = true 0`；重新演示时可用 `unset $player.<flag>`，`unsetAll` 用于按前缀批量删除。

## 组装 Rules 场景实现

本节的规则行追加到 MOD 的 `data/campaign/rules.csv`。市场、舰队、酒吧、任务和新游戏的使用方都是原版插件，Rules 数据会在这些入口触发时加入对应流程：

```text
mods/GearIndustry/
├── build.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── rules.csv                     # 追加本节拥有的 GearIn_rules_scene_* 行 (与 GearIn_rules_comm_*、GearIn_rules_apply_* 同文件共存)
└── jars\
```

## 完整最小例

将下面的数据追加到 MOD 的 `data/campaign/rules.csv`。每条记录都保持七列，局部片段中的规则在这里组成完整场景数据。

```csv
GearIn_rules_scene_market,MarketPostDock,"$market.id == jangala score:100
!$player.gearInSceneMarketSeen","$player.gearInSceneMarketSeen = true 0
$menuState = main 0
FireAll PopulateOptions",GearIn 市场场景：首次停靠 jangala 时本规则以 101 分胜过原版 marketDock (0 分)。FireBest 只执行一条规则，所以脚本必须复刻原版职责 ($menuState + FireAll PopulateOptions)，否则市场菜单不会出现。,,
GearIn_rules_scene_bar_add,AddBarEvents,$market.id == jangala,,AddBarEvent GearIn_rules_scene_bar "和 GearIn 演示员聊聊" "一个穿着测试连体服的 GearIn 演示员朝您招手，示意您过去坐坐。",,
GearIn_rules_scene_bar_pick,DialogOptionSelected,$option == GearIn_rules_scene_bar,,GearIn 酒吧场景：临时 bar 选项被点选后，BarCMD 写入 $option 并触发 DialogOptionSelected。选项与描述来自 AddBarEvent，存储在市场记忆 $core_tempBarEvents；每次进入酒吧 BarCMD 先清空再 FireAll AddBarEvents 重建。,GearIn_rules_scene_bar_back:回吧台,
GearIn_rules_scene_bar_back,DialogOptionSelected,$option == GearIn_rules_scene_bar_back,"BarCMD showOptions",,,
GearIn_rules_scene_fleet_hail,BeginFleetEncounter,"$faction.id == independent score:100
!$entity.isHostile
!$player.gearInSceneHailed","$player.gearInSceneHailed = true 0
FleetDesc
$shownFleetDescAlready = true 0
AddText ""GearIn 舰队场景：一支独立势力舰队呼叫了您；请在菜单中选择接受通讯请求。""
$hailing = true 0",,,
GearIn_rules_scene_comm,OpenCommLink,"$faction.id == independent score:100
$player.transponderOn
!$entity.isHostile
!$player.gearInSceneCommed","$player.gearInSceneCommed = true 0
$menuState = fleetConvMain 0
FireAll PopulateOptions",GearIn 对话场景 (OpenCommLink)：对独立势力舰队的问候语。脚本把菜单状态置为 fleetConvMain 并 FireAll PopulateOptions 填充对话菜单。,,
GearIn_rules_scene_conv,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent",,GearIn 对话场景 (PopulateOptions)：舰队对话菜单选项，点选后经 DialogOptionSelected 回流执行 EndConversation 回到舰队菜单。,"GearIn_rules_scene_conv_end:结束通讯",
GearIn_rules_scene_conv_end,DialogOptionSelected,$option == GearIn_rules_scene_conv_end,EndConversation,,,
GearIn_rules_scene_ngc_add,AddNewGameChoices,$global.isDevMode,,,"6:GearIn_rules_scene_ngc:GearIn 演示起点：额外 5000 星币 (dev)",
GearIn_rules_scene_ngc_pick,NewGameOptionSelected,"$option == GearIn_rules_scene_ngc
$global.isDevMode",NGCAddCredits 5000,,,
```

## 构建并载入规则

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

`rules.csv` 随 MOD 目录由加载器读取，构建仍用于确认 MOD 的现有 Java 产物可用。

### 验证方案

前置数据：已经启用包含本节 `data/campaign/rules.csv` 的 MOD，并追加 `GearIn_rules_scene_*` 规则行；启动 Dev Mode 以验证新游戏场景与开发选项。

1. 规则注册：启动游戏，在 `starsector.log` 中找到 `GearIn_rules_scene_market`、`_bar_add`、`_bar_pick`、`_bar_back`、`_fleet_hail`、`_comm`、`_conv`、`_conv_end`、`_ngc_add` 和 `_ngc_pick` 的 `Loading rule:` 行。
2. 市场场景：新建生涯，停靠 jangala：停靠许可文本后出现 `GearIn 市场场景：首次停靠 jangala...`，市场菜单完整 (通讯目录/酒吧/交易/Leave)；再次停靠 jangala：本节文本不再出现、菜单照常。
3. 酒吧场景：在 jangala 市场菜单点 "Take a shuttle down to visit a dockside bar"：出现酒吧描述文本与 "和 GearIn 演示员聊聊"；点选后出现本节酒吧文本与 "回吧台"；点 "回吧台" 选项重现；"Leave the bar"(ESC) 回到市场菜单。
4. 舰队场景：在超空间或星系内找一支非敌对独立舰队 (拾荒/商队) 并遭遇：出现呼叫文本；通讯选项高亮 "Accept the comm request"；接受后出现本节问候语与 "结束通讯"/"Cut the comm link"；点 "结束通讯" 回到舰队主菜单。
5. 任务/流程场景：主菜单新建游戏：角色创建对话框出现 `GearIn 演示起点：额外 5000 星币 (dev)`(顺序 6)；点选后选 `ngcWolf` 等原版起始；开局确认星币为原版初始加 5000。非 devMode 启动时该选项不出现。

预期结果：

- 规则注册：`starsector.log` 出现所有 `GearIn_rules_scene_*` 的 `Loading rule:` 行，启动器标准输出只保留变量单次使用 WARNING。
- 酒吧场景 (Jangala 市场菜单 → 酒吧)：进入酒吧显示 `BarPrintDesc` 描述文本与 "和 GearIn 演示员聊聊"；选中后出现本节酒吧文本与 "回吧台"，再次选择后重现酒吧菜单，"Leave the bar" 通过 `ReturnFromBar` 恢复市场菜单。
- 舰队场景：非敌对独立舰队遭遇时出现呼叫文本，通讯入口高亮为 "Accept the comm request"；接受后显示本节问候与 "结束通讯"/"Cut the comm link"，结束后回到舰队主菜单。
- 任务与新游戏场景：联系人任务列表使用匹配的前缀规则；Dev Mode 下出现 `GearIn 演示起点：额外 5000 星币 (dev)`，选择后追加 5000 信用点。
- 全程无 ERROR：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志，加载期无规则语法和命令注册异常。

## 故障分析

- **市场选项只剩 `Leave`**：覆盖 `MarketPostDock` 后没有复刻设置 `$menuState` 和填充选项的职责。
    - **解决方案**：执行 `$menuState = main 0` 与 `FireAll PopulateOptions`。

- **再次停靠时自定义文本消失**：一次性 Memory flag 已经置位，条件不再成立。
    - **解决方案**：用 `DumpMemory` 确认该键；需要重复展示时移除该 flag 或改用可重置条件。

- **酒吧没有临时选项**：`AddBarEvents` 的市场条件不匹配，或没有进入 `BarCMD.showOptions`。
    - **解决方案**：核对 `$market.id` 和酒吧入口是否执行了 `FireAll AddBarEvents`。

- **点击酒吧选项后面板空白**：回流规则只写了文本，没有添加后续选项。
    - **解决方案**：补充 options 列或调用菜单填充 trigger。

- **舰队遭遇没有自定义呼叫文本**：`BeginFleetEncounter` 候选分数低于剧情规则，或条件未命中。
    - **解决方案**：核对势力、敌对状态、一次性 flag 和 score。

- **通讯问候仍为原版**：拒绝通讯规则得分更高，或应答器条件不满足。
    - **解决方案**：按目标行为调整条件与 score，并保留原版拒绝规则的优先级。

- **任务列表显示 `No blurb found`**：规则 trigger 与 `getTriggerPrefix()` 生成的名称不一致。
    - **解决方案**：统一 `_blurb`、`_option`、`_blurbBar` 和 `_optionBar` 前缀。

- **新游戏对话没有演示选项**：规则没有挂在 `AddNewGameChoices`，或回流 trigger 写成了生涯对话版本。
    - **解决方案**：使用 `NewGameOptionSelected` 并满足 `$global.isDevMode` 条件。

- **交易面板规则报错**：`TradePanelFlavorText` 使用了依赖真实对话框的命令。
    - **解决方案**：只保留 `AddText` 等适用于假 dialog 的命令，并按 `$tradePanelMode` 检查文本条件。

- **对话只有文本没有选项**：`$menuState` 或 facts 未让 `PopulateOptions` 命中。
    - **解决方案**：核对当前 Memory 值、菜单状态和选项规则的场景条件。

- **规则未触发且日志无异常**：规则 ID 跨文件重复，或派发点的 trigger 拼写不一致。
    - **解决方案**：改用唯一前缀并逐字核对使用方传入的 trigger。

- **`$option` 条件始终为 false**：该条件写在非选项回流 trigger 中，没有任何插件写入 `$option`。
    - **解决方案**：把判断移到 `DialogOptionSelected`、`NewGameOptionSelected` 或酒吧字符串选项的回流路径。

## 总结

本节按对话、市场、舰队、酒吧、联系人任务和新游戏流程展开同一套 Rules 使用方式：原版派发点在固定时机发起匹配，规则行按条件与分数竞争，脚本负责文本、转场或菜单，插件再把结果交给玩家界面。场景 trigger 由原版使用方直接调用，规则行因此可以连接到既有流程。

排错时先确认 `Loading rule:` 日志，再确认 trigger 拼写和当前 facts，最后检查候选规则的脚本结果。`FireBest` 只执行一条，覆盖原版规则时要保留原有状态与菜单职责；`FireAll` 执行全部匹配项，菜单类 trigger 用它批量添加选项。`$menuState` 与 `$tradeMode` 在进入和退出时成对维护，`$option` 只在对应插件的回流 trigger 中有效，临时 bar 选项每次进入酒吧都会重建，任务 trigger 前缀要与 `getTriggerPrefix()` 保持一致，`TradePanelFlavorText` 只能使用适合假 dialog 的命令。

这套场景使用方式会继续连接 Rules 命令、Memory 生命周期和市场、舰队、酒吧对话插件；本页保留各场景的边界，后续页面分别展开这些机制。

## 建议参考项

- `data/campaign/rules.csv`：本节示例追加行与各类场景 trigger 的平台规则表；规则 id 前缀为 `GearIn_rules_scene_*`，并参照 `marketOpen` / `marketDock` / `marketPostOpenDefault` / `greetingFallthrough` / `initial_AnyFleet` / `marketReturnFromBar` 等回退规则。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：交互对话框插件，对话场景 trigger 的主要派发点与 `$option` 回流实现。
- `com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`：舰队遭遇与通讯对话插件，`BeginFleetEncounter` / `OpenCommLink` 的派发点。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧场景的 rulecmd 与对话插件，`AddBarEvents` / `ReturnFromBar` 派发与字符串选项回流。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`：写临时 bar 选项的 rulecmd(`$core_tempBarEvents` 记忆键)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub`：任务列表与任务对话 trigger(`<前缀>_blurb` / `<前缀>_option` / `AddMHCloseOption`) 的使用方。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：任务以酒吧事件展示时的 `<前缀>_blurbBar` / `<前缀>_optionBar` trigger 使用方。
- `com.fs.starfarer.api.impl.campaign.NewGameDialogPluginImpl`：主菜单新游戏对话框 (流程场景)，回流 trigger 为 `NewGameOptionSelected`。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：场景事实键 (`$hasMarket` / `$isHostile` / `$isContact` 等) 的写入方。
- `com.fs.starfarer.api.impl.campaign.rulecmd.EndConversation`：对话退出转场 rulecmd。
- `com.fs.starfarer.api.impl.campaign.rulecmd.OpenComms`：舰队通讯转场 rulecmd；
- `com.fs.starfarer.api.impl.campaign.rulecmd.HailPlayer`：`$hailing` 写入 rulecmd。
- `com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl`：弃档时的市场状态复位 (`$menuState` / `$tradeMode` / `$hasMarket`)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：单选派发与 keepOptions(第二参) 语义。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：规则记忆键常量 (`MISSION` 等)。
