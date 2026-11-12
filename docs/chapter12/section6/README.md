# 注册特殊物品

特殊物品可以保存附加数据，并通过插件提供显示与交互。本节制作两件物品：Salvage Token 在玩家货舱右键消耗，换取 10 补给；Refinery Core 在产业实验中提高 3 档稀有金属供给，再解除效果。两种用途分别说明货舱动作与产业数据怎样调用插件。

_本节预期阅读时间：\~22 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 在 `special_items.csv` 注册可加载的特殊物品；
- 使用 `SpecialItemSpecAPI` 读取静态规格；
- 使用 `SpecialItemData` 表达物品 ID 与附加数据；
- 从 Cargo 栈取得 `SpecialItemPlugin` 实例；
- 为产业安装物品和右键物品编写最小插件；
- 处理插件的 tooltip、价格、掉落参数和交互入口。

## 前置知识

- [C12-S2] 的 `CargoAPI`、`CargoStackAPI` 和特殊物品栈；
- [C12-S5] 的产业对象、供需数量和修饰器；
- [C7-S1] 的插件实例化与生命周期；
- 能够编辑 UTF-8 无 BOM 的 CSV 和 Java 文件。

## 注册特殊物品身份

在 `data/campaign/special_items.csv` 添加以下两条记录。核心 ID 使用 `GearIn_refinery_core`，代币使用 `GearIn_salvage_token`，分别登记各自的插件类：

```csv
name,id,tags,tech/manufacturer,rarity,base price,stack size,cargo space,baseRaidDanger,icon,sound id,sound id drop,plugin,plugin params,desc,order
Refinery Core,GearIn_refinery_core,"gear_industry, colony_item",GearIn Industries,0.5,75000,1000,1,MEDIUM,graphics/icons/cargo/nanoforge_pristine.png,ui_cargo_special_industrial_pickup,ui_cargo_special_industrial_drop,data.scripts.gearin.campaign.GearInRefineryCorePlugin,GearIn_element_works,"A GearIn industrial core that increases rare metal output when installed in Element Works.",1450
Salvage Token,GearIn_salvage_token,gear_industry,GearIn Industries,1,5000,1000,1,LOW,graphics/icons/cargo/modspec2.png,ui_chip_pickup,ui_modspec_drop,data.scripts.gearin.campaign.GearInSalvageTokenPlugin,,"A token that can be exchanged for a small supply cache.",1451
```

图标与声音采用原版资源，价格、稀有度和排序分别填写。`name` 用于显示，`id` 供代码与存档引用；名称可以调整，使用中的 ID 保持稳定。`tech/manufacturer` 提供设计来源文本，插件选择由完整类名决定。

**特殊物品的长描述直接写在 CSV 的 `desc`。** 基础插件从规格读取它显示在 tooltip 中；本页采用这条描述关系。`plugin params` 则是整类物品的附加字符串，核心记录中保存 `GearIn_element_works`，插件可以通过 `getSpec().getParams()` 读取。

## 保存实例数据

注册行描述整类物品，`SpecialItemData` 描述当前实例的 ID 与附加 `data`。例如核心的批次为 `batch-01`，产业槽保存这份数据。下面是局部写入：

```java
SpecialItemData installed = new SpecialItemData("GearIn_refinery_core", "batch-01");
industry.setSpecialItem(installed);
SpecialItemData current = industry.getSpecialItem();
```

`getId()` 读取注册 ID，`getData()` 读取实例字符串，`setData()` 可以修改它。批次、来源与少量参数适合放在 data 中，本节使用 `batch-01` 与 `salvage-cache`；需要结构化内容时，再约定当前功能的 JSON 字段。

`equals()` 与 `hashCode()` 同时比较 ID 和 data，所以两个同 ID、不同批次的物品可以保存在不同栈中。显示名称用于辨认，实例的相等与合并按数据判断。产业槽保存一份当前安装数据，货舱栈另外保存数量和空间。

## 把右键物品放入玩家货舱

先实现可以直接观察的右键动作。保存 `GearInSalvageTokenPlugin.java` 到 `src/data/scripts/gearin/campaign/`，它继承 `BaseSpecialItemPlugin`。在已有的 `onNewGameAfterTimePass(...)` 追加以下局部片段，沿用 `sector`，并添加 `SpecialItemData` 的 import：

```java
sector.getPlayerFleet().getCargo().addSpecial(
        new SpecialItemData("GearIn_salvage_token", "salvage-cache"), 1f);
```

新档向玩家货舱加入一枚代币。插件的 `hasRightClickAction()` 返回 `true`，界面就能调用 `performRightClickAction(RightClickActionHelper helper)`。用 helper 扣除当前物品并添加 10 补给，局部方法如下：

