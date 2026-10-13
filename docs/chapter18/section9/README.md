# 创建 MagicLib 运行时尾迹

自动尾迹已经能按数据生成，这次由 Java 决定每一组节点何时出现、接在哪条轨迹上。沿用测试导弹，为每枚弹体保存主体与辉光的 ID，再用 `SimpleEntity` 生成沿初始方向前进的残影。导弹转向、跳变和命中以后，画面可以直接比较连续段、关联实体与节点位置的区别。

*本节预期阅读时间：\~32 分钟。*

*本节预期操作时间：\~50 分钟。*

## 学习目标

完成后，您将能够：

- 为每枚发射物分配并跨帧复用稳定 trail ID；
- 使用 `addTrailMemberSimple(...)` 配置一条基础尾迹；
- 使用 `addTrailMemberAdvanced(...)` 配置渐变、滚动和尺寸脉冲；
- 使用 `SimpleEntity` 为计算位置建立独立尾迹锚点；
- 在位置跳变和实体结束时切断关联并停止跟踪。

## 前置知识

- [C8-S2] 理解战斗实体有效性与对象结束边界；
- [C8-S3] 理解战斗插件、暂停判断与每帧时间；
- [C18-S8] 的 trail sprite、节点生命周期和自动尾迹字段；
- [C18-S2] 的 `GearIn_geometry_missile` 弹体与德瓦林测试引用；
- 能够在 `.proj` 中为弹体注册 `OnFireEffectPlugin`。

## 区分自动尾迹与运行时节点

[C18-S8] 的 `MagicAutoTrails` 先按 projectile ID 找到 CSV 数据，再替每条匹配记录持续提交节点。运行时尾迹直接调用 `MagicTrailPlugin`：Java 决定何时生成节点、节点放在哪里、同一段轨迹复用哪个 ID，以及何时结束关联。

`MagicTrailPlugin` 把具有相同 ID、sprite 和渲染层的节点连接为一条带状轨迹。单个节点没有相邻点可以连接，至少提交两个节点后才会出现连续尾迹。运行时代码因此需要同时管理采样频率和 ID 生命周期，少管任何一个，画面都会很诚实地暴露问题。

先保存本节完整类到 `src/data/scripts/gearin/visuals/GearInMagicTrail.java`。在 `GearIn_geometry_missile.proj` 中追加下面的局部字段，保留现有 ID、引擎和 AI 选择关系：

```json
"onFireEffect": "data.scripts.gearin.visuals.GearInMagicTrail",
```

## 用稳定 ID 连接节点

`OnFireEffectPlugin` 的对象可能服务多次开火。**每枚弹体保存自己的尾迹状态。**把 trail ID 放在共用效果类字段中，会让后续弹体继续使用前一枚弹体的轨迹；在 `onFire(...)` 中为当前弹体创建独立跟踪器，状态才能与该弹体一起开始和结束：

```java
public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon,
        CombatEngineAPI engine) {
    engine.addPlugin(new TrailTracker(projectile));
    Vector2f detachedVelocity = new Vector2f(projectile.getVelocity());
    detachedVelocity.scale(0.65f);
    spawnDetachedTrail(engine, projectile.getLocation(), detachedVelocity);
}
```

`TrailTracker.init(...)` 为主体和辉光各调用一次 `getUniqueID()`。这两个 `float` 在后续帧保持不变，同一层的新节点才会接在已有节点后方：

```java
bodySprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
glowSprite = Global.getSettings().getSprite("fx", "base_trail_contrail");
bodyTrailId = MagicTrailPlugin.getUniqueID();
glowTrailId = MagicTrailPlugin.getUniqueID();
lastLocation = new Vector2f(projectile.getLocation());
addSegments();
```

ID 只标识当前战斗中的一段尾迹。新的战斗会重新初始化 MagicTrail 插件；需要永久保存的是产生尾迹的规则，而不是这个运行时数值。

## 使用 Simple 提交主体节点

