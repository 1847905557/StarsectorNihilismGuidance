# 使用 MagicLib 战斗小工具

本节为锁定目标绘制近侧圆周标记，并在玩家舰船旁显示接近度。距离转换为线性比例后，再由 MagicAnim 映射为平滑曲线，两种结果交给同一个 HUD 状态条。几何位置与显示进度都来自当前玩家和目标，刷新频率则分别安排。

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

- 使用 `MagicFakeBeam.getCollisionPointOnCircumference(...)` 计算线段与圆周的近侧交点；
- 将战斗距离归一化为 `0–1` 接近度；
- 使用 `MagicAnim.smooth(...)` 把线性进度映射为缓入缓出曲线；
- 使用 `MagicUI.drawHUDStatusBar(...)` 比较平滑主条与线性副条；
- 为每帧战斗插件分别安排 HUD 刷新与粒子节流。

## 前置知识

- [C8-S3] 的战斗插件生命周期与暂停处理；
- [C18-S1] 的 MagicLib 硬依赖和编译 classpath；
- [C18-S2] 的位置、距离和 `Vector2f`；
- [C18-S3] 的 `BaseEveryFrameCombatPlugin` 注册方式。

## 注册战斗目标探针

先保存本节完整的 `GearInTargetProbe`，再向既有 `data/config/settings.json` 的 `plugins` 对象追加唯一登记键：

```json
{
    "plugins": {
        "gearInTargetProbe": "data.scripts.gearin.combat.GearInTargetProbe"
    }
}
```

游戏为每场战斗创建一个插件实例。实例字段只保存当前战斗的引擎、上一个目标与粒子计时器，不跨战斗或存档持久化。

## 让目标与 HUD 使用同一次查询

登记完成后，探针取得玩家与当前锁定的舰船。中心距离用于计算接近度，玩家至目标中心的线段用于取得圆周交点；同一帧使用这组对象，显示就能对应当前目标。**HUD 每帧提交，粒子按间隔补充。**

```text
玩家舰船 + 当前舰船目标
    -> 中心距离 -> 线性接近度 -> MagicAnim.smooth -> HUD 主条
    -> 玩家位置到目标位置 -> MagicFakeBeam -> 圆周交点粒子
```

