# 用代码创建市场

本节用代码创建市场，把实体、子市场和经济循环连接起来。示例会设置市场的开放状态并检查商品流，为产业、交易界面与殖民地管理提供基础。

*本节预期阅读时间：~45 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 说出市场 (market) 的结构：`MarketAPI` 名下挂哪些子集合 (身份与实体绑定、条件、产业、子市场、商品、人物、状态、memory、stats)，以及每个子集合从哪个 getter 进入；
- 用代码路径在生涯里创建一个市场：`FactoryAPI.createMarket` → `setFactionId` / `setPrimaryEntity` → `addCondition` / `addIndustry` / `addSubmarket` → `EconomyAPI.addMarket` → `SectorEntityToken.setMarket`。
    - 并复述 "缺任何一步的失败形态"；
- 说出三种原版子市场 (`Submarkets.SUBMARKET_OPEN` / `SUBMARKET_BLACK` / `SUBMARKET_STORAGE`) 的插件行为差异：税率、合法性、库存上限与稳定度的关系、是否参与经济、用户交易的 `PlayerEconomyImpactMode`；
- 说出经济循环 (`EconomyAPI.advance` → 经济推进器 → 每月 `economyIterPerMonth` 次迭代 → 月末步 `updateStockpileAndPrice`) 的节奏。
    - 以及 `nextStep()` / `doubleStep()` / `tripleStep()` 只允许在 UI 交互路径调用、`EconomyUpdateListener` 必须经 `EconomyAPI.addUpdateListener` 注册这两条必须遵守的规则；
- 说出开放状态族 (`setFreePort` / `setHidden` / `setPlayerOwned` / `setSurveyLevel` / `setEconGroup` / tag) 各自改变什么可观察行为；
- 从症状定位典型故障：市场不出现、实体没有市场归属、`getCargoNullOk()` 判空失败、后台调 `nextStep` 卡顿、监听器永不回调、把全局基础价当市场价、误删同 id 全部条件、用 `getMemory()` 做纯读取等。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C10-S2] 的经济数据加载链与代码路径建市场收尾；
- [C6-S2] 的 `modPlugin` 生命周期与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义；
- [C6-S4] 的 settings 读取；
- [C7-S3] 的 `MutableStat` 与修饰值读取；
- [C9-S1] 的 `SectorAPI.getEconomy()`、生涯时钟与 `EveryFrameScript` 注册；
- [C9-S2] 的 `SectorEntityToken` 与地点容器挂载；
- [C9-S3] 的生涯对象一次性脚本与 `REGISTER_MARK` 标记。

## 术语

按术语约定，本节首次完整讲解以下词：

- **市场 (market)**：API 名保留 `MarketAPI`。一个 "可交易的经济节点"，绑定在一个主实体 (行星、空间站、残骸等) 上，持有势力、规模、条件、产业、子市场、商品库存、人物与开放状态。涉及殖民地 (colony) 语义时页面会明确说 "殖民地"；本节 `market` 一律写 "市场"。
- **子市场 (submarket)**：API 名保留 `SubmarketAPI`。市场下属的独立交易入口 (开放市场、黑市、仓库等)，每个子市场由静态 spec + 运行时 plugin + 自己的 cargo 组成。
- **商品 (commodity)**：API 名保留 `CommodityOnMarketAPI`(市场内单商品对象) 与 `CommoditySpecAPI`(全局商品规格)。"商品流" 指商品在市场网络里以经济单位流动、最终反映为库存与价格的循环。
- **库存 (stockpile)**：市场内单商品的存量，以 "经济单位刻度" 计量 (与用户 cargo 的原始单位不同，`CommoditySpecAPI.getEconUnit()` 是换算系数)。
- **开放状态 (open state)**：一组改变市场可交易语义的开关，包括自由港 (free port)、隐藏 (hidden)、用户所有 (player-owned)、survey 等级、经济组 (econ group) 与 tag。

## 梳理市场创建关系

本节的所有读取与注册都围绕同一个任务：在生涯里建一个市场，再实地观察它的结构、子市场与经济行为。先看市场对象的结构与创建链，再分别展开子市场差异与经济循环节奏；最后用一个可运行的示例把这套入口逐一在生涯里跑一遍。

### 市场对象 (MarketAPI)

先给结论：市场是绑定在主实体上的经济节点，它自己持有全部子集合，但商品流重算不属于它 —— 那是经济集合的职责。`MarketAPI`(`com.fs.starfarer.api.campaign.econ.MarketAPI`) 是纯接口，运行时实现为 `Market`。

### 市场结构速查表

