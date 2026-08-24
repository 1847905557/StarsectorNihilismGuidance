# 读取战斗状态与时间

本节读取战斗状态、双方舰队与时间，并制作一个每 2 秒向 `starsector.log` 报告状态的插件。修改战斗对象前，需要先确认当前状态和所用时间尺度。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 列出 `CombatEngineAPI` 状态访问器的四个分组，并说出代表方法的返回类型与语义；
- 用 `getShips()` 配合 `isAlive()` 正确遍历在场舰船，并解释残骸与停驻战机需要专门处理的原因；
- 用 `CombatFleetManagerAPI` 的五份名单与 `getDeployedCopyDFM()` 读取两侧舰队的部署状态；
- 区分战斗时间的四种语义，并解释暂停时 `advance()` 仍被调用而 `getElapsedInLastFrame()` 不为 0 的原因；
- 识别 `getPlayerShip()` 返回的 "Command Shuttle" 假舰，并用正确判据确认玩家部署状态；
- 从日志症状定位七类典型故障：残骸统计错误、暂停期间状态累计、玩家舰判据失效、跨战斗缓存实体引用、两种累计时间选错、时间倍率二次缩放、误用 `getWinningSideId()` 判断结束。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C4-S1] 的战役与生涯概念，以及 [C4-S2] 的模拟战入口；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C6-S3] 的 `Global` 生命周期、战斗引擎阶段与跨战斗引用边界；
- [C7-S1] 的 `addPlugin(...)` 注册、`EveryFrameCombatPlugin.advance` 回调与暂停检查；
- [C7-S3] 的 `MutableStat` 与 `getTimeMult()` 返回值。

## 文件与 API 链路

示例插件要回答三个问题：这场战斗属于哪一类、两侧舰队各处于什么状态、时间推进了多少。答案分别来自三个接口家族，本节先认识它们。涉及的接口都在 `com.fs.starfarer.api.combat`(`FleetSide` 在 `com.fs.starfarer.api.mission`)：

### 本节接口速查表

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `CombatEngineAPI` | 状态 / 时间 / 遍历入口 | `Global.getCombatEngine()` 取得 ([C6-S3] 讲生命周期)；本节讲状态读取，写侧由音效与粒子示例使用 |
| `CombatFleetManagerAPI` | 舰队名单与部署状态 | `engine.getFleetManager(FleetSide)` / `getFleetManager(int owner)` 取得 |
| `ShipAPI` / `CombatEntityAPI` | 单舰状态访问器 | `getShips()` 与 `DeployedFleetMemberAPI.getShip()` 的返回元素 |
| `DeployedFleetMemberAPI` | 已部署成员 (舰或战机联队) | `manager.getDeployedCopyDFM()` 的元素；`getMember()` 指向 `FleetMemberAPI` |
| `EveryFrameCombatPlugin` / `BaseEveryFrameCombatPlugin` | 每帧回调骨架 | 战斗插件模型 [C7-S1] |
| `IntervalUtil` | 定期间隔触发 (本节示例用它做周期日志) | `com.fs.starfarer.api.util` |
| `FleetSide` | `PLAYER` / `ENEMY` 两侧 | `getFleetManager(FleetSide)` |

### 状态访问器分组速查表

| 分组 | 成员 | 语义 |
|---|---|---|
| 战斗上下文 | `isInCampaign()` / `isInCampaignSim()` / `isSimulation()` / `isMission()` / `getMissionId()` | 这场战斗是哪一类：生涯遭遇战 / 生涯内模拟战 / 模拟战 / 战役；`getMissionId()` 非战役时返回 null |
| 玩家与实体 | `getPlayerShip()` / `isShipAlive(ShipAPI)` / `isEntityInPlay(CombatEntityAPI)` / `isInEngine(ShipAPI)` | 玩家舰与实体存活性 (详见 "玩家舰" 与 "实体存活性") |
| 战斗结果 | `isCombatOver()` / `getWinningSideId()` / `getCombatNotOverFor()` | 战斗是否结束、胜者 (详见 "战斗结果") |
| 时间 | `getTotalElapsedTime(boolean)` / `getElapsedInLastFrame()` / `getTimeMult()` / `getElapsedInContactWithEnemy()` / `isFleetsInContact()` / `isPaused()` / `setPaused(boolean)` | 战斗时间四种语义与暂停 (详见 "时间语义") |

