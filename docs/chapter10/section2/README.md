# 生涯事件监听器与事件边界

本节带您把生涯事件接入 MOD：先弄清两条注册通道 —— `CampaignEventListener` 与 `ListenerManagerAPI`，再创建三个监听器类并在 `onGameLoad` 注册它们。前面章节已经建立脚本轮询与插件生命周期，本节补上事件驱动这一环，让您的 MOD 在市场、贸易、战斗、舰队与声望发生时收到回调。完成后，您将拥有三个可运行的监听器、一张事件边界对照表和十条按症状排错的路径。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 说出两条监听器通道，并说清各自的存储、去重与派发语义：
  - Sector 级 `CampaignEventListener` 通道：`addListener` / `addTransientListener` / `addListenerWithTimeout` / `removeListener` / `getAllListeners`；
  - `ListenerManagerAPI` 类型化通道：`addListener` / `addListener(listener, isTransient)` / `removeListener` / `removeListenerOfClass` / `hasListener` / `hasListenerOfClass` / `getListeners(Class)`。
- 按分组说出 `CampaignEventListener` 全接口 21 个方法，并解释每一组事件在哪个边界点被触发 (战斗结束、贸易结算、舰队入地/离场/跳跃/到达、市场开关、声望变更、经济迭代)；
- 说出监听器生命周期规则：注册时机、persistent data 注册标记、**监听器必须在合适时机移除**、transient 监听器读档后必须重新注册、配对类 "脚本结束 ≠ 监听器移除"、`addListenerWithTimeout` 的游戏天单位与暂停冻结语义；
- 创建 `GearInCampaignListener`(Sector 级监听器)、`GearInListenerLifecycleScript`(按帧生命周期脚本，与监听器配对) 与 `GearInLocationChangeListener`(`ListenerManagerAPI` 类型化监听器)，并在 `onGameLoad` 正确注册；
- 从症状定位十类典型故障：没注册、`onApplicationLoad` 注册 NPE、重复注册成双、transient 读档丢失、僵尸监听器、超时单位误用、事件日志过频、战斗回调重复计数、双通道混淆、旧档不注册。

## 前置知识

本节的两条通道、事件边界与生命周期规则都建立在既有成果之上，请先掌握以下内容：

- 生涯引擎结构 (C08-S01)：`SectorAPI` 持有的监听器列表与脚本列表、`EveryFrameScript`、persistent data `CLASS_MARK` 注册标记、暂停语义 —— 本节的注册标记与配对模式从这里继续。
- 脚本序列化边界与真实秒换算 (C10-S01)：`scripts` 随档、`transientScripts` 读档清空、`advance(amount)` 入参是上一帧真实秒 —— 本节的监听器与生命周期脚本配对复用这两条结论。
- MOD 插件生命周期与阶段判断 (C06-S02、C06-S03)：`modPlugin` 生命周期回调与 persistent data 注册标记模式；`Global.getSector()` 的 null 语义与 "用 `getCurrentState()` 判断阶段"。
- 市场、贸易与声望 (C08-S05、C08-S08)：`reportPlayerMarketTransaction` 的实体是市场交易；声望事件回调的对象由 `SectorAPI.adjustPlayerReputation` 触发。
- 战斗域 (C07-S02)：`reportPlayerEngagement` 的 `EngagementResultAPI` 来自战斗结算。

## 术语

进入两条通道之前，先弄清本节承担首次讲解的两个概念；后续生涯域页面会直接沿用这两种说法：

- **监听器 (listener)**：API 名保留 `CampaignEventListener` / `ListenerManagerAPI` 等源码大小写。
  - 在生涯域中，监听器是 "对生涯事件感兴趣、事件发生时被引擎回调" 的对象。
  - 它与生涯脚本 (每帧被推进) 互补：脚本是轮询式 (每帧 `advance`)，监听器是事件驱动式 (事件发生才回调)。
  - 两者可以绑定在同一对象上 (API 侧的 `BaseCampaignEventListenerAndScript` 即此形态)，也可以拆成两个对象配对协作 (本节示例即拆分形态)，但两条机制的生命周期互不通用。
- **事件边界 (event boundary)**：一个生涯事件从 "游戏逻辑发生" 到 "全部已注册监听器被回调" 的完整链路 —— 触发点 (谁在什么条件下发起)、派发者 (Sector 级通道由引擎侧 `reportXxx` 方法族遍历、类型化通道由 `ListenerUtil.reportXxx` 遍历)、回调签名。排查 "回调没触发" 时必须沿着这条链逐段核对。

## 文件与 API 链路

两条通道的存储、去重与派发语义都不同，命名也容易混。先解决命名问题，再分别看两条通道。

### 命名说明：CampaignListener 与 CampaignEventListener

**公开**生涯事件监听器接口名是 `CampaignEventListener`(`com.fs.starfarer.api.campaign.CampaignEventListener`)。

旧资料里出现的 "CampaignListener" 写法只残留在引擎内部实现侧，不是公开 API，MOD 无法也不应注册到该接口。

正文一律使用公开接口名 `CampaignEventListener`；教程主题里的 "CampaignListener" 指的就是这个概念。

### 通道一：Sector 级 CampaignEventListener

通道一是 MOD 直接实现 `CampaignEventListener` 的常规路径。注册入口 (`SectorAPI`，`com.fs.starfarer.api.campaign.SectorAPI`)：

| 方法 | 语义 |
|---|---|
| `void addListener(CampaignEventListener listener)` | 注册到**持久**监听器列表 (随存档保存)|
| `void addTransientListener(CampaignEventListener listener)` | 注册到 **transient** 监听器列表 (不随存档保存，读档后清空)|
| `void addListenerWithTimeout(CampaignEventListener listener, float daysToKeep)` | 注册到超时跟踪器，**daysToKeep 个游戏天**后自动移除 |
| `void removeListener(CampaignEventListener listener)` | 从三个列表同时移除 |
| `List<CampaignEventListener> getAllListeners()` | 三个列表的并集快照 |

