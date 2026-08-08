# 制作生涯插件

本节编写并注册第一个生涯插件，让齿轮工业的行星与舰队打开自定义对话框。生涯插件会在事件发生前参与选择，注册和优先级错误可能让它不生效，或覆盖原版对话。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出生涯插件的注册与注销入口，以及 `getId()` 未覆写、id 撞车、未注册三种情况各自的可观察结果；
- 区分 transient 与非 transient 插件的存档边界与注册时机；
- 背出 `PickPriority` 的 7 级阶梯并解释竞争裁决规则 (取 `ordinal` 最高者、返回 `null` 即放弃、同优先级并列未定义)；
- 从零编写一个 transient 生涯插件与最小交互对话框，并完成注册；
- 用游戏内对话与 `starsector.log` 验证 picker 命中、优先级生效与 facts 写入；
- 从症状定位八类典型故障：插件消失、重复咨询、抢走原版对话、点击实体无对话、picker 抛异常、facts 递归、同优先级竞争、序列化问题。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C2-S1] 的注册入口与回调模型；
- [C3-S3] 的 `gear_industry` 势力定义；
- [C3-S6]、[C3-S7] 的新档生成入口、行星与市场实体；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C6-S2] 的 `modPlugin` 生命周期与 `onGameLoad` 回调；
- [C6-S3] 的 `Global.getSector()` 阶段语义；
- [C9-S1] 的生涯脚本注册与持久数据判断；
- [C12-S2] 的生涯事件监听器通道；
- [C14-S3] 的 Rules 命令与 [C14-S4] 的 `$` 前缀记忆变量。

## 术语

按术语约定，API 名保留源码大小写。本节首次完整讲解两个页面级术语：

- **生涯插件 (CampaignPlugin)**：API 名保留 `CampaignPlugin`。
    - 实现 `CampaignPlugin` 接口 (或继承 `BaseCampaignPlugin`) 并在生涯引擎注册的对象。
    - 它不随帧推进 (那是 `EveryFrameScript`，见前置知识)，而是被动接受两类咨询：picker 咨询 (`pickXxx`，在生涯事件发生时被调用，决定 "由哪个插件实例处理") 与 facts 咨询 (`updateXxxFacts`，在记忆被读取时被调用，负责写规则事实)。
- **picker(选择器)**：英文保留。
    - 对一组已注册的生涯插件逐个调用同一个 `pickXxx` 方法、收集非 `null` 的 `PluginPick` 并按 `PickPriority` 裁决出唯一获胜者的机制。
    - 返回 `null` 表示该插件放弃本次选择权。

## 注册入口与存档边界

### 注册与注销速查表

术语固定后，先解决第一件事：把插件放进生涯引擎的登记表。注册、去重与注销的入口都在 `SectorAPI` 上：

| 环节 | 文件或 API | 行为 |
|---|---|---|
| 注册 | `SectorAPI.registerPlugin(CampaignPlugin)`(`com.fs.starfarer.api.campaign.SectorAPI`) | 写入插件登记表 (`addPlugin`，去重规则见下行) |
| 去重 | 插件登记表 `addPlugin` | 若 `getId()` 非 `null`：先 `removePlugin(getId())` 再追加 —— **后注册的同 id 插件静默顶掉先注册者**；`getId()` 为 `null` 则不去重、直接追加 |
| 注销 | `SectorAPI.unregisterPlugin(String pluginId)`(`com.fs.starfarer.api.campaign.SectorAPI`) | 按 id 移除**第一个**匹配项；id 不匹配则什么都不做 |
| 存储 | 插件登记表 | 非 transient 插件随存档序列化、读档恢复；transient 插件写档时被过滤、不进存档 —— **这就是 transient 与非 transient 的存档边界** |
| 竞争 | 插件登记表的裁决逻辑 | 所有 picker 共用的裁决器 (见 "竞争裁决与优先级") |
| 聚合入口 | `SectorAPI.getPluginPicker()`(`com.fs.starfarer.api.campaign.SectorAPI`，返回 `PluginPickerAPI`) | 公开暴露 6 个聚合 picker：`pickFleetStubConverter` ×2、`pickImmigrationPlugin`、`pickAICoreAdminPlugin`、`pickAICoreOfficerPlugin`、`pickFleetInflater`(`com.fs.starfarer.api.campaign.PluginPickerAPI`)；其余 picker 由引擎内部调用 |

