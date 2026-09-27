# 制作角色技能

本节把同一套技能资源拆成三条可观察的角色技能：舰长只改变被驾驶舰船，舰队指挥官改变所属舰队，管理员改变自己治理的殖民地市场。读者会从注册行走到效果脚本，再用技能树、舰队界面、整备界面和殖民地界面确认每个对象收到的 modifier。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

- 解释 `skill_data.csv`、`.skill` 和 Java 效果类如何按同一个 ID 组合；
- 根据 `SkillEffectType` 选择单舰、舰队或治理市场的效果接口；
- 用多个 effect group 表达普通等级和精英等级，并用稳定 ID 管理 `apply()` 与 `unapply()`；
- 实现 `DescriptionSkillEffect` 和 `FleetTotalSource`，让技能面板与舰队汇总显示实际效果；
- 从技能树、状态面板和游戏日志定位 ID、作用域或 modifier 清理错误。

## 前置知识

- [C1-S1] 的 MOD ID、包名和资源路径约定；
- [C6-S1] 的 Java 构建、脚本类加载和数据资源注册；
- [C8-S4] 的 `MutableStat` modifier ID、`modify` 方法和 `unmodify` 方法；
- [C10-S3] 的 `FleetMemberAPI` 与舰船 stats；
- [C13-S1] 的 `PersonAPI`、角色槽位、技能等级和 `refreshCharacterStatsEffects()`；
- 能够在生涯中打开技能树、舰队信息、整备界面和殖民地管理界面。

## 让三个文件组成一个技能

技能不是单个 Java 类。`skill_data.csv` 提供技能树需要的名称、排序、层级、角色资格和图标；同 ID 的 `.skill` 文件声明能力树、作用域、等级组和每个效果的 `type`；脚本类实现这些效果要求的接口。加载器把三处 ID 对齐后才得到一个可学习的 `SkillSpecAPI`。任意一处写成另一个 ID，数据就无法汇合，技能树也没有可以展示的完整定义。

### 技能资源速查表

| 文件 | 负责的状态 | 本节关键字段 |
|---|---|---|
| `skill_data.csv` | 技能树注册和角色资格 | `id`、`name`、`order`、`tier`、`combat officer`、`admiral`、`admin`、`icon` |
| `<id>.skill` | 能力树分组和效果作用域 | `id`、`governingAptitude`、`scope`、`elite`、`effectGroups` |
| Java 效果类 | 运行时数值和描述 | `apply()`、`unapply()`、`getEffectDescription()`、接口对应的方法 |

三个 ID 在本节保持逐字一致：`GearIn_captain_mobility`、`GearIn_fleet_coordination` 和 `GearIn_colony_governance`。CSV 的 `combat officer`、`admiral` 和 `admin` 字段分别把技能放入舰长、舰队指挥官和管理员可以学习的角色范围；`.skill` 的 `scope` 再决定效果说明应该显示为单舰、舰队还是治理市场。

### 让注册失败留下可定位信号

加载器先读取 CSV 中的注册行，再读取 `data/characters/skills/` 下的 `.skill` 文件。`.skill` 的 `id` 如果没有对应 CSV 行，加载器会跳过它；CSV 有行而 `.skill` 的 ID 拼错时，技能树仍然缺少完整定义。两种情况都表现为技能不出现，因此排查顺序应该是先比较三个文件的 ID，再看日志中的跳过信息，最后检查脚本类的完整包名。

下面的资源片段展示舰长技能如何把描述类和数值类放入同一等级组。`DESCRIPTION` 只负责技能面板文字，`SHIP` 才会收到 `MutableShipStatsAPI`；两者共享 `requiredSkillLevel`，所以技能升到一级时一起出现。

```json
{
    "id":"GearIn_captain_mobility",
    "governingAptitude":"combat",
    "elite":true,
    "scope":"PILOTED_SHIP",
    "effectGroups":[
        {
            "name":"Level 1",
            "requiredSkillLevel":1,
            "effectBasedOnLevel":false,
            "effects":[
                {"type":"DESCRIPTION", "script":"data.scripts.gearin.campaign.GearInCaptainMobilityDescription"},
                {"type":"SHIP", "script":"data.scripts.gearin.campaign.GearInCaptainMobilityEffect"}
            ]
        },
        {
            "name":"Elite",
            "requiredSkillLevel":2,
            "effectBasedOnLevel":false,
            "effects":[
                {"type":"SHIP", "script":"data.scripts.gearin.campaign.GearInCaptainMobilityEliteEffect"}
            ]
        }
    ]
}
```

