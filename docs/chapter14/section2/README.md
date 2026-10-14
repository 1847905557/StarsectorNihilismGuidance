# 监听生涯事件

舰队生成、到达、跃迁或消失时，生涯引擎会派发相应事件，我们可以在这些时机处理自己的功能。本节用监听器观察一支舰队三十个游戏日，重点说明事件回调与登记方式，以及保存、读档和到期结束的关系。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~40 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `BaseCampaignEventListener` 只覆写当前任务需要的事件回调；
- 区分永久、transient 和限时监听器的容器与存档边界；
- 根据舰队 Memory 标记过滤事件，并安全读取到达、跃迁和消失参数；
- 判断重复登记、暂停、超时和读档分别如何改变监听器状态。

## 前置知识

- [C7-S1] 的 MOD 插件生命周期与 `onGameLoad()`；
- [C11-S1] 的舰队创建、配置与投放顺序；
- [C11-S2] 的舰队任务队列、目标实体和跨地点移动；
- [C14-S1] 的游戏天换算、暂停时的推进条件与普通对象的存档边界。

## 在生涯入口登记三十日监听

先创建 `src/data/scripts/gearin/campaign/GearInFleetLifecycleListener.java`，在 `GearInModPlugin` 中加入它的 import。限时登记片段放入生涯已经建立的 `onGameLoad(...)`，本轮计数保存在监听器实例的字段中。

**事件回调在实际状态变化时执行。** 上一节用 `EveryFrameScript` 按帧累计时间，适合计时与连续检查。现在要记录的是舰队到达这样的事件，`CampaignEventListener` 会在事件发生时收到对应回调；市场结算、战斗结束和经济迭代也按各自的事件处理。生涯时间流逝本身不会执行监听代码，引擎要先发生事件，再从注册表取出对象调用匹配的方法。

直接实现 `CampaignEventListener` 时，要提供整组事件方法，即使本次只需要其中四个。我们从 `BaseCampaignEventListener` 继承，它已经准备好空实现，子类覆写所需回调即可。基类还提供构造登记和 `reRegister()`，至于在哪个时机、进入哪个容器，则由调用方决定。

`super(true)` 会在基类构造期间直接执行 `Global.getSector().addListener(this)`，适合在生涯引擎建立后创建的永久监听器。`super(false)` 只构造对象，调用方可以先完成字段初始化，再选择永久、transient 或限时入口。本节使用 `super(false)`，在 `onGameLoad(...)` 调用 `reRegister(30.0f)`，把实例放入限时容器。

## 选择监听器容器

先根据这个功能要持续多久选择入口。三个入口对应三个独立容器，同时决定对象是否进入存档、是否自动到期，以及读档后是否重新登记。

### 注册容器速查表

| 入口 | 容器状态 | 保存与结束方式 |
| --- | --- | --- |
| `addListener(listener)` | 永久列表 | 对象随存档保存，调用 `removeListener(listener)` 后结束 |
| `addTransientListener(listener)` | transient 列表 | 列表不写入存档，每次读档后重新登记 |
| `addListenerWithTimeout(listener, days)` | `TimeoutTracker` | 对象与剩余游戏天数随存档保存，期限归零后自动移除 |

需要结束监听时，`removeListener(listener)` 会从三个容器同时移除这个对象。各容器也有自己的判重：永久和 transient 列表使用 `contains()`，限时容器用集合检查对象是否存在。这里通常按对象身份判断，新建两个字段相同的监听器，仍会得到两个登记项。

`getAllListeners()` 依次复制永久列表、追加 transient 列表，再追加限时容器中的对象，合并结果不会去重。本节只进入限时列表，合并后只有这一份事件观察。

::: warning 同一对象也可能收到多次回调

同一个对象若同时进入永久列表与限时容器，同一事件就会调用它两次。各容器自身的判重不处理这种跨容器重复。

:::

### 派发使用当前集合的快照

每个 `report...()` 派发方法都会遍历 `getAllListeners()` 返回的新列表。监听器可以在回调中调用 `removeListener(this)`，因为修改的是原注册表，不会破坏这次遍历。当前回调已经开始时，移除动作只影响后续事件；本轮方法仍会执行到返回。

