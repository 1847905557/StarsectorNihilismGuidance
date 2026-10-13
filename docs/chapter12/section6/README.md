# 注册特殊物品

商品与产业都已经接入，现在制作两件带行为的特殊物品。Refinery Core 在一次产业实验中给稀有金属供给加 3，再解除并恢复原安装数据；Salvage Token 放入玩家货舱，右键消耗一枚换取 10 补给。注册规格、实例 data、货物栈和插件分别接好，才能让同一物品在不同入口工作。

*本节预期阅读时间：\~22 分钟。*

*本节预期操作时间：\~30 分钟。*

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

## 特殊物品的数据组成

CSV 行提供静态规格，`SpecialItemSpecAPI` 保存这些规格，`SpecialItemData` 保存一次物品实例的 ID 与 data，`SpecialItemPlugin` 再根据物品栈执行价格、显示和交互行为。

同一份 `SpecialItemData` 可以放入 Cargo，也可以写入产业的特殊物品槽。Cargo 栈负责数量和容量，产业负责保存当前安装数据，插件负责解释数据并提供行为。

## 注册特殊物品身份

文件路径是 `data/campaign/special_items.csv`。表头列出名称、ID、标签、制造商、稀有度、价格、堆叠、空间、袭击危险度、图标、声音、插件、参数、描述和排序。

```csv
name,id,tags,tech/manufacturer,rarity,base price,stack size,cargo space,baseRaidDanger,icon,sound id,sound id drop,plugin,plugin params,desc,order
Refinery Core,GearIn_refinery_core,"gear_industry, colony_item",GearIn Industries,0.5,75000,1000,1,MEDIUM,graphics/icons/cargo/nanoforge_pristine.png,ui_cargo_special_industrial_pickup,ui_cargo_special_industrial_drop,data.scripts.gearin.campaign.GearInRefineryCorePlugin,GearIn_element_works,"A GearIn industrial core that increases rare metal output when installed in Element Works.",1450
Salvage Token,GearIn_salvage_token,gear_industry,GearIn Industries,1,5000,1000,1,LOW,graphics/icons/cargo/modspec2.png,ui_chip_pickup,ui_modspec_drop,data.scripts.gearin.campaign.GearInSalvageTokenPlugin,,"A token that can be exchanged for a small supply cache.",1451
```

`name` 是界面显示名，`id` 是代码和 `SpecialItemData` 使用的稳定键。名称可以修改，ID 一旦投入存档就应保持稳定。

`tags` 用于分类和原版逻辑筛选。`tech/manufacturer` 只提供设计来源文本，不承担插件选择。`rarity` 影响掉落选择，`base price` 供插件默认价格使用。

`stack size` 和 `cargo space` 决定货舱中的数量与空间表现。图标采用实际资源路径，两列声音采用已登记的声音 ID。

`plugin` 是实例化入口。游戏读取该类名后，会在特殊物品栈创建插件并调用 `setId()` 与 `init()`。`plugin params` 是规格级字符串，插件可以通过 `getSpec().getParams()` 读取。

特殊物品描述直接来自 CSV 的 `desc`。货舱 tooltip 通常由插件调用 `spec.getDesc()` 显示，文本描述系统不参与这条链路。

## 读取特殊物品规格

全局设置通过 `Global.getSettings().getSpecialItemSpec(id)` 返回 `SpecialItemSpecAPI`。这个对象描述 "这类物品是什么"，与某个 Cargo 栈的数量和 data 分开。

```java
SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec("GearIn_refinery_core");
if (spec != null) {
    log.info(spec.getId() + " / " + spec.getName());
    log.info("price=" + spec.getBasePrice() + ", stack=" + spec.getStackSize());
    log.info("space=" + spec.getCargoSpace() + ", icon=" + spec.getIconName());
    log.info("tags=" + spec.getTags() + ", rarity=" + spec.getRarity());
}
```

常用身份读取包括 `getId()`、`getName()`、`getDesc()`、`getManufacturer()` 和 `getParams()`。货舱布局使用 `getStackSize()` 与 `getCargoSpace()`，界面资源使用 `getIconName()`、`getSoundId()` 和 `getSoundIdDrop()`。

