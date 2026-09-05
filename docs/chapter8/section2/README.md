# 读取战斗对象

战斗引擎提供了多组对象集合。读取对象时先选择正确的集合，再用对象自身的位置、存活状态和当前帧状态完成观察；本节用 `addFloatingText(...)` 把遍历结果直接显示在战场上。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 从引擎集合读取舰船、弹体、导弹和光束；
- 过滤残骸、未部署成员和无效实体；
- 为对象选择位置与附着对象；
- 用浮动文字确认遍历结果。

## 前置知识

- [C8-S1] 的当前 `CombatEngineAPI` 和暂停判断；
- [C8-S3] 的每帧战斗插件接入方式；
- Java `for` 循环、`instanceof` 和对象方法调用；
- `addFloatingText(Vector2f, String, float, Color, CombatEntityAPI, float, float)` 的参数顺序。

## 遍历舰船

`engine.getShips()` 返回当前引擎对象表中的 `ShipAPI`。对象表包含残骸和等待移除的舰船，因此需要先用 `isAlive()` 过滤。玩家舰船还需要结合 `getPlayerShip()`、`isHulk()` 和 `isEntityInPlay()` 判断实际状态。

下面的片段放在每帧插件的 `advance()` 中，显示每艘存活舰船的船体 ID：

```java
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
```

`getDeployedCopyDFM()` 提供部署成员与场上舰船的映射。它适合回答“哪些舰队成员已经部署”，而 `getShips()` 适合回答“当前对象表里有哪些舰船”。读取成员时跳过战机联队和空间站模块，再从 `member.getShip()` 取得场上实体：

```java
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
```

## 遍历弹体

`engine.getProjectiles()` 返回 `DamagingProjectileAPI` 集合，并且已经包含导弹。统计所有弹体时只遍历这一集合，避免把导弹再次加入总数。弹体位置通过 `getLocation()` 取得，附着对象可以直接传入弹体：

```java
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
```

弹体只属于当前战斗。需要持续观察时，每帧重新读取集合或按间隔重新读取，不保存上一场战斗的对象引用。

## 遍历导弹

`engine.getMissiles()` 返回 `MissileAPI` 子集，适合读取制导、熄火、装填和飞行时间等导弹专属状态：

```java
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
```

导弹同时出现在 `getProjectiles()` 中。`getProjectiles()` 用于总弹体统计，`getMissiles()` 用于导弹专属字段读取。

## 遍历光束

`engine.getBeams()` 返回当前战斗中的 `BeamAPI`。光束本身不是 `CombatEntityAPI`，所以浮动文字位置使用 `beam.getTo()`，附着对象使用当前命中目标；没有命中目标时传入 `null`。

```java
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
```

`didDamageThisFrame()` 与 `getDamageTarget()` 只描述当前帧。需要观察持续光束时，每次重新遍历并读取这两个值，不能跨帧缓存目标。

::: warning 对象集合只属于当前战斗

`ShipAPI`、`DamagingProjectileAPI`、`MissileAPI` 和 `CombatEntityAPI` 引用都绑定当前战斗引擎。战斗结束后下一场战斗重新读取集合；旧引用即使还能调用方法，也不再代表新战斗中的对象。

:::

## 观察结果与过滤规则

浮动文字适合确认遍历入口是否选对，但不适合每帧无间隔地显示全部对象。实际观察时使用较短显示时间，或在 `IntervalUtil` 到期后再执行一次遍历。

对象过滤遵循三个判断：

- 舰船使用 `isAlive()` 排除残骸；
- 部署成员使用 `isFighterWing()`、`isStationModule()` 和 `getShip()` 选择可显示的舰体；
- 光束使用当前帧的 `didDamageThisFrame()` 和 `getDamageTarget()` 描述本帧结果。

## 验证方案

1. 在现有每帧战斗插件的 `advance()` 中临时加入一种遍历片段。
2. 进入测试战斗，观察对应对象位置的浮动文字。
3. 逐次测试舰船、部署成员、弹体、导弹和光束五种对象来源。
4. 击毁一艘舰船，确认残骸不会继续显示 `ship` 标签。
5. 发射导弹，确认导弹同时属于弹体集合且导弹片段可以读取 `guided`。
6. 激活光束，确认终点文字随 `didDamageThisFrame()` 改变。
7. 结束战斗并进入下一场，确认对象引用重新取得。

预期结果：

- 存活舰船和已部署成员在对应位置显示浮动文字；
- 弹体文字显示规格 ID，导弹文字额外显示导弹状态；
- 光束文字显示在终点，并按照当前帧命中状态变化；
- 残骸、空成员和跨战斗旧引用不会被当作当前对象使用。

## 故障分析

- **残骸仍然显示舰船标签**：直接遍历 `getShips()`，没有先判断 `isAlive()`。
    - **解决方案**：在 `addFloatingText(...)` 之前过滤残骸和已死亡舰船。

- **弹体总数大于实际发射数**：把 `getProjectiles()` 和 `getMissiles()` 两个集合相加。
    - **解决方案**：总数只读取 `getProjectiles()`，导弹专属信息单独读取 `getMissiles()`。

- **光束文字位置错误**：把 `BeamAPI` 当作 `CombatEntityAPI` 使用，或读取了错误坐标。
    - **解决方案**：使用 `beam.getTo()` 作为位置，使用 `getDamageTarget()` 作为附着对象。

- **第二场战斗仍显示上一场对象**：保存了旧战斗的舰船、弹体或光束引用。
    - **解决方案**：每场战斗重新取得 `CombatEngineAPI` 和对象集合。

## 总结

舰船、弹体、导弹和光束分别由 `getShips()`、`getProjectiles()`、`getMissiles()` 和 `getBeams()` 提供。遍历后要根据对象类型选择位置、附着对象和当前帧状态；浮动文字可以把集合关系和过滤结果直接显示在战场上。

## 建议参考项

- `CombatEngineAPI`：四类对象集合与 `addFloatingText(...)`；
- `ShipAPI`、`DamagingProjectileAPI`、`MissileAPI`、`BeamAPI`：对象位置和状态访问器；
- `CombatFleetManagerAPI.getDeployedCopyDFM()`：部署成员到场上舰船的映射；
- [C8-S1]：战斗引擎状态与暂停判断；
- [C8-S3]：每帧插件接入方式。
