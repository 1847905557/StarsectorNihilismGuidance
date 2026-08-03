# 可选依赖检测、类加载隔离与降级适配

本节讲解软依赖 (可选依赖，optional dependency) 的完整处理链：如何在代码里检测一个可选 MOD 是否可用、脚本类加载链如何决定 "类在不在"、为什么适配器要收敛直接引用，以及缺少依赖时 MOD 必须降级到什么行为。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 说出软依赖 (可选依赖) 与硬依赖的三点分界 (声明、检查、失败结果)，并区分数据型软依赖与代码型软依赖；
- 画出脚本类加载链的父子关系 (Janino 源码加载器 → jar URLClassLoader → 脚本安全过滤器 → 游戏核心)，说出 "jar 类优先于同名 `.java`"、"全部启用 MOD 的 jar 平铺在同一个类加载器"、"只有启用 MOD 的 jar 在类路径上" 三个结论；
- 解释脚本层的安全过滤器：为什么脚本代码禁止驱动 `java.lang.reflect`(`Method`/`Field`/`Constructor`/`Proxy`) 与 `java.io.File`，以及 `Class.forName` 为什么不受影响；
- 用两个判据组合检测软依赖：`isModEnabled(id)`(MOD 启用状态，逐字比较) 与类存在性检测 (`Class.forName` 或 LazyLib `ModUtils.isClassPresent`)，并说出把后者当作启用判断时的误判场景；
- 写出 "直接引用收敛在适配器类、守卫分支内懒解析、缺依赖回落默认值" 的适配器 (`GearInCompatBridge` 模式)，并从日志与行为定位 "未启用分支 / 类缺失 / 反射受限" 三类症状。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- MOD 目录结构、`mod_info.json` 必填/可选字段、`enabled_mods.json` 启用列表、启动器与 `starsector.log`(C01-S01)。
- 编译 JAR 链路 (`src` → `jars\GearIndustry.jar`) 与 "jar 类优先于同名 `.java`" 的加载结论 (C06-S01)。
- `modPlugin` 入口与 `onApplicationLoad()` 时机；本节的运行时检测接入沿用该入口 (C06-S02)。
- 硬依赖 (hard dependency) 声明、`CompatLevel` 检查、`isModEnabled`/`getModSpec`/`getAvailableModsCopy` 的运行时语义 (C12-S01)；软依赖是本节首次完整讲解。
- LazyLib `ModUtils.isModEnabled`/`isClassPresent` 的语义分工 (C12-S02)。
- LunaLib 设置 (`LunaSettings.csv`、`LunaSettings.getBoolean` 等) 的读取与保存细节 (C12-S05)；本节只消费其结论。

## 软依赖：定义与分界

软依赖 (可选依赖，optional dependency)= 本 MOD 的代码或数据引用了另一个 MOD 的能力，但那个 MOD 缺失或未启用时，本 MOD **仍然可以启动并降级运行**。与硬依赖对照：

| 维度 | 硬依赖 (C12-S01)| 软依赖 (本节)|
|---|---|---|
| 声明 | 写在 `mod_info.json` 的 `dependencies` 数组 (`id` 必填)| **不写**进 `dependencies`；写了就变成强制安装 |
| 检查 | 启动器的兼容检查把关 (缺失 → 红色不可勾选/自动禁用)| 代码在运行期自检 (`isModEnabled` / 类存在性)，缺失不拦启动 |
| 失败结果 | 缺失时 MOD 不可用 (启动器拦截或代码侧 `NoClassDefFoundError`)| 缺失时按检测结果分支，走降级行为，不报错 |

两种形态：

1. **数据型软依赖 (微型兼容)**：对方 MOD 按约定路径读取您的 CSV/JSON(`Global.getSettings().getMergedSpreadsheetDataForMod(...)` / `getMergedJSONForMod(...)`)，本 MOD 的代码零引用对方 —— 天然兼容，只要把数据文件放在约定目录。规范 (成熟实例规范原文)：微型兼容入口**不允许**出现在 `mod_info.json` 的 `dependencies` 中 —— 它没有代码引用，声明硬依赖只会给用户增加无谓的强制安装。
2. **代码型软依赖**：本 MOD 代码要调用对方 API 类 (如 `lunalib.lunaSettings.LunaSettings`)。
  - 这是本节主体：检测 + 隔离 + 降级三段缺一不可。代码引用了对方类却不做任何处理，等于把软依赖写成了隐式硬依赖 —— 缺失时运行期 `NoClassDefFoundError`(硬依赖故障分析中的同款症状)。