存储与持久性 (生涯引擎侧)：

- `listeners` 非 `transient` → **随存档保存**：常驻监听器跨读档继续有效，其非 `transient` 字段原样恢复。
- `transientListeners` 是 `transient` → **不随存档保存**，读档时重建为空列表 ——transient 监听器读档后必然消失。
- `listenersWithTimeout` 用 `TimeoutTracker`(公开 API `com.fs.starfarer.api.util.TimeoutTracker`) 承载；`items` 随档保存、`set` 读档时重建。
- `listenerManager` 是 `ListenerManagerAPI` 实例 (通道二)，读档时若为 null 会重建默认实例。

去重：`addListener` / `addTransientListener` 在写入前用 `contains` 去重 —— 列表按 `equals` 判等，监听器普遍不覆写 `equals`，此时即按对象同一性；`addListenerWithTimeout` 同样以 `contains` 判重。

派发：生涯引擎的 `reportXxx(...)` 方法族逐事件遍历 `getAllListeners()`(三列表并集的新列表快照) 调用回调。

因此**在回调里注册/移除监听器不会抛 `ConcurrentModificationException`**(派发基于快照)，但遍历顺序是 "持久 → transient → 超时" 三段的拼接顺序，不要依赖跨段次序。

超时语义 (引擎每帧推进时)：

```java
float var9 = this.getClock().convertToDays(var1);
if (!this.isPaused()) {
    this.listenersWithTimeout.advance(var9);
}
```

**`daysToKeep` 的单位是游戏天**(不是真实秒)；每帧按本帧折合的游戏天数扣减，且**只在未暂停时扣减**—— 暂停期间超时冻结。扣减到 0 后由 `TimeoutTracker.advance` 自动移除。

::: warning 常见坑：超时单位误用

`addListenerWithTimeout` / `reRegister` 的 `daysToKeep` 是**游戏天**(10 真实秒 = 1 游戏日)，且只在未暂停时扣减。把超时当真实秒、或以为暂停也在计时，都会得到 "永不超时 / 超时过慢" 的假象 (对应故障分析第 5 条)。

:::

帮助类 (公开 API)：

- `BaseCampaignEventListener(boolean permaRegister)`(`com.fs.starfarer.api.campaign.BaseCampaignEventListener`)：
  - `CampaignEventListener` 的**全方法空实现**，`permaRegister=true` 时构造器直接 `Global.getSector().addListener(this)`；
  - `reRegister(float daysToKeep)` 等价 `addListenerWithTimeout(this, daysToKeep)`；
  - 覆写关心的方法即可，不必实现 21 个方法的全接口。
- `BaseCampaignEventListenerAndScript(float days)`(`com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript`)：监听器与 `EveryFrameScript` 绑定在同一对象上的基类，构造器按 days 以超时方式注册监听器。

### 通道二：ListenerManagerAPI 类型化通道

通道一按对象存监听器，回调却按接口签名区分；`SectorAPI` 里的 `listenerManager` 就是按接口类型组织的第二通道。

`SectorAPI.getListenerManager()` 返回 `ListenerManagerAPI`(`com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI`，7 个方法)：

| 方法 | 语义 |
|---|---|
| `void addListener(Object listener)` | 注册到持久仓库 |
| `void addListener(Object listener, boolean isTransient)` | `isTransient=true` 注册到 transient 仓库 |
| `void removeListener(Object listener)` | 从两个仓库同时移除 |
| `void removeListenerOfClass(Class<?> c)` | 按类从两个仓库批量移除 |
| `boolean hasListener(Object listener)` | 两个仓库都查 |
| `boolean hasListenerOfClass(Class<?> c)` | 两仓库任一生效 |
| `<T> List<T> getListeners(Class<T> c)` | 返回持久 + transient 并集的**新列表副本** |

实现 (`ListenerManagerAPI`)：内部由两个仓库构成 ——`listeners`(持久) 与 `transientListeners`(transient，读档时重建)。语义要点：

- `addListener` / `addListener(listener, true)` 直接写入对应仓库，**`ListenerManagerAPI` 自身没有任何去重检查**(与通道一的 `contains` 去重不同；仓库内部语义本节不展开验证，不要依赖 "重复注册会被自动去重")。
- `removeListener` 同时清两个仓库；`removeListenerOfClass` 遍历两个仓库的**快照**逐个移除 (安全改列表)；`getListeners` 返回两仓库并集的新列表，派发侧遍历的是这份副本。
- 持久性：`ListenerManagerAPI` 实例随存档保存；transient 仓库读档后必然清空。

派发：所有类型化事件由公开 API 的 `ListenerUtil`(`com.fs.starfarer.api.campaign.listeners.ListenerUtil`) 静态方法族派发。

模式统一为 `for (X x : Global.getSector().getListenerManager().getListeners(X.class)) { x.report...( ... ); }`(约 50 个事件方法)。

类型化接口 40 余个，都在 `com.fs.starfarer.api.campaign.listeners` 包下：
- `ColonyInteractionListener`、`FleetEventListener`、`EconomyTickListener`；
- `CurrentLocationChangedListener`、`CargoGainedListener`、`DiscoverEntityListener`；
- `GateTransitListener` 等。

::: warning 常见坑：双通道并存，同一对象只走一条

部分事件两个通道都会派发。

例：`reportPlayerOpenedMarket` 派发时先遍历 `getAllListeners()` 调用 `CampaignEventListener.reportPlayerOpenedMarket`。

随后再调 `ListenerUtil.reportPlayerOpenedMarket` 派发 `ColonyInteractionListener`。

