# 制作每帧战斗插件

本节制作第一个每帧战斗插件，并在 `data/config/settings.json` 中完成注册。示例会把回调写入日志，用来确认插件生命周期和暂停时的推进方式。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/config/settings.json` 的 `plugins` 段注册每帧插件，并遵守 key 唯一、值为完整类名两条规则；
- 解释插件生命周期：每场战斗开始时为每个注册类创建新实例，之后每帧调用实例的 `advance`，战斗结束实例销毁；
- 用 `engine.isPaused()` 写对暂停期间的状态推进，并解释暂停期间 `advance` 仍然每帧被调用的原因；
- 从症状定位插件五类典型故障：无行为、`Problem loading class`、暂停期间状态照常推进、第一帧空指针、第二场战斗数值接着涨。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C3-S1] 的模拟战入口与战斗验证方法；
- [C6-S1] 的 Java 构建、JAR 输出与入口类；
- [C6-S3] 的 `Global` 生命周期、战斗引擎实例边界与跨战斗引用规则。

## 判断战斗帧语义

游戏战斗以**帧**为单位运行，正常速度下每秒约 60 帧。每一帧，游戏推进舰船、结算武器，然后**依次调用每个插件的 `advance` 方法**：

```text
advance(float amount, List<InputEventAPI> events)
```

`amount` 是距上一帧经过的秒数 (正常约 `0.017`)，`events` 是本帧的输入事件列表。插件收到的 `amount` 与 `getElapsedInLastFrame()` 是同一个数 (时间语义见 [C7-S3])。

## 注册战斗插件

每帧插件在 `data/config/settings.json` 的 `plugins` 段登记 (MOD 中不存在该文件就新建，只写自己的条目)：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

两条规则 (与船插的 CSV 注册同一逻辑)：

- **key 唯一**：`gearInPauseAwarePlugin` 全局唯一，带 `GearIn_` 前缀的可读命名同理。key 与其他来源重复时后读覆盖先读 —— 您的插件可能被别人的覆盖，通常还没报错。
- **值是完整类名**：包名.类名，必须与文件里的 `package` + `class` 完全一致。

游戏启动时读取这个类名并注册；**每场战斗开始时为它创建一个新实例**，之后每帧调用实例的 `advance`；战斗结束实例销毁。`engine.addPlugin(...)` 是战斗内的另一种注册方式 (本节示例之外的插件用它)，完整注册与移除见 [C7-S5]。

## 实现每帧战斗插件

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 建立战斗内计时状态

```java
// 插入位置：GearInPauseAwarePlugin 的类声明与字段区
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;
}
```

新增行为：每场战斗用独立实例保存计时和调用次数。完成信号：类名与 `settings.json` 注册值一致，构建通过；重新进入战斗时这些字段从零开始。

### 拦截暂停帧

```java
// 插入位置：GearInPauseAwarePlugin.advance() 开头
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

新增行为：回调总数包含暂停帧，战斗状态只在非暂停帧继续推进。完成信号：按 ESC 后 `calls` 仍增长，`elapsed` 保持不变。

### 累计非暂停时间

```java
// 插入位置：advance() 的暂停检查之后
elapsed += amount;
timeSinceLastReport += amount;
if (timeSinceLastReport >= REPORT_INTERVAL) {
    timeSinceLastReport = 0f;
    report(engine);
}
```

新增行为：插件每累计 10 秒非暂停时间报告一次。完成信号：正常运行约 10 秒后触发首次报告；暂停时间不会缩短下一次报告的等待时间。

### 同时提交日志与战场文字

```java
// 插入位置：GearInPauseAwarePlugin 的方法区
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

新增行为：同一份状态既进入 `starsector.log`，也附着在玩家舰船位置显示。完成信号：日志与战场文字给出相同的 `elapsed`、`calls` 和 `pausedCalls`；没有玩家舰船时只写日志。

## 完整最小例

示例做一件事：统计 "非暂停时间" 累计秒数 (`elapsed`) 与回调总次数 (`calls`)，每累计 10 秒向日志输出一次，并在玩家舰船位置显示一行浮动文字。暂停期间 `calls` 继续增长、`elapsed` 冻结 —— 这正是暂停检查的可观察结果。

文件：`src\data\scripts\gearin\combat\GearInPauseAwarePlugin.java`：

要点：

- **`extends BaseEveryFrameCombatPlugin`**：基类提供其余回调 (输入处理、渲染等) 的空实现，只需覆写 `advance`。
- **5 个字段**：插件自己记的数 (累计秒数、距上次报告多久、调用次数、暂停帧次数)。字段随实例生存：每场战斗一个新实例，数字从零开始 —— **不要**把实例缓存到下一场战斗，否则 `elapsed` 会从上一场接着涨。
- **`advance` 三件事按顺序**：`calls++` 在暂停检查**之前** (暂停帧也计数，这是 "暂停期间回调照常执行" 的观测点)；引擎引用**用到时才取** (`init()` 已废弃、不保证先于 `advance` 调用)；**暂停检查** `if (engine.isPaused()) { pausedCalls++; return; }` —— 暂停帧只计数、不推进状态。
- **`report`**：`Global.getLogger(...)` 写日志；`addFloatingText(...)` 在玩家舰船位置显示浮动文字 (3 秒后消失)。

## 观察暂停行为

1. 重启游戏，进模拟战 (有玩家舰船)，正常跑约 15 秒；
2. 打开 `starsector.log`：出现 `GearIn: elapsed 15s, calls 900 (paused 0)` 之类的报告 —— `calls` 约等于秒数 × 60，这正是每帧被调用一次的表现；
3. 按 ESC 暂停约 20 秒再恢复：暂停期间**没有**新报告；恢复后第一次报告的 `elapsed` 与暂停前连续 (暂停的 20 秒没被计入)，而 `calls` 明显多了一大截 —— 暂停期间 `advance` 一直在被调用，只是被暂停检查拦下了；
4. 故障注入：删掉 `advance` 里的暂停检查段 (`if (engine.isPaused()) {...return;}`)，重启、进战斗、暂停 20 秒 —— 恢复后 `elapsed` 照常涨了 20 秒。改回后恢复。

::: warning 状态推进必须先过暂停检查

任何**推进状态、累计时间、修改数值**的代码，在 `advance` 里都必须先检查 `engine.isPaused()`。游戏不会替您跳过暂停帧 —— 暂停期间您的代码每帧照跑，只是游戏不推进战斗逻辑。

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
if (engine.isPaused()) { return; } elapsed += amount; report(engine);
```

