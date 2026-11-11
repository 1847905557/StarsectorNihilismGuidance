# 注册市场商品

本节注册回火合金，使它能作为货物加入 Cargo，并在悬停时显示描述。随后到 Foundry 市场读取它的库存、供需与买卖报价，再通过势力规则将它列为违禁品。同一种商品在不同市场会有不同结果，规格与市场状态也要分别处理。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 在 `commodities.csv` 中注册稳定的商品 ID；
- 解释 CSV 字段如何进入 `CommoditySpecAPI`；
- 区分全局商品规格和市场商品状态；
- 读取库存、供需、贸易调节、合法性和市场价格；
- 判断商品注册、描述挂接和势力规则是否分别生效。

## 前置知识

- [C1-S1] 的 MOD 目录与数据合并；
- [C1-S7] 的 CSV 表头、列与转义；
- [C3-S1] 的势力文件与 `gear_industry`；
- [C3-S5] 的 `RESOURCE` 描述类型；
- [C12-S1] 的市场对象和市场查询入口；
- 能够启用 Console Commands 并进入生涯市场。

## 为回火合金建立规格

在 MOD 的 `data/campaign/commodities.csv` 注册本节商品。已有文件时只追加数据行；同 ID 行参与数据合并，因此新商品使用独立 ID `GearIn_tempered_alloy`。文件内容如下：

```csv
name,id,demand class,base price,sell price mult,export value,price variability,utility,origin,tags,stack size,cargo space,icon,sound id,sound id drop,order,economyTier,econUnitOrigUnused,econUnit,baseRaidDanger,eU * base value,iconWidthMult,plugin,desc
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,0.5,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,108000,1,,
```

显示名采用 `Tempered Alloy`，需求类别沿用 `heavy_machinery`。新商品身份与已有经济类别分别设置，市场才能按这个类别处理需求；本节先说明商品如何接入，生产规则留给产业实现。

基础价格为 180，出口价值为 700，价格波动级别为 3，效用为 1。它们是经济计算的输入，实际报价还要结合市场。`economyTier` 取 2.2，`econUnit` 取 600，参与经济规模与物品数量换算；货舱空间每件为 1，规格单栈上限为 1000，栈是否采用这个上限由 [C12-S2] 的 Cargo 设置决定。

`sell price mult` 是玩家出售商品时的价格倍率，默认为 1。本例设为 0.5，使回火合金的出售金额降为市场需求报价的一半，税费另计。对于这种按供需定价的普通经济商品，降低这一倍率会直接压低倒卖收益。

图标沿用原版金属图标，两列声音使用已注册的补给拾取与放下声音。`tags` 的 `industrial, expensive` 是两个独立标签，CSV 按 [C1-S7] 的规则给含逗号字段加引号。标签供分类与筛选使用，具体用途取决于读取它的功能。

### 商品字段速查表

| 字段 | 当前职责 |
|---|---|
| `name` / `id` | 显示名与代码、规则使用的稳定 ID |
| `demand class` | 经济需求类别，本例使用已建立的 `heavy_machinery` |
| `base price` / `export value` | 基础价格与出口价值 |
| `sell price mult` | 玩家出售时的价格倍率，默认 1，本例为 0.5 |
| `price variability` / `utility` | 价格波动级别与商品效用 |
| `origin` / `tags` | 来源标识与逗号分隔的标签集合 |
| `stack size` / `cargo space` | 规格单栈上限与单件空间 |
| `icon` / `iconWidthMult` | 图标路径与显示宽度倍率 |
| `sound id` / `sound id drop` | 物品拾取与放下声音 ID |
| `order` | 列表显示顺序 |
| `economyTier` / `econUnit` | 经济规模参数与数量换算基准 |
| `baseRaidDanger` | 突袭危险度 |
| `econUnitOrigUnused` / `eU * base value` | 表中的辅助数据列，保留原列位 |
| `plugin` / `desc` | 保留表头位置；商品插件和货舱长描述不从这两列接入 |

## 登记货舱描述

货舱长描述沿 [C3-S5] 的 `RESOURCE` 类型注册，键使用商品 ID。把下面两行写入 `data/strings/descriptions.csv`，已有表头时只追加记录：

```csv
id,type,text1,text2,text3,text4,text5,notes
GearIn_tempered_alloy,RESOURCE,经过回火处理的工业合金，可用于需要耐磨与韧性的结构部件。,,,,,
```

`text1` 在货舱与交易界面的悬停说明中显示。商品规格让货物可以被识别，描述行给它提供正文，两处 ID 逐字符对应。启动后先确认规格能够取得，再通过控制台加入货物，观察图标、声音与悬停结果。

