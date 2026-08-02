# 势力、货舱、情报与交互对话框

本页把生涯侧四个高频服务入口讲透：势力（`FactionAPI`）、货物（`CargoAPI`）、情报（`IntelManagerAPI` / `IntelInfoPlugin` / `BaseIntelPlugin` / `MessageIntel`）与交互对话框（`InteractionDialogAPI` / `InteractionDialogPlugin`），并给出组合四个入口的完整最小例。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出四个服务入口的进入方式：
  - `SectorAPI.getFaction(id)` / `getAllFactions()` / `getPlayerFaction()`；
  - `SectorEntityToken.getCargo()`（玩家舰队 `CampaignFleetAPI.getCargo()`、子市场 `SubmarketAPI.getCargo()`）；
  - `SectorAPI.getIntelManager()`；`CampaignUIAPI.showInteractionDialog(plugin, target)`；
- 说出 `FactionAPI` 的分组（身份、显示与颜色、关系、资源集合、经济字段、随机人物、doctrine/production/spec）与关系语义：
  - 全局对表、`-1..1` 钳位、`adjustRelationship` 直接加、`setRelationship` 直写；
  - `isHostileTo` = 关系等级 ≤ `HOSTILE`、`getRelToPlayer()` 包装；
- 说出 `CargoAPI` 的栈模型（stack 集合 + credits + 容量统计）、四类写入入口（资源 / 武器 / 战机芯片 / 特殊物品）、`removeItems` 的 NULL 占位栈语义，以及 `getMothballedShips()` 必须先 `initMothballedShips(factionId)` 的必须遵守的规则；
- 说出情报的创建→登记→可见→推进→移除全链：
  - `addIntel`（绕过通信中继可见性检查、直接置可见时间戳）与 `queueIntel`（走中继可见性检查）的差别；
  - `removeAllThatShouldBeRemoved()` 每帧清理、intel 集合随存档保存；
  - 并会写一个 `BaseIntelPlugin` 子类与一条 `MessageIntel` 纯消息；
- 说出交互对话框的插件生命周期（`init` → `optionSelected` → `advance` → `backFromEngagement` → `dismiss`）与常用入口（`setOptionOnEscape` / `makeOptionOpenCore` / `showCargoPickerDialog` / `addOptionSelectedText`）。
  - 并记住 `showInteractionDialog` 可能返回 `false`；
- 从症状定位故障（见「实例驱动故障分析」节），排查时沿**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置复核（沿用本页完整最小例的任务链）。

## 前置知识

本页建立在前置单元的基础上。

- **星区入口与脚本守卫**（S08-P01）：`SectorAPI` 星区结构（本页四个入口都从 `getSector()` 下的 getter 进入）、`onGameLoad` 注册守卫（persistent data 各自键的 `REGISTER_MARK` 模式）。
- **玩家舰队与成员**（S08-P03）：玩家舰队（`getPlayerFleet().getCargo()` 的货物归属）、`FleetDataAPI`（`getMothballedShips()` 返回类型）、`CampaignFleetAPI.getCargo()` 链路。
- **市场与子市场货物**（S08-P04）：子市场 cargo（`getCargo()` 用到时才创建 / `getCargoNullOk()` 可能 null）、商品 id 族（`Commodities`）。
- **全局对象与阶段判断**（S06-P03）：`Global.getSector()` 的 null 语义与「用 `getCurrentState()` 判断阶段」；本页所有读取与注册都发生在生涯引擎建立之后。
- **MOD 插件生命周期**（S06-P02）：`modPlugin` 生命周期与 `CLASS_MARK` 一次性注册守卫。
- **静态字段与 `MutableStat` 的区分**（S07-P02）：注意 `FactionAPI.getTariffFraction()` 取的是势力规格（`FactionSpecAPI`）里的普通数值，不是 `MutableStat`（与 S08-P04 的 `MarketAPI.getTariff()` 不同）。

## 术语

按术语约定，本页首次完整讲解四个词（后续页面继续使用；API 类名保留源码大小写）：

- **势力（faction）**：API 名保留 `FactionAPI`。一个身份完整的派系对象：id、显示名、颜色、关系表、已知资源集合、随机人物与 `.faction` 规格。玩家自身也是一个势力（`Factions.PLAYER`）。
- **货物（cargo）**：API 名保留 `CargoAPI`。实体（舰队、战利品、子市场、市场）持有的货物容器：栈集合 + 信用（credits）+ 容量统计。单个货物条目是 `CargoStackAPI`（栈）。
- **情报（intel）**：API 名保留 `IntelInfoPlugin`（单条情报约定）与 `IntelManagerAPI`（登记集合）。
  - 一条情报 = 一个实现了 `IntelInfoPlugin` 的插件实例，同时承载 Intel 页 UI 描述、消息更新与每帧推进（`BaseIntelPlugin` 兼为 `EveryFrameScript`）。
- **交互对话框（interaction dialog）**：API 名保留 `InteractionDialogAPI`（对话框本体）与 `InteractionDialogPlugin`（对话框内容插件）。生涯 UI 承载的模态对话：文本面板 + 选项面板 + 视觉面板。

## 文件与 API 链路

### 四类服务入口总览

| 服务 | 进入方式 | 核心接口 |
|---|---|---|
| 势力 | `Global.getSector().getFaction(id)` / `getAllFactions()` / `getPlayerFaction()` | `FactionAPI` |
| 货物 | `SectorEntityToken.getCargo()`（玩家舰队 `CampaignFleetAPI.getCargo()` 经 `SectorEntityToken` 实现；子市场 `SubmarketAPI.getCargo()` 见前置知识 S08-P04）| `CargoAPI` / `CargoStackAPI` |
| 情报 | `Global.getSector().getIntelManager()` | `IntelManagerAPI` / `IntelInfoPlugin` |
| 交互对话框 | `Global.getSector().getCampaignUI().showInteractionDialog(plugin, target)` | `InteractionDialogAPI` / `InteractionDialogPlugin` |

### 势力（FactionAPI）

