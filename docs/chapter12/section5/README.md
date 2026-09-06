# 制作市场条件

本节制作并注册一个市场条件，让条件插件在市场上应用、推进和移除效果。实例标识、勘测与抑制状态会决定条件能否出现和生效。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 在 `market_conditions.csv` 中注册条件，并实现插件生命周期；
- 区分条件 spec ID 与实例 token，准确查询和移除条件；
- 判断条件级勘测状态与市场勘测等级的作用范围；
- 管理条件抑制、解除抑制和重新应用时机；
- 根据市场效果与日志定位条件实例和状态故障。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C2-S2] 的 CSV 合并、重复键与空 id 行处理；
- [C7-S1] 的 `GearInModPlugin`、`onGameLoad` 与持久数据标记；
- [C8-S4] 的 `MutableStat、`StatBonus` 与 modifier 写入；
- [C10-S1] 的一次性 `EveryFrameScript` 与 `isDone()` 自停语义；
- [C10-S2] 的 `addPlanet` 条件市场与实体侧 survey 关系；
- [C12-S3] 的 `MarketAPI` 条件访问、实例 token 与条件移除；
- [C16-S2] 的 `loadJSON`，以及 [C16-S3] 的 `getMergedSpreadsheetData` 资源读取入口。

## 术语

按术语约定，本节首次完整讲解以下词：

- **市场条件 (condition)**：API 名保留 `MarketConditionAPI`(实例接口)、`MarketConditionSpecAPI`(静态配置)、`MarketConditionPlugin`(行为插件)。一个条件由一条 CSV 注册行 (spec) 和市场上的若干实例 (每个实例带唯一 token 与一个 plugin 实例) 组成。
- **token**：`addCondition(id)` 返回的唯一实例 id(`getIdForPluginModifications()`，形如 `ore_sparse_b44c3`)；spec id ≠ 实例 token。后续对 "具体实例" 的操作都以 token 定位。
- **survey(勘测)**：两层语义 —— 条件级 `isSurveyed` / `requiresSurveying`(该条件是否已被勘测、是否需要勘测) 与市场级 `SurveyLevel`(`NONE / SEEN / PRELIMINARY / FULL`)。
- **suppression(抑制)**：`suppressCondition(id)` / `unsuppressCondition(id)` 维护的 suppressed id 集合；被抑制的条件在重算时不再生效，但实例保留。
- **重算 (reapply)**：`MarketAPI.reapplyConditions()`(全部) 与 `reapplyCondition(token)`(单个) 对条件 plugin 执行 unapply → apply 的刷新流程。

## 梳理市场条件关系

三个对象分别落在 CSV 注册表、运行实例与行为插件上，先从注册链看起。

### market\_conditions.csv 注册链

原版注册表是 `data/campaign/market_conditions.csv`(169 行)。

### market\_conditions.csv 字段速查表

表头九个字段，每列被谁使用：

| 字段 | 写入对象 | 语义 / 使用方 |
|---|---|---|
| `name` | `MarketConditionSpecAPI.getName()` | 显示名；必填 (`getString`，缺列即加载失败)。`MarketConditionAPI.getName()` 先问 plugin，plugin 回退 spec 名 |
| `id` | spec 注册 key | 条件 spec id，CSV spec 加载期注册表的键；`addCondition(id)` 的入参；`ConditionGenDataSpec` 交叉校验键 |
| `tags` | `getTags()` | 逗号分隔字符串，`optString` 可选 |
| `planetary` | `spec.isPlanetary()` | `optBoolean` 默认 false；`MarketConditionAPI.isPlanetary()` 转发 plugin(默认读 spec)；"行星条件显示在条件面板右侧，市场条件显示在行星条件左侧的组里" |
| `decivRemove` | `spec.isDecivRemove()` | `optBoolean` 默认 false；文明衰退移除标记 |
| `script` | `getScriptClass()` | **plugin 类名**；必填。加载期经脚本类加载器 `registerScriptClass` 注册；实例化时机是条件实例创建时 |
| `desc` | `getDesc()` | tooltip 正文；`optString` 默认 `"No description... yet"`；支持 `$token` 替换 |
| `icon` | `getIcon()` | 图标贴图路径；**留空回退原版 `graphics/hullmods/generic_mod.png`**；非空时资源加载期排队 TEXTURE，路径无效 → 加载期失败 |
| `order` | `getOrder()` | `optDouble` 默认 20.0；条件列表排序 |

**加载入口**：CSV spec 加载期用 `readCSV("id", "data/campaign/market_conditions.csv", true)` 读取，逐行构造 `MarketConditionSpecAPI`。合并语义与 `hull_mods.csv` 相同 (同一读取器实现)：

**同文件重复 id 抛 `Duplicate key [...] while loading [...]`；跨文件重复 id 先到先得、后读入行静默跳过；空 id 行跳过**。MOD 只提交自己的行即可追加进原版表。

**`MarketConditionSpecAPI` 构造**：`id` / `name` / `icon` / `script` 四列 `getString` **必填** (缺列 → 加载期 `JSONException`)；`desc` / `order` / `planetary` / `decivRemove` / `tags` 可选。

**plugin 实例化**：`spec.getNewPluginInstance()` → 脚本类加载器 `loadScriptInstance(scriptClass)`，按类名注册表 `newInstance`**，必须无参可实例化** (类实现了接口并不等于实例会被调用，这一点在既有脚本讲解中已经确立)。

实例化时机是条件实例创建时，不是 CSV 加载时。

**交叉校验的完整流程**：procgen 表 `data/campaign/procgen/condition_gen_data.csv` 的每一行 (除 `_no_pick` 后缀) 必须在 `market_conditions.csv` 有对应行。

否则**加载期**抛 `RuntimeException("[...] is not a valid market condition, was loading: condition_gen_data.csv")`。

即：写 procgen 条件行前必须先有 market\_conditions.csv 行。

### MarketConditionAPI 生命周期

注册链让 spec 在加载期就位，实例则在运行期由插件按生命周期回调驱动，先看接口本身。

### 条件接口速查表

**接口速查** (`com.fs.starfarer.api.campaign.econ.MarketConditionAPI`、`com.fs.starfarer.api.characters.MarketConditionSpecAPI`、`com.fs.starfarer.api.campaign.econ.MarketConditionPlugin`)：

| 接口 | 高频成员 | 语义 |
|---|---|---|
| `MarketConditionAPI` | `getId()` / `getName()` / `getSpec()` / `getPlugin()` | spec id；显示名；静态配置；plugin |
| `MarketConditionAPI` | `getIdForPluginModifications()` | **唯一 token**(`id + "_" + genUID`) |
| `MarketConditionAPI` | `isSurveyed()` / `setSurveyed(boolean)` / `requiresSurveying()` | 条件级 survey；`requiresSurveying` 读 procgen 表的 `reqSurvey` |
| `MarketConditionAPI` | `isPlanetary()` | 转发 plugin(默认 spec 标记)，决定条件面板位置 |
| `MarketConditionSpecAPI` | `getOrder()` / `getIcon()` / `getDesc()` / `getScriptClass()` / `getTags()` / `isPlanetary()` / `isDecivRemove()` | CSV 九字段的运行时面 |
| `MarketConditionPlugin` | `init(market, condition)` / `setParam(Object)` / `apply(String id)` / `advance(float amount)` / `unapply(String id)` | 生命周期回调 |
| `MarketConditionPlugin` | `isTransient()` / `showIcon()` / `runWhilePaused()` / `getName()` / `getIconName()` | 存档与展示语义 |
| `MarketConditionPlugin` | `getTokenReplacements()` / `getHighlights()` / `getHighlightColors()` / `hasCustomTooltip()` / `createTooltip(...)` / `isTooltipExpandable()` / `getTooltipWidth()` | tooltip 链路 |
| `MarketConditionPlugin` | `getRelatedCommodities()` | 仅事件来源条件使用 |

**实例创建**：`MarketAPI.addCondition(id[, param])` 的完整顺序：

1. 创建 `MarketConditionAPI` 实例：从 CSV spec 加载期的注册表取 spec，**spec 缺失抛 `RuntimeException("Market condition [" + id + "] not found")`**；plugin 用到时才创建并 `init(market, condition)`。
2. 加入 `conditions` 列表、`resetCache()`。
3. `param` 非 null 时 `setParam(param)`(在 apply 之前)。
4. **未 suppressed 时立即 `plugin.apply(token)`**，注意这里只查 suppression，不查 surveyed。
5. `getGenSpec()` 非 null 时 `setSurveyed(!genSpec.isRequiresSurvey())`，**survey 状态在 apply 之后才写入**。
6. 返回 token(`getIdForPluginModifications()`)。

**推进**：`MarketAPI.advance(amount)` 每帧遍历全部条件 plugin 调 `advance(amount)`；`runWhilePaused()` 返回 false(基类默认) 时暂停分支不推进。

**移除/重算**：`unapply(id)` 在三条路径被调用：`removeCondition` / `removeSpecificCondition`(移除时)、`reapplyConditions` / `reapplyCondition`(重算时)。

**存档语义**：`isTransient()` 决定 plugin 是否进存档。`BaseMarketConditionPlugin.isTransient()` = true —— 写存档时 plugin 实例被丢弃，读档时按 spec 重建 plugin 并重新 `init`。

**不重放 `setParam`**(读档重建时只执行 `init`)。

`BaseMarketConditionPlugin2` 非 transient(`isTransient()` = false)，进存档、附带 `daysActive` 计时，"Use sparingly since it'll add to savefile bloat"。

### token 与实例访问

- **token 生成**：`id + "_" + Misc.genUID()`，全存档唯一；两个同 spec id 实例 token 必然不同。
- **四个查询入口** (`MarketAPI`)：
    - `getConditions()`：活列表，遍历前先拷贝；
    - `getCondition(id)` / `getFirstCondition(id)`：**同 spec id 的首个实例**，走 `conditionsCache`(按 spec id 首见即存、增删时 `resetCache`)；
    - `getSpecificCondition(token)`：按 token 线性扫描，精确实例。
- **移除不对称性** (市场侧访问入口讲解中已点名的必须遵守的规则，本节给出实现)：
    - `removeCondition(id)`：遍历移除**同 spec id 的全部实例**，逐个 `unapply`；
    - `removeSpecificCondition(token)`：只移除 token 对应实例并 `unapply`，随即返回。

删除具体实例必须用 `removeSpecificCondition(token)`

`removeCondition(id)` 移除同 spec id 的**全部**实例 —— 只想删一个实例时用 id 会连带删除其他实例并逐个 `unapply`，修饰全部回滚。请保留 `addCondition` 返回的 token 再删。

- **修饰隔离**：条件 plugin 往市场写修饰 (hazard、accessibility、stats 等) 时，原版 `BaseHazardCondition.apply(id)` 直接 `market.getHazard().modifyFlat(id, ...)`。
    - `apply` 传入的 id 就是 token，`BaseMarketConditionPlugin.getModId()` 是等价便捷入口。

禁止用 spec id 当 modifier source

修饰 source 必须用实例 token(`apply` 传入的 id 或 `getModId()`)：同 spec id 多实例会互相覆盖 —— 后 `apply` 覆盖先 `apply`，`unapply` 其中一个实例时全部修饰一起丢失。

### survey 语义

survey 是**两层**状态，别混用：

- **市场级 `SurveyLevel`**(`NONE / SEEN / PRELIMINARY / FULL`)：用户对行星的勘测进度。`CampaignPlugin.updateMarketFacts` 把 `$isSurveyed` / `$surveyLevel` 写进市场 memory。
- **条件级 `isSurveyed` / `requiresSurveying`**：单条条件的勘测状态。`requiresSurveying()` 查 `condition_gen_data.csv` 对应行的 `reqSurvey` 列 (表内 `ore_sparse` / `ore_abundant` / `ruins_*` 等资源类条件为 `TRUE`)。

### 条件 survey 初始值速查表

初始值来自四个入口：

| 入口 | 设置 | 出处 |
|---|---|---|
| procgen 行星生成 | `mc.setSurveyed(!genSpec.isRequiresSurvey())` | `PlanetConditionGenerator` |
| `addCondition(id)` | genSpec 非 null 时同上 | `MarketAPI.addCondition` |
| `economy.json` 的 `startingConditions` | **强制 `setSurveyed(true)`** | `economy.json` 加载期 |
| `Misc.setFullySurveyed`(勘测完成) | 全部条件 `setSurveyed(true)` + `setSurveyLevel(FULL)` | `com.fs.starfarer.api.util.Misc` |

用户侧完整链路：`addPlanet` 为行星挂条件市场 `market_<planetId>`(`Misc.initConditionMarket`，行星加入所在位置时调用；市场 id = `"market_" + planet.getId()`)。

打开行星调查面板时未勘测市场置 `PRELIMINARY`。

勘测完成 `applySurveyResults` → `Misc.setFullySurveyed` → 殖民界面 `showColonizeScreen` 开头 `market.reapplyConditions()` → 条件效果从此生效。

**重算检查** (本节核心)：

- `reapplyConditions()`：逐个 `unapply`；**仅 `isSurveyed() && !isConditionSuppressed(id)` 的条件才 `apply`**。
- `reapplyCondition(token)`：单实例 `unapply` +(未 suppressed)`apply`；**不检查 surveyed**，两个重算入口的检查条件不同。
- 未勘测条件的可观察行为：`addCondition` 当帧效果已生效 (apply 不查 surveyed)，**下一次重算后效果消失** (unapply 执行、apply 被跳过)；勘测完成后恢复。
- UI 侧：勘测扫描面板与勘测结果面板只展示 `isSurveyed()` 为 true 的条件。
- 运行期条件市场是常规 `Market` 实例，重算同时检查 surveyed 与 suppression。

### suppression 语义

- **机制**：`suppressCondition(id)` 把 spec id 加进市场的 `suppressedConditions` 集合 (用到时才创建)；`isConditionSuppressed(id)` 查集合。
    - `unsuppressCondition(id)` 移除，集合清空且未设置 `retainSuppressedConditionsSetWhenEmpty` 时置 null。
- **生效时机**：`suppressCondition` **本身不立即移除效果** —— 只在下次重算时，`reapplyConditions` 对它 `unapply` 但跳过 `apply`。**可观察结果**：suppress 后效果仍在，下一次经济迭代 (或稳定度变化、手动重算) 后才消失。

suppress 后立即读数值，效果还在

`suppressCondition(id)`：需要立即生效时，请显式调用重算后再读数。

- **实例保留**：suppression 只抑制 "生效"，`getConditions()` 里实例仍在、`getSpecificCondition(token)` 仍可查到。
- **持久化**：集合随市场存档；空集在写档时置 null。

原版为什么需要 suppression

产业或物品在生效期间 "占位" 覆盖一组条件：`PlanetaryShield` 在 `apply` 时 `market.suppressCondition(Conditions.METEOR_IMPACTS)`、`unapply` 时配对 `unsuppressCondition`；

`SolarArray` 抑制 `HOT` / `POOR_LIGHT`；Fusion Lamp 物品效果抑制一组条件。

原版所有用例都在 `apply` / `unapply` 中成对出现。

`suppressCondition(id)` 必须配对 `unsuppressCondition(id)`

suppressed 集合只增不减：漏掉配对会让该条件的效果永久休眠，重算时 `unapply` 执行、`apply` 永不回来。

### 重算触发

`reapplyConditions()` 被谁调用 (三个触发点)：

1. **经济迭代**：经济批量处理任务对每个市场调用 `reapplyConditions()` + `reapplyIndustries()`，每月 10 次迭代。
2. **稳定度变化**：`MarketAPI.advance` 检测稳定度数值发生变化时 → `reapplyConditions()` + `reapplyIndustries()` + `updatePrices()`。
3. **手动调用**：`reapplyConditions()` / `reapplyCondition(token)`；勘测完成后的殖民界面也走这条路径。

游戏内自然观察：给市场建好行星护盾 (`PlanetaryShield`) 后，`meteor_impacts` 条件的效果在**下一个经济迭代**消失，条件图标仍在 —— suppression + 重算的完整流程。

## 规则强度

机制讲完，把必须遵守与值得遵循的做法集中成清单，每条都附违反后的可观察结果：

### 规则强度速查表

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| `addCondition(id)` 的 id 必须存在于 `market_conditions.csv`(含跨 MOD 合并表)；spec 缺失时实例构造抛 `RuntimeException("Market condition [id] not found")` | 必须 | 运行期抛异常，条件未添加 |
| 条件 plugin 写市场修饰时，modifier source 必须用实例 token(`apply` 传入的 id 或 `getModId()`)，禁止用 spec id | 必须 | 同 spec id 多实例的修饰互相覆盖：后 apply 覆盖先 apply，`unapply` 一个实例全部修饰丢失 |
| 移除具体条件实例必须用 `removeSpecificCondition(token)`；`removeCondition(id)` 移除同 id 全部实例 | 必须 | 同 id 其他实例被连带移除并 unapply |
| `suppressCondition(id)` 必须配对 `unsuppressCondition(id)`；且效果变化在下次重算后才可观察 | 必须 | 漏配对 → 条件效果永久休眠；suppress 后立刻查数值 → 效果还在 (重算前不变) |
| transient 条件插件 (`BaseMarketConditionPlugin` 默认) 不得依赖 `setParam` 数据跨存档；需要持久参数必须改用非 transient 插件 (`BaseMarketConditionPlugin2`) 或把参数另存 (如市场 memory) | 必须 | 读档后条件参数恢复默认值：plugin 不进存档、读档重建不重放 `setParam` |
| 新增条件 id 若同时写入 `condition_gen_data.csv`，两表必须都有该行 | 必须 | 加载期 `RuntimeException("[...] is not a valid market condition, was loading: condition_gen_data.csv")` |
| 条件 icon 列留空会回退原版 `graphics/hullmods/generic_mod.png`；写一个不存在的路径则资源加载期失败 | 建议 | 留空：使用回退图标；写错路径：加载期报错 (TEXTURE 无 try/catch) |
| 遍历 `getConditions()` 前先拷贝 (活列表) | 建议 | 遍历中增删条件导致迭代器失效类问题 |
| 需要暂停时继续推进的条件必须覆写 `runWhilePaused()` 返回 true(基类默认 false) | 建议 | 暂停期间条件 plugin 不推进 (依赖时间流逝的效果停摆) |

## 实现市场条件流程

先注册条件 spec，再用实例 token 驱动修饰、重算与移除。以下片段按运行顺序递进。

### 注册条件规格

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
GearIn Ore Deposits,GearIn_ore_deposits,,TRUE,,data.scripts.gearin.campaign.GearInOreCondition,"Rich ore veins run beneath $marketName.",,50
```

