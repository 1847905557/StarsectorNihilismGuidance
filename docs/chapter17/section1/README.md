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
- [C6-S4] 的构建与游戏加载；
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
