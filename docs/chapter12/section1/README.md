# 创建生涯市场

本节在观测星系创建 Foundry 行星，为它建立规模 3 的市场，并开放公开市场、黑市与仓储。玩家从行星进入交易，经济系统从市场集合读取供需；我们把这两条关系接好，再通过价格、人物目录与经济更新确认市场的运行状态。

_本节预期阅读时间：\~24 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 创建行星并绑定一个可交互的 `MarketAPI`；
- 配置条件、产业、子市场和市场开放状态；
- 读取税率、价格、库存、人物、管理员和通信目录；
- 使用 `EconomyAPI` 查询市场集合并监听自然经济步；
- 根据实体交互和日志定位市场注册或绑定错误。

## 前置知识

- [C3-S3] 的星系、行星和生涯对象创建；
- [C7-S1] 的生涯入口和日志读取；
- [C10-S2] 的 `StarSystemAPI` 与 `SectorEntityToken` 查询；
- 理解 Java 接口、集合遍历和 `EveryFrameScript` 生命周期。

## 准备市场创建与观察入口

创建 `src/data/scripts/gearin/campaign/` 下的 `GearInMarketBuilder.java`、`GearInMarketInteraction.java` 与 `GearInEconomyProbe.java`。生成器建立市场，交互脚本比较并恢复临时状态，经济观察器等待更新后的结果。在 `GearInModPlugin` 添加三类的 import，向 [C10-S2] 已有的 `onGameLoad(...)` 登记追加以下局部片段，沿用其中的 `sector`：

```java
sector.addTransientScript(new GearInMarketBuilder());
sector.addTransientScript(new GearInMarketInteraction());
sector.addTransientScript(new GearInEconomyProbe());
```

这组登记放在观测星系的生成脚本之后。星系先存在，市场生成器才能添加行星；后两份脚本再按市场 ID 读取。市场生成器发现 `GearIn_foundry` 已存在时结束创建，因此读档保留市场，同时重新运行观察。

## 将市场连接到行星与经济系统

Foundry 行星使用 ID `GearIn_foundry`，市场使用 `GearIn_market_foundry`。两个对象名称同为 `GearIn Foundry`，归属齿轮工业，市场规模为 3。以下局部片段完成创建与绑定：

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

`createMarket(...)` 先得到市场，随后分别设置它与外界的关系。**市场的主实体、经济登记与行星的市场引用都要接好。** `setPrimaryEntity(planet)` 让市场取得主实体与地点；`addMarket(market, true)` 将它加入经济集合，并添加环境杂物与通信背景；`planet.setMarket(market)` 则让行星交互找到这份市场。市场势力与行星势力也分别设为 `gear_industry`。

主实体已经绑定，经济系统才能取得市场的地点。行星上已经保存市场，玩家才能从地图交互进入它。后续查找用 `economy.getMarket("GearIn_market_foundry")`，读取 `getPrimaryEntity()`、`getContainingLocation()`、`getFactionId()` 与 `isInEconomy()`，可以确认两条关系落在同一个市场上。

### 给市场提供基础交易环境

当前加入 `trade_center` 条件、`population` 与 `spaceport` 产业，并添加 `open_market`、`black_market`、`storage`。条件提供市场效果，产业参与人口、生产与基础设施，子市场提供实际交易和存储入口。

这些对象都用已注册 ID 创建。`getConditions()`、`getIndustries()` 与 `getSubmarketsCopy()` 读取当前集合，`hasSubmarket(...)`、`addSubmarket(...)` 与 `removeSubmarket(...)` 处理交易入口的存在状态。当前先采用原版内容，[C12-S3]、[C12-S4] 与 [C12-S6] 分别扩展交易规则、产业与条件。

## 读取补给库存与报价

市场建好后，`GearInEconomyProbe.logMarket(...)` 从经济集合取得 Foundry，读取原版补给 `supplies` 的商品状态。`getCommodityData(id)` 返回 `CommodityOnMarketAPI`，其中 `getStockpile()` 是库存状态，`getAvailable()` 是经济可得量。对象存在时仍可以没有库存，读取结果各自对应当前市场。

