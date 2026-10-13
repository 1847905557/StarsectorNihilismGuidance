# 实现专用子市场

Foundry 已经能交易，现在给它加一个有明确工业用途的入口。Refinery Depot 卖出精炼金属与重型机械，收购矿石与机械，舰船购买限定护卫舰和驱逐舰。注册数据决定名字与插件，插件再按库存、买卖方向和经济影响实现这些选择，界面里的每项限制都有对应方法。

*本节预期阅读时间：\~28 分钟。*

*本节预期操作时间：\~25 分钟。*

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

子市场由 CSV 行注册，但 CSV 行本身不会执行交易。它先建立一个静态规格，游戏把规格放进运行时市场后，再创建 `SubmarketAPI` 和对应的 `SubmarketPlugin`。CSV 的 ID 连接市场实例，插件类名连接具体行为。当前先登记，再添加到 Foundry 市场。

本节使用 `GearIn_refinery_depot` 作为 ID，显示名为 `Refinery Depot`。它代表工业势力专门处理金属原料、精炼金属和重型机械的交易站。`gear_industry` 是它的默认势力；市场创建或接入时仍可以按实际场景决定最终归属。

文件位置是 `data/campaign/submarkets.csv`。MOD 提交自己的数据行，并保持原版表头的列顺序：

```csv
id,name,faction,desc,script,icon,order
GearIn_refinery_depot,Refinery Depot,gear_industry,"An industrial depot for metal stockpiles and refinery equipment.",data.scripts.gearin.campaign.GearInRefineryDepotPlugin,graphics/factions/neutral_traders.png,1100
```

`id` 是代码和市场集合使用的稳定键，`name` 是玩家看到的名称，`faction` 提供默认势力，`desc` 描述市场定位，`script` 指向插件类，`icon` 提供入口图标，`order` 决定同一市场中子市场的排列位置。`order` 只影响排序，不改变交易优先级。

::: tip 注册与类名一起核对

CSV 能被读取而插件类缺失时，子市场规格存在但运行时无法创建行为对象。插件类能编译而 CSV 行拼写错误时，市场又不会出现对应入口。排查时先对照 ID，再对照完整类名和包路径。

:::

## 把新入口添加到 Foundry

创建 `src/data/scripts/gearin/campaign/GearInRefineryDepotPlugin.java` 和 `GearInSubmarketProbe.java`。在 [C12-S1] 的 `GearInEconomyProbe.advance(...)` 已取得市场之后、调用市场日志之前添加局部片段：

```java
market.addSubmarket("GearIn_refinery_depot");
GearInSubmarketProbe.log(market);
```

市场已经有同 ID 子市场时，`addSubmarket(String)` 保留原实例。第一次添加后，探针会记录刚建立的交易入口；打开界面时再初始化货舱并刷新库存。

### 读取规格与运行实例

`SubmarketSpecAPI` 保存注册阶段的静态信息。通过 `SubmarketAPI.getSpec()` 取得它，再读取规格 ID、名称、描述、默认势力和图标等字段。规格适合回答这个子市场在数据文件中被定义成什么。

```java
SubmarketSpecAPI spec = submarket.getSpec();
LOG.info(String.format("spec=%s name=%s faction=%s",
        spec.getId(), spec.getName(), spec.getFactionId()));
```

规格对象不是交易对象。它不会保存某个市场当前货舱，也不会代替插件判断某件货物是否合法。进入市场以后，代码应从 `SubmarketAPI` 取得运行时实例，再从 `getPlugin()` 取得实际行为。

`script` 列使用 Java 类的完整包名。加载器根据这个名称创建 `SubmarketPlugin`，然后调用插件的 `init(SubmarketAPI)`，让插件获得市场和子市场引用。类名、包名或大小写错误都会在创建运行时对象时暴露出来。

## 从市场取得子市场实例

市场持有子市场集合。调用方先用 `market.hasSubmarket(specId)` 判断，再用 `market.getSubmarket(specId)` 取得具体实例。不存在时不要直接解引用返回值；缺少入口可能是 CSV 没加载，也可能是该市场没有添加这个规格。

```java
if (market.hasSubmarket("GearIn_refinery_depot")) {
    SubmarketAPI depot = market.getSubmarket("GearIn_refinery_depot");
    SubmarketPlugin plugin = depot.getPlugin();
    LOG.info("plugin=" + plugin.getClass().getName());
}
```

