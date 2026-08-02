# 战斗引擎：状态读取、遍历与时间语义

本节把战斗域的"读取"侧讲透：`CombatEngineAPI` 的状态访问器（战斗上下文、玩家舰、战斗结果）、`CombatFleetManagerAPI` 的舰队名单与已部署成员遍历，以及 `ShipAPI` / `CombatEntityAPI` 的实体状态过滤、战斗时间与暂停语义（含暂停计时的两种累计、当前帧 `dt`、时间倍率）。写侧 API（伤害、生成、粒子、音效）由既有页面负责，本节只在需要核对状态时消费。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 背出 RC8 `CombatEngineAPI` 的状态访问器分组（战斗上下文 / 玩家与实体 / 战斗结果 / 时间），并说出每个代表方法的返回类型与语义；
- 用 `getShips()` 正确遍历"在场"舰船，说出 `isAlive()` / `isShipAlive()` / `isEntityInPlay()` 的关系，以及为什么遍历**必须**过滤残骸；
- 用 `CombatFleetManagerAPI` 的五份名单（部署 / 后备 / 瘫痪 / 击毁 / 撤退）与 `getDeployedCopyDFM()` 读取两侧舰队的部署状态；
- 说出战斗时间的四种语义（不含暂停累计、含暂停累计、当前帧 `dt`、与敌接触时间），并解释暂停时 `advance()` 仍被调用而 `getElapsedInLastFrame()` 不为 0；
- 解释为什么 `getPlayerShip()` 永远非 null（用到时才创建的"Command Shuttle"假舰），以及正确的玩家部署判据；
- 从症状定位六类典型故障：残骸统计错误、暂停期间状态累计、玩家舰判据失效、跨战斗缓存实体引用、两种累计时间选错、时间倍率二次缩放。

## 前置知识

以下既有知识是本节的前提：

- MOD 目录结构与 `starsector.log` 日志（C01-S01）：本节所有可观察结果都落在日志里。
- 战斗插件模型与每帧回调（C07-S01）：`addPlugin(...)` 注册、`EveryFrameCombatPlugin.advance` 每帧回调与暂停守卫——本节的示例插件建立在这个模型上。
- 战役（mission）与生涯（campaign）概念，以及 `GearIn_test` 测试战役（C03-S01）。
- 编译 JAR 链路（C06-S01）：本节示例编入同一个 `jars\GearIndustry.jar`。
- `Global` 生命周期——战斗引擎每场战斗结束销毁后立即重建、战斗外是非 null 空引擎、禁止跨战斗缓存引擎引用（C06-S03）：本节把同一条纪律延伸到引擎返回的舰船与列表。
- `MutableStat`——`getTimeMult()` 的返回类型（C07-S03）：本节只消费 `getModifiedValue()`，完整讲解见该单元。


## 文件与 API 链路

本节涉及的接口都在 `com.fs.starfarer.api.combat`（`FleetSide` 在 `com.fs.starfarer.api.mission`）：

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `CombatEngineAPI` | 状态 / 时间 / 遍历入口 | `Global.getCombatEngine()` 取得（C06-S03 讲生命周期）；本节讲状态读取；写侧（音效与粒子）由后续节消费 |
| `CombatFleetManagerAPI` | 舰队名单与部署状态 | `engine.getFleetManager(FleetSide)` / `getFleetManager(int owner)` 取得 |
| `ShipAPI` / `CombatEntityAPI` | 单舰状态访问器 | `getShips()` 与 `DeployedFleetMemberAPI.getShip()` 的返回元素 |
| `DeployedFleetMemberAPI` | 已部署成员（舰或战机联队）| `manager.getDeployedCopyDFM()` 的元素；`getMember()` 指向 `FleetMemberAPI`（完整讲解在 C08-S03）|
| `EveryFrameCombatPlugin` / `BaseEveryFrameCombatPlugin` | 每帧回调骨架 | 战斗插件模型（C07-S01） |
| `IntervalUtil` | 定期间隔触发（本节示例用它做周期日志）| `com.fs.starfarer.api.util` |
| `FleetSide` | `PLAYER` / `ENEMY` 两侧 | `getFleetManager(FleetSide)` |

`CombatEngineAPI` 状态访问器分组速查：

