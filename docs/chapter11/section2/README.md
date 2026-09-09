# 管理舰队中的成员

舰队成员是舰队数据中的可变集合。成员加入、移除、旗舰切换和优先顺序都会影响舰队界面、舰队汇总以及后续任务。FleetDataAPI 负责集合和旗舰，CampaignFleetAPI 负责地图实体与任务队列。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 区分成员集合副本和舰队内部集合；
- 添加、移除成员并恢复原旗舰；
- 读取优先顺序、军官列表和舰队汇总；
- 安排两步任务并观察当前任务和运动状态；
- 根据成员数量、旗舰和日志定位集合故障。

## 前置知识

- [C11-S1] 的 CampaignFleetAPI 取得方式；
- [C11-S4] 的 FleetMemberAPI 创建和单舰状态；
- Java List 副本、对象引用和空值判断；
- 能够从 starsector.log 核对舰队状态。

## 取得舰队数据

CampaignFleetAPI 表示地图上的舰队实体，FleetDataAPI 保存这支舰队的成员、旗舰和军官集合。调用 getFleetData() 不会复制舰队；返回对象仍属于同一支舰队。

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetDataAPI data = fleet.getFleetData();
LOG.info("members=" + data.getNumMembers()
        + " flagship=" + (data.getFlagship() == null
                ? "none"
                : data.getFlagship().getId()));
```

玩家舰队适合作为读取入口，因为成员列表、旗舰和汇总值都能直接在舰队界面复核。写入示例只操作临时创建的成员，并在结尾恢复原旗舰和成员数量。

## 记录集合基线

集合修改前先保存成员引用、旗舰和汇总值。列表副本用于比较成员集合，旗舰引用用于恢复，汇总值用于判断写入是否影响了舰队的派生状态。只保存船名或装配 ID 无法可靠定位同一个成员，因为它们都可能重复。

```java
List<FleetMemberAPI> membersBefore = data.getMembersListCopy();
FleetMemberAPI flagshipBefore = data.getFlagship();
int countBefore = data.getNumMembers();
int pointsBefore = fleet.getFleetPoints();
float burnBefore = data.getBurnLevel();

LOG.info("GearIn: baseline count=" + countBefore
        + " fp=" + pointsBefore
        + " burn=" + burnBefore
        + " flagship=" + (flagshipBefore == null
                ? "none"
                : flagshipBefore.getId()));
```

membersBefore 是一次性的当前状态副本。成员对象本身仍然是实际对象，所以可以用它读取船体、装配和封存状态；列表的增删只影响副本。需要对照写入前后的成员时，保留副本中的对象引用，并在写入后重新调用 getMembersListCopy()。

::: tip 先保存引用，再执行写入
恢复操作需要知道原旗舰和演示成员的对象身份。把基线放在写入之前，才能区分新增成员、顺序变化和汇总延迟。
:::

## 读取成员视图

成员读取入口回答的问题不同。选择入口前先确定需要遍历全部成员、读取旗舰，还是按部署优先级取得顺序。

### 成员读取入口速查表

| 方法 | 返回内容 | 适用场景 | 写入边界 |
| --- | --- | --- | --- |
| getMembersListCopy() | 当前成员列表副本 | 安全遍历和筛选 | 修改列表不会修改舰队 |
| getMembersInPriorityOrder() | 按优先级排列的成员列表 | 部署和界面顺序观察 | 顺序由舰队数据维护 |
| getNumMembers() | 当前成员数量 | 增删前后核对 | 只读汇总 |
| getFlagship() | 当前旗舰引用 | 旗舰切换与恢复 | 可能返回 null |
| getOfficersCopy() | 军官数据副本 | 读取军官归属 | 人物创建由人物专题负责 |

getMembersListCopy() 的价值在于稳定遍历。舰队内部集合变化时，调用方持有的旧副本不会自动增加或删除元素；需要最新状态时重新调用。

getMembersInPriorityOrder() 的顺序由舰队数据维护：后勤优先成员先出现，随后是非封存成员，最后是封存成员；同一类别保留原有顺序。这个返回值用于读取部署优先级，不能当作可写排序列表。需要把一个已经准备好的顺序写回时，调用 sortToMatchOrder(...)，并确认列表包含当前舰队中的全部成员。

getCombatReadyMembersListCopy() 只回答当前可用于战斗的成员数量。封存成员、船体状态和装配状态可能让它少于 getMembersListCopy()；因此不要用两个列表的长度相等作为正常条件。

```java
List<FleetMemberAPI> snapshot = data.getMembersListCopy();
for (FleetMemberAPI member : snapshot) {
    LOG.info("member=" + member.getId()
            + " hull=" + member.getHullId()
            + " flagship=" + member.isFlagship());
}
```

::: tip 副本保留取得时的成员视图
副本适合遍历和比较。对副本调用 add() 或 remove() 只改变本地 List，FleetDataAPI 中的成员不会变化。
:::

## 添加一名临时成员

成员由 FactoryAPI 创建，加入动作由 FleetDataAPI 完成。创建完成但尚未加入舰队时，成员没有这支舰队的集合归属；加入后，成员数量、FP 和后勤容量才会参与舰队汇总。

```java
FleetMemberAPI demo = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");
int membersBefore = data.getNumMembers();
float fleetPointsBefore = fleet.getFleetPoints();