`advance(...)` 在战斗暂停、玩家不存在、玩家已被摧毁、目标不存在或目标已被摧毁时结束本帧。失去目标时还会清零粒子计时器，重新锁定后从零累计粒子计时，当前帧的 `amount` 也参与累积。

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
float distance = MathUtils.getDistance(player.getLocation(), target.getLocation());
float linearProximity = 1f - Math.min(distance / 2000f, 1f);
```

映射结果具有明确边界：距离为 `0 su` 时等于 `1`，距离为 `1000 su` 时等于 `0.5`，距离达到或超过 `2000 su` 时等于 `0`。两个位置的距离为非负数，`Math.min(...)` 将量程之外的比例截到 1。这里传入位置向量，HUD 显示的是中心距离；换成实体重载后，碰撞半径也会进入结果。

接近度用于表现玩家与目标的空间关系，不参与伤害、命中或 AI 判断。改变 `DISPLAY_RANGE` 只会改变 HUD 的显示比例。

## 使用 smooth 映射动画曲线

`MagicAnim.smooth(float)` 接收 `0–1` 进度，并返回同一范围内的缓入缓出结果：

```java
float smoothProximity = MagicAnim.smooth(linearProximity);
```

输入 `0`、`0.5` 和 `1` 时，输出分别保持 `0`、`0.5` 和 `1`；输入 `0.25` 时约为 `0.15`，输入 `0.75` 时约为 `0.85`。曲线在两端变化较慢，在中段变化较快。

`smooth` 计算的是当前输入在曲线上的位置。目标瞬间跳到另一处，进度也会随当前距离立即变化；若需要状态在几帧内追赶目标值，还要由插件保存进度并按时间推进。这里采用即时映射，以便把显示比例与距离直接对应。

## 绘制双层 HUD 状态条

本例把平滑接近度交给主填充 `fill`，把线性接近度交给较宽的副填充 `secondfill`。两层使用相同绿色，距离文本提供当前位置的数值：

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

**副填充的显示长度会被限制在主填充以内。** 接近度大于 0.5 时，平滑值高于线性值，两层可以显示不同长度；接近度小于 0.5 时，线性副条被截到主条长度。因此，约 500 su 处适合比较两层，约 1500 su 处则能观察这项限制。

### HUD 参数速查表

| 参数          | 本例值               | 作用               |
| ------------- | -------------------- | ------------------ |
| `ship`        | `player`             | 指定状态条所属舰船 |
| `fill`        | `smoothProximity`    | 绘制平滑主填充     |
| `innerColor`  | `MagicUI.GREENCOLOR` | 指定主条颜色       |
| `borderColor` | `MagicUI.GREENCOLOR` | 指定边框颜色       |
| `secondfill`  | `linearProximity`    | 绘制线性副填充     |
| `bottext`     | 整数距离加 `su`      | 显示当前中心距离   |
| `toptext`     | `目标接近度`         | 标识状态条含义     |
| `offset`      | `false`              | 使用默认 HUD 位置  |

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

切换目标时，插件把计时器清零。新目标从零累计到阈值再生成粒子，旧目标的计时余量不带入。此前粒子继续按 0.08 秒寿命消失。

## 常用方法速查表

| API                                                   | 返回值或结果         | 本节用途                       |
| ----------------------------------------------------- | -------------------- | ------------------------------ |
| `ShipAPI.getShipTarget()`                             | `ShipAPI` 或 `null`  | 取得玩家当前锁定目标           |
| `MathUtils.getDistance(...)`                          | `float`              | 取得玩家与目标的中心距离       |
| `MagicFakeBeam.getCollisionPointOnCircumference(...)` | `Vector2f` 或 `null` | 取得线段遇到的近侧圆周交点     |
| `MagicAnim.smooth(float)`                             | `0–1` 的 `float`     | 把线性接近度映射为缓入缓出进度 |
| `MagicUI.drawHUDStatusBar(...)`                       | 无返回值             | 绘制主副填充、距离和标题       |
| `CombatEngineAPI.addSmoothParticle(...)`              | 无返回值             | 在交点生成短寿命标记           |

## 完整最小例

`GearInTargetProbe` 读取当前玩家、舰船目标与中心距离，再生成 HUD 和标记粒子。源码保存到 `src/data/scripts/gearin/combat/GearInTargetProbe.java`：

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
        if (engine.isPaused()) {
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

        float distance = MathUtils.getDistance(player.getLocation(), target.getLocation());
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

1. 保存源码与插件登记，构建并重启。进入战斗，保持未锁定目标，观察玩家 HUD 与战场粒子。
2. 锁定一艘存活敌舰，观察目标近侧碰撞圆和玩家舰船左上方 HUD。
3. 在 `2000 su` 内接近目标，比较绿色主填充与较宽副填充。
4. 拉开到 `2000 su` 以外，继续观察距离文本和两层填充。
5. 切换到第二艘敌舰，观察圆周标记的位置和首次粒子时机。
6. 取消锁定或摧毁当前目标，观察 HUD 状态条与新粒子生成。

预期结果：

- 无有效目标时不显示目标接近度状态条，也不生成交点粒子；
- 锁定有效目标后，绿色粒子按 0.05 秒间隔、每帧最多一个标记玩家一侧圆周；常规帧率下约每秒 20 个；
- 接近目标时，主填充与副填充都增加，距离文本以整数 `su` 更新；
- 平滑主条在量程两端变化较慢，线性副条按距离等比例变化；
- 距离达到或超过 `2000 su` 时，两层填充均为 `0`；
- 切换目标后，交点跟随新目标，计时从零累计达到 0.05 秒后生成新粒子，当前帧时间计入其中。

## 故障分析

- **锁定目标后 HUD 和粒子都没有出现**：插件没有从 settings 注册，或玩家取得的不是舰船目标。
    - **解决方案**：核对 `plugins.gearInTargetProbe` 的包名与类名，并确认目标来自 `player.getShipTarget()`。

- **HUD 出现但圆周没有粒子**：交点结果为 `null`，或粒子节流尚未达到 `0.05 秒`。
    - **解决方案**：核对四个交点参数的顺序，并确认线段终点使用目标中心。

- **状态条随距离增加而填满**：距离比例没有反转为接近度。
    - **解决方案**：使用 `1f - Math.min(distance / DISPLAY_RANGE, 1f)`。

- **两层长度相同，难以比较曲线**：接近度处于端点、中点，或线性副条高于主条后被截到相同长度。
    - **解决方案**：保留主条的 `smoothProximity` 与副条的 `linearProximity`，在约 `500 su` 处比较。

- **传入其他舰船后状态条不显示**：这个入口只为当前玩家舰船绘制。
    - **解决方案**：始终把 `engine.getPlayerShip()` 传给 `drawHUDStatusBar(...)`。

- **切换目标后粒子沿用上次采样的计时余量**：目标变化时没有清零计时器。
    - **解决方案**：比较 `target` 与 `previousTarget`，变化后把 `particleElapsed` 设为 `0f`。

## 总结

探针从当前目标取得圆周交点与中心距离，曲线映射改变显示进度，HUD 再按主副条的实际限制绘制。曲线只处理当前值，粒子计时则保存在插件字段中；目标切换时清除计时余量，既有粒子继续完成自己的寿命，两种刷新职责由此分开。

## 建议参考项

- `org.magiclib.util.MagicFakeBeam`：假光束和圆形边界求交；
- `org.magiclib.util.MagicAnim`：归一化进度的动画曲线；
- `org.magiclib.util.MagicUI`：战斗 HUD 状态条、文字与颜色常量；
- `org.lazywizard.lazylib.MathUtils`：两个位置向量之间的中心距离；
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：每帧战斗入口。
