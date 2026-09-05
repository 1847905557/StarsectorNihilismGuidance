# 制作每帧战斗插件

本节制作第一个每帧战斗插件，并在 `data/config/settings.json` 中完成注册。示例会把回调写入日志，用来确认插件生命周期和暂停时的推进方式。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/config/settings.json` 的 `plugins` 段注册每帧插件；
- 在 `advance` 中按战斗和暂停状态推进数据；
- 根据日志和运行表现定位注册、暂停与实例生命周期故障。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C4-S1] 的测试战役入口与战斗验证方法；
- [C6-S1] 的 Java 构建、JAR 输出与入口类；
- [C7-S2] 的 `Global` 生命周期、战斗引擎实例边界与跨战斗引用规则；
- [C8-S1] 的战斗引擎状态、时间与暂停判断；
- [C8-S2] 的战斗对象读取与当前战斗引用规则。

## 让代码持续参与战斗

武器效果、计时器和状态监测都需要在战斗持续期间反复执行。每帧战斗插件 `BaseEveryFrameCombatPlugin` 为这类逻辑提供统一入口：游戏推进战斗时调用插件的 `advance(float amount, List<InputEventAPI> events)`，插件便能读取当前状态并更新自己的数据。

::: tip 帧率与时间步长

正常速度下每秒约 60 帧。`amount` 是距上一帧经过的秒数，实际值随时间倍率和机器运行情况变化。

:::

```text
advance(float amount, List<InputEventAPI> events)
```

`amount` 是距上一帧经过的秒数，`events` 是本帧输入事件。逐帧计时必须累计 `amount`，不能假定每帧固定经过 `0.017` 秒。完整时间状态见 [C8-S1]。

## 注册战斗插件

每帧插件在 `data/config/settings.json` 的 `plugins` 段登记 (MOD 中不存在该文件就新建，只写自己的条目)：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

`gearInPauseAwarePlugin` 是全局唯一的注册 key；值必须与 Java 文件的 `package` 和类名组成的完整类名逐字一致。key 冲突会使后读配置覆盖已有条目，类名错误则会在日志中产生 `Problem loading class [...]`。

游戏启动时读取这个类名并注册；**每场战斗开始时为它创建一个新实例**，之后每帧调用实例的 `advance`；战斗结束实例销毁。`engine.addPlugin(...)` 是战斗内动态加入插件的另一种方式。

::: warning 每帧插件的生命周期边界

`init()` 已废弃，首次 `advance()` 不能假设引擎字段已经由 `init()` 设置。暂停期间 `advance()` 仍可能被调用，状态推进放在 `engine.isPaused()` 判断之后。插件实例只属于当前战斗；使用 `isCombatOver()` 判断收尾，插件自行注册监听器时先移除监听器，再移除插件。

:::

## 实现每帧战斗插件

本例需要同时证明两件事：插件在暂停期间仍收到回调，而计时状态在暂停期间保持不变。因此类中分别保存回调次数、暂停回调次数和非暂停时间，并把结果同时写入日志与战场文字。以下片段依次组成 `GearInPauseAwarePlugin`。

### 建立战斗内计时状态

这段代码写在 `GearInPauseAwarePlugin` 的类声明与字段区，声明报告间隔、引擎引用和计时字段：

```java
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;
}
```

这些字段属于插件实例。游戏为每场战斗创建新实例，因此重新进入战斗时计时与次数必须从零开始。

### 拦截暂停帧

这段代码写在 `GearInPauseAwarePlugin.advance()` 开头，先累计回调次数，再取得引擎引用并拦截暂停帧：

```java
calls++;
if (engine == null) {
    engine = Global.getCombatEngine();
    if (engine == null) {
        return;
    }
}
if (engine.isPaused()) {
    pausedCalls++;
    return;
}
```

`calls` 放在暂停检查之前，用于证明暂停时仍有回调；`elapsed` 的更新放在检查之后，只记录真正推进的战斗时间。引擎引用在首次使用时取得，避免依赖已废弃的 `init()` 调用顺序。

### 累计非暂停时间

这段代码写在 `advance()` 的暂停检查之后，累计非暂停时间，达到报告间隔就提交一次报告：

```java
elapsed += amount;
timeSinceLastReport += amount;
if (timeSinceLastReport >= REPORT_INTERVAL) {
    timeSinceLastReport = 0f;
    report(engine);
}
```

报告间隔同样累计非暂停的 `amount`。暂停不会缩短下一次报告的等待时间，也不会改变 `elapsed`。

### 同时提交日志与战场文字

这段代码写在 `GearInPauseAwarePlugin` 的方法区，定义 `report` 方法提交一次状态报告：

```java
private void report(CombatEngineAPI engine) {
    String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
            + " (paused " + pausedCalls + ")";
    Global.getLogger(GearInPauseAwarePlugin.class).info(text);
    if (engine.getPlayerShip() != null) {
        engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
                14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
    }
}
```

`Global.getLogger(...)` 负责写入 `starsector.log`，`addFloatingText(...)` 把同一条状态放到玩家舰船位置。没有可用玩家舰船时，日志仍能保留观察结果。

::: warning 状态推进必须先过暂停检查

任何**推进状态、累计时间、修改数值**的代码，在 `advance` 里都必须先检查 `engine.isPaused()`。游戏不会替您跳过暂停帧 —— 暂停期间您的代码每帧照跑，只是游戏不推进战斗逻辑。

:::

## 完整最小例

示例做一件事：统计 "非暂停时间" 累计秒数 (`elapsed`) 与回调总次数 (`calls`)，每累计 10 秒向日志输出一次，并在玩家舰船位置显示一行浮动文字。暂停期间 `calls` 继续增长、`elapsed` 冻结，暂停检查的可观察结果即在于此。

文件：`src/data/scripts/gearin/combat/GearInPauseAwarePlugin.java`：

::: details 完整成品：GearInPauseAwarePlugin

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 每帧战斗插件：演示暂停守卫与状态归属。
 *
 * <p>注册：MOD 的 data/config/settings.json 的 plugins 段写入本类完整类名。
 * 游戏启动时读取 plugins 段中的完整类名；每场战斗开始时创建新实例，
 * 战斗结束实例随引擎销毁，不得缓存到下一场战斗。</p>
 *
 * <p>advance() 在战斗暂停期间仍然每帧被调用，amount 也仍然非零；是否推进状态
 * 由插件自己决定：本例只用非暂停帧累加 elapsed，暂停帧只累计调用次数 calls。
 * 初始状态在构造器设置，不依赖已废弃的 init()(不保证在第一次 advance() 之前调用)。</p>
 */
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {

    /** 非暂停时间每累计多少秒输出一次报告。 */
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;

    public GearInPauseAwarePlugin() {
        // 初始状态在这里设置；init() 已废弃，不保证在第一次 advance() 之前被调用
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        calls++;
        if (engine == null) {
            engine = Global.getCombatEngine();
            if (engine == null) {
                return;
            }
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

## 排查暂停状态错误

每帧回调没有检查 `engine.isPaused()`。

**局部故障片段：**

```java
// 故障片段
elapsed += amount; report(engine);
```

**局部修正片段：**

```java
// 修正片段
if (engine.isPaused()) {
    return;
}
elapsed += amount;
report(engine);
```

复验：暂停战斗后确认计时日志停止，继续战斗后恢复。

## 验证方案

前置数据：`GearInPauseAwarePlugin` 已写入 `data/config/settings.json` 的 `plugins` 段，`jars/GearIndustry.jar` 已按 [C6-S1] 的流程构建并在启动器启用本 MOD。

1. 在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`。
2. 启动游戏，进入模拟战 (有玩家舰船)，正常跑约 15 秒。
3. 打开 `starsector.log`，搜索 `GearIn`，核对报告行与数字。
4. 按 ESC 暂停约 20 秒再恢复，再次核对报告行。

