# 生成并投放生涯舰队

本节根据参数生成一支生涯舰队，并把它投放到已经建立的地点容器中。示例会处理创建时的舰队规模与延迟填充，为巡逻、袭击等用途提供可复用入口。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 配置 `FleetParamsV3` 的来源市场与质量、六类点数、规模、军官与旗舰参数，并区分 "点数是目标不是硬上限" 的含义；
- 复述 `FleetFactoryV3.createFleet(params)` 的十步流程，并说出每步的可观察结果；
- 区分成员、旗舰与指挥官各自的生成规则：角色抽选、旗舰 picker 与 `setFlagship` 副作用、指挥官与军官的等级与数量；
- 解释 `FleetInflater` 的延迟膨胀语义、persistent 行为与膨胀结果 (武器 tier 概率、D 插/S 插、`REFIT` 来源)；
- 识别工厂写入舰队内存的键及其使用方，并用 `getMemoryWithoutUpdate()` 安全读写；
- 用 `LocationAPI.spawnFleet` 投放舰队，说明入容器、定位与免战的三合一语义及替代路径；
- 从日志与地图症状定位典型生成故障，并沿数据链给出核对路径。

## 前置知识

- [C6-S2] 的 `onGameLoad` 生命周期与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义；
- [C9-S1] 的生涯脚本注册与一次性判断；
- [C9-S2] 的地点容器、实体加入移除与市场实体定位；
- [C9-S3] 的舰队成员、`FleetDataAPI`、旗舰、指挥官、军官与成员变体读写；
- [C11-S1] 的人物、captain、officer、`setCaptain` 与等级读取；
- [C11-S3] 的 `VariantSource.REFIT`、`setVariant` 与 `originalVariant` 来源语义。

## 术语

按术语约定，本节首次完整讲解以下词：

- **生涯舰队工厂 (fleet factory)**：API 名保留 `FleetFactoryV3`(含参数对象 `FleetParamsV3`)。以 "点数预算 + 势力 + 舰队类型" 为输入、产出完整 `CampaignFleetAPI` 的一族原版工具类。同一族还有固定模板 `FleetFactory`(V1) 与旧参数对象 `FleetParams`。
- **舰队类型 (fleet type)**：API 名保留 `FleetTypes`。标识舰队用途的 id(如 `patrolSmall` / `patrolMedium` / `taskForce`)，用于取舰队显示名 (`FactionAPI.getFleetTypeName`，数据 `default_fleet_type_names.json`) 并写入内存 `$fleetType`。
- **旗舰 (flagship)**：API 名保留 `flagship`(`FleetMemberAPI.isFlagship` / `CampaignFleetAPI.getFlagship`)。舰队的代表成员；工厂里从最大 hull size 成员中抽选，`setFlagship` 会联动舰长。
- **舰队指挥官 (fleet commander)**：API 名保留 `commander`(`CampaignFleetAPI.getCommander` / `setCommander`)。统率全舰队的 `PersonAPI`；`setFlagship` 后旗舰舰长即指挥官。
- **膨胀 (inflate)**：API 名保留 `FleetInflater`。把 "生成态" 成员 (装配仍是角色变体原样) 按势力可用武器/战机/船插与质量随机补全为完整装配态的延迟过程。
- **市场 (market)**：API 名保留 `MarketAPI`。星系中的经济活动节点，持有势力、位置与生产质量；`FleetParamsV3.source` 从市场取得势力、坐标与质量，投放时以 `market.getPrimaryEntity()` 作锚点。
- **投放 (spawn)**：把已创建的舰队实体加入地点容器并定位的过程；本节特指 `LocationAPI.spawnFleet`。

## 梳理舰队投放关系

正文沿生成链组织：先配置参数对象，再走工厂流程，随后展开角色、军官、膨胀、内存与投放。每节先给结论，再讲机制与可观察结果。

### 参数对象：FleetParamsV3

`FleetParamsV3`(`com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`) 是**公开字段**的参数类，无 getter/setter。

**"Fleet point values are targets not hard limits."**(点数是目标不是硬上限)。

**"'Pts' are fleet points in this implementation, unlike FleetFactoryV2."**(V3 的 pts 就是 fleet points，与旧版不同)。

### FleetParamsV3 全参数分组速查表

字段全览如下：

| 分组 | 字段 | 语义 |
|---|---|---|
| 来源与质量 | `source`(`MarketAPI`)、`locInHyper`(`Vector2f`)、`quality`、`qualityMod`、`qualityOverride` | `source` 的 "Use setSource() to set the source or directly call updateQualityAndProducerFromSourceMarket() to set the quality. Otherwise, the market's quality won't get used." |
| 身份 | `factionId`、`fleetType`、`factionOverride` | 势力与舰队类型；`factionOverride` 非 null 时舰队势力直接用该对象 (名称仍按 `fleetType` 取) |
| 点数 | `combatPts` / `freighterPts` / `tankerPts` / `transportPts` / `linerPts` / `utilityPts` | 六类预算，`getTotalPts()` 求和 |
| 规模 | `maxShipSize`("0: fighter, 4: capital"，默认 1000)、`minShipSize`("only affects combat ships; smaller ships will still be added with remaining fleet points")、`maxNumShips`、`ignoreMarketFleetSizeMult`、`onlyApplyFleetSizeToCombatShips`、`doNotPrune`、`doNotAddShipsBeforePruning` | 船体等级与总数约束、市场规模乘数开关、裁剪开关 |
| 军官 | `withOfficers`(默认 `true`)、`officerLevelBonus`、`officerNumberBonus`、`maxOfficersToAdd`、`officerNumberMult`(默认 1)、`officerLevelLimit`、`commanderLevelLimit`、`commander`(`PersonAPI`)、`noCommanderSkills` | 指挥官与军官生成参数 |
| 旗舰 | `flagshipVariantId`(`String`)、`flagshipVariant`(`ShipVariantAPI`)、`onlyRetainFlagship` | 旗舰装配覆盖与 "只留旗舰" |
| 随机与 doctrine | `random`、`timestamp`、`doctrineOverride`、`modeOverride`(`ShipPickMode`)、`forceAllowPhaseShipsEtc`、`treatCombatFreighterSettingAsFraction`、`blockFallback`、`aiCores`、`doNotIntegrateAICores` | 随机源、时间戳 (武器可用性判定)、教条覆盖、抽选模式覆盖等 |
| 装配杂项 | `allWeapons`、`averageSMods`(`Integer`，null = 不加 S 插)、`addShips`(`List<String>`，"If non-null: these ship variants will be added to the fleet before anything else") | 膨胀参数与显式预置舰船 |

### FleetParamsV3 构造器速查表

三个构造器族 (内部都走 `init`)：

| 构造器 | 行为 |
|---|---|
| `(MarketAPI source, String fleetType, 六点数..., float qualityMod)` | `init(source, ...)`：`locInHyper = source.getLocationInHyperspace()`、`factionId = source.getFactionId()`、`timestamp = Global.getSector().getClock().getTimestamp()`、`updateQualityAndProducerFromSourceMarket()`(质量 = `Misc.getShipQuality(source, factionId)`) |
| `(MarketAPI source, Vector2f locInHyper, String factionId, Float qualityOverride, String fleetType, 六点数..., float qualityMod)` | 同上，另显式设 `locInHyper` / `factionId` / `qualityOverride` |
| `(Vector2f locInHyper, String factionId, Float qualityOverride, String fleetType, 六点数..., float qualityMod)` | 无市场版本：只写字段；`createFleet` 在无 source 且附近无同势力市场时建立 fake market 并重算质量 |

`setSource(MarketAPI source, boolean updateQuality)`：换来源市场；`updateQuality=true` 时重算质量。

