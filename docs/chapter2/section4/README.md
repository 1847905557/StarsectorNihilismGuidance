# 为武器加入命中与光束特效

数值是有尽头的。您能想到的所有数据大概已经被排列组合地用了个遍。该怎么别在多样性的海洋里被水淹没不知所措呢？只能想办法在特效上入手了。

恰好，特效对于武器的决定性也是相当可观的。原版也有不少面板平平无奇，实战远超同级的例子。一门武器在舰船上扮演什么角色，甚至可能由某些占主导地位的特效而决定。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S5] 的都林自动炮与乌里光束，本节为它们加入特效；
- [C1-S5] 的 `.wpn` 与 `.proj` 文件，特效类在其中登记；
- [C2-S1] 的外行读代码与回调写法，特效类的方法由游戏在固定时机调用；
- [C2-S1] 的 `script` 类名写法，`onHitEffect` 与 `beamEffect` 同样填完整类名。

## 两种特效的入口

射弹类武器的命中效果挂在弹体 `.proj` 的 `onHitEffect` 字段上，每发弹体命中时调用一次。光束武器的效果挂在 `.wpn` 的 `beamEffect` 字段上，光束开着的每一帧都调用一次。两个字段都填效果类的完整类名，写法与船插的 `script` 列相同。

| 字段          | 写在哪个文件    | 调用时机           | 效果类实现的接口     |
| ------------- | --------------- | ------------------ | -------------------- |
| `onHitEffect` | 弹体 `.proj`    | 每发弹体命中时一次 | `OnHitEffectPlugin`  |
| `beamEffect`  | 光束武器 `.wpn` | 光束开启期间每帧   | `BeamEffectPlugin`   |

前面几节的效果类都用 `extends` 继承一个基类；这两个效果类改用 `implements` 实现一个接口。区别在第六章以后才需要关心，此时照写即可。

## 都林：命中船体时追加爆炸

都林是高爆自动炮，每发 35 点高爆伤害。高爆伤害对护盾效果差，对装甲和船体效果好，这是它的定位。编者顺着这个定位加效果：**只有命中船体时**，每发有 25% 的概率在命中点追加一次 20 点高爆伤害的爆炸。命中护盾的弹体不触发效果。

原版离子炮的命中效果给了现成的判断写法：先排除命中护盾的情况，再确认目标是一艘船，最后掷一次概率。新建 `data/scripts/gearin/combat/GearInDurinOnHitEffect.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 都林命中船体时，按概率追加一次高爆爆炸。 */
public class GearInDurinOnHitEffect implements OnHitEffectPlugin {

    /** 每发命中触发的概率。 */
    private static final float CHANCE = 0.25f;

    /** 追加的高爆伤害。 */
    private static final float BONUS_DAMAGE = 20f;

    private static final Color EXPLOSION_COLOR = new Color(255, 120, 60, 255);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        // 命中护盾、目标不是舰船或未达到触发概率时，直接结束。
        if (shieldHit || !(target instanceof ShipAPI) || Math.random() > CHANCE) {
            return;
        }
        // 命中点的爆炸只是画面，伤害由下一行单独结算。
        engine.spawnExplosion(point, new Vector2f(), EXPLOSION_COLOR, 40f, 0.3f);
        engine.applyDamage(target, point, BONUS_DAMAGE, DamageType.HIGH_EXPLOSIVE, 0f, false, false,
                projectile.getSource());
    }
}
```

游戏调用 `onHit` 时，把这次命中的全部信息作为参数传进来：弹体、目标、命中点、是否命中护盾、伤害结果和战斗引擎。本例用到其中四个：

- `shieldHit` 为 `true` 表示命中护盾，这时直接 `return` 结束，不做任何事；
- `target instanceof ShipAPI` 检查目标是不是舰船，排除导弹、小行星这类目标；
- `Math.random()` 每次给出一个 0 到 1 之间的随机数，大于 0.25 时结束，所以只有约四分之一的命中会往下走；
- `engine` 是战斗引擎，爆炸和伤害都通过它产生。

