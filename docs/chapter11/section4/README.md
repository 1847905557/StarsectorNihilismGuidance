# 读取并修改舰船装配

本节给一艘狼更换武器、调整武器组与幅能，再将改装结果保留到存档中。操作从成员当前装配的副本开始，说明装备写入、分组和 OP 怎样共同决定结果；随后借窑级装配处理模块关系，并用 JSON 观察装配的数据表示。

_本节预期阅读时间：\~30 分钟。_

_本节预期操作时间：\~40 分钟。_

## 学习目标

完成后，您将能够：

- 从 `FleetMemberAPI` 取得当前装配并读取 `VariantSource`、船体和文件来源；
- 使用 `clone()` 建立隔离副本，修改武器槽、武器组、船插和幅能配置；
- 通过 `setVariant(...)` 将副本应用到成员，并观察 `stats` 与装配结果；
- 按模块槽读取父子装配关系，判断模块覆盖是否存在；
- 使用 `toJSONObject()` 检查序列化边界，并定位共享引用、槽位和 OP 相关故障。

## 前置知识

- [C4-S4] 的窑级主体、模块与 `GearIn_kiln_combat` 装配；
- [C11-S3] 的成员 `stats` 更新和派生值读取；
- [C11-S3] 的 `FleetMemberAPI` 成员归属与可部署状态；
- Java 对象引用、集合遍历、异常处理和日志输出；
- 能够打开舰队界面观察装配，并在 `starsector.log` 搜索固定前缀。

## 准备这次改装

创建 `src/data/scripts/gearin/campaign/GearInVariantRefitter.java` 与 `GearInModuleInspector.java`。前者把改装狼留在玩家舰队，后者读取 [C4-S4] 已定义的窑级模块装配。在 `GearInModPlugin` 添加两类的 `import`，向已有的 `onNewGameAfterTimePass(...)` 追加登记，沿用 `sector`：

```java
sector.addTransientScript(new GearInVariantRefitter());
sector.addTransientScript(new GearInModuleInspector());
```

两份脚本在新档推进后各执行一次。先处理狼，从成员取得它正在使用的 `ShipVariantAPI`。装配保存武器、武器组、船插、幅能数量、战机、模块映射与来源，船体规格则提供槽位和基础限制。以下局部片段记录当前装配：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");
ShipVariantAPI variant = member.getVariant();

String hullVariantId = variant.getHullVariantId();
String hullId = variant.getHullSpec().getHullId();
VariantSource source = variant.getSource();
String filePath = variant.getVariantFilePath();

LOG.info("variant read: hull=" + hullId
        + " variant=" + hullVariantId
        + " source=" + source
        + " file=" + filePath
        + " vents=" + variant.getNumFluxVents()
        + " capacitors=" + variant.getNumFluxCapacitors());
```

狼按 `wolf_Assault` 创建，`getHullVariantId()` 返回装配 ID，`getVariantFilePath()` 保留文件来源，`getSource()` 表示当前来源类别。`VariantSource` 有 `STOCK`、`MISSION_DESIGN`、`MISSION_SAVE`、`REFIT` 与 `HULL`，本节要从原版预设装配得到运行时改装结果。

## 建立副本，并让改装随成员保存

成员创建时可以从规格库取得设计，成员当前装配也可能已经有玩家的改动。我们从这个当前对象 `clone()`，在副本上集中修改，完成后一次写回。克隆会复制武器映射、武器组、普通与永久船插、受抑制船插、战机、模块映射和标签；船体规格继续共用。

**副本还要设置 `REFIT` 来源。** 克隆保留原来源，`STOCK` 装配在读档时按规格库 ID 恢复，`REFIT` 改装则随成员保存。以下局部片段同时确认对象独立与来源变化：

```java
ShipVariantAPI original = member.getVariant();
ShipVariantAPI copy = original.clone();
copy.setSource(VariantSource.REFIT);

boolean isolated = copy != original;
String originalSource = original.getSource().name();
String copiedSource = copy.getSource().name();

