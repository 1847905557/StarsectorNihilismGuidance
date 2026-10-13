# 读取并修改舰船装配

成员状态已经能够读取，这次实际改装一艘狼。先从成员取得当前装配，克隆后把一个小型能量槽改为点防激光，再调整武器组、船插与幅能，写回同一个成员。另用已有窑级装配查看模块与 JSON，把成员当前装备、父子关系和正式数据文件分别核对。

*本节预期阅读时间：\~30 分钟。*

*本节预期操作时间：\~40 分钟。*

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
- [C11-S4] 的 `FleetMemberAPI` 成员归属与可部署状态；
- Java 对象引用、集合遍历、异常处理和日志输出；
- 能够打开舰队界面观察装配，并在 `starsector.log` 搜索固定前缀。

## 登记改装与模块观察

创建 `src/data/scripts/gearin/campaign/GearInVariantRefitter.java` 和 `GearInModuleInspector.java`，添加到 `GearInModPlugin` 的 `import`。向 [C11-S3] 已有的 `onNewGameAfterTimePass(...)` 追加局部登记，沿用 `sector`：

```java
sector.addTransientScript(new GearInVariantRefitter());
sector.addTransientScript(new GearInModuleInspector());
```

改装类将狼加入玩家舰队，保留结果供界面核对。模块类以窑级主体装配建立观察对象，读取它已定义的模块槽。

**从目标成员取得当前装配，再建立独立副本。** `FleetMemberAPI.getVariant()` 返回成员当前正在使用的装配。这个对象同时保存船体规格、武器槽映射、船插集合、武器组、幅能电容与散热数量、模块映射和来源枚举。创建成员后立即读取时，装配可能来自规格库；需要人物与舰队上下文时，加入后再更新 `stats`。

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

`VariantSource` 表示来源，装配 ID 仍由 `getHullVariantId()` 读取。五种枚举为 `STOCK`、`MISSION_DESIGN`、`MISSION_SAVE`、`REFIT` 和 `HULL`。当前运行时改装副本设置为 `REFIT`，让修改后的装备作为成员装配保存；`STOCK` 装配按规格库 ID 恢复。

这也解释了为什么克隆后还要处理来源：复制提供独立对象，`REFIT` 表达当前改装的保存语义，两步各有作用。

::: tip 先确认对象来源，再决定写回方式
成员返回的装配可能属于规格库、任务数据或改装结果。修改前记录 `getSource()` 和 `getVariantFilePath()`，写回后重新读取这两个值，才能判断修改发生在目标成员上。
:::

## 建立隔离副本

`ShipVariantAPI.clone()` 会复制武器映射、武器组、船插集合、永久船插、受抑制船插、战机列表、模块映射和标签集合。副本仍然引用同一艘船体规格，但对装配字段的修改不会回写原始装配。

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

副本应用到成员后，成员会持有这份副本的引用。此后继续修改 `copy` 会直接影响成员，因此完成改装后应把副本视为成员的当前装配，不能再把它当作可复用的模板对象。

## 修改武器槽位

`addWeapon(slotId, weaponId)` 把指定武器写入指定槽位，`getWeaponId(slotId)` 读取槽位当前的武器 ID，`clearSlot(slotId)` 清空槽位。槽位 ID 来自船体规格，武器 ID 来自武器规格；两者不匹配时，装配可能无法应用或在界面中显示空槽。

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

`getNonBuiltInWeaponSlots()` 只返回实际装有武器的非内置槽位，`getFittedWeaponSlots()` 返回当前装配的已安装槽集合。读取这两个集合可以区分空槽、内置武器和改装脚本写入的槽位。

::: warning 写入武器前核对槽位规格
武器 ID、武器尺寸和武器类型都必须适配目标槽位。先通过 `getSlot(slotId)` 取得槽位，再用 `getWeaponSpec(slotId)` 观察写入结果；日志显示 ID 不代表该武器已经有效安装。
:::