`spawnExplosion` 只生成爆炸的画面，括号里依次是位置、速度、颜色、大小和持续秒数，本身不造成伤害。伤害由 `applyDamage` 单独结算：目标、位置、伤害量、伤害类型、EMP 量，后面两个 `false` 分别表示不穿透护盾、不产生软幅能，最后一个参数把伤害记在发射这发弹体的舰船名下。

这个效果的分量需要算清楚。都林每秒约发射 7 发，基础输出约 250 点高爆每秒；追加爆炸平均每秒 7 × 0.25 × 20 = 35 点，只在命中船体时生效，相当于命中船体时的输出提高约 14%。编者有意把它压在这个幅度：都林本身已是 4 装配点的小炮，追加效果的作用是提高它在护盾失效之后的输出，不改变它对护盾效果差的短板。

### 在弹体文件中登记

打开 `data/weapons/proj/GearIn_durin_shot.proj`，在顶层对象中加入一个字段，注意给前一行补上逗号：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInDurinOnHitEffect"
```

都林的 `.wpn` 通过 `projectileSpecId` 引用这个弹体，所以登记在弹体上，武器发射的每一发都带着这个效果。

### 进游戏核对都林

保存后重启游戏，给工匠装上都林，进模拟战。先攻击一艘开启护盾的敌舰：弹体命中护盾时，不应出现追加爆炸。等对方护盾过载，或绕到护盾覆盖不到的一侧，使弹体命中船体：每隔几发就会出现一次橙色爆炸，敌舰结构值的下降比只有弹体伤害时更快。

想确认是不是自己的代码在起作用，把 `CHANCE` 临时改成 `1f`、重启，此时命中船体的每一发都应伴随一次爆炸。确认之后改回 `0.25f`。

## 乌里：照射船体时放出电弧

乌里是能量光束，每秒 175 点能量伤害，射程 1000。光束的特点是持续照射，所以编者给它的效果也按照射时间结算：光束以满亮度照射舰船船体时，每 0.5 秒从光束末端向目标放一道电弧，带 40 点能量伤害和 200 点 EMP。EMP 会让被照射部位的武器和引擎暂时失灵，乌里因此从单纯的伤害光束变成一件能压制对手火力的武器。

光束效果每帧调用一次，不能每次调用都放一道电弧。参照原版离子光束的做法，在类里用一个字段累计时间，累计满 0.5 秒再放一次。新建 `data/scripts/gearin/combat/GearInUriBeamEffect.java`：

```java
package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;

/** 乌里满亮度照射舰船船体时，每隔一段时间放出 EMP 电弧。 */
public class GearInUriBeamEffect implements BeamEffectPlugin {

    /** 两次电弧之间的秒数。 */
    private static final float ARC_INTERVAL = 0.5f;

    private static final float ARC_DAMAGE = 40f;
    private static final float ARC_EMP = 200f;

