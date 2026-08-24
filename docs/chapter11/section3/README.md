# 读取与修改舰船变体

本节使用 `ShipVariantAPI` 读取、克隆并修改舰船变体。变体连接装配数据与存档，修改前需要明确对象来源以及哪些变化会被序列化保存。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 从注册表读取变体，并区分 `getVariant` 与 `FleetMemberAPI` 两条路径的失败分界；
- 解释 `clone()` 的深拷贝语义与来源保留，并验证修改克隆不污染注册表变体；
- 用 API 修改装配 (武器、武器组、船插、幅能、模块覆盖)，并判断每类操作的失败时机；
- 区分 `VariantSource` 五个来源，验证 `isStockVariant()` 与 `isEmptyHullVariant()` 的判定公式；
- 核对变体序列化与保存的边界：`toJSONObject` 键、克隆落盘、成员变体落档条件；
- 创建并注册一套七个类的变体演示套件，在生涯首帧核对全部 `GearIn ...` 日志；
- 从症状定位典型故障：装配不出现、启动加载失败、部署期槽位报错、共享污染、来源语义走错、内置内容改不动、战机武器无目标。

## 前置知识

- [C1-S6] 的舰船船体、`.ship` 文件、内置三件套与装配构建期和部署期错误边界；
- [C2-S2] 的船插、`hull_mods.csv` 与效果类实例；
- [C4-S1] 的战役装配结构、字段要求与任务导出流程；
- [C4-S4] 的模块船、`modules` 字段加载与部署期实体；
- [C6-S2] 的 `GearInModPlugin` 所有权与 `onGameLoad` 回调；
- [C9-S1] 的生涯每帧脚本注册；
- [C9-S3] 的 `FleetMemberAPI.getVariant()`、`setVariant(...)` 与装配引用边界。

## 术语

按术语约定，本节首次完整讲解两个词：

- **变体 (variant)**：API 名保留 `ShipVariantAPI`。一次装配的完整定义：船体 (hull)＋武器＋武器组＋船插＋幅能分配 (vents/capacitors)＋联队＋模块引用，外加一个注册表 id(`hullVariantId`) 与一个来源 (`VariantSource`)。
- **装配 (fit)**：变体的实际内容 —— 武器装在哪、船插装哪些、幅能怎么分、武器怎么分组。本节 "修改装配" 即修改这些字段。

## 变体从哪里来：注册与读取

### 变体注册入口速查表

变体有四个注册入口，对应四种来源：

| 入口 | 路径 | 来源 | 使用方 |
|---|---|---|---|
| 空装配自动生成 | `ship_data.csv` 每个注册行 | `HULL` | 按 hull 自动生成 `<hullId>_Hull` 空装配并注册 (无武器槽 displayName "Standard"、有武器槽 "Custom") |
| 数据变体 | `data/variants/**/*.variant`(core 与各 MOD 合并) | `STOCK` | 变体文件加载流程 (先读 `variantId`，已存在则**静默跳过** —— 跨文件先到先得；加载后置 `STOCK` 来源并注册) |
| 任务设计/任务存档 | `<saves>/missions/variants`、`<saves>/missions/<id>` | `MISSION_DESIGN` / `MISSION_SAVE` | 任务变体加载流程 |
| 存档内变体 | 随生涯存档保存的变体 (refit 界面保存的 `svd_<hullId>_<uid>` 变体) | `REFIT` | 读档时按 id 从存档恢复变体 |

为什么 `.variant` 文件引用 hull 时 hull 必然已注册

启动加载流程先读 `ship_data.csv` 的 hull 注册 (生成全部 `<hullId>_Hull`)，再加载 `data/variants` 变体 —— 所以 `.variant` 文件引用 hull 时，hull 必然已注册。

### 注册表读取速查表

公开读取 API(`com.fs.starfarer.api.SettingsAPI`)：

| 方法 | 语义 |
|---|---|
| `ShipVariantAPI getVariant(String variantId)` | 注册表未命中时回退读取存档内变体；两处都未命中 → `SettingsAPI.getVariant` 返回 null(不抛异常) |
| `List<String> getAllVariantIds()` | 全部已注册 id 的副本 |
| `ShipVariantAPI createEmptyVariant(String hullVariantId, ShipHullSpecAPI hullSpec)` | 代码构造空变体 (注意这不注册进注册表，也不设 `HULL` 来源) |
| `int computeNumFighterBays(ShipVariantAPI variant)` | 按变体算战机甲板数 |

注意 `SettingsAPI.getVariant` 未命中返回 null；"Ship hull variant not found!" 只在引擎内部路径抛。`FleetMemberAPI` 按 id 建成员时未命中走的是 `errorShipVariant` 回退 (默认 `nebula_Standard`)＋日志堆栈 (该回退在模拟战与标题演示章节已讲)。

两条路径不要混为一谈。

变体的下游使用方：

- `FactoryAPI.createFleetMember(FleetMemberType, String variantOrWingId)` / `(FleetMemberType, ShipVariantAPI)`(`com.fs.starfarer.api.FactoryAPI`)。
- `FleetMemberAPI.getVariant()` / `setVariant(variant, withRefit, withStatsUpdate)`(`com.fs.starfarer.api.fleet.FleetMemberAPI`；舰队成员章节)。
- `MutableShipStatsAPI.getVariant()`(`com.fs.starfarer.api.combat.MutableShipStatsAPI`)—— `applyEffectsBeforeShipCreation` 里取变体做装配级改动的标准入口。
- `ShipAPI.getVariant()`(`com.fs.starfarer.api.combat.ShipAPI`)—— 部署后的战斗实体。

一个关键事实 (`FleetMemberAPI`)：**成员按 id 创建时拿到的变体是注册表变体的 `clone()`**，不是共享引用；变体武器组数 > 1 时还会 `setMayAutoAssignWeapons(false)`。因此 "改成员变体" 与 "改注册表变体" 是两个隔离的层。

## 变体对象模型

### 变体字段速查表

注册表只按 id 对表；变体内部存了什么，决定后续每个修改操作的落点。`ShipVariantAPI`(`com.fs.starfarer.api.combat.ShipVariantAPI`) 是变体的公开接口，支持克隆；其字段即装配的存储形态：

