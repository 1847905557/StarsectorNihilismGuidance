# 配置 Gradle 工程

源码迁入 `src/` 后，需要配置编译依赖与构建产物。本节使用 Gradle 编译 Java 17 源码，并将 JAR 输出到 MOD 的 `jars/`。源码目录、游戏路径和输出位置确定后，后续修改可以沿用同一份构建配置。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 配置工程名、源码目录和 Java toolchain；
- 为游戏核心 JAR 声明 `compileOnly` 依赖；
- 设置 UTF-8、Java 17 字节码目标与 JAR 输出位置；
- 用 Gradle 任务确认工程配置和源码编译结果。

## 前置知识

- [C6-S1] 的 Java 17、IDE 设置与游戏 API 编译依赖；
- [C6-S2] 的 `src/` 源码布局；
- 能够阅读 Java 的包名、类声明与编译错误；
- 能够在终端进入目录并执行命令。

## 准备构建文件

在 `GearIndustry/` 根目录创建 `settings.gradle`、`build.gradle` 和 `gradle.properties`。它们分别保存工程名、构建配置与本机游戏路径。

Gradle 是执行这份构建配置的工具。使用 [C6-S1] 配置好的 JDK，并确认终端能够运行：

```text
gradle --version
```

确认命令可用后，以下操作均在 MOD 根目录进行。**Gradle 从当前工程读取构建配置**，终端所在目录需要与构建文件对应。

### 设置工程名

在 `settings.gradle` 写入：

```groovy
rootProject.name = "gear_in"
```

这是 Gradle 的工程名。MOD 的注册 ID 仍由 `mod_info.json` 决定，JAR 文件名则在后面的 `jar` 任务中设置；本教程为工程沿用 `gear_in`，方便辨认。

### 设置游戏路径

在 `gradle.properties` 中填写 `starsectorRoot`。下面是一个路径示例，请换成您的实际安装位置：

```properties
starsectorRoot=C:/Games/Starsector
```

路径要指向包含 `starsector-core` 的游戏根目录，Windows 下也可以使用 `/`。在 `build.gradle` 中用下面的局部片段读取它：

```groovy
def gameRoot = file(providers.gradleProperty("starsectorRoot").get())
```

`gradleProperty("starsectorRoot")` 取得同名工程属性，`file(...)` 把它交给 Gradle 作为文件路径使用。之后的依赖声明都从 `gameRoot` 出发；游戏换了位置，就修改这条属性。

## 让 Gradle 编译 src

在 `build.gradle` 开头启用 Java 插件，并选择 JDK 17：

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

Java 插件提供 `compileJava`、`jar` 等任务，toolchain 为编译任务选择 JDK。后面分别用这两个任务编译源码和生成 JAR。

Gradle 会查找本机可用的 JDK。若 JDK 17 位于它没有自动发现的位置，在 `gradle.properties` 中补充 `org.gradle.java.installations.paths`，填写 JDK 根目录；IDE 的 SDK 选择与 Gradle 的查找结果分别核对。

再写入源码目录的局部片段：

```groovy
sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}
```

`main` 是本工程的主源码集合，`srcDirs = ["src"]` 将 MOD 根目录下的 `src/` 设为源码目录。[C6-S2] 迁入的 Java 文件按包名保留目录层级，Gradle 从这里读取编译输入。

## 加入游戏 API 依赖

源码引用了游戏 API，因此编译时需要游戏的核心 JAR。在 `gameRoot` 定义之后加入依赖声明：

```groovy
dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
}
```

`fileTree` 收集游戏核心目录中的 JAR，`include` 选择该目录下的 `*.jar`。`compileOnly` 把它们放入编译依赖，供编译器解析游戏类。

**游戏本身提供这些类，MOD 的产物只需要保存您自己的代码。** 普通 Java 插件的 `jar` 任务打包本工程输出；这里也采用 `compileOnly` 表达游戏依赖由运行环境提供。若自行加入合并依赖 JAR 的打包配置，还需要确认其中的游戏类保持由游戏提供。

## 固定编码和字节码目标

继续在 `build.gradle` 中加入：

```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}
```

toolchain 选择编译器，`options.release` 固定目标 Java 版本。两项都设为 17，生成的 class 才与本教程使用的游戏运行时一致。`options.encoding` 则让编译器按 UTF-8 读取源码，中文注释和字符串也有确定的编码。