## 类加载隔离：脚本类加载链的父子关系

"隔离" 从何而来，取决于类加载链。启动资源加载期，脚本类加载器建立的脚本类加载链共三节 (脚本加载入口与启动加载线程同构)：

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

## 检测判据：isModEnabled 与类存在性

### 判据一：MOD 启用状态 (isModEnabled)

`Global.getSettings().getModManager().isModEnabled(id)`：id 是否在启用列表，**逐字比较、大小写敏感**(`ModManagerAPI.isModEnabled` 逐字比较语义)。语义 = "这个 MOD 被勾选了"。

- **必须**传对方 `mod_info.json` 的 `id` 字段值，不是显示名：LunaLib 的 id 是 `lunalib`，不是 `LunaLib`。
  - 用显示名当 id 的可观察结果：`isModEnabled` 恒返回 `false`，功能永远降级，且日志查不出任何报错。
- 成熟实例：只用 `isModEnabled` 判断软依赖是否可用；`lunalib` 没有出现在这些 MOD 的 `dependencies` 里 —— 这就是软依赖的实证。

### 判据二：类存在性 (Class.forName / ModUtils.isClassPresent)

语义 = "这个类在不在脚本类路径上"。两种写法：

- 原生：`Class.forName("lunalib.lunaSettings.LunaSettings")` 包在 `try { ... } catch (ClassNotFoundException e)` 里，缺类返回 `false`。注意单参 `Class.forName` 会触发类初始化 (执行静态块)；只想探测存在性、不想承受初始化副作用时，用双参 `Class.forName(name, false, loader)`(`initialize=false`)。
- 库写法：LazyLib `ModUtils.isClassPresent(classCanonicalName)`，语义同样是 classpath 存在性。

::: tip 单参与双参 Class.forName 怎么选
单参 `Class.forName(name)` 会触发类初始化 (执行静态块)，适合需要目标类完全就绪的完整探测；

只想回答 "类在不在"、不想承受初始化副作用 (静态块打日志、抛异常等) 时，用双参 `Class.forName(name, false, loader)`(`initialize=false`)，只查类路径、不触发初始化。

本节示例用单参 (成熟社区惯用)，两种写法的判据语义相同。
:::

### 分工与误用 (必须遵守的规则)

- `isModEnabled` 表达**启用状态**，类存在性表达**类路径存在性**，两个判据**必须**按各自语义使用 (成熟实例规范原文)。
- 把类存在性检测**当作**启用判断 (只查 `isClassPresent`，不查 `isModEnabled`) 的可观察结果：某类被其他 MOD 间接携带、或类在路径上而所属 MOD 未启用时，检测误报 "可用"，守卫失效，调用可选 API 的代码在无依赖环境下被放行并崩溃 (成熟实例陷阱原文)。
- 组合判据是安全写法：`启用 && 类在路径上` 才允许调用可选 API(本节示例即此写法)。
- 版本判断 (补充判据，可选)：需要按可选 MOD 版本分支时，用 `getModSpec(id).getVersionInfo()` 读已安装版本 (`getModSpec` 只查启用列表，调用前必须先过 `isModEnabled`)。

::: warning 判据用错的两个常见坑
两个误用场景都没有显式报错，只能从日志降级行或崩溃点反推：一是把显示名当 `id` 传 (如 `LunaLib` 而非 `lunalib`)，`isModEnabled` 恒返回 `false`，功能永久降级，日志查不出任何报错；

二是把类存在性检测当作启用判断，某类被其他 MOD 间接携带、或类在路径上而所属 MOD 未启用时，检测误报 "可用"，守卫放行后调用可选 API 的代码在无依赖环境下崩溃。
:::

## 适配器模式与反射边界

