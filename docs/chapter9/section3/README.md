# 详解武器动画

普通武器的贴图与开火效果已经具备，这次让武器本体也动起来。本节从都林复制一个中型测试武器，准备四帧贴图与独立炮管，再用配置和 `AnimationAPI` 控制播放。充能、开火与冷却期间播放，完全闲置后回到第 0 帧；先看清这一组状态，再增加更复杂的动画。

*本节预期阅读时间：\~10 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 配置连续编号的多帧武器贴图；
- 设置多帧武器的播放条件；
- 使用 `visualRecoil` 配置炮管后坐；
- 用 `AnimationAPI` 控制动画并定位故障。

## 前置知识

- [C1-S5] 的武器 CSV、`.wpn` 和弹体基础字段；
- [C5-S2] 的 `GearIn_weapon_fire` 声音登记；
- [C6-S4] 的 JAR 构建与游戏加载；
- [C8-S5] 的武器每帧脚本与实例状态。

## 配置多帧武器

复制都林的 `.wpn` 与 `weapon_data.csv` 数据行，把新 ID 改为 `GearIn_test_rotor`，名称改为 "旋转测试炮"。数值继续使用都林的数据；`.wpn` 将槽位尺寸设为 `MEDIUM`，其余需要修改的定义在下面给出。

准备多帧炮塔和硬点贴图时，**基路径以 `00.png` 结尾，编号从 00 连续增加到 `numFrames - 1`。** 引擎从这一张基贴图推导其余文件，所以数量与命名要一起确定。

### 多帧贴图的编号要求

本例准备下面八张帧图，放入 `graphics/weapons/`。每组四帧，画布与对齐点保持一致；另准备 `GearIn_test_rotor_gun.png` 作为炮管。旋转或位移由帧内容表现，文件名只负责告诉引擎读取哪张图：

```text
GearIn_test_rotor_turret00.png
GearIn_test_rotor_turret01.png
GearIn_test_rotor_turret02.png
GearIn_test_rotor_turret03.png
GearIn_test_rotor_hardpoint00.png
GearIn_test_rotor_hardpoint01.png
GearIn_test_rotor_hardpoint02.png
GearIn_test_rotor_hardpoint03.png
```

帧 0 是武器构造时首先读取的基帧。基帧缺失会使武器构造失败；后续帧缺失时，对应画面为空白。路径写成不带 `00` 的文件名时，编号推导会指向错误资源。

### 设置播放字段

把多帧字段写入 `data/weapons/GearIn_test_rotor.wpn`。普通武器的伤害、弹体、声音和槽位字段继续按 [C1-S5] 配置。

```json
{
    "id": "GearIn_test_rotor",
    "specClass": "projectile",
    "type": "BALLISTIC",
    "size": "MEDIUM",
    "displayArcRadius": 400,
    "turretSprite": "graphics/weapons/GearIn_test_rotor_turret00.png",
    "hardpointSprite": "graphics/weapons/GearIn_test_rotor_hardpoint00.png",
    "numFrames": 4,
    "frameRate": 12,
    "alwaysAnimate": false,
    "animateWhileFiring": true,
    "visualRecoil": 10.0,
    "turretGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "hardpointGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "renderHints": ["RENDER_BARREL_BELOW"],
    "turretOffsets": [10, 0],
    "turretAngleOffsets": [0],
    "hardpointOffsets": [15, 0],
    "hardpointAngleOffsets": [0],
    "barrelMode": "ALTERNATING",
    "animationType": "MUZZLE_FLASH",
    "muzzleFlashSpec": {
        "length": 30.0,
        "spread": 16.0,
        "particleSizeMin": 8.0,
        "particleSizeRange": 10.0,
        "particleDuration": 0.35,
        "particleCount": 12,
        "particleColor": [255, 155, 105, 185]
    },
    "projectileSpecId": "GearIn_durin_shot",
    "fireSoundTwo": "GearIn_weapon_fire",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect"
}
```

先核对 **基帧路径、帧数和帧率**，再看两个播放开关与炮管字段。弹体继续用 [C1-S5] 的 `GearIn_durin_shot`，声音用 [C5-S2] 已登记的 `GearIn_weapon_fire`。这样当前增加的内容集中在动画与后坐。

