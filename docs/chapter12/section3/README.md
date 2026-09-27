# 注册市场商品

本节从 `commodities.csv` 注册一件 GearIn 商品，分别读取它的全局规格和某个市场上的运行状态，再用市场价格接口确认买入与卖出方向。商品的描述、库存生成和产业供需仍由各自的数据与系统负责。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 在 `commodities.csv` 中注册稳定的商品 ID；
- 解释 CSV 字段如何进入 `CommoditySpecAPI`；
- 区分全局商品规格和市场商品状态；
- 读取库存、供需、贸易调节、合法性和市场价格；
- 判断商品注册、描述挂接和势力规则是否分别生效。

## 前置知识

- [C1-S1] 的 MOD 目录、CSV 合并和 UTF-8 无 BOM 规则；
- [C3-S2] 的势力文件与 `illegalCommodities` 字段；
- [C12-S1] 的市场对象和市场查询入口；
- 能够启用 Console Commands 并进入生涯市场。

## 注册商品身份

商品首先要有一个稳定的 ID。`name` 是玩家看到的显示名称，`id` 是代码、势力规则和市场数据共同使用的键。GearIn 本节继续使用 `GearIn_tempered_alloy`，它代表一件使用原版工业需求类别的回火合金。

文件位置是 `data/campaign/commodities.csv`。MOD 只提交自己的数据行，表头和原版表头保持一致；重复 ID 会让加载阶段无法建立唯一规格。

```csv
name,id,demand class,base price,export value,price variability,utility,origin,tags,stack size,cargo space,icon,sound id,sound id drop,order,economyTier,econUnitOrigUnused,econUnit,baseRaidDanger,eU * base value,iconWidthMult,plugin,desc
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,108000,1,,
```

加载成功后，`Global.getSettings().getCommoditySpec("GearIn_tempered_alloy")` 能取得一个 `CommoditySpecAPI`。这一步只建立商品规格；市场是否有库存、势力是否禁止交易，要到后续对象上分别确认。

## 配置 CSV 字段

字段可以按四组理解。身份字段决定代码如何找到商品；经济字段决定市场如何把商品纳入需求和价格计算；货舱字段决定玩家如何携带和看到它；扩展字段则把图像、声音或插件接到已经注册的商品上。

### 身份与显示

`name` 写入显示名称，`id` 是稳定键，`origin` 保存来源标识，`tags` 是可供规则和筛选使用的标签集合。标签以逗号分隔，`CommoditySpecAPI.hasTag()` 会对单个标签进行判断。

### 经济与价格

`demand class` 指向已有的需求类别，`base price` 是商品规格的基础价格，`export value` 是出口价值，`price variability` 表示价格波动范围，`utility` 参与商品效用计算。需求类别必须是游戏已经认识的类别；`heavy_machinery` 能让本例沿用原版工业经济分类。

### 货舱表现

`stack size` 是单个栈允许的数量，`cargo space` 是单位货物占用的货舱空间。`icon`、`sound id` 和 `sound id drop` 决定货舱与交易界面的视觉和音效资源。空间与堆叠是两个字段，数量能堆叠不代表单位空间为零。

### 经济规模与扩展

`economyTier`、`econUnitOrigUnused`、`econUnit` 和 `eU * base value` 服务于经济规模换算；`baseRaidDanger` 提供突袭危险度基础值。商品加载器不读取 `plugin` 列，`desc` 也不承担货舱长描述的挂接；商品名称与货舱描述文本来自不同数据入口，本节只确认规格字段。

::: tip 先验证 ID，再验证经济

商品 ID、需求类别和资源路径属于不同加载环节。先在日志中确认 `GearIn_tempered_alloy` 建立了规格，再检查市场对象和交易界面，故障定位会比同时修改多组字段更直接。

:::

## 读取全局商品规格

`CommoditySpecAPI` 描述这种商品的全局规格。它不代表某一个市场的库存，也不保存某个市场当前的买卖价。最常用的读取方式是从设置或经济对象取得规格，再按用途读取身份、价格、货舱和分类。