### 为什么 "反射桥接" 不是可选手段

任务清单常见的 "反射桥接" 思路 —— 运行时用 `Class.forName` + `getMethod`/`invoke` 调可选 API—— 在 RC8 脚本链上**不可行**：

`java.lang.reflect.Method` 等驱动反射的类型被脚本类加载器的过滤器拦截 (见 "类加载隔离")。

`Class.forName` 只能回答 "类在不在"；一旦代码索取 `Method`/`Field`/`Constructor`，立即抛 `SecurityException: File access and reflection are not allowed to scripts.`。

可观察结果就是运行期崩溃，且没有任何绕过途径 (过滤器在父加载器上，jar 类与 `.java` 类同等受限)。

### 适配器的正确形态：编译期直接引用 + 收敛 + 懒解析

真正可行且被成熟实例使用的形态是**静态桥接**：

1. **直接引用**：可选 API 的编译期引用 (如 `LunaSettings.getBoolean(...)`) 写进一个专用适配器类 (`GearInCompatBridge`；与成熟实例同构)。
2. **收敛**：全工程只有适配器这一处出现 `lunalib.*` 类名；使用方只 import 适配器类。
  - 编译期类路径上有可选 jar(`compileOnly` 或 fixture 类路径含 Lunalib jar)，运行期才谈得上缺失。
3. **懒解析**：JVM 按需解析常量池引用 —— 适配器类本身在可选依赖缺失时**可以正常加载**(类加载不触发方法体内引用的解析)；`LunaSettings` 的符号引用只在守卫分支**第一次执行**时才解析。
  - 无依赖时检测返回 false，分支不执行，引用永不解析，`NoClassDefFoundError` 不会出现。这正是 "缺依赖也能启动" 的机制基础。

::: info 为什么 "缺依赖也能启动"
类的加载与方法体引用的解析是两个时刻：加载适配器类本身不需要解析 `LunaSettings` 的符号引用，只有执行到持有该引用的那行代码时才解析。

守卫分支在无依赖时永远不执行，引用就永远不被解析 ——`NoClassDefFoundError` 只在 "引用被实际解析但类不在" 时出现。

这就是 "适配器 + 守卫分支" 能同时满足编译期强引用与运行期可缺失的原因。
:::

::: caution 适配器必须遵守的规则
- 可选 API 的直接引用**必须**全部收敛在守卫分支内 (`if (可用) { ... }` 的 then 分支)。
  - 字段初始化、静态块、构造器、无检测的公开方法体里出现可选类引用 → 适配器类加载/首次调用时 `NoClassDefFoundError` 或 `ExceptionInInitializerError`(`Error` 冒泡，不包装，启动或运行中止)。
- 适配器入口**必须**先过组合判据再碰可选 API(只查 `isModEnabled` 不查类存在性，遇到 "启用但 jar 损坏/类缺失" 时同样崩；只查类存在性不查启用，落入误判场景)。
  - 推荐组合判据：`启用 && 类在路径上`。
:::

适配器类**不要**注册进引擎按名实例化的列表 (`settings.json` 的 `plugins`、`modPlugin`、rules 脚本列等)—— 那些入口在加载期就 `newInstance`，任何构造路径上的引用都会立即解析，懒解析保护失效 (脚本类加载器的加载行为)。

适配器是普通工具类，由本 MOD 自己的代码按名调用。

脚本链上**禁止**驱动 `java.lang.reflect` 与 `java.io.File`(见 "类加载隔离")，适配器同样适用。

## 降级边界：无依赖时的行为

降级边界 = 可选依赖缺失时，适配器返回的默认值/默认动作，以及日志里可观测的降级记录。两条原则 (成熟实例归纳)：

1. **缺依赖 ≠ 报错**：检测失败走默认分支，写一行 INFO 日志说明降级原因 (如 `GearIn compat: LunaLib unavailable, degrading to fallback=true`)。
  - 用户在 `starsector.log` 里能看到 "这个功能为什么没接上"。
2. **降级值必须让 MOD 行为完整可用**：默认值由使用方按业务语义传入 (成熟实例缺 LunaLib 时返回 `true` = 全特效，功能不缺失，只是少了用户可配置入口)。

