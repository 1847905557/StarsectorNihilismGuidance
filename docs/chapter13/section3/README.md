# 制作角色技能

角色已经绑定，接下来给 Mira、Rowan 和 Selene 各加一条技能。舰长获得机动收益，指挥官提高舰队燃耗并减少超空间燃料消耗，管理员提高治理市场的稳定度与收入。先注册三份资源，再按作用对象实现效果和描述，技能等级与实际加成沿上一节的角色入口观察。

*本节预期阅读时间：\~30 分钟。*

*本节预期操作时间：\~50 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `skill_data.csv`、`.skill` 和 Java 效果类如何按同一个 ID 组合；
- 根据 `SkillEffectType` 选择单舰、舰队或治理市场的效果接口；
- 用多个 effect group 表达普通等级和精英等级，并用稳定 ID 管理 `apply()` 与 `unapply()`；
- 实现 `DescriptionSkillEffect` 和 `FleetTotalSource`，让技能面板与舰队汇总显示实际效果；
- 从技能树、状态面板和游戏日志定位 ID、作用域或 modifier 清理错误。

## 前置知识

- [C1-S7] 的 CSV 列与转义；
- [C6-S4] 的构建、JAR 与加载；
- [C8-S4] 的 `MutableStat` modifier ID、`modify` 方法和 `unmodify` 方法；
- [C11-S3] 的成员与舰队数值；
- [C13-S2] 的角色槽位、技能等级与刷新；
- 能够在生涯中打开技能树、舰队信息、整备界面和殖民地管理界面。

## 让三个文件组成一个技能

技能由注册行、定义文件和效果类共同接入。`skill_data.csv` 提供技能树需要的名称、排序、层级、角色资格和图标；同 ID 的 `.skill` 文件声明能力树、作用域、等级组和每个效果的 `type`；脚本类实现这些效果要求的接口。加载器把三处 ID 对齐后才得到一个可学习的 `SkillSpecAPI`。任意一处写成另一个 ID，数据就无法汇合，技能树也没有可以展示的完整定义。

### 技能资源速查表

| 文件 | 负责的状态 | 本节关键字段 |
|---|---|---|
| `skill_data.csv` | 技能树注册和角色资格 | `id`、`name`、`order`、`tier`、`combat officer`、`admiral`、`admin`、`icon` |
| `<id>.skill` | 能力树分组和效果作用域 | `id`、`governingAptitude`、`scope`、`elite`、`effectGroups` |
| Java 效果类 | 运行时数值和描述 | `apply()`、`unapply()`、`getEffectDescription()`、接口对应的方法 |

本节分别定义三个 ID：`GearIn_captain_mobility`、`GearIn_fleet_coordination` 和 `GearIn_colony_governance`。CSV 的三个角色资格字段分别用于军官、舰队指挥官与管理员的选择范围，`.skill` 的 `scope` 提供说明范围。当前给专用人物直接设置技能等级，资格与实际 `type` 都在定义中核对。

### 先保存注册与三份定义

先保存 `data/characters/skills/skill_data.csv` 的三条记录，已有表头时只追加数据行。再在同目录保存三个同名 `.skill` 文件。加载器先读取 CSV 的身份与角色字段，再加载效果定义。`.skill` 的 `id` 如果没有对应 CSV 行，加载器会跳过它；CSV 有行而 `.skill` 的 ID 拼错时，技能树仍然缺少完整定义。两种情况都表现为技能不出现，因此排查顺序应该是先比较三个文件的 ID，再看日志中的跳过信息，最后检查脚本类的完整包名。

注册行如下：

```csv
id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon,
GearIn_captain_mobility,Captain Mobility,1025,2,1,0,"Improve the speed and handling of the piloted ship.","GearIndustry",TRUE,,,,graphics/icons/skills/helmsmanship.png,
GearIn_fleet_coordination,Fleet Coordination,2025,2,1,0,"Improve fleet movement and hyperspace fuel economy.","GearIndustry",,TRUE,,,graphics/icons/skills/navigation.png,
GearIn_colony_governance,Colony Governance,3025,2,1,0,"Improve stability and income at the governed market.","GearIndustry",,,TRUE,,graphics/icons/skills/industrial_planning.png,
```

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

