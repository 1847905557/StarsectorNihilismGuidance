# 制作战术系统与右键战术系统

实际上，战术系统的设计才是决定舰船的最主要部分，战术系统对于舰船角色的影响，甚至远甚于它的属性、槽位等。也许只有在其它地方大量加 0 的 MOD 才可能把战术系统从舰船设计的宝座上拉下来吧。

所以这一部分，编者建议您谨慎考虑。但考虑也是之后的事，先做。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 读原版加速填弹器

工匠现在的主系统是上一章临时挂的加速填弹器。打开它的效果类 `data/shipsystems/scripts/AmmoFeedStats.java`，仍以外行身份逐行推测。原版开头的 `package`、import 与几行注释掉的旧代码在这里省略。

```java
public class AmmoFeedStats extends BaseShipSystemScript { // extends 的基类换成了战术系统用的

    public static final float ROF_BONUS = 1f; // 射速加成，1 大概是 100%
    public static final float FLUX_REDUCTION = 50f; // 幅能降低 50%

    // apply：系统生效期间被反复调用，多出了 state 和 effectLevel 两个参数
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        float mult = 1f + ROF_BONUS * effectLevel; // 1 + 1×effectLevel
        stats.getBallisticRoFMult().modifyMult(id, mult); // 实弹射速乘这个倍率
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f)); // 开火幅能乘 0.5
    }

    // unapply：系统关掉时调用，把 apply 写进去的东西撤掉
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
    }

    // getStatusData：左下角状态栏里那几行字
    public StatusData getStatusData(int index, State state, float effectLevel) {
        float mult = 1f + ROF_BONUS * effectLevel;
        float bonusPercent = (int) ((mult - 1f) * 100f);
        if (index == 0) return new StatusData("ballistic rate of fire +" + (int) bonusPercent + "%", false);
        if (index == 1) return new StatusData("ballistic flux use -" + (int) FLUX_REDUCTION + "%", false);
        return null;
    }
}
```

和船插相比，这里有以下不同：

- **`apply` 与 `unapply` 成对**。`apply` 在系统生效期间反复执行，`unapply` 在系统关闭时执行。凡是 `apply` 用某个 `id` 写进去的修饰，`unapply` 都要用同一个 `id` 撤掉，否则系统结束了加成还留着。
- **`effectLevel` 是系统的当前强度，取值在 0 到 1 之间。** 系统按下后有一段前摇，强度从 0 升到 1；全功率阶段保持 1；全功率结束后的后摇里再降回 0。`ROF_BONUS * effectLevel` 这类写法，让加成随这条曲线上升和回落。原版的幅能降低没有乘 `effectLevel`，所以它在系统一激活时就直接到位。

`getStatusData` 按 `index` 返回状态栏的每一行，返回文字是写死的字面量，游戏不翻译。本例沿用原版的英文，和后面进游戏看到的状态栏一致。

## 从机动推进器借来机动部分

突击填弹器不只加射速。编者想让工匠在开火的同时能够接近和转向，让这几秒成为主动接敌的时机，而不是停在原地射击。机动部分从原版机动推进器 `ManeuveringJetsStats.java` 借。这个类长一些，只看它 `apply` 里真正改数值的几行 (原版还有一段被 `&& false` 永久关掉的调试代码，略去)：

```java
public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    if (state == ShipSystemStatsScript.State.OUT) { // 后摇阶段单独处理
        stats.getMaxSpeed().unmodify(id); // 提前把航速收回，免得后摇还在飞
        stats.getMaxTurnRate().unmodify(id);
    } else {
        stats.getMaxSpeed().modifyFlat(id, 50f); // 最高航速 +50，固定值
        stats.getAcceleration().modifyPercent(id, 200f * effectLevel); // 加速 +200%
        stats.getDeceleration().modifyPercent(id, 200f * effectLevel); // 减速 +200%
        stats.getTurnAcceleration().modifyFlat(id, 30f * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, 200f * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, 15f);
        stats.getMaxTurnRate().modifyPercent(id, 100f);
    }
}
```

