# 读取战斗引擎状态

`CombatEngineAPI` 提供一场战斗的时间、场景、界面与结果。持续效果需要按秒推进，生涯专用逻辑需要识别遭遇战，收尾代码则需要判断战斗是否结束。本节用周期日志记录这些状态，说明各项判断怎样用于实际功能。

_本节预期阅读时间：\~16 分钟。_

_本节预期操作时间：\~20 分钟。_

## 学习目标

完成后，您将能够：

- 区分战斗上下文；
- 读取时间、暂停和结果；
- 按场景、界面和战场状态安排逻辑；
- 用日志比较实际变化。

## 前置知识

- [C4-S1] 的 `GearIn_test` 战役；
- [C6-S4] 的构建与加载；
- [C7-S2] 的 `Global` 服务和战斗引用；
- [C7-S5] 的日志与上下文；
- Java 覆写方法、字段与计时。

## 建立本场战斗的状态观察

创建 `src/data/scripts/gearin/combat/GearInCombatStatePlugin.java`，完整类在后面给出。在 `settings.json` 的 `plugins` 中加入本节局部登记：

```json
{
    "plugins": {
        "gearInCombatStatePlugin": "data.scripts.gearin.combat.GearInCombatStatePlugin"
    }
}
```

登记后，游戏为每场战斗创建一份插件。它在第一次 `advance(...)` 中通过 `Global.getCombatEngine()` 取得当前引擎，每经过两秒非暂停时间记录一次状态；下一场由新实例重新开始。

以下读取都使用这份引擎引用。插件的完整实现放在后面，[C8-S3] 再展开逐帧回调与实例状态；这里先使用这个入口比较战斗中的变化。

## 判断战役、模拟战与生涯遭遇

同一份代码可能出现在测试战役、整备模拟战或生涯遭遇战中。引擎分别提供这些场景的判断方法，功能限定在哪种战斗中运行，就采用对应条件。

### 战斗上下文速查表

| 方法                | 返回语义                         | 适用判断                  |
| ------------------- | -------------------------------- | ------------------------- |
| `isInCampaign()`    | 当前是生涯战斗且不是生涯内模拟战 | 只在真实生涯遭遇战运行    |
| `isInCampaignSim()` | 当前是生涯内模拟战               | 区分生涯模拟战            |
| `isSimulation()`    | 当前是任意模拟战                 | 识别模拟战入口            |
| `isMission()`       | 当前是战役                       | 识别 `data/missions` 战斗 |
| `getMissionId()`    | 当前战役 ID                      | 区分 `GearIn_test` 等战役 |

例如本章使用 `GearIn_test`，日志同时记录 `isMission()` 与 `getMissionId()`。前者确认战役场景，后者确认具体战役。生涯内模拟战则用 `isInCampaignSim()` 判断，不能因为它从生涯界面打开，就当作真实遭遇战处理。

## 让计时符合效果的持续条件

逐帧代码累计回调提供的 `amount`，其他战斗代码也可以通过 `getElapsedInLastFrame()` 取得本帧步长。计时之前，还要确定暂停是否应该计入：持续效果通常随战斗推进，比较玩家经历的总时长则可能需要包含暂停。

### 战斗时间速查表

| 方法                             | 语义                | 暂停时行为       |
| -------------------------------- | ------------------- | ---------------- |
| `getTotalElapsedTime(false)`     | 战斗推进时间        | 不累计           |
| `getTotalElapsedTime(true)`      | 实际经历时间        | 继续累计         |
| `getElapsedInLastFrame()`        | 当前帧时间步长      | 返回当前帧值     |
| `getTimeMult()`                  | 引擎时间倍率 `stat` | 读取修正后的倍率 |
| `getElapsedInContactWithEnemy()` | 双方接敌累计时间    | 不累计           |
| `isFleetsInContact()`            | 双方当前是否接敌    | 读取当前状态     |
| `isPaused()`                     | 当前是否暂停        | 返回暂停标志     |
| `setPaused(boolean)`             | 修改暂停状态        | 立即改变暂停标志 |

**暂停时，插件的 `advance()` 仍然会被调用。** 要让状态随战斗停止，就在 `isPaused()` 判断后累计时间和修改数值。本例也将报告计时放在这个位置，暂停时保留余量，恢复后继续。

正常速度下常见每秒约 60 帧，实际 `amount` 仍受时间倍率和运行情况影响。固定按每帧 0.017 秒累加，会把帧数误当成时间；直接累计传入步长，才能保持持续时间的含义。

日志同时读取 `total` 与 `totalWithPause`。暂停一段时间再恢复之后，前者对应战斗推进，后者包括暂停，两者的差异便有了实际操作依据。

