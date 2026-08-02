# 战斗插件与监听器：生命周期与注册

本页把战斗域的"对象生命周期"讲透：`EveryFrameCombatPlugin` 的三条注册路径、每帧回调顺序与移除方式（含 `init()` 已废弃的含义）；公开监听器系统 `com.fs.starfarer.api.combat.listeners` 的三块注册表面与每类监听器的必须遵守的放置规则；内部事件系统与公开 API 的边界，以及战斗状态归属的清理纪律（战斗结束的正确收尾方式）。读取侧能力（战斗时间、`isCombatOver()` 语义、实体存活性判定）来自战斗状态读取单元，本页直接消费它们完成收尾。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 中战斗插件的三条注册路径（settings `plugins` 段、战斗内 `addPlugin(...)`、战役 `MissionDefinitionAPI.addPlugin(...)`），并解释每场战斗插件实例如何被创建与附加；
- 说出插件回调的帧内顺序（`processInputPreCoreControls` → `advance` → 渲染回调），并解释 `init()` 已废弃的原因与 `advance()` 必须判 `engine == null` 的原因；
- 用 `removePlugin(...)` / `hasPluginOfClass(...)` 管理插件的战斗内移除，并说出自移除时必须同时移除自己注册的引擎级监听器；
- 说出公开监听器系统的三块注册表面（引擎 / 舰船 / 统计）与十类监听器各自的必须遵守的放置规则，以及放错层级后的可观察结果（静默不生效、无报错）；
- 说出引擎内部的事件监听器接口不是公开 API、MOD 代码不能实现它，并指出 MOD 侧对应的事件入口；
- 解释为什么战斗结束没有清理回调，以及正确的收尾方式——在 `advance()` 里用 `engine.isCombatOver()` 识别、随战斗结束由引擎销毁统一释放；
- 从症状定位七类典型故障：`init()` 依赖、监听器放错层级、插件移除后监听器残留、跨战斗缓存引用、暂停期间状态推进、内部接口误用、收尾时机不可靠。

## 前置知识

本节建立在前置单元的插件模型、引擎生命周期与战斗状态读取能力之上：

- MOD 目录结构与 `starsector.log`（S01-P01）：本页所有可观察结果都落在日志里。
- 战斗插件模型（S02-P01）：三类注册入口、`addPlugin(...)`、暂停守卫——本页细化生命周期与移除。
- 战役与生涯概念、测试战役（S03-P01）：`GearIn_test` 的部署与战斗结束是本页验证所依托的场景。
- 编译 JAR 链路（S06-P01）：前置单元建立的编译链路产出 `jars\GearIndustry.jar`。
- 引擎生命周期与跨战斗缓存禁令（S06-P03）：战斗引擎每场战斗结束销毁后立即重建、战斗外是非 null 空引擎——本页把同一条纪律延伸到插件、监听器与它们持有的引用。
- 战斗状态读取（S07-P01）：`isCombatOver()` 在结束淡出完成前已为 `true`、时间语义、`getShips()` 遍历与实体存活性——本页消费这些判定做收尾。
- 监听器通用概念（生涯侧事件系统，S10-P02）：完整讲解在生涯侧事件系统单元；本页只讲战斗域监听器，不重复通用概念。

## 文件与 API 链路

本页涉及的接口与注册表面：

| 接口 / 类型 | 本页角色 | 链路 |
|---|---|---|
| `EveryFrameCombatPlugin` | 每帧回调接口 | `extends CombatEnginePlugin`；前置单元讲模型，本页讲生命周期 |
| `CombatEnginePlugin` | 插件父接口，`init(CombatEngineAPI)` 已废弃 | `com.fs.starfarer.api.combat` |
| `BaseEveryFrameCombatPlugin` | 五个回调的空实现基类 | 建议继承（API 新增方法不破坏编译，见战斗插件模型单元）|
| `CombatEngineAPI.addPlugin / removePlugin / hasPluginOfClass` | 战斗内注册 / 移除 / 查询 | `addPlugin` 的 API 注释：init 在方法内被调用 |
| `CombatListenerManagerAPI` | 监听器表的增删查 | `engine.getListenerManager()` / `ShipAPI` / `MutableShipStatsAPI` 三块表面 |
| `com.fs.starfarer.api.combat.listeners.*` | 十类公开监听器接口 | 每类有必须遵守的放置层级规则（见"监听器"节）|
| `CombatListenerUtil` | 引擎内部派发入口 | 公开 API 包内的工具类（damage/range/OP/部署派发）|
| `IntervalUtil` | 定期间隔触发 | `com.fs.starfarer.api.util`（本页示例用它做周期日志）|

