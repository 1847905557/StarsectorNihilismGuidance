# 创建生涯市场

本节从一个独立行星开始，创建市场并把它绑定到实体和经济集合。随后读取 `MarketAPI` 常用状态、执行可恢复的人物与交易入口交互，再用 `EconomyAPI` 观察市场集合和自然经济更新。

*本节预期阅读时间：~24 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 创建行星并绑定一个可交互的 `MarketAPI`；
- 配置条件、产业、子市场和市场开放状态；
- 读取税率、价格、库存、人物、管理员和通信目录；
- 使用 `EconomyAPI` 查询市场集合并监听自然经济步；
- 根据实体交互和日志定位市场注册或绑定错误。

## 前置知识

- [C3-S4] 的星系、行星和生涯对象创建；
- [C7-S1] 的生涯入口和日志读取；
- [C10-S2] 的 `StarSystemAPI` 与 `SectorEntityToken` 查询；
- [C12-S3] 的商品 ID 与商品数据；
- [C12-S4] 的子市场取得和货舱初始化边界；
- 理解 Java 接口、集合遍历和 `EveryFrameScript` 生命周期。

## 创建行星并绑定 MarketAPI

市场由 `Global.getFactory().createMarket(id, name, size)` 创建。这个调用只返回运行时对象；市场要进入经济循环，还需要设置势力和主实体，调用 `EconomyAPI.addMarket(...)`，再把市场写回实体。

示例使用 `GearIn_foundry` 行星和 `GearIn_market_foundry` 市场。行星创建沿用已有的 `StarSystemAPI.addPlanet(...)` 用法，本节只保留能够承接市场绑定的最小调用：

```java
StarSystemAPI system = Global.getSector().getStarSystem("GearIn_observation_system");
PlanetAPI planet = system.addPlanet("GearIn_foundry", system.getStar(),
        "GearIn Foundry", "barren", 135f, 110f, 3600f, 180f);
MarketAPI market = Global.getFactory().createMarket(
        "GearIn_market_foundry", "GearIn Foundry", 3);
market.setFactionId("gear_industry");
market.setPrimaryEntity(planet);
market.addCondition("trade_center");
market.addIndustry("population");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("black_market");
market.addSubmarket("storage");
Global.getSector().getEconomy().addMarket(market, true);
planet.setMarket(market);
planet.setFaction("gear_industry");
```

`setPrimaryEntity` 建立市场到行星的主实体关系，`addMarket` 让经济对象能够找到市场，`planet.setMarket` 才让玩家从行星进入市场交互。三处缺少任意一处，市场对象仍可能存在，但地图交互、经济集合或势力显示会断开。

创建脚本再次运行时按实体 ID 检查；已有行星直接结束，不删除或重建存档对象。请自行把 `GearInMarketBuilder` 注册到生涯脚本，或在已有生涯入口中调用它。

## 读取 MarketAPI 的常用状态

市场配置完成后，`MarketAPI` 同时提供结构读取和运行时修改入口。先把市场看成一个有明确归属的对象：`getPrimaryEntity()` 返回主实体，`getContainingLocation()` 返回实体所在地点，`getFactionId()` 返回市场势力，`isInEconomy()` 则确认市场已经进入经济集合。条件、产业和子市场是市场的组成部分，但它们的深入规则由对应小节负责。

### 市场状态速查表

