# 为武器加入动画与效果回调

本节为武器加入动画与开火、命中、光束效果回调。效果类只有通过武器或射弹数据完成绑定才会被引擎调用，单独实现接口不会产生效果。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

* 区分 `.wpn` 的 `everyFrameEffect` / `beamEffect` 与 `.proj` 的 `onFireEffect` / `onHitEffect` 四个注册列的使用方与实例模型：每武器、每光束射线、每射弹独立实例，效果类必须可无参实例化；
* 解释弹丸与光束武器上 `EveryFrameWeaponEffectPlugin.advance` 的调用时机 (本帧转向 / 开火之前)、`advanceAfter`(本帧操作之后)、`WeaponEffectPluginWithInit.init`(武器构造时)，并验证暂停时这些回调全部停摆；
* 判断武器贴图动画的播放条件 (开火 / 充能 / `alwaysAnimate` 才推进、`animateWhileFiring:false` 时开火中冻结、`frameRate` 为帧 / 秒、帧环绕播放)，并列出帧贴图命名规则 (基路径以 `00.png` 结尾、帧文件顺序编号)；
* 使用 `weapon.getAnimation()` 的 `AnimationAPI`(`play` / `pause` / `setFrame` / `setFrameRate`) 程序化控制动画，并说明两条渲染约束：`getAnimation()` 对非动画武器返回 null、`getSprite()` 上设置的 alpha 与角度会被渲染前覆盖；
* 区分 `animationType` 的取值 (`NONE` / `GLOW` / `MUZZLE_FLASH` / `GLOW_AND_FLASH` / `SMOKE`) 与 `muzzleFlashSpec` / `smokeSpec` / `visualRecoil` 的角色、触发条件与可观察结果；
* 解释 `BeamEffectPlugin` 的每帧回调与 `BeamEffectPluginWithReset.reset()` 的调用窗口 (射线创建与销毁)，并只消费本帧光束状态 (`didDamageThisFrame` / `getDamageTarget`)；
* 用 `starsector.log` 与视口粒子从症状定位本节列出的八类典型故障。

## 前置知识

* [C1-S5] 的武器 CSV、`.wpn`、`.proj` 数据关系与注册字段；
* [C6-S1] 的 Java 构建与 JAR 输出；
* [C6-S3] 的 `Global` 生命周期与跨战斗引用边界；
* [C7-S1] 的战斗插件注册、实例区分与暂停语义；
* [C7-S3] 的 `MutableStat` 与 `damage.getModifier()` 返回值；
* [C7-S4] 的武器开火流、射弹生命周期与光束 DPS 语义。

## 梳理武器效果关系

先确认四个注册列与动画、视觉对象各落在哪个接口，再进入字段与时机。本节涉及的接口在 `com.fs.starfarer.api.combat`(`AnimationAPI` 在 `com.fs.starfarer.api`，数据对象在 `com.fs.starfarer.api.loading`)：

接口 / 类型

本节角色

注册列 / 获取入口

`EveryFrameWeaponEffectPlugin`

武器逐帧效果 (操作前回调)

`.wpn` `everyFrameEffect`

`EveryFrameWeaponEffectPluginWithAdvanceAfter`

武器逐帧效果 (操作后追加回调)

同上 (类实现即生效)

`WeaponEffectPluginWithInit`

武器效果构造回调

同上 (类实现即生效)

`BeamEffectPlugin`

光束逐帧效果

`.wpn` `beamEffect`(仅光束武器)

`BeamEffectPluginWithReset`

光束效果创建 / 销毁复位

同上 (类实现即生效)

`OnFireEffectPlugin`

开火一次性回调

`.proj` `onFireEffect`

`OnHitEffectPlugin`

命中一次性回调

`.proj` `onHitEffect`

`AnimationAPI`

武器贴图动画控制

`weapon.getAnimation()`(非动画武器返回 null)

`MuzzleFlashSpec`

炮口粒子参数

`.wpn` `muzzleFlashSpec`(`ProjectileWeaponSpecAPI.getMuzzleFlashSpec()`)

`smokeSpec` 数据对象

开火烟尘参数

`.wpn` `smokeSpec` 键

`ProjectileWeaponSpecAPI` / `BeamWeaponSpecAPI` / `ProjectileSpecAPI` / `MissileSpecAPI`

spec 读取侧

`weapon.getSpec()` 或按 id 读取 spec 的加载期存储

`DamageAPI`

光束 / 射弹伤害对象

`beam.getDamage()` / `projectile.getDamage()`[C7-S4] / [C7-S3]

### .wpn / .proj 注册列 (数据挂载点)

数据文件

键

原版实例

使用方

`data/weapons/*.wpn`

`everyFrameEffect`

`riftbeam.wpn` 的 `com.fs.starfarer.api.impl.combat.RiftBeamEffect`；`blinker_red.wpn` 的 `BlinkerEffect`

弹丸武器构造器 / 光束武器构造器

`data/weapons/*.wpn`

`beamEffect`

`ionbeam.wpn` 的 `com.fs.starfarer.api.impl.combat.IonBeamEffect`

光束射线构造器

`data/weapons/proj/*.proj`

`onFireEffect`

`canister_flak.proj`

武器构造期缓存，开火时回调

`data/weapons/proj/*.proj`

`onHitEffect`

`assaying_rift_proj.proj`

射弹构造期缓存，命中时回调

加载期：四个键都在加载武器 / 弹体 spec 时把类名登记给脚本类加载器，类在首次被实例化时才真正加载。

### .wpn 动画与视觉字段

加载器读取 (缺省值即默认)：

`.wpn` 键

默认

语义

`numFrames`

1

帧数；**大于 1 时武器才是动画武器** (`isAnimated`)

`frameRate`

1

播放帧率 (帧 / 秒)

`alwaysAnimate`

false

true 时闲置也持续播放

`animateWhileFiring`

true

false 时开火中冻结动画 (约 0.001 倍速)

`animationType`

NONE

弹丸武器开火视觉：`NONE` / `GLOW` / `MUZZLE_FLASH` / `GLOW_AND_FLASH` / `SMOKE`

`visualRecoil`

0

非 0 时启用炮管贴图 (`turretGunSprite` / `hardpointGunSprite`) 与后坐位移

`muzzleFlashSpec`

—

`animationType` 为 `MUZZLE_FLASH` / `GLOW_AND_FLASH` 时的炮口粒子参数

