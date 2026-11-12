# 确认 Java 与编译依赖

**从本章开始，教程假定您已掌握基础 Java 知识。** 本章将已有源码整理为独立工程，编译后通过 JAR 交给游戏加载。本节配置 JDK 与游戏 API 依赖，随后再迁移源码、配置 Gradle，完成构建与加载。

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

## 学习目标

完成后，您将能够：

- 为工程选择 JDK 17，并说明 Java 17 字节码目标的作用；
- 选择 IDE 的项目 SDK 与语言级别；
- 说明 classpath 中游戏 JAR 的编译职责；
- 将游戏依赖作为 compileOnly 配置。

## 前置知识

- [C1-S1] 的 MOD 目录与日志；
- [C2-S1] 至 [C2-S4] 的现有效果类与登记；
- Java 变量、方法、类、继承、接口与常用集合；
- 能够在 IDE 中创建工程并选择 SDK。

## 为工程选择 JDK

游戏使用 Java 17。本工程相应采用 JDK 17，生成的字节码目标也固定为 17。

游戏附带的 JRE 负责运行程序。编译源码还需要 `javac`，检查和打包产物还会用到 `jar`，这些开发工具由 JDK 提供。安装 JDK 17 后，将 IDE 的 Project SDK 指向这份安装，语言级别也设为 17。

本机可以同时安装多个 JDK，IDE 的 SDK 选项与终端的 PATH 也可以指向不同位置。**将 IDE 与命令行的编译工具都配置为 JDK 17。** 核对终端 `java`、`javac` 的版本和实际路径，确认它们使用预期的安装。

后面配置 Gradle 时还有两个相关设置：toolchain 选择编译器，`release` 指定产物的目标 Java 版本。运行 Gradle 自身的 JDK 则要满足所用 Gradle 版本的要求。它们都涉及 Java，但修改其中一项并不会替您完成其余设置。

::: warning 编译成功仍可能加载失败

高版本编译器可以生成游戏无法运行的 class。目标版本过高时，错误会在游戏加载阶段表现为 `UnsupportedClassVersionError`。本工程将 `release` 固定为 17，编译环境与产物目标分别核对。

:::

## 让编译器找到游戏 API

打开一份现有效果类，其中的 `com.fs.starfarer.api` 类型来自游戏。编译器要检查这些类型及其方法，就需要取得游戏已经编译好的类。把 `Starsector/starsector-core/` 下的 `*.jar` 加入工程 classpath，便是提供这组编译依赖。

classpath 指定查找类的目录和 JAR。配置完成后，IDE 应当能够解析源码中的游戏类型；若依然报找不到类，核对的对象就是实际游戏目录及其中的核心 JAR。

为了阅读接口与注释，还可以给这组依赖附加 `Starsector/starsector-core/starfarer.api.zip`。这样从代码跳转时能够进入公开 API 源码。**源码附件提供阅读内容，核心 JAR 提供编译所需的类。** 仅附加源码，并没有完成编译依赖配置。

## 决定 MOD 要打包哪些类

编译需要游戏 JAR，运行 MOD 时游戏也已经提供了这些类。因此我们的产物保存自己编写的代码即可。后面的 Gradle 配置采用 `compileOnly`：核心 JAR 参与编译，普通 Java 插件的 `jar` 任务则打包当前工程的输出。

这项区别在自行配置打包任务时尤其重要。若任务把依赖 JAR 一并合入产物，游戏类也可能被复制进去。核对这种任务时，检查它实际收集了哪些输入，继续让游戏运行环境提供核心类。

至此，游戏目录、JDK 与 API 依赖已经确定。下一节整理源码位置，[C6-S1] 再将源码目录、依赖和目标版本写进构建文件；同一套配置随后用于反复构建。

## 验证方案

1. 在 IDE 选择 JDK 17，将工程语言级别设为 17。
2. 核对终端 `java` 和 `javac` 版本，确认使用的是已配置的 JDK 17。
3. 将核心目录 JAR 加入工程编译依赖，附加公开 API 源码。
4. 从一处已有 API 引用跳转，确认 IDE 能够定位类型、方法与注释。

预期结果：

- IDE 与命令行使用 JDK 17，工程语言级别设为 17。
- IDE 可以解析已有游戏类，核心 JAR 进入编译 classpath。
- API 源码可用于查看方法与注释。
- MOD 产物职责为自己的类，游戏依赖按运行环境提供。

## 故障分析

- **加载时报 UnsupportedClassVersionError**：产物目标高于游戏支持版本。
    - **解决方案**：将编译目标 release 设为 17，使用对应工具链重新构建。

- **IDE 或编译器找不到 API 类**：核心 JAR 未进入 classpath，或路径指向不同目录。
    - **解决方案**：核对 starsector-core 路径和编译依赖，刷新工程。

- **终端工具版本与 IDE 不同**：PATH、SDK 和 Gradle 工具链各自选择了不同安装。
    - **解决方案**：按工具的实际路径核对，编译固定为 JDK 17。

- **JAR 包含游戏类**：自定义打包过程合并了核心依赖。
    - **解决方案**：使用工程输出作为产物输入，游戏 JAR 采用 compileOnly。

## 总结

本节准备的是源码离开游戏之后的编译条件：JDK 提供工具，核心 JAR 提供游戏类型，字节码目标保证产物能由当前运行时加载。源码附件让 API 便于阅读，这些条件随后都会落实到工程配置中。

现有系统、插件和生成器仍使用原来的完整类名。接下来改变源码的保存与构建方式，已经建立的数据引用继续沿用。

