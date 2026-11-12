# 实现市场条件

市场条件可以把一组环境效果集中到同一个实例。本节实现 Warfront，提高危险度、降低流通性并修饰来源市场的舰队质量，再比较抑制、勘测与移除时的结果。实例 token 管理各项修饰，参数与保存方式决定这份条件怎样延续。

_本节预期阅读时间：\~24 分钟。_

_本节预期操作时间：\~35 分钟。_

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
- [C8-S2] 的 `MutableStat`、`StatBonus` 和 modifier ID；
- [C12-S1] 的市场推进与经济观察入口。

## 注册 Warfront 并接到当前市场

在 `data/campaign/market_conditions.csv` 注册 `GearIn_warfront`，显示名采用 `Warfront`，插件使用本节的 `GearInWarfrontCondition`。文件内容如下：

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
Warfront,GearIn_warfront,,,,data.scripts.gearin.campaign.GearInWarfrontCondition,"A contested market with elevated hazards and improved fleet readiness.",graphics/icons/markets/hostile_activity.png,1450
```

`script` 写完整类名，`icon` 沿用原版敌对活动图标，`order` 取 1450。`planetary` 留空使它位于条件栏的市场条件区域；设为 `TRUE` 时进入右侧的行星条件区域。`decivRemove` 控制文明衰退时移除，本例留空采用默认的 `false`，标签也留空。

`desc` 是 tooltip 正文，基类支持 `$marketName` 等替换字段。注册行保存 `MarketConditionSpecAPI`，实际效果由当前市场的条件实例与插件提供。

保存 `GearInWarfrontCondition.java` 与 `GearInConditionProbe.java` 到 `src/data/scripts/gearin/campaign/`。在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 中，将以下局部调用接到产业与物品观察之后：

```java
GearInConditionProbe.log(market);
```

观察器在整轮经济更新后创建一次条件，比较各种状态，最后移除本次实例并恢复原抑制标记。当前市场引用由经济观察器提供，条件本身属于创建它的市场。

## 用实例 token 管理效果

通过 `addCondition(...)` 添加 Warfront，返回值保存为 `token`。以下局部片段传入倍率 1，并精确取得刚创建的实例：

```java
String token = market.addCondition(GearInWarfrontCondition.CONDITION_ID, 1.0f);
MarketConditionAPI condition = market.getSpecificCondition(token);
```

`GearIn_warfront` 是规格 ID，每次添加返回不同的实例 token，例如 `GearIn_warfront_ab12c`。`getCondition(id)` 与 `getFirstCondition(id)` 按规格找首个实例，`getSpecificCondition(token)` 才对应这一份条件。

**规格 ID 用于选择，实例 token 用于隔离修饰。** 同类型条件可以有多个实例，来源都写规格 ID 时，效果会互相覆盖。`BaseMarketConditionPlugin.getModId()` 返回本实例的 token，`apply(String id)` 接收到的也是这个 token。

### 写入危险度、流通性与舰队质量

插件的 `init(...)` 保存市场和条件引用，然后由 `apply(...)` 写入效果。危险度通过 `getHazard()`，流通性通过 `getAccessibilityMod()`，舰队质量通过 `Stats.FLEET_QUALITY_MOD` 的动态修饰。局部代码如下：

```java
String source = getModId();
market.getHazard().modifyFlat(source, 0.15f * multiplier, getName());
market.getAccessibilityMod().modifyFlat(source, -0.05f * multiplier, getName());
market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
        .modifyFlatAlways(source, 0.15f * multiplier, getName());
```

倍率为 1 时，危险度增加 15 个百分点，流通性修饰降低 5 个百分点，舰队质量增加 0.15。日志里的 `getAccessibilityMod().computeEffective(0f)` 读取流通性修饰，最终流通性还合并市场的其他条件。

舰队生成从来源市场取得质量时会读取质量动态项，它影响后续生成条件。已经生成的舰队仍持有原来的装配与质量结果，所以当前观察先确认市场动态值，实际生成沿 [C11-S1] 的来源市场处理。

### 用参数统一调整倍率

`addCondition(id, param)` 在应用前将参数交给 `setParam(Object)`。本例接受正数 `Float`，将它解释为三项效果的统一倍率，局部处理如下：

```java
if (param instanceof Float && ((Float) param) > 0f) {
    multiplier = (Float) param;
} else {
    multiplier = 1f;
}
```

传入 `1f` 使用基准值，`2f` 使三项效果翻倍，其他输入采用默认 1。固定条件可以使用无参数添加，保留插件默认状态；需要参数时，调用方与插件约定类型，日志记录实际倍率。

### 撤销同一实例的来源

移除或重算前，`unapply(...)` 按同一 token 解除三项修饰：

```java
String source = getModId();
market.getHazard().unmodifyFlat(source);
market.getAccessibilityMod().unmodifyFlat(source);
market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
        .unmodifyFlat(source);
