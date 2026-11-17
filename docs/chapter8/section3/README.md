# 配置武器脚本

是，我们之前是讲过武器脚本。但那个太简单了。现在我们 Java-ic 一点。

武器脚本实际上是好几种不同脚本的统称，每帧运行、一次性都有。`OnHitEffectPlugin` 已经是老面孔了，而 `OnFireEffectPlugin` 和它自然也很相似。至于最后那个，您大概也对于武器每帧脚本有些猜测了 —— 它实际上就是一个更为微缩化的战斗每帧脚本。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C2-S4] 的都林命中效果 `GearInDurinOnHitEffect` 与弹体文件的 `onHitEffect`；
- [C8-S2] 的来源 ID 与覆盖规则。

## 武器脚本的挂点

数据文件上有三个字段指向脚本类，分别在武器文件和弹体文件里：

| 字段 | 所在文件 | 接口 | 调用时机 |
| --- | --- | --- | --- |
| `everyFrameEffect` | 武器 `.wpn` | `EveryFrameWeaponEffectPlugin` | 每帧一次，在这门炮本帧的操作之前 |
| `onFireEffect` | 弹体 `.proj` | `OnFireEffectPlugin` | 每发弹体生成时 |
| `onHitEffect` | 弹体 `.proj` | `OnHitEffectPlugin` | 每发弹体命中之后 |

接口本身都很小。`EveryFrameWeaponEffectPlugin` 只有一个方法，它在本帧武器转向、开火等操作之前调用：

```java
public interface EveryFrameWeaponEffectPlugin {
	void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon);
}
```

`OnFireEffectPlugin` 与 `OnHitEffectPlugin` 各只有一个方法，参数里没有武器状态，只有这一发弹体：

```java
public interface OnFireEffectPlugin {
	void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine);
}

public interface OnHitEffectPlugin {
	void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit,
			ApplyDamageResultAPI damageResult, CombatEngineAPI engine);
}
```

一门炮把这三个接口全用上也不奇怪。原版现实干扰器的脚本类就同时实现了这三个接口：

```java
public class RealityDisruptorEffect implements OnFireEffectPlugin, OnHitEffectPlugin, EveryFrameWeaponEffectPlugin {
```

本节最要紧的一条规则：**武器的 `everyFrameEffect` 同时实现 `OnFireEffectPlugin` 时，游戏在同一个实例上调用 `onFire(...)`。** 两个接口的方法是同一个对象上的两个方法。

## 实例归谁所有

三个接口的实例来源不一样，这决定了字段能存什么：

| 接口 | 实例数量 | 存活范围 |
| --- | --- | --- |
| `EveryFrameWeaponEffectPlugin` | 每门武器一个 | 从武器创建到被摧毁 |
| `OnFireEffectPlugin`(登记在 `.proj`) | 每种弹体一个 | 整场战斗，这种弹体的全部弹体共用 |
| `OnHitEffectPlugin` | 每种弹体一个 | 整场战斗，这种弹体的全部弹体共用 |

**每门武器一个实例，所以每帧效果可以用字段保存这门炮自己的状态。** 工匠的两门都林各有自己的每帧效果对象，从战斗开始到这门炮被打坏，始终是同一个。

**弹体文件上的效果由这种弹体的全部弹体共用。** 两门都林射出的每一发都调用同一个对象，这个对象拿不到单发弹体的信息。原版的命中效果类因此几乎都不保存字段：共用的对象上存不下单发弹体的信息。

这条差别决定了效果的写法。要在某个位置累积状态，状态必须放在每门炮一个的那个实例上；要处理单发弹体，只能在 `onFire(...)` 或 `onHit(...)` 的参数里动手，不留在字段里。

## 命中回调的时序

`onHit(...)` 的 `damageResult` 是本次命中**已经结算**的结果，不是待修改的输入。`ApplyDamageResultAPI` 上的取值方法读的是算完之后的数字。

**要改一发弹体的伤害，必须在它命中之前，也就是开火时。** 命中回调拿到的伤害已经是定局，那里能做的只有追加新的伤害，改不了这一发本身。

`setDamageAmount(...)` 改的是单发弹体上的数值，不涉及 [C8-S2] 的舰船数值表，因此不需要来源 ID，也不会有多个来源叠在一起的问题。弹体一旦生成，它的伤害就固定下来，飞行途中再改其他东西也不影响它。

## 从弹体找回到武器

共用实例上的 `onHit(...)` 拿到的是弹体，而它需要更新某门炮的状态。这条路只有两步：

```java
WeaponAPI weapon = projectile.getWeapon();
if (weapon != null && weapon.getEffectPlugin() instanceof 效果类) {
    ((效果类) weapon.getEffectPlugin()).doSomething();
}
```

