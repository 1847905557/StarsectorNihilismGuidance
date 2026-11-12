# 接入 Rules 生涯场景

Rules 的语法可以复用，调用场景却决定了当前对象、菜单和返回方式。本节给 Jangala 停靠与酒吧、独立舰队遭遇和通讯，以及开发开局添加规则，沿实际进入与退出过程说明各个 trigger 的职责，联系人任务也结合原版入口展开。

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~40 分钟。_

## 学习目标

完成后，您将能够：

- 为对话、市场、舰队、酒吧、任务和新游戏流程选择对应的 `trigger`；
- 使用场景记忆与 `$option` 组织菜单和选项回流；
- 配合 `FireAll`、`FireBest` 与分数控制场景规则；
- 覆盖原版规则时保留原有结果职责；
- 根据场景状态和界面结果定位规则故障。

## 前置知识

- [C7-S1] 的 MOD 生命周期与规则文件加载边界；
- [C10-S1] 的 `addScript` 与持久数据判断；
- [C14-S3] 的 `showInteractionDialog` 与 `InteractionDialogPlugin` 生命周期；
- [C16-S1] 的 `rules.csv` 字段、加载和合并；
- [C16-S2] 的 `$option`、`score`、`FireBest`、`FireAll` 与组合写法；

## 让规则取得当前场景的对象

本节的 `GearIn_rules_scene_*` 成品追加到已有 `data/campaign/rules.csv`。场景在固定时机调用 trigger，规则用这个名字参加匹配，再按对象 facts 与菜单状态判断。相同菜单填充可以服务市场或通讯，条件要对应实际进入的场景。

### 场景 `trigger` 总览速查表

下面按场景定位入口，各段随后说明实际调用与结果。

| 场景   | `trigger`                                                                                        | 主要用途                             |
| ------ | ------------------------------------------------------------------------------------------------ | ------------------------------------ |
| 对话   | `OpenInteractionDialog`、`PopulateOptions`、`DialogOptionSelected`                               | 建立交互对话、填充选项、接收选项回流 |
| 对话   | `PickGreeting`、`OpenCommLink`                                                                   | 选择人物问候和舰队通讯开场           |
| 市场   | `MarketPostOpen`、`MarketPostDock`、`TradePanelFlavorText`                                       | 处理停靠、市场菜单和交易面板文本     |
| 舰队   | `BeginFleetEncounter`、`BeginFleetEncounter2`                                                    | 建立遭遇、`Continue` 和再次匹配      |
| 酒吧   | `BarShuttleDownPrintDesc`、`BarPrintDesc`、`AddBarEvents`、`ReturnFromBar`、`BarHighRepCrewGain` | 进入酒吧、重建临时选项、返回和奖励   |
| 任务   | `MHOpenText`、任务前缀 `trigger`、`AddMHCloseOption`、`MHPostMissionListText`                    | 展示任务列表、任务选项和酒吧任务     |
| 新游戏 | `BeginNewGameCreation`、`InitNewGameChoices`、`AddNewGameChoices`、`NewGameOptionSelected`       | 建立新游戏选项并处理起点回流         |

联系人任务还通过 `ContactPreMissionListText`、`AsebPreMissionListText`、`ContactPostAccept`、`ContactPrintAcceptText` 与 `ContactPrintDeclineText` 提供列表和接受、拒绝时的文字，实际任务仍从对应菜单进入。

### 让条件对应实际场景事实

规则对话建立 Memory 上下文时，生涯插件为对象更新 facts。`CoreCampaignPluginImpl` 提供以下常用键，条件按相应槽位读取：

- `updateEntityFacts`：`$tag:<tag>`、`$isHostile`、`$hasMarket`(非纯条件市场)、`$hasStation`、`$marketSize`、`$stability`、`$planetType`；舰队敌对判断可由 AI 提供，其他实体按势力关系档位处理；
- `updateMarketFacts`：`$tag:<tag>`、`$mc:<条件id>`、`$ind:<产业id>`、`$id`(市场 `id`) 等；
- `updatePersonFacts`：`$id`(人物 `id`)、`$isPerson`、`$isContact`、`$name` / `$personName`、`$rel` / `$relName`、`$rank` / `$post`、`$importance*`、`$level`、`$personality`、`$tag:<tag>` 等；
- `updateFactionFacts`：`$id`、`$friendlyToPlayer` / `$hostileToPlayer` / `$neutralToPlayer`、`$isHostile`。

菜单状态、贸易模式与呼叫状态由规则脚本和命令维护，标签条件则从实体、市场或人物的实际 tag 生成。读取最新 facts 与保存当前流程状态分别有入口，这些值共同决定当前规则能否参与。

## 对话与通讯

从地图进入规则对话时，插件先建立目标与 Memory，再查询初始 trigger。市场、舰队和酒吧都在这个会话中显示结果，各自的控制器继续安排后面的菜单。

