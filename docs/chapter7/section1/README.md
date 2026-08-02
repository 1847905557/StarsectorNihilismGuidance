# 战斗插件模型与每帧回调

本节建立战斗域最常用的代码形态：每帧插件。游戏每帧调用您的一个方法，您在里面读状态、推进自己的逻辑。`settings.json` 的 `plugins` 段是注册入口，`EveryFrameCombatPlugin` 是回调接口，暂停守卫是状态推进必须过的第一道检查。本节给出这三者的完整链路与一个可以直接运行的最小示例。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出每帧插件的注册方式：`data/config/settings.json` 的 `plugins` 段，以及两条必须遵守的规则（key 唯一、值是完整类名）；
- 说出插件的生命周期事实：每场战斗开始时为每个注册类创建**新实例**，之后每帧调用实例的 `advance`，战斗结束实例销毁；
- 用 `engine.isPaused()` 暂停守卫写出正确的状态推进，并解释为什么暂停期间 `advance` 仍然每帧被调用；
- 从症状定位插件四类典型故障：无行为、`Problem loading class`、暂停期间状态照常推进、第一帧空指针、第二场战斗数值接着涨。

## 前置知识

以下既有知识是本节的前提：

- MOD 目录结构与 `starsector.log` 日志（C01-S01）：本节所有可观察结果都落在日志里。
- 战役（mission）与生涯（campaign）概念，以及 `GearIn_test` 测试战役（C03-S01）。
- 编译 JAR 链路（C06-S01）：本节示例编入同一个 `jars\GearIndustry.jar`。
- `Global` 生命周期——战斗引擎每场战斗结束销毁后立即重建、战斗外是非 null 空引擎、禁止跨战斗缓存引擎引用（C06-S03）：本节的实例字段随插件实例（每场战斗一个）生存，同一条纪律的插件侧。

## 每帧是什么

游戏战斗以**帧**为单位运行，正常速度下每秒约 60 帧。每一帧，游戏推进舰船、结算武器，然后**依次调用每个插件的 `advance` 方法**：

```text
advance(float amount, List<InputEventAPI> events)
```

`amount` 是距上一帧经过的秒数（正常约 `0.017`），`events` 是本帧的输入事件列表。插件收到的 `amount` 与 `getElapsedInLastFrame()` 是同一个数（时间语义见 C07-S03）。

## 注册：settings.json 的 plugins 段

每帧插件在 `data/config/settings.json` 的 `plugins` 段登记（MOD 中不存在该文件就新建，只写自己的条目）：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

两条规则（与船插的 CSV 注册同一逻辑）：

- **key 唯一**：`gearInPauseAwarePlugin` 全局唯一，带 `GearIn_` 前缀的可读命名同理。key 与其他来源重复时后读覆盖先读——您的插件可能被别人的覆盖，通常还没报错。
- **值是完整类名**：包名.类名，必须与文件里的 `package` + `class` 完全一致。

游戏启动时读取这个类名并注册；**每场战斗开始时为它创建一个新实例**，之后每帧调用实例的 `advance`；战斗结束实例销毁。`engine.addPlugin(...)` 是战斗内的另一种注册方式（本节示例之外的插件用它），注册路径汇总在 C07-S06。

## 完整示例：GearInPauseAwarePlugin

示例做一件事：统计"非暂停时间"累计秒数（`elapsed`）与回调总次数（`calls`），每累计 10 秒向日志输出一次，并在玩家舰船位置显示一行浮动文字。暂停期间 `calls` 继续增长、`elapsed` 冻结——这正是暂停守卫的可观察结果。

文件：`src\data\scripts\gearin\combat\GearInPauseAwarePlugin.java`（fixture 中位于 `tutorial-fixtures/units/C07-S01/`，内容一致）：

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
 * 游戏启动时把核心与所有 MOD 的 settings.json 深合并（文本读取工具.readJSONMerged），
 * 并把 plugins 段全部类名注册为脚本类；每场战斗开始时为其中每个
 * EveryFrameCombatPlugin 类创建新实例并附加（脚本类加载器.loadScriptInstance 的
 * newInstance），战斗结束实例随引擎销毁，不得缓存到下一场战斗。</p>
 *
 * <p>advance() 在战斗暂停期间仍然每帧被调用，amount 也仍然非零；是否推进状态
 * 由插件自己决定：本例只用非暂停帧累加 elapsed，暂停帧只累计调用次数 calls。
 * 初始状态在构造器设置，不依赖已废弃的 init()（不保证在第一次 advance() 之前调用）。</p>
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

要点：

- **`extends BaseEveryFrameCombatPlugin`**：基类提供其余回调（输入处理、渲染等）的空实现，只需覆写 `advance`。
- **5 个字段**：插件自己记的数（累计秒数、距上次报告多久、调用次数、暂停帧次数）。字段随实例生存：每场战斗一个新实例，数字从零开始——**不要**把实例缓存到下一场战斗，否则 `elapsed` 会从上一场接着涨。
- **`advance` 三件事按顺序**：`calls++` 在守卫**之前**（暂停帧也计数，这是"暂停期间回调照常执行"的观测点）；引擎引用**用到时才取**（`init()` 已废弃、不保证先于 `advance` 调用）；**暂停守卫** `if (engine.isPaused()) { pausedCalls++; return; }`——暂停帧只计数、不推进状态。
- **`report`**：`Global.getLogger(...)` 写日志；`addFloatingText(...)` 在玩家舰船位置显示浮动文字（3 秒后消失）。

