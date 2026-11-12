# 舰队状态的重算过程

装配里增加一个幅能电容，成员的数值会随之重算；此前取得的 `stats` 却可能已经被替换。本节借一艘临时加入的狼说明这次变化，再为舰队添加短期航速效果、写入带期限的 Memory，分别确认数值的来源与状态的寿命。

_本节预期阅读时间：\~24 分钟。_

_本节预期操作时间：\~28 分钟。_

## 学习目标

完成后，您将能够：

- 解释成员 `stats` 的来源和重算顺序；
- 区分立即重算、延迟重算和旧引用失效；
- 观察舰队汇总与成员 `stats` 的不同职责；
- 覆盖全部 `FLEET` 标记并恢复临时 Memory。

## 前置知识

- [C11-S2] 的舰队成员集合；
- [C8-S2] 的 `MutableStat` 和来源 ID。

## 成员数值与舰队数值分别从哪里取得

生涯中的舰船保存在 `FleetMemberAPI` 中，它的 `getStats()` 返回单舰的 `MutableShipStatsAPI`；整支舰队的 `getStats()` 返回 `MutableFleetStatsAPI`，参与移动、传感器和后勤计算。单舰战斗速度与舰队地图航速从不同位置读取，下面是局部观察片段：

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetMemberAPI member = fleet.getFleetData().getMembersListCopy().get(0);
MutableShipStatsAPI memberStats = member.getStats();
MutableFleetStatsAPI fleetStats = fleet.getStats();

LOG.info("GearIn: member=" + member.getId()
        + " memberSpeed="
        + memberStats.getMaxSpeed().getModifiedValue()
        + " fleetBurn="
        + fleet.getFleetData().getBurnLevel()
        + " fleetSensor="
        + fleetStats.getSensorRangeMod().computeEffective(
                fleet.getSensorStrength()));
```

`memberSpeed` 是这艘船的战斗速度，`fleetBurn` 是舰队的有效航速等级，`fleetSensor` 则把舰队传感器修饰应用到当前传感器强度。读取时先确定所需结果属于舰船还是舰队，才能选对对象。

## 改变装配后重新取得 stats

本节的 `GearInFleetStatsChain` 临时创建一艘 `wolf_Assault`，加入玩家舰队后克隆其装配。克隆上增加一个幅能电容，随后通过 `setVariant(...)` 写回成员。下面是这一操作的局部片段：

```java
ShipVariantAPI cloned = member.getVariant().clone();
int capacitorsBefore = cloned.getNumFluxCapacitors();
cloned.setNumFluxCapacitors(capacitorsBefore + 1);

MutableShipStatsAPI statsBefore = member.getStats();
member.setVariant(cloned, false, true);
MutableShipStatsAPI statsAfter = member.getStats();

LOG.info("GearIn: capacitors=" + capacitorsBefore
        + "->" + cloned.getNumFluxCapacitors()
        + " statsReplaced=" + (statsBefore != statsAfter));
```

`setVariant(variant, withRefit, withStatsUpdate)` 的第二个参数控制改装差异对 CR 的处理，第三个参数控制本次是否立即重算。这里传入 `false, true`，应用新装配并立即重建数值；连续修改多项装配内容时，可以在副本上完成修改后统一写回。

**重算会建立新的 `stats`，后续操作从成员重新取得它。** 因此日志比较 `statsBefore != statsAfter`，确认的是对象替换。新增电容影响幅能容量，日志里的速度与航速仍可以保持原值；有些数值没变，并不意味着重算没有发生。

传入 `withStatsUpdate=false` 时，装配已经写回，派生数值却未在这次调用中重算。需要立即使用结果时，调用 `member.updateStats()`，再取得 `member.getStats()`。同时，已有的待更新标记也可能使 `getStats()` 在读取时完成重算。

::: warning stats 引用不能跨重算保存
成员已经换用新对象后，旧引用仍然是合法的 Java 对象。向它写入修饰可以正常执行，成员当前数值却不会改变。
:::

## 哪些效果会在重算中重新应用

成员重算先从船体与装配建立数值，再应用舰长个人技能、指挥官的舰队技能和船插，加入幅能配置，并处理船员、CR、封存等状态。成员进入舰队后，人物效果才具有所需的舰队上下文。舰队完整同步还会重算成员、应用舰队效果并更新汇总。

这也说明了持续效果应放在哪里。直接向某个 `stats` 实例写入的修饰，属于那个实例；船插和技能在正式回调中提供的修饰，则会随着重算再次应用。[C9-S1] 的 `applyEffectsBeforeShipCreation(...)` 正承担这项职责。**持续效果要有重算时能够重新找到的来源。** 即使一次修改已经在界面上显示，来源缺失仍会在下次重算时暴露。

### 状态来源速查表

| 来源      | 进入位置                      | 观察对象         |
| --------- | ----------------------------- | ---------------- |
| 船体规格  | 成员创建和 `stats` 初始化     | 基值             |
| 舰船装配  | `setVariant` 与 `updateStats` | 成员 `stats`     |
| 舰长      | 成员归属后的重算              | 当前成员         |
| 指挥官    | 成员与舰队完整重算            | 多个成员         |
| 舰队汇总  | 舰队级 `stats` 与成员汇总入口 | 移动和后勤结果   |
| CR 与船员 | `RepairTrackerAPI` 和成员状态 | 可部署性和修正值 |

### 对应舰长与指挥官

下面取得当前舰长与舰队指挥官，记录这次重算使用的人物：

```java
PersonAPI captain = member.getCaptain();
PersonAPI commander = fleet.getCommander();
LOG.info("captain=" + (captain == null ? "none" : captain.getNameString())
        + " commander=" + (commander == null ? "none" : commander.getNameString()));