`elite:true` 声明精英技能，第二组的 `requiredSkillLevel:2` 指定实际阈值。`effectBasedOnLevel` 只决定技能面板是否追加 "每级" 说明文字；无论取值如何，脚本都会收到当前等级，本例把固定数值写入 modifier。选择每级说明时，让返回的每级文本与代码实际计算方式对应。本例三种技能采用固定增量，当前等级用于决定哪些组已达到阈值。

其余两份定义保存为 `GearIn_fleet_coordination.skill` 和 `GearIn_colony_governance.skill`，分别连接舰队与市场作用域：

```json
{
    "id":"GearIn_fleet_coordination",
    "governingAptitude":"leadership",
    "scope":"FLEET",
    "effectGroups":[
        {
            "name":"Level 1",
            "requiredSkillLevel":1,
            "effectBasedOnLevel":false,
            "effects":[
                {"type":"DESCRIPTION", "script":"data.scripts.gearin.campaign.GearInFleetCoordinationDescription"},
                {"type":"FLEET", "script":"data.scripts.gearin.campaign.GearInFleetCoordinationEffect"}
            ]
        }
    ]
}
```

```json
{
    "id":"GearIn_colony_governance",
    "governingAptitude":"industry",
    "scope":"GOVERNED_OUTPOST",
    "effectGroups":[
        {
            "name":"Level 1",
            "requiredSkillLevel":1,
            "effectBasedOnLevel":false,
            "effects":[
                {"type":"DESCRIPTION", "script":"data.scripts.gearin.campaign.GearInColonyGovernanceDescription"},
                {"type":"GOVERNED_OUTPOST", "script":"data.scripts.gearin.campaign.GearInColonyGovernanceEffect"}
            ]
        }
    ]
}
```

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
| `HULLMOD_UNLOCK` | 定义中的船插解锁列表 | 船插 ID | 提供船插解锁 |
| `ABILITY_UNLOCK` | 定义中的能力解锁列表 | 能力 ID | 提供能力解锁 |

所有数值接口都继承 `LevelBasedEffect`，因此需要返回效果说明、每级说明和 `ScopeDescription`。`CharacterStatsSkillEffect` 适合角色本身的状态，`FleetStatsSkillEffect` 适合舰队移动和补给状态；两者都由角色 stats 刷新时重新应用。`MarketSkillEffect` 的市场参数只代表当前技能的治理市场，脚本直接使用这个参数即可保持作用范围。

## 让 apply 和 unapply 成对工作

**引擎提供效果来源 ID，写入与解除复用它。**引擎可能因为换舰、换角色、重新载入存档或刷新技能而再次调用 `apply()`；同一 ID 会覆盖同一来源的旧值，`unapply()` 再用这个 ID 移除它。下面的局部片段展示单舰效果的最小结构：数值集中在 `apply()`，清理由同一字段和同一 ID 完成。

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

这里的 `id` 来自引擎，而不是技能作者自行拼接的临时字符串。数值变化随同来源更新，撤销从同字段解除，反复刷新仍能回到其他来源共同决定的基线。

指挥官效果接收 `MutableFleetStatsAPI`，同样按字段逐项清理。`getFleetwideMaxBurnMod()` 是加法型 `StatBonus`，所以使用 `modifyFlat`；`getFuelUseHyperMult()` 是乘法型统计项，`0.9f` 表示保留原消耗的百分之九十。两个字段在解除时各自采用对应方法。

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

`DescriptionSkillEffect` 不参与 stats 计算，它只返回技能面板需要的文本、高亮字符串和高亮颜色。描述类应当写清作用对象和数值结果，让您可以把技能树上的一句话对应到舰船、舰队或市场中的实际变化。描述类与数值类分开后，修改 UI 文本不会意外改变 modifier。

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

