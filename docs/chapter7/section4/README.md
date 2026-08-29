# 观察武器开火与伤害过程

本节继续观察武器开火后的行为：回调顺序、射弹与光束结算，以及伤害事件。示例会把关键过程写入日志，便于核对每个对象在何时出现。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 区分 `getProjectiles()`、`getMissiles()`、`getBeams()` 三个对象集合的语义：`getProjectiles()` **包含**导弹 (API javadoc 原文 "Includes missiles.") 与光束脉冲射线，`getMissiles()` 是导弹子集，`getBeams()` 只含光束武器实体 —— 并解释为什么叠加前两个会重复统计；
- 说出武器开火流的回调顺序：`EveryFrameWeaponEffectPlugin.advance`(本帧转向/开火**之前**)→ 本帧核心操作 → `EveryFrameWeaponEffectPluginWithAdvanceAfter.advanceAfter`(**之后**)，以及 `fireShot` 内部 "先扣弹药、失败则不生成射弹、生成后再回调 `onFire`" 的次序；
- 列出射弹生命周期的关键状态 (`getElapsed` / `isFading` / `didDamage` / `getDamageTarget` / `getWeapon` 可为 null / 射弹不能命中 source) 与对应的可观察结果；
- 解释导弹七项特殊行为：
    - 装填判定 (未装填碰撞不结算伤害)；
    - 熄火 fizzle 流程 (`flameOut` / 碰撞类切换 / 淡出减伤)；
    - EMP 电弧抗性 (`empResistance` 计数)；
    - 制导与 flare(`isGuided` / `isFlare` / `GuidedMissileAI`)；
    - 射程/寿命换算 (`useProjectileRangeCalculation` 与射程修正)。
- 说明光束语义：`BeamAPI` 实体只在开火/充能期间存在、伤害是 DPS 语义且按 0.1 秒间隔结算、`didDamageThisFrame()` / `getDamageTarget()` 只对本帧有效；
- 用引擎/舰船监听器管理器注册 `DamageDealtModifier` / `DamageTakenModifier` / `DamageListener`，并从 `starsector.log` 症状定位本节列出的八类典型故障。

## 前置知识

- [C1-S5] 的武器 CSV、`.wpn`、`.proj` 数据关系与字段登记；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C6-S3] 的 `Global` 生命周期与跨战斗引用边界；
- [C7-S1] 的战斗插件注册、每帧回调与暂停判断；
- [C7-S2] 的 `CombatEngineAPI` 状态读取、实体遍历与暂停语义；
- [C7-S3] 的 `MutableStat` 返回类型与修饰值读取。

## 文件与 API 链路

本节先给出对象图，后面每一节的论述都从这张图取对象。接口集中在 `com.fs.starfarer.api.combat`(监听器在 `...api.combat.listeners`，spec 在 `com.fs.starfarer.api.loading`)：

### 本节接口速查表

| 接口 / 类型 | 本节角色 | 链路 |
| --- | --- | --- |
| `WeaponAPI` | 运行时武器实例 | `ShipAPI.getAllWeapons()` 取得 |
| `DamagingProjectileAPI` | 射弹运行态接口 | `engine.getProjectiles()` 的元素；`spawnProjectile(...)` 的产物 |
| `MissileAPI` | 导弹 (`extends DamagingProjectileAPI`) | `engine.getMissiles()` 的元素；`getProjectiles()` 也包含它 |
| `BeamAPI` | 光束武器实体 (开火/充能期间存在) | `engine.getBeams()` 的元素；`WeaponAPI.getBeams()` |
| `OnFireEffectPlugin` / `OnHitEffectPlugin` | 数据绑定的一次性回调 | `.proj` 的 `onFireEffect` / `onHitEffect` 字段挂载类名 |
| `EveryFrameWeaponEffectPlugin` / `WithAdvanceAfter` / `WeaponEffectPluginWithInit` | 武器逐帧效果 | `.wpn` 的 `everyFrameEffect` 字段挂载类名 |
| `BeamEffectPlugin` / `BeamEffectPluginWithReset` | 光束逐帧效果 | `.wpn` 光束武器的光束效果类 |
| `MissileAIPlugin` / `GuidedMissileAI` | 导弹每帧导航 | `ModPlugin.pickMissileAI(...)` 钩子或默认 AI(引擎按导弹类型选择) |
| `DamageDealtModifier` / `DamageTakenModifier` / `DamageListener` | 伤害事件监听器 | `engine.getListenerManager()` / `ShipAPI.getListenerManager()` |
| `CombatListenerManagerAPI` | 监听器注册表 | `addListener` / `removeListener` / `hasListener` / `getListeners` |
| `DamageAPI` | 单次伤害对象 | `projectile.getDamage()` / `beam.getDamage()`；`getModifier()` 返回 `MutableStat` |

### 数据挂载点速查表

数据挂载点 (原版数据中的实例)：

| 数据文件 | 键 | 原版实例 | 使用方 |
| --- | --- | --- | --- |
| `data/weapons/*.wpn` | `everyFrameEffect` | `riftbeam.wpn` 的 `"everyFrameEffect":"com.fs.starfarer.api.impl.combat.RiftBeamEffect"` | 武器实例构造时生成效果对象 |
| `data/weapons/proj/*.proj` | `onFireEffect` | `canister_flak.proj` | 武器开火生成射弹后回调 |
| `data/weapons/proj/*.proj` | `onHitEffect` | `assaying_rift_proj.proj` | 射弹命中结算时回调 |
| `data/weapons/proj/*.proj` | `specClass` | `"specClass":"missile"` (`annihilator_rocket.proj`) | 决定加载为 `MissileSpecAPI` 还是 `ProjectileSpecAPI` |

### 区分武器对象集合

- `engine.getProjectiles()` 返回 `List<DamagingProjectileAPI>`(`com.fs.starfarer.api.combat.CombatEngineAPI`，javadoc "Includes missiles.")：
    - 导弹 (`MissileAPI`) 与光束脉冲射线 (`spawnType` 为 `BEAM` / `BALLISTIC_AS_BEAM` 的射线，它也实现 `DamagingProjectileAPI`) 都算 "射弹"。
- `engine.getMissiles()` 返回 `List<MissileAPI>`，是射弹集合的导弹子集。
- `engine.getBeams()` 返回 `List<BeamAPI>`，只含光束武器实体，与光束脉冲射线是两类东西。
- 三者都是引擎对象表的类型查询 (`objects.getList(...)`)。

### 注册伤害监听器

- 引擎级：`engine.getListenerManager().addListener(Object)`(`CombatListenerManagerAPI` 提供 `addListener` / `removeListener` / `removeListenerOfClass` / `hasListener` / `getListeners`)。
- 舰船级：`ShipAPI.getListenerManager()`：
    - `DamageDealtModifier` javadoc 原文："When adding to a ship, add to the ship dealing the damage, NOT the one taking the damage."。
    - `DamageTakenModifier` 反之；
    - 两条 javadoc 都写 "Should add this to a ship where possible instead of the engine as performance will be better."。

## 武器开火流 (WeaponAPI)

对象图就位后，从每帧的起点看起：一次开火从武器实例的推进开始。

### 每帧回调顺序

武器实例由引擎随舰船创建，每帧推进一次。回调顺序 (弹丸类武器与光束武器同构)：

