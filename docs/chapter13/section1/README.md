# 声明并检测 MOD 依赖

本节在 `mod_info.json` 中声明 MOD 的强制依赖，并用 `ModManagerAPI` 检查运行时状态。依赖缺失或未启用时，启动器会先给出提示并阻止错误组合进入游戏。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 写出 `mod_info.json` 的 `dependencies` 数组，说出 `id`、`name`、`version` 三个字段的必需性与缺失后果；
- 解释硬依赖 (hard dependency) 的完整语义：谁检查、检查什么、失败时启动器做什么；
- 说出 `getDependencies()` 与 `getAllDependencies()` 的差别 (直接依赖 vs 递归展开的全部依赖)；
- 用 `Global.getSettings().getModManager()` 在运行时读取依赖与启用状态，区分 `isModEnabled`、`getModSpec`、`getAvailableModsCopy` 的语义；
- 从启动器行的颜色与文案、自动禁用弹窗、`starsector.log` 三个位置判断依赖问题出在哪一环。

## 前置知识

本节建立在既有教程工程与前面各节的机制之上，主要涉及三块基础：

- MOD 目录与 `mod_info.json` 必填字段、`enabled_mods.json` 启用列表、启动器与日志 [C1-S1]。
- 编译 JAR 链路与 `jars` 声明；依赖 MOD 的类同样只可能来自它自己的 JAR 或 `.java` 脚本 [C6-S1]。
- `modPlugin` 入口与 `onApplicationLoad()` 时机 (资源加载完成后、主菜单前)；本节的运行时检测示例从这个回调接入 [C6-S2]。

`dependencies` 声明是在既有 manifest 上的累积追加。软依赖 (optional dependency) 的运行时分支检测、调用隔离与降级适配属于软依赖专题 [C14-S1]，本节只覆盖声明式硬依赖与基础运行时检测。

## 硬依赖语义

硬依赖 = 在 `mod_info.json` 的 `dependencies` 数组里声明的、**必须已安装**的 MOD。

它的强制力来自启动器：依赖缺失或版本不符时，启动器不允许勾选本 MOD；此前已勾选的会在启动器主界面初始化时被自动禁用并弹窗。

这是声明式的检查：启动器只核对声明，不检查代码里是否真的引用 —— 引用与声明的一致性由 MOD 作者负责 (见故障分析第 6 条)。

### 声明：mod_info.json 的 dependencies 数组

```json
"dependencies": [
  {
    "id": "lw_lazylib",
    "name": "LazyLib"
  },
  {
    "id": "MagicLib",
    "name": "MagicLib",
    "version": "1.5.1"
  }
]
```

字段作用与缺失后果：

| 字段 | 必需性 | 作用与缺失后果 |
|---|---:|---|
| `id` | 必须 | 依赖 MOD 的 `mod_info.json` 的 `id` 字段值，大小写敏感、逐字匹配。缺失或不是字符串 → manifest 解析失败，整个 MOD 目录被跳过 (日志 `Error loading mod from [...]`)|
| `name` | 可选 | 启动器 "Required mods:" 列表的显示名；缺失时回退为 `id` 本身 |
| `version` | 可选 | 声明的最低要求版本，字符串 (如 `"3.0.0"`) 或对象 (`{"major":3,"minor":0,"patch":0}`) 均可；缺失 → 只检查依赖 MOD 是否安装，不比较版本 |

解析细节 (启动器解析 `mod_info.json` 时)：`id` 用 `getString`，缺字段抛 `JSONException` 并上抛为解析失败；

`name` 用 `optString("name", id)`；`version` 与顶层 `version` 字段共用同一套版本解析，写成对象时 `major` 必填，缺失同样导致解析失败。

::: caution 依赖项 `id` 必须逐字一致
`id` 必须与依赖 MOD 的 `mod_info.json` 的 `id` 字段逐字匹配、大小写敏感；启动器按 `equals` 逐字比对，拼错或大小写不符一律按未安装处理 (`MODULE_MISSING`)，错误文案只会提示缺少该 MOD，不会提示 ID 拼写有误。
:::

::: warning 声明与引用要一致
`dependencies` 数组只应列出本 MOD 代码**真实引用**的 MOD。两条错误方向：声明了但代码不用，只是给用户增加启动器强制安装的摩擦；代码用了但没声明，启动器无法自动发现，用户缺依赖时在运行期才炸 (见故障分析第 6 条)。
:::

