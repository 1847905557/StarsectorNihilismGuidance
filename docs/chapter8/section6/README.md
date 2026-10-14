# 配置战斗监听器

本节通过战斗监听器给伤害增加 10%，并记录实际结算结果。一个接口参与计算，另一个接口在结算后收到通知；它们还需要登记到适用的引擎或舰船上。完成这个示例后，再将同样的登记方式用于舰船推进、射程与 OP 修改。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 选择 `Listener` 或 `Modifier`；
- 挂载到引擎、舰船或 `stats`；
- 读取部署与伤害事件；
- 修改射程与 OP 回调结果；
- 管理注册、重复与收尾。

## 前置知识

- [C8-S1] 的战斗状态和结束判断；
- [C8-S2] 的当前对象；
- [C8-S3] 的插件登记和实例；
- [C8-S4] 的数值与来源 ID；
- [C7-S5] 的日志。

## 选择事件与监听范围

监听器接收特定事件，登记对象决定它监听哪一部分。整场战斗的伤害观察可以挂在引擎上，单舰效果则挂在对应舰船；OP 修饰由 stats 提供入口。先按功能选择接口与对象：

### 监听接口速查表

| 接口 | 作用 | 合法挂载位置 |
|---|---|---|
| `FleetMemberDeploymentListener` | 监听舰队成员部署 | `CombatEngineAPI` |
| `DamageListener` | 读取伤害结算结果 | 引擎或舰船 |
| `DamageDealtModifier` | 修改造成的伤害 | 引擎或造成伤害的舰船 |
| `DamageTakenModifier` | 修改承受的伤害 | 引擎或承受伤害的舰船 |
| `HullDamageAboutToBeTakenListener` | 在结构伤害结算前介入 | 引擎或承受伤害的舰船 |
| `AdvanceableListener` | 舰船级持续推进 | `ShipAPI` |
| `WeaponRangeModifier` | 修改武器最终射程 | `ShipAPI` |
| `WeaponBaseRangeModifier` | 修改武器基础射程 | `ShipAPI` |
| `WeaponOPCostModifier` | 修改武器 OP 消耗 | `MutableShipStatsAPI` |
| `FighterOPCostModifier` | 修改战机 OP 消耗 | `MutableShipStatsAPI` |

**接口与登记对象共同决定回调。** 实现了接口，还要进入对应对象的监听器管理器。下面的伤害示例作用于整场战斗，所以两类实例都登记到引擎。

## 增加伤害并记录结算

效果由 `DamageDealtModifier` 在造成方结算前处理。向 `damage.getModifier()` 写入本次修饰，再返回来源 ID。本例使用 `GearIn_dealt` 增加 10%：

```java
damage.getModifier().modifyPercent("GearIn_dealt", 10f);
return "GearIn_dealt";
```

回调的 `param` 可以是弹体、光束、EMP 电弧或自定义事件对象，`target`、`point` 与 `shieldHit` 提供目标、位置和护盾上下文。需要限定这项加成的适用事件时，就从这些参数判断。

结算完成后，`DamageListener` 收到 `ApplyDamageResultAPI`，读取实际护盾、装甲、结构与 EMP 结果。它可以在引擎上记录整场结算，也可以挂到舰船上观察自身伤害。

完整例分别用 `GearInDamageDealtModifier` 写入修饰，用 `GearInDamageListener` 记录结果。**输入修饰与最终结算在不同回调中取得**，日志中的伤害仍由整个结算过程决定。

## 登记本场监听实例

创建负责登记的 `GearInCombatListenerPlugin`，在 `settings.json` 的 `plugins` 中加入：

```json
{
    "plugins": {
        "gearInCombatListenerPlugin": "data.scripts.gearin.combat.GearInCombatListenerPlugin"
    }
}
```

插件在首次 `advance(...)` 取得本场引擎，通过 `getListenerManager()` 登记两个实例。登记前按类查询，当前对象已有同类监听器时继续使用已有登记。插件字段保存自己创建的实例，以便结束时移除。

单独管理监听器时，使用下列局部方法：

```java
CombatListenerManagerAPI manager = owner.getListenerManager();
manager.hasListener(listener);
manager.hasListenerOfClass(MyListener.class);
manager.addListener(listener);
manager.getListeners(MyListener.class);
manager.removeListener(listener);
manager.removeListenerOfClass(MyListener.class);
```

`owner` 是引擎或舰船，`MyListener` 换成实际类。查询、加入和移除都通过登记对象的管理器完成；stats 则提供自己的对应方法。

战斗进入 `isCombatOver()` 时，完整例先解除这两个监听器，再移除登记插件。**插件列表与监听器管理器分别保存对象。** 结束插件后，监听器登记仍可能存在，因此收尾也分别完成。

## 处理承受方与结构伤害

