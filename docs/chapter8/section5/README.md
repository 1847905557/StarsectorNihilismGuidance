# 配置武器脚本

同一把武器的持续状态、射弹生成、命中结算和光束运行由不同的脚本入口负责。本节从 `.wpn` 与 `.proj` 的字段开始，逐一把字段绑定到 Java 接口，并用日志和视觉效果确认回调确实发生。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 为武器或弹体配置正确的脚本入口；
- 根据回调参数读取开火、命中和光束状态；
- 用日志和游戏内效果验证脚本调用。

## 前置知识

- [C1-S1] 的 MOD 目录和 `starsector.log` 位置；
- [C2-S4] 的命中效果、光束效果与脚本注册规则；
- [C6-S1] 的 Java 编译与 JAR 输出；
- [C8-S3] 的战斗插件、暂停状态和 `advance` 回调；
- [C8-S2] 的战斗实体、弹体、导弹和光束遍历。

## 选择武器脚本入口

武器脚本的字段决定游戏在什么对象、什么时机创建并调用 Java 类。先根据要观察的事件选择字段，再把完整类名写入对应数据文件。

### 武器脚本注册速查表

先用这张表确定注册位置，再阅读后面的参数和完整示例。

| 数据文件字段 | Java 接口 | 触发时机 |
| --- | --- | --- |
| `.wpn` 的 `everyFrameEffect` | `EveryFrameWeaponEffectPlugin` | 武器每帧运行 |
| `.proj` 的 `onFireEffect` | `OnFireEffectPlugin` | 射弹生成后 |
| `.proj` 的 `onHitEffect` | `OnHitEffectPlugin` | 射弹命中并完成命中上下文时 |
| `.wpn` 的 `beamEffect` | `BeamEffectPlugin` | 光束激活期间逐帧运行 |

四个字段都使用 Java 完整类名。包名、类名和字段值保持一致，JAR 中也要存在对应的 `.class` 文件。

## 配置 `.wpn` 的 EveryFrame

`everyFrameEffect` 适合读取武器自身的持续状态，例如 `isFiring()`、弹药、冷却和蓄力。游戏为每把绑定了该脚本的武器创建独立实例，因此 `wasFiring` 这类字段只记录当前武器。

实现 `WeaponEffectPluginWithInit` 后，`init(WeaponAPI weapon)` 在脚本实例绑定到武器时调用。实现 `EveryFrameWeaponEffectPluginWithAdvanceAfter` 后，`advance(...)` 在本帧武器转向、开火等操作前调用，`advanceAfter(...)` 在这些操作完成后调用。前后两个回调读取到的弹药和冷却状态可能不同。

```json
{
  "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFlowEffect"
}
```

`advance()` 适合检测开火边沿；`advanceAfter()` 适合读取本帧操作完成后的弹药、冷却和蓄力值。下面的完整类用 `wasFiring` 记录从未开火到开火的变化，并以固定间隔输出操作后的状态。

::: details 完整成品：GearInWeaponFlowEffect

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 武器逐帧效果：只有把本类类名写进 .wpn 的 everyFrameEffect 字段时，
 * 游戏才会为每把武器创建独立实例并回调 init(weapon) / advance / advanceAfter。
 * advance 在本帧转向 / 开火等操作之前被调用，记录开火切换；
 * advanceAfter 在操作完成后按 2 秒节奏打印弹药 / 冷却 / 充能状态。
 */
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

## 配置 `.proj` 的 onFire

`onFireEffect` 写在弹体文件 `.proj` 中。类名加载成功后，射弹生成完成就会调用：

```java
void onFire(DamagingProjectileAPI projectile,
            WeaponAPI weapon,
            CombatEngineAPI engine)
```

`projectile` 是刚生成的弹体，`weapon` 是触发射击的武器，`engine` 是当前战斗引擎。这个入口适合读取弹体规格、来源和伤害，也适合在生成位置添加一次性效果。

```json
{
  "onFireEffect": "data.scripts.gearin.combat.GearInShotOnFireEffect"
}
```

完整例子只消费本次回调提供的对象，不把弹体引用保存到下一次开火：

::: details 完整成品：GearInShotOnFireEffect

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 射弹开火回调：只有把本类类名写进 .proj 的 onFireEffect 字段时才会被调用。
 * onFire 收到的就是刚生成的那一个射弹对象 (fireShot 生成后回调)；
 * 参数都是 "本次事件上下文"，禁止跨发缓存。
 */
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

## 配置 `.proj` 的 onHit

`onHitEffect` 同样写在 `.proj` 中。命中后，游戏把目标、命中点、护盾结果和实际伤害结果一起传入：

```java
void onHit(DamagingProjectileAPI projectile,
           CombatEntityAPI target,
           Vector2f point,
           boolean shieldHit,
           ApplyDamageResultAPI damageResult,
           CombatEngineAPI engine)
```

`target` 是命中的实体，`point` 是命中位置，`shieldHit` 表示命中护盾，`damageResult` 保存护盾、装甲、船体和 EMP 的本次结算结果。`onHitEffect` 适合在命中位置添加效果、区分护盾与船体命中，并记录一次命中日志。

```json
{
  "onHitEffect": "data.scripts.gearin.combat.GearInShotOnHitEffect"
}
```

命中结果直接从当前回调的 `ApplyDamageResultAPI` 读取：

::: details 完整成品：GearInShotOnHitEffect

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

/**
 * 射弹命中回调：只有把本类类名写进 .proj 的 onHitEffect 字段时才会被调用
 * (射弹 / 导弹 / 光束脉冲命中结算时触发)。参数都是 "本次命中上下文"，
 * 禁止跨命中缓存。
 */
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