data.addFleetMember(demo);

LOG.info("members=" + membersBefore + "->" + data.getNumMembers()
        + " fleetPoints=" + fleetPointsBefore + "->" + fleet.getFleetPoints());
```

同一成员引用只应加入一支舰队。需要把成员转移到另一支舰队时，先从原 FleetDataAPI 移除，再加入目标集合。

加入后可以主动要求集合同步。setSyncNeeded() 只标记需要同步，syncIfNeeded() 在下一次读取前处理标记；syncMemberLists() 直接整理成员列表。通常使用 syncIfNeeded() 即可，只有需要明确控制同步边界的脚本才直接调用同步方法。

```java
data.setSyncNeeded();
data.syncIfNeeded();

LOG.info("GearIn: synced count=" + data.getNumMembers()
        + " fp=" + fleet.getFleetPoints()
        + " combatReady="
        + data.getCombatReadyMembersListCopy().size());
```

setOnlySyncMemberLists(true) 会把同步范围限制在成员列表。它适用于只想整理集合的读取场景，不适合需要同时更新货舱容量、舰船分类或技能效果的完整状态操作；页面示例保持默认设置，让舰队正常完成自身汇总更新。

## 切换并恢复旗舰

旗舰必须属于当前成员集合。保存原旗舰后，把临时成员设为旗舰，读取 FleetDataAPI 与 FleetMemberAPI 的两个观察入口；清理前先恢复原旗舰，再移除临时成员。

```java
FleetMemberAPI originalFlagship = data.getFlagship();
data.setFlagship(demo);

LOG.info("dataFlagship=" + data.getFlagship().getId()
        + " memberFlagship=" + demo.isFlagship());