1. `EveryFrameWeaponEffectPlugin.advance(amount, engine, weapon)` —— 本帧转向、开火等核心操作**之前** (API javadoc 原文 "Called before this frame's operations for the weapon - turning, firing, etc.")。
2. 本帧核心操作：转向、充能/爆发、开火判定、冷却、弹药装填、禁用状态。
3. 若效果对象实现了 `EveryFrameWeaponEffectPluginWithAdvanceAfter`，`advanceAfter(amount, engine, weapon)` 在本帧操作**之后**调用。

### 暂停时武器 / 射弹 / 光束全部停摆

**暂停时武器不推进**。暂停期间引擎跳过对武器、射弹与光束实体的推进循环，武器效果回调、射弹移动、光束结算全部停摆 (与插件 `advance` 暂停时仍被调用不同，[C7-S1])。

### fireShot：一次开火做了什么

开火判定通过后，武器按炮管模式调用 `fireShot(barrel, angle)`，内部顺序固定：

1. **先扣弹药**：`deductOneAmmo()` 失败 (弹药武器弹尽)→ 本次不发射，不生成射弹、不开火音、无后坐力。可观察结果：弹尽后 "点击开火无任何反应"，但没有报错。
2. 后坐力 (`recoilTracker.applyRecoil()`)、开火音 (`fireSoundTwo` 声集在炮口位置播放)。
3. 引擎生成射弹实体 —— 按 `.proj` 的 `spawnType` / spec 类型分别创建：
    - `BALLISTIC` → 弹道弹体；
    - `PLASMA` → 等离子弹体；
    - `BEAM` / `BALLISTIC_AS_BEAM` → 光束脉冲射线；
    - `MissileSpecAPI` → 导弹实体；
    - `DamagingExplosionSpec` → 爆炸实体。
4. 炮口火光粒子 (视口附近才生成)、炮管闪光复位。
5. 若产物是 `DamagingProjectileAPI`，回调 `onFireEffect.onFire(projectile, weapon, engine)` —— `onFire` 收到的就是**刚生成的那一个**射弹对象；若该武器的 `everyFrameEffect` 本身也实现了 `OnFireEffectPlugin`，同一个效果对象还会再被调一次。

炮管模式：`LINKED` 一次齐射全部炮管；`DUAL` 双管交替偏移。

`ALTERNATING` / `ALTERNATING_BURST` 轮换炮管索引 (原版 `annihilator.wpn` 的 `"barrelMode":"ALTERNATING"`)。

硬点/炮塔的炮管偏移由 `.wpn` 的 `hardpointOffsets` / `turretOffsets` 数组决定。

### 必须遵守的规则

1. **修改武器数值前必须 `ensureClonedSpec()`**。`getSpec()` 返回的是所有同类武器共享的 spec 对象，直接改它会污染同 id 的所有武器实例 (`WeaponAPI.getSpec()`)。
    - `ensureClonedSpec()` 把当前实例切到私有克隆并保存 `getOriginalSpec()`；
    - 违反的可观察结果：多艘舰的同名武器数值一起变化，且跨战斗持续。
2. **`onFire` / `onHit` 回调参数只在本回调内使用，禁止跨发/跨帧缓存**。`onFire` 的 `projectile` 参数属于 "本发射弹"；把 `projectile`、`target` 或 `damageResult` 存进字段跨命中复用，会污染命中状态归属。
    - 违反的可观察结果：下一次开火/命中覆盖字段后，旧逻辑对已移除实体继续操作，或第二场战斗引用失效 (与 [C6-S3] / [C7-S2] 的跨战斗缓存禁令同一条纪律)。

## 射弹生命周期 (DamagingProjectileAPI)

射弹生成之后，生命周期由四件事决定：生成参数、飞行与射程换算、命中结算、集合归属。

- **生成**：`engine.spawnProjectile(ship, weapon, weaponId, point, angle, shipVelocity)`：
    - `weaponId` 按 id 查武器 spec 并取其弹体 spec；第四参 `projSpecId` 可直接覆盖弹体 spec。
    - `ship` 传 null 时引擎用 "转移穿梭机" 假舰作 source；返回 `CombatEntityAPI`(可为 null)。
- **飞行与射程**：弹道弹的射程换算为飞行时间 —— `maxRange` 先经舰船统计修正 (额外射程倍率/平值)，再除以弹速 (弹道弹创建时)：
    - 弹体在飞行末尾淡出 (淡出进度驱动 `damage.setMultiplier`)；`isFading()` 为 true 后伤害转软辐能 (`setSoftFlux(true)`)。
    - 可观察结果：射程即将耗尽时弹丸变淡、命中伤害下降，不再产生硬辐能。
- **命中**：碰撞判定 → 目标实体 `applyDamage`(碰撞处理器把射弹写入目标的 `paramAboutToApplyDamage`，作为伤害监听器的 `param` 参数)→ 引擎回调 `notifyDealtDamage`：
    - `didDamage()` 置 true、`getDamageTarget()` 指向承伤实体、`onHitEffect.onHit(...)` 回调。
    - 弹道弹命中后移除 (除非 `passThroughMissiles` / `passThroughFighters`)。
- **集合关系**：`getProjectiles()` 含导弹与光束脉冲；`getWeapon()` 可为 null(API 生成的无武器射弹)；`getSource()` 可为 null；`getTailEnd()` 只对 moving ray 与弹道弹有非空语义。

必须遵守的规则：

3. **射弹不能命中自己的 source**。`getSource()` javadoc 原文："Projectiles can't hit their source, except for fizzled-out missiles."：
    - 碰撞处理器对 source 命中做了三道豁免判定：熄火中的导弹经 `canHitSource()` 判定、`HITS_SHIPS_AND_ASTEROIDS` 碰撞类、`MISSILE_FF` 的爆炸弹。
    - 违反/误解的可观察结果：贴脸开火不会打到自己；熄火后的导弹可能回砸发射舰 (命中判定按熄火瞬间的舰船包围盒)。
4. **从导弹生成的 `BALLISTIC` / `BALLISTIC_AS_BEAM` / `PLASMA` 射弹必须 `setFromMissile(true)`**。
    - `setFromMissile` javadoc 原文："Should be set to true for BALLISTIC, BALLISTIC\_AS\_BEAM, and PLASMA\_SHOT projectiles spawned from a missile. Needed for incoming damage evaluation AI to function properly."。
    - 违反的可观察结果：敌方 "评估来袭伤害" 的 AI 不把这批射弹当作导弹对待，点防御/规避行为异常 (无报错，静默失效)。

## 导弹特殊行为 (MissileAPI)

导弹在射弹生命周期之上叠加七项特殊行为，日志里最先读出的通常是装填与熄火状态：

- **装填 (arming)**：`isArmed()` = `forceAlwaysArmed || (elapsed >= armingTime && (!isFizzling() || armedWhileFizzling))` (`armingTime` 来自 `MissileSpecAPI.getArmingTime()`)：
    - **未装填的导弹碰撞不结算伤害**：碰撞处理器遇到未装填导弹 (或两枚都未装填的导弹对撞) 直接走物理碰撞处理并返回。
    - 可观察结果：导弹在装填时间内撞上目标 —— 无伤害、无命中粒子，只弹开或消失。