注册的合法时机：**必须**在生涯引擎建立之后 ——`onGameLoad` / `onNewGame` 回调里 (`Global.getSector()` 已可用)。

在 `onApplicationLoad` 里调用 `registerPlugin` 会先对 `getSector()` 解引用，直接 NPE(`Global.getSector()` 的 null 语义见前置知识)。

### 生命周期与存档边界 (transient / 非 transient)

登记表写完后，下一个决定是生命周期：插件的状态要不要进存档，这直接决定注册代码写在哪里、写几次。`CampaignPlugin.isTransient()` 定义两种生命周期：

- **transient(返回 `true`，`BaseCampaignPlugin` 默认值)**：插件数据不进存档 (`writeReplace` 写档前过滤)，**必须**在每次读档后重新注册 ——"it needs to be re-added to the game every time (in ModPlugin.onGameLoad())"。
    - 注册入口是 `onGameLoad(boolean)`(新档与读档共用，见前置知识)。
- **非 transient(返回 `false`)**：插件实例随存档保存，可以只注册一次 (`onNewGame`，或 `onGameLoad` 里的 `CLASS_MARK` 首次进入检查内 —— 成熟实例正是把 `registerPlugin` 放进前置单元的首次进入检查内)。代价：实例字段随存档序列化 (XStream 边界)，且卸载 MOD 后存档里残留实例。

transient 插件的注册禁止挂进一次性检查

首次进入检查在开新档时放行并完成注册；首次自动存档后 transient 实例被 `writeReplace` 过滤，下次读档时检查标记已写入、注册不再执行 —— 插件从此永久消失且无任何报错。这是与脚本注册策略最显著的差异：脚本要一次性检查、transient 插件不能依赖它 (见 "规则强度")。

为什么默认 transient

默认 transient：transient 数据不进存档，卸载 MOD 不留残留，便于在既有存档上增删 MOD。

原文："Plugins should be transient unless they need to save data, to improve the ability to add/remove mods from an existing game."

本节示例采用 transient 模式。

`getId()` 与去重的联动：transient 插件每次读档重新注册时，若 `getId()` 返回稳定非 `null` 值，`addPlugin` 先按 id 移除旧实例再追加 —— **重复注册是幂等的** (不会叠加)。

若返回 `null`(未覆写，`BaseCampaignPlugin` 默认)，每次读档后新旧实例并存，picker 被逐个咨询、facts 重复执行。

## 竞争裁决与优先级

插件已经能进场了，但多个插件同时声称处理同一事件时，由谁说了算？竞争裁决与优先级阶梯回答这个问题。

### 裁决规则：PluginPick 与 PickPriority

每个 picker 返回 `PluginPick<T>`(`com.fs.starfarer.api.PluginPick`：公开字段 `plugin` 与 `priority`) 或 `null`。裁决器 `getPriorityPlugin`(插件登记表) 的精确语义：

1. 按注册顺序对**全部**已注册插件调用本次 picker 的 `pick` 方法，收集所有非 `null` 的 `PluginPick`**。没有 try/catch**—— 任何插件抛出的异常直接沿调用点冒泡。
2. 一个都没收集到 → 返回 `null`(本次选择没有任何插件接手)。
3. 只有一个 → 直接返回它的 `plugin`。
4. 多个 → 按 `priority.ordinal()` 升序稳定排序，返回**最后一个** (即 `ordinal` 最高者)。

关键结论：

- **返回 `null` = 放弃**：不参与竞争，把机会留给别的插件。这是每个 picker 的默认姿态 (`BaseCampaignPlugin` 的全部 picker 默认返回 `null`)。
- **`PluginPick` 的 `plugin` 不能是 `null`**：裁决器只检查 `PluginPick` 非 `null` 就让它参选，获胜后调用点拿到的是 `.plugin`。
    - 一个 `plugin == null` 的高优先级选择会**顶掉**其他插件的合法选择，而调用点多数把它当作 "无人处理"—— 点击实体后什么都不发生，连原版对话都不开。这场竞争唯一的成果，是让原版和您的插件都无话可说。

同优先级并列的结果未定义

"what gets picked when multiple plugins have the same priority is undefined"。

实现是稳定排序 (`Collections.sort`)+ 取最后一项，因此并列时**列表靠后者胜出**、列表顺序取决于注册时机 —— 这是实现观察，不是约定，**禁止依赖** (见 "规则强度")。

### PickPriority 优先级阶梯速查表

