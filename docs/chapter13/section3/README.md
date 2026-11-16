# 制作角色技能

给亲手造出来的人物写技能，很难忍住不把数字写大，总想让他们显得特别一点。

这里的三条技能都只给一点点。这些人往后会一直待在齿轮工业里，现在给他们多少，往后的平衡就得背多少。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C1-S7] 的 CSV 列与转义；
- [C8-S2] 的 `MutableStat` 来源 ID 与 `unmodify`；
- [C11-S1] 的巡逻队指挥官，机动与协同两条技能写在他身上；
- [C13-S2] 的管理员 Selene，治理技能写在她身上。

## 一条技能由三份东西组成

同一个技能 ID 对应三处：

| 文件 | 内容 |
| ---- | ---- |
| `skill_data.csv` | 名称、排序、图标，以及它属于战斗军官、舰队指挥还是管理员 |
| `.skill` | 作用域与效果组，指明每一组效果由哪个类实现 |
| Java 效果类 | `apply(...)` 与 `unapply(...)` 的实际数值 |

三处的 ID 必须一致。**任何一处写错，技能就不出现或不起作用。**

## 登记数据

`data/characters/skills/skill_data.csv`：

```csv
id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon,
GearIn_captain_mobility,Captain Mobility,1025,2,1,0,"Improve the speed and handling of the piloted ship.","GearIndustry",TRUE,,,,graphics/icons/skills/helmsmanship.png,
GearIn_fleet_coordination,Fleet Coordination,2025,2,1,0,"Improve fleet movement and hyperspace fuel economy.","GearIndustry",,TRUE,,,graphics/icons/skills/navigation.png,
GearIn_colony_governance,Colony Governance,3025,2,1,0,"Improve stability and income at the governed market.","GearIndustry",,,TRUE,,graphics/icons/skills/industrial_planning.png,
```

`combat officer`、`admiral`、`admin` 三列各标记一种使用者，同一条技能只填其中一列。本节的技能由脚本直接写入人物，不经过技能树，`tier` 与 `reqPoints` 只需合法。

原版的 `description` 是一句与效果无关的引文，本节写成功能说明。

## 描述技能：.skill 文件

`data/characters/skills/GearIn_captain_mobility.skill`：

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

- `scope` 写在技能层面，面板就把作用域单独列一行，不再在每条效果后重复；
- `effectGroups` 是一组一组的效果，`requiredSkillLevel` 为 1 的是普通等级，为 2 的是精英等级；
- `elite: true` 声明这条技能有精英等级；
- 每条效果的 `type` 决定它实现哪个接口：`SHIP` 对应 `ShipSkillEffect`，`FLEET` 对应 `FleetStatsSkillEffect`，`GOVERNED_OUTPOST` 对应 `MarketSkillEffect`，`DESCRIPTION` 对应 `DescriptionSkillEffect`。

另外两条技能只有一个等级，结构相同。`GearIn_colony_governance.skill`：

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

`GearIn_fleet_coordination.skill`：

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

`governingAptitude` 取原版的四个系：`combat`、`leadership`、`technology`、`industry`。

::: warning 类型与接口必须对应

`type` 写错，效果类与接口对不上，技能不会按预期生效。原版 `.skill` 中的 `CHARACTER_STATS` 是另一类写法，改人物属性而非直接改数值对象，本节不用。

:::

## 三种效果接口

| 接口 | `apply(...)` 收到 | 本节的技能 |
| ---- | ----------------- | ---------- |
| `ShipSkillEffect` | 舰船数值与船体级别 | 机动 |
| `FleetStatsSkillEffect` | 舰队数值 | 协同 |
| `MarketSkillEffect` | 市场 | 治理 |

三者都要求实现 `unapply(...)`，`apply(...)` 写入的每一项修饰，都要在 `unapply(...)` 里以同一个来源 ID 清掉。`id` 由游戏传入，不要自己编。

### 机动：普通与精英

`src/data/scripts/gearin/campaign/GearInCaptainMobilityEffect.java`：

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

`src/data/scripts/gearin/campaign/GearInCaptainMobilityEliteEffect.java`：

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

普通与精英各是一个类，对应 `.skill` 里的两个效果组。转向加速度加成只在精英组，所以只属于精英等级。

`getEffectDescription(...)` 的返回值显示在技能面板里，必须与 `apply(...)` 实际写入的数值一致，两者不一致时，游戏照样显示写错的那一份。