### 插件回调速查（RC8）

| 回调 | 时机 | 说明 |
|---|---|---|
| `init(CombatEngineAPI)` | 注册时（`addPlugin` 内）或战斗地图初始化时 | **已废弃**：不保证先于 `advance()` 被调用（`CombatEnginePlugin` API 注释原文）；`advance()` 必须判 null |
| `processInputPreCoreControls(float, List<InputEventAPI>)` | 每帧核心输入处理之前 | 输入事件的预处理钩子；引擎对输入的处理先于战斗推进 |
| `advance(float, List<InputEventAPI>)` | 每帧（**暂停时也调用**）| `amount` 是经时间倍率缩放后的帧长（与战斗状态读取单元的"同一份数"语义一致）；暂停守卫是插件责任（战斗插件模型单元）|
| `renderInWorldCoords(ViewportAPI)` / `renderInUICoords(ViewportAPI)` | 每帧渲染阶段 | 世界坐标 / UI 坐标两个空间，需要渲染的插件实现 |

### 监听器家族与放置层级（必须遵守的规则表）

以下"只能/必须"规则全部来自接口 javadoc 原文（0.98a-RC8 有效），**违反的共性是：静默不生效、无任何报错**——监听器被接受注册，但派发代码从不查询放错层级的那张表：

| 接口 | 必须放置的表面 | javadoc 原文（节选）| 派发证明 |
|---|---|---|---|
| `FleetMemberDeploymentListener` | **只能引擎** | "Can only be added to the combat engine, not to specific ships; won't do anything in the latter case." | 部署时 `CombatListenerUtil.reportFleetMemberDeployed` 只派发引擎级监听器（舰船级分支在实现里被注释掉）|
| `AdvanceableListener` | **只能舰船** | "Only supported when added to a ShipAPI, not to CombatEngineAPI." | 只有舰船推进时派发它（`getListenerManager() != null && amount > 0` 时）|
| `WeaponRangeModifier` / `WeaponBaseRangeModifier` | **只能舰船** | "Only applies when added to ships, NOT the combat engine, for performance reasons." | `CombatListenerUtil` 的范围修正只查舰船级表 |
| `WeaponOPCostModifier` / `FighterOPCostModifier` | **只能 `MutableShipStatsAPI`** | "Only applies when added to MutableShipStatsAPI, not to ShipAPI or the combat engine." | `CombatListenerUtil.getWeaponOPCost / getFighterOPCost` 只查 stats 级表 |
| `DamageDealtModifier` | 造成伤害的舰船（或引擎）| "When adding to a ship, add to the ship dealing the damage, NOT the one taking the damage." | 命中结算先派发引擎级、再派发攻击者舰船级 |
| `DamageTakenModifier` | 承受伤害的舰船（或引擎）| "When adding to a ship, add to the ship taking the damage, NOT the one dealing it." | 同上，目标舰船级 |
| `DamageListener` | 引擎或舰船 | 无特殊限制 | `reportDamageApplied` 引擎级 + 来源舰船级各派发一轮 |
| `HullDamageAboutToBeTakenListener` | 目标舰船（或引擎）| "if true is returned, the hull damage to be taken is negated." | 引擎级 + 舰船级，任一返回 `true` 即抵消该次船体伤害 |

补充两条 javadoc 的**建议**（软措辞，原文为 should）：

伤害族监听器 "Should add this to a ship where possible instead of the engine as performance will be better"（性能更好）；

OP 修正器 "Should be used with care, and ideally only through built-in hullmods"（建议只在内置船插中使用）。

本教程不把这两条升格为必须遵守的规则，但违反它们没有功能错误，只是性能或协调成本。

::: warning 监听器不生效时先核对放置表面

放错层级的监听器**静默不生效、无任何报错**：注册被接受，但派发代码从不查询放错层级的那张表。排查顺序：先对照上表确认注册表面，再检查日志——不要先怀疑注册代码本身。

:::

## 插件生命周期

### 注册：三条路径

1. **settings `plugins` 段（三类注册入口中的第二种）**：`settings.json` 的 `plugins` 段按"唯一 key → 完整类名"登记。该类必须继承 `EveryFrameCombatPlugin`。每场战斗开始时，引擎把 `plugins` 段登记的所有插件类各自实例化（`Class.forName(...).newInstance()`），每个实例包成插件适配器附加为战斗地形——**每场战斗都是全新实例**，跨战斗不共享插件字段。
2. **战斗内 `addPlugin(...)`（三类注册入口中的第三种）**：`engine.addPlugin(plugin)` 先把插件包成插件适配器附加为地形，再在方法内调用 `plugin.init(this)`（与 API 注释 "Plugin has its init method called inside this method." 一致）。
3. **战役 `MissionDefinitionAPI.addPlugin(...)`（战役定义侧的注册入口）**：战役定义的 `MissionDefinitionAPI` 接口也有 `addPlugin(EveryFrameCombatPlugin)`，内部把插件包成同一个插件适配器加入触发列表。三种路径最终都落到"插件适配器持有插件实例、挂在战斗地图上"。

