# 创建生涯市场

齿轮工业的首都在 Nipher Prime，Foundry 则只是绕着恒星转的一座小空间站。它的配置刚够让人停靠和买卖，连港务官都只是个临时顶上的名字。

后面几节会在这座站上堆满齿轮工业自己的东西，所以它现在越简陋越好。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S3] 的 Nipher 星系生成器，Nipher Prime 的市场在那里创建；
- [C7-S1] 的 `onNewGame()`，Foundry 只在新档中生成；
- [C10-S2] 的自定义实体与轨道，空间站的放置方式与遗迹相同。

## 空间站是自定义实体

Foundry 是绕 Nipher 恒星运行的空间站，ID 为 `GearIn_foundry`。空间站不是行星，用 `addCustomEntity(...)` 创建，类型取原版 `custom_entities.json` 中的 `station_side00`：

```java
SectorEntityToken station = system.addCustomEntity(
        STATION_ID, "Nipher Foundry", "station_side00", GearInModPlugin.FACTION_ID);
station.setCircularOrbitPointingDown(system.getStar(), 200f, 4200f, 150f);
```

`system` 取自 `sector.getStarSystem("Nipher")`，轨道焦点是该星系的恒星。轨道半径 4200 位于 Nipher Prime 的 3200 与 Nipher Second 的 5200 之间，不与它们重叠。

## 创建市场并接好三处连接

市场是独立的对象，创建之后还没有任何东西指向它：

```java
MarketAPI market = Global.getFactory().createMarket(MARKET_ID, "Nipher Foundry", 3);
market.setFactionId(GearInModPlugin.FACTION_ID);
market.setPrimaryEntity(station);
sector.getEconomy().addMarket(market, true);
station.setMarket(market);
```

`createMarket(id, name, size)` 的三个参数是市场 ID、名称与规模，规模 3 是一座小型站点。ID 为 `"GearIn_market_foundry"`，[C13-S1] 与 [C13-S2] 按这个 ID 查找市场，改名要一并改。

**市场与外界有三处连接，缺一不可。**

- `setPrimaryEntity(station)`：市场取得主实体，也就取得了所在地点。没有它，经济系统不知道这个市场在哪；
- `addMarket(market, true)`：加入经济系统。第二个参数为 `true` 时，同时为市场添加环境杂物与通信背景；
- `station.setMarket(market)`：让实体找到市场。没有它，玩家靠近空间站，打不开交易界面。

::: warning 经济里有市场，不等于玩家能进去

只做前两步，`economy.getMarket(MARKET_ID)` 能取到市场，空间站在星图上也看得见，但点击它没有任何交易选项。症状是 "星图上有，进不去"，对应的就是漏了第三步。

:::

## 条件、产业与子市场

```java
market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
market.addCondition("population_3");
market.addIndustry("population");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("black_market");
market.addSubmarket("storage");
```

条件提供市场效果，`population_3` 与规模 3 对应；产业参与人口与基础设施，`population` 与 `spaceport` 是一座能停靠的站点的最低配置；子市场是玩家实际看到的交易与存放入口，公开市场、黑市与仓储。

`setSurveyLevel(FULL)` 让玩家不必先勘测就能看到这个市场的全部信息。条件 ID 取自 `Conditions` 中的常量，子市场 ID 是原版已登记的类型。本节全部用原版内容，[C12-S3]、[C12-S4] 与 [C12-S6] 再分别换成自己的。

## 让人物出现在通信目录

市场人物与通信目录分开维护。`market.addPerson(person)` 把人物加入市场并设置所属市场，`market.getCommDirectory().addPerson(person)` 才建立通信条目：

```java
PersonAPI officer = Global.getFactory().createPerson();
officer.setFaction(GearInModPlugin.FACTION_ID);
officer.setName(new FullName("Doran", "Pike", FullName.Gender.MALE));
officer.setRankId(Ranks.SPACE_COMMANDER);
officer.setPostId(Ranks.POST_OFFICER);
market.addPerson(officer);
market.getCommDirectory().addPerson(officer);
```

**只做 `addPerson`，人物属于市场，却不会出现在通信目录里。** 漏掉目录登记，玩家在通信界面里就找不到这个人。

