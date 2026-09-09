# 配置战斗监听器

战斗监听器把部署、伤害、舰船推进和装配数值计算交给对应的回调处理。先按事件选择接口，再把监听器放到引擎、舰船或统计对象上，运行时就能在正确的对象范围内收到结果。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 按事件选择 Listener 或 Modifier 接口；
- 把监听器注册到匹配的对象并读取回调参数；
- 验证修饰结果并在战斗结束时清理实例。

## 前置知识

- [C8-S1] 的战斗引擎状态与时间读取；
- [C8-S2] 的战斗对象和舰船归属；
- [C8-S3] 的战斗插件注册、暂停判断与收尾；
- [C8-S4] 的 `MutableStat`、Dynamic 和 modifier ID；
- [C7-S5] 的日志记录方法。

## 监听器接口速查表

先把要观察或修改的事件写成一句话，再从下表选择接口和挂载对象。表格用于查阅，参数和调用结果在后文按类别展开。

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

## 监听引擎级事件

`CombatEngineAPI.getListenerManager()` 管理整场战斗的引擎级监听器。伤害结算是最直接的例子：`DamageListener` 读取已经完成的结果，`DamageDealtModifier` 在伤害结算前写入造成方的修饰值。注册前用 `hasListenerOfClass(...)` 检查同类实例，再调用 `addListener(...)`；战斗结束时由负责注册的插件先移除监听器，再移除自身。

### 读取伤害结算结果

`DamageListener` 在伤害已经结算后收到 `ApplyDamageResultAPI`，因此适合记录实际扣除的护盾、装甲、船体和 EMP 数值。将它注册到引擎监听器表可以观察整场战斗的结果：

下面的片段创建一个伤害监听器，并在注册前按类去重。

```java
CombatListenerManagerAPI listeners = engine.getListenerManager();
DamageListener damageListener = new GearInDamageListener();
if (!listeners.hasListenerOfClass(GearInDamageListener.class)) {
    listeners.addListener(damageListener);
}
```

::: warning 在注册前按类去重
一般来说用 `hasListenerOfClass` 进行去重处理。不去重导致的效果问题往往不容易快速被发现。
:::

引擎级和舰船级的 `DamageListener` 都由 `CombatListenerUtil.reportDamageApplied(...)` 派发。把它挂在舰船上时，日志范围只覆盖该舰船相关的伤害。

### 修改造成的伤害

`DamageDealtModifier` 挂在造成伤害的舰船，或作为整场战斗的引擎级修饰器。回调返回本次伤害修饰的来源 ID，实际数值写入 `damage.getModifier()`。完整示例使用同一引擎级表面注册：

下面的片段把造成方修饰器加入同一个引擎级监听器表。

```java
DamageDealtModifier dealtModifier = new GearInDamageDealtModifier();
if (!listeners.hasListenerOfClass(GearInDamageDealtModifier.class)) {
    listeners.addListener(dealtModifier);
}
```

`param` 可能是射弹、光束、EMP 电弧或脚本自定义对象；`target`、`point` 和 `shieldHit` 描述本次命中的对象、位置和护盾结果。

### 修改承受的伤害

`DamageTakenModifier` 挂在承受伤害的舰船，参数结构与造成伤害的修饰器相同。它适合按命中来源、护盾状态或目标类型修改 `DamageAPI` 的 modifier：

这个方法片段把承受伤害乘以 `0.9`，并返回本次修饰使用的稳定 ID。

```java
public String modifyDamageTaken(Object param,
                                CombatEntityAPI target,
                                DamageAPI damage,
                                Vector2f point,
                                boolean shieldHit) {
    damage.getModifier().modifyMult("GearIn_taken", 0.9f);
    return "GearIn_taken";
}
```

### 在结构伤害前介入

`HullDamageAboutToBeTakenListener` 在结构伤害落到舰船前触发。`damageAmount` 是本次即将造成的结构伤害；返回 `true` 会抵消这一笔伤害：

这个回调片段只抵消低于 50 点的结构伤害，用返回值表达是否拦截本次结算。

```java
public boolean notifyAboutToTakeHullDamage(Object param,
                                           ShipAPI ship,
                                           Vector2f point,
                                           float damageAmount) {
    return damageAmount < 50f;
}
```

该接口可以注册到引擎或承受伤害的舰船。若只保护一艘舰船，优先使用舰船级监听器，回调范围更小。

## 监听舰船级推进

`AdvanceableListener` 只有挂在 `ShipAPI` 时才会推进。它的唯一方法接收舰船级的时间增量：

下面的实现把本次推进时间交给舰船自己的计时器。

```java
public void advance(float amount) {
    reportInterval.advance(amount);
}
```

在 `ship.getListenerManager()` 上使用 `hasListenerOfClass(...)`、`addListener(...)`、`removeListener(...)` 管理实例。计时器和计数器放在每艘舰船自己的监听器对象里，日志就能对应到具体舰船；暂停和舰船销毁由所属对象的生命周期决定。

伤害监听器也可以挂在舰船上：`DamageDealtModifier` 观察造成伤害的舰船，`DamageTakenModifier` 与 `HullDamageAboutToBeTakenListener` 观察承受伤害的舰船，`DamageListener` 记录该舰船收到的结算结果。

## 配置射程与 OP 修饰器

### 修改武器射程

`WeaponRangeModifier` 和 `WeaponBaseRangeModifier` 都挂在 `ShipAPI`。前者作用于最终射程，后者作用于基础射程。引擎分别汇总 percent、mult 和 flat 返回值：

这个片段只让大型武器增加最终射程百分比，其余两种修饰保持中性值。

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