回调中新登记的监听器，要等下一次事件才会进入新的合并列表。与 [C14-S1] 的脚本快照类似，当前遍历使用的对象集合已经确定；这里的后续调用由下一次对应事件触发。

## 让限时登记只发生一次

`BaseCampaignEventListener.reRegister(daysToKeep)` 内部调用 `addListenerWithTimeout(this, daysToKeep)`。`daysToKeep` 的单位是游戏日，引擎先用 `CampaignClockAPI.convertToDays(amount)` 换算本帧时间，再在未暂停时推进 `TimeoutTracker`。暂停会同时冻结生涯日期和监听器期限。

::: warning reRegister 不会重置现有期限

对象已在限时容器中时，重复登记会被跳过，剩余期限保持原值。需要延期时，先 `removeListener(listener)`，再调用 `reRegister(newDays)`；移除也会清理它在其他监听器容器中的登记。

:::

本节希望每个存档只观察一轮，所以用 persistent data 键记录已经登记这一事实。限时容器则保存监听器、四个计数器和剩余期限：30 天以内保存读档，继续这轮观察；期限归零以后，监听器离开容器，persistent data 键仍保留，后续读档也就保持结束状态。

```java
Map<String, Object> data = sector.getPersistentData();
if (!data.containsKey(GearInFleetLifecycleListener.REGISTERED_KEY)) {
    GearInFleetLifecycleListener listener = new GearInFleetLifecycleListener();
    listener.reRegister(GearInFleetLifecycleListener.ACTIVE_DAYS);
    data.put(GearInFleetLifecycleListener.REGISTERED_KEY, Boolean.TRUE);
}
```

这段局部片段沿用 `onGameLoad(...)` 的 `sector`，添加 `java.util.Map` 的 import。标记在登记后写入。再次进入同一存档时，代码读取到该键，不会创建第二个对象。要重新开始一轮人工测试，需要在测试存档中同时移除旧监听器和该 persistent data 键，再重新执行登记代码。

## 用 Memory 选择目标舰队

生涯里的后台舰队也会不断生成、移动和消失，监听器会收到它们的事件。为了让本例的计数对应到指定舰队，回调读取舰队自身的 Memory，只处理 `$GearIn_listener_probe` 为 `true` 的对象。

**生成标记写在投放之前。** 在 [C11-S1] 的 `GearInFleetGenerator` 已取得 `generatedFleet` 后、`spawnFleet(...)` 前加入局部片段，沿用既有投放：

```java
generatedFleet.getMemoryWithoutUpdate().set(
        GearInFleetLifecycleListener.PROBE_MEMORY_KEY, true);
location.spawnFleet(anchor, 300f, 0f, generatedFleet);
```

投放时就会派发舰队生成回调，因此标记要提前写好。顺序颠倒时，监听器会错过唯一一次生成回调，后续到达、跃迁和消失仍能通过过滤。这里用 `getMemoryWithoutUpdate()` 直接读取 MOD 写入的键，省去为一个布尔标记刷新事实的操作。

## 读取舰队生命周期参数

接下来逐个观察状态转换。完整类为四类回调各保存一个整数计数，读档后比较时，就能看出哪一种事件继续发生了。计数、舰队 ID 与目标参数一起保留，才能对应到实际动作。

### 舰队回调速查表

| 回调 | 触发状态 | 关键参数与观察结果 |
| --- | --- | --- |
| `reportFleetSpawned(fleet)` | 舰队首次加入一个地点，并被标记为已经上报生成 | 舰队自身；投放后 `spawnedCount` 增加 |
| `reportFleetReachedEntity(fleet, entity)` | 舰队 AI 完成以实体为目标的任务 | 舰队与目标实体；日志显示目标 ID 和名称 |
| `reportFleetJumped(fleet, from, to)` | 舰队完成地点间跃迁 | 起点实体与 `JumpDestination`；任一层都可能为空 |
| `reportFleetDespawned(fleet, reason, param)` | 舰队进入消失流程 | 原因枚举决定 `param` 的可解释类型 |

`reportFleetReachedEntity()` 对应舰队任务系统确认到达目标的时机，不能只凭画面中的距离判断。某些 assignment 在完成时先触发消失或完成回调，记录事件时同时保留当前任务与实际目标，日志顺序也就能对应到这些调用。

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

