# 构建生涯地点

上一节已经取得当前星区，本节在其中建立一个观测星系，再分别按 ID、标签、类型和全量集合查找实体。恒星、行星和中继站提供可见对象，位置 token 提供坐标；查询日志继续记录玩家进入与离开后的变化，让容器归属和访问状态都有实际观察入口。

*本节预期阅读时间：\~24 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 区分星区地点、超空间和恒星系容器；
- 创建可查询的恒星、行星、自定义实体和跳跃点；
- 使用 ID、标签、类型和全量集合查询地点对象；
- 读取当前地点、玩家访问状态和最近访问天数；
- 根据容器归属和查询结果定位地点生成故障。

## 前置知识

- [C10-S1] 的 `SectorAPI` 当前地点和全局实体入口；
- [C3-S4] 的恒星系生成入口；
- [C7-S1] 的生涯入口注册和日志读取；
- 能够区分地点级对象与星区级对象。

## 登记生成与查询，再取得地点

创建 `src/data/scripts/gearin/campaign/GearInLocationBuilder.java` 和 `GearInLocationQuery.java`。在 [C7-S1] 的 `GearInModPlugin` 添加这两个类的 import，把下面的局部片段加入 `onGameLoad(...)`，登记顺序先生成、后查询，沿用方法内已有的 `sector`：

```java
if (!sector.hasTransientScript(GearInLocationBuilder.class)) {
    sector.addTransientScript(new GearInLocationBuilder());
}
if (!sector.hasTransientScript(GearInLocationQuery.class)) {
    sector.addTransientScript(new GearInLocationQuery());
}
```

生成器建立星系后结束，再次读档时已有星系直接保留。查询器首次立即记录，之后每个生涯日记录一次，便于比较访问变化。当前登记沿用 [C10-S1] 的 transient 方式。

**地点是实体的容器。** `getAllLocations()` 取得星区管理的地点，`getHyperspace()` 取得超空间，`getStarSystem(id)` 取得指定恒星系。`StarSystemAPI` 继承 `LocationAPI`，地点查询方法也可用于恒星系：

```java
SectorAPI sector = Global.getSector();
LocationAPI hyperspace = sector.getHyperspace();
StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");

if (system != null) {
    int entityCount = system.getAllEntities().size();
    boolean current = system.isCurrentLocation();
}
```

地点保存实体集合、访问状态、地点 memory 和地点级脚本。通过地点创建方法或 `addEntity(...)` 加入实体后，查询才会从该地点找到它。只保存 token 引用，仍要区分它是否已加入集合。

::: tip 容器归属
所有查询先确认地点对象，再确认实体的 `getContainingLocation()`。地点 ID 正确而实体归属为空时，问题发生在注册阶段，不在查询条件。
:::

## 创建用于查询的地点对象

本节定义 `GearIn_observation_system` 为观测星系 ID，实体也使用固定 ID，查询器每次从星区重新取得它们。`initStar` 创建恒星，`addPlanet` 创建绕恒星运行的行星，`addCustomEntity` 直接把自定义实体加入地点。

```java
StarSystemAPI system = sector.createStarSystem("GearIn_observation_system");
PlanetAPI star = system.initStar(
        "GearIn_observation_star",
        "star_yellow",
        500f,
        250f,
        100f,
        3f,
        2f);

system.getLocation().set(-12000f, 7000f);

PlanetAPI planet = system.addPlanet(
        "GearIn_observation_planet",
        star,
        "观测行星",
        "terran",
        25f,
        140f,
        2400f,
        180f);

CustomCampaignEntityAPI relay = system.addCustomEntity(
        "GearIn_observation_relay",
        "观测中继站",
        "comm_relay",
        Factions.INDEPENDENT);
relay.addTag("GearIn_observation_tag");
relay.setCircularOrbit(star, 70f, 3200f, 240f);
```

恒星初始化后，把星系放在超空间坐标 `(-12000, 7000)`，与此前的示例位置区分。行星与中继站就位后，再用 `autogenerateHyperspaceJumpPoints(...)` 建立往返入口。

```java
system.autogenerateHyperspaceJumpPoints(false, false, true);

SectorEntityToken marker = system.createToken(4200f, -900f);
marker.setId("GearIn_observation_marker");
marker.addTag("GearIn_observation_marker");
system.addEntity(marker);
```

