# 制作专用产业

本节给 Foundry 添加 Element Works，消耗金属、燃料与重型机械，生产稀有金属。输入不足时削减产出，建设或中断时停止供给，安装 Alpha core 后另提供稳定度效果。我们围绕这条生产过程实现产业，说明规模、缺货与功能状态怎样共同决定结果。

_本节预期阅读时间：\~30 分钟。_

_本节预期操作时间：\~45 分钟。_

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
- [C12-S2] 的商品 ID、商品规格和市场商品状态；
- [C7-S1] 的插件生命周期和持久数据；
- [C8-S2] 的 `MutableStat` 与修饰值；
- [C12-S1] 的经济更新监听与市场查询。

## 注册 Element Works

在 `data/campaign/industries.csv` 添加以下记录，产业 ID 使用 `GearIn_element_works`，插件指向本节的 `GearInElementWorksIndustry`：

```csv
id,name,cost mult,build time,income,upkeep,downgrade,upgrade,tags,data,image,plugin,desc,order,disruptDanger
GearIn_element_works,Element Works,45,90,,3,,,"industry, industrial",,graphics/icons/industry/refining.png,data.scripts.gearin.campaign.GearInElementWorksIndustry,"Consumes metals, fuel, and heavy machinery to produce rare metals.",510,MEDIUM
```

成本倍率 45、建设时间 90 生涯日与维护倍率 3 沿用原版 `refining`，图像也采用精炼产业图标。`income` 留空，本例没有独立的收入基础值；生产贡献继续进入市场经济。`order` 取 510，排在原版精炼的 500 之后。

`industry` 标签让它计入产业数量，`industrial` 提供分类。`desc` 给出静态说明，真正的供需与状态由 `plugin` 类实现。该类通过无参构造创建，完整类名与包路径逐字符对应。升级、降级与附加 `data` 当前留空，`disruptDanger` 使用 `MEDIUM`。

## 将产业添加到 Foundry

保存 `GearInElementWorksIndustry.java` 与 `GearInIndustryProbe.java` 到 `src/data/scripts/gearin/campaign/`。在 [C12-S1] 的 `GearInEconomyProbe.advance(...)` 取得市场后添加以下局部操作：

```java
if (!market.hasIndustry(GearInElementWorksIndustry.INDUSTRY_ID)) {
    market.addIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
}
Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
```

`addIndustry(...)` 从注册规格取得插件，调用 `init(id, market)` 建立归属，再执行 `apply()`。本例用 `Industry` 接口取得市场中的运行实例，已经存在时沿用它。后台调用直接添加成品产业，建设状态由后面的实验另行设置。

然后在 `ProbeListener.economyUpdated()` 的市场日志之后加入局部观察：

```java
GearInIndustryProbe.log(market);
```

两类与观察器位于同一包。产业先声明供需，经济更新再汇总市场结果，因此观察放在整轮结束后。接下来实现 `apply()`，让这份声明随市场规模与运行状态重新计算。

## 按市场规模声明生产

`GearInElementWorksIndustry` 继承 `BaseIndustry`，由基类保存供需、收入、维护、建设、核心、改良和特殊物品状态。自己的 `apply()` 先调用 `super.apply(true)`，更新通用供需修饰、收入维护与安装效果，再定义当前生产规则。

金属需求取市场规模，燃料与机械各取规模的一半，稀有金属理论产出取规模减 1。以下局部片段计算这些档位，整数除法用于燃料与机械：

```java
int size = market.getSize();
int metals = Math.max(1, size);
int fuel = Math.max(1, size / 2);
int machinery = Math.max(1, size / 2);
int output = Math.max(1, size - 1);
```

Foundry 规模为 3，基准需求是 3 金属、1 燃料、1 机械，理论产出为 2。规模 6 时分别为 6、3、3 与 5；规模 1 也保留至少 1 档输入和产出。[C12-S2] 已说明这些档位与货舱件数的区别。

通过 `demand(...)` 将三项输入写入需求：