市场结构 (字段与 getter)：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()` / `getName()` / `setName(String)` / `getSize()` / `setSize(int)` | 市场 id(全星区唯一)；名称；规模 (殖民地人口规模，`setSize` 变化触发 `reportColonySizeChanged` 监听器) |
| 实体绑定 | `getPrimaryEntity()` / `setPrimaryEntity(token)` / `getConnectedEntities()` / `getPlanetEntity()` | 主实体与关联实体集合；`setPrimaryEntity` 同时把实体加入 `connectedEntities` 并同步超空间坐标 |
| 位置 | `getStarSystem()` / `getContainingLocation()` / `getLocationInHyperspace()` / `getLocation()` / `isInHyperspace()` / `getOnOrAt()` | 全部由主实体推导；`getLocation()` 是 `getLocationInHyperspace()` 的别名 (不是星系内本地坐标)；`getOnOrAt()`：主实体带 `station` tag 返回 `"at"`，否则 `"on"` |
| 势力 | `getFactionId()` / `setFactionId(String)` / `getFaction()` | 市场势力；`setFactionId` 清缓存，`getFaction` 用到时才解析 |
| 条件 | `getConditions()` / `addCondition(id[, param])` / `getCondition(id)` / `getFirstCondition(id)` / `getSpecificCondition(token)` / `removeCondition(id)` / `removeSpecificCondition(token)` / `reapplyConditions()` / `suppressCondition(id)` / `isConditionSuppressed(id)` | 条件实例的增删查、重算与抑制 |
| 产业 | `getIndustries()` / `addIndustry(id[, params])` / `getIndustry(id)` / `hasIndustry(id)` / `hasFunctionalIndustry(id)` / `removeIndustry(id, mode, forUpgrade)` / `instantiateIndustry(id)` | 产业的增删查与实例化 |
| 商品 | `getAllCommodities()` / `getCommoditiesCopy()` / `getCommodityData(id)` / `getCommoditiesWithTag(tag)` / `getDemand(demandClass)` | 市场内商品数据与需求的读取入口 |
| 价格 | `getTariff()` / `getDemandPriceMod()` / `getSupplyPriceMod()` / `getSupplyPrice(id, qty, isPlayerPrice)` / `getDemandPrice(id, qty, isPlayerPrice)` / `updatePrices()` / `updatePriceMult()` | 税率是 `MutableStat`；价格方向以**市场为主体** (`getDemandPrice` = 市场买入价 = 用户卖出价) |
| 人物 | `addPerson(person)` / `removePerson(person)` / `getPeopleCopy()` / `getAdmin()` / `setAdmin(person)` / `getCommDirectory()` | 市场人物、管理员与通信目录 |
| 状态 | `isFreePort()` / `setFreePort(boolean)` / `isHidden()` / `setHidden(Boolean)` / `isPlayerOwned()` / `setPlayerOwned(boolean)` / `getSurveyLevel()` / `setSurveyLevel(...)` / `getEconGroup()` / `setEconGroup(String)` / `getTags()` / `addTag(String)` / `removeTag(String)` / `isIllegal(id)` / `isInEconomy()` | 开放状态开关与商品非法性判定 |
| 稳定度与危险度 | `getStability()` / `getStabilityValue()` / `getHazard()` / `getHazardValue()` | 稳定度取整到 0–10(`Market.getStabilityValue`)；危险度 `1f` = `100%` |
| 杂项 | `getMemory()` / `getMemoryWithoutUpdate()` / `getStats()` / `getDaysInExistence()` / `advance(float)` | `getMemory()` 触发所有插件的 `updateMarketFacts` 回调；纯读取用 `getMemoryWithoutUpdate()` |
| 子市场 | `addSubmarket(specId)` / `hasSubmarket(specId)` / `getSubmarket(specId)` / `getSubmarketsCopy()` / `removeSubmarket(specId)` | 子市场的增删与查询 |

#### [创建与注册链路]

**数据路径** (加载时序在经济数据加载主题中已讲，本节补结构)：`data/campaign/econ/economy.json` 列出 `starSystems`，每个星区一个 json(如 `econ/corvus.json`) 含 `markets` 数组。

每个市场条目：`entities`(数组，**首实体的 id 就是市场 id**)、`faction`、`size` 为必填；`tariff` 键仅模拟模式生效 (真实税率 = 势力 `tariffFraction`，`FactionSpecAPI` 默认 `0.3F`)；

可选键 `startingConditions`、`industries`(元素可为数组 `[id, param...]`)、`freePort`、`submarkets`。

**`submarkets` 键缺失时加载器补默认：`open_market` + `black_market` + `storage`，且市场有 `militarybase` 或 `highcommand` 产业时再加 `generic_military`**(经济数据加载器)。

加载器对每个实体执行 `entity.setMarket(market)` 与 `entity.setFaction(...)`，并把实体加入 `connectedEntities`。

**代码路径** (生涯代码里建市场，把经济数据主题的注册收尾展开为完整链)：

1. `Global.getFactory().createMarket(id, name, size)` 创建市场对象 (只建对象，不注册)。
2. `setFactionId(factionId)` 设势力；`setPrimaryEntity(entity)` 绑主实体。
3. 按需 `addCondition(...)` / `addIndustry(...)` / `addSubmarket(specId)`。
4. `economy.addMarket(market, withJunkAndChatter)` 注册进经济集合 (`withJunkAndChatter = true` 会加 junk 与无线电喧闹)。
5. `entity.setMarket(market)`(以及 `entity.setFaction(...)`) 把实体绑到市场。

建议使用 `Misc.initEconomyMarket` 或手动执行这条链：`createMarket(primaryEntity.getId(), ...)` → `setFactionId` → `setPrimaryEntity` → `getTariff().modifyFlat("generator", tarrif)` → `addSubmarket` ×N。

随后 `addCondition` ×N(`free_market` 条件同时 `setFreePort(true)`)→ `addIndustry` ×N。

随后 `globalEconomy.addMarket(newMarket, true)` → `primaryEntity.setMarket(newMarket)` → `primaryEntity.setFaction(factionID)`。

代码路径建市场必须完成注册收尾

`economy.addMarket` / `entity.setMarket` / `entity.setFaction` 缺一不可：

- 只 `createMarket` 不 `addMarket`：市场不在经济集合里，`isInEconomy()` 返回 `false`(`Market.isInEconomy` 就是查 `economy.getMarket(id) != null`)，不出现在任何经济/UI 入口，商品流永不更新它。**可观察结果**：市场 "不存在"，代码侧无报错。
- 不 `entity.setMarket`：实体与市场脱钩，交互入口 (进市场界面) 失效，`getPrimaryEntity()` 侧的市场归属为空。**可观察结果**：点击实体没有任何市场交互，日志无报错。
- 不 `entity.setFaction`：实体仍显示原势力 (市场与实体势力不一致)。

`EconomyAPI.removeMarket(market)` 从集合移除。

#### [市场条件与产业访问]

**条件 (condition)**：`getConditions()` 返回市场当前 `MarketConditionAPI` 列表 (活集合，只读遍历时先拷贝)。

`addCondition(id)` 返回该实例的**唯一 token**(`getIdForPluginModifications()`，形如 `ore_sparse_b44c3`，spec id ≠ 实例 token)；

带参数用 `addCondition(id, param)`(`Market.addCondition`：非 suppressed 时立即 `plugin.apply(token)`)。读取：

- `getCondition(id)` / `getFirstCondition(id)`：同 spec id 的**首个**实例；
- `getSpecificCondition(token)`：按唯一 token 取**具体**实例。

移除：`removeCondition(id)` **移除同 spec id 的全部实例**；`removeSpecificCondition(token)` 只移除 token 对应实例。

**必须记住这个不对称性**：多实例条件 (同 id 出现多次) 想删一个，用 token；用 id 会把所有副本一起删掉。**可观察结果**：同名条件全部消失。

`reapplyConditions()` 逐个 `unapply`/`apply`(suppressed 的跳过)；`suppressCondition(id)` 只抑制重算时的生效，保留实例与显示，配对 `unsuppressCondition(id)` 恢复。

条件 plugin 生命周期 (`MarketConditionPlugin`)：`init(market, condition)` → `setParam`(可选)→ `apply(id)` → 每帧 `advance`(`runWhilePaused()` 决定暂停时是否推进)→ 移除/重算时 `unapply(id)`。

**产业 (industry)**：`getIndustries()` 返回 `Industry` 列表；`addIndustry(id)` 幂等 (已存在则跳过)，spec 不存在时**抛 `RuntimeException("Industry [...] not found")`**。`addCondition(id)` 对不存在的条件 spec 同样抛出运行时异常。

`getIndustry(id)` / `hasIndustry(id)` 按 id 查；`hasFunctionalIndustry(id)` 额外要求 `isFunctional()`(被封禁/损坏的产业不算)；

`removeIndustry(id, mode, forUpgrade)` 三个参数按 API 语义调用 (代码里 mode 传 null)；`instantiateIndustry(id)` 只建实例不挂载。

产业系统的供需、建设和 disruption 细节由产业语义维护；本节只讲市场对产业的访问入口，产业制作见 [C10-S4]。

#### [商品与价格]

`getAllCommodities()` 返回**全部已注册商品**在市场上的数据对象 (对无关商品也是占位对象，`Market.getAllCommodities` 遍历已注册商品规格逐个 `getCommodityData`)；

`getCommodityData(id)` 按需创建单个 `CommodityOnMarketAPI`。

因此 "某商品在市场里有没有数据" 不是判断依据，要看数值。

### CommodityOnMarketAPI 成员速查表

`CommodityOnMarketAPI` 高频成员：

| 成员 | 语义 |
|---|---|
| `getStockpile()` / `setStockpile(float)` | 当前库存 (经济单位刻度) |
| `getAvailable()` | 当前可得量 (`getAvailableStat()` 的修饰后值，含低可达性扣减) |
| `getMaxSupply()` / `getMaxDemand()` | 供需上限 (产业重算后) |
| `getDemandClass()` / `getDemand()` | 需求类与 `MarketDemandAPI`(`getDemandValue()`) |
| `getTradeMod()` / `getTradeModPlus()` / `getTradeModMinus()` | 用户交易写入的 120 天衰减修饰 |
| `isIllegal()` / `isNonEcon()` / `isFuel()` / `isPersonnel()` | 商品级判定 |

价格：`getTariff()` 返回 `MutableStat`(税率；`getModifiedValue()` 是生效值)；`getSupplyPrice(id, qty, isPlayerPrice)` 是市场卖出价、`getDemandPrice(...)` 是市场买入价。

**方向以市场为主体** (`isPlayerPrice = true` 时套用用户价格修饰)。

`CommoditySpecAPI.getBasePrice()` 是全局基础价，**禁止**当市场买卖价用 (市场价必须走价格接口)。

`updatePrices()` 重算全部商品的 `updateCalc()`；`updatePriceMult()` 在 0.98a 是空实现。

**原版 javadoc 缺陷**：`updatePriceMult` 的 javadoc 声称 "按稳定度更新本地价格倍率"，实现却是空方法。调用它不会有任何效果。

#### [人物与开放状态]

**人物**：`addPerson(person)` 加入市场人物列表并同时写入 `person.setMarket(this)`；`removePerson(person)` 反向解除；`getPeopleCopy()` 返回快照。

**注意两个集合边界**：`addPerson` 不会自动创建通信目录 (comm directory) 条目，可见联系人需要另行 `getCommDirectory().addPerson(person)` 维护；`getCommDirectory()` 用到时才创建。

`setAdmin(admin)` 会移除旧管理员的市场与通信目录归属。

纯读取场景不要调用 `getAdmin()`

`getAdmin()` **用到时才创建**默认管理员 (`Market.getAdmin`：市场无 admin 时 new 一个默认人物并 `setAdmin`；用户拥有的市场回退到用户扮演的角色) —— 调用它会留下创建管理员的持久副作用：存档被写入一个从未有意创建的管理员。

**开放状态** (本节主题之一)：

- **自由港**：`setFreePort(true)` 自动添加 `free_market` 条件 (`Market.setFreePort`，条件 id 为 `free_market`，plugin 为 `FreeMarket`)。
    - `isFreePort()` 为 true 时 `isIllegal(id)` **恒返回 false**，自由港市场一切商品合法。
- **隐藏市场**：`setHidden(true)` 后市场不提供会暴露其存在的任务、事件或 intel。商品计算跳过该市场的进口需求；可达图、库存和价格路径仍可能使用它。
- **用户所有**：`setPlayerOwned(true)` 改变殖民地管理语义 (存档界面、收入、存储免费等)，并刷新用户的 outpost 效果。
- **survey 等级**：`SurveyLevel.NONE / SEEN / PRELIMINARY / FULL`，`getSurveyLevel()` 读出；核心世界与代码路径建市场时显式设 `FULL`。
- **经济组**：`getEconGroup()` / `setEconGroup(String)`：同一经济组的市场互相可见且只互相交易；所有 null 组市场属于同一组。
- **tag**：`addTag` / `removeTag` / `hasTag` / `getTags`：市场级标记 (与商品 tag、实体 tag 不同集合)。

#### [每帧推进 (advance)]

`Market.advance(amount)` 做什么：换算游戏日累加 `daysInExistence`；稳定度变化时 `reapplyConditions()` + `reapplyIndustries()` + `updatePrices()`；推进全部条件 plugin、全部子市场 plugin、memory、人物、建筑队列。

同时推进商品 `available/tradeMod/tradeModPlus/tradeModMinus` 的衰减并 `reapplyEventMod()`；推进产业。**市场自身不在这里做商品流重算** —— 商品流是经济集合的职责。

### 子市场 (SubmarketAPI)

市场结构里，子市场是用户最常打交道的入口。三种原版子市场看起来都是同一个交易界面，行为差异全部集中在各自插件上，可以先用一句话记住差异方向：税率 (市场税率 / 恒 0 / 恒 0)、合法性 (仅合法 / 全合法 / 用户市场全合法)、库存与稳定度的关系 (正相关 / 负相关 / 无)、经济参与 (参与 / 参与 / 不参与)。先看插件从哪来，再对照差异表。

#### [静态表与运行时对象]

子市场静态表是 `data/campaign/submarkets.csv`(列：`id,name,faction,desc,script,icon,order`)。

### 原版子市场 spec 速查表

原版五个 spec：

| id | faction | plugin(script 列) | order |
|---|---|---|---|
| `open_market` | (空，用市场势力) | `OpenMarketPlugin` | 0 |
| `generic_military` | (空) | `MilitarySubmarketPlugin` | 100 |
| `local_resources` | (空) | `LocalResourcesSubmarketPlugin` | 200 |
| `black_market` | `pirates` | `BlackMarketPlugin` | 999 |
| `storage` | `player` | `StoragePlugin` | 1000 |

id 常量在 `com.fs.starfarer.api.impl.campaign.ids.Submarkets`(`SUBMARKET_OPEN = "open_market"`、`SUBMARKET_BLACK = "black_market"`、`SUBMARKET_STORAGE = "storage"`、`GENERIC_MILITARY`、`LOCAL_RESOURCES`)。

运行时对象 (`SubmarketAPI`)：`addSubmarket(specId)` 创建实例时按 spec 的 `script` 列 `getNewPluginInstance()` 并 `plugin.init(this)`；faction 解析：spec 的 faction 非空用它 (找不到该 faction **抛 RuntimeException**)，为空回退市场势力。

`getSpecId()` / `getSpec()` / `getPlugin()` / `getCargo()` / `getCargoNullOk()` / `getTariff()`(转发 plugin)/ `isIllegalOnSubmarket(stack, action)` 是运行时入口；`getNameOneLine()` 把多行名压成单行。

`addSubmarket` 对已存在的 spec id **静默跳过** (`Market`)，列表按 spec `order` 排序。

#### [open / black / storage 三种子市场]

### 三种子市场行为速查表

三种子市场的插件差异 (`com.fs.starfarer.api.impl.campaign.submarkets` 包的公开 API 面)：

| 行为 | open\_market(`OpenMarketPlugin`) | black\_market(`BlackMarketPlugin`) | storage(`StoragePlugin`) |
|---|---|---|---|
| 税率 `getTariff()` | 市场税率 `market.getTariff().getModifiedValue()`(基类默认) | **恒 0** | **恒 0** |
| 合法性 | `shouldHaveCommodity = !market.isIllegal(com)`：只备货合法商品；交易时 `isIllegalOnSubmarket` 基类默认 = `market.isIllegal` | `isIllegalOnSubmarket` **恒 false**：什么都能交易 | 用户市场全部合法；中立势力仅舰船重载豁免，商品仍走基类 |
| 库存上限与稳定度 | `limit *= 0.25 + 0.75 * (stability/10)`：**稳定度越高货越多** (spec desc 原文) | `limit *= 0.25 + 0.75 * (1 - stability/10)`：**稳定度越低货越多** | 不参与经济，无商品流库存 |
| 经济参与 | 基类 `isParticipatesInEconomy() = true` | true | **false**：用户存取不影响经济 |
| 用户交易影响 | `getPlayerEconomyImpactMode() = PLAYER_SELL_ONLY` | `PLAYER_SELL_ONLY` | 无 (不参与经济，`reportPlayerMarketTransaction` 直接返回) |
| 专属行为 | `isOpenMarket() = true`；tooltip 在 SNEAK 模式提示 "需要开启应答器" | `isBlackMarket() = true`；走私嫌疑 tooltip(suspicion level 档位)；卖出蓝图触发延迟学习脚本 (`DelayedBlueprintLearnScript`) | `isFreeTransfer() = true`(免费转移，动词 Take/Leave)；一次性 5000 信用解锁 + 每月 `storageFreeFraction`(settings 键，= 0.01，即存值 1%/月，`Misc.getStorageFeeFraction` 读取)；**用户拥有的市场免费** |

基类 `BaseSubmarketPlugin` 的通用事实：`TRADE_IMPACT_DAYS = 120f`(用户交易修饰的持续天数)；

`getCargo()` **用到时才创建** unlimited cargo 并按 `submarket.getFaction()` 初始化 mothballed ships；`getCargoNullOk()` 直接返回字段，**可能为 null**；

`isBlackMarket()` 基类默认 = 市场势力对子市场势力敌对 (黑市插件覆写为恒 true)。

#### [库存 cargo 与合法性]

子市场库存是 `CargoAPI`：`getCargo()` 保证非 null(用到时才创建)，`getCargoNullOk()` 可能 null。**读取子市场库存前必须先判空或直接走 `getCargo()`** —— 直接用 `getCargoNullOk()` 返回值会 NPE。

合法性判定 `isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action)`：非 commodity stack 基类默认合法 (舰船走 `FleetMemberAPI` 重载)；`TransferAction` 只有 `PLAYER_BUY` / `PLAYER_SELL` 两向。

#### [用户交易对经济的影响]

交易发生后 UI 调用 `plugin.reportPlayerMarketTransaction(transaction)`(基类实现)：`isParticipatesInEconomy()` 为 false 直接返回 (storage 因此不影响经济)；

否则按 `getPlayerEconomyImpactMode()` 对**每个 commodity stack** 写 `CommodityOnMarketAPI.addTradeMod(Plus/Minus)`，持续 `TRADE_IMPACT_DAYS = 120` 游戏日。

open / black 都是 `PLAYER_SELL_ONLY` —— **用户卖出**给市场会正向影响该商品的经济单位 ("selling enough of a commodity will temporarily resolve a shortage"，spec desc 原文)，**用户买入**不产生使市场缺货的负向修饰 (`BlackMarketPlugin` 注释：防止刷买/卖循环)。

交易量的全局倍率 `economyPlayerTradeImpactMult`(settings 键，= 1)。

### 经济对象与商品流 (EconomyAPI)

子市场决定单个市场的交易入口，商品流何时重算则属于经济集合的职责。先给结论：经济推进器每月按 `economyIterPerMonth` 次迭代分布重算，只有当月最后一次迭代才写库存与价格；`nextStep` 系列与 `forceStockpileUpdate` 是两条手动重算入口。`EconomyAPI`(`com.fs.starfarer.api.campaign.econ.EconomyAPI`) 是经济集合接口，运行时按商品维护可达网络 (`CommodityMarketDataAPI`)。

#### [市场集合与商品注册表]

- 集合：`getMarketsCopy()`(快照)/ `getMarket(id)`(精确 id，无则 null)/ `getNumMarkets()` / `getMarkets(loc)` / `getLocationsWithMarkets()` / `getStarSystemsWithMarkets()`。
    - `getMarketsInGroup(group)` / `getMarketsWithSameGroup(market)`(经济组：同组市场互相可见且只互相交易，null 组同属一组)。
- 商品注册表：`getAllCommodityIds()` 返回全部已注册商品 id(来自 CSV spec 加载期的 `CommoditySpecAPI` 集合)；`getCommoditySpec(id)` 返回**全局规格** `CommoditySpecAPI`(基础价、econ unit、tag、demand class 等)。
    - **不等于**市场库存，读取的是全局 spec，不表示该 commodity 在某个市场有库存、供给或需求。
- `isSimMode()`：模拟模式 (战役/经济编辑器) 标记。

#### [经济推进循环 (商品流)]

`SectorAPI.getEconomy()` 的 `EconomyAPI.advance(amount)` 每帧被生涯引擎调用：`economyEnabled`(settings 键，= true) 时把 `amount` 换算成游戏日送入经济推进器；随后逐个 `market.advance(amount)`。

经济自身的商品流节奏 (经济推进器)：

1. **每月 `economyIterPerMonth` 次迭代** (settings 键 = 10)。跨月时重置剩余迭代数为 10，迭代间隔 = `(当月剩余天数) / 10` 游戏日。
2. 每次迭代的任务链 (主重算任务)：重算全部市场条件/产业 → 按商品逐个构建 `CommodityMarketDataAPI` 可达图 → **当月最后一次迭代**才 `updateStockpileAndPrice` 写库存与价格。
    - 随后依次执行市场重算、移民与整轮收尾。
3. 每次迭代结束触发 `reportEconomyTick`(`EconomyTickListener`)，跨月触发 `reportEconomyMonthEnd`。
4. 商品可达图 (`CommodityMarketDataAPI`)：每个商品一套，按 "本地生产 + 进口 + 短缺 + 低可达性" 拆解 `getAvailable()` 的修饰，隐藏市场跳过进口需求。
5. `updateStockpileAndPriceV2`(主重算任务)：重算 demand/greed 修饰、写 `setStockpile`(经济单位刻度)、按短缺/过剩设置价格倍率上下限 (`economyDeficitPriceIncrPerUnit` / `economyExcessPriceDecrPerUnit` 等)。
    - **供需均为 0 的商品用户买入价 × `economyNoDemandPriceMult`(0.5)**。

`economyIntervalnGameDays` 不能用来推断经济频率

settings.json 中 `economyIntervalnGameDays` 键 (值 30) 的原文注释是 `# not actually used by the economy, but used for a few other things`；真正节奏是上面的 `economyIterPerMonth` 迭代分布。