```java
CommoditySpecAPI spec = Global.getSettings().getCommoditySpec("GearIn_tempered_alloy");
LOG.info(String.format("spec id=%s name=%s demand=%s base=%.0f stack=%d space=%.1f",
        spec.getId(), spec.getName(), spec.getDemandClass(), spec.getBasePrice(),
        spec.getStackSize(), spec.getCargoSpace()));
```

身份和显示 getter 包括 `getId()`、`getName()`、`getLowerCaseName()`、`getOrigin()`、`getIconName()` 和 `getIconLargeName()`。其中 `getId()` 适合日志和规则引用，`getName()` 适合玩家可见文本，图标 getter 返回资源路径或图标标识。

经济 getter 包括 `getBasePrice()`、`getExportValue()`、`getPriceVariability()`、`getDemandClass()`、`getUtility()`、`getEconomyTier()` 和 `getEconUnit()`。这些值来自商品规格，不能直接当成某个市场已经计算出的最终价格。

货舱与资源分类 getter 包括 `getStackSize()`、`getCargoSpace()`、`getIconWidthMult()`、`getSoundId()` 和 `getSoundIdDrop()`。`isPersonnel()`、`isFuel()`、`isSupplies()` 能帮助通用代码识别原版特殊资源；普通商品通常返回 `false`。

标签和类型判断使用 `getTags()`、`hasTag()`、`isPrimary()`、`isExotic()`、`isMeta()` 与 `isNonEcon()`。这些判断适合筛选和显示，不替代市场对象上的库存和合法性状态。

`setName()`、`setIconName()`、`setDemandClass()`、`setBasePrice()`、`setExportValue()` 和 `setBaseDanger()` 会改变运行时规格。它们适合明确的全局重配置；临时查看市场状态时保持规格只读，避免把一次运行时修改误认为 CSV 已经生效。

规格对象还提供来源 Mod 查询能力。来源信息适合排查同名 ID 来自哪个数据包，但它不能代替 `getId()` 作为业务键。规则文件、货舱数据和市场查询都应继续使用稳定 ID。

商品分类判断也要和市场状态分开。`isFuel()`、`isSupplies()` 和 `isPersonnel()` 说明商品在通用物流代码中的特殊角色；`isNonEcon()`、`isMeta()` 和 `isExotic()` 则说明它参与经济计算的方式。它们都来自规格，不能直接说明当前市场有没有这种商品。

`getTags()` 返回标签集合，适合用于筛选或调试。标签是数据声明，不是市场库存，也不会自动替代势力的 `illegalCommodities` 规则。需要判断交易是否合法时，继续读取市场对象的合法性 API。

因此，规格日志通常回答商品注册成了什么，市场日志回答这个市场现在怎样处理它。把两组字段放在同一行时，应保留商品 ID 作为连接键，避免把全局基础值和市场即时值混成一个数字。

## 读取市场商品状态

`CommodityOnMarketAPI` 描述这件商品在这个市场上的当前状态。通过 `MarketAPI.getCommodityData(id)` 取得它，再用 `getCommodity()` 回到全局 `CommoditySpecAPI`，用 `getMarket()` 确认所属市场。

```java
CommodityOnMarketAPI onMarket = market.getCommodityData("GearIn_tempered_alloy");
CommoditySpecAPI spec = onMarket.getCommodity();
LOG.info("market=" + onMarket.getMarket().getId() + " spec=" + spec.getId());
```

库存相关 getter 是 `getStockpile()`、`getAvailable()`、`getDeficitQuantity()` 和 `getExcessQuantity()`。`stockpile` 是市场库存，`available` 是经过市场经济换算后可交易的数量；赤字与盈余描述市场相对供需的位置，不能简单等同于库存正负。

供需相关 getter 是 `getMaxSupply()`、`getMaxDemand()`、`getDemand()` 和 `getDemandValue()`。`updateMaxSupplyAndDemand()` 会按当前市场状态重新计算上下限，通常由经济更新流程调用；读取时应记录计算后的实际值。

