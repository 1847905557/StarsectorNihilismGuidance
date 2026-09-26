# 接入常用依赖库

LazyLib、GraphicsLib 和 MagicLib 都以独立 MOD 的形式安装，却通过不同的路径进入 GearIn：启动器先根据 `mod_info.json` 确认依赖关系，Gradle 再从三个 MOD 的 JAR 中取得编译所需的类，游戏启动后才由依赖库和 GearIn 各自完成运行时初始化。本节把这三层关系接通，后续页面便可以直接使用库提供的 Java API 和数据入口。

*本节预期阅读时间：约 20 分钟。*

*本节预期操作时间：约 25 分钟。*

## 学习目标

完成后，您将能够：

- 在 `mod_info.json` 中声明 LazyLib、GraphicsLib 和 MagicLib；
- 用 Gradle `compileOnly` 把三库 JAR 加入编译 classpath；
- 区分启动器依赖、编译期依赖和运行时初始化；
- 判断依赖版本、JAR 路径和初始化阶段分别会产生什么结果。

## 前置知识

- [C1-S1] 的 MOD 目录与 `mod_info.json`；
- [C6-S3] 的 Gradle 工程、`compileOnly` 和 JAR 输出；
- [C7-S1] 的 `BaseModPlugin` 与 `onApplicationLoad()`。

## 区分三层依赖

在 Java 源码中写下 `import org.lazywizard.lazylib.MathUtils;` 时，编译器必须能在 classpath 中找到 `MathUtils`；玩家启用 GearIn 时，启动器必须知道 `lw_lazylib` 是前置 MOD；真正进入游戏后，依赖库的 JAR 和入口插件还必须已经由游戏加载。这三个动作分别属于 manifest、Gradle 和运行时。

### 三层依赖职责速查表

| 层次 | 配置入口 | 直接结果 |
|---|---|---|
| 启动器依赖 | `mod_info.json.dependencies` | 查找并联动启用前置 MOD |
| 编译 classpath | Gradle `compileOnly` | 编译器能够解析依赖库类型 |
| 运行时初始化 | 依赖库入口与使用方入口 | 建立库的服务、shader 或数据注册表 |

`compileOnly` 不会替 GearIn 声明运行时依赖，`dependencies` 也不会替 Gradle 寻找 JAR。两份配置描述的是同一组库，却服务于两个不同阶段。

## 声明启动器依赖

在 GearIn 的 `mod_info.json` 中加入 `dependencies`。启动器读取的是依赖 MOD 自己声明的 `id`，因此这里的三个值必须与三库 manifest 逐字符一致：

```json
"dependencies": [
  {"id":"lw_lazylib","name":"LazyLib"},
  {"id":"shaderLib","name":"GraphicsLib"},
  {"id":"MagicLib","name":"MagicLib"}
]
```

`id` 决定启动器寻找哪个 MOD，`name` 决定依赖提示中显示什么文字。选中 GearIn 时，启动器沿依赖关系选中已安装的三库；其中一个 ID 不存在或对应 MOD 没有安装时，GearIn 无法被启用。

### 理解可选版本字段

依赖项还可以写入 `version`，例如：

```json
{"id":"lw_lazylib","name":"LazyLib","version":"3.0.0"}
```

这个值会被拆成 major、minor 和 patch 三部分，再与已安装 MOD 的版本逐级比较。它表达的是一个待比较的具体版本，不是最低版本范围：major 不同属于阻断加载的主版本不匹配，minor 或 patch 不同会形成相应的兼容级别，启动器仍允许选择。

本节成品省略三项依赖的 `version`，让启动器只按稳定 ID 确认三库是否存在。需要锁定经过验证的接口版本时，再为对应依赖填入具体版本并按上述兼容级别检查结果。

## 建立编译 classpath

GearIn 位于 `Starsector/mods/GearIndustry` 时，Gradle 工程根目录的父目录是 `mods`，再上一层是游戏根目录。先保存这两个位置，再列出三库提供 JAR 的目录：

