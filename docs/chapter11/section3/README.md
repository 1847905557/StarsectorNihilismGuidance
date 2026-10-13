# 舰队状态的重算过程

成员集合已经可以修改，接下来确认修改后的数值从哪里来。本节克隆临时成员的装配，比较重算前后的 `stats`，再读取舰队汇总与短期 `Memory` 标记。装配、人物、来源修饰和过期时间都放到同一次操作中观察，实验结束后恢复成员与原标记。

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

## 登记观察，再取得当前状态

创建 `src/data/scripts/gearin/campaign/GearInFleetStatsChain.java` 和 `GearInFleetMemoryProbe.java`。在 `GearInModPlugin` 添加两个类的 `import`，将以下局部方法加入现有入口：

```java
@Override
public void onNewGameAfterTimePass() {
    SectorAPI sector = Global.getSector();
    sector.addTransientScript(new GearInFleetStatsChain());
    sector.addTransientScript(new GearInFleetMemoryProbe());
}
```

当前新档推进完成后，各脚本执行一轮。状态脚本临时加入一名成员并恢复，`Memory` 脚本写入与恢复两个键，既有生成回调继续保留。

`FleetMemberAPI.getStats()` 返回当前成员的 `MutableShipStatsAPI`。装配、舰长、指挥官或舰队归属发生变化时，成员可能建立新的 `stats` 对象；保存旧引用只能用于比较重算前后的结果。

成员进入舰队后，舰长个人效果和舰队指挥官效果才拥有完整上下文。舰队汇总读取舰队级结果，成员 `stats` 读取单舰结果。

读取顺序决定您看到的是哪一层状态。先从 `FleetMemberAPI` 取得成员，再从成员取得 `MutableShipStatsAPI`；需要舰队级结果时，再从 `CampaignFleetAPI` 取得 `MutableFleetStatsAPI`。三种引用分别属于成员、单舰 `stats` 和舰队 `stats`，不能用其中一个替代另外两个。

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

日志中的单舰速度与舰队燃耗分别确认当前读取层级，随后再对临时成员做一次装配修改。

### 状态来源速查表

| 来源 | 进入位置 | 观察对象 |
| --- | --- | --- |
| 船体规格 | 成员创建和 `stats` 初始化 | 基值 |
| 舰船装配 | `setVariant` 与 `updateStats` | 成员 `stats` |
| 舰长 | 成员归属后的重算 | 当前成员 |
| 指挥官 | `FleetDataAPI` 的舰队重算 | 多个成员 |
| 舰队汇总 | 舰队级 `stats` | 移动和后勤汇总 |
| CR 与船员 | `RepairTrackerAPI` 和成员状态 | 可部署性和修正值 |

## 观察装配触发重算

克隆当前装配并修改幅能配置，再调用 `setVariant`。`withStatsUpdate` 为 `true` 时，成员立即建立新 `stats`；`withRefit` 控制是否按改装流程处理 CR 影响。

**重算建立新的 `stats`，之后从成员重新取得它。** 直接写在旧对象上的来源仅属于那个实例；正式效果由船体、人物、船插、幅能与当前状态重新应用。需要持续生效时，把修饰放到实际来源的回调中。

`setVariant(variant, withRefit, withStatsUpdate)` 的三个参数承担不同职责。`variant` 提供新的装配对象，`withRefit` 决定是否按改装流程处理成员状态，`withStatsUpdate` 决定是否在本次调用中更新 `stats`。页面示例把装配修改和 `stats` 读取放在同一段，便于比较调用前后的对象引用；实际脚本若连续修改多个属性，可以先完成克隆，再只调用一次 `setVariant`。

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

如果只把 `withStatsUpdate` 设为 `false`，装配已经替换，但成员的派生数值可能仍等待后续 `updateStats`。读取前调用 `member.updateStats()` 可以明确建立观察边界；读取后仍需重新取得 `getStats()`，因为重算可能替换对象。

::: warning stats 引用不能跨重算保存
重算可能整体替换 `MutableShipStatsAPI`。修改旧引用不会改变成员当前数值，日志通常也不会直接报错。
:::

## 读取舰长和指挥官效果

