# 以船插为例初步熟悉代码

从本节起，我们开始阅读原版代码。您此时不必懂 Java，编者也不打算先教。我们以外行的身份打开原版不稳定喷射器的效果类，逐行推测它在做什么，推测到足以动手的程度，就复制一份，改成齿轮工业自己的船插。

外行的推测当然粗糙，但每一处改动都要进游戏核对。推测错了，游戏会在核对那一步报错或给出不符的数值；在只改几十行短脚本的阶段，这比先系统学习 Java 更省时间。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 找到原版船插的两部分

一个船插由两部分组成：`data/hullmods/hull_mods.csv` 中的一行数据，以及这行数据指向的一个效果类。打开原版的 `hull_mods.csv`，找到不稳定喷射器所在的行：

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
不稳定喷射器,unstable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.hullmods.UnstableInjector,"根据船体级别，提高 {%s}/{%s}/{%s}/{%s} 的最高航速。

但干扰了武器定位与机库系统，而导致非导弹类武器射程降低 {%s}，且战机整备时间提高 {%s}。",提高航速，降低武器射程。,,graphics/hullmods/unstable_injector.png
```

`desc` 这一列很长，中间还换了行。远行星号的 CSV 里，字段用双引号括起来之后，直接按回车换行即可，换行是描述文字的一部分，不写 `\n` 这类转义符号。

本节用到的列如下：

| 列                                                          | 作用                                                     |
| ----------------------------------------------------------- | -------------------------------------------------------- |
| `name`                                                      | 装配界面显示的名称                                       |
| `id`                                                        | 船插 ID，舰船装配与其他代码通过它找到这个船插            |
| `tags`                                                      | 标签，`engines` 等决定它在哪些场合出现                   |
| `uiTags`                                                    | 装配界面的分类，如 `引擎`                                |
| `unlocked`                                                  | `TRUE` 表示玩家一开始就已解锁                            |
| `cost_frigate`、`cost_dest`、`cost_cruiser`、`cost_capital` | 护卫舰、驱逐舰、巡洋舰、主力舰四档的装配点消耗，必须填写 |
| `script`                                                    | 效果类的完整类名                                         |
| `desc`                                                      | 装配界面的描述，`{%s}` 是留给代码填写的位置              |
| `short`                                                     | 简短描述                                                 |
| `sprite`                                                    | 图标路径，留空时使用缺省图标                             |

`script` 列写着 `data.hullmods.UnstableInjector`。这是一个**完整类名**：最后一段 `UnstableInjector` 是类名，前面的 `data.hullmods` 是包名。包名中的点号对应目录层级，因此这个类就是 `starsector-core/data/hullmods/UnstableInjector.java`。

## 以外行身份阅读效果类

打开这个文件，内容简直令人眼花缭乱。

但即使以外行的身份，也能做一些简单的解析。下面在每行末尾的 `//` 之后写出一种推测；`//` 之后的文字是注释，游戏不执行。原文件开头的 `package` 一行和几行以 `//` 开头的旧代码在这里省略。

```java
import java.util.HashMap; // import 引入后面要用的东西，暂时不管
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class UnstableInjector extends BaseHullMod { // 类名与文件名相同；extends BaseHullMod 大概表示 "这是一个船插"

    private static Map mag = new HashMap(); // 这是什么？
    static {
        mag.put(HullSize.FRIGATE, 25f); // 似乎是船体级别和数字的对应
        mag.put(HullSize.DESTROYER, 20f); // 25/20/15/15，刚好是描述里的四档航速
        mag.put(HullSize.CRUISER, 15f);
        mag.put(HullSize.CAPITAL_SHIP, 15f);
    }

    private static final float RANGE_MULT = 0.85f; // 射程乘 0.85，也就是降低 15%？
    private static final float FIGHTER_RATE = 25f; // 战机整备时间提高 25%？

    // 方法名直译是 "在舰船创建之前应用效果"，效果应该都在这里
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(id, (Float) mag.get(hullSize)); // 对最高航速进行加成？数字按船体级别从 mag 里取
        stats.getBallisticWeaponRangeBonus().modifyMult(id, RANGE_MULT); // 实弹武器射程乘以 0.85
        stats.getEnergyWeaponRangeBonus().modifyMult(id, RANGE_MULT); // 能量武器射程同上，导弹不受影响

        stats.getFighterRefitTimeMult().modifyPercent(id, FIGHTER_RATE); // 战机整备时间增加 25%
    }

    // "获取描述参数"，index 似乎是第几个
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue(); // 第 0 个：护卫舰的 25
        if (index == 1) return "" + ((Float) mag.get(HullSize.DESTROYER)).intValue(); // 第 1 个：驱逐舰的 20
        if (index == 2) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue();
        if (index == 3) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue();
        if (index == 4) return "" + (int) Math.round((1f - RANGE_MULT) * 100f) + "%"; // (1 - 0.85) × 100 = 15%
        if (index == 5) return "" + (int) Math.round(FIGHTER_RATE) + "%"; // 25%
        return null;
    }
}
```