```java
demand(Commodities.METALS, metals, "Metal feedstock");
demand(Commodities.FUEL, fuel, "Refinery fuel");
demand(Commodities.HEAVY_MACHINERY, machinery, "Refinery equipment");
```

通过 `supply(...)` 声明产出：

```java
supply(Commodities.RARE_METALS, output, "Rare element output");
```

每项数量保存在 `MutableCommodityQuantity` 的 `MutableStat` 中，管理员、核心与外部效果还会提供修饰。读取结果用 `getQuantity().getModifiedValue()`，才能取得声明和修饰共同形成的数量。

这些方法按修饰来源写入，不是每次调用都把数量累加。市场添加、经济重算、稳定度变化或显式 `reapply()` 都可能再次应用产业，供需规则因而能跟随当前市场重新建立。

## 计算输入短缺

声明需求后，将它与当前市场的可得量比较。`getMaxDeficit(...)` 在三项输入中取最大正缺口：

```java
Pair<String, Integer> deficit = getMaxDeficit(
        Commodities.METALS, Commodities.FUEL, Commodities.HEAVY_MACHINERY);
```

每项缺口由修改后的整数需求减去市场可得档位，并以 0 为下限。返回的 `Pair` 保存最缺的商品 ID 与缺口数，没有正缺口时数量为 0。需要逐项处理时，`getAllDeficit()` 返回所有正缺口；本例只取最严重的一项。

将这个缺口交给 `applyDeficitToProduction(...)`，削减刚才声明的稀有金属供给：

```java
applyDeficitToProduction(1, deficit, Commodities.RARE_METALS);
```

第一个参数 1 区分缺货修饰与基础供给来源。最大缺口每增加 1，产出减少 1，其他输入的缺口不再叠加扣除。目标先通过 `supply(...)` 声明，这个方法才有供给可以削减；计算完成后，经济更新读取新的供给并传播到市场商品状态。

::: details 生产链的循环依赖
编者建议正式调整这组需求前，同时核对输入商品由哪些产业生产。原版重工业消耗金属与稀有金属，产出重型机械；本例又消耗机械生产稀有金属，已经形成相互依赖。

两边缺货时都按缺口停产，可能使生产链难以自行恢复。原版重工业限制了缺货削减，保留一定产出。本例集中演示最大缺口，实际产业还要结合进口来源与最低产出决定这种循环是否可接受。
:::

## 让停产与安装效果跟随功能状态

完整 `apply()` 在声明和缺货削减后检查 `isFunctional()`，功能状态为 `false` 时清空供给。首次建设与中断都属于停产，升级中的既有产业仍可运行。**功能状态的判断要落实到产出写入，界面上的状态才有经济结果。**

### 为 Alpha core 增加稳定度

本例额外让 Alpha core 提供 1 点稳定度，并限定为产业正常运行时生效。`setAICoreId()` 保存安装 ID，基类再按 Alpha、Beta、Gamma 或无核心调用对应方法。局部实现如下：

```java
@Override
protected void applyAlphaCoreModifiers() {
    if (isFunctional()) {
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
    } else {
        applyNoAICoreModifiers();
    }
}

@Override
protected void applyNoAICoreModifiers() {
    market.getStability().unmodifyFlat(getModId(2));
}
```

写入与清理使用同一个 `getModId(2)`。`reapply()` 先调用 `unapply()`，再调用 `apply()`；基类撤销时会进入 `applyNoAICoreModifiers()`，这里解除稳定度来源。停产时也走这条清理路径，稳定度收益就与功能状态对应。

核心对供需与维护费的通用效果继续由基类处理，子类只添加这项专用效果。改良也有自己的状态，通常通过产业界面和故事点流程设置，以下局部日志读取结果：

```java
LOG.info("improved=" + industry.isImproved());
```

`setImproved(boolean)` 改变状态，`applyImproveModifiers()` 提供专用修饰入口。本例保留基类状态处理，当前没有另外定义改良收益；需要自有收益时，在该回调中按状态成对写入和清理。

### 设置十日中断

`setDisrupted(...)` 将期限写入市场 Memory，`isDisrupted()` 判断是否中断，`getDisruptedDays()` 返回剩余生涯日。下面的局部操作设置 10 日，再重算供给：

