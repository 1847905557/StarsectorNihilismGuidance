# 制作可升级产业

本节制作一个可建设、升级并被外部中断的产业。示例会把产业接入市场经济循环，并在界面和日志中观察供需、升级与中断效果。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 在 `industries.csv` 中登记产业行，并解释从 CSV 行到运行时 `Industry` 实例的注册链；
- 区分 `Industry` / `BaseIndustry` 生命周期五个回调的时机与职责，并说出市场侧的两个驱动点；
- 用 `supply(...)` / `demand(...)` 在 `apply()` 里声明供需，用 `getMaxDeficit` / `applyDeficitToProduction` 实现缺货削减；
- 设置建设、升级与降级链，解释 `startUpgrading` → `finishBuildingOrUpgrading` 的完成行为与状态迁移；
- 安装与卸载 AI core，解释四个修饰钩子的成对撤销语义；
- 施放与解除 disruption，并解释它的内存键、中断表现与三个原版触发方；
- 从症状定位产业注册、供需、升级、AI core 与 disruption 的典型故障，并给出修正路径。

## 前置知识

- [C1-S1] 的 MOD 目录、命名约定与 `starsector.log` 日志位置；
- [C10-S2] 的经济 JSON、市场加载时机与 `industries` 数据结构；
- [C6-S2] 的插件生命周期与持久数据标记；
- [C6-S4] 的 settings 读取；
- [C7-S3] 的 `MutableStat` 与修饰值；
- [C9-S1] 的 `SectorAPI.getEconomy()`、生涯时钟与游戏日换算；
- [C9-S3] 的生涯对象一次性注册判断；
- [C10-S3] 的市场与产业访问入口、产业 ID 校验、经济循环节奏与监听器注册。

## 术语

按术语约定，本节首次完整讲解以下词：

- **产业 (industry)**：API 名保留 `Industry`。挂在一个市场下的 "生产/服务单元"，由静态 spec(`industries.csv` 行)+ 运行时实例 (plugin 列类名) 组成，承载供需、收入、维护费、AI core、特殊物品、改良与 disruption 等状态。与市场条件 (`MarketConditionAPI`) 并列但生命周期更重。
- **供需修饰 (supply/demand modifier)**：`MutableCommodityQuantity.getQuantity()` 上的 `MutableStat` flat 修饰。
    - 产业的 `supply(...)` / `demand(...)` 以 `"ind_<id>[_<n>]"` 为修饰 id 写入数量；
    - `supplyBonus` / `demandReduction` 是产业级 "产量加一 / 需求减一" 统计项，写入时合并为 `"ind_sb"` / `"ind_dr"`。
- **建设与升级 (building / upgrading)**：`isBuilding()` = 建设中 (含首次建设与升级)；`isUpgrading()` = 建设中且 `upgradeId != null`。`isFunctional()` = 非 disruption 且非 "建设中且未升级"。
- **AI core**：安装到产业上的三种商品化智能核心 (`alpha_core` / `beta_core` / `gamma_core`)，提供供需、维护费与 (自定义) 统计修饰，并提高 Luddic Path 的关注度 (`getPatherInterest`)。
- **disruption(中断)**：产业被外部行动 (突袭、审查) 打停的一段带倒计时的状态，存于市场 memory(key 含产业类名)。中断期间产业不产出、不推进建设。

## 梳理产业升级关系

本节的全部机制沿四个位置核对：**数据文件 → 注册入口 → 运行使用方 → 可观察结果**。先看产业如何从 CSV 行变成运行时对象，再依次进入生命周期、供需、建设升级、AI core 与 disruption 各链。

### 产业注册链 (industries.csv)

产业静态表是 `data/campaign/industries.csv`(含表头；原版 33 个活动产业，另有 1 个 `#` 注释行)。

### industries.csv 列速查表

15 个列按表头顺序为：

| 列 | `IndustrySpecAPI` 字段 | 语义 |
|---|---|---|
| `id` | `getId()` | 产业 spec key，全游戏唯一；`MarketAPI.addIndustry(id)` / `instantiateIndustry(id)` 按它查 spec |
| `name` | `getName()` | 显示名 (`getCurrentName()`) |
| `cost mult` | `getCost()` | 建设费用基础值 (`getBuildCost()`，可被 `setBuildCostOverride` 覆盖；升级/关停退款按它 × `industryRefundFraction`) |
| `build time` | `getBuildTime()` | 建设天数 (游戏日)；`startBuilding` / `startUpgrading` 时写入 |
| `income` | `getIncome()` | 收入基础值 (× 市场规模倍率后写入 `getIncome()` stat) |
| `upkeep` | `getUpkeep()` | 维护费基础值 (× 规模倍率 × 维护倍率后写入 `getUpkeep()` stat) |
| `downgrade` | `getDowngrade()` | 降级目标产业 id(`downgrade()` 使用) |
| `upgrade` | `getUpgrade()` | 升级目标产业 id(`startUpgrading()` 使用；UI 的 "升级" 按钮只在非空时出现) |
| `tags` | `getTags()` | 产业 tag；`industry` / `structure` 决定 `isIndustry()` / `isStructure()`(`Industries.TAG_INDUSTRY` / `TAG_STRUCTURE`)，`important` / `population` 等被 UI 与队列使用 |
| `data` | `getData()` | 附加数据字符串 |
| `image` | `getImageName()` | 产业贴图 (`getCurrentImage()`)；资源从全局资源集解析，MOD 可引用核心自带路径或自己的路径 |
| `plugin` | `getPluginClass()` | 产业 Java 类名；`getNewPluginInstance(market)` 用无参构造实例化 |
| `desc` | `getDesc()` | 描述文本 (tooltip 展示，支持 `$token` 替换) |
| `order` | `getOrder()` | 列表排序权重 |
| `disruptDanger` | `getDisruptDanger()` | `RaidDangerLevel` 枚举名 (NONE/MINIMAL/LOW/MEDIUM/HIGH/EXTREME)，突袭 "中断产业" 目标时长来源 |

注册链 (使用方视角的 "数据 → 注册" 半程)：

1. 加载器把每一行读成 `IndustrySpecAPI`，`id` 列成为 spec key(缺失/重复语义与 `hull_mods.csv` 的 `readCSVMerged` 一致：同文件重复 id 抛异常、跨文件重复后读行跳过 ——"产业不出现且无报错" 首选排查方向)；
2. `MarketAPI.addIndustry(id)` 或 `instantiateIndustry(id)` 从 CSV spec 加载期的注册表按 id 取 `IndustrySpecAPI`，查不到**抛 `RuntimeException("Industry [" + id + "] not found")`**(`Market.addIndustry`)。
    - 条件与产业都会校验 spec id；不存在时，添加入口立即抛出运行时异常；
3. `spec.getNewPluginInstance(market)` 无参实例化产业类 (`plugin` 列类名必须存在且可无参实例化，否则类加载失败)；
4. `addIndustry(id, params)` 带参数时先 `initWithParams(params)`(经济 JSON 的 `[id, param...]` 数组元素：识别 AI core id 与 params 匹配本产业 id 的特殊物品)；
5. 产业加入 `market.getIndustries()` 后**立即 `apply()`**——`addIndustry` 本身不校验 `isAvailableToBuild()`，代码路径可以给任何市场直接挂产业。

