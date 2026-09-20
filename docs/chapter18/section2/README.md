# 使用 LazyLib 几何工具

战斗脚本经常需要回答同一组空间问题：目标有多远、某个方向指向哪里、导弹应该向哪边转，以及一条预测线会先碰到舰体的什么位置。LazyLib 把这些计算整理成稳定的工具方法，本节用可见的粒子标记逐一观察结果，并将它们组合为一个制导导弹 AI。

*本节预期阅读时间：约 28 分钟。*

*本节预期操作时间：约 35 分钟。*

## 学习目标

完成后，您将能够：

- 区分坐标距离、实体表面距离和距离平方的适用场景；
- 用随机点、角度和向量方法建立可观察的空间分布；
- 判断向量方法会返回新坐标还是修改传入对象；
- 取得预测线与实体 bounds 的交点；
- 用旋转椭圆描述随目标朝向变化的区域；
- 将几何计算接入一个具有线性提前和路径标记的导弹 AI。

## 前置知识

- [C7-S1] 的 Java 类、方法、字段与 `BaseModPlugin`；
- [C8-S2] 的战斗实体、坐标、速度与碰撞类型；
- [C8-S3] 的每帧调用、暂停判断与 `IntervalUtil`；
- [C18-S1] 的 LazyLib 依赖声明与编译 classpath。

## 认识坐标、角度与可变向量

战斗空间使用 `Vector2f` 保存二维坐标和向量。同一个对象既可以表示世界坐标，也可以表示从原点出发的方向；方法只看到两个浮点分量，因此含义由调用方决定。角度以度为单位，`0f` 指向世界坐标的正 X 轴，增加角度表示逆时针旋转。

`CombatEntityAPI.getLocation()` 和 `getVelocity()` 返回引擎正在使用的可变对象。直接调用 `set(...)`、`scale(...)`，或者把它传给会原地修改向量的方法，都会改变这份对象本身。只做计算时先用 `new Vector2f(source)` 复制，或者选择带 `dest` 参数的重载。

这一区分会直接影响战斗画面。用于计算的向量被意外缩放时，舰船或导弹可能瞬间改变速度；看起来像几何公式算错，实际是输入数据已经被上一行代码改写。

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

`getDistanceSquared(...)` 适合只比较坐标距离的高频循环，因为 `Vector2f` 重载省去了平方根。实体重载需要处理碰撞半径，效率与普通距离方法相同，不能靠方法名免费省下一次计算。

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

### 从两点取得方向和朝向

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

### 取得实体和线段交点

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

实体重载读取目标的 `BoundsAPI`，返回预测线进入舰体边界的坐标。两条线段的重载返回它们的交点。两个方法都以 `null` 表示没有相交，因此调用方应把交点是否存在与坐标本身分开判断。

`isPointWithinBounds(...)` 检查具体坐标是否位于实体 bounds 内。它与碰撞圆判断不是同一件事：不规则舰体的角落可能落在碰撞圆内，却不在真实舰体边界内。

实体交点方法有一个重要边界：当整条预测线已经完全位于目标 bounds 内部时，它没有进入边界的交点，方法会返回 `null`。需要识别这种状态时，再用 `isPointWithinBounds(start, target)` 检查起点。

## 使用 `EllipseUtils` 描述旋转椭圆

### 生成并判断椭圆坐标

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

`width` 与 `height` 是椭圆在未旋转 X、Y 轴上的完整尺寸，`rotation` 再把整条椭圆轮廓旋转指定角度。`getPointOnEllipse(...)` 的最后一个角度选择轮廓上的确定位置；`getRandomPointOnEllipse(...)` 只在轮廓取点；`getRandomPointInEllipse(...)` 在内部面积均匀取点；`isPointWithinEllipse(...)` 同时接受内部和边界上的坐标。

连续绘制 `randomEdge` 会形成一圈随 `rotation` 转动的轮廓，绘制 `randomInside` 则会逐渐填满整片区域。导弹 AI 用目标朝向作为旋转角，因此紫色粒子会随着目标转向，而不是固定在世界坐标轴上。

## 组合制导与碰撞预测

已经存在 projectile ID 为 `GearIn_geometry_missile`、具有可转向 `engineSpec` 的导弹弹体；本节只接入它的 AI。

