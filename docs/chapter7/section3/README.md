# 调用声音播放器

武器常规开火、引擎推进、护盾展开这类原版自带的时机，按第五章的做法在数据文件里挂上声音 ID 就够了。但代码自己算出来的时机，比如都林命中船体后按概率触发的额外爆炸、乌里照射船体时随累计时间升调的充能声，原版流程并不知道它们什么时候发生，只能由代码在触发那一刻主动调用声音播放器。

这里的调用很直白：单次音效调一次响一次；循环音效则是按帧续命，哪一帧没调，声音就从哪一帧开始淡出。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C5-S1] 的 `sounds.json` 登记方法；
- [C2-S4] 的都林命中效果 `GearInDurinOnHitEffect` 与乌里光束效果 `GearInUriBeamEffect`；
- [C7-S2] 的 `Global` 服务。

## 一次还是持续

`Global.getSoundPlayer()` 返回 `SoundPlayerAPI`，播放方法按声音属于战场还是界面、响一次还是持续响来分。

| 方法 | 位置 | 调用方式 | 文件声道 |
| --- | --- | --- | --- |
| `playSound(...)` | 有 | 调一次，响一次 | 必须单声道 |
| `playLoop(...)` | 有 | **每次推进都要再调一次** | 应当单声道 |
| `playUISound(...)` | 无 | 调一次，响一次 | 必须立体声 |
| `playUILoop(...)` | 无 | 每次推进都要再调一次 | 立体声 |

单个音符调一次就够，循环音则是 "续命" 式的：每帧都要调用，漏一帧它就开始淡出。

**单声道与立体声的方向是反的，这里最容易记错。** 战场声音必须单声道，因为游戏要按镜头位置算出左右声道；立体声文件自带两路数据，游戏再往两边分，定位就散了。界面声音不能定位，用立体声正好。这条分工在文件格式上是硬的：战场音效用单声道文件，界面音效用立体声文件。

`playLoop(...)` 另有一个带淡入淡出的重载，多出 `float fadeIn, float fadeOut` 两个参数，需要控制淡出速度时用它。

## 为额外爆炸加入音效

[C2-S4] 的都林命中船体时，按概率追加一次高爆爆炸。爆炸有画面、有伤害，却没有声音，玩家只能看到火光。

先在 MOD 的 `sounds.json` 中登记这次爆炸的声音，借用原版毁灭者炮的爆炸文件：

```json
	"GearIn_durin_burst":[
		{"file":"sounds/sfx_wpn_guns/devastator_shell_exp_01.ogg","pitch":1.3,"volume":0.6},
		{"file":"sounds/sfx_wpn_guns/devastator_shell_exp_02.ogg","pitch":1.3,"volume":0.6},
	],
```

列出多个文件时，游戏每次随机选择其中一个。同一种爆炸连续出现时，完全相同的声音会显得单调，两三个变体就能明显缓解。音调提高到 `1.3`，是因为都林的爆炸比毁灭者炮的炮弹小得多，原调听起来过于沉重。

在 `GearInDurinOnHitEffect` 生成爆炸的位置之后加入播放：

```java
Global.getSoundPlayer().playSound("GearIn_durin_burst", 1f, 1f, point, target.getVelocity());
```

`playSound(...)` 依次传入声音 ID、音调倍率、音量倍率、声源坐标与声源速度。这里的倍率与 `sounds.json` 中的值相乘，写 `1f` 即沿用登记值。速度传入目标的速度，声音便随被命中的舰船一同移动。

## 让充能声随进度升高

[C2-S4] 的乌里满亮度照射船体时累计时间，每满 0.5 秒放出一道 EMP 电弧。这段累计过程本身是看不见的，玩家无从判断下一道电弧何时出现。我们为它加一段循环的充能声，音调随累计进度升高，电弧放出后回落。

登记循环音：

```json
	"GearIn_uri_charge":[
		{"file":"sounds/sfx_shields/flux_lightning_loop.ogg","pitch":1,"volume":0.5},
	],
```

**循环音必须每帧调用 `playLoop(...)`，停止调用后声音自行淡出。** 这一点与 `playSound(...)` 不同：循环音没有 "开始" 与 "停止" 两个调用，只有 "这一帧仍在播放"。需要停止时，什么都不写即可；条件不满足时提前 `return`，下一帧的循环音就不会被续上。

在 `GearInUriBeamEffect.advance(...)` 中累计时间之后加入：

```java
float progress = Math.min(1f, elapsed / ARC_INTERVAL);
Global.getSoundPlayer().playLoop("GearIn_uri_charge", beam, 0.8f + 0.6f * progress, 1f,
        point, target.getVelocity());
```

`playLoop(...)` 的第二个参数是播放者对象，用来区分不同的循环声源：同一个对象每帧重复调用同一个循环音只会续上同一份播放，不同对象则各自独立。这里传入 `beam` 本身，两门乌里同时照射时各自是不同的 `BeamAPI` 实例，两道充能声就不会混成一路。

音调从 `0.8` 升到 `1.4`，范围是试出来的：低于 `0.8` 时充能声与舰船引擎声混在一起，高于 `1.5` 则显得尖锐。光束离开船体或转到护盾上，前面的条件判断提前返回，充能声随即淡出，这也正好提示玩家累计已经中断。

## 修改后的两个类

