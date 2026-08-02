# 产业、供需与升级

本页把产业（`Industry`）讲透：产业如何注册（`industries.csv`）、`Industry` / `BaseIndustry` 生命周期（`init` / `apply` / `unapply` / `reapply` / `advance`）、供需如何写入并被经济循环使用，以及建设与升级链（`startBuilding` / `startUpgrading` / `finishBuildingOrUpgrading`）、AI core 的安装与修饰、disruption 的语义与触发方。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `industries.csv` 的 15 个列字段、`id` 的注册语义，以及从 CSV 行到运行时 `Industry` 实例的注册链（CSV spec 加载期 → `IndustrySpecAPI.getNewPluginInstance` → `init` → `apply`）；
- 说出 `Industry` 生命周期五个回调的职责与调用时机：
  - `init`（绑定 id 与市场）、`apply`（写供需/收入/维护/统计）、`unapply`（撤销）、`reapply`（= `unapply` + `apply`）、`advance`（每帧：建设进度与 disruption 结束检测）；
  - 以及 `Market.reapplyIndustries()` / `Market.advance` 这两个外部驱动点；
- 用 `supply(...)` / `demand(...)` 在 `apply()` 里声明供需：
  - 说出修饰最终进入 `MutableCommodityQuantity.getQuantity()`（`MutableStat`），并由经济步的 `updateMaxSupplyAndDemand()` 以"同商品取最大值"的方式使用；
  - 用 `getMaxDeficit` / `applyDeficitToProduction` 实现缺货削减；
- 说出建设与升级链：`startBuilding` / `startUpgrading` 置位 → `advance` 累积游戏日 → `finishBuildingOrUpgrading` 完成。
  - 升级时 `market.removeIndustry(id, null, true)` + `addIndustry(upgradeId)` 并迁移 AI core / 改良 / 特殊物品；`downgrade()` 立即触发同一条完成链；
- 说出 AI core 的安装语义：`setAICoreId` → `applyAICoreModifiers` 派发四个钩子（`applyAlpha/Beta/Gamma/NoAICoreModifiers`）。
  - 供需侧 `supplyBonus` / `demandReduction` 汇总、维护费 × `UPKEEP_MULT`、`getPatherInterest`、移除时 `notifyBeingRemoved` 返还；
- 说出 disruption 语义：`setDisrupted(days[, useMax])` 写入市场 memory（key 由类名生成）、`isFunctional()` 返回 false、建设暂停、进度归零、结束回调 `disruptionFinished()`，以及三个原版触发方（陆战队突袭、Hegemony AI 审查、Luddic Path 细胞）；
- 从症状定位十类典型故障：产业 id 未注册、供需写在 `apply()` 之外、缺货削减对未声明供给的商品无效、升级目标 id 未注册、AI core 修饰只加不撤、升级路径 `forUpgrade` 传错。
  - disruption key 同类冲突、`getMaxDeficit` 把"需求"当"短缺"、把 `getDisruptedDays()` 当剩余建设时间、改供需后立刻看商品流没变化。

结果必须沿着**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置复核（沿用本页的最小任务链）。

## 前置知识

本页构建在以下既有概念之上，建议按需回看：

- MOD 目录结构、`starsector.log` 与命名约定：数据/资源 key 前缀 `GearIn_`、包 `data.scripts.gearin.<domain>`、类 `GearIn<Subject>`（S01-P01）；
- `econ/*.json` 市场的 `industries` 数组与市场就位时机：元素可为纯 id 字符串或 `[id, param...]` 数组；`onNewGameAfterEconomyLoad` 之后经济与市场已就位（S03-P03）；
- `modPlugin` 生命周期与 persistent data 一次性守卫：每个注册项用各自的 persistent data 键（S06-P02）；
- `settings.json` 读取：本页引用的 `maxIndustries`、`industryRefundFraction` 等键（S06-P04）；
- `MutableStat`：本页的供需数量、收入、维护费都是它；`modifyFlat` 同 id 幂等覆盖（S07-P02）；
- `SectorAPI.getEconomy()` 入口与游戏日换算：`getClock().convertToDays(amount)` 把每帧秒数换算成游戏日（建设进度用它）（S08-P01）；
- 生涯对象族写法与一次性注册守卫（S08-P03）；
- 市场与产业访问入口、经济循环节奏：`MarketAPI.addIndustry` / `getIndustry` / `hasIndustry` / `hasFunctionalIndustry` / `removeIndustry` / `instantiateIndustry`。
  - 产业 id 校验与条件不同，未注册 id 抛 `RuntimeException`；经济步的推进节奏与 `EconomyUpdateListener` 注册方式（S08-P04）。

## 术语

按术语约定，本页首次完整讲解以下词（后续页面继续使用）：

- **产业（industry）**：API 名保留 `Industry`。挂在一个市场下的"生产/服务单元"，由静态 spec（`industries.csv` 行）+ 运行时实例（plugin 列类名）组成，承载供需、收入、维护费、AI core、特殊物品、改良与 disruption 等状态。与市场条件（`MarketConditionAPI`）并列但生命周期更重。
- **供需修饰（supply/demand modifier）**：`MutableCommodityQuantity.getQuantity()` 上的 `MutableStat` flat 修饰。
  - 产业的 `supply(...)` / `demand(...)` 以 `"ind_<id>[_<n>]"` 为修饰 id 写入数量；
  - `supplyBonus` / `demandReduction` 是产业级"产量加一 / 需求减一"统计项，写入时合并为 `"ind_sb"` / `"ind_dr"`。
- **建设与升级（building / upgrading）**：`isBuilding()` = 建设中（含首次建设与升级）；`isUpgrading()` = 建设中且 `upgradeId != null`。`isFunctional()` = 非 disruption 且非"建设中且未升级"。
- **AI core**：安装到产业上的三种商品化智能核心（`alpha_core` / `beta_core` / `gamma_core`），提供供需、维护费与（自定义）统计修饰，并提高 Luddic Path 的关注度（`getPatherInterest`）。
- **disruption（中断）**：产业被外部行动（突袭、审查）打停的一段带倒计时的状态，存于市场 memory（key 含产业类名）。中断期间产业不产出、不推进建设。

## 文件与 API 链路

### 产业注册链（industries.csv）

产业静态表是 `data/campaign/industries.csv`（含表头；RC8 原版 33 个活动产业，另有 1 个 `#` 注释行）。15 个列按表头顺序为：

| 列 | `IndustrySpecAPI` 字段 | 语义 |
|---|---|---|
| `id` | `getId()` | 产业 spec key，全游戏唯一；`MarketAPI.addIndustry(id)` / `instantiateIndustry(id)` 按它查 spec |
| `name` | `getName()` | 显示名（`getCurrentName()`）|
| `cost mult` | `getCost()` | 建设费用基础值（`getBuildCost()`，可被 `setBuildCostOverride` 覆盖；升级/关停退款按它 × `industryRefundFraction`）|
| `build time` | `getBuildTime()` | 建设天数（游戏日）；`startBuilding` / `startUpgrading` 时写入 |
| `income` | `getIncome()` | 收入基础值（× 市场规模倍率后写入 `getIncome()` stat）|
| `upkeep` | `getUpkeep()` | 维护费基础值（× 规模倍率 × 维护倍率后写入 `getUpkeep()` stat）|
| `downgrade` | `getDowngrade()` | 降级目标产业 id（`downgrade()` 使用）|
| `upgrade` | `getUpgrade()` | 升级目标产业 id（`startUpgrading()` 使用；UI 的"升级"按钮只在非空时出现）|
| `tags` | `getTags()` | 产业 tag；`industry` / `structure` 决定 `isIndustry()` / `isStructure()`（`Industries.TAG_INDUSTRY` / `TAG_STRUCTURE`），`important` / `population` 等被 UI 与队列使用 |
| `data` | `getData()` | 附加数据字符串 |
| `image` | `getImageName()` | 产业贴图（`getCurrentImage()`）；资源从全局资源集解析，MOD 可引用核心自带路径或自己的路径 |
| `plugin` | `getPluginClass()` | 产业 Java 类名；`getNewPluginInstance(market)` 用无参构造实例化 |
| `desc` | `getDesc()` | 描述文本（tooltip 展示，支持 `$token` 替换）|
| `order` | `getOrder()` | 列表排序权重 |
| `disruptDanger` | `getDisruptDanger()` | `RaidDangerLevel` 枚举名（NONE/MINIMAL/LOW/MEDIUM/HIGH/EXTREME），突袭"中断产业"目标时长来源 |

