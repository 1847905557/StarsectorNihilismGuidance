# 注册市场商品

往星区里加一种新货，静态规则写在 `commodities.csv`，单价、占舱、堆叠上限乃至经济体量都由它定调；真进了生涯，一个站点究竟按多少钱卖、按多少钱收，还得配合市场报价接口和势力违禁清单一起算。

大多数作者在设计第一种 MOD 专属商品时，总忍不住把单价和利润调得体面一点。这次我们拿回火合金 (`GearIn_tempered_alloy`) 反其道而行之 —— 公开货架拒收、回购价直接腰斩，做成严格管控的垄断工业品。想靠倒卖发财？尽早断了这个念头。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头、列数对齐与带逗号字段的引号转义；
- [C3-S4] 的 `RESOURCE` 类型描述，商品悬停说明走同一张表；
- [C12-S1] 的 Foundry 市场，后面会按它的市场 ID 读取报价。

## 在 commodities.csv 建立商品规格

所有能在货舱或市场上流通的商品，都登记在 `data/campaign/commodities.csv`。它采用与原版一致的 24 列表头，向 MOD 目录追加数据时不要改写表头顺序：

```csv
name,id,demand class,base price,sell price mult,export value,price variability,utility,origin,tags,stack size,cargo space,icon,sound id,sound id drop,order,economyTier,econUnitOrigUnused,econUnit,baseRaidDanger,eU * base value,iconWidthMult,plugin,desc
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,0.5,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/refined_metal.png,ui_cargo_metals,ui_cargo_metals_drop,2.9,2.2,200,600,,108000,1,,
```

这几项核心列的含义决定了回火合金在游戏里的全部物理与经济属性：

- **`name` 与 `id`**：第一列 `name` (`Tempered Alloy`) 是界面与货舱里实际看到的显示名称；第二列 `id` (`GearIn_tempered_alloy`) 是代码、脚本与配方引用的全局唯一标识，务必带上 `GearIn_` 前缀。
- **`demand class`**：填写 `heavy_machinery` (`重型机械` 的需求类别)，让经济模拟器沿用原版重工业的供需逻辑，无需额外编写一整套新需求模型。
- **价格与波动参数**：`base price` 为 `180`，`export value` 为 `700`，`price variability` 为 `3`，`utility` 为 `1`。其中 `sell price mult` 取 `0.5`，在交易界面把玩家将这批货物卖给子市场时的回购价格减半，配合后续黑市渠道与独占子市场控制流通利润。
- **货舱与音画**：单格堆叠上限 `stack size` 为 `1000`，单件占 `1` 单位货舱空间 (`cargo space`)；图标借用原版精炼金属图标，拾取与丢弃音效借用金属音效。
- **`econUnit` (经济档位换算基准)**：本例填 `600`。在 Starsector 的殖民地产业模型里，一座产业声明 `supply(..., 2)` 或 `demand(..., 3)` 时，指的都是**经济档位 (`unit`)** 而不是货舱里的个位数 ([C12-S4] 会具体用到它)；`econUnit` 决定了 1 档产出或贸易量大致对应多少实际货舱单位的经济体量，而相邻的 `eU * base value` (`600 * 180 = 108000`) 仅作表格里的参考列，引擎代码不会读取这一列。

## 登记货舱悬停描述

商品在货舱或交易面板悬停时弹出的长说明，并不是写在 `commodities.csv` 末尾的 `desc` 列里 (那一列不会作为货舱悬停描述加载)，而是走 [C3-S4] 介绍过的 `data/strings/descriptions.csv`：

```csv
GearIn_tempered_alloy,RESOURCE,经过回火处理的工业合金，可用于需要耐磨与韧性的结构部件。,,,,,
```

`id` 填商品 ID `GearIn_tempered_alloy`，`type` 填 `RESOURCE`，`text1` 即悬停说明正文，两处 ID 必须逐字一致。

## 商品规格与市场报价对象的区别

拿到回火合金的 ID 后，代码里会面对两类完全不同的接口：

| 对象 | 取得方式 | 管什么 |
| --- | --- | --- |
| 商品规格 (`CommoditySpecAPI`) | `Global.getSettings().getCommoditySpec(id)` | 全局静态定义：显示名称、基础单价、单格体积、堆叠上限与标签 |
| 市场商品状态 (`CommodityOnMarketAPI`) | `market.getCommodityData(id)` | 该市场独有的实时数据：本地供给/需求档位、短缺状态与动态报价 |

同一个回火合金在整个星区只有一份 `CommoditySpecAPI`，但在 Foundry、海盗黑市和霸主核心星系各自有不同的 `CommodityOnMarketAPI` 报价。读取基础体积或基准价走全局规格，算这笔买卖划不划算走市场接口：

```java
CommoditySpecAPI spec = Global.getSettings().getCommoditySpec("GearIn_tempered_alloy");
float basePrice = spec.getBasePrice();
float space = spec.getCargoSpace();

MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
float buy = market.getSupplyPrice("GearIn_tempered_alloy", 10, true);
float sell = market.getDemandPrice("GearIn_tempered_alloy", 10, true);
```