可选 API 自身也可能返回 "缺值"：`LunaSettings.getBoolean(modId, fieldId)` 返回可空 `Boolean`，mod 或字段缺失时记录 ERROR 日志并返回 `null`。

**必须**判空后再拆箱 —— 直接拆箱 `null` 的可观察结果：`NullPointerException`，运行期崩溃 (成熟实例陷阱原文)。

判空失败同样回落默认值。

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 声明 (不写)| 可选 MOD 的 `mod_info.json`(`id`: `lunalib`)| 软依赖**不**出现在 `dependencies` 数组里；代码侧用 `isModEnabled("lunalib")` 运行期自检 |
| 检测 1 | `Global.getSettings().getModManager().isModEnabled(id)` | `ModManagerAPI` 启用列表逐字比较、大小写敏感 |
| 检测 2 | `Class.forName(...)` / LazyLib `ModUtils.isClassPresent(...)` | 脚本类路径存在性；`java.lang.Class` 不在安全过滤器拦截范围 |
| 设置数据 | 可选 MOD 读 `data/config/LunaSettings.csv`(fieldID `GearIn_fullGraphicEffect`)| LunaLib 的 `LunaSettingsLoader` 加载默认值；文件只有 LunaLib 启用时被消费 (C12-S05)|
| 适配 | `lunalib.lunaSettings.LunaSettings.getBoolean(modId, fieldId)` | 返回可空 `Boolean`，mod/字段缺失时记录 ERROR 日志并返回 `null` |
| 类加载 | 启动器收集 → 脚本类加载器加载 | 启用 MOD 的 jar 平铺进单一 `URLClassLoader`；未启用 MOD 的 jar 不在路径上 |
| 过滤器 | 脚本安全过滤器的加载检查 | `java.io.*`/`java.lang.reflect.*`(白名单除外)→ `SecurityException` |
| 实例化 | 脚本类加载器按名实例化 | `Class.forName` + `newInstance`；`Exception` 包装为 `Problem loading class [...]`，`Error` 冒泡 |
| 日志 | `starsector-core\starsector.log` | `GearIn compat:` 降级/缺失行；LunaLib 侧的 `LunaSettings:` ERROR 行 |

## 完整最小例

在累计工程布局 (前序阶段建立) 上追加 (MOD 根目录 `Starsector\mods\GearIndustry`)：

本节示例新增：类 `data.scripts.gearin.modcompat.GearInCompatBridge`(归本节所有) 与设置项 ID `GearIn_fullGraphicEffect`(LunaLib 设置 fieldID，归本节所有)。

`GearInModPlugin` 归前序单元所有，本节只消费它。

