# 实现市场条件

市场已有交易与生产，最后给它加一项环境条件。本节的 Warfront 提高危险度、降低流通性并增加新生成舰队的质量，每个实例用自己的 token 管理修饰。观察器比较应用、抑制、勘测与移除，倍率和计时也按插件的保存范围安排。

*本节预期阅读时间：\~24 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 在 `market_conditions.csv` 注册条件；
- 使用 `MarketConditionSpecAPI` 读取静态规格；
- 区分 spec ID、实例 token 和插件对象；
- 继承 `BaseMarketConditionPlugin` 实现条件生命周期；
- 使用实例 token 写入并清理市场修饰；
- 处理 `setParam(Object)`、抑制、survey 和重算；
- 读取危险度、流通性和舰队质量动态 stat。

## 前置知识

- [C12-S1] 的 `MarketAPI` 和市场 stat；
- [C7-S1] 的插件生命周期与持久数据；
- [C8-S4] 的 `MutableStat`、`StatBonus` 和 modifier ID；
- [C12-S1] 的市场推进与经济观察入口。

## 市场条件的对象组成

`market_conditions.csv` 保存静态注册行，`MarketConditionSpecAPI` 代表这条规格，`MarketConditionAPI` 代表挂在某个市场上的实例，`MarketConditionPlugin` 负责实际行为。

本节使用 `GearIn_warfront` 和 `Warfront`。它是市场条件，不设置 `planetary`，因此显示在市场条件区域。它提高危险度，降低流通性，并提升市场生成舰队时读取的舰队质量动态 stat。

## 注册市场条件身份

文件位置是 `data/campaign/market_conditions.csv`。原版表头顺序如下：

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
Warfront,GearIn_warfront,,,,data.scripts.gearin.campaign.GearInWarfrontCondition,"A contested market with elevated hazards and improved fleet readiness.",graphics/icons/markets/hostile_activity.png,1450
```

`name` 是默认显示名，`id` 是全局 spec key。代码调用 `addCondition()` 时传入的是这个 ID。

`tags` 为条件分类标签。本例不需要额外标签，因此留空。

`planetary` 留空表示市场条件；填入 `TRUE` 才会进入行星条件区域。

`decivRemove` 控制文明衰退时是否自动移除。本例留空，使用默认值。

`script` 必须是插件类完整名称，游戏在创建条件实例时按名称实例化它。

`desc` 是 tooltip 描述，支持 `$marketName` 等基类 token replacement。

`icon` 使用原版市场图标。`order` 只影响条件列表排序。

保存 `GearInWarfrontCondition.java` 和 `GearInConditionProbe.java` 到 `src/data/scripts/gearin/campaign/`，完整类名与 CSV 对应。向 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 添加局部调用，接在前面的产业与物品观察之后：

```java
GearInConditionProbe.log(market);
```

当前观察器只在一轮经济更新后运行，创建本次实例、比较并移除，原有条件继续保留。

## 读取市场条件规格

`MarketConditionSpecAPI` 位于 `com.fs.starfarer.api.characters` 包，保存 CSV 的静态字段：

```java
MarketConditionSpecAPI spec = Global.getSettings()
        .getMarketConditionSpec(GearInWarfrontCondition.CONDITION_ID);
LOG.info(String.format("id=%s name=%s script=%s icon=%s order=%.0f",
        spec.getId(), spec.getName(), spec.getScriptClass(),
        spec.getIcon(), spec.getOrder()));
```

`getId()`、`getName()`、`getScriptClass()` 和 `getIcon()` 适合做注册检查。`getDesc()` 用于读取 tooltip 原文，`getTags()` 和 `hasTag()` 用于分类。

`isPlanetary()` 和 `isDecivRemove()` 分别读取两个布尔字段。`getGenSpec()` 返回可选的 procgen 规格；本节条件没有 `condition_gen_data.csv` 行，因此它不参与自动生成。

规格对象不保存某个市场的危险度或流通性当前值。那些值来自 `MarketAPI` 和条件插件写入的 modifier。

## 创建运行时实例

市场通过 `addCondition()` 创建条件。无参数形式使用插件默认状态，带参数形式会在 `apply()` 前调用 `setParam()`：

```java
String token = market.addCondition(GearInWarfrontCondition.CONDITION_ID, 1.0f);
MarketConditionAPI condition = market.getSpecificCondition(token);
```

返回值是实例 token，例如 `GearIn_warfront_ab12c`。同一个规格添加两次会取得两个 token，精确移除也沿 token 执行。

`getCondition(id)` 和 `getFirstCondition(id)` 按 spec ID 查找首个实例。需要精确操作某一个实例时，使用 `getSpecificCondition(token)`。

条件实例属于创建它的市场。读取和移除时从目标市场取得实例，不把实例对象跨市场缓存。

## 条件插件的方法与调用时机

`init(MarketAPI, MarketConditionAPI)` 保存市场和实例引用。基类的 `getModId()` 会返回当前实例 token。

`setParam(Object)` 接收 `addCondition(id, param)` 的参数。大多数固定条件并不需要这个入口，本例只为展示 API 边界而接受正数 `Float`。

`apply(String id)` 在条件生效时写入市场修饰。传入的 `id` 是实例 token，应直接作为 modifier source 使用。

`advance(float amount)` 在市场推进时运行，`elapsed` 按传入的秒数累计。本例用它观察实例时间，三项修饰仍由 `apply(...)` 写入。

`unapply(String id)` 在移除或重算前清理修饰。当前三项写入各有对应的解除方法。

## 写入三项市场修饰

危险度使用 `market.getHazard()`，流通性使用 `market.getAccessibilityMod()`。舰队质量使用 `market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)`。

```java
String source = getModId();
market.getHazard().modifyFlat(source, 0.15f * multiplier, getName());
market.getAccessibilityMod().modifyFlat(source, -0.05f * multiplier, getName());
market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
        .modifyFlatAlways(source, 0.15f * multiplier, getName());