- **哑弹概率**：`armedWhileFizzling = random > dudProbabilityOnFlameout` —— `dudProbabilityOnFlameout > 0` 的导弹熄火后可能保持未装填 (哑弹)，撞击无伤害。
- **熄火 (fizzle) 流程**：
    - `flameOut()` = `flightTime = maxFlightTime + 1`；
    - `flightTime > maxFlightTime` 首帧：发动机熄火、碰撞类切换为 `afterFlameoutCollisionClass`(默认 `MISSILE_FF`，之后仍可碰撞伤害)、尾迹终止 (除非 `noFlameoutOnFizzling`)；
    - `fizzleOnReachingWeaponRange`(`MissileSpecAPI.isFizzleOnReachingWeaponRange()`)：从 `getStart()` 到当前位置距离超过 `getMaxRange()` 即 `flameOut()` —— `getMaxRange()` 默认取发射武器的 `getRange()`；
    - 熄火期间：`isFizzling()`(`timeSinceFizzling > 0`)、`isFading()`(`timeSinceFizzling > fizzleTime - fadeTime`)；`noCollisionWhileFading` → 碰撞类置 `NONE`；`reduceDamageWhileFading` → 伤害乘数按剩余淡出比例下降且转软辐能；
    - 过期条件：`timeSinceFizzling >= fizzleTime` 或血量归零。
- **射程/寿命换算**：`maxFlightTime = spec.maxFlightTime * 额外射程倍率 + 额外射程 / maxSpeed`；`useProjectileRangeCalculation` 则按 "修正后射程 / 最大速度" 折算。可观察结果：给武器挂射程加成后导弹飞得更远。
- **EMP 电弧抗性**：`setEmpResistance(int)` javadoc 原文："Number of times a missile will ignore being hit by an system EMP *arc* (not emp damage) instead of flaming out."：
    - EMP 电弧命中导弹时，`empResistance <= 0` 直接 `flameOut()`，否则只递减。
    - 可观察结果：`empResistance=0`(默认) 的导弹被 EMP 电弧击中立即熄火坠落；有抗性时电弧命中只减计数。
- **制导与 flare**：`isGuided()` = AI 是 `GuidedMissileAI`：
    - `MissileAIPlugin` javadoc："An implementation of this should almost always also implement GuidedMissileAI, this is required for the missile to be properly affected by flares."。
    - 不实现 `GuidedMissileAI` 的自定义导弹 AI 不受 flare 影响。
    - `isFlare()` 对 `FLARE` / `FLARE_SEEKER` / `FLARE_JAMMER` 类型为 true。
    - `giveCommand(...)` 只能由 `MissileAIPlugin` 使用，且只有移动类 `ShipCommand` 有效 (`MissileAPI` javadoc 原文)。
- **默认 AI 选择**：`ModPlugin.pickMissileAI` 钩子优先，未覆盖时按 `missileType` 选默认 AI：
    - `MISSILE` → 通用跟踪 AI；
    - `HEATSEEKER` → 热追踪 AI；
    - `MIRV` → 分导弹头 AI；
    - `ROCKET` → 火箭 AI；
    - `FLARE` → 诱饵弹 AI；
    - `PHASE_CHARGE` / `BOMB` → `ProximityFuseAIAPI` 等。

## 光束语义 (BeamAPI)

射弹之外的另一类实体是光束。光束不生成弹体，而是由武器在开火 / 充能期间持有：

- **实体生命周期**：光束武器开火/充能期间持有光束实体 (实现 `BeamAPI`)，`engine.getBeams()` 可见：
    - 射线从炮口沿瞄准方向以 `beamSpeed` 每秒伸长至武器射程，命中目标时被截短 (`wasShortened`)；`isExpired()` 在发射舰已死 (hulk 且非 alive) 时为 true。
    - `BeamEffectPlugin.advance(amount, engine, beam)` 在光束武器充能/射击状态每帧调用；实现 `BeamEffectPluginWithReset` 的效果对象在光束实体创建与销毁时收到 `reset()`。
- **伤害是 DPS 语义**：光束伤害按 DPS 结算 (`damage.isDps() == true`)；伤害按 0.1 秒间隔结算，伤害乘数 = 累计亮度 / 累计时长：
    - 持续照射伤害爬升，起手与收尾 (充能上升/下降) 期间乘数低。
    - 可观察结果：光束武器的伤害不是每帧全额结算，而是约每秒 10 次、随照射时间增强。
- **命中状态**：`didDamageThisFrame()` = 本帧被截短 (命中) 且伤害乘数 > 0；`getDamageTarget()` 返回本帧截短目标；`getFrom()` / `getTo()` 是当前帧绝对引擎坐标端点，`getRayEndPrevFrame()` / `getLengthPrevFrame()` 是上一帧端点。

必须遵守的规则：

5. **`didDamageThisFrame()` / `getDamageTarget()` 只对本帧结算语义有效，禁止缓存跨帧使用**。光束实体每帧更新 from/to 与命中状态，上一帧的目标可能已经死亡、离开射线或换了目标。违反的可观察结果：把上一帧目标当本帧目标继续施加逻辑 —— 对已死目标操作、射线已转向仍引用旧目标。

## 事件监听器 (fire / damage)

对象流讲完，接下来是观察与介入点：fire / damage 两类事件监听器。前两个由 `.proj` 数据绑定，后三个挂引擎或舰船：

### 事件监听器速查表

| 接口 | 回调 | 挂载/注册 | 时机 |
| --- | --- | --- | --- |
| `OnFireEffectPlugin` | `onFire(projectile, weapon, engine)` | `.proj` `onFireEffect` | 武器生成射弹后 (`fireShot` 内) |
| `OnHitEffectPlugin` | `onHit(projectile, target, point, shieldHit, damageResult, engine)` | `.proj` `onHitEffect` | 射弹/导弹/光束脉冲命中结算时 (`notifyDealtDamage` 内) |
| `DamageDealtModifier` | `modifyDamageDealt(param, target, damage, point, shieldHit)` | 引擎或**造成伤害方**舰船 | 伤害结算前 (`ShipAPI.applyDamage`) |
| `DamageTakenModifier` | `modifyDamageTaken(param, target, damage, point, shieldHit)` | 引擎或**承受伤害方**舰船 | 伤害结算前 |
| `DamageListener` | `reportDamageApplied(source, target, result)` | 引擎或目标舰船 | 伤害结算后 |

`param` 参数取值：null、`DamagingProjectileAPI`、`BeamAPI`、`EmpArcEntityAPI` 或脚本自定义对象 (`com.fs.starfarer.api.combat.listeners.DamageDealtModifier` javadoc)。

它在碰撞时由引擎写入目标舰船 (`setParamAboutToApplyDamage`)。

### 类实现了接口 ≠ 实例被引擎调用

`OnFireEffectPlugin` / `OnHitEffectPlugin` / `EveryFrameWeaponEffectPlugin` 这些接口的实现类，只有类名被写进 `.proj` / `.wpn` 的数据字段时，引擎才会在创建射弹 / 武器时通过脚本加载器实例化并回调；用 `addPlugin` 注册成战斗插件，只让插件回调 (`advance` 等) 生效。

两类实例由完全不同的机制创建 (脚本类加载器每次创建新实例)，互相收不到对方的回调 —— "类实现了接口" 不等于 "实例被引擎调用"。

必须遵守的规则：