`getBasePrice()` 是规格默认值。插件可以根据市场或子市场返回另一价格，但不应把这种运行时价格写回 CSV 规格。

`getTags()` 返回标签集合，`hasTag(tag)` 适合做单个分类判断。`getOrder()` 控制列表排序，排序值只影响显示顺序。

规格也提供 `setDesc()`、`setName()`、`setIconName()`、`setBasePrice()`、`setManufacturer()`、`setParams()` 和 `setOrder()`。这些 setter 改变当前运行时规格，影响所有随后读取同一 spec 的插件；临时状态应放进 `SpecialItemData.data`，避免污染全局规格。

## 保存实例数据

`SpecialItemData` 只有两个字段：`id` 和 `data`。构造时传入注册 ID 与附加字符串：

```java
SpecialItemData installed = new SpecialItemData("GearIn_refinery_core", "batch-01");
industry.setSpecialItem(installed);
SpecialItemData current = industry.getSpecialItem();
```

`getId()` 返回注册 ID，`getData()` 返回附加数据，`setData()` 可以修改附加数据。`equals()` 与 `hashCode()` 同时比较 ID 和 data，因此同一 ID 的不同批次是不同的数据对象。

data 适合保存批次、来源或少量参数。本节使用 `batch-01` 和 `salvage-cache` 两个简单字符串；需要结构化内容时，再为当前功能约定 JSON 字段。

产业槽只保存一份当前 `SpecialItemData`。写入新对象前记录旧对象，完成观察后恢复旧值，能够避免测试改变存档中的产业状态。

## 从货舱栈取得插件

通过 `CargoAPI.addSpecial(data, quantity)` 加入特殊物品。随后从 `getStacksCopy()` 遍历栈，并用 `getSpecialDataIfSpecial()` 与 `getPlugin()` 读取实例。

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

栈的数量由 `CargoStackAPI.getSize()` 提供，特殊物品的规格由 `getSpecialItemSpecIfSpecial()` 提供。栈上的插件已经绑定了对应 spec；直接从全局设置重新创建插件会丢失栈引用。

## 特殊物品插件的初始化与行为入口

插件实现 `SpecialItemPlugin`。通常从 `BaseSpecialItemPlugin` 继承，以获得 ID、规格、名称、默认价格、设计类型和基础 tooltip 行为。

游戏先调用 `setId(id)`，基类随后读取 `SpecialItemSpecAPI`。当插件来自 Cargo 栈时，游戏继续调用 `init(stack)`；通过 spec 直接调用 `getNewPluginInstance(null)` 时，`init()` 不会被调用，只有 `setId()` 一定执行。

插件的 `getName()`、`getPrice(market, submarket)`、`getSpec()` 分别负责显示名、交易价格和规格访问。价格可以读取市场与子市场，但默认实现通常返回 CSV 的 base price。

`createTooltip()` 负责货舱和图鉴中的说明。`isTooltipExpandable()` 与 `getTooltipWidth()` 控制展开方式和宽度。`render()` 接收渲染器接口；简单物品可以保留空实现。

掉落组可以把参数传给 `resolveDropParamsToSpecificItemData(params, random)`，由插件把随机结果转换成 `SpecialItemData.data`。没有参数时返回空字符串即可。

## 把物品安装进产业槽

**产业槽数据与安装菜单是两个入口。** 当前观察器直接写 `Industry.setSpecialItem()` 并调用自定义应用方法，验证数据和供给修饰。正式产业菜单的安装按钮由 `InstallableIndustryItemPlugin` 提供；仅登记特殊物品不会自动出现那个按钮。

```java
SpecialItemData data = new SpecialItemData("GearIn_refinery_core", "batch-01");
industry.setSpecialItem(data);
GearInRefineryCorePlugin plugin = (GearInRefineryCorePlugin) spec.getNewPluginInstance(null);
plugin.applyToIndustry(industry);
```

