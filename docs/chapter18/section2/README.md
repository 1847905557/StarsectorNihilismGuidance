# 使用 LazyLib 几何工具

做制导和提前量预测时，最容易疏忽的是误把实体返回的坐标与速度当成纯数值副本。它们背后往往挂着活引用，同一个向量拿去原地旋转或缩放，会连带改掉发射体自己的航向。

这页拿 `Devarim` 弹体改一枚测试导弹 `GearIn_geometry_missile`，借提前量制导与多色调试粒子，顺便把 LazyLib 各组几何工具究竟返回新向量还是改动原对象标个清楚。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C1-S5] 的德瓦林武器、导弹弹体与 `engineSpec`；
- [C7-S1] 的 `BaseModPlugin` 与导弹 AI 选择入口；
- [C8-S1] 的每帧调用与暂停判断；
- [C8-S3] 的战斗实体 (`CombatEntityAPI`)、碰撞类别与粒子绘制，以及 [C9-S2] 的 `IntervalUtil`；
- [C18-S1] 的 LazyLib 依赖声明与编译 classpath。

## 准备测试导弹

复制 [C1-S5] 的 `data/weapons/proj/GearIn_dwalin_rocket.proj`，将副本保存为同目录的 `GearIn_geometry_missile.proj`。在副本中修改以下局部字段，保留贴图、尺寸、中心、碰撞与引擎配置：

```json
"id": "GearIn_geometry_missile",
"missileType": "MISSILE"
```

德瓦林的 `engineSpec` 已提供转向加速度 360、最大转向速度 180 和加速度 1000，足以观察后面的左右转向。测试时，把 `GearIn_dwalin.wpn` 的 `projectileSpecId` 改为新 ID：

```json
"projectileSpecId": "GearIn_geometry_missile"
```

新弹体在本节定义，原弹体继续保留。完成 AI 类和选择入口后再构建、发射；本次验证让德瓦林武器使用这枚测试导弹。

## 从导弹的动作确定所需计算

`GearInGeometryMissileAI` 在构造时取得发射舰当前目标。每帧发出加速命令后，AI 用目标中心距离除以导弹最大速度估算提前时间，再把时间限制在 `0f` 到 `2f`。目标速度乘以这段时间，就得到相对当前位置的线性提前量。

这个提前点假定目标保持当前速度。它计算便宜，足以表现横向移动目标的追踪差异；目标加速或急转时，下一帧重新计算，预测也随之改变。**制导使用的是当前运动数据，而不是目标未来动作。**

取得提前点以后，`VectorUtils.getAngle(...)` 给出应转向的角度，`MathUtils.getShortestRotation(...)` 决定左右。速度方向与弹体朝向相差超过 `20` 度时，AI 再发出横移命令，修正弹体已经转向、速度仍偏向一侧的侧滑。

目标离场、进入相位或采用 `CollisionClass.NONE` 时，AI 暂停这部分跟踪，只保持加速直飞。引用继续保留，同一目标恢复为可跟踪状态后再继续计算。下面的工具分别处理距离、方向与边界，完整类在最后将它们接回这套行为。

## 认识坐标、角度与可变向量

战斗空间使用 `Vector2f` 保存二维坐标和向量。坐标与方向都用两个浮点分量表示，含义由当前调用决定。**计算前先确认这份向量代表什么，以及方法会修改哪一个对象。** 角度以度为单位，`0f` 指向世界坐标的正 X 轴，增加角度表示逆时针旋转。

`CombatEntityAPI.getLocation()` 和 `getVelocity()` 返回引擎正在使用的可变对象。直接调用 `set(...)`、`scale(...)`，或者把它传给会原地修改向量的方法，都会改变这份对象本身。只做计算时先用 `new Vector2f(source)` 复制，或者选择带 `dest` 参数的重载。

::: warning 引擎返回的向量仍属于实体

把 `getVelocity()` 的结果交给原地缩放方法，会直接改变实体速度。只为计算提前量或预测线时，使用副本或独立的目标向量。

:::

用于计算的向量若被意外缩放，舰船或导弹可能瞬间改变速度。几何公式尚未得出结果，输入数据已经被上一行代码改写；此时排查方向和距离都得先回到向量的来源。

## 使用 `MathUtils` 计算空间关系

### 区分中心距离与实体距离

