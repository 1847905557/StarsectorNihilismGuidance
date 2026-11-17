# 生成生涯舰队

`FleetFactoryV3` 把战斗点数换成一堆船体以后，工作就算结束了。至于这支舰队待在哪个星系、挂在谁的轨道上、队列走空了去干什么、全灭之后什么时候再凑出一批船来，得由管理器和任务脚本各自接过去。

想让巡逻队在 Nipher 轨道上长期巡逻下去，投放、排任务和维持数量这三步一个也不能少。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S2] 的势力舰队角色与选船权重配置；
- [C7-S1] 的 `onGameLoad(...)` 与存档公共数据标记；
- [C10-S1] 的 `EveryFrameScript` 与 `sector.addScript(...)` 持久脚本注册；
- [C10-S2] 的 `MemoryAPI` 与 `getMemoryWithoutUpdate()`。

## 舰队对象与成员列表入口

生涯里的一支舰队是 `CampaignFleetAPI`。它继承自 `SectorEntityToken`，因此 [C10-S2] 的 Memory 与 [C10-S5] 的位置、朝向、归属势力、传感器轮廓等方法在舰队上一并可用。

舰队对象自身并不直接提供增删单艘舰船的方法；整支舰队的舰船与军官名册挂在 `fleet.getFleetData()` 返回的 `FleetDataAPI` 上 ([C11-S2] 将专门展开成员与军官管理)：

```java
FleetDataAPI data = fleet.getFleetData();
List<FleetMemberAPI> members = data.getMembersListCopy();
int count = data.getNumMembers();
```

同样，`CampaignFleetAPI` 上也没有直接取燃料或货舱的入口，货舱走 `fleet.getCargo()`，后勤相关数据则走 `fleet.getFleetData()` 与 `fleet.getLogistics()`。

## 任务队列与行为中断

舰队在星图上的航向与行动目标，由挂在 `CampaignFleetAPI` 上的任务队列 (`FleetAssignment`) 决定。`addAssignment(...)` 提供了从极简到带起止回调的多个重载：

```java
void addAssignment(FleetAssignment assignment, SectorEntityToken target, float maxDurationInDays);
void addAssignment(FleetAssignment assignment, SectorEntityToken target, float maxDurationInDays,
        Script onCompletion);
void addAssignment(FleetAssignment assignment, SectorEntityToken target, float maxDurationInDays,
        String actionText);
void addAssignment(FleetAssignment assignment, SectorEntityToken target, float maxDurationInDays,
        String actionText, Script onCompletion);
void addAssignment(FleetAssignment assignment, SectorEntityToken target, float maxDurationInDays,
        String actionText, boolean addTimeToNext, Script onStart, Script onCompletion);
```

其中 `maxDurationInDays` 是该项任务的最长持续生涯天数，`actionText` 是玩家把鼠标悬停在舰队上时看到的行动描述文本，`onStart` 与 `onCompletion` 则是在该条任务开始和完成瞬间执行的 `Script` 回调。

常用 `FleetAssignment` 枚举如下：

| 类别 | 枚举常量 | 默认提示前缀 | 是否自动拼接目标名 |
| --- | --- | --- | --- |
| 移动 | `GO_TO_LOCATION` | Travelling | 否 |
| 撤离解散 | `GO_TO_LOCATION_AND_DESPAWN` | Returning to | 是 |
| 星系巡逻 | `PATROL_SYSTEM` | Patrolling system | 否 |
| 定点防守 | `DEFEND_LOCATION` | Defending | 否 |
| 轨道环绕 | `ORBIT_PASSIVE`、`ORBIT_AGGRESSIVE` | Orbiting | 是 |
| 进攻与劫掠 | `ATTACK_LOCATION`、`RAID_SYSTEM` | Attacking / Raiding | 是 / 否 |
| 拦截与跟随 | `INTERCEPT`、`FOLLOW` | Intercepting / Following | 是 |
| 原地待命 | `HOLD`、`STANDING_DOWN` | Holding / Standing down | 否 |
| 货运投递 | `DELIVER_RESOURCES`、`DELIVER_PERSONNEL`、`DELIVER_CREW` | Delivering ... to | 是 |

注：`FleetAssignment` 中还有 `DELIVER_SUPPLIES`、`DELIVER_FUEL`、`DELIVER_MARINES` 与 `RESUPPLY` 等常量，但在原版 `impl/` 源码中没有任何调用点向 `addAssignment(...)` 传入过它们 (仅在 `SustainedBurnAbilityAI` 中被拿来做过条件判断)，编写自定义舰队任务时使用上表列出的常规枚举即可。

**任务队列不仅会在按天走完时变空，还可能在遭遇玩家或触发原版战术模块后提前出队而归零。** 因此任何需要长期维持行动的舰队，都不能只在生成时塞一次任务了事，必须挂一个 `EveryFrameScript` 在 `advance(...)` 中检查 `fleet.getCurrentAssignment() == null` —— 一旦发现当前没有任务，就重新排入下一轮巡逻队列。

## 单次生成与源点数量管理器

