# 读取生涯世界状态

生涯中的对象都属于当前存档，`SectorAPI` 是取得它们的总入口。本节给已有 MOD 入口加一个每五个生涯日记录一次的观察脚本，读取地点、玩家舰队、势力与时间，再比较暂停和读档后的状态。脚本负责定期调用，重点放在这些对象怎样取得、数据保存在哪个范围。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~25 分钟。*

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
- [C7-S5] 的日志位置、前缀与上下文；
- 能够区分存档数据、运行时对象和当前帧状态。

## 把状态观察接到生涯入口

创建 `src/data/scripts/gearin/campaign/GearInSectorStateSampler.java`，完整类稍后给出。在 [C7-S1] 的 `GearInModPlugin` 添加这个类的 import，并把以下局部片段加入现有 `onGameLoad(...)` 方法，既有功能继续保留，以下使用方法内已有的 `sector`：

```java
if (!sector.hasTransientScript(GearInSectorStateSampler.class)) {
    sector.addTransientScript(new GearInSectorStateSampler());
}
```

`addTransientScript(...)` 让生涯引擎反复调用这个观察脚本，每次进入存档重新登记；`hasTransientScript(...)` 查询当前列表，保持一个实例。它的字段属于当前运行阶段，存档标记另存于 persistent data。完整的脚本生命周期由 [C14-S1] 继续讲解。

**先在生涯入口取得当前星区，再从它取得下属对象。** `Global.getSector()` 返回当前存档的 `SectorAPI`，地点、玩家舰队、势力、经济与事件服务都从这里展开。以下是已经进入生涯后的局部读取：

```java
SectorAPI sector = Global.getSector();
LocationAPI currentLocation = sector.getCurrentLocation();
CampaignFleetAPI playerFleet = sector.getPlayerFleet();
FactionAPI playerFaction = sector.getPlayerFaction();
CampaignClockAPI clock = sector.getClock();
```

应用资源加载阶段尚未建立生涯，读取放在 `onGameLoad(...)` 和它登记的生涯脚本中。当前对象由本次回调重新取得，日志可以记录具体 ID；运行阶段与读取位置一起确认。

### 生涯服务入口速查表

| 服务 | 入口 | 当前用途 |
| --- | --- | --- |
| 地点 | `getCurrentLocation()`、`getAllLocations()` | 判断玩家所在容器并遍历地点 |
| 势力 | `getPlayerFaction()`、`getFaction(id)` | 读取玩家势力和指定势力 |
| 玩家 | `getPlayerFleet()`、`getPlayerPerson()`、`getPlayerStats()` | 取得玩家舰队、人物和统计对象 |
| 经济 | `getEconomy()` | 取得市场与经济集合的总入口 |
| 事件 | `getEventManager()`、`getIntelManager()` | 取得生涯事件和情报服务 |

::: tip 当前存档的对象
静态工具方法也使用当前 `Global.getSector()`。读档后要处理的新星区从这里取得，保存长期进度时使用实际存档数据。
:::

## 读取生涯时间

`CampaignClockAPI.getTimestamp()` 返回当前生涯时间戳，`getElapsedDaysSince(timestamp)` 返回从指定时间戳经过的生涯天数。**采样间隔采用生涯日，时间戳与换算由生涯时钟提供。** `advance(float amount)` 的输入单位是秒，当前脚本直接比较时钟时间戳。

```java
CampaignClockAPI clock = sector.getClock();
long sampleTimestamp = clock.getTimestamp();
float elapsedDays = clock.getElapsedDaysSince(sampleTimestamp);
String date = clock.getDateString();
String shortDate = clock.getShortDate();
```

脚本保存上次采样时间戳，经过五个生涯日后输出，再把当前时间作为下一次基线。暂停时生涯时钟冻结，本例的 `runWhilePaused()` 返回 `false`，脚本等待恢复后继续推进。

### 固定天数采样

```java
float daysSinceSample = clock.getElapsedDaysSince(lastSampleTimestamp);
if (daysSinceSample >= SAMPLE_INTERVAL_DAYS) {
    writeStateLog(sector, clock);
    lastSampleTimestamp = clock.getTimestamp();
}
```

这里的阈值是生涯日。当前使用 transient 登记，读档后新实例从当前时间重新计五日；已经写入的存档标记仍恢复。如果功能需要跨读档保留采样进度，把时间戳放入随存档保存的脚本或持久数据，再按那个基线比较。

## 查看全局数据

`getPersistentData()` 返回随存档保存的 Map，适合当前 "已经采样过" 标记。`getMemoryWithoutUpdate()` 取得星区 Memory 并保持当前事实，Memory 还提供过期时间语义。本例 transient 脚本的字段在读档后重建；字段的保存范围由实际登记方式决定。

```java
Map<String, Object> persistentData = sector.getPersistentData();
MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();

boolean hasSampled = Boolean.TRUE.equals(
        persistentData.get("GearIn_sector_sampled"));
Object probeValue = sectorMemory.get("$GearIn_sector_probe");
```

本节定义 `GearIn_sector_sampled` 为 persistent 键，`$GearIn_sector_probe` 为星区 Memory 观察键。采样完成后写入前者，后者只读取，当前未设置时为 `null`。需要添加规则状态时，再确定它属于哪个 Memory 与何时过期。

::: tip 持久数据与 memory
持久 Map 适合保存 "是否执行过" 这类存档标记；memory 还承担事实更新和过期值语义。两者都不能替代当前帧重新取得的地点、舰队和势力对象。
:::

## 观察状态日志

每次采样重新取得对象，日志只记录 ID、日期、地点数量、暂停与两种状态值。第一次日志先读取标记再写入，因此新档第一次为 `false`，下一次为 `true`；读档后该标记继续恢复。

编者建议先固定采样间隔，再比较同一组字段。暂停、跨地点和读档分别改变不同条件，输出内容保持稳定，变化才容易对应。

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

状态脚本从当前星区取得地点、玩家与服务，按生涯时钟每五日记录一次。当前脚本随运行阶段重建，persistent 标记随存档恢复，Memory 观察则属于星区作用域。

时间、对象与保存范围一起说明，日志才有明确的比较基线。后面继续取得地点、势力和实体，仍沿这组关系安排读取。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorAPI`：生涯世界根对象与全局服务入口，暂停推进、脚本推进和星区 memory 由其承接；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：时间戳、日期和生涯天数换算；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：memory 键值和过期状态读取；
- `com.fs.starfarer.api.EveryFrameScript`：采样器所需的最小调用接口。
