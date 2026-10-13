# 配置人物基础

Foundry 的通信目录已经能够处理人物，这次正式放入 Aster Vance。先给她稳定 ID 与资料，再登记到重要人物服务，加入市场和目录。首次创建、重复调用与读档都沿同一个 ID 找回这位人物，日志也分别确认资料、归属和界面入口。

*本节预期阅读时间：\~25 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 选择固定剧情人物的创建入口；
- 配置人物身份、标签和 Memory；
- 使用 `ImportantPeopleAPI` 登记、查询和借还人物；
- 区分市场成员、人物市场字段和通信目录；
- 让注册流程在重复调用和读档后复用对象。

## 前置知识

- [C7-S1] 的 `BaseModPlugin` 与 `onGameLoad`；
- [C7-S2] 的 `Global.getFactory()`；
- [C10-S1] 的星区服务与日志；
- [C12-S1] 创建的 `GearIn_market_foundry`。

## 把人物注册接到现有市场

创建 `src/data/scripts/gearin/campaign/GearInPersonScript.java`。把下面的局部调用加入 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()`，当前市场已建立，调用与其他观察顺序保留：

```java
new GearInPersonScript().run();
```

两个类在同一个包。每次进入存档后，经济观察器登记一轮监听，更新结束再执行当前人物注册；Aster 的对象与市场关系随存档保留。

### 选择固定人物的创建入口

`createPerson()` 返回空白人物，资料由调用方写入；`createRandomPerson()` 从势力资料池生成姓名和头像。固定剧情人物需要稳定身份，因此使用空白入口。两种入口都只创建对象，不会自动登记到重要人物、市场或通信目录。

```java
PersonAPI blank = Global.getFactory().createPerson();
PersonAPI random = Global.getSector().getFaction("gear_industry").createRandomPerson();
```

### 创建入口速查表

| 入口 | 资料来源 | 用途 |
|---|---|---|
| `createPerson()` | 调用方写入 | 固定剧情人物 |
| `createRandomPerson()` | 势力资料池 | 随机背景人物 |

## 配置人物身份

人物 ID 是跨脚本复用的稳定键，姓名只是显示资料。Aster Vance 使用 `GearIn_aster_vance`，女性，属于 `gear_industry`，rank 为 `Ranks.CITIZEN`，post 为 `Ranks.POST_TRADER`，头像为 `graphics/portraits/portrait_hegemony01.png`。

```java
person.setId(PERSON_ID);
person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
person.setFaction("gear_industry");
person.setRankId(Ranks.CITIZEN);
person.setPostId(Ranks.POST_TRADER);
person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
person.addTag(PERSON_TAG);
```

rank 表示身份层级，post 表示职务。读取稳定值使用 `getRankId()` 和 `getPostId()`；`getRank()` 和 `getPost()` 返回显示文本。标签是字符串集合，适合 MOD 分类，不能替代 ID。

### 人物资料速查表

| 资料 | 写入 | 读取 | 用途 |
|---|---|---|---|
| ID | `setId` | `getId` | 跨脚本定位 |
| 姓名 | `setName` | `getNameString` | 界面显示 |
| 性别 | `FullName.Gender` | `getGender` | 人物文本 |
| 势力 | `setFaction` | `getFaction` | 归属 |
| rank | `setRankId` | `getRankId` | 身份层级 |
| post | `setPostId` | `getPostId` | 职务 |
| 头像 | `setPortraitSprite` | `getPortraitSprite` | 人物图像 |
| 标签 | `addTag` | `hasTag` | 分类 |

`getRelToPlayer()` 只读取人物对玩家的关系，关系会参与对话显示；本节不修改关系。

## 读取人物相关 MemFlags

`MemFlags` 是原版约定键的集中定义，不是人物专用枚举。它同时包含舰队、市场、实体、全局和人物状态；使用时把键、实际写入对象、读取方与数据类型对应起来。人物直接使用的键如下。

### 人物相关 Memory 速查表

| 常量 | 字符串值 | 人物上的数据类型 | 具体语义 |
|---|---|---|---|
| `ENTITY_MISSION_IMPORTANT` | `$missionImportant` | reason 组合布尔标记 | 任务或其它流程把人物标为重要；通信目录和重要目标逻辑会读取它 |
| `MEMORY_KEY_MISSION_IMPORTANT` | `$missionImportant` | reason 组合布尔标记 | 与 `ENTITY_MISSION_IMPORTANT` 使用同一个原版键值，代码应通过 `Misc.makeImportant` 管理 |
| `EXCEPTIONAL_SLEEPER_POD_OFFICER` | `$exceptionalSleeperPodOfficer` | 布尔值 | 标记人物是特殊睡眠舱军官，军官技能变更逻辑据此处理 |
| `SUSPECTED_AI` | `$suspectedAI` | 布尔值 | 标记人物被判定为疑似 AI，原版生命周期逻辑会写入该状态 |
| `OFFICER_SKILL_PICKS_PER_LEVEL` | `$officerSkillPicksPerLevel` | 整数 | 覆盖军官每级获得的技能选择数量 |
| `OFFICER_MAX_LEVEL` | `$officerMaxLevel` | 整数 | 覆盖军官可达到的最高等级 |
| `OFFICER_MAX_ELITE_SKILLS` | `$officerMaxEliteSkills` | 整数 | 覆盖军官可拥有的精英技能数量上限 |

这些键的适用范围不同。`EXCEPTIONAL_SLEEPER_POD_OFFICER` 和 `SUSPECTED_AI` 属于人物身份或来源判定，三个 `OFFICER_` 键只在人物已经作为军官使用时才有意义。本节的 Aster Vance 只是普通重要人物，因此最终代码只写 MOD 自定义的 `$GearIn_aster_vance_ready`。

::: info $missionImportant
原版通过 reason 组合管理这个键，标记和解除使用 `Misc.makeImportant` 与 `Misc.makeUnimportant`，不能直接写 `false`，也不能用它替代 `ImportantPeopleAPI.addPerson`。
:::

每个人物都有自己的 `MemoryAPI`。注册流程使用 `getMemoryWithoutUpdate()` 写入 `$GearIn_aster_vance_ready`，表示基础资料已经完成。无期限值随人物保存，适合这个状态；带过期时间的值适合短期事件。

```java
person.getMemoryWithoutUpdate().set(PERSON_READY, true);
boolean ready = person.getMemoryWithoutUpdate().getBoolean(PERSON_READY);
```

标签表达分类，Memory 表达运行时状态。二者同时存在时，日志分别读取，避免把标签存在误认为注册完成。

## 登记重要人物

`Global.getSector().getImportantPeople()` 返回星区人物服务。先用 `getPerson(PERSON_ID)` 查询；已有对象直接复用，查询为空才创建。创建分支完成资料、标签和 Memory 后调用 `addPerson(person)`，这样返回对象已经可以按 ID 再次取得。

```java
ImportantPeopleAPI importantPeople = Global.getSector().getImportantPeople();
PersonAPI person = importantPeople.getPerson(PERSON_ID);
if (person != null) {
    return person;
}
person = Global.getFactory().createPerson();
// 写入资料与 Memory
importantPeople.addPerson(person);
return person;
```

`containsPerson(...)` 按人物 ID 检查登记，`getData(person).getPerson()` 再比较实际关联引用。数据还保存人物位置，本例在市场关联后写入 `getLocation().setMarket(market)`，使重要人物服务也按当前市场定位她。

需要按身份层级筛选时，`getPeopleWithRank(...)` 使用 rank 显示文本。按职务 ID 精确筛选，则使用 `getMatching(...)` 的人物过滤器：

```java
List<ImportantPeopleAPI.PersonDataAPI> traders = importantPeople.getMatching(
        data -> Ranks.POST_TRADER.equals(data.getPerson().getPostId()));
