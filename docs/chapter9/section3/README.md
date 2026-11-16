# 详解武器动画

如果只依靠 `.wpn` 里写死的数值，大概很难作出什么更为复杂的动画，而一些简单的平移也必须用麻烦的多帧图片处理。这些问题则可以借助 `AnimationAPI` 解决。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C1-S5] 的武器 CSV、`.wpn` 与弹体基础字段；
- [C5-S1] 的 `GearIn_weapon_fire` 声音登记；
- [C8-S3] 的武器每帧脚本，以及效果实例只属于一把武器这一点。

## 武器动画的接口

武器的贴图与帧播放分成三层，各由不同的对象管：

| 对象 | 取得方式 | 管什么 |
| --- | --- | --- |
| 帧播放 | `weapon.getAnimation()` | 当前第几帧、帧率、播放与暂停 |
| 贴图 | `weapon.getSprite()` | 当前这一帧的贴图对象，颜色与透明度 |
| 炮塔朝向 | `weapon.getCurrAngle()` / `setFacing(...)` | 武器整体指向 |

`AnimationAPI` (`com.fs.starfarer.api.AnimationAPI`) 的方法正好覆盖帧播放这一层：

```java
public interface AnimationAPI {
	/**
	 * @return Index of current animation frame, 0-based.
	 */
	int getFrame();

	int getNumFrames();
	void setFrame(int frame);

	void play();
	void pause();

	/**
	 * Reset the pause/play state and let the animation behave normally.
	 */
	void reset();

	float getAlphaMult();
	void setAlphaMult(float alphaMult);

	float getFrameRate();
	void setFrameRate(float frameRate);
}
```

**静态贴图的武器，`getAnimation()` 返回 `null`**，这是本节脚本第一件事就判空的原因。

**`weapon.getSprite()` 返回的贴图不能设角度与透明度，渲染之前游戏会把这两项覆盖掉。**

要在每帧脚本里改朝向，用 `weapon.setFacing(...)`，原版 `SensorDishRotationEffect` 就是这么做的。改颜色走 `SpriteAPI.setColor(...)`，透明度用其中的 alpha 通道。全树 `impl/combat/` 下 `setCurrAngle(...)` 一次都没被调用过。

## 准备帧图

本节制作 "旋转测试炮"，ID 为 `GearIn_test_rotor`，中型实弹武器。它复制都林的 CSV 行，伤害与弹体沿用都林，`GearIn_test_rotor` 只在外观上与都林不同。

主体动画由连续编号的帧图组成。贴图不必自己画：原版旋转炮 `graphics/weapons/rotary/` 下已有现成的四帧，炮塔为 44 × 44，硬点为 30 × 42。复制到 `graphics/weapons/` 并改名：

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

::: caution 帧图路径与编号

**`.wpn` 中写的路径必须以 `00.png` 结尾，编号从 00 连续增加到 `numFrames - 1`。** 引擎先读取 `00` 基帧，再按编号推导其余各帧。基帧缺失，武器无法加载；中间某一帧缺失，那一帧显示为空白；路径末尾不是 `00`，后续编号会指向不存在的文件。

:::

各帧的画布大小与对齐点必须一致，图像内容才能表现旋转。另取 `GearIn_test_rotor_gun.png` 作为独立炮管，本例直接复制都林的后坐贴图。主体帧图负责转动，炮管负责后坐，分开之后调整其中一项不会牵动另一项。

## 把帧图接到武器

`data/weapons/GearIn_test_rotor.wpn` 复制自都林的 `.wpn`，动画相关的字段如下，其余字段沿用都林：

```json
{
    "id": "GearIn_test_rotor",
    "size": "MEDIUM",
    "turretSprite": "graphics/weapons/GearIn_test_rotor_turret00.png",
    "hardpointSprite": "graphics/weapons/GearIn_test_rotor_hardpoint00.png",
    "numFrames": 4,
    "frameRate": 12,
    "alwaysAnimate": false,
    "animateWhileFiring": true,
    "visualRecoil": 10.0,
    "turretGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "hardpointGunSprite": "graphics/weapons/GearIn_test_rotor_gun.png",
    "everyFrameEffect": "data.scripts.gearin.combat.GearInWeaponFrameEffect"
}
```

`numFrames` 是帧数，`frameRate` 是每秒播放的帧数。`alwaysAnimate` 为 `false`，武器闲置时不会自行播放；`animateWhileFiring` 为 `true`，开火期间自动播放。

非零的 `visualRecoil` 让游戏读取 `turretGunSprite` 与 `hardpointGunSprite`，炮管沿炮口方向后坐再复位，主体仍使用自己的帧图。**视觉后坐与射击散布分别计算**，散布仍由 `minSpread`、`maxSpread` 等列决定。

## 让转速随开火变化

旋转炮的转子有惯性：开火后逐渐加速，停火后继续转一阵才慢慢停下。`.wpn` 中的 `frameRate` 是固定值，做不到这一点，需要由 `EveryFrameWeaponEffectPlugin` 每帧改写帧率。

`weapon.getAnimation()` 返回 `AnimationAPI`。静态贴图的武器没有动画对象，返回 `null`，调用前必须判空。

脚本的核心是一个转速比例 `spin`，取值 0 到 1：

