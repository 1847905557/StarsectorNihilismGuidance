# 舰队、舰队数据与成员

本页把生涯舰队（`CampaignFleetAPI`）、舰队数据（`FleetDataAPI`）与舰队成员（`FleetMemberAPI`）讲透：舰队怎么挂载成员、成员的保存态与装配怎么读写、战斗准备与后勤语义由哪些 API 承载。舰队坐标、任务与 AI 不在本页范围——本页只给与成员读写相关的部分。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 生涯舰队的三层对象链：`SectorAPI.getPlayerFleet()` → `CampaignFleetAPI`（舰队实体）→ `getFleetData()` → `FleetDataAPI`（成员集合）→ `FleetMemberAPI`（单个成员），以及每层的典型 getter 族；
- 说出 `FleetDataAPI` 的内部分层：
  - 持久槽位列表（含 NULL 占位）、同步视图（无 NULL 成员列表、排序列表、含战机展开列表）；
  - 派生状态（fleet points、cargo 容量、min crew、burn level）；
  - 并解释 `setSyncNeeded()` / `syncIfNeeded()` / `forceSync()` 的重算管线；
- 用 `FactoryAPI.createFleetMember` 创建成员、用 `addFleetMember` / `removeFleetMember` / `scuttle` 修改成员集合，说出添加时的自动行为（起名、补默认舰长、清旗舰标记）与移除时的槽位语义；
- 用四种成员视图读取舰队：
  - `getMembersListCopy()`（含战机联队，每联队 1 条）；
  - `getCombatReadyMembersListCopy()`（可部署）；
  - `getMembersInPriorityOrder()`（非封存优先）；
  - `getMembersListWithFightersCopy()`（战机逐架展开）；
- 读写 `FleetMemberAPI` 的保存态：
  - 身份（`getId` / `getHullId` / `getSpecId` / `getType`）；
  - 装配（`getVariant` / `setVariant`）、舰长（`getCaptain` / `setCaptain` / 旗舰）；
  - 船体状态（`getStatus`）、CR 与维修（`getRepairTracker`）；
- 说出战斗准备与后勤语义：`canBeDeployedForCombat()` 的判据、`getDeployCost()` / `getDeploymentPointsCost()` 的含义、cargo / fuel / crew 容量如何按成员求和、mothball（封存）如何改变统计值与 burn level；
- 从症状定位八类典型故障：成员没加进舰队、加了但数量不对、遍历拿不到战机、`getSnapshot()` 读档后为空、改装配没生效、战斗里 CR 低到不能部署、封存了还在烧补给、`setFlagship` 后舰长集体被重置。

## 前置知识

本节需要以下前置知识，按主题列出：

- **生涯时钟与脚本注册守卫**（S08-P01）：`SectorAPI`、生涯时钟、`EveryFrameScript` 注册守卫（persistent data `CLASS_MARK` 模式）。
- **生涯与战役、装配与共享注册表**（S03-P01）：生涯（campaign）与战役（mission）的区别；装配（variant）与舰船共享注册表。
- **无效装配 ID 的两条分流**（S04-P01）：`FleetMemberAPI` 路径日志 + `errorShipVariant` 回退（本页"成员创建"直接使用该结论）。
- **工厂入口与阶段判断**（S06-P03）：`Global.getFactory()` 是创建生涯对象的正式途径；`getCurrentState()` 阶段判断。
- **插件生命周期回调与守卫键**（S06-P02）：`GearInModPlugin` 的生命周期回调与 `CLASS_MARK` 守卫。

## 术语

按术语约定，本页首次完整讲解一个词（S08-P04 起继续使用）：

- **舰队成员（fleet member）**：API 名保留 `FleetMemberAPI`。一艘舰船或一个战机联队在生涯侧的保存态对象。
  - 成员不是战斗实体（`ShipAPI`）——战斗实体由成员在部署时实例化；成员承载船体 id、装配（variant）、舰长、船体状态、CR、维修状态与后勤数值。
  - `FleetMemberType` 区分 `SHIP` / `FIGHTER_WING` / `NULL` 三种成员类型。

## 文件与 API 链路

### 对象链：Sector → CampaignFleetAPI → FleetDataAPI → FleetMemberAPI

三层对象链：

