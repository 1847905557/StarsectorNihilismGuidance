# 接入可选依赖

本节为 MOD 接入可选依赖：依赖存在时启用对应功能，缺失时继续启动。代码型依赖通过启用状态判断和适配器隔离调用，数据型依赖则按对方约定提供配置文件。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 解释软依赖 (可选依赖) 与硬依赖的三点分界 (声明、检查、失败结果)，并区分数据型软依赖与代码型软依赖；
- 解释脚本类加载链的父子关系 (Janino 源码加载器 → jar URLClassLoader → 脚本安全过滤器 → 游戏核心)，并说明 "jar 类优先于同名 `.java`"、"全部启用 MOD 的 jar 平铺在同一个类加载器"、"只有启用 MOD 的 jar 在类路径上" 三个结论；
- 解释脚本层的安全过滤器：为什么脚本代码禁止驱动 `java.lang.reflect`(`Method`/`Field`/`Constructor`/`Proxy`) 与 `java.io.File`；
- 用 `isModEnabled(id)`(MOD 启用状态，逐字比较) 做主判据做调用隔离，识别把显示名当 id 传的误判场景；解释数据型软依赖 "约定位置放配置文件" 的形态；
- 说出类存在性检测 (`Class.forName` / LazyLib `ModUtils.isClassPresent`) 属进阶手段、默认不需要，及其适用场景 (类被其他 MOD 间接携带等)；
- 写出 "直接引用收敛在适配器类、检查分支内懒解析、缺依赖回落默认值" 的适配器 (`GearInCompatBridge` 模式)，并从日志与行为定位 "未启用分支 / 类缺失 / 反射受限" 三类症状。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- MOD 目录结构、`mod_info.json` 必填/可选字段、`enabled_mods.json` 启用列表、启动器与 `starsector.log`(C01-S01)。
- 编译 JAR 链路 (`src` → `jars\GearIndustry.jar`) 与 "jar 类优先于同名 `.java`" 的加载结论 (C06-S01)。
- `modPlugin` 入口与 `onApplicationLoad()` 时机；本节的运行时检测接入沿用该入口 (C06-S02)。
- 硬依赖 (hard dependency) 声明、`CompatLevel` 检查、`isModEnabled`/`getModSpec`/`getAvailableModsCopy` 的运行时语义 (C12-S01)；软依赖是本节首次完整讲解。
- LazyLib `ModUtils.isModEnabled`/`isClassPresent` 的语义分工 (C12-S02)。
- LunaLib 设置 (`LunaSettings.csv`、`LunaSettings.getBoolean` 等) 的读取与保存细节 (C13-S03)；本节只采用其结论，设置项 `GearIn_fullGraphicEffect` 的定义在 C13-S03 的 `LunaSettings.csv` 中，本节只消费。

## 软依赖：定义与分界

C12-S01 的硬依赖由启动器把关，缺失时 MOD 直接不可用。本节处理相反的约定：本 MOD 的代码或数据引用了另一个 MOD 的能力，但那个 MOD 缺失或未启用时，本 MOD **仍然可以启动并降级运行**，这就是软依赖 (可选依赖，optional dependency)。与硬依赖对照：

| 维度 | 硬依赖 (C12-S01)| 软依赖 (本节)|
|---|---|---|
| 声明 | 写在 `mod_info.json` 的 `dependencies` 数组 (`id` 必填)| **不写**进 `dependencies`；写了就变成强制安装 |
| 检查 | 启动器的兼容检查把关 (缺失 → 红色不可勾选/自动禁用)| 代码在运行期自检 (`isModEnabled` / 类存在性)，缺失不拦启动 |
| 失败结果 | 缺失时 MOD 不可用 (启动器拦截或代码侧 `NoClassDefFoundError`)| 缺失时按检测结果分支，走降级行为，不报错 |

两种形态：

