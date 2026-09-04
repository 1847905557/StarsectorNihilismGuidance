# 搭建 Java 工具链

本节搭建 Java 17 与 Gradle 工具链，把 MOD 源码预先编译成游戏能够加载的 JAR。编译会提前暴露代码错误，产物则通过 `mod_info.json` 进入游戏。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 列出当前基线的运行时组成：游戏自带的 Java 17 运行时 (Zulu 17.0.10) 与它要求的字节码版本上限；
- 在 IDE 中建立语言级别 17 的工程，把游戏侧公开 API(`com.fs.starfarer.api.*`) 作为唯一游戏侧编译依赖，并挂上 API 的源码附件；
- 用 Gradle(本教程固定 9.6.0) 以 `toolchain 17` + `--release 17` 编译，把产物输出到 MOD 的 `jars/`；
- 在 `mod_info.json` 写入 `jars` 数组，让游戏把 JAR 挂进脚本类路径；
- 用 `starsector.log` 的三类日志 (jar 加载行、类加载异常、插件运行日志) 判断链路断在哪一段；
- 解释编译 JAR 与即时编译源码的优先顺序，以及 "类在 jar 里但没有注册入口" 为什么不会有任何效果。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json`、启动器与日志；
- [C2-S2] 的完整类名引用、注册入口和 `Problem loading class [...]` 异常含义；
- [C7-S1] 的每帧回调、`BaseEveryFrameCombatPlugin` 和暂停语义；
- 理解 Java 17 字节码上限、Gradle toolchain 与 `--release` 的关系；
- 理解编译期依赖、运行时由游戏提供的公开 API 与 JAR 输出边界。

## 工具链基线

### 确认 Java 17 运行时上限

游戏进程由自带的 JRE 启动：`jre`(`release` 文件写明 `JAVA_VERSION="17.0.10"`，Zulu 17.48+15-CA)，`starsector-core/starsector.bat` 用 `../jre/bin/java.exe` 运行。因此：

编译 MOD 时**必须**把字节码目标定在 Java 17 或更低 (`--release 17`)；

用更高版本 JDK 的默认目标 (如 JDK 21 默认 target 21) 编译的 class，游戏加载时会抛 `UnsupportedClassVersionError`(日志出现 "Compiled for the wrong version of Java, change the compile target to Java 7")，表现为启动或战斗开始时报错。

任意已安装的 JDK 17 都可以直接作为编译工具；游戏自带的 `jre` 与 JDK 17 是同一发行版。JDK 17 与更高版本 JDK 可以并存，关键是编译目标必须低于等于 17。

### 配置 IDE 依赖

任选 IDEA、Eclipse 或 VS Code(装 Java 扩展) 均可，三个要点：

1. Project SDK / 语言级别设为 17；
2. 把游戏侧公开 API(`com.fs.starfarer.api.*`) 的编译面作为模块依赖加入工程 (编译期即可)；
3. 把 API 的源码包挂为同一依赖的源码附件，IDE 内跳转才能看到 API 注释 (如 `ModPlugin` 的 javadoc 写明 "应继承 `BaseModPlugin` 而不是直接实现接口")。

### 限定游戏侧编译依赖

游戏侧公开 API(`com.fs.starfarer.api.*`) 的编译面随游戏发行提供；`starsector.bat` 的 classpath 已包含它，**运行时 API 类由游戏提供**，所以：

- MOD 构建时用 `compileOnly` 引用公开 API(`com.fs.starfarer.api.*`) 的编译面，它**不得**被打包进 MOD 自己的 JAR；
- 教程基线只依赖公开 API 面；个别需要 `com.fs.starfarer.api.impl` 或实现类细节的 MOD 会额外把核心实现 jar 放上编译路径，本教程不采用这种做法。

为什么是 `compileOnly`？运行时 API 类由游戏提供，MOD 只须在编译期引用它们，产物只包含自己的类。把 API 编译面打包进 JAR 只会让产物膨胀；重复类还可能被游戏侧的父加载器静默遮蔽，造成类身份混淆。

### 配置 Gradle 编译目标

教程验证环境固定 Gradle 9.6.0。本教程的构建命令统一写作 `gradle ...`，要求 PATH 中可用的 `gradle`(教程开发环境为 Gradle 9.6.0)，具体的安装路径以您的环境为准。

`java.toolchain.languageVersion = JavaLanguageVersion.of(17)` 让 Gradle 自动选择本机 JDK 17，`options.release = 17` 强制字节码目标，两条编译约束缺一不可。

`options.release` 用于防止 Gradle 自身跑在更高版本 JDK 上时悄悄把 target 抬高 —— 漏掉它时编译仍会成功，字节码目标随 Gradle 运行 JDK 漂移，直到游戏加载类时才以 `UnsupportedClassVersionError` 暴露。

## 文件与 API 链路

工具链基线解决了 "怎么编译"，接下来要回答 "编译出的 jar 怎样进入游戏并生效"。从源码到游戏内生效要经过以下环节：

### JAR 加载链路速查表

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 源码 | `GearInFrameLogger`(源文件位于 `src/data/scripts/gearin/combat\`) | `javac`(Gradle `compileJava`) |
| 编译依赖 | 游戏侧公开 API(`com.fs.starfarer.api.*`) 编译面 | `compileOnly`；运行时由游戏提供 |
| 产物 | `jars/GearIndustry.jar` | Gradle `jar` 任务输出 |
| 声明 | `mod_info.json` 的 `jars` 数组 | `ModManagerAPI` 解析 (公开只读面为 `ModSpecAPI.getJars()`) |
| 挂载 | 游戏启动期 | 把 `<MOD 根目录>/<jars 声明值>` 逐项加入脚本类加载器的 jar 列表，日志 `Getting ready to load jar file [...]` 与 `Loading jar files` |
| 解析 | 脚本类加载器 (`URLClassLoader`) | 脚本类按完整类名 `Class.forName`；jar 类优先于即时编译源码 (Janino 源码编译器是 jar 类加载器的子加载器，父加载器优先) |
| 注册与实例化 | `data/config/settings.json` 的 `plugins` 段 | 资源加载期由 `loadStarmap()` 注册，先于 CSV spec；每场战斗创建新实例并附加 |

运行期取得脚本类加载器的公开 API：`SettingsAPI.getScriptClassLoader()`；按完整类名创建实例：`SettingsAPI.getInstanceOfScript(String)`。本节不调用它们，只作阅读链路的对照。

## 核对编译任务链

这条链路先不要逐环节实现。本节的策略是只建立一条最小任务链，先让一个类被注册、加载、运行，再验证修改能生效：

1. 定位使用方：`settings.json` 的 `plugins` 段 (key → 完整类名)→ 注册进脚本类加载器 → 每场战斗创建实例。jar 内类**必须**被某个注册入口按名引用才会被加载。
2. 建立最小对象：`GearInFrameLogger` 编译进 `jars/GearIndustry.jar`，声明 `jars`，登记 `gearInFrameLogger`。
3. 验证使用方收到对象：进模拟战，日志出现 `GearIn FrameLogger tick` 行。
4. 修改一个参数 (如 `TICK_INTERVAL`) 重新构建，确认输出变化符合预期，从而证明游戏加载的是新构建的 jar 而不是缓存或即时编译源码。

## 实现战斗日志插件

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段，先依次放入指定类或方法；此刻类还不会被游戏加载，运行信号要等本节的 Gradle、JAR 声明与注册全部就绪后统一核对 (见本节末尾的验证方案)。

### 声明战斗插件类型

```java
// 插入位置：GearInFrameLogger 的 advance 方法
CombatEngineAPI engine = Global.getCombatEngine();
```

### 配置日志状态

```java
// 插入位置：GearInFrameLogger 的 advance 方法
if (engine == null || engine.isPaused()) {
    return;
}
```

### 处理战斗时间回调

```java
// 插入位置：GearInFrameLogger 的字段区和 advance 方法
elapsed += amount;
if (elapsed >= TICK_INTERVAL) {
    elapsed = 0f;
    Global.getLogger(GearInFrameLogger.class).info(
        "GearIn FrameLogger tick at battle time " + engine.getTotalElapsedTime(false));
}
```

## 配置 Gradle 文件

要点：`srcDirs = ["src"]` 让 Gradle 从 `src/data/scripts/...` 编译 (与 [C2-S2] 既有的源码位置一致)；

`compileOnly` 声明 API 依赖但不打包；`jar` 任务把产物直接放进 `jars\`，文件名与 `mod_info.json` 声明一致。

## 配置项目文件

`settings.gradle` 只声明工程名，与 MOD ID 保持一致即可：

## 声明 JAR 产物

在 MOD manifest([C1-S1] 建立) 上追加 `jars` 字段：

`jars` 数组的每一项必须是相对 MOD 根目录的路径，用 `/` 分隔。游戏启动时按 `<MOD 根目录>/<声明值>` 拼接；写成绝对路径会被拼成 `<MOD 根目录>/<绝对路径>` 之类的无效路径。

## 配置运行时设置

MOD 的 `settings.json` 会与全局设置按对象键合并，只提交自己的键：

`gearInFrameLogger` 是本节登记 key，值必须是 jar 内存在的完整类名。

## 核对插件源码

最后一个文件是插件类本身，它在每帧回调中按固定间隔写一行日志：

对应关系：

- `extends BaseEveryFrameCombatPlugin`：提供全部 `EveryFrameCombatPlugin` 方法的空实现，只需覆写 `advance`；直接实现接口则要写满 5 个方法 (含弃用的 `init`) [C7-S1]。
- `Global.getLogger(GearInFrameLogger.class)`：log4j 记录器，输出到 `starsector-core/starsector.log`；全程不需要在编译期直接 import log4j。
- `engine.isPaused()` 判断：暂停帧不累计 ([C7-S1] 的暂停语义)。
- `engine.getTotalElapsedTime(false)`：战斗内累计秒数，不含暂停；`amount` 是本帧经过秒数。

构建命令 (在 `Starsector/mods/GearIndustry/` 目录执行)：

```text
gradle jar
```

产物为 `jars/GearIndustry.jar`，其内部条目按包路径组织，包含 `data/scripts/gearin/combat/GearInFrameLogger.class`(可用 `jar tf` 核对)。

想确认游戏加载的是新构建的 jar 而不是缓存或旧源码，可先改插件类里的一个参数 (如日志间隔常量) 再重新构建 —— 日志中 tick 间隔随之变化即证明产物已更新；用 `jar tf jars/GearIndustry.jar` 可直接核对 jar 内部条目。

### 验证方案

前置数据：`src/data/scripts/gearin/combat/GearInFrameLogger`、`build.gradle` 与 `settings.gradle`、`mod_info.json` 的 `jars` 声明、`data/config/settings.json` 的 `gearInFrameLogger` 注册。

1. 在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认构建成功。
2. 启动游戏进入 `GearIn_test` 模拟战并部署。
3. 等待约 3 秒，随后按 ESC 暂停战斗。

预期结果：

- 部署后日志出现 `GearIn FrameLogger tick at battle time ...`，约每 3 秒一条 (插件被注册入口加载，`engine` 引用非空)。
- 按 ESC 暂停后 tick 立即停止，恢复战斗后继续 (暂停帧不再累计)。

## 完整最小例

任务链的每一环都需要一个具体文件。下面在 `mods/GearIndustry/` 中逐个建立 (`data/`、`graphics/`、`sounds/` 沿用 [C1-S1] 建立的目录)：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── config\
│       └── settings.json
├── src\
│   └── data/scripts/gearin/combat\
└── jars\
```

