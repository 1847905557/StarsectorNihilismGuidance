# 制作完整船插

本节将复用 [C2-S1] 介绍的船插目标，制作一个相对完整的船插。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出船插的组成：`hull_mods.csv` 的一行数据 + 一个 Java 效果类；
- 用类名三处一致规则核对任何船插：文件名、`class` 后的名字、CSV `script` 列；
- 读懂效果类中四类内容：按舰船大小给出的数值参数、描述联动 (`%s`)、安装限制 (互斥)、固定句式 (`modifyFlat`/`modifyPercent`)；
- 独立排查本节新增的加载中断 (`JSONException`/`Duplicate key`) 与效果类故障。

## 前置知识

- [C1-S1] 的 MOD 目录与 `GearIn_` 前缀规则；
- [C1-S2] 的 CSV 数据行读取方法；
- [C2-S1] 的 Java 模板、参数、注册、回调与重启生效流程。

## 注册船插数据

在 MOD 的 `data/hullmods/hull_mods.csv` 写入 (文件不存在就新建，只包含本 MOD 的行；表头行必须保留)。20 列中，`id`、`cost_*`、`script` 三列沿用 [C2-S1] 的规则 (唯一前缀、四档必填、script 指向效果类名)；本节新增三列：

- **`unlocked`**：`TRUE` = 玩家默认解锁。
- **`desc`**：装配界面显示的描述。`desc` 列支持 `%s` 占位符，由效果类的 `getDescriptionParam` 按 `index` 顺序填充，组合示例见本节完整最小例。
- **`sprite`**：图标路径。本节不准备贴图 —— 留空也能用 (游戏退回默认图标 `graphics/hullmods/generic_mod.png`)，想换图标时把 32×32 的 PNG 放到该路径即可。

## 实现船插效果

效果类比上一节的 "加速器" 多三样东西 (其余按模板使用)：

**按舰船大小给出不同数值**

`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP` 是四档舰船大小：护卫舰/驱逐舰/巡洋舰/主力舰。按大小给不同数值的写法：四个数值分别对应四档舰船，按需修改。数值修改用 [C2-S1] 的固定句式，上一节用的是 `modifyPercent`(百分比)，这里换成 `modifyFlat`(固定值)：`modifyFlat(id, 15f)` = 最大速度直接 +15 单位/秒。

**描述联动 (`getDescriptionParam`)**

`desc` 列中的 `%s` 占位符由 `getDescriptionParam(index, hullSize)` 按 `index` 0→N 顺序填充。`%s` 多于返回值时，多余占位符显示 "null"；少于时多余参数被忽略。

**安装限制 (`isApplicableToShip` \+ `getUnapplicableReason`)**

互斥由这两个方法实现：前者返回能否安装 (`false` = 不能)，后者返回拒绝原因 (装配界面上显示的文字)。用 `ship.getVariant().hasHullMod(id)` 检查目标船插是否已装，即可实现任意两个船插的互斥。

::: info 太长不看版

- `applyEffectsBeforeShipCreation`：修改数值 (生涯面板和战斗都生效)；
- `getDescriptionParam`：填充描述里的 `%s`；
- `isApplicableToShip` \+ `getUnapplicableReason`：限制能否安装。

数值修改只写在第一个方法里 —— 写错位置 (比如写进 `applyEffectsAfterShipCreation`) 的效果是生涯面板看不到加成。

:::

::: tip 建议

沿用第一章的惯例：能复制粘贴就绝不硬写。复制原版 `unstable_injector` 行与效果类骨架，再修改参数，比从零输入更不易出错。

:::

三者的完整组合见本节完整最小例。

## 完整最小例

注册行写入 `data/hullmods/hull_mods.csv`；效果类写入 `src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`(文件路径即包路径，见 [C2-S1])。

```text {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"提高舰船最大速度 %s/%s/%s/%s，随舰船大小变化。不兼容 不稳定推进器。","提高舰船最大速度。",,graphics/hullmods/GearIn_stable_injector.png
```

::: details 完整成品：GearInStableInjectorHullMod

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * 稳定化推进器：按舰船大小提供小幅最大速度加成，与不稳定推进器 (unstable_injector) 互斥。
 * 注册在 hull_mods.csv 的 script 列；效果类每个应用会话只实例化一次。
 */
public class GearInStableInjectorHullMod extends BaseHullMod {

    /** 与 hull_mods.csv 的 id 列保持一致；其他船插检查本船插时使用该常量。 */
    public static final String ID = "GearIn_stable_injector";

