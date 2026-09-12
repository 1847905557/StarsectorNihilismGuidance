# 监听生涯事件

本节制作一个持续 30 个游戏日的舰队生命周期监听器。监听器只接收带有指定 Memory 标记的舰队，分别记录生成、到达、跃迁和消失事件，使注册位置、派发过程、存档恢复和超时结果都能从日志中核对。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

- 使用 `BaseCampaignEventListener` 只覆写当前任务需要的事件回调；
- 区分永久、transient 和限时监听器的容器与存档边界；
- 根据舰队 Memory 标记过滤事件，并安全读取到达、跃迁和消失参数；
- 判断重复登记、暂停、超时和读档分别如何改变监听器状态。

## 前置知识

- [C7-S1] 的 MOD 插件生命周期与 `onGameLoad()`；
- [C11-S1] 的舰队创建、配置与投放顺序；
- [C11-S2] 的舰队任务队列、目标实体和跨地点移动；
- [C14-S1] 的游戏天换算、暂停门控与普通对象的存档边界。

## 从逐帧推进切换到事件派发

`EveryFrameScript` 由引擎持续询问是否推进，适合累计时间和检查连续状态。`CampaignEventListener` 只在具体事件发生时收到回调，适合处理市场结算、战斗结束、舰队到达或经济迭代。监听器不会因为生涯时间流逝而自行执行代码；引擎必须先发生对应事件，再从注册表取出监听对象并调用匹配的方法。

`CampaignEventListener` 声明了整组生涯事件。直接实现该接口需要提供所有方法，即使任务只关心其中四个。`BaseCampaignEventListener` 已为全部方法提供空实现，子类只覆写所需回调即可。这个基类还把注册方式收进构造器和 `reRegister()`，但注册发生的时机和容器仍由调用方选择。

`super(true)` 会在基类构造期间直接执行 `Global.getSector().addListener(this)`，适合明确在生涯引擎建立后创建的永久监听器。`super(false)` 只构造对象，调用方可以完成字段初始化，再选择永久、transient 或限时入口。本节完整类使用 `super(false)`，由 `onGameLoad()` 调用 `reRegister(30.0f)` 进入限时容器。

## 选择监听器容器

三个入口保存到三个独立容器。选择入口会同时决定对象是否写入存档、是否自动到期，以及读档后是否需要重新登记。

### 注册容器速查表

| 入口 | 容器状态 | 保存与结束方式 |
| --- | --- | --- |
| `addListener(listener)` | 永久列表 | 对象随存档保存，调用 `removeListener(listener)` 后结束 |
| `addTransientListener(listener)` | transient 列表 | 列表不写入存档，每次读档后重新登记 |
| `addListenerWithTimeout(listener, days)` | `TimeoutTracker` | 对象与剩余游戏天数随存档保存，期限归零后自动移除 |

`removeListener(listener)` 会从三个容器同时移除给定对象。永久和 transient 列表分别用 `contains()` 防止同一对象在同一列表中重复加入；限时容器用集合判断对象是否已经存在。这里的判等通常表现为对象身份判等，因此新建两个字段相同的监听器仍是两个登记项。

同一对象也可能被加入不同容器。`getAllListeners()` 会依次复制永久列表、追加 transient 列表，再追加限时容器中的对象，并不对合并结果去重。如果一个对象同时进入永久列表和限时容器，同一个事件就会对它调用两次。注册代码需要为对象选择一个容器，而不是依赖各容器内部的去重检查。

### 派发使用当前集合的快照

每个 `report...()` 派发方法都会遍历 `getAllListeners()` 返回的新列表。监听器可以在回调中调用 `removeListener(this)`，因为修改的是原注册表，不会破坏这次遍历。当前回调已经开始时，移除动作只影响后续事件；本轮方法仍会执行到返回。

新监听器在回调中加入注册表后，也要等下一次事件才会出现于新的合并列表。这个边界与 [C14-S1] 的脚本列表快照相似，但触发单位从下一帧变成了下一次对应事件。

## 让限时登记只发生一次

`BaseCampaignEventListener.reRegister(daysToKeep)` 内部调用 `addListenerWithTimeout(this, daysToKeep)`。`daysToKeep` 的单位是游戏日，引擎先用 `CampaignClockAPI.convertToDays(amount)` 换算本帧时间，再在未暂停时推进 `TimeoutTracker`。暂停会同时冻结生涯日期和监听器期限。

