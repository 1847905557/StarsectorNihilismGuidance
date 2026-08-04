# 制作第一个船插效果类

本节建立第一个 Java 效果类：一个最小船插。通过修改一个参数，即可在游戏内观察到装配界面的数值变化；"修改——重启——观察" 这一流程是后续所有代码操作的基础。概念只展开到本节操作所需的程度，后半部分给出常见错误的排查方法。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~20 分钟 (含首次重启游戏)。*

## 学习目标

完成后，您将能够：

- 说出一个 Java 文件里哪些部分按模板使用、哪些参数可以修改；
- 完成第一次修改：改一个参数，重启游戏后在装配界面观察到速度加成变化；
- 说出注册与回调各自的含义 (各一句)；
- 独立解决 "修改后无变化" 与 "加载报错" 两类问题。

## 前置知识

- MOD 目录与 `mod_info.json`(C01-S01)：本节的文件都放进 `gear_in` MOD；
- CSV 数据行的读法 (C01-S02)：逗号分隔、行首 `#` 忽略；
- 能进入模拟战并打开装配界面 (C01-S02 的舰船)。

## Java 文件的结构

本节只有一个代码文件，完整内容如下：

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * 教程第一个最小船插：给舰船一个固定百分比的最大速度加成。
 * 整份文件只有两处是参数 ——SPEED_PERCENT 的数字与日志文字；其余全部按模板使用。
 */
public class GearInSpeedBooster extends BaseHullMod {