- 武器正在开火，`spin` 每帧增加 `amount / SPIN_UP_TIME`；
- 否则每帧减少 `amount / SPIN_DOWN_TIME`；
- `spin` 降到 0，调用 `pause()` 并用 `setFrame(0)` 回到第 0 帧；
- 否则把帧率设为 `MAX_FRAME_RATE * spin`，并调用 `play()`。

`spin` 是效果实例的字段。武器脚本的实例只属于一把武器 ([C8-S3])，多门旋转炮各自保存转速，互不影响。

**暂停时必须直接返回。** 战斗暂停时 `advance(...)` 仍会被调用，不判断 `engine.isPaused()`，暂停期间转速照常变化，恢复后转子的状态与暂停前不符。

加速用 1.5 秒，减速用 2 秒，最高 24 帧 / 秒，是 `.wpn` 中基础帧率 12 的两倍。减速比加速慢，是为了让停火后的余转清晰可见：玩家扫一眼转子，就知道这门炮刚刚开过火，不必再去看弹药或幅能。

这些数值只管外观，与平衡无关，因此直接写成代码常量，不放进 [C7-S2] 的设置。

::: warning reset 不会回到第 0 帧

`AnimationAPI.reset()` 恢复的是配置驱动的默认播放状态，不会指定帧号。要停在第 0 帧，需要 `setFrame(0)` 配合 `pause()`，本例在 `spin` 降到 0 时完成这两步。

:::

## 完整的转子脚本

`src/data/scripts/gearin/combat/GearInWeaponFrameEffect.java`：

```java
package data.scripts.gearin.combat;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 旋转测试炮的转子：连续开火时转速逐渐升高，停火后逐渐降低，完全停下后回到第 0 帧。 */
public class GearInWeaponFrameEffect implements EveryFrameWeaponEffectPlugin {

    /** 转速最高时的帧率。 */
    private static final float MAX_FRAME_RATE = 24f;

    /** 从静止加速到最高转速所需的开火时间，单位秒。 */
    private static final float SPIN_UP_TIME = 1.5f;

    /** 从最高转速减速到静止所需的时间，单位秒。 */
    private static final float SPIN_DOWN_TIME = 2f;

    /** 当前转速比例，0 为静止，1 为最高转速。每把武器各有一个实例。 */
    private float spin = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }
        AnimationAPI animation = weapon.getAnimation();
        if (animation == null) {
            return;
        }
        if (weapon.isFiring()) {
            spin = Math.min(1f, spin + amount / SPIN_UP_TIME);
        } else {
            spin = Math.max(0f, spin - amount / SPIN_DOWN_TIME);
        }
        if (spin <= 0f) {
            animation.pause();
            animation.setFrame(0);
        } else {
            animation.setFrameRate(MAX_FRAME_RATE * spin);
            animation.play();
        }
    }
}
```

## 登记武器

`data/weapons/weapon_data.csv` 复制都林的整行，名称改为 `旋转测试炮`，ID 改为 `GearIn_test_rotor`，其余列不动。伤害、弹体与声音全部沿用都林，`GearIn_weapon_fire` 已在 [C5-S1] 登记。

### 进游戏核对

执行 `gradle jar`，重启游戏，在模拟战的装配界面给工匠的中型实弹槽装上旋转测试炮。

- 武器闲置时，转子停在第 0 帧，画面不动。
- 开火后转子逐渐加速，约 1.5 秒后达到最高转速。停火后继续转约 2 秒才停下，并回到第 0 帧。
- 开火时炮管沿炮口方向后坐再复位，转子转动与炮管后坐互不影响。
- 暂停战斗，转子保持暂停瞬间的状态；恢复后从该状态继续。

## 常见问题

- **武器无法加载，日志报找不到贴图**：`turretSprite` 或 `hardpointSprite` 的路径没有以 `00.png` 结尾，或者基帧文件缺失。
    - **解决方案**：路径改为 `00.png`，并按 `numFrames` 补齐连续编号的帧文件。

- **转子某一时刻闪出空白帧**：中间某个编号的帧图缺失。
    - **解决方案**：核对炮塔与硬点各自的 `00` 至 `03`，两组都要齐全。

- **转子完全不动**：`getAnimation()` 返回 `null`，脚本在判空处直接返回了。
    - **解决方案**：检查 `numFrames` 是否大于 1，以及 `.wpn` 的贴图路径是否指向帧图。

- **停火后转子一直转，不回到第 0 帧**：`alwaysAnimate` 被设为 `true`，或者脚本没有接入。
    - **解决方案**：把 `alwaysAnimate` 设为 `false`，并确认 `everyFrameEffect` 的类名写的是完整包路径。

- **炮管没有后坐**：`visualRecoil` 为 0，或者炮管贴图缺失。
    - **解决方案**：同时配置非零的 `visualRecoil` 与 `turretGunSprite`、`hardpointGunSprite`。

- **暂停后转子状态与暂停前不符**：`advance(...)` 没有在暂停时返回。
    - **解决方案**：在方法开头判断 `engine.isPaused()`。

## 建议参考项

- `com.fs.starfarer.api.AnimationAPI`：帧读取、播放控制与帧率设置。
- `com.fs.starfarer.api.combat.WeaponAPI`：`getAnimation()` 与武器的开火状态。
- `com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin`：武器每帧脚本的接口。
- `data/weapons/chaingun.wpn`：原版旋转炮的 `numFrames` 与 `frameRate` 写法。
- `graphics/weapons/rotary/`：本节复制的四帧图来源。
