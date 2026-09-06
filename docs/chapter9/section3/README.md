# 详解武器动画

本节补充 [C1-S5] 的普通武器制作，专门处理多帧 `.wpn` 的贴图编号、播放条件和程序化控制。完成后，`GearIn_test_rotor` 会在充能或开火时播放帧动画，闲置时停在第 0 帧。

*本节预期阅读时间：~10 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 配置连续编号的多帧武器贴图；
- 设置多帧武器的播放条件；
- 使用 `visualRecoil` 配置炮管后坐；
- 用 `AnimationAPI` 控制动画并定位故障。

## 前置知识

- [C1-S5] 的武器 CSV、`.wpn` 和弹体基础字段；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C8-S3] 的每帧插件和暂停语义。

## 配置多帧武器

普通武器已有炮塔和硬点贴图；多帧武器还需要让引擎能够从一张基贴图推导出整组帧文件。基路径必须以 `00.png` 结尾，帧号从 `00` 连续增加到 `numFrames - 1`。

### 多帧贴图的编号要求

下面的文件名表示四帧动画。炮塔贴图和硬点贴图都要准备相同数量的帧，并保持画布与对齐点一致。

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
    "projectileSpecId": "GearIn_test_shot",
    "fireSoundTwo": "GearIn_weapon_fire",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect"
}
```

加粗关注这些新增或不同于普通武器的字段：`turretSprite`、`hardpointSprite` 的 `00.png` 后缀，`numFrames`、`frameRate`、`alwaysAnimate`、`animateWhileFiring`，以及 `visualRecoil` 和炮管贴图。其余字段只是使示例成为可独立读取的完整武器定义，含义沿用 [C1-S5]。

`numFrames` 决定帧数，`frameRate` 决定每秒切换多少帧。`alwaysAnimate` 为 `true` 时闲置也播放；`animateWhileFiring` 为 `false` 时开火期间暂停自动播放。本例让动画只在充能或开火阶段运行。

## 配置 `visualRecoil`

`visualRecoil` 控制开火时炮管贴图的视觉位移。设置非零值后，游戏会读取 `turretGunSprite` 或 `hardpointGunSprite`，让炮管沿炮口方向后坐再复位；炮塔主体仍由 `turretSprite` 或 `hardpointSprite` 提供。没有炮管贴图时，非零数值不会产生完整的炮管后坐效果。

```json
{
    "visualRecoil": 10.0,
    "turretGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "hardpointGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png"
}
```

这只是视觉后坐，散布仍由 `minSpread`、`maxSpread`、`spreadBuildup` 和 `spreadDecayRate` 控制，两组参数互不替代。

## 使用 `AnimationAPI`

动画武器在 `weapon.getAnimation()` 中提供 `AnimationAPI`；静态贴图武器返回 `null`，所以每次使用前都要判空。`getFrame()` 和 `setFrame(int)` 读取或指定当前帧，`getNumFrames()` 与 `getFrameRate()` 读取配置，`play()`、`pause()`、`reset()` 控制播放，`setFrameRate(float)` 可以临时改变速度。

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

`everyFrameEffect` 让引擎在每帧把当前 `WeaponAPI` 传给插件。下面的片段把充能或开火视为播放条件，完全闲置后暂停并回到第 0 帧；固定帧日志用于确认动画确实在变化。

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

1. 执行 `gradle jar`，确认 JAR 包含 `GearInWeaponFrameEffect.class`。
2. 检查炮塔和硬点贴图均存在 `00` 至 `03` 帧。
3. 进入测试战斗，确认日志记录 `frames=4` 和 `rate=12`。
4. 武器闲置时确认停在第 0 帧，充能或开火时确认帧号循环变化。
5. 临时调用 `setFrameRate(...)`，确认播放速度改变。
6. 设置 `visualRecoil` 和炮管贴图，确认开火时炮管发生视觉位移。
7. 删除一张后续帧确认对应画面为空白；删除 `00` 基帧确认武器构造失败。

## 故障分析

- **基贴图路径没有以 `00.png` 结尾。**

    - **解决方案**：将 `turretSprite` 和 `hardpointSprite` 改为 `00.png` 基路径，并按连续编号补齐帧文件。

- **`getAnimation()` 返回 `null`。**

    - **解决方案**：检查 `numFrames` 是否大于 1，并在调用动画方法前保留判空。

- **动画闲置时持续播放。**

    - **解决方案**：将 `alwaysAnimate` 设为 `false`，并在闲置分支调用 `pause()` 与 `setFrame(0)`。

- **开火后动画仍停在第 0 帧。**

    - **解决方案**：在充能或开火分支调用 `play()`，并确认 `animateWhileFiring` 为 `true`。

- **炮管没有视觉后坐。**

    - **解决方案**：同时配置非零 `visualRecoil` 与 `turretGunSprite` 或 `hardpointGunSprite`。

- **日志每帧刷屏。**

    - **解决方案**：只在帧号变化或开火边沿记录日志，不要在每次 `advance(...)` 中无条件输出。

## 总结

多帧武器先用以 `00.png` 结尾的基路径和连续帧文件建立贴图序列，再用 `numFrames`、`frameRate` 与两个播放条件字段决定默认播放行为。`visualRecoil` 单独控制炮管的视觉位移。逐帧插件通过判空取得 `AnimationAPI`，在武器状态变化时选择 `play()`、`pause()` 和 `setFrame(0)`，日志用于确认帧数、帧率和当前帧。

## 建议参考项

- `com.fs.starfarer.api.AnimationAPI`：帧读取、播放控制和帧率设置；
- `com.fs.starfarer.api.combat.WeaponAPI`：`getAnimation()`、武器状态和贴图访问；
- `com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI`：`getVisualRecoil()`。
