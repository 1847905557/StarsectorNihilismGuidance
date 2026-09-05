# 修改舰船数值

本节从 `MutableShipStatsAPI` 开始，说明普通 `MutableStat`、动态 `StatBonus` 和不同修改入口的职责。您会分别看到战斗插件、船插重建和战术系统如何写入、读取和清理舰船数值。

*本节预期阅读时间：\~22 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 从 `ShipAPI` 取得普通 stats 和 Dynamic；
- 选择 `MutableStat` 或 `StatBonus` 写入修饰；
- 在 `applyEffectsBeforeShipCreation`、`advance`、`apply` 和 `unapply` 中安排修改；
- 根据日志和游戏行为定位数值未生效或未清理的问题。

## 前置知识

- [C1-S1] 的 MOD ID、包路径与类名规则；
- [C2-S3] 的战术系统数据登记与效果脚本；
- [C4-S1] 的 `GearIn_test` 测试战役；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C8-S3] 的战斗插件注册、暂停判断与每帧回调。

## 取得 MutableShipStatsAPI

`ShipAPI.getMutableStats()` 是舰船数值的统一入口。普通 getter 返回 `MutableStat`，例如 `getMaxSpeed()`、`getFluxCapacity()` 和 `getFluxDissipation()`；这些对象保存固定 base 以及当前的 flat、percent、mult 修饰。

```java
ShipAPI ship = engine.getPlayerShip();
MutableShipStatsAPI stats = ship.getMutableStats();
MutableStat maxSpeed = stats.getMaxSpeed();
```

读取 `maxSpeed.getBaseValue()` 可以看到基础值，读取 `getModifiedValue()` 可以看到当前全部修饰后的值。战斗插件取得玩家舰时，先排除空对象、残骸、假舰和未部署状态，避免把写入落到没有实际玩家舰意义的对象上。

## 读取 DynamicStatsAPI

`MutableShipStatsAPI.getDynamic()` 返回 `DynamicStatsAPI`。Dynamic 按字符串 ID 保存动态统计项；`getMod(id)` 返回 `StatBonus`，写入方法与 `MutableStat` 相似，但读取时必须给出消费方使用的基础值：

```java
DynamicStatsAPI dynamic = stats.getDynamic();
StatBonus hullmodLimit = dynamic.getMod(Stats.MAX_PERMANENT_HULLMODS_MOD);
hullmodLimit.modifyFlat("GearIn_dynamic_example", 1f);
float effective = hullmodLimit.computeEffective(2f);
```

`computeEffective(2f)` 表示以 `2` 作为基础值计算结果。Dynamic 自身只保存修饰，具体效果由游戏读取这个 ID 的消费方决定；写入后没有对应读取方时，日志里的 Dynamic 数值会变化，游戏界面和行为仍保持原值。

`getStats()` 返回动态 `MutableStat` 集合，`getMods()` 返回动态 `StatBonus` 集合，适合在调试时确认对象是否已经登记。清理时使用 `unmodify(id)`，并复用写入时的同一个 modifier ID。

## 使用普通 MutableStat

### modifier 表速查表

`MutableStat` 保存一个 base 和三张以 modifier ID 为键的修饰表：

| 表 | 修改方法 | 汇总方式 | 单位语义 |
|---|---|---|---|
| `flatMods` | `modifyFlat(source, value)` | 相加 | 直接加减数值 |
| `percentMods` | `modifyPercent(source, value)` | 按 base 相加 | 基础值的百分比 |
| `multMods` | `modifyMult(source, value)` | 连乘 | 整体倍率 |

::: caution 不要用负 percent 抵消属性

负的 `percent` 修饰不是规范的减益实现方式。

一方面，它只按 base 值计算，即使是`modifyPercent(-100)` 也只能抵消 base。另一方面，负 percent 非常容易在无意间导致结果为负数。

:::

重算公式如下：

```text
modified = (base + base * percentMod / 100 + flatMod) * mult
```

