# 读取生涯世界状态

本节通过 `SectorAPI` 读取当前生涯，用每五日一次的记录比较地点、玩家舰队、势力与日期。采样器在读档后重新计时，存档标记则继续保留，我们将利用这个区别说明运行进度和生涯数据各自怎样保存。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 取得当前星区并定位常用全局服务入口；
- 读取生涯日期、时间戳和暂停状态；
- 区分 persistent data、星区 memory 和脚本字段；
- 按固定生涯天数输出状态采样日志；
- 根据暂停、读档和对象延迟取得现象定位读取错误。

## 前置知识

- [C7-S1] 的 MOD 入口和生涯回调；
- [C7-S2] 的 `Global.getSector()` 对象取得方式；
- [C7-S4] 的日志位置、前缀与上下文；
- 能够区分存档数据、运行时对象和当前帧状态。

## 给生涯入口接入采样脚本

本节每隔五个生涯日记录当前地点、玩家舰队与势力，再比较暂停和读档后的变化。创建 `src/data/scripts/gearin/campaign/GearInSectorStateSampler.java`，在 `GearInModPlugin` 添加类的 import，将下面局部登记加入已有 `onGameLoad(...)`，沿用其中的 `sector`：

```java
if (!sector.hasTransientScript(GearInSectorStateSampler.class)) {
    sector.addTransientScript(new GearInSectorStateSampler());
}
```

`hasTransientScript(...)` 查询当前列表，缺少实例时才加入。脚本随后持续接收生涯推进，暂停时返回 `false` 的 `runWhilePaused()` 使它停止调用。

**这份采样器只保存当前运行阶段的进度。** `addTransientScript(...)` 登记的对象不进入存档，每次读档由入口重新建立实例。第十四章会完整展开脚本生命周期，这里先用它定期读取世界。

## 从当前存档取得世界对象

进入生涯之后，`Global.getSector()` 返回当前存档的 `SectorAPI`。从它取得地点、舰队、势力与时钟，局部读取如下：

```java
SectorAPI sector = Global.getSector();
LocationAPI currentLocation = sector.getCurrentLocation();
CampaignFleetAPI playerFleet = sector.getPlayerFleet();
FactionAPI playerFaction = sector.getPlayerFaction();
CampaignClockAPI clock = sector.getClock();
```

这些对象都来自当前星区。玩家切换地点后，`getCurrentLocation()` 返回新的地点；读档后，访问当前 Sector 取得恢复的生涯。应用资源加载时还没有这份世界，因此读取放在 `onGameLoad(...)` 和它登记的脚本中。

静态工具方法也可以访问当前 `Global.getSector()`，数据的保存范围仍取决于实际所属对象。需要长期进度时，写入相应存档数据，不能仅凭访问器是静态方法就把对象保存在进程字段中。

### 生涯服务入口速查表

| 服务       | 入口                                                        | 当前用途                         |
| ---------- | ----------------------------------------------------------- | -------------------------------- |
| 地点       | `getCurrentLocation()`、`getAllLocations()`                 | 取得玩家当前地点与全星区地点集合 |
| 势力       | `getPlayerFaction()`、`getFaction(id)`                      | 取得玩家势力与指定势力           |
| 玩家       | `getPlayerFleet()`、`getPlayerPerson()`、`getPlayerStats()` | 取得玩家舰队、人物与统计对象     |
| 经济       | `getEconomy()`                                              | 查询市场与经济集合               |
| 事件与情报 | `getEventManager()`、`getIntelManager()`                    | 访问生涯事件与情报服务           |

本节将地点、舰队和势力的 ID 写入日志。后续专用页面再从相同入口展开各自的对象，读取来源继续保持一致。

## 按生涯日安排记录

采样使用 `CampaignClockAPI` 的时间戳。`getTimestamp()` 保存当前时刻，`getElapsedDaysSince(timestamp)` 计算从指定时刻经过的生涯天数，日期方法则提供可显示文本：

