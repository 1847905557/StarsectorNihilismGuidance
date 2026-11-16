# 读取生涯世界与 Memory

生涯里的状态总有一个存放处。跟着星球的、跟着舰队的、跟着存档的，游戏各有一处，取用方式互不相同。挑错一层，值会在不该消失的时候消失，或者在本该消失之后一直留着。

Memory 的过期以生涯日计。两参数的 `set` 写入的值不会过期，三参数的按给定天数过期，天数给零则在下一 tick 被清掉。同一个键在两种写法之间换一次，行为就完全不同。

脚本的字段不参与存档。要记的东西在读档之后还读得到，就不能放在那里。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C7-S1] 的 `onGameLoad(...)` 与存档初始化标记；
- [C7-S3] 的 `playUISound(...)`，本节会实际使用它；
- [C3-S3] 的 Nipher Prime 与市场 `GearIn_planet1`。

## 三层 Memory

Memory 是挂在不同对象上的多张表，每张表各自的过期与保存规则都一样。本节涉及三层：

| 层级       | 取得方式                          | 保存的内容             |
| ---------- | --------------------------------- | ---------------------- |
| 全局       | `sector.getMemory()`              | 与整个存档有关的标记   |
| 市场       | `market.getMemory()`              | 只属于这个市场的数据   |
| 实体、舰队 | `entity.getMemory()`              | 只属于这个对象的数据   |

**键必须以 `$` 开头，不写会直接抛异常。** `Memory` 的 `set(...)` 一进去就检查 `key.startsWith("$")`，不满足时抛 `RuntimeException("Key ... should start with $")`。本教程的键统一写成 `$GearIn_<名称>`。

同一个事实只应该存在于一层。"玩家是否已经与齿轮工业接触" 与整个存档有关，放在全局；"接触发生在哪一天" 只与 Nipher Prime 这个市场有关，放在市场。

## 读写与限时值

`MemoryAPI` 的常用方法如下：

| 方法                            | 作用                                   |
| ------------------------------- | -------------------------------------- |
| `set(key, value)`               | 写入，永不过期                         |
| `set(key, value, days)`         | 写入，经过指定生涯天数后自动清除       |
| `getBoolean(key)`、`getFloat(key)` | 读取，键不存在时返回 `false` 或 `0`  |
| `contains(key)`                 | 键是否存在                             |
| `unset(key)`                    | 删除                                   |

限时值适合表达 "最近发生过" 这类会自行失效的状态，不需要另写脚本去清理。过期按生涯天数计算，暂停时不流逝。

## 接触脚本

接触的判定需要持续检查玩家与 Nipher Prime 的距离，这是 `EveryFrameScript` 的工作。它的接口只有三个方法：`isDone()` 返回 `true` 时引擎清除脚本，`runWhilePaused()` 决定暂停时是否调用，`advance(amount)` 随脚本被推进而调用，`amount` 是那一次推进经过的现实秒数。脚本的生命周期留给 [C14-S1] 完整展开，这里只用它读取世界。

判定条件是玩家舰队与 Nipher Prime 位于同一地点，且距行星表面不超过 1500。脚本每 0.5 至 1 秒检查一次，不必逐帧计算距离：

```java
MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
if (market == null || market.getPrimaryEntity() == null) {
    return;
}
SectorEntityToken planet = market.getPrimaryEntity();
CampaignFleetAPI fleet = sector.getPlayerFleet();
if (fleet == null || fleet.getContainingLocation() != planet.getContainingLocation()) {
    return;
}
if (Misc.getDistance(fleet, planet) > planet.getRadius() + CONTACT_DISTANCE) {
    return;
}
```

`getMarket("GearIn_planet1")` 取回 [C3-S3] 创建的市场，行星实体从市场的 `getPrimaryEntity()` 得到。**应用加载阶段没有这份世界**，所以这些读取只能放在生涯脚本中，不能放在 `onApplicationLoad()`。

## 写入三层 Memory

条件满足后，脚本同时写入三个值：

```java
sector.getMemory().set(CONTACTED_KEY, true);
sector.getMemory().set(RECENT_KEY, true, RECENT_DAYS);
market.getMemory().set(TIMESTAMP_KEY, sector.getClock().getTimestamp());
Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
```

- `$GearIn_contacted` 写在全局，永不过期。后续章节的规则与脚本用它判断玩家是否已经见过齿轮工业；
- `$GearIn_contact_recent` 同样在全局，但带 30 天期限，30 个生涯天后自行消失；
- `$GearIn_contact_timestamp` 写在 Nipher Prime 的市场上，记录接触发生的时间戳。它只描述这个市场，换一个市场就没有意义；
- 最后一行播放 [C7-S3] 预告过的 `ui_rep_raise`，这是原版声望提升时的提示音，玩家听到它就知道发生了一次接触。

::: warning 时间戳是数字，不是天数