从当前成员与舰队取得已有舰长、指挥官，分别对应个人与舰队作用域。技能定义和等级由人物专题展开，当前观察这些人物怎样提供数值上下文。

```java
PersonAPI captain = member.getCaptain();
PersonAPI commander = fleet.getCommander();
LOG.info("captain=" + (captain == null ? "none" : captain.getNameString())
        + " commander=" + (commander == null ? "none" : commander.getNameString()));
member.updateStats();
```

`member.updateStats()` 会按当前成员装配、舰长、舰队归属和指挥官上下文重新应用正式修正。舰长为空时，成员仍然可以更新船体和装配来源；指挥官效果则依赖成员已经加入 `FleetDataAPI`。当前日志保留人物名称，再调用重算，以便对应这次成员的上下文。

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

## 读取舰队汇总

舰队级 `stats` 与成员 `stats` 各自有生命周期。集合变化、成员状态变化或人物变化后，重新读取舰队汇总和成员 最终数值。

```java
MutableFleetStatsAPI fleetStats = fleet.getStats();
LOG.info("fleetPoints=" + fleet.getFleetPoints()
        + " burn=" + fleet.getFleetData().getBurnLevel()
        + " sensor=" + fleetStats.getSensorRangeMod().computeEffective(
                fleet.getSensorStrength()));
```

舰队 `stats` 的修饰参与移动、传感器和后勤计算。`addTemporaryModFlat(...)`、`addTemporaryModPercent(...)` 与 `addTemporaryModMult(...)` 接收生涯日有效期，来源 ID 也用于移除。

**一个临时来源绑定一个 `stats` 对象。** 同来源再次写入同对象会更新修饰并重新设置剩余期限；修改另一项属性时使用另一来源。当前定义 `GearIn_FleetStatsProbe`，给舰队范围最大燃耗临时加 2，期限为半日：

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

`getBurnLevel()` 已合并舰队范围最大燃耗修饰，直接读取最终结果即可。暂停时有效期也停止推进，当前实验使用相同来源主动移除；只比较数值还不足以确认自己的记录已经解除。

## 写入并恢复舰队 Memory

`MemoryAPI` 通过字符串键保存运行时状态。`set` 写入值并可附带生涯天数过期时间，`getBoolean`、`getFloat` 或 `get` 按类型读取，`contains` 判断键是否存在，`unset` 立即移除。

### `FLEET` 标记速查表

| 常量 | 键值 | 常用值 | 直接影响 |
| --- | --- | --- | --- |
| `FLEET_DO_NOT_IGNORE_PLAYER` | `$cfai_doNotIgnorePlayer` | `boolean` | AI 考虑玩家 |
| `FLEET_IGNORES_OTHER_FLEETS` | `$cfai_ignoreOtherFleets` | `boolean` | 忽略其他舰队 |
| `FLEET_IGNORED_BY_OTHER_FLEETS` | `$cfai_ignoredByOtherFleets` | `boolean` | 被其他舰队忽略 |
| `FLEET_IGNORED_BY_FACTION` | `$cfai_ignoredByFaction` | 势力 ID 字符串 | 指定忽略势力 |
| `FLEET_PATROL_DISTANCE` | `$cfai_patrolDist` | `float` | 巡逻距离 |
| `FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN` | `$fidConifgGen` | `FleetInteractionDialogPluginImpl.FIDConfigGen` | 创建舰队交互配置 |
| `FLEET_NO_MILITARY_RESPONSE` | `$core_fleetNoMilitaryResponse` | `boolean` | 禁止军事响应 |
| `FLEET_FIGHT_TO_THE_LAST` | `$core_fightToTheLast` | `boolean` | 改变撤退判断 |
| `FLEET_BUSY` | `$core_fleetBusy` | `boolean` | 标记忙碌 |
| `FLEET_SPECIAL_ACTION` | `$core_fleetSpecialAction` | `boolean` | 与 `FLEET_BUSY` 配合标记特殊行动 |
| `FLEET_MILITARY_RESPONSE` | `$core_fleetMilitaryResponse` | `boolean` | 标记军事响应 |
| `FLEET_CHASING_GHOST` | `$core_fleetChasingGhost` | `boolean` | 标记追逐幽灵 |
| `FLEET_NOT_CHASING_GHOST` | `$core_fleetNotChasingGhost` | `boolean` | 禁止追逐幽灵 |
| `FLEET_CHASING_GHOST_RANDOM` | `$core_fleetChasingGhostRandom` | `boolean` | 随机追逐幽灵 |