| 分组 | 成员 | 语义 |
|---|---|---|
| 战斗上下文 | `isInCampaign()` / `isInCampaignSim()` / `isSimulation()` / `isMission()` / `getMissionId()` | 这场战斗是哪一类：生涯遭遇战 / 生涯内模拟战 / 模拟战 / 战役；`getMissionId()` 非战役时返回 null |
| 玩家与实体 | `getPlayerShip()` / `isShipAlive(ShipAPI)` / `isEntityInPlay(CombatEntityAPI)` / `isInEngine(ShipAPI)` | 玩家舰与实体存活性（详见"玩家舰"与"实体存活性"）|
| 战斗结果 | `isCombatOver()` / `getWinningSideId()` / `getCombatNotOverFor()` | 战斗是否结束、胜者（详见"战斗结果"）|
| 时间 | `getTotalElapsedTime(boolean)` / `getElapsedInLastFrame()` / `getTimeMult()` / `getElapsedInContactWithEnemy()` / `isFleetsInContact()` / `isPaused()` / `setPaused(boolean)` | 战斗时间四种语义与暂停（详见"时间语义"）|

## 战斗上下文与玩家舰

### 战斗上下文

- `isInCampaign()`：这场战斗发生在生涯里（含生涯内的模拟战）返回 `true`；战役（mission）与主菜单模拟战返回 `false`（API javadoc："true if this battle is inside the campaign, false otherwise (i.e., mission or simulation, including in-campaign simulations)"）。
- `isInCampaignSim()` / `isSimulation()` / `isMission()`：三层细分——生涯内模拟战只有 `isInCampaignSim()` 为 `true`；主菜单模拟战 `isSimulation()` 为 `true`；战役 `isMission()` 为 `true` 且 `getMissionId()` 返回战役 ID（如 `GearIn_test` 测试战役）。
- 用途：同一段逻辑在不同战斗类型下行为不同时（例如只在生涯遭遇战里开启某机制），先读这几个标志，不要在 `init` 里猜。

### 玩家舰：getPlayerShip() 永远非 null

RC8 中 `CombatEngineAPI.getPlayerShip()` 在玩家舰尚未设置时**不会**返回 null，而是用到时才创建一艘"Command Shuttle"假舰（`shuttlepod_xfer` 装配的克隆，owner 0）并返回。

可观察结果：生涯战斗的部署阶段（玩家还没部署任何舰船）`getPlayerShip()` 返回 `shuttlepod_xfer` 假舰；战役（mission）开局即设置玩家舰，通常观察不到假舰。

由此得到两条必须遵守的规则：

1. **禁止**用 `engine.getPlayerShip() == null` 判断"玩家未部署 / 不在战斗"。该表达式在战斗内永远为 `false`，依赖它的分支**永远不执行**（静默失效，无报错）。判断部署状态应读 `engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy()` 是否非空；判断是否在战斗用 `Global.getCurrentState() == GameState.COMBAT`。
2. 拿到 `getPlayerShip()` 后**必须**核对 `isHulk()` / `isAlive()`（或 `playerShip` 的舰型），再把它当作"玩家控制的真舰"使用——部署阶段拿到的是假舰，对它调用写操作没有实际玩家意义。

### 战斗结果

- `isCombatOver()`：战斗结束标志置位**且**结束淡出动画播放完才返回 `true`。可观察结果：胜负判定到淡出完成之间有一个窗口，`isCombatOver()` 仍为 `false`。
- `getWinningSideId()`：API javadoc 原文 "0 = player, 1 = enemy, 2 = player allies, no player ships left."；胜负未定时返回 -1（初始默认值）。因此**禁止**把 `getWinningSideId() != -1` 当作"战斗已结束"判据——它代表的是"胜者侧"，结束判定必须用 `isCombatOver()`。
- `setCombatNotOverFor(float)` / `getCombatNotOverFor()`：把战斗强制延续一段时间（防止按败北条件提前收场）；结束判定对"败北"还要求战斗时间累计超过 30 秒——战斗开始 30 秒内不会因一方"被击败"而结束。

## 舰队与舰船遍历

### 引擎对象表：getShips()

`engine.getShips()` 返回引擎对象表里的全部 `ShipAPI`，它**不是**"存活舰船列表"：

- 残骸（`isHulk() == true`）仍在对象表里（引擎自身的推进循环遍历时也要跳过残骸）；
- 已标记移除、尚未执行移除的舰船也还在表里；
- 停在甲板（机库）里的战机**不在**对象表里，但 `isAlive()` 仍为 `true`。

实体存活性三个入口的 RC8 语义：