其中 `data/scripts/gearin/combat\` 目录存放本节的 `GearInFrameLogger` 类。

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

```groovy
rootProject.name = "gear_in"
```

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

```json
{
  "plugins": {
    "gearInFrameLogger": "data.scripts.gearin.combat.GearInFrameLogger"
  }
}
```

::: details 完整成品：GearInFrameLogger

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

:::

## 排查 JAR 未加载故障

`mod_info.json` 的 `jars` 路径没有指向 MOD 根目录下的产物。

**局部故障片段：**

```java
// 故障片段
"jars": ["/build/GearIndustry.jar"]
```

**局部修正片段：**

```java
// 修正片段
"jars": ["jars/GearIndustry.jar"]
```

复验：重新构建并启动游戏，在日志中确认 JAR 被加载。

## 验证方案

前置数据：`mod_info.json`([C1-S1]，追加本节 `jars` 字段)、本节 `build.gradle`、`settings.gradle`、`data/config/settings.json` 与 `GearInFrameLogger` 类。

1. 在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jars/GearIndustry.jar` 生成。
2. 启动启动器，勾选 "齿轮工业"，进入主菜单后退出；打开 `starsector.log`，观察 jar 加载记录。
3. 进入游戏 → 模拟战，等待 3 秒以上，确认日志出现 `GearIn FrameLogger tick at battle time ...`。
4. 打开 `starsector.log`，查看 `GearInFrameLogger` 的 tick 记录。