这里出现一个新东西：`state`。它是系统当前所处的阶段，`State.OUT` 表示后摇。原版在后摇时提前把航速和转向收回，让船在动力降下来的同时就慢下来，不会靠着惯性一直冲。本例不需要这么精细，可以把航速也交给 `effectLevel`，后摇时它自然回落。

需要的就是这两段里改数值的语句。把它们拼进同一个效果类，是接下来要做的事。

## 把两段效果拼成突击填弹器

新建 `data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java`。它的骨架和加速填弹器一样，`apply` 里先写射速和幅能，再接上机动几行；`unapply` 把每一项都撤掉：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

/** 突击填弹器：实弹射速与机动同时提升的主动系统。 */
public class GearInAssaultFeedSystem extends BaseShipSystemScript {

    public static final float ROF_BONUS = 1f;
    public static final float FLUX_REDUCTION = 50f;
    public static final float SPEED_BONUS = 50f;
    public static final float MANEUVER_BONUS = 200f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getBallisticRoFMult().modifyMult(id, 1f + ROF_BONUS * effectLevel);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - FLUX_REDUCTION * 0.01f);
        stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
        stats.getAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getDeceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, 15f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("+" + (int) (SPEED_BONUS * effectLevel) + " top speed", false);
        }
        if (index == 1) {
            return new StatusData("improved maneuverability", false);
        }
        if (index == 2) {
            return new StatusData("ballistic rate of fire +" + (int) (ROF_BONUS * effectLevel * 100f) + "%", false);
        }
        if (index == 3) {
            return new StatusData("ballistic flux use -" + (int) FLUX_REDUCTION + "%", false);
        }
        return null;
    }
}
```

本例把机动比原版简化了：航速交给 `effectLevel`，省掉了原版的后摇特判和转向加速的固定项。满功率时，实弹射速 +100%、开火幅能 -50%、最高航速 +50、机动明显提升。射速与幅能都只作用于实弹，所以工匠配的实弹武器越多，这个系统越划算；若换上能量武器，它就只剩机动部分还有用。这是编者给工匠定的方向：一艘靠实弹近身压制的驱逐舰。

### 登记系统定义与 CSV

系统和船插一样，由两份数据加一个脚本组成：一份 `.system` 定义文件声明类型、AI 和脚本，一行 `ship_systems.csv` 登记时间与幅能，最后舰船数据挂上它的 id。

把原版 `data/shipsystems/ammofeed.system` 复制为 `GearIn_assaultfeed.system`，改成：

```json
{
    "id": "GearIn_assaultfeed",
    "type": "STAT_MOD",
    "aiType": "WEAPON_BOOST",
    "statsScript": "data.scripts.gearin.shipsystems.GearInAssaultFeedSystem",
    "weaponGlowColor": [255, 200, 0, 155],
    "weaponTypes": ["BALLISTIC"],
    "useSound": "system_ammo_feeder",
    "outOfUsesSound": "gun_out_of_ammo"
}
```

`statsScript` 指向刚写的效果类，填完整类名。`type` 为 `STAT_MOD` 表示这是一个改数值的系统，`aiType` 决定电脑怎么用它。

::: warning aiType 必须配得上 type

`aiType` 是这个系统交给 AI 的使用策略。它不能随便填：不同的 `aiType` 往往只能用在它对应的那一种 `type` 上，配错了，舰船在战斗里按下 `U` 交给 AI 接管的瞬间就会报错。`WEAPON_BOOST` 对应的就是加速填弹器这类属性强化系统，这里照抄原版，先不要改动。各种 `aiType` 的适用范围与自定义系统 AI 的写法，留到 [C9-S2]。

:::

在 `data/shipsystems/ship_systems.csv` 追加一行 (文件没有时连表头一起建)：

```csv
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
突击填弹器,GearIn_assaultfeed,,,,,,,,,,0.5,3,0.5,10,,,,,,,,,,,,offensive,graphics/icons/hullsys/ammo_feeder.png
```

这一行里真正决定手感的是四个时间列：`charge up` 0.5 秒前摇、`active` 3 秒全功率、`down` 0.5 秒后摇、`cooldown` 10 秒冷却。前摇和后摇就是 `effectLevel` 爬升和回落的那两段。编者把全功率压到 3 秒、冷却留到 10 秒，是要让玩家挑时机用它，而不是一冲上去就按。调完数值进游戏试过几轮，3 秒刚好够打完一轮近身，再长就没有收放的节奏了。

最后把工匠挂到这个系统。打开 MOD 的 `data/hulls/ship_data.csv`，把工匠那一行的 `system id` 列填成 `GearIn_assaultfeed`。这一步和上一章挂系统的改法相同，只换 id。

### 进游戏核对突击填弹器

保存效果类、`.system`、`ship_systems.csv` 和工匠数据，重启游戏。拿到工匠，配上实弹武器，进模拟战，按 `F` 激活系统。前摇 0.5 秒里加成逐渐提高，全功率 3 秒内射速明显变快、船也更灵活，后摇 0.5 秒回落，之后 10 秒冷却。

左下角状态栏会出现以 "突击填弹器" 为标题的四行，和脚本里的四行 `getStatusData` 对应：

![突击填弹器激活时的引擎表现与状态栏](./system_play.png 'system_play.png')

图里多出的 "Zero Flux Engine Boost" 是舰船零幅能时的固有加速，与本系统无关，核对时只看 "突击填弹器" 标题下的四行。

## 再做一个右键抵抗力场

`F` 已经给了突击填弹器，右键原本是工匠的护盾。编者用一个短时减伤顶替护盾：工匠装甲厚、航速慢，护盾对它的意义本就有限；换成抵抗力场之后，它在被集火或面对鱼雷时有一个主动的应对手段，代价是失去持续的护盾防御，每一次使用都必须挑准时机。

右键系统的做法和主系统几乎一样，区别只在舰船数据挂载的字段不同，以及这里要用一个特定的 AI。

::: warning 当前阶段右键系统只做防御型

右键战术系统同样要填 `aiType`。原版当前支持非相位右键战术系统的 AI 只有阻尼力场 (`DAMPER_FIELD`) 这一种。换句话说，想让一个非相位舰船的右键系统被 AI 正常使用，现阶段只能沿用阻尼力场的 AI，也就只能做减伤这类防御效果。编者建议您此时只设计防御型右键系统；别的 AI 要么用不了，要么得等 [C9-S2] 自己写系统 AI。

:::

把原版 `data/shipsystems/damper.system` 复制为 `GearIn_resistfield.system`，换掉 id 和脚本，其余的扰动颜色与声音照搬：

```json
{
    "id": "GearIn_resistfield",
    "type": "STAT_MOD",
    "aiType": "DAMPER_FIELD",
    "statsScript": "data.scripts.gearin.shipsystems.GearInResistFieldSystem",

    "jitterUnderColor": [255, 165, 90, 155],
    "jitterUnderCopies": 25,
    "jitterUnderRange": 7,

    "jitterColor": [255, 165, 90, 55],
    "jitterCopies": 2,
    "jitterRange": 5,

    "soundFilterType": "LOWPASS",
    "soundFilterGain": 0.75,
    "soundFilterGainHF": 0.25,

    "fadeActivationSoundOnChargedown": true,
    "useSound": "system_damper",
    "loopSound": "system_damper_loop",
    "deactivateSound": "system_damper_off",
    "outOfUsesSound": "gun_out_of_ammo"
}
```

`aiType` 这次是 `DAMPER_FIELD`，对应上面说的唯一可用的防御 AI。`jitterUnder*` 和 `jitter*` 是激活时那层橙色扰动，照搬原版即可。

在 `ship_systems.csv` 再追加一行：

```csv
抵抗力场,GearIn_resistfield,1,,,,,,,2,0.05,0.25,3,0.25,0,,,,,TRUE,,,,TRUE,TRUE,,"uses_damper_ai, defensive",graphics/icons/hullsys/damper_field.png
```

`tags` 里的 `uses_damper_ai` 必须保留，它和 `aiType` 一起让这套 AI 正常工作；`defensive` 把它标成防御系统。`noFiring`、`noVent` 等列的 `TRUE` 沿用阻尼力场，表示激活期间不能开火、不能排散幅能。

### 按伤害类型写减伤

新建 `data/scripts/gearin/shipsystems/GearInResistFieldSystem.java`。它不改航速或射速，而是降低舰船受到的伤害。舰船数值里每种伤害类型都有一个 "承受倍率"，倍率 0.25 就表示只承受原来 25% 的伤害：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

/** 抵抗力场：右键激活，短时间降低各类伤害承受。 */
public class GearInResistFieldSystem extends BaseShipSystemScript {

    public static final float HE_REDUCTION = 75f;
    public static final float EMP_REDUCTION = 75f;
    public static final float OTHER_REDUCTION = 50f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getHighExplosiveDamageTakenMult().modifyMult(id, 1f - HE_REDUCTION * 0.01f * effectLevel);
        stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_REDUCTION * 0.01f * effectLevel);
        stats.getKineticDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
        stats.getEnergyDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getHighExplosiveDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
        stats.getKineticDamageTakenMult().unmodify(id);
        stats.getEnergyDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("damage taken reduced", false);
        }
        return null;
    }
}
```

