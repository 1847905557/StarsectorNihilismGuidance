# 创建生涯市场

在 Starsector 里，空间站实体和经济市场是两套分开维护的对象 —— 哪怕把空间站放进了星系，没有接上 `MarketAPI` 和几条关键引用，玩家飞过去也只能看见一个不能停靠、没有交易栏的空壳。

只要先把实体和 `EconomyAPI` 的关联接通，再配齐原版的人口、太空港与基础子市场，就能在 Nipher 星系支起一座能运转的小型空间站 `Nipher Foundry`；剩下的自制产业与特产，可以留到后面一节节往上装。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S3] 的 Nipher 星系生成器，Nipher Prime 的市场在那里创建；
- [C7-S1] 的 `onNewGame()`，Foundry 只在新档中生成；
- [C10-S3] 的自定义实体与轨道，空间站的放置方式与遗迹相同。

## 把空间站实体放进 Nipher 星系

Foundry 是绕着 Nipher 恒星公转的工业空间站，实体 ID 为 `GearIn_foundry`。空间站与行星不同，不在 `addPlanet(...)` 这条线上创建，而是调用 `system.addCustomEntity(...)`，类型取原版 `data/config/custom_entities.json` 中已定义好的 `"station_side00"`：

```java
SectorEntityToken station = system.addCustomEntity(
        STATION_ID, "Nipher Foundry", "station_side00", GearInModPlugin.FACTION_ID);
station.setCircularOrbitPointingDown(system.getStar(), 200f, 4200f, 150f);
```

`system` 通过 `sector.getStarSystem("Nipher")` 取得，轨道焦点取该星系的恒星 `system.getStar()`。Nipher Prime 位于轨道半径 `3200`，Nipher Second 位于 `5200`，把 Foundry 放在 `4200` 刚好卡在两颗行星轨道之间，星图上不会重叠。

## 创建市场与三处必填连接

`MarketAPI` 本身是一个独立对象，刚造出来的时候既不在经济系统里，也没有跟空间站实体挂上钩：

```java
MarketAPI market = Global.getFactory().createMarket(MARKET_ID, "Nipher Foundry", 3);
market.setFactionId(GearInModPlugin.FACTION_ID);
market.setPrimaryEntity(station);
sector.getEconomy().addMarket(market, true);
station.setMarket(market);
```

`createMarket(id, name, size)` 依次传入市场 ID、显示名称和初始规模 (`3` 是一座小型空间站)。此处把市场 ID 定为 `"GearIn_market_foundry"`，后面的 [C13-S1]、[C13-S2]、[C14-S1] 与 [C16-S3] 都靠这个常量来找这座市场。

**市场和世界之间必须配齐三处引用：**

- `market.setPrimaryEntity(station)`：告诉市场它附着在哪个星图实体上，经济系统靠这根引用定位它属于哪个星系与轨道坐标；
- `sector.getEconomy().addMarket(market, true)`：把市场正式注册进星区经济调度；第二个参数传 `true` 时，游戏会一并生成太空站周边的轨道漂浮杂物并挂上无线电杂波 (`radio chatter`) 地形；
- `station.setMarket(market)`：反向把市场挂回实体上。

::: warning 漏掉 station.setMarket(market) 不会报错

前两步做完时，`sector.getEconomy().getMarket(MARKET_ID)` 已经能查出这份市场，空间站也好好地显示在星图上；但玩家飞过去点击空间站时，交互菜单里根本不会出现任何买卖或停靠选项。只有三处引用全部接通，这座站才算真正能进能买。

:::

## 挂载基础条件、产业与子市场

为了让站点一开始就能停靠、计入经济并打开常规交易，我们先借用原版已有的条件、产业和子市场打底：

```java
market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
market.addCondition("population_3");
market.addIndustry("population");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("black_market");
market.addSubmarket("storage");
```

- `market.getTariff().modifyFlat("default_tariff", ...)`：`createMarket(...)` 会把关税初始化为 `0`，需要显式写入所属势力的默认税率 (`30%`)，开放市场才会按常规标准收税；
- `setSurveyLevel(SurveyLevel.FULL)`：已殖民或玩家熟悉的阵营站点通常设为完全勘测，免得玩家靠近自己势力的空间站还要再扫一次；
- `population_3`：与规模 `3` 对应的人口条件；
- `population` (居住区 & 基础设施) 与 `spaceport` (太空港)：维持殖民地运转与星际贸易通航的最低产业配置；
- `open_market` (开放市场)、`black_market` (黑市) 与 `storage` (仓库)：提供基本交易、地下交易与存放入口。

