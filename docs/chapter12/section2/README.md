# 读写货舱内容

交易市场已经建立，这次直接读写它所使用的货舱数据。本节创建一份独立 `CargoAPI`，加入补给、燃料、人员和重型机械，记录数量、信用与空间，再用副本比较一次移除。库存容器可以由舰队、子市场或打捞界面提供；货物栈、信用和封存舰船的复制范围也分别确认。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 使用工厂创建独立的 `CargoAPI`；
- 读取和修改补给、燃料、船员、陆战队、商品与信用；
- 使用 `CargoStackAPI` 识别货物类型、数量、空间和显示信息；
- 读取容量、复制 Cargo，并理解空栈的处理方式；
- 将 Cargo 内的货物栈与封存舰船分开处理。

## 前置知识

- [C11-S4] 的 `FleetMemberAPI` 与舰队数据；
- [C12-S1] 的市场、子市场和实体绑定关系；
- [C10-S1] 的生涯登记与观察脚本。

## 登记独立货舱观察

创建 `src/data/scripts/gearin/campaign/GearInCargoServicesScript.java`，把这个类加入 `GearInModPlugin` 的 import。向 [C11-S3] 已有的 `onNewGameAfterTimePass(...)` 追加局部登记，沿用 `sector`：

```java
sector.addTransientScript(new GearInCargoServicesScript());
```

脚本在新档推进后创建独立货舱、记录并比较，完成后结束。当前数据用于日志实验。

**Cargo 是库存容器，先确认谁持有它。** 舰队、子市场与打捞界面都可以提供这份对象。读取已有对象时，舰队通常通过 `CampaignFleetAPI.getCargo()` 取得，子市场通过 `SubmarketAPI.getCargo()` 取得；交易或打捞界面会把正在操作的 Cargo 传给界面代码。拿到对象以后，读取数量、遍历栈和计算空间都使用同一套 `CargoAPI` 方法。

Cargo 自身只描述内容和库存状态，市场的价格、税率和子市场规则由 `MarketAPI` 与 `SubmarketAPI` 管理。需要判断某件货物属于哪一类时，读取它所在的 `CargoStackAPI`，不要把 Cargo 当成单一的商品列表。

## 创建独立货舱

用 `Global.getFactory().createCargo(true)` 创建独立货舱，再写入本次实验内容。当前创建结果默认启用 `unlimitedStacks`，它决定单个货物栈的数量上限；需要改为规格的栈上限时，调用 `setUnlimitedStacks(false)`，再用 `isUnlimitedStacks()` 读取选择。舰队的总容量另由成员与游戏规则计算。

```java
CargoAPI cargo = Global.getFactory().createCargo(true);
cargo.addSupplies(120f);
cargo.addFuel(80f);
cargo.addCrew(40);
cargo.addMarines(12);
cargo.addCommodity(Commodities.HEAVY_MACHINERY, 25f);
```

`createCargo(true)` 返回的是一份新的对象，不会自动挂到玩家舰队或市场。要让它成为某个系统的一部分，调用方需要把内容合并到目标 Cargo，或者把它交给能够接收 Cargo 的交易、奖励或打捞流程。

## 读取补给、燃料和人员

补给和燃料有独立的读写方法，数量使用 `float`。船员和陆战队都属于人员，但 API 分别提供了 `getCrew()` 与 `getMarines()`；`getTotalCrew()` 只统计船员，`getTotalPersonnel()` 则把船员和陆战队合在一起。本例初始船员 40、陆战队 12，总人员 52。写入后再从同一 Cargo 读取，局部操作如下：

```java
float supplies = cargo.getSupplies();
float fuel = cargo.getFuel();
int crew = cargo.getCrew();
int marines = cargo.getMarines();
int personnel = cargo.getTotalPersonnel();

cargo.addSupplies(20f);
cargo.removeFuel(10f);
cargo.addCrew(5);
cargo.removeMarines(2);
```

商品通过商品 ID 读写。`getCommodityQuantity(id)` 返回当前数量，`addCommodity()` 与 `removeCommodity()` 会更新对应商品栈；ID 应该来自 `Commodities` 或其他已注册的商品规格。

## 按商品 ID 读写商品

商品数量可以是小数，因此读取结果使用 `float`。添加商品时，Cargo 会按商品规格建立或更新对应栈；移除数量超过现有库存时，实际结果仍应通过再次读取确认。商品 ID 只描述是哪一种商品，价格和交易限制属于市场经济对象，不由 Cargo 保存。