同一对象不要同时注册两条通道，否则同一事件回调双份。

:::

### CampaignEventListener 全接口方法

`CampaignEventListener` 是唯一需要记全方法签名的接口。21 个方法按事件边界分组：

| 分组 | 方法 | 触发边界 |
|---|---|---|
| 市场 | `reportPlayerOpenedMarket(MarketAPI)` / `reportPlayerClosedMarket(MarketAPI)` / `reportPlayerOpenedMarketAndCargoUpdated(MarketAPI)` | 玩家打开/关闭市场交互；打开市场且货物刷新后 |
| 贸易 | `reportPlayerMarketTransaction(PlayerMarketTransaction)` / `reportPlayerDumpedCargo(CargoAPI)` / `reportPlayerDidNotTakeCargo(CargoAPI)` / `reportEncounterLootGenerated(FleetEncounterContextPlugin, CargoAPI)` | 贸易/整备结算、倾倒货物、未取走战利品、遭遇战利品生成 |
| 战斗 | `reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI)` / `reportBattleFinished(CampaignFleetAPI, BattleAPI)` / `reportPlayerEngagement(EngagementResultAPI)` | 战斗发生/结束 (含 AI vs AI 自动结算)、玩家战斗结算 |
| 舰队 | `reportFleetSpawned(CampaignFleetAPI)` / `reportFleetDespawned(CampaignFleetAPI, FleetDespawnReason, Object param)` / `reportFleetReachedEntity(CampaignFleetAPI, SectorEntityToken)` / `reportFleetJumped(CampaignFleetAPI, SectorEntityToken from, JumpDestination to)` | 舰队入地/离场/到达目标/跳跃 |
| 声望 | `reportPlayerReputationChange(String faction, float delta)` / `reportPlayerReputationChange(PersonAPI person, float delta)` | 玩家对势力/人物的声望变更 |
| 能力 | `reportPlayerActivatedAbility(AbilityPlugin, Object param)` / `reportPlayerDeactivatedAbility(AbilityPlugin, Object param)` | 玩家激活/停用能力 |
| 对话框 | `reportShownInteractionDialog(InteractionDialogAPI)` | 玩家打开任意交互对话框 |
| 经济 | `reportEconomyTick(int iterIndex)` / `reportEconomyMonthEnd()` | 每次经济迭代、每月末 |

`FleetDespawnReason` 枚举恰 6 值：
- `REACHED_DESTINATION`(param 是 `SectorEntityToken`)；
- `DESTROYED_BY_BATTLE`(param 是战斗前快照的 `BattleAPI`)；
- `NO_MEMBERS`
- `OTHER`
- `NO_REASON_PROVIDED`
- `PLAYER_FAR_AWAY`

### 事件边界：触发点表现

方法签名只说明回调能拿到什么，触发时机要落到引擎的实现位置。以下触发点全部来自引擎内部实现与公开 API 源码，是 "事件边界" 判定的依据：

| 事件 | 触发点 (本地表现)|
|---|---|
| 市场开启/关闭 | 打开市场交互对话框时 `reportPlayerOpenedMarket`，对话框关闭时 `reportPlayerClosedMarket`；打开贸易面板时 `reportPlayerOpenedMarketAndCargoUpdated` |
| 贸易结算 | 贸易面板与整备界面 (舰船、武器、战机) 结算后 `reportPlayerMarketTransaction`；倾倒货物、未取走战利品也经贸易面板触发 |
| 战斗发生/结束 | 自动结算轮结算出胜者后 `reportBattleOccurred`、战斗结束 `reportBattleFinished`；API javadoc：AI vs AI 每个自动结算轮一次、玩家遭遇战一次；玩家战斗结算生成 `EngagementResultAPI` 后 `reportPlayerEngagement`(`com.fs.starfarer.api.impl.campaign.FleetEncounterContext`)；战利品生成 `reportEncounterLootGenerated`(`com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`)|
| 舰队入地 | 舰队加入地点且未上报过 (`isReportedSpawned` 标志) 时 `reportFleetSpawned`—— 每支舰队只上报一次 |
| 舰队离场 | `CampaignFleetAPI.despawn(FleetDespawnReason, Object)` 内 `reportFleetDespawned`——reason 与 param 由调用方决定 |
| 舰队跳跃/到达 | 超空间跳跃处理上报 `reportFleetJumped`(devmode 跳跃同样触发)；舰队 AI 到达 assignment 目标时上报 `reportFleetReachedEntity` |
| 声望变更 | 声望行动处理插件结算后 `reportPlayerReputationChange`(`com.fs.starfarer.api.impl.campaign.CoreReputationPlugin`)|
| 经济迭代 | 经济推进器每次迭代 `reportEconomyTick(iterIndex)`、月末 `reportEconomyMonthEnd`(同时派发类型化通道 `EconomyTickListener`)|
| 当前位置变化 | 引擎检测到当前地点变化时经 `ListenerUtil.reportCurrentLocationChanged` 派发 (类型化通道 `CurrentLocationChangedListener`)|
| 对话框 | 打开交互对话框后上报 `reportShowInteractionDialog`(`SectorAPI`)，随后回调 `CampaignEventListener.reportShownInteractionDialog` |
| 遭遇开始 | 玩家舰队与实体接触形成遭遇时，引擎内部开始遭遇处理 (非公开 API，MOD 观察不到)；玩家侧后续以交互对话框/战斗事件体现 |
| 能力开关 | 公开 API 有派发方法，具体触发点在引擎内部 (本节不展开验证)—— 语义：玩家激活/停用能力时回调 |

## 生命周期规则

接口与边界回答 "有哪些事件、何时触发"，生命周期回答 "注册之后怎么办"。规则按强度分级：必须遵守的规则在前，建议在后。

::: caution 监听器生命周期必须遵守的规则

