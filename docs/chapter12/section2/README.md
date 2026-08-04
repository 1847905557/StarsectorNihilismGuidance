# 读取 MOD 内 JSON 文件

本节先说明游戏如何处理固定的 `strings.json` 入口，再回到 MOD 自己定义的 JSON 文件。这样既能看清原版文本表的加载和取值规则，也能知道什么时候需要用 `loadJSON` 主动读取自己的数据。

*本节预期阅读时间：~24 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 区分原版固定公开入口与 MOD 自有 JSON 路径；
- 说明 `strings.json` 的加载、合并、分类和取值方式；
- 说明 JSON 注释、尾逗号、编码和字段大小写边界；
- 使用 `SettingsAPI.loadJSON` 读取 MOD 自有 JSON；
- 从路径、BOM、JSON 语法、字段类型和缺失文本定位故障。

## 前置知识

- C12-S01 的文件读取概念、MOD 相对路径和 `modId`；
- C01-S01 的 JSON 基础与 UTF-8 无 BOM 要求；
- C06-S03 的 `Global.getSettings()` 和日志入口。

## 固定入口与自有路径

`data/strings/strings.json` 是原版固定公开入口。游戏在启动资源加载阶段主动查找原版与全部已启用 MOD 的同名文件，并按 strings 规则处理内容；MOD 只需把文件放在该路径，不需要在 `mod_info.json` 中声明，也不需要调用 `loadJSON` 注册。

其他 JSON 路径由 MOD 自己约定，例如 `data/config/gear_in/services.json`。游戏不会猜测这些文件的用途；代码必须给出路径并主动调用 `loadJSON`，读取后再解释字段。

## strings 文件的加载与合并

字符串表在启动资源加载阶段自动载入。日志先出现 `Loading strings`，随后加载 `data/strings/tooltips.json`、`data/strings/strings.json` 和 `data/strings/tips.json`。原版与各个已启用 MOD 的 `strings.json` 分别产生一行 `Loading JSON from [...]`，日志路径包含 `data/strings/strings.json`。

各来源的 `strings.json` 按启用顺序合并：

- 原版内容先进入结果，后续 MOD 依次合并；
- 对象值按键递归合并；
- 标量值由后加载来源覆盖前一个来源；
- 数组按键的合并规则处理，`music_*`、长度为 4 的 `color` 或 `button` 数组，以及首元素为 `core_clearArray` 的数组可以整体替换；
- `mod_info.json` 的 `fullOverrides` 可以整体替换某个文件。

MOD 只需要写自己的键，原版和其他 MOD 的键会继续保留。相同 `(category, id)` 的文本会受启用顺序影响，因此自有分类和键应使用 MOD 前缀，避免覆盖已有文本。

## 语法与编码

加载前，引号之外的 `#` 到行尾内容会被剥离，再交给 JSON 解析器；尾逗号也会被接受。括号、字符串引号和字段结构仍须符合 JSON 解析要求。

文件必须保存为 UTF-8 无 BOM。BOM 会粘在文件首字符并使加载失败；括号不配对或字符串引号不闭合会在加载阶段抛出带文件路径的 `JSONException`，启动随之中止。

分类名和键名逐字符匹配并区分大小写。分类或键不存在时，双参取值 API 返回 `Missing string: [<id>]`，并在 `starsector.log` 记录 ERROR；游戏继续运行，界面会显示该字面量。

## 取值 API 与占位符

运行期读取 strings 文本使用公开 API：

```java
String text = Global.getSettings().getString("GearIn_ui", "GearIn_text_hello");
```

双参 `getString(category, id)` 读取 `strings.json`。单参 `getString(key)` 读取 `data/config/settings.json`，两者的文件和键空间不同。

strings 的值可以包含两类占位符：

- `$token` 不会由引擎自动替换，使用方必须在取值后替换；
- `%s` 不会由引擎自动格式化，取值侧使用 `String.format` 传入参数。

例如，`$fleetName` 需要由舰队遭遇文本的使用方替换；`%s` 的参数数量和类型必须与模板一致。

## 原版分类与同目录文件

原版 `strings.json` 有 4 个顶层分类：

| 分类 | 内容 | 键风格 | 主要使用方 | 键数 |
|---|---|---|---|---:|
| `fleetInteractionDialog` | 生涯舰队遭遇对话：交战、追击、撤退、接舷、打捞和友军增援 | 大量 `$faction`、`$fleetName`、`$fleetOrShip` 等令牌 | 舰队遭遇对话 | 160 |
| `campaignHelp` | 首次生涯提示和低 CR、低补给等提示 | 键后缀使用 `_text`、`_dismiss` 或 `_action` | 生涯帮助弹窗 | 63 |
| `jumpPointInteractionDialog` | 跳跃点交互：`approach`、`starAnchor`、`noFuel`、`noExits` 等 | `$fleetOrShip` | 跳跃点交互对话 | 5 |
| `planetInteractionDialog` | 行星接近文本：`approach` | `$fleetOrShip`、`$planetName` | 行星交互对话 | 1 |

