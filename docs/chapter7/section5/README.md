# 记录和定位 MOD 日志

此前各类回调已经输出日志，本节整理正常记录、状态提示与异常堆栈的写法。类名提供代码来源，阶段与对象 ID 提供当前上下文；遇到加载或运行问题时，用这些信息追到实际调用位置。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~15 分钟。*

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

## 让日志来源对应实际类

在当前类中创建日志器：

```java
private static final Logger LOG = Global.getLogger(GearInLogWriter.class);
```

Global.getLogger 按 Class 取得 Log4j 日志器，输出进入 `Starsector/starsector-core/starsector.log`。类名作为来源信息，可以与消息里的业务对象对应。

**每个功能类使用自己的日志来源**。工具类可以统一消息格式，但调用记录仍要提供足够上下文，让读者看到是哪一阶段、处理哪个对象。

## 按当前事实选择级别

### 日志级别速查表

| 级别 | 使用场合 |
|---|---|
| info | 阶段完成、对象创建和需要观察的结果 |
| warn | 流程继续，但存在需要核对的状态 |
| error | 当前操作失败或捕获异常 |

下面的局部片段分别记录加载、阶段与失败：

```java
LOG.info("GearIn load: variant=GearIn_artizan_combat");
LOG.warn("GearIn state: sector not ready");
LOG.error("GearIn load failed: variant=GearIn_artizan_combat", exception);
```

有实际异常对象时，作为第二参数传给 error，堆栈才会保留。消息说明正在处理什么，异常说明实际失败在哪里，二者一起使用。

编者建议把正常状态与失败区分清楚。正常的加载阶段缺少 Sector 可以记录为阶段信息；只有操作确实需要它却没有可用对象时，再按当前失败处理。否则 warning 堆多了，真正值得检查的记录反而难找。

## 写出稳定的上下文

当前日志以 GearIn 开头，再放阶段、对象 ID 和实际结果：

```java
String prefix = "GearIn " + stage + ": object=" + objectId + " ";
LOG.info(prefix + detail);
```

**前缀用于找到 MOD，阶段和对象用于找到具体操作。** 加载、初始化与运行可以沿同一个对象 ID 追踪；比较数值时把相关参数写在一行，避免只输出一个没有归属的数字。

例如记录装配时写 variantId，记录舰船时写 hullId，记录回调时写当前阶段。格式保持稳定，字段按实际判断需要选择。

## 在捕获位置保留异常

已有加载操作失败时，把上下文与捕获到的异常一起记录，局部片段如下：

```java
try {
    Global.getSettings().getVariant("GearIn_artizan_combat");
} catch (RuntimeException exception) {
    GearInLogWriter.error("variant-load", "GearIn_artizan_combat",
            "variant could not be loaded", exception);
    throw exception;
}
```

这段记录不改变失败结果，当前异常继续交给调用链处理。可继续的业务状态按功能决定后续动作，日志本身只是说明事实。

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

这个工具统一 info、warn 和 error 的格式。需要直接使用功能类日志器时，也可以沿用同一消息约定。

## 搜索本次运行

打开当前 starsector.log，搜索 GearIn 或具体 ID。应用阶段查看类和资源，生涯查看存档对象，战斗查看实体、参数与堆栈。重新构建和启动后，按当前启动位置区分记录。

可观察日志形如：

```text
INFO GearIn load: object=GearIn_artizan_combat variant registered
WARN GearIn combat: object=GearIn_artizan_combat target unavailable
ERROR GearIn variant-load: object=GearIn_artizan_combat variant could not be loaded
java.lang.RuntimeException: ...
```

**先从首个相关异常追起**，后面的错误可能由前面的失败继续产生。日志包含调用来源、阶段与目标后，再对照实际文件和数据，就能把问题落到当前链上的位置。

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

类日志器提供来源，级别说明当前状态，消息字段说明阶段和对象，异常对象提供失败堆栈。稳定格式让记录可搜索，具体内容则由当前操作的实际判断决定。

后续验证继续沿对象与回调记录。日志量不是判断精度，能把一次结果追到对应输入、调用和文件，才是这套写法的作用。

## 建议参考项

- `com.fs.starfarer.api.Global`：getLogger 取得类日志器。
- `org.apache.log4j.Logger`：info、warn 与 error。
- `Starsector/starsector-core/starsector.log`：运行日志与异常堆栈。
