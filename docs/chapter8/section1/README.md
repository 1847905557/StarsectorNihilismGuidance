# 读取战斗引擎状态

进入战斗之后，代码还需要区分战役、模拟战与生涯遭遇，选择时间口径，并在结束时处理收尾。本节用一个周期日志插件读取 `CombatEngineAPI`，先观察当前场景和状态，再把这些判断用于后续功能。

*本节预期阅读时间：\~16 分钟。*

*本节预期操作时间：\~20 分钟。*

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

## 在本节登记状态插件

创建 `src/data/scripts/gearin/combat/GearInCombatStatePlugin.java`，完整类在后面给出。在 `settings.json` 的 `plugins` 中加入本节局部登记：

```json
{
    "plugins": {
        "gearInCombatStatePlugin": "data.scripts.gearin.combat.GearInCombatStatePlugin"
    }
}
```

游戏会在每场战斗创建这个实例。当前 `advance` 第一次调用取得引擎，在非暂停帧累计两秒，再记录状态。完整的每帧插件结构由 [C8-S3] 继续展开，当前登记已经足够运行本例。

**本场战斗的状态使用本场引擎。** 服务通过 `Global.getCombatEngine` 取得，状态与时间都基于该引用，下一场使用新插件实例。

## 判断战役、模拟战与生涯遭遇

相同效果可能用于不同入口。先读取上下文，再决定哪些状态适合当前功能。

### 战斗上下文速查表

| 方法 | 返回语义 | 适用判断 |
| --- | --- | --- |
| `isInCampaign()` | 当前是生涯战斗且不是生涯内模拟战 | 只在真实生涯遭遇战运行 |
| `isInCampaignSim()` | 当前是生涯内模拟战 | 区分生涯模拟战 |
| `isSimulation()` | 当前是任意模拟战 | 识别模拟战入口 |
| `isMission()` | 当前是战役 | 识别 `data/missions` 战斗 |
| `getMissionId()` | 当前战役 ID | 区分 `GearIn_test` 等战役 |

`getMissionId()` 只有在战役上下文中才有明确的战役用途。日志同时记录 `isMission()` 与 ID，便于确认进入的是预期战斗。

## 选择时间统计口径

逐帧计时采用回调 `amount`；其他已有战斗代码可以读取 `getElapsedInLastFrame`。不含暂停的累计时间用于推进状态，含暂停时间用于比较玩家经历的整段过程。

### 战斗时间速查表

| 方法 | 语义 | 暂停时行为 |
| --- | --- | --- |
| `getTotalElapsedTime(false)` | 战斗推进时间 | 不累计 |
| `getTotalElapsedTime(true)` | 实际经历时间 | 继续累计 |
| `getElapsedInLastFrame()` | 当前帧时间步长 | 返回当前帧值 |
| `getTimeMult()` | 引擎时间倍率 `stat` | 读取修正后的倍率 |
| `getElapsedInContactWithEnemy()` | 双方接敌累计时间 | 不累计 |
| `isFleetsInContact()` | 双方当前是否接敌 | 读取当前状态 |
| `isPaused()` | 当前是否暂停 | 返回暂停标志 |
| `setPaused(boolean)` | 修改暂停状态 | 立即改变暂停标志 |

暂停不会跳过插件的 `advance()`。凡是累计状态或修改数值的代码，都在 `isPaused()` 判断之后执行。

正常速度下常见每秒约 60 帧，但 `amount` 随时间倍率和运行情况变化。**按秒累计，再按暂停条件决定是否推进**，固定假设每帧 0.017 秒会让计时偏离实际。

本例报告间隔只用非暂停 `amount`。暂停后恢复，比较 `total` 与 `totalWithPause`，就能看到两种累计时间的差异。

## 核对结果与收尾

`isCombatOver` 表示战斗收尾状态，`getWinningSideId` 提供当前胜者侧。尚未确定时胜者可能为-1，结束判断仍使用 `isCombatOver`。

`setCombatNotOverFor` 与 `getCombatNotOverFor` 用于延长或读取继续时间。需要让战斗继续时，确认这项延长与当前胜负和撤退逻辑对应，再处理插件收尾。

## 读取界面与战场

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

本例把其中的界面、地图和后备状态同样记录到周期日志。需要据此显示提示时，按实际状态使用；例如 HUD 可见与对话框出现会影响提示是否适合当前画面。

## 用周期日志比较变化

首次 `advance` 调用时绑定当前引擎并记录入口；方法不依赖已废弃 `init` 先于 `advance` 的顺序。随后在非暂停帧累计间隔，局部片段如下：

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

**周期输出需要能够回答当前问题。** 本例把时间、上下文、界面与结果分为两行，便于一起查看，实例仍只服务本场战斗。

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

战斗引擎提供场景、时间、结果与界面状态。读取以当前引擎为范围，推进以 `amount` 和暂停条件为依据，结束则按 `combatOver` 处理。

后面会把这些判断用于对象遍历、数值、监听与绘制。先明确当前功能需要什么状态，再选择读取位置，日志才能对应真正影响结果的条件。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`：状态、时间、界面、后备与结果。
- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：`advance` 与其他战斗回调。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：`init` 的调用约定。
- `Starsector/starsector-core/starsector.log`：状态比较。
