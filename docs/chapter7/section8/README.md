# 战术系统运行态：Stats、状态机与目标

本节把战术系统的运行态讲透：`ShipSystemAPI` 的运行时 stats 访问器（状态、冷却、弹药、幅能、效果强度）、五态状态机的精确推进规则（含开关式、幅能不足提前结束与 `forceState` 改写）、目标获取的三条通道（用户右键坐标 `getTargetLoc()`、舰船当前目标 `getShipTarget()`、`TARGET_FOR_SHIP_SYSTEM` AI flag），以及 `ShipSystemAIScript` 的接入链与优先级（`aiType: CUSTOM` + `aiScript`、系统 AI 适配器、`aiHints` 威胁评估）。数据面（`.system`、`ship_systems.csv`、`ship_data.csv` 挂载）已在前置单元完整讲解，本节只在其上展开运行态，不重复数据面。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 背出 `ShipSystemAPI` 的状态访问器分组（状态 / 效果 / 冷却 / 弹药 / 幅能 / 激活控制 / 目标），说出每个代表方法的返回类型与语义；
- 精确说出五态状态机的推进规则：`IDLE`→`IN`→`ACTIVE`→`OUT`→`COOLDOWN` 每一步的转移条件、开关式（toggle）系统的两种结束路径、幅能不足提前结束、`forceState` / `deactivate` 的语义
  - 以及 stats 脚本的可见性边界（`apply` 只在 `effectLevel > 0` 或 `runScriptWhileIdle` 时被调用，`COOLDOWN`/`IDLE` 两态脚本不可见）；
- 说出目标获取的三条通道（`getTargetLoc()` 用户右键坐标、`getShipTarget()` 舰船当前目标、`TARGET_FOR_SHIP_SYSTEM` AI flag），解释 flag 的寿命语义（`FLAG_DURATION = 0.5f`，舰船 AI 覆写 1.0 秒）与用户舰侧的差异（无 AI 舰船无人写入 flag）；
  - 说出系统 AI 的接入链：
    - `.system` 的 `aiType: "CUSTOM"` + `aiScript` 类名 → `ShipSystemSpecAPI.getAIScript()` 实例化 → `init(ship, system, flags, engine)`
    - 包装成系统 AI 适配器 → 舰船 AI 每帧调 `advance(amount, missileDangerDir, collisionDangerDir, target)`
  - 并解释"statsScript 与 aiScript 是同名类的两个独立实例"这一事实；
- 从 `starsector.log` 症状定位典型故障：部署期 `Error creating AI of type [...]`、AI 从不使用系统、用户按 U 无反应、激活无加成、加成中途消失、目标过期、跨战斗缓存残留。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- 船体注册与 `system id` 挂载（C01-S02）：`ship_data.csv` 的 `system id` 列与 `GearIn_artizan` 示例行——本节示例的挂载目标。
- 战术系统数据面四段完整流程（C02-S03）：`.system` / `ship_systems.csv` / `ship_data.csv` / 效果脚本、`effectLevel` 概念——本节在其上展开运行态访问器、推进规则、目标与 AI 接入的完整细节。
- 原版 AI 基线：不写任何代码时，舰船自动使用原版默认 AI（转向、目标选择、开火、规避）；`ModPlugin.pickShipAI(...)` 返回 `null` 即保持原版 AI；`.system` 的 `aiType` 字段决定战术系统的 AI 行为（此前示例写的 `NONE` = 无 AI 行为，玩家按键不受影响）——本节展开系统 AI 的完整接入链。
- 实体判据与暂停语义（C07-S02）：`getPlayerShip()` 永不 null、`isAlive()` / `isEntityInPlay()` 实体判据、暂停语义——本节 AI 与脚本的守卫消费这些判据。
- `MutableStat` 的 modifier ID 纪律（C07-S03）：modifier ID 稳定性与 `modifyMult` / `unmodify` 纪律——本节 stats 脚本的写入做法同规。
- `Global` 生命周期与跨战斗缓存禁令（C06-S03）：禁止跨战斗缓存引擎/实体引用——本节 AI 与脚本里的实体引用同规。

## 文件与 API 链路

本节涉及的接口都在 `com.fs.starfarer.api.combat`（脚本接口在 `com.fs.starfarer.api.plugins`，基类在 `com.fs.starfarer.api.impl.combat`）：

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `ShipSystemAPI` | 舰船上运行时系统实例 | 战斗创建舰船时由 spec 生成（`createSystem`）；`ShipAPI.getSystem()` / `getPhaseCloak()` 取得 |
| `ShipSystemSpecAPI` | 系统数据 spec | `.system` + `ship_systems.csv` 合并产物；`getStatsScript()` / `getAIScript()` 按类名实例化脚本 |
| `ShipSystemStatsScript` / `Advanced` | 效果脚本接口 | `.system` 的 `statsScript` 挂载；`State` / `StatusData` / `apply` / `unapply` / `getStatusData` / `isUsable` / `getInfoText` |
| `BaseShipSystemScript` | 官方基类 | `com.fs.starfarer.api.impl.combat`；空实现全部方法，覆写方法返回 `-1` / `null` |
| `ShipSystemAIScript` | 系统 AI 接口 | `.system` 的 `aiScript` 挂载（仅 `aiType: CUSTOM` 时消费）；`init` / `advance` |
| `ShipwideAIFlags` | AI 短期 flag 容器 | `ShipAPI.getAIFlags()`；`TARGET_FOR_SHIP_SYSTEM` / `SYSTEM_TARGET_COORDS` 等 |
| `ShipAPI` | 舰船实体入口 | `getSystem()` / `getPhaseCloak()` / `getShipTarget()` / `getAIFlags()` / `useSystem()` |
| `MutableShipStatsAPI` | stats 集合 | 系统四个 bonus 族：`getSystemRangeBonus` / `getSystemCooldownBonus` / `getSystemUsesBonus` / `getSystemRegenBonus`（`StatBonus`）|
| `IntervalUtil` | 决策节流 | `com.fs.starfarer.api.util`；AI 决策与日志节奏 |

数据到运行态的链路（前置单元的数据面 + 本节运行态）：

```text
ship_data.csv 的 system id / defense id 列
  → ShipHullSpecAPI.systemId（船体 CSV 加载器）
  → 战斗创建 Ship 时按 id 查 ShipSystemSpecAPI（.system + ship_systems.csv 合并产物）
  → ShipSystemSpecAPI.createSystem(type) → 系统运行时对象（每舰一个）
  → 系统运行时对象构造器调用 spec.getStatsScript() 创建 stats 脚本实例（每系统一个）
  → 舰船 AI 创建时调用 spec.createSystemAI(aiType)
      → aiType == CUSTOM 时 spec.getAIScript() 创建 AI 脚本实例，init 后包装为系统 AI
  → 舰船 AI 每帧调 systemAI.advance(...)；系统运行时对象.advance 每帧推进状态机并回调 stats 脚本
```

`ShipSystemAPI` 状态访问器速查（全部签名见「建议参考项」）：