```java
public void performRightClickAction(RightClickActionHelper helper) {
    helper.removeFromClickedStackFirst(1);
    helper.addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, 10f);
}
```

helper 作用于当前交互中的货物，扣除优先从被右键点击的栈进行。`shouldRemoveOnRightClickAction()` 控制动作后自动移除一件；本例已经手动扣除，因此返回 `false`。实际右键从玩家货舱进行，交易界面已有待结算交易时，先完成交易再使用物品。

::: warning 手动扣除与自动移除分别选择
调用 `removeFromClickedStackFirst(1)` 后，再让 `shouldRemoveOnRightClickAction()` 返回 `true`，同一次动作会尝试两次移除。本例使用 helper 手动扣除，自动移除保持 `false`。
:::

## 给产业临时应用核心效果

核心实验使用 [C12-S5] 的 Element Works。保存 `GearInRefineryCorePlugin.java` 与 `GearInSpecialItemProbe.java` 到同一目录，在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 中，将以下局部调用放在产业状态日志之后：

```java
new GearInSpecialItemProbe().log(market);
```

观察器从当前市场取得产业，保存原安装数据，再创建 `SpecialItemData` 并写入槽。核心的应用由本节自定义方法显式调用，局部操作如下：

```java
SpecialItemData data = new SpecialItemData("GearIn_refinery_core", "batch-01");
industry.setSpecialItem(data);
GearInRefineryCorePlugin plugin = (GearInRefineryCorePlugin) spec.getNewPluginInstance(null);
plugin.applyToIndustry(industry);
```

`applyToIndustry(...)` 只对 `GearIn_element_works` 写入 `rare_metals` 供给，使用 `GearIn_refinery_core` 来源增加 3 档。`unapplyFromIndustry(...)` 按同一来源解除，完整观察器在 `finally` 中恢复安装数据，日志比较应用前、生效中与撤销后。

**写入产业槽、调用效果与建立安装菜单分别完成。** 本例验证槽数据与显式供给修饰；产业菜单由 `InstallableIndustryItemPlugin` 接入，持续效果还要进入产业的应用和撤销流程。CSV 的 `colony_item` 标签与参数字符串并没有自动完成这些工作。

原版 `GenericSpecialItemPlugin` 配合 `plugin params` 与 `ItemEffectsRepo` 提供产业安装物品的通用处理。本例从基础插件继承，当前方法由观察器直接调用，所以核心实验结束后解除，玩家代币的消耗与奖励则保留。

## 从栈与规格取得插件

上面的两个调用入口创建插件时，需要的上下文不同。货舱通过 `addSpecial(data, quantity)` 创建特殊物品栈，随后从栈取得实例数据与插件。以下局部片段使用独立测试货舱：

```java
CargoAPI cargo = Global.getFactory().createCargo(true);
cargo.addSpecial(new SpecialItemData("GearIn_salvage_token", "salvage-cache"), 1);
for (CargoStackAPI stack : cargo.getStacksCopy()) {
    if (!stack.isSpecialStack()) {
        continue;
    }
    SpecialItemData data = stack.getSpecialDataIfSpecial();
    SpecialItemPlugin plugin = stack.getPlugin();
    log.info(data.getId() + " / " + data.getData() + " / " + plugin.getName());
}
```

`getSize()` 提供数量，`getSpecialDataIfSpecial()` 返回当前 ID 与 data，`getSpecialItemSpecIfSpecial()` 返回规格。插件先通过 `setId(id)` 取得规格，再通过 `init(stack)` 保存当前栈，右键与货舱 tooltip 就拥有了栈上下文。

::: warning 无栈插件不会调用 init
`spec.getNewPluginInstance(null)` 只调用 `setId()`。这个实例可以读取名称、价格与描述；需要数量或实例 data 的方法，要由已绑定栈的入口调用。
:::

核心方法只需要规格名称与传入产业，因而可以使用无栈实例。插件可能为绘制、提示和交互分别创建，实例字段是否已有栈要按调用入口判断。

## 读取规格，扩展显示与掉落

`Global.getSettings().getSpecialItemSpec(id)` 返回整类物品的 `SpecialItemSpecAPI`，局部读取如下：

```java
SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec("GearIn_refinery_core");
if (spec != null) {
    log.info(spec.getId() + " / " + spec.getName());
    log.info("price=" + spec.getBasePrice() + ", stack=" + spec.getStackSize());
    log.info("space=" + spec.getCargoSpace() + ", icon=" + spec.getIconName());
    log.info("tags=" + spec.getTags() + ", rarity=" + spec.getRarity());
}
```