接口签名只是起点，语义才是本节的重点。最容易踩空的三处是战斗上下文判断、`getPlayerShip()` 的返回语义与战斗结束判定，先从它们开始。

## 读取战斗上下文

### 读取战斗引擎

- `isInCampaign()`：返回目标是生涯状态且当前战斗不是模拟战才为 `true`；战役 (mission) 与模拟战 (包括生涯内模拟战) 返回 `false`。
- `isInCampaignSim()` / `isSimulation()` / `isMission()`：三层细分 —— 生涯内模拟战的 `isInCampaignSim()` 与 `isSimulation()` 均为 `true`、`isInCampaign()` 为 `false`；主菜单模拟战只有 `isSimulation()` 为 `true`；战役 `isMission()` 为 `true` 且 `getMissionId()` 返回战役 ID(如 `GearIn_test` 测试战役)。
- 用途：同一段逻辑在不同战斗类型下行为不同时 (例如只在生涯遭遇战里开启某机制)，先读这几个标志，不要在 `init` 里猜。

### 读取玩家舰船

`CombatEngineAPI.getPlayerShip()` 在玩家舰尚未设置时**不会**返回 null，而是用到时才创建一艘 "Command Shuttle" 假舰 (`shuttlepod_xfer` 装配的克隆，owner 0) 并返回。

可观察结果：生涯战斗的部署阶段 (玩家还没部署任何舰船)`getPlayerShip()` 返回 `shuttlepod_xfer` 假舰；战役 (mission) 开局即设置玩家舰，通常观察不到假舰。

由此得到两条必须遵守的规则：

1. **禁止**用 `engine.getPlayerShip() == null` 判断 "玩家未部署 / 不在战斗"。该表达式在战斗内永远为 `false`，依赖它的分支**永远不执行** (静默失效，无报错)。判断部署状态应读 `engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy()` 是否非空；判断是否在战斗用 `Global.getCurrentState() == GameState.COMBAT`。
2. 拿到 `getPlayerShip()` 后**必须**核对 `isHulk()` / `isAlive()`(或 `playerShip` 的舰型)，再把它当作 "玩家控制的真舰" 使用 —— 部署阶段拿到的是假舰，对它调用写操作没有实际玩家意义。

### 判断战斗结果

- `isCombatOver()`：战斗结束标志置位**且**结束淡出动画播放完才返回 `true`。可观察结果：胜负判定到淡出完成之间有一个窗口，`isCombatOver()` 仍为 `false`。
- `getWinningSideId()`：API javadoc 原文 "0 = player, 1 = enemy, 2 = player allies, no player ships left."；胜负未定时返回 -1(初始默认值)。因此**禁止**把 `getWinningSideId() != -1` 当作 "战斗已结束" 判据 —— 它代表的是 "胜者侧"，结束判定必须用 `isCombatOver()`。
- `setCombatNotOverFor(float)` / `getCombatNotOverFor()`：把战斗强制延续一段时间 (防止按败北条件提前收场)；结束判定对 "败北" 还要求战斗时间累计超过 30 秒 —— 已部署舰船全灭时战斗立即结束，不受此门槛限制。

战斗类型与结束条件清楚了，接下来读取场上到底有哪些舰船 —— 引擎对象表和舰队管理器提供两条互补的路径。

## 遍历舰队与舰船

### 遍历战斗舰船

`engine.getShips()` 返回引擎对象表里的全部 `ShipAPI`，它**不是** "存活舰船列表"：

- 残骸 (`isHulk() == true`) 仍在对象表里 (引擎自身的推进循环遍历时也要跳过残骸)；
- 已标记移除、尚未执行移除的舰船也还在表里；
- 停在甲板 (机库) 里的战机**不在**对象表里，但 `isAlive()` 仍为 `true`。

