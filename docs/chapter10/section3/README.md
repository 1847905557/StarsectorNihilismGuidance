# 生涯插件：注册、优先级与生命周期

本节把生涯插件（`CampaignPlugin`）讲透：注册与注销入口、`PluginPick` + `PickPriority` 的竞争裁决、"谁在什么时候被咨询" 的调用点，并覆盖 transient / 非 transient 两种生命周期与存档边界。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出生涯插件的注册与注销入口（`SectorAPI.registerPlugin` / `unregisterPlugin(String pluginId)`）、`getId()` 在去重与注销中的作用，以及 "未注册、未覆写 `getId()`、id 撞车" 三种情况各自的可观察结果；
- 背出 7 级 `PickPriority` 阶梯（`CORE_GENERAL` < `MOD_GENERAL` < `CORE_SET` < `MOD_SET` < `CORE_SPECIFIC` < `MOD_SPECIFIC` < `HIGHEST`）与竞争裁决规则（取 `ordinal` 最高者、返回 `null` 即放弃、同优先级结果未定义），并与战斗 AI picker 使用的同一枚举对照；
- 说出每个 picker hook 的调用时机与调用点（实体交互对话、通信消息动作图标、战斗创建、自动结算轮、重生、自动装配、舰队模块化 AI、能力 AI、stub 转换），以及 6 个 facts 方法 "每次 `getMemory()` 都会重新执行" 的触发机制；
- 说出 transient 与非 transient 插件的存档边界与注册时机：transient 插件**必须**在每次读档后重新注册（`onGameLoad`）、不能挂一次性 persistent data 守卫；非 transient 插件随存档保存、可一次注册，并解释成熟实例的 `CLASS_MARK` 守卫为什么只适用于非 transient 插件；
- 从零写一个 transient 生涯插件（含最小交互对话框）并注册，用游戏内对话内容与 `starsector.log` 验证 picker 命中、优先级生效与 facts 写入；
- 从症状定位八类典型故障：插件消失、重复咨询、抢走原版对话、点击实体无对话、picker 抛异常、facts 递归、同优先级竞争、序列化问题。

## 前置知识

本节假设您已完成以下单元的学习：

- MOD 目录结构、`starsector.log` 与编译链路（C01-S01、C06-S01）
- 注册入口与回调心智模型（C02-S01）
- 势力 `gear_industry` 的定义 —— 本节示例用它做触发条件，只消费不定义（C03-S02）
- 新档生成入口 —— 累计工程为齿轮工业行星提供市场实体，是本节示例的可观察对象来源（C03-S03）
- `modPlugin` 生命周期回调与 persistent data `CLASS_MARK` 守卫 —— 本节的注册调用点挂在 `onGameLoad` 里，但**不用**守卫（C06-S02）
- `Global.getSector()` 的 null 语义 —— 注册必须发生在生涯引擎建立之后（C06-S03）
- 脚本注册与 persistent data 守卫 —— 本节的 transient 插件注册与之形成对照（C08-S01）
- 生涯事件监听器 —— 生涯插件与监听器是两条不同的扩展通道，本节末尾有对照（C10-S02）
- Rules 与记忆 ——facts 写入的 `$` 前缀记忆变量将由规则系统消费（C11-S03、C11-S04）

## 术语

按术语约定，API 名保留源码大小写。本节首次完整讲解两个页面级术语：

- **生涯插件（CampaignPlugin）**：API 名保留 `CampaignPlugin`。
  - 实现 `CampaignPlugin` 接口（或继承 `BaseCampaignPlugin`）并在生涯引擎注册的对象。
  - 它不随帧推进（那是 `EveryFrameScript`，见前置知识），而是被动接受两类咨询：picker 咨询（`pickXxx`，在生涯事件发生时被调用，决定 "由哪个插件实例处理"）与 facts 咨询（`updateXxxFacts`，在记忆被读取时被调用，负责写规则事实）。
- **picker（选择器）**：英文保留。
  - 对一组已注册的生涯插件逐个调用同一个 `pickXxx` 方法、收集非 `null` 的 `PluginPick` 并按 `PickPriority` 裁决出唯一获胜者的机制。
  - 返回 `null` 表示该插件放弃本次选择权。

## 文件与 API 链路

### 1. 注册与注销（登记表）

| 环节 | 文件或 API | 行为 |
|---|---|---|
| 注册 | `SectorAPI.registerPlugin(CampaignPlugin)`（`com.fs.starfarer.api.campaign.SectorAPI`）| 写入插件登记表（`addPlugin`，去重规则见下行）|
| 去重 | 插件登记表 `addPlugin` | 若 `getId()` 非 `null`：先 `removePlugin(getId())` 再追加 ——**后注册的同 id 插件静默顶掉先注册者**；`getId()` 为 `null` 则不去重、直接追加 |
| 注销 | `SectorAPI.unregisterPlugin(String pluginId)`（`com.fs.starfarer.api.campaign.SectorAPI`）| 按 id 移除**第一个**匹配项；id 不匹配则什么都不做 |
| 存储 | 插件登记表 | 非 transient 插件随存档序列化、读档恢复；transient 插件写档时被过滤、不进存档 ——**这就是 transient 与非 transient 的存档边界** |
| 竞争 | 插件登记表的裁决逻辑 | 所有 picker 共用的裁决器（见 "竞争裁决"）|
| 聚合入口 | `SectorAPI.getPluginPicker()`（`com.fs.starfarer.api.campaign.SectorAPI`，返回 `PluginPickerAPI`）| 公开暴露 6 个聚合 picker：`pickFleetStubConverter` ×2、`pickImmigrationPlugin`、`pickAICoreAdminPlugin`、`pickAICoreOfficerPlugin`、`pickFleetInflater`（`com.fs.starfarer.api.campaign.PluginPickerAPI`）；其余 picker 由引擎内部调用 |

