# 配置武器脚本

武器持续开火、弹体生成、命中与光束照射，会在不同位置调用脚本。本节将这些入口分别绑定到都林和乌里，用日志记录武器状态、射弹来源与伤害结算，再以光束末端的粒子对应逐帧信息。每段逻辑都使用本次回调提供的对象。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 选择 `wpn` 或 `proj` 的对应脚本字段；
- 读取当前武器、射弹、命中与光束参数；
- 区分每实例状态和单次事件；
- 用日志与粒子核对调用。

## 前置知识

- [C1-S5] 的都林弹体与乌里光束；
- [C2-S4] 的效果登记；
- [C6-S4] 的编译与 JAR；
- [C8-S2] 的对象和帧状态；
- [C8-S3] 的逐帧与暂停。

## 将脚本绑定到实际使用的数据

### 武器脚本字段速查表

| 字段 | 接口 | 当前用途 |
|---|---|---|
| `wpn.everyFrameEffect` | `EveryFrameWeaponEffectPlugin` | 读取当前武器的持续状态 |
| `proj.onFireEffect` | `OnFireEffectPlugin` | 弹体生成后的事件 |
| `proj.onHitEffect` | `OnHitEffectPlugin` | 当前命中与结算 |
| `wpn.beamEffect` | `BeamEffectPlugin` | 激活光束的逐帧信息 |

**`.wpn` 绑定武器与光束逻辑，`.proj` 绑定弹体事件。** 完整类名指向 JAR 中的实现，但还要由当前武器实际使用这份数据。特别是生成与命中脚本，登记在武器引用的弹体文件里，事件才能到达对应类。

## 记录武器实例的开火过程

在都林的 `.wpn` 中，将每帧效果绑定到 `GearInWeaponFlowEffect`。局部字段如下：

```json
"everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFlowEffect"
```

每把武器拥有自己的效果实例，所以它的计时器与 `wasFiring` 只跟踪当前这把武器。绑定时，`WeaponEffectPluginWithInit.init(...)` 记录武器标识、规格与光束分类。

随后在 `advance(...)` 中比较当前与上次开火状态。由未开火转为开火时，记录一次变化，再保存本帧状态：

```java
if (weapon.isFiring() && !wasFiring) {
    LOG.info("GearIn weapon " + weapon.getId() + " started firing"
            + " (before-frame callback)");
}
wasFiring = weapon.isFiring();
```

这个比较只在开始开火时产生日志。持续射击中的弹药、冷却与蓄力，放到 `EveryFrameWeaponEffectPluginWithAdvanceAfter.advanceAfter(...)` 记录；本例采用 2 秒报告间隔，避免连续输出淹没开火变化。

**同一帧操作前后，读到的武器状态可能不同。** `advanceAfter(...)` 提供武器本帧操作完成后的观察位置，因此两个方法分别记录变化与执行后的结果。

## 在生成回调处理本次弹体

都林引用的是 `GearIn_durin_shot.proj`，将生成脚本登记在这份文件中：

```json
"onFireEffect": "data.scripts.gearin.combat.GearInShotOnFireEffect"
```

`onFire(...)` 收到刚生成的 `projectile`、来源 `weapon` 与本场 `engine`。规格、伤害、EMP 与来源都可以在这个时机读取，生成位置也可用于安排单次表现。接口参数为：

```java
void onFire(DamagingProjectileAPI projectile,
            WeaponAPI weapon,
            CombatEngineAPI engine)
```

完整例将这些信息写入同一条记录。来源武器或舰船可能因创建方式不同而缺席，日志保留相应空值。每次开火事件传入的是本次生成的弹体，下一发继续使用下一次参数。

## 从命中参数读取结算

在同一份弹体文件中登记命中效果：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInShotOnHitEffect"
```

弹体命中后，`onHit(...)` 接收目标、命中位置、护盾状态与 `ApplyDamageResultAPI`。`shieldHit` 标记是否命中护盾，`damageResult` 提供本次护盾、装甲、结构与 EMP 结算：

```java
void onHit(DamagingProjectileAPI projectile,
           CombatEntityAPI target,
           Vector2f point,
           boolean shieldHit,
           ApplyDamageResultAPI damageResult,
           CombatEngineAPI engine)
