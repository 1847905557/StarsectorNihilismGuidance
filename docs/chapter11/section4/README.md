# 传递人物、成员与舰船状态

本节观察人物、舰队成员与战斗舰船之间的状态传递，确认装配和技能如何进入最终舰船数值。示例会触发数值重建，用日志区分能够保留和会被覆盖的修改。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 解释状态交接链的四个环节 (装配 → 成员 stats、人物技能 → 成员 stats、成员 stats → 战斗实体、`MutableStat` 重算语义) 与各自的触发入口；
- 验证 stats 重建 "整体新建 + 按固定序列重放" 的行为，并区分跨重建保留与丢弃的修改；
- 区分个人通道与舰队通道的归属条件：舰长个人技能 (SHIP 族) 需成员入队 (`getFleetData() != null`)，舰队指挥官舰队向技能 (ALL\_SHIPS\_IN\_FLEET) 经 `applyFleetwideToStats` 应用且跳过战机；
- 按部署链顺序解释战斗 stats 的建立过程 (新造 → `ShipAPI` 对象创建时绑定实体并重放 → 入引擎 → 战斗内换舰长)，并核对每步的生效时机；
- 解释 `MutableStat` 的延迟重算 (读 `getModifiedValue()` 才重算) 与同 ID 同类型幂等覆盖，识别引擎保留 ID 清单；
- 从可观察症状定位典型故障：生涯侧数值 "自己变回去"、换舰长后 stats 无变化、界面数值不刷新、生涯与部署数值对不上、战斗内换舰长旧加成残留、自定义 modifier 被引擎覆盖、重放入口内递归栈溢出、跨战斗缓存失效。

## 前置知识

- [C4-S4] 的模块船、模块船生成流程与独立 `ShipAPI` 实体；
- [C6-S2] 的 `GearInModPlugin` 所有权与 `onGameLoad` 注册检查；
- [C7-S3] 的 `MutableStat`、修饰公式、modifier ID 稳定性与 `unmodify`；
- [C9-S3] 的 `FleetMemberAPI` 变体、stats、成员加入与 `setCaptain`；
- [C11-S1] 的人物、`PersonAPI.getStats()`、舰长与默认舰长保存；
- [C11-S2] 的技能效果作用域、`refreshCharacterStatsEffects` 与 effect ID；
- [C11-S3] 的 `ShipVariantAPI.clone()`、`setSource`、`toJSONObject` 与部署期变体修改。

## 术语

本节首次完整讲解两个交接语义词；`MutableStat` 与 `FleetMemberAPI` 的中文写法与首次讲解位置见术语约定表：

- **状态交接 (handoff)**：状态从一个对象流向下一个对象的过程 —— 装配内容流入成员 stats、人物技能流入成员 stats、成员 stats 流入战斗舰船。交接经 `MutableShipStatsAPI` 进行，交接的完成标志是 "stats 对象被 (重新) 构造并重放"。
- **stats 重建 (stats rebuild)**：`FleetMemberAPI.updateStats()` 整体新建 stats 对象并按固定序列重放全部效果的行为。重建是交接的 "结算时刻"：重建后生效的修改保留，重建前直接写在旧对象上的修改被丢弃。

## 梳理状态传递关系

### 交接链总览

```text
装配 (ShipVariantAPI)
  │  舰船统计对象.create(variant)：引擎/反应堆数值成为各 MutableStat 的 base
  │  FleetMemberAPI.updateStats：船插 applyEffectsBeforeShipCreation + flux_capacitors/flux_vents + CR/船员
  ▼
成员 stats(FleetMemberAPI.getStats() → MutableShipStatsAPI)
  ▲
  │  setCaptain / setFleetCommanderForStats / setVariant(withStatsUpdate) / 舰队同步 / 读档
  │  └ 触发 updateStats 重建
  │
人物技能 (PersonAPI.getStats() → MutableCharacterStatsAPI)
  │  applyPersonalToStats(SHIP 族，舰长，需成员入队)
  │  applyFleetwideToStats(ALL_SHIPS_IN_FLEET，舰队指挥官)
  ▼
战斗 stats(舰船对象创建新造 → ShipAPI 对象绑定实体并重放)
  │  CombatEngineAPI.addEntity(CombatEntityAPI) → applyEffectsAfterShipAddedToCombatEngine(一次)
  ▼
战斗实体 (ShipAPI)
```

为什么交接链要存在

生涯侧与战斗侧不共享 stats 对象：成员 stats 属于舰队数据结构，战斗 stats 属于战斗场景，两边的数值必须通过 "重建 + 重放" 在各自场景内重新算出来。

交接链因此成为跨场景状态的唯一合法通道 —— 挂在重放入口上的效果 (技能、船插、buff) 天然获得幂等性与确定性；代价是重建时丢弃一切直接写在 stats 对象上的运行时修改。

这一取舍贯穿本节全部规则。

### 环节一：装配 → stats(stats 新建与 updateStats 重建序列)

成员与战斗侧的 stats 都实现 `MutableShipStatsAPI`。

按变体新建 stats 时，各 `MutableStat` 的**基值**来自船体 spec：`EngineSpecAPI` 的 `maxSpeed`/`acceleration`/`deceleration`/`maxTurnRate`/`turnAcceleration` → 机动性五统计项。

反应堆数值 → `fluxCapacity`/`fluxDissipation`(`ShipHullSpecAPI.getFluxCapacity()`/`getFluxDissipation()`，数据源是 `ship_data.csv` 的 `max flux` / `flux dissipation` 列，由船体 CSV 加载读入)。

另有 `breakProb`、维修率、传感器 (`initSensorStats`)。

**基值只来自船体** —— 变体的武器/船插不改变基值，只以 modifier 形式叠加。

`FleetMemberAPI.updateStats()` 是生涯侧唯一的全量重建入口，顺序固定：