下面的局部片段接收两个战斗实体和一个测试坐标，同时计算中心距离、实体表面距离和范围判断：

```java
private void inspectDistance(CombatEntityAPI source, CombatEntityAPI target,
        Vector2f testPoint) {
    float centerDistance = MathUtils.getDistance(
            source.getLocation(), target.getLocation());
    float surfaceDistance = MathUtils.getDistance(source, target);
    float distanceSquared = MathUtils.getDistanceSquared(
            source.getLocation(), testPoint);
    boolean inRange = MathUtils.isWithinRange(source, testPoint, 600f);

    Vector2f midpoint = MathUtils.getMidpoint(
            source.getLocation(), target.getLocation());
    Vector2f point = MathUtils.getPoint(midpoint, 120f, 30f);
    Vector2f samePoint = MathUtils.getPointOnCircumference(
            midpoint, 120f, 30f);
    boolean inside = MathUtils.isPointWithinCircle(point, midpoint, 120f);
}
```

两个 `Vector2f` 传给 `getDistance(...)` 时，结果是中心坐标之间的直线距离。传入 `CombatEntityAPI` 时，计算会计入双方碰撞半径，因此 `surfaceDistance` 表示碰撞圆外缘之间还剩多少距离。实体与坐标的重载同样会计入该实体的碰撞半径，`isWithinRange(...)` 则直接把这个关系压缩成布尔结果。

`getDistanceSquared(...)` 的 `Vector2f` 重载直接计算距离平方，适合高频比较。实体重载则先取得计入碰撞半径的普通距离，再将结果平方；这个重载仍然完成了普通距离计算，不能靠方法名免费省掉它。

`getMidpoint(...)` 返回两点正中的新坐标。`getPoint(...)` 根据中心、半径和角度取得圆周坐标，`getPointOnCircumference(...)` 是含义更直白的别名；示例中的 `point` 与 `samePoint` 因此重合。`isPointWithinCircle(...)` 把圆周也算作范围内部，所以 `inside` 为 `true`。

### 生成随机分布并整理角度

粒子、闪光和散射效果通常不需要固定坐标，而需要明确的分布形状。下面的局部片段同时生成圆内、圆周和线段上的随机点，并计算从当前朝向转向目标朝向的最短角差：

```java
private Vector2f sampleEffectPoint(Vector2f center, Vector2f lineEnd,
        float currentFacing, float desiredFacing) {
    float size = MathUtils.getRandomNumberInRange(4f, 8f);
    Vector2f cloudPoint = MathUtils.getRandomPointInCircle(center, 30f);
    Vector2f ringPoint = MathUtils.getRandomPointOnCircumference(center, 60f);
    Vector2f linePoint = MathUtils.getRandomPointOnLine(center, lineEnd);

    float normalized = MathUtils.clampAngle(desiredFacing);
    float turn = MathUtils.getShortestRotation(currentFacing, normalized);
    float alpha = MathUtils.clamp(size / 8f, 0f, 1f);
    return turn < 0f ? linePoint : turn > 0f ? ringPoint : cloudPoint;
}
```

`getRandomNumberInRange(...)` 同时提供浮点和整数重载，最小值与最大值决定返回区间。`getRandomPointInCircle(...)` 在圆的面积内均匀分布；它不会把大量点挤在中心。`getRandomPointOnCircumference(...)` 固定半径，只随机角度，连续绘制后会形成清晰圆环。`getRandomPointOnLine(...)` 的结果始终位于两个端点之间。

`clampAngle(...)` 把任意角度整理到一圈以内，适合保存朝向。`clamp(...)` 把普通数值限制在给定上下界，导弹 AI 用它限制提前时间。`getShortestRotation(...)` 返回最短角差：正值表示向左转，负值表示向右转。跨过 `0` 度时仍会选择较短方向，不需要手工编写 `360 - angle` 的分支。

## 使用 `VectorUtils` 修改方向向量

向量工具把坐标关系转换成可继续缩放和旋转的方向。下面的局部片段从起点指向终点，并分别演示目标向量重载与原地修改重载：