6. **伤害修改只能通过 `damage.getModifier()` 完成，并按 javadoc 返回修改 id(或 null)**。`DamageDealtModifier` javadoc 原文："Modifications to damage should ONLY be made using damage.getModifier()."：
    - 引擎在结算前应用监听器的修改、结算后按返回的 id 从 `damage.getModifier()` 还原。
    - `DamageAPI.getDamage()` 由乘数 (射弹淡出/光束亮度每帧重设) 与 `getModifier()` 返回的修正统计相乘得出。
    - 违反的可观察结果：直接 `damage.setDamage(...)` 会覆盖射弹的淡出乘数 (淡出中的射弹恢复全额伤害) 或被光束的每帧乘数重设覆盖 (修改不生效)，且不参与 "结算后还原"，多监听器并存时残留污染下一条伤害。

## 对象流速查表

把上面的对象流收成一张速查表，写代码时按环节查入口：

| 环节 | 入口 | 关键方法 | 要点 |
| --- | --- | --- | --- |
| 武器实例 | `ship.getAllWeapons()` | `getId` / `getType` / `getRange` / `getAmmo` / `getCooldownRemaining` / `getChargeLevel` / `isFiring` / `isBeam` / `isDisabled` | 每帧顺序：`advance` (前) → 核心操作 → `advanceAfter` (后)；暂停时不推进 |
| 一次开火 | `fireShot` | 扣弹药 → 后坐力 → `fireSoundTwo` → 生成射弹 → `onFire` | 弹药扣减失败则不生成射弹；`onFire` 参数是刚生成的射弹 |
| 射弹 | `engine.getProjectiles()` | `getElapsed` / `isFading` / `didDamage` / `getDamageTarget` / `getWeapon` / `getSource` | 含导弹与光束脉冲；不能命中 source；`getWeapon` / `getSource` 可为 null |
| 导弹 | `engine.getMissiles()` | `isArmed` / `isFizzling` / `isFading` / `isGuided` / `isFlare` / `getFlightTime` / `getMaxFlightTime` / `getMaxRange` / `getEmpResistance` | 未装填不结算伤害；EMP 电弧抗性计数；熄火淡出减伤 |
| 光束 | `engine.getBeams()` | `getFrom` / `getTo` / `getLength` / `getBrightness` / `didDamageThisFrame` / `getDamageTarget` / `getDamage` | 0.1 秒伤害间隔；DPS 语义；命中状态只对本帧有效 |
| 伤害回调 | `engine.getListenerManager()` | `modifyDamageDealt` / `modifyDamageTaken` / `reportDamageApplied` | 修改只能走 `damage.getModifier()` 并返回 id |

## 实现武器伤害观察器

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 注册伤害监听器

```java
// 插入位置：GearInFlowObserverPlugin.init()
this.engine = engine;
engine.getListenerManager().addListener(new GearInDamageObserverListener());
LOG.info("GearIn flow init: damage listeners registered on engine");
GearInStateReport.logWeapons(engine);
```

新增行为：本场战斗开始时接入结算前与结算后的伤害观察，并立即记录武器状态。完成信号：日志先出现初始化行，第一次造成伤害后依次出现 `GearIn dealt`、`GearIn taken` 和 `GearIn damage`。

### 识别新生成的射弹

```java
// 插入位置：GearInFlowObserverPlugin.trackNewProjectiles()
for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
    if (seenProjectiles.add(keyOf(projectile))) {
        LOG.info("GearIn spawn " + projectile.getProjectileSpecId()
                + " spawnType=" + projectile.getSpawnType()
                + " fromMissile=" + projectile.isFromMissile()
                + " weapon=" + (projectile.getWeapon() == null ? "null" : projectile.getWeapon().getId())
                + " source=" + (projectile.getSource() == null ? "null" : projectile.getSource().getHullSpec().getHullId()));
    }
}
```

新增行为：每个射弹实体只在首次进入对象表时报告一次。完成信号：每次开火出现一条 `GearIn spawn`；导弹和光束脉冲同样从 `getProjectiles()` 被识别。

### 对照射弹总集与导弹子集

```java
// 插入位置：GearInStateReport.logProjectiles()
List<DamagingProjectileAPI> projectiles = engine.getProjectiles();
List<MissileAPI> missiles = engine.getMissiles();
LOG.info("GearIn projectiles: total=" + projectiles.size()
        + " missiles=" + missiles.size()
        + " (missiles are included in total)");
for (MissileAPI missile : missiles) {
    LOG.info("GearIn missile " + missile.getProjectileSpecId()
            + " armed=" + missile.isArmed()
            + " guided=" + missile.isGuided()
            + " fizzling=" + missile.isFizzling()
            + " flight=" + missile.getFlightTime() + "/" + missile.getMaxFlightTime());
}
```

新增行为：总射弹数与导弹子集并列显示，并为导弹增加装填、制导和熄火状态。完成信号：发射一枚导弹时 `total` 与 `missiles` 同时增加 1，而统计结果不会把它相加成两个实体。

### 读取光束本帧命中状态

```java
// 插入位置：GearInStateReport.logBeams() 的 BeamAPI 循环内
WeaponAPI weapon = beam.getWeapon();
DamageAPI damage = beam.getDamage();
LOG.info("GearIn beam weapon=" + (weapon == null ? "null" : weapon.getId())
        + " length=" + beam.getLength()
        + " dps=" + damage.isDps()
        + " dmg=" + damage.getDamage()
        + " brightness=" + beam.getBrightness()
        + " didDamageThisFrame=" + beam.didDamageThisFrame()
        + " target=" + (beam.getDamageTarget() == null
                ? "null" : beam.getDamageTarget().getClass().getSimpleName()));
```

新增行为：光束长度、DPS、亮度和本帧目标进入同一条报告。完成信号：持续照射时 `didDamageThisFrame=true` 且目标为舰船实体；停火后光束实体与对应日志一起消失。

### 对照武器操作前后状态

```java
// 插入位置：GearInWeaponFlowEffect.advance() 与 advanceAfter()
if (weapon.isFiring() && !wasFiring) {
    LOG.info("GearIn weapon " + weapon.getId() + " started firing"
            + " (before-frame callback)");
}
wasFiring = weapon.isFiring();

weaponReportInterval.advance(amount);
if (weapon.isFiring() && weaponReportInterval.intervalElapsed()) {
    LOG.info("GearIn weapon " + weapon.getId() + " after-frame:"
            + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
            + " cooldown=" + weapon.getCooldownRemaining()
            + " charge=" + weapon.getChargeLevel());
}
```

新增行为：操作前记录开火边沿，操作后读取已经扣减的弹药、冷却和充能。完成信号：一次开火先出现 `before-frame callback`，持续开火时每 2 秒出现一次 `after-frame` 状态。

### 报告伤害结算前后

```java
// 插入位置：GearInDamageObserverListener 的对应监听器回调
LOG.info("GearIn dealt param=" + (param == null ? "null" : param.getClass().getSimpleName())
        + " target=" + target.getClass().getSimpleName()
        + " base=" + damage.getBaseDamage()
        + " total=" + damage.getDamage()
        + " shieldHit=" + shieldHit);
return null;

LOG.info("GearIn damage source=" + (source == null ? "null" : source.getClass().getSimpleName())
        + " target=" + target.getClass().getSimpleName()
        + " hull=" + result.getDamageToHull()
        + " armor=" + result.getTotalDamageToArmor()
        + " shields=" + result.getDamageToShields()
        + " emp=" + result.getEmpDamage());
```

