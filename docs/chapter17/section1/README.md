# 读取 MOD 内文本

说明文字、模板和可调整的数据都可以随 MOD 单独发布，代码在需要时取得内容。本节从一份工坊说明开始，用 `SettingsAPI.loadText` 在应用加载时读回它，再区分默认来源与指定 MOD 的读取方式。

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 说明主动读取 MOD 文件对数据组织和迭代的作用；
- 使用 `SettingsAPI.loadText` 读取 MOD 内文本；
- 写出 MOD 根目录相对路径和 `/` 分隔规则；
- 区分单参读取与 `modId` 限定读取；
- 从路径错误、BOM 和 MOD ID 错误定位文本读取故障。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 和 UTF-8 无 BOM 要求；
- [C6-S2] 的构建与游戏加载；
- [C7-S1] 的应用加载回调；
- [C7-S2] 的 `Global.getSettings()` 和日志入口。

## 把说明文字放入资源文件

在 MOD 的 `data/config/gear_in/notes.txt` 写入以下文本。路径从 MOD 根目录起算，目录名中的 `gear_in` 用来标明这份资源的归属：

```text
GearIn file loading is ready.
```

接着在 [C7-S1] 的 `GearInModPlugin.onApplicationLoad(...)` 追加读取与日志，保留既有代码。这个回调已声明 `throws Exception`，读取失败时由应用加载流程报告异常：

```java
String notes = Global.getSettings().loadText("data/config/gear_in/notes.txt", "gear_in");
Global.getLogger(GearInModPlugin.class).info("GearIn notes: " + notes);
```

构建并重新启动游戏后，`GearIn notes` 日志应包含文件里的句子。这里取得的是字符串，使用方可以把它交给界面、解析成自己的格式，或像本例一样直接记录。

**文字修改与代码修改有了各自的发布方式。** 调整 `notes.txt` 后重新加载资源即可；调整读取时机或处理方法，才需要重新构建 JAR。同一份文件也可以供多个代码入口使用。后续的 JSON 和 CSV 会为内容增加结构，运行期产生的数据则由 common 的读写入口保存。

## 为读取选择来源

上面的调用有两个参数：资源相对路径与 MOD ID。下面的局部片段比较它与单参数形式：

```java
String notes = Global.getSettings().loadText("data/config/gear_in/notes.txt");
String notesFromMod = Global.getSettings().loadText(
        "data/config/gear_in/notes.txt", "gear_in");
```

单参形式从当前资源空间选择一份文件。同一路径若由多个 MOD 提供，选中的来源会影响返回内容。双参形式把来源限定为指定 MOD；这里的 `gear_in` 对应 `mod_info.json` 的 `id`，并且该 MOD 已启用。

对这份自有说明，来源限定能够直接表达我们要读哪一个 MOD。向外开放的共享资源可以另行约定默认来源，但这个选择应当在调用处明确。

## 路径与返回文本

资源路径使用 `/` 分隔，例如 `data/config/gear_in/notes.txt`，起点是 MOD 根目录。引擎在读取前拒绝 `..`、反斜杠、冒号和以 `/` 开头的路径；带盘符的绝对路径同样会被拒绝。

`loadText` 按 UTF-8 解码，移除 `\r` 后返回文本，BOM 则会留在首字符。资源文件保存为 UTF-8 无 BOM，后续解析才能从真正的内容开始。

::: warning 读取成功也可能带回异常首字符
带 BOM 的文本可以被 `loadText` 读回；异常往往在后续解析或比较字符串时才出现。核对文件编码时，同时检查首字符。
:::

文件找不到、路径非法或 MOD ID 找不到，会在读取调用点产生异常。本例让异常沿应用加载报告，日志中的 `GearIn notes` 则标明成功读回内容的用途。

## 验证方案

前置数据：`gear_in` MOD、`notes.txt` 和入口类读取片段已就绪。

1. 保存资源与读取片段，构建 JAR 并重启，搜索 `GearIn notes`。
2. 使用单参 `loadText` 读取 `data/config/gear_in/notes.txt`，观察返回文本。
3. 将读取调用改为双参形式并传入 `gear_in`，再次启动并观察返回文本。

预期结果：

- 两次读取都返回 `GearIn file loading is ready.`；
- 双参调用只读取 `gear_in` 的资源；
- 路径、来源和编码正确时，启动日志没有文本读取异常。

## 故障分析

- **路径校验抛出异常**：路径含 `\\`、`..`、盘符或首字符 `/`。
    - **解决方案**：改用 MOD 根目录下以 `/` 分隔的相对路径，再调用 `loadText`。

- **双参读取出现空对象异常**：传入的 `modId` 与已启用 MOD 不一致。
    - **解决方案**：核对 `mod_info.json` 的 `id` 和启用状态。