市场对象还提供 `getUtilityOnMarket()`、`isPersonnel()`、`isFuel()`、`isNonEcon()`、`isMeta()`、`getExportIncome()` 和 `getCommodityMarketData()`。其中 `getUtilityOnMarket()` 已经考虑市场上的商品效用，适合与市场状态一起记录。

## 分解贸易调节的正负方向

经济事件会通过 trade modifier 改变商品的可交易状态。`getTradeMod()` 是综合贸易调节，`getTradeModPlus()` 只保留正向部分，`getTradeModMinus()` 只保留负向部分；`getCombinedTradeModQuantity()` 给出合并后的数量影响。

贸易调节是数量方向的附加状态。它可能来自事件、产业或其他市场效果，持续时间和来源由写入方管理。调节结果变化时，`available`、赤字/盈余和最终价格可能一起变化，但这不代表 `stockpile` 已经被同量修改。

读取调节器时要区分原始值和修正值。`MutableStatWithTempMods` 能继续提供修正后的统计结果，`getCombinedTradeModQuantity()` 则适合在日志中记录合并后的数量；页面示例只记录结果，不把临时修正保存成商品规格字段。

```java
float tradeQuantity = onMarket.getCombinedTradeModQuantity();
float modValue = onMarket.getTradeMod().getModifiedValue();
LOG.info(String.format("trade quantity=%.1f modified value=%.1f",
        tradeQuantity, modValue));
```

```java
LOG.info(String.format("trade mod=%.1f plus=%.1f minus=%.1f combined=%.1f",
        onMarket.getTradeMod().getModifiedValue(),
        onMarket.getTradeModPlus().getModifiedValue(),
        onMarket.getTradeModMinus().getModifiedValue(),
        onMarket.getCombinedTradeModQuantity()));
```

`addTradeMod()`、`addTradeModPlus()` 和 `addTradeModMinus()` 是写入入口，分别对应综合、正向和负向调节，并接受来源、数量和持续天数。它们属于经济事件或市场效果的职责；观察器只读取当前调节结果，不把调节器当成永久库存修改。

## 区分市场买入价与卖出价

商品规格提供基础价格，市场商品对象提供库存和供需状态，实际交易价格由 `MarketAPI` 根据数量、市场状态和玩家价格标志计算。`getDemandPrice(id, quantity, isPlayerPrice)` 是需求方向价格，表示市场买入、玩家卖出；`getSupplyPrice(id, quantity, isPlayerPrice)` 是供给方向价格，表示市场卖出、玩家买入。

```java
float demandPrice = market.getDemandPrice("GearIn_tempered_alloy", 100, true);
float supplyPrice = market.getSupplyPrice("GearIn_tempered_alloy", 100, true);
LOG.info(String.format("price for 100: demand=%.0f supply=%.0f", demandPrice, supplyPrice));
```

价格随着数量变化，不能只读取一次基础价格就推断整笔交易。`isPlayerPrice` 还会影响玩家交易方向的修正；页面验证时应同时记录商品 ID、数量和两个价格，避免把需求价与供给价写反。

市场价格读取应该和市场对象使用同一个商品 ID。不要先用规格对象的名称查找价格，也不要把 `getExportValue()` 当成玩家在市场界面看到的价格。基础价格是规格输入，需求、供给、贸易调节和数量共同参与最终计算。

同一市场中，需求方向与供给方向可以有不同结果。玩家卖出时使用需求方向，玩家买入时使用供给方向；日志字段应直接写出方向名称，避免只记录两个没有语义的浮点数。

```java
String id = onMarket.getId();
float sellToMarket = market.getDemandPrice(id, 100, true);
float buyFromMarket = market.getSupplyPrice(id, 100, true);
LOG.info(String.format("%s sell=%.0f buy=%.0f", id, sellToMarket, buyFromMarket));
```

## 判断商品交易合法性