| 字段 | 类型 | 语义 |
|---|---|---|
| `weapons` | `Map<String, String>` | 槽位 id → 武器 id(含内置武器；内置武器由 `setHullSpec` 时写入) |
| `weaponGroups` | `List<WeaponGroupSpec>` | 武器组，组内槽位列表 |
| `wings` | `List<String>` | 联队 id 列表，索引 = 甲板序 (内置联队占前 N 位) |
| `hullMods` / `permaMods` / `sMods` / `suppressedMods` | `LinkedHashSet<String>` | 船插、永久船插、内置增强 (s-mod)、压制船插 |
| `numFluxVents` / `numFluxCapacitors` | `int` | 幅能分配 |
| `variantDisplayName` | `String` | 默认 `"Strike"` |
| `hullVariantId` | `String` | 注册表 id |
| `source` / `sourceDetail` | `VariantSource` / `String` | 来源与来源详情 (如任务 id) |
| `stationModules` | `LinkedHashMap<String, String>` | 模块槽 id → 模块变体 id |
| `moduleVariants` / `savedModuleVariants` | `Map<String, ShipVariantAPI>` | 模块变体覆盖 (运行期，不随存档保存)；保存用的序列化副本 |
| `tags` / `goalVariant` / `originalVariant` / `mayAutoAssignWeapons` | 杂项 | 标签；autofit 目标标记；autofit 原始变体；是否允许部署期自动组武器 (**JSON 加载后恒为 `false`**；代码 `new` 出来的恒为 `true`) |

变体引用不要跨存档缓存

`hullSpec` 是 `transient` 字段：反序列化时按 `hullId` 重新从注册表取 (`readResolve`)。任何变体引用都不要跨存档缓存 —— 读档后旧引用里的 `hullSpec` 可能是陈旧或空对象。

## 变体来源 (VariantSource)

### VariantSource 速查表

字段表里的 `source` 决定保存、覆盖与克隆时的行为，值得单独讲。`VariantSource`(`com.fs.starfarer.api.loading.VariantSource`) 恰五个值：

| 值 | 设置点 | 语义 |
|---|---|---|
| `STOCK` | 加载 `data/variants` 变体时 | 数据文件变体；`isStockVariant()` 的判定就是 `source == STOCK` |
| `HULL` | 按 `ship_data.csv` 注册行生成 `<hullId>_Hull` 时 | 自动空装配 |
| `REFIT` | refit 界面保存变体 (`svd_` 前缀) 与代码 `setSource` | 玩家/代码改装配后的变体 |
| `MISSION_DESIGN` | `loadMissionVariants` 的 `<saves>/missions/variants` | 任务设计变体 (`isCommonMissionVariant()` 即此) |
| `MISSION_SAVE` | `loadMissionVariants` 的 `<saves>/missions/<id>` 与 `createMissionVariant` 导出 | 任务内保存的玩家改装配 |

两个高频判定：

- `isStockVariant()` = `getSource() == VariantSource.STOCK`。注意 `<hullId>_Hull` 空装配**不是** stock 变体 (来源是 `HULL`)。
- `isEmptyHullVariant()` = 无内置以外的船插 ∧ 无内置以外的联队 ∧ 无内置以外的武器 ∧ `source == VariantSource.HULL`。所以 "看起来空" 的代码构造变体 (`createEmptyVariant`) 不满足这个判定；战役制作章节也用这个判定识别自动 `<hullId>_Hull` 空装配。

## 修改装配：武器、武器组、船插与幅能

来源与结构都讲清了，接下来用 API 修改装配。先记住主线：改之前 `clone()`，改完检查来源；每张表的 "陷阱" 列就是失败时机。公开 API 方法 (`com.fs.starfarer.api.combat.ShipVariantAPI`) 与语义：

### 武器与槽位速查表

| 方法 | 语义与陷阱 |
|---|---|
| `void addWeapon(String slotId, String weaponId)` | 立即按武器 id 解析 spec(缺失 → 立刻抛 `RuntimeException("Weapon spec [...] not found!")`)；**槽位不校验** —— 坏槽位静默写入，延迟到部署期失败。内置武器槽的写入被忽略 (`addWeapon(slot, spec, false)` 只在非内置槽写入) |
| `void clearSlot(String slotId)` | 从武器表移除槽位 |
| `String getWeaponId(String slotId)` / `WeaponSpecAPI getWeaponSpec(String slotId)` | 未装 → `null` |
| `List<String> getNonBuiltInWeaponSlots()` | 装有武器的槽减去内置槽 |
| `Collection<String> getFittedWeaponSlots()` | 装有武器且非装饰槽 |
| `WeaponSlotAPI getSlot(String slotId)` | 委托 `hullSpec.getWeaponSlot` |

### 幅能与船插速查表

| 方法 | 语义 |
|---|---|
| `setNumFluxVents(int)` / `setNumFluxCapacitors(int)` | 直接写数值 |
| `addMod(String)` | 进 `hullMods`(被压制时忽略) |
| `removeMod(String)` | 内置船插与 perma 船插**返回不删** —— 想移除必须 `removePermaMod`，内置船插不可移除 |
| `clearHullMods()` | 清空后重放内置 + perma 船插 (与 `getHullMods().clear()` 不同，javadoc 明确这点) |
| `addPermaMod(String)` / `addPermaMod(String, boolean isSMod)` | 进 `permaMods`(isSMod 时同时进 `sMods`) 并 `addMod` |
| `getHullMods()` | 含内置与 perma 的完整集合 (`getAllMods()` 是实现类方法，接口外) |
| `getSuppressedMods()` / `addSuppressedMod(String)` | 压制船插：`addSuppressedMod` 会顺带 `removeMod` |

### 武器组速查表

| 方法 | 语义与陷阱 |
|---|---|
| `getWeaponGroups()` / `addWeaponGroup(WeaponGroupSpec)` | 读/追加；**空组被 `addWeaponGroup` 静默丢弃** |
| `WeaponGroupSpec getGroup(int index)` | 越界时**自动新建一个空 `LINKED` 组并追加** —— 遍历式调用会悄悄改变装配 |
| `autoGenerateWeaponGroups()` | 按 AI hints、弧线、武器尺寸把武器聚类成**最多 7 组**：PD / `NO_MANUAL_FIRE` 组排最后、导弹/火箭组排前、每组按内容决定 `LINKED`/`ALTERNATING` 与 autofire 默认值 (`autoGenerateWeaponGroupsV2`) |
| `boolean hasUnassignedWeapons()` | 存在非装饰、未入任何组的武器 → `true` |
| `void assignUnassignedWeapons()` | 无组 → 直接 `autoGenerateWeaponGroups()`；有组 → 未分配武器并入 (已有 7 组时并入第 7 组)，并清空空组 |