`updateQualityAndProducerFromSourceMarket()`：`quality = Misc.getShipQuality(source, factionId)`。

其中 `Misc`(`com.fs.starfarer.api.util.Misc`) 转发 `ShipQuality.getShipQuality`。

后者的完整类型名是 `com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality`。

市场生产质量 (`QualityData.quality` 计算值)+ 市场 `FLEET_QUALITY_MOD`，再减市场势力的 `doctrine.getShipQualityContribution()`、加目标势力的 `doctrine.getShipQualityContribution()`。

旧参数对象 `FleetParams`(V1，`com.fs.starfarer.api.impl.campaign.fleets.FleetParams`)：

**"'Pts' are not fleet points; 1/2/4/8 points = frigate/destroyer/cruiser/capital"**。

V1 的 pts 是尺寸当量不是 fleet points，V3 修正了这一点。

历史实现不使用，本节只在区分新旧时提及。

参数对象准备好了，接下来看工厂如何使用它 —— `FleetFactoryV3.createFleet` 的十步流程：

### 生成流程：FleetFactoryV3.createFleet

`FleetFactoryV3.createFleet(FleetParamsV3 params)`(`com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`) 返回已挂载 inflater 的完整舰队。十步流程：

1. **插件扩展点**：`Global.getSector().getGenericPlugins().pickPlugin(CreateFleetPlugin.class, params)` (`com.fs.starfarer.api.plugins.CreateFleetPlugin`) 非 null 时直接转交插件实现并返回 —— 默认逻辑只在未注册该插件时执行。
2. **市场选择** (`pickMarket`)：`source` 非 null 直接用；否则用 `locInHyper` 就近找同势力市场 (独立势力/掠夺者/`CUSTOM_SPAWNS_AS_INDEPENDENT` 势力放宽为 "不对独立势力敌对" 即可)。
    - 优先找 `getSize() >= getMinPreferredMarketSize(params)` 的市场 (`getMinPreferredMarketSize`：总点数 ≤20 → 1、≤50 → 3、≤100 → 5、≤150 → 7、更大 → 8)。
    - `source` 与 `locInHyper` 都为空 → 建 **fake market**：`createMarket("fake", "fake", 5)` + `getStability().modifyFlat("fake", 10000)` + `FLEET_QUALITY_MOD + BASE_QUALITY_WHEN_NO_MARKET(0.5)` + `COMBAT_FLEET_SIZE_MULT 1f`，用完把 `params.source` 还原为 null。

fake market 意味着什么

fake market 只提供 `0.5` 的基础质量修正；最终质量仍会经过市场质量计算，不能把它当作固定 `0.5`，也不能据此断言舰队必定满船 D 插。

3. **空舰队创建** (`createEmptyFleet(factionId, fleetType, market)`)：`Global.getFactory().createEmptyFleet(factionId, faction.getFleetTypeName(fleetType), true)`(`FactoryAPI.createEmptyFleet`，`aiMode=true`—— 关闭补给/燃料消耗、事故与船员需求)。
    - 写内存 `$fleetType`(`MemFlags.MEMORY_KEY_FLEET_TYPE`) 与 `$sourceMarket`(`MEMORY_KEY_SOURCE_MARKET`；fake 市场除外)。
    - 给所有 `AbilitySpecAPI.isAIDefault()` 的能力 `fleet.addAbility(id)`。
    - `Misc.getSalvageSeed(fleet)`(写内存 `$salvageSeed`，`MemFlags.SALVAGE_SEED`)。
    - `fleet.getFleetData().setOnlySyncMemberLists(true)`(生成期免同步，收尾时还原)。
4. **doctrine 拆分**：`factionId` 缺省取 `source.getFactionId()`。
    - `ShipPickMode mode = Misc.getShipPickMode(market, factionId)`(质量数据供应商市场为 null 或供应商势力与目标势力不同 → `IMPORTED`，否则 `PRIORITY_THEN_ALL`；`modeOverride` 可覆盖)。
    - `combatPts` 按 `doctrine.getWarships/getCarriers/getPhaseShips` 权重 (各加 `random.nextInt(3)-2` 抖动；`isStrictComposition()` 时不抖、各减 1) 拆成 `warships/carriers/phase` 三份，余数并入 warships。
    - **`banPhaseShipsEtc`** = 非玩家势力 ∧ `combatPts < FLEET_POINTS_THRESHOLD_FOR_ANNOYING_SHIPS(50)`—— 小舰队禁用 "烦人" 舰 (相位与特定船，`forceAllowPhaseShipsEtc=true` 可放开)。
5. **点数投放**：`params.addShips` 预置变体优先 (`addToFleet(ShipRolePick)`：`createFleetMember(SHIP, variantId)` + 起名 + `addFleetMember`，返回 fleet point cost 扣预算)。
    - `treatCombatFreighterSettingAsFraction` 时按 `combatFreighters = min(freighterPts, warships)*1.5 × getCombatFreighterProbability()` 加战斗货船；随机命中概率时按 `min(freighterPts, warships)*1.5`。
    - `addCombatFleetPoints`(warship/carrier/phase 三路 picker，按 `BASE_COUNTS_WITH_4`/`MAX_EXTRA_WITH_4` 尺寸计数表逐轮抽选，`maxShipSize`/`minShipSize` 过滤，`PRIORITY_THEN_ALL` 时优先抽 priority capital)。
    - 随后 `addFreighterFleetPoints` / `addTankerFleetPoints` / `addTransportFleetPoints` / `addLinerFleetPoints` / `addUtilityFleetPoints`(都是 `addPriorityOnlyThenAll`，有 priority 舰只用 priority)。
6. **超员裁剪**：成员数 > `maxShipsInAIFleet`(`settings.json` 中 = 30) 且未 `doNotPrune` → `pruneFleet`。
    - 按 combat/civilian FP 比例分配保留名额 (民用不足时至少保留 2+ 个名额)，五类列表按 hull size 降序、按 4:2:1:1(capital:cruiser:destroyer:frigate) 加权抽选。
    - 保留后 FP 仍超目标时用较小船替换较大船。
    - 最后 `fleet.getFleetData().sort()`。
7. **指挥官与军官** (`withOfficers=true` 时)：抽选旗舰并生成指挥官与军官。
8. **旗舰变体**：`fleet.getFlagship()` 非 null 时，`flagshipVariantId` 优先 (`Global.getSettings().getVariant(id)`；未注册 id 抛 `Ship hull variant [...] not found!`)，否则 `flagshipVariant`。
    - `fleet.getFlagship().setVariant(variant, false, true)`(非战机成员换装配会连船体一起换)+ `updateNumStatusesFromMember()`。
    - `onlyRetainFlagship=true` 时移除除旗舰外所有成员。
9. **收尾**：`fleet.forceSync()`；所有成员 `getRepairTracker().setCR(getMaxCR())`(新舰队 CR 直接拉满)。
    - `Misc.setSpawnFPMult(fleet, actualPoints / max(1, requestedPoints))`(写内存 `$spawnFPMult`，`FleetFactoryV3.KEY_SPAWN_FP_MULT = "$spawnFPMult"`；读取 `Misc.getSpawnFPMult`，`com.fs.starfarer.api.util.Misc`；使用方：`BaseRaidStage` 还原 "请求 FP" 判断)。
10. **inflater 挂载**：`DefaultFleetInflaterParams p`：

- `quality`(= `params.quality + params.qualityMod`，`qualityOverride` 非 null 时直接取 `qualityOverride`—— **质量优先级：qualityOverride > (quality + qualityMod)**)。
- `averageSMods`(传参时)、`persistent = true`、`seed = random.nextLong()`、`mode`、`timestamp`、`allWeapons`、`doctrineOverride` 时 `rProb = doctrine.getAutofitRandomizeProbability()`、`factionId`。
- `Misc.getInflater(fleet, p)`(`com.fs.starfarer.api.util.Misc`：`Global.getSector().getPluginPicker().pickFleetInflater(fleet, params)`)→ `fleet.setInflater(inflater)`。

