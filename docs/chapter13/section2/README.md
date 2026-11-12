# 让人物承担角色

本节把 Mira 分配给巡逻队中的一艘狼，让 Rowan 指挥整支舰队，再让 Selene 管理 Foundry。人物写入不同对象后，技能也沿不同范围应用；我们完成这些绑定，再处理军官成长与性格资料，确认人物状态如何影响实际角色。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 区分三种角色的归属对象、写入入口和读取入口；
- 使用 `MutableCharacterStatsAPI` 读取等级、经验和技能状态，并在修改后刷新；
- 使用 `OfficerDataAPI` 创建军官数据、加入 roster、增加经验和升级；
- 使用 `PersonalityAPI` 读取 ID、显示名和描述，并把性格与舰队行为联系起来；
- 从舰队、舰船、军官和殖民地界面判断绑定是否成功。

## 前置知识

- [C13-S1] 的 `PersonAPI` 创建、人物资料和稳定 ID；
- [C11-S2] 的 `CampaignFleetAPI`、`FleetDataAPI` 和成员集合；
- [C11-S3] 的 `FleetMemberAPI`、旗舰和舰长读取；
- [C12-S1] 创建的 `GearIn_market_foundry`；
- 能够打开舰队、舰船和殖民地管理界面。

## 给舰队与市场准备角色入口

保存 `GearInCaptainRole.java`、`GearInFleetCommanderRole.java` 与 `GearInAdministratorRole.java` 到 `src/data/scripts/gearin/campaign/`。三个类分别负责舰长、舰队指挥官与管理员，完整代码在后文给出。

在 [C11-S1] 的 `GearInFleetGenerator` 添加前两个角色类及 `FleetMemberAPI`、`FleetMemberType` 的 import，在基础舰队投放后、成员集合与任务实验之前加入局部调用：

```java
new GearInFleetCommanderRole().run(generatedFleet);
FleetMemberAPI roleMember = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");
generatedFleet.getFleetData().addFleetMember(roleMember);
new GearInCaptainRole().run(roleMember);
```

Rowan 指挥这支巡逻队，新增的狼由 Mira 担任舰长，舰队 FP 与成员数也包含它。新档生成执行一次，人物与舰队一起留在生涯中。玩家旗舰的舰长来自玩家人物，这里使用专用巡逻队，才能明确观察自定义舰长。

管理员从已有 Foundry 市场接入，在 [C12-S1] 的经济更新结束回调添加：

```java
new GearInAdministratorRole().run();
```

`PersonAPI` 保存人物资料，成员、舰队与市场各自保存角色引用。写入后从这些持有对象回读，再确认数值更新。下面分别处理这几个实际动作。

## 把 Mira 分配给狼

`GearInCaptainRole.run(member)` 接收刚加入巡逻队的成员。读取已有舰长，ID 为 `GearIn_officer` 时复用，否则创建 Mira Vale，再写入 `setCaptain(...)`。局部绑定如下：

```java
PersonAPI captain = member.getCaptain();
if (!"GearIn_officer".equals(captain.getId())) {
    captain = createCaptain();
}
member.setCaptain(captain);
member.setStatUpdateNeeded(true);
member.updateStats();
LOG.info("captain=" + member.getCaptain().getId());
```

`setStatUpdateNeeded(true)` 标记重算，`updateStats()` 按当前舰长更新成员数值。[C11-S3] 已说明 `person.getStats()` 与 `member.getStats()` 的区别，个人技能通过这个成员上下文进入单舰。战斗部署再从成员创建本场 `ShipAPI`。

完整类还把 Mira 加入当前舰队的军官名册，已有 `getOfficerData(person)` 时沿用原数据。**舰长分配与军官名册分别写入。** 成员保存谁驾驶它，名册保存可分配的军官及成长状态；设置舰长与加入名册各完成一个关系。

## 让 Rowan 指挥整支巡逻队

指挥官的 ID 使用 `GearIn_commander`，姓名为 Rowan Hale。`setCommander(...)` 写入舰队，把人物 stats 关联到这支舰队并刷新效果，随后 `forceSync()` 更新成员与汇总。局部操作如下：

