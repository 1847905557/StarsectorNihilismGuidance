# 以船插为例初步熟悉代码

本章开始涉及代码。编者将以一个提高舰船速度的船插为例，介绍 Java 文件中几个会用到的位置。先创建效果文件，再登记船插，最后修改参数并进入游戏观察结果。和此前的数据制作一样，仍然从复制和修改现成内容开始。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 找出示例 Java 文件中照抄的结构与可以修改的参数；
- 修改一个速度参数，重启后在装配界面和模拟战中确认效果；
- 解释数据注册与游戏回调在本例中的作用；
- 根据文件、数据与日志定位加载失败或修改未生效的问题。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 与 `gear_in` MOD；
- [C1-S2] 的舰船获取、装配界面与模拟战操作；
- [C1-S7] 的 CSV 列边界、行首注释与文本编码。

## 准备一个加速船插

本例将制作一个名为 "加速器" 的船插，效果是**提高舰船最大速度 10%**。船插 ID 为 `GearIn_speed_booster`，效果类名为 `GearInSpeedBooster`。

在 MOD 根目录下创建 `data/scripts/gearin/hullmods/GearInSpeedBooster.java`。完整文件见本节的 "完整最小例"，下面先解释修改时会涉及的几处内容。

### 包名、目录与类名

文件开头的包声明如下：

```java
package data.scripts.gearin.hullmods;
```

再看 import 行之后的类声明，局部片段如下：

```java
public class GearInSpeedBooster extends BaseHullMod {
    // 船插参数和效果方法放在这里。
}
```

`package` 说明这个类属于哪个包；在当前的源码目录里，点号对应目录层级。因此它放在 `data/scripts/gearin/hullmods/` 中，文件名则与 `class` 后面的 `GearInSpeedBooster` 一致。

`extends BaseHullMod` 表示继承游戏提供的船插基类，它提供了船插效果的标准结构。`import` 行引入下面用到的游戏类，本例按完整文件复制。

编者建议在当前阶段保持最小改动：包名、类名和方法声明按示例填写，效果参数按需要调整。这样加载失败时也容易对照原文件定位。

### 速度参数

在类里面找到这一行：

```java
private static final float SPEED_PERCENT = 10f;
```

`SPEED_PERCENT` 保存速度加成百分比。数字后的 `f` 表示 Java 的 `float` 类型；`10f` 与 `10.0f` 表示同一个值，`0.5f`、`3.14f` 也按这种方式书写。

把这里的 `10f` 改成 `50f`，速度加成就变成 50%。本次修改只替换这个数字，保留行末分号与声明的其他部分。

### 修改速度与记录回调

效果方法的局部片段如下：

```java
@Override
public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
    Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
}
```

游戏在计算这艘舰船的船插效果时调用这个方法，并把数值对象交给它。我们通过 `stats.getMaxSpeed()` 取得最大速度，再用 `modifyPercent(id, SPEED_PERCENT)` 写入加成。

**游戏在对应时机调用效果方法，这个过程称为回调。** 本例的方法名和参数列表采用船插接口规定的写法，具体效果写在方法体的大括号内。

第二行把 `GearIn_speed_booster applied` 写入 `starsector.log`，用于确认回调执行。引号内的字符串决定日志内容，本节保留这个固定搜索词。

## 注册船插数据

保存效果文件后，打开 MOD 的 `data/hullmods/hull_mods.csv`，为船插添加数据行。文件不存在时，新建文件并写入表头。

**`script` 列负责把数据行接到 Java 类。** 本节填写：

```text
data.scripts.gearin.hullmods.GearInSpeedBooster
```

这个完整类名由包名与类名组成。它必须与文件中的 `package` 和 `class` 声明一致，路径按包名对应，大小写也要一致。

**在数据中登记船插 ID 和效果类，这个过程称为注册。** 游戏读取数据后，装配界面使用其中的名称、成本与描述，效果计算则调用 `script` 指向的类。

### 确认数据行中的关键列

本节先看下面这些内容，其余值按完整示例保存：

- **`id`**：第 2 列，填写船插 ID `GearIn_speed_booster`，沿用 [C1-S1] 的数据前缀规则。
- **`cost_frigate`、`cost_dest`、`cost_cruiser`、`cost_capital`**：四种船体等级的装配点消耗，分别填写 `3`、`6`、`9`、`12`。
- **`script`**：填写效果类的完整名字 `data.scripts.gearin.hullmods.GearInSpeedBooster`。
- **`desc`**：装配界面显示的文字。本节写 "提高舰船最大速度 10%。"。

::: warning 四档装配点消耗都要填写

四个 `cost_*` 字段都需要数字。填 `0` 表示免费；留空会在加载时产生解析错误。修改数据时同时留意 [C1-S7] 的列边界，空列的逗号仍有作用。

:::

## 完整最小例

效果类写入 `data/scripts/gearin/hullmods/GearInSpeedBooster.java`，数据行写入 `data/hullmods/hull_mods.csv`。CSV 已有表头时，只追加这条船插记录。

