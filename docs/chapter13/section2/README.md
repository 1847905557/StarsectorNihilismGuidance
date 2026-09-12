# 让人物承担角色

本节把人物放进舰长、舰队指挥官和管理员槽位，并从真实持有者读取结果。军官使用 `OfficerDataAPI` 保存 roster 与成长数据，人物本身仍由 `PersonAPI`、`MutableCharacterStatsAPI` 和 `PersonalityAPI` 提供资料与状态。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

- 区分三种角色的归属对象、写入入口和读取入口；
- 使用 `MutableCharacterStatsAPI` 读取等级、经验和技能状态，并在修改后刷新；
- 使用 `OfficerDataAPI` 创建军官数据、加入 roster、增加经验和升级；
- 使用 `PersonalityAPI` 读取 ID、显示名和描述，并把性格与舰队行为联系起来；
- 从舰队、舰船、军官和殖民地界面判断绑定是否成功。

## 前置知识

- [C13-S1] 的 `PersonAPI` 创建、人物资料和稳定 ID；
- [C11-S2] 的 `CampaignFleetAPI`、`FleetDataAPI` 和成员集合；
- [C11-S4] 的 `FleetMemberAPI`、旗舰和舰长读取；
- [C12-S1] 创建的 `GearIn_market_foundry`；
- 能够打开舰队、舰船和殖民地管理界面。

## 人物对象与角色槽位

`PersonAPI` 是人物对象，角色槽位由其他对象保存引用。舰长字段属于 `FleetMemberAPI`，舰队指挥官字段属于 `CampaignFleetAPI`，管理员字段属于 `MarketAPI`。写入槽位不会改变人物的 Java 类型，只会改变持有引用的对象状态。

因此，验证必须从持有者开始。舰长使用 `member.getCaptain()`，指挥官使用 `fleet.getCommander()`，管理员使用 `market.getAdmin()`。人物的姓名、势力、性格和资料仍从 `PersonAPI` 读取；人物等级、经验和技能状态从 `person.getStats()` 读取。

### 角色归属速查表

| 角色 | 持有对象 | 写入入口 | 读取入口 | 下游表现 |
|---|---|---|---|---|
| 舰长 | `FleetMemberAPI` | `setCaptain(person)` | `getCaptain()` | 单舰 stats、舰船详情和战斗舰船 |
| 舰队指挥官 | `CampaignFleetAPI` | `setCommander(person)` | `getCommander()` | 舰队同步与成员 `getFleetCommander()` |
| 管理员 | `MarketAPI` | `setAdmin(person)` | `getAdmin()` | 殖民地管理和市场产业 |
| 军官数据 | `FleetDataAPI` | `addOfficer(...)` | `getOfficerData(person)` | 军官面板和成长状态 |

表中的持有对象决定了写入和回读入口。一个人物可以被多个系统读取，但每个角色的状态只能从自己的持有对象判断。

## MutableCharacterStatsAPI：人物的可变状态

`MutableCharacterStatsAPI` 位于人物内部，负责成长和技能状态。`getLevel()`、`getXP()`、`getSkillLevel(id)` 和 `hasSkill(id)` 只读取当前值；`addXP(long)` 与 `setSkillLevel(id, level)` 修改人物状态；`refreshCharacterStatsEffects()` 重新应用已经注册的效果。

经验和技能写入是两个不同动作。增加经验可能触发等级变化，但不会替代技能选择；直接设置技能等级可以改变人物状态，但不会创建技能文件或效果脚本。技能实现继续由 [C13-S3] 负责。

```java
MutableCharacterStatsAPI stats = person.getStats();
int levelBefore = stats.getLevel();
long xpBefore = stats.getXP();
stats.addXP(1000L);
stats.setSkillLevel("helmsmanship", 1f);
stats.refreshCharacterStatsEffects();
LOG.info(String.format("stats: level=%d->%d xp=%d->%d skill=%s has=%s",
        levelBefore, stats.getLevel(), xpBefore, stats.getXP(),
        stats.getSkillLevel("helmsmanship"), stats.hasSkill("helmsmanship")));
```

日志同时记录修改前后的值和刷新后的技能状态。`hasSkill` 只能证明人物拥有技能，不能证明舰长或指挥官效果已经到达成员 stats；下游对象还要经过自己的更新入口。

### stats 方法速查表

