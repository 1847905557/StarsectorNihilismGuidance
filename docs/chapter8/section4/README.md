# 修改舰船数值

第二章的船插和战术系统已经使用过数值修改。多个效果同时作用于舰船时，各项加成会合并计算；效果结束后，还需要解除自己的修饰。本节以速度和 S-mod 上限为例，讲解普通数值与动态统计项，并比较船插、战斗插件和战术系统的写入时机。

*本节预期阅读时间：\~25 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 从 `ShipAPI` 取得普通数值与动态统计项；
- 选择 `MutableStat` 或 `StatBonus` 写入修饰，并读取结果；
- 在船插、战斗插件和战术系统的回调中安排修改与清理；
- 根据日志与游戏表现定位加成未生效或未撤销的问题。

## 前置知识

- [C2-S1] 的船插登记与 `modifyPercent`、`modifyFlat` 用法；
- [C2-S3] 的 `GearIn_assaultfeed`、战术系统效果脚本与舰船挂载；
- [C4-S1] 的 `GearIn_test` 测试战役；
- [C6-S2] 的源码布局与 [C6-S4] 的 JAR 构建、声明；
- [C8-S3] 的战斗插件注册、暂停判断与每帧回调。

## 取得舰船数值

在战斗代码中取得玩家舰的 `MutableShipStatsAPI`，再读取最大速度。以下局部片段使用已经确认可用的玩家舰：

```java
ShipAPI ship = engine.getPlayerShip();
MutableShipStatsAPI stats = ship.getMutableStats();
MutableStat maxSpeed = stats.getMaxSpeed();
```

`getMutableStats()` 取得这艘舰船的数值对象，`getMaxSpeed()` 再取得最大速度。`getFluxCapacity()` 和 `getFluxDissipation()` 等普通 getter 也按这种方式返回 `MutableStat`。

每帧插件取得玩家舰时，要处理尚未部署、舰船已被摧毁和逃生艇等状态。完整示例在读取数值前检查这些条件。

`MutableStat` 保存基础值 `base` 和当前修饰。`getBaseValue()` 返回基础值，`getModifiedValue()` 返回合并全部修饰后的结果。比较加成时，先区分这两个值。

## 写入与合并修饰

给最大速度增加固定的 20，并读取修改结果，局部片段如下：

```java
maxSpeed.modifyFlat("GearIn_speed_example", 20f);
float speed = maxSpeed.getModifiedValue();
```

`modifyFlat` 沿用第二章的写法。`"GearIn_speed_example"` 是来源 ID，`20f` 是增量；这项修饰被写入当前舰船的最大速度对象。

### 数值修饰速查表

| 修饰 | 写入方法 | 汇总与单位 |
|---|---|---|
| flat | `modifyFlat(source, value)` | 各来源增量相加，与目标数值同单位 |
| percent | `modifyPercent(source, value)` | 各来源百分比相加，按基础值计算 |
| mult | `modifyMult(source, value)` | 各来源倍率连乘，作用于此前合并结果 |

`MutableStat` 分别用 `flatMods`、`percentMods` 和 `multMods` 保存这三类修饰，最终按下面的公式计算：

```text
modified = (base + base * percentMod / 100 + flatMod) * mult
```

例如基础速度为 80，再加 20、加基础值的 25%、乘 0.5，结果是：

```text
(80 + 80 * 25 / 100 + 20) * 0.5 = 60
```

**percent 按 base 计算，mult 作用于合并后的整体。** 需要整体减半时使用 `modifyMult(source, 0.5f)`；base 为 0 时，percent 的贡献也为 0。

::: warning 根据计算对象选择减益写法

负 percent 同样按 base 计算。例如基础值 80、固定增量 20，再写入 -100% 时，结果仍为 20。数值还可能被减到负数。编者建议您在需要整体缩放时使用 mult，并确认目标属性实际允许的范围。

:::

修改后调用 `getModifiedValue()` 即可取得当前结果。`setBaseValue(...)` 用于修改基础值；船插和战术系统通常在基础值之上添加修饰。

### 修改与读取方法速查表

