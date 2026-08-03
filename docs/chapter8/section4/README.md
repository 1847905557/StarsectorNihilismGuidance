# 舰队生成与投放：成员、旗舰与指挥官

本节把 "生涯舰队工厂" 讲透：`FleetParamsV3` 全参数、`FleetFactoryV3.createFleet` 的生成流程 (成员怎么按角色进队、旗舰怎么定、指挥官与军官怎么生成)、`FleetInflater` 的膨胀语义与变体应用、舰队内存键、以及把舰队投放 (spawn) 到地点的正确做法。成员对象的保存态读写、变体来源语义、人物与 captain/officer、地点容器与实体分别属于舰队数据、变体、人物与地点容器主题，本节只给最小定义并交叉引用。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `FleetParamsV3` 的全部参数组：来源市场与质量、超空间定位、六类点数 (combat/freighter/tanker/transport/liner/utility)、规模上限 (`maxShipSize`/`minShipSize`/`maxNumShips`)、军官族 (数量/等级/指挥官)、旗舰族 (`flagshipVariantId`/`flagshipVariant`/`onlyRetainFlagship`) 与杂项开关，并记住 javadoc 原文 "Fleet point values are targets not hard limits"；
- 复述 `FleetFactoryV3.createFleet(params)` 的十步流程：插件扩展点 → 市场选择 (含 fake market 备用方案)→ 空舰队创建 (AI mode、舰队名、能力、`$fleetType`/`$sourceMarket`/`$salvageSeed` 内存)→ doctrine 拆分战舰/航母/相位点数 → 按角色填舰 (`pickShipsForRole` + `ShipPickMode`)→ 超员裁剪 → 指挥官与军官 → 旗舰变体 → `forceSync` + CR 拉满 → inflater 挂载与 `$spawnFPMult`；
- 说出成员/旗舰/指挥官在工厂里的生成规则：成员来自角色 (role) 到变体的抽选 (`default_ship_roles.json`)，旗舰从最大 hull size 的加权 picker 中抽选，`setFlagship` 会把旗舰舰长设为舰队指挥官并重置其他成员舰长，指挥官来自 `OfficerManagerEvent.createOfficer` 且 `timid` 会升为 `cautious`；
- 说出 `FleetInflater` 的延迟膨胀语义：`createFleet` 只挂载不膨胀，`inflateIfNeeded` 在 AI/工具提示/战斗前首次触发；`persistent=true` 时膨胀后 inflater 保留、`null` 时移除；膨胀按质量决定武器 tier 概率、D 插数量与可选 S 插，结果变体来源 `REFIT` 且带 `originalVariant` 回指；
- 说出工厂写进舰队内存的键与使用方：`$fleetType`、`$sourceMarket`、`$salvageSeed`、`$spawnFPMult`(`Misc.setSpawnFPMult` 写入、`BaseRaidStage` 还原请求 FP 读取)、`$overrideNoAutofit`；
- 用 `LocationAPI.spawnFleet(anchor, xOffset, yOffset, fleet)` 投放舰队，说出其 "入容器 + 定位 (anchor 坐标 + 偏移)+ `setNoEngaging(3.0F)`" 的三合一语义，以及 `addEntity` + `setLocation` 替代路径；
- 从症状定位十二类典型故障：舰队零战力、舰队不出现、旗舰没换、指挥官缺失、军官数量为 0、成员装配未按质量膨胀、D 插数量异常、舰队堆在原点、位置不对、读档后装配变化、重复生成、点数与请求不符。

## 前置知识

本节建立在以下前置内容之上：

- 生涯引擎级脚本注册守卫 (C08-S01)：persistent data `REGISTER_MARK` 各自键模式。
- 地点容器与实体 (C08-S02)：`LocationAPI`、`market.getContainingLocation()` / `market.getPrimaryEntity()`、`addEntity`/`removeEntity` 的容器语义 (本节投放环节直接使用)。
- 舰队成员与 `FleetDataAPI`(C08-S03)：`getMembersListCopy` / `getFlagship` / `setFlagship` 副作用 / `getCommander` / `getOfficersCopy`、成员变体读写 (本节旗舰与指挥官环节直接使用)。
- 人物与 captain/officer(C09-S01)：指挥官与军官都是人物；`setCaptain`、`getStats().getLevel()`、personality。
- 变体 (C09-S03)：`VariantSource.REFIT`、`setVariant(variant, false, true)`、`originalVariant`—— 膨胀结果变体的来源语义本节使用。
- `GearInModPlugin` 的 `onGameLoad` 与 `Global.getSector()` 阶段语义 (C06-S02 / C06-S03)。

## 术语

按术语约定，本节首次完整讲解以下词 (后续页面继续使用)：

- **生涯舰队工厂 (fleet factory)**：API 名保留 `FleetFactoryV3`(含参数对象 `FleetParamsV3`)。以 "点数预算 + 势力 + 舰队类型" 为输入、产出完整 `CampaignFleetAPI` 的一族原版工具类。同一族还有固定模板 `FleetFactory`(V1) 与旧参数对象 `FleetParams`。
- **舰队类型 (fleet type)**：API 名保留 `FleetTypes`。标识舰队用途的 id(如 `patrolSmall` / `patrolMedium` / `taskForce`)，用于取舰队显示名 (`FactionAPI.getFleetTypeName`，数据 `default_fleet_type_names.json`) 并写入内存 `$fleetType`。
- **旗舰 (flagship)**：API 名保留 `flagship`(`FleetMemberAPI.isFlagship` / `CampaignFleetAPI.getFlagship`)。舰队的代表成员；工厂里从最大 hull size 成员中抽选，`setFlagship` 会联动舰长。
- **舰队指挥官 (fleet commander)**：API 名保留 `commander`(`CampaignFleetAPI.getCommander` / `setCommander`)。统率全舰队的 `PersonAPI`；`setFlagship` 后旗舰舰长即指挥官。
- **膨胀 (inflate)**：API 名保留 `FleetInflater`。把 "生成态" 成员 (装配仍是角色变体原样) 按势力可用武器/战机/船插与质量随机补全为完整装配态的延迟过程。
- **投放 (spawn)**：把已创建的舰队实体加入地点容器并定位的过程；本节特指 `LocationAPI.spawnFleet`。

## 文件与 API 链路

### 参数对象：FleetParamsV3

`FleetParamsV3`(`com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`) 是**公开字段**的参数类，无 getter/setter。

javadoc 原文两条：**"Fleet point values are targets not hard limits."**(点数是目标不是硬上限)。

**"'Pts' are fleet points in this implementation, unlike FleetFactoryV2."**(V3 的 pts 就是 fleet points，与旧版不同)。

全参数分组 (字段全览见下表)：