data.setFlagship(originalFlagship);
data.removeFleetMember(demo);
```

原舰队可能没有旗舰。恢复代码先判断 originalFlagship 是否仍属于舰队；引用已经被其他逻辑移除时，需要从当前成员中选择有效替代对象。

::: warning 移除当前旗舰前先完成替换
旗舰被移除后，依赖 getFlagship() 的名称、指挥官和部署逻辑可能得到空值。集合清理先恢复旗舰，再移除演示成员。
:::

setFlagship(...) 的副作用还包括为其他舰船设置新的默认舰长对象。临时切换完成后只恢复旗舰引用，并不能把其他脚本在期间修改过的舰长或军官状态全部复原；演示代码只在自己的测试成员上切换，避免覆盖玩家已有人员配置。

## 读取顺序和军官

getMembersInPriorityOrder() 用于观察舰队维护的成员优先顺序。它不是 getMembersListCopy() 的排序开关，调用方也不应通过修改返回列表重排舰队。

```java
List<FleetMemberAPI> priority = data.getMembersInPriorityOrder();
if (!priority.isEmpty()) {
    LOG.info("firstPriority=" + priority.get(0).getShipName());
}
LOG.info("officers=" + data.getOfficersCopy().size());
```

军官列表表示人物与舰队成员的归属关系。人物创建、技能等级和舰长效果在人物与状态专题展开；本页只确认集合中存在多少军官，以及成员集合变化没有意外删除人物。

## 观察舰队汇总

成员加入或移除后，CampaignFleetAPI 提供的 FP、货舱容量和燃料容量会按当前成员重新计算。读取汇总时重新调用访问器，不缓存写入前的数值。

```java
LOG.info("fleetPoints=" + fleet.getFleetPoints()
        + " cargoCapacity=" + fleet.getCargo().getMaxCapacity()
        + " fuelCapacity=" + fleet.getCargo().getMaxFuel());
```

成员的装配、船插或封存状态也可能改变部分汇总值。集合写入只解释成员是否参与计算；具体单舰状态由 [C11-S4] 负责，stats 重算由 [C11-S3] 负责。

getFleetPoints() 是舰队战力点数的当前汇总，getEffectiveStrength() 是按成员强度计算的另一种汇总。getMaxBurnLevel()、getMinBurnLevel() 和 getBurnLevel() 的含义不同：前两者分别描述最快和最慢成员的燃耗等级，getBurnLevel() 才是舰队当前可用的有效燃耗等级。封存成员不会参与最快燃耗等级的计算。

如果脚本需要处理成员被打捞或拆解的结果，可以使用 scuttle(member)。这个入口会从舰队移除成员，并把拆解所得燃料、补给和已装备武器加入货舱；它不是普通的 removeFleetMember() 替代品。页面演示只使用普通移除，避免把经济变化混入集合验证。

## 安排舰队任务

任务队列属于 CampaignFleetAPI。addAssignment() 把新任务放到队尾；当前任务由 getCurrentAssignment() 返回。持续时间以生涯天数计，目标必须在舰队能够访问的地点关系中保持有效。

```java
fleet.addAssignment(
        FleetAssignment.GO_TO_LOCATION,
        target,
        5f,
        "moving to " + target.getName());
fleet.addAssignment(
        FleetAssignment.ORBIT_PASSIVE,
        target,
        2f,
        "holding near " + target.getName());
```

加入队列后立即读取当前任务，可以确认第一项已经成为活动任务：

```java
FleetAssignmentDataAPI current = fleet.getCurrentAssignment();
if (current != null) {
    LOG.info("assignment=" + current.getAssignment()
            + " target=" + current.getTarget().getId()
            + " speed=" + fleet.getVelocity().length()
            + " facing=" + fleet.getFacing());
}
```

速度表示当前帧运动结果，不代表任务已经完成。舰队尚未投放、目标被移除或地点不可达时，队列仍可能存在，但地图上看不到预期移动。

::: tip 任务队列按生涯时间推进
addAssignment() 只改变待执行任务。舰队已经属于有效地点、目标仍然存在且生涯时间推进时，任务才会持续产生运动结果。
:::

## 处理一次集合修改

下面的局部序列把集合写入和恢复放在同一作用域。try/finally 可以确保日志或观察代码抛出异常时仍执行清理。

```java
FleetMemberAPI originalFlagship = data.getFlagship();
FleetMemberAPI demo = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");
try {
    data.addFleetMember(demo);
    data.setFlagship(demo);
    LOG.info("temporary members=" + data.getNumMembers());
} finally {
    if (originalFlagship != null) {
        data.setFlagship(originalFlagship);
    }
    data.removeFleetMember(demo);
}
```

本页没有完整 Java 类。片段用于加入已有生涯脚本或控制台辅助代码，调用方负责提供 fleet、data、target 和 logger。

## 让成员集合完成一次同步

成员写入和成员可读列表之间存在一个明确的同步边界。`addFleetMember(...)` 与 `removeFleetMember(...)` 先修改内部槽位，再调用 `setSyncNeeded()`；这一步只登记后续需要处理的工作。`syncIfNeeded()` 才会重新建立不含空槽的成员列表，并按当前舰队上下文更新成员 stats、舰长、货舱容量和缓存。

```java
FleetMemberAPI demo = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");

