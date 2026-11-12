# 读写 common 数据

一份跨生涯保存的战斗报告，需要在载入时读回，在记录后写入，并在数据格式改变时继续解释已有内容。本节把这份报告放到 common，以次数、最近说明和 schema 演示完整的读改写过程，再比较原版写缓存与 LazyLib 的保存时机。

_本节预期阅读时间：\~28 分钟。_

_本节预期操作时间：\~50 分钟。_

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

## 选择报告的保存位置

**common 属于用户数据空间，同名文件跨生涯存档共享。** 换一份存档，报告仍从同一个文件读回。这适合跨生涯计数、共用设置与报告；某一生涯中的剧情进度则随该生涯保存。

游戏的存档目录由启动配置决定，代码给出相对文件名即可。本节使用 `gear_in/state.json`，实际文件位于 `<存档目录>/common/gear_in/state.json.data`；游戏追加 `.data` 后缀。

文件名使用 MOD 自己的目录和名称，读写也采用 common 入口。MOD 资源目录用于发布输入，common 用于保存运行中的结果；即使逻辑文件名相同，实际位置仍由 API 决定。

## 在载入生涯时读回报告

在既有 `GearInModPlugin` 中加入下面的局部辅助方法，添加 `org.json.JSONObject` 的 import：

```java
private static JSONObject readCommonState() throws Exception {
    JSONObject state = new JSONObject();
    if (Global.getSettings().fileExistsInCommon("gear_in/state.json")) {
        state = Global.getSettings().readJSONFromCommon("gear_in/state.json", false);
    }
    if (!state.has("schema")) {
        state.put("schema", 1);
    }
    if (!state.has("battles")) {
        state.put("battles", 0);
    }
    if (!state.has("lastReport")) {
        state.put("lastReport", "");
    }
    return state;
}
```

`schema` 标识数据格式，`battles` 记录已保存的测试战斗报告次数，`lastReport` 保存最近的说明。首次使用时文件尚未建立，方法从空对象补出这三个字段；已有文件则读成原对象，只补入缺失字段。文件里其他内容因此继续保留。

补值发生在内存中，保存要等实际记录报告。原版 `readTextFileFromCommon(...)` 在目标与临时文件都不存在时返回空字符串，`readJSONFromCommon(...)` 解析这份空内容会抛出 `JSONException`，所以本例先判断文件存在性。已有文件的解析错误交给生涯入口记录。

向既有 `onGameLoad(boolean newGame)` 追加下面的局部调用，保留此前的生涯初始化：

```java
try {
    JSONObject state = readCommonState();
    Global.getLogger(GearInModPlugin.class).info("GearIn common read: " + state);
} catch (Exception ex) {
    Global.getLogger(GearInModPlugin.class).error("GearIn common read failed", ex);
}
```

构建、重启并载入生涯后，搜索 `GearIn common read`。首次读取的 schema 为 1、次数为 0、最近说明为空；已有字段则按文件进入日志。**载入只读取，次数在记录报告时增加。**

### 验证方案

1. 保存读取方法和生涯调用，构建 JAR 并重启，载入测试生涯，搜索 `GearIn common read`。
2. 已有测试文件时，记录字段值；游戏退出后，省略文件中的 `battles`，重新启动并载入，观察内存补入的 0。比较完成后恢复原值。
3. 在测试 JSON 中增加一个自定字段，再次读取，确认日志仍包含它。保留该字段，随后观察写入。

预期结果：

- 首次读取采用格式 1、次数 0 与空说明，已有值按文件读取；
- 缺失的字段补入内存对象，自定字段继续保留；
- 原版读取方法处理的是实际公共目录，读取调用本身没有保存内存修改。

## 在记录报告时修改并保存

向 `GearInModPlugin` 加入下面的局部静态方法：

```java
public static void recordCommonReport() throws Exception {
    JSONObject state = readCommonState();
    int schema = state.getInt("schema");
    if (schema < 2) {
        state.put("reports", state.optInt("reports", 0));
        state.put("schema", 2);
    }
    state.put("battles", state.getInt("battles") + 1);
    state.put("lastReport", "GearIn test report");
    Global.getSettings().writeJSONToCommon("gear_in/state.json", state, false);
    Global.getLogger(GearInModPlugin.class).info("GearIn common saved: " + state);
}
```

