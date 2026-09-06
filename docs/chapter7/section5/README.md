# 记录和定位 MOD 日志

本节建立一套可复用的 MOD 日志写法，让启动、资源加载和运行阶段都能留下带上下文的排查信息。

*本节预期阅读时间：约 12 分钟。*

*本节预期操作时间：约 15 分钟。*

## 学习目标

完成后，您将能够：

- 取得与类对应的日志器并选择合适的日志级别；
- 为日志加入稳定前缀、对象 ID 和阶段信息；
- 记录异常堆栈并根据日志定位故障位置；
- 在 `starsector.log` 中核对启动、加载和运行结果。

## 前置知识

- [C6-S1] 至 [C6-S4] 的 Java 17、源码迁移、Gradle 和 JAR 构建；
- [C7-S1] 的 MOD 入口回调和日志验证；
- [C7-S2] 的 `Global` 服务访问阶段；
- Java 方法、字符串拼接和异常对象的基本用法。

## 取得日志器

在 Java 类中调用 `Global.getLogger(当前类.class)` 取得该类的日志器。日志器将内容写入 `Starsector/starsector-core/starsector.log`，类名会成为日志来源的一部分。

```java
private static final Logger LOG = Global.getLogger(GearInLogWriter.class);
```

日志器应作为类级常量复用。每个类使用自己的 `Class` 参数，便于从日志直接定位代码来源。

## 选择日志级别

正常阶段、对象创建和验证结果使用 `info`；流程仍可继续但需要检查的状态使用 `warn`；当前操作失败或捕获到异常时使用 `error`。`error` 调用必须同时传入异常对象，日志才会保留堆栈和原始原因。

```java
LOG.info("GearIn load: variant=GearIn_artizan_combat");
LOG.warn("GearIn state: sector not ready");
LOG.error("GearIn load failed: variant=GearIn_artizan_combat", exception);
```

## 加入稳定上下文

每条 MOD 日志必须以稳定的 `GearIn` 前缀开始，并写出当前阶段、对象 ID 或关键参数。固定前缀用于全文搜索，阶段和对象字段用于缩小核对范围；数值和布尔状态直接写入同一行，便于比较多次运行结果。

```java
String prefix = "GearIn " + stage + ": object=" + objectId + " ";
LOG.info(prefix + detail);
```

同一对象的多条日志使用相同 ID 和阶段名称。这样可以沿着一个 ID 对照加载、初始化和运行记录。

## 记录异常上下文

捕获异常时先写出正在处理的对象和阶段，再把异常作为最后一个参数传给 `error`。日志同时保留稳定上下文、异常类型和堆栈，排查时可以直接回到触发点。

```java
try {
    loadVariant("GearIn_artizan_combat");
} catch (RuntimeException exception) {
    GearInLogWriter.error("variant-load", "GearIn_artizan_combat",
            "variant could not be loaded", exception);
}
```

可恢复状态使用 `warn` 并写出下一步检查对象；失败路径使用 `error` 并保留异常。日志内容必须描述当前事实和可观察结果。

## 搜索运行日志

验证时打开 `Starsector/starsector-core/starsector.log`，使用 `GearIn` 前缀或对象 ID 搜索。启动阶段核对类加载和资源路径，生涯阶段核对对象 ID 与状态，战斗阶段核对运行参数和异常堆栈。每次修改后重新构建并清理旧日志，再比较新的记录。

日志行应包含足以复现判断的字段，例如：

```text
INFO GearIn load: object=GearIn_artizan_combat variant registered
WARN GearIn combat: object=GearIn_artizan_combat target unavailable
ERROR GearIn variant-load: object=GearIn_artizan_combat variant could not be loaded
java.lang.RuntimeException: ...
```

## 完整最小例

下面的工具类集中封装三个级别的日志写法。调用方把阶段、对象 ID 和具体内容传入，日志格式保持一致。

::: details 完整成品：GearInLogWriter

```java
package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

public class GearInLogWriter {

    private static final Logger LOG = Global.getLogger(GearInLogWriter.class);

    private GearInLogWriter() {
    }

    public static void info(String stage, String objectId, String detail) {
        LOG.info(format(stage, objectId, detail));
    }

    public static void warn(String stage, String objectId, String detail) {
        LOG.warn(format(stage, objectId, detail));
    }

    public static void error(String stage, String objectId, String detail, Throwable cause) {
        LOG.error(format(stage, objectId, detail), cause);
    }

    private static String format(String stage, String objectId, String detail) {
        return "GearIn " + stage + ": object=" + objectId + " " + detail;
    }
}
```

:::

## 验证方案

前置数据：已按 [C6-S1] 至 [C6-S4] 建立 Java 工程，并准备可写入 `starsector.log` 的测试环境。

1. 编译包含 `GearInLogWriter` 的 JAR，并确认 class 条目位于 `data/scripts/gearin/GearInLogWriter.class`。
2. 在已有入口或脚本中分别调用 `info`、`warn` 和 `error`，传入同一个对象 ID 与三个阶段名称。
3. 启动游戏并打开 `Starsector/starsector-core/starsector.log`，搜索 `GearIn` 和测试对象 ID。
4. 对照日志级别、阶段、对象 ID、消息内容和异常堆栈。

预期结果：三种级别均写入稳定前缀、阶段和对象 ID；`error` 行后保留异常类型与堆栈；日志搜索可以定位到调用阶段和对象。

## 故障分析

- **日志中找不到 MOD 前缀**：调用使用了其他日志器或消息没有稳定的 `GearIn` 前缀。

    - **解决方案**：通过 `Global.getLogger(当前类.class)` 创建日志器，并统一使用 `GearIn` 前缀。

- **日志有消息但无法定位对象**：消息缺少阶段、对象 ID 或关键参数。

    - **解决方案**：调用日志方法时补齐阶段和对象 ID，把关键状态拼接到同一行。

- **错误日志没有异常堆栈**：`error` 只传入字符串，没有传入 `Throwable`。

    - **解决方案**：使用 `LOG.error(message, cause)`，保留捕获到的异常对象。

- **日志显示旧状态**：修改后的 JAR 没有重新构建或游戏仍读取旧产物。

    - **解决方案**：重新执行 `gradle jar`，确认 `jar tf` 的 class 条目和 `mod_info.json` 的 JAR 路径。

## 总结

`Global.getLogger` 为每个类提供可定位的日志器。`info`、`warn` 和 `error` 分别记录正常结果、需检查状态和失败路径；稳定的 `GearIn` 前缀、阶段和对象 ID 让日志可以全文搜索和逐次比较，异常对象则保留完整堆栈。

## 建议参考项

- `com.fs.starfarer.api.Global.getLogger(Class)`：取得 MOD 日志器；
- `org.apache.log4j.Logger`：`info`、`warn` 和 `error` 方法；
- `Starsector/starsector-core/starsector.log`：读取日志和异常堆栈。