注册链（使用方视角的"数据 → 注册"半程）：

1. 加载器把每一行读成 `IndustrySpecAPI`，`id` 列成为 spec key（缺失/重复语义与 `hull_mods.csv` 的 `readCSVMerged` 一致：同文件重复 id 抛异常、跨文件重复后读行跳过——"产业不出现且无报错"首选排查方向）；
2. `MarketAPI.addIndustry(id)` 或 `instantiateIndustry(id)` 从 CSV spec 加载期的注册表按 id 取 `IndustrySpecAPI`，查不到**抛 `RuntimeException("Industry [" + id + "] not found")`**（`Market.addIndustry`）。
   - 与条件不同：`addCondition` 不校验 id，产业校验；
3. `spec.getNewPluginInstance(market)` 无参实例化产业类（`plugin` 列类名必须存在且可无参实例化，否则类加载失败）；
4. `addIndustry(id, params)` 带参数时先 `initWithParams(params)`（经济 JSON 的 `[id, param...]` 数组元素：识别 AI core id 与 params 匹配本产业 id 的特殊物品）；
5. 产业加入 `market.getIndustries()` 后**立即 `apply()`**——`addIndustry` 本身不校验 `isAvailableToBuild()`，代码路径可以给任何市场直接挂产业。

`SettingsAPI.getIndustrySpec(id)`（`com.fs.starfarer.api.SettingsAPI`）是只读查询同一注册表的入口。

### 产业生命周期（Industry / BaseIndustry）

`Industry`（`com.fs.starfarer.api.campaign.econ.Industry`）是纯接口； `BaseIndustry`（`com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`）是产业公共基类。

`BaseIndustry` 归属默认状态（`supply` / `demand` map、`income` / `upkeep` stat、`building` / `buildProgress` / `upgradeId` / `aiCoreId` / `improved` / `special` 字段）与各链路默认行为。

原版 17 个产业类全部 `extends BaseIndustry`（`com.fs.starfarer.api.impl.campaign.econ.impl` 包）。

五个核心回调（API 签名与公开实现佐证）：

| 回调 | 时机 | 职责 |
|---|---|---|
| `init(String id, MarketAPI market)` | `getNewPluginInstance` 后、`apply` 前 | 绑定 id 与 market；`BaseIndustry.init` 同时 `readResolve()`（重取 spec、生成 `"ind_<id>"` / `"ind_<id>_<n>"` 修饰 id 族）|
| `apply()` | `addIndustry` 时、`reapplyIndustries` 每轮、tooltip 预览（克隆市场）时 | 写供需、收入/维护、统计修饰、AI core / 改良 / 特殊物品效果。子类先 `super.apply(true)` 再写自己的供需 |
| `unapply()` | `reapply` 前半、`removeIndustry`、`startBuilding` | 撤销 AI core 修饰（`applyNoAICoreModifiers`）、改良、移民修饰、特殊物品效果 |
| `reapply()` | 外部直接调用或 tooltip 预览 | 语义固定为 `unapply()` + `apply()`（API javadoc 原文："Calls unapply() and then reapply()"）|
| `advance(float amount)` | 市场每帧（`Market.advance` 对产业列表逐个调用）| `BaseIndustry.advance`：disruption 结束检测（`wasDisrupted` 翻转 → `disruptionFinished()`）；建设中且未中断时用 `getClock().convertToDays(amount)` 累积 `buildProgress`，达到 `buildTime` 调 `finishBuildingOrUpgrading()` |

`BaseIndustry.apply(boolean withIncomeUpdate)` 的内部顺序：

1. `updateSupplyAndDemandModifiers()`：重置并汇总 `supplyBonus` / `demandReduction`（AI core、改良、管理员 `Stats.SUPPLY_BONUS_MOD` / `Stats.DEMAND_REDUCTION_MOD`、外部修饰）；
2. `withIncomeUpdate` 时 `updateIncomeAndUpkeep()`（收入/维护 stat 写入）；
3. `applyAICoreModifiers()`：按 `aiCoreId` 派发 `applyAlphaCoreModifiers` / `applyBetaCoreModifiers` / `applyGammaCoreModifiers`（无 core 时 `applyNoAICoreModifiers`）；
4. `applyImproveModifiers()`（改良状态）；
5. 移民修饰注册、特殊物品效果（requirements 满足才 `effect.apply(this)`，否则 `effect.unapply(this)`）。

市场侧的两个驱动点（`Market`）：

- `Market.advance(amount)`：**稳定度取整值变化时**触发 `reapplyConditions()` + `reapplyIndustries()` + `updatePrices()`。
  - 随后对 `new ArrayList<>(industries)` 逐个 `advance(amount)`——产业每帧推进挂在市场推进上；
- `Market.reapplyIndustries()`：`getAdmin().getStats().refreshGovernedOutpostEffects(this)` → 对每个产业 `unapply()` + `apply()`。
  - 随后 `PopulationAndInfrastructure.modifyUpkeepByHazardRating(market, "upkeep_hazard_mod")`（维护费 × 危险度修饰统一在产业重算尾部补上）。

::: caution 必须遵守的规则：供需与统计修饰只能写在 `apply()` 里
`BaseIndustry.doPreSaveCleanup()` 在存档前把 `supply` / `demand` / `income` / `upkeep` 全部置 null， `doPostSaveRestore()` 重建空 map——供需只能由 `apply()` 每轮重建。只写在构造器或 `init` 里的供需，读档后全部消失且无报错。

这是本页第一条必须遵守的规则（见「规则强度」）。
:::

### 供需链

`BaseIndustry` 持有 `supply` / `demand` 两个 `Map<String, MutableCommodityQuantity>`； `getSupply(id)` / `getDemand(id)` 用到时才创建条目。

- **写入**：`supply(modId, commodityId, quantity, desc)` / `demand(...)` 在 `getQuantity()`（`MutableStat`）上 `modifyFlat`；`quantity == 0` 时 `unmodifyFlat`（"清掉这条供需"的正式写法）。
  - 默认 modId 族 = `"ind_<id>"`（索引 0）与 `"ind_<id>_<n>"`；`quantity > 0` 时把 `supplyBonus` / `demandReduction` 的生效值合并写入 `"ind_sb"` / `"ind_dr"`——**alpha core 的"产量 +1"就是这条路径**。
- **收入与维护**：`updateIncomeAndUpkeep()` → `applyIncomeAndUpkeep(size)`：`sizeMult = max(1, market.getSize() - 2)`。
  - `income = (int)(spec.getIncome() × sizeMult)` 写 `"ind_base"` flat + `"ind_stability"` mult（市场收入倍率）；`upkeep = (int)(spec.getUpkeep() × sizeMult)` 写 `"ind_base"` + `"ind_hazard"` mult（市场维护倍率）。
  - 随后 `applyAICoreToIncomeAndUpkeep()`（alpha/beta → `"ind_core"` × `UPKEEP_MULT = 0.75`，gamma 无）；**`!isFunctional()` 时收入修饰被移除**——建设中与中断中的产业不产生收入。