1. **数据型软依赖 (微型兼容)**：对方 MOD 按约定路径读取您的 CSV/JSON(`Global.getSettings().getMergedSpreadsheetDataForMod(...)` / `getMergedJSONForMod(...)`)，本 MOD 的代码零引用对方 —— 天然兼容，只要把数据文件放在约定目录。规范 (成熟实例规范原文)：微型兼容入口**不允许**出现在 `mod_info.json` 的 `dependencies` 中 —— 它没有代码引用，声明硬依赖只会给用户增加无谓的强制安装。
2. **代码型软依赖**：本 MOD 代码要调用对方 API 类 (如 `lunalib.lunaSettings.LunaSettings`)。
  - 这是本节主体：检测、隔离、降级三段缺一不可。代码引用了对方类却不做任何处理，等于把软依赖写成了隐式硬依赖 —— 缺失时运行期 `NoClassDefFoundError`(硬依赖故障分析中的同款症状)。

## 脚本类加载链与安全过滤器

代码型软依赖的风险来自类路径：对方类在不在脚本类路径上，由脚本类加载链决定，所以先看清这条链。启动资源加载期，脚本类加载器建立的脚本类加载链共三节 (脚本加载入口与启动加载线程同构)：

```text
Janino JavaSourceClassLoader（子，编译 data/scripts 下的 .java）
        ↓ 委托
URLClassLoader（中间，平铺全部启用 MOD 的 jar）
        ↓ 委托
脚本类加载器（父，脚本安全过滤器；其父是游戏进程类加载器）
```

1. **jar 收集**：启动器遍历**启用列表**中的 MOD，把每个 MOD 的 `jars` 数组展开为 `<MOD 路径>/<jar 路径>` 追加进脚本类加载器的 jar 列表 (按列表倒序)；随后脚本类加载器把这些路径变成 URL 数组交给 `URLClassLoader`。
2. **jar 优先**：类名解析从 Janino 发起，委托向上先到 jar 加载器 —— 同名类 jar 里已有就跳过编译 (日志 `Class [...] already loaded (perhaps from jar file, or due to a reference from another class), skipping compilation.`)。这是 "jar 类永远优先于同名 `.java`" 的机制来源 (前序编译链路讲解中已确立的结论)。
3. **MOD 之间不隔离**：全部启用 MOD 的 jar 在**同一个** `URLClassLoader` 里平铺。
  - 两个 MOD 发布同一个全限定类名时，`findClass` 按 URL 顺序先到先得 —— 先被搜索到的 jar 胜出，静默生效，没有任何冲突提示。
  - 软依赖的 "隔离" 不是类路径上的隔离，而是**引用点**上的隔离 (见 "适配器模式")。
4. **未启用的 MOD 不可见**：jar 收集只遍历启用列表，所以**未启用 MOD 的类不在脚本类路径上**。
  - 对单 jar 的可选 MOD，"启用" 与 "类在路径上" 两个判据在这条链上一致；判据差异只在类被其他 MOD 间接携带等场景出现 (见 "检测判据")。

脚本层与游戏核心之间的过滤器 (脚本安全过滤器的加载检查) 是本节另一条硬边界：

::: caution 脚本层安全过滤器是硬边界
脚本链上的类 (jar 类与 `.java` 类同链、同规则)**禁止**驱动：

- `java.io.*`(白名单类如 `BufferedReader`/`InputStream` 除外)；
- `java.nio.file.File*`
- `javax.script.*`
- `java.util.prefs.*`
- `sun.reflect.misc.MethodUtil`

**禁止**驱动 `java.lang.reflect.*`—— 仅放行 `AnnotatedElement`/`InvocationTargetException`/`Type`/`GenericDeclaration`(后两个正是 "接住反射失败" 所需的类型)，

`Method`/`Field`/`Constructor`/`Proxy`/`Modifier`/`Parameter` 等一律拦截。

违反的可观察结果：加载被拦截的类名时抛 `SecurityException: File access and reflection are not allowed to scripts.`，运行期崩溃，日志记 `ERROR`。

`java.lang.Class` 本身不在拦截范围：`Class.forName(...)` 可以正常使用 —— 这正是一切类存在性检测的前提。
:::

实例化失败的形态 (脚本类加载器按名实例化脚本)：`Class.forName(name, true, sourceClassLoader)` + `newInstance()`；

抛 `Exception` 包装成 `RuntimeException("Problem loading class [...]")`，而 `NoClassDefFoundError`/`ExceptionInInitializerError` 是 `Error`，不包装直接冒泡 (硬依赖讲解中已证)。

这条链能回答两个问题：MOD 勾没勾、类在不在。主流判据只用前者；"类在不在" 属进阶探测，见下。