记录消失时，根据原因与参数类型解释上下文。公开约定给出常见输入，通用回调还要记录实际参数类型；下面的局部片段据此选择可以读取的内容：

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

## 接上跃迁与消失动作

本节沿用 [C11-S2] 的实体任务观察到达。在当前生成器追加局部动作时，`generatedFleet` 为测试舰队，`anchor` 为来源市场主实体。下面的片段给出真实跃迁与消失入口，需要 `StarSystemAPI`、`JumpPointAPI` 和 `FleetDespawnReason` 的 import：

```java
StarSystemAPI destinationSystem = Global.getSector().getStarSystem("GearIn_observation_system");
JumpPointAPI entrance = destinationSystem.getAutogeneratedJumpPointsInHyper().get(0);
Global.getSector().doHyperspaceTransition(
        generatedFleet, anchor, entrance.getDestinations().get(0), 0f);
```

跃迁目标来自 [C10-S2] 已自动生成的入口。要观察消失，将下面局部调用放在完成测试任务的回调中，`target` 为该任务实际目标：

```java
generatedFleet.despawn(FleetDespawnReason.REACHED_DESTINATION, target);
```

按生成、到达、跃迁和消失的顺序分别制造事件。跳转后先推进时间，看到对应日志，再安排舰队结束，这样每个计数都能对应到同一支舰队的动作。

### 提前结束监听

超时用于固定窗口，任务提前取消时直接移除活动实例。`getAllListeners()` 返回副本，可以安全查找目标类型；`removeListener()` 随后从永久、transient 和限时容器同时清理这个对象。

```java
for (CampaignEventListener candidate : Global.getSector().getAllListeners()) {
    if (candidate instanceof GearInFleetLifecycleListener listener) {
        Global.getSector().removeListener(listener);
        break;
    }
}
```

这段代码只移除活动对象。每存档一次性策略还由 `REGISTERED_KEY` 控制；保留该键表示监听任务已经结束，移除该键表示允许下一次 `onGameLoad()` 开始新的 30 日窗口。

## 类型化监听器与组合类

需要观察当前地点或经济迭代时，还可以选择 `CurrentLocationChangedListener`、`EconomyTickListener` 这样的窄接口。`ListenerManagerAPI` 按接口类型保存对象，`ListenerUtil` 的对应方法负责派发。部分事件同时派发旧式 `CampaignEventListener` 与类型化接口，同一对象若接入两条通道，就会收到两次业务回调。

transient 类型化监听器需要在每次 `onGameLoad()` 中检查并补回。这里查询 `ListenerManagerAPI` 的集合，与旧式事件容器分别登记。

```java
ListenerManagerAPI manager = Global.getSector().getListenerManager();
if (!manager.hasListenerOfClass(GearInLocationChangeProbe.class)) {
    manager.addListener(new GearInLocationChangeProbe(), true);
}
```

`BaseCampaignEventListenerAndScript` 同时具备监听器与 `EveryFrameScript` 类型。默认构造器调用永久监听入口，带天数的构造器调用限时监听入口。

::: warning 组合类仍需单独登记脚本

两个构造器都只登记监听器。需要执行 `advance()` 时，还要把同一个实例传给 `addScript()`。

:::

```java
BaseCampaignEventListenerAndScript combined = new BaseCampaignEventListenerAndScript(30f) {
    private float elapsedDays;

    @Override
    public void advance(float amount) {
        elapsedDays += Global.getSector().getClock().convertToDays(amount);
    }

    @Override
    public boolean isDone() {
        return elapsedDays >= 30f;
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (fleet.getMemoryWithoutUpdate().getBoolean(
                GearInFleetLifecycleListener.PROBE_MEMORY_KEY)) {
            Global.getLogger(GearInFleetLifecycleListener.class)
                    .info("GearIn combined listener: spawned=" + fleet.getId());
        }
    }
};
sector.addScript(combined);

// 结束时分别清理两个登记方向。
sector.removeListener(combined);
sector.removeScript(combined);
```

`isDone()` 只控制脚本列表的自动移除。它变为 `true` 后，监听器登记仍在；监听回调中调用 `removeListener(this)`，脚本也仍会推进。**组合类的结束条件与清理，要分别落实到这两份登记。**

## 其他事件类别速查表

舰队事件之外，`BaseCampaignEventListener` 还提供市场、战斗、声望与经济等回调。这些方法沿用相同的登记和派发机制，区别在于事件发生的时机与传入的对象：