| 方法 | 作用 | 结果 |
|---|---|---|
| `getLevel()` | 读取人物等级 | 角色面板中的等级 |
| `getXP()` | 读取累计经验 | 日志中的经验值 |
| `addXP(long)` | 增加人物经验 | XP 增加或达到升级条件 |
| `getSkillLevel(id)` | 读取技能等级 | 技能树对应等级 |
| `setSkillLevel(id, level)` | 写入技能等级 | 人物技能状态改变 |
| `hasSkill(id)` | 判断是否拥有技能 | 与等级读取相互核对 |
| `refreshCharacterStatsEffects()` | 重放人物效果 | 下游读取刷新后的状态 |

## OfficerDataAPI：roster 与成长

`OfficerDataAPI` 不是人物资料的替代品。`Global.getFactory().createOfficerData(person)` 创建成长容器，`getPerson()` 返回它关联的 `PersonAPI`；经验、候选技能和升级状态保存在这个容器中。人物资料、性格和 stats 仍从关联人物读取。

把人物设置为舰长不会自动加入 roster，把人物加入 roster 也不会自动分配舰船。两个关系分别由 `FleetDataAPI` 和 `FleetMemberAPI` 维护，所以教程把它们作为相邻但独立的步骤。

```java
FleetDataAPI fleetData = fleet.getFleetData();
OfficerDataAPI officerData = Global.getFactory().createOfficerData(person);
fleetData.addOfficer(officerData);
OfficerDataAPI stored = fleetData.getOfficerData(person);
stored.addXP(500L);
if (stored.canLevelUp()) {
    stored.makeSkillPicks();
}
```

升级时先读取 `getSkillPicks()`，再把候选 ID 交给 `levelUp(skillId)`。再次调用 `makeSkillPicks()` 会重新生成候选，验证应记录候选后再选择。升级完成后从 `stored.getPerson()` 回到人物，读取 `getSkillLevel` 和 `hasSkill`，才能确认结果写回了关联人物。

### OfficerDataAPI 速查表

| 方法 | 职责 | 判断依据 |
|---|---|---|
| `getPerson()` | 回到人物对象 | 返回关联 `PersonAPI` |
| `setPerson(person)` | 替换关联人物 | 后续成长指向新人物 |
| `addXP(xp)` | 增加军官经验 | `canLevelUp()` 可能变为真 |
| `canLevelUp()` | 判断升级条件 | 是否可生成候选 |
| `makeSkillPicks()` | 生成候选技能 | 更新候选状态 |
| `getSkillPicks()` | 读取候选 ID | 选择 `levelUp` 参数 |
| `levelUp(skillId)` | 完成技能升级 | 人物技能等级改变 |
| `isMadePicks()` | 判断候选是否生成 | 避免重复投骰 |

## 舰长：单个 FleetMemberAPI 的范围

舰长是单舰成员的属性。示例从玩家舰队取得旗舰，把 `GearIn_officer` 写入 `setCaptain`，再从同一个成员读取 `getCaptain()`。其他成员不会因为旗舰有舰长而自动复用该人物。

舰长人物的个人技能在成员 stats 更新时参与单舰计算。`person.getStats()` 和 `member.getStats()` 是不同层级的对象；前者描述人物，后者描述船。设置舰长后只打印人物对象，不能证明成员已经保存了字段。

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetMemberAPI flagship = fleet.getFlagship();
PersonAPI captain = flagship.getCaptain();
if (captain == null || !"GearIn_officer".equals(captain.getId())) {
    captain = createCaptain();
}
flagship.setCaptain(captain);
flagship.setStatUpdateNeeded(true);
LOG.info("captain=" + flagship.getCaptain().getId());
```

`setStatUpdateNeeded(true)` 只是标记成员需要重新计算 stats，并不等于完成计算。战斗侧 `ShipAPI` 是由生涯成员生成的临时对象，生涯验证先以 `FleetMemberAPI` 为准。

## 舰队指挥官：CampaignFleetAPI 的范围

舰队指挥官由 `CampaignFleetAPI` 保存。调用 `setCommander` 后，`getCommander()` 返回指挥官，成员的 `getFleetCommander()` 读取同一舰队上下文。示例使用 `GearIn_commander`，与旗舰的 `GearIn_officer` 分开，日志可以直接检查两个槽位是否串线。

指挥官的舰队级效果要经过舰队同步和成员更新才能进入下游。`forceSync()` 会重新计算当前舰队组成和相关 stats，但不会创建 `OfficerDataAPI`，也不会把指挥官加入玩家 roster。

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
PersonAPI commander = fleet.getCommander();
if (commander == null || !"GearIn_commander".equals(commander.getId())) {
    commander = createCommander();
}
fleet.setCommander(commander);
fleet.forceSync();
LOG.info(String.format("commander=%s flagshipCommander=%s",
        fleet.getCommander().getId(),
        fleet.getFlagship().getFleetCommander().getId()));
```