### 成员与角色

流程第 5 步按角色填舰。成员的来源是**角色 (role) 抽选**，不是直接指定变体：

- 角色数据：`data/world/factions/default_ship_roles.json`(键如 `combatSmall`、`fastAttack`、`freighterSmall`)+ 各势力 `*.faction` 文件的 `shipRoles` 覆写。
    - `combatSmallForSmallFleet` 是 `combatSmall` 的 "剔除了低数量时难缠舰船" 副本。
- 调用链：`FleetFactoryV3.addToFleet(String role, ...)` → `market.pickShipsForRole(role, factionId, new ShipPickParams(mode, maxFP, timestamp, blockFallback), random)`(`FactionAPI.ShipPickParams`；`ShipPickMode`：`ALL` / `PRIORITY_ONLY` / `PRIORITY_THEN_ALL` / `IMPORTED`)→ `List<ShipRolePick>`。
    - 逐个 `addToFleet(ShipRolePick)`：`Global.getFactory().createFleetMember(FleetMemberType.SHIP, pick.variantId)`(成员即保存态成员)→ `fleet.getFleetData().pickShipName(member, random)` 起名 → `addFleetMember`。
- 快捷模板 `FleetFactory`(V1，同目录)：
    - `createPatrol(market, PatrolType.FAST/COMBAT/HEAVY)`(手写 `faction.pickShipAndAddToFleet(ShipRoles.*, ShipPickParams.all(), fleet)` 组合，`FactionAPI.pickShipAndAddToFleet`)+ `setAIMode(fleet, true)`；
    - `createMerc(...)`；
    - `createGenericFleet(factionId, name, qualityFactor, maxFP)`(`WeightedRandomPicker` 角色池 + 满载循环 + 无旗舰时补 `tempest_Attack`)。
    - V1 的 `setAIMode(fleet, true)` 其实只是 `forceSync()`(注释掉的补给装载代码)；V3 的 AI mode 在 `createEmptyFleet(..., true)` 创建时就定了。

### 旗舰与指挥官 (addCommanderAndOfficersV2)

成员进队之后，流程第 7 步决定谁当旗舰、谁统率全舰队：

`FleetFactoryV3.addCommanderAndOfficersV2`(入口 `addCommanderAndOfficers`，另有 `GenerateFleetOfficersPlugin` 扩展点优先)。`withOfficers=true` 且成员非空时执行：

- **候选过滤**：跳过战机联队、旗舰、平民 (`putOfficersOnCivShips=false` 时)、已有非默认舰长的成员；记录最大 hull size；按 `getFleetPointCost()` 加权把成员塞进两个 picker(`flagshipPicker` 只收尺寸 ≥ 最大尺寸的成员)；picker 为空时取 `members.get(0)` 作备用值。
- **旗舰**：`flagshipPicker.pickAndRemove()` 选出旗舰，从军官 picker 移除。
    - **`fleet.getFleetData().setFlagship(flagship)`**——`FleetDataAPI.setFlagship` 的副作用：清所有成员旗舰位、把旧旗舰/舰长是玩家的人物重置为默认舰长、新旗舰舰长 = 舰队指挥官 (`setCaptain(getCommander())`)。
- **指挥官**：`params.commander` 非 null 直接用 (`isPlayer()` 时军官数量改由玩家 `getOfficerNumber()` 技能决定)。
    - 否则 `OfficerManagerEvent.createOfficer(faction, commanderLevel, pref, false, null, true, true, -1, random)`，等级 = `min(maxOfficerLevel, maxAIFleetCommanderLevel)`(`settings.json` 中 `maxAIFleetCommanderLevel` = 7；`commanderLevelLimit` 非 0 可覆盖)。
    - **`timid` personality 一律升为 `cautious`**；`params.commander` 为空时 `setRankId(Ranks.SPACE_COMMANDER)` + `setPostId(Ranks.POST_FLEET_COMMANDER)`。
    - `addCommanderSkills`：`noCommanderSkills=true` 直接返回；技能数按等级阈值 `commanderLevelForOneSkill`(`settings.json` 中 = 4)/ `commanderLevelForTwoSkills`(`settings.json` 中 = 6) 取 1/2；技能池 = `doctrine.getCommanderSkills()` 剔除 `Skills.TAG_PLAYER_ONLY`，`commanderSkillsShuffleProbability` 概率洗牌后逐个 `setSkillLevel(id, 1)`。
- **军官数量**：`numOfficers = min(maxOfficers, combatShips / combatShipsPerOfficer)`。
    - `maxOfficers = officerAIMax`(`settings.json` 中 = 10)+ `(int)(doctrine.getOfficerQuality() * officerAIMaxMercsMult) + officerNumberBonus`；
    - `combatShipsPerOfficer = baseCombatShipsPerOfficer`(`settings.json` 中 = 3)\* `(1 - officerQualityMult*0.5)`；再加 `officerNumberBonus`、乘 `officerNumberMult`、受 `maxOfficersToAdd` 钳制。
    - **军官等级上限** `maxOfficerLevel = round(doctrine.getOfficerQuality()/2 + fleetSizeOfficerQualityMult * officerMaxLevel(5)) + officerLevelBonus`，`officerLevelLimit` 非 0 时覆盖；每个军官 `maxOfficerLevel - random.nextInt(3)`(≥1)。
- **技能偏好** (`getSkillPrefForShip`)：按武器槽尺寸加权 (LARGE×4/MEDIUM×2/SMALL×1) 统计能量/实弹/导弹占比与护盾/相位判定，合成 `SkillPickPreference` 枚举名 (`YES_ENERGY_NO_BALLISTIC_...`) 喂给 `createOfficer`。
- **上舰**：逐成员 `member.setCaptain(person)`(生涯侧舰长写入并重算成员 stats)；`params.commander.isPlayer()` 时额外 `fleet.getFleetData().addOfficer(person)` 入 roster。

### Inflater：膨胀语义

到这一步，成员表、旗舰与指挥官都已就位，但每艘船的装配仍是角色变体原样 —— 真正按势力武器与质量补全发生在膨胀：

`FleetInflater`(`com.fs.starfarer.api.campaign.FleetInflater`) 四成员：

- `inflate(CampaignFleetAPI)`
- `removeAfterInflating()` / `setRemoveAfterInflating(boolean)`
- `getParams()` / `getQuality()`
- `getAverageNumSMods()`

**延迟膨胀**：`createFleet` 只 `setInflater`**，不**立即膨胀。

`CampaignFleetAPI.inflateIfNeeded` 的执行条件与流程：

- 条件：`inflater != null && inflated == null && !faction.isNeutralFaction()`。
- 执行 `inflater.inflate(fleet)`，随后 `ListenerUtil.reportFleetInflated`。
- `removeAfterInflating()` 为 true 则 `inflater = null`，最后置 `inflated = true`。

触发点都在 "需要完整装配" 的时刻：AI 战术模块、实体工具提示等。

**`removeAfterInflating()` = `p.persistent == null || !p.persistent`**(`DefaultFleetInflater`)。

`persistent=true`(V3 默认) 时膨胀后 inflater 保留；`persistent=null` 时膨胀即拆。

膨胀前读到的是生成态装配

在 `inflateIfNeeded()` 之前读 `member.getVariant()`：武器仍是角色变体原样、无 D 插/S 插 (`source` 仍是 STOCK/生成时来源，非 REFIT)。引擎只在 AI/工具提示/战斗前自动触发膨胀 —— 需要完整装配时请先手动触发，不要在错误时机核对装配内容。