### 加载器检查：谁在什么时候检查

声明只是起点，真正的强制力来自启动器随后执行的检查：

- 检查入口：启动器的兼容性检查。启动器每次构建 MOD 行、刷新信息面板、初始化主界面时都会执行。
- 检查对象：`getAllDependencies()` —— 按已安装 MOD 的 manifest 递归展开全部依赖，包括依赖项的依赖 (`LinkedHashSet` 防环)。因此间接依赖 (依赖项的依赖) 失效同样产生硬错误。
- 判定级别 (从轻到重)：

| 级别 | 触发条件 | 启动器处理 |
|---|---|---|
| `COMPATIBLE` | 依赖已安装且版本相符 (或未声明版本)| 正常 |
| `PATCH_MISMATCH` | 依赖已安装，patch 不同 | 黄色警告，可勾选 |
| `MINOR_MISMATCH` | 依赖已安装，minor 不同 | 黄色警告，可勾选 |
| `MAJOR_MISMATCH` | 依赖已安装，major 不同；或依赖项声明了版本、而已安装依赖 MOD 自身 `gameVersion` 的 major 不符当前游戏 | 红色，不可勾选 |
| `VERSION_UNSPECIFIED` | 依赖项声明了版本但已安装 MOD 无版本信息；或已安装依赖 MOD 未声明 `gameVersion` | 红色，不可勾选 |
| `MODULE_MISSING` | 依赖 MOD 未安装 (不在 `Starsector\mods\` 下)| 红色，不可勾选 |

版本比较规则：major 不同 → `MAJOR_MISMATCH`；minor 不同 → `MINOR_MISMATCH`(依赖项与已安装 MOD 的版本都是 RC 编号时跳过 minor 比较)；

patch 不同 → `PATCH_MISMATCH`；全部相同 → `COMPATIBLE`。

整体级别取自身 `gameVersion` 级别与全部依赖级别中最重的一档。

### 启动器侧的可观察结果

检查结果最终呈现在启动器界面上，按严重程度表现为不同行为：

1. **红色 + 不可勾选**：整体级别为 `MODULE_MISSING` / `MAJOR_MISMATCH` / `VERSION_UNSPECIFIED` 的 MOD 行以红色绘制，点击勾选无反应 (启动器 MOD 列表的行面板)。
2. **描述面板**：选中 MOD 时列出 "Required mods:" 段，逐项显示 `<name> <version>`(红或黄着色)，并追加错误行：
  - 依赖缺失显示 `Error: this mod requires another mod that you don't have installed.`；
  - 依赖版本 major 不符显示 `Error: this mod requires a different version of a mod that you have installed.`；
  - 依赖版本 minor/patch 不符显示黄色 `Warning: this mod requires a different version of a mod that you have installed, but might run with this one.`。
3. **勾选联动**：勾选一个 MOD 会**自动勾选**它的全部依赖行 (含间接依赖)；取消一个 MOD 的勾选会**级联取消**声明它为依赖的 MOD。
4. **自动禁用 + 弹窗**：启动器主界面初始化时，已启用 MOD 中整体级别为硬错误 (`MODULE_MISSING` / `MAJOR_MISMATCH` / `VERSION_UNSPECIFIED`) 的会被移出启用列表，并立即保存 `Starsector\mods\enabled_mods.json`。
  - 弹窗：随后弹出 `Some of the enabled mods have unmet requirements or are not compatible with this version of the game and have been disabled.`(启动器)。
  - 不可绕过：手动改写 `enabled_mods.json` 无法绕过这一道检查。
  - 注：版本比较为代码逻辑 (major/minor/patch 差异规则) 而非硬编码表。

### 代码侧：运行时检测 API

启动器负责启动前的把关；代码侧还需要在运行时读取同样的事实，用于日志与分支判断。

运行时读取依赖状态的标准入口：`Global.getSettings().getModManager()`(`SettingsAPI.getModManager()`)→ `ModManagerAPI`：

| 方法 | 语义 |
|---|---|
| `isModEnabled(String id)` | id 是否在启用列表 (大小写敏感、逐字比较)|
| `getModSpec(String id)` | 只查**已启用** MOD；未启用或未安装 → `null` |
| `getAvailableModsCopy()` | 全部已安装 MOD 的副本，不区分启用状态 |
| `getEnabledModsCopy()` | 当前启用列表的快照 |
| `ModSpecAPI.getDependencies()` / `getAllDependencies()` | 直接依赖 / 递归展开 (含依赖的依赖)|

`ModSpecAPI.getVersionInfo()` 返回已安装 MOD **自己**的版本对象 (`VersionInfoAPI.getString()` 输出 `3.0.0` 形式)；

`ModDependencyAPI` 只有 `getId()` / `getName()` / `getVersionInfo()`。

运行期读到的启用列表与启动器勾选一致 —— 两者共用同一个 `ModManagerAPI` 与 `Starsector\mods\enabled_mods.json`。

硬依赖的代码侧含义：本 MOD 直接引用依赖 MOD 的类或资源。

依赖缺失时这些类不在脚本类路径上，JVM 在首次解析或初始化相关类时抛 `NoClassDefFoundError`；

若入口类 (`modPlugin`) 的初始化链引用缺失类，启动阶段即失败 (`Error` 不被包装，直接冒泡)。

这正是声明式硬依赖要由启动器把关的原因。

::: info 为什么由启动器把关
等到代码运行期再失败已经太晚 —— 用户连主菜单都进不去才知道缺了依赖；启动器在启动前拦截，才能给出缺什么、装什么的明确提示。
:::

::: caution 引用了就必须声明
代码里引用了依赖 MOD 的类或资源，`dependencies` 就必须声明该 MOD —— 这是玩家侧把关的唯一依据；启动器只核对声明，无法替代码检查引用，漏声明时缺依赖的用户只能在运行期以 `NoClassDefFoundError` 的形式发现问题。
:::

## 文件与 API 链路

上面分头出现的文件与 API，按数据流串起来就是同一条链：

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 声明 | `mod_info.json` 的 `dependencies` 数组 | 启动器解析：`id` 用 `getString`，`name`/`version` 可选 |
| 检查 | 启动器的兼容性检查 | 递归 `getAllDependencies` 逐项对照已安装 MOD；版本按 major/minor/patch 逐档比较 |
| 展示 | 启动器 Mods 列表 | 红/黄着色、不可勾选、"Required mods:" 列表与错误文案 |
| 联动 | 启动器勾选事件 | 勾选自动带出依赖、取消级联取消依赖者 |
| 强制 | 启动器主界面初始化 | 移除硬错误级别 MOD、保存 `enabled_mods.json`、弹窗 |
| 运行时读取 | `Global.getSettings().getModManager()` → `ModManagerAPI` | `isModEnabled` / `getModSpec` / `getAvailableModsCopy` / `getEnabledModsCopy`(API `com.fs.starfarer.api.SettingsAPI`、`com.fs.starfarer.api.ModManagerAPI`)|
| 代码引用 | 依赖 MOD 的 JAR / 脚本类 | 脚本类加载器：类缺失 → `NoClassDefFoundError` / `Problem loading class` |
| 日志 | `starsector-core\starsector.log` | `Found mod:` 扫描行、`Running with the following mods` 启动行、本节示例的 `GearIn dependency check:` 行 |

链路的每一环都要在工程里有个落点，否则只停留在纸面。接下来在既有工程上追加一个最小例，让声明、检查、强制与运行时读取全部可以观察。

## 完整最小例

在既有累计工程的布局上追加 (MOD 根目录 `Starsector\mods\GearIndustry`)；本节不新增任何数据 ID 或资源，只新增一个 Java 类：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── config\
│       └── settings.json
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # 既有插件类，本节追加一行调用
│       └── modcompat\
│           └── GearInDependencyCheck  # 本节新增
└── jars\
```

### mod_info.json(在既有 manifest 上追加 dependencies)

```json
{
  "id": "gear_in",
  "name": "齿轮工业",
  "author": "YourName",
  "version": {
    "major": 0,
    "minor": 1,
    "patch": 0
  },
  "description": "Starsector MOD 教程的练习项目。",
  "gameVersion": "0.98a-RC8",
  "jars": ["jars/GearIndustry.jar"],
  "modPlugin": "data.scripts.gearin.GearInModPlugin",
  "dependencies": [
    {
      "id": "lw_lazylib",
      "name": "LazyLib"
    },
    {
      "id": "MagicLib",
      "name": "MagicLib",
      "version": "1.5.1"
    }
  ]
}
```

要点：

- `id` 必须与依赖 MOD 自己的 `id` 逐字一致：LazyLib 的 id 是 `lw_lazylib`，MagicLib 的 id 是 `MagicLib` (打开对方的 `mod_info.json` 复制，不要凭印象写)。
- `name` 只影响启动器显示；LazyLib 一项省略 `version`(只检查存在性 —— `MagicLib` 官方注释 "Remove the specific version for future-proofing." 推荐的做法)，MagicLib 一项带 `version` 演示版本比较。
- 示例声明这两个依赖仅用于演示启动器把关与运行时检测，本教程代码没有调用它们的 API；您自己的 MOD 只声明真实引用的依赖。

### GearInDependencyCheck

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModDependencyAPI;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.VersionInfoAPI;

/**
 * 依赖的运行时检测：把 mod_info.json 声明的 dependencies 与启动器 MOD 管理器
 * 的运行时状态对齐，逐项输出 id、名称、是否启用与安装版本。
 *
 * 声明式硬依赖由启动器把关（缺失或版本不符时无法勾选）；本类只负责
 * 在代码里复核声明与事实，并把不一致暴露成明确的日志行。
 */
public class GearInDependencyCheck {

    public static final String SELF_MOD_ID = "gear_in";

    private GearInDependencyCheck() {
    }

    /**
     * 在 MOD 插件的 onApplicationLoad() 中调用一次（接入方式见页面正文）。
     */
    public static void reportDeclaredDependencies() {
        ModManagerAPI modManager = Global.getSettings().getModManager();
        ModSpecAPI self = modManager.getModSpec(SELF_MOD_ID);
        if (self == null) {
            Global.getLogger(GearInDependencyCheck.class).warn(
                "GearIn dependency check: mod [" + SELF_MOD_ID + "] is not enabled; cannot read declared dependencies");
            return;
        }
        List<ModDependencyAPI> declared = self.getDependencies();
        if (declared.isEmpty()) {
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: no dependencies declared in mod_info.json");
            return;
        }
        for (ModDependencyAPI dep : declared) {
            boolean enabled = modManager.isModEnabled(dep.getId());
            String installedVersion = "not installed";
            if (enabled) {
                ModSpecAPI installed = modManager.getModSpec(dep.getId());
                VersionInfoAPI versionInfo = installed.getVersionInfo();
                if (versionInfo != null) {
                    installedVersion = versionInfo.getString();
                }
            }
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: " + dep.getName() + " (" + dep.getId()
                    + ") enabled=" + enabled
                    + " installedVersion=" + installedVersion);
        }
    }
}
```

对应关系：

- `modManager.getModSpec(SELF_MOD_ID)`：返回本 MOD 自己的 `ModSpecAPI`，只在**本 MOD 已启用**时非 null —— `getModSpec` 只查启用列表。null 分支写 warn 日志而不是直接遍历，避免对 "未启用" 场景误报。
- `self.getDependencies()`：直接依赖列表，与 manifest 的 `dependencies` 数组一一对应。
- `modManager.isModEnabled(dep.getId())`：依赖是否在启用列表；依赖已安装但未勾选时返回 false。
- `modManager.getModSpec(dep.getId())`：只在 `enabled` 为 true 时调用 —— `getModSpec` 未启用即 null，null 上调用方法会 NPE。
- `installed.getVersionInfo().getString()`：读的是**已安装** MOD 自己的版本 (如 LazyLib 的 `3.0.0`)，与声明项里有没有写 `version` 无关。
- `Global.getLogger(GearInDependencyCheck.class)`：log4j 记录器，输出到 `starsector-core\starsector.log`。

### 接入片段 (在既有 `GearInModPlugin` 中追加一行)

```java
@Override
public void onApplicationLoad() {
    // 依赖检查接入：把 mod_info.json 声明的依赖与运行时启用状态对齐并写日志
    GearInDependencyCheck.reportDeclaredDependencies();
    // ... 既有回调内容 ...
}
```

`GearInModPlugin` 类定义于既有教程工程 (不归本节所有)，本节只调用它；示例工程不含此片段 (示例工程只含本节所有的 `GearInDependencyCheck`)。

### 构建

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/modcompat/GearInDependencyCheck.class`(可用 `jar tf` 核对)。

## 验证方案

前置数据：既有累计工程 ([C6-S2] 的累计成果) —— `mod_info.json` 已追加本节 `dependencies`、已新增 `GearInDependencyCheck` 类、`GearInModPlugin` 内已追加一行调用；已安装 LazyLib 与 MagicLib。

1. 构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`。
   - 核对产物：`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/modcompat/GearInDependencyCheck.class`。
2. 启动器声明检查：打开启动器 Mods 列表，选中 "齿轮工业"，确认描述面板出现 "Required mods:" 并列出 `LazyLib` 与 `MagicLib 1.5.1`；勾选 "齿轮工业"，确认 LazyLib、MagicLib 被自动勾选。
3. 运行时检测：启动游戏进入主菜单后退出，在 `starsector.log` 中观察依赖检查记录。
4. 把 MagicLib 依赖项的 `version` 改为 `"2.0.0"`，重启启动器，观察 "齿轮工业" 行与描述面板。

预期结果：

- **构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束。
- **声明生效 (启动器)**：Mods 列表中 "齿轮工业" 行正常、可勾选；选中后描述面板出现 "Required mods:"，列出 `LazyLib` 与 `MagicLib 1.5.1`。
- **勾选联动**：勾选 "齿轮工业" 时，LazyLib 与 MagicLib 被自动勾选；取消 LazyLib 的勾选，"齿轮工业" 的勾选被级联取消 (依赖仍安装时它仍可重新勾选 —— 启动器要求的是 "已安装"，不是 "必须同时启用")。
- **运行时检测**：启动游戏进入主菜单后退出，`starsector.log` 出现：

  ```text
     GearIn dependency check: LazyLib (lw_lazylib) enabled=true installedVersion=3.0.0
  GearIn dependency check: MagicLib (MagicLib) enabled=true installedVersion=1.5.1
  ```

  `installedVersion` 是已安装 MOD 的真实版本 (`getVersionInfo`)，LazyLib 声明项里没写 `version` 也能查出 `3.0.0`。
- 启动期间没有由 `gear_in` 引起的 `ERROR`。
- **变更验证 (版本)**：把 MagicLib 项的 `version` 改为 `"2.0.0"`，重启启动器。
  - "齿轮工业" 行变红且不可勾选，描述面板出现 `Error: this mod requires a different version of a mod that you have installed.`。

清理：把 MagicLib 依赖项的 `version` 恢复为 `1.5.1`。

## 故障分析

1. **"Error: this mod requires another mod that you don't have installed." 但依赖明明装了**。
  - 触发：依赖项 `id` 拼错或大小写不符 (如把 `lw_lazylib` 写成 `LazyLib`)。
  - 可观察：MOD 行红色、不可勾选，错误文案指向缺少该 MOD。
  - 原因：兼容性检查按 `equals` 逐字比对 id，大小写敏感 —— 它只认逐字相等，不认差不多。
  - 修正：打开依赖 MOD 自己的 `mod_info.json`，复制 `id` 字段值。
2. **上次还正常的 MOD 这次被自动禁用并弹窗**。
  - 触发：用户卸载/移走了依赖 MOD，或依赖项写了当前环境不存在的版本。
  - 可观察：启动器弹出 "Some of the enabled mods have unmet requirements or are not compatible with this version of the game and have been disabled."。
  - 可观察：`Starsector\mods\enabled_mods.json` 的 `enabledMods` 里本 MOD 已被移除。
  - 原因：启动器主界面初始化时对硬错误级别 MOD 执行移除并保存。
  - 修正：恢复依赖或修正声明。这是设计行为，不是 bug；也是已勾选 MOD 悄悄消失时首选排查的方向。
3. **勾选一个 MOD 却带出一串从没勾过的 MOD**。
  - 触发：该 MOD 声明了间接依赖 (依赖的依赖)。
  - 可观察：勾选后多行自动变为勾选态。
  - 原因：启动器 MOD 列表按 `getAllDependencies()` 递归自动勾选。
  - 修正：无，属预期行为；若某个依赖不应被连带启用，应把它降级为软依赖 —— 软依赖的声明与检测在软依赖专题中展开。
4. **`isModEnabled(...)` 返回 false 但启动器里明明勾着**。
  - 触发：把 `name` 当 `id` 传 (如 `isModEnabled("LazyLib")`)。
  - 可观察：运行时分支错误、日志显示依赖未启用。
  - 原因：`isModEnabled` 只认 `id` 且逐字比较。
  - 修正：代码里永远使用对方 manifest 的 `id`(如 `lw_lazylib`)，不用显示名。
5. **`getModSpec(id)` 返回 null 导致 NPE**。
  - 触发：目标 MOD 已安装但未启用，或未安装，代码未判空直接调用返回对象的方法。
  - 可观察：NPE 堆栈，日志 `ERROR`。
  - 原因：`getModSpec` 只查启用列表。
  - 修正：先 `isModEnabled` 判断，或改用 `getAvailableModsCopy()` 枚举安装态 (不区分启用)。
6. **代码引用了依赖 MOD 的类，用户没装那个依赖，启动或运行报错**。
  - 触发：用了 `org.lazywizard.lazylib` 之类的类，但 `dependencies` 没声明 (或声明了但依赖未被启用且类确实缺失)。
  - 可观察：入口类初始化链引用缺失类 → 启动阶段日志出现 `NoClassDefFoundError` 或 `Problem loading class [...]`(脚本类加载器不包装 `Error`，直接冒泡)。
  - 可观察：战斗或回调期首次调用 → 该处抛 `NoClassDefFoundError`，流程中止。
  - 原因：依赖 MOD 的类不在脚本类路径上；启动器只核对声明，无法替代码检查引用。
  - 修正：把用到的 MOD 加入 `dependencies` —— 这是玩家侧把关的唯一依据。
7. **manifest 解析失败、MOD 整个消失**。
  - 触发：`dependencies` 数组里某项缺 `id`、`id` 不是字符串，或 `version` 写成对象但缺 `major`。
  - 可观察：MOD 不出现在列表，日志 `Error loading mod from [<MOD 目录>]`。
  - 原因：`id` 用 `getString`，`version` 对象缺 `major` 同样抛异常，`JSONException` 上抛为解析失败。
  - 修正：恢复合法 JSON；`id` 必填，`version` 尽量用字符串或干脆省略。
8. **运行时检测日志没有出现**。
  - 触发：`GearInDependencyCheck.reportDeclaredDependencies()` 没有接到任何回调，或 MOD 未勾选。
  - 可观察：`starsector.log` 无 `GearIn dependency check:` 行；若本 MOD 未启用，只会有 warn 行 "mod [gear_in] is not enabled"。
  - 原因：类不会被自动加载 —— 没人按名调用它就什么都不做，运行时检测类必须由回调显式调用。
  - 修正：按接入片段一节把调用加进 `GearInModPlugin.onApplicationLoad()`，并确认 MOD 已勾选。

## 总结

本节把依赖从口头约定变成一条完整的强制链：`mod_info.json` 里声明，启动器在启动前按声明检查并强制，运行时再借 `ModManagerAPI` 把声明与事实对齐成日志行。三段共用同一份 `id` 与启用列表，任何一环的偏差都能沿同一条线对照。

排查依赖问题时先看启动器行的颜色与文案判断是哪一档级别，再看 `starsector.log` 的 `GearIn dependency check:` 行，最后核对 `id` 的大小写与 `enabled_mods.json`。

代码里引用了什么 MOD 的类，`dependencies` 就必须声明什么 —— 启动器拦得住没装和版本不符，拦不住漏声明，这是声明式依赖的固有边界，只能由代码侧补齐。

依赖话题的下一步是软依赖 (可选依赖，[C14-S1])：运行时分支检测、调用隔离与降级适配，把缺了依赖也能降级运行的策略留给代码侧，与声明式硬依赖形成互补。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`getModManager()`，运行时读取 MOD 管理器与启用列表的入口。
- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled` / `getModSpec` / `getAvailableModsCopy` / `getEnabledModsCopy`，运行时依赖与启用状态读取。
- `com.fs.starfarer.api.ModSpecAPI`：`getDependencies` / `getAllDependencies` / `getVersionInfo`，MOD 元数据与依赖列表。
- `com.fs.starfarer.api.ModDependencyAPI`：`getId` / `getName` / `getVersionInfo`，单个依赖项。
- `com.fs.starfarer.api.VersionInfoAPI`：`getString`，版本号字符串化输出。
- `com.fs.starfarer.api.Global`：`getSettings` / `getLogger`，全局入口与日志记录器。
- `data/scripts/gearin/modcompat`：本节示例类 `GearInDependencyCheck` 所在包。
- `mod_info.json`：MOD 根目录下的清单文件，`dependencies` 数组在此声明。
- `Starsector\mods\enabled_mods.json`：启动器启用列表，自动禁用时被立即改写。
- `starsector-core\starsector.log`：启动扫描与运行时日志输出位置。