### 建立对话并填充菜单

`RuleBasedInteractionDialogPluginImpl` 默认查询 `OpenInteractionDialog`。普通实体的 `defaultOpenDialog` 显示默认视觉、打印描述并提供绑定 ESC 的 Leave；警告信标由 `beaconOpenDialog` 的标签条件选择，市场实体由 `marketOpen` 的 `$hasMarket` 选择，随后进入 `MarketPostOpen`。

菜单由 `FireAll PopulateOptions` 实际填入。市场选项结合 `main`、`$hasMarket` 与贸易模式，通讯选项结合 `fleetConvMain`，人物选项结合 `$isPerson`。通讯目录与离开不要求贸易模式有效，贸易关闭时仍可以提供这两个动作。

**共用 trigger 的规则用场景条件限定。** 本节的通讯菜单行限定菜单状态与独立势力，市场菜单继续由原版规则完成，两组都可以参与同一次填充方式。

### 接收选项回流

规则对话收到选择后，将 ID 写入 LOCAL 的 `$option`，期限设为 0，再查询 `DialogOptionSelected`。条件使用相同 ID，也可以结合状态标记接管当前动作。显示文字继续独立，处理分支与实际数据对应。

酒吧字符串选项也由 `BarCMD` 写入并发起同类回流。新游戏对话另查 `NewGameOptionSelected`，因为它由自己的角色创建上下文处理选择。

### 选择人物问候与舰队问候

通讯目录选择人物后，当前实体将他设为活动人物，刷新人物上下文并查询 `OpenCDE`。`convDefault` 再调用 `PickGreeting`，人物 ID、声音、职务与剧情标记决定问候。主动通讯的 `npcCommsAccept` 先进入 `NPCCommsRequest`，默认规则再调用 `PickGreetingActive`。自定义入口也可以把问候设为初始 trigger，或继续分派子 trigger。

舰队通讯使用嵌入的规则对话，把对方指挥官设为活动人物，再查询 `OpenCommLink`。`greetingCGRweirdHM` 等特殊规则也参加这里的匹配。没有匹配时，插件显示 "You try to establish a comm link, but only get static."；原版 `greetingFallthrough` 则提供静噪文本与切断通讯选项。

原版问候结合势力关系、舰队敌对判断、遭遇评估与玩家应答器。友好结果设置 `fleetConvMain` 并填入菜单，应答器关闭时可提供开启选项。拒绝通讯的 `ignorePlayerCommRequests` 使用 10000 分，本节问候仍让这类结果优先。

下面三条局部数据把舰队问候、菜单和返回动作连在一起：

```csv
GearIn_rules_scene_comm,OpenCommLink,"$faction.id == independent score:100
$player.transponderOn
!$entity.isHostile
!$player.GearIn_scene_commed","$player.GearIn_scene_commed = true
$menuState = fleetConvMain 0
FireAll PopulateOptions",独立舰队回应了您的通讯，导航记录已经可以查阅。,,
GearIn_rules_scene_conv,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent",,通讯仍然保持连接。,"GearIn_rules_scene_conv_end:结束通讯",
GearIn_rules_scene_conv_end,DialogOptionSelected,$option == GearIn_rules_scene_conv_end,EndConversation,,,
```

`GearIn_rules_scene_comm` 只在非敌对、开启应答器的独立舰队上首次命中。它写入 `fleetConvMain` 后，`GearIn_rules_scene_conv` 才会添加 "结束通讯"；该选项回流到 `_conv_end` 并执行 `EndConversation`。拒绝通讯规则仍以更高分优先，原版 `convOptionLeave` 也会按 `$isPerson` 提供 `cutCommLink`。

问候与菜单沿同一目标和会话运行，回流仍由当前插件写入 `$option`。拒绝通讯、应答器与一次性标记各能影响这次候选，实际结果结合这些值和总分确认。

## 市场停靠与交易

Jangala 停靠实例从市场实体开始，先处理许可，再建立菜单。我们插入首次停靠文字，同时继续完成菜单所需状态。

### 从实体对话进入市场

停靠市场实体后，默认对话触发 `OpenInteractionDialog`。条件 `$hasMarket` 让 `marketOpen` 胜过普通实体规则，它依次执行 `ShowDefaultVisual`、`PrintDescription 3` 和 `FireBest MarketPostOpen`。

`MarketPostOpen` 决定许可与贸易模式。普通开启应答器的路径将 `$tradeMode` 设为 `OPEN`，显示许可后进入 `MarketPostDock`。关闭应答器时继续按条件分流：

