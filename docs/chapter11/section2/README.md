# 管理舰队中的成员

舰队已经出现在地图上，接下来查看它由哪些成员组成，以及集合修改怎样进入旗舰、后勤汇总和任务。读取玩家舰队时，可以直接对照舰队界面；写入实验则使用上一节新生成的巡逻队，保存原状态、加入临时成员，再完整恢复。集合副本、真实写入与地图任务分别有自己的入口。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 区分成员集合副本和舰队内部集合；
- 添加、移除成员并恢复旗舰与舰长；
- 读取优先顺序、军官关联和舰队汇总；
- 安排两步任务并观察当前任务与运动状态；
- 根据对象、同步和日志定位集合问题。

## 前置知识

- [C11-S1] 的生成舰队、来源市场与投放锚点；
- [C10-S1] 的生涯脚本、时间和日志；
- Java 集合、对象身份、匿名接口实现与 `try`/`finally`。

## 先选择读取与写入对象

`CampaignFleetAPI` 表示地图上的舰队，`FleetDataAPI` 保存这支舰队的成员、旗舰与军官。先取得容器，局部读取如下：

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetDataAPI data = fleet.getFleetData();
LOG.info("GearIn members: count=" + data.getNumMembers()
        + " flagship=" + (fleet.getFlagship() == null
                ? "none"
                : fleet.getFlagship().getId()));
```

`getFleetData()` 返回同一支舰队的容器，当前没有复制舰队。**列表副本用于观察，集合方法用于实际修改。** 船名和装配 ID 都可能重复，追踪同一个成员时使用对象引用或成员 ID。

本节的写入函数放入 [C11-S1] 的 `GearInFleetGenerator`。添加后文使用的 `FleetDataAPI`、`FleetMemberAPI`、`FleetMemberType`、`PersonAPI`、`List`、`Map`、`LinkedHashMap` 等 `import`；在基础舰队 `spawnFleet(...)` 完成后调用：

```java
inspectMemberCollection(generatedFleet);
scheduleAssignments(generatedFleet, anchor);
```

`generatedFleet` 与 `anchor` 都来自当前生成过程。成员实验在同一轮恢复，任务则留在这支专用巡逻队上推进。玩家舰队继续作为读取与界面对照对象。

## 取得成员、顺序与军官视图

### 成员读取入口速查表

| 方法 | 返回内容 | 当前用途 |
|---|---|---|
| `getMembersListCopy()` | 当前成员列表副本 | 遍历、筛选与保存基线 |
| `getMembersInPriorityOrder()` | 非封存成员在前、封存成员在后的列表 | 比较部署优先顺序 |
| `getNumMembers()` | 当前成员数量 | 增删与恢复核对 |
| `fleet.getFlagship()` | 当前旗舰引用或 `null` | 集合级旗舰状态 |
| `getCombatReadyMembersListCopy()` | 当前可参与战斗的成员副本 | 比较封存等状态的影响 |
| `getOfficersCopy()` | 当前军官列表副本 | 读取人物与成员的关联 |

副本保留取得时的成员视图，增删本地 List 只改变副本。成员对象本身仍是实际对象，它们的船体、装配或封存状态变化仍可以从引用读取。需要最新集合时，再调用一次 `getMembersListCopy()`。

```java
List<FleetMemberAPI> snapshot = data.getMembersListCopy();
for (FleetMemberAPI member : snapshot) {
    LOG.info("GearIn member: id=" + member.getId()
            + " hull=" + member.getHullId()
            + " flagship=" + member.isFlagship());
}
```

当前优先顺序按非封存、封存分组，组内保留原顺序。可战斗成员还受单舰状态影响，因此两种列表的数量分别解释。

::: tip 把副本与对象分别看
列表回答 "取得时有哪些成员"，成员引用回答 "这艘船现在是什么状态"。重新取得列表后，仍可以按成员 ID 对照同一艘船。
:::

## 添加临时成员并完成同步

`FactoryAPI.createFleetMember(...)` 创建成员，本例用原版 `wolf_Assault` 装配。`addFleetMember(...)` 将它加入当前舰队，成员归属、FP 和容量随后进入汇总：

```java
FleetMemberAPI temporary = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");
data.addFleetMember(temporary);
data.syncIfNeeded();
LOG.info("GearIn member added: id=" + temporary.getId()
        + " belongs=" + (temporary.getFleetData() == data)
        + " count=" + data.getNumMembers());