`SettingsAPI.getIndustrySpec(id)`(`com.fs.starfarer.api.SettingsAPI`) 是只读查询同一注册表的入口。

注册链只保证产业能被实例化；创建之后，每个产业还要按固定节奏重算供需、收入与建设状态，这由生命周期回调承担。

### 产业生命周期 (Industry / BaseIndustry)

`Industry`(`com.fs.starfarer.api.campaign.econ.Industry`) 是纯接口；`BaseIndustry`(`com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`) 是产业公共基类。

`BaseIndustry` 归属默认状态 (`supply` / `demand` map、`income` / `upkeep` stat、`building` / `buildProgress` / `upgradeId` / `aiCoreId` / `improved` / `special` 字段) 与各链路默认行为。

### 产业生命周期回调速查表

五个核心回调：

| 回调 | 时机 | 职责 |
|---|---|---|
| `init(String id, MarketAPI market)` | `getNewPluginInstance` 后、`apply` 前 | 绑定 id 与 market；`BaseIndustry.init` 同时 `readResolve()`(重取 spec、生成 `"ind_<id>"` / `"ind_<id>_<n>"` 修饰 id 族) |
| `apply()` | `addIndustry` 时、`reapplyIndustries` 每轮、tooltip 预览 (克隆市场) 时 | 写供需、收入/维护、统计修饰、AI core / 改良 / 特殊物品效果。子类先 `super.apply(true)` 再写自己的供需 |
| `unapply()` | `reapply` 前半、`removeIndustry`、`startBuilding` | 撤销 AI core 修饰 (`applyNoAICoreModifiers`)、改良、移民修饰、特殊物品效果 |
| `reapply()` | 外部直接调用或 tooltip 预览 | 语义固定为 `unapply()` + `apply()`("Calls unapply() and then reapply()") |
| `advance(float amount)` | 市场每帧 (`Market.advance` 对产业列表逐个调用) | `BaseIndustry.advance`：disruption 结束检测 (`wasDisrupted` 翻转 → `disruptionFinished()`)；建设中且未中断时用 `getClock().convertToDays(amount)` 累积 `buildProgress`，达到 `buildTime` 调 `finishBuildingOrUpgrading()` |

`BaseIndustry.apply(boolean withIncomeUpdate)` 的内部顺序：

1. `updateSupplyAndDemandModifiers()`：重置并汇总 `supplyBonus` / `demandReduction`(AI core、改良、管理员 `Stats.SUPPLY_BONUS_MOD` / `Stats.DEMAND_REDUCTION_MOD`、外部修饰)；
2. `withIncomeUpdate` 时 `updateIncomeAndUpkeep()`(收入/维护 stat 写入)；
3. `applyAICoreModifiers()`：按 `aiCoreId` 派发 `applyAlphaCoreModifiers` / `applyBetaCoreModifiers` / `applyGammaCoreModifiers`(无 core 时 `applyNoAICoreModifiers`)；
4. `applyImproveModifiers()`(改良状态)；
5. 移民修饰注册、特殊物品效果 (requirements 满足才 `effect.apply(this)`，否则 `effect.unapply(this)`)。

市场侧的两个驱动点 (`Market`)：

- `Market.advance(amount)`：**稳定度取整值变化时**触发 `reapplyConditions()` + `reapplyIndustries()` + `updatePrices()`。
    - 随后对 `new ArrayList<>(industries)` 逐个 `advance(amount)`—— 产业每帧推进挂在市场推进上；
- `Market.reapplyIndustries()`：`getAdmin().getStats().refreshGovernedOutpostEffects(this)` → 对每个产业 `unapply()` + `apply()`。
    - 随后 `PopulationAndInfrastructure.modifyUpkeepByHazardRating(market, "upkeep_hazard_mod")`(维护费 × 危险度修饰统一在产业重算尾部补上)。

必须遵守的规则：供需与统计修饰只能写在 `apply()` 里

`BaseIndustry.doPreSaveCleanup()` 在存档前把 `supply` / `demand` / `income` / `upkeep` 全部置 null，`doPostSaveRestore()` 重建空 map—— 供需只能由 `apply()` 每轮重建。只写在构造器或 `init` 里的供需，读档后全部消失且无报错。

这是本节第一条必须遵守的规则。

生命周期决定回调何时运行，供需链回答回调里写什么：产业对商品流的全部影响，都来自 `supply(...)` / `demand(...)` 写入 `MutableStat` 的数量修饰。

### 供需链

`BaseIndustry` 持有 `supply` / `demand` 两个 `Map<String, MutableCommodityQuantity>`；`getSupply(id)` / `getDemand(id)` 用到时才创建条目。

- **写入**：`supply(modId, commodityId, quantity, desc)` / `demand(...)` 在 `getQuantity()`(`MutableStat`) 上 `modifyFlat`；`quantity == 0` 时 `unmodifyFlat`("清掉这条供需" 的正式写法)。
    - 默认 modId 族 = `"ind_<id>_<n>"`(索引 0 即 `"ind_<id>_0"`)；无后缀 `"ind_<id>"` 是另一字段、不参与供需；`quantity > 0` 时把 `supplyBonus` / `demandReduction` 的生效值合并写入 `"ind_sb"` / `"ind_dr"`——**alpha core 的 "产量 +1" 就是这条路径**。
- **收入与维护**：`updateIncomeAndUpkeep()` → `applyIncomeAndUpkeep(size)`：`sizeMult = max(1, market.getSize() - 2)`。
    - `income = (int)(spec.getIncome() × sizeMult)` 写 `"ind_base"` flat + `"ind_stability"` mult(市场收入倍率)；`upkeep = (int)(spec.getUpkeep() × sizeMult)` 写 `"ind_base"` + `"ind_hazard"` mult(市场维护倍率)。
    - 随后 `applyAICoreToIncomeAndUpkeep()`(alpha/beta → `"ind_core"` × `UPKEEP_MULT = 0.75`，gamma 无)；**`!isFunctional()` 时收入修饰被移除** —— 建设中与中断中的产业不产生收入。
- **缺货**：`getMaxDeficit(commodityIds...)` 返回 `(商品, 缺口)`，缺口 = `getDemand(id).getQuantity().getModifiedInt() − market.getCommodityData(id).getAvailable()`。
    - `getAllDeficit(...)` 返回全部正缺口列表；`applyDeficitToProduction(index, deficit, commodities...)` 对列出的商品写负供给 (desc 为 "X shortage")。
    - **且只对已声明过供给的商品生效** (`if (getSupply(commodity).getQuantity().isUnmodified()) continue;`)。