`DefaultFleetInflater.inflate`(`com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater`) 行为要点：

- **可用池**：武器 = `faction.getKnownWeapons()` 过滤 `isWeaponKnownAt(id, timestamp)`，战机同理；按 tier/autofit 分类与 "priority"(`faction.isWeaponPriority`) 分组；船插 = `faction.getKnownHullMods()`。
- **质量驱动**：`getTierProbability(tier, quality)`——tier1 `min(0.9, 0.75+q)`、tier2 `min(0.9, 0.5+0.5q)`、tier3 `min(0.9, 0.25+0.25q)`，`allWeapons=true` 时全 1。
    - 每成员用 `CoreAutofitPlugin.doFit(currVariant, target, maxSmods, this)` 重新装配。
- **D 插**：`averageDmods = (1 - quality) / qualityPerDMod`(`settings.json` 中 `qualityPerDMod` = 0.2)。
    - 每成员 `getNumDModsToAdd = round(avg + random*3 - 2)` 钳 \[0,5\] 减已有 → `DModManager.setDHull` + `addDMods`。
    - **站 (station) 不加 D 插**。
- **S 插**：`averageSMods` 参数非 null 且非平民时，`getMaxSMods` = `averageSMods ± 1`(各 25% 概率) 钳 \[0,3\]，扣已有 S 插。
- **随机化**：`rProb = doctrine.getAutofitRandomizeProbability()`(`doctrineOverride` 时用其值) 概率开启 `RANDOMIZE`；升级概率 `min(0.1 + 0.5q, 0.5)`。
    - `p.seed` 非 null 时每成员 `random = new Random(p.seed * shipName.hashCode())`—— **同种子重膨胀结果一致** (注释原文："need this so that when reinflating a fleet that lost members, the members reinflate consistently")。
- **结果写回**：每成员建空变体 `createEmptyVariant(fleet.getId() + "_" + index, target.getHullSpec())`，`target.isStockVariant()` 时 `setOriginalVariant(target.getHullVariantId())`。
    - `doFit` 后 `setSource(VariantSource.REFIT)` + `member.setVariant(currVariant, false, false)`。
    - `memberIndex` 逐成员递增。
- **跳过**：`TAG_NO_AUTOFIT` / `TAG_NO_AUTOFIT_UNLESS_PLAYER`(非玩家势力)tag 的 hull/变体跳过；`$overrideNoAutofit`(`MEMORY_KEY_FORCE_AUTOFIT_ON_NO_AUTOFIT_SHIPS`) 为 true 时强制全膨胀。
- 结尾 `setSyncNeeded()` + `syncIfNeeded()`。

`CampaignFleetAPI.deflate`：仅非玩家舰队。

成员变体 `originalVariant` 非 null 且非 no\_autofit 时 `setVariant(注册表原变体, false, false)` 换回，然后 `inflated = null` + 同步。

**deflate 后再次触发 inflateIfNeeded 会按种子重膨胀**。

### 内存 (memory)

### 内存 (memory)

### 舰队内存键速查表

生成链的各个环节把关键状态写进舰队内存，集中列在下表；读写都经 `getMemoryWithoutUpdate()`：

| 键 (MemFlags 常量) | 写入点 | 使用方 |
|---|---|---|
| `$fleetType`(`MEMORY_KEY_FLEET_TYPE`) | `FleetFactoryV3.createEmptyFleet`；`FleetFactory.createEmptyFleet` | 舰队类型判定 (巡逻/袭击等行为脚本) 与调试 |
| `$sourceMarket`(`MEMORY_KEY_SOURCE_MARKET`) | 同上 (fake 市场除外) | 来源市场 id(重生/补给逻辑) |
| `$salvageSeed`(`SALVAGE_SEED`) | `Misc.getSalvageSeed(fleet)`(createFleet 调用即写入；`com.fs.starfarer.api.util.Misc`：种子缺失时按实体 id 哈希派生) | 掠夺/回收随机 |
| `$spawnFPMult`(`FleetFactoryV3.KEY_SPAWN_FP_MULT`) | `Misc.setSpawnFPMult(fleet, actual/requested)`(createFleet 收尾；`Misc`) | `BaseRaidStage.enoughMadeIt`(`com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage`)：`fleet.getFleetPoints() / mult` 还原 "请求 FP" 判断是否达到中止阈值 |
| `$overrideNoAutofit`(`MEMORY_KEY_FORCE_AUTOFIT_ON_NO_AUTOFIT_SHIPS`) | MOD 代码按需写入 | `inflate` 与 `deflate` 读取：强制对 no\_autofit 舰船也膨胀 |

舰队内存读取纪律

纯读取**必须**用 `getMemoryWithoutUpdate()`——`getMemory()` 会触发全插件 `updateEntityFacts` 回调，高频读取会造成回调风暴。

### 投放 (spawn)

舰队生成完毕、装配也按质量补全后，最后一步是把它放进地点容器 —— 投放。

**`LocationAPI.spawnFleet`**(`com.fs.starfarer.api.campaign.LocationAPI`) 是 "创建后投放" 的正式入口，四参签名：

`SectorEntityToken anchor, float xOffset, float yOffset, CampaignFleetAPI fleet`

语义：

1. `this.addObject(fleet)`—— **入容器** (`getAllEntities`/`getFleets` 可见，`isAlive()` 变 true)；
2. 坐标 = `anchor.getLocation() + (xOffset, yOffset)`(anchor 为 null 时坐标就是偏移本身)→ `fleet.setLocation(...)`；
3. `fleet.setNoEngaging(3.0F)`—— **投放后 3 秒内不交战** (`CampaignFleetAPI.setNoEngaging`)。

原版使用方：遭遇战生成 (anchor = 玩家舰队 + 随机 ±100 偏移；重生，anchor null)、开局玩家舰队 (anchor null)。

替代路径：`location.addEntity(fleet)` + `fleet.setLocation(x, y)` + `fleet.forceSync()`——`spawnFleet` 省掉这三步并附赠 3 秒免战。

**投放必须发生在生涯引擎建立之后**；创建与投放之间舰队不在任何容器里 (`isAlive()==false`，地点容器语义)。

### 注册入口

无论生成还是投放，都必须在生涯引擎建立之后执行；本节的注册沿用前置章节的 persistent data 判断模式。

注册时机与一次性判断

本节示例注册**必须**发生在生涯引擎建立之后，沿用前置章节讲过的 `onGameLoad(boolean newGame)` + persistent data 键判断模式；每个注册项用各自的键，**禁止**并入插件已有的 `CLASS_MARK` 判断内。

## 必须遵守的规则

前文散落在各节的约束里，以下几条违反后有明确可观察结果；做示例前先过一遍：

