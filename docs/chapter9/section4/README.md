# 人物、成员与变体的状态交接

本节把生涯侧与战斗侧的"状态交接链"讲透：装配（`ShipVariantAPI`）怎么变成成员 stats（`FleetMemberAPI.getStats()`），人物技能怎么到达成员 stats 与战斗舰船 stats；stats 重建在哪些时机被触发、重建时哪些修改会被保留、哪些会被丢弃，以及 `MutableStat` 的延迟重算与 modifier ID 幂等规则在其中扮演的角色。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 复述完整交接链的四个环节与各自入口：
  - 装配 → stats（新建 stats 时读船体基值 + `FleetMemberAPI.updateStats` 重建序列）
  - 人物技能 → 成员 stats（`applyPersonalToStats` / `applyFleetwideToStats`）
  - 成员 stats → 战斗实体（`instantiateForCombat` → 舰船对象创建（`createShip`）→ `Ship` 构造器 → `addObjectToEngine`）
  - 说出每个环节的触发点与顺序；
- 说出 `FleetMemberAPI.updateStats()` 每次**整体新建** stats 对象、按固定序列重放（舰长个人技能 → 舰队指挥官舰队向技能 → 船插 `applyEffectsBeforeShipCreation` → `flux_capacitors`/`flux_vents` → CR/船员 → 封存倍率）
  - 以及"运行时直接 `modifyFlat` 到 `getStats()` 返回值的修改不跨重建"这一硬事实；
- 说出技能重放的归属条件：舰长个人技能（SHIP 族）只在成员属于舰队（`getFleetData() != null`）时应用；舰队指挥官舰队向技能（ALL_SHIPS_IN_FLEET）经 `applyFleetwideToStats` 应用且跳过战机；
- 说出部署链的顺序细节：舰船对象创建（`createShip`）新造战斗 stats、`Ship` 构造器绑定实体并重放船插与舰长技能、`addObjectToEngine` 触发一次 `applyEffectsAfterShipAddedToCombatEngine`、战斗内 `ShipAPI.setCaptain` 解挂/挂载个人技能（含子模块）；
- 复述 `MutableStat` 的延迟重算（读 `getModifiedValue()` 才重算）、同 ID 同类型幂等覆盖、引擎保留 ID 清单（`flux_capacitors`/`flux_vents`、`cr_effect`、`crew skill bonus`/`crew understrength`、`mothballed_mod_id`）——交接链重放安全的前提；
- 从症状定位典型故障：生涯侧数值"自己变回去"、换舰长后 stats 无变化、界面数值不刷新、生涯与部署数值对不上、战斗内换舰长旧加成残留、自定义 modifier 被引擎覆盖、重放入口内递归栈溢出、跨战斗缓存失效。

## 前置知识

本节建议在完成前置单元后阅读，您需要已经理解：

- `MutableStat` 概念、`(base + base*percent/100 + flat) * mult` 公式、modifier ID 稳定性三规则（同 ID 同类型幂等覆盖、跨类型独立、`unmodify(source)` 清三类）（C07-S03）——本节的 stats 重建与重放全部建立在这套语义上；
- 舰队成员（`FleetMemberAPI`）：`getVariant()`/`setVariant(variant, withRefit, withStatsUpdate)`（传入引用不克隆）、`getStats()`、`addFleetMember` 自动行为、`setCaptain` 的生涯侧入口（C08-S03）；
- 人物与 captain：`PersonAPI.getStats()` 是技能等级与刷新入口；旗舰舰长特例；默认舰长保存压缩（C09-S01）；
- 技能效果作用域：SHIP 族（`ShipSkillEffect`）与 ALL_SHIPS_IN_FLEET 的应用入口在 `FleetMemberAPI.updateStats` / 舰船对象创建 / `Ship` 构造器
  - FLEET / CHARACTER_STATS 在 `refreshCharacterStatsEffects` 即时重放；effect id 即 modifier id（C09-S02）；
- `ShipVariantAPI`：`clone()` 深拷贝、`setSource`、`toJSONObject`、部署期创建舰船时对成员变体的武器组改写（写回传入变体）、成员变体 STOCK/HULL 来源不落档（C09-S03）；
- 模块船：模块在部署期由 `spawnStationModules` 生成，是独立 `ShipAPI` 实体，本节部署链对模块同样生效（C04-S03）；
- `GearInModPlugin` 所有权与 `onGameLoad` 注册守卫：本节调用点片段只消费它，不改动其文件（C06-S02）。

## 术语

按术语约定，本节首次完整讲解两个词（`MutableStat` / `FleetMemberAPI` 的中文写法与首次讲解位置见术语约定表）：

- **状态交接（handoff）**：状态从一个对象流向下一个对象的过程——装配内容流入成员 stats、人物技能流入成员 stats、成员 stats 流入战斗舰船。交接经 `MutableShipStatsAPI` 进行，交接的完成标志是"stats 对象被（重新）构造并重放"。
- **stats 重建（stats rebuild）**：`FleetMemberAPI.updateStats()` 整体新建 stats 对象并按固定序列重放全部效果的行为。重建是交接的"结算时刻"：重建后生效的修改保留，重建前直接写在旧对象上的修改被丢弃。

## 文件与 API 链路

### 交接链总览

```text
装配（ShipVariantAPI）
  │  舰船统计对象.create(variant)：引擎/反应堆数值成为各 MutableStat 的 base
  │  FleetMemberAPI.updateStats：船插 applyEffectsBeforeShipCreation + flux_capacitors/flux_vents + CR/船员
  ▼
成员 stats（FleetMemberAPI.getStats() → MutableShipStatsAPI）
  ▲
  │  setCaptain / setFleetCommanderForStats / setVariant(withStatsUpdate) / 舰队同步 / 读档
  │  └ 触发 updateStats 重建
  │
人物技能（PersonAPI.getStats() → MutableCharacterStatsAPI）
  │  applyPersonalToStats（SHIP 族，舰长，需成员入队）
  │  applyFleetwideToStats（ALL_SHIPS_IN_FLEET，舰队指挥官）
  ▼
战斗 stats（舰船对象创建（createShip）新造 → Ship 构造器绑定实体并重放）
  │  CombatEngineAPI.addObjectToEngine → applyEffectsAfterShipAddedToCombatEngine（一次）
  ▼
战斗实体（ShipAPI）
```

::: info 为什么交接链要存在
生涯侧与战斗侧不共享 stats 对象：成员 stats 属于舰队数据结构，战斗 stats 属于战斗场景，两边的数值必须通过"重建 + 重放"在各自场景内重新算出来。

交接链因此成为跨场景状态的唯一合法通道——挂在重放入口上的效果（技能、船插、buff）天然获得幂等性与确定性；代价是重建时丢弃一切直接写在 stats 对象上的运行时修改。

这一取舍贯穿本节全部规则。
:::

### 环节一：装配 → stats（stats 新建与 updateStats 重建序列）

成员与战斗侧的 stats 都实现 `MutableShipStatsAPI`。

按变体新建 stats 时，各 `MutableStat` 的**基值**来自船体 spec：`EngineSpecAPI` 的 `maxSpeed`/`acceleration`/`deceleration`/`maxTurnRate`/`turnAcceleration` → 机动性五统计项。

反应堆数值 → `fluxCapacity`/`fluxDissipation`（`ShipHullSpecAPI.getFluxCapacity()`/`getFluxDissipation()`，数据源是 `ship_data.csv` 的 `max flux` / `flux dissipation` 列，由船体 CSV 加载读入）。

