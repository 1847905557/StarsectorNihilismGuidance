# 构建生涯地点

本节以地点容器查询为中心，先取得 `LocationAPI` 和 `StarSystemAPI`，再建立一组可查询的恒星系对象。生成器只负责准备对象，查询器负责验证 ID、标签、类型、集合和访问状态。

*本节预期阅读时间：~24 分钟。*

*本节预期操作时间：~35 分钟。*

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

## 取得地点容器

`SectorAPI.getAllLocations()` 返回星区管理的地点集合，`getHyperspace()` 返回超空间，`getStarSystem(id)` 返回指定恒星系。`StarSystemAPI` 继承 `LocationAPI`，因此地点查询方法可以直接用于恒星系。

```java
SectorAPI sector = Global.getSector();
LocationAPI hyperspace = sector.getHyperspace();
StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");

if (system != null) {
    int entityCount = system.getAllEntities().size();
    boolean current = system.isCurrentLocation();
}
```

地点容器保存实体集合、访问状态、地点 memory 和地点级脚本。实体必须通过地点创建方法或 `addEntity` 加入容器；单独创建 token 并保存引用不会让地点查询找到它。

::: tip 容器归属
所有查询先确认地点对象，再确认实体的 `getContainingLocation()`。地点 ID 正确而实体归属为空时，问题发生在注册阶段，不在查询条件。
:::

## 创建用于查询的地点对象

生成器使用固定 ID，便于查询器在任何帧重新取得对象。`initStar` 创建恒星，`addPlanet` 创建绕恒星运行的行星，`addCustomEntity` 直接把自定义实体加入地点。

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

恒星系生成完毕后可以调用 `autogenerateHyperspaceJumpPoints()`。该方法会根据系统中的行星和中心对象建立超空间跳跃点；它不是普通实体查询的替代入口。

```java
system.autogenerateHyperspaceJumpPoints(false, false, true);

SectorEntityToken marker = system.createToken(4200f, -900f);
marker.addTag("GearIn_observation_marker");
system.addEntity(marker);
```

`createToken` 只创建地点 token，`addEntity` 才把它加入地点对象集合。位置 token 没有自定义实体 spec，适合记录坐标和挂载关系，不应当被当成带交互插件的 custom entity。

## 查询地点对象

地点查询按范围从窄到宽排列：稳定 ID、业务标签、类型过滤、全量集合。查询器应把结果数量和关键对象 ID 一起记录，避免只看到 "查询成功" 却无法判断返回的是哪个对象。

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

## 运行生成器和查询器

生成器只处理地点对象的创建，查询器只处理读取。两个脚本使用同一组新 ID，但不共享运行时对象引用；查询器每次执行都重新从星区取得地点。

## 完整最小例

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
        if (system == null) {
            system = sector.createStarSystem("GearIn_observation_system");
        }

        PlanetAPI star = system.getStar();
        if (star == null) {
            star = system.initStar(
                    "GearIn_observation_star",
                    "star_yellow",
                    500f,
                    250f,
                    100f,
                    3f,
                    2f);
        }

        if (system.getEntityById("GearIn_observation_planet") == null) {
            system.addPlanet(
                    "GearIn_observation_planet",
                    star,
                    "观测行星",
                    "terran",
                    25f,
                    140f,
                    2400f,
                    180f);
        }

        if (system.getEntityById("GearIn_observation_relay") == null) {
            CustomCampaignEntityAPI relay = system.addCustomEntity(
                    "GearIn_observation_relay",
                    "观测中继站",
                    "comm_relay",
                    Factions.INDEPENDENT);
            relay.addTag("GearIn_observation_tag");
            relay.setCircularOrbit(star, 70f, 3200f, 240f);
        }

        if (system.getEntityById("GearIn_observation_marker") == null) {
            SectorEntityToken marker = system.createToken(4200f, -900f);
            marker.addTag("GearIn_observation_marker");
            system.addEntity(marker);
        }

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
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class GearInLocationQuery implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInLocationQuery.class);

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
        LocationAPI location = sector.getStarSystem("GearIn_observation_system");
        if (!(location instanceof StarSystemAPI)) {
            LOG.warn("GearIn location query: system missing");
            done = true;
            return;
        }

        StarSystemAPI system = (StarSystemAPI) location;
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
        done = true;
    }
}
```
:::

## 验证方案

1. 启用示例 MOD，在新档进入 `GearIn_observation_system`。
2. 确认恒星、行星、中继站和位置 token 已出现在地图或地点集合中。
3. 搜索 `GearIn location built` 和 `GearIn location query`，核对四种查询数量。
4. 在系统内确认 `current=true`；离开后确认 `current=false`、`entered=true`。
5. 对从未进入的测试系统读取 `daysSinceVisit`，确认结果为 `Float.MAX_VALUE`。

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

地点页的核心是查询容器，而不是堆叠创建方法。先建立可重复取得的恒星系对象，再用 ID、标签、类型和全量集合逐层验证；访问状态需要把当前位置、曾经进入和最近访问天数分开读取。

## 建议参考项

- `com.fs.starfarer.api.campaign.LocationAPI`：地点容器、实体集合和访问状态入口，注册、ID 索引与标签查询由其承载；
- `com.fs.starfarer.api.campaign.StarSystemAPI`：恒星系初始化、生成与超空间跳跃点入口；
- `data/scripts/world/systems/`：原版恒星系生成示例。