主体层直接使用 `addTrailMemberSimple(...)`：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, bodyTrailId, bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

前三个参数确定关联实体、稳定 ID 和 sprite。`projectile` 让 `cutTrailsOnEntity(projectile)` 能找到这条尾迹；它不会让节点自动追随弹体。节点仍从第四个参数 `location` 取得本次世界坐标，因此每次采样都要重新读取当前位置。

`speed = 0f` 让节点生成后留在原地，`facing` 同时决定节点的运动方向和宽度方向。`startSize = 14f`、`endSize = 3f` 让单个节点随生命周期收窄；颜色的 alpha 分量不会进入尾迹计算，透明度由后面的 `0.8f` 单独控制。

最后四个时间与混合参数依次表示淡入 0.05 秒、完整显示 0.2 秒、淡出 0.8 秒，以及使用加色混合。节点的总寿命是三段时间之和。sprite 的宽和高都使用偶数尺寸，纹理才能稳定落在尾迹四边形上。

## 使用 Advanced 提交辉光节点

辉光层需要首尾颜色、纹理滚动和尺寸脉冲，因此改用参数完整的布尔混合重载：

```java
MagicTrailPlugin.addTrailMemberAdvanced(
        projectile, glowTrailId, glowSprite, location,
        0f, 0f, facing, 0f, 0f, 22f, 6f,
        new Color(90, 170, 255), new Color(30, 70, 180),
        0.55f, 0.05f, 0.15f, 0.6f, true,
        192f, 600f, -1f, new Vector2f(), glowOptions,
        CombatEngineLayers.CONTRAILS_LAYER, 1f);
```

`startSpeed` 与 `endSpeed` 控制节点从生成到消失的移动速度，随后的 `startAngularVelocity` 与 `endAngularVelocity` 控制旋转速度。本例把四项都设为 0，让节点停在采样位置；22→6 的尺寸与亮蓝→深蓝的颜色负责可见变化。

`textureLoopLength = 192f` 表示纹理每 192 su 重复一次，`textureScrollSpeed = 600f` 让纹理持续滚动。`textureOffset = -1f` 会为同一 trail tracker 选择固定的随机起点，连续节点仍共享同一纹理相位。零向量 `offsetVelocity` 不增加额外世界速度。

`CONTRAILS_LAYER` 明确渲染层，`frameOffsetMult = 1f` 保留完整的帧间位置修正。传入 `null` 渲染层时也会选择该层；倍率传入 0 时按 1 处理。自定义 OpenGL 混合重载把 `boolean additive` 替换为源与目标混合常量，其余生命周期相同。

高级选项使用准确的包装类型：

```java
private TrailTracker(DamagingProjectileAPI projectile) {
    this.projectile = projectile;
    glowOptions.put("SIZE_PULSE_WIDTH", 3f);
    glowOptions.put("SIZE_PULSE_COUNT", 2);
    glowOptions.put("FORWARD_PROPAGATION", false);
}
```

`SIZE_PULSE_WIDTH` 是 `Float`，让宽度每次最多增加 3 su；`SIZE_PULSE_COUNT` 是 `Integer`，让节点寿命内完成两次脉冲。`FORWARD_PROPAGATION` 是 `Boolean`，本例保持当前传播方式。键名正确而值类型错误时，对应选项不会进入 tracker。

`addTrailMemberAnimated(...)` 是兼容既有动态换图代码的弃用入口。当前静态 sprite 使用 `addTrailMemberAdvanced(...)`，避免让整条尾迹随最后一次提交反复切换纹理。

::: warning 同一条尾迹保持渲染属性稳定

**同一段轨迹使用稳定的 sprite、混合方式、渲染层和传播方式。**更换 sprite 会进入另一份纹理 tracker，渲染层或混合方式中途改变则会让同一逻辑 ID 产生不一致的绘制结果；需要另一种表现时分配新的 ID。

:::

## 区分关联实体与节点位置