## 检测判据与调用隔离

### 判据一（主线）：MOD 启用状态 (isModEnabled)

代码型软依赖的主判据。`Global.getSettings().getModManager().isModEnabled(id)`：id 是否在启用列表，**逐字比较、大小写敏感**(`ModManagerAPI.isModEnabled` 逐字比较语义)。语义 = "这个 MOD 被勾选了"。

- **必须**传对方 `mod_info.json` 的 `id` 字段值，不是显示名：LunaLib 的 id 是 `lunalib`，不是 `LunaLib`。
  - 用显示名当 id 的可观察结果：`isModEnabled` 恒返回 `false`，功能永远降级，且日志查不出任何报错。
- 成熟实例：只用 `isModEnabled` 判断软依赖是否可用；`lunalib` 没有出现在这些 MOD 的 `dependencies` 里 —— 这就是软依赖的实证。

### 判据二（数据型）：约定位置配置文件

数据型软依赖 (微型兼容) 的判据不是代码检测：把数据文件放到对方约定读取的位置 (如 `data/config/...` 固定路径)，本 MOD 零代码引用对方。对方缺失时文件不被读取，功能自然降级，不需要任何运行期判断 —— "约定位置" 本身即判据 (完整实例见 C13-S03 的 `LunaSettings.csv`)。

### 进阶：类存在性检测 (Class.forName / ModUtils.isClassPresent)

语义 = "这个类在不在脚本类路径上"。两种写法：

- 原生：`Class.forName("lunalib.lunaSettings.LunaSettings")` 包在 `try { ... } catch (ClassNotFoundException e)` 里，缺类返回 `false`。注意单参 `Class.forName` 会触发类初始化 (执行静态块)；只想探测存在性、不想承受初始化副作用时，用双参 `Class.forName(name, false, loader)`(`initialize=false`)。
- 库写法：LazyLib `ModUtils.isClassPresent(classCanonicalName)`，语义同样是 classpath 存在性。

::: tip 单参与双参 Class.forName 怎么选
单参 `Class.forName(name)` 会触发类初始化 (执行静态块)，适合需要目标类完全就绪的完整探测；

只想回答 "类在不在"、不想承受初始化副作用 (静态块打日志、抛异常等) 时，用双参 `Class.forName(name, false, loader)`(`initialize=false`)，只查类路径、不触发初始化。

本节示例用单参 (成熟社区惯用)，两种写法的判据语义相同。
:::

为什么默认不需要：启用列表语义下，已启用 MOD 的 jar 必然在脚本类路径上 (见 "脚本类加载链")，`isModEnabled` 一个判据已覆盖绝大多数场景；类存在性检测只在 "类可能被其他 MOD 间接携带"、"启用但 jar 损坏/版本漂移" 等特殊场景才需要补上。不鼓励默认使用，但不禁止 —— 需要时与启用判据组合使用是安全写法。

### 分工与误用 (必须遵守的规则)

- `isModEnabled` 表达**启用状态**，是代码型软依赖的主判据；类存在性检测表达**类路径存在性**，属进阶手段，两个判据**必须**按各自语义使用 (成熟实例规范原文)。
- 版本判断 (补充判据，可选)：需要按可选 MOD 版本分支时，用 `getModSpec(id).getVersionInfo()` 读已安装版本 (`getModSpec` 只查启用列表，调用前必须先过 `isModEnabled`)。
- (进阶) 把类存在性检测**当作**启用判断 (只查 `isClassPresent`，不查 `isModEnabled`) 的可观察结果：某类被其他 MOD 间接携带、或类在路径上而所属 MOD 未启用时，检测误报 "可用"，检查失效，调用可选 API 的代码在无依赖环境下被放行并崩溃 (成熟实例陷阱原文)。
- 组合判据是进阶安全写法：`启用 && 类在路径上` 才允许调用可选 API。

::: warning 判据用错的两个常见坑
两个误用场景都没有显式报错，只能从日志降级行或崩溃点反推：一是把显示名当 `id` 传 (如 `LunaLib` 而非 `lunalib`)，`isModEnabled` 恒返回 `false`，功能永久降级，日志查不出任何报错；