插件适配器是引擎内部的适配层：

- `advance` 把输入事件列表转成 `List<InputEventAPI>` 后透传 `plugin.advance(amount, events)`；
- `renderInWorldCoords` / `renderInUICoords` 原样透传；
- `init` 把当前战斗引擎实例传给 `plugin.init(...)`。

::: info 为什么存在插件适配器这一层

战斗地图的每帧驱动对象是"地形"（terrain）：推进与渲染阶段由地形系统统一遍历调用。插件不是地形，所以三条注册路径都先把插件实例包成插件适配器挂到地形上—— 适配器只做两件事：把输入事件列表转成 `List<InputEventAPI>` 透传给 `advance`，以及原样透传渲染回调。插件作者无需关心地形接口。

:::

### 帧内调用顺序

- 输入：战斗状态处理输入，经战斗地图派发到每个插件适配器的 `processInputPreCoreControls`（引擎对特定异常做了抑制，其余异常包装后抛出）。
- 推进：战斗地图推进时每个地形（含插件适配器）的 `advance` 都被调用。暂停分支同样执行。
- 渲染：战斗地图的 `renderForeground` 等阶段调用插件适配器的 `renderInWorldCoords`。

### init() 已废弃：必须遵守的规则

`CombatEnginePlugin.init` 的 API javadoc 原文：

"Deprecated, not guaranteed to be called before advance() is called for an EveryFrameCombatPlugin."

"Can still be relied on if the EveryFrameCombatPlugin.advance() method checks for any fields being set in init() being null."

由此得到必须遵守的规则：**`advance()` 必须先判 `engine == null`（以及一切依赖 `init()` 设置的字段），不得假设 `init()` 已经执行**。

违反的可观察结果：插件第一次 `advance()` 就抛空指针（日志出现 `Exception`，该帧战斗逻辑中断）；更隐蔽的变体是引擎字段被外层对象传入、`init()` 覆盖时顺序颠倒。

成熟实例的标准写法就是 `init` 存引擎 + `advance` 首行判空。

### 移除与战斗结束清理

- `engine.removePlugin(plugin)`：从战斗地形列表里移除对应插件适配器，此后该插件**不再收到任何回调**。`hasPluginOfClass(Class)` 遍历同一份地形列表做类型查询。
- **战斗结束没有清理回调**：`EveryFrameCombatPlugin` 没有"战斗结束"通知方法。战斗结束收尾时引擎销毁当前战斗实例：内部监听器表、战斗地图（承载插件的对象）、`customData` 全部随之释放，实例被丢弃——插件、引擎级监听器、战斗级共享状态一并释放。**插件与监听器随战斗结束"被统一释放"，而不是"收到通知后自己清理"**；需要在结束时刻做事的插件，必须在 `advance()` 里用 `engine.isCombatOver()` 识别时机（`isCombatOver()` 在胜负判定后、淡出完成前已为 `true`，此时引擎仍存活、回调仍在进行）。

::: caution 禁止跨战斗缓存插件、监听器或引擎引用

插件实例、引擎级监听器、`ShipAPI` 与 `CombatEngineAPI` 都只属于当前这场战斗：旧引擎在战斗结束时被丢弃，战斗外是非 null 空引擎。把它们存进静态字段或生涯侧对象，第二场战斗起引用全部失效且无任何报错。

:::

## 监听器系统

### 三块注册表面与用到时才创建

公开监听器表的统一接口是 `CombatListenerManagerAPI`，三块表面：

`addListener` / `removeListener` / `removeListenerOfClass` / `hasListener` / `hasListenerOfClass` / `getListeners(Class)`。

| 表面 | 取得方式 | 寿命 | 派发的监听器类型 |
|---|---|---|---|
| 引擎级 | `engine.getListenerManager()` | 一场战斗 | 伤害族、部署、OP 修正（stats 另表）……凡 `CombatListenerUtil` 以引擎表为起点的 |
| 舰船级 | `ship.addListener(x)` / `ship.getListenerManager()` | 一艘舰 | 伤害族、范围修正、`AdvanceableListener` |
| 统计级 | `stats.addListener(x)` / `stats.getListenerManager()` | 舰船统计对象 | `WeaponOPCostModifier` / `FighterOPCostModifier` |

