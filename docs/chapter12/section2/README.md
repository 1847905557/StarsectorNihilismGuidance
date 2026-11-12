# 读写货舱内容

舰队货舱、子市场库存与打捞物都可以通过 `CargoAPI` 读写。本节建立一份独立货舱，加入补给、燃料、人员和重型机械，再复制其中的货物，比较数量、空间与信用。掌握这些操作后，奖励和交易流程就有了实际修改库存的入口。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~15 分钟。_

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

## 创建一份独立货舱

创建 `src/data/scripts/gearin/campaign/GearInCargoServicesScript.java`，在 `GearInModPlugin` 添加该类的 import，并向已有的 `onNewGameAfterTimePass(...)` 追加以下局部登记，沿用 `sector`：

```java
sector.addTransientScript(new GearInCargoServicesScript());
```

脚本在新档推进后执行一次，创建独立对象并记录结果。用 `Global.getFactory().createCargo(true)` 取得货舱，加入当前实验的物品：

```java
CargoAPI cargo = Global.getFactory().createCargo(true);
cargo.addSupplies(120f);
cargo.addFuel(80f);
cargo.addCrew(40);
cargo.addMarines(12);
cargo.addCommodity(Commodities.HEAVY_MACHINERY, 25f);
```

这里的 `cargo` 没有挂到玩家舰队或市场，数量变化只发生在这份实验对象中。要修改实际库存，从舰队的 `getCargo()`、子市场的 `getCargo()` 或交易、打捞流程提供的对象取得货舱，再执行对应操作。

**取得哪份 Cargo，就修改哪份库存。** `CargoAPI` 管理内容，市场价格、税率与交易规则仍由市场和子市场处理。独立货舱也可以作为奖励或计算结果，最后交给相应流程接收。

## 按物品与数量读写内容

### 补给、燃料和人员

补给与燃料有专门的增减方法，数量采用 `float`。船员与陆战队分别用 `getCrew()` 和 `getMarines()` 读取；`getTotalCrew()` 统计船员，`getTotalPersonnel()` 合计船员与陆战队。本例初始总人员为 52，下面的局部片段读取后再做一次增减：

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

### 用商品 ID 定位重型机械

普通商品通过 ID 读写，`Commodities` 提供原版常量，自有商品则用已注册 ID。`getCommodityQuantity(...)` 返回所有对应商品栈的总量，`addCommodity(...)` 与 `removeCommodity(...)` 更新它们。数量可以带小数，局部片段如下：

```java
float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
cargo.addCommodity(Commodities.HEAVY_MACHINERY, 5f);
float machineryAfterAdd = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
cargo.removeCommodity(Commodities.HEAVY_MACHINERY, 2f);
LOG.info(String.format("machinery %.1f -> %.1f -> %.1f",
        machineryBefore, machineryAfterAdd,
        cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY)));
```

日志会记录重型机械从 25 增加到 30，再减少到 28。完整脚本另做一次副本比较，将原货舱从 25 减到 20，两个片段各自展示不同操作。

移除数量超过现有数量时，现有货物可能已经被扣除。`removeCommodity(...)` 没有返回值，通用 `removeItems(...)` 返回 `false` 时也可能已经部分移除；按固定数量扣费的功能，要在扣除前确认数量满足本次需求。

### 信用使用独立的可变值

`getCredits()` 返回 `MutableValue`。`get()` 读取金额，`set()` 直接设值，`add()` 与 `subtract()` 表示增减。以下局部片段先奖励 2500，再扣除 500：

```java
MutableValue credits = cargo.getCredits();
float before = credits.get();
credits.add(2500f);
LOG.info("credits=" + credits.get() + " before=" + before);
credits.subtract(500f);
```

信用独立于货物栈。遍历物品时不会取得信用，复制与清空物品时也要单独确认金额；本节的副本实验会展示这个区别。

## 从货物栈读取类型与空间

货舱会把同类物品组成 `CargoStackAPI`。`getStacksCopy()` 返回当前栈名单的副本，下面逐栈记录类型、名称、数量与空间：

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

`CargoItemType` 的 `RESOURCES`、`WEAPONS`、`FIGHTER_CHIP`、`SPECIAL` 与 `NULL` 分别表示资源、武器、战机、特殊物品与空占位。`getData()` 的含义随类型变化：前三类使用相应 ID，特殊物品使用 `SpecialItemData`。读取商品用 `isCommodityStack()` 和 `getCommodityId()`，读取武器用 `isWeaponStack()` 和 `getWeaponSpecIfWeapon()`，实际规格按物品类型取得。

