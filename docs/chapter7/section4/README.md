# 记录和定位 MOD 日志

日志需要让一次结果能够追溯到实际操作。本节从加载与运行记录出发，说明来源、级别、对象 ID 和异常堆栈怎样配合，再将消息格式整理为工具类。代码能够输出文字之后，还要决定这些文字能否帮助定位问题。

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 学习目标

完成后，您将能够：

- 取得类对应的日志器；
- 按事实选择 info、warn 和 error；
- 用稳定前缀、阶段与对象 ID 组织消息；
- 保留异常堆栈并定位失败操作；
- 比较本次启动和运行记录。

## 前置知识

- [C6-S4] 的构建与加载；
- [C7-S1] 的生命周期日志；
- [C7-S2] 的服务与阶段；
- Java 字符串、异常对象与 try／catch。

## 从记录定位本次失败

打开 `Starsector/starsector-core/starsector.log`，搜索 `GearIn` 或当前对象 ID。重新构建并启动之后，将记录对应到本次启动：应用阶段涉及类和资源，生涯阶段涉及存档对象，战斗阶段则涉及实体与参数。

本节采用的消息形如：

```text
INFO GearIn load: object=GearIn_artizan_combat variant registered
WARN GearIn combat: object=GearIn_artizan_combat target unavailable
ERROR GearIn variant-load: object=GearIn_artizan_combat variant could not be loaded
java.lang.RuntimeException: ...
```

前缀与对象 ID 用于找到相关记录，阶段说明当时正在做什么。发生异常时还需要完整堆栈，才能追到失败的调用。**定位从首个相关异常开始**，后面的错误可能只是前面的失败继续产生的结果。

下面的写法就为这些信息提供对应位置。

## 为功能类取得日志器

在当前类中创建日志器：

```java
private static final Logger LOG = Global.getLogger(GearInLogWriter.class);
```

`Global.getLogger(...)` 按 Class 取得 Log4j 日志器，输出进入前述日志文件。类名提供代码来源，消息中的 ID 提供本次处理的对象。

**功能类使用自己的日志来源。** 需要统一格式时可以使用工具类，但调用方仍要提供阶段和对象。本节的辅助类会集中输出来源，消息中因此保留这些业务信息。

## 用级别说明发生了什么

### 日志级别速查表

| 级别  | 使用场合                           |
| ----- | ---------------------------------- |
| info  | 阶段完成、对象创建和需要观察的结果 |
| warn  | 流程继续，但存在需要核对的状态     |
| error | 当前操作失败或捕获异常             |

记录正常加载、需要核对的状态和加载失败时，分别使用：

```java
LOG.info("GearIn load: variant=GearIn_artizan_combat");
LOG.warn("GearIn state: sector not ready");
LOG.error("GearIn load failed: variant=GearIn_artizan_combat", exception);
```

这里的 `error` 同时传入消息与异常对象。消息说明加载哪个装配，异常堆栈说明加载在哪个调用中失败。只输出 `exception.getMessage()`，会丢失这部分调用信息。

**编者认为警告也应当有分量。** 正常加载阶段缺少 Sector，可以记录为阶段信息；只有操作确实需要它却没有可用对象时，再按失败处理。警告积累过多，真正需要检查的记录反而容易被掩盖。

## 将消息对应到具体操作

日志以 `GearIn` 开头，随后写阶段、对象 ID 与结果。局部格式如下：

```java
String prefix = "GearIn " + stage + ": object=" + objectId + " ";
LOG.info(prefix + detail);
```

**固定格式方便搜索，具体字段解释本次操作。** 装配日志保留 `variantId`，舰船日志保留 `hullId`，生命周期日志保留回调。需要比较数值时，将相关输入与结果写在同一行，就能沿同一个 ID 追踪加载、初始化与运行过程。

消息内容随任务选择。只有一个脱离对象的数字，即使打印得再精确，也无法说明它属于哪次计算。

## 在异常发生处保留上下文

例如装配加载失败时，捕获位置同时知道本次请求的 ID 与实际异常。把两者交给日志工具，再沿原调用链处理失败：

```java
try {
    Global.getSettings().getVariant("GearIn_artizan_combat");
} catch (RuntimeException exception) {
    GearInLogWriter.error("variant-load", "GearIn_artizan_combat",
            "variant could not be loaded", exception);
    throw exception;
}
```

这段片段记录后重新抛出原异常，加载失败继续由调用链处理。若某种业务状态允许继续运行，后续动作则由该功能决定。日志保存发生的事实，捕获异常之后是否继续，仍是业务代码的职责。

## 完整最小例

工具类保存为 `src/data/scripts/gearin/GearInLogWriter.java`，调用方传入阶段、对象与信息。

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

工具统一 `info`、`warn` 与 `error` 的消息格式，调用方提供实际阶段、ID 和结果。直接使用功能类的日志器时，也可以沿用这套字段。回到前面的日志例子，来源、对象与堆栈便都有了明确的写入位置。

## 验证方案

1. 保存工具类，构建 JAR 并核对 class 条目。
2. 在既有入口的当前操作中调用 info，记录阶段、ID 与一个实际结果。
3. 在业务可继续的异常状态处调用 warn，明确当前条件。
4. 在可复现的失败操作捕获处用 error 保留异常，再按实际处理逻辑继续或抛出。
5. 运行对应场景，搜索前缀和对象 ID，比较消息与堆栈来源。

预期结果：

- 三种级别表达各自实际状态，字段与对象对应。
- 捕获异常的 error 记录包含原异常和堆栈。
- 前缀、阶段、ID 和关键参数可以定位实际调用。
- 本次构建与运行使用当前产物，日志与修改对应。

## 故障分析

- **找不到 MOD 消息**：前缀、级别过滤或实际调用未对应。
    - **解决方案**：核对当前日志器、消息和日志配置，运行实际触发点。

- **消息无法确定对象**：只记录笼统结果，缺少阶段和 ID。
    - **解决方案**：补齐当前操作的对象与关键参数。

- **error 消息缺少堆栈**：调用只传入文字，异常对象未记录。
    - **解决方案**：传入实际 Throwable 作为第二参数。

- **仍显示旧结果**：产物或加载路径采用旧代码。
    - **解决方案**：重新构建并核对 JAR 与 mod_info 路径，再启动场景。

## 总结

从消息定位对象，从级别判断状态，再通过异常堆栈追到失败调用。类日志器与稳定格式帮助检索，实际输入和结果解释发生的过程。

后续验证继续按对象与回调记录。能够把结果对应到输入、调用和文件，日志才提供了排错所需的信息。

## 建议参考项

- `com.fs.starfarer.api.Global`：getLogger 取得类日志器。
- `org.apache.log4j.Logger`：info、warn 与 error。
- `Starsector/starsector-core/starsector.log`：运行日志与异常堆栈。