`smokeSpec`

—

`animationType` 为 `SMOKE` 时的烟尘参数

`turretGlowSprite` / `hardpointGlowSprite`

—

`GLOW` / `GLOW_AND_FLASH` 的开火闪光贴图

`turretGunSprite` / `hardpointGunSprite`

—

`visualRecoil` 非 0 时的炮管 (barrel) 贴图

原版实例：

* `chaingun.wpn`(`numFrames:4`、`frameRate:20`、`animationType:"MUZZLE_FLASH"`、`muzzleFlashSpec` 完整对象)。
* `heavyac.wpn`(`visualRecoil:10.0`)；
* `amblaster.wpn`(`animationType:"GLOW_AND_FLASH"`)；
* `blinker_red.wpn`(`numFrames:2`、`frameRate:1`、`alwaysAnimate:"false"`，配 `BlinkerEffect` 人工切帧)。

## 武器贴图动画

注册列回答了 "哪个类被调用"，动画数据回答 "贴图怎么动"。

### 数据驱动动画：四个字段的播放条件

动画武器 (`numFrames > 1`) 的帧由引擎每帧推进 (弹丸与光束武器同构)，推进条件：

1. **只有开火、充能 (charge state 非 IDLE) 或 `alwaysAnimate:true` 时动画才走帧**；其余时间停在当前帧 (通常 0 帧)。
2. `animateWhileFiring:false` 时，开火中动画几乎冻结 (时间倍率 0.001)。
3. `burstDelay > 0` 的连射武器在冷却超过 burstDelay 且不在 burst 内时完全冻结 (倍率 0)。
4. 帧循环环绕：`currFrame += 时间 × frameRate`，超过 `numFrames` 回绕 (`Animation.advance`)。
5. 动画只在视口附近且武器未隐藏时推进。

`frameRate` 是 "帧 / 秒" 的播放速率，帧间切换由引擎驱动 —— 效果类无需自己数帧。

### 帧贴图命名规则

动画帧文件由基贴图路径推导：基路径 (`turretSprite` / `hardpointSprite`) **必须以 `00.png` 结尾**，帧 N 的路径 = 去掉 `00.png` 后拼接 `%02d.png`。原版 `chaingun.wpn` 的 `numFrames:4` 对应：

```text
graphics/weapons/rotary/rotary_cannon_turret_base00.png   # 帧 0
graphics/weapons/rotary/rotary_cannon_turret_base01.png   # 帧 1
graphics/weapons/rotary/rotary_cannon_turret_base02.png   # 帧 2
graphics/weapons/rotary/rotary_cannon_turret_base03.png   # 帧 3
```

帧贴图在**武器实例构造时**逐帧加载；基路径不以 `00.png` 结尾时帧 0 路径推导错位 (替换不命中，帧文件拼出错误文件名)，帧文件缺失则在武器创建阶段加载失败 —— 部署舰船时即报错，不会延迟到渲染。命名规则是必须遵守的 (规则 3)。

### AnimationAPI 程序化控制

`weapon.getAnimation()` 返回 `AnimationAPI`(**非动画武器返回 null**，`com.fs.starfarer.api.combat.WeaponAPI` javadoc 原文 "null for non-animated weapons")，提供：

* 帧读写：`getFrame()` / `setFrame(int)` / `getNumFrames()`；
* 播放控制：`play()`(置 playing 标志，闲置时也继续走帧)/ `pause()` / `reset()`；
* 速率：`getFrameRate()` / `setFrameRate(float)`(可变速)；
* 透明度：`getAlphaMult()` / `setAlphaMult(float)`。

引擎的自动推进受 `Animation` 的 `paused` 与 `playing` 双重影响：`pause()` 后引擎不再推进 (包括开火 / 充能期间)；`play()` 使闲置武器也持续循环。

效果类可以用 `setFrame` 直接定格 (原版 `BlinkerEffect` 每帧按舰船状态把导航灯切到 0 / 1 帧)，也可以用 `play` / `pause` 覆盖数据字段的默认播放节奏。

两条渲染约束 (`WeaponAPI.getSprite()` javadoc 原文)：

* 渲染前引擎会在 `getSprite()` 返回的贴图上调用 `setAlphaMult()` 与 `setAngle()`—— 在这张贴图上设置这两项**无效** (会被覆盖)。改透明度要用 `SpriteAPI.setColor()` 的 alpha 通道，改朝向要用 `WeaponAPI.setCurrAngle()`。

## 内置视觉：animationType、visualRecoil 与炮口粒子

动画解决帧的推进，开火瞬间的光、烟与炮管后坐由内置视觉字段承担。弹丸武器开火时，在视口 100 单位内且舰船允许炮口闪光 (`ShipAPI.isNoMuzzleFlash()` 为 false) 时按 `animationType` 分派：

`animationType`

开火视觉

数据来源

`NONE`

无内置粒子

—

`GLOW`

glow 贴图按充能时间一半淡出闪烁

`turretGlowSprite` / `hardpointGlowSprite`、`glowColor`

`MUZZLE_FLASH`

炮口粒子爆发

`muzzleFlashSpec`

`GLOW_AND_FLASH`

两者

上述两者

`SMOKE`

烟云 + 后喷烟

`smokeSpec`

`muzzleFlashSpec` 字段 (`com.fs.starfarer.api.loading.MuzzleFlashSpec`)：

* `length`(粒子射程)；
* `spread`(散度，度)；
* `particleSizeMin` / `particleSizeRange`(粒子尺寸区间)；
* `particleDuration`(寿命，秒)；
* `particleCount`(数量；`<= 0` 生成空 spec)；
* `particleColor`。

`smokeSpec` 字段 (`.wpn` 数据键)：

* `particleSizeMin` / `particleSizeRange`；
* `cloudParticleCount` / `cloudDuration` / `cloudRadius`(烟云)；
* `blowbackParticleCount` / `blowbackDuration` / `blowbackLength` / `blowbackSpread`(后喷)；
* `particleColor`。

`visualRecoil` 非 0 且基贴图存在时启用炮管贴图与后坐位移：`turretGunSprite` / `hardpointGunSprite` 作为炮管层渲染。

炮管层渲染用到 `weapon.getBarrelSpriteAPI()` / `renderBarrel` / `isRenderBarrelBelow()`。