这个公式直接对应三种用途：固定增量使用 `flat`，按基础值缩放使用 `percent`，整体倍率使用 `mult`。`getModifiedValue()` 每次读取都会返回当前结果，不需要额外刷新。base 为 `0` 的 `MutableStat` 使用 `percent` 没有实际效果。

### 修改与读取方法速查表

| 族 | 方法 | 语义 |
|---|---|---|
| 写入 | `modifyFlat` / `modifyPercent` / `modifyMult` | 同一 ID、同一类型覆盖旧条目 |
| 强制写入 | `modifyFlatAlways` / `modifyPercentAlways` / `modifyMultAlways` | 无条件保留条目 |
| 解除 | `unmodify(source)` | 清除该 ID 在三张表中的条目 |
| 定向解除 | `unmodifyFlat` / `unmodifyPercent` / `unmodifyMult` | 只清理指定表 |
| 读取 | `getBaseValue` / `getModifiedValue` / `getFlatMod` / `getPercentMod` / `getMult` / `isUnmodified` | 读取基础值、修饰结果和表状态 |
| 改 base | `setBaseValue(base)` | 修改基础值并重新计算 |

### 使用稳定的 modifier ID

写入、覆盖和解除必须复用同一个稳定 ID。同一来源使用新 ID 会增加新的条目；不同来源共用 ID 会互相覆盖；清理时使用不同 ID 会留下原有修饰。普通 `MutableStat` 和 Dynamic 的 `StatBonus` 都遵守这个规则。

## 选择修改入口

`applyEffectsBeforeShipCreation(...)` 用于船插在生涯 stats 重建和战斗舰船创建前写入稳定修饰。`advance(...)` 用于战斗运行期取得当前舰船、读取实时结果和执行临时修改。两者作用对象相同，但调用时机不同。

战术系统的 `apply(...)` 会在效果持续期间按 `effectLevel` 反复写入修饰，`unapply(...)` 在效果结束时清理。系统脚本使用传入的 `id` 作为普通 stat 的来源 ID；Dynamic 则使用自己的稳定 ID。

## 插件与船插示例

本主题使用两个互补的类：`GearInStatsHullMod` 展示 `applyEffectsBeforeShipCreation`，`GearInShipStatsProbe` 展示战斗插件中的 `advance`。两个类分别承担重建入口和运行期入口，避免把不同生命周期塞进同一个实现类。

### 在 applyEffectsBeforeShipCreation 中写入

船插收到的 `MutableShipStatsAPI` 已经属于当前舰船。固定速度修饰写入普通 `MutableStat`，动态上限写入 `DynamicStatsAPI.getMod(...)`；反复重建时复用稳定 ID，结果保持不变。

::: details 完整成品：GearInStatsHullMod

```java
package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInStatsHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInStatsHullMod.class);
    private static final String SPEED_ID = "GearIn_stats_hullmod_speed";
    private static final String DYNAMIC_ID = "GearIn_stats_hullmod_dynamic";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(SPEED_ID, 10f);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(DYNAMIC_ID, 1f);
        LOG.info("GearIn hullmod stats: speed=" + stats.getMaxSpeed().getModifiedValue()
                + " dynamicMaxHullmods="
                + stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).computeEffective(2f));
    }
}
```

:::

### 在 advance 中读取和临时修改

战斗插件每帧取得当前玩家舰的 `MutableShipStatsAPI`。前十秒写入普通速度修饰和 Dynamic 修饰，之后用相同 ID 清理；日志同时记录 `MutableStat.getModifiedValue()` 与 `StatBonus.computeEffective(2f)`。

::: details 完整成品：GearInShipStatsProbe

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * 完整最小例：战斗插件中的 MutableShipStatsAPI 与 DynamicStatsAPI。
 *
 * 本插件由战斗上下文注册，只在本场战斗内取得并使用舰船 stats：
 * - advance() 先检查暂停和玩家舰对象，再取得 MutableShipStatsAPI；
 * - 普通 MutableStat 用 getModifiedValue() 读取当前战斗数值；
 * - DynamicStatsAPI.getMod() 返回 StatBonus，必须用 computeEffective(base) 读取结果；
 * - 每帧重复写入复用稳定 ID，结束时用同一 ID 清理。
 */
