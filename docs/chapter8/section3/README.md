# 制作每帧战斗插件

持续战斗效果需要反复更新，`EveryFrameCombatPlugin` 提供这样的运行入口。本节制作一个计时插件，同时记录回调次数与非暂停时间：暂停后恢复时，两种记录会表现出不同变化，插件状态也随本场战斗保存和结束。

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 在 `settings.json` 登记每帧插件；
- 按 `amount` 和暂停状态推进数据；
- 管理本场实例与引擎引用；
- 根据日志确认计时和回调。

## 前置知识

- [C6-S4] 的构建与 JAR；
- [C7-S2] 的服务和战斗引用；
- [C8-S1] 的时间、场景与暂停；
- [C8-S2] 的对象和浮动文字。

## 创建并登记计时插件

创建 `src/data/scripts/gearin/combat/GearInPauseAwarePlugin.java`，继承 `BaseEveryFrameCombatPlugin`。基类提供默认实现，本例覆写 `advance(...)`，接收本帧 `amount` 与输入事件列表。完整类在后面给出。

在 `data/config/settings.json` 的 `plugins` 中加入局部登记：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

游戏启动时读取这个自有键与完整类名，每场战斗再创建对应实例。`package`、类声明与 JAR 条目共同提供这个名字，因此登记值要与它们一致。需要在已经运行的战斗中动态加入插件时，也可以使用 `engine.addPlugin(...)`。

本例将累计时间与计数器保存在实例字段中。由登记建立新实例之后，这场战斗从初始状态开始，下一场也会重新建立自己的记录。

## 暂停时保留调用记录

玩家按下暂停后，战斗状态应该停止推进，插件却仍会收到 `advance(...)`。本例用 `calls` 记录全部回调，用 `pausedCalls` 记录其中的暂停调用；累计时间另存于 `elapsed` 与 `timeSinceLastReport`。

在 `advance(...)` 开头加入以下局部片段。计数发生在暂停判断前，暂停帧记录完成后返回：

```java
calls++;
if (engine == null) {
    engine = Global.getCombatEngine();
}
if (engine.isPaused()) {
    pausedCalls++;
    return;
}
```

引擎在首次使用时通过 `Global.getCombatEngine()` 取得，之后保存到本场实例。这个绑定发生在战斗回调内，与当前战斗对应。

::: warning init 不保证先执行

`CombatEnginePlugin.init(...)` 不保证先于 `advance(...)`。编者建议将普通初始状态放在字段或构造器中，引擎则在首次使用时取得，避免第一次推进就依赖尚未写入的引用。

:::

## 累计时间并输出报告

**回调次数描述调用频率，`amount` 描述经过的秒数。** 帧率、时间倍率和运行情况都会影响步长，计时使用实际传入的增量。将以下局部片段放在暂停判断之后：

```java
elapsed += amount;
timeSinceLastReport += amount;
if (timeSinceLastReport >= REPORT_INTERVAL) {
    timeSinceLastReport = 0f;
    report(engine);
}
```

`elapsed` 保存本场累计时间，`timeSinceLastReport` 累计到 `REPORT_INTERVAL` 的 10 秒后执行报告，再从零开始计下一段间隔。暂停帧已经返回，所以暂停期间两者都保持原值，恢复后继续。

报告同时包含时间、全部调用次数和暂停调用次数。在 `report(...)` 中写入日志，并在有效玩家舰位置显示同一文字：

```java
String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
        + " (paused " + pausedCalls + ")";
Global.getLogger(GearInPauseAwarePlugin.class).info(text);
if (engine.getPlayerShip() != null) {
    engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
            14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
}
```

日志始终记录本次报告。玩家舰存在时，再将白色文字附着到它的位置，显示 3 秒。这样即使还没有可用玩家舰，计时结果仍然能够从日志取得。

## 让下一场从新状态开始

本场计时与引擎引用都属于当前插件。若将实例或字段保存在跨战斗的 `static`、外部对象中，下一场便可能继续使用旧记录。通过本节的 `settings.json` 登记，让游戏每场创建新实例，就能保持当前对象与当前计时对应。

