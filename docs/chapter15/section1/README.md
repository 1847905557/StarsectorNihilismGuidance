# 制作酒吧事件

本节制作一个生涯酒吧事件，一位落魄船员出售补给藏匿点的情报，玩家可以购买、婉拒或离开。示例会让事件进入酒吧、完成对话并正确收尾。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 区分酒吧事件、事件创建器与调度器的职责；
- 通过 `bar_events.csv` 或 Java 创建器注册酒吧事件；
- 控制事件的抽选权重、显示条件、有效期和超时；
- 实现提示、选项、事件对话与交互收尾；
- 根据入列、显示和对话结果定位酒吧事件故障。

## 前置知识

- [C7-S1] 的 `modPlugin` 生命周期与注册判断；
- [C7-S2] 的 `Global.getSector()` 阶段语义；
- [C11-S3] 的 `MarketAPI` 与市场条件过滤；
- [C14-S3] 的交互对话框面板、`showInteractionDialog` 与插件生命周期；
- [C12-S1] 的人物、关系与市场稳定随机人物；
- [C14-S1] 的 `advance(amount)` 真实秒语义与 `Misc.getDays` 换算；
- [C14-S2] 的事件边界、监听器生命周期与 XStream 序列化边界；
- [C16-S3] 的 Rules 命令与 [C16-S4] 的 Rules 内存。

## 术语

酒吧事件牵扯一组角色与一条展示管线，先统一叫法，便于后文在 API 名与中文说法之间保持一致。按术语约定，API 类名保留源码大小写。本节首次完整讲解以下术语：

- **酒吧事件 (bar event)**：API 名保留 `PortsideBarEvent`。
    - 一次可在星球/空间站酒吧出现、由玩家主动选择进入的短对话事件：酒吧提示 + 选项 (`addPromptAndOption`)→ 事件对话 (`init` / `optionSelected`)→ 结束返回酒吧。
    - 它独立于普通交互对话框：酒吧事件不自己调用 `showInteractionDialog`，而是由 `BarCMD` 在酒吧 UI 里把选项的 optionData 指到事件自身，再经 `BarEventDialogPlugin` 包装接管对话。
- **creator**：API 名保留 `GenericBarEventCreator`(`BarEventManager` 的内部接口) 与默认实现 `BaseBarEventCreator`。
    - 负责两件事：`createBarEvent()` 创建事件实例 (返回 null 表示本次放弃抽取)；提供抽取参数 (频率权重、active 时长、普通超时、accepted 超时、是否 priority)。
- **manager**：API 名保留 `BarEventManager`。全局酒吧事件调度器 (`EveryFrameScript`)：持有 creator 集合与 active/timeout 两个跟踪器，按游戏天推进并周期性抽取事件入列。
- **活动列表 (PortsideBarData)**：当前全部已抽取、等待被酒吧显示的事件实例列表。manager 决定 "创建哪些事件"，活动列表决定 "哪些事件可被显示"。
- **临时 bar 选项 (temp bar option)**：规则系统经 `AddBarEvent` 规则命令写入市场内存、由 `BarCMD` 当次展示的临时酒吧选项。它**不是** `PortsideBarEvent` 事件，点选后走普通规则对话 (`$option == <optionId>`)，每次展示前被清空重建。
- **事件对话 (event dialog)**：`BarEventDialogPlugin` 包装下的事件交互对话。玩家点选酒吧选项后，`BarCMD` 把对话框插件切换为 `BarEventDialogPlugin`，事件自身通过 `init` / `optionSelected` 驱动对话，结束时切换回 `BarCMD` 并回到酒吧。

## 梳理酒吧事件关系

术语就位后，下一步把两条入口链路接起来，数据文件链路把 CSV 行变成可用事件，代码注册链路登记手写 creator，最终都汇入 `BarEventManager` 的 creator 集合。先看清三个角色的公开 API 归属：

### 角色命名与归属速查表

任务清单里的 "BarEventAPI / BarEventCreatorAPI / BarManagerAPI" 对应本主题的三个角色；公开 API 中的具体类型名如下 (全部在 `com.fs.starfarer.api.*` 包路径内)：