1. 整体新建 stats 对象 (按当前变体)，旧对象作废；
2. `stats.setFleetMember(this)`；
3. 舰长个人技能：`captain.getStats().applyPersonalToStats(stats, hullSize)`—— **仅当 `getFleetData() != null`** (成员已入队)；
4. 舰队指挥官舰队向技能：`getFleetCommanderForStats()` 优先，否则回退 `fleetData.getCommander()`，`applyFleetwideToStats(stats, hullSize)`；
5. 变体全部船插 `applyEffectsBeforeShipCreation(hullSize, stats, id)`；
6. `flux_capacitors` = `numFluxCapacitors * FLUX_PER_CAPACITOR`、`flux_vents` = `numFluxVents * DISSIPATION_PER_VENT`(flat 写入，引擎保留 ID)；
7. `updateOnlyStatsThatCanRelyOnCROrCrew`：船员不足倍率 + `CombatReadinessPlugin.applyCRToStats(CR)`；
8. 封存 (mothballed) 时写 `mothballed_mod_id` 的 supplies/cargo/fuel/crew 倍率，否则 unmodify；
9. `stats.getDynamic().removeUmodified()`、`repairTracker.updateRates()`；
10. 无甲板 (`getNumFlightDecks() <= 0`) 时清空变体 `getWings()`；`fleetData.setSyncNeeded()`。

禁止直接写 `getStats()` 返回值

**禁止**对 `FleetMemberAPI.getStats()` 的返回值直接 `modifyFlat` / `modifyPercent` 并期待跨重建保留。

第 1 步整体新建 stats 对象，旧对象连同直接写在它上面的 modifier 一起作废 —— 数值 "自己变回去"，且无任何报错。

要跨重建保留的修改必须挂在重放入口上 (船插 `applyEffectsBeforeShipCreation`、技能效果、buff 系统)。

完整清单见 "规则强度"。

两个直接推论：**重建 = 丢弃** —— 第 1 步新造对象意味着任何直接写在旧 stats 对象上的 modifier 都不复存在 (`GearInStatsRebuild` 的 transient 步骤)。

**重放 = 幂等** —— 第 3–6 步的写入全部用固定来源 ID，同 ID 同值覆盖，因此重放任意次数值不变 (第 4 步在舰队同步里还会被再执行一次，见下)。

### 环节二：人物技能 → 成员 stats

基值之后是加成：人物技能要在 stats 重建时被重放进去，才谈得上生效。技能重放有两条通道：

- **个人通道** `applyPersonalToStats(stats, hullSize)`：遍历 `getShipEffects()`(`type == SHIP` 且 `getSkillLevel(governingSkill) >= requiredSkillLevel`)，逐效果 `apply(stats, hullSize, effectId, level)`：
    - effect id 是 `<技能id>_ship_<序号>`——"effect id 即 modifier id" 的规则在这里落地。
- **舰队通道** `applyFleetwideToStats(stats, hullSize)`：遍历 `getAllShipsInFleetEffects()`(`type == ALL_SHIPS_IN_FLEET`)，**跳过 `hullSize == FIGHTER`**。

舰长侧触发点 (都会走到 `updateStats`)：

- `FleetMemberAPI.setCaptain(person)`(`FleetMemberAPI`)：人物变化且 `withStatsUpdate`(默认 true)→ `updateStats()`。
- `FleetMemberAPI.setFleetCommanderForStats(person, fleetData)`：**只存 `fleetCommanderForStats` 并 `setStatUpdateNeeded(true)`**—— 不立即重建，下一次 `getStats()` 才重建：
    - 注意实现里第二个参数 `fleetData` **被忽略**，舰队数据始终来自成员自己的 `fleetData`。
- `getStats()` 延迟重建：`statUpdateNeeded == true` 时先 `updateStats()` 再返回；`setStatUpdateNeeded(true)` 还会使缓存的强度值失效。

成员侧前提：第 3 步的 `applyPersonalToStats` 有 `getFleetData() != null` 条件 —— **未入队的成员换舰长，stats 没有任何变化** (`GearInCaptainToMember` 的 preJoin 步骤即此)。

舰队同步 (`FleetDataAPI.syncIfNeeded`)：`needsSync` 时对每个成员 `updateStats()`，随后按 `getFleetCommanderForStats()` → `getFleetCommander()` → `commander` 回退链**再执行一次** `applyFleetwideToStats`—— 同 ID 幂等，不会翻倍。

任何写 `setSyncNeeded` 的舰队操作 (`addFleetMember`、`removeFleetMember`、`setVariant` 等) 最终都会经这个入口重建全员 stats；读 `getNumMembers()` 等视图 API 会触发同步。

读视图 API 会触发一次全员重建

`getNumMembers()` 等视图 API 会触发 `syncIfNeeded`—— 在日志里读到 `count` 的那一刻，全员 stats 已经重建了一遍 (含舰队向技能的二次重放)。这不是 bug：同 ID 幂等保证数值不变。调试时别把 "我读了一次成员数" 误当成重建来源。

### 环节三：成员 stats → 战斗实体 (部署链)

生涯侧成员 stats 到这里已经完整；部署时战斗侧并不复用这个对象，而是按自己的顺序重走一遍重建与重放。部署链：

1. 解析舰队指挥官：`getFleetCommander()` → `getFleetCommanderForStats()` → 战斗管理器默认指挥官；
2. 舰船对象创建 (构造器内绑定实体、重放)；
3. `updateStatsBasedOnCrew(战斗 stats)`—— 船员不足倍率写到**战斗** stats；
4. 封存或 `!canBeDeployedForCombat()` → `controlsLocked(true)`；
5. `CombatReadinessPlugin.applyCRToStats(CR, 战斗 stats, hullSize)`、`status.applyToShip`。

舰船对象创建的顺序：

1. 新建战斗 stats 对象 —— **部署时新造** (不是成员 stats 的引用)；
2. `stats.setFleetMember(member)`；
3. 指挥官 `applyFleetwideToStats(战斗 stats, hullSize)`(ALL\_SHIPS\_IN\_FLEET)；
4. 成员 buff 逐个应用；
5. `ShipAPI` 对象构造 (构造器内绑定实体、重放)；
6. `setOriginalCaptain(captain)`、`setFleetCommander(commander)`；
7. 全部船插 `applyEffectsAfterShipCreation(ship, id)`；
8. 舰长 `applyPersonalAfterShipCreationToShip`；
9. 若有指挥官：`setCaptain(null)` 再 `setCaptain(captain)` 的重挂舞蹈 (整理个人/舰队向技能的应用次序)。