1. **必须**：监听器注册只发生在生涯引擎建立之后。
   - `Global.getSector()` 在资源加载期为 null，在 `onApplicationLoad` 里注册的解引用即 NPE、启动中止。
   - 注册入口：`onNewGame` / `onGameLoad`。
2. **必须**：一次性注册用随存档保存的 persistent data 注册标记 (`CLASS_MARK` 模式，本节示例用各自键)。
   - 通道一 `addListener` 虽然自带 `contains` 去重，但配对的生命周期脚本半身 `addScript` 不去重 —— 注册标记缺失的可观察结果是读档次数越多日志越成双、行为叠加。
3. **必须**：监听器在不再需要时必须在合适时机移除 (`removeListener` / `removeListenerOfClass`)。
   - 监听器一旦注册就持续被派发：常驻注册表的对象还随存档用 XStream 序列化。
   - 违反的可观察结果：事件日志持续出现、行为持续叠加；监听器字段里的瞬态句柄或不可序列化状态拖进存档导致存档异常膨胀、保存/读档异常或读档后行为错乱。
   - 成熟实例：事件 factor 类在构造器注册、`notifyFactorRemoved` 里 `removeListener(this)`。
4. **必须**：transient 监听器 (`addTransientListener` / `addListener(listener, true)`) 读档后必须重新注册。
   - 读档时两条通道的 transient 仓库都会被清空。
   - 违反的可观察结果：读档后事件静默不触发、无任何报错。
   - 成熟实例：用 `addListener(epGen, true)` 注册 transient 遭遇点生成器。
5. **必须**：配对类 "脚本结束 ≠ 监听器移除"。
   - `isDone() == true` 只让引擎从脚本列表清理脚本半身，**监听器注册表不会自动移除**—— 必须显式 `removeListener(this)`。
   - 违反的可观察结果：脚本自停日志出现后事件回调仍持续 ("僵尸监听器")。

:::

建议 (建议强度)：

- 常驻监听器 (与具体任务无关、全程有效) 在 `onGameLoad` 注册一次；临时监听器 (剧情、任务、事件 factor、遭遇点生成器) 用通道二注册并在结束时移除。
- 注册前用 `hasListenerOfClass` / `hasListener` 查重 (成熟实例：先 `hasListenerOfClass` 再 `addListener`，随后 `getListeners(...).get(0)` 取回实例)。
- 事件与玩家无关的过滤放在回调内 (如 `fleet.isPlayerFleet()`)，不要在回调外做全局开关。

## 完整最小例

规则落到代码。在既有 MOD 布局与 `GearInModPlugin` 之上新增三个类 (不新增任何资源)，对应两条注册通道：

通道一 = 监听器 + 生命周期脚本配对 (`sector.addListener` / `sector.addScript`)；

通道二 = 类型化监听器 (`getListenerManager().addListener(..., true)`)：

```text
Starsector/mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data/
├── src/
│   └── data/scripts/gearin/campaign/
│       ├── GearInCampaignListener        # 本节新增（通道一：Sector 级监听器）
│       ├── GearInListenerLifecycleScript # 本节新增（通道一：生命周期脚本，与监听器配对）
│       └── GearInLocationChangeListener  # 本节新增（通道二：ListenerManagerAPI 类型化监听器）
└── jars/
```

### GearInCampaignListener(通道一：Sector 级监听器)

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 完整最小例：Sector 级生涯事件监听器（CampaignEventListener 角色）。
 * 注册入口：onGameLoad 门控块（页面调用点片段，REGISTER_MARK 一次性门控）。
 * 注册与自停由配对脚本 GearInListenerLifecycleScript 管理 —— 脚本到期后显式
 * removeListener：脚本结束不等于监听器移除（硬规则）。BaseCampaignEventListener
 * 提供全接口 21 方法的空实现，本类只覆写关心的事件边界（市场/贸易/战斗/舰队/声望）。
 */
public class GearInCampaignListener extends BaseCampaignEventListener {

    /** 存档持久化标记：本监听器及其生命周期脚本是否已在该存档注册过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CampaignListener";

    private static final Logger LOG = Global.getLogger(GearInCampaignListener.class);

    /** 不在构造器自注册；注册由调用点在 onGameLoad 门控块内显式执行。 */
    public GearInCampaignListener() {
        super(false);
    }