| 角色 | 类型 | 位置 |
|---|---|---|
| bar event(事件约定) | `PortsideBarEvent`(接口) | `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent` |
| bar event(默认实现) | `BaseBarEvent`(抽象) | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent` |
| bar event(带人物默认实现) | `BaseBarEventWithPerson`(抽象) | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson` |
| creator(约定 + 默认实现) | `GenericBarEventCreator`(`BarEventManager` 内部接口)+ `BaseBarEventCreator` | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`、`BaseBarEventCreator` |
| manager(调度器) | `BarEventManager` | `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager` |
| 活动列表 | `PortsideBarData` | `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData` |
| CSV spec 与路由 | `BarEventSpec` / `SpecBarEventCreator` | `com.fs.starfarer.api.loading.BarEventSpec`、`com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator` |
| 事件对话包装 | `BarEventDialogPlugin` | `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin` |
| 酒吧 UI 规则命令 | `BarCMD` | `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD` |
| 临时 bar 选项规则命令 | `AddBarEvent` / `RemoveBarEvent` | `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`、`RemoveBarEvent` |

### 数据文件链路 (bar\_events.csv)

1. 游戏在 CSV spec 加载期读取 `data/campaign/bar_events.csv`：`loadSpecsFromJson("bar event id", "data/campaign/bar_events.csv", BarEventSpec.class, ...)`(内部加载实现)，`bar event id` 列是 spec key。
2. 每行构造一个 `BarEventSpec`：`bar event id`(必填)、`plugin`(必填，`row.getString("plugin")`)、`freq`(默认 10)、`prob`(默认 1)、`min/max dur`(默认 30/40)、`min/max timeout`(默认 30/40)、`min/max accepted timeout`(默认 60/90)、`tags`(逗号分隔，可空)。
3. `SettingsAPI.getAllBarEventSpecs()` / `getBarEventSpec(id)` 暴露全部 spec。
4. `BarEventManager.readResolve()` → `updateBarEventCreatorsFromSpecs()`：对每个尚无 auto-added creator 的 spec 创建 `SpecBarEventCreator(specId)`。手工注册的同 ID creator 不阻止自动项建立，两者可以并存。
5. `SpecBarEventCreator`(继承 `BaseBarEventCreator`) 把 spec 的字段接到抽取参数上：
    - `createBarEvent()` 按 `spec.isMission()` 分派：plugin 是 `HubMissionWithBarEvent` 时创建 `HubMissionBarEventWrapper`(mission 酒吧包装)，否则 `spec.createEvent()` 直接 `getInstanceOfScript(pluginClass)` 实例化普通 `PortsideBarEvent`；
    - `isPriority()` = `spec.hasTag(Tags.MISSION_PRIORITY)`("priority" tag)。

**非 mission 的普通事件** (如 `LuddicFarmerBarEvent`、`HistorianBarEvent`) 不走 CSV，走代码注册。

### 注册入口

原版注册入口 (`CoreLifecyclePluginImpl`，`com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`)：

- `onGameLoad(boolean)` → `addScriptsIfNeeded()` → `addBarEvents()`：
    - `sector.hasScript(PortsideBarData.class)` 缺则 `addScript(new PortsideBarData())`；
    - `sector.hasScript(BarEventManager.class)` 缺则 `addScript(new BarEventManager())`；
    - `BarEventManager.getInstance()` 后对每个原版手写 creator 做 `!hasEventCreator(XxxCreator.class) → addEventCreator(new XxxCreator())`。

`PortsideBarData` 与 `BarEventManager` 构造器都把实例写入 sector memory 的固定键 (`$core_PortsideBarData` / `$core_genericBarEventManager`)。

**manager 实例、creator 列表、active 事件列表都随存档保存** (sector memory 持久化；`TimeoutTracker` 的 `set` 由 `readResolve` 重建)。

MOD 注册入口 (本节示例)：

```java
BarEventManager manager = BarEventManager.getInstance();
if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
    manager.addEventCreator(new GearInBarEventCreator());
}
```

要点：`hasEventCreator(Class)` 用 `isInstance` 判断，而 `addEventCreator` 本身**不去重** (直接 `creators.add`)，**判断缺失的可观察结果是同 class 的 creator 反复入列、抽取权重翻倍**。

注册时机必须在生涯引擎建立之后 (`onGameLoad` / `onNewGame`)；原版核心插件的 `onGameLoad` 先于 MOD 插件执行 (vanilla 固定最先，成熟实例直接依赖此顺序)，因此 `getInstance()` 不判空。

### 调度与抽取 (BarEventManager.advance)

两条入口都汇入 manager 之后，看调度器如何决定事件何时被创建、何时过期。`BarEventManager.advance(float amount)` 每帧被推进 (`runWhilePaused()` 返回 false，暂停不推进)：

1. `days = Misc.getDays(amount)`：**所有时长都是游戏天** (10 真实秒 = 1 游戏日)。
2. `active.advance(days)` / `timeout.advance(days)`：active 跟踪器里的事件按天扣减，扣到 0 自动移除；timeout 跟踪器里的 creator 同理。
3. `tracker`(`IntervalUtil(0.4f, 0.6f)`，游戏天) 到点才执行抽取；`tracker2`(20–40 天) 到点更新种子。
4. 未交互过期清理：在 `barEventCreators` 映射里但不在 active 跟踪器中的事件即未交互且 active 时长已过期，从映射与活动列表移除，并把对应 creator 置入超时：`dur = max(creator.getBarEventTimeoutDuration(), timeout 剩余)`。
5. 数量上限：`max = round(creators.size() * settings.getFloat("maxTotalBarEventsAsFractionOfEventTypes"))`(默认 0.5，见 settings.json)，最小 1；`active` 数量达到 `max` 或每个 creator 都有活跃事件时本次不再抽取。
6. 抽取：先建 priority picker(`isPriority()` 的 creator，权重取 `getBarEventFrequencyWeight()`) 与普通 picker。
    - **priority 优先**，未命中再抽普通；同一 `getBarEventId()` 已有活跃事件或 creator 在 timeout 中的跳过；抽中后 `createBarEvent()`**，返回 null 则本次放弃**。
    - 事件加入 `active` 跟踪器 (时长取 `getBarEventActiveDuration()`)、记录 `barEventCreators.put(event, creator)`、`PortsideBarData.getInstance().addEvent(event)` 入活动列表。

所有时长都用游戏天

酒吧事件的抽取与展示节奏都建立在 `Misc.getDays` 的换算上 (10 真实秒 = 1 游戏日)，且推进只在未暂停时发生 —— 用游戏天计价让 "一次事件从抽取到过期" 的语义与玩家实际游戏节奏挂钩，而不是与真实时间挂钩；暂停挂机时事件不会批量过期。

### 活动列表与事件生命周期 (PortsideBarData)

事件入列之后，谁决定它能被显示、何时被移除？活动列表与事件生命周期回答这两个问题。`PortsideBarData`(`EveryFrameScript`，`runWhilePaused()` false) 持有 `active` 列表：

- `addEvent(event)` 入列；`getEvents()` 返回列表 (活的引用，不是副本)。
- `advance(amount)` 每帧遍历：**在调 `curr.advance(amount)` 前后各查一次 `shouldRemoveEvent()`**，返回 true 就移除；需要 "限时消失" 的事件在 `advance` 里推进倒计时、到期由 `shouldRemoveEvent` 返回 true 自清。
- `removeEvent(event)` 同时从 manager 的 active 跟踪器移除。

事件生命周期完整流程：

### 事件生命周期速查表

| 阶段 | 触发 | 关键调用 |
|---|---|---|
| 抽取入列 | manager 周期性抽取 | `creator.createBarEvent()` → `active.add(event, activeDuration)` → `PortsideBarData.addEvent` |
| 显示过滤 | 玩家进酒吧，`BarCMD.showOptions` | `shouldRemoveEvent()` → `shouldShowAtMarket(market)` → `addPromptAndOption` → `wasShownAtMarket` |
| 事件对话 | 玩家点选酒吧选项 | `BarEventDialogPlugin.init` → `event.init` → `event.optionSelected` |
| 交互收尾 | 对话完成 | `done = true` + `BarEventManager.getInstance().notifyWasInteractedWith(this)` |
| 未交互过期 | active 时长扣完 | 未交互过期清理：活动列表移除 + creator 普通超时 |

**交互收尾 (必须)**：对话结束必须调 `BarEventManager.getInstance().notifyWasInteractedWith(this)`：它把事件从活动列表与 active 跟踪器移除，并把 creator 置入 `getBarEventAcceptedTimeoutDuration()`(与剩余普通超时取较大值)。

**只有接受分支算 "已交互"**：原版 `BaseGetCommodityBarEvent` 的 confirm 分支调用 `notifyWasInteractedWith`，cancel 分支只结束事件。

不调用的话，事件在 active 时长内继续留在活动列表、同一市场可能再次出现，creator 也只能拿到普通超时 (30–40 天而非 60–90 天)。

**存档边界 (必须)**：活动列表随 sector memory 存档 (XStream)，事件实例的非 transient 字段必须可序列化。

`BaseBarEvent` 已把对话句柄声明为 `transient`(`dialog` / `text` / `options` / `memoryMap`)，`done` / `noContinue` 同样声明为 `transient`，`shownAt` 是普通字段 (`MarketAPI`，可序列化)。

事件自定义字段只放可序列化数据。

### 酒吧显示 (BarCMD.showOptions)

事件在活动列表里就绪后，还差最后一步：玩家进入酒吧时，事件如何被挑出来显示。玩家在星球/空间站交互里选 "Take a shuttle down to visit a dockside bar"(`marketVisitBar` 选项，`data/campaign/rules.csv`) 触发 `bar_optionSel` 规则。

随后依次执行 `FireBest BarShuttleDownPrintDesc` / `FireBest BarPrintDesc` / `BarCMD showOptions`。

`BarCMD.showOptions()` 的展示管线：

1. 数量：`minBarEvents`(默认 1)/ `maxBarEvents`(默认 3)/ `barEventProbOneMore`(默认 0.5) 加权抽 `num`(见 settings.json)。
2. 已展示集合：市场内存 `$BarCMD_shownEvents` —— **存在则按事件 ID 匹配复现同一组事件** (`event.getBarEventId()`)，不存在则全量洗牌并 `needToSaveShown = true`。
    - 展示后 `mem.set(key, shown, time)`(20–40，即 `BAR_EVENT_MIN/MAX_TIME_BEFORE_CHANGING`)。内存过期按**游戏天**扣减 (`Memory` 内部实现：`convertToDays(amount)`、仅未暂停) —— 同一市场约 20–40 游戏天内复现同一组事件。
3. 临时选项：`AddBarEvent.clearTempEvents(market)` 清空 `$core_tempBarEvents`，然后 `FireAll.fire(..., "AddBarEvents")` 触发全部匹配的 `AddBarEvents` 分类规则，再逐个显示 (上限 `max`)。
4. 事件循环：**教程进行中 (`TutorialMissionIntel.isTutorialInProgress()`) 全部跳过**；数量占满即 break。
    - 依次检查 `shouldRemoveEvent()` 与 `shouldShowAtMarket(market)`；通过后 `event.addPromptAndOption(dialog, memoryMap)`(事件自己写提示与选项)→ `wasShownAtMarket(market)` → `shown.add(event.getBarEventId())`。
    - `isAlwaysShow()` 的事件不占普通名额，且排在最前 (按 `isAlwaysShow` 排序)。
5. 底部固定 "Leave the bar" 选项 + ESC 快捷键；`dialog.setPlugin(this)` + `init(dialog)`，对话框插件切换为 `BarCMD` 自身。

`BarCMD.optionSelected` 分派：

- `optionData instanceof PortsideBarEvent` → 创建 `BarEventDialogPlugin(this, this, event, memoryMap)`，`dialog.setPlugin(plugin)` + `plugin.init(dialog)`，**事件对话的唯一入口**；
- `"barLeave"` → `leaveBar()`(回市场对话)；`"barContinue"` → `showOptions(true)`；
- 其他字符串 (临时选项 / mission wrapper 选项)→ 写 `$BarCMD` 到实体内存、`dialog.setPlugin(originalPlugin)`、`$option = optionData`、`FireBest "DialogOptionSelected"`，落到规则对话。

### 事件对话 (BarEventDialogPlugin)

玩家点选酒吧选项后，对话权从 `BarCMD` 交给事件外壳。`BarEventDialogPlugin`(`com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`，`InteractionDialogPlugin`) 是事件的对话外壳：

- `init(dialog)`：`event.init(dialog, memoryMap)`；`event.isDialogFinished()` 已为 true 则直接 `endEvent()`。
- `optionSelected(optionText, optionData)`：`dialog.addOptionSelectedText(optionData)` 回显 (复用交互对话框面板的文本回显语义)；`event.optionSelected(optionText, optionData)`；事件完成则 `endEvent()`。
- `endEvent()`：`dialog.setPlugin(originalPlugin)`(切回 `BarCMD`)+ `cmd.returningFromEvent(event)` → `returningFromEvent(event.endWithContinue())`。
    - `endWithContinue()` 为 true 先显示 "Continue"(再点回酒吧)，false 直接 `showOptions(true)`("Nothing of note is going on at the bar." 提示)。

### 临时规则命令 (AddBarEvent / RemoveBarEvent)

以上都是事件本体；规则系统还能绕开事件链，直接给酒吧加临时选项。规则驱动的临时 bar 选项 (不属于 `PortsideBarEvent`，常用来给特定市场/特定条件加一次性酒吧入口)：

- `AddBarEvent <option id> <option text> <blurb> [color]`：从 `dialog.getInteractionTarget().getMarket()` 取市场。
    - option text 与 blurb 支持规则 token 替换 (`getStringWithTokenReplacement`)；写入市场内存 `$core_tempBarEvents` 容器 (`events.put(optionId, data)`，`LinkedHashMap` 保序)。
    - `getTempEvents` 不存在时创建并 `mem.set(KEY, events, 0f)`，即 **expire=0**，下一帧内存推进即清除，只活在当次展示里。
- `RemoveBarEvent <option id>`：`AddBarEvent.removeTempEvent(market, optionId)` 删除单个临时选项。

临时 bar 选项不能当长期状态读取

`BarCMD.showOptions` 每次展示前 `clearTempEvents` 再重触发 `AddBarEvents` 规则，临时选项只活在当次展示里。

`RemoveBarEvent` 只删临时选项，**不会**移除 `PortsideBarData` 活动列表里的事件。

期待 "加一次永久生效" 或 "删临时选项后事件也消失" 都会落空。

### 围绕一次交互实现回调

酒吧事件只为当前交互实现创建人物、写提示、添加选项、处理选择和收尾所需的回调。其余默认行为交给 `BaseBarEvent` 与 `BaseBarEventCreator`。判断遗漏时沿着调度入列、市场显示、选项点击和交互收尾四个阶段检查，比逐项对照接口表更快。

## 规则强度

### 本节规则速查表

链路与接口全表就位后，把本节反复出现的必须/建议规则集中列出，正文与验证方案直接引用。本节必须遵守的规则清单 (违反后的可观察结果均附上)：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 玩家与事件交互并结束对话时必须按事件语义调用 `BarEventManager.getInstance().notifyWasInteractedWith(this)`(原版 `BaseGetCommodityBarEvent` 仅 confirm 分支调用) | 必须 | 未调用时事件留在活动列表直到 active 时长过期；creator 只拿到普通超时而非 accepted 超时，重复出现频率更高 |
| `addPromptAndOption` 里酒吧选项的 optionData 必须是事件自身 (`addOption(text, this)`；原版 `BaseGetCommodityBarEvent` 同款) | 必须 | 点击选项不进入事件对话：`BarCMD.optionSelected` 只对 `optionData instanceof PortsideBarEvent` 创建 `BarEventDialogPlugin`；其他类型落入规则对话分支 (`$option` / `DialogOptionSelected`)，事件对话永不启动 |
| 事件与 creator 的非 transient 字段必须可序列化 (活动列表与 manager 存于 sector memory、随存档 XStream 保存) | 必须 | 保存/读档异常、读档后事件行为错乱；对话句柄 (`dialog` / `text` / `options` / `memoryMap`) 必须保持 `BaseBarEvent` 的 transient 声明 |
| creator 注册只发生在生涯引擎建立之后 (`onGameLoad` / `onNewGame`)，注册块必须用 `hasEventCreator(Class)` 判断 (`addEventCreator` 不去重) | 必须 | 注册过早 (如 `onApplicationLoad`)：`getInstance()` 返回 null 解引用 NPE、启动中止；判断缺失：每次读档重复入列同 class creator，抽取权重翻倍、事件过频 |
| `shouldShowAtMarket` 覆写时理解 shownAt 锁定语义 (`BaseBarEvent` 默认首次展示后锁定单一市场)，确需跨市场复用再自行处理 | 必须 (语义规则) | 事件在 A 市场展示后 B 市场不再出现，若覆写时忘了先调 `super` 或自行实现，过滤结果与预期不符 |
| `getBarEventId()` 与 creator 的 `getBarEventId()` 保持一致且稳定 (默认都是类简单名；覆写时两处同步) | 建议 | 不一致时 `$BarCMD_shownEvents` 按 ID 复现失效：市场内存里的 ID 匹配不到事件，该市场事件组显示为空；manager 的 active 去重键错位 |
| `isPriority()` / `isAlwaysShow()` 只用于剧情关键事件 | 建议 | 太多 priority 事件挤占普通事件 (`BarEventManager` "Having too many priority events could crowd out all other events entirely")；`isAlwaysShow` 事件永远占用展示名额 |
| 临时 bar 选项 (`$core_tempBarEvents`) 只活在当次展示里，不能当长期状态读取；`RemoveBarEvent` 只删临时选项、不删活动列表事件 | 事实 | 期待 "加一次永久生效" 或 "删临时选项后事件也消失" 都会落空 (临时选项当长期状态读、`RemoveBarEvent` 不删活动列表事件) |
| 教程进行中 (`TutorialMissionIntel.isTutorialInProgress()`) 酒吧事件全部跳过 | 事实 | 新档开头酒吧看不到任何事件属正常 |

## 实现酒吧事件

酒吧事件要连续通过 creator 在场、manager 抽中、市场允许显示、选项进入事件对话、对话通知 manager 收尾这几个边界。以下片段按这条链逐段接通。

### 注册事件 creator

把 creator 判断放入 `GearInModPlugin.onGameLoad()`。manager 的 creator 列表随存档保存，`hasEventCreator` 就是这里的去重门。

```java
BarEventManager manager = BarEventManager.getInstance();
if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
    manager.addEventCreator(new GearInBarEventCreator());
}
```

在 `GearInBarEventCreator.createBarEvent()` 中创建事件：

```java
@Override
public PortsideBarEvent createBarEvent() {
    return new GearInBarEvent();
}
```

首次抽中时日志出现 creator picked；连续读档不会增加同类 creator 的数量或抽取权重。

### 过滤可用市场

在 `GearInBarEvent.shouldShowAtMarket()` 中先保留基类的 `shownAt` 锁定，再检查太空港。

```java {3}
@Override
public boolean shouldShowAtMarket(MarketAPI market) {
    if (!super.shouldShowAtMarket(market)) {
        return false;
    }
    return market.hasSpaceport();
}
```

事件首次显示后只在同一市场继续可见；没有太空港的市场不会得到该选项。

### 把酒吧选项指向事件

在 `addPromptAndOption()` 中写提示，并把事件对象自身作为 optionData。`BarCMD` 依靠这个类型判断进入事件对话。

```java {8}
@Override
public void addPromptAndOption(InteractionDialogAPI dialog,
        Map<String, MemoryAPI> memoryMap) {
    super.addPromptAndOption(dialog, memoryMap);
    dialog.getTextPanel().addPara(
            "A disheveled spacer is talking about a drifting supply cache.");
    dialog.getOptionPanel().addOption(
            "Listen to the spacer's story", this);
}
```

酒吧面板出现提示与选项，点击后日志进入 `GearInBarEvent.init()`，而不是写入 `$option` 的普通规则分支。

### 建立购买与拒绝分支

在 `init()` 中重置完成状态并建立选项；在 `optionSelected()` 中只比较 optionData 常量。

```java
@Override
public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    super.init(dialog, memoryMap);
    done = false;
    dialog.getOptionPanel().addOption(
            "Buy the coordinates (500 credits)", OPTION_ACCEPT);
    dialog.getOptionPanel().addOption(
            "Hear them out but decline", OPTION_DECLINE);
}
```

```java
if (OPTION_ACCEPT.equals(optionData)) {
    Global.getSector().getPlayerFleet().getCargo()
            .getCredits().subtract(INFO_PRICE);
    finish();
} else if (OPTION_DECLINE.equals(optionData)) {
    noContinue = true;
    finish();
}
```

购买分支扣除 500 信用点并显示 Continue；拒绝分支不扣款，直接返回酒吧。

### 统一结束事件

把所有结束分支收敛到 `finish()`。`done` 让对话外壳结束，manager 通知负责移除活动事件并启动 accepted 冷却。

```java
private void finish() {
    done = true;
    BarEventManager.getInstance().notifyWasInteractedWith(this);
}
```

购买、拒绝和离开结束后，事件立即离开活动列表，并在默认 60–90 游戏天 accepted 冷却内不再被抽取。

## 组装酒吧事件实现

规则明确之后，可以动手了。本节示例在既有教程 MOD 布局与 `GearInModPlugin`(前置单元) 之上新增两个同包类，位于 `src/data/scripts/gearin/campaign\`，不新增任何资源、不新增 CSV 行：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       ├── GearInBarEvent          # 本节新增 (酒吧事件：显示、对话与生命周期收尾)
│       └── GearInBarEventCreator   # 本节新增 (creator：注册入口与抽取参数)
└── jars\
```