把这一行登记进 `data/campaign/market_conditions.csv` 后，加载日志会出现 `GearIn_ore_deposits`，spec 的 plugin 类与 `GearInOreCondition` 一致。

### 用实例 token 写入修饰

修饰的写入与撤销都发生在 `GearInOreCondition` 内。

```java
@Override
public void apply(String id) {
	market.getHazard().modifyFlat(id, HAZARD_MOD, getName());
	market.getAccessibilityMod().modifyFlat(id, accessibilityBonus, getName());
}

@Override
public void unapply(String id) {
	market.getHazard().unmodifyFlat(id);
	market.getAccessibilityMod().unmodifyFlat(id);
}
```

添加条件后 hazard 增加 0.15、accessibility 增加 0.10，移除具体 token 后两项同时恢复。

### 创建两个独立实例

这段实例创建代码位于 `GearInConditionLifecycleScript.runLifecycle`。

```java
String token1 = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
String token2 = market.addCondition(GearInOreCondition.ORE_DEPOSITS, 0.20f);
MarketConditionAPI first = market.getFirstCondition(GearInOreCondition.ORE_DEPOSITS);
```

运行后，两个 token 不同，首实例为 token1，两个 accessibility 修饰分别为 0.10 与 0.20。

### 抑制效果并重新启用

