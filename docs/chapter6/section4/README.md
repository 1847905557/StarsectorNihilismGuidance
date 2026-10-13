# 构建 JAR 并声明产物

工程已经能编译源码，本节执行 jar 任务生成产物，再在 mod_info.json 中填写加载路径。最后检查 JAR 内部的 class 条目，并进入游戏核对已有入口和效果，确认编辑的源码确实进入当前使用的产物。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 生成 jars/GearIndustry.jar；
- 检查产物中的包路径与 class；
- 在 MOD 信息中声明 JAR；
- 根据构建、加载和效果定位产物问题。

## 前置知识

- [C6-S2] 的 src 源码布局；
- [C6-S3] 的 Gradle 配置与编译任务；
- [C1-S1] 的 MOD 信息；
- [C3-S3] 的入口声明；
- [C2-S1] 的加速器参数测试。

## 构建并找到产物

在 MOD 根目录执行：

```text
gradle jar
```

Java 插件先处理源码编译，再把本工程输出打包。当前配置将产物保存到 `jars/GearIndustry.jar`。

**构建成功之后，继续检查实际文件。** 编者建议每次排查代码未更新时，先确认当前修改的工程、产物路径和加载路径；类能编译与游戏正在使用它，是两个需要分别核对的位置。

## 查看 JAR 条目

使用 JDK 提供的 jar 工具：

```text
jar tf jars/GearIndustry.jar
```

输出中应包含当前源码的类路径，例如：

```text
data/scripts/gearin/GearInModPlugin.class
data/scripts/gearin/campaign/GearInSystemGenerator.class
data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.class
data/missions/GearIn_test/MissionDefinition.class
```

包名转为目录，类名对应 class。核对已迁入的角色类齐备，产物只包含当前工程的输出。jar 命令未进入终端路径时，从安装的 JDK bin 目录执行同一工具。

## 在 MOD 信息中声明加载路径

打开既有 mod_info.json，加入或更新 jars 数组。下面为局部字段：

```json
"jars": ["jars/GearIndustry.jar"]
```

按相邻字段补齐逗号，保留原有 id、name、作者、版本和入口。`modPlugin` 继续使用 `data.scripts.gearin.GearInModPlugin`，系统和其他数据也保留完整类名。

**jars 路径相对于 MOD 根目录**，使用斜杠填写实际产物位置。构建输出与加载声明对应，游戏启动才能找到包含这些类的 JAR。

## 修改后的工作顺序

以后修改 src 中的 Java 代码，保存后重新执行 gradle jar，再重启游戏。数据文件继续按自身加载时机处理。

本节可以沿用加速器的速度参数做一次明显变化，比较构建前后的结果。这样从源码、class 到实际效果都能找到对应证据。

## 验证方案

1. 在工程根目录执行 gradle jar，检查 BUILD SUCCESSFUL 与产物文件。
2. 用 jar tf 查看迁入的入口、生成器、系统和战役类。
3. 核对 mod_info.json 的 jars 与 modPlugin 值。
4. 重启游戏，进入测试战役或生涯，检查入口与既有效果。
5. 修改加速器参数，重新构建、重启并比较速度，再核对本次日志。

预期结果：

- JAR 保存到 jars/GearIndustry.jar，class 条目按包路径组织。
- MOD 按声明加载当前产物，已有完整类名可解析。
- 战役、系统与船插继续使用当前代码。
- 参数变化在重新构建与加载后进入实际效果。

## 故障分析

- **游戏找不到 JAR**：声明路径与实际文件位置不同。
    - **解决方案**：核对 MOD 根目录下的 jars/GearIndustry.jar 及加载值。

- **产物缺少某个 class**：源码目录或 sourceSets 配置遗漏该文件。
    - **解决方案**：核对 src 下的 package 路径，再编译并查看 JAR 条目。

- **加载时报 UnsupportedClassVersionError**：产物目标高于游戏运行时。
    - **解决方案**：按第六章配置 JDK 工具链和 release 为 17，重新生成产物。

- **代码修改后效果保持原样**：当前游戏使用了其他产物，或修改后尚未构建和重启。
    - **解决方案**：确认工程目录、JAR 位置与加载路径，再完整执行保存、构建和启动。

- **终端找不到 jar 命令**：JDK 工具目录没有进入 PATH。
    - **解决方案**：使用当前 JDK 的 bin 目录执行 jar 工具。

## 总结

Gradle 生成工程产物，JAR 保存编译后的类，mod_info.json 声明游戏读取的文件。完整类名连接代码与数据，包路径则可以在产物内部直接核对。

从这里开始，后续 Java 示例统一放在 src 中，通过当前工程构建。第七章接着讲全局服务和入口生命周期，工程化配置继续沿用本章成果。

## 建议参考项

- `mod_info.json`：jars 与 modPlugin 声明。
- `build.gradle`：jar 输出与 Java 编译配置。
- `Starsector/starsector-core/starsector.log`：类加载和运行结果。