需要减少承受的伤害时，使用 `DamageTakenModifier`。它的参数与造成方相似，登记到承受方舰船之后，可采用下面的局部方法乘上 0.9：

```java
public String modifyDamageTaken(Object param, CombatEntityAPI target,
        DamageAPI damage, Vector2f point, boolean shieldHit) {
    damage.getModifier().modifyMult("GearIn_taken", 0.9f);
    return "GearIn_taken";
}
```

结构伤害结算前还可以使用 `HullDamageAboutToBeTakenListener`。返回 `true` 会抵消当前这一笔伤害，下面的局部方法保护低于 50 点的结构伤害：

```java
public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship,
        Vector2f point, float damageAmount) {
    return damageAmount < 50f;
}
```

只保护一艘舰船时，将监听器挂到该舰船；接口也支持引擎范围。这个返回值针对本次结构伤害，不是修改舰船的永久减伤属性。

部署事件使用 `FleetMemberDeploymentListener`，它只登记到引擎。`reportFleetMemberDeployed(...)` 提供本次 `DeployedFleetMemberAPI`，从部署成员取得舰体的关系沿用 [C8-S2]。

## 在舰船上推进状态

需要随着某艘舰船持续推进的状态，可以使用 `AdvanceableListener` 并挂到 `ShipAPI`。它的 `advance(...)` 接收所属舰船的推进时间，例如局部计时：

```java
public void advance(float amount) {
    reportInterval.advance(amount);
}
```

计时器属于这份监听实例，每艘舰船可以保留独立计数。登记和移除使用 `ship.getListenerManager()`，暂停与销毁也按所属舰船的运行阶段处理。

## 用返回值修改射程与 OP

### 武器射程

`WeaponRangeModifier` 挂到 `ShipAPI`，用返回值提供最终射程的 percent、mult 与 flat 修饰。下面的大型武器增加 10%，另外两个方法返回中性值：

```java
public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
    return weapon.getSize() == WeaponAPI.WeaponSize.LARGE ? 10f : 0f;
}

public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
    return 1f;
}

public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
    return 0f;
}
```

`WeaponBaseRangeModifier` 影响基础射程，方法名分别为 `getWeaponBaseRangePercentMod`、`getWeaponBaseRangeMultMod` 与 `getWeaponBaseRangeFlatMod`。参数同样可以按武器类型、尺寸或 ID 选择返回值，使用时按实际接口声明填写。

### 武器与战机 OP

`WeaponOPCostModifier` 和 `FighterOPCostModifier` 挂 `MutableShipStatsAPI`，接收当前计算值并返回新值：

```java
public int getWeaponOPCost(MutableShipStatsAPI stats, WeaponSpecAPI weapon, int currCost) {
    return Math.max(0, currCost - 2);
}

public int getFighterOPCost(MutableShipStatsAPI stats, FighterWingSpecAPI fighter, int currCost) {
    return Math.max(0, currCost - 1);
}
```

武器成本减 2，战机成本减 1，两者下限都为 0。`currCost` 提供已经计算到当前步骤的值，多个来源在它的基础上继续计算。这里将新成本作为返回值交回调用方，普通 stats 与 Dynamic 的来源记录仍采用 [C8-S4] 的写法。

## 完整最小例

三个类保存到 `src/data/scripts/gearin/combat/`，插件登记采用上面的条目。当前成品验证伤害结算与造成方修饰，其他局部方法按其接口和挂载对象扩展。

::: details 完整成品：`GearInDamageListener`

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;

public class GearInDamageListener implements DamageListener {

    private static final Logger LOG = Global.getLogger(GearInDamageListener.class);

    @Override
    public void reportDamageApplied(Object source,
            CombatEntityAPI target,
            ApplyDamageResultAPI result) {
        LOG.info("GearIn damage: target=" + target
                + " hull=" + result.getDamageToHull()
                + " armor=" + result.getTotalDamageToArmor()
                + " shields=" + result.getDamageToShields()
                + " emp=" + result.getEmpDamage());
    }
}
```

:::

::: details 完整成品：`GearInDamageDealtModifier`

```java
package data.scripts.gearin.combat;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;

public class GearInDamageDealtModifier implements DamageDealtModifier {

    @Override
    public String modifyDamageDealt(Object param,
            CombatEntityAPI target,
            DamageAPI damage,
            Vector2f point,
            boolean shieldHit) {
        damage.getModifier().modifyPercent("GearIn_dealt", 10f);
        return "GearIn_dealt";
    }
}
```

:::

::: details 完整成品：`GearInCombatListenerPlugin`

```java
package data.scripts.gearin.combat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 在本场战斗登记伤害监听与修饰，并在收尾时解除。 */
public class GearInCombatListenerPlugin extends BaseEveryFrameCombatPlugin {