这位港务官只是占位的人物。[C13-S1] 会按固定 ID 建立 Aster Vance，并让她出现在 Foundry 的通信目录中。

## 完整的 Foundry 生成器

`src/data/scripts/gearin/campaign/GearInFoundryGenerator.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 在 Nipher 星系中建立 Foundry 空间站及其市场，并让一名港务官出现在通信目录。 */
public class GearInFoundryGenerator {

    public static final String STATION_ID = "GearIn_foundry";
    public static final String MARKET_ID = "GearIn_market_foundry";

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.getStarSystem("Nipher");
        if (system == null || sector.getEconomy().getMarket(MARKET_ID) != null) {
            return;
        }

        // 空间站是自定义实体，不是行星；轨道焦点为 Nipher 的恒星
        SectorEntityToken station = system.addCustomEntity(
                STATION_ID, "Nipher Foundry", "station_side00", GearInModPlugin.FACTION_ID);
        station.setCircularOrbitPointingDown(system.getStar(), 200f, 4200f, 150f);

        // 市场：创建、设置势力与主实体、加入经济，三步缺一不可
        MarketAPI market = Global.getFactory().createMarket(MARKET_ID, "Nipher Foundry", 3);
        market.setFactionId(GearInModPlugin.FACTION_ID);
        market.setPrimaryEntity(station);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_3");
        market.addCondition("GearIn_warfront");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addIndustry("GearIn_element_works");
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.addSubmarket("storage");
        market.addSubmarket("GearIn_alloy_depot");
        sector.getEconomy().addMarket(market, true);
        station.setMarket(market);

        // 通信目录：先加入市场人物，再登记目录条目
        PersonAPI officer = Global.getFactory().createPerson();
        officer.setFaction(GearInModPlugin.FACTION_ID);
        officer.setName(new FullName("Doran", "Pike", FullName.Gender.MALE));
        officer.setRankId(Ranks.SPACE_COMMANDER);
        officer.setPostId(Ranks.POST_OFFICER);
        market.addPerson(officer);
        market.getCommDirectory().addPerson(officer);
    }
}
```

在 `GearInModPlugin.onNewGame()` 中，Nipher 生成之后调用：

```java
new GearInSystemGenerator().generate(sector);
new GearInFoundryGenerator().generate(sector);
new GearInAshenVergeGenerator().generate(sector);
seedRelations(sector);
```

生成器开头检查星系是否存在、市场是否已创建，保证重复调用时不会建出第二个 Foundry。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，用控制台命令 `jump Nipher` 进入星系。

- 星图上 Nipher 星系内出现 Nipher Foundry 空间站，名称与归属颜色与配置一致；
- 靠近并选中空间站，可以进入交易界面，看到公开市场、黑市与仓储；
- 打开通信目录，Doran Pike 在列；
- 保存并重新读档，市场与人物没有重复。

页面中的现象依据 API 与原版数据推出，编者尚未逐项在游戏内实测。

## 常见问题

- **星图上有空间站，点击后没有交易选项**：没有调用 `station.setMarket(market)`。
    - **解决方案**：让实体指向市场，三处连接都要接好。

- **经济查询取不到市场**：没有调用 `addMarket(...)`，或者市场 ID 与查询的不一致。
    - **解决方案**：核对 `MARKET_ID`，确认加入了经济系统。

- **通信目录里没有人物**：只调用了 `market.addPerson(...)`。
    - **解决方案**：再调用 `getCommDirectory().addPerson(...)`。

- **读档后出现第二个 Foundry**：生成放在了每次读档都会执行的位置。
    - **解决方案**：放在 `onNewGame()`，并保留生成器开头的存在性检查。

- **旧存档中没有 Foundry**：生成放在 `onNewGame()`，旧存档不会再触发。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：条件、产业、子市场与人物的全部方法。
- `com.fs.starfarer.api.campaign.econ.EconomyAPI`：`addMarket(...)` 与市场查询。
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：通信目录条目的增删与查询。
- `com.fs.starfarer.api.impl.campaign.ids.Conditions`：市场条件 ID 常量。
- `data/config/custom_entities.json`：`station_side00` 等空间站实体的定义。
