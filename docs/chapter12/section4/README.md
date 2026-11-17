# 制作专用产业

殖民地产业远不只是往市场清单里硬塞几行产出数字。一套像样的 `BaseIndustry` 插件得消耗上游原料、在断供或封锁时按比例减产、在未建成或被破坏时清空出货，还要管好 AI 核心塞进来时给什么加成。

照着这条生命周期写一座名为 `Element Works` 的精炼车间：它消耗金属与重型机械生产回火合金，缺料时按最大缺口一比一减产；哪怕塞进 Alpha 核心，也只按常规规则降低维护与需求、小幅增产并额外提高殖民地稳定度，而不直接给产能翻倍。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头与双引号转义；
- [C12-S1] 的 `GearInFoundryGenerator`，新产业将加入那里的 Foundry 市场；
- [C12-S2] 的回火合金 (`GearInAlloyCargo.COMMODITY_ID`) 与经济档位概念。

## 在 industries.csv 注册产业

产业的静态面板数据登记在 `data/campaign/industries.csv`，表头沿用原版的 15 列：

```csv
id,name,cost mult,build time,income,upkeep,downgrade,upgrade,tags,data,image,plugin,desc,order,disruptDanger
GearIn_element_works,Element Works,45,90,,3,,,"industry, industrial",,graphics/icons/industry/refining.png,data.scripts.gearin.campaign.GearInElementWorksIndustry,"Consumes metals and heavy machinery to produce tempered alloy.",510,MEDIUM
```

- 建造成本系数 `45`、工期 `90` 天、维护系数 `3` 以及图标直接参照原版的冶炼厂 (`refining`)；`order` 设为 `510`，排在原版冶炼厂 (`500`) 之后；
- **`tags` 中的 `"industry"` 极其重要**：Starsector 统计一颗殖民地是否达到上限的 "产业 (Industry)" 数量 (而非不占槽位的建筑 Structure)，看的正是这条标签；如果去掉 `"industry"`，这座设施就会变成不占产业名额的普通建筑；
- `income` 留空，表示该产业没有固定的底座现金收益，市场进账全部由产出物资的出口供需结算；
- `plugin` 指向实现 `Industry` 接口的行为类，必须保留无参构造方法。如果类名拼错，启动游戏不会报异常，但市场添加产业的瞬间会抛出找不到类错误。

## BaseIndustry 的生命周期与缺口削减

自定义的产业插件一般直接继承 `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`。市场每次触发经济刷新或调用 `reapplyIndustries()` 时，引擎都会重新执行一遍产业插件的 `apply()`。

`apply()` 开头必须先调用 `super.apply(true)`：它会把基础收入与维护费写入市场经济统计、读取并应用玩家在产业上使用的 "改进 (Improve)" 加成以及 Alpha/Beta/Gamma 核心的对应修饰钩子。随后再声明本产业的输入需求与输出供给：

```java
int size = market.getSize();
int metals = Math.max(1, size);
int machinery = Math.max(1, size / 2);
int output = Math.max(1, size - 1);

demand(Commodities.METALS, metals, "Metal feedstock");
demand(Commodities.HEAVY_MACHINERY, machinery, "Foundry equipment");
supply(GearInAlloyCargo.COMMODITY_ID, output, "Tempered alloy output");
```

以规模为 `3` 的 Nipher Foundry 为例：需求 `3` 档金属与 `1` 档重型机械，基础产出 `2` 档回火合金。这里的 `demand(...)` 与 `supply(...)` 写进的是 [C12-S2] 提到的**经济档位**，按来源键写入 `MutableCommodityQuantity` 中，重复执行 `apply()` 会自动覆盖更新，不会把数量越叠越大。

声明完供需之后，立即调用 `getMaxDeficit(...)` 与 `applyDeficitToProduction(...)` 处理输入短缺惩罚：

```java
Pair<String, Integer> deficit = getMaxDeficit(Commodities.METALS, Commodities.HEAVY_MACHINERY);
applyDeficitToProduction(1, deficit, GearInAlloyCargo.COMMODITY_ID);
```

- `getMaxDeficit(...)` 会逐个检查指定输入物在当前市场的可得量，找出缺口最大的一项，返回 `(商品 ID, 缺口档数)`；
- `applyDeficitToProduction(...)` 按这个最大缺口一对一削减 `GearIn_tempered_alloy` 的产出 (如果金属缺 2 档、重型机械缺 1 档，产出只扣减最严重的 `2` 档，而不叠加扣成 3 档)。**注意 `supply(...)` 必须先于 `applyDeficitToProduction(...)` 调用**，先有基础供给条目，削减修饰才有对象可减。

这里的原材料选用金属 (`Commodities.METALS`) 与重型机械 (`Commodities.HEAVY_MACHINERY`)，两者的上游分别是矿石冶炼与重工业，都不依赖回火合金，从而避免上游反过来依赖下游产品、在经济网络里形成闭合死锁。

## 停产清空与 AI 核心修饰

`apply()` 末尾还要加上一道很容易遗漏的收尾检查：

```java
if (!isFunctional()) {
    supply.clear();
}
```