::: details 完整成品：GearInSpeedBooster

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 为舰船提供固定百分比的最大速度加成。 */
public class GearInSpeedBooster extends BaseHullMod {

    /** 最大速度加成百分比。 */
    private static final float SPEED_PERCENT = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
        Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
    }
}
```

:::

```csv {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
加速器,GearIn_speed_booster,0,,,engines,引擎,1000,TRUE,,,3,6,9,12,data.scripts.gearin.hullmods.GearInSpeedBooster,"提高舰船最大速度 10%。","提高最大速度。",,
```

## 修改参数并比较结果

保存这两个文件，重启游戏，在装配界面给 [C1-S2] 的工匠安装 "加速器"，先观察 `10f` 的效果。然后把 Java 文件中的 `SPEED_PERCENT` 改为 `50f`，再次保存、重启和观察。

**实际加成已经变成 50%，描述仍然写着 10%。** `desc` 是 CSV 中的固定文字，效果类使用的是 Java 参数，两处分别修改。参数调整后也要同步描述；[C2-S2] 会介绍用代码填充描述参数的做法。

### 选择百分比或固定增量

本例使用 `modifyPercent(id, 10f)`，表示按基础最大速度增加 10%。如果希望直接增加固定速度，可以使用下面这个局部片段：

```java
stats.getMaxSpeed().modifyFlat(id, 50f);
```

它表示增加 50 单位/秒。百分比加成随基础速度变化，固定增量则直接加上指定数值。**同一种速度加成，对不同船体的价值也不同。** 例如固定加 50 对慢速主力舰的影响往往比对快速护卫舰更明显，编者建议您连同船体定位一起考虑。

## 验证方案

1. 保存本节的 Java 文件和 CSV，重启游戏，进入已有生涯的装配界面。
2. 给工匠安装 "加速器"，比较安装前后的最大速度，再进入模拟战观察机动表现。
3. 查看 `Starsector/starsector-core/starsector.log`，搜索 `GearIn_speed_booster applied`。
4. 将 `SPEED_PERCENT` 改为 `50f`，保存并重启，给同一艘舰船安装相同船插，再比较数值与机动表现。
5. 悬停船插，核对 CSV 描述与当前实际加成。

预期结果：

- 装配界面出现 "加速器"，工匠的装配点消耗为驱逐舰档的 6。
- `10f` 时，最大速度获得基础值 10% 的加成；`50f` 时，加成变为 50%，模拟战中可观察到变化。
- CSV 描述仍显示 "提高舰船最大速度 10%。"。
- 每次测试的效果计算阶段都能在日志中找到 `GearIn_speed_booster applied`，出现次数以实际日志为准。

## 故障分析

- **装配界面找不到加速器**：`hull_mods.csv` 缺少本节数据行，或行首仍带 `#`，游戏没有读到注册信息。
    - **解决方案**：确认文件位于 MOD 根目录下的 `data/hullmods/hull_mods.csv`，保留表头与唯一的 `GearIn_speed_booster` 行，保存后重启。

- **加载时报 `Error loading [...]`**：`script`、`package`、文件路径或类名没有指向同一个类。
    - **解决方案**：逐段核对 `data.scripts.gearin.hullmods.GearInSpeedBooster`，确认文件名与类声明一致后重启。

- **Java 加载错误带有行号**：分号缺失或括号不配对导致源码解析失败。
    - **解决方案**：按日志行号对照完整示例，修正该位置并重启。

- **参数修改后加成不变**：文件尚未保存、编辑了另一份源码，或游戏仍在使用启动时加载的类。
    - **解决方案**：确认修改的是当前 MOD 的 `GearInSpeedBooster.java`，保存后重启，再为测试舰船安装船插。

- **速度加成生效但搜索不到日志文字**：搜索词与代码中的字符串不同，或日志级别过滤了该输出。
    - **解决方案**：核对代码中的日志字符串与日志配置，在本次启动记录中搜索 `GearIn_speed_booster`。

- **加载时报 `JSONException`**：必填列缺失、四档 `cost_*` 留空，或 CSV 字段错位。
    - **解决方案**：按首行列名恢复列边界，补齐四档数字，以 UTF-8 无 BOM 保存并重启。

- **加载时报 `Duplicate key`**：同一 CSV 文件中重复登记了相同 ID。
    - **解决方案**：为每行保留唯一 ID，保存后重启。

## 总结

船插由数据行与效果类共同构成。CSV 提供身份、成本和描述，Java 方法写入实际加成；注册把两者联系起来，游戏则在数值计算时调用效果方法。

本节采用固定的百分比加成。下一节会加入按船体等级分档的参数、与参数联动的描述和安装限制，让它成为一个适合实际使用的船插。

## 建议参考项

- `data/hullmods/hull_mods.csv`：船插登记表，`id`、四档 `cost_*`、`script` 与 `desc` 的写入位置。
- `com.fs.starfarer.api.combat.BaseHullMod`：本节效果类使用的船插基类。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：`getMaxSpeed()` 提供最大速度修改入口。
- `Starsector/starsector-core/starsector.log`：加载错误与效果日志的观察位置。