```java
PersonAPI commander = fleet.getCommander();
if (!"GearIn_commander".equals(commander.getId())) {
    commander = createCommander();
}
fleet.setCommander(commander);
fleet.forceSync();
LOG.info(String.format("commander=%s flagshipCommander=%s",
        fleet.getCommander().getId(),
        fleet.getFlagship().getFleetCommander().getId()));
```

`fleet.getCommander()` 返回 Rowan，成员的 `getFleetCommander()` 从所属舰队取得同一指挥官。Mira 继续担任狼的舰长，两个人物的作用范围可以同时存在。核对时先确认成员属于这支舰队，再比较两个指挥官 ID。

## 让 Selene 管理 Foundry

管理员入口按 `GearIn_market_foundry` 取得市场，从已有管理员复用或创建 Selene Ward，ID 使用 `GearIn_admin`。`setAdmin(...)` 替换管理员，也维护人物集合和市场反查；绑定后刷新治理效果，再重算产业：

```java
MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
PersonAPI admin = market.getAdmin();
if (!"GearIn_admin".equals(admin.getId())) {
    admin = createAdministrator();
}
market.setAdmin(admin);
admin.getStats().refreshGovernedOutpostEffects(market);
market.reapplyIndustries();
LOG.info(String.format("admin=%s market=%s",
        market.getAdmin().getId(), admin.getMarket().getId()));
```

市场产业从 `market.getAdmin().getStats()` 读取管理员动态数值。当前完成角色绑定和刷新，[C13-S3] 再添加技能。`getAdmin()` 与 `person.getMarket()` 分别确认正向角色与反向归属；替换之后从市场取得当前人物，再观察产业和殖民地界面。

### 角色归属速查表

| 角色     | 持有对象           | 写入与读取                                   | 作用范围                                      |
| -------- | ------------------ | -------------------------------------------- | --------------------------------------------- |
| 舰长     | `FleetMemberAPI`   | `setCaptain(person)` / `getCaptain()`        | 单舰 stats、舰船详情与战斗对象                |
| 指挥官   | `CampaignFleetAPI` | `setCommander(person)` / `getCommander()`    | 舰队效果，成员通过 `getFleetCommander()` 读取 |
| 管理员   | `MarketAPI`        | `setAdmin(person)` / `getAdmin()`            | 治理市场与产业，人物用 `getMarket()` 反查     |
| 军官数据 | `FleetDataAPI`     | `addOfficer(...)` / `getOfficerData(person)` | 军官名册与成长状态                            |

## 修改人物技能，再更新对应角色

三种角色都使用 `PersonAPI`，姓名、势力和性格保存在人物中，等级、经验与技能保存在 `MutableCharacterStatsAPI`。以下局部片段增加经验，设置原版舵手技能，再读取人物结果：

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

增加经验和选择技能分别处理，达到升级条件并没有替人物选完技能。`setSkillLevel(...)` 直接改变已注册技能的等级，`refreshCharacterStatsEffects()` 重放相应人物与舰队效果；技能文件和效果实现由下一节提供。

**人物等级已经写入，还要让实际角色取得更新结果。** 舰长技能通过成员重算进入单舰，指挥官效果依赖人物 stats 已关联舰队，再同步成员与汇总；管理员效果刷新传入治理市场，再让产业重算。三个角色使用各自的对象与更新入口。

### 人物 stats 读取与写入速查表

| 方法                                    | 当前职责                         |
| --------------------------------------- | -------------------------------- |
| `getLevel()` / `getXP()`                | 读取等级与累计经验               |
| `addXP(long)`                           | 增加人物经验，按成长规则处理等级 |
| `getSkillLevel(id)` / `hasSkill(id)`    | 读取技能等级并确认是否拥有技能   |
| `setSkillLevel(id, level)`              | 写入已注册技能的等级             |
| `refreshCharacterStatsEffects()`        | 刷新人物效果及已关联舰队效果     |
| `refreshGovernedOutpostEffects(market)` | 刷新指定治理市场的效果           |

## 在军官数据中处理成长

军官经验仍写入关联人物的 stats，技能候选与选择状态由 `OfficerDataAPI` 保存。工厂的 `createOfficerData(person)` 创建这份数据，加入名册后再用 `getOfficerData(person)` 取得，局部片段如下：

