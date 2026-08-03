# 船插生命周期：tooltip、S-mod 与监听器

本节把船插从「数据行 + 效果类」扩展为完整对象：`HullModEffect` 的全部生命周期回调（每个回调的调用时机与调用点）、tooltip 方法族（`desc` / `sModDesc` 的 `%s` 填充、分段与 S-mod 效果展示）、S-mod（稳定性改造）语义（状态存储、上限、增强内置、奖励经验），以及监听器接入（注册点、状态归属与必须遵守的放置规则）。本节示例 `GearInArmorTrainingHullMod` 把五条生命周期回调（四条日志证明）、双段 tooltip 文本、S-mod 分支和两个监听器接口收进一个船插；读完本节，您应该能回答「一个船插在游戏里到底被哪些时机调用、tooltip 文本从哪来、S-mod 怎么让效果变强、战斗监听器挂在哪」。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 背出 `HullModEffect` 的全部生命周期回调：
  - `init` / `applyEffectsBeforeShipCreation` / `applyEffectsAfterShipCreation` / `applyEffectsAfterShipAddedToCombatEngine`
  - `advanceInCombat` / `advanceInCampaign` / `applyEffectsToFighterSpawnedByShip`
  - 说出每个回调的调用时机与调用点（生涯 stats 重建、战斗舰船构造、加入战斗引擎、每帧、装配预览舰）
  - 解释为什么效果类**必须**是会话级单例、跨船状态**必须**放进每船一个的监听器实例；
- 说出 tooltip 的组装顺序：
  - 宽度 → 标题 → `getDescriptionParam(0..9)` 填充 `desc` 的 `%s` → `shouldAddDescriptionToTooltip` → `addPostDescriptionSection`
  - `hasSModEffectSection` / `addSModSection` → 状态行（d-mod / 内置 / S-mod / 不可安装原因）
  - 以及 `sModDesc` 的 `%s` 由 `getSModDescriptionParam(0..9)` 填充；
- 说出 S-mod（稳定性改造）的完整语义：用故事点把普通船插内置进船体（`getSMods()`）、增强内置船插（`getSModdedBuiltIns()`）、`isSMod(stats/ship)` 同时查两个集合、`sModDesc` 列为空或 `#` 开头时无 S-mod 效果
  - 默认上限 2 个（`maxPermanentHullmods` + `Stats.MAX_PERMANENT_HULLMODS_MOD` 动态 stat）、增强内置不占上限且奖励 100% 经验、RC8 普通内置奖励经验只按舰船大小分档；
- 说出船插注册监听器的正确做法：`applyEffectsAfterShipCreation` 里 `ship.addListener(...)`、每船一个实例、必须遵守的放置规则（引用监听器接口 javadoc 原文）、`hasListenerOfClass` 防重复注册；
- 从症状定位典型故障：效果完全不生效、S-mod 分支永不触发、装配界面没有 S-mod 效果段、串船、监听器静默失效、`%s` 错位、生涯与战斗数值不一致。

## 前置知识

本节建立在以下前置知识之上，需要您已经理解：

- **命名约定**（C01-S01）：MOD ID `gear_in`、数据 / 资源 key 前缀 `GearIn_`、包 `data.scripts.gearin.<domain>`、类 `GearIn<Subject>`；本节示例类名 `GearInArmorTrainingHullMod`。
- **船插数据行**（C02-S02）：`hull_mods.csv` 的 `id` / `script` / `desc` 列与加载链 `loadHullMods`（CSV spec 加载期）→ `HullModSpecAPI` → 效果类会话级单例、`isApplicableToShip` / `getUnapplicableReason`、内置船插（`builtInMods`）。
- **stats 修饰体系**（C07-S03）：`MutableStat` 修饰与 modifier ID 必须遵守的规则、`applyEffectsBeforeShipCreation` 的三个时机（生涯重建重放 / `Ship` 构造 / 加入引擎）—— 本节在其上补齐其余回调。
- **公开监听器系统**（C07-S05）：`com.fs.starfarer.api.combat.listeners` 三块注册表面与每类监听器的必须遵守的放置规则、`CombatListenerUtil` 派发、`hasListenerOfClass` 守卫 —— 本节把监听器放进船插生命周期，放置规则不重复展开。
- **战斗状态读取与时间语义**（C07-S02）：本节 `advanceInCombat` 的每帧语义、暂停语义沿用其结论。
- **编译 JAR 链路**（C06-S01）：本节示例编入同一个 `jars/GearIndustry.jar`。
- **`Global` 生命周期**（C06-S03）：本节不缓存引擎/舰船引用，与前置 stats 讲解同规。
- **测试战役**（C03-S01）：`GearIn_test` 战役，用户舰 `GearIn_artizan`（本节验证场景）。

## 文件与 API 链路

本节涉及的接口（除注明外都在 `com.fs.starfarer.api.combat`）：

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `HullModEffect` | 船插效果接口，全部生命周期回调的定义处 | 数据行 `script` 列 → `HullModSpecAPI.getEffect()`（用到时才创建的单例）→ 各调用点 |
| `BaseHullMod` | 空实现基类 | 建议继承；`isSMod` / `isBuiltIn` / `hasSModEffect` / `addSModSection` 等默认实现在这里 |
| `ShipVariantAPI` | S-mod 状态存储 | `getSMods()`、`getSModdedBuiltIns()`、`addPermaMod(modId, isSMod)` |
| `HullModSpecAPI` | 船插静态配置 | `getSModEffectFormat()`、`getSModDescription(hullSize)` |
| `MutableShipStatsAPI` | stats 入口 | `getArmorBonus()`、`getEffectiveArmorBonus()`—— 本节示例的修饰目标 |
| `ArmorGridAPI` | 装甲网格读取 | `getGrid()` / `getMaxArmorInCell()` / `getArmorRating()`；战斗命中结算与监听器示例消费它 |
| `com.fs.starfarer.api.combat.listeners.*` | 监听器接口 | `HullDamageAboutToBeTakenListener`、`AdvanceableListener` 本节示例实现；必须遵守的放置规则见 C07-S05 |
| `Misc` | S-mod 辅助 | `CAN_SMOD_BUILT_IN`、`getMaxPermanentMods`、`getBuildInBonusXP` |
| `Stats` | 动态 stat id | `MAX_PERMANENT_HULLMODS_MOD = "max_permanent_hullmods_mod"`（`com.fs.starfarer.api.impl.campaign.ids.Stats`）|

