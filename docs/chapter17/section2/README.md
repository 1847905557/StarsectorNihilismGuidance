# 读取 MOD 内 JSON 文件

服务配置和界面文字都可以写成 JSON，但游戏取得它们的方式各有约定。本节先主动读取工坊服务的字段，再把一句问候放进自动加载的 strings 表。文件放在哪里、谁读取它、同名内容怎样合并，分别决定这两份数据如何生效。

_本节预期阅读时间：\~24 分钟。_

_本节预期操作时间：\~45 分钟。_

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