- **缺货**：`getMaxDeficit(commodityIds...)` 返回 `(商品, 缺口)`，缺口 = `getDemand(id).getQuantity().getModifiedInt() − market.getCommodityData(id).getAvailable()`。
  - `getAllDeficit(...)` 返回全部正缺口列表；`applyDeficitToProduction(index, deficit, commodities...)` 对列出的商品写负供给（desc 为"X shortage"）。
  - **且只对已声明过供给的商品生效**（`if (getSupply(commodity).getQuantity().isUnmodified()) continue;`）。
- **读取（经济步）**：经济步每次迭代重建商品可达图（`CommodityMarketDataAPI` 构造器）：对每个市场、每个商品调 `updateMaxSupplyAndDemand()`（`CommodityOnMarketAPI.updateMaxSupplyAndDemand`）。
  - **maxSupply / maxDemand = 该市场所有产业中同商品供需的最大值**（不是总和），同时记下该产业对 max 值的合法性判定（`isSupplyLegal` / `isDemandLegal`）。
  - 主商品（`CommoditySpecAPI.isPrimary()`）的 maxDemand 还会沿 demand class 传播给同类的其他商品。
  - 价格与库存写入沿用市场与商品流主题已介绍的 `updateStockpileAndPriceV2`。
  - 需求侧定价按商品的 `demand class`（`commodities.csv` 的 `demand class` 列：`supplies` / `fuel` / `crew` / `organics` / `heavy_machinery` / `domestic_goods` / `drugs` 等）聚合到 `MarketDemandAPI`。

::: info 背景：为什么商品流按「最大值」而非「总和」统计
多个产业可能供给同一商品（例如本页示例与其它产业都产 `crew`）。

经济步的 `CommodityOnMarketAPI.updateMaxSupplyAndDemand` 对「该市场所有产业中同商品」取最大值作为 `maxSupply` / `maxDemand`，而不是把各产业数量相加—— 市场总量以「单一最强供给/需求」为口径。

读商品流数字与定价时按这个口径理解，不要按总和预期。
:::

可观察结果形成完整流程：产业 tooltip 的 Production / Demand & effects 区块（`BaseIndustry.createTooltip`）按供需 map 的修饰值渲染图标；经济 UI 的短缺/过剩按 `CommodityMarketDataAPI` 的结果显示。

### 建设与升级链

状态字段：`building` / `buildProgress` / `buildTime` / `upgradeId`。

| 入口 | 行为（`BaseIndustry`）|
|---|---|
| `startBuilding()` | `building = true`、进度归零、`upgradeId = null`、`buildTime = spec.getBuildTime()`，然后 **`unapply()`**——建设中的产业立刻停摆（`isFunctional()` = `!(building && !isUpgrading())`）|
| `advance(amount)` | `building && !disrupted` 时 `buildProgress += convertToDays(amount)`；达到 `buildTime` 调 `finishBuildingOrUpgrading()`。**中断期间建设暂停** |
| `finishBuildingOrUpgrading()` | 升级分支（`upgradeId != null`）：`market.removeIndustry(getId(), null, true)` → `market.addIndustry(upgradeId)` → 把 `aiCoreId` / `improved` 迁移到新产业 → `upgradeFinished(previous)`（迁移特殊物品）→ 新产业 `reapply()`。首次建设分支：`buildingFinished()`（通知 + 队列下一项）→ `reapply()` |
| `startUpgrading()` | `building = true`、进度归零、`upgradeId = spec.getUpgrade()`、`buildTime = 升级 spec 的 buildTime`；**升级目标 spec 必须已注册**，否则 `getIndustrySpec(upgradeId)` 返回 null → 取 `getBuildTime()` 时 NPE |
| `downgrade()` | `building = true`、`upgradeId = spec.getDowngrade()`，立即 `finishBuildingOrUpgrading()`——降级走同一条完成链（同样要求降级目标已注册）|
| `cancelUpgrade()` | 复位 `building` / 进度 / `upgradeId` |

UI 使用方（产业交互对话框）：升级确认扣款后调 `startUpgrading()`；降级确认后调 `downgrade()` 并返还退款；关停调 `market.removeIndustry(id, interactionMode, false)`。

tooltip 的升级入口只在 `getSpec().getUpgrade() != null && !isBuilding()` 时出现（`BaseIndustry.createTooltip`）。

建设队列（`ConstructionQueue`，`com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue`）： `MarketAPI.getConstructionQueue()` 是队列容器，`addToEnd(id, cost)` 入队；

`Market.advance` 在"队列非空且当前无在建产业"时调 `BaseIndustry.buildNextInQueue(market)`：逐个弹出， `isAvailableToBuild()` 且未超产业上限才开工（`addIndustry` + `startBuilding` + 成本 override）。

否则给用户退款并记"Construction aborted" intel。

产业位上限： `Misc.getMaxIndustries(market)` = `market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).computeEffective(0)`（`Misc`）；

该 mod 由 `population` 产业（`PopulationAndInfrastructure.apply`）写入 `getMaxIndustries(size)`，读 settings `maxIndustries` 数组（`[1,1,1,2,3,4,4,4,4,4]`，size 1–10 减一索引）——**size 1–3 的殖民地最多 1 个产业位**。

超出时 `PopulationAndInfrastructure` 施加稳定度惩罚（`Misc.OVER_MAX_INDUSTRIES_PENALTY` = settings `overMaxIndustriesPenalty` = 5）。

`Misc.getNumIndustries(market)` 只统计 `isIndustry()` 的产业（升级中的按升级目标算）。

`isAvailableToBuild()`：市场无 `MARKET_NO_INDUSTRIES_ALLOWED` tag、已有 `population` 产业且自身不是 `population`。

### AI core 链

- 安装：`setAICoreId(String)` 写入 `aiCoreId`；`canInstallAICores()` 默认 true（`population` 等少数产业覆写为 false 的语义以各自类为准）。
  - UI 安装/卸下走 AI core 选择对话框（产业交互对话框）。
- 应用：`apply()` → `applyAICoreModifiers()` 按 core 派发四个**空实现钩子**：`applyAlphaCoreModifiers` / `applyBetaCoreModifiers` / `applyGammaCoreModifiers` / `applyNoAICoreModifiers`。
  - 自定义核心效果写这里。
- 供需侧（`updateAICoreToSupplyAndDemandModifiers`）：alpha → `supplyBonus +1` 且 `demandReduction +1`；beta / gamma → 仅 `demandReduction +1`。
  - "产量 +1 / 需求 −1 单位"就是 `SUPPLY_BONUS = 1` / `DEMAND_REDUCTION = 1`。
- 维护侧（`applyAICoreToIncomeAndUpkeep`）：alpha / beta → 维护费 × `0.75`（`"ind_core"` mult）；gamma → 无。
- 关注度：`getPatherInterest()`：alpha +4 / beta +2 / gamma +1，另加特殊物品的 pather tag 权重。
- 卸载/移除：`unapply()` 第一步调 `applyNoAICoreModifiers()`——**自定义核心修饰必须在这里成对撤销**。
  - `notifyBeingRemoved(mode, forUpgrade)` 在非升级移除时把 `aiCoreId` 与特殊物品返还给 cargo（`REMOTE` 交互进存储、其余进用户舰队）。
- 管理员人物：`AICoreAdminPlugin`（`com.fs.starfarer.api.campaign.AICoreAdminPlugin`）唯一方法是 `PersonAPI createPerson(String aiCoreId, String factionId, long seed)`。
  - 由实现类（原版 `AICoreAdminPluginImpl`）使用，用于把 AI core 变成殖民地管理员/官员。

### Disruption 链

状态不存产业对象，**存市场 memory**：