开火后炮管沿后坐方向位移并复位；数值越大位移越明显 (原版 `heavyac.wpn` 用 10.0)。

注意 `visualRecoil` 是视觉后坐，与散布相关的实际后坐由 `minSpread` / `maxSpread` / `spreadBuildup` / `spreadDecayRate` 控制，两者独立。

## 效果回调：注册列 → 实例 → 时机

数据字段与渲染字段已经看完，现在回到四个注册列本身：实例何时创建、每帧何时被调用。

为什么每个注册列都要独立实例化

四个注册列各自创建实例，根源在脚本类加载器的实例化行为：类名只作工厂键，每次调用都创建新实例。于是 "同一把武器绑多个类名" 或 "同一类名绑多个注册列" 得到的都是独立实例 —— 实例归属完全由绑定位置决定，类实现了接口不等于实例被引擎调用。

### everyFrameEffect(弹丸与光束通用)

实例与时机 (弹丸武器；光束武器同构)：

1. **武器构造时**：`.wpn` 登记的 `everyFrameEffect` 类名由脚本类加载器实例化 (**每次创建新实例**)→ 每把武器实例独有一个效果对象；若实现 `WeaponEffectPluginWithInit`，紧接着调用 `init(weapon)`。多艘舰、多把同 id 武器的效果对象互不相干。
2. **每帧**：`advance(amount, engine, weapon)` 在本帧转向、开火等操作**之前**调用 (API javadoc 原文 "Called before this frame's operations for the weapon - turning, firing, etc.")；本帧操作完成后，若实现 `EveryFrameWeaponEffectPluginWithAdvanceAfter` 再调 `advanceAfter`。
3. **暂停时武器不推进** → 两个回调全部停摆 ([C7-S1] / [C7-S4] 的暂停语义；与战斗插件 `advance` 暂停时仍被调用不同)。
4. 若该效果类同时实现 `OnFireEffectPlugin`，开火时**同一个实例**还会被调一次 `onFire`——"一类多角色、一武器一实例" 是原版常用做法 (`RiftBeamEffect` 用 everyFrameEffect 在光束武器上轮询 `weapon.getBeams()` 生成裂隙；`RealityDisruptorEffect` 一个类同时是 everyFrameEffect / onFireEffect / onHitEffect，见 "同实例多角色")。

### beamEffect(光束武器专属)

* 注册列 `beamEffect` 只在 `specClass: beam` 的 `.wpn` 上被读取；写到弹丸武器 `.wpn` 上**静默不生效**。
* 实例：每条光束射线实体构造时调 `getSpec().getBeamEffect()` → **每条射线独立实例**；实现 `BeamEffectPluginWithReset` 的实例在射线创建与销毁时收到 `reset()`—— 跨射线状态在这里复位。
* 每帧调用：射线在开火 / 充能期间存在，光束武器每帧对每条射线调一次 `advance(amount, engine, beam)`。调用点在端点更新前后的位置随命中与否变化：**射线被目标截短 (命中) 时在更新 `to` 端点之后调用，未命中时在更新之前调用** —— 效果里读 `beam.getTo()` 的语义随命中状态不同，命中时读到本帧截短端点，未命中时读到上一帧端点。
* 光束伤害是 DPS 语义按 0.1 秒结算 [C7-S4]；`beam.getDamage().getDpsDuration()` 在结算帧返回累计时长、其余帧为 0—— 原版 `IonBeamEffect` 用 `dpsDuration` 推进 `IntervalUtil`(并处理快速时间下的 `wasZero` 边沿)，使特效节奏跟随真实伤害结算。
* `didDamageThisFrame()` / `getDamageTarget()` 只对本帧结算语义有效 ([C7-S4] 规则 5 同规)：光束逐帧可能切换命中目标，禁止把上一帧目标缓存跨帧使用。

### onFireEffect / onHitEffect(.proj)

* 注册列：`onFireEffect` / `onHitEffect` 在 projectile 与 missile 两类 `.proj` 上都被读取。
* 实例：`onFireEffect` 在**武器构造时**缓存到武器对象 (每武器一个实例)；`onHitEffect` 在**射弹构造时**缓存 (每射弹一个实例)。
* 时机：`onFire` 在 `fireShot` 生成射弹之后立即调用，参数是刚生成的那一颗射弹 (弹药不足时不生成射弹、也不回调 ——[C7-S4])；`onHit` 在命中伤害结算 (`notifyDealtDamage`) 时调用，`shieldHit` 与 `ApplyDamageResultAPI` 是本次结算上下文。弹道弹穿行时默认不触发 `onHit`，`.proj` 的 `applyOnHitEffectWhenPassThrough:true` 可改为穿行也触发。
* 参数都是 "本次事件上下文"，**禁止跨发 / 跨帧缓存** ([C7-S4] 规则 2 同规)。

### 同实例多角色 (RealityDisruptorEffect 模式)

原版 `RealityDisruptorEffect`(`com.fs.starfarer.api.impl.combat.RealityDisruptorEffect`) 同时实现三个效果接口：

* `EveryFrameWeaponEffectPlugin`
* `OnFireEffectPlugin`
* `OnHitEffectPlugin`

类注释原文："will be multiple instances of this, as this doubles as the every frame effect and the on fire effect (same instance)"。

要点：

* `.wpn` 的 `everyFrameEffect` 与 `.proj` 的 `onFireEffect` / `onHitEffect` 可以指向**同一个类名**，但每个注册列各创建自己的实例；
* 同一把武器上，"每武器实例" 同时收到 `advance`(每帧) 与 `onFire`(每次开火)—— 两个回调共享该实例的字段，是跨回调传状态的合法窗口；
* 反之，`.proj` 绑定产生的 `onHit` 实例与武器效果实例是不同对象 —— 跨角色传状态必须另找通道 (如 `weapon.setCustom(...)` / `getCustom()`)。

## 必须遵守的规则

从实例模型与回调时机中提炼出的约束集中在这里，本节示例逐条对应。

