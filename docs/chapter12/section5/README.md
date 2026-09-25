# 制作专用产业

产业把市场中的商品需求、商品产出、收入和维护费连接成一个可持续更新的生产单元。本节注册一个消耗金属、燃料和重型机械并产出稀有金属的产业，随后观察缺货、建设状态、AI core、改良和中断如何影响它。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 在 `industries.csv` 中注册一个产业规格；
- 通过 `IndustrySpecAPI` 读取静态注册数据；
- 使用 `BaseIndustry` 实现产业生命周期；
- 在 `apply()` 中声明规模化需求和产出；
- 使用缺货接口削减产业产出；
- 读取建设、升级、功能、收入和维护状态；
- 成对处理 AI core 修饰和 disruption 回调；
- 通过市场界面和日志确认产业运行结果。

## 前置知识

- [C12-S1] 的市场、势力和产业访问入口；
- [C12-S3] 的商品 ID、商品规格和市场商品状态；
- [C7-S1] 的插件生命周期和持久数据；
- [C8-S4] 的 `MutableStat` 与修饰值；
- [C10-S1] 的经济步、生涯时钟和游戏日换算。

## 产业与市场的数据关系

产业挂在一个 `MarketAPI` 上。市场提供规模、商品库存、经济刷新和建设队列，产业根据这些输入声明自己的需求和产出。经济步读取产业声明，再把结果传播到市场商品价格、库存、短缺和贸易界面。

产业有三层数据关系。`industries.csv` 保存静态规格，`IndustrySpecAPI` 代表这条规格，`Industry` 代表市场中的运行时接口，`BaseIndustry` 提供大多数通用实现。CSV 的 `plugin` 列指向一个可无参构造的 Java 类，市场添加产业时会实例化这个类并调用 `init()`。

本节的产业 ID 是 `GearIn_element_works`，显示名是 `Element Works`。它消耗 `metals`、`fuel` 和 `heavy_machinery`，产出 `rare_metals`。商品数量随市场规模线性变化，因此同一个产业放在不同规模的市场中会产生不同的供需值。

## 注册产业规格

产业数据文件位于 `data/campaign/industries.csv`。原版表头包含 15 列，MOD 数据行必须保持相同顺序：

```csv
id,name,cost mult,build time,income,upkeep,downgrade,upgrade,tags,data,image,plugin,desc,order,disruptDanger
GearIn_element_works,Element Works,45,90,,3,,,"industry, industrial",,graphics/icons/industry/refining.png,data.scripts.gearin.campaign.GearInElementWorksIndustry,"Consumes metals, fuel, and heavy machinery to produce rare metals.",510,MEDIUM
```

注册行的 `id` 是全局查找键。`MarketAPI.addIndustry("GearIn_element_works")` 和 `market.getIndustry("GearIn_element_works")` 都依赖这个值。

`name` 是默认显示名。运行时名称还可能受到本地化或插件状态影响，因此界面读取通常使用 `getCurrentName()`。

`cost mult` 和 `build time` 为建设流程提供基础参数。本例沿用原版 `refining` 的 45 成本和 90 天建设时间。

`income` 留空，`upkeep` 使用原版 `refining` 的 3。基类会按市场规模和危险度修饰维护费；本节的产业没有独立收入基础值。

`downgrade` 和 `upgrade` 留空表示产业没有预设的升级链。升级与降级接口仍然会在正文中说明，真正的升级链需要另一个已经注册的目标产业。

`tags` 使用 `industry, industrial`。前者让产业计入产业数量，后者用于产业分类和部分界面筛选。

`data` 是传给产业规格的附加字符串。本例没有额外参数，因此留空。

`image` 指向产业图像。示例使用原版精炼产业图标，MOD 也可以使用自己的资源路径。

`plugin` 必须填写完整 Java 类名。类名、包名和文件路径任何一处不一致，产业都无法被正确实例化。

`desc` 是产业规格描述，通常显示在产业 tooltip 中。它描述静态定位，不承担运行时产出计算。

`order` 影响产业列表中的排序。本例使用 510，排在原版 `refining` 的 500 之后。