### GearInBarEvent(bar event 约定)

**角色**：酒吧事件 —— 继承 `BaseBarEvent`(`PortsideBarEvent` 约定的默认实现)，承担显示过滤 (`shouldShowAtMarket`)、写提示与选项 (`addPromptAndOption`)。

事件对话 (`init` / `optionSelected`) 与生命周期收尾 (`finish` = `done = true` + `notifyWasInteractedWith(this)`)。

事件 ID 不覆写，即类简单名 `"GearInBarEvent"`。

完整代码：

事件本体写好了，还需要一个 creator 把事件交到调度器手里并给出抽取参数。

### GearInBarEventCreator(creator 约定与注册入口)

**角色**：creator —— 继承 `BaseBarEventCreator`(`BarEventManager.GenericBarEventCreator` 约定的默认实现)，提供注册入口与抽取参数。

本示例只覆写 `createBarEvent()` 创建 `GearInBarEvent`，其余抽取参数 (频率权重、active 时长、普通超时、accepted 超时、priority) 全部保持默认。

完整代码：

对应关系：

- `extends BaseBarEvent`：事件约定的默认实现，`getBarEventId()` 默认返回类简单名 `"GearInBarEvent"`(本示例不覆写，事件 ID 即此值)。
    - `shouldRemoveEvent()` 默认 false(事件靠 active 时长或交互收尾移除)；`endWithContinue()` 默认 true(买下情报后先显示 "Continue" 再回酒吧)。