| 操作 | 方法 | 当前用途 |
|---|---|---|
| 写入 | `modifyFlat`、`modifyPercent`、`modifyMult` | 同来源、同类型覆盖已有值 |
| 保留条目 | `modifyFlatAlways`、`modifyPercentAlways`、`modifyMultAlways` | 即使数值为中性值，也写入条目；按签名提供描述参数 |
| 全部解除某来源 | `unmodify(source)` | 清除该来源在三张表中的修饰 |
| 解除某类型 | `unmodifyFlat`、`unmodifyPercent`、`unmodifyMult` | 清除该来源的指定类型修饰 |
| 读取结果 | `getBaseValue`、`getModifiedValue` | 读取基础值与最终值 |
| 读取汇总 | `getFlatMod`、`getPercentMod`、`getMult` | 查看三类修饰各自的合计 |
| 检查状态 | `isUnmodified` | 检查修饰表是否为空 |
| 修改基础值 | `setBaseValue` | 设置 base，并在读取时重新计算 |

### 使用稳定的来源 ID

在每帧回调中重复执行下面的局部片段：

```java
stats.getMaxSpeed().modifyFlat("GearIn_speed_example", 20f);
```

**同一个来源 ID、同一类修饰更新同一条记录。** 因此每帧写入仍是固定加 20。换一个来源 ID，则新增一项参与计算的修饰。

不同效果使用独立的稳定 ID，同一个效果写入与解除时使用相同 ID。下面的局部片段清理刚才的速度修饰：

```java
stats.getMaxSpeed().unmodify("GearIn_speed_example");
```

清理后，其他船插与技能的加成继续参与计算。编者建议您调试时同时检查来源记录和最终数值；舰船已有其他加成时，仅凭最终值判断自己的效果是否残留，很容易误判。

## 读写动态统计项

普通速度可以直接通过 getter 取得，动态统计项则用字符串 ID 查找。`stats.getDynamic()` 返回 `DynamicStatsAPI`，其中 `getMod(id)` 返回保存修饰的 `StatBonus`。

下面用 S-mod 数量上限演示一个局部片段：

```java
DynamicStatsAPI dynamic = stats.getDynamic();
StatBonus hullmodLimit = dynamic.getMod(Stats.MAX_PERMANENT_HULLMODS_MOD);
hullmodLimit.modifyFlat("GearIn_dynamic_example", 1f);
float effective = hullmodLimit.computeEffective(2f);
```

`Stats.MAX_PERMANENT_HULLMODS_MOD` 指定动态统计项，`GearIn_dynamic_example` 标记本次修饰的来源。前者用于取得对象，后者用于覆盖和清理其中的修饰。

`StatBonus` 保存 flat、percent、mult 修饰，基础值在读取时提供。`computeEffective(2f)` 就是把 2 作为基准，连同当前修饰计算结果。原版的 `Misc.getMaxPermanentMods(...)` 读取这项修饰时，从设置中的 `maxPermanentHullmods` 取得基准，再计算并取整。

**动态修饰的实际效果由读取方和计算基准决定。** 本节日志使用 `2f` 演示计算，实际 S-mod 上限由装配侧按设置中的基准读取。战斗日志中的这个值用于观察修饰的写入与清理。

`getMods()` 返回动态 `StatBonus` 集合，`getStats()` 返回动态 `MutableStat` 集合；后者可以通过 `getStat(id)` 按需取得，默认基础值为 1。调试时可以查看这两类集合，但撤销自己的效果仍使用来源 ID 调用 `unmodify`。

自定义功能也可以使用自己的动态 ID，同时在功能代码中读取修饰并计算结果。这样不同船插、技能或环境效果可以向同一项统计写入加成，再由该功能统一使用。

## 把修改放到对应时机

同一种数值可以从不同入口修改。**效果的适用场合与持续时间决定写入位置**：

- **船插的 `applyEffectsBeforeShipCreation(...)`**：在生涯数值重建和战斗舰船创建前写入，适合安装船插后持续存在的加成。
- **战斗插件的 `advance(...)`**：在当前战斗中读取实时对象和数值，适合计时与临时效果。
- **战术系统的 `apply(...)` 与 `unapply(...)`**：在系统效果期间持续写入，再在结束时清理。

本节分别使用 `GearInStatsHullMod`、`GearInShipStatsProbe` 与 `GearInDynamicStatsSystem` 实现这三种效果，完整类在局部操作说明后给出。

### 船插写入持续加成

在 `GearInStatsHullMod.applyEffectsBeforeShipCreation(...)` 中写入以下局部片段：

```java
stats.getMaxSpeed().modifyFlat(SPEED_ID, 10f);
stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(DYNAMIC_ID, 1f);
```

船插增加 10 速度，并给动态上限增加 1。每次重建仍使用 `SPEED_ID` 与 `DYNAMIC_ID`，更新的还是自己的记录。两个 ID 在完整类中固定为 `GearIn_stats_hullmod_speed` 与 `GearIn_stats_hullmod_dynamic`。

