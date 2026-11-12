# 详解武器动画

本节制作一门带主体动画与独立炮管的测试武器。四帧贴图表现主体动作，炮管在开火时后坐，脚本让动画覆盖充能、开火与冷却，完全闲置后回到第 0 帧。资源准备、数据设置与播放控制共同决定最后的画面。

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~15 分钟。_

## 学习目标

完成后，您将能够：

- 配置连续编号的多帧武器贴图；
- 设置多帧武器的播放条件；
- 使用 `visualRecoil` 配置炮管后坐；
- 用 `AnimationAPI` 控制动画并定位故障。

## 前置知识

- [C1-S5] 的武器 CSV、`.wpn` 和弹体基础字段；
- [C5-S1] 的 `GearIn_weapon_fire` 声音登记；
- [C6-S2] 的 JAR 构建与游戏加载；
- [C8-S3] 的武器每帧脚本与实例状态。

## 准备帧图与炮管

复制都林的 `.wpn` 与 `weapon_data.csv` 行，将新武器 ID 改为 `GearIn_test_rotor`，名称为 "旋转测试炮"。伤害等数值沿用都林，槽位尺寸在 `.wpn` 中改为 `MEDIUM`。本例集中处理动画，弹体与声音继续使用既有登记。

主体动画由连续帧图组成。炮塔与硬点分别准备四帧，放在 `graphics/weapons/` 下：

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

各帧画布与对齐点保持一致，图像内容表现旋转或位移。引擎先读取 `00.png` 基帧，再推导后续编号，所以 **路径以 `00.png` 结尾，编号从 00 连续增加至 `numFrames - 1`**。

基帧缺失会导致武器构造失败，后续帧缺失则让相应画面为空白。路径不含 `00` 时，后续编号也会指向错误资源。

另准备 `GearIn_test_rotor_gun.png` 作为独立炮管。主体帧图与炮管承担不同动作：前者切换画面，后者在开火时后坐与复位。保持这两种资源独立，调整动作时也能分别处理。

## 把动画资源接到武器

保存 `data/weapons/GearIn_test_rotor.wpn`，沿用都林的弹体 `GearIn_durin_shot` 和第五章登记的 `GearIn_weapon_fire`。本例定义如下：

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

主体路径指向两组 `00` 基帧，`numFrames` 指定四帧，`frameRate` 指定每秒 12 帧。炮口、弹体与声音仍按 [C1-S5] 的武器关系工作，`everyFrameEffect` 则接到下面的播放控制类。

### 默认播放与脚本控制

`alwaysAnimate` 决定是否在闲置时继续自动播放，`animateWhileFiring` 控制开火期间的自动播放；后者为 `false` 时会显著减慢这一阶段的动画。本例使用 `false` 与 `true`，随后再由脚本明确决定充能、开火、冷却和完全闲置时的播放状态。

编者更倾向于让武器动画表现工作状态。充能、开火与冷却都有动作，完全闲置后回到稳定姿态，玩家才容易从画面辨认武器是否仍在工作。本例据此安排播放条件，帧图的动作幅度则按武器外观继续调整。

### 独立炮管的后坐

定义中的 `visualRecoil` 为 10.0，两种槽位都引用同一张炮管贴图。相关局部字段是：

```json
{
    "visualRecoil": 10.0,
    "turretGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "hardpointGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png"
}
```

非零 `visualRecoil` 让游戏读取 `turretGunSprite` 或 `hardpointGunSprite`，炮管沿炮口方向后坐再复位，主体仍使用自己的帧图。只有位移数值却没有炮管资源，无法得到完整的后坐表现。

**视觉后坐与射击散布分别计算。** `visualRecoil` 改变炮管画面，散布继续由 `minSpread`、`maxSpread`、`spreadBuildup` 和 `spreadDecayRate` 决定。验证时分别确认主体帧序列和炮管位移，才能判断是哪一部分未生效。

## 在武器实例中控制播放

创建 `src/data/scripts/gearin/combat/GearInWeaponFrameEffect.java`，实现 `WeaponEffectPluginWithInit` 与 `EveryFrameWeaponEffectPlugin`。前者在武器绑定时执行初始化，后者每帧接收当前 `WeaponAPI`。

### 初始化动画对象

从 `weapon.getAnimation()` 取得 `AnimationAPI`。静态贴图武器返回 `null`，先记录这一结果；动画武器则记录帧数与帧率。局部片段放在 `init(...)` 中：

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

完整类还调用 `setFrameRate(FRAME_RATE)`，将本实例的速度设为 `12f`。这意味着后续比较播放速度时，要修改这个常量并重新构建，单独调整数据文件中的帧率仍会被初始化代码覆盖。

### 用状态决定播放与静止帧

将以下局部逻辑放入 `advance(...)`。充能、开火或剩余冷却大于 0 时播放，三者都结束后暂停并指定第 0 帧：

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

`play()` 与 `pause()` 控制播放状态，`setFrame(0)` 指定静止姿态。完整类使用 `idleFrameApplied`，只在进入完全闲置时重置一次；`lastReportedFrame` 在帧号变化时记录，`wasFiring` 在开火状态变化时记录。

这些字段属于每把武器的效果实例，沿用 [C8-S3] 的对象范围。多门测试炮可以各自保留帧号和开火状态，日志也按武器 ID 对应。

## 进一步调整动画

`getFrame()` 读取当前帧，`getNumFrames()` 读取总帧数。`getFrameRate()` 与 `setFrameRate(...)` 用于比较和修改速度，`getAlphaMult()` 与 `setAlphaMult(...)` 用于动画透明度。

::: warning reset 不会指定第 0 帧

`reset()` 恢复配置驱动的默认播放状态。要回到第 0 帧，调用 `setFrame(0)`；要停止在这个姿态，还要配合 `pause()`。本例的闲置分支分别完成这两项操作。

:::

透明度也通过动画对象调整。武器 `getSprite()` 返回的对象会在渲染前被引擎重新设置角度和透明度，直接在那份 Sprite 上设置这些属性，不能作为持续效果的依据。

帧号日志确认播放次序，画面确认动作与对齐。帧序列能够正常循环之后，再比较帧率和动作幅度，这时改变的是动画表现，而非文件读取或播放入口。

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

- `jar tf jars/GearIndustry.jar` 的输出包含 `GearInWeaponFrameEffect.class`；
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

旋转测试炮已经将连续帧图、独立炮管与武器状态接在一起。基帧和编号决定资源怎样加载，后坐字段决定炮管怎样移动，实例脚本决定何时播放、何时返回静止帧。

技术关系成立之后，帧内容、速度与动作幅度仍要共同服务武器的外观。修改帧率时也要对应实际控制位置，本例由初始化中的常量指定速度。

## 建议参考项

- `com.fs.starfarer.api.AnimationAPI`：帧读取、播放控制和帧率设置；
- `com.fs.starfarer.api.combat.WeaponAPI`：`getAnimation()`、武器状态和贴图访问；
- `com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI`：`getVisualRecoil()`。
