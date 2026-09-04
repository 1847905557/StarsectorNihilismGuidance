# 读取与修改生涯舰队

本节读取并修改生涯舰队、舰队数据与舰队成员，包括成员加入、保存状态、装配和战斗准备；本节集中处理成员数据，舰队坐标、任务与 AI 由对应生涯对象页面负责。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 列出生涯舰队的三层对象链 (`SectorAPI.getPlayerFleet()` → `CampaignFleetAPI` → `FleetDataAPI` → `FleetMemberAPI`)，并解释成员集合的持久槽位、同步视图与派生状态；
- 用 `FactoryAPI.createFleetMember` 创建成员，用 `addFleetMember` / `removeFleetMember` / `scuttle` 修改成员集合，并解释添加时的自动行为；
- 区分四种成员视图的读取结果，按需求选择正确的遍历入口；
- 读写成员保存态的身份、装配、舰长、船体状态与 CR / 维修；
- 解释战斗准备判据、部署成本，以及后勤容量求和与封存豁免的语义；
- 从症状定位成员读写相关的典型故障，并给出修正路径。

## 前置知识

- [C4-S1] 的生涯与战役域、装配与舰船共享注册表；
- [C4-S2] 的无效装配 ID、路径日志与 `errorShipVariant` 回退；
- [C6-S2] 的插件生命周期回调与持久数据标记；
- [C6-S3] 的 `Global.getFactory()` 创建入口与阶段语义；
- [C9-S1] 的生涯时钟、`EveryFrameScript` 注册时机与一次性判断。

## 术语

按术语约定，本节首次完整讲解一个词：

- **舰队成员 (fleet member)**：API 名保留 `FleetMemberAPI`。一艘舰船或一个战机联队在生涯侧的保存态对象。
    - 成员不是战斗实体 (`ShipAPI`) —— 战斗实体由成员在部署时实例化；成员承载船体 id、装配 (variant)、舰长、船体状态、CR、维修状态与后勤数值。
    - `FleetMemberType` 区分 `SHIP` / `FIGHTER_WING` / `NULL` 三种成员类型。

## 梳理生涯舰队关系

先建立整条对象链的骨架，再逐层进入集合内部、创建入口与读写语义。

### 对象链：Sector → CampaignFleetAPI → FleetDataAPI → FleetMemberAPI

本节所有成员读写都发生在这条三层链上。

### 舰队对象链速查表

各层进入方式与职责如下：

| 层 | 进入方式 | 职责 |
|---|---|---|
| 舰队实体 | `SectorAPI.getPlayerFleet()` / `LocationAPI.getFleets()` 等 | `CampaignFleetAPI`(`com.fs.starfarer.api.campaign.CampaignFleetAPI`)：舰队身份、坐标、速度、势力、成员集合入口、统计、战斗绑定、inflater |
| 成员集合 | `CampaignFleetAPI.getFleetData()` | `FleetDataAPI`(`com.fs.starfarer.api.campaign.FleetDataAPI`)：成员增删遍历、旗舰、军官、同步、快照、容量与 burn 计算 |
| 单个成员 | `FleetDataAPI` 各列表 / `CampaignFleetAPI.getFlagship()` | `FleetMemberAPI`(`com.fs.starfarer.api.fleet.FleetMemberAPI`)：成员保存态全部字段与数值 |

### CampaignFleetAPI 高频成员速查表

| 成员 | 语义 |
|---|---|
| `getFleetData()` | 成员集合入口 (本节主链路) |
| `getFlagship()` | 旗舰成员；无旗舰时返回第一个非战机成员 (`CampaignFleetAPI.getFlagship`) |
| `isPlayerFleet()` | 是否为玩家舰队 |
| `getStats()` | `MutableFleetStatsAPI`：舰队级统计 (burn 修正等) |
| `getLogistics()` | `FleetLogisticsAPI`：补给/燃料消耗与维修调度 |
| `getFleetPoints()` | 当前舰队 fleet point 总量 |
| `getMembersWithFightersCopy()` | 含战机展开的成员副本 |
| `isEmpty()` | 成员列表是否为空 |
| `isValidPlayerFleet()` | "舰队非空且不全由战机联队组成" |
| `forceSync()` | 让容量、船员等与舰队构成、cargo 匹配，并重放舰队 buff / 船插 / 技能效果 |
| `setLocation(x, y)` | 舰队坐标写入入口 |
| `getLocation()` | 坐标读取；**禁止**用它写坐标 (`setLocation` 才是坐标写入入口) |
| `setAIMode(boolean)` | 关闭补给/燃料消耗、事故与船员需求，LR 锁 100% |
| `getInflater()` / `inflateIfNeeded()` / `deflate()` | 保存态 ↔ 完整装配态的膨胀入口 |

对象链只给出进入方式；集合内部如何组织成员，决定成员增删之后您读到的列表和统计是否可信，先看它的分层结构。

### FleetData 的分层结构

`FleetDataAPI` 是成员集合的接口，按持久与派生分四层：

为什么按 "持久 / 派生" 分层

只有槽位列表随存档保存；视图与派生状态每次同步时由成员重算，读档后集合因此自动自洽，成员增删也不需要手动维护任何视图或统计值。