1. **效果类必须可无参实例化**。所有效果注册列都由脚本类加载器按类名创建实例 (要求无参构造器)。违反的可观察结果：绑定的类缺失、类名写错或无无参构造器时，武器 / 射线 / 射弹创建点抛 `Problem loading class [...]`(`RuntimeException`)—— 部署舰船即报错，或武器 / 射弹创建失败。
2. **回调参数只在本回调内消费，禁止跨发 / 跨帧 / 跨实例缓存**。`onFire` 的 `projectile`、`onHit` 的 `target` / `damageResult`、光束的 `didDamageThisFrame` / `getDamageTarget` 都只在对应回调与对应帧有效 (与 [C7-S4] 规则 2、4 同一条纪律)。违反的可观察结果：把上一帧目标当本帧目标继续操作 —— 对已死实体施加逻辑；或跨发射复用射弹引用 —— 旧引用失效或被下一次覆盖。
3. **动画武器帧贴图必须按命名规则放置**：基贴图路径以 `00.png` 结尾，帧文件按 `00, 01, ..., N-1` 顺序编号。违反的可观察结果：基路径不以 `00.png` 结尾时帧 0 路径推导错位 (拼出错误文件名)，帧文件缺失在武器构造时加载失败 —— 部署舰船即报错。
4. **`weapon.getAnimation()` 必须判空**。只有 `numFrames > 1` 的武器有动画对象 (`com.fs.starfarer.api.combat.WeaponAPI` javadoc 原文 "null for non-animated weapons")。违反的可观察结果：对静态贴图武器调用 `getAnimation()` 直接 NPE。
5. **不得在 `getSprite()` 返回的贴图上设置 alpha 与角度**。渲染前引擎会调用 `setAlphaMult()` / `setAngle()` 覆盖 (`WeaponAPI.getSprite()` javadoc 原文)。改透明度用 `SpriteAPI.setColor()` 的 alpha 通道、改朝向用 `WeaponAPI.setCurrAngle()`。违反的可观察结果：设置值被覆盖，视觉无变化 (静默失效)。
6. **`beamEffect` 只对光束武器生效**。注册列只在 `specClass: beam` 时被读取。违反的可观察结果：写在弹丸 `.wpn` 上完全无效，无任何报错 (静默)—— 回调不执行。
7. **修改武器数值前必须 `ensureClonedSpec()`**([C7-S4] 规则 1 同规)；效果类如需改伤害只能走 `damage.getModifier()`([C7-S4] 规则 6)。本节示例只观察并生成纯视觉粒子，不改数值；违反的可观察结果见开火流单元 [C7-S4]。

## 实现武器动画与回调

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 识别动画武器

```java
// 插入位置：GearInWeaponFrameEffect.init()
AnimationAPI animation = weapon.getAnimation();
LOG.info("GearIn weapon fx init " + weapon.getId()
        + " beam=" + weapon.isBeam()
        + " animated=" + (animation != null)
        + (animation != null
                ? " frames=" + animation.getNumFrames() + " rate=" + animation.getFrameRate()
                : ""));
```

新增行为：武器创建时区分动画贴图与静态贴图，并报告帧数和帧率。完成信号：转轮武器显示 `animated=true frames=4 rate=12.0`，静态光束武器显示 `animated=false`，且不发生空指针。

### 在操作前控制动画

```java
// 插入位置：GearInWeaponFrameEffect.advance()
AnimationAPI animation = weapon.getAnimation();
if (animation != null) {
    boolean idle = weapon.getChargeLevel() <= 0f
            && weapon.getCooldownRemaining() <= 0f
            && !weapon.isFiring();
    if (idle) {
        animation.pause();
        if (!idleFrameApplied) {
            animation.setFrame(0);
            idleFrameApplied = true;
        }
    } else {
        animation.play();
        idleFrameApplied = false;
    }
}
```

新增行为：充能或开火时播放动画，完全闲置后只执行一次暂停并归零。完成信号：开火时帧号循环，冷却结束后稳定停在第 0 帧。

### 在操作后读取弹药与冷却

```java
// 插入位置：GearInWeaponFrameEffect.advanceAfter()
if (weapon.isFiring()) {
    afterReportInterval.advance(amount);
    if (afterReportInterval.intervalElapsed()) {
        LOG.info("GearIn weapon fx " + weapon.getId() + " after-frame:"
                + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                + " cooldown=" + weapon.getCooldownRemaining());
    }
}
```

新增行为：开火核心操作完成后，每秒读取一次已经扣减的弹药和当前冷却。完成信号：`before-frame callback` 先出现，随后 `after-frame` 中的弹药数量反映本帧开火结果。

### 复位并观察光束命中

```java
// 插入位置：GearInBeamHitEffect.reset() 与 advance()
lastDidDamage = false;

boolean didDamage = beam.didDamageThisFrame();
CombatEntityAPI target = beam.getDamageTarget();
if (didDamage && !lastDidDamage) {
    LOG.info("GearIn beam fx hit start: target="
            + (target == null ? "null" : target.getClass().getSimpleName()));
}
if (didDamage) {
    sparkInterval.advance(amount);
    if (sparkInterval.intervalElapsed()) {
        Vector2f point = beam.getRayEndPrevFrame();
        engine.addHitParticle(point, new Vector2f(),
                6f, 1f, 0.4f, new Color(130, 200, 255, 200));
    }
}
lastDidDamage = didDamage;
```

新增行为：每条射线创建时清空边沿状态，只在本帧命中期间按 0.15 秒节奏生成火花。完成信号：首次命中出现一条 `hit start`，持续照射产生端点粒子，停火后的新射线重新开始计数。

### 记录刚生成的射弹

```java
// 插入位置：GearInOnFireEffect.onFire()
LOG.info("GearIn onFire " + projectile.getProjectileSpecId()
        + " weapon=" + (weapon == null ? "null" : weapon.getId())
        + " spawnType=" + projectile.getSpawnType()
        + " speed=" + projectile.getVelocity().length());
```

新增行为：每次成功生成射弹后记录弹体、武器、生成类型和初速。完成信号：弹药充足时每发一条日志；弹药耗尽后不生成射弹，也不触发该回调。

### 记录本次命中结算

```java
// 插入位置：GearInOnHitEffect.onHit()
LOG.info("GearIn onHit " + projectile.getProjectileSpecId()
        + " target=" + target.getClass().getSimpleName()
        + " shieldHit=" + shieldHit
        + " hull=" + damageResult.getDamageToHull()
        + " armor=" + damageResult.getTotalDamageToArmor()
        + " shields=" + damageResult.getDamageToShields()
        + " emp=" + damageResult.getEmpDamage());
```