1. key：`getDisruptedKey()` = `"$core_disrupted_" + getClass().getSimpleName()`——**按 Java 类名生成**，同一市场内共用同一 Java 类的多个产业（不同 spec id）共享同一个 key；

::: warning 共用同一 Java 类的多个产业共享同一个 disruption key
同一市场内两个不同 spec id 但共用同一 plugin 类的产业（原版 farming / aquaculture 都 `extends Farming`）共享同一个 key：对其中一个施放 disruption，另一个也会被中断。这类产业必须覆写 `getDisruptedKey()` 区分（如加 spec id 后缀）。
:::

2. 施加：`setDisrupted(float days)` = `setDisrupted(days, false)`；`setDisrupted(days, useMax)` 先过 `canBeDisrupted()`（默认 true；`population` 覆写为 false）。
   - `useMax = true` 时取 `max(现有剩余, days)`（`MemoryAPI.getExpire(key)`），`useMax = false` 直接覆盖。
   - `dur <= 0` 时 `memory.unset(key)`，否则 `memory.set(key, true, dur)`（带天数的过期标记）。
   - 从未中断变中断时调 `notifyDisrupted()`。
3. 读取：`isDisrupted()` = `memory.is(key, true)`；`getDisruptedDays()` = `memory.getExpire(key)`（负数归零）；
4. 结束：`advance(amount)` 检测 `isDisrupted()` 从 true 翻回 false → `disruptionFinished()`（两个钩子默认空实现）。

中断期间的可观察行为：`isFunctional()` 恒 false → 供需 map 里"生产中"的判定失效、`applyIncomeAndUpkeep` 移除收入； `getBuildOrUpgradeProgress()` 返回 0；`advance` 的进度累积被 `!disrupted` 条件跳过（建设暂停）。

tooltip 显示红字 `Operations disrupted! X days until return to normal function.`； `getBuildOrUpgradeDaysText()` / `getBuildOrUpgradeProgressText()` 显示 `Disrupted: X days left`。

::: warning `getDisruptedDays()` 是剩余中断天数，不是剩余建设时间
建设剩余时间 = `buildTime − buildProgress`；中断期间建设暂停、进度冻结，要显示"还剩多少天恢复"应读 `getDisruptedDays()`。两套读数口径不同，直接混用会让 UI 数字与 tooltip 对不上。
:::

三个原版触发方（都在公开 API 面 `com.fs.starfarer.api.impl.campaign.*`）：

- **陆战队突袭**：`MarketCMD`（`com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD`）的"中断产业"目标用 `DisruptIndustryRaidObjectivePluginImpl`。
  - 时长 = `marineTokens × spec.getDisruptDanger().disruptionDays − 已中断天数`，`disruptDanger` 列取值见 `RaidDangerLevel` 枚举（NONE 60 / MINIMAL 50 / LOW 40 / MEDIUM 30 / HIGH 20 / EXTREME 10 天）；
- **Hegemony AI 审查**：`com.fs.starfarer.api.impl.campaign.intel.inspection.HIActionStage` 以 `curr.setDisrupted((intel.getRandom().nextFloat() * 45f) + 15f)` 施放——15–60 天；
- **Luddic Path 细胞**：`com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel` 以 `target.setDisrupted(disruptionDur, true)` 施放。
  - `useMax = true` 保留较长剩余时间。

## 规则强度

本页必须遵守的规则清单（"必须/禁止"，均附违反后的可观察结果）：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 产业 id 必须已在 `industries.csv` 注册后才可 `addIndustry` / `instantiateIndustry` / 用作 `upgrade` / `downgrade` 列目标（`Market.addIndustry` 抛 `RuntimeException("Industry [...] not found")`；`startUpgrading` 对未注册目标 `getIndustrySpec` 返回 null 后取 `getBuildTime()` NPE）| 必须 | `addIndustry` 抛 `RuntimeException`、产业不出现且流程中断；升级启动瞬间 NPE；升级完成时 `addIndustry(upgradeId)` 抛异常 |
| 供需、收入/维护与统计修饰必须写在 `apply()` 里，禁止只在构造器 / `init` 写入（`doPreSaveCleanup` 置空四个 map，`doPostSaveRestore` 重建空 map）| 必须 | 存档后读档，产业 tooltip 与商品流中的供需条目全部消失、无报错 |
| 想在缺货时被 `applyDeficitToProduction` 削减的商品，必须先 `supply(...)` 声明（`if (getSupply(commodity).getQuantity().isUnmodified()) continue;`）| 必须 | 缺货时该商品产量不减、tooltip 无 shortage 削减显示 |
| 升级 / 降级必须由 `startUpgrading` / `downgrade` → `finishBuildingOrUpgrading` 驱动，其中 `removeIndustry(id, null, true)` 的 `forUpgrade` 必须为 true | 必须 | 自定义替换链传 `false` 时 AI core / 特殊物品被 `notifyBeingRemoved` 返还 cargo，同时新产业仍继承一份——物品被复制；传错完成时机则队列与 intel 通知缺失 |
| AI core 自定义修饰必须成对：`applyAlpha/Beta/GammaCoreModifiers` 里加的修饰必须在 `applyNoAICoreModifiers` 撤销（`unapply()` 第一步就是 `applyNoAICoreModifiers`）| 必须 | 卸下 core 或产业重算后修饰残留且反复叠加（如稳定度 +1 永不消失）|
| 同一市场内共用同一 Java 类的多个产业（如不同 spec id 同一 plugin 类），必须覆写 `getDisruptedKey()` 区分 | 必须 | 对其中一个施放 disruption，同市场同类产业全部进入中断（key 由类名生成）；原版 farming / aquaculture 共用 `Farming` 类即存在此语义 |
| 修改供需后不要期待当前帧的 `getAvailable()` / 价格立即变化 | 建议 | 数值在下一个经济步（每月 10 次迭代）才被抓取重算；要立即反映用 `EconomyAPI.forceStockpileUpdate(market)` |
| `getDisruptedDays()` 是"剩余中断天数"，不是剩余建设时间（建设剩余 = `buildTime − buildProgress`）| 必须 | 把中断天数当剩余建设时间显示，UI 数字与 tooltip 对不上 |
| 中断期间的产业收入被移除、建设暂停（`applyIncomeAndUpkeep`；`advance`）| 必须 | 在 `advance` 里无视 disruption 继续累积 `buildProgress` 会与基类语义冲突（进度在恢复后"跳变"）|
## 完整最小例

示例所有权：`GearInTrainingIndustry`（产业 id `GearIn_training_industry`，本页拥有）。

在既有示例的 MOD 布局与 `GearInModPlugin` 之上新增：一条 `industries.csv` 注册行（数据文件，位于 `data\campaign\industries.csv`，用 CSV 合并语义提交自己的行）、一个演示类（本页所有）。

先看数据侧的注册行（自洽要求：`plugin` 列类名与本页 Java 类逐字一致，`upgrade` 列目标是已注册的原版 id `militarybase`——本页使用它演示升级链，不定义新 id）：

```text
GearIn_training_industry,GearIn Training Industry,20,60,,2,,militarybase,"training, industry, industrial",,graphics/icons/industry/light_industry.png,data.scripts.gearin.campaign.GearInTrainingIndustry,"A facility that puts recruits through a rigorous training regimen, turning idle hands into serviceable crew.",250,LOW
```

- `build time = 60`（游戏日）、`upkeep = 2`、`upgrade = militarybase`、`tags` 含 `industry`（`isIndustry()` 为 true，占产业位）、`disruptDanger = LOW`（40 天/令牌，见 `RaidDangerLevel`）；
- `image` 列用原版路径 `graphics/icons/industry/light_industry.png` 占位（全局资源集解析，核心自带该贴图；本页无资产，MOD 发布时替换为自己的贴图）。

