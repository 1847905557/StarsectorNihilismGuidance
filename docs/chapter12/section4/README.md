# 实现专用子市场

本节给 Foundry 添加 Refinery Depot：出售金属与重型机械，收购矿石和机械，并提供小型舰船交易。这个入口需要同时决定库存怎样刷新、玩家能买卖什么，以及交易怎样影响市场经济；我们继承 `BaseSubmarketPlugin`，把这些规则接到实际交易界面。

_本节预期阅读时间：\~28 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 在 `submarkets.csv` 注册一个带插件入口的子市场；
- 区分 `SubmarketSpecAPI`、`SubmarketAPI` 和 `SubmarketPlugin` 的职责；
- 从市场取得子市场并读取势力、税率、货舱和插件状态；
- 使用 `SubmarketPlugin` 控制货物库存、商品交易和舰船交易；
- 解释交易动词、经济影响和界面入口之间的边界；
- 用日志和交易界面验证自定义子市场行为。

## 前置知识

- [C12-S1] 的 `MarketAPI`、市场势力和子市场集合；
- [C12-S2] 的 `CargoAPI`、货舱栈和封存舰船；
- [C12-S3] 的商品 ID 和市场商品对象；
- 能够进入拥有 `gear_industry` 市场的生涯并查看交易界面。

## 注册子市场身份

在 `data/campaign/submarkets.csv` 注册 `GearIn_refinery_depot`，显示名为 `Refinery Depot`。记录中的 `script` 指向本节的插件类，市场随后按 ID 添加这个入口。文件内容如下：

```csv
id,name,faction,desc,script,icon,order
GearIn_refinery_depot,Refinery Depot,gear_industry,"An industrial depot for metal stockpiles and refinery equipment.",data.scripts.gearin.campaign.GearInRefineryDepotPlugin,graphics/factions/neutral_traders.png,1100
```

`faction` 使用 `gear_industry`，作为子市场的默认势力；`icon` 沿用原版中立商人图标，`order=1100` 使入口排在本例三个原版子市场之后。`desc` 描述交易站，实际库存与限制由插件提供。CSV 建立 `SubmarketSpecAPI`，市场添加时创建 `SubmarketAPI` 并实例化 `script`，因此 ID 与完整类名都要对应。

## 把新入口添加到 Foundry

创建 `src/data/scripts/gearin/campaign/GearInRefineryDepotPlugin.java` 与 `GearInSubmarketProbe.java`。前者继承 `BaseSubmarketPlugin`，后者读取交易入口的运行状态。在 [C12-S1] 的 `GearInEconomyProbe.advance(...)` 取得市场后、记录市场之前，加入局部调用：

```java
market.addSubmarket("GearIn_refinery_depot");
GearInSubmarketProbe.log(market);
```

市场已有同 ID 入口时，`addSubmarket(...)` 保留原实例。第一次添加会调用插件的 `init(SubmarketAPI)`，基类保存 `market` 与 `submarket`。本例直接沿用这些引用，计时也沿用基类的 `advance(...)`；需要添加自有状态时，再覆写相应方法并调用 `super`。下面是局部继承写法，完整类无需重复这些空转发：

```java
@Override
public void init(SubmarketAPI submarket) {
    super.init(submarket);
}

@Override
public void advance(float amount) {
    super.advance(amount);
}
```

基类在 `advance(...)` 中把帧时间转换成生涯日，累计货物与舰船刷新间隔。玩家进入交易时，界面调用 `updateCargoPrePlayerInteraction()`，插件再根据累计时间更新库存。**库存刷新跟随交互入口，计时跟随生涯推进。**

## 决定库存与收购范围

Refinery Depot 补充的商品是 `metals`、`rare_metals` 与 `heavy_machinery`，收购范围是 `ore`、`rare_ore` 与 `heavy_machinery`。库存与收购使用两份集合，分别进入刷新和交易判断：炼厂出售精炼材料，也接收原料与设备。

### 按时间补充指定商品

刷新使用基类的 `addAndRemoveStockpiledResources(...)`，市场继续提供供需状态，插件通过 `shouldHaveCommodity()` 选择哪些商品进入补货流程。`getStockpileLimit()` 返回当前子市场希望保有的物品件数，本例取 20 与最大供给档位乘 2 中较大的值：

```java
@Override
public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
    return contains(STOCKPILE_IDS, commodity.getId());
}

@Override
public int getStockpileLimit(CommodityOnMarketAPI commodity) {
    return shouldHaveCommodity(commodity)
            ? Math.max(20, commodity.getMaxSupply() * 2)
            : 0;
}
```

