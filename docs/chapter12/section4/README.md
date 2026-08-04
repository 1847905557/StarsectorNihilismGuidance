# 读写 common 数据

本节在存档 `common` 中读取并保存持久状态。完整流程是先处理文件不存在、字段缺失和版本变化，再修改目标字段并明确写盘；重启后重新读取的结果决定这次保存是否成立。

*本节预期阅读时间：~28 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 定位 `<存档目录>/common/` 和 MOD 专属文件；
- 使用原版入口或 LazyLib 读取 common 文本与 JSON；
- 使用 `writeTextFileToCommon`、`writeJSONToCommon` 或 `CommonDataJSONObject.save()` 写入数据；
- 组织读—改—写流程，处理缺失字段、版本迁移和未知字段；
- 从文件内容、重启结果和日志定位 common 读写故障。

## 前置知识

- C06-S02 的生涯生命周期回调；
- C06-S03 的 `Global.getSettings()`；
- C12-S02 的 JSON 结构、字段读取和编码规则。

## common 的位置与语义

游戏存档目录由启动器决定，MOD 不应把绝对路径写入代码。相对 common 文件名由 MOD 自己命名，例如 `gear_in/state.json`；实际文件位于 `<存档目录>/common/gear_in/state.json`。

MOD 资源目录提供随 MOD 发布的只读输入，common 则保存运行期间产生的持久状态。common 文件属于当前用户数据；代码只读写自己的相对文件，不覆盖其他 MOD 的内容。

## 读取 common

原版 `SettingsAPI` 提供 common 文本和 JSON 读取入口。LazyLib `JSONUtils.loadCommonJSON` 返回 `CommonDataJSONObject`，可以使用 `get`、`has` 和 `opt` 方法读取字段。

```java
JSONObject state = Global.getSettings().readJSONFromCommon("gear_in/state.json");
int battles = state.optInt("battles", 0);
String schema = state.optString("schema", "1");
```

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json")) {
    int battles = data.optInt("battles", 0);
    String schema = data.optString("schema", "1");
}
```

文件不存在时，读取结果可能是空对象或空文本，具体取决于入口。代码把缺失文件和缺失键作为初始化状态，并在内存中补齐可运行值。

### 版本字段

每个持久化对象写入 `schema` 或 `version` 字段。读取时先判断版本，再执行迁移；未知字段保留在对象中，避免新版本读取旧文件后丢失用户数据。

### 读取实例

`gear_in/state.json` 保存 `schema`、`battles` 和 `lastReport`。在 `onGameLoad` 中读取文件：不存在时使用 `schema=1`、`battles=0`；存在时保留已有值，并把缺少的字段补齐到内存对象。

### 验证方案

前置数据：可加载 MOD、`onGameLoad` 读取片段和一个 `gear_in/state.json` 测试文件。

1. 启动游戏并加载一个已有存档，观察读取日志中的 common 文件名。
2. 读取 `state.json` 的 `schema` 和 `battles`，观察代码使用的值。
3. 删除 `battles` 键后重新加载存档，观察内存中补入的缺省值。
4. 写入较旧的 `schema` 后重新加载，观察迁移分支和最终使用的版本。

预期结果：

- 读取路径指向当前存档的 `common` 目录；
- 已有字段按文件值读取；
- 缺失文件或字段不会让读取流程失去可运行状态；
- 版本迁移后对象包含当前代码要求的字段；
- 未知字段仍保留在读取对象中。

## 写入 common

原版写入入口分别处理文本与 JSON：

```java
Global.getSettings().writeTextFileToCommon("gear_in/state.txt", text);
Global.getSettings().writeJSONToCommon("gear_in/state.json", state, true);
```

`writeJSONToCommon` 的 `onlyIfChanged` 可以避免内容没有变化时重复写入。文本写入有单次大小边界，写入过程先写临时文件，再替换目标文件；调用侧仍需记录异常和文件名。

LazyLib 的 `CommonDataJSONObject` 直接封装 JSON：

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json")) {
    data.put("schema", 1);
    data.put("battles", data.optInt("battles", 0) + 1);
    data.save();
}
```