### 特殊物品规格速查表

| 字段与读取入口                                                        | 用途                       |
| --------------------------------------------------------------------- | -------------------------- |
| `name`、`id`；`getName()`、`getId()`                                  | 显示名称与稳定 ID          |
| `tags`；`getTags()`、`hasTag()`                                       | 分类与读取方的筛选         |
| `tech/manufacturer`；`getManufacturer()`                              | 设计来源文本               |
| `rarity`、`base price`；`getRarity()`、`getBasePrice()`               | 掉落选择参数与默认价格     |
| `stack size`、`cargo space`；`getStackSize()`、`getCargoSpace()`      | 堆叠上限与单件空间         |
| `baseRaidDanger`；`getBaseDanger()`                                   | 突袭危险度                 |
| `icon`、两列声音；`getIconName()`、`getSoundId()`、`getSoundIdDrop()` | 图标与已登记声音           |
| `plugin`、`plugin params`；`getNewPluginInstance(...)`、`getParams()` | 插件创建与规格级参数字符串 |
| `desc`、`order`；`getDesc()`、`getOrder()`                            | 描述正文与列表排序         |

规格的 `setDesc()`、`setName()`、`setIconName()`、`setBasePrice()`、`setManufacturer()`、`setParams()` 与 `setOrder()` 修改全局定义，随后读取同一规格的插件也会受影响。临时实例状态保存到 data，插件依当前栈解释它。

基础插件的 `getName()` 读取规格名，`getDesignType()` 读取制造商，`getPrice(market, submarket)` 默认采用基础价格。自定义价格可以结合传入市场计算，费用与合法性继续由交易流程处理。

`createTooltip(...)` 提供货舱和图鉴说明，`isTooltipExpandable()` 与 `getTooltipWidth()` 控制展开和宽度。需要特殊图形时实现 `render(...)` 并使用渲染接口；当前物品保留基础图标与说明。原版蓝图在右键时更新已知设计，`WormholeScannerPlugin` 则在右键动作中改变通行能力，可以分别参考它们的对象与入口。

掉落参数由 `resolveDropParamsToSpecificItemData(params, random)` 转换为实例 data。无参数时返回空字符串，返回 `null` 表示不产生该物品；实际加入 Cargo 仍由掉落流程完成。

## 完整最小例

三个类保存到前述路径，产业实验与玩家代币分别采用上述入口。

::: details 完整成品：GearInRefineryCorePlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;

public class GearInRefineryCorePlugin extends BaseSpecialItemPlugin {
    public static final String ITEM_ID = "GearIn_refinery_core";
    public static final String MOD_ID = "GearIn_refinery_core";

    public void applyToIndustry(Industry industry) {
        if (!"GearIn_element_works".equals(industry.getId())) {
            return;
        }
        industry.getSupply("rare_metals").getQuantity().modifyFlat(MOD_ID, 3f, getName());
    }

    public void unapplyFromIndustry(Industry industry) {
        if (!"GearIn_element_works".equals(industry.getId())) {
            return;
        }
        industry.getSupply("rare_metals").getQuantity().unmodifyFlat(MOD_ID);
    }
}
```

:::

::: details 完整成品：GearInSalvageTokenPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class GearInSalvageTokenPlugin extends BaseSpecialItemPlugin {
    public static final String ITEM_ID = "GearIn_salvage_token";

    @Override
    public boolean hasRightClickAction() {
        return true;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        helper.removeFromClickedStackFirst(1);
        helper.addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, 10f);
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }
}
```

:::