| 层 | 进入方式 | 职责 |
|---|---|---|
| 舰队实体 | `SectorAPI.getPlayerFleet()` / `getFleetById` 等 | `CampaignFleetAPI`（`com.fs.starfarer.api.campaign.CampaignFleetAPI`）：舰队身份、坐标、速度、势力、成员集合入口、统计、战斗绑定、inflater |
| 成员集合 | `CampaignFleetAPI.getFleetData()` | `FleetDataAPI`（`com.fs.starfarer.api.campaign.FleetDataAPI`）：成员增删遍历、旗舰、军官、同步、快照、容量与 burn 计算 |
| 单个成员 | `FleetDataAPI` 各列表 / `CampaignFleetAPI.getFlagship()` | `FleetMemberAPI`（`com.fs.starfarer.api.fleet.FleetMemberAPI`）：成员保存态全部字段与数值 |

`CampaignFleetAPI` 高频成员（`CampaignFleetAPI`）：

| 成员 | 语义 |
|---|---|
| `getFleetData()` | 成员集合入口（本页主链路）|
| `getFlagship()` | 旗舰成员；无旗舰时返回第一个非战机成员（`CampaignFleetAPI.getFlagship`）|
| `isPlayerFleet()` | 是否为玩家舰队 |
| `getStats()` | `MutableFleetStatsAPI`：舰队级统计（burn 修正等）|
| `getLogistics()` | `FleetLogisticsAPI`：补给/燃料消耗与维修调度 |
| `getFleetPoints()` | 当前舰队 fleet point 总量 |
| `getMembersWithFightersCopy()` | 含战机展开的成员副本（见"四种成员视图"）|
| `isEmpty()` | 成员列表是否为空 |
| `isValidPlayerFleet()` | "舰队非空且不全由战机联队组成"（javadoc 原文）|
| `forceSync()` | 让容量、船员等与舰队构成、cargo 匹配，并重放舰队 buff / 船插 / 技能效果（见"FleetData 的分层结构"）|
| `setLocation(x, y)` | 舰队坐标写入入口 |
| `getLocation()` | 坐标读取；**禁止**用它写坐标（`setLocation` 才是坐标写入入口）|
| `setAIMode(boolean)` | 关闭补给/燃料消耗、事故与船员需求，LR 锁 100%（javadoc 原文）|
| `getInflater()` / `inflateIfNeeded()` / `deflate()` | 保存态 ↔ 完整装配态的膨胀入口（见"战斗准备"）|

### FleetData 的分层结构

`FleetDataAPI` 是成员集合的接口，按持久与派生分四层：

::: info 为什么按"持久 / 派生"分层

只有槽位列表随存档保存；视图与派生状态每次同步时由成员重算——这样读档后集合自动自洽，成员增删也不需要手动维护任何视图或统计值。

:::

1. **持久槽位列表**：`members`——按槽位存储成员，槽位可被 `NULL_MEMBER` 占位（`FleetMemberType.NULL`）。这是**唯一随存档保存**的成员层。
   - 添加成员填入第一个空槽（`findFirstUnusedIndex`），按索引移除只在原槽放回 NULL（`removeFleetMemberAtIndex`），整体移除才收缩（`removeFleetMemberAndCollapseSlot`）。
2. **同步视图**：`membersWithoutNull`（不可变）、`sortedMembersWithoutNull`（按 hull size ordinal 降序 + fleet point cost 降序）、`sortedMembersWithoutNullWithFighters`。
   - 战机展开经 `Utils.expandFighterWingsCampaign` 把每个联队展开成逐架成员；展开只取非封存且 CR > 0 的成员，封存成员排到末尾。
   - `syncMemberLists()` 重建这三个视图。
3. **派生状态**：`fleetPointsUsed` / `effectiveStrength`、`cargo`（容量上限由成员重算）、`minCrew`、`travelSpeed`、`fuelPerLightYear`、burn level 三件套。全部在同步时重算。
4. **瞬态层**：`snapshot`（`transient` 字段）——`takeSnapshot()` / `getSnapshot()` 的成员快照，**不随存档保存**（接口 javadoc 原文："The snapshot is transient and will not be in the save file."）。

同步管线（`syncIfNeeded`）：`setSyncNeeded()` 置位 → 下一次读成员/容量前触发——成员 stats 全量重算、`cacheClearedOnSync` 清空、舰队指挥官 fleetwide 效果重放、船员重分配（`recrewFleetMembersV2`）、fleet sync 船插的 `onFleetSync`。

