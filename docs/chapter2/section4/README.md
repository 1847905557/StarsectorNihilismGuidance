# 为武器加入命中与光束特效

本节将引入一些基本的武器效果，包括弹体命中 (OnHit) 与光束武器每帧 (BeamEffect) 两类，并初步解析它们在数据文件中的注册方式。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 说出武器调用代码的两个注册列：弹体文件 `.proj` 的 `onHitEffect`、光束武器文件 `.wpn` 的 `beamEffect`；
- 按模板写一个 OnHit 效果类：射弹命中时写日志、在命中点生成光点；
- 按模板写一个 BeamEffect 效果类：光束激活期间每秒写一行日志；
- 用类名三处一致 (武器版) 核对注册，独立解决 "效果未触发" 与 "`Problem loading class`"。

## 前置知识

- 武器数据链：`weapon_data.csv` / `.wpn` / `.proj`[C1-S5]—— 本节给其中的 `.proj` 与 `.wpn` 加键；
- 模板与参数、类名三处一致 [C2-S1]、[C2-S2]。

## 武器调用效果类的机制

与船插相同，游戏不会自动加载未注册的类 —— 需要在数据文件里写类名。武器侧有两个注册列：

| 注册列 | 写在哪个文件 | 调用时机 |
|---|---|---|
| `onHitEffect` | 弹体文件 `.proj` | 该弹体**命中**目标时 |
| `beamEffect` | 武器文件 `.wpn`(仅光束武器) | 光束武器**激活期间**每帧 |

`beamEffect` 只在 `specClass: "beam"` 的光束武器上被读取 —— 写到火炮、导弹的 `.wpn` 上静默无效。这两列的值都是完整类名 (包名.类名)，游戏在需要时创建实例并调用。建议始终把效果类与对应武器放在同一 MOD 内，避免跨 MOD 依赖。

## OnHit：射弹命中时

### 第一步：效果类

文件：`src\data\scripts\gearin\combat\GearInHitLogger.java`：

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

/**
 * 射弹命中效果：命中时打一行日志，并在命中点放一个橙色光点。
 * 注册在弹体文件的 onHitEffect 键；方法签名是模板，只有日志文字是参数。
 */
public class GearInHitLogger implements OnHitEffectPlugin {

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Global.getLogger(GearInHitLogger.class).info("GearIn hit! shieldHit=" + shieldHit);
        engine.addHitParticle(point, new Vector2f(), 30f, 1f, 0.4f, Color.ORANGE);
    }
}
```

两类内容，与船插同构：

- **`implements OnHitEffectPlugin` + `onHit(...)` 方法**：按模板使用。`onHit` 的方法签名有六个参数，实际需要修改的只有一行。游戏调用时会把 "射弹、目标、命中点、是否打在护盾上" 等信息传入，本节只使用其中两个：
  - 日志文字 (引号内)：命中时写入 `starsector.log`，`shieldHit` 显示这次打在护盾还是船体；
  - `addHitParticle(...)` 一行：在命中点生成一个光点 (大小、颜色为数字参数，可修改)。粒子机制本节不展开，只需知道这一行在命中点生成光点。
- **`package` / `import` / 类名**：按模板使用。

### 第二步：注册到武器

打开 [C1-S5] 制作的火炮的弹体文件 (`data/weapons/GearIn_durin_shot.proj` 之类)，在 JSON 中加一行：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInHitLogger",
```

### 第三步：验证

1. 重启游戏，进模拟战用这把武器命中敌人；
2. 退出游戏打开 `starsector.log`：每命中一次出现一行 `GearIn hit! shieldHit=false`(打在船体) 或 `shieldHit=true`(打在护盾)；
3. 命中瞬间命中点出现一个橙色光点。

建议验证前清空 `starsector.log` 再重启，便于定位本次运行的新行。

## BeamEffect：光束激活期间

### 第一步：效果类