另有 `breakProb`、维修率、传感器（`initSensorStats`）。

**基值只来自船体**——变体的武器/船插不改变基值，只以 modifier 形式叠加。

`FleetMemberAPI.updateStats()`是生涯侧唯一的全量重建入口，顺序固定：

| 步 | 内容 | 出处 |
|---|---|---|
| 1 | 整体新建 stats 对象（按当前变体），旧对象作废 | — |
| 2 | `stats.setFleetMember(this)` | — |
| 3 | 舰长个人技能：`captain.getStats().applyPersonalToStats(stats, hullSize)`——**仅当 `getFleetData() != null`**（成员已入队）| — |
| 4 | 舰队指挥官舰队向技能：`getFleetCommanderForStats()` 优先，否则回退 `fleetData.getCommander()`，`applyFleetwideToStats(stats, hullSize)` | — |
| 5 | 变体全部船插 `applyEffectsBeforeShipCreation(hullSize, stats, id)` | — |
| 6 | `flux_capacitors` = `numFluxCapacitors * FLUX_PER_CAPACITOR`、`flux_vents` = `numFluxVents * DISSIPATION_PER_VENT`（flat 写入，引擎保留 ID）| — |
| 7 | `updateOnlyStatsThatCanRelyOnCROrCrew`：船员不足倍率 + `CombatReadinessPlugin.applyCRToStats(CR)` | — |
| 8 | 封存（mothballed）时写 `mothballed_mod_id` 的 supplies/cargo/fuel/crew 倍率，否则 unmodify | — |
| 9 | `stats.getDynamic().removeUmodified()`、`repairTracker.updateRates()` | — |
| 10 | 无甲板（`getNumFlightDecks() <= 0`）时清空变体 `getWings()`；`fleetData.setSyncNeeded()` | — |

::: caution 禁止直接写 `getStats()` 返回值
**禁止**对 `FleetMemberAPI.getStats()` 的返回值直接 `modifyFlat` / `modifyPercent` 并期待跨重建保留。

第 1 步整体新建 stats 对象，旧对象连同直接写在它上面的 modifier 一起作废——数值"自己变回去"，且无任何报错。

要跨重建保留的修改必须挂在重放入口上（船插 `applyEffectsBeforeShipCreation`、技能效果、buff 系统）。

完整清单见「规则强度」。
:::

两个直接推论：**重建 = 丢弃**——第 1 步新造对象意味着任何直接写在旧 stats 对象上的 modifier 都不复存在（`GearInStatsRebuild` 的 transient 步骤）。

**重放 = 幂等 **——第 3–6 步的写入全部用固定来源 ID，同 ID 同值覆盖，因此重放任意次数值不变（第 4 步在舰队同步里还会被再执行一次，见下）。

### 环节二：人物技能 → 成员 stats

技能重放的两条通道：

- **个人通道** `applyPersonalToStats(stats, hullSize)`：遍历 `getShipEffects()`（`type == SHIP` 且 `getSkillLevel(governingSkill) >= requiredSkillLevel`），逐效果 `apply(stats, hullSize, effectId, level)`
  - effect id 是 `<技能id>_ship_<序号>`——「effect id 即 modifier id」的规则在这里落地。
- **舰队通道** `applyFleetwideToStats(stats, hullSize)`：遍历 `getAllShipsInFleetEffects()`（`type == ALL_SHIPS_IN_FLEET`），**跳过 `hullSize == FIGHTER`**。

舰长侧触发点（都会走到 `updateStats`）：

- `FleetMemberAPI.setCaptain(person)`（`FleetMemberAPI`）：人物变化且 `withStatsUpdate`（默认 true）→ `updateStats()`。
- `FleetMemberAPI.setFleetCommanderForStats(person, fleetData)`：**只存 `fleetCommanderForStats` 并 `setStatUpdateNeeded(true)`**——不立即重建，下一次 `getStats()` 才重建
  - 注意 RC8 实现里第二个参数 `fleetData` **被忽略**，舰队数据始终来自成员自己的 `fleetData`。
- `getStats()` 延迟重建：`statUpdateNeeded == true` 时先 `updateStats()` 再返回；`setStatUpdateNeeded(true)`还会使缓存的强度值失效。

成员侧前提：第 3 步的 `applyPersonalToStats` 有 `getFleetData() != null` 条件——**未入队的成员换舰长，stats 没有任何变化** （`GearInCaptainToMember` 的 preJoin 步骤即此）。

舰队同步（`FleetDataAPI.syncIfNeeded`）：`needsSync` 时对每个成员 `updateStats()`，随后按 `getFleetCommanderForStats()` → `getFleetCommander()` → `commander` 回退链**再执行一次** `applyFleetwideToStats`——同 ID 幂等，不会翻倍。

任何写 `setSyncNeeded` 的舰队操作 （`addFleetMember`、`removeFleetMember`、`setVariant` 等）最终都会经这个入口重建全员 stats；读 `getNumMembers()` 等视图 API 会触发同步。

::: warning 读视图 API 会触发一次全员重建
`getNumMembers()` 等视图 API 会触发 `syncIfNeeded`——在日志里读到 `count` 的那一刻，全员 stats 已经重建了一遍（含舰队向技能的二次重放）。这不是 bug：同 ID 幂等保证数值不变。调试时别把"我读了一次成员数"误当成重建来源。
:::

### 环节三：成员 stats → 战斗实体（部署链）

部署链（`instantiateForCombat`）：

1. 解析舰队指挥官：`getFleetCommander()` → `getFleetCommanderForStats()` → 战斗管理器默认指挥官；
2. 舰船对象创建：`createShip(variant, shipName, ..., captain, commander, member)`；
3. `updateStatsBasedOnCrew(战斗 stats)`——船员不足倍率写到**战斗** stats；
4. 封存或 `!canBeDeployedForCombat()` → `controlsLocked(true)`；
5. `CombatReadinessPlugin.applyCRToStats(CR, 战斗 stats, hullSize)`、`status.applyToShip`。

舰船对象创建（`createShip`）的顺序：

| 步 | 内容 | 出处 |
|---|---|---|
| 1 | 新建战斗 stats 对象——**部署时新造**（不是成员 stats 的引用）| — |
| 2 | `stats.setFleetMember(member)` | — |
| 3 | 指挥官 `applyFleetwideToStats(战斗 stats, hullSize)`（ALL_SHIPS_IN_FLEET）| — |
| 4 | 成员 buff 逐个应用 | — |
| 5 | `new Ship(stats, variant, true, captain, memberId, member)`——构造器内绑定实体、重放 | — |
| 6 | `setOriginalCaptain(captain)`、`setFleetCommander(commander)` | — |
| 7 | 全部船插 `applyEffectsAfterShipCreation(ship, id)` | — |
| 8 | 舰长 `applyPersonalAfterShipCreationToShip` | — |
| 9 | 若有指挥官：`setCaptain(null)` 再 `setCaptain(captain)` 的重挂舞蹈（整理个人/舰队向技能的应用次序）| — |

`Ship` 构造器：

- `stats.setEntity(this)` / `stats.setFleetMember(var6)`——战斗 stats 与实体互相绑定，`MutableShipStatsAPI.getEntity()` 由此非 null；
- 全部船插 `applyEffectsBeforeShipCreation`（`var3=true` 时）；
- `setCaptain(var4)` → `applyPersonalToShip`（舰长技能）：个人 SHIP 技能 + `applyPersonalAfterShipCreationToShip`（含系统/相位数值烘焙 `updateValuesFromStats`）+ 指挥官 `applyFleetwideToShip`；
- `flux_capacitors`/`flux_vents` 再写一次——与生涯序列同 ID 幂等。