`PickPriority` 是 `CampaignPlugin` 的嵌套枚举，共 7 级 (`com.fs.starfarer.api.campaign.CampaignPlugin`；战斗 AI picker 用到的也是同一个枚举，这里给出生涯侧的完整语义)：

| 优先级 | ordinal | 语义 (归纳) |
|---|---|---|
| `CORE_GENERAL` | 0 | 只应由核心代码使用；MOD 用这一级 "可能根本不会被选中" |
| `MOD_GENERAL` | 1 | MOD 整体替换某项生涯功能 (如 "与所有行星的交互") |
| `CORE_SET` | 2 | 只应由核心代码使用 |
| `MOD_SET` | 3 | 处理某一类情形 (如 "与所有丛林行星的交互")；覆盖一切 `_GENERAL` |
| `CORE_SPECIFIC` | 4 | 核心代码用于特定遭遇 (特殊行星、特殊舰队) |
| `MOD_SPECIFIC` | 5 | MOD 用于特定遭遇 (某支特定舰队的专属对话) |
| `HIGHEST` | 6 | 绝对最高；无充分理由不要用 (MOD 整合包用它解决内部冲突) |

裁决只看 `ordinal`，**层级名称只是语义约定**：`MOD_SET`(3) 与 `CORE_SET`(2) 竞争时 `MOD_SET` 胜出，但如果两个 MOD 都用 `MOD_SPECIFIC` 处理同一目标，谁生效未定义。

原版核心插件 (`CoreCampaignPluginImpl`，`com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`) 几乎全部返回 `CORE_GENERAL`，只有教程重生对话框用 `MOD_SPECIFIC`。

MOD 用 `MOD_GENERAL` 及以上就能稳定覆盖原版，用 `CORE_GENERAL` 则与原版并列、结果未定义。

## 只覆写当前竞争点

生涯插件的公开 hook 很多，实际类应只覆写需要参与竞争的 picker 或 facts 更新。本例只接管目标实体的交互对话，并为 Rules 写入一个事实；其他对象返回 `null`，明确放弃本次选择。需要扩展其他入口时，从 `CampaignPlugin` 查签名，再沿同一套优先级和命中范围判断。

## 对照：GenericPluginManagerAPI

排查 "注册了却从未被调用" 时，还有一套名字相近的注册表容易混进来：`SectorAPI.getGenericPlugins()`(`com.fs.starfarer.api.campaign.SectorAPI`) 返回 `GenericPluginManagerAPI`。

后者的完整类型名是 `com.fs.starfarer.api.campaign.GenericPluginManagerAPI`；它按 "类 + 参数" 注册通用插件 (`addPlugin(plugin, isTransient)`)。

插件实现 `GenericPlugin.getHandlingPriority(Object params)` 返回 **int** 优先级 (负数 = 不处理)，使用方用 `pickPlugin(Class, params)` 取最高分者。

它与 `CampaignPlugin` 是**两套独立机制**：优先级是 int 常量 (`CORE_GENERAL=0` ... `MOD_SUBSET=300` ... `HIGHEST=Integer.MAX_VALUE`，注意这里叫 `SUBSET` 不是 `SET`)。

注册表也不同 (`SectorAPI.getGenericPlugins()` 与 `registerPlugin` 互不相通)。

本节示例不使用它。排查 "注册了却从未被调用" 时，编者第一步永远是确认注册走的是哪一套入口；两个 API 名字里都带 Plugin，注册表却互不相通。

## 规则强度

机制、速查与对照都清楚了，动手之前先记下写作约束。**必须** (违反后的可观察结果写在括号里)：

1. **picker 无法处理时必须返回 `null`**("Return null if this CampaignPlugin implementation doesn't provide one.")。
    - 返回 "占位实例" 或 `plugin == null` 的 `PluginPick` → 顶掉低优先级插件的合法选择后自身不可用：`showInteractionDialog` 拿到 `null` 插件直接返回 `false`(**点击实体什么都不发生，连原版对话都不开**，生涯状态)。
2. **picker 内不得抛出未处理异常**。
    - 裁决器 `getPriorityPlugin` 没有 try/catch(插件登记表)，异常沿调用点冒泡 → 生涯流程中断、UI 报错 (日志 `ERROR` + 异常堆栈指向本插件的 picker)。
    - 无法处理就返回 `null`，不要抛。
3. **transient 插件必须在每次 `onGameLoad` 重新注册** ("needs to be re-added to the game every time (in ModPlugin.onGameLoad())")。
    - 不注册 → 读档后插件缺失、picker 不响应、facts 不写，**无任何报错**。