三条关键链路（sModDesc、tooltip、生命周期），全部带调用点：

```text
sModDesc 链路：
hull_mods.csv 的 sModDesc 列
  → loadHullMods（CSV spec 加载期）：optString("sModDesc", "")
  → HullModSpecAPI.setSModEffectFormat→ getSModEffectFormat()
  → BaseHullMod.hasSModEffect()（非空且不以 # 开头）
  → getSModDescription(hullSize)：String.format 填充 getSModDescriptionParam(0..9)
    → addSModEffectSection

tooltip 链路（标准 tooltip 的 createHullModTooltip）：
getTooltipWidth() → 标题 / 制造商
  → getDescriptionParam(0..9, hullSize, ship) 填充 desc 的 %s
  → shouldAddDescriptionToTooltip 决定是否显示 desc 段
  → addPostDescriptionSection
  → hasSModEffectSection → addSModSection
  → 状态行：d-mod / 内置 / 增强内置 / S-mod / 不可安装原因 / canBeAddedOrRemovedNow / addRequiredItemSection

生命周期调用点：
applyEffectsBeforeShipCreation：FleetMemberAPI.updateStats（生涯重建重放）+
  Ship 构造器（战斗部署）—— 前置单元已详述
applyEffectsAfterShipCreation：舰船对象创建 createShip（createStats=true）；
  装配界面预览舰同路径（舰船显示面板）
applyEffectsAfterShipAddedToCombatEngine：Ship.applyEffectsAfterShipAddedToCombatEngine
  （appliedAfterAddEffects 守卫）← 战斗引擎的 addObjectToEngine
advanceInCombat：Ship.advance 每帧遍历 variant.getAllMods()
advanceInCampaign：CampaignFleetAPI.advance 每成员；FleetDataAPI 同步时 amount=0
```

## 生命周期：HullModEffect 全部回调

::: info 为什么效果类必须是会话级单例

`HullModEffect` 接口 javadoc 开篇即必须遵守的规则：**效果类每个应用会话只实例化一次**（"the effect class is instantiated once per application session. Storing campaign data in data members of an implementing class is a bad idea (will likely cause memory leaks), use SectorAPI.getPersistentData() instead."）。

违反的可观察结果：两个船插实例或两艘船共享字段，计时器串船、计数互相覆盖；把跨存档数据放字段还会造成泄漏。因此：**跨船状态必须放在每船一个的监听器实例（或 `getDynamic()` 标记位，见前置单元），不得放在效果类字段**。

:::

| 回调 | 调用时机 | 调用点 | 要点 |
|---|---|---|---|
| `init(HullModSpecAPI spec)` | spec 就绪后、首次使用效果时 | `HullModSpecAPI.getEffect()` 用到时才实例化 | `BaseHullMod` 用它保存 spec；`isSMod` / `isBuiltIn` / `hasSModEffect` 都依赖 spec 非 null |
| `applyEffectsBeforeShipCreation(HullSize, MutableShipStatsAPI, String id)` | 生涯 stats 每次重建重放；战斗舰船构造 | `FleetMemberAPI.updateStats`；`Ship` 构造器 | 改 stats 的唯一正式入口（前置讲解必须遵守的规则）；`id` 参数就是本船插的 spec id，直接用作修饰 ID；S-mod 分支也在这里 |
| `applyEffectsAfterShipCreation(ShipAPI, String id)` | 战斗侧舰船实体创建后；装配界面预览舰也会创建 | 舰船对象创建流程的 `createShip`（`createStats=true` 时）；装配界面预览面板用 `createShip(variant, true, ...)` | **不承担 stats 修改**（API javadoc 原文："Effects applied here should NOT affect ship stats as this does not get called from the campaign."）；监听器注册的正式位置 |
| `applyEffectsAfterShipAddedToCombatEngine(ShipAPI, String id)` | 舰船第一次加入战斗引擎 | `Ship` 的 `appliedAfterAddEffects` 守卫 ← `CombatEngineAPI.addObjectToEngine` | **每艘船只回调一次**（API javadoc 原文："Only called once. Not called again if the ship is removed and then added back to the engine."）；做一次性战斗初始化（本节示例用它打日志证明）|
| `advanceInCombat(ShipAPI, float amount)` | 战斗中每帧 | `Ship.advance` 遍历 `getVariant().getAllMods()` | **暂停时不调用**（API javadoc 原文："Not called while paused."）；无状态写入合法（前置讲解的每帧幂等做法），跨船状态不行 |
| `advanceInCampaign(FleetMemberAPI, float amount)` | 生涯每帧；舰队数据重同步时 `amount=0` | `CampaignFleetAPI.advance` 每成员；`FleetDataAPI` 同步时 | **暂停时不调用**；API javadoc 原文："Not called while paused. But, called when the fleet data needs to be re-synced, with amount=0 (such as if, say, a fleet member is moved around in the fleet screen.)" |
| `applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id)` | 航母舰船生成战机后 | 战机出生链 | 效果向母舰生成的战机传播；本节不展开，示例不实现 |

::: warning 不要依赖「只在战斗里调用一次」来初始化

用户在装配界面改装配时，显示实体经舰船对象创建流程的 `createShip` 重建，`applyEffectsAfterShipCreation` 会在预览舰上运行 —— 注册监听器、打日志都会发生一次。真正的「每船一次」在 `applyEffectsAfterShipAddedToCombatEngine`。

:::