```java
private Vector2f buildPredictionVector(Vector2f start, Vector2f end,
        float angleOffset, float length) {
    float angle = VectorUtils.getAngle(start, end);
    Vector2f direction = VectorUtils.getDirectionalVector(start, end);
    float facing = VectorUtils.getFacing(direction);
    if (VectorUtils.isZeroVector(direction)) {
        return new Vector2f();
    }

    Vector2f rotatedCopy = VectorUtils.rotate(
            direction, angleOffset, new Vector2f());
    Vector2f resizedCopy = VectorUtils.resize(
            rotatedCopy, length, new Vector2f());

    VectorUtils.rotate(direction, angle - facing);
    VectorUtils.resize(direction, length);
    return resizedCopy;
}
```

`getAngle(start, end)` 返回起点朝向终点的世界角度，`getFacing(vector)` 把一个方向向量转换为同一套角度。`getDirectionalVector(...)` 返回从起点指向终点的单位向量；两点重合时结果是零向量，`isZeroVector(...)` 可以在继续计算朝向前识别它。

带 `dest` 参数的 `rotate(...)` 与 `resize(...)` 不修改输入，而把结果写进目标向量。省略 `dest` 的重载会直接修改传入对象，并返回同一个对象以便连续调用。上例中的 `rotatedCopy` 和 `resizedCopy` 与输入分离，后两次调用则改变了 `direction`。

导弹预测线从单位 X 向量开始，先按弹体朝向旋转，再缩放到固定长度。若把 `missile.getVelocity()` 直接传给无 `dest` 的 `resize(...)`，导弹速度会被改成预测线长度；这类错误通常很醒目，只是方向未必指向您希望检查的代码行。

## 使用 `CollisionUtils` 检查预测路径

碰撞工具处理的是有限线段。下面的局部片段检查预测路径与目标实体的交点，同时检查它是否与另一条边界线相交：

```java
private Vector2f inspectPath(Vector2f start, Vector2f end,
        CombatEntityAPI target, Vector2f edgeStart, Vector2f edgeEnd) {
    Vector2f hullHit = CollisionUtils.getCollisionPoint(start, end, target);
    Vector2f edgeHit = CollisionUtils.getCollisionPoint(
            start, end, edgeStart, edgeEnd);
    boolean endInside = CollisionUtils.isPointWithinBounds(end, target);
    return hullHit != null ? hullHit : edgeHit != null ? edgeHit
            : endInside ? new Vector2f(end) : null;
}
```

目标具有 exact bounds 时，实体重载返回有限预测线遇到的最近边界点副本；起点已在舰体内部时，返回起点副本。**但缺少 exact bounds 时，方法改用碰撞圆判断，发生碰撞就直接返回实体中心 `target.getLocation()` 的活引用，而非副本。** 两条线段的重载返回交点副本，不过若待测线段的端点贴近目标线段极近 (距离平方 `<= 0.11111`) 也会直接返回传入的端点引用；未相交时返回 `null`。后续还要就地修改交点的话，一律先包一层 `new Vector2f(...)`。

`isPointWithinBounds(...)` 检查具体坐标是否位于实体 bounds 内。它与碰撞圆判断不是同一件事：不规则舰体的角落可能位于碰撞圆内，却不在真实舰体边界内。

预测起点已经位于 bounds 内部时，标记就在起点，即使整条线都在舰体内。需要区分 "从外部进入" 与 "已经在内部" 时，读取 `isPointWithinBounds(start, target)`；这两种状态都可能得到有效坐标。

## 使用 `EllipseUtils` 描述旋转椭圆

椭圆适合描述沿舰首方向拉长的搜索区、效果区或调试轮廓。下面的局部片段使用固定中心、宽度、高度和旋转角，覆盖 `EllipseUtils` 的全部公开方法：

```java
private boolean inspectEllipse(Vector2f center, Vector2f testPoint) {
    float width = 320f;
    float height = 140f;
    float rotation = 35f;

    Vector2f edge = EllipseUtils.getPointOnEllipse(
            center, width, height, rotation, 20f);
    Vector2f randomEdge = EllipseUtils.getRandomPointOnEllipse(
            center, width, height, rotation);
    Vector2f randomInside = EllipseUtils.getRandomPointInEllipse(
            center, width, height, rotation);
    return EllipseUtils.isPointWithinEllipse(
            testPoint, center, width, height, rotation);
}
```