同一管线还执行 `updateFleetPointsUsed`、`updateCargoCapacities`、 `updateTravelSpeed`、logistics 刷新、buff 应用、舰队计数与 burn bonus。

`CampaignFleetAPI.forceSync()` 等价于 `setSyncNeeded` + `syncIfNeeded` 执行两次。

### 成员创建与注册

创建成员的正式途径（`FactoryAPI`）：

| 成员 | 语义 |
|---|---|
| `Global.getFactory().createFleetMember(FleetMemberType type, String variantOrWingId)` | 按装配 id（SHIP）或联队 id（FIGHTER_WING）创建 |
| `Global.getFactory().createFleetMember(FleetMemberType type, ShipVariantAPI variant)` | 按装配对象创建 |

（`SettingsAPI` 有同签名双入口；两处创建结果一致，习惯用 `Global.getFactory()`。）

创建后成员即处于"保存态"：自带默认舰长（人格 `steady`）、唯一 id（`Misc.genUID()`）、从 CSV spec 加载期取装配并 **clone**（成员各自持有副本）、以及 `FleetMemberStatusAPI`（船体状态）与 `RepairTrackerAPI`（CR/维修）两个状态对象。

**创建不等于入队**—— 成员必须交给 `FleetDataAPI.addFleetMember(...)` 才会成为舰队一员。

无效装配 id 的两条分流：`SHIP` 类型装配缺失 → 打印堆栈 + 回退 `errorShipVariant`（默认 `nebula_Standard`），**不抛异常**；

`FIGHTER_WING` 类型联队缺失 → 抛 `RuntimeException("... is not a valid fighter wing id")`。

### 成员添加 / 移除 / 遍历

`FleetDataAPI` 的成员修改入口（`FleetDataAPI`）：

| 成员 | 语义 |
|---|---|
| `addFleetMember(FleetMemberAPI)` | 加入成员（自动行为见下）|
| `addFleetMember(String variantId)` | 按装配 id 创建 `SHIP` 成员并加入，返回新成员 |
| `removeFleetMember(FleetMemberAPI)` | 从列表移除并收缩槽位 |
| `scuttle(FleetMemberAPI)` | 移除并把拆解收益与已装武器放入 cargo（见"后勤语义"）|
| `clear()` | 清空成员列表 |
| `setFlagship(FleetMemberAPI)` | 指定旗舰（副作用见"实例驱动故障分析"）|

`addFleetMember` 的自动行为：同一对象引用已在列表 → **直接返回不重复添加**；填入第一个 NULL 槽；非战机且船名为空 → 自动起名；写回 `setFleetData(this)`；舰长为空 → 补默认舰长；清旗舰标记；`setSyncNeeded()`。

::: caution addFleetMember 禁止传 null

传 null 会抛 `RuntimeException("Attempting to add null fleet member to [...]")`。入队前先对创建结果判空—— `createFleetMember` 的 `SHIP` 分支对无效装配 id 会回退 `errorShipVariant` 而**不会**返回 null。

:::

四种成员视图（读取全部返回副本，可安全遍历）：

| 视图 | 内容 |
|---|---|
| `getMembersListCopy()` | 成员副本（含战机联队，每个联队 1 条；无 NULL 占位），最常用 |
| `getCombatReadyMembersListCopy()` | 非封存且 `canBeDeployedForCombat()` 的成员 |
| `getMembersInPriorityOrder()` | 非封存成员在前、封存成员在后，类内保持原顺序 |
| `getMembersListWithFightersCopy()` | 战机联队按联队数量逐架展开的完整列表（`Utils.expandFighterWingsCampaign`；仅非封存且 CR > 0 的联队展开，封存排末尾）|

另有 `getMemberWithCaptain(PersonAPI)` 按舰长查成员、`getNumMembers()`、`getFleetPointsUsed()`。成员级实体脚本与视图偏移（`FleetMemberViewAPI`）不在本页范围。

### FleetMemberAPI：身份、装配、舰长、数值

