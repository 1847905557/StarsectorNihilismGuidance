# 编写生涯逐帧脚本

逐帧脚本，`EveryFrameScript`，几乎一切需要计时的复杂效果都与之相关。

就这么简单。但考虑到需要计时的效果五花八门，多种多样，一个 MOD 里过半会在生涯里 "运行"(考虑到 运行 某种意义上暗示了时间的流逝) 的脚本都将基于它，说它是最基本最重要的脚本类别都不为过。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C7-S1] 的 `onNewGame()` 与 `onGameLoad()` 分工；
- [C11-S1] 的巡逻舰队管理器；
- [C12-S6] 的 Warfront 条件与 [C12-S1] 的市场 ID。

## 三个方法的调用频率

```java
public interface EveryFrameScript {
    boolean isDone();
    boolean runWhilePaused();
    void advance(float amount);
}
```

| 方法 | 调用时机 | 返回值 |
| ---- | -------- | ------ |
| `advance(amount)` | 随登记它的对象推进，`amount` 为该次推进经过的现实秒数 | 无 |
| `isDone()` | 与 `advance(...)` 同一轮，排在它之后 | `true` 时引擎把实例移出脚本表 |
| `runWhilePaused()` | 原版脚本以它的返回值为常量使用 | 暂停期间是否继续调用 `advance(...)` |

**"每帧" 这个说法只在登记于 `SectorAPI` 时成立。** 能收 `EveryFrameScript` 的接口不止一个：`SectorAPI`、`LocationAPI`、`SectorEntityToken`、`FleetOrStubAPI` 上都有 `addScript(...)`。`LocationAPI` 的注释写明了差别：脚本随该地点的 `advance()` 运行，而**不是当前地点时，每秒推进的次数可能更少**。同一个脚本登记在不同对象上，节奏就不同。

`isDone()` 与 `advance(...)` 的先后有据可查：`BaseEventManager.advance(...)` 遍历活动事件时先调 `advance(amount)`，再问 `isDone()`，问出 `true` 的移出列表。树内可见的调度循环基本都是这个次序。

`runWhilePaused()` 的读取点看不到。全树对它的调用表达式为零，只有各处 `EveryFrameScript` 实现的覆写，读它的是引擎核心。原版脚本一律把它当常量：返回 `false` 就一直返回 `false`，不在运行期改变。**它管的是脚本的长期形态，与当前状态无关**；要让脚本按条件启停，只能在 `advance(...)` 里分支，或者换一个实例。

`isDone()` 的结论由脚本自己给出。引擎不做推断，`advance(...)` 没有产生任何副作用也不会被回收，代价要到几十个生涯天之后才看得出来。

## 帧时间换算成生涯天数

`amount` 是这一帧实际过去的时间。玩家开时间加速，同一现实秒对应的游戏时间就变长；把它直接累加，倒计时在加速与不加速时走得不一样长。换算交给时钟，取到的是生涯天数。

```java
float days = Global.getSector().getClock().convertToDays(amount);
```

`CampaignClockAPI` 另有 `getDay()`、`getTimestamp()`、`getElapsedDaysSince(long)`。脚本里所有跨度判断都取生涯天数，与存档记录、界面日期同一个基准。

## 登记方式与存档

| 方法 | 序列化 | 读档之后 |
| ---- | ------ | -------- |
| `SectorAPI.addScript(script)` | 是，字段一并写入 | 原实例，字段保持 |
| `SectorAPI.addTransientScript(script)` | 否 | 不存在，须由 `onGameLoad(...)` 重新登记 |

配套 `hasScript(Class)`、`hasTransientScript(Class)`、`removeScriptsOfClass(Class)`、`getScripts()`。

选择只看一件事：状态是不是存在字段里。存在字段里就必须进存档，否则读档时连同计数一起丢掉；状态写在 Memory 里的脚本没有可存的东西，临时登记即可，读档时重新挂一份。

随存档保存的代价在发布之后才收。类名与包名一起写入，改名会让旧存档找不到类；玩家卸载 MOD，引用它的存档读不出来。**已发布且随存档保存的脚本类，名字就是它的存档格式。** 字段同理：市场、实体、人物都能按 ID 重新取，入档只会在读档后留下失效引用。

## 原版的舰队管理器

来看一个原版的例子。

`SourceBasedFleetManager` 随存档保存，字段如下：

```java
protected List<CampaignFleetAPI> fleets = new ArrayList<CampaignFleetAPI>();
protected SectorEntityToken source;
protected int minFleets;
protected int maxFleets;
protected float respawnDelay;
protected float destroyed = 0;
```

没有倒计时字段。补充延迟由 `destroyed` 承担：`reportFleetDespawnedToListener(...)` 里递加，`advance(...)` 里按 `respawnDelay` 递减。**延迟用倒计时表示还是用衰减计数表示，差别在一帧之内击毁多支的时候** —— 计数逐支累加，倒计时只减一轮。原版在这里选计数，因为它要处理的正是舰队成批消失。

`advance(...)` 的顺序：

1. 换算天数，`destroyed` 衰减；
2. 从 `fleets` 移除已消失的舰队；
3. 源点被毁则 `source = null`，此后不再生成；
4. 依玩家与源点的距离定上限：近于 `thresholdLY` 时在 `minFleets` 与 `maxFleets` 之间插值，远于此取 `minFleets`；
5. 超出上限，对足够远的舰队调用 `despawn(...)`；
6. 不足，生成一支，每帧至多一支，并 `fleet.addEventListener(this)` 接收它的消失回调。