| 方法 | 语义 |
|---|---|
| `ship.isAlive()` | 与引擎侧 `engine.isShipAlive(ship)` 同一判定——非残骸、`getHitpoints() > 0`、且（仍在引擎对象表中或属于停在甲板的战机）。战机停驻时仍为 `true` |
| `engine.isShipAlive(ship)` | 同上，引擎侧入口（`null` 安全：传 null 返回 `false`）|
| `engine.isEntityInPlay(entity)` | 实体当前在战斗场上：已入对象表且未被标记移除（刚加入战斗、尚未入表的实体也算在场）——残骸若仍在对象表中，此方法为 `true`；停驻甲板的战机为 `false` |

::: caution 遍历必须过滤残骸
遍历 `getShips()` 时**必须**先用 `isAlive()` 过滤（需要含残骸的场景才用 `!isHulk()` 或 `isEntityInPlay()` 自行定义）。违反的可观察结果：把残骸数进"存活舰船"统计；对残骸执行写操作（补甲板、回血等）白白消耗；死亡当帧的舰船仍被处理一帧导致重复逻辑。`getShips()` + `isAlive()` 过滤是战斗遍历的标准做法。
:::

### 舰队管理器：五份名单

`engine.getFleetManager(FleetSide.PLAYER / ENEMY)` 与 `engine.getFleetManager(int owner)`（0 = 玩家侧、1 = 敌方侧）返回两侧的 `CombatFleetManagerAPI`。

注意：`getFleetManager(int)` 在战斗外的空引擎 / 已销毁的旧引擎上**不会**返回 null，而是新建一个空管理器返回——所以"拿不到管理器"同样不能靠判 null，而要依靠引擎生命周期主题给出的阶段判据。

管理器把舰队成员按状态分成五份名单，全部以快照副本（Copy）返回，修改副本不影响引擎：

| 方法 | 名单含义 |
|---|---|
| `getDeployedCopy()` | 当前已部署（在场）的成员 |
| `getReservesCopy()` | 尚未部署、还在后备的成员 |
| `getDisabledCopy()` | 瘫痪（CR 耗尽 / 无法行动）的已部署成员 |
| `getDestroyedCopy()` | 已击毁的成员 |
| `getRetreatedCopy()` | 已撤退的成员 |

遍历已部署成员用 `getDeployedCopyDFM()`（`List<DeployedFleetMemberAPI>`）：每个元素把"部署记录"与"场上实体"连起来——

- `getMember()` 是 `FleetMemberAPI`（生涯侧身份，完整讲解见生涯主题）
- `getShip()` 是场上 `ShipAPI`（战机联队返回联队长机）
- `isFighterWing()` / `isStationModule()` / `isAlly()` 是常用过滤

需要按成员定位场上实体时用 `getShipFor(FleetMemberAPI)`（未部署返回 null）与 `getDeployedFleetMember(ShipAPI)`。

遍历 `getDeployedCopyDFM()` 时跳过 `isFighterWing()` 与 `isStationModule()`、只处理主力舰，是已部署成员遍历的成熟做法。

::: caution 禁止跨战斗缓存实体引用
从名单（`getShips()` / `getDeployedCopyDFM()` 等）拿到的 `ShipAPI` / 列表**禁止**跨战斗缓存—— 战斗结束后引擎销毁重建，旧实体的 `isAlive()` 委托给**新**引擎实例判定，在新引擎里任何旧舰都不在对象表中，`isAlive()` / `isEntityInPlay()` 恒为 `false`；列表本身也只反映获取那一刻的状态。可观察结果：第二场战斗起缓存引用"数据还在但全部判定失效"，且无任何报错（与既有引擎引用禁令同一条纪律的实体侧）。
:::

## 时间语义

RC8 的战斗时间语义：

| 方法 | 语义 | 暂停时行为 |
|---|---|---|
| `getTotalElapsedTime(false)` | 累计战斗时间，**暂停不计** | 停止累计 |
| `getTotalElapsedTime(true)` | 累计真实经历时间，**含暂停**（暂停时也增长）| 继续累计 |
| `getElapsedInLastFrame()` | 当前帧的 `dt`；API javadoc 原文 "Does *not* return 0 if the game is paused; actually the *current* frame." | **非 0**（暂停时也不例外）|
| `getTimeMult()` | 引擎全局时间倍率（`MutableStat`，完整讲解见时间倍率主题）；每帧 `dt` 先乘 `getModifiedValue()` 再乘 settings 的 `combatSpeedMult`（默认 1f，见 `settings.json` 的 `combatSpeedMult` 项）才用于推进战斗 | — |
| `getElapsedInContactWithEnemy()` | 双方互见敌舰的累计时间（API javadoc "Time elapsed while both sides can see at least one enemy ship."）| 不累计 |
| `isPaused()` / `setPaused(boolean)` | 暂停标志（开 / 关）| — |