4. **transient 插件的注册禁止挂进 persistent data 一次性检查** (机制推导：检查标记写入后注册不再执行，而 transient 实例不被存档保留)。
    - 违反 → 新档一切正常，首次读档后插件永久消失且无报错 (这是与脚本注册策略最显著的差异 —— 脚本要一次性检查、transient 插件不能依赖它)。
5. **返回的 `PluginPick` 必须携带非 `null` 插件实例** (裁决器只检查 `PluginPick` 非 null，调用点只检查取出的 plugin)。
    - 违反 → 高优先级 "废票" 顶掉其他 MOD 的合法选择 (见第 1 条的可观察结果)。
6. **注册必须发生在生涯引擎建立之后** (`onGameLoad` / `onNewGame`)。
    - 在 `onApplicationLoad` 注册 → `Global.getSector()` 为 `null`，NPE 启动中止。
7. **facts 回调内禁止调用会再次触发 facts 的 `getMemory()`**(典型：`updateGlobalFacts` 内调 `sector.getMemory()`、`updateEntityFacts` 内调 `entity.getMemory()`)。
    - 每次 `getMemory()` 都会重跑全插件对应 facts(生涯引擎)，自我触发构成无限递归 → `StackOverflowError`。
    - 读记忆用传入的 `memory` 参数或 `getMemoryWithoutUpdate()`。
8. **禁止依赖同优先级并列的结果** ("what gets picked when multiple plugins have the same priority is undefined")。
    - 实现是稳定排序取列表靠后者，但这是实现观察、非约定 → 与其他 MOD 同层竞争时谁生效不保证 (版本升级可能反转)。
    - 要保证自己生效，用明确的层级差 (MOD 至少 `MOD_GENERAL`)。

**建议** (违反的可观察结果写在括号里)：

- **覆写 `getId()` 返回稳定唯一值** ("should be unique. Can be null, but shouldn't.")。返回 `null` → 注册不去重，transient 插件每次读档后实例累积，picker 被咨询多次、facts 重复写入 (日志成双)。
- **插件 id 全局唯一、带 MOD 前缀** (如 `GearIn_CampaignPlugin`)。id 撞车 → 后注册的同 id 插件静默顶掉先注册者 (去重按 id 全局生效，跨 MOD 也如此)。
- **默认 `isTransient()` 返回 `true`**("should be transient unless they need to save data")。一律返回 `false` → 插件实例随存档序列化 (XStream 边界风险、存档体积增长、卸载 MOD 后残留)，本可瞬态的状态被意外持久化。
- **facts 写入的变量过期时间设为 `0`**("Any variables set here should have an expiration time of 0, since this method will be called every time the getMemory() method is called")。
    - 非 0 过期 → 事实滞留，规则系统读到陈旧值、记忆被污染。
- **facts 键使用 MOD 专属前缀** ("Mod-added facts should have their variable names use a mod-specific prefix to avoid conflicts")。无前缀 → 与原版 `$name`、`$relValue` 等键或别的 MOD 键互相覆盖。
- **priority 按语义层级选择** (`MOD_GENERAL` 整体替换 / `MOD_SET` 一类情形 / `MOD_SPECIFIC` 特定遭遇 / `HIGHEST` 慎用)。用错层级 → 过度覆盖 (所有对话被换成自己的) 或覆盖不足 (被更高优先级插件顶掉)。
- **picker 与 facts 里不做重活** (机制事实：pick 发生在对话打开、战斗创建等交互路径上；facts 在每次 `getMemory()` 时对全部插件重跑)。重活 → 卡顿、频繁重复执行。

## 实现生涯插件

先让插件在每次读档后稳定回到登记表，再逐步扩大它的职责。picker 的每个非 `null` 返回值都会参与竞争，因此命中条件与优先级必须同时写清。

### 注册 transient 插件

把注册调用直接放进 `GearInModPlugin.onGameLoad()`，不使用 persistent data 门。稳定 ID 会让重复注册替换旧实例。

```java
Global.getSector().registerPlugin(new GearInCampaignPlugin());
```

完成信号：新档与每次读档后都出现一次注册日志，齿轮工业实体始终能进入本插件的 picker。

### 提供稳定插件身份

在 `GearInCampaignPlugin` 中覆写身份与存档策略。`getId()` 参与全局去重，必须使用 MOD 前缀。