- `marketPostOpenToOffIsFine` 将 `$tradeMode` 设为 `OPEN`；
- `marketPostOpenToOffSneak` 还检查 `!$faction.c:allowsTransponderOffTrade` 与 `!$market.mc:free_market`，将 `$tradeMode` 设为 `SNEAK`；
- `marketPostOpenToOffPatrols` 通过 `IsSeenByPatrols $faction.id true` 判断巡逻发现，将 `$tradeMode` 设为 `NONE`；
- `marketPostOpenNoTradeHostile` 用 `$market.playerHostileTimeout score:100` 处理敌对超时；
- `marketPostOpenDefaultNeutral` 只检查 `$hasMarket` 与 `$faction.isNeutralFaction score:100`，让中立势力继续停靠流程而不打印许可文本。

这些路径仍调用停靠结果，模式分别为 `OPEN`、`SNEAK` 或 `NONE`。菜单规则再按这个状态提供合法贸易、潜入交易或当前可用的其他动作。

### 建立市场菜单并复位状态

默认 `marketDock` 设置 `main` 并填入菜单。任务交付、债务与雇佣兵离队也在 `MarketPostDock` 竞争，相关命令分别使用 100、1000 与 500 分，再由自己的对话结果接续流程。特殊势力与剧情也可以在这里获选。

菜单中的通讯目录、酒吧、交易、维修、军事动作与离开分别来自 `marketAddOptionCommDir`、`marketOptBar`、`marketOptTradeMulti`、`marketAddOptionRepair1/2/3`、`marketOptHostile` 和 `marketAddOptionLeave`。对应数据为 `marketCommDir`、`marketVisitBar`、`marketOpenCoreUI`、`marketRepair`、`marketConsiderHostile` 与离开键。酒吧和交易还要求模式不为 `NONE`，酒吧继续检查中立势力与 `noBar`。

`EndConversation` 在普通市场对话中检查 `$hasMarket` 与本地 `$menuState`。缺少菜单状态时，它重新触发 `MarketPostOpen`；已有状态时，它调用 `FireAll PopulateOptions`。放弃殖民地使用 `AbandonMarketPluginImpl` 将 `$menuState` 设为 `main`、将 `$tradeMode` 设为 `NONE`、移除 `$hasMarket`，随后 `FireAll OpenInteractionDialog` 重建界面。

