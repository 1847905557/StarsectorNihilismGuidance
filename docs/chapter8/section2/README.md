# 读取战斗对象

战斗引擎中的舰船、弹体与光束，可以从各自的集合取得；舰队成员还通过部署映射对应到场上舰体。本节遍历这些对象，用浮动文字标出位置和状态，重点区分集合包含什么，以及标签该附着到谁。

_本节预期阅读时间：\~14 分钟。_

_本节预期操作时间：\~20 分钟。_

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