`WeaponGroupSpec`(`com.fs.starfarer.api.loading.WeaponGroupSpec`) 是普通类：

- `WeaponGroupType getType()`(只有 `LINKED`/ `ALTERNATING`，`com.fs.starfarer.api.loading.WeaponGroupType`)。
- `List<String> getSlots()`、`addSlot`/`removeSlot`；
- `isAutofireOnByDefault()`/`setAutofireOnByDefault(boolean)`

`clone()` 深拷贝槽位列表。`getGroup(i)` 越界时自动新建空组并追加 —— 想读第 N 组，读完它就真的存在了。读取一律用 `getWeaponGroups()`。

**部署期使用方**：对**非战机**，若 "`isMayAutoAssignWeapons()` 且 (组为空或有未分配武器或只有 1 组)"→ `autoGenerateWeaponGroups()`；否则 → `assignUnassignedWeapons()`。

两个调用**直接写回传入的变体对象**：舰队部署路径传的是成员的克隆变体 (成员进入战斗时生成的克隆)，所以注册表变体不被污染，但成员变体本身会被改写；如果直接把共享/复用变体传进会部署的路径，它会被改。

**战机 (`isFighter()`) 两条都不触发** —— 战机未入组武器拿不到瞄准目标 (见舰船制作章节)。

### OP 成本速查表

| 方法 | 语义 |
|---|---|
| `computeOPCost(MutableCharacterStatsAPI stats)` | \= 武器 OP + 船插 OP + vents + capacitors |
| `computeWeaponOPCost(stats)` | 引用缺失的武器/联队 spec 时抛 `RuntimeException`(消息形如 `Weapon spec with id [...] not found`、`Fighter wing spec with id [...] not found`—— **实现原文的格式化串缺右括号，不要依赖消息文本做解析**) |
| `computeHullModOPCost()` / `computeHullModOPCost(stats)` | 只算非内置、非 perma 船插 |
| `getUnusedOP(stats)` | 船体 OP(`hullSpec.getOrdnancePoints(stats)`)− `computeOPCost` |

## 模块变体

武器、武器组、船插与幅能都落在变体自身字段上；模块变体则是变体与变体之间的引用关系。

- `Map<String, String> getStationModules()`：模块槽 id → 模块变体 id(`.variant` 文件的 `modules` 字段；对象与数组两种写法都被接受、加载期不校验，见模块船制作章节)。
- `List<String> getModuleSlots()`：`stationModules` 中槽位类型为 `STATION_MODULE` 的槽。
- `ShipVariantAPI getModuleVariant(String slotId)`：先查运行期覆盖 `moduleVariants`，未命中且槽位是模块槽时按 `stationModules` 的变体 id 查注册表。
- `void setModuleVariant(String slotId, ShipVariantAPI variant)`：**只接受非 STOCK 来源的变体作为覆盖** ——`null` 与 STOCK 来源变体都按 "移除覆盖" 处理。挂覆盖时 `stationModules` 条目同步指向覆盖变体的 `hullVariantId`。

模块覆盖必须是非 STOCK 来源

`setModuleVariant(slotId, variant)` 只接受非 STOCK 来源的变体作为覆盖 ——`null` 与 STOCK 来源变体都按 "移除覆盖" 处理。忘记 `setSource(REFIT)` 是模块覆盖 "没生效" 的首因 (对应规则强度第 6 条)。

## 克隆、序列化与保存

装配改完之后，变体要能复制、能写盘、能随存档保存 —— 这一步的边界决定改动能在哪里生效。

- `clone()`：深拷贝 `weapons`、`weaponGroups`(逐组 `clone`)、`hullMods`/`permaMods`/`sMods`/`sModdedBuiltIns`/ `suppressedMods`/`wings`/`tags`/`stationModules`，`moduleVariants` 递归克隆：
    - **`hullSpec` 是共享引用**；来源等标量随 `super.clone` 复制 —— **克隆保留原来源**，需要时显式 `setSource`。
- `toJSONObject()`：键与 `.variant` 数据文件一致 ——`displayName`/`hullId`/`variantId`/`fluxVents`/`fluxCapacitors`；`goalVariant` 仅 `true` 时输出：
    - `weaponGroups`(`mode` 用枚举名、`autofire`、`weapons` 为槽→武器 id 映射)；`modules`(数组形式)。
    - `wings`/ `hullMods` 只序列化非内置内容；`permaMods`/`sMods`/`tags`(tags 非空才输出)。
    - 副作用：序列化时若没有任何已装联队会清空 `wings` 列表。
- 引擎的变体保存入口 (`saveVariant(spec, path)`)：克隆一份、把克隆与原件的来源都置 `STOCK`、注册 (覆盖) 并写 JSON 文件 —— 引擎内部保存变体统一走 "克隆后落盘"。
- 任务导出完整流程 (战役制作章节)：`createMissionVariant` 写 `<saves>/missions/<id>/mission_<id>_ship_<n>.variant`。
- 成员变体持久化边界 (成员随档保存时)：**只有非 STOCK 且非 HULL 来源的变体随成员存档** (写入 `savedVariant`)：
    - STOCK/HULL 来源的成员变体不落档，读档后重新从注册表按 id 克隆 —— 所以部署期对成员变体的武器组写入 (来源仍为 STOCK 时) 不会跨存档残留。

成员变体落档边界

成员随档保存时只把**非 STOCK 且非 HULL 来源**的变体写入存档；STOCK/HULL 来源的成员变体不落档，读档后重新从注册表按 id 克隆 ——"改了装配但读档后变回原样" 排查时先看来源，克隆后记得显式 `setSource`。

## 规则强度

前面各节把失败时机散落在表里，这里收拢成一份对照清单。排错时从症状反查规则编号，比逐表翻找更快。

**必须/禁止 (当前基线仍有效，违反有明确可观察结果)**：

1. `.variant` 数据文件**必须**包含 `displayName`、`hullId`、`variantId`、`fluxVents`、`fluxCapacitors`、`weaponGroups` 六个键：
    - 缺键 → `JSONException` 从变体文件加载流程冒泡，**启动加载失败** (战役制作章节已定：`weaponGroups` 必填)。
