# 实现专用子市场

Foundry 的合金仓库只做一种生意：只收回火合金，也只卖回火合金，税也比公开市场收得狠。一座垄断物资的产地，规矩理应由卖方来定，没必要像普通市场那样来者不拒。

您要是想在这里顺手卖掉一船战利品，那么请回公开市场去。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C12-S1] 的 Foundry 市场与 `addSubmarket(...)`，新子市场在那里加入；
- [C12-S2] 的回火合金与 `GearInAlloyCargo`，子市场只经营这一种商品；
- [C1-S7] 的 CSV 表头与转义。

## 注册子市场

子市场的身份登记在 `data/campaign/submarkets.csv`，表头沿用原版的七列：

```csv
id,name,faction,desc,script,icon,order
GearIn_alloy_depot,Alloy Depot,gear_industry,"A depot run by the foundry. It stocks tempered alloy and buys nothing else.",data.scripts.gearin.campaign.GearInAlloyDepotPlugin,graphics/factions/neutral_traders.png,1100
```

`id` 是市场添加这个入口时使用的名字，`script` 是行为插件的完整类名，两处都必须与代码逐字一致。`faction` 为 `gear_industry`，是子市场的默认势力。`order` 取 1100，排在三个原版子市场之后。图标借用原版中立商人的图标。

CSV 只定义身份。库存、收购范围、税率这些规矩全部由插件提供，所以这一行写对之后，真正要写的是插件。

**`script` 的类名写错，游戏不会在启动时报错。** 症状出现在市场添加子市场的时候：入口不出现，或者日志里出现类找不到的记录。这一条在以后所有按类名加载的登记中都适用。

## 插件决定交易规矩

插件继承 `BaseSubmarketPlugin`，只覆写需要改变的方法，其余沿用基类。本节的插件负责定时补货、限定商品、拒绝舰船与设定税率。

### 定时补货

玩家打开交易界面时，界面调用 `updateCargoPrePlayerInteraction()`。补货在这里完成：

```java
float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
addAndRemoveStockpiledResources(seconds, false, true, true);
sinceLastCargoUpdate = 0f;
```

基类的 `advance(...)` 随生涯推进累计 `sinceLastCargoUpdate`，单位是生涯天。补货方法要的是秒，所以先换算，再交给 `addAndRemoveStockpiledResources(...)`，最后把计时清零。**库存刷新跟随交易入口，计时跟随生涯推进**：玩家一直不来，库存不会凭空刷新，来的时候按累计的天数一次补足。

补哪些商品由 `shouldHaveCommodity(...)` 选择，补到多少由 `getStockpileLimit(...)` 决定：

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

上限取 20 与市场最大供给档位的两倍中较大者。未被选中的商品上限为 0，库存里已有的数量也会逐步消退。

### 只收购、只出售回火合金

`isIllegalOnSubmarket(...)` 返回 `true` 表示拒绝这笔转移。商品 ID 与货物栈各有一个重载，栈的版本先排除非商品栈，再转交 ID 的判断：

```java
@Override
public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    return !ALLOY.equals(commodityId);
}
```

买卖两个方向共用同一条规则，所以这座仓库既只卖回火合金，也只收回火合金。拒绝文字由 `getIllegalTransferText(...)` 提供，玩家从中读到限制的原因，不必猜。

### 不经营舰船

舰船有自己的重载。本例一律拒绝，并让舰船界面不显示这个入口：

```java
@Override
public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
    return true;
}

@Override
public boolean showInFleetScreen() {
    return false;
}
```

### 税率与经济

`getTariff()` 返回 0.2，即成交额的 20%。`isParticipatesInEconomy()` 返回 `true`，玩家的出售会按 [C12-S2] 的贸易调节影响市场供需。回火合金是本势力的垄断物资，税率因此定得比公开市场略高，玩家要么认账，要么去走 [C12-S2] 中提到的黑市。

## 添加到 Foundry

在 `GearInFoundryGenerator` 添加其他子市场之后加入这一行：

```java
market.addSubmarket("GearIn_alloy_depot");
```

完整的生成器见 [C12-S1]。市场已有同 ID 入口时，`addSubmarket(...)` 保留原实例。

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

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry。

- 交易界面出现 Alloy Depot，库存里只有回火合金；
- 尝试出售其他商品，被拒绝并显示 `The depot only trades tempered alloy`；
- 舰船界面里没有这个入口；
- 成交时税额约为金额的 20%。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **交易界面没有 Alloy Depot**：`submarkets.csv` 没有加载，或者没有调用 `addSubmarket(...)`。
    - **解决方案**：核对 CSV 的 ID 与代码中的字符串，并确认生成器已加入这一行。

- **入口出现，库存却是空的**：`shouldHaveCommodity(...)` 没有选中回火合金，或者商品 ID 拼错。
    - **解决方案**：核对 `GearInAlloyCargo.COMMODITY_ID`，并确认市场上有该商品的供给档位。

- **日志里出现类找不到的记录**：`script` 列的完整类名写错。
    - **解决方案**：与插件的包名和类名逐字核对。

- **其他商品仍能出售**：只覆写了商品 ID 的重载，货物栈的重载没有转交。
    - **解决方案**：栈的版本先判断 `isCommodityStack()`，再调用 ID 的判断。

- **旧存档中没有这个子市场**：Foundry 只在新档中生成。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin`：全部可覆写方法与补货逻辑。
- `com.fs.starfarer.api.campaign.SubmarketPlugin`：`TransferAction` 与 `PlayerEconomyImpactMode`。
- `com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin`：原版仓储，免费转移的写法。
- `data/campaign/submarkets.csv`：原版子市场的登记。