`numFrames` 决定帧数，`frameRate` 决定每秒切换多少帧。`alwaysAnimate` 为 `true` 时闲置也播放；`animateWhileFiring` 为 `false` 时开火期间暂停自动播放。后面的脚本会把充能、开火与冷却都作为播放条件，完全闲置时显式暂停并回到第 0 帧。

## 把炮管后坐与主体动画分别配置

`visualRecoil` 控制开火时炮管贴图的视觉位移。设置非零值后，游戏会读取 `turretGunSprite` 或 `hardpointGunSprite`，让炮管沿炮口方向后坐再复位；炮塔主体仍由 `turretSprite` 或 `hardpointSprite` 提供。没有炮管贴图时，非零数值不会产生完整的炮管后坐效果。

```json
{
    "visualRecoil": 10.0,
    "turretGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "hardpointGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png"
}
```

**炮管位移与射击散布分别配置。** `visualRecoil` 只改变炮管画面，散布由 `minSpread`、`maxSpread`、`spreadBuildup` 和 `spreadDecayRate` 决定。编者建议观察时把炮管与主体分开看：主体四帧是否播放、炮管是否沿炮口方向退回，是两个不同结果。

## 使用 `AnimationAPI`

动画武器通过 `weapon.getAnimation()` 提供 `AnimationAPI`，静态贴图武器返回 `null`。当前插件读取后按实际对象选择动画处理。

`getFrame()` 与 `setFrame(int)` 读取和指定当前帧，`getNumFrames()` 与 `getFrameRate()` 读取数量与速度。`play()`、`pause()` 显式改变播放状态，`reset()` 恢复配置驱动的默认播放状态；需要回到第 0 帧时使用 `setFrame(0)`。`setFrameRate(float)` 改变播放速度，本例在初始化时使用 `FRAME_RATE` 常量，便于实际比较。

这段代码放在效果类的初始化方法中，用于记录该武器是否真的提供动画对象：

```java
AnimationAPI animation = weapon.getAnimation();
if (animation == null) {
    LOG.warn("GearIn weapon animation unavailable: " + weapon.getId());
    return;
}
LOG.info("GearIn weapon animation: " + weapon.getId()
        + " frames=" + animation.getNumFrames()
        + " rate=" + animation.getFrameRate());
```

## 编写武器每帧插件

`everyFrameEffect` 让引擎在每帧把当前 `WeaponAPI` 传给插件。把以下局部逻辑放入 `advance(...)`。充能、开火或冷却中的任一状态都继续播放，三者结束后暂停并回到第 0 帧。完整类只在帧号变化时记录日志，便于把画面与序列对应：

```java
AnimationAPI animation = weapon.getAnimation();
if (animation != null) {
    boolean active = weapon.isFiring()
            || weapon.getChargeLevel() > 0f
            || weapon.getCooldownRemaining() > 0f;
    if (active) {
        animation.play();
    } else {
        animation.pause();
        animation.setFrame(0);
    }
}
```

`getAlphaMult()` 和 `setAlphaMult(float)` 可调整动画透明度。武器的 `getSprite()` 返回对象会在渲染前被引擎重新设置角度和透明度，动画插件不应依赖在该对象上直接设置这两个属性。

## 完整最小例

保存为 `src/data/scripts/gearin/combat/GearInWeaponFrameEffect.java`，`.wpn` 的 `everyFrameEffect` 保留前面的完整类名。

::: details 完整成品：GearInWeaponFrameEffect