member.updateStats();
```

舰长提供个人效果，指挥官提供舰队效果，技能还要满足各自的作用域与等级条件。人物创建和技能定义由后面的专题展开，这里先确认参与重算的人物。船体与装配也有自己的来源，人物效果是否适用要单独判断。

从成员还可以取得它所属舰队的指挥官。下面的局部片段适合与成员归属一起记录：

```java
PersonAPI captain = member.getCaptain();
PersonAPI fleetCommander = member.getFleetCommander();

LOG.info("GearIn: captain="
        + (captain == null ? "none" : captain.getNameString())
        + " fleetCommander="
        + (fleetCommander == null
                ? "none"
                : fleetCommander.getNameString()));
```

## 给舰队添加有期限的航速效果

舰队汇总在成员与人物变化后重新读取。以下片段继续观察 FP、有效航速与传感器结果：

```java
MutableFleetStatsAPI fleetStats = fleet.getStats();
LOG.info("fleetPoints=" + fleet.getFleetPoints()
        + " burn=" + fleet.getFleetData().getBurnLevel()
        + " sensor=" + fleetStats.getSensorRangeMod().computeEffective(
                fleet.getSensorStrength()));
```

如果只是给舰队短暂提高航速，可以使用舰队的临时修饰。`addTemporaryModFlat(...)`、`addTemporaryModPercent(...)` 与 `addTemporaryModMult(...)` 都接受以生涯日为单位的有效期。本例给舰队范围最大航速加 2，持续半日，使用来源 ID `GearIn_FleetStatsProbe`：

```java
MutableFleetStatsAPI stats = fleet.getStats();
String source = "GearIn_FleetStatsProbe";

stats.addTemporaryModFlat(
        0.5f,
        source,
        "GearIn movement probe",
        2f,
        stats.getFleetwideMaxBurnMod());

LOG.info("GearIn: hasMod=" + stats.hasMod(source)
        + " burn="
        + fleet.getFleetData().getBurnLevel());

stats.removeTemporaryMod(source);
```

`getBurnLevel()` 合并了这项舰队航速修饰，日志可以直接读取有效结果。这个局部实验随后调用 `removeTemporaryMod(source)` 主动移除；实际功能也可以让它随生涯时间自动到期，暂停期间有效期停止推进。

临时修饰的来源 ID 同时登记目标属性和期限。重复写入同一目标时，会更新修饰并重新设置期限；**同一个来源 ID 不能用于另一项属性，否则会抛出异常。** 需要同时提高航速和传感器范围时，为两项属性各用一个来源。移除后用 `hasMod(source)` 核对记录，最终数值还可能受到其他效果影响。

## 写入并恢复舰队 Memory

临时数值效果有自己的登记入口，AI 状态则常通过舰队的 `MemoryAPI` 保存。Memory 用以 `$` 开头的字符串作键：`set(key, value)` 写入无期限值，三参数重载附带生涯日期限；`contains(...)` 判断键是否存在，`get(...)` 取得原对象，`unset(...)` 立即移除键。

本例用 `getMemoryWithoutUpdate()` 取得舰队 Memory，临时写入原版的 `FLEET_BUSY`。这个键已经可能由别的功能使用，所以先保存存在状态、原值与剩余期限：

```java
String key = MemFlags.FLEET_BUSY;
MemoryAPI memory = fleet.getMemoryWithoutUpdate();

boolean hadKey = memory.contains(key);
Object oldValue = memory.get(key);
float oldExpire = hadKey ? memory.getExpire(key) : 0f;

memory.set(key, true, 0.25f);
LOG.info("GearIn: contains=" + memory.contains(key)
        + " busy=" + memory.getBoolean(key)
        + " expire=" + memory.getExpire(key));

