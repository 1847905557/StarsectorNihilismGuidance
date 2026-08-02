# 制作最小船插：数据注册与回调

本节制作第一个能在游戏内完整生效的船插：数据注册、效果类、装配限制与描述文本。示例所有权： `GearInStableInjectorHullMod`（稳定化推进器，`不稳定推进器`的调整版：只保留小幅速度加成，取消射程与甲板惩罚，并与原插件互斥）。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/hullmods/hull_mods.csv` 中注册一行船插数据，并说出每个字段在 0.98a-RC8 的使用方；
- 写出最小效果类：`applyEffectsBeforeShipCreation(...)` 修改数值、`getDescriptionParam(...)` 提供描述高亮、`isApplicableToShip(...)` 与 `getUnapplicableReason(...)` 实现安装限制；
- 指出船插从注册到生效的完整链路：CSV 行 → `HullModSpecAPI` → 效果类实例 → 生涯与战斗两处使用方；
- 区分 `hidden` 与 `hiddenEverywhere`、`unlocked` 与蓝图、普通船插与内置（built-in）船插；
- 用装配界面、战斗模拟和 `starsector.log` 验证船插行为。

## 前置知识

本页假设您已完成两个前置单元：MOD 目录与 ID 体系、类/接口/回调与注册入口——船插正是这两部分知识的组合使用方。

- 完成 MOD 目录、`mod_info.json` 与 ID 前缀（S01-P01）：了解 MOD 如何被游戏加载，以及“全局名称”的 ID 纪律。
- 完成类、接口、回调与注册入口（S02-P01）：理解“实现接口、由游戏回调”的插件形态。

船插是本教程第一个“数据行 + Java 效果类”的组合对象：数据行告诉游戏“存在这个船插”，效果类告诉游戏“它做什么”。船插的数据文件 `data/hullmods/hull_mods.csv` 由多个 MOD 与游戏本体合并，因此 id 冲突规则沿用“全局名称”原则。

## 概念：船插（hullmod）

船插是挂在舰船 variant（舰船配置）上的可拆卸或内置改装。一个船插由两部分组成：

1. `data/hullmods/hull_mods.csv` 中的一行数据：名称、id、装配点消耗、标签、描述与效果类类名；
2. 效果类：实现 `HullModEffect` 接口（建议继承 `BaseHullMod` 获得全部方法的空实现）的 Java 类，在游戏确定时机被回调。

进入链路之前，先记住两个运行时事实：

::: warning 常见坑：效果类是会话级单例

`HullModEffect` 的 API 注释明确：效果类每个应用会话只实例化一次。也就是说船插实例不是“每艘船一个”，跨船的状态（比如计时器）如果放进字段会串船并可能造成泄漏；每艘船的差异必须通过回调参数（`stats`、`ship`）读取，需要跨存档的数据走教程后续的持久化入口。

:::

- **三个核心回调的职责不能混。**
   - `applyEffectsBeforeShipCreation(HullSize, MutableShipStatsAPI, String id)` 是数值入口，生涯与战斗都会调用；
   - `applyEffectsAfterShipCreation(ShipAPI, String id)` 只在战斗创建舰船后调用，API 注释明确它不是 stats 入口（生涯不会调用它）；
   - `getDescriptionParam(int, HullSize)` 为描述文本的 `%s` 提供高亮数值。

## 文件与 API 链路

### 注册：hull_mods.csv → HullModSpecAPI → 效果类

1. 数据加载阶段，游戏读取合并后的 `data/hullmods/hull_mods.csv`（按 `id` 列取键），逐行构造 `HullModSpecAPI`（实现 `com.fs.starfarer.api.loading.HullModSpecAPI`）。
2. 每行的 `script` 列类名写入 spec 的 `effectClass`，并登记为可加载的类名。
3. 效果类实例按需创建：`HullModSpecAPI.getEffect()` 第一次被调用时加载并缓存对应类名的实例，之后复用同一个实例——这就是“会话级单例”的实现。
4. 贴图由 `sprite` 列路径进入资源队列；该列为空时使用 `graphics/hullmods/generic_mod.png`。

### 生效：效果回调的两处使用方

- 生涯侧：`FleetMemberAPI` 在舰队数据同步时遍历 `variant.getAllMods()`，对每个船插调用 `getEffect().applyEffectsBeforeShipCreation(hullSize, stats, id)`。所以生涯界面（舰队面板、战术地图）看到的数值就包含船插效果。
- 战斗侧：`ShipAPI` 在舰船实体构造时做同样的遍历；实体创建完成后由游戏调用 `applyEffectsAfterShipCreation(ship, id)`。

::: caution source id 纪律

传入的 `id` 就是该船插的 spec id（CSV 的 `id` 列），应把它作为 `MutableStat.modifyFlat/modifyPercent` 等修改的 source id——多个船插共用硬编码 id 会互相覆盖，卸载一个还会误伤其他船插（见故障分析第 5 条）。

:::

- `getAllMods()` 是 variant 对象的方法：装配的、内置的、perma 的船插全部在其中；`ShipVariantAPI.getHullMods()` 与 `hasHullMod(String)` 是公开接口方法（`com.fs.starfarer.api.combat.ShipVariantAPI`）。

### 装配界面与描述

- 装配界面的船插列表过滤掉 `hidden`、`hiddenEverywhere` 与带 `dmod` tag 的船插；然后按 `showInRefitScreenModPickerFor(ship)`、`isApplicableToShip(ship)` 判断能否安装，不满足时用 `getUnapplicableReason(ship)` 显示原因。
- 描述 tooltip 把 `desc` 中的 `%s` 依次用 `getDescriptionParam(0..9, hullSize, ship)` 的返回值填充。

### 内置（built-in）船插

- 内置船插写在舰船船体的 `data/hulls/*.ship` JSON 的 `builtInMods` 数组，例如 `data/hulls/afflictor.ship` 内置 `"phasefield"` 与 `"delicate"`。
- 构建 variant 时，游戏把 `ShipHullSpecAPI.getBuiltInMods()` 合并进 variant 的船插集合，因此内置船插走同一条 `applyEffectsBeforeShipCreation` 链，且不消耗装配点。
- `BaseHullMod.isBuiltIn(ship)` 就是检查 `ship.getHullSpec().getBuiltInMods()` 是否包含自己的 spec id（`com.fs.starfarer.api.combat.BaseHullMod`）。

## 完整最小例：稳定化推进器

### 第一步：注册数据行

在 MOD 的 `data/hullmods/hull_mods.csv` 写入（文件在 MOD 中不存在就新建，只包含本 MOD 的行）：

```csv {1}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc, standard",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"提高舰船最大速度 %s/%s/%s/%s 单位/秒，随舰船大小变化。不兼容 不稳定推进器。","提高舰船最大速度。",,graphics/hullmods/GearIn_stable_injector.png
```

要点：

- `script` 列的类名必须与下文效果类的**包名 + 类名**完全一致，否则船插无法生效（见故障分析第 3 条）。
- 四个 `cost_*` 列都必须填数字：即使船插只会被内置（不消耗装配点）也填 `0`。
- `unlocked=TRUE` 表示玩家默认解锁（无需蓝图即可在装配界面使用）；`sprite` 指向 MOD 的贴图路径。
- `desc` 中的 `%s` 按顺序由效果类的 `getDescriptionParam` 填充，本行有 4 个 `%s`，对应护卫舰/驱逐舰/巡洋舰/主力舰四档速度加成。

### 第二步：放贴图

把图标放到 `graphics/hullmods/GearIn_stable_injector.png`（32×32 的 PNG）：

![](./GearIn_stable_injector.png)

### 第三步：效果类

把源码放在 MOD 的 `data/scripts/...` 路径下，游戏启动时会即时编译；在本教程后续阶段，示例改为编译进 JAR 并声明在 `mod_info.json` 的 `jars`，类名写法不变。

::: tip 调试技巧

`data/scripts/` 下的源码由游戏启动时即时编译：改动类后重启游戏即生效，无需手动构建，这是船插开发期最快的验证回路。

:::

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

逐段说明：

- `extends BaseHullMod`：`HullModEffect` 的全部方法都有空实现，只覆盖需要的三个方法；`@Override` 保证签名与接口一致（拼错必须编译失败）。
- `SPEED_BONUS`：按 `HullSize`（`ShipAPI.HullSize`：`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP`）查表，护卫舰 15、驱逐舰 10、巡洋舰 5、主力舰 5。
- `applyEffectsBeforeShipCreation`：`stats.getMaxSpeed()` 返回 `MutableStat`，`modifyFlat(id, bonus)` 用船插 id 作为 source id 写入加值。这个方法生涯与战斗都调用，因此两个环境的数值一致。
- `getDescriptionParam`：index 0–3 对应 `desc` 中四个 `%s`；返回 `null` 的 index 不会被使用。
- `isApplicableToShip` + `getUnapplicableReason`：`ShipVariantAPI.hasHullMod("unstable_injector")` 判断舰船是否已装原版不稳定推进器（原版 id，见 `data/hullmods/hull_mods.csv` 的 `unstable_injector` 行），已装则不可安装并给出原因。
   - 这是 0.98a-RC8 原版互斥船插的写法，例如 `com.fs.starfarer.api.impl.hullmods.ShieldShunt` 对“已装临时护盾发生器”返回 `false` 与 “Incompatible with Makeshift Shield Generator”。

### 验证方案（基于本例）

- 前置数据：`mod_info.json`（S01-P01 的成果）、本页的 `hull_mods.csv` 行、`graphics/hullmods/GearIn_stable_injector.png`、效果类源码。
- 操作：
  1. 启动到生涯，打开装配界面，确认“稳定化推进器”出现在列表中；
  2. 装到一艘护卫舰上，确认 tooltip 四个数字高亮且为 15/10/5/5；
  3. 把“不稳定推进器”装到同一艘船，再尝试安装本船插，确认被拒绝并显示“不兼容不稳定推进器”；
  4. 进入战斗模拟，对比安装前后最大速度（护卫舰应 +15）；
  5. 打开 `starsector-core/starsector.log`，确认存在 `Loading hullmod [稳定化推进器] (source: [.../hull_mods.csv])` 且无 `ERROR`。
- 故障注入（可选）：把 `script` 列改成 `data.scripts.gearin.hullmods.NoSuchClass`，重启后查看装配界面与日志，应得到故障分析第 3 条的可观察结果；改回后恢复。
- 通过信号：五步操作全部符合预期，日志无错误。
- 清理：移除测试用数据行与贴图（或整个测试 MOD）即可。

## hull_mods.csv 字段速查表

::: info 为什么有“必须”与“选填”之分

以下“必须/选填”与 0.98a-RC8 的船插表读取方式一致（`getString`/`getInt` 缺失会抛 JSONException，`optXxx` 缺失取默认值）。

:::

| 字段 | 必需 | 作用与可观察结果 |
|---|---|---|
| `name` | 必须 | 显示名。缺失 → 该行读取抛 JSONException，加载中断 |
| `id` | 必须 | 注册表键，全局唯一。列为空 → 该行被整体跳过（按 `id` 列取键，空键静默跳过）；同文件内重复 → 抛 `Duplicate key [...]` 异常；与其他文件重复 → 后读入的那一行被跳过，船插不出现 |
| `tier` | 选填 | 默认 0。蓝图类物品按 tier 过滤抽取（`com.fs.starfarer.api.campaign.impl.items.ModSpecItemPlugin`），地面突袭质量也引用它 |
| `rarity` | 选填 | 默认 1.0。蓝图抽取权重，数值越大越容易被抽到 |
| `tech/manufacturer` | 选填 | 默认 `Common`。tooltip 的设计类型行；`Common` 不显示 |
| `tags` | 选填 | 逗号分隔。自动装配、D 插与市场分类用，见 tags 速查表 |
| `uiTags` | 选填 | 装配界面的文字分组，直接填可读文字，建议参考原版（如 `Engines`、`Shields`）|
| `base value` | 选填 | 默认 1000。成本价 |
| `unlocked` | 选填 | 默认 false。`TRUE` → 玩家默认解锁，无需蓝图（可用列表把非 hidden 且 alwaysUnlocked 的船插一并收录）|
| `hidden` | 选填 | 默认 false。`TRUE` → 从可用列表与装配列表隐藏（效果仍可在已安装的船上生效）|
| `hiddenEverywhere` | 选填 | 默认 false。`TRUE` → 连已安装显示都隐藏：装配界面的可见船插列表会过滤掉它，但 `getHullMods()` 仍包含它，效果照常 |
| `cost_frigate` / `cost_dest` / `cost_cruiser` / `cost_capital` | 必须 | 四档舰船的装配点消耗。缺失 → JSONException，加载中断。内置船插也填 0 |
| `script` | 必须 | 效果类完整类名（包名.类名）。缺失 → 读取该行抛 JSONException，加载中断；类不存在 → 使用该船插时抛 `Problem loading class [...]` |
| `desc` | 选填 | 默认 `No description... yet.`。`%s` 由 `getDescriptionParam(0..9)` 依次填充 |
| `short` | 选填 | 一句话描述 |
| `sModDesc` | 选填 | S-mod 效果文本（0.98a-RC8 的列）。为空时 S-mod 无额外文本 |
| `sprite` | 选填 | 图标路径。缺省 `graphics/hullmods/generic_mod.png` |

## 实用 tags 速查表

tag 写在 `tags` 列，逗号分隔。常量定义见 `com.fs.starfarer.api.impl.campaign.ids.Tags` 与 `com.fs.starfarer.api.impl.campaign.ids.HullMods`。

| tag | 作用 |
|---|---|
| `offensive` | 自动装配评估：对综合进攻能力有直接增益的船插 |
| `defensive` | 自动装配评估：对综合防御能力有直接增益的船插 |
| `shields` | 自动装配评估：对护盾能力有直接增益的船插 |
| `engines` | 自动装配评估：对移动能力有直接增益的船插 |
| `phase` | 自动装配评估：对相位能力有直接增益的船插 |
| `non_phase` | 自动装配评估：不建议安装在相位舰船上的船插 |
| `special` | 自动装配评估：倾向于不随意移除或安装 |
| `req_spaceport` | 必须遵守的规则：只能在有空间站或空间站的殖民地安装/移除。`BaseHullMod.canBeAddedOrRemovedNow(...)` 检查，不满足时装配界面拒绝操作并显示 `getCanNotBeInstalledNowReason(...)` |
| `no_build_in` | 必须遵守的规则：不可用故事点内置（装配界面内置按钮被禁用）|
| `dmod` | 标记为 D 插：参与 D 插计数与显示，装配界面的船插选择列表过滤掉它 |
| `damage` | 进入战斗后 D 插通用抽取池的**必要条件**：只设 `dmod` 不带 `damage` 的船插不会被抽取（`DModManager.addDMods` 以 `damage` 为底池过滤）|
| `damageStruct` | 结构损伤型 D 插：每艘船至多一个，已有 `damageStruct` 时不再抽取同类 |
| `destroyedDamageAlways` | 战斗中粉碎的船会直接带上全部带此 tag 的 D 插 |
| `fighterBayDamage` | 只进入带甲板（`fighter bays`）舰船的 D 插抽取池 |
| `phaseDamage` | 只进入相位舰船的 D 插抽取池 |
| `peak_time` | 只出现在峰值时间不为无限的舰船上 |
| `reqShields` | 只出现在有护盾的舰船上 |
| `civOnly` | 只出现在民用舰船上 |
| `notPhase` | 不会出现在相位舰船上 |
| `notAuto` | 不会出现在无人舰船上 |

说明：D 插的过滤规则（`peak_time`/`reqShields`/`civOnly`/`notPhase`/`notAuto`）在 `DModManager.removeUnsuitedMods(...)` 中按舰船属性逐条剔除。

`damage` 底池与 `destroyedDamageAlways` 的加池逻辑在 `addDMods(...)`。

RC8 的 `Tags` 还定义 `civ`、`notCiv`、`carrierDamageAlways`、`no_drop`、 `no_drop_salvage` 等 tag，本页不展开。不在本表上的 tag（如 `merc`、`standard`）与船插性能无关，属于市场/蓝图分类，详见后文对应单元。

## 预期结果

1. 启动游戏，进入生涯，打开装配界面，船插列表出现“稳定化推进器”（`unlocked=TRUE`，无需蓝图）。
2. 安装到护卫舰：tooltip 显示 `提高舰船最大速度 15/10/5/5 单位/秒，随舰船大小变化。不兼容 不稳定推进器。`，其中四个数字高亮。
3. 舰船最大速度获得 15（护卫舰档）加成；生涯舰队面板与战斗中的数值一致。
4. 给同一艘船先装“不稳定推进器”再尝试安装本船插：不可安装，原因显示“不兼容不稳定推进器”。
5. 游戏内实装效果见下图（装配界面截图）：

![](./hullmod_play.png)

6. `starsector.log` 中 `Loading hullmod [稳定化推进器] (source: [.../data/hullmods/hull_mods.csv])` 一行存在，没有 `ERROR`。

## 实例驱动故障分析

每条给出触发条件、可观察结果与修正。

1. **装配界面找不到船插。**
   - 触发：CSV 行未写入、`id` 与其他来源重复、`unlocked` 缺失且玩家没有对应蓝图、`script` 类名写错。
   - 可观察：列表中没有“稳定化推进器”，多数情况日志无报错（跨文件重复 id 是静默跳过）。
   - 修正：确认行确实在 MOD 的 `data/hullmods/hull_mods.csv`；`id` 使用 `GearIn_` 前缀且全局唯一；调试期保持 `unlocked=TRUE`；核对 `script` 的包名与类名。
2. **加载阶段中断，日志出现 JSONException 或 `Duplicate key`。**
   - 触发：`cost_*` 四列之一留空、`name`/`id`/`script` 缺失、同一文件内两行 id 相同。
   - 可观察：加载阶段异常堆栈，船插表加载失败。
   - 修正：四档 cost 都填数字；必填列不空；同文件内不重复 id。
3. **`Problem loading class [...]` 异常。**
   - 触发：`script` 列类名与类的包名+类名不一致，或类不存在。
   - 可观察：第一次使用该船插（查看 tooltip、生成舰船、同步舰队数据）时抛 `RuntimeException("Problem loading class [...]")`；如果游戏尝试即时编译同名源码失败，日志还会出现 `Error loading [...]`。
   - 修正：本示例必须恰好是 `data.scripts.gearin.hullmods.GearInStableInjectorHullMod`，源码按 `data/scripts/gearin/hullmods/` 的包路径存放。
4. **效果不生效（速度没有变化）。**
   - 触发：把 `stats.getMaxSpeed().modifyFlat(...)` 写进了 `applyEffectsAfterShipCreation`。
   - 可观察：生涯面板没有加成；因为 `HullModEffect` 的 API 注释明确该方法 **不**被生涯调用、不应承担数值修改。
   - 修正：数值修改一律放在 `applyEffectsBeforeShipCreation`。
5. **两个船插的数值互相覆盖，或卸载一个把另一个的加成也清掉。**
   - 触发：多个船插用同一个硬编码 source id（如都写 `modifyFlat("bonus", ...)`），或同一船插在不同地方用不同 id。
   - 可观察：后应用者覆盖先应用者；按 id 解除时误伤其他船插。
   - 修正：一律使用回调传入的船插 id（即 CSV 的 `id`）作为 source id，需要恢复/覆盖时用同一个 id。
6. **互斥失效：两个推进器能同时安装。**
   - 触发：只写了数据行，没写 `isApplicableToShip`。
   - 可观察：两者都可安装、效果叠加。
   - 修正：`isApplicableToShip` 返回 `false` + `getUnapplicableReason` 给原因。注意这两个方法只在装配/自动装配流程生效；不要用 `applyEffectsAfterShipCreation` 里 `removeMod` 的方式补救——该方法只在战斗内调用，战斗内修改的是共享 variant，且不是数值入口。
7. **描述文本出现空位或未替换的 `%s`。**
   - 触发：`desc` 的 `%s` 数量与 `getDescriptionParam` 的返回值数量不一致，或对应 index 返回 `null`。
   - 可观察：tooltip 中 `%s` 位置空白/错位（tooltip 按 index 0..9 依序取参数）。
   - 修正：`desc` 的每个 `%s` 在 `getDescriptionParam` 中都有对应 index 返回非 null 字符串。
8. **图标显示异常。**
   - 触发：`sprite` 路径指向不存在的贴图，或文件名与磁盘不一致。
   - 可观察：装配界面图标异常，加载阶段日志出现贴图资源错误。
   - 修正：路径以 MOD 根目录为基准，写成 `graphics/hullmods/GearIn_stable_injector.png` 并确认文件存在。
9. **隐藏语义用错。**
   - 触发：把 `hidden` 和 `hiddenEverywhere` 混用。
   - 可观察：设 `hidden=TRUE` 但列表仍能看到（说明写到了 `hiddenEverywhere` 或都没生效）；或 `hiddenEverywhere=TRUE` 时玩家在已装船插栏看不到它，误以为失效。
   - 修正：`hidden` 隐藏“可选列表”，`hiddenEverywhere` 连“已安装显示”一起隐藏，两者效果都照常生效。

## 总结

最小船插的完整流程是：`data/hullmods/hull_mods.csv` 一行数据（`id` 唯一、`script` 指向效果类、四档 `cost` 必填）→ 加载阶段生成 `HullModSpecAPI` → 效果类按需实例化为会话级单例。

生涯（`FleetMemberAPI`）与战斗（`ShipAPI`）两处使用方调用 `applyEffectsBeforeShipCreation`。

装配界面用 `isApplicableToShip`/`getUnapplicableReason` 限制安装、tooltip 用 `getDescriptionParam` 填充描述。

内置船插通过 `data/hulls/*.ship` 的 `builtInMods` 进入同一条回调链且不消耗装配点。

调试时按“数据行 → 类名 → 回调 → 描述”四段排查：先确认注册 （日志 `Loading hullmod [...]`），再确认类名，再确认回调位置，最后确认 `%s` 数量。

这个“数据行 + 效果类”的组合模式是后续所有可注册游戏对象的通用模板：战术系统、武器、舰船都会沿用“注册数据行 + 类回调”的完整流程，只是数据表与接口族不同。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插效果类接口，含会话级单例与回调职责的 API 注释。
- `com.fs.starfarer.api.combat.BaseHullMod`：接口默认实现，建议继承；含 `isBuiltIn`、`canBeAddedOrRemovedNow` 等现成逻辑。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`、`com.fs.starfarer.api.combat.MutableStat`：数值入口与 `modifyFlat`/`modifyPercent` 修改方法。
- `com.fs.starfarer.api.combat.ShipAPI`、`com.fs.starfarer.api.combat.ShipVariantAPI`、`com.fs.starfarer.api.combat.ShipHullSpecAPI`：舰船与 variant 的读取入口 （`getHullSize`、`getHullMods`、`hasHullMod`、`getBuiltInMods` 等）。
- `com.fs.starfarer.api.combat.ShipAPI.HullSize`：四档舰船大小枚举（`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP`）。
- `com.fs.starfarer.api.loading.HullModSpecAPI`：船插数据规格接口（`getEffect` 等）。
- `com.fs.starfarer.api.impl.campaign.ids.HullMods`、`com.fs.starfarer.api.impl.campaign.ids.Tags`：船插 id 与 tag 常量（`UNSTABLE_INJECTOR`、`HULLMOD_*` 系列）。
- `com.fs.starfarer.api.impl.campaign.DModManager`：D 插抽取底池（`damage`）与按舰船属性的过滤剔除。
- `com.fs.starfarer.api.campaign.impl.items.ModSpecItemPlugin`：蓝图抽取的 tier 过滤与 rarity 权重。
- `com.fs.starfarer.api.impl.hullmods.ShieldShunt`：原版互斥船插的写法参考（`isApplicableToShip` + `getUnapplicableReason`）。
- `data/hullmods/hull_mods.csv`：船插注册数据表（原版 `unstable_injector` 行与各 D 插行的 tags 可对照）。
- `data/hulls/afflictor.ship`：`builtInMods` 数组的内置船插声明示例。
- `data/campaign/procgen/drop_groups.csv`：`item_modspec` 蓝图掉落表。
- `graphics/hullmods/generic_mod.png`：`sprite` 缺省贴图。
