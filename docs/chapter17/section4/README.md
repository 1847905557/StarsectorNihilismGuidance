# 读写 common 数据

配置文件提供随 MOD 发布的输入，运行中产生的状态则需要另一处保存。本节把战斗报告写到 common：载入生涯时读回，记录一份报告后保存，再换存档或重启核对。读取同时处理缺失字段与 schema 迁移，已有对象中的其他字段继续保留；公共文件的共享范围和真正的写入时机也一起确认。

*本节预期阅读时间：\~28 分钟。*

*本节预期操作时间：\~50 分钟。*

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

## 先确定这份数据由谁共享

**common 属于用户数据空间，跨生涯存档共享。**换一份存档，代码仍读取同名公共文件。某一生涯中的剧情进度继续随该生涯保存；跨生涯计数、共用设置和报告可以放到 common。

游戏的存档目录由启动配置决定，代码给出相对文件名即可。本节使用 `gear_in/state.json`，实际文件位于 `<存档目录>/common/gear_in/state.json.data`；游戏追加 `.data` 后缀。

MOD 资源目录保存发布时的输入，common 保存运行期状态。文件名使用自己的目录和名称，读取方法也选择 common 入口。资源路径和公共文件名即使看起来相同，读取位置仍由 API 决定。

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

文件首次使用时还不存在，因此先检查存在性；已有文件读成原来的 JSONObject，再为缺失字段补值。`schema` 标识数据格式，`battles` 记录已保存的测试战斗报告次数，`lastReport` 保存最近的说明。这里补入的是内存对象，保存仍由后面的写入入口完成。

向既有 `onGameLoad(boolean newGame)` 追加下面的局部调用，保留此前的生涯初始化：

```java
try {
    JSONObject state = readCommonState();
    Global.getLogger(GearInModPlugin.class).info("GearIn common read: " + state);
} catch (Exception ex) {
    Global.getLogger(GearInModPlugin.class).error("GearIn common read failed", ex);
}
```

构建、重启并载入生涯后，搜索 `GearIn common read`。首次读取使用 `schema` 为 1、`battles` 为 0 和空的 `lastReport`；已有字段按文件内容进入日志。**载入只读取，报告在实际记录时增加。**

### 原版与 LazyLib 的读取入口

原版可以读取公共文本和 JSON。文件不存在时，`readTextFileFromCommon(...)` 返回空字符串；`readJSONFromCommon(...)` 解析空内容会抛出 `JSONException`。本节通过存在性判断安排首次初始化；已有文件无法解析时，异常由生涯入口记录。

LazyLib 将文件名和 JSONObject 包装在一起。以下为 LazyLib 取值的局部用法，需要该库已启用并进入编译依赖；载入非空对象时也会保存一次：

```java
CommonDataJSONObject data = JSONUtils.loadCommonJSON("gear_in/state.json");
int battles = data.optInt("battles", 0);
String schema = data.optString("schema", "1");
```

添加 `org.lazywizard.lazylib.JSONUtils` 和其嵌套类型 `JSONUtils.CommonDataJSONObject` 的 import。`loadCommonJSON(...)` 捕获读取或解析异常，单参形式可返回空对象；带默认资源路径的重载则可从 MOD 配置取得初始结构。载入的 JSON 非空时，包装对象在构造期间调用 save，将内容写回；空对象直接返回，尚未执行这次保存。

`opt` 方法在缺失或无法按所需类型取值时使用给定缺省，`get` 方法适合要求字段存在并能转换的场合。是否采用缺省，由这份数据允许的内容决定。

::: warning LazyLib 载入与关闭都可能保存

`loadCommonJSON(...)` 载入非空 JSON 时会保存一次，`CommonDataJSONObject.close()` 再调用 `save()`。需要纯读取时采用原版入口；使用 LazyLib 读改写时，把载入、修改和关闭对应的保存安排清楚。

:::

### 验证方案