- `shouldShowAtMarket`：先调 `super`(shownAt 锁定语义)，再演示市场条件 (`hasSpaceport()`)；日志记录过滤结果，供验证方案核对。
- `addPromptAndOption`：写提示文本 + `addOption("Listen to the spacer's story", this)` —— **optionData 是事件自身**，这是进入事件对话的机制 (`BarCMD` 只对 `optionData instanceof PortsideBarEvent` 创建 `BarEventDialogPlugin`)。
- `init`：`super.init` 之后**自行重置 `done = false`**(`BaseBarEvent.init` 不清完成标记，原版 `BaseGetCommodityBarEvent.init` 同样自行重置)，读档后同一事件再次进入对话不会卡在已完成状态。
- `optionSelected`：三个分支都用**常量比较 optionData**(不用 optionText，沿用交互对话框部分的 optionData 常量比较纪律)。
    - 买下情报真实扣减玩家舰队 cargo credits(`getCargo().getCredits().subtract`)；拒绝/离开分支设 `noContinue = true`(结束后不显示 "Continue" 直接回酒吧)。
- `finish()`：`done = true` + `notifyWasInteractedWith(this)`，交互收尾必须遵守的规则；日志打出 `isDialogFinished()` 供核对。
- `extends BaseBarEventCreator`：抽取参数全部保持默认 (权重 10、active 30–40 天、普通超时 30–40 天、accepted 超时 60–90 天、非 priority)，只覆写 `createBarEvent()` 创建 `GearInBarEvent`。