`elite:true` 使第二组成为精英等级组，`requiredSkillLevel:2` 决定它只有在技能达到二级时才应用。`effectBasedOnLevel` 只决定技能面板是否追加 "每级" 说明文字；无论取值如何，脚本都会收到当前等级，本例把固定数值写入 modifier。设为 `true` 时，才需要让 `level` 参与计算，保持面板文字与实际增量一致。

## 用 SkillEffectType 选作用对象

`SkillEffectType` 是数据文件中的作用对象选择器。它决定引擎把哪个 API 传给脚本，Java 类实现的接口必须与这个选择相匹配。`scope` 影响技能说明中的范围文字，`type` 才决定具体的应用入口。

### 作用域速查表

| `type` | 接口 | 引擎传入的对象 | 本节用途 |
|---|---|---|---|
| `SHIP` | `ShipSkillEffect` | 一艘舰船的 `MutableShipStatsAPI` | 舰长机动训练 |
| `ALL_SHIPS_IN_FLEET` | `ShipSkillEffect` | 舰队中的每艘非战机舰船 | 需要全舰队逐船应用时使用 |
| `SHIP_FIGHTERS` | `ShipSkillEffect` | 某艘母舰起飞的战机 | 需要绑定母舰时使用 |
| `ALL_FIGHTERS_IN_FLEET` | `ShipSkillEffect` | 舰队中的全部战机 | 需要绑定舰队时使用 |
| `CHARACTER_STATS` | `CharacterStatsSkillEffect` | 角色的 `MutableCharacterStatsAPI` | 修改命令点、经验或动态 stats |
| `FLEET` | `FleetStatsSkillEffect` | 角色所属舰队的 `MutableFleetStatsAPI` | 舰队协同指挥 |
| `GOVERNED_OUTPOST` | `MarketSkillEffect` | 角色治理的 `MarketAPI` | 殖民地治理 |
| `ALL_OUTPOSTS` | `MarketSkillEffect` | 玩家全部治理市场 | 需要全市场效果时使用 |
| `DESCRIPTION` | `DescriptionSkillEffect` | 技能面板文字 | 描述和高亮，不写数值 |

所有数值接口都继承 `LevelBasedEffect`，因此需要返回效果说明、每级说明和 `ScopeDescription`。`CharacterStatsSkillEffect` 适合角色本身的状态，`FleetStatsSkillEffect` 适合舰队移动和补给状态；两者都由角色 stats 刷新时重新应用。`MarketSkillEffect` 的市场参数只代表当前技能的治理市场，脚本直接使用这个参数即可保持作用范围。

## 让 apply 和 unapply 成对工作

每次 modifier 都需要一个稳定的 `id`。引擎可能因为换舰、换角色、重新载入存档或刷新技能而再次调用 `apply()`；同一 ID 会覆盖同一来源的旧值，`unapply()` 再用这个 ID 移除它。下面的局部片段展示单舰效果的最小结构：数值集中在 `apply()`，清理由同一字段和同一 ID 完成。

```java
public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
    stats.getMaxSpeed().modifyPercent(id, 8f);
    stats.getAcceleration().modifyPercent(id, 12f);
}

public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
    stats.getMaxSpeed().unmodify(id);
    stats.getAcceleration().unmodify(id);
}
```

这里的 `id` 来自引擎，而不是技能作者自行拼接的临时字符串。把 `apply()` 写成 `modifyPercent("captain", ...)`，再在 `unapply()` 使用另一个字符串，会留下旧 modifier；玩家会看到技能已经降级，舰船速度仍然没有恢复。