```java
industry.setDisrupted(10f, false);
industry.reapply();
LOG.info("functional=" + industry.isFunctional());
```

完整观察器的 `runDisruptionProbe(...)` 用这条路径记录中断前后状态。市场继续推进，基类的 `advance(...)` 检测到中断结束时调用 `disruptionFinished()`，本例在通知与结束回调中记录日志。需要主动结束时可以设为 0 日并重算，这次实验等待自然结束。

::: warning 共用插件类可能共享中断期限
中断键默认使用插件简单类名。同一市场中的多个产业共用一个插件类时，会读写同一个期限；需要独立中断时，覆写 `getDisruptedKey()`，将产业身份纳入键。
:::

中断测试接在当前经济观察器里，将整轮结束后的调用改为以下局部片段：

```java
GearInIndustryProbe.runDisruptionProbe(market);
```

验证完成后把观察入口恢复为 `GearInIndustryProbe.log(market)`。中断自然结束后，下次应用重新声明生产，市场商品数据也随经济更新刷新。

## 建设、收入与市场结果

### 首次建设与升级

`startBuilding()` 清零进度并调用 `unapply()`，本例在后续 `apply()` 中根据功能状态清空供给。市场的 `advance(...)` 换算生涯日并推进建设，中断期间停止建设进度；完成后调用 `finishBuildingOrUpgrading()`。

`isBuilding()` 同时表示首次建设与升级，`isUpgrading()` 区分已有升级目标的情况。`getBuildOrUpgradeProgress()` 返回进度比例，`getBuildTime()` 读取当前产业规格的建设时间；升级流程使用目标规格的时间推进。

升级完成时，市场用目标产业替换当前产业，迁移 AI core 与改良，再重新应用。`downgrade()` 使用规格的降级目标。本例两种目标都为空；需要建立转换关系时，先注册目标产业，再接到 UI 或建设队列。

::: warning 基类许可不证明升级目标存在
`BaseIndustry` 默认的 `canUpgrade()` 与 `canDowngrade()` 返回 `true`。调用转换前还要确认规格的 `upgrade` 或 `downgrade` 指向有效产业；本例没有这两个目标。
:::

### 读取收入、维护与供需

`getIncome()` 与 `getUpkeep()` 返回产业级 `MutableStat`。规格提供基础值，市场规模、收入与维护倍率、核心和功能状态决定最终值，局部读取如下：

```java
int income = (int) industry.getIncome().getModifiedValue();
int upkeep = (int) industry.getUpkeep().getModifiedValue();
```

收入基础值为 0 时，Element Works 仍能贡献商品供给。市场商品的可得量、价格与库存由经济系统汇总其他产业、条件和贸易后计算，因此产业供给与市场最终状态要分别记录。`log(...)` 读取当前数量和状态，`logState(...)` 还列出所有供需。

供需在 `apply()` 中重建。保存时可清理并重建这些统计对象，读档后市场重新应用产业，建设、核心、改良等状态继续提供上下文。

## 核对注册规格与原版规则

`IndustrySpecAPI` 保存身份、显示、建设与基础经济参数。以下局部片段取得本例规格：

```java
IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(
        GearInElementWorksIndustry.INDUSTRY_ID);
if (spec != null) {
    LOG.info(String.format("id=%s name=%s plugin=%s build=%.0f cost=%.0f",
            spec.getId(), spec.getName(), spec.getPluginClass(),
            spec.getBuildTime(), spec.getCost()));
}
```

### 产业规格字段速查表