## Tooltip 方法族

船插 tooltip 由 tooltip 渲染流程的 `createHullModTooltip` 组装，消费的方法族如下：

| 方法 | 职责 | 调用点 |
|---|---|---|
| `getTooltipWidth()` | tooltip 宽度 | `BaseHullMod` 默认 369 |
| `getDescriptionParam(int index, HullSize, ShipAPI ship)` | 填充 `desc` 的 `%s` | 依序取 index 0..9；三参版是实际被调版本，`BaseHullMod` 默认转发给两参版 |
| `shouldAddDescriptionToTooltip(hullSize, ship, isForModSpec)` | 是否显示 `desc` 段 | ship 可为 null（modspec 场景 hullSize 固定 `CAPITAL_SHIP`，API javadoc 原文）|
| `addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)` | `desc` 段之后追加自定义段落 | 原版 `HighScatterAmp` 用它写 "Interaction" 段落 |
| `hasSModEffectSection(hullSize, ship, isForModSpec)` | 是否显示 S-mod 段 | `BaseHullMod` 默认：无 `sModEffect` 或（内置且非增强）时为 false |
| `addSModSection(tooltip, hullSize, ship, width, isForModSpec, isForBuildInList)` | S-mod 标题 + 效果段 / 说明 | 内部调 `addSModEffectSection`，效果文本来自 `spec.getSModDescription(hullSize)` |
| `getSModDescriptionParam(int index, HullSize)` / 三参版 | 填充 `sModDesc` 的 `%s` | `HullModSpecAPI.getSModDescription` 用 `String.format` 依序取 index 0..9 |

由此得到必须遵守的规则：

1. **`desc` 里每个 `%s` 都必须能在 `getDescriptionParam(0..9)` 拿到非 null 返回值**，index 从 0 开始按出现顺序对应。违反的可观察结果：tooltip 中 `%s` 位置空白或显示 `%s` 字面量（对照本节故障分析 3）
   - 原版写法是参数带单位字符（如 `"" + (int)ARMOR_BONUS_PERCENT + "%"`），`desc` 本身不写裸 `%`——`addPara` 内部走 `String.format`，裸 `%` 会解析失败。
2. **`sModDesc` 列与 `getSModDescriptionParam` 的 `%s` 约定与 `desc` 相同**（index 0..9）。`sModDesc` 为空 → 无 S-mod 效果段（见下节 `hasSModEffect`）。
3. 需要完全自绘 tooltip 时用 `shouldAddDescriptionToTooltip` 返回 `false` + `addPostDescriptionSection` 全权接管（原版 `HighScatterAmp` 的做法），但 `%s` 约定仍在 `getDescriptionParam` 里保留。

## S-mod（稳定性改造）

### 概念与状态存储

S-mod 是用故事点把船插 "内置" 进船体的改造：不消耗装配点、不可移除，并激活 `sModDesc` 描述的效果。它由装配界面操作产生（船插选择对话框的确认逻辑对每个新内置船插调用 `variant.addPermaMod(id, true)`），状态存在装配上：

- `ShipVariantAPI.getSMods()`：普通 S-mod 的 id 集合（`LinkedHashSet`）；
- `ShipVariantAPI.getSModdedBuiltIns()`：被 "增强" 的原生内置船插 id 集合（RC8 `Misc.CAN_SMOD_BUILT_IN = true`，内置船插可以增强）；
- `BaseHullMod.isSMod(stats)` / `isSMod(ship)` 同时查这两个集合 —— 增强内置也算 S-mod。

效果代码的 S-mod 分支写在 `applyEffectsBeforeShipCreation` 里（stats 侧唯一入口），原版做法见原版船插 `ArmoredWeapons`（`Starsector/starsector-core/data/hullmods/`）：

```java
boolean sMod = isSMod(stats);
if (sMod) {
    stats.getBallisticRoFMult().modifyMult(id, 1f + SMOD_BONUS * 0.01f);
    stats.getEnergyRoFMult().modifyMult(id, 1f + SMOD_BONUS * 0.01f);
}
```

同一 `id` 同类型只保留一条条目，因此基础值与 S-mod 值用同一个 id 覆盖（同 ID 覆盖语义，见前置讲解），不需要分支 `unmodify`。

### sModDesc 与 hasSModEffect

::: caution `sModDesc` 不得以 `#` 开头写正常文本

以 `#` 开头是「关闭 S-mod 效果」的开关（原版注释用法），`BaseHullMod.hasSModEffect()` 据此判定无 S-mod 效果。违反后果：装配界面不再显示 S-mod 效果段。

:::

- `hull_mods.csv` 的 `sModDesc` 列（20 列表头，位于 `short` 与 `sprite` 之间）→ `HullModSpecAPI` 的 `sModEffectFormat`。
- `BaseHullMod.hasSModEffect()`：`sModEffectFormat` 非空、非全空白、**不以 `#` 开头** → 有 S-mod 效果。
- `isSModEffectAPenalty()` 返回 true 时 S-mod 段用 "penalty" 样式（`BaseHullMod.addSModSection`）—— 高成本船插的 S-mod 可能是惩罚。
- `hasSModEffectSection`：结合内置 / 增强内置 / 惩罚语义决定是否显示；内置且未增强时显示 "enhanced built-in" 说明文本
  - （"This effect only applies if this built-in hullmod is enhanced using a %s. Doing this does not count against the maximum number of s-mods a ship can have."）
- `sModDesc` 为空时 tooltip 显示 "Aside from removing its ordnance point cost, this hullmod gains no extra effect from being built into the hull using a story point."—— 没有效果不等于不能内置。

### 上限、故事点与奖励经验（RC8 基线）

- **默认上限 2 个**：`settings.json` 的 `maxPermanentHullmods: 2` → `Misc.MAX_PERMA_MODS`
  - `Misc.getMaxPermanentMods(ship)` = `round(stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).computeEffective(2))`
  - 船插 / 技能可以用 `stats.getDynamic().getMod("max_permanent_hullmods_mod").modifyFlat(...)` 提升上限 —— S-mod 上限不是写死的数字，是动态 stat。