下面的局部规则只在首次停靠 Jangala 时插入一段文本，同时复刻 `marketDock` 的菜单职责：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_scene_market,MarketPostDock,"$market.id == jangala score:100
!$player.GearIn_scene_market_seen","$player.GearIn_scene_market_seen = true
$menuState = main 0
FireAll PopulateOptions",Jangala 的港口人员确认了您的停靠，交易与通信入口已准备就绪。,,
```

命中后写入永久玩家标记，第二次停靠恢复其他规则选择。重演时删除 `$player.GearIn_scene_market_seen`，开发模式用 `DumpMemory` 核对。当前规则恢复 `main` 与菜单，插入的文字因而仍有后续操作。

**覆盖的是整份结果，不只是文字。** 替换 marketDock 还要设置 `main` 与调用菜单，替换 marketOpen 还要显示默认视觉、打印描述并进入许可处理。

::: details 停靠问候与结算的优先级
本节用覆盖展示菜单的结果职责，市场规则总分为 101；原版正常交付检查也可能取得 101 分，出现并列。验证这段问候时，使用没有待交付、债务或特殊停靠流程的测试状态。

编者不认为一段港口问候值得优先于任务结算。正式内容应当给结算留下位置，或把附加叙述安排到已有流程可接续的入口；增加分数本身并没有承接被压过的那份工作。
:::

### 限制交易面板命令

交易面板打开时，自行建立 `local`、`faction`、`global`、`player`，以及适用的 `market` 与 `sourceMarket`，再查询 `TradePanelFlavorText`。`$global.tradePanelMode` 表示 `MARKET`、`CARGO` 或 `SALVAGE`，与停靠时的贸易许可不同；规则可结合市场规模、稳定度取得文字。

::: warning TradePanelFlavorText 接收受限的交易文本上下文

当前上下文提供文本面板与目标，视觉和选项面板为空，关闭方法也不结束交易。使用 `AddText` 等文本命令，才能让结果作用于实际提供的区域。
:::

## 舰队遭遇与通讯

本节的独立舰队先在遭遇时呼叫玩家，再进入通讯。遭遇开局使用 `BeginFleetEncounter`，通讯问候使用前面的 `OpenCommLink`，两者还要配合舰队插件自己的主菜单。

### 预检遭遇规则

`FleetInteractionDialogPluginImpl.reinit(withContinueOnRuleFound)` 先用 `getBestMatching(null, "BeginFleetEncounter", ...)` 预检候选。找到匹配项时，面板只显示 `REINIT_CONTINUE` 对应的 "`Continue`"；玩家继续后，`reinitPostContinue` 才真正调用 `fireBest("BeginFleetEncounter")`。本地 `$fidpi_addContinue` 为真时，第二次匹配改用 `BeginFleetEncounter2`。

初次遭遇也会创建 `embedded` 对话并触发 `BeginFleetEncounter`。规则脚本写入 `$fidpi_addContinue` 后，插件清空当前选项，只留下 "`Continue`"，让文本先显示完再重建舰队主菜单。

原版规则通常选择两种结果。`LPTitheCheck`、`mpm_pirateEncounter` 等规则用 `AddText` 写呼叫文本，并将 `$hailing` 设为 `true`；`customsInspectionScan` 只写文本，`tOffPatrolBegin` 则通过 `OpenComms` 直接进入通讯。普通回退规则 `initial_AnyFleet` 使用 `FleetDesc`，再把 `$shownFleetDescAlready` 设为 `true`，避免重复输出舰队描述。

**遭遇规则提供后续菜单读取的状态。** 执行结果后，舰队插件在 `INIT` 中重建主菜单，规则自带选项不会保留为最终菜单。呼叫标记、`OpenComms` 与继续状态分别影响接下来的动作。

### 从呼叫进入通讯

`$hailing` 让主菜单把 "Open a comm link" 改为高亮的 "Accept the comm request"，随后移除这次标记。`HailPlayer` 写入呼叫，`OpenComms` 设置 `directToComms`，让舰队插件直接进入通讯。

通讯规则把 `$menuState` 设为 `fleetConvMain` 后调用 `FireAll PopulateOptions`。`shrineFleetConvOption`、`academyFleetConvOption` 等规则读取 `$entity.<flag>` 添加专用选项，`convOptionLeave` 根据 `$isPerson` 添加 `cutCommLink`。`DialogOptionSelected` 中的 `convEnd` 再执行 `ShowDefaultVisual` 与 `EndConversation`，返回舰队主菜单。人物问候仍通过通讯目录或接触人入口进入 `PickGreeting`。

舰队规则可以读取敌对判断、势力、应答器、自有实体标记、巡逻身份与来源市场条件。`relativeStrength` 是 AI 遭遇选择的映射：交战为 1，脱离或面对更强目标保持位置为 -1，普通保持为 0；它不是两支舰队 FP 的差值。`$isPatrol` 与 `$sourceMarket.mc:<条件>` 则用于巡逻和应答器检查。

下面的局部规则按普通回退规则的模式写出舰队描述，再发起通讯请求：

```csv
GearIn_rules_scene_fleet_hail,BeginFleetEncounter,"$faction.id == independent score:100
!$entity.isHostile
!$player.GearIn_scene_hailed","$player.GearIn_scene_hailed = true
FleetDesc
$shownFleetDescAlready = true 0
AddText ""独立舰队发来了通讯请求。""
$hailing = true 0",,,
```

命中后显示舰队描述与呼叫，主菜单提供接受通讯。玩家永久标记让后续遭遇恢复其他规则，剧情候选仍按更高分选择。

没有出现呼叫文本时，应同时检查势力、敌对状态、一次性 `flag` 和候选分数。问候已经出现但无法返回主菜单时，应检查 `EndConversation` 对应的回流 ID。

## 酒吧选项与事件

Jangala 酒吧实例添加一条临时邀请，选择后显示通讯记录说明，再返回吧台。它通过 Rules 字符串选项接入，与 [C15-S3] 的 Java 事件同时由 `BarCMD` 管理。

### 进入酒吧并重建选项

市场菜单中的 `marketVisitBar` 由 `marketOptBar` 以 `order` 5 添加。该选项回流到 `bar_optionSel` 后，脚本依次调用 `FireBest BarShuttleDownPrintDesc`、`FireBest BarPrintDesc` 和 `BarCMD showOptions`。`BarPrintDesc` 规则使用 `ShowImageVisual` 与描述文本，部分势力提供专属图像。

`BarCMD.showOptions` 先清空选项面板，再调用 `AddBarEvent.clearTempEvents(market)` 清除上一轮临时数据，并执行 `FireAll AddBarEvents`。`AddBarEvent <optionId> <选项文本> <描述文本> [颜色]` 把结果写入市场 Memory 的 `$core_tempBarEvents`；选项与描述先完成 `token` 替换，`blurb` 进入文本面板，`option` 进入选项面板。

临时选项之后，酒吧追加活动 Java 事件与任务 wrapper，再提供绑定 ESC 的 "Leave the bar"。`dialog.setPlugin(this)` 让 `BarCMD` 接管这些选择，后续按对象或字符串类型转交。

原版 `AddBarEvents` 规则通常用 `$market.id == <市场>` 与剧情 `flag` 限定事件。`gaMeetHegLieutenantOnCoatl` 依赖市场和剧情状态，`goToTheGABarEventOption` 还使用 `RollProbability 0.2`。每次展示酒吧菜单都会先清空再重建，因此临时选项不承担跨次进入的长期状态。

### 区分事件、离开和字符串选项

`BarCMD.optionSelected` 根据 `optionData` 的类型和 ID 选择后续行为。`PortsideBarEvent` 进入 `BarEventDialogPlugin`；`barLeave` 调用 `leaveBar()`；`barContinue` 调用 `showOptions(true)`。普通字符串选项与任务包装选项会先用 `dialog.setPlugin(originalPlugin)` 还原规则对话，再执行 `mem.set("$option", optionData, 0)` 和 `FireBest DialogOptionSelected`。

下面三条局部规则添加临时字符串选项，接收 `$option`，并通过 `BarCMD showOptions` 返回吧台：

```csv
GearIn_rules_scene_bar_add,AddBarEvents,$market.id == jangala,"AddBarEvent GearIn_rules_scene_bar ""和 GearIn 演示员聊聊"" ""一个穿着测试连体服的 GearIn 演示员朝您招手，示意您过去坐坐。""",,,
GearIn_rules_scene_bar_pick,DialogOptionSelected,$option == GearIn_rules_scene_bar,,演示员放下终端，向您介绍刚刚整理的通讯记录。,GearIn_rules_scene_bar_back:回吧台,
GearIn_rules_scene_bar_back,DialogOptionSelected,$option == GearIn_rules_scene_bar_back,"BarCMD showOptions",,,
```

进入 Jangala 酒吧后，`_bar_add` 生成 "和 `GearIn` 演示员聊聊"。选择它会命中 `_bar_pick`，显示说明并提供 "回吧台"；`_bar_back` 再让 `BarCMD` 重建菜单。没有临时选项时，应检查 `$market.id` 和 `BarCMD.showOptions` 是否进入了 `AddBarEvents`；选择后面板空白时，应为回流规则提供后续 `options` 或菜单填充命令。

### 返回市场并处理奖励

`BarCMD returnFromEvent [true|false]` 返回酒吧，`true` 先提供 Continue。离开酒吧则停止环境音，恢复原规则插件与视觉，查询 `ReturnFromBar`。默认结果设置 `main`、填入市场菜单，并显示 "You leave the bar and take a shuttle back to your $shipOrFleet."。

高声望酒吧事件的 `BarHRESpeechB` 调用 `FireAll BarHighRepCrewGain`。舰队点数与概率条件决定哪些船员奖励匹配，通过的 `AddCommodity crew N` 都执行；这里的 `score:2` 没有让高分档淘汰其他匹配项，因为当前使用的是 FireAll。

::: warning 临时选项与任务前缀属于不同识别路径

`RemoveBarEvent` 只删除 `$core_tempBarEvents` 中的临时选项，不影响 `PortsideBarData` 的 `active` 事件。`HubMissionBarEventWrapper` 的 `option` ID 以任务前缀开头，`getWrapperFor` 用 `startsWith` 识别它，`abortMissions` 会中止其他任务包装；自定义临时选项不得复用已注册任务的前缀。
:::

## 联系人任务

联系人在当前人物会话中提供任务。Rules 显示邀请与选择，`BaseMissionHub` 准备和接受任务对象，每份 `HubMission` 继续保存自己的内容与结果。

### 打开并填充任务列表

人物菜单的 `addMHubOption` 检查人物与任务 hub，调用 `setMHOptionText` 准备文字，添加顺序 5 的 `mh_open`。`$mHub` 使用 `BaseMissionHub.KEY`，默认文本为 "Do you have any work for me?"。

`mh_open` 回流到 `openMhubOptionSel` 后，脚本依次执行 `Call $mHub prepare`、`FireBest MHOpenText`、`Call $mHub listMissions` 和 `SetEnabled mh_open false`。`contactMHOpenText` 会继续调用 `ContactPreMissionListText`，根据 `$mh_count`、`$mh_firstInlineBlurb` 和 `$mHub_contactSuspended` 生成列表前文本；`asebMHOpenText` 使用 `AsebPreMissionListText` 处理 `Sebestyen` 的专用文本。

`listMissions(...)` 为提供规则描述的任务调用 `<前缀>_blurb` 与 `<前缀>_option`，例如 `hijack_blurb`、`hijack_option`；直接提供共用 blurb 的任务另由 hub 组合文字。末尾通过 `AddMHCloseOption` 加入绑定 ESC 的 `mh_close`，启用描述时再调用 `MHPostMissionListText`。这些选项调用采用 `true` 保留菜单，前面列出的任务也就不会被后一份覆盖。

### 接受、拒绝并返回列表

任务选项通过 `DialogOptionSelected` 回流。`contactMissionAcceptGeneric` 检查 `$option == contact_accept`，再执行 `FireBest ContactPrintAcceptText`、`Call $mHub accept $missionId`、`FireBest ContactPostAccept` 和 `Call $mHub returnToList`。拒绝分支使用 `ContactPrintDeclineText` 写对应文本。

`BaseMissionHub.callEvent` 处理 `prepare`、`listMissions`、`returnToList`、`accept` 和 `doCleanup`；`accept` 最终调用 `mission.accept(dialog, memoryMap)`。规则通过 `$missionId` 与 `$mission` 读取当前任务，`SetActiveMission`、`ClearActiveMission` 或对话插件的 `setActiveMission` 负责切换当前任务对象。

任务 `trigger` 必须由 `mission.getTriggerPrefix()` 加 `_blurb`、`_option`、`_blurbBar` 或 `_optionBar` 组成。前缀不一致时，`BaseMissionHub.listMissions` 会显示 `No blurb found for <prefix>`，`FireBest` 返回 `false`，任务选项也不会出现。

### 在酒吧包装任务

任务 wrapper 有人物时，暂时将 LOCAL 切到任务人物，保存实体并提供人物势力。更新任务交互数据后调用 `_blurbBar` 与 `_optionBar`，再恢复原局部上下文。`hijack_blurbBar` 与 `hijack_optionBar` 使用这条路径，[C15-S5] 展示了对应的自有接入。

玩家选择包装任务后，`BarCMD.optionSelected` 进入任务对话，`abortMissions` 中止没有被选择的其他包装。通讯面板的 `missions` 页签使用 Java `MissionBoardAPI` 与 `CampaignMissionPlugin.playerAccept`，属于另一套任务板流程。

## 新游戏流程

主菜单的新游戏对话使用角色创建 Memory，选项通过 `NewGameOptionSelected` 处理。本节的开发选项在这个上下文中增加起始信用，不等待生涯建立。

### 建立起点选项

`init` 创建 `memoryMap`，LOCAL 指向角色数据实体的 Memory，同时提供 GLOBAL；`Dev` `Mode` 会写入 `$global.isDevMode`。进入 `CONTINUE_CHOICES` 后，插件触发 `BeginNewGameCreation`。`onNewGameCreationStart` 先执行 `NGCAddCredits 2000`，再调用 `FireAll InitNewGameChoices`；`initNewGameChoices` 随后调用 `FireAll AddNewGameChoices`，加入 `ngcWolf`、`ngcWayfarer` 与受 `$global.isDevMode` 限制的开发选项。

### 处理专属选项回流

玩家选择起点后，插件把 ID 写入 `$option`，设置 `expire 0`，再触发 `NewGameOptionSelected`。对应规则检查 `$option == ngcX`，通过 `NGCAddShip`、`NGCAddCredits`、`NGCSetDifficulty` 和 `NGCDone` 组合起始舰船、信用点、难度与完成动作。

下面两条局部规则只在 `Dev` `Mode` 添加 `GearIn` 起点，选择后额外增加 5000 信用点：

```csv
GearIn_rules_scene_ngc_add,AddNewGameChoices,$global.isDevMode,,,"6:GearIn_rules_scene_ngc:GearIn 演示起点：额外 5000 星币 (dev)",
GearIn_rules_scene_ngc_pick,NewGameOptionSelected,"$option == GearIn_rules_scene_ngc
$global.isDevMode",NGCAddCredits 5000,,,
```

`NGCAddCredits` 放在 `_pick`，玩家选择时才增加金额。菜单由 FireAll 填入，把奖励写在 `_add` 中，会在选项显示时直接发放。当前开发选项没有一次性标记，每次选择都增加 5000；验证只选择一次，再继续原版起始舰船流程。

::: warning 新游戏使用独立回流 trigger

`NewGameOptionSelected` 只由主菜单的新游戏对话触发，生涯对话使用 `DialogOptionSelected`。回流名称写错后，选项仍能显示，但选择时不会命中处理规则。
:::

## 保持场景进入与返回对应

菜单状态随场景赋值与恢复。`PopulateOptions`、`AddBarEvents` 和 `AddNewGameChoices` 合并结果，许可、问候、停靠与任务文本则选择当前胜者；调用方式与结果职责一起决定画面。

`options` 列的 ID 必须与 `DialogOptionSelected` 或 `NewGameOptionSelected` 中的 `$option == <id>` 逐字一致。默认条件提供基础分数，MOD 示例使用 100 级分数胜过普通回退规则，同时让 1000 级剧情规则继续优先。一次性行为在 `conditions` 检查玩家标记，在 `script` 无期限写入 `true`；当前流程临时状态继续按用途设置期限。重新演示时可用 `unset $player.<flag>`，`unsetAll` 用于按前缀批量删除。

## 组装 Rules 场景实现

本节十条记录追加到既有规则文件，市场、舰队、酒吧和新游戏入口按原版场景触发。当前文件位置如下：

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
!$player.GearIn_scene_market_seen","$player.GearIn_scene_market_seen = true
$menuState = main 0
FireAll PopulateOptions",Jangala 的港口人员确认了您的停靠，交易与通信入口已准备就绪。,,
GearIn_rules_scene_bar_add,AddBarEvents,$market.id == jangala,"AddBarEvent GearIn_rules_scene_bar ""和 GearIn 演示员聊聊"" ""一个穿着测试连体服的 GearIn 演示员朝您招手，示意您过去坐坐。""",,,
GearIn_rules_scene_bar_pick,DialogOptionSelected,$option == GearIn_rules_scene_bar,,演示员放下终端，向您介绍刚刚整理的通讯记录。,GearIn_rules_scene_bar_back:回吧台,
GearIn_rules_scene_bar_back,DialogOptionSelected,$option == GearIn_rules_scene_bar_back,"BarCMD showOptions",,,
GearIn_rules_scene_fleet_hail,BeginFleetEncounter,"$faction.id == independent score:100
!$entity.isHostile
!$player.GearIn_scene_hailed","$player.GearIn_scene_hailed = true
FleetDesc
$shownFleetDescAlready = true 0
AddText ""独立舰队发来了通讯请求。""
$hailing = true 0",,,
GearIn_rules_scene_comm,OpenCommLink,"$faction.id == independent score:100
$player.transponderOn
!$entity.isHostile
!$player.GearIn_scene_commed","$player.GearIn_scene_commed = true
$menuState = fleetConvMain 0
FireAll PopulateOptions",独立舰队回应了您的通讯，导航记录已经可以查阅。,,
GearIn_rules_scene_conv,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent",,通讯仍然保持连接。,"GearIn_rules_scene_conv_end:结束通讯",
GearIn_rules_scene_conv_end,DialogOptionSelected,$option == GearIn_rules_scene_conv_end,EndConversation,,,
GearIn_rules_scene_ngc_add,AddNewGameChoices,$global.isDevMode,,,"6:GearIn_rules_scene_ngc:GearIn 演示起点：额外 5000 星币 (dev)",
GearIn_rules_scene_ngc_pick,NewGameOptionSelected,"$option == GearIn_rules_scene_ngc
$global.isDevMode",NGCAddCredits 5000,,,
```