| 分组 | 成员 | 语义 |
|---|---|---|
| 状态 | `getState()` / `isActive()` / `isOn()` / `isStateActive()` / `isChargeup()` / `isChargedown()` / `isCoolingDown()` | `SystemState` 五态枚举与四个布尔视图（详见"状态机"）|
| 效果 | `getEffectLevel()` / `getChargeUpDur()` / `getChargeActiveDur()` / `getChargeDownDur()` | 效果强度 0–1 与三段时间（来自 CSV `charge up` / `active` / `down`）|
| 冷却 | `getCooldown()` / `getCooldownRemaining()` / `setCooldown()` / `setCooldownRemaining()` | 冷却总时长与剩余；剩余量只在 `COOLDOWN` 态倒计时，`IN`/`ACTIVE`/`OUT` 返回完整时长，`IDLE` 返回 0（充能计时器的冷却进度值）——这也是原版内置 AI 用 `getCooldownRemaining() > 0` 作守卫时能同时拦下"激活中/后摇中"的原因 |
| 弹药 | `getAmmo()` / `getMaxAmmo()` / `getAmmoPerSecond()` / `getAmmoReloadProgress()` / `isOutOfAmmo()` / `setAmmo()` | `max uses` / `regen` 两列的运行态（`AmmoTrackerAPI`）|
| 幅能 | `getFluxPerUse()` / `getFluxPerSecond()` / `setFluxPerUse()` / `setFluxPerSecond()` | 每次激活与每秒幅能成本（系统创建时按 flux 容量/耗散折算）|
| 激活控制 | `canBeActivated()` / `deactivate()` / `forceState(state, progress)` | 激活许可、强制进入冷却、直接改写状态机 |
| 目标 | `getTargetLoc()` | 用户右键激活时传入的引擎坐标（`Vector2f`，AI 激活时为 null）|
| 脚本 | `getScript()` / `getSpecAPI()` / `getId()` / `getDisplayName()` | stats 脚本实例、spec、id 与显示名 |

四个 bonus 族的使用方（`ShipSystemSpecAPI` 的取值方式）：

```text
getSystemRangeBonus().computeEffective(spec.range)      → getRange(stats)
getSystemCooldownBonus().computeEffective(spec.cooldown) → getCooldown(stats)
getSystemUsesBonus().computeEffective(spec.maxUses)      → getMaxUses(stats)
getSystemRegenBonus().computeEffective(spec.regen)       → getRegen(stats)
```

::: info bonus 的部署时烘焙与即时读取
系统运行时对象在创建时（构造器调用）**一次性**把这些值写回充能记录—— 冷却、次数与恢复的 bonus 在舰船部署时烘焙，运行期改动这几项不会反映到本场战斗（重新部署才重算）；

而直接调用 `spec.getRange(stats)` / `stats.getSystemRangeBonus().computeEffective(...)` 的读取（如本节示例的 AI 每次决策现取有效射程）是即时的。
:::

## 状态机：五态与推进规则

`ShipSystemAPI.SystemState` 与 `ShipSystemStatsScript.State` 是同一组状态的两种视图（枚举顺序一致），回调里拿到的是 `State`，`ShipSystemAPI.getState()` 返回 `SystemState`。

五个状态与 `effectLevel`（= 充能进度值）：

| 状态 | `getChargeProgress` | 语义 |
|---|---|---|
| `IDLE` | 0 | 待机；激活许可通过后响应激活请求 |
| `IN` | `timer / chargeUp`（`charge up` 为 0 时恒 1）| 前摇，线性增强 |
| `ACTIVE` | 1 | 全功率段 |
| `OUT` | `1 - timer / down`（`down` 为 0 时恒 1）| 后摇，线性减弱 |
| `COOLDOWN` | 0 | 冷却，期间无法再次激活 |

### 推进规则（每帧充能推进）

每帧系统运行时对象把"本帧是否有激活请求"传给充能推进 `advance(activated, dt)`，状态机按当前态分派：

1. **IDLE + 激活请求**：按顺序检查——
   - `canBeActivated()`（`requiresZeroFluxBoost` 时要求零幅能加速引擎开启）
   - 弹药（用弹药系统且弹尽 → `failedToActivateDueToAmmo`）
   - `raiseFlux(activationFlux, hardFlux)` 扣激活幅能（扣不动 → `failedToActivateDueToFlux`）
   - `deductOneAmmo()` 扣一发（失败 → `restoreFlux` 退回幅能 + `failedToActivateDueToAmmo`）
   - 全部通过 → `state = IN`、`timer = 0`、回调 `systemChargingUp()`。
2. **IN**：`timer += dt`；`timer >= charge up` → `state = ACTIVE`、`timer = 0`、回调 `systemActivated()`。`charge up` 为 0 时当帧即转入 `ACTIVE`（`IN` 只存在一帧，该帧 `effectLevel` 为 1）。
3. **IN / ACTIVE 每帧扣幅能**：`raiseFlux(fluxPerSecond * dt, hardFlux)`；扣不动且当前系统可关闭（`canBeDeactivated()`）→ **提前结束**：`state = OUT`、回调 `systemChargingDown()`。可观察结果：幅能成本超过耗散能力时，`ACTIVE` 段缺失或极短，系统"刚开就关"。
4. **ACTIVE 结束路径**：
   - 非开关式（`toggle` 空/`FALSE`）：`timer >= active` → `OUT`（自动结束）；
   - 开关式（`toggle=TRUE`）且 `active > 0`：同样按 `timer >= active` 自动结束（例如原版 Burn Drive `active=5`，开满 5 秒自动关）；
   - 开关式且 `active` 为 0（如原版 Fortress Shield）：时长分支整体跳过，系统**无限保持 `ACTIVE`**，直到再次激活请求到来且 `canBeDeactivated()` → `OUT`（开关式分支的手动关闭）。
5. **OUT**：`timer += dt`；`timer >= down` → `state = COOLDOWN`、`timer = 0`、回调 `systemDeactivated()`。
6. **COOLDOWN**：`timer += dt`；`timer >= cooldown` → `state = IDLE`、回调 `systemCooldownFinished()`。`cooldown` 为 0 时冷却段只存在一帧。

激活请求的来源：用户按 U / 右键，AI 调 `ShipAPI.useSystem()`（→ `giveCommand(USE_SYSTEM)`；命令处理把参数坐标传给 `system.fire(param)`）。系统对象的 `fire(loc)` 只记录 `targetLoc` 并置激活请求标志。

### 必须遵守的规则

1. **`isUsable()` 返回 `false` 时，激活请求被丢弃**。系统每帧推进时，在激活请求帧先检查脚本是否 `ShipSystemStatsScriptAdvanced` 且 `isUsable(system, ship)` 为真；为假则清掉请求并调 `failedToActivateDueToAmmo()`（连续失败 2 秒后才播放失败音）。
   - 违反/误解的可观察结果：用户按 U **没有任何状态变化、没有报错**；连续空按约 2 秒后听到"弹药耗尽"音效
   - 因此**禁止**把 `isUsable` 写成恒 `true` 之外的"随口返回"—— 它决定"能不能按下"，而状态机决定"按下后走哪段"。