```

这份来源在各属性中对应同一条件。重新应用替换它的当前值，移除本实例也只清理自己的来源，其他实例仍可参与市场计算。

## 比较抑制与两种重算

条件可以保留在市场中而暂时停止生效。`suppressCondition(specId)` 将规格 ID 加入抑制集合，随后调用 `reapplyConditions()` 撤销并跳过这些实例；解除抑制后再重算，才能恢复效果。以下局部片段分别记录标记后、重算后与恢复后：

```java
market.suppressCondition(GearInWarfrontCondition.CONDITION_ID);
float before = market.getHazard().getModifiedValue();
market.reapplyConditions();
float suppressed = market.getHazard().getModifiedValue();
market.unsuppressCondition(GearInWarfrontCondition.CONDITION_ID);
market.reapplyConditions();
float restored = market.getHazard().getModifiedValue();
```

**抑制先改变标记，效果在重算时改变。** 所以 `before` 仍可以保留应用值，`suppressed` 才反映撤销。抑制按规格 ID 处理同类实例，`getConditions()` 仍包含这些对象。

### 勘测状态怎样参与重算

条件的 `isSurveyed()` 保存实例勘测状态，`requiresSurveying()` 来自可选的生成规格；市场的 `getSurveyLevel()` 表示整个市场的勘测等级。本例没有 `condition_gen_data.csv` 记录，默认不要求勘测，创建后的实例仍拥有自己的勘测状态。

观察器手动执行 `setSurveyed(false)`，随后全量重算，再单独重算本次 token。**全量重算检查勘测与抑制，单实例重算只检查抑制。** 全量入口先撤销每份条件，再应用已勘测且未抑制者；单实例入口撤销指定对象，再按抑制标记决定应用。因此本例手动设为未勘测后，前者移除效果，后者重新应用效果。

::: warning 单实例重算会略过勘测状态
`reapplyCondition(token)` 可以让未勘测实例重新提供效果。需要按市场勘测状态统一应用时，使用 `reapplyConditions()`；本节两种调用用于比较这项差异。
:::

### 移除本次条件

比较结束后，观察器把勘测状态改回 `true`，用 `removeSpecificCondition(token)` 删除本次实例，并恢复原抑制状态。删除会调用它的 `unapply()`，三项修饰随来源解除。

`removeCondition(specId)` 则移除同一规格的所有实例。当前实验只处理自己添加的一份，所以保存返回的 token；其他功能添加了同类型条件时，仍能保留它们。

## 保存倍率与计时

Warfront 除了当前修饰，还保存 `multiplier` 与 `elapsed`。市场推进时调用 `advance(amount)`，本例直接累计传入的秒数，三项效果仍由 `apply(...)` 写入。

`BaseMarketConditionPlugin.isTransient()` 默认返回 `true`，保存时只保留条件，读档后重新创建插件，字段回到初始值。本例要让倍率与计时延续，因此返回 `false`：

```java
@Override
public boolean isTransient() {
    return false;
}
```

**插件保存方式取决于需要延续的实例状态。** 固定效果可以按规格重建，倍率已经设为 2 时，读档恢复成 1 就改变了效果。也可以把必要状态放到市场 Memory，再在重建时读取；需要按生涯日累计的插件可参考 `BaseMarketConditionPlugin2`，它保存插件并提供 `daysActive`。

本节观察器在同一轮移除条件，日志验证的是应用和恢复。长期保留条件的功能还要在保存读档后比较倍率与计时，才能确认状态实际延续。

## 读取规格与显示条件

`MarketConditionSpecAPI` 位于 `com.fs.starfarer.api.characters` 包，从设置取得注册结果。局部读取如下：

```java
MarketConditionSpecAPI spec = Global.getSettings()
        .getMarketConditionSpec(GearInWarfrontCondition.CONDITION_ID);
LOG.info(String.format("id=%s name=%s script=%s icon=%s order=%.0f",
        spec.getId(), spec.getName(), spec.getScriptClass(),
        spec.getIcon(), spec.getOrder()));
```

`getId()`、`getName()`、`getScriptClass()`、`getIcon()` 与 `getOrder()` 对应身份、插件和显示，`getDesc()` 取得原描述，`getTags()` 与 `hasTag()` 读取分类。`isPlanetary()`、`isDecivRemove()` 读取两项布尔设置，`getGenSpec()` 返回可选生成规格；当前条件由脚本添加，未配置自动生成。

界面沿用基类的 `getName()`、`getIconName()`、`hasCustomTooltip()` 与 `createTooltip()`，通过 `getTokenReplacements()` 替换描述中的市场字段。`isTooltipExpandable()` 与 `getTooltipWidth()` 控制提示展开和宽度，`runWhilePaused()` 决定暂停推进，当前采用默认的 `false`。市场数值仍从 `MarketAPI` 读取，规格只提供定义。

### 原版条件速查表

| 条件              | 插件                        | 主要效果                 | 本节关系          |
| ----------------- | --------------------------- | ------------------------ | ----------------- |
| `ore_sparse`      | `ResourceDepositsCondition` | 资源供给                 | 原版 procgen 条件 |
| `hot`             | `BaseHazardCondition`       | 危险度                   | 基类修饰范式      |
| `solar_array`     | `SolarArray`                | 抑制环境条件             | suppression 范式  |
| `GearIn_warfront` | `GearInWarfrontCondition`   | 危险度、流通性、舰队质量 | 参数与 token 示例 |

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

Warfront 用规格 ID 创建，用实例 token 写入和解除效果。倍率控制三项修饰，抑制与勘测决定重算时是否生效，删除本次实例后按原抑制状态恢复市场。

全量与单实例重算各有条件，插件是否保存又决定字段能否延续。明确当前实例、应用入口与状态寿命，市场条件才会在重算和读档后继续保持预期行为。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketConditionAPI`：市场条件实例和 token；
- `com.fs.starfarer.api.campaign.econ.MarketConditionPlugin`：条件插件生命周期；
- `com.fs.starfarer.api.characters.MarketConditionSpecAPI`：CSV 静态规格；
- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`：条件基类默认行为；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：条件添加、重算、抑制和移除入口。