- **读取 (经济步)**：经济步每次迭代重建商品可达图 (`CommodityMarketDataAPI` 构造器)：对每个市场、每个商品调 `updateMaxSupplyAndDemand()`(`CommodityOnMarketAPI.updateMaxSupplyAndDemand`)。
    - **maxSupply / maxDemand = 该市场所有产业中同商品供需的最大值** (不是总和)，同时记下该产业对 max 值的合法性判定 (`isSupplyLegal` / `isDemandLegal`)。
    - 主商品 (`CommoditySpecAPI.isPrimary()`) 的 maxDemand 还会沿 demand class 传播给同类的其他商品。
    - 价格与库存写入沿用市场与商品流主题已介绍的 `updateStockpileAndPriceV2`。
    - 需求侧定价按商品的 `demand class`(`commodities.csv` 的 `demand class` 列：`supplies` / `fuel` / `crew` / `organics` / `heavy_machinery` / `domestic_goods` / `drugs` 等) 聚合到 `MarketDemandAPI`。

背景：为什么商品流按 "最大值" 而非 "总和" 统计

多个产业可能供给同一商品 (例如本节示例与其它产业都产 `crew`)。

经济步的 `CommodityOnMarketAPI.updateMaxSupplyAndDemand` 对 "该市场所有产业中同商品" 取最大值作为 `maxSupply` / `maxDemand`，而不是把各产业数量相加 —— 市场总量以 "单一最强供给/需求" 为口径。

读商品流数字与定价时按这个口径理解，不要按总和预期。

可观察结果形成完整流程：产业 tooltip 的 Production / Demand & effects 区块 (`BaseIndustry.createTooltip`) 按供需 map 的修饰值渲染图标；经济 UI 的短缺/过剩按 `CommodityMarketDataAPI` 的结果显示。

供需让一个建成的产业参与经济；建设与升级链则决定产业如何从无到有、从低级到高级。

### 建设与升级链

状态字段：`building` / `buildProgress` / `buildTime` / `upgradeId`。

### 建设与升级入口速查表

| 入口 | 行为 (`BaseIndustry`) |
|---|---|
| `startBuilding()` | `building = true`、进度归零、`upgradeId = null`、`buildTime = spec.getBuildTime()`，然后 **`unapply()`**—— 建设中的产业立刻停摆 (`isFunctional()` = `!(building && !isUpgrading())`) |
| `advance(amount)` | `building && !disrupted` 时 `buildProgress += convertToDays(amount)`；达到 `buildTime` 调 `finishBuildingOrUpgrading()`。**中断期间建设暂停** |
| `finishBuildingOrUpgrading()` | 升级分支 (`upgradeId != null`)：`market.removeIndustry(getId(), null, true)` → `market.addIndustry(upgradeId)` → 把 `aiCoreId` / `improved` 迁移到新产业 → `upgradeFinished(previous)`(迁移特殊物品)→ 新产业 `reapply()`。首次建设分支：`buildingFinished()`(通知 + 队列下一项)→ `reapply()` |
| `startUpgrading()` | `building = true`、进度归零、`upgradeId = spec.getUpgrade()`、`buildTime = 升级 spec 的 buildTime`；**升级目标 spec 必须已注册**，否则 `getIndustrySpec(upgradeId)` 返回 null → 取 `getBuildTime()` 时 NPE |
| `downgrade()` | `building = true`、`upgradeId = spec.getDowngrade()`，立即 `finishBuildingOrUpgrading()`—— 降级走同一条完成链 (同样要求降级目标已注册) |
| `cancelUpgrade()` | 复位 `building` / 进度 / `upgradeId` |

UI 使用方 (产业交互对话框)：升级确认扣款后调 `startUpgrading()`；降级确认后调 `downgrade()` 并返还退款；关停调 `market.removeIndustry(id, interactionMode, false)`。

tooltip 的升级入口只在 `getSpec().getUpgrade() != null && !isBuilding()` 时出现 (`BaseIndustry.createTooltip`)。

建设队列 (`ConstructionQueue`，`com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue`)：`MarketAPI.getConstructionQueue()` 是队列容器，`addToEnd(id, cost)` 入队；

`Market.advance` 在 "队列非空且当前无在建产业" 时调 `BaseIndustry.buildNextInQueue(market)`：逐个弹出，`isAvailableToBuild()` 且未超产业上限才开工 (`addIndustry` + `startBuilding` + 成本 override)。

否则给用户退款并记 "Construction aborted" intel。

产业位上限：`Misc.getMaxIndustries(market)` = `market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).computeEffective(0)`(`Misc`)；

该 mod 由 `population` 产业 (`PopulationAndInfrastructure.apply`) 写入 `getMaxIndustries(size)`，读 settings `maxIndustries` 数组 (`[1,1,1,2,3,4,4,4,4,4]`，size 1–10 减一索引)——**size 1–3 的殖民地最多 1 个产业位**。

超出时 `PopulationAndInfrastructure` 施加稳定度惩罚 (`Misc.OVER_MAX_INDUSTRIES_PENALTY` = settings `overMaxIndustriesPenalty` = 5)。

`Misc.getNumIndustries(market)` 只统计 `isIndustry()` 的产业 (升级中的按升级目标算)。

`isAvailableToBuild()`：市场无 `MARKET_NO_INDUSTRIES_ALLOWED` tag、已有 `population` 产业且自身不是 `population`。

建设链会迁移产业的状态，AI core 正是其中最常被迁移的一种：它同时改供需、维护与 Luddic Path 关注度，而且修饰必须成对撤销。

### AI core 链

- 安装：`setAICoreId(String)` 写入 `aiCoreId`；所有原版产业的 `canInstallAICores()` 默认返回 true，包含 population。
    - UI 安装/卸下走 AI core 选择对话框 (产业交互对话框)。
- 应用：`apply()` → `applyAICoreModifiers()` 按 core 派发四个**空实现钩子**：`applyAlphaCoreModifiers` / `applyBetaCoreModifiers` / `applyGammaCoreModifiers` / `applyNoAICoreModifiers`。
    - 自定义核心效果写这里。
- 供需侧 (`updateAICoreToSupplyAndDemandModifiers`)：alpha → `supplyBonus +1` 且 `demandReduction +1`；beta / gamma → 仅 `demandReduction +1`。
    - "产量 +1 / 需求 −1 单位" 就是 `SUPPLY_BONUS = 1` / `DEMAND_REDUCTION = 1`。
- 维护侧 (`applyAICoreToIncomeAndUpkeep`)：alpha / beta → 维护费 × `0.75`(`"ind_core"` mult)；gamma → 无。
- 关注度：`getPatherInterest()`：alpha +4 / beta +2 / gamma +1，另加特殊物品的 pather tag 权重。
- 卸载/移除：`unapply()` 第一步调 `applyNoAICoreModifiers()`—— **自定义核心修饰必须在这里成对撤销**。
    - `notifyBeingRemoved(mode, forUpgrade)` 在非升级移除时把 `aiCoreId` 与特殊物品返还给 cargo(`REMOTE` 交互进存储、其余进用户舰队)。
- 管理员人物：`AICoreAdminPlugin`(`com.fs.starfarer.api.campaign.AICoreAdminPlugin`) 唯一方法是 `PersonAPI createPerson(String aiCoreId, String factionId, long seed)`。
    - 由实现类 (原版 `AICoreAdminPluginImpl`) 使用，用于把 AI core 变成殖民地管理员/官员。