`ShipAPI` 对象构造：

- `stats.setEntity(this)` / `stats.setFleetMember(member)`—— 战斗 stats 与实体互相绑定，`MutableShipStatsAPI.getEntity()` 由此非 null；
- 全部船插 `applyEffectsBeforeShipCreation`(重放时)；
- `setCaptain(captain)` → `applyPersonalToShip`(舰长技能)：个人 SHIP 技能 + `applyPersonalAfterShipCreationToShip`(含系统/相位数值烘焙 `updateValuesFromStats`)+ 指挥官 `applyFleetwideToShip`；
- `flux_capacitors`/`flux_vents` 再写一次 —— 与生涯序列同 ID 幂等。

入引擎：`CombatEngineAPI.addEntity(CombatEntityAPI)`→ 触发全部船插的第三时机 —— **每个战斗实体只触发一次**。

模块船走同一条链：模块船生成流程 (舰船对象创建) 为每个模块变体新建 `FleetMemberAPI` 再创建舰船对象。

战斗内换舰长：`ShipAPI.setCaptain(person)` 先 `unapplyPersonalToShip` 旧舰长 (**含子模块**)，再 `applyPersonalToShip` 新舰长 (含子模块)—— 统计立即变化；`getPersonality()` = 舰长 personality id。

`getOriginalCaptain()` 返回部署初始舰长。

### 环节四：MutableStat 延迟重算与保留 ID

前三环节的每一次重放都依赖同一套底层语义：为什么同一序列重放任意次数值不变、引擎为什么敢在多个时机重复写入？答案在 `MutableStat` 本身。它是公开 API 类 (`com.fs.starfarer.api.combat.MutableStat`，公开具体类)：

- **延迟重算**：`modifyFlat`/`modifyPercent`/`modifyMult`/`setBaseValue` 只做标记，`getModifiedValue()`/ `getFlatMod()`/`getPercentMod()`/`getMult()` 读取时才真正重算：
    - `modified = base + base*percent/100 + flat`，再 `* mult`。
- **同 ID 幂等**：`modifyFlat(source, value)` 在 "同 source 已存在且值相同" 时只更新描述；`unmodify(source)` 同时清理三类。交接链的 "重放任意次不翻倍" 完全依赖这套语义。
- **引擎保留 ID**(本节列全，均不可占用)：
    - `flux_capacitors`/`flux_vents`(`FleetMemberAPI.updateStats`、`ShipAPI` 对象构造)；
    - `cr_effect`(`CRPluginImpl.applyCRToStats`，`com.fs.starfarer.api.impl.combat.CRPluginImpl`，按 CR 写故障率/机动性/伤害修正)。
    - `crew skill bonus`/`crew understrength`(`CRPluginImpl.applyMaxCRCrewModifiers`，`maxCombatReadiness`)。
    - `mothballed_mod_id`(`FleetMemberAPI`)。

同一来源 ID 写不同值，后写者静默覆盖

两个位置共用同一个来源 ID、却在不同时机写不同值时，没有报错 —— 只有最后一次写入生效 (`modifyFlat` 只在 "同 source 已存在且值相同" 时保持幂等)。排查 "数值不对但没有任何错误" 时，先检查是否有两处代码共用了同一个来源 ID。

### 触发点汇总速查表

四个环节里提到的触发点收拢成一张表，重建时机、生效时机一目了然：

| 触发 | 行为 | 出处 |
|---|---|---|
| `FleetMemberAPI` 构造 / 读档 | 首次 `updateStats()` | `FleetMemberAPI` |
| `setCaptain(person)`(人物变化) | 立即 `updateStats()` | `FleetMemberAPI` |
| `setFleetCommanderForStats(person, data)`(人物变化) | 只标记 `statUpdateNeeded`，下次 `getStats()` 重建 | `FleetMemberAPI` |
| `setVariant(variant, withRefit, withStatsUpdate)` | `withRefit` 算 CR 影响并 `applyCREvent`(`computeVariantDiffCRImpact`)；`withStatsUpdate` 立即 `updateStats()` | `FleetMemberAPI` |
| `setStatUpdateNeeded(true)` | 标记；下次 `getStats()` / `updateStats()` 重建 | `FleetMemberAPI` |
| 舰队同步 `syncIfNeeded` | 全员 `updateStats()` + 舰队向技能二次重放 | `FleetDataAPI` |
| 部署 (舰船对象创建) | 新造战斗 stats 并完整重放 (环节三) | `FleetMemberAPI` |
| 战斗内 `ShipAPI.setCaptain` | 解挂旧/挂新个人技能 (含子模块)，立即生效 | `ShipAPI` |
| 入引擎 `addEntity` | 船插第三时机，每实体一次 | `CombatEngineAPI`、`ShipAPI` |

## 规则强度速查表

