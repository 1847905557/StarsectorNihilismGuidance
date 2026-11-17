# 读取生涯世界

刚从战斗脚本切到生涯层时，最容易让人产生错觉的是 `EveryFrameScript` 那几个眼熟的方法名 —— 看起来好像只是把战场的 `advance(amount)` 搬到了星图上。

真正棘手的是存档那一刻：生涯对象图会被 XStream 直接序列化进存档。哪类脚本会写进存档、哪类脚本读档即失，外加跨星系坐标的核算边界，这几条底线理清以后，后面的生涯逻辑才不至于一读档就散架。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C3-S3] 的 Nipher 星系与 `GearIn_planet1` 市场；
- [C7-S1] 的 `onNewGame()`、`onGameLoad(boolean newGame)` 与 `getPersistentData()`；
- [C7-S2] 的 `Global.getSector()` 访问时机。

## 星区对象树与核心入口

进入生涯后，`Global.getSector()` 返回代表当前存档世界的 `SectorAPI`。整个星区的对象围绕它分层挂载：

| 类别 | `SectorAPI` 入口方法 | 返回对象与用途 |
| --- | --- | --- |
| 地点 | `getStarSystem(name)`、`getStarSystems()`、`getHyperspace()`、`getCurrentLocation()` | 取得指定恒星系、超空间或玩家当前所在的 `LocationAPI` |
| 实体查询 | `getEntityById(id)` | 在全星区范围内按唯一 ID 查找 `SectorEntityToken`，未找到返回 `null` |
| 经济与市场 | `getEconomy().getMarket(id)`、`getEconomy().getMarketsCopy()` | 按市场 ID 取得 `MarketAPI`，或遍历全星区所有市场 |
| 玩家状态 | `getPlayerFleet()`、`getPlayerPerson()`、`getPlayerStats()` | 取得玩家舰队实体、玩家人物对象与技能统计 |
| 势力 | `getFaction(id)`、`getPlayerFaction()`、`getAllFactions()` | 取得指定势力或玩家所属势力的 `FactionAPI` |
| 时间与管理 | `getClock()`、`getIntelManager()`、`getListenerManager()` | 取得生涯时钟、情报管理器与生涯事件监听器管理器 |
| 存档键值表 | `getPersistentData()`、`getMemoryWithoutUpdate()` | 取得随存档保存的公共数据 `Map` 与全局 `MemoryAPI` ([C10-S2]) |

在生涯对象之间导航时，有两条高频链路几乎每写一个脚本都会用到：

1. **从市场找回天体实体**：[C3-S3] 注册的市场 ID 是 `"GearIn_planet1"`，通过 `sector.getEconomy().getMarket("GearIn_planet1")` 拿到 `MarketAPI` 后，调用 `market.getPrimaryEntity()` 即可取回它附着的那颗 Nipher Prime 行星实体 (`SectorEntityToken`)；反过来，从任何绑定了市场的实体上调用 `entity.getMarket()` 也能取回对应的 `MarketAPI`。
2. **先判同一地点，再算距离**：每个恒星系 (`StarSystemAPI`) 与超空间 (`Hyperspace`) 都是各自独立的二维平面 (`LocationAPI`)。一艘停在超空间 `(0, 0)` 的舰队与一颗位于 Nipher 星系 `(0, 0)` 的行星，它们的 `getLocation()` 数值完全相同，却根本不在同一个空间里。因此调用 `Misc.getDistance(a, b)` 之前，必须先核对两者的 `getContainingLocation()` 是否指向同一个地点：

```java
SectorEntityToken planet = market.getPrimaryEntity();
CampaignFleetAPI playerFleet = sector.getPlayerFleet();
if (playerFleet == null || playerFleet.getContainingLocation() != planet.getContainingLocation()) {
    return;
}
float distanceFromSurface = Misc.getDistance(playerFleet, planet) - planet.getRadius();
```

`Misc.getDistance(a, b)` 计算的是两个实体中心点之间的距离；若要判断舰队距离行星表面有多远，还需减去 `planet.getRadius()`。但一般不会特意这样。

## 生涯时钟与经过天数

`sector.getClock()` 返回 `CampaignClockAPI`。生涯里所有与 "过了多少天" 有关的逻辑都必须走生涯时钟，不能用切帧的秒数自己累加，否则一旦玩家加快时间流速或读档，计时就会同游戏日历脱节：

| 方法 | 作用 |
| --- | --- |
| `getCycle()`、`getMonth()`、`getDay()` | 当前星历的周期 (年)、月、日整数 |
| `getDateString()` | 格式化的当前日期字符串 (如 `Mar 15, c206`；若要 `206.3.15` 格式调 `getShortDate()`) |
| `getTimestamp()` | 返回 `long` 类型的当前绝对时间戳，用于记录某个事件发生的时刻 |
| `getElapsedDaysSince(long timestamp)` | 计算从给定时间戳至今经过的生涯天数 (`float`) |
| `convertToDays(float seconds)` | 将 `advance(amount)` 传入的现实秒数换算为当前流速下的生涯天数 |

**不要拿两个 `getTimestamp()` 的差值直接当成天数或秒数使用。** `getTimestamp()` 返回的是引擎内部的原始时钟刻度，记录起点时把 `long start = clock.getTimestamp()` 存起来，后续一律调用 `clock.getElapsedDaysSince(start)` 获取经过的生涯天数。

## 生涯每帧脚本与存档序列化

生涯中的持续逻辑实现 `com.fs.starfarer.api.EveryFrameScript` 接口：

```java
public interface EveryFrameScript {
	boolean isDone();
	boolean runWhilePaused();
	void advance(float amount);
}
```