## 重新生成和读取武器组

武器安装与武器组是两层数据。`autoGenerateWeaponGroups()` 根据当前武器集合生成分组；`getWeaponGroups()` 返回分组对象，`WeaponGroupSpec.getSlots()` 返回该组引用的槽位。组模式由 `WeaponGroupType` 表示，自动开火默认值由 `isAutofireOnByDefault()` 表示。

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

本例修改武器后重建分组，再切换第一组的默认自动开火。自动生成会按武器与槽位相似度重新组织，原手动顺序也可能随之变化。**分组写入按当前设计选择，读后无需自动重建。** 保留玩家分组时，按现有组调整目标槽位；重建时则逐组核对类型、槽位与默认开火。

### 武器组字段速查表

| 要观察的对象 | 读取入口 | 观察含义 |
| --- | --- | --- |
| 槽位中的武器 | `getWeaponId(slotId)` | 当前槽位的武器 ID |
| 已安装槽位 | `getFittedWeaponSlots()` | 包含实际武器的槽位集合 |
| 武器组 | `getWeaponGroups()` | 当前分组对象集合 |
| 分组槽位 | `WeaponGroupSpec.getSlots()` | 该组引用的槽位 ID |
| 分组模式 | `WeaponGroupSpec.getType()` | 联动或交替等组行为 |
| 默认自动开火 | `isAutofireOnByDefault()` | 进入战斗时的组开火默认值 |

## 修改船插和幅能分配

`addMod(modId)`、`removeMod(modId)` 修改普通船插集合；`getHullMods()` 读取包含普通船插的集合，`getNonBuiltInHullmods()` 可用于观察非内置部分。永久船插和受抑制船插有独立入口，不要用普通集合的清空操作代替它们。

`setNumFluxVents(...)` 与 `setNumFluxCapacitors(...)` 直接设置幅能散热和电容数量。数值是否允许由船体和军械点数共同决定；装配对象可以暂时保存超出 OP 的值，但应用到成员或进入改装界面时会出现校验或回退。

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

本例保留改装结果，并记录 OP。需要可逆实验时保存原装配引用，结束后写回；需要观察超限时，则同时记录总成本、船插成本与剩余 OP。当前狼的 `WS 001` 是小型能量槽，原装离子炮换成点防激光后，腾出的点数用于增加一散热和一电容。

```java
int opCost = copy.computeOPCost(null);
int unusedOp = copy.getUnusedOP(null);
LOG.info("variant OP: cost=" + opCost + " unused=" + unusedOp);
```

## 把副本应用到成员

`FleetMemberAPI.setVariant(variant, withRefit, withStatsUpdate)` 把调用方提供的装配引用写入成员。`withRefit` 控制是否按改装差异处理 CR 影响，`withStatsUpdate` 控制是否立即调用成员 `stats` 更新。改装结果需要进入舰队界面和部署判断时，应在写回后重新读取成员的 `getVariant()`、`getStats()` 和 `canBeDeployedForCombat()`。

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

`setVariant(..., false, true)` 适合本节的可逆观察，因为它不会把改装差异作为一次 "field refit" CR 事件处理，但会重建成员 `stats`。若使用 `withRefit=true`，应同时记录 CR 前后值，并按 [C11-S3] 的 `stats` 重算规则观察派生结果。

::: tip 应用后的副本就是成员当前装配
写回后不要继续修改 `original`，也不要把 `copy` 当作多个成员共用的模板。每艘演示成员都从自己的当前装配重新 `clone()`，才能避免连带修改。
:::

## 读取模块和父子关系

带模块槽的父装配通过 `getModuleSlots()` 返回槽位 ID；`getModuleVariant(slotId)` 返回该槽位当前的模块装配，可能为 `null`。`getStationModules()` 返回槽位到模块装配 ID 的映射。`setModuleVariant(slotId, moduleVariant)` 只应在槽位属于当前父装配时调用。

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