两个关键事实：

1. **暂停时 `advance()` 仍被调用**：暂停不中断战斗引擎对 `EveryFrameCombatPlugin` 的每帧回调——`advance()` 在暂停时照常被调用。**必须**在推进状态前检查 `engine.isPaused()`（C07-S01 已立此规）。违反的可观察结果：暂停期间自己的计时 / 状态照常累计，解除暂停后数值已偏移。
2. **插件收到的 `amount` 与 `getElapsedInLastFrame()` 是同一个数**：引擎按 `timeMult` 与 `combatSpeedMult` 缩放后得到本帧 `dt`，同时写入 `getElapsedInLastFrame()` 并作为 `amount` 传给每帧回调，暂停时也照常传入。默认两个倍率都是 1，`amount` 即真实帧长。做"按推进时间累计"直接用 `amount` 或 `getElapsedInLastFrame()`，**不要**再乘 `engine.getTimeMult().getModifiedValue()`—— 那会二次缩放，游戏内时间倍率一改（慢动作等）自己的计时就比战斗时间快/慢。违反的可观察结果：默认倍率下看不出来，一旦有 `timeMult` 修正（技能、慢动作效果），累计计时与 `getTotalElapsedTime(false)` 的差值按倍率扩大。

选哪种累计时间的决策：

- 需要"战斗推进了多少"用 `getTotalElapsedTime(false)`（败北判定的 30 秒门槛用的就是这条累计）；
- 需要"玩家实际经历了多久"（含暂停发呆）用 `getTotalElapsedTime(true)`；
- 逐帧推进用 `amount` / `getElapsedInLastFrame()`。

## 完整最小例

在既有的 MOD 布局与战斗插件模型（C07-S01）之上，新增一个插件类文件（不新增任何资源、不新增任何注册项），包与类名遵循命名约定（`data.scripts.gearin.combat`、`GearIn<Subject>`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin           # 既有插件；本节不改动
│       ├── GearInGlobalAccess        # 既有类；本节只消费
│       └── combat\
│           └── GearInCombatStatePlugin   # 本节新增
└── jars\
```

### GearInCombatStatePlugin

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗状态读取——引擎状态、时间语义与舰队/舰船遍历。
 *
 * 本插件由战斗上下文注册（C07-S01 的插件模型），只在本场战斗内有效：
 * - 引擎引用在 init() 中保存，只在本场战斗使用；禁止跨战斗缓存（C06-S03）；
 * - advance() 在暂停时仍会被调用（combatMap.advance 照常执行），
 *   状态推进前必须先检查 engine.isPaused()；
 * - getShips() 包含残骸与等待移除的舰船，遍历必须用 isAlive() 过滤；
 * - 战机停在甲板时 isAlive() 仍为 true，但不在 getShips() 的列表里；
 * - getPlayerShip() 在没有玩家舰时返回惰性创建的“Command Shuttle”假舰，
 *   永远非 null，不能拿它判断玩家部署状态。
 */
public class GearInCombatStatePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatStatePlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
                + " campaignSim=" + engine.isInCampaignSim()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫：暂停时 advance 仍会被调用，状态不得推进（C07-S01）
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logEngineTime();
            logFleetManagers();
            logShips();
        }
    }

    /** 引擎时间语义：暂停不计/计、当前帧 dt、时间倍率、接触时间与战斗结果。 */
    private void logEngineTime() {
        LOG.info("GearIn time: total=" + engine.getTotalElapsedTime(false)
                + " totalWithPause=" + engine.getTotalElapsedTime(true)
                + " lastFrame=" + engine.getElapsedInLastFrame()
                + " timeMult=" + engine.getTimeMult().getModifiedValue()
                + " contact=" + engine.getElapsedInContactWithEnemy()
                + " fleetsInContact=" + engine.isFleetsInContact()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }

    /** 两侧舰队管理器：部署/后备/瘫痪/击毁/撤退五份名单的大小，以及已部署成员遍历。 */
    private void logFleetManagers() {
        for (FleetSide side : FleetSide.values()) {
            CombatFleetManagerAPI manager = engine.getFleetManager(side);
            LOG.info("GearIn fleet " + side + ": owner=" + manager.getOwner()
                    + " deployed=" + manager.getDeployedCopy().size()
                    + " reserves=" + manager.getReservesCopy().size()
                    + " disabled=" + manager.getDisabledCopy().size()
                    + " destroyed=" + manager.getDestroyedCopy().size()
                    + " retreated=" + manager.getRetreatedCopy().size());

            int aliveShips = 0;
            for (DeployedFleetMemberAPI member : manager.getDeployedCopyDFM()) {
                if (member.isFighterWing() || member.isStationModule()) {
                    continue;
                }
                ShipAPI ship = member.getShip();
                if (ship != null && ship.isAlive()) {
                    aliveShips++;
                }
            }
            LOG.info("GearIn deployed " + side + ": aliveShips=" + aliveShips);
        }
    }

    /** 引擎对象表遍历：getShips() 含残骸，必须用 isAlive() 过滤；顺带核对玩家舰判据。 */
    private void logShips() {
        int alive = 0;
        int notAlive = 0;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isAlive()) {
                alive++;
            } else {
                notAlive++;
            }
        }
        ShipAPI player = engine.getPlayerShip();
        LOG.info("GearIn ships: alive=" + alive + " notAlive=" + notAlive
                + " playerShip=" + (player == null ? "null" : player.getHullSpec().getHullId())
                + " playerAlive=" + (player != null && player.isAlive())
                + " playerInPlay=" + (player != null && engine.isEntityInPlay(player)));
    }
}
```

