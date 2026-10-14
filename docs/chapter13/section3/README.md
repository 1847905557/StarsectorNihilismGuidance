# 制作角色技能

本节给 Mira、Rowan 与 Selene 添加各自的技能，分别改变单舰机动、舰队航速与燃料消耗，以及治理市场的稳定度和收入。每条技能由注册数据、效果定义与脚本组成，再通过人物等级和角色绑定进入实际对象。普通与精英等级的选择、说明文字与效果撤销也在这个过程中处理。

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

## 注册三条角色技能

先为三条技能确定 ID：`GearIn_captain_mobility` 用于舰长，`GearIn_fleet_coordination` 用于指挥官，`GearIn_colony_governance` 用于管理员。注册行给出名称、图标与角色资格，定义文件选择作用对象和等级组，Java 类实现数值与描述。

### 技能资源速查表

| 文件 | 负责的状态 | 本节关键字段 |
|---|---|---|
| `skill_data.csv` | 技能树注册和角色资格 | `id`、`name`、`order`、`tier`、`combat officer`、`admiral`、`admin`、`icon` |
| `<id>.skill` | 能力树分组和效果作用域 | `id`、`governingAptitude`、`scope`、`elite`、`effectGroups` |
| Java 效果类 | 运行时数值和描述 | `apply()`、`unapply()`、`getEffectDescription()`、接口对应的方法 |

在 `data/characters/skills/skill_data.csv` 保存以下记录，已有表头时只追加数据行。随后在同目录保存三个对应的 `.skill` 文件：

```csv
id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon,
GearIn_captain_mobility,Captain Mobility,1025,2,1,0,"Improve the speed and handling of the piloted ship.","GearIndustry",TRUE,,,,graphics/icons/skills/helmsmanship.png,
GearIn_fleet_coordination,Fleet Coordination,2025,2,1,0,"Improve fleet movement and hyperspace fuel economy.","GearIndustry",,TRUE,,,graphics/icons/skills/navigation.png,
GearIn_colony_governance,Colony Governance,3025,2,1,0,"Improve stability and income at the governed market.","GearIndustry",,,TRUE,,graphics/icons/skills/industrial_planning.png,
```

`combat officer`、`admiral` 与 `admin` 分别给出角色选择资格。本节向专用人物直接赋值，仍让资格与效果对象保持一致。加载按 CSV 的 ID 与定义内部 ID 对应：未登记的定义会被跳过，已登记 ID 缺少对应定义时则可能使加载失败。

## 实现 Mira 的机动技能

保存 `GearIn_captain_mobility.skill`。普通组提供速度与加速度，精英组另提供转向加速度；描述类与数值类共用普通组阈值。定义如下：

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

`DESCRIPTION` 提供面板文字，`SHIP` 接收驾驶舰船的 `MutableShipStatsAPI`。普通组的 `requiredSkillLevel` 为 1，精英组为 2；`elite:true` 声明精英资格，实际启用仍由组阈值选择。等级 2 时两个组都应用，普通效果继续保留。

普通效果写入最大速度 +8% 与加速度 +12%，撤销时从同一字段解除同一来源。以下局部方法放在 `GearInCaptainMobilityEffect`：

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

精英类用同样接口给转向加速度增加 15%。两类各有来源 ID，等级降为 1 时，普通组仍适用，精英组解除。`ShipSkillEffect` 还通过 `LevelBasedEffect` 返回效果说明、每级说明与 `ScopeDescription`，完整类采用 `PILOTED_SHIP`。

::: warning 每级说明不会自动缩放效果
`effectBasedOnLevel` 只决定技能面板是否追加 "每级" 说明文字；无论取值如何，脚本都会收到当前等级。按等级缩放数值需要在效果代码中实现，返回的每级文本也应与计算方式对应。
:::

在 [C13-S2] 的 `GearInCaptainRole.run(member)` 已取得人物后、调用 `member.setCaptain(person)` 之前，加入局部赋值：

```java
person.getStats().setSkillLevel("GearIn_captain_mobility", 2f);
person.getStats().refreshCharacterStatsEffects();
```