`FactionAPI`（`com.fs.starfarer.api.campaign.FactionAPI`）是纯接口，势力集合由引擎统一管理。RC8 的成员按功能分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()`、`getDisplayName()`、`getDisplayNameWithArticle()`、`getDisplayNameIsOrAre()` | 势力 id（全局唯一，与 `.faction` 文件 id 一致）与显示名变体 |
| 颜色 | `getColor()`、`getBaseUIColor()`、`getGridUIColor()`、`getDarkUIColor()`、`getSecondaryUIColor()`、`getBrightUIColor()`、`getRelColor(id)` / `getRelColor(RepLevel)` | UI 配色族；`getRelColor` 按关系值混合敌我色 |
| 关系 | `getRelationship(id)`、`getRelationshipLevel(id)`、`setRelationship(id, float)`、`setRelationship(id, RepLevel)`、`adjustRelationship(id, delta)`、`adjustRelationship(id, delta, limit)`、`ensureAtBest`、`ensureAtWorst`、`isAtWorst` / `isAtBest`、`isHostileTo`、`getRelToPlayer()` | 见「关系语义」|
| 资源集合 | `getKnownShips()`、`addKnownShip`、`removeKnownShip`、`knowsShip`、`getKnownWeapons()`、`getKnownFighters()`、`getKnownIndustries()`、priority 族、`clearShipRoleCache()` | 已知蓝图与优先资源；`removeKnown*` 的读档回写规则见「规则强度」|
| 经济字段 | `getTariffFraction()`、`getTollFraction()`、`getFineFraction()`、`getInternalCommsChannel()`、`isIllegal(String)`、`getIllegalCommodities()`、`makeCommodityIllegal/Legal` | 势力级税率（注意：静态值，非 `MutableStat`）与商品合法性 |
| 随机与显示 | `createRandomPerson()`、`getLogo()`、`getCrest()`、`isShowInIntelTab()` / `setShowInIntelTab`、`getCustom()`、`getCustomBoolean`、`getCustomString`、`getRank`、`getPost`、`pickRandomShipName` | `.faction` 文件数据与随机生成 |
| 舰船选择 | `pickShip(role, params, ...)`、`pickShipAndAddToFleet`、`ShipPickParams` / `ShipPickMode`、`getNumAvailableForRole` | 自动装配舰船选择（本页只给入口，深度归后续单元）|
| 配置 | `getDoctrine()`、`getProduction()`、`getFactionSpec()`、`getMemory()`、`getMemoryWithoutUpdate()`、`isNeutralFaction()`、`isPlayerFaction()` | doctrine / production / 规格与势力 memory |

**关系语义（全局对表）**：

1. 关系存储在一张**全局对表**里：键 `idA_idB` 与 `idB_idA` 共享同一个对条目，新对默认 `0.0`（同势力对默认 `1.0`）；写入时把值钳位到 `[-1, 1]`。
   - 因此 `getRelationship(id)` 读到的是「该势力视角下与目标势力」的值，读取未建立的对会**用到时才创建**默认 0 的对条目。
2. `getRelationship(id)` 直接返回对表值；`getRelationshipLevel(id)` = `RepLevel.getLevelFor(值)`。
   - `RepLevel` 阈值：`T1=0.09`、`T2=0.24`、`T3=0.49`、`T4=0.74`；`getMin()` 不包含边界、`getMax()` 包含边界（javadoc 原文）。
3. `adjustRelationship(id, delta)` **直接相加**并经 `setValue` 钳位，不触发任何监听；`adjustRelationship(id, delta, limit)` 按 `limit` 等级封顶/封底（正方向默认 `COOPERATIVE`、负方向默认 `VENGEFUL`）。
   - `setRelationship(id, float)` 直写；`setRelationship(id, RepLevel)` 写到等级边界：`NEUTRAL` = 0、正级 = `getMin()+0.01`、负级 = `-getMin()-0.01`。
4. `isHostileTo(id)` = `isAtBest(id, HOSTILE)`：关系等级 ≤ `HOSTILE` 即敌对。
5. 玩家视角包装：`getRelToPlayer()` 返回 `RelationshipAPI`（`com.fs.starfarer.api.characters.RelationshipAPI`：`getRel()`、`getLevel()`）；玩家势力对玩家自己 = 同势力对默认 `1.0`。
6. **玩家声望 action 的正规路径**是 `SectorAPI.adjustPlayerReputation(action, factionId/person)`（javadoc："This method will pick the highest-priority ReputationActionResponsePlugin and call its handlePlayerReputationAction() method"；原版 `AdjustRep` rulecmd 同款调用，`com.fs.starfarer.api.impl.campaign.rulecmd.AdjustRep`）。
   - `FactionAPI` 的关系写入是**原始写入**，不经过 response plugin 的 delta 计算，也不产生声望监听通知。

### 货物（CargoAPI）

`CargoAPI`（`com.fs.starfarer.api.campaign.CargoAPI`）是纯接口。结构三件套：**栈集合**（`stacks`，`getStacksCopy()` 返回副本）、

**信用**（`credits` 字段，`getCredits()` 返回 `MutableValue`——`com.fs.starfarer.api.util.MutableValue`：`get()` / `set()` / `add()` / `subtract()`）、

**容量统计**（`maxCapacity` / `maxFuel` / `maxPersonnel` / `spaceUsed`）。

读写分族（方法均位于 `CargoAPI`）：

| 分族 | 写入 | 读取 | 底层 |
|---|---|---|---|
| 补给 / 燃料 | `addSupplies` / `removeSupplies`、`addFuel` / `removeFuel` | `getSupplies` / `getFuel` | 资源栈 `"supplies"` / `"fuel"`；`getSupplies` 另含 `extraSuppliesUsed` |
| 人员 | `addCrew` / `removeCrew`、`addMarines` / `removeMarines` | `getCrew` / `getMarines` / `getTotalCrew` / `getTotalPersonnel` | 资源栈 `"crew"` / `"marines"`（整数语义；`crew` 写入触发携带舰队 `setSyncNeeded`）|
| 通用商品 | `addCommodity` / `removeCommodity` | `getCommodityQuantity` | `addItems(RESOURCES, id, qty)` |
| 武器 | `addWeapons` / `removeWeapons` | `getNumWeapons` / `getWeapons` | `WEAPONS` 栈（整数、roundSize）|
| 战机芯片 | `addFighters` / `removeFighters` | `getNumFighters` / `getFighters` | `FIGHTER_CHIP` 栈 |
| 船插 / 特殊物品 | `addHullmods` / `addSpecial` | `getQuantity` | `SPECIAL` 栈（`SpecialItemData`；船插 data = `new SpecialItemData("modspec", hullModId)`）|
| mothballed ships | `addMothballedShip` | `getMothballedShips` | 见「必须遵守的规则」|
| 通用汇总 | `addItems` / `removeItems`（返回是否完成）| `getQuantity` / `getStacksCopy` | 栈模型 |

**栈语义（`CargoStackAPI`，`com.fs.starfarer.api.campaign.CargoStackAPI`）**： `getType()`（`CargoItemType`：`RESOURCES` / `WEAPONS` / `FIGHTER_CHIP` / `SPECIAL` / `NULL`）、 `getData()`（通常是 String 资源 id）、

`getSize()` / `getFree()` / `getMaxSize()` / `isFull()`、`getCargoSpace()`。

`isNull()`（**空占位栈**，javadoc："If true, it's an empty cargo stack. These get created for spacing, a result of the player moving cargo around."）。资源的小数部分会累计（`addItems` 内部路径）；

**`removeItems` 把减到 1.0 以下的栈原地替换为 `NULL_STACK` 占位**。

::: warning 回滚后栈数量对不上，不是 bug

「数值回滚了，`getStacksCopy().size()` 却没变回原样」是 `removeItems` 的占位语义而不是错误：减空的栈被原地替换为 `NULL_STACK` 占位（`isNull()` 为 true），要多出来的占位清掉，必须调 `removeEmptyStacks()`。

:::

**必须遵守的规则（mothballed ships）**：`getMothballedShips()` 返回的是**可能为 null 的字段**，使用前**必须**先调 `initMothballedShips(factionId)`。

`CargoAPI` javadoc 原文："Call initMothballedShips(String factionId) before using this method."；

`addMothballedShip` 内部会自动以 `"independent"` 初始化（成员创建后置 `setMothballed(true)`）。

::: caution 使用 `getMothballedShips()` 前必须先初始化

违反必须遵守的规则的直接可观察结果：`getMothballedShips()` 返回 null 解引用即 NPE。而 `addMothballedShip` 内部会自动初始化，不需要您自己先 init—— 只有**读取**路径要求先行 `initMothballedShips(factionId)`。

:::

**容量统计**： `getMaxCapacity()` / `getMaxFuel()` / `getMaxPersonnel()` / `getSpaceUsed()` / `getSpaceLeft()` / `getFreeCrewSpace()` / `getFreeFuelSpace()`；`updateSpaceUsed()` 重算已用空间。

玩家舰队的容量上限由 `FleetDataAPI` 同步管线按成员求和写入（前置知识已记录）—— **直接改 cargo 数值不会重算容量**。

### 情报（IntelManagerAPI / IntelInfoPlugin / BaseIntelPlugin / MessageIntel）

**入口**：`SectorAPI.getIntelManager()` → `IntelManagerAPI`（`com.fs.starfarer.api.campaign.comm.IntelManagerAPI`）。intel 集合随存档保存，读档后仍然可用。

::: info 为什么情报集合随存档保存

intel 是生涯状态的组成部分：玩家可见时间戳、`NEW` 标记窗口、结束倒计时都要跨存档存活，所以情报集合随存档一起保存。这也是「演示情报必须当帧移除」的原因—— `addIntel` 进去不删，就会永远留在存档里。

:::

**创建与登记两条链**：

1. **`addIntel(plugin)`**（`IntelManagerAPI`）直接加入已知集合：置玩家可见时间戳、调 `reportMadeVisibleToPlayer()`、`autoAddCampaignMessage()`（默认 true）时经 `CampaignUIAPI.addMessage` 弹左下消息（`IntelManagerAPI.addIntel`）。
   - **`addIntel` 绕过通信中继可见性检查**——`IntelInfoPlugin.canMakeVisibleToPlayer` javadoc 原文："Only checked if adding using IntelManager.queueIntel(). addIntel() bypasses this and all other checks."。
   - `addIntel(plugin, forceNoMessage)` / `addIntel(plugin, forceNoMessage, textPanel)` 控制消息行为。
2. **`queueIntel(plugin[, maxCommQueueDays])`**（`IntelManagerAPI`）加入通信队列；玩家进入通信中继范围或 `forceAddNextFrame()` 时，转为可见 intel 并出队。
   - 同批最多 3 条、第 4 条起合并为一条 "New messages" 汇总。

**每帧推进与清理**：未暂停且有玩家舰队时，情报管理器每帧推进——通信队列的可见性检查与 `removeAllThatShouldBeRemoved()` 都在这条路径；

`shouldRemoveIntel()` 返回 true 的情报被移除并调 `reportRemovedIntel()`；`IntelManagerAPI.removeIntel` 会同时移除并出队。

**`BaseIntelPlugin`**（`com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`）是自带的完整默认实现（同时实现 `IntelInfoPlugin` / `EveryFrameScript` / `CallableEvent` / `CommSnifferReadableIntel`），MOD 自定义情报的标准父类。

常用默认行为：

| 成员 | 语义 |
|---|---|
| `advance(amount)` | 把秒数换算成游戏日推进 ending 倒计时；`isDone()` = `isEnded()` |
| `runWhilePaused()` | 默认 `false`（暂停不推进）|
| `endImmediately()` / `endAfterDelay()` / `endAfterDelay(days)` | 结束流程；`getBaseDaysAfterEnd()` 默认 **3 游戏日** |
| `createIntelInfo` / `getName()` | 列表条目 = 名称 + 子弹行；`getName()` 返回 null 则不显示 |
| `createSmallDescription` | 小描述（默认「请覆写」）；`hasSmallDescription()` 默认 true |
| `getIcon()` | null = 无图标（`IntelInfoPlugin` javadoc："40x40, no icon if null."）|
| `getIntelTags(map)` | 默认含 `INTEL_IMPORTANT` / `INTEL_NEW` / 中继范围内的 `INTEL_LOCAL`；**map 为 null 时安全返回**（基类内部已做 null 判断）|
| `sendUpdate` / `sendUpdateIfPlayerHasIntel` | 更新消息；后者要求已有玩家可见时间戳 |
| `BUTTON_DELETE` / `addDeleteButton` / `buttonPressConfirmed` | 删除按钮（确认框 + `endImmediately`）|
| `getCommMessageSound` | 发布/更新音效（`ui_intel_something_posted` / `ui_intel_update` 等常量）|

**`MessageIntel`**（`com.fs.starfarer.api.impl.campaign.intel.MessageIntel`）：左下消息区的**纯消息**类，javadoc 原文：

"For campaign messages that show up in the lower-left corner, but are not tied to an actual piece of intel."——

`new MessageIntel(text[, color[, highlights]])`、`addLine(...)` 多行追加、`getCommMessageSound` 默认 `ui_intel_minor_message`；用 `CampaignUIAPI.addMessage(IntelInfoPlugin)` 发送。

**`IntelInfoPlugin` 约定要点**（`com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`）： `getIntelTags(map)` **必须处理 map 为 null**（javadoc："Method NEEDS to handle map being null gracefully."）；

`NEW_DAYS = 5`（新情报「NEW」标记窗口）；`IntelSortTier` 排序档；

`getMapLocation(map)` 与 `getArrowData(map)` 控制地图定位与箭头； `isHidden()` 让已可见情报在 UI 与消息中隐藏（`BaseIntelPlugin` 默认实现含教程状态判断）。

### 交互对话框（InteractionDialogAPI / InteractionDialogPlugin）

**打开**：`CampaignUIAPI.showInteractionDialog(plugin, interactionTarget)`（`CampaignUIAPI`）——javadoc 原文：

"Returns true if dialog was actually shown, false otherwise (if, for example, UI is already showing another dialog)."、

"interactionTarget can be null."。

**插件生命周期（`InteractionDialogPlugin`）**：`init(dialog)` → `optionSelected(optionText, optionData)` / `optionMousedOver` → 每帧 `advance(amount)` → 战斗返回 `backFromEngagement(result)`。

`getContext()` / `getMemoryMap()`（Rules 侧用）。

对话框本体（`InteractionDialogAPI`）常用入口分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 面板 | `getOptionPanel()`、`getTextPanel()`、`getVisualPanel()` | 三个面板的访问入口 |
| 内容写入 | `setPromptText`、`setOptionOnEscape(text, optionId)`、`setOptionOnConfirm`、`addOptionSelectedText`、`setOptionColor`、`makeStoryOption` | 提示文本、ESC/确认键行为、选项点击后的回显 |
| 布局 | `setTextWidth`/`setTextHeight`/`setXOffset`/`setYOffset`、`setOpacity`、`setBackgroundDimAmount`、`hideTextPanel`/`showTextPanel`、`hideVisualPanel`/`showVisualPanel` | 尺寸、透明度与面板显隐 |
| 关闭 | `dismiss()`、`dismissAsCancel()` | 正常关闭 / 以取消语义关闭 |
| picker | `showCargoPickerDialog`、`showFleetMemberPickerDialog`、`showFleetMemberRecoveryDialog`、`showIndustryPicker`、`showCustomDialog`、`showCustomVisualDialog`、`showCampaignEntityPicker`、`showGroundRaidTargetPicker`、`showCommDirectoryDialog` | 各类选择器 |
| core UI 与战斗 | `makeOptionOpenCore(optionId, tabId, mode)`、`startBattle(context)` | 打开 core UI 页签 / 从对话框开战 |
| 上下文 | `getInteractionTarget()`、`getPlugin()`、`setPlugin`、`setInteractionTarget` | 交互目标与插件互写 |

::: warning 选项分支判断用 `optionData`，不要用 `optionText`

`optionSelected(optionText, optionData)` 的第一个参数是用户看到的显示文本（可随 UI 文案变化），第二个才是 `addOption` 时传入的 id。按 `optionText` 做分支会在文案改动后静默失效（见故障分析第 9 条）。

:::

::: tip 把 `showInteractionDialog` 的返回值写进日志

对话框在已有对话框显示时会静默返回 `false`——不记录返回值，故障就只剩「对话框没弹出来」这个没有报错的现象。本页演示正是把 `shown` 布尔值记入日志，`shown=false` 时其余演示不受影响。

:::

**面板常用成员**：`OptionPanelAPI`（`com.fs.starfarer.api.campaign.OptionPanelAPI`）——`addOption(text, data[, tooltip])`、 `addOption(text, data, color, tooltip)`、`clearOptions`。

`setEnabled(data, enabled)`（javadoc："Only works for options, not selectors."）。

`setShortcut`、`addSelector`、 `addOptionConfirmation`、`removeOption`、`setStoryOptionParams`。

`TextPanelAPI`（`com.fs.starfarer.api.campaign.TextPanelAPI`）——`addPara(text[, color])`、 `addPara(format, hl, highlights)`、`addParagraph`、`highlightInLastPara`、`setHighlightColorsInLastPara`、`clear`。

另有 `getDialog`、`beginTooltip`/`addTooltip`。

## 规则强度

本页必须遵守的规则清单（违反后的可观察结果均附上）：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| `getIntelTags(SectorMapAPI map)` 必须处理 map 为 null（`IntelInfoPlugin` javadoc："Method NEEDS to handle map being null gracefully."）| 必须 | 地图 tooltip / Intel 页地图渲染路径 NPE |
| `getMothballedShips()` 使用前必须先调 `initMothballedShips(factionId)`（`CargoAPI` javadoc 原文）| 必须 | `getMothballedShips()` 返回 null 解引用 NPE |
| `removeKnownShip` / `removeKnownWeapon` / `removeKnownFighter` 想永久生效，必须把「要移除的清单」持久化并在每次读档重新移除（`FactionAPI` javadoc 原文："...needs to be stored and they need to be re-removed on every game load."）| 必须 | 读档后 `.faction` 定义的蓝图重新回到已知集合 |
| 直接改动 known / priority 集合后调 `clearShipRoleCache()`（`FactionAPI` javadoc："Should be called after direct manipulation..."）| 建议 | 再次的 role 选择继续使用旧缓存 |
| 玩家声望调整（任务/事件等 action 语义）走 `SectorAPI.adjustPlayerReputation`（`SectorAPI` javadoc）| 建议 | 直接写 `FactionAPI` 关系绕过 `ReputationActionResponsePlugin` 的 delta 计算与监听通知 |
| `addIntel` 绕过 `canMakeVisibleToPlayer` 检查（`IntelInfoPlugin` javadoc 原文）| 事实 | `addIntel` 立即可见并弹消息；`queueIntel` 才走通信中继可见性检查 |
| `showInteractionDialog` 在已有对话框显示时返回 false（`CampaignUIAPI` javadoc 原文）| 事实 | 调用方不检查返回值时对话框「没弹出来」且无报错 |
| `CargoAPI.setFreeTransfer` / `isFreeTransfer` 已废弃（`CargoAPI` `@Deprecated`，"Use SectorEntityToken.setFreeTransfer() instead."）| 事实 | 实体转移费用语义由实体 free transfer 入口表达 |

## 完整最小例

在既有 MOD 工程（布局与插件入口 `GearInModPlugin`）之上新增七个类：一个抽象基类、四个服务演示脚本与两个插件类。一文件一角色，不新增任何资源文件；四个脚本在 `onGameLoad` 各自注册，首个 `advance()` 各执行一次演示并自停：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInOneShotScript          # 本页新增（抽象基类，不参与注册）
│       ├── GearInFactionServicesScript  # 本页新增（势力段脚本）
│       ├── GearInCargoServicesScript    # 本页新增（货物段脚本）
│       ├── GearInIntelServicesScript    # 本页新增（情报段脚本）
│       ├── GearInDialogServicesScript   # 本页新增（对话框段脚本）
│       ├── GearInServicesIntel          # 本页新增（情报插件）
│       └── GearInServicesDialog         # 本页新增（对话框插件）
└── jars\
```