`reRegister()` 的名字容易造成误判。对象已经存在于限时容器时，`addListenerWithTimeout()` 会跳过写入，所以再次调用不会把剩余时间恢复为 30 天。确实需要延期时，应先 `removeListener(listener)`，再调用 `reRegister(newDays)`；这两步也会改变该对象在其他监听器容器中的状态，调用点需要明确承担重新登记。

本节采用每个存档只登记一次的策略。persistent data 键保存已经登记这一事实；监听器本身和四个计数器由限时容器保存。保存发生在 30 天期限内时，读档会恢复剩余期限和计数。期限归零后监听器离开容器，persistent data 键继续存在，因此后续读档保持结束状态。

```java
SectorAPI sector = Global.getSector();
Map<String, Object> data = sector.getPersistentData();
if (!data.containsKey(GearInFleetLifecycleListener.REGISTERED_KEY)) {
    GearInFleetLifecycleListener listener = new GearInFleetLifecycleListener();
    listener.reRegister(GearInFleetLifecycleListener.ACTIVE_DAYS);
    data.put(GearInFleetLifecycleListener.REGISTERED_KEY, Boolean.TRUE);
}
```

标记在监听器成功登记后写入。再次进入同一存档时，代码读取到该键，不会创建第二个对象。要重新开始一轮人工测试，需要在测试存档中同时移除旧监听器和该 persistent data 键，再重新执行登记代码。

## 用 Memory 选择目标舰队

生涯中持续有后台舰队生成、移动和消失。监听全部舰队会让日志被无关事件淹没，也无法把一次操作对应到一个计数。完整监听器读取舰队自身的 Memory，只处理 `$GearIn_listener_probe` 为 `true` 的对象。

生成回调在舰队首次加入地点时派发，因此标记必须在 `LocationAPI.addEntity(fleet)` 前写入。读者已经完成舰队创建后，只需在投放语句前增加一行：

```java
fleet.getMemoryWithoutUpdate().set(GearInFleetLifecycleListener.PROBE_MEMORY_KEY, true);
location.addEntity(fleet);
```

如果先投放再写标记，监听器会错过该舰队唯一一次生成回调，后续到达、跃迁和消失事件仍能通过过滤。使用 `getMemoryWithoutUpdate()` 可以直接读取 MOD 自己写入的键，避免为了一个布尔标记触发事实刷新。

## 读取舰队生命周期参数

四个回调描述的是不同状态转换。完整类为每类事件保存独立整数计数，使保存读档后能够判断具体哪条链路继续，而不是只看到一个无法归因的总数。

### 舰队回调速查表

| 回调 | 触发状态 | 关键参数与观察结果 |
| --- | --- | --- |
| `reportFleetSpawned(fleet)` | 舰队首次加入一个地点，并被标记为已经上报生成 | 舰队自身；投放后 `spawnedCount` 增加 |
| `reportFleetReachedEntity(fleet, entity)` | 舰队 AI 完成以实体为目标的任务 | 舰队与目标实体；日志显示目标 ID 和名称 |
| `reportFleetJumped(fleet, from, to)` | 舰队完成地点间跃迁 | 起点实体与 `JumpDestination`；任一层都可能为空 |
| `reportFleetDespawned(fleet, reason, param)` | 舰队进入消失流程 | 原因枚举决定 `param` 的可解释类型 |

`reportFleetReachedEntity()` 对应的是舰队任务系统确认到达目标，不是玩家视野中的距离估计。某些 assignment 在完成时先触发消失或完成回调，测试时应以实际日志顺序判断调用点，不把界面动画顺序当作 API 顺序。

跃迁参数有两层对象关系。`from` 通常是起点跳跃实体；`to` 是 `JumpDestination`，真正的目的地实体由 `to.getDestination()` 返回。完整类逐层判空，把缺失位置记录为 `null`，避免日志代码本身打断事件派发。

### 消失原因速查表

| `FleetDespawnReason` | `param` 的公开约定 | 安全读取方式 |
| --- | --- | --- |
| `REACHED_DESTINATION` | `SectorEntityToken` | 先做 `instanceof SectorEntityToken`，再读取 ID 和名称 |
| `DESTROYED_BY_BATTLE` | 战斗前快照 `BattleAPI` | 先做 `instanceof BattleAPI`，再读取战斗上下文 |
| `NO_MEMBERS` | 未限定 | 记录空值或实际运行时类型 |
| `OTHER` | 未限定 | 由调用该舰队 `despawn()` 的代码定义 |
| `NO_REASON_PROVIDED` | 未限定 | 记录空值或实际运行时类型 |
| `PLAYER_FAR_AWAY` | 未限定 | 记录空值或实际运行时类型 |