LOG.info("variant clone: isolated=" + isolated
        + " originalSource=" + originalSource
        + " copiedSource=" + copiedSource
        + " originalGroups=" + original.getWeaponGroups().size()
        + " copiedGroups=" + copy.getWeaponGroups().size());
```

编者建议将保存、读档作为这类运行时改装的验收环节。`clone()` 已经成功、界面也显示了新武器，仍不能证明这份装备会留在存档中；来源保持 `STOCK` 时，问题恰好要到读档才出现。

写回后，成员持有的就是 `copy`。随后再修改它，会直接改变成员当前装配；要将设计用于另一艘舰船，再为那艘成员建立独立副本。

## 为狼更换装备

### 用点防激光替换离子炮

狼的 `WS 001` 是小型能量槽，`wolf_Assault` 在其中安装了离子炮。本例用 `pdlaser` 点防激光替换它：`addWeapon(slotId, weaponId)` 写入武器，`getWeaponId(slotId)` 读取当前武器 ID，`clearSlot(slotId)` 则清空槽位。局部片段如下：

```java
String slotId = "WS 001";
String weaponBefore = copy.getWeaponId(slotId);

copy.addWeapon(
        slotId,
        "pdlaser");

String weaponAfter = copy.getWeaponId(slotId);
WeaponSpecAPI weaponSpec = copy.getWeaponSpec(slotId);

LOG.info("weapon slot: slot=" + slotId
        + " before=" + weaponBefore
        + " after=" + weaponAfter
        + " spec=" + (weaponSpec == null ? "null" : weaponSpec.getWeaponId()));
```

这里两个参数来自不同定义：槽位 ID 来自船体，武器 ID 来自武器规格。两者都存在，还要满足尺寸与类型的适配。`getNonBuiltInWeaponSlots()` 返回实际装有武器的非内置槽，`getFittedWeaponSlots()` 返回当前已安装的槽集合，可用于确认本次写入的位置。

::: warning 写入武器前核对槽位规格
`addWeapon(...)` 写入映射，读回 ID 只证明映射已改变。通过 `getSlot(slotId)` 核对尺寸与类型，再用 `getWeaponSpec(slotId)` 读取武器规格，才能确认适配。
:::

### 安排武器组

武器写入槽位之后，分组还要单独处理。组里保存槽位 ID，通过 `getWeaponGroups()` 取得 `WeaponGroupSpec`，再读取类型、槽位与默认自动开火。本例用 `autoGenerateWeaponGroups()` 按当前装备重建，局部日志记录生成结果：

```java
copy.autoGenerateWeaponGroups();
List<WeaponGroupSpec> groups = copy.getWeaponGroups();

for (int i = 0; i < groups.size(); i++) {
    WeaponGroupSpec group = groups.get(i);
    LOG.info("weapon group: index=" + i
            + " type=" + group.getType()
            + " autofire=" + group.isAutofireOnByDefault()
            + " slots=" + group.getSlots());
}
```

自动生成会清除原分组，再按武器与槽位特征重新组织，组模式用 `WeaponGroupType` 表示。完整例还切换第一组的默认自动开火，用于展示 `setAutofireOnByDefault(...)` 的写入结果。

**重建分组意味着接受原安排被替换。** 本节使用新创建的狼，可以由脚本决定分组；修改玩家已有的装配时，原分组本身也是玩家的设计，需要保留就沿现有组调整槽位。读取分组直接调用 getter，自动生成是一项写入操作。

### 武器组字段速查表

| 要观察的对象 | 读取入口                     | 观察含义                 |
| ------------ | ---------------------------- | ------------------------ |
| 槽位中的武器 | `getWeaponId(slotId)`        | 当前槽位的武器 ID        |
| 已安装槽位   | `getFittedWeaponSlots()`     | 包含实际武器的槽位集合   |
| 武器组       | `getWeaponGroups()`          | 当前分组对象集合         |
| 分组槽位     | `WeaponGroupSpec.getSlots()` | 该组引用的槽位 ID        |
| 分组模式     | `WeaponGroupSpec.getType()`  | 联动或交替等组行为       |
| 默认自动开火 | `isAutofireOnByDefault()`    | 进入战斗时的组开火默认值 |

### 调整船插与幅能，核对 OP

离子炮换成点防激光后腾出 OP，本例增加一散热和一电容，同时用移除、添加 `fluxdistributor` 展示船插集合操作。`addMod(...)` 与 `removeMod(...)` 使用船插 ID，`setNumFluxVents(...)` 与 `setNumFluxCapacitors(...)` 直接设置数量：

```java
boolean hadFluxDistributor = copy.hasHullMod("fluxdistributor");
int ventsBefore = copy.getNumFluxVents();
int capacitorsBefore = copy.getNumFluxCapacitors();