二是 (进阶) 把类存在性检测当作启用判断，某类被其他 MOD 间接携带、或类在路径上而所属 MOD 未启用时，检测误报 "可用"，检查通过后被放行，调用可选 API 的代码在无依赖环境下崩溃。
:::

## 适配器模式与反射边界

判据只回答 "能不能用"，调用可选 API 的代码必须落在某处。先看一个常见但不成立的做法，再看正确形态。

### 为什么 "反射桥接" 不是可选手段

任务清单常见的 "反射桥接" 思路 —— 运行时用 `Class.forName` + `getMethod`/`invoke` 调可选 API—— 在脚本链上**不可行**：

`java.lang.reflect.Method` 等驱动反射的类型被脚本类加载器的过滤器拦截 (见 "脚本类加载链与安全过滤器")。

`Class.forName` 只能回答 "类在不在"；一旦代码索取 `Method`/`Field`/`Constructor`，立即抛 `SecurityException: File access and reflection are not allowed to scripts.`。

可观察结果就是运行期崩溃，且没有任何绕过途径 (过滤器在父加载器上，jar 类与 `.java` 类同等受限)。

### 适配器的正确形态：编译期直接引用 + 收敛 + 懒解析

真正可行且被成熟实例使用的形态是**静态桥接**：

1. **直接引用**：可选 API 的编译期引用 (如 `LunaSettings.getBoolean(...)`) 写进一个专用适配器类 (`GearInCompatBridge`；与成熟实例同构)。
2. **收敛**：全工程只有适配器这一处出现 `lunalib.*` 类名；使用方只 import 适配器类。
  - 编译期类路径上有可选 jar(`compileOnly` 依赖)，运行期才谈得上缺失。
3. **懒解析**：JVM 按需解析常量池引用 —— 适配器类本身在可选依赖缺失时**可以正常加载**(类加载不触发方法体内引用的解析)；`LunaSettings` 的符号引用只在检查分支**第一次执行**时才解析。
  - 无依赖时检测返回 false，分支不执行，引用永不解析，`NoClassDefFoundError` 不会出现。这正是 "缺依赖也能启动" 的机制基础。

::: info 为什么 "缺依赖也能启动"
类的加载与方法体引用的解析是两个时刻：加载适配器类本身不需要解析 `LunaSettings` 的符号引用，只有执行到持有该引用的那行代码时才解析。

检查分支在无依赖时永远不执行，引用就永远不被解析 ——`NoClassDefFoundError` 只在 "引用被实际解析但类不在" 时出现。

这就是 "适配器 + 检查分支" 能同时满足编译期强引用与运行期可缺失的原因。
:::

::: caution 适配器必须遵守的规则
- 可选 API 的直接引用**必须**全部收敛在检查分支内 (`if (可用) { ... }` 的 then 分支)。
  - 字段初始化、静态块、构造器、无检测的公开方法体里出现可选类引用 → 适配器类加载/首次调用时 `NoClassDefFoundError` 或 `ExceptionInInitializerError`(`Error` 冒泡，不包装，启动或运行中止)。
- 适配器入口**必须**先过启用判据 (`isModEnabled`) 再碰可选 API；无检测的直接调用在缺依赖时 `NoClassDefFoundError`。
  - 推荐判据：`isModEnabled(id)`(主线)；类存在性检测属进阶加固，特殊场景再叠加 (组合判据 `启用 && 类在路径上`)。
:::

适配器类**不要**注册进引擎按名实例化的列表 (`settings.json` 的 `plugins`、`modPlugin`、rules 脚本列等)—— 那些入口在加载期就 `newInstance`，任何构造路径上的引用都会立即解析，懒解析保护失效 (脚本类加载器的加载行为)。

适配器是普通工具类，由本 MOD 自己的代码按名调用。

脚本链上**禁止**驱动 `java.lang.reflect` 与 `java.io.File`(见 "脚本类加载链与安全过滤器")，适配器同样适用。

## 降级边界：无依赖时的行为

引用收敛之后还差最后一段：缺依赖时返回什么、日志怎么记。降级边界 = 可选依赖缺失时，适配器返回的默认值/默认动作，以及日志里可观测的降级记录。两条原则 (成熟实例归纳)：

1. **缺依赖 ≠ 报错**：检测失败走默认分支，写一行 INFO 日志说明降级原因 (如 `GearIn compat: LunaLib unavailable, degrading to fallback=true`)。
  - 用户在 `starsector.log` 里能看到 "这个功能为什么没接上"。