高亮文字与颜色按相同顺序一一对应。描述里写了两个高亮词，就返回两个颜色；数组长度不一致时，技能文本可能显示但高亮渲染会出现缺失。

## 在整备界面提供舰队汇总

实现 `FleetTotalSource` 的效果类可以返回一个 `FleetTotalItem`。这个对象只描述舰队汇总行，不会替代 `apply()` 的实际 modifier。指挥官技能把汇总行写成 "+1 burn, -10% fuel"，您在整备界面看到它时，仍然可以回到舰队状态查看具体数值。

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

舰长技能的 `SHIP` 效果只影响拥有该技能的角色所驾驶的舰船；只作为舰队指挥官持有该技能时，`SHIP` 也不会变为逐舰舰队效果；要影响每艘成员，需要对应的 `ALL_SHIPS_IN_FLEET` 类型。指挥官技能的 `FLEET` 效果作用于该角色指挥的舰队，舰船的单独 stats 会在舰队状态刷新后反映新的移动值。管理员技能的 `GOVERNED_OUTPOST` 效果只作用于该角色管理的市场，换管理员后旧市场会执行 `unapply()`，新市场再执行 `apply()`。

本例分别给三个人物赋值，日志可以直接对应作用对象。舰长换船后重新建立成员 stats，指挥官变化后刷新舰队，管理员变化后从治理市场刷新；角色与实际数值分别确认。

## 给已有角色设置技能并刷新

在 [C13-S2] 的 `GearInCaptainRole.run(member)` 中，已经取得或创建人物后、`member.setCaptain(person)` 之前加入局部片段：

```java
person.getStats().setSkillLevel("GearIn_captain_mobility", 2f);
person.getStats().refreshCharacterStatsEffects();
```

2 让普通与精英组同时生效，1 只启用普通组。之后沿原方法绑定舰长并更新成员，读取当前舰船 stats。

在 `GearInFleetCommanderRole.run(fleet)` 取得人物后、`fleet.setCommander(person)` 之前加入：

```java
person.getStats().setSkillLevel("GearIn_fleet_coordination", 1f);
```

指挥官写入时将人物 stats 关联到当前舰队并刷新，原有 `forceSync()` 再更新成员与汇总。

在 `GearInAdministratorRole.run()` 已执行 `market.setAdmin(person)` 之后加入局部片段：

```java
person.getStats().setSkillLevel("GearIn_colony_governance", 1f);
person.getStats().refreshGovernedOutpostEffects(market);
market.reapplyIndustries();
```

**人物技能、角色绑定与下游更新各自完成。** 管理员刷新明确传入 Foundry，再让产业按当前市场值重算。需要比较清理时，保存原技能等级，改为 0 并按同一入口刷新，再恢复原等级；一次观察就能对应写入、解除和恢复。

`Global.getSettings().getSkillSpec(id)` 还可以读取名称、图标、角色资格、作用域与效果列表。规格可加载、人物已取得等级、对象收到修饰分别从各自位置观察。

## 完整最小例

七个类保存到 `src/data/scripts/gearin/campaign/`，各自承担普通、精英、舰队、市场或描述职责。注册与赋值入口采用前述片段。

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

前置数据：[C13-S2] 的专用巡逻队、测试狼与 Foundry 管理员入口已经就位，三份技能定义、CSV 与七类保存完成。