- 装配界面的计数：普通 S-mod 计入上限，**增强内置不占上限**（装配界面的计数提示原文 "Hull-standard built-in mods do not count against the limit."；计数代码把增强内置单独以 `*` 显示）。
- 故事点消耗：每个普通 S-mod / 每次增强内置各消耗 1 点（"Building mods into a hull uses a story point for each hullmod."）。
- 奖励经验（RC8 基线）：增强内置固定 100%（原文 "enhancing it always grants %s bonus experience." 参数 "100%"）。普通内置的奖励经验按舰船大小分档：
  - `settings.json` 的 `bonusXP` 段 `permModFrigate: 0.75` / `permModDestroyer: 0.5` / `permModCruiser: 0.25` / `permModCapital: 0`，由 `Misc.getBuildInBonusXP(mod, size)` 返回
  - **RC8 中 "成本低于 X OP 才有奖励" 的阈值逻辑已整段注释**（`Misc` 中 `permModNoBonusXPOPThreshold` / `permModMaxBonusXP` 相关逻辑已被注释）—— 奖励与 OP 成本无关；装配界面的 "成本低于 %s OP" 说明文本是 RC8 遗留文案，与行为不符。

## 监听器接入（船插视角）

前置单元已给出公开监听器系统的三块注册表面（引擎 `getListenerManager()` / 舰船 `ship.addListener(...)` / 统计 `stats.addListener(...)`）与每类监听器的必须遵守的放置规则。本节补充船插侧的接入做法：

1. **注册时机必须用 `applyEffectsAfterShipCreation`**：此时 `ship` 实体完整可用，且每个实体恰好回调一次（含装配界面预览舰）
   - 效果类是单例，不能在里面直接注册 "给自己用" 的舰船级监听器 —— 监听器必须挂在传入的 `ship` 上
   - 原版做法：`HighScatterAmp` 在 `applyEffectsAfterShipCreation` 里 `ship.addListener(new HighScatterAmpDamageDealtMod(ship));`。
2. **每船一个监听器实例是唯一的合法跨船状态容器**：监听器在 `applyEffectsAfterShipCreation` 里 `new` 出来，持有自己的计时器 / 计数器，随舰船实体一起销毁 —— 这是 "效果类单例" 约束的正面对策（本节示例的 `ArmorTrainingListener` 就是这么做的）。
3. 必须遵守的放置规则摘要（完整表见前置讲解）：
   - `HullDamageAboutToBeTakenListener` 挂承受伤害的舰船（或引擎）；`DamageTakenModifier` 挂承受舰；`DamageDealtModifier` 挂造成舰
   - `AdvanceableListener` **只能挂舰船**（javadoc 原文 "Only supported when added to a ShipAPI, not to CombatEngineAPI."）；OP 成本类修饰只能挂 `MutableShipStatsAPI`
   - 违反的共性可观察结果：**静默不生效、无任何报错**。

::: tip 防重复注册

同一实体重复创建（如多次 `createShip`）会让同一类监听器叠加，`AdvanceableListener` 叠加后每帧被派发多次（tick 日志翻倍是典型症状）。**建议**在注册前用 `ship.getListenerManager().hasListenerOfClass(...)` 守卫（成熟实例的常见做法）。

:::

## 完整最小例：装甲训练

示例所有权：`GearInArmorTrainingHullMod`。概念：船员装甲训练 —— 提高有效护甲（`getArmorBonus` + `getEffectiveArmorBonus`）；装甲平均比例高于 50% 时由监听器抵消舰体结构伤害；S-mod 提高合计加成并在战斗中按装甲比例每帧动态加成。

示例覆盖五条生命周期回调（四条日志证明 + 两个 stats/行为点）、`desc` / `sModDesc` 双段 tooltip、S-mod 分支、两个监听器接口。

### 第一步：注册数据行

在 MOD 的 `data/hullmods/hull_mods.csv` 写入（文件在 MOD 中不存在就新建，只包含本 MOD 的行；0.98a-RC8 表头 20 列）：

```csv {1}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
装甲训练,GearIn_armor_training,0,,,"defensive, merc, standard",Defenses,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInArmorTrainingHullMod,"提高舰船的有效护甲评级与命中结算护甲 %s。装甲平均比例高于 %s 时，抵消即将发生的舰体结构伤害。","提高有效护甲；高装甲时抵消结构伤害。",S-mod 将有效护甲加成提高至 %s，战斗中按装甲比例至多再获得 %s。,
```

要点（字段的使用方速查表见前置讲解）：

- `script` 列 = 注册入口：`data.scripts.gearin.hullmods.GearInArmorTrainingHullMod` 与下文类的包名 + 类名必须逐字一致。
- `desc` 有 2 个 `%s`（对应 `getDescriptionParam(0)` = `10%`、`getDescriptionParam(1)` = `50%`）；`sModDesc` 有 2 个 `%s`（对应 `getSModDescriptionParam(0)` = `25%`、`getSModDescriptionParam(1)` = `15%`）。
- `sprite` 留空 → 使用默认 `graphics/hullmods/generic_mod.png`（本单元无 PNG 资产）。
- 四个 `cost_*` 列都填数字（`4,8,12,20`），`unlocked=TRUE` 保证装配界面可直接安装。

### 第二步：效果类

文件：MOD 源码目录 `src/data/scripts/gearin/hullmods/` 下的 `GearInArmorTrainingHullMod`（本节代码块与 fixture 文件逐字一致）：

fixture 位于 `tutorial-fixtures/units/C07-S07/src/main/java/data/scripts/gearin/hullmods/`。