1. **持久槽位列表**：`members`，按槽位存储成员，槽位可被 `NULL_MEMBER` 占位 (`FleetMemberType.NULL`)。这是**唯一随存档保存**的成员层。
    - 添加成员填入第一个空槽 (`findFirstUnusedIndex`)，按索引移除只在原槽放回 NULL(`removeFleetMemberAtIndex`)，整体移除才收缩 (`removeFleetMemberAndCollapseSlot`)。
2. **同步视图**：`membersWithoutNull`(不可变)、`sortedMembersWithoutNull`(按 hull size ordinal 降序 + fleet point cost 降序)、`sortedMembersWithoutNullWithFighters`。
    - 战机展开经 `Utils.expandFighterWingsCampaign` 为每个 fitted wing 槽生成一条 `FIGHTER_WING` 成员；联队内战机数量仍保存在联队数据中。展开只取非封存且 CR > 0 的成员，封存成员排到末尾。
    - `syncMemberLists()` 重建这三个视图。
3. **派生状态**：`fleetPointsUsed` / `effectiveStrength`、`cargo`(容量上限由成员重算)、`minCrew`、`travelSpeed`、`fuelPerLightYear`、burn level 三件套。全部在同步时重算。
4. **瞬态层**：`snapshot`(`transient` 字段) —— `takeSnapshot()` / `getSnapshot()` 的成员快照，**不随存档保存** (接口 "The snapshot is transient and will not be in the save file.")。

同步管线 (`syncIfNeeded`)：`setSyncNeeded()` 置位 → 下一次读成员/容量前触发 —— 成员 stats 全量重算、`cacheClearedOnSync` 清空、舰队指挥官 fleetwide 效果重放、船员重分配 (`recrewFleetMembersV2`)、fleet sync 船插的 `onFleetSync`。

同一管线还执行 `updateFleetPointsUsed`、`updateCargoCapacities`、`updateTravelSpeed`、logistics 刷新、buff 应用、舰队计数与 burn bonus。

`CampaignFleetAPI.forceSync()` 等价于 `setSyncNeeded` + `syncIfNeeded` 执行两次。

集合的持久层是槽位列表；往槽位里放成员之前，先得能把成员造出来。创建与入队是两件事，分开讲。

### 成员创建与注册

### 成员创建入口速查表

创建成员的正式途径 (`FactoryAPI`)：

| 成员 | 语义 |
|---|---|
| `Global.getFactory().createFleetMember(FleetMemberType type, String variantOrWingId)` | 按装配 id(SHIP) 或联队 id(FIGHTER\_WING) 创建 |
| `Global.getFactory().createFleetMember(FleetMemberType type, ShipVariantAPI variant)` | 按装配对象创建 |

(`SettingsAPI` 有同签名双入口；两处创建结果一致，习惯用 `Global.getFactory()`。)

创建后成员即处于 "保存态"：自带默认舰长 (人格 `steady`)、唯一 id(`Misc.genUID()`)、从 CSV spec 加载期取装配并 **clone**(成员各自持有副本)、以及 `FleetMemberStatusAPI`(船体状态) 与 `RepairTrackerAPI`(CR/维修) 两个状态对象。

**创建不等于入队** —— 成员必须交给 `FleetDataAPI.addFleetMember(...)` 才会成为舰队一员。

无效装配 id 的两条分流：`SHIP` 类型装配缺失 → 打印堆栈 + 回退 `errorShipVariant`(默认 `nebula_Standard`)，**不抛异常**；

`FIGHTER_WING` 类型联队缺失 → 抛 `RuntimeException("... is not a valid fighter wing id")`。

创建出来的成员要交给修改入口才会进入集合；改完集合之后，读取侧的四种视图决定您能看到什么。

### 成员添加 / 移除 / 遍历

### FleetDataAPI 修改入口速查表

`FleetDataAPI` 的成员修改入口 (`FleetDataAPI`)：

| 成员 | 语义 |
|---|---|
| `addFleetMember(FleetMemberAPI)` | 加入成员 |
| `addFleetMember(String variantId)` | 按装配 id 创建 `SHIP` 成员并加入，返回新成员 |
| `removeFleetMember(FleetMemberAPI)` | 从列表移除并收缩槽位 |
| `scuttle(FleetMemberAPI)` | 移除并把拆解收益与已装武器放入 cargo |
| `clear()` | 清空成员列表 |
| `setFlagship(FleetMemberAPI)` | 指定旗舰 |

`addFleetMember` 的自动行为：同一对象引用已在列表 → **直接返回不重复添加**；填入第一个 NULL 槽；非战机且船名为空 → 自动起名；写回 `setFleetData(this)`；舰长为空 → 补默认舰长；清旗舰标记；`setSyncNeeded()`。

addFleetMember 禁止传 null

传 null 会抛 `RuntimeException("Attempting to add null fleet member to [...]")`。入队前先对创建结果判空 —— `createFleetMember` 的 `SHIP` 分支对无效装配 id 会回退 `errorShipVariant` 而**不会**返回 null。

### 四种成员视图速查表

四种成员视图 (读取全部返回副本，可安全遍历)：