报告方法读回原对象，在格式低于 2 时补出 `reports`，并把 schema 改为 2；已有的 `reports` 和其他字段继续保留。随后次数加 1、写入最近说明，整个对象交给原版入口保存。

这就是示例的数据迁移。旧格式只缺少一个字段，补值就能建立新结构；若字段含义也有改变，版本分支还要完成相应转换。保存当前 schema 后，下次读取才能知道这份数据已经按哪个格式处理。

测试战斗结束后，通过 Console Commands 执行一次：

```text
runcode data.scripts.gearin.GearInModPlugin.recordCommonReport();
```

执行一次便记录一份测试报告：`battles` 加 1，`lastReport` 写为示例文本。写入参数 `false` 让本次保存直接执行，随后可以从磁盘读回；换另一份生涯仍能取得同一结果。

## 根据保存频率选择写入方式

原版写入入口分别接受文本和 JSON。下面是用于说明参数的局部片段：

```java
Global.getSettings().writeTextFileToCommon("gear_in/state.txt", text);
Global.getSettings().writeJSONToCommon("gear_in/state.json", state, true);
```

`writeJSONToCommon(..., true)` 缓存序列化内容，变化时把写入放到后台队列；重复提交相同内容可以省去一次写入。对应的 `readJSONFromCommon(..., true)` 可以把读取内容放入写缓存，缓存已有该文件时会直接从缓存解析对象。`false` 适合本节直接比较磁盘内容的实验。

**后台调用返回与磁盘写入完成是两个时机。** 验证持久化时，重启后从文件读回；比较缓存是否省去写入时，再观察重复提交相同内容后的文件更新时间。缓存读取适合配合这种保存方式使用，也会使返回对象反映缓存里的内容。

common 文本单次最多写入 **1,048,576 个 Java 字符**。写入先使用 `.data.inprogress` 临时文件，再替换目标文件。异常发生在哪一步会影响磁盘状态，因此按目标文件、临时文件和异常日志核对本次结果；调用失败后的原文件状态也按实际文件判断。

## 用 LazyLib 组织读改写

LazyLib 的 `CommonDataJSONObject` 把 JSONObject 与公共文件名关联起来。以下是局部取值用法，需要 LazyLib 已启用并进入编译依赖，添加 `org.lazywizard.lazylib.JSONUtils` 及嵌套类型 `JSONUtils.CommonDataJSONObject` 的 import：

```java
CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json");
int battles = data.optInt("battles", 0);
String schema = data.optString("schema", "1");
```

`opt` 方法在字段缺失或无法转换为所需类型时采用给定缺省，`get` 方法则要求字段能够取值。`loadCommonJSON(...)` 会捕获读取与解析异常：单参形式返回空对象，带默认资源路径的重载可以改从 MOD 配置取得初始结构。

编者建议对不可丢失的记录采用本例的原版读取入口，并在解析失败时保留明确的处理机会。LazyLib 返回空对象很方便，但随后若按初始数据修改并保存，已有文件的内容也会被覆盖；是否接受这个结果，取决于这份数据能否重新建立。

::: warning LazyLib 载入时也可能写入
载入非空 JSON 时，包装对象在构造期间调用 `save()`；空对象尚未执行这次保存。`close()` 还会再次调用 `save()`，因此载入与关闭各有自己的写入时机。
:::