`disruptDanger` 选择突袭或其他行动施加中断时使用的危险等级。本例与 `refining` 一样使用 `MEDIUM`。

## 读取产业规格

`IndustrySpecAPI` 是读取 CSV 结果的入口。它描述产业身份、显示、建设和经济参数，不持有某个市场的当前供需值。

```java
IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(
        GearInElementWorksIndustry.INDUSTRY_ID);
if (spec != null) {
    LOG.info(String.format("id=%s name=%s plugin=%s build=%.0f cost=%.0f",
            spec.getId(), spec.getName(), spec.getPluginClass().getName(),
            spec.getBuildTime(), spec.getCost()));
}
```

`getId()`、`getName()` 和 `getPluginClass()` 对应 CSV 的身份和插件列。`getImageName()`、`getDesc()` 和 `getOrder()` 对应界面资源、描述和排序。

`getCost()`、`getBuildTime()`、`getIncome()` 和 `getUpkeep()` 是建设与经济的基础值。市场规模、稳定度、危险度、AI core 和改良状态会在运行时影响最终统计项。

`getUpgrade()` 和 `getDowngrade()` 返回目标产业 ID。目标为空时，产业没有对应方向的转换；目标存在时，目标 ID 仍然必须能在全局产业规格表中找到。

`getTags()` 返回标签集合。`hasTag()` 适合判断 `industry`、`industrial`、`structure` 等分类。

`getDisruptDanger()` 返回 `RaidDangerLevel`。它只提供中断危险等级，实际中断状态由运行时产业和市场 memory 保存。

规格对象适合用于注册检查和界面描述。商品数量、功能状态、缺货和中断天数属于运行时产业，应从 `Industry` 实例读取。

## 取得运行时产业

市场添加产业时使用注册 ID：

```java
if (!market.hasIndustry(GearInElementWorksIndustry.INDUSTRY_ID)) {
    market.addIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
}
Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
```

`hasIndustry()` 先做存在判断，避免重复添加。`addIndustry()` 会从规格表取出插件类，实例化后调用 `init(id, market)`，随后执行一次 `apply()`。

`Industry` 是调用方依赖的稳定接口。它提供市场、规格、供需、收入、维护、建设和中断状态。自定义实现通常继承 `BaseIndustry`，让基类承担通用状态和撤销流程。

`BaseIndustry` 内部保存 `supply`、`demand`、`income`、`upkeep`、`building`、`buildProgress`、`upgradeId`、`aiCoreId`、`improved` 和特殊物品状态。子类只需要在明确的钩子中补充自己的规则。

产业实例属于具体市场。读取某市场的产业时，始终从该市场重新取得对象，不把实例跨市场缓存。

## 生命周期方法的触发时机

`init(String id, MarketAPI market)` 建立产业与市场的关系。基类会保存 ID 和市场引用，并读取对应 `IndustrySpecAPI`。

`apply()` 是产业声明供需和应用修饰的主要入口。市场添加产业、经济重算、稳定度变化和外部调用都可能触发它。

`unapply()` 撤销当前产业写入的修饰。移除产业、重新应用产业和开始建设时，基类会先走撤销流程。

`reapply()` 的语义是先 `unapply()` 再 `apply()`。自定义产业应让同一修饰 ID 在两个方向保持一致。

`advance(float amount)` 接收市场推进的帧时间。基类在这里推进建设进度，并检测 disruption 是否结束。

供需只能在 `apply()` 中重建。存档清理会释放供需和收入对象，读档恢复后市场会重新调用产业应用逻辑。

## 写入规模化供需

本例使用市场规模作为数量基准：

```java
int size = market.getSize();
int metals = Math.max(1, size);
int fuel = Math.max(1, size / 2);
int machinery = Math.max(1, size / 2);
int output = Math.max(1, size - 1);
```

规模 1 的市场也会保留至少 1 单位输入和产出。规模 6 的市场会需求 6 金属、3 燃料、3 重型机械，并在完整供给时产生 5 单位稀有金属。

`demand()` 把商品需求写入产业的需求 map：