新增行为：结算前读取伤害对象，结算后读取实际分配到护盾、装甲、船体和 EMP 的结果。完成信号：同一次命中先出现 `GearIn dealt`，随后出现 `GearIn damage`；本节观察器返回 `null`，不会改变数值。

## 例集：六个单角色类与注册列速查表

六个类按注册列分工：战斗插件与伤害监听器由代码注册，武器逐帧效果与射弹回调由数据绑定，周期报告器是包内协作的静态工具。

| 类 | 角色 | 注册 / 绑定列 |
| --- | --- | --- |
| `GearInFlowObserverPlugin` | 战斗插件观察者 | `CombatEngineAPI.addPlugin` (战斗上下文注册) |
| `GearInDamageObserverListener` | 引擎级伤害监听器 | `engine.getListenerManager().addListener` (由 `GearInFlowObserverPlugin.init()` 接入) |
| `GearInWeaponFlowEffect` | 武器逐帧效果 | `.wpn` `everyFrameEffect` |
| `GearInShotOnFireEffect` | 射弹开火回调 | `.proj` `onFireEffect` |
| `GearInShotOnHitEffect` | 射弹命中回调 | `.proj` `onHitEffect` |
| `GearInStateReport` | 运行态周期报告器 (静态) | 无独立注册列；由 `GearInFlowObserverPlugin` 包内调用 |

## GearInFlowObserverPlugin(战斗插件，addPlugin 注册)

由战斗上下文 `addPlugin` 注册。`init(CombatEngineAPI)` 把伤害监听器接入引擎，并打印玩家舰武器清单 (部署前 `getPlayerShip()` 是转移穿梭机假舰，没有武器)。

`advance` 先做暂停判断 (暂停时武器 / 射弹 / 光束都不推进，只有插件 `advance` 仍被调用)，再逐帧识别新生成的射弹 (纯 Java 即可见，不依赖数据绑定)。

最后按 3 秒节奏触发三次周期报告 (实现见 `GearInStateReport`)。

可观察信号：`GearIn flow init: damage listeners registered on engine`、`GearIn spawn ...`、周期报告行。

## GearInDamageObserverListener(引擎级伤害监听器，listenerManager.addListener 接入)

由 `GearInFlowObserverPlugin.init()` 用 `engine.getListenerManager().addListener(...)` 接入，每次伤害结算都会回调：

结算前 `modifyDamageDealt` / `modifyDamageTaken`(本类一律返回 `null`，不改数值)，结算后 `reportDamageApplied`。

可观察信号：`GearIn dealt ...` / `GearIn taken ...`(结算前) 与 `GearIn damage ...`(结算后)，顺序固定为先 `dealt` 后 `damage`。

若要修改伤害，必须走 `damage.getModifier()` 修改并返回同一个 id (硬规范 6)。

## GearInWeaponFlowEffect(武器逐帧效果，.wpn everyFrameEffect 绑定)

只有把本类类名写进 `.wpn` 的 `everyFrameEffect` 字段时，游戏才会为每把武器创建独立实例并回调 `init(WeaponAPI)` / `advance` / `advanceAfter`。

`advance` 在本帧转向 / 开火等操作**之前**被调用 (API javadoc 原文 "Called before this frame's operations for the weapon - turning, firing, etc.")，记录开火切换；

`advanceAfter` 在操作完成后按 2 秒节奏打印弹药 / 冷却 / 充能状态。

可观察信号：`GearIn weapon init ...`、`GearIn weapon ... started firing (before-frame callback)`、`GearIn weapon ... after-frame: ...`。

## GearInShotOnFireEffect(射弹开火回调，.proj onFireEffect 绑定)

只有把本类类名写进 `.proj` 的 `onFireEffect` 字段时才会被调用。

`onFire` 收到的就是刚生成的那一个射弹对象 (`fireShot` 生成后回调)。

参数都是 "本次事件上下文"，禁止跨发缓存 (规则 2)。可观察信号：`GearIn onFire ...`。

## GearInShotOnHitEffect(射弹命中回调，.proj onHitEffect 绑定)

只有把本类类名写进 `.proj` 的 `onHitEffect` 字段时才会被调用 (射弹 / 导弹 / 光束脉冲命中结算时触发)。参数都是 "本次命中上下文"，禁止跨命中缓存 (规则 2)。可观察信号：`GearIn onHit ...`。

## GearInStateReport(周期报告器，包内协作)

三个静态方法只做观察、不修改任何游戏数值，由 `GearInFlowObserverPlugin` 按 3 秒节奏调用：

- `logWeapons` 打印玩家舰全部武器的弹药 / 冷却 / 充能 / 开火状态与光束实体数；
- `logProjectiles` 演示 "`getProjectiles()` 包含导弹" —— `total` 与 `missiles` 两列并排，导弹还逐枚打印装填 / 制导 / 熄火 / 寿命 / EMP 抗性状态；
- `logBeams` 演示光束 DPS 语义与命中状态 (`dps` / `dmg` / `brightness` / `didDamageThisFrame` / `target`)。

可观察信号：周期 `GearIn weapon ...`、`GearIn projectiles: total=... missiles=...`、`GearIn missile ...`、`GearIn beam ...`。

## 注册与接入片段

注册片段 (工程片段 —— 本节不修改任何既有文件，只演示注册调用的写法)：

```java
    // 已有战斗插件的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInFlowObserverPlugin());
        // ... 原有逻辑
    }
```

数据接入片段 (工程片段 —— 本单元不创建数据文件；接入后武器逐帧效果与射弹回调才生效。以下 ID 由本节声明，路径在 `mods/GearIndustry/` 下自洽)：

```text
data/weapons/GearIn_test_gun.wpn        # 片段：弹丸武器，绑定 everyFrameEffect
data/weapons/proj/GearIn_test_shot.proj # 片段：弹体 spec，绑定 onFireEffect / onHitEffect
```

```json
// GearIn_test_gun.wpn(片段)
{
    "id": "GearIn_test_gun",
    "specClass": "projectile",
    "type": "ENERGY",
    "size": "SMALL",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFlowEffect",
    "projectileSpecId": "GearIn_test_shot"
}
```

```json
// GearIn_test_shot.proj(片段)
{
    "id": "GearIn_test_shot",
    "specClass": "projectile",
    "spawnType": "BALLISTIC",
    "onFireEffect": "data.scripts.gearin.combat.GearInShotOnFireEffect",
    "onHitEffect": "data.scripts.gearin.combat.GearInShotOnHitEffect"
}
```

数据绑定类名：必须遵守的规则

`everyFrameEffect` / `onFireEffect` / `onHitEffect` 的类名必须是脚本类加载器可实例化的完整类名 (含包名)；绑错或类缺失时，武器 / 射弹创建抛出 `Problem loading class [...]`(可定位)。

伤害修改示范片段 (对应 `GearInDamageObserverListener` 的修改做法；本节不改数值，只演示正确写法)：

```java
    // 若要修改伤害，必须走 damage.getModifier() 并返回修改 id(硬规范 6)：
    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        damage.getModifier().modifyPercent("GearIn_flow_demo", 5f); // 示例：+5% 伤害
        return "GearIn_flow_demo"; // 引擎结算后会按此 id 还原
    }
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用前置知识中的编译链路)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/combat/` 下的六个类 (可用 `jar tf` 核对)。