1. **质量必须来自市场**。`FleetParamsV3.source` 要求经 `setSource(market, true)`、市场构造器或 `updateQualityAndProducerFromSourceMarket()` 设置来源；无 source 且附近无同势力市场时，`createFleet` 会建立 fake market 并重算质量，最终质量受 `+0.5` 基础值、进口惩罚和 doctrine 贡献差影响，通常约为 `0.25–0.5`，平均 D 插约为 `(1-quality)/0.2`。
2. **质量优先级：`qualityOverride` > `quality + qualityMod`**。不设 `qualityOverride` 时 `qualityMod` 才叠加到市场质量上；同时设置时 `qualityMod` 被无视。附近没有同势力市场时工厂会使用 fake market 重算质量。
3. **点数是目标不是硬上限**。"舰队点数与请求不符" 是**正常现象**，实际受市场供应、`maxShipsInAIFleet` 裁剪与角色缺货影响；需要还原请求值用 `Misc.getSpawnFPMult(fleet)`。
4. **`flagshipVariantId` / `flagshipVariant` 只在 `getFlagship()` 非 null 时应用**，且 `setVariant(variant, false, true)` 会连船体一起换。舰队为空时不生效；指定了异 hull 的旗舰变体后，旗舰船体与角色会与抽选时不同。
5. **`setFlagship(member)` 的副作用必须接受**：其他成员舰长被重置为默认、新旗舰舰长 = 舰队指挥官。旗舰读回 `getCaptain() == getCommander()`；不要用逐成员 `setCaptain` 对抗该语义。
6. **膨胀前读取成员装配拿到的是未按质量调整的生成态变体**。需要完整装配必须先触发 `inflateIfNeeded()`—— 引擎在 AI/工具提示/战斗前自动触发；在此之前读 `member.getVariant()`：武器仍是角色变体原样、无 D 插/S 插 (`source` 仍是 STOCK/生成时来源，非 REFIT)。
7. **`removeAfterInflating()` = `persistent == null || !persistent`**。V3 生成 `persistent=true` → 膨胀后 inflater 保留；自定义 inflater 忘记设 persistent 时，首次 `inflateIfNeeded` 后 `getInflater()` 变 null，二次膨胀不可能。
8. **读写舰队内存必须用 `getMemoryWithoutUpdate()`**。`getMemory()` 会触发全插件 `updateEntityFacts` 回调，高频读取会造成回调风暴。
9. **投放必须在生涯引擎建立后调用** `spawnFleet`(或 `addEntity` + `setLocation`)。`createFleet` 后没投放：舰队不在容器、`isAlive()==false`、地图不可见，且无任何报错。
10. **需要可复现的随机结果时必须显式给 `params.random`**。生成带 D 插/S 插的随机结果由 `params.random` 与 inflater `seed` 决定；同一参数两次生成结果不同；读档后重膨胀 (deflate→inflate) 结果与膨胀前一致 (每成员随机源由 `seed × shipName.hashCode()` 派生)。

**建议**：

- 需要确定性测试时：`params.random = new Random(固定种子)`；不设 `qualityOverride` 时同时固定 `source`(质量含市场动态值，跨存档会变)。
- 生成后立即投放用 `spawnFleet` 一行 (含 3 秒免战)；需要自定义轨道或延迟再考虑 `addEntity` + `setLocation` + 自行 `setNoEngaging`。
- 给巡逻/袭击等 AI 舰队按需写 `MemFlags`(如 `FLEET_IGNORED_BY_OTHER_FLEETS`) 与 `setTransponderOn(false)`。

## 实现舰队投放工厂

下面把市场来源、预算、延迟膨胀和地点投放串成一条可观察链。每段都是 `GearInFleetFactory.runOnce` 中的局部代码；确认当前完成信号后再加入下一段。

### 取得来源市场与投放锚点

插入位置：`runOnce` 开头。参数质量和最终坐标都依赖市场，先把市场、地点容器和主实体一起验证，避免生成完成后才发现无处投放。

```java
MarketAPI market = pickMarket();
if (market == null) {
	LOG.error("GearIn fleet factory: no market found, aborting");
	return;
}
LocationAPI location = market.getContainingLocation();
SectorEntityToken anchor = market.getPrimaryEntity();
if (location == null || anchor == null) {
	LOG.error("GearIn fleet factory: market has no location or primary entity");
	return;
}
```

完成信号：日志能同时给出市场 ID、地点 ID 和市场质量；异常市场在创建舰队之前明确中止。

### 配置预算、质量与旗舰

插入位置：市场检查之后。市场构造器把来源、势力、超空间位置、时间戳与市场质量一起写入参数；随后只覆盖本次演示需要改变的字段。

```java
FleetParamsV3 params = new FleetParamsV3(
		market, FleetTypes.PATROL_MEDIUM,
		40f, 12f, 6f, 0f, 0f, 0f, 0f);
params.qualityOverride = 0.6f;
params.flagshipVariantId = "tempest_Attack";
params.officerLevelBonus = 1;
```

完成信号：`params.getTotalPts()` 为 58，`source` 指向选中的市场，日志同时显示市场质量与 `qualityOverride=0.60`。

### 生成成员并核对旗舰

插入位置：参数配置之后。工厂会完成成员抽选、裁剪、指挥官与军官生成、旗舰覆盖、CR 同步和 inflater 挂载；空成员集合必须在继续前中止。

```java
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
if (members.isEmpty()) {
	LOG.error("GearIn fleet factory: generated fleet is empty, aborting");
	return;
}

FleetMemberAPI flagship = fleet.getFlagship();
LOG.info("GearIn fleet factory flagship: hull=" + flagship.getHullId()
		+ " variant=" + flagship.getVariant().getHullVariantId()
		+ " captainIsCommander=" + (flagship.getCaptain() == fleet.getCommander()));
```

完成信号：成员数大于 0，旗舰为 `tempest_Attack`，且 `captainIsCommander=true`；实际 FP 可偏离 58，因为预算是目标值。

### 触发延迟膨胀

插入位置：旗舰核对之后。先记录 inflater 的有效质量，再触发一次膨胀并统计 `REFIT` 来源装配与 D 插数量。

```java
LOG.info("GearIn fleet factory pre-inflate: inflated=" + fleet.isInflated()
		+ " quality=" + fleet.getInflater().getQuality());
fleet.inflateIfNeeded();

int dmodTotal = 0;
int refitVariants = 0;
for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
	dmodTotal += DModManager.getNumDMods(member.getVariant());
	if (member.getVariant().getSource() == VariantSource.REFIT) {
		refitVariants++;
	}
}
```

完成信号：`isInflated` 从 `false` 变为 `true`，`refitVariants` 与参与膨胀的成员数对应，质量 0.6 下日志能观察到 D 插分布。

### 读取工厂写入的内存

插入位置：膨胀统计之后。纯读取使用 `getMemoryWithoutUpdate()`，避免触发舰队事实刷新回调；请求 FP 的比例通过 `Misc` 读取。

```java
String fleetType = fleet.getMemoryWithoutUpdate().getString(
		MemFlags.MEMORY_KEY_FLEET_TYPE);
String sourceMarket = fleet.getMemoryWithoutUpdate().getString(
		MemFlags.MEMORY_KEY_SOURCE_MARKET);
long salvageSeed = fleet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED);
float spawnFPMult = Misc.getSpawnFPMult(fleet);
```

完成信号：`fleetType=patrolMedium`、`sourceMarket=jangala` (或实际备用市场 ID)，并能读到稳定的 salvage seed 与非零 FP 比例。

### 投放并核对地点容器

插入位置：`runOnce` 末段。`spawnFleet` 同时完成入容器、锚点偏移定位和 3 秒免战；演示核对后再移除，避免向存档遗留舰队。

```java
location.spawnFleet(anchor, 0f, 0f, fleet);
LOG.info("GearIn fleet factory spawned: inLocation="
		+ (fleet.getContainingLocation() == location)
		+ " alive=" + fleet.isAlive()
		+ " at=" + fleet.getLocation());

location.removeEntity(fleet);
LOG.info("GearIn fleet factory removed: alive=" + fleet.isAlive());
```

完成信号：投放日志先显示 `inLocation=true`、`alive=true` 且坐标等于锚点，移除后 `alive=false`。

## 组装舰队投放实现

机制与规则都就位后，把它们串成一条可观察的日志链：在既有 MOD 布局与 `GearInModPlugin` 之上新增一个演示脚本类 (不新增资源文件)。脚本每完成一环就打印一行日志，验证方案以这些日志为对照：

```text {8}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       └── GearInFleetFactory   # 本节新增 (归本节所有)
└── jars\
```