::: details 完整成品：GearInSpecialItemProbe

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class GearInSpecialItemProbe {
    public void log(MarketAPI market) {
        inspectSpec("GearIn_refinery_core");
        inspectSpec("GearIn_salvage_token");

        CargoAPI cargo = Global.getFactory().createCargo(true);
        SpecialItemData coreData = new SpecialItemData("GearIn_refinery_core", "batch-01");
        SpecialItemData tokenData = new SpecialItemData("GearIn_salvage_token", "salvage-cache");
        cargo.addSpecial(coreData, 1f);
        cargo.addSpecial(tokenData, 1f);

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isSpecialStack()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                SpecialItemPlugin plugin = stack.getPlugin();
                Global.getLogger(GearInSpecialItemProbe.class).info(
                        "special=" + data.getId() + ", data=" + data.getData() +
                        ", plugin=" + plugin.getClass().getSimpleName());
            }
        }

        Industry industry = market.getIndustry("GearIn_element_works");
        SpecialItemData previous = industry.getSpecialItem();
        SpecialItemSpecAPI coreSpec = Global.getSettings().getSpecialItemSpec(coreData.getId());
        GearInRefineryCorePlugin corePlugin = (GearInRefineryCorePlugin) coreSpec.getNewPluginInstance(null);
        float before = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        float during;
        try {
            industry.setSpecialItem(coreData);
            corePlugin.applyToIndustry(industry);
            during = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        } finally {
            corePlugin.unapplyFromIndustry(industry);
            industry.setSpecialItem(previous);
        }
        float after = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        Global.getLogger(GearInSpecialItemProbe.class).info(
                "rare_metals before=" + before + ", during=" + during + ", after=" + after);
    }

    private void inspectSpec(String id) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
        if (spec == null) {
            Global.getLogger(GearInSpecialItemProbe.class).warn("missing special item spec=" + id);
            return;
        }
        Global.getLogger(GearInSpecialItemProbe.class).info(
                "spec=" + spec.getId() + ", name=" + spec.getName() +
                ", price=" + spec.getBasePrice() + ", stack=" + spec.getStackSize() +
                ", space=" + spec.getCargoSpace() + ", tags=" + spec.getTags());
    }

}
```

:::

## 验证方案

前置数据：MOD 已加载两条 `special_items.csv` 注册行、三个 Java 类，并且测试市场包含 `GearIn_element_works` 产业。

1. 保存 CSV、三个类、产业实验与新档代币入口，构建重启并创建测试新档，再进入 Foundry 市场。
2. 运行观察器，读取两个 `SpecialItemSpecAPI` 的 ID、名称、价格、堆叠、空间和标签。
3. 创建两份 `SpecialItemData`，加入独立 Cargo 并遍历特殊物品栈。
4. 将 `GearIn_refinery_core` 写入产业槽，调用插件应用 `rare_metals` 产出修饰。
5. 记录安装修饰生效前、生效中和撤销后的供给数量。
6. 打开玩家货舱，完成已有待结算交易，记录补给数量，右键 Salvage Token，比较代币与补给的变化。
7. 查看产业实验的 before/during/after 日志，确认原安装数据恢复；右键的消耗与 10 补给奖励保留在玩家货舱。

预期结果：

- 两个 ID、显示名和插件类正确对应；
- Cargo 栈能够返回 `SpecialItemData` 与插件实例；
- `Refinery Core` 只对 `GearIn_element_works` 的 `rare_metals` 供给增加 3；
- modifier 清理后供给数量恢复到观察前值；
- `Salvage Token` 右键动作移除 1 个当前物品并增加 10 单位补给；
- 产业原安装数据与供给恢复；玩家货舱保存本次右键消耗与奖励结果。

## 故障分析

- **规格读取失败**：注册路径或 ID 与代码不一致。
    - **解决方案**：检查 CSV 的 `id`、MOD 数据路径和插件日志中的实际加载 ID，确保代码使用同一字符串。

- **货物栈插件与预期不同**：注册行的插件名称指向其他类。
    - **解决方案**：核对注册行的 `plugin` 全限定类名，并确认类位于 MOD 的 `data.scripts.gearin.campaign` 包。

- **产业产出没有变化**：产业 ID 或应用入口与当前物品实验不一致。
    - **解决方案**：确认产业 ID 是 `GearIn_element_works`，且应用和撤销使用同一个 modifier ID。

- **右键后物品被扣除两次**：手动扣除与自动移除同时启用。
    - **解决方案**：插件已经通过 helper 移除当前栈时，将 `shouldRemoveOnRightClickAction()` 保持为 `false`。

## 总结

代币通过货舱栈取得插件，右键动作使用 helper 完成消耗与奖励；核心实验从规格创建无栈插件，显式写入产业供给并按来源撤销。注册规格、实例 data 与栈数量分别提供两种功能所需的信息。

产业槽数据还要通过安装菜单与产业生命周期接入，才能成为长期安装效果。本节先把每个实际调用说明清楚，结果分别在玩家货舱与产业日志中确认。

## 建议参考项

- `data/campaign/special_items.csv`：特殊物品规格与插件入口的注册来源；
- `com.fs.starfarer.api.campaign.SpecialItemSpecAPI`：特殊物品静态规格的读取入口；
- `com.fs.starfarer.api.campaign.SpecialItemData`：物品实例 ID 与附加数据的表达对象；
- `com.fs.starfarer.api.campaign.SpecialItemPlugin`：价格、显示、掉落和交互行为的插件接口；
- `com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin`：特殊物品插件的继承起点；
- `com.fs.starfarer.api.campaign.econ.Industry`：产业特殊物品槽的写入与读取入口。