- `isDone()` 返回 `true` 时，引擎在当前帧将该脚本从运行列表中移除；
- `runWhilePaused()` 返回 `false` 时，玩家在生涯界面暂停时不调用 `advance(...)`；除非脚本专门处理暂停期间的 UI 刷新，否则一律返回 `false`；
- `advance(float amount)` 每帧调用一次。由于生涯脚本通常不需要按 60 帧的频率轮询全星区对象，实战中普遍配合 `IntervalUtil` (如 `new IntervalUtil(0.5f, 1f)`) 将检查频率降到每半秒至一秒一次。

把 `EveryFrameScript` 挂进星区时，`SectorAPI` 提供了两个名字相近、存档行为却截然相反的方法：

| 注册方式 | 是否写进存档 XML | 读档后的状态 | 适用场景与代价 |
| --- | --- | --- | --- |
| `sector.addScript(script)` | **是** (连同实例字段一起序列化) | 由存档反序列化恢复，无需在 `onGameLoad` 重新注册 | 适合持有复杂运行期状态的长周期管理器 ([C11-S1])；代价是**一旦在后续版本重命名或删除该类，旧存档读档会直接因找不到类而报错** |
| `sector.addTransientScript(script)` | **否** | 读档后消失，需在 `onGameLoad` 中重新挂载 | 适合轮询检测、全局监听等逻辑本身无须进档的脚本；重构或改名不会破坏旧存档，但**脚本字段在读档后会全部归零** |

这也解释了为什么在 `addTransientScript(...)` 注册的脚本里，绝对不能用 `private boolean triggered = false;` 这样的实例字段来记录 "某件事已经发生过" —— 玩家只要按一下 `F5` / `F9` 存取档，`onGameLoad(...)` 就会 `new` 出一个全新的脚本实例，`triggered` 重新变回 `false`，一次性逻辑就会再次触发。

在 `GearInModPlugin.onGameLoad(...)` 中挂载瞬态脚本时，标准写法是同时检查存档中的完成标记与 `hasTransientScript(...)`：

```java
SectorAPI sector = Global.getSector();
if (!sector.hasTransientScript(MyCampaignScript.class)) {
    sector.addTransientScript(new MyCampaignScript());
}
```

## 存档公共数据：getPersistentData()

既然瞬态脚本的字段在读档后会清空，跨读档保留的数据就得放进随存档保存的容器里。Starsector 提供了两套持久化容器：一套是 [C7-S1] 见过的 `sector.getPersistentData()`，另一套是下一节 [C10-S2] 专门展开的 `MemoryAPI`。

`sector.getPersistentData()` 返回一个全局的 `Map<String, Object>`，存进去的键值对会原样写进存档：

```java
Map<String, Object> data = sector.getPersistentData();
if (!data.containsKey("GearIn_reward_start_timestamp")) {
    data.put("GearIn_reward_start_timestamp", sector.getClock().getTimestamp());
}
long start = (Long) data.get("GearIn_reward_start_timestamp");
```

什么时候用 `getPersistentData()`，什么时候用 [C10-S2] 的 `MemoryAPI`？判断标准很简单：

- **只在您自己的 Java 代码内部读写的长效数据** (如倒计时起点 `Long` 时间戳、已初始化标记、自定义状态表)，放进 `sector.getPersistentData()`，键名带上 `GearIn_` 前缀防止与其他 MOD 冲突；
- **需要按生涯天数自动过期、需要挂在具体市场/实体/舰队身上、或者后续要被 `rules.csv` 对话规则与任务系统直接读取的标记**，一律放进 [C10-S2] 的 `MemoryAPI`。

::: caution 严禁往存档容器里塞临时 UI 对象或跨生命周期引用

无论是 `sector.getPersistentData()`、`MemoryAPI`，还是通过 `sector.addScript(...)` 进档的持久脚本字段，最终都会在玩家保存时被 XStream 递归写进 XML。如果把战场引擎对象 (`CombatEngineAPI`、`ShipAPI`)、图形贴图 (`SpriteAPI`) 或对话框插件存进这些容器，轻则存档体积暴涨，重则直接触发序列化异常让玩家连档都存不上。

:::

## 常见问题

- **在 `onApplicationLoad()` 中调用 `Global.getSector().getEconomy()` 报空指针或拿不到市场**：游戏启动阶段尚未加载任何生涯存档，星区经济与星系还未建立。
    - **解决方案**：所有针对星系、市场、势力关系与玩家舰队的读取，一律放在 `onNewGame()`、`onGameLoad(...)` 或生涯脚本的 `advance(...)` 中执行。

- **`Misc.getDistance(playerFleet, planet)` 在玩家身处超空间时也算出了小于阈值的距离**：没有先判断两个实体是否位于同一个 `LocationAPI`。
    - **解决方案**：计算距离前先确认 `playerFleet.getContainingLocation() == planet.getContainingLocation()`。

- **删除或重命名某个 `EveryFrameScript` 类后，旧存档无法加载并报 `CannotResolveClassException`**：该脚本此前是通过 `sector.addScript(...)` 注册的，类名已被写入存档 XML。
    - **解决方案**：开发期可能改名的轮询脚本优先使用 `sector.addTransientScript(...)`；已通过 `addScript(...)` 进档的类如需废弃，应保留空壳类使 `isDone()` 返回 `true` 以兼容旧存档。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorAPI`：星区总入口、脚本注册 (`addScript` / `addTransientScript`) 与 `getPersistentData()`。
- `com.fs.starfarer.api.campaign.LocationAPI`：恒星系与超空间的公共父接口。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：生涯时间戳与经过天数换算。
- `com.fs.starfarer.api.EveryFrameScript`：生涯每帧脚本接口。
- `com.fs.starfarer.api.util.IntervalUtil`：按随机间隔节流每帧轮询的工具类。
