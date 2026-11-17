# 创建 MagicLib 运行时尾迹

弹体半路折向切段，或者本体早已炸毁却还想留下一截脱手消散的残影，靠弹体表静态绑定就难以实现了。

这时候得直接向 `MagicTrailPlugin` 申请稳定 ID 并逐帧补点 —— 什么时候断段、脱离载体后由谁保底清理，都需要开火脚本自己写清楚。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C8-S1] 的每帧战斗插件与暂停判断；
- [C8-S3] 的战斗实体有效性与结束边界；
- [C18-S2] 的 `GearIn_geometry_missile` 弹体；
- [C18-S8] 的 trail sprite、节点状态与自动尾迹字段；
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

### 比较节点 ID 的使用方式

下面的局部故障片段在每次提交时生成新 ID。各节点分别进入新 tracker，无法与相邻节点连接：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, MagicTrailPlugin.getUniqueID(), bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

修正片段在初始化时保存 ID：

```java
bodyTrailId = MagicTrailPlugin.getUniqueID();
```

后续提交反复使用这个字段，同一连续段的节点才能相互连接：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, bodyTrailId, bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

## 使用 Simple 提交主体节点

主体层直接使用 `addTrailMemberSimple(...)`：

```java
MagicTrailPlugin.addTrailMemberSimple(
        projectile, bodyTrailId, bodySprite, location,
        0f, facing, 14f, 3f, new Color(120, 220, 255),
        0.8f, 0.05f, 0.2f, 0.8f, true);
```

前几个位置依次指定关联实体、稳定 ID、sprite 与世界采样点。`projectile` 让 `cutTrailsOnEntity(projectile)` 能找到这条尾迹；它不会让节点自动追随弹体，每次采样仍需主动传入当前的 `location` 世界坐标。

`speed = 0f` 让节点生成后留在原地，`facing` 同时决定节点的运动方向和宽度方向。`startSize = 14f`、`endSize = 3f` 让单个节点随生命周期收窄；颜色的 alpha 分量不会进入尾迹计算，透明度由后面的 `0.8f` 单独控制。

末尾的淡入 `0.05` 秒、完整显示 `0.2` 秒、淡出 `0.8` 秒与加色混合参数共同决定单节存续时间。节点的总存活时长是这三段时间之和；`sprite` 的宽和高都使用偶数尺寸，纹理才能对齐尾迹四边形不发生偏移。

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

`textureLoopLength = 192f` 表示纹理每 192 su 重复一次；`textureScrollSpeed = 600f` 表示每秒滚动 0.6 个完整纹理周期。`textureOffset = -1f` 为同一个 tracker 选择固定随机起点，后续节点共享这份相位，零向量 `offsetVelocity` 则不增加额外世界速度。

`CONTRAILS_LAYER` 明确渲染层，传入 `null` 也会采用这一层。`frameOffsetMult = 1f` 保留弹体节点的位置修正：25 参 `addTrailMemberAdvanced` 重载会取 `proj.getSource().getVelocity()` 乘以帧长和该倍率来修正位置 (因此若 `linkedEntity` 是没有 `getSource()` 来源舰的弹体会直接空指针；而无 `frameOffsetMult` 的 24 参重载与 `Simple` 均不含此逻辑，并非省略时等同于传 1f)。传 `0` 给该参数仍按 `1f` 处理；自定义 OpenGL 混合重载则用源与目标混合常量替换 `boolean additive`，节点存活时长的含义不变。

高级选项使用准确的包装类型：

```java
private TrailTracker(DamagingProjectileAPI projectile) {
    this.projectile = projectile;
    glowOptions.put("SIZE_PULSE_WIDTH", 3f);
    glowOptions.put("SIZE_PULSE_COUNT", 2);
    glowOptions.put("FORWARD_PROPAGATION", false);
}
```

`SIZE_PULSE_WIDTH` 是 `Float`，让宽度每次最多增加 3 su；`SIZE_PULSE_COUNT` 是 `Integer`，让节点存活时间内完成两次脉冲。`FORWARD_PROPAGATION` 是 `Boolean`，本例保持当前传播方式。键名正确而值类型错误时，对应选项不会进入 tracker。

`addTrailMemberAnimated(...)` 是兼容既有动态换图代码的弃用入口。当前静态 sprite 使用 `addTrailMemberAdvanced(...)`，避免让整条尾迹随最后一次提交反复切换纹理。

::: warning 纹理与渲染层也决定连续段

同一 ID 更换 sprite 或渲染层，会进入另一份 tracker，原节点继续留在原段。需要独立表现时分配新 ID，并保持该段的混合与传播方式稳定。

:::

## 区分关联实体与节点位置