## 读取全局商品规格

通过设置或经济对象取得 `CommoditySpecAPI`，可以读取刚才的全局定义。以下局部片段记录回火合金的身份、经济类别、基础价格与货舱规格：

```java
CommoditySpecAPI spec = Global.getSettings().getCommoditySpec("GearIn_tempered_alloy");
LOG.info(String.format("spec id=%s name=%s demand=%s base=%.0f stack=%d space=%.1f",
        spec.getId(), spec.getName(), spec.getDemandClass(), spec.getBasePrice(),
        spec.getStackSize(), spec.getCargoSpace()));
```

这些值说明商品注册成了什么。即使规格的基础价格为 180，当前市场报价也可以不同；库存与供需从市场对象继续取得。

### 规格读取入口速查表

| 读取目的 | 入口 |
|---|---|
| 身份与名称 | `getId()`、`getName()`、`getLowerCaseName()`、`getOrigin()` |
| 图标与声音 | `getIconName()`、`getIconLargeName()`、`getIconWidthMult()`、`getSoundId()`、`getSoundIdDrop()` |
| 经济参数 | `getBasePrice()`、`getExportValue()`、`getPriceVariability()`、`getDemandClass()`、`getUtility()`、`getEconomyTier()`、`getEconUnit()` |
| 出售价格倍率 | `getSellPriceMult()` |
| 堆叠与空间 | `getStackSize()`、`getCargoSpace()` |
| 标签与经济分类 | `getTags()`、`hasTag()`、`isPrimary()`、`isExotic()`、`isMeta()`、`isNonEcon()` |
| 物流资源身份 | `isPersonnel()`、`isFuel()`、`isSupplies()` |
| 数据来源 | 来源 MOD 查询，供注册冲突与加载来源定位 |

`isPrimary()` 判断商品 ID 是否也是它自己的需求类别。本例使用 `heavy_machinery` 类别，因此回火合金不是这个类别的主商品。`exotic`、`meta` 与 `nonecon` 标签分别影响对应经济分类，人员、燃料与补给判断则供物流功能使用。分类、库存与违禁规则各有读取入口，标签本身不会自动建立市场货物或势力限制。

`setName()`、`setIconName()`、`setDemandClass()`、`setBasePrice()`、`setExportValue()` 与 `setBaseDanger()` 会改变运行时全局规格，随后读取它的对象也会取得新值。本例只读规格，商品数据由 CSV 提供，市场即时状态按下面的对象处理。

## 到 Foundry 读取商品状态

创建 `src/data/scripts/gearin/campaign/GearInCommodityProbe.java`。在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 中，将以下局部调用放在市场日志之后、`expired = true` 之前：

```java
GearInCommodityProbe.log(market);
```

两类位于同一包，监听器已经保存 Foundry 市场引用。完整观察器对回火合金、补给与毒品逐项读取，零库存也可以记录当前经济状态。它从 `market.getCommodityData(id)` 取得 `CommodityOnMarketAPI`，再通过 `getCommodity()` 回到规格、`getMarket()` 确认市场，局部片段如下：

```java
CommodityOnMarketAPI onMarket = market.getCommodityData("GearIn_tempered_alloy");
CommoditySpecAPI spec = onMarket.getCommodity();
LOG.info("market=" + onMarket.getMarket().getId() + " spec=" + spec.getId());
```

### 区分经济档位与货物件数

`getStockpile()` 读取库存状态，`getAvailable()` 读取经济可得量档位。`getMaxSupply()`、`getMaxDemand()` 是产业供需档位，`getDemand()` 返回需求对象，`getDemandValue()` 读取需求值。经济更新通过 `updateMaxSupplyAndDemand()` 重新取得当前产业的供需，观察器在更新结束后读取。

**产业的供需档位与 Cargo 中的件数采用不同单位。** `getDeficitQuantity()` 返回可按较高价格向市场出售的件数，`getExcessQuantity()` 返回可按较低价格从市场购买的件数，适合与本次交易数量比较。换算还受到 `econUnit` 与当前经济规模影响，档位 3 不能直接理解为库存 3 件。

需要进一步解释当前市场时，`getUtilityOnMarket()` 已考虑当地商品效用，`getExportIncome()` 读取出口收入，`getCommodityMarketData()` 取得商品的市场汇总数据。市场商品也提供 `isPersonnel()`、`isFuel()`、`isNonEcon()` 与 `isMeta()` 等分类，用于相应计算。

### 读取近期贸易带来的调节

