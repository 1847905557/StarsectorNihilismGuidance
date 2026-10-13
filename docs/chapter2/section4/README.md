# 为武器加入命中与光束特效

第一章的武器已经可以使用原版弹体和光束表现。本节为都林自动炮添加命中光点，为乌里光束添加周期光点，并用日志确认两种效果的调用。注册仍然填写完整类名，但命中效果写在弹体文件，光束效果写在武器文件。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 在弹体 `.proj` 的 `onHitEffect` 与光束 `.wpn` 的 `beamEffect` 登记效果类；
- 复制并修改命中效果，在命中点创建粒子和日志；
- 复制并修改光束效果，按秒数间隔创建粒子和日志；
- 核对完整类名与文件位置，定位注册和触发问题。

## 前置知识

- [C1-S5] 的都林自动炮、乌里光束及 `.wpn`、`.proj` 关系；
- [C2-S1] 的 Java 文件、参数修改、注册与回调；
- [C2-S2] 的文件路径、包名、类名与注册值对应关系。

## 先确定效果在哪个时机发生

同样是武器特效，射弹命中与光束持续照射需要不同回调。**效果类的接口决定它接收什么信息，数据里的注册位置决定它被哪个对象使用。**

### 武器效果入口速查表

| 字段 | 文件 | 调用时机 |
|---|---|---|
| `onHitEffect` | 弹体 `.proj` | 弹体命中目标时 |
| `beamEffect` | 光束武器 `.wpn` | 光束激活期间每帧 |

两处都填写完整类名，`beamEffect` 由 `specClass: "beam"` 的光束武器读取。下面沿用前面章节的包名与文件组织方式，分别制作两个效果类。

## 给都林自动炮添加命中光点

在 MOD 根目录创建 `data/scripts/gearin/combat/GearInHitLogger.java`。完整类在后面给出，先看 `onHit(...)` 方法体中的局部片段：

```java
Global.getLogger(GearInHitLogger.class).info("GearIn hit! shieldHit=" + shieldHit);
engine.addHitParticle(point, new Vector2f(), 30f, 1f, 0.4f, Color.ORANGE);
```

游戏在射弹命中时传入射弹、目标、命中点、护盾状态、伤害结果和战斗引擎。方法声明按接口填写，本例先使用命中点 `point`、护盾状态 `shieldHit` 与引擎 `engine`。

第一行记录本次命中；`shieldHit` 为 `true` 表示打在护盾上，为 `false` 则是其他命中。引号里的日志文字按需要修改，验证时使用相同搜索词。

第二行创建橙色光点。`point` 指定位置，`new Vector2f()` 提供零速度，后面的 30、1、0.4 分别是尺寸、亮度和持续秒数，`Color.ORANGE` 指定颜色。**本节可直接调整的是日志文字与粒子参数**，类名、import 和方法声明按完整示例填写。

编者建议先用明显的尺寸与颜色验证。确认回调确实执行之后，再调出合适的表现；粒子小得看不见和效果根本没有触发，需要用不同办法排查。

### 在弹体文件中登记

打开 `data/weapons/proj/GearIn_durin_shot.proj`，在顶层对象内加入下面的局部字段，按相邻字段补好逗号：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInHitLogger"
```

都林自动炮的 `.wpn` 已通过 `projectileSpecId` 引用这个弹体。登记后，命中效果随该弹体调用。

## 给乌里光束添加周期光点

创建 `data/scripts/gearin/combat/GearInBeamLogger.java`。与命中效果相比，这次游戏在光束激活期间反复调用 `advance(...)`，因此先设一个输出间隔：

```java
private static final float REPORT_INTERVAL = 1f;
private float timeSinceLastReport;
```

`REPORT_INTERVAL` 是两次报告之间的秒数，`timeSinceLastReport` 保存上次报告之后累计的时间。它作为类的字段，在多次调用之间保留。

在 `advance(...)` 中累计 `amount`，局部片段如下：

```java
timeSinceLastReport += amount;
if (timeSinceLastReport >= REPORT_INTERVAL) {
    timeSinceLastReport = 0f;
    Global.getLogger(GearInBeamLogger.class).info("GearIn beam tick");
    engine.addHitParticle(beam.getRayEndPrevFrame(), new Vector2f(), 20f, 1f, 0.3f, Color.CYAN);
}
```

`amount` 是本次更新经过的秒数，正常情况下常见值约为 0.017，但它随运行情况变化。**按时间计效果，就累计秒数**。累计达到 1 秒时输出日志，在上一帧的光束末端位置创建青色光点，然后重新计时。

`Color.CYAN` 指定青色，20 为粒子尺寸，0.3 为持续秒数。想让效果更频繁，缩短 `REPORT_INTERVAL`；想改变外观，调整粒子参数。其他逻辑先沿用这个结构，持续伤害与更复杂的每帧效果以后再展开。

### 在光束武器文件中登记

打开 `data/weapons/GearIn_uri.wpn`，在顶层对象内加入：

```json
"beamEffect": "data.scripts.gearin.combat.GearInBeamLogger"
```

`beamEffect` 属于光束武器定义。包声明为 `data.scripts.gearin.combat`，类名为 `GearInBeamLogger`，完整类名与这里的值对应。

## 完整最小例

两个效果类分别写入上面的文件，注册字段写入都林的弹体与乌里的光束定义。

::: details 完整成品：GearInHitLogger

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 射弹命中时记录护盾状态，并在命中点创建橙色粒子。 */
public class GearInHitLogger implements OnHitEffectPlugin {

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Global.getLogger(GearInHitLogger.class).info("GearIn hit! shieldHit=" + shieldHit);
        engine.addHitParticle(point, new Vector2f(), 30f, 1f, 0.4f, Color.ORANGE);
    }
}
```