if (hadKey) {
    memory.set(key, oldValue, oldExpire);
} else {
    memory.unset(key);
}
```

`getExpire(...)` 返回剩余生涯日，-1 表示没有期限。`getBoolean`、`getFloat`、`getLong` 和 `getInt` 按所需类型读取，`get()` 则用于保留原对象。**原来没有键与原值为 `false` 是两种状态，恢复时分别处理。** 原键存在时用三参数 `set(...)` 写回，缺席时调用 `unset(...)`。

完整的 `GearInFleetMemoryProbe` 对 `FLEET_BUSY` 和 `FLEET_PATROL_DISTANCE` 都做这项操作，写入期限为 0.25 日，并在同一轮恢复。因此日志验证的是写入和恢复；如果要观察自然过期，还要让标记保留到生涯时间超过有效期。暂停时剩余期限也停止减少。

### `FLEET` 标记速查表

这些键由原版 AI 或交互功能读取，值的类型由读取方决定。Memory 本身只保存对象，写入键后发生什么，要结合消费它的功能判断。

| 常量                                           | 键值                            | 保存的值                                        | 作用                                     |
| ---------------------------------------------- | ------------------------------- | ----------------------------------------------- | ---------------------------------------- |
| `FLEET_DO_NOT_IGNORE_PLAYER`                   | `$cfai_doNotIgnorePlayer`       | `boolean`                                       | 让 AI 考虑玩家                           |
| `FLEET_IGNORES_OTHER_FLEETS`                   | `$cfai_ignoreOtherFleets`       | `boolean`                                       | 让舰队忽略其他舰队                       |
| `FLEET_IGNORED_BY_OTHER_FLEETS`                | `$cfai_ignoredByOtherFleets`    | `boolean`                                       | 让其他舰队忽略当前舰队                   |
| `FLEET_IGNORED_BY_FACTION`                     | `$cfai_ignoredByFaction`        | 势力 ID 字符串                                  | 指定忽略当前舰队的势力                   |
| `FLEET_PATROL_DISTANCE`                        | `$cfai_patrolDist`              | `float`                                         | 提供巡逻距离                             |
| `FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN` | `$fidConifgGen`                 | `FleetInteractionDialogPluginImpl.FIDConfigGen` | 创建舰队交互配置                         |
| `FLEET_NO_MILITARY_RESPONSE`                   | `$core_fleetNoMilitaryResponse` | `boolean`                                       | 阻止舰队参加军事响应                     |
| `FLEET_FIGHT_TO_THE_LAST`                      | `$core_fightToTheLast`          | `boolean`                                       | 改变战斗中的撤退判断                     |
| `FLEET_BUSY`                                   | `$core_fleetBusy`               | `boolean`                                       | 标记忙碌，影响其他功能是否接管舰队       |
| `FLEET_SPECIAL_ACTION`                         | `$core_fleetSpecialAction`      | `boolean`                                       | 与 `FLEET_BUSY` 配合标记正在执行特定行动 |
| `FLEET_MILITARY_RESPONSE`                      | `$core_fleetMilitaryResponse`   | `boolean`                                       | 标记正在参与军事响应                     |
| `FLEET_CHASING_GHOST`                          | `$core_fleetChasingGhost`       | `boolean`                                       | 标记正在调查虚假传感器信号               |
| `FLEET_NOT_CHASING_GHOST`                      | `$core_fleetNotChasingGhost`    | `boolean`                                       | 暂时阻止调查虚假传感器信号               |
| `FLEET_CHASING_GHOST_RANDOM`                   | `$core_fleetChasingGhostRandom` | `java.util.Random`                              | 保存调查虚假传感器信号所用的随机源       |

## 完整最小例

创建 `src/data/scripts/gearin/campaign/GearInFleetStatsChain.java` 与 `GearInFleetMemoryProbe.java`，分别保存下面的两个类。在 `GearInModPlugin` 添加它们的 `import`，把以下局部登记加入现有的新档推进后回调：

```java
@Override
public void onNewGameAfterTimePass() {
    SectorAPI sector = Global.getSector();
    sector.addTransientScript(new GearInFleetStatsChain());
    sector.addTransientScript(new GearInFleetMemoryProbe());
}
```

两份脚本各执行一轮。状态脚本移除临时成员，Memory 脚本恢复两个原键；当前入口继续保留已有的新档生成逻辑。

::: details 完整成品：GearInFleetStatsChain

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;

public class GearInFleetStatsChain implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetStatsChain";
    private static final Logger LOG = Global.getLogger(GearInFleetStatsChain.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        SectorAPI sector = Global.getSector();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                "wolf_Assault");

        if (member == null) {
            LOG.warn("GearIn fleet stats: member creation failed");
            return;
        }

        data.addFleetMember(member);

        ShipVariantAPI original = member.getVariant();
        MutableShipStatsAPI before = member.getStats();
        MutableFleetStatsAPI fleetStats = fleet.getStats();
        float speedBefore = before.getMaxSpeed().getModifiedValue();
        float burnBefore = data.getBurnLevel();
        PersonAPI captain = member.getCaptain();
        PersonAPI commander = fleet.getCommander();

        ShipVariantAPI refit = original.clone();
        refit.setNumFluxCapacitors(
                original.getNumFluxCapacitors() + 1);
        member.setVariant(refit, false, true);
        member.updateStats();

        MutableShipStatsAPI after = member.getStats();
        LOG.info("GearIn fleet stats: member=" + member.getId()
                + " captain=" + (captain == null
                        ? "none"
                        : captain.getNameString())
                + " commander=" + (commander == null
                        ? "none"
                        : commander.getNameString())
                + " speedBefore=" + speedBefore
                + " speedAfter=" + after.getMaxSpeed().getModifiedValue()
                + " burnBefore=" + burnBefore
                + " burnAfter=" + data.getBurnLevel()
                + " statsRebuilt=" + (before != after)
                + " sensorRange="
                + fleetStats.getSensorRangeMod().computeEffective(
                        fleet.getSensorStrength())
                + " fleetPoints=" + fleet.getFleetPoints());

        member.setVariant(original, false, true);
        member.updateStats();
        data.removeFleetMember(member);
        data.syncIfNeeded();
    }
}
```

:::

