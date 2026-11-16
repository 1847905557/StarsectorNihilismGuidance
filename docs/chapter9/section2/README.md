# 详解战术系统

战术系统的效果反而没有太多要补充的，但它的 `.system` 文件反而会引出几个不太常见但复杂的变化。除此之外，战术系统的 AI 则是另一个大难点，毕竟一方面这是作者最可能接触到 AI 的位置，而另一方面一个表现不好的 AI 总是比表现好的更令人印象深刻。

扯远了。我们先从基础重新开始。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C2-S3] 的 `.system` 文件、`ship_systems.csv` 与 `BaseShipSystemScript`；
- [C8-S2] 的修饰合并规则与来源 ID；
- [C4-S4] 的窑与模块。

## 战术系统的接口分工

战术系统在代码侧由三个不同的接口拼起来，名字相近，管的事完全不同：

| 接口 | 所在包 | 管什么 |
| --- | --- | --- |
| `ShipSystemStatsScript` | `com.fs.starfarer.api.plugins` | 效果本身：`apply` / `unapply` / `getStatusData`，附带 `State` 枚举与 `StatusData` 类 |
| `ShipSystemStatsScriptAdvanced` | 同上 | 在上一项基础上加 `isUsable(...)` 与 `getInfoText(...)` |
| `ShipSystemAIScript` | `com.fs.starfarer.api.combat` | AI 什么时候按这个键 |

**没有名为 `ShipSystemScript` 的接口。** 三个名字都在真源码里，按前两个名字去搜才有结果。`BaseShipSystemScript` 在 `impl.combat` 下，实现了 `ShipSystemStatsScriptAdvanced`，[C2-S3] 继承的就是它。

效果与 AI 是两个独立的类，由 `.system` 的两个字段分别指向：

```json
"statsScript":"data.scripts.gearin.shipsystems.GearInRangeSystem",
"aiType":"CUSTOM",
"aiScript":"data.scripts.gearin.shipsystems.GearInRangeSystemAI",
```

`aiType` 是内置 AI 的类型名，`aiScript` 是自定义 AI 的类名。原版只有 5 个系统用 `aiScript`，其余靠 `aiType` 挑一个内置策略。**原版没有任何类同时继承 `BaseShipSystemScript` 又实现 `ShipAIPlugin`**：`ShipAIPlugin` 是整船 AI，与系统无关。系统脚本与系统 AI 之间要传参数，靠自建的旁路接口。

`ShipSystemAIScript` 只有两个方法：

```java
public interface ShipSystemAIScript {
	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine);
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target);
}
```

`init(...)` 里有一个坑：不能在 `init` 里取系统脚本，那会触发类加载顺序依赖，游戏启动时直接崩。原版 `ConstructionSwarmSystemAI` 就栽在这里。要拿脚本参数，等第一次 `advance(...)` 之后再取。

## 范围增幅的设计

范围增幅的效果是：

- 实弹武器射程提高 30%；
- 最大转向速度与转向加速度降低 50%，作为代价；
- 效果同时作用于窑的模块；
- 可使用 2 次，每 25 秒恢复 1 次。

窑本体与模块都装有实弹武器。系统脚本只拿到窑本体的数值对象，模块要单独处理，这是本节第一个要解决的问题。

AI 的使用时机也有明确的答案：**目标刚好在射程之外，增幅之后能够到。** 目标已经在射程内时使用，只换来转向变慢；目标远在增幅后的射程之外时使用，次数白白耗掉。原版没有按这个条件判断的 `aiType`，需要自己编写。

## 战术系统的状态与效果强度

系统从激活到恢复要经过几个状态，`apply(...)` 的参数 `state` 告诉脚本当前处于哪一个：

| 状态       | 含义                         | 对应 `ship_systems.csv` 的列 |
| ---------- | ---------------------------- | ---------------------------- |
| `IN`       | 充能，效果逐渐增强           | `charge up`                  |
| `ACTIVE`   | 效果保持最强                 | `active`                     |
| `OUT`      | 效果逐渐减弱                 | `down`                       |
| `COOLDOWN` | 冷却，不能再次使用           | `cooldown`                   |
| `IDLE`     | 待机，可以使用               | 无                           |

另一个参数 `effectLevel` 是效果强度，在 `IN` 期间从 0 升到 1，`ACTIVE` 期间为 1，`OUT` 期间从 1 降回 0。

`apply(...)` 的调用条件只有一句：**这个系统的效果值大于 0，或者写了 `runScriptWhileIdle`。** 状态在这里只用来算出效果值。`IN`、`ACTIVE`、`OUT` 期间效果值都大于 0，所以这期间每帧调用；`COOLDOWN` 与 `IDLE` 的效果值为 0，默认不调。