### GearInFleetFactory

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回 `executed`(演示执行后自停)；`runWhilePaused()` 返回 `false`；`advance` 包 `try/catch`(任一步抛错只记 ERROR 不自停失败，前置章节同款写法)。
- `REGISTER_MARK`：persistent data 判断键 (`"GearIn_FleetFactory"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不含任何工作单元编号变体。
- `FleetParamsV3` market 构造器：`source=jangala`、`fleetType=patrolMedium`、六类点数、`qualityMod=0`——`params.quality` 即市场质量 (`Misc.getShipQuality`)，日志里与 `qualityOverride` 并排可对照优先级。
- `params.qualityOverride = 0.6f`：有效质量 0.6 → `averageDmods=(1-0.6)/0.2=2`，多数成员带 1–3 个 D 插；tier2 武器概率 `0.5+0.3=0.8`、tier3 `0.25+0.15=0.4`。
- `params.flagshipVariantId = "tempest_Attack"`：旗舰装配覆盖 (`createFleet` 旗舰变体段)，换 hull 为 `tempest`—— 日志 `flagship hull=tempest variant=tempest_Attack`。
- `fleet.getFlagship().getCaptain() == fleet.getCommander()`：`setFlagship` 的副作用 (`FleetDataAPI.setFlagship` 内部实现)，演示核对 `captainIsCommander=true`。
- `fleet.inflateIfNeeded()`：手动触发膨胀 (引擎在 AI/工具提示/战斗前也会自动触发)；`isInflated=true` 且 `inflaterAttached=true`(`persistent=true` 保留)；成员变体来源 REFIT、D 插合计按质量分布。
- 内存五键：`$fleetType`/`$sourceMarket` 来自 `createEmptyFleet`；`$salvageSeed` 来自 `Misc.getSalvageSeed`；`$spawnFPMult` 来自 `Misc.setSpawnFPMult`(实际/请求 FP)；`$overrideNoAutofit` 未写 (默认 false)。
- `location.spawnFleet(anchor, 0f, 0f, fleet)`：投放三合一；`spawned` 行核对 `inLocation=true`、坐标 == 锚点坐标、`alive=true`。
- `FleetFactory.createPatrol(market, PatrolType.FAST)`：V1 固定模板对照 (`patrolSmall` 类型、手写角色组合)；第二个 `spawnFleet` 带 +200,+200 偏移展示偏移参数。
- 清理段：`removeEntity` 移除两支舰队 —— 演示脚本**不留副作用**，可反复读档重放。两支舰队连 3 秒的免战窗口都没撑到就被同帧移除，这份免战期在本例中纯属理论福利；`spawnFleet` 仍是正确的投放写法，它把入容器、定位与免战三件事合成一行。

调用点 (工程片段 —— 方法所在类归前置章节所有，本节只演示调用，不改动那些文件)：

```java
    // 在 GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFleetFactory.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFleetFactory.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFleetFactory());
    }
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用既有构建链路)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInFleetFactory.class`(可用 `jar tf` 核对)。

## 关键故障实验：舰队创建成功但地图上找不到

工厂返回了成员完整的 `CampaignFleetAPI`，但该对象尚未加入任何地点容器。

**局部故障片段：**

```java
// 故障片段
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
LOG.info("created members=" + fleet.getFleetData().getNumMembers());
```

**局部修正片段：**

```java {4}
// 修正片段
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
LocationAPI location = market.getContainingLocation();
location.spawnFleet(market.getPrimaryEntity(), 0f, 0f, fleet);
```

实际症状：成员、旗舰与指挥官都能从局部对象读到，但 `fleet.isAlive()` 为 `false`，`location.getFleets()` 不包含它，地图上也没有图标。

原因：`createFleet` 只生成舰队对象；地点容器并不知道该对象存在。`spawnFleet` 才负责把舰队加入容器、设置坐标并施加短暂免战。

复验：投放后 `fleet.getContainingLocation() == location`、`fleet.isAlive() == true`，并且 `location.getFleets().contains(fleet)`；舰队坐标应与市场主实体一致。

## 验证方案

前置数据：既有 MOD 布局与插件接入 (含本节调用点片段)、`GearInFleetFactory` 源码。

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInFleetFactory.class`。
2. **新建生涯**：确认 `starsector.log` 按顺序出现全部 `GearIn fleet factory` 日志行 (`start` / `params` / `created` / `flagship` / `commander` / `pre-inflate` / `inflated` / `memory` / `spawned` / `patrol` / `done`)。
    - 关键核对：`flagship hull=tempest variant=tempest_Attack captainIsCommander=true`、`pre-inflate isInflated=false ... inflaterQuality=0.60`、`inflated isInflated=true inflaterAttached=true refitVariants=<N>`、`memory fleetType=patrolMedium sourceMarket=jangala ... spawnFPMult=<mult>`、`spawned inLocation=true ... alive=true`、`done fleetAlive=false patrolAlive=false`。
    - 观察 `GearIn fleet factory` 的完整日志序列。
