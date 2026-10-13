# 制作完整船插

上一节的加速器只提供固定百分比加成，本节把它扩展为一个实际可用的速度船插：按船体等级设置不同数值，让描述随参数更新，并限制它与原版不稳定推进器同时安装。齿轮工业的 "稳定化推进器" 采用较小的速度收益，保留武器射程和甲板性能。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 用一条 CSV 数据与一个效果类组成船插；
- 核对文件、类名和 `script` 注册值；
- 按船体等级设置参数，用 `%s` 联动描述，并检查安装条件；
- 根据日志、装配说明与实际数值定位注册或效果问题。

## 前置知识

- [C1-S1] 的 MOD 目录与 `GearIn_` 前缀规则；
- [C1-S7] 的 CSV 数据行、描述转义与编码；
- [C2-S1] 的效果文件、参数修改、注册、回调与重启生效流程。

## 以不稳定推进器为基础修改

原版不稳定推进器提高速度，同时影响射程与战机整备。编者准备保留它的分档速度写法，去掉这些代价，再相应降低速度收益。**改造原版效果时，加成和代价需要一起考虑**；单纯去掉所有代价，当然也能做出一个船插，但结果往往只是原版的直接上位替代。

从原版 `data/hullmods/hull_mods.csv` 复制 `unstable_injector` 行到 MOD 的同路径文件，改为 `GearIn_stable_injector`。效果类参考原版 `data/hullmods/UnstableInjector.java`，本例保存为 `data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`。

先给它准备一张 32×32 的 PNG 图标，放入 `graphics/hullmods/GearIn_stable_injector.png`。本例使用的图标如下：

![稳定化推进器的装配图标](./GearIn_stable_injector.png "GearIn_stable_injector.png")

包名仍按文件目录填写，类名与文件名一致，CSV 的 `script` 填完整类名。登记方式与上一节相同。

## 给各船体等级设置速度加成

固定加 15 对不同船型的影响并不一样。本例为护卫舰设 15、驱逐舰设 10、巡洋舰和主力舰设 5；战机档也填 15。这样可以用同一份文件处理不同船体，同时把大的速度增量集中在较小船型上。

在效果类的字段区使用下面的局部片段：

```java
private static final Map<HullSize, Float> SPEED_BONUS = new HashMap<>();
static {
    SPEED_BONUS.put(HullSize.FIGHTER, 15f);
    SPEED_BONUS.put(HullSize.FRIGATE, 15f);
    SPEED_BONUS.put(HullSize.DESTROYER, 10f);
    SPEED_BONUS.put(HullSize.CRUISER, 5f);
    SPEED_BONUS.put(HullSize.CAPITAL_SHIP, 5f);
}
```

`HullSize.FRIGATE` 等名称是游戏的船体等级。`put(...)` 把该等级与加成数值配对；`Map` 在这里作为一张按等级取值的表。调整设计时，直接修改每行末尾的数字即可，`import` 按完整文件复制。

游戏调用 `applyEffectsBeforeShipCreation(...)` 时会传入当前的 `hullSize`。效果方法据此取值，局部片段如下：

```java
Float bonus = SPEED_BONUS.get(hullSize);
if (bonus != null) {
    stats.getMaxSpeed().modifyFlat(id, bonus);
}
```

`get(hullSize)` 取得当前等级的加成，`modifyFlat` 把它作为固定速度写入。回调传入的 `id` 标记这条修饰的来源，沿用这个 ID，反复计算时会更新同一项效果。

**数值放在创建前回调中，生涯和战斗都能使用它。** 当前设计适用于不同船体，因此参数表同时包含战机；它的描述仍以常见四档舰船为主。

## 让描述跟着参数更新

上一节修改了速度，CSV 描述却保持原值。本节改用 `%s` 占位符：

```text
提高舰船最大速度 %s/%s/%s/%s 单位/秒，随船体等级变化。不兼容 不稳定推进器。
```

这段文字填入 `hull_mods.csv` 的 `desc`。游戏显示船插描述时，调用效果类的 `getDescriptionParam(...)`，依次用返回的文字替换占位符。