| 视图 | 内容 |
|---|---|
| `getMembersListCopy()` | 成员副本 (含战机联队，每个联队 1 条；无 NULL 占位)，最常用 |
| `getCombatReadyMembersListCopy()` | 非封存且 `canBeDeployedForCombat()` 的成员 |
| `getMembersInPriorityOrder()` | 非封存成员在前、封存成员在后，类内保持原顺序 |
| `getMembersListWithFightersCopy()` | 每个 fitted wing 槽对应一条 `FIGHTER_WING` 成员；仅展开非封存且 CR > 0 的联队 |

另有 `getMemberWithCaptain(PersonAPI)` 按舰长查成员、`getNumMembers()`、`getFleetPointsUsed()`。成员级实体脚本与视图偏移 (`FleetMemberViewAPI`) 不在本节范围。

成员进入集合后，读写的落点就是它的保存态。`FleetMemberAPI` 按功能分八组：

### FleetMemberAPI：身份、装配、舰长、数值

### FleetMemberAPI 成员分组速查表

`FleetMemberAPI`(`com.fs.starfarer.api.fleet.FleetMemberAPI`) 按功能分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()`("Unique id, generated using Misc.genUID()")、`getSpecId()`、`getHullId()`、`getType()`、`getHullSpec()` | 成员唯一 id；装配 spec id；船体 id；`SHIP`/`FIGHTER_WING`/`NULL` |
| 装配 | `getVariant()`、`setVariant(ShipVariantAPI, boolean withRefit, boolean withStatsUpdate)` | 保存态装配读写；`getVariant().getHullVariantId()` 读装配 id |
| 舰长 | `getCaptain()`、`setCaptain(PersonAPI)`、`getFleetCommander()`、`isFlagship()` / `setFlagship(boolean, boolean)` | 成员舰长；舰队指挥官 (`fleetData.getCommander()`)；旗舰标记 |
| 状态 | `getStatus()`、`getRepairTracker()`、`needsRepairs()`、`canBeRepaired()`、`isMothballed()` | 船体状态、CR/维修、封存 |
| 数值 | `getFleetPointCost()`、`getDeployCost()`、`getDeploymentPointsCost()`、`getDeploymentCostSupplies()`、`getMemberStrength()`、`getBaseValue()`、`getBaseBuyValue()`、`getBaseSellValue()` | 舰队点数、CR 部署成本、部署点数/补给、成员强度、基础价值 |
| 后勤 | `getCargoCapacity()`、`getFuelCapacity()`、`getMinCrew()`、`getMaxCrew()`、`getNeededCrew()`、`getCrewFraction()`、`getFuelUse()` | 单成员后勤数值 |
| 分类 | `isFighterWing()`、`isFrigate()`、`isDestroyer()`、`isCruiser()`、`isCapital()`、`isCivilian()`、`isCarrier()`、`getNumFlightDecks()`、`isPhaseShip()`、`isStation()` | 按 hull size / 角色判定 |
| 其他 | `getFleetData()`、`getStats()`(`MutableShipStatsAPI`)、`getCrewComposition()`、`isAlly()` / `setAlly(boolean)`、`setShipName()` | 回链集合、成员统计、船员构成、战斗瞬态标记、船名 |

**装配写入语义** (`FleetMemberAPI.setVariant`)：`withRefit = true` 时按新旧装配差异对 CR 施加事件 (`applyCREvent(..., "field refit")`)；

非战机成员会同步更新 `specId = variant.getHullVariantId()`(**换装配会连船体一起换**)；`withStatsUpdate = true` 时立即重算成员 stats。

setVariant 保存的是传入引用

创建成员时的装配已被克隆，但 `setVariant` 传入的引用需要调用方自己管理生命周期，不要长期共享同一装配引用。

保存态的 CR 与部署成本决定成员能否上场。战斗准备是 "当前状态" 判据，补给和时间可以恢复。

### 战斗准备语义

- **CR(Combat Readiness)**：`getRepairTracker()` 返回 `RepairTrackerAPI`(`com.fs.starfarer.api.fleet.RepairTrackerAPI`)。
    - `getCR()` 返回 0–1，**已按船员占比修正**；`getBaseCR()` 不含船员修正；`getMaxCR()` 上限；`setCR(float)` 直接写入；`applyCREvent(float, String)` 施加 "近期事件"(显示在 CR 悬浮提示里)。
    - 新成员 CR 初始 `0.5F`。CR 低于阈值会出现在战斗外 "无法部署"。
- **部署判据**：`canBeDeployedForCombat()`(`FleetMemberAPI`) —— 封存 → `false`；战机且 CR ≤ 0 → `false`；否则要求 `CR >= noDeployCRPercent` 且 `crewFraction >= noDeployCrewPercent`(`settings.json`中为 0% 与 5%)。
    - **战斗准备是 "当前状态" 判据**：CR 或船员不足时返回 `false`，补给与时间可恢复。
- **部署成本**：`getDeployCost()` 是 CR 占比 (`CRPerDeploymentPercent * CRToDeploy / 100`，战机乘联队数量；`FleetMemberAPI` "From 0 to 1, CR fraction. Multiplied by number of fighters if fighter wing.")。
    - `getDeploymentPointsCost()` 是部署点数 (战机乘数量)；`getDeploymentCostSupplies()` 先舍入部署补给，再应用最小 1 的下限保护。
- **保存态与膨胀态**：`CampaignFleetAPI.getInflater()` 持有 `FleetInflater`(由 `FleetFactoryV3` 生成的舰队挂载)。
    - `inflateIfNeeded()` 在战役侧把保存态成员补全为完整装配态 (`CampaignFleetAPI.inflateIfNeeded`：inflater 存在、未膨胀、非中立势力时执行；`removeAfterInflating()` 为真则随后移除 inflater)；`deflate()` 回到保存态表达。
    - 玩家舰队的成员默认即保存态，战斗部署时由战斗侧实例化。

能部署是一回事，舰队养不养得起是另一回事：容量、船员与补给消耗按成员求和，封存则把成员从这些负担中豁免出来。

### 后勤语义

- **容量按成员求和**：`updateCargoCapacities` 遍历成员，把 `getFuelCapacity` / `getCargoCapacity` / `getMinCrew` / `getMaxCrew` / `getFuelUse` 累加 (战机按联队数量加权)。
    - 写入 cargo 的 `maxFuel` / `maxCapacity` / `maxPersonnel`，并算出 `minCrew` 与 `fuelPerLightYear`；读取侧 `CampaignFleetAPI.getCargo()`(经 `SectorEntityToken.getCargo()`) 拿到 `CargoAPI`。
- **burn level**：`getMaxBurnLevel()` 是**非封存**成员中最快者；`getMinBurnLevel()` 是全体最慢者；`getBurnLevel()` 是有效 burn(含深超空间与舰队 mod 修正，上限 20)。
    - **原版 javadoc 缺陷**：`getMaxBurnLevel` 的 javadoc 声称含 `getFleetwideMaxBurnMod`，实现并不含。舰队级 mod 只作用于 `getBurnLevel()`。
    - `CampaignFleetAPI.getCurrBurnLevel()` 是当前实际流速。
- **封存 (mothball)**：`getRepairTracker().setMothballed(true)` 后：成员不进 `getCombatReadyMembersListCopy()`、排到 `getMembersInPriorityOrder()` 末尾、`canBeDeployedForCombat()` 恒 `false`。
    - 该调用立即执行成员 `updateStats`，并标记舰队数据等待同步。成员 stats 此时已经重算；舰队容量等派生数据在同步后更新。
    - 封存 stats 把补给月耗乘以 `supplyConsumptionMothballedMult`，并把 cargo / fuel / maxCrew / minCrew 乘数置 0。
    - 封存船**不贡献容量也不烧船员**，即语义上的 "封存闲置船"。

封存修正立即生效

`setMothballed(true)` 在状态变化时立即执行成员 `updateStats` 并标记舰队同步；且 `getCargo()` / `getMembers()` / `getNumMembers()` 等读取路径本身先触发 `syncIfNeeded` —— 改完封存标志直接读数即可，无需手动 `forceSync`。

- **scuttle(拆解)**：`scuttle(member)` 移除成员并把拆解收益 (补给、燃料、重型机械，`getSuppliesFromScuttling` 等)、已装非 built-in 武器与战机 LPC、AI 核心放入 cargo。
    - 拆解是**不可逆**的成员移除方式，与 `removeFleetMember` 的差别在于收益入舱。

机制讲完，示例脚本需要在正确时机注册，才能观察到这些行为。

### 注册入口

本节示例注册**必须**发生在生涯引擎建立之后：沿用生涯回调 `onGameLoad(boolean newGame)` + 各自 persistent data 键的一次性判断模式 (不要把注册键并入插件既有的 `CLASS_MARK` 判断)。

本节用一个自停脚本把创建、入队、读写、清理整条链走一遍，全程只操作演示成员。

## 实现舰队状态读取

本页用一个执行后自停的脚本走完创建、入队、状态修改与清理。以下均为局部片段；每次只增加一组行为，日志与舰队界面给出完成信号。

### 让脚本只执行一次

插入位置：`GearInFleetMemberScript.advance`。先置位再执行序列，即使序列中途抛错，也不会在下一帧继续创建重复成员；`isDone()` 随后让引擎移除脚本。

```java
@Override
public void advance(float amount) {
	if (executed) {
		return;
	}
	executed = true;
	runSequence();
}
```

完成信号：一次注册只产生一组 `GearIn fleet member` 日志，下一帧脚本从引擎脚本集合中移除。

### 创建成员并加入舰队

插入位置：`runSequence` 取得 `CampaignFleetAPI` 与 `FleetDataAPI` 之后。创建结果仍是独立保存态对象；两次 `addFleetMember` 才真正改变玩家舰队集合。

```java
FleetMemberAPI wolf = Global.getFactory().createFleetMember(
		FleetMemberType.SHIP, "wolf_Assault");
