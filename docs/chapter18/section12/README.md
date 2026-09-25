# 使用 MagicLib 战斗小工具

MagicLib 除了拖尾与生涯工具，还提供圆形边界求交、HUD 状态条和动画曲线。本节把三项小工具接到同一个战斗目标探针：锁定目标后，圆周粒子标出近侧边界，HUD 同时比较线性接近度与平滑接近度。

预计阅读时间：20 分钟。

预计操作时间：25 分钟。

## 学习目标

- 使用 `MagicFakeBeam.getCollisionPointOnCircumference(...)` 计算线段与圆周的近侧交点；
- 将战斗距离归一化为 `0–1` 接近度；
- 使用 `MagicAnim.smooth(...)` 把线性进度映射为缓入缓出曲线；
- 使用 `MagicUI.drawHUDStatusBar(...)` 比较平滑主条与线性副条；
- 为每帧战斗插件分别安排 HUD 刷新与粒子节流。

## 前置知识

- [C7-S1] 的战斗插件生命周期；
- [C18-S1] 的 MagicLib 硬依赖和编译 classpath；
- [C18-S2] 的位置、距离和 `Vector2f`；
- [C18-S3] 的 `BaseEveryFrameCombatPlugin` 注册方式。

## 组织目标探针的数据流

探针只读取玩家当前锁定的舰船目标。一个有效帧从同一组玩家与目标数据产生三项结果：中心距离形成线性接近度，平滑曲线形成主条进度，玩家至目标中心的线段形成圆周交点。

```text
玩家舰船 + 当前舰船目标
    -> 中心距离 -> 线性接近度 -> MagicAnim.smooth -> HUD 主条
    -> 玩家位置到目标位置 -> MagicFakeBeam -> 圆周交点粒子
```

`advance(...)` 在战斗暂停、玩家不存在、玩家已被摧毁、目标不存在或目标已被摧毁时结束本帧。失去目标时还会清零粒子计时器，重新锁定后从一个完整的 `0.05 秒` 周期开始。

## 计算目标圆周交点

`getCollisionPointOnCircumference(...)` 接收线段起点、线段终点、圆心和半径。本例把玩家位置作为起点，把目标位置同时作为线段终点和圆心，再使用目标的碰撞半径：

```java
Vector2f collisionPoint = MagicFakeBeam.getCollisionPointOnCircumference(
        player.getLocation(),
        target.getLocation(),
        target.getLocation(),
        target.getCollisionRadius());
```

正常交战距离下，返回值是玩家一侧的目标圆周交点。线段没有到达圆周时返回 `null`；调用方只有在结果存在时才生成粒子。线段起点已经位于圆内时，方法直接返回起点，因此贴近或穿入碰撞圆的玩家会在自身位置看到标记。

这个方法按圆心和半径处理几何边界。LazyLib `CollisionUtils` 面向舰船边界、线段和实际碰撞轮廓等查询；选择依据是当前问题需要抽象圆周，还是需要实体的具体碰撞形状。

## 把距离映射为接近度

玩家与目标的中心距离使用 `MathUtils.getDistance(...)` 取得。本例把 `2000 su` 设为显示量程，距离越近，接近度越高：

```java
float distance = MathUtils.getDistance(player, target);
float linearProximity = 1f - Math.min(distance / 2000f, 1f);
```

映射结果具有明确边界：距离为 `0 su` 时等于 `1`，距离为 `1000 su` 时等于 `0.5`，距离达到或超过 `2000 su` 时等于 `0`。战斗距离不会是负数，因此 `Math.min(...)` 已经完成本例需要的上限钳制。

接近度用于表现玩家与目标的空间关系，不参与伤害、命中或 AI 判断。改变 `DISPLAY_RANGE` 只会改变 HUD 的显示比例。

## 使用 smooth 映射动画曲线

`MagicAnim.smooth(float)` 接收 `0–1` 进度，并返回同一范围内的缓入缓出结果：

```java
float smoothProximity = MagicAnim.smooth(linearProximity);
```

输入 `0`、`0.5` 和 `1` 时，输出分别保持 `0`、`0.5` 和 `1`；输入 `0.25` 时约为 `0.15`，输入 `0.75` 时约为 `0.85`。曲线在两端变化较慢，在中段变化较快。

这个方法只映射当前输入值，不保存上一帧数据，也不根据 `amount` 追赶目标值。跨帧计时和状态仍由战斗插件字段负责。

## 绘制双层 HUD 状态条

本例把平滑接近度放入主填充 `fill`，把线性接近度放入较宽的副填充 `secondfill`。两层使用相同绿色，移动过程中可以直接比较曲线差异：

