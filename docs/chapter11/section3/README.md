# 舰队状态的重算过程

本节观察装配、成员、舰长、指挥官和 fleet total 如何进入舰队状态。重点是重算时机、对象引用和舰队 memory 的有效期。

*本节预期阅读时间：~24 分钟。*

*本节预期操作时间：~28 分钟。*

## 学习目标

完成后，您将能够：

- 解释成员 stats 的来源和重算顺序；
- 区分立即重算、延迟重算和旧引用失效；
- 观察 fleet total 与成员 stats 的不同职责；
- 覆盖全部 FLEET 标记并恢复临时 memory。

## 前置知识

- [C11-S2] 的舰队成员集合；
- [C11-S4] 的单个成员状态；
- [C11-S5] 的装配克隆和 setVariant 调用；
- [C13-S3] 的技能等级与作用域；
- [C8-S4] 的 MutableStat 和 modifier ID。

## 取得状态对象

FleetMemberAPI.getStats 返回当前成员的 MutableShipStatsAPI。装配、舰长、指挥官或舰队归属发生变化时，成员可能建立新的 stats 对象；保存旧引用只能用于比较重算前后的结果。

成员进入舰队后，舰长个人效果和舰队指挥官效果才拥有完整上下文。fleet total 读取舰队级结果，成员 stats 读取单舰结果。

读取顺序决定您看到的是哪一层状态。先从 FleetMemberAPI 取得成员，再从成员取得 MutableShipStatsAPI；需要舰队级结果时，再从 CampaignFleetAPI 取得 MutableFleetStatsAPI。三种引用分别属于成员、单舰 stats 和舰队 stats，不能用其中一个替代另外两个。

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

这个片段只读取已有对象。它不创建技能、不改变装配，也不主动触发战斗；日志中的单舰速度和舰队燃耗分别用于确认读取层级。

### 状态来源速查表

| 来源 | 进入位置 | 观察对象 |
| --- | --- | --- |
| 船体规格 | 成员创建和 stats 初始化 | 基值 |
| 舰船装配 | setVariant 与 updateStats | 成员 stats |
| 舰长 | 成员归属后的重算 | 当前成员 |
| 指挥官 | FleetDataAPI 的舰队重算 | 多个成员 |
| fleet total | 舰队级 stats | 移动和后勤汇总 |
| CR 与船员 | RepairTracker 和成员状态 | 可部署性和修正值 |

## 观察装配触发重算

克隆当前装配并修改幅能配置，再调用 setVariant。withStatsUpdate 为 true 时，成员立即建立新 stats；withRefit 控制是否按改装流程处理 CR 影响。

```java
MutableShipStatsAPI before = member.getStats();
ShipVariantAPI refit = member.getVariant().clone();
refit.setNumFluxCapacitors(refit.getNumFluxCapacitors() + 1);
member.setVariant(refit, false, true);
MutableShipStatsAPI after = member.getStats();
LOG.info("statsRebuilt=" + (before != after));
```

直接对 getStats 返回对象写入的 modifier 属于当前 stats 实例。下一次 updateStats 会重新播放正式来源，旧 modifier 随旧对象消失。

setVariant(variant, withRefit, withStatsUpdate) 的三个参数承担不同职责。variant 提供新的装配对象，withRefit 决定是否按改装流程处理成员状态，withStatsUpdate 决定是否在本次调用中更新 stats。页面示例把装配修改和 stats 读取放在同一段，便于比较调用前后的对象引用；实际脚本若连续修改多个属性，可以先完成克隆，再只调用一次 setVariant。

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

如果只把 withStatsUpdate 设为 false，装配已经替换，但成员的派生数值可能仍等待后续 updateStats。读取前调用 member.updateStats() 可以明确建立观察边界；读取后仍需重新取得 getStats()，因为重算可能替换对象。

::: warning stats 引用不能跨重算保存
重算可能整体替换 MutableShipStatsAPI。修改旧引用不会改变成员当前数值，日志通常也不会直接报错。
:::

## 读取舰长和指挥官效果

状态链示例只读取现有人物对象，不创建技能。舰长用于个人作用域，指挥官用于舰队作用域；技能定义、等级和 effect 语义由人物技能页面负责。

```java
PersonAPI captain = member.getCaptain();
PersonAPI commander = fleet.getCommander();
LOG.info("captain=" + (captain == null ? "none" : captain.getNameString())
        + " commander=" + (commander == null ? "none" : commander.getNameString()));
member.updateStats();
```