#### [手动重算入口]

- `nextStep()` / `doubleStep()` / `tripleStep()`：立即执行一/二/三步迭代 (含 `withStockpileUpdate=true`)。

`nextStep` 系列只允许在 UI 交互路径调用

"Should only be called from UI interactions, not from scripts that run in the background. Calling this method is likely to take longer than the time to render a single frame."

这些方法同步执行整轮迭代，**禁止**在后台脚本 (`EveryFrameScript`、每帧回调) 里调用。

违反后的可观察结果：单帧阻塞明显超过一帧渲染时间，大存档下肉眼可见的卡顿/掉帧 (加载器稳定化也是这套入口)。

- `forceStockpileUpdate(market)`：对市场全部非 `nonecon` 商品立即 `updateStockpileAndPrice`，适合 "改完供需想让库存立即反映" 的场景。

#### [EconomyUpdateListener]

`EconomyAPI.EconomyUpdateListener`(在 `EconomyAPI` 中声明) 三个方法：

- `commodityUpdated(String commodityId)`(每个商品一次，逐商品通知)；
- `economyUpdated()`(整轮结束一次，整轮收尾通知)；
- `isEconomyListenerExpired()`(返回 true 的监听器**由引擎自动移除**)。

监听器注册必须走 `EconomyAPI.addUpdateListener()`