`FleetMemberAPI`（`com.fs.starfarer.api.fleet.FleetMemberAPI`）按功能分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()`（"Unique id, generated using Misc.genUID()"，javadoc 原文）、`getSpecId()`、`getHullId()`、`getType()`、`getHullSpec()` | 成员唯一 id；装配 spec id；船体 id；`SHIP`/`FIGHTER_WING`/`NULL` |
| 装配 | `getVariant()`、`setVariant(ShipVariantAPI, boolean withRefit, boolean withStatsUpdate)` | 保存态装配读写；`getVariant().getHullVariantId()` 读装配 id |
| 舰长 | `getCaptain()`、`setCaptain(PersonAPI)`、`getFleetCommander()`、`isFlagship()` / `setFlagship(boolean, boolean)` | 成员舰长；舰队指挥官（`fleetData.getCommander()`）；旗舰标记 |
| 状态 | `getStatus()`、`getRepairTracker()`、`needsRepairs()`、`canBeRepaired()`、`isMothballed()` | 船体状态、CR/维修、封存（见"战斗准备"）|
| 数值 | `getFleetPointCost()`、`getDeployCost()`、`getDeploymentPointsCost()`、`getDeploymentCostSupplies()`、`getMemberStrength()`、`getBaseValue()`、`getBaseBuyValue()`、`getBaseSellValue()` | 舰队点数、CR 部署成本、部署点数/补给、成员强度、基础价值 |
| 后勤 | `getCargoCapacity()`、`getFuelCapacity()`、`getMinCrew()`、`getMaxCrew()`、`getNeededCrew()`、`getCrewFraction()`、`getFuelUse()` | 单成员后勤数值（见"后勤语义"）|
| 分类 | `isFighterWing()`、`isFrigate()`、`isDestroyer()`、`isCruiser()`、`isCapital()`、`isCivilian()`、`isCarrier()`、`getNumFlightDecks()`、`isPhaseShip()`、`isStation()` | 按 hull size / 角色判定 |
| 其他 | `getFleetData()`、`getStats()`（`MutableShipStatsAPI`）、`getCrewComposition()`、`isAlly()` / `setAlly(boolean)`（"Transient, not saved."，javadoc 原文）、`setShipName()` | 回链集合、成员统计、船员构成、战斗瞬态标记、船名 |

**装配写入语义**（`FleetMemberAPI.setVariant`）：`withRefit = true` 时按新旧装配差异对 CR 施加事件（`applyCREvent(..., "field refit")`）；

非战机成员会同步更新 `specId = variant.getHullVariantId()`（**换装配会连船体一起换**）；`withStatsUpdate = true` 时立即重算成员 stats。

::: warning setVariant 保存的是传入引用

创建成员时的装配已被克隆，但 `setVariant` 传入的引用需要调用方自己管理生命周期——不要长期共享同一装配引用。

:::

### 战斗准备语义

- **CR（Combat Readiness）**：`getRepairTracker()` 返回 `RepairTrackerAPI`（`com.fs.starfarer.api.fleet.RepairTrackerAPI`）。
  - `getCR()` 返回 0–1，**已按船员占比修正**（javadoc 原文）；`getBaseCR()` 不含船员修正；`getMaxCR()` 上限；`setCR(float)` 直接写入；`applyCREvent(float, String)` 施加"近期事件"（显示在 CR 悬浮提示里）。
  - 新成员 CR 初始 `0.5F`。CR 低于阈值会出现在战斗外"无法部署"（见下）。
- **部署判据**：`canBeDeployedForCombat()`（`FleetMemberAPI`）——封存 → `false`；战机且 CR ≤ 0 → `false`；否则要求 `CR >= noDeployCRPercent` 且 `crewFraction >= noDeployCrewPercent`（`settings.json`，RC8 为 0% 与 5%）。
  - **战斗准备是"当前状态"判据**：CR 或船员不足时返回 `false`，补给与时间可恢复。
- **部署成本**：`getDeployCost()` 是 CR 占比（`CRPerDeploymentPercent * CRToDeploy / 100`，战机乘联队数量；`FleetMemberAPI` javadoc："From 0 to 1, CR fraction. Multiplied by number of fighters if fighter wing."）。
  - `getDeploymentPointsCost()` 是部署点数（战机乘数量）；`getDeploymentCostSupplies()` 是部署补给（含最小 1 上限保护）。
- **保存态与膨胀态**：`CampaignFleetAPI.getInflater()` 持有 `FleetInflater`（由 `FleetFactoryV3` 生成的舰队挂载）。
  - `inflateIfNeeded()` 在战役侧把保存态成员补全为完整装配态（`CampaignFleetAPI.inflateIfNeeded`：inflater 存在、未膨胀、非中立势力时执行；`removeAfterInflating()` 为真则随后移除 inflater）；`deflate()` 回到保存态表达。
  - 玩家舰队的成员默认即保存态，战斗部署时由战斗侧实例化。

### 后勤语义

- **容量按成员求和**：`updateCargoCapacities` 遍历成员，把 `getFuelCapacity` / `getCargoCapacity` / `getMinCrew` / `getMaxCrew` / `getFuelUse` 累加（战机按联队数量加权）。
  - 写入 cargo 的 `maxFuel` / `maxCapacity` / `maxPersonnel`，并算出 `minCrew` 与 `fuelPerLightYear`；读取侧 `CampaignFleetAPI.getCargo()`（经 `SectorEntityToken.getCargo()`）拿到 `CargoAPI`。
- **burn level**：`getMaxBurnLevel()` 是**非封存**成员中最快者（含 `getFleetwideMaxBurnMod`）；`getMinBurnLevel()` 是全体最慢者；`getBurnLevel()` 是有效 burn（含深超空间与舰队 mod 修正，上限 20）。
  - `CampaignFleetAPI.getCurrBurnLevel()` 是当前实际流速。
- **封存（mothball）**：`getRepairTracker().setMothballed(true)` 后：成员不进 `getCombatReadyMembersListCopy()`、排到 `getMembersInPriorityOrder()` 末尾、`canBeDeployedForCombat()` 恒 `false`。
  - stats 重算时补给月耗 × `supplyConsumptionMothballedMult`、cargo / fuel / maxCrew / minCrew 乘数置 0（`FleetMemberAPI.updateStats`）。
  - 封存船**不贡献容量也不烧船员**，这正是"封存闲置船"的语义。

::: warning 封存修正是延迟生效的

stats 修正在下一次 `updateStats` 才写入——改完封存标志后要触发同步（如 `forceSync`）再读数，否则读到的还是旧值。

:::

- **scuttle（拆解）**：`scuttle(member)` 移除成员并把拆解收益（补给、燃料、重型机械，`getSuppliesFromScuttling` 等）、已装非 built-in 武器与战机 LPC、AI 核心放入 cargo。
  - 拆解是**不可逆**的成员移除方式，与 `removeFleetMember` 的差别在于收益入舱。

### 注册入口

本页示例注册**必须**发生在生涯引擎建立之后：沿用生涯回调 `onGameLoad(boolean newGame)` + 各自 persistent data 键守卫模式（不要把注册键并入插件既有的 `CLASS_MARK` 守卫）。注册见"完整最小例"的调用点片段。

## 完整最小例

在既有 MOD 布局与插件生命周期类之上新增一个演示脚本类（不新增资源文件）：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data/
├── src/
│   └── data/scripts/gearin/campaign/
│       └── GearInFleetMemberScript   # 本页新增（归本页所有）
└── jars/
```