机制讲完了，现在把必须遵守的边界收拢成清单。以下规则 ("必须/禁止") 均附违反后的可观察结果：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 需要跨 stats 重建保留的修改**必须**挂在重放入口上 (船插 `applyEffectsBeforeShipCreation`、技能效果、buff 系统)**；禁止**直接对 `FleetMemberAPI.getStats()` 返回值 `modifyFlat`/`modifyPercent` 并期待跨重建保留 | 必须 | 下次 `updateStats()`(换舰长 / 换装配 / 舰队同步 / 读档) 整体新建 stats 对象 (`FleetMemberAPI.updateStats`)，这些修改被丢弃 —— 数值 "自己变回去"，无任何报错 (`GearInStatsRebuild` 的 transient 步骤) |
| 舰长个人技能 (SHIP 族) **必须**在成员入队 (`getFleetData() != null`) 后才会应用；用 "未入队成员" 验证技能链路读不到效果 | 必须 | 未入队成员换舰长后 stats 无任何变化 (`FleetMemberAPI.updateStats` 的舰队条件) |
| 换舰长 / 换装配后**必须**经会触发重算的入口 (`setCaptain`、`setVariant(..., withStatsUpdate=true)`、显式 `updateStats()`、或 `setStatUpdateNeeded(true)` 后读 `getStats()`) | 必须 | 读取端 `getStats()` 返回旧 stats 对象 (`getStats()` 只在 `statUpdateNeeded` 时重建)，界面 / 日志数值停在旧值 |
| `setVariant` 传入引用不克隆，stats 与成员**共享同一变体** —— 修改成员变体后 `stats.getVariant()` 立即同见；保留旧引用 "防污染" 是无效假设 | 必须 | 部署期创建舰船时对成员变体的武器组改写 (前置知识中变体条目) 与生涯侧修改互相可见，数值/装配表现与预期不符 |
| 战斗内换舰长**必须**走 `ShipAPI.setCaptain`；换人后旧舰长个人加成 (含子模块) 立即被解挂 | 必须 | 直接改人物对象而不重新 `setCaptain` → 战斗 stats 无变化；缓存 "旧舰长修正仍生效" 的假设在换人瞬间失效 (`ShipAPI.setCaptain`) |
| **禁止**复用引擎保留 modifier ID：`flux_capacitors` / `flux_vents`、`cr_effect`、`crew skill bonus`、`crew understrength`、`mothballed_mod_id` | 必须 | 写入的 modifier 被引擎重放覆盖，或覆盖引擎写入的值 (同 ID 同类型后写者胜，`MutableStat.modifyFlat`)，数值错乱且难排查 (`FleetMemberAPI`、`CRPluginImpl`) |
| **禁止**在 `applyEffectsBeforeShipCreation`(或其它被 `updateStats` 重放的入口) 内调用会再次触发 `updateStats` 的成员 API(`setCaptain`、`setVariant(..., true)`、`updateStats()`) | 必须 | `updateStats` 没有防重入检查 (防重入标记仅 tooltip 路径使用)，递归重建直至 `StackOverflowError` |
| 战斗 stats / `ShipAPI` 引用**禁止**跨战斗缓存 | 必须 | 下一场战斗读到失效对象 (跨战斗缓存问题见前置知识；部署链每场新造战斗 stats)，数值过期或静默异常 |
| 重放安全的前提：同一来源 ID 用同一类型同值 (幂等覆盖)，重放顺序 "先 unapply 后 apply" | 必须 | 换 id / 换类型导致旧条目残留或叠加 (前置知识中的幂等与作用域规则在交接链的落地；`MutableStat.unmodify(source)` 只清指定来源) |
| 调试 stats 交接时先打印 stats 对象同一性 (`before != after`) 确认重建发生 | 建议 | 无法区分 "修改没生效" 与 "修改被重建丢弃" |
| 用 `setFleetCommanderForStats` 临时挂指挥官前记录原值，测完置 `null` 复位 | 建议 | 指挥官残留导致后续读取全部带未知加成 (同帧回滚模式) |
| 生涯侧只读基线用未入队成员；技能链路验证在入队后进行 | 建议 | 把两条语义 (未入队不应用 / 入队应用) 混在一起，读到的差异无法归因 |

## 实现人物舰队状态传递

状态交接必须按引擎实际顺序观察。先创建并入队成员，再改变舰长或指挥官，最后主动制造一次重建；只看最终数值会把不同通道混在一起。

### 创建成员并读取基线

在 `GearInVariantToStats.runDemo()` 中创建成员并读取 stats。成员尚未入队时也会有一份按变体生成的 stats，但舰长个人技能尚未进入这条链。

```java
FleetMemberAPI wolf = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP, "wolf_Assault");
MutableShipStatsAPI stats = wolf.getStats();
LOG.info(String.format("baseline variantShared=%s memberLinked=%s speed=%.1f",
        stats.getVariant() == wolf.getVariant(),
        stats.getFleetMember() == wolf,
        stats.getMaxSpeed().getModifiedValue()));
```

完成信号：日志中的 `variantShared` 与 `memberLinked` 均为 `true`，并得到一组稳定的舰船基线数值。

### 用新变体触发重建

在同一 `runDemo()` 中克隆变体并调用 `setVariant`。`withStatsUpdate=true` 立即重建；`withRefit=false` 不计算玩家改装造成的 CR 影响。

```java
ShipVariantAPI refit = wolf.getVariant().clone();
refit.setNumFluxCapacitors(refit.getNumFluxCapacitors() + 5);
refit.setNumFluxVents(refit.getNumFluxVents() + 5);
wolf.setVariant(refit, false, true);
MutableShipStatsAPI rebuilt = wolf.getStats();
```

完成信号：`rebuilt` 是新 stats 对象，幅能容量增加 1000，幅能散耗增加 50，且 `rebuilt.getVariant() == refit`。

### 让入队舰长技能生效

在 `GearInCaptainToMember.runDemo()` 中先给人物学习 `gunnery_implants`，再把成员加入玩家舰队。入队后显式 `updateStats()`，个人 SHIP 效果才会进入成员 stats。

```java
PersonAPI gunner = Global.getFactory().createPerson();
gunner.getStats().setSkillLevel("gunnery_implants", 1f);
wolf.setCaptain(gunner);
fleetData.addFleetMember(wolf);
wolf.updateStats();
```

完成信号：后坐力倍率变为 0.750，自动瞄准变为 1.250，实弹与能量武器射程各增加 15%。同一人物在未入队成员上只会得到基线值。

### 让舰队指挥官影响成员

在 `GearInCommanderToMember.runDemo()` 中设置 `setFleetCommanderForStats`，随后读取 `getStats()` 触发延迟重建。第二个 `fleetData` 参数不改变成员实际使用的舰队数据。