```java
@Override
public String getId() {
    return "GearIn_CampaignPlugin";
}

@Override
public boolean isTransient() {
    return true;
}
```

完成信号：同一加载周期连续注册两次后，picker 仍只咨询一个 GearIn 插件实例。

### 收窄对话目标

在 `pickInteractionDialogPlugin()` 中分别检查舰队势力与市场势力。其余实体返回 `null`，明确把选择权留给原版。

```java
if (interactionTarget instanceof CampaignFleetAPI fleet
        && fleet.getFaction() != null
        && DEMO_FACTION_ID.equals(fleet.getFaction().getId())) {
    return new PluginPick<>(new GearInInteractionDialog(),
            PickPriority.MOD_SPECIFIC);
}
if (interactionTarget.getMarket() != null
        && DEMO_FACTION_ID.equals(interactionTarget.getMarket().getFactionId())) {
    return new PluginPick<>(new GearInInteractionDialog(),
            PickPriority.MOD_SPECIFIC);
}
return null;
```

完成信号：齿轮工业舰队与市场实体打开演示对话，原版势力实体继续打开原版对话。

### 写入规则事实

在 `updateGlobalFacts()` 中只使用传入的 `MemoryAPI`。过期时间设为 0，键使用 MOD 专属前缀。

```java
@Override
public void updateGlobalFacts(MemoryAPI memory) {
    memory.set("$gearIn_demoFact", true, 0f);
}
```

完成信号：任何安全位置读取 Sector memory 后，`$gearIn_demoFact` 都为 `true`；回调不会递归进入 `getMemory()`。

### 响应对话选项

在 `GearInInteractionDialog.init()` 中保存对话引用并建立选项，再在 `optionSelected()` 中分别刷新事实与关闭对话。

```java
@Override
public void optionSelected(String optionText, Object optionData) {
    if (OPT_REFRESH.equals(optionData)) {
        refreshFact();
        showOptions();
    } else if (OPT_LEAVE.equals(optionData)) {
        dialog.dismiss();
    }
}
```

完成信号：`Refresh facts` 追加一条最新事实，`Leave` 关闭对话并返回生涯地图。

## 组装生涯插件实现