```

同一成员引用属于一支舰队。转移成员时先从原容器移除，再加入目标容器。加入也会为尚未有船名或舰长的成员补上默认值，默认 `steady` 舰长与军官列表中的人物分别处理。

**集合写入会标记需要同步，读取时再完成同步。** `setSyncNeeded()` 登记待处理状态，`syncIfNeeded()` 重建成员列表并更新相关 `stats`、容量与缓存；`syncMemberLists()` 直接整理列表。`getNumMembers()` 等读取入口也可能触发待处理同步，观察时要把这个副作用一起考虑。

### 同步方法速查表

| 方法 | 作用与条件 |
|---|---|
| `setSyncNeeded()` | 标记需要处理后续同步 |
| `syncIfNeeded()` | 按当前待处理标记完成同步 |
| `syncMemberLists()` | 直接整理成员列表 |
| `setOnlySyncMemberLists(true)` | 限制同步为列表整理，`stats`、容量与其他缓存另按完整同步处理 |

需要比较只整理列表与完整同步时，下面的局部片段保存并恢复原开关：

```java
boolean listOnlyBefore = data.isOnlySyncMemberLists();
data.setOnlySyncMemberLists(true);
data.setSyncNeeded();
data.syncIfNeeded();
LOG.info("GearIn list sync: members=" + data.getMembersListCopy().size());
data.setOnlySyncMemberLists(listOnlyBefore);
data.setSyncNeeded();
data.syncIfNeeded();
```

当前成品实验沿用完整同步。编者建议先让普通成员操作完成，再按实际需要收缩同步范围；列表看起来正确，与所有派生数值已经刷新，是两项观察。

## 切换旗舰并恢复人员

`data.setFlagship(member)` 维护整个集合的旗舰标记，并把舰队指挥官交给新旗舰。这一步也会影响成员舰长，因此恢复实验同时保存原旗舰与每名原成员的舰长引用。

把以下局部方法加入生成器：

```java
private void inspectMemberCollection(CampaignFleetAPI fleet) {
    FleetDataAPI data = fleet.getFleetData();
    List<FleetMemberAPI> membersBefore = data.getMembersListCopy();
    FleetMemberAPI flagshipBefore = fleet.getFlagship();
    Map<FleetMemberAPI, PersonAPI> captainsBefore = new LinkedHashMap<>();
    for (FleetMemberAPI member : membersBefore) {
        captainsBefore.put(member, member.getCaptain());
    }
    FleetMemberAPI temporary = Global.getFactory().createFleetMember(
            FleetMemberType.SHIP,
            "wolf_Assault");
    try {
        data.addFleetMember(temporary);
        data.setFlagship(temporary);
        data.syncIfNeeded();
        LOG.info("GearIn collection temporary: count=" + data.getNumMembers()
                + " flagship=" + fleet.getFlagship().getId()
                + " memberFlagship=" + temporary.isFlagship()
                + " fleetPoints=" + fleet.getFleetPoints());
    } finally {
        data.setFlagship(flagshipBefore);
        for (Map.Entry<FleetMemberAPI, PersonAPI> entry : captainsBefore.entrySet()) {
            entry.getKey().setCaptain(entry.getValue());
        }
        data.removeFleetMember(temporary);
        data.syncIfNeeded();
        LOG.info("GearIn collection restored: count=" + data.getNumMembers()
                + " expected=" + membersBefore.size()
                + " flagship=" + (fleet.getFlagship() == null
                        ? "none"
                        : fleet.getFlagship().getId()));
    }
}
```

`try/finally` 将写入与恢复放在同一作用域。先恢复旗舰和原舰长，再移除临时成员，最后同步读取。当前函数在一次调用里完成这些动作，保存的成员仍属于同一舰队。

**旗舰标记与舰长关系一起恢复。** 单独调用 `FleetMemberAPI.setFlagship(true)` 只处理该成员，集合级切换使用 `FleetDataAPI`。原舰队没有旗舰时，保存的 `null` 也由集合入口恢复。较长时间运行的功能需要重新确认原成员是否仍属于舰队，再决定替代旗舰；本例的同步实验保持这一组成员不变。

## 比较顺序与军官关联

下面的局部观察分别记录内部顺序和优先顺序：

```java
List<FleetMemberAPI> currentOrder = data.getMembersListCopy();
List<FleetMemberAPI> priorityOrder = data.getMembersInPriorityOrder();
for (int index = 0; index < currentOrder.size(); index++) {
    LOG.info("GearIn current order: index=" + index
            + " id=" + currentOrder.get(index).getId());
}
for (int index = 0; index < priorityOrder.size(); index++) {
    FleetMemberAPI member = priorityOrder.get(index);
    LOG.info("GearIn priority order: index=" + index
            + " id=" + member.getId()
            + " mothballed=" + member.getRepairTracker().isMothballed());
}
```

`sortToMatchOrder(...)` 才将新顺序写回。传入当前全部成员，重排完成后重新读取列表。下面的局部实验交换前两名，再恢复原顺序：

```java
List<FleetMemberAPI> originalOrder = data.getMembersListCopy();
List<FleetMemberAPI> desiredOrder = data.getMembersListCopy();
if (desiredOrder.size() >= 2) {
    FleetMemberAPI first = desiredOrder.get(0);
    desiredOrder.set(0, desiredOrder.get(1));
    desiredOrder.set(1, first);
    data.sortToMatchOrder(desiredOrder);
    data.syncIfNeeded();
    LOG.info("GearIn reordered first=" + data.getMembersListCopy().get(0).getId());
    data.sortToMatchOrder(originalOrder);
    data.syncIfNeeded();
}
```

成员引用保持对象身份，列表位置随排序变化。缺少成员的排序列表会使剩余槽位由容器另行整理，完整副本更适合这次实验。

军官列表与成员列表分别存在。军官可以尚未分配舰船，`getMemberWithCaptain(person)` 按人物对象查找当前成员：

```java
for (OfficerDataAPI officer : data.getOfficersCopy()) {
    PersonAPI person = officer.getPerson();
    FleetMemberAPI assigned = data.getMemberWithCaptain(person);
    LOG.info("GearIn officer: name=" + person.getNameString()
            + " level=" + person.getStats().getLevel()
            + " assigned=" + (assigned == null ? "none" : assigned.getId()));
}
```

这段读取需要 `com.fs.starfarer.api.characters.OfficerDataAPI` 的 `import`。人物创建与技能由后面专题展开；当前只核对归属。若功能调用 `addOfficer(...)`，结束时按保存的人物或军官数据调用 `removeOfficer(...)`，成员移除与军官列表移除分别完成。

## 重新读取舰队汇总

集合修改后，从各自入口重新取得汇总值，局部观察如下：

```java
LOG.info("GearIn fleet totals: members=" + data.getNumMembers()
        + " fp=" + fleet.getFleetPoints()
        + " strength=" + fleet.getEffectiveStrength()
        + " maxBurn=" + data.getMaxBurnLevel()
        + " minBurn=" + data.getMinBurnLevel()
        + " burn=" + data.getBurnLevel()
        + " cargo=" + fleet.getCargo().getMaxCapacity()
        + " fuel=" + fleet.getCargo().getMaxFuel());
