# 在生涯场景中使用 Rules

本节把已有 Rules 规则接入对话、市场、舰队、酒吧与任务等生涯场景。每类场景会在固定时机触发匹配，并提供不同的变量供规则使用。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 区分五类场景各自的核心 trigger，并说清每类的触发时机、派发点与主要可用变量：
    - 对话：`OpenInteractionDialog` / `PopulateOptions` / `DialogOptionSelected` / `PickGreeting` / `OpenCommLink`；
    - 市场：`MarketPostOpen` / `MarketPostDock` / `TradePanelFlavorText`；
    - 舰队：`BeginFleetEncounter` / `OpenCommLink` / `PickGreeting`；
    - 酒吧：`AddBarEvents` / `BarPrintDesc` / `ReturnFromBar` / `BarHighRepCrewGain`；
    - 任务：`MHOpenText` / `<前缀>_blurb` / `<前缀>_option` / `<前缀>_blurbBar` / `<前缀>_optionBar` / `AddMHCloseOption` / `NewGameOptionSelected` / `AddNewGameChoices`；
- 解释场景组合写法：`$menuState` / `$tradeMode` 状态记忆限定菜单、`$option` 回流 `DialogOptionSelected` 形成选项完整流程、`FireAll` 填充菜单与 `FireBest` 叙述/转场的分层、`score:` 竞争纪律、以及 "进入/退出配对" 的转场模式；
- 说明覆盖原版规则时的职责纪律：`FireBest` 只执行一条规则，覆盖者必须复刻被覆盖规则的结果职责 (最典型的：`MarketPostDock` 的 `$menuState = main 0` + `FireAll PopulateOptions`)，否则市场菜单不会出现；
- 从症状定位场景故障 (见故障分析节)，并沿着**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置核对 (沿用规则基础页的最小任务链)。

## 前置知识

- [C6-S2] 的 MOD 生命周期与规则文件加载边界；
- [C9-S1] 的 `addScript` 与持久数据判断；
- [C9-S6] 的 `showInteractionDialog` 与 `InteractionDialogPlugin` 生命周期；
- [C14-S1] 的 `rules.csv` 七列、匹配流程、条件脚本语法、文本 token 替换与直接写行即注册的效果；
- [C14-S3] 的 `BaseCommandPlugin` 与内置规则命令；
- [C14-S4] 的 `MemoryAPI` 过期、持久化、`$option` 回流与场景事实键。

## 术语

按术语约定，本节首次完整讲解以下词；API 类名保留源码大小写：

- **场景 trigger(scene trigger)**：`rules.csv` 中按 "生涯场景" 分组的标准触发器名。
    - 场景 trigger 的候选池由原版派发点 (Java 插件) 以固定字符串发起匹配；MOD 向其中添加规则即挂载到该场景。
    - 与规则基础页的自定义 trigger 相对：自定义 trigger 只能由 `RuleBasedInteractionDialogPluginImpl` 构造器或 `FireBest`/`FireAll` 主动调用，场景 trigger 由原版使用方在固定时机调用。
- **场景事实键 (scene fact key)**：`CoreCampaignPluginImpl` 的 `update*Facts` 钩子与规则脚本共同维护的记忆键 (如 `$hasMarket`、`$menuState`、`$tradeMode`、`$hailing`、`$mh_count`、`$mHub`)。
    - 它们是场景规则的 "上下文开关"，条件列必须引用它们才能把规则限定在正确的场景与状态下。
- **临时 bar 选项 (temp bar option)**：由 `AddBarEvents` 规则经 `AddBarEvent` rulecmd 写入市场记忆 `$core_tempBarEvents` 的酒吧选项。`BarCMD.showOptions` 每次进酒吧先清空再重建 —— 临时选项不跨进入保留。
- **任务 trigger 前缀 (mission trigger prefix)**：任务 (`HubMission`) 的 `getTriggerPrefix()`(如原版 `hijack`、`smug`、`sitm`)。任务对话规则以 `前缀_blurb` / `前缀_option`(对话列表) 与 `前缀_blurbBar` / `前缀_optionBar`(酒吧包装) 为 trigger 名。
- **流程场景 (flow scene)**：非实体交互对象驱动的规则场景，本节唯一例子是主菜单新游戏对话框 (`NewGameDialogPluginImpl`)。它的 `$option` 回流 trigger 是 `NewGameOptionSelected`，不是 `DialogOptionSelected`。

接下来看五类场景的 trigger 由谁在何时发起匹配。

## 梳理 Rules 场景关系

先看总览表确认各 trigger 的派发点与时机，再按对话、市场、舰队、酒吧、任务的顺序逐个展开，最后归纳跨场景的组合写法。

### 场景 trigger 总览速查表

原版 `rules.csv`(`Starsector\starsector-core\data\campaign\rules.csv`) 中，本节五类场景的核心 trigger 与派发点如下；规则基础页已统计的 `DialogOptionSelected`(6050)、`PopulateOptions`(334)、`OpenInteractionDialog`(121) 不再重复，只补充场景归属与派发点。