:::

::: details 完整成品：GearInBeamLogger

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import org.lwjgl.util.vector.Vector2f;

/** 光束激活期间计时，每秒记录日志并创建末端粒子。 */
public class GearInBeamLogger implements BeamEffectPlugin {

    /** 两次报告之间的秒数。 */
    private static final float REPORT_INTERVAL = 1f;

    private float timeSinceLastReport;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            Global.getLogger(GearInBeamLogger.class).info("GearIn beam tick");
            engine.addHitParticle(beam.getRayEndPrevFrame(), new Vector2f(), 20f, 1f, 0.3f, Color.CYAN);
        }
    }
}
```

:::

保存时核对 **目录与 package、文件名与 class、注册字段与完整类名**。这一组对应关系与船插相同，变化的是具体接口与数据字段。

## 验证方案

1. 保存效果类与两处注册字段，重启游戏，给测试舰船安装都林自动炮和乌里光束。
2. 进入模拟战，用都林分别击中敌舰护盾与船体，观察命中光点。
3. 用乌里持续照射至少数秒，再停止开火，观察光点和日志的时间间隔。
4. 在 `Starsector/starsector-core/starsector.log` 的本次启动记录中搜索 `GearIn hit!` 与 `GearIn beam tick`。
5. 修改其中一条日志文字或 `REPORT_INTERVAL`，保存并重启，重复相同操作。

预期结果：

- 每次触发命中回调时，日志出现 `GearIn hit! shieldHit=...`，命中点创建橙色光点。
- 护盾与船体命中对应不同的 `shieldHit` 值。
- 光束激活期间约每累计 1 秒输出一次日志，并在末端创建青色光点；停火后该效果停止调用。
- 修改日志字符串后搜索词随之变化，修改间隔后报告与粒子的出现频率变化。

## 故障分析

- **命中或光束效果没有触发**：字段写进不同对象、名称拼错，或光束字段用于非光束武器。
    - **解决方案**：核对都林弹体的 `onHitEffect` 和乌里光束的 `beamEffect`，保存后重启。

- **日志出现 Problem loading class [...]**：包名、类名、文件目录或注册值不一致。
    - **解决方案**：按完整类名逐段核对文件和声明，再重启。

- **命中日志出现但光点难以辨认**：粒子尺寸或持续时间过小。
    - **解决方案**：调整 `addHitParticle` 的尺寸和持续时间，在相同目标上重新测试。

- **光束日志间隔不符**：`REPORT_INTERVAL` 数值与预期秒数不同。
    - **解决方案**：设为 `1f`，保存并重启，持续照射后比较报告时间。

## 总结

命中与光束效果沿用数据注册、Java 回调的组织方式。`onHitEffect` 跟随弹体命中，`beamEffect` 跟随光束更新；传入的位置、目标和时间信息决定效果在哪里、何时发生。

目前的示例用粒子与日志建立可观察结果。各注册字段的实例关系、开火和武器每帧效果，以及光束伤害过程，由 [C8-S5] 与 [C9-S3] 继续讲解。

## 建议参考项

- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：命中效果的 `onHit(...)` 接口。
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束更新的 `advance(...)` 接口。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addHitParticle(...)` 创建粒子。
- `com.fs.starfarer.api.combat.BeamAPI`：`getRayEndPrevFrame()` 读取上一帧末端位置。
- `data/weapons/proj/`：弹体定义与 `onHitEffect` 字段。
- `data/weapons/`：光束武器定义与 `beamEffect` 字段。