造舰队有两条路线，取决于您希望生成一支一次性事件舰队，还是维持一批会死而复生的常驻舰队。

### 用 FleetFactoryV3.createFleet(...) 单次生成

无论是哪种路线，把战斗点数变成一支实际编制的底层入口都是 `FleetFactoryV3.createFleet(FleetParamsV3)`：

```java
FleetParamsV3 params = new FleetParamsV3(
        null,
        source.getLocationInHyperspace(),
        GearInModPlugin.FACTION_ID,
        1f,
        FleetTypes.PATROL_SMALL,
        COMBAT_POINTS,
        0f, 0f, 0f, 0f, 0f,
        0f);
params.random = random;
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
```

构造参数依次指定市场 (无市场源点时传 `null`)、超空间坐标、势力 ID、装配质量系数 (`1f` 为标准质量)、舰队类型 (`FleetTypes.PATROL_SMALL`)，以及战舰、货船、油船、运兵船、客船、功能船六类点数与额外质量加成。工厂会按 [C3-S2] 在 `.faction` 文件中配置的选船权重与学说自动抽船、分配指挥官与军官。

**调用 `FleetFactoryV3.createFleet(...)` 后必须检查 `fleet == null || fleet.isEmpty()`。** 当势力没有可用船体蓝图，或给定点数凑不出最小舰船时，工厂在 `0.98.5a` 中返回的是一支**成员数为 0 的空舰队**而不是 `null` (`null` 只在自定义 `CreateFleetPlugin` 拦截返回空时出现)，两者都要一并拦截。

### 用 SourceBasedFleetManager 维持常驻数量

如果希望围绕某个据点 (如行星或空间站) 长期维持一定数量的巡逻队，直接继承原版的 `SourceBasedFleetManager`：

```java
public GearInPatrolFleetManager(SectorEntityToken source) {
    super(source, THRESHOLD_LY, MIN_FLEETS, MAX_FLEETS, RESPAWN_DAYS);
}
```

`SourceBasedFleetManager` 本身实现了 `EveryFrameScript` 与 `FleetEventListener`，替您包揽了所有繁琐的数量调度：

- 当玩家舰队距离源点 `source` 不超过 `THRESHOLD_LY` (光年) 时，管理器将活跃舰队数补充到 `MAX_FLEETS`；当玩家远离源点超过阈值后，在视野外悄然撤走多余舰队，只保留 `MIN_FLEETS` 支以节省全局开销；
- 某支下属舰队在战斗中被击毁 (`DESTROYED_BY_BATTLE`) 后，会按 `RESPAWN_DAYS` 个生涯日逐步恢复补充名额；若只是按任务返港解散 (`REACHED_DESTINATION`)，则只从活跃列表中移除，在后续帧按目标上限直接补回。

原版余辉空间站的 `RemnantStationFleetManager` 与流亡矿工的 `RogueMinerMiscFleetManager` 均直接继承自该基类。

## 投放进地点与巡逻行为标记

`FleetFactoryV3.createFleet(...)` 返回的舰队还没有放入任何地点。在 `spawnFleet()` 中必须成对调用 `addEntity(...)` 与 `setLocation(...)`：

```java
source.getContainingLocation().addEntity(fleet);
fleet.setLocation(source.getLocation().x, source.getLocation().y);
fleet.setFacing(random.nextFloat() * 360f);
```

**两行缺一不可：** 只调 `addEntity(...)` 不设坐标，舰队会直接刷在星系中心的 `(0, 0)` 原点 (要是有恒星就直接泡进日冕里了)；只调 `setLocation(...)` 不调 `addEntity(...)`，舰队根本不在星图场景中，玩家永远看不见它。

接着向舰队自身的 Memory 写入原版 `MemFlags` 行为标记，约束巡逻 AI 的行为模式：

```java
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
```

- `MEMORY_KEY_PATROL_FLEET` (`$isPatrol`)：标明这是一支正规巡逻队，使原版的应答机盘查与走私搜查逻辑生效，后续章节 ([C14-S2]、[C14-S4]) 也靠这个标记识别齿轮工业巡逻队；
- `MEMORY_KEY_ALLOW_LONG_PURSUIT` 设为 `false`：禁止死追不放，目标逃出巡逻防区后即放弃追击；
- `MEMORY_KEY_NO_JUMP` 设为 `true`：禁止跳入超空间，确保巡逻队始终留在 Nipher 星系内；
- `MEMORY_KEY_MAKE_HOLD_VS_STRONGER` 设为 `true`：遭遇明显强于自身的敌方舰队时就地固守，而不是莽撞冲上去白给。

## 完整的巡逻队管理器与任务脚本

本节包含两个配套类：`GearInPatrolFleetManager` 负责围绕 Nipher Prime (`GearIn_planet1`) 维持 1 至 2 支 30 点的巡逻队；`GearInPatrolAssignmentScript` 挂在每支生成的舰队上，负责按 "出港准备 2 天 → 星系巡逻 8 天 → 返港休整 2 天 → 进港解散" 的顺序循环排程。

### 巡逻队管理器