## 保存数据并重新加载

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

`rules.csv` 随 MOD 目录由加载器读取，构建仍用于确认 MOD 的现有 Java 产物可用。

## 验证方案

前置数据：已经启用包含本节 `data/campaign/rules.csv` 的 MOD，并追加 `GearIn_rules_scene_*` 规则行；启动 `Dev` `Mode` 以验证新游戏场景与开发选项。

1. 启动游戏，确认十个 `GearIn_rules_scene_*` ID 开始解析且规则加载完成。
2. 使用没有待交付、债务与特殊停靠流程的新档，开启应答器停靠 Jangala。确认本节首次停靠文字与完整市场菜单，再停靠时原版接续菜单。
3. 进入 Jangala 酒吧，选择 "和 GearIn 演示员聊聊"，取得记录说明与 "回吧台"，返回后再用 "Leave the bar" 或 ESC 回到市场菜单。
4. 遭遇非敌对独立舰队，确认呼叫与高亮的 "Accept the comm request"，接受后使用 "结束通讯" 返回舰队主菜单。
5. 开发模式新建游戏，只选择一次额外 5000 的选项，再完成原版起始舰船选择，确认初始信用增加 5000；普通模式核对这个开发选项的条件。

预期结果：

- 规则注册：`starsector.log` 出现所有 `GearIn_rules_scene_*` 的 `Loading rule:` 行，启动器标准输出只保留变量单次使用 WARNING。
- 酒吧显示本节邀请，选择后取得记录说明与返回选项，离开酒吧通过 `ReturnFromBar` 恢复市场菜单。
- 独立舰队发起呼叫，接受通讯后显示本节问候与 "结束通讯"、"Cut the comm link"，结束后返回舰队主菜单。
- 任务与新游戏场景：联系人任务列表使用匹配的前缀规则；`Dev` `Mode` 下出现 `GearIn 演示起点：额外 5000 星币 (dev)`，选择后追加 5000 信用点。
- 加载日志显示各规则 ID，场景入口按目标条件形成文本、选项与返回结果。