| 事件类别 | 代表回调 | 适合读取的状态 |
| --- | --- | --- |
| 市场与贸易 | `reportPlayerOpenedMarket`、`reportPlayerMarketTransaction` | 市场对象、交易结算和货物变化 |
| 战斗 | `reportBattleOccurred`、`reportBattleFinished`、`reportPlayerEngagement` | 战斗快照、胜方和玩家结算结果 |
| 声望与能力 | `reportPlayerReputationChange`、`reportPlayerActivatedAbility` | 势力或人物关系变化、能力参数 |
| 经济 | `reportEconomyTick`、`reportEconomyMonthEnd` | 经济迭代索引与月末结算边界 |

## 完整最小例

保存下列类，限时登记与投放前标记分别采用前面的局部入口。

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
    public static final String REGISTERED_KEY = "GearIn_fleet_lifecycle_registered";
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
        return fleet.getMemoryWithoutUpdate().getBoolean(PROBE_MEMORY_KEY);
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

::: details 完整成品：GearInLocationChangeProbe

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/** 记录玩家当前地点的切换。 */
public class GearInLocationChangeProbe implements CurrentLocationChangedListener {
    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        Global.getLogger(GearInLocationChangeProbe.class).info(
                "GearIn location changed: from=" + (prev == null ? "none" : prev.getId())
                + " to=" + curr.getId());
    }
}
```

:::

## 验证方案

前置数据：已启用包含 [C14-S2] 代码的测试 MOD；准备一个可重新载入的测试存档，以及一支能够被投放、接收实体任务、完成跃迁和主动消失的测试舰队。

1. 保存类、限时登记与巡逻队投放前标记，构建重启，创建新档，触发实际生成与监听。
2. 检查 `getAllListeners()`，确认只有一个 `GearInFleetLifecycleListener`，并确认 persistent data 已写入 `GearIn_fleet_lifecycle_registered`。
3. 创建测试舰队，在投放前写入 `$GearIn_listener_probe`，再把舰队加入当前地点。
4. 给舰队安排一个实体目标并等待任务完成，记录生成和到达日志中的计数、舰队 ID 与目标实体。
5. 让同一舰队完成一次地点间跃迁，记录起点和目的地实体。
6. 使用 `REACHED_DESTINATION` 与目标实体调用该舰队的消失入口，记录原因和 `destination` 上下文。
7. 在监听期限内保存并读档，继续使用另一支带标记舰队触发事件，比较读档前后的四类计数。
8. 暂停游戏并保持一段真实时间，再解除暂停，确认 30 游戏日期限只按未暂停的生涯时间减少。
9. 对当前活动实例再次调用 `reRegister(30.0f)`，从原登记时刻继续推进，确认到期时间没有重置。
10. 推进满 30 个游戏日并确认活动集合中已经没有该监听器，随后保存游戏；重新载入这份超时后的存档并再次执行 `onGameLoad()` 登记片段。
11. 按前面的类型化注册方式，实现当前地点变更接口，在位置切换后记录结果，保存读档比较 transient 重登。
12. 在组合接口功能中分别登记脚本与监听，再按两个移除入口结束，核对两种回调各自由实际容器管理。

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

这轮观察从投放前的 Memory 标记开始，四类回调记录同一支舰队的状态变化。限时容器保存实例与剩余期限，persistent data 则记住这轮观察已经开始，结束后也保留这个事实。

使用组合类时，**编者建议把两个移除入口安排在同一个结束动作中**。`isDone()` 成立只会结束脚本，监听器仍留在自己的注册表里。这个类替您合并了接口，两套生命周期却仍由您负责。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignEventListener`：生涯事件回调及 `FleetDespawnReason` 参数约定；
- `com.fs.starfarer.api.campaign.BaseCampaignEventListener`：空回调实现、构造登记和限时登记入口；
- `com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript`：监听器与逐帧脚本组合类型；
- `com.fs.starfarer.api.campaign.SectorAPI`：监听器三个注册容器、合并读取和显式移除；
- `com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI`：类型化监听器的永久与 transient 仓库；
- `com.fs.starfarer.api.util.TimeoutTracker`：限时对象的剩余时间、判重和自动移除机制。