目录结构（沿用既有示例页的布局，只新增加粗两项）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── industries.csv      # 新增本页注册行（CSV 合并，勿改核心文件）
├── src\
│   └── data\scripts\gearin\campaign\
│       └── GearInTrainingIndustry    # 本页新增（归本页所有）
└── jars\
```

### GearInTrainingIndustry

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/**
 * 完整最小例：产业（Industry）注册、供需、升级、AI core 与 disruption 演示。
 *
 * 注册入口：industries.csv 一行（plugin 列 = 本类全名，id = INDUSTRY_ID）。市场侧由
 * MarketAPI.addIndustry / instantiateIndustry 消费；演示安装点片段见页面正文
 * （GearInModPlugin 的 onGameLoad，persistent data 键 REGISTER_MARK 一次性门控）。
 *
 * 行为：
 *   1. apply() 声明供需：需求 SUPPLIES（训练耗材）与 CREW（教官），供给 CREW（结业船员）；
 *      supplies 缺货时按缺口削减船员产出（applyDeficitToProduction）。
 *   2. 收入/维护由 spec（income=0, upkeep=2）经 applyIncomeAndUpkeep 写入。
 *   3. AI core：alpha 额外给市场 +1 稳定度（applyAlphaCoreModifiers / applyNoAICoreModifiers
 *      成对）；供需/维护侧走 BaseIndustry 默认（alpha: 产量+1、需求-1、维护×0.75）。
 *   4. disruption：notifyDisrupted / disruptionFinished 打日志；setDisrupted 由外部触发
 *      （突袭、AI 审查、Luddic Path），中断期间 isFunctional()=false、建设暂停。
 *
 * 演示全程可清理：removeIndustry(GearInTrainingIndustry.INDUSTRY_ID, null, false) 即返还
 * AI core 并撤销全部修饰。
 */
public class GearInTrainingIndustry extends BaseIndustry {

    /** 产业 id：与 industries.csv 的 id 列逐字一致。 */
    public static final String INDUSTRY_ID = "GearIn_training_industry";

    /** 存档持久化标记：演示安装是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_TrainingIndustry";

    /** industries.csv upgrade 列目标（原版产业 id，本页只消费不定义）。 */
    public static final String UPGRADE_TARGET_ID = "militarybase";

    private static final Logger LOG = Global.getLogger(GearInTrainingIndustry.class);

    @Override
    public void apply() {
        // 顺序：updateSupplyAndDemandModifiers -> updateIncomeAndUpkeep ->
        // applyAICoreModifiers -> applyImproveModifiers（BaseIndustry.apply(boolean)）
        super.apply(true);

        int size = market.getSize();

        demand(Commodities.SUPPLIES, size, "Training operations");
        demand(Commodities.CREW, Math.max(1, size / 2), "Instructors");
        supply(Commodities.CREW, Math.max(1, size - 2), "Trained crew");

        // supplies 缺口会削减船员产出（只对已声明供给的商品生效）
        Pair<String, Integer> deficit = getMaxDeficit(Commodities.SUPPLIES);
        applyDeficitToProduction(1, deficit, Commodities.CREW);

        if (!isFunctional()) {
            supply.clear();
        }

        LOG.info(String.format(
                "GearIn training industry applied: market=%s size=%d supplyCrew=%d demandSupplies=%d demandCrew=%d income=%d upkeep=%d aiCore=%s disrupted=%s functional=%s",
                market.getName(), size,
                (int) getSupply(Commodities.CREW).getQuantity().getModifiedValue(),
                (int) getDemand(Commodities.SUPPLIES).getQuantity().getModifiedValue(),
                (int) getDemand(Commodities.CREW).getQuantity().getModifiedValue(),
                (int) getIncome().getModifiedValue(),
                (int) getUpkeep().getModifiedValue(),
                aiCoreId == null ? "none" : aiCoreId,
                isDisrupted(), isFunctional()));
    }

    @Override
    public void unapply() {
        super.unapply(); // 先 applyNoAICoreModifiers，再撤销改良/移民/特殊物品
    }

    // ---- AI core：自定义修饰必须成对（apply / unapply 对称） ----

    @Override
    protected void applyAlphaCoreModifiers() {
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }

    // ---- disruption 钩子（默认空实现，这里只打日志观察） ----

    @Override
    protected void notifyDisrupted() {
        LOG.info(String.format(
                "GearIn training industry disrupted: market=%s days=%d key=%s",
                market.getName(), (int) getDisruptedDays(), getDisruptedKey()));
    }

    @Override
    protected void disruptionFinished() {
        LOG.info("GearIn training industry disruption finished: market=" + market.getName());
    }

    /**
     * 观察助手：把目标市场里本产业的状态打一条日志（含供需明细）。
     * 供调用点、控制台与排障复用；市场无该产业时打印 not present。
     */
    public static void logState(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("GearIn training industry state: not present at " + market.getName());
            return;
        }

        StringBuilder supplyDesc = new StringBuilder();
        for (MutableCommodityQuantity curr : industry.getAllSupply()) {
            if (supplyDesc.length() > 0) supplyDesc.append(", ");
            supplyDesc.append(curr.getCommodityId())
                    .append("=")
                    .append((int) curr.getQuantity().getModifiedValue());
        }
        StringBuilder demandDesc = new StringBuilder();
        for (MutableCommodityQuantity curr : industry.getAllDemand()) {
            if (demandDesc.length() > 0) demandDesc.append(", ");
            demandDesc.append(curr.getCommodityId())
                    .append("=")
                    .append((int) curr.getQuantity().getModifiedValue());
        }

        LOG.info(String.format(
                "GearIn training industry state: market=%s id=%s supply=[%s] demand=[%s] income=%d upkeep=%d buildProgress=%.1f/%s building=%s upgrading=%s functional=%s disrupted=%s aiCore=%s upgrade=%s",
                market.getName(), industry.getId(), supplyDesc, demandDesc,
                (int) industry.getIncome().getModifiedValue(),
                (int) industry.getUpkeep().getModifiedValue(),
                industry.getBuildOrUpgradeProgress() * industry.getBuildTime(),
                industry.getBuildTime(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(),
                industry.isDisrupted(),
                industry.getAICoreId() == null ? "none" : industry.getAICoreId(),
                industry.getSpec().getUpgrade()));
    }

    /**
     * 探针助手：对目标市场里本产业施放 10 天 disruption 并立即清除（useMax=false 覆盖语义），
     * 打印 前后/期间 三态。只用于验证 setDisrupted / isDisrupted / getDisruptedDays 链路。
     */
    public static void runDisruptionProbe(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("GearIn training industry disruption probe: not present at " + market.getName());
            return;
        }
        LOG.info(String.format(
                "GearIn training industry disruption probe: before disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
        industry.setDisrupted(10f, false);
        LOG.info(String.format(
                "GearIn training industry disruption probe: during disrupted=%s days=%.1f functional=%s progress=%s",
                industry.isDisrupted(), industry.getDisruptedDays(),
                industry.isFunctional(), industry.getBuildOrUpgradeProgressText()));
        industry.setDisrupted(0f);
        LOG.info(String.format(
                "GearIn training industry disruption probe: after disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
    }
}
```

对应关系：

- `implements` 侧：`extends BaseIndustry` 继承全部默认行为（供需 map、收入/维护 stat、建设状态、AI core 派发、disruption memory 键）；类只写四类扩展点—— `apply()` / `unapply()`、AI core 成对钩子、disruption 钩子、两个静态观察助手。
- `INDUSTRY_ID` 与 `industries.csv` 的 `id` 列、`plugin` 列与本类全名**三者逐字一致**（`Market.addIndustry` 按 id 查 spec、按 plugin 类名实例化）。
- `UPGRADE_TARGET_ID = "militarybase"`：只用于与 CSV 行互相核对，运行时不读取它——升级链真正使用的是 `getSpec().getUpgrade()`（CSV `upgrade` 列）。
  - 升级完成时 `finishBuildingOrUpgrading` 会 `removeIndustry` + `addIndustry("militarybase")` 并迁移 AI core / 改良 / 特殊物品（`BaseIndustry`）。
