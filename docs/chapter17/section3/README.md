# 读取 MOD 内 CSV

同一张 CSV 可以由本 MOD 独立读取，也可以允许其他 MOD 增补或覆盖。本节用工坊服务表比较这些入口：先取得自己的原始行，再加入一份外部表，确认行键怎样决定合并结果。选择读取方法时，我们也就同时决定了谁有权提供这份数据。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~35 分钟。_

## 学习目标

完成后，您将能够：

- 使用 `loadCSV` 读取列名映射的对象数组；
- 使用 `getMergedSpreadsheetData` 读取跨 MOD 合并表；
- 使用 `getMergedSpreadsheetDataForMod` 读取指定 master 文件并允许外部 MOD 覆盖行；
- 区分单文件读取、合并读取和 JSON 合并；
- 根据重复键、路径和 master 归属定位表格读取故障。

## 前置知识

- [C1-S7] 的 CSV 表头、空列与文本转义规则；
- [C3-S5] 的描述表键与合并语义；
- [C17-S1] 的 MOD 相对路径和 `modId` 限定读取；
- [C17-S2] 的 JSON 单文件读取。

## 创建服务表并读取当前 MOD

在 GearIn MOD 内保存 `data/config/gear_in/services.csv`，本例的 MOD 目录名为 `GearIndustry`，manifest 中的 ID 为 `gear_in`：

```csv
id,label,interval
GearIn_service,工坊服务,10
```

在 `GearInModPlugin.onApplicationLoad(...)` 添加 `JSONArray`、`JSONObject` 的 import，追加局部读取并记录：

```java
JSONArray ownRows = Global.getSettings().loadCSV("data/config/gear_in/services.csv", "gear_in");
for (int index = 0; index < ownRows.length(); index++) {
    JSONObject row = ownRows.getJSONObject(index);
    Global.getLogger(GearInModPlugin.class).info("GearIn CSV: id=" + row.getString("id")
            + " label=" + row.getString("label") + " interval=" + row.getInt("interval"));
}
```

`loadCSV(path, modId)` 使用 manifest 的 MOD ID，`gear_in` 因而取得自己的原始表。每行转换为 `JSONObject`，表头成为键；`getString(...)` 与 `getInt(...)` 按列名读取，日志记录 `工坊服务` 和 10。

## 按行键合并同路径的表

其他 MOD 也在同一路径提供文件时，读取方法决定怎样处理它们。以下局部片段比较当前资源空间中的单文件、普通合并与指定 master 的合并：

```java
JSONArray rows = Global.getSettings().loadCSV("data/config/gear_in/services.csv");
JSONArray merged = Global.getSettings().getMergedSpreadsheetData("id", "data/config/gear_in/services.csv");
JSONArray master = Global.getSettings().getMergedSpreadsheetDataForMod(
        "id", "data/config/gear_in/services.csv", "GearIndustry");
```

`loadCSV(path)` 选择一份文件。`getMergedSpreadsheetData(...)` 读取同路径的多份表，按 `id` 合并：同一文件内部重复键会抛出异常，跨文件同键保留先纳入的行。

`getMergedSpreadsheetDataForMod(...)` 先取得基础表，其他来源再按键替换基础行。这里的第三个参数通过资源来源路径选择 master，因此使用目录名 `GearIndustry`。**它与 `loadCSV(path, modId)` 的 MOD ID 参数具有不同的选择含义。**

::: warning master 名称匹配资源目录
本例目录名为 `GearIndustry`，MOD ID 为 `gear_in`。指定 master 时传入 `GearIndustry`，并保持目录名称能唯一识别基础来源；该参数按来源路径中是否包含给定文本选择文件。
:::

多个非 master 提供同键行时，接口不保证唯一覆盖者。**采用 master 合并，就把基础行的替换权交给了其他来源。** 对外开放服务配置时，应说明哪些键允许覆盖；自有服务需要固定取值时，使用 `loadCSV(path, modId)` 选择自己的文件。

### JSON 使用另一种合并单位

`getMergedJSON(...)` 递归合并对象键，标量由后加载来源覆盖，数组按引擎规则追加或整体替换。每份文件提供自己的分类或键，合并单位就转为对象结构。需要读取单一 MOD 的 JSON 时，沿用 [C17-S2] 的 `loadJSON(path, modId)`。

### 选择入口速查表