`projectile.getWeapon()` 返回发射这一发弹体的那门炮，那门炮可能为 `null`，例如通过 API 直接生成的弹体。`weapon.getEffectPlugin()` 返回这门炮的 `everyFrameEffect` 实例，也就是那个每门炮只有一个、能保存状态的对象。

转换前用 `instanceof` 判断是必须的：这份弹体可能被别的武器引用，而那门炮上挂的未必是同一个类。少了这一步，遇到别的武器就会抛 `ClassCastException`。

这两步合起来，是 "共用如何回到某一门具体的炮" 的标准解法。任何需要在命中时更新单门武器状态的效果，都走这条路径。

## 为都林加入单门武器的命中叠层

像 "同一门炮连续命中同一个目标时伤害逐层递增、转火或停火超时则归零" 这样的机制，状态只归属于单门武器自身，[C8-S2] 那种挂在船插上对全舰一视同仁的改法就派不上用场了。

都林的基础单发伤害是 35 点。本节为它补上这套叠层机制：同一门都林连续命中同一艘敌舰的船体时，每层使后续弹体伤害提高 10%，最高叠到 5 层；一旦切换目标或超过 2 秒没有再次命中船体，层数立即归零。

按上面的规则分配，三个位置各承担一部分：

- 每帧效果保存这门武器的目标与层数，并负责超时归零；
- 开火效果在弹体生成时，按当前层数提高这一发的伤害；
- 命中效果在命中船体之后，通知发射它的那门武器更新层数。

**层数保存在武器的每帧效果中，而不是舰船或目标上。** 两门都林各自叠层，才符合 "一门武器自己的效果" 这一前提。若保存在目标舰船上，两门都林会共用同一个计数，玩家无法判断哪一门在叠加。

## 武器脚本

`src/data/scripts/gearin/combat/GearInDurinStackEffect.java` 同时实现 `EveryFrameWeaponEffectPlugin` 与 `OnFireEffectPlugin`：

```java
package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 都林的叠层：连续命中同一目标时逐层提高伤害，超时或更换目标后归零。 */
public class GearInDurinStackEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private static final int MAX_STACKS = 5;
    private static final float DAMAGE_PER_STACK = 0.1f;
    private static final float RESET_SECONDS = 2f;

    private ShipAPI target = null;
    private int stacks = 0;
    private float sinceLastHit = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || stacks == 0) {
            return;
        }
        sinceLastHit += amount;
        if (sinceLastHit > RESET_SECONDS) {
            target = null;
            stacks = 0;
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (stacks > 0) {
            projectile.setDamageAmount(projectile.getDamageAmount() * (1f + stacks * DAMAGE_PER_STACK));
        }
    }

    /** 由命中效果在命中船体后调用，更新本武器的层数。 */
    public void registerHit(ShipAPI hit) {
        if (hit == target) {
            stacks = Math.min(MAX_STACKS, stacks + 1);
        } else {
            target = hit;
            stacks = 1;
        }
        sinceLastHit = 0f;
    }
}
```

`advance(...)` 只负责计时。它在每帧的武器操作之前调用。原版的武器效果大多在开头检查 `engine.isPaused()`，这里同样检查，避免暂停期间的时间计入超时。

`onFire(...)` 读取的是这门武器当前的层数，写入的是刚生成的这一发弹体。同一个实例上的两个方法读同一个 `stacks` 字段，这正是前面那条实例规则带来的便利。

`registerHit(...)` 是本类自己的方法，不是接口要求的。命中效果需要通过它通知武器，所以它必须是 `public`。

## 命中效果通知武器

[C2-S4] 的 `GearInDurinOnHitEffect` 在命中船体时按概率追加爆炸。在它的开头加入通知：

```java
WeaponAPI weapon = projectile.getWeapon();
if (!shieldHit && target instanceof ShipAPI && weapon != null
        && weapon.getEffectPlugin() instanceof GearInDurinStackEffect) {
    ((GearInDurinStackEffect) weapon.getEffectPlugin()).registerHit((ShipAPI) target);
}
```

这四个判断各挡一种情况。`!shieldHit` 挡护盾命中，`target instanceof ShipAPI` 挡命中的不是舰船 (例如命中的是导弹线或导弹)，`weapon != null` 挡没有发射者的弹体，`instanceof` 挡别的武器。

护盾命中不计入层数。护盾拦截的是弹体，不是船体，若护盾命中也能叠层，玩家对着护盾就能刷满层数，再转向船体。