调用点 (工程片段，方法所在类 `GearInModPlugin` 归前置单元所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(前置单元所有)onGameLoad(boolean newGame) 内新增本节注册块。
    // BarEventManager 实例与 creator 列表存放在 sector memory($core_genericBarEventManager)，
    // 随存档保存；hasEventCreator 即持久化门控 (原版 addBarEvents 与成熟实例同款写法)。
    // 原版 CoreLifecyclePluginImpl.onGameLoad 先于 MOD 插件执行 (vanilla 固定最先)，
    // 因此 getInstance() 不会返回 null。
    BarEventManager manager = BarEventManager.getInstance();
    if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
        manager.addEventCreator(new GearInBarEventCreator());
        LOG.info("GearIn bar event: creator registered");
    }
```

想演示 "高频字段修改验证"，把 `createBarEvent()` 之外的高频参数覆写掉再观察抽取频率变化，例如：

```java
    // GearInBarEventCreator 内覆写 (演示用)：
    @Override
    public float getBarEventFrequencyWeight() {
        return 100f; // 默认 10f；权重 ×10，事件出现频率明显上升
    }
```

临时 bar 选项 (规则驱动，不经过 creator) 的 rules.csv 片段 —— 放在 `data/campaign/rules.csv` 末尾，`AddBarEvents` 分类规则在每次展示酒吧时由 `BarCMD` 触发：

```text
gearIn_barRumor,AddBarEvents,"$market.id == jangala
!$global.gearIn_barRumorDone","AddBarEvent gearIn_barRumorOption ""Ask about the missing supplies"" ""A port official is complaining loudly about missing supplies. Sounds like a problem someone could get paid to solve.""",,,
gearIn_barRumorSel,DialogOptionSelected,$option == gearIn_barRumorOption,"$global.gearIn_barRumorDone = true","The official lowers their voice. ""The manifest says the supplies were never loaded, but I saw the crates leave the dock. Someone has been skimming.""","gearIn_barBackToBar:Back to the bar",
gearIn_barBackToBar,DialogOptionSelected,$option == gearIn_barBackToBar,BarCMD returnFromEvent false,,,
```

(`jangala` 是原版 Corvus 星系的市场。带教程的新档从 Galatia 开始；跳过教程或 spacer 路线从 Corvus 开始，开发路线还可以从 hyperspace 开始。规则变量、分类和 `FireBest` 细节见 [C16-S3] / [C16-S4]。)

临时选项点选后走 `DialogOptionSelected` 规则；`BarCMD returnFromEvent false` 从规则对话回到酒吧选项。

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用前置单元的编译链路)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含两个 class 条目 (可用 `jar tf` 核对)：

- `data/scripts/gearin/campaign/GearInBarEvent.class`
- `data/scripts/gearin/campaign/GearInBarEventCreator.class`

## 验证方案

前置数据：既有教程 MOD 布局与 `GearInModPlugin`(含本节调用点片段，见前置知识 [C6-S1] / [C7-S1])、本节两个类、(可选)rules.csv 临时选项片段。

1. 游戏构建：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含两个类的 `.class`。
2. 新建生涯，在教程进行中进入酒吧，观察事件和临时选项。
3. 跳过或完成教程后进入 Jangala 酒吧，查看 `creator picked`、`eligible at`、`prompt at`、`shown at` 四条日志，以及酒吧面板的提示文本和选项。
4. 在同一市场等待 20–40 游戏天后再次进入酒吧，观察事件组。
5. 分别使用三个存档 (或完成后读档冷却)，依次选择 "Buy the coordinates"、"Hear them out but decline"、"Leave the spacer to their drink"，观察信用点、Continue 选项、返回酒吧过程和结束日志。
6. 完成事件后保存并读档，跨 60–90 游戏天观察事件是否再次出现。
7. 不完成事件时保存并读档，观察活动列表和继续对话入口。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到两个类的 `.class`。
- **跳过/完成教程后**：进入任一有太空港的市场 (如 Corvus 星系的 Jangala)，交互里选 "Take a shuttle down to visit a dockside bar"；`starsector.log` 依次出现 `GearIn bar event: creator picked, creating event (id=GearInBarEvent)`、`GearIn bar event: eligible at Jangala`、`GearIn bar event: prompt at Jangala (event id=GearInBarEvent)`、`GearIn bar event: shown at Jangala`。
- 酒吧面板出现提示 "A disheveled spacer at the bar..." 与选项 "Listen to the spacer's story"(事件 ID `GearInBarEvent` 会出现在同一市场的 `$BarCMD_shownEvents` 里，约 20–40 游戏天内再次进入同一市场复现同一组事件)。
- 事件不是每次进酒吧都出现：抽取受 `minBarEvents` / `maxBarEvents` / 权重与随机影响；同一时间每个事件 ID 至多一个活跃实例。
- **事件对话**：点 "Listen to the spacer's story" 后日志出现 `dialog init (event id=GearInBarEvent)`，对话出现三个选项。
- 点 "Buy the coordinates (500 credits)"：日志 `bought coordinates, credits -500`，玩家舰队信用点减少 500(`cargo.getCredits()` 可负，演示不检查余额)；日志 `finished, notified manager (done=true)`；对话显示 "Continue"，点后回到酒吧选项 (`endWithContinue()` 默认 true 的路径)。
- 点 "Hear them out but decline" / "Leave the spacer to their drink"：日志 `declined` / `left` + `finished, notified manager (done=true)`；**不显示** "Continue"，直接回到酒吧选项 (`noContinue = true` 路径)。
- 三条路径都触发 `notifyWasInteractedWith`：事件立即从活动列表移除，creator 进入 accepted 超时 (60–90 游戏天) —— 事件在冷却期内不再出现。
- **读档**：完成后保存并读档 —— `hasEventCreator` 判断使 creator 不重复注册 (日志无二次 "creator registered" 之外的重复注册行)；未完成时保存读档，事件实例仍在活动列表 (随 sector memory 存档) 且可继续对话 (`init` 里 `done = false` 重置)。全程无 `gear_in` 引起的 `ERROR`。

## 故障分析

- **注册时出现空指针异常**：`onApplicationLoad` 阶段还没有 sector 和 `BarEventManager`。

    - **解决方案**：将 creator 注册移到 `onGameLoad` 或 `onNewGame`。

- **酒吧始终没有该事件**：creator 未被抽取、返回了 null，或市场被 `shouldShowAtMarket` 过滤。

    - **解决方案**：依次核对 `creator picked`、`eligible/skip`、`prompt/shown` 日志，修正最先缺失的阶段。

- **事件只在首次市场出现**：默认 `shownAt` 会把事件锁定到第一次展示的市场。

    - **解决方案**：需要跨市场显示时覆写 `shouldShowAtMarket` 并管理状态。

- **点击酒吧选项后进入普通规则对话**：`addOption` 的 optionData 不是事件对象。

    - **解决方案**：改为 `addOption(text, this)`。

- **完成后的事件仍反复出现**：完成分支没有通知 manager 移除活动事件。

    - **解决方案**：设置完成状态时调用 `notifyWasInteractedWith(this)`。

- **倒计时结束后事件仍未移除**：到期状态没有让 `shouldRemoveEvent()` 返回 true。

    - **解决方案**：同步更新到期字段和移除判断。

- **未交互事件提前消失**：默认 active 时长 30–40 游戏天已经耗尽。

    - **解决方案**：需要更长窗口时覆写 `getBarEventActiveDuration()`。

- **读档越多事件越频繁**：每次载入都追加了同类 creator。

    - **解决方案**：注册前用 `hasEventCreator(Class)` 判断。

- **普通事件几乎不再出现**：某个事件被设为 priority，优先抽取挤占了普通 picker。

    - **解决方案**：只为剧情关键事件保留 priority。

- **临时酒吧选项没有出现**：`AddBarEvents` 的市场条件或一次性 flag 不满足。

    - **解决方案**：核对 `$market.id` 与规则内存值。

- **临时酒吧选项下一帧就消失**：`$core_tempBarEvents` 会在展示前重建，并以 `expire=0` 清理。

    - **解决方案**：长期状态改存独立 memory flag。

- **事件选项进入错误分支**：`optionSelected` 比较了显示文本，或常量与 `addOption` 的 optionData 不一致。

    - **解决方案**：统一比较 optionData 常量。

- **保存或读档时事件字段报错**：事件或 creator 保存了非 transient 的对话句柄、引擎对象。

    - **解决方案**：持久字段只保留可序列化数据，句柄保持 transient。

## 总结

本节把酒吧事件接成了一条完整流程：注册入口决定事件从哪来，调度入列决定何时出现，显示过滤决定在哪个市场出现，对话收尾决定何时离开。排错时按 "注册 → 抽取 → 过滤 → 显示" 的顺序核对日志，流程停在哪一环，日志就缺哪一环。

可复用的判断如下：注册只发生在生涯引擎建立之后，并用 `hasEventCreator(Class)` 判断去重 (沿用原版 `CoreLifecyclePluginImpl.addBarEvents` 的写法)；所有时长都是游戏天 (`Misc.getDays` 换算)，manager 与活动列表随存档保存，事件字段必须可序列化；酒吧选项的 optionData 必须是事件自身 (`addOption(text, this)`)，交互收尾按事件语义调用 `notifyWasInteractedWith(this)`。`shouldRemoveEvent` 默认 false、`shouldShowAtMarket` 默认锁定单一市场、教程进行中全部隐藏；多数 "该出现没出现、该消失没消失" 的现象都可由上述三条默认语义解释，怀疑事件逻辑前先对照它们。

临时 bar 选项走规则系统，每次展示前清空重建，不是事件本体，也不能当长期状态读；`RemoveBarEvent` 只删临时选项，不碰活动列表。

本节只覆盖 "事件本体" 链路：mission 类酒吧事件的 `HubMissionBarEventWrapper` 包装与 `bar_events.csv` 的 mission plugin 编写。

`BaseBarEventWithPerson` 的 market 稳定人物生成属于人物对象主题，规则对话的深度语法属于 Rules 主题；本节只保留酒吧事件的接入边界。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent`：bar event 的回调方法约定。
- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData`：活动事件列表 (全局单例，随 sector memory 存档)。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：全局调度器与 `GenericBarEventCreator` 内部接口。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent`：bar event 默认实现：transient 对话句柄、shownAt 锁定、`getBarEventId` 默认类简单名。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator`：creator 默认实现与全部抽取参数默认值。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson`：带 market 稳定人物的默认实现。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：CSV spec 自动生成的 creator(mission / 普通分派)。
- `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`：事件对话包装 (init / optionSelected / endEvent)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧 UI 展示管线与选项分派。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AddBarEvent`：写入临时 bar 选项的规则命令；
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.RemoveBarEvent`：移除临时 bar 选项的规则命令。
- `com.fs.starfarer.api.loading.BarEventSpec`：bar\_events.csv 行的解析。
- `com.fs.starfarer.api.SettingsAPI`：`getAllBarEventSpecs()` / `getBarEventSpec(id)`。
- `com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`：原版注册入口 (`addBarEvents`)。
- `com.fs.starfarer.api.impl.campaign.ids.Tags`：`MISSION_PRIORITY`("priority" tag)。
- `com.fs.starfarer.api.util.TimeoutTracker`：active / timeout 跟踪器 (`advance` 扣到 0 移除)。
- `com.fs.starfarer.api.util.Misc`：`getDays` 时间换算。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：`set(key, value[, expire])` 记忆读写。
- `data/campaign/bar_events.csv`：酒吧事件 spec 数据 (11 列)。
- `data/config/settings.json`：`minBarEvents` / `maxBarEvents` / `barEventProbOneMore` / `maxTotalBarEventsAsFractionOfEventTypes`。
- `data/campaign/rules.csv`：`marketVisitBar` / `bar_optionSel` / `AddBarEvents` 分类规则 / `BarCMD returnFromEvent false`。
- `data/campaign/econ/corvus.json`：`jangala` 市场实体 (验证临时选项片段用)。
- `Starsector/starsector-core/starsector.log`：按总结中的顺序核对流程日志的主要依据。

