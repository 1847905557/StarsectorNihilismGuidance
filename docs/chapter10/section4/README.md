# 酒吧事件与临时规则

本节把生涯酒吧事件（bar event）讲透：事件约定 `PortsideBarEvent`、creator 约定 `GenericBarEventCreator`、全局调度器 `BarEventManager`；还覆盖活动列表 `PortsideBarData`、酒吧显示链路（`BarCMD` / `BarEventDialogPlugin`）与规则驱动的临时 bar 选项（`AddBarEvent` / `RemoveBarEvent`），并给出完整最小例 `GearInBarEvent` 与配套 creator `GearInBarEventCreator`。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 酒吧事件三个角色的公开 API 归属：
  - **bar event** 约定是 `PortsideBarEvent`（`com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent`）；
  - **creator** 约定是 `BarEventManager.GenericBarEventCreator`（内部接口）；
  - **manager** 是 `BarEventManager`；
  - 以及配套的 `PortsideBarData`（活动事件列表）、`BaseBarEvent` / `BaseBarEventCreator` / `BaseBarEventWithPerson`（默认实现）、 `SpecBarEventCreator`（CSV 路由）、`BarEventSpec`（CSV spec）、`BarEventDialogPlugin`（事件对话包装）、`BarCMD`（酒吧 UI 规则命令）、 `AddBarEvent` / `RemoveBarEvent`（临时 bar 选项规则命令）。
- 说出酒吧事件的两条注册/数据链路：
  - 数据文件链路 `data/campaign/bar_events.csv`（11 列）→ `BarEventSpec` → CSV spec 加载期（内部实现）→ `SettingsAPI.getAllBarEventSpecs()` → `BarEventManager.updateBarEventCreatorsFromSpecs()` 自动生成 `SpecBarEventCreator`；
  - 代码注册链路 `BarEventManager.getInstance().addEventCreator(creator)`（`hasEventCreator(Class)` 守卫，`onGameLoad` 执行）。
- 说出 `BarEventManager` 的调度语义：按游戏天推进（`Misc.getDays`）、active/timeout 两个 `TimeoutTracker`、未挂接事件清理、`maxTotalBarEventsAsFractionOfEventTypes` 上限、priority picker 优先、普通 picker 按 `getBarEventFrequencyWeight()` 权重抽取、 `createBarEvent()` 返回 null 跳过；
- 说出事件生命周期：创建入列（active 时长内可显示）→ 显示过滤（`shouldRemoveEvent` / `shouldShowAtMarket`）→ `addPromptAndOption` 写提示与选项（**optionData 必须是事件自身**）→ `BarEventDialogPlugin` 事件对话（`init` → `optionSelected` → `isDialogFinished` → 回到酒吧）→ 交互收尾 `notifyWasInteractedWith(this)`（移除 + accepted 超时）；以及未交互时 active 时长过期后的清理与普通超时；
- 说出酒吧显示链路的可调参数：`settings.json` 的 `minBarEvents` / `maxBarEvents` / `barEventProbOneMore`、市场内存 `$BarCMD_shownEvents`（按事件 ID 复现）、`AddBarEvents` 规则分类与 `AddBarEvent` / `RemoveBarEvent` 临时选项的"每次展示前清空重建"语义；
- 从症状定位故障（见"实例驱动故障分析"节）：结果必须沿着**数据文件/注册入口 → 调度入列 → 显示过滤 → 对话收尾**四个位置复核。

## 前置知识

本节假设您已完成以下单元的学习：

- 交互对话框三面板（`TextPanelAPI.addPara`、`OptionPanelAPI.addOption`）、`showInteractionDialog` 与对话框插件生命周期——酒吧事件对话复用的是同一套面板 API；`showInteractionDialog` 返回 false 的语义同样适用于酒吧对话以外的调用（C08-S09）。
- 事件边界与监听器生命周期——本节的"事件 ID 使用方"是同一套"注册入口 → 派发 → 可观察结果"方法论的酒吧域应用；XStream 序列化边界（常驻对象随档保存）直接适用于"事件实例随 sector memory 存档"（C10-S02）。
- 时间语义（`advance(amount)` 真实秒、`Misc.getDays` 换算）——`BarEventManager.advance` 与 `PortsideBarData.advance` 的推进节奏都建立在这条换算上（C10-S01）。
- `modPlugin` 生命周期与注册守卫模式（C06-S02）；`Global.getSector()` 的 null 语义与"用 `getCurrentState()` 判断阶段"（C06-S03）——creator 注册只发生在生涯引擎建立之后。
- 市场（`MarketAPI`）——`shouldShowAtMarket` 的市场条件过滤（`hasSpaceport()`、`getFactionId()` 等）（C08-S05）。
- Rules 命令与 Rules 内存（后续单元）——本节的 `AddBarEvent` / `RemoveBarEvent` 与 `BarCMD` 都是规则命令（`BaseCommandPlugin` 子类），本节只给命令语义与规则片段，规则引擎细节由这两个单元展开（C11-S03 / C11-S04）。
- 人物与关系——`BaseBarEventWithPerson` 的 market 稳定随机人物（`seed + market.getId().hashCode()`）来自人物生成面（C09-S01）。

## 术语

按术语约定，API 类名保留源码大小写。本节首次完整讲解以下术语（后续页面继续使用）：

- **酒吧事件（bar event）**：API 名保留 `PortsideBarEvent`。
  - 一次可在星球/空间站酒吧出现、由玩家主动选择进入的短对话事件：酒吧提示 + 选项（`addPromptAndOption`）→ 事件对话（`init` / `optionSelected`）→ 结束返回酒吧。
  - 它独立于普通交互对话框：酒吧事件不自己调用 `showInteractionDialog`，而是由 `BarCMD` 在酒吧 UI 里把选项的 optionData 指到事件自身，再经 `BarEventDialogPlugin` 包装接管对话。
- **creator**：API 名保留 `GenericBarEventCreator`（`BarEventManager` 的内部接口）与默认实现 `BaseBarEventCreator`。
  - 负责两件事：`createBarEvent()` 创建事件实例（返回 null 表示本次放弃抽取）；提供抽取参数（频率权重、active 时长、普通超时、accepted 超时、是否 priority）。
- **manager**：API 名保留 `BarEventManager`。全局酒吧事件调度器（`EveryFrameScript`）：持有 creator 集合与 active/timeout 两个跟踪器，按游戏天推进并周期性抽取事件入列。
- **活动列表（PortsideBarData）**：当前全部已抽取、等待被酒吧显示的事件实例列表。manager 决定"创建哪些事件"，活动列表决定"哪些事件可被显示"。
- **临时 bar 选项（temp bar option）**：规则系统经 `AddBarEvent` 规则命令写入市场内存、由 `BarCMD` 当次展示的临时酒吧选项。它**不是** `PortsideBarEvent` 事件，点选后走普通规则对话（`$option == <optionId>`），每次展示前被清空重建。
- **事件对话（event dialog）**：`BarEventDialogPlugin` 包装下的事件交互对话。玩家点选酒吧选项后，`BarCMD` 把对话框插件切换为 `BarEventDialogPlugin`，事件自身通过 `init` / `optionSelected` 驱动对话，结束时切换回 `BarCMD` 并回到酒吧。

## 文件与 API 链路

### 角色命名与归属总览

任务清单里的"BarEventAPI / BarEventCreatorAPI / BarManagerAPI"对应本主题的三个角色；RC8 公开 API 中的具体类型名如下（全部在 `com.fs.starfarer.api.*` 包路径内）：