```java
PersonAPI commander = Global.getFactory().createPerson();
commander.getStats().setSkillLevel("crew_training", 1f);
wolf.setFleetCommanderForStats(commander, fleetData);
float withCommander = wolf.getStats()
        .getMaxCombatReadiness().getModifiedValue();
wolf.setFleetCommanderForStats(null, null);
float restored = wolf.getStats()
        .getMaxCombatReadiness().getModifiedValue();
```

完成信号：读取 `withCommander` 后最大 CR 上升；置回 `null` 再读取，数值恢复到设置前。

### 观察直接修改如何丢失

在 `GearInStatsRebuild.runDemo()` 中保存旧 stats 引用，临时写入速度 modifier，再调用 `updateStats()`。

```java
MutableShipStatsAPI before = wolf.getStats();
before.getMaxSpeed().modifyFlat("GearIn_transient_speed", 25f);
float transientSpeed = before.getMaxSpeed().getModifiedValue();
wolf.updateStats();
MutableShipStatsAPI after = wolf.getStats();
```

完成信号：`before != after`，速度从临时加成值回到基线；`setStatUpdateNeeded(true)` 后下一次 `getStats()` 才会再次创建对象。

## 组装状态传递实现

现在把规则落到代码上。在既有 MOD 工程 (MOD 布局与 `GearInModPlugin` 的 `onGameLoad` 注册门，见前置知识) 之上，您将新增 4 个自包含演示类 (不新增资源文件)，每个类演示交接链的一个环节，各带独立的注册键，挂载后第一次 `advance()` 执行一次演示并自停：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInVariantToStats    # 环节一：装配 → stats(本节新增)
│       ├── GearInCaptainToMember   # 环节二 (个人)：技能 → 成员 stats
│       ├── GearInCommanderToMember # 环节二 (舰队向)：指挥官 → 成员 stats
│       └── GearInStatsRebuild      # 环节四：stats 重建与惰性重算
└── jars\
```

### GearInVariantToStats(装配 → stats)

职责：演示装配 → stats 交接 —— 未入队 wolf\_Assault 成员的 stats 基值与对象关系基线 (`variantShared` / `memberLinked`)。

克隆装配加 5 电容 + 5 散热后 `setVariant(withRefit=false, withStatsUpdate=true)` 触发重算 (幅能容量 +5_200、幅能散耗 +5_10，引擎保留 ID 的 flat 写入)。

`stats.getVariant()` 与传入变体同一引用。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_VariantToStats"` 做一次性判断 (调用点片段见下)。

### GearInCaptainToMember(个人技能 → 成员)

职责：演示个人技能 → 成员 stats 交接 —— 未入队换舰长 (`gunnery_implants` L1 不应用)→ 入队 + 显式 `updateStats`(后坐力倍率 1.0 → 0.75、自动瞄准 0.0 → 1.0、实弹/能量射程 +15%)→ 换回无技能人物后数值恢复。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_CaptainToMember"` 做一次性判断 (调用点片段见下)。

### GearInCommanderToMember(舰队指挥官 → 成员)

职责：演示舰队指挥官 → 成员 stats 交接 ——`setFleetCommanderForStats` 挂 `crew_training`(ALL\_SHIPS\_IN\_FLEET)，`getStats()` 延迟重建后 `maxCombatReadiness` 上升，置回 `null` 后恢复。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_CommanderToMember"` 做一次性判断 (调用点片段见下)。

### GearInStatsRebuild(stats 重建与延迟重算)

职责：演示 `MutableStat` 重建与延迟重算 —— 运行时 transient modifier(`GearIn_transient_speed` +25) 不跨 `updateStats` 重建 (stats 对象同一性为 false、修改被丢弃)。

`setStatUpdateNeeded(true)` 后下一次 `getStats()` 才重建。

触发入口：`onGameLoad` 注册，persistent data 键 `REGISTER_MARK = "GearIn_StatsRebuild"` 做一次性判断 (调用点片段见下)。

调用点 (工程片段 —— 方法所在类归既有插件所有，本节只演示调用，不改动那些文件)：

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

对应关系与观察点 (原演示的 8 步观察点，按类归属)：

