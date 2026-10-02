# 构建 JAR 并声明产物

本节执行 Gradle JAR 任务，把 `jars/GearIndustry.jar` 写入 `mod_info.json`，并检查产物中的 class 条目。

*本节预期阅读时间：约 8 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

完成后，您将能够：

- 生成 `GearIndustry.jar`；
- 在 `mod_info.json` 声明相对路径；
- 用 `jar tf` 检查 JAR 内容并定位构建错误。

## 前置知识

- [C6-S2] 的 `src/` 源码布局；
- [C6-S3] 的 Gradle 配置；
- [C1-S1] 的 `mod_info.json`。

## 生成 JAR

在 MOD 工程目录执行：

```text
gradle jar
```

Gradle 会把 `src/` 中的 Java 文件编译后写入 `jars/GearIndustry.jar`。

## 声明相对路径

在 `mod_info.json` 的 `jars` 数组中写入相对于 MOD 根目录的路径：

```json
{
  "id": "gear_in",
  "name": "齿轮工业",
  "jars": ["jars/GearIndustry.jar"]
}
```

路径必须使用 `/`，不能写绝对路径或构建目录路径。

## 检查产物条目

执行：

```text
jar tf jars/GearIndustry.jar
```

输出必须包含迁移后类的包路径，例如 `data/scripts/gearin/GearInModPlugin.class`。JAR 中不应出现游戏 API 的复制类。

## 验证方案

前置数据：[C6-S1] 至 [C6-S3] 的工程配置与源码迁移已完成。

1. 执行 `gradle jar`。
2. 确认 `jars/GearIndustry.jar` 存在。
3. 检查 `mod_info.json` 的相对路径。
4. 执行 `jar tf` 并确认 class 条目。
5. 启动游戏并在日志中确认 JAR 加载记录。

预期结果：

- 构建成功；
- JAR 位于 `jars/`；
- manifest 路径有效；
- class 条目与包名一致。

## 故障分析

- **找不到 JAR**：`jars` 路径不是相对 MOD 根目录的 `jars/GearIndustry.jar`。
    - **解决方案**：把 `jars` 改为 `jars/GearIndustry.jar`，然后重新启动游戏。

- **JAR 中缺少 class**：源码未位于 `src/` 或 Gradle source set 配置错误。
    - **解决方案**：检查 [C6-S2] 和 [C6-S3]。

- **出现 `UnsupportedClassVersionError`**：构建使用了高于 Java 17 的目标。
    - **解决方案**：回到 [C6-S1] 和 [C6-S3] 固定版本。

## 总结

`gradle jar` 生成 `jars/GearIndustry.jar`，`mod_info.json` 通过相对路径声明它，`jar tf` 用于确认包路径和 class 条目。完成本节后，进入 Chapter 7 的全局服务与入口注册。

## 建议参考项

- `mod_info.json`：`jars` 数组。