`.system` 里加一句 `"runScriptWhileIdle": true`，这条条件就一直成立，`COOLDOWN` 与 `IDLE` 期间 `apply(...)` 照样每帧调用。系统的状态因此不再等于效果在不在，脚本要在 `apply(...)` 里自己看 `state` 分支：激活的那几段写主动效果，其余状态写被动效果。原版激光雷达阵列这么做，系统没开的时候舰船仍有 35% 的武器射程加成。

这个开关还有两个连带后果。`unapply(...)` 仍会在效果结束时调一次，但舰队撤退、系统正处于活动状态时，引擎会另走一条路径再调一次，所以别把它当成 "只执行一次" 的地方写收尾逻辑。另外 `apply(...)` 现在会在待机期每帧运行，里面那些每帧都做的计算也就跟到了待机期，该省的还得省。

因此**修饰的数值应当乘上 `effectLevel`**。这样效果会随充能和消退平滑变化，而不是在某一帧突然出现或消失。

## 修改本体与模块的数值

`apply(...)` 收到的 `stats` 属于窑本体。要找到模块，先从 `stats.getEntity()` 取得舰船对象，再遍历 `getChildModulesCopy()`：

```java
@Override
public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    applyTo(stats, id, effectLevel);
    if (stats.getEntity() instanceof ShipAPI ship) {
        for (ShipAPI module : ship.getChildModulesCopy()) {
            applyTo(module.getMutableStats(), id, effectLevel);
        }
    }
}

private static void applyTo(MutableShipStatsAPI stats, String id, float effectLevel) {
    stats.getBallisticWeaponRangeBonus().modifyMult(id, 1f + RANGE_BONUS * effectLevel);
    stats.getMaxTurnRate().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
    stats.getTurnAcceleration().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
}
```

`RANGE_BONUS` 为 `0.3f`，`TURN_PENALTY` 为 `0.5f`。本体与模块使用同一个来源 ID，`unapply(...)` 以同样的方式遍历，逐个调用 `unmodify(id)`。

`stats.getEntity()` 在战斗外可能为 `null`，`instanceof` 的判断同时排除了这种情况。

::: tip 为什么射程用 `modifyMult`

[C8-S2] 介绍过，percent 相加，mult 相乘。射程加成用乘数，增幅就作用在其他船插与技能的加成之上：窑装了原版目标定位系统之后，增幅仍然在已经提高的射程上再提高 30%。

这是有意为之的取舍。如果希望增幅与其他射程加成相加，改用 `modifyPercent(id, 30f * effectLevel)`。

:::

模块的转向数值也被修改了，但模块固定在本体上，自身不转向。这两行对模块没有实际作用，保留它们是为了让本体与模块共用 `applyTo(...)`，不必再写一个只改射程的版本。

## 在状态栏显示效果

玩家驾驶的舰船使用系统时，屏幕左下方的状态栏会列出系统效果。条目由 `getStatusData(...)` 提供：

```java
@Override
public StatusData getStatusData(int index, State state, float effectLevel) {
    if (index == 0) {
        int range = Math.round(RANGE_BONUS * effectLevel * 100f);
        return new StatusData("ballistic range +" + range + "%", false);
    }
    if (index == 1) {
        int turn = Math.round(TURN_PENALTY * effectLevel * 100f);
        return new StatusData("turn rate -" + turn + "%", true);
    }
    return null;
}
```

游戏从 `index` 为 0 开始依次调用，直到返回 `null` 为止。`StatusData` 的第二个参数表示这一条是否为减益，为 `true` 时以警示色显示。

条目文字用英文，与 [C2-S3] 和原版一致。数值同样乘上 `effectLevel`，充能和消退期间，玩家看到的是当前的实际数值。

## 判断系统能否使用

窑的实弹武器全部永久损毁之后，范围增幅已经没有意义，不应再允许使用。`isUsable(...)` 返回 `false` 时，玩家按键无效，AI 调用 `useSystem()` 也不会激活：

```java
@Override
public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
    return hasRangeWeapon(ship);
}

public static boolean isRangeWeapon(WeaponAPI weapon) {
    return weapon.getType() == WeaponType.BALLISTIC
            && !weapon.isDecorative()
            && !weapon.isPermanentlyDisabled()
            && !weapon.hasAIHint(AIHints.PD);
}
```

`isPermanentlyDisabled()` 只对永久损毁的武器返回 `true`。暂时被打瘫、稍后会自行修复的武器仍然算作可用，否则系统会在战斗中频繁地在可用与不可用之间切换。