例如，第一个占位符使用护卫舰参数，局部片段如下：

```java
@Override
public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) {
        return "" + SPEED_BONUS.get(HullSize.FRIGATE).intValue();
    }
    return null;
}
```

`index` 从 0 开始，0 对应第一个 `%s`，1 对应第二个，以此类推。`intValue()` 把 15f 转成整数 15，前面的 `"" +` 将它转为可显示的字符串。完整文件继续返回驱逐舰、巡洋舰和主力舰的三个参数。

**描述与效果读取同一张数值表，调整参数后两者一起变化。** 四个占位符需要四个返回值；缺少对应返回值会在描述中出现 `null`，多出的返回值则没有对应显示位置。

这里使用的是船插的描述接口。以后遇到其他对象时，继续核对该对象实际采用的文本来源与填充方法。

## 加入安装条件

"稳定化推进器" 与 "不稳定推进器" 提供相似功能，本例限制它安装到已有不稳定推进器的舰船上。

`isApplicableToShip(...)` 返回是否允许安装，`getUnapplicableReason(...)` 返回拒绝原因。两个方法都检查同一个装配条件：

```java
@Override
public boolean isApplicableToShip(ShipAPI ship) {
    return !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
}

@Override
public String getUnapplicableReason(ShipAPI ship) {
    if (ship.getVariant().hasHullMod(UNSTABLE_INJECTOR)) {
        return "不兼容 不稳定推进器";
    }
    return null;
}
```

`ship.getVariant()` 取得舰船装配，`hasHullMod(...)` 检查其中是否包含指定船插；`!` 表示取反。完整文件把原版 ID `unstable_injector` 保存在 `UNSTABLE_INJECTOR` 中。

这个判断由稳定化推进器执行，验证时先安装原版不稳定推进器，再尝试安装本船插。需要在两个安装方向都实施互斥时，两种船插各自的安装判断都要包含对应条件。

## 完整最小例

CSV 写入 `data/hullmods/hull_mods.csv`，效果类保存到 `data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`。已有表头时只追加本船插记录，图标使用上面准备的路径。

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"提高舰船最大速度 %s/%s/%s/%s 单位/秒，随船体等级变化。不兼容 不稳定推进器。","提高舰船最大速度。",,graphics/hullmods/GearIn_stable_injector.png
```

`unlocked=TRUE` 让玩家默认解锁；四档装配点分别为 5、10、15、25；`sprite` 提供图标。其余数据沿用已有船插登记规则。

::: details 完整成品：GearInStableInjectorHullMod

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 按船体等级提供速度加成，安装时检查不稳定推进器。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

    /** 与 hull_mods.csv 的 id 列保持一致；其他船插检查本船插时使用该常量。 */
    public static final String ID = "GearIn_stable_injector";

    /** 原版不稳定推进器的 id，用于互斥检查。 */
    private static final String UNSTABLE_INJECTOR = "unstable_injector";

    private static final Map<HullSize, Float> SPEED_BONUS = new HashMap<>();
    static {
        SPEED_BONUS.put(HullSize.FIGHTER, 15f);
        SPEED_BONUS.put(HullSize.FRIGATE, 15f);
        SPEED_BONUS.put(HullSize.DESTROYER, 10f);
        SPEED_BONUS.put(HullSize.CRUISER, 5f);
        SPEED_BONUS.put(HullSize.CAPITAL_SHIP, 5f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Float bonus = SPEED_BONUS.get(hullSize);
        if (bonus != null) {
            stats.getMaxSpeed().modifyFlat(id, bonus);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + SPEED_BONUS.get(HullSize.FRIGATE).intValue();
        }
        if (index == 1) {
            return "" + SPEED_BONUS.get(HullSize.DESTROYER).intValue();
        }
        if (index == 2) {
            return "" + SPEED_BONUS.get(HullSize.CRUISER).intValue();
        }
        if (index == 3) {
            return "" + SPEED_BONUS.get(HullSize.CAPITAL_SHIP).intValue();
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getVariant().hasHullMod(UNSTABLE_INJECTOR)) {
            return "不兼容 不稳定推进器";
        }
        return null;
    }
}
```