### GearInFleetMemberScript

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
 * 完整最小例：生涯舰队成员（FleetMemberAPI）与舰队数据（FleetDataAPI）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK 一次性门控
 * （沿用既有 CLASS_MARK 门控模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停（isDone()=true，引擎下一帧自动移除本脚本）：
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

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_FleetMemberScript";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetMemberScript() {
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

        // --- 创建成员（先不入队）---
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

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回 `executed`（脚本执行后自停，引擎下一帧自动移除）；`runWhilePaused()` 返回 `false`（暂停时不执行）。
- `REGISTER_MARK`：persistent data 守卫键（`"GearIn_FleetMemberScript"`），**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不含任何工作单元编号变体。
- `createFleetMember(FleetMemberType.SHIP, "...")`：创建入口。
  - `wolf_Assault` / `hammerhead_Elite` 是原版注册装配 id（`data/variants/wolf/wolf_Assault.variant`、`data/variants/hammerhead/hammerhead_Elite.variant`，variant id = 文件名去扩展名）。
- `data.addFleetMember(...)`：入队入口；加入后 `getNumMembers()` / `getFleetPoints()` / `getCargo().getMaxCapacity()` 随之变化—— 容量变化来自同步管线里的 `updateCargoCapacities`。
- `getStatus().applyHullFractionDamage(0.3f)` / `repairFully()`：船体状态写入与恢复（`hammerhead_Elite` 是普通舰船，单一状态槽，hull fraction 从 1.0 到 0.7 再回 1.0）。
- `getRepairTracker().setCR(0.3f)` / `setMothballed(true)`：CR 与封存写入；`canBeDeployedForCombat()` 与 `getMembersInPriorityOrder()` 是"当前状态"读取。
- `setVariant(wolf.getVariant(), true, true)`：装配写入——非战机成员换装配后 `getHullId()` 从 `hammerhead` 变为 `wolf`（`setVariant` 同步更新 `specId`），换回原装配恢复。
  - 演示中 `hammerhead` 与 `wolf` 短暂共享同一装配引用且双方都不再改装配，属可控演示；生产代码避免长期共享装配引用。