`linkedEntity` 进入 MagicTrail 的切断索引，`position` 进入当前节点。把导弹作为关联实体，只意味着可以按导弹一次切断主体和辉光；MagicTrail 不会从导弹自动推导节点坐标，也不会在导弹移动后修改已经生成的节点。

这种分离使一条尾迹可以关联舰船，却从引擎喷口坐标生成；也可以关联一个专用锚点，再让锚点沿完全由代码计算的路线移动。关联决定如何结束，位置决定节点画在哪里。

## 使用 SimpleEntity 建立独立锚点

`SimpleEntity` 把一个位置、武器或舰船引擎包装成 `CombatEntityAPI`，适合承担只有坐标和切断关系的视觉锚点。

### SimpleEntity 构造方式速查表

| 构造参数 | 位置来源 | 适用效果 |
|---|---|---|
| `Vector2f` | 调用方逐帧更新的坐标 | 独立飞线、计算轨迹和脱离实体的残影 |
| `WeaponAPI` | 当前武器位置 | 炮口或炮塔关联尾迹 |
| `ShipEngineAPI` | 当前引擎位置 | 每个引擎独立保存 ID 的喷焰尾迹 |

完整成果公开 `spawnDetachedTrail(...)`，复制位置和速度后创建独立跟踪器：

```java
public static void spawnDetachedTrail(CombatEngineAPI engine,
        Vector2f location, Vector2f velocity) {
    engine.addPlugin(new DetachedTrailTracker(location, velocity));
}
```

跟踪器用位置构造 `SimpleEntity`，防止调用方后续修改原向量时连带改变锚点：

```java
private DetachedTrailTracker(Vector2f location, Vector2f velocity) {
    anchor = new SimpleEntity(new Vector2f(location));
    this.velocity = new Vector2f(velocity);
}
```

每帧先按初始速度的 0.65 倍移动锚点，再从新位置生成洋红色节点。导弹后续转向不会改变这份速度，所以残影继续沿开火瞬间的方向前进。0.75 秒后，跟踪器按 `anchor` 切断关联并退出；它与导弹尾迹拥有不同 ID 和结束条件。

## 控制采样、切段和收尾

两个跟踪器都在非暂停时间内累积 `segmentTimer`，每 0.05 秒提交一次节点。导弹跟踪器使用归零方式，让节点密度保持简洁；独立锚点减去一个间隔，能够保留超出阈值的少量累计时间：

```java
segmentTimer += amount;
if (segmentTimer >= SEGMENT_INTERVAL) {
    segmentTimer -= SEGMENT_INTERVAL;
    addSegment();
}
```

导弹被直接移动到另一处以后，旧坐标和新坐标已经失去空间连续性。当前坐标与上一帧相差超过 250 su 时，旧 ID 已经代表跳变前的连续段，因此先切断，再为两层取得新 ID：

```java
if (MathUtils.getDistance(lastLocation, projectile.getLocation())
        > TELEPORT_DISTANCE) {
    MagicTrailPlugin.cutTrailsOnEntity(projectile);
    bodyTrailId = MagicTrailPlugin.getUniqueID();
    glowTrailId = MagicTrailPlugin.getUniqueID();
}
```

命中、淡出和离场都结束当前弹体的采样。`cutTrailsOnEntity(...)` 只阻止新节点接回旧 tracker，已经生成的节点仍按自己的淡入、保持和淡出时间消失；随后移除每帧插件，避免没有弹体的跟踪器继续运行。

## 在战斗中触发位置跳变

准备 Console Commands，在场上只有玩家这枚测试导弹、弹体仍在飞行时执行：

```text
runcode com.fs.starfarer.api.Global.getCombatEngine().getMissiles().get(0).getLocation().translate(600f, 0f);
```

这次直接改变导弹的位置，下一帧与此前位置相差 600 su，超过切段阈值。恢复战斗后观察旧段淡出、新段从新坐标开始。

## 常用方法速查表