注册的合法时机：**必须**在生涯引擎建立之后 ——`onGameLoad` / `onNewGame` 回调里（`Global.getSector()` 已可用）。

在 `onApplicationLoad` 里调用 `registerPlugin` 会先对 `getSector()` 解引用，直接 NPE（`Global.getSector()` 的 null 语义见前置知识）。

### 2. 竞争裁决：PluginPick 与 PickPriority

每个 picker 返回 `PluginPick<T>`（`com.fs.starfarer.api.PluginPick`：公开字段 `plugin` 与 `priority`）或 `null`。裁决器 `getPriorityPlugin`（插件登记表）的精确语义：

1. 按注册顺序对**全部**已注册插件调用本次 picker 的 `pick` 方法，收集所有非 `null` 的 `PluginPick`。**没有 try/catch**—— 任何插件抛出的异常直接沿调用点冒泡。
2. 一个都没收集到 → 返回 `null`（本次选择没有任何插件接手）。
3. 只有一个 → 直接返回它的 `plugin`。
4. 多个 → 按 `priority.ordinal()` 升序稳定排序，返回**最后一个**（即 `ordinal` 最高者）。

关键结论：

- **返回 `null` = 放弃**：不参与竞争，把机会留给别的插件。这是每个 picker 的默认姿态（`BaseCampaignPlugin` 的全部 picker 默认返回 `null`）。
- **`PluginPick` 的 `plugin` 不能是 `null`**：裁决器只检查 `PluginPick` 非 `null` 就让它参选，获胜后调用点拿到的是 `.plugin`。
  - 一个 `plugin == null` 的高优先级选择会**顶掉**其他插件的合法选择，而调用点多数把它当作 "无人处理"—— 点击实体后什么都不发生，连原版对话都不开。

::: warning 同优先级并列的结果未定义

API javadoc 原文（`CampaignPlugin`）明确写 "what gets picked when multiple plugins have the same priority is undefined"。

RC8 的实现是稳定排序（`Collections.sort`）+ 取最后一项，因此并列时**列表靠后者胜出**、列表顺序取决于注册时机 —— 这是实现观察，不是约定，**禁止依赖**（见 "规则强度"）。

:::

### 3. 优先级阶梯（PickPriority）

`PickPriority` 是 `CampaignPlugin` 的嵌套枚举，共 7 级（`com.fs.starfarer.api.campaign.CampaignPlugin`；战斗 AI picker 用到的也是同一个枚举，这里给出生涯侧的完整语义）：

| 优先级 | ordinal | javadoc 语义（归纳）|
|---|---:|---|
| `CORE_GENERAL` | 0 | 只应由核心代码使用；MOD 用这一级 "可能根本不会被选中" |
| `MOD_GENERAL` | 1 | MOD 整体替换某项生涯功能（如 "与所有行星的交互"）|
| `CORE_SET` | 2 | 只应由核心代码使用 |
| `MOD_SET` | 3 | 处理某一类情形（如 "与所有丛林行星的交互"）；覆盖一切 `_GENERAL` |
| `CORE_SPECIFIC` | 4 | 核心代码用于特定遭遇（特殊行星、特殊舰队）|
| `MOD_SPECIFIC` | 5 | MOD 用于特定遭遇（某支特定舰队的专属对话）|
| `HIGHEST` | 6 | 绝对最高；无充分理由不要用（MOD 整合包用它解决内部冲突）|

裁决只看 `ordinal`，**层级名称只是语义约定**：`MOD_SET`(3) 与 `CORE_SET`(2) 竞争时 `MOD_SET` 胜出，但如果两个 MOD 都用 `MOD_SPECIFIC` 处理同一目标，谁生效未定义。

原版核心插件（`CoreCampaignPluginImpl`，`com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`）几乎全部返回 `CORE_GENERAL`，只有教程重生对话框用 `MOD_SPECIFIC`。

MOD 用 `MOD_GENERAL` 及以上就能稳定覆盖原版，用 `CORE_GENERAL` 则与原版并列、结果未定义。

### 4. 全部 hook 速查（picker 19 + facts 6 + 身份 2）

`CampaignPlugin` 共 27 个方法：`getId()`、`isTransient()`、19 个 picker、6 个 facts。

`BaseCampaignPlugin` 为全部方法提供默认实现（`getId()` → `null`、`isTransient()` → `true`、picker → `null`、facts → 空操作）。

只覆写需要的即可（`com.fs.starfarer.api.campaign.BaseCampaignPlugin`）。

**Picker（19 个）**—— 调用时机与内部实现定位：