## 故障分析

- **市场选项只剩 `Leave`**：覆盖 `MarketPostDock` 后没有复刻设置 `$menuState` 和填充选项的职责。
    - **解决方案**：执行 `$menuState = main 0` 与 `FireAll PopulateOptions`。

- **再次停靠时自定义文本消失**：一次性 Memory `flag` 已经置位，条件不再成立。
    - **解决方案**：用 `DumpMemory` 确认该键；需要重复展示时移除该 `flag` 或改用可重置条件。

- **酒吧没有临时选项**：`AddBarEvents` 的市场条件不匹配，或没有进入 `BarCMD.showOptions`。
    - **解决方案**：核对 `$market.id` 和酒吧入口是否执行了 `FireAll AddBarEvents`。

- **点击酒吧选项后面板空白**：回流规则只写了文本，没有添加后续选项。
    - **解决方案**：补充 `options` 列或调用菜单填充 `trigger`。

- **舰队遭遇没有自定义呼叫文本**：`BeginFleetEncounter` 候选分数低于剧情规则，或条件未命中。
    - **解决方案**：核对势力、敌对状态、一次性 `flag` 和 `score`。

- **通讯问候仍为原版**：拒绝通讯规则得分更高，或应答器条件不满足。
    - **解决方案**：按目标行为调整条件与 `score`，并保留原版拒绝规则的优先级。