| 场景 | trigger | 条数 | 派发点 (使用方) | 触发时机 |
|---|---|---|---|---|
| 对话 | `OpenInteractionDialog` | 121 | `RuleBasedInteractionDialogPluginImpl.init` 的 `fireBest(initialTrigger)`(默认 trigger，规则基础页已证)；`AbandonMarketPluginImpl` 的 `FireAll` | 交互对话框初始化；弃档后回到停靠对话 |
| 对话 | `PopulateOptions` | 334 | 规则脚本 `FireAll`(如 `marketDock`、`greetingDefaultFriendly`)；`EndConversation`；`BaseMissionHub.listMissions` | 填充选项菜单 |
| 对话 | `DialogOptionSelected` | 6050 | `RuleBasedInteractionDialogPluginImpl.optionSelected`；`BarCMD.optionSelected` | 用户点选任意选项 (`$option` 回流) |
| 对话 | `PickGreeting` | 199 | 规则脚本 `FireBest`(`OpenCDE` → `convDefault`；`DialogOptionSelected` → `npcCommsAccept`)；`RuleBasedInteractionDialogPluginImpl` 构造器可指定 `PickGreeting` 为初始 trigger | 人物通讯连接被接受时的问候 |
| 对话 | `OpenCommLink` | 183 | `FleetInteractionDialogPluginImpl` 的 `fireBest`；规则脚本 `FireBest`(如 `greetingCGRweirdHM` 等势力特殊问候) | 打开舰队通讯链接 |
| 市场 | `MarketPostOpen` | 22 | `marketOpen` 规则 (`OpenInteractionDialog`) 脚本 `FireBest`；`EndConversation` | 停靠后进入市场对话；对话结束仍在市场时 |
| 市场 | `MarketPostDock` | 32 | `MarketPostOpen` 规则脚本 `FireBest` | 停靠流程收尾 (`$menuState = main` + 菜单) |
| 市场 | `TradePanelFlavorText` | 23 | 交易面板的 `FireBest` 调用 (假 dialog) | 每次打开交易面板 |
| 舰队 | `BeginFleetEncounter` | 61 | `FleetInteractionDialogPluginImpl.reinit` 预检 + `reinitPostContinue` / `optionSelected` 的 `fireBest` | 舰队遭遇对话初始与 Continue 之后 |
| 舰队 | `OpenCommLink` / `PickGreeting` | 183 / 199 | 见 "对话" 行 | 打开通讯 / 人物问候 |
| 酒吧 | `AddBarEvents` | 16 | `BarCMD.showOptions`(先清空再 `FireAll`) | 每次展示酒吧选项 |
| 酒吧 | `BarPrintDesc` | 10 | `bar_optionSel` 规则 (`$option == marketVisitBar`) 脚本 `FireBest` | 进入酒吧 |
| 酒吧 | `ReturnFromBar` | 5 | `BarCMD.leaveBar` 的 `FireBest` | 离开酒吧 (还原插件后) |
| 酒吧 | `BarHighRepCrewGain` | 6 | `BarHRESpeechB` 规则脚本 `FireAll` | 高声望酒吧事件演讲结束 |
| 任务 | `MHOpenText` | 2 | `openMhubOptionSel` 规则脚本 `FireBest` | 任务列表文本之前 |
| 任务 | `<前缀>_blurb` / `<前缀>_option` | 每条任务各 1 | `BaseMissionHub.listMissions`(`FireBest`，带 keepOptions) | 任务列表展开 |
| 任务 | `AddMHCloseOption` | 5 | `BaseMissionHub.listMissions`(`FireBest`) | 任务列表收尾 |
| 任务 | `MHPostMissionListText` | 2 | `BaseMissionHub.listMissions`(`FireBest`) | 任务列表文本之后 |
| 任务 | `<前缀>_blurbBar` / `<前缀>_optionBar` | 每条任务各 1 | `HubMissionBarEventWrapper.addPromptAndOption`(`FireBest`) | 任务以酒吧事件展示 |
| 任务 | `NewGameOptionSelected` / `AddNewGameChoices` | 15 / 8 | `NewGameDialogPluginImpl` 的 `fireBest`；`InitNewGameChoices` → `AddNewGameChoices` 链 | 主菜单新游戏对话框 |

计数口径：原版 `rules.csv` 非注释行统计，与规则基础页相同；上表之外的少量 trigger 如下：

- `BeginFleetEncounter2` 另有 1 条；
- `BarShuttleDownPrintDesc` 1 条；
- `ContactPreMissionListText` 4 条；
- `AsebPreMissionListText` 3 条；
- `ContactPostAccept` 8 条；
- `ContactPrintAcceptText` 10 条；
- `ContactPrintDeclineText` 1 条；
- `InitNewGameChoices` / `BeginNewGameCreation` 各 1 条。

条数口径说明

上表条数为原版 `rules.csv` 非注释行的统计，仅用于帮助您估量各 trigger 在原版中的使用密度与竞争烈度，不构成任何 API 承诺，随游戏版本可能变化。

### 场景事实键的写入方

规则条件里最常见的场景事实键由 `CoreCampaignPluginImpl`(`com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`) 的 `update*Facts` 钩子在规则匹配前写入实体/市场/人物/势力记忆：

- `updateEntityFacts`：`$tag:<tag>`、`$isHostile`(按声望档位)、`$hasMarket`(非纯条件市场)、`$hasStation`、`$marketSize`、`$stability`、`$planetType`；
- `updateMarketFacts`：`$tag:<tag>`、`$mc:<条件id>`、`$ind:<产业id>`、`$id`(市场 id) 等；
- `updatePersonFacts`：`$id`(人物 id)、`$isPerson`、`$isContact`、`$name` / `$personName`、`$rel` / `$relName`、`$rank` / `$post`、`$importance*`、`$level`、`$personality`、`$tag:<tag>` 等；
- `updateFactionFacts`：`$id`、`$friendlyToPlayer` / `$hostileToPlayer` / `$neutralToPlayer`、`$isHostile`。

`$menuState` / `$tradeMode` / `$hailing` 等**状态**键不由 facts 钩子写，而是由规则脚本与原版 rulecmd 维护 (见各场景小节)。规则基础页的结论仍然成立：`$tag:` 类条件的前提是目标实体/市场/人物确实带该 tag。

### 对话场景 (OpenInteractionDialog / PopulateOptions / DialogOptionSelected / PickGreeting / OpenCommLink)

对话场景是其余场景的底座：进入、填菜单、回收选项都在这里发生，市场、舰队与酒吧复用同一套机制。

- **`OpenInteractionDialog`(121 条)**：交互对话框的默认初始 trigger(`RuleBasedInteractionDialogPluginImpl` 构造器默认值，规则基础页已证)。
    - 原版规则分成两组：无条件/实体类回退规则 (`defaultOpenDialog`：`ShowDefaultVisual` + `PrintDescription 3` + `SetShortcut defaultLeave "ESCAPE"` + `defaultLeave:Leave`)。
    - 实体条件类 (`beaconOpenDialog`：`$tag:warning_beacon`；`marketOpen`：`$hasMarket` → `ShowDefaultVisual` + `PrintDescription 3` + `FireBest MarketPostOpen`，即市场场景的入口)。
- **`PopulateOptions`(334 条)**：菜单填充器，由规则脚本 `FireAll` 调用 (不是插件直接触发)。
    - 所有选项规则必须用状态条件限定，否则该选项会一直出现或一直不出现。
    - 市场菜单用 `$menuState == main` + `$hasMarket` + `$tradeMode != NONE` (如 `marketCommDir`、`marketOptBar`、`marketOptTradeMulti`、`marketAddOptionLeave`)。
    - 舰队对话菜单用 `$menuState == fleetConvMain`；
    - 人物对话用 `$isPerson`(`convOptionLeave`：`cutCommLink:Cut the comm link`)。
- **`DialogOptionSelected`(6050 条)**：选项回流唯一使用方 (`RuleBasedInteractionDialogPluginImpl.optionSelected`：本地记忆写 `$option`、`expire 0`、`fireBest("DialogOptionSelected")`)。
    - 规则基础页已证全部 6050 条都带 `$option ==` 条件。
    - 本节补充两个非默认回流方：`BarCMD.optionSelected` 对酒吧字符串选项同样写 `$option` 并 `FireBest "DialogOptionSelected"`；`NewGameDialogPluginImpl.optionSelected` 写 `$option` 后触发的是 **`NewGameOptionSelected`**(见任务场景)。
- **`PickGreeting`(199 条)**：人物通讯问候。
    - 派发链：通讯目录点选人物 → 通讯面板触发 `FireBest "OpenCDE"` → `OpenCDE` 规则 `convDefault` 脚本 `FireBest PickGreeting`：
        - 问候规则按 `$isContact`、`$id`(人物)、声望等条件选一条，展示 `ShowPersonVisual` + 问候文本 + 选项 (`FireAll PopulateOptions` 或直接列选项)。
    *   `NPCCommsRequest` 触发器 (`npcCommsAccept` 规则脚本 `FireBest`) 处理 NPC 主动通讯请求。
    *   以问候 trigger 为初始 trigger、或自定义问候子 trigger(`FireBest` 分派 + `score:` 加分) 都是常见扩展写法。