### 实体存活性速查表

| 方法 | 语义 |
|---|---|
| `ship.isAlive()` | 与引擎侧 `engine.isShipAlive(ship)` 同一判定 —— 非残骸、`getHitpoints() > 0`、且 (仍在引擎对象表中或属于停在甲板的战机)。战机停驻时仍为 `true` |
| `engine.isShipAlive(ship)` | 同上，引擎侧入口 (`null` 安全：传 null 返回 `false`) |
| `engine.isEntityInPlay(entity)` | 实体当前在战斗场上：已入对象表且未被标记移除 (刚加入战斗、尚未入表的实体也算在场)—— 残骸若仍在对象表中，此方法为 `true`；停驻甲板的战机为 `false` |

::: warning 遍历必须过滤残骸

遍历 `getShips()` 时**必须**先用 `isAlive()` 过滤 (需要含残骸的场景才用 `!isHulk()` 或 `isEntityInPlay()` 自行定义)。违反的可观察结果：把残骸数进 "存活舰船" 统计；对残骸执行写操作 (补甲板、回血等) 白白消耗；死亡当帧的舰船仍被处理一帧导致重复逻辑。`getShips()` + `isAlive()` 过滤是战斗遍历的标准做法。

:::

### 区分舰队名单

`engine.getFleetManager(FleetSide.PLAYER / ENEMY)` 与 `engine.getFleetManager(int owner)`(0 = 玩家侧、1 = 敌方侧) 返回两侧的 `CombatFleetManagerAPI`。

注意：`getFleetManager(int)` 在战斗外的空引擎 / 已销毁的旧引擎上**不会**返回 null，而是新建一个空管理器返回 —— 所以 "拿不到管理器" 同样不能靠判 null，而要依靠引擎生命周期主题给出的阶段判据。

### 舰队名单速查表

管理器把舰队成员按状态分成五份名单，全部以快照副本 (Copy) 返回，修改副本不影响引擎：

| 方法 | 名单含义 |
|---|---|
| `getDeployedCopy()` | 当前已部署 (在场) 的成员 |
| `getReservesCopy()` | 尚未部署、还在后备的成员 |
| `getDisabledCopy()` | 瘫痪 (CR 耗尽 / 无法行动) 的已部署成员 |
| `getDestroyedCopy()` | 已击毁的成员 |
| `getRetreatedCopy()` | 已撤退的成员 |

遍历已部署成员用 `getDeployedCopyDFM()`(`List<DeployedFleetMemberAPI>`)：每个元素把 "部署记录" 与 "场上实体" 连起来：

- `getMember()` 是 `FleetMemberAPI`(生涯侧身份，完整讲解见生涯主题)。
- `getShip()` 是场上 `ShipAPI`(战机联队返回联队长机)；
- `isFighterWing()` / `isStationModule()` / `isAlly()` 是常用过滤。

需要按成员定位场上实体时用 `getShipFor(FleetMemberAPI)`(未部署返回 null) 与 `getDeployedFleetMember(ShipAPI)`。

遍历 `getDeployedCopyDFM()` 时跳过 `isFighterWing()` 与 `isStationModule()`、只处理主力舰，是已部署成员遍历的成熟做法。

::: warning 禁止跨战斗缓存实体引用

从名单 (`getShips()` / `getDeployedCopyDFM()` 等) 拿到的 `ShipAPI` / 列表**禁止**跨战斗缓存 —— 战斗结束后引擎销毁重建，旧实体的 `isAlive()` 委托给**新**引擎实例判定：在新引擎里旧舰不在对象表中，`isEntityInPlay()` 恒为 `false`；`isAlive()` 只查 `wasRemoved`，上局存活舰返回 `true`。列表本身也只反映获取那一刻的状态。可观察结果：第二场战斗起缓存引用 "数据还在但判定混乱"，且无任何报错 (与既有引擎引用禁令同一条纪律的实体侧)。这里的 "销毁" 即 `destroyInstance`：它属于引擎内部方法 (非公开 API)，本节用作 "战斗结束销毁" 的简称，插件自身没有该方法。