`linkedEntity` 进入 MagicTrail 的切断索引，`position` 进入当前节点。把导弹作为关联实体，只意味着可以按导弹一次切断主体和辉光；MagicTrail 不会从导弹自动推导节点坐标，也不会在导弹移动后修改已经生成的节点。

这种分离使一条尾迹可以关联舰船，却从引擎喷口坐标生成；也可以关联一个专用锚点，再让锚点沿完全由代码计算的路线移动。关联决定如何结束，位置决定节点画在哪里。

## 使用 SimpleEntity 建立独立锚点

`SimpleEntity` 把一个位置、武器或舰船引擎包装成 `CombatEntityAPI`，适合承担只有坐标和切断关系的视觉锚点。

### SimpleEntity 构造方式速查表

| 构造参数        | 位置来源             | 适用效果                           |
| --------------- | -------------------- | ---------------------------------- |
| `Vector2f`      | 调用方逐帧更新的坐标 | 独立飞线、计算轨迹和脱离实体的残影 |
| `WeaponAPI`     | 当前武器位置         | 炮口或炮塔关联尾迹                 |
| `ShipEngineAPI` | 当前引擎位置         | 每个引擎独立保存 ID 的喷焰尾迹     |

完整成果公开 `spawnDetachedTrail(...)`，复制位置和速度后创建独立跟踪器：

```java
public static void spawnDetachedTrail(CombatEngineAPI engine,
        Vector2f location, Vector2f velocity) {
    engine.addPlugin(new DetachedTrailTracker(location, velocity));
}
```

`SimpleEntity` 的坐标构造器保存位置副本，跟踪器也单独保存速度副本。局部构造如下：

```java
private DetachedTrailTracker(Vector2f location, Vector2f velocity) {
    anchor = new SimpleEntity(new Vector2f(location));
    this.velocity = new Vector2f(velocity);
}
```

每帧先按初始速度的 0.65 倍移动锚点，再从新位置生成洋红色节点。导弹后续转向不会改变这份速度，所以残影继续沿开火瞬间的方向前进。0.75 秒后，跟踪器调用 `cutTrailsOnEntity(anchor)` 并从战斗引擎移除自身。注意独立构造的 `SimpleEntity` 并未加入 `engine` (`engine.isEntityInPlay(anchor)` 始终为 `false`)，而 `MagicTrailPlugin` 每 1 秒会自动清理不在场实体的切断映射，因此真正保证残影按时收尾的是停提交节点并卸载插件本身。

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

| 方法                          | 保存或输入                             | 直接结果                                           |
| ----------------------------- | -------------------------------------- | -------------------------------------------------- |
| `getUniqueID()`               | 每条连续尾迹保存一个返回值             | 后续同 ID 节点连接为一段轨迹                       |
| `addTrailMemberSimple(...)`   | 单一速度、颜色和加色选择               | 在 `CONTRAILS_LAYER` 提交基础节点                  |
| `addTrailMemberAdvanced(...)` | 起止状态、纹理、额外速度、选项与渲染层 | 提交完整可调节点                                   |
| `cutTrailsOnEntity(...)`      | 建立节点时使用的关联实体               | 当前关联 tracker 改用内部切断 ID，后续节点形成新段 |
| `spawnDetachedTrail(...)`     | 世界坐标与速度副本                     | 创建由 `SimpleEntity` 驱动的短时残影               |

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

### 进游戏核对

构建包含 `GearInMagicTrail` 与 `.proj` 挂载的 MOD，进入有不发射导弹敌舰的模拟战：

- 连续发射测试导弹，观察青色主体、蓝色渐变滚动脉冲辉光，以及转向后依然保持发射初速 0.65 倍直飞的洋红色残影；
- 暂停战斗确认采样与锚点移动全部冻结，恢复后继续推进；并在仅剩一枚飞行中导弹时执行上文 `translate(600f, 0f)` 命令，验证超过 250 su 跳变后旧段留在原处淡出、新段从新坐标起笔而不会拉出一根 600 su 飞线；
- 导弹命中或消失后 0.75 秒内独立残影也停止延长，已有节点各自淡出，`starsector.log` 无异常。

## 常见问题

- **节点持续提交却没有连续尾迹**：每次调用 `getUniqueID()`，各 tracker 只有一个节点，达不到连接所需的两个节点。
    - **解决方案**：在跟踪器初始化时保存 ID，提交节点时反复使用该字段，只在明确切段后更新。

## 建议参考项

- `org.magiclib.plugins.MagicTrailPlugin`：创建 ID、提交 Simple 或 Advanced 节点并切断实体关联；
- `org.lazywizard.lazylib.combat.entities.SimpleEntity`：把位置、武器或舰船引擎包装为轻量战斗实体；
- `com.fs.starfarer.api.combat.OnFireEffectPlugin`：在 projectile 生成后取得弹体和当前战斗引擎；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：添加和移除每枚弹体或独立锚点的跟踪插件。
