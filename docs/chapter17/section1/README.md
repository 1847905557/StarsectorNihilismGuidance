# 读取 MOD 内文本

前面的配置多由游戏按固定路径读取，这次让代码主动取一份自有文本。我们把工坊说明放到 MOD 资源目录，在应用加载时读回并记录。文件来源、相对路径与编码先确定，后面的 JSON 和 CSV 再沿同样的入口解释结构。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~25 分钟。*

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

## 创建资源并接到应用加载

先在 MOD 的 `data/config/gear_in/notes.txt` 写入以下文本：

```text
GearIn file loading is ready.
```

向 [C7-S1] 的 `GearInModPlugin.onApplicationLoad(...)` 追加局部读取，既有代码保留；该方法已声明 `throws Exception`，当前读取异常沿应用加载报告：

```java
String notes = Global.getSettings().loadText("data/config/gear_in/notes.txt", "gear_in");
Global.getLogger(GearInModPlugin.class).info("GearIn notes: " + notes);
```

**资源内容可以独立于 JAR 修改，代码决定何时读它、怎样使用它。**配置、文本模板和其他易变数据因此可以离开 Java 类；调整资源文件后，只需让游戏重新加载资源，无需重新编译 JAR，同一份数据也可以交给多个代码入口使用。

本例先按字符串使用内容，JSON 与 CSV 再添加结构。需要运行期保存时，common 使用另一组读写入口；资源路径继续表达 MOD 发布的输入文件。

## 读取文本

MOD 资源路径从 MOD 根目录开始，使用 `/` 分隔，例如 `data/config/gear_in/notes.txt`。路径不包含盘符、反斜杠、`..` 或首字符 `/`。

```java
String notes = Global.getSettings().loadText("data/config/gear_in/notes.txt");
String notesFromMod = Global.getSettings().loadText(
        "data/config/gear_in/notes.txt", "gear_in");
```

单参形式从当前资源空间寻找文件；双参形式把来源限定为指定 MOD。当前 `modId` 对应 `mod_info.json` 的 `gear_in`，游戏按这个已启用 MOD 选择来源。

`loadText` 返回文件的原始文本。引擎按 UTF-8 解码并移除 `\r`，不会替您剥离 BOM；因此含中文的资源文件保存为 UTF-8 无 BOM。读取失败由调用侧记录上下文，日志不会自动替您说明文件的业务用途。

### 路径与编码边界

引擎在读取前拒绝 `..`、反斜杠、冒号和以 `/` 开头的路径。资源路径使用 `/`，文件位于 MOD 目录内；绝对路径 (含盘符) 不属于资源读取入口。

文件带 BOM 时，BOM 会进入返回文本的首字符。文件找不到、路径非法和 MOD ID 找不到分别在读取调用点产生异常，排查时先核对入口、相对路径和文件编码。

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

工坊说明从 MOD 根目录相对路径取得，当前用来源限定选择 `gear_in`，日志记录实际返回文本。编码与路径处理发生在读取，内容怎样使用则由当前代码决定。

编者建议先让一份文本读回正确，再增加结构和共享来源。相同路径可以由不同 MOD 提供，来源写清以后，调整文件的结果才容易比较。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadText`；路径安全校验由引擎在读取入口自动执行。
- `data/config/`：MOD 自有文本资源可以使用的目录；
- `mod_info.json`：MOD ID 来源。
