# 枢纽任务系统：Intel 与事件链

本节带您把生涯任务的枢纽系统组装成一条可以运行的链：Intel 条目负责展示与跟踪，阶段图与触发器负责推进，目标检测负责判定完成，最终用一个 `GearInHubMission` 示例把整条链接进酒吧。前置章节已经备好 Intel 登记、脚本推进与酒吧调度，现在正好用一套完整任务把它们串起来；做完后，您可以在游戏里验收任务从出现、接取、推进到结算的全程。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 区分任务清单旧名对应的实际类型：枢纽任务约定是 `HubMission`，Intel 约定是 `IntelInfoPlugin` 与 `IntelManagerAPI`，并说明 `MissionDefinitionAPI` 属于战役任务而非生涯任务；
- 解释 `HubMission` 接口与 `BaseHubMission` 类族的职责分工，以及 `HubMissionWithTriggers` / `HubMissionWithSearch` / `HubMissionWithBarEvent` 各补充了什么能力；
- 复述任务生命周期的创建、接受、推进与结束四段，说出每段的关键调用与失败表现；
- 解释阶段管理的内部结构 (`StageData` 与阶段图) 和 `setCurrentStage` 的完整副作用链；
- 说明目标完成检测的帧推进轮询、对话驱动与时限三通道，以及 `ConditionChecker` 家族和 `setStageOn*` / `connectWith*` 的连接声明方式；
- 区分单任务触发器链与跨任务全局链两种事件链形态，并说明各自的注册与回写方式；
- 从零编写一个 `HubMissionWithBarEvent` 任务 (本节 `GearInHubMission`)，用一条 `bar_events.csv` 行和六条 `rules.csv` 规则把它接进酒吧，并验证从出现、接取、推进到结算的完整流程；
- 根据症状定位枢纽任务的典型故障 (见 "故障分析")。

## 前置知识

本节会复用已经建立的对话、Intel、脚本与酒吧调度基础，请先熟悉以下主题：

- 交互对话框三面板、`showInteractionDialog`、`SectorAPI.adjustPlayerReputation`(任务完成结算复用) 与 Intel 两条登记链 (`addIntel` 立即可见 / `queueIntel` 通信中继可见性)—— 本节 Intel 侧从这里继续 (C08-S09)。
- 人物 (`PersonAPI`) 与 market 联系人 —— 任务的给任务者 (giver) 与目标联系人都是人物：
  - `OfficerManagerEvent`、`ContactIntel` 语义被 `BaseHubMission` 的 `findOrCreateGiver` / `findOrCreateTrader` / `setPersonIsPotentialContactOnSuccess` 使用 (C09-S01)。
- `EveryFrameScript` 推进约定与 `convertToDays` 换算 ——`BaseIntelPlugin` 实现 `EveryFrameScript`，`advanceImpl` 里 `days = clock.convertToDays(amount)`：
  - 任务随 `scripts` 列表随档保存、`transientScripts` 读档清空的结论直接适用 (C10-S01)。
- 事件边界与生命周期纪律 —— 任务的 "注册入口 → 派发 → 可观察结果" 方法论同源；任务实例随存档序列化的 XStream 边界与监听器一致 (C10-S02)。
- `PortsideBarEvent` / `BarEventManager` / `PortsideBarData` / `HubMissionBarEventWrapper` 的调度与 `notifyWasInteractedWith` 收尾 —— 本节 bar 路线直接复用该调度器，`SpecBarEventCreator` 的 mission 分派是两条路线的交界点 (C10-S04)。
- 战役 (mission) 与 `MissionDefinitionAPI`—— 对照对象，本节不重复 (C03-S01)。
- `modPlugin` 生命周期与 `Global.getSector()` 阶段语义 —— 任务相关注册 (`bar_events.csv` 由加载期读取、无代码注册；代码侧只有 `addScript` / `addIntel` 发生在生涯引擎建立之后)(C06-S02 / C06-S03)。

## 术语

按术语约定，API 名保留源码大小写。本节首次完整讲解以下页面级术语 (不进入共享术语表)：

- **枢纽任务 (hub mission)**：API 名保留 `HubMission`(接口) 与 `BaseHubMission`(基类)。一种 "任务 = 阶段图 + 触发器 + Intel" 的生涯任务模型：
  - 任务是一个 `BaseIntelPlugin`(用户在 Intel 界面跟踪进度)，同时是一个 `EveryFrameScript`(每帧推进阶段)，通过 `MissionHub`(联系人任务板) 或酒吧事件两种入口提供给用户。
- **任务阶段 (stage)**：API 名保留 `StageData`。任务状态机的一个节点，用任意 `Object` 作 id(惯例是枚举常量)。任务在同一时刻只有一个 `currentStage`；阶段之间用**连接 (`StageConnection`)**组成有向图。
- **目标 (objective)**：本节术语。任务给用户的一项 "当前要做的事"= 当前阶段 + 阶段连接上的完成条件 (`ConditionChecker`)。任务的成功/失败由 `successStages` / `failStages` 集合判定，不单独建模。
- **事件链 (event chain)**：本节术语，两种形态：①单任务内 —— 阶段连接 (`connections`)+ 触发器 (`triggers`) 组成的事件序列；②跨任务 —— 多个任务 Intel 经全局内存阶段 (如成熟实例的 `MissionStage`) 串成的大剧情链。
- **触发器 (trigger)**：API 名保留 `MissionTrigger`。绑定到若干阶段、带一个 `ConditionChecker` 与一串 `TriggerAction` 的一次性动作；条件满足且任务处于绑定阶段时执行并从列表移除。

## 文件与 API 链路

以下按任务的组装顺序展开：先澄清类型名，再建立 Intel 与 HubMission 两条链，随后依次是生命周期、阶段管理、目标检测与事件链，最后落到注册入口。前几节以静态知识为主，从生命周期一节开始进入可以观察的运行行为。

### 命名说明：HubMissionAPI / IntelAPI / MissionDefinitionAPI

任务清单里的三个名字的实际归属如下 (全部在 `com.fs.starfarer.api.*` 包路径内)：

| 任务清单名称 | 实际类型 | 位置 |
|---|---|---|
| HubMissionAPI | `HubMission`(接口)| `com.fs.starfarer.api.impl.campaign.missions.hub.HubMission` |
| 任务基类族 | `BaseHubMission` / `HubMissionWithTriggers` / `HubMissionWithSearch` / `HubMissionWithBarEvent` | 同目录 |
| 任务板 | `MissionHub` / `BaseMissionHub` | 同目录 |
| 任务创建器 | `HubMissionCreator` / `BaseHubMissionCreator` | 同目录 |
| 酒吧包装 | `HubMissionBarEventWrapper` | 同目录 |
| IntelAPI | `IntelInfoPlugin`(接口)+ `IntelManagerAPI`(登记表)+ `BaseIntelPlugin`(默认实现)| `com.fs.starfarer.api.campaign.comm` 与 `com.fs.starfarer.api.impl.campaign.intel` |
| MissionDefinitionAPI | `MissionDefinitionAPI`(存在，但属于战役任务)| `com.fs.starfarer.api.mission.MissionDefinitionAPI` |

`MissionDefinitionAPI` 的 javadoc 原文第一行就说明它由战役任务脚本用来创建任务场景内容 —— 它服务 `data/missions/<id>/` 的战役任务场景，与生涯枢纽任务是两套互不相通的系统。

::: info 为什么本节能给出实现类细节
`BaseHubMission` 等 "实现" 类位于公开 API 源码内 (`com.fs.starfarer.api.impl.campaign.missions.hub.*` 包路径，随 `starfarer.api.jar` 发行)，可以直接读取 —— 本节的行为结论与代码片段都来自这条公开面。
:::

先看 Intel 半身：它决定任务以什么形式出现在 Intel 界面，`BaseHubMission` 的展示与收尾行为全部来自这一侧。

### 1. Intel 侧：登记链与生命周期

`IntelInfoPlugin`(`com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`) 是 Intel 条目的完整约定 (含 `TableRowClickData` / `ListInfoMode` / `IntelSortTier` 等嵌套类型)。

MOD 侧几乎不直接实现它，而是继承 `BaseIntelPlugin`(`com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`)。

它同时实现 `IntelInfoPlugin`、`CallableEvent`、`EveryFrameScript`、`CommSnifferReadableIntel` 四个接口，是 "Intel 条目 + 每帧脚本" 的组合基类。

登记表 (`IntelManagerAPI`) 的语义 (Intel 登记链的结论此处不展开，只补 `IntelManagerAPI` 内部实现细节)：

| 方法 | 语义 |
|---|---|
| `addIntel(plugin)` / `addIntel(plugin, forceNoMessage)` / `addIntel(plugin, forceNoMessage, textPanel)` | 立即登记：置 `playerVisibleTimestamp`、调 `reportMadeVisibleToPlayer`、默认 `autoAddCampaignMessage()` 加一条通信消息 (`forceNoMessage` / `textPanel` 两个变体改写消息去向)|
| `queueIntel(plugin, maxCommQueueDays)` / `queueIntel(plugin)` | 进通信队列；`IntelManagerAPI.advance` 每帧 (未暂停) 检查 `canMakeVisibleToPlayer(relay)` 或 `forceAddNextFrame()`，可见则 `addIntel`—— 前 3 条单独显示、第 4 条起合并进 `NewMessagesIntel` |
| `removeIntel(plugin)` | 从 intel 表与通信队列同时移除；确实移除了才调 `reportRemovedIntel` |
| `hasIntel(plugin)` / `getIntel()` / `getIntel(Class)` / `getFirstIntel(Class)` | 查询 |