## 读取界面与战场

计时之外，提示和战斗逻辑还需要使用当前界面与战场状态。以下按它们所影响的操作安排读取：

### 界面状态

- `isUIShowingDialog()`：当前是否显示战斗对话框；
- `isUIShowingHUD()`：当前是否显示战斗 HUD；
- `isUIAutopilotOn()`：玩家舰是否处于自动驾驶。

提示文字需要避开战斗对话框，或仅在 HUD 显示时出现，就将相应状态写入显示条件。自动驾驶状态则用于区分玩家舰当前由谁操控。

### 战场范围与速度

- `getMapWidth()`、`getMapHeight()`：当前战斗地图范围；
- `isInFastTimeAdvance()`：当前是否处于快速时间推进。

计算战场坐标时使用实际地图宽高。处于快速推进时，低频提示或测试日志也可以根据该状态调整节奏。

### 舰队与撤退状态

- `playerHasNonAllyReserves()`：玩家侧是否还有非盟友后备舰船；
- `playerHasAllyReserves()`：玩家盟友侧是否还有后备舰船；
- `isEnemyInFullRetreat()`：敌方是否进入全面撤退。

这些判断描述整场战斗的后备与撤退状态。具体舰船、弹体和光束从对象集合读取，[C8-S2] 将继续处理它们。本例把界面、地图和后备状态一同写入日志，开关自动驾驶、部署后备或进入收尾时可以分别比较。

## 判断结束与胜者

`isCombatOver()` 表示战斗进入收尾，`getWinningSideId()` 提供当前胜者侧。胜者尚未确定时可能为 -1，结束判定因此使用 `isCombatOver()`，胜者信息另外记录。

需要延长战斗继续时间时，使用 `setCombatNotOverFor(...)`，读取剩余继续时间则使用 `getCombatNotOverFor()`。这项延长还要与当前胜负和撤退逻辑对应，插件自己的收尾也按实际结束条件安排。

## 用周期日志比较变化

插件在首次 `advance(...)` 中绑定引擎并记录场景，不依赖已废弃的 `init(...)` 先执行。随后将以下局部片段用于报告计时：

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

`logEngineTime()` 记录时间与结果，`logContext()` 记录场景、界面和战场状态。它们在同次报告中调用，后面的完整类保留这些字段。比较暂停或结束前后的记录时，数值变化就能对应到当时的操作。

## 完整最小例

保存本节类并保留上面的 `settings` 登记，再构建 JAR。

::: details 完整成品：`GearInCombatStatePlugin`

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/** 周期记录当前战斗的场景、时间、界面与结果。 */
public class GearInCombatStatePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatStatePlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    private void bindEngine() {
        engine = Global.getCombatEngine();
        LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
                + " campaignSim=" + engine.isInCampaignSim()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            bindEngine();
        }
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
                + " autopilot=" + engine.isUIAutopilotOn()
                + " reserves=" + engine.playerHasNonAllyReserves()
                + " allyReserves=" + engine.playerHasAllyReserves()
                + " enemyRetreat=" + engine.isEnemyInFullRetreat()
                + " map=" + engine.getMapWidth() + "/" + engine.getMapHeight()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }
}
```

:::

## 验证方案

1. 保存源码与登记，构建并重启，进入 `GearIn_test`。
2. 搜索 `GearIn battle init`，核对 `mission` 与战役 ID。
3. 正常运行数秒，比较 `GearIn time` 和 `context` 的时间、接敌、地图与界面。
4. 暂停约 10 秒再恢复，比较两种累计时间和报告间隔。
5. 开关自动驾驶、查看战斗对话与 HUD，比较对应状态。
6. 结束战斗，查看 `combatOver`、`winner`、撤退与后备状态。

预期结果：

- 战役入口 `mission` 为 `true`，`missionId` 对应 `GearIn_test`。
- 非暂停报告按 2 秒间隔出现，暂停期间计时保持，恢复后继续。
- `total` 和 `totalWithPause` 分别按自身口径累计。
- 自动驾驶、HUD、对话、地图和后备读取当前引擎状态。
- 结果判断采用 `combatOver`，`winner` 提供对应侧信息。

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

本场引擎提供功能需要的运行条件：场景判断限定适用战斗，时间与暂停决定状态推进，界面状态决定提示是否适合显示，结束标记则用于收尾。

周期日志将这些条件与实际操作对应起来。后面的对象遍历、数值和绘制继续使用同一份引擎，分别读取自己的对象与状态。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`：状态、时间、界面、后备与结果。
- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：`advance` 与其他战斗回调。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：`init` 的调用约定。
- `Starsector/starsector-core/starsector.log`：状态比较。