**禁止加入 `SectorAPI.getListenerManager()`**，原文："Needs to be added via EconomyAPI.addUpdateListener(); should not be added to SectorAPI.getListenerManager()."

违反后的可观察结果：监听器永不回调且**无任何报错** (监听器只在经济迭代里被遍历)。

注意 `commodityUpdated` 的每次回调都是**同一经济步内**对单个商品的增量通知，`economyUpdated` 才是 "整轮完成"。

## 实现市场创建流程

本例从经济集合中选择一个既有市场，依次检查结构、子市场、商品与税率，最后挂载一个自过期经济监听器。以下局部片段均来自完整成品，按调用顺序逐步加入。

### 选择可巡检的市场

插入位置：`GearInMarketPicker.pick`。首选 ID 不存在时，回退到第一个拥有开放子市场的市场。

```java
MarketAPI market = economy.getMarket(preferredId);
if (market == null) {
	for (MarketAPI candidate : economy.getMarketsCopy()) {
		if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
			market = candidate;
			break;
		}
	}
}
return market;
```

完成信号：`jangala` 存在时稳定命中；改成不存在的 ID 后仍能返回一个带 `open_market` 的市场。

### 编排一次市场巡检

插入位置：`GearInMarketInspector.runInspection`。取到市场后按结构、子市场、商品和税率的顺序调用四个角色类。

```java
EconomyAPI economy = Global.getSector().getEconomy();
MarketAPI market = new GearInMarketPicker(targetMarketId).pick(economy);
if (market == null) {
	LOG.info("GearIn market inspection abort: no open-market market found");
	return;
}
GearInMarketStructureLog.log(market, economy);
GearInSubmarketLog.log(market);
GearInCommodityLog.log(market);
GearInTariffProbe.log(market);
```

完成信号：日志依次出现 structure、conditions、industries、submarket、commodity 与 tariff probe，不存在目标市场时有明确回退或中止记录。

### 安全读取子市场库存

插入位置：`GearInSubmarketLog.log` 的循环体。先检查 spec，再对 `getCargoNullOk()` 判空；巡检不会为了读取而创建库存。

```java
if (!market.hasSubmarket(specId)) {
	LOG.info("GearIn submarket " + specId + ": present=false");
	continue;
}
SubmarketAPI submarket = market.getSubmarket(specId);
CargoAPI cargoNullOk = submarket.getCargoNullOk();
float supplies = cargoNullOk == null ? 0f
		: cargoNullOk.getCommodityQuantity(Commodities.SUPPLIES);
```

完成信号：未初始化库存显示 `cargoNullOk=null`，已交互的子市场显示 `created`；两种情况都不会抛 NPE。

### 对照库存、供需与价格

插入位置：`GearInCommodityLog.log`。同一商品同时读取经济单位库存、可得量、供需上限与玩家交易价格。

