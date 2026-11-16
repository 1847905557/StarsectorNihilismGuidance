# 配置战斗监听器

编者上一次听到 回调 这两个字的时候，还以为是什么新兴的 AI 语，但实际上它是 Callback 的中文名。真是不常见。

战斗监听器就是各类回调的总和。虽然严格来说更像是 Hook，甚至还混了些每帧脚本进去，但姑且这么理解也没问题。对于大多数你不希望每帧运行而导致额外性能开销的情况、或者需要某些精确结算时点的情况，那么就该选择战斗监听器。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C8-S2] 的船插写法、来源 ID 与 `modifyPercent(...)`；
- [C8-S3] 中 `projectile.getWeapon()` 取得发射武器的写法。

## 监听器接口的分类

`com.fs.starfarer.api.combat.listeners` 下是一组按结算位置分工的接口：

| 用途 | 接口 | 回调时机 |
| --- | --- | --- |
| 改伤害 | `DamageDealtModifier`、`DamageTakenModifier` | 伤害结算之前 |
| 收通知 | `DamageListener`、`HullDamageAboutToBeTakenListener` | 伤害结算之后、或船体受损之前 |
| 改武器属性 | `WeaponRangeModifier`、`WeaponBaseRangeModifier` | 计算射程时 |
| 改装配点 | `WeaponOPCostModifier`、`FighterOPCostModifier` | 计算装配点数时 |
| 其它钩子 | `AdvanceableListener`、`ShipRenderListener`、`FleetMemberDeploymentListener` | 各自对应的事件 |

两个改伤害的接口方向相反，区别在于挂到哪一方：

```java
/**
 * When adding to a ship, add to the ship dealing the damage, NOT the one taking the damage.
 *
 * Should add this to a ship where possible instead of the engine as performance will be better.
 */
public interface DamageDealtModifier {
```

`DamageTakenModifier` 是同一句话的镜像：挂到受到伤害的一方。挂反了，效果就作用在错误的一方，而且不会报错。

## 能挂在哪里

监听器不是随便挂的，每个接口只认特定的宿主：

| 接口 | 宿主规则 | 宿主 |
| --- | --- | --- |
| `AdvanceableListener` | 只能挂在 `ShipAPI` 上，挂到 `CombatEngineAPI` 不生效 | 舰船 |
| `WeaponOPCostModifier`、`FighterOPCostModifier` | 只有挂在 `MutableShipStatsAPI` 上才算数，挂到 `ShipAPI` 或战斗引擎都不行 | 舰船数值表 |
| `FleetMemberDeploymentListener` | 只能挂到战斗引擎上，挂到具体舰船什么也不做 | 引擎 |
| `WeaponRangeModifier`、`WeaponBaseRangeModifier` | 只能挂在舰船上，出于性能考虑不挂引擎 | 舰船 |
| `DamageDealtModifier`、`DamageTakenModifier`、`HullDamageAboutToBeTakenListener` | 尽量挂在舰船上而不是引擎上，性能更好 | 舰船优先 |

**挂在错误的宿主上不会报错，只是不生效。** 射程监听器挂到引擎上，射程一点变化都没有，日志也是干净的。

`ShipAPI` 上有一整套管理方法，`addListener(Object)` 是唯一的添加形式，参数是 `Object`，不按接口类型约束：

```java
CombatListenerManagerAPI getListenerManager();

void addListener(Object listener);
void removeListener(Object listener);
void removeListenerOfClass(Class<?> c);
boolean hasListener(Object listener);
boolean hasListenerOfClass(Class<?> c);
<T> List<T> getListeners(Class<T> c);
```

一个监听器都没有时，`getListenerManager()` 返回 `null`。

`CombatEngineAPI` 上**没有** `addListener(...)`，只有 `getListenerManager()`。要往引擎挂监听器，得写 `Global.getCombatEngine().getListenerManager().addListener(...)`。

## 改伤害

`DamageDealtModifier` 只有一个方法：

```java
String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
        Vector2f point, boolean shieldHit);
```

`param` 是造成伤害的对象，有四种可能：`null`、`DamagingProjectileAPI`、`BeamAPI`、`EmpArcEntityAPI`。脚本也可以传入自己造的对象。

要知道是哪门武器造成的伤害，得先判断 `param` 的类型再取武器。`null` 是真实存在的情况，电弧与脚本自定义的对象也各有各的类型，所以转换前必须判断。

`damage.getModifier()` 返回一个 [C8-S2] 介绍过的 `MutableStat`，写法与舰船数值完全相同：同一个来源 ID、同一类修饰覆盖，换一段话就是同一套规则。

