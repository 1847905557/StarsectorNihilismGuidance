# 构建生涯地点

本节建立一个观测星系，从地点中查询恒星、行星、中继站与坐标标记，再记录玩家进入和离开后的状态。创建方法决定对象怎样加入世界，查询方法决定取得什么范围，访问记录则让同一个地点在不同阶段提供不同信息。

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

## 准备一个可以进入的观测星系

地点是生涯实体的容器，超空间与恒星系都提供 `LocationAPI` 的查询方法。本节建立 `GearIn_observation_system`，用恒星、行星和中继站提供可见对象，再用坐标 token 比较引用与集合归属。

创建 `src/data/scripts/gearin/campaign/GearInLocationBuilder.java` 和 `GearInLocationQuery.java`，将两类加入 `GearInModPlugin` 的 import。在已有 `onGameLoad(...)` 中依次登记，沿用方法内的 `sector`：

```java
if (!sector.hasTransientScript(GearInLocationBuilder.class)) {
    sector.addTransientScript(new GearInLocationBuilder());
}
if (!sector.hasTransientScript(GearInLocationQuery.class)) {
    sector.addTransientScript(new GearInLocationQuery());
}
```

生成器先建立地点，查询器随后取得它。生成器完成一轮后结束，读档时已有星系继续保留；查询器首次立即输出，以后每个生涯日记录一次。两者都由本次运行重新登记，世界对象本身则随存档保存。

## 把恒星与实体加入地点

`sector.createStarSystem(...)` 创建恒星系，`initStar(...)` 初始化恒星。随后设置超空间位置，添加行星与中继站：

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

星系位于超空间 `(-12000, 7000)`，与此前示例的位置区分。行星绕本系恒星运行，中继站使用原版 `comm_relay` 类型，属于独立势力。`GearIn_observation_tag` 用于查询这组业务对象，名称用于地图显示。

`addPlanet(...)` 与 `addCustomEntity(...)` 已经将返回对象加入当前地点。从同一个 `system` 创建它们，后面的地点查询就能在这个星系中取得。

### 创建坐标 token

坐标标记不需要中继站那样的图像与插件。本例用 `createToken(...)` 建立位置，写入 ID 与标签，再明确加入集合。星系对象就位之后，调用自动生成方法建立超空间入口：

```java
system.autogenerateHyperspaceJumpPoints(false, true, true);

SectorEntityToken marker = system.createToken(4200f, -900f);
marker.setId("GearIn_observation_marker");
marker.addTag("GearIn_observation_marker");
system.addEntity(marker);
```

**创建 token 与加入实体集合是两个动作。** 它可以单独作为坐标锚点使用；本例需要按地点 ID 与标签查询，所以调用 `addEntity(marker)`。在地图上显示的仍是恒星、行星、中继站与跳跃点，标记通过集合与日志确认。

自动生成方法的第二个参数设为 `true`，在星系边缘建立双向跳跃点，玩家能够从超空间进入，也能够经它离开。第一个参数为 `false`，不为气态巨行星生成单向入口；第三个参数为 `true`，同时生成行星条件。

::: warning token 引用不能证明查询已接入

`createToken(...)` 返回坐标对象，并不自动加入地点实体集合。需要让地点查询取得它时，调用 `addEntity(...)`，再从该地点核对 ID 与集合。

:::

## 从星区取得地点，再查询实体

世界对象创建后，查询代码不依赖生成器的局部引用。每次从当前 Sector 取得目标地点，超空间与恒星系分别有对应入口：

```java
SectorAPI sector = Global.getSector();
LocationAPI hyperspace = sector.getHyperspace();
StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");

if (system != null) {
    int entityCount = system.getAllEntities().size();
    boolean current = system.isCurrentLocation();
}
```

`getAllLocations()` 提供星区管理的地点集合，`getHyperspace()` 提供超空间，`getStarSystem(...)` 取得指定恒星系。`StarSystemAPI` 继承 `LocationAPI`，因此查询星系实体时也使用地点方法。

地点还保存自己的 Memory、访问状态与附着脚本。查询时选择哪个地点，决定检索哪个实体集合；实体的 `getContainingLocation()` 则用于回读实际所在地点。

### 地点查询方式速查表

| 需要取得的内容 | 方法 | 结果与适用范围 |
|---|---|---|
| 指定 ID 的对象 | `getEntityById(id)` | 从当前地点取得目标，未找到返回 `null` |
| 具有业务标签的对象 | `getEntitiesWithTag(tag)` | 遍历地点中的标签匹配项 |
| 指定接口或类型的对象 | `getEntities(Class)` | 按实现类或接口筛选 |
| 地点全部实体 | `getAllEntities()` | 取得当前地点的完整对象集合 |

本例首先按 `GearIn_observation_relay` 查询中继站，再查 `GearIn_observation_tag`，最后比较类型集合与全量集合。日志保留中继站所在地点以及各集合数量，就能将唯一对象与筛选范围分别对应。

ID 查询首次使用时建立地点索引，查询仍会检查对象是否属于地点集合。对象移除或未写入相应 ID 时，结果可能为空；标签查询则检查当前集合中的对象。出现空值时，沿创建 ID、集合成员与查询地点核对。

## 记录玩家进入与离开之后的变化

地点已经能够查询，接下来区分当前位置与访问记录。`isCurrentLocation()` 判断玩家现在是否在这里，`isEnteredByPlayer()` 判断是否曾进入恒星系，`getDaysSinceLastPlayerVisit()` 计算最近在该地点之后经过的生涯天数。

读取这些状态的局部片段如下：

```java
boolean current = system.isCurrentLocation();
boolean entered = system.isEnteredByPlayer();
float daysSinceVisit = system.getDaysSinceLastPlayerVisit();

if (daysSinceVisit == Float.MAX_VALUE) {
    LOG.info("GearIn location has never been visited");
}
```

从未访问时，最近访问时间尚未建立，天数返回 `Float.MAX_VALUE`。进入之后，当前地点的访问时间随推进更新；离开后再读取，得到距离最近访问经过的时间。

**`current` 描述现在，`entered` 保留曾经进入的事实。** 因此系统内为 `current=true`，离开后变为 `false`，`entered` 却仍为 `true`。分别记录进入前、系统内与离开后的日志，才能解释这几个状态的变化。

## 让生成与查询各自延续

生成器只在目标星系不存在时完整创建，查询器则持续按同一 ID 重新取得它。读档恢复已经生成的世界，新的查询实例再从当前日期开始记录。

这也为后面的生涯功能提供了对象来源：需要地点时，从星区取得它；需要其中的实体时，再按 ID、标签或类型查询。观察日志保留的是当前世界状态，生成器不需要为后续查询保留一组长期字段。

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
        system.autogenerateHyperspaceJumpPoints(false, true, true);
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

1. 保存两个类与登记，构建、重启并进入新档，核对 `GearIn location built` 与首次 `GearIn location query`。
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

观测星系中的可见实体由地点创建方法加入，坐标标记则另行登记到集合。查询代码从当前星区重新取得地点，再按 ID、标签、类型或全量集合读取目标。

访问日志继续记录玩家的变化。当前位置、曾经进入与最近访问天数分别表达现在、历史事实和时间间隔，后续功能也根据所需条件选择。

## 建议参考项

- `com.fs.starfarer.api.campaign.LocationAPI`：地点、实体集合和访问状态入口，注册、ID 索引与标签查询由其承载；
- `com.fs.starfarer.api.campaign.StarSystemAPI`：恒星系初始化、生成与超空间跳跃点入口；
- `data/scripts/world/systems/`：原版恒星系生成示例。