:::

## 验证方案

1. 保存效果文件、CSV 和图标，重启游戏，在装配界面查找 "稳定化推进器"。
2. 分别在护卫舰、驱逐舰、巡洋舰和主力舰上查看描述与速度，核对 15、10、5、5 四档加成。
3. 在一艘舰船上先安装原版 "不稳定推进器"，再尝试安装 "稳定化推进器"，观察拒绝原因。
4. 移除原版船插，安装稳定化推进器，进入模拟战比较最大速度与武器射程。
5. 修改参数表中的护卫舰数值，保存并重启，再查看同一档船体的速度和描述。
6. 在 `Starsector/starsector-core/starsector.log` 中查找 `Loading hullmod [稳定化推进器]`，核对相关加载错误。

预期结果：

- 船插默认解锁，图标显示正常，装配点消耗对应当前船型。
- 描述显示 15/10/5/5，四个数字高亮，实际速度按当前档增加。
- 已安装不稳定推进器时，本船插安装被拒，并显示 "不兼容 不稳定推进器"。
- 稳定化推进器提高速度，武器射程与战机整备保留原来的数值。
- 护卫舰参数修改后，描述中的第一项和该档速度同时更新。
- 日志包含本船插的加载记录。

观察装配列表中的图标、四档参数与不兼容说明：

![稳定化推进器在装配界面中的描述参数](./hullmod_play.png "hullmod_play.png")

四个高亮数字由效果类返回，固定说明保存在 CSV 中。验证时把这两部分分别与参数表和描述行核对。

## 故障分析

- **装配界面找不到船插**：注册行未加载，或 `script` 指向了不同的效果类。
    - **解决方案**：核对 CSV 的 ID、文件路径与完整类名，保存后重启。

- **生涯速度加成没有生效**：数值修改写在创建后回调中，该回调只参与战斗对象创建。
    - **解决方案**：将速度修改放入 `applyEffectsBeforeShipCreation`，重启后重新检查。

- **两个船插的加成互相覆盖**：效果使用了相同的硬编码来源 ID。
    - **解决方案**：使用回调传入的船插 `id`，让不同来源各自记录修饰。

- **安装条件没有生效**：判断使用了错误的船插 ID，或只改了说明文字。
    - **解决方案**：确认两个方法都检查 `unstable_injector`，先安装原版船插再测试。

- **描述出现 null**：`%s` 数量超过已提供的描述参数。
    - **解决方案**：按 0、1、2、3 为四个占位符提供返回值，保存后重启。

- **加载时报 JSONException 或 Duplicate key**：必填列留空、列边界错误或重复登记了船插 ID。
    - **解决方案**：按表头核对数据行，填写四档成本并保留唯一 ID。

## 总结

本节把固定加速效果扩展为分档参数、联动描述与安装条件。参数表提供数值，创建前回调写入效果，描述回调返回显示参数，安装方法检查装配条件；各自负责的内容都有明确位置。

以后调整船插时，沿着这些位置修改，比把所有逻辑塞进一个方法更方便。本例采用原版已有的结构，设计判断主要落实在速度收益、装配成本和安装条件上。

## 建议参考项

- `data/hullmods/hull_mods.csv`：船插登记与 `desc`、`script`、`sprite` 字段。
- `data/hullmods/UnstableInjector.java`：原版分档速度与描述填充写法。
- `com.fs.starfarer.api.combat.BaseHullMod`：船插基类和默认实现。
- `com.fs.starfarer.api.combat.HullModEffect`：效果、描述与安装条件的回调。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：最大速度修改入口。
- `com.fs.starfarer.api.combat.ShipAPI.HullSize`：船体等级枚举。
- `com.fs.starfarer.api.impl.hullmods.ShieldShunt`：安装条件与拒绝原因的原版写法。
- `graphics/hullmods/generic_mod.png`：缺省船插图标。
