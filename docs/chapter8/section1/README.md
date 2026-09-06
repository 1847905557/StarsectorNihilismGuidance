# 读取战斗引擎状态

战斗代码先要判断当前引擎处于什么阶段，再决定是否读取时间、界面或战斗结果。本节围绕 `CombatEngineAPI` 解释这些状态，并用一份周期日志确认判断结果。

*本节预期阅读时间：~16 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 区分战斗上下文；
- 读取时间、暂停和结果状态；
- 根据引擎状态选择运行逻辑；
- 用日志确认状态变化。

## 前置知识

- [C1-S1] 的 MOD 目录和 `starsector.log` 位置；
- [C4-S1] 的测试战役和战斗验证方法；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C7-S2] 的 `Global` 服务阶段与跨战斗引用边界；
- [C8-S3] 的每帧战斗插件注册与暂停守卫。

## 取得当前战斗引擎

在战斗插件的回调中使用当前 `CombatEngineAPI`。每场战斗都重新取得引擎对象，状态判断和时间读取都以这份引用为准。

```java
CombatEngineAPI engine = Global.getCombatEngine();
```

## 判断战斗上下文

同一段逻辑可能运行在生涯遭遇战、模拟战或战役中。先读取上下文，再决定是否执行只适用于某种战斗的代码。

### 战斗上下文速查表

| 方法 | 返回语义 | 适用判断 |
| --- | --- | --- |
| `isInCampaign()` | 当前是生涯战斗且不是生涯内模拟战 | 只在真实生涯遭遇战运行 |
| `isInCampaignSim()` | 当前是生涯内模拟战 | 区分生涯模拟战 |
| `isSimulation()` | 当前是任意模拟战 | 识别模拟战入口 |
| `isMission()` | 当前是战役 | 识别 `data/missions` 战斗 |
| `getMissionId()` | 当前战役 ID | 区分 `GearIn_test` 等战役 |

`getMissionId()` 只有在战役上下文中才有明确的战役用途。日志同时记录 `isMission()` 与 ID，便于确认进入的是预期战斗。

## 读取战斗时间与暂停

战斗时间有不同的统计口径。需要推进逻辑时使用不含暂停的累计时间；需要记录玩家实际经历的时间时使用包含暂停的累计时间；逐帧逻辑直接使用上下文传入的 `amount`，如果没有则使用 `getElapsedInLastFrame()`。

::: tip 帧与时间步长

正常速度下每秒约 60 帧。`amount` 是当前帧经过的秒数，时间倍率会改变这个值。

:::

### 战斗时间速查表

| 方法 | 语义 | 暂停时行为 |
| --- | --- | --- |
| `getTotalElapsedTime(false)` | 战斗推进时间 | 不累计 |
| `getTotalElapsedTime(true)` | 实际经历时间 | 继续累计 |
| `getElapsedInLastFrame()` | 当前帧时间步长 | 返回当前帧值 |
| `getTimeMult()` | 引擎时间倍率 stat | 读取修正后的倍率 |
| `getElapsedInContactWithEnemy()` | 双方接敌累计时间 | 不累计 |
| `isFleetsInContact()` | 双方当前是否接敌 | 读取当前状态 |
| `isPaused()` | 当前是否暂停 | 返回暂停标志 |
| `setPaused(boolean)` | 修改暂停状态 | 立即改变暂停标志 |

暂停不会跳过插件的 `advance()`。凡是累计状态或修改数值的代码，都在 `isPaused()` 判断之后执行。

## 读取战斗结果

`isCombatOver()` 是战斗收尾判据；`getWinningSideId()` 只提供胜者侧。胜者 ID 尚未确定时可以是 `-1`，所以不能用胜者 ID 代替结束判定。

`setCombatNotOverFor(float)` 与 `getCombatNotOverFor()` 用于读取或延长战斗继续时间。需要让战斗继续时，先确认这项延长是否与当前胜负逻辑相符。

## 读取界面与战斗环境

引擎还提供几组直接影响战斗逻辑的状态：

### 界面状态

- `isUIShowingDialog()`：当前是否显示战斗对话框；
- `isUIShowingHUD()`：当前是否显示战斗 HUD；
- `isUIAutopilotOn()`：玩家舰是否处于自动驾驶。

需要避免在对话框遮挡时显示提示，或只在 HUD 可见时更新界面文字时，使用这些状态作为条件。

### 战场范围与速度

- `getMapWidth()`、`getMapHeight()`：当前战斗地图范围；
- `isInFastTimeAdvance()`：当前是否处于快速时间推进。

地图范围适合限制坐标计算，快速推进状态适合调整低频提示或测试日志的输出节奏。

### 舰队与撤退状态

- `playerHasNonAllyReserves()`：玩家侧是否还有非盟友后备舰船；
- `playerHasAllyReserves()`：玩家盟友侧是否还有后备舰船；
- `isEnemyInFullRetreat()`：敌方是否进入全面撤退。

这些方法读取的是引擎级战斗状态。舰船、弹体和光束对象的遍历放在 [C8-S2]。