### 说明文字

`src/data/scripts/gearin/campaign/GearInCaptainMobilityDescription.java`：

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

`getHighlights()` 返回要高亮的子串，`getHighlightColors()` 的长度应与它相同。

### 舰队协同

`src/data/scripts/gearin/campaign/GearInFleetCoordinationEffect.java`：

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

这个类同时实现 `FleetTotalSource`，`getFleetTotalItem()` 提供舰队汇总里的一行 `Fleet coordination`，`sortOrder` 决定排序。

`src/data/scripts/gearin/campaign/GearInFleetCoordinationDescription.java`：

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

### 治理

`src/data/scripts/gearin/campaign/GearInColonyGovernanceEffect.java`：

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

稳定度是 `MutableStatWithTempMods`，写入用 `modifyFlat` 并给出描述，解除用 `unmodifyFlat`。收入乘数是 `MutableStat`，对应 `modifyPercent` 与 `unmodify`。**写入与解除不成对，修饰就清不掉。**

`src/data/scripts/gearin/campaign/GearInColonyGovernanceDescription.java`：

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

## 把技能写给人物

技能数据只是声明，人物没有拿到等级，什么也不会发生。等级由 `setSkillLevel(id, level)` 写入，普通为 1，精英为 2。

Selene 的治理技能写在创建分支里，登记之前：

```java
admin.getStats().setSkillLevel(SKILL_ID, 1f);
sector.getImportantPeople().addPerson(admin);
```

`SKILL_ID` 为 `GearIn_colony_governance`。[C13-S2] 已有的 `refreshGovernedOutpostEffects(market)` 与 `reapplyIndustries()` 让它进入 Foundry 的数值。

巡逻队指挥官 Brann Hale 的两条技能写在 `createCommander(...)` 里，紧接在设定职务之后：

```java
person.getStats().setSkillLevel("GearIn_captain_mobility", 1f);
person.getStats().setSkillLevel("GearIn_fleet_coordination", 1f);
```

Brann 同时是舰队指挥官与旗舰舰长，机动技能作用于他驾驶的旗舰，协同技能作用于整支舰队。

三条技能的加成都不大：机动 8% 航速与 12% 加速度，精英再加 15% 转向加速度；协同 1 级最大航速与 10% 超空间燃料；治理 1 点稳定度与 10% 收入。

管理员技能叠加在市场已有的全部效果上，初版就给大数字，后面所有产业数值都要为它让路。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- Nipher Foundry 的管理员 Selene 拥有 Colony Governance，市场稳定度与收入相应提高；
- 巡逻队指挥官 Brann 拥有 Captain Mobility 与 Fleet Coordination；
- 保存并读档，三条技能仍在，数值没有叠加。

页面中的现象依据接口与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **技能面板里没有这条技能**：`skill_data.csv` 的 ID 与 `.skill` 不一致，或者目录写错。
    - **解决方案**：三处 ID 逐字核对，文件放在 `data/characters/skills/` 下。

- **人物有技能，数值不变**：作用域与接口对不上，或者没有刷新。
    - **解决方案**：核对 `type` 与效果接口的对应关系，治理技能还要调用 `refreshGovernedOutpostEffects(...)`。

- **数值越刷越多**：`unapply(...)` 没有清掉 `apply(...)` 写入的修饰。
    - **解决方案**：写入与解除成对，使用同一个来源 ID。

- **精英效果从不出现**：`.skill` 缺少 `elite: true`，或者人物的技能等级只有 1。
    - **解决方案**：补上声明，用 `setSkillLevel(id, 2f)` 写入精英等级。

- **旧存档中没有技能**：技能随人物创建写入，旧存档的人物已经存在。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.characters.ShipSkillEffect`：单舰效果接口，机动技能使用。
- `com.fs.starfarer.api.characters.FleetStatsSkillEffect`：舰队效果接口，协同技能使用。
- `com.fs.starfarer.api.characters.MarketSkillEffect`：市场效果接口，治理技能使用。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：`setSkillLevel(...)` 与各类刷新方法。
- `com.fs.starfarer.api.characters.SkillSpecAPI`：技能规格，包含使用者标记与作用域。
- `data/characters/skills/helmsmanship.skill`：带精英组的原版写法。
- `data/characters/skills/industrial_planning.skill`：原版治理技能。
