# 读取生涯世界状态

本节从 `SectorAPI` 取得当前存档的生涯世界，并把时间、地点、势力、玩家对象和持久数据整理成可以观察的状态日志。完整示例按固定生涯天数采样；脚本外壳只承担重复调用，不在这里展开脚本生命周期专题。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~25 分钟。*

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
- [C1-S1] 的 `starsector.log` 日志位置和搜索方法；
- 能够区分存档数据、运行时对象和当前帧状态。

## 取得当前星区

`Global.getSector()` 返回当前存档的 `SectorAPI`。它是生涯世界的根入口：地点、玩家舰队、玩家势力、经济、事件管理器和全局 memory 都从这里继续取得。

```java
SectorAPI sector = Global.getSector();
if (sector == null) {
    return;
}

LocationAPI currentLocation = sector.getCurrentLocation();
CampaignFleetAPI playerFleet = sector.getPlayerFleet();
FactionAPI playerFaction = sector.getPlayerFaction();
CampaignClockAPI clock = sector.getClock();
```

对象取得顺序先确认星区，再读取下属对象。新档生成、战斗切换或读档尚未完成时，某个下属对象可能暂时为空；读取代码应记录缺失对象并等待下一次调用，而不是立即把空值写入持久状态。

### 生涯服务入口速查表

| 服务 | 入口 | 当前用途 |
| --- | --- | --- |
| 地点 | `getCurrentLocation()`、`getAllLocations()` | 判断玩家所在容器并遍历地点 |
| 势力 | `getPlayerFaction()`、`getFaction(id)` | 读取玩家势力和指定势力 |
| 玩家 | `getPlayerFleet()`、`getPlayerPerson()`、`getPlayerStats()` | 取得玩家舰队、人物和统计对象 |
| 经济 | `getEconomy()` | 取得市场与经济集合的总入口 |
| 事件 | `getEventManager()`、`getIntelManager()` | 取得生涯事件和情报服务 |

::: tip 对象取得时机
`SectorAPI` 本身是存档根对象，但下属服务可能在新档生成或读档阶段延迟建立。遇到 `null` 时保留下一次读取机会，不能把一次缺失当作永久不存在。
:::

## 读取生涯时间

`CampaignClockAPI.getTimestamp()` 返回当前生涯时间戳，`getElapsedDaysSince(timestamp)` 返回从指定时间戳经过的生涯天数。时间换算使用生涯时钟，而不是把 `advance(float amount)` 当作天数。

```java
CampaignClockAPI clock = sector.getClock();
long sampleTimestamp = clock.getTimestamp();
float elapsedDays = clock.getElapsedDaysSince(sampleTimestamp);
String date = clock.getDateString();
String shortDate = clock.getShortDate();
```

采样器保存上一次采样时间戳，并在经过目标天数后再次读取。暂停期间全局 memory 的推进量为零，普通脚本也不会调用 `advance`；恢复运行后，时钟和采样器才会继续前进。

### 固定天数采样

```java
float daysSinceSample = clock.getElapsedDaysSince(lastSampleTimestamp);
if (daysSinceSample >= SAMPLE_INTERVAL_DAYS) {
    writeStateLog(sector, clock);
    lastSampleTimestamp = clock.getTimestamp();
}
```

这里的阈值是生涯天数，不是现实秒数。若采样期间发生读档，脚本应重新取得 `SectorAPI` 和 `CampaignClockAPI`，再根据持久时间戳决定是否补记一次日志。

## 查看全局数据

`getPersistentData()` 适合保存跨读档的简单标记；`getMemoryWithoutUpdate()` 适合读取星区 memory 而不主动触发更新。脚本自己的字段只属于当前脚本实例，不能当作存档持久状态。

```java
Map<String, Object> persistentData = sector.getPersistentData();
MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();

boolean hasSampled = Boolean.TRUE.equals(
        persistentData.get("GearIn_sector_sampled"));
Object probeValue = sectorMemory.get("$GearIn_sector_probe");
```

写入 persistent data 时使用稳定、带主题前缀的键；写入 memory 时明确键的作用域和过期策略。读档后 persistent data 会恢复，脚本字段是否存在取决于脚本实例是否重新创建。

::: tip 持久数据与 memory
持久 Map 适合保存 "是否执行过" 这类存档标记；memory 还承担事实更新和过期值语义。两者都不能替代当前帧重新取得的地点、舰队和势力对象。
:::

## 观察状态日志

完整示例把一次采样拆成三部分：先取得当前对象，再判断是否达到采样间隔，最后输出稳定字段。日志不保存对象引用，只保存 ID、日期、地点数量和暂停状态，读档后可以重新建立同一观察链。

## 完整最小例

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
        if (sector == null || sector.getClock() == null) {
            return;
        }

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

1. 构建并启用示例 MOD，使用新档进入生涯。
2. 推进至少 5 个生涯天数，搜索 `GearIn sector sample`。
3. 暂停游戏并保持一段时间，确认没有新的普通采样日志。
4. 恢复运行并切换地点，确认 `date`、`timestamp` 和 `location` 发生变化。
5. 保存、退出并重新读档，确认 persistent 标记可以读取，地点和玩家舰队通过当前星区重新取得。

## 故障分析

- **日志一直没有采样**：采样器的生涯天数阈值尚未达到，或脚本在暂停期间没有推进。

    - **解决方案**：恢复运行并推进至少 `SAMPLE_INTERVAL_DAYS`，再检查 `runWhilePaused()` 和 `getElapsedDaysSince` 的输入时间戳。

- **读档后地点或舰队字段为空**：脚本保存了运行时对象或在星区对象完成前读取。

    - **解决方案**：只持久化 ID、标记和时间戳；每次采样重新从 `SectorAPI` 取得对象，并在缺失时等待下一次调用。

- **persistent 标记读档后仍为 false**：写入使用了脚本字段或不同的键名。

    - **解决方案**：统一使用 `getPersistentData()` 和稳定键 `GearIn_sector_sampled`，不要把 memory 值当作持久 Map 值读取。

## 总结

`SectorAPI` 是生涯世界的根入口。读取状态时先取得当前星区，再按服务分类取得地点、势力、玩家、经济和事件对象；时间采样使用 `CampaignClockAPI` 的生涯天数；跨读档标记使用 persistent data；暂停和读档期间始终重新取得运行时对象。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorAPI`：生涯世界根对象与全局服务入口，暂停推进、脚本推进和星区 memory 由其承接；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：时间戳、日期和生涯天数换算；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：memory 键值和过期状态读取；
- `com.fs.starfarer.api.EveryFrameScript`：采样器所需的最小调用接口。