3. **界面核对**：在演示执行的那一帧附近暂停，`jangala` 行星位置会出现两支舰队图标并几乎立刻消失 (同帧即被移除，观察窗口不到一帧)；核对以日志为准。
4. **只执行一次**：等待若干游戏日后日志无新增 `GearIn fleet factory` 行；保存并读档后仍无新增 (`REGISTER_MARK` 判断已置位)；`jangala` 附近无残留舰队。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInFleetFactory.class`。

- **新建生涯 (首次进入存档后第一帧)**：`starsector.log` 依次出现：

    ```text
    GearIn fleet factory start: market=jangala size=6 location=corvus quality=<市场质量> GearIn fleet factory params: faction=hegemony fleetType=patrolMedium marketQuality=<Q> qualityOverride=0.60 totalPts=58 GearIn fleet factory created: name=Patrol faction=hegemony fleetType=patrolMedium members=<N> fighters=<战机数> fp=<FP> GearIn fleet factory flagship: hull=tempest variant=tempest_Attack captainIsCommander=true GearIn fleet factory commander: name=<指挥官名> level=<L> personality=<personality> officers=<M> GearIn fleet factory pre-inflate: isInflated=false inflaterAttached=true inflaterQuality=0.60 GearIn fleet factory inflated: isInflated=true inflaterAttached=true refitVariants=<N> dmods=<D> GearIn fleet factory memory: fleetType=patrolMedium sourceMarket=jangala salvageSeed=<S> spawnFPMult=<mult> GearIn fleet factory spawned: inLocation=true at=(<ax>, <ay>) anchor=(<ax>, <ay>) alive=true GearIn fleet factory patrol: name=Fast Picket members=<P> fp=<FPp> flagship=<hull> GearIn fleet factory done: fleetAlive=false patrolAlive=false
    ```

    其中 `<N>` 是生成成员数 (40+18 点数预算下典型 7–12 艘)、`<战机数>` 是战机联队数 (无航母时为 0)、`<FP>` 是实际 fleet points(**可能低于 58**—— 点数是目标不是硬上限)、`<L>` 是指挥官等级 (`officerLevelBonus=1` 生效)、`<M>` 是军官数 (≥1 为正常)、`<D>` 是 D 插总数 (质量 0.6 下典型 ≈ 2×成员数)、`<mult>` 是 `$spawnFPMult`(实际/请求)。`flagship` 与 `patrol` 两行的 `fleetAlive=false` 证明清理生效。

- **游戏内地图**：演示期间 (日志可见时)`jangala` 行星位置短暂出现两支舰队图标 (同帧即被移除，若看不到可在暂停时核对日志)；演示结束后地图无残留。

- **读档**：不出现任何 `GearIn fleet factory` 日志 (`REGISTER_MARK` 判断已置位、脚本不再注册)；`jangala` 附近无残留舰队。

- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

## 故障分析

- **生成舰队的战力奇低**：来源市场的质量偏低，或 `qualityMod` 进一步降低了有效质量。

    - **解决方案**：记录 inflater 的 `getQuality()`；需要固定测试条件时，使用 market 构造器、`setSource(market, true)` 或显式 `qualityOverride`，重新生成。

- **舰队生成时启动中止**：`createFleet` 被放在 `onApplicationLoad`，此时 `Global.getSector()` 仍为 null。

    - **解决方案**：把注册放入 `onGameLoad` 的一次性判断块。

- **指定 `flagshipVariantId` 后旗舰没换**：装配 ID 无效或舰队为空。

    - **解决方案**：用 `getAllVariantIds()` 核对 ID，并确认生成成员数大于 0。

- **指挥官和军官完全没有生成**：`withOfficers=false` 或成员列表为空都会跳过创建。

    - **解决方案**：启用军官生成，并先解决舰队成员为空的问题。

- **生成后读取装配时看不到武器**：舰队尚未膨胀，成员仍保留角色变体。

    - **解决方案**：需要完整装配时先调用 `fleet.inflateIfNeeded()`。

- **舰队创建成功但地图与 `getFleets()` 都找不到**：对象没有进入地点容器。

    - **解决方案**：使用 `spawnFleet`，或先 `addEntity` 再设置位置。

- **舰队堆在原点或偏移方向错误**：`spawnFleet` 的 anchor 传了 null。

    - **解决方案**：传入 `market.getPrimaryEntity()` 等实际锚点。

- **每次读档都会多出一支舰队**：注册缺少独立的 persistent data 标记。

    - **解决方案**：使用本节 `REGISTER_MARK`。

- **跨创建时舰队装配无法复现**：默认 random 让新创建使用不同 seed。

    - **解决方案**：设置 `params.random = new Random(固定种子)`。

- **实际舰队点数明显低于请求值**：市场供应、30 舰上限和角色点数分配都会裁剪结果。

    - **解决方案**：用 `Misc.getSpawnFPMult(fleet)` 还原请求值，再核对角色配置。

- **D 插数量过多或为零**：代码混用了 `qualityOverride` 与市场质量，或误读了 `qualityPerDMod`。质量 0.6 时平均 2 个，质量 1.0 时平均 0 个。

    - **解决方案**：读取 inflater 的 `getQuality()` 核对质量取值。

- **投放后舰队立刻进入交战**：手动 `addEntity` 路径没有附加免战时间。

    - **解决方案**：优先使用 `spawnFleet`；自定义投放时调用 `fleet.setNoEngaging(...)`。

## 总结

本节把 "生涯舰队" 从参数到投放串成一条完整链：`FleetParamsV3` 决定来源与预算，`FleetFactoryV3.createFleet` 按十步把它变成带成员的舰队，`FleetInflater` 在需要完整装配时按质量补全，`LocationAPI.spawnFleet` 最后把它放进地点容器。每个环节断掉时症状都不同：质量没取到 → 满船 D 插；没投放 → 地图上查无此舰队且无报错；膨胀前读装配 → 拿到生成态。

排错与复用记住四条判断。其一，点数是目标不是硬上限，实际点数受供应与裁剪影响，还原请求值用 `$spawnFPMult`。其二，质量优先级 `qualityOverride` > `quality + qualityMod`；没有来源市场时工厂会建立 fake market 并重算质量，D 插平均为 `(1-quality)/0.2` 个/船。其三，膨胀是延迟的：`createFleet` 只挂 inflater，`persistent=true` 时膨胀后保留，同 `seed` 重膨胀结果一致。其四，`setFlagship` 会把旗舰舰长设为指挥官并重置其他成员舰长，读回 `getCaptain() == getCommander()` 是设计行为而非故障。

舰队行为 (任务、AI) 与舰队间交互的主题会直接使用本节生成的舰队对象；成员对象、人物与变体三个主题则是本节三块底座。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`：参数对象 (公开字段、三个构造器、`setSource`、`updateQualityAndProducerFromSourceMarket`、`getTotalPts`)。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：`createFleet` 主流程 (`pickMarket` / `createEmptyFleet` / `pruneFleet` / `addCommanderAndOfficersV2` / `addCommanderSkills` / `getSkillPrefForShip`)。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactory`：V1 固定模板 (`createPatrol` / `createMerc` / `createGenericFleet`)。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetParams`：V1 旧参数对象 (区分新旧时提及)。
- `com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater` / `DefaultFleetInflaterParams`：膨胀实现与参数 (`persistent`、`averageSMods`、`seed`)。
- `com.fs.starfarer.api.campaign.FleetInflater`：`inflate` / `removeAfterInflating` / `getQuality` / `getAverageNumSMods` 接口。
- `com.fs.starfarer.api.campaign.LocationAPI`：`spawnFleet`(入容器 + 定位 + 3 秒免战)、`addEntity` / `removeEntity`。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：`getFlagship` / `getCommander` / `getInflater` / `inflateIfNeeded` / `isInflated` / `setNoEngaging` / `getFleetPoints`。
- `com.fs.starfarer.api.EveryFrameScript`：示例脚本实现的接口 (`isDone` / `runWhilePaused` / `advance`)。
- `com.fs.starfarer.api.Global`：`getSector()`、`getLogger(Class)`、`getSettings()`、`getFactory()` 等全局入口。
- `com.fs.starfarer.api.FactoryAPI`：`createEmptyFleet(String, String, boolean)`(`aiMode=true` 的空舰队创建)。
- `com.fs.starfarer.api.campaign.FactionAPI`：`ShipPickParams`、`pickShipAndAddToFleet`、`getFleetTypeName`。
- `com.fs.starfarer.api.util.Misc`：`getShipQuality`、`getShipPickMode`、`getSalvageSeed`、`getSpawnFPMult` / `setSpawnFPMult`、`getInflater`。
- `com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality`：市场生产质量计算 (`QualityData.quality` + `FLEET_QUALITY_MOD` + 势力 doctrine 贡献差)。
- `com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage`：`$spawnFPMult` 使用方 (还原请求 FP 判断中止阈值)。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：`SALVAGE_SEED`、`MEMORY_KEY_SOURCE_MARKET`、`MEMORY_KEY_FLEET_TYPE`、`MEMORY_KEY_FORCE_AUTOFIT_ON_NO_AUTOFIT_SHIPS`。
- `com.fs.starfarer.api.impl.campaign.ids.FleetTypes`：`PATROL_SMALL` / `PATROL_MEDIUM` / `PATROL_LARGE` / `TASK_FORCE`。
- `com.fs.starfarer.api.impl.campaign.DModManager`：`getNumDMods`、`setDHull`、`addDMods`。
- `com.fs.starfarer.api.plugins.CreateFleetPlugin`：舰队生成插件扩展点。
- `data/config/settings.json`：`qualityPerDMod`(0.2)、`officerAIMax`(10)、`maxAIFleetCommanderLevel`(7)、`commanderLevelForOneSkill`(4)/ `commanderLevelForTwoSkills`(6)、`baseCombatShipsPerOfficer`(3)、`maxShipsInAIFleet`(30)。
- `data/world/factions/default_ship_roles.json`：角色键 (`combatSmall`、`fastAttack`、`freighterSmall` 等)。
- `data/world/factions/default_fleet_type_names.json`：舰队类型显示名 (`patrolSmall`→"Fast Picket"、`patrolMedium`→"Patrol"、`taskForce`→"Task Force")。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。

## 完整最小例