## 用周期日志观察状态

`GearInCombatStatePlugin` 每 2 秒记录上下文、时间和战斗结果。它只负责读取引擎状态，舰船、弹体、导弹和光束的遍历由 [C8-S2] 处理。

初始化时记录战斗上下文：

```java
this.engine = engine;
LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
        + " campaignSim=" + engine.isInCampaignSim()
        + " simulation=" + engine.isSimulation()
        + " mission=" + engine.isMission()
        + " missionId=" + engine.getMissionId());
```

周期报告在非暂停帧累计间隔，并把两种累计时间、时间倍率、接敌状态和胜负结果写到同一行：

```java
if (engine.isPaused()) {
    return;
}
reportInterval.advance(amount);
if (reportInterval.intervalElapsed()) {
    logEngineTime();
    logContext();
}
```

## 完整最小例

下面的类只读取当前战斗引擎状态，并把上下文、时间、暂停和结果写入日志。

::: details 完整成品：GearInCombatStatePlugin

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗引擎状态读取 —— 上下文、时间、暂停与战斗结果。
 *
 * 本插件由战斗上下文注册，只在本场战斗内有效：
 * - 状态读取只使用当前战斗的引擎引用；
 * - advance() 在暂停时仍可能被调用，状态推进前必须检查 engine.isPaused()；
 * - getWinningSideId() 只表示胜者侧，战斗结束判定使用 isCombatOver()。
 */
public class GearInCombatStatePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatStatePlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
                + " campaignSim=" + engine.isInCampaignSim()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return;
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logEngineTime();
            logContext();
        }
    }

    /** 引擎时间语义：暂停不计/计、当前帧 dt、时间倍率、接触时间与战斗结果。 */
    private void logEngineTime() {
        LOG.info("GearIn time: total=" + engine.getTotalElapsedTime(false)
                + " totalWithPause=" + engine.getTotalElapsedTime(true)
                + " lastFrame=" + engine.getElapsedInLastFrame()
                + " timeMult=" + engine.getTimeMult().getModifiedValue()
                + " contact=" + engine.getElapsedInContactWithEnemy()
                + " fleetsInContact=" + engine.isFleetsInContact()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }

    /** 记录适合决定逻辑分支的引擎状态。 */
    private void logContext() {
        LOG.info("GearIn context: campaign=" + engine.isInCampaign()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId()
                + " paused=" + engine.isPaused()
                + " dialog=" + engine.isUIShowingDialog()
                + " hud=" + engine.isUIShowingHUD()
                + " fastTime=" + engine.isInFastTimeAdvance()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }
}
```

:::

## 验证方案

1. 按 [C8-S3] 的方式注册并构建 `GearInCombatStatePlugin`。
2. 进入 `GearIn_test`，观察初始化日志中的战斗类型和 `missionId`。
3. 正常运行数秒，核对 `GearIn time:` 与 `GearIn context:` 的时间、接敌和胜负字段。
4. 暂停战斗约 10 秒再恢复，比较 `total`、`totalWithPause` 和 `lastFrame`。
5. 打开战斗对话框或自动驾驶，观察对应界面状态字段变化。
6. 结束战斗，核对 `isCombatOver()` 与胜者字段的先后关系。

预期结果：

- 战役战斗显示 `mission=true` 和对应 `missionId`；
- 暂停期间不推进不含暂停的战斗时间；
- 包含暂停的累计时间可以继续增长；
- 胜者字段用于显示胜者侧，结束判定使用 `isCombatOver()`；
- 日志没有与 `GearInCombatStatePlugin` 相关的错误。

## 故障分析

- **战斗类型判断错误**：把 `isSimulation()`、`isInCampaignSim()` 和 `isMission()` 当作同一个状态。

    - **解决方案**：同时记录三个方法和 `getMissionId()`，按照实际战斗入口选择判断条件。

- **暂停期间计时继续推进**：在 `isPaused()` 判断前累计 `amount`。

    - **解决方案**：把状态推进和报告间隔放到暂停守卫之后。

- **胜者尚未确定却提前收尾**：用 `getWinningSideId() != -1` 代替战斗结束判定。

    - **解决方案**：用 `isCombatOver()` 判定收尾，再读取 `getWinningSideId()` 记录结果。

- **界面提示出现在错误阶段**：没有检查对话框、HUD 或自动驾驶状态。

    - **解决方案**：在显示提示前读取对应的界面状态方法。

## 总结

本节把 `CombatEngineAPI` 的读取分成四类判断：上下文确认当前战斗类型，时间方法决定推进口径，结果方法决定收尾状态，界面与战场方法决定提示是否适合当前阶段。读取结果应绑定当前战斗引擎，具体舰船、弹体、导弹和光束对象交给 [C8-S2]。

## 建议参考项

- `CombatEngineAPI`：战斗上下文、时间、暂停、界面和结果访问器；
- `EveryFrameCombatPlugin`：战斗插件的 `advance` 回调；
- `Starsector/starsector-core/starsector.log`：核对状态日志的读取位置。