## 建议参考项

- `Starsector/starsector-core/*.jar`：核心编译依赖。
- `Starsector/starsector-core/starfarer.api.zip`：公开 API 源码附件。

## 配置 Gradle 工程

本节为 MOD 配置 Gradle，让它从 `src/` 编译源码，引用游戏核心 JAR，最后生成 `jars/GearIndustry.jar`。源码位置、游戏路径和 Java 目标都写进工程文件，此后修改代码便可以沿用同一组构建任务。

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

Gradle 根据工程配置安排编译和打包。沿用 [C6-S1] 的 JDK，安装并配置 Gradle 后，在终端执行：

```text
gradle --version
```

命令可用后，在 `GearIndustry/` 根目录创建 `settings.gradle`、`build.gradle` 和 `gradle.properties`。它们分别保存工程名、构建任务与本机游戏路径。下面的命令都在这个目录执行，**终端所在工程决定 Gradle 读取哪份配置**。

::: details 构建配置也属于工程

**编者更看重工程能够完整交接。** 源码目录、依赖与目标版本如果只存在于某个编辑器的本机设置里，拿到源码的人还要重新猜一遍。本节将这些条件保存为 Gradle 配置，换编辑器或交给他人时继续使用。这里配置的 Java 编译与普通 JAR 打包，已经足以承担当前 MOD 的构建。

:::

### 设置工程名

在 `settings.gradle` 写入：

```groovy
rootProject.name = "gear_in"
```

Gradle 用它标识当前工程。教程沿用 `gear_in`，便于与练习 MOD 对应；MOD 的注册 ID 仍在 `mod_info.json`，产物文件名则由稍后的 `jar` 任务设置。

### 设置游戏路径

在 `gradle.properties` 中填写 `starsectorRoot`。下面是一个路径示例，请换成您的实际安装位置：

```properties
starsectorRoot=C:/Games/Starsector
```

路径指向包含 `starsector-core` 的游戏根目录，Windows 下也可以使用 `/`。接下来需要从这个位置收集依赖。在 `build.gradle` 中，Java 插件声明之后加入以下局部片段：

```groovy
def gameRoot = file(providers.gradleProperty("starsectorRoot").get())
```

`gradleProperty("starsectorRoot")` 读取工程属性，`file(...)` 将它解析为路径，保存到 `gameRoot`。本机安装位置只需在属性文件中填写一次，后面的依赖声明都从这里取得游戏目录。

## 配置 Java 编译任务

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

Java 插件提供 `compileJava`、`jar` 等任务，toolchain 则指定编译使用 JDK 17。现在任务已经建立，还需要告诉它们源码与依赖的位置。

Gradle 会查找本机可用的 JDK。若 JDK 17 位于它没有自动发现的位置，在 `gradle.properties` 中补充 `org.gradle.java.installations.paths`，填写 JDK 根目录。IDE 的 SDK 与 Gradle 的 toolchain 分别设置，IDE 中选好 JDK 17 后，仍要确认 Gradle 实际找到的安装。

### 指定源码集合

在 `build.gradle` 加入：

```groovy
sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}
```

`main` 表示主源码集合，`srcDirs` 指向 MOD 根目录下的 `src/`。[C6-S2] 迁入的 Java 文件因而成为编译输入，内部的包目录层级继续保留。

### 提供游戏依赖

源码中已经使用了游戏 API，所以编译器还需要游戏核心 JAR。在 `gameRoot` 定义之后声明依赖：

```groovy
dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
}
```

`fileTree` 从核心目录收集 `*.jar`，`compileOnly` 将它们提供给编译器。源码中的游戏类型与方法由此能够解析。

**游戏核心类仍由游戏提供，MOD JAR 保存本工程的类。** 普通 Java 插件的 `jar` 任务打包工程输出，与这里的依赖方式相配。以后若增加合并依赖的打包任务，还要检查输入，避免把核心 JAR 也合入产物。

### 固定源码编码与产物目标

继续在 `build.gradle` 中加入：

```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}
```

`options.encoding` 指定编译器以 UTF-8 读取源码，中文注释与字符串也就有了确定的编码。`options.release` 固定目标 Java 版本，与前面选择的 JDK 一同设为 17，使生成的 class 能由教程采用的游戏运行时加载。

::: warning 编译器版本与目标版本

toolchain 选择编译器，`release` 选择产物目标。目标版本过高时，编译仍可能成功，游戏加载时才报告 `UnsupportedClassVersionError`，因此这两项设置都保留为 17。

:::

## 设置 JAR 输出

最后配置 `jar` 任务：

```groovy
tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}
```

`destinationDirectory` 指定输出目录，`archiveFileName` 指定文件名，合起来得到 `jars/GearIndustry.jar`。这也是下一节写入 `mod_info.json` 的路径。**构建输出与游戏加载声明使用同一个文件位置**，生成产物后才能让游戏读取它。

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

至此，工程已经知道源码在哪里、依赖哪些游戏类、采用什么编译目标，以及产物输出到哪里。`compileJava` 成功后出现的 class，是这份配置实际工作的结果。

下一节执行 `jar` 并填写加载声明。后续修改源码继续使用当前任务，构建条件也随工程保留下来。

## 建议参考项

- `Starsector/starsector-core/*.jar`：`compileOnly` 使用的游戏核心编译依赖。
- `settings.gradle`：当前工程名的设置位置。
- `build.gradle`：源码集合、编译选项、依赖与 JAR 任务的配置位置。
- `gradle.properties`：`starsectorRoot` 本机路径属性的设置位置。