2. **`apply` 里写入的每一项 stat 修改，`unapply` 必须用同一把钥匙撤销**。引擎传入的 `id` 是 `系统id + " effect"`（例如 `GearIn_targetingsystem effect`），modifier ID 稳定性纪律在这里同样成立。违反的可观察结果：系统结束后效果残留（速度加成不消失），且**没有任何报错**。
3. **`forceState(state, progress)` 直接改写运行时状态机，跳过正常转移回调**。
   - `progress` 的语义按目标态不同（`IN` → 前摇进度、`OUT` → 后摇进度、`COOLDOWN` → 冷却比例、`ACTIVE` → 时长比例）；`deactivate()` → `forceDeactivate()` 直接进入 `COOLDOWN`
   - 它适合脚本 / 调试逻辑，**禁止**用它在正常流程里"代替"关闭—— 它会跳过 `systemChargingDown` / `systemDeactivated` 回调的一部分语义。
4. **stats 脚本的可见性边界**：`apply` 只在 `effectLevel > 0`（`IN`/`ACTIVE`/`OUT`）或 `.system` 开启 `runScriptWhileIdle` 时每帧被调用；`effectLevel` 归零时（进入 `COOLDOWN`/`IDLE`、舰船撤退或解体）`unapply` 被调用一次。
   - **`COOLDOWN` 与 `IDLE` 两态在 stats 脚本里不可见**—— 需要观察这两态的时机用 AI 角色或战斗插件
   - `getStatusData` 只对用户舰船每帧按 `index = 0、1、2...` 查询（上限 7 个）
   - `runScriptWhilePaused`（默认 `false`）控制暂停期间是否额外运行脚本（`advanceEvenIfPaused`）
5. **暂停时状态机不推进**：系统随舰船推进（系统的每帧推进由 `Ship.advance` 驱动），暂停分支只推进用户舰与地图（前置单元已证），因此 `effectLevel` 冻结在暂停前的值；AI 角色与 stats 脚本也随之停摆，脚本里**不必**再写 `isPaused()` 守卫（写了也无害，成熟实例仍保留）。

## 目标获取：三条通道

1. **用户右键坐标**：用户右键（或点击）激活系统时，`USE_SYSTEM` 命令携带鼠标位置坐标并传给 `system.fire(loc)`，随后 `getTargetLoc()` 返回该坐标（`ShipSystemAPI.getTargetLoc()`）
   - AI 激活时参数为 null（系统 AI 发出的 `USE_SYSTEM` 激活命令不带坐标参数），`targetLoc` 保持 null
   - 可观察结果：用户右键激活的传送/位移类系统以光标为落点，AI 激活的同类系统没有落点参数。
2. **舰船当前目标**：`ShipAPI.getShipTarget()`——用户瞄准锁定的目标或 AI 的当前攻击目标。它不依赖任何 flag，用户舰与 AI 舰都可用，是目标系统的首选回退。
3. **AI flag**：`ShipwideAIFlags.AIFlags.TARGET_FOR_SHIP_SYSTEM`。
    - **引擎侧维护者**：舰船 AI 每帧在调用系统 AI **之前**执行 `ship.getAIFlags().setFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM, 1.0F, target)`，目标不可见（战争迷雾）时写入 null
      - 也就是说这个 flag 的"custom 数据"就是当前目标，读它不需要自定义 AI 就有值
   - flag 寿命：`ShipwideAIFlags.FLAG_DURATION = 0.5f`，舰船 AI 覆写为 1.0 秒，过期由持有方每帧调 `aiFlags.advance(amount)` 清除

由通道 3 引出的两个关键事实（`ShipAPI.getAIFlags()`）：

- **`getAIFlags()` 永不返回 null**：非 AI 舰船（用户舰）会用到时才创建一份 flag 容器，以 `"nonAI_SAIF_" + shipId` 为键存在引擎 customData 里
  - 但**非 AI 舰船没有任何每帧刷新者**——`TARGET_FOR_SHIP_SYSTEM` 只会被舰船 AI 写入，用户舰的这份 flag 无人写入、也永不自动过期。

::: caution 读取 flag 前必须判"舰船是否有 AI"
`ship.getShipAI() == null`（用户舰）时 flag 语义不可用，回退 `getShipTarget()`。成熟实例正是这个判空做法。
:::

### 必须遵守的规则

6. **AI 写入的目标 flag 有寿命，消费前必须再验证一次**。flag 默认 0.5 秒过期、舰船 AI 覆写 1.0 秒——AI 上一轮决策写入的目标，下一帧可能已经死亡、脱离射程或换了目标。
   - `stats` 启动侧拿到 `getCustom(AIFlags.TARGET_FOR_SHIP_SYSTEM)` 后**必须**重新执行合法性检查（存活、非残骸、非相位、敌我、射程），验证不过就不施加效果。
   - 违反的可观察结果：目标被击毁后效果仍持续一段（flag 未过期窗口），或反过来效果"闪断"（flag 已过期）
   - AI 侧同理：激活期间要持续刷新 flag（本节示例每 0.2–0.3 秒重写一次），否则 `IN`/`ACTIVE` 后段锁会丢。
7. **目标合法性检查必须包含相位与残骸**。`isValidTarget` 的标准五件套：`isAlive()`、`!isHulk()`、`!isPhased()`、`owner` 与发射侧不同、距离不超过有效射程（`getSystemRangeBonus` 修正后的值）。
   - 漏掉相位判定的可观察结果：系统对相位中的敌舰"锁了个寂寞"（效果无效但状态文字显示锁定）
   - 漏掉残骸判定的结果：对残骸施加效果、浪费幅能，且无报错。

## 系统 AI：接入、实例化与优先级

`ShipSystemAIScript`（`com.fs.starfarer.api.combat`）只有两个方法：

- `init(ShipAPI, ShipSystemAPI, ShipwideAIFlags, CombatEngineAPI)`
- `advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)`

接入链：

```text
.system 里 "aiType": "CUSTOM" + "aiScript": "data.scripts.gearin.shipsystems.GearInTargetingSystem"
  → ShipSystemSpecAPI.aiType / aiScript 字段
  → 舰船 AI 创建时，其构造器调 spec.createSystemAI(...)
  → createSystemAI 的 switch 命中 CUSTOM 分支：
      spec.getAIScript() → 脚本类加载器.loadScriptInstance(aiScript)（类缓存 + 每次 newInstance）
      → aiScript.init(ship, ship.getSystem(), aiFlags, engine)
      → 包装成系统 AI 适配器（advance 转发给 ShipSystemAIScript.advance）
  → 舰船 AI 每帧调 systemAI.advance(amount, 导弹危险方向, 碰撞危险方向, target)
      （target 是 AI 当前目标，迷雾中不可见时为 null）
```

### 必须遵守的规则

8. **`aiScript` 只在 `aiType: "CUSTOM"` 时被消费**。`createSystemAI` 按 `aiType` 枚举分派到内置 AI 类（EMP → 原版 EMP 系统 AI、相位 → 原版相位隐形系统 AI 等），唯一走 `getAIScript()` 的分支是 CUSTOM。
   - `aiType: "NONE"` 不创建任何系统 AI（返回 null）。
   - `aiType` 缺失、拼错或与 `type` 不配对时，**舰船部署生成 AI 的瞬间**抛 `RuntimeException("Error creating AI of type [...] for ship system [...]")` ，该舰船无法进入战斗——与数据面单元故障分析中的同一可观察结果
   - `aiType: CUSTOM` 而 `aiScript` 缺失/类名错时同样在部署期失败（`getAIScript()` 返回 null → `init` 空指针被包装；类名错则 `Problem loading class [...]`）。