::: details 完整成品：GearInFleetFactory

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：生涯舰队工厂 (FleetParamsV3 -> FleetFactoryV3.createFleet -> inflate -> spawn)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控
 * (沿用既有模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停 (isDone()=true，引擎下一帧自动移除本脚本)：
 *   1. 基线：选定演示市场 (优先 jangala)，记录地点/锚点与市场质量；
 *   2. FleetParamsV3(market 构造器：source/quality/timestamp 一次就位)，qualityOverride=0.6
 *      压质量 (平均 D 插约 2 个/船，可观察)，flagshipVariantId 指定旗舰变体；
 *   3. FleetFactoryV3.createFleet：记录舰队名/势力/类型/成员数/战机数/FP；
 *   4. 旗舰与指挥官：flagship hull 与变体、舰长是否就是舰队指挥官、指挥官等级/personality、军官数；
 *   5. 膨胀前状态：isInflated=false、inflater 已挂载、inflater.getQuality()==0.6；
 *   6. inflateIfNeeded 后：isInflated=true、inflater 保留 (persistent=true)、
 *      成员变体来源 REFIT、D 插总数 (质量 0.6 -> 平均 2 个/船)；
 *   7. 内存键：$fleetType / $sourceMarket / $salvageSeed / $spawnFPMult；
 *   8. 投放：location.spawnFleet(anchor, 0, 0, fleet) -> 入容器、坐标=锚点坐标、isAlive=true；
 *   9. 对照：FleetFactory.createPatrol(V1 固定模板) 生成快速巡逻队并投放；
 *  10. 清理：同帧 removeEntity 移除两支舰队，不留任何持久副作用。
 */
public class GearInFleetFactory implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_FleetFactory";

    private static final Logger LOG = Global.getLogger(GearInFleetFactory.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetFactory() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn fleet factory failed", t);
        }
    }

    private void runOnce() {
        MarketAPI market = pickMarket();
        if (market == null) {
            LOG.error("GearIn fleet factory: no market found, aborting");
            return;
        }
        LocationAPI location = market.getContainingLocation();
        SectorEntityToken anchor = market.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.error("GearIn fleet factory: market has no containing location or primary entity, aborting");
            return;
        }
        LOG.info(String.format(
                "GearIn fleet factory start: market=%s size=%d location=%s quality=%.2f",
                market.getId(), market.getSize(), location.getId(),
                Misc.getShipQuality(market, market.getFactionId())));

        // --- 1. FleetParamsV3：market 构造器 (source + quality + timestamp 一次就位)---
        // combatPts=40, freighterPts=12, tankerPts=6；总点数 58 -> getMinPreferredMarketSize=5
        FleetParamsV3 params = new FleetParamsV3(market, FleetTypes.PATROL_MEDIUM,
                40f, 12f, 6f, 0f, 0f, 0f, 0f);
        params.qualityOverride = 0.6f;          // 覆盖市场质量：quality=0.6 -> 平均 D 插 (1-0.6)/0.2 = 2 个/船
        params.flagshipVariantId = "tempest_Attack"; // 旗舰装配覆盖 (换 hull 为 tempest)
        params.officerLevelBonus = 1;           // 军官/指挥官等级 +1
        LOG.info(String.format(
                "GearIn fleet factory params: faction=%s fleetType=%s marketQuality=%.2f qualityOverride=%.2f totalPts=%.0f",
                params.factionId, params.fleetType, params.quality, params.qualityOverride, params.getTotalPts()));

        // --- 2. FleetFactoryV3.createFleet：完整生成链 ---
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        LOG.info(String.format(
                "GearIn fleet factory created: name=%s faction=%s fleetType=%s members=%d fighters=%d fp=%.0f",
                fleet.getName(), fleet.getFaction().getId(),
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE),
                members.size(), fleet.getNumFighters(), fleet.getFleetPoints()));
        if (members.isEmpty()) {
            LOG.error("GearIn fleet factory: generated fleet is empty, aborting");
            return;
        }

        // --- 3. 旗舰与指挥官 ---
        FleetMemberAPI flagship = fleet.getFlagship();
        String flagshipInfo = flagship == null ? "none" : String.format(
                "hull=%s variant=%s captainIsCommander=%s",
                flagship.getHullId(), flagship.getVariant().getHullVariantId(),
                flagship.getCaptain() == fleet.getCommander());
        LOG.info("GearIn fleet factory flagship: " + flagshipInfo);
        LOG.info(String.format(
                "GearIn fleet factory commander: name=%s level=%d personality=%s officers=%d",
                fleet.getCommander().getNameString(), fleet.getCommander().getStats().getLevel(),
                fleet.getCommander().getPersonalityAPI().getId(),
                fleet.getFleetData().getOfficersCopy().size()));

        // --- 4. 膨胀前：inflater 已挂载但未膨胀 ---
        LOG.info(String.format(
                "GearIn fleet factory pre-inflate: isInflated=%s inflaterAttached=%s inflaterQuality=%s",
                fleet.isInflated(), fleet.getInflater() != null,
                fleet.getInflater() == null ? "n/a" : String.format("%.2f", fleet.getInflater().getQuality())));

        // --- 5. 惰性膨胀：inflateIfNeeded 一次性补全装配 ---
        fleet.inflateIfNeeded();
        int dmodTotal = 0;
        int refitVariants = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            dmodTotal += DModManager.getNumDMods(member.getVariant());
            if (member.getVariant().getSource() == VariantSource.REFIT) {
                refitVariants++;
            }
        }
        LOG.info(String.format(
                "GearIn fleet factory inflated: isInflated=%s inflaterAttached=%s refitVariants=%d dmods=%d",
                fleet.isInflated(), fleet.getInflater() != null, refitVariants, dmodTotal));

        // --- 6. 工厂写入的舰队内存键 ---
        LOG.info(String.format(
                "GearIn fleet factory memory: fleetType=%s sourceMarket=%s salvageSeed=%d spawnFPMult=%.3f",
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE),
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_SOURCE_MARKET),
                fleet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED),
                Misc.getSpawnFPMult(fleet)));

        // --- 7. 投放：入容器 + 定位 (锚点坐标 + 偏移)+ setNoEngaging(3s) ---
        float anchorX = anchor.getLocation().x;
        float anchorY = anchor.getLocation().y;
        location.spawnFleet(anchor, 0f, 0f, fleet);
        LOG.info(String.format(
                "GearIn fleet factory spawned: inLocation=%s at=(%.0f, %.0f) anchor=(%.0f, %.0f) alive=%s",
                fleet.getContainingLocation() == location,
                fleet.getLocation().x, fleet.getLocation().y, anchorX, anchorY, fleet.isAlive()));

        // --- 8. 对照：FleetFactory(V1) 固定模板 createPatrol ---
        CampaignFleetAPI patrol = FleetFactory.createPatrol(market, FleetFactory.PatrolType.FAST);
        List<FleetMemberAPI> patrolMembers = patrol.getFleetData().getMembersListCopy();
        LOG.info(String.format(
                "GearIn fleet factory patrol: name=%s members=%d fp=%.0f flagship=%s",
                patrol.getName(), patrolMembers.size(), patrol.getFleetPoints(),
                patrol.getFlagship() == null ? "none" : patrol.getFlagship().getHullId()));
        location.spawnFleet(anchor, 200f, 200f, patrol);

        // --- 9. 清理：同帧移除，不留任何持久副作用 ---
        location.removeEntity(fleet);
        location.removeEntity(patrol);
        LOG.info(String.format(
                "GearIn fleet factory done: fleetAlive=%s patrolAlive=%s",
                fleet.isAlive(), patrol.isAlive()));
    }

    /** 演示市场：优先 jangala(hegemony 核心市场)，否则第一个有 open 子市场的市场。 */
    private MarketAPI pickMarket() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market != null) {
            return market;
        }
        for (MarketAPI candidate : Global.getSector().getEconomy().getMarketsCopy()) {
            if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                return candidate;
            }
        }
        return null;
    }
}
```

:::