FleetMemberAPI hammerhead = Global.getFactory().createFleetMember(
		FleetMemberType.SHIP, "hammerhead_Elite");

data.addFleetMember(wolf);
data.addFleetMember(hammerhead);
```

完成信号：`getNumMembers()` 比初始值增加 2，舰队界面同时出现 Wolf 与 Hammerhead，cargo 容量和 fleet points 随同步管线更新。

### 用成员 ID 核对集合

插入位置：成员入队之后。读取副本再按唯一 ID 比较，避免把可能重复的船名、船体 ID 或装配 ID 当成成员身份。

```java
List<FleetMemberAPI> members = data.getMembersListCopy();
boolean wolfFound = false;
boolean hammerheadFound = false;
for (FleetMemberAPI member : members) {
	if (member.getId().equals(wolf.getId())) {
		wolfFound = true;
	}
	if (member.getId().equals(hammerhead.getId())) {
		hammerheadFound = true;
	}
}
```

完成信号：遍历日志中的 `wolfFound` 与 `hammerheadFound` 同时为 `true`，列表长度与 `getNumMembers()` 一致。

### 修改船体状态与 CR

插入位置：集合核对之后。先制造 30% 船体损伤并修满，再暂时把 CR 写为 0.3；这两组状态分别由 `FleetMemberStatusAPI` 与 `RepairTrackerAPI` 持有。

```java {2,8}
float hullBefore = hammerhead.getStatus().getHullFraction();
hammerhead.getStatus().applyHullFractionDamage(0.3f);
LOG.info("GearIn fleet member hull: " + hullBefore + " -> "
		+ hammerhead.getStatus().getHullFraction());