点防御武器也被排除在外。它们的目标是导弹和战机，射程提高对它们作用很小，不该成为使用系统的理由。

`isRangeWeapon(...)` 声明为 `public static`，下文的 AI 会用同一个标准挑选武器。**系统认为哪些武器有用，AI 就按哪些武器判断**，两处标准不一致时，AI 会在系统不可用的时候反复尝试使用它。

### 说明不能使用的原因

舰船信息中，系统名称旁可以显示一段简短的说明，由 `getInfoText(...)` 提供：

```java
@Override
public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
    if (!system.isActive() && !hasRangeWeapon(ship)) {
        return "NO BALLISTICS";
    }
    return null;
}
```

返回 `null` 时，游戏显示默认的状态文字，例如剩余次数或冷却进度。

## 为系统编写 AI

`.system` 文件的 `aiType` 写成 `CUSTOM`，再用 `aiScript` 指定一个实现 `ShipSystemAIScript` 的类，游戏就会把这个系统的使用决定交给它：

```json
"aiType": "CUSTOM",
"aiScript": "data.scripts.gearin.shipsystems.GearInRangeSystemAI",
```

接口只有两个方法：

- `init(...)` 在舰船进入战斗时调用一次，传入舰船、系统、AI 标记与战斗引擎；
- `advance(...)` 在舰船由 AI 控制时每帧调用，最后一个参数 `target` 是 AI 当前选定的目标，可能为 `null`。

与船插不同，**AI 脚本为每艘舰船单独创建一个实例**，字段中可以保存这艘舰船的数据。原版的 `DarkenedGazeSystemAI` 就是这样写的。

### 降低判断频率

AI 不需要每帧都做决定。用 `IntervalUtil` 把判断间隔控制在 0.25 至 0.35 秒之间：

```java
private final IntervalUtil interval = new IntervalUtil(0.25f, 0.35f);

@Override
public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    interval.randomize();
}
```

`IntervalUtil` 每次在给定范围内随机取一个间隔，`randomize()` 再把初始进度打乱。一支舰队里同时开战的十几艘窑，不会在同一帧集中计算。

### 排除不该使用的情况

每到判断时机，先排除不能使用或不值得使用的情况：

```java
if (system.isActive() || system.getCooldownRemaining() > 0f || system.isOutOfAmmo()) {
    return;
}
if (ship.getFluxTracker().isOverloadedOrVenting() || ship.getFluxLevel() > MAX_FLUX_LEVEL) {
    return;
}
if (target == null || !target.isAlive() || target.isHulk()) {
    return;
}
```

`MAX_FLUX_LEVEL` 为 `0.85f`。幅能接近上限的舰船很快就要后撤或主动散幅，此时增加射程也打不出几发。

这几项检查都很廉价，放在前面，后面遍历武器的计算就只在必要时执行。

### 判断增幅能否带来收益

核心判断只有一句：**有没有武器现在够不到目标，增幅之后能够到。**

```java
private int countWeaponsBroughtIntoRange(ShipAPI target) {
    int count = 0;
    for (WeaponAPI weapon : ship.getAllWeapons()) {
        if (!GearInRangeSystem.isRangeWeapon(weapon)) {
            continue;
        }
        float distance = Misc.getDistance(weapon.getLocation(), target.getLocation())
                - Misc.getTargetingRadius(weapon.getLocation(), target, false);
        float range = weapon.getRange();
        if (distance > range && distance <= range * (1f + GearInRangeSystem.RANGE_BONUS)) {
            count++;
        }
    }
    return count;
}
```

距离从武器位置算起，减去目标的瞄准半径。`Misc.getTargetingRadius(...)` 返回从给定方向看过去目标碰撞边界的半径，原版 `DarkenedGazeSystemAI` 判断射程时也这样计算。直接用两船中心的距离，目标越大，判断出的距离就越偏远。

`weapon.getRange()` 是当前的实际射程，已经包含其他船插与技能的加成。系统激活后它会变大，但这时 `system.isActive()` 已经让判断提前返回，不会重复计算。

计数大于 0 就调用 `ship.useSystem()`。

::: tip 阈值是取舍，不是定论

"至少 1 门武器" 是最宽松的条件，窑往往在接近目标的途中就用掉一次，换来比对手更早开火。要求至少 2 门，AI 会更保守，次数更多地留到正面交火时。两种都说得通，取决于这个系统在设计上偏向抢先手还是打持久战。

:::

## 待机时也运行的脚本