`CommodityOnMarketAPI.isIllegal()` 和 `isIllegalAssumePrimary()` 直接反映市场对商品的合法性判断，`isSupplyLegal()` 与 `isDemandLegal()` 则分别说明供给和需求方向是否允许。势力文件的 `illegalCommodities` 数组使用同一个商品 ID：

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "GearIn_tempered_alloy"
],
```

进入 GearIn 市场后，市场对象会把势力规则转换成运行时合法性状态。商品规格已加载、货舱描述已挂接、势力规则已生效是三条独立链路；一条链路正常不能代替另外两条。

`isIllegal()` 是市场针对当前商品的直接判断，`isIllegalAssumePrimary()` 会按主商品规则进行判断；两者适用于不同的规则查询场景。`isSupplyLegal()` 和 `isDemandLegal()` 则把合法性拆成供给与需求方向，适合在交易界面或交易脚本中分别给出提示。

如果商品已经注册但市场对象为空，合法性 API 没有读取意义。先确认 `CommodityOnMarketAPI` 存在，再判断 `isIllegal()`；如果市场对象存在而规则状态异常，沿着势力 ID 和 `illegalCommodities` 逐字检查。

## 规格与市场数据的边界

规格 setter 适合全局配置，市场库存 setter 适合明确的市场事件或测试流程，贸易调节 setter 则应由产生经济效果的代码管理。观察器读取 `setStockpile()`、`setMaxSupply()`、`setMaxDemand()` 的对应结果即可，不在本页临时改写市场状态。

市场对象可能尚未为某个 ID 建立数据。调用 `market.getCommodityData(id)` 后先检查返回值，再读取状态；不存在时说明商品没有接入该市场的经济集合，不能把它误判成库存为零。

市场数据的生命周期也和规格不同。规格通常在加载时建立，并在整个运行期间被多个市场共享；市场商品对象属于一个具体市场，会随着经济步推进、事件效果和交易行为变化。保存规格引用用于显示是合理的，保存市场状态并跨市场复用则会把对象归属弄错。

如果一个市场没有该商品的市场数据，仍然可以通过设置取得规格并显示商品名称；只有在 `CommodityOnMarketAPI` 存在时，代码才能记录库存、供需和合法性。通用日志因此应分别记录规格存在和市场接入，而不是用一个空值掩盖两种状态。

```java
CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
CommodityOnMarketAPI data = market.getCommodityData(commodityId);
LOG.info(String.format("commodity=%s spec=%s marketData=%s",
        commodityId, spec != null, data != null));
```

这条判断也适用于控制台验证。控制台把商品加入玩家货舱，只能证明规格和货舱 ID 可用；它不会自动证明目标市场已经建立了需求、供给和价格数据。市场界面与日志需要分别观察，才能确认商品已经进入经济对象。

读取市场对象时还应保留市场 ID。相同商品在不同市场可能有不同的库存、供需上限、合法性和价格；只记录商品 ID 会丢掉最重要的对象归属。

市场 ID 和商品 ID 组成了观察记录的最小定位键。日志、调试面板和后续事件都可以沿这两个 ID 回到正确对象。

这种定位方式也让多个市场之间的对照日志保持可读。

## 完整最小例

下面的类由调用方传入目标市场，只负责同时记录全局规格、市场状态、贸易调节、合法性和买卖价格。

::: details 完整成品：GearInCommodityProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class GearInCommodityProbe {
    private static final Logger LOG = Global.getLogger(GearInCommodityProbe.class);
    private static final String[] COMMODITY_IDS = {
            "GearIn_tempered_alloy",
            "supplies",
            "drugs"
    };

    private GearInCommodityProbe() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        for (String commodityId : COMMODITY_IDS) {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
            CommodityOnMarketAPI onMarket = market.getCommodityData(commodityId);
            if (spec == null || onMarket == null) {
                LOG.warn("GearIn commodity unavailable: " + commodityId);
                continue;
            }
            LOG.info(String.format(
                    "GearIn commodity %s: name=%s demand=%s base=%.0f stack=%d space=%.1f tags=%s stockpile=%.1f available=%d maxSupply=%d maxDemand=%d utility=%.2f deficit=%d excess=%d tradeMod=%.1f illegal=%s supplyLegal=%s demandLegal=%s demandPrice(100)=%.0f supplyPrice(100)=%.0f",
                    spec.getId(), spec.getName(), spec.getDemandClass(), spec.getBasePrice(),
                    spec.getStackSize(), spec.getCargoSpace(), spec.getTags(),
                    onMarket.getStockpile(), onMarket.getAvailable(), onMarket.getMaxSupply(),
                    onMarket.getMaxDemand(), onMarket.getUtilityOnMarket(),
                    onMarket.getDeficitQuantity(), onMarket.getExcessQuantity(),
                    onMarket.getCombinedTradeModQuantity(), onMarket.isIllegal(),
                    onMarket.isSupplyLegal(), onMarket.isDemandLegal(),
                    market.getDemandPrice(commodityId, 100, true),
                    market.getSupplyPrice(commodityId, 100, true)));
        }
    }
}
```