| 角色 | RC8 类型 | 位置 |
|---|---|---|
| bar event（事件约定）| `PortsideBarEvent`（接口）| `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent` |
| bar event（默认实现）| `BaseBarEvent`（抽象）| `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent` |
| bar event（带人物默认实现）| `BaseBarEventWithPerson`（抽象）| `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson` |
| creator（约定 + 默认实现）| `GenericBarEventCreator`（`BarEventManager` 内部接口）+ `BaseBarEventCreator` | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`、`BaseBarEventCreator` |
| manager（调度器）| `BarEventManager` | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager` |
| 活动列表 | `PortsideBarData` | `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData` |
| CSV spec 与路由 | `BarEventSpec` / `SpecBarEventCreator` | `com.fs.starfarer.api.loading.BarEventSpec`、`com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator` |
| 事件对话包装 | `BarEventDialogPlugin` | `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin` |
| 酒吧 UI 规则命令 | `BarCMD` | `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD` |
| 临时 bar 选项规则命令 | `AddBarEvent` / `RemoveBarEvent` | `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`、`RemoveBarEvent` |

### 数据文件链路（bar_events.csv）

1. 游戏在 CSV spec 加载期读取 `data/campaign/bar_events.csv`：`loadSpecsFromJson("bar event id", "data/campaign/bar_events.csv", BarEventSpec.class, ...)`（内部加载实现），`bar event id` 列是 spec key。
2. 每行构造一个 `BarEventSpec`：`bar event id`（必填）、`plugin`（必填，`row.getString("plugin")`）、`freq`（默认 10）、`prob`（默认 1）、`min/max dur`（默认 30/40）、`min/max timeout`（默认 30/40）、`min/max accepted timeout`（默认 60/90）、`tags`（逗号分隔，可空）。
3. `SettingsAPI.getAllBarEventSpecs()` / `getBarEventSpec(id)` 暴露全部 spec。
4. `BarEventManager.readResolve()` → `updateBarEventCreatorsFromSpecs()`：对每个**还没有 creator** 的 spec 自动创建一个 `SpecBarEventCreator(specId)`。CSV 里新加的行，下一次读档（manager 反序列化触发 readResolve）即生效。
5. `SpecBarEventCreator`（继承 `BaseBarEventCreator`）把 spec 的字段接到抽取参数上：
   - `createBarEvent()` 按 `spec.isMission()` 分派——plugin 是 `HubMissionWithBarEvent` 时创建 `HubMissionBarEventWrapper`（mission 酒吧包装），否则 `spec.createEvent()` 直接 `getInstanceOfScript(pluginClass)` 实例化普通 `PortsideBarEvent`；
   - `isPriority()` = `spec.hasTag(Tags.MISSION_PRIORITY)`（"priority" tag）。

原版 `bar_events.csv` 26 行全部是 mission 类（`com.fs.starfarer.api.impl.campaign.missions.*`）。

**非 mission 的普通事件**（如 `LuddicFarmerBarEvent`、`HistorianBarEvent`）不走 CSV，走代码注册（见下）。

### 注册入口

原版注册入口（`CoreLifecyclePluginImpl`，`com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`）：

- `onGameLoad(boolean)` → `addScriptsIfNeeded()` → `addBarEvents()`：
  - `sector.hasScript(PortsideBarData.class)` 缺则 `addScript(new PortsideBarData())`；
  - `sector.hasScript(BarEventManager.class)` 缺则 `addScript(new BarEventManager())`；
  - `BarEventManager.getInstance()` 后对每个原版手写 creator 做 `!hasEventCreator(XxxCreator.class) → addEventCreator(new XxxCreator())`。

`PortsideBarData` 与 `BarEventManager` 构造器都把实例写入 sector memory 的固定键（`$core_PortsideBarData` / `$core_genericBarEventManager`）。

**manager 实例、creator 列表、active 事件列表都随存档保存**（sector memory 持久化； `TimeoutTracker` 的 `set` 由 `readResolve` 重建）。

MOD 注册入口（本节示例，见"完整最小例"）：

```java
BarEventManager manager = BarEventManager.getInstance();
if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
    manager.addEventCreator(new GearInBarEventCreator());
}
```

要点：`hasEventCreator(Class)` 用 `isInstance` 判断，而 `addEventCreator` 本身**不去重**（直接 `creators.add`）——**守卫缺失的可观察结果是同 class 的 creator 反复入列、抽取权重翻倍**。

注册时机必须在生涯引擎建立之后（`onGameLoad` / `onNewGame`）；原版核心插件的 `onGameLoad` 先于 MOD 插件执行（vanilla 固定最先，成熟实例直接依赖此顺序），因此 `getInstance()` 不判空。

### 调度与抽取（BarEventManager.advance）

`BarEventManager.advance(float amount)` 每帧被推进（`runWhilePaused()` 返回 false，暂停不推进）：

1. `days = Misc.getDays(amount)`——**所有时长都是游戏天**（10 真实秒 = 1 游戏日）。
2. `active.advance(days)` / `timeout.advance(days)`：active 跟踪器里的事件按天扣减，扣到 0 自动移除；timeout 跟踪器里的 creator 同理。
3. `tracker`（`IntervalUtil(0.4f, 0.6f)`，游戏天）到点才执行抽取；`tracker2`（20–40 天）到点更新种子。
4. 未挂接清理：在 `barEventCreators` 映射里但不在 active 跟踪器中的事件 = 未交互且 active 时长已过期——从映射与活动列表移除，并把对应 creator 置入超时：`dur = max(creator.getBarEventTimeoutDuration(), timeout 剩余)`。
5. 数量上限：`max = round(creators.size() * settings.getFloat("maxTotalBarEventsAsFractionOfEventTypes"))`（默认 0.5，见 settings.json），最小 1；`active` 数量达到 `max` 或每个 creator 都有活跃事件时本次不再抽取。
6. 抽取：先建 priority picker（`isPriority()` 的 creator，权重 = `getBarEventFrequencyWeight()`）与普通 picker。
   - **priority 优先**，未命中再抽普通；同一 `getBarEventId()` 已有活跃事件或 creator 在 timeout 中的跳过；抽中后 `createBarEvent()`，**返回 null 则本次放弃**。
   - 事件加入 `active` 跟踪器（时长 = `getBarEventActiveDuration()`）、记录 `barEventCreators.put(event, creator)`、 `PortsideBarData.getInstance().addEvent(event)` 入活动列表。

::: info 为什么所有时长都用游戏天

酒吧事件的抽取与展示节奏都建立在 `Misc.getDays` 的换算上（10 真实秒 = 1 游戏日），且推进只在未暂停时发生——用游戏天计价让"一次事件从抽取到过期"的语义与玩家实际游戏节奏挂钩，而不是与真实时间挂钩；暂停挂机时事件不会批量过期。

:::

### 活动列表与事件生命周期（PortsideBarData）

`PortsideBarData`（`EveryFrameScript`，`runWhilePaused()` false）持有 `active` 列表：

- `addEvent(event)` 入列；`getEvents()` 返回列表（活的引用，不是副本）。
- `advance(amount)` 每帧遍历：**在调 `curr.advance(amount)` 前后各查一次 `shouldRemoveEvent()`**，返回 true 就移除——需要"限时消失"的事件在 `advance` 里推进倒计时、到期由 `shouldRemoveEvent` 返回 true 自清。
- `removeEvent(event)` 同时从 manager 的 active 跟踪器移除。

事件生命周期完整流程：

