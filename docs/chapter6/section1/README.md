# Java 工具链：IDE、Gradle 与依赖

本节把 Java 从 "游戏启动时即时编译源码" 切换到 "先编译成 JAR 再交给游戏加载"：编译期就能暴露错误，产物也能按包路径稳定交付。您将建立 Java 17 工具链，用 Gradle 把游戏侧公开 API(`com.fs.starfarer.api.*`) 作为编译依赖，产出 `jars/` 目录下的运行 JAR，在 `mod_info.json` 的 `jars` 字段声明它，最后从日志与游戏内行为验证整条链路。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 说出当前基线的运行时基线：游戏自带的 Java 17 运行时 (Zulu 17.0.10) 与它要求的字节码版本上限；
- 在 IDE 中建立语言级别 17 的工程，把游戏侧公开 API(`com.fs.starfarer.api.*`) 作为唯一游戏侧编译依赖，并挂上 API 的源码附件；
- 用 Gradle(本教程固定 9.6.0) 以 `toolchain 17` + `--release 17` 编译，把产物输出到 MOD 的 `jars/`；
- 在 `mod_info.json` 写入 `jars` 数组，让游戏把 JAR 挂进脚本类路径；
- 用 `starsector.log` 的三类日志 (jar 加载行、类加载异常、插件运行日志) 判断链路断在哪一段；
- 解释编译 JAR 与即时编译源码的优先顺序，以及 "类在 jar 里但没有注册入口" 为什么不会有任何效果。

## 前置知识

开始本节前，您需要熟悉以下概念：

- MOD 目录结构、`mod_info.json`、启动器与日志；`src/` 不是游戏查找源码的前缀 (C01-S01)。
- 注册、回调与类名一致概念；`settings.json` 的 `plugins` 段、`BaseEveryFrameCombatPlugin`；暂停语义 (C07-S01)。
- 完整类名如何被数据文件引用，`Problem loading class [...]` 异常的含义 (C02-S02)。

## 工具链基线

### Java 17 是运行时上限

游戏进程由自带的 JRE 启动：`Starsector\jre`(`release` 文件写明 `JAVA_VERSION="17.0.10"`，Zulu 17.48+15-CA)，`starsector-core\starsector.bat` 用 `..\jre\bin\java.exe` 运行。因此：

::: caution 必须
编译 MOD 时**必须**把字节码目标定在 Java 17 或更低 (`--release 17`)；

用更高版本 JDK 的默认目标 (如 JDK 21 默认 target 21) 编译的 class，游戏加载时会抛 `UnsupportedClassVersionError`(日志出现 `class file has wrong version`)，表现为启动或战斗开始时报错。
:::

任意已安装的 JDK 17 都可以直接作为编译工具；游戏自带的 `Starsector\jre` 与 JDK 17 是同一发行版。JDK 17 与更高版本 JDK 可以并存，关键是编译目标必须低于等于 17。

### IDE

任选 IDEA、Eclipse 或 VS Code(装 Java 扩展) 均可，三个要点：

1. Project SDK / 语言级别设为 17；
2. 把游戏侧公开 API(`com.fs.starfarer.api.*`) 的编译面作为模块依赖加入工程 (编译期即可，见下)；
3. 把 API 的源码包挂为同一依赖的源码附件，IDE 内跳转才能看到 API 注释 (如 `ModPlugin` 的 javadoc 写明 "应继承 `BaseModPlugin` 而不是直接实现接口")。

### API 依赖：只有游戏侧公开 API

游戏侧公开 API(`com.fs.starfarer.api.*`) 的编译面随游戏发行提供；`starsector.bat` 的 classpath 已包含它，**运行时 API 类由游戏提供**，所以：

- MOD 构建时用 `compileOnly` 引用公开 API(`com.fs.starfarer.api.*`) 的编译面，它**不得**被打包进 MOD 自己的 JAR；
- 教程基线只依赖公开 API 面；个别需要 `com.fs.starfarer.api.impl` 或实现类细节的 MOD 会额外把核心实现 jar 放上编译路径，本教程不采用这种做法。