对应关系：

- `init` 里的 `battle init` 行：读战斗上下文四标志 + `getMissionId()`，用 `GearIn_test` 战役验证"这场是 mission"。
- `advance` 的 `if (engine.isPaused()) return;`：暂停守卫（规则 1）；守卫还顺带让报告节奏在暂停时停摆——恢复后 `total` 与 `totalWithPause` 的差值就是暂停时长的现场证明。
- `logEngineTime`：把"时间语义"表变成日志——`total` / `totalWithPause` / `lastFrame` / `timeMult` / `contact` / `fleetsInContact` / `combatOver` / `winner` 一次打印。
- `logFleetManagers`：两侧管理器的五份名单大小 + `getDeployedCopyDFM()` 遍历（跳过 `isFighterWing()` / `isStationModule()`，只数存活主力舰——已部署成员遍历的成熟做法）。
- `logShips`：`getShips()` + `isAlive()` 过滤分成 alive / notAlive 两堆（残骸与刚移除的舰计入 notAlive）；`playerShip` 行核对 `getPlayerShip()` 永不 null 的判据（部署阶段显示 `shuttlepod_xfer` 假舰、`playerInPlay=false`）。
- `IntervalUtil(2f, 2f)`：固定 2 秒节奏的周期触发器（`advance` + `intervalElapsed()` 每帧调用）。

注册片段（工程片段——本节不改动既有文件，只演示调用）：

```java
    // 既有战斗插件的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInCombatStatePlugin());
        // ... 原有逻辑
    }
```

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用既有 MOD 构建流程）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/GearInCombatStatePlugin.class`（可用 `jar tf` 核对）。

本节代码块与 fixture 源码逐字一致（fixture 位于 `tutorial-fixtures/units/C07-S02/src/main/java`），fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

前置数据：既有 MOD 布局（C06-S01）与战斗插件接入（C07-S01，含注册片段）、`GearInCombatStatePlugin` 类、`GearIn_test` 测试战役（C03-S01）。

1. **fixture 编译**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC07s02Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/combat/GearInCombatStatePlugin.class`。
3. **战役冒烟**：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 `GearIn battle init: ... mission=true missionId=GearIn_test`，随后每 2 秒三条周期报告；没有 `gear_in` 引起的 `ERROR`。
4. **暂停语义**：战斗内按空格暂停约 10 秒再恢复，暂停期间无报告；恢复后 `totalWithPause` 增长约 10 秒而 `total` 基本不变（对应预期结果 5）。
5. **残骸过滤**：击毁一艘敌方舰，`GearIn ships:` 的 `alive` 减 1、`notAlive` 加 1（对应预期结果 6）。
6. **玩家舰判据**：生涯战斗中在部署阶段观察 `playerShip=shuttlepod_xfer playerInPlay=false`，部署后变玩家舰（对应预期结果 7）。
7. **战斗结束**：清空一侧舰队，确认 `combatOver=true`、`winner=0/1` 与结束动画的先后关系（对应预期结果 8）。
8. **故障注入**：把 `advance` 里的 `if (engine.isPaused()) return;` 注释掉——暂停期间报告照常打印且 `total` 照常增长（对应故障 2）；改回后确认恢复。

