# 读取 MOD 内 JSON 文件

文本已经可以主动读回，现在把工坊服务写成 JSON，再把界面文字放入自动加载的 strings 表。两种文件各有入口：代码主动解释配置字段，游戏按固定路径合并文本分类，运行时再按分类和键取值。先完成自有配置读取，再比较这两种数据怎样进入代码。

*本节预期阅读时间：\~24 分钟。*

*本节预期操作时间：\~45 分钟。*

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

## 固定入口与自有路径

`data/strings/strings.json` 是原版固定公开入口。游戏在启动资源加载阶段主动查找原版与全部已启用 MOD 的同名文件，并按 strings 规则处理内容；MOD 只需把文件放在该路径，不需要在 `mod_info.json` 中声明，也不需要调用 `loadJSON` 注册。

其他 JSON 路径由 MOD 自己约定，例如 `data/config/gear_in/services.json`。游戏不会猜测这些文件的用途；当前代码给出路径、调用 `loadJSON(...)`，再按本节字段解释结果。

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

构建并重启后，搜索 `GearIn service`。日志应包含 `GearIn_service` 与 `interval=10`；服务什么时候运行，仍由使用这些字段的代码决定。游戏读到了文件，还没有替您做完服务。

`loadJSON` 返回 `JSONObject`。本例明确提供 `serviceId`，所以直接取值；`interval` 是可选配置，省略时采用 10。**缺省值表达文件允许省略的内容。**需要必填的字段时，`get` 方法可以让错误在读取位置显现。

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

## strings 文件的加载与合并

字符串表在启动资源加载阶段自动载入。日志先出现 `Loading strings`，随后加载 `data/strings/tooltips.json`、`data/strings/strings.json` 和 `data/strings/tips.json`。原版与各个已启用 MOD 的 `strings.json` 分别产生一行 `Loading JSON from [...]`，日志路径包含 `data/strings/strings.json`。

各来源的 `strings.json` 按启用顺序合并：

- 原版内容先进入结果，后续 MOD 依次合并；
- 对象值按键递归合并；
- 标量值由后加载来源覆盖前一个来源；
- 数组按键的合并规则处理，`music_*`、长度为 4 的 `color` 或 `button` 数组，以及首元素为 `core_clearArray` 的数组可以整体替换；
- `mod_info.json` 的 `replace` 可以整体替换某个文件。

MOD 只需要写自己的键，原版和其他 MOD 的键会继续保留。相同 `(category, id)` 的文本会受启用顺序影响，因此自有分类和键应使用 MOD 前缀，避免覆盖已有文本。

## 语法与编码

加载前，引号之外的 `#` 到行尾内容会被剥离，再交给 JSON 解析器；尾逗号也会被接受。括号、字符串引号和字段结构仍须符合 JSON 解析要求。

文件采用 UTF-8 无 BOM。BOM 会粘在文件首字符并使加载失败；括号不配对或字符串引号不闭合会在加载阶段抛出带文件路径的 `JSONException`，启动随之中止。

分类名和键名逐字符匹配并区分大小写。分类或键不存在时，双参取值 API 返回 `Missing string: [<id>]`，并在 `starsector.log` 记录 ERROR；游戏继续运行，界面会显示该字面量。

## 取值 API 与占位符

运行期读取 strings 文本使用公开 API：

```java
String text = Global.getSettings().getString("GearIn_ui", "GearIn_text_hello");
```

双参 `getString(category, id)` 读取 `strings.json`。单参 `getString(key)` 读取 `data/config/settings.json`，两者的文件和键空间不同。

strings 的值可以包含两类占位符：

- `$token` 不会由引擎自动替换，使用方取值后再替换；
- `%s` 不会由引擎自动格式化，取值侧使用 `String.format` 传入参数。

例如，`$faction`、`$fleetOrShip` 等令牌由舰队遭遇文本的使用方替换；`%s` 的参数数量和类型与模板对应，格式化结果才符合当前显示。

## 原版分类速查表

原版 `strings.json` 有 4 个顶层分类：

| 分类 | 内容 | 键风格 | 主要使用方 | 键数 |
| --- | --- | --- | --- | ---: |
| `fleetInteractionDialog` | 生涯舰队遭遇对话：交战、追击、撤退、接舷、打捞和友军增援 | 大量 `$faction`、`$fleetOrShip` 等令牌 | 舰队遭遇对话 | 160 |
| `campaignHelp` | 首次生涯提示和低 CR、低补给等提示 | 键后缀使用 `_text`、`_dismiss` 或 `_action` | 生涯帮助弹窗 | 63 |
| `jumpPointInteractionDialog` | 跳跃点交互：`approach`、`starAnchor`、`noFuel`、`noExits` 等 | `$fleetOrShip` | 跳跃点交互对话 | 5 |
| `planetInteractionDialog` | 行星接近文本：`approach` | `$fleetOrShip`、`$planetName` | 行星交互对话 | 1 |

MOD 可以使用自己的分类，不需要复用原版分类名。对话类值依靠 `$token` 注入动态内容，帮助类值可以使用 `\n` 组织多段文本。

## 同目录文件

`data/strings/tooltips.json` 与 `strings.json` 不合并，键空间独立；值可以是纯字符串，也可以是包含 `title` 和 `body` 的对象。`data/strings/tips.json` 保存加载界面的随机小贴士。两者都不能用双参 `getString` 读取。单参 `getString(key)` 的目标是 `data/config/settings.json`。

## 定义并读取本节文本键

在 MOD 的 `data/strings/strings.json` 加入本节分类与键，已有分类时在同一对象内追加：

```json
{
    "GearIn_ui": {
        "GearIn_text_hello": "你好，工坊。"
    }
}
```

这个分类与键在本节定义，后面的本地化封装继续沿用。将下列局部调用加入应用加载方法：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn strings: "
        + Global.getSettings().getString("GearIn_ui", "GearIn_text_hello"));
```

需要比较已有键的覆盖时，在本 MOD 的文本表追加原版 `planetInteractionDialog` 分类中的 `approach` 键：

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

**文本键决定取哪句话，调用方决定怎样填入动态内容。**这里日志保留令牌，行星对话使用方才会替换舰队与行星名称。

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

服务配置由当前来源限定读取，字段通过 JSONObject 解释；strings 表则按固定路径自动合并，通过分类与键取文字。可选字段的缺省、重复文本键的覆盖与模板替换分别由各自入口处理。

编者建议先区分这份文件是谁读取，再决定数据放在哪里。结构相同的 JSON 不代表用途相同，配置和界面文字都需要自己的实际使用方。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadJSON`、`getString`；路径安全校验由读取入口内部执行。
- `data/strings/strings.json`：原版公开的本地化文本入口；
- `data/config/`：MOD 自有 JSON 资源目录；