完整刷新方法把 `sinceLastCargoUpdate` 转回秒后交给基类，再清零计时。库存低于上限时随经过时间补充，高于上限时逐步减少；未选中的商品使用上限 0，已有数量也逐步消退。玩家卖入的矿石因此可以暂时留在 Cargo 中，补货集合只说明插件主动供应哪些商品。

### 按买卖方向判断商品

玩家出售时检查 `PLAYER_SELL_IDS`，购买时检查 `STOCKPILE_IDS`。`isIllegalOnSubmarket(...)` 返回 `true` 表示拒绝这笔转移，局部方法如下：

```java
@Override
public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    if (action == TransferAction.PLAYER_SELL) {
        return !contains(PLAYER_SELL_IDS, commodityId);
    }
    return !contains(STOCKPILE_IDS, commodityId);
}
```

界面传入货物栈时，另一个重载先判断商品栈，再转交同一套 ID 规则。当前对非商品栈返回 `false`，允许其交易；主动生成的货物仍是上述商品，舰船库存另行处理：

```java
@Override
public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
    if (!stack.isCommodityStack()) {
        return false;
    }
    return isIllegalOnSubmarket(stack.getCommodityId(), action);
}
```

拒绝原因由 `getIllegalTransferText(CargoStackAPI, TransferAction)` 返回，完整类为买卖分别提供文字。修改收购或库存范围时，提示也对应这份集合更新，玩家才能从拒绝原因理解当前限制。

## 补充小型舰船并限制购买

舰船放在 Cargo 的封存舰船集合中，交易判断直接接收 `FleetMemberAPI`。本例购买时只允许护卫舰与驱逐舰，出售方向保留大型舰船交易：

```java
@Override
public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
    if (action == TransferAction.PLAYER_BUY) {
        return !(member.isFrigate() || member.isDestroyer());
    }
    return false;
}
```

生成库存时，`okToUpdateShipsAndWeapons()` 使用基类默认的 30 生涯日间隔。完整方法达到间隔后清空旧舰船，用 `addShips(...)` 请求 30 FP 的战斗舰船，`maxShipSize` 取 2，按市场势力挑选护卫舰与驱逐舰，再清零 `sinceSWUpdate`。

生成限制与交易限制分别成立。玩家卖入的巡洋舰可以留在库存中，但购买判断仍会拒绝它；下一次舰船刷新还会重建集合。因此验证大型舰船出售后，要同时确认购买方向和后续刷新结果。

`showInFleetScreen()` 与 `showInCargoScreen()` 都返回 `true`，货物和舰船界面都显示这个子市场。货物栈与舰船各使用自己的交易重载，提示文字也分别提供。

## 设置税率和交易文字

费用采用固定的 30% 税率，`getTariff()` 返回 `0.30f`。动作文字采用 `Acquire` 与 `Refine and sell`，实际买卖方向继续由 `TransferAction` 判断。以下局部方法给出界面用值：

```java
@Override
public float getTariff() {
    return 0.30f;
}

@Override
public String getBuyVerb() {
    return "Acquire";
}

@Override
public String getSellVerb() {
    return "Refine and sell";
}
```

## 让出售商品影响市场经济

Refinery Depot 是公开子市场，`isOpenMarket()` 为 `true`，黑市与军事市场标志为 `false`，免费转移也为 `false`。这些标志供交易界面、基类规则和舰船生成使用，当前商品许可仍采用本节覆写的集合判断。

成交时，界面把交易交给 `reportPlayerMarketTransaction(...)`。基类根据 `isParticipatesInEconomy()`、影响模式与 `getPlayerTradeImpactMult()` 写入 [C12-S3] 的贸易调节。本例参与经济，数量倍率为 1，模式采用 `PLAYER_SELL_ONLY`：

```java
@Override
public boolean isParticipatesInEconomy() {
    return true;
}

@Override
public PlayerEconomyImpactMode getPlayerEconomyImpactMode() {
    return PlayerEconomyImpactMode.PLAYER_SELL_ONLY;
}
```

这个模式使用正向贸易调节：玩家净出售可以提高可得量，买回商品则抵消同一方向的调节。正向通道的最终影响以 0 为下限，所以购买不会把可得量压到原基准以下。实际库存仍在交易时转移，经济调节另按物品数量换算成档位。