`SubmarketAPI` 是某个市场中的运行时持有者。`getMarket()` 返回所属市场，`getSpec()` 返回静态规格，`getPlugin()` 返回行为插件，`getFaction()` 返回当前势力。`setFaction()` 能改变运行时势力，但它不会修改 CSV 的默认值。

`getSpecId()` 是子市场集合索引，`getName()` 和 `getNameOneLine()` 提供显示名称，`getTariff()` 读取当前税率，`getCargo()` 取得可写货舱，`getCargoNullOk()` 只读取已经建立的货舱。`isIllegalOnSubmarket(CargoStackAPI, TransferAction)` 是 API 层的转发入口，最终交易判断仍由插件提供。

## 子市场插件的初始化与推进

`SubmarketPlugin` 是本节的核心。它不只是一组标志 getter，而是子市场在运行时执行的行为集合：插件负责初始化、推进时间、刷新货舱、判断交易、返回界面文字，并决定玩家交易是否影响市场经济。

绑定时调用 `init(SubmarketAPI)`，基类保存 `market` 与 `submarket`。需要额外初始化时覆写并先调用 `super.init(...)`；当前没有新增初始化状态，沿用默认实现即可。

`advance(float amount)` 接收生涯时间推进。它适合累计刷新间隔或处理插件自己的计时；货舱刷新通常集中在 `updateCargoPrePlayerInteraction()`，这样玩家打开交易界面前可以得到最新状态。

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

`advance(...)` 把秒换成生涯日，更新两种刷新间隔。当前仍用基类实现，货物与舰船集中在交互前刷新；它们由经过时间与既有库存决定。**每帧推进计时，交互前执行库存更新。**

## 刷新子市场货舱库存

`getCargo()` 会在需要时创建货舱，`getCargoNullOk()` 只返回已存在对象。基类创建货舱时还会初始化封存舰船集合；这也是为什么插件代码可以把普通商品和封存舰船放在同一个 Cargo 容器中，却仍然使用不同的 API 处理它们。

`addAllCargo(CargoAPI)` 按栈合并另一份货舱。它适合把奖励或外部货物并入子市场，但不负责费用、权限或交易记录。子市场交易流程应由界面和插件共同完成，不能用一次 `addAllCargo()` 代替真实交易。

`updateCargoPrePlayerInteraction()` 是玩家打开交易界面前的刷新入口。Refinery Depot 复用基类的 `addAndRemoveStockpiledResources()` 流程，只覆盖 `shouldHaveCommodity()` 和 `getStockpileLimit()`，这样市场经济仍然提供供需数据，插件负责限制库存集合。

本例的刷新实现先把经过的时间交给基类库存流程，再按更新间隔清空旧舰船并调用 `addShips()`。`maxShipSize` 设为 `2`，让基类生成流程只挑选护卫舰和驱逐舰；玩家出售大型舰船的判断仍由另一个交易方向重载负责。

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

`shouldHaveCommodity()` 只回答库存刷新时是否保留该商品。玩家出售另由 `isIllegalOnSubmarket(String, TransferAction)` 判断。库存集合回答商店提供什么，出售集合回答商店收什么，本例按炼厂的两种用途分别设计。

## 过滤商品交易

Refinery Depot 的库存集合是 `metals`、`rare_metals` 和 `heavy_machinery`。玩家出售集合是 `ore`、`rare_ore` 和 `heavy_machinery`。这两个集合有意不同：市场可以卖出精炼后的金属，玩家则把矿物和重型机械卖给炼厂。

`isIllegalOnSubmarket(String commodityId, TransferAction action)` 接收商品 ID 和交易方向。`PLAYER_BUY` 方向检查市场库存白名单，`PLAYER_SELL` 方向检查玩家出售白名单。返回 `true` 表示该交易不允许继续。

```java
@Override
public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    if (action == TransferAction.PLAYER_SELL) {
        return !contains(PLAYER_SELL_IDS, commodityId);
    }
    return !contains(STOCKPILE_IDS, commodityId);
}
```

货物栈重载先确认商品，再转交字符串方法。当前商品按两份集合判断，其他栈沿默认允许路径；实际刷新只生成本节商品与小型舰船。需要进一步限制装备交易时，再按栈类型增加对应规则。