`.system` 文件中加入 `"runScriptWhileIdle": true`，`apply(...)` 在 `COOLDOWN` 与 `IDLE` 期间也会每帧调用。原版激光雷达阵列用它实现被动效果：系统没有激活时，舰船仍有 35% 的武器射程加成。

开启后有两点需要注意：

- **`unapply(...)` 不再被调用。** 原版的 `.system` 文件里普遍写着这一项不生效。激活结束后的清理，要在 `apply(...)` 中根据 `state` 自己完成；
- `apply(...)` 中的效果要按 `state` 分支，否则激活期间的效果会一直存在。

范围增幅没有被动效果，不开启这一项。需要被动效果时，参考原版 `LidarArrayStats` 的写法：在 `apply(...)` 中判断 `state` 是否为 `IN`、`ACTIVE` 或 `OUT`，激活时写入主动效果，其余状态写入被动效果。

## 完整的范围增幅

`src/data/scripts/gearin/shipsystems/GearInRangeSystem.java`：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

/** 范围增幅：实弹武器射程提高，代价是转向变慢；效果同步到模块。 */
public class GearInRangeSystem extends BaseShipSystemScript {

    /** 满效果时的实弹射程加成，0.3 即 30%。 */
    public static final float RANGE_BONUS = 0.3f;

    /** 满效果时的转向减益，0.5 即 50%。 */
    public static final float TURN_PENALTY = 0.5f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        applyTo(stats, id, effectLevel);
        if (stats.getEntity() instanceof ShipAPI ship) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                applyTo(module.getMutableStats(), id, effectLevel);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        unapplyFrom(stats, id);
        if (stats.getEntity() instanceof ShipAPI ship) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                unapplyFrom(module.getMutableStats(), id);
            }
        }
    }

    private static void applyTo(MutableShipStatsAPI stats, String id, float effectLevel) {
        stats.getBallisticWeaponRangeBonus().modifyMult(id, 1f + RANGE_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
        stats.getTurnAcceleration().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
    }

    private static void unapplyFrom(MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            int range = Math.round(RANGE_BONUS * effectLevel * 100f);
            return new StatusData("ballistic range +" + range + "%", false);
        }
        if (index == 1) {
            int turn = Math.round(TURN_PENALTY * effectLevel * 100f);
            return new StatusData("turn rate -" + turn + "%", true);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return hasRangeWeapon(ship);
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (!system.isActive() && !hasRangeWeapon(ship)) {
            return "NO BALLISTICS";
        }
        return null;
    }

    /** 能从本系统获益的武器：未永久损毁、不以点防御为主的实弹武器。 */
    public static boolean isRangeWeapon(WeaponAPI weapon) {
        return weapon.getType() == WeaponType.BALLISTIC
                && !weapon.isDecorative()
                && !weapon.isPermanentlyDisabled()
                && !weapon.hasAIHint(AIHints.PD);
    }

    private static boolean hasRangeWeapon(ShipAPI ship) {
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (isRangeWeapon(weapon)) {
                return true;
            }
        }
        return false;
    }
}
```

`src/data/scripts/gearin/shipsystems/GearInRangeSystemAI.java`：

```java
package data.scripts.gearin.shipsystems;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** 范围增幅的 AI：目标刚好在实弹武器射程之外、增幅后能够到时才使用。 */
public class GearInRangeSystemAI implements ShipSystemAIScript {