```java
float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
cargo.addCommodity(Commodities.HEAVY_MACHINERY, 5f);
float machineryAfterAdd = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
cargo.removeCommodity(Commodities.HEAVY_MACHINERY, 2f);
LOG.info(String.format("machinery %.1f -> %.1f -> %.1f",
        machineryBefore, machineryAfterAdd,
        cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY)));
```

商品、武器、战机和特殊物品都可以出现在 Cargo 中，但它们的附加数据不同。商品适合用商品 ID 直接查询；其他物品通常先取得栈，再通过栈上的类型和数据对象继续处理。这样既能保持商品查询的简单性，也能保留装备对象所需的规格信息。

## 读写 MutableValue 信用

Cargo 的信用不是一个普通 `float` 字段，而是 `MutableValue` 对象。`getCredits()` 取得这个可变值，`get()` 读取当前金额，`set()` 直接设值，`add()` 与 `subtract()` 用于增减。交易、奖励和费用代码通常使用增减方法，这样可以保持调用意图清晰。

```java
MutableValue credits = cargo.getCredits();
float before = credits.get();
credits.add(2500f);
LOG.info("credits=" + credits.get() + " before=" + before);
credits.subtract(500f);
```

信用不会生成 `CargoStackAPI`，遍历货舱栈时也不会看到它。需要复制或清空 Cargo 时，应按照 `CargoAPI` 对信用的语义单独确认结果。

## 读取容量和空间占用

`getMaxCapacity()` 是货舱容量上限，`getSpaceUsed()` 是当前物品占用的空间，`getSpaceLeft()` 是可用空间。空间不是商品数量的简单相加：人员、燃料、武器和特殊物品可能有不同的单件空间，封存舰船也由独立的舰队数据承载。向有限容量货舱写入后，应重新读取实际数量和空间。

**容量描述当前状态，直接添加方法仍可能形成超载。** `addCommodity(...)` 按货物与栈规则加入数量，不会替调用方按总货舱容量截断。实际转移、奖励与界面代码按照各自流程处理容量，当前记录添加后的数量与空间。

`getSpaceLeft()` 将容量减去已用空间，并以 0 为下限。独立 Cargo 使用自身默认容量，当前为 1000；舰队货舱再由实际舰队配置容量。超过上限时仍按实际数量与已用空间观察，剩余空间以 0 为下限。

如果需要判断某一类物品是否会超出容量，先用 `getCargoSpacePerUnit()` 取得单位空间，再结合 `getSize()` 和当前 `getSpaceLeft()` 计算一个估算值。这个估算只用于界面提示；真正写入后仍以 Cargo 的实际数量为准。

```java
float leftBefore = cargo.getSpaceLeft();
float unitSpace = 0f;
for (CargoStackAPI stack : cargo.getStacksCopy()) {
    if (stack.isCommodityStack()
            && Commodities.HEAVY_MACHINERY.equals(stack.getCommodityId())) {
        unitSpace = stack.getCargoSpacePerUnit();
    }
}
LOG.info(String.format("space before=%.1f machinery unit=%.2f", leftBefore, unitSpace));
```

```java
float capacity = cargo.getMaxCapacity();
float used = cargo.getSpaceUsed();
float left = cargo.getSpaceLeft();
LOG.info(String.format("cargo space: %.1f / %.1f, left=%.1f", used, capacity, left));
```

::: tip 单栈上限与总容量

`unlimitedStacks` 处理每一栈能容纳多少件，`getMaxCapacity()` 处理总空间，两者各有用途。当前创建默认使用无限栈，参数写成 `false` 也采用这个默认结果；需要选择规格栈上限时显式调用 `setUnlimitedStacks(false)`。

:::

## 按类型读取货舱栈

`CargoStackAPI` 是 Cargo 内的一项货物栈。`getStacksCopy()` 返回当前栈集合的副本，适合遍历和记录；它不是对内部集合的实时视图。每个栈都有一个 `CargoItemType`，可以通过 `getType()` 判断大类，再使用对应的专用方法读取数据。

常用读取方式如下：

- `getSize()`：栈内数量；
- `getCargoSpace()`：该栈当前占用的货舱空间；
- `getCargoSpacePerUnit()`：单件空间；
- `getDisplayName()`：适合日志或界面显示的名称；
- `getData()`：栈关联的原始数据对象；
- `isCommodityStack()`、`getCommodityId()`：判断并读取商品栈；
- `isWeaponStack()`、`getWeaponSpecIfWeapon()`：判断并读取武器栈；
- `isNull()`：判断栈是否没有有效内容。