入引擎：`CombatEngineAPI.addObjectToEngine`（`CombatEngineAPI`）→ `Ship.applyEffectsAfterShipAddedToCombatEngine`（`Ship`）——全部船插的第三时机，**每个战斗实体只触发一次**。

模块船走同一条链：`spawnStationModules`（舰船对象创建）为每个模块变体新建 `FleetMemberAPI` 再 `createShip`。

战斗内换舰长：`ShipAPI.setCaptain(person)`（`Ship`）先 `unapplyPersonalToShip` 旧舰长（**含子模块**），再 `applyPersonalToShip` 新舰长（含子模块）——统计立即变化；`getPersonality()` = 舰长 personality id。

`getOriginalCaptain()` 返回部署初始舰长。

### 环节四：MutableStat 延迟重算与保留 ID

`MutableStat` 本身是公开 API 类（`com.fs.starfarer.api.combat.MutableStat`，公开具体类）：

- **延迟重算**：`modifyFlat`/`modifyPercent`/`modifyMult`/`setBaseValue` 只做标记，`getModifiedValue()`/ `getFlatMod()`/`getPercentMod()`/`getMult()` 读取时才真正重算
  - `modified = base + base*percent/100 + flat`，再 `* mult`。
- **同 ID 幂等**：`modifyFlat(source, value)` 在"同 source 已存在且值相同"时只更新描述；`unmodify(source)` 同时清理三类。交接链的"重放任意次不翻倍"完全依赖这套语义。
- **引擎保留 ID**（本节列全，均不可占用）：
  - `flux_capacitors`/`flux_vents`（`FleetMemberAPI.updateStats`、`Ship` 构造器）
  - `cr_effect`（`CRPluginImpl.applyCRToStats`，`com.fs.starfarer.api.impl.combat.CRPluginImpl`，按 CR 写故障率/机动性/伤害修正）
  - `crew skill bonus`/`crew understrength`（`CRPluginImpl.applyMaxCRCrewModifiers`，`maxCombatReadiness`）
  - `mothballed_mod_id`（`FleetMemberAPI`）。

::: warning 同一来源 ID 写不同值，后写者静默覆盖
两个位置共用同一个来源 ID、却在不同时机写不同值时，没有报错——只有最后一次写入生效（`modifyFlat` 只在"同 source 已存在且值相同"时保持幂等）。排查"数值不对但没有任何错误"时，先检查是否有两处代码共用了同一个来源 ID。
:::

### 触发点汇总

| 触发 | 行为 | 出处 |
|---|---|---|
| `FleetMemberAPI` 构造 / 读档 | 首次 `updateStats()` | `FleetMemberAPI` |
| `setCaptain(person)`（人物变化）| 立即 `updateStats()` | `FleetMemberAPI` |
| `setFleetCommanderForStats(person, data)`（人物变化）| 只标记 `statUpdateNeeded`，下次 `getStats()` 重建 | `FleetMemberAPI` |
| `setVariant(variant, withRefit, withStatsUpdate)` | `withRefit` 算 CR 影响并 `applyCREvent`（`computeVariantDiffCRImpact`）；`withStatsUpdate` 立即 `updateStats()` | `FleetMemberAPI` |
| `setStatUpdateNeeded(true)` | 标记；下次 `getStats()` / `updateStats()` 重建 | `FleetMemberAPI` |
| 舰队同步 `syncIfNeeded` | 全员 `updateStats()` + 舰队向技能二次重放 | `FleetDataAPI` |
| 部署 `instantiateForCombat` | 新造战斗 stats 并完整重放（环节三）| `FleetMemberAPI` |
| 战斗内 `ShipAPI.setCaptain` | 解挂旧/挂新个人技能（含子模块），立即生效 | `Ship` |
| 入引擎 `addObjectToEngine` | 船插第三时机，每实体一次 | `CombatEngineAPI`、`Ship` |

## 规则强度

本节必须遵守的规则清单（"必须/禁止"，均附违反后的可观察结果）：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 需要跨 stats 重建保留的修改**必须**挂在重放入口上（船插 `applyEffectsBeforeShipCreation`、技能效果、buff 系统）；**禁止**直接对 `FleetMemberAPI.getStats()` 返回值 `modifyFlat`/`modifyPercent` 并期待跨重建保留 | 必须 | 下次 `updateStats()`（换舰长 / 换装配 / 舰队同步 / 读档）整体新建 stats 对象（`FleetMemberAPI.updateStats`），这些修改被丢弃——数值"自己变回去"，无任何报错（`GearInStatsRebuild` 的 transient 步骤）|
| 舰长个人技能（SHIP 族）**必须**在成员入队（`getFleetData() != null`）后才会应用；用"未入队成员"验证技能链路读不到效果 | 必须 | 未入队成员换舰长后 stats 无任何变化（`FleetMemberAPI.updateStats` 的舰队条件）|
| 换舰长 / 换装配后**必须**经会触发重算的入口（`setCaptain`、`setVariant(..., withStatsUpdate=true)`、显式 `updateStats()`、或 `setStatUpdateNeeded(true)` 后读 `getStats()`）| 必须 | 读取端 `getStats()` 返回旧 stats 对象（`getStats()` 只在 `statUpdateNeeded` 时重建），界面 / 日志数值停在旧值 |
| `setVariant` 传入引用不克隆，stats 与成员**共享同一变体**——修改成员变体后 `stats.getVariant()` 立即同见；保留旧引用"防污染"是无效假设 | 必须 | 部署期创建舰船时对成员变体的武器组改写（前置知识中变体条目）与生涯侧修改互相可见，数值/装配表现与预期不符 |
| 战斗内换舰长**必须**走 `ShipAPI.setCaptain`；换人后旧舰长个人加成（含子模块）立即被解挂 | 必须 | 直接改人物对象而不重新 `setCaptain` → 战斗 stats 无变化；缓存"旧舰长修正仍生效"的假设在换人瞬间失效（`Ship.setCaptain`）|
| **禁止**复用引擎保留 modifier ID：`flux_capacitors` / `flux_vents`、`cr_effect`、`crew skill bonus`、`crew understrength`、`mothballed_mod_id` | 必须 | 写入的 modifier 被引擎重放覆盖，或覆盖引擎写入的值（同 ID 同类型后写者胜，`MutableStat.modifyFlat`），数值错乱且难排查（`FleetMemberAPI`、`CRPluginImpl`）|
| **禁止**在 `applyEffectsBeforeShipCreation`（或其它被 `updateStats` 重放的入口）内调用会再次触发 `updateStats` 的成员 API（`setCaptain`、`setVariant(..., true)`、`updateStats()`）| 必须 | `updateStats` 无递归守卫（防重入标记仅 tooltip 路径使用），递归重建直至 `StackOverflowError` |
| 战斗 stats / `ShipAPI` 引用**禁止**跨战斗缓存 | 必须 | 下一场战斗读到失效对象（跨战斗缓存问题见前置知识；部署链每场新造战斗 stats），数值过期或静默异常 |
| 重放安全的前提：同一来源 ID 用同一类型同值（幂等覆盖），重放顺序"先 unapply 后 apply" | 必须 | 换 id / 换类型导致旧条目残留或叠加（前置知识中的幂等与作用域规则在交接链的落地；`MutableStat.unmodify(source)` 只清指定来源）|
| 调试 stats 交接时先打印 stats 对象同一性（`before != after`）确认重建发生 | 建议 | 无法区分"修改没生效"与"修改被重建丢弃" |
| 用 `setFleetCommanderForStats` 临时挂指挥官前记录原值，测完置 `null` 复位 | 建议 | 指挥官残留导致后续读取全部带未知加成（同帧回滚模式）|
| 生涯侧只读基线用未入队成员；技能链路验证在入队后进行 | 建议 | 把两条语义（未入队不应用 / 入队应用）混在一起，读到的差异无法归因 |