### GearInOneShotScript（抽象基类）

**职责**：一次性演示脚本骨架——封装 `EveryFrameScript` 三方法（`isDone` / `runWhilePaused` / `advance`），首次 `advance()` 执行一次子类实现的 `runDemo()` 后自停。

**绑定注册入口**：不参与任何注册；四个服务演示脚本继承它，各自负责自己的注册。

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * 一次性演示脚本基类：挂载后第一次 advance() 执行一次子类实现的 runDemo() 并自停
 * （isDone() 返回 true，引擎在下一帧自动从脚本列表移除本脚本）。
 *
 * 四个服务演示脚本（势力 / 货物 / 情报 / 对话框）都继承本类，各自只实现 runDemo()，
 * 避免每个演示类重复 EveryFrameScript 的骨架方法。本类是抽象基类，不参与任何注册；
 * 各子类的注册在 onGameLoad 的 persistent data 门内各自执行（见页面调用点片段）。
 */
public abstract class GearInOneShotScript implements EveryFrameScript {

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInOneShotScript() {
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
        runDemo();
    }

    /** 子类实现一次演示：只在第一次 advance() 时被调用，全部行为在一个调用内完成。 */
    protected abstract void runDemo();
}
```

### GearInFactionServicesScript（势力段脚本）

**职责**：`FactionAPI` 常用入口演示——读取玩家势力身份/颜色与对霸主、海盗、独立势力的关系（数值、等级、敌对判定、`getRelToPlayer` 包装）；对霸主执行一次 `adjustRelationship(+0.05)` 并立即 `setRelationship` 精确回滚；

读取 tariff/toll/fine、非法商品、已知资源集合规模、`isShowInIntelTab`。

**绑定注册入口**：onGameLoad 的 persistent data 守卫 `addScript` 注册，守卫键 `REGISTER_MARK = "GearIn_FactionServices"`（与调用点片段逐字一致）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例（势力段）：FactionAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家势力身份/颜色与对霸主/海盗/独立势力的关系（数值、等级、敌对判定、getRelToPlayer
 * 包装）；对霸主 adjustRelationship(+0.05) 后立即 setRelationship 精确回滚（"写入后必须回滚"
 * 基线写法）；读取 tariff/toll/fine、非法商品、已知资源集合规模、isShowInIntelTab。不遗留持久状态。
 */
public class GearInFactionServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_FactionServices";

    private static final Logger LOG = Global.getLogger(GearInFactionServicesScript.class);

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        FactionAPI playerFaction = sector.getPlayerFaction();
        FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
        FactionAPI pirates = sector.getFaction(Factions.PIRATES);
        FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);

        LOG.info(String.format(
                "GearIn campaign services faction: player=%s(%s) baseColor=%s darkColor=%s | hegemony=%s pirates=%s",
                playerFaction.getId(), playerFaction.getDisplayNameWithArticle(),
                playerFaction.getBaseUIColor(), playerFaction.getDarkUIColor(),
                hegemony.getDisplayName(), pirates.getDisplayName()));

        LOG.info(String.format(
                "GearIn campaign services faction relations: hegemony=%s(%s) hostile=%s | pirates=%s(%s) hostile=%s | independent=%s(%s) hostile=%s",
                fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                playerFaction.isHostileTo(Factions.HEGEMONY),
                fmt(playerFaction.getRelationship(Factions.PIRATES)),
                playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                playerFaction.isHostileTo(Factions.PIRATES),
                fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName(),
                playerFaction.isHostileTo(Factions.INDEPENDENT)));

        // 关系写入并精确回滚：adjustRelationship 直接加、setRelationship 直写原值（势力管理器钳位 [-1,1]）
        float relBefore = hegemony.getRelationship(Factions.PLAYER);
        hegemony.adjustRelationship(Factions.PLAYER, 0.05f);
        float relDuring = hegemony.getRelationship(Factions.PLAYER);
        hegemony.setRelationship(Factions.PLAYER, relBefore);
        float relAfter = hegemony.getRelationship(Factions.PLAYER);
        LOG.info(String.format(
                "GearIn campaign services faction rel write-rollback: %s -> %s -> %s (rolled back)",
                fmt(relBefore), fmt(relDuring), fmt(relAfter)));
        LOG.info(String.format(
                "GearIn campaign services faction misc: relToPlayer=%s(%s) tariff=%.3f toll=%.3f fine=%.3f drugsIllegal=%s illegalCount=%d knownShips=%d knownWeapons=%d knownFighters=%d showInIntelTab=%s",
                fmt(playerFaction.getRelToPlayer().getRel()),
                playerFaction.getRelToPlayer().getLevel().getDisplayName(),
                playerFaction.getTariffFraction(), playerFaction.getTollFraction(), playerFaction.getFineFraction(),
                playerFaction.isIllegal(Commodities.DRUGS), playerFaction.getIllegalCommodities().size(),
                playerFaction.getKnownShips().size(),
                playerFaction.getKnownWeapons().size(),
                playerFaction.getKnownFighters().size(),
                playerFaction.isShowInIntelTab()));
    }
}
```