在保留一个实例之后执行这段抑制与恢复。

```java
market.suppressCondition(GearInOreCondition.ORE_DEPOSITS);
float beforeReapply = market.getHazard().getModifiedValue();
market.reapplyConditions();
float afterReapply = market.getHazard().getModifiedValue();
market.unsuppressCondition(GearInOreCondition.ORE_DEPOSITS);
market.reapplyConditions();
```

suppress 当帧数值不变，重算后效果消失；unsuppress 并重算后恢复。

### 核对勘测门控并清理实例

生命周期演示末段执行这段勘测门控与清理。

```java
String token = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
MarketConditionAPI condition = market.getSpecificCondition(token);
condition.setSurveyed(false);
market.reapplyConditions();
condition.setSurveyed(true);
market.reapplyConditions();
market.removeSpecificCondition(token);
```

未勘测重算后效果休眠，重新勘测后恢复，按 token 移除只删除该实例并回到基线。

## 组装市场条件实现

规则只有落进代码才算数。下面把注册、生命周期、重算与两类检查串成一条可运行的链路。

在既有 MOD 布局与 `GearInModPlugin` 注册结构之上新增一个数据文件 (追加一行) 与两个类。本节新增条件 ID `GearIn_ore_deposits`(示例所有权 `GearInOreCondition`)，只由本节定义：

