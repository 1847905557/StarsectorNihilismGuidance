# 迁移代码文件到 src

本节把从此前维护的 Java 源码放入 MOD 的 `src/`，同时保留 `data/` 中的 JSON、CSV、文本和图片。包名和运行时完整类名保持不变。

*本节预期阅读时间：约 8 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

- 区分 `data/` 运行数据和 `src/` Java 源码；
- 将脚本类和战役定义类迁移到对应的 `src` 路径；
- 检查包名、类名和路径的一致性。

## 前置知识

- [C1-S1] 的 MOD 目录结构；
- [C4-S1] 的战役目录和 `MissionDefinition`；
- [C6-S1] 的 Java 17 与编译依赖；
- Java 包名与目录结构的对应规则。

## 区分两个目录

在此之前，本教程引导您直接在 `data/` 下保存 Java 源码。但从现在开始，您已经是一个成熟的创作者了，Java 文件必须放入 `src/`，而 `data/` 只保存游戏读取的数据和资源。

```text
GearIndustry/
├── data/
│   ├── config/settings.json
│   └── missions/GearIn_test/
│       ├── descriptor.json
│       ├── mission_text.txt
│       └── icon.png
└── src/
    └── data/
        ├── scripts/gearin/
        └── missions/GearIn_test/MissionDefinition.java
```

## 举例迁移脚本类

把 `data/scripts/gearin/<domain>/ClassName.java` 移到 `src/data/scripts/gearin/<domain>/ClassName.java`。文件中的 `package data.scripts.gearin.<domain>;`、类名和注册值不变；迁移后的源码由后续构建配置统一编译。

## 举例迁移战役定义类

把 `data/missions/<mission_id>/MissionDefinition.java` 移到 `src/data/missions/<mission_id>/MissionDefinition.java`。`descriptor.json`、`mission_text.txt`、图标和 `mission_list.csv` 继续放在 `data/missions/`；类仍使用 `package data.missions.<mission_id>;`。

## 清理重复文件

迁移后，MOD 根目录下不得保留同名 Java 源文件。使用全文搜索确认每个完整类名只有一个 `.java` 文件。

## 验证方案

1. 列出 `src/data/scripts/` 和 `src/data/missions/`；
2. 对照 package、目录和类名；
3. 确认 `data/` 只保留运行数据与资源；
4. 搜索重复的 `.java` 文件名。

预期结果：Java 源码全部位于 `src/`，运行数据仍位于 `data/`，注册值和完整类名无需改变。

## 故障分析

- **后续构建找不到源码**：文件仍在 `data/` 或构建配置没有把 `src/` 作为源码目录。

    - **解决方案**：确认 Java 文件已经迁移到 `src/`，再在构建配置中声明 `src/`。

- **日志出现 `Problem loading class`**：package、类名或目录不一致。

    - **解决方案**：按完整类名逐字修正。

- **同一类出现两份**：迁移后旧文件没有删除。

    - **解决方案**：只保留 `src/` 下的源码。

## 总结

C6 之后 Java 源码统一放在 `src/`，运行数据继续放在 `data/`。迁移只改变文件位置，不改变包名、类名、ID 或数据文件路径。

## 建议参考项

- `Starsector/starsector-core/starfarer.api.zip`：原版 API 归档，迁移类的包名与完整类名的对照来源。