**返回值是 `String`，不是伤害数字。** 它返回施加在 `damage.getModifier()` 上的那个修饰的来源 ID，没有改动时返回 `null`。这不是一个随手填的占位符：`CombatListenerUtil` 会把它收集进一个列表，用来说明这一发伤害被哪些来源改过。

伤害走 `damage.getModifier()` 修改，不要直接改数值。原版自己也并不总是这么做：`SquallOnFireEffect` 的 `modifyDamageDealt(...)` 直接用 `damage.setDamage(damage.getBaseDamage() + DAMAGE)`。这是原版的写法，不是建议照抄的做法：走 `getModifier()` 的伤害在装配界面与提示里还能被其它系统看到，直接 `setDamage` 只在这一次结算里成立。

## 改射程

`WeaponRangeModifier` 有三个方法，对应 [C8-S2] 合并规则中的三种修饰：

```java
float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon);
float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon);
float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon);
```

中性值能从游戏的聚合方式反推出来，见 `CombatListenerUtil`：

```java
	public static float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
		float mod = 0f;
		if (ship != null) {
			for (WeaponRangeModifier x : new ArrayList<WeaponRangeModifier>(ship.getListeners(WeaponRangeModifier.class))) {
				mod += x.getWeaponRangePercentMod(ship, weapon);
			}
		}
		return mod;
	}

	public static float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
		float mod = 1f;
		if (ship != null && ship.getListenerManager() != null) {
			for (WeaponRangeModifier x : new ArrayList<WeaponRangeModifier>(ship.getListeners(WeaponRangeModifier.class))) {
				mod *= x.getWeaponRangeMultMod(ship, weapon);
			}
		}
		return mod;
	}
```

percent 与 flat 从 `0f` 起累加，mult 从 `1f` 起累乘。**所以乘数的中性值是 `1`，另外两个是 `0`。** 乘数方法误返回 `0`，这门武器的射程会变成零，而且不会有任何报错。

这些方法都收到 `weapon` 参数，监听器因此可以只改某一类武器。游戏计算射程的频率很高，这些方法里只该做简单判断，不该创建对象或写日志。

`WeaponBaseRangeModifier` 的方法同名同义，改的是武器的基础射程，原版的高散射增幅器用它按武器原始射程算惩罚。要改最终射程，用 `WeaponRangeModifier`。

## 注册时机

`HullModEffect` 的两个创建期钩子分工不同：`applyEffectsAfterShipCreation(...)` 拿得到 `ShipAPI`，可以挂监听器，但生涯界面不调用它，所以不该在这里改数值。

[C8-S2] 已经说明 `applyEffectsBeforeShipCreation(...)` 改的是会反映到装配界面上的那一层。

**监听器挂在 `applyEffectsAfterShipCreation(...)` 里。** 这是唯一一个既有 `ShipAPI` 又保证被调用的位置。

有一件事关系到所有从这里出发的状态：效果类在整个应用里只实例化一次，字段在所有舰船、所有存档之间共用，要存数据得用 `SectorAPI.getPersistentData()`。监听器实例则是每艘船挂一个新的，那个对象才是保存单船数据的地方。

## 破甲校准

前面讲的是这一套机制的通用规则。现在给一个具体的用场：破甲校准，它只影响舰船上的小型实弹武器，命中装甲与船体时伤害提高 15%，命中护盾时不变，同时射程降低 15%。

两项效果分别由两个监听器接口实现，写在同一个类里，由船插在舰船创建后挂上。看炉人装有四门火神炮与两门都林，全部是小型实弹武器，本节把这个船插加到它的战斗装配上。

**这个船插把小型实弹武器推向近距离破甲。** 射程降低迫使舰船贴近目标，而伤害加成只在护盾失效之后生效。它与 [C8-S2] 的过热冶炉方向一致：齿轮工业的舰船以承受压力换取输出，玩家需要判断何时冒险贴近。

## 完整船插

`src/data/scripts/gearin/hullmods/GearInBreachCalibrationHullMod.java`：

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;

import org.lwjgl.util.vector.Vector2f;

/** 破甲校准：小型实弹武器命中装甲与船体时伤害提高，射程降低。 */
public class GearInBreachCalibrationHullMod extends BaseHullMod {

    public static final String HULLMOD_ID = "GearIn_breach_calibration";