局部判断的核心是让枚举和值类型同时成立。只根据 `reason` 强制转换会把调用方传入错误参数的问题变成监听器异常。

```java
if (reason == FleetDespawnReason.REACHED_DESTINATION
        && param instanceof SectorEntityToken destination) {
    return "destination=" + entityLabel(destination);
}
if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE
        && param instanceof BattleAPI battle) {
    return "battlePlayerInvolved=" + battle.isPlayerInvolved();
}
return param == null ? "param=null" : "paramType=" + param.getClass().getName();
```

## 显式结束活动监听器

超时适合固定观察窗口，任务提前取消时应直接移除活动实例。`getAllListeners()` 返回副本，可以安全查找目标类型；`removeListener()` 随后从永久、transient 和限时容器同时清理这个对象。

```java
for (CampaignEventListener candidate : Global.getSector().getAllListeners()) {
    if (candidate instanceof GearInFleetLifecycleListener listener) {
        Global.getSector().removeListener(listener);
        break;
    }
}
```

这段代码只移除活动对象。每存档一次性策略还由 `REGISTERED_KEY` 控制；保留该键表示监听任务已经结束，移除该键表示允许下一次 `onGameLoad()` 开始新的 30 日窗口。

## 划清另外两种监听方式

`ListenerManagerAPI` 按接口类型存放监听器，由 `ListenerUtil` 的对应方法派发。它适合 `CurrentLocationChangedListener`、`EconomyTickListener` 等窄接口。部分事件会同时派发旧式 `CampaignEventListener` 和类型化接口，同一对象接入两条通道时会收到两次业务回调。

transient 类型化监听器需要在每次 `onGameLoad()` 中检查并补回。这里检查的是 `ListenerManagerAPI` 自己的仓库，与 `SectorAPI.getAllListeners()` 没有交集。

```java
ListenerManagerAPI manager = Global.getSector().getListenerManager();
if (!manager.hasListenerOfClass(GearInLocationChangeProbe.class)) {
    manager.addListener(new GearInLocationChangeProbe(), true);
}
```

`BaseCampaignEventListenerAndScript` 同时具备监听器和 `EveryFrameScript` 类型，但两个基类构造器只登记监听器部分。默认构造器调用永久监听入口，带天数的构造器调用限时监听入口；两者都不会把对象加入脚本列表。需要执行 `advance()` 时，调用方还要显式调用 `addScript()`。

```java
GearInCombinedListener combined = new GearInCombinedListener();
SectorAPI sector = Global.getSector();
sector.addScript(combined);

// 结束时分别清理两个注册表。
sector.removeListener(combined);
sector.removeScript(combined);
```

`isDone()` 只参与脚本列表的自动移除。它变为 `true` 后，监听器登记仍然存在；监听回调中调用 `removeListener(this)` 也不会结束脚本推进。组合类必须为两个方向各自定义结束条件和清理动作。

## 其他事件类别速查表

舰队生命周期用于完整实现，`BaseCampaignEventListener` 的其余回调仍遵循相同注册和派发规则。选择回调时应从需要观察的状态转换反推方法，而不是一次覆写全部接口。

| 事件类别 | 代表回调 | 适合读取的状态 |
| --- | --- | --- |
| 市场与贸易 | `reportPlayerOpenedMarket`、`reportPlayerMarketTransaction` | 市场对象、交易结算和货物变化 |
| 战斗 | `reportBattleOccurred`、`reportBattleFinished`、`reportPlayerEngagement` | 战斗快照、胜方和玩家结算结果 |
| 声望与能力 | `reportPlayerReputationChange`、`reportPlayerActivatedAbility` | 势力或人物关系变化、能力参数 |
| 经济 | `reportEconomyTick`、`reportEconomyMonthEnd` | 经济迭代索引与月末结算边界 |

## 验证方案

前置数据：已启用包含 [C14-S2] 代码的测试 MOD；准备一个可重新载入的测试存档，以及一支能够被投放、接收实体任务、完成跃迁和主动消失的测试舰队。