### Disruption 链

状态不存产业对象，**存市场 memory**：

1. key：`getDisruptedKey()` = `"$core_disrupted_" + getClass().getSimpleName()`—— **按 Java 类名生成**，同一市场内共用同一 Java 类的多个产业 (不同 spec id) 共享同一个 key；

共用同一 Java 类的多个产业共享同一个 disruption key

同一市场内两个不同 spec id 但共用同一 plugin 类的产业 (原版 farming / aquaculture 都 `extends Farming`) 共享同一个 key：对其中一个施放 disruption，另一个也会被中断。这类产业必须覆写 `getDisruptedKey()` 区分 (如加 spec id 后缀)。

2. 施加：`setDisrupted(float days)` = `setDisrupted(days, false)`；`setDisrupted(days, useMax)` 先过 `canBeDisrupted()`(默认 true；`population` 覆写为 false)。
    - `useMax = true` 时取 `max(现有剩余, days)`(`MemoryAPI.getExpire(key)`)，`useMax = false` 直接覆盖。
    - `dur <= 0` 时 `memory.unset(key)`，否则 `memory.set(key, true, dur)`(带天数的过期标记)。
    - 从未中断变中断时调 `notifyDisrupted()`。
3. 读取：`isDisrupted()` = `memory.is(key, true)`；`getDisruptedDays()` = `memory.getExpire(key)`(负数归零)；
4. 结束：`advance(amount)` 检测 `isDisrupted()` 从 true 翻回 false → `disruptionFinished()`(两个钩子默认空实现)。

中断期间的可观察行为：`isFunctional()` 恒 false → 供需 map 里 "生产中" 的判定失效、`applyIncomeAndUpkeep` 移除收入；`getBuildOrUpgradeProgress()` 返回 0；`advance` 的进度累积被 `!disrupted` 条件跳过 (建设暂停)。

tooltip 显示红字 `Operations disrupted! X days until return to normal function.`；`getBuildOrUpgradeProgressText()` 显示 `Disrupted: N days left`，`getBuildOrUpgradeDaysText()` 中断时只返回 `N days`。

`getDisruptedDays()` 是剩余中断天数，不是剩余建设时间

建设剩余时间 = `buildTime − buildProgress`；中断期间建设暂停、进度冻结，要显示 "还剩多少天恢复" 应读 `getDisruptedDays()`。两套读数口径不同，直接混用会让 UI 数字与 tooltip 对不上。

三个原版触发方 (都在公开 API 面 `com.fs.starfarer.api.impl.campaign.*`)：

- **陆战队突袭**：`MarketCMD`(`com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD`) 的 "中断产业" 目标用 `DisruptIndustryRaidObjectivePluginImpl`。
    - 新增中断量 = `marineTokens × spec.getDisruptDanger().disruptionDays`，随后按既有中断天数施加阻尼，并把结果加到已中断时长；`disruptDanger` 列取值见 `RaidDangerLevel` 枚举 (NONE 60 / MINIMAL 50 / LOW 40 / MEDIUM 30 / HIGH 20 / EXTREME 10 天)；
- **Hegemony AI 审查**：`com.fs.starfarer.api.impl.campaign.intel.inspection.HIActionStage` 以 `curr.setDisrupted((intel.getRandom().nextFloat() * 45f) + 15f)` 施放 ——15–60 天；
- **Luddic Path 细胞**：`com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel` 以 `target.setDisrupted(disruptionDur, true)` 施放。
    - `useMax = true` 保留较长剩余时间。

AI core 提高 Luddic Path 的关注度，disruption 正是外部行动打断产业的方式 —— 它的状态存放位置与其他字段都不同。

## 规则强度

本节必须遵守的规则清单 ("必须/禁止"，均附违反后的可观察结果)：

### 规则强度速查表

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 产业 id 必须已在 `industries.csv` 注册后才可 `addIndustry` / `instantiateIndustry` / 用作 `upgrade` / `downgrade` 列目标 (`Market.addIndustry` 抛 `RuntimeException("Industry [...] not found")`；`startUpgrading` 对未注册目标 `getIndustrySpec` 返回 null 后取 `getBuildTime()` NPE) | 必须 | `addIndustry` 抛 `RuntimeException`、产业不出现且流程中断；升级启动瞬间 NPE；升级完成时 `addIndustry(upgradeId)` 抛异常 |
| 供需、收入/维护与统计修饰必须写在 `apply()` 里，禁止只在构造器 / `init` 写入 (`doPreSaveCleanup` 置空四个 map，`doPostSaveRestore` 重建空 map) | 必须 | 存档后读档，产业 tooltip 与商品流中的供需条目全部消失、无报错 |
| 想在缺货时被 `applyDeficitToProduction` 削减的商品，必须先 `supply(...)` 声明 (`if (getSupply(commodity).getQuantity().isUnmodified()) continue;`) | 必须 | 缺货时该商品产量不减、tooltip 无 shortage 削减显示 |
| 升级 / 降级必须由 `startUpgrading` / `downgrade` → `finishBuildingOrUpgrading` 驱动，其中 `removeIndustry(id, null, true)` 的 `forUpgrade` 必须为 true | 必须 | 自定义替换链传 `false` 时 mode 为 null、cargo 为 null，AI core / 特殊物品直接丢失；传错完成时机则队列与 intel 通知缺失 |
| AI core 自定义修饰必须成对：`applyAlpha/Beta/GammaCoreModifiers` 里加的修饰必须在 `applyNoAICoreModifiers` 撤销 (`unapply()` 第一步就是 `applyNoAICoreModifiers`) | 必须 | 卸下 core 或产业重算后修饰残留且反复叠加 (如稳定度 +1 永不消失) |
| 同一市场内共用同一 Java 类的多个产业 (如不同 spec id 同一 plugin 类)，必须覆写 `getDisruptedKey()` 区分 | 必须 | 对其中一个施放 disruption，同市场同类产业全部进入中断 (key 由类名生成)；原版 farming / aquaculture 共用 `Farming` 类即存在此语义 |
| 修改供需后不要期待当前帧的 `getAvailable()` / 价格立即变化 | 建议 | 数值在下一个经济步 (每月 10 次迭代) 才被抓取重算；要立即反映用 `EconomyAPI.forceStockpileUpdate(market)` |
| `getDisruptedDays()` 是 "剩余中断天数"，不是剩余建设时间 (建设剩余 = `buildTime − buildProgress`) | 必须 | 把中断天数当剩余建设时间显示，UI 数字与 tooltip 对不上 |
| 中断期间的产业收入被移除、建设暂停 (`applyIncomeAndUpkeep`；`advance`) | 必须 | 在 `advance` 里无视 disruption 继续累积 `buildProgress` 会与基类语义冲突 (进度在恢复后 "跳变") |

规则表之外，本节用一个可运行的产业把四条链串起来 —— 下面制作 `GearIn_training_industry`。

## 实现产业升级流程