```

危险度是百分比形式的市场危险值，`0.15` 表示增加 15 个百分点。流通性以 1.0 为基础，负修饰会降低市场可达性。舰队质量 dynamic stat 由舰队生成逻辑读取，不等于某一支已经存在舰队的固定属性。

**规格 ID 选择条件，实例 token 隔离每份修饰。** 两个实例使用不同 token 后各自参与计算，移除当前一份时就只解除它的来源。

## 使用参数倍率

本例把 `Float` 参数解释为三项效果的统一倍率：

```java
if (param instanceof Float && ((Float) param) > 0f) {
    multiplier = (Float) param;
} else {
    multiplier = 1f;
}
```

`1.0f` 保持基准值，`2.0f` 使三项修饰翻倍。本例接受正数 `Float`，其余输入恢复默认 1；调用传 `1f` 或 `2f`，插件仍按当前市场统计项写入。

多数条件可以直接使用 CSV 固定效果，不需要调用带参数的添加入口。使用参数时，插件应记录实际倍率，便于排查存档中的不同实例。

## 配对清理修饰

```java
String source = getModId();
market.getHazard().unmodifyFlat(source);
market.getAccessibilityMod().unmodifyFlat(source);
market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
        .unmodifyFlat(source);
```

`reapplyConditions()` 会对条件执行 unapply，再对满足条件的实例执行 apply。固定 source 能保证重算不会积累重复 modifier。

## 使用抑制和重算

`suppressCondition(specId)` 把 spec ID 加入市场抑制集合。它不会立刻撤销当前效果；下一次 `reapplyConditions()` 才会执行 unapply 并跳过 apply。

`unsuppressCondition(specId)` 只解除集合标记，同样需要重算才能恢复效果。抑制和解除抑制应成对出现。

```java
market.suppressCondition(GearInWarfrontCondition.CONDITION_ID);
float before = market.getHazard().getModifiedValue();
market.reapplyConditions();
float suppressed = market.getHazard().getModifiedValue();
market.unsuppressCondition(GearInWarfrontCondition.CONDITION_ID);
market.reapplyConditions();
float restored = market.getHazard().getModifiedValue();
```

抑制只影响生效，不会从 `getConditions()` 删除实例。需要删除一个实例时，仍然使用它的 token。

## 区分 survey 状态

条件级 `isSurveyed()` 表示这一实例是否已勘测，`requiresSurveying()` 来自 procgen 规则。市场级 `getSurveyLevel()` 表示玩家对整个市场的勘测进度。

本例没有 procgen 行，因此 `requiresSurveying()` 为 `false`。观察器仍然手动读取并切换 `setSurveyed(false)` 和 `setSurveyed(true)`，用于说明状态接口，不把它当作本条件的生效门槛。

**全量重算检查勘测与抑制，单实例重算检查抑制。** `reapplyConditions()` 对所有实例先解除，再对已勘测且未抑制者应用；`reapplyCondition(token)` 精确重算一份，按抑制状态决定应用。当前观察器分别记录这些结果。

## 删除具体实例

`removeSpecificCondition(token)` 只删除 token 对应的一个实例，并调用该实例的 `unapply()`。

`removeCondition(specId)` 会删除同一 spec ID 的全部实例。页面示例只有一个实例，但实际代码应保留 `addCondition()` 返回的 token，以避免误删其他实例。

## 存档和界面边界

基类 `isTransient()` 默认为 `true`，读档时插件按规格重新建立，实例字段回到初始值。本例接收倍率并累计时间，因此覆写为 `false`，让这份插件状态随条件保存：

```java
@Override
public boolean isTransient() {
    return false;
}
```

固定效果可以沿用默认值，需要按生涯日累计的功能也可使用 `BaseMarketConditionPlugin2`。另一种保存方式是把实际状态放入市场 Memory，重建时再读取。**保存范围由当前功能状态决定。** 倍率设为 2 却在读档后恢复默认 1，已经改变了效果设计。

基类默认提供 `getName()`、`getIconName()`、`getTokenReplacements()`、`hasCustomTooltip()` 和 `createTooltip()`。CSV 描述由 token replacement 生成最终文本。

`isTooltipExpandable()`、`getTooltipWidth()` 和 `runWhilePaused()` 控制界面与暂停推进行为。本例不覆盖复杂 tooltip，只使用基类的描述和图标入口。

## 对照原版条件的插件职责

下面的速查表用于比较原版条件插件与本节自定义插件的职责。

### 原版条件速查表

| 条件 | 插件 | 主要效果 | 本节关系 |
| --- | --- | --- | --- |
| `ore_sparse` | `ResourceDepositsCondition` | 资源供给 | 原版 procgen 条件 |
| `hot` | `BaseHazardCondition` | 危险度 | 基类修饰范式 |
| `solar_array` | `SolarArray` | 抑制环境条件 | suppression 范式 |
| `GearIn_warfront` | `GearInWarfrontCondition` | 危险度、流通性、舰队质量 | 参数与 token 示例 |

## 完整最小例

两类保存到前述路径，保留条件注册与经济观察器调用。

::: details 完整成品：GearInWarfrontCondition

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInWarfrontCondition extends BaseMarketConditionPlugin {
    public static final String CONDITION_ID = "GearIn_warfront";
    private static final Logger LOG = Global.getLogger(GearInWarfrontCondition.class);
    private float multiplier = 1f;
    private float elapsed;

    @Override
    public void init(MarketAPI market, MarketConditionAPI condition) {
        super.init(market, condition);
        multiplier = 1f;
        elapsed = 0f;
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Float && ((Float) param) > 0f) {
            multiplier = (Float) param;
        } else {
            multiplier = 1f;
        }
    }

    @Override
    public void apply(String id) {
        String source = getModId();
        market.getHazard().modifyFlat(source, 0.15f * multiplier, getName());
        market.getAccessibilityMod().modifyFlat(source, -0.05f * multiplier, getName());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(source, 0.15f * multiplier, getName());
        LOG.info(String.format("Warfront applied: market=%s token=%s multiplier=%.2f hazard=%.2f accessibility=%.2f fleetQuality=%.2f",
                market.getName(), source, multiplier,
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f)));
    }

    @Override
    public void unapply(String id) {
        String source = getModId();
        market.getHazard().unmodifyFlat(source);
        market.getAccessibilityMod().unmodifyFlat(source);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(source);
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public float getElapsed() {
        return elapsed;
    }
}
```