`WeaponBaseRangeModifier` 使用相同的三个返回方法，但修改的是基础射程。`weapon` 参数可以用于按武器类型、尺寸或 ID 返回不同数值。

### 修改武器与战机 OP

`WeaponOPCostModifier` 和 `FighterOPCostModifier` 只挂在 `MutableShipStatsAPI`。回调接收当前计算值，返回值成为新的装配消耗：

下面两个方法分别降低武器和战机的当前 OP 消耗，并把结果限制在零以上。

```java
public int getWeaponOPCost(MutableShipStatsAPI stats,
                           WeaponSpecAPI weapon,
                           int currCost) {
    return Math.max(0, currCost - 2);
}

public int getFighterOPCost(MutableShipStatsAPI stats,
                            FighterWingSpecAPI fighter,
                            int currCost) {
    return Math.max(0, currCost - 1);
}
```

多个修饰器同时存在时，应基于传入的 `currCost` 继续计算，使不同来源的结果能够连续传递。射程修饰器和 OP 修饰器通过监听器回调返回结果；普通 `MutableStat` 与 Dynamic 的 modifier 写入仍按 [C8-S4] 的规则处理。

## 管理监听器实例

引擎和舰船都通过 `CombatListenerManagerAPI` 提供增删查操作：

下面的片段展示同一管理器上的去重、注册、查询和移除操作。

```java
CombatListenerManagerAPI manager = owner.getListenerManager();

manager.hasListener(listener);
manager.hasListenerOfClass(MyListener.class);
manager.addListener(listener);

manager.removeListener(listener);
manager.removeListenerOfClass(MyListener.class);
manager.getListeners(MyListener.class);
```

统计对象使用 `MutableShipStatsAPI.addListener(...)` 及对应移除方法。注册前完成去重，插件收尾时清理自己添加的监听器，下一场战斗或下一艘舰船重新创建所属实例。

## 完整最小例

::: details 完整成品：GearInDamageListener

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

::: details 完整成品：GearInDamageDealtModifier

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

## 验证方案

前置数据：已按 [C8-S3] 接入每帧战斗插件；[C4-S1] 的 `GearIn_test` 战役可用，本节的 `GearInDamageListener` 和 `GearInDamageDealtModifier` 已就绪。

1. 启动 `GearIn_test` 战斗，确认 `GearInDamageListener` 和 `GearInDamageDealtModifier` 各完成一次注册。
2. 让舰船承受护盾、装甲和结构伤害，搜索 `GearIn damage`，核对结算字段与造成方修饰结果。
3. 观察舰船级 `AdvanceableListener` 的周期日志，暂停并恢复战斗后比较推进次数。
4. 在装配界面检查武器射程、武器 OP 和战机 OP 的变化。
5. 结束战斗，确认引擎级监听器先清理，再移除负责注册它的插件。
6. 开始第二场战斗，确认监听器状态和对象引用重新建立。
7. 在 `starsector.log` 中搜索稳定前缀、接口名称和对象 ID。

预期结果：

- `DamageListener` 记录实际结算字段，修饰器返回的来源 ID 出现在对应 modifier 中；
- `AdvanceableListener` 只在所属舰船推进时输出；
- 射程和 OP 结果与回调返回值一致；
- 战斗结束后不再出现上一场战斗的监听器日志；
- 第二场战斗的计数从新实例开始。

## 故障分析

- **监听器注册后没有回调**：接口与挂载对象不匹配。

    - **解决方案**：按接口表确认对象归属；部署监听器注册到引擎，舰船级推进注册到 `ShipAPI`，OP 修饰器注册到 `MutableShipStatsAPI`。

- **伤害修饰结果方向相反**：`DamageDealtModifier` 与 `DamageTakenModifier` 挂到了错误的一方。

    - **解决方案**：造成伤害的一方使用 `DamageDealtModifier`，承受伤害的一方使用 `DamageTakenModifier`，再次进行同一命中实验。

- **舰船推进日志始终为空**：`AdvanceableListener` 被注册到引擎或统计对象。

    - **解决方案**：通过 `ship.getListenerManager()` 注册，并确认监听器实例绑定当前 `ShipAPI`。

- **射程或 OP 数值没有变化**：修饰器挂载表面正确但实现了错误的接口。

    - **解决方案**：最终射程使用 `WeaponRangeModifier`，基础射程使用 `WeaponBaseRangeModifier`，武器和战机 OP 使用对应的 stats 修饰器。

- **同一事件每次出现多条日志**：同一对象重复注册了监听器实例。

    - **解决方案**：注册前调用 `hasListenerOfClass(...)` 或 `hasListener(...)`，保留一个属于当前对象的实例。

- **插件停止后仍出现部署日志**：引擎级监听器仍处于注册状态。

    - **解决方案**：在 `removePlugin(this)` 前调用 `engine.getListenerManager().removeListener(listener)`。

- **第二场战斗沿用上一场状态**：监听器实例或其字段跨战斗保存。

    - **解决方案**：把计数器和计时器放在本场战斗创建的监听器中，清除静态字段和旧引擎引用。

- **日志无法定位触发对象**：日志缺少接口、阶段或对象 ID。

    - **解决方案**：每条日志加入稳定的 `GearIn` 前缀、接口名称和舰船或成员 ID。

## 总结

配置战斗监听器时，先根据事件选择接口，再把实例挂到接口要求的引擎、舰船或 stats 对象。部署和伤害接口读取战斗事件，`AdvanceableListener` 负责舰船级推进，射程与 OP 修饰器通过回调返回对应的计算结果。注册前去重，战斗结束时清理引擎级监听器，下一场战斗重新建立属于当前对象的状态。

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