在本节我们先只写入这些原版条目，保证生成器立即可运行；后续做好专用的 [C12-S3] 合金仓库子市场、[C12-S4] Element Works 产业与 [C12-S6] Warfront 条件后，再按节逐项补进 `GearInFoundryGenerator`。

## 把临时港务官放进通信目录

殖民地人物名单 (`market.addPerson(...)`) 与右下角停靠界面显示的通信目录 (`market.getCommDirectory()`) 是分开存储的：

```java
PersonAPI officer = Global.getFactory().createPerson();
officer.setFaction(GearInModPlugin.FACTION_ID);
officer.setName(new FullName("Doran", "Pike", FullName.Gender.MALE));
officer.setRankId(Ranks.SPACE_COMMANDER);
officer.setPostId(Ranks.POST_OFFICER);
market.addPerson(officer);
market.getCommDirectory().addPerson(officer);
```

只调用 `market.addPerson(officer)` 时，这名港务官在数据上已经归属 Foundry 市场，但玩家停靠后打开通信目录却看不到他的名字；必须补上 `market.getCommDirectory().addPerson(officer)`，人物才会出现在通信列表里。这位 Doran Pike 目前只充当占位角色，到了 [C13-S1] 与 [C13-S2]，我们会换用固定 ID 建立联络人 Aster Vance 与正式管理员。

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

/** 在 Nipher 星系中建立 Foundry 空间站及其基础市场，并让一名港务官出现在通信目录。 */
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

        // 市场：设置主实体、加入经济系统、再让空间站实体指回市场，三处连接缺一不可
        MarketAPI market = Global.getFactory().createMarket(MARKET_ID, "Nipher Foundry", 3);
        market.setFactionId(GearInModPlugin.FACTION_ID);
        market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
        market.setPrimaryEntity(station);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_3");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.addSubmarket("storage");
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

### 在入口类中调用

在 `GearInModPlugin.onNewGame()` 中，于 `GearInSystemGenerator` 之后、`seedRelations(sector)` 之前加入生成调用：

```java
new GearInSystemGenerator().generate(sector);
new GearInFoundryGenerator().generate(sector);
new GearInAshenVergeGenerator().generate(sector);
seedRelations(sector);
```

生成器开头的 `sector.getEconomy().getMarket(MARKET_ID) != null` 判断确保了即便代码被意外多次调用，也不会在同一个位置叠出第二座市场。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，执行 `jump Nipher` 查看恒星周围。

- 在 Nipher Prime 轨道外侧多出了一座命名为 `Nipher Foundry` 的齿轮工业空间站；
- 停靠空间站后，交易栏里可见开放市场、黑市与仓库三个入口，产业栏里有居住区 & 基础设施与太空港；
- 点开通信目录，能够看到港务官 `Doran Pike`；
- 保存并重新读档，空间站与市场没有发生重复生成。

## 常见问题

- **星图上看得到 `Nipher Foundry`，靠近点击却没有停靠和交易选项**：只调了 `addMarket(...)`，忘记反过来调用 `station.setMarket(market)`。
    - **解决方案**：确保 `market.setPrimaryEntity(station)`、`economy.addMarket(market, true)` 与 `station.setMarket(market)` 三行完整执行。

- **通信目录里找不到生成的港务官 `Doran Pike`**：只调用了 `market.addPerson(officer)`。
    - **解决方案**：紧接着调用 `market.getCommDirectory().addPerson(officer)`。

- **新档进 Nipher 星系后空间站没有出现**：`GearInFoundryGenerator` 写在了 `GearInSystemGenerator` 之前，导致生成时 `sector.getStarSystem("Nipher")` 尚未创建而直接 `return`。
    - **解决方案**：确认 `GearInFoundryGenerator.generate(sector)` 在 `GearInSystemGenerator.generate(sector)` 之后调用。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场条件、产业、子市场与人物接口。
- `com.fs.starfarer.api.campaign.econ.EconomyAPI`：`addMarket(...)` 与市场查询入口。
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：殖民地通信目录条目管理。
- `com.fs.starfarer.api.impl.campaign.ids.Conditions`：原版市场条件 ID 常量 (`Industries`、`Submarkets` 同包)。
- `data/config/custom_entities.json`：`station_side00` 等空间站实体的图形与交互定义。