`getDisplayName()` 供界面与日志辨认，稳定引用仍使用 ID 或数据对象。`getSize()` 是数量，`getCargoSpace()` 是整栈空间，`getCargoSpacePerUnit()` 是单件空间。`isNull()` 判断空占位，它可能由玩家整理货物或转移操作留下；清理使用 `removeEmptyStacks()`，清理后重新取得栈名单。

数量还可以通过栈的 `setSize()`、`add()` 与 `subtract()` 修改。按某种商品增减时，本节采用 Cargo 层方法，让它定位对应栈；需要编辑具体一栈时才使用栈方法。**列表副本中的栈仍属于原货舱**，通过栈引用写入，原库存也会改变。

### 总容量与单栈上限

独立 Cargo 的默认总容量为 1000。`getMaxCapacity()` 读取上限，`getSpaceUsed()` 读取占用，`getSpaceLeft()` 返回剩余空间并以 0 为下限：

```java
float capacity = cargo.getMaxCapacity();
float used = cargo.getSpaceUsed();
float left = cargo.getSpaceLeft();
LOG.info(String.format("cargo space: %.1f / %.1f, left=%.1f", used, capacity, left));
```

物品空间按规格计算。本例补给与重型机械占货舱，燃料和人员的商品空间为 0，它们另有燃料与人员容量。武器、战机及特殊物品也有各自的单位空间，不能把所有数量简单相加。

要估算还能加入多少重型机械，从它的栈取得单位空间，再结合剩余容量计算。局部观察如下：

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

**直接添加货物可以造成超载。** `addCommodity(...)` 按栈规则写入，并不会按总容量截断；超载后 `getSpaceLeft()` 仍为 0，超出多少要结合已用空间与上限判断。实际奖励、转移与界面流程各自决定如何处理容量，写入后的数量与空间再从当前 Cargo 读取。

单栈上限另由 `unlimitedStacks` 控制，使用 `isUnlimitedStacks()` 读取。它决定同类货物怎样堆叠，舰队总容量则来自成员与游戏规则。

::: warning 工厂参数不切换单栈上限
当前 `createCargo(...)` 的参数为 `true` 或 `false` 时都会启用无限栈。需要使用规格中的栈上限时，显式调用 `setUnlimitedStacks(false)`。
:::

## 复制货物，比较原对象与副本

现在用 `createCopy()` 复制货物，在原货舱移除 5 重型机械。完整脚本的日志会显示原数量 20、副本数量 25；两份栈的数量已经独立。以下局部片段取得用于计算的副本：

```java
CargoAPI calculation = cargo.createCopy();
for (CargoStackAPI stack : calculation.getStacksCopy()) {
    if (stack.isCommodityStack()) {
        LOG.info("calculation stack=" + stack.getCommodityId());
    }
}
```

**`createCopy()` 复制货物数量与无限栈设置，信用和封存舰船分别处理。** 所以本例原信用为 2500，副本信用为 0。附加 data 对象也可能继续共用：商品 ID 是不可变字符串，本次数量比较可以直接使用；带可变实例数据的物品，还要按数据类型判断复制范围。

保存某次结算前的货物，可以保存这样的独立副本。市场或打捞界面继续使用实际 Cargo，跨帧操作时重新从持有者取得当前库存与栈；日志也记录持有者，几份都装着补给的货舱才不会混淆。

### 合并与清空的范围

`addAll(other)` 按栈把另一份货物加入目标，`removeAll(other)` 按栈移除对应数量。信用单独增减，`clear()` 清空普通货物栈。把打捞结果加入玩家货舱时，这些方法承担库存写入，费用、权限和结算仍由所属流程完成；市场库存同样不能仅靠合并代替交易。

Cargo 中还可以保存封存舰船。使用前调用 `initMothballedShips(factionId)`，再从 `getMothballedShips()` 取得 `FleetDataAPI`，通过 [C11-S2] 的成员 API 操作舰船。它们与货物栈分别维护，`clear()` 清空货物时仍保留舰船与信用。需要同时清理时，对每组实际对象分别处理。

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

独立货舱中的补给、燃料、人员与重型机械通过对应方法写入，信用单独增减，栈提供数量、类型与空间。复制后的物品数量独立，信用、舰船与附加数据按各自范围处理。

实际功能从所属舰队、子市场或打捞流程取得 Cargo，库存修改才会落到正确对象。数量、容量与结算也分别确认，这套读写方法才能用于完整的奖励或交易。

## 建议参考项

- `com.fs.starfarer.api.campaign.CargoAPI`：货舱内容、容量、复制和合并；
- `com.fs.starfarer.api.campaign.CargoStackAPI`：货舱栈的类型、数量、空间和关联数据；
- `com.fs.starfarer.api.util.MutableValue`：信用等可变数值的读写；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：封存舰船集合；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：封存舰船对象；
- `com.fs.starfarer.api.impl.campaign.ids.Commodities`：原版商品 ID 常量。