member.updateStats() 会按当前成员装配、舰长、舰队归属和指挥官上下文重新应用正式修正。舰长为空时，成员仍然可以更新船体和装配来源；指挥官效果则依赖成员已经加入 FleetDataAPI。这个页面只读取已经存在的人物对象，避免把人物创建和技能等级混入 stats 重算。

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

## 读取 fleet total

舰队级 stats 与成员 stats 各自有生命周期。集合变化、成员状态变化或人物变化后，重新读取舰队汇总和成员 modified value。

```java
MutableFleetStatsAPI fleetStats = fleet.getStats();
LOG.info("fleetPoints=" + fleet.getFleetPoints()
        + " burn=" + fleet.getFleetData().getBurnLevel()
        + " sensor=" + fleetStats.getSensorRangeMod().computeEffective(
                fleet.getSensorStrength()));
```

舰队 stats 中的 modifier 会进入舰队移动、传感器和补给相关计算。调用 addTemporaryModFlat、addTemporaryModPercent 或 addTemporaryModMult 时，source 字符串就是后续移除该临时效果的索引；相同 source 会覆盖或合并同一来源的写入，演示脚本因此使用固定且唯一的 GearIn 前缀。

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
        + stats.getFleetwideMaxBurnMod().computeEffective(
                fleet.getFleetData().getBurnLevel()));

stats.removeTemporaryMod(source);
```

临时 modifier 的有效期以生涯天数推进。暂停时生涯时间不增长，剩余时间不会因为真实世界经过而减少；移除测试效果时使用同一个 source，不能只依靠数值回到基线。

## 写入并恢复舰队 memory

MemoryAPI 通过字符串键保存运行时状态。set 写入值并可附带生涯天数过期时间，getBoolean、getFloat 或 get 按类型读取，contains 判断键是否存在，unset 立即移除。

### FLEET 标记速查表

| 常量 | 键值 | 常用值 | 直接影响 |
| --- | --- | --- | --- |
| FLEET_DO_NOT_IGNORE_PLAYER | $cfai_doNotIgnorePlayer | boolean | AI 考虑玩家 |
| FLEET_IGNORES_OTHER_FLEETS | $cfai_ignoreOtherFleets | boolean | 忽略其他舰队 |
| FLEET_IGNORED_BY_OTHER_FLEETS | $cfai_ignoredByOtherFleets | boolean | 被其他舰队忽略 |
| FLEET_IGNORED_BY_FACTION | $cfai_ignoredByFaction | faction | 指定忽略势力 |
| FLEET_PATROL_DISTANCE | $cfai_patrolDist | float | 巡逻距离 |
| FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN | $fidConifgGen | object | 交互配置覆盖 |
| FLEET_NO_MILITARY_RESPONSE | $core_fleetNoMilitaryResponse | boolean | 禁止军事响应 |
| FLEET_FIGHT_TO_THE_LAST | $core_fightToTheLast | boolean | 改变撤退判断 |
| FLEET_BUSY | $core_fleetBusy | boolean | 标记忙碌 |
| FLEET_SPECIAL_ACTION | $core_fleetSpecialAction | boolean | 标记特殊行动 |
| FLEET_MILITARY_RESPONSE | $core_fleetMilitaryResponse | boolean | 标记军事响应 |
| FLEET_CHASING_GHOST | $core_fleetChasingGhost | boolean | 标记追逐幽灵 |
| FLEET_NOT_CHASING_GHOST | $core_fleetNotChasingGhost | boolean | 禁止追逐幽灵 |
| FLEET_CHASING_GHOST_RANDOM | $core_fleetChasingGhostRandom | boolean | 随机追逐幽灵 |

过期时间按生涯时间推进。暂停时生涯时间不增长，标记的剩余有效期也不会按真实时间减少。

MemoryAPI 的 set(key, value) 写入不带过期时间；set(key, value, expire) 使用生涯天数作为有效期。get() 返回原始对象，getBoolean、getFloat、getLong 和 getInt 按目标类型读取；类型不匹配时，读取结果不能当作转换后的数值。getMemoryWithoutUpdate() 取得现有 memory，不额外推进其中的过期计时，适合在一次采样中保存原值。

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
    memory.set(key, oldValue);
    if (oldExpire > 0f) {
        memory.expire(key, oldExpire);
    }
} else {
    memory.unset(key);
}
```

恢复存在状态和值时要同时保存 contains、原始对象和过期信息。只把旧值写回而不恢复有效期，会把原本即将过期的标记变成永久标记；只调用 unset 则会丢失原本由游戏系统维护的状态。