::: info PLAYER_SELL_ONLY 仍记录购买
基类将出售数量写为正值，购买数量写为负值，合并后只采用正向部分。购买可以抵消此前出售带来的提升；这个模式限定的是最终影响方向。
:::

## 扩展入口与提示的可选用法

本例点击后直接打开交易面板，沿用基类 `getOnClickAction(CoreUIAPI)` 的 `OPEN_SUBMARKET`。需要先显示对话时，采用 `SHOW_TEXT_DIALOG`，通过 `getDialogText()`、`getDialogOptions()` 与 `getDialogTextHighlights()` 提供文本、动作和高亮；原版仓储就用对话处理首次开放。

交易入口的补充提示用 `getTooltipAppendix()` 与 `getTooltipAppendixHighlights()`。需要自定义 tooltip 时，再配合 `hasCustomTooltip()`、`createTooltip()`、`isTooltipExpandable()` 与 `getTooltipWidth()` 处理绘制、展开和宽度。`isHidden()` 控制入口隐藏，两种库存界面的显示由前述方法决定。

费用显示还可通过 `getTariffTextOverride()`、`getTariffValueOverride()`、`getTotalTextOverride()` 与 `getTotalValueOverride()` 覆盖。它们提供文字，实际税率和费用计算仍使用交易规则。Refinery Depot 当前只改动作与拒绝文本，基础面板已经能够表达本例交易。

### 原版子市场速查表

表中列出各子市场的交易定位，以及用于判断具体行为的主要标志。

| spec ID                 | 典型插件                    | 税率/转移            | 主要标志     | 主要用途                         |
| ----------------------- | --------------------------- | -------------------- | ------------ | -------------------------------- |
| `open_market`           | `OpenMarketPlugin`          | 普通税率、非免费转移 | 公开市场     | 合法商品与常规舰船交易           |
| `black_market`          | `BlackMarketPlugin`         | 通常免税、非免费转移 | 黑市         | 允许更多非法商品                 |
| `storage`               | `StoragePlugin`             | 免费转移             | 仓储         | 保存货物和舰船                   |
| `GearIn_refinery_depot` | `GearInRefineryDepotPlugin` | 0.30、非免费转移     | 公开工业市场 | 金属库存、矿物出售和小型舰船购买 |

## 读取子市场运行状态

规则实现后，从 Foundry 取得实际子市场验证。观察器先用 `hasSubmarket(specId)` 确认入口，再用 `getSubmarket(specId)` 取得 `SubmarketAPI`。局部查询如下：

```java
if (market.hasSubmarket("GearIn_refinery_depot")) {
    SubmarketAPI depot = market.getSubmarket("GearIn_refinery_depot");
    SubmarketPlugin plugin = depot.getPlugin();
    LOG.info("plugin=" + plugin.getClass().getName());
}
```

`getMarket()` 返回所属市场，`getPlugin()` 返回行为插件，`getSpecId()` 是集合使用的 ID。名称从 `getName()` 与 `getNameOneLine()` 读取，税率从 `getTariff()` 读取，货物转移判断由 `isIllegalOnSubmarket(...)` 转发给插件。

规格通过 `getSpec()` 读取，保存注册时的 ID、名称、描述、图标与默认势力：

```java
SubmarketSpecAPI spec = submarket.getSpec();
LOG.info(String.format("spec=%s name=%s faction=%s",
        spec.getId(), spec.getName(), spec.getFactionId()));
```

实际势力使用 `submarket.getFaction()`，`setFaction(...)` 可以修改这个运行实例，CSV 默认值保持原样。规格、当前实例与插件分别提供数据定义、所属市场和行为，日志将它们对应到同一个 spec ID。

完整观察器遍历三个原版入口与 Refinery Depot，记录货舱是否初始化及插件标志。局部记录如下：

```java
SubmarketAPI submarket = market.getSubmarket(specId);
SubmarketPlugin plugin = submarket.getPlugin();
CargoAPI cargo = submarket.getCargoNullOk();
LOG.info(String.format("%s plugin=%s cargo=%s tariff=%.2f",
        submarket.getSpecId(), plugin.getClass().getSimpleName(),
        cargo == null ? "null" : "created", plugin.getTariff()));
```

`getCargoNullOk()` 返回 `null` 时，货舱还未建立；`getCargo()` 会在需要时创建，并由基类初始化封存舰船集合。当前日志用前者观察初始化状态，打开交易后再比较实际库存。`addAllCargo(...)` 可以按栈加入外部货物，费用、权限与交易报告仍由调用流程负责。

## 完整最小例