`src/data/scripts/gearin/combat/GearInDurinOnHitEffect.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 都林命中船体时，按概率追加一次带音效的高爆爆炸。 */
public class GearInDurinOnHitEffect implements OnHitEffectPlugin {

    private static final float CHANCE = 0.25f;
    private static final float BONUS_DAMAGE = 20f;
    private static final Color EXPLOSION_COLOR = new Color(255, 120, 60, 255);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (shieldHit || !(target instanceof ShipAPI) || Math.random() > CHANCE) {
            return;
        }
        engine.spawnExplosion(point, new Vector2f(), EXPLOSION_COLOR, 40f, 0.3f);
        Global.getSoundPlayer().playSound("GearIn_durin_burst", 1f, 1f, point, target.getVelocity());
        engine.applyDamage(target, point, BONUS_DAMAGE, DamageType.HIGH_EXPLOSIVE, 0f, false, false,
                projectile.getSource());
    }
}
```

`src/data/scripts/gearin/combat/GearInUriBeamEffect.java`：

```java
package data.scripts.gearin.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;

/** 乌里满亮度照射船体时累计充能，每满一个间隔放出 EMP 电弧，充能声随进度升高。 */
public class GearInUriBeamEffect implements BeamEffectPlugin {

    private static final float ARC_INTERVAL = 0.5f;
    private static final float ARC_DAMAGE = 40f;
    private static final float ARC_EMP = 200f;

    private float elapsed = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (!(target instanceof ShipAPI) || beam.getBrightness() < 1f) {
            return;
        }
        Vector2f point = beam.getRayEndPrevFrame();
        if (target.getShield() != null && target.getShield().isWithinArc(point)) {
            return;
        }
        elapsed += amount;

        float progress = Math.min(1f, elapsed / ARC_INTERVAL);
        Global.getSoundPlayer().playLoop("GearIn_uri_charge", beam, 0.8f + 0.6f * progress, 1f,
                point, target.getVelocity());

        if (elapsed < ARC_INTERVAL) {
            return;
        }
        elapsed = 0f;
        engine.spawnEmpArc(beam.getSource(), point, target, target, DamageType.ENERGY, ARC_DAMAGE, ARC_EMP,
                100000f, "tachyon_lance_emp_impact", beam.getWidth() + 9f, beam.getFringeColor(),
                beam.getCoreColor());
    }
}
```

两个类都不再保留 [C2-S4] 中逐行解释的注释。从本章起，注释只说明代码本身无法表达的意图。

### 进游戏核对

执行 `gradle jar`，重启游戏，在测试战役中操控装有都林与乌里的舰船。都林的爆炸概率是 0.25，一场战斗里打出的都林弹足够多，声音会反复出现。

- 都林命中敌舰船体，多打几轮，会出现额外的橙色爆炸并带一声爆响；命中护盾则没有这一声；
- 乌里照射敌舰船体，充能声从低到高，每满半秒放出一道电弧，声音回落；
- 同一门乌里移到护盾上，充能声在半秒内淡出，电弧不再出现；
- 两门乌里同时照射同一目标，能听到两份充能声，音调各自随自己的进度变化。

## 界面音效与音乐

战场之外的声音不带位置，也没有远近变化。`playUISound(id, pitch, volume)` 响一次，原版的按钮音、声望变化提示都用它，例如 `ui_rep_raise`。[C10-S2] 在玩家首次接触齿轮工业时用它给出提示。需要持续的界面音用 `playUILoop(...)`，规则与战场循环音相同：每次推进都要再调一次，原版 `BarCMD` 用它做对话音量随渐显变化的循环。

音乐默认由游戏按场景挑选，[C5-S1] 登记的市场音乐走的就是这条路。要在特定场合换成自己的曲目，用 `playCustomMusic(fadeOut, fadeIn, musicSetId)`：淡入淡出以整秒计，`musicSetId` 传 `null` 表示只停当前曲目、不接新的。另有一个带 `looping` 的重载。

**换曲不只是调一个方法。** 默认音乐在曲目结束后会自动接下一首，自定义曲目放完的瞬间就会被打断。要让它真正占住这段时间，得先关掉默认播放：

```java
Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
Global.getSoundPlayer().playCustomMusic(2, 1, "GearIn_theme");
```

`setSuspendDefaultMusicPlayback(true)` 不打断正在响的曲子，只拦下后续自动接续。用完之后调 `setSuspendDefaultMusicPlayback(false)` 放回去，否则整场生涯都不再有背景音乐。

原版剧情任务 `TheUsurpers` 在关键对话里就是这样成对调用的：切入专属曲目时置为 `true`，对话结束恢复默认音乐时置回 `false`。齿轮工业目前没有强制换曲的场景，这一段先留作参考，[C19-S5] 的音乐设置面板会实际用到。

## 常见问题

- **代码中播放的声音完全听不到，日志中出现声音 ID 找不到的记录**：`sounds.json` 中没有这个 ID，或文件路径错误。
    - **解决方案**：核对 ID 拼写与 `file` 路径，原版文件的路径从 `sounds/` 开始书写。

- **循环音只响一下就消失**：`playLoop(...)` 只在某一帧调用，而不是每帧调用。
    - **解决方案**：把调用放在每帧都会执行的位置，例如 `advance(...)` 中。

- **两门武器同时开火，只能听到一份循环音**：两次调用传入了同一个播放者对象。
    - **解决方案**：传入能区分各自来源的对象，例如光束或武器本身。

- **声音听起来始终在屏幕中央，没有远近变化**：音频文件是立体声。
    - **解决方案**：位置声音改用单声道文件。

## 建议参考项

- `com.fs.starfarer.api.SoundPlayerAPI`：全部播放方法，本节用到的 `playSound(...)`、`playLoop(...)`、`playUISound(...)` 都在这个接口上。
- `com.fs.starfarer.api.SoundAPI`：`playSound(...)` 的返回值，可以在播放中调整音量或停止。
- `com.fs.starfarer.api.impl.campaign.missions.askonia.TheUsurpers`：剧情对话中切换与结束自定义音乐的写法。
- `data/config/sounds.json`：原版全部声音 ID，借用原版文件时从这里查找路径。