- 四个类统一沿用既有的一次性演示模式：`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除；`REGISTER_MARK` **必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致，且每个类独立成键、不挂进既有插件的 `CLASS_MARK` 门。
- 观察点 1(`GearInVariantToStats` 步骤 1—— 基线绑定)：`createFleetMember(SHIP, "wolf_Assault")` 构造即 `updateStats`(`FleetMemberAPI`)，此时成员未入队：
    - `variantShared=true`(`stats.getVariant() == wolf.getVariant()`，同一引用，stats 对象持有变体引用) 与 `memberLinked=true`(`stats.getFleetMember() == wolf`) 是 "交接已绑定" 的现场证明。
- 观察点 2(`GearInCaptainToMember` 步骤 1—— 未入队换舰长)：`gunner.getStats().setSkillLevel("gunnery_implants", 1f)` 只重放 CHARACTER\_STATS / FLEET 效果 (`gunnery_implants` 没有这两类)：
    - `setCaptain(gunner)` 触发重建，但 `getFleetData()` 为 null → `applyPersonalToStats` 被跳过 → `recoil=1.000 aim=-0.500`。
- 观察点 3(`GearInCaptainToMember` 步骤 2—— 入队生效)：`addFleetMember`(写 `fleetData` 并 `setSyncNeeded`) 后显式 `updateStats()`—— 个人技能开始应用：
    - `gunnery_implants` L1 的三个 Level 效果：
        - Level2：`getAutofireAimAccuracy().modifyFlat(id, 1.0)`(`com.fs.starfarer.api.impl.campaign.skills.GunneryImplants`)。
        - Level3：`getBallisticWeaponRangeBonus()` / `getEnergyWeaponRangeBonus().modifyPercent(id, 15)`。
        - Level1：`getMaxRecoilMult().modifyMult(id, 1 - 0.01*25)`。
    *   结果 `recoil=0.750 aim=1.250 ballisticRange=15.0 energyRange=15.0`；
    *   日志里的 `count` 读 `getNumMembers()`，会触发一次全量舰队同步 (`FleetDataAPI.syncIfNeeded`)，全员 `updateStats` 再跑一遍 —— 同 ID 幂等，数值不变。
- 观察点 4(`GearInStatsRebuild` 步骤 2—— 重建即丢弃)：`modifyFlat("GearIn_transient_speed", 25f)` 直接写 `getStats()` 返回值 → `speed` 增 25：
    - `updateStats()` 整体新建对象 (`rebuiltNewObject=true`)→ 修改被丢弃 (`speedAfterRebuild` 回到 `speedBefore`)。
    - **这是 "重建即丢弃" 的现场证明**。
- 观察点 4b(`GearInStatsRebuild` 步骤 3—— 延迟重建)：`setStatUpdateNeeded(true)` 只置标记；`getStats()` 延迟重建 → `sameObjectAsBefore=false`。
- 观察点 5(`GearInVariantToStats` 步骤 2—— 装配 → stats)：克隆装配加 5 电容 + 5 散热，`setVariant(refit, false, true)`：`withRefit=false` 跳过 CR 影响计算 (`computeVariantDiffCRImpact`)，`withStatsUpdate=true` 立即重建：
    - `flux_capacitors` = `numCaps * 200`(settings.json 的 `fluxPerCapacitor:200`)、`flux_vents` = `numVents * 10` (`dissipationPerVent:10`)→ 幅能容量 `+1000`、散耗 `+50`，与船插/CR 贡献无关，**确定性**。
    - `sharedVariant=true`(`setVariant` 传入引用不克隆，重建后 stats 仍指向同一变体)、`specId=wolf_Assault`。
- 观察点 6(`GearInCaptainToMember` 步骤 3—— 换舰长恢复)：`setCaptain(plain)`(无技能默认人物)→ 立即重建 → `recoil 0.750 -> 1.000`。
- 观察点 7(`GearInCommanderToMember`—— 指挥官 → 成员)：`setFleetCommanderForStats(commander, fleetData)` 只标记 (第二参数被忽略)；`getStats()` 延迟重建 → `applyFleetwideToStats`：
    - `crew_training` Level1 (`getMaxCombatReadiness().modifyFlat(id, crBonus*0.01)`，`com.fs.starfarer.api.impl.campaign.skills.CrewTraining`，非民用船生效)→ `maxCR` 上升。
    - 置 `null` 后回退 `fleetData.getCommander()`(玩家)→ 恢复原值。
- 观察点 8(清理)：四个类各自 `removeFleetMember(wolf)` → 玩家舰队成员数与初始一致 (同步延迟到下一次同步，`getNumMembers()` 会立即同步)。演示脚本**不留副作用** (人物对象只存在于局部变量，不被任何持久集合引用)。

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含四个类的 class 条目 (可用 `jar tf` 核对)。

### 关键故障实验：成员 stats 重建后自定义加成消失

先在成员 stats 上直接写入自定义 modifier，再通过换装或舰队同步触发 `updateStats()`；日志会显示修改对象已经被替换。

**局部故障片段：**

```java
// 故障片段
member.getStats().getMaxSpeed().modifyFlat(MOD_ID, 5f);
```

**局部修正片段：**

```java
// 修正片段
// 放入船插的 applyEffectsBeforeShipCreation(hullSize, stats, id)
stats.getMaxSpeed().modifyFlat(id, 5f);
```

原因：成员 stats 是重建产物，直接修改只存在于当前对象。把修改放在会被 `updateStats()` 重放的入口，并使用该入口传入的稳定 `id`，每次重建都会重新写入。复验：触发换舰长、换装配和舰队同步，三次读取的速度都应保留 5 点加成；移除该船插后再重建，数值恢复。

## 验证方案

前置数据：MOD 布局与 `GearInModPlugin`(含本节调用点片段；[C6-S1]、[C6-S2])、本节四个演示类、观察 `speedAfterRebuild` 语义同样成立 (界面触发同步 = 重建 = 丢弃；对应规则强度与 "重建后修改值消失" 故障条目)。

1. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含四个类的 class 条目。
2. **新建生涯**：查看 `starsector.log` 中按注册顺序出现的四组日志：`GearIn variantToStats ...`、`GearIn captainToMember ...`、`GearIn commanderToMember ...`、`GearIn statsRebuild ...`。
3. **只执行一次**：等待若干游戏日，继续观察四个演示类的日志。
4. 保存并读档，再次观察四个演示类的日志和玩家舰队构成。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInVariantToStats.class` 等四个类的 class 条目。
- **新建生涯 (首次进入存档后第一帧)**：`starsector.log` 按注册顺序依次出现以下日志 (`<N>` 为初始成员数；数值以实际为准，但标出的关系是确定性的)：

    ```text
    GearIn variantToStats baseline: hull=wolf specId=wolf_Assault caps=9 vents=10 fluxCap base=2250 mod=<2250+1800> fluxDiss base=150 mod=<150+100+fluxdistributor贡献> speed=<135.0> maxCR=<0.200~0.700 视船员> variantShared=true memberLinked=true GearIn variantToStats refit: caps 9->14 vents 10->15 fluxCap <a>-><a+1000> (+1000) fluxDiss <b>-><b+50> (+50) sharedVariant=true specId=wolf_Assault GearIn captainToMember start: members=<N> GearIn captainToMember preJoin: recoil=1.000 aim=-0.500 (skills not applied, member not in fleet) GearIn captainToMember joined: count=<N+1> recoil=0.750 aim=1.250 ballisticRange=15.0 energyRange=15.0 GearIn captainToMember swap: recoil 0.750 -> 1.000 (restored) GearIn captainToMember done: members=<N> (initial <N>) GearIn commanderToMember start: members=<N> GearIn commanderToMember: maxCR <c> -> <c+crewTraining加成> (crewTraining) -> <c> (reset) GearIn commanderToMember done: members=<N> (initial <N>) GearIn statsRebuild start: members=<N> GearIn statsRebuild transient: speed <135.0> -> <160.0> (with transient), rebuiltNewObject=true speedAfterRebuild=<135.0> GearIn statsRebuild lazy: sameObjectAsBefore=false GearIn statsRebuild done: members=<N> (initial <N>)
    ```

    要点核对：`variantToStats baseline` 的 `variantShared=true`/`memberLinked=true`(stats 与变体、成员双向绑定)；`captainToMember preJoin` 的 `1.000/-0.500`(未入队不应用技能)；`captainToMember joined` 的 `0.750/1.250/15.0/15.0`(入队后技能生效，`gunnery_implants` L1 的 recoil/aim/range)；`statsRebuild transient` 的 `rebuiltNewObject=true` 与 `speedAfterRebuild` 回落 (重建即丢弃)；`variantToStats refit` 的 `+1000/+50` 与 `sharedVariant=true`(装配 → stats 交接与引用共享)；`captainToMember swap` 的 `0.750 -> 1.000`(换人立即重算并恢复)；`commanderToMember` 的 "中间值 > 前后值且恢复值 == 初始值"(舰队指挥官 → 成员 stats 交接)；四个 `done` 行成员数均与各自 `start` 行一致 (无残留)。类间顺序以注册顺序为准，类内顺序确定。