| 任务                       | 入口                             | 结果                       |
| -------------------------- | -------------------------------- | -------------------------- |
| 读取自己的表               | `loadCSV(path, modId)`           | 指定 MOD 的原始对象数组    |
| 读取所有来源的同路径表     | `getMergedSpreadsheetData`       | 按键合并后的对象数组       |
| 读取指定 master 并允许覆盖 | `getMergedSpreadsheetDataForMod` | master 行可被其他 MOD 替换 |
| 读取合并 JSON              | `getMergedJSON`                  | 递归合并对象               |

## 用外部表观察覆盖与新增

路径确定参与读取的文件，键列确定每一行的身份。在第二份测试 MOD 的 `data/config/gear_in/services.csv` 写入以下记录，既替换 `GearIn_service`，也提供自己的新键：

```csv
id,label,interval
GearIn_service,外部覆盖服务,20
GearIn_external_service,外部新增服务,15
```

这份输入同时提供同键和新键。向应用加载方法追加以下局部片段，把两种合并结果直接写入日志：

```java
JSONArray mergedRows = Global.getSettings().getMergedSpreadsheetData(
        "id", "data/config/gear_in/services.csv");
JSONArray masterRows = Global.getSettings().getMergedSpreadsheetDataForMod(
        "id", "data/config/gear_in/services.csv", "GearIndustry");
Global.getLogger(GearInModPlugin.class).info("GearIn merged CSV: " + mergedRows);
Global.getLogger(GearInModPlugin.class).info("GearIn master CSV: " + masterRows);
```

master 模式以 `GearIndustry` 中的表为基础，外部同键行将它替换为 `外部覆盖服务`、20。普通合并按来源先后保留同键行；新增的 `GearIn_external_service` 在两种结果中都能取得。

这次只有一个外部覆盖者，结果可以明确对应到它提供的记录。继续开放公共表时，自有目录保留 MOD 前缀，并说明键列与覆盖入口；扩展 MOD 也要区分自己新增的键和有意替换的键。

## 验证方案

前置数据：GearIn MOD 目录名为 `GearIndustry`，另有一份提供上述覆盖与新增行的测试 MOD。

1. 只启用 `gear_in`，保存服务表和应用调用，构建重启。观察 `GearIn CSV` 中 `工坊服务` 与 10。
2. 启用提供上述两行的测试 MOD，重启并搜索 `GearIn merged CSV`，观察同键行的 `label` 与 `interval`。
3. 比较 `GearIn master CSV`，确认 `GearIn_service` 为 `外部覆盖服务`、20。
4. 在两种合并日志中查找 `GearIn_external_service`，确认 `外部新增服务`、15。

预期结果：

- 单文件读取返回 `GearIn_service` 的原始字段；
- 普通合并包含所有来源的非冲突行；
- master 合并按同键替换 master 行；
- 键不重复、路径正确时，读取日志没有表格解析异常。

## 故障分析

- **合并加载报告 `Duplicate key`**：同一文件内的键列出现重复值。
    - **解决方案**：让每行键唯一，再加载。

- **合并结果缺少目标行**：路径、键列或来源 MOD 与调用参数不一致。
    - **解决方案**：核对同路径文件与 `id` 列，单来源读取核对 `gear_in`，master 读取核对资源目录名 `GearIndustry`。

- **普通合并取得其他来源的同键行**：同键行按先纳入者保留，读取结果受来源顺序影响。
    - **解决方案**：改用唯一键。

- **结果只包含当前 MOD 的行**：代码使用了 `loadCSV(path, modId)` 单来源入口。
    - **解决方案**：需要跨来源时改用合并入口。

- **CSV 字段整体错位**：含英文逗号的字段没有按 CSV 规则加引号。
    - **解决方案**：修正转义并重新读取。

## 总结

服务表从列名生成 JSONObject 行，来源限定读取原始数据，普通合并按当前来源规则处理，master 合并则主动开放同键替换。新键继续进入合并结果，JSON 的对象键合并使用另一组方法。

决定入口之前，先说明谁拥有基础数据、谁可以覆盖。文件路径相同以后，这个选择仍要在当前 API 调用里表达。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadCSV` 单文件读取，`getMergedSpreadsheetData` 普通合并表，`getMergedSpreadsheetDataForMod` 带 master 的合并表，`getMergedJSON` 对象键递归合并。