::: details 完整成品：GearInFleetMemoryProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class GearInFleetMemoryProbe implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemoryProbe";
    private static final Logger LOG = Global.getLogger(GearInFleetMemoryProbe.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        SectorAPI sector = Global.getSector();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();

        boolean hadBusy = memory.contains(MemFlags.FLEET_BUSY);
        Object oldBusy = memory.get(MemFlags.FLEET_BUSY);
        float busyExpire = memory.getExpire(MemFlags.FLEET_BUSY);
        boolean hadPatrolDistance = memory.contains(MemFlags.FLEET_PATROL_DISTANCE);
        Object oldPatrolDistance = memory.get(MemFlags.FLEET_PATROL_DISTANCE);
        float patrolExpire = memory.getExpire(MemFlags.FLEET_PATROL_DISTANCE);

        try {
            memory.set(MemFlags.FLEET_BUSY, true, 0.25f);
            memory.set(MemFlags.FLEET_PATROL_DISTANCE, 5f, 0.25f);
            LOG.info("GearIn fleet memory: busy=" + memory.getBoolean(MemFlags.FLEET_BUSY)
                    + " patrolDistance=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE));
        } finally {
            if (hadBusy) {
                memory.set(MemFlags.FLEET_BUSY, oldBusy, busyExpire);
            } else {
                memory.unset(MemFlags.FLEET_BUSY);
            }
            if (hadPatrolDistance) {
                memory.set(MemFlags.FLEET_PATROL_DISTANCE, oldPatrolDistance, patrolExpire);
            } else {
                memory.unset(MemFlags.FLEET_PATROL_DISTANCE);
            }
            LOG.info("GearIn fleet memory restored: busyPresent="
                    + memory.contains(MemFlags.FLEET_BUSY)
                    + " busy=" + memory.get(MemFlags.FLEET_BUSY)
                    + " busyExpire=" + memory.getExpire(MemFlags.FLEET_BUSY)
                    + " patrolPresent=" + memory.contains(MemFlags.FLEET_PATROL_DISTANCE)
                    + " patrol=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE)
                    + " patrolExpire=" + memory.getExpire(MemFlags.FLEET_PATROL_DISTANCE));
        }
    }
}
```

:::

## 验证方案

前置数据：玩家舰队拥有至少一艘成员，并且存档已经完成人物和舰队初始化。

1. 构建并重启，建立新档，触发新档推进后的两个一轮脚本。
2. 搜索 `GearIn fleet stats`，核对 `statsRebuilt`、人物名称、速度、航速与 `fleetPoints`，随后打开舰队界面确认临时成员已经移除。
3. 搜索 `GearIn fleet memory` 与 `restored`，比较 `busy`、`patrolDistance` 的写入、原存在状态和剩余期限。
4. 重新读取两个键，确认恢复为脚本执行前的存在状态和值。

预期结果：

- 装配修改后 `statsRebuilt=true`，日志读取新对象的数值；增加电容时速度和航速可以保持原值。
- `fleetPoints` 等汇总在临时成员加入期间读取，脚本完成后临时成员已移除。
- Memory 写入日志显示 `busy=true`、`patrolDistance=5`，恢复日志显示原存在状态、值与剩余期限。

## 故障分析

- **数值没有变化**：没有触发重算，或读取的是旧 `stats` 引用。
    - **解决方案**：在 `setVariant` 或成员状态写入后调用 `updateStats()`，再重新取得 `getStats()` 并读取最终数值。

- **舰长效果缺失**：成员没有加入 `FleetDataAPI`，或技能作用域不适用。
    - **解决方案**：确认成员已经加入目标舰队，读取 `member.getCaptain()` 与 `fleet.getCommander()`，再检查技能的作用域和等级。

- **舰队汇总停留在旧值**：集合变化后使用了缓存的汇总值。
    - **解决方案**：重新取得 `fleet.getStats()`、`fleet.getFleetPoints()` 和 `fleet.getFleetData()`，不要继续使用写入前的对象引用。

- **Memory 类型错误**：写入类型与读取方法不一致。
    - **解决方案**：用 `get()` 查看原始对象类型，再选择 `getBoolean()`、`getFloat()`、`getLong()` 或 `getInt()`；无法确定类型时不要强制转换。

- **标记持续存在**：恢复时没有区分原来不存在和原来存在。
    - **解决方案**：同时保存 `contains(key)` 和原值，原来没有键时调用 `unset()`，原来有键时写回原值并恢复过期时间。

- **临时 `modifier` 在暂停后仍然存在**：把真实秒数当作了生涯天数，或测试期间没有继续推进生涯时间。
    - **解决方案**：按生涯天数设置有效期，在暂停和继续运行两个阶段分别记录 `getExpire()` 与当前值。

- **Memory 标记影响了其他 AI**：测试使用了原版常用键，却没有保存和恢复原状态。
    - **解决方案**：写入前保存 `contains`、原值和过期时间，测试结束后按原存在状态恢复；演示只使用短有效期。

## 总结

成员的数值由船体、装配、人物和当前状态重新建立。修改装配后，从成员取得新的 `stats`，持续效果放在重算会再次调用的来源中，单次写入才能成为可持续的功能。

舰队临时修饰登记属性与生涯日期限，Memory 保存供其他功能读取的状态。两者都有有效期，也有各自的移除与恢复方法；本节用日志确认对象替换和原状态恢复，使这两个过程各有明确结果。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员 `stats`；
- `com.fs.starfarer.api.fleet.MutableFleetStatsAPI`：舰队 `stats`；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值与过期；
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：舰队标记。

## 维护舰队成员状态

给玩家一艘舰船时，除了船体和装配，还要决定它的损伤、船员、CR 与封存状态。本节创建一艘狼和一艘锤头鲨，用后者演示损伤、缺员与封存，再把它留在舰队中，比较脚本写入的即时状态与游戏继续整备后的结果。

## 学习目标

完成后，您将能够：

- 创建一艘拥有稳定生涯身份的 `FleetMemberAPI`，并把它加入指定舰队；
- 读取船体完整度、船员数量、CR、维修状态、封存状态和可部署结果；
- 在修改单舰状态后重新读取结果，并判断是否需要更新成员 `stats`；
- 解释 `FleetMemberAPI` 与战斗侧 `ShipAPI` 的对象边界。

## 前置知识

- [C11-S2] 的 `FleetDataAPI` 成员归属、旗舰和集合顺序；
- [C11-S3] 的成员 `stats` 重算与装配替换边界；
- Java 对象引用、一次性脚本和 `starsector.log` 的基本用法。

## 创建并保留生涯成员

创建 `src/data/scripts/gearin/campaign/GearInFleetMemberBuilder.java` 与 `GearInFleetMemberStatus.java`。前者提供可复用的成员创建方法，后者调用这个方法创建锤头鲨，再修改它的状态。在 `GearInModPlugin` 添加两类的 `import`，向已有的 `onNewGameAfterTimePass(...)` 追加登记，沿用其中的 `sector`：

```java
sector.addTransientScript(new GearInFleetMemberBuilder());
sector.addTransientScript(new GearInFleetMemberStatus());
```

新档推进完成后，两份脚本各执行一轮。狼采用 `wolf_Assault`，锤头鲨采用 `hammerhead_Elite`，两艘都保留在玩家舰队，便于后面通过界面和战斗继续观察。

成员创建沿用 [C11-S2] 的工厂入口。**创建后还要加入舰队**，舰队界面、汇总与部署编队才会包含它。下面是创建方法的核心操作：

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetDataAPI data = fleet.getFleetData();
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");

data.addFleetMember(member);

LOG.info("member id=" + member.getId()
        + " fleet=" + fleet.getName()
        + " count=" + data.getNumMembers());
```