:::

名单告诉您谁在场，时间则告诉您这一帧推进了多少。战斗时间的四种语义是最后一处容易选错的地方。

## 判断战斗时间

### 战斗时间语义速查表

| 方法 | 语义 | 暂停时行为 |
|---|---|---|
| `getTotalElapsedTime(false)` | 累计战斗时间，**暂停不计** | 停止累计 |
| `getTotalElapsedTime(true)` | 累计真实经历时间，**含暂停** (暂停时也增长) | 继续累计 |
| `getElapsedInLastFrame()` | 当前帧的 `dt`；API javadoc 原文 "Does *not* return 0 if the game is paused; actually the *current* frame." | **非 0**(暂停时也不例外) |
| `getTimeMult()` | 引擎全局时间倍率 (`MutableStat`，完整讲解见时间倍率主题)；每帧 `dt` 先乘 `getModifiedValue()` 再乘 settings 的 `combatSpeedMult`(默认 1f，见 `settings.json` 的 `combatSpeedMult` 项) 才用于推进战斗 | — |
| `getElapsedInContactWithEnemy()` | 双方互见敌舰的累计时间 (API javadoc "Time elapsed while both sides can see at least one enemy ship.") | 不累计 |
| `isPaused()` / `setPaused(boolean)` | 暂停标志 (开 / 关) | — |

两个关键事实：

1. **暂停时 `advance()` 仍被调用**：暂停不中断战斗引擎对 `EveryFrameCombatPlugin` 的每帧回调 ——`advance()` 在暂停时照常被调用。**必须**在推进状态前检查 `engine.isPaused()`([C7-S1] 已立此规)。违反的可观察结果：暂停期间自己的计时 / 状态照常累计，解除暂停后数值已偏移。
2. **插件收到的 `amount` 与 `getElapsedInLastFrame()` 是同一个数**：引擎按 `timeMult` 与 `combatSpeedMult` 缩放后得到本帧 `dt`，同时写入 `getElapsedInLastFrame()` 并作为 `amount` 传给每帧回调，暂停时也照常传入。默认两个倍率都是 1，`amount` 即真实帧长。做 "按推进时间累计" 直接用 `amount` 或 `getElapsedInLastFrame()`，**不要**再乘 `engine.getTimeMult().getModifiedValue()`—— 那会二次缩放，游戏内时间倍率一改 (慢动作等) 自己的计时就比战斗时间快/慢。违反的可观察结果：默认倍率下看不出来，一旦有 `timeMult` 修正 (技能、慢动作效果)，累计计时与 `getTotalElapsedTime(false)` 的差值按倍率扩大。

选哪种累计时间的决策：

- 需要 "战斗推进了多少" 用 `getTotalElapsedTime(false)`(败北判定的 30 秒门槛用的就是这条累计；已部署全灭时绕过门槛)；
- 需要 "玩家实际经历了多久"(含暂停发呆) 用 `getTotalElapsedTime(true)`；
- 逐帧推进用 `amount` / `getElapsedInLastFrame()`。

语义齐全之后，把它们写进一个可运行的插件，让日志替您说出这些状态。

## 实现战斗状态读取

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 记录战斗上下文

```java
// 插入位置：GearInCombatStatePlugin.init()
this.engine = engine;
LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
        + " campaignSim=" + engine.isInCampaignSim()
        + " simulation=" + engine.isSimulation()
        + " mission=" + engine.isMission()
        + " missionId=" + engine.getMissionId());
```

新增行为：插件在创建时确认战斗类型并保存本场引擎引用。完成信号：进入 `GearIn_test` 后日志显示 `mission=true` 和对应 `missionId`；进入生涯遭遇战后 `campaign=true`。

### 建立周期报告

```java
// 插入位置：GearInCombatStatePlugin.advance()
if (engine.isPaused()) {
    return;
}
reportInterval.advance(amount);
if (reportInterval.intervalElapsed()) {
    logEngineTime();
    logFleetManagers();
    logShips();
}
```