文件：`src\data\scripts\gearin\combat\GearInBeamLogger.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import org.lwjgl.util.vector.Vector2f;

/**
 * 光束效果：光束武器激活期间每帧被调用，每累计 1 秒打一行日志并在光束末端放一个青色光点。
 * 注册在光束武器 .wpn 的 beamEffect 键；REPORT_INTERVAL 与日志文字是参数。
 */
public class GearInBeamLogger implements BeamEffectPlugin {

    /** 每累计多少秒打一行日志（参数）。 */
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

新增的一个概念：**`advance(float amount, ...)` 在光束激活期间每帧被调用**，`amount` 是距上一帧的秒数 (正常约 0.017)。字段 `timeSinceLastReport` 每帧累加 `amount`，达到 `REPORT_INTERVAL`(参数，默认 1 秒) 时输出一行日志、生成一个光点并归零 —— 这是 "每秒执行一次" 的固定写法，后续编写光束持续伤害、每帧修正都会用到。其余按模板使用。

### 第二步：注册到武器

打开 [C1-S5] 制作的**光束武器**的 `.wpn`，在 JSON 中加一行：

```json
"beamEffect": "data.scripts.gearin.combat.GearInBeamLogger",
```

### 第三步：验证

1. 重启游戏，进模拟战用光束武器照射敌人并保持开火；
2. `starsector.log`：光束激活期间每秒一行 `GearIn beam tick`，停止开火即停；
3. 光束末端 (命中点) 每秒出现一个青色光点。

## 类名三处一致 (武器版)

注册列的值 = 完整类名，同样三处必须一致：

| 位置 | 本例 (OnHit) |
|---|---|
| 文件路径 | `src/data/scripts/gearin/combat/GearInHitLogger.java` |
| 文件内 `public class` 后 | `GearInHitLogger` |
| 注册列值 | `data.scripts.gearin.combat.GearInHitLogger` |

`package` 行与目录对应、前两处拼起来等于第三处 —— 与船插同一套规则，只是注册列从 `hull_mods.csv` 的 `script` 换成了 `.proj` 的 `onHitEffect` / `.wpn` 的 `beamEffect`。

## 错误对照表

| 症状 | 原因 | 修法 |
|---|---|---|
| `Problem loading class [data.scripts.gearin.combat.GearInHitLogger]` | 类名三处不一致，或类不存在 | 按类名三处一致 (武器版) 核对 |
| 命中无日志、无光点 | `onHitEffect` 写错文件 (写到了 `.wpn`) 或键名拼错 | 确认写在弹体 `.proj` 上，键名 `onHitEffect` |
| 光束无日志 | `beamEffect` 写到了非光束武器，或键名拼错 | 确认 `.wpn` 的 `specClass` 是 `"beam"`，键名 `beamEffect` |
| 日志有但光点不出现 | 光点尺寸或持续时间过小 | 调整 `addHitParticle` 的 size 与 duration 参数 |

## 验证方案

前置数据：已经绑定 `GearInHitLogger` 的弹体武器和已经绑定 `GearInBeamLogger` 的光束武器。

1. 从模拟战与 `starsector.log`开始验证。
2. 重启游戏并进入模拟战，用弹体武器命中敌舰。
3. 用光束武器持续照射敌舰，然后停止开火。
4. 修改一处日志文字，重启后重复对应攻击。

预期结果：

- `starsector.log` 每发一行 `GearIn hit! shieldHit=...`，命中点出现橙色光点。
- 激活期间每秒出现一行 `GearIn beam tick`，光束末端出现青色光点，停火后停止输出。

清理：把临时修改的日志文字恢复为 `GearIn hit! shieldHit=` 或 `GearIn beam tick`。

## 故障分析

1. **效果类不触发**。触发：注册列写错文件 (`onHitEffect` 写到 `.wpn`)、键名拼错、`beamEffect` 挂到非光束武器。可观察：无日志、无报错 (静默)。修正：核对注册列位置与键名。
2. **`Problem loading class [...]`**。触发：类名三处不一致或类不存在。修正：按类名三处一致 (武器版) 核对。
3. **命中日志有但光点不出现**。触发：粒子参数过小。修正：调大 `addHitParticle` 的 size(第三个数字) 与 duration(第五个数字)。
4. **光束日志节奏不符**。触发：`REPORT_INTERVAL` 理解偏差。可观察：改大后日志变稀。修正：该数字 = 多少秒输出一行，`1f` = 每秒一行。

## 总结

给武器添加效果类 = 在数据文件里写类名：弹体 `.proj` 的 `onHitEffect`(命中时)、光束 `.wpn` 的 `beamEffect`(激活期间每帧)。效果类 = 按模板使用的接口方法 + 可修改的参数 (日志文字、间隔、粒子数值)。

这两个接口的完整机制 —— 每种注册列的实例模型、`onFireEffect`/`everyFrameEffect` 等其余注册列、光束结算细节 —— 在 [C7-S4] (观察武器开火与伤害过程) 与 [C7-S9] (为武器加入动画与效果回调) 展开。

## 建议参考项

- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：命中回调接口 (`onHit(...)`)。
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束每帧回调接口 (`advance(float, CombatEngineAPI, BeamAPI)`)。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addHitParticle(...)`(命中点粒子) 等。
- `com.fs.starfarer.api.combat.BeamAPI`：`getRayEndPrevFrame()`(上一帧光束末端位置)。
- `data/weapons/*.proj`：弹体文件 (`onHitEffect` 注册列)。
- `data/weapons/*.wpn`：武器文件 (`beamEffect` 注册列，仅 `specClass: "beam"`)。