复验：暂停战斗后确认计时日志停止，继续战斗后恢复。

## 验证方案

前置数据：`GearInPauseAwarePlugin` 已写入 `data/config/settings.json` 的 `plugins` 段，`jars\GearIndustry.jar` 已按 [C6-S1] 的流程构建并在启动器启用本 MOD。

1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`。
2. 启动游戏，进入模拟战 (有玩家舰船)，正常跑约 15 秒。
3. 打开 `starsector.log`，搜索 `GearIn`，核对报告行与数字。
4. 按 ESC 暂停约 20 秒再恢复，再次核对报告行。

预期结果：

- 约 15 秒后 `starsector.log` 出现 `GearIn: elapsed ...s, calls ... (paused 0)` 形态的报告，玩家舰船位置出现过一行白色浮动文字 (约 3 秒后消失)；
- 暂停约 20 秒期间没有新报告；恢复后第一次报告的 `elapsed` 与暂停前连续 (暂停的 20 秒未被计入)，`calls` 明显大于按帧率推算的调用数 —— 暂停期间回调照常执行，状态被暂停检查拦下；
- `starsector.log` 没有与 `GearInPauseAwarePlugin` 相关的 `ERROR`。

## 故障分析

- **插件没有任何行为**：`settings.json` 缺少条目、文件路径错误，或 key 被其他来源覆盖时，游戏不会创建实例，日志和画面都没有 `GearIn:` 信号。确认文件位于 `data/config/settings.json`，key 全局唯一，值与 `package`、`class` 组成的完整类名一致；重新开战后应出现报告和浮动文字。
- **日志出现 `Problem loading class [...]`**：注册类名与 `package`、`class` 不一致，或源码没有进入 JAR 时，类加载器无法解析插件。让注册值、包声明和类名逐字一致，把源码放入 `data/scripts/gearin/combat/` 并重新构建 JAR；再进战斗时错误应消失。
- **暂停期间状态照常推进**：`advance` 在暂停检查前累计状态时，恢复后 `elapsed` 会包含暂停时间。把状态推进放在 `engine.isPaused()` 守卫之后，仅将 `calls++` 这类观测回调次数的计数放在守卫前；暂停并恢复后，`elapsed` 应保持连续。
- **第一次 `advance` 抛出空指针**：在已废弃的 `init(engine)` 中保存引擎并假设它先执行，会让首次 `advance()` 看到 null。初始状态放入构造器，引擎在使用时通过 `Global.getCombatEngine()` 获取；重新开战后首次回调应正常完成。
- **第二场战斗的数值接着上涨**：插件实例被保存到 `static` 字段或战斗外对象时，旧实例会跨战斗存活。只通过 `settings.json` 登记，让游戏每场创建新实例；连续进入两场战斗后，第二场的 `elapsed` 和 `calls` 应从初始值开始。

## 总结

本节完成了第一个战斗插件从注册到验证的完整流程：`settings.json` 的 `plugins` 段登记类名，游戏每场战斗创建新实例并逐帧调用 `advance`，实例字段随战斗而生灭。三段中任何一段断开，症状各不相同：没注册是完全没有行为，类名不一致是 `Problem loading class [...]`，实例被缓存则是第二场战斗数值接着涨。

排错时沿注册条目、构建产物、状态推进的顺序核对，比对着画面猜测更快。暂停语义是本节的核心判断：游戏不会替您跳过暂停帧，凡是推进状态、累计时间的代码，都要先过 `engine.isPaused()` 这一道检查。

插件注册的完整生命周期 (三条注册路径、`addPlugin` / `removePlugin`、战斗结束无清理回调) 与监听器系统见 [C7-S5]。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧回调接口 (`advance`、`processInputPreCoreControls`、两个 `render...`，暂停期间照常被调用)。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：全部空实现的基类，示例继承它只覆写 `advance`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`、`getElapsedInLastFrame()`(暂停时返回当前帧值)、`addPlugin` / `removePlugin`、`getPlayerShip()`、`addFloatingText(...)`。
- `com.fs.starfarer.api.Global`：`getLogger(Class)`(输出到 `starsector.log`)、`getCombatEngine()` (战斗外返回非 null 空引擎，仅资源加载期为 `null`)。
- `data/config/settings.json`：`plugins` 段注册入口 (游戏本体与所有 MOD 同路径文件深合并，key 全局唯一)。
- `Starsector/starsector-core/starsector.log`：运行日志，插件 `Global.getLogger(...)` 的输出位置。

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
 * 游戏启动时把核心与所有 MOD 的 settings.json 深合并(文本读取工具.readJSONMerged)，
 * 并把 plugins 段全部类名注册为脚本类；每场战斗开始时为其中每个
 * EveryFrameCombatPlugin 类创建新实例并附加(脚本类加载器.loadScriptInstance 的
 * newInstance)，战斗结束实例随引擎销毁，不得缓存到下一场战斗。</p>
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