新增行为：命中回调把本次伤害在船体、装甲、护盾和 EMP 之间的分配写入日志。完成信号：命中护盾与命中装甲时各列出现不同结果，参数不会被保存到下一发射弹。

## 组装武器效果实现

规则需要落到可编译的类上。在编译 JAR 链路的 MOD 布局 [C6-S1] 与武器开火流单元 [C7-S4] 的 `GearInWeaponFlowEffect` 之上新增四个 Java 类，本节不新增任何资源文件。

`.wpn` / `.proj` 接入片段与帧贴图清单见下文，仅演示挂载点，本节不创建数据文件。

包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearIn<Role>`)。

四个类按注册列一一对应：

类

所有权

本节关系

`GearInModPlugin`

[C6-S2]

本节不改动

`GearInGlobalAccess`

[C6-S3]

本节只消费

`GearInCombatStatePlugin`

[C7-S2]

本节只消费

`GearInWeaponFlowEffect`

[C7-S4]

本节只消费

`GearInWeaponFrameEffect`

本节

新增 (`.wpn` `everyFrameEffect`)

`GearInBeamHitEffect`

本节

新增 (`.wpn` `beamEffect`，仅光束武器)

`GearInOnFireEffect`

本节

新增 (`.proj` `onFireEffect`)

`GearInOnHitEffect`

本节

新增 (`.proj` `onHitEffect`)

### GearInWeaponFrameEffect(`.wpn` everyFrameEffect)

武器逐帧效果，绑定 `everyFrameEffect` 后每把武器一个实例：

* `init` 报告武器是否动画化及帧数 / 帧率；
* `advance`(操作前) 用 `AnimationAPI` 程序化控制贴图动画 (闲置冻结回 0 帧、否则 `play()`) 并按帧变化打印；
* `advanceAfter`(操作后) 按 1 秒节奏打印弹药 / 冷却。

### GearInBeamHitEffect(`.wpn` beamEffect，仅光束武器)

光束效果，绑定 `beamEffect` 后每条光束射线一个实例：`reset` 在射线创建与销毁时各调一次 (跨射线状态在这里复位)；`advance` 每帧只消费本帧命中状态 (`didDamageThisFrame` / `getDamageTarget`)。

命中时按 0.15 秒节奏在上一帧射线端点 (`getRayEndPrevFrame()`) 撒纯视觉火花 (`addHitParticle`)，命中边沿打日志。

### GearInOnFireEffect(`.proj` onFireEffect)

开火回调，绑定 `onFireEffect` 后每武器一个实例：武器开火生成射弹后回调一次，参数是刚生成的那一颗射弹 (速度 / 朝向已就绪)，打印弹体 id、发射武器、`spawnType` 与弹速 —— 只观察不改数值，参数禁止缓存跨发射使用。

### GearInOnHitEffect(`.proj` onHitEffect)

命中回调，绑定 `onHitEffect` 后每射弹一个实例：射弹命中结算时回调一次，打印命中结算结果 (`ApplyDamageResultAPI` 的 hull / armor / shields / emp)—— 参数是本次结算上下文，禁止缓存跨命中使用。

四个类的全部字段都是实例字段：每武器 / 每射线 / 每射弹实例各有一份，跨实例互不干扰；跨战斗实例全部重建，禁止跨战斗缓存 ([C6-S3] 纪律)。

### 数据接入片段

(工程片段 —— 本节不创建数据文件；接入后四个效果类才生效。以下 ID 由本节声明，路径在 `Starsector\mods\GearIndustry\` 下自洽)：

```text
data\weapons\GearIn_test_rotor.wpn         # 弹丸武器：GearInWeaponFrameEffect（everyFrameEffect）+ 动画 / 内置视觉字段
data\weapons\GearIn_test_beam.wpn          # 光束武器：GearInWeaponFrameEffect（everyFrameEffect）+ GearInBeamHitEffect（beamEffect）
data\weapons\proj\GearIn_test_shot.proj    # 弹体 spec：GearInOnFireEffect / GearInOnHitEffect（onFireEffect / onHitEffect）
graphics\weapons\gearin\GearIn_test_rotor_turret_base00.png     # 帧 0（您自备，命名规则见"帧贴图命名规则"）
graphics\weapons\gearin\GearIn_test_rotor_turret_base01.png     # 帧 1
graphics\weapons\gearin\GearIn_test_rotor_turret_base02.png     # 帧 2
graphics\weapons\gearin\GearIn_test_rotor_turret_base03.png     # 帧 3
graphics\weapons\gearin\GearIn_test_rotor_hardpoint_base00.png  # 帧 0
graphics\weapons\gearin\GearIn_test_rotor_hardpoint_base01.png  # 帧 1
graphics\weapons\gearin\GearIn_test_rotor_hardpoint_base02.png  # 帧 2
graphics\weapons\gearin\GearIn_test_rotor_hardpoint_base03.png  # 帧 3
graphics\weapons\gearin\GearIn_test_beam_turret_base.png        # 光束武器基贴图
graphics\weapons\gearin\GearIn_test_beam_hardpoint_base.png     # 光束武器基贴图
graphics\weapons\gearin\GearIn_test_shot.png                    # 弹体贴图
```

```json
// GearIn_test_rotor.wpn（片段：弹丸武器，绑定 GearInWeaponFrameEffect；动画与内置视觉字段全开）
{
    "id": "GearIn_test_rotor",
    "specClass": "projectile",
    "type": "BALLISTIC",
    "size": "MEDIUM",
    "turretSprite": "graphics/weapons/gearin/GearIn_test_rotor_turret_base00.png",
    "hardpointSprite": "graphics/weapons/gearin/GearIn_test_rotor_hardpoint_base00.png",
    "numFrames": 4,
    "frameRate": 12,
    "alwaysAnimate": false,
    "animateWhileFiring": true,
    "animationType": "GLOW_AND_FLASH",
    "muzzleFlashSpec": {"length": 30.0, "spread": 10.0, "particleSizeMin": 4.0, "particleSizeRange": 8.0, "particleDuration": 0.2, "particleCount": 8, "particleColor": [255, 230, 140, 220]},
    "turretOffsets": [14, 0],
    "turretAngleOffsets": [0],
    "hardpointOffsets": [14, 0],
    "hardpointAngleOffsets": [0],
    "barrelMode": "LINKED",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect",
    "projectileSpecId": "GearIn_test_shot"
}
```

```json
// GearIn_test_beam.wpn（片段：光束武器，两个注册列各绑一个效果类）
{
    "id": "GearIn_test_beam",
    "specClass": "beam",
    "type": "ENERGY",
    "size": "MEDIUM",
    "turretSprite": "graphics/weapons/gearin/GearIn_test_beam_turret_base.png",
    "hardpointSprite": "graphics/weapons/gearin/GearIn_test_beam_hardpoint_base.png",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect",
    "beamEffect": "data.scripts.gearin.combat.GearInBeamHitEffect",
    "turretOffsets": [18, 0],
    "turretAngleOffsets": [0],
    "hardpointOffsets": [26, 0],
    "hardpointAngleOffsets": [0],
    "fringeColor": [120, 200, 255, 255],
    "coreColor": [255, 255, 255, 255],
    "glowColor": [130, 200, 255, 255],
    "width": 12.0,
    "textureType": "SMOOTH",
    "textureScrollSpeed": 120.0,
    "pixelsPerTexel": 2.0
}
```

```json
// GearIn_test_shot.proj（片段：弹体 spec，绑定 GearInOnFireEffect 与 GearInOnHitEffect）
{
    "id": "GearIn_test_shot",
    "specClass": "projectile",
    "spawnType": "BALLISTIC",
    "onFireEffect": "data.scripts.gearin.combat.GearInOnFireEffect",
    "onHitEffect": "data.scripts.gearin.combat.GearInOnHitEffect",
    "bulletSprite": "graphics/weapons/gearin/GearIn_test_shot.png",
    "fringeColor": [255, 200, 120, 255],
    "coreColor": [255, 255, 255, 255],
    "glowColor": [255, 220, 150, 180],
    "glowRadius": 10.0,
    "length": 8.0,
    "width": 4.0,
    "fadeTime": 0.1,
    "collisionClass": "PROJECTILE_NO_FF",
    "collisionClassByFighter": "PROJECTILE_FIGHTER"
}
```

类名必须与全限定名逐字一致

`everyFrameEffect` / `beamEffect` / `onFireEffect` / `onHitEffect` 的类名必须是脚本类加载器可实例化的完整类名，绑错或类缺失时武器 / 射线 / 射弹创建抛 `Problem loading class [...]`，部署舰船即报错。

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用编译 JAR 链路)：

```text
gradle jar
```

核对产物

`gradle jar` 后用 `jar tf jars\GearIndustry.jar` 核对 `data/scripts/gearin/combat/` 下四个效果类都在 —— 类缺失或包路径不符时，先查包名与类名拼写，再查 `everyFrameEffect` / `beamEffect` / `onFireEffect` / `onHitEffect` 四处的全限定名。

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/` 下的四个类：