## 配置 `.wpn` 的 BeamEffect

`beamEffect` 写在光束武器的 `.wpn` 中。光束激活期间，游戏按帧调用：

```java
void advance(float amount,
             CombatEngineAPI engine,
             BeamAPI beam)
```

`BeamAPI` 提供光束起点、终点、长度、伤害和当前帧目标。`didDamageThisFrame()` 与 `getDamageTarget()` 只表示本帧的命中状态；每次回调都重新读取，不能缓存上一帧目标。`amount` 是当前帧经过的时间，可用于控制日志或视觉效果的间隔。

```json
{
  "beamEffect": "data.scripts.gearin.combat.GearInBeamLogger"
}
```

下面的例子每累计约 1 秒记录一次当前帧状态，并在光束末端添加青色光点。光束停止后回调停止，下一次激活会继续创建新的调用周期。

::: details 完整成品：GearInBeamLogger

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

## 核对注册值与类名

四个完整类名分别写入实际使用的 `.wpn` 或 `.proj` 字段：

```json
{
  "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFlowEffect",
  "beamEffect": "data.scripts.gearin.combat.GearInBeamLogger"
}
```

```json
{
  "onFireEffect": "data.scripts.gearin.combat.GearInShotOnFireEffect",
  "onHitEffect": "data.scripts.gearin.combat.GearInShotOnHitEffect"
}
```

`package` 声明、类名、字段值和 JAR 条目必须逐字对应。每种回调都只会在对应对象和对应时机出现：EveryFrame 读取武器持续状态，onFire 读取刚生成的弹体，onHit 读取当前命中上下文，BeamEffect 读取当前激活光束。

## 验证方案

1. 为测试武器的 `.wpn` 写入 `everyFrameEffect`，为光束武器写入 `beamEffect`。
2. 为测试弹体的 `.proj` 写入 `onFireEffect` 和 `onHitEffect`。
3. 构建 JAR，并用 `jar tf` 核对四个 `.class` 条目。
4. 进入包含测试武器的战斗，观察 `GearIn weapon init` 和开火前后日志。
5. 开火并命中目标，核对 `GearIn onFire` 与 `GearIn onHit` 的弹体 ID、目标和伤害结果。
6. 激活光束，核对 `GearIn beam tick`、当前帧命中标记、目标和末端青色光点。
7. 暂停战斗后检查四类回调和计时日志是否停止，恢复后再次观察。
8. 在 `starsector.log` 中搜索四组稳定前缀，并复核 `.wpn`、`.proj` 字段与类名。

预期结果：

- EveryFrame 日志显示初始化、开火边沿以及操作后的弹药、冷却和蓄力；
- onFire 日志显示刚生成的弹体和来源武器；
- onHit 日志显示命中目标、护盾标志和 `ApplyDamageResultAPI` 结果；
- BeamEffect 日志只在光束激活期间出现，并显示当前帧目标和末端光点；
- 暂停期间回调状态冻结，恢复后继续推进。

## 故障分析

- **`.wpn` 或 `.proj` 中没有出现任何对应日志**：字段、文件类型或完整类名不正确时，游戏不会调用脚本。

    - **解决方案**：检查字段名、文件类型和完整类名，确认 JAR 中存在对应的 `.class` 条目。

- **日志出现 `Problem loading class`**：注册值与 Java 类的实际名称不一致。

    - **解决方案**：核对 `package`、类名、目录路径和注册字符串，重新构建 JAR。

- **EveryFrame 只有 `advance` 日志，操作后的状态没有记录**：类没有实现带 `advanceAfter` 的接口，或没有实现对应方法。

    - **解决方案**：让类实现 `EveryFrameWeaponEffectPluginWithAdvanceAfter`，并实现 `advanceAfter(...)`。

- **onFire 或 onHit 没有回调**：脚本类没有绑定到测试弹体实际使用的 `.proj`。

    - **解决方案**：把类名写入 `.proj` 的对应字段，确认测试武器实际使用了该弹体文件。

- **BeamEffect 在非光束武器上没有日志**：`beamEffect` 只对光束武器的激活阶段生效。

    - **解决方案**：只在光束武器的 `.wpn` 中配置 `beamEffect`，并在光束激活期间观察日志。

- **光束日志显示上一帧目标或命中状态**：回调中复用了上一帧的对象或状态。

    - **解决方案**：在每次 `advance(...)` 中重新读取 `didDamageThisFrame()` 和 `getDamageTarget()`，不要跨帧保存对象。

- **暂停后恢复时报告时间偏移**：观察计时器在暂停帧继续累计。

    - **解决方案**：在需要暂停一致性的外层战斗插件中检查 `engine.isPaused()`，只在非暂停帧累计观察时间。

## 总结

`.wpn` 的 `everyFrameEffect` 处理武器级持续回调，`beamEffect` 处理光束激活期间的逐帧状态；`.proj` 的 `onFireEffect` 处理射弹生成，`onHitEffect` 处理当前命中上下文。回调参数只对应当前武器实例、当前射弹事件或当前光束帧，日志和视觉效果可以直接验证字段绑定与调用时机。

## 建议参考项

- `EveryFrameWeaponEffectPlugin`、`OnFireEffectPlugin`、`OnHitEffectPlugin` 与 `BeamEffectPlugin` API；
- `data/weapons/`：原版武器与弹体定义，`everyFrameEffect`、`beamEffect`、`onFireEffect`、`onHitEffect` 注册字段的原版实例；
- `Starsector/starsector-core/starsector.log`：核对回调日志的读取位置。
