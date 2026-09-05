# 确认 Java 与编译依赖

本节确认 Starsector 的 Java 17 运行时、IDE 语言级别和游戏 API 编译依赖，为后续工程化配置建立边界。

*本节预期阅读时间：约 8 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

- 确认游戏使用的 Java 17 与字节码上限；
- 在 IDE 中设置 Java 17；
- 判断游戏 API 的编译期依赖和 `compileOnly` 范围。

## 前置知识

- [C1-S1] 的 MOD 目录和启动日志；
- 理解 Java 编译、JAR 和 classpath；
- 能够在 IDE 中选择项目 SDK。

## 确认 Java 17

游戏使用安装目录中的 `jre` 启动，`jre/release` 的版本为 Java 17。因此 MOD 的 class 文件目标必须为 17 (虽说可以用版本更低的 JDK，但最好不要那样做)。使用更高版本默认目标编译时，游戏加载会报告 `UnsupportedClassVersionError`。

## 配置 IDE 和依赖

把 Project SDK 和语言级别设为 17，并将游戏公开 API 的编译面加入工程。API 源码附件只用于 IDE 跳转和注释阅读，不会进入 MOD JAR。

Gradle 只允许在编译期引用游戏核心目录中的 JAR，依赖声明必须使用 `compileOnly`，构建产物只包含 MOD 自己的类。本教程使用 `starsector-core/*.jar` 作为完整编译期 classpath。

## 验证方案

1. 确认 IDE SDK 为 Java 17；
2. 查看 `jre/release`；
3. 确认工程依赖使用 `compileOnly`。

预期结果：IDE、Gradle 和游戏运行时都以 Java 17 为目标，游戏 API 只作为编译期依赖。

## 故障分析

- **加载时报 `UnsupportedClassVersionError`**：字节码目标高于 17。

    - **解决方案**：将 toolchain 和 `--release` 设为 17。

- **编译时报 `cannot find symbol`**：游戏 API 未进入编译 classpath。

    - **解决方案**：检查核心目录路径和 `compileOnly` 声明。

- **JAR 中出现游戏 API 类**：依赖被打包进产物。

    - **解决方案**：改用 `compileOnly`，不要把核心 JAR 打包。

## 总结

Java 17 是游戏可加载的字节码上限，公开 API 只在编译期加入 classpath。

## 建议参考项

- `Starsector/jre/release`：Java 版本；
- `starsector-core/*.jar`：编译依赖。