| picker | 触发时机 | 调用点（内部实现）|
|---|---|---|
| `pickInteractionDialogPlugin(SectorEntityToken)` | 玩家点击实体（行星/空间站/跳跃点/舰队等）打开交互对话框；舰队遭遇时打开遭遇对话 | 生涯状态：`showInteractionDialog`、`startEncounterInvolvingPlayerFleet` |
| `pickInteractionDialogPlugin(Object, SectorEntityToken)` | 点击通信消息的动作图标 | 通信消息列表、情报（intel）列表 |
| `pickBattleCreationPlugin(SectorEntityToken)` | 进入战斗前创建战场 | 生涯状态 |
| `pickBattleAutoresolverPlugin(BattleAPI)` | 每轮自动结算（`doAutoresolveRound`）| `Battle.doAutoresolveRound` |
| `pickRespawnPlugin()` | 玩家舰队覆灭后的 shuttle 对话框 | 生涯状态：`showShuttleDialog` |
| `pickAutofitPlugin(FleetMemberAPI)` | 整备界面自动装配；`member` 为 `null` 表示从舰队界面分配闲置军官 | 整备界面：自动装配与批量装配对话框 |
| `pickAssignmentAIModule` / `pickStrategicAIModule` / `pickTacticalAIModule` / `pickNavigationAIModule` | 生涯舰队创建模块化 AI 时（每个舰队一次）| 创建舰队模块化 AI（`ModularFleetAIAPI`）时；返回 `null` → 回退原版模块（如默认导航模块）|
| `pickAbilityAI(AbilityPlugin, ModularFleetAIAPI)` | 舰队首次推进到某项能力时才**咨询一次**并缓存；返回 `null` → 该能力此后不再咨询 | 舰队模块化 AI（`ModularFleetAIAPI`）推进时（咨询结果缓存）|
| `pickStubConverter(FleetStubAPI)` / `pickStubConverter(CampaignFleetAPI)` | 舰队实体与 "休眠 stub" 相互转换时 | `FleetStubAPI` 创建与相互转换时 |
| `pickReputationActionResponsePlugin(Object, String)` / `(Object, PersonAPI)` | 玩家声望变动（`adjustPlayerReputation`）时 | 生涯引擎 |
| `pickImmigrationPlugin(MarketAPI)` | 殖民地人口/移民计算 | 经 `PluginPickerAPI` 由经济侧消费，内部实现无调用点 |
| `pickAICoreAdminPlugin(String)` / `pickAICoreOfficerPlugin(String)` | 给殖民地/舰队分配 AI 核心时 | 经 `PluginPickerAPI` 由 UI 侧消费（插件登记表；注意：战斗中（COMBAT 状态）AI 核心军官有空实现回退）|
| `pickFleetInflater(CampaignFleetAPI, Object)` | 舰队生成时填充成员与装配 | 经 `PluginPickerAPI` 消费（详细讲解在后续专门页面，本节只登记入口）|

**Facts（6 个）**—— 触发机制：**每次调用对应对象的 `getMemory()` 都会对所有插件重新执行一次**（API javadoc 原文），返回值写进传进来的 `MemoryAPI`：

| facts 方法 | 触发点（内部实现的 `getMemory()`）|
|---|---|
| `updateGlobalFacts(MemoryAPI)` | 生涯引擎 `getMemory` |
| `updateEntityFacts(SectorEntityToken, MemoryAPI)` | 实体基类 `getMemory` |
| `updateMarketFacts(MarketAPI, MemoryAPI)` | `Market.getMemory`（含行星条件市场）|
| `updateFactionFacts(FactionAPI, MemoryAPI)` | `Faction.getMemory` |
| `updatePersonFacts(PersonAPI, MemoryAPI)` | `Person.getMemory` |
| `updatePlayerFacts(MemoryAPI)` | 玩家角色数据 |

原版核心插件用 facts 写全部 `$` 前缀规则变量（`CoreCampaignPluginImpl.updateEntityFacts` 等）：实体 tag、`$name`、`$relValue`、 `$marketSize`……**每次 `getMemory()` 重写**，所以规则系统读到的永远是新鲜值。

`getMemoryWithoutUpdate()` 是绕过 facts 重写的只读路径（`SectorAPI` 等记忆持有对象均提供），在 facts 回调里读记忆**必须**用它或直接用传入的 `memory` 参数（见 "规则强度"）。

::: caution facts 回调内禁止调用会再次触发 facts 的 `getMemory()`

典型：`updateGlobalFacts` 内调 `sector.getMemory()`、`updateEntityFacts` 内调 `entity.getMemory()`。

每次 `getMemory()` 都会重跑全插件对应 facts，自我触发构成无限递归 → `StackOverflowError`。

读记忆用传入的 `memory` 参数或 `getMemoryWithoutUpdate()`（见 "规则强度"）。

:::

### 5. 生命周期与存档边界（transient / 非 transient）

`CampaignPlugin.isTransient()` 的 javadoc 定义了两种生命周期：

- **transient（返回 `true`，`BaseCampaignPlugin` 默认值）**：插件数据不进存档（`writeReplace` 写档前过滤），**必须**在每次读档后重新注册 ——javadoc 原文："it needs to be re-added to the game every time (in ModPlugin.onGameLoad())"。
  - 注册入口是 `onGameLoad(boolean)`（新档与读档共用，见前置知识）。
- **非 transient（返回 `false`）**：插件实例随存档保存，可以只注册一次（`onNewGame`，或 `onGameLoad` 里的 `CLASS_MARK` 守卫内 —— 成熟实例正是把 `registerPlugin` 放进前置单元的首次进入守卫内）。代价：实例字段随存档序列化（XStream 边界），且卸载 MOD 后存档里残留实例。

::: caution transient 插件的注册禁止挂进一次性守卫

守卫在新档首次进入时打开并完成注册，首次自动存档后 transient 实例被 `writeReplace` 过滤，下次读档时守卫已关闭、不再注册 —— 插件从此永久消失且无任何报错。这是与脚本注册策略最显著的差异：脚本要守卫、transient 插件不能守卫（见 "规则强度"）。

:::

::: info 为什么默认 transient

javadoc 明确建议默认 transient：transient 数据不进存档，卸载 MOD 不留残留，便于在既有存档上增删 MOD。

原文："Plugins should be transient unless they need to save data, to improve the ability to add/remove mods from an existing game."

本节示例采用 transient 模式。

:::

`getId()` 与去重的联动：transient 插件每次读档重新注册时，若 `getId()` 返回稳定非 `null` 值，`addPlugin` 先按 id 移除旧实例再追加 ——**重复注册是幂等的**（不会叠加）。

若返回 `null`（未覆写，`BaseCampaignPlugin` 默认），每次读档后新旧实例并存，picker 被逐个咨询、facts 重复执行。

