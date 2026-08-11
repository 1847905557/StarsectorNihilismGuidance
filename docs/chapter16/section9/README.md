# 用 MagicLib 为导弹制作可切断尾迹

导弹瞬移最容易暴露尾迹的生命周期错误：弹体到了新位置，旧节点仍从原处连来。本节为每枚发射物建立独立跟踪器，按固定间隔追加两层尾迹，并在位置跳变、命中、淡出或离场时切断关联。

*本节预期阅读时间：~22 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 从 `OnFireEffectPlugin` 为每枚发射物创建独立尾迹状态；
- 为同一弹体分配并跨帧复用两条 MagicTrail ID；
- 按时间间隔追加实体关联的尾迹节点；
- 检测位置跳变，切断旧段并用新 ID 从新位置续接；
- 在命中、淡出或离场时移除跟踪插件。

## 前置知识

- [C7-S1] 的战斗插件、暂停守卫与每帧时间；
- [C7-S4] 的战斗实体有效性与收尾；
- 能够在 `.proj` 中为弹体武器注册 `OnFireEffectPlugin`。

## 明确尾迹生命周期

测试导弹持续飞行时应显示内层实体和外层辉光。把弹体瞬移超过 250 su 后，旧尾迹留在原处淡出，新尾迹从传送终点开始；两段之间没有横跨战场的连接。弹体命中或进入淡出后，不再产生新节点。

## 文件与 API 链路

武器发射回调提供 `DamagingProjectileAPI` 与当前 `CombatEngineAPI`。每枚弹体对应一个 `TrailTracker`；跟踪器从 `MagicTrailPlugin.getUniqueID()` 取得两条 ID，用 `addTrailMemberSimple()` 追加节点，用 `cutTrailsOnEntity()` 结束实体关联，最后通过 `CombatEngineAPI.removePlugin()` 退出本场战斗。

### 为每枚发射物建立跟踪器

**插入位置：`GearInMagicTrail.onFire()`。** 不把 ID 放在武器效果实例上，而是让每枚发射物拥有自己的跟踪插件。

```java
public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon,
        CombatEngineAPI engine) {
    engine.addPlugin(new TrailTracker(projectile));
}
```

完成信号：连续发射两枚导弹时，各自形成独立尾迹，节点不会串到另一枚弹体上。

### 初始化贴图与两条尾迹 ID

**插入位置：`TrailTracker.init()`。** 贴图使用 MagicLib 在 `fx` 分类注册的资源，每条尾迹只在初始化或切断后取得一次 ID。

```java
bodySprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
glowSprite = Global.getSettings().getSprite("fx", "base_trail_contrail");
bodyTrailId = MagicTrailPlugin.getUniqueID();
glowTrailId = MagicTrailPlugin.getUniqueID();
lastLocation = new Vector2f(projectile.getLocation());
addSegments();
```

完成信号：发射当帧已经登记首组节点，后续帧继续复用相同的两条 ID。

### 按固定间隔追加两层节点

**插入位置：`TrailTracker.advance()` 的有效弹体分支。** 累积非暂停时间，每 0.05 秒登记一组节点。

```java
segmentTimer += amount;
if (segmentTimer >= SEGMENT_INTERVAL) {
    segmentTimer = 0f;
    addSegments();
}
```

完成信号：帧率变化时节点密度由时间间隔控制；实体层与辉光层沿同一路径连续延伸。

### 在位置跳变后换用新 ID

**插入位置：追加节点之前。** 比较上一帧和当前帧坐标；超过 250 su 时先切断全部实体关联尾迹，再为两层分别取得新 ID。

```java
if (MathUtils.getDistance(lastLocation, projectile.getLocation())
        > TELEPORT_DISTANCE) {
    MagicTrailPlugin.cutTrailsOnEntity(projectile);
    bodyTrailId = MagicTrailPlugin.getUniqueID();
    glowTrailId = MagicTrailPlugin.getUniqueID();
}
```

完成信号：传送前的节点按自身寿命淡出，传送后的首个节点不会连接到旧坐标。

### 在弹体终止时停止跟踪

**插入位置：`advance()` 的暂停守卫之后。** 命中、淡出或离场都意味着不再追加节点；切断后同时移除本枚弹体的跟踪插件。

```java
if (!engine.isEntityInPlay(projectile)
        || projectile.isFading() || projectile.didDamage()) {
    cutAndRemove();
    return;
}
```

完成信号：弹体消失后旧节点只按各自的 `in/main/out` 时长淡出，插件不再留在每帧列表中空转。

## 让尾迹无法连成线

下面是**局部故障片段**：

```java
float trailId = MagicTrailPlugin.getUniqueID();
MagicTrailPlugin.addTrailMemberSimple(projectile, trailId, sprite, location,
        0f, facing, 14f, 3f, color, 0.8f, 0.05f, 0.2f, 0.8f, true);
```

实际症状：每次追加都属于一条新尾迹，画面只剩彼此独立的短节点，无法形成连续条带。

原因：MagicTrail 用 ID 把跨帧节点归入同一条轨迹。ID 的生命周期必须覆盖整段连续飞行。

下面是**局部修正片段**：

```java
bodyTrailId = MagicTrailPlugin.getUniqueID();
// 后续 addTrailMemberSimple() 持续复用 bodyTrailId。
```

复验：让导弹直飞 2 秒，记录 ID 只在初始化时产生；再瞬移一次，确认 ID 只在切断点更新。

## 完整最小例