引擎级表随引擎创建而创建，恒非 null；

舰船级与统计级表是**用到时才创建**。

舰船与舰船统计对象在第一次 `addListener(...)` 时才创建监听器表，所以 `ship.getListenerManager()` 在第一次 `addListener` 之前返回 null。

必须遵守的规则：**给舰船/统计加监听器直接用 `addListener(...)` / `removeListenerOfClass(...)`，不要先读 `getListenerManager()` 再判空使用**； `AdvanceableListener` 的帧派发本来就带 null 判。

### 派发行为

- 部署事件：战斗舰队管理器自动部署与战情室手动部署都会触发部署事件——内部监听器派发一轮，`CombatListenerUtil.reportFleetMemberDeployed` 派发引擎级 `FleetMemberDeploymentListener`（舰船级分支在 util 里被注释掉，这就是"只能引擎"的实现依据）。
- 伤害族：舰船/导弹命中结算先派发引擎级 `DamageDealtModifier` / `DamageTakenModifier`，再派发攻击者 / 目标舰船级；结算后 `reportDamageApplied` 派发引擎级 + 来源舰船级 `DamageListener`；船体伤害前 `notifyAboutToTakeHullDamage` 聚合引擎级 + 舰船级返回值，任一 `true` 抵消。
- 范围与 OP：`CombatListenerUtil` 的范围修正只查舰船级表，OP 修正只查 stats 级表——与 javadoc 放置规则一一对应。
- 迭代纪律：引擎派发与 `CombatListenerUtil` 都**先拷贝列表再迭代**（`new ArrayList<>(...)`），监听器表内部统一存储、`getListeners(Class)` 返回的是可迭代视图。**建议**：不要在监听器回调内部对同一张表增删（要增删先取快照）；同一场战斗重复注册同一类监听器前用 `hasListenerOfClass` 守卫（这是成熟实例的通用做法）。

### 内部事件监听接口：不是公开 API

引擎内部存在一套内部事件监听接口，主题覆盖成员部署 / 撤退、舰船瘫痪 / 击毁、战机联队击毁、目标点易主、命令下达、成员俘获八个事件主题。

它的方法签名使用**内部类型**（引擎内部舰船、战机联队、战斗命令、部署成员与舰队成员等模型类）。

实现者是引擎内部组件：战斗舰队管理器、部署管理器、战斗群管理器、部队移交管理器。

战斗 UI 面板与教程脚本——它们由引擎内部注册进内部监听器表并派发。

战役"击毁指定舰船"类任务触发器由另一套内部事件接口支撑：引擎在战斗结束判定时查询任务事件的挂起状态。

与公开 API 的关系（硬边界）：

- **内部事件监听接口不在公开 API 编译面中，MOD 代码不能实现或注册它**。可观察结果：编译期报类找不到（公开 API 编译面不含它）；即便通过内部类路径编译通过，它依赖的内部类型与注册表是引擎内部未公开的约定，任何版本都可能变动。
- MOD 侧对应的事件入口是公开的 `com.fs.starfarer.api.combat.listeners`：部署事件 → `FleetMemberDeploymentListener`；伤害 → `DamageDealtModifier` / `DamageTakenModifier` / `DamageListener` / `HullDamageAboutToBeTakenListener`；"战斗结束"本身没有事件，用 `advance()` + `engine.isCombatOver()`。
- 内部接口仅作边界说明，不作为 API 引用。

## 状态归属与清理（必须遵守的规则汇总）

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 插件、监听器及其持有的引擎 / 舰船 / 列表引用**禁止跨战斗缓存**（引擎生命周期单元同一条纪律的插件侧）| 第二场战斗起缓存引用全部失效且无报错（旧引擎已在战斗结束时被丢弃，战斗外引擎是非 null 空引擎）；`isAlive()` / `isEntityInPlay()` 恒为 false |
| 2 | **必须** | 插件在战斗内自移除（`removePlugin`）时，**必须先移除它注册的引擎级监听器**——引擎级监听器不随插件移除 | 插件已停回调，但它注册的 `FleetMemberDeploymentListener` / 伤害监听器仍在被派发（日志、效果继续出现），监听器持有的状态残留到战斗结束 |
| 3 | **必须** | 战斗结束**没有清理回调**；收尾在 `advance()` 里用 `engine.isCombatOver()` 识别 | 把结算 / 清理放在"战斗结束后"的假设回调里 → 那段代码永不执行；依赖 `Global.getCombatEngine()` 在战斗外取值 → 读到空引擎 |
| 4 | **必须** | 监听器按"监听器家族与放置层级"表的层级注册 | 静默不生效、无报错（`FleetMemberDeploymentListener` 挂舰船、`AdvanceableListener` 挂引擎、范围 / OP 修正器挂错表面等）|
| 5 | **必须** | `advance()` 先判 `engine == null`（`init()` 已废弃）| 首次 `advance()` 空指针异常 |
| 6 | **必须** | 状态推进前检查 `engine.isPaused()`；`AdvanceableListener.advance` 回调同样要自己守卫（它的派发不带暂停信息）| 暂停期间状态照常累计（与战斗插件模型、战斗状态读取单元同规）|
| 7 | 建议 | 伤害族监听器优先挂舰船而非引擎（性能）；OP 修正器只在内置船插中使用 | 无功能错误，只有性能或协调成本（javadoc 原文 should）|
| 8 | 建议 | 不要在监听器回调内直接增删同一张监听器表（派发方先拷贝列表）| 特定注册顺序下出现重复派发或 `ConcurrentModificationException`（引擎内部全走拷贝，这个坑只在自定义派发里出现）|

