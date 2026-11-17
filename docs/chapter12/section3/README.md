# 实现专用子市场

星区里的开放市场、黑市、军需补给乃至私人仓库，背后共用同一套扩展接口：`submarkets.csv` 负责登记入口，真正决定这扇门后卖什么、收什么、抽几成关税的，是继承自 `BaseSubmarketPlugin` 的行为插件。

借着这套机制，就能在 Foundry 里单独隔出一间直营的合金仓库 (`Alloy Depot`) —— 货架上只吞吐回火合金，不混矿石也不卖船，玩家点开交易界面的那一刻，就能看明白这里的规矩。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头对齐与双引号转义；
- [C12-S1] 的 `GearInFoundryGenerator`，新子市场将加入那里的 Foundry 市场；
- [C12-S2] 的回火合金 ID (`GearInAlloyCargo.COMMODITY_ID`) 与违禁规则。

## 在 submarkets.csv 登记入口

自定义子市场的规格表位于 `data/campaign/submarkets.csv`，表头共 7 列：

```csv
id,name,faction,desc,script,icon,order
GearIn_alloy_depot,Alloy Depot,gear_industry,"A depot run by the foundry. It stocks tempered alloy and buys nothing else.",data.scripts.gearin.campaign.GearInAlloyDepotPlugin,graphics/factions/neutral_traders.png,1100
```

- `id` 是 `market.addSubmarket(...)` 传入的唯一标识 `GearIn_alloy_depot`；
- `faction` 填 `gear_industry`，使交易声望与界面势力颜色对齐齿轮工业；
- `script` 指向继承了 `SubmarketPlugin` 的完整类名；
- `order` 设为 `1100`，确保它排在开放市场 (`0`)、黑市 (`999`) 和仓库 (`1000`) 之后。

**`submarkets.csv` 的 `script` 类名会在游戏启动读取数据时由 `ScriptStore` 提前校验。** 如果类名写错或包路径不符，往往在进入主菜单前就会直接报错退出；只有类名存在但缺少无参构造函数或内部逻辑有误时，才会拖到开新档向市场调用 `addSubmarket(...)` 时抛出异常。

## 继承 BaseSubmarketPlugin 定制规则

`BaseSubmarketPlugin` 已经替我们实现了基本的货舱维护与界面交互框架，专用仓库只需覆写四个关键切面。

### 按生涯经过时间刷新库存

玩家每次停靠空间站并切换到某个子市场标签页时，游戏会调用插件的 `updateCargoPrePlayerInteraction()`：

```java
float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
addAndRemoveStockpiledResources(seconds, false, true, true);
sinceLastCargoUpdate = 0f;
```

基类的 `advance(amount)` 会在后台随生涯时钟不断累加 `sinceLastCargoUpdate` (单位为生涯天)，而 `addAndRemoveStockpiledResources(...)` 接收的是换算后的秒数。因此只在玩家真正推门看货的那一刻，插件才按累计过去的天数一口气把货架补满、清零计时器，既省掉了后台空转的损耗，效果上又与每日匀速补货毫无二致。

哪些商品允许上货架、最多囤多少，则分别交由 `shouldHaveCommodity(...)` 与 `getStockpileLimit(...)` 回答：

```java
@Override
public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
    return ALLOY.equals(commodity.getId());
}

@Override
public int getStockpileLimit(CommodityOnMarketAPI commodity) {
    return shouldHaveCommodity(commodity) ? Math.max(20, commodity.getMaxSupply() * 2) : 0;
}
```

这里顺带解释一下为什么仓库的囤货上限直接压在了 `20`：原版的 `OpenMarketPlugin` 在算补货上限时，会把供应档位乘上 `commodities.csv` 的 `econUnit` (`600`)，动辄刷出成百上千单位；而对于规模 `3` 的 Foundry (`getMaxSupply()` 为 `2` 档) 来说，`commodity.getMaxSupply() * 2` 只有 `4`，因此这里的 `Math.max(20, ...)` 有意将单次囤货压在 `20` 个货舱单位左右，卡死批量扫货的空间。对于其余所有商品，`shouldHaveCommodity(...)` 返回 `false` 且上限为 `0`，确保货架里不会混进别的东西。

### 限定可交易的商品与拒绝文字

`isIllegalOnSubmarket(...)` 返回 `true` 时，交易界面会标红并禁止玩家买入或卖出对应货物：

```java
@Override
public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    return !ALLOY.equals(commodityId);
}

@Override
public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
    if (!stack.isCommodityStack()) {
        return true;
    }
    return isIllegalOnSubmarket(stack.getCommodityId(), action);
}
```

