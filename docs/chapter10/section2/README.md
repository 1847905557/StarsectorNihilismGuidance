# 读写各层 Memory

点开原版任何一个任务或 `rules.csv` 对话表，满眼都是以 `$` 开头的键值。它们读写的并不是上一节的 `getPersistentData()`，而是按对象作用域切分出来的 `MemoryAPI`。

除了一层层跟着行星、市场、舰队和人物走之外，这套容器最省事的地方在于支持按生涯天数自动过期 —— 写进去时顺手给个天数，到点就由引擎自己清掉，不必另写计时器脚本去盯。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S3] 的 Nipher Prime 与市场 `GearIn_planet1`；
- [C7-S3] 的 `playUISound(...)` 界面音效播放；
- [C10-S1] 的 `SectorAPI` 对象导航、`CampaignClockAPI` 与 `addTransientScript(...)`。

## 各层 Memory 与归属原则

`MemoryAPI` 是挂在不同生涯对象上的多张键值表。每张表各自的读写与过期规则完全相同，差别只在于它跟着哪个对象走：

| 作用域层级 | 取得方式 | 适合存放的内容 |
| --- | --- | --- |
| 全局 | `sector.getMemoryWithoutUpdate()` / `sector.getMemory()` | 与整个存档有关的全局进度或剧情标记 |
| 市场 | `market.getMemoryWithoutUpdate()` | 只属于这一个殖民地或空间站市场的状态 |
| 实体与舰队 | `entity.getMemoryWithoutUpdate()` | 只属于这颗行星、这座信标或这支舰队的状态 |
| 势力 | `faction.getMemoryWithoutUpdate()` | 属于该势力的全局标记 |
| 人物 | `person.getMemoryWithoutUpdate()` | 属于特定 NPC 联络人或军官的标记 |

这些层级在后续 [C15-S5] 的 `rules.csv` 对话规则中分别对应 `$global`、`$market`、`$entity`、`$faction` 与 `local` (`person` 在对话中映射为 `MemKeys.LOCAL`)。同一个事实只应当写进它真正归属的那一层："玩家是否已经与齿轮工业建立首次接触" 关乎整个存档的后续剧情解锁，放在全局 Memory；而 "首次踏足 Nipher Prime 发生在哪一天" 只描述这一个市场，放在 `GearIn_planet1` 市场的 Memory 上。

**所有 Memory 键都必须以 `$` 开头，漏写会当场抛出运行期异常。** `MemoryAPI.set(...)` 在入口处强制检查 `key.startsWith("$")`，不满足时直接抛 `RuntimeException("Key ... should start with $")`。本教程自定义的键统一采用 `$GearIn_<名称>` 格式。

### getMemoryWithoutUpdate() 与 getMemory() 的区别

每个持有 Memory 的对象上都同时提供 `getMemory()` 与 `getMemoryWithoutUpdate()` 两个方法：

- `getMemory()` 会先遍历所有已注册的 `CampaignPlugin`，触发 `updateGlobalFacts`、`updateEntityFacts` 等回调把实时关系、位置、身份等动态事实刷进表里后再返回；
- `getMemoryWithoutUpdate()` 不跑任何事实刷新，直接把当前 `MemoryAPI` 映射交出来。在日常轮询脚本、情报判断或读写自定义键时，应当使用 `getMemoryWithoutUpdate()` 以避免每帧重复触发全部插件的事实计算。

## 永久值与按天过期的限时值

`MemoryAPI` 的核心读写方法如下：

| 方法 | 作用 |
| --- | --- |
| `set(key, value)` | 写入键值，永不过期 (除非后续被覆盖或手动删除) |
| `set(key, value, expireDays)` | 写入键值，经过 `expireDays` 个生涯日后由引擎自动清除 |
| `expire(key, expireDays)` | 为已存在的键设置或修改剩余过期天数 |
| `getBoolean(key)`、`getFloat(key)`、`getLong(key)`、`getString(key)` | 按类型读取；键不存在时布尔返回 `false`、数值返回 `0`、字符串返回 `null` |
| `contains(key)` | 判断键当前是否存在 (已过期的键返回 `false`) |
| `unset(key)` | 立即删除该键 |

限时值的过期时间以**生涯日**为单位，随生涯时钟推进而扣减，生涯暂停时不流逝。如果把 `expireDays` 传为 `0` (即 `set(key, value, 0f)`)，该键会在下一次**非暂停**的生涯时钟推进时被清除，因此在打开对话或暂停期间依然保持可读。