if (demo == null) {
    LOG.warn("GearIn: unable to create member for sync probe");
    return;
}

data.addFleetMember(demo);

LOG.info("GearIn: marked=" + data.getNumMembers()
        + " syncOnly=" + data.isOnlySyncMemberLists());

data.syncIfNeeded();

LOG.info("GearIn: synchronized=" + data.getNumMembers()
        + " memberFleetData=" + (demo.getFleetData() == data)
        + " ready=" + data.getCombatReadyMembersListCopy().size());

data.removeFleetMember(demo);
data.syncIfNeeded();
```

`getNumMembers()` 会通过 `getMembers()` 读取同步后的列表，因此它可以触发一次待处理同步。显式调用 `syncIfNeeded()` 适合需要把日志分成写入时刻和完成时刻的调试代码；普通游戏逻辑可以直接重新访问读入口，让容器按需处理标记。

### 控制只同步成员列表

`setOnlySyncMemberLists(true)` 会让下一次同步只重建成员列表，随后 `syncIfNeeded()` 清除该次待处理标记。它不能代替完整舰队同步，因为成员 stats、货舱容量和舰队级缓存可能仍等待后续处理。

```java
data.setOnlySyncMemberLists(true);
data.setSyncNeeded();
data.syncIfNeeded();

LOG.info("GearIn: listOnly=" + data.isOnlySyncMemberLists()
        + " members=" + data.getMembersListCopy().size());

data.setOnlySyncMemberLists(false);
data.setSyncNeeded();
data.syncIfNeeded();
```

测试脚本使用该开关时，要在同一个作用域恢复原值。长时间保持 `true` 会使后续代码误以为完整状态已经刷新，最终表现为界面成员列表正确而 FP、燃耗或容量滞后。

::: tip 观察点要放在同步之后
集合写入后的第一个日志记录应区分 `setSyncNeeded()` 和 `syncIfNeeded()`。汇总值、可部署数量和成员 stats 应在同步完成后重新取得。
:::

## 读取成员顺序并写回排序

成员有三个容易混淆的顺序来源。`getMembersListCopy()` 反映当前内部顺序，`getMembersInPriorityOrder()` 按非封存成员、封存成员分组，`sortToMatchOrder(...)` 才会把传入顺序写回成员槽位。读取入口返回的列表都不能直接当作写入口。

```java
List<FleetMemberAPI> currentOrder = data.getMembersListCopy();
List<FleetMemberAPI> priorityOrder = data.getMembersInPriorityOrder();

LOG.info("GearIn: currentOrder=" + currentOrder.size()
        + " priorityOrder=" + priorityOrder.size());

for (int index = 0; index < currentOrder.size(); index++) {
    FleetMemberAPI member = currentOrder.get(index);
    LOG.info("GearIn: current=" + index
            + " id=" + member.getId());
}

for (int index = 0; index < priorityOrder.size(); index++) {
    FleetMemberAPI member = priorityOrder.get(index);
    LOG.info("GearIn: priority=" + index
            + " id=" + member.getId()
            + " mothballed=" + member.getRepairTracker().isMothballed());
}
```

需要改变顺序时，先制作包含当前全部成员的副本，再调用 `sortToMatchOrder(...)`。传入缺少成员的列表会让剩余槽位由实现重新整理，造成下一次读取顺序与预期不同；演示代码只读取顺序，不对玩家舰队执行排序写入。

```java
List<FleetMemberAPI> desiredOrder = data.getMembersListCopy();