hammerhead.getStatus().repairFully();

float crBefore = hammerhead.getRepairTracker().getCR();
hammerhead.getRepairTracker().setCR(0.3f);
LOG.info("GearIn fleet member deployable: "
		+ hammerhead.canBeDeployedForCombat());
hammerhead.getRepairTracker().setCR(crBefore);
```

完成信号：船体比例呈现 `1.0 → 0.7 → 1.0`，CR 呈现原值 `→ 0.3 →` 原值；演示成员最终没有遗留损伤。

### 观察封存对部署顺序的影响

插入位置：CR 恢复之后。封存立即改变部署判据，并让成员排到优先顺序末尾；解除封存后再继续后续装配实验。

```java
boolean deployBefore = hammerhead.canBeDeployedForCombat();
hammerhead.getRepairTracker().setMothballed(true);
List<FleetMemberAPI> priorityOrder = data.getMembersInPriorityOrder();
String firstPriority = priorityOrder.isEmpty()
		? "none" : priorityOrder.get(0).getShipName();
LOG.info("GearIn fleet member mothball: " + deployBefore + " -> "
		+ hammerhead.canBeDeployedForCombat() + " | first=" + firstPriority);
hammerhead.getRepairTracker().setMothballed(false);
```

完成信号：Hammerhead 的 `canBeDeployedForCombat()` 从 `true` 变为 `false`，优先列表首项来自非封存成员。

### 换装配后清理演示成员

插入位置：`runSequence` 末尾。保存原装配引用，短暂换成 Wolf 装配并立即恢复，最后从集合移除两个演示成员。

```java
ShipVariantAPI originalVariant = hammerhead.getVariant();
hammerhead.setVariant(wolf.getVariant(), true, true);
LOG.info("GearIn fleet member swapped hull=" + hammerhead.getHullId());
hammerhead.setVariant(originalVariant, true, true);

data.removeFleetMember(wolf);
data.removeFleetMember(hammerhead);
```

完成信号：换装期间 `getHullId()` 为 `wolf`，恢复后为 `hammerhead`；最终成员数与 fleet points 回到脚本开始时的值。

## 组装舰队状态实现

在既有 MOD 布局与插件生命周期类之上新增一个演示脚本类 (不新增资源文件)：

```text {8}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data/
├── src/
│   └── data/scripts/gearin/campaign/
│       └── GearInFleetMemberScript   # 本节新增 (归本节所有)
└── jars/
```

### GearInFleetMemberScript

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回 `executed`(脚本执行后自停，引擎下一帧自动移除)；`runWhilePaused()` 返回 `false`(暂停时不执行)。
- `REGISTER_MARK`：persistent data 判断键，用于判断脚本是否已在该存档执行过 (`"GearIn_FleetMemberScript"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不含任何工作单元编号变体。
- `createFleetMember(FleetMemberType.SHIP, "...")`：创建入口。
    - `wolf_Assault` / `hammerhead_Elite` 是原版注册装配 id(`data/variants/wolf/wolf_Assault.variant`、`data/variants/hammerhead/hammerhead_Elite.variant`，variant id 即文件名去扩展名)。
- `data.addFleetMember(...)`：入队入口；加入后 `getNumMembers()` / `getFleetPoints()` / `getCargo().getMaxCapacity()` 随之变化，容量变化来自同步管线里的 `updateCargoCapacities`。
- `getStatus().applyHullFractionDamage(0.3f)` / `repairFully()`：船体状态写入与恢复 (`hammerhead_Elite` 是普通舰船，单一状态槽，hull fraction 从 1.0 到 0.7 再回 1.0)。
- `getRepairTracker().setCR(0.3f)` / `setMothballed(true)`：CR 与封存写入；`canBeDeployedForCombat()` 与 `getMembersInPriorityOrder()` 是 "当前状态" 读取。
- `setVariant(wolf.getVariant(), true, true)`：装配写入，非战机成员换装配后 `getHullId()` 从 `hammerhead` 变为 `wolf`(`setVariant` 同步更新 `specId`)，换回原装配恢复。
    - 演示中 `hammerhead` 与 `wolf` 短暂共享同一装配引用且双方都不再改装配，属可控演示；生产代码避免长期共享装配引用。
- 清理段 `removeFleetMember`：把两个演示成员移除，玩家舰队恢复初始状态，演示脚本**不留副作用**，可反复读档重放。