| 阶段 | 触发 | 关键调用 |
|---|---|---|
| 抽取入列 | manager 周期性抽取 | `creator.createBarEvent()` → `active.add(event, activeDuration)` → `PortsideBarData.addEvent` |
| 显示过滤 | 玩家进酒吧，`BarCMD.showOptions` | `shouldRemoveEvent()` → `shouldShowAtMarket(market)` → `addPromptAndOption` → `wasShownAtMarket` |
| 事件对话 | 玩家点选酒吧选项 | `BarEventDialogPlugin.init` → `event.init` → `event.optionSelected` |
| 交互收尾 | 对话完成 | `done = true` + `BarEventManager.getInstance().notifyWasInteractedWith(this)` |
| 未交互过期 | active 时长扣完 | 未挂接清理：活动列表移除 + creator 普通超时 |

::: caution 交互收尾（必须）

对话结束必须调 `BarEventManager.getInstance().notifyWasInteractedWith(this)`：它把事件从活动列表与 active 跟踪器移除。

并把 creator 置入 `getBarEventAcceptedTimeoutDuration()`（与剩余普通超时取较大值）。

**接受与拒绝都算"已交互"**（原版 `BaseGetCommodityBarEvent` 的 confirm 与 cancel 分支都调用）。

不调用的话，事件在 active 时长内继续留在活动列表、同一市场可能再次出现，creator 也只能拿到普通超时（30–40 天而非 60–90 天）。

:::

::: caution 存档边界（必须）

活动列表随 sector memory 存档（XStream），事件实例的非 transient 字段必须可序列化。

`BaseBarEvent` 已把对话句柄声明为 `transient`（`dialog` / `text` / `options` / `memoryMap`）， `done` / `noContinue` 同样声明为 `transient`，`shownAt` 是普通字段（`MarketAPI`，可序列化）。

事件自定义字段只放可序列化数据。

:::

### 酒吧显示（BarCMD.showOptions）

玩家在星球/空间站交互里选"Visit the bar"（`marketVisitBar` 选项，`data/campaign/rules.csv`）触发 `bar_optionSel` 规则。

随后依次执行 `FireBest BarShuttleDownPrintDesc` / `FireBest BarPrintDesc` / `BarCMD showOptions`。

`BarCMD.showOptions()` 的展示管线：

1. 数量：`minBarEvents`（默认 1）/ `maxBarEvents`（默认 3）/ `barEventProbOneMore`（默认 0.5）加权抽 `num`（见 settings.json）。
2. 已展示集合：市场内存 `$BarCMD_shownEvents`——**存在则按事件 ID 匹配复现同一组事件**（`event.getBarEventId()`），不存在则全量洗牌并 `needToSaveShown = true`。
   - 展示后 `mem.set(key, shown, time)`（20–40，即 `BAR_EVENT_MIN/MAX_TIME_BEFORE_CHANGING`）。内存过期按**游戏天**扣减（`Memory` 内部实现：`convertToDays(var1)`、仅未暂停）——同一市场约 20–40 游戏天内复现同一组事件。
3. 临时选项：`AddBarEvent.clearTempEvents(market)` 清空 `$core_tempBarEvents`，然后 `FireAll.fire(..., "AddBarEvents")` 触发全部匹配的 `AddBarEvents` 分类规则，再逐个显示（上限 `max`）。
4. 事件循环：**教程进行中（`TutorialMissionIntel.isTutorialInProgress()`）全部跳过**；数量占满即 break。
   - 依次检查 `shouldRemoveEvent()` 与 `shouldShowAtMarket(market)`；通过后 `event.addPromptAndOption(dialog, memoryMap)`（事件自己写提示与选项）→ `wasShownAtMarket(market)` → `shown.add(event.getBarEventId())`。
   - `isAlwaysShow()` 的事件不占普通名额，且排在最前（按 `isAlwaysShow` 排序）。
5. 底部固定 "Leave the bar" 选项 + ESC 快捷键；`dialog.setPlugin(this)` + `init(dialog)`——对话框插件切换为 `BarCMD` 自身。

`BarCMD.optionSelected` 分派：

- `optionData instanceof PortsideBarEvent` → 创建 `BarEventDialogPlugin(this, this, event, memoryMap)`，`dialog.setPlugin(plugin)` + `plugin.init(dialog)`——**事件对话的唯一入口**；
- `"barLeave"` → `leaveBar()`（回市场对话）；`"barContinue"` → `showOptions(true)`；
- 其他字符串（临时选项 / mission wrapper 选项）→ 写 `$BarCMD` 到实体内存、`dialog.setPlugin(originalPlugin)`、`$option = optionData`、`FireBest "DialogOptionSelected"`——落到规则对话。

### 事件对话（BarEventDialogPlugin）

`BarEventDialogPlugin`（`com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`， `InteractionDialogPlugin`）是事件的对话外壳：

- `init(dialog)`：`event.init(dialog, memoryMap)`；`event.isDialogFinished()` 已为 true 则直接 `endEvent()`。
- `optionSelected(optionText, optionData)`：`dialog.addOptionSelectedText(optionData)` 回显（复用交互对话框面板的文本回显语义）；`event.optionSelected(optionText, optionData)`；事件完成则 `endEvent()`。
- `endEvent()`：`dialog.setPlugin(originalPlugin)`（切回 `BarCMD`）+ `cmd.returningFromEvent(event)` → `returningFromEvent(event.endWithContinue())`。
  - `endWithContinue()` 为 true 先显示 "Continue"（再点回酒吧），false 直接 `showOptions(true)`（"Nothing of note is going on at the bar." 提示）。

### 临时规则命令（AddBarEvent / RemoveBarEvent）

规则驱动的临时 bar 选项（不属于 `PortsideBarEvent`，常用来给特定市场/特定条件加一次性酒吧入口）：

- `AddBarEvent <option id> <option text> <blurb> [color]`：从 `dialog.getInteractionTarget().getMarket()` 取市场。
  - option text 与 blurb 支持规则 token 替换（`getStringWithTokenReplacement`）；写入市场内存 `$core_tempBarEvents` 容器（`events.put(optionId, data)`，`LinkedHashMap` 保序）。
  - `getTempEvents` 不存在时创建并 `mem.set(KEY, events, 0f)`——**expire=0**，下一帧内存推进即清除，只活在当次展示里。
- `RemoveBarEvent <option id>`：`AddBarEvent.removeTempEvent(market, optionId)` 删除单个临时选项。

::: warning 临时 bar 选项不能当长期状态读取

`BarCMD.showOptions` 每次展示前 `clearTempEvents` 再重触发 `AddBarEvents` 规则——临时选项只活在当次展示里。

`RemoveBarEvent` 只删临时选项，**不会**移除 `PortsideBarData` 活动列表里的事件。

期待"加一次永久生效"或"删临时选项后事件也消失"都会落空。

:::

### 接口全表

`PortsideBarEvent`（11 个方法）：

| 方法 | 语义 |
|---|---|
| `void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)` | 事件对话接管：面板句柄、初始文本与选项 |
| `boolean isDialogFinished()` | 对话完成标记（`BaseBarEvent` = `done`）|
| `boolean endWithContinue()` | 结束后是否先显示 "Continue" 再回酒吧 |
| `void optionSelected(String optionText, Object optionData)` | 对话选项回调（optionData 比较）|
| `boolean shouldRemoveEvent()` | 是否从活动列表移除（每帧前后各查一次）|
| `boolean shouldShowAtMarket(MarketAPI market)` | 当前市场是否显示（默认 shownAt 锁定单一市场）|
| `void advance(float amount)` | 活动列表中的每帧推进（未暂停时）|
| `void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)` | 酒吧展示：写提示与选项（optionData = 事件自身）|
| `void wasShownAtMarket(MarketAPI market)` | 展示后回调（记录 shownAt）|
| `String getBarEventId()` | 事件 ID（去重/复现键，默认类简单名）|
| `boolean isAlwaysShow()` | 恒显示：不占名额、排序最前（剧情关键事件用，勿滥用）|