```java
CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
LOG.info(String.format(
		"GearIn commodity %s: stockpile=%.1f available=%d supply=%d demand=%d buy=%.0f sell=%.0f",
		commodityId, commodity.getStockpile(), commodity.getAvailable(),
		commodity.getMaxSupply(), commodity.getMaxDemand(),
		market.getDemandPrice(commodityId, 100, true),
		market.getSupplyPrice(commodityId, 100, true)));
```

完成信号：`supplies` 与 `drugs` 各产生一行，价格随市场供需变化，不再等同于全局基础价。

### 修改税率并当帧回滚

插入位置：`GearInTariffProbe.log`。修饰 ID 固定，读回三个阶段后立即移除修饰。

```java {3}
MutableStat tariff = market.getTariff();
float before = tariff.getModifiedValue();
tariff.modifyFlat(TARIFF_MOD_ID, 0.05f);
float during = tariff.getModifiedValue();
tariff.unmodifyFlat(TARIFF_MOD_ID);
float after = tariff.getModifiedValue();
```

完成信号：日志严格呈现 `before → before + 0.05 → before`，存档中的市场税率没有残留修改。

### 注册自过期经济监听器

插入位置：巡检末尾与 `GearInEconomyTickLog`。监听器必须交给经济对象；整轮更新后置位，下一次过期检查自动移除。

```java {1}
economy.addUpdateListener(new GearInEconomyTickLog());

@Override
public void economyUpdated() {
	economyFired = true;
	LOG.info("GearIn economy listener economyUpdated (expiring)");
}

@Override
public boolean isEconomyListenerExpired() {
	return economyFired;
}
```

完成信号：下一经济步只出现一次首次商品更新与一次整轮更新日志，之后不再回调。

## 组装市场创建实现

结构、差异与节奏都清楚了，接下来把它们跑起来：在既有 MOD 布局与 `GearInModPlugin` 之上新增七个单角色类 (不新增任何资源、不新增任何注册项)，同处 `data.scripts.gearin.campaign` 包内协作。上文出现的每条必须遵守的规则都在首次展开处说明，本节的类按这些规则逐一落地。

巡检脚本在挂载后的第一帧执行**只读 + 即刻回滚**的序列：经济基线 → 目标市场选择 → 市场结构 → 三种子市场差异 → 商品库存与价格 → 税率瞬时修改并回滚 → 注册一个自过期的 `EconomyUpdateListener`(演示商品流通知)。

全部巡检不遗留任何持久状态 (税率回滚、监听器整轮通知后自过期、脚本自停)：

- `GearInMarketInspector`：巡检调度脚本 (`EveryFrameScript`)：注册判断 + 目标市场选择 + 经济基线 + 编排各日志器 + 注册自过期监听器；
- `GearInMarketPicker`：目标市场选择器：优先指定 id，查无时回退到第一个有开放子市场的市场；
- `GearInMarketStructureLog`：市场结构日志器：身份/势力/实体绑定/survey/开放状态/条件与产业 id 列表及人物数与同经济组市场数；
- `GearInSubmarketLog`：子市场差异日志器：open/black/storage 的税率、势力、plugin 类、cargo 用到时才创建的语义与三个标志；
- `GearInCommodityLog`：商品日志器：演示商品的库存、可得量、供需上限、买卖价与合法性；
- `GearInTariffProbe`：税率探针：瞬时 `modifyFlat` 并当帧 `unmodifyFlat` 回滚，记录 before/during/after；
- `GearInEconomyTickLog`：经济监听 (`EconomyUpdateListener`)：记录下一经济步的首次 `commodityUpdated` 与整轮 `economyUpdated`，之后自过期。

### GearInMarketInspector：巡检调度脚本

巡检入口 (`EveryFrameScript` 角色)：挂载后第一次 `advance()` 执行一次巡检序列并自停 (`isDone()=true`，引擎下一帧自动移除本脚本)。

**绑定**：注册入口为生涯回调 `onGameLoad`，persistent data 键 `REGISTER_MARK = "GearIn_MarketInspector"` 一次性注册标记 (沿用既有模式：每个注册项用各自的键，不并入插件的 `CLASS_MARK` 标记)。

### GearInMarketPicker：目标市场选择

目标市场选择器：优先按指定 id 取市场 (默认 `"jangala"`，原版核心世界，必存在)；id 为空或查无此市场时回退扫描，取第一个有 `open_market` 子市场的市场，仍找不到返回 null。

**绑定**：由 `GearInMarketInspector.runInspection()` 包内协作调用，无独立注册项。

### GearInMarketStructureLog：市场结构日志

市场结构日志器 (静态工具类)：输出目标市场的身份 / 势力 / 实体绑定 / survey / 开放状态、条件与产业 id 列表，以及市场人物数量与同经济组市场数。**绑定**：由 `GearInMarketInspector` 包内协作调用，无独立注册项。

### GearInSubmarketLog：子市场差异日志

子市场差异日志器 (静态工具类)：遍历 `open_market` / `black_market` / `storage` 三种原版子市场，输出税率、势力、plugin 类、cargo 用到时才创建语义与开放 / 黑市 / 免费转移标志；对缺失的子市场只输出 `present=false`。

**绑定**：由 `GearInMarketInspector` 包内协作调用，无独立注册项。

### GearInCommodityLog：商品日志

商品日志器 (静态工具类)：对 `supplies` / `drugs` 输出库存、可得量、供需上限、市场买入/卖出价与合法性 (价格方向以市场为主体)。**绑定**：由 `GearInMarketInspector` 包内协作调用，无独立注册项。

### GearInTariffProbe：税率探针

税率探针 (静态工具类)：对 `MarketAPI.getTariff()`(`MutableStat`) 做瞬时 `modifyFlat("GearIn_inspect_tariff", 0.05f)` 并当帧 `unmodifyFlat` 回滚，记录生效值 before / during / after。

演示 "修改高频参数观察结果变化" 的最小任务链，无持久残留。

**绑定**：由 `GearInMarketInspector` 包内协作调用，无独立注册项。

### GearInEconomyTickLog：经济监听

经济更新监听器 (`EconomyUpdateListener` 角色)：记录下一个经济步的第一次 `commodityUpdated` 与整轮 `economyUpdated`。

之后 `isEconomyListenerExpired()` 返回 true，引擎在下一个经济步自动从监听器列表移除；只持有两个 boolean，随存档序列化无副作用。

**绑定**：由 `GearInMarketInspector` 经 `economy.addUpdateListener(...)` 注册 —— **必须**走 `EconomyAPI.addUpdateListener`，不能加入 `SectorAPI.getListenerManager()`。

对应关系：

- 巡检模式：`implements EveryFrameScript` 沿用生涯一次性脚本模式：`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除。
- `REGISTER_MARK`：persistent data 标记键 (`"GearIn_MarketInspector"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不并入 `GearInModPlugin` 的 `CLASS_MARK` 标记 (旧档补注册规则见前置知识)。
- `GearInMarketPicker.pick`：`economy.getMarket(id)` 精确 id 查找 + `hasSubmarket(Submarkets.SUBMARKET_OPEN)` 回退，对应 "市场 id = economy.json entities 首实体 id" 的读取链路。
- `GearInSubmarketLog.log`：`submarket.getCargoNullOk()` 判空后 `getCommodityQuantity` —— 演示 "库存用到时才创建、`getCargoNullOk()` 可能为 null" 的必须遵守的规则。
- `GearInTariffProbe.log`：`modifyFlat("GearIn_inspect_tariff", 0.05f)` → `unmodifyFlat`；`getTariff()` 是 `MutableStat`(前置知识已介绍)，当帧回滚、无持久残留；这是 "修改高频参数观察结果变化" 的最小任务链第 4 步。
- `GearInEconomyTickLog.log`：`economy.addUpdateListener`；监听器**必须**走 `EconomyAPI.addUpdateListener`；`isEconomyListenerExpired()` 返回 true 后由引擎自动移除，不残留存档。

调用点 (工程片段，方法所在类归属插件生命周期主题，本节只演示调用，不改动既有文件)：

```java
    // GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInMarketInspector.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInMarketInspector.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInMarketInspector());
    }
```

