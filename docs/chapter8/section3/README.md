# 制作每帧战斗插件

前两节已经运行状态和对象观察，本节详细解释每帧插件怎样登记、怎样保存本场状态，以及暂停时哪些代码仍会调用。示例同时累计回调次数和非暂停时间，用日志与浮动文字比较两者。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

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

## 选择持续战斗入口

`BaseEveryFrameCombatPlugin` 提供默认方法。`advance` 接收本次 `amount` 和输入事件列表，适合更新持续效果、计时器或状态观察。

**每帧是调用频率，`amount` 才是经过的秒数。** 当前帧率、时间倍率与运行情况都会影响它，计时逻辑累计真实增量。实例字段保存本场状态，下一场由新实例重新开始。

`settings` 的局部登记如下：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

`key` 为自有唯一名称，值为完整类名。启动时读取注册，每场战斗创建实例；当前战斗中也可用 `engine.addPlugin` 动态加入。值与 `package`、`class` 和 JAR 条目保持一致。

## 把回调次数与推进时间分开

当前类保存 `elapsed`、`timeSinceLastReport`、`calls` 和 `pausedCalls`。`calls` 在暂停判断前增加，`elapsed` 在判断后推进。

在 `advance` 开头加入局部片段：

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

`CombatEnginePlugin` 的 `init` 不保证先于 `advance`，因此当前首次使用时取得引擎。本方法已处于战斗回调，随后按暂停状态决定推进。

**暂停可以阻止当前代码推进状态，但它没有替换这次回调。** 示例特意保留 `calls` 计数，用来观察暂停时也收到调用。

## 累计非暂停时间

暂停判断之后执行：

```java
elapsed += amount;
timeSinceLastReport += amount;
if (timeSinceLastReport >= REPORT_INTERVAL) {
    timeSinceLastReport = 0f;
    report(engine);
}
```

`REPORT_INTERVAL` 为 10 秒，两份时间都只累计非暂停 `amount`。周期报告输出当前累计值与两种调用数，恢复后继续从暂停前状态推进。

在 `report` 中用日志保存结果，并在有效玩家舰位置显示同一文字：

```java
String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
        + " (paused " + pausedCalls + ")";
Global.getLogger(GearInPauseAwarePlugin.class).info(text);
if (engine.getPlayerShip() != null) {
    engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
            14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
}
```

报告与实际玩家舰是否存在分开处理。日志总能记录计时，浮动文字则在可用实体位置显示 3 秒。

## 按实例管理当前战斗

当前字段属于插件实例。把它们保存到跨战斗 `static` 或外部对象，下一场就可能继续旧计时和旧引擎。需要收尾时按 `isCombatOver` 处理，自己注册的监听器先解除，再移除当前插件。

编者建议先把一个持续功能的创建、推进和结束安排完整，再扩大到其他对象。时间和引用的范围正确，效果才不会在第二场变成另一种表现。

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

每帧插件通过 `settings` 登记，当前实例保存本场字段，`advance` 按 `amount` 推进。暂停检查的位置决定状态是否累计，回调次数和经过时间则分别记录。

后面的监听与渲染会继续借用这个运行入口。把注册、实例、推进和收尾对应好，再增加实际功能，就能保持各场战斗的行为一致。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：逐帧、输入和绘制方法。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：默认实现。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：`init` 调用约定。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：暂停、`addPlugin`、`removePlugin` 和文字。
- `data/config/settings.json`：`plugins` 登记。
- `Starsector/starsector-core/starsector.log`：回调与计时。
