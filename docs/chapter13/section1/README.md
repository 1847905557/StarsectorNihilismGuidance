# 配置人物基础

本节让 Aster Vance 留在 Foundry 的通信目录中，供后续脚本按固定身份找到她。姓名、头像与职务组成界面资料，重要人物服务保存可查询的对象，市场和目录提供实际接触入口。创建、登记与关联分别完成，读档后才能继续使用同一位人物。

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

两类在同一个包。每次载入存档，经济观察器等待一轮更新后调用人物流程，当前市场已经建立。Aster 与关联关系会随存档保存，这个入口因此既要处理首次创建，也要处理已有的人物。

### 选择固定人物的创建入口

固定人物采用 `createPerson()`，由调用方填写资料。需要随机背景人物时，`createRandomPerson()` 从势力资料池生成姓名与头像。以下局部片段对照两个入口：

```java
PersonAPI blank = Global.getFactory().createPerson();
PersonAPI random = Global.getSector().getFaction("gear_industry").createRandomPerson();
```

两者得到的是人物对象，重要人物登记与市场关联由调用方继续完成。本节把创建放在固定 ID 查询之后，首次运行才建立 Aster。

## 先按 ID 取得人物，再配置首次资料

从 `Global.getSector().getImportantPeople()` 取得重要人物服务，查询 `GearIn_aster_vance`。已有对象直接返回，只有首次调用才创建并登记。`getOrCreatePerson()` 的局部结构如下：

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

这样，已有对话进度和人物状态继续留在原对象上，读取存档不会再用一份新建资料覆盖它。完整类在创建分支写完身份、标签与 Memory，最后调用 `addPerson(...)`。

### 写入 Aster 的身份

人物 ID 先设为 `GearIn_aster_vance`，然后填写 Aster Vance、女性、齿轮工业公民与商人职务。头像采用 `graphics/portraits/portrait_hegemony01.png`，标签使用同一前缀。局部配置如下：

```java
person.setId(PERSON_ID);
person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
person.setFaction("gear_industry");
person.setRankId(Ranks.CITIZEN);
person.setPostId(Ranks.POST_TRADER);
person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
person.addTag(PERSON_TAG);
```

**固定 ID 在登记前确定，后续脚本继续使用它。** 重要人物服务按 ID 建立查询，姓名与头像则供玩家辨认。`rank` 表示身份层级，`post` 表示职务；`getRankId()`、`getPostId()` 返回稳定值，`getRank()`、`getPost()` 按人物势力取得显示文本。

标签是分类集合，供 MOD 查询使用。完整类还写入 `$GearIn_aster_vance_ready`，标记这份基础资料已经完成；状态含义与人物 ID 分别保存。

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

`getRelToPlayer()` 返回人物与玩家的关系对象，参与对话等显示。本节读取身份与归属，关系沿用已有状态。

## 关联市场和通信目录

人物登记后，`run()` 将她关联到 [C12-S1] 的 `GearIn_market_foundry`。`market.addPerson(person)` 同时加入人物集合并设置人物的市场字段，局部操作如下：

```java
MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
if (!market.getPeopleCopy().contains(person)) {
    market.addPerson(person);
}
```

单独调用 `person.setMarket(market)` 只保存人物的反查字段。这里从集合入口加入，随后可以从 `getPeopleCopy()` 与 `person.getMarket()` 两端确认归属。

再将人物放进通信目录。目录另有自己的条目，按人物查询后添加：

```java
CommDirectoryAPI directory = market.getCommDirectory();
int entriesBefore = directory.getEntriesCopy().size();
if (directory.getEntryForPerson(person) == null) {
    directory.addPerson(person);
}
int entriesAfter = directory.getEntriesCopy().size();
```

`ImportantPeopleAPI` 的附加数据也保存位置，完整类调用 `getData(person).getLocation().setMarket(market)`。这样后续人物服务既能按 ID 找到她，也能知道她位于 Foundry。目录出现人物，说明界面入口已接好；市场集合、人物字段和服务位置仍各有职责。

### 人物关联速查表

| 对象 | 写入 | 检查 | 结果 |
|---|---|---|---|
| 重要人物服务 | `addPerson` | `getPerson` | 可按 ID 查询 |
| 市场集合 | `MarketAPI.addPerson` | `getPeopleCopy` | 市场集合拥有成员 |
| 人物市场字段 | `MarketAPI.addPerson` 同步人物字段 | `getMarket` | 人物反查市场 |
| 通信目录 | `CommDirectoryAPI.addPerson` | `getEntryForPerson` | 界面出现条目 |

## 让重复调用继续使用原人物

`getOrCreatePerson()` 处理固定 ID 查询与首次登记，`run()` 处理市场、目录、服务位置和日志，两者传递同一个人物。首次运行时 `created=true`；同存档再次调用或读档后，人物服务返回已有对象，`created=false`，入口只补齐缺失的关联。

`containsPerson(person)` 按人物 ID 检查登记，`getData(person).getPerson() == person` 则确认服务保存的具体对象。因此日志分别记录 `important` 与 `dataLinked`，再记录 `market`、`personMarket`、`directory` 和目录数量前后值。姓名相同的两个对象，仍可能在这些关系中得到不同结果。