`GenericBarEventCreator`（8 个方法，`BarEventManager` 内部接口）：

| 方法 | 默认值（`BaseBarEventCreator`）| 语义 |
|---|---|---|
| `PortsideBarEvent createBarEvent()` | null（子类必须覆写）| 创建事件实例；返回 null 放弃本次抽取 |
| `float getBarEventFrequencyWeight()` | 10f | 普通 picker 抽取权重 |
| `float getBarEventActiveDuration()` | 30 + rand×10（30–40 游戏天）| 事件在 active 跟踪器的存活时长 |
| `float getBarEventTimeoutDuration()` | 30 + rand×10（30–40 游戏天）| 未交互过期后的 creator 冷却 |
| `float getBarEventAcceptedTimeoutDuration()` | 60 + rand×30（60–90 游戏天）| 玩家交互后的 creator 冷却 |
| `boolean isPriority()` | false | 进入 priority picker（先于普通 picker；javadoc 原文："Should be used sparingly, for gameplay-essential events"）|
| `String getBarEventId()` | 类简单名 | creator 级去重键（同一 ID 同时只有一个活跃事件）|
| `boolean wasAutoAdded()` | false | 是否由 CSV spec 自动生成（`SpecBarEventCreator` 专用）|

`BaseBarEvent` 其余默认：`isAlwaysShow()` false、`shouldRemoveEvent()` false。

`shouldShowAtMarket` = `shownAt == null || shownAt == market`、`getBarEventId()` = `getClass().getSimpleName()`、 `endWithContinue()` = `!noContinue`。

## 规则强度

本节必须遵守的规则清单（违反后的可观察结果均附上）：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 玩家与事件交互并结束对话时必须调 `BarEventManager.getInstance().notifyWasInteractedWith(this)`（接受与拒绝都算交互；原版 `BaseGetCommodityBarEvent` 的 confirm 与 cancel 分支同款）| 必须 | 事件留在活动列表直到 active 时长过期：同一市场可能再次出现同一事件；creator 只拿到普通超时（30–40 天）而非 accepted 超时（60–90 天），重复出现频率更高 |
| `addPromptAndOption` 里酒吧选项的 optionData 必须是事件自身（`addOption(text, this)`；原版 `BaseGetCommodityBarEvent` 同款）| 必须 | 点击选项不进入事件对话：`BarCMD.optionSelected` 只对 `optionData instanceof PortsideBarEvent` 创建 `BarEventDialogPlugin`；其他类型落入规则对话分支（`$option` / `DialogOptionSelected`），事件对话永不启动 |
| 事件与 creator 的非 transient 字段必须可序列化（活动列表与 manager 存于 sector memory、随存档 XStream 保存）| 必须 | 保存/读档异常、读档后事件行为错乱；对话句柄（`dialog` / `text` / `options` / `memoryMap`）必须保持 `BaseBarEvent` 的 transient 声明 |
| creator 注册只发生在生涯引擎建立之后（`onGameLoad` / `onNewGame`），注册块必须用 `hasEventCreator(Class)` 守卫（`addEventCreator` 不去重）| 必须 | 注册过早（如 `onApplicationLoad`）：`getInstance()` 返回 null 解引用 NPE、启动中止；守卫缺失：每次读档重复入列同 class creator，抽取权重翻倍、事件过频 |
| `shouldShowAtMarket` 覆写时理解 shownAt 锁定语义（`BaseBarEvent` 默认首次展示后锁定单一市场），确需跨市场复用再自行处理 | 必须（语义规则）| 事件在 A 市场展示后 B 市场不再出现——若覆写时忘了先调 `super` 或自行实现，过滤结果与预期不符 |
| `getBarEventId()` 与 creator 的 `getBarEventId()` 保持一致且稳定（默认都是类简单名；覆写时两处同步）| 建议 | 不一致时 `$BarCMD_shownEvents` 按 ID 复现失效：市场内存里的 ID 匹配不到事件，该市场事件组显示为空；manager 的 active 去重键错位 |
| `isPriority()` / `isAlwaysShow()` 只用于剧情关键事件 | 建议 | 太多 priority 事件挤占普通事件（`BarEventManager` javadoc 原文："Having too many priority events could crowd out all other events entirely"）；`isAlwaysShow` 事件永远占用展示名额 |
| 临时 bar 选项（`$core_tempBarEvents`）只活在当次展示里，不能当长期状态读取；`RemoveBarEvent` 只删临时选项、不删活动列表事件 | 事实 | 期待"加一次永久生效"或"删临时选项后事件也消失"都会落空（临时选项当长期状态读、`RemoveBarEvent` 不删活动列表事件）|
| 教程进行中（`TutorialMissionIntel.isTutorialInProgress()`）酒吧事件全部跳过 | 事实 | 新档开头酒吧看不到任何事件属正常 |

## 完整最小例

本节示例在既有教程 MOD 布局与 `GearInModPlugin`（前置单元）之上新增两个同包类，位于 `src\data\scripts\gearin\campaign\`，不新增任何资源、不新增 CSV 行：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInBarEvent          # 本节新增（酒吧事件：显示、对话与生命周期收尾）
│       └── GearInBarEventCreator   # 本节新增（creator：注册入口与抽取参数）
└── jars\
```

### GearInBarEvent（bar event 约定）

**角色**：酒吧事件——继承 `BaseBarEvent`（`PortsideBarEvent` 约定的默认实现），承担显示过滤（`shouldShowAtMarket`）、写提示与选项（`addPromptAndOption`）。

事件对话（`init` / `optionSelected`）与生命周期收尾（`finish` = `done = true` + `notifyWasInteractedWith(this)`）。

事件 ID 不覆写，即类简单名 `"GearInBarEvent"`。

完整代码（与示例工程 fixture 逐字一致）：

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;

/**
 * 完整最小例：酒吧事件（PortsideBarEvent）——酒吧选项、事件对话与生命周期收尾。
 *
 * 事件 ID：BaseBarEvent.getBarEventId() 默认返回类简单名，本示例不覆写，
 * 事件 ID 为 "GearInBarEvent"。该 ID 的消费者：BarEventManager 抽取时按
 * creator 的 getBarEventId() 去重（同一 ID 同时只有一个活跃事件）；BarCMD 展示后
 * 把 event.getBarEventId() 写入市场内存 $BarCMD_shownEvents（带过期时间），
 * 下次进入同一市场时按 ID 匹配复现同一组事件。
 *
 * 生命周期：
 * 1. 抽取与入列：creator.createBarEvent() 创建实例；BarEventManager 把它加入
 *    active 跟踪器（时长 = getBarEventActiveDuration()）并写入 PortsideBarData 活动列表；
 * 2. 显示：玩家进入酒吧时 BarCMD.showOptions() 遍历活动列表，经
 *    shouldShowAtMarket() 过滤、addPromptAndOption() 写入提示与选项——
 *    选项的 optionData 必须是事件自身（this），BarCMD.optionSelected() 只对
 *    optionData instanceof PortsideBarEvent 进入 BarEventDialogPlugin 事件对话；
 * 3. 对话：BarEventDialogPlugin.init() 调 event.init() 接管对话面板；每次点选
 *    经 event.optionSelected()；isDialogFinished() 返回 true 时回到酒吧
 *    （endWithContinue() 决定是否先显示 "Continue"）；
 * 4. 收尾：玩家与事件交互后必须调 BarEventManager.getInstance()
 *    .notifyWasInteractedWith(this)——把事件从活动列表与 active 跟踪器移除，
 *    并把 creator 置入 accepted 超时（默认 60-90 游戏天），否则事件会在
 *    active 时长内继续出现在同一市场、creator 也只能拿到普通超时。
 *
 * 演示内容：酒吧里一位落魄船员兜售一份补给藏匿点的情报。玩家可以花 500 信用点
 * 买下坐标（真实写入玩家舰队 cargo credits），或婉拒、或直接离开；
 * 三个分支都以 notifyWasInteractedWith 收尾，验证 accepted 超时路径。
 */