    // ---- 事件边界：市场 ----

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        LOG.info(String.format("GearIn listener: market opened: %s (size %d)",
                market.getName(), market.getSize()));
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        LOG.info("GearIn listener: market closed: " + market.getName());
    }

    // ---- 事件边界：贸易 ----

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        LOG.info(String.format("GearIn listener: trade at %s, credit value %.0f",
                transaction.getMarket().getName(), transaction.getCreditValue()));
    }

    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
        LOG.info("GearIn listener: encounter loot generated, " + loot.getStacksCopy().size() + " stacks");
    }

    // ---- 事件边界：战斗 ----

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        LOG.info(String.format("GearIn listener: battle occurred, player involved=%s, winner=%s",
                battle.isPlayerInvolved(), winnerName(primaryWinner)));
    }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        LOG.info(String.format("GearIn listener: battle finished, player involved=%s, winner=%s",
                battle.isPlayerInvolved(), winnerName(primaryWinner)));
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        LOG.info("GearIn listener: player engagement resolved, didPlayerWin=" + result.didPlayerWin());
    }

    // ---- 事件边界：舰队 ----

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        LOG.info("GearIn listener: fleet spawned: " + fleet.getName() + " (" + fleet.getFaction().getId() + ")");
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        LOG.info("GearIn listener: fleet despawned: " + fleet.getName() + ", reason=" + reason);
    }

    // ---- 事件边界：声望 ----

    @Override
    public void reportPlayerReputationChange(String faction, float delta) {
        LOG.info(String.format("GearIn listener: reputation change vs faction %s by %+.2f", faction, delta));
    }

    @Override
    public void reportPlayerReputationChange(PersonAPI person, float delta) {
        LOG.info(String.format("GearIn listener: reputation change vs %s by %+.2f",
                person.getName().getFullName(), delta));
    }

    private static String winnerName(CampaignFleetAPI fleet) {
        if (fleet == null) {
            return "none";
        }
        return fleet.getName() + " (" + fleet.getFaction().getId() + ")";
    }
}
```

对应关系：

- `extends BaseCampaignEventListener`：全接口 21 个方法的空实现，本类只覆写关心的 11 个事件方法 (市场/贸易/战斗/舰队/声望五个事件边界)。
- `super(false)`：不在构造器自注册 (区别于 `permaRegister=true` 的自动注册写法)，注册统一收敛到调用点的注册块，符合本教程的注册纪律。
- `REGISTER_MARK`：persistent data 注册标记键，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；它标记 "监听器 + 生命周期脚本" 成对注册，由调用点统一检查。
- 注册入口：调用点的注册块内 `sector.addListener(listener)`(通道一持久列表)。
- 回调全部只读公开 API 入参并打日志：`market.getName()` / `getSize()`、`transaction.getMarket().getName()` / `getCreditValue()`、`battle.isPlayerInvolved()`、`result.didPlayerWin()`、`loot.getStacksCopy().size()`、`fleet.getFaction().getId()`、`person.getName().getFullName()`。

### GearInListenerLifecycleScript(通道一：生命周期脚本)

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 完整最小例：监听器生命周期脚本（EveryFrameScript 角色）。
 *
 * 与 GearInCampaignListener（监听器角色）配对：原组合类拆分出的脚本半身。
 * 注册入口：onGameLoad 门控块内与监听器一同注册（sector.addScript(this)）。
 * 职责：按游戏天累计挂载时长，daysToLive >= 0 且到期时显式
 * sector.removeListener(listener) 并自停 ——isDone() 只清理脚本列表，
 * 监听器注册表必须单独移除，否则事件继续派发且实例随存档序列化（硬规则）。
 */
public class GearInListenerLifecycleScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInListenerLifecycleScript.class);

    /** 受管监听器：到期时由本脚本显式移除。 */
    private GearInCampaignListener listener;

    /** 挂载以来的游戏天数（精确累加，保留小数）；随存档序列化，读档后继续累计。 */
    private float daysActive = 0f;

    /** 演示参数：挂载满多少游戏天后自停并移除监听器；< 0 表示常驻不停止。 */
    private int daysToLive = -1;

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

    public GearInListenerLifecycleScript(GearInCampaignListener listener) {
        this.listener = listener;
    }

    /** 修改自停天数；改回 -1 恢复常驻。 */
    public void setDaysToLive(int daysToLive) {
        this.daysToLive = daysToLive;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        // amount 是上一帧真实秒数；经时钟换算才是本帧的游戏天数（10 真实秒 = 1 游戏日）。
        daysActive += clock.convertToDays(amount);

        if (daysToLive >= 0 && daysActive >= daysToLive) {
            done = true;
            // 硬规则：脚本结束不等于监听器移除。isDone() 只清理脚本列表，
            // 监听器注册表必须显式 removeListener，否则事件继续派发。
            sector.removeListener(listener);
            LOG.info(String.format(
                    "GearIn listener: removed itself after %.1f game days (isDone()=true)", daysActive));
        }
    }
}
```

对应关系：

- `implements EveryFrameScript`：脚本半身提供 `isDone` / `runWhilePaused` / `advance`，按游戏天累计挂载时长，是本节 "生命周期脚本" 角色。
- 构造器持有受管监听器引用；`setDaysToLive(int)` 是**本节演示参数，归属脚本类**——`< 0` 常驻、`>= 0` 到期自停并移除监听器。
- `advance` 里 `clock.convertToDays(amount)`：`amount` 是上一帧真实秒数，游戏天累加必须经时钟换算 (10 真实秒 = 1 游戏日)。
- `sector.removeListener(listener)`：脚本半身 `done=true` 的同时显式移除监听器 —— 规则 5 的演示 (脚本结束 ≠ 监听器移除)。
- 注册入口：调用点的注册块内 `sector.addScript(script)`(脚本列表)；`addScript` 不去重，这正是注册块需要整体判断的缘由之一。

### GearInLocationChangeListener(通道二：类型化监听器)

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/**
 * 完整最小例：经 ListenerManagerAPI 注册的类型化监听器（typed listener）。
 *
 * 与 GearInCampaignListener（Sector 级 CampaignEventListener 通道）不同，本类走的是
 * 第二条通道：SectorAPI.getListenerManager().addListener(listener, isTransient) 注册、
 * removeListener(this) 移除，事件由 ListenerUtil 按接口类型派发
 * （本类对应 CurrentLocationChangedListener，派发点在生涯引擎的 advance
 * 检测到当前地点变化时）。
 *
 * 注册入口：onGameLoad（调用点片段见页面正文），用 transient=true 注册 ——
 * transient 监听器不随存档保存（ListenerManagerAPI 的 transientListeners 仓库读档时
 * 重建为空），因此读档后必须重新注册；调用点用 hasListenerOfClass 判空
 * 再补注册即可覆盖旧档。
 *
 * 演示点：
 * 1. reportCurrentLocationChanged(prev, curr)：玩家当前地点变化（进出超空间、进入星系）时触发；
 * 2. 自移除：触发满 reportsLeft 次后 removeListener(this) 并打日志 —— 监听器在不再需要时
 *    必须在合适时机移除，否则事件持续派发（硬规则）；
 * 3. 全部字段可随存档序列化；本类字段不涉及瞬态句柄。
 */