1. 保存读取方法和生涯调用，构建 JAR 并重启，载入测试生涯，搜索 `GearIn common read`。
2. 已有测试文件时，记录字段值；游戏退出后，省略文件中的 `battles`，重新启动并载入，观察内存补入的 0。比较完成后恢复原值。
3. 在测试 JSON 中增加一个自定字段，再次读取，确认日志仍包含它。保留该字段，随后观察写入。

预期结果：

- 首次读取采用格式 1、次数 0 与空说明，已有值按文件读取；
- 缺失的字段补入内存对象，自定字段继续保留；
- 原版读取方法处理的是实际公共目录，读取调用本身没有保存内存修改。

## 记录报告，再把对象保存

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

**先读原对象，再改目标字段，最后保存。**格式 1 增加 `reports` 并写为格式 2；已有 `reports` 和其他字段保留。这就是本节的迁移：根据已知旧格式建立当前需要的字段，迁移完成后保存当前 schema，下次读取便可使用它。

迁移的动作随数据结构决定。这里新增一个字段，直接补值即可；真实格式需要转换字段含义时，再把相应转换放进版本分支。编者建议把旧输入、转换结果和使用方一起写清，版本号本身不会替您完成转换。

测试战斗结束后，通过 Console Commands 执行一次：

```text
runcode data.scripts.gearin.GearInModPlugin.recordCommonReport();
```

该入口显式记录一份测试报告，`battles` 加 1，`lastReport` 写入示例文本。`false` 让本次 JSON 写入直接执行，随后按磁盘内容读回；载入另一份生涯也能看到同一份结果。

### 文本写入与 JSON 写缓存

原版写入入口分别接受文本和 JSON。下面是用于说明参数的局部片段：

```java
Global.getSettings().writeTextFileToCommon("gear_in/state.txt", text);
Global.getSettings().writeJSONToCommon("gear_in/state.json", state, true);
```

`writeJSONToCommon(..., true)` 使用序列化内容缓存，内容变化时将写入放到后台队列；相同内容可以省去重复写入。对应的 `readJSONFromCommon(..., true)` 用于准备采用写缓存的数据。`false` 适合本节直接观察磁盘的实验。

后台模式返回时，调用已经完成，磁盘可能仍在等待写入。验证持久化时，重启后从文件读回；需要比较缓存是否省去了写入时，再观察相同内容重复提交后的文件更新时间。

common 文本单次最多写入 **1,048,576 个 Java 字符**。写入先使用 `.data.inprogress` 临时文件，再替换目标文件。异常发生在哪一步会影响磁盘状态，因此按目标文件、临时文件和异常日志核对本次结果；调用失败后的原文件状态也按实际文件判断。

### 使用 LazyLib 保存同一个对象

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

载入非空 JSON 时，原内容已保存一次；`put` 再改变内存对象，结束作用域时 `close()` 调用 `save()` 写入修改。需要在块内提前保存时可以显式调用 `data.save()`；正常结束时仍会再保存一次。增量修改继续使用读回的对象，未知字段也就留在其中。

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

报告先从公共文件读成对象，缺失字段在内存中补齐，格式分支完成已有数据的迁移；记录时修改次数和说明，再调用写入入口。文件由用户数据空间共享，所以换存档也继续使用同名结果。

原版直接写入、JSON 写缓存和 LazyLib 自动关闭分别安排不同的保存时机。观察调用结果以后，再从文件读回，才能把内存变化与持久化对应起来。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`fileExistsInCommon`、`readTextFileFromCommon`、`readJSONFromCommon`、`writeTextFileToCommon` 与 `writeJSONToCommon` 提供公共文件入口；
- `org.lazywizard.lazylib.JSONUtils`：`loadCommonJSON` 将公共 JSON 关联到文件名；
- `org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject`：`save()` 写入，`close()` 调用保存；
- `<存档目录>/common/`：用户的跨存档公共文件位置。