### 战斗插件先取得当前引擎

在 `GearInShipStatsProbe.advance(...)` 开头写入：

```java
if (engine == null) {
    engine = Global.getCombatEngine();
    LOG.info("GearIn stats probe init: engine ready");
}
if (engine.isPaused()) {
    return;
}
```

本插件在战斗回调中第一次使用引擎时取得引用，之后只在本场战斗内使用。接着取得玩家舰，并确认它存活、属于可操作舰船；暂停帧在这里结束。

### 前十秒写入，之后清理

在引擎和玩家舰检查之后，用下面的局部片段安排时间：

```java
float t = engine.getTotalElapsedTime(false);
if (t < REMOVE_AT) {
    applyModifiers(stats);
} else {
    unapplyModifiers(stats);
}
```

`REMOVE_AT` 为 `10f`，时间从本场战斗开始计算，暂停时间由 `false` 排除。前十秒，插件用自己的 ID 加 20 速度、加 1 动态修饰；十秒后，持续解除自己的来源。

日志每两秒输出普通数值、Dynamic 计算结果和两个来源 ID 的存在状态。十秒后，插件来源被移除，船插的 10 速度继续保留。**清理结果应回到保留其他来源的基线**，后面的验证按这个结果比较。

### 战术系统跟随效果强度

`GearInDynamicStatsSystem.apply(...)` 的局部片段如下：

```java
stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
        .modifyFlat(DYNAMIC_ID, effectLevel);
```

`SPEED_BONUS` 为 `80f`。前摇中，`effectLevel` 从 0 升至 1，全功率保持 1，后摇再回到 0，速度加成也随它变化。普通速度使用游戏传入的系统 `id`，动态修饰使用 `GearIn_dynamic_system`。

在 `unapply(...)` 中，用相同 ID 清理：

```java
stats.getMaxSpeed().unmodify(id);
stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).unmodify(DYNAMIC_ID);
```

::: warning 写入与清理一一对应

`apply` 写入的每个来源都要在 `unapply` 中清理。普通数值与 Dynamic 使用各自的来源时，分别调用对应对象的 `unmodify`。

:::

## 登记三种示例

三个 Java 文件分别放入 `src/data/scripts/gearin/hullmods/`、`src/data/scripts/gearin/combat/` 与 `src/data/scripts/gearin/shipsystems/`，包名与完整文件一致。

在 `data/hullmods/hull_mods.csv` 追加下列记录，已有表头时只追加第二行。船插 ID `GearIn_stats_hullmod` 在这里定义，四档装配点沿用最初示例的 3、6、9、12：

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
数值测试船插,GearIn_stats_hullmod,0,,,engines,引擎,1000,TRUE,,,3,6,9,12,data.scripts.gearin.hullmods.GearInStatsHullMod,"最大速度增加 10，S-mod 上限增加 1。","用于观察数值修饰。",,
```

在 `data/config/settings.json` 的 `plugins` 对象中加入本插件的局部登记：

```json
{
    "plugins": {
        "gearInShipStatsProbe": "data.scripts.gearin.combat.GearInShipStatsProbe"
    }
}
```

沿用 [C2-S3] 的 `GearIn_assaultfeed` 系统与工匠挂载关系，将 `GearIn_assaultfeed.system` 的 `statsScript` 值改为：

```text
data.scripts.gearin.shipsystems.GearInDynamicStatsSystem
```

系统 CSV 与舰船的 `system id` 沿用已有登记。测试时安装船插，进入战斗观察临时修饰，再激活系统比较三种来源的效果。

## 完整最小例

三个类分别承担船插、临时战斗修饰和战术系统效果。按上面的路径保存，再构建 `jars/GearIndustry.jar`。

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
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/** 周期读取玩家舰数值，并在战斗开始十秒后移除临时修饰。 */
public class GearInShipStatsProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInShipStatsProbe.class);

    private static final String MOD_SPEED = "GearIn_ship_stats_probe_speed";
    private static final String MOD_DYNAMIC = "GearIn_ship_stats_probe_dynamic";
    private static final float REMOVE_AT = 10f;

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            engine = Global.getCombatEngine();
            LOG.info("GearIn stats probe init: engine ready");
        }
        if (engine.isPaused()) {
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
                + " dynamicEntries=" + dynamic.getMods().size()
                + " probeSpeedPresent=" + maxSpeed.getFlatMods().containsKey(MOD_SPEED)
                + " probeDynamicPresent=" + hullmodLimit.getFlatBonuses().containsKey(MOD_DYNAMIC));
    }
}
```

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