## 完整最小例

在前置单元的 MOD 布局与战斗插件模型之上新增两个类文件（不新增任何资源、不新增任何注册项），包与类名遵循命名约定（`data.scripts.gearin.combat`、`GearIn<Role>`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin               # 前置单元所有；本页不改动
│       ├── GearInGlobalAccess            # 前置单元所有；本页只消费
│       └── combat\
│           ├── GearInCombatStatePlugin   # 前置单元所有；本页只消费
│           ├── GearInCombatLifecyclePlugin  # 本页新增：战斗生命周期插件
│           └── GearInDeploymentListener  # 本页新增：引擎级部署监听器
└── jars\
```

### GearInCombatLifecyclePlugin（战斗生命周期插件）

`BaseEveryFrameCombatPlugin` 子类，承担插件角色：`init` 保存引擎并注册监听器、`advance` 三个前置守卫（判 null / 暂停 / 已收尾）、用 `isCombatOver()` 轮询收尾并自移除。

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗插件与引擎级监听器的生命周期、移除与状态归属。
 *
 * 生命周期事实（0.98a-RC8）：
 * - init() 已废弃：不保证在 advance() 前被调用（CombatEnginePlugin 的 API 注释），
 *   advance() 必须先判 engine == null；
 * - advance() 在暂停时仍会被调用，状态推进前必须先检查 engine.isPaused()（S02-P01）；
 * - 战斗结束没有回调：插件必须在 advance() 里用 engine.isCombatOver() 自行识别收尾时机，
 *   此时引擎仍存活（结束淡出尚未完成，S07-P01）；
 * - 引擎级监听器不随插件移除而注销：插件调用 engine.removePlugin(this) 自移除时，
 *   必须同时把注册到 engine.getListenerManager() 的监听器 removeListener 掉；
 * - 引擎、插件、监听器与舰船引用禁止跨战斗缓存（S06-P03）：本类所有状态
 *   都只属于当前这场战斗，战斗结束由引擎销毁统一释放。
 */
public class GearInCombatLifecyclePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatLifecyclePlugin.class);

    /** 引擎引用。init() 已废弃，advance() 中必须判 null。 */
    private CombatEngineAPI engine;

    /** 本插件在 init() 里注册的引擎级监听器；自移除时必须显式移除。 */
    private final GearInDeploymentListener deploymentListener = new GearInDeploymentListener();

    private boolean finalized = false;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        CombatListenerManagerAPI listeners = engine.getListenerManager();
        if (!listeners.hasListenerOfClass(GearInDeploymentListener.class)) {
            listeners.addListener(deploymentListener);
            LOG.info("GearIn lifecycle init: deployment listener registered");
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // init() 不保证先于 advance() 执行，必须判 null
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）
        }
        if (finalized) {
            return;
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            LOG.info("GearIn lifecycle: total=" + engine.getTotalElapsedTime(false)
                    + " combatOver=" + engine.isCombatOver());
        }
        if (engine.isCombatOver()) {
            finalizeBattle();
        }
    }

    /** 收尾：先移除自己注册的引擎级监听器，再移除插件自身。 */
    private void finalizeBattle() {
        engine.getListenerManager().removeListener(deploymentListener);
        engine.removePlugin(this);
        finalized = true;
        LOG.info("GearIn lifecycle finalize: deployments=" + deploymentListener.deployedCount
                + " listener removed, plugin removed");
    }
}
```

**注册入口**：由既有战斗插件的 `init()` 内调用 `engine.addPlugin(new GearInCombatLifecyclePlugin())` 挂入当前战斗（注册片段见下文）。

### GearInDeploymentListener（引擎级部署监听器）