```

FP 与有效强度分别表示当前战力汇总，货舱与燃料容量来自 `Cargo`。最大、最小燃耗描述成员范围，并包含对应最大燃耗修饰；最大燃耗计算排除封存成员。`getBurnLevel()` 则给出舰队有效燃耗，进一步合并舰队范围修饰与实际限制。地图速度还受运行环境影响，按有效值和实际速度一起观察。

成员装配、船插、船员与封存状态也会影响汇总。单舰状态由 [C11-S4] 展开，重算关系由 [C11-S3] 接着说明。

::: warning 拆解带有经济结果
`scuttle(member)` 会移除成员，并把拆解所得燃料、补给和装备武器加入货舱。当前临时成员实验使用 `removeFleetMember(...)`，集合恢复按原成员数量核对。
:::

## 给专用舰队安排两步任务

任务队列属于 `CampaignFleetAPI`。`addAssignment(...)` 将任务加入队尾，`getCurrentAssignment()` 取得队首，`getAssignmentsCopy()` 观察整个队列。持续时间以生涯日计，目标使用当前生成过程的市场主实体。

把下面两个局部方法加入生成器，并添加 `FleetAssignment`、`FleetAssignmentDataAPI`、`EveryFrameScript` 与 `IntervalUtil` 的 `import`：

```java
private void scheduleAssignments(CampaignFleetAPI fleet, SectorEntityToken target) {
    fleet.clearAssignments();
    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 5f,
            "moving to " + target.getName());
    fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 2f,
            "holding near " + target.getName());
    logAssignment(fleet);
    fleet.addScript(new EveryFrameScript() {
        private final IntervalUtil reportInterval = new IntervalUtil(0.1f, 0.1f);

        @Override
        public boolean isDone() {
            return fleet.getCurrentAssignment() == null;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            reportInterval.advance(Global.getSector().getClock().convertToDays(amount));
            if (reportInterval.intervalElapsed()) {
                logAssignment(fleet);
            }
        }
    });
}