- **`OpenCommLink`(183 条)**：舰队通讯对话。
    - 派发：`FleetInteractionDialogPluginImpl` 开通讯 (`OPEN_COMM`) 时创建 embedded `RuleBasedInteractionDialogPluginImpl` (`setEmbeddedMode(true)`、`setActivePerson(对方指挥官)`) 并 `fireBest("OpenCommLink")`：
        - 无匹配时文本面板显示 "You try to establish a comm link, but get only static."。
    *   原版问候规则族按关系与状态分派：
        - `$faction.friendlyToPlayer`(友好→`$menuState = fleetConvMain 0` + `FireAll PopulateOptions`)；
        - `$faction.neutralToPlayer`
        - `$entity.isHostile` + `$entity.relativeStrength`(敌舰强弱)；
        - `!$player.transponderOn` (应答器关闭→"Turn your transponder on" 选项)。
        - `$entity.ignorePlayerCommRequests score:10000`(拒绝通讯)；
        - 无条件 `greetingFallthrough` 回退规则 (1 分)。

### 市场场景 (MarketPostOpen / MarketPostDock / TradePanelFlavorText)

市场对话是规则场景中 "进入 → 状态 → 菜单 → 退出" 的最完整流程：

1. **进入**：停靠市场实体 → 默认对话初始化 `fireBest("OpenInteractionDialog")` → `marketOpen` 规则 (条件 `$hasMarket`，1 分) 命中：`ShowDefaultVisual` + `PrintDescription 3` + `FireBest MarketPostOpen`。
2. **停靠许可**：`MarketPostOpen`(22 条) 按条件选一条 (`$hasMarket` + `$player.transponderOn` + 关系分派；
    - `marketPostOpenDefault` 打印停靠许可文本、`$tradeMode = OPEN 0`、`FireBest MarketPostDock`)。
    - **应答器关闭时**，`marketPostOpenDefault` 等要求 `$player.transponderOn` 的规则不匹配，但原版有专门的应答器关闭规则族接管：
        - `marketPostOpenToOffIsFine`(`$hasMarket` + `!$player.transponderOn`，2 分，`$tradeMode = OPEN 0`)。
        - `marketPostOpenToOffSneak`(另需 `!$faction.c:allowsTransponderOffTrade` + `!$market.mc:free_market`，4 分，`$tradeMode = SNEAK 0`)。
        - `marketPostOpenToOffPatrols`(另需 `IsSeenByPatrols $faction.id true`，5 分，`$tradeMode = NONE 0`)。
        - `marketPostOpenNoTradeHostile`(`$market.playerHostileTimeout score:100`)；
        - 停靠流程继续 (`FireBest MarketPostDock` 照常)，只是贸易模式不同，不会 "无停靠文本、不进市场菜单"。
    *   中立势力市场由 `marketPostOpenDefaultNeutral` 回退规则接管 (条件只有 `$hasMarket` + `$faction.isNeutralFaction score:100`，无应答器条件)—— 停靠流程继续但无许可文本。
3. **停靠收尾**：`MarketPostDock`(32 条) 中 `marketDock`(无条件，1 分) 是回退规则：`$menuState = main 0` + `FireAll PopulateOptions`。
    - 其余 31 条是场景附加 (任务交付检查 `DeliveryMission checkCompletion score:100`、债务/雇佣兵检查 `MarketCMD checkDebtEffect score:1000`、`MarketCMD checkMercsLeaving score:500`、特殊势力检查等)。
    - `FireBest` 只执行一条 —— 覆盖者必须自己复刻 `marketDock` 的结果职责 (见规则强度与 "市场选项只剩 `Leave`" 故障条目)。
4. **市场菜单**：`PopulateOptions` 中 `$menuState == main` + `$hasMarket` 的规则加选项：
    - `marketCommDir`(通讯目录，`!$faction.isNeutralFaction`)。
    - `marketVisitBar`(酒吧，`$tradeMode != NONE` + `!$faction.isNeutralFaction` + `!$market.noBar`)。
    - `marketOpenCoreUI`(交易/整备)；
    - `marketRepair`(`$tradeMode == OPEN` + `Repair*` 命令条件)；
    - `marketConsiderHostile`(军事选项)；
    - `marketAddOptionLeave` (`marketLeave:Leave`)。
5. **退出/再进入**：`EndConversation` rulecmd：对话插件不是 `FleetInteractionDialogPluginImpl` 时，若 `$hasMarket` 且本地记忆无 `$menuState` → `FireBest MarketPostOpen`(重新走停靠许可)；否则 `FireAll PopulateOptions`。
    - `AbandonMarketPluginImpl`(弃档) 复位 `$menuState = main`、`$tradeMode = NONE`、`unset $hasMarket` 后 `FireAll "OpenInteractionDialog"`。
6. **交易面板**：
    - 交易面板每次打开面板时以 `FireBest` 发起 `"TradePanelFlavorText"` 匹配 (传入假 dialog 与记忆槽位)。
    - memoryMap 由交易面板在更新 flavor 文本时自建 (槽位与规则匹配用的槽位结构一致：`local` / `faction` / `global` / `player` / `market` / `sourceMarket`)。
    - `$tradePanelMode`(MARKET/CARGO/SALVAGE) 由面板按自身模式写入 sector 记忆 (与 `$tradeMode` 无关；这些槽位的一个实际使用例是势力关系描述文本的生成，规则基础页已述)。
    - 原版 23 条按 `$global.tradePanelMode`、`$marketSize`、`$stability` 分派文本。

交易面板的 dialog 是假 dialog

交易面板制造假 dialog 来匹配 `TradePanelFlavorText`：

flavor 规则只能用 `AddText` 等不依赖真实对话上下文的命令，视觉面板、`DismissDialog` 等依赖真实对话上下文的命令会异常或静默无效 (与规则基础页所讲的假 dialog 限制同类)。

覆盖 FireBest 目标必须复刻结果职责

`FireBest` 只执行一条规则：命中本 MOD 的规则，被覆盖的原版回退规则就不再执行。

因此覆盖 `MarketPostDock` 的 `marketDock` 时，必须把它的结果职责复刻进自己的脚本 (`$menuState = main 0` + `FireAll PopulateOptions`)；

覆盖 `OpenInteractionDialog` 的 `marketOpen` 时同理 (`ShowDefaultVisual` + `PrintDescription 3` + `FireBest MarketPostOpen`)。

否则市场菜单不会出现 (选项面板空、只剩 failsafe/Leave)，停靠后卡在对话 (见 "市场选项只剩 `Leave`" 故障条目)。

### 舰队场景 (BeginFleetEncounter / OpenCommLink / PickGreeting)