```text {7,11,12}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── market_conditions.csv   # 本节新增：在文件末尾追加下面一行
├── jars\                           # 构建产物目录 (gradle jar)
└── src\
    └── data/scripts/gearin/campaign\
        ├── GearInOreCondition             # 本节新增 (归本节所有)
        └── GearInConditionLifecycleScript # 本节新增 (归本节所有)
```

### market\_conditions.csv 追加行

追加到 `data/campaign/market_conditions.csv`(表头沿用原版九列；MOD 行按 id 合并进原版表，跨文件无重复冲突)：

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
GearIn Ore Deposits,GearIn_ore_deposits,,TRUE,,data.scripts.gearin.campaign.GearInOreCondition,"Rich, dense ore veins run deep beneath the surface of $marketName, hinting at profitable mining opportunities.",,50
```

逐字段对照 (静态交叉核对)：

- `id` = `GearIn_ore_deposits`：与 `GearInOreCondition.ORE_DEPOSITS` 常量逐字一致，是 `addCondition` 的唯一合法入参；
- `script` = `data.scripts.gearin.campaign.GearInOreCondition`：与类全名逐字一致，加载期 `registerScriptClass`、实例化时按名 `newInstance`；
- `planetary` = `TRUE`：条件显示在行星条件区 (面板右侧)；
- `icon` **留空**：回退原版 `graphics/hullmods/generic_mod.png`(本单元无 PNG 资产，留空是有意为之)；
- `desc` 里的 `$marketName` 由 `BaseMarketConditionPlugin` 的 token 替换链填充 (`getTokenReplacements()` 提供 `$marketName` / `$marketFaction` / `$marketSystem` 等，tooltip 创建时逐个 `replaceAll`)；
- `order` = 50：与原版 ore 族 (order 50/60) 同一区间。

### GearInOreCondition

对应关系：

- `extends BaseMarketConditionPlugin`：免费获得 `init`、`getTokenReplacements`、tooltip 默认实现、`isTransient() = true` 等全套默认；`getModId()` 返回实例 token。
- `apply(id)` 的 `id` 参数就是实例 token：修饰 source 用 token，多实例互相隔离 (规则强度第 2 条)；`getName()` 来自 spec 的 `name` 列。
- `setParam(Object)`：`addCondition(id, param)` 的第二参在 apply 之前到达 (`MarketAPI.addCondition` 的调用顺序)，示例用 `Number` 覆盖 accessibility 默认值。
- `createTooltipAfterDescription`：spec desc(含 `$marketName` token) 之后追加数值行 (tooltip 创建时先替换 token 再调本钩子)。

### GearInConditionLifecycleScript

对应关系：

- `REGISTER_MARK = "GearIn_ConditionLifecycleScript"`：persistent data 检查键，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；与既有市场演示脚本 (`"GearIn_MarketInspector"`) 是各自独立的键。
- 第 1 步 `getSpecificCondition(token)`：`addCondition` 返回 token 后按 token 精确取实例；`getGenSpec() == null` 核对本条件无 procgen 行 (因此 `requiresSurveying()` = false、初始 `surveyed` = true)。
- 第 2 步 `addCondition(id, 0.20f)`：`setParam` 在 apply 之前被调用，`accMod2` = 0.20 证明参数生效；`getFirstCondition(id)` 返回首个实例 (token1)。
- 第 3 / 6 步：`removeCondition(id)` 与 `removeSpecificCondition(token)` 的不对称性双向演示 (规则强度第 3 条)。
- 第 5 步：suppress 后**先**读 hazard(效果仍在)，**再** `reapplyConditions()`(效果消失) —— 演示 "suppress 只写集合、效果在重算后才消失"(规则强度第 4 条)。
- 第 7 步：`setSurveyed(false)` 后重算 → unapply 执行、apply 跳过 (hazard 回落基线)；`setSurveyed(true)` 后重算 → 恢复，对应 survey 检查。
- 第 8 步：`isPlanetConditionMarketOnly()` 过滤条件市场 (行星市场 `market_<planetId>`)，`requiresSurveying()` 读 procgen 表 `reqSurvey`，只读对照，不做任何修改。

调用点 (工程片段，方法所在类归既有注册类所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(既有注册类)onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInConditionLifecycleScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInConditionLifecycleScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInConditionLifecycleScript());
    }
```