产业必须先由 CSV 注册，再由 `BaseIndustry` 子类提供运行时行为。下面按注册、供需、缺货、AI core、中断和安装的顺序逐段接通。

### 注册产业规格

插入位置：MOD 自己的 `data/campaign/industries.csv`。ID、升级目标与 plugin 类名在这一行建立静态关系。

```text
GearIn_training_industry,GearIn Training Industry,20,60,,2,,militarybase,"training, industry, industrial",,graphics/icons/industry/light_industry.png,data.scripts.gearin.campaign.GearInTrainingIndustry,"A facility that trains serviceable crew.",250,LOW
```

完成信号：`SettingsAPI.getIndustrySpec("GearIn_training_industry")` 返回非 null，`getUpgrade()` 为 `militarybase`，plugin 类能无参实例化。

### 声明供需

插入位置：`GearInTrainingIndustry.apply`，紧接 `super.apply(true)`。市场规模同时决定训练耗材、教官和结业船员的数量。

```java
int size = market.getSize();
demand(Commodities.SUPPLIES, size, "Training operations");
demand(Commodities.CREW, Math.max(1, size / 2), "Instructors");
supply(Commodities.CREW, Math.max(1, size - 2), "Trained crew");
```

完成信号：size 3 市场显示 supplies 需求 3、crew 需求 1、crew 供给 1；保存读档后这些条目由 `apply()` 重建。

### 让耗材短缺削减产出

插入位置：供需声明之后。先从 supplies 需求计算最大缺口，再把缺口施加到已声明的 crew 供给；非功能状态清空生产。

```java
Pair<String, Integer> deficit = getMaxDeficit(Commodities.SUPPLIES);
applyDeficitToProduction(1, deficit, Commodities.CREW);

if (!isFunctional()) {
	supply.clear();
}
```

完成信号：supplies 可得量不足时 crew 产出下降；建设或中断期间 tooltip 不再显示有效产出。

### 成对应用 AI core 修饰

插入位置：`GearInTrainingIndustry` 的 AI core 钩子。两个方法使用同一个修饰 ID，安装 Alpha core 增加稳定度，卸下或重算时撤销。

```java {3,9}
@Override
protected void applyAlphaCoreModifiers() {
	market.getStability().modifyFlat(getModId(2), 1,
			"Alpha core (" + getNameForModifier() + ")");
}

@Override
protected void applyNoAICoreModifiers() {
	market.getStability().unmodifyFlat(getModId(2));
}
```

完成信号：安装 Alpha core 后稳定度增加 1，卸下后 `ind_GearIn_training_industry_2` 修饰消失，反复 reapply 不叠加。

### 施加并解除产业中断

插入位置：调试入口调用 `runDisruptionProbe`，其核心动作使用公开 `Industry` 接口。10 天中断写入后立即读取状态，再以 0 天解除。

```java {2,6}
Industry industry = market.getIndustry(INDUSTRY_ID);
industry.setDisrupted(10f, false);
LOG.info("disrupted=" + industry.isDisrupted()
		+ " days=" + industry.getDisruptedDays()
		+ " functional=" + industry.isFunctional());
industry.setDisrupted(0f);
```

完成信号：期间显示 `disrupted=true`、约 10 天、`functional=false`；解除后功能恢复，但 `getDisruptedDays()` 仍返回残留的中断时长 10.0。

### 在生涯市场安装产业

插入位置：既有 `GearInModPlugin.onGameLoad` 的独立 persistent data 判断块。先确认目标市场和 spec，再加入产业并运行观察助手。

```java
MarketAPI market = Global.getSector().getEconomy().getMarket("corvus_IIIa");
if (market != null
		&& Global.getSettings().getIndustrySpec(GearInTrainingIndustry.INDUSTRY_ID) != null
		&& !market.hasIndustry(GearInTrainingIndustry.INDUSTRY_ID)) {
	market.addIndustry(GearInTrainingIndustry.INDUSTRY_ID);
	GearInTrainingIndustry.logState(market);
	GearInTrainingIndustry.runDisruptionProbe(market);
}
```

完成信号：产业只安装一次，日志能读到 `upgrade=militarybase`，读档时不会重复添加。

## 组装产业升级实现

示例所有权：`GearInTrainingIndustry`(产业 id `GearIn_training_industry`，本节拥有)。

在既有示例的 MOD 布局与 `GearInModPlugin` 之上新增：一条 `industries.csv` 注册行 (数据文件，位于 `data/campaign/industries.csv`，用 CSV 合并语义提交自己的行)、一个演示类 (本节所有)。

先看数据侧的注册行 (自洽要求：`plugin` 列类名与本节 Java 类逐字一致，`upgrade` 列目标是已注册的原版 id `militarybase`—— 本节使用它演示升级链，不定义新 id)：

```text
GearIn_training_industry,GearIn Training Industry,20,60,,2,,militarybase,"training, industry, industrial",,graphics/icons/industry/light_industry.png,data.scripts.gearin.campaign.GearInTrainingIndustry,"A facility that puts recruits through a rigorous training regimen, turning idle hands into serviceable crew.",250,LOW
```

- `build time = 60`(游戏日)、`upkeep = 2`、`upgrade = militarybase`、`tags` 含 `industry`(`isIndustry()` 为 true，占产业位)、`disruptDanger = LOW`(40 天/令牌，见 `RaidDangerLevel`)；
- `image` 列用原版路径 `graphics/icons/industry/light_industry.png` 占位 (全局资源集解析，核心自带该贴图；本节无资产，MOD 发布时替换为自己的贴图)。

目录结构 (沿用既有示例页的布局，只新增加粗两项)：

```text {7,10}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── industries.csv      # 新增本节注册行 (CSV 合并，勿改核心文件)
├── src\
│   └── data/scripts/gearin/campaign\
│       └── GearInTrainingIndustry    # 本节新增 (归本节所有)
└── jars\
```

### GearInTrainingIndustry

对应关系：

- `implements` 侧：`extends BaseIndustry` 继承全部默认行为 (供需 map、收入/维护 stat、建设状态、AI core 派发、disruption memory 键)；类只写四类扩展点 —— `apply()` / `unapply()`、AI core 成对钩子、disruption 钩子、两个静态观察助手。
- `INDUSTRY_ID` 与 `industries.csv` 的 `id` 列、`plugin` 列与本类全名**三者逐字一致** (`Market.addIndustry` 按 id 查 spec、按 plugin 类名实例化)。
- `UPGRADE_TARGET_ID = "militarybase"`：只用于与 CSV 行互相核对，运行时不读取它 —— 升级链真正使用的是 `getSpec().getUpgrade()`(CSV `upgrade` 列)。
    - 升级完成时 `finishBuildingOrUpgrading` 会 `removeIndustry` + `addIndustry("militarybase")` 并迁移 AI core / 改良 / 特殊物品 (`BaseIndustry`)。
