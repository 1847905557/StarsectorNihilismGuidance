# 注册特殊物品

一件特殊物品该有多强？刚好强到值得专程去找，再强一点就过头了。

精炼核心对 Element Works 的提升因此很克制。回火合金一旦不再稀缺，前面几节为它定下的垄断也就白定了。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C12-S4] 的 Element Works，物品装入这座产业；
- [C12-S2] 的 `GearInAlloyCargo`，用来把物品放进货舱做验证；
- [C7-S1] 的 `onApplicationLoad()`，效果在那里登记。

## 注册物品

特殊物品登记在 `data/campaign/special_items.csv`，表头沿用原版的十六列：

```csv
name,id,tags,tech/manufacturer,rarity,base price,stack size,cargo space,baseRaidDanger,icon,sound id,sound id drop,plugin,plugin params,desc,order
Refinery Core,GearIn_refinery_core,"gear_industry, colony_item",GearIn Industries,,75000,1000,1,MEDIUM,graphics/icons/cargo/catalytic_core.png,ui_cargo_special_industrial_pickup,ui_cargo_special_industrial_drop,com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin,GearIn_element_works,"A foundry core that raises tempered alloy output when installed in Element Works.",1450
```

`id` 是 `GearIn_refinery_core`。图标借用原版催化核心，声音借用原版的产业物品拾取与放下音。`colony_item` 标签把它归入殖民地物品。

`plugin` 用原版的 `GenericSpecialItemPlugin`，它负责货舱里的显示与产业安装菜单。**`plugin params` 写能装入的产业 ID**，这里是 `GearIn_element_works`；原版据此判断一座产业是否愿意接受这件物品，多个产业用逗号分隔。写错了，物品在这座产业的菜单里不会出现。

长描述直接写在 `desc` 列，货舱悬停时显示。

## 效果登记在静态表里

CSV 只负责让物品存在，装入之后的效果由 `InstallableItemEffect` 实现。原版把所有产业槽物品的效果放在静态表 `ItemEffectsRepo.ITEM_EFFECTS` 里，键是物品 ID。`BaseIndustry` 每次重算都会按已装物品的 ID 从这张表取出效果：已安装就调用 `apply(industry)`，否则调用 `unapply(industry)`。

所以精炼核心不必由任何脚本显式调用，只要登记进这张表，就随产业的每次重算生效。

**效果对象存放在静态表里，跨存档读取一直存在，不能在字段里保存任何生涯对象的引用。** 产业、市场都只在方法参数里使用，用完即弃。把某座产业存进字段，换一份存档之后，效果还在操作上一份存档里的对象。

## 写效果

效果继承 `BaseInstallableItemEffect`，构造时传入物品 ID，基类据此取得规格 `spec`。核心的作用是让产出提高 1 档：

```java
industry.getSupplyBonus().modifyFlat(spec.getId(), SUPPLY_BONUS, spec.getName());
```

`getSupplyBonus()` 是产业整体的供给加成，按来源 ID 写入。来源用物品 ID，`unapply(...)` 按同一个 ID 解除：

```java
industry.getSupplyBonus().unmodifyFlat(spec.getId());
```

加成只有 1 档：Foundry 规模 3，基础产出 2 档，一枚核心把它抬到 3 档，提升一半，已经足以让玩家为它专程跑一趟。再高，回火合金就不再稀缺。

## 完整的效果类

`src/data/scripts/gearin/campaign/GearInRefineryCoreEffect.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;

/** 精炼核心装入 Element Works 后，产出提高 1 档。效果登记在静态表中，不保存任何生涯对象的引用。 */
public class GearInRefineryCoreEffect extends BaseInstallableItemEffect {

    public static final String ITEM_ID = "GearIn_refinery_core";

    private static final int SUPPLY_BONUS = 1;

    public GearInRefineryCoreEffect() {
        super(ITEM_ID);
    }

    @Override
    public void apply(Industry industry) {
        industry.getSupplyBonus().modifyFlat(spec.getId(), SUPPLY_BONUS, spec.getName());
    }

    @Override
    public void unapply(Industry industry) {
        industry.getSupplyBonus().unmodifyFlat(spec.getId());
    }
}
```

## 在入口类登记

静态表在应用加载时就要填好。在 `GearInModPlugin.onApplicationLoad()` 中加入：

```java
ItemEffectsRepo.ITEM_EFFECTS.put(GearInRefineryCoreEffect.ITEM_ID, new GearInRefineryCoreEffect());
```

这一步不依赖存档，所以放在 `onApplicationLoad()`，不放在 `onGameLoad(...)`。**漏掉这一行，物品能进货舱，也能在菜单里选中，装进产业后效果取到 `null`，什么也不发生。** 这是本节最常见的症状。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 控制台执行 `additem GearIn_refinery_core 1`，货舱中出现精炼核心，图标为催化核心，悬停显示描述；
- 打开 Nipher Foundry 的 Element Works，产业菜单中可以选择安装精炼核心；
- 安装后回火合金的产出提高 1 档，取出后恢复；
- 保存并重新读档，安装状态与产出不变。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **物品在货舱里，产业菜单里却选不到**：`plugin params` 没有写产业 ID，或写错。
    - **解决方案**：填 `GearIn_element_works`，多个产业用逗号分隔。

- **装进去之后产出没有变化**：没有在 `onApplicationLoad()` 登记效果。
    - **解决方案**：往 `ItemEffectsRepo.ITEM_EFFECTS` 里 `put` 效果，键与物品 ID 一致。

- **取出之后加成仍在**：`unapply(...)` 解除的来源 ID 与 `apply(...)` 写入的不一致。
    - **解决方案**：两处都用 `spec.getId()`。

- **控制台提示找不到物品**：`special_items.csv` 没有被读取，或列数与表头不一致。
    - **解决方案**：核对 16 列，含逗号的字段加引号。

- **旧存档中没有这个产业**：Foundry 只在新档中生成。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo`：原版全部产业槽物品效果的登记表。
- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect`：效果基类与说明文字的扩展点。
- `com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect`：原版按档位提高供给的写法。
- `com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin`：通用物品插件，读取 `plugin params`。
- `data/campaign/special_items.csv`：原版物品的登记。