舰队遭遇与通讯共用同一套对话底座：`BeginFleetEncounter` 负责遭遇开局，`OpenCommLink` 与 `PickGreeting` 负责通讯与问候。

- **`BeginFleetEncounter`(61 条)**：舰队遭遇对话。两次匹配时机 (`FleetInteractionDialogPluginImpl`)：
    - `reinit(withContinueOnRuleFound)`：`getBestMatching(null, "BeginFleetEncounter", ...)` **预检** —— 命中则只显示 "Continue"(`REINIT_CONTINUE`)。
    - 用户点击后 `reinitPostContinue` `fireBest("BeginFleetEncounter")` (本地记忆 `$fidpi_addContinue` 为真时改走 `BeginFleetEncounter2`)。
    - 初次遭遇 (`optionSelected`)：创建 embedded 对话后同样 `fireBest("BeginFleetEncounter")`；规则脚本置 `$fidpi_addContinue` 则清空选项只留 "Continue"。
    - 规则模式：
        - 脚本 `AddText "You're being hailed by the $faction $otherShipOrFleet." $faction.baseColor` + `$hailing = true 0`(如 `LPTitheCheck`、`mpm_pirateEncounter`)：
            - `customsInspectionScan` 只 `AddText` 不写 `$hailing`；
            - `tOffPatrolBegin` 以 `OpenComms` 直接进入通讯。
        *   或 `FleetDesc` + `$shownFleetDescAlready = true 0` (`initial_AnyFleet` 回退规则，1 分)。
        *   文本经 `FireBest` 的 `addText` 显示。
        *   **BeginFleetEncounter 规则不依赖 `options` 列** —— 选项面板随后由舰队主菜单接管 (`INIT` 路径清空选项)。
    *   `$hailing` 的可观察效果：舰队主菜单里 "Open a comm link" 选项高亮并改名 "Accept the comm request"(随后 `unset $hailing`)。
        *   `OpenComms` rulecmd 置 `FleetInteractionDialogPluginImpl.directToComms = true` 直接进入通讯。
        *   `HailPlayer` rulecmd 是写 `$hailing` 的正式途径。
- **`OpenCommLink`**：见对话场景。
    - 通讯内规则把 `$menuState = fleetConvMain 0` + `FireAll PopulateOptions` 切换成舰队对话菜单：
        - `shrineFleetConvOption`、`academyFleetConvOption` 等按 `$entity.<flag>` 条件加选项；
        - `convOptionLeave` 按 `$isPerson` 加 `cutCommLink`。
    *   `cutCommLink` 选项由 `DialogOptionSelected` 规则 `convEnd` 等处理：`ShowDefaultVisual` + `EndConversation` → 回舰队主菜单。
- **`PickGreeting`**：人物通讯 (见对话场景)。舰队侧经通讯目录/接触人入口进入。
- **可用变量**：
    - `$entity.relativeStrength`(正=对方更强)；
    - `$entity.isHostile`
    - `$faction.id` / `$faction.friendlyToPlayer`
    - `$player.transponderOn`
    - `$entity.<自定义 flag>`(记忆键)；
    - `$isPatrol` / `$sourceMarket.mc:<条件>`(应答器检查规则族)。

### 酒吧场景 (AddBarEvents / BarPrintDesc / ReturnFromBar / BarHighRepCrewGain)

酒吧场景是 "市场菜单 → 临时选项 → 事件对话 → 返回" 的完整流程 (`BarCMD` 既是 rulecmd 也是对话插件，`com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`)：

1. **进入**：市场菜单 `marketVisitBar`(`marketOptBar` 规则，选项 order 5)→ `bar_optionSel` 规则 (`$option == marketVisitBar`)：
    - `FireBest BarShuttleDownPrintDesc` + `FireBest BarPrintDesc`(10 条，`ShowImageVisual space_bar` + 描述文本)。
    - `BarCMD showOptions`。
2. **展示选项**：`BarCMD.showOptions`：
    - `dialog.getOptionPanel().clearOptions()`
    - `AddBarEvent.clearTempEvents(market)` + `FireAll.fire(null, dialog, memoryMap, "AddBarEvents")`
    - 把 `$core_tempBarEvents` 中的临时选项 (`blurb` 进文本面板、`option` 进选项面板、可带颜色)。
    - 再追加 `PortsideBarData` 的 active 事件 (Java `BaseBarEvent` / `HubMissionBarEventWrapper`)。
    - 末尾加 `"Leave the bar"`(id `barLeave`，ESC 快捷键)。
    - `dialog.setPlugin(this)`(BarCMD 接管对话)。
3. **临时选项**：`AddBarEvents`(16 条) 规则用 `AddBarEvent <optionId> <选项文本> <描述文本> [颜色]` rulecmd 写临时选项：
    - 存储：`com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent` 用 `KEY = "$core_tempBarEvents"` 存市场记忆，`getTempEvents` / `clearTempEvents` 维护。
    - 选项文本与描述做 token 替换。
    - 原版 16 条按 `$market.id == <市场>` + 剧情 flag 条件添加 (如 `gaMeetHegLieutenantOnCoatl`、`goToTheGABarEventOption` 带 `RollProbability 0.2`)。
    - **临时选项每轮重建**：每次进酒吧 `BarCMD` 先 `clearTempEvents` 再 `FireAll AddBarEvents`，跨进入不保留。
4. **点选**：`BarCMD.optionSelected`：
    - `PortsideBarEvent` 选项 → `BarEventDialogPlugin`(事件对话，Java)。
    - `"barLeave"` → `leaveBar()`；
    - `"barContinue"` → `showOptions(true)`：
    - 字符串选项 (含临时选项与 mission 包装选项)→ `dialog.setPlugin(originalPlugin)` 还原规则对话：
        - `mem.set("$option", optionData, 0)` + `FireBest "DialogOptionSelected"`—— 与 `RuleBasedInteractionDialogPluginImpl` 的回流完全一致。
        - 本节最小例的酒吧规则正是消费这里。
5. **返回/退出**：`BarCMD.returnFromEvent [true|false]`(从事件对话返回酒吧；true 时给 "Continue") 与 `leaveBar`(停掉酒吧环境音、`dialog.setPlugin(originalPlugin)`、`ShowDefaultVisual`、`FireBest "ReturnFromBar"`)。
    - `ReturnFromBar`(5 条) 中 `marketReturnFromBar`：`$menuState = main 0` + `FireAll PopulateOptions` + 文本 "You leave the bar and take a shuttle back to your $shipOrFleet."—— **退出转场把市场菜单状态复位**。
6. **奖励**：`BarHighRepCrewGain`(6 条) 由高声望酒吧事件演讲规则 `BarHRESpeechB` 脚本 `FireAll` 触发 (`AddCommodity crew N`，按 `$player.fleetPoints` / `RollProbability` / `score:2` 分档)。

临时 bar 选项的三个边界