9. **statsScript 与 aiScript 是同名类的两个独立实例**。`getStatsScript()` 与 `getAIScript()` 各自经脚本类加载器按类名创建实例——类缓存共享，但**每次 `newInstance()`**。
   - 一个类同时挂两个角色时，stats 实例与 AI 实例的**实例字段互不共享**；共享逻辑必须走静态方法（本节示例的全部目标逻辑都是静态的）或 `ship.setCustomData`。
   - 违反的可观察结果：在 stats 实例字段里存 AI 决策状态（或反过来），另一侧永远读不到——静默失效，无报错。
10. **系统 AI 只在 AI 舰船上运行**。`systemAI` 由舰船 AI 在创建时生成，用户舰没有舰船 AI，`ShipSystemAIScript.advance` 对用户舰**永不调用**。需要用户侧"自动"行为时走 stats 脚本（`apply` 对用户舰照常回调）或战斗插件，不能依赖 AI 脚本。
11. **AI 决策必须节流**。`ShipSystemAIScript.advance` 每帧被调用，直接做全战场扫描每帧都会执行；成熟实例与原版内置 AI 都在 0.2–0.5 秒粒度决策（如 `IntervalUtil` 配 `0.2f–0.3f` 或 `0.25f–0.5f` 区间）。违反的可观察结果：行为抖动与不必要的性能开销（无报错）。

### 优先级：aiHints 威胁评估

`.system` 的 `aiHints` 段（`threatRange` / `threatAmount` / `threatArc` / `threatAngle`）是"系统威胁"影响**敌方** AI 的通道；`ShipSystemSpecAPI` 解析这些字段。

原版 `emp.system` 用 `threatRange: 600, threatAmount: 4, threatArc: 360`。

威胁评估器评估敌舰时，满足以下条件就把 `threatAmount` 计入威胁分并扩展评估射程：

- 该系统 `getCooldownRemaining() < 3.0F`（接近就绪）且 `threatAmount > 0`
- 距离在 `threatRange`（经 `getSystemRangeBonus` 修正）内
- 方向落在 `threatAngle ± threatArc` 里

可观察结果：给系统写大 `threatAmount` 后，敌方 AI 会把这艘舰当作高威胁目标（更早规避、更谨慎接近）；`threatAmount: 0`（默认）则系统不参与威胁评估。

## 完整最小例：目标锁定系统

示例系统 `GearIn_targetingsystem`：`STAT_MOD` 类型、`aiType: CUSTOM` ，stats 脚本与 AI 脚本是**同一个类** `data.scripts.gearin.shipsystems.GearInTargetingSystem` 的两个实例。

行为基线：锁定有效期内（敌方、存活、非相位、在有效射程内）`IN`/`ACTIVE` 段按 `effectLevel` 施加最大速度倍率 `1.5`；锁定失效立即撤销。

AI 舰船每 0.2–0.3 秒决策一次：验证目标 → 写入 `TARGET_FOR_SHIP_SYSTEM` flag（激活期间持续刷新）→ `ship.useSystem()`。

在既有 MOD 布局与战术系统数据面之上新增一个类文件，包与类名遵循命名约定（`data.scripts.gearin.shipsystems`、`GearIn<Subject>`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── shipsystems\
│       ├── GearIn_targetingsystem.system   # 本节数据接入片段（见下）
│       └── ship_systems.csv                # 注册行（见下）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin                 # 既有入口类；本节不改动
│       ├── GearInGlobalAccess              # 既有全局访问类；本节只消费
│       └── shipsystems\
│           ├── GearInBurstDriveSystem      # 既有系统类；本节只消费
│           ├── GearInResistFieldSystem     # 既有系统类；本节只消费
│           └── GearInTargetingSystem       # 本节新增（stats 脚本 + AI 脚本同名类）
└── jars\
```

### GearInTargetingSystem

```java
package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战术系统运行态——Stats 脚本、状态机、目标锁定与系统 AI 的完整流程。
 *
 * 一个类身兼两个角色，两个角色由数据绑定各自实例化一次：
 * 1. Stats 脚本角色（ShipSystemStatsScriptAdvanced）：.system 的 statsScript 字段挂载。
 *    实例在舰船部署时由系统运行时对象构造器创建一次（spec.getStatsScript()），
 *    apply 只在 effectLevel &gt; 0（或 runScriptWhileIdle）期间每帧调用，
 *    unapply 在 effectLevel 归零边界调用一次；COOLDOWN/IDLE 两态脚本不可见。
 *    引擎传入的 id 是 "系统id effect"（系统运行时对象.getStatModId），modify 与 unmodify 必须用同一把钥匙。
 * 2. AI 角色（ShipSystemAIScript）：.system 的 aiScript 字段挂载，aiType 必须为 CUSTOM。
 *    实例在舰船 AI 创建时由 createSystemAI 实例化一次（spec.getAIScript()），
 *    init 缓存 ship/system/flags/engine，advance 每帧被舰船 AI 调用，
 *    参数 target 是舰船 AI 的当前目标（不可见时为 null）。
 *
 * 关键纪律：
 * - statsScript 与 aiScript 是同名类的两个独立实例（脚本类加载器.loadScriptInstance
 *   每次 newInstance），实例字段互不共享；共享逻辑必须走静态方法（本类全部目标逻辑都是静态的）；
 * - TARGET_FOR_SHIP_SYSTEM 由舰船 AI 每帧在调用系统 AI 前写入（时长 1 秒），
 *   本类 AI 角色也会把验证过的目标写进去；flag 有持续时间，消费前必须再验证一次；
 * - 玩家舰没有舰船 AI，AI 角色不会运行，flag 也无人写入——玩家侧目标回退
 *   到 ship.getShipTarget()（瞄准镜锁定的目标）；
 * - 目标 ShipAPI 属于本场战斗实体，禁止跨战斗缓存（与战斗实体同一纪律）。
 */
public class GearInTargetingSystem extends BaseShipSystemScript implements ShipSystemAIScript {

	private static final Logger LOG = Global.getLogger(GearInTargetingSystem.class);

	/** 系统标称射程（经 systemRangeBonus 修正前的基础值）。 */
	public static final float RANGE = 800f;
	/** 锁定有效时满 effectLevel 下的最大速度倍率（1.5 倍）。 */
	public static final float SPEED_MULT = 1.5f;
	/** AI 写入 TARGET_FOR_SHIP_SYSTEM 的 flag 持续时间（秒），与原版舰船 AI 的 1.0 秒一致。 */
	public static final float AI_TARGET_FLAG_DURATION = 1.0f;
	/** AI 决策节流区间（0.2–0.3 秒一次决策，成熟实例同款）。 */
	private static final float AI_DECISION_MIN = 0.2f;
	private static final float AI_DECISION_MAX = 0.3f;

	// ---------- AI 角色字段（ShipSystemAIScript 实例独有） ----------
	private CombatEngineAPI engine;
	private ShipAPI aiShip;
	private ShipSystemAPI system;
	private ShipwideAIFlags aiFlags;
	private final IntervalUtil decisionInterval = new IntervalUtil(AI_DECISION_MIN, AI_DECISION_MAX);
	private SystemState lastAISystemState = null;