| 分组 | 字段 | 语义 |
|---|---|---|
| 来源与质量 | `source`(`MarketAPI`)、`locInHyper`(`Vector2f`)、`quality`、`qualityMod`、`qualityOverride` | 见 "质量与市场" 小节；`source` 的 javadoc 原文："Use setSource() to set the source or directly call updateQualityAndProducerFromSourceMarket() to set the quality. Otherwise, the market's quality won't get used." |
| 身份 | `factionId`、`fleetType`、`factionOverride` | 势力与舰队类型；`factionOverride` 非 null 时舰队势力直接用该对象 (名称仍按 `fleetType` 取)|
| 点数 | `combatPts` / `freighterPts` / `tankerPts` / `transportPts` / `linerPts` / `utilityPts` | 六类预算，`getTotalPts()` 求和 |
| 规模 | `maxShipSize`(javadoc："0: fighter, 4: capital"，默认 1000)、`minShipSize`(javadoc："only affects combat ships; smaller ships will still be added with remaining fleet points")、`maxNumShips`、`ignoreMarketFleetSizeMult`、`onlyApplyFleetSizeToCombatShips`、`doNotPrune`、`doNotAddShipsBeforePruning` | 舰船尺寸与总数约束、市场规模乘数开关、裁剪开关 |
| 军官 | `withOfficers`(默认 `true`)、`officerLevelBonus`、`officerNumberBonus`、`maxOfficersToAdd`、`officerNumberMult`(默认 1)、`officerLevelLimit`、`commanderLevelLimit`、`commander`(`PersonAPI`)、`noCommanderSkills` | 指挥官与军官生成参数 (见 "指挥官与军官" 节)|
| 旗舰 | `flagshipVariantId`(`String`)、`flagshipVariant`(`ShipVariantAPI`)、`onlyRetainFlagship` | 旗舰装配覆盖与 "只留旗舰"(见 "旗舰" 节)|
| 随机与 doctrine | `random`、`timestamp`、`doctrineOverride`、`modeOverride`(`ShipPickMode`)、`forceAllowPhaseShipsEtc`、`treatCombatFreighterSettingAsFraction`、`blockFallback`、`aiCores`、`doNotIntegrateAICores` | 随机源、时间戳 (武器可用性判定)、教条覆盖、抽选模式覆盖等 |
| 装配杂项 | `allWeapons`、`averageSMods`(`Integer`，null = 不加 S 插)、`addShips`(`List<String>`，javadoc："If non-null: these ship variants will be added to the fleet before anything else")| 膨胀参数与显式预置舰船 |

三个构造器族 (内部都走 `init`)：

| 构造器 | 行为 |
|---|---|
| `(MarketAPI source, String fleetType, 六点数..., float qualityMod)` | `init(source, ...)`：`locInHyper = source.getLocationInHyperspace()`、`factionId = source.getFactionId()`、`timestamp = Global.getSector().getClock().getTimestamp()`、`updateQualityAndProducerFromSourceMarket()`(质量 = `Misc.getShipQuality(source, factionId)`)|
| `(MarketAPI source, Vector2f locInHyper, String factionId, Float qualityOverride, String fleetType, 六点数..., float qualityMod)` | 同上，另显式设 `locInHyper` / `factionId` / `qualityOverride` |
| `(Vector2f locInHyper, String factionId, Float qualityOverride, String fleetType, 六点数..., float qualityMod)` | 无市场版本：只写字段，**不**调用 `updateQualityAndProducerFromSourceMarket`(质量取字段默认值 0)|

`setSource(MarketAPI source, boolean updateQuality)`：换来源市场；`updateQuality=true` 时重算质量。

`updateQualityAndProducerFromSourceMarket()`：`quality = Misc.getShipQuality(source, factionId)`。

其中 `Misc`(`com.fs.starfarer.api.util.Misc`) 转发 `ShipQuality.getShipQuality`。

后者的完整类型名是 `com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality`。

市场生产质量 (`QualityData.quality` 计算值)+ 市场 `FLEET_QUALITY_MOD`，再减市场势力的 `doctrine.getShipQualityContribution()`、加目标势力的 `doctrine.getShipQualityContribution()`。

旧参数对象 `FleetParams`(V1，`com.fs.starfarer.api.impl.campaign.fleets.FleetParams`)：javadoc 原文：

**"'Pts' are not fleet points; 1/2/4/8 points = frigate/destroyer/cruiser/capital"**。

V1 的 pts 是尺寸当量不是 fleet points，V3 修正了这一点。

历史实现不使用，本节只在区分新旧时提及。

### 生成流程：FleetFactoryV3.createFleet

`FleetFactoryV3.createFleet(FleetParamsV3 params)`(`com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`) 返回已挂载 inflater 的完整舰队。十步流程：

1. **插件扩展点**：`Global.getSector().getGenericPlugins().pickPlugin(CreateFleetPlugin.class, params)` (`com.fs.starfarer.api.plugins.CreateFleetPlugin`) 非 null 时直接转交插件实现并返回 —— 默认逻辑只在不插拔件时执行。
2. **市场选择**(`pickMarket`)：`source` 非 null 直接用；否则用 `locInHyper` 就近找同势力市场 (独立势力/掠夺者/`CUSTOM_SPAWNS_AS_INDEPENDENT` 势力放宽为 "不对独立势力敌对" 即可)。
   - 优先找 `getSize() >= getMinPreferredMarketSize(params)` 的市场 (`getMinPreferredMarketSize`：总点数 ≤20 → 1、≤50 → 3、≤100 → 5、≤150 → 7、更大 → 8)。
   - `source` 与 `locInHyper` 都为空 → 建 **fake market**：`createMarket("fake", "fake", 5)` + `getStability().modifyFlat("fake", 10000)` + `FLEET_QUALITY_MOD + BASE_QUALITY_WHEN_NO_MARKET(0.5)` + `COMBAT_FLEET_SIZE_MULT 1f`，用完把 `params.source` 还原为 null。

::: info fake market 意味着什么
fake market 意味着质量 0.5、无市场势力贡献 —— 这解释了 "无市场版本 + 无 qualityOverride" 时舰队满船 D 插的根源。
:::

3. **空舰队创建**(`createEmptyFleet(factionId, fleetType, market)`)：`Global.getFactory().createEmptyFleet(factionId, faction.getFleetTypeName(fleetType), true)`(`FactoryAPI.createEmptyFleet`，`aiMode=true`—— 关闭补给/燃料消耗、事故与船员需求)。
   - 写内存 `$fleetType`(`MemFlags.MEMORY_KEY_FLEET_TYPE`) 与 `$sourceMarket`(`MEMORY_KEY_SOURCE_MARKET`；fake 市场除外)。
   - 给所有 `AbilitySpecAPI.isAIDefault()` 的能力 `fleet.addAbility(id)`。
   - `Misc.getSalvageSeed(fleet)`(写内存 `$salvageSeed`，`MemFlags.SALVAGE_SEED`)。
   - `fleet.getFleetData().setOnlySyncMemberLists(true)`(生成期免同步，收尾时还原)。