| 入口 | 作用 | 观察重点 |
| --- | --- | --- |
| `getId()` / `getName()` / `getSize()` | 读取市场身份和规模 | 与创建参数一致 |
| `getPrimaryEntity()` / `getConnectedEntities()` | 读取实体绑定 | 主实体是 `GearIn_foundry` |
| `getFactionId()` / `getFaction()` | 读取市场势力 | 返回 `gear_industry` |
| `getSurveyLevel()` / `setSurveyLevel(...)` | 读取或设置勘测等级 | 影响市场条件的可见状态 |
| `getTariff()` | 读取税率 `MutableStat` | `getModifiedValue()` 是当前生效值 |
| `getCommodityData(id)` | 取得单个商品数据 | 同时检查库存和可得量 |
| `getDemandPrice(...)` / `getSupplyPrice(...)` | 读取市场买入价和卖出价 | 价格会随库存和经济更新变化 |
| `getPeopleCopy()` / `getAdmin()` | 读取人物和管理员 | `getAdmin()` 可能创建默认管理员 |
| `getCommDirectory()` | 取得通信目录 | 条目数量独立于人物列表 |
| `isFreePort()` / `setFreePort(...)` | 读取或修改自由港状态 | 自由港会改变违禁品判定 |
| `hasSubmarket(...)` / `addSubmarket(...)` / `removeSubmarket(...)` | 查询、添加和移除子市场 | 以 spec ID 区分入口 |

税率是 `MutableStat`，价格接口的方向以市场为主体：`getDemandPrice` 是市场买入价，也就是用户卖出时看到的价格；`getSupplyPrice` 是市场卖出价。库存读取使用 `getCommodityData(id)`，商品对象存在并不代表库存大于零，需要同时检查 `getStockpile()` 和 `getAvailable()`。修改税率或开放状态后，界面和价格会在相应刷新时使用新的值。

条件、产业和子市场的共同特点是 "对象挂在市场上，市场负责提供查询入口"。`addCondition` 和 `addIndustry` 按 ID 创建实例，`addSubmarket` 按 spec ID 创建交易入口；`getConditions()`、`getIndustries()` 和 `getSubmarketsCopy()` 返回当前集合。创建时只需要接入这些对象，具体的条件效果、产业生命周期和交易规则分别在后续小节展开。

## 管理人物与通信目录

市场人物列表和通信目录是两个集合。`MarketAPI.addPerson(person)` 记录人物属于哪个市场；`CommDirectoryAPI.addPerson(person)` 才会建立可显示的通信目录条目。目录返回的是 `CommDirectoryEntryAPI`，它保存条目 ID、人物和显示所需的入口信息。读取目录使用 `getEntriesCopy()`，按人物查找使用 `getEntryForPerson(person)`，移除时使用 `removePerson(person)` 或 `removeEntry(entry)`。

下面的局部流程只展示集合关系。人物本身的姓名、技能和职业不属于本节重点：

```java
PersonAPI person = Global.getFactory().createPerson();
market.addPerson(person);
String entryId = market.getCommDirectory().addPerson(person);
boolean sameEntry = market.getCommDirectory().getEntryForPerson(person) != null;
int entries = market.getCommDirectory().getEntriesCopy().size();
market.getCommDirectory().removePerson(person);
market.removePerson(person);
```

`getAdmin()` 在市场没有管理员时可能创建默认人物，因此它不是普通的无副作用 getter。需要保留管理员时明确调用并随后从人物与目录中管理它；只查看市场是否有人物时使用 `getPeopleCopy()`。

## 读取市场 Memory 与 MemFlags

市场实现 `HasMemory`，`getMemoryWithoutUpdate()` 适合读取和写入市场自身的临时状态，`getMemory()` 还可能触发市场事实更新。`MemFlags` 是原版约定的稳定键名；键属于市场 Memory 时，读取对象必须是 `market.getMemoryWithoutUpdate()`，不能改读实体或舰队 Memory。

### 市场标志速查表

| 键 | 值 | 用途 |
| --- | --- | --- |
| `MemFlags.MARKET_MILITARY` | boolean | 标记市场具有军事设施语义 |
| `MemFlags.MARKET_PATROL` | boolean | 标记市场启用巡逻相关语义 |
| `MemFlags.MARKET_HAS_CUSTOM_INTERACTION_OPTIONS` | boolean | 使用自定义市场交互入口 |
| `MemFlags.MARKET_EXTRA_SUSPICION` | float | 为走私检查增加额外怀疑值 |
| `MemFlags.MEMORY_MARKET_SMUGGLING_SUSPICION_LEVEL` | float | 保存市场当前走私怀疑等级 |
| `MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET` | boolean/带期限值 | 标记玩家在市场附近的敌对行为 |
| `MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS` | boolean | 阻止市场自动初始化通信目录条目 |