成员读取到的指挥官是集合传播结果。若 `getCommander()` 正确而 `getFleetCommander()` 为空，先确认成员仍属于当前舰队，再检查同步是否完成。

## 管理员：MarketAPI 的范围

管理员由市场保存。`setAdmin(person)` 替换市场字段，`getAdmin()` 返回当前管理员。人物的 `setMarket(market)` 与 `getMarket()` 是反向关系，脚本同时维护两个方向，便于日志验证。

市场产业从 `market.getAdmin().getStats()` 读取管理员的动态数值。这个入口说明管理员 stats 会影响经济计算，但本节只验证绑定和刷新，不实现管理员技能效果。

```java
MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
PersonAPI admin = market.getAdmin();
if (admin == null || !"GearIn_admin".equals(admin.getId())) {
    admin = createAdministrator();
}
market.setAdmin(admin);
admin.setMarket(market);
admin.getStats().refreshCharacterStatsEffects();
LOG.info(String.format("admin=%s market=%s",
        market.getAdmin().getId(), admin.getMarket().getId()));
```

市场界面读取市场字段，不能用旧的 `PersonAPI` 引用代替 `market.getAdmin()`。管理员替换后，先从市场重新取得对象，再检查人物反向市场字段。

### 角色状态边界速查表

| 维度 | 舰长 | 舰队指挥官 | 管理员 |
|---|---|---|---|
| 归属字段 | `FleetMemberAPI` | `CampaignFleetAPI` | `MarketAPI` |
| 直接读取 | `getCaptain()` | `getCommander()` | `getAdmin()` |
| 主要下游 | 单舰 stats、战斗舰船 | 舰队 stats、成员同步 | 市场产业、殖民地界面 |
| 复核入口 | `member.getStats()` | `member.getFleetCommander()` | `person.getMarket()` |

## PersonalityAPI：性格资料与行为

性格由 `PersonAPI` 保存。`setPersonality(Personalities.STEADY)` 写入稳定 ID，`getPersonalityAPI()` 返回 `PersonalityAPI`，再用 `getId()`、`getDisplayName()` 和 `getDescription()` 分别读取机器标识、界面名称和说明文本。

```java
person.setPersonality(Personalities.STEADY);
PersonalityAPI personality = person.getPersonalityAPI();
LOG.info(String.format("personality: id=%s name=%s description=%s",
        personality.getId(), personality.getDisplayName(),
        personality.getDescription()));
```

脚本逻辑应比较 ID，界面验证可以观察显示名和描述。性格是 AI 决策输入，不决定人物是否属于军官、舰长、舰队指挥官或管理员；角色归属仍由外部持有对象的字段决定。

观察行为时固定舰队组成、目标和战斗条件。单次路线变化不足以证明性格设置失败，先确认当前舰长或指挥官字段确实引用了设置性格的人物。

## 角色的生命周期与对象复用

角色入口的顺序是取得目标对象、按稳定 ID 复用或创建人物、补齐资料、写入角色槽位、从持有者回读。这个顺序把输入、人物和角色三个故障层分开，日志也能证明写入确实进入游戏对象。

重复运行时，先读取槽位中的人物或 roster 数据。只有当前对象为空或 ID 不符时才创建人物；无条件调用 `createPerson()` 会让新对象覆盖旧引用，姓名即使相同也无法证明是同一个对象。

一次运行内可以用对象相等性比较传入人物和回读人物。跨运行或读档后使用稳定 ID，因为载入会产生新的 Java 引用，引用地址变化不表示角色关系丢失。

舰长、指挥官和管理员的对象复用条件不同：舰长从成员字段复用，指挥官从舰队字段复用，管理员从市场字段复用。军官数据从 `getOfficerData(person)` 复用。四个入口都遵守 "先读后建"。