4. **doctrine 拆分**：`factionId` 缺省取 `source.getFactionId()`。
   - `ShipPickMode mode = Misc.getShipPickMode(market, factionId)`(质量数据供应商市场为 null 或供应商势力与目标势力不同 → `IMPORTED`，否则 `PRIORITY_THEN_ALL`；`modeOverride` 可覆盖)。
   - `combatPts` 按 `doctrine.getWarships/getCarriers/getPhaseShips` 权重 (各加 `random.nextInt(3)-2` 抖动；`isStrictComposition()` 时不抖、各减 1) 拆成 `warships/carriers/phase` 三份，余数并入 warships。
   - **`banPhaseShipsEtc`** = 非玩家势力 ∧ `combatPts < FLEET_POINTS_THRESHOLD_FOR_ANNOYING_SHIPS(50)`—— 小舰队禁用 "烦人" 舰 (相位与特定船，`forceAllowPhaseShipsEtc=true` 可放开)。
5. **点数投放**：`params.addShips` 预置变体优先 (`addToFleet(ShipRolePick)`：`createFleetMember(SHIP, variantId)` + 起名 + `addFleetMember`，返回 fleet point cost 扣预算)。
   - `treatCombatFreighterSettingAsFraction` 或随机命中 `doctrine.getCombatFreighterProbability()` 时按 `combatFreighters = min(freighterPts, warships)*1.5` 加战斗货船。
   - `addCombatFleetPoints`(warship/carrier/phase 三路 picker，按 `BASE_COUNTS_WITH_4`/`MAX_EXTRA_WITH_4` 尺寸计数表逐轮抽选，`maxShipSize`/`minShipSize` 过滤，`PRIORITY_THEN_ALL` 时优先抽 priority capital)。
   - 随后 `addFreighterFleetPoints` / `addTankerFleetPoints` / `addTransportFleetPoints` / `addLinerFleetPoints` / `addUtilityFleetPoints`(都是 `addPriorityOnlyThenAll`，有 priority 舰只用 priority)。
6. **超员裁剪**：成员数 > `maxShipsInAIFleet`(`settings.json` 中 = 30) 且未 `doNotPrune` → `pruneFleet`。
   - 按 combat/civilian FP 比例分配保留名额 (民用不足时至少保留 2+ 个名额)，五类列表按 hull size 降序、按 4:2:1:1(capital:cruiser:destroyer:frigate) 加权抽选。
   - 保留后 FP 仍超目标时用较小船替换较大船。
   - 最后 `fleet.getFleetData().sort()`。
7. **指挥官与军官**(`withOfficers=true` 时)：见 "指挥官与军官" 节。
8. **旗舰变体**：`fleet.getFlagship()` 非 null 时，`flagshipVariantId` 优先 (`Global.getSettings().getVariant(id)`；未注册 id 抛 `Ship hull variant [...] not found!`)，否则 `flagshipVariant`。
   - `fleet.getFlagship().setVariant(variant, false, true)`(非战机成员换装配会连船体一起换)+ `updateNumStatusesFromMember()`。
   - `onlyRetainFlagship=true` 时移除除旗舰外所有成员。
9. **收尾**：`fleet.forceSync()`；所有成员 `getRepairTracker().setCR(getMaxCR())`(新舰队 CR 直接拉满)。
   - `Misc.setSpawnFPMult(fleet, actualPoints / max(1, requestedPoints))`(写内存 `$spawnFPMult`，`FleetFactoryV3.KEY_SPAWN_FP_MULT = "$spawnFPMult"`；读取 `Misc.getSpawnFPMult`，`com.fs.starfarer.api.util.Misc`；使用方见 "内存" 节)。
10. **inflater 挂载**：`DefaultFleetInflaterParams p`：
   - `quality`(= `params.quality + params.qualityMod`，`qualityOverride` 非 null 时直接取 `qualityOverride`——**质量优先级：qualityOverride > (quality + qualityMod)**)。
   - `averageSMods`(传参时)、`persistent = true`、`seed = random.nextLong()`、`mode`、`timestamp`、`allWeapons`、`doctrineOverride` 时 `rProb = doctrine.getAutofitRandomizeProbability()`、`factionId`。
   - `Misc.getInflater(fleet, p)`(`com.fs.starfarer.api.util.Misc`：`Global.getSector().getPluginPicker().pickFleetInflater(fleet, params)` )→ `fleet.setInflater(inflater)`。

### 成员与角色

成员的来源是**角色 (role) 抽选**，不是直接指定变体：

- 角色数据：`data/world/factions/default_ship_roles.json`(键如 `combatSmall`、`fastAttack`、`freighterSmall`)+ 各势力 `*.faction` 文件的 `shipRoles` 覆写。
  - `combatSmallForSmallFleet` 是 `combatSmall` 的 "剔除了低数量时难缠舰船" 副本 (文件注释原文)。
- 消费链：`FleetFactoryV3.addToFleet(String role, ...)` → `market.pickShipsForRole(role, factionId, new ShipPickParams(mode, maxFP, timestamp, blockFallback), random)`(`FactionAPI.ShipPickParams`；`ShipPickMode`：`ALL` / `PRIORITY_ONLY` / `PRIORITY_THEN_ALL` / `IMPORTED`)→ `List<ShipRolePick>`。
  - 逐个 `addToFleet(ShipRolePick)`：`Global.getFactory().createFleetMember(FleetMemberType.SHIP, pick.variantId)`(成员即保存态成员)→ `fleet.getFleetData().pickShipName(member, random)` 起名 → `addFleetMember`。
- 快捷模板 `FleetFactory`(V1，同目录)：
  - `createPatrol(market, PatrolType.FAST/COMBAT/HEAVY)`(手写 `faction.pickShipAndAddToFleet(ShipRoles.*, ShipPickParams.all(), fleet)` 组合，`FactionAPI.pickShipAndAddToFleet`)+ `setAIMode(fleet, true)`；
  - `createMerc(...)`；
  - `createGenericFleet(factionId, name, qualityFactor, maxFP)`(`WeightedRandomPicker` 角色池 + 满载循环 + 无旗舰时补 `tempest_Attack`)。
  - V1 的 `setAIMode(fleet, true)` 其实只是 `forceSync()`(注释掉的补给装载代码)；V3 的 AI mode 在 `createEmptyFleet(..., true)` 创建时就定了。

### 旗舰与指挥官 (addCommanderAndOfficersV2)

`FleetFactoryV3.addCommanderAndOfficersV2`(入口 `addCommanderAndOfficers`，另有 `GenerateFleetOfficersPlugin` 扩展点优先)。`withOfficers=true` 且成员非空时执行：

- **候选过滤**：跳过战机联队、旗舰、平民 (`putOfficersOnCivShips=false` 时)、已有非默认舰长的成员；记录最大 hull size；按 `getFleetPointCost()` 加权把成员塞进两个 picker(`flagshipPicker` 只收尺寸 ≥ 最大尺寸的成员)；picker 为空时取 `members.get(0)` 作备用值。
- **旗舰**：`flagshipPicker.pickAndRemove()` 选出旗舰，从军官 picker 移除。
  - **`fleet.getFleetData().setFlagship(flagship)`**——`FleetDataAPI.setFlagship` 的副作用：清所有成员旗舰位、把旧旗舰/舰长是玩家的人物重置为默认舰长**、新旗舰舰长 = 舰队指挥官**(`setCaptain(getCommander())`)。