价格方向以市场为主体：`getDemandPrice("supplies", 10, true)` 表示市场买入 10 补给，即用户卖出；`getSupplyPrice(...)` 表示市场卖出，即用户买入。**这两个返回值是指定数量的商品金额**，税额由子市场交易流程处理。价格还会随库存、供需和经济刷新改变，[C12-S2] 进一步说明这些商品数据。

交互脚本另外比较税率、自由港与仓储。税率是 `MutableStat`，通过来源 `GearIn_market_interaction_tariff` 添加 0.05 的平加修饰；自由港切换会影响当前违禁品判断，仓储用子市场 ID 添加。记录临时状态后，脚本移除税率来源、恢复自由港，并在原来没有仓储时移除新入口。这样日志能确认每项写入及恢复，稍后打开的市场采用恢复后的状态。

## 让人物出现在通信目录

市场人物归属与通信目录分开维护。`market.addPerson(person)` 将人物加入市场并设置所属市场，`market.getCommDirectory().addPerson(person)` 才建立通信条目。下面的局部实验创建人物、登记目录，再分别移除：

```java
PersonAPI person = Global.getFactory().createPerson();
market.addPerson(person);
String entryId = market.getCommDirectory().addPerson(person);
boolean sameEntry = market.getCommDirectory().getEntryForPerson(person) != null;
int entries = market.getCommDirectory().getEntriesCopy().size();
market.getCommDirectory().removePerson(person);
market.removePerson(person);
```

`addPerson(...)` 返回条目 ID，`getEntriesCopy()` 读取目录，`getEntryForPerson(...)` 按人物查询条目。条目是 `CommDirectoryEntryAPI`，保存人物数据和显示内容；移除时可以使用人物引用，也可以使用条目或条目 ID。人物名单用 `getPeopleCopy()` 读取，两个集合数量可以不同。

::: warning getAdmin 会补建默认管理员
市场没有管理员时，`getAdmin()` 会创建默认人物并加入人物列表。本例的市场日志调用了这个方法；只读取已有名单时使用 `getPeopleCopy()`，目录条目仍由通信目录维护。
:::

## 在市场 Memory 中记录状态

市场实现 `HasMemory`。`getMemoryWithoutUpdate()` 取得市场自己的 Memory，`getMemory()` 还会让生涯插件更新市场事实。[C11-S3] 已说明键值与期限的恢复，本例沿用这个方法临时写入 `MemFlags.MARKET_PATROL`：保存原存在状态、对象与剩余期限，记录后立即恢复。

市场、主实体与舰队各有自己的 Memory。原版键的读取方使用市场对象时，本节也从 `market.getMemoryWithoutUpdate()` 写入，对象与键一起决定效果。下面的局部片段读取军事与巡逻标志：

```java
MemoryAPI memory = market.getMemoryWithoutUpdate();
boolean military = memory.getBoolean(MemFlags.MARKET_MILITARY);

// 临时写入采用记录原状态的完整观察流程。
boolean patrol = memory.getBoolean(MemFlags.MARKET_PATROL);
```

### 市场标志速查表

| 键                                                        | 值               | 用途                           |
| --------------------------------------------------------- | ---------------- | ------------------------------ |
| `MemFlags.MARKET_MILITARY`                                | boolean          | 标记市场具有军事设施语义       |
| `MemFlags.MARKET_PATROL`                                  | boolean          | 标记市场启用巡逻相关语义       |
| `MemFlags.MARKET_HAS_CUSTOM_INTERACTION_OPTIONS`          | boolean          | 使用自定义市场交互入口         |
| `MemFlags.MARKET_EXTRA_SUSPICION`                         | float            | 为走私检查增加额外怀疑值       |
| `MemFlags.MEMORY_MARKET_SMUGGLING_SUSPICION_LEVEL`        | float            | 保存市场当前走私怀疑等级       |
| `MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET` | boolean/带期限值 | 标记玩家在市场附近的敌对行为   |
| `MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS`               | boolean          | 阻止市场自动初始化通信目录条目 |

这些键向读取它们的功能提供市场状态。实际势力、产业与子市场继续通过对应结构方法设置，单独写入巡逻标志并没有创建军事设施或生成巡逻队。

## 等待经济更新，再读取市场

创建时设置的产业与条件还要参与经济计算。`GearInEconomyProbe` 先记录当前结果，再通过 `EconomyAPI.addUpdateListener(...)` 登记监听器，等待经济更新后重新记录 Foundry。

