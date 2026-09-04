# 读取 MOD 内文本

本节让 Java 主动读取一份 MOD 文本资源，把容易变化的数据从编译后的类中分离出来。这里建立的资源来源、路径和编码边界同样适用于 [C16-S2] 的 JSON、[C16-S3] 的 CSV 与 [C16-S4] 的 common 数据。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说明主动读取 MOD 文件对数据组织和迭代的作用；
- 使用 `SettingsAPI.loadText` 读取 MOD 内文本；
- 写出 MOD 根目录相对路径和 `/` 分隔规则；
- 区分单参读取与 `modId` 限定读取；
- 从路径错误、BOM 和 MOD ID 错误定位文本读取故障。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 和 UTF-8 无 BOM 要求；
- [C6-S1] 的 Java 构建与入口类；
- [C7-S2] 的 `Global.getSettings()` 和日志入口。

## 为什么先读取文本

前面章节使用的许多数据文件都由游戏在固定路径自动加载。主动读取则把选择权交给 MOD：代码决定何时读取哪个路径，也决定怎样解释返回内容。配置、文本模板和其他易变数据因此可以离开 Java 类；调整资源文件后，只需让游戏重新加载资源，无需重新编译 JAR，同一份数据也可以交给多个代码入口使用。

文本是这条主线最干净的起点。`loadText` 只暴露资源空间、路径和编码，JSON 与 CSV 在此基础上增加结构解析，common 再增加运行期写入与持久化；本节先固定这三个文件边界。

## 读取文本

MOD 资源路径从 MOD 根目录开始，使用 `/` 分隔，例如 `data/config/gear_in/notes.txt`。路径不包含盘符、反斜杠、`..` 或首字符 `/`。

```java
String notes = Global.getSettings().loadText("data/config/gear_in/notes.txt");
String notesFromMod = Global.getSettings().loadText(
        "data/config/gear_in/notes.txt", "gear_in");
```

单参形式从当前资源空间寻找文件；双参形式把来源限定为指定 MOD。`modId` 必须与 `mod_info.json` 的 `id` 逐字符一致，且该 MOD 已启用。

`loadText` 返回文件的原始文本。引擎按 UTF-8 解码并移除 `\r`，不会替您剥离 BOM；因此含中文的资源文件保存为 UTF-8 无 BOM。读取失败由调用侧记录上下文，日志不会自动替您说明文件的业务用途。

### 路径与编码边界

引擎在读取前拒绝 `..`、反斜杠、冒号和以 `/` 开头的路径。资源路径使用 `/`，文件位于 MOD 目录内；绝对路径 (含盘符) 不属于资源读取入口。

文件带 BOM 时，BOM 会进入返回文本的首字符。文件找不到、路径非法和 MOD ID 找不到分别在读取调用点产生异常，排查时先核对入口、相对路径和文件编码。

### 最小实例

在 `data/config/gear_in/notes.txt` 写入：

```text
GearIn file loading is ready.
```

入口类在 `onApplicationLoad` 中读取该文件并把返回文本写入日志。代码只保存读取结果，不修改 MOD 目录中的源文件。

### 验证方案

前置数据：`gear_in` MOD、`notes.txt` 和入口类读取片段已就绪。

1. 启动游戏，观察 `starsector.log` 中入口类写入的读取日志。
2. 使用单参 `loadText` 读取 `data/config/gear_in/notes.txt`，观察返回文本。
3. 将读取调用改为双参形式并传入 `gear_in`，再次启动并观察返回文本。

预期结果：

- 两次读取都返回 `GearIn file loading is ready.`；
- 双参调用只读取 `gear_in` 的资源；
- 路径、来源和编码正确时，启动日志没有文本读取异常。

清理：删除入口类中的临时日志探针。

## 故障分析

- **路径校验抛出异常**：路径含 `\\`、`..`、盘符或首字符 `/`。

    - **解决方案**：改用 MOD 根目录下以 `/` 分隔的相对路径，再调用 `loadText`。

- **双参读取出现空对象异常**：传入的 `modId` 与已启用 MOD 不一致。

    - **解决方案**：核对 `mod_info.json` 的 `id` 和启用状态。

- **返回文本的首字符异常**：源文件带有 BOM。

    - **解决方案**：以 UTF-8 无 BOM 重新保存。

- **读取到另一份同名文件**：资源路径过于通用，合并后的文件覆盖了目标。

    - **解决方案**：使用带 MOD 前缀的目录或双参入口限定来源。

## 总结

文件读取先确定资源空间和相对路径，再把文件内容交给代码。`loadText` 返回字符串；路径校验、编码和 `modId` 决定能否取得目标文件。JSON 对象的读取与固定公开入口见 [C16-S2]。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadText`；路径安全校验由引擎在读取入口自动执行。
- `data/config/`：MOD 自有文本资源可以使用的目录；
- `mod_info.json`：MOD ID 来源。