- **任务列表显示 `No blurb found`**：规则 `trigger` 与 `getTriggerPrefix()` 生成的名称不一致。
    - **解决方案**：统一 `_blurb`、`_option`、`_blurbBar` 和 `_optionBar` 前缀。

- **新游戏对话没有演示选项**：规则没有挂在 `AddNewGameChoices`，或回流 `trigger` 写成了生涯对话版本。
    - **解决方案**：使用 `NewGameOptionSelected` 并满足 `$global.isDevMode` 条件。

- **交易面板规则报错**：`TradePanelFlavorText` 使用了依赖真实对话框的命令。
    - **解决方案**：使用 `AddText` 等适用于当前文本面板的命令，并按 `$global.tradePanelMode` 核对场景条件。

- **对话只有文本没有选项**：`$menuState` 或 `facts` 未让 `PopulateOptions` 命中。
    - **解决方案**：核对当前 Memory 值、菜单状态和选项规则的场景条件。

- **规则未触发且日志无异常**：规则 ID 跨文件重复，或派发点的 `trigger` 拼写不一致。
    - **解决方案**：改用唯一前缀并逐字核对使用方传入的 `trigger`。

- **`$option` 条件始终为 `false`**：该条件写在非选项回流 `trigger` 中，没有任何插件写入 `$option`。
    - **解决方案**：把判断移到 `DialogOptionSelected`、`NewGameOptionSelected` 或酒吧字符串选项的回流路径。

