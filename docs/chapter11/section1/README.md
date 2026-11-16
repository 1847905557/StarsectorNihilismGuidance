# 生成生涯舰队

把一支舰队造出来只要一次调用。造完之后它还不存在：不在任何地点里，没有任务，数量也没人维持。

`FleetFactoryV3` 把点数换成一堆船就结束了。投放、排任务、维持数量，各是另一套东西，哪一步漏了都不会报错，只会在游戏里表现成舰队不见了或者站着不动。

---

_本节预期阅读时间：\~14 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C7-S1] 的 `onGameLoad(...)` 与存档公共数据标记，管理器在那里登记；
- [C10-S1] 的 `EveryFrameScript`，管理器与任务脚本都是它的实现；
- [C3-S2] 的势力舰队配置，舰队的舰船从该势力的已知舰船中抽选。

## 舰队由哪些对象组成

生涯里的一支舰队是 `CampaignFleetAPI`。它同时是 `SectorEntityToken`，所以 [C10-S2] 讲过的轨道、位置、归属、传感器、Memory，在舰队上一并可用。

舰队自己不持有成员。成员列表在 `getFleetData()` 返回的 `FleetDataAPI` 上，那是一门完整的增删查改接口：

```java
FleetMemberAPI addFleetMember(FleetMemberAPI member);
FleetMemberAPI addFleetMember(String variantId);
void removeFleetMember(FleetMemberAPI member);
List<FleetMemberAPI> getMembersListCopy();
List<FleetMemberAPI> getCombatReadyMembersListCopy();
int getNumMembers();
```

**`addFleetMember(String variantId)` 返回新建的成员。** 只要装配 ID 就够，船名、ID 由游戏生成，拿到返回值再改。原版的 `EconomyFleetAssignmentAI` 建完立刻把成员封存，就是这么写的。

`CampaignFleetAPI` 上还有 `getFlagship()` 与 `setCommander(...)`，前者直接返回旗舰成员，后者指定舰队指挥官。**设了指挥官，旗舰仍是原来那艘，它的舰长也不会因此变成指挥官。**

`CampaignFleetAPI` 上没有 `getCargo()` 与 `getFuel()`。货物与燃料从 `getFleetData()` 与 `getLogistics()` 走。

## 任务队列

舰队做什么，由任务队列决定。队列挂在 `CampaignFleetAPI` 上，从只给任务与目标，到同时指定文字与开始、结束时的回调，参数逐层加：

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

参数各不相同：`maxDurationInDays` 是任务的最长持续天数，`actionText` 是玩家在舰队信息里看到的任务描述；两个 `Script` 分别在任务开始与结束时执行。

`FleetAssignment` 的常量按作用分几类：

| 用途 | 常量 | 玩家看到的前缀 | 是否附目标名 |
| --- | --- | --- | --- |
| 送货 | `DELIVER_RESOURCES`、`DELIVER_SUPPLIES`、`DELIVER_FUEL`、`DELIVER_PERSONNEL`、`DELIVER_CREW`、`DELIVER_MARINES` | Delivering ... to | 是 |
| 补给 | `RESUPPLY` | Resupplying at | 是 |
| 移动 | `GO_TO_LOCATION` | Travelling | 否 |
| 驻守 | `PATROL_SYSTEM` | Patrolling system | 否 |
| 防守 | `DEFEND_LOCATION` | Defending | 否 |
| 环绕 | `ORBIT_PASSIVE`、`ORBIT_AGGRESSIVE` | Orbiting | 是 |
| 进攻 | `ATTACK_LOCATION` | Attacking | 是 |
| 袭击 | `RAID_SYSTEM` | Raiding | 否 |
| 撤离 | `GO_TO_LOCATION_AND_DESPAWN` | Returning to | 是 |
| 拦截 | `INTERCEPT` | Intercepting | 是 |
| 跟随 | `FOLLOW` | Following | 是 |
| 待命 | `HOLD` | Holding | 否 |
| 收队 | `STANDING_DOWN` | Standing down | 否 |

前缀与目标名决定了 `actionText` 为空时玩家看到什么。给了 `actionText`，那行字就整个换成它，前缀不出现。

**有一批常量在原版没有调用点。** `DELIVER_SUPPLIES`、`DELIVER_FUEL`、`DELIVER_MARINES`、`RESUPPLY`、`ATTACK_LOCATION` 在原版 `impl/` 下一个 `addAssignment(...)` 都没有，前四个只在 `SustainedBurnAbilityAI` 里被当作判断条件比较过。写给 NPC 用的任务，能参照的原版写法集中在 `GO_TO_LOCATION`、`ORBIT_PASSIVE`、`ORBIT_AGGRESSIVE`、`PATROL_SYSTEM`、`DEFEND_LOCATION`、`INTERCEPT`、`RAID_SYSTEM`、`FOLLOW`、`HOLD`、`GO_TO_LOCATION_AND_DESPAWN` 与 `STANDING_DOWN` 上。

