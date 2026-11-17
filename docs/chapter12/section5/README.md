# 注册特殊物品

殖民地特殊物品的定义与效果是拆开存放的：`special_items.csv` 只登记图标、底价与适用范围，真正改写产出和悬停提示的是全局静态表里的 `BaseInstallableItemEffect` 单例 —— 正因它跨存档常驻，效果类里绝不能留任何市场或产业的实例引用。

守住这条生命周期边界后，做一颗克制版的精炼核心挂进车间槽位，让那点增产幅度既值得玩家专门跑一趟，又不至于让垄断货泛滥贬成廉价品。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头与双引号转义；
- [C7-S1] 的 `onApplicationLoad()`，物品效果需要在应用启动阶段注册；
- [C12-S4] 的 `Element Works` 产业，精炼核心将安装到这座产业的槽位中。

## 在 special_items.csv 登记物品

特殊物品统一登记在 `data/campaign/special_items.csv`，表头沿用原版的 16 列：

```csv
name,id,tags,tech/manufacturer,rarity,base price,stack size,cargo space,baseRaidDanger,icon,sound id,sound id drop,plugin,plugin params,desc,order
Refinery Core,GearIn_refinery_core,"gear_industry, colony_item",GearIn Industries,,75000,1000,1,MEDIUM,graphics/icons/cargo/catalytic_core.png,ui_cargo_special_industrial_pickup,ui_cargo_special_industrial_drop,com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin,GearIn_element_works,"A foundry core that raises tempered alloy output when installed in Element Works.",1450
```

- **`id` 与 `tags`**：ID 为 `GearIn_refinery_core`；`colony_item` 标签告诉 UI 和产业菜单这是一个可装入殖民地产业槽位的物品；
- **`plugin` 与 `plugin params`**：直接复用原版的 `GenericSpecialItemPlugin`，它会自动读取 `plugin params` 这一列里的产业 ID 列表 (多个 ID 可用逗号分隔)。本例填入 `GearIn_element_works`，打开 Element Works 的产业界面时就会出现这件物品的安装选项；如果写错或留空，物品放进货舱也无法安装进目标产业；
- **视觉与音效**：图标借用原版催化核心 (`catalytic_core.png`)，拾取与放下音效沿用工业特殊物品音效。和普通商品不同，特殊物品的长描述直接写在末尾的 `desc` 列即可。

## 静态效果总表与无状态约束

CSV 解决的是 "玩家怎么看到和选中这件物品"，装进去之后的数值增益以及悬停在产业槽上显示的高亮提示，则由 `InstallableItemEffect` 接口负责。原版将所有殖民地槽位物品的效果集中保存在 `ItemEffectsRepo.ITEM_EFFECTS` 这个 `static Map<String, InstallableItemEffect>` 中，键就是物品 ID。

每当市场里的某座产业触发 `reapply()` 或打开安装面板时，`BaseIndustry` 会按槽位里已装的物品 ID 去 `ItemEffectsRepo.ITEM_EFFECTS` 查表，命中则调 `apply(industry)`，取下或卸载时调 `unapply(industry)`。

**因为 `ITEM_EFFECTS` 是进程级的静态表，里面的效果实例在切换存档、返回主菜单再开新档期间全程只创建一次。** 任何 `MarketAPI`、`Industry` 或生涯记忆状态都只能在方法参数里临时取用，绝不能存进效果类的成员变量里。

## 实现产出加成与安装面板提示

继承 `com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect`，构造器将物品 ID 传给父类以自动加载 `SpecialItemSpecAPI spec`：

```java
@Override
public void apply(Industry industry) {
    industry.getSupplyBonus().modifyFlat(spec.getId(), SUPPLY_BONUS, spec.getName());
}

@Override
public void unapply(Industry industry) {
    industry.getSupplyBonus().unmodifyFlat(spec.getId());
}
```

`industry.getSupplyBonus()` 控制的是该产业全部输出物资的供给档位加成；`apply` 与 `unapply` 两处必须使用完全相同的来源 ID (`spec.getId()`)，才能在拆下物品时把加成撤干净。