## 完整最小例

::: details 完整成品：GearInBarEvent

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
 * 完整最小例：酒吧事件 (PortsideBarEvent) —— 酒吧选项、事件对话与生命周期收尾。
 * 事件 ID 默认为类简单名 "GearInBarEvent"：creator 抽取按 ID 去重，BarCMD 展示后
 * 把 ID 写入市场内存，下次进入同一市场按 ID 复现同一组事件。生命周期：creator
 * 创建实例并入 active 跟踪器；玩家进酒吧时经 shouldShowAtMarket 过滤并写入选项
 * (optionData 必须是事件自身)；点选进入事件对话；交互后必须 notifyWasInteractedWith
 * 收尾，把事件移出并把 creator 置入 accepted 超时。演示：花 500 信用点买补给
 * 藏匿点情报、婉拒或离开，三个分支都验证 accepted 超时路径。
 */
public class GearInBarEvent extends BaseBarEvent {

    private static final Logger LOG = Global.getLogger(GearInBarEvent.class);

    /** 对话选项 id(仅事件对话内部使用；optionData 用常量比较，不用文本)。 */
    public static final String OPTION_ACCEPT = "gearIn_barAccept";
    public static final String OPTION_DECLINE = "gearIn_barDecline";
    public static final String OPTION_LEAVE = "gearIn_barLeave";

