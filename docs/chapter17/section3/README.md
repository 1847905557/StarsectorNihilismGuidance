# 读取 MOD 内 CSV

本节处理表格和跨 MOD 数据：先读取单个 CSV，再根据数据所有权选择合并入口。关键区别是文件是否需要参与全局合并，以及重复键最终由哪一方负责。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `loadCSV` 读取列名映射的对象数组；
- 使用 `getMergedSpreadsheetData` 读取跨 MOD 合并表；
- 使用 `getMergedSpreadsheetDataForMod` 读取指定 master 文件并允许外部 MOD 覆盖行；
- 区分单文件读取、合并读取和 JSON 合并；
- 根据重复键、路径和 master 归属定位表格读取故障。

## 前置知识

- [C1-S2] 的 CSV 表头、引号和 ID 规则；
- [C3-S5] 的描述表键与合并语义；
- [C17-S1] 的 MOD 相对路径和 `modId` 限定读取；
- [C17-S2] 的 JSON 单文件读取。

## 单文件与合并入口

```java
JSONArray rows = Global.getSettings().loadCSV("data/config/gear_in/services.csv");
JSONArray merged = Global.getSettings().getMergedSpreadsheetData("id", "data/config/gear_in/services.csv");
JSONArray master = Global.getSettings().getMergedSpreadsheetDataForMod(
        "id", "data/config/gear_in/services.csv", "gear_in");
```

`loadCSV` 适合读取本 MOD 自己拥有的文件。`getMergedSpreadsheetData` 适合多个来源按同一路径合并的表；同一文件内部重复键会抛出异常，跨文件冲突按合并顺序处理。`getMergedSpreadsheetDataForMod` 把 `gear_in` 作为 master，其他 MOD 可以用同一键替换 master 行，适合提供可覆盖的基础数据。

### JSON 合并

`getMergedJSON` 递归合并对象键，标量由后加载来源覆盖，数组按引擎规则追加或整体替换。需要自己的文件只提供自己的分类或键；需要读取单一 MOD 的 JSON 时使用 `loadJSON(path, modId)`，基础见 [C17-S2]。

### 选择入口速查表

| 任务 | 入口 | 结果 |
| --- | --- | --- |
| 读取自己的表 | `loadCSV` | 一个对象数组 |
| 读取所有来源的同路径表 | `getMergedSpreadsheetData` | 按键合并后的对象数组 |
| 读取指定 master 并允许覆盖 | `getMergedSpreadsheetDataForMod` | master 行可被其他 MOD 替换 |
| 读取合并 JSON | `getMergedJSON` | 递归合并对象 |

### 最小实例

在 `data/config/gear_in/services.csv` 写入：

```csv
id,label,interval
GearIn_service,工坊服务,10
```

代码读取 `id`、`label` 和 `interval`，并把结果写入日志。第二份测试 MOD 使用同一路径和 `GearIn_service` 键，观察普通合并和 master 合并的差异。

## 数据所有权

文件路径相同不代表数据所有权相同。自己的数据路径使用 MOD 前缀；公共表格必须明确键列、覆盖者和预期合并入口。不要复制整份原版文件，只提交需要新增或覆盖的行。

### 验证方案

前置数据：`services.csv`、一个提供同键覆盖行的测试 MOD，以及三种读取调用已就绪。

1. 使用 `loadCSV` 读取 `services.csv`，观察日志中的 `GearIn_service` 行。
2. 使用 `getMergedSpreadsheetData` 读取同一路径，观察合并后的 `label` 和 `interval`。
3. 使用 `getMergedSpreadsheetDataForMod` 指定 `gear_in`，观察 master 行被覆盖后的字段值。
4. 在测试 MOD 中加入一个不重复的键，重新启动并观察该行出现在合并结果中。

预期结果：

- 单文件读取返回 `GearIn_service` 的原始字段；
- 普通合并包含所有来源的非冲突行；
- master 合并按同键替换 master 行；
- 键不重复、路径正确时，读取日志没有表格解析异常。

## 故障分析

- **合并加载报告 `Duplicate key`**：同一文件内的键列出现重复值。

    - **解决方案**：让每行键唯一，再加载。

- **合并结果缺少目标行**：路径、键列或来源 MOD 与调用参数不一致。

    - **解决方案**：逐项核对并重新合并。

- **自有值被其他 MOD 覆盖**：普通合并按 "先加载者胜" 静默丢弃后到同键行。唯一键才能防覆盖；带 MOD master 的入口反而**启用**覆盖 (master 行恰是被替换的一方)，不是修复手段。

    - **解决方案**：改用唯一键。

- **结果只包含当前 MOD 的行**：代码使用了 `loadCSV(path, modId)` 单来源入口。

    - **解决方案**：需要跨来源时改用合并入口。

- **CSV 字段整体错位**：含英文逗号的字段没有按 CSV 规则加引号。

    - **解决方案**：修正转义并重新读取。

## 总结

CSV 和 JSON 的读取入口由数据所有权决定。单文件入口读取自己的资源，合并入口处理跨 MOD 数据；带 master 的表格还需要明确谁提供基础行、谁可以覆盖。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadCSV` 单文件读取，`getMergedSpreadsheetData` 普通合并表，`getMergedSpreadsheetDataForMod` 带 master 的合并表，`getMergedJSON` 对象键递归合并。