- **指挥官**：`params.commander` 非 null 直接用 (`isPlayer()` 时军官数量改由玩家 `getOfficerNumber()` 技能决定)。
  - 否则 `OfficerManagerEvent.createOfficer(faction, commanderLevel, pref, false, null, true, true, -1, random)`，等级 = `min(maxOfficerLevel, maxAIFleetCommanderLevel)`(`settings.json` 中 `maxAIFleetCommanderLevel` = 7；`commanderLevelLimit` 非 0 可覆盖)。
  - **`timid` personality 一律升为 `cautious`**；`params.commander` 为空时 `setRankId(Ranks.SPACE_COMMANDER)` + `setPostId(Ranks.POST_FLEET_COMMANDER)`。
  - `addCommanderSkills`：`noCommanderSkills=true` 直接返回；技能数按等级阈值 `commanderLevelForOneSkill`(`settings.json` 中 = 4)/ `commanderLevelForTwoSkills`(`settings.json` 中 = 6) 取 1/2；技能池 = `doctrine.getCommanderSkills()` 剔除 `Skills.TAG_PLAYER_ONLY`，`commanderSkillsShuffleProbability` 概率洗牌后逐个 `setSkillLevel(id, 1)`。
- **军官数量**：`numOfficers = min(maxOfficers, combatShips / combatShipsPerOfficer)`。
  - `maxOfficers = officerAIMax`(`settings.json` 中 = 10)+ `(int)(doctrine.getOfficerQuality() * officerAIMaxMercsMult) + officerNumberBonus`；
  - `combatShipsPerOfficer = baseCombatShipsPerOfficer`(`settings.json` 中 = 3)* `(1 - officerQualityMult*0.5)`；再加 `officerNumberBonus`、乘 `officerNumberMult`、受 `maxOfficersToAdd` 钳制。
  - **军官等级上限** `maxOfficerLevel = round(doctrine.getOfficerQuality()/2 + fleetSizeOfficerQualityMult * officerMaxLevel(5)) + officerLevelBonus`，`officerLevelLimit` 非 0 时覆盖；每个军官 `maxOfficerLevel - random.nextInt(3)`(≥1)。
- **技能偏好**(`getSkillPrefForShip`)：按武器槽尺寸加权 (LARGE×4/MEDIUM×2/SMALL×1) 统计能量/实弹/导弹占比与护盾/相位判定，合成 `SkillPickPreference` 枚举名 (`YES_ENERGY_NO_BALLISTIC_...`) 喂给 `createOfficer`。
- **上舰**：逐成员 `member.setCaptain(person)`(生涯侧舰长写入并重算成员 stats)；`params.commander.isPlayer()` 时额外 `fleet.getFleetData().addOfficer(person)` 入 roster。

### Inflater：膨胀语义

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

::: warning 膨胀前读到的是生成态装配
在 `inflateIfNeeded()` 之前读 `member.getVariant()`：武器仍是角色变体原样、无 D 插/S 插 (`source` 仍是 STOCK/生成时来源，非 REFIT)。引擎只在 AI/工具提示/战斗前自动触发膨胀 —— 需要完整装配时请先手动触发，不要在错误时机核对装配内容。
:::

`DefaultFleetInflater.inflate`(`com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater`) 行为要点：

- **可用池**：武器 = `faction.getKnownWeapons()` 过滤 `isWeaponKnownAt(id, timestamp)`，战机同理；按 tier/autofit 分类与 "priority"(`faction.isWeaponPriority`) 分组；船插 = `faction.getKnownHullMods()`。
- **质量驱动**：`getTierProbability(tier, quality)`——tier1 `min(0.9, 0.75+q)`、tier2 `min(0.9, 0.5+0.5q)`、tier3 `min(0.9, 0.25+0.25q)`，`allWeapons=true` 时全 1。
  - 每成员用 `CoreAutofitPlugin.doFit(currVariant, target, maxSmods, this)` 重新装配。
- **D 插**：`averageDmods = (1 - quality) / qualityPerDMod`(`settings.json` 中 `qualityPerDMod` = 0.2)。
  - 每成员 `getNumDModsToAdd = round(avg + random*3 - 2)` 钳 [0,5] 减已有 → `DModManager.setDHull` + `addDMods`。
  - **站 (station) 不加 D 插**。
- **S 插**：`averageSMods` 参数非 null 且非平民时，`getMaxSMods` = `averageSMods ± 1`(各 25% 概率) 钳 [0,3]，扣已有 S 插。
- **随机化**：`rProb = doctrine.getAutofitRandomizeProbability()`(`doctrineOverride` 时用其值) 概率开启 `RANDOMIZE`；升级概率 `min(0.1 + 0.5q, 0.5)`。
  - `p.seed` 非 null 时每成员 `random = new Random(p.seed * shipName.hashCode())`——**同种子重膨胀结果一致**(注释原文："need this so that when reinflating a fleet that lost members, the members reinflate consistently")。
- **结果写回**：每成员建空变体 `createEmptyVariant(fleet.getId() + "_" + index, target.getHullSpec())`，`target.isStockVariant()` 时 `setOriginalVariant(target.getHullVariantId())`。
  - `doFit` 后 `setSource(VariantSource.REFIT)` + `member.setVariant(currVariant, false, false)`。
  - `memberIndex` 逐成员递增。
- **跳过**：`TAG_NO_AUTOFIT` / `TAG_NO_AUTOFIT_UNLESS_PLAYER`(非玩家势力)tag 的 hull/变体跳过；`$overrideNoAutofit`(`MEMORY_KEY_FORCE_AUTOFIT_ON_NO_AUTOFIT_SHIPS`) 为 true 时强制全膨胀。
- 结尾 `setSyncNeeded()` + `syncIfNeeded()`。

`CampaignFleetAPI.deflate`：仅非玩家舰队。

成员变体 `originalVariant` 非 null 且非 no_autofit 时 `setVariant(注册表原变体, false, false)` 换回，然后 `inflated = null` + 同步。

**deflate 后再次触发 inflateIfNeeded 会按种子重膨胀**。

### 内存 (memory)

工厂写入的舰队内存键 (全部写 `fleet.getMemoryWithoutUpdate()`，读取同)：

| 键 (MemFlags 常量)| 写入点 | 使用方 |
|---|---|---|
| `$fleetType`(`MEMORY_KEY_FLEET_TYPE`)| `FleetFactoryV3.createEmptyFleet`；`FleetFactory.createEmptyFleet` | 舰队类型判定 (巡逻/袭击等行为脚本) 与调试 |
| `$sourceMarket`(`MEMORY_KEY_SOURCE_MARKET`)| 同上 (fake 市场除外)| 来源市场 id(重生/补给逻辑)|
| `$salvageSeed`(`SALVAGE_SEED`)| `Misc.getSalvageSeed(fleet)`(createFleet 调用即写入；`com.fs.starfarer.api.util.Misc`：种子缺失时按实体 id 哈希派生)| 掠夺/回收随机 |
| `$spawnFPMult`(`FleetFactoryV3.KEY_SPAWN_FP_MULT`)| `Misc.setSpawnFPMult(fleet, actual/requested)`(createFleet 收尾；`Misc`)| `BaseRaidStage.enoughMadeIt`(`com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage`)：`fleet.getFleetPoints() / mult` 还原 "请求 FP" 判断是否达到中止阈值 |
| `$overrideNoAutofit`(`MEMORY_KEY_FORCE_AUTOFIT_ON_NO_AUTOFIT_SHIPS`)| MOD 代码按需写入 | `inflate` 与 `deflate` 读取：强制对 no_autofit 舰船也膨胀 |