```java
demand(Commodities.METALS, metals, "Metal feedstock");
demand(Commodities.FUEL, fuel, "Refinery fuel");
demand(Commodities.HEAVY_MACHINERY, machinery, "Refinery equipment");
```

`supply()` 把商品产出写入供给 map：

```java
supply(Commodities.RARE_METALS, output, "Rare element output");
```

数量进入 `MutableCommodityQuantity`，再由市场、管理员、AI core、改良和外部修饰合并计算。产业代码读取时应使用 `getQuantity().getModifiedValue()`，因为原始数量可能已经被修饰。

`super.apply(true)` 应放在子类供需声明之前。基类会先重建收入、维护、AI core 和改良修饰，子类随后写入本产业的商品流。

## 计算输入短缺

需求声明只表示产业想要多少商品，市场库存是否满足需求由市场商品对象提供。`getMaxDeficit()` 会在给定输入中寻找最大的缺口：

```java
Pair<String, Integer> deficit = getMaxDeficit(
        Commodities.METALS, Commodities.FUEL, Commodities.HEAVY_MACHINERY);
```

缺口大致等于修改后的需求减去市场可得量。返回的 `Pair` 包含商品 ID 和缺口数量；没有正缺口时，数量为零。

`getAllDeficit()` 返回所有正缺口，适合需要分别记录多种输入短缺的产业。它不会自动改变产出。

`applyDeficitToProduction()` 把指定缺口转成负供给修饰：

```java
applyDeficitToProduction(1, deficit, Commodities.RARE_METALS);
```

第一个参数是每单位缺口削减的产出数量。本例中，最大的输入缺口每增加 1，稀有金属产出减少 1。

该方法只会削减已经声明过的供给。若目标商品没有通过 `supply()` 写入供给 map，调用不会凭空创建产出。

短缺削减发生在同一轮 `apply()` 中。经济步随后读取新的修改后供给，并更新市场商品状态。

## 观察收入与维护费

`getIncome()` 与 `getUpkeep()` 返回产业级 `MutableStat`。基类根据规格基础值、市场规模、稳定度、危险度、AI core 和功能状态计算它们。

```java
int income = (int) industry.getIncome().getModifiedValue();
int upkeep = (int) industry.getUpkeep().getModifiedValue();
```

产业处于建设中或 disruption 状态时，`isFunctional()` 返回 `false`，收入修饰会被基类移除，供需结果也应按停摆状态理解。

市场经济步会使用产业供需更新商品价格和库存。产业类读取自己的收入和维护值，市场类读取所有产业汇总后的商品状态。

## 读取建设与升级状态

`isBuilding()` 表示首次建设或升级正在进行。`isUpgrading()` 表示建设状态中存在升级目标。

`getBuildOrUpgradeProgress()` 返回当前进度比例，`getBuildTime()` 返回本次建设或升级的天数。

`startBuilding()` 会清零进度并调用 `unapply()`，因此未建成产业不会继续贡献正常供需。

`startUpgrading()` 读取规格中的 `upgrade` 目标，目标规格必须已经注册。

`finishBuildingOrUpgrading()` 在达到建设时间后完成首次建设或替换为升级目标，并迁移 AI core 与改良状态。

`downgrade()` 使用规格中的 `downgrade` 目标。目标为空时，调用方应先通过 `canDowngrade()` 判断。

本例的 CSV 没有升级和降级目标，因此完整代码只读取这些状态。需要升级链时，新增目标产业行并在 UI 或建设队列中调用对应入口。

## 应用和撤销 AI core

`setAICoreId()` 保存安装的 AI core ID。基类根据该 ID 调用 `applyAlphaCoreModifiers()`、`applyBetaCoreModifiers()`、`applyGammaCoreModifiers()` 或 `applyNoAICoreModifiers()`。

本例让 Alpha core 为市场稳定度增加 1 点：

```java
@Override
protected void applyAlphaCoreModifiers() {
    market.getStability().modifyFlat(getModId(2), 1,
            "Alpha core (" + getNameForModifier() + ")");
}

@Override
protected void applyNoAICoreModifiers() {
    market.getStability().unmodifyFlat(getModId(2));
}
```

