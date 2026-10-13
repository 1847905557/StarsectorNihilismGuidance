# 读取战斗对象

战斗状态已经可以读取，接下来选择实际对象集合。本节分别观察舰船、部署成员、弹体、导弹和光束，用浮动文字标明位置与当前信息。对象来源不同，过滤条件和附着位置也按各自类型处理。

*本节预期阅读时间：\~14 分钟。*

*本节预期操作时间：\~20 分钟。*

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

## 在现有插件中加入观察入口

沿用上一节 `GearInCombatStatePlugin`。添加下面的 `import`，原有 `import` 保留：

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

字段区添加局部计时器：

```java
private final IntervalUtil objectInterval = new IntervalUtil(0.25f, 0.25f);
```

在 `advance` 的暂停判断之后增加观察计时，当前先调用 `showShips`：

```java
objectInterval.advance(amount);
if (objectInterval.intervalElapsed()) {
    showShips();
}
```

下面分别给出五种观察方法。每次选择一种调用，方法保留在同一个插件类中。**观察结果先按对象来源分开**，密集同时标记所有弹体与光束，会影响判断。

## 舰船与部署成员

`getShips` 读取当前引擎对象表，它也包含残骸和等待移除的舰船，因此先判断 `isAlive`。玩家舰还可以结合 `getPlayerShip`、`isHulk` 与 `isEntityInPlay` 检查当前状态。

将以下局部方法加入类中：

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

`addFloatingText` 的参数依次为位置、文字、字体尺寸、颜色、附着实体、调整项与显示时间。这里传入舰船位置并附着到同一 `ship`，显示 0.5 秒。

部署成员提供另一种关系：从玩家侧 `getDeployedCopyDFM` 取得已经部署的成员，再用 `getShip` 得到当前实体。它适合查看舰队成员怎样对应到场上舰船：

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

先过滤联队和模块，再查看可用存活舰体。要观察这一类时，把计时器内的调用改为 `showDeployed`。

## 弹体与导弹

`getProjectiles` 已经包含导弹。总弹体统计只使用这个集合，`getMissiles` 则单独读取导弹专属信息。

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

文字显示 `projectileSpecId`，位置和附着对象来自当前弹体，显示时间为 0.25 秒。观察导弹时改用：

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

导弹还提供制导、熄火和飞行时间等状态，本例先记录 `isGuided`。**总数与子集用途分别计算**，把两份集合相加会重复算导弹。

## 光束与当前帧目标

`BeamAPI` 不是 `CombatEntityAPI`。文字位置使用 `getTo`，附着对象使用当前伤害目标，目标缺席时该参数可以为 `null`：

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

`didDamageThisFrame` 和 `getDamageTarget` 描述当前帧。每次观察重新取得值，光束持续照射也可能在不同帧改变目标。

选择 `showBeams` 后，比较文字位置与光束终点。附着实体和坐标并不是同一参数，先把位置对齐，再判断目标状态是否对应。

## 让观察范围跟随本场战斗

本例按 0.25 秒间隔重新遍历，标签使用较短显示时间。舰船状态、弹体存在和光束目标都在读取时重新判断，下一场使用新的插件实例。

编者建议观察时固定一种武器和目标，再换另一类集合。先确认自己读的对象是什么，比在屏幕上输出一大堆文字更有价值。

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

对象集合按类型提供当前战斗信息。舰船表、部署映射、弹体总集、导弹子集和光束帧状态各自回答不同问题，读取后再选择位置与附着对象。

本节在已有插件中建立固定观察入口，接下来会解释它的逐帧与暂停处理。对象范围和运行时机一起确认后，再把数据用于实际效果。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`：对象集合与 `addFloatingText`。
- `com.fs.starfarer.api.combat.ShipAPI`：舰船位置、存活与部署状态。
- `com.fs.starfarer.api.combat.DamagingProjectileAPI`：弹体状态。
- `com.fs.starfarer.api.combat.MissileAPI`：导弹专属状态。
- `com.fs.starfarer.api.combat.BeamAPI`：位置与当前目标。
- `com.fs.starfarer.api.combat.CombatFleetManagerAPI`：部署成员映射。
