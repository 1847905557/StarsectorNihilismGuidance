# 确认 Java 与编译依赖

从本章开始，教程按读者已掌握基础 Java 组织代码讲解。此前的源码已经能由游戏加载，本章将它们整理为独立工程，编译后通过 JAR 使用。本节先确定 JDK、字节码目标与游戏 API 依赖，随后再迁移源码和配置 Gradle。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 确认游戏运行时与本工程的 Java 目标；
- 选择 IDE 的项目 SDK 与语言级别；
- 说明 classpath 中游戏 JAR 的编译职责；
- 将游戏依赖作为 compileOnly 配置。

## 前置知识

- [C1-S1] 的 MOD 目录与日志；
- [C2-S1] 至 [C2-S4] 的现有效果类与登记；
- Java 变量、方法、类、继承、接口与常用集合；
- 能够在 IDE 中创建工程并选择 SDK。

## 核对游戏运行时

查看游戏安装目录中的 `jre/release`。当前基线的运行时为 Java17，MOD 工程也统一以 17 作为目标。高于这个目标的 class 在当前游戏中加载时，会报告 UnsupportedClassVersionError。

**运行游戏的 JRE 与编译源码的 JDK 分别使用。** JRE 提供运行环境，JDK 还包含 javac、jar 等开发工具。为工程安装 JDK17，在 IDE 中将 Project SDK 与语言级别设为 17。

本机有多个 JDK 时，IDE 选项、终端 java／javac 以及 Gradle 工具链要分别核对。后面的 toolchain 选择编译器，release 固定产物目标；Gradle 进程本身还需采用其版本支持的运行 JDK。

## 把游戏类加入编译依赖

源码引用的 `com.fs.starfarer.api` 类由游戏提供。编译器需要先找到它们，因此把 `starsector-core/*.jar` 加入工程 classpath。

classpath 决定编译时从哪些目录或 JAR 寻找类。引用游戏 API 时使用游戏核心 JAR；IDE 附加 `starfarer.api.zip` 可以查看公开接口与注释，源码附件用于阅读，编译仍使用 JAR。

**本工程打包自己的类，游戏类由游戏运行环境提供。** 后面在 Gradle 中使用 compileOnly 表达这个依赖关系，避免把核心 JAR 合并进 MOD 产物。普通 Java 插件的 jar 任务打包工程输出，自定义合并任务需要单独核对输入。

当前先确认游戏目录可读、核心 JAR 齐备和 JDK 可用，具体依赖声明在 [C6-S3] 写入。

## 验证方案

1. 查看游戏 jre/release，核对 Java17 运行时。
2. 在 IDE 选择 JDK17 与对应语言级别。
3. 查看终端 java 和 javac 版本，确认使用路径与预期一致。
4. 将核心目录 JAR 作为工程编译依赖，附加公开 API 源码供跳转查看。
5. 查看一处已有 API 引用，确认 IDE 能够定位其类型。

预期结果：

- 游戏使用当前 Java17 运行时，工程语言级别与目标按 17 设置。
- IDE 可以解析已有游戏类，核心 JAR 进入编译 classpath。
- API 源码可用于查看方法与注释。
- MOD 产物职责为自己的类，游戏依赖按运行环境提供。

## 故障分析

- **加载时报 UnsupportedClassVersionError**：产物目标高于游戏支持版本。
    - **解决方案**：将编译目标 release 设为 17，使用对应工具链重新构建。

- **IDE 或编译器找不到 API 类**：核心 JAR 未进入 classpath，或路径指向不同目录。
    - **解决方案**：核对 starsector-core 路径和编译依赖，刷新工程。

- **终端工具版本与 IDE 不同**：PATH、SDK 和 Gradle 工具链各自选择了不同安装。
    - **解决方案**：按工具的实际路径核对，编译固定为 JDK17。

- **JAR 包含游戏类**：自定义打包过程合并了核心依赖。
    - **解决方案**：使用工程输出作为产物输入，游戏 JAR 采用 compileOnly。

## 总结

工程化先确定编译环境与依赖。游戏运行时决定字节码目标，JDK 提供开发工具，classpath 让编译器解析游戏类；源码附件则服务阅读和 IDE 定位。

下一节整理源码路径，再把这些约定写进构建文件。后续代码按同一套目标编译，数据注册继续引用原来的完整类名。

## 建议参考项

- `Starsector/jre/release`：游戏运行时版本。
- `Starsector/starsector-core/*.jar`：核心编译依赖。
- `Starsector/starsector-core/starfarer.api.zip`：公开 API 源码附件。