想演示换目标市场 (最小任务链第 4 步的另一种做法)，注册前先设参数：

```java {2}
    GearInMarketInspector inspector = new GearInMarketInspector();
    inspector.setTargetMarketId("asharu"); // corvus 星系的独立势力市场 (economy.json 首实体 id 即市场 id)
    Global.getSector().addScript(inspector);
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/` 下的七个类 (可用 `jar tf` 核对)。

## 关键故障实验：经济监听器始终没有回调

监听器实现正确，但被注册到通用监听器管理器。

**局部故障片段：**

```java
// 故障片段：注册位置错误
Global.getSector().getListenerManager().addListener(new GearInEconomyTickLog());
```

**局部修正片段：**

```java
// 修正片段：交给经济对象
Global.getSector().getEconomy().addUpdateListener(new GearInEconomyTickLog());
```

实际症状：市场、商品流和其他经济事件正常推进，但 `commodityUpdated` 与 `economyUpdated` 永远没有日志，也没有注册异常。

原因：`EconomyUpdateListener` 只由 `EconomyAPI` 自己维护和遍历，通用监听器管理器不会派发这两个回调。

复验：修正注册后保持生涯未暂停，跨过下一次经济迭代；应先收到至少一次 `commodityUpdated`，再收到一次 `economyUpdated`，后续迭代不再回调。

## 验证方案

前置数据：既有 MOD 布局 [C6-S1] 与 `GearInModPlugin`([C6-S2]，含本节调用点片段)、七个巡检类。

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/` 下的七个类。
2. **新建生涯**：在 `starsector.log` 中观察 `GearIn market inspection` 相关记录。
3. **结构核对**：`structure` 行 `id=jangala`、`inEconomy=true`、`freePort=false`、`onOrAt=on`；三条 `submarket` 行 tariff 依次约 `0.300` / `0.000` / `0.000`，black 的 `faction=pirates`、storage 的 `freeTransfer=true`。
4. **经济监听器**：注册日志后保持未暂停游玩，几十真实秒内应出现 `GearIn economy listener first commodityUpdated` 与 `GearIn economy listener economyUpdated (expiring)`；继续观察该日志不重复出现；暂停 (空格) 期间不出现；恢复后出现。
5. **读档**：保存后读档，确认无 `inspection start` 日志 (`REGISTER_MARK` 标记已置位)；若监听器在保存前未触发，读档后继续等待下一个经济步。
6. **参数演示**：注册前 `inspector.setTargetMarketId("asharu")` 重新构建：`structure` 行 `id=asharu ... faction=independent`；`GearIn commodity drugs` 行 `illegal` 值随市场势力变化 (pirate/自由港市场为 false)。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/` 下的七个类 (`GearInMarketInspector` 等)。

- **新建生涯**：`starsector.log` 依次出现：

    ```text
       GearIn market inspection start: markets=NN commodities=33
    ```

    (`NN` 是存档市场数，以日志为准；`commodities=33` 是 原版商品规格数，`getAllCommodityIds()` 来自 CSV spec 加载期的 `CommoditySpecAPI` 集合)。随后出现：

    ```text
       GearIn market structure: id=jangala name=Jangala size=6 faction=hegemony inEconomy=true survey=FULL playerOwned=false freePort=false hidden=false onOrAt=on primaryEntity=Jangala
    ```

    `Jangala` 是 corvus 星系核心市场 (`econ/corvus.json`：faction `hegemony`、size 6)，`onOrAt=on`(主实体是行星，无 `station` tag)；`survey` 以实际存档为准。

- **条件与产业**：

    ```text
       GearIn market conditions: count=6 ids=[habitable, population_6, inimical_biosphere, organics_plentiful, farmland_adequate, extreme_weather] GearIn market industries: count=7 ids=[population, farming, spaceport, mining, heavybatteries, militarybase, starfortress_mid]
    ```

    (与 `econ/corvus.json` 的 `startingConditions` / `industries` 数组一致，以日志为准；其他市场看各自条目。)

- **三种子市场**：

    ```text
       GearIn submarket open_market: present=true name=Open Market tariff=0.300 faction=hegemony plugin=OpenMarketPlugin cargoNullOk=null supplies=0.0 open=true black=false freeTransfer=false GearIn submarket black_market: present=true name=Black Market tariff=0.000 faction=pirates plugin=BlackMarketPlugin cargoNullOk=null supplies=0.0 open=false black=true freeTransfer=false GearIn submarket storage: present=true name=Storage tariff=0.000 faction=player plugin=StoragePlugin cargoNullOk=null supplies=0.0 open=false black=false freeTransfer=true
    ```

    核对：open 税率 = 市场税率 (原版默认 `0.3`，`FactionSpecAPI.tariffFraction` 默认值)；black 与 storage 恒 `0.000`；black 的势力是 `pirates`(`submarkets.csv`)、storage 是 `player`；`cargoNullOk=null` 表示该子市场 cargo 尚未被创建 (用到时才创建语义)。若某个市场曾被交互过则为 `created` 并带 supplies 数量；`freeTransfer=true` 只出现在 storage。

- **商品** (数值随存档经济状态浮动，方向核对不变)：

    ```text
       GearIn commodity supplies: stockpile=NNNN.N available=NNN maxSupply=NNN maxDemand=NNN illegal=false demandPrice(100)=NNN supplyPrice(100)=NNN GearIn commodity drugs: stockpile=NN.N available=N maxSupply=N maxDemand=N illegal=true demandPrice(100)=NNN supplyPrice(100)=NNN
    ```

    `supplies` 的市场买入/卖出价在库存充足时接近基础价区间；`drugs` 对 hegemony 市场 `illegal=true`(非自由港)，开放市场不备货、黑市可交易 (`BlackMarketPlugin.isIllegalOnSubmarket` 恒 false)。

- **人物与经济组**：`GearIn market people: count=N`(Jangala 的 NPC 人物数，以日志为准)；`GearIn market econGroup: group=null sameGroupMarkets=M`，其中 `M` 是当前经济中 null 组市场总数。

- **税率回滚**：

    ```text
       GearIn tariff probe: before=0.300 during=0.350 after=0.300
    ```

    `during` 恒比 `before` 高 `0.05`，`after` 与 `before` 相等，修改已被当帧回滚。

- **经济监听器** (商品流通知的观察窗口)：注册日志后，在**下一个经济步** (每月 10 次迭代，间隔最多约当月剩余天数/10 个游戏日，未暂停时几十真实秒内) 出现：

    ```text
       GearIn economy listener first commodityUpdated: <某个商品 id> GearIn economy listener economyUpdated (expiring)
    ```

    之后不再出现 —— 监听器在整轮通知后由引擎自动移除 (`isEconomyListenerExpired()` 为 true)。暂停期间经济不推进，监听器日志会推迟到恢复后。

- **读档**：不重复执行 (`REGISTER_MARK` 标记已置位)，也不出现 `inspection start` 日志；经济监听器若在读档前未触发，读档后继续等待下一个经济步 (监听器只持 boolean，随存档恢复无副作用)。

- **参数演示**：注册前 `setTargetMarketId("asheru")`(不存在于原版的 id)→ 回退扫描生效，`structure` 行打印第一个有开放市场的市场而不是 `asheru`；改成 `"asharu"` → `structure` 行打印 `id=asharu ... faction=independent`。

- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

## 故障分析

- **市场查询返回 null**：只创建了市场对象，没有调用 `economy.addMarket`，或市场 ID 写错。

    - **解决方案**：补齐注册，以 `econ/*.json` 的首实体 ID 为准。

- **实体没有市场交互**：市场已加入经济集合，但没有执行 `entity.setMarket(market)`。

    - **解决方案**：执行 `entity.setMarket(market)` 完成实体绑定。

- **读取子市场库存时抛 NPE**：`getCargoNullOk()` 可能返回 null。

    - **解决方案**：判空，或需要自动创建库存时使用 `getCargo()`。