`put` 只改变内存对象；`close()` 不代替 `save()`。写入后重新加载文件，才能确认磁盘内容已经更新。

### 读—改—写与迁移

写入前先读取当前对象，再修改需要修改的键。保留未知字段，缺省值只在字段不存在时补入。版本迁移完成后写回当前版本，避免每次启动重复迁移。

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json")) {
    int schema = data.optInt("schema", 1);
    if (schema < 2) {
        data.put("reports", data.optInt("reports", 0));
        data.put("schema", 2);
    }
    data.put("battles", data.optInt("battles", 0) + 1);
    data.save();
}
```

### 写入边界

写入失败时保留原文件，日志记录目标和异常。用户设置由专门的设置库管理时，MOD 不直接绕过其保存流程。

### 验证方案

前置数据：本节的读取对象、`gear_in/state.json` 和一个在 `onGameLoad` 或测试战斗中执行的写入片段。

1. 读取当前 `state.json`，记录 `battles` 的初始值。
2. 将 `battles` 加 1，并调用 `save()` 或 `writeJSONToCommon`。
3. 退出当前场景并重新加载存档，观察读取到的 `battles`。
4. 写入一个旧版本对象，重新加载并观察迁移后的 `schema` 与新增字段。
5. 重复写入相同内容，观察 `onlyIfChanged` 对文件更新时间的影响。

预期结果：

- common 文件位于当前存档的 `common` 目录；
- 写入后重新加载能读到递增后的 `battles`；
- 未知字段保持不变；
- 旧版本文件迁移到当前 `schema`；
- 内容未变化时不会产生不必要的 JSON 写入；
- 写入失败时原文件仍可读取，并有明确错误日志。

清理：删除测试存档中的 `gear_in/state.json` 或恢复测试前的文件内容。

## 故障分析

- 读取到空值：文件不存在、文件名不一致或使用了另一个存档。先观察当前存档和日志中的相对文件名。
- 缺键抛异常：直接使用 `get` 读取了可缺失字段。初始化字段使用 `opt` 或先用 `has` 判断。
- 旧存档行为异常：缺少版本字段或迁移分支未覆盖旧版本。先读取版本，再执行明确迁移。
- 数据被新版本清掉：重建对象时只写已知字段。迁移时保留未知字段。
- 重启后数值归零：只调用了 `put`，没有调用 common 写入入口。把写入集中到 `save()` 或 `writeJSONToCommon`。
- 文件内容被缺省值覆盖：每次读取都把缺省对象整体写回。缺省值只填充缺少的键。
- 用户设置被改回：绕过设置库直接写它管理的 common 文件。让设置库负责定义、读取和保存。
- 写入后文件损坏：没有保留原文件或没有记录失败。使用原子写入入口，并在异常时保留旧文件。
- 大文件写入失败：超过文本写入边界。拆分数据或改用 JSON 结构并减少单次写入内容。
- 读到了 MOD 目录文件：把资源路径和 common 相对文件名混用。两者入口和生命周期不同。

## 总结

common 是存档持久数据空间。读取时处理缺失字段和版本迁移，写入时保留未知字段并调用真正的写盘入口；重启后再次读取的结果决定持久化是否成立。

## 建议参考项

- `SettingsAPI.readJSONFromCommon`：原版 common JSON 读取入口；
- `SettingsAPI.writeTextFileToCommon`：common 文本写入；
- `SettingsAPI.writeJSONToCommon`：common JSON 写入；
- `JSONUtils.loadCommonJSON`：LazyLib common JSON 读取入口；
- `CommonDataJSONObject.save()`：LazyLib JSON 写盘入口；
- `<存档目录>/common/`：存档持久化文件位置。
