# 管理舰队中的成员

生成舰队时，我们把舰船的选择交给了工厂。现在直接管理其中的成员：加入一艘狼，把它临时设为旗舰，再恢复原有舰船与舰长。随后给这支巡逻队安排前往市场、停留环绕的任务，从成员管理接到地图上的行动。

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

## 从舰队取得成员

从玩家舰队开始读取，舰队界面正好可以与日志对照。`CampaignFleetAPI` 表示地图上的整支舰队，它的 `getFleetData()` 返回管理成员、旗舰与军官的 `FleetDataAPI`：

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetDataAPI data = fleet.getFleetData();
LOG.info("GearIn members: count=" + data.getNumMembers()
        + " flagship=" + (fleet.getFlagship() == null
                ? "none"
                : fleet.getFlagship().getId()));
```

这里的 `data` 管理的就是玩家舰队。取得成员列表时，游戏提供的是列表副本，适合遍历、筛选和保存当前顺序：

```java
List<FleetMemberAPI> snapshot = data.getMembersListCopy();
for (FleetMemberAPI member : snapshot) {
    LOG.info("GearIn member: id=" + member.getId()
            + " hull=" + member.getHullId()
            + " flagship=" + member.isFlagship());
}
```

列表保存取得时有哪些成员，以及它们当时的排列顺序。此后增删这个 List，舰队本身保持原样；再次调用 `getMembersListCopy()`，才能取得最新名单。船名和装配 ID 都可能重复，日志用成员 ID 识别具体舰船，后面的恢复操作则直接保存成员引用。

::: warning 列表副本保留真实成员引用
列表里的成员仍是真实舰船。通过这些引用修改舰长、装配或封存状态，会直接影响对应成员；复制名单并没有复制每艘舰船。
:::

## 加入一艘狼，再恢复原舰队

写入实验使用 [C11-S1] 新生成的基础巡逻队。把本节的局部方法加入 `GearInFleetGenerator`，在基础舰队的 `spawnFleet(...)` 完成后调用：

```java
inspectMemberCollection(generatedFleet);
scheduleAssignments(generatedFleet, anchor);
```

`generatedFleet` 是刚投放的舰队，`anchor` 是来源市场的主实体。第一个方法在一次调用里完成成员实验和恢复，第二个方法留下任务供生涯继续执行。添加这些方法所用的 `FleetDataAPI`、`FleetMemberAPI`、`FleetMemberType`、`PersonAPI`、`List`、`Map` 与 `LinkedHashMap` 等 `import`；任务部分的类型在后文说明。

### 创建成员并加入舰队

用原版 `wolf_Assault` 装配创建一名成员，再交给 `addFleetMember(...)`。下面是成员实验中的加入操作：

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

加入时，成员会取得舰队归属，缺少的船名和舰长也会补齐；默认舰长采用 `steady` 性格。已有成员转移到另一支舰队时，要先从原舰队移除，同一个成员只属于一支舰队。

数量、FP 和容量需要跟随成员变化更新。加入操作会标记待同步，`syncIfNeeded()` 完成这次更新，然后日志读取结果。`getNumMembers()` 这样的读取也可能触发待处理同步，因此不能把每个 getter 都当成毫无副作用的观察。

### 临时切换旗舰

接下来把这艘狼设为旗舰。**切换旗舰也会调整舰长，恢复时要同时恢复人员关系。** `data.setFlagship(...)` 把舰队指挥官交给新旗舰，并维护集合中的旗舰标记。只保存原旗舰引用，还不足以恢复这次操作前的舰队。

下面的局部方法先保存原名单、旗舰与各成员的舰长，在 `finally` 中按原引用恢复：

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

恢复顺序也有意义：先切回原旗舰，再写回各舰长，随后移除狼并同步。这样，恢复旗舰时产生的人员调整才会被原舰长覆盖。原来没有旗舰时，保存的 `null` 同样通过集合入口写回。

`FleetMemberAPI.setFlagship(true)` 只改一个成员的标记；要切换整支舰队的旗舰，使用这里的 `FleetDataAPI.setFlagship(...)`。本例在同一次调用里恢复，原成员始终留在舰队。若功能跨越一段生涯时间，原旗舰可能已经损失或转移，恢复时就要根据当时的成员决定旗舰。

::: warning 拆解会产生回收物
`scuttle(member)` 会移除成员，并将拆解所得燃料、补给和装备武器等加入货舱。这里用 `removeFleetMember(...)` 撤销临时加入，恢复后按原成员数量核对。
:::

## 让汇总跟随成员变化

成员名单只是舰队状态的一部分。装配、船插、船员、封存状态和人物效果都会参与汇总，[C11-S3] 继续解释数值重算，[C11-S4] 展开单舰状态。本节先在集合操作完成后读取结果：

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

FP 与有效强度用于观察舰队战力，货舱和燃料容量从 `Cargo` 读取。日志里的 `burn` 是**航速等级**：`getMaxBurnLevel()` 取非封存成员中最高的最大航速，`getMinBurnLevel()` 取成员中最低的最大航速，两者读取各成员已修饰的最大航速。`getBurnLevel()` 进一步合并舰队范围的航速修饰并处理有效值的限制，地图上的实际运动还可以结合速度向量观察。

### 同步方法速查表

| 方法 | 作用与条件 |
|---|---|
| `setSyncNeeded()` | 标记需要处理后续同步 |
| `syncIfNeeded()` | 按当前待处理标记完成同步，更新成员数值、容量与缓存 |
| `syncMemberLists()` | 直接整理成员列表 |
| `setOnlySyncMemberLists(true)` | 将同步限制为列表整理，成员数值、容量与其他缓存留待完整同步 |

只需要整理成员顺序时，可以启用列表同步。下面的局部片段保存开关，完成列表操作后恢复原设置，再安排一次同步：

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

本节的成员实验采用完整同步，因为我们还要比较 FP 和容量。限制同步后，名单已经正确，派生数值却可能尚未刷新；这两项结果要分别确认。

## 调整排列顺序与查询军官

成员名单还有不同的读取用途。普通列表保留当前排列，`getMembersInPriorityOrder()` 将非封存成员放在封存成员之前，组内保留原顺序；`getCombatReadyMembersListCopy()` 则筛选当前能参加战斗的成员。下面的局部日志比较前两种顺序：

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

取得优先顺序的列表不会重排原名单。要写回顺序，把希望采用的排列交给 `sortToMatchOrder(...)`。本例交换前两名，再恢复原顺序：

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

这个操作改变排列位置，成员身份保持原样。排序列表只列出部分成员时，余下成员会在这些成员之后继续排列；本例使用完整名单，恢复结果可以逐项对照。

舰队的军官另有一份名单。军官可以尚未分配舰船，普通成员也可以使用默认舰长，因此成员数量与军官数量没有一一对应关系。用 `getMemberWithCaptain(person)` 按人物引用查询分配结果：

```java
for (OfficerDataAPI officer : data.getOfficersCopy()) {
    PersonAPI person = officer.getPerson();
    FleetMemberAPI assigned = data.getMemberWithCaptain(person);
    LOG.info("GearIn officer: name=" + person.getNameString()
            + " level=" + person.getStats().getLevel()
            + " assigned=" + (assigned == null ? "none" : assigned.getId()));
}
```

添加 `com.fs.starfarer.api.characters.OfficerDataAPI` 的 `import` 后，可以把这段放在成员观察中。`getMemberWithCaptain(...)` 比较人物对象，姓名相同的人物仍可能是不同对象。若实验还调用了 `addOfficer(...)`，结束时按保存的人物调用 `removeOfficer(...)`；移除舰船和移除军官分别处理。

### 成员读取入口速查表

| 方法 | 返回内容 | 当前用途 |
|---|---|---|
| `getMembersListCopy()` | 当前成员列表副本 | 遍历、筛选与保存原顺序 |
| `getMembersInPriorityOrder()` | 非封存成员在前、封存成员在后的列表 | 比较成员优先顺序 |
| `getNumMembers()` | 当前成员数量 | 增删与恢复核对 |
| `fleet.getFlagship()` | 当前旗舰引用或 `null` | 确认舰队旗舰 |
| `getCombatReadyMembersListCopy()` | 当前可参与战斗的成员副本 | 比较封存等状态的影响 |
| `getOfficersCopy()` | 当前军官列表副本 | 查询人物与成员的关联 |

## 给专用舰队安排两步任务

成员管理完成后，让巡逻队前往来源市场，再在那里停留两日。通过 `CampaignFleetAPI.addAssignment(...)` 依次加入任务，AI 按队列执行；持续时间采用生涯日，目标用生成过程取得的市场主实体。

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

`GO_TO_LOCATION` 最多持续 5 日，到达目标后可以提前结束；随后执行 2 日的 `ORBIT_PASSIVE`。`getCurrentAssignment()` 返回当前队首，`getAssignmentsCopy()` 返回队列副本，副本中的任务对象仍属于原队列。这里先立即记录一轮，再由舰队脚本每 0.1 生涯日记录，队列结束后观察脚本也结束。

`FleetAssignment` 位于 `com.fs.starfarer.api.campaign`，`FleetAssignmentDataAPI` 位于其 `ai` 子包。观察脚本沿用 [C10-S1] 的生涯时间换算，将帧时间转换成日后推进 `IntervalUtil`。

舰队已投放、目标有效，并且生涯时间继续推进时，任务才能产生地图行为。速度反映当前运动，队首说明正在执行的任务；其他 AI 可能插入或清空任务，所以日志同时保留目标、剩余天数与队列数量。

本节可以清空任务，是因为这支新舰队的行动由当前生成器安排。**编者建议在接管已有舰队前，先确定其原任务是否允许丢失。** 将类型和目标保存下来，再重新入队，并不能恢复已经消耗的时间、回调和自定义上下文。`addAssignment(...)` 的高级重载还接受开始脚本、结束脚本与时间转移等参数，原任务可能正依靠这些内容推进所属功能。

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
- 同步后的 FP、有效强度、航速等级与容量从当前对象读取。
- 专用巡逻队先执行 `GO_TO_LOCATION`，再执行 `ORBIT_PASSIVE`，任务状态与地图移动对应。

## 故障分析

- **修改列表后舰队没有变化**：修改的是成员副本。
    - **解决方案**：使用 `addFleetMember(...)`、`removeFleetMember(...)` 或 `sortToMatchOrder(...)` 写入当前容器。

- **临时成员反复增加**：生成或调用入口重复执行。
    - **解决方案**：保留新档的一次性登记，成员实验在同一次调用中完成恢复。

- **恢复旗舰后舰长发生变化**：只保存了旗舰引用。
    - **解决方案**：保存每名原成员的舰长，恢复旗舰后按对象写回舰长，再移除临时成员。

- **汇总与界面不一致**：使用旧数值或只完成了列表同步。
    - **解决方案**：恢复完整同步开关，执行 `setSyncNeeded()` 与 `syncIfNeeded()`，再读取 FP、航速等级和容量。

- **任务存在但舰队不移动**：舰队未投放、目标失效或时间暂停。
    - **解决方案**：核对舰队与目标的地点、当前任务和暂停状态，使用生成过程取得的主实体。

- **日志只记录了第二项任务**：读取前第一项已经到达或被其他 AI 改写。
    - **解决方案**：保留添加后的即时日志，再比较间隔日志的队列数量与目标。

## 总结

这艘临时加入的狼让我们同时接触了成员归属、旗舰和舰长。名单副本保存成员引用，真正的增删与排序由 `FleetDataAPI` 写入；操作完成后同步，再读取数量与汇总，才能确认当前舰队。

地图行动由任务队列继续推进。对自己生成的巡逻队，可以直接决定去哪里、停留多久；接管已有舰队时，还要承担原任务中时间、回调和上下文的去留。

## 建议参考项

- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员、旗舰、顺序、军官与同步。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：汇总、投放与任务队列。
- `com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI`：队首、目标、已用与最大持续时间。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份、舰长与旗舰标记。
- `com.fs.starfarer.api.FactoryAPI`：按装配创建成员。