2. 变体文件的 `hullId` **必须**指向已注册 hull(否则 `Ship hull spec [...] not found!`)；`weapons` 里的武器 id **必须**有效 (否则 `Weapon spec [...] not found!`)—— 两者都是加载期失败，非静默。
3. 代码里 `addWeapon` 的武器 id **必须**有效 —— 调用点立刻抛 `Weapon spec [...] not found!`，而不是延迟报错。
4. 槽位 id **必须**是 hull 真实存在的槽。`addWeapon` 不校验 (静默写入)，部署期才抛 `Ship hull [<hullId>] variant [<variantId>]: slot id [<slot>] not found for weapon [<weapon>]`，舰船不生成。
5. **禁止**试图用 `addWeapon` 改内置武器槽、用 `removeMod` 移除内置/perma 船插 —— 两者都静默不生效，装配不变且无任何报错。
6. `setModuleVariant` 的变体参数**必须**是非 STOCK 来源；传 STOCK 或 `null` 都按 "移除覆盖" 静默处理。
7. **禁止**跨文件重复 `variantId`—— 后者静默跳过，"装配不出现" 的首选排查方向 (战役制作章节)。
8. 未注册 id 走 `SettingsAPI.getVariant` → 返回 null；走 `FleetMemberAPI` 路径 → `errorShipVariant` 回退 (模拟战与标题演示章节)。两条路径行为不同，排查时先分清。

**建议**：

- 修改装配前先 `clone()`(`getVariant().clone()` → `setSource(REFIT)` → 修改 → `setVariant(newVariant, false, false)`)；改完来源记得 `setSource`，克隆不会自动改。
- 代码组装完武器后调用 `autoGenerateWeaponGroups()` 或 `assignUnassignedWeapons()` 补组，避免部署期隐式补组改写变体。
- 运行时按 id 取变体用 `SettingsAPI.getVariant`；要遍历全部注册变体用 `getAllVariantIds()`(按注册表 id 全量扫描模式)。

## 实现舰船变体修改

本页把一个注册表变体送过四个可观察节点：读取来源、克隆改装、补齐武器组、覆盖模块。每段都放在对应角色的 `runOnce()` 内；日志出现后再进入下一段，最后才处理序列化。

### 读取注册表变体

在 `GearInVariantReader.runOnce()` 中读取已注册的 `brawler_Assault`，同时读取引擎按 hull 自动生成的 `brawler_Hull`。

```java
MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();
ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");
ShipVariantAPI hullVariant = Global.getSettings().getVariant("brawler_Hull");
GearInVariantLog.logVariant("stock", stock, stats);
LOG.info("GearIn hull source=" + hullVariant.getSource()
        + " empty=" + hullVariant.isEmptyHullVariant());
```

完成信号：日志显示 `brawler_Assault` 的 `source=STOCK`，并显示 `brawler_Hull` 的 `source=HULL` 与 `empty=true`。

### 隔离克隆并改装

在 `GearInVariantRefitter.runOnce()` 中先克隆，再改槽位、幅能和船插，最后把来源改成 `REFIT`。`clone()` 复制装配集合，但不会替你改来源。

```java
ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");
ShipVariantAPI refit = stock.clone();
refit.clearSlot("WS 003");
refit.addWeapon("WS 003", "railgun");
refit.setNumFluxVents(14);
refit.setNumFluxCapacitors(10);
refit.addMod("hardened_subsystems");
refit.removeMod("reinforcedhull");
refit.setSource(VariantSource.REFIT);
refit.setVariantDisplayName("GearIn Refit");
```

完成信号：日志中的克隆变体显示 `source=REFIT`、`WS 003=railgun`、幅能 14/10；随后再次读取 stock，原槽位仍是 `annihilator`。

### 让武器进入可控分组

在 `GearInWeaponGroupBuilder.runOnce()` 中从空装配开始装两门同型武器，先观察未分组状态，再调用自动分组。

```java
ShipVariantAPI fromEmpty = Global.getSettings().getVariant("brawler_Hull").clone();
fromEmpty.setSource(VariantSource.REFIT);
fromEmpty.addWeapon("WS 003", "railgun");
fromEmpty.addWeapon("WS 004", "railgun");
LOG.info("before groups=" + fromEmpty.getWeaponGroups().size()
        + " unassigned=" + fromEmpty.hasUnassignedWeapons());
fromEmpty.autoGenerateWeaponGroups();
```

完成信号：调用前 `unassigned=true`，调用后生成一个包含两个槽位的武器组。战机变体要在数据文件中提前写好分组，因为部署期补组不覆盖战机。

### 覆盖模块变体

在 `GearInModuleOverrider.runOnce()` 中读取空间站模块槽，克隆模块并设为 `REFIT`，再挂回站体克隆。

```java
ShipVariantAPI station = Global.getSettings().getVariant("station1_Standard");
ShipVariantAPI moduleCopy = station.getModuleVariant("WS 009").clone();
moduleCopy.setSource(VariantSource.REFIT);
ShipVariantAPI stationCopy = station.clone();
stationCopy.setModuleVariant("WS 009", moduleCopy);
```

完成信号：`stationCopy.getModuleVariant("WS 009").getSource()` 为 `REFIT`。若传入 STOCK 来源，调用会按移除覆盖处理，站体仍返回原模块。

### 序列化改装结果

在 `GearInVariantSerializer.runOnce()` 中对 REFIT 克隆调用 `toJSONObject()`，只核对输出键，不把这个运行时对象误当成注册表文件。

```java
ShipVariantAPI refit = Global.getSettings().getVariant("brawler_Assault").clone();
refit.setSource(VariantSource.REFIT);
JSONObject json = refit.toJSONObject();
LOG.info("GearIn refit JSON: " + json.toString(2));
```

完成信号：日志 JSON 至少包含 `displayName`、`hullId`、`variantId`、幅能字段和 `weaponGroups`；改装是否随存档保留仍由来源与成员保存路径决定。

## 组装舰船变体实现