- `apply()` 里 `super.apply(true)` 先跑基类汇总 (供需 bonus、收入/维护、AI core、改良)，再写本产业的供需与缺货削减；`isFunctional()` 为 false(建设中/中断) 时清空供给。
- `applyAlphaCoreModifiers` / `applyNoAICoreModifiers` 用 `getModId(2)`(= `"ind_GearIn_training_industry_2"`) 成对加减稳定度：安装 alpha core 市场稳定度 +1，卸下/重算即撤销。
- `runDisruptionProbe` 全程用公开 API(`setDisrupted` / `isDisrupted` / `getDisruptedDays` / `setDisrupted(0f)`)。`setDisrupted(0f)` 走 `memory.unset(key)`：data 键被删，仅残留不可见的过期记录，`getDisruptedDays()` 仍返回中断时长 —— 所以探针的 `after` 行 days 显示 10.0，但市场内存里不再有 `$core_disrupted_GearInTrainingIndustry` 键 (DumpMemory 查不到)，属正常现象 (见验证方案核对)。

观察助手可以复用

`logState` 与 `runDisruptionProbe` 都是静态方法，不依赖本节调用点 —— 任何代码 (控制台、其它回调、调试入口) 都可以直接调用：`logState(market)` 打印本产业的供需/收入/建设/中断全状态；

`runDisruptionProbe(market)` 施放并立即清除 10 天 disruption，用于核对 `setDisrupted` / `isDisrupted` / `getDisruptedDays` 链路。

调用点 (工程片段 —— 方法所在类不属于本节所有权，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInTrainingIndustry.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInTrainingIndustry.REGISTER_MARK, true);
        MarketAPI market = Global.getSector().getEconomy().getMarket("corvus_IIIa");
        if (market != null) {
            market.addIndustry(GearInTrainingIndustry.INDUSTRY_ID);
            GearInTrainingIndustry.logState(market);
            GearInTrainingIndustry.runDisruptionProbe(market);
        }
    }
```

想走数据驱动的注册 (替代上面的代码安装)：

在 `data/campaign/econ\<星系>.json` 的目标市场条目 `industries` 数组里加一行 `"GearIn_training_industry"`(或 `["GearIn_training_industry", "alpha_core"]`—— 数组第二元素起由 `initWithParams` 识别安装 AI core / 特殊物品)。

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`(可用 `jar tf` 核对)。

注册行、类与调用点就绪后，按下面的方案验证整条链。

## 关键故障实验：添加产业时报告规格不存在

代码使用的 ID 与 `industries.csv` 注册行不一致。

**局部故障片段：**

```java
// 故障片段
market.addIndustry("GearIn_training");
```

**局部修正片段：**

```java {2}
// 修正片段
String industryId = GearInTrainingIndustry.INDUSTRY_ID;
if (Global.getSettings().getIndustrySpec(industryId) == null) {
	throw new IllegalStateException("Missing industry spec: " + industryId);
}
market.addIndustry(industryId);
```

实际症状：`Market.addIndustry` 抛出 `RuntimeException: Industry [GearIn_training] not found`，产业没有加入市场，后续 reapply、升级和中断代码均无法执行。

原因：运行时注册键是 CSV 的完整 ID `GearIn_training_industry`；`reapplyIndustries()` 只重放已经加入的实例，不会创建缺失 spec。

复验：修正后 `market.hasIndustry(INDUSTRY_ID)` 为 true，`getSpec().getUpgrade()` 返回 `militarybase`；完成 60 游戏日建设后可启动升级，中断探针能读取同一实例。

## 验证方案

前置数据：MOD 布局与 `GearInModPlugin`(含本节调用点片段)、`data/campaign/industries.csv` 注册行、`GearInTrainingIndustry` 类。

1. 游戏构建：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，`BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`。
    - 确认 `data/campaign/industries.csv` 合并后 `SettingsAPI.getIndustrySpec("GearIn_training_industry")` 返回非 null(可用日志或加载期检查)。
2. 代码安装：新建生涯，在 `starsector.log` 中观察产业安装与中断探针记录。
3. 读档：保存后读档，不出现重复安装日志 (`REGISTER_MARK` 已置位)。
4. 用户殖民地建设：殖民 (size 3 以下注意产业位上限 1)→ 产业列表出现 `GearIn Training Industry` → 建造 (建设期 `functional=false`)→ 60 游戏日后 `functional=true`。
    - 点击升级 → 完成后产业变为 `Military Base`。