插件只对 ID 为 `GearIn_element_works` 的产业写入 `rare_metals` 供给。修饰 ID 固定为 `GearIn_refinery_core`，撤销时使用同一个 ID 调用 `unmodifyFlat()`。

## 把右键物品放入玩家货舱

保存 `GearInSalvageTokenPlugin.java` 与本节其他类到 `src/data/scripts/gearin/campaign/`。向 [C11-S3] 已有的 `onNewGameAfterTimePass(...)` 追加下面的局部片段，沿用 `sector`，并添加 `SpecialItemData` 的 import：

```java
sector.getPlayerFleet().getCargo().addSpecial(
        new SpecialItemData("GearIn_salvage_token", "salvage-cache"), 1f);
```

新档创建一枚代币，打开玩家货舱即可操作。插件让 `hasRightClickAction()` 返回 `true`，在 `performRightClickAction(RightClickActionHelper helper)` 完成扣除与奖励。局部方法如下：

```java
public void performRightClickAction(RightClickActionHelper helper) {
    helper.removeFromClickedStackFirst(1);
    helper.addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, 10f);
}
```

`shouldRemoveOnRightClickAction()` 决定交互完成后是否自动移除。插件已经手动移除当前栈时返回 `false`，可以避免一次动作扣除两次。

## 观察 tooltip、掉落和 UI 边界

基础插件的 tooltip 会使用规格名称、制造商和描述。自定义插件可以追加物品 data、产业状态或动作提示，但应把费用和交易合法性留给交易界面已有流程。

`getDesignType()` 返回制造商文本，`getPrice()` 返回当前交易价格，`render()` 只在物品需要特殊图形时实现。`resolveDropParamsToSpecificItemData()` 只负责掉落参数转换，不负责向 Cargo 写入物品。

插件通过 `getSpec()` 取得静态字段，通过保存的 `stack` 取得当前栈。没有栈的插件实例仍可以显示名称和 tooltip，但不能读取栈数量或调用栈来源相关逻辑。

## 对照原版插件的接口边界

原版 `GenericSpecialItemPlugin` 根据 `plugin params` 和 `ItemEffectsRepo` 处理产业安装物品；蓝图插件在右键动作中修改已知蓝图；`WormholeScannerPlugin` 展示了带参数的右键入口。GearIn 示例把产业修饰和消耗流程写在自定义类中，便于观察每个接口的调用边界。

## 在经济观察器中运行产业实验

保存 `GearInSpecialItemProbe.java` 与核心插件之后，把下面的局部调用加入 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()`，位于产业状态日志之后：

```java
new GearInSpecialItemProbe().log(market);
```

当前市场已添加 Element Works，观察器建立独立测试货舱、取得已注册规格，再在同一轮保存产业原物品、应用 +3、解除并恢复。右键实验则在玩家货舱完成。

编者建议把物品的 data 与数量分别记录。两枚名称相同的核心可以有不同批次，栈的合并与相等判断也受 data 影响，名称并不足以定位当前实例。

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
6. 打开玩家货舱，记录补给数量，右键 Salvage Token，比较代币与补给的变化。
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

两件物品从 CSV 取得规格，用 `SpecialItemData` 保存 ID 与批次，再由栈插件提供显示与行为。核心实验显式应用并解除供给修饰，代币则通过货舱右键消耗和奖励。

物品规格、产业槽与安装菜单各有接入入口。先确定当前操作由谁调用、需要什么对象，再组合插件方法，实际结果就能在产业日志或玩家货舱中确认。

## 建议参考项

- `data/campaign/special_items.csv`：特殊物品规格与插件入口的注册来源；
- `com.fs.starfarer.api.campaign.SpecialItemSpecAPI`：特殊物品静态规格的读取入口；
- `com.fs.starfarer.api.campaign.SpecialItemData`：物品实例 ID 与附加数据的表达对象；
- `com.fs.starfarer.api.campaign.SpecialItemPlugin`：价格、显示、掉落和交互行为的插件接口；
- `com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin`：特殊物品插件的继承起点；
- `com.fs.starfarer.api.campaign.econ.Industry`：产业特殊物品槽的写入与读取入口。