过期时间按生涯时间推进。暂停时生涯时间不增长，标记的剩余有效期也不会按真实时间减少。

`set(key, value)` 写入无期限值，`set(key, value, expire)` 按生涯日设置期限；`getExpire(...)` 的 -1 表示没有期限。`get()` 保留原对象，`getBoolean`、`getFloat`、`getLong` 和 `getInt` 按指定类型读取。`getMemoryWithoutUpdate()` 取得现有事实，当前用于先保存原状态。

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

**恢复同时保存存在状态、原对象与剩余期限。** 原键存在时用三参数 `set(...)` 写回，原键缺席时 `unset(...)`。本例在同一轮执行恢复，剩余期限按这次操作前的值保留。

## 完整最小例

两个类保存到前述路径，保留新档推进后的登记。

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
2. 核对日志中的 `statsRebuilt`、`fleetPoints` 和成员移除结果。
3. 搜索 `GearIn fleet memory` 与 `restored`，比较 `busy`、`patrolDistance` 的写入、原存在状态和剩余期限。
4. 重新读取两个键，确认恢复为脚本执行前的存在状态和值。

预期结果：

- 装配修改后 `stats` 对象发生重建；
- 舰队汇总 能读取当前舰队汇总；
- Memory 标记按指定类型读取；
- 过期和恢复不会污染测试存档。

## 故障分析

- **数值没有变化**：没有触发重算，或读取的是旧 `stats` 引用。
    - **解决方案**：在 `setVariant` 或成员状态写入后调用 `updateStats()`，再重新取得 `getStats()` 并读取 最终数值。

- **舰长效果缺失**：成员没有加入 `FleetDataAPI`，或技能作用域不适用。
    - **解决方案**：确认成员已经加入目标舰队，读取 `member.getCaptain()` 与 `fleet.getCommander()`，再检查技能的作用域和等级。

- **舰队汇总 停留旧值**：集合变化后使用了缓存的汇总值。
    - **解决方案**：重新取得 `fleet.getStats()`、`fleet.getFleetPoints()` 和 `fleet.getFleetData()`，不要继续使用写入前的对象引用。

- **Memory 类型错误**：写入类型与读取方法不一致。
    - **解决方案**：用 `get()` 查看原始对象类型，再选择 `getBoolean()`、`getFloat()`、`getLong()` 或 `getInt()`；无法确定类型时不要强制转换。

- **标记持续存在**：恢复时没有区分原来不存在和原来存在。
    - **解决方案**：同时保存 `contains`(`key`) 和原值，原来没有键时调用 `unset()`，原来有键时写回原值并恢复过期时间。

- **临时 `modifier` 在暂停后仍然存在**：把真实秒数当作了生涯天数，或测试期间没有继续推进生涯时间。
    - **解决方案**：按生涯天数设置有效期，在暂停和继续运行两个阶段分别记录 `getExpire()` 与当前值。

- **Memory 标记影响了其他 AI**：测试使用了原版常用键，却没有保存和恢复原状态。
    - **解决方案**：写入前保存 `contains`、原值和过期时间，测试结束后按原存在状态恢复；演示只使用短有效期。

## 总结

装配与正式效果重建成员 `stats`，舰队 `stats` 和集合另行提供汇总。读取时确认当前对象，临时修饰使用稳定来源与生涯日期限，`Memory` 实验则完整恢复存在、值和过期状态。

编者建议先把一次修改的来源与恢复对应清楚，再让它持续运行。重算后的旧引用与恢复遗漏都可能让日志看起来正常，实际读取却已经换了对象或范围。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员 `stats`；
- `com.fs.starfarer.api.fleet.MutableFleetStatsAPI`：舰队 `stats`；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值与过期；
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：舰队标记。