```java
package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：HullMod 生命周期、tooltip、S-mod 与监听器接入。
 *
 * 主题：装甲训练。效果类扩展 BaseHullMod（HullModEffect 的空实现基类）；
 * 效果类是会话级单例（HullModEffect javadoc 原文），本类不持有任何跨船字段 ——
 * 每艘船自己的状态（周期计时、抵消计数）放在 applyEffectsAfterShipCreation
 * 里注册的监听器实例中（每艘船一个实例，随舰船实体一起销毁）。
 *
 * 生命周期覆盖：
 * - applyEffectsBeforeShipCreation：生涯与战斗都会调用的 stats 入口（C07-S03）；
 *   这里同时演示 S-mod 分支：isSMod(stats) 为 true 时把同一 id 的修饰覆盖为更大值；
 * - applyEffectsAfterShipCreation：战斗侧舰船实体创建后调用（含装配界面预览舰），
 *   注册监听器的正式位置；不承担 stats 修改（API javadoc 原文）；
 * - applyEffectsAfterShipAddedToCombatEngine：每艘船加入战斗引擎只回调一次
 *   （API javadoc 原文 "Only called once."）；
 * - advanceInCombat：战斗每帧调用，暂停时不调用（API javadoc 原文）；
 *   演示每帧幂等写 StatBonus（同 ID 覆盖，不累积，C07-S03 规则 6）；
 * - advanceInCampaign：生涯每帧调用；舰队数据重同步时以 amount=0 调用
 *   （API javadoc 原文），这里只在该时机输出一行作为生命周期证明。
 *
 * tooltip：desc 的 %s 由 getDescriptionParam(0..n) 填充，sModDesc 的 %s
 * 由 getSModDescriptionParam(0..n) 填充（BaseHullMod / 标准 tooltip）。
 */
public class GearInArmorTrainingHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInArmorTrainingHullMod.class);

    /** 基础效果：有效护甲 +10%（getDescriptionParam(0) 输出同值）。 */
    public static final float ARMOR_BONUS_PERCENT = 10f;
    /** S-mod 额外加成：applyEffects 合计 25%、动态加成上限 15%（getSModDescriptionParam 0/1 输出）。 */
    public static final float SMOD_ADDITIONAL_PERCENT = 15f;
    /** 装甲平均比例高于该值时，监听器抵消即将发生的舰体结构伤害。 */
    public static final float NEGATION_ARMOR_FRACTION = 0.5f;
    /** 动态修饰 ID：与 hullmod id 分开，避免与 applyEffects 的条目互相覆盖。 */
    private static final String DYNAMIC_ID = "GearIn_armor_training_dynamic";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // S-mod 分支：同一 id 同类型只保留一条条目，S-mod 时直接覆盖为合计值（C07-S03 覆盖语义）。
        float bonus = ARMOR_BONUS_PERCENT + (isSMod(stats) ? SMOD_ADDITIONAL_PERCENT : 0f);
        stats.getArmorBonus().modifyPercent(id, bonus);           // 面板/生涯可见的护甲评级
        stats.getEffectiveArmorBonus().modifyPercent(id, bonus);  // 命中结算时的有效护甲
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 每艘船实体一个监听器实例：跨船状态只能放在这里，不能放效果类字段。
        // 装配界面预览舰也会走到这里（舰船显示面板用 createShip(variant, true, ...) 重建显示实体）。
        ship.addListener(new ArmorTrainingListener(ship));
        LOG.info("GearIn armor training: listener attached to " + ship.getName());
    }

    @Override
    public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
        // 一次性回调：舰船移除后再加入引擎也不会再次调用（API javadoc 原文）。
        LOG.info("GearIn armor training combat-ready: " + ship.getName() + " (once per engine add)");
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 每帧调用、暂停时不调用。效果类不保存跨船字段，这里只做无状态写入：
        // S-mod 时每帧把"当前装甲平均比例"写成动态修饰。命中结算时装甲网格
        // 以 getEffectiveArmorBonus().computeEffective(...) 读取，因此下一击即生效；同 ID 每帧重复调用是幂等覆盖，不会累积。
        if (!isSMod(ship) || !ship.isAlive() || ship.isHulk()) return;
        float frac = armorFraction(ship.getArmorGrid());
        ship.getMutableStats().getEffectiveArmorBonus()
                .modifyPercent(DYNAMIC_ID, SMOD_ADDITIONAL_PERCENT * frac);
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        // 生涯每帧调用；舰队数据重同步（如舰队界面移动成员）时以 amount=0 调用（API javadoc 原文）。
        // 只在同步时机输出一行，作为"该回调确实被调用"的证明，避免每帧刷屏。
        if (amount <= 0f) {
            LOG.info("GearIn armor training campaign sync: " + member.getShipName()
                    + " variant=" + member.getVariant().getHullVariantId());
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) ARMOR_BONUS_PERCENT + "%";
        if (index == 1) return "" + Math.round(NEGATION_ARMOR_FRACTION * 100f) + "%";
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) (ARMOR_BONUS_PERCENT + SMOD_ADDITIONAL_PERCENT) + "%";
        if (index == 1) return "" + (int) SMOD_ADDITIONAL_PERCENT + "%";
        return null;
    }

    /** 装甲网格平均比例（0..1）：网格总值 /（格子数 × 单格最大值）。 */
    private static float armorFraction(ArmorGridAPI grid) {
        float[][] cells = grid.getGrid();
        float maxCell = grid.getMaxArmorInCell();
        if (cells.length == 0 || maxCell <= 0f) return 0f;
        float total = 0f;
        int count = 0;
        for (float[] row : cells) {
            for (float value : row) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0f : total / (count * maxCell);
    }

    /**
     * 每艘船一个实例（applyEffectsAfterShipCreation 里注册），持有本船的状态。
     * 同时实现两个监听器接口：
     * - HullDamageAboutToBeTakenListener：舰体结构伤害结算前回调，返回 true 即抵消
     *   （API javadoc 原文："if true is returned, the hull damage to be taken is negated."）；
     * - AdvanceableListener：舰船级每帧推进（Ship.advance 仅对 amount>0 派发，即暂停时不调用），
     *   这里用它做 5 秒一次的状态输出 —— 周期计时器是单船状态，合法放在本实例。
     */
    public static class ArmorTrainingListener implements HullDamageAboutToBeTakenListener, AdvanceableListener {

        private final ShipAPI ship;
        private final IntervalUtil reportInterval = new IntervalUtil(5f, 5f);
        private long negations;

        public ArmorTrainingListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float frac = armorFraction(ship.getArmorGrid());
            if (frac <= NEGATION_ARMOR_FRACTION) return false;
            negations++;
            LOG.info("GearIn armor training: hull damage negated amount=" + damageAmount
                    + " armorFraction=" + String.format("%.2f", frac)
                    + " negations=" + negations);
            return true; // 抵消本次舰体结构伤害
        }

        @Override
        public void advance(float amount) {
            reportInterval.advance(amount);
            if (!reportInterval.intervalElapsed()) return;
            float frac = armorFraction(ship.getArmorGrid());
            LOG.info("GearIn armor training tick: " + ship.getName()
                    + " rating=" + ship.getArmorGrid().getArmorRating()
                    + " armorFraction=" + String.format("%.2f", frac)
                    + " negations=" + negations);
        }
    }
}
```