指挥官效果接收 `MutableFleetStatsAPI`，同样按字段逐项清理。`getFleetwideMaxBurnMod()` 是加法型 `StatBonus`，所以使用 `modifyFlat`；`getFuelUseHyperMult()` 是乘法型统计项，`0.9f` 表示保留原消耗的百分之九十。两种 modifier 混在一个脚本里时，必须保留各自对应的解除方法。

```java
public void apply(MutableFleetStatsAPI stats, String id, float level) {
    stats.getFleetwideMaxBurnMod().modifyFlat(id, 1f);
    stats.getFuelUseHyperMult().modifyMult(id, 0.9f);
}

public void unapply(MutableFleetStatsAPI stats, String id) {
    stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
    stats.getFuelUseHyperMult().unmodify(id);
}
```

管理员效果的对象是 `MarketAPI`。稳定度是平面增量，收入倍率是乘法统计项的百分比修改；两项都用同一个效果 ID，市场切换或管理员替换时才会一起移除。`GOVERNED_OUTPOST` 让引擎只把当前管理员负责的市场传入脚本。

```java
public void apply(MarketAPI market, String id, float level) {
    market.getStability().modifyFlat(id, 1f, "Colony governance");
    market.getIncomeMult().modifyPercent(id, 10f, "Colony governance");
}

public void unapply(MarketAPI market, String id) {
    market.getStability().unmodifyFlat(id);
    market.getIncomeMult().unmodify(id);
}
```

## 让面板文字和数值保持同一来源

`DescriptionSkillEffect` 不参与 stats 计算，它只返回技能面板需要的文本、高亮字符串和高亮颜色。描述类应当写清作用对象和数值结果，让读者可以把技能树上的一句话对应到舰船、舰队或市场中的实际变化。描述类与数值类分开后，修改 UI 文本不会意外改变 modifier。

```java
public class GearInFleetCoordinationDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "A commander coordinates movement across the entire fleet.";
    }

    public String[] getHighlights() {
        return new String[] {"entire fleet"};
    }

    public Color[] getHighlightColors() {
        return new Color[] {Misc.getHighlightColor()};
    }
}
```

`getHighlights()` 与 `getHighlightColors()` 必须按相同顺序返回对应数组。描述里写了两个高亮词，就返回两个颜色；数组长度不一致时，技能文本可能显示但高亮渲染会出现缺失。

## 在整备界面提供舰队汇总

实现 `FleetTotalSource` 的效果类可以返回一个 `FleetTotalItem`。这个对象只描述舰队汇总行，不会替代 `apply()` 的实际 modifier。指挥官技能把汇总行写成 "+1 burn, -10% fuel"，读者在整备界面看到它时，仍然可以回到舰队状态查看具体数值。

```java
public FleetTotalItem getFleetTotalItem() {
    FleetTotalItem item = new FleetTotalItem();
    item.label = "Fleet coordination";
    item.value = "+1 burn, -10% fuel";
    item.sortOrder = 50f;
    return item;
}
```

舰队汇总行适合表达整支舰队的统一收益。单舰速度和市场稳定度没有共同的舰队总量，因此舰长和管理员效果不实现 `FleetTotalSource`，它们分别在整备舰船 stats 和市场界面观察。

## 角色属性、战机和全舰队效果的边界

`CHARACTER_STATS` 直接接收 `MutableCharacterStatsAPI`，可以写入命令点或动态 stats；`SHIP_FIGHTERS` 与 `ALL_FIGHTERS_IN_FLEET` 仍然使用 `ShipSkillEffect`，区别在于引擎选择一艘母舰的战机，还是舰队中的全部战机。`ALL_SHIPS_IN_FLEET` 同样使用 `ShipSkillEffect`，但会为每艘舰船分别调用 `apply()`。这些类型的关键判断是作用对象集合，而不是脚本类名。

技能等级刷新时，角色 stats 会按当前等级重新计算各个 effect group。脚本不能假设 `apply()` 只调用一次，也不能把上次调用的对象缓存在静态字段中。需要根据等级变化计算数值时，使用 `level`；固定等级效果则将 `effectBasedOnLevel` 设为 `false`，并在描述中明确实际增量。

## 三条技能如何连接到角色

