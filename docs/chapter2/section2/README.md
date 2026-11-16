# 制作完整船插

玩家装配船插时看不到代码，只看得到描述、图标和能不能装上。藏匿效果描述、数字与实际不符、图标缺失，玩家就不会觉得它是个值得多看一眼的东西。

**不要敷衍玩家唯一能看到的部分。**

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C2-S1] 的稳定化推进器：效果类 `GearInStableInjectorHullMod` 与 `hull_mods.csv` 中的那一行，本节在它们之上补全；
- [C2-S1] 的回调写法，方法名与参数列表必须与基类一致。

## 让描述跟着参数变

上一节在 `desc` 里写了一句固定文字，数字则藏在代码的 `SPEED_BONUS` 表里。改了表中的数值，描述不会跟着变，玩家看到的航速和实际得到的航速就会对不上。

原版不稳定喷射器的描述里没有写死数字，而是留了几个 `{%s}`：

```text
根据船体级别，提高 {%s}/{%s}/{%s}/{%s} 的最高航速。
```

`{%s}` 是一个占位符，花括号让它在装配界面里高亮显示。游戏显示描述时，调用效果类的 `getDescriptionParam(...)`，按顺序把返回的文字填进每个占位符。稳定化推进器照此改造：`desc` 用四个 `{%s}` 对应四档船体，代码负责返回四个数字。

把 `hull_mods.csv` 里这一行的 `desc` 改成带占位符的写法：

```csv {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"根据船体级别，提高 {%s}/{%s}/{%s}/{%s} 的最高航速。不兼容 不稳定喷射器。",提高航速。,,graphics/hullmods/GearIn_stable_injector.png
```

`desc` 用双引号括起来，因为里面有逗号。末尾的 `sprite` 这次填上了图标路径，`short` 仍是不带数字的简述。

### 在效果类里返回这四个数字

给上一节的效果类加一个 `getDescriptionParam` 方法。它从同一张 `SPEED_BONUS` 表取值，所以改表里的数字时，描述和实际加成一起变：

```java
@Override
public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) {
        return "" + ((Float) SPEED_BONUS.get(HullSize.FRIGATE)).intValue();
    }
    if (index == 1) {
        return "" + ((Float) SPEED_BONUS.get(HullSize.DESTROYER)).intValue();
    }
    if (index == 2) {
        return "" + ((Float) SPEED_BONUS.get(HullSize.CRUISER)).intValue();
    }
    if (index == 3) {
        return "" + ((Float) SPEED_BONUS.get(HullSize.CAPITAL_SHIP)).intValue();
    }
    return null;
}
```

`index` 从 0 数起，第 0 个占位符对应护卫舰，依次到第 3 个的主力舰。`(Float) SPEED_BONUS.get(...)` 把表里的值取出来，同样要加 `(Float)` 转换，`intValue()` 再把 `15f` 变成整数 `15`，前面的 `"" +` 把它接成一段文字。四个占位符需要四个返回值；少一个，描述里就会显示 `null`。

## 准备一张图标

船插需要一张 32×32 的 PNG 图标，放进 `graphics/hullmods/GearIn_stable_injector.png`，路径与 `sprite` 列一致。本例使用的图标如下：

![稳定化推进器的装配图标](./GearIn_stable_injector.png 'GearIn_stable_injector.png')

图标是贴图，不经代码。`sprite` 列留空时，游戏用一张缺省图标顶替，不会报错，但多个船插共用同一张缺省图标时，玩家在装配界面中无从区分。

## 和不稳定喷射器二选一

稳定化推进器提供的也是航速加成，和原版不稳定喷射器功能重叠。两者都是装在引擎上的推进部件，同时安装会让航速叠加到两者各自的设计范围之外。编者因此限制它们不能同时安装。

船插基类提供了两个方法处理安装限制：`isApplicableToShip(...)` 返回能不能装，`getUnapplicableReason(...)` 返回不能装时显示给玩家的原因。两个方法检查同一个条件，即这艘船上有没有不稳定喷射器：

```java
@Override
public boolean isApplicableToShip(ShipAPI ship) {
    return ship != null && !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
}

@Override
public String getUnapplicableReason(ShipAPI ship) {
    return "不兼容 不稳定喷射器";
}
```