```java
for (CargoStackAPI stack : cargo.getStacksCopy()) {
    LOG.info(String.format("type=%s name=%s size=%.1f space=%.1f null=%s",
            stack.getType(), stack.getDisplayName(), stack.getSize(),
            stack.getCargoSpace(), stack.isNull()));
    if (stack.isCommodityStack()) {
        LOG.info("commodity=" + stack.getCommodityId());
    }
}
```

类型决定 `getData()` 的解释：商品、武器和战机栈使用相应 ID，特殊物品栈使用 `SpecialItemData`。武器规格则通过 `getWeaponSpecIfWeapon()` 取得。读取前先判断类型，再调用对应 getter，可以避免把不同物品的底层数据混在一起。

`CargoItemType.RESOURCES`、`WEAPONS`、`FIGHTER_CHIP`、`SPECIAL` 和 `NULL` 是栈分类，而不是商品 ID。一个资源分类下仍然可能有多种商品；一个特殊物品栈的 `getData()` 也不能直接转换成商品规格。代码通常先判断 `getType()`，然后调用与该分类匹配的 getter。

栈数量可以由 `setSize()` 直接设置，也可以用 `add()`、`subtract()` 做增减。教学示例优先使用 Cargo 层的 `addCommodity()`、`removeCommodity()` 等专用方法，因为它们会按商品语义定位目标栈；只有已经取得明确栈对象并且确实需要逐栈编辑时，才直接修改栈数量。

`getDisplayName()` 适合日志和玩家可见文本，`getData()` 适合继续交给规格或插件处理。两者承担的职责不同：显示名称不能作为稳定 ID 保存，数据对象也不适合直接拼接给玩家展示。

`getStacksCopy()` 的副本只保证遍历时集合稳定。栈本身仍然属于原 Cargo，直接调用栈的修改方法会改变原对象。需要分别计算货物数量时使用 `createCopy()`，副本中的栈数量独立；附加 data 对象的共享范围继续按其类型确认。

```java
CargoAPI calculation = cargo.createCopy();
for (CargoStackAPI stack : calculation.getStacksCopy()) {
    if (stack.isCommodityStack()) {
        LOG.info("calculation stack=" + stack.getCommodityId());
    }
}
```

::: warning 货物栈和封存舰船分开处理

普通货物通过 `CargoStackAPI` 遍历；封存舰船通过 `cargo.getMothballedShips()` 取得 `FleetDataAPI`，每艘船再以 `FleetMemberAPI` 表示。两组数据都属于 Cargo 的内容，但不会在同一个 `CargoStackAPI` 中出现。处理舰船时应使用舰队 API 的方法，不要尝试把 `FleetMemberAPI` 强行转换成货物栈。

:::

## 清理、合并和复制 Cargo

`removeEmptyStacks()` 会清理当前 Cargo 中的空栈。清理后重新调用 `getStacksCopy()`，不要继续使用已经移除的栈引用。`addAll(CargoAPI)` 可以把另一份 Cargo 的内容合并进来，`removeAll(CargoAPI)` 按栈移除内容；这两个操作都应在确认目标容量和权限后执行。

**`createCopy()` 复制货物栈数量与无限栈设置，信用和封存舰船另行处理。** 栈上的规格与附加 data 对象也可能继续共享。本节用不可变商品 ID 比较数量，重型机械的移除只改变原 Cargo；信用副本初始为 0，单独记录即可看清两种范围。

合并时要先确认两份 Cargo 的用途。把打捞结果合并到玩家货舱属于一次明确的库存转移；把市场 Cargo 合并到临时清单则可能绕过交易流程。`addAll()` 只负责数据合并，费用、权限、市场库存和界面刷新仍由调用方承担。

清空操作也要区分范围。`clear()` 处理 Cargo 保存的普通库存，封存舰船仍由舰队数据保存；如果业务需要同时移除舰船、信用和物品，应该分别记录每组数据的原值，再按相同对象边界执行清理。日志至少记录操作前后的 Cargo 身份、商品数量和空间占用，便于确认清理没有误触其他持有者。

对于市场和打捞流程，Cargo 往往会在界面关闭、交易完成或奖励结算时继续被使用。脚本在这些流程中保存的栈引用只适合短时观察；跨帧保存时应重新取得持有者提供的 Cargo，或者保存 `createCopy()` 结果作为只读快照。

编者建议先固定货舱对象，再按同一组字段比较操作。几份货舱都装着补给，名称并不能区分它们；持有者与对象引用才说明当前改的是哪一份。

## 完整最小例

保存下列类并保留新档登记。数量、副本和信用采用同一组日志比较。

::: details 完整成品：GearInCargoServicesScript

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.MutableValue;