### 逐段说明

- `extends BaseHullMod`：`HullModEffect` 全部方法的空实现基类（前置讲解已介绍）；本类不持有任何跨船字段（会话级单例约束）。
- `applyEffectsBeforeShipCreation`：`isSMod(stats)` 读取装配上的 S-mod 状态（普通 S-mod 与增强内置都算）；同 `id` 同类型只保留一条条目，S-mod 时把 10% 覆盖为 25%（同 ID 覆盖语义）
  - `getArmorBonus()` 决定面板与网格创建时的护甲评级（`armorValue = getArmorBonus().computeEffective(...)`）；`getEffectiveArmorBonus()` 决定每次命中结算的有效护甲 —— 两个 stat 都写，行为才完整。
- `applyEffectsAfterShipCreation`：注册每船一个的 `ArmorTrainingListener` 实例并打日志。日志会在装配界面预览舰上先出现一次（预览舰也走舰船对象创建流程的 `createShip`）。
- `applyEffectsAfterShipAddedToCombatEngine`：战斗开始时打一行一次性日志 ——"每船只回调一次" 的证明。
- `advanceInCombat`：S-mod 时每帧把 "当前装甲平均比例" 写成 `DYNAMIC_ID` 修饰（幂等覆盖）；命中结算逐击读取，因此下一击即生效
  - `DYNAMIC_ID` 与 hullmod id 分开，**避免与 `applyEffectsBeforeShipCreation` 的条目在同一张表里互相覆盖**（故障分析 6 的反面示范）。
- `advanceInCampaign`：只在 `amount <= 0f`（舰队数据重同步）时打一行 ——"该回调确实被调用" 且 "同步时 amount=0" 的证明，同时避免每帧刷屏。
- `getDescriptionParam` / `getSModDescriptionParam`：按 index 输出参数字符串；`null` 的 index 不会被 tooltip 使用。
  - `ArmorTrainingListener`：同时实现 `HullDamageAboutToBeTakenListener`（返回 `true` 抵消结构伤害，`notifyAboutToTakeHullDamage` 语义来自 javadoc 原文）与 `AdvanceableListener`（每帧推进、暂停不派发）
  - `IntervalUtil(5f, 5f)` 与 `negations` 计数是单船状态，合法放在这里。

### 注册与构建

注册入口就是数据行的 `script` 列 —— 船插不需要任何额外代码注册（对比前置单元插件的战斗内 `addPlugin` 注册方式）。

使用方的完整流程：

- `hull_mods.csv` 行 → `HullModSpecAPI` → `getEffect()` 单例
- `FleetMemberAPI.updateStats` / `Ship` 构造器（stats）
- 舰船对象创建流程的 `createShip`（监听器）
- `Ship.advance`（每帧）
- `createHullModTooltip`（tooltip 渲染流程）

构建命令（在 `Starsector/mods/GearIndustry/` 执行，沿用前置编译链路讲解）：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/hullmods/GearInArmorTrainingHullMod.class`（可用 `jar tf` 核对）。

### 验证方案（基于本例）

验证对象为本例的 `hull_mods.csv` 行与 `GearInArmorTrainingHullMod` 类，验证入口为生涯装配界面与 `GearIn_test` 测试战役。

- **前置数据**：MOD 布局与编译链路（C06-S01）、本节 `hull_mods.csv` 行（含 `sModDesc`）、示例类 `GearInArmorTrainingHullMod` 已编入 `jars/GearIndustry.jar`、测试战役 `GearIn_test`（C03-S01，用户舰 `GearIn_artizan`）。
- **操作**：
  1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC07s07Java --no-daemon`，确认 `BUILD SUCCESSFUL`（退出码 0）。
  2. 游戏构建：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/hullmods/GearInArmorTrainingHullMod.class`。
  3. 注册与 tooltip：启动到生涯，装配界面确认「装甲训练」出现在列表；悬停 tooltip 核对 desc 两处高亮（10% / 50%）与 "S-mod bonus" 段落（25% / 15%）； `starsector.log` 有 `Loading hullmod [装甲训练]` 且无 `ERROR`。
  4. 数值：给 `GearIn_artizan` 安装，确认装甲评级 500 → 550；用 1 个故事点内置，确认 625 且 tooltip 显示 S-mod 段。
  5. 生命周期：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 `listener attached`（部署时）、`combat-ready ... (once per engine add)`（仅一次）、每 5 秒 `tick` 行；按空格暂停约 10 秒，暂停期间无 tick，恢复后继续。
  6. 抵消：让敌人集中攻击同一装甲点，出现结构伤害判定时核对 `hull damage negated` 行与舰体结构不掉血；打到平均装甲低于 50% 后抵消停止。
  7. S-mod 动态加成：S-mod 状态下对比满甲与半甲时的掉甲速度；把 `DYNAMIC_ID` 换成 `id` 复现覆盖故障（数值乱跳 / 面板与战斗不一致），改回后恢复。
  8. 生涯同步：舰队界面移动一次成员，核对 `campaign sync` 行。
  9. 上限：对同一驱逐舰尝试故事点内置第 3 个普通船插，确认被拒绝（计数 `2 / 2`）。
- **通过信号**：操作 1–9 全部符合预期；没有 `gear_in` 引起的 `ERROR`。
- **清理**：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures/build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC07s07Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/hullmods/GearInArmorTrainingHullMod.class`。
3. **注册证明**：启动时 `starsector.log` 出现 `Loading hullmod [装甲训练] (source: [.../hull_mods.csv])`（与前置数据行讲解同款），无 `ERROR`。
4. **tooltip**：装配界面把 "装甲训练" 装到一艘船（或直接在船插列表悬停）：`desc` 显示 `提高舰船的有效护甲评级与命中结算护甲 10%。装甲平均比例高于 50% 时，抵消即将发生的舰体结构伤害。`（`10%` 与 `50%` 高亮）
   - `sModDesc` 非空 → tooltip 出现 "S-mod bonus" 段落（黄色标题），文本为 `S-mod 将有效护甲加成提高至 25%，战斗中按装甲比例至多再获得 15%。`。