约束记下之后动手：本节示例在累计工程的既有布局与 `GearInModPlugin`(前置单元) 之上新增两个同包类，位于 `src\data\scripts\gearin\campaign\`，不新增任何数据文件、除注册调用点片段外不新增注册项：

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

### GearInCampaignPlugin(CampaignPlugin 角色)

**角色**：生涯插件 —— 继承 `BaseCampaignPlugin`，只覆写 `getId` / `isTransient` / `pickInteractionDialogPlugin` / `updateGlobalFacts`。

**绑定**：注册表 (`SectorAPI.registerPlugin`，注册调用点片段见下)。`isTransient()` 返回 `true`，数据不进存档，必须由调用点在每次读档后重新注册；`getId()` 返回稳定非 `null` 值，重复注册被按 id 去重、只会替换旧实例。

**picker 与 facts**：`pickInteractionDialogPlugin` 对 "齿轮工业" 势力的两类实体命中：

- 舰队 (`instanceof CampaignFleetAPI` 分支)；
- 市场实体 (`getMarket()` 分支)。

命中时返回 `new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC)`。

`MOD_SPECIFIC`(5) > 原版 `CORE_GENERAL`(0)，必胜；其他实体返回 `null` 放弃。

`updateGlobalFacts` 每次 `sector.getMemory()` 都会重新执行，写入 MOD 专属前缀、过期时间 0 的演示事实。

### GearInInteractionDialog(InteractionDialogPlugin 角色)

**角色**：被 picker 选中的最小交互对话框 —— 实现 `InteractionDialogPlugin`，7 个方法全部来自接口，只实现 `init` / `optionSelected`，其余空实现。

**绑定**：不注册 —— 由 `GearInCampaignPlugin.pickInteractionDialogPlugin` 在 pick 时现场创建并以 `PickPriority.MOD_SPECIFIC` 返回。

原版 `CoreCampaignPluginImpl` 也是这个模式：picker 每次咨询都会新建实例，插件类必须无参可实例化。

**同包协作**：`refreshFact()` 经同包静态常量 `GearInCampaignPlugin.DEMO_GLOBAL_FACT` 读事实键。

读 `Global.getSector().getMemory()`—— **这次读取本身就会触发全插件 (含 `GearInCampaignPlugin`) 的 `updateGlobalFacts`**，所以显示的值必然反映 "已注册 + facts 生效" 两个事实。

这里只读不写，不构成递归 (递归规则见 "规则强度")。

对应关系：

- `extends BaseCampaignPlugin`：27 个方法全部有默认实现，只覆写 `getId` / `isTransient` / `pickInteractionDialogPlugin` / `updateGlobalFacts`。
- `getId()` 返回 `"GearIn_CampaignPlugin"`：非 `null`、全局唯一、带 MOD 前缀 ——transient 插件每次读档重注册时被 `addPlugin` 按 id 顶替，不会累积 (插件登记表)。
- `isTransient()` 返回 `true`：数据不进存档；这是 `BaseCampaignPlugin` 默认值，显式写出是为了讲明语义。
- `pickInteractionDialogPlugin`：先判 `instanceof CampaignFleetAPI`(舰队势力匹配)，再判 `getMarket() != null`(市场实体势力匹配，覆盖行星/空间站)；命中返回 `MOD_SPECIFIC`(5 > 原版 `CORE_GENERAL` 0，必胜)；不命中返回 `null` 放弃。
- `new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC)`：对话框实例在 pick 时现场创建 (原版 `CoreCampaignPluginImpl` 也是这个模式 ——picker 每次咨询都会新建实例，插件类必须无参可实例化)。
- `updateGlobalFacts`：`memory.set(DEMO_GLOBAL_FACT, true, 0f)`——MOD 专属前缀 + 过期时间 0。
- `GearInInteractionDialog implements InteractionDialogPlugin`：7 个方法全部来自接口 (`com.fs.starfarer.api.campaign.InteractionDialogPlugin`)，本节示例只实现了 `init` / `optionSelected`，其余空实现；
    - 对话框类经同包静态常量 `GearInCampaignPlugin.DEMO_GLOBAL_FACT` 与插件类协作 (一文件一角色，两个类各自承担一个接口族)。

对话框文字用英文

游戏内置字体不含 CJK 字形：页面正文可以中文，进游戏渲染的字符串一律英文 (本示例的对话框文案即全英文)。

调用点 (工程片段 —— 方法所在类 `GearInModPlugin` 归前置单元所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(前置单元所有)onGameLoad(boolean newGame) 内新增本节注册块。
    // transient 插件必须每次读档都重新注册：不要挂 persistent data 门 (门关后插件将永远缺失，
    // 且 transient 实例不进存档、无法靠读档恢复)。
    // registerPlugin 内部按 getId() 去重：重复注册只会替换旧实例，不会叠加。
    Global.getSector().registerPlugin(new GearInCampaignPlugin());
    Global.getLogger(GearInModPlugin.class).info("GearIn campaign plugin registered");
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元的编译链路)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含两个 class 条目 (可用 `jar tf` 核对)：

- `data/scripts/gearin/campaign/GearInCampaignPlugin.class`
- `data/scripts/gearin/campaign/GearInInteractionDialog.class`

## 关键故障实验：插件已注册但对话仍由原版处理

让目标实体返回与原版相同的 `CORE_GENERAL`。两个候选同级，裁决结果取决于登记顺序，演示对话可能被原版覆盖。

**局部故障片段：**

```java
// 故障片段
return new PluginPick<>(plugin, PickPriority.CORE_GENERAL);
```

**局部修正片段：**

```java
// 修正片段
if (!matchesTarget(entity)) {
    return null;
}
return new PluginPick<>(plugin, PickPriority.MOD_SPECIFIC);
```

原因：裁决器只比较 `ordinal`，同级并列没有稳定契约。复验：分别点击一个齿轮工业市场实体和一个原版市场实体；前者必须稳定打开演示对话，后者必须稳定走原版路径。保存并读档后重复两次，结果保持一致。

## 验证方案

前置数据：累计工程 (MOD 骨架、势力 `gear_industry`、星系生成器、`GearInModPlugin`，见前置知识)＋本节两个类与注册调用点片段。

1. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `GearInCampaignPlugin.class` 与 `GearInInteractionDialog.class`。
2. 注册与 picker：新建生涯，确认 `starsector.log` 出现 `GearIn campaign plugin registered`；进入齿轮星系点击齿轮工业行星，确认弹出演示对话框且事实显示 `= true`。
3. 放弃路径：点击原版势力行星/空间站/舰队/跳跃点，确认打开原版对话。
4. 读档：保存后读档，确认注册日志重现、演示对话框仍在。
5. 参数演示：把 `DEMO_FACTION_ID` 改成 `"pirates"` 重新构建，观察海盗势力实体的对话框。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInCampaignPlugin.class` 与 `data/scripts/gearin/campaign/GearInInteractionDialog.class`。
- **注册**：新建生涯 (或读档) 后 `starsector.log` 出现一行 `GearIn campaign plugin registered`(来自调用点片段)；读档后再次出现同一行 (transient 插件每次读档重新注册，见 "生命周期与存档边界")。
- **picker 命中**：进入齿轮星系 (累计工程生成的齿轮工业系统)，点击任一齿轮工业势力的行星 —— 弹出 `GearIn demo dialog`：
    - 第一行显示 `routed by pickInteractionDialogPlugin() with PickPriority.MOD_SPECIFIC` 与目标名；
    - 第二行显示 `Global fact [$gearIn_demoFact] = true`。
    - 这证明：本插件的 pick 在竞争中原版 `CORE_GENERAL` 对话中胜出，且 `updateGlobalFacts` 已生效。