```

射弹、导弹与光束脉冲的对应命中都可以沿弹体事件处理。**目标与结果使用本次回调参数。** 它们一起描述当前这笔命中，保留上一次的目标或结算来处理下一次事件，会混淆实际结果。

## 在照射期间读取光束

乌里光束的逐帧脚本登记在 `.wpn`：

```json
"beamEffect": "data.scripts.gearin.combat.GearInBeamLogger"
```

光束激活期间，`advance(...)` 接收步长、引擎与 `BeamAPI`。起点、终点、长度、伤害和目标都从这次传入的光束读取：

```java
void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
```

完整类每累计 1 秒记录目标、`didDamageThisFrame()` 与长度，再在上一帧末端位置创建青色光点。日志对应帧状态，粒子对应读取的末端位置。光束停止激活后更新停止，下一次激活沿实例中的计时继续。

**光束在画面中存在，不代表每一帧都产生伤害。** 比较命中与未命中时，重新读取 `getDamageTarget()` 和 `didDamageThisFrame()`，才能把持续照射与本帧结算区分开。

## 完整最小例

四个类保存到 `src/data/scripts/gearin/combat/`，当前字段写入对应都林和乌里数据，保存后构建并重启。

::: details 完整成品：`GearInWeaponFlowEffect`

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.util.IntervalUtil;

/** 记录武器实例的初始化、开火变化和操作后状态。 */
public class GearInWeaponFlowEffect implements EveryFrameWeaponEffectPluginWithAdvanceAfter,
        WeaponEffectPluginWithInit {

    private static final Logger LOG = Global.getLogger(GearInWeaponFlowEffect.class);

    /** 开火状态 (每个武器效果实例各有一份)。 */
    private boolean wasFiring = false;
    private final IntervalUtil weaponReportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(WeaponAPI weapon) {
        LOG.info("GearIn weapon init " + weapon.getId()
                + " spec=" + weapon.getSpec().getWeaponId()
                + " beam=" + weapon.isBeam());
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon " + weapon.getId() + " started firing"
                    + " (before-frame callback)");
        }
        wasFiring = weapon.isFiring();
    }

    @Override
    public void advanceAfter(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧武器操作完成后 (EveryFrameWeaponEffectPluginWithAdvanceAfter)
        weaponReportInterval.advance(amount);
        if (weapon.isFiring() && weaponReportInterval.intervalElapsed()) {
            LOG.info("GearIn weapon " + weapon.getId() + " after-frame:"
                    + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                    + " cooldown=" + weapon.getCooldownRemaining()
                    + " charge=" + weapon.getChargeLevel()
                    + " firing=" + weapon.isFiring());
        }
    }
}
```

:::

::: details 完整成品：`GearInShotOnFireEffect`

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 记录本次生成弹体的规格、来源与伤害。 */
public class GearInShotOnFireEffect implements OnFireEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInShotOnFireEffect.class);

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        LOG.info("GearIn onFire " + projectile.getProjectileSpecId()
                + " weapon=" + (weapon == null ? "null" : weapon.getId())
                + " source=" + (projectile.getSource() == null ? "null" : projectile.getSource().getHullSpec().getHullId())
                + " spawnType=" + projectile.getSpawnType()
                + " damage=" + projectile.getDamageAmount()
                + " emp=" + projectile.getEmpAmount());
    }
}
```

:::

::: details 完整成品：`GearInShotOnHitEffect`

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/** 记录本次命中的目标、护盾与伤害结算结果。 */
public class GearInShotOnHitEffect implements OnHitEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInShotOnHitEffect.class);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        LOG.info("GearIn onHit " + projectile.getProjectileSpecId()
                + " target=" + target.getClass().getSimpleName()
                + " shieldHit=" + shieldHit
                + " hull=" + damageResult.getDamageToHull()
                + " armor=" + damageResult.getTotalDamageToArmor()
                + " shields=" + damageResult.getDamageToShields()
                + " emp=" + damageResult.getEmpDamage());
    }
}
```

:::