* `GearInWeaponFrameEffect.class`
* `GearInBeamHitEffect.class`
* `GearInOnFireEffect.class`
* `GearInOnHitEffect.class`

## 关键故障实验：效果类编译成功但武器完全没有反应

只实现接口，没有在 `.wpn` 或 `.proj` 中绑定类名。

**局部故障片段：**

```java
// 故障片段
public class GearInWeaponFrameEffect implements EveryFrameWeaponEffectPlugin { }
```

**局部修正片段：**

```java
// 修正片段
"everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect"
```

复验：开火并观察动画、光束或命中日志。

## 验证方案

前置数据：编译 JAR 流程的 MOD 布局 [C6-S1]、本节三个数据文件与帧贴图清单、四个效果类，以及装有 `GearIn_test_rotor` 与 `GearIn_test_beam` 的测试舰。

1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，再用 `jar tf jars\GearIndustry.jar` 核对 `data/scripts/gearin/combat/` 下的四个类。
2. 进入 `GearIn_test` 战役部署测试舰，查看 `starsector.log` 中的两行初始化日志。
3. 用 `GearIn_test_rotor` 射击并停火，观察动画帧；再把 `.wpn` 的 `animateWhileFiring` 设为 `false`，重启后重复射击。
4. 持续射击，核对 `started firing (before-frame callback)` 与每秒一次的 `after-frame:` 日志。
5. 核对每次开火的 `GearIn onFire ...` 日志与命中时的 `GearIn onHit ...` 日志。
6. 用 `GearIn_test_beam` 照射目标，观察射线创建、命中、端点粒子与停火后的销毁。
7. 按空格暂停，再恢复战斗并观察上述日志。

预期结果：

* `jars\GearIndustry.jar` 包含 `GearInWeaponFrameEffect.class`、`GearInBeamHitEffect.class`、`GearInOnFireEffect.class` 与 `GearInOnHitEffect.class`。
* 部署时出现 `GearIn weapon fx init GearIn_test_rotor ... animated=true frames=4 rate=12.0` 与 `GearIn weapon fx init GearIn_test_beam ... animated=false`；光束武器没有 `numFrames`，动画对象为 null。
* 开火时帧号按 0→1→2→3→0 循环；停火且冷却结束后出现一次 `idle -> frame 0 (programmatic)` 并停在第 0 帧；`animateWhileFiring:false` 时开火期间帧号冻结。
* 每次开火在生成射弹前出现 `started firing (before-frame callback)`；持续射击时每秒出现一条 `after-frame: ammo=... cooldown=...`。
* 每发射弹出现 `GearIn onFire GearIn_test_shot weapon=GearIn_test_rotor spawnType=BALLISTIC speed=...`；命中时出现 `GearIn onHit GearIn_test_shot target=Ship shieldHit=... hull=... armor=... shields=... emp=...`。
* 光束创建与销毁时各出现一次 `GearIn beam fx reset (ray created or destroyed)`；命中后出现 `GearIn beam fx hit start: target=Ship brightness=...`，照射期间射线端点每 0.15 秒出现一批蓝色火花，停火后射线与粒子消失。
* 暂停期间上述日志全部停止更新，恢复后继续。

清理：把 `GearIn_test_rotor.wpn` 的 `animateWhileFiring` 恢复为 `true`。

## 故障分析