	// ---------- Stats 角色字段（ShipSystemStatsScript 实例独有） ----------
	private State lastApplyState = null;

	// ================= 共享目标逻辑（静态，两个角色共用同一语义） =================

	/** 有效射程：基础 RANGE 经 systemRangeBonus 修正（ShipSystemSpecAPI.getRange 同式）。 */
	public static float getRange(ShipAPI source) {
		if (source == null) {
			return RANGE;
		}
		return source.getMutableStats().getSystemRangeBonus().computeEffective(RANGE);
	}

	/** 目标合法性：敌方、存活、非残骸/相位/转移穿梭机、且在有效射程内。 */
	public static boolean isValidTarget(ShipAPI source, ShipAPI target) {
		if (source == null || target == null) {
			return false;
		}
		if (target == source) {
			return false;
		}
		if (!target.isAlive()) {
			return false;
		}
		if (target.isHulk()) {
			return false;
		}
		if (target.isPhased()) {
			return false;
		}
		if (target.isShuttlePod()) {
			return false;
		}
		if (target.getOwner() == source.getOwner()) {
			return false;
		}
		Vector2f delta = Vector2f.sub(target.getLocation(), source.getLocation(), new Vector2f());
		return delta.length() <= getRange(source);
	}

	/**
	 * 读取 AI 写入的目标：TARGET_FOR_SHIP_SYSTEM 的 custom 数据。
	 * 玩家舰没有舰船 AI，本类 AI 角色也不会运行，flag 无人写入——返回 null。
	 */
	public static ShipAPI readAITarget(ShipAPI source) {
		if (source == null || source.getShipAI() == null) {
			return null;
		}
		ShipwideAIFlags flags = source.getAIFlags();
		if (flags == null || !flags.hasFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM)) {
			return null;
		}
		Object data = flags.getCustom(AIFlags.TARGET_FOR_SHIP_SYSTEM);
		return data instanceof ShipAPI ? (ShipAPI) data : null;
	}

	/** 把验证通过的目标写入 TARGET_FOR_SHIP_SYSTEM（AI 角色调用 ship.useSystem() 前使用）。 */
	public static boolean writeAITarget(ShipAPI source, ShipAPI target) {
		if (source == null || source.getShipAI() == null) {
			return false;
		}
		ShipwideAIFlags flags = source.getAIFlags();
		if (flags == null || !isValidTarget(source, target)) {
			return false;
		}
		flags.setFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM, AI_TARGET_FLAG_DURATION, target);
		return true;
	}

	/** 目标解析：AI flag 优先，回退舰船当前目标（getShipTarget()），全部经过再验证。 */
	public static ShipAPI pickTarget(ShipAPI source) {
		if (source == null) {
			return null;
		}
		ShipAPI target = readAITarget(source);
		if (isValidTarget(source, target)) {
			return target;
		}
		target = source.getShipTarget();
		if (isValidTarget(source, target)) {
			return target;
		}
		return null;
	}

	// ================= Stats 脚本角色（ShipSystemStatsScriptAdvanced） =================

	/**
	 * IN/ACTIVE 期间每帧调用：锁定有效时按 effectLevel 施加最大速度倍率，
	 * 锁定失效或进入 OUT 立即撤销——目标是"启动侧再验证，验证不过不加成"。
	 */
	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		ShipAPI ship = (ShipAPI) stats.getEntity();
		if (ship == null) {
			return;
		}
		if (lastApplyState != state) {
			LOG.info("GearIn targeting stats: state " + lastApplyState + " -> " + state
					+ " effectLevel=" + effectLevel);
			lastApplyState = state;
		}
		boolean locked = isValidTarget(ship, pickTarget(ship));
		if (state == ShipSystemStatsScript.State.OUT || !locked) {
			stats.getMaxSpeed().unmodify(id);
		} else {
			stats.getMaxSpeed().modifyMult(id, 1f + (SPEED_MULT - 1f) * effectLevel);
		}
	}

	/** effectLevel 归零边界（进入 COOLDOWN/IDLE、舰船撤退或解体）由引擎调用一次。 */
	@Override
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().unmodify(id);
		LOG.info("GearIn targeting stats: unapply effect (id=" + id + ")");
	}

	/** 仅玩家舰：IN/ACTIVE 期间显示目标锁定状态（无目标时按减益样式显示）。 */
	@Override
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index != 0) {
			return null;
		}
		if (state != State.IN && state != State.ACTIVE) {
			return null;
		}
		CombatEngineAPI engine = Global.getCombatEngine();
		ShipAPI player = engine == null ? null : engine.getPlayerShip();
		boolean locked = player != null && isValidTarget(player, pickTarget(player));
		return new StatusData(locked ? "target locked" : "no target", !locked);
	}

	/** 可用性许可：激活中始终可用；待机时必须有有效锁定目标，否则按下 U 不激活。 */
	@Override
	public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
		if (system.isActive()) {
			return true;
		}
		return isValidTarget(ship, pickTarget(ship));
	}

	/** 信息栏文字：返回 null 时 UI 使用默认 "READY" / "ACTIVE"。 */
	@Override
	public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
		if (system.isOutOfAmmo()) {
			return null;
		}
		if (system.getState() != SystemState.IDLE) {
			return null;
		}
		return isValidTarget(ship, pickTarget(ship)) ? "READY" : "NO TARGET";
	}

	// ================= AI 脚本角色（ShipSystemAIScript） =================

	/** AI 实例创建时调用一次：缓存引用并随机化决策节流相位。 */
	@Override
	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
		this.aiShip = ship;
		this.system = system;
		this.aiFlags = flags;
		this.engine = engine;
		this.decisionInterval.randomize();
		LOG.info("GearIn targeting AI init: ship=" + ship.getHullSpec().getHullId()
				+ " system=" + system.getId());
	}

	/**
	 * 每帧被舰船 AI 调用：守卫链（暂停/实体/存活/CR/冷却/弹药）→ 决策节流 →
	 * 目标补全与再验证 → 写 flag（激活期间每 0.2–0.3 秒刷新，flag 只有 1 秒寿命）→
	 * 过载/高幅能守卫 → ship.useSystem()。
	 */
	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		if (engine.isPaused()) {
			return; // 暂停守卫：暂停时 AI 决策不推进
		}
		logStateTransition();

		if (!engine.isEntityInPlay(aiShip) || !aiShip.isAlive()) {
			return;
		}
		if (aiShip.getCurrentCR() <= 0f) {
			return;
		}
		if (system.getCooldownRemaining() > 0f) {
			return; // 冷却中（COOLDOWN 状态）
		}
		if (system.isOutOfAmmo()) {
			return;
		}

		decisionInterval.advance(amount);
		if (!decisionInterval.intervalElapsed()) {
			return; // 决策节流
		}

		// 目标补全与再验证：参数 target 优先（舰船 AI 传入，不可见时为 null），回退舰船当前目标
		ShipAPI lock = target;
		if (lock == null) {
			lock = aiShip.getShipTarget();
		}
		if (!isValidTarget(aiShip, lock)) {
			clearAITarget();
			return;
		}
		writeAITarget(aiShip, lock);

		if (system.isActive()) {
			return; // 激活中：只刷新锁定（flag 1 秒过期，不刷新 ACTIVE 后半段会丢锁），不重复激活
		}
		if (aiShip.getFluxTracker().isOverloadedOrVenting()) {
			return;
		}
		if (aiShip.getFluxLevel() > 0.9f) {
			return; // 高幅能不启动
		}
		LOG.info("GearIn targeting AI: lock " + lock.getHullSpec().getHullId()
				+ " range=" + getRange(aiShip));
		aiShip.useSystem(); // Ship.useSystem() -> giveCommand(USE_SYSTEM)（玩家按 U 与 AI 同一入口）
	}

	/** 清除过期的目标 flag（目标失效后下一轮决策不再复用旧锁）。 */
	private void clearAITarget() {
		if (aiFlags != null) {
			aiFlags.removeFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM);
		}
	}

	/** AI 视角的运行时状态机跳变日志：完整五态（含 COOLDOWN/IDLE，Stats 角色看不到这两态）。 */
	private void logStateTransition() {
		if (system == null) {
			return;
		}
		SystemState current = system.getState();
		if (current != lastAISystemState) {
			LOG.info("GearIn targeting AI: state " + lastAISystemState + " -> " + current
					+ " effectLevel=" + system.getEffectLevel()
					+ " cooldownRemaining=" + system.getCooldownRemaining());
			lastAISystemState = current;
		}
	}
}