public class GearInLocationChangeListener implements CurrentLocationChangedListener {

    private static final Logger LOG = Global.getLogger(GearInLocationChangeListener.class);

    /** 演示参数：最多触发多少次后自移除。 */
    private int reportsLeft = 5;

    public GearInLocationChangeListener() {
        // 空构造：注册由调用方执行（onGameLoad，见页面调用点片段）
    }

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        LOG.info(String.format("GearIn listener: current location changed: %s -> %s",
                locationName(prev), locationName(curr)));

        reportsLeft--;
        if (reportsLeft <= 0) {
            Global.getSector().getListenerManager().removeListener(this);
            LOG.info("GearIn listener: location listener removed itself after 5 reports");
        }
    }

    private static String locationName(LocationAPI location) {
        return location == null ? "null" : location.getName();
    }
}
```

对应关系：

- `implements CurrentLocationChangedListener`：类型化接口只有一个方法 `reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr)`，派发在引擎检测到当前地点变化时经 `ListenerUtil` 完成。
- `reportsLeft` 自减到 0 后 `getListenerManager().removeListener(this)`：规则 3(在合适时机移除) 的演示。
- transient 注册 + `hasListenerOfClass` 判空补注册：规则 4(读档后重新注册) 的演示。
- 注册入口：调用点片段里 `getListenerManager().addListener(new GearInLocationChangeListener(), true)`(通道二 transient 仓库)。

调用点 (工程片段 —— 方法所在类属既有 MOD 插件工程，本节只演示调用，不改动原文件)：

```java
    // GearInModPlugin.onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    SectorAPI sector = Global.getSector();

    // 通道一：监听器 + 生命周期脚本配对注册。addListener 按同一性去重，但 addScript 不去重，
    // 因此整个注册块必须用 REGISTER_MARK 门控（硬规则 2）。
    if (!sector.getPersistentData().containsKey(GearInCampaignListener.REGISTER_MARK)) {
        sector.getPersistentData().put(GearInCampaignListener.REGISTER_MARK, true);
        GearInCampaignListener listener = new GearInCampaignListener();
        GearInListenerLifecycleScript script = new GearInListenerLifecycleScript(listener);
        sector.addListener(listener);   // 监听器：通道一持久列表
        sector.addScript(script);       // 生命周期脚本：脚本列表
    }

    // 通道二：类型化监听器，transient 注册。读档后 transient 仓库被清空，
    // hasListenerOfClass 判空补注册（硬规则 4）；ListenerManagerAPI 不去重，别去掉这个判空。
    if (!sector.getListenerManager().hasListenerOfClass(GearInLocationChangeListener.class)) {
        sector.getListenerManager().addListener(new GearInLocationChangeListener(), true);
    }
```

想演示自停，在注册前先设参数：

```java
    GearInCampaignListener listener = new GearInCampaignListener();
    GearInListenerLifecycleScript script = new GearInListenerLifecycleScript(listener);
    script.setDaysToLive(30); // 约 30 个游戏日（约 300 真实秒）后自停并移除监听器
    sector.addListener(listener);
    sector.addScript(script);
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用既有工程)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含以下 class 条目 (可用 `jar tf` 核对)：
- `data/scripts/gearin/campaign/GearInCampaignListener.class`
- `GearInListenerLifecycleScript.class`
- `GearInLocationChangeListener.class`

本节三个代码块与 fixture 源码逐字一致；fixture 编译验收命令见下方验证方案。

### 验证方案

本节成果分四组验证：工程编译与构建、事件边界、读档与自停、故障注入。前置数据：既有 MOD 布局 (C06-S01)、`GearInModPlugin`(C06-S02，含本节调用点片段) 与本节三个类。入口依次为新建生涯、市场、战斗、声望、跳跃与读档；观察位置统一为游戏日志 (`Starsector/starsector-core/starsector.log`) 中的 `GearIn listener` 条目。

**1. fixture 编译**

- 操作：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC10s02Java --no-daemon`。
- 预期结果：以 `BUILD SUCCESSFUL` 结束 (退出码 0)。

**2. 游戏构建**

- 操作：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`。
- 预期结果：`BUILD SUCCESSFUL`；`jar tf jars/GearIndustry.jar` 能看到三个类的 `.class` 条目。

**3. 新建生涯与事件边界**

- 操作：新建生涯，正常游玩，再按事件边界逐个触发：打开/关闭市场、买卖货物、与敌对舰队交战、改变声望。
- 预期结果：未触发事件时日志不出现周期性的 `GearIn listener` 输出 (监听器是事件驱动，不产生周期日志)—— 这本身是 "注册成功且无副作用" 的判据。触发后的日志按事件核对：
  - 打开市场交互：`GearIn listener: market opened: <市场名> (size N)`；关闭：`GearIn listener: market closed: <市场名>`。
  - 市场买入/卖出 (或整备界面购买舰船/武器/战机)：每次结算出现 `GearIn listener: trade at <市场名>, credit value <数值>`。
  - 遭遇战：战斗阶段出现 `GearIn listener: battle occurred, player involved=true, winner=...` 与 `GearIn listener: battle finished, ...`(玩家遭遇战各一次)；战斗结算后出现 `GearIn listener: player engagement resolved, didPlayerWin=<true/false>`；出现战利品时 `GearIn listener: encounter loot generated, N stacks`。
  - 改变与势力的声望 (完成任务、送礼、攻击势力目标)：`GearIn listener: reputation change vs faction <id> by +X.XX`。
  - 任何舰队入地/离场 (包括 AI 舰队)：`fleet spawned` / `fleet despawned, reason=<REASON>`—— 活动星区里这类日志会比较频繁，属正常。
  - 启动与游玩全程无 `gear_in` 引起的 `ERROR`。

**4. 位置监听器**