`BaseIntelPlugin` 的生命周期 (MOD 写任务 Intel 时直接继承)：

- `advance(float amount)`：`isEnded()` 直接返回；`isEnding()` 时按游戏天扣 `endingTimeRemaining`，扣完置 `ended = true` 并调 `notifyEnded()`；否则转 `advanceImpl(amount)`—— 子类覆写点。
- `endAfterDelay()` / `endAfterDelay(float days)` / `endImmediately()`：置 `ending = true` + 剩余天数：
  - `getBaseDaysAfterEnd()` 默认 **3 游戏日**—— 任务结束后 Intel 条目还留 3 天展示结算结果，之后由引擎按 `shouldRemoveIntel()`(默认 `isEnded`) 清理。
- `isDone()` = `isEnded()`、`runWhilePaused()` = false—— 作为 `EveryFrameScript`，任务结束后引擎自动从脚本列表移除。
- 更新推送：`sendUpdate(listInfoParam, textPanel)`(走 `addIntelToTextPanel`)、`sendUpdateIfPlayerHasIntel(...)`(走 `CampaignUIAPI.addMessage`)—— 任务阶段变化时给用户 "进度更新" 消息的两条通道。

Intel 条目只是展示层，任务的状态与推进逻辑在 `HubMission` 接口与实现类族里。

### 2. HubMission 接口与类族

`HubMission`(16 个方法)：

| 方法 | 语义 |
|---|---|
| `String getTriggerPrefix()` | 规则分类前缀 = `getMissionId()` |
| `String getMissionId()` / `void setMissionId(String)` | 任务 ID(规则键根、`getReason()` 返回值)|
| `void setGenRandom(Random)` | 注入任务随机源 (hub 从 creator 传入)|
| `void accept(InteractionDialogAPI, Map<String, MemoryAPI>)` | 用户接受任务 |
| `void updateInteractionData(dialog, memoryMap)` | 把任务状态写进对话 LOCAL 内存 (`$<missionId>_stage` 等)|
| `void createAndAbortIfFailed(MarketAPI market, boolean barEvent)` | 创建入口；失败即 `abort()` |
| `void abort()` | 清理任务副作用 (回滚 `changes`)|
| `boolean isMissionCreationAborted()` | 创建是否已放弃 |
| `MissionHub getHub()` / `void setHub(MissionHub)` | 任务板归属 |
| `HubMissionCreator getCreator()` / `void setCreator(HubMissionCreator)` | 创建器归属 |
| `PersonAPI getPerson()` / `PersonAPI getPersonOverride()` / `void setPersonOverride(PersonAPI)` | 给任务者 (giver) 人物 |

类族职责 (全部在 `missions.hub` 包内)：

| 类 | 职责 |
|---|---|
| `BaseHubMission` | 抽象基类：Intel 半身 + 脚本半身 + 阶段图 + 触发器 + 奖励/声望 + 生成辅助。子类必须实现 `create(MarketAPI createdAt, boolean barEvent)` |
| `HubMissionWithTriggers` | 触发器 API 的完整实现：`beginXxxTrigger` / `trigger*` 动作族 / `endTrigger`；`FleetSize` / `FleetQuality` / `OfficerNum` / `OfficerQuality` 枚举与几十个 `TriggerAction` 嵌套类 |
| `HubMissionWithSearch` | 目标选择系统：`require*` / `prefer*` 要求族 + `pickSystem` / `pickPlanet` / `pickEntity` / `pickMarket` / `pickCommodity` 抽取器 |
| `HubMissionWithBarEvent` | 酒吧入口补充：`shouldShowAtMarket(MarketAPI)`(默认 true，在 `create()` **之前**被调用)|
| `MissionHub` / `BaseMissionHub` | 联系人任务板：持有 creator 列表与 `offered` 列表，`updateOfferedMissions` 抽取、`accept` 派发、`listMissions` 写规则选项 |
| `HubMissionCreator` / `BaseHubMissionCreator` | 任务创建器：`createHubMission(MissionHub)` 实例化、频率/超时/声望过滤、完成计数 |
| `HubMissionBarEventWrapper` | 酒吧包装 (`BaseBarEvent` 子类)：把 `HubMissionWithBarEvent` 任务包装成酒吧事件 |
| `MissionTrigger` | 触发器数据类 (`ConditionChecker` + 阶段集合 + `TriggerAction` 列表)|

类族职责已经清楚，接下来看任务在运行时的四段生命周期：创建、接受、推进与结束。

### 3. 生命周期四段

**创建 (createAndAbortIfFailed)**：

```java
protected boolean isBarEvent = false;
public void createAndAbortIfFailed(MarketAPI market, boolean barEvent) {
    isBarEvent = barEvent;
    if (getPerson() != null) {
        genMissionRewardMultAndQuality();
    }
    if (!create(market, barEvent)) {
        abort();
    }
}
protected abstract boolean create(MarketAPI createdAt, boolean barEvent);
```

要点：`create()` 返回 `false` 表示 "本次不成立该任务"(目标选不出来、ref 键被占用、市场条件不满足)。

基类立即 `abort()` 回滚已登记的 `changes`(`VariableSet` / `MadeImportant` / `EntityAdded` / `PersonAdded` 等 `Abortable`)。

随后把 `aborted` 置位 —— 调用方用 `isMissionCreationAborted()` 跳过该任务。

::: caution `create()` 的副作用必须可回滚
**`create()` 里做的一切副作用都必须登记进 `changes` 或走基类方法**，否则失败回滚不干净。可观察结果：创建失败或任务结束时实体/人物/标志残留在星区 —— 重复出现、地图永久标记、内存键不清理 (规则强度 "必须" 第 2 条)。
:::

**接受 (accept)**：

```java
public void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    setImportant(true);
    if (startingStage == null) {
        throw new RuntimeException("startingStage can not be null. Use setStartingStage()");
    }
    setCurrentStage(startingStage, null, null);
    acceptImpl(dialog, memoryMap);
    // cargoOnAccept 直接进用户货舱
    Global.getSector().getIntelManager().addIntel(this, false, text);
    Global.getSector().addScript(this);
    startingStage = null;
    runTriggers();
}
```

顺序固定：

- 置重要标记；
- **必须已 `setStartingStage`**(null 抛 RuntimeException)。
- `setCurrentStage(startingStage)`(进入起点阶段，此时若起点就是 success/fail 阶段会立即结算)。
- `acceptImpl`(子类放置任务实体/舰队)；
- `addIntel`(Intel 条目即刻可见)；
- `addScript`(脚本半身开始每帧推进)；
- `startingStage` 置空 (任务已在进行中，abort 语义随之切换)。
- `runTriggers()`

**推进 (advanceImpl)**：

```java
@Override
protected void advanceImpl(float amount) {
    float days = Global.getSector().getClock().convertToDays(amount);
    StageData stage = getData(currentStage);
    if (stage != null) {
        stage.elapsed += days;
    }
    elapsed += days;
    if (timeLimit != null && timeLimit.days < elapsed && (timeLimit.noLimitWhileInSystem == null ||
            timeLimit.noLimitWhileInSystem != Global.getSector().getCurrentLocation())) {
        setCurrentStage(timeLimit.failStage, null, null);
        timeLimit = null;
        runTriggers();
    }
    tracker.advance(days);
    if (tracker.intervalElapsed()) {
        checkStageChangesAndTriggers(null, null);
    }
}
```

每帧：当前阶段 `elapsed` 与任务总 `elapsed` 按游戏天累计 (`convertToDays` 换算，见前置知识)→ 时限判定 (超时且用户不在豁免星系则直接切失败阶段)→ `tracker`(`IntervalUtil(0.09f, 0.11f)` 游戏天) 到点执行阶段连接检查。

**结束 (setCurrentStage 的终态分支)**：`setCurrentStage` 切到 `successStages` 成员 → `endSuccess`：
  - 置 `result`、加 XP(`getXPReward`)、加信用点 (`getCreditsReward`)、`adjustRep(RepActions.MISSION_SUCCESS)`、`endSuccessImpl`、`cargoOnSuccess` 入舱、`endAfterDelay()`、发 `END_MISSION_UPDATE` 更新。
  - `creator.incrCompleted()`、潜在联系人入列、`abort()`、置 `completedKey`。

`endFailure` 与 `endAbandon` 同理 (无奖励、`incrFailed`、无惩罚失败阶段走 `noPenaltyFailStages`)。

最终 `endAfterDelay` 的倒计时走完 → `BaseIntelPlugin` 置 `ended` → **`notifyEnded()`(`super.notifyEnded()` + `Global.getSector().removeScript(this)`)**。

脚本半身由引擎在下一帧按 `isDone()` 清理，Intel 条目按 `shouldRemoveIntel()` 由 `IntelManagerAPI` 清理。

生命周期围绕阶段切换展开。先看阶段的数据结构：`StageData`、阶段图与 `setCurrentStage` 的完整副作用。