## 完整最小例

规则讲得再多，不如让日志替您说话。在前置知识所述的 MOD 布局与战斗插件模型之上，本节新增六个单角色 Java 类 (不新增资源；`.wpn` / `.proj` 接入片段仅演示挂载点，本单元不创建数据文件)，包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearIn<Role>`)：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin\
│       ├── GearInModPlugin            # C6-S2 所有；本节不改动
│       ├── GearInGlobalAccess         # C6-S3 所有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin   # C7-S2 所有；本节只消费
│           ├── GearInFlowObserverPlugin  # 本节新增 (六类)
│           ├── GearInDamageObserverListener
│           ├── GearInWeaponFlowEffect
│           ├── GearInShotOnFireEffect
│           ├── GearInShotOnHitEffect
│           └── GearInStateReport
└── jars\
```

::: details 完整成品：GearInFlowObserverPlugin

```java
package data.scripts.gearin.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 战斗插件观察者：由战斗上下文 addPlugin 注册 (C7-S1 模型)。
 * init() 把伤害监听器接入引擎，每帧识别新生成的射弹，并按 3 秒节奏
 * 触发武器 / 射弹 / 导弹 / 光束的运行态报告 (实现见 GearInStateReport)。
 * 暂停守卫：暂停时武器 / 射弹 / 光束都不推进，只有插件 advance 仍被调用 (C7-S1)。
 */
public class GearInFlowObserverPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInFlowObserverPlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(3f, 3f);
    /** 已见过的射弹 (specId + 实体身份码)，用于逐帧识别 "新生成" 的射弹。 */
    private final Set<String> seenProjectiles = new HashSet<>();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        engine.getListenerManager().addListener(new GearInDamageObserverListener());
        LOG.info("GearIn flow init: damage listeners registered on engine");
        GearInStateReport.logWeapons(engine);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return;
        }
        trackNewProjectiles();
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            GearInStateReport.logWeapons(engine);
            GearInStateReport.logProjectiles(engine);
            GearInStateReport.logBeams(engine);
        }
    }

    /** 逐帧识别新生成的射弹 (含导弹与光束脉冲)，打印生成时刻的运行态。 */
    private void trackNewProjectiles() {
        for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
            if (seenProjectiles.add(keyOf(projectile))) {
                LOG.info("GearIn spawn " + projectile.getProjectileSpecId()
                        + " spawnType=" + projectile.getSpawnType()
                        + " fromMissile=" + projectile.isFromMissile()
                        + " weapon=" + (projectile.getWeapon() == null ? "null" : projectile.getWeapon().getId())
                        + " source=" + (projectile.getSource() == null ? "null" : projectile.getSource().getHullSpec().getHullId())
                        + " damage=" + projectile.getDamageAmount()
                        + " emp=" + projectile.getEmpAmount());
            }
        }
    }

    private static String keyOf(DamagingProjectileAPI projectile) {
        return projectile.getProjectileSpecId() + "@" + System.identityHashCode(projectile);
    }
}
```

:::

::: details 完整成品：GearInDamageObserverListener

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;

/**
 * 引擎级伤害监听器 (结算前修改 + 结算后报告)：由 GearInFlowObserverPlugin.init()
 * 接入 engine.getListenerManager()，每次伤害结算都会回调。本类不改数值 ——
 * 要修改只能走 damage.getModifier() 并返回修改 id(硬规范 6)，修改方法一律返回 null。
 */