- 清理段 `removeFleetMember`：把两个演示成员移除，玩家舰队恢复初始状态——演示脚本**不留副作用**，可反复读档重放。

调用点（工程片段——方法所在类归插件生命周期类所有，本页只演示调用，不改动那些文件）：

```java
    // 在 GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本页注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFleetMemberScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFleetMemberScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFleetMemberScript());
    }
```

构建命令（在 `mods/GearIndustry/` 执行，沿用既有 MOD 构建流程）：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`（可用 `jar tf` 核对）。本页源码与 fixture 逐字一致，fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

- **前置数据**：`gear_in` MOD 布局与插件生命周期类（S06-P01 / S06-P02，含本页调用点片段）、本页脚本类、生涯时钟脚本（S08-P01，非必需）。
- **操作**：
  1. fixture 编译（验收 J）：在 Git Bash 执行 `gradle -p tutorial-fixtures compileS08p03Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
  2. 游戏构建：在 `mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`。
  3. 新建生涯：确认 `starsector.log` 按"预期结果"第 3 条顺序出现 `GearIn fleet member` 各日志行（`start` / `created` / `added` / `iterate` / `damage` / `repair` / `CR` / `mothball` / `variant swap` / `variant restore` / `done`）。
     - `added` 行成员数比 `start` 行多 2；`done` 行成员数与 `start` 一致；全程无 `gear_in` 引起的 `ERROR`。
  4. 界面核对：演示日志出现时打开舰队界面（F 键），确认多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成（"预期结果"第 4 条）。
  5. 只执行一次：等待若干游戏日后日志无新增 `GearIn fleet member` 行；保存并读档后仍无新增（`REGISTER_MARK` 守卫已置位，"预期结果"第 5 条）。
  6. 故障注入 1：注释掉调用点片段的 `addScript` 行，重启新档：无任何 `GearIn fleet member` 日志（对应"实例驱动故障分析"第 1 条前半）；改回后确认恢复。
  7. 故障注入 2：把 `setMothballed(true)` 之后改成立刻读 `playerFleet.getCargo().getMaxCapacity()`（不先同步），对比加入 `forceSync()` 后的读数—— 验证封存修正延迟生效（对应"实例驱动故障分析"第 8 条）。