- **返回文本的首字符异常**：源文件带有 BOM。
    - **解决方案**：以 UTF-8 无 BOM 重新保存。

- **读取到另一份同名文件**：资源路径过于通用，当前资源空间选择了其他来源。
    - **解决方案**：使用带 MOD 前缀的目录或双参入口限定来源。

## 总结

`loadText` 把资源文件交给代码使用：相对路径定位文件，MOD ID 限定来源，返回的字符串再由调用方处理。文件里的内容可以独立于 JAR 调整；读取时机与使用方式仍属于代码职责。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadText`；路径安全校验由引擎在读取入口自动执行。
- `data/config/`：MOD 自有文本资源可以使用的目录；
- `mod_info.json`：MOD ID 来源。

## 读取 MOD 内 JSON 文件

服务配置和界面文字都可以写成 JSON，但游戏取得它们的方式各有约定。本节先主动读取工坊服务的字段，再把一句问候放进自动加载的 strings 表。文件放在哪里、谁读取它、同名内容怎样合并，分别决定这两份数据如何生效。

## 学习目标

完成后，您将能够：

- 区分原版固定公开入口与 MOD 自有 JSON 路径；
- 说明 `strings.json` 的加载、合并、分类和取值方式；
- 说明 JSON 注释、尾逗号、编码和字段大小写边界；
- 使用 `SettingsAPI.loadJSON` 读取 MOD 自有 JSON；
- 从路径、BOM、JSON 语法、字段类型和缺失文本定位故障。

## 前置知识

- [C17-S1] 的文件读取概念、MOD 相对路径和 `modId`；
- [C1-S1] 的 JSON 基础与 UTF-8 无 BOM 要求；
- [C7-S2] 的 `Global.getSettings()` 和日志入口。

## 区分文件的使用方

`data/strings/strings.json` 是游戏约定的文本入口。游戏启动时会主动查找原版与已启用 MOD 的同名文件，合并后供代码取值；文件放在这个路径即可参与加载。

`data/config/gear_in/services.json` 则由 MOD 自己约定。我们需要给出读取路径，调用 `loadJSON(...)`，并解释其中的字段。JSON 描述的是数据结构，文件的用途由实际使用方确定。

## 创建服务配置并读回字段

在 `data/config/gear_in/services.json` 写入：

```json
{
  "serviceId": "GearIn_service",
  "interval": 10
}
```

向 `GearInModPlugin.onApplicationLoad(...)` 追加以下局部读取，添加 `org.json.JSONObject` 的 import：

```java
JSONObject services = Global.getSettings().loadJSON(
        "data/config/gear_in/services.json", "gear_in");
Global.getLogger(GearInModPlugin.class).info("GearIn service: id="
        + services.getString("serviceId") + " interval=" + services.optInt("interval", 10));
```

构建并重启后，搜索 `GearIn service`，日志应包含 `GearIn_service` 与 `interval=10`。这一步确认配置能被读取；服务何时运行、间隔怎样使用，仍要由后续代码实现。文件名叫 services，并不会让游戏凭空增加一项服务。

`loadJSON` 返回 `JSONObject`。本例把 `serviceId` 作为必填字段，用 `getString` 直接取得；`interval` 允许省略，`optInt` 此时采用 10。**缺省值是配置约定的一部分。** 哪个字段可以省略，应由这份数据的含义决定。

### 来源与字段读取

以下是用于比较来源和缺省值的局部片段：

```java {4}
JSONObject services = Global.getSettings().loadJSON(
        "data/config/gear_in/services.json");
String serviceId = services.getString("serviceId");
int interval = services.optInt("interval", 10);
```

单参形式从当前资源空间选择文件；双参形式把来源限定为指定 MOD。本节使用已启用的 `gear_in`，与 `mod_info.json` 的 ID 对应。

JSON 路径遵循 [C17-S1] 的资源路径规则。引擎在读取前拒绝 `..`、反斜杠、冒号和以 `/` 开头的路径。`getString` 在字段缺失时抛出异常，已有值可以转为字符串；`getInt` 和 `getBoolean` 按各自规则转换，无法取得所需类型时抛出异常。允许缺省的字段用 `optString`、`optInt` 等方法并给出明确的替代值。

### 验证方案

1. 保存配置和读取调用，构建 JAR 并重启，在 `starsector.log` 搜索 `GearIn service`。
2. 将调用改为单参形式，再次构建、重启并比较字段。
3. 从配置省略 `interval`，重启并观察缺省值，然后恢复 `"interval": 10` 与来源限定调用。

预期结果：

- 两种读取均得到 `GearIn_service`，来源限定调用选择 `gear_in` 的文件；
- `interval` 按文件读取，省略时采用 10；
- 路径、编码、语法和字段类型正确时，启动正常完成。

## 在 strings 表中登记问候

在 MOD 的 `data/strings/strings.json` 加入本节分类与键，已有分类时在同一对象内追加：

```json
{
    "GearIn_ui": {
        "GearIn_text_hello": "你好，工坊。"
    }
}
```

`GearIn_ui` 是分类，`GearIn_text_hello` 是分类内的键，后面的本地化封装继续沿用它们。游戏加载该文件后，双参 `getString(category, id)` 就可以取回文字。局部调用如下：

```java
String text = Global.getSettings().getString("GearIn_ui", "GearIn_text_hello");
```

将取值与日志加入应用加载方法：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn strings: "
        + Global.getSettings().getString("GearIn_ui", "GearIn_text_hello"));
```

