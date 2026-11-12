# 迁移代码文件到 src

准备好编译环境后，需要为编译器指定源码。本节将已有 Java 文件迁入 MOD 根目录的 `src/`，保留原来的包名和注册值。战役目录中还混有描述与图片，我们也将借此区分哪些文件交给编译器，哪些继续由游戏直接读取。

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

## 学习目标

完成后，您将能够：

- 区分源码输入与游戏数据；
- 按包路径整理脚本和战役定义类；
- 保留已有完整类名和数据注册；
- 核对每个类的唯一源码位置。

## 前置知识

- [C1-S1] 的 MOD 目录；
- [C2-S3] 的系统源码和 [C3-S4] 的入口、生成器；
- [C4-S1] 的 MissionDefinition 目录；
- [C6-S1] 的 JDK 17 与编译依赖；
- Java 包名、类名与目录结构。

## 建立源码目录

在 MOD 根目录建立 `src/`。后面的 Gradle 配置将从这里收集 Java 文件，因此迁移时要连同包目录一起保留。游戏直接读取的 JSON、CSV、文本和图片继续留在原位置。整理后的目录例如：

```text
GearIndustry/
├── data/
│   ├── config/settings.json
│   └── missions/GearIn_test/
│       ├── descriptor.json
│       ├── mission_text.txt
│       └── icon.jpg
└── src/
    └── data/
        ├── scripts/gearin/
        └── missions/GearIn_test/MissionDefinition.java
```

这里容易产生一个误解：文件多了一层 `src/`，包名是否也要增加 `src`？

**包目录从源码根目录开始计算。** 将 `src/` 指定为根目录后，`data.scripts.gearin` 仍对应它内部的 `data/scripts/gearin/`。完整文件位置因此是 `src/data/scripts/gearin/`，包名保留原值。

## 迁移系统、入口与生成器

先将 `data/scripts/gearin/<domain>/ClassName.java` 移到 `src/data/scripts/gearin/<domain>/ClassName.java`。文件中的 `package` 与类声明保持原样，数据文件引用的完整类名也继续使用。

以突击填弹器为例，迁移后的文件位置是：

```text
src/data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java
```

它的包声明仍为 `data.scripts.gearin.shipsystems`，系统定义中的 `statsScript` 仍为 `data.scripts.gearin.shipsystems.GearInAssaultFeedSystem`。后续编译得到同名 class，游戏再按这个名字加载系统实现。

MOD 入口与星系生成器也按各自包路径迁入。**文件位置增加了源码根目录，运行时身份保持不变。** 因此这次迁移无需重新命名系统、船插或 MOD 入口。

## 分开战役代码与战役数据

战役的 `MissionDefinition.java` 原本和描述、图标放在同一个目录。将它移到 `src/data/missions/GearIn_test/MissionDefinition.java`，包名保留 `data.missions.GearIn_test`。

`descriptor.json`、`mission_text.txt`、`icon.jpg` 与 `mission_list.csv` 仍然放在 `data/missions/` 下。游戏按战役 ID 读取这些文件，定义类则从编译产物中加载。若把整套战役文件一起迁入 `src/`，Java 文件的位置虽然正确，游戏却会失去原路径下的描述和图标。

后面检查 JAR 时也沿用这个对应关系：包名转成内部目录，类名成为 class 文件名。`src/` 只参与选择编译输入，不进入 class 的包路径。

## 收束源码与注册关系

迁移完成后，按完整类名搜索 MOD 目录。每个类保留 `src/` 中的一份源码，尤其要核对 MOD 入口、系统效果与战役定义。此前复制样本时，副本是工作的起点；现在若将同名副本也作为编译输入，就会得到重复类错误。

随后对照数据注册：系统的 `statsScript`、船插的脚本字段、MOD 的 `modPlugin` 与战役定义，都应继续指向原来的完整类名。至此，编译器将从新位置取得源码，游戏仍按既有名字取得实现。

## 验证方案

1. 列出 src/data/scripts/gearin 与 src/data/missions 下的 Java 文件。
2. 对照 package、目录、文件名和类声明。
3. 搜索每个完整类名，确认源码位置唯一。
4. 核对系统、船插、插件和战役数据仍引用当前类名。
5. 确认 data 目录中的 JSON、CSV、文本和图片保持原路径。

预期结果：

- Java 源码位于 src 内部的包路径。
- 数据和资源继续供游戏从原目录读取。
- 完整类名、对象 ID 与数据注册保持一致。
- 每个类只有一份用于编译的源码。

## 故障分析

- **后续 compileJava 没有发现源码**：文件未迁入 src 或源码集合指向其他目录。
    - **解决方案**：核对 Java 文件位置，并在构建配置中指定 src。

- **完整类名找不到**：包名与注册值不同，或源码未进入构建产物。
    - **解决方案**：对照 package 和类名，完成下一节编译后核对 class 条目。

- **编译出现重复类**：相同 package 和类名有多份源码。
    - **解决方案**：按完整类名整理为 src 中的唯一文件。

- **战役描述或图标缺失**：运行数据随源码一起被移走。
    - **解决方案**：把战役数据与图标保留在 data/missions 的原目录。

## 总结

源码已经集中到 `src/`，包目录保留原来的层级，游戏数据和资源则留在原路径。改变的是编译器取得文件的位置，完整类名与数据注册继续成立。

下一节将这个源码目录写入 Gradle 配置。之后新增 Java 类也保存到相应包目录，构建任务就能统一收集它们。

## 建议参考项

- `Starsector/starsector-core/starfarer.api.zip`：包名、类型与公开方法的对照来源。
- `mod_info.json`：入口类和后续 JAR 声明。