接口语义讲完了，接下来把每个语义点实际跑一遍。在既有 MOD 布局与前置的 `GearInModPlugin` 生命周期例之上新增七个类 (不新增任何资源文件)：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       ├── GearInVariantLauncher      # 本节新增：注册载体 (归本节所有)
│       ├── GearInVariantReader        # 注册表读取
│       ├── GearInVariantRefitter      # 克隆改装
│       ├── GearInWeaponGroupBuilder   # 武器组生成
│       ├── GearInModuleOverrider      # 模块变体覆盖
│       ├── GearInVariantSerializer    # 变体序列化
│       └── GearInVariantLog           # 包内日志助手
└── jars\
```

### GearInVariantLauncher(注册入口类)

职责：薄接入类，本节唯一的注册入口。`REGISTER_MARK` 是存档持久化判断键；判断通过后调用静态 `register(SectorAPI)`，把五个角色脚本按顺序挂进生涯。每个角色脚本首次 `advance()` 后 `isDone()` 置 true，引擎下一帧自动移除 —— 整套演示在首个生涯帧内顺序完成，读档后不再重复。

### GearInVariantReader(注册表读取)

职责：演示 `SettingsAPI.getVariant` 的注册表读取与来源判定 —— 打印股票变体 `brawler_Assault`(STOCK) 的身份/装配/OP 摘要、自动空装配 `brawler_Hull` (HULL 来源，`source == HULL`、`isEmptyHullVariant() == true`) 与注册表变体总数。

调用关系：由 `GearInVariantLauncher.register()` 第一个挂载；摘要打印复用 `GearInVariantLog.logVariant()`。

### GearInVariantRefitter(克隆改装)

职责：演示 `clone()` 的深拷贝语义 —— 修改克隆 (换武器、改幅能、改船插、改来源、重新生成武器组) 不影响注册表原变体；并给出 stock 与 refit 的 OP 对照 (`computeOPCost` = 武器 OP + 船插 OP + vents + caps；`getUnusedOP` = 船体 OP − 已用 OP)。

调用关系：由 `GearInVariantLauncher.register()` 第二个挂载；摘要打印复用 `GearInVariantLog.logVariant()`。

### GearInWeaponGroupBuilder(武器组生成)

职责：演示空装配装武器但无武器组时的 `hasUnassignedWeapons() == true`，以及 `autoGenerateWeaponGroups()` 按 AI hints / 弧线 / 尺寸重新聚类补组 (同型武器必合并为一组，最多 7 组)。

调用关系：由 `GearInVariantLauncher.register()` 第三个挂载。

### GearInModuleOverrider(模块变体覆盖)

职责：演示 `stationModules` / `getModuleVariant` / `setModuleVariant`——`setModuleVariant` 只接受非 STOCK 来源的覆盖 (STOCK/null 都会被当作 "移除覆盖" 处理)。

调用关系：由 `GearInVariantLauncher.register()` 第四个挂载。

### GearInVariantSerializer(变体序列化)

职责：演示 `toJSONObject()` 的输出键与 `.variant` 数据文件一致 (`displayName`/`hullId`/`variantId`/`fluxVents`/`fluxCapacitors`/ `weaponGroups`/`modules`/`wings`/`hullMods`/`permaMods`/`sMods`/`tags`)。

调用关系：由 `GearInVariantLauncher.register()` 第五个挂载。

### GearInVariantLog(包内日志助手)

职责：包内私有助手，不实现接口、不直接注册。`GearInVariantReader` 与 `GearInVariantRefitter` 共用的变体身份/来源/装配/OP 摘要打印格式，避免格式串在两处重复。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/**
 * 包内协作：把变体身份 / 来源 / 装配 / OP 摘要打印到日志。
 * GearInVariantReader 与 GearInVariantRefitter 共用的打印格式，避免两处重复。
 */
final class GearInVariantLog {

    private static final Logger LOG = Global.getLogger(GearInVariantLog.class);

    private GearInVariantLog() {
    }

    static void logVariant(String label, ShipVariantAPI variant, MutableCharacterStatsAPI stats) {
        LOG.info(String.format(
                "GearIn %s: id=%s displayName=%s hull=%s source=%s isStockVariant=%s isEmptyHullVariant=%s "
                        + "weapons=%s mods=%s wings=%s vents=%d caps=%d op=%d unused=%d",
                label, variant.getHullVariantId(), variant.getDisplayName(), variant.getHullSpec().getHullId(),
                variant.getSource(), variant.isStockVariant(), variant.isEmptyHullVariant(),
                variant.getNonBuiltInWeaponSlots(), variant.getHullMods(), variant.getWings(),
                variant.getNumFluxVents(), variant.getNumFluxCapacitors(),
                variant.computeOPCost(stats), variant.getUnusedOP(stats)));
    }
}
```

对应关系：

- 五个脚本类 (`GearInVariantReader` / `GearInVariantRefitter` / `GearInWeaponGroupBuilder` / `GearInModuleOverrider` / `GearInVariantSerializer`)`implements EveryFrameScript`：
    - 三方法约定与 "`isDone` 先查再推进" 的循环语义见生涯脚本章节；`done` 在首次 `advance` 后置位，引擎下一帧自动移除。