    /** 幅能高于这个比例时不使用，舰船很快就要后撤或散幅。 */
    private static final float MAX_FLUX_LEVEL = 0.85f;

    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.35f);

    private ShipAPI ship;
    private ShipSystemAPI system;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        interval.randomize();
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        if (system.isActive() || system.getCooldownRemaining() > 0f || system.isOutOfAmmo()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting() || ship.getFluxLevel() > MAX_FLUX_LEVEL) {
            return;
        }
        if (target == null || !target.isAlive() || target.isHulk()) {
            return;
        }
        if (countWeaponsBroughtIntoRange(target) > 0) {
            ship.useSystem();
        }
    }

    /** 统计现在够不到目标、增幅后能够到的武器数量。 */
    private int countWeaponsBroughtIntoRange(ShipAPI target) {
        int count = 0;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!GearInRangeSystem.isRangeWeapon(weapon)) {
                continue;
            }
            float distance = Misc.getDistance(weapon.getLocation(), target.getLocation())
                    - Misc.getTargetingRadius(weapon.getLocation(), target, false);
            float range = weapon.getRange();
            if (distance > range && distance <= range * (1f + GearInRangeSystem.RANGE_BONUS)) {
                count++;
            }
        }
        return count;
    }
}
```

## 登记战术系统

`data/shipsystems/GearIn_range_system.system`：

```json
{
    "id": "GearIn_range_system",
    "type": "STAT_MOD",
    "aiType": "CUSTOM",
    "aiScript": "data.scripts.gearin.shipsystems.GearInRangeSystemAI",
    "statsScript": "data.scripts.gearin.shipsystems.GearInRangeSystem",
    "weaponGlowColor": [255, 170, 60, 155],
    "weaponTypes": ["BALLISTIC"],
    "useSound": "system_targeting_feed",
    "loopSound": "system_targeting_feed_loop",
    "outOfUsesSound": "gun_out_of_ammo"
}
```

`useSound` 与 `loopSound` 借用原版目标馈送系统的音效。

`data/shipsystems/ship_systems.csv` 新增一行：

```csv
范围增幅,GearIn_range_system,,,,,,,,2,0.04,1,6,1,4,,,,,,,,,,,,offensive,graphics/icons/hullsys/lidar_barrage.png
```

`max uses` 为 `2`，`regen` 为 `0.04`，即每秒恢复 0.04 次，25 秒恢复 1 次。充能 1 秒、持续 6 秒、消退 1 秒，两次使用之间至少冷却 4 秒。图标借用原版激光雷达阵列的 `lidar_barrage.png`。

`data/strings/descriptions.csv` 新增系统说明：

```csv
GearIn_range_system,SHIP_SYSTEM,"窑的火控增幅系统。将测距与弹道解算功率集中给实弹武器，射程随之延伸，代价是解算期间舰船转向迟缓。效果同样作用于窑的模块。",进攻,"提高实弹武器射程，但降低舰船转向能力。",射程|转向,"实弹武器射程提高 {{30%}}，最大转向速度与转向加速度降低 {{50%}}。",
```

最后在 `data/hulls/ship_data.csv` 中，把窑的 `system id` 从 `GearIn_assaultfeed` 改为 `GearIn_range_system`。工匠仍然使用突击填弹器。

### 进游戏核对

执行 `gradle jar`，重启游戏，在模拟战中驾驶窑。

- 使用系统，状态栏出现 "ballistic range +30%" 与 "turn rate -50%" 两条，后者以警示色显示。数值在充能和消退期间逐渐变化。
- 激活期间，本体与模块上的都林自动炮射程指示圈都变大。
- 系统的次数显示为 2，用完后约 25 秒恢复 1 次。
- 切换为 AI 控制，让窑与一艘原版巡洋舰交战。窑在接近途中、目标刚好处于射程之外时使用系统，进入近距离交火后不再使用。

## 常见问题

- **模块的射程没有变化**：只修改了 `apply(...)` 收到的 `stats`。
    - **解决方案**：通过 `stats.getEntity()` 取得舰船，遍历 `getChildModulesCopy()`，对每个模块的 `getMutableStats()` 写入同样的修饰。

- **系统结束后转向仍然很慢**：`unapply(...)` 没有遍历模块，或者开启了 `runScriptWhileIdle`。
    - **解决方案**：`unapply(...)` 与 `apply(...)` 遍历相同的对象；开启 `runScriptWhileIdle` 时，在 `apply(...)` 中按 `state` 自行清理。

- **AI 从不使用系统**：`aiType` 不是 `CUSTOM`，或者 `aiScript` 的类名写错。
    - **解决方案**：两项都检查。类名必须写完整的包路径，与 `statsScript` 的写法相同。

- **AI 一进入战斗就用掉全部次数**：判断时没有排除已在射程内的武器，或者用了两船中心的距离。
    - **解决方案**：只统计 "现在够不到、增幅后能够到" 的武器，距离减去目标的瞄准半径。

- **武器全部损毁后仍能使用**：`isUsable(...)` 没有覆盖，或者检查时包含了装饰武器。
    - **解决方案**：在 `isUsable(...)` 中调用 `isRangeWeapon(...)` 的判断，排除装饰武器与永久损毁的武器。

## 建议参考项

- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：`State`、`StatusData` 与各个覆盖方法的定义。
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：全部覆盖方法的默认返回值。
- `com.fs.starfarer.api.combat.ShipSystemAIScript`：自定义系统 AI 的接口。
- `com.fs.starfarer.api.impl.combat.dweller.DarkenedGazeSystemAI`：原版自定义系统 AI，包含射程判断与 `IntervalUtil` 的用法。
- `com.fs.starfarer.api.impl.combat.LidarArrayStats`：原版激光雷达阵列，`runScriptWhileIdle` 与被动射程加成的写法。