```

对应关系：

- 静态目标逻辑（`getRange` / `isValidTarget` / `readAITarget` / `writeAITarget` / `pickTarget`）：两个角色共享的同一语义——AI 写入与 stats 消费走同一套合法性检查（规则 6、7），"AI 与 Stats 各自复制过滤条件"是高频陷阱。
- `readAITarget` 的 `source.getShipAI() == null` 守卫：用户舰无 AI，flag 无人写入（规则 6 的用户侧事实）；`pickTarget` 回退 `getShipTarget()`（通道 2）。
- `apply`：`(ShipAPI) stats.getEntity()` 取舰船（战斗期非 null）；`state == OUT || !locked` 时 `unmodify(id)`，否则 `modifyMult(id, 1 + 0.5 * effectLevel)`—— "目标验证不过就不加成"。
  - `lastApplyState` 状态跳变日志把 `IN → ACTIVE → OUT` 变成日志证明 （规则 4 的可见性边界：`COOLDOWN`/`IDLE` 在 stats 侧看不到，由 AI 侧补全）。
- `unapply`：撤销同一把钥匙并打日志（引擎传入的 `id` = `GearIn_targetingsystem effect`，规则 2）。
- `isUsable` / `getInfoText`：`isUsable` 返回 false 时按 U 不激活（规则 1）；`getInfoText` 返回 null 时 UI 用默认 `READY` /`ACTIVE`（`ShipSystemStatsScriptAdvanced` javadoc 原文）。
- AI 角色 `init`：缓存引用并 `decisionInterval.randomize()`（规则 11 的节流相位随机化）。
- AI 角色 `advance`：守卫链（暂停 → 实体存在 → 存活 → CR → 冷却 → 弹药 → 节流 → 目标再验证 → 写 flag → 激活中只刷新 → 过载/高幅能 → `useSystem`），顺序是成熟实例的标准做法；`system.isActive()` 分支只刷新 flag 不重复激活（规则 6 的"持续刷新"）。
- `logStateTransition`：AI 视角的完整五态日志（`IDLE → IN → ACTIVE → OUT → COOLDOWN → IDLE`）——这是 stats 脚本看不见的两态（`COOLDOWN`/`IDLE`）的唯一脚本侧观察点。
- `writeAITarget` 写入时长为 `1.0f`（与舰船 AI 的覆写一致），`clearAITarget` 在目标失效时清 flag。

### 数据接入片段（工程片段——本单元不创建数据文件）

```json
// data\shipsystems\GearIn_targetingsystem.system（片段）
{
    "id": "GearIn_targetingsystem",
    "type": "STAT_MOD",
    "aiType": "CUSTOM",
    "statsScript": "data.scripts.gearin.shipsystems.GearInTargetingSystem",
    "aiScript": "data.scripts.gearin.shipsystems.GearInTargetingSystem",
    "range": 800,
    "useSound": "system_targeting_feed",
    "deactivateSound": "gun_out_of_ammo",
    "aiHints": {
        "threatRange": 800,
        "threatAmount": 1,
        "threatArc": 360,
        "threatAngle": 0
    }
}
```

```csv {2}
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
目标锁定,GearIn_targetingsystem,1,,,,,,,,,0.5,4,0.5,3,,,,,,,,,,,offensive,graphics/icons/hullsys/targeting_feed.png
```

::: warning 测试舰挂载要还原
挂载到测试舰（C02-S03 示例行的 `system id` 是 `GearIn_burstdrive`；接入本节系统后要恢复原状，改回即可）。
:::

```csv {5}
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
工匠,GearIn_artizan,驱逐舰,未知,GearIn_targetingsystem,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,5,12,360,0.25,10,10,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
```

数据字段（CSV 28 列，数据面单元速查表已有全部列）：`flux/second=1`（`IN`/`ACTIVE` 段每秒幅能）。

`charge up=0.5`、`active=4`、`down=0.5`、`cooldown=3`、`tags=offensive`；

`.system` 的 `range` 被 `getSystemRangeBonus` 修正后是本例"有效射程"的 base（`ShipSystemSpecAPI` 的 `getRange`）。

`aiHints` 让敌方 AI 把就绪中的本系统计入威胁（`threatAmount=1`、`threatRange=800`）。

构建命令（在 `Starsector\mods\GearIndustry\` 执行）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/shipsystems/GearInTargetingSystem.class`（可用 `jar tf` 核对）。本节源码与 fixture 中的同名类逐字一致，fixture 编译验收命令见「预期结果」。

### 验证方案（基于本例）

前置数据：

- 既有 MOD 布局（C06-S01）、战术系统数据面（C02-S03）
- 本节数据接入片段（`GearIn_targetingsystem.system`、`ship_systems.csv` 注册行、`GearIn_artizan` 的 `system id` 挂载）
- `GearInTargetingSystem` 类实现

操作步骤：

1. **fixture 编译**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC07s08Java --no-daemon`，确认以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/shipsystems/GearInTargetingSystem.class`。
3. **数据接入**：启动游戏，日志核对 `Loading ship system [data/shipsystems/GearIn_targetingsystem.system]` 与 `Applying data from ship_systems.csv to [GearIn_targetingsystem]`
   - 没有与 `GearIn_targetingsystem` 相关的 `ERROR`。