::: caution 舰队内存读取纪律
纯读取**必须**用 `getMemoryWithoutUpdate()`——`getMemory()` 会触发全插件 `updateFleetFacts` 回调，高频读取会造成回调风暴。
:::

### 投放 (spawn)

**`LocationAPI.spawnFleet`**(`com.fs.starfarer.api.campaign.LocationAPI`) 是 "创建后投放" 的正式入口，四参签名：

`SectorEntityToken anchor, float xOffset, float yOffset, CampaignFleetAPI fleet`

语义：

1. `this.addObject(fleet)`——**入容器**(`getAllEntities`/`getFleets` 可见，`isAlive()` 变 true)；
2. 坐标 = `anchor.getLocation() + (xOffset, yOffset)`(anchor 为 null 时坐标就是偏移本身)→ `fleet.setLocation(...)`；
3. `fleet.setNoEngaging(3.0F)`——**投放后 3 秒内不交战**(`CampaignFleetAPI.setNoEngaging`)。

原版使用方：遭遇战生成 (anchor = 玩家舰队 + 随机 ±100 偏移；重生，anchor null)、开局玩家舰队 (anchor null)。

替代路径：`location.addEntity(fleet)` + `fleet.setLocation(x, y)` + `fleet.forceSync()`——`spawnFleet` 省掉这三步并附赠 3 秒免战。

**投放必须发生在生涯引擎建立之后**；创建与投放之间舰队不在任何容器里 (`isAlive()==false`，地点容器语义)。

### 注册入口

::: caution 注册时机与守卫
本节示例注册**必须**发生在生涯引擎建立之后，沿用前置章节讲过的 `onGameLoad(boolean newGame)` + persistent data 键守卫模式；每个注册项用各自的键**，禁止**挂进插件已有的 `CLASS_MARK` 守卫内。注册块见 "完整最小例" 的调用点片段。
:::

## 规则强度

**必须/禁止 (RC8 仍有效，违反有明确可观察结果)**：

| 规则 | 依据 | 违反后的可观察结果 |
|---|---|---|
| 要让舰队质量取到市场，**必须**通过 `setSource(market, true)`、市场构造器或 `updateQualityAndProducerFromSourceMarket()` 设置来源 (`FleetParamsV3.source` javadoc 原文)| `FleetParamsV3` 的 `source` 字段 javadoc | `params.quality` 停留在 0：`createFleet` 里有效质量 = 0 → 平均 D 插 `(1-0)/0.2 = 5` 个/船、武器 tier 概率趋近下限，舰队战力明显低于同势力原版舰队 |
| 质量优先级：`qualityOverride` > `quality + qualityMod`；不设 `qualityOverride` 时 `qualityMod` 才叠加到市场质量上 | `FleetFactoryV3` 质量优先级分支 | 同时设了 `qualityOverride` 与 `qualityMod` 时 `qualityMod` 被无视；只设 `qualityMod` 不设来源时质量仍为 0 |
| 点数是目标不是硬上限 (javadoc 原文)；"舰队点数与请求不符" 是**正常现象**，还原请求值用 `$spawnFPMult` | `FleetParamsV3` javadoc；`FleetFactoryV3` 收尾段 | 以为生成 bug—— 实际受市场供应、`maxShipsInAIFleet` 裁剪与角色缺货影响；用 `Misc.getSpawnFPMult(fleet)` 还原 |
| `flagshipVariantId` / `flagshipVariant` 只在 `getFlagship()` 非 null 时应用；`setVariant(variant, false, true)` 会连船体一起换 | `FleetFactoryV3` 旗舰变体段；`FleetMemberAPI.setVariant` | 舰队为空时不生效；指定了异 hull 的旗舰变体后旗舰船体/角色与抽选时不同 |
| `setFlagship(member)` 的副作用**必须**接受：其他成员舰长被重置为默认、新旗舰舰长 = 舰队指挥官 | `FleetDataAPI.setFlagship` 内部实现 | 旗舰读回 `getCaptain() == getCommander()`；不要用逐成员 `setCaptain` 对抗该语义 |
| 膨胀前读取成员装配拿到的是**未按质量调整的生成态变体**；需要完整装配必须先触发 `inflateIfNeeded()`(引擎在 AI/工具提示/战斗前自动触发)| `CampaignFleetAPI.inflateIfNeeded` 内部实现 | 在 `inflateIfNeeded()` 之前读 `member.getVariant()`：武器仍是角色变体原样、无 D 插/S 插 (`source` 仍是 STOCK/生成时来源，非 REFIT)|
| `removeAfterInflating()` = `persistent == null || !persistent`；V3 生成 `persistent=true` → 膨胀后 inflater 保留 | `DefaultFleetInflater`；`FleetFactoryV3` | 自定义 inflater 忘记设 persistent 时，首次 `inflateIfNeeded` 后 `getInflater()` 变 null，二次膨胀不可能 |
| 读/写舰队内存**必须**用 `getMemoryWithoutUpdate()` | `CampaignFleetAPI` 的 `getMemory` / `getMemoryWithoutUpdate` 语义 (见 "内存" 节)| `getMemory()` 触发全插件 `updateFleetFacts` 回调，高频读取回调风暴 |
| 投放舰队**必须**在生涯引擎建立后调用 `spawnFleet`(或 `addEntity`+`setLocation`)，否则舰队不在容器、`isAlive()==false`、地图不可见 | 地点容器语义；`LocationAPI.spawnFleet` 实现语义 | `createFleet` 后没投放：`getFleets()`/地图上查无此舰队，无任何报错 |
| 生成带 D 插/S 插的随机结果由 `params.random` 与 inflater `seed` 决定；需要可复现时**必须**显式给 `params.random` | `FleetFactoryV3`；`DefaultFleetInflater`(种子一致性注释)| 同一参数两次生成结果不同；读档后重膨胀 (deflate→inflate) 结果与膨胀前不一致 |

**建议**：

- 需要确定性测试时：`params.random = new Random(固定种子)` 且不设 `qualityOverride` 时同时固定 `source`(质量含市场动态值，跨存档会变)。
- 生成后立即投放用 `spawnFleet` 一行 (含 3 秒免战)；需要自定义轨道/延迟再考虑 `addEntity` + `setLocation` + 自行 `setNoEngaging`。
- 给巡逻/袭击等 AI 舰队按需写 `MemFlags`(如 `FLEET_IGNORED_BY_OTHER_FLEETS`) 与 `setTransponderOn(false)`。

## 完整最小例