同一个键在 `set(key, value)` 与 `set(key, value, days)` 之间覆写时，后一次调用会重置它的过期状态：原本带期限的键被两参数 `set` 覆盖后会变成永久键；原本永久的键被三参数 `set` 覆盖后则会开始倒计时。

## 首次接触齿轮工业：GearInContactScript

[C3-S3] 创建的 Nipher Prime 行星是齿轮工业的大本营，但玩家首次飞抵这里时，游戏还不会有任何反馈。本节通过 `GearInContactScript` 将 `EveryFrameScript` 与三层 Memory 写入结合起来：当玩家舰队进入 Nipher 星系并靠近到 Nipher Prime 行星表面 `1500` 距离以内时，记录接触状态并播放一声提示音。

触发时脚本同时写下三个不同性质的 Memory 条目：

- `$GearIn_contacted`：写入全局 Memory，永不过期。后续章节的声望奖励 ([C10-S4]) 与巡逻情报 ([C14-S4]) 都靠读取它来判断玩家是否已经见过齿轮工业；
- `$GearIn_contact_recent`：写入全局 Memory，带 `30` 个生涯日的有效期，30 天后自动消失；
- `$GearIn_contact_timestamp`：写入 `GearIn_planet1` 的市场 Memory，存下接触发生时的 `sector.getClock().getTimestamp()` 绝对时间戳；
- 最后调用 `Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f)` 播放原版声望提升时的界面提示音。

### 完整接触脚本

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
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(CONTACTED_KEY);
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

        sector.getMemoryWithoutUpdate().set(CONTACTED_KEY, true);
        sector.getMemoryWithoutUpdate().set(RECENT_KEY, true, RECENT_DAYS);
        market.getMemoryWithoutUpdate().set(TIMESTAMP_KEY, sector.getClock().getTimestamp());
        Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
    }
}
```

`isDone()` 直接读取全局 Memory 中的 `$GearIn_contacted`：一旦玩家靠近行星触发了写入，下一帧引擎检查 `isDone()` 时就会把脚本清理掉。

### 在入口类中登记脚本

在 `GearInModPlugin` 的 `onGameLoad(...)` 中加入：

```java
SectorAPI sector = Global.getSector();
if (!sector.getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY)
        && !sector.hasTransientScript(GearInContactScript.class)) {
    sector.addTransientScript(new GearInContactScript());
}
```

这里用的正是 [C10-S1] 讲过的瞬态脚本配存档标记的模式：尚未接触过的存档在每次读档时挂上脚本；一旦接触完成并存盘，此后读档时 `getBoolean(CONTACTED_KEY)` 为 `true`，连脚本实例都不必再创建。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。在控制台输入 `jump Nipher` 可以把舰队直接送到 Nipher 星系。

- 舰队距离 Nipher Prime 较远时，不播放提示音；
- 飞近到行星表面 `1500` 距离以内，响起一声 `ui_rep_raise` 提示音；
- 在控制台执行 `runcode Global.getSector().getMemoryWithoutUpdate().getBoolean("$GearIn_contacted")`，输出为 `true`；
- 保存并重新读档，再次靠近 Nipher Prime，提示音不会重复响起。

## 常见问题

- **写入 Memory 时游戏崩溃并报 `Key ... should start with $`**：键名漏掉了开头的 `$` 字符。
    - **解决方案**：所有写入 `MemoryAPI` 的键名都必须以 `$` 开头，如 `$GearIn_contacted`。

- **每次读档后靠近行星都会重新响起提示音**：将 "是否已接触" 存进了瞬态脚本的成员变量，或者 `onGameLoad(...)` 漏掉了对 `CONTACTED_KEY` 的判断。
    - **解决方案**：完成标记必须写入 `sector.getMemoryWithoutUpdate()`，并由 `isDone()` 与 `onGameLoad(...)` 共同读取。

- **旧存档中靠近行星没有任何反应**：Nipher 星系与 `GearIn_planet1` 市场只在 `onNewGame()` 中生成 ([C7-S1])，旧存档中不存在该市场。
    - **解决方案**：新建存档进行测试。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值读写、按生涯日自动过期与 `unset(...)` 的完整定义。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：原版各层 Memory 在规则系统中的标准作用域名称。
- `com.fs.starfarer.api.campaign.rules.HasMemory`：所有持有 `getMemory()` 与 `getMemoryWithoutUpdate()` 的对象实现的公共接口。
- `data/config/sounds.json`：`ui_rep_raise` 等原版界面提示音的登记位置。