- `apply()` 里 `super.apply(true)` 先跑基类汇总（供需 bonus、收入/维护、AI core、改良），再写本产业的供需与缺货削减；`isFunctional()` 为 false（建设中/中断）时清空供给。
- `applyAlphaCoreModifiers` / `applyNoAICoreModifiers` 用 `getModId(2)`（= `"ind_GearIn_training_industry_2"`）成对加减稳定度：安装 alpha core 市场稳定度 +1，卸下/重算即撤销（成对写法见「规则强度」的规则）。
- `runDisruptionProbe` 全程用公开 API（`setDisrupted` / `isDisrupted` / `getDisruptedDays` / `setDisrupted(0f)`），当帧清除、无持久残留（`setDisrupted(0f)` → `memory.unset(key)`）。

::: tip 观察助手可以复用
`logState` 与 `runDisruptionProbe` 都是静态方法，不依赖本页调用点——任何代码（控制台、其它回调、调试入口）都可以直接调用：`logState(market)` 打印本产业的供需/收入/建设/中断全状态；

`runDisruptionProbe(market)` 施放并立即清除 10 天 disruption，用于核对 `setDisrupted` / `isDisrupted` / `getDisruptedDays` 链路。
:::

调用点（工程片段——方法所在类不属于本页所有权，本页只演示调用，不改动那些文件）：

```java
    // GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本页注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInTrainingIndustry.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInTrainingIndustry.REGISTER_MARK, true);
        MarketAPI market = Global.getSector().getEconomy().getMarket("asheru");
        if (market != null) {
            market.addIndustry(GearInTrainingIndustry.INDUSTRY_ID);
            GearInTrainingIndustry.logState(market);
            GearInTrainingIndustry.runDisruptionProbe(market);
        }
    }
```

想走数据驱动的注册（替代上面的代码安装）：

在 `data\campaign\econ\<星系>.json` 的目标市场条目 `industries` 数组里加一行 `"GearIn_training_industry"`（或 `["GearIn_training_industry", "alpha_core"]`——数组第二元素起由 `initWithParams` 识别安装 AI core / 特殊物品）。

构建命令（在 `Starsector\mods\GearIndustry\` 执行）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`（可用 `jar tf` 核对）。

本页源码与示例工程的 fixture 源码逐字一致，fixture 编译验收命令见「验证方案（基于本例）」与「预期结果」。

### 验证方案（基于本例）

本方案以 `GearInTrainingIndustry` 的最终形态为对象（CSV 注册行 + 类 + 调用点片段）。

前置数据：MOD 布局与 `GearInModPlugin`（含本页调用点片段）、`data\campaign\industries.csv` 注册行、`GearInTrainingIndustry` 类（与示例工程 fixture 源码逐字一致）。

操作：

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileS08p05Java --no-daemon`，以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，`BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`。
   - 确认 `data\campaign\industries.csv` 合并后 `SettingsAPI.getIndustrySpec("GearIn_training_industry")` 返回非 null（可用日志或加载期检查）。
3. 代码安装：新建生涯，`starsector.log` 依次出现 `applied` / `state` / 三条 `disruption probe` 日志，核对见「预期结果」第 3–4 条；启动与游玩全程无 `gear_in` 引起的 `ERROR`。
4. 读档：保存后读档，不出现重复安装日志（`REGISTER_MARK` 守卫已置位，见「预期结果」第 8 条）。
5. 用户殖民地建设：殖民（size 3 以下注意产业位上限 1）→ 产业列表出现 `GearIn Training Industry` → 建造（建设期 `functional=false`）→ 60 游戏日后 `functional=true`。
   - 点击升级 → 完成后产业变为 `Military Base`（见「预期结果」第 5 条）。
6. AI core：安装 alpha core → `applied` 日志 `aiCore=alpha_core`、市场稳定度 +1；卸下 → 稳定度修饰消失（见「预期结果」第 6 条）。
7. disruption 实战：装 core 后等待 Hegemony AI 审查，或对殖民地执行陆战队「中断产业」突袭 → `notifyDisrupted` 日志 + tooltip `Operations disrupted!` + 收入消失。
   - 时间到 → `disruptionFinished` 日志 + 恢复（见「预期结果」第 7 条）。
8. 故障注入（逐项做完恢复）：
   - ① 把调用点 `addIndustry` 的参数改成 `"GearIn_training_industr"`（拼错）→ `RuntimeException: Industry [...] not found`，对应「实例驱动故障分析」第 1 条；
   - ② 把 `apply()` 里的 `demand(Commodities.SUPPLIES, size, ...)` 挪到构造器 → 读档后 `state` 的 demand 只剩 `crew=1`（supplies 需求消失），对应第 2 条；
   - ③ 注释掉 `applyNoAICoreModifiers` 的 `unmodifyFlat` → 装 alpha core 再卸下，市场稳定度 +1 残留，对应第 5 条。

通过信号：以上操作与核对结果全部符合。

清理：恢复所有注入的故障；取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。
## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileS08p05Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`； `data\campaign\industries.csv` 已含本页注册行（CSV 合并，勿改动核心文件）。
3. **代码安装（asheru，size 3 海盗市场）**：新建生涯，`starsector.log` 依次出现
```text
GearIn training industry applied: market=Asheru size=3 supplyCrew=1 demandSupplies=3 demandCrew=1 income=0 upkeep=NN aiCore=none disrupted=false functional=true GearIn training industry state: market=Asheru id=GearIn_training_industry supply=[crew=1] demand=[supplies=3, crew=1] income=0 upkeep=NN buildProgress=0.0/60.0 building=false upgrading=false functional=true disrupted=false aiCore=none upgrade=militarybase
```
数值核对：size 3 → `supplyCrew = max(1, 3-2) = 1`、`demandSupplies = 3`、`demandCrew = max(1, 3/2) = 1`； `income = 0`（spec 收入列空）；`upkeep = 2 × max(1, size-2) × 市场维护倍率`（以日志为准）；缺货削减只在 asheru 的 supplies 可得量不足 3 时把 `supplyCrew` 压到 0/负数区间（tooltip 图标同步变红）。
4. **disruption 探针**（同一批日志）：
```text
GearIn training industry disruption probe: before disrupted=false days=0.0 GearIn training industry disruption probe: during disrupted=true days=10.0 functional=false progress=Disrupted: 10 days left GearIn training industry disruption probe: after disrupted=false days=0.0
```
核对：`during` 行 `functional=false`（`isFunctional()` 在中断时恒 false）、 `progress` 显示 `Disrupted: 10 days left`（`getBuildOrUpgradeProgressText` 的中断分支）； `setDisrupted(0f)` 后 memory key 被 unset，`after` 恢复。市场内存里残留键 `$core_disrupted_GearInTrainingIndustry`（10 天过期），可用 `DumpMemory` 或内存检查核对。
5. **建设与升级（用户殖民地路径）**：用户殖民后产业列表出现 `GearIn Training Industry`（`isAvailableToBuild` 要求市场有 `population` 且无 `MARKET_NO_INDUSTRIES_ALLOWED` tag；size 1–3 的殖民地产业位上限 1，注意是否占满）；建造后 `applied` 日志的 `functional=false`（`isBuilding` 中，tooltip 显示 "Currently under construction and not producing anything"）； `advance` 按 `convertToDays(amount)` 累积 60 个游戏日后完成，`applied` 日志恢复 `functional=true`。点击升级：确认扣款后 `startUpgrading()`，完成后产业被替换为 `Military Base`（`militarybase` 行），AI core / 改良 / 特殊物品迁移（`finishBuildingOrUpgrading`）。
6. **AI core**：往产业装 alpha core（AI core 选择对话框）：`applied` 日志 `aiCore=alpha_core`；市场稳定度 +1（`applyAlphaCoreModifiers` 的 `ind_GearIn_training_industry_2` 修饰）；tooltip 的 alpha 描述显示维护费 −25%（`UPKEEP_MULT = 0.75`）、需求 −1、产量 +1（`DEMAND_REDUCTION` / `SUPPLY_BONUS = 1`）；卸下 core 后稳定度修饰消失（`applyNoAICoreModifiers`）。`getPatherInterest` = 4（alpha）会提高 Luddic Path 对该市场的兴趣。
7. **中断的实战触发**：给装 core 的殖民地等待 Hegemony AI 审查 intel（15–60 天），或对该市场执行陆战队"中断产业"突袭（时长 = 令牌数 × `disruptDanger.disruptionDays`，本行 LOW = 40 天/令牌）——`notifyDisrupted` 日志出现，tooltip 显示 `Operations disrupted!`，收入消失、建设暂停；时间到后 `disruptionFinished` 日志出现，恢复。
8. **读档**：不重复安装（`REGISTER_MARK` 守卫已置位），不出现 `applied` / `state` / `probe` 日志；`doPreSaveCleanup` / `doPostSaveRestore` 之后供需由 `apply()` 重建——读档后 `state` 日志（手动触发）的 supply/demand 与读档前一致。
9. **清理**：`market.removeIndustry(GearInTrainingIndustry.INDUSTRY_ID, null, false)` 后：「applied」日志消失、稳定度修饰撤销、装有 alpha core 时 core 返还用户 cargo（`notifyBeingRemoved`）。