| 字段与读取入口                                                        | 当前含义                                                             |
| --------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `id`、`name`、`plugin`；`getId()`、`getName()`、`getPluginClass()`    | 稳定 ID、默认名称与插件完整类名；运行名称用产业的 `getCurrentName()` |
| `cost mult`、`build time`；`getCost()`、`getBuildTime()`              | 成本倍率按信用单位与建设倍率换算为金额，建设时间采用生涯日           |
| `income`、`upkeep`；`getIncome()`、`getUpkeep()`                      | 规格基础收入与维护值，运行时继续按市场条件修饰                       |
| `upgrade`、`downgrade`；`getUpgrade()`、`getDowngrade()`              | 已注册的目标产业 ID，本例留空                                        |
| `tags`；`getTags()`、`hasTag()`                                       | `industry`、`industrial`、`structure` 等分类                         |
| `data`；`getData()`                                                   | 规格附加字符串，本例留空                                             |
| `image`、`desc`、`order`；`getImageName()`、`getDesc()`、`getOrder()` | 图像、静态描述与列表顺序                                             |
| `disruptDanger`；`getDisruptDanger()`                                 | 突袭中断的 `RaidDangerLevel`，当前中断期限由产业与市场 Memory 保存   |

### 原版产业速查表

| 产业                   | 输入与产出                                                              | 典型插件                     | 本节关注点                     |
| ---------------------- | ----------------------------------------------------------------------- | ---------------------------- | ------------------------------ |
| `mining`               | `heavy_machinery`、`drugs` 输入，矿石与有机物等产出                     | `Mining`                     | 多商品供给和地表条件           |
| `refining`             | `ore`、`rare_ore`、`heavy_machinery` 输入，`metals`、`rare_metals` 输出 | `Refining`                   | 原版精炼生产链                 |
| `GearIn_element_works` | `metals`、`fuel`、`heavy_machinery` 输入，`rare_metals` 输出            | `GearInElementWorksIndustry` | 规模化需求、最大短缺和状态观察 |

原版精炼分别对金属与稀有金属选择输入缺口，采矿还依赖地表资源条件。本例沿用同一套产业生命周期，只把当前生产对象集中为一项稀有金属供给，便于确认规模、短缺与停产关系。

## 完整最小例

两个类保存到前述路径，注册行、产业添加与经济结束后的观察入口一起保留。

::: details 完整成品：GearInElementWorksIndustry

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
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
        if (!isFunctional()) {
            supply.clear();
        }

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
        if (isFunctional()) {
            market.getStability().modifyFlat(getModId(2), 1,
                    "Alpha core (" + getNameForModifier() + ")");
        } else {
            applyNoAICoreModifiers();
        }
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

}
```

:::

::: details 完整成品：GearInIndustryProbe

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
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

    public static void logState(MarketAPI market) {
        Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
        if (industry == null) {
            LOG.info("Element Works state: not present at " + market.getName());
            return;
        }
        StringBuilder supply = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllSupply()) {
            if (supply.length() > 0) {
                supply.append(", ");
            }
            supply.append(quantity.getCommodityId()).append("=")
                    .append((int) quantity.getQuantity().getModifiedValue());
        }
        StringBuilder demand = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllDemand()) {
            if (demand.length() > 0) {
                demand.append(", ");
            }
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
        Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
        if (industry == null) {
            return;
        }
        LOG.info(String.format("Element Works disruption before: disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
        industry.setDisrupted(10f, false);
        LOG.info(String.format("Element Works disruption during: disrupted=%s days=%.1f functional=%s",
                industry.isDisrupted(), industry.getDisruptedDays(), industry.isFunctional()));
        industry.reapply();
    }
}
```

:::

## 验证方案

前置数据：启用包含本节产业注册行和两个 Java 类的 GearIn MOD，并使用拥有 `gear_industry` 市场的测试存档。

1. 保存 CSV、两类与经济观察器中的添加调用，构建重启，进入 Foundry 市场存档，查看新产业。
2. 打开产业详情，核对显示名、图标、描述和插件类 `GearInElementWorksIndustry`。
3. 先在 Foundry 的规模 3 记录需求 3 金属、1 燃料、1 机械与理论产出 2，再在规模 6 的测试市场运行 `GearInIndustryProbe.log(market)`，读取三项需求、`rare_metals` 供给、收入、维护费和功能状态。
4. 降低市场中 `metals`、`fuel` 或 `heavy_machinery` 的可得量，调用产业 `reapply()`，再次记录 `rare_metals` 供给和最大缺口。
5. 在测试市场取得产业，调用 `startBuilding()` 与 `reapply()`，再推进市场时间，读取 `isBuilding()`、`getBuildOrUpgradeProgress()` 和 `isFunctional()`；本例建设时间为 90 生涯日，完成后确认供给恢复。
6. 安装 Alpha core 并重新应用产业，读取市场稳定度；移除 Alpha core 后再次应用产业，读取同一修饰是否撤销。
7. 调用 `GearInIndustryProbe.runDisruptionProbe(market)`，记录十日中断与停产，再推进至少十个生涯日，等待结束回调并重新读取供给。

