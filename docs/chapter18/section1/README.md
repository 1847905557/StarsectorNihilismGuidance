# 接入常用依赖库

LazyLib、GraphicsLib 和 MagicLib 分别提供计算、图形与其他常用功能。使用它们之前，现有 MOD 要同时接通启动器的依赖关系和 Gradle 的编译路径。本节完成这两份配置，再说明库加载后，哪些服务可以直接调用，哪些数据仍由使用方登记。

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 在 `mod_info.json` 中声明 LazyLib、GraphicsLib 和 MagicLib；
- 用 Gradle `compileOnly` 把三库 JAR 加入编译 classpath；
- 区分启动器依赖、编译期依赖和运行时初始化；
- 判断依赖版本、JAR 路径和初始化阶段分别会产生什么结果。

## 前置知识

- [C1-S1] 的 MOD 目录与 `mod_info.json`；
- [C6-S1] 的 Gradle 工程、`compileOnly` 和 JAR 输出；
- [C7-S1] 的 `BaseModPlugin` 与 `onApplicationLoad()`。

## 声明启动器依赖

在 GearIn 的 `mod_info.json` 中加入 `dependencies`。启动器按依赖 MOD 的 `id` 查找，下面三个值分别对应三库的 manifest：

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

启动器把这个值拆成 major、minor 和 patch，与已安装版本逐级比较。major 不同会阻断加载，minor 或 patch 不同则形成相应的兼容级别，仍允许选择。

::: warning version 使用具体版本比较
`"version":"3.0.0"` 表达与 3.0.0 的版本比较，较高版本也可能发生不匹配。填入该字段时，按 major、minor、patch 的实际结果判断。
:::

本节成品省略三项依赖的 `version`，让启动器只按稳定 ID 确认三库是否存在。需要锁定经过验证的接口版本时，再为对应依赖填入具体版本并按上述兼容级别检查结果。

## 建立编译 classpath

打开 [C6-S1] 的 `build.gradle`，沿用 `gradle.properties` 中的 `starsectorRoot`，从游戏根目录找到 `mods`。先补齐下面的目录变量：

```groovy
def gameRoot = file(providers.gradleProperty("starsectorRoot").get())
def modsDir = file("${gameRoot}/mods")

def dependencyJarDirs = [
    "${modsDir}/LazyLib/jars",
    "${modsDir}/LazyLib/jars/internal",
    "${modsDir}/GraphicsLib/jars",
    "${modsDir}/MagicLib/jars",
]
```

LazyLib 的主目录与 `internal` 目录一起提供其主 JAR 和配套 JAR，GraphicsLib 与 MagicLib 则从各自的 `jars` 目录取得文件。数组先确定位置，下面的 `dependencies` 再把这些文件交给编译器：

```groovy
dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
    dependencyJarDirs.each { dir ->
        compileOnly fileTree(dir: dir, include: ["*.jar"])
    }
}
```

第一条 `compileOnly` 保留 [C6-S1] 的游戏 API，循环再把四个目录中的 JAR 加入编译 classpath。保存后，为实际文件列表补一个查看任务：

```groovy
tasks.register("printCompileClasspath") {
    doLast {
        sourceSets.main.compileClasspath.files.each { println it }
    }
}
```

执行 `gradle printCompileClasspath` 就能看到编译器使用的文件路径。

Gradle 能够据此解析三库的类，但打包 GearIn 时不会把这些外部类复制进 `GearIndustry.jar`。玩家的游戏进程仍从三库各自的 MOD 目录加载对应 JAR。

这份配置通过游戏路径属性定位依赖，工程放在其他目录也可以编译。若按目录层级推导路径，只有工程位于 `Starsector/mods/GearIndustry` 时，`rootDir.parentFile` 才是 `mods`；工程移动后，这个条件也跟着变化。依赖数组中的库目录名仍按您实际安装的名称填写。

## 让编译配置与游戏加载对应

现在可以在代码中导入 `org.lazywizard.lazylib.MathUtils`。编译器从 classpath 找到这个类，启动器根据 manifest 联动启用 LazyLib，游戏再加载它的 JAR。**编译通过与依赖 MOD 已加载，各自有对应的配置。**

### 依赖配置职责速查表