- 操作：连续跳跃 5 次，进出超空间或星系。
- 预期结果：前 5 次出现 `current location changed`；第 5 次后出现 `location listener removed itself after 5 reports`，第 6 次起不再出现。

**5. 读档**

- 操作：保存后读档，再次跳跃并打开市场；随后退出到主菜单，再进档一次。
- 预期结果：无重复注册 —— 无 `trade at` 成双 (`REGISTER_MARK` 注册标记 + `addListener` 去重)；位置监听器因 transient 读档清空、由 `hasListenerOfClass` 判空重新注册 (读档后继续出现位置变化日志且不叠加，计数从 5 次重新开始)；退出到主菜单后生涯引擎停止、日志停止，再进档按第 3、4 项继续。

**6. 自停演示**

- 操作：把 `daysToLive` 设为 30 重新构建，进档等待约 300 真实秒；演示完改回 -1 恢复常驻。
- 预期结果：挂载约 300 真实秒后出现：

   ```text
   GearIn listener: removed itself after 30.0 game days (isDone()=true)
   ```

   之后所有 `GearIn listener` 事件日志停止 —— 脚本半身被引擎从脚本列表移除 (`hasScript(GearInListenerLifecycleScript.class)` 为 `false`)，监听器被 `removeListener` 移除 (事件不再派发)。这同时验证了规则 5 的修正路径。

**7. 暂停语义**

- 操作：暂停游戏中打开市场。
- 预期结果：监听器不受暂停影响 (事件驱动，暂停中打开市场仍会触发 `market opened`)；若用 `addListenerWithTimeout` / `reRegister` 演示超时，暂停期间超时不计时 (游戏天不前进)，恢复后继续。

**8. 故障注入**

- 操作 8a：注释掉调用点片段的 `addListener` 行 (保留 `addScript`)，重启新档。
  - 预期结果：`market opened` 等事件日志全部缺失、无报错 (对应故障分析第 1 条)；改回后确认恢复。
- 操作 8b：把通道二的 `addListener(..., true)` 改为不加 `hasListenerOfClass` 判空并去掉 transient 参数，读档两次。
  - 预期结果：位置变化日志逐次叠加 (对应故障分析第 2、3 条)；改回后确认恢复。
- 操作 8c：注释掉脚本 `advance` 里的 `sector.removeListener(listener)`(保留 `done = true`)，`daysToLive = 30`。
  - 预期结果：自停日志出现后事件日志继续出现 (僵尸监听器，对应故障分析第 4 条)；改回后确认恢复。

通过信号：操作 1–7 全部符合；操作 8 的三组注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件 (`tutorial-fixtures/build.gradle` 等) 做过修改。

## 故障分析

十个故障按症状组织：每个故障先写您实际看到的现象，再给触发条件、断链位置与核对顺序。验证方案的故障注入 8a–8c 分别对应第 1、2/3、4 条。

1. **监听器完全没有日志**。
   - 症状：对应事件没有任何 `GearIn listener` 日志且无报错；或启动直接中止，日志里的 NPE 堆栈指向 `onApplicationLoad`。
   - 触发：忘了调用 `addListener` / `getListenerManager().addListener`，或把注册写进 `onApplicationLoad`(此刻 `Global.getSector()` 为 null)。
   - 原因：注册入口错误或缺失，事件派发时注册表里没有该监听器。
   - 修正：把注册放进 `onGameLoad` / `onNewGame` 的注册块内 (规则 1)，重新进档后事件日志应出现。
2. **同一事件回调双份**。
   - 症状：`market opened` 等同一事件同一来源出现两条日志，且读档次数越多越成双。
   - 触发：同一对象既 `sector.addListener(this)` 又 `getListenerManager().addListener(this)`(双通道各注册一份)，或配对注册块没有注册标记，每次读档 `addScript` 再挂一份 (脚本半身叠加)。
   - 原因：两条通道各自派发同一个事件，或者脚本列表没有去重、注册块整体缺一次性判断。
   - 修正：一个对象只走一条通道；注册块整体用 `REGISTER_MARK` 判断是否已注册 (必须遵守的规则 2)。
3. **读档后事件静默消失**。
   - 症状：读档后对应事件无任何日志、无任何报错。
   - 触发：transient 监听器 (`addTransientListener` 或 `addListener(x, true)`) 没有在读档回调里重新注册。
   - 原因：读档时两条通道的 transient 仓库都被清空，监听器已不在注册表。
   - 修正：用 `hasListenerOfClass` 判空补注册 (本节调用点片段的通道二写法)，读档后事件日志应恢复。
4. **脚本自停后事件仍在触发 (僵尸监听器)**。
   - 症状：`removed itself ... (isDone()=true)` 日志出现后，`market opened` 等事件日志继续出现。
   - 触发：生命周期脚本只置 `done = true`，没有在到期路径里 `removeListener(listener)`。
   - 原因：`isDone()` 只让引擎清理脚本列表，监听器注册表不会自动移除 —— 脚本半身被清理了，监听器还在注册表里。
   - 修正：在到期路径内显式 `removeListener(listener)`(本节示例即如此)，复验自停日志出现后事件日志停止。
5. **超时监听器 "永不超时" 或超时过慢**。
   - 症状：挂机等待超时无效果，或按真实秒预期时超时明显偏慢。
   - 触发：把 `addListenerWithTimeout` 的 `daysToKeep` 当成真实秒，或以为暂停也在计时。
   - 原因：`daysToKeep` 的单位是游戏天 (10 真实秒 = 1 游戏日)，且只在未暂停时扣减；暂停期间 `listenersWithTimeout` 不推进。
   - 修正：按 "游戏天、仅未暂停时扣减" 理解并使用，恢复游戏后超时继续扣减。
