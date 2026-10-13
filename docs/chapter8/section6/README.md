# 配置战斗监听器

战斗回调之外，游戏还按部署、伤害和数值计算派发监听事件。本节先根据功能选择接口与挂载对象，再用两个伤害监听示例观察结算与修饰。注册插件负责本场实例的加入和收尾，其余局部方法展示舰船推进、射程与 OP 的对应位置。

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

## 根据功能选择对象

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

**接口与挂载对象一起决定是否收到回调。** 先说明希望观察谁，再选择表中的入口；舰船级功能只监听当前舰船，整场观察才使用引擎范围。

## 观察伤害结算与造成方修饰

`DamageListener` 在伤害结算后收到 `ApplyDamageResultAPI`，提供实际护盾、装甲、结构与 EMP 结果。当前挂在引擎上观察整场结算，舰船也能使用对应监听记录自身伤害。

`DamageDealtModifier` 在造成方结算前写入 `damage.getModifier`，返回本次来源 ID。本例按 `GearIn_dealt` 增加 10%：

```java
damage.getModifier().modifyPercent("GearIn_dealt", 10f);
return "GearIn_dealt";
```

`param` 可以是弹体、光束、EMP 电弧或自定义事件对象，`target`、`point` 和 `shieldHit` 提供目标、位置与护盾上下文。

完整的 `GearInDamageListener` 负责结果日志，`GearInDamageDealtModifier` 负责当前修饰，两者按角色分开。**修饰输入与结算结果分别观察**，同一命中可以按事件和结果对应。

## 注册并在结束时解除

创建本节 `GearInCombatListenerPlugin`，在 `settings` 的 `plugins` 加入：

```json
{
    "plugins": {
        "gearInCombatListenerPlugin": "data.scripts.gearin.combat.GearInCombatListenerPlugin"
    }
}
```

当前插件首次 `advance` 取得引擎，在它的 `getListenerManager` 中登记两个实例。按类检查防止同一功能重复注册；状态由本场实例保存，`isCombatOver` 时先解除监听，再移除插件。

单独管理时采用下列局部操作：

```java
CombatListenerManagerAPI manager = owner.getListenerManager();
manager.hasListener(listener);
manager.hasListenerOfClass(MyListener.class);
manager.addListener(listener);
manager.getListeners(MyListener.class);
manager.removeListener(listener);
manager.removeListenerOfClass(MyListener.class);
```

这里 `owner` 是当前引擎或舰船，`MyListener` 为实际类；`stats` 也提供自己的增删和查询方法。移除自己注册的实例，保持对象范围明确。

## 其他伤害入口

`DamageTakenModifier` 作用于承受伤害的一方，参数与造成方相似。下面是局部方法，装入对应接口实现后挂到承受方舰船：

```java
public String modifyDamageTaken(Object param, CombatEntityAPI target,
        DamageAPI damage, Vector2f point, boolean shieldHit) {
    damage.getModifier().modifyMult("GearIn_taken", 0.9f);
    return "GearIn_taken";
}
```

`HullDamageAboutToBeTakenListener` 在结构伤害前介入，`true` 抵消当前这一笔伤害：

```java
public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship,
        Vector2f point, float damageAmount) {
    return damageAmount < 50f;
}
```

当前片段保护低于 50 点的结构伤害。只需要保护一艘舰船时挂在该舰船，范围更直接；接口也支持引擎监听。

`FleetMemberDeploymentListener` 只挂引擎，`reportFleetMemberDeployed` 接收本次 `DeployedFleetMemberAPI`。部署身份与场上舰体的关系按前面的对象映射理解。

## 在舰船上推进状态

`AdvanceableListener` 挂 `ShipAPI`，`advance` 接收所属舰船的推进时间。局部方法如下：

```java
public void advance(float amount) {
    reportInterval.advance(amount);
}
```

计时器属于当前监听实例，每艘舰船的计数独立。通过 `ship.getListenerManager` 登记与移除，暂停和销毁按所属对象运行阶段处理。

## 用返回值修改射程与 OP

### 武器射程

`WeaponRangeModifier` 挂 `ShipAPI`，通过 `percent`、`mult` 和 `flat` 返回最终射程修饰。局部方法使大型武器增加 10%：

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

本例分别减 2 和 1，下限 0。多个来源基于 `currCost` 继续计算，便于连续传递结果。这里由回调返回数值，普通 `stats` 和 `Dynamic` 仍按上一节使用修饰表。

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

监听接口按事件和计算过程派发，挂载对象限定范围。结果监听读取实际结算，`Modifier` 改变当前计算，舰船推进与射程作用于 `ShipAPI`，OP 作用于 `stats`。

注册与清理由实际拥有它们的功能处理。编者建议按当前对象建立实例，先把一种事件完整验证，再增加其他监听；对象和范围清楚，重复和跨场问题就容易定位。

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