- **事实刷新**：在对话框中选 `Refresh facts`—— 新增一行且 `= true`(每次 `getMemory()` 重跑 facts，值始终新鲜)；选 `Leave`—— 对话框关闭，返回生涯地图。
- **放弃路径**：点击原版势力的行星、空间站、舰队或跳跃点时，仍由原版交互对话处理。
- **读档**：保存后读档，`GearIn campaign plugin registered` 再次出现，齿轮工业实体仍打开演示对话框。
- **参数演示**：把 `DEMO_FACTION_ID` 改为 `"pirates"` 并重新构建后，海盗势力实体打开演示对话框，齿轮工业实体回到原版交互对话。

清理：把 `DEMO_FACTION_ID` 恢复为 `"gear_industry"`。

## 故障分析

- **读档后自定义对话消失**：transient 插件只在新档阶段注册，读档时实例已经被清除。改为在每次 `onGameLoad` 无条件注册，读档后再次点击目标实体确认自定义对话恢复。
- **每次读档后 picker 日志都会增加**：插件没有稳定 `getId()`，重复注册无法替换旧实例。返回唯一且固定的 ID，连续读档后确认每次咨询只记录一次。
- **其他势力也打开自定义对话**：picker 的命中条件只检查了宽泛实体类型。加入势力 ID、tag 或记忆标志条件，其余目标返回 `null`；逐个点击不同势力实体复验。
- **目标实体仍打开原版对话**：插件与原版使用同一优先级，并列结果受注册顺序影响。选择与职责相符的 MOD 优先级，重载后确认目标稳定由本插件处理。
- **点击目标后没有任何对话**：picker 返回了插件字段为 null 的 `PluginPick`，或在选择阶段抛出异常。无法处理时直接返回 `null`，并消除 picker 内异常；再次点击确认原版或自定义对话能够打开。
- **facts 更新时报 `StackOverflowError`**：回调内再次调用 `getMemory()`，触发了自身递归。只使用传入的 `memory` 或 `getMemoryWithoutUpdate()`，重新读取 facts 后确认堆栈不再增长。
- **与其他 MOD 竞争时结果不稳定**：两个插件对同一目标返回了相同优先级。收窄双方命中范围，确需独占时再使用更高优先级；改变加载顺序后确认结果保持一致。
- **保存或读档时插件字段报错**：非 transient 插件保存了 UI 或引擎句柄。持久插件只保留可序列化状态，跨会话数据放入 `getPersistentData()`；完成一次保存和读档复验。

## 总结

本节把生涯插件这条扩展通道接通了：注册表决定谁在场，优先级决定谁上场，生命周期决定谁活多久。写插件前先定三件事 —— 命中条件收窄到什么范围、优先级选哪一级、transient 还是非 transient；最后一项直接决定注册代码的位置，transient 必须挂在 `onGameLoad` 且每次读档都执行，不能依赖一次性检查。

picker 是 "咨询制"：返回非 `null` 就接管这次处理，因此条件必须收窄，否则会让其他势力也打开自定义对话；无法处理时返回 `null` 永远是对的。八条必须项里最常踩的是三处：无法处理时忘了返回 `null`、transient 没在每次读档重注册、返回了空插件 `PluginPick`。排查时从可观察结果反推：插件消失查注册时机与存档边界，重复咨询查 `getId()`，对话被抢或抢不到查条件宽度与优先级层级，点击无反应查是否返回了空插件或抛了异常。