### 6. 另一种机制：GenericPluginManagerAPI（对照）

`SectorAPI.getGenericPlugins()`（`com.fs.starfarer.api.campaign.SectorAPI`）返回 `GenericPluginManagerAPI`。

后者的完整类型名是 `com.fs.starfarer.api.campaign.GenericPluginManagerAPI`；它按 "类 + 参数" 注册通用插件（`addPlugin(plugin, isTransient)`）。

插件实现 `GenericPlugin.getHandlingPriority(Object params)` 返回 **int** 优先级（负数 = 不处理），使用方用 `pickPlugin(Class, params)` 取最高分者。

它与 `CampaignPlugin` 是**两套独立机制**：优先级是 int 常量（`CORE_GENERAL=0` … `MOD_SUBSET=300` … `HIGHEST=Integer.MAX_VALUE`，注意这里叫 `SUBSET` 不是 `SET`）。

注册表也不同（`SectorAPI.getGenericPlugins()` 与 `registerPlugin` 互不相通）。

本节示例不使用它，只在排查 "注册了却从未被调用" 时把它与 `CampaignPlugin` 区分开。

## 规则强度

**必须**（违反后的可观察结果写在括号里）：

1. **picker 无法处理时必须返回 `null`**（API javadoc 每个 picker 的原文："Return null if this CampaignPlugin implementation doesn't provide one."）。
   - 返回 "占位实例" 或 `plugin == null` 的 `PluginPick` → 顶掉低优先级插件的合法选择后自身不可用：`showInteractionDialog` 拿到 `null` 插件直接返回 false（**点击实体什么都不发生，连原版对话都不开**，生涯状态）。
2. **picker 内不得抛出未处理异常**。
   - 裁决器 `getPriorityPlugin` 没有 try/catch（插件登记表），异常沿调用点冒泡 → 生涯流程中断、UI 报错（日志 `ERROR` + 异常堆栈指向本插件的 picker）。
   - 无法处理就返回 `null`，不要抛。
3. **transient 插件必须在每次 `onGameLoad` 重新注册**（javadoc 原文 "needs to be re-added to the game every time (in ModPlugin.onGameLoad())"）。
   - 不注册 → 读档后插件缺失、picker 不响应、facts 不写，**无任何报错**。
4. **transient 插件的注册禁止挂进 persistent data 一次性守卫**（机制推导：守卫关闭后不再注册，而 transient 实例不被存档保留）。
   - 违反 → 新档一切正常，首次读档后插件永久消失且无报错（这是与脚本注册策略最显著的差异 —— 脚本要守卫、transient 插件不守卫）。
5. **返回的 `PluginPick` 必须携带非 `null` 插件实例**（裁决器只检查 `PluginPick` 非 null，调用点只检查取出的 plugin）。
   - 违反 → 高优先级 "废票" 顶掉其他 MOD 的合法选择（见第 1 条的可观察结果）。
6. **注册必须发生在生涯引擎建立之后**（`onGameLoad` / `onNewGame`）。
   - 在 `onApplicationLoad` 注册 → `Global.getSector()` 为 `null`，NPE 启动中止。
7. **facts 回调内禁止调用会再次触发 facts 的 `getMemory()`**（典型：`updateGlobalFacts` 内调 `sector.getMemory()`、`updateEntityFacts` 内调 `entity.getMemory()`）。
   - 每次 `getMemory()` 都会重跑全插件对应 facts（生涯引擎），自我触发构成无限递归 → `StackOverflowError`。
   - 读记忆用传入的 `memory` 参数或 `getMemoryWithoutUpdate()`。
8. **禁止依赖同优先级并列的结果**（API javadoc 原文 "what gets picked when multiple plugins have the same priority is undefined"）。
   - RC8 实现是稳定排序取列表靠后者，但这是实现观察、非约定 → 与其他 MOD 同层竞争时谁生效不保证（版本升级可能反转）。
   - 要保证自己生效，用明确的层级差（MOD 至少 `MOD_GENERAL`）。

**建议**（API javadoc 用 should 表达；违反的可观察结果写在括号里）：

- **覆写 `getId()` 返回稳定唯一值**（javadoc："should be unique. Can be null, but shouldn't."）。返回 `null` → 注册不去重，transient 插件每次读档后实例累积，picker 被咨询多次、facts 重复写入（日志成双）。
- **插件 id 全局唯一、带 MOD 前缀**（如 `GearIn_CampaignPlugin`）。id 撞车 → 后注册的同 id 插件静默顶掉先注册者（去重按 id 全局生效，跨 MOD 也如此）。
- **默认 `isTransient()` 返回 `true`**（javadoc："should be transient unless they need to save data"）。一律返回 `false` → 插件实例随存档序列化（XStream 边界风险、存档体积增长、卸载 MOD 后残留），本可瞬态的状态被意外持久化。
- **facts 写入的变量过期时间设为 `0`**（javadoc："Any variables set here should have an expiration time of 0, since this method will be called every time the getMemory() method is called"）。
  - 非 0 过期 → 事实滞留，规则系统读到陈旧值、记忆被污染。
- **facts 键使用 MOD 专属前缀**（javadoc："Mod-added facts should have their variable names use a mod-specific prefix to avoid conflicts"）。无前缀 → 与原版 `$name`、`$relValue` 等键或别的 MOD 键互相覆盖。
- **priority 按 javadoc 语义层级选择**（`MOD_GENERAL` 整体替换 / `MOD_SET` 一类情形 / `MOD_SPECIFIC` 特定遭遇 / `HIGHEST` 慎用）。用错层级 → 过度覆盖（所有对话被换成自己的）或覆盖不足（被更高优先级插件顶掉）。
- **picker 与 facts 里不做重活**（机制事实：pick 发生在对话打开、战斗创建等交互路径上；facts 在每次 `getMemory()` 时对全部插件重跑）。重活 → 卡顿、频繁重复执行。