`ship.getVariant()` 取得这艘船当前的装配，`hasHullMod(...)` 检查其中有没有指定 id 的船插，`!` 把结果取反，没装不稳定喷射器时才允许装本船插。`UNSTABLE_INJECTOR` 是一个常量，保存原版 id `unstable_injector`。

这里有一处必须说清的限制：**这条检查只写在稳定化推进器一侧。** 先装了稳定化推进器，再装不稳定喷射器时，轮到原版那一侧判断，而原版的代码并不认识齿轮工业的船插，拦不住。换句话说，当前只能保证 "有不稳定喷射器时装不上稳定化推进器" 这一个方向。要两个方向都互斥，得改到原版船插，而改原版文件会影响到所有用到它的地方，代价过大。编者在这里只做单向限制，另一个方向靠玩家自己注意；双向互斥的做法留到 [C9-S1]。

## 完整成品

效果类保存到 `data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`，在上一节的基础上加了 `getDescriptionParam` 与两个安装检查方法：

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 稳定化推进器：按船体级别提高最高航速，与不稳定喷射器互斥。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

    /** 原版不稳定喷射器的 id，用于互斥检查。 */
    private static final String UNSTABLE_INJECTOR = "unstable_injector";

    /** 船体级别与航速加成的对应表。 */
    private static final Map SPEED_BONUS = new HashMap();

    static {
        SPEED_BONUS.put(HullSize.FRIGATE, 15f);
        SPEED_BONUS.put(HullSize.DESTROYER, 10f);
        SPEED_BONUS.put(HullSize.CRUISER, 5f);
        SPEED_BONUS.put(HullSize.CAPITAL_SHIP, 5f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BONUS.get(hullSize));
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.FRIGATE)).intValue();
        }
        if (index == 1) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.DESTROYER)).intValue();
        }
        if (index == 2) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CRUISER)).intValue();
        }
        if (index == 3) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CAPITAL_SHIP)).intValue();
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "不兼容 不稳定喷射器";
    }
}
```

### 进游戏核对

保存效果类、CSV 和图标，重启游戏，进入装配界面。在护卫舰、驱逐舰、巡洋舰、主力舰上分别悬停稳定化推进器，描述里的四个数字应是高亮的 15/10/5/5，与各档实际航速加成一致。

把 `SPEED_BONUS` 表里护卫舰的 `15f` 改成 `25f`，保存并重启，护卫舰的描述和实际加成应同时变成 25。确认之后改回 `15f`。

再测互斥：给一艘船装上原版不稳定喷射器，稳定化推进器此时应变灰、装不上，悬停显示 "不兼容 不稳定喷射器"。反过来先装稳定化推进器、再装不稳定喷射器，则不会被拦住，这正是上面说的单向限制。

![稳定化推进器在装配界面中的描述与四档参数](./hullmod_play.png 'hullmod_play.png')

## 常见问题

- **描述里出现 `null`**：`{%s}` 的数量多于 `getDescriptionParam` 提供的返回值。
    - **解决方案**：四个占位符对应 `index` 0 到 3 四个返回值，逐一核对后重启。

- **描述数字不高亮**：`desc` 里用的是 `%s` 而不是 `{%s}`。
    - **解决方案**：按原版写法给占位符加上花括号，保存后重启。

- **装配界面里图标是一张灰图**：`sprite` 路径与实际文件位置不符，或图标没放进 `graphics/hullmods/`。
    - **解决方案**：核对 `sprite` 列与文件路径，确认 PNG 已就位，保存后重启。

- **互斥没有生效**：检查用的 id 写错，或测试的是先装稳定化推进器的那个方向。
    - **解决方案**：确认常量是原版 id `unstable_injector`，并按 "先装不稳定喷射器" 的方向测试。

## 建议参考项

- `data/hullmods/UnstableInjector.java`：描述占位符 `{%s}` 与 `getDescriptionParam` 的原版写法。
- `graphics/hullmods/`：原版船插图标，可对照尺寸与风格。
- `com.fs.starfarer.api.combat.BaseHullMod`：`isApplicableToShip`、`getUnapplicableReason` 与 `getDescriptionParam` 的定义。