`FleetMemberDeploymentListener` 实现，承担监听器角色：统计并记录敌我成员部署事件，`deployedCount` 是本场战斗的状态。

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener;

/**
 * 引擎级部署监听器：FleetMemberDeploymentListener 只能注册到引擎，
 * 注册到舰船不会生效（CombatListenerUtil 只查引擎级表）。
 * 部署计数是本场战斗的状态，随战斗结束与监听器一起释放。
 * 绑定位置：由 GearInCombatLifecyclePlugin.init() 注册到 engine.getListenerManager()。
 */
public class GearInDeploymentListener implements FleetMemberDeploymentListener {

    private static final Logger LOG = Global.getLogger(GearInDeploymentListener.class);

    int deployedCount = 0;

    @Override
    public void reportFleetMemberDeployed(DeployedFleetMemberAPI member) {
        deployedCount++;
        ShipAPI ship = member.getShip();
        LOG.info("GearIn deployed: ally=" + member.isAlly()
                + " fighterWing=" + member.isFighterWing()
                + " ship=" + (ship == null ? "null" : ship.getHullSpec().getHullId()));
    }
}
```

**注册入口**：由 `GearInCombatLifecyclePlugin.init()` 注册到 `engine.getListenerManager()`（引擎级表面）—— `hasListenerOfClass` 去重守卫 + `addListener(...)`，见上方插件代码。

对应关系：

- `init` 里的注册：`engine.getListenerManager().hasListenerOfClass(...)` 守卫 + `addListener(...)`（成熟实例的注册做法），日志行 `GearIn lifecycle init:` 是注册成功的现场证明。
- `advance` 的三个前置守卫：`engine == null`（规则 5）、`engine.isPaused()`（规则 6）、`finalized`（防止同帧重复收尾）。
- `advance` 的收尾识别：`engine.isCombatOver()` 为 `true` 时调用 `finalizeBattle()`——战斗结束没有回调，收尾必须在推进循环里自行识别（规则 3）。
- `finalizeBattle`：**先** `removeListener`（引擎级监听器不随插件移除，规则 2），**再** `removePlugin(this)`（此后插件不再收到回调）。
- `GearInDeploymentListener`：引擎级 `FleetMemberDeploymentListener`（必须遵守的放置规则：只能引擎）；`deployedCount` 是监听器自己持有的战斗级状态，随战斗结束释放。
- `IntervalUtil(2f, 2f)`：固定 2 秒节奏的周期日志（与战斗状态读取单元同款）。

注册片段（工程片段——本页不改动前置单元所有文件，只演示调用）：

```java
    // 已有战斗插件（战斗插件模型单元的拥有者类）的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInCombatLifecyclePlugin());
        // ... 原有逻辑
    }