贸易和经济事件通过带期限的修饰改变可得状态。`getTradeMod()` 允许正负值，`getTradeModPlus()` 只取正向部分，`getTradeModMinus()` 只取负向部分，`getCombinedTradeModQuantity()` 返回三者合并后的物品数量影响。下面两段局部日志分别记录总结果与各通道：

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

这些通道使用 `MutableStatWithTempMods`，日志从 `getModifiedValue()` 取得修饰后的值。`addTradeMod(...)`、`addTradeModPlus(...)` 与 `addTradeModMinus(...)` 接受来源、数量与持续生涯日，写入后再换算成可得量档位修饰。

因此近期交易可以改变可得量、赤字或盈余和报价，而库存并未按同样数量直接改变。需要直接设置当前市场库存时使用 `setStockpile(...)`，设置供需上限时使用 `setMaxSupply(...)`、`setMaxDemand(...)`；它们与带期限的事件效果分别处理。本节的观察器只读当前结果。

## 为指定数量取得买卖报价

有了规格与当前市场状态，再从 `MarketAPI` 取得报价。`getDemandPrice(...)` 表示市场买入，即玩家卖出；`getSupplyPrice(...)` 表示市场卖出，即玩家买入。本例固定 100 件，并采用玩家价格：

```java
float demandPrice = market.getDemandPrice("GearIn_tempered_alloy", 100, true);
float supplyPrice = market.getSupplyPrice("GearIn_tempered_alloy", 100, true);
LOG.info(String.format("price for 100: demand=%.0f supply=%.0f", demandPrice, supplyPrice));
```

**返回值是这 100 件商品的市场报价。** `isPlayerPrice` 决定是否应用玩家价格修饰，库存、供需与近期贸易决定当前报价。交易界面还会给玩家的出售金额乘上 `sell price mult`，之后计算税费。例如，本例 100 件回火合金的需求报价为 18000 星币时，税前出售金额为 `18000 × 0.5 = 9000` 星币。

下面分别计算回火合金的税前买卖金额。人员、非经济商品和自定义子市场采用各自的交易规则，本例使用普通经济商品的供需报价：

```java
String id = onMarket.getId();
float sellToMarket = market.getDemandPrice(id, 100, true) * spec.getSellPriceMult();
float buyFromMarket = market.getSupplyPrice(id, 100, true);
LOG.info(String.format("%s sellBeforeTariff=%.0f buyBeforeTariff=%.0f",
        id, sellToMarket, buyFromMarket));
```

## 判断商品交易合法性

把回火合金列为齿轮工业的违禁品。打开 [C3-S1] 的 `data/world/factions/gear_industry.faction`，在已有 `illegalCommodities` 数组中追加商品 ID，其余条目保留。局部形式如下：

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "GearIn_tempered_alloy"
],
```

`CommodityOnMarketAPI.isIllegal()` 读取当前市场判断，普通势力规则同时考虑商品 ID 与需求类别，自由港还会改变结果。`isIllegalAssumePrimary()` 直接检查势力违禁列表中的商品 ID，适合取得这项直接声明。比较两者时，同时确认市场势力与自由港状态。

`isSupplyLegal()`、`isDemandLegal()` 则保留产业供给与需求的合法性结果，经济更新从提供相应供需的产业读取。具体子市场是否允许一笔买卖，由 [C12-S4] 的交易插件决定。产业合法性、市场违禁判断与子市场交易许可各自有用途，当前日志分别记录。

最后让日志保留市场上下文。完整观察器的 `log(...)` 已在循环前加入以下局部行：

```java
LOG.info("GearIn commodity context: market=" + market.getId());
```

市场 ID 与商品 ID 一起定位结果。控制台能加入回火合金后，继续悬停确认描述、进入目标市场确认违禁标记，再按相同数量比较报价与税额。

## 完整最小例

保存为 `src/data/scripts/gearin/campaign/GearInCommodityProbe.java`，调用入口采用前面的经济观察器。

::: details 完整成品：GearInCommodityProbe

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

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
        LOG.info("GearIn commodity context: market=" + market.getId());
        for (String commodityId : COMMODITY_IDS) {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
            CommodityOnMarketAPI onMarket = market.getCommodityData(commodityId);
            LOG.info(String.format(
                    "GearIn commodity %s: name=%s demand=%s base=%.0f stack=%d space=%.1f tags=%s stockpile=%.1f available=%d maxSupply=%d maxDemand=%d utility=%.2f deficit=%d excess=%d tradeMod=%.1f illegal=%s supplyLegal=%s demandLegal=%s demandPrice(100)=%.0f supplyPrice(100)=%.0f sellMult=%.3f sellBeforeTariff(100)=%.0f",
                    spec.getId(), spec.getName(), spec.getDemandClass(), spec.getBasePrice(),
                    spec.getStackSize(), spec.getCargoSpace(), spec.getTags(),
                    onMarket.getStockpile(), onMarket.getAvailable(), onMarket.getMaxSupply(),
                    onMarket.getMaxDemand(), onMarket.getUtilityOnMarket(),
                    onMarket.getDeficitQuantity(), onMarket.getExcessQuantity(),
                    onMarket.getCombinedTradeModQuantity(), onMarket.isIllegal(),
                    onMarket.isSupplyLegal(), onMarket.isDemandLegal(),
                    market.getDemandPrice(commodityId, 100, true),
                    market.getSupplyPrice(commodityId, 100, true),
                    spec.getSellPriceMult(),
                    market.getDemandPrice(commodityId, 100, true) * spec.getSellPriceMult()));
        }
    }
}
```