静态自查：`GearInTrainingIndustry` 类与示例工程 fixture 源码逐字一致（包括 import 顺序）；`INDUSTRY_ID` 与 CSV `id` 列、`plugin` 列类名逐字一致； `REGISTER_MARK` 与调用点片段的键逐字一致；

没有把工作单元编号写进任何运行时名称（类名、包名、产业 id、persistent data 键、modifier id 均不含 S08-P05 变体）。

## 实例驱动故障分析

1. **`addIndustry` 抛 `RuntimeException: Industry [...] not found`**。触发：`industries.csv` 没有该 id 行，或行被跨文件重复行静默覆盖（`readCSVMerged` 语义），或 id 拼写不一致。可观察：`Market.addIndustry` / `instantiateIndustry` 直接抛异常，日志 ERROR、产业不出现、后续流程中断。修正：以 `SettingsAPI.getIndustrySpec(id)` 返回非 null 为注册成功判据，先核对 CSV 行与调用 id 逐字一致。
2. **读档后产业供需消失、tooltip 空**。触发：供需写在构造器 / `init` 而不是 `apply()`。可观察：`doPreSaveCleanup` 置空四个 map、`doPostSaveRestore` 重建空 map，读档后没有任何回调重建供需——无报错，只有商品流和 tooltip 变化。修正：供需与统计修饰全部放 `apply()`（`reapplyIndustries` / tooltip 预览都会重放它）。
3. **缺货了产量不减**。触发：`applyDeficitToProduction` 列出的商品从没 `supply(...)` 过。可观察：`isUnmodified()` 短路跳过，该商品产出纹丝不动，tooltip 无 shortage 削减图标。修正：先声明供给再挂缺货削减（`LightIndustry.apply` 的写法：先 `demand`/`supply` 声明——ORGANICS 需求、DOMESTIC_GOODS / LUXURY_GOODS / DRUGS 供给——再对 ORGANICS 缺口 `applyDeficitToProduction(1, deficit, DOMESTIC_GOODS, LUXURY_GOODS, DRUGS)`）。
4. **升级启动/完成时报错**。触发：`upgrade` 列指向未注册 id。可观察：`startUpgrading` 里 `getIndustrySpec(upgradeId)` 返回 null → 取 `getBuildTime()` NPE；即使绕过，完成时 `market.addIndustry(upgradeId)` 抛 `Industry [...] not found`。修正：升级/降级目标必须是已注册产业 id；跨 MOD 升级目标要求对方 MOD 已启用（硬依赖声明遵循依赖校验主题的惯例）。
5. **卸下 AI core 后效果残留**。触发：`applyAlphaCoreModifiers` 加了修饰，但 `applyNoAICoreModifiers` 没撤销。可观察：卸 core / `reapply` 后修饰仍在且每次重算叠加（`unapply()` 第一步只调 `applyNoAICoreModifiers`）。修正：成对钩子（本页示例的稳定度修饰就是成对写法）。
6. **升级时 AI core / 特殊物品"复制"**。触发：自定义升级流程里 `removeIndustry(id, mode, false)` 而非 `forUpgrade=true`。可观察：`notifyBeingRemoved(mode, false)` 把 core / 特殊物品返还 cargo，同时 `finishBuildingOrUpgrading` 又迁移一份给新产业——用户白得一份。修正：升级链路必须走 `startUpgrading` → `finishBuildingOrUpgrading`（内部 `forUpgrade=true`）；自定义代码不要手写替换链。
7. **同市场两个产业同时被中断**。触发：两个不同 spec id 共用同一 Java 类（如原版 farming / aquaculture 都 `extends Farming`），没覆写 `getDisruptedKey()`。可观察：key = `"$core_disrupted_" + 类名`，同一市场内两个实例共享 key——对 A 施放 disruption，B 的 `isDisrupted()` 也为 true、双双停摆。修正：共用类的多产业覆写 `getDisruptedKey()`（如加 spec id 后缀）。
8. **把 `getMaxDeficit` 的结果当"总需求"用**。触发：读需求缺口时误以为 `deficit.two` 是需求值。可观察：`getMaxDeficit` 返回的是 `max(0, demand − available)`，库存充足时是 0，不是需求；按它算"需求"会得到偏小的数字。修正：需求读 `getDemand(id).getQuantity().getModifiedValue()`，短缺读 `getMaxDeficit` / `getAllDeficit`。
9. **把 `getDisruptedDays()` 当剩余建设时间显示**。触发：UI 文案直接拼 `getDisruptedDays()`。可观察：中断期间显示的是剩余中断天数（`getBuildOrUpgradeDaysText` 的中断分支），与正常建设的 `buildTime − buildProgress` 口径不一致。修正：区分两套读数（见本页「规则强度」的规则）。
10. **改完供需立刻看商品流，数值没变**。触发：`apply()` 里改了 `demand(...)` 后在当前帧读 `getAvailable()` / 价格。可观察：供需被抓取发生在经济步的 `CommodityMarketDataAPI`（`updateMaxSupplyAndDemand`），不是每帧——未暂停时几十真实秒内才有下一轮迭代；价格/库存写入只在月末步（经济步的节奏）进行。修正：等一个经济步，或对目标市场调 `EconomyAPI.forceStockpileUpdate(market)` 局部刷新。
11. **`supply(com, 0)` 想清零但"没生效"**。触发：用 `supply(commodityId, 0)` 清供给，但另一处仍以相同 modId 写了正值。可观察：`quantity == 0` 分支是 `unmodifyFlat(modId)`——只移除自己这个 modId 的修饰，别的修饰源（AI core 的 `ind_sb`、其他产业）仍在。修正：清供需用 `unmodifyFlat` 对应自己的 modId，理解 0 是"撤销本条"而非"写 0"。

## 总结

本页把产业讲成四条链：