功能需要主动结束时，以 `isCombatOver()` 等实际条件安排收尾。如果还登记了自己的监听器，先解除它们，再移除插件；这些登记各自有结束入口，下一节之后的监听示例会使用这套关系。

## 完整最小例

保存为 `src/data/scripts/gearin/combat/GearInPauseAwarePlugin.java`，登记采用上面的 `plugins` 条目。

::: details 完整成品：`GearInPauseAwarePlugin`

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 比较非暂停时间、总回调次数与暂停回调次数。 */
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {

    /** 非暂停时间每累计多少秒输出一次报告。 */
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        calls++;
        if (engine == null) {
            engine = Global.getCombatEngine();
        }
        if (engine.isPaused()) {
            pausedCalls++;
            return;
        }
        elapsed += amount;
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            report(engine);
        }
    }

    private void report(CombatEngineAPI engine) {
        String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
                + " (paused " + pausedCalls + ")";
        Global.getLogger(GearInPauseAwarePlugin.class).info(text);
        if (engine.getPlayerShip() != null) {
            engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
                    14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
        }
    }
}
```

:::

## 验证方案

1. 保存源码与登记，构建 JAR 并重启，进入有玩家舰的模拟战。
2. 正常运行约 15 秒，查看 `GearIn: elapsed` 报告与白色浮动文字。
3. 暂停约 20 秒，再恢复，比较 `elapsed`、`calls` 和 `pausedCalls`。
4. 结束后进入第二场，比较新实例的起始计时。
5. 在日志核对完整类名与当前回调结果。

预期结果：

- 每累计 10 秒报告一次，文字约 3 秒后消失。
- 暂停期间报告数量为 0，`calls` 和 `pausedCalls` 仍记录回调。
- 恢复后 `elapsed` 保持非暂停时间口径。
- 第二场从新实例状态开始，引用属于当前引擎。

## 故障分析

- **插件没有任何行为**：`settings.json` 缺少条目、文件路径错误，或 `key` 被其他来源覆盖时，游戏不会创建实例，日志和画面都没有 `GearIn:` 信号。
    - **解决方案**：确认文件位于 `data/config/settings.json`，`key` 全局唯一，值与 `package`、`class` 组成的完整类名一致，重新开战。

- **日志出现 `Problem loading class [...]`**：注册类名与 `package`、`class` 不一致，或源码没有进入 JAR 时，类加载器无法解析插件。
    - **解决方案**：让注册值、包声明和类名逐字一致，把源码放入 `src/data/scripts/gearin/combat/` 并重新构建 JAR。

- **暂停期间状态照常推进**：`advance` 在暂停检查前累计状态时，恢复后 `elapsed` 会包含暂停时间。
    - **解决方案**：把状态推进放在 `engine.isPaused()` 守卫之后，仅将 `calls++` 这类观测回调次数的计数放在守卫前。

- **第一次 `advance` 抛出空指针**：在已废弃的 `init(engine)` 中保存引擎并假设它先执行，会让首次 `advance()` 看到 `null`。
    - **解决方案**：初始状态放入构造器，引擎在使用时通过 `Global.getCombatEngine()` 获取，重新开战。

- **第二场战斗的数值接着上涨**：插件实例被保存到 `static` 字段或战斗外对象时，旧实例会跨战斗存活。
    - **解决方案**：只通过 `settings.json` 登记，让游戏每场创建新实例。

## 总结

暂停期间仍有回调，计时是否继续则由代码中的暂停判断决定。本例把调用计数放在判断前，把时间累计放在判断后，恢复时就能同时确认两种行为。

引擎与状态由本场实例保存，新的战斗从新的实例开始。后面的监听与绘制会继续使用这个运行入口，在相应对象上增加功能。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：逐帧、输入和绘制方法。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：默认实现。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：`init` 调用约定。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：暂停、`addPlugin`、`removePlugin` 和文字。
- `data/config/settings.json`：`plugins` 登记。
- `Starsector/starsector-core/starsector.log`：回调与计时。