```java
MagicUI.drawHUDStatusBar(
        player,
        smoothProximity,
        MagicUI.GREENCOLOR,
        MagicUI.GREENCOLOR,
        linearProximity,
        Math.round(distance) + " su",
        "目标接近度",
        false);
```

### HUD 参数速查表

| 参数 | 本例值 | 作用 |
| --- | --- | --- |
| `ship` | `player` | 指定状态条所属舰船 |
| `fill` | `smoothProximity` | 绘制平滑主填充 |
| `innerColor` | `MagicUI.GREENCOLOR` | 指定主条颜色 |
| `borderColor` | `MagicUI.GREENCOLOR` | 指定边框颜色 |
| `secondfill` | `linearProximity` | 绘制线性副填充 |
| `bottext` | 整数距离加 `su` | 显示当前中心距离 |
| `toptext` | `目标接近度` | 标识状态条含义 |
| `offset` | `false` | 使用默认 HUD 位置 |

`drawHUDStatusBar(...)` 内部只为当前玩家舰船绘制，而且要求战斗引擎存在 viewport、HUD 当前可见。传入其他舰船、隐藏 HUD 或离开正常战斗画面时，方法会自行结束。

## 节流圆周标记粒子

HUD 每个有效帧都要提交，才能跟随界面刷新；粒子只需定期补充。本例累计 `amount`，每 `0.05 秒` 生成一个持续 `0.08 秒` 的平滑粒子：

```java
particleElapsed += amount;
if (particleElapsed < 0.05f) {
    return;
}
particleElapsed %= 0.05f;
```

粒子寿命比间隔长 `0.03 秒`，相邻标记会轻微重叠。零速度让粒子停留在本次计算的交点；目标移动后，新粒子会出现在新的圆周位置，旧粒子在 `0.08 秒` 内自然消失。

切换目标时，插件把计时器清零。新目标必须等待一个完整间隔才产生第一个粒子，旧目标的累计时间不会带入新目标。

## 注册战斗目标探针

在 `data/config/settings.json` 的 `plugins` 对象中登记插件。注册 key 在合并后的设置中必须唯一：

```json
{
    "plugins": {
        "gearInTargetProbe": "data.scripts.gearin.combat.GearInTargetProbe"
    }
}
```

游戏为每场战斗创建一个插件实例。实例字段只保存当前战斗的引擎、上一个目标与粒子计时器，不跨战斗或存档持久化。

## 常用方法速查表

| API | 返回值或结果 | 本节用途 |
| --- | --- | --- |
| `ShipAPI.getShipTarget()` | `ShipAPI` 或 `null` | 取得玩家当前锁定目标 |
| `MathUtils.getDistance(...)` | `float` | 取得玩家与目标的中心距离 |
| `MagicFakeBeam.getCollisionPointOnCircumference(...)` | `Vector2f` 或 `null` | 取得线段遇到的近侧圆周交点 |
| `MagicAnim.smooth(float)` | `0–1` 的 `float` | 把线性接近度映射为缓入缓出进度 |
| `MagicUI.drawHUDStatusBar(...)` | 无返回值 | 绘制主副填充、距离和标题 |
| `CombatEngineAPI.addSmoothParticle(...)` | 无返回值 | 在交点生成短寿命标记 |

## 完整最小例

`GearInTargetProbe` 只读取当前玩家、舰船目标与距离，并生成 HUD 和诊断粒子。将它放在 `data/scripts/gearin/combat/GearInTargetProbe.java`，再按上一节的 settings 片段注册。

::: details 完整成品：GearInTargetProbe

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicUI;

public final class GearInTargetProbe extends BaseEveryFrameCombatPlugin {

    private static final float DISPLAY_RANGE = 2000f;
    private static final float PARTICLE_INTERVAL = 0.05f;
    private static final float PARTICLE_DURATION = 0.08f;
    private static final float PARTICLE_SIZE = 18f;
    private static final Color PARTICLE_COLOR = new Color(90, 255, 140, 220);
    private static final Vector2f ZERO = new Vector2f();