### 4. 阶段管理 (StageData 与阶段图)

内部结构：

```java
protected Object currentStage = null;
protected LinkedHashMap<Object, StageData> stages = ...;
protected List<Object> successStages = new ArrayList<>();
protected List<Object> failStages = new ArrayList<>();
protected List<Object> noPenaltyFailStages = new ArrayList<>();
protected Object abandonStage = Abandon.ABANDON;
protected List<StageConnection> connections = ...;
protected List<MissionTrigger> triggers = ...;
protected List<Abortable> changes = ...;
```

- `StageData`：`id` + `elapsed`(当前阶段内累计游戏天)+ `important`(本阶段的 `ImportanceData` 列表)。`getData(id)` 延迟创建。
- `StageConnection`：`from` / `to` / `checker`——`from == null` 表示 "任意阶段"。
- 阶段声明：`setStartingStage`、`setSuccessStage` / `addSuccessStages`、`setFailureStage` / `addFailureStages`、`setNoPenaltyFailureStage` / `addNoPenaltyFailureStages`、`setAbandonStage` / `setNoAbandon`。
  - **阶段 id 惯例是枚举常量**(`updateInteractionData` 会做 `((Enum)getCurrentStage()).name()`)。

`setCurrentStage` 的完整副作用链 —— 写任务代码必须理解：

1. 同阶段直接返回 (幂等)；
2. 离开旧阶段：解除该阶段登记的 `makeImportant`(`Misc.makeUnimportant` + `removeImportanceChanges`)、`unset` 阶段标志 (`removeMemoryFlagChanges`)；
3. 写入 `currentStage`；若新阶段在 `timeLimit.endLimitStages` 里则取消时限；
4. 进入新阶段：登记新阶段 `makeImportant`(`Misc.makeImportant(memory, getReason())` + `changes.add(MadeImportant)`)、写 `FlagData` 标志 (`setFlag` 族登记的按阶段生效标志)；
5. 结束判定：`!doNotEndMission` 时依次检查 `successStages` / `failStages` / `abandonStage`，命中即结算；否则 `sendUpdateForNextStep(NEXT_STEP_UPDATE, textPanel)` 发 "下一步" 更新；
6. `runTriggers()`。

阶段图决定任务往哪里走，完成检测决定它什么时候走。本节看目标定义与三条检测通道。

### 5. 目标定义与完成检测

**目标定义**：目标 = 当前阶段 + 阶段连接的 `ConditionChecker`。连接声明两条 API 族：

| 声明方式 | 语义 | 检测器 |
|---|---|---|
| `setStageOnGlobalFlag(to, flag)` | 全局内存布尔标志为 true → 进 `to` | `GlobalBooleanChecker` |
| `setStageOnMemoryFlag(to, HasMemory, flag)` / `setStageOnMemoryFlag(to, MemoryAPI, flag)` | 指定对象内存布尔标志 → 进 `to` | `MemoryBooleanChecker` |
| `setStageOnDaysElapsed(to, days)` | 从任务开始累计满天数 → 进 `to` | `DaysElapsedChecker` |
| `setStageOnEntityNotAlive(to, entity)` | 实体死亡/失效 → 进 `to` | `EntityNotAliveChecker` |
| `setStageOnEnteredLocation(to, location)` | 用户进入地点 → 进 `to` | `EnteredLocationChecker` |
| `setStageInRangeOfEntity(to, entity, range)` | 用户进入实体范围 → 进 `to` | `InRangeOfEntityChecker` |
| `setStageOnWithinHyperspaceRange(to, entity, rangeLY, ...)` | 用户进入超空间范围 → 进 `to` | `InHyperRangeOfEntityChecker` |
| `setStageOnInRangeOfCommRelay(to)` | 用户进入通信中继范围 → 进 `to` | `InCommRelayRangeChecker` |
| `setStageOnCustomCondition(to, checker)` | 自定义条件 → 进 `to` | `ConditionChecker` 任意实现 |
| `connectWithXxx(from, to, ...)` | 带起点的连接 (`from` 明确时只在 `from` 阶段生效)| 同族检测器 |
| `connectWithMarketDecivilized` / `connectWithHostilitiesEnded` / `connectWithHostilitiesStarted` | 市场去文明 / 敌对结束 / 敌对开始 | `MarketDecivChecker` / `HostilitiesEndedChecker` / `HostilitiesStartedChecker` |

**完成检测三通道**：

1. **帧推进轮询**：`advanceImpl` → `tracker.intervalElapsed()` → `checkStageChangesAndTriggers(null, null)`。
   - 检测循环：对 `connections` 逐条检查 "`conn.from == currentStage || conn.from == null` 且 `conn.to != currentStage` 且 `checker.conditionsMet()`"。
   - 命中即 `setCurrentStage(conn.to)` 并**移除该连接**(除非 `setStageTransitionsRepeatable()`)。
   - `while (changed)` 直到无变化，最后 `runTriggers()`。
   - **连接是一次性的**—— 同一条连接只触发一次阶段转换。
2. **对话驱动**：rules 把完成标志写进目标对象内存 (如 `$gearInHub_delivered = true`)。
   - 再 `Call $<missionId>_ref updateStage` 或 `Call $<missionId>_ref updateData`——`BaseHubMission.callEvent` 里 `"updateStage"` → `checkStageChangesAndTriggers(dialog, memoryMap)`。
   - 这就是 "对话里完成目标" 的标准收尾 (原版 `cheapCom` 的 `$cheapCom_completed = true` + `Call $cheapCom_ref updateStage` 同款)。
3. **时限**：`setTimeLimit(failStage, days, noLimitWhileInSystem, ...noLimitAfterStages)` → `advanceImpl` 里 `timeLimit.days < elapsed` 且用户不在豁免星系 → `setCurrentStage(failStage)`。
   - 进入 `endLimitStages` 后时限取消。

::: caution 完成标志写哪里：目标内存必须与规则作用域一致
`setStageOnMemoryFlag(to, X, flag)` 检测的是**对象 X 的内存**(或显式 `MemoryAPI`)，rules 里 `$flag = true` 写的是**当前对话作用域**(人物对话 = 该人物内存、市场对话 = 市场实体内存；作用域细节由规则引擎与内存作用域主题展开)。

两者必须指向同一对象 —— 写错内存时连接永不满足，任务永远卡在阶段 (无报错，只差最后一段)。
:::

**任务与规则的握手 (interaction memory 与 mission ref)**：

- `updateInteractionData`：`interactionMemory = memoryMap.get(MemKeys.LOCAL)`。
  - 写 `$<missionId>_stage`(当前阶段枚举名) 与人物代词/称呼 token(`setPersonTokens`)，再调 `updateInteractionDataImpl()`—— 子类在这里用 `set("$key", value)` 写规则变量 (LOCAL 人物内存，随档持久)。
- **mission ref**：`setGlobalReference(key)` / `setPersonMissionRef(person, key)` / `setFactionMissionRef` / `setMarketMissionRef` / `setEntityMissionRef` 把任务实例自身写进目标内存 (键已被占用时返回 `false`—— 同键任务互斥)。
  - rules 侧 `Call $<key> showMap` / `Call $<key> updateStage` / `Call $<key> repSuccess` 就是打到任务的 `callEvent`。
  - ref 作为 `VariableSet` 登记进 `changes`，任务结束 `abort()` 时从内存移除。

**地图标记与 Intel 展示**：`makeImportant(person/entity/market, flag, Enum... stages)` 按阶段登记地图重要标记 (`ImportanceData`)。

`getMapLocation(map)` 默认返回当前阶段第一个重要实体的位置 (不可发现实体回退星系中心)—— 任务 Intel 的地图定位**不需要覆写**，只要登记了 `makeImportant`。

`addDescriptionForNonEndStage` 与 `addNextStepText` 按当前阶段写 Intel 描述与下一步；`addDescriptionForCurrentStage` 对终态自动输出成功/失败/放弃文案。

单个任务里，阶段连接与触发器组成一条事件链；跨多个任务的大剧情需要第二种形态。本节把两种形态放在一起对照。

### 6. 事件链：触发器链与跨任务全局链

**形态一：单任务触发器链 (`MissionTrigger`)**。创建纪律 (`HubMissionWithTriggers`)：

```java
beginCustomTrigger(new AlwaysTrueChecker(), Stage.RETURN);   // 或 beginGlobalFlagTrigger / beginDaysElapsedTrigger / ...
triggerSetGlobalMemoryValue("$gearInHub_enteredReturn", true); // 任意 trigger* 动作
endTrigger();                                                 // 必须成对
```

- `beginCustomTrigger(checker, Object... stages)` 开一个 `currTrigger`。
  - **`endTrigger()` 把触发器入列**—— 没有 begin 就 end 抛 `RuntimeException("endTrigger() called without a corresponding beginTrigger()")`。
  - 没 end 就开下一个抛 `"Already began a trigger, call endTrigger() to finish it"`。
- 触发器执行 (`runTriggers`)：`trigger.getStages().contains(currentStage)` 且 `condition.conditionsMet()` → 依次执行 `TriggerAction.doAction(context)` → **从列表移除**(一次性)。
  - 调用时机：`accept` 末尾、`setCurrentStage` 末尾、`checkStageChangesAndTriggers` 末尾、时限切换后。
