# 舰队状态的重算过程

装配里增加一个幅能电容，成员的数值会随之重算；此前取得的 `stats` 却可能已经被替换。本节借一艘临时加入的狼说明这次变化，再为舰队添加短期航速效果、写入带期限的 Memory，分别确认数值的来源与状态的寿命。

*本节预期阅读时间：\~24 分钟。*

*本节预期操作时间：\~28 分钟。*

## 学习目标

完成后，您将能够：

- 解释成员 `stats` 的来源和重算顺序；
- 区分立即重算、延迟重算和旧引用失效；
- 观察舰队汇总与成员 `stats` 的不同职责；
- 覆盖全部 `FLEET` 标记并恢复临时 Memory。

## 前置知识

- [C11-S2] 的舰队成员集合；
- [C8-S4] 的 `MutableStat` 和来源 ID。

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

| 来源 | 进入位置 | 观察对象 |
| --- | --- | --- |
| 船体规格 | 成员创建和 `stats` 初始化 | 基值 |
| 舰船装配 | `setVariant` 与 `updateStats` | 成员 `stats` |
| 舰长 | 成员归属后的重算 | 当前成员 |
| 指挥官 | 成员与舰队完整重算 | 多个成员 |
| 舰队汇总 | 舰队级 `stats` 与成员汇总入口 | 移动和后勤结果 |
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

| 常量 | 键值 | 保存的值 | 作用 |
| --- | --- | --- | --- |
| `FLEET_DO_NOT_IGNORE_PLAYER` | `$cfai_doNotIgnorePlayer` | `boolean` | 让 AI 考虑玩家 |
| `FLEET_IGNORES_OTHER_FLEETS` | `$cfai_ignoreOtherFleets` | `boolean` | 让舰队忽略其他舰队 |
| `FLEET_IGNORED_BY_OTHER_FLEETS` | `$cfai_ignoredByOtherFleets` | `boolean` | 让其他舰队忽略当前舰队 |
| `FLEET_IGNORED_BY_FACTION` | `$cfai_ignoredByFaction` | 势力 ID 字符串 | 指定忽略当前舰队的势力 |
| `FLEET_PATROL_DISTANCE` | `$cfai_patrolDist` | `float` | 提供巡逻距离 |
| `FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN` | `$fidConifgGen` | `FleetInteractionDialogPluginImpl.FIDConfigGen` | 创建舰队交互配置 |
| `FLEET_NO_MILITARY_RESPONSE` | `$core_fleetNoMilitaryResponse` | `boolean` | 阻止舰队参加军事响应 |
| `FLEET_FIGHT_TO_THE_LAST` | `$core_fightToTheLast` | `boolean` | 改变战斗中的撤退判断 |
| `FLEET_BUSY` | `$core_fleetBusy` | `boolean` | 标记忙碌，影响其他功能是否接管舰队 |
| `FLEET_SPECIAL_ACTION` | `$core_fleetSpecialAction` | `boolean` | 与 `FLEET_BUSY` 配合标记正在执行特定行动 |
| `FLEET_MILITARY_RESPONSE` | `$core_fleetMilitaryResponse` | `boolean` | 标记正在参与军事响应 |
| `FLEET_CHASING_GHOST` | `$core_fleetChasingGhost` | `boolean` | 标记正在调查虚假传感器信号 |
| `FLEET_NOT_CHASING_GHOST` | `$core_fleetNotChasingGhost` | `boolean` | 暂时阻止调查虚假传感器信号 |
| `FLEET_CHASING_GHOST_RANDOM` | `$core_fleetChasingGhostRandom` | `java.util.Random` | 保存调查虚假传感器信号所用的随机源 |

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