`Starsector\mods\GearIndustry` 是教程虚构工程路径，本节内部引用保持自洽。

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                # 不加 lunalib 依赖（软依赖不声明）
├── data\
│   └── config\
│       └── LunaSettings.csv     # 可选数据文件，只有 LunaLib 启用时被消费
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin                     # 前序单元所有，本节追加一行调用
│       └── modcompat\
│           └── GearInCompatBridge              # 本节新增
└── jars\
```

`data/config/LunaSettings.csv`(虚构工程配套数据，本节内部自洽；列为 LunaLib 约定字段)：

```csv
fieldID,fieldName,fieldType,defaultValue,fieldDescription
GearIn_fullGraphicEffect,Full Graphic Effect,Boolean,true,是否启用生涯全屏转场特效
```

### GearInCompatBridge

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * 软依赖适配器（兼容层）：把对可选依赖 LunaLib 的全部直接引用收敛在本类，
 * 工程里只有这一处出现 lunalib.* 类；其余代码只消费本类的静态方法。
 *
 * 调用链：isModEnabled 判定 MOD 启用状态，Class.forName 判定 API 类是否在
 * 脚本类路径上，两者都通过才进入持有 LunaSettings 直接引用的分支；LunaLib
 * 缺失或设置字段缺失时回落到调用方传入的默认值（降级边界）。
 */
public class GearInCompatBridge {

    /** LunaLib 的 MOD id，与 Starsector\mods\Lunalib\mod_info.json 的 id 字段逐字一致。 */
    public static final String LUNA_MOD_ID = "lunalib";

    /** 可选 API 类全名（类存在性检测用，不直接 import 也能探测）。 */
    public static final String LUNA_SETTINGS_CLASS = "lunalib.lunaSettings.LunaSettings";

    /** 本 MOD 在 data/config/LunaSettings.csv 中登记的设置项 fieldID。 */
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private GearInCompatBridge() {
    }

    /** 判据一：LunaLib 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    /** 判据二：LunaSettings 类是否在脚本类路径上；缺类返回 false，不抛异常。 */
    public static boolean isLunaSettingsClassPresent() {
        try {
            Class.forName(LUNA_SETTINGS_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 组合判据：MOD 已启用且 API 类在类路径上，此时才能安全调用 LunaSettings。 */
    public static boolean isLunaSettingsUsable() {
        return isLunaLibEnabled() && isLunaSettingsClassPresent();
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
- `LUNA_SETTINGS_CLASS` + `Class.forName`：类存在性判据；`catch (ClassNotFoundException)` 缺类回落，不把异常抛出 (`java.lang.Class` 不在安全过滤器拦截范围)。
- `isLunaSettingsUsable()`：组合判据 `启用 && 类在路径上`，适配入口的唯一放行条件。
- `LunaSettings.getBoolean(LUNA_MOD_ID, FULL_GRAPHIC_FIELD)`：唯一持有可选 API 直接引用的语句，位于守卫分支内；依赖缺失时该分支不执行，符号引用永不解析，`NoClassDefFoundError` 不会出现 (懒解析)。
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

`GearInModPlugin` 类归前序单元所有，本节只消费它；fixture 例不含此片段 (fixture 只含本节所有的 `GearInCompatBridge`)。

### 构建

```text
gradle jar
```

编译注意事项：适配器持有 `LunaSettings` 的直接引用，**编译期**类路径上必须有 LunaLib 的 jar(`compileOnly` 依赖或开发机已装 Lunalib)；运行期没有则走降级，不影响启动。

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/modcompat/GearInCompatBridge.class`(可用 `jar tf` 核对)。

### 验证方案 (基于本例)

前置数据：前序累计工程 (C06-S01) 基础上追加本节内容 (`data/config/LunaSettings.csv`、`GearInCompatBridge`、`GearInModPlugin.onGameLoad` 内一行调用)；本机已安装 Lunalib。

- 操作 1(构建核对)：在 MOD 根目录执行 `gradle jar`，确认 `BUILD SUCCESSFUL`；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/modcompat/GearInCompatBridge.class`。
- 操作 2(无 LunaLib 降级路径)：启动器**不**勾选 Lunalib，勾选 "齿轮工业" 启动并进入生涯；`starsector.log` 出现 `GearIn compat: LunaLib unavailable, degrading to fallback=true`，游戏正常无 `gear_in` 引起的 `ERROR`。
- 操作 3(有 LunaLib 完整路径)：勾选 Lunalib 重启进入生涯；`starsector.log` 无降级行；打开 LunaLib 设置界面确认 `GearIn_fullGraphicEffect` 项存在，改值为 `false` 保存后重读存档，确认行为变为 `false`(设置生效)。
- 操作 4(故障注入·字段缺失)：从 `LunaSettings.csv` 删除 `GearIn_fullGraphicEffect` 行重启：确认 LunaLib ERROR 行 `LunaSettings: Value GearIn_fullGraphicEffect of type Boolean not found in JSONObject` 与适配器降级行成对出现，无 NPE；恢复后确认复原。
- 操作 5(故障注入·引用泄漏)：把 `getFullGraphicEffect` 里的 `LunaSettings.getBoolean(...)` 移到 `isLunaSettingsUsable()` 检查之前，不勾选 Lunalib 重启：确认抛 `NoClassDefFoundError`(对应故障分析第 1 条)；改回后确认恢复。
- 通过信号：操作 2 的降级日志、操作 3 的设置生效、操作 4/5 的注入与恢复全部符合。
- 清理：取消勾选测试 MOD；删除测试期间改动 (恢复 `LunaSettings.csv` 与适配器源码)。

## 预期结果

1. **构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/modcompat/GearInCompatBridge.class`。
2. **无 LunaLib(降级路径)**：启动器不勾选 Lunalib，启动游戏进入生涯。
  - `starsector.log` 出现一行 `GearIn compat: LunaLib unavailable, degrading to fallback=true`，`getFullGraphicEffect(true)` 返回 `true`(全特效默认行为)，游戏正常无 `ERROR`。