预期结果：

- 约 15 秒后 `starsector.log` 出现 `GearIn: elapsed ...s, calls ... (paused 0)` 形态的报告，玩家舰船位置出现过一行白色浮动文字 (约 3 秒后消失)；
- 暂停约 20 秒期间没有新报告；恢复后第一次报告的 `elapsed` 与暂停前连续 (暂停的 20 秒未被计入)，`calls` 明显大于按帧率推算的调用数 —— 暂停期间回调照常执行，状态被暂停检查拦下；
- `starsector.log` 没有与 `GearInPauseAwarePlugin` 相关的 `ERROR`。

## 故障分析

- **插件没有任何行为**：`settings.json` 缺少条目、文件路径错误，或 key 被其他来源覆盖时，游戏不会创建实例，日志和画面都没有 `GearIn:` 信号。

    - **解决方案**：确认文件位于 `data/config/settings.json`，key 全局唯一，值与 `package`、`class` 组成的完整类名一致，重新开战。

- **日志出现 `Problem loading class [...]`**：注册类名与 `package`、`class` 不一致，或源码没有进入 JAR 时，类加载器无法解析插件。

    - **解决方案**：让注册值、包声明和类名逐字一致，把源码放入 `src/data/scripts/gearin/combat/` 并重新构建 JAR。

- **暂停期间状态照常推进**：`advance` 在暂停检查前累计状态时，恢复后 `elapsed` 会包含暂停时间。

    - **解决方案**：把状态推进放在 `engine.isPaused()` 守卫之后，仅将 `calls++` 这类观测回调次数的计数放在守卫前。

- **第一次 `advance` 抛出空指针**：在已废弃的 `init(engine)` 中保存引擎并假设它先执行，会让首次 `advance()` 看到 null。

    - **解决方案**：初始状态放入构造器，引擎在使用时通过 `Global.getCombatEngine()` 获取，重新开战。

- **第二场战斗的数值接着上涨**：插件实例被保存到 `static` 字段或战斗外对象时，旧实例会跨战斗存活。

    - **解决方案**：只通过 `settings.json` 登记，让游戏每场创建新实例。

## 总结

每帧战斗插件让代码持续参与当前战斗。`settings.json` 决定插件能否被创建，`advance` 承担逐帧推进，实例字段只保存本场战斗的状态。

暂停不会阻止插件回调，因此计时与数值更新必须放在 `engine.isPaused()` 检查之后。日志与战场文字分别提供持久记录和即时反馈，可以共同确认回调、暂停守卫和实例边界。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧回调接口 (`advance`、`processInputPreCoreControls`、两个 `render...`，暂停期间照常被调用)。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：全部空实现的基类，示例继承它只覆写 `advance`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`、`getElapsedInLastFrame()`(暂停时返回当前帧值)、`addPlugin` / `removePlugin`、`getPlayerShip()`、`addFloatingText(...)`。
- `com.fs.starfarer.api.Global`：`getLogger(Class)`(输出到 `starsector.log`)、`getCombatEngine()` (战斗外返回非 null 空引擎，仅资源加载期为 `null`)。
- `data/config/settings.json`：`plugins` 段注册入口 (游戏本体与所有 MOD 同路径文件深合并，key 全局唯一)。
- `Starsector/starsector-core/starsector.log`：运行日志，插件 `Global.getLogger(...)` 的输出位置。