2. **降级值必须让 MOD 行为完整可用**：默认值由使用方按业务语义传入 (成熟实例缺 LunaLib 时返回 `true` = 全特效，功能不缺失，只是少了用户可配置入口)。

可选 API 自身也可能返回 "缺值"：`LunaSettings.getBoolean(modId, fieldId)` 返回可空 `Boolean`，mod 或字段缺失时记录 ERROR 日志并返回 `null`。

**必须**判空后再拆箱 —— 直接拆箱 `null` 的可观察结果：`NullPointerException`，运行期崩溃 (成熟实例陷阱原文)。

判空失败同样回落默认值。

## 文件与 API 链路

以上各节把处理链拆开了，先用一张表收拢每个环节的落点；下一节的最小例逐项实现它们。

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 声明 (不写)| 可选 MOD 的 `mod_info.json`(`id`: `lunalib`)| 软依赖**不**出现在 `dependencies` 数组里；代码侧用 `isModEnabled("lunalib")` 运行期自检 |
| 检测 1 | `Global.getSettings().getModManager().isModEnabled(id)` | `ModManagerAPI` 启用列表逐字比较、大小写敏感 |
| 检测 2 (进阶) | `Class.forName(...)` / LazyLib `ModUtils.isClassPresent(...)` | 脚本类路径存在性；默认不需要，特殊场景 (类被间接携带、jar 损坏防御) 才用 |
| 设置数据 | 可选 MOD 读 `data/config/LunaSettings.csv`(fieldID `GearIn_fullGraphicEffect`；定义在 C13-S03)| LunaLib 的 `LunaSettingsLoader` 加载默认值；文件只有 LunaLib 启用时才会被读取 (C13-S03)|
| 适配 | `lunalib.lunaSettings.LunaSettings.getBoolean(modId, fieldId)` | 返回可空 `Boolean`，mod/字段缺失时记录 ERROR 日志并返回 `null` |
| 类加载 | 启动器收集 → 脚本类加载器加载 | 启用 MOD 的 jar 平铺进单一 `URLClassLoader`；未启用 MOD 的 jar 不在路径上 |
| 过滤器 | 脚本安全过滤器的加载检查 | `java.io.*`/`java.lang.reflect.*`(白名单除外)→ `SecurityException` |
| 实例化 | 脚本类加载器按名实例化 | `Class.forName` + `newInstance`；`Exception` 包装为 `Problem loading class [...]`，`Error` 冒泡 |
| 日志 | `starsector-core\starsector.log` | `GearIn compat:` 降级/缺失行；LunaLib 侧的 `LunaSettings:` ERROR 行 |

## 完整最小例

在前序累计工程 (C06-S01) 的布局上追加两处 (MOD 根目录 `Starsector\mods\GearIndustry`)：一个适配器类、一行接入调用。

本节示例新增：类 `data.scripts.gearin.modcompat.GearInCompatBridge`(归本节所有)；设置项 `GearIn_fullGraphicEffect` 的定义在 C13-S03 的 `LunaSettings.csv` 中 (第 7 行)，本节只消费。

`GearInModPlugin` 归前序单元所有，本节只调用它。