3. **有 LunaLib(完整路径)**：勾选 Lunalib 重启进入生涯。
  - `starsector.log` 无降级行；LunaLib 设置界面出现 `GearIn_fullGraphicEffect` 项 (默认 `true`)，改值为 `false` 后存档重读，`getFullGraphicEffect(true)` 返回 `false`—— 设置值生效。
4. **字段缺失**：`LunaSettings.csv` 里删掉 `GearIn_fullGraphicEffect` 行后重启：LunaLib 打 `LunaSettings: Value GearIn_fullGraphicEffect of type Boolean not found in JSONObject`(ERROR)，适配器打 `GearIn compat: field [GearIn_fullGraphicEffect] missing in LunaSettings, fallback=true`，返回默认值，无 NPE。
5. **类存在性**：`isLunaSettingsClassPresent()` 在 LunaLib 启用时返回 `true`、未启用时返回 `false`(未启用 MOD 的 jar 不在脚本类路径上，启动器只收集启用 MOD)。

静态自查：`mod_info.json` 不含 `lunalib` 依赖项；`LUNA_MOD_ID` 与 `Starsector\mods\Lunalib\mod_info.json` 的 `id` 逐字一致；Java 源码为 UTF-8 无 BOM、CRLF 换行。

## 实例驱动故障分析

1. **无 LunaLib 时启动正常，但调用 `LunaSettings.getBoolean` 的代码抛 `NoClassDefFoundError`**。
  - 触发：可选类引用泄漏到了守卫分支之外 —— 字段初始化、静态块、构造器，或某个无检测的公开方法体里直接出现 `lunalib.*` 类。
  - 可观察：`NoClassDefFoundError`/`ExceptionInInitializerError` 冒泡 (`Error` 不包装，由脚本类加载器按名实例化时直接冒出)，启动中止或运行中止。
  - 修正：全工程搜索 `lunalib` 引用，除适配器守卫分支外一律移除；把可选引用收敛进 `if (isLunaSettingsUsable())` 的 then 分支。
2. **`isModEnabled("lunalib")` 恒为 false，但启动器里明明勾着 Lunalib**。
  - 触发：把显示名 `LunaLib` 当 id 传。
  - 可观察：功能永久降级，日志只有降级行、无任何报错 (`isModEnabled` 逐字比较，大小写敏感)。
  - 修正：复制对方 `mod_info.json` 的 `id` 字段值。
3. **`isClassPresent` 返回 true 但功能没有接上，或反过来误放行崩溃**。
  - 触发：用类存在性判据代替启用判据。
  - 可观察 (误放行)：可选类被其他 MOD 间接携带、而所属 MOD 未启用时，检测返回 true，守卫放行，调用可选 API 的代码在缺依赖环境下运行并崩溃。
  - 修正：组合判据 `启用 && 类在路径上`(成熟实例陷阱原文)。
4. **运行期 `SecurityException: File access and reflection are not allowed to scripts.`**。
  - 触发：脚本链代码驱动 `java.lang.reflect`(`getMethod`/`getField`/`newProxyInstance` 等) 或 `java.io.File`。
  - 可观察：加载被拦截类型时抛 SecurityException，崩溃 (脚本类加载器的过滤行为)。
  - 修正：改用本节适配器形态 (编译期直接引用 + 守卫)；`Class.forName` 只用于存在性检测，不索取 `Method`/`Field`。
5. **`NullPointerException`，堆栈指向拆箱 `LunaSettings.getBoolean(...)` 返回值**。
  - 触发：字段未定义或 mod 未登记时 `getBoolean` 返回 `null`，代码直接拆箱。
  - 可观察：运行期 NPE(LunaLib 先打一条 ERROR)。
  - 修正：判空后回落默认值 (成熟实例陷阱原文)。