四个 getter 分别是高爆、EMP、动能、能量四种伤害的承受倍率，写法和前面的 `modifyMult` 相同，只是乘的对象换成了 "受到多少伤害"。编者让高爆和 EMP 降得更多 (各 75%)，动能和能量降 50%：工匠装甲厚、弱点在于高爆伤害与 EMP 造成的瘫痪，抵抗力场针对的正是这类集火。减伤都乘了 `effectLevel`，所以前摇时减伤逐渐到位，不是一按就满。

最后修改工匠那一行：`shield type` 由 `FRONT` 改为 `PHASE`，`defense id` 填 `GearIn_resistfield`。`PHASE` 在这里的作用是把右键交给 `defense id` 指定的系统，实际行为由抵抗力场的定义与脚本决定，工匠并不会因此变成相位舰。原版先锋级的右键阻尼力场用的就是这种写法。`shield arc`、`shield upkeep` 与 `shield efficiency` 三列随护盾一起失去作用，可以清空。

### 进游戏核对抵抗力场

重启游戏，进模拟战，右键激活抵抗力场。舰船周围出现一层橙色扰动，左下角显示减伤状态；此时让敌舰打工匠，装甲与结构值的下降应明显变慢，高爆与 EMP 尤其明显。持续时间结束后扰动消失，减伤撤销。