- `GearInVariantLauncher.REGISTER_MARK`：persistent data 判断键 (`"GearIn_VariantLauncher"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
- `GearInVariantReader`：`getVariant` 读取注册表；`brawler_Assault` 是 `data/variants/brawler_Assault.variant`(STOCK，`goalVariant: true`)；`brawler_Hull` 是自动空装配 (HULL 来源，brawler 有武器槽所以 displayName 是 "Custom")。
    - `GearInVariantRefitter`：克隆后修改 ——`clearSlot`/`addWeapon`/`setNumFluxVents`/`setNumFluxCapacitors`/`addMod`/ `removeMod`/`setSource`/`setVariantDisplayName`/`autoGenerateWeaponGroups` 一次集齐。
    - brawler 的 `WS 003` 是 SMALL UNIVERSAL 硬点 (`data/hulls/brawler.ship`)，`railgun` 是小型实弹，可装。
    - 克隆隔离验证：原 `stock` 的 `WS 003` 仍是 `annihilator`(`brawler_Assault.variant` 的组 2)，幅能不变。
- `GearInWeaponGroupBuilder`：空装配克隆后只装两门武器、无武器组 → `hasUnassignedWeapons() == true`；`autoGenerateWeaponGroups()` 后生成 1 个组。
    - `GearInModuleOverrider`：`station1_Standard`(`data/variants/stations/station1_Standard.variant`，`modules` 用数组写法) 的 `WS 009` 是 `STATION_MODULE` 槽 (`data/hulls/station1.ship`)。
    - 模块变体是 `module_large1_lowtech_Standard`。
    - 克隆模块变体并 `setSource(REFIT)` 后挂到 `stationCopy`—— 覆盖生效 (来源显示 REFIT，id 不变)。
- `GearInVariantSerializer`：`toJSONObject()` 序列化克隆后的 refit 变体，键与 `.variant` 文件一致。
- `GearInVariantLog`：包内日志摘要助手，两个脚本共用同一份打印格式，避免重复。

调用点 (工程片段 —— 方法所在类由前置生命周期例持有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(前置生命周期例持有) 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInVariantLauncher.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInVariantLauncher.REGISTER_MARK, true);
        GearInVariantLauncher.register(Global.getSector());
    }
    // 可选冗余查重 (建议：hasScript 与 persistent data 门互为冗余)：
    if (!Global.getSector().hasScript(GearInVariantReader.class)) {
        GearInVariantLauncher.register(Global.getSector());
    }
```

(上面两段只取一段即满足 "不重复注册"；两段同留时第二段会在第一段之后补注册，不会叠加 —— 但演示时建议只保留判断段，避免混淆。生涯脚本章节对同样的二选一做过说明。)

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/` 下七个类的 class 条目 (可用 `jar tf` 核对)。

## 关键故障实验：改装结果污染了其他舰船

在 `GearInVariantRefitter.runOnce()` 中暂时直接修改注册表返回的 stock 变体，再创建第二个同 id 成员。两个成员都会拿到被改过的槽位，说明共享对象已经被污染。

**局部故障片段：**

```java
// 故障片段
ShipVariantAPI variant = Global.getSettings().getVariant("wolf_Assault"); variant.addWeapon("WS 001", "lightmg");
```

**局部修正片段：**

```java
// 修正片段
ShipVariantAPI variant = Global.getSettings().getVariant("wolf_Assault").clone();
variant.setSource(VariantSource.REFIT);
variant.addWeapon("WS 001", "lightmg");
```

原因：注册表对象由多个创建路径共享；`FleetMemberAPI` 创建成员时会克隆，但直接修改 `SettingsAPI.getVariant()` 的结果会先改变注册表。复验：重启或重新载入原始数据，改为 clone + `setSource(REFIT)`，连续创建两个成员，确认只有显式使用该克隆的成员拥有 `lightmg`。

## 验证方案

前置数据：既有 MOD 布局 [C6-S1] + `GearInModPlugin` ([C6-S2]，含本节调用点片段)、本节七个类；生涯时钟脚本 `GearInSectorClockScript` (可选)。

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含七个类的 class 条目。
2. **新建生涯**：查看 `starsector.log` 中本节七个类输出的全部 `GearIn ...` 日志。
3. **读档**：保存后读档，观察本节脚本是否重复执行。
4. 进入任意有 `station1` 的市场，观察空间站模块是否正常生成。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/` 下七个类的 class 条目。
- **新建生涯**：`starsector.log` 按注册顺序出现以下日志 (数值以实际游戏为准)：

    ```text
    GearIn stock: id=brawler_Assault displayName=Assault hull=brawler source=STOCK isStockVariant=true isEmptyHullVariant=false weapons=[WS 001, WS 002, WS 003, WS 004] mods=[fluxbreakers, reinforcedhull] wings=[] vents=10 caps=8 op=... unused=... GearIn hull empty variant: id=brawler_Hull source=HULL isStockVariant=false isEmptyHullVariant=true displayName=Custom GearIn refit clone: id=brawler_Assault displayName=GearIn Refit hull=brawler source=REFIT isStockVariant=false isEmptyHullVariant=false weapons=[...] mods=[fluxbreakers, hardened_subsystems] wings=[] vents=14 caps=10 op=... unused=... GearIn clone isolation: stock WS 003=annihilator vents=10 caps=8 hasReinforcedHull=true GearIn from empty: groups=0 hasUnassignedWeapons=true GearIn from empty groups after autoGenerateWeaponGroups: LINKED[[WS 003, WS 004]] GearIn OP: stock=... refit=... hullOP=... unusedRefit=... GearIn modules: stationModules={WS 006=module_armour3_Standard, ...} module WS 009=module_large1_lowtech_Standard override=module_large1_lowtech_Standard overrideSource=REFIT GearIn refit JSON: { "displayName": "GearIn Refit", "hullId": "brawler", "variantId": "brawler_Assault", ... } GearIn registered variant count: 647
    ```

    要点核对：`brawler_Hull` 的 `isStockVariant=false` 且 `isEmptyHullVariant=true`(来源是 HULL 不是 STOCK)；`refit clone` 的来源是 REFIT、`reinforcedhull` 已被移除、`hardened_subsystems` 已加入、vents/caps 变成 14/10；`clone isolation` 一行证明原注册表变体未被污染 (WS 003 仍是 `annihilator`、幅能 10/8)；`overrideSource=REFIT` 证明非 STOCK 覆盖已挂上。
- **各脚本错误行**：若日志出现任一脚本角色的 ERROR 行 (含堆栈)：
    - `GearIn variant reader failed` / `GearIn variant refitter failed` / `GearIn weapon group builder failed`
    - `GearIn module overrider failed` 或 `GearIn variant serializer failed`；
    - 说明对应角色脚本某一步抛错；
    - 按堆栈对照 "规则强度" 节定位 (最常见的是引用了不存在的 id)。
- **读档**：不重复执行演示 (`REGISTER_MARK` 门已关、`hasScript` 命中)；`onGameLoad` 里新档与读档各执行一次演示是设计行为 (每个角色脚本只跑一次即自停)。
- **退出到主菜单**：脚本停止 (生涯场景不再推进)；回主菜单后再进档按第 5 条继续。

## 故障分析

- **装配没有出现在注册表中**：变体 ID 重复，或文件没有放在 `data/variants` 下并使用 `.variant` 后缀。

    - **解决方案**：调整文件位置并换用唯一 ID。

- **加载变体时启动中止**：文件缺少必填键，或引用了不存在的 hull、武器 ID。

    - **解决方案**：根据日志中的 `JSONException` 或 `spec [...] not found` 核对数据，重启。

- **`getVariant` 返回 null**：注册表和存档都没有与传入 ID 完全匹配的变体。

    - **解决方案**：用 `getAllVariantIds()` 核对大小写与拼写，再重新调用读取入口。

- **部署时报告槽位不存在**：`addWeapon` 接受了 hull 中没有的槽位 ID，错误到构建舰船时才暴露。

    - **解决方案**：写入前通过 `getSlot` 或 `getAllWeaponSlots` 核对槽位，再部署同一装配。

- **修改一艘船后同装配舰船全部变化**：代码直接修改了 `SettingsAPI.getVariant` 返回的共享对象。

    - **解决方案**：先 `clone()`，修改后通过 `setVariant` 写回目标成员。

- **读档后装配恢复原状**：克隆保留了 STOCK 或 HULL 来源，成员没有按改装结果落档。

    - **解决方案**：修改前调用 `setSource(VariantSource.REFIT)`，保存并读档。

- **内置装备没有被修改**：普通写入和移除接口会忽略内置武器、内置船插或 perma 船插。

    - **解决方案**：只修改允许改装的槽位，perma 项使用对应接口。

- **战机武器没有目标**：战机变体存在未分组武器，而部署流程不会为战机自动补组。

    - **解决方案**：在数据文件中写全 `weaponGroups`。

- **读取武器组后多出空组**：越界调用 `getGroup(i)` 会创建并追加新组。

    - **解决方案**：遍历改用 `getWeaponGroups()`。

- **OP 计算报告武器 spec 不存在**：变体仍引用已删除或未加载的武器、联队 ID。

    - **解决方案**：清理失效引用并确认依赖已启用，再重新计算 OP。

## 总结

本节建立了一条完整的变体数据链：`ship_data.csv` 与 `data/variants` 负责注册，`SettingsAPI.getVariant` 负责读取，`clone()` 隔离修改，来源决定覆盖与落档，`toJSONObject` 与 `saveVariant` 完成序列化。排错时先分清两条读取路径，再沿 id、来源、克隆三层定位 —— 这是本节最可复用的判断。

- 公开面只有 `ShipVariantAPI` 接口；`hullSpec` 不随存档保存，读档按 `hullId` 重建 —— **变体引用禁止跨存档缓存**。
- `addWeapon` 校验武器 id 但不校验槽位：武器 id 错 → 调用点抛；槽位 id 错 → 部署期抛。
- 内置武器槽与内置/perma 船插不可改 (静默不生效)；`clearHullMods` 保留内置与 perma。
- `getGroup(i)` 越界新建空组；`addWeaponGroup` 丢弃空组；`autoGenerateWeaponGroups` 最多 7 组且写回传入变体 —— 改装配先 `clone()`。
- 克隆保留来源；需要 REFIT 语义要显式 `setSource`。
- `setModuleVariant` 只接受非 STOCK 覆盖；模块槽 id 由 `getModuleSlots()` 给出 (STATION\_MODULE 槽)。
- 战机不触发部署期补组；未入组武器拿不到目标。

变体只是数据，它的生命周期终点是部署与存档：本节把数据层的全部语义讲清后，它经 `setVariant` 挂到舰队成员、随人物 (Person) 与 `MutableStat` 进入状态交接链 —— 那些交接点 "谁克隆、谁改来源、谁落档" 的差异，正是由本节的规则决定的。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：变体公开接口 —— 读取、克隆、装配修改、武器组、模块与序列化。
- `com.fs.starfarer.api.SettingsAPI`：`getVariant` / `getAllVariantIds` / `createEmptyVariant` / `computeNumFighterBays`。
- `com.fs.starfarer.api.loading.VariantSource`：变体来源五值枚举 (STOCK / HULL / REFIT / MISSION\_DESIGN / MISSION\_SAVE)。
- `com.fs.starfarer.api.loading.WeaponGroupSpec` / `com.fs.starfarer.api.loading.WeaponGroupType`：武器组结构与组类型 (LINKED / ALTERNATING)。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：`getVariant` / `setVariant`(舰队侧读写)。
- `com.fs.starfarer.api.FactoryAPI`：`createFleetMember`(按变体/联队 id 建成员)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI` / `com.fs.starfarer.api.combat.ShipAPI`：`getVariant`(部署前后取变体的入口)。
- `data.scripts.gearin.campaign.GearInVariantLauncher`：本节注册入口类 (含 `REGISTER_MARK` 键)。
- `data/variants/brawler_Assault.variant`：示例 stock 变体 (武器组、`goalVariant: true`、`hullMods`)。
- `data/variants/stations/station1_Standard.variant`：`modules` 数组写法 (解析时剥除 `#` 注释)。
- `data/hulls/brawler.ship`：`weaponSlots`(WS 001/002 MEDIUM BALLISTIC，WS 003/004 SMALL UNIVERSAL 硬点)。
- `data/hulls/station1.ship`：`STATION_MODULE` 槽 (含 `WS 009`)。

## 完整最小例

::: details 完整成品：GearInVariantLauncher

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 角色：注册载体 (薄接线类)。onGameLoad 的一次性门控通过后调用 register()，
 * 把本节五个角色脚本 (读取 / 克隆改装 / 武器组 / 模块覆盖 / 序列化) 逐一挂进生涯。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，注册前用 sector persistent
 * data 键 REGISTER_MARK 做一次性门控 (C6-S2 的 CLASS_MARK 模式，模式同 C9-S1
 * 的 GearInSectorClockScript)。每个角色脚本首次 advance() 后 isDone() 置 true，
 * 引擎下一帧自动移除 —— 整套演示在首个生涯帧内顺序完成，读档后不再重复。
 */
public final class GearInVariantLauncher {

    /** 存档持久化标记：本组演示脚本是否已在该存档注册过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_VariantLauncher";

    private GearInVariantLauncher() {
    }

    /** 注册本节全部变体演示脚本；每个脚本跑一次即自停。 */
    public static void register(SectorAPI sector) {
        sector.addScript(new GearInVariantReader());
        sector.addScript(new GearInVariantRefitter());
        sector.addScript(new GearInWeaponGroupBuilder());
        sector.addScript(new GearInModuleOverrider());
        sector.addScript(new GearInVariantSerializer());
    }
}
```

:::

::: details 完整成品：GearInVariantReader

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/**
 * 角色：注册表读取。演示 SettingsAPI.getVariant 的注册表读取与来源判定 ——
 * 股票变体 brawler_Assault(STOCK) 的身份 / 装配 / OP 摘要、自动空装配 brawler_Hull
 * (HULL 来源，source == HULL、isEmptyHullVariant() == true) 与注册表变体总数。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停：首次 advance() 后 isDone() 置 true)。
 */
public class GearInVariantReader implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantReader.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn variant reader failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();

        // 1) 注册表读取：缺失 id 返回 null
        //    (变体注册表的 getVariant，注册表未命中时还会回退查询存档变体数据)
        ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");
        GearInVariantLog.logVariant("stock", stock, stats);

        // 2) 每个注册 hull 自动生成 <hullId>_Hull 空装配，source == HULL
        ShipVariantAPI hullVariant = Global.getSettings().getVariant("brawler_Hull");
        LOG.info(String.format(
                "GearIn hull empty variant: id=%s source=%s isStockVariant=%s isEmptyHullVariant=%s displayName=%s",
                hullVariant.getHullVariantId(), hullVariant.getSource(),
                hullVariant.isStockVariant(), hullVariant.isEmptyHullVariant(),
                hullVariant.getDisplayName()));

        // 3) 注册表统计：全部已注册变体 id(数据变体、<hullId>_Hull 空装配与任务变体)
        LOG.info("GearIn registered variant count: " + Global.getSettings().getAllVariantIds().size());
    }
}
```

:::

::: details 完整成品：GearInVariantRefitter

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：克隆改装。演示 clone() 的深拷贝语义 —— 修改克隆 (换武器、改幅能、改船插、
 * 改来源、重新生成武器组) 不影响注册表原变体；并给出 stock 与 refit 的 OP 对照
 * (computeOPCost = 武器 OP + 船插 OP + vents + caps；getUnusedOP = 船体 OP - 已用 OP)。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停)。
 */