本例的 `important=true` 表示已登记到重要人物服务。任务中的重要标志另由 Memory 管理，二者的作用在下面区分。跨读档继续按 ID 查询，当前一次调用中再用对象引用核对关联。

## 用 Memory 保存人物状态

人物有自己的 `MemoryAPI`。当前 `$GearIn_aster_vance_ready` 使用无期限值，表示首次资料已经写完，随人物保存。局部读写如下：

```java
person.getMemoryWithoutUpdate().set(PERSON_READY, true);
boolean ready = person.getMemoryWithoutUpdate().getBoolean(PERSON_READY);
```

标签表达分类，Memory 表达当前运行状态；带期限值适合短期事件。本例的最终代码只写自己的资料标记，原版 `MemFlags` 还供任务与军官功能使用，按实际读取方选择。

### 人物相关 Memory 速查表

| 常量 | 字符串值 | 人物上的数据类型 | 具体语义 |
|---|---|---|---|
| `ENTITY_MISSION_IMPORTANT` | `$missionImportant` | reason 组合布尔标记 | 任务等流程标为重要，通信目录与重要目标逻辑读取 |
| `MEMORY_KEY_MISSION_IMPORTANT` | `$missionImportant` | reason 组合布尔标记 | 与前一常量共用键，通过 `Misc.makeImportant` 管理 |
| `EXCEPTIONAL_SLEEPER_POD_OFFICER` | `$exceptionalSleeperPodOfficer` | 布尔值 | 特殊睡眠舱军官身份，技能变更逻辑据此处理 |
| `SUSPECTED_AI` | `$suspectedAI` | 布尔值 | 疑似 AI 身份，原版生命周期逻辑会写入 |
| `OFFICER_SKILL_PICKS_PER_LEVEL` | `$officerSkillPicksPerLevel` | 整数 | 军官每级的技能候选数量 |
| `OFFICER_MAX_LEVEL` | `$officerMaxLevel` | 整数 | 军官最高等级 |
| `OFFICER_MAX_ELITE_SKILLS` | `$officerMaxEliteSkills` | 整数 | 军官精英技能上限 |

三个 `OFFICER_` 键由军官成长功能读取，Aster 当前只是普通剧情人物。`MemFlags` 也集中定义市场、舰队与全局键，使用时仍要对应实际对象和类型。

::: warning 任务重要标志按 reason 管理
`$missionImportant` 由多个 reason 共同决定。使用 `Misc.makeImportant(...)` 标记，`Misc.makeUnimportant(...)` 解除自己的原因，才能保留其他任务的标记。人物服务登记继续调用 `ImportantPeopleAPI.addPerson(...)`。
:::

## 筛选人物与登记占用

固定人物按 ID 查询。需要取得同类人物时，使用 `getMatching(...)` 按人物字段筛选。下面的局部片段按 `Ranks.POST_TRADER` 读取商人，需要 `java.util.List`：

```java
List<ImportantPeopleAPI.PersonDataAPI> traders = importantPeople.getMatching(
        data -> Ranks.POST_TRADER.equals(data.getPerson().getPostId()));
```

`getPeopleWithRank(...)` 使用 rank 显示文本。当前 `getPeopleWithPost(...)` 也不能据传入 post ID 完成这里的职务筛选，因此本节明确比较 `getPostId()`，数据 ID 与界面文本分别使用。

任务还可以登记人物占用。`checkOutPerson(person, reason)` 将原因加入附加数据的集合，`returnPerson(...)` 移除同一原因，市场与目录关系继续保留。以下局部片段登记后立即归还，需要 `java.util.Set`：

```java
String reason = "GearIn_person_example";
if (importantPeople.canCheckOutPerson(person, reason)) {
    importantPeople.checkOutPerson(person, reason);
    Set<String> during = importantPeople.getData(person).getCheckedOutFor();
    importantPeople.returnPerson(person, reason);
}
```

::: warning canCheckOutPerson 检查当前 reason
返回 `true` 表示当前原因尚未登记，人物仍可能被其他原因占用。需要独占使用时，再结合 `isCheckedOutForAnything()` 或 `getCheckedOutFor()` 确认全部占用记录。
:::

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

- **目录出现同名人物的多个条目**：反复创建了不同人物对象，再分别加入目录。
    - **解决方案**：按固定 ID 从人物服务复用对象，添加目录前用 `getEntryForPerson(person)` 查询当前条目。

## 总结

Aster 的首次资料保存在人物对象中，重要人物服务按固定 ID 查找，市场集合与通信目录让她出现在 Foundry。重复调用取得原人物并补齐关联，读档后也沿同一身份继续使用。

人物状态、任务重要标志与占用原因分别管理。后续任务可以找到这位商人，也能判断当前关系与占用；姓名和头像完成了界面资料，固定身份与实际关联完成了接入。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物资料、标签和 Memory；
- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：重要人物登记、查询和借还；
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：通信目录条目；
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：原版人物 Memory 键；
- `com.fs.starfarer.api.util.Misc`：按 reason 管理重要标志。