    /** 速度加成百分比（参数：改这个数字，其余不动）。 */
    private static final float SPEED_PERCENT = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
        Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
    }
}
```

这个文件只有四类内容：

- **`package` 行**：类的存放目录。`data.scripts.gearin.hullmods` 对应 `src/data/scripts/gearin/hullmods/`，文件必须放在该目录下。按模板使用。
- **`import` 行**：向游戏借用的工具 (日志、船插基类、数值修改器)。按模板使用，不认识的名称不影响本节操作。
- **`public class GearInSpeedBooster extends BaseHullMod`**：类的名字。两条规则**：类名必须与文件名一致**(文件 `GearInSpeedBooster.java` 对应类名 `GearInSpeedBooster`)；`extends BaseHullMod` 表示采用船插的标准模板。按模板使用。
- **`SPEED_PERCENT = 10f`**：本节唯一的参数 —— 速度加成百分比。改为 `50f` 即加成 50%。`f` 是小数标记，`10f` 与 `10.0f` 等价。
- **方法 `applyEffectsBeforeShipCreation` 及其两行**：按模板使用，方法签名的参数不需要全部理解。方法体内两行各有用途：`modifyPercent(id, SPEED_PERCENT)` 是 "给最大速度加百分之几" 的固定句式；`Global.getLogger(...).info("...")` 向游戏日志写入一行文字 —— 引号内的文字可修改，改成什么，日志里就出现什么。

::: info 最小形态

一个能生效的船插效果类，最小形态是 "一个类 + 一个方法 + 一个参数"。继承、对象、方法签名的完整含义本节不展开 —— 它们不影响本节操作。

:::

## 注册船插数据行

游戏不会自动加载未注册的类。船插的登记位置是 `data/hullmods/hull_mods.csv`—— 在 MOD 根目录建立该文件，写入以下两行 (第一行是表头；原版存在同名文件，游戏合并读取)：

```text
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
加速器,GearIn_speed_booster,0,,,engines,引擎,1000,TRUE,,,3,6,9,12,data.scripts.gearin.hullmods.GearInSpeedBooster,"提高舰船最大速度 10%。","提高最大速度。",,
```

本节只关注这一行中的四列，其余按模板使用：

- **`id` 列**(第 2 列)：船插的全局唯一标识，`GearIn_` 前缀是 ID 纪律 (C01-S01)。本行是 `GearIn_speed_booster`。
- **`cost_*` 四列**：装配点消耗 —— 护卫舰 3、驱逐 6、巡洋 9、主力 12。修改这四个数字可调整成本。
- **`script` 列**：类的完整名。这就是注册：游戏在数据中读到这个类名，需要时加载它。类名必须与文件中的 `class GearInSpeedBooster` 一字不差。
- **`desc` 列**：装配界面显示的描述文字。引号内的文字可修改。

## 修改参数与观察

1. 把 `SPEED_PERCENT` 从 `10f` 改为 `50f`，保存文件。
2. 重启游戏。开发期的编译方式是：`data/scripts/` 目录下的源码由游戏**启动时自动编译**，改完代码重启即生效，不需要任何构建工具。正式工程化 (编译进 JAR) 在 C06-S01，类名规则不变。
3. 进入模拟战，把 C01-S02 建立的舰船开进装配界面，安装 "加速器"。
4. 观察两处结果：装配界面的 tooltip 显示速度加成 **50%**；退出游戏后打开 `Starsector/starsector-core/starsector.log`，搜索 `GearIn_speed_booster`，能看到代码里写入的那行日志 —— 这行日志证明游戏确实调用了您的代码。

::: tip 建议

验证前清空 `starsector.log` 再重启游戏，本次运行产生的新行会更容易定位。

:::

改回 `10f` 再重启，加成回到 10%——"修改——重启——观察" 的流程至此建立，后续章节均依赖该流程。

## 参数的写法

- **小数带 `f`**：`10f`、`0.5f`、`3.14f`。游戏数值几乎都是浮点数，写数字时带 `f` 结尾是固定习惯。
- **`modifyPercent` 加的是百分比**：`modifyPercent(id, 10f)` = 最大速度 +10%。
- **`modifyFlat` 加的是固定值**：`modifyFlat(id, 50f)` = 直接 +50 单位/秒。下一节使用这种写法。

::: warning 修改后无变化时的排查顺序

① 文件保存在 `gear_in` 的 `src/data/scripts/...` 下；② 游戏确实重启过；③ 装配界面确实安装了船插。三项确认后仍无变化，见下方故障分析。

:::

## 错误对照表 (第一版)

| 症状 | 原因 | 修法 |
|---|---|---|
| 装配界面里没有 "加速器" | CSV 行未生效 | 文件路径必须是 MOD 根的 `data/hullmods/hull_mods.csv`；保留表头行；文件以 UTF-8 无 BOM 保存 |
| 启动时日志报 `Problem loading class [data.scripts.gearin.hullmods.GearInSpeedBooster]` | 类名三处不一致 | 核对文件名、`class` 后的名字、CSV `script` 列 —— 必须一字不差 (下一节系统化核对) |
| 日志报 `Error loading [...]` 且带行号 | Java 代码书写错误 (漏分号、括号不配对) | 按报错行号对照本节代码逐字检查 |
| 修改参数后无变化 | 未重启游戏，或改的不是游戏加载的那份文件 | 确认文件路径；重启游戏 |
| 船插已装但数值无变化 | 观察对象错误 (tooltip 已是 50% 但船本身基础速度低) | 用装配界面的速度数值对比安装前后 |

## 预期结果

- 装配界面出现 "加速器"，tooltip 显示 "提高舰船最大速度 50%"(修改参数后)；
- `starsector.log` 出现一行 `GearIn_speed_booster applied`；
- 模拟战里舰船速度明显高于未安装时。

## 实例驱动故障分析

1. **类能编译但没有效果**。触发：`hull_mods.csv` 里没有这一行，或 `script` 列写错。可观察：游戏内无任何反应，日志也没有本船插相关行。修正：确认 CSV 路径与 `script` 列类名。
2. **`Problem loading class`**。触发：`script` 列类名、`package` 行、`class` 行三者不一致。可观察：启动或第一次使用船插时日志报错。修正：三处逐一对照 (下一节给出系统化核对法)。
3. **修改参数后加成不变**。触发：改的是别的文件，或游戏未重启。可观察：tooltip 数值依旧是旧值。修正：确认编辑的是 `gear_in` 的 `GearInSpeedBooster.java`；重启游戏。最常见的原因不是代码写错，而是游戏没有重启。
4. **日志文字没出现**。触发：日志级别过滤或搜索词不对。可观察：游戏内行为正常但搜不到。修正：搜索 `GearIn_speed_booster` 而非完整句子；验证前清空 `starsector.log` 最易定位。

## 总结

- 一个 Java 文件 = 按模板使用的部分 (包名、import、类声明、方法签名)+ 可修改的参数 (数字、引号内的文字)；
- 注册 = 在数据文件的 `script` 列写类名；回调 = 游戏在特定时机调用您的方法；
- 修改、重启、观察 —— 朴素，但每一节都靠它。

下一节把参数扩展到完整船插：装配点、描述占位符与安装限制。

## 建议参考项

- `data/hullmods/hull_mods.csv`：船插登记文件 (`script` 列 = 类名，`id` 列 = 唯一标识)。
- `src/data/scripts/gearin/hullmods/GearInSpeedBooster.java`：本节最小船插效果类。
- `data.scripts.gearin.hullmods.GearInSpeedBooster`：类的完整名 (注册用)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证回调被调用的位置)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：数值修改入口 (`getMaxSpeed()` 等；本节的 `modifyPercent` 作用于其返回对象)。