public class GearInShipStatsProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInShipStatsProbe.class);

    private static final String MOD_SPEED = "GearIn_ship_stats_probe_speed";
    private static final String MOD_DYNAMIC = "GearIn_ship_stats_probe_dynamic";
    private static final float REMOVE_AT = 10f;

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn stats probe init: engine ready");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) {
            return;
        }
        MutableShipStatsAPI stats = ship.getMutableStats();

        float t = engine.getTotalElapsedTime(false);
        if (t < REMOVE_AT) {
            applyModifiers(stats);
        } else {
            unapplyModifiers(stats);
        }

        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logStats(ship, stats);
        }
    }

    /** 战斗运行期重复写入普通 MutableStat 和 Dynamic StatBonus。 */
    private void applyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyFlat(MOD_SPEED, 20f);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                .modifyFlat(MOD_DYNAMIC, 1f);
    }

    /** 使用相同 ID 清理普通 stat 与 Dynamic。 */
    private void unapplyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().unmodify(MOD_SPEED);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).unmodify(MOD_DYNAMIC);
    }

    /** 周期报告普通 MutableStat 与 Dynamic 的读取结果。 */
    private void logStats(ShipAPI ship, MutableShipStatsAPI stats) {
        MutableStat maxSpeed = stats.getMaxSpeed();
        DynamicStatsAPI dynamic = stats.getDynamic();
        StatBonus hullmodLimit = dynamic.getMod(Stats.MAX_PERMANENT_HULLMODS_MOD);
        LOG.info("GearIn stats read: hull=" + ship.getHullSpec().getHullId()
                + " maxSpeed base=" + maxSpeed.getBaseValue()
                + " modified=" + maxSpeed.getModifiedValue()
                + " flat=" + maxSpeed.getFlatMod()
                + " unmodified=" + maxSpeed.isUnmodified()
                + " dynamicMaxHullmods=" + hullmodLimit.computeEffective(2f)
                + " dynamicEntries=" + dynamic.getMods().size());
    }
}
```

:::

## 战术系统示例

战术系统把修改绑定到系统状态。`apply` 在前摇、全功率和后摇期间持续调用，`effectLevel` 从 `0` 变化到 `1` 再回到 `0`；`unapply` 在系统结束时清理普通 stat 和 Dynamic。

::: warning apply 与 unapply 必须成对

`apply` 写入的每个 modifier ID 都必须在 `unapply` 中使用同一个 ID 清理。普通 stat 与 Dynamic 使用不同来源时，必须分别调用对应的 `unmodify`。

:::

::: details 完整成品：GearInDynamicStatsSystem

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInDynamicStatsSystem extends BaseShipSystemScript {

    private static final float SPEED_BONUS = 80f;
    private static final String DYNAMIC_ID = "GearIn_dynamic_system";

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                .modifyFlat(DYNAMIC_ID, effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).unmodify(DYNAMIC_ID);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("dynamic stats system active", false);
        }
        return null;
    }
}
```

:::

`.system`、`ship_systems.csv` 和 `ship_data.csv` 的登记关系沿用 [C2-S3]。本节只增加效果脚本 `data.scripts.gearin.shipsystems.GearInDynamicStatsSystem`，并观察 `apply`、`effectLevel` 与 `unapply` 对数值的影响。

## 验证方案

前置数据：`GearIn_test` 测试战役、已注册的战斗插件、`GearInStatsHullMod`、`GearInShipStatsProbe` 和 `GearInDynamicStatsSystem`。