copy.removeMod("fluxdistributor");
copy.addMod("fluxdistributor");
copy.setNumFluxVents(ventsBefore + 1);
copy.setNumFluxCapacitors(capacitorsBefore + 1);

LOG.info("variant resources: hadFluxDistributor=" + hadFluxDistributor
        + " hullMods=" + copy.getNonBuiltInHullmods()
        + " vents=" + ventsBefore + "->" + copy.getNumFluxVents()
        + " capacitors=" + capacitorsBefore + "->"
        + copy.getNumFluxCapacitors());
```

`getHullMods()` 读取装配中的船插集合，`getNonBuiltInHullmods()` 筛出普通可拆卸部分；永久船插与受抑制船插各有专门入口，清空普通集合不能代替这些操作。幅能数量还受船体限制与军械点数约束，setter 写入成功并不保证设计合法。

应用前记录成本。`computeOPCost(null)` 返回总 OP，`getUnusedOP(null)` 返回剩余点数，需要区分武器与船插成本时再读取对应成本方法：

```java
int opCost = copy.computeOPCost(null);
int unusedOp = copy.getUnusedOP(null);
LOG.info("variant OP: cost=" + opCost + " unused=" + unusedOp);
```

## 把完成的改装写回成员

武器、武器组、船插与幅能全部调整后，将 `copy` 写回成员。沿 [C11-S3] 的重算方式调用 `setVariant(..., false, true)`，再从成员读取当前装配与派生结果：

```java
member.setVariant(
        copy,
        false,
        true);
member.updateStats();

ShipVariantAPI applied = member.getVariant();
LOG.info("variant applied: sameReference=" + (applied == copy)
        + " source=" + applied.getSource()
        + " vents=" + applied.getNumFluxVents()
        + " capacitors=" + applied.getNumFluxCapacitors()
        + " memberStrength=" + member.getMemberStrength()
        + " deployable=" + member.canBeDeployedForCombat());
```

`withRefit` 决定是否按装配差异处理 "field refit" CR 事件，`withStatsUpdate` 决定是否立即更新成员数值。这里传入 `false, true`，保留改装结果并重建 `stats`；采用 `withRefit=true` 时，CR 前后值也纳入观察。写回后的 `getVariant()` 就是当前副本，`getStats()` 与 `canBeDeployedForCombat()` 则读取更新后的结果。

本例让改装狼留在玩家舰队。可逆实验可以保存 `original`，结束后将它写回；原装配与改装副本各自保留，恢复时才能取得正确对象。

## 沿父装配读取与修改模块

窑级的 `GearIn_kiln_combat` 在 `MODULE_0` 上使用 `GearIn_kiln_module_combat`。这些关系已经在 [C4-S4] 的主体装配定义，现在从加载后的对象读取：`getModuleSlots()` 提供模块槽，`getStationModules()` 提供槽位到装配 ID 的映射，`getModuleVariant(slotId)` 取得当前模块装配。以下是局部观察片段：

```java
List<String> moduleSlots = variant.getModuleSlots();
Map<String, String> moduleIds = variant.getStationModules();