5. **数值**：`GearIn_artizan` 的装甲评级 500（前置单元 ship_data.csv 中的定义）→ 装配界面 / 生涯面板显示 550（+10%）；故事点内置（S-mod）后显示 625（+25%）。
6. **生命周期日志**（进入 `GearIn_test` 战役一场战斗，用户舰 `GearIn_artizan`）：
   - `GearIn armor training: listener attached to 工匠`（部署时；若用户舰是自动生成的预览实体，装配界面阶段可能已出现过一次同款行）；
   - `GearIn armor training combat-ready: 工匠 (once per engine add)`—— 只出现一次，即使舰船被打掉重新部署也不会再次出现；
   - 之后每 5 秒一条 `GearIn armor training tick: 工匠 rating=550.0 armorFraction=1.00 negations=0`；按空格暂停，暂停期间没有 tick（`advanceInCombat` / `AdvanceableListener` 都暂停不派发）。
7. **S-mod 上限**：对一艘驱逐舰（如 `GearIn_artizan`）故事点内置第 3 个普通船插时被拒绝，界面显示 `2 / 2`（`maxPermanentHullmods: 2`）。
8. **抵消行为**：让敌人持续攻击用户舰同一装甲点，直到该点护甲剥光、开始出结构伤害：若此时全船装甲平均比例仍高于 50%，日志出现 `GearIn armor training: hull damage negated amount=... armorFraction=0.7x negations=N`，舰体结构值不下降；继续攻击到平均比例低于 50% 后，抵消停止、结构正常掉血。
9. **S-mod 动态加成**：S-mod 状态下，装甲平均比例随战斗下降时，命中结算的有效护甲逐击降低（动态修饰 `DYNAMIC_ID` 每帧重写）；对比未装 / 已装 S-mod 在同一受击点的掉甲速度。
10. **生涯同步日志**：在舰队界面移动一次成员（或修改装配后关闭舰队界面），日志出现 `GearIn armor training campaign sync: 工匠 variant=...`（`advanceInCampaign` 以 `amount=0` 调用）。

静态自查：`GearInArmorTrainingHullMod` 与 fixture 逐字一致（包括 import 顺序与注释）。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key：

- 包 `data.scripts.gearin.hullmods`
- 类 `GearInArmorTrainingHullMod`
- 修饰 ID `GearIn_armor_training_dynamic`
- 数据 ID `GearIn_armor_training`

## 实例驱动故障分析

每条给出触发条件、可观察结果与修正。

1. **效果完全不生效（装甲评级没有变化）。** 触发：CSV 行未写入、`id` 跨文件重复（静默跳过）、`script` 类名与类不匹配、`applyEffectsBeforeShipCreation` 没写进 stats。可观察：装配界面找不到船插、或找到但数值不变；多数情况日志无报错。
   - 修正：按前置讲解的 "数据行 → 类名 → 回调位置" 四段排查法复核
   - 本节示例的两个 stats 都要写（`getArmorBonus` 管显示、`getEffectiveArmorBonus` 管结算，只写一个会出现 "面板有了但战斗没变" 或反之）。
2. **S-mod 分支永不触发（内置后加成不变）。** 触发：分支写在 `advanceInCombat` 或 `applyEffectsAfterShipCreation`（生涯 stats 路径没有它们）；或 `isSMod(stats)` 传入的 `stats.getVariant()` 为 null（API 外的构造路径）
   - 可观察：装配界面显示 `2 / 2` 计数、故事点正常扣、`sModDesc` 文本正常显示，但生涯面板数值与普通安装时相同
   - 修正：S-mod 分支必须写在 `applyEffectsBeforeShipCreation`，且用回调传入的 `stats`（本节示例的写法）。
3. **装配界面没有 S-mod 效果段。** 触发：`sModDesc` 列为空、或内容以 `#` 开头、或 `%s` 数量与 `getSModDescriptionParam` 返回值不匹配
   - 可观察：tooltip 只显示 "Aside from removing its ordnance point cost, this hullmod gains no extra effect..." 或 `%s` 位置空白（`hasSModEffect()` 为 false）
   - 修正：填 `sModDesc`、不以 `#` 开头、`%s` 一一对应（index 0..9）。
4. **跨船状态串船（计数 / 计时互相污染）。** 触发：把 `negations` 计数、`IntervalUtil`、或任何 per-ship 字段写进 `GearInArmorTrainingHullMod`（效果类单例）。可观察：两艘装有该船插的船共享计数；一艘船触发抵消后另一艘的日志计数也跟着涨；同一字段在多艘船之间漂移。
   - 修正：状态放进 `applyEffectsAfterShipCreation` 里 `new` 出来的监听器实例（每船一个）；效果类只保留常量与无状态方法（本节示例的结构）。