## 完整最小例

本节示例在累计工程的既有布局与 `GearInModPlugin`（前置单元）之上新增两个同包类，位于 `src\data\scripts\gearin\campaign\`，不新增任何数据文件、除注册调用点片段外不新增注册项：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInCampaignPlugin
│       └── GearInInteractionDialog
└── jars\
```

### GearInCampaignPlugin（CampaignPlugin 角色）

**角色**：生涯插件 —— 继承 `BaseCampaignPlugin`，只覆写 `getId` / `isTransient` / `pickInteractionDialogPlugin` / `updateGlobalFacts`。

**绑定**：注册表（`SectorAPI.registerPlugin`，注册调用点片段见下）。 `isTransient()` 返回 `true`，数据不进存档，必须由调用点在每次读档后重新注册；`getId()` 返回稳定非 `null` 值，重复注册被按 id 去重、只会替换旧实例。

**picker 与 facts**：`pickInteractionDialogPlugin` 对 "齿轮工业" 势力的两类实体命中：

- 舰队（`instanceof CampaignFleetAPI` 分支）
- 市场实体（`getMarket()` 分支）

命中时返回 `new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC)`。

`MOD_SPECIFIC`(5) > 原版 `CORE_GENERAL`(0)，必胜；其他实体返回 `null` 放弃。

`updateGlobalFacts` 每次 `sector.getMemory()` 都会重新执行，写入 MOD 专属前缀、过期时间 0 的演示事实。

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 完整最小例：生涯插件（CampaignPlugin）的注册、picker、优先级与生命周期。
 *
 * 本类是 transient 生涯插件：数据不进存档，必须由调用点（GearInModPlugin.onGameLoad，
 * 见页面正文）在每次读档后重新注册；registerPlugin 按 getId() 去重，重复注册只会
 * 替换旧实例，不会叠加。
 *
 * pickInteractionDialogPlugin：对"齿轮工业"（C03-S02 注册的势力 id）的舰队与市场实体
 * 返回 GearInInteractionDialog（PickPriority.MOD_SPECIFIC）；对其他实体返回 null，即放弃本次
 * 选择权，由原版 CORE_GENERAL 对话继续生效。
 *
 * updateGlobalFacts：每次 sector.getMemory() 都会重新执行；写入的演示事实使用 MOD
 * 专属前缀且过期时间为 0，符合 API 对 facts 写法的要求。
 *
 * 被 picker 选中的对话框由同包类 GearInInteractionDialog 承担（一文件一角色，见该文件）。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    /** 演示触发势力：齿轮工业势力 id（C03-S02 定义，本节只消费不定义）。 */
    public static final String DEMO_FACTION_ID = "gear_industry";

    /** updateGlobalFacts 写入的演示事实键：MOD 专属前缀 + 过期时间 0。 */
    public static final String DEMO_GLOBAL_FACT = "$gearIn_demoFact";

    @Override
    public String getId() {
        // 非 null 且稳定：注册去重与 unregisterPlugin 都依赖它。
        return "GearIn_CampaignPlugin";
    }

    @Override
    public boolean isTransient() {
        // transient：不随存档保存，每次读档后必须重新注册（见页面调用点片段）。
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget == null) {
            return null;
        }
        if (interactionTarget instanceof CampaignFleetAPI fleet) {
            if (fleet.getFaction() != null && DEMO_FACTION_ID.equals(fleet.getFaction().getId())) {
                return new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC);
            }
        }
        if (interactionTarget.getMarket() != null
                && DEMO_FACTION_ID.equals(interactionTarget.getMarket().getFactionId())) {
            return new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC);
        }
        return null; // 不处理：把选择权留给其他插件（原版 CORE_GENERAL 对话）
    }

    @Override
    public void updateGlobalFacts(MemoryAPI memory) {
        // 每次 sector.getMemory() 都会重新执行本方法；0 过期防止事实滞留污染记忆。
        memory.set(DEMO_GLOBAL_FACT, true, 0f);
    }
}
```

### GearInInteractionDialog（InteractionDialogPlugin 角色）

**角色**：被 picker 选中的最小交互对话框 —— 实现 `InteractionDialogPlugin`，7 个方法全部来自接口，只实现 `init` / `optionSelected`，其余空实现。

**绑定**：不注册 —— 由 `GearInCampaignPlugin.pickInteractionDialogPlugin` 在 pick 时现场创建并以 `PickPriority.MOD_SPECIFIC` 返回。

原版 `CoreCampaignPluginImpl` 也是这个模式：picker 每次咨询都会新建实例，插件类必须无参可实例化。

**同包协作**：`refreshFact()` 经同包静态常量 `GearInCampaignPlugin.DEMO_GLOBAL_FACT` 读事实键。

读 `Global.getSector().getMemory()`——**这次读取本身就会触发全插件（含 `GearInCampaignPlugin`）的 `updateGlobalFacts`**，所以显示的值必然反映 "已注册 + facts 生效" 两个事实。

这里只读不写，不构成递归（递归规则见 "规则强度"）。

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 完整最小例：被生涯插件 picker 选中的交互对话框（InteractionDialogPlugin 角色）。
 *
 * 由 GearInCampaignPlugin.pickInteractionDialogPlugin 在 pick 时现场创建并以
 * PickPriority.MOD_SPECIFIC 返回，证明"本插件的选择胜出"与"facts 已写入"，
 * 提供"刷新事实"与"离开"两个选项。
 *
 * refreshFact() 读 Global.getSector().getMemory()—— 这次读取本身会触发全插件
 * （含 GearInCampaignPlugin）的 updateGlobalFacts，因此显示值必然反映
 * "已注册 + facts 生效"两个事实；这里只读不写，不构成递归。
 */
public class GearInInteractionDialog implements InteractionDialogPlugin {

    private static final String OPT_REFRESH = "refresh";
    private static final String OPT_LEAVE = "leave";

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        SectorEntityToken target = dialog.getInteractionTarget();
        dialog.getTextPanel().addParagraph(
                "GearIn demo dialog: routed by pickInteractionDialogPlugin() "
                        + "with PickPriority.MOD_SPECIFIC. Target: "
                        + (target != null ? target.getName() : "null"));
        refreshFact();
        dialog.setPromptText("GearIn campaign plugin demo");
        showOptions();
    }

    private void refreshFact() {
        boolean fact = Global.getSector().getMemory().getBoolean(GearInCampaignPlugin.DEMO_GLOBAL_FACT);
        dialog.getTextPanel().addParagraph(
                "Global fact [" + GearInCampaignPlugin.DEMO_GLOBAL_FACT + "] = " + fact
                        + " (recomputed by updateGlobalFacts() on every getMemory() call)");
    }

    private void showOptions() {
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Refresh facts", OPT_REFRESH);
        dialog.getOptionPanel().addOption("Leave", OPT_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPT_REFRESH.equals(optionData)) {
            refreshFact();
            showOptions();
        } else if (OPT_LEAVE.equals(optionData)) {
            dialog.dismiss();
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
```