在既有 MOD 布局与 `GearInModPlugin` 之上新增一个演示脚本类 (不新增资源文件)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       └── GearInFleetFactory   # 本节新增（归本节所有）
└── jars\
```

### GearInFleetFactory

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
 * 完整最小例：生涯舰队工厂（FleetParamsV3 -> FleetFactoryV3.createFleet -> inflate -> spawn）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK 一次性门控
 * （沿用既有模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停（isDone()=true，引擎下一帧自动移除本脚本）：
 *   1. 基线：选定演示市场（优先 jangala），记录地点/锚点与市场质量；
 *   2. FleetParamsV3（market 构造器：source/quality/timestamp 一次就位），qualityOverride=0.6
 *      压质量（平均 D 插约 2 个/船，可观察），flagshipVariantId 指定旗舰变体；
 *   3. FleetFactoryV3.createFleet：记录舰队名/势力/类型/成员数/战机数/FP；
 *   4. 旗舰与指挥官：flagship hull 与变体、舰长是否就是舰队指挥官、指挥官等级/personality、军官数；
 *   5. 膨胀前状态：isInflated=false、inflater 已挂载、inflater.getQuality()==0.6；
 *   6. inflateIfNeeded 后：isInflated=true、inflater 保留（persistent=true）、
 *      成员变体来源 REFIT、D 插总数（质量 0.6 -> 平均 2 个/船）；
 *   7. 内存键：$fleetType / $sourceMarket / $salvageSeed / $spawnFPMult；
 *   8. 投放：location.spawnFleet(anchor, 0, 0, fleet) -> 入容器、坐标=锚点坐标、isAlive=true；
 *   9. 对照：FleetFactory.createPatrol（V1 固定模板）生成快速巡逻队并投放；
 *  10. 清理：同帧 removeEntity 移除两支舰队，不留任何持久副作用。
 */
public class GearInFleetFactory implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_FleetFactory";

    private static final Logger LOG = Global.getLogger(GearInFleetFactory.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetFactory() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
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

        // --- 1. FleetParamsV3：market 构造器（source + quality + timestamp 一次就位）---
        // combatPts=40, freighterPts=12, tankerPts=6；总点数 58 -> getMinPreferredMarketSize=5
        FleetParamsV3 params = new FleetParamsV3(market, FleetTypes.PATROL_MEDIUM,
                40f, 12f, 6f, 0f, 0f, 0f, 0f);
        params.qualityOverride = 0.6f;          // 覆盖市场质量：quality=0.6 -> 平均 D 插 (1-0.6)/0.2 = 2 个/船
        params.flagshipVariantId = "tempest_Attack"; // 旗舰装配覆盖（换 hull 为 tempest）
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

        // --- 7. 投放：入容器 + 定位（锚点坐标 + 偏移）+ setNoEngaging(3s) ---
        float anchorX = anchor.getLocation().x;
        float anchorY = anchor.getLocation().y;
        location.spawnFleet(anchor, 0f, 0f, fleet);
        LOG.info(String.format(
                "GearIn fleet factory spawned: inLocation=%s at=(%.0f, %.0f) anchor=(%.0f, %.0f) alive=%s",
                fleet.getContainingLocation() == location,
                fleet.getLocation().x, fleet.getLocation().y, anchorX, anchorY, fleet.isAlive()));

        // --- 8. 对照：FleetFactory（V1）固定模板 createPatrol ---
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

    /** 演示市场：优先 jangala（hegemony 核心市场），否则第一个有 open 子市场的市场。 */
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

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回 `executed`(演示执行后自停)；`runWhilePaused()` 返回 `false`；`advance` 包 `try/catch`(任一步抛错只记 ERROR 不自停失败，前置章节同款写法)。
- `REGISTER_MARK`：persistent data 守卫键 (`"GearIn_FleetFactory"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不含任何工作单元编号变体。
- `FleetParamsV3` market 构造器：`source=jangala`、`fleetType=patrolMedium`、六类点数、`qualityMod=0`——`params.quality` 即市场质量 (`Misc.getShipQuality`)，日志里与 `qualityOverride` 并排可对照优先级。
- `params.qualityOverride = 0.6f`：有效质量 0.6 → `averageDmods=(1-0.6)/0.2=2`，多数成员带 1–3 个 D 插；tier2 武器概率 `0.5+0.3=0.8`、tier3 `0.25+0.15=0.4`。
- `params.flagshipVariantId = "tempest_Attack"`：旗舰装配覆盖 (`createFleet` 旗舰变体段)，换 hull 为 `tempest`—— 日志 `flagship hull=tempest variant=tempest_Attack`。
- `fleet.getFlagship().getCaptain() == fleet.getCommander()`：`setFlagship` 的副作用 (`FleetDataAPI.setFlagship` 内部实现)，演示核对 `captainIsCommander=true`。
- `fleet.inflateIfNeeded()`：手动触发膨胀 (引擎在 AI/工具提示/战斗前也会自动触发)；`isInflated=true` 且 `inflaterAttached=true`(`persistent=true` 保留)；成员变体来源 REFIT、D 插合计按质量分布。
- 内存五键：`$fleetType`/`$sourceMarket` 来自 `createEmptyFleet`；`$salvageSeed` 来自 `Misc.getSalvageSeed`；`$spawnFPMult` 来自 `Misc.setSpawnFPMult`(实际/请求 FP)；`$overrideNoAutofit` 未写 (默认 false)。
- `location.spawnFleet(anchor, 0f, 0f, fleet)`：投放三合一；`spawned` 行核对 `inLocation=true`、坐标 == 锚点坐标、`alive=true`。
- `FleetFactory.createPatrol(market, PatrolType.FAST)`：V1 固定模板对照 (`patrolSmall` 类型、手写角色组合)；第二个 `spawnFleet` 带 +200,+200 偏移展示偏移参数。
- 清理段：`removeEntity` 移除两支舰队 —— 演示脚本**不留副作用**，可反复读档重放。

调用点 (工程片段 —— 方法所在类归前置章节所有，本节只演示调用，不改动那些文件)：

```java
    // 在 GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFleetFactory.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFleetFactory.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFleetFactory());
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用既有构建链路)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInFleetFactory.class`(可用 `jar tf` 核对)。

本节源码与 fixture 逐字一致 (fixture 位于 `tutorial-fixtures/units/C08-S04` 的 `data/scripts/gearin/campaign` 包内)，fixture 编译验收命令见 "预期结果"。

### 验证方案 (基于本例)

前置数据：既有 MOD 布局与插件接入 (含本节调用点片段)、`GearInFleetFactory` 源码。

操作步骤：

1. **fixture 编译**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC08s04Java --no-daemon`，确认以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInFleetFactory.class`。
3. **新建生涯**：确认 `starsector.log` 按 "预期结果" 第 3 条顺序出现全部 `GearIn fleet factory` 日志行 (`start` / `params` / `created` / `flagship` / `commander` / `pre-inflate` / `inflated` / `memory` / `spawned` / `patrol` / `done`)。
   - 关键核对：`flagship hull=tempest variant=tempest_Attack captainIsCommander=true`、`pre-inflate isInflated=false ... inflaterQuality=0.60`、`inflated isInflated=true inflaterAttached=true refitVariants=<N>`、`memory fleetType=patrolMedium sourceMarket=jangala ... spawnFPMult=<mult>`、`spawned inLocation=true ... alive=true`、`done fleetAlive=false patrolAlive=false`。
   - 全程无 `gear_in` 引起的 `ERROR`。
