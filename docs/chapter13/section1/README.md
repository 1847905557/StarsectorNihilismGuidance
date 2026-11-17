# 配置人物基础

想让一名 NPC 在存档里长期存续，单靠工厂造出 `PersonAPI` 还不够：必须把它放进 `ImportantPeopleAPI` 建立全局索引，再单独挂进市场名录与通信目录，否则代码里查得到人，玩家靠站却看不见。

这节用 Nipher Foundry 的联系人 Aster Vance 做个示范：按固定 ID 只建一次，再把她送进空间站的通信名录。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C12-S1] 的 Foundry 市场与通信目录，联系人将停驻在那里；
- [C7-S1] 的 `onNewGame()`，固定人物只在新档中创建。

## 固定 ID 与重要人物服务

所有需要跨章节引用的重要人物，都会保存在 `sector.getImportantPeople()` 返回的 `ImportantPeopleAPI` 中。创建固定 ID 人物的标准写法是 "先查后建"：

```java
ImportantPeopleAPI people = sector.getImportantPeople();
PersonAPI person = people.getPerson(PERSON_ID);
if (person != null) {
    return person;
}
person = Global.getFactory().createPerson();
person.setId(PERSON_ID);
// 写入资料
people.addPerson(person);
```

- **先设 ID 再登记**：`ImportantPeopleAPI` 在调用 `addPerson(person)` 时按 `person.getId()` 建立索引。如果先 `addPerson(...)` 再改 ID，索引表里的键值不会跟着更新；
- **重要度与独占借用**：除按 ID 查找外，`ImportantPeopleAPI` 还承载两项在任务与剧情系统里常用的能力 —— 一是用 `getPeopleWithPost(...)`、`getMatching(...)` 按职务或过滤条件批量检索人物，二是提供 `checkOutPerson(person, reason)` / `returnPerson(person, reason)` 借还锁，防止多个随机事件在同一时间把同一个联系人征用成失踪目标或双料委托人。

## 写入身份与交互字段

人物基础档案在 `PersonAPI` 上逐项填写：

```java
person.setId(PERSON_ID);
person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
person.setFaction(GearInModPlugin.FACTION_ID);
person.setRankId(Ranks.CITIZEN);
person.setPostId(Ranks.POST_TRADER);
person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
```

- `FullName(first, last, gender)` 决定姓名文本与代词性别，若需要单名角色可以把 `last` 设为 `""`；
- `setFaction(...)` 决定人员界面底色、阵营归属与敌对判断，对玩家关系可通过 `getRelToPlayer()` 读取或修改；
- `rankId` 是身份层级 (军衔或公民头衔)，`postId` 是岗位职务 (决定通信面板中的职业标签)，常用常量集中在 `Ranks` 中；
- 头像路径从 `graphics/` 起写。若是经常在酒吧或情报页露面的联系人，还可以通过 `setImportanceAndVoice(PersonImportance, random)` 设定人物分量与对话语气，用 `getMemoryWithoutUpdate()` 写入对话条件标志。

## 接到市场、通信目录与位置索引

身份资料写完并调用 `people.addPerson(person)` 之后，人物与空间站之间还有三处引用需要一并接通：

```java
people.addPerson(person);
market.addPerson(person);
market.getCommDirectory().addPerson(person);
people.getData(person).getLocation().setMarket(market);
```

- `market.addPerson(...)` 将人物加入该市场的人员集合，并设置人物记录上的所属市场；
- `market.getCommDirectory().addPerson(...)` 负责在停靠界面的通信目录里生成可点击条目；
- `people.getData(person).getLocation().setMarket(...)` 更新 `ImportantPeopleAPI` 内部 `PersonLocationAPI` 的位置标记，方便外部脚本在不遍历全部市场的情况下直接判断她现在待在哪座站 (同理也有 `setEntity(...)` 用于驻留在舰队或特定星图实体上的角色)。

::: warning 漏掉通信目录与调用顺序的两个陷阱

