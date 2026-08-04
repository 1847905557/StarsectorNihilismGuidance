# 制作完整船插

本节将复用 [C2-S2] 介绍的船插目标，制作一个相对完整的船插。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出船插的组成：`hull_mods.csv` 的一行数据 + 一个 Java 效果类；
- 用类名三处一致规则核对任何船插：文件名、`class` 后的名字、CSV `script` 列；
- 读懂效果类中四类内容：按舰船大小给出的数值参数、描述联动 (`%s`)、安装限制 (互斥)、固定句式 (`modifyFlat`/`modifyPercent`)；
- 独立排查 "装配界面找不到船插" 与 "`Problem loading class`" 两类故障。

## 前置知识

- MOD 目录与 `GearIn_` 前缀纪律 (C01-S01)；
- CSV 数据行读法 (C01-S02)；
- 模板与参数、注册与回调的含义、重启即生效 (C02-S01)。

本节示例所有权：`GearInStableInjectorHullMod`(稳定化推进器 —— 原版 "不稳定推进器" 的调整版：只保留小幅速度加成，取消惩罚，并与原插件互斥)。

## 类名三处一致

游戏靠**完整类名**找到效果类。一个类名出现在三个位置，必须一字不差：

| 位置 | 本例的值 |
|---|---|
| 文件路径：`src/data/scripts/...` 下的目录 + 文件名 | `src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java` |
| 文件内：`public class` 后的名字 | `GearInStableInjectorHullMod` |
| CSV：`script` 列 | `data.scripts.gearin.hullmods.GearInStableInjectorHullMod` |

规则：前两处拼起来必须等于第三处 (`data.scripts.gearin.hullmods` 来自 `package` 行，对应 `data/scripts/gearin/hullmods/` 目录)。任何一处不一致，游戏都找不到类，报 `Problem loading class [...]`。本节末尾的故障注入会触发一次。

## 完整最小例：稳定化推进器

### 第一步：注册数据行

在 MOD 的 `data/hullmods/hull_mods.csv` 写入 (文件不存在就新建，只包含本 MOD 的行；表头行必须保留)：

```text
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"提高舰船最大速度 %s/%s/%s/%s 单位/秒，随舰船大小变化。不兼容 不稳定推进器。","提高舰船最大速度。",,graphics/hullmods/GearIn_stable_injector.png
```

20 列中，本节只关注 8 列，其余按模板使用：

- **`id`**：船插的唯一标识，`GearIn_` 前缀。装配点、脚本、其他船插引用它都用这个值。
- **`cost_frigate` / `cost_dest` / `cost_cruiser` / `cost_capital`**：四档舰船的装配点消耗 (本例 5/10/15/25)。四列都必须填数字 —— 即使船插只会被内置也填 `0`，留空会让游戏加载中断。
- **`script`**：效果类完整类名 (类名三处一致的第一处核对点)。
- **`unlocked`**：`TRUE` = 玩家默认解锁，无需蓝图即可装配。建议调试期保持 `TRUE`，验证完成后再调整。
- **`desc`**：装配界面显示的描述。引号里的 **`%s` 是占位符**，由效果类的 `getDescriptionParam` 依次填数字 (本例 4 个 `%s` = 四档舰船的速度加成)，对应关系见效果类一节。
- **`sprite`**：图标路径。本节不准备贴图 —— 留空也能用 (游戏退回默认图标 `graphics/hullmods/generic_mod.png`)，想换图标时把 32×32 的 PNG 放到该路径即可。

::: warning 四档 cost 缺一不可

`cost_*` 四列之一留空 → 游戏读取该行抛 JSONException，加载中断。填 `0` 是合法的 (表示免费)。

:::

### 第二步：效果类

将以下类完整写入 `src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`(文件路径即包路径，见类名三处一致表)：

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * 稳定化推进器：按舰船大小提供小幅最大速度加成，与不稳定推进器（unstable_injector）互斥。
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

比上一节的 "加速器" 多三样东西，逐个看 (其余按模板使用)：

**① 按舰船大小给出不同数值 (`SPEED_BONUS` 段)**

`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP` 是四档舰船大小：护卫舰/驱逐舰/巡洋舰/主力舰。这段的写法是模板，**参数是四个数字**—— 想改成 "护卫舰 +30、主力舰 +10"，改数字即可。上一节用的 `modifyPercent`(百分比)，这里换成了 `modifyFlat`(固定值)：`modifyFlat(id, 15f)` = 最大速度直接 +15 单位/秒。

**② 描述联动 (`getDescriptionParam`)**

CSV 的 `desc` 里有 4 个 `%s`，这个方法按顺序 (`index` 0→3) 把四档加成填进去。修改 `SPEED_BONUS` 的数字后，描述里的高亮数值自动跟着变，不需要改 CSV 里的描述文字。`%s` 的数量必须与方法返回值的数量一致，多了少了都会让 tooltip 出现空位。

**③ 安装限制 (`isApplicableToShip` + `getUnapplicableReason`)**

"与不稳定推进器互斥" 由这两个方法实现：前者返回能否安装 (`false` = 不能)，后者返回拒绝原因 (装配界面上显示的文字)。`hasHullMod("unstable_injector")` 检查舰船是否已装原版不稳定推进器 (原版 id，可在原版 `hull_mods.csv` 里查到)。这是 原版互斥船插的标准写法。

::: info 三个方法各管一件事

- `applyEffectsBeforeShipCreation`：修改数值 (生涯面板和战斗都生效)；
- `getDescriptionParam`：填充描述里的 `%s`；
- `isApplicableToShip` + `getUnapplicableReason`：限制能否安装。