通过信号：操作 1–7 全部符合；操作 8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（如 `tutorial-fixtures/build.gradle`）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC07s02Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/combat/GearInCombatStatePlugin.class`。
3. **战斗初始化**：进入 `GearIn_test` 战役一场战斗，`starsector.log` 出现一行
```text
GearIn battle init: campaign=false campaignSim=false simulation=false mission=true missionId=GearIn_test
```
生涯遭遇战则对应 `campaign=true` 其余为 false。没有 `ERROR`。
4. **周期报告**：战斗（未暂停）中每 2 秒出现三条 `GearIn time:` / `GearIn fleet ...:`（含两条 `GearIn deployed ...:`）/ `GearIn ships:` 日志；`time` 行的 `total` 稳步增长、`winner=-1`、`combatOver=false`。
5. **暂停语义**：按空格暂停约 10 秒再恢复，暂停期间**没有**报告输出（守卫生效）；恢复后的下一条 `GearIn time:` 里 `totalWithPause` 比暂停前约多 10 秒，而 `total` 增长远小于 10 秒——两条累计的差值就是暂停时长。
6. **残骸过滤**：击毁一艘敌方舰船后，下一条 `GearIn ships:` 的 `alive` 减 1、`notAlive` 加 1（残骸计入 notAlive 而不是消失）。
7. **玩家舰判据**：生涯战斗的部署阶段（玩家尚未部署任何舰船）`playerShip=shuttlepod_xfer`、`playerAlive=true`、`playerInPlay=false`；部署玩家舰后 `playerShip` 变为玩家舰的 hull id、`playerInPlay=true`。战役开局即设置玩家舰，通常直接看到玩家舰。
8. **战斗结束**：战斗收尾时 `combatOver=true`、`winner=0`（玩家胜）或 `1`（敌方胜）；结束动画期间 `combatOver` 已为 true 但画面尚未淡出到结算。

静态自查：`GearInCombatStatePlugin` 类源码与 fixture 逐字一致（包括 import 顺序与注释）。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.combat`、类 `GearInCombatStatePlugin`）。

## 实例驱动故障分析