    /** 距离上一次电弧累计的秒数，在多次调用之间保留。 */
    private float elapsed = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        // 照射对象不是舰船，或光束未达到满亮度时，不计时。
        if (!(target instanceof ShipAPI) || beam.getBrightness() < 1f) {
            return;
        }
        Vector2f point = beam.getRayEndPrevFrame();
        // 光束末端落在护盾范围内，说明照的是护盾，同样不计时。
        if (target.getShield() != null && target.getShield().isWithinArc(point)) {
            return;
        }
        elapsed += amount;
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

`advance` 的三个参数依次是这一帧经过的秒数、战斗引擎和光束本身。代码从上往下做了几道排除：

- `beam.getDamageTarget()` 取得光束正在照射的对象，不是舰船就结束；
- `beam.getBrightness()` 是光束亮度，开火和停火的渐变阶段小于 1，只有满亮度时才往下走；
- `target.getShield().isWithinArc(point)` 判断光束末端是否位于护盾覆盖的角度内，位于其中就视为照射护盾，不计时。原版离子光束用的也是这个判断。

通过这几道排除之后，`elapsed` 加上这一帧的秒数。累计满 0.5 秒时清零，再调用 `spawnEmpArc` 放电弧。它的参数较多，依次是：伤害记在谁名下、电弧起点、电弧挂靠的对象、电弧命中的对象、伤害类型、伤害量、EMP 量、最大距离、命中音效、粗细、外圈颜色和内芯颜色。最大距离填一个很大的数，表示不限距离；音效沿用原版离子光束的 `tachyon_lance_emp_impact`；颜色直接取这道光束自身的颜色，电弧就和光束同色。

`elapsed` 写在方法外面，作为类的字段。写在方法里面的变量每次调用都会重新开始，写在外面的字段则在多次调用之间保留，所以能一帧一帧地累计下去。

这个效果的分量同样要算。电弧伤害每秒 2 × 40 = 80 点，约为光束本身的 46%；但它只在照射船体时生效，而乌里作为长射程光束，大部分时间照射的是护盾。真正有分量的是每秒 400 点 EMP，它让护盾失效的敌舰很快失去武器和引擎。编者测试时，乌里与都林配在同一艘工匠上：乌里压制对方的武器，都林在护盾失效后攻击船体，两者的效果是互相衔接的。

### 在光束武器文件中登记

打开 `data/weapons/GearIn_uri.wpn`，在顶层对象中加入：

```json
"beamEffect": "data.scripts.gearin.combat.GearInUriBeamEffect"
```

`beamEffect` 只对 `specClass` 为 `beam` 的武器有效，乌里的 `.wpn` 正是这一类，所以这次登记在武器文件上，不在弹体上。

### 进游戏核对乌里

保存后重启游戏，给工匠装上乌里，进模拟战。先照射一艘开启护盾的敌舰：光束照射护盾时，不应出现电弧。等对方护盾过载，或照射护盾覆盖不到的部位，持续照射：每隔半秒左右，光束末端会放出一道与光束同色的电弧，被照射部位的武器和引擎陆续变灰失灵。

把 `ARC_INTERVAL` 临时改成 `0.1f`、重启再照，电弧应明显变密，说明计时由这个字段控制。确认之后改回 `0.5f`。

## 常见问题

- **命中或照射时什么都没发生**：字段写进了错误的文件，或 `beamEffect` 写在了非光束武器上。
    - **解决方案**：`onHitEffect` 写在都林的 `.proj`，`beamEffect` 写在乌里的 `.wpn`，保存后重启。

- **启动时报 `Problem loading class [`**：字段里的完整类名与源码的 `package`、类名或文件位置不一致。
    - **解决方案**：按完整类名逐段核对目录与文件名，保存后重启。

- **加载武器时报 `JSONException`**：新加字段的前一行缺少逗号。
    - **解决方案**：给新字段前一行的末尾补上逗号，保存后重启。

- **命中护盾时也出现爆炸或电弧**：护盾判断被删掉，或条件写反。
    - **解决方案**：都林保留 `shieldHit` 的判断，乌里保留 `isWithinArc` 的判断，对照完整代码修正。

## 建议参考项

- `com.fs.starfarer.api.impl.combat.IonCannonOnHitEffect`：原版离子炮的命中效果，先排除护盾再掷概率的判断写法来自它。
- `com.fs.starfarer.api.impl.combat.IonBeamEffect`：原版离子光束的效果，累计时间后放电弧的做法来自它。
- `data/weapons/proj/ioncannon_shot.proj`：`onHitEffect` 字段的登记样本。
- `data/weapons/ionbeam.wpn`：`beamEffect` 字段的登记样本。
- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：命中效果的接口。
- `com.fs.starfarer.api.combat.BeamEffectPlugin`：光束效果的接口。