```java
FleetDataAPI fleetData = fleet.getFleetData();
OfficerDataAPI officerData = Global.getFactory().createOfficerData(person);
fleetData.addOfficer(officerData);
OfficerDataAPI stored = fleetData.getOfficerData(person);
stored.addXP(500L);
if (stored.canLevelUp() && !stored.isMadePicks()) {
    stored.makeSkillPicks();
}
```

这段用于尚未登记的军官数据，已有名册记录时继续取得原记录。`getPerson()` 返回关联人物，资料与性格也从它读取；`setPerson(...)` 替换这份数据的关联人物。

`addXP(...)` 达到军官升级条件时，会为尚未建立的选择自动生成候选。`canLevelUp()` 确认条件，`getSkillPicks()` 读取候选 ID，将选中的 ID 交给 `levelUp(skillId)` 后，人物等级和技能随之增加。完成后候选清空，仍满足下一级条件时再生成下一组。

::: warning 已生成的候选不要在刷新界面时重掷
`makeSkillPicks()` 再次调用会清空并重新生成候选。使用 `isMadePicks()` 判断当前状态，已建立时读取 `getSkillPicks()`，玩家的本轮选择才能保持一致。
:::

升级后从 `stored.getPerson()` 读取 `getSkillLevel(...)` 与 `hasSkill(...)`，再按前述角色更新数值。名册拥有这份成长数据，成员舰长字段仍单独维护。

## 设置性格并观察实际人物

三个人物都采用 `Personalities.STEADY`。`setPersonality(...)` 写性格 ID，`getPersonalityAPI()` 返回名称与说明，以下局部日志读取三项资料：

```java
person.setPersonality(Personalities.STEADY);
PersonalityAPI personality = person.getPersonalityAPI();
LOG.info(String.format("personality: id=%s name=%s description=%s",
        personality.getId(), personality.getDisplayName(),
        personality.getDescription()));
```

脚本比较稳定 ID，界面读取显示名与说明。舰长性格参与战斗 AI，指挥官性格也参与舰队决策；角色关系仍由成员、舰队和市场的引用决定。观察行为时使用相同的舰队组成、目标与战斗条件，并先确认当前角色引用了这位人物。

## 保留角色，并按原对象恢复

本节的三个入口从当前角色字段复用人物：Mira 从成员舰长读取，Rowan 从舰队指挥官读取，Selene 从市场管理员读取；军官数据则从名册查询。重复调用先取得这些对象，再决定是否创建，当前资料与成长状态就能保留。

跨读档继续使用稳定 ID，同一次调用中再比较传入与回读的引用。人物、成员和舰队 stats 各自属于当前对象，数值恰好相同也没有改变它们的作用范围。

可逆角色实验保存原人物与持有对象，结束时把舰长写回原成员、指挥官写回原舰队、管理员写回原市场，并完成对应更新。军官名册和舰长分配分别清理，管理员替换还可能移除旧人物的市场与目录关联，恢复时按实际关系处理。

技能异常也沿同一过程定位：人物是否拥有等级，角色是否引用这个人物，下游对象是否已经更新。下一节的技能实现继续使用本节建立的三个角色。

## 完整最小例

