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
- [C6-S2] 的构建与加载；
- [C7-S2] 的 `Global` 服务和战斗引用；
- [C7-S4] 的日志与上下文；
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

以下读取都使用这份引擎引用。插件的完整实现放在后面，[C8-S1] 再展开逐帧回调与实例状态；这里先使用这个入口比较战斗中的变化。

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

这些判断描述整场战斗的后备与撤退状态。具体舰船、弹体和光束从对象集合读取，[C8-S1] 将继续处理它们。本例把界面、地图和后备状态一同写入日志，开关自动驾驶、部署后备或进入收尾时可以分别比较。

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

## 读取战斗对象

战斗引擎中的舰船、弹体与光束，可以从各自的集合取得；舰队成员还通过部署映射对应到场上舰体。本节遍历这些对象，用浮动文字标出位置和状态，重点区分集合包含什么，以及标签该附着到谁。

## 学习目标

完成后，您将能够：

- 从引擎读取四类战斗对象和部署映射；
- 按存活、部署与当前帧信息选择对象；
- 设置浮动文字的位置和附着实体；
- 在当前战斗内按间隔观察集合。

## 前置知识

- [C8-S1] 的状态插件、登记与当前引擎；
- [C7-S2] 的战斗引用范围；
- Java 循环、`instanceof` 和对象方法；
- 能够部署舰船，使用弹体、导弹与光束。

## 给状态插件加入对象观察

沿用 `GearInCombatStatePlugin` 的登记与本场引擎，在类中加入下面的 `import`：

```java
import java.awt.Color;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.mission.FleetSide;
```

对象观察采用 0.25 秒间隔。在字段区添加计时器：

```java
private final IntervalUtil objectInterval = new IntervalUtil(0.25f, 0.25f);
```

在 `advance(...)` 的暂停判断之后推进它，初次观察使用 `showShips()`：

```java
objectInterval.advance(amount);
if (objectInterval.intervalElapsed()) {
    showShips();
}
```

后面的观察方法都加入这个类，切换时修改计时器内的调用。每次显示一类标签，弹体和光束的文字就不会混在一起遮挡。状态报告继续保留，两个计时器各自工作。

## 标出存活舰船

`engine.getShips()` 返回引擎中的舰船对象，其中也可能包含残骸与等待移除的舰船。要标记当前存活舰体，就在遍历中检查 `isAlive()`。玩家舰还可以通过 `getPlayerShip()` 取得，结合 `isHulk()` 与 `isEntityInPlay()` 判断状态。

将以下局部方法加入插件：

```java
private void showShips() {
    for (ShipAPI ship : engine.getShips()) {
        if (!ship.isAlive()) {
            continue;
        }
        engine.addFloatingText(
                ship.getLocation(),
                "ship " + ship.getHullSpec().getHullId(),
                12f,
                Color.WHITE,
                ship,
                0f,
                0.5f);
    }
}
```

`addFloatingText(...)` 接收位置、文字、字体尺寸、颜色、附着实体、调整项与显示时间。本例的坐标与附着实体都来自同一 `ship`，白色标签显示 0.5 秒，内容为它的船体 ID。

## 从部署成员找到舰体

读取舰队部署情况时，从玩家侧舰队管理器的 `getDeployedCopyDFM()` 取得部署成员，再通过成员的 `getShip()` 找到场上实体。下面改用这条映射显示绿色标签：

```java
private void showDeployed() {
    for (DeployedFleetMemberAPI member : engine
            .getFleetManager(FleetSide.PLAYER).getDeployedCopyDFM()) {
        if (member.isFighterWing() || member.isStationModule()) {
            continue;
        }
        ShipAPI ship = member.getShip();
        if (ship != null && ship.isAlive()) {
            engine.addFloatingText(
                    ship.getLocation(),
                    "deployed " + ship.getHullSpec().getHullId(),
                    12f,
                    Color.GREEN,
                    ship,
                    0f,
                    0.5f);
        }
    }
}
```

本例跳过联队与模块，再取得有效的存活舰体。将计时器调用改为 `showDeployed()` 后，标签来自玩家侧的部署成员；与直接遍历 `getShips()` 相比，读取范围和对象关系都已经不同。

## 读取弹体，再取得导弹专属状态

`getProjectiles()` 包含当前弹体，导弹也在其中。先使用这个集合显示每个弹体的规格 ID：

```java
private void showProjectiles() {
    for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
        engine.addFloatingText(
                projectile.getLocation(),
                "projectile " + projectile.getProjectileSpecId(),
                10f,
                Color.YELLOW,
                projectile,
                0f,
                0.25f);
    }
}
```

黄色文字来自 `projectileSpecId`，坐标与附着对象来自当前弹体，显示 0.25 秒。需要读取制导状态时，使用导弹集合并调用导弹自己的方法：