```java
CampaignClockAPI clock = sector.getClock();
long sampleTimestamp = clock.getTimestamp();
float elapsedDays = clock.getElapsedDaysSince(sampleTimestamp);
String date = clock.getDateString();
String shortDate = clock.getShortDate();
```

脚本第一次推进时，把当前时间戳保存为 `lastSampleTimestamp`。以后每次比较距离该时刻的天数，达到 `SAMPLE_INTERVAL_DAYS` 的 5 日后输出，再以当前时刻开始下一段间隔：

```java
float daysSinceSample = clock.getElapsedDaysSince(lastSampleTimestamp);
if (daysSinceSample >= SAMPLE_INTERVAL_DAYS) {
    writeStateLog(sector, clock);
    lastSampleTimestamp = clock.getTimestamp();
}
```

这里记录的是生涯经过时间。`advance(float amount)` 提供秒数，不能直接当作生涯日；当前示例采用时钟时间戳比较，需要累计步长的功能则通过生涯时钟换算。

暂停时生涯时钟冻结，采样器也停止推进，因此真实时间经过多久，都不会增加这一段五日间隔。恢复后继续从暂停前的时间戳比较。

## 区分采样进度与存档标记

现在考虑读档：脚本是新实例，`lastSampleTimestamp` 重新建立，五日采样会从加载后的时刻重新计算。但 "这个存档已经采样过" 应当继续保留，所以它写入 Sector 的 persistent data。

同时读取星区 Memory 中的观察键，比较两种数据入口：

```java
Map<String, Object> persistentData = sector.getPersistentData();
MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();

boolean hasSampled = Boolean.TRUE.equals(
        persistentData.get("GearIn_sector_sampled"));
Object probeValue = sectorMemory.get("$GearIn_sector_probe");
```

`GearIn_sector_sampled` 是 persistent data 的键，完成采样后写入 `true`；`$GearIn_sector_probe` 属于星区 Memory，本节只读取，尚未设置时为 `null`。`getMemoryWithoutUpdate()` 返回现有事实，Memory 还提供过期时间等语义。

**脚本字段、persistent data 与 Memory 各自保存所需状态。** 此例中，脚本字段记录本次采样间隔，persistent data 记录该存档是否采样过，星区 Memory 提供规则状态的读取入口。需要跨读档延续计时的功能，则把时间戳保存到普通脚本或持久数据中，再按那份基准继续。

## 将日志对应到实际操作

每次采样重新取得当前对象，记录日期、时间戳、地点数量、玩家所在地点、舰队、势力与两种数据值。切换地点时只改变相关对象与记录，采样仍按原有五日间隔执行。

**标记在输出之后写入。** 因此新档第一次日志为 `persistent=false`，第二次为 `true`。保存第一次采样后的存档再读档，标记恢复为 `true`，而脚本重新计五日。这个差异正好说明了两者的保存范围。

完整类只保存时间戳与初始化状态，所有世界对象在采样时取得。日志中的空值反映当时引用状态，具体读取继续放在已建立生涯的回调内。

## 完整最小例

保存下列类，并保留前面的生涯登记片段，再构建 JAR。