两个方法使用同一个修饰 ID。重新应用、卸载或移除产业时，基类会调用撤销方向，避免稳定度修饰残留。

基类也会处理 AI core 对供需和维护费的原版修饰。子类只添加自己的专用效果，不重复实现原版 core 规则。

## 读取改良状态

`isImproved()` 返回产业是否已经改良，`setImproved(boolean)` 改变状态，`applyImproveModifiers()` 负责应用规格或插件定义的改良修饰。

改良通常由产业界面和故事点流程触发。观察类可以读取改良状态并记录前后变化：

```java
LOG.info("improved=" + industry.isImproved());
```

本例不伪造改良菜单，也不新增特殊改良效果。这样日志可以确认状态，而改良具体效果仍由基类和原版产业规则负责。

## 写入和结束中断状态

`setDisrupted(float days)` 在市场 memory 中写入中断倒计时。`isDisrupted()` 判断当前状态，`getDisruptedDays()` 返回剩余天数。

```java
industry.setDisrupted(10f, false);
LOG.info("functional=" + industry.isFunctional());
industry.setDisrupted(0f);
```

中断期间产业不再被视为正常运行，收入和生产应按停摆状态观察。市场继续推进时间，倒计时结束后基类会调用 `disruptionFinished()`。

本例覆盖 `notifyDisrupted()` 和 `disruptionFinished()`，只记录日志，不修改中断时长。

中断 key 默认由产业插件类名参与生成。多个产业共用同一个 Java 类时，应根据实际需求覆写 `getDisruptedKey()`，避免它们共享倒计时。

## 产业声明与市场经济步的衔接

产业的 `apply()` 写入市场侧供需，市场经济步再调用商品市场对象的更新逻辑。`CommodityOnMarketAPI` 的库存、可得量、最大供需和价格由市场经济状态决定。

因此，产业日志适合回答 "产业声明了什么"，市场商品日志适合回答 "市场最终得到了什么"。两者的数值可能因为其他产业、管理员、条件和势力修饰而不同。

观察产业时先取得 `MarketAPI`，再通过 `getIndustry(INDUSTRY_ID)` 取得运行时实例。观察器不创建市场，也不代替市场经济步。

## 对照原版产业的生产规则

下面的速查表用于比较输入、产出和本节示例的教学重点。

### 原版产业速查表

| 产业 | 输入与产出 | 典型插件 | 本节关注点 |
| --- | --- | --- | --- |
| `mining` | 矿石与有机物输入，矿产输出 | `Mining` | 多商品供给和地表条件 |
| `refining` | `ore`、`rare_ore` 输入，`metals`、`transplutonics` 输出 | `Refining` | 原版精炼生产链 |
| `GearIn_element_works` | `metals`、`fuel`、`heavy_machinery` 输入，`rare_metals` 输出 | `GearInElementWorksIndustry` | 规模化需求、最大短缺和状态观察 |

原版插件提供平衡后的生产规则。GearIn 示例保留同一套 `BaseIndustry` 生命周期，只替换供需集合和缺货削减目标。

## 完整最小例

### 完整成品：GearInElementWorksIndustry

::: details 完整成品：GearInElementWorksIndustry

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

public class GearInElementWorksIndustry extends BaseIndustry {
    public static final String INDUSTRY_ID = "GearIn_element_works";
    private static final Logger LOG = Global.getLogger(GearInElementWorksIndustry.class);

    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();
        int metals = Math.max(1, size);
        int fuel = Math.max(1, size / 2);
        int machinery = Math.max(1, size / 2);
        int output = Math.max(1, size - 1);

        demand(Commodities.METALS, metals, "Metal feedstock");
        demand(Commodities.FUEL, fuel, "Refinery fuel");
        demand(Commodities.HEAVY_MACHINERY, machinery, "Refinery equipment");
        supply(Commodities.RARE_METALS, output, "Rare element output");

        Pair<String, Integer> deficit = getMaxDeficit(
                Commodities.METALS, Commodities.FUEL, Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit, Commodities.RARE_METALS);