- **效果回调从不执行**：类只注册成战斗插件，而 `.wpn` 或 `.proj` 没有绑定对应注册列。补上完整类名并重启，开火或命中时应出现回调日志。
- **创建武器、射线或射弹时报告 `Problem loading class [...]`**：类名错误、类缺失或没有无参构造器。核对完整类名和构造器，再用 `jar tf jars\GearIndustry.jar` 检查 class 路径；修正后实体应能创建。
- **`beamEffect` 没有任何行为**：注册列写在弹丸武器的 `.wpn` 中时，引擎不会创建光束效果实例。让武器使用 `specClass: beam`，或改绑 `everyFrameEffect`；重新开火后应出现对应日志。
- **`getAnimation()` 抛出 NPE**：静态贴图武器的 `getAnimation()` 返回 null。调用动画方法前先判空，重新部署静态武器后不应再出现异常。
- **动画停在第 0 帧**：`numFrames` 保持 1、`alwaysAnimate:false` 且武器闲置，或调用 `pause()` 后没有调用 `play()`，都会让帧号停住。核对 `.wpn` 的播放条件并让 `pause()`、`play()` 成对使用；开火后帧号应按配置循环。
- **部署时报告帧文件加载错误**：基贴图路径没有以 `00.png` 结尾，或所需帧文件缺失。按命名规则补齐全部帧，重新部署后应完成动画资源加载。
- **动画帧画面错位**：各帧贴图的尺寸或主体对齐点不一致。统一画布和主体位置后重新播放，切帧时图像应保持稳定。
- **光束命中后没有火花**：`didDamageThisFrame()` 为 false、命中点在视口外，或用结算帧外为 0 的 `dpsDuration` 推进节流器时，粒子不会生成。先用 `hit start` 日志确认命中，把节流推进改用 `amount` 或 `IonBeamEffect` 的边沿处理；持续照射时应每 0.15 秒出现火花。
- **第二场战斗仍在使用失效实体**：回调参数被保存并跨帧或跨战斗复用时，旧实体不属于当前引擎。参数只在当前回调内消费，需要持续状态时每帧从 `weapon.getBeams()` 或 `engine.getProjectiles()` 查询；第二场战斗应取得新实体。

## 总结

本节把四个注册列串成一条完整的链：`.wpn` 的 `everyFrameEffect` / `beamEffect` 与 `.proj` 的 `onFireEffect` / `onHitEffect` 声明 "绑哪个类"，对应实体创建时由脚本类加载器按类名实例化 (每武器、每光束射线、每射弹独立实例，必须可无参实例化)，之后由引擎按角色、按时机调用。动画数据面与帧贴图命名构成效果层的数据支柱，`AnimationAPI` 程序化控制与内置视觉构成渲染支柱，四个单角色示例类各对应一个注册列。

排错时先分清 "类实现了接口" 与 "实例被引擎调用"：回调不执行先查注册列有没有绑，再查类名与无参构造器；动画与视觉异常先核对 `.wpn` 数据字段与帧贴图命名，再检查程序化控制是否成对 (`pause()` / `play()`)。

一类多角色时每个注册列仍各创建实例，跨角色传状态需要显式通道 (`weapon.setCustom` / `getCustom`)；四条规则 (回调参数只在本回调内消费、`getAnimation()` 判空、帧贴图命名、`getSprite()` 不可设 alpha / 角度) 是叠加自定义视觉时的检查清单。

## 建议参考项

* `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin` / `EveryFrameWeaponEffectPluginWithAdvanceAfter` / `WeaponEffectPluginWithInit`：武器逐帧效果接口族 (操作前 / 操作后 / 构造回调)。
* `com.fs.starfarer.api.combat.BeamEffectPlugin` / `BeamEffectPluginWithReset`：光束逐帧回调与射线创建 / 销毁复位。
* `com.fs.starfarer.api.combat.OnFireEffectPlugin` / `OnHitEffectPlugin`：开火与命中一次性回调。
* `com.fs.starfarer.api.AnimationAPI`：动画程序化控制 (`getFrame` / `setFrame` / `play` / `pause` / `reset` / `getFrameRate` / `setFrameRate` / `getAlphaMult` / `setAlphaMult`)。
* `com.fs.starfarer.api.combat.WeaponAPI`：`getAnimation`(非动画武器返回 null)、`getSprite`(alpha / 角度渲染前被覆盖)、`setCurrAngle`、`getBarrelSpriteAPI`、`setCustom` / `getCustom`。
* `com.fs.starfarer.api.combat.BeamAPI`：`didDamageThisFrame` / `getDamageTarget` / `getRayEndPrevFrame` / `getDamage` / `getBrightness`。
* `com.fs.starfarer.api.combat.CombatEngineAPI`：`addHitParticle`；`com.fs.starfarer.api.combat.ShipAPI`：`isNoMuzzleFlash`。
* `com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI`(`getMuzzleFlashSpec` / `getVisualRecoil` / `getHardpointGunSpriteName` / `getTurretGunSpriteName`)、`com.fs.starfarer.api.loading.BeamWeaponSpecAPI`(`getBeamEffect`)、`com.fs.starfarer.api.loading.ProjectileSpecAPI`(`getOnFireEffect` / `getOnHitEffect` / `isApplyOnHitEffectWhenPassThrough`)：spec 读取侧与注册列数据挂载点；`com.fs.starfarer.api.loading.MuzzleFlashSpec`：炮口粒子参数；`.wpn` 的 `smokeSpec` 键：烟尘参数。
* `com.fs.starfarer.api.impl.combat.BlinkerEffect`(`setFrame` 人工切帧做法)、`com.fs.starfarer.api.impl.combat.RealityDisruptorEffect`(一类多角色实例模式)、`com.fs.starfarer.api.impl.combat.IonBeamEffect`(`getDpsDuration` 推进节流器)、`com.fs.starfarer.api.impl.combat.RiftBeamEffect`(everyFrameEffect 轮询 `weapon.getBeams()`)：原版实现参考。
* `data/weapons/*.wpn` 与 `data/weapons/proj/*.proj`：四个注册列的数据挂载点 (MOD 根目录相对路径)。

## 完整最小例

::: details 完整成品：GearInWeaponFrameEffect

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 武器逐帧效果（.wpn everyFrameEffect 注册列，每把武器一个实例）。
 * 构造时 init(weapon)；之后每帧在本帧"转向 / 开火之前"回调 advance、操作完成后
 * 回调 advanceAfter。演示 AnimationAPI 程序化控制贴图动画（play / pause / setFrame）。
 */