| 方法 | 保存或输入 | 直接结果 |
|---|---|---|
| `getUniqueID()` | 每条连续尾迹保存一个返回值 | 后续同 ID 节点连接为一段轨迹 |
| `addTrailMemberSimple(...)` | 单一速度、颜色和加色选择 | 在 `CONTRAILS_LAYER` 提交基础节点 |
| `addTrailMemberAdvanced(...)` | 起止状态、纹理、额外速度、选项与渲染层 | 提交完整可调节点 |
| `cutTrailsOnEntity(...)` | 建立节点时使用的关联实体 | 当前关联 tracker 改用内部切断 ID，后续节点形成新段 |
| `spawnDetachedTrail(...)` | 世界坐标与速度副本 | 创建由 `SimpleEntity` 驱动的短寿命残影 |

## 比较错误与正确的 ID 生命周期

下面的局部故障片段在每次提交节点时创建新 ID。每个节点都会进入新的 tracker，画面只剩互不连接的短亮点：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, MagicTrailPlugin.getUniqueID(), bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

正确写法在初始化时保存 ID：

```java
bodyTrailId = MagicTrailPlugin.getUniqueID();
```

提交节点时只读取该字段：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, bodyTrailId, bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

## 完整最小例

测试弹体的 `.proj` 使用以下注册字段：

```json
"onFireEffect": "data.scripts.gearin.visuals.GearInMagicTrail",
```

完整类为每枚弹体建立关联尾迹和独立残影。主体与辉光按弹体切断，洋红色残影按自己的 `SimpleEntity` 锚点结束。

::: details 完整成品：GearInMagicTrail

```java
package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
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
        Vector2f detachedVelocity = new Vector2f(projectile.getVelocity());
        detachedVelocity.scale(0.65f);
        spawnDetachedTrail(engine, projectile.getLocation(), detachedVelocity);
    }

    public static void spawnDetachedTrail(CombatEngineAPI engine,
            Vector2f location, Vector2f velocity) {
        engine.addPlugin(new DetachedTrailTracker(location, velocity));
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
        private final Map<String, Object> glowOptions = new HashMap<>();

        private TrailTracker(DamagingProjectileAPI projectile) {
            this.projectile = projectile;
            glowOptions.put("SIZE_PULSE_WIDTH", 3f);
            glowOptions.put("SIZE_PULSE_COUNT", 2);
            glowOptions.put("FORWARD_PROPAGATION", false);
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
            if (engine.isPaused()) {
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
            MagicTrailPlugin.addTrailMemberAdvanced(
                    projectile, glowTrailId, glowSprite, location,
                    0f, 0f, facing, 0f, 0f, 22f, 6f,
                    new Color(90, 170, 255), new Color(30, 70, 180),
                    0.55f, 0.05f, 0.15f, 0.6f, true,
                    192f, 600f, -1f, new Vector2f(), glowOptions,
                    CombatEngineLayers.CONTRAILS_LAYER, 1f);
        }

        private void cutAndRemove() {
            MagicTrailPlugin.cutTrailsOnEntity(projectile);
            engine.removePlugin(this);
        }
    }

    private static final class DetachedTrailTracker
            extends BaseEveryFrameCombatPlugin {
        private static final float SEGMENT_INTERVAL = 0.05f;
        private static final float LIFETIME = 0.75f;

        private final SimpleEntity anchor;
        private final Vector2f velocity;
        private CombatEngineAPI engine;
        private SpriteAPI sprite;
        private float trailId;
        private float segmentTimer;
        private float elapsed;

        private DetachedTrailTracker(Vector2f location, Vector2f velocity) {
            anchor = new SimpleEntity(new Vector2f(location));
            this.velocity = new Vector2f(velocity);
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
            sprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
            trailId = MagicTrailPlugin.getUniqueID();
            addSegment();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine.isPaused()) {
                return;
            }
            elapsed += amount;
            if (elapsed >= LIFETIME) {
                MagicTrailPlugin.cutTrailsOnEntity(anchor);
                engine.removePlugin(this);
                return;
            }
            Vector2f movement = new Vector2f(velocity);
            movement.scale(amount);
            Vector2f.add(anchor.getLocation(), movement, anchor.getLocation());

            segmentTimer += amount;
            if (segmentTimer >= SEGMENT_INTERVAL) {
                segmentTimer -= SEGMENT_INTERVAL;
                addSegment();
            }
        }

        private void addSegment() {
            MagicTrailPlugin.addTrailMemberSimple(
                    anchor, trailId, sprite, anchor.getLocation(),
                    0f, VectorUtils.getFacing(velocity), 10f, 2f,
                    new Color(255, 90, 220), 0.65f,
                    0.02f, 0.08f, 0.35f, true);
        }
    }
}
```