`createToken(...)` 创建坐标 token，`setId(...)` 让它可按本节 ID 找回，`addEntity(...)` 才加入地点集合。它没有 custom entity 的图像与插件，当前用于坐标和挂载关系；在地图上看见的对象是恒星、行星、中继站与跳跃点。

## 查询地点对象

先找已知中继站，再找它的标签，最后比较类型集合与全量集合。**查询条件对应需要找的对象，日志同时保留 ID 和数量。** 一句 "成功" 无法解释到底找到了哪个中继站。

### 查询方式速查表

| 查询方式 | API | 适用判断 |
| --- | --- | --- |
| 稳定 ID | `getEntityById(id)` | 读取唯一对象，查不到返回 `null` |
| 标签 | `getEntitiesWithTag(tag)` | 读取一组具有业务标签的对象 |
| 类型 | `getEntities(Class)` | 按实现类或接口筛选对象 |
| 全量 | `getAllEntities()` | 观察地点对象总量和生成结果 |

`getEntityById` 首次调用会建立地点 ID 索引。对象已经从地点移除或 ID 没有写入时，索引查询返回 `null`；标签查询则直接检查当前地点对象集合。

### 读取访问状态

`isCurrentLocation()` 判断玩家当前是否位于该地点，`StarSystemAPI.isEnteredByPlayer()` 判断玩家是否曾进入恒星系，`getDaysSinceLastPlayerVisit()` 读取距离上次进入的生涯天数。未访问过的地点返回 `Float.MAX_VALUE`，不能把它当作普通的 "零天前"。

```java
boolean current = system.isCurrentLocation();
boolean entered = system.isEnteredByPlayer();
float daysSinceVisit = system.getDaysSinceLastPlayerVisit();

if (daysSinceVisit == Float.MAX_VALUE) {
    LOG.info("GearIn location has never been visited");
}
```

::: tip current 与 entered
玩家正在地点内时 `current` 为 `true`；玩家离开后它会变成 `false`，但已经进入过的恒星系仍可保持 `entered=true`。两个值需要分别验证。
:::

## 让生成与读取保持各自职责

生成器在星系不存在时完整创建一次，查询器每次按同一个 ID 重新取得地点。读档后保留已经生成的世界，再建立本次运行的查询实例。

编者建议先看初次进入前的日志，再进入、离开后比较。`current`、`entered` 与最近访问天数回答不同问题，留在星系里连续看几行日志，并不能替代这次往返。

## 完整最小例

两个类保存到前述路径，保留生成与查询的登记片段。

::: details 完整成品：GearInLocationBuilder
```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInLocationBuilder implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_LocationBuilder";

    private static final Logger LOG = Global.getLogger(GearInLocationBuilder.class);

    private boolean done;

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
        if (done) {
            return;
        }

        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system != null) {
            done = true;
            return;
        }
        system = sector.createStarSystem("GearIn_observation_system");
        PlanetAPI star = system.initStar(
                "GearIn_observation_star",
                "star_yellow",
                500f,
                250f,
                100f,
                3f,
                2f);
        system.getLocation().set(-12000f, 7000f);
        system.addPlanet(
                "GearIn_observation_planet",
                star,
                "观测行星",
                "terran",
                25f,
                140f,
                2400f,
                180f);
        CustomCampaignEntityAPI relay = system.addCustomEntity(
                "GearIn_observation_relay",
                "观测中继站",
                "comm_relay",
                Factions.INDEPENDENT);
        relay.addTag("GearIn_observation_tag");
        relay.setCircularOrbit(star, 70f, 3200f, 240f);
        SectorEntityToken marker = system.createToken(4200f, -900f);
        marker.setId("GearIn_observation_marker");
        marker.addTag("GearIn_observation_marker");
        system.addEntity(marker);
        system.autogenerateHyperspaceJumpPoints(false, false, true);
        LOG.info("GearIn location built: id=" + system.getId());
        done = true;
    }
}
```
:::

