# 配置武器脚本

第二章已经给武器绑定命中与光束效果，本节继续区分武器每帧、生成射弹、命中结算和光束更新。各回调接收的对象范围不同，先按实际需要选择入口，再读取当前实例或事件提供的信息。

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

## 按事件选择入口

### 武器脚本字段速查表

| 字段 | 接口 | 当前用途 |
|---|---|---|
| `wpn.everyFrameEffect` | `EveryFrameWeaponEffectPlugin` | 读取当前武器的持续状态 |
| `proj.onFireEffect` | `OnFireEffectPlugin` | 弹体生成后的事件 |
| `proj.onHitEffect` | `OnHitEffectPlugin` | 当前命中与结算 |
| `wpn.beamEffect` | `BeamEffectPlugin` | 激活光束的逐帧信息 |

**字段决定调用位置，接口决定参数。** 完整类名和当前 JAR 对应，只有实际使用该数据文件的对象才会调用它。

## 观察每把武器的持续状态

在都林 `wpn` 中设置局部字段：

```json
"everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFlowEffect"
```

每把武器有自己的效果实例，`wasFiring` 因此只保存该武器的开火状态。`WeaponEffectPluginWithInit` 提供绑定时的 `init`，`EveryFrameWeaponEffectPluginWithAdvanceAfter` 提供操作完成后的 `advanceAfter`。

**同一帧操作前后读取的值可能不同。** 当前 `advance` 记录开始开火的变化，`advanceAfter` 记录已经执行后的弹药、冷却和蓄力。

开火边沿局部片段如下：

```java
if (weapon.isFiring() && !wasFiring) {
    LOG.info("GearIn weapon " + weapon.getId() + " started firing"
            + " (before-frame callback)");
}
wasFiring = weapon.isFiring();
```

操作后累计 2 秒再报告，完整类也记录 `init` 里的武器 ID、规格与是否光束。输出间隔用于减少持续日志，当前武器实例保留自己的计时器。

## 观察射弹生成

在都林实际引用的 `GearIn_durin_shot.proj` 中设置：

```json
"onFireEffect": "data.scripts.gearin.combat.GearInShotOnFireEffect"
```

`onFire` 接收刚生成的 `projectile`、来源 `weapon` 和当前 `engine`。它适合读取规格、伤害、EMP 和来源，并在生成位置安排单次表现。

```java
void onFire(DamagingProjectileAPI projectile,
            WeaponAPI weapon,
            CombatEngineAPI engine)
```

当前完整类记录这些字段，来源对象可能因创建方式不同而缺席，日志按实际对象状态记录。本次弹体用于本次事件，下一发仍从新的回调参数取得。

## 观察本次命中结果

在同一弹体登记：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInShotOnHitEffect"
```

`onHit` 接收当前目标、位置、护盾状态和 `ApplyDamageResultAPI`。`shieldHit` 标记护盾命中，`damageResult` 提供当前护盾、装甲、结构与 EMP 结算。

```java
void onHit(DamagingProjectileAPI projectile,
           CombatEntityAPI target,
           Vector2f point,
           boolean shieldHit,
           ApplyDamageResultAPI damageResult,
           CombatEngineAPI engine)
```

射弹、导弹和光束脉冲的对应命中沿这类弹体事件处理。**读取本次结果时，直接使用回调参数**，避免上一笔命中对象参与当前判断。

## 观察光束当前帧

在乌里 `wpn` 设置：

```json
"beamEffect": "data.scripts.gearin.combat.GearInBeamLogger"
```

`advance` 接收 `amount`、`engine` 和 `BeamAPI`。光束提供起点、终点、长度、伤害和当前目标，`didDamageThisFrame` 与 `getDamageTarget` 每次重新读取。

```java
void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
```

完整类每累计 1 秒记录目标、帧伤害标志与长度，再在上一帧末端位置创建青色光点。停止激活后该更新过程停止，下一次激活继续按实例计时。

编者建议比较光束命中与未命中的两段结果。持续存在的画面和逐帧伤害并不是同一个信息，读取参数时要按本帧结算解释。

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

武器每帧与光束属于持续实例，射弹生成与命中属于当前事件。`wpn` 和 `proj` 分别登记对应接口，实例字段保存自己的状态，事件参数则提供本次对象和结果。

后续动画、伤害和复杂效果继续采用这些入口。先选对象与时机，再组织读取和表现，完整类名、数据引用与日志就能形成可核对的调用关系。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin`：武器级持续回调 (`everyFrameEffect`)；
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束激活期间的逐帧回调 (`beamEffect`)；
- `com.fs.starfarer.api.combat.OnFireEffectPlugin`：射弹生成回调 (`onFireEffect`)；
- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：当前命中上下文回调 (`onHitEffect`)；
- `data/weapons/`：原版武器与弹体定义，`everyFrameEffect`、`beamEffect`、`onFireEffect`、`onHitEffect` 注册字段的原版实例；
- `Starsector/starsector-core/starsector.log`：核对回调日志的读取位置。
