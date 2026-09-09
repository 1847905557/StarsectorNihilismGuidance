# 用 LazyLib 为战术系统寻找安全跃迁点

跃迁系统不能只把舰船丢到鼠标坐标：候选点可能穿过舰体、落在碰撞圈里，或迫使舰船突然反向。本节生成一组椭圆候选点，逐个排除危险路径，再把唯一高分坐标交给战术系统。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `EllipseUtils` 围绕固定起点生成一组带朝向的候选坐标；
- 用 `CollisionUtils` 排除移动线段穿过战斗实体的候选；
- 用距离与最短角差建立可解释的评分；
- 在候选确定前隔离引擎持有的可变位置向量；
- 把唯一结果交给战术系统，并为无可用落点保留取消分支。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

GearIn 相位驱动器要向舰首前方跃迁约 700 su，但战场中央有大型舰船横穿。正确结果是落点偏向仍然畅通的一侧，路径上出现阻挡时自动换候选。

## 文件与 API 链路

`EllipseUtils` 生成保持舰首方向的候选环；`CollisionUtils` 判断从当前位置到候选点的线段是否穿过实体；`MathUtils` 和 `VectorUtils` 计算距离、角差与朝向。最终对象只保存坐标和评分。

### 生成十二个椭圆候选点

`GearInSafeJumpPlanner.find()` 在循环开头于舰首前方 180 度范围均匀取点：

```java
float angle = -90f + i * 180f / 11f;
Vector2f point = EllipseUtils.getPointOnEllipse(origin, 700f, 420f, facing, angle);
```

调试绘制能看到 12 个候选，而非一个鼠标点。

### 排除穿过舰体的路径

`find()` 在候选过滤处对每个阻挡实体执行线段碰撞检查；调用方传入的 `blockers` 只包含需要避让的其他实体，不包含正在跃迁的舰船：

```java
if (CollisionUtils.getCollisionPoint(origin, point, blocker) != null) {
    blocked = true;
}
```

被大型舰遮挡的一侧不再进入评分。

### 用朝向代价压低急转弯

`find()` 在评分处同时考虑跃迁距离与舰首角差：

```java
float turn = Math.abs(MathUtils.getShortestRotation(facing, angle));
float score = MathUtils.getDistance(origin, point) - turn * 2f;
```

正前方畅通时优先选择正前方，受阻时才偏转。

### 只保留当前最高分

`find()` 在 `best` 更新处把候选坐标和评分封装为独立结果：

```java
if (best == null || score > best.getScore()) {
    best = new GearInJumpCandidate(point, score);
}
```

循环结束后至多返回一个 GearInJumpCandidate。

### 把安全点交给系统状态机

战术系统 `apply()` 在 `IN` 阶段入口先处理 `null`，再把结果坐标写入舰船位置：

```java
GearInJumpCandidate jump = planner.find(ship.getLocation(), ship.getFacing(), blockers);
if (jump == null) return;
ship.getLocation().set(jump.getPoint());
```

没有安全点时系统取消；有安全点时舰船只移动一次。

## 完整最小例

`GearInJumpCandidate` 在构造和读取时都复制坐标，`GearInSafeJumpPlanner` 只负责筛选。实际移动仍由战术系统状态机决定。

::: details 完整成品：GearInJumpCandidate

```java
package data.scripts.gearin.lib;

import org.lwjgl.util.vector.Vector2f;

public final class GearInJumpCandidate {
    private final Vector2f point;
    private final float score;

    public GearInJumpCandidate(Vector2f point, float score) {
        this.point = new Vector2f(point);
        this.score = score;
    }

    public Vector2f getPoint() {
        return new Vector2f(point);
    }

    public float getScore() {
        return score;
    }
}
```

:::

::: details 完整成品：GearInSafeJumpPlanner

```java
package data.scripts.gearin.lib;

import java.util.List;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.EllipseUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEntityAPI;

public final class GearInSafeJumpPlanner {
    private static final int CANDIDATE_COUNT = 12;

    public GearInJumpCandidate find(Vector2f origin, float facing,
            List<CombatEntityAPI> blockers) {
        GearInJumpCandidate best = null;
        for (int i = 0; i < CANDIDATE_COUNT; i++) {
            float angle = -90f + i * 180f / (CANDIDATE_COUNT - 1f);
            Vector2f point = EllipseUtils.getPointOnEllipse(origin, 700f, 420f, facing, angle);
            if (crossesBlocker(origin, point, blockers)) {
                continue;
            }
            float score = MathUtils.getDistance(origin, point)
                    - Math.abs(MathUtils.getShortestRotation(facing, angle)) * 2f;
            if (best == null || score > best.getScore()) {
                best = new GearInJumpCandidate(point, score);
            }
        }
        return best;
    }

    private boolean crossesBlocker(Vector2f start, Vector2f end,
            List<CombatEntityAPI> blockers) {
        for (CombatEntityAPI blocker : blockers) {
            if (CollisionUtils.getCollisionPoint(start, end, blocker) != null) {
                return true;
            }
        }
        return false;
    }
}
```

:::

## 验证方案

前置数据：测试舰舰首朝向空旷区域，舰首左侧放置一艘大型舰，调试入口能绘制候选点和最终落点。

1. 在没有阻挡实体时触发系统，记录 12 个候选与最终落点。
2. 把大型舰移到正前方，再次触发系统，观察穿过其碰撞范围的候选被排除。
3. 让阻挡实体覆盖全部候选，确认规划器返回 `null`，系统不移动舰船。

预期结果：

- 每次规划固定生成 12 个候选，碰撞候选不会进入评分；
- 正前方畅通时角差代价使落点保持靠近舰首方向，受阻后结果转向仍可通行的一侧；
- 全部候选受阻时返回 `null`，舰船坐标保持不变；
- `starsector.log` 没有未处理异常。

## 故障分析

- **计算候选时舰船位置不断变化**：候选变量直接引用了 `ship.getLocation()`。

    - **解决方案**：复制起点后再计算，最终结果确定前不写回引擎向量。

- **规划器始终返回 null**：阻挡列表包含正在跃迁的舰船本身。

    - **解决方案**：调用前排除该舰船。

- **候选点左右颠倒**：点方向 = `offset + angle`(单一度数制)。

    - **解决方案**：检查两角相加方向与正负号。

- **终点安全但移动路径擦过模块**：阻挡列表遗漏模块实体，或只检查了终点。

    - **解决方案**：加入模块并检测完整移动线段。

## 总结

安全跃迁的核心是先生成多个可替代坐标，再让碰撞与朝向共同淘汰它们。任何会修改引擎向量的操作都推迟到候选已经唯一确定之后。

## 建议参考项

- `org.lazywizard.lazylib.EllipseUtils`：`getPointOnEllipse` 生成带旋转的候选点；
- `org.lazywizard.lazylib.CollisionUtils`：`getCollisionPoint` 检查移动线段与实体碰撞；
- `org.lazywizard.lazylib.MathUtils`：`getShortestRotation` 计算带环绕的最短角差。