5. **监听器静默不生效（抵消从未触发）。** 触发：把 `HullDamageAboutToBeTakenListener` 挂到了 `engine.getListenerManager()` 之外 —— 例如挂到造成伤害的船上、或注册后从未 `ship.addListener(...)`（只 `new` 了实例）。可观察：没有任何报错、日志永远没有 negated 行。
   - 修正：按前置讲解的必须遵守的放置规则核对层级（伤害族挂承受舰 / 引擎）；在 `applyEffectsAfterShipCreation` 里 `ship.addListener(...)`（本节示例的写法）。
6. **动态修饰与静态修饰互相覆盖（数值乱跳）。** 触发：`advanceInCombat` 用与 `applyEffectsBeforeShipCreation` 相同的修饰 ID（都写 `id`）。可观察：每帧覆盖后命中结算只剩一个来源的值；或生涯重建重放时动态值被静态值覆盖，战斗手感与面板不一致。
   - 修正：动态来源用独立稳定 ID（本节 `DYNAMIC_ID`）；同 ID 只用于 "同值覆盖" 语义（前置讲解的覆盖规则）。
7. **tick 日志翻倍（同一船每帧被派发多次）。** 触发：`applyEffectsAfterShipCreation` 对同一实体重复注册监听器（多次 `createShip` 同一 variant 场景、或注册代码写进了会被重复调用的路径）。可观察：`GearIn armor training tick:` 每 5 秒出现多行。
   - 修正：注册前用 `ship.getListenerManager().hasListenerOfClass(...)` 守卫，或保证注册代码只执行一次（前置讲解的注册纪律）。
8. **生涯需要但效果只写在战斗回调里。** 触发：把装甲加成写进 `advanceInCombat` 或 `applyEffectsAfterShipCreation` 而不写 `applyEffectsBeforeShipCreation`。可观察：战斗内数值正确、生涯面板（舰队面板 / 战术地图）没有加成 —— 两个环境两套数值。
   - 修正：stats 修饰一律放 `applyEffectsBeforeShipCreation`（前置讲解必须遵守的规则）；战斗回调只做战斗内的事。

## 总结

本节把船插讲成了一个完整的生命周期对象：七条回调各有明确的调用时机与调用点，stats 修改只在 `applyEffectsBeforeShipCreation`，跨船状态只放进每船一个的监听器实例。

tooltip 由方法族按固定顺序组装，`desc` / `sModDesc` 的 `%s` 必须与参数方法一一对应。

S-mod 是写在装配上的状态（`getSMods()` 与 `getSModdedBuiltIns()` 两个集合，`isSMod` 同时查两者），`sModDesc` 非空且不以 `#` 开头才有效果段，上限默认 2 且可经动态 stat 提升。

监听器在 `applyEffectsAfterShipCreation` 注册、随舰船实体销毁，必须遵守的放置规则决定它是静默生效还是静默失效。

就逻辑上的后续而言，这套「回调时机 + 状态归属」的框架并不限于船插：武器、战术系统与战斗插件的每帧行为服从同一套实例归属与回调纪律，监听器事件模型在伤害、命中与击毁的结算链上还会继续扩展 —— 读懂本节的生命周期调用点与放置规则，就拿到了阅读整个战斗侧行为链的钥匙。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插效果接口，七条生命周期回调的定义处（会话级单例、暂停与 `amount=0` 语义的 javadoc 原文）。
- `com.fs.starfarer.api.combat.BaseHullMod`：空实现基类，`isSMod` / `hasSModEffect` / tooltip 方法族的默认实现。
- `com.fs.starfarer.api.combat.ShipVariantAPI`：S-mod 状态存储，`getSMods()` / `getSModdedBuiltIns()` / `addPermaMod(modId, isSMod)`。
- `com.fs.starfarer.api.loading.HullModSpecAPI`：船插静态配置，`getSModDescription(hullSize)` / `getSModEffectFormat()`。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：stats 入口，`getArmorBonus()` / `getEffectiveArmorBonus()` / `getDynamic()`。
- `com.fs.starfarer.api.combat.ArmorGridAPI`：装甲网格读取，`getGrid()` / `getMaxArmorInCell()` / `getArmorRating()`。
- `com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener`：舰体结构伤害前回调，返回 `true` 即抵消伤害。
- `com.fs.starfarer.api.combat.listeners.AdvanceableListener`：舰船级每帧推进，仅支持挂载到 `ShipAPI`。
- `com.fs.starfarer.api.combat.listeners.CombatListenerUtil`：伤害族监听器的聚合派发。
- `com.fs.starfarer.api.util.Misc`：S-mod 辅助，`CAN_SMOD_BUILT_IN` / `getMaxPermanentMods` / `getBuildInBonusXP`。
- `com.fs.starfarer.api.impl.campaign.ids.Stats`：`MAX_PERMANENT_HULLMODS_MOD = "max_permanent_hullmods_mod"`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：生涯侧回调参数，`getShipName()` / `getVariant()`。
- `data/hullmods/hull_mods.csv`：`script` 列注册入口与 `desc` / `sModDesc` 列（20 列表头）。
- `data/config/settings.json`：`maxPermanentHullmods` 与 `bonusXP` 分档。
- `data/hullmods/`：原版效果类参考（`ArmoredWeapons` 的 S-mod 分支做法）。
- `com.fs.starfarer.api.impl.hullmods.HighScatterAmp`：原版实现类，监听器注册与自绘 tooltip 做法。
- `src/data/scripts/gearin/hullmods/`：本节示例类在 MOD 侧的源码位置。
- `tutorial-fixtures/units/C07-S07/src/main/java/data/scripts/gearin/hullmods/`：本节示例类（含嵌套监听器 `ArmorTrainingListener`）的 fixture 位置。