    private CombatEngineAPI engine;
    private ShipAPI previousTarget;
    private float particleElapsed;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            resetTarget();
            return;
        }

        ShipAPI target = player.getShipTarget();
        if (target == null || !target.isAlive() || !engine.isEntityInPlay(target)) {
            resetTarget();
            return;
        }

        if (target != previousTarget) {
            previousTarget = target;
            particleElapsed = 0f;
        }

        float distance = MathUtils.getDistance(player, target);
        float linearProximity = 1f - Math.min(distance / DISPLAY_RANGE, 1f);
        float smoothProximity = MagicAnim.smooth(linearProximity);

        MagicUI.drawHUDStatusBar(
                player,
                smoothProximity,
                MagicUI.GREENCOLOR,
                MagicUI.GREENCOLOR,
                linearProximity,
                Math.round(distance) + " su",
                "目标接近度",
                false);

        particleElapsed += amount;
        if (particleElapsed < PARTICLE_INTERVAL) {
            return;
        }
        particleElapsed %= PARTICLE_INTERVAL;

        Vector2f collisionPoint = MagicFakeBeam.getCollisionPointOnCircumference(
                player.getLocation(),
                target.getLocation(),
                target.getLocation(),
                target.getCollisionRadius());
        if (collisionPoint != null) {
            engine.addSmoothParticle(
                    collisionPoint, ZERO, PARTICLE_SIZE, 1f,
                    PARTICLE_DURATION, PARTICLE_COLOR);
        }
    }

    private void resetTarget() {
        previousTarget = null;
        particleElapsed = 0f;
    }
}
```

:::

## 验证方案

前置数据：MagicLib 与 LazyLib 已安装并启用，`GearInTargetProbe` 已加入 settings 的 `plugins` 对象，测试战斗包含至少两艘可以锁定的敌舰。

1. 启动一场战斗，保持未锁定目标并观察玩家 HUD 与战场粒子；
2. 锁定一艘存活敌舰，观察目标近侧碰撞圆和玩家舰船左上方 HUD；
3. 在 `2000 su` 内接近目标，比较绿色主填充与较宽副填充；
4. 拉开到 `2000 su` 以外，继续观察距离文本和两层填充；
5. 切换到第二艘敌舰，观察圆周标记的位置和首次粒子时机；
6. 取消锁定或摧毁当前目标，观察 HUD 状态条与新粒子生成。

预期结果：

- 无有效目标时不显示目标接近度状态条，也不生成交点粒子；
- 锁定有效目标后，绿色粒子以每秒 `20` 个的频率标记玩家一侧圆周；
- 接近目标时，主填充与副填充都增加，距离文本以整数 `su` 更新；
- 平滑主条在量程两端变化较慢，线性副条按距离等比例变化；
- 距离达到或超过 `2000 su` 时，两层填充均为 `0`；
- 切换目标后，交点跟随新目标，等待 `0.05 秒` 后出现第一个新粒子。

## 故障分析

- **锁定目标后 HUD 和粒子都没有出现**：插件没有从 settings 注册，或玩家取得的不是舰船目标。
    - **解决方案**：核对 `plugins.gearInTargetProbe` 的包名与类名，并确认目标来自 `player.getShipTarget()`。

- **HUD 出现但圆周没有粒子**：交点结果为 `null`，或粒子节流尚未达到 `0.05 秒`。
    - **解决方案**：核对四个交点参数的顺序，并确认线段终点使用目标中心。

- **状态条随距离增加而填满**：距离比例没有反转为接近度。
    - **解决方案**：使用 `1f - Math.min(distance / DISPLAY_RANGE, 1f)`。

- **主条与副条完全重合**：两层传入了同一个进度，或目标恰好处于端点和中点。
    - **解决方案**：主条传入 `smoothProximity`，副条传入 `linearProximity`，并在约 `500 su` 或 `1500 su` 处比较。

- **状态条显示在其他舰船旁边**：调用方传入了玩家以外的舰船。
    - **解决方案**：始终把 `engine.getPlayerShip()` 传给 `drawHUDStatusBar(...)`。

- **切换目标后立即出现旧位置粒子**：目标变化时没有清零粒子计时器。
    - **解决方案**：比较 `target` 与 `previousTarget`，变化后把 `particleElapsed` 设为 `0f`。

## 总结

`MagicFakeBeam` 用圆心和半径把目标近侧边界转化为明确坐标，`MagicAnim` 把线性接近度变成缓入缓出曲线，`MagicUI` 则把两个进度放到同一条 HUD 状态条中。三项 API 共享玩家与当前目标，形成一个职责单一、结果可观察的战斗探针。

HUD 需要逐帧刷新，粒子需要按时间节流。把界面提交、几何计算和跨帧计时分开后，刷新频率、粒子寿命和目标切换行为都能独立核对。

## 建议参考项

- `org.magiclib.util.MagicFakeBeam`：假光束和圆形边界求交；
- `org.magiclib.util.MagicAnim`：归一化进度的动画曲线；
- `org.magiclib.util.MagicUI`：战斗 HUD 状态条、文字与颜色常量；
- `org.lazywizard.lazylib.MathUtils`：战斗实体中心距离；
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：每帧战斗入口。