1. **只做 `market.addPerson(...)` 不加进 `CommDirectoryAPI`**：人物在数据上属于市场，玩家停靠空间站后却无法在通信列表里发起对话。管理员等幕后角色可以不进通信目录，但联系人必须加上这一步；
2. **先调 `people.getData(person)` 再 `people.addPerson(person)`**：人物尚未纳入服务时 `getData(person)` 返回 `null`，直接链式调用 `.getLocation()` 会立刻抛出空指针异常。

:::

## 在新档中创建

Foundry 市场建立之后，在 `GearInModPlugin.onNewGame()` 中调用联系人创建方法：

```java
new GearInFoundryGenerator().generate(sector);
GearInAsterContact.create(sector);
```

Foundry 市场尚未生成时，`sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID)` 返回 `null`，`create(...)` 直接放弃创建并返回 `null`，因此两者顺序不可颠倒。

## 完整的联系人类

`src/data/scripts/gearin/campaign/GearInAsterContact.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** Foundry 的联系人 Aster Vance：按固定 ID 创建一次，登记到重要人物服务、市场与通信目录。 */
public final class GearInAsterContact {

    public static final String PERSON_ID = "GearIn_aster_vance";

    private GearInAsterContact() {
    }

    /** 已存在则直接返回；Foundry 尚未建立时返回 null。 */
    public static PersonAPI create(SectorAPI sector) {
        ImportantPeopleAPI people = sector.getImportantPeople();
        PersonAPI person = people.getPerson(PERSON_ID);
        if (person != null) {
            return person;
        }
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return null;
        }

        person = Global.getFactory().createPerson();
        person.setId(PERSON_ID);
        person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
        person.setFaction(GearInModPlugin.FACTION_ID);
        person.setRankId(Ranks.CITIZEN);
        person.setPostId(Ranks.POST_TRADER);
        person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");

        people.addPerson(person);
        market.addPerson(person);
        market.getCommDirectory().addPerson(person);
        people.getData(person).getLocation().setMarket(market);
        return person;
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry：

- 通信目录中出现 Aster Vance，职务显示为商人，立绘加载正常；
- 控制台执行 `runcode Global.getSector().getImportantPeople().getPerson("GearIn_aster_vance")` 返回非空，且 `getData("GearIn_aster_vance").getLocation().getMarket().getId()` 为 `"GearIn_market_foundry"`；
- 保存并重新读档，通信目录中仍然只有一个 Aster。

## 常见问题

- **通信目录里没有 Aster**：只调用了 `market.addPerson(...)`，没有登记目录。
    - **解决方案**：补全 `market.getCommDirectory().addPerson(person)`。

- **按 ID 查不到人物**：没有调用 `people.addPerson(...)`，或者 ID 是在登记之后才设置的。
    - **解决方案**：务必先 `person.setId(...)`，再 `people.addPerson(person)`。

- **读档后出现两个 Aster**：创建代码放进了 `onGameLoad(...)` 这类每次读档都会执行的回调，又缺少 `getPerson(PERSON_ID)` 的幂等检查。
    - **解决方案**：放在 `onNewGame()`，并保留先查后建判断。

- **`create(...)` 返回 `null`**：Foundry 市场尚未生成，或者 `getData(person)` 被放在了 `addPerson(person)` 之前触发了空指针。
    - **解决方案**：确保 `GearInFoundryGenerator` 先执行，且 `addPerson` 先于 `getData`。

- **旧存档中没有 Aster**：固定联系人仅在新档初始化时生成。
    - **解决方案**：使用新档验证，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：固定人物注册、过滤检索、位置追踪与 `checkOutPerson` 借用管理。
- `com.fs.starfarer.api.characters.PersonAPI`：身份资料、关系、`PersonImportance` 与个人 Memory 读写。
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：殖民地通信目录条目的增删、排序与可见性。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：身份层级与岗位职务常量表。