        LOG.info(String.format(
                "Element Works applied: market=%s size=%d output=%d income=%d upkeep=%d functional=%s disrupted=%s aiCore=%s improved=%s",
                market.getName(), size,
                (int) getSupply(Commodities.RARE_METALS).getQuantity().getModifiedValue(),
                (int) getIncome().getModifiedValue(), (int) getUpkeep().getModifiedValue(),
                isFunctional(), isDisrupted(), aiCoreId == null ? "none" : aiCoreId, isImproved()));
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }

    @Override
    protected void notifyDisrupted() {
        LOG.info(String.format("Element Works disrupted: market=%s days=%.1f",
                market.getName(), getDisruptedDays()));
    }

    @Override
    protected void disruptionFinished() {
        LOG.info("Element Works disruption finished: market=" + market.getName());
    }

    public static void logState(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("Element Works state: not present at " + market.getName());
            return;
        }
        StringBuilder supply = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllSupply()) {
            if (supply.length() > 0) supply.append(", ");
            supply.append(quantity.getCommodityId()).append("=")
                    .append((int) quantity.getQuantity().getModifiedValue());
        }
        StringBuilder demand = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllDemand()) {
            if (demand.length() > 0) demand.append(", ");
            demand.append(quantity.getCommodityId()).append("=")
                    .append((int) quantity.getQuantity().getModifiedValue());
        }
        LOG.info(String.format(
                "Element Works state: id=%s spec=%s supply=[%s] demand=[%s] income=%d upkeep=%d building=%s upgrading=%s functional=%s improved=%s disrupted=%s aiCore=%s",
                industry.getId(), industry.getSpec().getId(), supply, demand,
                (int) industry.getIncome().getModifiedValue(), (int) industry.getUpkeep().getModifiedValue(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(), industry.isImproved(),
                industry.isDisrupted(), industry.getAICoreId() == null ? "none" : industry.getAICoreId()));
    }

    public static void runDisruptionProbe(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) return;
        LOG.info(String.format("Element Works disruption before: disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
        industry.setDisrupted(10f, false);
        LOG.info(String.format("Element Works disruption during: disrupted=%s days=%.1f functional=%s",
                industry.isDisrupted(), industry.getDisruptedDays(), industry.isFunctional()));
        industry.setDisrupted(0f);
        LOG.info(String.format("Element Works disruption after: disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
    }
}
```

:::

### 完整成品：GearInIndustryProbe

::: details 完整成品：GearInIndustryProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class GearInIndustryProbe {
    private static final Logger LOG = Global.getLogger(GearInIndustryProbe.class);

    private GearInIndustryProbe() {
    }

    public static void log(MarketAPI market) {
        Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
        if (industry == null) {
            LOG.info("Industry probe: Element Works not present at " + market.getName());
            return;
        }
        LOG.info(String.format(
                "Industry probe: market=%s id=%s name=%s size=%d income=%d upkeep=%d building=%s upgrading=%s functional=%s improved=%s disrupted=%s aiCore=%s",
                market.getName(), industry.getId(), industry.getCurrentName(), market.getSize(),
                (int) industry.getIncome().getModifiedValue(), (int) industry.getUpkeep().getModifiedValue(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(), industry.isImproved(),
                industry.isDisrupted(), industry.getAICoreId() == null ? "none" : industry.getAICoreId()));
        LOG.info(String.format("Industry probe: metals=%d fuel=%d heavyMachinery=%d rareMetals=%d",
                (int) industry.getDemand(Commodities.METALS).getQuantity().getModifiedValue(),
                (int) industry.getDemand(Commodities.FUEL).getQuantity().getModifiedValue(),
                (int) industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().getModifiedValue(),
                (int) industry.getSupply(Commodities.RARE_METALS).getQuantity().getModifiedValue()));
    }
}
```

:::

## 验证方案

前置数据：启用包含本节产业注册行和两个 Java 类的 GearIn MOD，并使用拥有 `gear_industry` 市场的测试存档。

1. 启动游戏并进入目标市场，在产业列表中添加 `GearIn_element_works`。
2. 打开产业详情，核对显示名、图标、描述和插件类 `GearInElementWorksIndustry`。
3. 在规模为 6 的市场运行 `GearInIndustryProbe.log(market)`，读取三项需求、`rare_metals` 供给、收入、维护费和功能状态。
4. 降低市场中 `metals`、`fuel` 或 `heavy_machinery` 的可得量，调用产业 `reapply()`，再次记录 `rare_metals` 供给和最大缺口。
5. 将产业置于建设状态并推进市场时间，读取 `isBuilding()`、`getBuildOrUpgradeProgress()` 和 `isFunctional()`。
6. 安装 Alpha core 并重新应用产业，读取市场稳定度；移除 Alpha core 后再次应用产业，读取同一修饰是否撤销。
7. 调用 `GearInElementWorksIndustry.runDisruptionProbe(market)`，记录中断前、期间和清除后的状态，再推进市场时间观察结束回调。

预期结果：

- 产业列表出现 `Element Works`，插件类为 `GearInElementWorksIndustry`；
- 规模 6 时需求为 6 `metals`、3 `fuel`、3 `heavy_machinery`，理论产出为 5 `rare_metals`；
- 输入商品出现缺口后，`getMaxDeficit()` 返回正缺口，`rare_metals` 修改后供给低于理论产出；
- 市场规模改变后，三项需求和理论产出按同一规模规则变化；
- 建设期间 `isBuilding()` 为 `true`，`isFunctional()` 为 `false`，进度随市场时间推进；
- Alpha core 安装后稳定度出现固定修饰，移除或重算后该修饰被撤销；
- disruption 期间 `isDisrupted()` 为 `true`、`isFunctional()` 为 `false`，清除并推进时间后出现 `disruptionFinished()` 日志。

## 故障分析

- **产业没有出现在市场列表**：
    - **解决方案**：检查 CSV 行是否位于 `data/campaign/industries.csv`，并确认市场没有同 ID 的旧实例。

- **产业 ID 找不到**：
    - **解决方案**：逐字核对 `GearIn_element_works` 的大小写、下划线和插件常量。

- **插件无法实例化**：
    - **解决方案**：检查完整类名、包路径、无参构造和 JAR 内部路径。

- **需求或产出为零**：
    - **解决方案**：确认供需声明位于 `apply()`，并确认市场已经完成一次经济重算。

- **短缺没有减少产出**：
    - **解决方案**：确认 `getMaxDeficit()` 的输入列表只包含已声明需求，并把缺口传给 `applyDeficitToProduction()`。

- **缺货削减方向错误**：
    - **解决方案**：检查削减目标是否为 `Commodities.RARE_METALS`，并核对输入商品与产出目标的方向。

- **AI core 修饰残留**：
    - **解决方案**：检查 `applyAlphaCoreModifiers()` 与 `applyNoAICoreModifiers()` 是否使用相同的修饰 ID。

- **中断状态没有恢复**：
    - **解决方案**：确认市场时间继续推进，并检查 `setDisrupted(0f)` 是否仅用于可回滚测试。

- **改良状态与界面不同步**：
    - **解决方案**：先执行产业重算，再读取 `isImproved()`。

- **建设进度异常**：
    - **解决方案**：检查 `build time`、建设队列和市场经济步，升级目标为空时不要调用升级入口。

## 总结

产业注册由 CSV 规格和 Java 插件共同完成。`IndustrySpecAPI` 负责读取静态身份和建设参数，`BaseIndustry` 负责运行时状态，子类在 `apply()` 中声明商品需求和产出。

缺货处理由需求、市场可得量和产出削减三部分组成。`getMaxDeficit()` 找到最大输入缺口，`applyDeficitToProduction()` 把缺口转换为产出负修饰，经济步再把修改后的供需传播到市场商品状态。

建设、AI core、改良和 disruption 都通过基类生命周期进入产业。自定义修饰使用成对的应用与撤销入口，观察器通过市场取得产业并记录最终状态。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.Industry`：产业运行时接口；
- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`：产业生命周期与供需基类；
- `com.fs.starfarer.api.loading.IndustrySpecAPI`：`industries.csv` 静态规格读取入口；
- `com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity`：供需数量与修饰值；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场产业、商品和经济步入口。