与生涯事件监听器的对照：`CampaignEventListener` 在事件发生后被动接收回调，生涯插件在事件发生前被咨询；`GenericPluginManagerAPI` 是另一套 int 优先级注册表，排查 "注册了却从未被调用" 时先分清用的是哪一套。本节写入的 `$` 前缀事实供 [C14-S1] 的 Rules 对话读取，选中的对话框供 [C9-S6] 的交互对话流程使用，生涯插件这条扩展通道至此形成完整流程。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：生涯插件接口 ——`PickPriority` 7 级枚举、19 个 picker、6 个 facts、`getId` / `isTransient`。
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：全部默认实现与 "继承而非实现" 的建议。
- `com.fs.starfarer.api.PluginPick`：`plugin` + `priority` 公开字段与构造器。
- `com.fs.starfarer.api.campaign.SectorAPI`：`registerPlugin` / `unregisterPlugin` / `getMemory` / `getPluginPicker` / `getGenericPlugins`。
- `com.fs.starfarer.api.campaign.PluginPickerAPI`：公开聚合的 6 个 picker。
- `com.fs.starfarer.api.campaign.GenericPluginManagerAPI`：另一套 int 优先级通用插件机制 (`pickPlugin`)。
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互对话框接口 (7 个方法)。
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：`getTextPanel` / `getOptionPanel` / `setPromptText` / `dismiss` / `getInteractionTarget`。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：`set(key, value, expire)`、`getBoolean` 等记忆读写。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：原版核心生涯插件 ——`CORE_GENERAL` 默认、重生教程分支 `MOD_SPECIFIC`、facts 写 `$` 前缀变量。
- `data/scripts/gearin/campaign`：本节两个示例类的包目录。
- `Starsector/starsector-core/starsector.log`：注册与 picker 排查的主要依据。

## 完整最小例

::: details 完整成品：GearInCampaignPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 完整最小例：生涯插件 (CampaignPlugin) 的注册、picker、优先级与生命周期。
 *
 * 本类是 transient 生涯插件：数据不进存档，必须由调用点 (GearInModPlugin.onGameLoad，
 * 见页面正文) 在每次读档后重新注册；registerPlugin 按 getId() 去重，重复注册只会
 * 替换旧实例，不会叠加。
 *
 * pickInteractionDialogPlugin：对 "齿轮工业"(C3-S2 注册的势力 id) 的舰队与市场实体
 * 返回 GearInInteractionDialog(PickPriority.MOD_SPECIFIC)；对其他实体返回 null，即放弃本次
 * 选择权，由原版 CORE_GENERAL 对话继续生效。
 *
 * updateGlobalFacts：每次 sector.getMemory() 都会重新执行；写入的演示事实使用 MOD
 * 专属前缀且过期时间为 0，符合 API 对 facts 写法的要求。
 *
 * 被 picker 选中的对话框由同包类 GearInInteractionDialog 承担 (一文件一角色，见该文件)。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    /** 演示触发势力：齿轮工业势力 id(C3-S2 定义，本节只消费不定义)。 */
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
        // transient：不随存档保存，每次读档后必须重新注册 (见页面调用点片段)。
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
        return null; // 不处理：把选择权留给其他插件 (原版 CORE_GENERAL 对话)
    }

    @Override
    public void updateGlobalFacts(MemoryAPI memory) {
        // 每次 sector.getMemory() 都会重新执行本方法；0 过期防止事实滞留污染记忆。
        memory.set(DEMO_GLOBAL_FACT, true, 0f);
    }
}
```

:::

::: details 完整成品：GearInInteractionDialog

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
 * 完整最小例：被生涯插件 picker 选中的交互对话框 (InteractionDialogPlugin 角色)。
 *
 * 由 GearInCampaignPlugin.pickInteractionDialogPlugin 在 pick 时现场创建并以
 * PickPriority.MOD_SPECIFIC 返回，证明 "本插件的选择胜出" 与 "facts 已写入"，
 * 提供 "刷新事实" 与 "离开" 两个选项。
 *
 * refreshFact() 读 Global.getSector().getMemory()—— 这次读取本身会触发全插件
 * (含 GearInCampaignPlugin) 的 updateGlobalFacts，因此显示值必然反映
 * "已注册 + facts 生效" 两个事实；这里只读不写，不构成递归。
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

:::