对应关系：

- `extends BaseCampaignPlugin`：27 个方法全部有默认实现，只覆写 `getId` / `isTransient` / `pickInteractionDialogPlugin` / `updateGlobalFacts`（`BaseCampaignPlugin` javadoc 明确建议继承而非直接实现接口）。
- `getId()` 返回 `"GearIn_CampaignPlugin"`：非 `null`、全局唯一、带 MOD 前缀 ——transient 插件每次读档重注册时被 `addPlugin` 按 id 顶替，不会累积（插件登记表）。
- `isTransient()` 返回 `true`：数据不进存档；这是 `BaseCampaignPlugin` 默认值，显式写出是为了讲明语义（javadoc 建议的默认写法）。
- `pickInteractionDialogPlugin`：先判 `instanceof CampaignFleetAPI`（舰队势力匹配），再判 `getMarket() != null`（市场实体势力匹配，覆盖行星/空间站）；命中返回 `MOD_SPECIFIC`（5 > 原版 `CORE_GENERAL` 0，必胜）；不命中返回 `null` 放弃。
- `new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC)`：对话框实例在 pick 时现场创建（原版 `CoreCampaignPluginImpl` 也是这个模式 ——picker 每次咨询都会新建实例，插件类必须无参可实例化）。
- `updateGlobalFacts`：`memory.set(DEMO_GLOBAL_FACT, true, 0f)`——MOD 专属前缀 + 过期时间 0。
- `GearInInteractionDialog implements InteractionDialogPlugin`：7 个方法全部来自接口（`com.fs.starfarer.api.campaign.InteractionDialogPlugin`），本节示例只实现了 `init` / `optionSelected`，其余空实现；
  - 对话框类经同包静态常量 `GearInCampaignPlugin.DEMO_GLOBAL_FACT` 与插件类协作（一文件一角色，两个类各自承担一个接口族）。

::: tip 对话框文字用英文

游戏内置字体不含 CJK 字形：页面正文可以中文，进游戏渲染的字符串一律英文（本示例的对话框文案即全英文）。

:::

调用点（工程片段 —— 方法所在类 `GearInModPlugin` 归前置单元所有，本节只演示调用，不改动那些文件）：

```java
    // GearInModPlugin（前置单元所有）onGameLoad(boolean newGame) 内新增本节注册块。
    // transient 插件必须每次读档都重新注册：不要挂 persistent data 门（门关后插件将永远缺失，
    // 且 transient 实例不进存档、无法靠读档恢复）。
    // registerPlugin 内部按 getId() 去重：重复注册只会替换旧实例，不会叠加。
    Global.getSector().registerPlugin(new GearInCampaignPlugin());
    Global.getLogger(GearInModPlugin.class).info("GearIn campaign plugin registered");
```

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元的编译链路）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含两个 class 条目（可用 `jar tf` 核对）：
- `data/scripts/gearin/campaign/GearInCampaignPlugin.class`
- `data/scripts/gearin/campaign/GearInInteractionDialog.class`

本节两个代码块与 fixture 逐字一致，fixture 编译验收命令见 "预期结果"。

### 验证方案（基于本例）

**前置数据**：累计工程（MOD 骨架、势力 `gear_industry`、星系生成器、`GearInModPlugin`，见前置知识）＋本节两个类与注册调用点片段。

静态自查：

- 两个代码块与 fixture 逐字一致（包括 import 顺序）。
- `DEMO_FACTION_ID`、`DEMO_GLOBAL_FACT`、`getId()` 返回值与调用点片段逐字一致。
- 运行时名称（类名、包名、插件 id、记忆键）均不含工作单元编号。
- `isTransient()` 为 `true` 时调用点没有 persistent data 守卫。