完整类既是武器发射效果，也是每枚弹体跟踪器的创建入口。两层尾迹都与弹体关联，因此一次 `cutTrailsOnEntity()` 可以同时结束它们。

::: details 完整成品：GearInMagicTrail

```java
package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.List;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;

public class GearInMagicTrail implements OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon,
            CombatEngineAPI engine) {
        engine.addPlugin(new TrailTracker(projectile));
    }

    private static final class TrailTracker extends BaseEveryFrameCombatPlugin {
        private static final float SEGMENT_INTERVAL = 0.05f;
        private static final float TELEPORT_DISTANCE = 250f;

        private final DamagingProjectileAPI projectile;
        private CombatEngineAPI engine;
        private SpriteAPI bodySprite;
        private SpriteAPI glowSprite;
        private float bodyTrailId;
        private float glowTrailId;
        private float segmentTimer;
        private Vector2f lastLocation;

        private TrailTracker(DamagingProjectileAPI projectile) {
            this.projectile = projectile;
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
            bodySprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
            glowSprite = Global.getSettings().getSprite("fx", "base_trail_contrail");
            bodyTrailId = MagicTrailPlugin.getUniqueID();
            glowTrailId = MagicTrailPlugin.getUniqueID();
            lastLocation = new Vector2f(projectile.getLocation());
            addSegments();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine == null || engine.isPaused()) {
                return;
            }
            if (!engine.isEntityInPlay(projectile)
                    || projectile.isFading() || projectile.didDamage()) {
                cutAndRemove();
                return;
            }
            if (MathUtils.getDistance(lastLocation, projectile.getLocation())
                    > TELEPORT_DISTANCE) {
                MagicTrailPlugin.cutTrailsOnEntity(projectile);
                bodyTrailId = MagicTrailPlugin.getUniqueID();
                glowTrailId = MagicTrailPlugin.getUniqueID();
            }
            segmentTimer += amount;
            if (segmentTimer >= SEGMENT_INTERVAL) {
                segmentTimer = 0f;
                addSegments();
            }
            lastLocation.set(projectile.getLocation());
        }

        private void addSegments() {
            Vector2f location = projectile.getLocation();
            float facing = projectile.getFacing();
            MagicTrailPlugin.addTrailMemberSimple(
                    projectile, bodyTrailId, bodySprite, location,
                    0f, facing, 14f, 3f, new Color(120, 220, 255),
                    0.8f, 0.05f, 0.2f, 0.8f, true);
            MagicTrailPlugin.addTrailMemberSimple(
                    projectile, glowTrailId, glowSprite, location,
                    0f, facing, 22f, 6f, new Color(60, 120, 255),
                    0.55f, 0.05f, 0.15f, 0.6f, true);
        }

        private void cutAndRemove() {
            MagicTrailPlugin.cutTrailsOnEntity(projectile);
            engine.removePlugin(this);
        }
    }
}
```

:::

## 验证方案

前置数据：武器已经注册 `GearInMagicTrail`，MagicLib 的两张 `fx` 贴图可读取，测试入口能够移动仍在场的弹体。

1. 连续发射两枚导弹，观察每枚弹体各自拥有内层与辉光层。
2. 让第一枚直飞至少 2 秒，确认尾迹连续，第二枚的节点没有接入第一枚。
3. 把仍在飞行的弹体瞬移 600 su，观察旧段与新段之间没有连接线。
4. 暂停 2 秒，确认弹体和尾迹节点都停止推进；恢复后继续生成。
5. 让导弹命中目标，再观察已有节点淡出，命中点之后不再新增节点。

预期结果：

- 每枚弹体创建 1 个跟踪插件、2 条独立尾迹 ID；
- 非暂停状态下每 0.05 秒最多追加一组两层节点；
- 位置跳变超过 250 su 时，两条旧尾迹同时切断，新节点使用新 ID；
- 命中、淡出或离场后，跟踪插件从引擎移除；
- `starsector.log` 没有未处理异常。

## 故障分析

- **尾迹只有一串短亮点**：每次追加都重新取得 ID，节点没有落到同一条尾迹。

    - **解决方案**：把 ID 保存在弹体跟踪器中，只在初始化或切段时更新。

- **传送后出现横跨战场的直线**：位置跳变检测发生在追加节点之后，旧段已经跨过跳跃。

    - **解决方案**：先比较上一帧坐标并调用切断，再提交新位置节点。

- **两枚导弹的尾迹连在一起**：ID 保存在共享武器效果字段。

    - **解决方案**：让 `onFire()` 为每枚 `DamagingProjectileAPI` 创建独立跟踪器。

- **弹体消失后插件仍在推进**：终止分支只切断尾迹，没有移除跟踪器。

    - **解决方案**：同步调用 `engine.removePlugin(this)`。

## 总结

MagicTrail 的连续性由 "同一弹体、同一段飞行、同一 ID" 决定。发射时建立状态，飞行时按时间追加，位置跳变时切段换 ID，实体终止时切断并移除跟踪器；这四个边界共同保证尾迹既连续，又不会跨越不连续的位置。

## 建议参考项

- `MagicTrailPlugin.getUniqueID`：创建战斗内尾迹标识；
- `MagicTrailPlugin.addTrailMemberSimple`：追加实体关联的尾迹节点；
- `MagicTrailPlugin.cutTrailsOnEntity`：同时切断同一实体关联的现有尾迹；
- `CombatEngineAPI.removePlugin`：结束单枚弹体的跟踪器。