想演示换目标市场，注册前先设参数：

```java {2}
    GearInConditionLifecycleScript script = new GearInConditionLifecycleScript();
    script.setTargetMarketId("asharu"); // corvus 星系独立势力市场 (economy.json 首实体 id 即市场 id)
    Global.getSector().addScript(script);
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，与既有示例相同)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInOreCondition.class` 与 `data/scripts/gearin/campaign/GearInConditionLifecycleScript.class`(可用 `jar tf` 核对)。

## 关键故障实验：添加条件时报告规格不存在

代码使用的 ID 与 CSV 注册键不一致。

**局部故障片段：**

```java
// 故障片段
market.addCondition("GearIn_ore");
```

**局部修正片段：**

```java {2}
// 修正片段
String token = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
MarketConditionAPI condition = market.getSpecificCondition(token);
```

实际症状：`addCondition` 抛出 `Market condition [GearIn_ore] not found`，没有实例 token，重算也没有对象可处理。

原因：注册键是 `GearIn_ore_deposits`；`reapplyConditions()` 只能重放现有实例，不能补注册缺失 spec。

复验：修正后 `condition` 非 null，`condition.getId()` 为完整 spec ID，hazard 与 accessibility 修饰的 source 等于返回 token。

## 验证方案

前置数据：既有 MOD 布局与 `GearInModPlugin` 注册结构 (含本节调用点片段，[C6-S1]、[C7-S1] 已确立)、`market_conditions.csv` 追加行、两个 Java 类。

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含两个 class。
2. 启动游戏，查看 `Loading condition: GearIn_ore_deposits` 加载日志。
3. **新建生涯**：在 `starsector.log` 中观察 `GearIn condition lifecycle` 相关记录。
4. **数值核对**：`add` 行 `plugin=GearInOreCondition`、`planetary=true`、`surveyed=true`、`genSpec=null`、`icon=graphics/hullmods/generic_mod.png`；`applied` 行 `hazardModSource` 等于 `token`、`accModValue=0.10`；`multi` 行 `distinct=true`、`accMod1=0.10 accMod2=0.20`。
5. **不对称性**：`removeAll` 后 `conditions` 回到基线，`removeOne` 后同样回到基线。
6. **suppression 时序**：`suppress` 行 `hazardBeforeReapply` 带修饰、`hazardAfterReapply` 回到基线、`conditions` 多一个；`unsuppress` 行恢复。
7. **survey 检查与原版对照**：`surveyGate` 行 `afterAdd` 与 `afterSurveyedReapply` 相等、`afterUnsurveyedReapply` 回到基线；`vanillaSurvey` 行存在 (具体行星与条件以存档生成结果为准，`none found` 分支也属预期)。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到两个 class。加载期 `starsector.log` 出现 `Loading market conditions` 与 `Loading condition: GearIn_ore_deposits`(加载器的既有日志格式；原版每行条件同样各打一条)。

- **新建生涯**：`starsector.log` 依次出现。

    ```text
    GearIn condition lifecycle start: market=jangala conditions=6 hazard=1.25 accessibilityFlat=1.00
    ```

    (`conditions` 数、`hazard`、`accessibilityFlat` 以实际存档为准；Jangala 原版 6 条件、hazard 1.25)。

- **add 与 applied**(token 随机后缀以日志为准)：

    ```text
    GearIn condition lifecycle add: id=GearIn_ore_deposits token=GearIn_ore_deposits_xxxxx plugin=GearInOreCondition planetary=true surveyed=true reqSurvey=false genSpec=null specOrder=50 icon=graphics/hullmods/generic_mod.png GearIn condition lifecycle add: desc=Rich, dense ore veins run deep beneath the surface of $marketName, hinting at profitable mining opportunities. GearIn condition lifecycle applied: hazard=1.40 hazardModSource=GearIn_ore_deposits_xxxxx hazardModValue=0.15 accessibilityFlat=1.10 accModValue=0.10 GearIn condition lifecycle tokens: $marketName=Jangala $marketFaction=Hegemony
    ```

    核对：`plugin=GearInOreCondition`(script 列生效)；`planetary=true`(CSV 列生效)；`surveyed=true`、`reqSurvey=false`、`genSpec=null`(无 procgen 行 → 默认已勘测)；`icon` 是回退的 `graphics/hullmods/generic_mod.png`(icon 列留空)；`hazardModSource` 与 `token` 相同 (modifier source 即实例 token)；`desc` 打印的是 `spec.getDesc()` 的**原始文本** (含字面 `$marketName`) —— token 替换只发生在 tooltip 创建时，演示脚本从不创建 tooltip，日志不会看到替换值。

- **多实例 + param**：

    ```text
    GearIn condition lifecycle multi: token1=GearIn_ore_deposits_xxxxx token2=GearIn_ore_deposits_yyyyy distinct=true firstConditionIsToken1=true hasCondition=true hasSpecific=true GearIn condition lifecycle multi: hazard=1.40 accessibilityFlat=1.30 accMod1=0.10 accMod2=0.20
    ```

    核对：两个 token 不同；`getFirstCondition(id)` 是首个实例 (token1)；accessibility 平值叠加 `0.10 + 0.20`，且 `accMod1` / `accMod2` 各自按 token 独立可取 (修饰隔离)。

- **removeCondition(id) 删同 id 全部 (不对称性 A)**：

    ```text
    GearIn condition lifecycle removeAll: conditions=6 hazard=1.25 accessibilityFlat=1.00
    ```

    `conditions` 回到基线 (两个实例一起消失)、hazard / accessibility 全部回滚。

- **reapplyCondition(token) 单实例重算**：

    ```text
    GearIn condition lifecycle reapplyOne: hazardBefore=1.40 hazardAfter=1.40 unchanged=true
    ```

    先 unapply(回落) 再 apply(恢复)，终值不变，单实例重算是 "刷一遍"，不是叠加。

- **suppression(先不生效、重算后休眠)**：

    ```text
    GearIn condition lifecycle suppress: suppressed=true conditions=7 hazardBeforeReapply=1.40 hazardAfterReapply=1.25 (effect dormant) GearIn condition lifecycle unsuppress: suppressed=false hazard=1.40 (effect restored)
    ```

    核对：suppress 后实例仍在 (`conditions=7`)、效果未变 (`hazardBeforeReapply` 仍带修饰)；`reapplyConditions()` 后效果消失 (unapply 执行、apply 被跳过)；配对 `unsuppressCondition` 后重算恢复。