源码可以正常编译，产物也可能因目标版本过高而在游戏中报 `UnsupportedClassVersionError`。**编译器版本和游戏加载的字节码版本需要分别确认**，因此这里同时设置 toolchain 与 `release`。

## 设置 JAR 输出

最后配置 `jar` 任务：

```groovy
tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}
```

`archiveFileName` 设置文件名，`destinationDirectory` 设置输出目录。两者合起来得到 `jars/GearIndustry.jar`，下一节会把同一个路径写入 `mod_info.json`。

**JAR 输出路径需要与 `mod_info.json` 的加载路径一致。** 本节确定输出位置，下一节构建产物并填写加载声明。

## 完整最小例

三个文件都位于 MOD 根目录。`gradle.properties` 按上面的示例填写实际游戏路径，其余两个文件如下。

`settings.gradle`：

```groovy
rootProject.name = "gear_in"
```

`build.gradle`：

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

## 验证方案

1. 在 MOD 根目录执行 `gradle tasks --all`，检查任务列表。
2. 执行 `gradle compileJava`，确认构建完成。
3. 检查 `build/classes/java/main/`，确认出现与 `src/` 中包名对应的 class 文件。
4. 核对 `jar` 任务的输出配置：目录为 `jars`，文件名为 `GearIndustry.jar`。

预期结果：

- 任务列表包含 `compileJava` 和 `jar`。
- 编译使用 JDK 17、UTF-8 和 Java 17 目标，任务完成时显示 `BUILD SUCCESSFUL`。
- 有 Java 源码时，`build/classes/java/main/` 中出现对应 class 文件。
- JAR 输出已配置为 `jars/GearIndustry.jar`，可继续执行构建与游戏声明。

## 故障分析

- **执行 gradle 时找不到命令**：Gradle 尚未安装，或终端环境没有对应的可执行路径。
    - **解决方案**：配置 Gradle 命令的可执行路径，重新打开终端后运行 `gradle --version`。

- **读取不到 starsectorRoot**：工程属性未填写，或当前命令没有在包含构建文件的目录执行。
    - **解决方案**：在 MOD 根目录的 `gradle.properties` 填写 `starsectorRoot`，从该目录重新执行任务。

- **Gradle 提示找不到 Java 17 安装**：toolchain 要求 JDK 17，但自动发现结果中没有对应安装。
    - **解决方案**：确认已经安装 JDK 17，在 `gradle.properties` 的 `org.gradle.java.installations.paths` 中填写其根目录，再执行编译。

- **compileJava 显示 NO-SOURCE**：`src/` 中没有 Java 文件，或 `srcDirs` 指向其他位置。
    - **解决方案**：确认迁入的 Java 文件位于 `src/`，并将主源码目录设为 `["src"]`。

- **编译找不到游戏 API 类**：游戏路径错误，或核心 JAR 没有进入 `compileOnly`。
    - **解决方案**：确认 `starsectorRoot` 指向包含 `starsector-core` 的目录，再核对 `fileTree`。

- **游戏报告 UnsupportedClassVersionError**：编译产物的目标版本高于游戏运行时。
    - **解决方案**：确认 toolchain 与 `options.release` 均为 17，重新编译和构建 JAR。

- **MOD JAR 中出现游戏类**：自定义打包配置把核心依赖合并进了产物。
    - **解决方案**：使用本节普通 `jar` 输出，让游戏依赖以 `compileOnly` 参与编译，并检查自定义打包任务的输入。

## 总结

工程配置将 `src/` 中的源码、核心 JAR 编译依赖与 Java 17 目标组织为一套构建任务。`compileJava` 生成 class 文件，`jar` 将本工程输出打包到 `jars/GearIndustry.jar`。

下一节执行 JAR 构建，并在 `mod_info.json` 中声明产物路径。后续修改代码时继续使用这份配置，重新构建即可更新产物。

## 建议参考项

- `Starsector/starsector-core/*.jar`：`compileOnly` 使用的游戏核心编译依赖。
- `settings.gradle`：当前工程名的设置位置。
- `build.gradle`：源码集合、编译选项、依赖与 JAR 任务的配置位置。
- `gradle.properties`：`starsectorRoot` 本机路径属性的设置位置。