新增行为：非暂停时间每累计 2 秒生成一组状态快照。完成信号：正常战斗时三组日志按相同节奏出现；暂停期间全部停止。

### 对照两种累计时间

```java
// 插入位置：GearInCombatStatePlugin.logEngineTime()
LOG.info("GearIn time: total=" + engine.getTotalElapsedTime(false)
        + " totalWithPause=" + engine.getTotalElapsedTime(true)
        + " lastFrame=" + engine.getElapsedInLastFrame()
        + " timeMult=" + engine.getTimeMult().getModifiedValue()
        + " contact=" + engine.getElapsedInContactWithEnemy()
        + " fleetsInContact=" + engine.isFleetsInContact()
        + " combatOver=" + engine.isCombatOver()
        + " winner=" + engine.getWinningSideId());
```

新增行为：一行日志同时给出推进时间、含暂停时间、帧长、倍率、接敌状态和战斗结果。完成信号：暂停约 10 秒后恢复，`totalWithPause - total` 比暂停前增加约 10 秒。

### 统计双方部署状态

```java
// 插入位置：GearInCombatStatePlugin.logFleetManagers() 的 FleetSide 循环内
CombatFleetManagerAPI manager = engine.getFleetManager(side);
LOG.info("GearIn fleet " + side + ": owner=" + manager.getOwner()
        + " deployed=" + manager.getDeployedCopy().size()
        + " reserves=" + manager.getReservesCopy().size()
        + " disabled=" + manager.getDisabledCopy().size()
        + " destroyed=" + manager.getDestroyedCopy().size()
        + " retreated=" + manager.getRetreatedCopy().size());
```

新增行为：玩家侧和敌方侧的五份成员名单进入同一格式的日志。完成信号：部署、击毁或撤退舰船后，下一次报告中只有对应名单的数量发生变化。

### 排除残骸并核对玩家舰

```java
// 插入位置：GearInCombatStatePlugin.logShips()
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
```

新增行为：对象表被分成存活舰船和残骸/待移除舰船，并单独核对玩家舰是否真正在场。完成信号：击毁一艘舰船后 `alive` 减少、`notAlive` 增加；部署阶段可看到玩家假舰与 `playerInPlay=false` 的反差。

## 核对状态插件源码

对应关系：

- `init` 里的 `battle init` 行：读战斗上下文四标志 + `getMissionId()`，用 `GearIn_test` 战役验证 "这场是 mission"。
- `advance` 的 `if (engine.isPaused()) return;`：把时间语义的第 1 条落成代码 —— 暂停时不推进状态，报告节奏也随之停摆；恢复后 `total` 与 `totalWithPause` 的差值就是暂停时长的现场证明。
- `logEngineTime`：把 "时间语义" 表变成日志 ——`total` / `totalWithPause` / `lastFrame` / `timeMult` / `contact` / `fleetsInContact` / `combatOver` / `winner` 一次打印。
- `logFleetManagers`：两侧管理器的五份名单大小 + `getDeployedCopyDFM()` 遍历 (跳过 `isFighterWing()` / `isStationModule()`，只数存活主力舰 —— 已部署成员遍历的成熟做法)。
- `logShips`：`getShips()` + `isAlive()` 过滤分成 alive / notAlive 两堆 (残骸与刚移除的舰计入 notAlive)；`playerShip` 行核对 `getPlayerShip()` 永不 null 的判据 (部署阶段显示 `shuttlepod` 假舰、`playerInPlay=false`)。
- `IntervalUtil(2f, 2f)`：固定 2 秒节奏的周期触发器 (`advance` + `intervalElapsed()` 每帧调用)。

注册片段 (工程片段 —— 本节不改动既有文件，只演示调用)：

```java
    // 既有战斗插件的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInCombatStatePlugin());
        // ... 原有逻辑
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用既有 MOD 构建流程)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/GearInCombatStatePlugin.class`(可用 `jar tf` 核对)。

## 完整最小例