    /** 原版不稳定推进器的 id，用于互斥检查。 */
    private static final String UNSTABLE_INJECTOR = "unstable_injector";

    private static final Map<HullSize, Float> SPEED_BONUS = new HashMap<>();
    static {
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
        if (index == 0) return "" + SPEED_BONUS.get(HullSize.FRIGATE).intValue();
        if (index == 1) return "" + SPEED_BONUS.get(HullSize.DESTROYER).intValue();
        if (index == 2) return "" + SPEED_BONUS.get(HullSize.CRUISER).intValue();
        if (index == 3) return "" + SPEED_BONUS.get(HullSize.CAPITAL_SHIP).intValue();
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

## 故障注入：触发类名不一致

1. 把 CSV `script` 列改为 `data.scripts.gearin.hullmods.NoSuchClass`，保存、重启；
2. 打开装配界面查看本船插的 tooltip，或让游戏生成这艘船 —— 加载期报 `Error loading [data.scripts.gearin.hullmods.NoSuchClass]`，无法进入装配界面；
3. 改回正确类名，重启，确认恢复。

这个报错就是类名三处一致被破坏的可观察结果 (规则见 [C2-S1])。之后在任何船插、武器、战术系统上见到 `Error loading`，首先核对类名三处。

## 验证方案

前置数据：本节的 `hull_mods.csv` 注册行与 `GearInStableInjectorHullMod` 效果类。

1. 重启游戏并进入生涯装配界面，找到 "稳定化推进器"。
2. 把它安装到护卫舰上，观察 tooltip、最大速度和互斥提示。
3. 进入模拟战比较安装前后的最大速度。
4. 退出战斗，在 `starsector.log` 中观察船插加载记录。

预期结果：

- 装配界面出现 "稳定化推进器"，`unlocked=TRUE` 时无需蓝图。
- 护卫舰 tooltip 显示 `提高舰船最大速度 15/10/5/5，随舰船大小变化。不兼容 不稳定推进器。`，四个数字高亮。
- 舰船最大速度增加 15(护卫舰档)，生涯舰队面板与战斗数值一致。
- "稳定化推进器" 与 "不稳定推进器" 互斥，安装被拒并显示原因。
- `starsector.log` 出现 `Loading hullmod [稳定化推进器]`，且没有相关 `ERROR`。

## 故障分析

- **速度加成没有生效**：数值修改写进 `applyEffectsAfterShipCreation` 时，生涯侧不会执行它。

    - **解决方案**：把 `modifyFlat(...)` 移入 `applyEffectsBeforeShipCreation`，再重新部署舰船。

- **两个船插数值互相覆盖**：多个船插共用 `modifyFlat("bonus", ...)` 之类的硬编码 source id。

    - **解决方案**：改用回调传入的船插 `id`。

- **互斥限制没有生效**：数据行本身不会建立互斥判断。

    - **解决方案**：补齐 `isApplicableToShip` 与 `getUnapplicableReason`。

- **描述出现空位**：`desc` 中的 `%s` 数量超过 `getDescriptionParam` 返回的参数。

    - **解决方案**：为每个占位符提供对应 `index` 的非 null 字符串并重启。

## 总结

船插 = `hull_mods.csv` 一行数据 (`id` 唯一、`script` 指向效果类、四档 `cost` 必填)+ 一个效果类 (数值、描述、限制三类职责分属不同方法)。

"数据行 + 效果类" 是本教程所有可注册游戏对象的通用结构：战术系统、武器、舰船都会沿用 "注册数据行 + 类回调" 的流程，只是数据表与接口不同。类名三处一致和 `Problem loading class` 的排查经验，在后面每一章都会用到。

## 建议参考项

- `data/hullmods/hull_mods.csv`：船插注册表 (原版 `unstable_injector` 行可对照)。
- `com.fs.starfarer.api.combat.BaseHullMod`：船插效果类模板基类 (含 `isBuiltIn` 等现成逻辑)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：数值入口 (`getMaxSpeed()` 等)，配合 `modifyFlat`/`modifyPercent` 使用。
- `com.fs.starfarer.api.combat.ShipAPI.HullSize`：四档舰船大小 (`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP`)。
- `com.fs.starfarer.api.impl.hullmods.ShieldShunt`：原版互斥船插写法参考。
- `graphics/hullmods/generic_mod.png`：`sprite` 缺省图标。