`width` 与 `height` 在这组 API 中是未旋转 X、Y 方向的半轴长度，`320f`、`140f` 对应完整尺寸 640 × 280 su，`rotation` 再旋转整条轮廓。**参数名写作宽高，实际传入的是半轴。** `getPointOnEllipse(...)` 的最后一个角度选择轮廓上的确定位置，`getRandomPointOnEllipse(...)` 沿轮廓取点，`getRandomPointInEllipse(...)` 在内部面积均匀取点，`isPointWithinEllipse(...)` 接受内部与边界，并带有少量浮点容差。

连续绘制 `randomEdge` 会形成一圈随 `rotation` 转动的轮廓，绘制 `randomInside` 则会逐渐填满整片区域。导弹 AI 用目标朝向作为旋转角，因此紫色粒子会随着目标转向，而不是固定在世界坐标轴上。

## 接回导弹 AI 与调试绘制

弹体与武器引用已经准备好，现在把计算接成 `GearInGeometryMissileAI`。保存到 `src/data/scripts/gearin/lib/GearInGeometryMissileAI.java`，完整类见本节成品。

制导按本节开头的行为执行，调试绘制另外沿导弹当前朝向延伸 `900` su。青色粒子显示预测线，`CollisionUtils.getCollisionPoint(...)` 返回有效结果时再用橙色标记交点；黄色勾出碰撞圆，紫色显示随目标朝向旋转的椭圆。`IntervalUtil` 把每枚导弹的绘制间隔固定为 `0.08f` 秒。

在现有 `GearInModPlugin` 中加入下面的局部方法。projectile ID 命中时，`PickPriority.MOD_SET` 让 GearIn 的选择覆盖更低优先级结果；其他导弹继续交给后续插件或原版逻辑：

```java
@Override
public PluginPick<MissileAIPlugin> pickMissileAI(
        MissileAPI missile, ShipAPI launchingShip) {
    if ("GearIn_geometry_missile".equals(missile.getProjectileSpecId())) {
        return new PluginPick<>(
                new GearInGeometryMissileAI(missile, launchingShip),
                PickPriority.MOD_SET);
    }
    return null;
}
```

在入口类的 import 区域追加以下局部声明，`PickPriority` 来自 CampaignPlugin 的嵌套枚举：

```java
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.gearin.lib.GearInGeometryMissileAI;
```

发射时 `launchingShip.getShipTarget()` 决定初始目标，AI 本身不搜索附近实体；范围查询和自动目标选择由 [C18-S3] 负责。

## 常用方法速查表

| 类               | 方法                                             | 结果或修改对象                            |
| ---------------- | ------------------------------------------------ | ----------------------------------------- |
| `MathUtils`      | `getDistance(...)`                               | 返回坐标中心距离，实体重载计入碰撞半径    |
| `MathUtils`      | `getDistanceSquared(...)`                        | 返回距离平方，坐标重载适合热路径比较      |
| `MathUtils`      | `isWithinRange(...)`                             | 返回是否处于指定范围                      |
| `MathUtils`      | `getPoint(...)`、`getPointOnCircumference(...)`  | 返回给定圆周角度的新坐标                  |
| `MathUtils`      | `getRandomPointInCircle(...)`                    | 返回圆内均匀面积分布的新坐标              |
| `MathUtils`      | `getRandomPointOnCircumference(...)`             | 返回固定半径圆周上的新坐标                |
| `MathUtils`      | `getRandomPointOnLine(...)`                      | 返回有限线段上的新坐标                    |
| `MathUtils`      | `getRandomNumberInRange(...)`                    | 返回指定区间内的随机数                    |
| `MathUtils`      | `clamp(...)`、`clampAngle(...)`                  | 返回限制后的数值或标准角度                |
| `MathUtils`      | `getShortestRotation(...)`                       | 返回带方向的最短角差                      |
| `MathUtils`      | `getMidpoint(...)`、`isPointWithinCircle(...)`   | 返回中点或圆形范围判断                    |
| `VectorUtils`    | `getAngle(...)`、`getFacing(...)`                | 返回世界角度                              |
| `VectorUtils`    | `getDirectionalVector(...)`、`isZeroVector(...)` | 返回单位方向或零向量判断                  |
| `VectorUtils`    | `resize(...)`、`rotate(...)`                     | 修改输入向量，或把结果写入 `dest`         |
| `CollisionUtils` | `getCollisionPoint(...)`                         | 返回实体或线段交点，没有交点时返回 `null` |
| `CollisionUtils` | `isPointWithinBounds(...)`                       | 返回坐标是否处于实体 bounds               |
| `EllipseUtils`   | `getPointOnEllipse(...)`                         | 返回旋转椭圆轮廓上的确定坐标              |
| `EllipseUtils`   | `getRandomPointOnEllipse(...)`                   | 返回旋转椭圆轮廓上的随机坐标              |
| `EllipseUtils`   | `getRandomPointInEllipse(...)`                   | 返回旋转椭圆内部的随机坐标                |
| `EllipseUtils`   | `isPointWithinEllipse(...)`                      | 返回坐标是否处于旋转椭圆内                |