    /** 买下情报的信用点价格 (真实从玩家舰队 cargo 扣减)。 */
    public static final int INFO_PRICE = 500;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        // BaseBarEvent 默认：shownAt == null 或 == 当前市场才显示 (首次展示后锁定单一市场)。
        boolean ok = super.shouldShowAtMarket(market);
        if (!ok) {
            LOG.info("GearIn bar event: skip " + market.getName()
                    + " (shownAt locked to " + getShownAt() + ")");
            return false;
        }
        // 演示市场条件：只出现在有太空港的市场 (新档可访问的核心市场基本满足)。
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

        // 关键机制：选项的 optionData 必须是事件自身 (this)。BarCMD.optionSelected()
        // 只对 optionData instanceof PortsideBarEvent 才创建 BarEventDialogPlugin，
        // 进入事件对话；换成字符串或其他对象则落入规则对话框分支。
        dialog.getOptionPanel().addOption("Listen to the spacer's story", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        LOG.info("GearIn bar event: dialog init (event id=" + getBarEventId() + ")");
        // 对话每次进入都重置完成标记 (BaseBarEvent.init 不清 done，需要自行重置)。
        done = false;
        showRumor();
    }

    private void showRumor() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        text.addPara("You sit down across from the spacer and buy them a drink. Once the drink is "
                + "half-gone, they lower their voice and tell you about a cargo pod that drifted off "
                + "a burning freighter - still full of supplies, just waiting to be found. "
                + "\"I'd sell you the coordinates for a song,\"they say.");
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