:::

## 验证方案

前置数据：启用包含本节 `commodities.csv` 注册行和 `GearInCommodityProbe` 类的 GearIn MOD，并使用拥有 `gear_industry` 市场且势力 `illegalCommodities` 已含 `GearIn_tempered_alloy` 的测试存档，同时已启用 Console Commands 控制台。

1. 启动游戏并进入 GearIn 市场，确认 `commodities.csv` 已经加载。
2. 执行 `additem GearIn_tempered_alloy 10`，打开货舱和交易界面。
3. 查看 `GearIn commodity` 日志，按市场 ID 对应回火合金、补给与毒品，核对规格、经济档位、物品数量和两个价格方向。
4. 悬停回火合金，查看 RESOURCE 正文与违禁品标记；在同一市场状态下交易 100 件，比较界面的税前出售金额与日志的 `sellBeforeTariff(100)`，另行核对税额。

预期结果：

- 日志出现 `GearIn_tempered_alloy` 的名称、需求类别、基础价格、堆叠和货舱空间；
- 市场商品日志同时出现库存、可得量、供需上限、赤字/盈余和贸易调节；
- 回火合金的 `sellMult` 为 0.5，`sellBeforeTariff(100)` 为 `demandPrice(100)` 的一半；`supplyPrice(100)` 是玩家买入这 100 件商品的税前金额；
- 市场合法性状态与 `illegalCommodities` 中的商品 ID 一致；
- 货舱显示 `Tempered Alloy ×10`，悬停正文来自本节 RESOURCE 行；价格方向与税费分别观察。

## 故障分析

- **商品规格为空**：`commodities.csv` 路径错误、列位错位或商品 ID 重复。
    - **解决方案**：核对文件路径、表头顺序和 `GearIn_tempered_alloy`，重启后查看加载日志。

- **取得商品对象时发生加载错误**：商品 ID 未注册或需求类别没有建立。
    - **解决方案**：先用设置取得商品规格，再核对 CSV 的 ID 与 `demand class`，重启后从目标市场查询。

- **基础价格与交易价格不同**：把 `CommoditySpecAPI.getBasePrice()` 当成了玩家当前交易价。
    - **解决方案**：使用 `MarketAPI.getDemandPrice()` 和 `getSupplyPrice()`，固定数量与玩家价格标志，再给出售金额乘上 `getSellPriceMult()`，最后核对税费。

- **违禁品标记没有出现**：势力文件中的 ID 与商品注册 ID 不一致。
    - **解决方案**：逐字核对 `illegalCommodities` 中的 `GearIn_tempered_alloy`，确认当前市场属于 `gear_industry` 且未启用自由港，再读取 `isIllegal()` 并核对所用子市场的交易规则。

- **价格方向记录反了**：把需求价写成玩家买入，或把供给价写成玩家卖出。
    - **解决方案**：按需求方向是市场买入、供给方向是市场卖出的规则重新标注日志和验证结果。

## 总结

回火合金的规格由 CSV 提供，货舱正文用同一 ID 登记为 `RESOURCE`，市场再按当前供需和贸易计算状态。经济档位、物品件数与交易金额各有单位，报价也区分市场买入和卖出。

势力违禁列表提供商品规则，市场条件影响当前判断，子市场决定实际交易许可。记录市场、商品与数量，就能把这些结果对应到玩家正在进行的交易。

## 建议参考项

- `data/campaign/commodities.csv`：商品 ID、显示、经济和货舱字段的注册来源；
- `com.fs.starfarer.api.campaign.econ.CommoditySpecAPI`：全局商品规格和分类属性；
- `com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI`：市场商品库存、供需、调节和合法性状态；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场价格、商品接入和合法性查询；
- `Starsector/starsector-core/starsector.log`：确认商品加载和运行时错误。