:::

## 验证方案

前置数据：Console Commands 已启用，测试敌舰使用不发射导弹的装配，便于按正文命令选择玩家弹体。

1. 构建 MOD，启动游戏并进入能够连续发射测试导弹的战斗。
2. 连续发射两枚导弹，观察每枚弹体后方的青色主体、蓝色辉光和洋红色残影。
3. 让第一枚导弹转向，比较弹体关联尾迹与保持初始方向的独立残影。
4. 观察蓝色辉光的首尾颜色、纹理滚动和两次尺寸脉冲。
5. 暂停战斗 2 秒，确认节点提交和独立锚点移动同时停止，再恢复战斗。
6. 场上只保留一枚飞行中的测试导弹，执行正文位置跳变命令，观察旧段留在原处淡出，新段从新位置开始。
7. 让导弹命中目标或进入淡出，观察关联尾迹停止增加，已有节点继续完成自身生命周期。
8. 等待发射后的 0.75 秒，确认洋红色残影停止增加；再次连续发射，比较各枚弹体的尾迹关联。

预期结果：

- 每枚弹体拥有互不串联的主体、辉光与发射残影；
- 主体层按单一颜色收窄，辉光层呈现颜色渐变、纹理滚动和尺寸脉冲；
- 导弹转向后，`SimpleEntity` 残影仍按发射瞬间速度的 0.65 倍直线移动；
- 非暂停状态下，跟踪器每 0.05 秒最多追加一组节点；
- 位置跳变超过 250 su 时，两条弹体尾迹同时切段，新节点不会横跨 600 su；
- 命中、淡出或离场后，导弹跟踪器停止提交节点；
- 独立残影在 0.75 秒后停止提交节点；
- `starsector.log` 没有未处理异常。

## 故障分析

- **尾迹只有互不连接的短亮点**：节点提交处每次调用 `getUniqueID()`，相邻节点进入了不同 tracker。
    - **解决方案**：在跟踪器初始化时保存 ID，提交节点时反复使用该字段，只在明确切段后更新。

## 总结

尾迹跟踪器随弹体开始和结束，固定 ID 把相邻节点接成一段轨迹。节点坐标每次由代码提交，关联实体则用于切断；独立锚点沿自己的速度前进，两种关系因此可以分别控制。Simple 提供基础节点，Advanced 增加首尾状态、纹理与渲染属性。

导弹跟踪器让尾迹服从实体生命周期，`SimpleEntity` 则让纯计算位置也能拥有相同的切断关系。只要为每段轨迹保存自己的 ID，并在空间或生命周期不连续时主动切段，运行时生成的效果就能保持连续而不跨越不该连接的位置。

## 建议参考项

- `org.magiclib.plugins.MagicTrailPlugin`：创建 ID、提交 Simple 或 Advanced 节点并切断实体关联；
- `org.lazywizard.lazylib.combat.entities.SimpleEntity`：把位置、武器或舰船引擎包装为轻量战斗实体；
- `com.fs.starfarer.api.combat.OnFireEffectPlugin`：在 projectile 生成后取得弹体和当前战斗引擎；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：添加和移除每枚弹体或独立锚点的跟踪插件。