当产业尚在建造队列中、或者刚遭特工破坏陷入瘫痪时，`isFunctional()` 返回 `false`。`super.apply(true)` 只管算维护和界面状态，**如果不手工执行 `supply.clear()`，还在施工或已中断的产业依然会往市场经济表里源源不断地写入虚构供给**。

`super.apply(true)` 在 `BaseIndustry` 中会先执行标准的 Alpha 核心供需加成 (`applyAlphaCoreSupplyAndDemandModifiers()` 提供 `+1` 产出、`-1` 需求，若不想给 `+1` 产出需像原版 `Spaceport` 那样另行覆写该方法) 与 `-25%` 维护费折减 (`applyAICoreToIncomeAndUpkeep()`)，随后才派发到 `applyAlphaCoreModifiers()`，拆下核心或产业瘫痪时则交由 `applyNoAICoreModifiers()` 撤回自定义修饰：

```java
@Override
protected void applyAlphaCoreModifiers() {
    if (isFunctional()) {
        market.getStability().modifyFlat(getModId(2), 1, "Alpha core (" + getNameForModifier() + ")");
    } else {
        applyNoAICoreModifiers();
    }
}

@Override
protected void applyNoAICoreModifiers() {
    market.getStability().unmodifyFlat(getModId(2));
}
```

`getModId(2)` 是 `BaseIndustry` 为第 2 类修饰预留的来源键生成方法，两处传入相同的来源键，才能保证拔下 Alpha 核心时稳定度加成被干净撤掉。

## 将产业接入 Foundry

在 [C12-S1] 的 `GearInFoundryGenerator.generate(...)` 中，于太空港 `"spaceport"` 之后追加产业挂载：

```java
market.addIndustry("population");
market.addIndustry("spaceport");
market.addIndustry("GearIn_element_works");
```

`addIndustry(...)` 会按 `industries.csv` 中的配置实例化 `GearInElementWorksIndustry` 并立即执行一次 `apply()`，紧随其后的 [C12-S3] Alloy Depot 仓库也就终于能从市场供给里读到回火合金了。

## 完整的产业类

`src/data/scripts/gearin/campaign/GearInElementWorksIndustry.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/** Element Works：消耗金属与重型机械，生产回火合金；输入短缺时按最大缺口削减产出。 */
public class GearInElementWorksIndustry extends BaseIndustry {

    public static final String INDUSTRY_ID = "GearIn_element_works";

    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();
        int metals = Math.max(1, size);
        int machinery = Math.max(1, size / 2);
        int output = Math.max(1, size - 1);

        demand(Commodities.METALS, metals, "Metal feedstock");
        demand(Commodities.HEAVY_MACHINERY, machinery, "Foundry equipment");
        supply(GearInAlloyCargo.COMMODITY_ID, output, "Tempered alloy output");

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.METALS, Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit, GearInAlloyCargo.COMMODITY_ID);
        if (!isFunctional()) {
            supply.clear();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        if (isFunctional()) {
            market.getStability().modifyFlat(getModId(2), 1, "Alpha core (" + getNameForModifier() + ")");
        } else {
            applyNoAICoreModifiers();
        }
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry 并打开殖民地管理界面。

- 产业列表中出现 `Element Works`，消耗金属 (`3`) 与重型机械 (`1`)，产出回火合金 (`2`)；
- 推进数个生涯天后打开 [C12-S3] 的 `Alloy Depot`，货架上已补出可供购买的回火合金库存；
- 当金属或重型机械输入短缺时，产出栏会如实减去对应的缺口档数并标出红字缺货来源；
- 给该产业装入 Alpha 核心，维护费用下降、供需按常规核芯规则微调，且殖民地稳定度明细中出现 `+1` 来源，拆下后全部恢复。

## 常见问题

- **产业列表中没有出现 `Element Works`**：`GearInFoundryGenerator` 尚未调用 `market.addIndustry("GearIn_element_works")`，或 `industries.csv` 的 15 列格式错位、`plugin` 类名拼写有误。
    - **解决方案**：核对 `industries.csv` 列数与完整类名，确认类已打进 JAR 并已加入市场。

- **输入物已经短缺，但回火合金产出档位纹丝不动**：将 `applyDeficitToProduction(...)` 写在了 `supply(...)` 之前。
    - **解决方案**：确保先调用 `supply(...)` 声明基础产出，再调用 `applyDeficitToProduction(...)`。

- **产业正在建造中或被打瘫时市场依然能买到产出物资**：`apply()` 结尾漏掉了 `if (!isFunctional()) { supply.clear(); }`。
    - **解决方案**：在确认产业未就绪时清空 `supply` 映射。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`：`apply(boolean)`、`demand(...)`、`supply(...)`、`getMaxDeficit(...)` 与 `getModId(int)`。
- `com.fs.starfarer.api.impl.campaign.econ.impl.Refining`：原版冶炼厂供需、短缺与核心效果写法 (`HeavyIndustry` 同包)。
- `com.fs.starfarer.api.impl.campaign.ids.Commodities`：原版大宗商品 ID 常量 (`METALS`、`HEAVY_MACHINERY` 等)。
- `data/campaign/industries.csv`：原版产业登记表头与标签写法。