- **通过信号**：操作 1–5 全部符合；操作 6、7 的注入与恢复都符合预期。
- **清理**：取消勾选测试 MOD；确认没有对共享文件（如 fixture 构建脚本）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行

   ```text
   gradle -p tutorial-fixtures compileS08p03Java --no-daemon
   ```

   以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInFleetMemberScript.class`。
3. **新建生涯（首次进入存档后第一帧）**：`starsector.log` 依次出现

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

   其中 `<N>` 是初始成员数、`<F>` 是初始 fleet points、`<C>` 是初始 cargo 容量，均以实际日志为准；`firstPriority` 是玩家舰队第一个非封存成员的船名（演示期间 `hammerhead` 被封存所以不在队首；玩家舰队为空时输出 `none`）。演示只执行一次，之后无任何 `GearIn fleet member` 日志。
4. **游戏内舰队界面（F 键）**：演示期间（日志可见时）舰队界面多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。
5. **读档**：不出现任何 `GearIn fleet member` 日志（`REGISTER_MARK` 守卫已置位、脚本不再注册）；玩家舰队与演示前一致。
6. **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。

静态自查：脚本类源码与 fixture 逐字一致（包括 import 顺序）；`REGISTER_MARK` 与调用点片段的键逐字一致；没有把工作单元编号写进任何运行时名称（类名、包名、persistent data 键均不含 S08-P03 变体）；示例只使用既有 GearIn ID（无新增资源 ID）。

## 实例驱动故障分析

1. **成员没加进舰队（数量不变、日志没有 added 行）**。触发：只 `createFleetMember` 忘了 `addFleetMember`（创建 ≠ 入队）。
   - 可观察：created 日志出现但 added 行缺失，舰队界面无新船，`getNumMembers()` 不变且无报错。
   - 修正：把成员交给 `FleetDataAPI.addFleetMember(...)`；向玩家舰队添加用 `Global.getSector().getPlayerFleet().getFleetData()`。
2. **`addFleetMember` 抛 `Attempting to add null fleet member`**。触发：把 null 传给 `addFleetMember`（如装配 id 查不到时自己返回了 null）。
   - 可观察：`RuntimeException` 堆栈，异常消息含舰队名。
   - 修正：创建入口 `createFleetMember` 的 `SHIP` 分支对无效装配 id 回退 `errorShipVariant`，**不会**返回 null——先把创建结果判空再入队。
3. **同一成员数量成双或行为叠加**。触发：对已入队成员再次 `addFleetMember`（或注册脚本无守卫导致每档跑两次）。
   - 可观察：前者直接无效果（同引用重复添加被忽略）；后者演示日志出现两次、成员被加两轮。
   - 修正：注册用 `REGISTER_MARK` 守卫（本页调用点片段）；需要"该成员是否已在队"的判断用 `getMembersListCopy()` 按 `getId()` 比较。
4. **战机数量对不上（联队被算成 1 条而不是 N 架）**。触发：用 `getMembersListCopy()` / `getNumMembers()` 统计"舰队里有多少架战机"。
   - 可观察：`getMembersListCopy()` 返回的列表里战机联队是 1 条（联队对象本身，经 `getMembers()` 全量返回），联队内数量要读 `getNumFightersInWing()`；需要逐架展开必须用 `getMembersListWithFightersCopy()`（`Utils.expandFighterWingsCampaign`）。
   - 修正：按需求选视图——"每个联队算 1 条"用 `getMembersListCopy()`，"逐架计数"用 `getMembersListWithFightersCopy()`；注意展开视图只包含非封存且 CR > 0 的联队，封存 / 零 CR 联队不会展开。
5. **`getSnapshot()` 读档后为空或与成员不一致**。触发：把 `takeSnapshot()` 的快照当成持久成员集合（接口 javadoc："The snapshot is transient and will not be in the save file."；`snapshot` 是 `transient` 字段）。
   - 可观察：读档后快照丢失；战斗中或舰队被销毁前拍下的快照与当前成员无关。
   - 修正：持久状态一律读 `getMembersListCopy()` 等成员集合视图；快照只用于"战斗前/销毁前"的临时比较。
6. **改装配没生效（战斗里还是旧船）**。触发：把 `setVariant` 当成战斗实体修改入口。
   - 可观察：成员保存态已变（`getVariant()` 新值），但已部署的战斗实体仍按战斗实体状态读取（`FleetMemberAPI.setVariant` 只改写保存态）。
   - 修正：生涯侧改装配用 `setVariant(variant, true, true)`（带 refit CR 修正与 stats 重算）；战斗实体的修改要在部署侧做。
7. **CR 低到不能部署，战斗准备列表里没有船**。触发：刚经历战斗/缺补给后读 `getCombatReadyMembersListCopy()`。
   - 可观察：`getCR() < noDeployCRPercent`（默认 0）或 `getCrewFraction() < noDeployCrewPercent`（默认 5%）时 `canBeDeployedForCombat()` 返回 `false`，成员不进战斗准备列表（`settings.json`）。
   - 修正：先看 `getCR()` / `getCrewFraction()` 两个判据（船员不足也是原因）；CR 恢复靠补给与时间。
8. **封存了还在烧补给/还有容量**。触发：`setMothballed(true)` 后立刻读容量。
   - 可观察：封存修正（补给月耗 × `supplyConsumptionMothballedMult`、cargo/fuel/maxCrew/minCrew 乘数置 0）只在 `updateStats` 时写入；立即读取仍是旧值。
   - 修正：改完封存标志后触发一次同步（`CampaignFleetAPI.forceSync()` 或读 `getMembers()` 触发 `syncIfNeeded`）再读数。
9. **`setFlagship` 后其他成员舰长被重置**。触发：把 `setFlagship` 当成纯布尔标记写入。
   - 可观察：原旗舰（或舰长即玩家的成员）舰长被换成全 0 属性新人，新旗舰舰长 = 舰队指挥官（接口 javadoc 原文："Will also set the captains of all the other ships to a new person with all-0 stats."）。
   - 修正：接受该副作用（原版语义），不要用 `setFlagship` 逐成员手动调 `setCaptain` 对抗。
10. **演示日志出现但游戏内舰队没变化（或反过来）**。触发：在错误生命周期阶段调用（如 `onApplicationLoad` 里 `Global.getSector()` 为 null 即 NPE）。可观察：注册点放错 → 启动中止或脚本永不推进。修正：注册放 `onGameLoad` 守卫块内。

## 总结

本页把生涯舰队讲成一条链：**入口**（`SectorAPI.getPlayerFleet()` → `CampaignFleetAPI`）→ **集合**（`getFleetData()` → `FleetDataAPI`：槽位列表 + 同步视图 + 派生状态 + 瞬态快照）→ **成员**（`FleetMemberAPI`：身份 / 装配 / 舰长 / 状态 / 数值）。

记忆要点：

- **创建 ≠ 入队**：`FactoryAPI.createFleetMember` 造出保存态成员（装配已克隆、默认舰长、唯一 id），`FleetDataAPI.addFleetMember` 才入队；同引用重复添加被忽略、null 入队抛异常。
- **改集合要想到同步**：`addFleetMember` / `removeFleetMember` / `scuttle` 只置 `setSyncNeeded`；容量、fleet points、burn、stats 的重算发生在 `syncIfNeeded`（读成员列表即触发）或 `forceSync`。
- **四种视图各管一摊**：
  - `getMembersListCopy()` 含联队（每联队 1 条）；
  - `getCombatReadyMembersListCopy()` 可部署；
  - `getMembersInPriorityOrder()` 非封存优先；
  - `getMembersListWithFightersCopy()` 战机逐架展开。
- **保存态与战斗态分离**：`setVariant` 只改保存态；`getSnapshot()` 是瞬态；`isAlly()` 不随存档。
- **战斗准备 = CR + 船员**：`canBeDeployedForCombat()` 由 `noDeployCRPercent` / `noDeployCrewPercent` 决定；`getDeployCost()` 是 CR 占比（战机乘数量）。
- **后勤 = 容量求和 + 封存豁免**：cargo/fuel/crew 容量按成员求和；封存后不贡献容量、不烧船员、不进战斗准备，但修正在 `updateStats` 才生效。

这条对象链到此已经形成完整流程。顺着它继续向前：舰队坐标写入、任务与 AI 调度位于集合层之上，是围绕舰队实体的另一组 API 主题；舰队侧的市场补给来源则落在经济系统一侧——本节的成员读写语义是这两条支线的共同底座，后续遇到"给某支舰队加船、改装配、查部署状态"的需求时，都可以回到本节的三层链路上定位。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队实体；成员集合入口、统计、战斗绑定、inflater。
- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员集合；增删遍历、四种成员视图、同步与快照。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员保存态；身份、装配、舰长、状态、数值。
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体状态与维修（`getHullFraction` / `repairFully` / `applyHullFractionDamage`）。
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR 与封存（`getCR` / `setCR` / `setMothballed`）。
- `com.fs.starfarer.api.fleet.FleetMemberType`：成员类型枚举（`SHIP` / `FIGHTER_WING` / `NULL`）。
- `com.fs.starfarer.api.FactoryAPI`：成员创建入口（`createFleetMember`）。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：舰队 cargo 读取入口（`getCargo()`）。
- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配对象（`setVariant` 的入参类型；`getHullVariantId` 读装配 id）。
- `com.fs.starfarer.api.EveryFrameScript`：示例脚本接口（`isDone` / `runWhilePaused` / `advance`）。
- `data/config/settings.json`：部署判据基线（`noDeployCRPercent` = 0、`noDeployCrewPercent` = 5）。
- `data/variants/wolf/wolf_Assault.variant`、`data/variants/hammerhead/hammerhead_Elite.variant`：示例使用的原版装配（variant id = 文件名去扩展名）。