- **removeSpecificCondition(token) 只删一个 (不对称性 B)**：

    ```text
    GearIn condition lifecycle removeOne: conditions=6 hazard=1.25 accessibilityFlat=1.00
    ```

- **survey 检查**：

    ```text
    GearIn condition lifecycle surveyGate: afterAdd=1.40 afterUnsurveyedReapply=1.25 afterSurveyedReapply=1.40
    ```

    核对：新添加实例当帧生效 (apply 不查 surveyed)；`setSurveyed(false)` 后重算效果消失；`setSurveyed(true)` 后重算恢复，重算只对 surveyed 的条件 apply。

- **原版对照 (只读)**：

    ```text
    GearIn condition lifecycle vanillaSurvey: market=market_corvus_IIIa surveyLevel=NONE condition=ore_sparse surveyed=false
    ```

    (具体行星与条件以存档生成结果为准；只要存在任意 `requiresSurveying()==true` 且 `surveyed=false` 的条件即通过；若新档所有条件市场都已勘测，则出现 `none found` 一行，也属预期分支。)

- **收尾与读档**：

    ```text
    GearIn condition lifecycle final: conditions=6 hazard=1.25 accessibilityFlat=1.00 (baseline restored) GearIn condition lifecycle done
    ```

    无任何 `gear_in` 引起的 `ERROR`。读档不重复执行 (`REGISTER_MARK` 已置位)，也不出现 `GearIn condition lifecycle start` 日志。

## 故障分析

- **条件完全不出现**：ID 重复、文件路径错误或 CSV 行没有进入合并表时，日志不会出现 `Loading condition: <id>`。

    - **解决方案**：先确认注册日志，再换用唯一 ID。

- **`addCondition` 抛 `Market condition [...] not found`**：调用 ID 没有对应 spec。

    - **解决方案**：逐字核对 CSV 的 `id` 与 `ORE_DEPOSITS` 常量。

- **删除一个条件却把同 ID 全删**：`removeCondition(id)` 面向 spec ID。

    - **解决方案**：保存 `addCondition` 返回的 token，改用 `removeSpecificCondition(token)`。

- **读档后条件参数恢复默认**：transient 插件不会保存 `setParam` 状态。

    - **解决方案**：改用 `BaseMarketConditionPlugin2`，或把参数保存到市场 memory / persistent data。

- **抑制条件后效果仍在**：`suppressCondition` 只修改集合，效果要到下一次重算才撤销。

    - **解决方案**：需要立即更新时调用 `reapplyConditions()`，解除时配对 `unsuppressCondition(id)`。

- **未 survey 条件在重算后消失**：`reqSurvey=TRUE` 的条件只有在 `isSurveyed()` 后才会重新 apply。

    - **解决方案**：完成勘测，或让自定义条件不进入 procgen 检查。

- **同 spec ID 的多个实例互相覆盖**：modifier source 使用了 spec ID。

    - **解决方案**：改用 `id` 参数或 `getModId()`，让每个 token 拥有独立修饰。

- **条件图标路径错误导致加载失败**：`icon` 指向不存在的贴图。

    - **解决方案**：留空以使用 `graphics/hullmods/generic_mod.png`，或补齐真实文件。

## 总结

本节把市场条件讲成三条链：

- **注册链**：`market_conditions.csv`(九字段：`name,id,tags,planetary,decivRemove,script,desc,icon,order`)→ CSV spec 加载期 (`readCSV("id", ...)` 合并、`registerScriptClass`、icon 回退、`putSpec`)→ `addCondition(id)` 实例化 (spec 缺失抛异常)→ plugin 生命周期。MOD 追加一行即注册；加载日志 `Loading condition: <id>` 是注册成功的第一个可观察信号。
- **生命周期链**：`init(market, condition)` → `setParam(param)` → `apply(token)` → 每帧 `advance(amount)`(`runWhilePaused()` 决定暂停时是否推进)→ `unapply(token)`(移除/重算时)。`isTransient()` 决定 plugin 是否进存档；transient 插件读档重建不重放 `setParam`。
- **重算链**：三个触发点 (经济迭代、稳定度变化 `MarketAPI.advance`、手动调用)→ `reapplyConditions()`(全部)/ `reapplyCondition(token)`(单个)→ 每实例 `unapply` + 按检查条件 `apply`。两个入口的检查条件不同：`reapplyConditions()` 跳过未勘测或已抑制的实例 (**surveyed && !suppressed**)，`reapplyCondition(token)` 只跳过已抑制的实例 (不查 surveyed)。

记忆要点：

- token = `spec id + "_" + genUID`；查询用 `getCondition(id)`(首个)/ `getSpecificCondition(token)`(精确)；删除**必须**用 token(`removeCondition(id)` 删同 id 全部)。
- modifier source 必须用 token；`suppressCondition` 只写集合、效果在下次重算才消失、必须配对 `unsuppressCondition`。
- survey 两层：市场级 `SurveyLevel`(用户勘测进度)vs 条件级 `isSurveyed`(procgen `reqSurvey` 决定初始值)；未 survey 条件在重算时休眠。
- 条件 `icon` 留空回退原版图标；`condition_gen_data.csv` 与 `market_conditions.csv` 互相校验。

市场侧访问入口的既有讲解给出了 `addCondition` 返回 token、`getSpecificCondition` / `getFirstCondition` 的用法，本节补全了实现、检查与生命周期；行星市场 (`market_<planetId>`) 承载 survey 语义。