MOD 可以使用自己的分类，不需要复用原版分类名。对话类值依靠 `$token` 注入动态内容，帮助类值可以使用 `\n` 组织多段文本。

`data/strings/tooltips.json` 与 `strings.json` 不合并，键空间独立；值可以是纯字符串，也可以是包含 `title` 和 `body` 的对象。`data/strings/tips.json` 保存加载界面的随机小贴士。两者都不能用双参 `getString` 读取。单参 `getString(key)` 的目标是 `data/config/settings.json`。

## 读取自有 JSON

```java
JSONObject config = Global.getSettings().loadJSON(
        "data/config/gear_in/services.json");
JSONObject configFromMod = Global.getSettings().loadJSON(
        "data/config/gear_in/services.json", "gear_in");
```

单参形式从当前资源空间寻找文件；双参形式把来源限定为指定 MOD。`modId` 必须与 `mod_info.json` 的 `id` 逐字符一致，且该 MOD 已启用。

`loadJSON` 返回 `JSONObject`。读取成功只说明文件可以解析，字段存在性与类型仍由后续访问判断：

```java
JSONObject services = Global.getSettings().loadJSON(
        "data/config/gear_in/services.json");
String serviceId = services.getString("serviceId");
int interval = services.optInt("interval", 10);
```

JSON 路径继续遵守 C12-S01 的资源路径规则。`validateFileName` 会拒绝 `..`、反斜杠、冒号和以 `/` 开头的路径。`getString` 等强制取值方法在字段缺失或类型不符时抛出异常；允许字段缺失时使用 `optString`、`optInt` 等入口并提供明确的替代值。

### 自有 JSON 最小实例

在 `data/config/gear_in/services.json` 写入：

```json
{
  "serviceId": "GearIn_service",
  "interval": 10
}
```

入口类在 `onApplicationLoad` 中读取文件，并把 `serviceId` 与 `interval` 写入日志。该文件是 MOD 自有配置，不参与 `strings.json` 的固定加载与合并。

### 验证方案

前置数据：`gear_in` MOD、`services.json` 和入口类读取片段已就绪。

1. 启动游戏，观察 `starsector.log` 中入口类写入的 JSON 读取日志。
2. 使用单参 `loadJSON` 读取 `data/config/gear_in/services.json`，观察 `serviceId` 和 `interval`。
3. 将调用改为双参形式并传入 `gear_in`，再次启动并观察相同字段。

预期结果：

- JSON 返回 `GearIn_service` 和数值 `10`；
- 双参调用只读取 `gear_in` 的资源；
- 路径、编码、语法和字段类型正确时，启动日志没有 JSON 读取异常。

清理：删除入口类中的临时日志探针。

## strings.json 验证

前置数据：原版和 `gear_in` 的 `data/strings/strings.json` 已存在，文件包含一个同名键和一个只由 `gear_in` 提供的新键。

### 验证方案

1. 启动游戏，观察 `Loading strings` 和包含 `data/strings/strings.json` 的加载日志。
2. 在界面或日志中读取一个原版分类的已知键，再读取 `gear_in` 自有分类的新键。
3. 让 `gear_in` 提供与原版相同的 `(category, id)`，重新启动并观察最终文本。
4. 将分类名或键名改成大小写不同的写法，再读取该文本。

预期结果：

- 原版与 `gear_in` 的 strings 文件都在启动阶段被加载；
- `gear_in` 自有键可以通过双参 `getString` 读取；
- 相同键由后加载来源覆盖；
- 分类或键大小写不一致时返回 `Missing string: [<id>]`，日志记录 ERROR。

清理：删除测试 MOD 中与原版冲突的键和日志探针，恢复测试前的 `strings.json`。

## 故障分析

- JSON 在首字符处解析失败：文件可能带 BOM。以 UTF-8 无 BOM 保存。
- JSON 解析报告括号或字符串错误：对象结构、引号或逗号不符合解析要求。检查错误位置附近的字段。
- 字段读取失败：文件已经读入，但字段缺失或类型不符。核对字段名和类型；允许缺失时使用对应的 `opt` 方法。
- 双参调用的 `modId` 写错：找不到 MOD 规格，调用点出现空对象异常。核对 `mod_info.json` 的 `id` 和启用状态。
- 自有 JSON 放入固定路径后没有自动产生业务结果：固定路径只决定游戏加载文件，不会替自有字段建立使用方。用 `loadJSON` 主动读取并解释内容。
- 相同 strings 键的文本与预期不同：后加载来源覆盖了先前内容。给自有分类和键增加 MOD 前缀。
- `strings.json` 返回 `Missing string: [...]`：分类或键逐字符不一致，或文件没有放在固定路径。检查大小写、路径和 MOD 启用状态。

## 总结

原版固定入口负责加载 `strings.json`，双参 `getString` 负责按分类和键取值；MOD 自有 JSON 则通过 `loadJSON` 主动读取。两条路径都依赖正确的文件位置、编码、结构和字段名。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadJSON`、`getString` 和 `validateFileName`；
- `data/strings/strings.json`：原版公开的本地化文本入口；
- `data/config/`：MOD 自有 JSON 资源目录；
- C12-S05：`GearInI18n` 本地化实现。