`Starsector\mods\GearIndustry` 是教程虚构工程路径，本节内部引用保持自洽。

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                # 不加 lunalib 依赖（软依赖不声明）
├── data\
│   └── config\
│       └── LunaSettings.csv     # C13-S03 定义；只有 LunaLib 启用时被消费
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin                     # 前序单元所有，本节追加一行调用
│       └── modcompat\
│           └── GearInCompatBridge              # 本节新增
└── jars\
```

`data/config/LunaSettings.csv` 的完整定义 (含本节消费的 `GearIn_fullGraphicEffect` 行) 在 C13-S03，本节不新增文件。

### GearInCompatBridge

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * 软依赖适配器（兼容层）：把对可选依赖 LunaLib 的全部直接引用收敛在本类，
 * 工程里只有这一处出现 lunalib.* 类；其余代码只消费本类的静态方法。
 *
 * 调用链：isModEnabled 判定 MOD 启用状态，通过才进入持有 LunaSettings 直接
 * 引用的分支；LunaLib 缺失或设置字段缺失时回落到调用方传入的默认值（降级边界）。
 * 类存在性检测属进阶手段（见本节"进阶"小节），默认不需要。
 */
public class GearInCompatBridge {

    /** LunaLib 的 MOD id，与 Starsector\mods\Lunalib\mod_info.json 的 id 字段逐字一致。 */
    public static final String LUNA_MOD_ID = "lunalib";

    /** 本 MOD 在 data/config/LunaSettings.csv 中登记的设置项 fieldID（文件由 C13-S03 定义）。 */
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private GearInCompatBridge() {
    }

    /** 判据：LunaLib 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    /** 可用性判据：MOD 已启用即认为可选 API 可用（类存在性检测属进阶手段，默认不启用）。 */
    public static boolean isLunaSettingsUsable() {
        return isLunaLibEnabled();
    }

    /**
     * 适配入口（降级边界）：LunaLib 可用时读取 GearIn_fullGraphicEffect 设置，
     * 否则返回调用方传入的默认值。LunaSettings.getBoolean 返回可空 Boolean，
     * 字段缺失时 LunaLib 记录 ERROR 日志并返回 null，这里同样回落到默认值。
     */
    public static boolean getFullGraphicEffect(boolean fallback) {
        if (!isLunaSettingsUsable()) {
            Global.getLogger(GearInCompatBridge.class).info(
                "GearIn compat: LunaLib unavailable, degrading to fallback=" + fallback);
            return fallback;
        }
        Boolean value = LunaSettings.getBoolean(LUNA_MOD_ID, FULL_GRAPHIC_FIELD);
        if (value == null) {
            Global.getLogger(GearInCompatBridge.class).info(
                "GearIn compat: field [" + FULL_GRAPHIC_FIELD + "] missing in LunaSettings, fallback=" + fallback);
            return fallback;
        }
        return value;
    }
}
```

对应关系：

- `LUNA_MOD_ID = "lunalib"`：对方 `mod_info.json` 的 `id` 字段值，逐字一致 (不是显示名 `LunaLib`)；这是 `isModEnabled` 判据的输入。
- `isLunaSettingsUsable()`：启用判据 (`isLunaLibEnabled()` 的别名)，适配入口的唯一放行条件；类存在性检测属进阶加固，示例不默认启用。
- `LunaSettings.getBoolean(LUNA_MOD_ID, FULL_GRAPHIC_FIELD)`：唯一持有可选 API 直接引用的语句，位于检查分支内；依赖缺失时该分支不执行，符号引用永不解析，`NoClassDefFoundError` 不会出现 (懒解析)。
- `Boolean value` + 判空：`getBoolean` 返回可空 `Boolean`，字段缺失时 LunaLib 打 ERROR 日志并返回 `null`；判空失败再回落默认值，避免拆箱 NPE。
- `Global.getLogger(GearInCompatBridge.class)`：降级/缺失日志走 `starsector-core\starsector.log`。
- 全类只有 `import lunalib.lunaSettings.LunaSettings` 一处可选依赖引用：这就是 "收敛"。

### 接入片段 (在 GearInModPlugin.onGameLoad 中追加一行)

```java
@Override
public void onGameLoad(boolean newGame) {
    // 软依赖适配器接入：把 LunaLib 设置与降级默认值收敛在 GearInCompatBridge
    boolean fullGraphic = GearInCompatBridge.getFullGraphicEffect(true);
    // ... 既有回调内容（前序讲解的 CLASS_MARK 门控等）……
}
```

`GearInModPlugin` 类归前序单元所有，本节只调用它；本节示例类不含该片段。

### 构建

```text
gradle jar
```