例如，读取军事标志时只需要读取市场 Memory：

```java
MemoryAPI memory = market.getMemoryWithoutUpdate();
boolean military = memory.getBoolean(MemFlags.MARKET_MILITARY);

// 一般由对应的产业设置
memory.set(MemFlags.MARKET_PATROL, true);
memory.unset(MemFlags.MARKET_PATROL);
```

这些键改变的是原版系统读取到的市场状态；它们不会替代 `setFactionId`、`addIndustry` 或 `addSubmarket` 这样的结构配置。

自由港状态、子市场集合和税率都可以在一次交互流程中被读取和修改。示例会记录原值，完成观察后按同一对象恢复；这样读者能够看到修改的影响，同时不会把演示状态留在存档里。

## 使用 EconomyAPI 观察经济集合

`Global.getSector().getEconomy()` 返回当前生涯的 `EconomyAPI`。它负责市场集合、商品规格查询和经济更新。`getMarket(id)` 适合精确取得目标市场，`getMarketsCopy()` 适合遍历快照，`getMarkets(LocationAPI)` 适合按地点筛选，`getLocationsWithMarkets()` 和 `getStarSystemsWithMarkets()` 适合从地点或星系反查市场。`getNumMarkets()` 和 `getAllCommodityIds()` 用于记录经济规模，`getCommoditySpec(id)` 则返回商品的全局规格，而不是某个市场的库存。

经济组用 `getMarketsWithSameGroup(market)` 或 `getMarketsInGroup(group)` 查询。市场的 `econGroup` 相同，经济可见范围才会相互关联；这组查询只返回集合关系，不会改变市场本身。需要让修改后的库存立即进入经济计算时，使用 `forceStockpileUpdate(market)`；它只刷新指定市场的库存相关数据，不等价于完整经济步。

经济更新监听器必须交给 `EconomyAPI.addUpdateListener(...)`。`commodityUpdated` 表示某个商品完成更新，`economyUpdated` 表示本轮经济更新结束；监听器返回过期后，经济对象会在后续更新中移除它。`nextStep()`、`doubleStep()` 和 `tripleStep()` 只适合市场界面等 UI 交互，不应从后台生涯脚本调用。

`GearInMarketInteraction` 负责一次市场交互，记录人物与通信目录、市场 Memory，并把税率、自由港和子市场恢复到原值。`GearInEconomyProbe` 负责经济集合查询和自然经济更新监听；`commodityUpdated` 先报告商品更新，`economyUpdated` 再报告整轮更新。`nextStep()`、`doubleStep()` 和 `tripleStep()` 只能由 UI 交互使用，后台脚本只监听自然更新。

## 完整最小例

::: details 完整成品：GearInMarketBuilder
```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class GearInMarketBuilder implements EveryFrameScript {
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system == null) {
            done = true;
            return;
        }
        PlanetAPI planet = (PlanetAPI) system.getEntityById("GearIn_foundry");
        if (planet != null) {
            done = true;
            return;
        }
        planet = system.addPlanet("GearIn_foundry", system.getStar(),
                "GearIn Foundry", "barren", 135f, 110f, 3600f, 180f);
        MarketAPI market = Global.getFactory().createMarket(
                "GearIn_market_foundry", "GearIn Foundry", 3);
        market.setFactionId("gear_industry");
        market.setPrimaryEntity(planet);
        market.addCondition("trade_center");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.addSubmarket("storage");
        EconomyAPI economy = sector.getEconomy();
        economy.addMarket(market, true);
        planet.setMarket(market);
        planet.setFaction("gear_industry");
        done = true;
    }
}
```
:::

