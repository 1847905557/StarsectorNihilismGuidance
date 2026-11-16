# 注册市场商品

回火合金是齿轮工业的第一种自有商品，而且从一开始就是垄断物资：齿轮工业愿意卖给您，却不怎么愿意从您手里收回来，在它自己的地盘上公开交易更是想都别想。

相信不少人做出第一种特产商品时，脑子里想的是怎么靠它发财。那就先把这个念头放一放吧，在这个 MOD 里，想拿它赚钱的人得去黑市。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头、列与转义；
- [C3-S4] 的 `RESOURCE` 描述类型，商品的悬停说明走这条路；
- [C12-S1] 的 Foundry 市场，本节的商品在那里读取报价。

## 为回火合金建立规格

回火合金是齿轮工业的第一种自有商品，ID 为 `GearIn_tempered_alloy`，名称 `GearIn_` 前缀之外的部分是显示名。商品注册在 `data/campaign/commodities.csv`，表头沿用原版的 24 列，已有文件时只追加数据行：

```csv
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,0.5,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/refined_metal.png,ui_cargo_metals,ui_cargo_metals_drop,2.9,2.2,200,600,,108000,1,,
```

`demand class` 沿用原版的 `heavy_machinery`，市场按这个类别处理需求，新商品不必另造经济类别。`base price` 为 180，`export value` 为 700，`price variability` 为 3，`utility` 为 1，它们是经济计算的输入，实际报价还要结合市场。

`sell price mult` 取 0.5，是玩家出售时的价格倍率，默认值为 1。回火合金是按供需定价的普通商品，把倍率压低一半，倒卖的收益就被压掉一半，对一种由本势力垄断的合金来说，这个代价正合适。

`stack size` 为 1000，`cargo space` 为 1，每件占一格货舱空间。图标借用原版精炼金属 `refined_metal.png`，两列声音借用原版的金属拾取与放下音。`tags` 里的 `industrial, expensive` 含逗号，整段加引号，规则见 [C1-S7]。

**CSV 的列数必须与表头一致。** 少一列或多一列，整行被错位读取，商品注册看似成功，字段却各自错位到别的列。

## 登记货舱描述

商品悬停时的长描述沿 [C3-S4] 的 `RESOURCE` 类型注册，键就是商品 ID，写入 `data/strings/descriptions.csv`：

```csv
GearIn_tempered_alloy,RESOURCE,经过回火处理的工业合金，可用于需要耐磨与韧性的结构部件。,,,,,
```

`text1` 显示在货舱与交易界面的悬停说明里。规格让商品能被识别，描述行给它正文，两处 ID 必须逐字符一致。**`commodities.csv` 末尾的 `desc` 列不接入这段描述**，把说明写在那一列里，游戏里什么也不会显示。

## 规格与市场状态是两回事

商品的信息分在两处：

| 对象 | 取得方式 | 内容 |
| ---- | -------- | ---- |
| 全局规格 | `Global.getSettings().getCommoditySpec(id)` | 名称、基础价格、栈上限、货舱空间、标签 |
| 市场商品 | `market.getCommodityData(id)` | 这个市场里的库存、供需与实际报价 |

规格只描述这种商品本身，所有市场共用。同一种合金在 Foundry 和别处的价格可以不同，差别全在市场商品上。

```java
CommoditySpecAPI spec = Global.getSettings().getCommoditySpec("GearIn_tempered_alloy");
float basePrice = spec.getBasePrice();
float space = spec.getCargoSpace();
```

规格读不到，多半是 ID 拼错，或者 CSV 整行被错位读取。

## 市场报价的方向

报价从市场的角度命名：

```java
MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
float buy = market.getSupplyPrice("GearIn_tempered_alloy", 10, true);
float sell = market.getDemandPrice("GearIn_tempered_alloy", 10, true);
```

`getSupplyPrice(...)` 是市场卖出，即玩家买入；`getDemandPrice(...)` 是市场买入，即玩家卖出。**两个方向别记反**，记反之后，脚本里算出来的盈亏正负颠倒。返回值是指定数量的总金额，税额由子市场的交易流程另算，第三个参数为 `true` 时取玩家价。

`sell price mult` 作用在玩家卖出的这一侧，所以 `getDemandPrice(...)` 的结果会是同等条件下的一半。

## 列为违禁品

违禁品由势力决定。在 `data/world/factions/gear_industry.faction` 的 `illegalCommodities` 里追加商品 ID：

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "hand_weapons",
    "ai_cores",
    "GearIn_tempered_alloy",
],
```

这会让齿轮工业控制的市场在公开市场拒绝交易回火合金，运进黑市则不受限。这种合金是垄断物资：公开市场拒绝交易，黑市不受此限。读取用 `faction.isIllegal("GearIn_tempered_alloy")`。

## 读写玩家货舱

玩家货舱从 `Global.getSector().getPlayerFleet().getCargo()` 取得。商品用 `addCommodity(id, quantity)` 与 `removeCommodity(id, quantity)` 加减，`getCommodityQuantity(id)` 读取现有数量。

**直接 `addCommodity` 不检查空间。** 货舱满了照样加进去，玩家会看到货舱超载。本例先用 `getSpaceLeft()` 除以每件占用的空间，算出放得下的数量再加入；扣除时不超过现有数量，避免写成负数。

## 完整的货舱类

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

这个类只提供读写方法，由后续章节的产业与任务调用。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 控制台执行 `addcommodity GearIn_tempered_alloy 50`，货舱中出现回火合金，图标为精炼金属，悬停显示描述；
- 到 Foundry 的公开市场，回火合金被拒绝交易；到黑市则可以交易；
- 在黑市出售，得到的金额约为买入价的一半以下；
- 保存并重新读档，货舱中的数量不变。

页面中的现象依据 API 与原版数据推出，编者尚未逐项在游戏内实测。

## 常见问题

- **控制台提示找不到商品**：CSV 没有被读取，或者行被错位读取。
    - **解决方案**：核对列数与表头一致，含逗号的字段加引号。

- **货舱里有商品，却没有图标和说明**：图标路径或描述行的 ID 写错。
    - **解决方案**：核对 `icon` 列与 `descriptions.csv` 的 ID，不要写在 `desc` 列。

- **在市场里没有报价**：`demand class` 写成了不存在的类别。
    - **解决方案**：沿用原版已有的类别，如 `heavy_machinery`。

- **公开市场仍能交易**：违禁品列表没有追加，或者追加到了别的势力文件。
    - **解决方案**：核对 `gear_industry.faction` 的 `illegalCommodities`。

- **货舱显示超载**：直接 `addCommodity` 没有检查空间。
    - **解决方案**：先用 `getSpaceLeft()` 算出放得下的数量。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.CommoditySpecAPI`：规格的全部读取方法。
- `com.fs.starfarer.api.campaign.CargoAPI`：货舱加减、容量与空间查询。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：报价与市场商品状态。
- `com.fs.starfarer.api.campaign.FactionAPI`：违禁品的读取方法。
- `data/campaign/commodities.csv`：原版的商品表头与数据行。