1. 编译包含 `GearInFleetLifecycleListener` 的 Java 代码，进入测试存档并执行一次限时登记。
2. 检查 `getAllListeners()`，确认只有一个 `GearInFleetLifecycleListener`，并确认 persistent data 已写入 `GearIn_C14S2_listener_registered`。
3. 创建测试舰队，在投放前写入 `$GearIn_listener_probe`，再把舰队加入当前地点。
4. 给舰队安排一个实体目标并等待任务完成，记录生成和到达日志中的计数、舰队 ID 与目标实体。
5. 让同一舰队完成一次地点间跃迁，记录起点和目的地实体。
6. 使用 `REACHED_DESTINATION` 与目标实体调用该舰队的消失入口，记录原因和 `destination` 上下文。
7. 在监听期限内保存并读档，继续使用另一支带标记舰队触发事件，比较读档前后的四类计数。
8. 暂停游戏并保持一段真实时间，再解除暂停，确认 30 游戏日期限只按未暂停的生涯时间减少。
9. 对当前活动实例再次调用 `reRegister(30.0f)`，从原登记时刻继续推进，确认到期时间没有重置。
10. 推进满 30 个游戏日并确认活动集合中已经没有该监听器，随后保存游戏；重新载入这份超时后的存档并再次执行 `onGameLoad()` 登记片段。
11. 注册 transient 类型化位置监听器，保存并读档，确认 `onGameLoad()` 的类型检查会建立新实例。
12. 使用组合监听器片段分别登记脚本和监听器，依次只移除其中一个注册方向，观察剩余方向继续收到的调用。

预期结果：

- Java 编译完成，加载期没有类缺失或方法签名错误；
- 测试舰队投放后只出现一条 `event=spawned` 日志，`spawnedCount` 为 `1`；
- 完成实体任务后 `reachedCount` 增加，日志目标与实际 assignment 目标一致；
- 完成跃迁后 `jumpedCount` 增加，日志安全显示起点和 `JumpDestination` 的目的地；
- `REACHED_DESTINATION` 消失日志包含目标实体，`despawnedCount` 增加；
- 30 日内读档后四个计数从保存值继续，同一事件没有双份日志；
- 暂停期间监听器保持在限时容器中，解除暂停后继续消耗剩余游戏天数；
- 对活动实例再次调用 `reRegister()` 后仍在原期限到期；
- 满 30 个游戏日后活动集合中没有该监听器；保存并读取这份超时后的存档时，persistent data 键继续阻止重新登记；
- transient 类型化监听器读档后由注册片段补回；
- 组合对象只从脚本列表移除时仍接收监听事件，只从监听器容器移除时仍接收 `advance()`。

## 故障分析

- **投放测试舰队后没有生成日志**：Memory 标记在 `LocationAPI.addEntity()` 之后写入，生成事件派发时过滤条件仍为 `false`。

    - **解决方案**：创建舰队后先向 `getMemoryWithoutUpdate()` 写入 `$GearIn_listener_probe`，再把舰队加入地点。

- **日志持续记录后台舰队**：回调直接处理所有传入舰队，没有检查测试 Memory 键。

    - **解决方案**：让四个回调首先执行同一个 `isProbeFleet()` 判断，只处理标记为 `true` 的舰队。

- **同一舰队事件出现两份相同日志**：同一对象进入了两个 Sector 监听器容器，或创建了两个不同的监听器实例。

    - **解决方案**：检查 `getAllListeners()` 中的对象身份和类型，为完整监听器只选择限时容器，并用 persistent data 控制实例创建。

- **再次调用 `reRegister()` 后仍按原时间到期**：对象已经存在于 `TimeoutTracker`，限时登记入口跳过了重复写入。

    - **解决方案**：需要重置期限时先调用 `removeListener(listener)`，再对同一对象调用 `reRegister(newDays)`。

- **暂停一段真实时间后监听器仍未超时**：限时容器只在生涯未暂停时扣减换算后的游戏天数。

    - **解决方案**：解除暂停并按生涯日期验证期限，记录登记时和检查时的游戏日差值。

- **读档后 transient 类型化回调消失**：`ListenerManagerAPI` 的 transient 仓库在读档时重建为空集合。

    - **解决方案**：在每次 `onGameLoad()` 中使用 `hasListenerOfClass()` 检查，并以 `isTransient=true` 重新登记。

- **组合监听器的 `advance()` 从未执行**：`BaseCampaignEventListenerAndScript` 的构造器只登记监听器部分。

    - **解决方案**：构造对象后把同一个实例传给 `SectorAPI.addScript()`。