::: details 完整成品：GearInMarketInteraction
```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class GearInMarketInteraction implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInMarketInteraction.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private static final String TARIFF_ID = "GearIn_market_interaction_tariff";
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        SectorAPI sector = Global.getSector();
        MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
        if (market == null) {
            done = true;
            return;
        }
        recordPersonAndDirectory(market);
        recordMemory(market);
        restoreMarketState(market);
        done = true;
    }

    private void recordPersonAndDirectory(MarketAPI market) {
        PersonAPI person = Global.getFactory().createPerson();
        market.addPerson(person);
        CommDirectoryAPI directory = market.getCommDirectory();
        String entryId = directory.addPerson(person);
        LOG.info(String.format("GearIn directory: marketPeople=%d entries=%d entryId=%s found=%s",
                market.getPeopleCopy().size(), directory.getEntriesCopy().size(), entryId,
                directory.getEntryForPerson(person) != null));
        directory.removePerson(person);
        market.removePerson(person);
    }

    private void recordMemory(MarketAPI market) {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        boolean oldPatrol = memory.getBoolean(MemFlags.MARKET_PATROL);
        memory.set(MemFlags.MARKET_PATROL, true);
        LOG.info("GearIn market memory: patrol="
                + memory.getBoolean(MemFlags.MARKET_PATROL));
        memory.set(MemFlags.MARKET_PATROL, oldPatrol);
    }

    private void restoreMarketState(MarketAPI market) {
        MutableStat tariff = market.getTariff();
        float oldTariff = tariff.getModifiedValue();
        boolean oldFreePort = market.isFreePort();
        boolean hadStorage = market.hasSubmarket("storage");
        tariff.modifyFlat(TARIFF_ID, 0.05f);
        market.setFreePort(!oldFreePort);
        market.addSubmarket("storage");
        LOG.info(String.format("GearIn market interaction during: tariff=%.3f freePort=%s storage=%s",
                tariff.getModifiedValue(), market.isFreePort(), market.hasSubmarket("storage")));
        tariff.unmodify(TARIFF_ID);
        market.setFreePort(oldFreePort);
        if (!hadStorage) {
            market.removeSubmarket("storage");
        }
        LOG.info(String.format("GearIn market interaction restored: tariff=%.3f freePort=%s storage=%s",
                tariff.getModifiedValue(), market.isFreePort(), market.hasSubmarket("storage")));
    }
}
```
:::

::: details 完整成品：GearInEconomyProbe
```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

public class GearInEconomyProbe implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInEconomyProbe.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        SectorAPI sector = Global.getSector();
        EconomyAPI economy = sector.getEconomy();
        MarketAPI market = economy.getMarket(MARKET_ID);
        if (market == null) {
            LOG.warn("GearIn market probe: market missing id=" + MARKET_ID);
            done = true;
            return;
        }
        logMarket(economy, market);
        logEconomy(economy, market);
        economy.addUpdateListener(new ProbeListener());
        LOG.info("GearIn market probe: economy listener registered");
        done = true;
    }

    private void logEconomy(EconomyAPI economy, MarketAPI market) {
        StarSystemAPI system = market.getStarSystem();
        LOG.info(String.format(
                "GearIn economy probe: sameGroup=%d locationMarkets=%d system=%s specs=%s",
                economy.getMarketsWithSameGroup(market).size(),
                economy.getMarkets(market.getContainingLocation()).size(),
                system == null ? "none" : system.getId(),
                economy.getCommoditySpec("supplies").getId()));
        economy.forceStockpileUpdate(market);
    }

    private void logMarket(EconomyAPI economy, MarketAPI market) {
        CommodityOnMarketAPI supplies = market.getCommodityData("supplies");
        MutableStat tariff = market.getTariff();
        LOG.info(String.format(
                "GearIn market probe: id=%s entity=%s faction=%s size=%d markets=%d commodities=%d tariff=%.3f stockpile=%.1f available=%.1f demandPrice=%.1f supplyPrice=%.1f people=%d admin=%s commEntries=%d freePort=%s submarkets=%d",
                market.getId(), market.getPrimaryEntity().getId(), market.getFactionId(),
                market.getSize(), economy.getNumMarkets(), economy.getAllCommodityIds().size(),
                tariff.getModifiedValue(), supplies.getStockpile(), supplies.getAvailable(),
                market.getDemandPrice("supplies", 10, true),
                market.getSupplyPrice("supplies", 10, true),
                market.getPeopleCopy().size(), market.getAdmin().getName(),
                market.getCommDirectory().getEntriesCopy().size(), market.isFreePort(),
                market.getSubmarketsCopy().size()));
    }

    private static class ProbeListener implements EconomyAPI.EconomyUpdateListener {
        private boolean expired;

        @Override
        public void commodityUpdated(String commodityId) {
            LOG.info("GearIn economy update: commodity=" + commodityId);
        }

        @Override
        public void economyUpdated() {
            LOG.info("GearIn economy update: complete");
            expired = true;
        }

        @Override
        public boolean isEconomyListenerExpired() {
            return expired;
        }
    }
}
```
:::

