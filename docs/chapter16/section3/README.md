# 用 LazyLib 制作自动点防御决策器

点防御武器若只打最近导弹，会把火力浪费在擦肩而过的小弹头上。本节先按射界过滤，再以载荷和距离计算威胁，让炮塔优先转向真正会造成损失的目标。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `AIUtils` 限定点防御需要考虑的敌方导弹范围；
- 在评分前用 `WeaponUtils` 排除射界外目标；
- 把伤害、EMP 与距离组合成可观察的威胁分；
- 每帧只选择一个最高威胁目标并驱动炮塔转向；
- 从射界、评分分母和控制权定位点防御失效。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

一枚高爆鱼雷从 1100 su 外正面接近，同时一枚低伤害导弹从 300 su 外掠过侧面。点防御应锁定射界内的鱼雷，并持续给出瞄准点。

## 文件与 API 链路

### 点防御 API 职责速查表

| 对象 | 职责 |
|---|---|
| `AIUtils` | 提供当前帧附近敌方导弹 |
| `WeaponUtils` | 判断射界并驱动炮塔朝向 |
| `CombatUtils` | 按区域补充查询 |
| `DefenseUtils` | 把本舰装甲状态纳入扩展评分 |

### 取得当前帧威胁列表

**插入位置：`GearInPointDefenseDirector.choose()` 开头。** 只查询本舰 1400 su 内的敌方导弹。

```java
for (MissileAPI missile : AIUtils.getNearbyEnemyMissiles(weapon.getShip(), 1400f)) {
    // 当前帧候选
}
```

完成信号：远离防区的导弹不会参与排序。

### 先排除炮塔够不到的目标

**插入位置：`choose()` 的循环首个分支。** 用武器弧而非舰首朝向判断可射击性。

```java
if (!WeaponUtils.isWithinArc(missile, weapon)) {
    continue;
}
```

完成信号：侧后方导弹不会抢走锁定。

### 把载荷和距离合成威胁分

**插入位置：`GearInMissileThreat.score()`。** 高伤害、高 EMP、近距离都会提高优先级。

```java
float payload = missile.getDamageAmount() + missile.getEmpAmount() * 0.5f;
return payload * 1000f / Math.max(100f, distance);
```

完成信号：鱼雷的分数高于擦过侧面的轻型导弹。

### 保留最高威胁导弹

**插入位置：`choose()` 的 `best` 更新处。** 一次扫描完成目标选择。

```java
if (score > bestScore) {
    best = missile;
    bestScore = score;
}
```

完成信号：每帧最多产生一个锁定目标。

### 让炮塔开始转向

**插入位置：`choose()` 返回前。** 只在目标存在时调用瞄准助手。

```java
if (best != null) {
    WeaponUtils.aimTowardsPoint(weapon, best.getLocation(), Global.getCombatEngine().getElapsedInLastFrame());
}
```

完成信号：炮塔朝目标转动，目标消失后自然重新选择。

## 排查关键故障

下面是**局部故障片段**：

```java
MissileAPI target = AIUtils.getNearestEnemyMissile(ship);
return target;
```

实际症状：炮塔频繁追逐射界外的小导弹，真正接近的鱼雷无人处理。

原因：最近距离没有表达射界与载荷，目标也没有经过武器可达性过滤。

下面是**局部修正片段**：

```java
if (WeaponUtils.isWithinArc(missile, weapon)) {
    float score = threat.score(ship, missile);
}
```

复验：同时生成 300 su 外的低载荷侧弹和 1100 su 外的高载荷正面鱼雷，记录两者是否进入射界、各自分数与最终目标。

## 完整最小例

`GearInMissileThreat` 只计算分数，`GearInPointDefenseDirector` 负责查询、过滤与执行。这样可以独立观察分数，而不必先让炮塔取得控制权。

::: details 完整成品：GearInMissileThreat

```java
package data.scripts.gearin.lib;

import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public final class GearInMissileThreat {
    public float score(ShipAPI ship, MissileAPI missile) {
        float distance = Math.max(100f, MathUtils.getDistance(ship, missile));
        float payload = missile.getDamageAmount() + missile.getEmpAmount() * 0.5f;
        return payload * 1000f / distance;
    }
}
```

:::

::: details 完整成品：GearInPointDefenseDirector

```java
package data.scripts.gearin.lib;

import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;

import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public final class GearInPointDefenseDirector {
    private final GearInMissileThreat threat = new GearInMissileThreat();

    public MissileAPI choose(WeaponAPI weapon) {
        MissileAPI best = null;
        float bestScore = 0f;
        for (MissileAPI missile : AIUtils.getNearbyEnemyMissiles(weapon.getShip(), 1400f)) {
            if (!WeaponUtils.isWithinArc(missile, weapon)) {
                continue;
            }
            float score = threat.score(weapon.getShip(), missile);
            if (score > bestScore) {
                best = missile;
                bestScore = score;
            }
        }
        if (best != null) {
            WeaponUtils.aimTowardsPoint(weapon, best.getLocation(), Global.getCombatEngine().getElapsedInLastFrame());
        }
        return best;
    }
}
```

:::

## 验证方案

前置场景：给测试舰安装一门可转向点防御武器，并准备可分别从正面与侧后方生成导弹的测试入口。

1. 在 1100 su 正面生成一枚高伤害鱼雷，记录其射界判断、威胁分和炮塔转向。
2. 在 300 su 侧后方同时生成一枚低伤害导弹，确认它在评分前被射界过滤。
3. 把低伤害导弹移入射界，记录两枚导弹的分数与最终返回目标。
4. 让鱼雷离开 1400 su 查询范围，确认候选列表与锁定随下一帧更新。
5. 临时改用最近导弹故障片段，再恢复分层选择，比较两次锁定结果。

预期结果：

- 1400 su 外目标不进入候选列表，射界外目标不进入评分；
- 两个目标均在射界内时，返回值对应威胁分较高者；
- 每帧最多返回一个 `MissileAPI`，该目标存在时炮塔才接受转向指令；
- 目标离开范围或消失后，下一帧重新选择剩余目标；
- `starsector.log` 没有未处理异常。

## 故障分析

- **炮塔追逐射界外的小导弹**：评分前只比较了距离，没有过滤射界。

    - **解决方案**：先调用 `isWithinArc`，再比较威胁分。

- **炮塔在两个目标之间抖动**：两个目标分数接近，每帧都会交换排名。

    - **解决方案**：保留当前目标，并要求新目标超过切换阈值。

- **炮塔完全不转向**：控制代码没有运行在武器效果或自定义 AI 中，原版 AI 仍持有武器。

    - **解决方案**：把调用接入实际控制回调。

- **威胁分变为无穷大**：导弹距离为零或过小时直接进入分母。

    - **解决方案**：为距离设置正数下限。

## 总结

点防御决策依次经过查询、可达性过滤、威胁评分和执行。把评分独立成角色类后，您可以加入装甲缺口、弹头类型或命中时间，而不必改动炮塔控制流程。

## 建议参考项

- `AIUtils.getNearbyEnemyMissiles`：取得当前帧导弹候选；
- `WeaponUtils.isWithinArc`：确认武器射界；
- `WeaponUtils.aimTowardsPoint`：驱动炮塔朝指定坐标转向。