1. **"存活舰船"统计含残骸**。触发：`int n = 0; for (ShipAPI s : engine.getShips()) n++;` 直接数列表。可观察：击毁一艘敌舰后计数不降——残骸仍在对象表里（`isHulk()`），要等移除才消失；死亡当帧的舰还会被多算一次。修正：`if (!s.isAlive()) continue;`（必须遵守的规则：遍历必须过滤）；需要残骸本身时用 `!isHulk()` 明确表达意图。
2. **暂停期间状态照常累计**。触发：`advance()` 里没有 `engine.isPaused()` 守卫，直接做计时/进度累计。可观察：暂停 10 秒后解除，自己的累计比 `getTotalElapsedTime(false)` 多出约 10 秒——战斗根本没推进，数值已经偏了。修正：状态推进前检查 `engine.isPaused()`；需要"含暂停的真实经历时间"才用 `getTotalElapsedTime(true)`（C07-S01 对同一纪律有另一个视角）。
3. **`getPlayerShip()` 判据失效**。触发：`if (engine.getPlayerShip() == null) return;` 想跳过"玩家未部署"阶段。可观察：分支**永远不执行**——RC8 在玩家舰未设置时用到时才返回 `shuttlepod_xfer`"Command Shuttle"假舰，`getPlayerShip()` 战斗内永不返回 null；后果是部署阶段代码把假舰当玩家舰处理。修正：部署判断读 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()`；战斗内外判断用 `Global.getCurrentState()`。
4. **跨战斗缓存 `ShipAPI` 或 `getShips()` 列表**。触发：把某艘舰或整个列表存进字段，下一场战斗继续用。可观察：第二场战斗起 `isAlive()` / `isEntityInPlay()` 恒为 `false`——`Ship.isAlive()` 委托给**当前**引擎实例判定，旧舰不在新引擎的对象表里；没有任何报错，逻辑静默失效。修正：每次需要时从 `engine.getShips()` / 管理器名单现取，禁止缓存（与既有引擎引用禁令同一条纪律）。
5. **两种累计时间选错**。触发：做"战斗推进了多久"的判定却用 `getTotalElapsedTime(true)`。可观察：玩家频繁暂停后判定值偏大（把暂停发呆时间也算成战斗时间）；反过来需要"真实经历时间"（如演出计时）却用 `false` 版本，玩家暂停时演出提前结束。修正：推进语义用 `false`、经历语义用 `true`、逐帧用 `amount` / `getElapsedInLastFrame()`。
6. **时间倍率二次缩放**。触发：`advance(amount)` 里写 `amount * engine.getTimeMult().getModifiedValue()` 再累计自己的计时。可观察：默认 `timeMult=1` 时与 `getTotalElapsedTime(false)` 一致；一旦有倍率修正（技能、慢动作），自己的计时比战斗时间快/慢—— 因为插件收到的 `amount` 已按 `getTimeMult()` 与 settings `combatSpeedMult`（默认 1f）缩放。修正：直接用 `amount` 或 `getElapsedInLastFrame()`，不重复乘倍率。
7. **用 `getWinningSideId()` 判断战斗结束**。触发：`if (engine.getWinningSideId() != -1) { 结算逻辑 }`。可观察：胜负已定时 `winner` 已非 -1（胜者侧确定），但 `isCombatOver()` 仍为 `false`（结束淡出动画未完成），结算逻辑提前执行、界面还在战斗中。修正：结束判定用 `isCombatOver()`；`getWinningSideId()` 只回答"胜者是谁"。

## 总结

本节把战斗域的"读取"侧讲成一个完整流程：`CombatEngineAPI` 状态访问器分四组（战斗上下文、玩家与实体、战斗结果、时间），`getShips()` 遍历必须用 `isAlive()` 过滤。

舰队管理器五份名单与 `getDeployedCopyDFM()` 读部署状态，战斗时间四种语义与暂停时 `advance()` 的行为都落到可观察的日志表现上。

两个"永不 null"入口（`getPlayerShip()` 的用到时才创建的假舰、`getFleetManager(int)` 的新建空管理器）与实体 / 列表的跨战斗缓存禁令，是写侧代码同样要遵守的边界。

逻辑上，本节的读取做法是战斗写侧（伤害、生成、粒子、音效）的前提：写操作同样需要先核对状态与时间语义。

`getTimeMult()` 返回的 `MutableStat` 属于时间倍率主题，本节只消费其修正值；`getMember()` 指向的 `FleetMemberAPI` 携带生涯侧身份，其成员属性由生涯主题完整讲解。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`（公开 API）：状态访问器、遍历入口与时间语义的签名源头
- `com.fs.starfarer.api.combat.CombatFleetManagerAPI`（公开 API）：五份名单、`getDeployedCopyDFM()` 与成员定位
- `com.fs.starfarer.api.combat.ShipAPI` / `com.fs.starfarer.api.combat.CombatEntityAPI`（公开 API）：单舰与实体状态访问器（`isAlive()` / `isHulk()` / `isEntityInPlay()`）
- `com.fs.starfarer.api.combat.DeployedFleetMemberAPI`（公开 API）：部署记录与场上实体的连接（`getMember()` / `getShip()` / `isFighterWing()`）
- `com.fs.starfarer.api.fleet.FleetMemberAPI`（公开 API）：`getMember()` 的返回类型，生涯侧身份
- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin` / `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`（公开 API）：插件骨架与回调签名
- `com.fs.starfarer.api.util.IntervalUtil`（公开 API）：定期间隔触发器
- `com.fs.starfarer.api.mission.FleetSide`（公开 API）：`PLAYER` / `ENEMY` 两侧
- `com.fs.starfarer.api.Global`（公开 API）：`getCombatEngine()` / `getCurrentState()` / `getLogger()` 入口
- `data/config/settings.json`：`combatSpeedMult` 帧 dt 全局倍率配置
- `docs/chapter7/section1/README.md`：战斗插件模型、注册入口与暂停守卫
- `docs/chapter6/section3/README.md`：引擎生命周期、阶段判据与跨战斗缓存禁令
- `tutorial-fixtures/units/C07-S02/src/main/java`：本节示例源码（fixture）
