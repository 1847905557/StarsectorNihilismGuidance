# 配置人物基础

Aster Vance 是齿轮工业在 Foundry 的联系人，往后的对话和事件链都要从她这里开始。

给一个人物定下固定的身份，听上去是全书最无聊的工作之一。可之后几章挂在她身上的东西，全都靠这个身份才找得到她。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C12-S1] 的 Foundry 市场与通信目录，Aster 在那里出现；
- [C7-S1] 的 `onNewGame()`，联系人只在新档中创建。

## 固定 ID 与重要人物服务

Aster Vance 是 Foundry 的联系人，ID 为 `GearIn_aster_vance`。后续章节的对话、技能与事件链都按这个 ID 找她。

人物用 `Global.getFactory().createPerson()` 创建，资料由调用方逐项填写。创建之后交给重要人物服务登记，此后才能按 ID 取回同一个对象：

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

**先查后建。** 已有同 ID 的人物就直接返回，不再创建。这一条在以后所有需要固定身份的对象上都适用，包括实体、市场与任务。

## 写入身份

姓名、势力、身份层级、职务与头像逐项写入：

```java
person.setId(PERSON_ID);
person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
person.setFaction(GearInModPlugin.FACTION_ID);
person.setRankId(Ranks.CITIZEN);
person.setPostId(Ranks.POST_TRADER);
person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
```

`rank` 是身份层级，`post` 是职务，两者取自 `Ranks` 的常量。头像借用原版资源，路径从 `graphics/` 起写。**固定 ID 必须在登记之前设好**，登记之后再改，服务里的索引不会跟着变。

Aster 的职务是商人。往后的对话与事件链都从她开始，这样的人物用不着军衔，只需要玩家每次找到的都是同一个她。头像借用原版霸主的一张，没有另画。

## 接到市场与通信目录

人物登记之后，还有三处连接要接：

```java
people.addPerson(person);
market.addPerson(person);
market.getCommDirectory().addPerson(person);
people.getData(person).getLocation().setMarket(market);
```

- `market.addPerson(...)` 把人物加入市场的人物集合，并设置人物所属的市场；
- `getCommDirectory().addPerson(...)` 才建立通信条目，玩家在通信界面里看到的就是它；
- `getLocation().setMarket(...)` 告诉重要人物服务她在哪里，后续脚本按位置查找时用得到。

**只做 `market.addPerson`，人物属于市场，却不会出现在通信目录里。** 漏掉目录登记，玩家就找不到这个人。

Foundry 是 [C12-S1] 建立的 `GearIn_market_foundry`，市场取不到时，`create(...)` 返回 `null`，不创建人物。

## 在新档中创建

在 `GearInModPlugin.onNewGame()` 中，Foundry 生成之后调用：

```java
new GearInFoundryGenerator().generate(sector);
GearInAsterContact.create(sector);
```

顺序不能颠倒：Foundry 先存在，联系人才有地方可去。

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

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry。

- 通信目录中出现 Aster Vance，职务为商人，头像为所借用的原版头像；
- 控制台执行 `runcode Global.getSector().getImportantPeople().getPerson("GearIn_aster_vance")`，返回非空；
- 保存并重新读档，通信目录中仍然只有一个 Aster。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **通信目录里没有 Aster**：只调用了 `market.addPerson(...)`，没有登记目录。
    - **解决方案**：再调用 `getCommDirectory().addPerson(...)`。

- **按 ID 查不到人物**：没有调用 `people.addPerson(...)`，或 ID 在登记之后才设置。
    - **解决方案**：先 `setId(...)`，再登记。

- **读档后出现两个 Aster**：创建放在了每次读档都会执行的位置，且没有先查后建。
    - **解决方案**：放在 `onNewGame()`，并保留 `getPerson(PERSON_ID)` 的存在性检查。

- **`create(...)` 返回 `null`**：Foundry 尚未生成。
    - **解决方案**：调整调用顺序，使 Foundry 生成器先于联系人执行。

- **旧存档中没有 Aster**：联系人只在新档中创建。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：登记、查询、位置与占用的方法。
- `com.fs.starfarer.api.characters.PersonAPI`：身份资料与 Memory 的读写。
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：通信目录条目的增删与查询。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：身份层级与职务常量。