::: details 完整成品：GearInLocationQuery
```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class GearInLocationQuery implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInLocationQuery.class);

    private boolean reported;
    private long lastReportTimestamp;

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
        if (reported && clock.getElapsedDaysSince(lastReportTimestamp) < 1f) {
            return;
        }
        lastReportTimestamp = clock.getTimestamp();
        reported = true;
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system == null) {
            LOG.warn("GearIn location query: system missing");
            return;
        }

        SectorEntityToken relay = system.getEntityById("GearIn_observation_relay");
        List<SectorEntityToken> tagMatches = system.getEntitiesWithTag(
                "GearIn_observation_tag");
        List<?> tokenMatches = system.getEntities(SectorEntityToken.class);
        float daysSinceVisit = system.getDaysSinceLastPlayerVisit();
        String relayLocation = relay == null || relay.getContainingLocation() == null
                ? "null"
                : relay.getContainingLocation().getId();

        LOG.info(String.format(
                "GearIn location query: id=%s all=%d typed=%d tags=%d current=%s entered=%s daysSinceVisit=%s relayLocation=%s",
                system.getId(),
                system.getAllEntities().size(),
                tokenMatches.size(),
                tagMatches.size(),
                system.isCurrentLocation(),
                system.isEnteredByPlayer(),
                daysSinceVisit,
                relayLocation));
    }
}
```
:::

## 验证方案

前置数据：包含本节生成器与查询器两个脚本类的 GearIn MOD 已构建并启用，并准备一个新档。

1. 保存两个类与登记，构建、重启并进入新档，先查看 `GearIn location built` 与首次 `GearIn location query`。
2. 前往超空间坐标 `(-12000, 7000)`，通过跳跃点进入观测星系，观察恒星、行星与中继站；位置 token 在地点集合日志中确认。
3. 推进至少一个生涯日，再搜索 `GearIn location query`，核对 `all`、`typed`、`tags`、`relayLocation` 与访问状态。
4. 在系统内记录 `current=true`，离开并推进一日后比较 `current=false` 与 `entered=true`。
5. 比较首次进入前的 `Float.MAX_VALUE` 与离开后的最近访问天数，再保存读档，确认已有星系保留、查询继续记录。

预期结果：

- 恒星、行星、中继站与跳跃点可在地图观察，位置 token 通过 ID 与地点集合取得；
- 日志出现 `GearIn location built` 与 `GearIn location query`，四种查询数量与创建结果一致；
- 系统内 `current=true`，离开后 `current=false`、`entered=true`；
- 从未进入的测试系统 `daysSinceVisit` 返回 `Float.MAX_VALUE`。

## 故障分析

- **ID 查询返回 `null`**：实体没有进入地点集合，或查询 ID 与创建 ID 不一致。
    - **解决方案**：确认创建方法来自目标 `LocationAPI`，并逐字核对 `GearIn_observation_*` ID。

- **标签数量为零但实体存在**：标签写入发生在另一个 token，或查询发生在生成器执行前。
    - **解决方案**：确认 `addTag` 作用于实际加入地点的对象，并先完成生成器再运行查询器。

- **跳跃点没有出现在超空间**：自动生成调用发生在恒星系结构完成前，或重复生成覆盖了预期结果。
    - **解决方案**：先完成恒星和行星，再调用 `autogenerateHyperspaceJumpPoints`，并检查返回的自动跳跃点集合。

- **最近访问天数显示极大值**：玩家尚未进入该地点。
    - **解决方案**：先进入恒星系，再在离开后读取 `getDaysSinceLastPlayerVisit()`。

## 总结

观测星系建立了固定 ID 的实体和往返入口，地点查询再分别使用 ID、标签、类型与全量集合。位置 token 的创建、命名和加入集合也分别完成，引用与容器归属能够一起核对。

访问日志在进入前、系统内与离开后持续记录。当前地点、曾经访问和最近访问天数各有自己的含义，后续功能按实际需要选择。

## 建议参考项

- `com.fs.starfarer.api.campaign.LocationAPI`：地点、实体集合和访问状态入口，注册、ID 索引与标签查询由其承载；
- `com.fs.starfarer.api.campaign.StarSystemAPI`：恒星系初始化、生成与超空间跳跃点入口；
- `data/scripts/world/systems/`：原版恒星系生成示例。