`src/data/scripts/gearin/campaign/GearInPatrolFleetManager.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import data.scripts.gearin.GearInModPlugin;

/** 齿轮工业巡逻舰队管理器：从源点派出，在所在星系巡逻，被击毁后按间隔补充。 */
public class GearInPatrolFleetManager extends SourceBasedFleetManager {

    /** 玩家离源点超过这个距离 (光年) 时，数量降到最小值。 */
    private static final float THRESHOLD_LY = 3f;

    private static final int MIN_FLEETS = 1;
    private static final int MAX_FLEETS = 2;

    /** 被击毁后补充一支舰队所需的生涯天数。 */
    private static final float RESPAWN_DAYS = 20f;

    private static final float COMBAT_POINTS = 30f;

    public GearInPatrolFleetManager(SectorEntityToken source) {
        super(source, THRESHOLD_LY, MIN_FLEETS, MAX_FLEETS, RESPAWN_DAYS);
    }

    @Override
    protected CampaignFleetAPI spawnFleet() {
        if (source == null) {
            return null;
        }
        Random random = new Random();
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                GearInModPlugin.FACTION_ID,
                1f,
                FleetTypes.PATROL_SMALL,
                COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        source.getContainingLocation().addEntity(fleet);
        fleet.setLocation(source.getLocation().x, source.getLocation().y);
        fleet.setFacing(random.nextFloat() * 360f);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);

        fleet.addScript(new GearInPatrolAssignmentScript(fleet, source));
        return fleet;
    }
}
```

### 巡逻任务排程脚本

`src/data/scripts/gearin/campaign/GearInPatrolAssignmentScript.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/** 巡逻舰队的任务安排：出港后巡逻，随后返回源点并解散。 */
public class GearInPatrolAssignmentScript implements EveryFrameScript {

    private static final float PATROL_DAYS = 8f;

    private final CampaignFleetAPI fleet;
    private final SectorEntityToken source;

    public GearInPatrolAssignmentScript(CampaignFleetAPI fleet, SectorEntityToken source) {
        this.fleet = fleet;
        this.source = source;
        assign();
    }

    private void assign() {
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, source, 2f, "preparing for patrol");
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, null, PATROL_DAYS, "patrolling");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 2f, "standing down");
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 5f);
    }

    @Override
    public boolean isDone() {
        return !fleet.isAlive();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (fleet.getCurrentAssignment() == null) {
            assign();
        }
    }
}
```

### 在入口类中登记管理器

`SourceBasedFleetManager` 内部维护着已派出舰队的列表与重生倒计时，这份状态必须随存档持久化保存。因此在 `GearInModPlugin.onGameLoad(...)` 中必须使用 [C10-S1] 讲过的 `sector.addScript(...)` 持久注册，并配合 `sector.getPersistentData()` 中的标记确保整个存档只注册一次：

```java
if (!sector.getPersistentData().containsKey("GearIn_patrol_manager_added")) {
    SectorEntityToken prime = sector.getEntityById("GearIn_planet1");
    if (prime != null) {
        sector.addScript(new GearInPatrolFleetManager(prime));
        sector.getPersistentData().put("GearIn_patrol_manager_added", true);
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 在控制台执行 `jump Nipher`，飞近 Nipher Prime，可以看到轨道附近生成了 1 至 2 支齿轮工业巡逻队；
- 悬停查看舰队信息，行动描述依次从 `preparing for patrol` 切换到 `patrolling`、`standing down` 与 `返回 Nipher Prime`；
- 保存并重新读档，巡逻队数量保持不变，不会因读档而翻倍。

## 常见问题

- **进入 Nipher 星系后始终看不到巡逻队**：`onGameLoad(...)` 中未登记管理器，或 `.faction` 文件中没有配置可用的战斗舰船角色导致 `FleetFactoryV3.createFleet(...)` 返回了空舰队。
    - **解决方案**：核对 `onGameLoad(...)` 注册逻辑与 [C3-S2] 的势力选船配置。

- **巡逻队生成在了恒星正中央而非 Nipher Prime 轨道**：只调用了 `addEntity(fleet)`，漏掉了 `fleet.setLocation(...)`。
    - **解决方案**：`addEntity(...)` 后紧接着将舰队坐标设为源点的 `getLocation()`。

- **每次保存读档，星系里的巡逻队数量就翻一倍**：在 `onGameLoad(...)` 中调用 `sector.addScript(new GearInPatrolFleetManager(...))` 时没有用 `getPersistentData()` 标记防重。
    - **解决方案**：持久脚本会随存档自动恢复，必须通过 `getPersistentData()` 标记保证只在首次加载时添加一次。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：生涯舰队实体、任务队列与 `getFleetData()` 入口。
- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager`：基于源点距离与重生间隔的舰队数量管理器基类。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`：舰队工厂参数结构。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：`createFleet(...)` 抽选舰船与自动配置军官的实现。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：控制舰队 AI 行为的常用 Memory 键名常量。
- `com.fs.starfarer.api.campaign.FleetAssignment`：舰队任务类型枚举。