构建并重启，`GearIn strings` 应记录 `你好，工坊。`。**分类和键选择文字，调用方负责把它送到显示位置。** 单参 `getString(key)` 读取的是 `data/config/settings.json`；此处取 strings 文本使用双参形式。

## 合并之后，同名文字由谁提供

字符串表在启动资源加载阶段自动载入。日志先出现 `Loading strings`，随后加载 `data/strings/tooltips.json`、`data/strings/strings.json` 和 `data/strings/tips.json`。原版与各个已启用 MOD 的 `strings.json` 分别产生一行 `Loading JSON from [...]`，路径包含 `data/strings/strings.json`。

各来源的 `strings.json` 按启用顺序合并：原版内容先进入结果，后续 MOD 依次加入。对象按键递归合并，标量由后加载来源覆盖，因此一个 MOD 只写自己的分类和键，其余内容仍会保留。

数组通常追加内容；键以 `music_` 开头，或原有数组长度为 4 且键名包含 `color` 或 `button` 时，会清除原数组后加入新内容。新数组以 `core_clearArray` 为首元素时也会先清除原内容。`mod_info.json` 的 `replace` 可以进一步整体替换文件。

自有分类和键采用 MOD 前缀，便于维持这种归属。若有意修改已有文字，就需要使用原来的分类与键。

编者不赞成为了几句文字接管整份原版文本表。按键合并已经能完成这项工作；整体替换还会把原版内容的维护、其他 MOD 的同路径输入一并牵涉进来，取舍应当与实际修改范围相称。

下面用行星接近文本演示覆盖，在本 MOD 的文本表追加原版 `planetInteractionDialog` 分类中的 `approach`：

```json
"planetInteractionDialog": {
    "approach": "$fleetOrShip 正在接近 $planetName；这是工坊的覆盖文本。"
}
```

这是 JSON 对象内的局部字段，放在 `GearIn_ui` 分类之后，并补上分类之间的逗号。同一个文件同时保留自有分类和覆盖分类。再向应用加载方法追加观察：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn strings approach: "
        + Global.getSettings().getString("planetInteractionDialog", "approach"));
```

日志会保留 `$fleetOrShip` 和 `$planetName`，行星对话的使用方才会替换它们。`getString` 只取回模板；`$token` 的替换、`%s` 的 `String.format` 格式化，都由取值后的代码处理。舰队遭遇文本里的 `$faction`、`$fleetOrShip` 同样由对应对话填入动态内容，格式参数的数量与类型也要符合模板。

::: warning 覆盖的是整个文本键
同名 `(category, id)` 的后加载值会替换原句。新增自己的文字使用自有键；覆盖实验结束后移除 `planetInteractionDialog.approach`，行星对话才会重新采用原版文本。
:::

## 文件语法与缺失文本

加载前，引号之外的 `#` 到行尾内容会被剥离，尾逗号也能被解析。括号、字符串引号和字段结构仍按 JSON 组织，文件保存为 UTF-8 无 BOM。BOM 会留在首字符，括号不配对或引号不闭合会产生带文件路径的 `JSONException`，使启动中止。

分类名和键名区分大小写。分类或键缺失时，双参 API 返回 `Missing string: [<id>]` 并记录 ERROR，游戏继续运行，界面可能显示这句占位文字。于是，文件能解析、文本键能取值和界面显示正确，需要分别确认。

## 原版分类速查表

原版 `strings.json` 有 4 个顶层分类：

| 分类                         | 内容                                                         | 键风格                                      | 主要使用方     | 键数 |
| ---------------------------- | ------------------------------------------------------------ | ------------------------------------------- | -------------- | ---: |
| `fleetInteractionDialog`     | 生涯舰队遭遇对话：交战、追击、撤退、接舷、打捞和友军增援     | 大量 `$faction`、`$fleetOrShip` 等令牌      | 舰队遭遇对话   |  160 |
| `campaignHelp`               | 首次生涯提示和低 CR、低补给等提示                            | 键后缀使用 `_text`、`_dismiss` 或 `_action` | 生涯帮助弹窗   |   63 |
| `jumpPointInteractionDialog` | 跳跃点交互：`approach`、`starAnchor`、`noFuel`、`noExits` 等 | `$fleetOrShip`                              | 跳跃点交互对话 |    5 |
| `planetInteractionDialog`    | 行星接近文本：`approach`                                     | `$fleetOrShip`、`$planetName`               | 行星交互对话   |    1 |