回头对照 CSV 的描述："根据船体级别，提高 {%s}/{%s}/{%s}/{%s} 的最高航速...射程降低 {%s}，且战机整备时间提高 {%s}。" 描述中恰好有六个 `{%s}`，`getDescriptionParam` 恰好返回第 0 到第 5 个参数，依次是 25、20、15、15、15%、25%。

虽然猜测很粗糙，但也八九不离十了：

- `mag` 是一张按船体级别查数字的表；
- `applyEffectsBeforeShipCreation` 中的每一行修改一项舰船属性，`modifyFlat` 加上固定数值，`modifyMult` 乘以倍率，`modifyPercent` 增加百分比；
- `getDescriptionParam` 把代码里的数字填进描述的 `{%s}`。

`id` 与 `hullSize` 这类参数由游戏在调用方法时传入，我们照写即可。**游戏在规定时机调用这些名称固定的方法，这种方式称为回调。** 方法名和括号里的参数列表必须与原版一致，能改的是大括号内的内容。

## 复制一份改成稳定化推进器

接下来要做的事情，就是大胆地把需要的部分拼在一起。齿轮工业的 "稳定化推进器" 只保留不稳定喷射器的航速加成，去掉射程与战机的代价。

**去掉代价，收益就必须跟着降低。** 否则它只是不稳定喷射器的无代价版本，任何玩家都没有理由再装原版。编者把四档航速定为 15/10/5/5，装配点消耗沿用 5/10/15/25。这样一来，主力舰要花 25 点装配点换 5 点航速，这个船插在大船上本就不划算，也不该靠它提速。与其另写一条安装限制把它挡在大船外，大船上的低性价比已足以让玩家放弃它。

### 效果类

在 MOD 根目录下创建 `data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`，内容如下：

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 稳定化推进器：按船体级别提高最高航速。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

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
        // 按当前船体级别查表，把结果加到最高航速上。
        stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BONUS.get(hullSize));
    }
}
```

与原版相比，改动集中在以下几处：

- **`package`**：原版是 `data.hullmods`，这里改为 `data.scripts.gearin.hullmods`，必须与文件所在目录逐段对应。`data/scripts/` 是 MOD 存放源码的位置，`gearin` 一层把齿轮工业的类与其他 MOD 分开。
- **类名**：改为 `GearInStableInjectorHullMod`，与文件名一致，大小写也一致。
- **表名与数值**：`mag` 改为更好认的 `SPEED_BONUS`，四档数字换成 15/10/5/5。
- **效果**：只保留航速一行，射程与战机两项删除；描述参数方法暂时删除，[C2-S2] 再加回来。

`@Override` 标记这个方法来自船插基类，写错方法名时，它能让加载阶段直接报错，而不是让效果悄悄失效。原版没有写，编者建议写上。

::: warning 照搬原版的 Map 写法

游戏启动时自行编译 MOD 中的 `.java` 源码，它使用的编译器不支持一部分较新的 Java 写法。网上常见的 `new HashMap<>()`、`Map<HullSize, Float>` 等带尖括号的写法，放进这张表会导致加载失败。本章的表一律照原版写成 `new HashMap()`，取值时用 `(Float)` 转换。

:::

### 登记数据

在 MOD 的 `data/hullmods/hull_mods.csv` 中写入下面的内容。文件不存在时连同表头一起新建；已有表头时只追加第 2 行：

```csv {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,根据船体级别，提高最高航速。,提高航速。,,
```

`script` 列填写刚才的完整类名，`package` 与类名缺一不可。`tags` 去掉了原版的 `standard`，其余数据沿用原版。`desc` 暂时不带数字，`sprite` 暂时留空，两者都在下一节补上。

### 进游戏核对

保存两个文件，重启游戏，进入生涯的装配界面，给工匠安装 "稳定化推进器"。工匠是驱逐舰，原本的最高航速为 80，安装后应显示 90。再找一艘护卫舰安装，最高航速应增加 15。

随后把 `DESTROYER` 一行的 `10f` 改成 `30f`，保存并重启，工匠的最高航速应变为 110。这一步确认游戏读取的确实是您刚才编辑的文件。确认之后改回 `10f`。

**修改 `.java` 文件后必须重启游戏才能生效。** 游戏只在启动时编译一次源码。

## 常见问题

- **装配界面找不到稳定化推进器**：`hull_mods.csv` 不在 MOD 的 `data/hullmods/` 下，或这一行被 `#` 注释。
    - **解决方案**：核对文件位置，确认行首没有 `#`，保存后重启。

- **启动时报错，日志中出现 `Error loading [`**：`script` 列、`package`、文件目录与类名四者没有指向同一个类，或源码中有拼写错误。
    - **解决方案**：逐段核对 `data.scripts.gearin.hullmods.GearInStableInjectorHullMod`，再按日志给出的行号检查源码。

- **加载时报 `JSONException`**：四档 `cost_*` 有留空，或逗号数量不对导致列错位。
    - **解决方案**：对照表头补齐四档数字与空列逗号，以 UTF-8 无 BOM 保存后重启。

- **改了数字但航速不变**：修改的是另一份源码，或改完没有重启。
    - **解决方案**：确认编辑的是当前 MOD 中的文件，保存后完整重启游戏。