public class GearInBarEvent extends BaseBarEvent {

    private static final Logger LOG = Global.getLogger(GearInBarEvent.class);

    /** 对话选项 id（仅事件对话内部使用；optionData 用常量比较，不用文本）。 */
    public static final String OPTION_ACCEPT = "gearIn_barAccept";
    public static final String OPTION_DECLINE = "gearIn_barDecline";
    public static final String OPTION_LEAVE = "gearIn_barLeave";

    /** 买下情报的信用点价格（真实从玩家舰队 cargo 扣减）。 */
    public static final int INFO_PRICE = 500;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        // BaseBarEvent 默认：shownAt == null 或 == 当前市场才显示（首次展示后锁定单一市场）。
        boolean ok = super.shouldShowAtMarket(market);
        if (!ok) {
            LOG.info("GearIn bar event: skip " + market.getName()
                    + " (shownAt locked to " + getShownAt() + ")");
            return false;
        }
        // 演示市场条件：只出现在有太空港的市场（新档可访问的核心市场基本满足）。
        if (!market.hasSpaceport()) {
            LOG.info("GearIn bar event: skip " + market.getName() + " (no spaceport)");
            return false;
        }
        LOG.info("GearIn bar event: eligible at " + market.getName());
        return true;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        LOG.info(String.format("GearIn bar event: prompt at %s (event id=%s)",
                market.getName(), getBarEventId()));

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("A disheveled spacer at the bar is telling anyone who will listen about a cache "
                + "of supplies drifting somewhere in this system.");

        // 关键机制：选项的 optionData 必须是事件自身（this）。BarCMD.optionSelected()
        // 只对 optionData instanceof PortsideBarEvent 才创建 BarEventDialogPlugin，
        // 进入事件对话；换成字符串或其他对象则落入规则对话框分支。
        dialog.getOptionPanel().addOption("Listen to the spacer's story", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        LOG.info("GearIn bar event: dialog init (event id=" + getBarEventId() + ")");
        // 对话每次进入都重置完成标记（BaseBarEvent.init 不清 done，需要自行重置）。
        done = false;
        showRumor();
    }

    private void showRumor() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        text.addPara("You sit down across from the spacer and buy them a drink. Once the drink is "
                + "half-gone, they lower their voice and tell you about a cargo pod that drifted off "
                + "a burning freighter - still full of supplies, just waiting to be found. "
                + "\"I'd sell you the coordinates for a song,\" they say.");
        options.clearOptions();
        options.addOption("Buy the coordinates (" + INFO_PRICE + " credits)", OPTION_ACCEPT);
        options.addOption("Hear them out but decline", OPTION_DECLINE);
        options.addOption("Leave the spacer to their drink", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        TextPanelAPI text = dialog.getTextPanel();
        if (OPTION_ACCEPT.equals(optionData)) {
            text.addPara("The spacer scribbles coordinates on a napkin and slides it over. You pay "
                    + "them " + INFO_PRICE + " credits and pocket the napkin.");
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.getCredits().subtract(INFO_PRICE);
            LOG.info("GearIn bar event: bought coordinates, credits -" + INFO_PRICE);
            finish();
        } else if (OPTION_DECLINE.equals(optionData)) {
            text.addPara("The spacer shrugs. \"Suit yourself. The void isn't going anywhere.\"");
            // noContinue = true：结束时不显示 "Continue"，直接回到酒吧选项。
            noContinue = true;
            LOG.info("GearIn bar event: declined");
            finish();
        } else if (OPTION_LEAVE.equals(optionData)) {
            text.addPara("You finish your drink and get up. The spacer returns to telling the story "
                    + "to nobody in particular.");
            noContinue = true;
            LOG.info("GearIn bar event: left");
            finish();
        } else {
            LOG.info("GearIn bar event: unknown option data " + optionData);
        }
    }

    /** 对话收尾：置完成标记并通知 manager 移除本事件（creator 进入 accepted 超时）。 */
    private void finish() {
        done = true;
        BarEventManager.getInstance().notifyWasInteractedWith(this);
        LOG.info("GearIn bar event: finished, notified manager (done=" + isDialogFinished() + ")");
    }

    @Override
    public void wasShownAtMarket(MarketAPI market) {
        super.wasShownAtMarket(market);
        LOG.info("GearIn bar event: shown at " + market.getName());
    }

    @Override
    public void advance(float amount) {
        // 事件在活动列表中每帧被 PortsideBarData.advance 推进（未暂停时）；
        // 本示例没有时间逻辑，保留空实现。需要限时消失的事件在这里推进
        // 倒计时并在到期时由 shouldRemoveEvent() 返回 true。
    }
}
```

### GearInBarEventCreator（creator 约定与注册入口）

**角色**：creator——继承 `BaseBarEventCreator`（`BarEventManager.GenericBarEventCreator` 约定的默认实现），提供注册入口与抽取参数。

本示例只覆写 `createBarEvent()` 创建 `GearInBarEvent`，其余抽取参数（频率权重、active 时长、普通超时、accepted 超时、priority）全部保持默认。

完整代码（与示例工程 fixture 逐字一致）：

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * 完整最小例：酒吧事件 creator（GenericBarEventCreator）——注册入口与抽取参数。
 *
 * 注册入口：BarEventManager.getInstance().addEventCreator(this)，调用点片段见页面正文
 * （GearInModPlugin.onGameLoad 内的注册块）。manager 实例与 creator 列表存放在
 * sector memory（键 $core_genericBarEventManager），随存档保存，因此用
 * hasEventCreator(Class) 作为持久化门控（原版 CoreLifecyclePluginImpl.addBarEvents
 * 与两个成熟实例同款写法），不需要额外的 persistent data 键。
 *
 * 抽取参数（全部在 BaseBarEventCreator 有默认值，本示例只覆写 createBarEvent）：
 * - getBarEventFrequencyWeight()：普通 picker 的抽取权重，默认 10f；
 * - getBarEventActiveDuration()：事件在 active 跟踪器中的时长（游戏天），默认 30+rand*10；
 * - getBarEventTimeoutDuration()：事件未交互、active 过期移除后 creator 的冷却（游戏天），默认 30+rand*10；
 * - getBarEventAcceptedTimeoutDuration()：玩家交互（接受或拒绝都算）后的冷却（游戏天），默认 60+rand*30；
 * - isPriority()：true 时进入 priority picker（先于普通 picker 抽取），默认 false。
 */
public class GearInBarEventCreator extends BaseBarEventCreator {

    private static final Logger LOG = Global.getLogger(GearInBarEventCreator.class);

    @Override
    public PortsideBarEvent createBarEvent() {
        LOG.info("GearIn bar event: creator picked, creating event (id=" + getBarEventId() + ")");
        return new GearInBarEvent();
    }
}
```