- 临时选项不能当长期状态 (每轮清空重建)；
- `RemoveBarEvent` 只删市场记忆中的临时选项、不碰 `PortsideBarData` 的 active 事件；
- `HubMissionBarEventWrapper` 的选项 id 以任务前缀开头，`getWrapperFor` 用 `startsWith` 识别、`abortMissions` 会中止**其它** mission 包装 —— 自定义临时选项不要使用与已注册任务相同的前缀。

### 任务场景 (MHOpenText / 任务前缀 trigger / AddMHCloseOption / 新游戏流程场景)

生涯任务对话 (联系人任务) 是规则 + Java 任务插件 (`BaseMissionHub` / `HubMission`，属生涯对象体系) 的协作：

1. **入口**：人物对话菜单 `PopulateOptions` 规则 `addMHubOption`(条件 `$isPerson` + `MissionHubCMD hasHub` + `Call $mHub setMHOptionText`) 加 `5:mh_open:$mh_openOptionText` ("Do you have any work for me?")。
    - `$mHub` = `BaseMissionHub.KEY`，`setMHOptionText` 写 `$mh_openOptionText`。
2. **列表**：点 `mh_open` → `openMhubOptionSel` 规则：`Call $mHub prepare` → `FireBest MHOpenText` →`Call $mHub listMissions` → `SetEnabled mh_open false`。
    - `MHOpenText`(2 条：`contactMHOpenText` / `asebMHOpenText`)→ `FireBest ContactPreMissionListText`(4 条，按 `$mh_count` / `$mh_firstInlineBlurb` / `$mHub_contactSuspended` 打印列表前文本)。
3. **展开**：`BaseMissionHub.listMissions` 对每份 offered mission：
    - `FireBest <前缀>_blurb true` / `FireBest <前缀>_option true`(每条任务各 1 条规则，如 `hijack_blurb` / `hijack_option`)。
    - 随后 `FireBest AddMHCloseOption true`(5 条：`mh_close` 选项 + `SetShortcut mh_close "ESCAPE"`)。
    - `withBlurbs` 时 `FireBest MHPostMissionListText`(2 条："So, how about it?" 等)。
    - **`FireBest` 的第二个参数 `true` 是 keepOptions**(`FireBest.fire` 把参数字符串 tokenize)。
4. **接受/拒绝**：任务选项 (如 `hijack_start`) 经 `DialogOptionSelected` 回流到任务规则：
    - 接受类规则 `contactMissionAcceptGeneric`(`$option == contact_accept`)：`FireBest ContactPrintAcceptText` + `Call $mHub accept $missionId` + `FireBest ContactPostAccept` + `Call $mHub returnToList`。
    - `BaseMissionHub.callEvent` 分派 `prepare` / `listMissions` / `returnToList` / `accept` / `doCleanup` 动作；`accept` 调 `mission.accept(dialog, memoryMap)`。
    - 任务规则可读 `$missionId` 与 `$mission` 记忆 (`MemKeys.MISSION`)。
    - 写入方：`SetActiveMission` / `ClearActiveMission` rulecmd，或插件 `setActiveMission`(`RuleBasedInteractionDialogPluginImpl` / `FleetInteractionDialogPluginImpl`)。
5. **酒吧任务包装**：任务也可以出现在酒吧：
    - `HubMissionBarEventWrapper.addPromptAndOption` 把 `LOCAL` 临时换成任务人物记忆 (`ENTITY` / `PERSON_FACTION` 一并换)。
    - 调 `mission.updateInteractionData(dialog, memoryMap)`，然后 `FireBest <前缀>_blurbBar true` + `FireBest <前缀>_optionBar true`，再恢复记忆。
    - 原版对应 `hijack_blurbBar` / `hijack_optionBar` 等。
    - 用户点选后由 `BarCMD.optionSelected` 进入任务对话 (`abortMissions` 中止未选中的包装)。
6. **对照：任务板**：通讯面板的 missions 页签是 **Java** 任务板 (`MissionBoardAPI` / `MissionAvailability.playerAccept`)，不走规则 —— 本节只把它作为 "非规则场景" 的边界。
7. **流程场景：新游戏对话框**：主菜单新游戏 → `NewGameDialogPluginImpl`(`com.fs.starfarer.api.impl.campaign.NewGameDialogPluginImpl`)：
    - `init` 组装 memoryMap(`LOCAL` = 角色数据实体记忆、`GLOBAL`，devMode 时 `$global.isDevMode`)。
    - 选项回流：`memory.set("$option", ...)` + `expire 0` + `fireBest("NewGameOptionSelected")`。
    - `CONTINUE_CHOICES` → `fireBest("BeginNewGameCreation")`；
    - 规则链：`BeginNewGameCreation` → `onNewGameCreationStart`(`NGCAddCredits 2000` + `FireAll InitNewGameChoices`)→ `InitNewGameChoices` → `initNewGameChoices`(`FireAll AddNewGameChoices`)。
    - `AddNewGameChoices`(8 条) 加起始选项 (`ngcWolf` / `ngcWayfarer` / dev 选项以 `$global.isDevMode` 为条件)。
    - `NewGameOptionSelected`(15 条) 处理器 (`$option == ngcX` → `NGC*` 命令族：`NGCAddShip` / `NGCAddCredits` / `NGCSetDifficulty` / `NGCDone` 等)。
    - **注意 `NewGameOptionSelected` 是主菜单对话框的专属回流 trigger**，在生涯对话里写它永不触发。

### 场景组合写法

把五类场景放在一起看，会得到三组反复出现的写法。

- **状态机模式**：`$menuState`(`main` / `fleetConvMain`) 与 `$tradeMode`(`OPEN` / `NONE` / `SNEAK`) 是场景状态记忆：
    - `PopulateOptions` 菜单规则以它们为条件；
    - 转场必须成对 —— 进入时赋值 (`marketDock`：`$menuState = main 0`；`greetingDefaultFriendly`：`$menuState = fleetConvMain 0`)。
    - 退出时复位 (`ReturnFromBar` → `$menuState = main`；`AbandonMarketPluginImpl` → `$tradeMode = NONE`)。
- **分层模式**：`FireAll` 填充 (`PopulateOptions` / `AddNewGameChoices` / `AddBarEvents`—— 候选规则全部执行、全部添加选项)：
    - `FireBest` 叙述与转场 (`MarketPostOpen` / `MarketPostDock` / `BarPrintDesc` / `MHOpenText` / `PickGreeting`—— 只取分数最高一条)。
- **回流完整流程**：`options` 列 id ↔ `DialogOptionSelected`(或 `NewGameOptionSelected`) 规则 `$option == <id>` 逐字一致 (大小写敏感)。
- **竞争纪律**：默认条件 1 分；原版剧情规则常用 `score:1000`(`$tag:xxx score:1000` 等)；MOD 覆盖回退规则用 100 级即可 (100–103 分恒胜 1–3 分的回退规则、输给 1000 级剧情规则 —— 见最小例)。

一次性演示 flag 的写法

条件列写 `!$player.<flag>`、脚本列写 `$player.<flag> = true 0`(`expire 0`)：第一次命中后 flag 置位，第二次进入自动回退原版行为。