- 动作族 (`trigger*`，全部汇聚到 `triggerCustomAction`)：
  - 生成类：`triggerSpawnEntity` / `triggerSpawnDerelictHull` / `triggerSpawnDebrisField` / `triggerSpawnShipGraveyard` / `triggerSpawnFleetAtPickedLocation`。
  - 舰队类：`triggerCreateFleet` / `triggerSetFleetFaction` / `triggerOrderFleetPatrol` / `triggerOrderFleetInterceptPlayer` / `triggerFleetMakeImportant`。
  - 标志类：`triggerSetGlobalMemoryValue` / `triggerSetMemoryValue` / `triggerSetFleetFlag` / `triggerSetEntityFlag` / `triggerUnset*`。
  - 人物类：`triggerMovePersonToMarket` / `triggerUnhideCommListing` / `triggerSetPersonMissionRef`。
  - 奖励类：`triggerAddCommodityDrop` / `triggerAddWeaponDrop` / `triggerAddHullmodDrop` / `triggerAddSpecialItemDrop` / `triggerAddCustomDrop`。
  - 其他：`triggerMakeDiscoverable` / `triggerAddAbilities` / `triggerSetRemnantConfig` / `triggerFleetNoAutoDespawn`。

**形态二：跨任务全局链 (成熟实例的 `MissionStage` 模式)**。大剧情把 "阶段" 提升为全局状态，每个步骤由独立任务 Intel 承载，步骤结束回写全局阶段：

1. 全局阶段枚举 + 唯一读写入口：管理器 `getStage()` 从全局内存读、`setStage(MissionStage)` 写；规则侧转换命令把字符串转枚举 (写错字符串会把阶段退回 `STAGE_NOT_STARTED`)。
2. 每步一个任务 Intel(成熟实例中每步一个独立任务类)，**各自的结束条件 (时限耗尽/条件失效) 里回写全局阶段**(如 `STAGE_DOOMED`)—— 不回写则后续阶段继续消费旧状态。
3. 入口用酒吧路线的调度 (各阶段 bar creator，`shouldShowAtMarket` 只匹配特定全局阶段)；中间步骤用 rules `Call` 委托创建/推进 Intel。

对照结论：`HubMission` 的内部阶段图是**单任务事件链**(阶段 + 连接 + 触发器，自带回滚与清理)；`MissionStage` 全局链是**跨任务事件链**(全局内存 + 每步 Intel + 回写纪律)。

两者可以嵌套 —— 全局链的一个步骤本身可以是一个 `HubMission`(如成熟实例中挂进 `person_missions.csv` 的 `HubMissionWithBarEvent`)。

机制已经完整，还差最后一步：把任务接进游戏。两条注册路线 —— 酒吧事件与联系人任务板 —— 都在这里。

### 7. 注册入口与使用方完整流程

枢纽任务有两条注册路线 (本节示例走 bar 路线)：

**路线 A：酒吧事件 (bar_events.csv)**—— 酒吧调度器的 mission 分派：

1. `data/campaign/bar_events.csv` 一行 (CSV spec 加载期读取；表头 11 列：`bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin` )：

   ```text
   gearInHub,,10,1,30,40,15,20,60,90,data.scripts.gearin.campaign.GearInHubMission
   ```
2. `BarEventSpec`(`com.fs.starfarer.api.loading.BarEventSpec`)：`plugin` 必填；`isMission()` 判定 plugin 是 `HubMissionWithBarEvent` 子类。
3. `BarEventManager.readResolve()` → `updateBarEventCreatorsFromSpecs()`(调度器为每个无 creator 的 spec 自动建 `SpecBarEventCreator`)。
4. `SpecBarEventCreator.createBarEvent()`：`spec.isMission()` → `new HubMissionBarEventWrapper(specId)`。
   - `BarEventSpec.isMission()` 用 `getInstanceOfScript(pluginClass) instanceof HubMissionWithBarEvent` 判定 ——**plugin 必须是 `HubMissionWithBarEvent` 子类**。
   - `createMission()` 反射实例化并把 `setMissionId(id)` 与 (若存在同名 person mission spec 的) 图标一并设置。
5. `HubMissionBarEventWrapper.shouldShowAtMarket(market)`：按 `seed + market.getId().hashCode()` 掷 `spec.getProb()`；通过则 `spec.createMission()`(反射实例化任务)→ `setMissionId(specId)`(与 `createMission` 幂等重复)：
   - `setGenRandom(genRandom)` → `mission.shouldShowAtMarket(market)`。6. `addPromptAndOption`：`mission.createAndAbortIfFailed(market, true)`；创建失败 (`isMissionCreationAborted()`) 则本次不展示。
   - 否则把 LOCAL 内存切到任务人、`updateInteractionData`、`FireBest` 触发 `<prefix>_blurbBar` 与 `<prefix>_optionBar` 规则。
7. 用户点选后 rules 走 `contact_acceptBar` → **`BarCMD accept $missionId`**(`BarCMD` 的 `accept` 分支)：按 `getBarEventId()` 找到 wrapper → `BarEventManager.notifyWasInteractedWith(w)`(酒吧事件的交互收尾)：
   - `w.getMission().accept(dialog, memoryMap)`—— 进入 "生命周期四段" 的接受段。

**路线 B：联系人任务板 (person_missions.csv + `BaseMissionHub`)**：

1. `data/campaign/person_missions.csv` 一行 (CSV spec 加载期读取；17 列)：
   - `demo_dcm,demo_aegle,,,,,10,10,30,FRIENDLY,,,,,,graphics/icons/missions/dead_drop.png,data.scripts.campaign.missions.DemoMission` (示例行)。
2. `PersonMissionSpec`(`com.fs.starfarer.api.loading.PersonMissionSpec`)：
   - `person id` / `tagsAll` / `tagsAny` / `tagsNotAny` / `freq` / `min-max timeout`
   - `min-max rep` / `importance` / `reqMission*` / `icon` / `plugin`。
   - `createMission()` 反射实例化 `plugin`。
3. 用户与联系人开任务板 (`MissionHubCMD hasHub` → `addMHubOption` → `openMhubOptionSel` → `Call $mHub prepare`)。
   - `BaseMissionHub.prepare` → `updateOfferedMissions`—— 超时/完成过滤 → 按 `getFrequencyWeight` 建 priority/普通 picker → 数量 `MIN_TO_SHOW`–`MAX_TO_SHOW`(settings `contactMinMissions` / `contactMaxMissions`)。
   - `creator.createHubMission(this)` → `setHub` / `setCreator` / `setGenRandom` → `createAndAbortIfFailed(person.getMarket(), false)` → 成功者进 `offered` 并 `updateInteractionData`。
4. `listMissions`：`FireBest.fire(..., getTriggerPrefix() + "_blurb true")` / `"_option true"` 写任务列表选项。
   - 接受：`Call $mHub accept $missionId`(`BaseMissionHub.callEvent` → `accept`：`curr.accept(dialog, memoryMap)` → 从 `offered` 移除 → creator 进 accepted 超时)。

**使用方完整流程**：

- 任务 ID `gearInHub` → `bar_events.csv` 行 (注册入口)→ `SpecBarEventCreator` → `HubMissionBarEventWrapper`(抽取与展示)→ `BarCMD accept`(接受入口)→ `BaseHubMission.accept`。
- `IntelManagerAPI.addIntel`(Intel 使用方)+ `SectorAPI.addScript`(脚本使用方)→ `advanceImpl` / `checkStageChangesAndTriggers`(阶段推进使用方)→ rules `Call $gearInHub_ref updateStage`(完成检测使用方)。
- `endSuccess`(奖励使用方)→ `notifyEnded` → `removeScript`(清理使用方)。

rules 侧的 `$gearInHub_ref` / `$gearInHub_stage` / `gearInHub_blurbBar` / `gearInHub_optionBar` 是同一 ID 的规则使用方。

## 规则强度

以上机制里有几处一旦写错，任务会静默失效或直接报错。以下按强度集中列出本节的关键约束，正文与故障分析按编号引用；编者建议写任务类时把这张表放在手边，每完成一段就对照一次。

**必须/禁止 (当前基线仍有效，违反后的可观察结果附上；出处见文末 "建议参考项")**：