6. **`isDone()` 恒返回 true**。
   - 症状：脚本半身第一帧就被移除，`advance` 一次都不执行，自停日志从不出现，事件却持续回调。
   - 触发：完成标记写反或占位返回 `true`。
   - 原因：引擎第一帧先查 `isDone`，返回 true 时直接移除脚本，`removeListener` 自然没机会执行 —— 脚本半身消失、监听器半身残留 (又是僵尸监听器，且无自停日志)。
   - 修正：`done` 初始 `false`，仅在条件满足时置 `true`。
7. **事件日志过频刷屏**。
   - 症状：日志被无关舰队淹没，找不到玩家相关事件。
   - 触发：`reportFleetSpawned` / `reportFleetDespawned` 覆盖所有 AI 舰队，活动星区每帧多条。
   - 原因：回调没有按玩家相关过滤。
   - 修正：回调内过滤 (如 `fleet.isPlayerFleet()` 或按势力 id)，把玩家关心的事件单独归类。
8. **战斗计数偏高**。
   - 症状：用 `reportBattleOccurred` 统计的 "玩家战斗次数" 与预期不符。
   - 触发：`reportBattleOccurred` 只对 AI vs AI 每个自动结算轮调用一次、对玩家遭遇战一次 (API javadoc)，但玩家舰队**在战斗中**若包含多轮自动结算 (如玩家脱战后 AI 继续互打)，不会产生 player 标记。
   - 原因：统计口径选错了回调 —— `reportBattleOccurred` 反映战斗过程，不反映玩家结算结果。
   - 修正：玩家战斗统计用 `reportPlayerEngagement`(`EngagementResultAPI.didPlayerWin()`)；战斗过程观察用 `reportBattleOccurred` 且以 `battle.isPlayerInvolved()` 过滤。
9. **双通道混淆导致业务重复**。
   - 症状：同一市场事件业务逻辑执行两次。
   - 触发：为同一业务同时实现 `CampaignEventListener.reportPlayerOpenedMarket` 与 `ColonyInteractionListener.reportPlayerOpenedMarket` 并各注册一份。
   - 原因：两个通道各自派发同一个事件，同一对象被回调双份。
   - 修正：同一业务只选一条通道；确需两通道时明确分工 (如一个做统计、一个做 UI)。
10. **旧档永远不注册监听器**。
    - 症状：旧档读档后无任何 `GearIn listener` 日志、监听器缺失且无报错。
    - 触发：把注册放进既有 `GearInModPlugin.CLASS_MARK` 注册块内 (该键在新档首次进入时已置位)。
    - 原因：旧档缺少本节自己的注册键，被既有注册块整体跳过，注册代码永远不会执行。
    - 修正：每个注册项用各自的 persistent data 键 (本节 `REGISTER_MARK`)，旧档缺键即视为首次注册。

## 总结

本节建立了一条从注册到回调的完整判断链：两条通道决定监听器存哪里、怎么去重、由谁派发，21 个方法决定哪些事件值得监听，生命周期规则决定注册与移除的时机。动手前先回答三个问题：走哪条通道、什么时候注册、什么时候移除 —— 三个答案都落在 `onGameLoad` 的注册块和配对脚本里，而不是散落在构造器或 `onApplicationLoad`。

排错时沿用 "触发点 → 派发者 → 回调签名" 的边界分析法：回调没触发，先确认事件真的发生 (边界点)，再确认派发者遍历了注册表，最后核对签名与注册时机。这套方法可以迁移到同一家族的其他事件驱动机制：能力与对话框事件、战斗结算后的流程钩子、经济与声望的联动处理，都能沿这条链逐段核对 "回调为什么没触发"。

最容易反复踩中的三个判断：持久监听器随存档序列化，字段必须可序列化；transient 监听器读档后必然清空，必须重新注册；脚本 `isDone()` 只清理脚本，监听器必须显式移除。把这三条连同 "游戏天、暂停冻结" 的超时语义写进注释，可以少背许多与您无关的报错黑锅。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignEventListener`：公开生涯监听器接口，全接口 21 个方法，含 `FleetDespawnReason` 枚举 (6 值)。
- `com.fs.starfarer.api.campaign.SectorAPI`：通道一注册入口 (`addListener` / `addTransientListener` / `addListenerWithTimeout` / `removeListener` / `getAllListeners`) 与 `getListenerManager()`。
- `com.fs.starfarer.api.campaign.BaseCampaignEventListener`：全方法空实现基类；`permaRegister` 构造器与 `reRegister(daysToKeep)`。
- `com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript`：监听器与 `EveryFrameScript` 绑定在同一对象的基类 (原版 `CoreScript` 同形态)。
- `com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI`：通道二类型化仓库，7 个方法。
- `com.fs.starfarer.api.campaign.listeners.ListenerUtil`：类型化事件派发静态方法族 (按 `getListeners(X.class)` 遍历)。
- `com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener`：本节类型化监听器接口 (单方法)。
- `com.fs.starfarer.api.util.TimeoutTracker`：`addListenerWithTimeout` 的底层超时跟踪器。
- `com.fs.starfarer.api.campaign.PlayerMarketTransaction`：贸易事件的取值入口 (`getMarket` / `getSubmarket` / `getCreditValue` / `getLineItems`)。
- `com.fs.starfarer.api.campaign.BattleAPI`：`isPlayerInvolved()`；`com.fs.starfarer.api.combat.EngagementResultAPI`：`didPlayerWin()`。
- `com.fs.starfarer.api.impl.campaign.CoreScript`：原版 "监听器 + 脚本" 实例，组合形态参考。
- `tutorial-fixtures/build.gradle`：`compileC10s02Java` 任务 (source set `s10p02` → 本单元 fixture 目录)。
- `tutorial-fixtures/units/C10-S02/src/main/java/data/scripts/gearin/campaign/`：本节三个类的 fixture 源码 (与正文代码块逐字一致)。
