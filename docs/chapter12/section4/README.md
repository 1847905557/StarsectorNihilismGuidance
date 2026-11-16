# 制作专用产业

Element Works 是 Foundry 真正产出回火合金的地方，它的设计偏保守：缺了原料就老老实实减产，装上核心，换来的也是一座更安稳的站点，而不是更多的合金。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C12-S1] 的 Foundry 市场与 `addIndustry(...)`，产业在那里加入；
- [C12-S2] 的回火合金，本节的产业以它为产出；
- [C1-S7] 的 CSV 表头与转义。

## 注册产业

产业的身份登记在 `data/campaign/industries.csv`，表头沿用原版的十五列：

```csv
id,name,cost mult,build time,income,upkeep,downgrade,upgrade,tags,data,image,plugin,desc,order,disruptDanger
GearIn_element_works,Element Works,45,90,,3,,,"industry, industrial",,graphics/icons/industry/refining.png,data.scripts.gearin.campaign.GearInElementWorksIndustry,"Consumes metals and heavy machinery to produce tempered alloy.",510,MEDIUM
```

成本倍率、建设时间与维护倍率沿用原版 `refining`，图像也借用精炼产业的图标。`income` 留空，产业没有独立的收入基础值，贡献都走供需。`order` 取 510，排在原版精炼的 500 之后。

`industry` 标签让它计入产业数量，`plugin` 是行为类的完整类名，必须有无参构造。**类名写错时，游戏不会在启动时报错**，症状出现在市场添加产业的那一刻。

## 声明生产

插件继承 `BaseIndustry`，由基类保存供需、收入、维护与核心状态。`apply()` 先调用 `super.apply(true)`，再声明自己的供需：

```java
int size = market.getSize();
int metals = Math.max(1, size);
int machinery = Math.max(1, size / 2);
int output = Math.max(1, size - 1);

demand(Commodities.METALS, metals, "Metal feedstock");
demand(Commodities.HEAVY_MACHINERY, machinery, "Foundry equipment");
supply(GearInAlloyCargo.COMMODITY_ID, output, "Tempered alloy output");
```

Foundry 规模为 3：需求 3 档金属、1 档重型机械，产出 2 档回火合金。这些数字是经济档位，不是货舱里的件数，换算见 [C12-S2]。

`demand(...)` 与 `supply(...)` 按修饰来源写入，重复调用不会累加。市场添加、经济重算或 `reapply()` 都会再次执行 `apply()`，所以每次都完整声明，不要依赖上一次的结果。

## 计算输入短缺

声明需求之后，把它与市场当前的可得量比较。`getMaxDeficit(...)` 在给定的输入里取最大的正缺口，返回最缺的商品 ID 与缺口档数：

```java
Pair<String, Integer> deficit = getMaxDeficit(Commodities.METALS, Commodities.HEAVY_MACHINERY);
applyDeficitToProduction(1, deficit, GearInAlloyCargo.COMMODITY_ID);
```

最大缺口每增加 1 档，回火合金的产出减少 1 档；只取最严重的一项，其他输入的缺口不叠加。第一个参数 1 用来区分缺货修饰与基础供给来源。**目标必须先用 `supply(...)` 声明**，否则没有可削减的对象，缺口算出来也不起作用。

本节的输入只有金属与重型机械，两者都不由回火合金生产，所以不会形成原料与产品互相依赖的循环。原版重工业消耗金属，产出重型机械，Foundry 恰好站在这条链的下游。

## 功能状态与核心

声明与削减之后，`apply()` 检查功能状态：

```java
if (!isFunctional()) {
    supply.clear();
}
```

首次建设与中断期间 `isFunctional()` 为 `false`，产出清空。**界面上显示停产，经济里也必须真的停产**，这一步把两者接在一起；漏掉它，建设中的产业照样出货。

Alpha 核心的效果在 `applyAlphaCoreModifiers()` 里写入：功能正常时市场稳定度 +1，否则按无核心处理，撤销修饰。一座靠垄断物资立足的站点，稳定度比产量更值钱。

## 加入 Foundry

[C12-S1] 的生成器在加入停靠设施之后添加这一行：

```java
market.addIndustry("GearIn_element_works");
```

`addIndustry(...)` 按 ID 找到注册规格，创建插件实例并执行 `apply()`。

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

执行 `gradle jar`，重启游戏，**新建一份存档**，打开 Nipher Foundry 的产业界面。

- 产业列表里有 Element Works，供需一栏为金属、重型机械需求与回火合金产出；
- 金属或重型机械短缺时，回火合金的产出相应下降；
- 安装 Alpha 核心后，稳定度提高 1。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **产业列表里没有 Element Works**：`industries.csv` 没有加载，或 `plugin` 的类名写错。
    - **解决方案**：核对 ID 与完整类名，并确认生成器已调用 `addIndustry(...)`。

- **产出始终为 0**：`supply(...)` 的商品 ID 拼错，或产业尚未建成。
    - **解决方案**：核对 `GearInAlloyCargo.COMMODITY_ID`，并检查 `isFunctional()`。

- **短缺没有削减产出**：先调用了 `applyDeficitToProduction(...)`，后声明 `supply(...)`。
    - **解决方案**：先声明供给，再应用缺口。

- **建设中的产业仍在出货**：没有在不可用时清空供给。
    - **解决方案**：在 `apply()` 末尾检查 `isFunctional()`。

- **旧存档中没有这个产业**：Foundry 只在新档中生成。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`：供需、缺口与核对状态的全部方法。
- `com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry`：原版重工业，缺货削减与最低产出的写法。
- `com.fs.starfarer.api.impl.campaign.ids.Commodities`：原版商品 ID 常量。
- `data/campaign/industries.csv`：原版产业的登记。