- **注册链**：`industries.csv`（15 列）→ CSV spec 加载期注册的 `IndustrySpecAPI`（id 为 key）→ `MarketAPI.addIndustry` / `instantiateIndustry`（未注册抛 `RuntimeException`）→ `getNewPluginInstance(market)` 实例化 → `init` → `apply`。产业 id 校验比条件严格。
- **生命周期链**：`apply()` 写供需/收入/维护/统计 → `unapply()` 撤销 → `reapply()` = 两者拼接 → 每帧 `advance`（建设进度 + disruption 结束检测）→ 市场侧 `Market.advance`（稳定度变化触发 `reapplyIndustries`）与 `Market.reapplyIndustries` 是两大驱动点。**供需只能活在 `apply()` 里**（存档清理会置空 map）。
- **供需与升级链**：`supply` / `demand` 是 `MutableCommodityQuantity` 上的 `MutableStat` flat 修饰（modId 族 `ind_<id>_<n>`）；经济步按"同商品取最大值"使用（`updateMaxSupplyAndDemand`）；缺货削减只作用于已声明供给的商品。升级：`startUpgrading` → `advance` 累积游戏日 → `finishBuildingOrUpgrading` → `removeIndustry(id, null, true)` + `addIndustry(upgradeId)` + 迁移（AI core / 改良 / 特殊物品）；降级立即走同一完成链；产业位上限由 `population` 按 `maxIndustries` 数组写入。
- **AI core 与 disruption 链**：core 安装 = `setAICoreId` + `apply()` 派发四个空实现钩子；供需侧 `supplyBonus` / `demandReduction`、维护侧 ×0.75（alpha/beta）、`getPatherInterest` 关注度；自定义修饰必须成对撤销。disruption = 市场 memory 里带过期天数的标记（key 按类名生成），中断期间 `isFunctional()` 恒 false、建设暂停、收入移除；三个原版触发方（突袭 / Hegemony 审查 / Luddic Path 细胞）都只调 `setDisrupted`，不需要额外接入。

记忆要点：

- CSV `id` 必须与 `addIndustry` 参数逐字一致；`upgrade` / `downgrade` 列目标必须已注册。
- `supply` / `demand` / 收入 / 维护只写在 `apply()`；缺货削减前先 `supply(...)`。
- `getModId(n)` = `"ind_<id>_<n>"`；AI core 修饰成对（`applyXxxCoreModifiers` ↔ `applyNoAICoreModifiers`）。
- `setDisrupted(days, useMax)`：`useMax=true` 保留较大剩余；`setDisrupted(0)` 解除；同市场同 Java 类的多产业共享 disruption key。
- 产业位 = `maxIndustries[size-1]`（size 1–3 都是 1）；`isFunctional()` = 非中断 ∧ 非"建设中且未升级"。

从逻辑上看，产业不是孤立对象：市场与商品流的经济循环（经济步）是产业供需的使用端，时钟与游戏日换算（`getClock().convertToDays`）是建设进度的换算来源， `MutableStat` 承载全部数量修饰；本页示例的 `REGISTER_MARK` 一次性守卫沿用既有生涯注册示例的写法。

产业写完并注册后，它在市场里的可见性（tooltip 数字、商品流、价格、突袭中断）全部由这些既有机制承接— —本页只负责把产业自身的四条链写对。

## 建议参考项

- Java API（包路径）：
  - `com.fs.starfarer.api.campaign.econ.Industry`——产业接口全貌：生命周期五回调、供需、建设/升级、AI core、disruption、特殊物品/改良
  - `com.fs.starfarer.api.loading.IndustrySpecAPI`—— `industries.csv` 全部列字段的 getter/setter 与 `getNewPluginInstance` 无参实例化
  - `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`——产业公共基类：`apply(boolean)` 顺序、供需写入与 `ind_sb` / `ind_dr` 合并、`applyDeficitToProduction`、`applyIncomeAndUpkeep`、`advance`、建设/升级/降级链、AI core 派发与四钩子、 `getPatherInterest`、disruption 系列（`getDisruptedKey` / `setDisrupted` / `getDisruptedDays` / `canBeDisrupted` / `isDisrupted`）、 `doPreSaveCleanup` / `doPostSaveRestore`、`initWithParams`、`isAvailableToBuild`、tooltip 渲染
  - `com.fs.starfarer.api.impl.campaign.econ.impl.LightIndustry`——`apply()` 标准写法范例（先声明供需、再挂缺货削减）
  - `com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure`——`MAX_INDUSTRIES` 写入、超限稳定度惩罚、`getMaxIndustries(size)`、`canBeDisrupted() = false`
  - `com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue`——建设队列容器与 `addToEnd`
  - `com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity`——商品 id + `MutableStat` 数量
  - `com.fs.starfarer.api.campaign.AICoreAdminPlugin`——`createPerson(aiCoreId, factionId, seed)`，把 AI core 变成殖民地管理员/官员
  - `com.fs.starfarer.api.impl.campaign.ids.Industries`——tag 常量 `TAG_INDUSTRY` / `TAG_STRUCTURE` / `TAG_POPULATION`
  - `com.fs.starfarer.api.impl.campaign.ids.Commodities`—— `SUPPLIES` / `CREW` / `ALPHA_CORE` / `BETA_CORE` / `GAMMA_CORE`
  - `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD`——`RaidDangerLevel.disruptionDays` 与突袭「中断产业」时长计算
  - `com.fs.starfarer.api.impl.campaign.graid.DisruptIndustryRaidObjectivePluginImpl`—— 突袭中断目标的 `disruptDanger` 使用与 `setDisrupted` 施放
  - `com.fs.starfarer.api.impl.campaign.intel.inspection.HIActionStage`——Hegemony AI 审查对产业施放 15–60 天 disruption
  - `com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel`—— Luddic Path 细胞以 `setDisrupted(dur, true)` 施放并保留较长剩余
  - `com.fs.starfarer.api.util.Misc`—— `getMaxIndustries` / `getNumIndustries` / `getCurrentlyBeingConstructed` / `OVER_MAX_INDUSTRIES_PENALTY`
  - `com.fs.starfarer.api.SettingsAPI`——`getIndustrySpec(id)` 只读查询产业注册表
  - `com.fs.starfarer.api.campaign.econ.MarketAPI`—— 产业访问族（`addIndustry` / `getIndustry` / `hasIndustry` / `hasFunctionalIndustry` / `removeIndustry` / `instantiateIndustry`）与 `getConstructionQueue`
  - `Market`——`addIndustry` / `instantiateIndustry` / `removeIndustry` / `reapplyIndustries`、`advance` 稳定度重算与产业推进
  - `CommodityOnMarketAPI`——`updateMaxSupplyAndDemand` 同商品取最大值与合法性、`getAvailable` / `getMaxSupply` / `getMaxDemand`
  - `CommodityMarketDataAPI`——经济步使用产业供需（`updateMaxSupplyAndDemand`、demand class 传播）
  - 产业交互对话框——升级/降级/关停 UI 使用方、退款计算
- 原版数据（相对路径）：
  - `data/campaign/industries.csv`——产业静态表：15 列表头与 `population` / `spaceport` / `militarybase` / `heavybatteries` 等原版行
  - `data/campaign/commodities.csv`—— `demand class` 列（supplies / fuel / crew / organics / heavy_machinery / domestic_goods / drugs 等）
  - `data/campaign/econ/corvus.json`——市场 `industries` 数组写法（纯 id 字符串与 `[id, param...]`）
  - `data/config/settings.json`——`maxIndustries` 数组、`overMaxIndustriesPenalty`、`industryRefundFraction`、`industryImproveBase`
- 本页示例：
  - `data/scripts/gearin/campaign/GearInTrainingIndustry`——训练产业类（与示例工程 fixture 源码逐字一致）