    private static final float DAMAGE_BONUS = 15f;
    private static final float RANGE_PENALTY = 15f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.addListener(new BreachCalibrationListener());
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) DAMAGE_BONUS + "%";
        }
        if (index == 1) {
            return (int) RANGE_PENALTY + "%";
        }
        return null;
    }

    private static boolean isAffected(WeaponAPI weapon) {
        return weapon != null && weapon.getSize() == WeaponSize.SMALL
                && weapon.getType() == WeaponType.BALLISTIC;
    }

    /** 同时修改伤害与射程的监听器，每艘舰船一个实例。 */
    public static class BreachCalibrationListener implements DamageDealtModifier, WeaponRangeModifier {

        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                Vector2f point, boolean shieldHit) {
            if (shieldHit) {
                return null;
            }
            WeaponAPI weapon = null;
            if (param instanceof DamagingProjectileAPI) {
                weapon = ((DamagingProjectileAPI) param).getWeapon();
            } else if (param instanceof BeamAPI) {
                weapon = ((BeamAPI) param).getWeapon();
            }
            if (!isAffected(weapon)) {
                return null;
            }
            damage.getModifier().modifyPercent(HULLMOD_ID, DAMAGE_BONUS);
            return HULLMOD_ID;
        }

        @Override
        public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f;
        }

        @Override
        public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return isAffected(weapon) ? 1f - RANGE_PENALTY / 100f : 1f;
        }

        @Override
        public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f;
        }
    }
}
```

**监听器不保存状态。** 它只根据参数作出判断，所以每艘舰船挂一个新实例即可，不需要检查是否已经挂过。若监听器需要记住某艘舰船的数据，应当在构造时传入这艘舰船，并保存在实例字段里，原版的高散射增幅器就是这样写的。

`isAffected(...)` 是两个接口共用的判断，写成静态方法放在船插类里。`modifyDamageDealt(...)` 中的 `MissileAPI` 也继承自 `DamagingProjectileAPI`，导弹因此会走第一个分支，但导弹的武器类型不是实弹，判断会将它排除。

## 登记船插

`data/hullmods/hull_mods.csv` 新增一行：

```csv
破甲校准,GearIn_breach_calibration,0,,,"offensive, merc",武器,3000,TRUE,,,3,6,9,15,data.scripts.gearin.hullmods.GearInBreachCalibrationHullMod,"小型实弹武器命中装甲与船体时伤害提高 {%s}，射程降低 {%s}。",小型实弹武器近距破甲。,,graphics/hullmods/ballistics_integration.png
```

图标暂时借用原版的 `ballistics_integration.png`。在 `data/variants/GearIn_furnaceman_combat.variant` 的 `hullMods` 中加入这个船插：

```json
"hullMods": ["expanded_deck_crew", "GearIn_breach_calibration"],
```

看炉人的装配点数上限为 75，原有装配占用 61 点，加入这个船插的 6 点后为 67 点。

### 进游戏核对

执行 `gradle jar`，重启游戏。

- 在装配界面查看看炉人，火神炮与都林的射程比原值短 15%。火神炮原射程 250，此时为 212 左右。
- 进入测试战役，用看炉人攻击护盾已经崩溃的敌舰，火神炮在船体上显示的伤害数字大于未装此船插时的数字。
- 攻击护盾时，护盾上显示的伤害数字不变。

对比时，可以先去掉变体文件中的船插构建一次，记下数字，再加回来。

## 常见问题

- **所有武器射程变为零**：`getWeaponRangeMultMod(...)` 对不受影响的武器返回了 `0`。
    - **解决方案**：乘数方法的中性值为 `1f`。

- **射程没有变化，也没有报错**：监听器挂在了战斗引擎的 `getListenerManager()` 上。
    - **解决方案**：射程接口只在挂在舰船上时生效，改用 `ship.addListener(...)`。

- **伤害加成作用在了受到攻击的一方**：误用了 `DamageTakenModifier`。
    - **解决方案**：修改己方造成的伤害使用 `DamageDealtModifier`。

- **战斗中命中时报 `ClassCastException`**：`param` 没有判断类型就直接转换。
    - **解决方案**：`param` 可能为 `null` 或电弧等其他对象，转换前用 `instanceof` 判断。

## 建议参考项

- `com.fs.starfarer.api.combat.listeners.DamageDealtModifier`：`param` 的可能类型、`getModifier()` 的使用要求与返回值含义。
- `com.fs.starfarer.api.combat.listeners.WeaponRangeModifier`：射程修改，只在挂在舰船上时生效。
- `com.fs.starfarer.api.combat.listeners.CombatListenerUtil`：游戏如何合并多个射程监听器的返回值。
- `com.fs.starfarer.api.impl.hullmods.PDIntegration`：从 `param` 取得武器，再按武器判断伤害加成。
- `com.fs.starfarer.api.impl.hullmods.HighScatterAmp`：原版高散射增幅器，一个船插挂两个监听器。
- `com.fs.starfarer.api.impl.combat.SquallOnFireEffect`：原版直接用 `setDamage(...)` 改伤害的写法，绕过了 `getModifier()`。