public class GearInVariantRefitter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantRefitter.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn variant refitter failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();
        ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");

        // 1) 克隆后修改装配：clone() 深拷贝全部集合 (武器表、武器组逐组、船插、联队、模块变体；船体 spec 共享引用)，修改克隆不影响原变体
        ShipVariantAPI refit = stock.clone();
        refit.clearSlot("WS 003");                    // 卸下旧武器
        refit.addWeapon("WS 003", "railgun");         // 装上小实弹 (brawler 的 WS 003 是 SMALL UNIVERSAL 硬点)
        refit.setNumFluxVents(14);
        refit.setNumFluxCapacitors(10);
        refit.addMod("hardened_subsystems");
        refit.removeMod("reinforcedhull");
        refit.setSource(VariantSource.REFIT);
        refit.setVariantDisplayName("GearIn Refit");
        refit.autoGenerateWeaponGroups();             // 按 AI hints/弧线/尺寸重新聚类，最多 7 组
        GearInVariantLog.logVariant("refit clone", refit, stats);

        // 2) 克隆隔离：改克隆后注册表原变体不变
        LOG.info(String.format(
                "GearIn clone isolation: stock WS 003=%s vents=%d caps=%d hasReinforcedHull=%s",
                stock.getWeaponId("WS 003"), stock.getNumFluxVents(), stock.getNumFluxCapacitors(),
                stock.hasHullMod("reinforcedhull")));

        // 3) OP 对照：computeOPCost = 武器 OP + 船插 OP + vents + caps；getUnusedOP = 船体 OP - 已用 OP
        int opStock = stock.computeOPCost(stats);
        int opRefit = refit.computeOPCost(stats);
        int hullOp = stock.getHullSpec().getOrdnancePoints(stats);
        LOG.info(String.format(
                "GearIn OP: stock=%d refit=%d hullOP=%d unusedRefit=%d",
                opStock, opRefit, hullOp, refit.getUnusedOP(stats)));
    }
}
```

:::

::: details 完整成品：GearInWeaponGroupBuilder

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponGroupSpec;

/**
 * 角色：武器组生成。演示空装配装武器但无武器组时的
 * hasUnassignedWeapons() == true，以及 autoGenerateWeaponGroups() 按 AI hints / 弧线 /
 * 尺寸重新聚类补组 (同型武器必合并为一组，最多 7 组)。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停)。
 */
public class GearInWeaponGroupBuilder implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInWeaponGroupBuilder.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn weapon group builder failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // 1) 空装配 + 武器但无武器组：hasUnassignedWeapons() == true，autoGenerateWeaponGroups() 补组
        ShipVariantAPI fromEmpty = Global.getSettings().getVariant("brawler_Hull").clone();
        fromEmpty.setSource(VariantSource.REFIT);
        fromEmpty.addWeapon("WS 003", "railgun");
        fromEmpty.addWeapon("WS 004", "railgun");
        LOG.info(String.format(
                "GearIn from empty: groups=%d hasUnassignedWeapons=%s",
                fromEmpty.getWeaponGroups().size(), fromEmpty.hasUnassignedWeapons()));
        fromEmpty.autoGenerateWeaponGroups();
        StringBuilder groupSummary = new StringBuilder();
        for (WeaponGroupSpec group : fromEmpty.getWeaponGroups()) {
            groupSummary.append(group.getType()).append('[').append(group.getSlots()).append("] ");
        }
        LOG.info("GearIn from empty groups after autoGenerateWeaponGroups: " + groupSummary);
    }
}
```