## 三层 stats 的传播

人物 stats、成员 stats 和舰队 stats 是不同层级。舰长人物 stats 通过成员更新进入单舰，舰队指挥官人物 stats 通过舰队同步进入成员，管理员人物 stats 由市场产业读取。数值相同不代表对象相同，日志应带上层级名称。

修改人物技能后只刷新人物 stats，成员或舰队是否重新计算取决于它们自己的更新入口。舰长示例标记成员更新，指挥官示例调用 `forceSync`，管理员示例刷新人物 stats；这三个动作不能互相替代。

技能效果出现异常时，先检查人物技能等级，再检查角色槽位，最后检查对应下游 stats。[C13-S3] 只处理技能注册和效果实现，角色归属错误应在本节的数据流中修复。

## 替换与恢复

测试替换角色前保存原人物 ID 和持有对象。舰长恢复到原成员，指挥官恢复到原舰队，管理员恢复到原市场；每次恢复后重新读取字段确认完成。

移除舰长不会自动删除 roster 中的军官，移除 roster 也不会替成员清空舰长。清理必须分别调用对应对象的 API，不能用清空整个舰队或市场来掩盖关系错误。

管理员替换可能影响市场通信目录。验证先检查 `getAdmin()` 与 `getMarket()`，再观察殖民地界面；旧人物引用只用于比较，不作为当前状态来源。

## 角色实现的组织

三个完整类分别负责舰长、舰队指挥官和管理员。每个类的 `run()` 只操作一个持有对象，创建方法只返回该角色需要的人物，日志只记录该角色的关键回读字段。

舰长类使用 `GearIn_officer` 和 Mira Vale，并把人物加入 roster；舰队指挥官类使用 `GearIn_commander` 和 Rowan Hale；管理员类使用 `GearIn_admin` 和 Selene Ward。不同 ID 让验证能够区分角色槽位。

军官升级用局部片段说明，不增加第四个角色类。军官是 `FleetDataAPI` 中的人物成长数据，不是新的角色持有者；成为舰长后仍要单独调用成员的 `setCaptain`。

## 完整最小例

三个类分别作为入口运行，完整源码与 fixture 保持一致。