for (String slotId : moduleSlots) {
    ShipVariantAPI module = variant.getModuleVariant(slotId);
    String moduleId = module == null
            ? "<none>"
            : module.getHullVariantId();
    LOG.info("module relation: slot=" + slotId
            + " mapId=" + moduleIds.get(slotId)
            + " variant=" + moduleId
            + " source=" + (module == null ? "<none>" : module.getSource()));
}
```

模块槽没有有效映射时，`getModuleVariant(...)` 可以返回 `null`；有默认映射时，也可能直接返回规格库中的模块装配。**克隆父装配不等于所有默认模块都已成为独立副本。** 父装配会递归克隆已有的模块覆盖对象，默认的装配 ID 映射仍可继续引用规格库。

因此修改某个模块时，从 `getModuleVariant(slotId)` 取得当前模块，另行克隆并设置 `REFIT`，再用父装配的 `setModuleVariant(slotId, moduleCopy)` 写回。槽位先按父装配核对，写回后重新读取模块与映射；单独修改一个模块副本，还没有改变父装配所用的模块。已经交给其他成员的模块装配也保持自己的引用关系。

### 模块关系速查表

| 模块关系      | 入口                                | 返回结果             |
| ------------- | ----------------------------------- | -------------------- |
| 可用模块槽    | `getModuleSlots()`                  | 父装配定义的槽位 ID  |
| 当前模块      | `getModuleVariant(slotId)`          | 模块装配或 `null`    |
| 保存的模块 ID | `getStationModules()`               | 槽位到装配 ID 的映射 |
| 写入模块      | `setModuleVariant(slotId, variant)` | 更新父装配的模块关系 |
| 模块来源      | `module.getSource()`                | 该模块装配自身的来源 |

## 用 JSON 观察装配数据

装配还可以通过 `toJSONObject()` 转为 JSON。返回对象包含显示名、船体与装配 ID、幅能数量、武器组、船插，以及适用的战机和模块映射，适合核对数据文件中的字段。以下局部片段记录返回结果：

```java
try {
    JSONObject json = member.getVariant().toJSONObject();
    LOG.info("variant json: length=" + json.toString().length()
            + " source=" + member.getVariant().getSource()
            + " variant=" + member.getVariant().getHullVariantId());
} catch (JSONException ex) {
    LOG.error("GearIn variant JSON failed", ex);
}
```

`weaponGroups` 记录当前组模式、自动开火与槽位武器，`modules` 保存模块 ID 映射。这个 JSON 并没有把运行时模块副本的所有内部状态嵌入其中，成员存档与 `.variant` 数据文件也采用各自的保存方式。

调用只返回 `JSONObject`。要制作正式 `.variant`，仍按 [C4-S1] 的装配导出与文件登记流程保存，然后重新加载验证；这里的日志用于观察字段。

## 完整最小例

两个类保存到前述路径，分别提供成员改装与父子装配观察。新档入口执行一轮，改装后的成员继续留在玩家舰队。

::: details 完整成品：GearInVariantRefitter

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponGroupSpec;

/**
 * 克隆一份装配，修改武器、武器组、船插和幅能后应用到演示成员。
 */
public class GearInVariantRefitter implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_VariantRefitter";

    private static final Logger LOG = Global.getLogger(GearInVariantRefitter.class);

    private static final String SLOT_ID = "WS 001";
    private static final String VARIANT_ID = "wolf_Assault";

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
        done = true;

        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                VARIANT_ID);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        ShipVariantAPI original = member.getVariant();
        ShipVariantAPI refit = original.clone();
        refit.setSource(VariantSource.REFIT);

        String weaponBefore = refit.getWeaponId(SLOT_ID);
        int ventsBefore = refit.getNumFluxVents();
        int capacitorsBefore = refit.getNumFluxCapacitors();

        refit.addWeapon(
                SLOT_ID,
                "pdlaser");
        refit.autoGenerateWeaponGroups();

        if (!refit.getWeaponGroups().isEmpty()) {
            WeaponGroupSpec firstGroup = refit.getWeaponGroups().get(0);
            firstGroup.setAutofireOnByDefault(
                    !firstGroup.isAutofireOnByDefault());
        }

        refit.removeMod("fluxdistributor");
        refit.addMod("fluxdistributor");
        refit.setNumFluxVents(ventsBefore + 1);
        refit.setNumFluxCapacitors(capacitorsBefore + 1);

        member.setVariant(
                refit,
                false,
                true);
        member.updateStats();

        LOG.info("GearIn variant refitter: id=" + member.getId()
                + " hull=" + member.getHullId()
                + " source=" + refit.getSource()
                + " weapon=" + weaponBefore + "->" + refit.getWeaponId(SLOT_ID)
                + " vents=" + ventsBefore + "->" + refit.getNumFluxVents()
                + " capacitors=" + capacitorsBefore + "->"
                + refit.getNumFluxCapacitors()
                + " groups=" + refit.getWeaponGroups().size()
                + " op=" + refit.computeOPCost(null)
                + " deployable=" + member.canBeDeployedForCombat());
    }
}
```