两个类保存到前述路径，市场添加与探针调用保留在经济观察器。

::: details 完整成品：GearInRefineryDepotPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.SubmarketPlugin.PlayerEconomyImpactMode;
import com.fs.starfarer.api.campaign.SubmarketPlugin.TransferAction;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

public class GearInRefineryDepotPlugin extends BaseSubmarketPlugin {
    private static final String[] STOCKPILE_IDS = {
            "metals",
            "rare_metals",
            "heavy_machinery"
    };

    private static final String[] PLAYER_SELL_IDS = {
            "ore",
            "rare_ore",
            "heavy_machinery"
    };

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            getCargo().getMothballedShips().clear();
            addShips(market.getFactionId(), 30f, 0f, 0f, 0f, 0f, 0f,
                    null, 0f, ShipPickMode.PRIORITY_THEN_ALL, null, 2);
            sinceSWUpdate = 0f;
        }
    }

    @Override
    public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
        return contains(STOCKPILE_IDS, commodity.getId());
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI commodity) {
        return shouldHaveCommodity(commodity) ? Math.max(20, commodity.getMaxSupply() * 2) : 0;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) {
            return !contains(PLAYER_SELL_IDS, commodityId);
        }
        return !contains(STOCKPILE_IDS, commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (!stack.isCommodityStack()) {
            return false;
        }
        return isIllegalOnSubmarket(stack.getCommodityId(), action);
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return action == TransferAction.PLAYER_SELL
                ? "Only ores and heavy machinery can be sold here"
                : "This depot does not stock that commodity";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_BUY) {
            return !(member.isFrigate() || member.isDestroyer());
        }
        return false;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Only frigates and destroyers are sold by this depot";
    }

    @Override
    public float getTariff() {
        return 0.30f;
    }

    @Override
    public String getBuyVerb() {
        return "Acquire";
    }

    @Override
    public String getSellVerb() {
        return "Refine and sell";
    }

    @Override
    public boolean isFreeTransfer() {
        return false;
    }

    @Override
    public boolean isOpenMarket() {
        return true;
    }

    @Override
    public boolean isBlackMarket() {
        return false;
    }

    @Override
    public boolean isMilitaryMarket() {
        return false;
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return true;
    }

    @Override
    public float getPlayerTradeImpactMult() {
        return 1f;
    }

    @Override
    public PlayerEconomyImpactMode getPlayerEconomyImpactMode() {
        return PlayerEconomyImpactMode.PLAYER_SELL_ONLY;
    }

    @Override
    public boolean showInFleetScreen() {
        return true;
    }

    @Override
    public boolean showInCargoScreen() {
        return true;
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
```

:::

::: details 完整成品：GearInSubmarketProbe

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import org.apache.log4j.Logger;

public class GearInSubmarketProbe {
    private static final Logger LOG = Global.getLogger(GearInSubmarketProbe.class);
    private static final String[] SPEC_IDS = {
            Submarkets.SUBMARKET_OPEN,
            Submarkets.SUBMARKET_BLACK,
            Submarkets.SUBMARKET_STORAGE,
            "GearIn_refinery_depot"
    };

    private GearInSubmarketProbe() {
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
            CargoAPI cargo = submarket.getCargoNullOk();
            LOG.info(String.format(
                    "GearIn submarket %s: name=%s faction=%s tariff=%.2f plugin=%s cargo=%s open=%s black=%s freeTransfer=%s military=%s participates=%s impactMode=%s impactMult=%.1f",
                    submarket.getSpecId(), submarket.getNameOneLine(),
                    submarket.getFaction().getId(), plugin.getTariff(),
                    plugin.getClass().getSimpleName(), cargo == null ? "null" : "created",
                    plugin.isOpenMarket(), plugin.isBlackMarket(), plugin.isFreeTransfer(),
                    plugin.isMilitaryMarket(), plugin.isParticipatesInEconomy(),
                    plugin.getPlayerEconomyImpactMode(), plugin.getPlayerTradeImpactMult()));
        }
    }
}
```

:::

## 验证方案

前置数据：启用包含本节 `submarkets.csv` 注册行和 `GearInRefineryDepotPlugin`、`GearInSubmarketProbe` 两个 Java 类的 GearIn MOD，并使用拥有 `gear_industry` 市场的测试存档。

1. 保存 CSV、两类与经济观察器中的添加调用，构建重启，进入已有 Foundry 市场的存档并推进一帧。
2. 打开市场入口，确认出现 `Refinery Depot`，并记录子市场的显示名称和图标。
3. 首次打开 Refinery Depot，观察主动补充的商品为 `metals`、`rare_metals` 和 `heavy_machinery`。
4. 尝试把 `ore`、`rare_ore`、`heavy_machinery` 和其他商品卖入，比较允许与拒绝的提示。
5. 打开舰船交易列表，确认玩家可购买的舰船只有护卫舰和驱逐舰。
6. 选择一艘更大型舰船出售，确认出售方向允许，再尝试购买同一艘，确认购买方向仍按舰级拒绝。
7. 查看 30% 税率、`Acquire`、`Refine and sell` 与拒绝商品时的文字，核对文字说明的矿石和机械范围。
8. 查看入口执行时的 `GearIn submarket` 日志，再重新读档执行探针，比较四个子市场的插件、货舱初始化、开放状态与经济影响。

预期结果：

- `Refinery Depot` 的 ID、名称、势力和插件类正确；
- 税率为 `0.30`，公开市场为真，黑市为假，免费转移为假；
- 子市场主动补充三种指定商品，卖入的矿石可暂时保留并随刷新逐步减少；
- 玩家出售白名单只允许 `ore`、`rare_ore` 和 `heavy_machinery`；
- 玩家购买舰船只允许护卫舰和驱逐舰；
- 玩家出售舰船不受舰级限制；
- 经济参与为真，玩家影响模式为 `PLAYER_SELL_ONLY`，倍率为 `1.0`；
- 原版公开市场、黑市、仓储和自定义子市场的日志差异清晰可见。

## 故障分析

- **市场没有 Refinery Depot**：`submarkets.csv` 没有加载，或市场没有添加 `GearIn_refinery_depot`。
    - **解决方案**：核对 CSV 路径、ID、order 和市场子市场集合，再查看加载日志。

- **插件类创建失败**：`script` 列的完整类名与 Java 包名不一致。
    - **解决方案**：逐字核对 `data.scripts.gearin.campaign.GearInRefineryDepotPlugin`，并确认类继承 `BaseSubmarketPlugin`。

- **库存出现非金属商品**：`shouldHaveCommodity()` 白名单和库存刷新规则不一致。
    - **解决方案**：检查 `STOCKPILE_IDS` 与 `getStockpileLimit()`，不要用玩家出售白名单替代库存白名单。

- **允许出售错误商品**：交易方向没有区分 `PLAYER_SELL` 与 `PLAYER_BUY`。
    - **解决方案**：在字符串和 CargoStack 重载中先判断 `TransferAction`，再使用对应集合。

- **大型舰船无法出售**：舰船过滤代码对买卖方向使用了同一限制。
    - **解决方案**：只在 `PLAYER_BUY` 分支检查 `isFrigate()` 与 `isDestroyer()`，出售分支返回允许。

- **子市场税率或按钮文字不对**：界面读取的是插件方法返回值，CSV 的 `desc` 不负责这些状态。
    - **解决方案**：核对 `getTariff()`、`getBuyVerb()` 和 `getSellVerb()`，再重新打开交易界面。

- **货舱日志显示为空**：调用了 `getCargoNullOk()`，但玩家还没有打开或刷新该子市场。
    - **解决方案**：把 `null` 解释为尚未初始化；需要强制创建时使用 `getCargo()`，并记录这一行为。

## 总结

Refinery Depot 已将注册规格接到 Foundry，插件按经过时间补充商品与小型舰船，并按买卖方向判断许可。库存集合、收购集合和舰船重载分别决定玩家实际能进行的交易，税率与提示提供对应的费用和说明。

成交还进入市场经济记录。正向贸易调节保留净出售带来的提升，库存转移与经济档位变化分别发生。子市场的作用由这些实际规则共同完成，名称与按钮文字只是界面的表达。

## 建议参考项

- `data/campaign/submarkets.csv`：子市场规格、势力、插件和图标注册来源；
- `com.fs.starfarer.api.campaign.econ.SubmarketSpecAPI`：静态子市场规格；
- `com.fs.starfarer.api.campaign.econ.SubmarketAPI`：具体市场中的子市场运行时对象；
- `com.fs.starfarer.api.campaign.SubmarketPlugin`：子市场交易、货舱、舰船和界面行为接口；
- `com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin`：复用原版库存和交易基础行为的继承起点；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：舰船交易对象和舰级判断；
- `com.fs.starfarer.api.impl.campaign.ids.Submarkets`：原版子市场 ID 常量。