4. **用户侧**：进入 `GearIn_test` 战役，无目标按 U 确认无反应（`isUsable` 返回 false）；右键锁定敌舰后按 U，核对四行 stats 状态日志与 `unapply effect (id=GearIn_targetingsystem effect)`，观察 1.5 倍速度与 `target locked` 状态文字；击毁目标确认加成立即撤销。
5. **AI 侧**：把挂载该系统的舰船放入敌方，进入战斗后核对 `GearIn targeting AI init`、`lock` 日志与五态状态机日志；观察敌方舰船实际使用系统；击毁其目标后确认不再出现 `lock`。
6. **暂停**：战斗内按空格暂停约 10 秒，确认 AI 决策与状态机日志停更，恢复后继续。
7. **故障注入 1**：把 `.system` 的 `aiType` 从 `CUSTOM` 改成 `NONE`——AI 舰船不再主动使用系统（无 `lock` 日志），用户侧行为不变；改回后恢复（对应故障 2 的定位练习）。
8. **故障注入 2**：把 `advance` 里激活中只刷新 flag 的分支注释掉（激活后不再写 flag）——`ACTIVE` 段约 1 秒后加成消失（若 stats 侧不回退 `getShipTarget()` 则必现，对应故障 3 触发二）；改回后确认恢复。

通过信号：操作 1–6 全部符合；操作 7、8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures` 构建配置等）做过修改。

::: tip 日志核对技巧
在 `Starsector/starsector-core/starsector.log` 中按 `GearIn targeting` 前缀检索即可定位本示例的全部输出行（stats 侧 `GearIn targeting stats:`、AI 侧 `GearIn targeting AI:` 分开）；出现 `ERROR` 时优先查看 `gear_in` 包相关的堆栈。
:::

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC07s08Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/shipsystems/GearInTargetingSystem.class`。
3. **数据接入**：按数据接入片段接入后启动游戏，日志出现 `Loading ship system [data/shipsystems/GearIn_targetingsystem.system]` 与 `Applying data from ship_systems.csv to [GearIn_targetingsystem]`
   - 没有与 `GearIn_targetingsystem` 相关的 `ERROR`。
4. **用户侧手动激活（`GearIn_test` 战役或模拟战，`GearIn_artizan` 已挂载）**：无目标时按 U——**没有任何反应**（`isUsable` 返回 false，规则 1；日志无状态变化）；连续空按约 2 秒后出现失败音。鼠标右键锁定一艘敌舰（`getShipTarget()` 非 null）后按 U——系统正常进入 `IN`，日志出现
```text
GearIn targeting stats: state null -> IN effectLevel=0.0 GearIn targeting stats: state IN -> ACTIVE effectLevel=1.0 GearIn targeting stats: state ACTIVE -> OUT effectLevel=1.0 GearIn targeting stats: unapply effect (id=GearIn_targetingsystem effect)
```
期间用户舰最大速度约为基础 1.5 倍（`GearIn_artizan` 基础 80 → 120），左下角状态栏显示 `target locked`（绿色）。目标被击毁或脱离射程后，加成**立即**撤销（`unmodify`），状态文字变 `no target`（红色样式）。
5. **AI 侧自动激活**：把挂载了该系统的舰船放进敌方（生涯遭遇战或模拟战对手），其 AI 进入用户视野（`target` 非 null）且目标有效时，日志出现
```text
GearIn targeting AI init: ship=<敌方hullId> system=GearIn_targetingsystem GearIn targeting AI: lock <目标hullId> range=800.0
```
随后敌方舰船实际使用系统（速度提升）；`logStateTransition` 输出完整五态：
```text
GearIn targeting AI: state null -> IDLE ... GearIn targeting AI: state IDLE -> IN ... GearIn targeting AI: state IN -> ACTIVE ... GearIn targeting AI: state ACTIVE -> OUT ... GearIn targeting AI: state OUT -> COOLDOWN cooldownRemaining=... GearIn targeting AI: state COOLDOWN -> IDLE ...
```
冷却结束后若目标仍有效，重复该循环。激活期间每 0.2–0.3 秒 `writeAITarget` 刷新 flag（ACTIVE 全程锁定不断）。
6. **目标过期**：AI 的目标被击毁后，不再出现 `lock` 日志（`clearAITarget` 生效）；目标复活/换目标后恢复。
7. **暂停语义**：按空格暂停，暂停期间 AI 决策日志与状态机日志停更；恢复后继续（与前置单元的时间语义同规）。
8. **状态栏与信息文字**：用户舰在 `IDLE` 态把鼠标悬停系统图标，`getInfoText` 显示 `NO TARGET`（有锁定目标时 `READY`）；`IN`/`ACTIVE` 段状态栏显示锁定状态。