需要重新演示时换一个新 flag 名即可 (原版没有 "清记忆" 的 rulecmd，devMode 下可用 `DumpMemory` 查看记忆键)。

## 规则强度速查表

本节必须遵守的规则清单 (违反后的可观察结果均附上；规则基础页已列的加载期致命错误与跨文件重复 id 语义在本节所有场景继续有效，不重复)：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 覆盖 `FireBest` 目标规则时必须复刻被覆盖规则的结果职责 (市场场景：`MarketPostDock` 的 `$menuState = main 0` + `FireAll PopulateOptions`；`OpenInteractionDialog` 的 `ShowDefaultVisual` + `PrintDescription 3` + `FireBest MarketPostOpen`) | 必须 | `FireBest` 只执行一条规则：市场菜单不出现 (选项面板空、只剩 failsafe/Leave)，停靠后卡在对话 |
| `options` 列选项 id 与 `DialogOptionSelected` / `NewGameOptionSelected` 规则的 `$option == <id>` 逐字一致 (大小写敏感) | 必须 | 点选后无规则匹配：对话文本面板出现 `ERROR: no rule found for option <id>` + failsafe |
| 任务规则 trigger 名必须等于 `mission.getTriggerPrefix() + "_blurb" / "_option" / "_blurbBar" / "_optionBar"` | 必须 | `BaseMissionHub.listMissions` 文本面板打印 `No blurb found for <prefix>`、`FireBest` 返回 false、任务选项缺失 |
| 临时 bar 选项必须经 `AddBarEvents` 规则 + `AddBarEvent` rulecmd 添加；它每轮被清空重建，不是持久状态 | 事实 | 跨进酒吧不保留；`BarCMD.showOptions` 先 `clearTempEvents` 再 `FireAll AddBarEvents` |
| 自定义临时 bar 选项 id 不得使用与任何已注册任务相同的前缀 | 事实 | `BarCMD.getWrapperFor` / `abortMissions` 按 `optionId.startsWith(mission.getTriggerPrefix())` 识别，同前缀选项被当作 mission 包装处理、其余包装被中止 |
| `BeginFleetEncounter` 规则不依赖 `options` 列 (交互经 `$hailing` / `OpenComms` / 主菜单转场) | 事实 | `reinitPostContinue` / `INIT` 路径在规则执行后接管选项面板，`options` 列内容被清空 |
| `TradePanelFlavorText` 规则的 dialog 是假 dialog | 事实 | 依赖真实对话上下文的命令 (视觉面板、`DismissDialog` 等) 异常或无效 (交易面板的假 dialog 机制) |
| `NewGameOptionSelected` 只在主菜单新游戏对话框回流 `$option`；`DialogOptionSelected` 只在生涯对话回流 | 必须 | 写错场景的回流 trigger 规则永不触发且无报错 |
| `$menuState` / `$tradeMode` 由场景脚本维护，进入与退出必须配对 | 必须 | 未复位时市场/对话菜单选项残留或消失 (如不设 `$menuState` 的市场菜单永不显示) |
| `PopulateOptions` / `AddBarEvents` / `AddNewGameChoices` 菜单规则必须加状态或场景条件判断 (`$menuState == ...`、`$market.id == ...`、`$global.isDevMode` 等) | 必须 | 没有条件限定的规则在每个匹配场景都添加选项 (如进任何市场都出现本 MOD 的酒吧选项) |
| 条件列命令副作用与跨文件重复 id 静默跳过 (规则基础页已列) | 必须/事实 | 见规则基础页规则强度表 |

上表的每一条纪律都会直接落进下面的最小例：覆盖规则复刻职责，场景规则用分数赢得竞争，一次性 flag 控制演示次数。

## 组装 Rules 场景实现