舰长技能的 `SHIP` 效果只影响拥有该技能的角色所驾驶的舰船；同一角色被绑定到舰队指挥官槽位时，技能仍然按照 `type` 的单舰规则工作。指挥官技能的 `FLEET` 效果作用于该角色指挥的舰队，舰船的单独 stats 会在舰队状态刷新后反映新的移动值。管理员技能的 `GOVERNED_OUTPOST` 效果只作用于该角色管理的市场，换管理员后旧市场会执行 `unapply()`，新市场再执行 `apply()`。

把三条技能分别分配给不同角色，可以清楚观察对象归属：舰长换船时只有单舰数值变化，指挥官改变后整支舰队的移动数值变化，管理员替换后只有其治理市场的稳定度和收入变化。这个分配也让同一个 `PersonAPI` 不会同时承担三个不同槽位的验证结果。

## 完整最小例

下面的资源和 Java 类组成三条可加载的技能。代码块与项目中的完整源文件保持逐字一致。

::: details 完整成品：GearInCaptainMobilityEffect

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class GearInCaptainMobilityEffect implements ShipSkillEffect {
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        stats.getMaxSpeed().modifyPercent(id, 8f);
        stats.getAcceleration().modifyPercent(id, 12f);
    }

    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+8% top speed and +12% acceleration for the piloted ship";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.PILOTED_SHIP;
    }
}
```

:::

::: details 完整成品：GearInCaptainMobilityEliteEffect

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class GearInCaptainMobilityEliteEffect implements ShipSkillEffect {
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        stats.getTurnAcceleration().modifyPercent(id, 15f);
    }

    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getTurnAcceleration().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+15% turn acceleration for the piloted ship";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.PILOTED_SHIP;
    }
}
```

:::

::: details 完整成品：GearInCaptainMobilityDescription

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInCaptainMobilityDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "A captain keeps one ship responsive under fire.";
    }

    public String[] getHighlights() {
        return new String[] {"one ship", "responsive"};
    }

    public Color[] getHighlightColors() {
        Color color = Misc.getHighlightColor();
        return new Color[] {color, color};
    }
}
```

:::

::: details 完整成品：GearInFleetCoordinationEffect

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.FleetStatsSkillEffect;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;

public class GearInFleetCoordinationEffect implements FleetStatsSkillEffect, FleetTotalSource {
    public void apply(MutableFleetStatsAPI stats, String id, float level) {
        stats.getFleetwideMaxBurnMod().modifyFlat(id, 1f);
        stats.getFuelUseHyperMult().modifyMult(id, 0.9f);
    }

    public void unapply(MutableFleetStatsAPI stats, String id) {
        stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
        stats.getFuelUseHyperMult().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+1 maximum burn level and -10% hyperspace fuel use for the fleet";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.FLEET;
    }

    public FleetTotalItem getFleetTotalItem() {
        FleetTotalItem item = new FleetTotalItem();
        item.label = "Fleet coordination";
        item.value = "+1 burn, -10% fuel";
        item.sortOrder = 50f;
        return item;
    }
}
```

:::

::: details 完整成品：GearInFleetCoordinationDescription

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInFleetCoordinationDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "A commander coordinates movement across the entire fleet.";
    }

    public String[] getHighlights() {
        return new String[] {"entire fleet"};
    }

    public Color[] getHighlightColors() {
        return new Color[] {Misc.getHighlightColor()};
    }
}
```

:::

::: details 完整成品：GearInColonyGovernanceEffect

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.MarketSkillEffect;

public class GearInColonyGovernanceEffect implements MarketSkillEffect {
    public void apply(MarketAPI market, String id, float level) {
        market.getStability().modifyFlat(id, 1f, "Colony governance");
        market.getIncomeMult().modifyPercent(id, 10f, "Colony governance");
    }

    public void unapply(MarketAPI market, String id) {
        market.getStability().unmodifyFlat(id);
        market.getIncomeMult().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+1 stability and +10% income for the governed market";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.GOVERNED_OUTPOST;
    }
}
```

:::