调用点 (工程片段，方法所在类归插件生命周期类所有，本节只演示调用，不改动那些文件)：

```java
    // 在 GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFleetMemberScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFleetMemberScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFleetMemberScript());
    }
```

构建命令 (在 `mods/GearIndustry/` 执行，沿用既有 MOD 构建流程)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`(可用 `jar tf` 核对)。

脚本与注册点就绪后，按下面的方案验证整条链。

## 关键故障实验：修改后的舰队成员在战斗准备中消失

成员已创建并能正常读写，但从未加入玩家舰队的 `FleetDataAPI`。

**局部故障片段：**

```java
// GearInFleetMemberScript.runSequence 中只创建和修改
FleetMemberAPI member = Global.getFactory().createFleetMember(
		FleetMemberType.SHIP, "wolf_Assault");
member.setShipName("GearIn Test Ship");
```

**局部修正片段：**

```java
// 创建完成后显式加入目标舰队
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
fleet.getFleetData().addFleetMember(member);
```

实际症状：局部变量上的船名和装配都能读回，日志也没有异常，但 `getNumMembers()` 不变、舰队界面没有新船、战斗准备列表中也找不到该成员。

原因：`createFleetMember` 只构造保存态成员；舰队集合、同步视图与派生统计只观察 `FleetDataAPI` 持有的对象。

复验：记录加入前后的 `getNumMembers()`，应精确增加 1；重新打开舰队界面，按 `member.getId()` 遍历 `getMembersListCopy()`，应命中同一个成员。

## 验证方案

前置数据：`gear_in` MOD 布局与插件生命周期类 ([C6-S1] / [C6-S2]，含本节调用点片段)、本节脚本类、生涯时钟脚本 (非必需)。

1. 游戏构建：在 `mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`。
2. 新建生涯：确认 `starsector.log` 按顺序出现 `GearIn fleet member` 各日志行 (`start` / `created` / `added` / `iterate` / `damage` / `repair` / `CR` / `mothball` / `variant swap` / `variant restore` / `done`)。
    - 观察 `start`、`added` 与 `done` 行的成员数。
3. 界面核对：演示日志出现时打开舰队界面 (F 键)，确认多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。
4. 只执行一次：等待若干游戏日后日志无新增 `GearIn fleet member` 行；保存并读档后仍无新增 (`REGISTER_MARK` 已写入 persistent data，脚本不再注册)。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`。

- **新建生涯 (首次进入存档后第一帧)**：`starsector.log` 依次出现：

    ```text
       GearIn fleet member start: members=<N> fleetPoints=<F> cargoCapacity=<C>
       GearIn fleet member created: wolf id=<id> hull=wolf variant=wolf_Assault fp=<fp> | hammerhead id=<id> hull=hammerhead variant=hammerhead_Elite fp=<fp>
       GearIn fleet member added: members=<N+2> (was <N>) fleetPoints=<F'> (was <F>) cargoCapacity=<C'> (was <C>)
       GearIn fleet member iterate: count=<N+2> wolfFound=true hammerheadFound=true
       GearIn fleet member damage: hullFraction 1.00 -> 0.70 needsRepairs=true
       GearIn fleet member repair: hullFraction=1.00 needsRepairs=false
       GearIn fleet member CR: 0.50 -> 0.30 canBeDeployedForCombat=true
       GearIn fleet member mothball: canBeDeployedForCombat=true->false firstPriority=<船名> mothballed=true
       GearIn fleet member variant swap: hull=wolf variant=wolf_Assault
       GearIn fleet member variant restore: hull=hammerhead variant=hammerhead_Elite
       GearIn fleet member done: members=<N> (initial <N>) fleetPoints=<F> (initial <F>)
    ```

    其中 `<N>` 是初始成员数、`<F>` 是初始 fleet points、`<C>` 是初始 cargo 容量，均以实际日志为准；`firstPriority` 是玩家舰队第一个非封存成员的船名 (演示期间 `hammerhead` 被封存所以不在队首；玩家舰队为空时输出 `none`)。演示只执行一次，之后无任何 `GearIn fleet member` 日志。

- **游戏内舰队界面 (F 键)**：演示期间 (日志可见时) 舰队界面多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。

- **读档**：不出现任何 `GearIn fleet member` 日志 (`REGISTER_MARK` 已写入 persistent data、脚本不再注册)；玩家舰队与演示前一致。

- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

## 故障分析

- **成员数不变且日志没有 added 行**：代码只调用了 `createFleetMember`，没有把保存态成员加入集合。

    - **解决方案**：使用玩家舰队的 `FleetDataAPI.addFleetMember(...)`。

- **`addFleetMember` 抛出 `Attempting to add null fleet member`**：调用方把 null 传给了集合。

    - **解决方案**：检查自定义创建入口的返回值，确认非 null 后再入队。

- **同一成员被加入两轮**：注册脚本缺少一次性判断，每次读档都会重新执行添加逻辑。

    - **解决方案**：使用 `REGISTER_MARK` 门控。

- **重复调用 `addFleetMember` 没有任何变化**：同一对象引用已经在舰队集合中。

    - **解决方案**：通过 `getMembersListCopy()` 按 `getId()` 判断是否已入队。