`createFleetMember(...)` 的第二个参数是装配或战机联队的规格 ID；`member.getId()` 是游戏为这个成员生成的独立 ID。本节保留生成的 ID，后续用它追踪同一艘船。

加入后，成员的 `getFleetData()` 指向目标舰队，集合也会标记待同步。记录加入前后的归属，可以把新对象与舰队里的成员对应起来。以下局部片段使用锤头鲨：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "hammerhead_Elite");

LOG.info("before add: id=" + member.getId()
        + " fleetData=" + member.getFleetData());

data.addFleetMember(member);

String variantId = member.getVariant() == null
        ? "<none>"
        : member.getVariant().getHullVariantId();
LOG.info("after add: id=" + member.getId()
        + " hull=" + member.getHullId()
        + " variant=" + variantId
        + " fleetData=" + member.getFleetData()
        + " count=" + data.getNumMembers());
```

成员加入时会补齐缺少的船名与默认舰长，并清除新成员的旗舰标记。完整创建方法另外接受 `makeFlagship` 参数，本次传入 `false`，保持玩家原旗舰。旗舰切换的可选用法在状态说明之后给出。

## 记录修改前的状态

锤头鲨创建后，先取得它的状态对象。损伤从 `getStatus()` 读取，船员从 `getCrewComposition()` 读取，CR、维修速率与封存由 `getRepairTracker()` 管理。下面的局部日志把这些结果记录在同一个成员 ID 下：

```java
FleetMemberStatusAPI status = member.getStatus();
CrewCompositionAPI crewComposition = member.getCrewComposition();
RepairTrackerAPI repairTracker = member.getRepairTracker();
ShipVariantAPI variant = member.getVariant();

float hullFraction = status.getHullFraction();
float crewFraction = member.getCrewFraction();
int crew = crewComposition.getCrewInt();
float cr = repairTracker.getCR();
boolean needsRepairs = status.needsRepairs();
boolean mothballed = repairTracker.isMothballed();
String hullVariantId = variant == null
        ? "<none>"
        : variant.getHullVariantId();

LOG.info("member status: id=" + member.getId()
        + " hull=" + hullFraction
        + " crew=" + crew
        + " crewFraction=" + crewFraction
        + " cr=" + cr
        + " repairs=" + needsRepairs
        + " mothballed=" + mothballed
        + " variant=" + hullVariantId);
```

船体完整度与 CR 都使用 0 到 1 的比例。`getCrewInt()` 返回船员数量，`getCrewFraction()` 返回最低船员需求的满足比例；`getBaseCR()` 保留基础 CR，`getCR()` 则反映船员不足后的有效 CR。这些数值需要分别记录，否则缺员造成的 CR 下降很容易被误认为 `setCR(...)` 没有生效。

维修速率与近期 CR 事件也可以从维修跟踪器读取，损伤本身仍由 `status` 表示。接下来按完整状态脚本的顺序，先施加损伤，再修改船员和 CR，最后切换封存。

## 施加损伤与修复船体

状态脚本用 `applyHullFractionDamage(0.2f)` 施加相当于最大船体 20% 的损伤。需要指定完整度时还可以使用 `setHullFraction(...)`，直接修复则使用 `repairFully()`。下面的局部实验在损伤后立即修复，分别记录两次结果；完整状态脚本会保留损伤供界面观察：

```java
float hullBefore = member.getStatus().getHullFraction();
member.getStatus().applyHullFractionDamage(0.2f);
float hullAfterDamage = member.getStatus().getHullFraction();
boolean needsRepairAfterDamage = member.needsRepairs();

member.getStatus().repairFully();
float hullAfterRepair = member.getStatus().getHullFraction();
boolean needsRepairAfterRepair = member.needsRepairs();

LOG.info("hull transition: before=" + hullBefore
        + " damaged=" + hullAfterDamage
        + " repaired=" + hullAfterRepair
        + " needsRepairs=" + needsRepairAfterDamage
        + "->" + needsRepairAfterRepair);