在既有的 MOD 布局与战斗插件模型 [C7-S1] 之上，新增一个插件类文件 (不新增任何资源、不新增任何注册项)，包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearIn<Subject>`)：

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

::: details 完整成品：GearInCombatStatePlugin

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
 * 完整最小例：战斗状态读取 —— 引擎状态、时间语义与舰队/舰船遍历。
 *
 * 本插件由战斗上下文注册 (C7-S1 的插件模型)，只在本场战斗内有效：
 * - 引擎引用在 init() 中保存，只在本场战斗使用；禁止跨战斗缓存 (C6-S3)；
 * - advance() 在暂停时仍会被调用 (combatMap.advance 照常执行)，
 *   状态推进前必须先检查 engine.isPaused()；
 * - getShips() 包含残骸与等待移除的舰船，遍历必须用 isAlive() 过滤；
 * - 战机停在甲板时 isAlive() 仍为 true，但不在 getShips() 的列表里；
 * - getPlayerShip() 在没有玩家舰时返回惰性创建的 "Command Shuttle" 假舰，
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
            return; // 暂停守卫：暂停时 advance 仍会被调用，状态不得推进 (C7-S1)
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

:::

## 排查舰船状态错误

把 `getPlayerShip()` 当作部署判据。

**局部故障片段：**

```java
// 故障片段
if (engine.getPlayerShip() == null) { return; }
```

**局部修正片段：**

```java
// 修正片段
if (engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy().isEmpty()) { return; }
```

复验：未部署和已部署各观察一次玩家侧名单。

## 验证方案

前置数据：既有 MOD 布局 [C6-S1] 与战斗插件接入 ([C7-S1]，含注册片段)、`GearInCombatStatePlugin` 类、`GearIn_test` 测试战役 [C4-S1]。

1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，并核对 `jar tf jars\GearIndustry.jar` 的内容。
2. 进入 `GearIn_test` 战役一场战斗，让插件运行数秒。
3. 战斗内按空格暂停约 10 秒再恢复。
4. 击毁一艘敌方舰船，观察下一条 `GearIn ships:` 日志。
5. 在生涯战斗中先后观察部署阶段与部署玩家舰后的日志。
6. 清空一侧舰队，观察战斗收尾与结束动画的先后关系。

预期结果：

- **游戏构建**：`BUILD SUCCESSFUL`；`jar tf` 能看到 `data/scripts/gearin/combat/GearInCombatStatePlugin.class`
- **战役冒烟**：`starsector.log` 出现一行：
- **暂停语义**：暂停期间**没有**报告输出 (暂停检查生效)；恢复后的下一条 `GearIn time:` 里 `totalWithPause` 比暂停前约多 10 秒，而 `total` 增长远小于 10 秒 —— 两条累计的差值就是暂停时长
- **残骸过滤**：`alive` 减 1、`notAlive` 加 1(残骸计入 notAlive 而不是消失)
- **玩家舰判据**：部署阶段 (玩家尚未部署任何舰船)`playerShip=shuttlepod`(日志打印 hull id；假舰 variant 为 `shuttlepod_xfer`)、`playerAlive=false`、`playerInPlay=false`；部署玩家舰后 `playerShip` 变为玩家舰的 hull id、`playerInPlay=true`。战役开局即设置玩家舰，通常直接看到玩家舰
- **战斗结束**：战斗收尾时 `combatOver=true`、`winner=0`(玩家胜) 或 `1`(敌方胜)；结束动画期间 `combatOver` 仍为 false，淡出完成后才变 true

## 故障分析

- **击毁敌舰后存活数不降**：直接统计 `engine.getShips()` 会把尚未移除的残骸算进去。

    - **解决方案**：遍历时用 `isAlive()` 过滤存活舰船；需要识别残骸时再检查 `isHulk()`。

- **暂停期间累计仍然增长**：暂停不会中断 `advance()` 回调，缺少 `engine.isPaused()` 守卫时，计时和进度会继续推进。

    - **解决方案**：状态更新前加入暂停检查。

- **玩家未部署分支从不执行**：`getPlayerShip()` 在玩家舰尚未设置时会返回 `shuttlepod_xfer` 的 `Command Shuttle` 假舰，因此 null 判断无法识别未部署状态。

    - **解决方案**：改读 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()`。