## 完整最小例

这个类接收一枚已经生成的导弹，持续计算提前点、转向和预测交点。`DEBUG` 保持为 `true` 时显示四种几何粒子；改为 `false` 只会停止绘制，不改变制导。

::: details 完整成品：GearInGeometryMissileAI

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.EllipseUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Guides one missile toward the launching ship's selected target.
 * LazyLib supplies the lead, turn, prediction, collision, and debug geometry.
 */
public final class GearInGeometryMissileAI implements MissileAIPlugin, GuidedMissileAI {
    private static final boolean DEBUG = true;
    private static final float MAX_LEAD_TIME = 2f;
    private static final float PREDICTION_LENGTH = 900f;
    private static final Vector2f ZERO = new Vector2f();

    private static final Color LINE_COLOR = new Color(80, 220, 255, 220);
    private static final Color HIT_COLOR = new Color(255, 140, 60, 240);
    private static final Color CIRCLE_COLOR = new Color(255, 220, 80, 220);
    private static final Color ELLIPSE_COLOR = new Color(210, 100, 255, 220);

    private final MissileAPI missile;
    private final IntervalUtil debugInterval = new IntervalUtil(0.08f, 0.08f);
    private CombatEntityAPI target;

    public GearInGeometryMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        this.missile = missile;
        target = launchingShip == null ? null : launchingShip.getShipTarget();
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || missile.isFading() || missile.isFizzling()) {
            return;
        }

        missile.giveCommand(ShipCommand.ACCELERATE);
        if (!isTrackable(engine)) {
            return;
        }

        Vector2f leadPoint = calculateLeadPoint();
        steerToward(leadPoint);

        Vector2f predictionEnd = createPredictionEnd();
        Vector2f collisionPoint = CollisionUtils.getCollisionPoint(
                missile.getLocation(), predictionEnd, target);
        drawDebugGeometry(engine, amount, predictionEnd, collisionPoint);
    }

    private boolean isTrackable(CombatEngineAPI engine) {
        if (target == null || !engine.isEntityInPlay(target)
                || target.getCollisionClass() == CollisionClass.NONE) {
            return false;
        }
        return !(target instanceof ShipAPI) || !((ShipAPI) target).isPhased();
    }

    private Vector2f calculateLeadPoint() {
        float distance = MathUtils.getDistance(missile.getLocation(), target.getLocation());
        float speed = Math.max(1f, missile.getMaxSpeed());
        float leadTime = MathUtils.clamp(distance / speed, 0f, MAX_LEAD_TIME);

        Vector2f leadOffset = new Vector2f(target.getVelocity());
        leadOffset.scale(leadTime);
        return Vector2f.add(target.getLocation(), leadOffset, new Vector2f());
    }

    private void steerToward(Vector2f leadPoint) {
        float desiredFacing = VectorUtils.getAngle(missile.getLocation(), leadPoint);
        float turn = MathUtils.getShortestRotation(missile.getFacing(), desiredFacing);
        if (turn < 0f) {
            missile.giveCommand(ShipCommand.TURN_RIGHT);
        } else if (turn > 0f) {
            missile.giveCommand(ShipCommand.TURN_LEFT);
        }

        if (!VectorUtils.isZeroVector(missile.getVelocity())) {
            float velocityFacing = VectorUtils.getFacing(missile.getVelocity());
            float slip = MathUtils.getShortestRotation(missile.getFacing(), velocityFacing);
            if (slip < -20f) {
                missile.giveCommand(ShipCommand.STRAFE_LEFT);
            } else if (slip > 20f) {
                missile.giveCommand(ShipCommand.STRAFE_RIGHT);
            }
        }
    }

    private Vector2f createPredictionEnd() {
        Vector2f direction = new Vector2f(1f, 0f);
        VectorUtils.rotate(direction, missile.getFacing());
        VectorUtils.resize(direction, PREDICTION_LENGTH);
        return Vector2f.add(missile.getLocation(), direction, new Vector2f());
    }

    private void drawDebugGeometry(CombatEngineAPI engine, float amount,
            Vector2f predictionEnd, Vector2f collisionPoint) {
        if (!DEBUG) {
            return;
        }
        debugInterval.advance(amount);
        if (!debugInterval.intervalElapsed()) {
            return;
        }

        addMarker(engine, MathUtils.getRandomPointOnLine(
                missile.getLocation(), predictionEnd), LINE_COLOR);
        if (collisionPoint != null) {
            addMarker(engine, MathUtils.getRandomPointInCircle(
                    collisionPoint, 12f), HIT_COLOR);
        }

        float radius = target.getCollisionRadius();
        addMarker(engine, MathUtils.getRandomPointOnCircumference(
                target.getLocation(), radius), CIRCLE_COLOR);
        addMarker(engine, EllipseUtils.getRandomPointOnEllipse(
                target.getLocation(), radius * 1.6f, radius * 0.75f,
                target.getFacing()), ELLIPSE_COLOR);
    }

    private void addMarker(CombatEngineAPI engine, Vector2f point, Color color) {
        float size = MathUtils.getRandomNumberInRange(4f, 8f);
        engine.addSmoothParticle(point, ZERO, size, 1f, 0.2f, color);
    }
}
```

:::

### 进游戏核对

执行 `gradle jar`，重启游戏进入带德瓦林测试舰的模拟战 (`GearIn_geometry_missile` 已绑定并挂上 AI 选择插件)：

- 锁定静止目标并分别让其位于导弹左右两侧发射，导弹按最短角差转向目标，青色预测线、黄色碰撞圆与随目标旋转的紫色椭圆同步出现；
- 让目标横向移动并分别使预测线穿过与偏离舰体：导弹朝前方线性提前点修正，预测线命中 exact bounds 时生成橙色交点粒子；
- 目标进入相位或暂时改为 `CollisionClass.NONE` 时导弹保持加速直飞，恢复后继续跟踪同一目标；
- 将 `DEBUG` 设为 `false` 重建后再试，调试粒子完全停止而导弹制导正常。

## 常见问题

- **导弹总向目标当前坐标追尾**：提前时间使用了零速度、零距离或未经限制的错误输入。
    - **解决方案**：用中心距离除以至少 `1f` 的最大速度，并通过 `MathUtils.clamp(...)` 限制结果后缩放目标速度。

- **导弹左右转向相反**：`getShortestRotation(...)` 的正负分支与 `TURN_LEFT`、`TURN_RIGHT` 对应颠倒。
    - **解决方案**：保持正值左转、负值右转，并用目标分别位于两侧的场景检查分支。

- **计算预测线后导弹速度突然改变**：无 `dest` 的 `VectorUtils.resize(...)` 或 `rotate(...)` 直接修改了引擎速度向量。
    - **解决方案**：先复制 `getVelocity()`，或把独立目标向量传给带 `dest` 的重载。

- **预测线附近没有橙色粒子**：有限线段实际未到达目标边界，或目标处于不可跟踪状态。
    - **解决方案**：核对当前朝向、900 su 预测长度与目标碰撞状态，并用 `isPointWithinBounds(...)` 判断起点是否已在舰体内。

- **所有导弹都持续生成大量粒子**：调试绘制没有经过 `DEBUG` 与 `IntervalUtil` 限制。
    - **解决方案**：只在计时区间结束后绘制一组标记，发布构建把 `DEBUG` 设为 `false`。

## 建议参考项

- `org.lazywizard.lazylib.MathUtils`：距离、范围、角度、坐标与随机分布；
- `org.lazywizard.lazylib.VectorUtils`：方向、朝向、旋转、缩放与目标向量重载；
- `org.lazywizard.lazylib.CollisionUtils`：有限路径与实体 bounds 的交点；
- `org.lazywizard.lazylib.EllipseUtils`：旋转椭圆的轮廓、内部采样与范围判断；
- `com.fs.starfarer.api.combat.MissileAIPlugin`：导弹 AI 的每帧入口；
- `com.fs.starfarer.api.combat.GuidedMissileAI`：导弹目标的读取与替换接口。