- **后台脚本调用 `nextStep()` 后卡顿**：该调用会同步执行整轮经济迭代。

    - **解决方案**：后台逻辑改用 `forceStockpileUpdate(market)` 或等待自然经济步。

- **`EconomyUpdateListener` 从不回调**：监听器注册到了 Sector listener 表。

    - **解决方案**：改用 `economy.addUpdateListener(...)`。

- **市场价格与交易界面不一致**：`CommoditySpecAPI.getBasePrice()` 只是全局基础价。

    - **解决方案**：买卖价格读取市场的 `getDemandPrice` 与 `getSupplyPrice`。

- **删除一个条件却把同 ID 全删**：`removeCondition(id)` 面向 spec ID，会移除所有同 ID 实例。

    - **解决方案**：保存 `addCondition` 返回的 token，改用 `removeSpecificCondition(token)`。

- **每帧读取市场 memory 导致回调风暴**：`getMemory()` 会触发全部插件的 `updateMarketFacts`。

    - **解决方案**：纯读取使用 `getMemoryWithoutUpdate()`。

- **读取管理员后存档多出人物**：`getAdmin()` 会在缺失时创建默认管理员。

    - **解决方案**：读取前确认市场已有 admin，避免触发创建副作用。

- **经济频率观察与 `economyIntervalnGameDays` 不符**：该设置键不参与实际经济推进。

    - **解决方案**：按 `economyIterPerMonth` 和经济迭代日志判断节奏。

- **关闭自由港后黑市仍存在**：自由港条件与黑市子市场是两条独立链。

    - **解决方案**：使用 `removeSubmarket` 处理黑市，不要把 `setFreePort(false)` 当作黑市开关。

- **交易后立即读取库存看不到变化**：交易影响通过 120 游戏日的 trade mod 缓慢衰减。

    - **解决方案**：等待一个经济步后再读取。

## 总结

本节把市场、子市场与经济对象讲成三条链：市场链从 `createMarket` 经收尾调用把市场注册进经济集合并绑到实体；子市场链从 `submarkets.csv` 的 spec 生成插件实例，差异集中在税率、合法性、库存与稳定度的关系和经济参与；经济链由 `EconomyAPI.advance` 每帧喂给经济推进器，每月 10 步、月末步写库存与价格。排错时抓住一个分工即可少走弯路：市场持有结构与交易入口，经济集合负责重算，子市场插件决定单个入口的行为差异 —— 市场是实体绑定的经济节点，不是经济重算的执行者。

记忆要点：

- 市场 id = `econ/*.json` `entities` 首实体 id；`getMarket(id)` 精确匹配、查无返回 null。
- `addCondition` 返回 token：删具体实例用 `removeSpecificCondition(token)`，`removeCondition(id)` 删同 id 全部；条件与产业的 spec id 不存在时，添加入口都会抛出运行时异常。
- `getCargoNullOk()` 可能 null；`getMemory()` 触发全插件回调；`getAdmin()` 用到时才创建管理员：三个 "用到时才创建/副作用" 入口。
- `economyIterPerMonth = 10`、`economyIntervalnGameDays` 不参与经济；`nextStep` 系列禁止后台调用；监听器必须走 `addUpdateListener`。
- 自由港 → `isIllegal` 全 false；隐藏市场跳过进口需求；同经济组市场只互相可见与交易。

地点容器与实体挂载承载着市场主实体，舰队成员是生涯对象族的另一支；本节的 `REGISTER_MARK` 注册模式沿用生涯对象族的既有做法。市场的行为由三条链共同决定 —— 子市场的插件差异、经济循环的迭代节奏、开放状态的语义开关；产业供需、交易界面与殖民地管理都沿这些入口展开。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场接口全貌：身份、实体绑定、条件、产业、商品、价格、人物、开放状态与每帧推进。
- `com.fs.starfarer.api.campaign.econ.SubmarketAPI`：子市场运行时入口 (spec、plugin、cargo、税率、合法性)。
- `com.fs.starfarer.api.campaign.SubmarketPlugin`：子市场插件角色：税率、合法性、经济参与、用户交易影响 (`PlayerEconomyImpactMode` / `TransferAction`)。
- `com.fs.starfarer.api.campaign.econ.EconomyAPI`：经济集合、商品注册表、手动重算入口与 `EconomyUpdateListener`(注册必须经 `addUpdateListener`)。
- `com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI`：单商品数据：库存、可得量、供需上限、交易修饰。
- `com.fs.starfarer.api.campaign.econ.CommoditySpecAPI`：全局商品规格：基础价、econ unit、demand class。
- `com.fs.starfarer.api.campaign.econ.MarketConditionAPI`：条件实例：spec id 与唯一 token。
- `com.fs.starfarer.api.impl.campaign.ids.Submarkets`：五个原版子市场 spec id 常量。
- `com.fs.starfarer.api.impl.campaign.ids.Commodities`：原版商品 id 常量 (`SUPPLIES` / `DRUGS`)。
- `com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin`：子市场基类：交易影响天数、cargo 用到时才创建、默认税率与合法性。
- `com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin` / `BlackMarketPlugin` / `StoragePlugin`：三种原版子市场的插件差异。
- `com.fs.starfarer.api.combat.MutableStat`：税率等修饰统计：`modifyFlat` / `unmodifyFlat` / `getModifiedValue`。
- `com.fs.starfarer.api.FactoryAPI`：`createMarket` 创建市场对象 (只建对象，不注册)。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：`getMarket` / `setMarket` 实体侧绑定。
- `com.fs.starfarer.api.util.Misc`：`getStorageFeeFraction` 读取 `storageFreeFraction` 存值费率。
- `data/campaign/econ/economy.json`：经济入口配置：`starSystems` 列表与 `defaultTariff`。
- `data/campaign/econ/corvus.json`：示例市场条目 (`jangala` / `asharu`)。
- `data/campaign/submarkets.csv`：五个原版子市场 spec(`script` 列即 plugin)。
- `data/campaign/market_conditions.csv`：条件 spec(含 `free_market`)。
- `data/config/settings.json`：经济参数键 (`economyIterPerMonth`、`economyPlayerTradeImpactMult`、`storageFreeFraction` 等)。

## 完整最小例