```

`repairFully()` 恢复可修复的船体、装甲及模块状态。只将船体比例设为 1 时，装甲或模块脱离状态仍可能使 `needsRepairs()` 为 `true`；因此维修结果还要结合 `computeRepairednessFraction()` 读取，它同时考虑船体与装甲。

实际生涯维修继续受维修速率与资源条件影响。本例直接调用状态接口，说明的是损伤如何写入成员；后续消耗与恢复由游戏的维修流程处理。

## 让船员不足反映到 CR

把锤头鲨的船员改为最低需求的一半，再将基础 CR 设置为 0.3。`setCrew(...)` 写入当前船员组成，`setCR(...)` 的值使用 0 到 1 的比例。局部片段如下：

```java
CrewCompositionAPI composition = member.getCrewComposition();
float crewBefore = composition.getCrew();
float crBefore = member.getRepairTracker().getCR();

composition.setCrew(Math.max(1f, member.getMinCrew() * 0.5f));
member.getRepairTracker().setCR(0.3f);

float crewAfter = composition.getCrew();
float crewFractionAfter = member.getCrewFraction();
float crAfter = member.getRepairTracker().getCR();
boolean deployable = member.canBeDeployedForCombat();

LOG.info("readiness transition: crew=" + crewBefore
        + "->" + crewAfter
        + " crewFraction=" + crewFractionAfter
        + " cr=" + crBefore
        + "->" + crAfter
        + " deployable=" + deployable);
```

**基础 CR 为 0.3、船员比例为 0.5 时，有效 CR 为 0.15。** `getCR()` 给出的正是后一个结果，`getBaseCR()` 仍为 0.3。`canBeDeployedForCombat()` 先判断封存，再比较 CR 与船员阈值：`settings.json` 中 `noDeployCRPercent` 为 0、`noDeployCrewPercent` 为 5，读取时转换成比例；战机还要求有效 CR 大于 0。因此半员与低 CR 不等于必然禁止部署，实际布尔结果由该方法读取。

::: warning 单舰船员会在舰队同步时重新分配
玩家舰队从公共货舱向各成员分配船员。本例直接修改的是即时组成，后续同步可能重新填满；长期缺员需要结合舰队货舱中的船员与各成员需求处理。
:::

## 切换封存状态

有些成员需要保留在舰队中，暂时停止使用。`setMothballed(true)` 保存封存前的基础 CR，再将基础 CR 置为 0，部署判断返回 `false`。下面的局部片段先封存，再解除封存：

```java
RepairTrackerAPI tracker = member.getRepairTracker();
boolean mothballedBefore = tracker.isMothballed();

tracker.setMothballed(true);
boolean mothballedAfter = tracker.isMothballed();
boolean deployableWhileMothballed = member.canBeDeployedForCombat();

tracker.setMothballed(false);
boolean deployableAfterRelease = member.canBeDeployedForCombat();

LOG.info("mothball transition: before=" + mothballedBefore
        + " after=" + mothballedAfter
        + " deployableWhileMothballed=" + deployableWhileMothballed
        + " deployableAfterRelease=" + deployableAfterRelease);
```

解除封存时，跟踪器恢复封存前保存的基础 CR，再按当前船员比例计算有效 CR。完整脚本先设置 0.3，封存期间读取 0，解除后基础值回到 0.3。封存切换还会更新成员 `stats` 并标记舰队待同步，因此解除后的界面结果继续受船员重新分配与生涯整备影响。

`getMemberStrength()` 也受封存与 CR 等状态影响。比较当前强度时，在状态更新后重新读取，避免保留切换前的数值。

## 装配与旗舰对成员状态的影响

### 更换装配后刷新派生数值

本节的损伤与封存操作直接作用于成员状态；装配替换还会改变这些状态所依赖的数值。沿 [C11-S3] 的方式克隆当前装配，用 `setVariant(..., false, true)` 写回并重算。下面的局部片段只修改显示名，再记录部署 CR 消耗与成员强度，[C11-S4] 继续修改实际装备：

```java
ShipVariantAPI replacement = member.getVariant().clone();
replacement.setVariantDisplayName("GearIn status check");

member.setVariant(
        replacement,
        false,
        true);

member.updateStats();
LOG.info("variant applied: id=" + member.getId()
        + " variant=" + member.getVariant().getHullVariantId()
        + " deployCost=" + member.getDeployCost()
        + " memberStrength=" + member.getMemberStrength());
```

`withRefit=true` 会按新旧装配差异处理改装 CR 影响，这里传入 `false`，让 CR 比较集中在当前状态操作上。第三个参数 `true` 触发 `updateStats()`，最低船员需求、CR 上限等派生值按新装配重算，后续 `stats` 也重新取得。

装配替换作用于这个成员，舰队归属仍由 `FleetDataAPI` 管理。重算后在原舰队重新读取数量、旗舰和汇总，才能继续对应同一个成员。

### 可选：让新成员临时成为旗舰

创建方法的 `makeFlagship=true` 调用集合级 `setFlagship(...)`，清除其他成员的旗舰标记，并将指挥官交给新旗舰。沿 [C11-S2] 的恢复方法，同时保存原旗舰与舰长。以下局部片段另创建一艘演示狼，并在同一次调用中切换和恢复：

```java
FleetMemberAPI originalFlagship = fleet.getFlagship();
FleetMemberAPI demoMember = GearInFleetMemberBuilder.createMember(
        fleet,
        "wolf_Assault",
        false);