1. 保存技能资源和角色赋值片段，构建重启，新建测试存档，让巡逻队与角色入口执行。
2. 通过 `getSkillSpec(id)` 或人物技能提示查看三个名称、图标、角色资格和效果说明，核对各自注册 ID。
3. 查看 Mira 的测试狼，普通等级读取最大速度与加速度；当前等级 2 还启用转向加速度 15% 加成。把等级改为 1 后重新构建新档比较，普通效果继续保留。
4. 在专用巡逻队读取有效燃耗与超空间燃料统计，核对 Rowan 的 +1 舰队范围最大燃耗和 0.9 燃料倍率；舰队汇总项同时提供说明。
5. 打开 Foundry，查看 Selene 的技能与市场稳定度、收入倍率，核对 +1 稳定度和 10% 收入修饰。
6. 在同一对象的局部等级实验中保存原等级，改为 0 并完成成员、舰队或治理市场刷新，观察来源解除；恢复原等级，再读取同一组数值。
7. 保存读档后比较角色 ID、等级与当前数值；日志发生加载错误时，逐项对照资源 ID、作用类型与完整类名。

预期结果：

- 三个技能规格分别取得 `GearIn_captain_mobility`、`GearIn_fleet_coordination` 与 `GearIn_colony_governance`。
- Mira 的舰船有 8% 最大速度、12% 加速度，等级 2 再有 15% 转向加速度；等级 1 保留普通组。
- Rowan 的关联舰队获得 +1 范围最大燃耗和 0.9 超空间燃料倍率，汇总行解释这一组收益。
- Selene 的治理市场写入 +1 稳定度和 10% 收入，其他来源继续参与合并。
- 降为 0 并刷新后解除对应来源，恢复等级后重新应用；读档保留角色与技能对应关系。

## 故障分析

- **新技能定义无法取得**：注册行与定义文件 ID 不一致。
    - **解决方案**：比较 CSV 行、`.skill` 文件名和 `.skill` 内的 `id`，确认三个字符串逐字一致，再检查日志中的跳过信息。

- **技能面板出现，数值效果未生效**：作用对象或接口与当前角色不匹配。
    - **解决方案**：检查 `type` 与接口是否匹配，并确认脚本类的完整包名、公共无参构造和对应的 `apply()` 签名正确。

- **舰长技能降级后仍有加成**：来源或字段清理遗漏。
    - **解决方案**：在 `unapply()` 中使用引擎传入的同一个 `id`，并分别调用 `unmodify()` 清理速度和加速度。

- **舰队汇总行出现而状态保持原值**：显示对象已接入，实际效果尚未写入。
    - **解决方案**：检查 `FleetTotalSource` 是否只负责显示，确认真正的燃烧等级和燃料 modifier 写在 `FleetStatsSkillEffect.apply()` 中。

- **管理员技能改变了其他市场**：实现重新查找了错误市场。
    - **解决方案**：确认 `.skill` 使用 `GOVERNED_OUTPOST`，并在 `MarketSkillEffect.apply()` 中只使用引擎传入的 `MarketAPI`。

- **反复刷新后收入倍率持续叠加**：每次使用新的来源 ID。
    - **解决方案**：保持 modifier ID 稳定，使用 `modifyPercent` 覆盖同一来源，并在 `unapply()` 中调用 `unmodify()`。

## 总结

三条技能分别写入单舰、舰队与治理市场，普通和精英组由等级阈值选择。CSV 给出身份与资格，定义文件连接作用对象与脚本，描述和汇总再把实际效果显示出来。

编者建议沿 "人物等级 → 角色持有者 → 当前数值" 核对技能。界面文字与加成都接好后，再改变等级或角色，稳定来源和成对清理才能让结果按设计恢复。

## 建议参考项

- `com.fs.starfarer.api.characters.SkillSpecAPI`：技能规格、资格、作用域和效果列表。
- `com.fs.starfarer.api.characters.SkillEffectType`：定义文件的作用对象选择。
- `com.fs.starfarer.api.characters.ShipSkillEffect`：单舰与战机数值。
- `com.fs.starfarer.api.characters.FleetStatsSkillEffect`：关联舰队数值。
- `com.fs.starfarer.api.characters.MarketSkillEffect`：治理市场的效果。
- `com.fs.starfarer.api.characters.DescriptionSkillEffect`：面板文本和高亮。
- `com.fs.starfarer.api.characters.FleetTotalSource`：提供舰队汇总显示项。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：等级设置与角色、治理市场刷新。