## 验证方案

前置数据：使用 [C4-S1] 的 `GearIn_test` 与工匠测试装配，完成本节三个效果的登记和挂载；测试舰船保留本节数值船插，以便比较各来源。

1. 构建 MOD，用 `jar tf jars/GearIndustry.jar` 确认三个类的 class 条目存在，保存数据后重启游戏。
2. 在测试战役的装配界面安装 "数值测试船插"，观察 `GearIn hullmod stats` 日志，记录此时的速度基线。
3. 进入战斗并立即部署工匠，先保持战术系统关闭，在前十秒观察 `GearIn stats read`。
4. 十秒后继续观察，比较速度、`dynamicMaxHullmods`、`probeSpeedPresent` 和 `probeDynamicPresent`。
5. 激活战术系统，观察全功率与自动结束，观察速度和 Dynamic 计算值随 `effectLevel` 变化，以及结束后的恢复。
6. 暂停约十秒再恢复，比较暂停前后的运行期日志。

预期结果：

- JAR 中包含 `GearInStatsHullMod.class`、`GearInShipStatsProbe.class` 和 `GearInDynamicStatsSystem.class`。
- 船插写入速度 +10 和 Dynamic +1；其日志确认创建前回调已经执行。
- 前十秒，插件再加 20 速度和 1 动态修饰，两个 `probe*Present` 为 `true`；十秒后两者为 `false`。
- 临时效果清理后，速度回到保留其他来源的基线；以 2 计算的 Dynamic 同样保留船插的 +1。其他修饰均为空时，两阶段速度为 110 与 90，Dynamic 为 4 与 3；实际结果以实际日志为准。
- 战术系统满效果时增加 80 速度和 1 动态修饰，结束后回到激活前结果。
- `dynamicEntries` 用于观察动态集合大小，来源清理结果由两个 `probe*Present` 确认。
- 暂停期间产生的运行期报告数量为 0，恢复后继续按非暂停时间运行。

## 故障分析

- **普通数值每帧持续增加**：每次调用使用随机、时间戳或递增来源 ID，修饰表不断新增记录。
    - **解决方案**：为同一效果复用稳定 ID，并在结束时用相同 ID 调用 `unmodify`。

- **Dynamic 日志变化，实际功能保持原样**：当前功能没有读取该动态项，或测试时提供的基础值与读取方不同。
    - **解决方案**：核对动态统计项 ID、实际读取场合与 `computeEffective(baseValue)` 的基础值。

- **生涯数值没有变化**：效果只在战斗插件的 `advance()` 中写入。
    - **解决方案**：将安装后需要持续保留的修饰放进船插的 `applyEffectsBeforeShipCreation(...)`。

- **战术系统结束后仍有加成**：`unapply()` 未清理 `apply()` 写入的全部来源。
    - **解决方案**：分别核对普通数值与 Dynamic 的来源 ID，并在对应对象上解除。

- **percent 写入后结果不变**：目标数值的 base 为 0，百分比没有计算基准。
    - **解决方案**：读取 base，按效果用途选择 flat、mult 或有实际读取方的动态统计项。

## 总结

普通 `MutableStat` 保存基础值，动态 `StatBonus` 在计算时接收基础值；两者都使用来源 ID 管理修饰。覆盖与清理需要复用相同来源，结果则按 flat、percent 和 mult 的规则合并。

船插参与数值重建，战斗插件维护临时修饰，战术系统按效果强度持续写入并在结束时清理。排查数值问题时，沿对象、来源、读取方与调用时机核对，便能区分效果尚未写入、其他来源仍在生效和清理遗漏这几种情况。

## 建议参考项

- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：普通数值与 Dynamic 的访问入口。
- `com.fs.starfarer.api.combat.MutableStat`：base、三类修饰、覆盖、计算与解除方法。
- `com.fs.starfarer.api.combat.StatBonus`：动态修饰与 `computeEffective` 计算。
- `com.fs.starfarer.api.util.DynamicStatsAPI`：`getMod`、`getStat` 及两类动态集合。
- `com.fs.starfarer.api.util.Misc`：`getMaxPermanentMods` 提供 S-mod 上限的实际读取示例。
- `com.fs.starfarer.api.combat.HullModEffect`：创建前船插效果入口。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：战斗插件 `init` 的调用约定。
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：`apply`、`unapply` 与 `effectLevel`。
- `data/config/settings.json`：本节插件登记与 `maxPermanentHullmods` 基础值。
- `Starsector/starsector-core/starsector.log`：数值、修饰来源与回调的观察位置。