Map<FleetMemberAPI, PersonAPI> captains = new LinkedHashMap<>();
for (FleetMemberAPI member : data.getMembersListCopy()) {
    captains.put(member, member.getCaptain());
}
data.setFlagship(demoMember);
LOG.info("temporary flagship=" + fleet.getFlagship().getId());
data.setFlagship(originalFlagship);
for (Map.Entry<FleetMemberAPI, PersonAPI> entry : captains.entrySet()) {
    entry.getKey().setCaptain(entry.getValue());
}
```

这段需要 `Map`、`LinkedHashMap` 与 `PersonAPI`，恢复完成后新增的演示狼仍留在舰队。`FleetMemberAPI.setFlagship(true)` 只处理当前成员的标记，并可能设置其舰长；集合级入口才同时维护其他成员。因此本例切换和恢复都调用 `FleetDataAPI`。

::: warning 跨生涯时间恢复旗舰要确认原成员的归属
原旗舰已经损失或转入另一支舰队时，原引用不能直接恢复为当前舰队旗舰。恢复前从当前名单确认其归属，再决定旗舰与舰长安排。
:::

## 从生涯成员进入战斗

成员保留在舰队后，可以继续验证战斗中的结果。部署时，游戏根据它的装配、舰长、指挥官、CR、船员和损伤创建战斗侧 `ShipAPI`。封存与不可部署状态会限制控制或部署，进入战斗前先记录当前成员：

```java
boolean deployable = member.canBeDeployedForCombat();
String campaignId = member.getId();

LOG.info("combat boundary: campaignMember=" + campaignId
        + " deployable=" + deployable
        + " hull=" + member.getHullId()
        + " cr=" + member.getRepairTracker().getCR());
```

部署列表反映当时的状态，退出战斗后从生涯舰队重新取得同一成员 ID，读取 CR 与船体完整度。生涯成员跟随存档，`ShipAPI` 对应本场战斗实例；跨战斗功能保存成员身份，再从当前舰队取得对象。

## 完整最小例

创建类提供可复用的 `createMember(...)`，状态类用同一入口创建锤头鲨。保存两类并保留前面的新档登记。

::: details 完整成品：GearInFleetMemberBuilder

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 创建一艘生涯舰队成员，并可选择把它设为旗舰。
 */
public class GearInFleetMemberBuilder implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberBuilder";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberBuilder.class);

    private static final String VARIANT_ID = "wolf_Assault";

    private boolean done;

    /**
     * 创建成员、加入舰队并返回创建结果。
     *
     * @param fleet 目标舰队
     * @param variantId 船体装配 ID
     * @param makeFlagship 是否把新成员设为旗舰
     * @return 已加入目标舰队的成员
     */
    public static FleetMemberAPI createMember(
            CampaignFleetAPI fleet,
            String variantId,
            boolean makeFlagship) {
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                variantId);
        data.addFleetMember(member);

        if (makeFlagship) {
            data.setFlagship(member);
        }

        return member;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = createMember(
                fleet,
                VARIANT_ID,
                false);

        LOG.info("GearIn member builder: id=" + member.getId()
                + " hull=" + member.getHullId()
                + " variant=" + member.getVariant().getHullVariantId()
                + " fleet=" + fleet.getName()
                + " members=" + fleet.getFleetData().getNumMembers()
                + " flagship=" + member.isFlagship());
    }
}
```

:::

::: details 完整成品：GearInFleetMemberStatus

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;

/**
 * 修改并记录一艘生涯成员的状态，随后保留成员供舰队界面观察。
 */