::: details 完整成品：GearInMarketInspector

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * 市场巡检脚本 (EveryFrameScript 角色)：挂载后第一次 advance() 执行一次市场/子市场/
 * 经济对象巡检序列并自停 (isDone()=true，引擎下一帧自动移除本脚本)。注册入口：生涯回调
 * onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控
 * (沿用既有模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 */
public class GearInMarketInspector implements EveryFrameScript {

    /** 存档持久化标记：本巡检是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_MarketInspector";

    private static final Logger LOG = Global.getLogger(GearInMarketInspector.class);

    /** 巡检目标市场 id；默认 "jangala"(原版核心世界，必存在)。 */
    private String targetMarketId = "jangala";

    /** 巡检是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInMarketInspector() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    /** 修改巡检目标市场；改回 null 时回退到 "第一个有开放市场的市场"。 */
    public void setTargetMarketId(String targetMarketId) {
        this.targetMarketId = targetMarketId;
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runInspection();
    }

    private void runInspection() {
        EconomyAPI economy = Global.getSector().getEconomy();

        // --- 经济基线 ---
        LOG.info(String.format(
                "GearIn market inspection start: markets=%d commodities=%d",
                economy.getNumMarkets(), economy.getAllCommodityIds().size()));

        MarketAPI market = new GearInMarketPicker(targetMarketId).pick(economy);
        if (market == null) {
            LOG.info("GearIn market inspection abort: no open-market market found");
            return;
        }

        GearInMarketStructureLog.log(market, economy);
        GearInSubmarketLog.log(market);
        GearInCommodityLog.log(market);
        GearInTariffProbe.log(market);

        economy.addUpdateListener(new GearInEconomyTickLog());
        LOG.info("GearIn market inspection registered economy update listener (self-expires)");
        LOG.info("GearIn market inspection done");
    }
}
```

:::

::: details 完整成品：GearInMarketPicker

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 目标市场选择器：优先按指定 id 取市场；id 为空或查无此市场时回退扫描，
 * 取第一个有开放市场 (open_market 子市场) 的市场，仍找不到返回 null。
 * 供巡检脚本 GearInMarketInspector 编排时调用。
 */
public class GearInMarketPicker {

    /** 首选市场 id；null 或查无此市场时走回退扫描。 */
    private final String preferredId;

    public GearInMarketPicker(String preferredId) {
        this.preferredId = preferredId;
    }

    /** 返回首选市场；不可用时回退扫描，仍找不到返回 null。 */
    public MarketAPI pick(EconomyAPI economy) {
        MarketAPI market = economy.getMarket(preferredId);
        if (market == null) {
            for (MarketAPI candidate : economy.getMarketsCopy()) {
                if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                    market = candidate;
                    break;
                }
            }
        }
        return market;
    }
}
```

:::

::: details 完整成品：GearInMarketStructureLog

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

/**
 * 市场结构日志器：输出目标市场的身份/势力/实体绑定/survey/开放状态/条件与产业 id 列表，
 * 以及市场人物数量与同经济组市场数。由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInMarketStructureLog {

    private static final Logger LOG = Global.getLogger(GearInMarketStructureLog.class);

    private GearInMarketStructureLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market, EconomyAPI economy) {
        LOG.info(String.format(
                "GearIn market structure: id=%s name=%s size=%d faction=%s inEconomy=%s survey=%s playerOwned=%s freePort=%s hidden=%s onOrAt=%s primaryEntity=%s",
                market.getId(), market.getName(), market.getSize(), market.getFactionId(),
                market.isInEconomy(), market.getSurveyLevel(), market.isPlayerOwned(),
                market.isFreePort(), market.isHidden(), market.getOnOrAt(),
                market.getPrimaryEntity() == null ? "null" : market.getPrimaryEntity().getName()));

        List<MarketConditionAPI> conditions = market.getConditions();
        StringBuilder conditionIds = new StringBuilder();
        for (MarketConditionAPI condition : conditions) {
            if (conditionIds.length() > 0) {
                conditionIds.append(", ");
            }
            conditionIds.append(condition.getId());
        }
        LOG.info(String.format(
                "GearIn market conditions: count=%d ids=[%s]",
                conditions.size(), conditionIds));

        List<Industry> industries = market.getIndustries();
        StringBuilder industryIds = new StringBuilder();
        for (Industry industry : industries) {
            if (industryIds.length() > 0) {
                industryIds.append(", ");
            }
            industryIds.append(industry.getId());
        }
        LOG.info(String.format(
                "GearIn market industries: count=%d ids=[%s]",
                industries.size(), industryIds));

        LOG.info(String.format(
                "GearIn market people: count=%d",
                market.getPeopleCopy().size()));
        LOG.info(String.format(
                "GearIn market econGroup: group=%s sameGroupMarkets=%d",
                market.getEconGroup(), economy.getMarketsWithSameGroup(market).size()));
    }
}
```

:::

::: details 完整成品：GearInSubmarketLog

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 子市场差异日志器：遍历 open_market/black_market/storage 三种原版子市场，
 * 输出税率、势力、plugin 类、cargo 惰性创建语义与开放/黑市/免费转移标志。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInSubmarketLog {

    private static final Logger LOG = Global.getLogger(GearInSubmarketLog.class);

    private static final String[] SPEC_IDS = {
            Submarkets.SUBMARKET_OPEN,
            Submarkets.SUBMARKET_BLACK,
            Submarkets.SUBMARKET_STORAGE,
    };

    private GearInSubmarketLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        for (String specId : SPEC_IDS) {
            if (!market.hasSubmarket(specId)) {
                LOG.info("GearIn submarket " + specId + ": present=false");
                continue;
            }
            SubmarketAPI submarket = market.getSubmarket(specId);
            SubmarketPlugin plugin = submarket.getPlugin();
            CargoAPI cargoNullOk = submarket.getCargoNullOk();
            float supplies = 0f;
            if (cargoNullOk != null) {
                supplies = cargoNullOk.getCommodityQuantity(Commodities.SUPPLIES);
            }
            LOG.info(String.format(
                    "GearIn submarket %s: present=true name=%s tariff=%.3f faction=%s plugin=%s cargoNullOk=%s supplies=%.1f open=%s black=%s freeTransfer=%s",
                    specId, submarket.getNameOneLine(), submarket.getTariff(),
                    submarket.getFaction().getId(), plugin.getClass().getSimpleName(),
                    cargoNullOk == null ? "null" : "created", supplies,
                    plugin.isOpenMarket(), plugin.isBlackMarket(), plugin.isFreeTransfer()));
        }
    }
}
```

:::

::: details 完整成品：GearInCommodityLog

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * 商品日志器：对演示商品输出库存、可得量、供需上限、市场买入/卖出价与合法性
 * (价格方向以市场为主体：demandPrice = 市场买入 = 玩家卖出)。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInCommodityLog {

    private static final Logger LOG = Global.getLogger(GearInCommodityLog.class);

    private static final String[] COMMODITY_IDS = {
            Commodities.SUPPLIES,
            Commodities.DRUGS,
    };

    private GearInCommodityLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        for (String commodityId : COMMODITY_IDS) {
            CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
            LOG.info(String.format(
                    "GearIn commodity %s: stockpile=%.1f available=%d maxSupply=%d maxDemand=%d illegal=%s demandPrice(100)=%.0f supplyPrice(100)=%.0f",
                    commodityId, commodity.getStockpile(), commodity.getAvailable(),
                    commodity.getMaxSupply(), commodity.getMaxDemand(),
                    market.isIllegal(commodityId),
                    market.getDemandPrice(commodityId, 100, true),
                    market.getSupplyPrice(commodityId, 100, true)));
        }
    }
}
```

:::

::: details 完整成品：GearInTariffProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * 税率探针：对市场税率 (MutableStat) 做瞬时 modifyFlat 并当帧 unmodifyFlat 回滚，
 * 记录生效值 before/during/after，演示 "修改高频参数观察结果变化" 的最小任务链。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用，无持久残留。
 */
public class GearInTariffProbe {

    private static final Logger LOG = Global.getLogger(GearInTariffProbe.class);

    private static final String TARIFF_MOD_ID = "GearIn_inspect_tariff";

    private GearInTariffProbe() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        MutableStat tariff = market.getTariff();
        float before = tariff.getModifiedValue();
        tariff.modifyFlat(TARIFF_MOD_ID, 0.05f);
        float during = tariff.getModifiedValue();
        tariff.unmodifyFlat(TARIFF_MOD_ID);
        float after = tariff.getModifiedValue();
        LOG.info(String.format(
                "GearIn tariff probe: before=%.3f during=%.3f after=%.3f",
                before, during, after));
    }
}
```

:::

::: details 完整成品：GearInEconomyTickLog

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;

/**
 * 经济更新监听器 (EconomyUpdateListener 角色)：记录下一个经济步的第一次
 * commodityUpdated 与整轮 economyUpdated，之后 isEconomyListenerExpired() 返回 true，
 * 引擎在下一个经济步自动从监听器列表移除。
 *
 * 只持有两个 boolean，无外部引用，随存档序列化无副作用。注册入口：必须经
 * EconomyAPI.addUpdateListener() 注册，不能加入 SectorAPI.getListenerManager()。
 */
public class GearInEconomyTickLog implements EconomyUpdateListener {

    private static final Logger LOG = Global.getLogger(GearInEconomyTickLog.class);

    private boolean commodityFired = false;
    private boolean economyFired = false;

    @Override
    public void commodityUpdated(String commodityId) {
        if (!commodityFired) {
            commodityFired = true;
            LOG.info("GearIn economy listener first commodityUpdated: " + commodityId);
        }
    }

    @Override
    public void economyUpdated() {
        if (!economyFired) {
            economyFired = true;
            LOG.info("GearIn economy listener economyUpdated (expiring)");
        }
    }

    @Override
    public boolean isEconomyListenerExpired() {
        return economyFired;
    }
}
```

:::