第 5 步与第 6 步的顺序不能互换。先回收再生成，远端那批先解散；反过来写，同一帧里 `fleets` 既超上限又添了新成员，要等下一轮 `despawn(...)` 才追得回来。

`isDone()` 读字段 `done`，源点消失且 `fleets` 为空时置位；`runWhilePaused()` 返回 `false`。两处都以字段承载，方法里不现算。

## 把战事周期写进 Foundry

然后想想我们该怎么用上它。

Foundry 要的是周期：战事持续一段时间，平息一段时间，交替往复。阶段存在脚本的字段里，因此用 `addScript(...)` 登记。

```java
daysLeft -= sector.getClock().convertToDays(amount);
if (daysLeft > 0f) {
    return;
}
```

归零之后切换条件：

```java
if (market.hasCondition(GearInWarfrontCondition.CONDITION_ID)) {
    market.removeCondition(GearInWarfrontCondition.CONDITION_ID);
    daysLeft = CALM_DAYS;
} else {
    market.addCondition(GearInWarfrontCondition.CONDITION_ID);
    daysLeft = WAR_DAYS;
}
```

阶段用 `hasCondition(...)` 判断，不另存布尔字段。市场持有这个条件就在战时，条件本身就是状态。少一个字段，也少一处读档之后可能与实际条件对不上的地方。

条件直接增删，之后不重算，原版 `EstablishedPolityScript` 处理殖民地条件时也是这个写法。

归零即重置，一帧里溢出的那点天数不带进下一段。常规帧率下可以忽略；要严格对齐就把余数并进新的 `daysLeft`。

战事 60 天、平息 30 天。这个比值让 Foundry 长期处在受损状态，只有三成时间能正常交易，是这组数字唯一的后果，不必另找解释。市场每次按 ID 重新查找，找不到即返回。`isDone()` 恒为 `false`：这个周期没有终点。

## 完整的脚本

`src/data/scripts/gearin/campaign/GearInWarfrontCycleScript.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Foundry 周围的战事时起时息：按生涯天数在市场上挂上、撤下 Warfront 条件。脚本随存档保存。 */
public class GearInWarfrontCycleScript implements EveryFrameScript {

    /** 战事持续的生涯天数。 */
    private static final float WAR_DAYS = 60f;

    /** 战事平息的生涯天数。 */
    private static final float CALM_DAYS = 30f;

    /** 当前阶段还剩多少生涯天，随存档保存。新档开局时 Foundry 正处于战事中。 */
    private float daysLeft = WAR_DAYS;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        daysLeft -= sector.getClock().convertToDays(amount);
        if (daysLeft > 0f) {
            return;
        }
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return;
        }
        if (market.hasCondition(GearInWarfrontCondition.CONDITION_ID)) {
            market.removeCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = CALM_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围的战事暂时平息了。");
        } else {
            market.addCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = WAR_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围再度爆发战事。");
        }
    }
}
```

## 登记战事脚本

在 `GearInModPlugin.onNewGame()` 里，紧跟 Foundry 生成：

```java
new GearInFoundryGenerator().generate(sector);
sector.addScript(new GearInWarfrontCycleScript());
```

读档时不登记。脚本随存档保存，读档时实例已经在星区里。

把这个登记写进 `onGameLoad(...)` 的后果值得记下：那个回调每次读档执行一遍，登记几回就有几份脚本同时推动同一个周期。**症状是消息成对出现、条件在两份脚本之间来回翻转**，没有报错。同类错误在监听器与插件上一样成立：随存档保存的对象，登记点只能有一个，而且只能被执行一次。

确需在读档时补登记，参照 [C11-S1]，用存档公共数据里的标记确认只登记一次。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry。

- 市场条件中存在 Warfront；
- 推进约 60 个生涯天，界面左侧出现战事平息的消息，Warfront 从条件栏消失；
- 再推进约 30 天，出现再度开战的消息，Warfront 重新出现；
- 两次消息之间保存并读档，下一条消息仍按原节奏出现。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **读档之后脚本不再生效**：登记用的是 `addTransientScript(...)`，脚本没能进存档。
    - **解决方案**：有状态的脚本改用 `addScript(...)`。

- **同一件事发生两次**：脚本登记了多份，常见于把登记写进 `onGameLoad(...)`。
    - **解决方案**：只在 `onNewGame()` 登记，或在登记前以 `hasScript(...)` 检查。

- **改了类名，旧存档读不出来**：存档里记的是原类名与包名。
    - **解决方案**：已发布且随存档保存的脚本类保持原名。

- **开加速之后节奏跟着变**：把 `amount` 当秒数直接累加，没走 `convertToDays(...)`。
    - **解决方案**：时间换算统一交给时钟。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：各方法的注释。
- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager`：原版的逐帧脚本实现，生成、衰减与结束都在其中。
- `com.fs.starfarer.api.campaign.SectorAPI`：脚本的登记、查询与移除。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`convertToDays(...)`、`getDay()` 与 `getTimestamp()`。
- `com.fs.starfarer.api.impl.campaign.intel.events.EstablishedPolityScript`：原版周期性增删市场条件。