```java
private void showMissiles() {
    for (MissileAPI missile : engine.getMissiles()) {
        String text = "missile " + missile.getProjectileSpecId()
                + " guided=" + missile.isGuided();
        engine.addFloatingText(
                missile.getLocation(),
                text,
                10f,
                Color.ORANGE,
                missile,
                0f,
                0.25f);
    }
}
```

导弹标签额外记录 `isGuided()`，导弹对象还提供熄火与飞行时间等状态。**`getMissiles()` 读取的是总弹体集合中的导弹子集。** 统计总数时使用 `getProjectiles()`；若将两个集合的数量相加，导弹就会被计算两次。

## 光束与当前帧目标

光束没有可直接作为浮动文字附着实体的 `CombatEntityAPI` 身份。位置从 `getTo()` 取得，附着对象则使用本帧伤害目标；没有目标时传入 `null`：

```java
private void showBeams() {
    for (BeamAPI beam : engine.getBeams()) {
        CombatEntityAPI target = beam.getDamageTarget();
        engine.addFloatingText(
                beam.getTo(),
                beam.didDamageThisFrame() ? "beam hit" : "beam",
                12f,
                Color.CYAN,
                target,
                0f,
                0.5f);
    }
}
```

`didDamageThisFrame()` 与 `getDamageTarget()` 都描述当前帧。光束持续显示时，目标与伤害状态仍可能变化，因此每次观察重新读取。

切换到 `showBeams()` 后，标签的位置对应光束终点，`beam hit` 则表示本帧造成了伤害。**文字坐标和附着实体是两个参数。** 本例分别从光束与目标取得它们，不能把光束对象直接当作附着实体。

## 让标签反映读取时的对象

插件每隔 0.25 秒重新遍历一次，标签使用较短显示时间。舰船存活状态、弹体存在与光束目标都按这次读取判断，旧标签随后消失。

验证时分别部署舰船、发射对应武器，再切换观察方法。标签内容说明读取了哪个对象，坐标与附着关系说明它显示在哪里。下一场战斗由新插件实例读取新集合，已有引用也随本场使用结束。

## 验证方案

1. 将方法与计时器加入上一节插件，选择 `showShips`，构建并进入测试战役。
2. 部署舰船并击毁一艘，比较存活标签和残骸。
3. 依次切换 `showDeployed`、`showProjectiles`、`showMissiles`，每次构建重启，用对应对象测试。
4. 使用 `showBeams`，持续照射目标并比较命中与未命中标签。
5. 结束战斗后重新进入，确认集合与实体来自当前场景。

预期结果：

- 存活舰船和实际部署成员在对应位置显示 ID。
- 弹体显示 `spec` ID，导弹增加 `guided` 状态。
- 导弹同属总弹体集合，读取时各用途分开。
- 光束文字位于终点，命中标签采用当前帧信息。
- 每次场景采用新的引擎与对象引用。

## 故障分析

- **残骸仍带 `ship` 标签**：遍历未过滤存活状态。
    - **解决方案**：在文字创建前检查 `isAlive`。

- **弹体数量重复**：总弹体与导弹集合相加。
    - **解决方案**：总统计用 `getProjectiles`，导弹专属字段另读。

- **光束文字位置不符**：使用了不同坐标或把光束当成附着实体。
    - **解决方案**：位置用 `getTo`，附着用 `getDamageTarget`。

- **下一场显示上一场对象**：跨场保存了旧引用。
    - **解决方案**：在当前战斗实例重新读取集合。

## 总结

舰船表提供场上舰体，部署映射提供成员与实体的关系，弹体集合包含导弹，光束则按帧提供终点与伤害目标。选择集合之后，还要按对象状态过滤，并给浮动文字提供正确的坐标和附着实体。

这些读取已经接到同一个插件。下一节展开它的逐帧与暂停处理，再将观察入口用于持续功能。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`：对象集合与 `addFloatingText`。
- `com.fs.starfarer.api.combat.ShipAPI`：舰船位置、存活与部署状态。
- `com.fs.starfarer.api.combat.DamagingProjectileAPI`：弹体状态。
- `com.fs.starfarer.api.combat.MissileAPI`：导弹专属状态。
- `com.fs.starfarer.api.combat.BeamAPI`：位置与当前目标。
- `com.fs.starfarer.api.combat.CombatFleetManagerAPI`：部署成员映射。

## 制作每帧战斗插件

持续战斗效果需要反复更新，`EveryFrameCombatPlugin` 提供这样的运行入口。本节制作一个计时插件，同时记录回调次数与非暂停时间：暂停后恢复时，两种记录会表现出不同变化，插件状态也随本场战斗保存和结束。

## 学习目标

完成后，您将能够：

- 在 `settings.json` 登记每帧插件；
- 按 `amount` 和暂停状态推进数据；
- 管理本场实例与引擎引用；
- 根据日志确认计时和回调。

## 前置知识

- [C6-S2] 的构建与 JAR；
- [C7-S2] 的服务和战斗引用；
- [C8-S1] 的时间、场景与暂停；
- [C8-S1] 的对象和浮动文字。

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