在规则基础页的 MOD 布局 (`Starsector\mods\GearIndustry\`，含 `data\campaign\rules.csv` 与 `GearInRulesIntro` 类) 之上，**只追加规则行**。

本节五个场景的使用方全是原版插件 (`RuleBasedInteractionDialogPluginImpl` / `FleetInteractionDialogPluginImpl` / `BarCMD` / `NewGameDialogPluginImpl` / 交易面板)，MOD 侧不需要新增 Java 类：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── rules.csv                     # 追加本节拥有的 GearIn_rules_scene_* 行(与规则基础页的 GearIn_rules_intro_* 同文件共存)
└── jars\
```

### 配置场景规则数据

```csv
GearIn_rules_scene_market,MarketPostDock,"$market.id == jangala score:100
!$player.gearInSceneMarketSeen","$player.gearInSceneMarketSeen = true 0
$menuState = main 0
FireAll PopulateOptions",GearIn 市场场景：首次停靠 jangala 时本规则以 101 分胜过原版 marketDock(1 分)。FireBest 只执行一条规则，所以脚本必须复刻原版职责($menuState + FireAll PopulateOptions)，否则市场菜单不会出现。,,
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
FireAll PopulateOptions",GearIn 对话场景(OpenCommLink)：对独立势力舰队的问候语。脚本把菜单状态置为 fleetConvMain 并 FireAll PopulateOptions 填充对话菜单。,,
GearIn_rules_scene_conv,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent",,GearIn 对话场景(PopulateOptions)：舰队对话菜单选项 —— 点选后经 DialogOptionSelected 回流执行 EndConversation 回到舰队菜单。,"GearIn_rules_scene_conv_end:结束通讯",,
GearIn_rules_scene_conv_end,DialogOptionSelected,$option == GearIn_rules_scene_conv_end,EndConversation,,,
GearIn_rules_scene_ngc_add,AddNewGameChoices,$global.isDevMode,,,6:GearIn_rules_scene_ngc:GearIn 演示起点：额外 5000 星币 (dev),,
GearIn_rules_scene_ngc_pick,NewGameOptionSelected,"$option == GearIn_rules_scene_ngc
$global.isDevMode",NGCAddCredits 5000,,,
```

对应关系：

- **`GearIn_rules_scene_market`(市场场景，trigger `MarketPostDock`)**：条件 `$market.id == jangala score:100` + `!$player.gearInSceneMarketSeen`，总分 101，恒胜原版回退规则 `marketDock`(1 分)：
    - 原版高优先规则 (`MarketCMD checkDebtEffect score:1000`、`checkMercsLeaving score:500`、`DeliveryMission checkCompletion score:100`) 在激活时仍然获胜 —— 演示 "竞争分层"。
    - 脚本**复刻** `marketDock` 职责 (`$menuState = main 0` + `FireAll PopulateOptions`) 并置一次性 flag—— 第二次停靠 jangala 自动回退原版行为。
    - `FireBest` 先 `addText` 显示本规则文本、后 `runScript` 填充菜单。
- **`GearIn_rules_scene_bar_add` / `_pick` / `_back`(酒吧场景)**：`AddBarEvents` 规则条件 `$market.id == jangala`，脚本 `AddBarEvent <id> <文本> <描述>`。
    - 进 jangala 酒吧 (市场菜单 "Take a shuttle down to visit a dockside bar")→ `bar_optionSel` 打印描述并 `BarCMD showOptions` → 清空重建临时选项。
    - 用户点选 `GearIn_rules_scene_bar` → `BarCMD.optionSelected` 写 `$option` 并 `FireBest "DialogOptionSelected"` → `_pick` 显示文本 + `_back` 选项。
    - 点 `_back` → `BarCMD showOptions` 重新显示酒吧选项；
    - "Leave the bar"(`barLeave`，原版 BarCMD 提供)→ `ReturnFromBar` → `$menuState = main` + 市场菜单复位。
- **`GearIn_rules_scene_fleet_hail`(舰队场景，trigger `BeginFleetEncounter`)**：条件 `$faction.id == independent score:100` + `!$entity.isHostile` + `!$player.gearInSceneHailed`，总分 102：
    - 恒胜原版回退规则 `initial_AnyFleet`(1 分)、输给 1000 级剧情规则 (优雅降级)。
    - 脚本按原版模式：置一次性 flag、`FleetDesc` + `$shownFleetDescAlready = true 0`(**复刻 `initial_AnyFleet` 职责**)、`AddText` 呼叫文本、`$hailing = true 0`。
    - 可观察：遭遇独立舰队时主菜单出现高亮 "Accept the comm request"。
- **`GearIn_rules_scene_comm` / `_conv` / `_conv_end`(对话场景，trigger `OpenCommLink` + `PopulateOptions` + `DialogOptionSelected`)。**
    - 接受通讯请求 → `fireBest("OpenCommLink")` → 本规则 (103 分) 胜过原版问候回退规则 (1–3 分；`$entity.ignorePlayerCommRequests score:10000` 时仍由原版拒绝)。
    - 问候文本 + `$menuState = fleetConvMain` + `FireAll PopulateOptions` → `_conv` 加菜单选项 → 点选 → `_conv_end` 执行 `EndConversation`。
    - `FleetInteractionDialogPluginImpl.reinit` 预检 (`BeginFleetEncounter` 无匹配)→ 回舰队主菜单。
    - 原版 `convOptionLeave`(`$isPerson`) 仍提供 `cutCommLink` 选项。
- **`GearIn_rules_scene_ngc_add` / `_pick`(任务/流程场景，trigger `AddNewGameChoices` + `NewGameOptionSelected`)。**
    - devMode(`$global.isDevMode`，`NewGameDialogPluginImpl.init` 写入) 下新游戏对话框出现演示起点选项 (顺序 6，排在原版 ngc 选项之后)。
    - 点选 → `NewGameOptionSelected` 回流 → `NGCAddCredits 5000`(脚本列)—— 之后继续选择原版起始即可，两者叠加。
    - **`NGCAddCredits` 放在回流处理器而非 `AddNewGameChoices` 规则里**：`FireAll` 会执行命中规则的全部脚本，写在 `_add` 里会在列表阶段就加钱。
- **为什么不新增 Java**：规则基础页的最小例需要 Java 是因为它用了自定义 trigger(`GearInRulesIntro`，没有原版派发点)；本节全部使用原版场景 trigger，派发点 (使用方) 是原版插件，规则行本身就是完整的注册与消费流程。需要自定义 trigger 对话时沿用规则基础页的 `GearInRulesIntro` / `REGISTER_MARK` 模式。
- **共享文件纪律**：`data/campaign/rules.csv` 是跨单元共享文件：规则基础页拥有 `GearIn_rules_intro_*`、本节拥有 `GearIn_rules_scene_*`；同前缀内 id 唯一，跨文件重复 id 先到先得的纪律见规则基础页规则强度表。

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，与规则基础页一致)：

```text
gradle jar
```

本节只改数据文件：`rules.csv` 随 MOD 目录被加载器读取，无需进 jar。`jar tf jars\GearIndustry.jar` 的输出里不应该也不需要包含本节新内容。

### 验证方案

前置数据：规则基础页的 MOD 布局 (前置：[C14-S1]；`GearInRulesIntro` 类可保留，本节不依赖它)，追加本节 10 条 `GearIn_rules_scene_*` 规则行；启动器勾选 Dev Mode (验证新游戏场景与 dev 选项)。

1. 规则注册：启动游戏，在 `starsector.log` 中找到本节 10 条 `Loading rule: GearIn_rules_scene_*` 行 (市场 1 + 酒吧 3 + 舰队 1 + 通讯 3 + 新游戏 2；注意 `_conv_end` 与 `_ngc_pick` 也在其中)。
2. 市场场景：新建生涯，停靠 jangala：停靠许可文本后出现 `GearIn 市场场景：首次停靠 jangala...`，市场菜单完整 (通讯目录/酒吧/交易/Leave)；再次停靠 jangala：本节文本不再出现、菜单照常。
3. 酒吧场景：在 jangala 市场菜单点 "Take a shuttle down to visit a dockside bar"：出现酒吧描述文本与 "和 GearIn 演示员聊聊"；点选后出现本节酒吧文本与 "回吧台"；点 "回吧台" 选项重现；"Leave the bar"(ESC) 回到市场菜单。
4. 舰队场景：在超空间或星系内找一支非敌对独立舰队 (拾荒/商队) 并遭遇：出现呼叫文本；通讯选项高亮 "Accept the comm request"；接受后出现本节问候语与 "结束通讯"/"Cut the comm link"；点 "结束通讯" 回到舰队主菜单。
5. 任务/流程场景：主菜单新建游戏：角色创建对话框出现 `GearIn 演示起点：额外 5000 星币 (dev)`(顺序 6)；点选后选 `ngcWolf` 等原版起始；开局确认星币 = 原版初始 + 5000。非 devMode 启动时该选项不出现。

预期结果：

- 规则注册 (加载期)：`starsector.log` 出现本节 10 条 `Loading rule:` 行：`GearIn_rules_scene_market` / `..._bar_add` / `..._bar_pick` / `..._bar_back` / `..._fleet_hail` / `..._comm` / `..._conv` / `..._conv_end` / `..._ngc_add` / `..._ngc_pick`(日志路径与加载行为在规则基础页已证)。标准输出可能出现单次使用变量 WARNING(`System.out.println`，正常现象，非错误)。
- 酒吧场景 (jangala 市场菜单 → 酒吧)：进入酒吧显示 `BarPrintDesc` 描述文本 + 选项 "和 GearIn 演示员聊聊"(描述文本进文本面板)：点选后出现 `GearIn 酒吧场景：临时 bar 选项...` 文本 + "回吧台" 选项；点 "回吧台" 酒吧选项重新出现；"Leave the bar"(ESC) 出现 "You leave the bar..." 文本 + 市场菜单复位。
- 舰队场景：找到一支非敌对的独立势力舰队 (如拾荒舰队) 并接近，遭遇对话出现 `GearIn 舰队场景：一支独立势力舰队呼叫了您...` 文本：舰队主菜单通讯选项高亮为 "Accept the comm request"；接受后出现 `GearIn 对话场景(OpenCommLink)：对独立势力舰队的问候语。` + 菜单选项 "结束通讯" (另有原版 "Cut the comm link")；点 "结束通讯" 回到舰队主菜单。
- 任务/流程场景 (devMode)：启动器勾选 Dev Mode 后新建游戏，角色创建对话框出现 `GearIn 演示起点：额外 5000 星币 (dev)` 选项；点选后继续选任一原版起始，开局星币 = 原版初始 + 5000。非 devMode 时该选项不出现 (`$global.isDevMode` 条件限定)。
- 全程无 ERROR：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志；加载期无 `Duplicate key` / `Command [...] not found` / 赋值/比较操作符错位等规则加载异常 (规则基础页规则强度表的加载期五连)。