if (desiredOrder.size() >= 2) {
    FleetMemberAPI first = desiredOrder.get(0);
    desiredOrder.set(0, desiredOrder.get(1));
    desiredOrder.set(1, first);
    data.sortToMatchOrder(desiredOrder);
    data.syncIfNeeded();
}
```

排序完成后重新调用 `getMembersListCopy()`，不要继续使用写入前的列表判断界面顺序。成员对象引用仍然稳定，列表位置不稳定；需要追踪某艘船时以对象引用或成员 ID 作为观察键。

## 读取军官 roster 与成员关联

`getOfficersCopy()` 返回舰队 roster 的列表副本。它与 `getMembersListCopy()` 是两个独立集合：一名军官可以尚未挂到成员，也可以通过成员的 captain 关系挂到某艘船。`getMemberWithCaptain(person)` 用对象身份反查当前挂载成员。

```java
List<OfficerDataAPI> officers = data.getOfficersCopy();

for (OfficerDataAPI officer : officers) {
    PersonAPI person = officer.getPerson();
    FleetMemberAPI assigned = data.getMemberWithCaptain(person);

    LOG.info("GearIn: officer=" + person.getNameString()
            + " level=" + person.getStats().getLevel()
            + " assigned=" + (assigned == null
                    ? "none"
                    : assigned.getId()));
}
```

加入成员时，原版实现会为没有舰长的成员创建默认的 `steady` 人物；这项默认补全属于成员加入过程，不能推断为 roster 中已有一名玩家军官。人物专题负责 officer 的创建、等级和技能，本页只观察集合关系。

如果测试脚本主动调用 `addOfficer(...)`，清理阶段要保存 `OfficerDataAPI` 或 `PersonAPI` 对象并调用对应的 `removeOfficer(...)`。移除成员不会替您删除 roster 中独立存在的军官。

## 读取舰队汇总的来源

舰队汇总至少包含成员数量、FP、有效强度、燃耗和货舱容量。它们的读取入口分布在 `FleetDataAPI`、`CampaignFleetAPI` 和 `CargoAPI`，写入成员后要分别重新访问。一个缓存变量不能代表所有层级的同步状态。

```java
int members = data.getNumMembers();
int fleetPoints = fleet.getFleetPoints();
float effectiveStrength = fleet.getEffectiveStrength();
float maxBurn = data.getMaxBurnLevel();
float minBurn = data.getMinBurnLevel();
float burn = data.getBurnLevel();
float cargoCapacity = fleet.getCargo().getMaxCapacity();
float fuelCapacity = fleet.getCargo().getMaxFuel();

LOG.info("GearIn: members=" + members
        + " fp=" + fleetPoints
        + " strength=" + effectiveStrength
        + " maxBurn=" + maxBurn
        + " minBurn=" + minBurn
        + " burn=" + burn
        + " cargo=" + cargoCapacity
        + " fuel=" + fuelCapacity);
```

`getMaxBurnLevel()` 和 `getMinBurnLevel()` 描述成员燃耗范围，`getBurnLevel()` 描述舰队当前可以使用的有效值。最大和最小燃耗都包含舰队级最大燃耗修正；最大燃耗的成员筛选还会排除封存成员。深空等环境会进一步影响有效燃耗，因此地图运动验证要以 `getBurnLevel()` 和实际速度共同判断。

成员装配、船插、船员或封存状态变化也会改变汇总。集合页面只负责确认成员是否在容器中；单舰状态和 stats 重算分别由 [C11-S4] 和 [C11-S3] 说明。

## 让任务队列按照顺序执行

`CampaignFleetAPI.addAssignment(...)` 有多个重载。页面使用带动作文本的重载，让日志和舰队界面能够识别每一项任务；脚本回调、是否把时间加到下一项以及开始回调属于同一组高级参数，只有确实需要生命周期回调时才使用。

```java
SectorEntityToken target = fleet.getContainingLocation()
        .getEntityById("gear_in_training_buoy");