对应关系：

- `extends BaseBarEvent`：事件约定的默认实现——`getBarEventId()` 默认返回类简单名 `"GearInBarEvent"`（本示例不覆写，事件 ID 即此值）。
  - `shouldRemoveEvent()` 默认 false（事件靠 active 时长或交互收尾移除）；`endWithContinue()` 默认 true（买下情报后先显示 "Continue" 再回酒吧）。
- `shouldShowAtMarket`：先调 `super`（shownAt 锁定语义），再演示市场条件（`hasSpaceport()`）；日志记录过滤结果，供验证方案核对。
- `addPromptAndOption`：写提示文本 + `addOption("Listen to the spacer's story", this)`——**optionData 是事件自身**，这是进入事件对话的机制（`BarCMD` 只对 `optionData instanceof PortsideBarEvent` 创建 `BarEventDialogPlugin`）。
- `init`：`super.init` 之后**自行重置 `done = false`**（`BaseBarEvent.init` 不清完成标记，原版 `BaseGetCommodityBarEvent.init` 同样自行重置）——读档后同一事件再次进入对话不会卡在已完成状态。
- `optionSelected`：三个分支都用**常量比较 optionData**（不用 optionText，沿用交互对话框部分的 optionData 常量比较纪律）。
  - 买下情报真实扣减玩家舰队 cargo credits（`getCargo().getCredits().subtract`）；拒绝/离开分支设 `noContinue = true`（结束后不显示 "Continue" 直接回酒吧）。
- `finish()`：`done = true` + `notifyWasInteractedWith(this)`——交互收尾必须遵守的规则；日志打出 `isDialogFinished()` 供核对。
- `extends BaseBarEventCreator`：抽取参数全部保持默认（权重 10、active 30–40 天、普通超时 30–40 天、accepted 超时 60–90 天、非 priority），只覆写 `createBarEvent()` 创建 `GearInBarEvent`。

调用点（工程片段——方法所在类 `GearInModPlugin` 归前置单元所有，本节只演示调用，不改动那些文件）：

```java
    // GearInModPlugin（前置单元所有）onGameLoad(boolean newGame) 内新增本节注册块。
    // BarEventManager 实例与 creator 列表存放在 sector memory（$core_genericBarEventManager），
    // 随存档保存；hasEventCreator 即持久化门控（原版 addBarEvents 与成熟实例同款写法）。
    // 原版 CoreLifecyclePluginImpl.onGameLoad 先于 MOD 插件执行（vanilla 固定最先），
    // 因此 getInstance() 不会返回 null（注册时机见故障分析 1）。
    BarEventManager manager = BarEventManager.getInstance();
    if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
        manager.addEventCreator(new GearInBarEventCreator());
        LOG.info("GearIn bar event: creator registered");
    }
```

想演示"高频字段修改验证"，把 `createBarEvent()` 之外的高频参数覆写掉再观察抽取频率变化，例如：

```java
    // GearInBarEventCreator 内覆写（演示用）：
    @Override
    public float getBarEventFrequencyWeight() {
        return 100f; // 默认 10f；权重 ×10，事件出现频率明显上升
    }
```

临时 bar 选项（规则驱动，不经过 creator）的 rules.csv 片段——放在 `data/campaign/rules.csv` 末尾，`AddBarEvents` 分类规则在每次展示酒吧时由 `BarCMD` 触发：

```text
gearIn_barRumor,AddBarEvents,"$market.id == jangala
!$global.gearIn_barRumorDone","AddBarEvent gearIn_barRumorOption ""Ask about the missing supplies"" ""A port official is complaining loudly about missing supplies. Sounds like a problem someone could get paid to solve.""",,,
gearIn_barRumorSel,DialogOptionSelected,$option == gearIn_barRumorOption,"$global.gearIn_barRumorDone = true","The official lowers their voice. ""The manifest says the supplies were never loaded, but I saw the crates leave the dock. Someone has been skimming.""","gearIn_barBackToBar:Back to the bar",
gearIn_barBackToBar,DialogOptionSelected,$option == gearIn_barBackToBar,BarCMD returnFromEvent false,,,
```

（`jangala` 是原版 Corvus 星系（新档出生星系）的市场，验证方案可直接验证；规则变量/分类/`FireBest` 细节见 C11-S03 / C11-S04。）

临时选项点选后走 `DialogOptionSelected` 规则；`BarCMD returnFromEvent false` 从规则对话回到酒吧选项。

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元的编译链路）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含两个 class 条目（可用 `jar tf` 核对）：
- `data/scripts/gearin/campaign/GearInBarEvent.class`
- `data/scripts/gearin/campaign/GearInBarEventCreator.class`

