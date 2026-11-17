# 制作角色技能

Starsector 的角色技能拆在三层：`skill_data.csv` 管面板身份与归类，`.skill` 编排系别、作用域和等级组，真正往舰船、舰队或殖民地里写入修饰、并在失效时一并清掉的，则是对应的 Java 效果类。

顺着这套三层结构做三条示范技能 —— 单舰机动、舰队协同与殖民地治理，分别挂到指挥官与管理员身上。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C1-S7] 的 CSV 列格式与转义规则；
- [C8-S2] 的 `MutableStat` 来源 ID 与 `unmodify` 清理机制；
- [C11-S2] 的 `GearInPatrolRoster`，巡逻队指挥官 Brann Hale 的机动与协同两条技能写在他身上；
- [C13-S2] 的管理员 Selene，治理技能写在她身上。

## 技能的三层组成

同一条技能在工程里同时跨越三处定义，ID 必须逐字对齐：

| 文件 | 负责什么 |
| ---- | -------- |
| `data/characters/skills/skill_data.csv` | 名称、排序、图标、背景说明，以及允许谁持有 (`combat officer`、`admiral`、`admin`) |
| `data/characters/skills/<id>.skill` | 所属系别 (`governingAptitude`)、精英开关、面板作用域与效果组 (`effectGroups`) |
| Java 效果类 | 具体的数值修改、撤回逻辑、悬浮面板与舰队汇总条渲染 |

## 登记静态表：skill_data.csv

在 `data/characters/skills/skill_data.csv` 中追加三条定义 (表头共 14 列，与原版完全一致)：

```csv
id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon,
GearIn_captain_mobility,Captain Mobility,1025,2,1,0,"Improve the speed and handling of the piloted ship.","GearIndustry",TRUE,,,,graphics/icons/skills/helmsmanship.png,
GearIn_fleet_coordination,Fleet Coordination,2025,2,1,0,"Improve fleet movement and hyperspace fuel economy.","GearIndustry",,TRUE,,,graphics/icons/skills/navigation.png,
GearIn_colony_governance,Colony Governance,3025,2,1,0,"Improve stability and income at the governed market.","GearIndustry",,,TRUE,,graphics/icons/skills/industrial_planning.png,
```

- `combat officer`、`admiral`、`admin` 三列分别对应单舰军官、舰队指挥官与殖民地管理员；
- `tier`、`reqPoints` 与 `reqPointsPerExtraSkill` 控制技能树里的层级与点亮门槛。若您的技能只供脚本直接赋予特定角色，这几列填入合法数值即可，还可在 `tags` 中标上 `"npc_only"`，将其从玩家技能树与 AI 核心可选池中隐藏 (`Skills.TAG_NPC_ONLY`)。

## 编排效果组：.skill 文件

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

- **`governingAptitude`**：取原版四系之一：`combat`、`leadership`、`technology`、`industry`，决定技能图标边框底色与归类系别；
- **`scope`**：把 `scope` 写在技能顶层时，UI 面板会在顶部单列一行作用域说明，而不再在每条子效果末尾重复显示；不过各个 Java 效果类仍需实现 `getScopeDescription()` 并返回合法的 `ScopeDescription`；
- **`elite` 与 `effectGroups`**：设了 `"elite": true` 后，技能最高可升到 2 级：`requiredSkillLevel: 1` 为普通效果组，`requiredSkillLevel: 2` 为精英效果组。`effectBasedOnLevel` 为 `false` 表示每组只在达到门槛时开关，不按技能等级乘算；
- **常用 `type` 取值 (`SkillEffectType`)**：
  - `DESCRIPTION`：对应 `DescriptionSkillEffect`，输出整条技能的风味或概念说明文字；
  - `SHIP` / `ALL_SHIPS_IN_FLEET` / `SHIP_FIGHTERS` / `ALL_FIGHTERS_IN_FLEET`：对应 `ShipSkillEffect`，按所驾单舰、全队舰船或对应战机联队分别注入；
  - `FLEET`：对应 `FleetStatsSkillEffect`，作用于 `MutableFleetStatsAPI` (例如最大航速与超空间油耗)；
  - `GOVERNED_OUTPOST` / `ALL_OUTPOSTS`：对应 `MarketSkillEffect`，直接把受治或名下全部 `MarketAPI` 传给效果类；
  - `CHARACTER_STATS`：对应 `CharacterStatsSkillEffect`，改写人物自身的 `MutableCharacterStatsAPI.getDynamic()` (例如原版 `industrial_planning.skill` 给全产业产出 +1 的全局统计词条)。若技能既要直接改市场，又要通过动态词条给下游产业读取，通常会按作用对象混用这两种 `type`。

另外两条单等级技能结构相同。`data/characters/skills/GearIn_colony_governance.skill`：

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

`data/characters/skills/GearIn_fleet_coordination.skill`：

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

::: warning type 与 Java 接口必须配对

`.skill` 里的 `type` 决定引擎按哪个接口实例化 `script`。例如把 `GOVERNED_OUTPOST` 填给只实现了 `CharacterStatsSkillEffect` 的类，游戏在刷新人物效果时会因为类型转换失败而报错。

:::

## 三种效果接口与完整实现

| 接口 | `apply(...)` 收到的目标对象 | 本节示范技能 |
| ---- | --------------------------- | ------------ |
| `ShipSkillEffect` | `MutableShipStatsAPI` 与船体级别 `HullSize` | `Captain Mobility` (含精英组) |
| `FleetStatsSkillEffect` | `MutableFleetStatsAPI` | `Fleet Coordination` (附带 `FleetTotalSource`) |
| `MarketSkillEffect` | `MarketAPI` | `Colony Governance` |