::: details 完整成品：GearInColonyGovernanceDescription

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInColonyGovernanceDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "An administrator turns one governed market into a steadier source of income.";
    }

    public String[] getHighlights() {
        return new String[] {"one governed market", "income"};
    }

    public Color[] getHighlightColors() {
        Color color = Misc.getHighlightColor();
        return new Color[] {color, color};
    }
}
```

:::

## 验证方案

前置数据：已安装包含三条技能资源的 MOD，并拥有可绑定舰长、舰队指挥官和管理员的生涯存档。

1. 构建并安装包含三条技能资源的 MOD，进入技能树，确认三个名称、图标和角色资格分别对应舰长、舰队指挥官和管理员。
2. 将舰长技能升到一级，把角色绑定到一艘舰船，打开整备界面，记录速度和加速度；升到精英等级后重新打开舰船 stats，确认转向加速度增加。
3. 将舰队指挥官技能升到一级，绑定到当前舰队指挥官，观察舰队最高燃烧等级、超空间燃料消耗和整备界面的舰队汇总行。
4. 将管理员技能升到一级并任命到一个市场，记录市场稳定度和收入倍率，再查看技能面板中的描述和高亮。
5. 替换舰长、舰队指挥官或管理员后重新进入对应界面，确认旧对象的 modifier 被清除，新对象获得自己的技能效果。
6. 保存并重新载入存档，重复查看三类状态，确认技能等级、角色槽位和市场治理关系仍然对应原对象。
7. 查看 `starsector.log`，将技能树缺失、脚本类名错误或效果未生效的现象与资源 ID、完整包名和作用域逐项对照。

预期结果：

- 技能树显示三条技能及正确角色资格；
- 舰长技能改变被驾驶舰船的速度、加速度和精英转向加速度；
- 指挥官技能改变舰队移动、燃料消耗并显示汇总行；
- 管理员技能改变治理市场的稳定度和收入；
- 重复刷新与存档载入保留正确的 modifier 状态。

## 故障分析

- **技能树中没有新技能**：
  - **解决方案**：比较 CSV 行、`.skill` 文件名和 `.skill` 内的 `id`，确认三个字符串逐字一致，再检查日志中的跳过信息。

- **技能面板出现，数值效果没有出现**：
  - **解决方案**：检查 `type` 与接口是否匹配，并确认脚本类的完整包名、公共无参构造和对应的 `apply()` 签名正确。

- **舰长技能降级后舰船仍然加速**：
  - **解决方案**：在 `unapply()` 中使用引擎传入的同一个 `id`，并分别调用 `unmodify()` 清理速度和加速度。

- **舰队汇总行出现，舰队状态没有改变**：
  - **解决方案**：检查 `FleetTotalSource` 是否只负责显示，确认真正的燃烧等级和燃料 modifier 写在 `FleetStatsSkillEffect.apply()` 中。

- **管理员技能改变了错误的市场**：
  - **解决方案**：确认 `.skill` 使用 `GOVERNED_OUTPOST`，并在 `MarketSkillEffect.apply()` 中只使用引擎传入的 `MarketAPI`。

- **重复进入界面后收入倍率不断叠加**：
  - **解决方案**：保持 modifier ID 稳定，使用 `modifyPercent` 覆盖同一来源，并在 `unapply()` 中调用 `unmodify()`。

## 总结

三条技能共享同一套注册结构，却把数值写入三个不同对象：`ShipSkillEffect` 处理舰船，`FleetStatsSkillEffect` 处理舰队，`MarketSkillEffect` 处理治理市场。`.skill` 的 `type` 决定对象选择，Java 接口决定可调用的 stats API，稳定 modifier ID 负责刷新和移除。描述类与汇总对象把运行时结果翻译成技能面板和整备界面中的可读信息。

## 建议参考项

- `C13-S1`：人物创建、角色槽位和技能等级刷新；
- `C13-S2`：舰长、舰队指挥官和管理员对象归属；
- `技能效果接口`：`SkillEffectType`、`ShipSkillEffect`、`FleetStatsSkillEffect`、`MarketSkillEffect`、`DescriptionSkillEffect` 和 `FleetTotalSource`；
- `运行时 stats`：`MutableShipStatsAPI`、`MutableFleetStatsAPI` 和 `MarketAPI` 中与本节效果对应的访问器；
- `原版技能资源`：`Helmsmanship`、`Navigation` 和 `IndustrialPlanning`。