4. **界面核对**：在演示执行的那一帧附近暂停，`jangala` 行星位置应短暂出现两支舰队图标 (3 秒免战后立即被移除，观察窗口小；以日志为准)。
5. **只执行一次**：等待若干游戏日后日志无新增 `GearIn fleet factory` 行；保存并读档后仍无新增 (`REGISTER_MARK` 守卫已置位)；`jangala` 附近无残留舰队。
6. **故障注入 1(质量未设置)**：把 `FleetParamsV3` 换成 locInHyper 构造器并删除 `qualityOverride` 行，重启新档：`params` 行 `marketQuality=0.00`、`inflated` 行 `dmods` 明显增大 (平均 5 个/船)，对应故障分析第 1 条；改回后确认恢复。
7. **故障注入 2(不投放)**：注释掉 `location.spawnFleet(anchor, 0f, 0f, fleet);` 与清理行，重启新档：`spawned` 行缺失、地图上无舰队、无报错，对应故障分析第 6 条；改回后确认恢复。
8. **故障注入 3(膨胀前读装配)**：把 `fleet.inflateIfNeeded()` 移到日志 `spawned` 之后，观察 `inflated` 行之前读到的成员装配 (本演示不直接打印，可用调试器或临时日志核对 `getSource()` 非 REFIT)，对应故障分析第 5 条；改回后确认恢复。

通过信号：操作 1–5 全部符合；操作 6–8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件 (`tutorial-fixtures` 构建配置等) 做过修改。

::: tip 日志核对技巧
在 `Starsector/starsector-core/starsector.log` 中按时间戳检索 `GearIn fleet factory` 前缀即可定位本示例的全部输出行；出现 `ERROR` 时优先查看 `gear_in` 包相关的堆栈。
:::

## 预期结果

