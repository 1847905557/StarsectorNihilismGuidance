# 制作完整船插

本节将复用 [C2-S1] 介绍的船插目标，制作一个相对完整的船插。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出船插的组成：`hull_mods.csv` 的一行数据 + 一个 Java 效果类；
- 用类名三处一致规则核对任何船插：文件名、`class` 后的名字、CSV `script` 列；
- 读懂效果类中四类内容：按船体等级给出的数值参数、描述联动 (`%s`)、安装限制 (互斥)、固定句式 (`modifyFlat`/`modifyPercent`)；
- 独立排查本节新增的加载中断 (`JSONException`/`Duplicate key`) 与效果类故障。

## 前置知识

- [C1-S1] 的 MOD 目录与 `GearIn_` 前缀规则；
- [C1-S2] 的 CSV 数据行读取方法；
- [C2-S1] 的 Java 模板、参数、注册、回调与重启生效流程。

## 补充更多船插数据

其余列沿用 [C2-S1] 的规则，本节再介绍几列即将用到的：

- **`unlocked`**：`TRUE` = 玩家默认解锁。
- **`desc`**：装配界面显示的描述。`desc` 列支持 `%s` 占位符，由效果类的 `getDescriptionParam` 按 `index` 顺序填充。
- **`sprite`**：图标路径。本节假设您已经准备好了一张 32×32 的 PNG 贴图 —— 就像前几节那样。

## 实现船插效果

本节将进一步复现原版插件使用过的三种通用机制：按船体等级分档数值、描述占位符联动、插件不兼容限制。它们在 Mod 设计中也很有用。

### 按船体等级分档数值

一般来说，插件的数值主要考虑 护卫舰/驱逐舰/巡洋舰/主力舰，但最好也要总是把 战机 纳入考量 —— 一方面是为了适配插件装在战机上的情况，另一方面也是为了某些奇奇怪怪的兼容性。

效果类的 `applyEffectsBeforeShipCreation(HullSize hullSize, ...)` 直接收到当前船体等级，因此 "按等级查表取值" 是数值类船插的标准写法：数值放进 `Map<HullSize, Float>`，方法内按传入的 `hullSize` 取对应值，再进行使用。

### 描述占位符联动

"静态文本 + 代码填充" 是游戏显示体系的通用模式：船插、武器、战术系统的 `desc` 描述列都支持 `%s` 占位符，由对应效果类的 `getDescriptionParam(int index, HullSize hullSize)` 按 `index` 0→N 顺序返回填充文本。

数据 (CSV 描述) 与显示逻辑 (代码返回值) 分离 —— 改数值表后描述自动同步，不用改文案；`%s` 多于返回值时显示 `null`，少于时多余参数被忽略。

### 安装合法性限制

`isApplicableToShip(ShipAPI ship)` 与 `getUnapplicableReason(ShipAPI ship)` 是装配系统对船插的唯一合法性入口：前者返回能否安装，后者返回拒绝原因文本。

互斥只是最常见的用法 —— 用 `ship.getVariant().hasHullMod(id)` 检查目标船插是否已装即可。

::: info 太长不看版

- `applyEffectsBeforeShipCreation`：不妨试试根据船体等级修改数值；
- `getDescriptionParam`：填充描述里的 `%s`；
- `isApplicableToShip` \+ `getUnapplicableReason`：限制能否安装。

:::

::: tip 建议

沿用第一章的惯例：能复制粘贴就绝不硬写。复制原版 `unstable_injector` 行与效果类骨架，再修改参数，比从零输入更不易出错。

:::

## 完整最小例

注册行写入 `data/hullmods/hull_mods.csv`；效果类写入 `src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`。

```text
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"提高舰船最大速度 %s/%s/%s/%s，随船体等级变化。不兼容 不稳定推进器。","提高舰船最大速度。",,graphics/hullmods/GearIn_stable_injector.png
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
 * 稳定化推进器：按船体等级提供小幅最大速度加成，与不稳定推进器 (unstable_injector) 互斥。
 * 注册在 hull_mods.csv 的 script 列；效果类每个应用会话只实例化一次。
 * 一般不会展示战机的加成数值。
 */
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

## 故障注入：描述占位符与返回值数量不一致

1. 把 CSV `desc` 列改为 `提高舰船最大速度 %s/%s/%s/%s/%s，随船体等级变化。不兼容 不稳定推进器。`(5 个 `%s`)，保存、重启；
2. 在装配界面安装本船插，查看 tooltip —— 第 5 个占位符没有对应返回值，显示 `null`；
3. 改回 4 个 `%s`，重启，确认 tooltip 恢复正常。

占位符多于返回值时，多余占位符显示 `null`；返回值多于占位符时，多余参数被忽略。数量不一致不会报错，只在描述里留下 `null` 或被丢弃的填充值 —— 核对时以装配界面 tooltip 为准。

## 验证方案

前置数据：本节的 `hull_mods.csv` 注册行与 `GearInStableInjectorHullMod` 效果类。

1. 重启游戏并进入生涯装配界面，找到 "稳定化推进器"。
2. 把它安装到护卫舰上，观察 tooltip、最大速度和互斥提示。
3. 进入模拟战比较安装前后的最大速度。
4. 退出战斗，在 `starsector.log` 中观察船插加载记录。

预期结果：

- 装配界面出现 "稳定化推进器"，`unlocked=TRUE` 时无需蓝图。
- 护卫舰 tooltip 显示 `提高舰船最大速度 15/10/5/5，随船体等级变化。不兼容 不稳定推进器。`，四个数字高亮。
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
- `com.fs.starfarer.api.combat.ShipAPI.HullSize`：舰船的船体等级。
- `com.fs.starfarer.api.impl.hullmods.ShieldShunt`：原版互斥船插写法参考。
- `graphics/hullmods/generic_mod.png`：`sprite` 缺省图标。