::: details 完整成品：GearInSectorStateSampler

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class GearInSectorStateSampler implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_SectorStateSampler";

    private static final String PERSISTENT_SAMPLE_KEY = "GearIn_sector_sampled";
    private static final String MEMORY_PROBE_KEY = "$GearIn_sector_probe";
    private static final float SAMPLE_INTERVAL_DAYS = 5f;
    private static final Logger LOG = Global.getLogger(GearInSectorStateSampler.class);

    private long lastSampleTimestamp;
    private boolean initialized;

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
        CampaignClockAPI clock = sector.getClock();
        if (!initialized) {
            lastSampleTimestamp = clock.getTimestamp();
            initialized = true;
        }

        float daysSinceSample = clock.getElapsedDaysSince(lastSampleTimestamp);
        if (daysSinceSample < SAMPLE_INTERVAL_DAYS) {
            return;
        }

        writeStateLog(sector, clock);
        lastSampleTimestamp = clock.getTimestamp();
        sector.getPersistentData().put(PERSISTENT_SAMPLE_KEY, true);
    }

    private void writeStateLog(SectorAPI sector, CampaignClockAPI clock) {
        LocationAPI currentLocation = sector.getCurrentLocation();
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        FactionAPI playerFaction = sector.getPlayerFaction();
        Map<String, Object> persistentData = sector.getPersistentData();
        MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();
        Object probeValue = sectorMemory.get(MEMORY_PROBE_KEY);

        String locationId = currentLocation == null
                ? "null"
                : currentLocation.getId();
        String fleetId = playerFleet == null
                ? "null"
                : playerFleet.getId();
        String factionId = playerFaction == null
                ? "null"
                : playerFaction.getId();

        LOG.info(String.format(
                "GearIn sector sample: date=%s timestamp=%d paused=%s locations=%d location=%s fleet=%s faction=%s persistent=%s probe=%s",
                clock.getDateString(),
                clock.getTimestamp(),
                sector.isPaused(),
                sector.getAllLocations().size(),
                locationId,
                fleetId,
                factionId,
                Boolean.TRUE.equals(persistentData.get(PERSISTENT_SAMPLE_KEY)),
                probeValue));
    }
}
```

:::

## 验证方案

前置数据：包含本节采样脚本类的 GearIn MOD 已构建并启用，并准备一个新档。

1. 保存类与 `onGameLoad(...)` 登记，构建 JAR，重启游戏并进入新档。
2. 推进至少 5 个生涯天数，搜索 `GearIn sector sample`。
3. 暂停游戏并保持一段时间，确认没有新的普通采样日志。
4. 恢复运行并切换地点，确认 `date`、`timestamp` 和 `location` 发生变化。
5. 完成一次采样后保存并退出，重新读档，再推进五日，比较 persistent 标记与当前地点、玩家舰队。

预期结果：

- 生涯天数达到采样阈值后，日志出现 `GearIn sector sample`；
- 暂停游戏期间没有新的普通采样日志；
- 恢复运行并切换地点后，`date`、`timestamp` 和 `location` 发生变化；
- 首次采样时 `persistent=false`，之后及读档后的采样为 `true`；`probe` 未设置时为 `null`。
- 读档后脚本重新计五日，地点与玩家舰队从当前星区取得。

## 故障分析

- **日志一直没有采样**：生涯登记缺失、五日阈值尚未达到，或当前处于暂停。
    - **解决方案**：核对 `onGameLoad(...)` 的 transient 登记，恢复运行并推进五日，再比较时间戳与 `runWhilePaused()`。

- **读取地点或舰队时发生空引用**：读取代码放在生涯尚未建立的应用回调中。
    - **解决方案**：将当前世界读取放入 `onGameLoad(...)` 登记的生涯脚本，并在采样时取得当前星区对象。

- **persistent 标记读档后仍为 false**：写入使用了脚本字段或不同的键名。
    - **解决方案**：统一使用 `getPersistentData()` 和稳定键 `GearIn_sector_sampled`，不要把 memory 值当作持久 Map 值读取。

## 总结

五日采样从当前星区取得对象，用生涯时钟比较间隔，再记录结果。暂停不增加生涯天数，读档重新建立 transient 计时，已经写入的 persistent 标记却继续属于这个存档。

后续的地点、势力与实体同样从当前 Sector 取得。运行进度怎样延续，取决于它保存在哪个对象与容器中。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorAPI`：生涯世界根对象与全局服务入口，暂停推进、脚本推进和星区 memory 由其承接；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：时间戳、日期和生涯天数换算；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：memory 键值和过期状态读取；
- `com.fs.starfarer.api.EveryFrameScript`：采样器所需的最小调用接口。