:::

## 验证方案

前置数据：启用包含本节 `commodities.csv` 注册行和 `GearInCommodityProbe` 类的 GearIn MOD，并使用拥有 `gear_industry` 市场且势力 `illegalCommodities` 已含 `GearIn_tempered_alloy` 的测试存档，同时已启用 Console Commands 控制台。

1. 启动游戏并进入 GearIn 市场，确认 `commodities.csv` 已经加载。
2. 执行 `additem GearIn_tempered_alloy 10`，打开货舱和交易界面。
3. 由调用方把当前市场传给 `GearInCommodityProbe.log(market)`，查看日志中的规格和市场状态。
4. 对照交易界面记录 100 单位商品的买入与卖出价格，并检查势力违禁品标记。

预期结果：

- 日志出现 `GearIn_tempered_alloy` 的名称、需求类别、基础价格、堆叠和货舱空间；
- 市场商品日志同时出现库存、可得量、供需上限、赤字/盈余和贸易调节；
- `demandPrice(100)` 对应市场买入、玩家卖出，`supplyPrice(100)` 对应市场卖出、玩家买入；
- 市场合法性状态与 `illegalCommodities` 中的商品 ID 一致；
- 货舱和交易界面显示 `Tempered Alloy ×10`。

## 故障分析

- **商品规格为空**：`commodities.csv` 路径错误、列位错位或商品 ID 重复。
    - **解决方案**：核对文件路径、表头顺序和 `GearIn_tempered_alloy`，重启后查看加载日志。

- **市场商品对象为空**：商品已注册，但目标市场没有建立该商品的市场数据。
    - **解决方案**：确认市场经济集合和商品需求类别，再使用 `market.getCommodityData(id)` 检查接入状态。

- **基础价格与交易价格不同**：把 `CommoditySpecAPI.getBasePrice()` 当成了玩家当前交易价。
    - **解决方案**：使用 `MarketAPI.getDemandPrice()` 和 `getSupplyPrice()`，同时固定数量与玩家价格标志。

- **违禁品标记没有出现**：势力文件中的 ID 与商品注册 ID 不一致。
    - **解决方案**：逐字核对 `illegalCommodities` 中的 `GearIn_tempered_alloy`，再读取 `isIllegal()`、`isSupplyLegal()` 和 `isDemandLegal()`。

- **价格方向记录反了**：把需求价写成玩家买入，或把供给价写成玩家卖出。
    - **解决方案**：按需求方向是市场买入、供给方向是市场卖出的规则重新标注日志和验证结果。

## 总结

商品注册建立的是全局规格，`CommoditySpecAPI` 读取这些规格；市场接入后，`CommodityOnMarketAPI` 才能描述库存、供需、贸易调节和合法性。实际交易价由 `MarketAPI` 结合数量和市场状态计算，排错时沿着 CSV、规格、市场对象和价格入口逐层确认。

## 建议参考项

- `data/campaign/commodities.csv`：商品 ID、显示、经济和货舱字段的注册来源；
- `com.fs.starfarer.api.campaign.econ.CommoditySpecAPI`：全局商品规格和分类属性；
- `com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI`：市场商品库存、供需、调节和合法性状态；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场价格、商品接入和合法性查询；
- `Starsector/starsector-core/starsector.log`：确认商品加载和运行时错误。