public class GearInWeaponFrameEffect implements WeaponEffectPluginWithInit,
        EveryFrameWeaponEffectPluginWithAdvanceAfter {
    private static final Logger LOG = Global.getLogger(GearInWeaponFrameEffect.class);
    private int lastReportedFrame = -1; // 帧变化日志去重
    private boolean wasFiring = false; // 开火边沿记录
    private boolean idleFrameApplied = false; // 闲置帧只强制一次
    private final IntervalUtil afterReportInterval = new IntervalUtil(1f, 1f); // 操作后日志节流

    @Override
    public void init(WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation(); // 非动画武器返回 null（硬规范 4）
        LOG.info("GearIn weapon fx init " + weapon.getId()
                + " beam=" + weapon.isBeam()
                + " animated=" + (animation != null)
                + (animation != null ? " frames=" + animation.getNumFrames() + " rate=" + animation.getFrameRate() : ""));
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧转向 / 开火等操作之前；闲置且冷却完成时冻结回 0 帧（BlinkerEffect
        // 同款姿势），否则恢复播放（play() 覆盖 alwaysAnimate:false 的数据默认）。
        AnimationAPI animation = weapon.getAnimation();
        if (animation != null) {
            boolean idle = weapon.getChargeLevel() <= 0f
                    && weapon.getCooldownRemaining() <= 0f
                    && !weapon.isFiring();
            if (idle) {
                animation.pause();
                if (!idleFrameApplied) {
                    animation.setFrame(0);
                    idleFrameApplied = true;
                    LOG.info("GearIn weapon fx " + weapon.getId() + " idle -> frame 0 (programmatic)");
                }
            } else {
                animation.play();
                idleFrameApplied = false;
            }
            if (animation.getFrame() != lastReportedFrame) {
                LOG.info("GearIn weapon fx " + weapon.getId()
                        + " frame=" + animation.getFrame() + "/" + animation.getNumFrames()
                        + " firing=" + weapon.isFiring()
                        + " charge=" + weapon.getChargeLevel()
                        + " cooldown=" + weapon.getCooldownRemaining());
                lastReportedFrame = animation.getFrame();
            }
        }
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon fx " + weapon.getId() + " started firing (before-frame callback)");
        }
        wasFiring = weapon.isFiring();
    }
    @Override
    public void advanceAfter(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧操作完成后（WithAdvanceAfter）：弹药扣减已生效，可观察操作后状态。
        if (weapon.isFiring()) {
            afterReportInterval.advance(amount);
            if (afterReportInterval.intervalElapsed()) {
                LOG.info("GearIn weapon fx " + weapon.getId() + " after-frame:"
                        + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                        + " cooldown=" + weapon.getCooldownRemaining());
            }
        }
    }
}
```

:::

::: details 完整成品：GearInBeamHitEffect

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.BeamEffectPluginWithReset;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 光束效果（.wpn beamEffect 注册列，仅光束武器；每条光束射线一个实例）。
 * 射线创建与销毁时调 reset()（实现 BeamEffectPluginWithReset 时），开火 / 充能
 * 期间每帧回调 advance。本类只消费"本帧"光束命中状态并生成纯视觉命中粒子，
 * 不修改任何游戏数值。
 */
public class GearInBeamHitEffect implements BeamEffectPlugin, BeamEffectPluginWithReset {

    private static final Logger LOG = Global.getLogger(GearInBeamHitEffect.class);

    /** 命中粒子节流器（光束 0.1 秒结算一次，逐帧撒粒子会过密）。 */
    private final IntervalUtil sparkInterval = new IntervalUtil(0.15f, 0.15f);
    /** 上一帧命中状态（仅用于命中边沿日志，不跨实例复用）。 */
    private boolean lastDidDamage = false;

    @Override
    public void reset() {
        // 射线创建与销毁各调一次（光束射线构造器 / 光束武器.destroyBeamRays）：
        // 跨射线状态在这里复位。
        lastDidDamage = false;
        LOG.info("GearIn beam fx reset (ray created or destroyed)");
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // 每帧只消费本帧光束状态（规则 2：didDamageThisFrame / getDamageTarget
        // 只对本帧结算语义有效，禁止缓存跨帧使用）。
        boolean didDamage = beam.didDamageThisFrame();
        CombatEntityAPI target = beam.getDamageTarget();
        if (didDamage && !lastDidDamage) {
            LOG.info("GearIn beam fx hit start:"
                    + " target=" + (target == null ? "null" : target.getClass().getSimpleName())
                    + " brightness=" + beam.getBrightness());
        }
        if (didDamage) {
            // 纯视觉命中火花：以本帧射线端点（上一帧端点）为基准撒粒子，不造成伤害。
            // 光束伤害按 0.1 秒间隔结算（C7-S4）；IonBeamEffect 的替代写法是用
            // beam.getDamage().getDpsDuration() 推进节流器，让粒子节奏跟随真实结算。
            sparkInterval.advance(amount);
            if (sparkInterval.intervalElapsed()) {
                Vector2f point = beam.getRayEndPrevFrame();
                engine.addHitParticle(point, new Vector2f(0f, 0f),
                        6f, 1f, 0.4f, new Color(130, 200, 255, 200));
            }
        }
        lastDidDamage = didDamage;
    }
}
```

:::

::: details 完整成品：GearInOnFireEffect

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 开火回调（.proj onFireEffect 注册列，每武器一个实例）。
 * 武器开火生成射弹后回调一次，参数是刚生成的那一颗射弹，
 * 禁止缓存跨发射使用。本类只观察并打日志，不修改任何游戏数值。
 */
public class GearInOnFireEffect implements OnFireEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInOnFireEffect.class);

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        // 参数是"本发射弹"上下文：射弹刚生成，速度 / 朝向已就绪
        // （原版 CanisterFlakPlugin 在此改写弹速与旋转，本节只观察）。
        LOG.info("GearIn onFire " + projectile.getProjectileSpecId()
                + " weapon=" + (weapon == null ? "null" : weapon.getId())
                + " spawnType=" + projectile.getSpawnType()
                + " speed=" + projectile.getVelocity().length());
    }
}
```

:::

::: details 完整成品：GearInOnHitEffect

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
 * 命中回调（.proj onHitEffect 注册列，每射弹一个实例）。
 * 射弹命中结算时回调一次，参数是本次命中结算结果，
 * 禁止缓存跨命中使用。本类只观察并打日志，不修改任何游戏数值。
 */
public class GearInOnHitEffect implements OnHitEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInOnHitEffect.class);

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