::: info 背景
为什么是 `compileOnly`？运行时 API 类由游戏提供，MOD 只须在编译期引用它们，产物只包含自己的类。把 API 编译面打包进 JAR 只会让产物膨胀；重复类还可能被游戏侧的父加载器静默遮蔽，造成类身份混淆。
:::

### Gradle

教程验证环境固定 Gradle 9.6.0。本教程的构建命令统一写作 `gradle ...`，要求 PATH 中可用的 `gradle`(教程开发环境为 Gradle 9.6.0)，具体的安装路径以您的环境为准。

两条编译约束缺一不可：`java.toolchain.languageVersion = JavaLanguageVersion.of(17)` 让 Gradle 自动选择本机 JDK 17；`options.release = 17` 强制字节码目标。

::: warning 注意
`options.release` 用于防止 Gradle 自身跑在更高版本 JDK 上时悄悄把 target 抬高 —— 漏掉它时编译仍会成功，字节码目标随 Gradle 运行 JDK 漂移，直到游戏加载类时才以 `UnsupportedClassVersionError` 暴露。
:::

## 文件与 API 链路

工具链基线解决了 "怎么编译"，接下来要回答 "编译出的 jar 怎样进入游戏并生效"。从源码到游戏内生效共七段：

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 源码 | `GearInFrameLogger`(源文件位于 `src\data\scripts\gearin\combat\`) | `javac`(Gradle `compileJava`) |
| 编译依赖 | 游戏侧公开 API(`com.fs.starfarer.api.*`) 编译面 | `compileOnly`；运行时由游戏提供 |
| 产物 | `jars\GearIndustry.jar` | Gradle `jar` 任务输出 |
| 声明 | `mod_info.json` 的 `jars` 数组 | `ModManagerAPI` 解析 (公开只读面为 `ModSpecAPI.getJars()`) |
| 挂载 | 游戏启动期 | 把 `<MOD 根目录>/<jars 声明值>` 逐项加入脚本类加载器的 jar 列表，日志 `Getting ready to load jar file [...]` 与 `Loading jar files` |
| 解析 | 脚本类加载器 (`URLClassLoader`) | 脚本类按完整类名 `Class.forName`；jar 类优先于即时编译源码 (Janino 源码编译器是 jar 类加载器的子加载器，父加载器优先) |
| 注册与实例化 | `data\config\settings.json` 的 `plugins` 段 | CSV spec 加载期把 key→类名注册进脚本类加载器；每场战斗对 `EveryFrameCombatPlugin` 类型创建新实例并附加 |

运行期取得脚本类加载器的公开 API：`SettingsAPI.getScriptClassLoader()`；按完整类名创建实例：`SettingsAPI.getInstanceOfScript(String)`。本节不调用它们，只作阅读链路的对照。

## 最小任务链

七段链路先不要逐段实现。本节的策略是只建立一条最小任务链，先让一个类被注册、加载、运行，再验证修改能生效：
1. 定位使用方：`settings.json` 的 `plugins` 段 (key → 完整类名)→ 注册进脚本类加载器 → 每场战斗创建实例。jar 内类**必须**被某个注册入口按名引用才会被加载 (见故障分析第 5 条)。
2. 建立最小对象：`GearInFrameLogger` 编译进 `jars\GearIndustry.jar`，声明 `jars`，登记 `gearInFrameLogger`。
3. 验证使用方收到对象：进模拟战，日志出现 `GearIn FrameLogger tick` 行。
4. 修改一个参数 (如 `TICK_INTERVAL`) 重新构建，确认输出变化符合预期，从而证明游戏加载的是新构建的 jar 而不是缓存或即时编译源码。

## 完整最小例

任务链的每一环都需要一个具体文件。下面在 `Starsector\mods\GearIndustry\` 中逐个建立 (`data/`、`graphics/`、`sounds/` 沿用 C01-S01 建立的目录)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── config\
│       └── settings.json
├── src\
│   └── data\scripts\gearin\combat\
└── jars\
```

其中 `data\scripts\gearin\combat\` 目录存放本节的 `GearInFrameLogger` 类，完整源码见下文代码块。

### build.gradle

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

def gameRoot = file("Starsector")

sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}

dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}
```

要点：`srcDirs = ["src"]` 让 Gradle 从 `src\data\scripts\...` 编译 (与 C02-S02 既有的源码位置一致)；

`compileOnly` 声明 API 依赖但不打包；`jar` 任务把产物直接放进 `jars\`，文件名与 `mod_info.json` 声明一致。

### settings.gradle

`settings.gradle` 只声明工程名，与 MOD ID 保持一致即可：

```groovy
rootProject.name = "gear_in"
```

### mod_info.json

在 MOD manifest(C01-S01 建立) 上追加 `jars` 字段：

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
  "jars": ["jars/GearIndustry.jar"]
}
```

::: caution 必须
`jars` 数组的每一项必须是相对 MOD 根目录的路径，用 `/` 分隔。游戏启动时按 `<MOD 根目录>/<声明值>` 拼接；写成绝对路径会被拼成 `<MOD 根目录>/<盘符绝对路径>` 之类的无效路径。
:::

### data\config\settings.json

MOD 的 `settings.json` 会与全局设置按对象键合并，只提交自己的键：

```json
{
  "plugins": {
    "gearInFrameLogger": "data.scripts.gearin.combat.GearInFrameLogger"
  }
}
```

`gearInFrameLogger` 是本节登记 key，值必须是 jar 内存在的完整类名。

### GearInFrameLogger

最后一个文件是插件类本身，它在每帧回调中按固定间隔写一行日志：

```java
package data.scripts.gearin.combat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

public class GearInFrameLogger extends BaseEveryFrameCombatPlugin {

    private static final float TICK_INTERVAL = 3f;

    private float elapsed;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }
        elapsed += amount;
        if (elapsed >= TICK_INTERVAL) {
            elapsed = 0f;
            Global.getLogger(GearInFrameLogger.class).info(
                "GearIn FrameLogger tick at battle time " + engine.getTotalElapsedTime(false));
        }
    }
}
```

对应关系：

- `extends BaseEveryFrameCombatPlugin`：提供全部 `EveryFrameCombatPlugin` 方法的空实现，只需覆写 `advance`；直接实现接口则要写满 4 个方法 (C02-S01)。
- `Global.getLogger(GearInFrameLogger.class)`：log4j 记录器，输出到 `starsector-core\starsector.log`；全程不需要在编译期直接 import log4j。
- `engine.isPaused()` 判断：暂停帧不累计 (C02-S01 的暂停语义)。
- `engine.getTotalElapsedTime(false)`：战斗内累计秒数，不含暂停；`amount` 是本帧经过秒数。

构建命令 (在 `Starsector\mods\GearIndustry\` 目录执行)：

```text
gradle jar
```

产物为 `jars\GearIndustry.jar`，其内部条目按包路径组织，包含 `data/scripts/gearin/combat/GearInFrameLogger.class`(可用 `jar tf` 核对)。

::: tip 提示
想确认游戏加载的是新构建的 jar 而不是缓存或旧源码，可先改一个参数 (如把 `TICK_INTERVAL` 从 `3f` 改为 `5f`) 再重新构建 —— 日志中 tick 间隔随之变化即证明产物已更新；用 `jar tf jars\GearIndustry.jar` 可直接核对 jar 内部条目。
:::

## 验证方案

验证入口：启动器 (勾选 MOD) 与模拟战，观察位置为 `Starsector\starsector-core\starsector.log`。前置数据：`mod_info.json`(C01-S01，追加本节 `jars` 字段)、本节 `build.gradle`、`settings.gradle`、`data\config\settings.json` 与 `GearInFrameLogger` 类。

1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jars\GearIndustry.jar` 生成。
2. 启动启动器，勾选 "齿轮工业"，进入主菜单后退出；打开 `Starsector\starsector-core\starsector.log`，确认出现 `Getting ready to load jar file [Starsector\mods\GearIndustry\jars\GearIndustry.jar]` 与 `Loading jar files`，且没有由 `gear_in` 引起的 `ERROR`。
3. 进入游戏 → 模拟战，等待 3 秒以上，确认日志出现 `GearIn FrameLogger tick at battle time ...`。
4. 故障注入：把 `jars` 声明改为 `["jars/NotExist.jar"]`，重启进模拟战，确认报 `Problem loading class [...]`；改回后再确认恢复。

预期结果：

- 步骤 2：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jars\GearIndustry.jar` 存在。
- 步骤 3：`starsector.log` 出现 jar 加载两行 —— `Getting ready to load jar file [Starsector\mods\GearIndustry\jars\GearIndustry.jar]` 与 `Loading jar files`，且没有由 `gear_in` 引起的 `ERROR`。
- 步骤 4：运行 3 秒以上后，日志每隔约 3 秒出现一行 `GearIn FrameLogger tick at battle time ...`。这条日志只可能在类被成功加载并实例化后产生，证明 jar 内容、`jars` 声明、`plugins` 登记三段链路全部打通。
- 步骤 5：注入 `["jars/NotExist.jar"]` 时出现 `Problem loading class [...]`，改回后恢复；把 `TICK_INTERVAL` 改成 `5f` 重新 `gradle jar` 并重启，tick 间隔随之变为 5 秒。若间隔没有变化，说明游戏加载的不是刚构建的 jar (检查缓存目录、是否还有残留源码文件，见故障分析第 6 条)。

清理：取消勾选测试 MOD；删除测试期间写入的源码文件 (若按故障分析第 6 条做过实验)。

## 故障分析

下面每条先给出读者实际看到的症状，再说明触发条件、原因与修正方法。

1. **编译失败 `cannot find symbol`**：`javac` 报 `package com.fs.starfarer.api does not exist`，或找不到 `CombatEngineAPI`。触发：游戏侧公开 API(`com.fs.starfarer.api.*`) 没有进 classpath(漏写 `compileOnly`)，或 IDE 语言级别低于 17。原因：编译期类路径上根本没有 API 类，符号自然解析失败。修正：按本节 `build.gradle` 配置依赖，IDE 内确认模块依赖与 Project SDK 均为 17 后重新构建。
2. **构建成功但日志没有 jar 加载行**：`starsector.log` 里找不到 `Getting ready to load jar file`，被引用的类在战斗开始时抛 `Problem loading class [data.scripts.gearin.combat.GearInFrameLogger]`(由 `ClassNotFoundException` 引起)。触发：`mod_info.json` 缺失 `jars` 字段、MOD 未勾选、或 jar 声明在未启用的 MOD 上。原因：jar 从未被加入脚本类加载器，缺失要等类被按名引用时才暴露。修正：确认 manifest 含 `"jars": ["jars/GearIndustry.jar"]` 且 MOD 已勾选，重启游戏复验。
3. **jar 路径拼错**：日志**仍会**打印 `Getting ready to load jar file [<MOD 根>/<声明值>]`，错误要等类被引用才暴露，表现为第 2 条的 `Problem loading class`。触发：`jars` 声明成绝对路径，或相对路径与实际布局不符。原因：`URLClassLoader` 构造时不校验文件存在性 —— 它对您写的路径抱有无条件的信任；缺文件的事实要等首次按名加载该类时才发作。修正：把声明值改成相对路径并核对文件名，重启复验。
4. **字节码版本过高**：加载该类时日志出现 `UnsupportedClassVersionError`(含 `class file has wrong version`)，启动或战斗阶段弹 Fatal 提示。触发：用 JDK 21+ 的默认 target 编译 (漏掉 `options.release`)，或 IDE 输出语言级别高于 17。原因：class 文件版本号超过游戏自带 JRE (Java 17) 能接受的上限。修正：`toolchain 17` 与 `options.release = 17` 同时保留，重编译后复验。
5. **类在 jar 里但游戏没有任何行为**：无报错、无日志、`data/scripts/plugins` 目录扫描**不**会扫 jar—— 它只按源码扩展名注册脚本类。触发：类没有被任何注册入口按名引用。原因：类只有被用到时才加载，没人按名找它就永远不会被加载。修正：沿 `settings.json` 的 `plugins`、`mod_info.json` 的 `modPlugin`(C06-S02) 或数据文件的 `script` 列 (C02-S02) 引用它。
6. **改了源码重启后行为没变**：即使修改源码里的日志文本，输出仍是 jar 里的旧文本。触发：MOD 根下残留同名源码文件 (旧工作流在 `data\scripts\gearin\combat\` 留下的同名类)。原因：jar 类加载器是 Janino 源码编译器的父加载器，父加载器优先，jar 类永远盖过即时编译的源码。修正：删除 MOD 根下残留源码文件，以 jar 为唯一事实来源；构建前确认 `gradle jar` 已重新执行。
7. **`Problem loading class` 且日志确认 jar 已加载**：`Getting ready to load jar file` 存在，但类加载抛 `Problem loading class [...]`。触发：jar 内容与声明类名不匹配 (包名或类名拼写不一致、类打进了别的 jar)。原因：注册入口按名引用类时，jar 里找不到对应的完整类名。修正：用 `jar tf` 核对类路径，与 settings 里的完整类名逐段对照。

## 总结

本节把 "源码即时编译" 换成 "先编译成 jar 再按名加载"，并让这条链的每一段都有了可观察信号：`gradle jar` 的构建结果、日志里的 jar 加载行、模拟战中的 tick 行，以及改动参数后的行为变化。链在哪一段断，日志就会在哪一段缺信号，因此验证顺序就是排查顺序。

排查时依次问：jar 有没有被声明 (看日志加载行)、类名有没有被注册入口引用、字节码版本是否低于等于 17、jar 里是否真的有这个类。任何一环缺失，最终都表现为 `Problem loading class` 或 "没有任何行为"，区别只在日志哪一段先断。

"编译为类、按名引用" 的机制同样适用于 MOD 入口类：从 jar 实例化入口类、在游戏应用生命周期回调 (如 `onApplicationLoad`) 中做初始化，是本节链路的直接延续 (C06-S02)。

## 建议参考项

- `com.fs.starfarer.api.Global`：`getCombatEngine()`、`getLogger(Class)`(log4j 记录器入口)。
- `com.fs.starfarer.api.ModSpecAPI`：`getJars()`(MOD 声明的 jar 列表)。
- `com.fs.starfarer.api.SettingsAPI`：`getScriptClassLoader()`、`getInstanceOfScript(String)`(脚本类加载与实例化入口)。
- `com.fs.starfarer.api.ModPlugin`：javadoc 注明应继承 `BaseModPlugin` 而非直接实现接口。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：`EveryFrameCombatPlugin` 的空实现基类。
- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：战斗每帧回调接口。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`、`getTotalElapsedTime(boolean)`。
- `data/config/settings.json`：`plugins` 段 (key → 完整类名)。
- `mod_info.json`：`jars` 数组 (相对 MOD 根目录的 jar 路径)。
- `build.gradle` / `settings.gradle`：Gradle 构建配置 (toolchain 17、`--release 17`、jar 输出到 `jars/`)。
- `jars/GearIndustry.jar`：构建产物 (可用 `jar tf` 核对条目)。
- `Starsector/starsector-core/starsector.log`：运行日志 (jar 加载行、类加载异常、插件日志)。
- `Starsector/starsector-core/starsector.bat`：游戏启动命令 (`..\jre\bin\java.exe`，classpath 含游戏侧公开 API 编译面)。
- `Starsector/jre/release`：自带 JRE 版本信息 (Zulu 17.0.10)。