| 规则 | 强度依据 | 违反后的可观察结果 |
|---|---|---|
| 接受前**必须**调用 `setStartingStage(id)` | `accept` 对 null 直接抛 `RuntimeException("startingStage can not be null. Use setStartingStage()")` | 用户接受任务瞬间启动中止 (异常沿规则执行链冒泡，日志 ERROR 堆栈指向 `BaseHubMission.accept`)|
| `create()` 里注册的副作用**必须**能回滚：实体/人物/标志经基类方法 (`spawn*` / `makeImportant` / `setFlag` / `set*MissionRef`) 或显式 `changes.add(...)`，结束时由 `abort()` 统一清理 | `EntityAdded.abort` 只移除带 `REMOVE_ON_MISSION_OVER` tag 的实体 | 创建失败或任务结束时实体/人物/标志残留在星区：重复出现、地图永久标记、内存键不清理 |
| `setStageOnMemoryFlag` / `connectWithMemoryFlag` 的目标内存**必须**与 rules 写标志的作用域一致 | 内存键与作用域语义 (见本节 "完成检测" 节)| 完成标志写到了别处内存：阶段连接永不满足，任务永远卡在当前阶段 (无报错，只差最后一段)|
| `beginXxxTrigger` 与 `endTrigger` **必须**成对 | 两条 RuntimeException 原文："Already began a trigger" / "endTrigger() called without a corresponding beginTrigger()" | 任务创建期抛 RuntimeException；缺 end 的触发器永不入列 (动作静默缺失)|
| 规则侧推进任务**必须**经 `Call $<missionId>_ref updateStage` / `updateData`，不能只写内存标志 | `callEvent` 的 `updateStage` / `updateData` 分支 | 只写 `$flag = true` 不 `updateStage`：`checkStageChangesAndTriggers` 在下一轮轮询 (每 0.09–0.11 游戏天) 仍会检测到，但对话内不立即推进、Intel 不更新 —— 表现为 "对话里说完成了，界面/日志下一帧才变"；依赖即时性的业务 (如立即结算) 会错乱 |
| 任务实例字段**必须**可随存档序列化 (任务随 `scripts` 列表 XStream 保存的结论见前置知识)；对话句柄等瞬态引用用 `transient` | `BaseHubMission` 已把 `startingStage` / `cargoOnAccept` / `aborted` / `interactionMemory` / `doNotEndMission` 标 `transient` | 保存/读档异常、读档后任务字段指向失效对象、行为错乱 |
| mission ref 键**必须** MOD 专属且唯一 | `set*MissionRef` 对已占用键返回 false | 与其他 MOD 撞键：任务创建被拒 (`create` 返回 false → 任务不出现)；同 MOD 两个任务共用一键则后建者失败 |
| bar 路线任务类**必须**继承 `HubMissionWithBarEvent` | `BarEventSpec.isMission()` 按此分派 | plugin 类型错位：wrapper 不创建、任务被当普通 `PortsideBarEvent` 反射实例化 —— 构造/类型错误，酒吧选项不出现或报错 (酒吧事件陷阱 "plugin 类型错位" 同源)|
| 完成/失败/放弃路径**必须**落在 `successStages` / `failStages` / `abandonStage` 集合内 | `setCurrentStage` 只对这些集合结算 | 阶段切到集合外对象：任务不结束、只发 NEXT_STEP_UPDATE，永久 "进行中" |
| 时限失效**必须**有对应失败阶段 (`setTimeLimit(failStage, ...)` 的第一参)| 时限分支的切换目标语义 | `advanceImpl` 时限分支切到一个非失败阶段：任务 "卡死" 且每次帧推进都重复切 |

**建议 (违反的可观察结果附上)**：

- 任务 ID 全局唯一、带 MOD 前缀 (`gearInHub`)；内存键族与规则分类随之唯一 (`$gearInHub_*` / `gearInHub_blurbBar`...)。撞名 → 与别的 MOD 规则分类/内存键互相覆盖 (症状同键冲突主题的既有结论)。
- 奖励与声望用方法族：`setCreditReward` / `setCreditReward(CreditReward)` / `setXPReward` / `setRepPersonChangesXxx` / `setRepFactionChangesXxx` / `setRepChanges(...)` / `setNoRepChanges()`。
  - 直接调 `adjustRep` 会绕过 `adjustedRep` 一次性保护，且与 `repSuccess`/`repFailure` 规则动作重复结算。
- 目标选择先 `require*` 后 `pick*`(`HubMissionWithSearch` 的要求系统)，不要在 `create` 里手写遍历。手写 → 复用不了市场/星系筛选与 `preferMarketInDirectionOfOtherMissions` 等成品能力，且 `genRandom` 一致性被破坏。
- 每阶段至多一个 `makeImportant` 主要目标 (`makePrimaryObjective`)，不要给一个阶段塞多个重要标记 ——Intel 地图定位只取第一个，其余标记只增加噪声。

## 完整最小例

机制、约束与注册入口都齐了，现在把它们落成一个可以编译、可以进游戏验收的任务类。

在既有 MOD 布局与 `GearInModPlugin` 之上新增一个任务类与两行数据 (`bar_events.csv` 行 + `rules.csv` 片段；数据行见本节 "注册入口与使用方完整流程" 与下方规则片段)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       ├── bar_events.csv    # 追加一行（见"注册入口与消费者闭环"节）
│       └── rules.csv         # 追加规则片段（见下方）
├── src\
│   └── data\scripts\gearin\campaign\
│       └── GearInHubMission   # 本节新增（归本节所有）
└── jars\
```

### GearInHubMission

```java
package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：枢纽任务（HubMission）——Intel、阶段、目标与事件链。
 *
 * 任务 ID：gearInHub（bar_events.csv 的 mission id 列 = 本类在 CSV 的 spec id；
 * 该 ID 的消费者：HubMissionBarEventWrapper.setMissionId、规则分类前缀
 * gearInHub_blurbBar / gearInHub_optionBar、交互内存键 $gearInHub_stage、
 * mission ref 键 $gearInHub_ref）。本节所有权，不含工作单元编号变体。
 *
 * 注册入口：data/campaign/bar_events.csv 一行（plugin 列 = 本类全名）+ rules.csv
 * 六条规则（blurbBar / optionBar / startBar / 任务文本 / 数据收讫 / 交付结算），见页面正文。
 * 展示链路：BarEventManager -> SpecBarEventCreator（isMission 判定）->
 * HubMissionBarEventWrapper -> shouldShowAtMarket / createAndAbortIfFailed(true)
 * -> updateInteractionData -> FireBest gearInHub_blurbBar / gearInHub_optionBar。
 * 接受链路：rules 的 contact_acceptBar -> BarCMD accept gearInHub ->
 * wrapper.getMission().accept() -> addIntel + addScript + setCurrentStage。
 *
 * 演示点：
 * 1. Intel：本类经 BaseHubMission 继承 BaseIntelPlugin——accept 时 addIntel
 *    （Intel 界面可见）+ addScript（每帧推进），结束时 endAfterDelay(3 游戏日)
 *    -> notifyEnded -> removeScript；
 * 2. 阶段：Stage 枚举四态（CONTACT -> RETURN -> COMPLETED / FAILED），
 *    setCurrentStage 切阶段时登记/解除 makeImportant 地图标记并发送下一步更新；
 * 3. 目标完成检测三通道：对话驱动（rules 写 $gearInHub_dataReceived /
 *    $gearInHub_delivered + Call $gearInHub_ref updateStage，走 MemoryBooleanChecker
 *    连接）、帧推进轮询（tracker 每 0.09-0.11 游戏天查连接）、时限
 *    （setTimeLimit(Stage.FAILED, 60, null) 到期切失败）；
 * 4. 事件链：两个一次性触发器 —— 进入 RETURN 阶段时写全局内存标记
 *    （AlwaysTrueChecker），任务进行满 30 游戏天时写半程标记（DaysElapsedChecker）；
 * 5. 奖励：信用点（按距离与质量折算）与双声望（人物 MEDIUM / 势力 LOW）。
 */
public class GearInHubMission extends HubMissionWithBarEvent {

    public static final float MISSION_DAYS = 60f;

    /** 阶段图：CONTACT（去目标市场找联系人）-> RETURN（回任务人处交货）-> 终态。 */
    public enum Stage {
        CONTACT,
        RETURN,
        COMPLETED,
        FAILED,
    }

    private static final Logger LOG = Global.getLogger(GearInHubMission.class);