- **战机联队只被计为一个成员**：`getMembersListCopy()` 与 `getNumMembers()` 按联队对象计数。`getMembersListWithFightersCopy()` 按 fitted wing 槽生成成员，不按单架战机展开；封存或零 CR 联队不会展开。

    - **解决方案**：按计数目的选择接口：联队对象数使用 `getNumMembers()`，展开成员使用 `getMembersListWithFightersCopy()`。

- **`getSnapshot()` 读档后为空**：snapshot 是瞬态字段，不进入存档。

    - **解决方案**：持久成员状态读取 `getMembersListCopy()`；快照只用于战斗前或销毁前的临时比较。

- **修改装配后战斗中仍是旧船**：`FleetMemberAPI.setVariant` 只修改保存态，已部署实体不会跟随变化。

    - **解决方案**：生涯侧使用 `setVariant(variant, true, true)`，战斗实体在部署侧处理。

- **战斗准备列表里没有低 CR 舰船**：CR 低于部署门槛，或船员比例低于默认 5%，都会让 `canBeDeployedForCombat()` 返回 false。

    - **解决方案**：核对 `getCR()` 与 `getCrewFraction()`，补充船员并等待 CR 恢复。

- **封存后仍显示补给消耗和货舱容量**：封存修饰立即写入成员 stats(`setMothballed` 同步执行成员 `updateStats`)，只有舰队级求和 (补给消耗、货舱容量) 延迟到舰队同步时更新。

    - **解决方案**：读取 `getMembers()` 等路径自动触发同步，无需手动 `forceSync()`。

- **`setFlagship` 后其他成员舰长被重置**：该方法会为其他舰船设置全零属性新人，并让新旗舰使用舰队指挥官。

    - **解决方案**：按这一语义安排调用，不要再逐成员调用 `setCaptain` 对抗。

- **演示日志出现但舰队没有变化**：脚本注册在错误生命周期阶段，或操作的不是当前玩家舰队。

    - **解决方案**：将注册放入 `onGameLoad` 的一次性判断块，并从 `Global.getSector().getPlayerFleet()` 重新取得目标。

## 总结

本节把生涯舰队讲成一条链：入口 (`SectorAPI.getPlayerFleet()` → `CampaignFleetAPI`) → 集合 (`getFleetData()` → `FleetDataAPI`) → 成员 (`FleetMemberAPI`)。创建不等于入队、改集合后要想到同步、保存态与战斗态分离，是整条链上反复用到的三条判断 —— 演示脚本正是围绕这三条展开的。

排错时先按症状定位层：成员数量与遍历顺序的问题查集合视图与同步时机，数值没变或读档丢失的问题查保存态字段与瞬态层，部署与后勤的问题查 CR、船员与封存判据。封存修正立即写入成员 stats、只有舰队级求和延迟到同步，这是最常被忽略的一处。

这条对象链到此已经形成完整流程。舰队坐标写入、任务与 AI 调度位于集合层之上，市场补给来源落在经济系统一侧；本节的成员读写语义是这些操作的共同底座。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队实体；成员集合入口、统计、战斗绑定、inflater。
- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员集合；增删遍历、四种成员视图、同步与快照。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员保存态；身份、装配、舰长、状态、数值。
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体状态与维修 (`getHullFraction` / `repairFully` / `applyHullFractionDamage`)。
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR 与封存 (`getCR` / `setCR` / `setMothballed`)。
- `com.fs.starfarer.api.fleet.FleetMemberType`：成员类型枚举 (`SHIP` / `FIGHTER_WING` / `NULL`)。
- `com.fs.starfarer.api.FactoryAPI`：成员创建入口 (`createFleetMember`)。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：舰队 cargo 读取入口 (`getCargo()`)。
- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配对象 (`setVariant` 的入参类型；`getHullVariantId` 读装配 id)。
- `com.fs.starfarer.api.EveryFrameScript`：示例脚本接口 (`isDone` / `runWhilePaused` / `advance`)。
- `data/config/settings.json`：部署判据基线 (`noDeployCRPercent` = 0、`noDeployCrewPercent` = 5)。
- `data/variants/wolf/wolf_Assault.variant`、`data/variants/hammerhead/hammerhead_Elite.variant`：示例使用的原版装配 (variant id 即文件名去扩展名)。

## 完整最小例