随后沿原方法绑定 Mira 并更新成员。人物拥有技能等级，成员又引用这位舰长，单舰效果才有实际对象。只将同一技能交给舰队指挥官，并不会把 `SHIP` 变成逐舰效果；需要逐舰应用时另选对应类型。

## 实现 Rowan 的舰队协同技能

保存 `GearIn_fleet_coordination.skill`。这条技能接收整支舰队的统计项，用 `FLEET` 类型连接效果类：

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

给舰队范围最大航速加 1，并将超空间燃料消耗乘以 0.9。`GearInFleetCoordinationEffect` 的局部应用与撤销如下：

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

航速修饰是 `StatBonus` 的平加，燃料消耗是统计项的倍率，各字段按对应方式清理。地图有效航速沿 [C11-S2] 的 `getBurnLevel()` 读取，超空间燃料使用则从舰队燃料统计观察。

在 `GearInFleetCommanderRole.run(fleet)` 取得 Rowan 后、`fleet.setCommander(person)` 之前加入：

```java
person.getStats().setSkillLevel("GearIn_fleet_coordination", 1f);
```

写入指挥官时，人物 stats 关联当前舰队并刷新，原有 `forceSync()` 更新成员与汇总。效果作用于关联舰队，单舰战斗速度仍由舰长技能或其他单舰来源决定。

### 显示舰队汇总说明

这个效果类还实现 `FleetTotalSource`，返回 `FleetTotalItem`，在舰队汇总中提供标签、内容与排序。局部返回方法如下：

```java
public FleetTotalItem getFleetTotalItem() {
    FleetTotalItem item = new FleetTotalItem();
    item.label = "Fleet coordination";
    item.value = "+1 burn, -10% fuel";
    item.sortOrder = 50f;
    return item;
}
```

**汇总文字与实际修饰分别提供。** 这行文字解释统一的舰队效果，数值仍由 `apply(...)` 写入，再从当前舰队确认。舰长机动与管理员市场效果在各自对象上观察，本例只让指挥官效果提供舰队汇总。

## 实现 Selene 的市场治理技能

保存 `GearIn_colony_governance.skill`，用 `GOVERNED_OUTPOST` 接收管理员治理的市场：

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

`GearInColonyGovernanceEffect` 在传入市场增加 1 点稳定度，并向收入倍率写入 +10% 修饰，局部方法如下：

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

稳定度与收入都按技能来源管理，其他市场来源继续参与合并。效果直接使用引擎传入的 `MarketAPI`，当前管理员与治理市场的关系由 [C13-S2] 的绑定提供。

在 `GearInAdministratorRole.run()` 已调用 `market.setAdmin(person)` 之后，加入局部赋值和刷新：

```java
person.getStats().setSkillLevel("GearIn_colony_governance", 1f);
person.getStats().refreshGovernedOutpostEffects(market);
market.reapplyIndustries();
```

治理效果明确刷新 Foundry，产业再按新的市场值重算。换管理员或重新应用时，同一技能来源先解除再写入，市场结果跟随当前人物。

## 扩展到其他作用对象

三条技能分别使用 `ShipSkillEffect`、`FleetStatsSkillEffect` 与 `MarketSkillEffect`。扩展时根据需要作用于哪个对象，选择 `SkillEffectType` 与相应接口。**`type` 决定调用对象，`scope` 提供说明范围。** 声明文字与实际调用分别核对。

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

数值接口继承 `LevelBasedEffect`，提供效果说明、每级说明和 `ScopeDescription`。`CHARACTER_STATS` 修改人物本身的命令点、经验或动态统计，`FLEET` 使用关联舰队，两者在人物刷新时重新应用。

逐船与战机类型仍使用 `ShipSkillEffect`，区别在于引擎选择的集合。`ALL_SHIPS_IN_FLEET` 对非战机成员逐船应用，`SHIP_FIGHTERS` 对当前母舰的战机应用，`ALL_FIGHTERS_IN_FLEET` 对舰队全部战机应用；脚本使用实际传入对象完成数值修改。

## 让面板文字和数值保持同一来源

数值类的 `getEffectDescription(level)` 描述实际增量，独立 `DescriptionSkillEffect` 提供面板插语、高亮与颜色。普通组达到阈值时，描述和数值一起选入，玩家可以把说明对应到实际角色。以下局部类描述整支舰队的协同作用：

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