::: details 完整成品：GearInCaptainRole

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInCaptainRole {
    private static final Logger LOG = Global.getLogger(GearInCaptainRole.class);

    public void run() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI flagship = fleet.getFlagship();
        PersonAPI person = flagship.getCaptain();
        if (person == null || !"GearIn_officer".equals(person.getId())) {
            person = createCaptain();
        }
        flagship.setCaptain(person);
        flagship.setStatUpdateNeeded(true);
        FleetDataAPI data = fleet.getFleetData();
        OfficerDataAPI officer = data.getOfficerData(person);
        if (officer == null) {
            data.addOfficer(person);
            officer = data.getOfficerData(person);
        }
        LOG.info(String.format("captain=%s officer=%s personality=%s",
                flagship.getCaptain().getId(), officer != null,
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createCaptain() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_officer");
        person.setName(new FullName("Mira", "Vale", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
```

:::

::: details 完整成品：GearInFleetCommanderRole

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInFleetCommanderRole {
    private static final Logger LOG = Global.getLogger(GearInFleetCommanderRole.class);

    public void run() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        PersonAPI person = fleet.getCommander();
        if (person == null || !"GearIn_commander".equals(person.getId())) {
            person = createCommander();
        }
        fleet.setCommander(person);
        fleet.forceSync();
        LOG.info(String.format("commander=%s flagshipCommander=%s personality=%s",
                fleet.getCommander().getId(),
                fleet.getFlagship().getFleetCommander().getId(),
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createCommander() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_commander");
        person.setName(new FullName("Rowan", "Hale", FullName.Gender.MALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
```

:::

::: details 完整成品：GearInAdministratorRole

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInAdministratorRole {
    private static final Logger LOG = Global.getLogger(GearInAdministratorRole.class);

    public void run() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
        PersonAPI person = market.getAdmin();
        if (person == null || !"GearIn_admin".equals(person.getId())) {
            person = createAdministrator();
        }
        market.setAdmin(person);
        person.setMarket(market);
        person.getStats().refreshCharacterStatsEffects();
        LOG.info(String.format("admin=%s market=%s personality=%s",
                market.getAdmin().getId(), person.getMarket().getId(),
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createAdministrator() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_admin");
        person.setName(new FullName("Selene", "Ward", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
```

:::

## 验证方案

前置数据：启用包含本节脚本的 MOD，准备玩家舰队、可部署旗舰和 [C12-S1] 创建的 `GearIn_market_foundry`。保存测试存档副本，重复运行和读档都使用副本。

1. 运行 `GearInCaptainRole`，打开舰队界面并选中旗舰，读取舰长姓名和日志中的 `captain`；
2. 打开军官面板，确认 `GearIn_officer` 出现在 roster，并检查 `getOfficerData(person)` 返回数据；
3. 运行 `GearInFleetCommanderRole`，重新打开舰队界面，比较舰队指挥官与旗舰舰长的 ID；
4. 让舰队完成一次移动或战斗部署，确认成员仍能读取舰队指挥官；
5. 运行 `GearInAdministratorRole`，打开 `GearIn_market_foundry` 的殖民地管理界面，确认管理员显示为 Selene Ward；
6. 读取管理员的 `getMarket()` 和市场的 `getAdmin()`，确认两者指向同一市场；
7. 查看三个入口输出的 `PersonalityAPI` 日志，核对 ID、显示名和描述来自 `steady`；
8. 在存档副本中再次运行三个入口，确认没有新增角色对象；
9. 重新载入存档后读取舰长、指挥官、管理员和军官 roster，确认稳定 ID 仍可取得。

预期结果：

- 旗舰的 `getCaptain()` 返回 `GearIn_officer`；
- `getOfficerData(person)` 返回关联的 `OfficerDataAPI`；
- `getCommander()` 返回 `GearIn_commander`；
- 旗舰的 `getFleetCommander()` 与舰队指挥官一致；
- `getAdmin()` 返回 `GearIn_admin`；
- `GearIn_admin.getMarket()` 返回 `GearIn_market_foundry`；
- 三个人物的性格 ID 均为 `steady`；
- 重复运行不会新增角色对象；
- 重新载入后角色资料和槽位归属仍可读取。

## 故障分析

- **舰队界面没有显示新的舰长**：目标成员不是当前旗舰，或没有调用 `setCaptain`。

  - **解决方案**：从当前舰队重新取得 `getFlagship()`，绑定人物后读取同一成员的 `getCaptain()`。

- **军官出现在 roster，但舰船没有舰长**：`OfficerDataAPI` 不负责写入成员舰长字段。

  - **解决方案**：保留 `addOfficer(...)`，再对目标成员调用 `setCaptain(person)`。

- **舰队指挥官日志正确，成员读取为空**：舰队尚未同步，或读取的是旧成员对象。

  - **解决方案**：调用 `forceSync()`，再从当前舰队取得成员并读取 `getFleetCommander()`。

- **管理员界面仍显示旧人物**：市场字段已被覆盖，代码继续读取旧引用。

  - **解决方案**：从 `market.getAdmin()` 重新读取当前管理员，并同步设置 `person.setMarket(market)`。

- **管理员 stats 改变但市场数值不变**：管理员效果尚未刷新或市场尚未重算。

  - **解决方案**：刷新管理员 stats，再重新打开市场界面读取当前值。

- **性格显示为空或行为没有变化**：性格 ID 无效，或人物没有绑定到当前角色槽位。

  - **解决方案**：使用 `Personalities.STEADY` 等有效 ID，读取 `getPersonalityAPI()` 后检查角色字段。

## 总结

`PersonAPI` 保存人物资料，`MutableCharacterStatsAPI` 保存成长和技能状态，`OfficerDataAPI` 保存军官 roster 数据。舰长、舰队指挥官和管理员分别由成员、舰队和市场持有引用；绑定后从持有者回读，才能证明关系已经写入并进入下游计算。`PersonalityAPI` 提供性格 ID、界面名称和描述，角色对象再把人物资料交给舰船、舰队或市场行为使用。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物资料、stats、性格和反向归属；
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：等级、经验、技能状态和刷新；
- `com.fs.starfarer.api.characters.OfficerDataAPI`：军官 roster 数据与升级；
- `com.fs.starfarer.api.characters.PersonalityAPI`：性格 ID、显示名和描述；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队指挥官和同步；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：舰长字段和成员 stats；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：管理员字段和市场读取。