::: details 完整成品：GearInFleetMemberScript

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 完整最小例：生涯舰队成员 (FleetMemberAPI) 与舰队数据 (FleetDataAPI)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控
 * (沿用既有 CLASS_MARK 门控模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停 (isDone()=true，引擎下一帧自动移除本脚本)：
 *   1. 读取玩家舰队初始成员数与 fleet points、cargo 容量；
 *   2. 用 Global.getFactory().createFleetMember 创建 wolf_Assault 与 hammerhead_Elite 两个 SHIP 成员；
 *   3. 分别 addFleetMember 加入玩家舰队，记录加入前后成员数、fleet points、cargo 容量变化；
 *   4. 遍历 getMembersListCopy() 验证新成员在列；
 *   5. 船体状态：applyHullFractionDamage(0.3) -> getStatus().getHullFraction() / needsRepairs() -> repairFully()；
 *   6. CR：setCR(0.3f) 压低 CR 再恢复；
 *   7. 封存：setMothballed(true) 对 canBeDeployedForCombat() 与 getMembersInPriorityOrder() 的影响；
 *   8. 装配：setVariant 换成 wolf 装配 -> getHullId() 变化 -> 换回原装配；
 *   9. 清理：removeFleetMember 移除两个演示成员，恢复初始状态。
 *
 * 演示全程只操作演示成员；结束后玩家舰队成员数与初始一致。
 */
public class GearInFleetMemberScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_FleetMemberScript";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetMemberScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
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
        runSequence();
    }

    private void runSequence() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetDataAPI data = playerFleet.getFleetData();

        int initialCount = data.getNumMembers();
        float initialFleetPoints = playerFleet.getFleetPoints();
        float initialCargo = playerFleet.getCargo().getMaxCapacity();
        LOG.info(String.format(
                "GearIn fleet member start: members=%d fleetPoints=%.0f cargoCapacity=%.0f",
                initialCount, initialFleetPoints, initialCargo));

        // --- 创建成员 (先不入队)---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        FleetMemberAPI hammerhead = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hammerhead_Elite");
        LOG.info(String.format(
                "GearIn fleet member created: wolf id=%s hull=%s variant=%s fp=%d | hammerhead id=%s hull=%s variant=%s fp=%d",
                wolf.getId(), wolf.getHullId(), wolf.getVariant().getHullVariantId(), wolf.getFleetPointCost(),
                hammerhead.getId(), hammerhead.getHullId(),
                hammerhead.getVariant().getHullVariantId(), hammerhead.getFleetPointCost()));

        // --- 加入舰队 ---
        data.addFleetMember(wolf);
        data.addFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn fleet member added: members=%d (was %d) fleetPoints=%.0f (was %.0f) cargoCapacity=%.0f (was %.0f)",
                data.getNumMembers(), initialCount,
                playerFleet.getFleetPoints(), initialFleetPoints,
                playerFleet.getCargo().getMaxCapacity(), initialCargo));

        // --- 遍历验证 ---
        List<FleetMemberAPI> members = data.getMembersListCopy();
        boolean wolfFound = false;
        boolean hammerheadFound = false;
        for (FleetMemberAPI member : members) {
            if (member.getId().equals(wolf.getId())) {
                wolfFound = true;
            }
            if (member.getId().equals(hammerhead.getId())) {
                hammerheadFound = true;
            }
        }
        LOG.info(String.format(
                "GearIn fleet member iterate: count=%d wolfFound=%s hammerheadFound=%s",
                members.size(), wolfFound, hammerheadFound));

        // --- 船体状态：损伤与修复 ---
        float hullBefore = hammerhead.getStatus().getHullFraction();
        hammerhead.getStatus().applyHullFractionDamage(0.3f);
        LOG.info(String.format(
                "GearIn fleet member damage: hullFraction %.2f -> %.2f needsRepairs=%s",
                hullBefore, hammerhead.getStatus().getHullFraction(), hammerhead.needsRepairs()));
        hammerhead.getStatus().repairFully();
        LOG.info(String.format(
                "GearIn fleet member repair: hullFraction=%.2f needsRepairs=%s",
                hammerhead.getStatus().getHullFraction(), hammerhead.needsRepairs()));

        // --- CR 与战斗准备 ---
        float crBefore = hammerhead.getRepairTracker().getCR();
        hammerhead.getRepairTracker().setCR(0.3f);
        LOG.info(String.format(
                "GearIn fleet member CR: %.2f -> %.2f canBeDeployedForCombat=%s",
                crBefore, hammerhead.getRepairTracker().getCR(), hammerhead.canBeDeployedForCombat()));
        hammerhead.getRepairTracker().setCR(crBefore);

        // --- 封存与遍历顺序 ---
        boolean deployBefore = hammerhead.canBeDeployedForCombat();
        hammerhead.getRepairTracker().setMothballed(true);
        List<FleetMemberAPI> priorityOrder = data.getMembersInPriorityOrder();
        String firstPriority = priorityOrder.isEmpty() ? "none" : priorityOrder.get(0).getShipName();
        LOG.info(String.format(
                "GearIn fleet member mothball: canBeDeployedForCombat=%s->%s firstPriority=%s mothballed=%s",
                deployBefore, hammerhead.canBeDeployedForCombat(), firstPriority, hammerhead.isMothballed()));
        hammerhead.getRepairTracker().setMothballed(false);

        // --- 装配写入：换装配 -> hull 变化 -> 换回 ---
        ShipVariantAPI originalVariant = hammerhead.getVariant();
        hammerhead.setVariant(wolf.getVariant(), true, true);
        LOG.info(String.format(
                "GearIn fleet member variant swap: hull=%s variant=%s",
                hammerhead.getHullId(), hammerhead.getVariant().getHullVariantId()));
        hammerhead.setVariant(originalVariant, true, true);
        LOG.info(String.format(
                "GearIn fleet member variant restore: hull=%s variant=%s",
                hammerhead.getHullId(), hammerhead.getVariant().getHullVariantId()));

        // --- 清理：恢复初始舰队 ---
        data.removeFleetMember(wolf);
        data.removeFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn fleet member done: members=%d (initial %d) fleetPoints=%.0f (initial %.0f)",
                data.getNumMembers(), initialCount, playerFleet.getFleetPoints(), initialFleetPoints));
    }
}
```

:::