- **报错定位**：若日志出现某个演示类的 ERROR 堆栈 (前缀 `GearIn variantToStats / captainToMember / commanderToMember / statsRebuild`)，说明该步抛错 —— 按堆栈对照 "规则强度" 节定位 (最常见的是引用了不存在的技能 id 或装配 id)。
- **读档**：不重复执行演示 (四个 `REGISTER_MARK` 键已关)；玩家舰队构成与演示前一致。
- **退出到主菜单**：脚本停止 (生涯状态不再运行)；回主菜单后再进档按第 5 条继续。

## 故障分析

- **生涯侧数值自行恢复**：`updateStats` 新建了 stats 对象，直接写在旧对象上的 modifier 随之失效。将长期效果放进船插、技能或 buff 的重放入口；触发一次重建后确认数值仍在。
- **换舰长后成员数值没有变化**：成员尚未加入舰队，个人技能应用被 `getFleetData() != null` 条件跳过。先入队再调用 `setCaptain`，随后比较 stats。
- **换装配后界面仍显示旧值**：修改没有设置重算标记，`getStats()` 继续返回旧对象。使用带 stats 更新的 `setVariant`、`updateStats` 或 `setStatUpdateNeeded(true)`，再重开舰队界面。
- **生涯数值与战斗数值不一致**：部署会创建新的战斗 stats，并继续叠加战斗期效果。生涯侧只用于预览；部署后从 `ShipAPI.getMutableStats()` 读取最终值复验。
- **战斗中换舰长后加成没有更新**：只修改人物对象不会把技能重新应用到战斗 stats。调用 `ShipAPI.setCaptain(newPerson)`；需要恢复时传入 `getOriginalCaptain()`，再检查对应 modifier。
- **自定义 modifier 被覆盖**：代码复用了引擎的 modifier ID，同类型的后写值覆盖了前值。改用带 MOD 前缀的专属 ID，重建 stats 后确认引擎值与自定义值同时存在。
- **船插应用时出现 `StackOverflowError`**：`applyEffectsBeforeShipCreation` 内再次调用了会触发 `updateStats` 的成员 API。该入口只修改 stats 或变体内容，把换舰长、换装配移到脚本层；重新触发重建后确认不再递归。
- **下一场战斗读到旧 stats**：代码跨战斗保存了 `ShipAPI` 或 `getMutableStats()` 引用。每场战斗重新查询，并在战斗结束时丢弃引用；下一场核对对象与数值均来自当前实体。
- **舰队技能只影响部分成员**：舰队向效果会跳过战机，具体技能还可能要求非民用舰或军官驾驶。按效果作用域和技能条件逐艘核对，确认符合条件的成员获得 modifier。
- **更换装配后 CR 下降**：`setVariant` 使用了 `withRefit=true`，引擎按装配差异应用现场改装惩罚。代码批量换装时传 `false`；再次替换并确认 CR 不再扣减。
- **提高技能等级后成员数值不变**：SHIP 效果要等成员 stats 重建，`setSkillLevel` 本身不会触发该过程。随后调用 `updateStats()` 或触发舰队同步，再读取成员数值。

## 总结

本节把状态交接讲成一条四环节链：装配内容决定 stats 基值，人物技能经个人/舰队两条通道在重建时重放，部署链为战斗新造 stats 并按自己的顺序重放一遍，`MutableStat` 的延迟重算与同 ID 幂等则保证这一切可以安全重复。链的结算时刻始终是 `updateStats`：整体新建 stats 对象、按固定序列重放 —— 要跨结算保留的修改，只能挂在重放入口上。

记忆要点：

- 交接的结算时刻是 `updateStats`：新建 stats、按序重放、同 ID 幂等；要跨结算保留的修改必须挂在重放入口。
- 舰长技能要 "入队才生效"(`getFleetData() != null`)；舰队向技能跳过战机。
- `setVariant` 传引用不克隆，stats 与成员共享变体；部署链每场新造战斗 stats，禁止跨战斗缓存。
- 战斗内换舰长 = 解挂旧 + 挂新 (含子模块)，立即生效；`getPersonality` 来自舰长。
- 调试先看对象同一性 (`before != after`)，再谈数值。

这条链闭合后的意义在于：生涯侧与战斗侧各自维护自己的 stats，跨场景只通过 "重建 + 重放" 传递语义、不共享对象 —— 因此任何 "跨战斗缓存" 的尝试都必然读到过期对象，而任何 "挂在重放入口" 的修改都天然获得幂等性。生涯侧的脚本时序、事件与存档主题都可沿用本节的注册判断模式 (每注册项独立 persistent data 键) 与 "先问重放入口、再问来源 ID" 的排查顺序；变体本身的语义 (克隆、来源、武器组改写) 也始终以本节的 `setVariant` 引用共享规则为落点。

## 建议参考项