### GearInCargoServicesScript（货物段脚本）

**职责**：`CargoAPI` 常用入口演示——读取玩家舰队补给/燃料/陆战队员/信用/空间/栈数基线；`addSupplies(100)` / `addFuel(50)` / `addMarines(10)` 后读取中间值，再同量 remove 回滚并读取终值（验证数值精确复原）；

读取重型机械商品量与栈列表前三条（`CargoStackAPI` 的 type/data/size/isNull）。

**绑定注册入口**：onGameLoad 的 persistent data 守卫 `addScript` 注册，守卫键 `REGISTER_MARK = "GearIn_CargoServices"`（与调用点片段逐字一致）。

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * 完整最小例（货物段）：CargoAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家舰队补给/燃料/陆战队员/信用/空间/栈数基线；addSupplies(100)/addFuel(50)/addMarines(10)
 * 后读取中间值，再 remove 同量回滚并读取终值（验证数值精确复原）；读取重型机械商品量与栈列表
 * 前几条（CargoStackAPI 的 type/data/size/isNull）。不遗留持久状态。
 */
public class GearInCargoServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CargoServices";

    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        CargoAPI cargo = sector.getPlayerFleet().getCargo();
        float suppliesBefore = cargo.getSupplies();
        float fuelBefore = cargo.getFuel();
        int marinesBefore = cargo.getMarines();
        float creditsBefore = cargo.getCredits().get();
        float spaceBefore = cargo.getSpaceUsed();
        float capacityBefore = cargo.getMaxCapacity();
        int stacksBefore = cargo.getStacksCopy().size();
        float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);

        cargo.addSupplies(100f);
        cargo.addFuel(50f);
        cargo.addMarines(10);
        int stacksDuring = cargo.getStacksCopy().size();
        float suppliesDuring = cargo.getSupplies();
        float fuelDuring = cargo.getFuel();
        int marinesDuring = cargo.getMarines();

        cargo.removeSupplies(100f);
        cargo.removeFuel(50f);
        cargo.removeMarines(10);
        int stacksAfter = cargo.getStacksCopy().size();
        int nullStacksAfter = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isNull()) {
                nullStacksAfter++;
            }
        }
        float suppliesAfter = cargo.getSupplies();
        float fuelAfter = cargo.getFuel();
        int marinesAfter = cargo.getMarines();

        LOG.info(String.format(
                "GearIn campaign services cargo write-rollback: supplies %.0f -> %.0f -> %.0f | fuel %.0f -> %.0f -> %.0f | marines %d -> %d -> %d | machinery %.0f | credits %.0f | space %.0f/%.0f",
                suppliesBefore, suppliesDuring, suppliesAfter, fuelBefore, fuelDuring, fuelAfter,
                marinesBefore, marinesDuring, marinesAfter, machineryBefore, creditsBefore, spaceBefore, capacityBefore));
        LOG.info(String.format("GearIn campaign services cargo stacks: before=%d during=%d after=%d nullAfter=%d",
                stacksBefore, stacksDuring, stacksAfter, nullStacksAfter));

        // 栈列表前三条（CargoStackAPI：type / data / size / isNull）
        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        int stacksShown = Math.min(3, stacks.size());
        for (int i = 0; i < stacksShown; i++) {
            CargoStackAPI stack = stacks.get(i);
            LOG.info(String.format("GearIn campaign services cargo stack[%d]: type=%s data=%s size=%.0f null=%s",
                    i, stack.getType(), stack.getData(), stack.getSize(), stack.isNull()));
        }
    }
}
```

### GearInIntelServicesScript（情报段脚本）

**职责**：`IntelManagerAPI` 常用入口演示——创建自定义情报 `GearInServicesIntel`（`BaseIntelPlugin` 子类）`addIntel` 登记、`hasIntel` 验证、 `removeIntel` 移除（不留存档残留）；再用 `MessageIntel` 发一条纯消息（左下消息区）。

**绑定注册入口**：onGameLoad 的 persistent data 守卫 `addScript` 注册，守卫键 `REGISTER_MARK = "GearIn_IntelServices"`（与调用点片段逐字一致）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例（情报段）：IntelManagerAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK
 * 一次性门控（每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：创建自定义情报 GearInServicesIntel（extends BaseIntelPlugin）addIntel 登记、
 * hasIntel 验证、removeIntel 移除（不留存档残留）；再用 MessageIntel 发一条纯消息
 * （左下消息区）。演示全程不遗留持久状态。
 */
public class GearInIntelServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_IntelServices";

    private static final Logger LOG = Global.getLogger(GearInIntelServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        IntelManagerAPI intelManager = sector.getIntelManager();
        GearInServicesIntel intel = new GearInServicesIntel();
        intelManager.addIntel(intel);
        boolean present = intelManager.hasIntel(intel);
        int intelCount = intelManager.getIntel(GearInServicesIntel.class).size();
        LOG.info(String.format(
                "GearIn campaign services intel: added present=%s count=%d (campaign message posted)",
                present, intelCount));
        intelManager.removeIntel(intel);
        boolean absent = !intelManager.hasIntel(intel);
        LOG.info(String.format(
                "GearIn campaign services intel: removed absent=%s count=%d",
                absent, intelManager.getIntel(GearInServicesIntel.class).size()));

        MessageIntel message = new MessageIntel(
                "GearIn 生涯服务速览：纯消息情报（MessageIntel）演示", Misc.getTextColor());
        message.addLine("addLine 追加的第二行：消息不绑定情报页面，只在左下消息区显示。",
                Misc.getGrayColor());
        CampaignUIAPI ui = sector.getCampaignUI();
        ui.addMessage(message);
        LOG.info("GearIn campaign services intel: message-only intel posted");
    }
}
```