```java
package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;

public class GearInWeaponFrameEffect implements WeaponEffectPluginWithInit,
        EveryFrameWeaponEffectPlugin {
    private static final Logger LOG = Global.getLogger(GearInWeaponFrameEffect.class);
    private static final float FRAME_RATE = 12f;

    private int lastReportedFrame = -1;
    private boolean idleFrameApplied = false;
    private boolean wasFiring = false;

    @Override
    public void init(WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation();
        if (animation == null) {
            LOG.warn("GearIn weapon animation unavailable: " + weapon.getId());
            return;
        }
        animation.setFrameRate(FRAME_RATE);
        LOG.info("GearIn weapon animation: " + weapon.getId()
                + " frames=" + animation.getNumFrames()
                + " rate=" + animation.getFrameRate());
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation();
        if (animation != null) {
            boolean active = weapon.isFiring()
                    || weapon.getChargeLevel() > 0f
                    || weapon.getCooldownRemaining() > 0f;
            if (active) {
                animation.play();
                idleFrameApplied = false;
            } else {
                animation.pause();
                if (!idleFrameApplied) {
                    animation.setFrame(0);
                    idleFrameApplied = true;
                    LOG.info("GearIn weapon animation idle: " + weapon.getId());
                }
            }
            if (animation.getFrame() != lastReportedFrame) {
                LOG.info("GearIn weapon animation frame: " + weapon.getId()
                        + " frame=" + animation.getFrame()
                        + "/" + animation.getNumFrames());
                lastReportedFrame = animation.getFrame();
            }
        }
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon animation firing: " + weapon.getId());
        }
        wasFiring = weapon.isFiring();
    }
}
```

:::

## 验证方案

前置数据：[C4-S1] 的 `GearIn_test` 与可安装中型实弹武器的工匠装配。

1. 保存数据、贴图与源码，执行 `gradle jar`，确认 JAR 包含 `GearInWeaponFrameEffect.class`，重启游戏。
2. 检查炮塔和硬点贴图均存在 `00` 至 `03` 帧。
3. 在装配界面安装旋转测试炮，进入战斗，确认日志记录 `frames=4` 和 `rate=12`。
4. 武器闲置时确认停在第 0 帧，充能、开火或冷却时确认帧号循环变化。
5. 把 `FRAME_RATE` 改为 `6f`，构建并重启再测试，比较同一组帧图的播放速度与 `rate=6` 日志；比较完恢复 `12f`。
6. 设置 `visualRecoil` 和炮管贴图，确认开火时炮管发生视觉位移。

预期结果：

- `jar tf jars/GearIndustry.jar` 能看到 `GearInWeaponFrameEffect.class`；
- 炮塔和硬点贴图均存在 `00` 至 `03` 帧；
- 进入测试战斗后，日志记录 `frames=4` 和 `rate=12`；
- 武器闲置时停在第 0 帧，充能、开火或冷却时帧号循环变化；
- `FRAME_RATE` 为 6 时，帧序列速度为原来的一半，初始化日志同时记录 6；
- 设置 `visualRecoil` 与炮管贴图后，开火时炮管发生视觉位移。

## 故障分析

- **武器加载时基帧读取失败**：路径没有以 `00.png` 结尾或基帧缺失。
    - **解决方案**：将 `turretSprite` 和 `hardpointSprite` 改为 `00.png` 基路径，并按连续编号补齐帧文件。

- **`getAnimation()` 返回 `null`**：武器按静态贴图创建。
    - **解决方案**：检查 `numFrames` 是否大于 1，并在调用动画方法前保留判空。

- **完全闲置时动画持续播放**：默认自动播放或脚本未执行闲置分支。
    - **解决方案**：将 `alwaysAnimate` 设为 `false`，并在闲置分支调用 `pause()` 与 `setFrame(0)`。

- **开火后动画仍停在第 0 帧**：播放开关或脚本分支没有接入。
    - **解决方案**：在充能或开火分支调用 `play()`，并确认 `animateWhileFiring` 为 `true`。

- **炮管没有视觉后坐**：位移量或炮管贴图缺失。
    - **解决方案**：同时配置非零 `visualRecoil` 与 `turretGunSprite` 或 `hardpointGunSprite`。

- **日志每帧刷屏**：每次回调都无条件记录。
    - **解决方案**：只在帧号变化或开火边沿记录日志，不要在每次 `advance(...)` 中无条件输出。

## 总结

旋转测试炮沿用都林的弹体与数值，通过连续帧图建立主体动画，独立炮管表现后坐。配置决定默认播放，脚本则按当前充能、开火和冷却状态安排播放与闲置帧。

动画做得是否自然，最终还要看帧内容和节奏。先用帧号与画面确认顺序，再调整帧率和动作幅度，技术错误与美术取舍才能分别判断。

## 建议参考项

- `com.fs.starfarer.api.AnimationAPI`：帧读取、播放控制和帧率设置；
- `com.fs.starfarer.api.combat.WeaponAPI`：`getAnimation()`、武器状态和贴图访问；
- `com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI`：`getVisualRecoil()`。