此外，在货舱或产业槽菜单中悬停物品时，原版会在 CSV 描述下方额外打印一行带高亮数值的具体效果说明。**如果不覆写 `addItemDescriptionImpl(...)`，菜单里就只会孤零零显示 CSV 那句纯文本描述，玩家看不出具体能加多少产出：**

```java
@Override
protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
        InstallableItemDescriptionMode mode, String pre, float pad) {
    text.addPara(pre + "Increases Element Works output by %s unit.",
            pad, Misc.getHighlightColor(), "" + SUPPLY_BONUS);
}
```

参数 `pre` 由父类根据展示语境 (`CARGO_TOOLTIP` 货舱悬停、`INDUSTRY_MENU_TOOLTIP` 安装菜单或 `INDUSTRY_TOOLTIP` 已安装提示) 自动拼好了物品名称前缀，直接拼在效果说明前面即可。如果希望像原版催化核心一样限制行星大气或宜居度条件，还可以覆写 `getSimpleReqs(Industry)` 返回前提标签数组；本例的 Element Works 位于空间站上，不设行星环境限制。

## 完整的效果类

`src/data/scripts/gearin/campaign/GearInRefineryCoreEffect.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

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

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
            InstallableItemDescriptionMode mode, String pre, float pad) {
        text.addPara(pre + "Increases Element Works output by %s unit.",
                pad, Misc.getHighlightColor(), "" + SUPPLY_BONUS);
    }
}
```

### 在入口类登记效果

既然 `ItemEffectsRepo.ITEM_EFFECTS` 属于全局静态表且依赖 `SpecialItemSpecAPI`，最合适的注册位置就是 [C7-S1] 的 `GearInModPlugin.onApplicationLoad()` —— 此时游戏已经读完了 `special_items.csv`，构造 `new GearInRefineryCoreEffect()` 时刚好能取到物品规格：

```java
ItemEffectsRepo.ITEM_EFFECTS.put(GearInRefineryCoreEffect.ITEM_ID, new GearInRefineryCoreEffect());
```

记得在 `GearInModPlugin` 中导入 `com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo` 与 `data.scripts.gearin.campaign.GearInRefineryCoreEffect`。原版通用产业物品插件 `GenericInstallableItemPlugin.isInstallableItem(...)` 会先查询 `ItemEffectsRepo.ITEM_EFFECTS.containsKey(...)`；如果不写这一行，物品能正常进货舱，**但在 Element Works 的安装菜单里甚至选不到它**。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 在控制台执行 `additem GearIn_refinery_core 1`，货舱出现精炼核心，使用催化核心图标，悬停显示简介与 `1` 单位增产高亮说明；
- 飞往 Nipher Foundry 并打开 Element Works 管理面板，在特殊物品槽中可见 `Refinery Core` 安装选项；
- 装入精炼核心后，回火合金产出由 `2` 档升至 `3` 档，取下核心后立即恢复 `2` 档；
- 保存并重新读档，核心的安装状态与 `3` 档产出保持不变。

## 常见问题

- **控制台提示找不到 `GearIn_refinery_core`**：`special_items.csv` 的 16 列格式错位或文件路径不在 `data/campaign/` 下。
    - **解决方案**：核对表头列数与含逗号的 `"gear_industry, colony_item"` 引号转义。

- **物品在货舱里，但在 Element Works 安装菜单里是空的**：`special_items.csv` 的 `plugin params` 漏填 `GearIn_element_works`、`tags` 漏了 `colony_item`，或者 `GearInModPlugin.onApplicationLoad()` 尚未把该 ID 放进 `ItemEffectsRepo.ITEM_EFFECTS`。
    - **解决方案**：核对 `tags` 包含 `colony_item`、`plugin params` 填入目标产业 ID `GearIn_element_works`，并确认已执行 `ItemEffectsRepo.ITEM_EFFECTS.put(...)`。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo`：原版全套产业特殊物品在静态表中的注册与提示实现 (`CATALYTIC_CORE` 等)。
- `com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect`：`apply(...)`、`unapply(...)` 与 `addItemDescriptionImpl(...)` 基类实现。
- `com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect`：纯产供/降耗物品的抽象基类参考。
- `com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin`：通用特殊物品插件对 `plugin params` 的读取逻辑。
- `data/campaign/special_items.csv`：原版殖民地特殊物品的 16 列登记。