6. **两个 MOD 提供同一个全限定类名，行为时而正确时而错乱**。
  - 触发：共享同一个 `URLClassLoader`，同名类按 URL 顺序先到先得 (脚本类加载器的加载行为)。
  - 可观察：启用顺序/优先级变化后，`Class.forName` 返回的可能是对方实现，功能表现随启用组合漂移。
  - 修正：这是 MOD 之间不隔离的固有现象 —— 本 MOD 的类名保持唯一前缀 (`GearIn*`)，可选 API 一律通过对方的正式类访问。
7. **把软依赖误写进 `dependencies`，用户缺 LunaLib 时 MOD 直接被启动器拦截**。
  - 触发：适配器已经做好降级，却仍把 `lunalib` 声明为硬依赖。
  - 可观察：LunaLib 缺失时 "齿轮工业" 行红色不可勾选/自动禁用 (硬依赖的启动器拦截语义)，用户无法选择降级运行。
  - 修正：代码型软依赖**不**写 `dependencies`；只有真正的强制引用才写 (硬依赖讲解)。
8. **降级日志缺失，功能悄悄变了行为**。
  - 触发：检测失败分支不写日志，或降级值写死在适配器里。
  - 可观察：用户与编者都无法从日志判断 "设置没接上" 还是 "被关闭"。
  - 修正：降级分支写 INFO 日志 (`GearIn compat: ...`)，降级值由调用方传入 (业务默认语义明确)。

## 总结

本节完整走通了软依赖的处理链：

- **检测**：`isModEnabled` 判启用、`Class.forName`/`ModUtils.isClassPresent` 判类路径存在性，组合判据 "启用 && 类在路径上" 才放行。
- **隔离**：可选 API 的直接引用收敛进适配器 `GearInCompatBridge` 的守卫分支，懒解析保证缺依赖时类仍可加载。
- **降级**：缺依赖或字段缺值回落调用方默认值，INFO 日志可观测，判空后再拆箱可空返回值。
- **验证**：无依赖启动走降级、有依赖读设置、故障注入核对日志。

脚本链安全过滤器拦截 `java.lang.reflect` 驱动与 `java.io.File`，决定了 "反射桥接" 不可行，适配器只能收敛编译期直接引用。

逻辑上的后续：这套 "检测——隔离——降级" 骨架与组合判据不绑定 LunaLib，可以原样复用到对其他可选 MOD 的能力接入 —— 适配器按目标 API 换形、降级默认值按业务语义传入；需要按版本分支时，再在组合判据上补 `getVersionInfo()` 一层。

排查既有软依赖问题时也按同一顺序问：判据用对没有、引用是否全部在守卫分支内、降级分支有没有日志、`dependencies` 里有没有误写。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局入口，`getSettings()` 与 `getLogger(Class)` 所在。
- `com.fs.starfarer.api.SettingsAPI`：`getModManager()` 取得 MOD 管理器。
- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled(id)`(启用列表逐字比较、大小写敏感) 等运行时检测方法。
- `com.fs.starfarer.api.ModSpecAPI`：`getVersionInfo()` 读已安装版本 (版本判断用)。
- `lunalib.lunaSettings.LunaSettings`：可选依赖 API，`getBoolean(modId, fieldId)` 返回可空 `Boolean`。
- `data.scripts.gearin.modcompat`：本节示例包，软依赖适配器 `GearInCompatBridge` 所在。
- `data/config/LunaSettings.csv`(MOD 根目录起)：本节设置项 `GearIn_fullGraphicEffect` 的默认值登记处。
- `Starsector/mods/Lunalib/mod_info.json`(游戏安装根起)：`id` 字段逐字比对对象。
- `Starsector/starsector-core/starsector.log`(游戏安装根起)：降级/缺失日志的核对位置。
- `tutorial-fixtures/units/C13-S01/src/main/java/data/scripts/gearin/modcompat`：`GearInCompatBridge` 源码目录 (页面代码块的磁盘对照)。
