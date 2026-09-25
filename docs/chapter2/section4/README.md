# 为武器加入命中与光束特效

本节将引入一些基本的武器效果，包括弹体命中 (OnHit) 与光束武器每帧 (BeamEffect) 两类，并初步解析它们在数据文件中的注册方式。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 列出武器调用代码的两个注册列：弹体文件 `.proj` 的 `onHitEffect`、光束武器文件 `.wpn` 的 `beamEffect`；
- 仿照示例写一个 OnHit 效果类：射弹命中时写日志、在命中点生成光点；
- 仿照示例写一个 BeamEffect 效果类：光束激活期间每秒写一行日志；
- 核对文件路径、`package`、类名和注册值，独立解决 "效果未触发" 与 "`Problem loading class`"。

## 前置知识

- [C1-S5] 的 `weapon_data.csv`、`.wpn` 与 `.proj` 数据关系；
- [C2-S1] 的最小示例 Java 文件、参数修改、注册与回调；
- [C2-S2] 的文件名、类名与注册类名一致规则。

## 武器调用效果类的机制

真正的特效由代码驱动。既然是代码，那一般来说都需要配置注册位置。

### 武器注册列速查表

武器侧有两个注册列：

| 注册列 | 写在哪个文件 | 调用时机 |
|---|---|---|
| `onHitEffect` | 弹体文件 `.proj` | 该弹体**命中**目标时 |
| `beamEffect` | 武器文件 `.wpn`(仅光束武器) | 光束武器**激活期间**每帧 |

`beamEffect` 只在 `specClass: "beam"` 的光束武器上被读取。这两列的值都是完整类名 (包名.类名)，如果您直到现在都不知道什么叫包名和类名，那您接下来抄例子的时候可得注意了。

## OnHit：射弹命中时

### 实现命中效果类

文件：`data/scripts/gearin/combat/GearInHitLogger.java`：

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
 * 注册在弹体文件的 onHitEffect 键；方法签名照抄示例，只有日志文字是参数。
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

两类内容，基本上还是某种疑似固定部分加某种疑似可变部分：

- **`implements OnHitEffectPlugin` \+ `onHit(...)` 方法**：照抄示例即可。`onHit` 的方法签名有六个参数，实际需要修改的只有一行。游戏调用时会把 "射弹、目标、命中点、是否打在护盾上" 等信息传入，本节只使用其中两个：
    - 日志文字 (引号内)：命中时写入 `starsector.log`，`shieldHit` 显示这次打在护盾还是船体；
    - `addHitParticle(...)` 一行：在命中点生成一个光点 (大小、颜色为数字参数，可修改)。粒子机制本节不展开，只需知道这一行在命中点生成光点。
- **`package` / `import` / 类名**：照抄示例即可。

### 注册命中效果

打开 [C1-S5] 制作的火炮的弹体文件 (`data/weapons/proj/GearIn_durin_shot.proj` 之类)，在 JSON 中加一行：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInHitLogger",
```

## BeamEffect：光束激活期间

### 实现光束效果类

文件：`data/scripts/gearin/combat/GearInBeamLogger.java`：

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

    /** 每累计多少秒打一行日志 (参数)。 */
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

新增的一个概念：**`advance(float amount, ...)` 在光束激活期间每帧被调用**，`amount` 是距上一帧的秒数 (正常约 0.017)。字段 `timeSinceLastReport` 每帧累加 `amount`，达到 `REPORT_INTERVAL`(参数，默认 1 秒) 时输出一行日志、生成一个光点并归零。固定间隔的写法同样适用于光束持续伤害与每帧修正。其余部分照抄示例即可。

### 注册光束效果

打开 [C1-S5] 制作的**光束武器**的 `.wpn`，在 JSON 中加一行：

```json
"beamEffect": "data.scripts.gearin.combat.GearInBeamLogger",
```

## 对齐效果类的注册信息

注册列的值必须是完整类名，同样三处必须一致：文件路径 `data/scripts/gearin/combat/GearInHitLogger.java`、文件内 `public class` 后的 `GearInHitLogger`、注册列值 `data.scripts.gearin.combat.GearInHitLogger`。

`package` 行与目录对应、前两处拼起来等于第三处 —— 与船插同一套规则，只是注册列从 `hull_mods.csv` 的 `script` 换成了 `.proj` 的 `onHitEffect` / `.wpn` 的 `beamEffect`。

## 验证方案

前置数据：已经绑定 `GearInHitLogger` 的弹体武器和已经绑定 `GearInBeamLogger` 的光束武器。建议验证前清空 `starsector.log` 再重启，便于定位本次运行的新行。

1. 从模拟战与 `starsector.log`开始验证。
2. 重启游戏并进入模拟战，用弹体武器命中敌舰。
3. 用光束武器持续照射敌舰，然后停止开火。
4. 修改一处日志文字，重启后重复对应攻击。

预期结果：

- `starsector.log` 每发一行 `GearIn hit! shieldHit=...`，命中点出现橙色光点。
- 激活期间每秒出现一行 `GearIn beam tick`，光束末端出现青色光点，停火后停止输出。

## 故障分析

- **效果类没有触发**：注册列写进了错误文件、键名拼错，或 `beamEffect` 挂在非光束武器上时，日志和画面都会保持安静。
    - **解决方案**：核对 `.proj` 的 `onHitEffect` 与光束 `.wpn` 的 `beamEffect`，修正后重新命中目标。

- **日志出现 `Problem loading class [...]`**：注册类名、包声明和文件路径没有指向同一个类。
    - **解决方案**：逐字核对三处名称，重启。

- **命中日志出现但光点不可见**：粒子的尺寸或持续时间过小。
    - **解决方案**：调大 `addHitParticle` 的第三个参数 `size` 与第五个参数 `duration`，再次命中。

- **光束日志节奏不符**：`REPORT_INTERVAL` 表示两次输出之间的秒数，数值越大日志越稀。
    - **解决方案**：设为 `1f` 后持续照射目标。

## 总结

给武器添加效果类，就是在数据文件里写类名。弹体 `.proj` 写 `onHitEffect`(命中时)，光束 `.wpn` 写 `beamEffect`(激活期间每帧)。效果类由仿照示例的接口方法和可修改的参数 (日志文字、间隔、粒子数值) 组成。

这两个接口的完整机制 —— 每种注册列的实例模型、`onFireEffect`/`everyFrameEffect` 等其余注册列、光束结算细节 —— 在观察武器开火与伤害过程、为武器加入动画与效果回调时展开。

## 建议参考项

- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：命中回调接口 (`onHit(...)`)。
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束每帧回调接口 (`advance(float, CombatEngineAPI, BeamAPI)`)。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addHitParticle(...)`(命中点粒子) 等。
- `com.fs.starfarer.api.combat.BeamAPI`：`getRayEndPrevFrame()`(上一帧光束末端位置)。
- `data/weapons/proj/*.proj`：弹体文件 (`onHitEffect` 注册列)。
- `data/weapons/*.wpn`：武器文件 (`beamEffect` 注册列，仅 `specClass: "beam"`)。