MOD 可以建立自己的分类。对话文本用令牌填入动态内容，帮助文字可以用 `\n` 分段，分类本身只负责组织这些键。

### 同目录下的其他文本文件

`data/strings/tooltips.json` 与 strings 各自加载，内容彼此独立；tooltip 值可以是纯字符串，也可以是含 `title` 和 `body` 的对象。`data/strings/tips.json` 保存加载界面的随机小贴士。这两份文件由各自使用方读取，双参 `getString` 的目标仍是 strings 表。

## 比较 strings 的固定加载

### 验证方案

1. 保存自有分类与观察调用，构建重启，搜索 `Loading strings`、`data/strings/strings.json` 和 `GearIn strings`。
2. 追加覆盖分类及第二条日志，构建重启，观察 `GearIn strings approach`。在生涯中接近并交互一颗行星，比较接近文本。
3. 实验完成后移除覆盖用的 `planetInteractionDialog` 字段，保留 `GearIn_ui`，重启确认原版行星文本恢复。

预期结果：

- 原版与 `gear_in` 的 strings 文件都在启动阶段被加载；
- 自有键返回 `你好，工坊。`；
- 覆盖键的日志保留 `$fleetOrShip` 和 `$planetName`，行星交互显示替换后的名称与工坊文字；
- 只提供自有分类时，原版分类继续保留。

## 故障分析

- **JSON 在首字符处解析失败**：文件带有 BOM。
    - **解决方案**：以 UTF-8 无 BOM 重新保存，再调用 `loadJSON`。

- **JSON 报告括号或字符串错误**：对象结构、引号或逗号不合法。
    - **解决方案**：根据错误位置修正相邻字段，再次加载。

- **读取字段时抛出异常**：字段缺失。
    - **解决方案**：核对字段名和类型，允许缺失时改用对应 `opt` 方法。

- **双参读取出现空对象异常**：传入的 `modId` 与已启用 MOD 不一致。
    - **解决方案**：核对 `mod_info.json` 的 `id` 和启用状态。

- **自有 JSON 没有产生任何业务结果**：文件没有使用方，固定路径只负责资源加载。
    - **解决方案**：通过 `loadJSON` 主动读取并解释字段。

- **strings 文本被其他内容替换**：分类或键与后加载来源重复。
    - **解决方案**：为自有分类和键添加 MOD 前缀，重启。

- **`getString` 返回 `Missing string`**：分类、键、固定路径或 MOD 启用状态不正确。
    - **解决方案**：逐字符核对这些入口，再调用 `getString`。

## 总结

自有服务配置由 `loadJSON` 读成对象，代码解释字段；strings 则由游戏按固定路径合并，调用方以分类和键取得模板。字段缺省、同名文本覆盖和动态内容替换各有明确入口，文件的 JSON 外形并不能代替这些使用约定。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadJSON`、`getString`；路径安全校验由读取入口内部执行。
- `data/strings/strings.json`：原版公开的本地化文本入口；
- `data/config/`：MOD 自有 JSON 资源目录；

## 读取 MOD 内 CSV

同一张 CSV 可以由本 MOD 独立读取，也可以允许其他 MOD 增补或覆盖。本节用工坊服务表比较这些入口：先取得自己的原始行，再加入一份外部表，确认行键怎样决定合并结果。选择读取方法时，我们也就同时决定了谁有权提供这份数据。

## 学习目标

完成后，您将能够：

- 使用 `loadCSV` 读取列名映射的对象数组；
- 使用 `getMergedSpreadsheetData` 读取跨 MOD 合并表；
- 使用 `getMergedSpreadsheetDataForMod` 读取指定 master 文件并允许外部 MOD 覆盖行；
- 区分单文件读取、合并读取和 JSON 合并；
- 根据重复键、路径和 master 归属定位表格读取故障。

## 前置知识

- [C1-S7] 的 CSV 表头、空列与文本转义规则；
- [C3-S4] 的描述表键与合并语义；
- [C17-S1] 的 MOD 相对路径和 `modId` 限定读取；
- [C17-S1] 的 JSON 单文件读取。

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

`getMergedJSON(...)` 递归合并对象键，标量由后加载来源覆盖，数组按引擎规则追加或整体替换。每份文件提供自己的分类或键，合并单位就转为对象结构。需要读取单一 MOD 的 JSON 时，沿用 [C17-S1] 的 `loadJSON(path, modId)`。

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