第二个接收 `CargoStackAPI` 的重载是交易界面拖拽货物栈时真正最常进的分支：先用 `stack.isCommodityStack()` 把武器、战机联队、特殊物品这类非普通商品全部拒绝，再把商品 ID 交给前一个重载统一判断 (`!ALLOY.equals(...)`)。配套覆写 `getIllegalTransferText(...)`，玩家鼠标悬停在红框上就能看到明确的拒绝原因 `"The depot only trades tempered alloy"`。

### 屏蔽舰船交易页签

合金仓库不负责卖船也不收船，将舰船版本的 `isIllegalOnSubmarket(FleetMemberAPI, TransferAction)` 固定返回 `true`，再把 `showInFleetScreen()` 设为 `false`，就能让这个子市场干脆地从舰队改装/买船界面的子市场栏里隐身。

### 关税与经济影响

`getTariff()` 返回 `0.2f` (20% 关税，比原版开放市场的 30% 低，或按需自行定价，此处重点展示覆盖基类默认税率的方式)；`isParticipatesInEconomy()` 返回 `true`，使玩家在合金仓库的买卖同样受本地供需定价与贸易影响。

## 将专用仓库接入 Foundry

在 [C12-S1] 的 `GearInFoundryGenerator.generate(...)` 中，紧跟在 `"storage"` 之后补充一行挂载：

```java
market.addSubmarket("open_market");
market.addSubmarket("black_market");
market.addSubmarket("storage");
market.addSubmarket("GearIn_alloy_depot");
```

如果该市场已经包含同 ID 的子市场，`addSubmarket(...)` 不会重复添加。

## 完整的仓库插件

`src/data/scripts/gearin/campaign/GearInAlloyDepotPlugin.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin.TransferAction;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

/** Foundry 的回火合金仓库：定时补货，只出售、只收购回火合金，不出售舰船。 */
public class GearInAlloyDepotPlugin extends BaseSubmarketPlugin {

    private static final String ALLOY = GearInAlloyCargo.COMMODITY_ID;

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;
    }

    @Override
    public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
        return ALLOY.equals(commodity.getId());
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI commodity) {
        return shouldHaveCommodity(commodity) ? Math.max(20, commodity.getMaxSupply() * 2) : 0;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return !ALLOY.equals(commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (!stack.isCommodityStack()) {
            return true;
        }
        return isIllegalOnSubmarket(stack.getCommodityId(), action);
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "The depot only trades tempered alloy";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        return true;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "The depot does not deal in ships";
    }

    @Override
    public float getTariff() {
        return 0.2f;
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return true;
    }

    @Override
    public boolean showInFleetScreen() {
        return false;
    }
}
```

注：`BaseSubmarketPlugin` 会把 `sinceLastCargoUpdate` 初始化为 `31f`，因此在当前 [C12-S3] 第一次打开 `Alloy Depot` 时，仓库货架就会按 `Math.max(20, ...)` 的保底上限立即补出 `20` 单位回火合金 (此时 `commodity.getMaxSupply()` 仍为 `0`)；等下一节 [C12-S4] 为 Foundry 接上 Element Works 产业后，这批库存才会随市场真实供应量补货。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry 并打开交易界面。

- 子市场页签中在仓库之后出现 `Alloy Depot`；
- 尝试把玩家货舱里的补给、燃料、武器或其它物品拖进 `Alloy Depot` 的买卖区，全部遭到拦截并提示 `The depot only trades tempered alloy`；
- 在控制台给玩家加入回火合金并拖入 `Alloy Depot` 出售，允许正常交易且关税显示为 `20%`；
- 切换到舰队买船/整备页面，顶部子市场列表中不显示 `Alloy Depot`。

## 常见问题

- **停靠 Foundry 后交易页签里找不到 `Alloy Depot`**：`GearInFoundryGenerator` 尚未补充 `market.addSubmarket("GearIn_alloy_depot")`，或 `submarkets.csv` 的 ID 与 `script` 类名有拼写偏差。
    - **解决方案**：确认 `submarkets.csv` 7 列完整、`GearInAlloyDepotPlugin` 已打进 JAR，且生成器已调用 `addSubmarket(...)`。

- **往仓库里拖武器、战机联队或特殊物品竟然能成功卖出**：忘记覆写接收 `CargoStackAPI` 的 `isIllegalOnSubmarket` 重载，导致游戏沿用了基类默认实现。
    - **解决方案**：在 `CargoStackAPI` 重载中先过滤 `!stack.isCommodityStack()`，再转交商品 ID 判断。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin`：补货周期 (`sinceLastCargoUpdate`)、库存上限与商品/舰船准入方法。
- `com.fs.starfarer.api.campaign.SubmarketPlugin`：`TransferAction` 枚举与税率接口。
- `com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin`：原版开放市场的货架库存刷新与 `econUnit` 用法。
- `com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin`：原版仓库的免费存储与舰队界面展示设置。
- `data/campaign/submarkets.csv`：原版子市场登记表头与排序参考。