## 验证方案

前置数据：已经建立 `GearIn_observation_system`，并且您知道如何自行注册或调用三个示例脚本。

1. 注册并运行 `GearInMarketBuilder`，在地图中找到 `GearIn Foundry` 行星。
2. 点击行星进入市场，确认市场名称、势力和三个子市场入口。
3. 运行 `GearInMarketInteraction`，搜索 `GearIn directory` 和 `GearIn market interaction`，核对人物、通信目录、Memory、税率、自由港和子市场恢复日志。
4. 运行 `GearInEconomyProbe`，搜索 `GearIn market probe` 和 `GearIn economy probe`，核对市场 ID、实体 ID、规模、经济组、地点市场数和商品规格。
5. 等待自然经济步，搜索 `GearIn economy update`，确认商品更新后出现整轮经济更新。

预期结果：

- 地图存在 `GearIn Foundry`，其市场入口能够打开；
- `GearIn_market_foundry` 同时出现在实体和 `EconomyAPI` 市场集合中；
- 市场拥有 `trade_center`、`population`、`spaceport` 和三个子市场；
- 交互流程结束后税率、自由港和仓储子市场状态与执行前一致；
- 自然经济步触发 `commodityUpdated` 和 `economyUpdated`。

## 故障分析

- **行星存在但没有市场入口**：市场没有写回行星，或市场没有完成经济集合注册。

    - **解决方案**：确认 `EconomyAPI.addMarket(...)`、`planet.setMarket(...)` 和 `planet.setFaction(...)` 都在创建流程中执行。

- **日志显示市场存在但地图查询不到**：`MarketAPI` 没有绑定正确的主实体。

    - **解决方案**：核对 `setPrimaryEntity(planet)` 和 `getPrimaryEntity().getId()`。

- **管理员数量在纯读取后增加**：调用 `getAdmin()` 时市场没有管理员，API 创建了默认人物。

    - **解决方案**：只在需要管理员交互时调用 `getAdmin()`，其余读取使用 `getPeopleCopy()`。

- **经济监听器没有回调**：监听器没有加入 `EconomyAPI`，或脚本在经济步前被移除。

    - **解决方案**：使用 `EconomyAPI.addUpdateListener(...)`，并保持监听器对象直到 `economyUpdated()` 触发。

- **探针结束后市场状态仍被修改**：只恢复了税率，没有恢复自由港或子市场集合。

    - **解决方案**：保存每个被修改状态的原值，并在同一流程中逐项恢复。

## 总结

`MarketAPI` 的创建和应用分成对象创建、实体绑定和经济集合注册三个阶段。条件、产业和子市场在创建后附加到市场对象，玩家通过实体上的市场入口进入交互。

常用 MarketAPI 读取覆盖身份、势力、价格、库存、人物、管理员、通信目录、开放状态和子市场。`EconomyAPI` 负责跨市场查询与经济更新监听；临时交互保存原值并恢复，才能让测试结果保持可重复。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场身份、实体绑定、状态、价格、人物和子市场入口；
- `com.fs.starfarer.api.campaign.econ.EconomyAPI`：市场集合、经济更新和更新监听器；
- `com.fs.starfarer.api.FactoryAPI`：`createMarket(...)` 和其他生涯对象创建入口；
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体市场、势力和地图交互入口；
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：市场通信目录条目管理。