- **第二场战斗中的缓存舰船判定混乱**：旧舰不在新引擎的对象表中，跨战斗保存的舰船或列表会让 `isEntityInPlay()` 持续返回 `false`，而 `isAlive()` 仍可能返回 `true`。

    - **解决方案**：每次使用时从当前引擎或舰队管理器重新取得对象。

- **战斗计时包含错误的暂停区间**：`getTotalElapsedTime(true)` 包含暂停时间，`false` 版本只累计战斗推进时间。

    - **解决方案**：推进判定使用 `false`，演出等真实经历时间使用 `true`，逐帧累计使用 `amount` 或 `getElapsedInLastFrame()`。

- **时间倍率改变后计时偏移**：传入 `advance(amount)` 的 `amount` 已受 `getTimeMult()` 与 `combatSpeedMult` 缩放，再乘一次倍率会重复计算。

    - **解决方案**：直接累计 `amount` 或读取 `getElapsedInLastFrame()`。

- **结算逻辑在退场动画前执行**：`getWinningSideId()` 在胜者确定时已经返回侧别，而 `isCombatOver()` 要等结束淡出完成才变为 `true`。

    - **解决方案**：用 `isCombatOver()` 触发结算，只用 `getWinningSideId()` 判断胜者。

## 总结

本节把战斗域的读取侧讲成一个完整流程：`CombatEngineAPI` 状态访问器分四组 (战斗上下文、玩家与实体、战斗结果、时间)，`getShips()` 遍历必须用 `isAlive()` 过滤，舰队管理器五份名单与 `getDeployedCopyDFM()` 读部署状态，战斗时间的四种语义与暂停时 `advance()` 的行为都落到可观察的日志表现上。

两个 "永不 null" 入口 (`getPlayerShip()` 用到时才创建的假舰、`getFleetManager(int)` 新建的空管理器) 与实体、列表的跨战斗缓存禁令，是写侧代码同样要遵守的边界 —— 写操作同样需要先核对状态与时间语义，本节的读取做法是它们的共同前提。

`getTimeMult()` 返回的 `MutableStat` 属于时间倍率主题，本节只读取其修正值；`getMember()` 指向的 `FleetMemberAPI` 携带生涯侧身份，其成员属性由生涯主题完整讲解。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatEngineAPI`(公开 API)：状态访问器、遍历入口与时间语义的签名源头。
- `com.fs.starfarer.api.combat.CombatFleetManagerAPI`(公开 API)：五份名单、`getDeployedCopyDFM()` 与成员定位。
- `com.fs.starfarer.api.combat.ShipAPI` / `com.fs.starfarer.api.combat.CombatEntityAPI`(公开 API)：单舰与实体状态访问器 (`isAlive()` / `isHulk()` / `isEntityInPlay()`)。
- `com.fs.starfarer.api.combat.DeployedFleetMemberAPI`(公开 API)：部署记录与场上实体的连接 (`getMember()` / `getShip()` / `isFighterWing()`)。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`(公开 API)：`getMember()` 的返回类型，生涯侧身份。
- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin` / `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`(公开 API)：插件骨架与回调签名。
- `com.fs.starfarer.api.util.IntervalUtil`(公开 API)：定期间隔触发器。
- `com.fs.starfarer.api.mission.FleetSide`(公开 API)：`PLAYER` / `ENEMY` 两侧。
- `com.fs.starfarer.api.Global`(公开 API)：`getCombatEngine()` / `getCurrentState()` / `getLogger()` 入口。
- `data/config/settings.json`：`combatSpeedMult` 帧 dt 全局倍率配置。
- `docs/chapter7/section1/README.md`：战斗插件模型、注册入口与暂停检查。
- `docs/chapter6/section3/README.md`：引擎生命周期、阶段判据与跨战斗缓存禁令。