if (target == null) {
    LOG.warn("GearIn: no target for assignment chain");
    return;
}

fleet.clearAssignments();
fleet.addAssignment(
        FleetAssignment.GO_TO_LOCATION,
        target,
        5f,
        "moving to training buoy");
fleet.addAssignment(
        FleetAssignment.ORBIT_PASSIVE,
        target,
        2f,
        "orbiting training buoy");

List<FleetAssignmentDataAPI> assignments = fleet.getAssignmentsCopy();
LOG.info("GearIn: assignmentCount=" + assignments.size());
```

队列首项由 `getCurrentAssignment()` 返回，后续项通过 `getAssignmentsCopy()` 观察。`FleetAssignmentDataAPI` 的 `getElapsedDays()` 和 `getMaxDurationInDays()` 都以生涯天数表达；读取 `getTarget()` 时要处理目标为空的情况。

```java
FleetAssignmentDataAPI current = fleet.getCurrentAssignment();

if (current != null) {
    float remaining = current.getMaxDurationInDays()
            - current.getElapsedDays();
    LOG.info("GearIn: assignment=" + current.getAssignment()
            + " target=" + (current.getTarget() == null
                    ? "none"
                    : current.getTarget().getId())
            + " remainingDays=" + remaining
            + " expired=" + current.isExpired());
}
```

当第一项过期时，任务模块移除队首并让第二项成为当前任务。其他 AI 逻辑可能调用 `clearAssignments()` 或插入新的队首任务，因此验证要在添加后立即记录队列，再在生涯时间推进后重复记录。

::: warning 任务目标需要保持有效
任务记录的是 `SectorEntityToken` 对象引用。目标被移出地点、舰队不在有效地点或任务模块被冻结时，队列记录仍可能存在，运动结果却不会达到预期。
:::

## 用 try/finally 恢复舰队

集合演示应把所有写入和恢复放在同一作用域。恢复阶段至少处理原旗舰、演示成员的归属、任务队列和同步标记；如果脚本修改了军官或成员舰长，还要按对象身份保存对应状态。

```java
List<FleetMemberAPI> membersBefore = data.getMembersListCopy();
FleetMemberAPI flagshipBefore = data.getFlagship();
List<FleetAssignmentDataAPI> assignmentsBefore = fleet.getAssignmentsCopy();
FleetMemberAPI demo = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");

if (demo == null) {
    throw new IllegalStateException("Unable to create temporary member");
}