1. **fixture 编译**：在 Git Bash 执行。
```text
gradle -p tutorial-fixtures compileC08s04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInFleetFactory.class`。
3. **新建生涯 (首次进入存档后第一帧)**：`starsector.log` 依次出现。
```text
GearIn fleet factory start: market=jangala size=6 location=jangala quality=<市场质量> GearIn fleet factory params: faction=hegemony fleetType=patrolMedium marketQuality=<Q> qualityOverride=0.60 totalPts=58 GearIn fleet factory created: name=Patrol faction=hegemony fleetType=patrolMedium members=<N> fighters=<战机数> fp=<FP> GearIn fleet factory flagship: hull=tempest variant=tempest_Attack captainIsCommander=true GearIn fleet factory commander: name=<指挥官名> level=<L> personality=<personality> officers=<M> GearIn fleet factory pre-inflate: isInflated=false inflaterAttached=true inflaterQuality=0.60 GearIn fleet factory inflated: isInflated=true inflaterAttached=true refitVariants=<N> dmods=<D> GearIn fleet factory memory: fleetType=patrolMedium sourceMarket=jangala salvageSeed=<S> spawnFPMult=<mult> GearIn fleet factory spawned: inLocation=true at=(<ax>, <ay>) anchor=(<ax>, <ay>) alive=true GearIn fleet factory patrol: name=Fast Picket members=<P> fp=<FPp> flagship=<hull> GearIn fleet factory done: fleetAlive=false patrolAlive=false
```
其中 `<N>` 是生成成员数 (40+18 点数预算下典型 7–12 艘)、`<战机数>` 是战机联队数 (无航母时为 0)、`<FP>` 是实际 fleet points(**可能低于 58**—— 点数是目标不是硬上限)、`<L>` 是指挥官等级 (`officerLevelBonus=1` 生效)、`<M>` 是军官数 (≥1 为正常)、`<D>` 是 D 插总数 (质量 0.6 下典型 ≈ 2×成员数)、`<mult>` 是 `$spawnFPMult`(实际/请求)。`flagship` 与 `patrol` 两行的 `fleetAlive=false` 证明清理生效。
4. **游戏内地图**：演示期间 (日志可见时)`jangala` 行星位置短暂出现两支舰队图标 (3 秒免战后移除，若看不到可在暂停时核对日志)；演示结束后地图无残留。
5. **读档**：不出现任何 `GearIn fleet factory` 日志 (`REGISTER_MARK` 守卫已置位、脚本不再注册)；`jangala` 附近无残留舰队。
6. **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR`(故障注入的预期例外场景除外)。

静态自查：

- `GearInFleetFactory` 与 fixture 逐字一致 (包括 import 顺序与注释)。
- `REGISTER_MARK` 与调用点片段的键逐字一致。
- 没有把工作单元编号写进任何运行时名称 (类名、包名、persistent data 键、日志前缀均不含单元编号变体)。
- 示例只消费既有 GearIn ID 与原版 ID(无新增资源 ID)。

## 实例驱动故障分析

1. **舰队 "凭空生成" 但战力奇低 (满船 D 插、武器全是低档)**。
   - 触发：`new FleetParamsV3(locInHyper, ...)` 无市场版本且没设 `qualityOverride`。
   - 可观察：`params.quality` 是默认 0 → 有效质量 0 → 平均 D 插 5 个/船、tier 概率下限 (`getTierProbability`)；与同势力原版舰队对比战力悬殊。
   - 修正：用 market 构造器 / `setSource(market, true)`(`FleetParamsV3.source` javadoc 原文)；或显式 `qualityOverride`。
2. **舰队生成在错误生命周期阶段 (`onApplicationLoad` 里调 `createFleet`)**。
   - 触发：注册点放错。
   - 可观察：`Global.getSector()` 为 null → NPE 启动中止 (生命周期阶段语义)；或 economy 未加载时 `getMarketsCopy()` 为空 → `pickMarket` 返回 null → 以 fake market 作备用方案。
   - 修正：注册放 `onGameLoad` 守卫块内 (注册守卫语义)。
3. **指定了 `flagshipVariantId` 但旗舰没换**。
   - 触发：id 拼错 (`SettingsAPI.getVariant` 抛 `Ship hull variant [...] not found!`，变体主题讲过)；或舰队为空成员 (`getFlagship()` 为 null，旗舰变体应用条件不成立)；或 `flagshipVariantId` 与 `flagshipVariant` 同时设置 (前者优先)。
   - 可观察：异常中断或旗舰仍是抽选 hull。
   - 修正：id 用 `getAllVariantIds()` 核对；检查生成成员数非 0。
4. **指挥官/军官完全不生成**。
   - 触发：`withOfficers=false`(`createFleet` 跳过该步)；或成员列表为空 (`addCommanderAndOfficersV2` 对空成员直接返回)。
   - 可观察：`getCommander()` 是默认指挥官、`getOfficersCopy()` 空。
   - 修正：确认 `withOfficers` 默认 true；先排查角色缺货导致的空舰队 (故障 10)。
5. **生成后读成员装配，发现 "没装武器"**。
   - 触发：在 `inflateIfNeeded()` 之前读 `member.getVariant()`。
   - 可观察：变体是角色变体原样 (STOCK/生成时来源、无 D 插、武器未按质量调整)—— 膨胀是延迟的，`createFleet` 不膨胀 (`CampaignFleetAPI.inflateIfNeeded` 内部实现)。
   - 修正：需要完整装配先 `fleet.inflateIfNeeded()`；引擎在 AI/工具提示/战斗前会自动触发，不要自己在错误时机核对装配内容。
6. **舰队创建成功但地图上/`getFleets()` 里找不到**。
   - 触发：`createFleet` 后忘了投放 (`spawnFleet` / `addEntity`)。
   - 可观察：舰队对象存在 (`isAlive()==false`)，不在任何地点容器 (地点容器语义)，`getFleets()` 与地图均无，无任何报错。
   - 修正：`location.spawnFleet(anchor, x, y, fleet)` 或 `location.addEntity(fleet)` + `setLocation`。
7. **舰队位置不对 (堆在原点或偏移方向反了)**。
   - 触发：`spawnFleet(null, x, y, fleet)` 误以为 null anchor 表示 "用市场锚点"。
   - 可观察：坐标 = 偏移本身 (anchor 为 null 分支)，与市场位置无关。
   - 修正：anchor 传 `market.getPrimaryEntity()`(或任意实体)；偏移是 "锚点坐标 + 偏移"。
8. **同一存档里演示/生成重复执行，舰队成双**。
   - 触发：注册没有 persistent data 守卫 (或键挂进了插件已有的 `CLASS_MARK` 守卫内)。
   - 可观察：每次读档重放，`onGameLoad` 新档与读档各生成一次，日志成双。
   - 修正：本节调用点片段的 `REGISTER_MARK` 守卫 (每个注册项各自键)。
9. **读档后舰队装配与膨胀前不同**。
   - 触发：读档时舰队成员变体未落档 (`originalVariant` 回指注册表，变体持久化边界) 或 deflate 后重膨胀。
   - 可观察：膨胀结果随 `seed` 随机 —— 同一存档每次 deflate→inflate 结果一致 (`seed * shipName.hashCode()`)，但**跨创建**(新 `random`) 结果不同。
   - 修正：需要跨创建可复现时显式 `params.random = new Random(固定种子)`；不要依赖默认随机。
10. **舰队点数与请求严重不符 (过小)**。
   - 触发：市场缺货 (`pickShipsForRole` 返回空/不足)、`maxShipsInAIFleet(30)` 超员裁剪 (`pruneFleet`)、六类点数分配后 `rem.fp` 用尽。
   - 可观察：`getFleetPoints()` < 请求点数且无报错 ——javadoc 原文 "targets not hard limits"。
   - 修正：用 `Misc.getSpawnFPMult(fleet)` 还原请求值；排查角色配置 (`default_ship_roles.json` 与势力覆写)。
11. **D 插数量与预期不符 (过多/为零)**。
   - 触发：`qualityOverride` 与市场质量混用 (优先级 `qualityOverride > quality + qualityMod`)、或 `averageDmods` 公式里的 `qualityPerDMod` 被误当成 D 插上限。
   - 可观察：质量 0.6 时平均 `(1-0.6)/0.2=2` 个/船、质量 1.0 时平均 0 个 (`getAverageDmodsForQuality`)。
   - 修正：核对有效质量 (inflater 的 `getQuality()` 是权威值，本节演示 pre-inflate 日志即此)。
12. **投放后舰队立刻被玩家/他人攻击**。
   - 触发：用 `addEntity` + `setLocation` 手动投放且没调 `setNoEngaging`。
   - 可观察：敌对舰队/玩家贴近时立即进入交战 (`spawnFleet` 附赠的 `setNoEngaging(3.0F)` 缺失)。
   - 修正：就近用 `spawnFleet`；自定义投放路径要自行 `fleet.setNoEngaging(...)`。
13. **`FleetMembershipChecker` 找不到**。
   - 触发：按旧材料引用该名称。
   - 可观察：**0.98a-RC8 公开 API 与引擎内部源码中不存在 `FleetMembershipChecker`**，`LocationAPI.spawnFleet` 当前签名为四参；该名称在本基线无对应对象。
   - 修正：按 RC8 现状用 `spawnFleet(anchor, xOffset, yOffset, fleet)`；不要在代码或文档中引用不存在的类。

## 总结

本节把生涯舰队工厂讲成一条链：

- **参数**(`FleetParamsV3`：来源市场+质量、六类点数、规模、军官、旗舰、杂项)。
- **生成**(`FleetFactoryV3.createFleet`：插件点 → `pickMarket`(fake 备用方案)→ `createEmptyFleet`(AI mode/名字/能力/内存)→ doctrine 拆分 → 按角色填舰 (`pickShipsForRole` + `ShipPickMode`)→ 超员裁剪 → 指挥官与军官 → 旗舰变体 → `forceSync`+CR 拉满 → inflater 挂载 + `$spawnFPMult`)。
- **膨胀**(`inflateIfNeeded` 延迟触发；质量决定武器 tier 概率与 D 插；`averageSMods` 决定 S 插；结果 REFIT + `originalVariant`；`persistent=true` 保留 inflater)。
- **内存**(`$fleetType` / `$sourceMarket` / `$salvageSeed` / `$spawnFPMult` / `$overrideNoAutofit`，纯读写用 `getMemoryWithoutUpdate()`)。
- **投放**(`LocationAPI.spawnFleet` 入容器+定位+3 秒免战；或 `addEntity`+`setLocation`)。

记忆要点：

- **点数是目标不是硬上限**(javadoc 原文)；实际点数受供应与裁剪影响，`$spawnFPMult` = 实际/请求。
- 质量优先级：`qualityOverride` > `quality + qualityMod`；质量必须来自市场 (`setSource`/构造器)，否则为 0 → 满船 D 插。
- 成员 = 角色抽选 (`ShipPickMode` 决定来源)；旗舰从最大 hull size 加权抽选；`setFlagship` 把旗舰舰长设为指挥官并重置其他成员舰长。
- 指挥官默认 `OfficerManagerEvent.createOfficer`，`timid` 升 `cautious`；技能数按等级阈值 4/6 取 1/2，来自 doctrine 技能表。
- 膨胀是延迟的：`createFleet` 只挂 inflater；`inflateIfNeeded` 首次触发；`persistent=true` 膨胀后 inflater 保留。
- D 插平均 `(1-quality)/0.2` 个/船；同 `seed` 重膨胀结果一致。
- 投放：`spawnFleet(anchor, xOffset, yOffset, fleet)` 三合一 (入容器 + 锚点坐标+偏移 + `setNoEngaging(3.0F)`)；anchor 可 null。
- 0.98a-RC8 不存在 `FleetMembershipChecker`，`spawnFleet` 是四参签名。

舰队行为 (任务、AI) 与舰队间交互的主题，逻辑上直接消费本节生成的舰队对象；成员对象、人物与变体三个主题则是本节三块底座。

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
- `tutorial-fixtures/units/C08-S04/src/main/java/data/scripts/gearin/campaign`：`GearInFleetFactory` 源码目录 (页面代码块的磁盘对照)。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。