静态自查：`GearInTargetingSystem` 与 fixture 逐字一致（包括 import 顺序与注释）。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.shipsystems`、类 `GearInTargetingSystem`、系统 ID `GearIn_targetingsystem`）。

## 实例驱动故障分析

1. **部署期 `Error creating AI of type [...] for ship system [...]`**。触发：`aiType` 缺失、拼错，或 `aiType: CUSTOM` 但没有 `aiScript`（或类名错）。
   - 可观察：不是按键时，而是**舰船部署生成 AI 的瞬间**抛 `RuntimeException`（`createSystemAI` 的 catch 包装），该舰船无法进入战斗；类名错时日志是 `Problem loading class [...]`
   - 修正：`aiType: "CUSTOM"` + `aiScript` 完整类名（与包名、路径三者一致），规则 8。
2. **AI 从不使用系统（无 `lock` 日志）**。触发：守卫链某环永假——最常见的是目标不可见（战争迷雾中 `target` 为 null 且 `getShipTarget()` 也为 null）、目标被 `isValidTarget` 拒绝（相位、残骸、同侧、超出射程）、CR 为 0、 `isOutOfAmmo()`、冷却中。
   - 可观察：`GearIn targeting AI init` 有、`lock` 无，系统状态机日志停在 `IDLE`
   - 修正：按守卫链顺序逐环核对（实体 → 存活 → CR → 冷却 → 弹药 → 节流 → 目标），先确认目标确实存在且合法。
3. **AI 激活了但没有加成（或加成中途消失）**。触发一：stats 侧读 flag 但没判 `getShipAI() == null`，用户舰上读不到任何值——可观察：用户侧手动激活时锁定永远判定为 false。
   - 触发二：AI 只在激活前写一次 flag，`IN`（0.5 秒）+ `ACTIVE` 段超过 flag 的 1 秒寿命后 `readAITarget` 返回 null，且 stats 侧不回退 `getShipTarget()`——可观察：`ACTIVE` 前段有加成、约 1 秒后加成消失（速度回落），状态文字闪断
   - 修正：`pickTarget` 先 flag 后 `getShipTarget()` 回退（规则 6）；AI 激活期间持续刷新 flag（本节示例的 `system.isActive()` 刷新分支）。
4. **用户按 U 无反应**。触发：`isUsable` 返回 false（无有效锁定目标——注意 `readAITarget` 对用户舰恒返回 null，判定完全依赖 `getShipTarget()`），或幅能/弹药/冷却不足。
   - 可观察：**没有任何状态变化、没有报错**；连续空按约 2 秒后听到失败音（`failedToActivateDueToFlux`/`Ammo` 的 2 秒门槛）
   - 修正：按规则 1 核对 `isUsable`；用 `getInfoText` 让 UI 提前说明原因（`NO TARGET`）。
5. **幅能不足提前结束（`ACTIVE` 段缺失）**。触发：`flux/second` 超过舰船净耗散能力。
   - 可观察：`IN` 后状态机**跳过 `ACTIVE` 直接进 `OUT`**（扣幅能失败且可关闭 → 立即转 `OUT`），日志 `state IN -> ACTIVE` 之后紧跟 `state ACTIVE -> OUT`（`ACTIVE` 只存在一帧或数帧）
   - 修正：核对 `flux/second` 与舰船 `flux dissipation`；给系统挂耗散加成。
6. **效果残留**。触发：`apply` 里 `modifyMult` 的钥匙与 `unapply` 的 `unmodify` 不一致（比如自己拼了别的 id），或某分支只写不撤。
   - 可观察：系统结束后速度倍率仍在（无报错，与 modifier ID 稳定性故障同族）
   - 修正：一律用引擎传入的 `id`（`系统id + " effect"`），`OUT` 分支与 `unapply` 都 `unmodify(id)`（规则 2）。
7. **跨战斗缓存目标引用**。触发：把目标 `ShipAPI` 存进静态字段或 `ship.setCustomData`，下一场战斗继续用。
   - 可观察：第二场战斗起 `isAlive()` / `isEntityInPlay()` 恒为 false（实体委托给**当前**引擎判定，与实体判据同规），锁定判定全部失败，无报错
   - 修正：目标只在本帧决策/回调内消费；AI 实例与 stats 实例每场战斗重建，字段随实例自然重置（规则 9 的实例化事实），**禁止**用 static 或跨战斗 customData 传目标。
8. **决策节流缺失导致行为抖动**。触发：`advance` 里不做 `IntervalUtil` 节流，每帧全量扫描与决策。可观察：AI 行为高频抖动（每帧开关尝试）、日志刷屏；多舰同挂时帧时间上升。修正：0.2–0.3 秒节流（规则 11）。

## 总结

- **运行时 stats 访问器分七组**：
  - 状态（`getState` + 四个布尔视图）、效果（`getEffectLevel`）
  - 冷却（`getCooldown` / `getCooldownRemaining`）、弹药（`getAmmo` / `getAmmoPerSecond` / `getAmmoReloadProgress` / `isOutOfAmmo`）
  - 幅能（`getFluxPerUse` / `getFluxPerSecond`）、激活控制（`canBeActivated` / `deactivate` / `forceState`）、目标（`getTargetLoc`）
  - 四个 bonus 族（`getSystemRangeBonus` / `getSystemCooldownBonus` / `getSystemUsesBonus` / `getSystemRegenBonus`）修正 spec 原始值
  - 系统创建时一次性把这些值写回充能记录，运行期改动这几项不反映本场战斗。
- **五态推进规则**：
  - `IDLE` 激活检查（可激活 → 弹药 → 扣幅能 → 扣弹药）
  - `IN`（满 `charge up` 转 `ACTIVE`）
  - `ACTIVE`（非开关式按 `active` 结束；开关式 `active>0` 同样自动结束、`active=0` 无限保持直到手动关闭；每帧幅能不足且可关闭立即提前转 `OUT`）
  - `OUT`（满 `down` 转 `COOLDOWN`）
  - `COOLDOWN`（满 `cooldown` 回 `IDLE`）
  - `effectLevel`：`IN` 爬升、 `ACTIVE` 恒 1、`OUT` 回落、`IDLE`/`COOLDOWN` 为 0。
- **stats 脚本可见性边界**：`apply` 只在 `effectLevel > 0` 或 `runScriptWhileIdle` 时调用，`COOLDOWN`/`IDLE` 不可见；`unapply` 在归零边界调用一次；`getStatusData` 仅用户舰。修改钥匙 = `系统id + " effect"`，`apply`/`unapply` 必须一致。
- **目标三通道**：`getTargetLoc()`（用户右键坐标）、`getShipTarget()`（舰船当前目标，用户与 AI 通用，首选回退）、`TARGET_FOR_SHIP_SYSTEM` flag（舰船 AI 每帧写入 1.0 秒寿命；非 AI 舰无人写入、永不过期）。flag 消费前必须再验证；AI 激活期间必须持续刷新。
- **系统 AI 接入**：`aiType: CUSTOM` + `aiScript` 是唯一消费 `ShipSystemAIScript` 的入口（`NONE` 无 AI、缺失/不配对部署期抛 `Error creating AI of type [...]`）
  - statsScript 与 aiScript 是同名类两个实例，共享逻辑必须静态；系统 AI 只跑在 AI 舰上；决策必须节流
  - `aiHints` 的 `threat*` 字段参与敌方威胁评估（就绪中的系统才算威胁，`cooldownRemaining < 3.0F`）

逻辑上看，运行态主线自然延伸到战斗视觉与特效层：动画、开火与命中特效消费的正是状态机的时间轴与目标判定结果，五态模型与目标纪律在那条链路上会反复出现；数据面字段速查与 modifier 纪律的完整细节，仍由对应前置单元持有。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipSystemAPI`：运行时系统实例的访问器全集（`SystemState` 五态、效果 / 冷却 / 弹药 / 幅能、激活控制与目标坐标）。
- `com.fs.starfarer.api.combat.ShipSystemSpecAPI`：系统数据 spec，`getStatsScript()` / `getAIScript()` 按类名实例化脚本，`getRange(stats)` 等取值方法。
- `com.fs.starfarer.api.combat.ShipSystemAIScript`：系统 AI 接口，`init` / `advance` 双方法。
- `com.fs.starfarer.api.combat.ShipwideAIFlags`：AI 短期 flag 容器，`TARGET_FOR_SHIP_SYSTEM` 与 `FLAG_DURATION` 寿命语义。
- `com.fs.starfarer.api.combat.ShipAPI`：舰船实体入口（`getSystem` / `getShipTarget` / `getAIFlags` / `useSystem` / `getShipAI`）。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：四个系统 bonus 族（`getSystemRangeBonus` / `getSystemCooldownBonus` / `getSystemUsesBonus` / `getSystemRegenBonus`）与 `getMaxSpeed`。
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：效果脚本接口，`State` 五态、`apply` / `unapply` / `getStatusData`。
- `com.fs.starfarer.api.plugins.ShipSystemStatsScriptAdvanced`：高级扩展，`isUsable` / `getInfoText`。
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：官方基类，覆写方法默认返回 `-1` / `null`。
- `com.fs.starfarer.api.util.IntervalUtil`：AI 决策节流计时器（`randomize` / `advance` / `intervalElapsed`）。
- `data/shipsystems/ship_systems.csv`：系统注册 28 列表头与 Fortress Shield（开关式 `active` 留空）、EMP Emitter、Damper Field、Targeting Feed 等原版行。
- `data/shipsystems/targetingfeed.system` / `data/shipsystems/emp.system`：原版 `.system` 实例（`aiType` 枚举与 `aiHints` 的 `threat*` 字段参考）。
- `data/shipsystems/GearIn_targetingsystem.system`（示例 MOD 内）：本节数据接入片段（`aiType: CUSTOM` + 双脚本类名）。
- `tutorial-fixtures/units/C07-S08`：本节示例 fixture（类与页面代码块逐字一致；`compileC07s08Java` 编译任务）。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录（`gradle jar` 构建命令执行位置）。
- `Starsector/starsector-core/starsector.log`：游戏运行日志（验证方案的日志核对位置）。