```java
@Override
public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
    if (!stack.isCommodityStack()) {
        return false;
    }
    return isIllegalOnSubmarket(stack.getCommodityId(), action);
}
```

`getIllegalTransferText(CargoStackAPI, TransferAction)` 提供玩家看到的失败原因。它不负责做判断，判断已经在 `isIllegalOnSubmarket` 完成；两者都需要维护时，应先修改白名单，再修改对应的提示文本。

## 限制舰船交易

舰船交易使用另一个重载：`isIllegalOnSubmarket(FleetMemberAPI, TransferAction)`。这条入口与货物栈重载完全分开，不能把舰船对象转换成 CargoStackAPI。

Refinery Depot 只限制玩家购买：`PLAYER_BUY` 时允许 `member.isFrigate()` 或 `member.isDestroyer()`，巡洋舰、主力舰和其他舰级被拒绝；`PLAYER_SELL` 时返回 `false`，所以玩家仍可出售更大型舰船。

```java
@Override
public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
    if (action == TransferAction.PLAYER_BUY) {
        return !(member.isFrigate() || member.isDestroyer());
    }
    return false;
}
```

`okToUpdateShipsAndWeapons()` 控制插件是否到了更新舰船和武器库存的时间。自定义插件可以复用基类的刷新间隔；如果重新生成舰船，应在更新时清理旧集合，并通过基类的舰船添加方法限制 `maxShipSize`。

`showInFleetScreen()` 和 `showInCargoScreen()` 决定子市场是否出现在舰队和货舱相关界面。Refinery Depot 两者都返回 `true`，因为它既处理货物也提供舰船交易。

## 设置税率和交易文字

`getTariff()` 返回本子市场的交易税率。Refinery Depot 固定返回 `0.30f`，表示本子市场固定的 30% 税率。税率只负责费用比例，不决定商品是否合法，也不决定市场库存。

`getBuyVerb()` 和 `getSellVerb()` 改变交易界面里的动作文字。Refinery Depot 使用 `Acquire` 和 `Refine and sell`，用于表达它和普通公开市场的工业定位。文字改变不改变交易方向，实际方向仍由 `TransferAction` 决定。

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

## 公开市场标志与经济影响

`isFreeTransfer()` 表示交易是否绕过普通转移费用，Refinery Depot 返回 `false`。`isOpenMarket()` 返回 `true`，`isBlackMarket()` 返回 `false`，`isMilitaryMarket()` 返回 `false`，这组标志会影响交易界面、非法货物基线和舰船生成规则。

`isParticipatesInEconomy()` 返回 `true` 时，玩家交易可以进入市场经济记录。`getPlayerEconomyImpactMode()` 返回 `PLAYER_SELL_ONLY`，意味着玩家出售的商品会影响经济调节，玩家买入不会按同一方式产生影响。

`getPlayerTradeImpactMult()` 返回 `1f`，表示不对玩家交易数量再做倍率修正。插件基类会在 `reportPlayerMarketTransaction()` 中读取这些设置，并把交易转换为市场商品的 trade modifier。

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

经济参与、玩家影响方向和价格变化是三件相关但独立的事。插件返回参与经济，不等于每一笔交易都会改变库存；影响模式也不等于直接修改 `CommodityOnMarketAPI` 的 stockpile。

## 界面入口与文本覆盖

`getOnClickAction(CoreUIAPI)` 决定点击子市场时打开交易面板还是显示文本对话框。基类默认返回 `OPEN_SUBMARKET`，Refinery Depot 沿用这个行为。

`getDialogText()`、`getDialogOptions()` 和 `getDialogTextHighlights()` 适合需要先经过对话的子市场。Refinery Depot 不实现完整对话面板，因此这些方法只在正文中说明职责，不放进自定义插件的复杂实现。

`getTooltipAppendix()` 和 `getTooltipAppendixHighlights()` 可以在交易界面追加限制说明；`isTooltipExpandable()`、`hasCustomTooltip()`、`createTooltip()` 和 `getTooltipWidth()` 则控制提示框是否展开、是否使用自定义绘制以及绘制尺寸。

`isHidden()` 控制子市场是否隐藏，`showInFleetScreen()` 与 `showInCargoScreen()` 控制两个库存入口。`getTariffTextOverride()`、`getTariffValueOverride()`、`getTotalTextOverride()` 和 `getTotalValueOverride()` 可以覆盖费用和总额文本，但不改变实际费用计算。