- `com.fs.starfarer.api.combat.MutableStat`：公开类即实现 —— 延迟重算、同 ID 幂等覆盖、`unmodify(source)` 清三类 (环节四)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：成员/战斗 stats 的统一接口与数值访问器：
    - 数值访问器：`getFluxCapacity`、`getMaxSpeed`、`getMaxCombatReadiness`、`getAutofireAimAccuracy`、`getMaxRecoilMult`。
    - 射程与对象访问器：`getBallisticWeaponRangeBonus`、`getEnergyWeaponRangeBonus`、`getVariant`、`getFleetMember`、`getEntity`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：生涯侧成员入口 ——`getStats`、`setCaptain`、`setVariant`、`setStatUpdateNeeded`、`setFleetCommanderForStats`、`updateStats`。
- `com.fs.starfarer.api.characters.PersonAPI` 与 `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：人物与技能等级入口 (`setSkillLevel`、`refreshCharacterStatsEffects`)。
- `com.fs.starfarer.api.combat.ShipAPI`：战斗实体 ——`getMutableStats`、`getCaptain`、`getOriginalCaptain`、`setCaptain`、`getFleetCommander`。
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：船体 spec——`getFluxCapacity`/`getFluxDissipation` 是幅能基值来源，嵌套 `EngineSpecAPI`。
- `com.fs.starfarer.api.impl.combat.CRPluginImpl`：`cr_effect` / `crew skill bonus` / `crew understrength` 保留 ID 的引擎写入方。
- `com.fs.starfarer.api.impl.campaign.skills.GunneryImplants` 与 `com.fs.starfarer.api.impl.campaign.skills.CrewTraining`：本节演示使用的原版技能效果类。
- `data/scripts/gearin/campaign`：本节四个演示类 (`GearInVariantToStats`、`GearInCaptainToMember`、`GearInCommanderToMember`、`GearInStatsRebuild`) 所在的 MOD 源码包。
- `data/config/settings.json`：`fluxPerCapacitor:200`、`dissipationPerVent:10` 的出处。
- `data/hulls/ship_data.csv` 与 `data/variants/wolf/wolf_Assault.variant`：演示基线数据 (wolf 的 `max flux`/ `flux dissipation`/`max speed`；`fluxCapacitors:9`/`fluxVents:10`)。

## 完整最小例

::: details 完整成品：GearInVariantToStats

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
 * 环节一：装配 -> stats 交接 (舰船 stats 基值与 setVariant 重算)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 基线：创建 wolf_Assault 成员 (未入队)，读取舰船 stats 基值 (幅能/速度/CR) 与
 *      stats.getVariant() / getFleetMember() 的对象关系；
 *   2. 装配 -> stats：克隆装配加 5 电容 + 5 散热后 setVariant(withRefit=false,
 *      withStatsUpdate=true)，幅能容量 +5*200、幅能散耗 +5*10(引擎保留 ID 的 flat 写入)，
 *      stats.getVariant() 与传入变体同一引用。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键)，不遗留任何持久状态。
 */
public class GearInVariantToStats implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_VariantToStats";

    private static final Logger LOG = Global.getLogger(GearInVariantToStats.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInVariantToStats() {
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

:::

::: details 完整成品：GearInCaptainToMember

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
 * 环节二 (个人)：舰长技能 -> 成员 stats 交接 (applyPersonalToStats)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 未入队换舰长：setCaptain 触发 updateStats，但舰长个人技能不应用
 *      (updateStats 的舰队条件：getFleetData() 为 null 时跳过 applyPersonalToStats)；
 *   2. 入队 + 显式 updateStats：个人技能 (gunnery_implants L1) 开始应用，
 *      后坐力倍率 1.0 -> 0.75、自动瞄准 0.0 -> 1.0、实弹/能量射程 +15%；
 *   3. 入队换舰长：setCaptain 立即重算，换回无技能人物后数值恢复。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键与演示成员的增删)，不遗留任何持久状态。
 */
public class GearInCaptainToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CaptainToMember";

    private static final Logger LOG = Global.getLogger(GearInCaptainToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCaptainToMember() {
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

        // --- 2. 入队 + 显式 updateStats：个人技能开始应用 (getFleetData() 已非 null) ---
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

:::

::: details 完整成品：GearInCommanderToMember

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
 * 环节二 (舰队向)：舰队指挥官 -> 成员 stats 交接 (applyFleetwideToStats)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 建成员并入队：stats 只含装配基值 (无任何技能加成)；
 *   2. setFleetCommanderForStats 挂 crew_training(ALL_SHIPS_IN_FLEET)，getStats()
 *      惰性重建后 maxCombatReadiness 上升；
 *   3. 置回 null：舰队向加成撤销，数值恢复。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键与演示成员的增删)，不遗留任何持久状态。
 */
public class GearInCommanderToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CommanderToMember";

    private static final Logger LOG = Global.getLogger(GearInCommanderToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCommanderToMember() {
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn commanderToMember start: members=%d", initialCount));

        // --- 1. 建成员并入队：stats 只含装配基值 (无任何技能加成) ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleetData.addFleetMember(wolf);
        float maxCRBefore = wolf.getStats().getMaxCombatReadiness().getModifiedValue();

        // --- 2. 舰队指挥官 -> 成员 stats(ALL_SHIPS_IN_FLEET 经 applyFleetwideToStats 重放) ---
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

:::

::: details 完整成品：GearInStatsRebuild

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
 * 环节四：MutableStat 重建与惰性重算 (updateStats 整体新建舰船 stats)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 运行时 transient modifier 不跨重建：直接 modifyFlat 到 getStats() 返回值，
 *      updateStats 后 stats 对象整体重建 (对象同一性为 false)、修改被丢弃；
 *   2. 惰性重建：setStatUpdateNeeded(true) 后下一次 getStats() 才重建。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键与演示成员的增删)，不遗留任何持久状态。
 */
public class GearInStatsRebuild implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_StatsRebuild";

    private static final Logger LOG = Global.getLogger(GearInStatsRebuild.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInStatsRebuild() {
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn statsRebuild start: members=%d", initialCount));

        // --- 1. 建成员并入队 (无舰长：updateStats 重放时无技能加成干扰) ---
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

:::