编译注意事项：适配器持有 `LunaSettings` 的直接引用，**编译期**类路径上必须有 LunaLib 的 jar(`compileOnly` 依赖或开发机已装 Lunalib)；运行期没有则走降级，不影响启动。

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/modcompat/GearInCompatBridge.class`(可用 `jar tf` 核对)。

## 验证方案

前置数据：前序累计工程 (C06-S01) 基础上追加本节内容 (沿用 C13-S03 的 `data/config/LunaSettings.csv`、新增 `GearInCompatBridge`、`GearInModPlugin.onGameLoad` 内一行调用)；本机已安装 Lunalib。

1. 构建核对：在 MOD 根目录执行 `gradle jar`，确认 `BUILD SUCCESSFUL`；用 `jar tf jars\GearIndustry.jar` 查看 `data/scripts/gearin/modcompat/GearInCompatBridge.class` 是否在产物内。
2. 降级路径：启动器**不**勾选 Lunalib，勾选 "齿轮工业" 启动并进入生涯，查看 `starsector.log` 中的 `GearIn compat:` 与 `LunaSettings:` 行。
3. 完整路径：勾选 Lunalib 重启进入生涯，打开 LunaLib 设置界面，观察 `GearIn_fullGraphicEffect` 项并查看日志。

预期结果：

- 构建：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/modcompat/GearInCompatBridge.class`。
- 降级路径：`starsector.log` 出现 `GearIn compat: LunaLib unavailable, degrading to fallback=true`，`getFullGraphicEffect(true)` 返回 `true`(全特效默认行为)，游戏正常且没有由 `gear_in` 引起的 `ERROR`。
- 完整路径：`starsector.log` 无降级行；LunaLib 设置界面出现 `GearIn_fullGraphicEffect` 项 (默认 `true`)，改值为 `false` 保存后重读存档，行为变为 `false` —— 设置值生效。
- 字段缺失：LunaLib 打 `LunaSettings: Value GearIn_fullGraphicEffect of type Boolean not found in JSONObject`(ERROR)，适配器打 `GearIn compat: field [GearIn_fullGraphicEffect] missing in LunaSettings, fallback=true`，两行成对出现，返回默认值且无 NPE。
- 类存在性 (进阶，可选验证)：类存在性检测属进阶手段，本节示例不包含该判据；需要验证时按 "进阶" 小节写法在启用/未启用两种状态下分别探测。

清理：恢复 `LunaSettings.csv` 中临时删除的 `GearIn_fullGraphicEffect` 行。

## 故障分析

### 无 LunaLib 时启动正常，但调用 `LunaSettings.getBoolean` 的代码抛 `NoClassDefFoundError`

- 触发：可选类引用泄漏到了检查分支之外 —— 字段初始化、静态块、构造器，或某个无检测的公开方法体里直接出现 `lunalib.*` 类。
- 原因：这些位置的符号引用在类加载或首次调用时就被解析，类不在脚本类路径上时 `NoClassDefFoundError`/`ExceptionInInitializerError` 冒泡 (`Error` 不包装，由脚本类加载器按名实例化时直接冒出)，启动或运行中止。
- 修正：全工程搜索 `lunalib` 引用，除适配器检查分支外一律移除；把可选引用收敛进 `if (isLunaSettingsUsable())` 的 then 分支，重启后确认降级行正常出现。

### `isModEnabled("lunalib")` 恒为 false，但启动器里明明勾着 Lunalib

- 触发：把显示名 `LunaLib` 当 id 传。
- 原因：`isModEnabled` 按 id 逐字比较、大小写敏感，显示名与对方 `mod_info.json` 的 `id` 字段不一致，所以恒返回 false。
- 修正：复制对方 `mod_info.json` 的 `id` 字段值；重启后确认降级行消失。

### (进阶) `isClassPresent` 返回 true 但功能没有接上，或反过来误放行崩溃

- 触发：把类存在性判据 (进阶手段) 当作启用判据使用。
- 原因：类存在性只回答 "类在不在脚本类路径上"，不回答所属 MOD 是否启用；可选类被其他 MOD 间接携带、而所属 MOD 未启用时，检测返回 true，检查通过后被放行，调用可选 API 的代码在缺依赖环境下运行并崩溃。
- 修正：回退到主线判据 `isModEnabled`，或使用进阶组合判据 `启用 && 类在路径上`(成熟实例陷阱原文)，改后分别用启用与未启用两种状态复验。

### 运行期 `SecurityException: File access and reflection are not allowed to scripts.`

- 触发：脚本链代码驱动 `java.lang.reflect`(`getMethod`/`getField`/`newProxyInstance` 等) 或 `java.io.File`。
- 原因：脚本安全过滤器在父加载器上拦截这些类型，jar 类与 `.java` 类同等受限，没有绕过途径。
- 修正：改用本节适配器形态 (编译期直接引用 + 检查分支)；`Class.forName` 只用于存在性检测，不索取 `Method`/`Field`。