注册链决定条件 "存在"，生命周期链决定条件 "如何作用"，重算链决定检查 "何时生效"，三条链在逻辑上是同一件事的三面。token 是实例级操作的锚点，survey 与 suppression 是两类效果检查，而重算 (经济迭代、稳定度变化、手动调用) 是检查生效的唯一时机。把握这一整体，就能在任意触发点预测条件的可观察行为。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketConditionAPI`：条件实例接口 (spec id、token、survey 状态、`getGenSpec`、`isPlanetary`)。
- `com.fs.starfarer.api.characters.MarketConditionSpecAPI`：条件静态配置 (CSV 九字段的运行时面)。
- `com.fs.starfarer.api.campaign.econ.MarketConditionPlugin`：条件行为插件 (生命周期回调 + tooltip 链路 + `isTransient`)。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：条件访问族、suppression 族、`SurveyLevel`、两个重算入口。
- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`：条件插件基类 (transient 默认、token 替换、tooltip 默认实现)。
- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin2`：非 transient 条件插件 (`daysActive` 计时，进存档)。
- `com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition`：原版 hazard 条件基类 (modifier source 取实例 token 的范本)。
- `com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition`：`ore_sparse` 等资源条件的 supply 修饰。
- `com.fs.starfarer.api.impl.campaign.econ.PlanetaryShield`：原版 suppress/unsuppress 配对用法。
- `com.fs.starfarer.api.impl.campaign.econ.SolarArray`：原版条件抑制用法 (HOT / POOR\_LIGHT)。
- `com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo`：Fusion Lamp 物品效果抑制一组条件。
- `com.fs.starfarer.api.impl.campaign.procgen.PlanetConditionGenerator`：procgen 行星生成时设置条件 survey 状态。
- `com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec`：`reqSurvey` 列读取。
- `com.fs.starfarer.api.util.Misc`：`setFullySurveyed`、`initConditionMarket` 等工具入口。
- `com.fs.starfarer.api.combat.MutableStat`：`modifyFlat` / `unmodifyFlat` / `getFlatStatMod`。
- `com.fs.starfarer.api.combat.StatBonus`：`getFlatBonus()` 总量与按 source 查询。
- `com.fs.starfarer.api.impl.campaign.ids.Conditions`：`ORE_SPARSE`、`METEOR_IMPACTS` 等条件常量。
- `data/campaign/market_conditions.csv`：条件注册表 (九字段，MOD 追加行即注册)。
- `data/campaign/procgen/condition_gen_data.csv`：procgen 条件表 (`reqSurvey` 列决定初始 survey 状态)。

## 完整最小例

::: details 完整成品：GearInOreCondition

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：市场条件 (MarketConditionAPI) 插件。
 * 注册入口：data/campaign/market_conditions.csv 的 script 列 (类名逐字一致)，条件实例
 * 由脚本类加载器按类名 newInstance 并 init 绑定。apply(id) 以传入的实例 token 为
 * modifier source 写入 hazard +0.15 与 accessibility +0.10 (param 可覆盖)，unapply(id)
 * 按同一 source 撤销；token 而非 spec id 做 source，同 spec 多实例才不互相覆盖。
 * plugin 实例不进存档 (transient)，读档后按 spec 重建重新 init；tooltip 在 spec desc
 * 之后追加本条件的数值说明。
 */
public class GearInOreCondition extends BaseMarketConditionPlugin {

    /** 条件 spec id(与 market_conditions.csv 的 id 列逐字一致)。 */
    public static final String ORE_DEPOSITS = "GearIn_ore_deposits";

    /** hazard 平值修饰 (1f = 100%)。 */
    public static final float HAZARD_MOD = 0.15f;

    /** accessibility 平值修饰默认值；addCondition(id, param) 的 param 可覆盖。 */
    public static final float DEFAULT_ACCESSIBILITY_BONUS = 0.10f;

    private float accessibilityBonus = DEFAULT_ACCESSIBILITY_BONUS;

    public GearInOreCondition() {
        // 空构造：脚本类加载器按类名 newInstance，必须无参可实例化
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Number) {
            accessibilityBonus = ((Number) param).floatValue();
        }
    }

    @Override
    public void apply(String id) {
        MarketAPI m = market;
        m.getHazard().modifyFlat(id, HAZARD_MOD, getName());
        m.getAccessibilityMod().modifyFlat(id, accessibilityBonus, getName());
    }

    @Override
    public void unapply(String id) {
        market.getHazard().unmodifyFlat(id);
        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        String hazardPct = "+" + (int) (HAZARD_MOD * 100f) + "%";
        String accPct = "+" + (int) (accessibilityBonus * 100f) + "%";
        tooltip.addPara("%s hazard, %s accessibility (GearIn ore veins)", 10f,
                Misc.getHighlightColor(), hazardPct, accPct);
    }
}
```

:::

::: details 完整成品：GearInConditionLifecycleScript

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * 完整最小例：市场条件生命周期演示脚本 (token、survey、suppression、重算)。
 * 注册入口：生涯回调 onGameLoad，persistent data 键 REGISTER_MARK 一次性门控
 * (每个注册项用各自的键)；挂载后第一帧执行一次完整序列并自停 (isDone()=true)。
 * 序列依次演示 addCondition 的 spec→token 与立即 apply、多实例按 token 隔离、
 * removeCondition 与 removeSpecificCondition 的不对称、reapplyCondition 单实例重算、
 * suppress/survey 门控的重算休眠与恢复，以及原版条件市场 survey 状态只读对照；
 * 全部实例当帧移除，不遗留持久状态。
 */
