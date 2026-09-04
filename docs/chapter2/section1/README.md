# 以船插为例初步熟悉代码

本节将简单解释一个代码文件 (严格来说，是 Java 效果类) 的大致形状，并说明如何通过修改一个参数，即可在游戏内观察到装配界面的数值变化。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~20 分钟 (含首次重启游戏)。*

## 学习目标

完成后，您将能够：

- 解释示例 Java 文件里哪些部分照抄即可、哪几处参数需要修改；
- 完成第一次修改：改一个参数，重启游戏后在装配界面观察到速度加成变化；
- 解释注册与回调各自的含义 (各一句)；
- 独立解决 "修改后无变化" 与 "加载报错" 两类问题。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 与 `gear_in` MOD；
- [C1-S2] 的 CSV 分隔与行首 `#` 注释规则；
- [C1-S2] 的模拟战入口与装配界面操作。

## Java 文件的结构

本节只有一个代码文件：

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * 教程第一个最小船插：给舰船一个固定百分比的最大速度加成。
 * 这是一个能直接用的最小示例：整份文件只有两处参数 —— SPEED_PERCENT 的数字与日志文字，其余照抄即可。
 */
public class GearInSpeedBooster extends BaseHullMod {

    /** 速度加成百分比 (参数：改这个数字，其余不动)。 */
    private static final float SPEED_PERCENT = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
        Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
    }
}
```

这个文件只有较少的几类内容：

- **`package` 行**：类的存放目录。`data.scripts.gearin.hullmods` 对应 MOD 根下的 `data/scripts/gearin/hullmods/`，文件必须放在该目录下。
- **`import` 行**：向游戏借用的工具。照抄示例即可，不认识的名称不影响本节操作。
- **`public class GearInSpeedBooster extends BaseHullMod`**：类的名字。类名必须与文件名一致；`extends BaseHullMod` 表示采用船插的标准写法。
- **`SPEED_PERCENT = 10f`**：本节唯一的参数，即速度加成百分比。改为 `50f` 即加成 50%。`f` 是小数标记，`10f` 与 `10.0f` 等价。
- **方法 `applyEffectsBeforeShipCreation` 及其两行**：方法签名的参数不需要全部理解。方法体内两行各有用途：`modifyPercent(id, SPEED_PERCENT)` 是 "给最大速度加百分之几" 的固定句式；`Global.getLogger(...).info("...")` 向游戏日志写入一行文字，引号内的文字可修改，改成什么，日志里就出现什么。**一般来说不会随便写日志，这里仅作演示**。

::: info 最小形态

一个船插效果，在最简陋的情况下只需要 "一个类 + 一个方法 + 一个参数" 就能起效。

继承、对象、方法签名的完整含义本节不展开讲解。

:::

## 注册船插数据行

游戏不会自动加载未注册的类。船插的登记位置是 `data/hullmods/hull_mods.csv`：在 MOD 根目录建立该文件，写入以下两行 (第一行是表头)：

```text {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
加速器,GearIn_speed_booster,0,,,engines,引擎,1000,TRUE,,,3,6,9,12,data.scripts.gearin.hullmods.GearInSpeedBooster,"提高舰船最大速度 10%。","提高最大速度。",,
```

本节只关注这一行中的四列，其余保持原样：

- **`id` 列** (第 2 列)：船插的全局唯一标识，`GearIn_` 前缀是 ID 纪律 [C1-S1]。本行是 `GearIn_speed_booster`。
- **`cost_*` 四列**：装配点消耗 —— 护卫舰 3、驱逐 6、巡洋 9、主力 12。修改这四个数字可调整成本。
- **`script` 列**：类的完整名。游戏在数据中读到这个类名，需要时按它加载，注册便由此完成。类名必须与文件中的 `class GearInSpeedBooster` 一字不差。
- **`desc` 列**：装配界面显示的描述文字。引号内的文字可修改

::: warning 四档 cost 缺一不可

`cost_*` 四列之一留空将直接导致游戏报错。填 `0` 是合法的 (表示免费)。

:::

## Java 中参数的写法

- **小数带 `f`**：`10f`、`0.5f`、`3.14f`。游戏数值几乎都是浮点数，写数字时带 `f` 结尾是固定习惯。
- **`modifyPercent` 加的是百分比**：`modifyPercent(id, 10f)` 使最大速度 +10%。
- **`modifyFlat` 加的是固定值**：`modifyFlat(id, 50f)` 直接加 50 单位/秒。

::: warning 修改后无变化时的排查顺序

文件保存在 `gear_in` 的 `data/scripts/...` 下、游戏确实重启过、装配界面确实安装了船插 —— 三项确认后仍无变化，请按故障分析的对应条目排查。

虽然，最常见的问题应该是文件没保存。

:::

## 验证方案

前置数据：[C1-S2] 建立的舰船、本节注册的 `GearIn_speed_booster` 船插与 `GearInSpeedBooster.java`。

1. 从装配界面、模拟战与 `Starsector/starsector-core/starsector.log`开始验证。
2. 在装配界面为测试舰船安装 "加速器"，核对 tooltip 与速度数值。
3. 进入模拟战观察舰船速度，退出后在日志中搜索 `GearIn_speed_booster`。
4. 把 `SPEED_PERCENT` 改为 `50f`，保存并重启游戏。
5. 在装配界面为测试舰船安装 "加速器"，核对 tooltip 与速度数值。
6. 进入模拟战观察舰船速度，退出后在日志中搜索 `GearIn_speed_booster`。

预期结果：

- 装配界面出现 "加速器"，tooltip 始终显示 CSV `desc` 列文本 "提高舰船最大速度 10%"(描述数字未修改)；
- `starsector.log` 出现两行相隔甚远的 `GearIn_speed_booster applied`；
- 第一次测试中，模拟战里舰船速度略高于未安装时。
- 第二次测试中，模拟战里舰船速度明显高于未安装时。

清理：把 `SPEED_PERCENT` 恢复为 `10f`。

## 故障注入：触发类名不一致

1. 把 CSV `script` 列改为 `data.scripts.gearin.hullmods.NoSuchClass`，保存、重启；
2. 打开装配界面查看本船插的 tooltip，或让游戏生成这艘船 —— 加载期报 `Error loading [data.scripts.gearin.hullmods.NoSuchClass]`，无法进入装配界面；
3. 改回正确类名，重启，确认恢复。

这个报错说明 CSV 注册值没有指向实际效果类。之后在任何船插、武器或战术系统上见到 `Error loading`，首先核对文件名、`class` 后的名字与注册列中的完整类名。

## 故障分析

- **类能编译但没有效果**：`hull_mods.csv` 缺少对应行时，类未被注册，游戏和日志都没有本船插的反应；`script` 列写错 (类名对应不到任何源码) 时该名字已进入启动编译队列，加载会在 `Error loading [...]` 处中断，不会静默。

    - **解决方案**：确认文件位于 MOD 根的 `data/hullmods/hull_mods.csv`，保留表头行并以 UTF-8 无 BOM 保存，核对 CSV 路径与完整类名后重启。

- **日志出现 `Error loading [...]`**：`script` 列、`package` 行和 `class` 行没有指向同一个类。

    - **解决方案**：逐一对照文件名、`class` 后的名字与 `script` 列，再重启。

- **日志报告 `Error loading [...]` 并带行号**：漏分号、括号不配对等 Java 书写错误会在日志中带行号报告。

    - **解决方案**：按报错行号对照本节代码逐字检查，修正后重启。

- **修改参数后加成不变**：当前编辑的可能是另一份文件，或游戏仍在使用重启前加载的类。

    - **解决方案**：确认文件属于 `gear_in` 的 `GearInSpeedBooster.java` 并重启。

- **日志文字没出现**：日志级别过滤或搜索词不对时，游戏内行为可以正常而搜索不到完整句子。

    - **解决方案**：验证前清空 `starsector.log`，再搜索稳定标识 `GearIn_speed_booster`。

- **加载中断并报告 `JSONException`**：必填列缺失或某个 `cost_*` 留空会破坏数据行。

    - **解决方案**：补齐四档数字后重启。

- **加载中断并报告 `Duplicate key`**：同一文件内存在重复 `id`。

    - **解决方案**：为每行保留唯一 ID 并重启。

## 总结

- 示例 Java 文件里只有少数参数需要修改 (数字、引号内的文字)，其余部分 (包名、import、类声明、方法签名) 照抄示例即可；想做出不同的效果，也从仿照示例改参数开始；
- 注册指在数据文件的 `script` 列写类名；回调指游戏在特定时机调用您的方法；

"修改 → 重启 → 观察" 这一流程是代码操作的基础；在本节范围内，重启用于确认修改已经被加载。

## 建议参考项

- `data/hullmods/hull_mods.csv`：船插登记文件 (`script` 列填类名，`id` 列填唯一标识)。
- `data/scripts/gearin/hullmods/GearInSpeedBooster.java`：本节最小船插效果类。
- `data.scripts.gearin.hullmods.GearInSpeedBooster`：类的完整名 (注册用)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证回调被调用的位置)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：数值修改入口 (`getMaxSpeed()` 等；本节的 `modifyPercent` 作用于其返回对象)。