```

::: tip 注册前的去重守卫是通用做法

`GearInCombatLifecyclePlugin.init()` 里先 `hasListenerOfClass(GearInDeploymentListener.class)` 再 `addListener(...)` ，避免同一场战斗重复注册同类监听器；给舰船 / 统计加监听器同样直接用 `addListener(...)`，不要先读 `getListenerManager()` 判空。

:::

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元的编译链路）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含（可用 `jar tf` 核对）：

- `data/scripts/gearin/combat/GearInCombatLifecyclePlugin.class`
- `data/scripts/gearin/combat/GearInDeploymentListener.class`

本页两段源码与磁盘 fixture 逐字一致，fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

以本页两个类为对象，把「构建 → 部署 → 收尾 → 跨战斗」整条链路走一遍。

前置数据：

- MOD 布局与编译链路（S06-P01）、战斗插件接入与注册片段（S02-P01）、`GearIn_test` 测试战役（S03-P01）；
- 本页两个类，随编译进入 `jars\GearIndustry.jar`。

操作：

1. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含两个类的 `.class`。
2. 战斗初始化：进入 `GearIn_test` 一场战斗，`starsector.log` 出现 `GearIn lifecycle init: deployment listener registered`，没有 `gear_in` 引起的 `ERROR`。若已有战斗注册片段则每场战斗出现一次（每场战斗都是新实例，必须遵守的规则：不跨战斗缓存）。
3. 周期报告：战斗（未暂停）中每 2 秒出现一行 `GearIn lifecycle: total=<秒数> combatOver=false`，`total` 稳步增长；暂停约 10 秒再恢复，暂停期间没有输出（守卫生效）。
4. 部署事件：任意成员部署时（敌方 AI 自动部署、用户从后备手动部署都算）出现一行 `GearIn deployed: ally=... fighterWing=... ship=<hullId>`；每场战斗的 `deployments=` 计数等于该场部署日志条数（监听器状态随战斗归属）。
5. 收尾与移除：清空一侧舰队，胜负判定后（结束动画期间）出现 `GearIn lifecycle finalize: deployments=<n> listener removed, plugin removed`，此后战斗淡出期间**不再有**周期报告与部署日志——插件已 `removePlugin`、监听器已 `removeListener`（收尾不依赖任何"战斗结束回调"，规则 3）。
6. 跨战斗实例：退出战斗再进一场，`init` 日志重现、`deployments=` 从头计数——每场战斗新实例，旧引用全部作废（规则 1）。
7. 故障注入：临时注释掉 `finalizeBattle()` 里的 `engine.getListenerManager().removeListener(deploymentListener);`——战斗结束前的淡出窗口内再次部署仍会打印 `GearIn deployed:`（对应故障 2 的残留）；改回后确认恢复。

通过信号：操作 1–6 的可观察结果全部符合，操作 7 的注入与恢复符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileS07p04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/combat/GearInCombatLifecyclePlugin.class` 与 `data/scripts/gearin/combat/GearInDeploymentListener.class`。
3. **战斗初始化**：进入 `GearIn_test` 测试战役一场战斗，`starsector.log` 出现一行
```text
GearIn lifecycle init: deployment listener registered
```
没有 `ERROR`。若已有战斗注册片段则每场战斗出现一次（每场战斗都是新实例，必须遵守的规则：不跨战斗缓存）。
4. **周期报告**：战斗（未暂停）中每 2 秒出现一行
```text
GearIn lifecycle: total=<秒数> combatOver=false
```
`total` 稳步增长（暂停时守卫生效、无输出）。
5. **部署监听器**：任意成员部署时（敌方 AI 自动部署、用户从后备手动部署都算）出现一行
```text
GearIn deployed: ally=false fighterWing=false ship=<hullId>
```
每场战斗的 `deployments=` 计数等于该场部署日志条数（监听器状态随战斗归属）。
6. **战斗结束收尾**：胜负判定后（结束动画期间）出现一行
```text
GearIn lifecycle finalize: deployments=<n> listener removed, plugin removed
```
此后**不再有**周期报告与部署日志——插件已 `removePlugin`、监听器已 `removeListener`；战斗淡出与结算照常进行（收尾不依赖任何"战斗结束回调"，规则 3）。
7. **跨战斗实例**：退出战斗再进一场，日志再次出现 `GearIn lifecycle init:`，且 `deployments=` 从头计数——每场战斗新实例，旧引用全部作废（规则 1）。