所有效果类都必须在 `unapply(...)` 里使用传入的同一 `id` 参数清理自己写入的修饰，**千万不要自己硬编码别的来源键** —— 否则人物洗点、离队或卸任后，该条修饰词条将永久残留在船只或市场上。

### 单舰机动：普通与精英

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

`getEffectDescription(...)` 的文本只负责展示，不会自动同步 `apply(...)` 里的数字；两边修改时记得一起改。

### 说明文字接口：DescriptionSkillEffect

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

`getHighlights()` 返回需要高亮的子串数组，**`getHighlightColors()` 返回的数组长度必须和它完全相等**；`getTextColor()` 返回 `null` 时沿用默认灰白正文色。

### 舰队协同与舰队汇总条：FleetTotalSource

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

除了修改 `MutableFleetStatsAPI` 之外，这个类还额外实现了 `FleetTotalSource`：`getFleetTotalItem()` 返回一条 `FleetTotalItem`，让这条技能像原版舰队后勤技能一样出现在玩家舰队界面底部的汇总统计栏里，并通过 `sortOrder` 控制显示顺位。

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

### 殖民地治理

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

`market.getStability()` 返回 `MutableStatWithTempMods` (继承自 `MutableStat`)，写入时附带来源说明字符串 `"Colony governance"` 以便悬浮详情面板列出条目；`unapply(...)` 里调用 `unmodifyFlat(id)` 或 `unmodify(id)` 都能按同一个来源键精确撤销。

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

数据表和脚本类就位后，还要通过 `person.getStats().setSkillLevel(id, level)` 实际赋予角色：普通等级填 `1f`，带精英效果的等级填 `2f`。

管理员 Selene 的治理技能放在 [C13-S2] `GearInAdministratorRole.assign(...)` 的创建分支里，随后靠现有的 `setAdmin(admin)`、`refreshGovernedOutpostEffects(market)` 与 `reapplyIndustries()` 带进 Foundry 市场数值：

```java
admin.getStats().setSkillLevel("GearIn_colony_governance", 1f);
sector.getImportantPeople().addPerson(admin);
```

巡逻队指挥官 Brann Hale 的两条技能则加在 [C11-S2] `GearInPatrolRoster.createCommander(Random)` 里，紧接在设定性格与职务之后：

```java
person.getStats().setSkillLevel("GearIn_captain_mobility", 1f);
person.getStats().setSkillLevel("GearIn_fleet_coordination", 1f);
```

由于 Brann 同时担任巡逻队指挥官与旗舰舰长，因此 `GearIn_fleet_coordination` 会作用于整支巡逻队，而 `GearIn_captain_mobility` 则作用于他亲自驾驶的那艘工匠级旗舰；若把 `GearIn_captain_mobility` 改成 `2f`，还能立刻激活 `.skill` 中定义好的精英转向加速度加成。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**：

- 打开 Nipher Foundry 殖民地界面，管理员 Selene Ward 持有 `Colony Governance`，市场稳定度面板能看到来自 `"Colony governance"` 的 `+1` 加成，收入乘数提高 `10%`；
- 飞往巡逻队附近查看指挥官 Brann Hale，同时持有 `Captain Mobility` 与 `Fleet Coordination`；
- 保存并重新读档，三条技能与对应修正条目依然完好，不会出现重复叠加。

## 常见问题

- **技能面板里找不到技能**：`skill_data.csv` 的 ID 与 `.skill` 不一致，或者文件路径不在 `data/characters/skills/` 下。
    - **解决方案**：逐字核对三处 ID，确认文件处于 `data/characters/skills/` 目录且格式正确。

- **人物身上显示了技能图标，但数值完全不变**：`.skill` 中的 `type` 与 Java 类实现的接口不匹配，或修改管理员技能后未触发 `refreshGovernedOutpostEffects(market)`。
    - **解决方案**：核对 `SkillEffectType` 与接口对应关系；若改了管理员技能，补调 `refreshGovernedOutpostEffects(market)` 与 `market.reapplyIndustries()`。

- **撤职或洗点后加成越刷越多**：`unapply(...)` 漏掉了某项修饰，或者自行传入了与形参 `id` 不同的字符串。
    - **解决方案**：在 `unapply(...)` 中用同一个形参 `id` 一对一撤销所有写入过的 `MutableStat` / `StatBonus` 修饰。

- **精英效果始终不触发**：`.skill` 缺少 `"elite": true`，或者给人物写入的等级只有 `1f`。
    - **解决方案**：确认 `.skill` 声明了 `"elite": true` 与 `requiredSkillLevel: 2` 效果组，并用 `setSkillLevel(id, 2f)` 赋予精英等级。

- **旧存档中的人物没有新技能**：人物技能在创建角色时写入，旧档里已实例化的 `PersonAPI` 不会自动补写。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.characters.ShipSkillEffect`：单舰及全队舰船效果接口。
- `com.fs.starfarer.api.characters.FleetStatsSkillEffect`：舰队动态统计效果接口，常与 `FleetTotalSource` 搭配使用。
- `com.fs.starfarer.api.characters.MarketSkillEffect`：殖民地市场直接修正接口。
- `com.fs.starfarer.api.characters.CharacterStatsSkillEffect`：人物动态属性修正接口 (`CHARACTER_STATS`)。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：`setSkillLevel(...)` 与 `refreshGovernedOutpostEffects(...)` 所在接口。
- `com.fs.starfarer.api.characters.SkillSpecAPI`：技能规格、作用域与 `SkillEffectType` 定义。
- `data/characters/skills/helmsmanship.skill`：带精英效果组的原版写法。
- `data/characters/skills/industrial_planning.skill`：结合 `CHARACTER_STATS` 与自定义描述 (`BaseSkillEffectDescription`) 的原版写法。