`src/data/scripts/gearin/combat/GearInDurinOnHitEffect.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 都林命中船体时通知武器叠层，并按概率追加一次带音效的高爆爆炸。 */
public class GearInDurinOnHitEffect implements OnHitEffectPlugin {

    private static final float CHANCE = 0.25f;
    private static final float BONUS_DAMAGE = 20f;
    private static final Color EXPLOSION_COLOR = new Color(255, 120, 60, 255);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        WeaponAPI weapon = projectile.getWeapon();
        if (!shieldHit && target instanceof ShipAPI && weapon != null
                && weapon.getEffectPlugin() instanceof GearInDurinStackEffect) {
            ((GearInDurinStackEffect) weapon.getEffectPlugin()).registerHit((ShipAPI) target);
        }
        if (shieldHit || !(target instanceof ShipAPI) || Math.random() > CHANCE) {
            return;
        }
        engine.spawnExplosion(point, new Vector2f(), EXPLOSION_COLOR, 40f, 0.3f);
        Global.getSoundPlayer().playSound("GearIn_durin_burst", 1f, 1f, point, target.getVelocity());
        engine.applyDamage(target, point, BONUS_DAMAGE, DamageType.HIGH_EXPLOSIVE, 0f, false, false,
                projectile.getSource());
    }
}
```

追加爆炸的 20 点伤害不随层数提高。它是 [C2-S4] 的独立效果，与叠层互不干扰，走的是命中回调里 `applyDamage(...)` 那条路，不是 `setDamageAmount(...)`。

## 登记脚本

在都林的武器文件 `data/weapons/GearIn_durin.wpn` 中加入 `everyFrameEffect`：

```json
"everyFrameEffect": "data.scripts.gearin.combat.GearInDurinStackEffect"
```

弹体文件 `GearIn_durin_shot.proj` 只保留 [C2-S4] 的 `onHitEffect`，**不登记 `onFireEffect`**。

本节依赖的正是 "同一个实例" 这条规则：`onFire(...)` 与 `advance(...)` 运行在同一个对象上，读的是同一个 `stacks` 字段。若在弹体文件中再登记一次 `onFireEffect`，游戏会为这种弹体另建一个实例，它的 `stacks` 始终为 0，叠层也就永远不会提高伤害。

原版饥渴裂隙 (`assaying_rift`) 也是同样的配置方式：它的武器文件登记了同时实现 `EveryFrameWeaponEffectPlugin` 与 `OnFireEffectPlugin` 的 `AssayingRiftEffect`，而弹体文件里只保留 `onHitEffect`，把 `onFireEffect` 留给武器上的同一个实例处理。

## 核对叠层

层数不显示在界面上，需要一个看得见的标志。临时在 `registerHit(...)` 的末尾加一行浮字，层数变化时显示在目标上方：

```java
Global.getCombatEngine().addFloatingText(hit.getLocation(), "stacks " + stacks, 20f, Color.ORANGE,
        hit, 0f, 0f);
```

这行需要导入 `java.awt.Color` 与 `com.fs.starfarer.api.Global`。它只用于核对，确认效果之后删除，浮字不是这项机制的一部分。

### 进游戏核对

执行 `gradle jar`，重启游戏，进入测试战役，操控工匠射击敌方舰船的船体。

- 连续命中同一艘舰船时，浮字从 `stacks 1` 逐次增加到 `stacks 5`，此后不再增加。
- 停火超过 2 秒再开火，浮字回到 `stacks 1`。
- 转向另一艘舰船，浮字同样回到 `stacks 1`。
- 命中护盾时不出现浮字，随后命中船体时层数不因护盾命中而增加。
- 工匠的两门都林各自计数。一门打满 5 层后，另一门的第一次命中仍显示 `stacks 1`。

确认之后，删除 `registerHit(...)` 中的浮字与对应的 `import`，重新构建。

## 常见问题

- **伤害始终不变**：弹体文件中另外登记了 `onFireEffect`，游戏为它创建了一个层数恒为 0 的新实例。
    - **解决方案**：删除弹体文件中的 `onFireEffect`，只在武器文件中登记 `everyFrameEffect`。

- **两门都林的层数同步增长**：层数保存在了命中效果的字段里。命中效果由全部都林弹体共用。
    - **解决方案**：层数只保存在武器的每帧效果中。

- **暂停一段时间后层数归零**：`advance(...)` 没有检查暂停。
    - **解决方案**：`advance(...)` 开头检查 `engine.isPaused()`。

- **命中时抛出 `ClassCastException`**：直接把 `getEffectPlugin()` 的返回值转换为 `GearInDurinStackEffect`，而命中这一发的不是都林。
    - **解决方案**：转换前用 `instanceof` 判断。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin`：`advance(...)` 的调用时机。
- `com.fs.starfarer.api.combat.OnFireEffectPlugin`：弹体生成时的回调参数。
- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：命中时的回调参数与 `damageResult`。
- `com.fs.starfarer.api.combat.DamagingProjectileAPI`：`setDamageAmount(...)` 与 `getWeapon()`。
- `com.fs.starfarer.api.combat.WeaponAPI`：`getEffectPlugin()` 返回武器的 `everyFrameEffect` 实例。
- `com.fs.starfarer.api.impl.combat.RealityDisruptorEffect`：原版现实干扰器，一个类同时实现三个接口，每帧效果与开火效果是同一个实例。