**队列走完，舰队就没有任务了。** 被打断也会走完：与玩家交战之后，原版会清空任务队列。所以任务脚本要在 `advance(...)` 里检查 `getCurrentAssignment() == null`，为空时重排一轮。

## 两支舰队怎么造出来

造舰队有两条路，选哪条看出这支舰队该不该由游戏来管数量。

要自己决定这支舰队什么时候出现，用 `FleetFactoryV3.createFleet(...)`。参数是一份 `FleetParamsV3`，填势力、坐标、舰队类型与点数，工厂按该势力的已知舰船与学说抽选：

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

**它可能返回 `null`。** 势力没有可用舰船、点数不够凑出一艘，都会如此。判空之后再往下走。

要让游戏替你管数量，继承 `SourceBasedFleetManager`。它是一个 `EveryFrameScript`，构造时给定源点、距离阈值、最少与最多舰队数、补充间隔，自己维护数量：

- 玩家离源点越近，允许存在的舰队数越接近最大值，离得远则降到最小值；
- 舰队被击毁后，按补充间隔逐步恢复；
- 数量超出允许值时，在玩家看不见的地方移除多余的舰队。

子类只需实现 `spawnFleet()`：造一支舰队，加入地点，返回它。

**原版继承它的只有 `RemnantStationFleetManager` 与 `RogueMinerMiscFleetManager`。** 前者的构造参数实际传的是 `(station, 1f, 0, maxFleets, 25f, 6, 12)`，后者的父类调用是 `super(source, 3f, 0, 5, 5f)`。想照着抄，只有这两份可看。

## 舰队怎么进世界

造出来的舰队还不在任何地点里，两种投放方式对应上面两条路。

```java
location.addEntity(fleet);
fleet.setLocation(source.getLocation().x, source.getLocation().y);
```

`addEntity(...)` 是加入地点，`setLocation(...)` 是给坐标。**缺一不可**：只调前者，舰队出现在原点；只调后者，舰队不属于任何地点，游戏不会处理它。

`spawnFleet(...)` 是另一回事。它属于 `LocationAPI` 与各类管理器，不是 `CampaignFleetAPI` 的方法，作用是在地点里按生成规则产出一支舰队。原版真正把舰队放进世界的地方，用的都是 `addEntity(...)`。

上面这些是舰队层的东西。下面用它们做一支巡逻队。

### 巡逻行为的标记

舰队的 AI 读取舰队自己的 Memory 来决定行为，这四个标记都来自 `MemFlags`：

```java
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
```

- `$isPatrol`：标明这是巡逻队，原版的拦截与盘查逻辑据此判断；
- `$cfai_longPursuit` 为 `false`：不做长距离追击，追出巡逻范围就放弃；
- `$cfai_noJump` 为 `true`：不跳出所在星系，巡逻队不该跟着玩家进超空间；
- `$cfai_holdVsStronger` 为 `true`：遇到明显更强的对手时原地固守，不去送死。

这几项让一支小巡逻队守在自己的轨道上，不去追杀玩家。要做成追击型的舰队，把前两项改回默认即可。

## 安排巡逻任务

管理器只负责数量，舰队做什么由任务脚本决定。脚本构造时就给出一串任务，舰队依次执行：

```java
fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, source, 2f, "preparing for patrol");
fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, null, PATROL_DAYS, "patrolling");
fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 2f, "standing down");
fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 5f);
```

- 先在源点轨道上停留 2 天，玩家靠近时能看到它出港前的样子；
- 在所在星系巡逻 `PATROL_DAYS` 即 8 天，目标为 `null` 表示不指定地点；
- 回到源点轨道停留 2 天；
- 最后飞向源点并解散，舰队从世界中移除。

`maxDurationInDays` 是任务的最长持续天数，`actionText` 是玩家在舰队信息里看到的任务描述。

**任务队列走完，舰队就没有任务了。** 脚本的 `advance(...)` 检查 `getCurrentAssignment() == null`，为空时重新安排一轮。这是原版 `RemnantAssignmentAI` 的做法。没有这一步，舰队在被打断 (例如与玩家交战后)、任务被清空时会停在原地不动。

`isDone()` 在舰队不再存活时返回 `true`，脚本随之被引擎清除，不会留下指向已移除舰队的引用。

### 源点与 Foundry

本例的源点是 Nipher Prime 的行星实体，登记时按 ID `GearIn_planet1` 取得。[C12-S1] 建立 Foundry 之后，把登记处的实体换成 Foundry 即可，管理器和任务脚本都不必修改。

## 登记管理器

管理器是随存档保存的脚本，用 `sector.addScript(...)` 登记，**只登记一次**。重复登记会让每次读档都多出一个管理器，舰队数量成倍增加：

```java
if (!sector.getPersistentData().containsKey(PATROL_ADDED_KEY)) {
    SectorEntityToken prime = sector.getEntityById("GearIn_planet1");
    if (prime != null) {
        sector.addScript(new GearInPatrolFleetManager(prime));
        sector.getPersistentData().put(PATROL_ADDED_KEY, true);
    }
}
```