```java
boolean existed = memory.contains(MemFlags.FLEET_BUSY);
Object original = memory.get(MemFlags.FLEET_BUSY);
try {
    memory.set(MemFlags.FLEET_BUSY, true, 0.25f);
    LOG.info("busy=" + memory.getBoolean(MemFlags.FLEET_BUSY));
} finally {
    if (existed) {
        memory.set(MemFlags.FLEET_BUSY, original);
    } else {
        memory.unset(MemFlags.FLEET_BUSY);
    }
}
```

::: tip 恢复存在状态
恢复 memory 时同时记录 contains 和原值。原来没有键的情况要调用 unset，单纯写回 null 会留下不同的状态。
:::

## 完整最小例

两个完整类分别观察 stats 链和 memory 标记。

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

        boolean oldBusy = memory.getBoolean(MemFlags.FLEET_BUSY);
        Object oldPatrolDistance = memory.get(MemFlags.FLEET_PATROL_DISTANCE);

        memory.set(MemFlags.FLEET_BUSY, true, 0.25f);
        memory.set(MemFlags.FLEET_PATROL_DISTANCE, 5f, 0.25f);

        LOG.info("GearIn fleet memory: busy=" + memory.getBoolean(MemFlags.FLEET_BUSY)
                + " patrolDistance=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE));

        if (oldBusy) {
            memory.set(MemFlags.FLEET_BUSY, true);
        } else {
            memory.unset(MemFlags.FLEET_BUSY);
        }
        if (oldPatrolDistance == null) {
            memory.unset(MemFlags.FLEET_PATROL_DISTANCE);
        } else {
            memory.set(MemFlags.FLEET_PATROL_DISTANCE, oldPatrolDistance);
        }
    }
}
```

:::

## 验证方案

前置数据：玩家舰队拥有至少一艘成员，并且存档已经完成人物和舰队初始化。

1. 构建 MOD 并执行状态链脚本。
2. 核对日志中的 statsRebuilt、fleetPoints 和成员移除结果。
3. 执行 memory 脚本，观察 busy 和 patrolDistance 的写入值。
4. 重新读取两个键，确认恢复为脚本执行前的存在状态和值。

预期结果：

- 装配修改后 stats 对象发生重建；
- fleet total 能读取当前舰队汇总；
- memory 标记按指定类型读取；
- 过期和恢复不会污染测试存档。

## 故障分析

- **数值没有变化**：没有触发重算，或读取的是旧 stats 引用。
    - **解决方案**：在 setVariant 或成员状态写入后调用 updateStats()，再重新取得 getStats() 并读取 modified value。

- **舰长效果缺失**：成员没有加入 FleetDataAPI，或技能作用域不适用。
    - **解决方案**：确认成员已经加入目标舰队，读取 member.getCaptain() 与 fleet.getCommander()，再检查技能的作用域和等级。

- **fleet total 停留旧值**：集合变化后使用了缓存的汇总值。
    - **解决方案**：重新取得 fleet.getStats()、fleet.getFleetPoints() 和 fleet.getFleetData()，不要继续使用写入前的对象引用。

- **memory 类型错误**：写入类型与读取方法不一致。
    - **解决方案**：用 get() 查看原始对象类型，再选择 getBoolean()、getFloat()、getLong() 或 getInt()；无法确定类型时不要强制转换。

- **标记持续存在**：恢复时没有区分原来不存在和原来存在。
    - **解决方案**：同时保存 contains(key) 和原值，原来没有键时调用 unset()，原来有键时写回原值并恢复过期时间。

- **临时 modifier 在暂停后仍然存在**：把真实秒数当作了生涯天数，或测试期间没有继续推进生涯时间。
    - **解决方案**：按生涯天数设置有效期，在暂停和继续运行两个阶段分别记录 getExpire() 与当前值。

- **memory 标记影响了其他 AI**：测试使用了原版常用键，却没有保存和恢复原状态。
    - **解决方案**：写入前保存 contains、原值和过期时间，测试结束后按原存在状态恢复；演示只使用短有效期。

## 总结

成员 stats 是重算结果，fleet total 是舰队级结果，MemoryAPI 提供带过期时间的 AI 标记。调试先确认对象是否重建，再确认来源和恢复状态。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员 stats；
- `com.fs.starfarer.api.fleet.MutableFleetStatsAPI`：舰队 stats；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值与过期；
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：舰队标记。