5. AI core：安装 alpha core → `applied` 日志 `aiCore=alpha_core`、市场稳定度 +1；卸下 → 稳定度修饰消失。
6. disruption 实战：装 core 后等待 Hegemony AI 审查，或对殖民地执行陆战队 "中断产业" 突袭 → `notifyDisrupted` 日志 + tooltip `Operations disrupted!` + 收入消失。
    - 时间到 → `disruptionFinished` 日志 + 恢复。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInTrainingIndustry.class`；`data/campaign/industries.csv` 已含本节注册行 (CSV 合并，勿改动核心文件)。

- **代码安装 (corvus\_IIIa (Garnir)，size 3 海盗市场)**：新建生涯，`starsector.log` 依次出现。

    ```text
    GearIn training industry applied: market=Garnir size=3 supplyCrew=1 demandSupplies=3 demandCrew=1 income=0 upkeep=NN aiCore=none disrupted=false functional=true GearIn training industry state: market=Garnir id=GearIn_training_industry supply=[crew=1] demand=[supplies=3, crew=1] income=0 upkeep=NN buildProgress=0.0/60.0 building=false upgrading=false functional=true disrupted=false aiCore=none upgrade=militarybase
    ```

    数值核对：size 3 → `supplyCrew = max(1, 3-2) = 1`、`demandSupplies = 3`、`demandCrew = max(1, 3/2) = 1`；`income = 0`(spec 收入列空)；`upkeep = 2 × max(1, size-2) × 市场维护倍率`(以日志为准)；缺货削减只在 corvus\_IIIa (Garnir) 的 supplies 可得量不足 3 时把 `supplyCrew` 压到 0/负数区间 (tooltip 图标同步变红)。

- **disruption 探针** (同一批日志)：

    ```text
    GearIn training industry disruption probe: before disrupted=false days=0.0 GearIn training industry disruption probe: during disrupted=true days=10.0 functional=false progress=Disrupted: 10 days left GearIn training industry disruption probe: after disrupted=false days=10.0
    ```

    核对：`during` 行 `functional=false`(`isFunctional()` 在中断时恒 false)、`progress` 显示 `Disrupted: 10 days left`(`getBuildOrUpgradeProgressText` 的中断分支)；`setDisrupted(0f)` 后 memory data 键被 unset，仅残留不可见的 expire 记录，`after` 行 days 仍为 10.0。市场内存里**没有**残留的 `$core_disrupted_GearInTrainingIndustry` 键 —— 用 `DumpMemory` 核对应查不到该键。

- **建设与升级 (用户殖民地路径)**：用户殖民后产业列表出现 `GearIn Training Industry`(`isAvailableToBuild` 要求市场有 `population` 且无 `MARKET_NO_INDUSTRIES_ALLOWED` tag；size 1–3 的殖民地产业位上限 1，注意是否占满)；建造后 `applied` 日志的 `functional=false`(`isBuilding` 中，tooltip 显示 "Currently under construction and not producing anything")；`advance` 按 `convertToDays(amount)` 累积 60 个游戏日后完成，`applied` 日志恢复 `functional=true`。点击升级：确认扣款后 `startUpgrading()`，完成后产业被替换为 `Military Base`(`militarybase` 行)，AI core / 改良 / 特殊物品迁移 (`finishBuildingOrUpgrading`)。

- **AI core**：往产业装 alpha core(AI core 选择对话框)：`applied` 日志 `aiCore=alpha_core`；市场稳定度 +1(`applyAlphaCoreModifiers` 的 `ind_GearIn_training_industry_2` 修饰)；tooltip 的 alpha 描述显示维护费 −25%(`UPKEEP_MULT = 0.75`)、需求 −1、产量 +1(`DEMAND_REDUCTION` / `SUPPLY_BONUS = 1`)；卸下 core 后稳定度修饰消失 (`applyNoAICoreModifiers`)。`getPatherInterest` = 4(alpha) 会提高 Luddic Path 对该市场的兴趣。

- **中断的实战触发**：给装 core 的殖民地等待 Hegemony AI 审查 intel(15–60 天)，或对该市场执行陆战队 "中断产业" 突袭 (新增中断量以令牌数 × `disruptDanger.disruptionDays` 为基准，并按既有中断时长阻尼)——`notifyDisrupted` 日志出现，tooltip 显示 `Operations disrupted!`，收入消失、建设暂停；时间到后 `disruptionFinished` 日志出现，恢复。

- **读档**：不重复安装 (`REGISTER_MARK` 已置位)，不出现 `applied` / `state` / `probe` 日志；`doPreSaveCleanup` / `doPostSaveRestore` 之后供需由 `apply()` 重建 —— 读档后 `state` 日志 (手动触发) 的 supply/demand 与读档前一致。

- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

**预期结果**：以上操作与核对结果全部符合。

清理：调用 `market.removeIndustry(GearInTrainingIndustry.INDUSTRY_ID, null, false)`；产业移除后，"applied" 日志消失、稳定度修饰撤销，已安装的 alpha core 随产业移除直接丢失 (mode 为 null 时不返还)。

## 故障分析

验证方案中的故障注入直接复现对应症状；每项从实际日志或界面现象开始。

- **`addIndustry` 抛 `Industry [...] not found`**：CSV 缺少对应 ID、行被重复覆盖或调用 ID 拼错。

    - **解决方案**：用 `SettingsAPI.getIndustrySpec(id)` 确认注册，再逐字核对 CSV 与调用点。

- **读档后产业供需消失**：供需写在构造器或 `init`，而不是每次重算都会调用的 `apply()`。

    - **解决方案**：把供需与统计修饰移入 `apply()`。

- **缺货时产量没有减少**：缺货削减前没有先声明对应商品的 supply。

    - **解决方案**：先完成 `demand`/`supply`，再调用 `applyDeficitToProduction`。

- **升级启动或完成时报错**：`upgrade` 指向未注册产业 ID。

    - **解决方案**：补齐注册或修改升级目标，确认目标 MOD 已启用。

- **卸下 AI core 后效果仍然存在**：`applyAlphaCoreModifiers` 与 `applyNoAICoreModifiers` 没有成对撤销。

    - **解决方案**：为每个 AI core 修饰提供对应的移除逻辑。

- **升级时 AI core 或特殊物品被复制**：自定义代码调用了 `removeIndustry(..., false)`。

    - **解决方案**：让升级流程使用 `forUpgrade=true` 的原版链路。

- **同市场两个产业同时中断**：不同产业共用 Java 类却共用默认 disruption key。

    - **解决方案**：覆写 `getDisruptedKey()`，把 spec ID 纳入 key。

- **把 `getMaxDeficit` 当成总需求**：它返回的是 `max(0, demand - available)`。

    - **解决方案**：总需求读取 `getDemand(id).getQuantity().getModifiedValue()`。

- **界面把中断天数显示成建设剩余时间**：`getDisruptedDays()` 与建设进度是两套读数。

    - **解决方案**：分别使用中断天数和 `buildTime - buildProgress`。

- **修改供需后当前帧数值没有变化**：经济数据在经济步中抓取。

    - **解决方案**：等待一个经济步，或对目标市场调用 `EconomyAPI.forceStockpileUpdate(market)`。

- **`supply(com, 0)` 没有清掉全部供给**：0 只撤销当前 modId 的修饰。

    - **解决方案**：使用对应的 `unmodifyFlat`，并检查其他修饰来源。

## 总结

本节把产业讲成四条链：注册链让 CSV 行变成可实例化的 `Industry`，生命周期链决定状态何时重算，供需与建设升级链决定产业如何参与经济循环，AI core 与 disruption 链决定它的修饰与被打断的方式。条件与产业都会在添加时校验 spec id，升级与降级都走同一条完成链，disruption 状态存在市场 memory 而非产业对象里 —— 这三条是本节最容易被绕进去的地方。

记忆要点：

- CSV `id` 必须与 `addIndustry` 参数逐字一致；`upgrade` / `downgrade` 列目标必须已注册。
- `supply` / `demand` / 收入 / 维护只写在 `apply()`；缺货削减前先 `supply(...)`。
- `getModId(n)` = `"ind_<id>_<n>"`；AI core 修饰成对 (`applyXxxCoreModifiers` ↔ `applyNoAICoreModifiers`)。
- `setDisrupted(days, useMax)`：`useMax=true` 保留较大剩余；`setDisrupted(0)` 解除；同市场同 Java 类的多产业共享 disruption key。
- 产业位 = `maxIndustries[size-1]`(size 1–3 都是 1)；`isFunctional()` = 非中断 ∧ 非 "建设中且未升级"。

从逻辑上看，产业不是孤立对象：市场与商品流的经济循环 (经济步) 是产业供需的使用端，时钟与游戏日换算 (`getClock().convertToDays`) 是建设进度的换算来源，`MutableStat` 承载全部数量修饰；本节示例的 `REGISTER_MARK` 一次性标记沿用既有生涯注册示例的写法。产业写完并注册后，它在市场里的可见性 (tooltip 数字、商品流、价格、突袭中断) 全部由这些既有机制承接 —— 本节只负责把产业自身的四条链写对。

## 建议参考项

- Java API(包路径)：
    - `com.fs.starfarer.api.campaign.econ.Industry`—— 产业接口全貌：生命周期五回调、供需、建设/升级、AI core、disruption、特殊物品/改良。
    - `com.fs.starfarer.api.loading.IndustrySpecAPI`—— `industries.csv` 全部列字段的 getter/setter 与 `getNewPluginInstance` 无参实例化。
    - `com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry`—— 产业公共基类：`apply(boolean)` 顺序、供需写入与 `ind_sb` / `ind_dr` 合并、`applyDeficitToProduction`、`applyIncomeAndUpkeep`、`advance`、建设/升级/降级链、AI core 派发与四钩子、`getPatherInterest`、disruption 系列 (`getDisruptedKey` / `setDisrupted` / `getDisruptedDays` / `canBeDisrupted` / `isDisrupted`)、`doPreSaveCleanup` / `doPostSaveRestore`、`initWithParams`、`isAvailableToBuild`、tooltip 渲染。
    - `com.fs.starfarer.api.impl.campaign.econ.impl.LightIndustry`——`apply()` 标准写法范例 (先声明供需、再挂缺货削减)。
    - `com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure`——`MAX_INDUSTRIES` 写入、超限稳定度惩罚、`getMaxIndustries(size)`、`canBeDisrupted() = false`。
    - `com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue`—— 建设队列容器与 `addToEnd`；
    - `com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity`—— 商品 id + `MutableStat` 数量；
    - `com.fs.starfarer.api.campaign.AICoreAdminPlugin`——`createPerson(aiCoreId, factionId, seed)`，把 AI core 变成殖民地管理员/官员。
    - `com.fs.starfarer.api.impl.campaign.ids.Industries`——tag 常量 `TAG_INDUSTRY` / `TAG_STRUCTURE` / `TAG_POPULATION`。
    - `com.fs.starfarer.api.impl.campaign.ids.Commodities`—— `SUPPLIES` / `CREW` / `ALPHA_CORE` / `BETA_CORE` / `GAMMA_CORE`。
    - `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD`——`RaidDangerLevel.disruptionDays` 与突袭 "中断产业" 时长计算。
    - `com.fs.starfarer.api.impl.campaign.graid.DisruptIndustryRaidObjectivePluginImpl`—— 突袭中断目标的 `disruptDanger` 使用与 `setDisrupted` 施放。
    - `com.fs.starfarer.api.impl.campaign.intel.inspection.HIActionStage`——Hegemony AI 审查对产业施放 15–60 天 disruption。
    - `com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel`—— Luddic Path 细胞以 `setDisrupted(dur, true)` 施放并保留较长剩余。
    - `com.fs.starfarer.api.util.Misc`—— `getMaxIndustries` / `getNumIndustries` / `getCurrentlyBeingConstructed` / `OVER_MAX_INDUSTRIES_PENALTY`。
    - `com.fs.starfarer.api.SettingsAPI`——`getIndustrySpec(id)` 只读查询产业注册表；
    - `com.fs.starfarer.api.campaign.econ.MarketAPI`—— 产业访问族 (`addIndustry` / `getIndustry` / `hasIndustry` / `hasFunctionalIndustry` / `removeIndustry` / `instantiateIndustry`) 与 `getConstructionQueue`。
    - `Market`——`addIndustry` / `instantiateIndustry` / `removeIndustry` / `reapplyIndustries`、`advance` 稳定度重算与产业推进。
    - `CommodityOnMarketAPI`——`updateMaxSupplyAndDemand` 同商品取最大值与合法性、`getAvailable` / `getMaxSupply` / `getMaxDemand`。
    - `CommodityMarketDataAPI`—— 经济步使用产业供需 (`updateMaxSupplyAndDemand`、demand class 传播)。
    - 产业交互对话框 —— 升级/降级/关停 UI 使用方、退款计算。
- 原版数据 (相对路径)：
    - `data/campaign/industries.csv`—— 产业静态表：15 列表头与 `population` / `spaceport` / `militarybase` / `heavybatteries` 等原版行。
    - `data/campaign/commodities.csv`—— `demand class` 列 (supplies / fuel / crew / organics / heavy\_machinery / domestic\_goods / drugs 等)。
    - `data/campaign/econ/corvus.json`—— 市场 `industries` 数组写法 (纯 id 字符串与 `[id, param...]`)。
    - `data/config/settings.json`——`maxIndustries` 数组、`overMaxIndustriesPenalty`、`industryRefundFraction`、`industryImproveBase`。
- 本节示例：
    - `data/scripts/gearin/campaign/GearInTrainingIndustry`—— 训练产业类。

## 完整最小例

::: details 完整成品：GearInTrainingIndustry

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
 * 完整最小例：产业 (Industry) 注册、供需、升级、AI core 与 disruption 演示。
 *
 * 注册入口：industries.csv 一行 (plugin 列 = 本类全名，id = INDUSTRY_ID)。市场侧由
 * MarketAPI.addIndustry / instantiateIndustry 消费；演示安装点片段见页面正文
 * (GearInModPlugin 的 onGameLoad，persistent data 键 REGISTER_MARK 一次性门控)。
 *
 * 行为：
 *   1. apply() 声明供需：需求 SUPPLIES(训练耗材) 与 CREW(教官)，供给 CREW(结业船员)；
 *      supplies 缺货时按缺口削减船员产出 (applyDeficitToProduction)。
 *   2. 收入/维护由 spec(income=0, upkeep=2) 经 applyIncomeAndUpkeep 写入。
 *   3. AI core：alpha 额外给市场 +1 稳定度 (applyAlphaCoreModifiers / applyNoAICoreModifiers
 *      成对)；供需/维护侧走 BaseIndustry 默认 (alpha: 产量+1、需求-1、维护×0.75)。
 *   4. disruption：notifyDisrupted / disruptionFinished 打日志；setDisrupted 由外部触发
 *      (突袭、AI 审查、Luddic Path)，中断期间 isFunctional()=false、建设暂停。
 *
 * 演示全程可清理：removeIndustry(GearInTrainingIndustry.INDUSTRY_ID, null, false) 撤销全部修饰；
 * mode 为 null 时 AI core 直接丢失、不返还。
 */
public class GearInTrainingIndustry extends BaseIndustry {

    /** 产业 id：与 industries.csv 的 id 列逐字一致。 */
    public static final String INDUSTRY_ID = "GearIn_training_industry";

    /** 存档持久化标记：演示安装是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_TrainingIndustry";

    /** industries.csv upgrade 列目标 (原版产业 id，本节只消费不定义)。 */
    public static final String UPGRADE_TARGET_ID = "militarybase";

    private static final Logger LOG = Global.getLogger(GearInTrainingIndustry.class);

    @Override
    public void apply() {
        // 顺序：updateSupplyAndDemandModifiers -> updateIncomeAndUpkeep ->
        // applyAICoreModifiers -> applyImproveModifiers(BaseIndustry.apply(boolean))
        super.apply(true);

        int size = market.getSize();

        demand(Commodities.SUPPLIES, size, "Training operations");
        demand(Commodities.CREW, Math.max(1, size / 2), "Instructors");
        supply(Commodities.CREW, Math.max(1, size - 2), "Trained crew");

        // supplies 缺口会削减船员产出 (只对已声明供给的商品生效)
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

    // ---- AI core：自定义修饰必须成对 (apply / unapply 对称) ----

    @Override
    protected void applyAlphaCoreModifiers() {
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }

    // ---- disruption 钩子 (默认空实现，这里只打日志观察) ----

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
     * 观察助手：把目标市场里本产业的状态打一条日志 (含供需明细)。
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
     * 探针助手：对目标市场里本产业施放 10 天 disruption 并立即清除 (useMax=false 覆盖语义)，
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

:::
