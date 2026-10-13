# 迁移代码文件到 src

本节将现有 Java 源码放入 MOD 工程的 src 目录，游戏直接读取的数据与资源继续留在原位置。包名与完整类名保持一致，后面的 Gradle 配置编译 src 中的文件，注册数据则继续引用同一组类。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

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
- [C6-S1] 的 JDK17 与编译依赖；
- Java 包名、类名与目录结构。

## 以 src 为编译输入

在 MOD 根目录建立 src，把 Java 文件连同对应包目录迁入。数据目录继续保存 JSON、CSV、文本与图标，例如：

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

**源码目录的根与 Java 包名是不同层次。** src 作为编译输入，内部仍按 package 组织，因此 data.scripts.gearin 对应 src/data/scripts/gearin。

## 整理脚本类

将 `data/scripts/gearin/<domain>/ClassName.java` 移到 `src/data/scripts/gearin/<domain>/ClassName.java`。包名、类声明和注册字段保留。

例如突击填弹器改为：

```text
src/data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java
```

其 package 仍为 data.scripts.gearin.shipsystems，系统定义的 statsScript 仍为 data.scripts.gearin.shipsystems.GearInAssaultFeedSystem。入口与生成器采用相同的路径整理。

**迁移改变编译输入的位置，运行时仍按完整类名找对象。** 后续源码进入 JAR 后，注册关系继续成立。

## 整理战役定义类

将 `data/missions/GearIn_test/MissionDefinition.java` 移到 `src/data/missions/GearIn_test/MissionDefinition.java`，package 仍为 data.missions.GearIn_test。

descriptor.json、mission_text.txt、icon.jpg 与 mission_list.csv 继续放在 data/missions 下。游戏按战役 ID 读取这些文件，定义类则在产物中提供同一完整名字。

这也是后续检查 JAR 条目的依据：包名转为内部目录，类名成为 class 文件名。

## 核对唯一源码与引用

迁移后按完整类名搜索，每个类只保留当前 src 中的一份源码。尤其是 MOD 入口、系统效果和战役定义，复制整理后容易出现同名文件。

编者建议先按角色逐类迁移，再对照注册值核对。改包名、改 ID 和改构建方式一起进行时，错误来源会更多；本节保持已经使用过的名字，只整理目录。

## 验证方案

1. 列出 src/data/scripts/gearin 与 src/data/missions 下的 Java 文件。
2. 对照 package、目录、文件名和类声明。
3. 搜索每个完整类名，确认源码位置唯一。
4. 核对系统、船插、插件和战役数据仍引用当前类名。
5. 查看 data 目录中的 JSON、CSV、文本和图片保持原路径。

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

src 提供编译输入，data 继续提供游戏运行数据。脚本类和战役类按已有 package 组织，完整名字保持原值，注册字段也沿用原来的引用。

目录整理完成后，下一节让 Gradle 读取 src、解析核心依赖并设置 JAR 输出。源码、构建输入和运行引用各有对应位置，后续新增类也按这套关系保存。

## 建议参考项

- `Starsector/starsector-core/starfarer.api.zip`：包名、类型与公开方法的对照来源。
- `mod_info.json`：入口类和后续 JAR 声明。