### GearInDialogServicesScript（对话框段脚本）

**职责**：`InteractionDialogAPI` 常用入口演示—— 用 `CampaignUIAPI.showInteractionDialog(plugin, null)` 打开演示对话框（`GearInServicesDialog`），记录返回的 `shown` 布尔值（已有对话框显示时返回 false）。

**绑定注册入口**：onGameLoad 的 persistent data 守卫 `addScript` 注册，守卫键 `REGISTER_MARK = "GearIn_DialogServices"`（与调用点片段逐字一致）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;

/**
 * 完整最小例（对话框段）：InteractionDialogAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK
 * 一次性门控（每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：用 CampaignUIAPI.showInteractionDialog(plugin, null) 打开演示对话框
 * （GearInServicesDialog implements InteractionDialogPlugin），记录返回的 shown 布尔值
 * （已有对话框显示时返回 false，见 javadoc）；对话框由玩家点选选项后自行关闭，
 * 本脚本不留持久状态。
 */
public class GearInDialogServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_DialogServices";

    private static final Logger LOG = Global.getLogger(GearInDialogServicesScript.class);

    @Override
    protected void runDemo() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(new GearInServicesDialog(), null);
        LOG.info("GearIn campaign services dialog: shown=" + shown
                + " (interactionTarget=null; 玩家在对话框内点选选项后由选项回调关闭)");
    }
}
```

### GearInServicesIntel（情报插件）

**职责**：自定义情报插件（`BaseIntelPlugin` 子类）——实现名称/图标/小描述/Intel 标签；`getIcon()` 返回 null 走默认图标； `getIntelTags(map)` 先调 `super.getIntelTags(map)`（基类默认实现内部已做 null 判断）再追加自定义 tag。

**绑定注册入口**：由 `GearInIntelServicesScript` 经 `IntelManagerAPI.addIntel(plugin)` 登记（`addIntel` 绕过可见性检查，立即置玩家可见时间戳并弹出左下消息）。

```java
package data.scripts.gearin.campaign;

import java.util.Set;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例（情报插件）：自定义情报（IntelInfoPlugin 的默认实现 BaseIntelPlugin 子类）。
 *
 * 绑定位置：由 GearInIntelServicesScript 经 IntelManagerAPI.addIntel(plugin) 登记
 * （addIntel 绕过可见性检查，立即置玩家可见时间戳并弹出左下消息）。
 *
 * 契约要点：getIcon() 返回 null 时 Intel 页面使用默认图标（IntelInfoPlugin javadoc：
 * "40x40, no icon if null."）；getIntelTags(map) 必须处理 map 为 null
 * （javadoc："Method NEEDS to handle map being null gracefully."）——本类先调
 * super.getIntelTags(map)，基类默认实现内部已做 null 判断，再追加自定义 tag。
 */
public class GearInServicesIntel extends BaseIntelPlugin {

    @Override
    public String getName() {
        return "GearIn 服务速览（自定义情报演示）";
    }