数值修改只写在第一个方法里 —— 写错位置 (比如写进 `applyEffectsAfterShipCreation`) 的效果是生涯面板看不到加成，见故障分析第 4 条。

:::

### 第三步：装船验证

1. 重启游戏，进入生涯，打开装配界面 ——"稳定化推进器" 应出现在船插列表；
2. 装到护卫舰上：tooltip 显示 "提高舰船最大速度 15/10/5/5 单位/秒..."，四个数字高亮；
3. 先装 "不稳定推进器" 再尝试装本船插：被拒绝，原因显示 "不兼容 不稳定推进器"；
4. 进模拟战对比安装前后最大速度 (护卫舰应 +15)；
5. 退出游戏，检查 `starsector.log`：应有 `Loading hullmod [稳定化推进器]` 一行且无 `ERROR`。

::: tip 建议

沿用第一章的惯例：能复制粘贴就绝不硬写。复制原版 `unstable_injector` 行与效果类骨架，再修改参数，比从零输入更不易出错。

:::

## 故障注入：触发类名不一致

1. 把 CSV `script` 列改为 `data.scripts.gearin.hullmods.NoSuchClass`，保存、重启；
2. 打开装配界面查看本船插的 tooltip，或让游戏生成这艘船 —— 日志出现 `Problem loading class [data.scripts.gearin.hullmods.NoSuchClass]`；
3. 改回正确类名，重启，确认恢复。

这个报错就是类名三处一致被破坏的可观察结果。类名改错时游戏会明确报 `Problem loading class`—— 相比静默失败，这已经算客气。之后在任何船插、武器、战术系统上见到这个报错，首先核对类名三处。

## 错误对照表

| 症状 | 原因 | 修法 |
|---|---|---|
| 装配界面找不到船插 | CSV 行未生效 / `id` 与其他来源重复 / `unlocked` 不是 `TRUE` | 确认文件在 MOD 根 `data/hullmods/hull_mods.csv`；`id` 全局唯一；调试期 `unlocked=TRUE` |
| 启动中断，日志报 `JSONException` | `cost_*` 四列之一留空，或 `name`/`id`/`script` 缺失 | 四档 cost 都填数字；必填列不空 |
| `Problem loading class [...]` | 类名三处不一致，或类不存在 | 按类名三处一致表逐一核对 |
| 装上了但数值无变化 | 数值写进了 `applyEffectsAfterShipCreation` | 数值修改一律放 `applyEffectsBeforeShipCreation` |
| 两个船插加成互相覆盖 | 多个船插用同一个硬编码 source id | 一律使用回调传入的 `id`(即 CSV 的 `id`) |
| 互斥失效，两个都能装 | 只写了数据行，没写 `isApplicableToShip` | 补 `isApplicableToShip` 返回 `false` + `getUnapplicableReason` 给原因 |
| tooltip 出现空位或未替换的 `%s` | `desc` 的 `%s` 数量与 `getDescriptionParam` 返回值不一致 | 每个 `%s` 都有对应 `index` 返回非 null |

## 验证方案

前置数据：本节的 `hull_mods.csv` 注册行与 `GearInStableInjectorHullMod` 效果类。

1. 重启游戏并进入生涯装配界面，找到 "稳定化推进器"。
2. 把它安装到护卫舰上，观察 tooltip、最大速度和互斥提示。
3. 进入模拟战比较安装前后的最大速度。
4. 退出战斗，在 `starsector.log` 中观察船插加载记录。

预期结果：

- 装配界面出现 "稳定化推进器"，`unlocked=TRUE` 时无需蓝图。
- 护卫舰 tooltip 显示 `提高舰船最大速度 15/10/5/5 单位/秒，随舰船大小变化。不兼容 不稳定推进器。`，四个数字高亮。
- 舰船最大速度增加 15(护卫舰档)，生涯舰队面板与战斗数值一致。
- "稳定化推进器" 与 "不稳定推进器" 互斥，安装被拒并显示原因。
- `starsector.log` 出现 `Loading hullmod [稳定化推进器]`，且没有相关 `ERROR`。

## 故障分析

1. **装配界面找不到船插**。触发：CSV 行未写入、`id` 与其他来源重复 (跨文件重复是静默跳过，日志无报错)、`unlocked` 缺失、`script` 类名写错。修正：确认文件路径；`GearIn_` 前缀唯一；调试期 `unlocked=TRUE`；核对类名。
2. **加载中断，`JSONException` 或 `Duplicate key`**。触发：`cost_*` 之一留空、必填列缺失、同一文件内两行 `id` 相同。修正：四档 cost 填数字；同文件内不重复 `id`。
3. **`Problem loading class [...]`**。触发：类名三处不一致或类不存在。修正：按类名三处一致表核对；源码放在 `data/scripts/gearin/hullmods/` 包路径下。
4. **效果不生效 (速度没变)**。触发：把 `modifyFlat(...)` 写进了 `applyEffectsAfterShipCreation`—— 该方法不被生涯调用，不应承担数值修改。修正：数值修改一律放 `applyEffectsBeforeShipCreation`。
5. **两个船插数值互相覆盖**。触发：多个船插共用硬编码 source id(如都写 `modifyFlat("bonus", ...)`)。修正：一律用回调传入的船插 `id` 作 source id。
6. **互斥失效**。触发：只写了数据行，没写 `isApplicableToShip`。修正：补 `isApplicableToShip` + `getUnapplicableReason`。
7. **描述出现空位**。触发：`%s` 数量与 `getDescriptionParam` 返回值数量不一致。修正：`desc` 的每个 `%s` 都有对应 `index` 返回非 null 字符串。

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