## 故障分析

- **市场选项只剩 `Leave`**：覆盖 `MarketPostDock` 后没有复刻设置 `$menuState` 和填充选项的职责。执行 `$menuState = main 0` 与 `FireAll PopulateOptions`，重新停靠后确认主菜单恢复。
- **再次停靠时自定义文本消失**：一次性 memory flag 已经置位，条件不再成立。用 `DumpMemory` 确认该键；需要重复展示时改用可重置条件，再次停靠复验。
- **酒吧没有临时选项**：`AddBarEvents` 的市场条件不匹配，或没有进入 `BarCMD.showOptions`。核对 `$market.id`，重新打开目标市场酒吧确认选项被当轮重建。
- **点击酒吧选项后面板空白**：回流规则只写了文本，没有添加后续选项。补充 options 列或调用菜单填充 trigger，再次点击确认返回选项出现。
- **舰队遭遇没有自定义呼叫文本**：`BeginFleetEncounter` 候选分数低于剧情规则，或条件未命中。核对势力、敌对状态和 score，在遭遇主菜单确认胜出的文本。
- **通讯问候仍为原版**：拒绝通讯规则得分更高，或应答器条件不满足。按目标行为调整条件与 score，再接受通讯确认自定义问候出现。
- **任务列表显示 `No blurb found`**：规则 trigger 与 `getTriggerPrefix()` 生成的名称不一致。统一 `_blurb`、`_option`、`_blurbBar` 和 `_optionBar` 前缀，刷新任务列表确认红字消失。
- **新游戏对话没有演示选项**：规则没有挂在 `AddNewGameChoices`，或回流 trigger 写成了生涯对话版本。使用 `NewGameOptionSelected` 并满足 devMode 条件，重开新游戏对话确认选项出现。
- **交易面板规则报错**：`TradePanelFlavorText` 使用了依赖真实对话框的命令。只保留 `AddText` 和交易模式变量判断，再打开交易面板确认文本正常显示。
- **对话只有文本没有选项**：`$menuState` 或事实键未让 `PopulateOptions` 命中。核对当前 memory 值并修正菜单状态，重新触发后确认至少有返回选项。
- **规则未触发且日志无异常**：规则 ID 跨文件重复，或派发点的 trigger 拼写不一致。改用唯一前缀并逐字核对 trigger，再确认 `Loading rule:` 和运行文本同时出现。
- **`$option` 条件始终为 false**：该条件写在非选项回流 trigger 中，没有任何插件写入 `$option`。把判断移到 `DialogOptionSelected`、`NewGameOptionSelected` 或酒吧回流路径，再点击选项复验。

## 总结

本节把五类生涯场景讲成同一条链：原版派发点在固定时机发起匹配，规则行按条件与分数竞争命中，脚本负责叙述、转场或填充菜单，最终以游戏内文本、菜单与日志给出可观察结果。覆盖市场、酒吧、舰队、新游戏四处的完整最小例把这条链从注册验证到游戏内；场景 trigger 与自定义 trigger 的分界也由此清楚：前者只需写行，后者才需要 Java 入口 (规则基础页已述)。

排错时沿四个位置核对：`Loading rule:` 日志确认注册、trigger 拼写确认匹配、分数确认竞争、职责复刻与状态复位确认行为。`FireBest` 只执行一条，覆盖原版规则必须复刻其结果职责；`FireAll` 全执行，菜单类 trigger 用它批量添加选项。状态键 `$menuState` / `$tradeMode` 进入与退出成对维护；`$option` 回流只属于各自对话框插件的回流 trigger；临时 bar 选项每轮清空重建；任务 trigger 前缀必须与 `getTriggerPrefix()` 一致；`TradePanelFlavorText` 只匹配假 dialog。

从逻辑上看，这套使用方式的下游是三个主题：规则命令体系 (自定义 rulecmd 可替代 `BarCMD` / `EndConversation` 等现成使用方式)、记忆语义 (本节的一次性 flag 与 `$menuState` 的过期与持久化由此完整展开)、对话插件生命周期 (市场/舰队/酒吧场景的 Java 侧底座)。

## 建议参考项

- `data/campaign/rules.csv`(MOD 侧)：本节示例追加行所在文件，规则 id 前缀 `GearIn_rules_scene_*`。
- `Starsector\starsector-core\data\campaign\rules.csv`(原版)：五类场景 trigger 的平台规则表：
    - 本节引用的回退规则 (`marketOpen` / `marketDock` / `marketPostOpenDefault` / `greetingFallthrough` / `initial_AnyFleet` / `marketReturnFromBar` 等) 的参照来源。
- `com.fs.starfarer.api.impl.campaign.rulecmd.RuleBasedInteractionDialogPluginImpl`：交互对话框插件，对话场景 trigger 的主要派发点与 `$option` 回流实现。
- `com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`：舰队遭遇与通讯对话插件，`BeginFleetEncounter` / `OpenCommLink` 的派发点。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧场景的 rulecmd 与对话插件，`AddBarEvents` / `ReturnFromBar` 派发与字符串选项回流。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`：写临时 bar 选项的 rulecmd(`$core_tempBarEvents` 记忆键)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub`：任务列表与任务对话 trigger(`<前缀>_blurb` / `<前缀>_option` / `AddMHCloseOption`) 的使用方。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：任务以酒吧事件展示时的 `<前缀>_blurbBar` / `<前缀>_optionBar` trigger 使用方。
- `com.fs.starfarer.api.impl.campaign.NewGameDialogPluginImpl`：主菜单新游戏对话框 (流程场景)，回流 trigger 为 `NewGameOptionSelected`。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：场景事实键 (`$hasMarket` / `$isHostile` / `$isContact` 等) 的写入方。
- `com.fs.starfarer.api.impl.campaign.rulecmd.EndConversation`：对话退出转场 rulecmd。
- `com.fs.starfarer.api.impl.campaign.rulecmd.OpenComms` / `com.fs.starfarer.api.impl.campaign.rulecmd.HailPlayer`：舰队通讯转场与 `$hailing` 写入 rulecmd。
- `com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl`：弃档时的市场状态复位 (`$menuState` / `$tradeMode` / `$hasMarket`)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：单选派发与 keepOptions(第二参) 语义。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：规则记忆键常量 (`MISSION` 等)。