1. 构建 MOD，确认 JAR 中包含 `GearInStatsHullMod.class`、`GearInShipStatsProbe.class` 和 `GearInDynamicStatsSystem.class`。
2. 进入 `GearIn_test`，观察船插加载日志，核对 `GearIn hullmod stats` 的普通 stat 与 Dynamic 数值。
3. 进入战斗并运行十秒，搜索 `GearIn stats read`，比较 `maxSpeed modified`、`dynamicMaxHullmods` 和 `dynamicEntries`。
4. 十秒后继续观察日志，确认普通 stat 恢复基础值，Dynamic 修饰使用相同 ID 清理。
5. 激活战术系统，观察速度随 `effectLevel` 上升和下降，结束后确认速度与 Dynamic 修饰恢复。
6. 暂停战斗约十秒再恢复，确认插件暂停期间没有新增 stats 报告。

预期结果：

- JAR 中存在三个新增类的 `.class` 条目；
- 船插日志显示 `applyEffectsBeforeShipCreation` 已写入普通 stat 与 Dynamic；
- 插件运行期间 `maxSpeed modified` 高于 `base`，`dynamicMaxHullmods` 高于 `computeEffective(2f)` 的基础值；
- 清理后 `maxSpeed` 回到基础值，Dynamic 条目不再保留本节 modifier ID；
- 战术系统前摇和后摇中的速度修饰随 `effectLevel` 变化，`unapply` 后恢复原值；
- 暂停期间没有新的运行期报告。

## 故障分析

- **普通 stat 数值持续增加**：每帧使用随机、时间戳或递增 modifier ID，修饰表不断新增条目。

    - **解决方案**：复用固定 ID，并在结束时使用同一个 ID 调用 `unmodify`。

- **Dynamic 日志有变化但游戏行为没有变化**：写入的动态 stat 没有被当前功能消费，或 `computeEffective` 使用了错误的基础值。

    - **解决方案**：核对动态 stat ID、实际消费方和 `computeEffective(baseValue)` 的基准值。

- **生涯数值没有变化**：只在战斗插件 `advance()` 中写入修改。

    - **解决方案**：把生涯和战斗创建阶段需要保留的修改写入 `applyEffectsBeforeShipCreation(...)`。

- **战术系统结束后速度或 Dynamic 仍然增加**：`unapply()` 没有清理 `apply()` 写入的全部 ID。

    - **解决方案**：让普通 stat 和 Dynamic 分别使用对应的稳定 ID，并在 `unapply()` 中逐项清理。

- **普通 stat 使用 percent 没有效果**：目标 stat 的 base 为 `0`，percent 修饰没有计算基准。

    - **解决方案**：读取目标 stat 的 base，改用 flat、mult 或实际消费方支持的 Dynamic stat。

## 总结

`MutableShipStatsAPI` 提供普通 stats 与 Dynamic 的统一访问入口。普通 getter 返回 `MutableStat`，按固定 base 计算 flat、percent 和 mult；`getDynamic()` 返回 `DynamicStatsAPI`，通过 `StatBonus` 和 `computeEffective(baseValue)` 按消费方提供的基础值计算动态结果。

船插的 `applyEffectsBeforeShipCreation` 负责在 stats 重建和舰船创建前写入稳定修饰，战斗插件的 `advance` 负责运行期读取和临时修改，战术系统的 `apply` 与 `unapply` 负责效果期间的持续写入和结束清理。稳定 modifier ID、明确的 Dynamic 消费方和成对的清理调用共同决定数值是否能够稳定复现。

## 建议参考项

- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：普通 stats 与 Dynamic 的访问入口；
- `com.fs.starfarer.api.combat.MutableStat`：base、flat、percent、mult 与普通 modifier 表；
- `com.fs.starfarer.api.combat.StatBonus`：Dynamic 修饰、`computeEffective` 与 `unmodify`；
- `com.fs.starfarer.api.util.DynamicStatsAPI`：动态 stat 的 `getMod`、`getStats` 与 `getMods`；
- `com.fs.starfarer.api.combat.HullModEffect`：`applyEffectsBeforeShipCreation` 的生涯与战斗创建入口；
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：`apply`、`unapply` 与 `effectLevel`；
- `Starsector/starsector-core/starsector.log`：核对普通 stat、Dynamic 和入口生命周期日志。