public class GearInDamageObserverListener
        implements DamageDealtModifier, DamageTakenModifier, DamageListener {

    private static final Logger LOG = Global.getLogger(GearInDamageObserverListener.class);

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        LOG.info("GearIn dealt param=" + (param == null ? "null" : param.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " base=" + damage.getBaseDamage()
                + " total=" + damage.getDamage()
                + " shieldHit=" + shieldHit);
        return null;
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        LOG.info("GearIn taken param=" + (param == null ? "null" : param.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " base=" + damage.getBaseDamage()
                + " shieldHit=" + shieldHit);
        return null;
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        LOG.info("GearIn damage source=" + (source == null ? "null" : source.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " hull=" + result.getDamageToHull()
                + " armor=" + result.getTotalDamageToArmor()
                + " shields=" + result.getDamageToShields()
                + " emp=" + result.getEmpDamage());
    }
}
```

:::

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
 * advance 在本帧转向 / 开火等操作之前 (API javadoc 原文 "Called before this frame's
 * operations for the weapon - turning, firing, etc.")，记录开火切换；
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
 * 参数都是 "本次事件上下文"，禁止跨发缓存 (硬规范 2)。
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
 * 禁止跨命中缓存 (硬规范 2)。
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

::: details 完整成品：GearInStateReport

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 战斗运行态周期报告器：由 GearInFlowObserverPlugin 按 3 秒节奏调用，
 * 打印玩家舰武器、射弹 / 导弹集合与光束实体的运行态。只做观察，
 * 不修改任何游戏数值。
 */
public class GearInStateReport {

    private static final Logger LOG = Global.getLogger(GearInStateReport.class);

    /** 武器运行态：弹药、冷却、充能、开火与光束实体数。 */
    public static void logWeapons(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip(); // 战斗内永不 null(C7-S2)；部署前是转移穿梭机假舰
        for (WeaponAPI weapon : player.getAllWeapons()) {
            LOG.info("GearIn weapon " + weapon.getId()
                    + " type=" + weapon.getType() + " size=" + weapon.getSize()
                    + " range=" + weapon.getRange()
                    + " charge=" + weapon.getChargeLevel()
                    + " cooldown=" + weapon.getCooldownRemaining() + "/" + weapon.getCooldown()
                    + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                    + " firing=" + weapon.isFiring()
                    + " beam=" + weapon.isBeam()
                    + " beams=" + weapon.getBeams().size()
                    + " disabled=" + weapon.isDisabled());
        }
    }

    /** 射弹集合与导弹子集：getProjectiles() 已包含导弹 (API javadoc "Includes missiles.")，不要叠加统计。 */
    public static void logProjectiles(CombatEngineAPI engine) {
        List<DamagingProjectileAPI> projectiles = engine.getProjectiles();
        List<MissileAPI> missiles = engine.getMissiles();
        LOG.info("GearIn projectiles: total=" + projectiles.size()
                + " missiles=" + missiles.size()
                + " (missiles are included in total)");
        for (MissileAPI missile : missiles) {
            LOG.info("GearIn missile " + missile.getProjectileSpecId()
                    + " armed=" + missile.isArmed()
                    + " armingTime=" + missile.getArmingTime()
                    + " guided=" + missile.isGuided()
                    + " flare=" + missile.isFlare()
                    + " fizzling=" + missile.isFizzling()
                    + " fading=" + missile.isFading()
                    + " flight=" + missile.getFlightTime() + "/" + missile.getMaxFlightTime()
                    + " maxRange=" + missile.getMaxRange()
                    + " empRes=" + missile.getEmpResistance()
                    + " mine=" + missile.isMine());
        }
    }

    /** 光束实体：只在开火 / 充能期间存在；伤害是 DPS 语义，按 0.1 秒间隔结算。 */
    public static void logBeams(CombatEngineAPI engine) {
        for (BeamAPI beam : engine.getBeams()) {
            WeaponAPI weapon = beam.getWeapon();
            DamageAPI damage = beam.getDamage();
            LOG.info("GearIn beam weapon=" + (weapon == null ? "null" : weapon.getId())
                    + " from=(" + beam.getFrom().x + "," + beam.getFrom().y + ")"
                    + " length=" + beam.getLength()
                    + " dps=" + damage.isDps()
                    + " dmg=" + damage.getDamage()
                    + " brightness=" + beam.getBrightness()
                    + " didDamageThisFrame=" + beam.didDamageThisFrame()
                    + " target=" + (beam.getDamageTarget() == null ? "null" : beam.getDamageTarget().getClass().getSimpleName()));
        }
    }
}
```

:::

## 关键故障实验：导弹伤害被报告两次

同时遍历 `getProjectiles()` 和 `getMissiles()` 并将导弹计数两遍。

**局部故障片段：**

```java
// 故障片段
for (DamagingProjectileAPI p : engine.getProjectiles()) { report(p); } for (MissileAPI m : engine.getMissiles()) { report(m); }
```

**局部修正片段：**

```java
// 修正片段
for (DamagingProjectileAPI p : engine.getProjectiles()) { report(p); }
```

复验：发射一枚导弹，日志中只保留一次生命周期记录。

## 验证方案

前置数据：前置知识中的 MOD 布局与战斗插件接入 (含本节注册片段)、六个示例类源码、`GearIn_test` 测试战役 [C4-S1]。

1. **构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/combat/` 下的六个类。
2. **战役冒烟**：进入 `GearIn_test` 一场战斗，在 `Starsector/starsector-core/starsector.log` 核对 `GearIn flow init: damage listeners registered on engine`：
    - 部署玩家舰后观察 `GearIn weapon ...` / `GearIn projectiles: ...` / `GearIn beam ...` 周期报告。
3. **开火流**：用任意弹道武器开火，核对下一帧出现 `GearIn spawn ... spawnType=BALLISTIC fromMissile=false ...`，以及 `GearIn dealt ...` → `GearIn damage ...` 的先后顺序。
4. **集合语义**：发射导弹后核对 `GearIn projectiles:` 的 `total` 与 `missiles` 同步增长，`GearIn missile ...` 行 `armed` 从 false 变 true；光束武器开火时若其弹体是 `spawnType=BEAM` 的射弹，`spawn` 日志同样出现。
5. **光束**：用光束武器照射目标，核对 `GearIn beam ...` 行 `dps=true`、`didDamageThisFrame=true`、`target=Ship`；停火后该行消失 (光束实体只存在于开火 / 充能期间)。
6. **暂停**：按空格暂停，确认周期报告停更、武器 / 射弹 / 光束全部冻结；恢复后报告继续。
7. **数据绑定角色 (可选)**：按数据接入片段自建 `GearIn_test_gun.wpn` / `GearIn_test_shot.proj` 并装到测试舰：
    - 核对 `GearIn weapon init ...` / `GearIn weapon ... started firing (before-frame callback)` / `GearIn onFire ...` / `GearIn onHit ...`。
    - 持续射击时每 2 秒一条 `GearIn weapon ... after-frame: ...`。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/combat/` 下的六个类。
- **初始化**：进入 `GearIn_test` 战役 [C4-S1] 一场战斗，`starsector.log` 出现 `GearIn flow init: damage listeners registered on engine`：
    - 随后 (部署玩家舰后) 每 3 秒出现 `GearIn weapon ...` / `GearIn projectiles: ...` / `GearIn beam ...` 周期报告；没有 `gear_in` 引起的 `ERROR`。
- **开火 → 射弹生成**：武器开火后下一帧出现 `GearIn spawn <specId> spawnType=BALLISTIC fromMissile=false weapon=... source=... damage=... emp=...`：
    - 发射导弹时同一条日志的 `spawnType=MISSILE`(导弹同时计入 `getProjectiles()` 与 `getMissiles()`，周期报告里 `total` 与 `missiles` 一致增长)。
- **集合语义**：`GearIn projectiles:` 行的 `total` 恒大于等于 `missiles`；光束武器开火时，若其弹体是 `spawnType=BEAM` 的射弹，`spawn` 日志同样出现 (光束脉冲也是 `DamagingProjectileAPI`)。
- **伤害监听器**：任意命中/被命中后出现 `GearIn dealt param=... target=... base=... total=... shieldHit=...`(结算前) 与 `GearIn damage source=... target=... hull=... armor=... shields=... emp=...`(结算后)：
    - 顺序固定：先 `dealt` 后 `damage`。
- **导弹状态**：发射导弹后 `GearIn missile ...` 行显示 `armed=false armingTime=...`(装填中)→ 数秒后 `armed=true`；`flight` 稳步增长、`fizzling/fading` 在熄火后变 true；被 EMP 电弧击中的导弹 `empRes=0` 时立即 `fizzling=true`。
- **光束状态**：光束武器开火期间 `GearIn beam ...` 行 `dps=true`、`dmg` 随照射增强、命中目标时 `didDamageThisFrame=true` 且 `target=Ship`；停火后该行消失 (光束实体只存在于开火/充能期间)。
- **数据绑定角色 (接入片段后)**：装好 `GearIn_test_gun.wpn` / `GearIn_test_shot.proj` 并给一艘测试舰装上该武器后：
    - 武器创建时 `GearIn weapon init GearIn_test_gun ...`(`GearInWeaponFlowEffect` 实例收到 `init(WeaponAPI)`)。
    - 开火时 `GearIn weapon GearIn_test_gun started firing (before-frame callback)`(每发前回调)、`GearIn onFire GearIn_test_shot ...`(生成后，`GearInShotOnFireEffect`)。
    - 命中时 `GearIn onHit GearIn_test_shot ...`(`GearInShotOnHitEffect`)；
    - 持续射击时每 2 秒一条 `GearIn weapon GearIn_test_gun after-frame: ...`(操作后回调)。
- **暂停语义**：按空格暂停，暂停期间周期报告停更 (`GearInFlowObserverPlugin.advance` 的暂停判断生效)、武器/射弹/光束全部冻结；恢复后报告继续。

## 故障分析

- **导弹被统计两次**：`getProjectiles()` 已包含导弹和光束脉冲，`getMissiles()` 是导弹子集；叠加两个集合会让计数虚高。

    - **解决方案**：总数只使用 `getProjectiles()`，需要导弹字段时再查 `getMissiles()`。

- **武器数值在所有同 ID 实例间漂移**：直接调用 `weapon.getSpec().setMaxAmmo(...)` 会修改共享 spec，并跨战斗影响同类武器。

    - **解决方案**：先调用 `ensureClonedSpec()` 切到私有克隆，再修改。

- **`onFire` 或 `onHit` 方法没有执行**：类实现接口并不等于数据实例已绑定，`.proj` 缺少 `onFireEffect` 或 `onHitEffect` 时方法会静默失效。

    - **解决方案**：填入完整类名并重启；若类名错误，日志会出现 `Problem loading class [...]`，据此修正。

- **导弹命中目标却没有伤害**：导弹可能仍在 `armingTime` 内，熄火后 `armedWhileFizzling=false`，或淡出期间 `reduceDamageWhileFading` 把伤害降到接近 0。

    - **解决方案**：核对 spec 字段和命中时刻，用 `MissileAPI.isArmed()` 逐帧确认，在已装填且未淡出时命中。

- **EMP 电弧命中后导弹立即熄火**：默认 `empResistance=0` 时，非 EMP 伤害的 EMP 电弧会直接调用 `flameOut()`，导弹失去制导并坠落。

    - **解决方案**：需要抗性时设置 `setEmpResistance(n)`。

- **光束逻辑继续使用上一帧目标**：把 `beam.getDamageTarget()` 存进字段跨帧后，旧目标即使死亡或离开射线，本帧仍会被处理。

    - **解决方案**：只在 `BeamEffectPlugin.advance` 当前帧读取 `didDamageThisFrame` 和 `getDamageTarget()`。

- **伤害修改不生效或残留到下一次攻击**：直接调用 `damage.setDamage(x)`，或返回的 modifier ID 与实际修改 ID 不一致，会绕过引擎的应用和还原流程。

    - **解决方案**：只使用 `damage.getModifier().modify*(id, ...)` 并返回同一 ID。

- **射弹按预期命中发射舰**：正常射弹不会命中 source，只有 `HITS_SHIPS_AND_ASTEROIDS` 碰撞类、熄火导弹和 `MISSILE_FF` 爆炸弹等豁免路径可能回砸。

    - **解决方案**：先确认碰撞类和 `DamagingProjectileAPI.getSource()`，需要回砸时明确使用相应豁免。

## 总结

本节把 "武器触发 → 射弹 / 光束" 的对象流走了一遍：`fireShot` 按固定次序生成射弹，射弹经历生成、飞行、命中与淡出，导弹在生命周期之上叠加装填、熄火、EMP 抗性与制导，光束则按 DPS 语义以 0.1 秒间隔结算。贯穿全页的判断方法是先问两个问题：这段逻辑读的是哪个集合，结果在什么时机生效。集合上，`getProjectiles()` 已包含导弹与光束脉冲，`getMissiles()` 只是子集，`getBeams()` 只含光束武器实体；时机上，`didDamageThisFrame()` 与 `getDamageTarget()` 只对本帧有效，回调参数只属于本次事件。

排查时从注册与绑定点开始核对，比对着现象猜测更快：数据绑定类名没写进 `.proj` / `.wpn` 是第一嫌疑 (静默失效、无报错)，缓存了本帧命中状态是第二嫌疑，叠加统计两个集合排第三。六条必须遵守的规则是本节的行为底线：改武器数值前 `ensureClonedSpec()`、回调上下文禁止缓存、射弹不命中 source、导弹派生射弹必须 `setFromMissile(true)`、光束命中状态只对本帧有效、伤害修改只能走 `getModifier()` 并返回 id。

本节建立的监听器与数据绑定模型，同样适用于伤害联动、光束视觉效果与自定义导弹 AI；AI 侧的开火决策与光束充能曲线也可沿这条对象流继续分析。

## 建议参考项

- `com.fs.starfarer.api.combat.WeaponAPI`：武器运行时接口 —— `getSpec` / `ensureClonedSpec` / `getOriginalSpec`、弹药 / 冷却 / 充能 / 开火状态、`getBeams` / `getEffectPlugin` / `getDerivedStats`。
- `com.fs.starfarer.api.combat.DamagingProjectileAPI`：射弹运行态接口：
    - `getSource`(射弹不能命中 source)、`setFromMissile`、`didDamage` / `getDamageTarget`。
    - `isFading`、`getSpawnType`、`getWeapon`(可为 null)、`getTailEnd`。
- `com.fs.starfarer.api.combat.MissileAPI`：导弹运行态：
    - `isArmed` / `getArmingTime`、`flameOut` / `isFizzling`、`getEmpResistance` / `setEmpResistance`。
    - `isGuided` / `isFlare`、`getFlightTime` / `getMaxFlightTime` / `getMaxRange`。
    - `giveCommand`(仅 `MissileAIPlugin` 可用)、`isMine`。
- `com.fs.starfarer.api.combat.BeamAPI`：光束武器实体：
    - `getFrom` / `getTo` / `getLength` / `getBrightness`
    - `didDamageThisFrame` / `getDamageTarget`(只对本帧有效)；
    - `getRayEndPrevFrame` / `getLengthPrevFrame`、`getDamage`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：战斗引擎入口 —— `getProjectiles`(含导弹)、`getMissiles`、`getBeams`、`spawnProjectile`、`getListenerManager`。
- `com.fs.starfarer.api.combat.ShipAPI`：`getAllWeapons`、`getListenerManager`。
- `com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI`：监听器注册表 —— `addListener` / `removeListener` / `removeListenerOfClass` / `hasListener` / `getListeners`。
- `com.fs.starfarer.api.combat.listeners.DamageDealtModifier` / `DamageTakenModifier` / `DamageListener` / `ApplyDamageResultAPI`：伤害事件监听器族：
    - 挂造成伤害方 / 承受伤害方舰船 (能挂舰船就不挂引擎)；修改只能走 `damage.getModifier()` 并返回 id。
    - 结果对象含 `getDamageToHull` / `getTotalDamageToArmor` / `getDamageToShields` / `getEmpDamage`。
- `com.fs.starfarer.api.combat.listeners.CombatListenerUtil`：引擎级与舰船级监听器的查找与调用顺序。
- `com.fs.starfarer.api.combat.OnFireEffectPlugin` / `OnHitEffectPlugin`：`.proj` 数据绑定的一次性回调 —— `onFire` / `onHit` 参数只在本回调内使用。
- `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin` / `EveryFrameWeaponEffectPluginWithAdvanceAfter` / `WeaponEffectPluginWithInit`：武器逐帧效果接口族：
    - 操作前 `advance` / 操作后 `advanceAfter` / 实例初始化 `init(WeaponAPI)`。
- `com.fs.starfarer.api.combat.BeamEffectPlugin` / `BeamEffectPluginWithReset`：光束逐帧效果与实体创建 / 销毁重置。
- `com.fs.starfarer.api.combat.MissileAIPlugin` / `GuidedMissileAI`：导弹每帧导航 —— 不实现 `GuidedMissileAI` 的自定义 AI 不受 flare 影响。
- `com.fs.starfarer.api.combat.DamageAPI`：单次伤害对象 —— `getModifier`(`MutableStat`)、`getBaseDamage`、`isDps`、`getDpsDuration`、`setSoftFlux`。
- `com.fs.starfarer.api.loading.MissileSpecAPI`：导弹数据 spec：
    - `getArmingTime` / `getFlameoutTime` / `getAfterFlameoutCollisionClass` / `getDudProbabilityOnFlameout`
    - `isFizzleOnReachingWeaponRange` / `isNoCollisionWhileFading` / `isReduceDamageWhileFading` / `isUseProjectileRangeCalculation`
    - `getOnFireEffect` / `getOnHitEffect`
- `com.fs.starfarer.api.loading.WeaponSpecAPI`：武器数据 spec —— `getWeaponId` / `getProjectileSpec` / `getMaxRange`。
- `data/weapons/riftbeam.wpn`：原版 `everyFrameEffect` 键实例。
- `data/weapons/proj/canister_flak.proj`：原版 `onFireEffect` 键实例。
- `data/weapons/proj/assaying_rift_proj.proj`：原版 `onHitEffect` 键实例。
- `data/weapons/annihilator.wpn`：`barrelMode` / `projectileSpecId` 键实例。
- `data/weapons/proj/annihilator_rocket.proj`：`specClass:"missile"` 实例。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。