- **组合脚本已经完成，监听回调仍然出现**：`isDone()` 只控制脚本列表，没有修改监听器注册表。

    - **解决方案**：组合对象结束时同时调用 `removeListener(this)`，让监听器方向离开注册表。

- **舰队消失事件抛出类型转换异常**：代码只根据预期原因直接转换 `param`，实际参数为空或类型与原因不符。

    - **解决方案**：同时检查 `FleetDespawnReason` 和 `instanceof`，未满足公开约定时记录空值或运行时类型。

## 总结

`BaseCampaignEventListener` 让实现类只关注需要的事件，但对象能否收到回调仍由注册容器决定。永久、transient 和限时登记具有不同的存档与结束规则；派发时形成的合并副本解释了回调中安全移除、下一事件生效和跨容器双回调。限时监听器以未暂停的游戏天推进，同一实例重复 `reRegister()` 不会刷新期限。

舰队生命周期回调的可靠性取决于过滤时机和参数边界。Memory 标记需要在舰队投放前写入，跃迁目标需要从 `JumpDestination` 继续取实体，消失上下文需要同时判断原因与运行时类型。四个独立计数器把事件链变成可保存、可读档和可逐项核对的状态。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignEventListener`：生涯事件回调及 `FleetDespawnReason` 参数约定；
- `com.fs.starfarer.api.campaign.BaseCampaignEventListener`：空回调实现、构造登记和限时登记入口；
- `com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript`：监听器与逐帧脚本组合类型；
- `com.fs.starfarer.api.campaign.SectorAPI`：监听器三个注册容器、合并读取和显式移除；
- `com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI`：类型化监听器的永久与 transient 仓库；
- `com.fs.starfarer.api.util.TimeoutTracker`：限时对象的剩余时间、判重和自动移除机制。

## 完整最小例

::: details 完整成品：GearInFleetLifecycleListener

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * 在 30 个游戏日内记录带指定 Memory 标记的舰队生命周期事件。
 */
public class GearInFleetLifecycleListener extends BaseCampaignEventListener {

    public static final String PROBE_MEMORY_KEY = "$GearIn_listener_probe";
    public static final String REGISTERED_KEY = "GearIn_C14S2_listener_registered";
    public static final float ACTIVE_DAYS = 30.0f;

    private static final Logger LOG = Global.getLogger(GearInFleetLifecycleListener.class);

    private int spawnedCount = 0;
    private int reachedCount = 0;
    private int jumpedCount = 0;
    private int despawnedCount = 0;

    public GearInFleetLifecycleListener() {
        super(false);
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        spawnedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=spawned fleet=%s count=%d",
                fleet.getId(), spawnedCount));
    }

    @Override
    public void reportFleetReachedEntity(CampaignFleetAPI fleet, SectorEntityToken entity) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        reachedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=reached fleet=%s count=%d target=%s",
                fleet.getId(), reachedCount, entityLabel(entity)));
    }

    @Override
    public void reportFleetJumped(
            CampaignFleetAPI fleet,
            SectorEntityToken from,
            JumpDestination to) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        jumpedCount++;
        SectorEntityToken destination = to == null ? null : to.getDestination();
        LOG.info(String.format(
                "GearIn fleet listener: event=jumped fleet=%s count=%d from=%s to=%s",
                fleet.getId(), jumpedCount, entityLabel(from), entityLabel(destination)));
    }

    @Override
    public void reportFleetDespawned(
            CampaignFleetAPI fleet,
            FleetDespawnReason reason,
            Object param) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        despawnedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=despawned fleet=%s count=%d reason=%s %s",
                fleet.getId(), despawnedCount, String.valueOf(reason),
                despawnContext(reason, param)));
    }

    private static boolean isProbeFleet(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(PROBE_MEMORY_KEY);
    }

    private static String despawnContext(FleetDespawnReason reason, Object param) {
        if (reason == FleetDespawnReason.REACHED_DESTINATION
                && param instanceof SectorEntityToken destination) {
            return "destination=" + entityLabel(destination);
        }
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE
                && param instanceof BattleAPI battle) {
            return "battlePlayerInvolved=" + battle.isPlayerInvolved();
        }
        return param == null
                ? "param=null"
                : "paramType=" + param.getClass().getName();
    }

    private static String entityLabel(SectorEntityToken entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getId() + "(" + entity.getName() + ")";
    }
}
```

:::