:::

::: details 完整成品：GearInModuleInspector

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 读取父装配的模块关系、来源和 JSON 序列化结果。
 */
public class GearInModuleInspector implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_ModuleInspector";

    private static final Logger LOG = Global.getLogger(GearInModuleInspector.class);

    private static final String VARIANT_ID = "GearIn_kiln_combat";

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
        done = true;

        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                VARIANT_ID);
        ShipVariantAPI variant = member.getVariant();

        LOG.info("GearIn module inspector: variant="
                + variant.getHullVariantId()
                + " source=" + variant.getSource()
                + " moduleSlots=" + variant.getModuleSlots().size()
                + " stationModules=" + variant.getStationModules().size());

        for (String slotId : variant.getModuleSlots()) {
            ShipVariantAPI module = variant.getModuleVariant(slotId);
            LOG.info("GearIn module: slot=" + slotId
                    + " variant=" + (module == null ? "null" : module.getHullVariantId())
                    + " source=" + (module == null ? "null" : module.getSource()));
        }

        try {
            JSONObject json = variant.toJSONObject();
            LOG.info("GearIn module JSON: length=" + json.toString().length()
                    + " hasWeaponGroups=" + json.has("weaponGroups")
                    + " hasModules=" + json.has("modules"));
        } catch (JSONException ex) {
            LOG.error("GearIn module JSON failed", ex);
        }
    }
}
```

:::

改装脚本结束后，狼留在玩家舰队；模块脚本只读取窑级装配与 JSON。两类分别使用自己的对象，结果由下面的界面、日志与读档步骤确认。

## 验证方案

前置数据：[C4-S4] 的窑级主体与模块数据、贴图和两份装配已经就位；本节登记使用新档推进后的入口。

1. 构建 MOD，确认 JAR 中存在 `GearInVariantRefitter.class` 和 `GearInModuleInspector.class`。
2. 新建存档，等待入口执行后搜索 `GearIn variant refitter`，记录成员 ID、槽位武器、幅能、分组、OP 与 `REFIT` 来源。
3. 打开舰队界面，按成员 ID 定位演示成员，核对武器、武器组、船插、散热和电容；重新取得装配确认应用结果。
4. 搜索同一轮执行的 `GearIn module inspector`、`GearIn module` 和 `GearIn module JSON`，记录模块槽、模块来源和 JSON 长度。
5. 与 [C11-S3] 保留的未改装狼比较 `WS 001` 与幅能数量，确认两艘拥有各自装配；保存读档后再查看改装狼。
6. 核对 MOD 的 `data/variants/`，确认本节 JSON 观察返回数据对象，目录中的正式装配仍是已保存的数据文件。

预期结果：

- 改装类日志显示原槽位武器与新武器 ID、幅能变化和重新生成后的武器组；
- 改装狼加入玩家舰队，来源为 `REFIT`，界面显示点防激光、幅能与武器组，读档后改装结果仍保留；
- 模块日志读取 `GearIn_kiln_combat` 的 `MODULE_0` 与 `GearIn_kiln_module_combat`，JSON 包含武器组与模块映射；
- 未改装成员和规格库对象保持原值；
- 没有产生跨脚本共享装配引用或未经请求的 `.variant` 文件。

## 故障分析

- **另一艘舰船也出现同样改装**：直接修改了规格库或其他成员共用的装配对象。
    - **解决方案**：始终从目标成员的 `getVariant()` 创建 `clone()`，只把副本交给该成员，并在日志中确认引用关系。

- **武器槽读取为空**：槽位 ID 不属于当前船体，或武器 ID 与槽位类型不匹配。
    - **解决方案**：先读取 `getSlot(slotId)`、`getWeaponSpec(slotId)` 和 `getWeaponId(slotId)`，确认槽位存在、武器规格可解析且尺寸与类型匹配。

- **武器已经安装但武器组没有变化**：只调用了 `addWeapon(...)`，保留的旧分组没有引用新槽位，或自动生成时机错误。
    - **解决方案**：武器写入完成后选择 `autoGenerateWeaponGroups()`，再读取每个 `WeaponGroupSpec.getSlots()`，必要时重新建立分组。

- **武器组顺序或自动开火状态异常**：无条件重建分组覆盖了原有手动分组，或只修改了一个分组而没有观察其他分组。
    - **解决方案**：先记录组数量、类型、槽位和自动开火标志；只在明确需要重建时调用自动生成，并逐组验证。

- **船插看似写入但 OP 超限**：装配集合发生变化，但 `computeOPCost(...)` 已超过船体可用军械点数。
    - **解决方案**：在应用前记录 `computeOPCost(...)` 和 `getUnusedOP(...)`，将幅能数量和船插调整到合法范围，再调用 `setVariant(copy, false, true)` 触发成员 `stats` 更新。

- **成员装配改变但舰队界面仍显示旧数值**：`setVariant(...)` 的 `withStatsUpdate` 为 `false`，或读取了更新前缓存的 `stats`。
    - **解决方案**：写回时传入 `withStatsUpdate=true`，随后调用 `member.updateStats()`，重新取得 `getStats()`、`getMemberStrength()` 和 `canBeDeployedForCombat()`。

- **模块检查没有返回模块**：父装配没有模块槽、槽位 ID 不属于该父装配，或模块映射只保存了默认来源。
    - **解决方案**：先遍历 `getModuleSlots()`，再对每个槽位调用 `getModuleVariant(...)`，同时记录 `getStationModules()` 中的映射值；父装配为空时保留 `null` 结果。

- **模块修改没有进入父装配**：只修改了模块副本，没有调用父装配的 `setModuleVariant(...)`。
    - **解决方案**：确认槽位属于父装配，并显式写回 `setModuleVariant(slotId, moduleCopy)`；随后重新读取父装配映射。

- **来源值被错误覆盖**：为了让装配可保存而随意调用 `setSource(...)`，导致来源语义与数据流不一致。
    - **解决方案**：只在明确的装配生命周期边界设置 `VariantSource`，记录修改前后来源；来源枚举不代替正式文件保存流程。

- **JSON 日志存在但没有保存文件**：把 `toJSONObject()` 的返回值当成文件写入结果。
    - **解决方案**：把 JSON 仅用于日志和字段核对；正式保存时使用游戏装配数据流程，并重新加载文件验证。

- **脚本重复产生成员**：一次性脚本没有持久标记，或每次启动都把同一示例注册为新实例。
    - **解决方案**：使用实例级 `done` 防止重复执行，并在正式 MOD 中用专用 Memory 记录演示成员 ID，执行前先检查已有成员。

## 总结

这艘狼的改装从独立副本开始，在槽位、分组与 OP 之间完成配置，再写回成员并重算。`REFIT` 来源使运行时设计随成员保存，界面与读档共同确认这份改装。

模块仍通过父装配选择，修改时取得当前模块副本再显式写回。JSON 便于观察数据字段，成员存档和正式装配文件则各有保存职责。对象独立、装备适配与保存来源都成立，改装才算完成。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配读取、克隆、武器、船插、幅能、模块和 JSON；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：装配应用、`stats` 更新和部署判断；
- `com.fs.starfarer.api.loading.VariantSource`：装配来源枚举；
- `com.fs.starfarer.api.loading.WeaponGroupSpec`：武器组类型、槽位和自动开火默认值；
- `com.fs.starfarer.api.loading.WeaponSlotAPI`：槽位类型、尺寸和适配检查；
- `data/variants/`：原版装配文件和字段示例；
- `data/weapons/`：原版武器规格和武器 ID；
- `Starsector/starsector-core/starsector.log`：核对装配读取、应用和 JSON 日志。