    private PersonAPI giver;
    private MarketAPI sourceMarket;
    private MarketAPI targetMarket;
    private PersonAPI sourceContact;
    private int reward = 0;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        // 酒吧路线：create() 之前被调用（HubMissionBarEventWrapper.shouldShowAtMarket）。
        // 本示例任何有太空港的市场都可出现。
        return market.hasSpaceport();
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            // 酒吧路线需要自己生成给任务者（联系人路线由 hub 的 getPerson() 提供）。
            setGiverRank(Ranks.CITIZEN);
            setGiverPost(Ranks.POST_TRADER);
            setGiverImportance(pickImportance());
            setGiverFaction(Factions.INDEPENDENT);
            setGiverTags(Tags.CONTACT_TRADE);
            findOrCreateGiver(createdAt, true, false);
        }

        giver = getPerson();
        if (giver == null) return false;
        sourceMarket = giver.getMarket();
        if (sourceMarket == null) return false;

        // mission ref：rules 侧 Call $gearInHub_ref <action> 打到本任务的 callEvent。
        // 键已被其他任务占用时返回 false——create 失败、本次不提供该任务。
        if (!setPersonMissionRef(giver, "$gearInHub_ref")) {
            return false;
        }

        // ---- 目标选择：HubMissionWithSearch 的要求系统 ----
        requireMarketIsNot(sourceMarket);
        requireMarketFaction(sourceMarket.getFactionId());
        requireMarketFactionNotHostileTo(Factions.PLAYER);
        preferMarketInDirectionOfOtherMissions();
        targetMarket = pickMarket();
        if (targetMarket == null) return false;

        sourceContact = findOrCreateTrader(targetMarket.getFactionId(), targetMarket, true);
        if (sourceContact == null || !setPersonMissionRef(sourceContact, "$gearInHub_ref")) {
            return false;
        }

        // ---- 阶段图 ----
        setStartingStage(Stage.CONTACT);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // ---- 目标完成检测：内存标志连接（对话驱动，rules 置位 + Call updateStage）----
        setStageOnMemoryFlag(Stage.RETURN, sourceContact, "$gearInHub_dataReceived");
        setStageOnMemoryFlag(Stage.COMPLETED, giver, "$gearInHub_delivered");

        // ---- 目标时限：60 游戏天超时进 FAILED ----
        setTimeLimit(Stage.FAILED, MISSION_DAYS, null);

        // ---- 地图目标标记：进入对应阶段时点亮 ----
        makeImportant(sourceContact, "$gearInHub_hasData", Stage.CONTACT);
        makeImportant(giver, "$gearInHub_returnMark", Stage.RETURN);

        // ---- 事件链：一次性触发器 ----
        // 进入 RETURN 阶段即写全局标记（AlwaysTrueChecker 恒真，进阶段即触发）。
        beginCustomTrigger(new AlwaysTrueChecker(), Stage.RETURN);
        triggerSetGlobalMemoryValue("$gearInHub_enteredReturn", true);
        endTrigger();
        // 任务进行满 30 游戏天（无论停在哪个阶段）写半程标记。
        // 注意：beginDaysElapsedTrigger(float, Object...) 与 (float, Object, Object...)
        // 两个重载在三个实参时会歧义，这里直接用它内部构造的 DaysElapsedChecker。
        beginCustomTrigger(new DaysElapsedChecker(MISSION_DAYS / 2f, this), Stage.CONTACT, Stage.RETURN);
        triggerSetGlobalMemoryValue("$gearInHub_halfTime", true);
        endTrigger();

        // ---- 奖励与声望 ----
        reward = getRoundNumber(30000f + getDistanceLY(targetMarket) * 3000f + getQuality() * 20000f);
        creditReward = reward;
        setRepPersonChangesMedium();
        setRepFactionChangesLow();

        LOG.info(String.format("GearIn hub mission: created at %s, target %s, reward %d",
                createdAt.getName(), targetMarket.getName(), reward));
        return true;
    }

    @Override
    protected void updateInteractionDataImpl() {
        // 写规则变量（LOCAL 人物内存，随档持久；$gearInHub_stage 由基类自动写）。
        set("$gearInHub_reward", Misc.getWithDGS(reward));
        set("$gearInHub_targetMarketName", targetMarket.getName());
        set("$gearInHub_dist", getDistanceLY(targetMarket));
        set("$gearInHub_contactName", sourceContact.getNameString());
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        LOG.info("GearIn hub mission: accepted, currentStage=" + getCurrentStage());
    }

    @Override
    public String getBaseName() {
        return "Lost Shipment Data";
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("A shipment of factory data went missing while a convoy passed through %s. "
                    + "Talk to %s there to retrieve the data cache.",
                    opad, h, targetMarket.getName(), sourceContact.getNameString());
        } else if (currentStage == Stage.RETURN) {
            info.addPara("Return to %s and hand the recovered data over to %s.",
                    opad, h, sourceMarket.getName(), giver.getNameString());
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("Go to %s and talk to %s",
                    pad, tc, h, targetMarket.getName(), sourceContact.getNameString());
            return true;
        }
        if (currentStage == Stage.RETURN) {
            info.addPara("Return to %s and talk to %s",
                    pad, tc, h, sourceMarket.getName(), giver.getNameString());
            return true;
        }
        return false;
    }
}
```

对应关系：

- `extends HubMissionWithBarEvent`：任务约定的完整默认实现链 (`BaseHubMission` → `HubMissionWithTriggers` → `HubMissionWithSearch` → `HubMissionWithBarEvent`)。
  - `shouldShowAtMarket` 在 `create()` **之前**被 wrapper 调用 (`HubMissionBarEventWrapper.shouldShowAtMarket`)，返回 false 本次不展示。
- `create(createdAt, true)`：bar 路线用 `setGiverRank/Post/Importance/Faction/Tags` + `findOrCreateGiver(createdAt, true, false)` 生成任务人 (`addToCommDirectory=true`：任务人留在通讯录，交付对话可达；原版 extr 任务同款)。
  - `getPerson()` / `getMarket()` 判空、`setPersonMissionRef` 判占用 —— 任何一步失败返回 false 即 `abort()`。
  - `requireMarketIsNot(sourceMarket)` / `requireMarketFaction(...)` / `requireMarketFactionNotHostileTo(Factions.PLAYER)` / `preferMarketInDirectionOfOtherMissions()` / `pickMarket()`
  - 目标选择要求系统 (`HubMissionWithSearch`)。
  - 与同一联系人同时存在的其他任务会互相避开方向 (`preferMarketInDirectionOfOtherMissions`)。
- `findOrCreateTrader(faction, market, true)`：目标市场生成/复用联系人 (`cleanUpOnMissionOverIfWasNewPerson=true` 时新人物随任务结束清理)。
- `setStageOnMemoryFlag(Stage.RETURN, sourceContact, "$gearInHub_dataReceived")`：完成检测连接 ——rules 在联系人对话里写 `$gearInHub_dataReceived = true`(写进联系人内存) 再 `Call $gearInHub_ref updateStage`，连接命中切到 RETURN。
  - **目标内存与规则作用域必须一致**(规则强度 "必须" 第 3 条)。
- `setTimeLimit(Stage.FAILED, MISSION_DAYS, null)`：时限；`advanceImpl` 按任务总 `elapsed`(游戏天) 判定，超时切 FAILED。
- `makeImportant(sourceContact, "$gearInHub_hasData", Stage.CONTACT)`：CONTACT 阶段联系人市场点亮地图标记；`getMapLocation` 用默认实现 (当前阶段第一个重要实体)。
- `makeImportant(giver, "$gearInHub_returnMark", Stage.RETURN)`：RETURN 阶段任务人市场点亮地图标记；标志与完成连接监听的 `$gearInHub_delivered` 分开（进入 RETURN 时引擎写阶段标志，若同名会立即命中完成连接，交付对话不会发生）。
- `beginCustomTrigger(new AlwaysTrueChecker(), Stage.RETURN)` + `triggerSetGlobalMemoryValue(...)` + `endTrigger()`：一次性触发器 (进 RETURN 即写全局内存 `$gearInHub_enteredReturn`)。
    - 半程触发器用 `beginCustomTrigger(new DaysElapsedChecker(MISSION_DAYS / 2f, this), ...)`；
    - `DaysElapsedChecker(days, mission)` 判 `mission.elapsed >= days`(与 `beginDaysElapsedTrigger(days, this)` 同语义)，避开该 helper 双重载在三个实参下的编译歧义。
  - **必须成对 endTrigger**。
- `creditReward = reward`：直接写受保护字段 (`getCreditsReward` 返回它)，绕过 `setCreditReward` 的 `rewardMult` 与千位取整，日志与发放一致；声望走 `setRepPersonChangesMedium` / `setRepFactionChangesLow`。
- `updateInteractionDataImpl`：`set(...)` 写 `$gearInHub_*` 规则变量 (LOCAL 人物内存，随档持久)；`$gearInHub_stage` 由基类 `updateInteractionData` 自动写。
- `addDescriptionForNonEndStage` / `addNextStepText`：Intel 界面的阶段描述与下一步 (终态文案由 `addDescriptionForCurrentStage` 自动处理)。

### bar_events.csv 行与 rules.csv 片段

`data/campaign/bar_events.csv` 追加一行 (列序与表头一致，见 "注册入口与使用方完整流程")：

```text
gearInHub,,10,1,30,40,15,20,60,90,data.scripts.gearin.campaign.GearInHubMission
```

`data/campaign/rules.csv` 追加 (规则引擎完整语法与内存作用域不在此展开；这里按酒吧事件路由的最简流程写法给出)：

```text
gearInHubBlurbBar,gearInHub_blurbBar,,,"An independent trader at the bar is complaining loudly about a shipment of factory data that went missing in transit.",,
gearInHubOfferOptionBar,gearInHub_optionBar,,,,gearInHub_startBar:Ask about the missing shipment,
gearInHubOfferTextBar,DialogOptionSelected,"$option == gearInHub_startBar","$missionId = gearInHub
FireBest GearInHubOfferText",,"contact_acceptBar:Accept
contact_declineBarNoContinue:Decline",
gearInHubTextBar,GearInHubOfferText,,"SetTextHighlights $gearInHub_reward $gearInHub_targetMarketName
Call $gearInHub_ref showMap ""Destination: $gearInHub_targetMarketName""","The trader leans in: ""The data was lost when the convoy jumped through $gearInHub_targetMarketName. My contact there - $gearInHub_contactName - can get it back. I'll pay $gearInHub_reward credits if you deliver it to me.""",,
gearInHubDataReceivedGreeting,PickGreeting,"$gearInHub_hasData score:1000
Call $gearInHub_ref updateData","ShowPersonVisual
$gearInHub_dataReceived = true
Call $gearInHub_ref updateStage","So you're the trader's courier. Here - the factory data cache, intact. It's all there.",contact_pickAnotherGreeting:Continue,
gearInHubDeliveryGreeting,PickGreeting,"$gearInHub_returnMark score:1000
Call $gearInHub_ref updateData","ShowPersonVisual
$gearInHub_delivered = true
Call $gearInHub_ref updateStage","The trader takes the datapad and runs a quick check: ""That's everything. Here's your payment - $gearInHub_reward credits.""",contact_pickAnotherGreeting:Continue,
```

交付收尾两条规则沿用原版同类任务的 `PickGreeting` 模式（原版 `cheapCom` 取货与 `extr` / `jabr` 回任务人同款）：前往目标市场与联系人 `$gearInHub_contactName` 通讯时命中 `gearInHubDataReceivedGreeting`（条件 `$gearInHub_hasData`——CONTACT 阶段 `makeImportant` 写在联系人内存的阶段标志），脚本把 `$gearInHub_dataReceived = true` 写进联系人内存（与 `setStageOnMemoryFlag(Stage.RETURN, sourceContact, ...)` 作用域一致）并 `Call $gearInHub_ref updateStage`，阶段切到 RETURN；回来源市场与任务人通讯时命中 `gearInHubDeliveryGreeting`（条件 `$gearInHub_returnMark`——RETURN 阶段地图标记标志，进入 RETURN 时由 `makeImportant` 写入；脚本把 `$gearInHub_delivered = true` 写进任务人内存——完成连接监听的标志，与阶段标志分开），并 `Call $gearInHub_ref updateStage`，连接 RETURN→COMPLETED 命中并结算。两条规则对应验证方案步骤 3/4；`score:1000` 让规则胜过原版通用问候，`contact_pickAnotherGreeting:Continue` 是原版问候回环选项（选后回到通讯问候流程）。

::: warning 规则分类必须与 mission id 一致
规则 ID 用驼峰 (`gearInHubBlurbBar`)，规则**分类**是 `gearInHub_blurbBar` / `gearInHub_optionBar`。

分类由 `getTriggerPrefix()` = mission id 决定，必须与 CSV 的 mission id 列一致。

分类拼错时 `FireBest` 找不到规则，酒吧选项静默缺失 (无报错、无日志)。
:::

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInHubMission.class`(可用 `jar tf` 核对)。

## 验证方案

本节成果分三层验收：先构建示例 MOD，再进游戏走完任务链，最后用注入用例复现故障分析的结论。运行验证需在 Starsector 环境中执行：以下步骤与预期结果已完整保留，实际表现以游戏内为准。

### 构建与游戏验证

- 入口：Git Bash 命令行。
- 前置数据：既有 MOD 布局与 `GearInModPlugin`(C06-S01 / C06-S02)、本节 `GearInHubMission`、`bar_events.csv` 行、`rules.csv` 片段。
- 操作步骤：
  1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`；
  2. 执行 `jar tf jars\GearIndustry.jar` 核对类文件。
- 观察位置：命令行输出与 jar 内容列表。
- 预期结果：
  - `gradle jar` 以 `BUILD SUCCESSFUL` 结束，`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInHubMission.class`。

### 酒吧任务完整流程

- 入口：生涯游戏，任一有太空港市场的酒吧。
- 前置数据：既有 MOD 布局与 `GearInModPlugin`、本节任务类与两行数据 (同上)。
- 操作步骤：
  1. 跳过/完成教程后进入酒吧，核对日志与酒吧提示/选项 (事件不是每次都在，受酒吧调度影响，可多进几个市场或等待)；
  2. 点 "Ask about the missing shipment"，核对文本后选 "Accept"；
  3. 前往目标市场与联系人 `$gearInHub_contactName` 对话并走交付规则；
  4. 回任务人市场与其对话并走收尾规则；
  5. 接受后挂机 600 真实秒，观察失败路径；
  6. 接受后保存并读档，任务完成后再次读档核对清理。
- 观察位置：`starsector.log`、通信面板、Intel 界面、地图标记、全局内存 (控制台)。
- 预期结果：
  - 日志出现 `GearIn hub mission: created at <市场名>, target <目标市场名>, reward <金额>`；酒吧面板出现 "An independent trader at the bar is complaining loudly..." 与选项 "Ask about the missing shipment"(抽取受酒吧调度的权重/时长影响，不是每次进酒吧都在；同一时间每个任务 ID 至多一个活跃实例)。
  - 点 "Ask about the missing shipment" 后文本显示目标市场与报酬 (`$gearInHub_reward` / `$gearInHub_targetMarketName` 已由 `updateInteractionData` 写入)；选 "Accept" 后日志出现 `GearIn hub mission: accepted, currentStage=CONTACT`，通信面板出现新消息 (`addIntel` 默认 `autoAddCampaignMessage`)，Intel 界面出现任务条目 "Lost Shipment Data"，地图上目标市场出现任务标记 (`makeImportant(sourceContact, ..., Stage.CONTACT)`)。
  - 交付规则写 `$gearInHub_dataReceived = true` 并 `Call $gearInHub_ref updateStage` 后阶段切到 RETURN：Intel 更新为 "Return to <来源市场>..."，地图标记切到任务人市场 (`makeImportant(giver, ..., Stage.RETURN)`)，全局内存 `$gearInHub_enteredReturn` 变为 true(`AlwaysTrueChecker` 触发器已执行)；RETURN 阶段停留，直到玩家回来源市场触发交付规则。
  - 交付规则写 `$gearInHub_delivered = true` + `Call $gearInHub_ref updateStage` 后连接 RETURN→COMPLETED 命中：信用点增加 `$gearInHub_reward`、人物与势力声望上调、Intel 显示成功结算文案并保留约 3 游戏日后消失 (`getBaseDaysAfterEnd`)、`starsector.log` 无 `gear_in` 引起的 ERROR。
  - 接受后 60 游戏天 (600 真实秒) 内未完成：Intel 显示失败文案、人物声望下调 (`setRepPersonChangesMedium` 的失败侧)、约 3 游戏日后消失；`$gearInHub_halfTime` 在任务进行满 30 游戏天时被写 true(半程触发器)。
  - 读档后 Intel 条目仍在、阶段/`elapsed` 从存档继续 (`StageData.elapsed` 是普通字段)；`$gearInHub_*` 写在人物内存 (随档持久)，任务结束由 `abort()` 统一清理；联系人随任务结束清理 (REMOVE_ON_MISSION_OVER tag)，任务人保留在市场 (与 vanilla cheapCom 一致)，标志与地图标记由 `abort()` 统一清理。

### 故障注入与参数演示

- 入口：Git Bash 命令行 + 生涯游戏。
- 前置数据：同上，每次改动后重建。
- 操作步骤：
  1. 注释掉 `setStartingStage(Stage.CONTACT)` 调用，重建后接受任务并核对异常；改回后确认恢复；
  2. 把 `setStageOnMemoryFlag(Stage.COMPLETED, giver, "$gearInHub_delivered")` 的目标改成 `sourceContact`，交付后核对阶段；改回后确认恢复；
  3. 删掉 `endTrigger()` 后重建，核对创建期异常；改回后确认恢复；
  4. 把 `MISSION_DAYS` 改成 10 重建，接受后挂机核对失败路径；改回后确认恢复。
- 观察位置：`starsector.log`、对话流程、Intel 状态。
- 预期结果：
  - 注入 1 对应故障分析 2：接受任务时抛 `RuntimeException("startingStage can not be null...")`。
  - 注入 2 对应故障分析 3：交付后阶段不推进到 COMPLETED、任务卡在 RETURN。
  - 注入 3 对应故障分析 5：创建期抛 "Already began a trigger..."。
  - 参数演示：`MISSION_DAYS = 10` 时接受后 100 真实秒内未完成即失败 (高频参数修改验证最小任务链第 4 步)。

::: tip 高频调试技巧
把 `MISSION_DAYS` 从 60 改成 10 重建，失败路径压缩到约 100 真实秒内即可复现 (对应上文参数演示)；`starsector.log` 里 `GearIn hub mission` 前缀的日志覆盖创建与接受两个阶段，是核对任务走完 `create` → `accept` 的快捷观察点。
:::

通过信号：三组方案的预期结果全部出现；注入用例恢复后回到正常路径；全程无 `gear_in` 引起的 `ERROR`。

清理：取消勾选测试 MOD

## 故障分析

以下故障围绕您能看到的症状组织，编号与验证方案的注入用例以及规则强度表的条目对应。

1. **酒吧里任务从不出现，且没有任何日志。**
   - 触发与原因：`bar_events.csv` 行没加或 plugin 列类名拼错时，`SpecBarEventCreator` 找不到 spec，不会生成 creator；`rules.csv` 分类与 mission id 不一致时，规则分类 `gearInHub_blurbBar` 不命中 (`FireBest` 无输出、选项缺失)。两种情况都表现为无 `GearIn hub mission` 日志、无报错。
   - 修正：核对 CSV 行的 11 列与 plugin 全名 (`data.scripts.gearin.campaign.GearInHubMission`)，并确认规则分类与 `getTriggerPrefix()` = `gearInHub` 一致 (与酒吧事件的同类陷阱同源)。修正后重新进酒吧，任务应出现。
2. **接受任务瞬间启动中止 (NPE/异常)，堆栈指向 `BaseHubMission.accept`。**
   - 触发与原因：`create()` 里忘了 `setStartingStage(...)`，`accept` 对 null 起点直接抛 `RuntimeException("startingStage can not be null. Use setStartingStage()")`，异常沿规则执行链冒泡。
   - 修正：`create()` 成功分支必须 `setStartingStage`(本节示例第 2 个演示点)；补上后重新接受任务确认正常。
3. **任务卡在某个阶段，对话里已经提示完成但阶段不推进。**
   - 触发与原因：`setStageOnMemoryFlag` 的目标对象与 rules 写标志的作用域不一致 (如连接挂在 `giver` 内存、规则却在实体内存写 `$gearInHub_delivered`)；或规则只写标志没 `Call $gearInHub_ref updateStage`。前一种情况 `checkStageChangesAndTriggers` 判 `conditionsMet()` 恒为 false，帧轮询通道每 0.09–0.11 游戏天重试也不中；后一种情况对话内不立即推进，帧轮询通道会补查但有延迟。
   - 修正：核对连接目标与规则作用域指向同一对象，并补 `Call $gearInHub_ref updateStage`(规则强度 "必须" 第 3、5 条)；修正后重走交付对话，阶段应切到 RETURN。
4. **任务完成后实体/人物/地图标记残留。**
   - 触发与原因：`create()` 里手写 `addEntity` / `setFlag` / 自建人物，没走基类方法也没登记 `changes`。任务结束时 `abort()` 只清理 `changes` 里的 `Abortable`，目标实体还留在星区、`makeImportant` 标记不灭、`$gearInHub_*` 内存键残留。
   - 修正：生成用 `spawn*` / `findOrCreateTrader(..., cleanUpOnMissionOverIfWasNewPerson=true)`、标记用 `makeImportant` / `setFlag`、ref 用 `set*MissionRef`—— 全部自动登记 `changes`；修正后完成一次任务并读档确认无残留。
5. **`RuntimeException("Already began a trigger...")` 或触发器静默不执行。**
   - 触发与原因：`beginXxxTrigger` 与 `endTrigger` 不成对；或把触发器动作写在 `begin` 之前 / `end` 之后。前者创建期抛异常；后者触发器永不入列，动作静默缺失 (如 `$gearInHub_enteredReturn` 永远不写)。
   - 修正：`begin*Trigger` → `trigger*` 动作 → `endTrigger` 严格成对 (规则强度 "必须" 第 4 条)；补上后重建确认异常消失。
6. **任务 "永不结束"：阶段切到 COMPLETED/FAILED 但什么都不发生。**
   - 触发与原因：`setSuccessStage` / `setFailureStage` 没调用，或阶段 id 传了与连接目标不同的对象 (如 String "COMPLETED" vs 枚举 `Stage.COMPLETED`)。`setCurrentStage` 对集合外阶段只发 `NEXT_STEP_UPDATE`，无奖励、无结算文案、任务永久进行中。
   - 修正：成功/失败/放弃阶段必须登记进对应集合，阶段 id 全程用同一枚举；修正后重走收尾对话确认结算。
7. **时限到期任务卡死而非失败。**
   - 触发与原因：`setTimeLimit(Stage.FAILED, ...)` 的失败阶段不在 `failStages` 里，或传了 `null`。`advanceImpl` 时限分支 `setCurrentStage(null)` 或切到非失败阶段，结束判定不命中，且 `timeLimit` 已置空不再重试。
   - 修正：时限目标必须是已登记的失败阶段 (规则强度 "必须" 第 10 条)；修正后挂机到时限，任务应进入失败。
8. **完成奖励与日志/提示不符 (多给/少给)。**
   - 触发与原因：用 `setCreditReward(min, max)` 但日志打印的是 `min`(实际发放受 `rewardMult` 与千位取整影响)；或 `repSuccess` 规则与 `endSuccess` 里的 `adjustRep` 双份结算。表现为信用点数与 Intel 文案不一致、声望一次任务涨两次。
   - 修正：本节示例直接写 `creditReward` 字段保证一致；声望结算只走 `endSuccess` 的 `adjustRep`(`adjustedRep` 一次性保护)，规则侧 `repSuccess` 只用于对话内的即时反馈。
9. **同一任务 ID 同时出现两个活跃任务 (重复接取)。**
   - 触发与原因：bar 路线的事件在 active 跟踪器里存活期内再次被抽中 (酒吧调度按 `getBarEventId()` 去重只对同一 creator 生效)；或 mission ref 键漏判 —— `setPersonMissionRef` 对已占用键返回 false 才挡住同键任务。表现为 `$gearInHub_ref` 被第二个任务覆盖 (第一个任务失去规则入口) 或两个任务并存。
   - 修正：保持 ref 键唯一判断 (`setPersonMissionRef` 返回 false 即 `create` 失败)；任务与任务的互斥靠 ref 键而不是靠运气。
10. **读档后任务行为错乱或存档损坏。**
   - 触发与原因：任务字段放了不可序列化对象 (对话句柄、`PersonAPI` 派生瞬态引用)。任务随 `scripts` 列表随档保存，读档后 `giver` / `targetMarket` 指向失效对象，或保存/读档直接异常。
   - 修正：只保留可序列化字段；瞬态句柄用 `transient` + 需要时重建 (`BaseHubMission` 已示范)。
11. **联系人路线 (person_missions.csv) 任务列表里没有该任务。**
   - 触发与原因：`person id` 或 `tags` 不匹配任何联系人、声望低于 `min rep`(`FRIENDLY`)、`importance` 高于联系人、`reqMission*` 未满足。`BaseMissionHub.updateOfferedMissions` 的过滤静默跳过，任务板无选项、无日志、无报错。
   - 修正：对照 `PersonMissionSpec` 字段与目标联系人 (成熟实例的 CSV 行写法)。
12. **任务 Intel 在地图上定位错误 (指向星系中心或消失)。**
   - 触发与原因：`makeImportant` 的实体不可发现或未登记 `ImportanceData.locType`；或一个阶段登记了多个重要实体。`getMapLocation(map)` 只取当前阶段第一个重要实体，不可发现实体回退星系中心；标记消失常因实体已死亡 (`!entity.isAlive()` 跳过)。
   - 修正：每阶段一个主要目标 (`makePrimaryObjective` 调整顺序)；确需隐蔽目标时接受 "星系中心" 定位或自行覆写 `getMapLocation(map, currentStage)`。

## 总结

本节把枢纽任务系统串成一条从注册到结算的完整链：`bar_events.csv` 一行把任务交到酒吧调度器手里，`accept` 一次完成起点阶段、Intel 登记与脚本登记，随后 `advanceImpl` 按游戏天推进，`setCurrentStage` 命中终态集合时结算奖励，最后由 `endAfterDelay` 走到 `notifyEnded` 收尾。Intel 条目与脚本半身都来自 `BaseIntelPlugin` 这一侧，阶段图、完成检测与触发器属于任务自己。

排错时先问三个问题：任务有没有出现，任务有没有被接受，任务卡在哪个阶段。没有出现查注册行与规则分类；一接受就报错查 `setStartingStage`；卡阶段查连接目标与规则作用域是否指向同一对象、`updateStage` 是否被 `Call`。完成标志写进哪块内存，决定三条检测通道里哪一条会命中 —— 先写内存，再 `Call $<missionId>_ref updateStage`，这是本节最值得记住的握手。

从逻辑上看，rules 的语法与内存作用域规则是这条链的自然下游；事件链的两种形态 (单任务触发器链 / 跨任务全局链) 则为任务接入更宏观的剧情推进提供了位置。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMission`：任务约定接口 (11 个方法)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`：任务基类 (生命周期 / 阶段 / 完成检测 / 奖励与生成辅助)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers`：触发器 API(`begin*Trigger` / `trigger*` 动作族 / `endTrigger`)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch`：目标选择要求系统 (`require*` / `prefer*` / `pick*`)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：酒吧入口补充 (`shouldShowAtMarket`)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.MissionHub` / `BaseMissionHub`：联系人任务板 (路线 B)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionCreator` / `BaseHubMissionCreator`：任务创建器 (频率 / 声望过滤 / 计数)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：酒吧包装 (路线 A 的抽取与展示)。
- `com.fs.starfarer.api.impl.campaign.missions.hub.MissionTrigger`：触发器数据类。
- `com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission`：原版酒吧任务实例 (对照对象)。
- `com.fs.starfarer.api.campaign.comm.IntelInfoPlugin` / `IntelManagerAPI`：Intel 约定与登记表。
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：Intel 条目 + 每帧脚本组合基类。
- `com.fs.starfarer.api.loading.BarEventSpec` / `PersonMissionSpec`：两条注册路线的规格类。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：酒吧事件创建器 (mission 分派)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD` / `MissionHubCMD`：接受任务的规则命令。
- `com.fs.starfarer.api.mission.MissionDefinitionAPI`：战役任务 API(对照项，与枢纽任务无关)。
- `com.fs.starfarer.api.impl.campaign.ids.Factions` / `Ranks` / `Tags`：示例用到的 id 常量。
- `com.fs.starfarer.api.util.Misc`：`makeImportant` / `getWithDGS` / `getDGSCredits` 等工具。
- `data/campaign/bar_events.csv`：酒吧事件注册 (本节任务一行)。
- `data/campaign/person_missions.csv`：联系人任务板注册 (路线 B)。
- `data/campaign/rules.csv`：本节六条任务规则。
- `data/config/settings.json`：任务板数量配置 (`contactMinMissions` / `contactMaxMissions` 等)。