![抵抗力场激活时的橙色扰动](./right_play.png 'right_play.png')

主系统和右键系统分别显示在舰船详情的 System 与 Special 两处，名称各来自自己的那行系统数据：

![工匠详情里的突击填弹器与抵抗力场](./right_show.png 'right_show.png')

## 常见问题

- **加载时报 `Fatal: ship_systems.csv is missing systems`**：`.system` 定义里有这个 id，但 `ship_systems.csv` 缺对应行，或那行被注释。
    - **解决方案**：确认 CSV 里登记了同一个 id，行首无 `#`，保存后重启。

- **按 `U` 交给 AI 后立刻报错**：`aiType` 与 `type` 不匹配，或右键系统用了阻尼力场以外的 AI。
    - **解决方案**：主系统照抄原版 `WEAPON_BOOST`，右键系统用 `DAMPER_FIELD` 并保留 `uses_damper_ai` 标签。

- **系统一按就结束**：四个时间列全为空或为 0，阶段在一帧内走完。
    - **解决方案**：填上 `charge up`、`active`、`down`、`cooldown`，保存后重启。

- **系统结束后加成还在**：`unapply` 漏撤了某一项。
    - **解决方案**：对照 `apply` 里每一个 `modify`，在 `unapply` 补上同 id 的 `unmodify`，重启后完整跑一个周期。

- **部署时报 `Ship system [...] not found`**：工匠的 `system id` 或 `defense id` 拼错，或系统没登记。
    - **解决方案**：核对工匠两列的 id 与两份 `.system`、CSV 一致。