:::

::: details 完整成品：GearInConditionProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInConditionProbe {
    private static final Logger LOG = Global.getLogger(GearInConditionProbe.class);

    private GearInConditionProbe() {
    }

    public static void log(MarketAPI market) {
        boolean wasSuppressed = market.isConditionSuppressed(GearInWarfrontCondition.CONDITION_ID);
        String token = market.addCondition(GearInWarfrontCondition.CONDITION_ID, 1.0f);
        MarketConditionAPI condition = market.getSpecificCondition(token);
        MarketConditionSpecAPI spec = condition.getSpec();
        MarketConditionPlugin plugin = condition.getPlugin();
        LOG.info(String.format("Warfront condition: id=%s token=%s name=%s script=%s surveyed=%s requiresSurveying=%s planetary=%s",
                condition.getId(), condition.getIdForPluginModifications(), condition.getName(),
                spec.getScriptClass(), condition.isSurveyed(), condition.requiresSurveying(), condition.isPlanetary()));
        LOG.info(String.format("Warfront values: hazard=%.2f accessibility=%.2f fleetQuality=%.2f plugin=%s",
                market.getHazard().getModifiedValue(), market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f),
                plugin.getClass().getSimpleName()));
        LOG.info("Warfront market survey level=" + market.getSurveyLevel());

        market.suppressCondition(GearInWarfrontCondition.CONDITION_ID);
        float beforeReapply = market.getHazard().getModifiedValue();
        market.reapplyConditions();
        LOG.info(String.format("Warfront suppressed: beforeReapplyHazard=%.2f afterReapplyHazard=%.2f",
                beforeReapply, market.getHazard().getModifiedValue()));
        market.unsuppressCondition(GearInWarfrontCondition.CONDITION_ID);
        market.reapplyConditions();
        LOG.info(String.format("Warfront restored: hazard=%.2f accessibility=%.2f fleetQuality=%.2f",
                market.getHazard().getModifiedValue(), market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f)));

        condition.setSurveyed(false);
        market.reapplyConditions();
        LOG.info("Warfront condition survey all: surveyed=" + condition.isSurveyed()
                + " hazard=" + market.getHazard().getModifiedValue());
        market.reapplyCondition(token);
        LOG.info("Warfront condition survey specific: hazard="
                + market.getHazard().getModifiedValue());
        condition.setSurveyed(true);
        market.removeSpecificCondition(token);
        if (wasSuppressed) {
            market.suppressCondition(GearInWarfrontCondition.CONDITION_ID);
        } else {
            market.unsuppressCondition(GearInWarfrontCondition.CONDITION_ID);
        }
        market.reapplyConditions();
        LOG.info("Warfront condition removed: present=" + market.hasSpecificCondition(token)
                + " suppressed=" + market.isConditionSuppressed(GearInWarfrontCondition.CONDITION_ID));
    }
}
```

:::

## 验证方案

前置数据：启用包含本节 `market_conditions.csv` 行和两个 Java 类的 GearIn MOD，并使用拥有 `gear_industry` 市场的测试存档。

1. 保存 CSV、两类与调用入口，构建重启，进入 Foundry 存档，推进一轮经济更新后查看 Warfront 日志。
2. 读取日志中的 `GearIn_warfront`、实例 token、`Warfront` 名称、插件类和规格脚本类。
3. 核对危险度、流通性和 `Stats.FLEET_QUALITY_MOD` 的动态值。
4. 观察 suppression 前的数值，执行 `reapplyConditions()`，再观察 suppression 后的数值。
5. 解除 suppression 并再次执行 `reapplyConditions()`，读取恢复后的三项数值。
6. 比较条件勘测、市场勘测与单实例、全量重算的日志，再查看本次 token 删除后的原状态。

预期结果：

- spec ID 为 `GearIn_warfront`，名称为 `Warfront`，插件类为 `GearInWarfrontCondition`；
- 参数 `1.0f` 下危险度修饰为 `+0.15`、流通性修饰为 `-0.05`、舰队质量修饰为 `+0.15`；
- suppression 重算前效果仍在，重算后效果被移除；
- unsuppression 并重算后，三项修饰恢复；
- `requiresSurveying()` 为 `false`，条件级和市场级 survey 值可以分别读取；
- `removeSpecificCondition(token)` 后实例不存在，三项市场修饰回到观察前状态。

## 故障分析

- **条件没有加载**：注册路径、ID 或脚本字段不一致。
    - **解决方案**：检查 `market_conditions.csv` 的表头、ID、脚本类和图标路径。

- **插件类无法实例化**：完整类名或 JAR 条目与注册不同。
    - **解决方案**：核对 `data.scripts.gearin.campaign.GearInWarfrontCondition` 与无参构造。

- **修饰互相覆盖**：不同实例共用规格 ID 作为来源。
    - **解决方案**：使用 `getModId()` 返回的实例 token，不要使用 `GearIn_warfront` 作为 source。

- **抑制后数值尚未变化**：尚未执行条件重算。
    - **解决方案**：调用 `reapplyConditions()`，再读取危险度、流通性和舰队质量。

- **删除后同类型条件都消失**：按规格 ID 执行了全类型移除。
    - **解决方案**：保存 `addCondition()` 返回的 token，并使用 `removeSpecificCondition(token)`。

- **参数没有生效**：输入类型或范围不符合当前正数 Float 约定。
    - **解决方案**：确认传入的是正数 `Float`，并在 `setParam()` 中记录倍率。

- **现有舰队质量保持原值**：动态项影响后续生成参数。
    - **解决方案**：读取 `Stats.FLEET_QUALITY_MOD` dynamic stat 的 `computeEffective(0f)`，不要只观察已经生成的舰队。

## 总结

Warfront 从规格创建当前市场的条件实例，倍率决定三项效果，实例 token 管理写入与解除。抑制、勘测和重算按各自入口影响生效，删除本次 token 后恢复原市场状态。

插件字段的保存也属于这份设计：固定效果可以重建，持久倍率与计时按需要保存。编者建议把注册身份、修饰来源和保存范围分别写清，再让条件长期留在市场上。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketConditionAPI`：市场条件实例和 token；
- `com.fs.starfarer.api.campaign.econ.MarketConditionPlugin`：条件插件生命周期；
- `com.fs.starfarer.api.characters.MarketConditionSpecAPI`：CSV 静态规格；
- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`：条件基类默认行为；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：条件添加、重算、抑制和移除入口。