    /** 对话收尾：置完成标记并通知 manager 移除本事件 (creator 进入 accepted 超时)。 */
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
        // 事件在活动列表中每帧被 PortsideBarData.advance 推进 (未暂停时)；
        // 本示例没有时间逻辑，保留空实现。需要限时消失的事件在这里推进
        // 倒计时并在到期时由 shouldRemoveEvent() 返回 true。
    }
}
```

:::

::: details 完整成品：GearInBarEventCreator

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * 完整最小例：酒吧事件 creator(GenericBarEventCreator) —— 注册入口与抽取参数。
 *
 * 注册入口：BarEventManager.getInstance().addEventCreator(this)
 * (GearInModPlugin.onGameLoad 内的注册块)。manager 实例与 creator 列表存放在
 * sector memory(键 $core_genericBarEventManager)，随存档保存，因此用
 * hasEventCreator(Class) 作为持久化门控，不需要额外的 persistent data 键。
 *
 * 抽取参数 (全部在 BaseBarEventCreator 有默认值，本示例只覆写 createBarEvent)：
 * - getBarEventFrequencyWeight()：普通 picker 的抽取权重，默认 10f；
 * - getBarEventActiveDuration()：事件在 active 跟踪器中的时长 (游戏天)，默认 30+rand*10；
 * - getBarEventTimeoutDuration()：事件未交互、active 过期移除后 creator 的冷却 (游戏天)，默认 30+rand*10；
 * - getBarEventAcceptedTimeoutDuration()：玩家交互 (接受或拒绝都算) 后的冷却 (游戏天)，默认 60+rand*30；
 * - isPriority()：true 时进入 priority picker(先于普通 picker 抽取)，默认 false。
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

:::