这些 UI 方法的职责是呈现和入口，交易白名单仍由商品与舰船的 `isIllegalOnSubmarket` 重载决定。文字与运行判断一起对应，玩家看到的拒绝原因才符合实际交易方向。

## 对照原版与自定义子市场的定位

### 原版子市场速查表

先用这张表确定四个子市场的定位，再回到插件方法判断具体行为。

| spec ID | 典型插件 | 税率/转移 | 主要标志 | 主要用途 |
| --- | --- | --- | --- | --- |
| `open_market` | `OpenMarketPlugin` | 普通税率、非免费转移 | 公开市场 | 合法商品与常规舰船交易 |
| `black_market` | `BlackMarketPlugin` | 通常免税、非免费转移 | 黑市 | 允许更多非法商品 |
| `storage` | `StoragePlugin` | 免费转移 | 仓储 | 保存货物和舰船 |
| `GearIn_refinery_depot` | `GearInRefineryDepotPlugin` | 0.30、非免费转移 | 公开工业市场 | 金属库存、矿物出售和小型舰船购买 |

这组差异只用于建立方向。实际行为仍来自插件方法：公开标志不会自动生成金属库存，免费转移也不会自动允许所有舰船，必须回到对应的插件入口确认。

## 读取子市场运行状态

观察器接收调用方传入的 `MarketAPI`，不负责选择市场、创建市场或添加子市场。它遍历三个原版子市场和 Refinery Depot，记录 `SubmarketAPI` 的规格、势力、税率、货舱初始化状态和插件标志。

```java
SubmarketAPI submarket = market.getSubmarket(specId);
SubmarketPlugin plugin = submarket.getPlugin();
CargoAPI cargo = submarket.getCargoNullOk();
LOG.info(String.format("%s plugin=%s cargo=%s tariff=%.2f",
        submarket.getSpecId(), plugin.getClass().getSimpleName(),
        cargo == null ? "null" : "created", plugin.getTariff()));
```

优先使用 `getCargoNullOk()` 做观察。它返回 `null` 时表示货舱尚未建立，不代表库存数量为零。只有需要写入或明确触发创建时，才使用 `getCargo()`。

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
3. 打开 Refinery Depot，观察库存只出现 `metals`、`rare_metals` 和 `heavy_machinery`。
4. 尝试把 `ore`、`rare_ore`、`heavy_machinery` 和其他商品卖入，比较允许与拒绝的提示。
5. 打开舰船交易列表，确认玩家可购买的舰船只有护卫舰和驱逐舰。
6. 选择一艘更大型舰船尝试出售，确认出售方向不受舰级限制。
7. 查看 30% 税率、`Acquire`、`Refine and sell` 与拒绝商品时的文字，核对文字说明的矿石和机械范围。
8. 查看入口执行时的 `GearIn submarket` 日志，再重新读档执行探针，比较四个子市场的插件、货舱初始化、开放状态与经济影响。

预期结果：

- `Refinery Depot` 的 ID、名称、势力和插件类正确；
- 税率为 `0.30`，公开市场为真，黑市为假，免费转移为假；
- 子市场库存只刷新三种指定商品；
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

Refinery Depot 从注册行接到 Foundry 市场，插件再分别处理库存、收购商品、舰船购买、税率与经济影响。买卖方向用 `TransferAction` 判断，界面文字跟随同一组实际限制。

编者建议先固定交易站的用途，再写集合与规则。本例卖金属、收矿石，两个列表有意不同；交易入口的行为就由这些具体选择组成。

## 建议参考项

- `data/campaign/submarkets.csv`：子市场规格、势力、插件和图标注册来源；
- `com.fs.starfarer.api.campaign.econ.SubmarketSpecAPI`：静态子市场规格；
- `com.fs.starfarer.api.campaign.econ.SubmarketAPI`：具体市场中的子市场运行时对象；
- `com.fs.starfarer.api.campaign.SubmarketPlugin`：子市场交易、货舱、舰船和界面行为接口；
- `com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin`：复用原版库存和交易基础行为的继承起点；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：舰船交易对象和舰级判断；
- `com.fs.starfarer.api.impl.campaign.ids.Submarkets`：原版子市场 ID 常量。