注意市场报价方法的命名视角是站在 **市场自己** 这一边的：`getSupplyPrice(...)` 是 "市场供货卖出" 的价格 (即玩家买入价)，`getDemandPrice(...)` 是 "市场收购需求" 的价格 (即玩家交货卖出价)，第三个布尔参数 `true` 表示按玩家交易口径计价，返回值为给定数量的总金额 (税率另算)。不过 `Market.getDemandPrice(...)` 返回的是尚未乘入 `sell price mult` 的基准收购价；前面设下的 `0.5` 卖出倍率，会在玩家真正打开子市场交易界面卖货时由交易层乘上去。

## 列为齿轮工业的违禁品

为了让齿轮工业控制的殖民地拒绝在开放市场上自由收购或倾销这种合金，打开 [C3-S1] 建立的 `data/world/factions/gear_industry.faction`，将 `"GearIn_tempered_alloy"` 追加到 `illegalCommodities` 数组中：

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "hand_weapons",
    "ai_cores",
    "GearIn_tempered_alloy",
],
```

加上这一条后，`faction.isIllegal("GearIn_tempered_alloy")` 返回 `true`。齿轮工业名下市场的开放市场会自动拒绝这件物资的常规交易，玩家若想私下脱手，就只能转向地下黑市，或是去下一节 [C12-S3] 专门开设的合金仓库。

## 读写玩家货舱：GearInAlloyCargo

操作玩家舰队的货舱通过 `Global.getSector().getPlayerFleet().getCargo()` 获取 `CargoAPI`。增减普通商品的方法是 `addCommodity(id, quantity)` 与 `removeCommodity(id, quantity)`，查询现有数量用 `getCommodityQuantity(id)`。

需要注意一个常见隐患：**`cargo.addCommodity(...)` 不会自动检查剩余空间。** 哪怕玩家货舱早已装满，强行调用照样会把东西硬塞进去，导致货舱直接显示超载报错。我们在工具类 `GearInAlloyCargo` 中先按 `cargo.getSpaceLeft() / spacePerUnit` 算出当前最多能塞进去多少件再写入，扣除时同样截断到现有持有量以内，避免写出负库存。

### 完整货舱工具类

`src/data/scripts/gearin/campaign/GearInAlloyCargo.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

/** 回火合金的货舱读写：按剩余空间加入玩家货舱，扣除时不超过现有数量。 */
public final class GearInAlloyCargo {

    public static final String COMMODITY_ID = "GearIn_tempered_alloy";

    private GearInAlloyCargo() {
    }

    /** 加入玩家货舱，返回实际加入的数量；空间不足时只加入放得下的部分。 */
    public static int give(int amount) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        float spacePerUnit = Global.getSettings().getCommoditySpec(COMMODITY_ID).getCargoSpace();
        int fit = amount;
        if (spacePerUnit > 0f) {
            fit = (int) Math.min(amount, cargo.getSpaceLeft() / spacePerUnit);
        }
        if (fit <= 0) {
            return 0;
        }
        cargo.addCommodity(COMMODITY_ID, fit);
        Global.getSector().getCampaignUI().addMessage("获得回火合金 x" + fit);
        return fit;
    }

    /** 从玩家货舱扣除，返回实际扣除的数量。 */
    public static int take(int amount) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int owned = (int) cargo.getCommodityQuantity(COMMODITY_ID);
        int removed = Math.min(amount, owned);
        if (removed <= 0) {
            return 0;
        }
        cargo.removeCommodity(COMMODITY_ID, removed);
        return removed;
    }
}
```

这个工具类会在 [C16-S1] 与 [C16-S3] 的港口盘查与酒吧事件里直接复用来收发回火合金。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 在控制台执行 `addcommodity GearIn_tempered_alloy 50`，货舱里多出 `50` 单位回火合金，使用精炼金属图标，鼠标悬停显示中文长描述；
- 飞往 Nipher Foundry 打开开放市场，回火合金被标为违禁且无法在该栏交易；切换到黑市标签页则可以正常卖出；
- 查看黑市卖出单价，明显低于基准买价的一半；
- 保存并重新读档，货舱中的物品数量保持不变。

## 常见问题

- **控制台执行 `addcommodity` 提示找不到商品 ID**：`commodities.csv` 路径写错、编码非 UTF-8，或该行字段存在格式错误导致加载被跳过。
    - **解决方案**：确认 `data/campaign/commodities.csv` 路径、表头顺序与 ID 拼写无误，且无残留的格式错行。

- **货舱里已经看得到商品，悬停却是一片空白**：将说明文字写在了 `commodities.csv` 末尾的 `desc` 列，而没有写进 `descriptions.csv`。
    - **解决方案**：在 `data/strings/descriptions.csv` 中添加 `type` 为 `RESOURCE`、主键为 `GearIn_tempered_alloy` 的记录。

- **在 Foundry 开放市场依然能够随意买卖回火合金**：`gear_industry.faction` 的 `illegalCommodities` 未保存或格式被尾逗号破坏，或者测试时跑在了别的势力的站点上。
    - **解决方案**：确认 `gear_industry.faction` 已将 `"GearIn_tempered_alloy"` 列入 `illegalCommodities`。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.CommoditySpecAPI`：商品规格的全局读取接口。
- `com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI`：单座市场内商品供需、短缺与价格接口。
- `com.fs.starfarer.api.campaign.CargoAPI`：货舱增减、空余体积 (`getSpaceLeft()`) 与库存查询。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：`getSupplyPrice(...)` 与 `getDemandPrice(...)`。
- `data/campaign/commodities.csv`：原版商品表头与 `econUnit` 数值参考。