窑级主体定义了 `MODULE_0`，映射到 `GearIn_kiln_module_combat`。父装配的 `clone()` 会递归复制已存在的模块装配。修改父装配副本不会自动改变已经单独应用到其他成员的模块；反过来，修改一个模块副本也不会替换父装配映射，除非显式调用 `setModuleVariant(...)`。

### 模块关系速查表

| 模块关系 | 入口 | 返回结果 |
| --- | --- | --- |
| 可用模块槽 | `getModuleSlots()` | 父装配定义的槽位 ID |
| 当前模块 | `getModuleVariant(slotId)` | 模块装配或 `null` |
| 保存的模块 ID | `getStationModules()` | 槽位到装配 ID 的映射 |
| 写入模块 | `setModuleVariant(slotId, variant)` | 更新父装配的模块关系 |
| 模块来源 | `module.getSource()` | 该模块装配自身的来源 |

## 观察来源和 JSON 序列化

`toJSONObject()` 把装配的显示名、船体 ID、装配 ID、幅能数量、武器组、模块映射和战机等字段写入一个 `JSONObject`。它用于调试、日志和保存前检查，调用本身不会创建 `.variant` 文件，也不会把结果登记到规格库。

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

序列化结果中的 `weaponGroups` 是当前分组快照，`modules` 是父装配当前的模块 ID 映射。需要正式保存时，应使用游戏装配数据流程生成目标文件，并重新加载验证；不要把日志中的 JSON 文本直接当作已保存装配。

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

改装结果从成员当前装配重新读取，模块结果沿父装配的槽位与映射读取。JSON 返回当前字段快照，正式 `.variant` 文件仍按数据文件流程保存与登记。

## 验证方案

前置数据：[C4-S4] 的窑级主体与模块数据、贴图和两份装配已经就位；本节登记使用新档推进后的入口。

1. 构建 MOD，确认 JAR 中存在 `GearInVariantRefitter.class` 和 `GearInModuleInspector.class`。
2. 新建存档，等待入口执行后搜索 `GearIn variant refitter`，记录成员 ID、槽位武器、幅能、分组、OP 与 `REFIT` 来源。
3. 打开舰队界面，按成员 ID 定位演示成员，核对武器、武器组、船插、散热和电容；重新取得装配确认应用结果。
4. 运行 `GearInModuleInspector`，搜索 `GearIn module inspector`、`GearIn module` 和 `GearIn module JSON`，记录模块槽、模块来源和 JSON 长度。
5. 与 [C11-S4] 保留的未改装狼比较 `WS 001` 与幅能数量，确认两艘拥有各自装配；保存读档后再查看改装狼。
6. 在不写入文件的情况下重新载入存档，确认 JSON 日志只用于观察，正式装配文件没有被脚本意外生成。

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

改装狼从自身装配克隆副本，设置 `REFIT` 来源，再完成武器、分组、船插与幅能修改，写回成员并刷新数值。成员加入舰队后，界面与读档都能继续观察同一份改装。

窑级装配则提供实际父子关系，模块副本通过父装配入口写回，JSON 用于查看当前字段。编者建议把对象独立性、来源、槽位与 OP 一起确认；改装能够显示、能够保存、能够正确部署，各自有明确的观察位置。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配读取、克隆、武器、船插、幅能、模块和 JSON；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：装配应用、`stats` 更新和部署判断；
- `com.fs.starfarer.api.loading.VariantSource`：装配来源枚举；
- `com.fs.starfarer.api.loading.WeaponGroupSpec`：武器组类型、槽位和自动开火默认值；
- `com.fs.starfarer.api.loading.WeaponSlotAPI`：槽位类型、尺寸和适配检查；
- `data/variants/`：原版装配文件和字段示例；
- `data/weapons/`：原版武器规格和武器 ID；
- `Starsector/starsector-core/starsector.log`：核对装配读取、应用和 JSON 日志。