**经济更新监听器登记在 `EconomyAPI`。** 每个商品更新结束时调用 `commodityUpdated(commodityId)`，整轮结束后调用 `economyUpdated()`。本例在整轮回调中记录市场并将 `expired` 设为 `true`，经济系统在后续检查时移除它。外层一轮脚本结束后，已经登记的监听器仍等待自己的回调。

自然经济步与打开交易界面都可能触发计算，因此比较日志时要对应实际触发时机。本例的 `forceStockpileUpdate(market)` 只刷新这个市场的库存与相关价格，整个市场网络的一轮更新另有职责。

::: warning 后台脚本不适合强制推进完整经济步
`nextStep()`、`doubleStep()` 与 `tripleStep()` 执行同步经济计算，可能耗时超过一帧。它们用于适当的 UI 交互；后台生涯功能通过更新监听器等待结果。
:::

### 经济集合查询速查表

| 查询入口                                                       | 返回内容与用途                               |
| -------------------------------------------------------------- | -------------------------------------------- |
| `getMarket(id)`                                                | 按市场 ID 取得 Foundry 等指定市场            |
| `getMarketsCopy()`                                             | 遍历当前市场名单副本                         |
| `getMarkets(location)`                                         | 取得指定地点中的市场                         |
| `getLocationsWithMarkets()` / `getStarSystemsWithMarkets()`    | 取得具有市场的地点或星系                     |
| `getMarketsWithSameGroup(market)` / `getMarketsInGroup(group)` | 查询经济组内市场，组由 `econGroup` 区分      |
| `getNumMarkets()` / `getAllCommodityIds()`                     | 记录当前市场数量与已注册商品 ID              |
| `getCommoditySpec(id)`                                         | 取得商品全局规格，当前库存从市场商品对象读取 |

同一经济组内的市场相互贸易，并按组限制经济可见范围。查询只取得这些关系；要改变所属经济组，设置市场的 `econGroup`。本例使用现有分组，日志记录组内与地点内市场数量。

### 市场状态速查表

| 入口                                                               | 作用                                 | 观察重点                          |
| ------------------------------------------------------------------ | ------------------------------------ | --------------------------------- |
| `getId()` / `getName()` / `getSize()`                              | 读取市场身份和规模                   | 与创建参数一致                    |
| `getPrimaryEntity()` / `getConnectedEntities()`                    | 读取实体绑定                         | 主实体是 `GearIn_foundry`         |
| `getFactionId()` / `getFaction()`                                  | 读取市场势力                         | 返回 `gear_industry`              |
| `getSurveyLevel()` / `setSurveyLevel(...)`                         | 读取或设置勘测等级                   | 影响市场条件的可见状态            |
| `getTariff()`                                                      | 读取税率 `MutableStat`               | `getModifiedValue()` 是当前生效值 |
| `getCommodityData(id)`                                             | 取得单个商品数据                     | 同时检查库存和可得量              |
| `getDemandPrice(...)` / `getSupplyPrice(...)`                      | 读取指定数量的市场买入金额和卖出金额 | 价格随库存和经济更新变化          |
| `getPeopleCopy()` / `getAdmin()`                                   | 读取人物和管理员                     | `getAdmin()` 可能创建默认管理员   |
| `getCommDirectory()`                                               | 取得通信目录                         | 条目数量独立于人物列表            |
| `isFreePort()` / `setFreePort(...)`                                | 读取或修改自由港状态                 | 自由港会改变违禁品判定            |
| `hasSubmarket(...)` / `addSubmarket(...)` / `removeSubmarket(...)` | 查询、添加和移除子市场               | 以 spec ID 区分入口               |

## 完整最小例