**操作步骤**：

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC10s03Java --no-daemon`，确认 `BUILD SUCCESSFUL`（退出码 0）。
2. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `GearInCampaignPlugin.class` 与 `GearInInteractionDialog.class`。
3. 注册与 picker：新建生涯，确认 `starsector.log` 出现 `GearIn campaign plugin registered`；进入齿轮星系点击齿轮工业行星，确认弹出演示对话框且事实显示 `= true`（预期结果第 3、4 条）。
4. 放弃路径：点击原版势力行星/空间站/舰队/跳跃点，确认打开原版对话（预期结果第 6 条）。
5. 读档：保存后读档，确认注册日志重现、演示对话框仍在（预期结果第 7 条）。
6. 故障注入 1：注释掉 `onGameLoad` 里的 `registerPlugin` 调用，重启读档 —— 齿轮工业行星对话恢复原版且无任何报错（对应故障分析第 1 条）；改回后确认恢复。
7. 故障注入 2：把 `PickPriority.MOD_SPECIFIC` 改成 `CORE_GENERAL`，重新构建并新建生涯 —— 演示对话框不再可靠出现（与原版并列，结果未定义，对应故障分析第 4 条）；改回后确认恢复。
8. 故障注入 3：把 `getId()` 的返回值改成 `null`（注释掉覆写），读档两次后交互齿轮工业行星 ——`updateGlobalFacts` 被咨询多次（可通过在 facts 里临时加日志观察成双现象，对应故障分析第 2 条）；改回后确认恢复。
9. 参数演示：把 `DEMO_FACTION_ID` 改成 `"pirates"` 重新构建 —— 海盗势力实体弹出演示对话框（触发条件随参数变化，验证 "修改高频参数 → 结果变化"）；改回后确认恢复。

**通过信号**：操作 1–5 全部符合；操作 6–9 的注入与恢复都符合预期。

**清理**：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle`、`.cursor\tutorial-work\` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC10s03Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInCampaignPlugin.class` 与 `data/scripts/gearin/campaign/GearInInteractionDialog.class`。
3. **注册**：新建生涯（或读档）后 `starsector.log` 出现一行 `GearIn campaign plugin registered`（来自调用点片段）；读档后再次出现同一行（transient 插件每次读档重新注册，见 "生命周期与存档边界"）。
4. **picker 命中**：进入齿轮星系（累计工程生成的齿轮工业系统），点击任一齿轮工业势力的行星 —— 弹出 `GearIn demo dialog`：
   - 第一行显示 `routed by pickInteractionDialogPlugin() with PickPriority.MOD_SPECIFIC` 与目标名；
   - 第二行显示 `Global fact [$gearIn_demoFact] = true`。
   - 这证明：本插件的 pick 在竞争中原版 `CORE_GENERAL` 对话中胜出，且 `updateGlobalFacts` 已生效。
5. **事实刷新**：在对话框中选 `Refresh facts`—— 新增一行且 `= true`（每次 `getMemory()` 重跑 facts，值始终新鲜）；选 `Leave`—— 对话框关闭，返回生涯地图。
6. **放弃路径**：点击任意非齿轮工业实体（原版势力行星、空间站、舰队、跳跃点）—— 打开的是原版对话（本插件返回 `null`，选择权留给原版 `CORE_GENERAL` 插件）。若点击后**什么都不发生**，见故障分析第 5 条。
7. **读档后仍生效**：保存并读档 —— 日志再次出现注册行，点击齿轮工业行星演示对话框依旧出现（transient 重新注册成功；若消失，见故障分析第 1 条）。
8. **fleet 触发**：与齿轮工业势力的舰队（如市场生成的防御舰队）遭遇 —— 遭遇对话同样是 `GearIn demo dialog`（`instanceof CampaignFleetAPI` 分支）。

## 实例驱动故障分析

1. **读档后演示对话框消失、插件 "不存在"，且无任何报错**。
   - 触发：transient 插件只在 `onNewGame`（或 persistent data 守卫内）注册。
   - 可观察：新档正常（第一次注册发生在自动存档前），读档后插件不在 `plugins` 列表里（transient 实例被 `writeReplace` 过滤）、守卫已关闭不再注册 —— 齿轮工业行星对话恢复原版。
   - 修正：transient 插件在 `onGameLoad` **无条件**注册（本节调用点片段）；对照脚本注册（见前置知识）：脚本与 transient 插件的注册策略相反（脚本要守卫、transient 插件不能守卫）。
2. **插件明明只注册一次，picker 却被咨询多次、facts 重复写入（日志成双）**。
   - 触发：`getId()` 未覆写（返回 `null`，`BaseCampaignPlugin` 默认），transient 插件每次读档重新注册时 `addPlugin` 不去重直接追加。
   - 可观察：读档次数越多，同一次 `getMemory()` 里 `updateGlobalFacts` 被调用的次数越多。
   - 修正：覆写 `getId()` 返回稳定唯一值（去重按 id 生效）。
3. **所有势力的对话都变成了演示对话框（过度覆盖）**。
   - 触发：picker 条件写宽 —— 比如只判 `interactionTarget instanceof CampaignFleetAPI` 就返回 `PluginPick`，没有检查势力。
   - 可观察：`MOD_SPECIFIC`(5) > 原版 `CORE_GENERAL`(0)，所有舰队对话都被本插件接管。
   - 修正：条件收窄到明确目标（势力 id、tag、记忆标志），其余返回 `null`。
4. **点击齿轮工业行星，打开的仍是原版对话（覆盖不足）**。
   - 触发：priority 用错层级 —— 若用 `CORE_GENERAL`(0)，与原版核心插件同层并列，并列结果未定义（API javadoc 原文）。
   - RC8 实现按列表顺序稳定排序、并列时取靠后者，谁靠后取决于注册时机 —— 本插件的演示对话框**不再可靠出现**（可能被原版顶掉，也可能 "时灵时不灵"）。
   - 修正：MOD 至少用 `MOD_GENERAL`(1)，本示例用 `MOD_SPECIFIC`(5)；并列竞争禁止依赖（规则强度第 8 条）。