```

这段局部观察需要 `java.util.List`。固定人物仍按 ID 查询，集合筛选用于查看当前相同职务的其他人物。

### ImportantPeopleAPI 速查表

| 方法 | 作用 |
|---|---|
| `getPerson` | 按 ID 查询 |
| `addPerson` | 登记人物 |
| `containsPerson` | 检查登记 |
| `getData` | 检查附加数据 |
| `getPeopleWithRank` | 按 rank 显示文本筛选 |
| `getPeopleWithPost` | 现有筛选入口；本例职务筛选使用 `getMatching` 读取 post ID |
| `canCheckOutPerson` | 检查能否借出 |
| `checkOutPerson` | 写入借出 reason |
| `returnPerson` | 移除借出 reason |

### 借出与归还

借出只改变占用集合。借出和归还使用同一个 reason，`getCheckedOutFor()` 可以观察该 reason 是否存在。

```java
String reason = "GearIn_person_example";
if (importantPeople.canCheckOutPerson(person, reason)) {
    importantPeople.checkOutPerson(person, reason);
    Set<String> during = importantPeople.getData(person).getCheckedOutFor();
    importantPeople.returnPerson(person, reason);
}
```

## 关联市场和通信目录

`GearIn_market_foundry` 由 [C12-S1] 创建。`MarketAPI.addPerson(person)` 同时加入成员集合并设置人物的市场字段。单独调用 `PersonAPI.setMarket(market)` 只写反查字段；当前使用集合入口，然后从两端回读：

```java
MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
if (!market.getPeopleCopy().contains(person)) {
    market.addPerson(person);
}
```

市场成员不等于通信目录条目。目录是独立集合，先用 `getEntryForPerson(person)` 查询，缺失时才添加。

```java
CommDirectoryAPI directory = market.getCommDirectory();
int entriesBefore = directory.getEntriesCopy().size();
if (directory.getEntryForPerson(person) == null) {
    directory.addPerson(person);
}
int entriesAfter = directory.getEntriesCopy().size();
```

### 归属边界速查表

| 对象 | 写入 | 检查 | 结果 |
|---|---|---|---|
| 重要人物服务 | `addPerson` | `getPerson` | 可按 ID 查询 |
| 市场集合 | `MarketAPI.addPerson` | `getPeopleCopy` | 市场集合拥有成员 |
| 人物市场字段 | `MarketAPI.addPerson` 同步人物字段 | `getMarket` | 人物反查市场 |
| 通信目录 | `CommDirectoryAPI.addPerson` | `getEntryForPerson` | 界面出现条目 |

## 处理重复调用

注册入口可以在 `onGameLoad` 多次执行。稳定性来自星区保存的对象关系，而不是脚本实例或静态缓存。首次运行创建人物；再次调用时固定 ID 返回已有对象，当前资料与运行状态继续保留，代码补齐缺失市场和目录关联。重新载入后重新取得服务并按相同 ID 查询。

日志记录 `created`、`important`、`dataLinked`、`market`、`personMarket`、`directory` 和 `entriesBefore->entriesAfter`。这些字段分别对应创建、登记、附加数据、市场集合、人物字段、通信目录和目录数量；只记录姓名无法证明引用一致。

### 运行状态速查表

| 场景 | `created` | 人物 | 关联行为 |
|---|---|---|---|
| 首次运行 | `true` | 新建并登记 | 补齐市场和目录 |
| 同存档再次运行 | `false` | 复用已有对象 | 只补缺失关联 |
| 重新载入存档 | `false` | 从存档恢复 | 检查并补齐关联 |

## 组合注册流程

完整类只保留两个业务方法。`getOrCreatePerson()` 负责 ID 查询、创建、配置和登记；`run()` 负责市场双向关系、通信目录和日志。两个方法之间传递同一个 `PersonAPI`，因此所有集合检查都针对同一对象。

执行顺序是查询 ID、必要时创建和配置、登记重要人物、取得市场、补齐市场关系、补齐目录、记录状态。每一步都有对应读取入口，故障可以定位到具体对象边界。

## 完整最小例

保存下列类，调用入口保留在经济更新结束后的市场观察中。

::: details 完整成品：GearInPersonScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * 创建或读取 Aster Vance，并把人物加入 GearIn Foundry 的通信目录。
 * 固定人物 ID 与目录查询使重复调用保持同一个人物和目录条目。
 */
public class GearInPersonScript {

    public static final String PERSON_ID = "GearIn_aster_vance";

    private static final Logger LOG = Global.getLogger(GearInPersonScript.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private static final String PERSON_TAG = "GearIn_aster_vance";
    private static final String PERSON_READY = "$GearIn_aster_vance_ready";

    public void run() {
        ImportantPeopleAPI importantPeople = Global.getSector().getImportantPeople();
        boolean existed = importantPeople.getPerson(PERSON_ID) != null;
        PersonAPI person = getOrCreatePerson();

        MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
        if (!market.getPeopleCopy().contains(person)) {
            market.addPerson(person);
        }
        CommDirectoryAPI directory = market.getCommDirectory();
        int entriesBefore = directory.getEntriesCopy().size();
        if (directory.getEntryForPerson(person) == null) {
            directory.addPerson(person);
        }
        int entriesAfter = directory.getEntriesCopy().size();

        ImportantPeopleAPI.PersonDataAPI data = importantPeople.getData(person);
        data.getLocation().setMarket(market);
        LOG.info(String.format(
                "GearIn person: id=%s created=%s name=%s gender=%s faction=%s rank=%s post=%s "
                        + "tag=%s ready=%s important=%s dataLinked=%s market=%s personMarket=%s directory=%s entries=%d->%d",
                person.getId(), !existed, person.getNameString(), person.getGender(),
                person.getFaction().getId(), person.getRankId(), person.getPostId(),
                person.hasTag(PERSON_TAG), person.getMemoryWithoutUpdate().getBoolean(PERSON_READY),
                importantPeople.containsPerson(person), data.getPerson() == person,
                market.getPeopleCopy().contains(person), person.getMarket() == market,
                directory.getEntryForPerson(person) != null,
                entriesBefore, entriesAfter));
    }

    private PersonAPI getOrCreatePerson() {
        ImportantPeopleAPI importantPeople = Global.getSector().getImportantPeople();
        PersonAPI person = importantPeople.getPerson(PERSON_ID);
        if (person != null) {
            return person;
        }

        person = Global.getFactory().createPerson();
        person.setId(PERSON_ID);
        person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setRankId(Ranks.CITIZEN);
        person.setPostId(Ranks.POST_TRADER);
        person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
        person.addTag(PERSON_TAG);
        person.getMemoryWithoutUpdate().set(PERSON_READY, true);
        importantPeople.addPerson(person);
        return person;
    }
}
```