三个类保存到前述路径，保留按顺序追加的生涯登记。

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
        boolean hadPatrol = memory.contains(MemFlags.MARKET_PATROL);
        Object oldPatrol = memory.get(MemFlags.MARKET_PATROL);
        float oldExpire = memory.getExpire(MemFlags.MARKET_PATROL);
        try {
            memory.set(MemFlags.MARKET_PATROL, true);
            LOG.info("GearIn market memory: patrol="
                    + memory.getBoolean(MemFlags.MARKET_PATROL));
        } finally {
            if (hadPatrol) {
                memory.set(MemFlags.MARKET_PATROL, oldPatrol, oldExpire);
            } else {
                memory.unset(MemFlags.MARKET_PATROL);
            }
            LOG.info("GearIn market memory restored: present="
                    + memory.contains(MemFlags.MARKET_PATROL)
                    + " value=" + memory.get(MemFlags.MARKET_PATROL)
                    + " expire=" + memory.getExpire(MemFlags.MARKET_PATROL));
        }
    }

    private void restoreMarketState(MarketAPI market) {
        MutableStat tariff = market.getTariff();
        float oldTariff = tariff.getModifiedValue();
        boolean oldFreePort = market.isFreePort();
        boolean hadStorage = market.hasSubmarket("storage");
        LOG.info(String.format("GearIn market interaction before: tariff=%.3f freePort=%s storage=%s",
                oldTariff, oldFreePort, hadStorage));
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
        economy.addUpdateListener(new ProbeListener(market));
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
                "GearIn market probe: id=%s entity=%s faction=%s size=%d markets=%d commodities=%d tariff=%.3f stockpile=%.1f available=%d demandPrice=%.1f supplyPrice=%.1f people=%d admin=%s commEntries=%d freePort=%s submarkets=%d",
                market.getId(), market.getPrimaryEntity().getId(), market.getFactionId(),
                market.getSize(), economy.getNumMarkets(), economy.getAllCommodityIds().size(),
                tariff.getModifiedValue(), supplies.getStockpile(), supplies.getAvailable(),
                market.getDemandPrice("supplies", 10, true),
                market.getSupplyPrice("supplies", 10, true),
                market.getPeopleCopy().size(), market.getAdmin().getName(),
                market.getCommDirectory().getEntriesCopy().size(), market.isFreePort(),
                market.getSubmarketsCopy().size()));
    }

    private class ProbeListener implements EconomyAPI.EconomyUpdateListener {
        private final MarketAPI market;
        private boolean expired;

        private ProbeListener(MarketAPI market) {
            this.market = market;
        }

        @Override
        public void commodityUpdated(String commodityId) {
            LOG.info("GearIn economy update: commodity=" + commodityId);
        }

        @Override
        public void economyUpdated() {
            LOG.info("GearIn economy update: complete");
            logMarket(Global.getSector().getEconomy(), market);
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

前置数据：按 [C10-S2] 生成的观测星系；本节三类与生涯登记已保存。

1. 构建 JAR 并重启，进入观测星系存档，推进一帧后在地图找到 `GearIn Foundry`。
2. 点击行星进入市场，确认市场名称、势力和三个子市场入口。
3. 搜索 `GearIn directory`、`GearIn market memory` 与 `GearIn market interaction`，核对临时人物移除、Memory 的存在与期限，以及税率、自由港与仓储恢复。
4. 搜索同一轮脚本的 `GearIn market probe` 与 `GearIn economy probe`，核对市场 ID、实体 ID、规模、经济组、地点市场数和商品规格。
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

- **经济监听器没有回调**：监听器没有加入 `EconomyAPI`，已被标为过期，或经济更新尚未发生。
    - **解决方案**：核对 `addUpdateListener(...)` 与 `isEconomyListenerExpired()`，继续推进生涯时间，等到 `economyUpdated()` 后结束本次观察。

- **探针结束后市场状态仍被修改**：只恢复了税率，没有恢复自由港或子市场集合。
    - **解决方案**：保存每个被修改状态的原值，并在同一流程中逐项恢复。

## 总结

Foundry 的行星交互与经济登记已经连接到同一个市场，原版条件、产业和子市场提供基础交易环境。报价以市场为主体，人物归属与通信目录分别维护，临时状态写入后按原值恢复。

市场还会继续参与经济更新。监听器在更新结束后取得结果，后面的商品、交易规则与生产效果也接到这份市场上；市场 ID 与主实体保留了它们在生涯世界中的共同位置。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场身份、实体绑定、状态、价格、人物和子市场入口；
- `com.fs.starfarer.api.campaign.econ.EconomyAPI`：市场集合、经济更新和更新监听器；
- `com.fs.starfarer.api.FactoryAPI`：`createMarket(...)` 和其他生涯对象创建入口；
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体市场、势力和地图交互入口；
- `com.fs.starfarer.api.campaign.CommDirectoryAPI`：市场通信目录条目管理。