## 总结

Jangala 停靠文字承接市场菜单，酒吧邀请通过字符串回流再返回吧台，独立舰队呼叫接到通讯，开发选项在角色创建时发放信用。每个结果都进入场景提供的对象与下一步调用。

相同 Rules 写法要结合当前 Memory、控制器与菜单职责使用。覆盖结果后继续接上流程，当前动作也就能从进入、选择走到返回。

## 建议参考项

- `data/campaign/rules.csv`：本节示例追加行与各类场景 `trigger` 的平台规则表；规则 `id` 前缀为 `GearIn_rules_scene_*`，并参照 `marketOpen` / `marketDock` / `marketPostOpenDefault` / `greetingFallthrough` / `initial_AnyFleet` / `marketReturnFromBar` 等回退规则。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：交互对话框插件，对话场景 `trigger` 的主要派发点与 `$option` 回流实现。
- `com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`：舰队遭遇与通讯对话插件，`BeginFleetEncounter` / `OpenCommLink` 的派发点。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧场景的 `rulecmd` 与对话插件，`AddBarEvents` / `ReturnFromBar` 派发与字符串选项回流。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`：写临时 `bar` 选项的 `rulecmd`(`$core_tempBarEvents` 记忆键)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub`：任务列表与任务对话 `trigger`(`<前缀>_blurb` / `<前缀>_option` / `AddMHCloseOption`) 的使用方。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：任务以酒吧事件展示时的 `<前缀>_blurbBar` / `<前缀>_optionBar` `trigger` 使用方。
- `com.fs.starfarer.api.impl.campaign.NewGameDialogPluginImpl`：主菜单新游戏对话框 (流程场景)，回流 `trigger` 为 `NewGameOptionSelected`。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：场景事实键 (`$hasMarket` / `$isHostile` / `$isContact` 等) 的写入方。
- `com.fs.starfarer.api.impl.campaign.rulecmd.EndConversation`：对话退出转场 `rulecmd`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.OpenComms`：舰队通讯转场 `rulecmd`；
- `com.fs.starfarer.api.impl.campaign.rulecmd.HailPlayer`：`$hailing` 写入 `rulecmd`。
- `com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl`：放弃殖民地时的市场状态复位 (`$menuState` / `$tradeMode` / `$hasMarket`)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：单选派发与 `keepOptions`(第二参) 语义。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：规则记忆键常量 (`MISSION` 等)。