三个类采用上述调用入口。

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

    public void run(FleetMemberAPI member) {
        CampaignFleetAPI fleet = member.getFleetData().getFleet();
        PersonAPI person = member.getCaptain();
        if (!"GearIn_officer".equals(person.getId())) {
            person = createCaptain();
        }
        member.setCaptain(person);
        member.setStatUpdateNeeded(true);
        FleetDataAPI data = fleet.getFleetData();
        OfficerDataAPI officer = data.getOfficerData(person);
        if (officer == null) {
            data.addOfficer(person);
            officer = data.getOfficerData(person);
        }
        member.updateStats();
        LOG.info(String.format("GearIn captain: member=%s captain=%s officer=%s personality=%s",
                member.getId(), member.getCaptain().getId(), officer != null,
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

    public void run(CampaignFleetAPI fleet) {
        PersonAPI person = fleet.getCommander();
        if (!"GearIn_commander".equals(person.getId())) {
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
        if (!"GearIn_admin".equals(person.getId())) {
            person = createAdministrator();
        }
        market.setAdmin(person);
                person.getStats().refreshGovernedOutpostEffects(market);
        market.reapplyIndustries();
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

前置数据：[C11-S1] 的基础巡逻队生成入口及 [C12-S1] 的 Foundry 市场；三个角色调用按正文接入。

1. 构建重启并创建新档，等待巡逻队生成，搜索舰长日志，按成员 ID 定位加入的测试狼。
2. 在 `GearIn captain` 日志确认 `captain=GearIn_officer`、`officer=true`，再在战斗预览中核对测试狼的舰长；军官数据来自当前巡逻队的 `getOfficerData(person)`。
3. 前往巡逻队所在星系，在接触或战斗预览中查看人物，比较日志中的指挥官与当前成员的指挥官 ID。
4. 让巡逻队移动或进入战斗预览，核对成员仍读取 Rowan 的舰队上下文。
5. 等待经济更新结束后的管理员入口，搜索 `admin=GearIn_admin` 日志，打开 Foundry 的殖民地界面，确认管理员显示为 Selene Ward。
6. 读取管理员的 `getMarket()` 和市场的 `getAdmin()`，确认两者指向同一市场。
7. 查看三个入口输出的 `PersonalityAPI` 日志，核对 ID、显示名和描述来自 `steady`。
8. 在同一对象上重复调用角色入口，再保存读档；原巡逻队与成员保留，市场入口复用已绑定的 Selene。
9. 重新载入存档后读取舰长、指挥官、管理员和军官 roster，确认稳定 ID 仍可取得。

预期结果：

- 专用巡逻队测试狼的 `getCaptain()` 返回 `GearIn_officer`；
- `getOfficerData(person)` 返回关联的 `OfficerDataAPI`；
- `getCommander()` 返回 `GearIn_commander`；
- 旗舰的 `getFleetCommander()` 与舰队指挥官一致；
- `getAdmin()` 返回 `GearIn_admin`；
- `GearIn_admin.getMarket()` 返回 `GearIn_market_foundry`；
- 三个人物的性格 ID 均为 `steady`；
- 重复运行不会新增角色对象；
- 重新载入后角色资料和槽位归属仍可读取。

## 故障分析

- **成员舰长回读为玩家人物**：绑定目标是玩家当前旗舰。
    - **解决方案**：使用已加入专用巡逻队的测试成员，调用 `run(member)` 后从同一成员读取。

- **军官出现在 roster，但舰船没有舰长**：`OfficerDataAPI` 不负责写入成员舰长字段。
    - **解决方案**：保留 `addOfficer(...)`，再对目标成员调用 `setCaptain(person)`。

- **舰队指挥官日志正确，成员读取为空**：舰队尚未同步，或读取的是旧成员对象。
    - **解决方案**：调用 `forceSync()`，再从当前舰队取得成员并读取 `getFleetCommander()`。

- **管理员界面仍显示旧人物**：市场字段已被覆盖，代码继续读取旧引用。
    - **解决方案**：从 `market.getAdmin()` 与人物 `getMarket()` 两端回读，确认 `setAdmin(...)` 已执行。

- **管理员 stats 改变但市场数值不变**：管理员效果尚未刷新或市场尚未重算。
    - **解决方案**：刷新管理员 stats，再重新打开市场界面读取当前值。

- **性格显示为空或行为没有变化**：性格 ID 无效，或人物没有绑定到当前角色槽位。
    - **解决方案**：使用 `Personalities.STEADY` 等有效 ID，读取 `getPersonalityAPI()` 后检查角色字段。

## 总结

Mira、Rowan 与 Selene 分别写入成员、舰队与市场，角色关系从各自对象回读。人物 stats 保存经验与技能，名册中的军官数据管理升级候选，性格提供 AI 使用的资料。

人物状态沿角色进入实际对象，更新也在相应范围完成。保留当前人物、处理对应关系，再确认成员或市场结果，下一节的技能就有了明确的作用对象。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物资料、stats、性格和反向归属；
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：等级、经验、技能状态和刷新；
- `com.fs.starfarer.api.characters.OfficerDataAPI`：军官 roster 数据与升级；
- `com.fs.starfarer.api.characters.PersonalityAPI`：性格 ID、显示名和描述；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队指挥官和同步；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：舰长字段和成员 stats；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：管理员字段和市场读取。