    @Override
    public String getIcon() {
        return null; // null = 无图标，Intel 页面使用默认图标
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("这是一条由 GearInIntelServicesScript 创建并立即移除的演示情报。", Misc.getTextColor(), 0f);
        info.addPara("登记入口 addIntel(plugin) 会直接置玩家可见时间戳并弹出左下消息；"
                + "本演示在同一帧调用 removeIntel(plugin) 移除，不留存档残留。", Misc.getGrayColor(), 10f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        // 基类默认实现已处理 map == null（map 非 null 时才计算 INTEL_LOCAL 距离）
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        return tags;
    }
}
```

### GearInServicesDialog（对话框插件）

**职责**：演示对话框插件（`InteractionDialogPlugin`）——`init` 写入文本面板与三个选项（查看势力关系 / 查看舰队货物 / 关闭演示）并绑定 ESC； `optionSelected` 按 `optionData` 分支展示对应信息，关闭选项与 ESC 走 `dismiss()`；未使用的回调保留空实现。

**绑定注册入口**：由 `GearInDialogServicesScript` 经 `CampaignUIAPI.showInteractionDialog(plugin, null)` 打开（target 传 null，`getInteractionTarget()` 返回 null）。

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例（对话框插件）：演示交互对话框（InteractionDialogPlugin）。
 * 绑定位置：GearInDialogServicesScript 经 CampaignUIAPI.showInteractionDialog(plugin,
 * target) 打开（target 传 null）。optionSelected 的 optionText 是显示文本、optionData 是
 * addOption 传入的 id，分支判断必须用 optionData；未使用的回调保留空实现。
 */
public class GearInServicesDialog implements InteractionDialogPlugin {

    public static final String OPTION_FACTION = "faction";
    public static final String OPTION_CARGO = "cargo";
    public static final String OPTION_CLOSE = "close";

    private static final Logger LOG = Global.getLogger(GearInServicesDialog.class);

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        String target = dialog.getInteractionTarget() == null ? "null（本演示未绑定交互目标）"
                : dialog.getInteractionTarget().getName();
        dialog.getTextPanel().addPara("GearIn 生涯服务速览：交互对话框演示。交互目标=" + target
                + "。选择下方选项查看对应服务信息，或选择关闭结束对话框。");
        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption("查看势力关系", OPTION_FACTION, "显示玩家与霸主、海盗、独立势力的关系数值与等级");
        options.addOption("查看舰队货物", OPTION_CARGO, "显示玩家舰队补给、燃料、船员与信用状态");
        options.addOption("关闭演示", OPTION_CLOSE, "关闭本演示对话框");
        dialog.setOptionOnEscape("关闭演示", OPTION_CLOSE);
        LOG.info("GearIn campaign services dialog init: interactionTarget=" + target);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_FACTION.equals(optionData)) {
            FactionAPI playerFaction = Global.getSector().getPlayerFaction();
            dialog.getTextPanel().addPara(String.format(
                    "势力关系：霸主 %s(%s) 海盗 %s(%s) 独立 %s(%s)",
                    fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                    playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.PIRATES)),
                    playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                    playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_FACTION);
        } else if (OPTION_CARGO.equals(optionData)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            dialog.getTextPanel().addPara(String.format(
                    "舰队货物：补给 %.0f 燃料 %.0f 船员 %d 陆战队员 %d 信用 %.0f 已用空间 %.0f/%.0f",
                    cargo.getSupplies(), cargo.getFuel(), cargo.getCrew(), cargo.getMarines(),
                    cargo.getCredits().get(), cargo.getSpaceUsed(), cargo.getMaxCapacity()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CARGO);
        } else if (OPTION_CLOSE.equals(optionData)) {
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CLOSE);
            dialog.dismiss();
        }
    }

    // 未使用的回调保留空实现（InteractionDialogPlugin 要求全部实现）。
    @Override public void optionMousedOver(String optionText, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }
}
```

### 绑定位置一览

| 类 | 角色 | 绑定注册入口 |
|---|---|---|
| `GearInOneShotScript` | 抽象基类：一次性演示脚本骨架 | 不参与注册 |
| `GearInFactionServicesScript` | 势力段演示脚本 | onGameLoad `addScript`，守卫键 `GearIn_FactionServices` |
| `GearInCargoServicesScript` | 货物段演示脚本 | onGameLoad `addScript`，守卫键 `GearIn_CargoServices` |
| `GearInIntelServicesScript` | 情报段演示脚本 | onGameLoad `addScript`，守卫键 `GearIn_IntelServices` |
| `GearInDialogServicesScript` | 对话框段演示脚本 | onGameLoad `addScript`，守卫键 `GearIn_DialogServices` |
| `GearInServicesIntel` | 自定义情报插件（`BaseIntelPlugin` 子类）| `IntelManagerAPI.addIntel(plugin)`（由情报段脚本调用）|
| `GearInServicesDialog` | 演示对话框插件（`InteractionDialogPlugin`）| `CampaignUIAPI.showInteractionDialog(plugin, null)`（由对话框段脚本调用）|

### 行为要点

- `extends GearInOneShotScript`：沿用脚本页的一次性演示模式——首帧 `advance()` 执行一次 `runDemo()`，`isDone()` 置位后引擎下一帧自动移除。
- `REGISTER_MARK`：persistent data 守卫键——四个脚本各自独立（`GearIn_FactionServices` / `GearIn_CargoServices` / `GearIn_IntelServices` / `GearIn_DialogServices`）。
  - **必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不并入既有插件的 `CLASS_MARK` 守卫。
- 势力段：`getFaction(id)` / `getPlayerFaction()` 入口；关系写入演示 `adjustRelationship(+0.05)` → `setRelationship(原值)` 同帧精确回滚。
  - 关系写入有 `[-1,1]` 钳位，原值必在 `[-1,1]` 内，回滚必然精确；`getRelToPlayer()` 对玩家势力自己返回 `1.0`（同势力对默认值）。
- 货物段：`addSupplies(100)` / `addFuel(50)` / `addMarines(10)` 后同量 remove 回滚，`supplies/fuel/marines` 终值恒等于初值。
  - `stacks after` = `stacks during`（`removeItems` 留下 NULL 占位栈），`nullAfter` = 本演示新腾空的栈数（0–3，取决于演示前是否已有对应栈）。
- 情报段：`addIntel` → `hasIntel` → `removeIntel` 同帧形成完整流程，不留存档残留；`addIntel` 触发 `autoAddCampaignMessage()`（左下消息）；`MessageIntel` 走 `CampaignUIAPI.addMessage` 发纯消息。
- 对话框段：`showInteractionDialog(plugin, null)` 返回 `shown`；对话框由用户点选驱动（`optionSelected` 写文本与日志），`OPTION_CLOSE` 调 `dismiss()` 关闭。

### 调用点片段

在既有 MOD 插件的 `onGameLoad(boolean newGame)` 内新增本页注册块（该插件类归前置单元所有，本页只演示调用，不改动那个文件）：

```java
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFactionServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFactionServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFactionServicesScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInCargoServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCargoServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCargoServicesScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInIntelServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInIntelServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInIntelServicesScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInDialogServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInDialogServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInDialogServicesScript());
    }
```

### 构建命令

在 `Starsector\mods\GearIndustry\` 执行：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含七个类的 `.class` 条目（可用 `jar tf` 核对）。本页七个类的源码与对应 fixture 逐字一致，fixture 编译验收命令见下方「验证方案」。

### 验证方案（基于本例）

前置数据（来自前置知识与本节内容）：

- 既有 MOD 工程：`mod_info.json`、构建脚本与插件入口 `GearInModPlugin`（S06-P02），以及脚本守卫模式（S08-P01）；
- 本页七个类与调用点片段中的四个 `addScript` 注册块（各带独立的 `REGISTER_MARK` 键）。

操作：

1. **fixture 编译**：在仓库根执行 `gradle -p tutorial-fixtures compileS08p07Java --no-daemon`，确认以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含七个类的 `.class` 条目。
3. **新建生涯（首次进入存档后第一帧）**：确认 `starsector.log` 按「预期结果」的日志序列顺序出现四个脚本的日志行（势力段 ×4、货物段 ×2 组、情报段 ×3、对话框段 1 行），且**没有**旧复合演示的收尾 `done` 行。
   - `write-rollback` 行的中间值分别比初值高 `0.05` / `100` / `50` / `10`，终值与初值相等；全程无 `gear_in` 引起的 `ERROR`。
4. **消息区**：演示帧后左下消息区出现两条 `GearIn` 消息——点击第一条跳转 Intel 页签（`addIntel` 的自动发布消息）、点击第二条无跳转（`MessageIntel` 纯消息）。
5. **对话框**：游戏暂停并弹出演示对话框；依次点「查看势力关系」（文本面板追加关系行、日志 `option selected: faction`）、「查看舰队货物」（追加货物行、日志 `option selected: cargo`）。
   - 「关闭演示」或按 ESC（日志 `option selected: close`、对话框关闭、游戏恢复）。
6. **读档**：保存后读档，确认无任何 `GearIn campaign services` 日志（四个 `REGISTER_MARK` 守卫均已置位），无对话框/消息残留。
7. **故障注入 1（可选）**：注释掉调用点片段中的某个 `addScript` 块，重启新档：只有该段日志缺失、其余三段正常（对应故障分析第 11 条前半）；改回后确认恢复。
8. **故障注入 2（可选）**：把 `intelManager.addIntel(intel)` 改为 `intelManager.queueIntel(intel)`：`hasIntel` 返回 false（情报在通信队列等待）、对话框段不受影响。
   - 提前置 `setForceAddNextFrame(true)` 则当帧转正（对应故障分析第 2 条）。
9. **故障注入 3（可选）**：把 `getIntelTags` 的 `super.getIntelTags(map)` 改成先解引用 `map.getPlayerFleet()`：打开 Intel 页或地图 tooltip 时 NPE（对应故障分析第 4 条）；改回后确认恢复。
10. **参数演示（可选）**：把势力段 `adjustRelationship(Factions.PLAYER, 0.05f)` 的 delta 改成 `0.2f` 重新构建：`write-rollback` 行中间值比初值高 `0.20`，终值仍与初值相等（回滚精确性验证）。

通过信号：操作 1–6、10 全部符合；7–9 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（fixture 构建脚本等）做过修改。

## 预期结果

**新建生涯（首次进入存档后第一帧）**：`starsector.log` 依次出现

```text
GearIn campaign services faction: player=player(<带冠词显示名>) baseColor=<Color> darkColor=<Color> | hegemony=Hegemony pirates=Pirates
GearIn campaign services faction relations: hegemony=<R1>(<L1>) hostile=<H1> | pirates=<R2>(<L2>) hostile=<H2> | independent=<R3>(<L3>) hostile=<H3>
GearIn campaign services faction rel write-rollback: <R> -> <R+0.05> -> <R> (rolled back)
GearIn campaign services faction misc: relToPlayer=1.00(Cooperative) tariff=<T> toll=<T> fine=<F> drugsIllegal=false illegalCount=0 knownShips=<N> knownWeapons=<N> knownFighters=<N> showInIntelTab=<B>
GearIn campaign services cargo write-rollback: supplies <S> -> <S+100> -> <S> | fuel <F> -> <F+50> -> <F> | marines <M> -> <M+10> -> <M> | machinery <Q> | credits <C> | space <U>/<Cap>
GearIn campaign services cargo stacks: before=<N> during=<N+k> after=<N+k> nullAfter=<k>
GearIn campaign services cargo stack[0..2]: type=<Type> data=<Data> size=<Size> null=<B>
GearIn campaign services intel: added present=true count=1 (campaign message posted)
GearIn campaign services intel: removed absent=true count=0
GearIn campaign services intel: message-only intel posted
GearIn campaign services dialog: shown=true (interactionTarget=null; 玩家在对话框内点选选项后由选项回调关闭)
```

`<带冠词显示名>` 是 `getDisplayNameWithArticle()` 的返回（新档玩家势力为 `player` 的显示名变体，以实际值为准）；`k` 是本演示新增的栈数（0–3）：若演示前玩家货舱已有补给/燃料/陆战队员栈则 `k` 更小；`nullAfter` 恒等于 `k`。

关系/货物核对：`write-rollback` 行的第二段恒比第一段高 `0.05`（起始关系接近 `1.0` 时被钳位则例外，新档霸主关系不会），第三段与第一段相等。

`drugsIllegal=false`、`illegalCount=0`（新档玩家势力无非法商品声明）、`relToPlayer=1.00(Cooperative)`（玩家势力对玩家自己 = 同势力对默认 `1.0`）。

其余数值以实际存档为准。

日志段的顺序与调用点片段的注册顺序一致（势力 → 货物 → 情报 → 对话框），四个脚本各自只执行一次。

对话框打开时插件 `init` 还会写入一条 `GearIn campaign services dialog init: interactionTarget=null（本演示未绑定交互目标）`，出现在 `dialog: shown=true` 一行之后。

**左下消息区**：演示帧弹出两条消息—— `addIntel` 触发的 `autoAddCampaignMessage` 发布消息（点击跳转 Intel 页签，对应 `MessageClickAction.INTEL_TAB`）与 `MessageIntel` 的两行纯消息（不跳转）。

**交互对话框**：演示帧后游戏暂停并弹出演示对话框，文本首段含「交互目标=null（本演示未绑定交互目标）」；日志出现 `dialog init: interactionTarget=null（本演示未绑定交互目标）`。用户依次点选三个选项：

- 点「查看势力关系」：文本面板追加一行 `势力关系：霸主 ... 海盗 ... 独立 ...`，日志出现 `dialog option selected: faction`；
- 点「查看舰队货物」：文本面板追加 `舰队货物：补给 ...` 行，日志出现 `dialog option selected: cargo`；
- 点「关闭演示」（或按 ESC）：对话框关闭，日志出现 `dialog option selected: close`。

**读档**：不出现任何 `GearIn campaign services` 日志（四个 `REGISTER_MARK` 守卫均已置位、脚本不再注册）；演示遗留的对话框与消息不会随存档保存。

**全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。

## 实例驱动故障分析

下面每条都给出触发条件、可观察结果和修正。

1. **`showInteractionDialog` 返回 false，对话框没弹出来且无报错**。触发：在已有对话框显示时再调（例如演示脚本与某个 bar 事件/教程对话框撞车）。
   - 可观察：`shown=false` 日志、游戏无任何对话框、无报错（`CampaignUIAPI` javadoc 原文）。
   - 修正：把对话框作为唯一入口或先判 `CampaignUIAPI.isShowingDialog()`；本页演示记录了返回值，`shown=false` 时其余演示不受影响。
2. **情报没出现在 Intel 页（日志有、页面没有）**。触发：用 `queueIntel` 而用户舰队不在通信中继范围内，又没设 `forceAddNextFrame`。
   - 可观察：情报在通信队列等待，`getIntel()` 里查不到；无报错。
   - 修正：需要立即登记用 `addIntel`（绕过可见性检查，`IntelInfoPlugin` javadoc 原文）；需要「进中继才可见」的时序用 `queueIntel` + `setPostingLocation` / `setPostingRangeLY`（`BaseIntelPlugin`）。
3. **情报一直不消失，`shouldRemoveIntel()` 恒 false**。触发：自定义 `IntelInfoPlugin` 没实现结束语义（直接 implements 接口而非继承 `BaseIntelPlugin`）。
   - 可观察：情报永久留在 Intel 页与存档（intel 集合随存档保存）。
   - 修正：继承 `BaseIntelPlugin`，结束用 `endAfterDelay()` / `endImmediately()`，`shouldRemoveIntel()` 默认 = `isEnded()` 即自动清理。
4. **`getIntelTags(map)` 抛 NPE**。触发：覆写时直接 `map.getPlayerFleet()` 之类解引用。
   - 可观察：地图 tooltip / Intel 页渲染路径 NPE（`IntelInfoPlugin` javadoc："Method NEEDS to handle map being null gracefully."）。
   - 修正：先判 null 或调 `super.getIntelTags(map)`（基类内部已判）。
5. **`getMothballedShips()` NPE**。触发：没调 `initMothballedShips(factionId)` 直接取。
   - 可观察：`NullPointerException`，堆栈指向取用点（该字段可为 null）。
   - 修正：使用前先 `initMothballedShips(factionId)`（`CargoAPI` javadoc）；`addMothballedShip` 内部会自动初始化，不需要自己先 init。
6. **货物回滚后数量对但栈数量对不上**。触发：把 `getStacksCopy().size()` 当「货物条目数」核对。
   - 可观察：回滚后 `stacks` 数量比回滚前多 `k`，多出的栈 `isNull()==true`（`removeItems` 把减空的栈原地替换为 `NULL_STACK` 占位；`CargoStackAPI.isNull()` javadoc）。
   - 修正：核对数量读 `getSupplies()` / `getFuel()` 等数值接口；栈是存储细节，清理占位用 `removeEmptyStacks()`。
7. **关系写入「没生效」或「生效过头」**。触发：把 `adjustRelationship(id, delta)` 当带监听的声望系统用，或忘了写入时的 `[-1,1]` 钳位。
   - 可观察：直接写入不弹声望变化消息、不触发监听；值超界被钳位。
   - 修正：玩家声望 action 走 `SectorAPI.adjustPlayerReputation`；直接写关系时自行回滚并接受「无监听」语义（本页演示写法）。
8. **读档后蓝图/已知资源「回来了」**。触发：调了 `removeKnownShip` 以为永久移除。
   - 可观察：读档后 `.faction` 文件定义的蓝图重新加入已知集合（`FactionAPI` javadoc 原文）。
   - 修正：把「要移除的清单」存 persistent data，每次读档重新 `removeKnown*`；改完集合后调 `clearShipRoleCache()` 让 role 选择用新缓存。
9. **对话框选项点了没反应**。触发：`optionSelected` 里比较的是 `optionText`（显示文本）而不是 `optionData`。
   - 可观察：文本渲染正常但选择分支永不命中、日志缺失（`InteractionDialogPlugin.optionSelected(optionText, optionData)`——`optionText` 是用户看到的文本，`optionData` 是 `addOption` 时传入的 id）。
   - 修正：用 `OPTION_FACTION.equals(optionData)` 这类 data 比较（本页演示写法）。
10. **对话框关闭不了（ESC 无效、无关闭选项）**。触发：没设置关闭选项也没设 `setOptionOnEscape`。
    - 可观察：ESC 默认关闭行为缺失或点关闭无回调。
    - 修正：`setOptionOnEscape(text, optionId)` 绑定 ESC（`InteractionDialogAPI`）；注意 `setOptionOnEscape(null, optionId)` 会让 ESC 不执行关闭；关闭回调里调 `dismiss()`。
11. **演示日志出现但游戏内没有任何可观察内容**。触发：在错误生命周期阶段注册（如 `onApplicationLoad` 里 `Global.getSector()` 为 null 即 NPE），或 `addIntel` / `addMessage` / `showInteractionDialog` 的返回值被忽略。
    - 可观察：注册点放错 → 启动中止或脚本永不推进；返回 false 被忽略 → 对话框静默缺失。
    - 修正：注册放 `onGameLoad` 守卫块内（沿用脚本守卫模式）；对 `showInteractionDialog` 的布尔返回值做日志（本页演示写法）。
12. **`MessageIntel` 消息点击后跳转到奇怪页面**。触发：把纯消息当 intel 用（绑定 `MessageClickAction` 或加入情报管理器）。
    - 可观察：消息显示异常或点击行为不符合「纯消息」预期。
    - 修正：`MessageIntel` 的用途是左下消息区纯消息（javadoc 原文），走 `CampaignUIAPI.addMessage(intel)`；要出现在 Intel 页请用 `BaseIntelPlugin` 子类 + `addIntel`。

## 总结

本页把四个生涯服务入口讲成四条链，并在完整最小例里组合使用：

- **势力链**：`SectorAPI.getFaction(id)` / `getPlayerFaction()` → `FactionAPI`（身份 / 颜色 / 关系 / 资源 / 经济字段 / 规格）→ 关系对表（`-1..1` 钳位、双向共享、对条目用到时才建）。
  - `getRelationship` / `getRelationshipLevel` / `isHostileTo` 读取，`adjustRelationship` / `setRelationship` / `ensureAtBest/Worst` 写入。
  - 玩家声望 action 的正规路径是 `SectorAPI.adjustPlayerReputation`。
- **货物链**：`SectorEntityToken.getCargo()`（玩家舰队 / 子市场同源）→ `CargoAPI`（栈集合 + credits + 容量）→ 按分族读写（资源 / 武器 / 战机芯片 / 特殊物品 / mothballed ships）。
  - 栈细节（`removeItems` 留 NULL 占位、容量需 `updateSpaceUsed`、mothballed ships 必须先 init）。
- **情报链**：`SectorAPI.getIntelManager()` → 创建 `BaseIntelPlugin` 子类（或 `MessageIntel`）→ `addIntel`（立即登记 + 弹消息，绕过可见性检查）或 `queueIntel`（通信中继可见性）。
  - 情报管理器每帧推进（未暂停时）→ `shouldRemoveIntel` / `removeAllThatShouldBeRemoved` 清理 → intel 集合随存档保存。
- **对话框链**：`CampaignUIAPI.showInteractionDialog(plugin, target)`（可能返回 false）→ `InteractionDialogPlugin` 生命周期（`init` → `optionSelected` → `advance` → `backFromEngagement`）。
  - `InteractionDialogAPI` 面板与入口（`getTextPanel` / `getOptionPanel` / `setOptionOnEscape` / `dismiss` / picker 族 / `makeOptionOpenCore`）。

记忆要点：

- 关系写入是**原始写入**（无监听、钳位 `[-1,1]`）；玩家声望 action 走 `adjustPlayerReputation`。
- `getMothballedShips()` 必须先 `initMothballedShips`；`getStacksCopy()` 含 NULL 占位栈。
- `addIntel` 立即登记（弹消息、绕过中继检查）；`queueIntel` 才走可见性链路；intel 随存档保存。
- `showInteractionDialog` 可能返回 `false`；对话框选项比较用 `optionData` 不用 `optionText`。
- `getIntelTags(map)` 必须处理 map 为 null；`removeKnown*` 永久生效需要读档重移除 + `clearShipRoleCache`。

脚本守卫与 `Global` 阶段语义是本页底座；`REGISTER_MARK` 注册模式沿用既有脚本页「每个注册项用各自的键」的惯例。

本页只覆盖四个入口的「常用高频面」：势力关系监听、Rules 侧对话框、 `BarEventPlugin` 对话框入口与任务类情报（`BaseMissionIntel`）在逻辑上属于更深的主题，本页不展开。

## 建议参考项

- `com.fs.starfarer.api.Global`——全局入口：`getSector()`、`getLogger()`
- `com.fs.starfarer.api.campaign.SectorAPI`——生涯引擎入口。
  - 关系、情报、对话框、货物四类入口都能从这里进入：`getFaction` / `getAllFactions` / `getPlayerFaction` / `getIntelManager` / `getCampaignUI` / `getPlayerFleet` / `adjustPlayerReputation` / `getPersistentData` / `addScript`。
- `com.fs.starfarer.api.campaign.FactionAPI`——势力接口：身份、颜色、关系读写、已知资源集合、经济字段与 doctrine/production/spec
- `com.fs.starfarer.api.characters.RelationshipAPI`——关系值包装：`getRel()` / `getLevel()`
- `com.fs.starfarer.api.campaign.CargoAPI`——货物容器：分族读写、容量统计、`getStacksCopy()`、`removeEmptyStacks()`
- `com.fs.starfarer.api.campaign.CargoStackAPI`——货物单栈：类型、数据、大小与 `isNull()` 占位判定
- `com.fs.starfarer.api.util.MutableValue`——credits 的可变值容器：`get` / `set` / `add` / `subtract`
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`——情报登记集合：`addIntel` / `queueIntel` / `removeIntel` / `removeAllThatShouldBeRemoved`
- `com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`——单条情报约定：`getIntelTags(map)` 必须容忍 null map
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`——MOD 自定义情报标准父类：结束倒计时、删除按钮、更新消息、默认图标语义
- `com.fs.starfarer.api.impl.campaign.intel.MessageIntel`——左下消息区纯消息类：`new MessageIntel(...)` + `addLine(...)`
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`——对话框本体：面板、选项行为（ESC/确认/回显）、picker 族、`dismiss()`
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`——对话框内容插件：七方法生命周期（`init` → `optionSelected` → `advance` → `backFromEngagement`）
- `com.fs.starfarer.api.campaign.OptionPanelAPI`——选项面板：`addOption` / `setEnabled` / `addSelector` / `addOptionConfirmation`
- `com.fs.starfarer.api.campaign.TextPanelAPI`——文本面板：`addPara` / `highlightInLastPara` / `clear`
- `com.fs.starfarer.api.campaign.CampaignUIAPI`——生涯 UI：`showInteractionDialog` / `addMessage` / `isShowingDialog`
- `com.fs.starfarer.api.campaign.RepLevel`——关系等级枚举与阈值：`getLevelFor`、`getMin` / `getMax` 边界语义
- `com.fs.starfarer.api.campaign.SectorEntityToken`、`CampaignFleetAPI`——`getCargo()` 货物入口
- `com.fs.starfarer.api.util.Misc`——文本与高亮颜色常量（`getTextColor` / `getGrayColor` 等）
- `com.fs.starfarer.api.impl.campaign.ids.Factions`、`Commodities`、`Tags`——id 常量：势力 / 商品 / intel 标签
- `com.fs.starfarer.api.impl.campaign.rulecmd.AdjustRep`——原版声望 action rulecmd：`adjustPlayerReputation` 的原版调用参考
- `data/scripts/gearin/campaign/`——本页七个示例类所在目录（MOD 内相对路径）