:::

## 验证方案

前置数据：测试 MOD 已包含 [C12-S1] 创建的市场，并准备可重新载入的存档。

1. 执行 `gradle jar`，确认 MOD 构建完成。
2. 载入存档并推进一轮经济更新，触发现有观察器中的 `run()`。
3. 在 `starsector.log` 查找 `GearIn person:`。
4. 打开 GearIn Foundry 通信目录，确认姓名、头像和职务。
5. 退出并重新载入同一存档，再推进经济更新，比较 `created` 与目录数量。
6. 保存并重新载入，再次检查固定 ID 和目录条目。

预期结果：

- 首次运行日志包含 `id=GearIn_aster_vance` 和 `created=true`；
- 人物姓名为 `Aster Vance`，性别为 `FEMALE`，势力为 `gear_industry`；
- rank ID 为 `citizen`，post ID 为 `trader`；
- `tag=true` 且 `ready=true`；
- `important=true` 且 `dataLinked=true`；
- `market=true` 且 `personMarket=true`；
- `directory=true`，通信目录能取得 Aster Vance；
- 同存档再次调用时 `created=false`，`entriesBefore` 与 `entriesAfter` 相等；
- 重新载入存档后固定 ID 仍返回 Aster Vance。

## 故障分析

- **每次调用都创建人物**：查询 ID 与写入 ID 不一致，或登记没有执行。
    - **解决方案**：统一使用 `PERSON_ID`，并在返回前调用 `importantPeople.addPerson(person)`。

- **市场存在但目录没有人物**：只维护了市场集合。
    - **解决方案**：查询 `getEntryForPerson(person)`，缺失时调用 `directory.addPerson(person)`。

- **市场集合中没有人物**：只写了人物反查字段。
    - **解决方案**：通过 `market.addPerson(person)` 加入集合，再从 `person.getMarket()` 与 `getPeopleCopy()` 两端读取。

- **目录出现重复条目**：无条件调用 `directory.addPerson`。
    - **解决方案**：添加前查询目标人物。

## 总结

Aster 的资料保存在人物上，重要人物服务按 ID 查找，市场集合与通信目录各自提供入口。注册从当前市场出发，重复调用继续使用原人物，缺失关系按同一对象补齐。

编者建议日志同时保留 ID、市场与目录数量。名字可以相同，只有这些关系对应起来，后面的任务与交互才能找到您准备的那位人物。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物资料、标签和 Memory；
- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：重要人物登记、查询和借还；
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：通信目录条目；
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：原版人物 Memory 键；
- `com.fs.starfarer.api.util.Misc`：按 reason 管理重要标志。