private void logAssignment(CampaignFleetAPI fleet) {
    FleetAssignmentDataAPI current = fleet.getCurrentAssignment();
    if (current != null) {
        LOG.info("GearIn assignment: type=" + current.getAssignment()
                + " target=" + (current.getTarget() == null
                        ? "none"
                        : current.getTarget().getId())
                + " remainingDays=" + (current.getMaxDurationInDays()
                        - current.getElapsedDays())
                + " expired=" + current.isExpired()
                + " count=" + fleet.getAssignmentsCopy().size()
                + " speed=" + fleet.getVelocity().length()
                + " facing=" + fleet.getFacing());
    }
}
```

任务先立即记录，再由附着在该舰队上的观察脚本每 0.1 生涯日记录。第一项到达目标或结束后，第二项成为当前任务；队列结束，观察脚本也结束。`FleetAssignment` 位于 `com.fs.starfarer.api.campaign`，`FleetAssignmentDataAPI` 位于其 `ai` 子包。

**队列改变与舰队实际运动分别观察。** 生涯时间推进、舰队已投放且目标有效，任务才产生地图结果。速度为当前帧结果，尚不能单独证明任务完成。其他 AI 也可能清空或插入任务，日志保留目标、剩余天数与队列数量用于比较。

`addAssignment(...)` 的高级重载还接受结束脚本、开始脚本与时间转移等参数，用于相应生命周期。已有任务的已用时间、脚本回调和自定义上下文各有归属；将任务类型和目标重新加入队列，只复制了部分描述。本节把任务实验放在新生成的专用舰队上，清空和两步安排都属于这次任务。

## 验证方案

前置数据：保留原版核心世界的新档测试环境，已按本节将局部方法接到基础生成器。

1. 构建并重启，新建存档，等待两支巡逻队生成；搜索 `GearIn collection temporary` 与 `restored`。
2. 比较成员数量、旗舰 ID 与 FP，核对临时成员增加一名，结束后恢复原集合及舰长引用。
3. 在对应局部观察函数中比较列表副本、优先顺序、军官关联与汇总，执行排序实验后核对原顺序。
4. 前往来源市场星系，观察基础舰队到达市场主实体后进入被动环绕；搜索 `GearIn assignment` 的队首变化、剩余天数与速度。
5. 在玩家舰队界面读取成员与旗舰，对照读取日志；当前写入实验使用新生成的巡逻队。

预期结果：

- 临时集合比基线多一名成员，恢复后数量、原旗舰与原舰长重新对应。
- 副本保持取得时的成员，真实集合变化由成员方法写入；排序实验恢复原顺序。
- 军官日志按人物对象关联成员，未分配者显示 `none`。
- 同步后的 FP、有效强度、燃耗与容量从当前对象读取。
- 专用巡逻队先执行 `GO_TO_LOCATION`，再执行 `ORBIT_PASSIVE`，任务状态与地图移动对应。

## 故障分析

- **修改列表后舰队没有变化**：修改的是成员副本。
    - **解决方案**：使用 `addFleetMember(...)`、`removeFleetMember(...)` 或 `sortToMatchOrder(...)` 写入当前容器。

- **临时成员反复增加**：生成或调用入口重复执行。
    - **解决方案**：保留新档的一次性登记，成员实验在同一次调用中完成恢复。

- **恢复旗舰后舰长发生变化**：只保存了旗舰引用。
    - **解决方案**：保存每名原成员的舰长，恢复旗舰后按对象写回舰长，再移除临时成员。

- **汇总与界面不一致**：使用旧数值或只完成了列表同步。
    - **解决方案**：恢复完整同步开关，执行 `setSyncNeeded()` 与 `syncIfNeeded()`，再读取 FP、燃耗和容量。

- **任务存在但舰队不移动**：舰队未投放、目标失效或时间暂停。
    - **解决方案**：核对舰队与目标的地点、当前任务和暂停状态，使用生成过程取得的主实体。

- **只看到第二项任务**：读取前第一项已经到达或被其他 AI 改写。
    - **解决方案**：保留添加后的即时日志，再比较间隔日志的队列数量与目标。

## 总结

成员集合由 `FleetDataAPI` 写入与同步，副本用于当前观察，旗舰切换还涉及人员关系。保存对象基线、完成写入、恢复原状态，再重新读取汇总，成员实验就能对应明确结果。

任务则由地图舰队按生涯时间执行。本节使用同一支新巡逻队观察队首与运动，把集合恢复与持续任务分别放到适合的运行位置。

## 建议参考项

- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员、旗舰、顺序、军官与同步。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：汇总、投放与任务队列。
- `com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI`：队首、目标、已用与最大持续时间。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份、舰长与旗舰标记。
- `com.fs.starfarer.api.FactoryAPI`：按装配创建成员。