### `NullPointerException`，堆栈指向拆箱 `LunaSettings.getBoolean(...)` 返回值

- 触发：字段未定义或 mod 未登记时 `getBoolean` 返回 `null`，代码直接拆箱。
- 原因：`getBoolean` 返回可空 `Boolean`，缺值时 LunaLib 先打一条 ERROR 再返回 `null`；对 `null` 拆箱即抛 NPE。
- 修正：判空后回落默认值 (成熟实例陷阱原文)，再用删除字段场景复验无 NPE。

### 两个 MOD 提供同一个全限定类名，行为时而正确时而错乱

- 触发：两个 MOD 共享同一个 `URLClassLoader`，同名类按 URL 顺序先到先得 (脚本类加载器的加载行为)。
- 原因：启用顺序或优先级变化后，`Class.forName` 返回的可能是对方实现，功能表现随启用组合漂移。
- 修正：这是 MOD 之间不隔离的固有现象 —— 本 MOD 的类名保持唯一前缀 (`GearIn*`)，可选 API 一律通过对方的正式类访问。

### 把软依赖误写进 `dependencies`，用户缺 LunaLib 时 MOD 直接被启动器拦截

- 触发：适配器已经做好降级，却仍把 `lunalib` 声明为硬依赖。
- 原因：`dependencies` 里的条目走硬依赖的启动器拦截语义，LunaLib 缺失时 "齿轮工业" 行红色不可勾选或自动禁用，用户无法选择降级运行。
- 修正：代码型软依赖**不**写 `dependencies`；只有真正的强制引用才写 (硬依赖讲解)。

### 降级日志缺失，功能悄悄变了行为

- 触发：检测失败分支不写日志，或降级值写死在适配器里。
- 原因：没有日志，用户与编者都无法从日志判断 "设置没接上" 还是 "被关闭"。
- 修正：降级分支写 INFO 日志 (`GearIn compat: ...`)，降级值由调用方传入 (业务默认语义明确)。

## 总结

本节走通了软依赖的三段处理链：判据 (`isModEnabled`，主线) 决定能不能碰可选 API，适配器决定引用在哪里被解析，降级决定缺依赖时返回什么。三条边界独立失效 —— 判据用错会静默误判，引用泄漏会直接崩溃，降级值或日志缺失会让行为悄悄偏离，因此验证要覆盖无依赖、有依赖和故障注入三条路径。

脚本链的安全过滤器拦截 `java.lang.reflect` 驱动与 `java.io.File`，决定了 "反射桥接" 不可行，可选 API 只能以编译期直接引用收敛进适配器，再靠懒解析保住缺依赖时的启动。

这套骨架不绑定 LunaLib。接入其他可选 MOD 时，适配器按目标 API 换形、降级默认值按业务语义传入、需要版本分支时在启用判据上补 `getVersionInfo()` 一层 (类存在性检测属进阶，特殊场景再叠加)；排查既有软依赖问题时按同一顺序核对：判据是否用对、引用是否全部在检查分支内、降级分支是否写日志、`dependencies` 是否误写。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局入口，`getSettings()` 与 `getLogger(Class)` 所在。
- `com.fs.starfarer.api.SettingsAPI`：`getModManager()` 取得 MOD 管理器。
- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled(id)`(启用列表逐字比较、大小写敏感) 等运行时检测方法。
- `com.fs.starfarer.api.ModSpecAPI`：`getVersionInfo()` 读已安装版本 (版本判断用)。
- `lunalib.lunaSettings.LunaSettings`：可选依赖 API，`getBoolean(modId, fieldId)` 返回可空 `Boolean`。
- `data.scripts.gearin.modcompat`：本节示例包，软依赖适配器 `GearInCompatBridge` 所在。
- `data/config/LunaSettings.csv`(MOD 根目录起；定义在 C13-S03)：本节消费的设置项 `GearIn_fullGraphicEffect` 的默认值登记处。
- `Starsector/mods/Lunalib/mod_info.json`(游戏安装根起)：`id` 字段逐字比对对象。
- `Starsector/starsector-core/starsector.log`(游戏安装根起)：降级/缺失日志的核对位置。