## 暂停实验：亲眼看到"每帧"

1. 重启游戏，进模拟战（有玩家舰船），正常跑约 15 秒；
2. 打开 `starsector.log`：出现 `GearIn: elapsed 15s, calls 900 (paused 0)` 之类的报告——`calls` 约等于秒数 × 60，这就是"每帧被调用一次"的证据；
3. 按 ESC 暂停约 20 秒再恢复：暂停期间**没有**新报告；恢复后第一次报告的 `elapsed` 与暂停前连续（暂停的 20 秒没被计入），而 `calls` 明显多了一大截——暂停期间 `advance` 一直在被调用，只是被守卫拦下了；
4. 故障注入：删掉 `advance` 里 `if (engine.isPaused()) {...return;}` 两行，重启、进战斗、暂停 20 秒——恢复后 `elapsed` 照常涨了 20 秒。改回后恢复。

::: warning 状态推进必须先过守卫

任何**推进状态、累计时间、修改数值**的代码，在 `advance` 里都必须先检查 `engine.isPaused()`。游戏不会替您跳过暂停帧——暂停期间您的代码每帧照跑，只是游戏不推进战斗逻辑。

:::

## 插件错误对照表

| 症状 | 原因 | 修法 |
|---|---|---|
| 插件没有任何行为 | `settings.json` 没写条目、路径写错、key 与其他来源重复 | 确认文件在 MOD 的 `data/config/settings.json`；key 唯一；值 = 包名.类名 |
| `Problem loading class [...]` | 类名与包声明不一致，或文件不在 MOD 根目录可解析位置 | 类名三处一致；文件放 `data/scripts/gearin/combat/`（不是 `src\`） |
| 暂停期间状态照常推进 | `advance` 里在守卫之前累加/修改状态 | 状态推进代码放守卫之后；纯计数（`calls++`）放守卫前是故意的 |
| 第一次 `advance` 空指针 | 在 `init(engine)` 里保存 `engine` 并假设它先被调用 | 初始状态放构造器；引擎引用用到时才取 |
| 第二场战斗数值接着涨 | 把插件实例缓存到了 `static` 字段或战斗外对象 | 用 settings 登记，让游戏每场战斗创建新实例 |

## 预期结果

1. **fixture 编译（验收）**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC07s01Java --no-daemon`，以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`。
3. **运行观察**：进模拟战（有玩家舰船）约 15 秒后，`starsector.log` 出现 `GearIn: elapsed ...s, calls ... (paused 0)`，且玩家舰船位置出现过一行白色浮动文字（约 3 秒后消失）。
4. **暂停语义**：按 ESC 暂停约 20 秒再恢复——暂停期间没有新报告；恢复后第一次报告的 `elapsed` 与暂停前连续，`calls` 明显大于按帧率推算的调用数。
5. **无错误**：`starsector.log` 没有与 `GearInPauseAwarePlugin` 相关的 `ERROR`。

静态自查：`GearInPauseAwarePlugin` 类源码与 fixture 逐字一致（包括 import 顺序与注释）。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.combat`、类 `GearInPauseAwarePlugin`）。

## 总结

每帧插件是战斗编程的基础模型，三段链路：

- **注册**：`data/config/settings.json` 的 `plugins` 段（key 全局唯一、值为完整类名）→ 加载期注册类名 → 每场战斗 `newInstance` 并附加；
- **回调**：战斗地图每帧调用 `advance`（暂停期间照常，`amount` 非零）——状态推进必须过 `engine.isPaused()` 守卫；
- **实例**：初始状态放构造器（`init()` 已废弃）、字段随实例生存、实例禁止跨战斗缓存。

插件注册的完整生命周期（三条注册路径、`addPlugin`/`removePlugin`、战斗结束无清理回调）在 C07-S06 展开；监听器系统在 C07-S06 一并讲解。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧回调接口（`advance`、`processInputPreCoreControls`、两个 `render...`，暂停期间照常被调用）。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：全部空实现的基类，示例继承它只覆写 `advance`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`、`getElapsedInLastFrame()`（暂停时返回当前帧值）、`addPlugin` / `removePlugin`、`getPlayerShip()`、`addFloatingText(...)`。
- `com.fs.starfarer.api.Global`：`getLogger(Class)`（输出到 `starsector.log`）、`getCombatEngine()` （战斗外返回非 null 空引擎，仅资源加载期为 `null`）。
- `data/config/settings.json`：`plugins` 段注册入口（游戏本体与所有 MOD 同路径文件深合并，key 全局唯一）。
- `Starsector/starsector-core/starsector.log`：运行日志，插件 `Global.getLogger(...)` 的输出位置。
- `tutorial-fixtures/units/C07-S01/`：本节示例 fixture（类与页面代码块逐字一致）。