`getTimestamp()` 返回的是时钟的绝对时间戳。要得到 "距今多少天"，用 `clock.getElapsedDaysSince(timestamp)`，不要直接拿时间戳做减法。

:::

### 脚本自己结束

`isDone()` 读取 `$GearIn_contacted`：接触一旦发生，脚本在下一帧被引擎清除。`GearInModPlugin.onGameLoad(...)` 只在尚未接触时登记脚本，两处判断一致。

**脚本是 transient 的，不随存档保存，标记是 Memory，随存档保存。** 读档之后脚本由入口类重新建立，是否还需要它，由存档里的标记决定。把 "是否已完成" 放进脚本字段，读档后字段归零，接触会被重复触发。

### 登记脚本

在 `GearInModPlugin` 的 `onGameLoad(...)` 中加入：

```java
SectorAPI sector = Global.getSector();
if (!sector.getMemory().getBoolean(GearInContactScript.CONTACTED_KEY)
        && !sector.hasTransientScript(GearInContactScript.class)) {
    sector.addTransientScript(new GearInContactScript());
}
```

`hasTransientScript(...)` 防止同一次读档重复登记。

## 完整的接触脚本

`src/data/scripts/gearin/campaign/GearInContactScript.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** 玩家首次靠近 Nipher Prime 时，记录与齿轮工业的接触。 */
public class GearInContactScript implements EveryFrameScript {

    /** 全局 Memory：玩家是否已与齿轮工业接触，永不过期。 */
    public static final String CONTACTED_KEY = "$GearIn_contacted";

    /** 全局 Memory：最近接触过，30 天后自动消失。 */
    public static final String RECENT_KEY = "$GearIn_contact_recent";

    /** 市场 Memory：接触发生时的时间戳。 */
    public static final String TIMESTAMP_KEY = "$GearIn_contact_timestamp";

    private static final String MARKET_ID = "GearIn_planet1";

    /** 判定为接触的距离，从行星表面起算。 */
    private static final float CONTACT_DISTANCE = 1500f;

    private static final float RECENT_DAYS = 30f;

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        return Global.getSector().getMemory().getBoolean(CONTACTED_KEY);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        SectorAPI sector = Global.getSector();
        MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
        if (market == null || market.getPrimaryEntity() == null) {
            return;
        }
        SectorEntityToken planet = market.getPrimaryEntity();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() != planet.getContainingLocation()) {
            return;
        }
        if (Misc.getDistance(fleet, planet) > planet.getRadius() + CONTACT_DISTANCE) {
            return;
        }

        sector.getMemory().set(CONTACTED_KEY, true);
        sector.getMemory().set(RECENT_KEY, true, RECENT_DAYS);
        market.getMemory().set(TIMESTAMP_KEY, sector.getClock().getTimestamp());
        Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
    }
}
```

## 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。控制台命令 `Jump Nipher` 可以把舰队直接送到 Nipher 星系，不必先找跳跃点。

- 舰队距 Nipher Prime 超过约 1650 时，什么也不发生；
- 靠近到行星表面 1500 以内，听到一声界面提示音，这是 `ui_rep_raise`；
- 保存并重新读档，再次靠近同一颗行星，提示音不再响起。标记已经随存档保存，脚本不会再被登记；
- 在控制台执行 `runcode Global.getSector().getMemory().getBoolean("$GearIn_contacted")` 之类的语句，可以直接读取这个键，接触前后分别为 `false` 与 `true`。

## 常见问题

- **靠近行星后没有任何反应**：脚本没有登记，或者 `MARKET_ID` 与 [C3-S3] 创建的市场 ID 不一致。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记片段，以及市场 ID `GearIn_planet1` 的拼写。

- **每次读档都会再次听到提示音**："是否已接触" 保存在了脚本字段里，没有写入 Memory。
    - **解决方案**：标记写在 `sector.getMemory()`，并让入口类与 `isDone()` 都读取它。

- **`$GearIn_contact_recent` 在接触后很快消失**：期限的单位是生涯天数，不是秒。
    - **解决方案**：确认 `RECENT_DAYS` 为 `30f`，并在游戏内观察日期推进，而不是现实时间。

- **读取市场时出现空引用**：读取放在了 `onApplicationLoad()`，此时生涯尚未建立。
    - **解决方案**：把读取移入生涯脚本，并保留 `market == null` 的判断。

- **旧存档中没有 Nipher 星系，脚本永远不触发**：星系只在新档生成，见 [C7-S1]。
    - **解决方案**：使用新档测试。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：读写、限时值与 `expire` 的完整方法。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：原版规则使用的各层 Memory 名称。
- `com.fs.starfarer.api.EveryFrameScript`：三个方法的调用语义。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`getTimestamp()` 与 `getElapsedDaysSince(...)`。
- `data/config/sounds.json`：`ui_rep_raise` 的原版登记。