public class GearInCargoServicesScript implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }

        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.addSupplies(120f);
        cargo.addFuel(80f);
        cargo.addCrew(40);
        cargo.addMarines(12);
        cargo.addCommodity(Commodities.HEAVY_MACHINERY, 25f);

        MutableValue credits = cargo.getCredits();
        credits.add(2500f);

        CargoAPI snapshot = cargo.createCopy();
        LOG.info(String.format(
                "GearIn cargo created: supplies=%.0f fuel=%.0f crew=%d marines=%d personnel=%d credits=%.0f used=%.1f left=%.1f",
                cargo.getSupplies(), cargo.getFuel(), cargo.getCrew(), cargo.getMarines(),
                cargo.getTotalPersonnel(),
                credits.get(), cargo.getSpaceUsed(), cargo.getSpaceLeft()));

        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        int nullStacks = 0;
        for (CargoStackAPI stack : stacks) {
            if (stack.isNull()) {
                nullStacks++;
            }
            LOG.info(String.format("GearIn cargo stack: type=%s name=%s size=%.1f space=%.1f",
                    stack.getType(), stack.getDisplayName(), stack.getSize(), stack.getCargoSpace()));
        }

        cargo.removeCommodity(Commodities.HEAVY_MACHINERY, 5f);
        LOG.info(String.format("GearIn cargo changed: machinery=%.0f snapshotMachinery=%.0f credits=%.0f snapshotCredits=%.0f nullStacks=%d",
                cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY),
                snapshot.getCommodityQuantity(Commodities.HEAVY_MACHINERY),
                cargo.getCredits().get(), snapshot.getCredits().get(), nullStacks));
        done = true;
    }
}
```

:::

## 验证方案

前置数据：包含本节 Cargo 示例类的 GearIn MOD 已构建并启用。

1. 保存类与新档登记，构建重启，进入新档，搜索 `GearIn cargo created`。
2. 查看日志中的 Cargo 总览，以及每个栈的类型、名称、数量和空间。
3. 确认原 Cargo 的重型机械减少 5，而 `snapshot` 仍保留创建时的数量。
4. 在已有舰队或子市场货舱上使用只读的栈与容量片段，比较实际持有者、空间与货物数量。

预期结果：

- 日志出现 Cargo 总览以及每个栈的类型、名称、数量和空间；
- 原 Cargo 的重型机械由 25 变为 20，副本保持 25；原信用为 2500，副本信用为 0；
- 独立 Cargo 的容量与单栈状态分别读取；实际舰队或子市场继续按同一套方法观察持有的货舱。

## 故障分析

- **写入后已用空间超过总容量**：直接添加方法按货物栈处理数量，未按总容量截断。
    - **解决方案**：分别读取数量、总容量与已用空间，在实际转移流程按目标容量安排数量。

- **信用没有出现在栈遍历中**：信用没有生成货舱栈。
    - **解决方案**：通过 `getCredits()` 取得 `MutableValue`，再使用 `get()`、`add()` 或 `subtract()`。

- **遍历过程中出现空栈**：转移或清理过程留下了没有有效内容的栈。
    - **解决方案**：使用 `isNull()` 记录状态，清理完成后重新调用 `getStacksCopy()`。

- **把封存舰船当成普通货物处理**：舰船数据不属于 `CargoStackAPI`。
    - **解决方案**：通过 `getMothballedShips()` 取得 `FleetDataAPI`，再使用 `FleetMemberAPI` 读取舰船。

- **副本变化影响原货舱**：代码继续修改了原 Cargo 或共享的栈对象。
    - **解决方案**：使用 `createCopy()` 建立独立对象，只有在需要时才调用 `addAll()` 合并。

## 总结

独立货舱用资源与商品方法写入数量，用 `MutableValue` 记录信用，再从栈读取类型与空间。总容量、单栈上限和实际数量分别处理，货物栈副本、信用和封存舰船也各有复制范围。

这些方法可以复用到舰队、子市场与打捞数据，持有者则决定后续流程。当前先把一份货舱的数量前后值看清，交易与奖励再接到各自入口。

## 建议参考项

- `com.fs.starfarer.api.campaign.CargoAPI`：货舱内容、容量、复制和合并；
- `com.fs.starfarer.api.campaign.CargoStackAPI`：货舱栈的类型、数量、空间和关联数据；
- `com.fs.starfarer.api.util.MutableValue`：信用等可变数值的读写；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：封存舰船集合；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：封存舰船对象；
- `com.fs.starfarer.api.impl.campaign.ids.Commodities`：原版商品 ID 常量。