本节两个类的源码与 fixture 逐字一致（`tutorial-fixtures\units\C10-S04\src\main\java\data\scripts\gearin\campaign\` 下同名两文件），fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

**前置数据**：既有教程 MOD 布局与 `GearInModPlugin`（含本节调用点片段，见前置知识 C06-S01 / C06-S02）、本节两个类、（可选）rules.csv 临时选项片段。

**操作步骤**：

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC10s04Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
2. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含两个类的 `.class`。
3. 新建生涯：新档教程进行中时进酒吧，确认无事件与临时选项（预期结果 3）；跳过/完成教程后进入 Jangala 酒吧，核对 `creator picked` / `eligible at` / `prompt at` / `shown at` 四条日志与酒吧面板的提示文本、选项（预期结果 4）。
   - 同一市场 20–40 游戏天内重复进入，确认事件组复现。
4. 事件对话三分支：分别用三个存档（或完成后读档冷却）验证 "Buy the coordinates"（信用点 −500、显示 Continue、点后回酒吧）、"Hear them out but decline"（无 Continue 直接回酒吧）、"Leave the spacer to their drink"（同左）；每次结束都出现 `finished, notified manager (done=true)`（预期结果 5）。
5. 交互后冷却：完成事件后保存读档，长时间等待或跨 60–90 游戏天观察事件不再出现（accepted 超时）；不完成事件时保存读档，事件仍在活动列表、可继续对话（预期结果 6）。
6. 临时选项：启用 rules.csv 片段后进 Jangala 酒吧，出现 "Ask about the missing supplies"；点选 → 规则对话 → "Back to the bar" 回酒吧；再离开重进，临时选项不再出现（flag 已置位）（预期结果 7）。
7. 故障注入 1：注释调用点片段的 `addEventCreator` 行，重启新档：无 `creator picked` 日志、酒吧无该事件、无报错（对应故障分析 2）；改回后确认恢复。
8. 故障注入 2：去掉 `hasEventCreator` 守卫（直接 `addEventCreator`），读档两次再游玩：`creators` 列表叠加、事件出现频率明显上升（对应故障分析 8）；改回后确认恢复。
9. 故障注入 3：把 `addOption(..., this)` 改成 `addOption(..., "someId")`：点击选项后不走事件对话、走规则对话分支（对应故障分析 4）；改回后确认恢复。
10. 故障注入 4：注释 `finish()` 里的 `notifyWasInteractedWith(this)`：事件完成后仍在酒吧重复出现（对应故障分析 5）；改回后确认恢复。
11. 参数演示：给 `GearInBarEventCreator` 覆写 `getBarEventFrequencyWeight()` 返回 100f 重建：事件出现频率明显上升（预期结果 4 的对照）；改回默认后恢复。

**通过信号**：操作 1–7、11 全部符合；操作 8–10 的注入与恢复都符合预期；全程无 `gear_in` 引起的 `ERROR`。

**清理**：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC10s04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到两个类的 `.class`。
3. **新建生涯（教程进行中）**：新档开头教程进行中时酒吧**没有**事件与临时选项（教程进行中全部跳过）——属正常，不是故障。
4. **跳过/完成教程后**：
   - 进入任一有太空港的市场（如 Corvus 星系的 Jangala），交互里选 "Visit the bar"；`starsector.log` 出现
```text
GearIn bar event: creator picked, creating event (id=GearInBarEvent) GearIn bar event: eligible at Jangala GearIn bar event: prompt at Jangala (event id=GearInBarEvent) GearIn bar event: shown at Jangala
```
酒吧面板出现提示 "A disheveled spacer at the bar..." 与选项 "Listen to the spacer's story"（事件 ID `GearInBarEvent` 会出现在同一市场的 `$BarCMD_shownEvents` 里，约 20–40 游戏天内再次进入同一市场复现同一组事件）。
   - 事件不是每次进酒吧都出现：抽取受 `minBarEvents` / `maxBarEvents` / 权重与随机影响；同一时间每个事件 ID 至多一个活跃实例。
5. **事件对话**：点 "Listen to the spacer's story" → 日志 `dialog init (event id=GearInBarEvent)`，对话出现三个选项：
   - 点 "Buy the coordinates (500 credits)"：日志 `bought coordinates, credits -500`，玩家舰队信用点减少 500（`cargo.getCredits()` 可负，演示不检查余额）；日志 `finished, notified manager (done=true)`；对话显示 "Continue"，点后回到酒吧选项（`endWithContinue()` 默认 true 的路径）。
   - 点 "Hear them out but decline" / "Leave the spacer to their drink"：日志 `declined` / `left` + `finished, notified manager (done=true)`；**不显示** "Continue"，直接回到酒吧选项（`noContinue = true` 路径）。
   - 三条路径都触发 `notifyWasInteractedWith`：事件立即从活动列表移除，creator 进入 accepted 超时（60–90 游戏天）——事件在冷却期内不再出现。
6. **读档**：完成后保存并读档——`hasEventCreator` 守卫使 creator 不重复注册（日志无二次 "creator registered" 之外的重复注册行）；未完成时保存读档，事件实例仍在活动列表（随 sector memory 存档）且可继续对话（`init` 里 `done = false` 重置）。全程无 `gear_in` 引起的 `ERROR`。
7. **临时 bar 选项（规则片段）**：在 Jangala 进酒吧，出现 "Ask about the missing supplies" 选项（`AddBarEvents` 规则命中）；点选后进入规则对话并置 `$global.gearIn_barRumorDone = true`；选 "Back to the bar" 回到酒吧选项；再次离开再进同一市场——临时选项不再出现（flag 守卫）。
   - 注意：`$core_tempBarEvents` 每次展示前清空重建，临时选项本身不跨展示持久。

静态自查：`GearInBarEvent` / `GearInBarEventCreator` 与 fixture 逐字一致（包括 import 顺序）； `hasEventCreator(GearInBarEventCreator.class)` 与调用点片段的类名一致。

事件 ID（`GearInBarEvent`）、选项 id（`gearIn_barAccept` 等）与规则 id（`gearIn_barRumor` 等）均不含工作单元编号变体；没有把工作单元编号写进任何运行时名称。

## 实例驱动故障分析

::: tip 定位技巧：沿日志四段找断点

注册 → 抽取（`creator picked` 日志）→ 过滤（`eligible` / `skip` 日志）→ 显示（`prompt` / `shown` 日志），四段日志把链路切成四段；事件"不出现在酒吧"时逐段核对，日志停在哪一段，问题就在哪一段。

:::

1. **`BarEventManager.getInstance()` 在注册点 NPE（null）**。
   - 触发：在 `onApplicationLoad` 里注册——`BarEventManager` 由原版 `CoreLifecyclePluginImpl.onGameLoad` 的 `addBarEvents` 创建（写入 sector memory），资源加载期 sector 尚不存在。
   - 可观察：启动中止、NPE 堆栈指向注册行。
   - 修正：注册放 `onGameLoad` / `onNewGame` 守卫块内（生涯引擎阶段语义）；原版核心插件回调先于 MOD 插件执行，`onGameLoad` 里 `getInstance()` 不会为 null（成熟实例直接依赖此顺序）。
2. **creator 注册了但酒吧里永远没有该事件**。
   - 触发：教程进行中（酒吧事件全部跳过）；或 `createBarEvent()` 返回了 null（抽取时被跳过）；或目标市场被 `shouldShowAtMarket` 过滤掉（如 shownAt 已锁定到别的市场，或市场无太空港）。
   - 可观察：无任何 `GearIn bar event` 日志或只有 `skip` 日志；无报错。
   - 修正：沿"注册 → 抽取（`creator picked` 日志）→ 过滤（`eligible/skip` 日志）→ 显示（`prompt/shown` 日志）"四段日志定位断点；教程阶段直接跳过等待。
3. **事件在某个市场显示过一次后，其他市场再也不出现**。
   - 触发：没理解 `BaseBarEvent.shouldShowAtMarket` 的 shownAt 锁定语义——首次展示后 `shownAt` 固定为该市场，事件只在该市场继续可显示，直到被移除。
   - 可观察：`skip <市场> (shownAt locked to <市场>)` 日志。
   - 修正：这是默认语义，需要跨市场复用的事件自行覆写 `shouldShowAtMarket`（并自行管理状态）。
4. **点了酒吧选项但事件对话没启动，落到普通规则对话**。
   - 触发：`addPromptAndOption` 里 `addOption(text, 字符串或其它对象)`，optionData 不是事件自身。
   - 可观察：点击后文本按 `$option` 规则走（`BarCMD.optionSelected` 的字符串分支），`dialog init` 日志缺失；`$BarCMD` 被写入实体内存。
   - 修正：`addOption(text, this)`——`BarCMD` 只对 `optionData instanceof PortsideBarEvent` 创建 `BarEventDialogPlugin`。
5. **事件完成后仍反复出现在酒吧**。
   - 触发：完成分支没有调 `notifyWasInteractedWith(this)`。
   - 可观察：`finished` 日志后事件仍出现在同一市场（`BarCMD` 的 `shouldShowAtMarket` 对该市场仍为 true）；事件直到 active 时长（30–40 天默认）过期才被当作未挂接事件清理，且 creator 只拿到普通超时。
   - 修正：`done = true` 的同时调 `BarEventManager.getInstance().notifyWasInteractedWith(this)`（必须遵守的规则；原版与成熟实例同款）。
6. **事件"该消失"却一直不消失**。
   - 触发：事件设计了限时逻辑但只改了 `advance`，没有让 `shouldRemoveEvent()` 返回 true。
   - 可观察：倒计时走完事件仍在活动列表与酒吧里（`PortsideBarData.advance` 前后各查一次 `shouldRemoveEvent`；默认 false）。
   - 修正：到期逻辑写在 `advance`，到期 `shouldRemoveEvent()` 返回 true（自清）；或接受 active 时长过期由 manager 按未挂接事件清理（此时 creator 进入普通超时）。
7. **事件未交互就消失了**。
   - 触发：把未交互消失当成 bug——实际是正常生命周期：active 跟踪器按 `getBarEventActiveDuration()`（默认 30–40 游戏天）扣完，事件失去挂接并被清理，creator 进入普通超时。
   - 可观察：`PortsideBarData` 事件消失、creator 被置超时。
   - 修正：想长时间可交互就覆写 `getBarEventActiveDuration()` 调大（成熟实例直接给 `10000000000f` 做一次性事件）。
8. **事件出现频率不对（过高/过低）**。
   - 触发：注册块没有 `hasEventCreator` 守卫——每次读档 `addEventCreator` 再入列一份同 class creator，`creators` 列表膨胀、抽取权重翻倍（`addEventCreator` 不去重）；或覆写了 `getBarEventFrequencyWeight()` 但没意识到它只影响普通 picker（priority 事件永远优先）。
   - 可观察：读档次数越多事件越频繁（权重 = 数量 × 单权重）；priority 事件会挤掉普通事件。
   - 修正：`hasEventCreator(Class)` 守卫（isInstance 判类）；priority 只用于剧情关键事件。
9. **临时 bar 选项（AddBarEvents 规则）不出现或只出现一次就消失**。
   - 触发：市场 id 条件写错（如用不存在的市场）、flag 已被置位、或把临时选项当长期状态读。
   - 可观察：`BarCMD.showOptions` 每次先 `clearTempEvents` 再触发 `AddBarEvents` 规则——条件不匹配则当次无该选项； `$core_tempBarEvents` 容器 expire=0，下一帧内存推进即清除。
   - 修正：核对 `$market.id` 值（原版市场可用 `data/campaign/econ/*.json` 的 `entities` 核对，如 `jangala` 在 `corvus.json`）；一次性 flag 用 `$global.xxx = true` 规则赋值；不要把临时选项当长期状态。
10. **事件对话里点选项没反应或走了错误分支**。
   - 触发：`optionSelected` 里比较的是 `optionText`（显示文本）而不是 `optionData`；或选项 id 常量与 `addOption` 传入不一致。
   - 可观察：文本渲染正常但分支不命中、`unknown option data` 日志（本示例会打出）。
   - 修正：`optionData` 用常量比较（沿用交互对话框部分的同款纪律）；`BarEventDialogPlugin.optionSelected` 先回显再转交 `event.optionSelected`。
11. **保存或读档报错/事件行为错乱**。
   - 触发：事件/creator 自定义字段放了不可序列化状态（对话句柄、引擎对象等非 transient 字段）。
   - 可观察：存档写入/读档异常，或读档后事件对话框句柄失效（`PortsideBarData.active` 与 manager 都随 sector memory 存档，XStream 序列化）。
   - 修正：非 transient 字段只放可序列化数据；句柄类字段保持 `BaseBarEvent` 的 transient 声明。

## 总结

本节把酒吧事件讲成一条链：

- **数据/注册入口**（`bar_events.csv` → `BarEventSpec` → `SpecBarEventCreator` 自动生成；代码入口 `BarEventManager.addEventCreator` + `hasEventCreator` 守卫）
- **调度入列**（`BarEventManager.advance`：游戏天推进、priority/普通双 picker、active/timeout 跟踪器、 `maxTotalBarEventsAsFractionOfEventTypes` 上限）
- **显示过滤**（`BarCMD.showOptions`：`min/maxBarEvents` 数量、 `$BarCMD_shownEvents` 按事件 ID 复现、 `shouldRemoveEvent` / `shouldShowAtMarket` / `addPromptAndOption` / `wasShownAtMarket`）
- **对话收尾**（`BarEventDialogPlugin` 包装 → `init` / `optionSelected` / `isDialogFinished` / `endWithContinue` → `notifyWasInteractedWith` 移除 + accepted 超时）

记忆要点：

- RC8 的角色名：bar event = `PortsideBarEvent`，creator = `GenericBarEventCreator`（`BarEventManager` 内部接口），manager = `BarEventManager`；活动列表 = `PortsideBarData`。
- 所有时长都是**游戏天**（`Misc.getDays` 换算）；manager 与活动列表存 sector memory，**随存档保存**（事件字段必须可序列化）。
- 酒吧选项的 optionData 必须是**事件自身**（`addOption(text, this)`）——这是进入事件对话的唯一机制。
- 交互收尾必须 `notifyWasInteractedWith(this)`（接受与拒绝都算）；未交互的事件按 active 时长过期、creator 拿普通超时。
- `shouldRemoveEvent` 默认 false（事件靠 active 时长或交互移除）；`shouldShowAtMarket` 默认 shownAt 锁定单一市场；教程进行中酒吧事件全部隐藏。
- 临时 bar 选项（`AddBarEvent` / `RemoveBarEvent` + `AddBarEvents` 规则分类）每次展示前清空重建，不是 `PortsideBarEvent`，点选走规则对话。

交互对话框面板 API 与事件注册/序列化纪律是本节底座；本节的 `hasEventCreator` 守卫模式沿用原版 `CoreLifecyclePluginImpl.addBarEvents` 的写法。

本节只覆盖"事件本体"链路：mission 类酒吧事件的 `HubMissionBarEventWrapper` 包装与 `bar_events.csv` 的 mission plugin 编写。

`BaseBarEventWithPerson` 的 market 稳定人物生成（人物生成面）、规则对话的深度语法（规则引擎部分）由后续单元展开。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent`：bar event 约定——11 个回调方法。
- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData`：活动事件列表（全局单例，随 sector memory 存档）。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：全局调度器与 `GenericBarEventCreator` 内部接口。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent`：bar event 默认实现——transient 对话句柄、shownAt 锁定、`getBarEventId` 默认类简单名。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator`：creator 默认实现与全部抽取参数默认值。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson`：带 market 稳定人物的默认实现。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：CSV spec 自动生成的 creator（mission / 普通分派）。
- `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`：事件对话包装（init / optionSelected / endEvent）。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧 UI 展示管线与选项分派。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent` / `RemoveBarEvent`：临时 bar 选项规则命令。
- `com.fs.starfarer.api.loading.BarEventSpec`：bar_events.csv 行的解析。
- `com.fs.starfarer.api.SettingsAPI`：`getAllBarEventSpecs()` / `getBarEventSpec(id)`。
- `com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`：原版注册入口（`addBarEvents`）。
- `com.fs.starfarer.api.impl.campaign.ids.Tags`：`MISSION_PRIORITY`（"priority" tag）。
- `com.fs.starfarer.api.util.TimeoutTracker`：active / timeout 跟踪器（`advance` 扣到 0 移除）。
- `com.fs.starfarer.api.util.Misc`：`getDays` 时间换算。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：`set(key, value[, expire])` 记忆读写。
- `data/campaign/bar_events.csv`：酒吧事件 spec 数据（11 列）。
- `data/config/settings.json`：`minBarEvents` / `maxBarEvents` / `barEventProbOneMore` / `maxTotalBarEventsAsFractionOfEventTypes`。
- `data/campaign/rules.csv`：`marketVisitBar` / `bar_optionSel` / `AddBarEvents` 分类规则 / `BarCMD returnFromEvent false`。
- `data/campaign/econ/corvus.json`：`jangala` 市场实体（验证临时选项片段用）。
- `data/scripts/gearin/campaign`：本节两个示例类的包目录。
- `tutorial-fixtures/units/C10-S04/src/main/java/data/scripts/gearin/campaign`：本节 fixture（`GearInBarEvent` / `GearInBarEventCreator`）。
- `Starsector/starsector-core/starsector.log`：验证日志四段的主要依据。