try {
    data.addFleetMember(demo);
    data.setFlagship(demo);
    data.syncIfNeeded();

    LOG.info("GearIn: temporary count=" + data.getNumMembers()
            + " currentFlagship=" + data.getFlagship().getId());
} finally {
    boolean flagshipStillPresent = false;
    for (FleetMemberAPI member : data.getMembersListCopy()) {
        if (member == flagshipBefore) {
            flagshipStillPresent = true;
            break;
        }
    }

    if (flagshipStillPresent) {
        data.setFlagship(flagshipBefore);
    } else {
        data.ensureHasFlagship();
    }

    if (demo.getFleetData() == data) {
        data.removeFleetMember(demo);
    }

    fleet.clearAssignments();
    for (FleetAssignmentDataAPI assignment : assignmentsBefore) {
        if (assignment.getTarget() != null
                && assignment.getTarget().getContainingLocation()
                        == fleet.getContainingLocation()) {
            fleet.addAssignment(
                    assignment.getAssignment(),
                    assignment.getTarget(),
                    assignment.getMaxDurationInDays(),
                    assignment.getActionText());
        }
    }

    data.syncIfNeeded();
    LOG.info("GearIn: restored count=" + data.getNumMembers()
            + " expected=" + membersBefore.size()
            + " flagship=" + (data.getFlagship() == null
                    ? "none"
                    : data.getFlagship().getId()));
}
```

任务对象的已用时间、脚本回调和内部自定义数据不一定能通过公开重载完整复制。测试脚本要把任务恢复目标限定在仍然有效的实体，并把不可复制的运行时上下文列入人工验证项；临时演示不应清空玩家原有任务后直接结束。

## 完整最小例

本页不新增完整 Java 类。成员集合和任务队列都依赖调用方已经取得的生涯舰队，因此完整代码会把多个职责重新混在一个入口中；正文片段分别展示每个写入动作，便于在现有脚本中安全接入。

## 验证方案

前置数据：使用包含至少一艘舰船的测试存档，并保存一份可在验证后删除的副本。

1. 记录玩家舰队的成员数量、旗舰名称、FP、货舱容量和燃料容量。
2. 执行临时成员创建与加入片段，重新打开舰队界面。
3. 核对成员数量增加一名，临时成员出现在列表中，FP 和容量按成员属性变化。
4. 执行旗舰切换片段，确认舰队界面的旗舰标记转移到临时成员。
5. 执行恢复与移除片段，确认成员数量、旗舰和汇总恢复。
6. 对地图上的测试舰队添加两步任务，观察当前任务文本与位置变化。
7. 打开 starsector.log，核对成员 ID、优先顺序、军官数量、当前任务和速度字段。

预期结果：

- 列表副本包含取得时的成员，但修改副本不会改变舰队界面；
- addFleetMember() 后成员数量和汇总重新计算；
- FleetDataAPI 与成员对象对旗舰状态的读取一致；
- 恢复顺序完成后，临时成员消失，原旗舰重新生效；
- 当前任务首先是 GO_TO_LOCATION，到达后切换为 ORBIT_PASSIVE；
- 日志没有空目标、空旗舰或重复成员异常。

## 故障分析

- **修改列表后舰队没有变化**：代码修改的是 getMembersListCopy() 返回的副本。

    - **解决方案**：使用 addFleetMember() 和 removeFleetMember() 写入 FleetDataAPI。

- **成员数量增加两次**：同一注册入口重复创建并加入成员。

    - **解决方案**：记录成员引用或使用一次性注册标记，加入前检查目标集合。

- **移除成员后旗舰为空**：临时成员仍是当前旗舰。

    - **解决方案**：先恢复仍在集合中的原旗舰，再移除临时成员。

- **FP 或容量还是旧值**：日志使用了写入前缓存的数值。

    - **解决方案**：集合修改后重新调用 CampaignFleetAPI 和 CargoAPI 的访问器。

- **任务存在但舰队不移动**：舰队未加入地点、目标失效或生涯时间没有推进。

    - **解决方案**：核对双方 containingLocation、目标存活状态和当前暂停状态。

- **第二项任务覆盖第一项观察**：读取时第一项已经完成或被其他 AI 清除。

    - **解决方案**：增加第一项持续时间，并在添加后立即记录 getCurrentAssignment()。

## 总结

FleetDataAPI 维护成员、旗舰、顺序和军官集合，CampaignFleetAPI 根据当前集合提供汇总并执行任务队列。集合副本负责安全读取，写入入口负责真实状态变化，恢复顺序负责让测试存档回到原始成员结构。

## 建议参考项

- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员集合、旗舰、顺序和军官入口；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队汇总、运动状态和任务队列；
- `com.fs.starfarer.api.campaign.FleetAssignmentDataAPI`：当前任务与目标；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份与旗舰状态；
- `com.fs.starfarer.api.FactoryAPI`：舰队成员创建入口。