::: details 完整成品：`GearInBeamLogger`

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class GearInBeamLogger implements BeamEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInBeamLogger.class);
    private static final float REPORT_INTERVAL = 1f;

    private float timeSinceLastReport;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        timeSinceLastReport += amount;
        if (timeSinceLastReport < REPORT_INTERVAL) {
            return;
        }
        timeSinceLastReport = 0f;
        String target = beam.getDamageTarget() == null
                ? "null"
                : beam.getDamageTarget().getClass().getSimpleName();
        LOG.info("GearIn beam tick target=" + target
                + " didDamageThisFrame=" + beam.didDamageThisFrame()
                + " length=" + beam.getLength());
        engine.addHitParticle(beam.getRayEndPrevFrame(), new Vector2f(),
                20f, 1f, 0.3f, Color.CYAN);
    }
}
```

:::

## 验证方案

1. 保存四个类与武器／弹体字段，构建并核对 `class` 条目。
2. 在测试战斗使用都林，查看 `weapon` `init` 和操作前后状态。
3. 发射并命中目标，比较 `onFire` 的生成参数与 `onHit` 的实际结算。
4. 使用乌里光束，比较有目标、无目标和当前帧伤害，观察末端青色粒子。
5. 暂停再恢复，核对实际回调和计时变化。
6. 搜索四组前缀，按字段、数据文件与完整类名确认调用归属。

预期结果：

- 每武器实例记录自己的 `init`、开火变化和操作后参数。
- `onFire` 对应当前生成弹体，`onHit` 对应当前命中与结算。
- 光束日志按当前帧读取目标和伤害，激活期间按秒记录。
- 暂停时相关更新保持，恢复后继续当前状态。
- 日志与视觉可以定位各入口实际使用的对象。

## 故障分析

- **`.wpn` 或 `.proj` 中没有出现任何对应日志**：字段、文件类型或完整类名不正确时，游戏不会调用脚本。
    - **解决方案**：检查字段名、文件类型和完整类名，确认 JAR 中存在对应的 `.class` 条目。

- **日志出现 `Problem loading class`**：注册值与 Java 类的实际名称不一致。
    - **解决方案**：核对 `package`、类名、目录路径和注册字符串，重新构建 JAR。

- **`EveryFrame` 只有 `advance` 日志，操作后的状态没有记录**：类没有实现带 `advanceAfter` 的接口，或没有实现对应方法。
    - **解决方案**：让类实现 `EveryFrameWeaponEffectPluginWithAdvanceAfter`，并实现 `advanceAfter(...)`。

- **`onFire` 或 `onHit` 没有回调**：脚本类没有绑定到测试弹体实际使用的 `.proj`。
    - **解决方案**：把类名写入 `.proj` 的对应字段，确认测试武器实际使用了该弹体文件。

- **`BeamEffect` 在非光束武器上没有日志**：`beamEffect` 只对光束武器的激活阶段生效。
    - **解决方案**：只在光束武器的 `.wpn` 中配置 `beamEffect`，并在光束激活期间观察日志。

- **光束日志显示上一帧目标或命中状态**：回调中复用了上一帧的对象或状态。
    - **解决方案**：在每次 `advance(...)` 中重新读取 `didDamageThisFrame()` 和 `getDamageTarget()`，不要跨帧保存对象。

- **暂停后恢复时报告时间偏移**：观察计时器在暂停帧继续累计。
    - **解决方案**：在需要暂停一致性的外层战斗插件中检查 `engine.isPaused()`，只在非暂停帧累计观察时间。

## 总结

武器效果保存每把武器的持续状态，开火与命中回调提供本次弹体及结算，光束效果读取激活期间的帧信息。它们分别由武器和弹体数据中的字段接入。

都林与乌里的日志现在能够对应到这些时机。后续动画和复杂效果沿用同一组入口，在需要的事件中读取对象、计算结果并安排表现。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin`：武器级持续回调 (`everyFrameEffect`)；
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束激活期间的逐帧回调 (`beamEffect`)；
- `com.fs.starfarer.api.combat.OnFireEffectPlugin`：射弹生成回调 (`onFireEffect`)；
- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：当前命中上下文回调 (`onHitEffect`)；
- `data/weapons/`：原版武器与弹体定义，`everyFrameEffect`、`beamEffect`、`onFireEffect`、`onHitEffect` 注册字段的原版实例；
- `Starsector/starsector-core/starsector.log`：核对回调日志的读取位置。