5. **点击齿轮工业行星，什么都不发生（连原版对话都不开）**。
   - 触发：picker 返回了 `plugin == null` 的 `PluginPick`（如 `new PluginPick<>(null, PickPriority.HIGHEST)`），或 picker 内部抛异常。
   - 可观察：前者 —— 裁决器只检查 `PluginPick` 非 null，null 插件 "废票" 赢得竞争， `showInteractionDialog` 拿到的插件为 `null` 直接 `return false`（生涯状态）；后者 —— 异常沿 `getPriorityPlugin` 冒泡（无 try/catch），日志 `ERROR` + 堆栈指向本插件 picker。
   - 修正：无法处理就返回 `null`（而不是空插件 `PluginPick`）；picker 内不抛异常。
6. **`StackOverflowError`，堆栈在 `updateGlobalFacts` 附近**。
   - 触发：`updateGlobalFacts` 内调用了 `Global.getSector().getMemory()`（或 `updateEntityFacts` 内调用 `entity.getMemory()`）。
   - 可观察：每次 `getMemory()` 重跑全插件 facts，自我触发无限递归。
   - 修正：facts 回调只用传入的 `memory` 参数或 `getMemoryWithoutUpdate()`（本节对话框里读记忆是 "对话侧" 代码，不在 facts 回调内，安全）。
7. **与其他 MOD 同层竞争，谁生效不稳定**。
   - 触发：两个 MOD 对同一目标都用 `MOD_SPECIFIC` 并各自返回非 null。
   - 可观察：哪个对话出现取决于注册顺序（RC8 稳定排序取列表靠后者），变更启用顺序或版本后可能反转 —— 约定未定义。
   - 修正：按目标收窄到互不重叠的条件；需要绝对优先时用 `HIGHEST`（成熟实例对特定标记舰队用 `HIGHEST`，注释写明 "please do not break it"），但 `HIGHEST` 慎用。
8. **存档异常 / 读档后插件字段失效**。
   - 触发：非 transient 插件把不可序列化对象（UI 引用、引擎句柄）放进实例字段，实例随存档保存。
   - 可观察：存档/读档异常（XStream 边界），或读档后字段指向失效对象。
   - 修正：默认 `isTransient() == true`；确实需要持久状态的插件只保留可序列化字段，跨会话数据放 `getPersistentData()`（见前置知识）。

## 总结

本节把生涯插件讲成一条链：

- **注册**（`SectorAPI.registerPlugin` → 插件登记表，`getId()` 非 null 时按 id 去重、 `unregisterPlugin` 按 id 移除第一个匹配）
- **竞争**（每个事件点对全部插件逐个咨询对应 picker，`PluginPick` 非 null 才参选，按 `PickPriority.ordinal` 取最高； `null` = 放弃；并列未定义，RC8 实现为稳定排序取列表靠后者）
- **消费**（对话、战斗创建/自动结算、重生、自动装配、舰队 AI 模块、stub 转换、通信动作图标等调用点使用获胜插件； `null` 时多数调用点回退原版或什么都不做）
- **facts**（6 个 `updateXxxFacts` 在各自对象每次 `getMemory()` 时对全部插件重跑，写 `$` 前缀、0 过期的规则事实）
- **生命周期**：分界是 `isTransient()`——transient 插件不进存档、**每次读档必须重新注册、不能挂一次性守卫**；非 transient 插件随存档保存、可一次注册（成熟实例放 `CLASS_MARK` 守卫内）。

记忆要点：

- 8 条 "必须" 里最常踩的是第 1、3、5 条：返回 `null` 放弃、transient 每次读档重注册、不要返回空插件 `PluginPick`。
- `CampaignPlugin` 的 picker 是 "咨询制"：返回非 null 就**接管**该次处理，条件必须收窄，否则就是故障分析第 3 条的过度覆盖。
- 与生涯事件监听器的对照：监听器（`CampaignEventListener`）被动接收 "已经发生的事件"（战斗结束、市场活动、舰队遭遇等回调）；生涯插件在事件**发生前**被咨询（对话由谁开、战斗由谁建、自动结算由谁做、AI 模块由谁当）。两条通道注册表不同、互不相通。
- `GenericPluginManagerAPI` 是另一套 int 优先级机制，注册表独立，排查 "注册了却从未被调用" 时先分清用的是哪一套。

把视角再抬高一层：注册表决定谁在场，优先级决定谁上场，生命周期决定谁活多久 ——picker 在事件发生前被咨询、facts 在记忆被读取时重写，两者共同服务于 "让 MOD 代码在正确的时间点接管正确的对象"。

本节写入的 `$` 前缀事实与选中的对话框，正是后续规则系统与交互页面消费的原料；生涯插件这条扩展通道至此形成完整流程。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：生涯插件接口 ——`PickPriority` 7 级枚举、19 个 picker、6 个 facts、`getId` / `isTransient`。
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：全部默认实现与 "继承而非实现" 的建议。
- `com.fs.starfarer.api.PluginPick`：`plugin` + `priority` 公开字段与构造器。
- `com.fs.starfarer.api.campaign.SectorAPI`：`registerPlugin` / `unregisterPlugin` / `getMemory` / `getPluginPicker` / `getGenericPlugins`。
- `com.fs.starfarer.api.campaign.PluginPickerAPI`：公开聚合的 6 个 picker。
- `com.fs.starfarer.api.campaign.GenericPluginManagerAPI`：另一套 int 优先级通用插件机制（`pickPlugin`）。
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互对话框接口（7 个方法）。
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：`getTextPanel` / `getOptionPanel` / `setPromptText` / `dismiss` / `getInteractionTarget`。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：`set(key, value, expire)`、`getBoolean` 等记忆读写。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：原版核心生涯插件 ——`CORE_GENERAL` 默认、重生教程分支 `MOD_SPECIFIC`、facts 写 `$` 前缀变量。
- `data/scripts/gearin/campaign`：本节两个示例类的包目录。
- `Starsector/starsector-core/starsector.log`：注册与 picker 排查的主要依据。