| 层次           | 配置入口                     | 直接结果                          |
| -------------- | ---------------------------- | --------------------------------- |
| 启动器依赖     | `mod_info.json.dependencies` | 查找并联动启用前置 MOD            |
| 编译 classpath | Gradle `compileOnly`         | 编译器能够解析依赖库类型          |
| 运行时初始化   | 依赖库入口与使用方入口       | 建立库的服务、shader 或数据注册表 |

`compileOnly` 不会替 GearIn 声明运行时依赖，`dependencies` 也不会替 Gradle 寻找 JAR。两份配置描述的是同一组库，却服务于两个不同阶段。

## 接到库已有的运行入口

启动器完成依赖检查后，游戏根据每个前置 MOD 的 `mod_info.json` 加载它自己的 JAR 与入口插件。GearIn 只负责调用公开入口，以及登记只属于 GearIn 的数据文件。

LazyLib 的运行环境由 LazyLib 自身建立。GearIn 编译通过后，可以在战斗或生涯代码中直接调用 `MathUtils`、`AIUtils` 等公开工具，具体方法从下一节开始讲解。

MagicLib 同样通过自己的入口处理库初始化和约定数据文件。Java API 由 GearIn 的调用点使用，MagicTrail、MagicBounty 等数据则放在 MagicLib 规定的路径；两类接入都以 MagicLib MOD 已被加载为前提。

GraphicsLib 的入口也调用 `ShaderLib.init()`。使用方在自己的 `onApplicationLoad()` 中调用它，可以确保检查能力与读取数据之前已经完成初始化；环境已建立时，这个调用直接返回。GearIn 随后检查 shader 与 buffer 能力，再登记自己的 light/texture CSV，具体操作由后续 GraphicsLib 页面展开。

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
  "gameVersion":"0.98.5a-RC3",
  "dependencies":[
    {"id":"lw_lazylib","name":"LazyLib"},
    {"id":"shaderLib","name":"GraphicsLib"},
    {"id":"MagicLib","name":"MagicLib"}
  ],
  "jars":["jars/GearIndustry.jar"],
  "modPlugin":"data.scripts.gearin.GearInModPlugin"
}
```

`build.gradle` 沿用 [C6-S1] 的 Java 17、源码目录和 JAR 任务，并把依赖目录加入已有的 `dependencies`：

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

def gameRoot = file(providers.gradleProperty("starsectorRoot").get())
def modsDir = file("${gameRoot}/mods")

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

tasks.register("printCompileClasspath") {
    doLast {
        sourceSets.main.compileClasspath.files.each { println it }
    }
}
```

完整配置包含 `printCompileClasspath`，继续写代码前可以执行它确认实际依赖。manifest 和 Gradle 准备完成后，GraphicsLib 的初始化接到应用加载入口；光照与纹理的登记在相应功能页完成。

## 验证方案

前置数据：LazyLib、GraphicsLib 和 MagicLib 位于 `Starsector/mods/`，GearIn 的 manifest 与 Gradle 已采用本节成品配置。

1. 执行 `gradle printCompileClasspath`，在文件列表中定位游戏核心、LazyLib、GraphicsLib 和 MagicLib 的 JAR。
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

- **GearIndustry.jar 中出现依赖库包**：额外的归档配置把依赖 JAR 合并进了产物。
    - **解决方案**：检查归档任务的输入，移除依赖合并配置，保留 `compileOnly`，重新构建并用 `jar tf` 检查产物。

- **GraphicsLib 类可以调用但光照和贴图数据没有生效**：manifest 与 classpath 已接通，使用方尚未完成 `ShaderLib.init()` 和数据登记。
    - **解决方案**：按后续 GraphicsLib 页面在应用加载阶段建立初始化与 CSV 加载链。

## 总结

三库通过 manifest 参与游戏加载，通过 `compileOnly` 提供编译所需类型，外部类继续由各库自己的 JAR 提供。接下来使用库的服务时，调用点负责自己的参数与资源登记，库入口负责建立运行环境；加载与调用由此能够分别追到实际配置。

## 建议参考项

- `mod_info.json`：`dependencies` 的 ID、名称与可选版本；
- `Gradle compileOnly`：只进入编译 classpath 的依赖声明；
- `Starsector/mods/LazyLib/mod_info.json`：`lw_lazylib` ID 与 JAR 目录；
- `Starsector/mods/GraphicsLib/mod_info.json`：`shaderLib` ID 与 `ShaderLib` 入口；
- `Starsector/mods/MagicLib/mod_info.json`：`MagicLib` ID 与运行时入口。