高亮词与颜色数组按相同顺序对应。两处高亮分别提供两种颜色，描述文字、数值说明与汇总行各完成自己的显示职责，实际修饰仍由数值回调提供。

## 在等级变化时清理原效果

**引擎提供效果来源 ID，应用与撤销使用同一个 ID。** 换舰、换人物、读档和技能刷新都可能再次调用效果。`modify` 覆盖同一来源，`unapply` 从各字段解除它，当前对象才能继续合并剩余来源。

本节的固定增量由等级阈值选组。需要数值随等级缩放时，使用传入的 `level` 计算，并让每级说明对应计算方式；`effectBasedOnLevel=false` 本身没有改变回调收到的等级。

验证清理时保存当前等级，设为 0 并走对应成员、舰队或治理市场的更新入口，再恢复原等级。回调每次使用引擎传入对象，保存上一次对象到静态字段会使后续应用指向错误目标。

`Global.getSettings().getSkillSpec(id)` 提供名称、图标、角色资格、说明范围和效果列表。规格加载、人物等级与角色当前数值分别确认，技能的接入过程才完整。

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
4. 在专用巡逻队读取有效航速与超空间燃料统计，核对 Rowan 的 +1 舰队范围最大航速和 0.9 燃料倍率；舰队汇总项同时提供说明。
5. 打开 Foundry，查看 Selene 的技能与市场稳定度、收入倍率，核对 +1 稳定度和 10% 收入修饰。
6. 在同一对象的局部等级实验中保存原等级，改为 0 并完成成员、舰队或治理市场刷新，观察来源解除；恢复原等级，再读取同一组数值。
7. 保存读档后比较角色 ID、等级与当前数值；日志发生加载错误时，逐项对照资源 ID、作用类型与完整类名。

预期结果：

- 三个技能规格分别取得 `GearIn_captain_mobility`、`GearIn_fleet_coordination` 与 `GearIn_colony_governance`。
- Mira 的舰船有 8% 最大速度、12% 加速度，等级 2 再有 15% 转向加速度；等级 1 保留普通组。
- Rowan 的关联舰队获得 +1 范围最大航速和 0.9 超空间燃料倍率，汇总行解释这一组收益。
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
    - **解决方案**：核对汇总类与实际效果类，确认航速和燃料 modifier 写在 `FleetStatsSkillEffect.apply()` 中，人物 stats 已关联当前舰队并刷新。

- **管理员技能改变了其他市场**：实现重新查找了错误市场。
    - **解决方案**：确认 `.skill` 使用 `GOVERNED_OUTPOST`，并在 `MarketSkillEffect.apply()` 中只使用引擎传入的 `MarketAPI`。

- **反复刷新后收入倍率持续叠加**：每次使用新的来源 ID。
    - **解决方案**：保持 modifier ID 稳定，使用 `modifyPercent` 覆盖同一来源，并在 `unapply()` 中调用 `unmodify()`。

## 总结

Mira 的技能改变所驾驶舰船，Rowan 的技能作用于关联舰队，Selene 的技能应用到治理市场。注册行给出身份与资格，定义文件选择等级组和对象，效果类再写入数值并提供对应说明。

角色或等级变化后，效果按稳定来源清理和重新应用。人物等级、实际持有者与当前数值分别成立，面板文字和汇总结果才能对应同一条技能。

## 建议参考项

- `com.fs.starfarer.api.characters.SkillSpecAPI`：技能规格、资格、作用域和效果列表。
- `com.fs.starfarer.api.characters.SkillEffectType`：定义文件的作用对象选择。
- `com.fs.starfarer.api.characters.ShipSkillEffect`：单舰与战机数值。
- `com.fs.starfarer.api.characters.FleetStatsSkillEffect`：关联舰队数值。
- `com.fs.starfarer.api.characters.MarketSkillEffect`：治理市场的效果。
- `com.fs.starfarer.api.characters.DescriptionSkillEffect`：面板文本和高亮。
- `com.fs.starfarer.api.characters.FleetTotalSource`：提供舰队汇总显示项。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：等级设置与角色、治理市场刷新。