public class GearInConditionLifecycleScript implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_ConditionLifecycleScript";

    private static final Logger LOG = Global.getLogger(GearInConditionLifecycleScript.class);

    /** 演示目标市场 id；默认 "jangala"(原版核心世界，必存在)。 */
    private String targetMarketId = "jangala";

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInConditionLifecycleScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内)
    }

    /** 修改演示目标市场；默认 "jangala"。 */
    public void setTargetMarketId(String targetMarketId) {
        this.targetMarketId = targetMarketId;
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runLifecycle();
    }

    private void runLifecycle() {
        SectorAPI sector = Global.getSector();
        MarketAPI market = sector.getEconomy().getMarket(targetMarketId);
        if (market == null) {
            LOG.info("GearIn condition lifecycle abort: market " + targetMarketId + " not found");
            return;
        }

        LOG.info(String.format(
                "GearIn condition lifecycle start: market=%s conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getId(), market.getConditions().size(),
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 1. addCondition：spec id -> 实例 token，plugin 立即 apply ---
        String token = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        MarketConditionAPI mc = market.getSpecificCondition(token);
        MarketConditionSpecAPI spec = mc.getSpec();
        MarketConditionPlugin plugin = mc.getPlugin();
        LOG.info(String.format(
                "GearIn condition lifecycle add: id=%s token=%s plugin=%s planetary=%s surveyed=%s reqSurvey=%s genSpec=%s specOrder=%.0f icon=%s",
                mc.getId(), mc.getIdForPluginModifications(),
                plugin.getClass().getSimpleName(), mc.isPlanetary(), mc.isSurveyed(),
                mc.requiresSurveying(), mc.getGenSpec() == null ? "null" : "present",
                spec.getOrder(), spec.getIcon()));
        LOG.info(String.format(
                "GearIn condition lifecycle add: desc=%s", spec.getDesc()));

        float hazardAfterAdd = market.getHazard().getModifiedValue();
        float accFlatAfterAdd = market.getAccessibilityMod().getFlatBonus();
        MutableStat.StatMod hazardMod = market.getHazard().getFlatStatMod(token);
        MutableStat.StatMod accMod = market.getAccessibilityMod().getFlatBonus(token);
        LOG.info(String.format(
                "GearIn condition lifecycle applied: hazard=%.2f hazardModSource=%s hazardModValue=%s accessibilityFlat=%.2f accModValue=%s",
                hazardAfterAdd,
                hazardMod == null ? "null" : hazardMod.getSource(),
                hazardMod == null ? "null" : String.format("%.2f", hazardMod.getValue()),
                accFlatAfterAdd,
                accMod == null ? "null" : String.format("%.2f", accMod.getValue())));

        Map<String, String> tokens = plugin.getTokenReplacements();
        LOG.info(String.format(
                "GearIn condition lifecycle tokens: $marketName=%s $marketFaction=%s",
                tokens.get("$marketName"), tokens.get("$marketFaction")));

        // --- 2. 多实例 + param：token 独立、修饰按 token 隔离 ---
        String token2 = market.addCondition(GearInOreCondition.ORE_DEPOSITS, 0.20f);
        MarketConditionAPI first = market.getFirstCondition(GearInOreCondition.ORE_DEPOSITS);
        LOG.info(String.format(
                "GearIn condition lifecycle multi: token1=%s token2=%s distinct=%s firstConditionIsToken1=%s hasCondition=%s hasSpecific=%s",
                token, token2, !token.equals(token2),
                first != null && token.equals(first.getIdForPluginModifications()),
                market.hasCondition(GearInOreCondition.ORE_DEPOSITS),
                market.hasSpecificCondition(token)));
        MutableStat.StatMod accMod1 = market.getAccessibilityMod().getFlatBonus(token);
        MutableStat.StatMod accMod2 = market.getAccessibilityMod().getFlatBonus(token2);
        LOG.info(String.format(
                "GearIn condition lifecycle multi: hazard=%.2f accessibilityFlat=%.2f accMod1=%s accMod2=%s",
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus(),
                accMod1 == null ? "null" : String.format("%.2f", accMod1.getValue()),
                accMod2 == null ? "null" : String.format("%.2f", accMod2.getValue())));

        // --- 3. removeCondition(id)：同 id 全部实例一起移除 (不对称性 A) ---
        market.removeCondition(GearInOreCondition.ORE_DEPOSITS);
        LOG.info(String.format(
                "GearIn condition lifecycle removeAll: conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 4. reapplyCondition(token)：单实例重算 = unapply + apply ---
        String token3 = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        float hazardBeforeReapply = market.getHazard().getModifiedValue();
        market.reapplyCondition(token3);
        float hazardAfterReapply = market.getHazard().getModifiedValue();
        LOG.info(String.format(
                "GearIn condition lifecycle reapplyOne: hazardBefore=%.2f hazardAfter=%.2f unchanged=%s",
                hazardBeforeReapply, hazardAfterReapply, hazardBeforeReapply == hazardAfterReapply));

        // --- 5. suppression：suppress 本身不改效果，下次重算才生效 ---
        market.suppressCondition(GearInOreCondition.ORE_DEPOSITS);
        float hazardSuppressedBeforeReapply = market.getHazard().getModifiedValue();
        market.reapplyConditions();
        float hazardSuppressedAfterReapply = market.getHazard().getModifiedValue();
        LOG.info(String.format(
                "GearIn condition lifecycle suppress: suppressed=%s conditions=%d hazardBeforeReapply=%.2f hazardAfterReapply=%.2f (effect dormant)",
                market.isConditionSuppressed(GearInOreCondition.ORE_DEPOSITS),
                market.getConditions().size(),
                hazardSuppressedBeforeReapply, hazardSuppressedAfterReapply));
        market.unsuppressCondition(GearInOreCondition.ORE_DEPOSITS);
        market.reapplyConditions();
        LOG.info(String.format(
                "GearIn condition lifecycle unsuppress: suppressed=%s hazard=%.2f (effect restored)",
                market.isConditionSuppressed(GearInOreCondition.ORE_DEPOSITS),
                market.getHazard().getModifiedValue()));

        // --- 6. removeSpecificCondition(token)：只移除一个实例 (不对称性 B) ---
        market.removeSpecificCondition(token3);
        LOG.info(String.format(
                "GearIn condition lifecycle removeOne: conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 7. survey 门控：重算只对 surveyed 的条件 apply ---
        String token4 = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        MarketConditionAPI mc4 = market.getSpecificCondition(token4);
        float hazardAfterAdd4 = market.getHazard().getModifiedValue();
        mc4.setSurveyed(false);
        market.reapplyConditions();
        float hazardUnsurveyed = market.getHazard().getModifiedValue();
        mc4.setSurveyed(true);
        market.reapplyConditions();
        float hazardSurveyed = market.getHazard().getModifiedValue();
        market.removeSpecificCondition(token4);
        LOG.info(String.format(
                "GearIn condition lifecycle surveyGate: afterAdd=%.2f afterUnsurveyedReapply=%.2f afterSurveyedReapply=%.2f",
                hazardAfterAdd4, hazardUnsurveyed, hazardSurveyed));

        // --- 8. 原版对照 (只读)：找一颗条件市场上 reqSurvey 条件的初始状态 ---
        boolean found = false;
        for (MarketAPI candidate : sector.getEconomy().getMarketsCopy()) {
            if (!candidate.isPlanetConditionMarketOnly()) {
                continue;
            }
            for (MarketConditionAPI c : candidate.getConditions()) {
                if (c.requiresSurveying()) {
                    LOG.info(String.format(
                            "GearIn condition lifecycle vanillaSurvey: market=%s surveyLevel=%s condition=%s surveyed=%s",
                            candidate.getId(), String.valueOf(candidate.getSurveyLevel()),
                            c.getId(), c.isSurveyed()));
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            LOG.info("GearIn condition lifecycle vanillaSurvey: none found (all condition-only markets surveyed)");
        }

        LOG.info(String.format(
                "GearIn condition lifecycle final: conditions=%d hazard=%.2f accessibilityFlat=%.2f (baseline restored)",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));
        LOG.info("GearIn condition lifecycle done");
    }
}
```

:::