这里不能用 [C10-S1] 的 `addTransientScript(...)`：管理器记着当前有哪些舰队，这份状态必须随存档保存。标记 `GearIn_patrol_manager_added` 写在存档公共数据里，保证登记只发生一次。

## 完整的巡逻队

`src/data/scripts/gearin/campaign/GearInPatrolFleetManager.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 齿轮工业巡逻舰队管理器：从源点出动，在所在星系巡逻，被击毁后按间隔补充。 */
public class GearInPatrolFleetManager extends SourceBasedFleetManager {

    /** 玩家离源点超过这个距离 (光年) 时，数量降到最小值。 */
    private static final float THRESHOLD_LY = 3f;

    private static final int MIN_FLEETS = 1;
    private static final int MAX_FLEETS = 2;

    /** 被击毁后补充一支舰队所需的生涯天数。 */
    private static final float RESPAWN_DAYS = 20f;

    private static final float COMBAT_POINTS = 30f;

    /** 巡逻队旗舰的装配 ID，沿用 C3-S2 配置的工匠。 */
    private static final String FLAGSHIP_VARIANT = "GearIn_artizan_combat";

    /** 指挥官等级。 */
    private static final int COMMANDER_LEVEL = 4;

    /** 僚舰军官等级。 */
    private static final int OFFICER_LEVEL = 2;

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
        params.withOfficers = false;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) {
            return null;
        }

        // 旗舰与指挥官由我们自己指定。setFlagship 会重置其他成员的舰长，所以放在最前面
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
        flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random));
        fleet.getFleetData().addFleetMember(flagship);
        fleet.getFleetData().setFlagship(flagship);
        PersonAPI commander = createCommander(random);
        fleet.setCommander(commander);
        flagship.setCaptain(commander);
        addWingOfficer(fleet, flagship, random);
        fleet.forceSync();

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

    /** 创建巡逻队指挥官：等级与技能由原版抽选，姓名、性格与职务自行指定。 */
    private PersonAPI createCommander(Random random) {
        PersonAPI person = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                COMMANDER_LEVEL, SkillPickPreference.ANY, random);
        person.setName(new FullName("Brann", "Hale", FullName.Gender.ANY));
        person.setPersonality(Personalities.AGGRESSIVE);
        person.setRankId(Ranks.SPACE_COMMANDER);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);
        return person;
    }

    /** 给点数最高的非旗舰成员配一名军官：先登记进舰队的军官名单，再派到这艘舰船上。 */
    private void addWingOfficer(CampaignFleetAPI fleet, FleetMemberAPI flagship, Random random) {
        FleetMemberAPI target = null;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == flagship || member.isFighterWing() || member.isCivilian()) {
                continue;
            }
            if (target == null || member.getFleetPointCost() > target.getFleetPointCost()) {
                target = member;
            }
        }
        if (target == null) {
            return;
        }
        PersonAPI officer = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                OFFICER_LEVEL, SkillPickPreference.ANY, random);
        officer.setPostId(Ranks.POST_OFFICER);
        fleet.getFleetData().addOfficer(officer);
        target.setCaptain(officer);
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 控制台执行 `jump Nipher`，在 Nipher Prime 轨道附近可以看到齿轮工业的巡逻舰队，数量为 1 至 2 支；
- 舰队信息中的任务描述依次为 `preparing for patrol`、`patrolling`，之后是 `standing down` 与 `Returning to`；
- 将这支舰队击毁，约 20 个生涯天之后补充出新的舰队；
- 保存并重新读档，舰队数量没有成倍增加，管理器只有一个。

## 常见问题

- **轨道上始终没有舰队**：管理器没有登记，或者 `createFleet(...)` 返回了 `null`。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记与标记；确认势力有可用舰船，并适当提高战斗点数。

- **舰队出现在星系中心而不是行星轨道**：只调用了 `addEntity(...)`，没有设置位置。
    - **解决方案**：在 `addEntity(...)` 之后调用 `setLocation(...)`。

- **每次读档舰队数量都增加**：管理器被重复登记，或者用 `addTransientScript(...)` 登记。
    - **解决方案**：用 `addScript(...)` 登记，并以存档公共数据中的标记保证只登记一次。

- **舰队停在原地不动**：任务队列走完或被清空后，没有重新安排任务。
    - **解决方案**：在 `advance(...)` 中检查 `getCurrentAssignment() == null` 并重新安排。

- **旧存档中没有巡逻队**：旧存档没有 Nipher Prime 的实体，登记处取到 `null`。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager`：数量、补充与移除的完整逻辑。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`：全部生成字段，本节只用了其中的点数与类型。
- `com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantAssignmentAI`：原版的任务重排写法。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：舰队行为标记的全部取值。
- `com.fs.starfarer.api.campaign.FleetAssignment`：各任务类型的含义。