`GearInGeometryMissileAI` 在构造时读取发射舰当前目标。每帧先发出加速命令，再用目标中心距离除以导弹最大速度估算提前时间，并将时间限制在 `0f` 到 `2f`。目标速度乘以这段时间，便得到线性提前量。

提前点只预测目标保持当前速度时的位置。它计算便宜，能让横向移动目标产生明显的追踪差异；目标急转、加速或减速时，预测也会随下一帧重新计算，不会假装自己掌握未来。

导弹朝向提前点时，`VectorUtils.getAngle(...)` 提供目标角度，`MathUtils.getShortestRotation(...)` 决定左右转向。速度向量与弹体朝向相差超过 `20f` 度时，AI 额外发出横移命令，减小弹体已经转向而速度仍偏向一侧的侧滑。

目标离开战场、进入相位或把碰撞类型改为 `CollisionClass.NONE` 时，AI 只保持加速直飞。`target` 引用仍然保留；同一目标恢复为可跟踪状态后，转向与预测会继续执行。

预测线沿导弹当前朝向延伸 `900f` su。`CollisionUtils.getCollisionPoint(...)` 找到它进入目标舰体的位置后，调试绘制用青色粒子显示整条路径，用橙色粒子标记交点，用黄色粒子勾出碰撞圆，再用紫色粒子显示旋转椭圆。`IntervalUtil` 把每枚导弹的绘制间隔固定为 `0.08f` 秒，避免每帧持续堆积粒子。

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

这个入口需要导入 `PluginPick`、`PickPriority`、`MissileAIPlugin`、`MissileAPI`、`ShipAPI` 和 `GearInGeometryMissileAI`。发射时 `launchingShip.getShipTarget()` 决定初始目标，AI 本身不搜索附近实体；范围查询和自动目标选择由 [C18-S3] 负责。

## 常用方法速查表

| 类 | 方法 | 结果或修改对象 |
|---|---|---|
| `MathUtils` | `getDistance(...)` | 返回坐标中心距离，实体重载计入碰撞半径 |
| `MathUtils` | `getDistanceSquared(...)` | 返回距离平方，坐标重载适合热路径比较 |
| `MathUtils` | `isWithinRange(...)` | 返回是否处于指定范围 |
| `MathUtils` | `getPoint(...)`、`getPointOnCircumference(...)` | 返回给定圆周角度的新坐标 |
| `MathUtils` | `getRandomPointInCircle(...)` | 返回圆内均匀面积分布的新坐标 |
| `MathUtils` | `getRandomPointOnCircumference(...)` | 返回固定半径圆周上的新坐标 |
| `MathUtils` | `getRandomPointOnLine(...)` | 返回有限线段上的新坐标 |
| `MathUtils` | `getRandomNumberInRange(...)` | 返回指定区间内的随机数 |
| `MathUtils` | `clamp(...)`、`clampAngle(...)` | 返回限制后的数值或标准角度 |
| `MathUtils` | `getShortestRotation(...)` | 返回带方向的最短角差 |
| `MathUtils` | `getMidpoint(...)`、`isPointWithinCircle(...)` | 返回中点或圆形范围判断 |
| `VectorUtils` | `getAngle(...)`、`getFacing(...)` | 返回世界角度 |
| `VectorUtils` | `getDirectionalVector(...)`、`isZeroVector(...)` | 返回单位方向或零向量判断 |
| `VectorUtils` | `resize(...)`、`rotate(...)` | 修改输入向量，或把结果写入 `dest` |
| `CollisionUtils` | `getCollisionPoint(...)` | 返回实体或线段交点，没有交点时返回 `null` |
| `CollisionUtils` | `isPointWithinBounds(...)` | 返回坐标是否处于实体 bounds |
| `EllipseUtils` | `getPointOnEllipse(...)` | 返回旋转椭圆轮廓上的确定坐标 |
| `EllipseUtils` | `getRandomPointOnEllipse(...)` | 返回旋转椭圆轮廓上的随机坐标 |
| `EllipseUtils` | `getRandomPointInEllipse(...)` | 返回旋转椭圆内部的随机坐标 |
| `EllipseUtils` | `isPointWithinEllipse(...)` | 返回坐标是否处于旋转椭圆内 |

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

        float radius = Math.max(20f, target.getCollisionRadius());
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