    private final GearInDamageListener damageListener = new GearInDamageListener();
    private final GearInDamageDealtModifier dealtModifier = new GearInDamageDealtModifier();
    private CombatEngineAPI engine;
    private boolean registered;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            engine = Global.getCombatEngine();
        }
        CombatListenerManagerAPI manager = engine.getListenerManager();
        if (!registered) {
            if (!manager.hasListenerOfClass(GearInDamageListener.class)) {
                manager.addListener(damageListener);
            }
            if (!manager.hasListenerOfClass(GearInDamageDealtModifier.class)) {
                manager.addListener(dealtModifier);
            }
            registered = true;
        }
        if (engine.isCombatOver()) {
            manager.removeListener(damageListener);
            manager.removeListener(dealtModifier);
            engine.removePlugin(this);
        }
    }
}
```

:::

## 验证方案

1. 保存三个类和插件登记，构建并重启，进入 `GearIn_test`。
2. 用武器命中护盾、装甲和结构，搜索 `GearIn damage` 并比较实际字段。
3. 在对应局部接口实现中分别测试舰船推进、射程或 OP，核对挂载对象和返回值。
4. 暂停再恢复，比较所属对象的推进。
5. 结束战斗，核对引擎级实例解除，再进入下一场检查新注册。
6. 比较日志中的接口、目标和来源 ID，确认同一功能只登记一次。

预期结果：

- 伤害监听记录结算字段，造成方修饰写入 `GearIn_dealt`。
- 各局部接口在匹配对象上生效，射程与 OP 采用相应返回值。
- 状态属于当前舰船或战斗实例。
- 收尾解除本插件登记的监听，下一场重新建立。

## 故障分析

- **监听器注册后没有回调**：接口与挂载对象不匹配。
    - **解决方案**：按接口表确认对象归属；部署监听器注册到引擎，舰船级推进注册到 `ShipAPI`，OP 修饰器注册到 `MutableShipStatsAPI`。

- **伤害修饰结果方向相反**：`DamageDealtModifier` 与 `DamageTakenModifier` 挂到了错误的一方。
    - **解决方案**：造成伤害的一方使用 `DamageDealtModifier`，承受伤害的一方使用 `DamageTakenModifier`，再次进行同一命中实验。

- **舰船推进日志始终为空**：`AdvanceableListener` 被注册到引擎或统计对象。
    - **解决方案**：通过 `ship.getListenerManager()` 注册，并确认监听器实例绑定当前 `ShipAPI`。

- **射程或 OP 数值没有变化**：修饰器挂载表面正确但实现了错误的接口。
    - **解决方案**：最终射程使用 `WeaponRangeModifier`，基础射程使用 `WeaponBaseRangeModifier`，武器和战机 OP 使用对应的 `stats` 修饰器。

- **同一事件每次出现多条日志**：同一对象重复注册了监听器实例。
    - **解决方案**：注册前调用 `hasListenerOfClass(...)` 或 `hasListener(...)`，保留一个属于当前对象的实例。

- **插件停止后仍出现部署日志**：引擎级监听器仍处于注册状态。
    - **解决方案**：在 `removePlugin(this)` 前调用 `engine.getListenerManager().removeListener(listener)`。

- **第二场战斗沿用上一场状态**：监听器实例或其字段跨战斗保存。
    - **解决方案**：把计数器和计时器放在本场战斗创建的监听器中，清除静态字段和旧引擎引用。

- **日志无法定位触发对象**：日志缺少接口、阶段或对象 ID。
    - **解决方案**：每条日志加入稳定的 `GearIn` 前缀、接口名称和舰船或成员 ID。

## 总结

伤害修饰参与计算，结果监听读取结算；舰船推进、射程和 OP 则分别登记到适用对象。接口决定事件，登记对象决定范围，单独实现接口还不足以收到通知。

本场插件负责创建、登记与解除伤害监听。结束时从管理器移除自己的实例，再结束插件，下一场重新建立这一组对象。

## 建议参考项

- `com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI`：监听器增删查；
- `com.fs.starfarer.api.combat.listeners.CombatListenerUtil`：伤害与结构伤害事件派发；
- `com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener`：部署成员回调；
- `com.fs.starfarer.api.combat.listeners.DamageListener`：伤害事件监听；
- `com.fs.starfarer.api.combat.listeners.DamageDealtModifier`：造成伤害修正；
- `com.fs.starfarer.api.combat.listeners.DamageTakenModifier`：承受伤害修正；
- `com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener`：舰体濒损预警；
- `com.fs.starfarer.api.combat.listeners.AdvanceableListener`：逐帧推进回调；
- `com.fs.starfarer.api.combat.listeners.WeaponRangeModifier`：武器射程修正；
- `com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier`：武器基础射程修正；
- `com.fs.starfarer.api.combat.listeners.WeaponOPCostModifier`：武器 OP 消耗修正；
- `com.fs.starfarer.api.combat.listeners.FighterOPCostModifier`：战机 OP 消耗修正。