## 完整最小例

在既有 MOD 工程（MOD 布局与 `GearInModPlugin` 的 `onGameLoad` 注册门，见前置知识）之上新增 4 个自包含演示类（不新增资源文件），每个类演示交接链的一个环节，各带独立的注册键，挂载后第一次 `advance()` 执行一次演示并自停：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInVariantToStats    # 环节一：装配 → stats（本节新增）
│       ├── GearInCaptainToMember   # 环节二（个人）：技能 → 成员 stats
│       ├── GearInCommanderToMember # 环节二（舰队向）：指挥官 → 成员 stats
│       └── GearInStatsRebuild      # 环节四：stats 重建与惰性重算
└── jars\
```

### GearInVariantToStats（装配 → stats）

职责：演示装配 → stats 交接——未入队 wolf_Assault 成员的 stats 基值与对象关系基线（`variantShared` / `memberLinked`）。

克隆装配加 5 电容 + 5 散热后 `setVariant(withRefit=false, withStatsUpdate=true)` 触发重算（幅能容量 +5*200、幅能散耗 +5*10，引擎保留 ID 的 flat 写入）。

`stats.getVariant()` 与传入变体同一引用。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_VariantToStats"` 一次性守卫（调用点片段见下）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节一：装配 -> stats 交接（舰船 stats 基值与 setVariant 重算）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停（isDone()=true，引擎下一帧自动移除）：
 *   1. 基线：创建 wolf_Assault 成员（未入队），读取舰船 stats 基值（幅能/速度/CR）与
 *      stats.getVariant() / getFleetMember() 的对象关系；
 *   2. 装配 -> stats：克隆装配加 5 电容 + 5 散热后 setVariant(withRefit=false,
 *      withStatsUpdate=true)，幅能容量 +5*200、幅能散耗 +5*10（引擎保留 ID 的 flat 写入），
 *      stats.getVariant() 与传入变体同一引用。
 *
 * 演示全程同帧回滚（只留下 REGISTER_MARK 键），不遗留任何持久状态。
 */
public class GearInVariantToStats implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_VariantToStats";

    private static final Logger LOG = Global.getLogger(GearInVariantToStats.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInVariantToStats() {
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
        runDemo();
    }

    private void runDemo() {
        // --- 1. 基线：成员未入队，stats 已在构造器内按变体首次建立 ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        ShipVariantAPI variant = wolf.getVariant();
        MutableShipStatsAPI stats = wolf.getStats();
        LOG.info(String.format(
                "GearIn variantToStats baseline: hull=%s specId=%s caps=%d vents=%d "
                        + "fluxCap base=%.0f mod=%.0f fluxDiss base=%.0f mod=%.0f speed=%.1f maxCR=%.3f "
                        + "variantShared=%s memberLinked=%s",
                wolf.getHullId(), wolf.getSpecId(), variant.getNumFluxCapacitors(), variant.getNumFluxVents(),
                stats.getFluxCapacity().getBaseValue(), stats.getFluxCapacity().getModifiedValue(),
                stats.getFluxDissipation().getBaseValue(), stats.getFluxDissipation().getModifiedValue(),
                stats.getMaxSpeed().getModifiedValue(),
                stats.getMaxCombatReadiness().getModifiedValue(),
                stats.getVariant() == variant,
                stats.getFleetMember() == wolf));

        // --- 2. 装配 -> stats：setVariant 触发重算；vents/caps 是引擎保留 ID 的 flat 写入 ---
        ShipVariantAPI refit = wolf.getVariant().clone();
        int capsOld = refit.getNumFluxCapacitors();
        int ventsOld = refit.getNumFluxVents();
        float fluxCapBefore = stats.getFluxCapacity().getModifiedValue();
        float fluxDissBefore = stats.getFluxDissipation().getModifiedValue();
        refit.setNumFluxCapacitors(capsOld + 5);
        refit.setNumFluxVents(ventsOld + 5);
        wolf.setVariant(refit, false, true); // withRefit=false 跳过 CR 影响计算；withStatsUpdate=true 立即重算
        MutableShipStatsAPI statsAfterRefit = wolf.getStats();
        LOG.info(String.format(
                "GearIn variantToStats refit: caps %d->%d vents %d->%d fluxCap %.0f->%.0f (+%.0f) "
                        + "fluxDiss %.0f->%.0f (+%.0f) sharedVariant=%s specId=%s",
                capsOld, refit.getNumFluxCapacitors(), ventsOld, refit.getNumFluxVents(),
                fluxCapBefore, statsAfterRefit.getFluxCapacity().getModifiedValue(),
                statsAfterRefit.getFluxCapacity().getModifiedValue() - fluxCapBefore,
                fluxDissBefore, statsAfterRefit.getFluxDissipation().getModifiedValue(),
                statsAfterRefit.getFluxDissipation().getModifiedValue() - fluxDissBefore,
                statsAfterRefit.getVariant() == refit,
                wolf.getSpecId()));
    }
}
```

### GearInCaptainToMember（个人技能 → 成员）

职责：演示个人技能 → 成员 stats 交接——未入队换舰长（`gunnery_implants` L1 不应用）→ 入队 + 显式 `updateStats`（后坐力倍率 1.0 → 0.75、自动瞄准 0.0 → 1.0、实弹/能量射程 +15%）→ 换回无技能人物后数值恢复。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_CaptainToMember"` 一次性守卫（调用点片段见下）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节二（个人）：舰长技能 -> 成员 stats 交接（applyPersonalToStats）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停（isDone()=true，引擎下一帧自动移除）：
 *   1. 未入队换舰长：setCaptain 触发 updateStats，但舰长个人技能不应用
 *      （updateStats 的舰队条件：getFleetData() 为 null 时跳过 applyPersonalToStats）；
 *   2. 入队 + 显式 updateStats：个人技能（gunnery_implants L1）开始应用，
 *      后坐力倍率 1.0 -> 0.75、自动瞄准 0.0 -> 1.0、实弹/能量射程 +15%；
 *   3. 入队换舰长：setCaptain 立即重算，换回无技能人物后数值恢复。
 *
 * 演示全程同帧回滚（只留下 REGISTER_MARK 键与演示成员的增删），不遗留任何持久状态。
 */