静态自查：`GearInCombatLifecyclePlugin` 与 `GearInDeploymentListener` 的源码与磁盘 fixture 逐字一致（包括 import 顺序与注释）。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.combat`、类 `GearInCombatLifecyclePlugin` / `GearInDeploymentListener`）。

## 实例驱动故障分析

1. **依赖 `init()` 初始化字段**。触发：`advance()` 直接使用 `engine` 字段，不在首行判 null。可观察：`init()` 已废弃（`CombatEnginePlugin` API 注释原文），不保证先于 `advance()` 执行；首次回调即空指针，日志出现 `Exception`，该帧插件逻辑中断。修正：`advance()` 首行 `if (engine == null) return;`（标准做法）；初始状态在构造器或注册点设置。
2. **插件自移除后监听器残留**。触发：`finalizeBattle()` 只调 `engine.removePlugin(this)`，不调 `removeListener`。可观察：插件停回调后，战斗结束前的部署事件仍打印 `GearIn deployed:`——`removePlugin` 只从地形列表移除适配器，引擎级监听器表不受影响，监听器继续被派发、其状态残留到战斗结束。修正：**先** `engine.getListenerManager().removeListener(listener)`，**再** `removePlugin(this)`（规则 2）。
3. **把收尾放在"战斗结束后"**。触发：假设存在战斗结束回调（例如在别处注册一个监听器等"结束事件"）。可观察：收尾代码永不执行——公开 API 没有战斗结束事件；引擎结束时对监听器表整体丢弃，不通知任何插件或监听器。修正：在 `advance()` 里用 `engine.isCombatOver()` 识别收尾（语义：胜负判定后、淡出完成前已为 `true`，引擎仍存活）；需要跨战斗持久的结果写进生涯侧数据。
4. **跨战斗缓存插件 / 监听器 / 舰船引用**。触发：把插件实例、引擎级监听器或 `ShipAPI` 存进静态字段或生涯侧对象，下一场战斗继续用。可观察：第二场战斗起引用全部失效且无报错——旧引擎已在战斗结束时被丢弃，战斗外是非 null 空引擎；旧舰的 `isAlive()` 恒为 false。修正：每场战斗从 `init()` / 注册点重新获取，禁止缓存（规则 1）。
5. **监听器放错层级**。触发：`ship.addListener(fleetMemberDeploymentListener)`、`engine.getListenerManager().addListener(advanceableListener)`，或把 `WeaponRangeModifier` / `WeaponOPCostModifier` 挂到引擎。可观察：**静默不生效、无任何报错**——部署派发只查引擎级表（util 的舰船分支被注释）、`AdvanceableListener` 只在舰船推进时派发、范围修正只查舰船级表、OP 修正只查 stats 级表。修正：按"监听器家族与放置层级"表放，别猜（规则 4）。
6. **暂停期间状态推进**。触发：`advance()` 或 `AdvanceableListener.advance` 里没有暂停守卫。可观察：暂停 10 秒后解除，自己的计数 / 计时多出约 10 秒——战斗没推进，数值已偏（`AdvanceableListener` 的派发不携带暂停信息，舰船推进只保证回调，守卫是监听器自己的责任）。修正：回调内检查 `engine.isPaused()`。
7. **误用内部事件监听接口**。触发：试图 import 引擎内部的事件监听接口并实现 / 注册。可观察：编译失败（该接口不在公开 API 编译面）；即使绕过编译，其方法签名依赖内部类型与内部注册表，属于引擎内部未公开的约定，任何版本都可能变动。修正：用公开的 `com.fs.starfarer.api.combat.listeners`（部署 → `FleetMemberDeploymentListener`，伤害 → 伤害族监听器；"战斗结束"没有事件，用 `isCombatOver()` 轮询）。
8. **在回调里直接增删监听器表**。触发：某个监听器回调内对同一引擎表 `addListener` / `removeListener`。可观察：引擎派发都先拷贝列表（`CombatListenerUtil` 全部 `new ArrayList<>(...)`），大多数场景侥幸不炸；一旦自定义派发直接遍历 `getListeners()` 返回的视图，出现重复派发或 `ConcurrentModificationException`。修正：增删前先取快照，或延后到下一次 `advance()`（建议级，规则 8 之外）。

## 总结

本节把战斗域的生命周期讲透：插件三条注册路径统一落到插件适配器挂在地形上、`init()` 已废弃所以 `advance()` 必须判空、战斗结束没有回调而靠 `isCombatOver()` 自行收尾。

公开监听器三块表面与必须遵守的放置规则、内部事件监听接口的边界，以及插件 / 监听器 / 引擎引用只属于当前这场战斗的归属纪律。

从逻辑上展望：战斗渲染域还有一类挂接在战斗引擎上的分层渲染插件，其 `cleanup()` 与本节的收尾纪律互为对照；舰队成员在生涯侧的完整身份与生命周期，则是归属纪律在战役域的延伸—— "随所属对象销毁统一释放、不自行等待通知、禁止跨所属对象缓存"这套模型，会继续作为后续生命周期问题的统一基线。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧战斗回调接口（`processInputPreCoreControls` / `advance` / `renderInWorldCoords` / `renderInUICoords`），`extends CombatEnginePlugin`。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：插件父接口，`init` 已废弃（不保证先于 `advance()` 被调用）。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：五个回调的空实现基类。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：当前战斗引擎接口，提供 `addPlugin` / `removePlugin` / `hasPluginOfClass` / `getListenerManager` / `getCustomData`。
- `com.fs.starfarer.api.combat.ShipAPI` 与 `com.fs.starfarer.api.combat.MutableShipStatsAPI`：舰船级 / 统计级监听器表的取得与增删查。
- `com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI`：监听器表统一接口（`addListener` / `removeListener` / `removeListenerOfClass` / `hasListener` / `hasListenerOfClass` / `getListeners`）。
- `com.fs.starfarer.api.combat.listeners.*`：公开监听器接口族，各类的必须遵守的放置规则见接口 javadoc。
- `com.fs.starfarer.api.combat.listeners.CombatListenerUtil`：引擎内部派发入口（伤害 / 范围 / OP / 部署）。
- `com.fs.starfarer.api.combat.DeployedFleetMemberAPI`：部署成员信息（`getMember` / `isFighterWing` / `getShip` / `isAlly` / `isStationModule`）。
- `com.fs.starfarer.api.mission.FleetSide`：舰队侧枚举（`PLAYER` / `ENEMY`）。
- `com.fs.starfarer.api.util.IntervalUtil`：定期间隔触发工具（本页示例用于周期日志）。
- `data/config/settings.json`：原版配置，`plugins` 段按"唯一 key → 完整类名"登记每场战斗实例化的插件。
