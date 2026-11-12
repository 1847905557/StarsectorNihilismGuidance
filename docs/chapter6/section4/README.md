# 构建 JAR 并声明产物

本节将编译结果打包为 JAR，并在 `mod_info.json` 中声明加载路径。到这里，源码的迁移和构建才与游戏重新接通。我们将通过已有入口和加速器效果确认，游戏使用的确实是这份工程生成的代码。

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

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

## 生成 MOD 的 JAR

在 MOD 根目录执行：

```text
gradle jar
```

`jar` 任务先执行所需的源码编译，再打包本工程输出。上一节已经指定文件名和目录，任务完成后，MOD 根目录下应当出现 `jars/GearIndustry.jar`。

`BUILD SUCCESSFUL` 表示构建任务完成。游戏还需要通过加载声明取得这个文件，数据中的完整类名也需要在产物中存在，后面的检查就沿这两处继续。

## 核对产物中的类

JAR 内部按包路径保存编译后的类。使用 JDK 提供的 `jar` 工具列出内容：

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

这些路径与源码的 `package` 和类名对应：包名转为内部目录，类名成为 class 文件名。核对迁入的入口、生成器、系统和战役定义都已出现，便能确认它们进入了本次产物。

普通 `jar` 任务保存当前工程输出，游戏核心类仍由游戏提供。如果自行调整过打包输入，也在这里确认内容范围。终端无法识别 `jar` 命令时，使用已安装 JDK 的 `bin/` 目录中的同一工具。

## 在 MOD 信息中声明加载路径

打开 MOD 根目录下的 `mod_info.json`，加入或更新 `jars` 数组。下面是局部字段：

```json
"jars": ["jars/GearIndustry.jar"]
```

按相邻字段补齐逗号，保留已有 ID、名称、作者、版本与入口。**`jars` 的路径相对于 MOD 根目录。** 这里填写刚才生成的 `jars/GearIndustry.jar`，游戏启动时就会读取这份产物。

`modPlugin` 仍为 `data.scripts.gearin.GearInModPlugin`，系统和其他注册数据也继续使用原来的完整类名。源码迁入 `src/` 后，这些名字没有改变；游戏根据加载到的 class 解析它们。

## 让一次代码修改进入游戏

从这里起，编辑 `src/` 中的源码之后，执行 `gradle jar` 生成新产物，再重启游戏加载。游戏读取的是 JAR 中的 class，保存 Java 文件之后还要完成编译与打包。数据文件则继续按自身的加载时机处理。

用 [C2-S1] 已有的加速器速度参数做一次明显调整。构建后进入测试战役，比较速度变化与当前日志。如果结果仍然保持原样，依次核对编辑的工程、生成的 JAR 和 `jars` 声明，就能定位这次修改停在了哪里。

## 验证方案

1. 在工程根目录执行 gradle jar，检查 BUILD SUCCESSFUL 与产物文件。
2. 用 jar tf 核对迁入的入口、生成器、系统和战役类。
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

现有源码已经通过编译、打包和加载声明重新接入游戏。构建配置决定产物内容与位置，`mod_info.json` 决定游戏读取的 JAR，原有数据注册再通过完整类名取得实现。

后续 Java 示例都放在 `src/`，沿用本章工程构建。准备环境的工作到此完成，下一章开始处理代码在游戏中实际使用的服务与调用时机。

## 建议参考项

- `mod_info.json`：jars 与 modPlugin 声明。
- `build.gradle`：jar 输出与 Java 编译配置。
- `Starsector/starsector-core/starsector.log`：类加载和运行结果。