public class GearInCaptainToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CaptainToMember";

    private static final Logger LOG = Global.getLogger(GearInCaptainToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCaptainToMember() {
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn captainToMember start: members=%d", initialCount));

        // --- 1. 未入队换舰长：setCaptain 触发 updateStats，但个人技能不应用 ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        PersonAPI gunner = Global.getFactory().createPerson();
        gunner.getStats().setSkillLevel("gunnery_implants", 1f);
        wolf.setCaptain(gunner);
        LOG.info(String.format(
                "GearIn captainToMember preJoin: recoil=%.3f aim=%.3f (skills not applied, member not in fleet)",
                wolf.getStats().getMaxRecoilMult().getModifiedValue(),
                wolf.getStats().getAutofireAimAccuracy().getModifiedValue()));

        // --- 2. 入队 + 显式 updateStats：个人技能开始应用（getFleetData() 已非 null） ---
        fleetData.addFleetMember(wolf);
        wolf.updateStats();
        LOG.info(String.format(
                "GearIn captainToMember joined: count=%d recoil=%.3f aim=%.3f ballisticRange=%.1f energyRange=%.1f",
                fleetData.getNumMembers(),
                wolf.getStats().getMaxRecoilMult().getModifiedValue(),
                wolf.getStats().getAutofireAimAccuracy().getModifiedValue(),
                wolf.getStats().getBallisticWeaponRangeBonus().getPercentMod(),
                wolf.getStats().getEnergyWeaponRangeBonus().getPercentMod()));

        // --- 3. 入队换舰长：立即重算；换回无技能人物后恢复 ---
        float recoilWithGunner = wolf.getStats().getMaxRecoilMult().getModifiedValue();
        PersonAPI plain = Global.getFactory().createPerson();
        wolf.setCaptain(plain);
        float recoilRestored = wolf.getStats().getMaxRecoilMult().getModifiedValue();
        LOG.info(String.format("GearIn captainToMember swap: recoil %.3f -> %.3f (restored)",
                recoilWithGunner, recoilRestored));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn captainToMember done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
```

### GearInCommanderToMember（舰队指挥官 → 成员）

职责：演示舰队指挥官 → 成员 stats 交接——`setFleetCommanderForStats` 挂 `crew_training`（ALL_SHIPS_IN_FLEET），`getStats()` 延迟重建后 `maxCombatReadiness` 上升，置回 `null` 后恢复。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_CommanderToMember"` 一次性守卫（调用点片段见下）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节二（舰队向）：舰队指挥官 -> 成员 stats 交接（applyFleetwideToStats）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停（isDone()=true，引擎下一帧自动移除）：
 *   1. 建成员并入队：stats 只含装配基值（无任何技能加成）；
 *   2. setFleetCommanderForStats 挂 crew_training（ALL_SHIPS_IN_FLEET），getStats()
 *      惰性重建后 maxCombatReadiness 上升；
 *   3. 置回 null：舰队向加成撤销，数值恢复。
 *
 * 演示全程同帧回滚（只留下 REGISTER_MARK 键与演示成员的增删），不遗留任何持久状态。
 */
public class GearInCommanderToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CommanderToMember";

    private static final Logger LOG = Global.getLogger(GearInCommanderToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCommanderToMember() {
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn commanderToMember start: members=%d", initialCount));

        // --- 1. 建成员并入队：stats 只含装配基值（无任何技能加成） ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleetData.addFleetMember(wolf);
        float maxCRBefore = wolf.getStats().getMaxCombatReadiness().getModifiedValue();

        // --- 2. 舰队指挥官 -> 成员 stats（ALL_SHIPS_IN_FLEET 经 applyFleetwideToStats 重放） ---
        PersonAPI commander = Global.getFactory().createPerson();
        commander.getStats().setSkillLevel("crew_training", 1f);
        wolf.setFleetCommanderForStats(commander, fleetData); // 人物变更 -> 标记 statUpdateNeeded
        float maxCRWithCommander = wolf.getStats().getMaxCombatReadiness().getModifiedValue();

        // --- 3. 置回 null：舰队向加成撤销，数值恢复 ---
        wolf.setFleetCommanderForStats(null, null);
        float maxCRRestored = wolf.getStats().getMaxCombatReadiness().getModifiedValue();
        LOG.info(String.format(
                "GearIn commanderToMember: maxCR %.3f -> %.3f (crewTraining) -> %.3f (reset)",
                maxCRBefore, maxCRWithCommander, maxCRRestored));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn commanderToMember done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
```

### GearInStatsRebuild（stats 重建与延迟重算）

职责：演示 `MutableStat` 重建与延迟重算——运行时 transient modifier（`GearIn_transient_speed` +25）不跨 `updateStats` 重建（stats 对象同一性为 false、修改被丢弃）。

`setStatUpdateNeeded(true)` 后下一次 `getStats()` 才重建。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_StatsRebuild"` 一次性守卫（调用点片段见下）。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节四：MutableStat 重建与惰性重算（updateStats 整体新建舰船 stats）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停（isDone()=true，引擎下一帧自动移除）：
 *   1. 运行时 transient modifier 不跨重建：直接 modifyFlat 到 getStats() 返回值，
 *      updateStats 后 stats 对象整体重建（对象同一性为 false）、修改被丢弃；
 *   2. 惰性重建：setStatUpdateNeeded(true) 后下一次 getStats() 才重建。
 *
 * 演示全程同帧回滚（只留下 REGISTER_MARK 键与演示成员的增删），不遗留任何持久状态。
 */
public class GearInStatsRebuild implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_StatsRebuild";

    private static final Logger LOG = Global.getLogger(GearInStatsRebuild.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInStatsRebuild() {
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn statsRebuild start: members=%d", initialCount));

        // --- 1. 建成员并入队（无舰长：updateStats 重放时无技能加成干扰） ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleetData.addFleetMember(wolf);
        wolf.updateStats();

        // --- 2. 运行时 transient modifier 不跨重建：updateStats 整体新建舰船 stats ---
        MutableShipStatsAPI before = wolf.getStats();
        float speedBefore = before.getMaxSpeed().getModifiedValue();
        before.getMaxSpeed().modifyFlat("GearIn_transient_speed", 25f);
        float speedWithTransient = wolf.getStats().getMaxSpeed().getModifiedValue();
        wolf.updateStats();
        MutableShipStatsAPI after = wolf.getStats();
        LOG.info(String.format(
                "GearIn statsRebuild transient: speed %.1f -> %.1f (with transient), "
                        + "rebuiltNewObject=%s speedAfterRebuild=%.1f",
                speedBefore, speedWithTransient, before != after, after.getMaxSpeed().getModifiedValue()));

        // --- 3. 惰性重建：setStatUpdateNeeded(true) 后下一次 getStats() 才重建 ---
        wolf.setStatUpdateNeeded(true);
        MutableShipStatsAPI lazy = wolf.getStats();
        LOG.info(String.format("GearIn statsRebuild lazy: sameObjectAsBefore=%s", after == lazy));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn statsRebuild done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
```

调用点（工程片段——方法所在类归既有插件所有，本节只演示调用，不改动那些文件）：

```java
    // 在 GearInModPlugin 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInVariantToStats.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInVariantToStats.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInVariantToStats());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInCaptainToMember.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCaptainToMember.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCaptainToMember());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInCommanderToMember.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCommanderToMember.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCommanderToMember());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInStatsRebuild.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInStatsRebuild.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInStatsRebuild());
    }
```

对应关系与观察点（原演示的 8 步观察点，按类归属）：

- 四个类统一沿用既有的一次性演示模式：`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除；`REGISTER_MARK` **必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致，且每个类独立成键、不挂进既有插件的 `CLASS_MARK` 门。
- 观察点 1（`GearInVariantToStats` 步骤 1——基线绑定）：`createFleetMember(SHIP, "wolf_Assault")` 构造即 `updateStats`（`FleetMemberAPI`），此时成员未入队
  - `variantShared=true`（`stats.getVariant() == wolf.getVariant()`，同一引用，stats 对象持有变体引用）与 `memberLinked=true`（`stats.getFleetMember() == wolf`）是"交接已绑定"的现场证明。
- 观察点 2（`GearInCaptainToMember` 步骤 1——未入队换舰长）：`gunner.getStats().setSkillLevel("gunnery_implants", 1f)` 只重放 CHARACTER_STATS / FLEET 效果（`gunnery_implants` 没有这两类）
  - `setCaptain(gunner)` 触发重建，但 `getFleetData()` 为 null → `applyPersonalToStats` 被跳过 → `recoil=1.000 aim=0.000`。
- 观察点 3（`GearInCaptainToMember` 步骤 2——入队生效）：`addFleetMember`（写 `fleetData` 并 `setSyncNeeded`）后显式 `updateStats()`——个人技能开始应用
  - `gunnery_implants` L1 的三个 Level 效果：
    - Level2：`getAutofireAimAccuracy().modifyFlat(id, 1.0)`（`com.fs.starfarer.api.impl.campaign.skills.GunneryImplants`）
    - Level3：`getBallisticWeaponRangeBonus()` / `getEnergyWeaponRangeBonus().modifyPercent(id, 15)`
    - Level1：`getMaxRecoilMult().modifyMult(id, 1 - 0.01*25)`
  - 结果 `recoil=0.750 aim=1.000 ballisticRange=15.0 energyRange=15.0`
  - 日志里的 `count` 读 `getNumMembers()`，会触发一次全量舰队同步（`FleetDataAPI.syncIfNeeded`），全员 `updateStats` 再跑一遍——同 ID 幂等，数值不变。
- 观察点 4（`GearInStatsRebuild` 步骤 2——重建即丢弃）：`modifyFlat("GearIn_transient_speed", 25f)` 直接写 `getStats()` 返回值 → `speed` 增 25
  - `updateStats()` 整体新建对象（`rebuiltNewObject=true`）→ 修改被丢弃（`speedAfterRebuild` 回到 `speedBefore`）
  - **这是"重建即丢弃"的现场证明**。
- 观察点 4b（`GearInStatsRebuild` 步骤 3——延迟重建）：`setStatUpdateNeeded(true)` 只置标记；`getStats()` 延迟重建 → `sameObjectAsBefore=false`。
- 观察点 5（`GearInVariantToStats` 步骤 2——装配 → stats）：克隆装配加 5 电容 + 5 散热，`setVariant(refit, false, true)`：`withRefit=false` 跳过 CR 影响计算（`computeVariantDiffCRImpact`），`withStatsUpdate=true` 立即重建
  - `flux_capacitors` = `numCaps * 200`（settings.json 的 `fluxPerCapacitor:200`）、`flux_vents` = `numVents * 10` （`dissipationPerVent:10`）→ 幅能容量 `+1000`、散耗 `+50`，与船插/CR 贡献无关，**确定性**
  - `sharedVariant=true`（`setVariant` 传入引用不克隆，重建后 stats 仍指向同一变体）、`specId=wolf_Assault`。
- 观察点 6（`GearInCaptainToMember` 步骤 3——换舰长恢复）：`setCaptain(plain)`（无技能默认人物）→ 立即重建 → `recoil 0.750 -> 1.000`。
- 观察点 7（`GearInCommanderToMember`——指挥官 → 成员）：`setFleetCommanderForStats(commander, fleetData)` 只标记（第二参数被忽略）；`getStats()` 延迟重建 → `applyFleetwideToStats`
  - `crew_training` Level1 （`getMaxCombatReadiness().modifyFlat(id, crBonus*0.01)`， `com.fs.starfarer.api.impl.campaign.skills.CrewTraining`，非民用船生效）→ `maxCR` 上升
  - 置 `null` 后回退 `fleetData.getCommander()`（玩家）→ 恢复原值。
- 观察点 8（清理）：四个类各自 `removeFleetMember(wolf)` → 玩家舰队成员数与初始一致（同步延迟到下一次同步，`getNumMembers()` 会立即同步）。演示脚本**不留副作用**（人物对象只存在于局部变量，不被任何持久集合引用）。

构建命令（在 `Starsector\mods\GearIndustry\` 执行）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含四个类的 class 条目（可用 `jar tf` 核对）。本节四个代码块与 fixture 逐字一致（fixture 位于 `tutorial-fixtures/units/C09-S04/`），fixture 编译验收命令见「预期结果」。

### 验证方案（基于本例）

- 前置数据：MOD 布局与 `GearInModPlugin`（含本节调用点片段；C06-S01、C06-S02）、本节四个演示类。
- 操作 1（fixture 编译）：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC09s04Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
- 操作 2（游戏构建）：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含四个类的 class 条目。
- 操作 3（新建生涯）：确认 `starsector.log` 按注册顺序依次出现四个类的日志组（`GearIn variantToStats ...` / `GearIn captainToMember ...` / `GearIn commanderToMember ...` / `GearIn statsRebuild ...`）；关键核对见「预期结果」第 3 条。
- 操作 4（战斗交叉验证）：给玩家旗舰学 `gunnery_implants`（技能树 C 键）后进入一场战斗部署旗舰，确认武器后坐力与自动瞄准符合技能描述（生涯 stats 预览与战斗 stats 的一致性；对应故障分析第 4 条）；战内用技能/命令换旗舰舰长，确认加成即时变化（对应环节三与故障分析第 5 条）。
- 操作 5（只执行一次）：等待若干游戏日后日志无新增四个演示类的日志行；保存并读档后仍无新增（四个 `REGISTER_MARK` 键已关）；读档后玩家舰队构成与演示前一致。
- 操作 6（故障注入 1——重建丢弃）：把 `GearInStatsRebuild` 里 `modifyFlat("GearIn_transient_speed", 25f)` 之后的 `updateStats()` 去掉，改为等下一次舰队同步（例如按一次 F 键开舰队界面再关）
    - 观察 `speedAfterRebuild` 语义同样成立（界面触发同步 = 重建 = 丢弃；对应规则强度第 1 条与故障分析第 1 条）。
- 操作 7（故障注入 2——保留 ID 冲突）：把 `GearInStatsRebuild` 的 transient modifier 来源 ID 临时改成 `flux_capacitors`，重启新档：`GearIn variantToStats refit` 的 `+1000` 核对不再成立（此写入与引擎写入同 ID 互踩；对应规则强度第 6 条）；改回后确认恢复。
- 操作 8（故障注入 3——未入队不生效）：把 `GearInCaptainToMember` 中的 `fleetData.addFleetMember(wolf);` 调用注释掉再运行：`joined` 日志不出现、`preJoin` 之后直接是 `swap`，且所有技能相关数值保持 `1.000/0.000`（对应规则强度第 2 条与故障分析第 2 条）；改回后确认恢复。
- 通过信号：操作 1–5 全部符合；操作 6–8 的注入与恢复都符合预期。
- 清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures/build.gradle` 等）做过修改。

::: tip 用增量核对，不要依赖绝对数值
若存档已学 ALL_SHIPS_IN_FLEET 技能，步骤 3–7 的基线会含玩家舰队向加成；四个类的核对项都是增量 / 方向比较（`+1000/+50`、`0.750 -> 1.000`、恢复值 == 初始值），不受影响。核对绝对数值建议用新建生涯。
:::

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC09s04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInVariantToStats.class` 等四个类的 class 条目。
3. **新建生涯（首次进入存档后第一帧）**：`starsector.log` 按注册顺序依次出现以下日志（`<N>` 为初始成员数；数值以实际为准，但标出的关系是确定性的）：
```text
GearIn variantToStats baseline: hull=wolf specId=wolf_Assault caps=9 vents=10 fluxCap base=2250 mod=<2250+1800+fluxdistributor贡献> fluxDiss base=150 mod=<150+100+fluxdistributor贡献> speed=<150.0> maxCR=<0.200~0.700 视船员> variantShared=true memberLinked=true GearIn variantToStats refit: caps 9->14 vents 10->15 fluxCap <a>-><a+1000> (+1000) fluxDiss <b>-><b+50> (+50) sharedVariant=true specId=wolf_Assault GearIn captainToMember start: members=<N> GearIn captainToMember preJoin: recoil=1.000 aim=0.000 (skills not applied, member not in fleet) GearIn captainToMember joined: count=<N+1> recoil=0.750 aim=1.000 ballisticRange=15.0 energyRange=15.0 GearIn captainToMember swap: recoil 0.750 -> 1.000 (restored) GearIn captainToMember done: members=<N> (initial <N>) GearIn commanderToMember start: members=<N> GearIn commanderToMember: maxCR <c> -> <c+crewTraining加成> (crewTraining) -> <c> (reset) GearIn commanderToMember done: members=<N> (initial <N>) GearIn statsRebuild start: members=<N> GearIn statsRebuild transient: speed <150.0> -> <175.0> (with transient), rebuiltNewObject=true speedAfterRebuild=<150.0> GearIn statsRebuild lazy: sameObjectAsBefore=false GearIn statsRebuild done: members=<N> (initial <N>)
```
要点核对：`variantToStats baseline` 的 `variantShared=true`/`memberLinked=true`（stats 与变体、成员双向绑定）； `captainToMember preJoin` 的 `1.000/0.000`（未入队不应用技能）；`captainToMember joined` 的 `0.750/1.000/15.0/15.0`（入队后技能生效， `gunnery_implants` L1 的 recoil/aim/range）；`statsRebuild transient` 的 `rebuiltNewObject=true` 与 `speedAfterRebuild` 回落（重建即丢弃）；`variantToStats refit` 的 `+1000/+50` 与 `sharedVariant=true`（装配 → stats 交接与引用共享）； `captainToMember swap` 的 `0.750 -> 1.000`（换人立即重算并恢复）；`commanderToMember` 的"中间值 > 前后值且恢复值 == 初始值"（舰队指挥官 → 成员 stats 交接）；四个 `done` 行成员数均与各自 `start` 行一致（无残留）。类间顺序以注册顺序为准，类内顺序确定。
4. **报错定位**：若日志出现某个演示类的 ERROR 堆栈（前缀 `GearIn variantToStats / captainToMember / commanderToMember / statsRebuild`），说明该步抛错——按堆栈对照「规则强度」节定位（最常见的是引用了不存在的技能 id 或装配 id）。
5. **读档**：不重复执行演示（四个 `REGISTER_MARK` 键已关）；玩家舰队构成与演示前一致。
6. **退出到主菜单**：脚本停止（生涯状态不再运行）；回主菜单后再进档按第 5 条继续。

静态自查：四个类与 fixture 逐字一致（包括 import 顺序与注释）；各 `REGISTER_MARK` 与调用点片段的键逐字一致；没有把工作单元编号写进任何运行时名称（类名、包名、persistent data 键、modifier 来源 ID、日志前缀均不含 C09-S04 变体）；示例只消费既有 GearIn ID 与原版 ID（无新增资源 ID）。

## 实例驱动故障分析

1. **生涯侧改了成员 stats，数值"自己变回去"**。
   - 触发：对 `member.getStats().getMaxSpeed()` 直接 `modifyFlat("我的id", ...)` 后什么都不做，过一会儿（换舰长、开舰队界面、舰队同步、读档）数值恢复原样
   - 可观察：无任何报错，修改静默消失——`updateStats` 整体新建 stats 对象，旧对象连同直接写在它上面的 modifier 一起作废（`GearInStatsRebuild` 的 `rebuiltNewObject=true`）
   - 修正：跨重建的修改必须挂在重放入口（船插 `applyEffectsBeforeShipCreation`、技能效果、buff 系统）。
2. **换舰长后 stats 无变化**。
   - 触发：成员还没 `addFleetMember` 就 `setCaptain(带技能人物)`
   - 可观察：`setCaptain` 确实调用了 `updateStats`，但 stats 数值纹丝不动——`applyPersonalToStats` 被 `getFleetData() != null` 条件跳过（`GearInCaptainToMember` 的 `1.000/0.000`）
   - 修正：先入队再换舰长（或直接验证"未入队不应用"这条语义本身）。
3. **换舰长/换装配后界面数值不变**。触发：只改了人物/装配字段，没走触发重算的入口（例如自己 new 一个变体但不 `setVariant`）。可观察：`getStats()` 返回旧 stats 对象（`getStats()` 只在 `statUpdateNeeded` 时重建），舰队界面数值停在旧值。
   - 修正：`setCaptain` / `setVariant(..., withStatsUpdate=true)` / 显式 `updateStats()` / `setStatUpdateNeeded(true)` 后读 `getStats()`。
4. **生涯数值与部署数值对不上**。
   - 触发：在生涯侧读 `member.getStats()` 推断战斗内表现
   - 可观察：部署链（`instantiateForCombat` → `createShip`）新造战斗 stats 并按自己的顺序重放，战斗实体上的最终值还叠加战斗期写入（战术系统数值烘焙 `updateValuesFromStats`、战斗插件、伤害结算）；CR/船员在两次重建的时机相同，但战斗期修改不会出现在生涯 stats 里
   - 修正：把生涯 stats 当"预览/界面值"，战斗行为以部署后的 `ShipAPI.getMutableStats()` 为准。
5. **战斗里换舰长，旧加成还在或新加成没有**。触发：改了人物对象（如给新舰长 `setSkillLevel`）但没重新 `ShipAPI.setCaptain`，或期望 `getCaptain()` 读回的人自动生效。可观察：战斗 stats 无变化（`setCaptain` 是唯一入口）；反过来换人后旧舰长的个人加成（含子模块）立刻消失。
   - 修正：换人必须 `ShipAPI.setCaptain(newPerson)`；要"还原"用 `setCaptain(getOriginalCaptain())`。
6. **自定义 modifier 被引擎"覆盖"或数值错乱**。
   - 触发：用了 `flux_capacitors`、`cr_effect`、`crew skill bonus`、`mothballed_mod_id` 之类的 ID 写自己的效果
   - 可观察：重建后新写的 modifier 被引擎值覆盖（同 ID 同类型后写者胜，`MutableStat.modifyFlat`），或覆盖引擎写入的值导致 CR/船员/封存语义错乱
   - 修正：modifier 来源 ID 用 MOD 专属前缀（`GearIn_`），保留 ID 清单见"环节四"。
7. **在船插 `applyEffectsBeforeShipCreation` 里改舰长/装配 → 栈溢出**。
   - 触发：效果里调用 `member.setCaptain(...)` 或 `member.setVariant(..., true)`（都会再进 `updateStats`）
   - 可观察：`StackOverflowError`，`updateStats` 无递归守卫（防重入标记仅 tooltip 使用）
   - 修正：重放入口只写 stats/变体内容，不动会触发重建的成员 API；需要换舰长/装配在脚本层做。
8. **跨战斗缓存 stats 引用读到旧值/异常**。触发：把某场战斗的 `ShipAPI` 或 `getMutableStats()` 引用存到存档级/跨战斗变量。可观察：下一场战斗读到的对象是上一场的（部署链每场新造战斗 stats），数值过期或对当前引擎无效（跨战斗缓存问题见前置知识）。修正：战斗内查询、战斗外丢弃。
9. **舰队向技能"只对部分船生效"**。
   - 触发：`crew_training`/`wolfpack_tactics` 等 ALL_SHIPS_IN_FLEET 效果没覆盖全部成员
   - 可观察：`applyFleetwideToStats` 跳过战机（`hullSize == FIGHTER` 守卫），且效果自身有条件（`CrewTraining` 非民用、`WolfpackTactics` 需军官驾驶等）——战机不吃舰队向加成属正常语义；个别船不吃加成先查效果自身条件
   - 修正：区分"作用域"（技能效果一节）与"交接通道"两个维度排查。
10. **换了装配 CR 掉了一截**。
    - 触发：`setVariant(variant, true, ...)`（`withRefit=true`）
    - 可观察：`computeVariantDiffCRImpact` 按新旧变体差异算 CR 影响并 `applyCREvent(-delta, "field refit")`——界面 CR 下降是引擎语义，不是 bug
    - 修正：代码侧批量换装配用 `withRefit=false` 跳过；玩家 refit 界面的 CR 惩罚保留。
11. **技能等级改了但成员 stats 不变**。
    - 触发：`setSkillLevel` 后直接读 `member.getStats()` 期望 SHIP 效果已生效
    - 可观察：FLEET / CHARACTER_STATS 在 `refreshCharacterStatsEffects` 即时重放，SHIP 族要等成员 stats 重建（技能效果一节时序）——`setSkillLevel` 本身不触发 `updateStats`
    - 修正：随后显式 `updateStats()` 或触发舰队同步；本节演示的 `GearInCaptainToMember` 正是这条时序的两半。

## 总结

本节把状态交接讲成一条四环节链：

- **装配 → stats**：新建 stats 时把船体引擎/反应堆数值读为各 `MutableStat` 基值
  - `FleetMemberAPI.updateStats()` 按固定序列全量重建：舰长个人技能（需入队）→ 舰队指挥官舰队向技能 → 船插 `applyEffectsBeforeShipCreation` → `flux_capacitors`/`flux_vents` → CR/船员 → 封存倍率
  - **重建 = 整体新建对象**：挂在旧对象上的运行时修改一律被丢弃。
- **人物技能 → 成员 stats**：`applyPersonalToStats`（SHIP 族，舰长）与 `applyFleetwideToStats`（ALL_SHIPS_IN_FLEET，指挥官）是两条通道；`setCaptain` 立即重算、`setFleetCommanderForStats` 只标记（第二参数被忽略）、`getStats()` 延迟重建、舰队同步全员重建。
- **成员 stats → 战斗实体**：`instantiateForCombat` → 舰船对象创建（`createShip`：新造战斗 stats + 舰队向技能 + buff → `Ship` 构造器绑定实体并重放船插/舰长技能/幅能 → 船插 `applyEffectsAfterShipCreation` → 舰长重挂）
  - `addObjectToEngine` 触发一次船插第三时机；战斗内 `setCaptain` 解挂/挂载个人技能（含子模块）。
- **MutableStat 语义**：延迟重算、同 ID 同类型幂等（重放安全的前提）、引擎保留 ID 清单（`flux_capacitors`/`flux_vents`、`cr_effect`、`crew skill bonus`/`crew understrength`、`mothballed_mod_id`）。

记忆要点：

- 交接的结算时刻是 `updateStats`：新建 stats、按序重放、同 ID 幂等；要跨结算保留的修改必须挂在重放入口。
- 舰长技能要"入队才生效"（`getFleetData() != null`）；舰队向技能跳过战机。
- `setVariant` 传引用不克隆，stats 与成员共享变体；部署链每场新造战斗 stats，禁止跨战斗缓存。
- 战斗内换舰长 = 解挂旧 + 挂新（含子模块），立即生效；`getPersonality` 来自舰长。
- 调试先看对象同一性（`before != after`），再谈数值。

这条链闭合后的意义在于：生涯侧与战斗侧各自维护自己的 stats，跨场景只通过"重建 + 重放"传递语义、不共享对象——因此任何"跨战斗缓存"的尝试都必然读到过期对象，而任何"挂在重放入口"的修改都天然获得幂等性。

后续生涯侧的脚本时序、事件与存档主题都会沿用本节的注册守卫模式（每注册项独立 persistent data 键）与"先问重放入口、再问来源 ID"的排查顺序。

变体本身的语义（克隆、来源、武器组改写）也始终以本节的 `setVariant` 引用共享规则为落点。

## 建议参考项

- `com.fs.starfarer.api.combat.MutableStat`：公开类即实现——延迟重算、同 ID 幂等覆盖、`unmodify(source)` 清三类（环节四）。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：成员/战斗 stats 的统一接口与数值访问器
  - 数值访问器：`getFluxCapacity`、`getMaxSpeed`、`getMaxCombatReadiness`、`getAutofireAimAccuracy`、`getMaxRecoilMult`
  - 射程与对象访问器：`getBallisticWeaponRangeBonus`、 `getEnergyWeaponRangeBonus`、`getVariant`、`getFleetMember`、`getEntity`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：生涯侧成员入口——`getStats`、`setCaptain`、`setVariant`、`setStatUpdateNeeded`、`setFleetCommanderForStats`、`updateStats`。
- `com.fs.starfarer.api.characters.PersonAPI` 与 `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：人物与技能等级入口（`setSkillLevel`、`refreshCharacterStatsEffects`）。
- `com.fs.starfarer.api.combat.ShipAPI`：战斗实体——`getMutableStats`、`getCaptain`、`getOriginalCaptain`、`setCaptain`、`getFleetCommander`。
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：船体 spec——`getFluxCapacity`/`getFluxDissipation` 是幅能基值来源，嵌套 `EngineSpecAPI`。
- `com.fs.starfarer.api.impl.combat.CRPluginImpl`：`cr_effect` / `crew skill bonus` / `crew understrength` 保留 ID 的引擎写入方。
- `com.fs.starfarer.api.impl.campaign.skills.GunneryImplants` 与 `com.fs.starfarer.api.impl.campaign.skills.CrewTraining`：本节演示使用的原版技能效果类。
- `data/scripts/gearin/campaign`：本节四个演示类（`GearInVariantToStats`、`GearInCaptainToMember`、`GearInCommanderToMember`、`GearInStatsRebuild`）所在的 MOD 源码包。
- `data/config/settings.json`：`fluxPerCapacitor:200`、`dissipationPerVent:10` 的出处。
- `data/hulls/ship_data.csv` 与 `data/variants/wolf/wolf_Assault.variant`：演示基线数据（wolf 的 `max flux`/ `flux dissipation`/`max speed`；`fluxCapacitors:9`/`fluxVents:10`）。
- `tutorial-fixtures/units/C09-S04/`：本节 fixture（与正文四个代码块逐字一致）。
- `tutorial-fixtures/build.gradle`：C09-S04 的 source set 与 `compileC09s04Java` 任务。
