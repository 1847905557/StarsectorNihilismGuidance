# 读写 common 数据

本节在存档 `common` 中读取并保存持久状态。完整流程是先处理文件不存在、字段缺失和版本变化，再修改目标字段并明确写盘；重启后重新读取的结果决定这次保存是否成立。

*本节预期阅读时间：~28 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 定位并读取 common 目录中的 MOD 文本与 JSON；
- 使用原版入口或 LazyLib 写回 common 数据；
- 在读写过程中处理缺失字段、版本迁移与未知字段；
- 根据文件、重启结果和日志定位 common 数据故障。

## 前置知识

- [C7-S1] 的生涯生命周期回调；
- [C7-S2] 的 `Global.getSettings()`；
- [C17-S2] 的 JSON 结构、字段读取和编码规则。

## common 的位置与语义

游戏存档目录由启动器决定，MOD 不应把绝对路径写入代码。相对 common 文件名由 MOD 自己命名，例如 `gear_in/state.json`；实际文件位于 `<存档目录>/common/gear_in/state.json.data`。

MOD 资源目录提供随 MOD 发布的只读输入，common 则保存运行期间产生的持久状态。common 文件属于当前用户数据；代码只读写自己的相对文件，不覆盖其他 MOD 的内容。

## 读取 common

原版 `SettingsAPI` 提供 common 文本和 JSON 读取入口。LazyLib `JSONUtils.loadCommonJSON` 返回 `CommonDataJSONObject`，可以使用 `get`、`has` 和 `opt` 方法读取字段。

```java
JSONObject state = new JSONObject();
if (Global.getSettings().fileExistsInCommon("gear_in/state.json")) {
    state = Global.getSettings().readJSONFromCommon("gear_in/state.json", false);
}
int battles = state.optInt("battles", 0);
String schema = state.optString("schema", "1");
```

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json")) {
    int battles = data.optInt("battles", 0);
    String schema = data.optString("schema", "1");
}
```

文件不存在时，文本入口返回空字符串，LazyLib `loadCommonJSON` 捕获解析异常并返回空对象。原版 `readJSONFromCommon` 会抛 `JSONException`，因此直接读取前先检查文件是否存在。

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

`put` 先改变内存对象；`close()` 会调用 `save()` 完成写盘。try-with-resources 块结束时数据即保存，显式 `save()` 用于块内提前持久化；写入后重新加载文件，才能确认磁盘内容已经更新。

### 读 → 改 → 写与迁移

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

## 故障分析

- **读取 common 文件得到空值**：文件不存在、名称不一致或当前打开了另一存档。

    - **解决方案**：记录存档标识和相对文件名，修正后重新读取。

- **读取可选字段时抛出异常**：代码直接对缺失键使用 `get`。

    - **解决方案**：初始化字段改用 `opt` 或先调用 `has`。

- **旧存档载入后行为异常**：数据缺少版本字段，或迁移分支没有覆盖该版本。

    - **解决方案**：先读取版本并执行对应迁移。

- **升级后未知字段消失**：迁移时重建了只含已知字段的新对象。

    - **解决方案**：改为在原对象上补充和转换字段。

- **重启后数值恢复为零**：普通 `JSONObject` 只在内存中修改，没有传给 common 写入入口；或 `CommonDataJSONObject` 既未显式保存也未关闭。

    - **解决方案**：调用对应写入入口，或让 try-with-resources 正常关闭对象。

- **文件内容被缺省值覆盖**：载入时把完整缺省对象写回了已有文件。

    - **解决方案**：只为缺失键填充默认值。

- **用户设置被自动改回**：代码直接写入了设置库管理的 common 文件。

    - **解决方案**：改由设置库读取和保存。

- **写入后 common 文件无法解析**：写入过程覆盖了原文件且失败时没有保留副本。

    - **解决方案**：使用原子写入入口并保留旧文件。

- **大文件写入失败**：单次文本超过写入入口可处理的边界。

    - **解决方案**：拆分数据或缩减结构，重新写入。

- **读取结果来自 MOD 目录**：资源路径与 common 相对文件名混用了不同入口。

    - **解决方案**：改用 common 读取方法并记录实际存档路径。

## 总结

common 是存档持久数据空间。读取时处理缺失字段和版本迁移，写入时保留未知字段并调用真正的写盘入口；重启后再次读取的结果决定持久化是否成立。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`readJSONFromCommon` 原版 common JSON 读取、`writeTextFileToCommon` common 文本写入、`writeJSONToCommon` common JSON 写入；
- `org.lazywizard.lazylib.JSONUtils`：`loadCommonJSON` LazyLib common JSON 读取入口，写盘经其嵌套对象 `CommonDataJSONObject` 的 `save()` 完成；
- `<存档目录>/common/`：存档持久化文件位置。