预期结果：

- 产业列表出现 `Element Works`，插件类为 `GearInElementWorksIndustry`；
- 规模 6 时需求为 6 `metals`、3 `fuel`、3 `heavy_machinery`，理论产出为 5 `rare_metals`；
- 输入商品出现缺口后，`getMaxDeficit()` 返回正缺口，`rare_metals` 修改后供给低于理论产出；
- 市场规模改变后，三项需求和理论产出按同一规模规则变化；
- 建设期间 `isBuilding()` 为 `true`，`isFunctional()` 为 `false`，进度随市场时间推进；
- Alpha core 安装后稳定度出现固定修饰，移除或重算后该修饰被撤销；
- 中断期间 `isDisrupted()` 为 `true`、`isFunctional()` 为 `false`，供给清空；十日结束后出现 `disruptionFinished()`，下一次应用恢复生产。

## 故障分析

- **产业没有出现在市场列表**：注册行或市场添加入口缺失。
    - **解决方案**：检查 CSV 行是否位于 `data/campaign/industries.csv`，并确认市场没有同 ID 的旧实例。

- **产业 ID 找不到**：数据 ID 与调用常量不一致。
    - **解决方案**：逐字核对 `GearIn_element_works` 的大小写、下划线和插件常量。

- **插件无法实例化**：完整类名与 JAR 条目不一致。
    - **解决方案**：检查完整类名、包路径、无参构造和 JAR 内部路径。

- **需求或产出为零**：产业尚未正常运行或经济重算未完成。
    - **解决方案**：确认供需声明位于 `apply()`，并确认市场已经完成一次经济重算。

- **短缺没有减少产出**：缺口或削减目标没有接到声明的供给。
    - **解决方案**：确认 `getMaxDeficit()` 的输入列表只包含已声明需求，并把缺口传给 `applyDeficitToProduction()`。

- **缺货削减方向错误**：把输入商品作为产出削减目标。
    - **解决方案**：检查削减目标是否为 `Commodities.RARE_METALS`，并核对输入商品与产出目标的方向。

- **AI 核心修饰残留**：撤销来源与写入来源不一致。
    - **解决方案**：检查 `applyAlphaCoreModifiers()` 与 `applyNoAICoreModifiers()` 是否使用相同的修饰 ID。

- **中断状态没有恢复**：市场时间未完成十日推进。
    - **解决方案**：确认市场时间继续推进，确认本次中断日志只触发一次，再等待十个生涯日。

- **改良状态与界面不同步**：状态修改后尚未重新应用效果。
    - **解决方案**：先执行产业重算，再读取 `isImproved()`。

- **建设进度异常**：建设时间或队列目标与当前产业状态不一致。
    - **解决方案**：检查 `build time`、建设队列和市场经济步，升级目标为空时不要调用升级入口。

## 总结

Element Works 按当前市场规模声明三项需求与稀有金属供给，再按最大输入短缺削减产出，停产时清空供给。建设、核心和中断都沿产业生命周期推进，专用稳定度效果也与正常运行状态对应。

产业声明进入经济更新后，还与其他生产、市场条件和贸易共同形成结果。理论产出、当前产业供给与市场可得量分别读取，生产链中的输入来源和循环依赖也就能随实际数量一起判断。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.Industry`：产业运行时接口；
- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`：产业生命周期与供需基类；
- `com.fs.starfarer.api.loading.IndustrySpecAPI`：`industries.csv` 静态规格读取入口；
- `com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity`：供需数量与修饰值；
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场产业、商品和经济步入口。