准备修改并保存时，try-with-resources 可以安排关闭。以下为局部写入片段：

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json")) {
    int schema = data.optInt("schema", 1);
    if (schema < 2) {
        data.put("reports", data.optInt("reports", 0));
        data.put("schema", 2);
    }
    data.put("battles", data.optInt("battles", 0) + 1);
    data.put("lastReport", "GearIn test report");
}
```

这里载入非空 JSON 时先保存原内容，`put` 修改内存对象，离开作用域后 `close()` 再保存修改。块内也可以显式调用 `data.save()` 提前保存，正常关闭时仍会增加一次写入。增量修改使用读回的对象，未知字段继续留在其中。

用户设置若由设置库管理，使用该库的读取与保存入口。文件看起来是普通 common JSON，实际还有自己的使用方和刷新时机；直接改写后，库的保存可能再次写回它掌握的值。

### 验证方案

前置数据：Console Commands 已启用，准备两份生涯存档；同一公共测试文件用于观察跨存档结果。

1. 记录当前 `battles`，完成一场测试战斗后执行报告命令，检查 `GearIn common saved` 和实际 `gear_in/state.json.data`。
2. 退出游戏后重新启动、载入原生涯，确认读取到增加后的次数；再载入另一份生涯比较同名文件。
3. 在游戏退出时，把测试文件的 `schema` 设为 1，省略 `reports`，保留此前增加的自定字段。重启后执行报告命令，检查迁移与保存结果。
4. 保存同一对象时采用 JSON 写缓存参数 `true`，重复提交相同内容，等待后台写入后比较文件更新时间；完成缓存实验后恢复报告方法中的 `false`。
5. 采用 LazyLib 局部片段时，在相同报告入口中用它替换原版读写，观察关闭后保存的字段与再次读取结果。

预期结果：

- 每次报告命令将 `battles` 增加 1，`lastReport` 为 `GearIn test report`；
- 重启与换存档后读到同一个公共文件的已保存值；
- 格式 1 迁移后包含 `reports`，schema 为 2，自定字段保持原值；
- 相同序列化内容可以由写缓存省去重复写入，持久化结果按文件读回确认；
- LazyLib 载入非空对象时保存原内容，关闭后保存修改；提前 `save()` 再增加一次保存。

## 故障分析

- **原版 JSON 读取抛出异常**：公共文件不存在，或已有内容不能解析。
    - **解决方案**：按逻辑文件名检查存在性；已有文件时根据异常位置修正 JSON，再读取。

- **读取可选字段时抛出异常**：直接对缺失键使用了 `get`。
    - **解决方案**：按字段的可选含义用 `opt`，或先通过 `has` 补入缺省值。

- **迁移后字段仍不符合当前格式**：版本分支没有执行所需转换，或迁移完成后没有保存当前 schema。
    - **解决方案**：对照旧输入补齐对应转换，并在本节写入入口保存当前格式。

- **保存后自定字段消失**：更新时创建了只含已知字段的新对象。
    - **解决方案**：读取现有对象，在该对象上补充与修改字段。

- **重启后次数没有保留**：内存修改尚未交给写入入口，或 LazyLib 对象尚未保存、关闭。
    - **解决方案**：执行报告写入方法，或让自动关闭完成保存，再从公共文件读回。

- **已有值被缺省覆盖**：每次载入都把整份默认对象写回已有文件。
    - **解决方案**：读取已有对象，只为缺失字段补值，并在需要记录时保存。

- **后台写入返回后文件仍是此前内容**：JSON 写缓存的后台队列尚未完成。
    - **解决方案**：等待写入完成并重新读取；直接磁盘实验采用本节的 `false` 参数。

- **写入异常后文件无法解析**：写入或替换阶段失败，目标文件的实际状态需要核对。
    - **解决方案**：停止使用损坏内容，核对目标、`.data.inprogress` 与异常日志，修正并恢复测试数据后再写入。

- **大文本写入失败**：单次文本超过 1,048,576 个 Java 字符。
    - **解决方案**：缩减或按用途拆分数据后写入。

- **用户设置再次恢复为此前数值**：设置库继续按自身保存流程写回它管理的文件。
    - **解决方案**：通过该库的入口读取、更新和保存。

- **读到的是 MOD 资源文件**：调用使用了资源读取入口。
    - **解决方案**：为公共逻辑文件名使用 common 读写方法，按实际存档目录核对文件。

## 总结

公共报告的完整过程是读回原对象、补齐缺失字段、按 schema 处理格式，再于实际记录时修改并保存。文件跨生涯共享，换存档仍会取得同名结果。原版直接写入、后台写缓存与 LazyLib 的载入、关闭各有保存时机，确认持久化时便要同时对应调用和磁盘内容。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`fileExistsInCommon`、`readTextFileFromCommon`、`readJSONFromCommon`、`writeTextFileToCommon` 与 `writeJSONToCommon` 提供公共文件入口；
- `org.lazywizard.lazylib.JSONUtils`：`loadCommonJSON` 将公共 JSON 关联到文件名；
- `org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject`：`save()` 写入，`close()` 调用保存；
- `<存档目录>/common/`：用户的跨存档公共文件位置。