:::

::: details 完整成品：GearInModuleOverrider

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：模块变体覆盖。演示 stationModules / getModuleVariant / setModuleVariant——
 * setModuleVariant 只接受非 STOCK 来源的覆盖 (STOCK/null 都会被当作 "移除覆盖" 处理)。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停)。
 */
public class GearInModuleOverrider implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInModuleOverrider.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn module overrider failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // stationModules 是 slotId -> variantId 映射；getModuleVariant 按槽取模块变体，
        // setModuleVariant 只接受非 STOCK 来源的覆盖 (STOCK/null 都会被当作 "移除覆盖" 处理)
        ShipVariantAPI station = Global.getSettings().getVariant("station1_Standard");
        ShipVariantAPI moduleStock = station.getModuleVariant("WS 009");
        ShipVariantAPI stationCopy = station.clone();
        ShipVariantAPI moduleCopy = moduleStock.clone();
        moduleCopy.setSource(VariantSource.REFIT);
        stationCopy.setModuleVariant("WS 009", moduleCopy);
        LOG.info(String.format(
                "GearIn modules: stationModules=%s module WS 009=%s override=%s overrideSource=%s",
                station.getStationModules(),
                moduleStock.getHullVariantId(),
                stationCopy.getModuleVariant("WS 009").getHullVariantId(),
                stationCopy.getModuleVariant("WS 009").getSource()));
    }
}
```

:::

::: details 完整成品：GearInVariantSerializer

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：变体序列化。演示 toJSONObject() 的输出键与 .variant 数据文件一致
 * (displayName/hullId/variantId/fluxVents/fluxCapacitors/weaponGroups/modules/wings/hullMods/permaMods/sMods/tags)。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停)。
 */
public class GearInVariantSerializer implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantSerializer.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn variant serializer failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // 克隆一份 STOCK 变体并显式改 REFIT 来源后序列化，对照 .variant 文件键
        ShipVariantAPI refit = Global.getSettings().getVariant("brawler_Assault").clone();
        refit.setSource(VariantSource.REFIT);
        try {
            JSONObject json = refit.toJSONObject();
            LOG.info("GearIn refit JSON: " + json.toString(2));
        } catch (Exception e) {
            LOG.error("GearIn variant JSON serialization failed", e);
        }
    }
}
```

:::