预期结果：

- 步骤 2：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jars/GearIndustry.jar` 存在。
- 步骤 3：`starsector.log` 先出现前缀为 `Getting ready to load jar file [` 的准备日志，方括号内解析结果包含 `mods/GearIndustry/jars/GearIndustry.jar`，随后出现 `Loading jar files`；安装根目录部分以本机实际路径为准，且没有由 `gear_in` 引起的 `ERROR`。
- 步骤 4：运行 3 秒以上后，日志每隔约 3 秒出现一行 `GearIn FrameLogger tick at battle time ...`。这条日志只可能在类被成功加载并实例化后产生，证明 jar 内容、`jars` 声明、`plugins` 登记这条链路全部打通。

## 故障分析

- **编译时报 `cannot find symbol`**：`com.fs.starfarer.api.*` 没有进入 classpath，或 IDE 语言级别低于 17。

    - **解决方案**：按本节 `build.gradle` 配置 `compileOnly`，确认 Project SDK 为 17 后重新构建。

- **日志没有 jar 加载行**：`mod_info.json` 缺少 `jars` 字段、MOD 没有启用，或 jar 声明属于未启用的 MOD。

    - **解决方案**：确认 manifest 含 `"jars": ["jars/GearIndustry.jar"]`，启用 MOD 后重启。

- **日志有 jar 加载行，但引用类时报 `Problem loading class`**：声明路径可能指向不存在的文件，或 jar 内没有对应完整类名。

    - **解决方案**：把声明改成正确的相对路径，再用 `jar tf` 对照包名和类名。

- **日志报告 `UnsupportedClassVersionError`**：class 文件版本高于游戏的 Java 17。

    - **解决方案**：保留 `toolchain 17` 与 `options.release = 17`，重新构建 jar。

- **类在 jar 里但游戏没有任何行为**：该类没有被注册入口按名引用。

    - **解决方案**：通过 `settings.json` 的 `plugins`、`mod_info.json` 的 `modPlugin`[C6-S2] 或数据文件的 `script` 列 [C2-S2] 接入。

- **修改源码并重启后行为仍是旧版本**：MOD 根目录残留同名源码，或 jar 没有重新构建。

    - **解决方案**：删除重复源码，以 jar 为唯一来源，并在启动前重新执行 `gradle jar`。

## 总结

本节把 "源码即时编译" 换成 "先编译成 jar 再按名加载"，并让这条链的每一段都有了可观察信号，`gradle jar` 的构建结果、日志里的 jar 加载行、模拟战中的 tick 行，以及改动参数后的行为变化。链在哪一段断，日志就会在哪一段缺信号，因此验证顺序就是排查顺序。

排查时依次问 jar 有没有被声明 (看日志加载行)、类名有没有被注册入口引用、字节码版本是否低于等于 17、jar 里是否真的有这个类。任何一环缺失，最终都表现为 `Problem loading class` 或 "没有任何行为"，区别只在日志哪一段先断。

"编译为类、按名引用" 的机制同样适用于 MOD 入口类：从 jar 实例化入口类、在游戏应用生命周期回调 (如 `onApplicationLoad`) 中做初始化，是本节链路的直接延续 [C6-S2]。

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
- `Starsector/starsector-core/starsector.bat`：游戏启动命令 (`../jre/bin/java.exe`，classpath 含游戏侧公开 API 编译面)。
- `Starsector/jre/release`：自带 JRE 版本信息 (Zulu 17.0.10)。