public class GearInFleetMemberStatus implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberStatus";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberStatus.class);

    private static final String VARIANT_ID = "hammerhead_Elite";

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = GearInFleetMemberBuilder.createMember(
                fleet,
                VARIANT_ID,
                false);

        FleetMemberStatusAPI status = member.getStatus();
        CrewCompositionAPI crew = member.getCrewComposition();
        RepairTrackerAPI tracker = member.getRepairTracker();

        float hullBefore = status.getHullFraction();
        float crBefore = tracker.getCR();
        float crewBefore = crew.getCrew();

        status.applyHullFractionDamage(0.2f);
        crew.setCrew(Math.max(1f, member.getMinCrew() * 0.5f));
        tracker.setCR(0.3f);
        tracker.setMothballed(true);

        float hullAfter = status.getHullFraction();
        float crWhileMothballed = tracker.getCR();
        float crewAfter = crew.getCrew();
        boolean deployableWhileMothballed = member.canBeDeployedForCombat();

        tracker.setMothballed(false);
        float crAfterRelease = tracker.getCR();
        float baseCrAfterRelease = tracker.getBaseCR();

        LOG.info("GearIn member status: id=" + member.getId()
                + " hull=" + hullBefore + "->" + hullAfter
                + " crew=" + crewBefore + "->" + crewAfter
                + " crewFraction=" + member.getCrewFraction()
                + " cr=" + crBefore + "->" + crAfterRelease
                + " crWhileMothballed=" + crWhileMothballed
                + " baseCrAfterRelease=" + baseCrAfterRelease
                + " mothballed=false"
                + " deployableWhileMothballed=" + deployableWhileMothballed
                + " deployable=" + member.canBeDeployedForCombat()
                + " needsRepairs=" + member.needsRepairs()
                + " fleetMembers=" + data.getNumMembers());
    }
}
```

:::

两个脚本执行后结束，成员继续保留在玩家舰队。后续功能通过已保存的成员 ID 从当前集合取得对象，再修改它的状态；重新调用创建入口会得到另一艘船和另一个 ID。

## 验证方案

前置数据：按本章新档入口测试，玩家舰队已建立，两个脚本在新档推进后登记。

1. 构建 MOD，确认 JAR 包含 `GearInFleetMemberBuilder.class` 和 `GearInFleetMemberStatus.class`。
2. 开始新档，等待两份脚本执行，在 `starsector.log` 搜索 `GearIn member builder` 与 `GearIn member status`。
3. 打开舰队界面，按日志中的成员 ID、船体和装配名称定位新成员，确认它属于玩家舰队且未被错误设为旗舰。
4. 在状态日志中记录 `hull`、`crew`、`cr`、`mothballed`、`needsRepairs` 和 `deployable` 字段，比较封存期间与解除后的值。
5. 在舰队界面确认锤头鲨保留、最终解除封存，比较船体和 CR；船员组成按货舱重新分配，维修与 CR 也会随生涯时间继续推进。
6. 在不修改脚本对象引用的情况下进入一次战斗，观察部署列表对不可部署状态的提示；退出战斗后重新取得同一成员 ID。

预期结果：

- 创建日志包含自动生成的成员 ID、`wolf_Assault` 装配和玩家舰队成员数量；
- 即时状态日志显示船体与船员变化，封存期间有效 CR 为 0、`deployableWhileMothballed=false`；解除后基础 CR 为 0.3，有效 CR 按当时船员比例计算；
- 解除封存后，`canBeDeployedForCombat()` 按当前 CR 与船员阈值返回结果；船体损伤另由损伤和维修字段记录；
- 演示成员保留在玩家舰队中，舰队界面和日志使用同一个成员 ID；
- 战斗结束后重新取得 `FleetMemberAPI`，没有依赖战斗侧 `ShipAPI` 的旧引用。

## 故障分析

- **日志显示成员已创建，但舰队界面没有成员**：创建了 `FleetMemberAPI`，却没有把它加入目标 `FleetDataAPI`。
    - **解决方案**：确认 `data.addFleetMember(member)` 在读取成员数量和设置旗舰之前执行，并核对 `member.getFleetData() == data`。

- **新成员意外成为旗舰**：调用了 `data.setFlagship(member)`，或直接设置成员旗舰标志后没有恢复原旗舰。
    - **解决方案**：默认创建时传入 `makeFlagship=false`，需要切换时保存原旗舰，使用集合级 `setFlagship(...)` 完成切换和恢复。

- **船体完整度恢复但仍然显示维修中**：装甲、模块或其他部件仍有损伤，`getHullFraction()` 没有覆盖这些状态。
    - **解决方案**：同时读取 `needsRepairs()` 与 `computeRepairednessFraction()`，通过游戏维修流程处理剩余损伤。

- **CR 数值与预期不同**：读取的是 `getBaseCR()`、`getCR()` 或船员比例修正前后的不同阶段，或者把百分数当作 `0` 到 `1` 的小数传入。
    - **解决方案**：记录 `getCR()`、`getBaseCR()` 和 `getCrewFraction()`，调用 `setCR(0.3f)` 这类小数值，并在修改后重新读取。

- **成员封存后仍尝试部署**：只检查船体状态，没有检查 `isMothballed()` 或 `canBeDeployedForCombat()`。
    - **解决方案**：在部署入口先读取 `canBeDeployedForCombat()`，将封存、CR、船员和维修状态分别写入日志。

- **装配替换后派生值没有变化**：使用 `setVariant(..., false, false)` 后继续使用旧 `stats`，或只修改了共享装配对象。
    - **解决方案**：对装配先 `clone()`，写回时传入 `withStatsUpdate=true`，随后调用 `updateStats()` 并重新读取成员派生值。

- **战斗结束后访问对象报错**：把战斗侧 `ShipAPI` 引用保存到跨战斗字段或存档数据。
    - **解决方案**：只保存生涯成员 ID；每次战斗结束后从玩家舰队的 `FleetDataAPI` 重新取得 `FleetMemberAPI`。

- **演示成员重复出现**：一次性脚本实例被重新注册或没有使用持久标记判断已经创建过成员。
    - **解决方案**：为每个脚本保留 `done` 状态，并在正式入口使用专用 Memory 记录成员 ID；执行前检查已有成员再决定复用或清理。

## 总结

一艘生涯舰船由成员保存身份与当前状态，加入 `FleetDataAPI` 后才参与舰队上下文。损伤、船员、CR 与封存各有入口，也相互影响：缺员降低有效 CR，封存保存并清零基础 CR，同步与生涯推进继续改变整备结果。

脚本的即时日志与稍后打开的界面可能处于不同阶段。本节保留两艘成员，用 ID 对应日志、界面和战斗；状态发生变化时，就能沿着同一艘船确认变化的原因。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份、装配、状态、部署和 `stats` 入口；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员加入、移除、旗舰和集合同步；
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体、装甲、部件损伤和维修状态；
- `com.fs.starfarer.api.fleet.CrewCompositionAPI`：船员和海军陆战队数量；
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR、维修事件、封存和恢复速率；
- `com.fs.starfarer.api.combat.ShipAPI`：战斗侧舰船对象及其生命周期；
- `data/config/settings.json`：部署阈值配置；
- `Starsector/starsector-core/starsector.log`：核对脚本输出与运行时错误。