```groovy
def gameRoot = rootDir.parentFile.parentFile
def modsDir = rootDir.parentFile

def dependencyJarDirs = [
    "${modsDir}/LazyLib/jars",
    "${modsDir}/LazyLib/jars/internal",
    "${modsDir}/GraphicsLib/jars",
    "${modsDir}/MagicLib/jars",
]
```

LazyLib 的主目录与 `internal` 目录共同进入成熟项目的 classpath；GraphicsLib 和 MagicLib 各自从 `jars` 目录取得主 JAR 及配套 JAR。目录数组只描述文件位置，随后由 `dependencies` 块决定这些文件以什么方式参与构建：

```groovy
dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
    dependencyJarDirs.each { dir ->
        compileOnly fileTree(dir: dir, include: ["*.jar"])
    }
}
```

第一条 `compileOnly` 保留 [C6-S3] 建立的游戏 API；循环把四个依赖目录中的 `.jar` 加入同一个编译 classpath。Gradle 能够据此解析三库的类，但打包 GearIn 时不会把这些外部类复制进 `GearIndustry.jar`。玩家的游戏进程仍从三库各自的 MOD 目录加载对应 JAR。

如果 MOD 工程不在 `Starsector/mods/` 下，`rootDir.parentFile` 便不再是 `mods`。此时应把 `gameRoot` 和 `modsDir` 改为当前机器的真实目录关系，后面的依赖数组和 `compileOnly` 结构保持相同。

## 理解运行时加载

启动器完成依赖检查后，游戏根据每个前置 MOD 的 `mod_info.json` 加载它自己的 JAR 与入口插件。GearIn 只负责调用公开入口，以及登记只属于 GearIn 的数据文件。

LazyLib 的运行环境由 LazyLib 自身建立。GearIn 编译通过后，可以在战斗或生涯代码中直接调用 `MathUtils`、`AIUtils` 等公开工具，具体方法从下一节开始讲解。

MagicLib 同样通过自己的入口处理库初始化和约定数据文件。Java API 由 GearIn 的调用点使用，MagicTrail、MagicBounty 等数据则放在 MagicLib 规定的路径；两类接入都以 MagicLib MOD 已被加载为前提。

GraphicsLib 还要求使用方在 `onApplicationLoad()` 中调用 `ShaderLib.init()`。初始化完成后，GearIn 才检查 shader 与 buffer 能力，并读取自己的 light/texture CSV。后续 GraphicsLib 页面会逐步建立这条入口，本节先把 manifest 与 classpath 准备到位。

## 选择渲染依赖

::: tip GraphicsLib 与 BoxUtil 的职责
GraphicsLib 用于 shader、动态光照以及 material/normal 贴图管线。需要创建自定义渲染实体、批量提交 Sprite 贴图或管理渲染队列时，可以参考 [C20-S6] 的 BoxUtil 选型。
:::

## 完整最小例

把三项依赖放回 GearIn 的 manifest 后，`mod_info.json` 的相关内容如下：

```json
{
  "id":"gear_in",
  "name":"Gear Industry",
  "version":"1.0.0",
  "gameVersion":"0.98a-RC8",
  "dependencies":[
    {"id":"lw_lazylib","name":"LazyLib"},
    {"id":"shaderLib","name":"GraphicsLib"},
    {"id":"MagicLib","name":"MagicLib"}
  ],
  "jars":["jars/GearIndustry.jar"],
  "modPlugin":"data.scripts.gearin.GearInModPlugin"
}
```

`build.gradle` 沿用 [C6-S3] 的 Java 17、源码目录和 JAR 任务，并把依赖目录加入已有的 `dependencies`：

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

def gameRoot = rootDir.parentFile.parentFile
def modsDir = rootDir.parentFile

def dependencyJarDirs = [
    "${modsDir}/LazyLib/jars",
    "${modsDir}/LazyLib/jars/internal",
    "${modsDir}/GraphicsLib/jars",
    "${modsDir}/MagicLib/jars",
]

sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}

dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
    dependencyJarDirs.each { dir ->
        compileOnly fileTree(dir: dir, include: ["*.jar"])
    }
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

这两份配置共同完成接入：manifest 让启动器找到三库，Gradle 让编译器找到三库 API。GraphicsLib 的使用方初始化会在进入其功能页面时加入 `GearInModPlugin`。

## 验证方案

前置数据：LazyLib、GraphicsLib 和 MagicLib 位于 `Starsector/mods/`，GearIn 的 manifest 与 Gradle 已采用本节成品配置。

1. 执行 `gradle dependencies --configuration compileClasspath`，在输出中定位 LazyLib、GraphicsLib 和 MagicLib 的 JAR。
2. 执行 `gradle jar`，再用 `jar tf jars/GearIndustry.jar` 检查产物条目。
3. 打开启动器并勾选 GearIn，确认三项依赖随之启用。
4. 取消任一依赖，再次勾选 GearIn，观察启动器给出的依赖提示。
5. 恢复三项依赖，以正常配置启动游戏并检查 `starsector.log` 中的 MOD 加载记录。

预期结果：

- `compileClasspath` 同时包含游戏 API 和三库 JAR；
- `GearIndustry.jar` 只包含 GearIn 自身编译产物，没有复制依赖库的包；
- 启动器按三个精确 ID 建立依赖关系，缺少其中一项时指出对应名称；
- 三库完整时，游戏能够加载 GearIn，后续页面可以继续接入库的运行功能。

## 故障分析

- **启动器找不到依赖**：GearIn 声明的 `id` 与依赖库 manifest 中的值不一致，或对应 MOD 没有安装。
    - **解决方案**：按三库 manifest 恢复精确 ID，并确认目录直接位于 `Starsector/mods/`。

- **启动器阻止选择带版本的依赖**：声明版本与已安装版本发生 major 不匹配，或对方没有可判定的版本。
    - **解决方案**：核对实际兼容版本；确认接口兼容后更新具体版本值，或按本节成品省略该字段。

- **Gradle 报告依赖库的包不存在**：`modsDir` 没有指向真实 `Starsector/mods`，或 JAR 目录名称与安装目录不一致。
    - **解决方案**：执行 classpath 查询并逐项修正四个目录。

- **编译通过但进入游戏时报 `NoClassDefFoundError`**：Gradle 已看到 JAR，而 manifest 没有声明对应运行时依赖。
    - **解决方案**：在 `dependencies` 中补回该库的精确 ID，并通过启动器启用它。

- **manifest 已声明但 Java 无法编译**：启动器关系已经存在，而依赖 JAR 没有进入 `compileClasspath`。
    - **解决方案**：补齐 `dependencyJarDirs` 和 `compileOnly fileTree(...)`。

- **GearIndustry.jar 中出现依赖库包**：构建使用了 `implementation` 或把依赖 JAR 并入了归档任务。
    - **解决方案**：恢复 `compileOnly`，重新构建并用 `jar tf` 检查产物。

- **GraphicsLib 类可以调用但光照和贴图数据没有生效**：manifest 与 classpath 已接通，使用方尚未完成 `ShaderLib.init()` 和数据登记。
    - **解决方案**：按后续 GraphicsLib 页面在应用加载阶段建立初始化与 CSV 加载链。

## 总结

三库接入由三个阶段共同完成：manifest 决定启动器能否建立依赖关系，Gradle `compileOnly` 决定源码能否解析库 API，运行时入口决定具体服务与数据是否可用。三层使用同一组依赖，却不能互相代替。

## 建议参考项

- `mod_info.json`：`dependencies` 的 ID、名称与可选版本；
- `Gradle compileOnly`：只进入编译 classpath 的依赖声明；
- `LazyLib/mod_info.json`：`lw_lazylib` ID 与 JAR 目录；
- `GraphicsLib/mod_info.json`：`shaderLib` ID 与 `ShaderLib` 入口；
- `MagicLib/mod_info.json`：`MagicLib` ID 与运行时入口。