## 验证方案

前置数据：`GearIn_geometry_missile` 已绑定可转向导弹弹体，`GearInModPlugin` 已加入 AI 选择方法，并准备一艘能够发射该导弹的测试舰。

1. 执行 `gradle jar`，启动游戏并让测试舰锁定一个静止目标。
2. 发射导弹，分别让目标位于弹体左侧和右侧，观察导弹转向与四色粒子。
3. 让目标横向移动，再让预测线分别穿过和偏离目标舰体。
4. 让目标进入相位或暂时使用 `CollisionClass.NONE`，随后恢复原状态。
5. 把 `DEBUG` 改为 `false` 并重新构建，观察同一导弹的制导和粒子表现。
6. 恢复 `DEBUG = true`，连续发射多枚导弹并观察粒子密度。

预期结果：

- 导弹继承发射舰当前目标，并按最短角差向正确方向转弯；
- 横向移动目标使导弹朝目标前方的线性提前点转向；
- 青色粒子沿当前预测线分布，黄色粒子保持在目标碰撞圆周，紫色粒子随目标朝向旋转；
- 预测线穿过目标 bounds 时出现橙色交点粒子，偏离时不出现；
- 目标暂时不可跟踪时导弹保持加速直飞，恢复后继续跟踪同一目标；
- `DEBUG = false` 时几何粒子停止，导弹转向与加速继续生效；
- 连续发射时每枚导弹按固定间隔生成粒子，没有逐帧形成不受控制的粒子密度。

## 故障分析

- **导弹总向目标当前坐标追尾**：提前时间使用了零速度、零距离或未经限制的错误输入。

    - **解决方案**：用中心距离除以至少 `1f` 的最大速度，并通过 `MathUtils.clamp(...)` 限制结果后缩放目标速度。

- **导弹左右转向相反**：`getShortestRotation(...)` 的正负分支与 `TURN_LEFT`、`TURN_RIGHT` 对应颠倒。

    - **解决方案**：保持正值左转、负值右转，并用目标分别位于两侧的场景检查分支。

- **计算预测线后导弹速度突然改变**：无 `dest` 的 `VectorUtils.resize(...)` 或 `rotate(...)` 直接修改了引擎速度向量。

    - **解决方案**：先复制 `getVelocity()`，或把独立目标向量传给带 `dest` 的重载。

- **预测线穿过舰体却没有橙色粒子**：整条线已经位于目标 bounds 内，或弹体朝向使有限线段没有进入边界。

    - **解决方案**：同时检查预测起点的 `isPointWithinBounds(...)` 结果，并确认预测长度覆盖目标。

- **所有导弹都持续生成大量粒子**：调试绘制没有经过 `DEBUG` 与 `IntervalUtil` 限制。

    - **解决方案**：只在计时区间结束后绘制一组标记，发布构建把 `DEBUG` 设为 `false`。

## 总结

LazyLib 的几何工具把战斗空间拆成可以独立判断的关系：`MathUtils` 处理距离、角度和分布，`VectorUtils` 在方向与向量之间转换，`CollisionUtils` 把预测路径落到实体边界，`EllipseUtils` 提供带朝向的非圆形区域。

导弹 AI 只是在每帧把这些关系按顺序组合起来。排错时也可以沿相同顺序检查输入坐标、方向向量、转角符号和碰撞结果，而不必把整段制导代码当成一个无法拆开的公式。

## 建议参考项

- `org.lazywizard.lazylib.MathUtils`：距离、范围、角度、坐标与随机分布；
- `org.lazywizard.lazylib.VectorUtils`：方向、朝向、旋转、缩放与目标向量重载；
- `org.lazywizard.lazylib.CollisionUtils`：有限路径与实体 bounds 的交点；
- `org.lazywizard.lazylib.EllipseUtils`：旋转椭圆的轮廓、内部采样与范围判断；
- `com.fs.starfarer.api.combat.MissileAIPlugin`：导弹 AI 的每帧入口；
- `com.fs.starfarer.api.combat.GuidedMissileAI`：导弹目标的读取与替换接口。
