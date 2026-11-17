# 构建生涯地点

第三章搭 Nipher 星系时只要按部就班放恒星、摆行星，一套标准流程就能走通。到了深空遗迹、古战场或者干脆没有恒星的异常地带，没有中心太阳照样要布环境光、建轨道、放地形，做法就完全不同了。

学会在这样的地点里把残骸与跳跃点安顿妥当，再在运行期按 ID 或标签检索其中的对象，才算真正掌握了 `LocationAPI` 的构建。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~25 分钟。_

## 前置知识

- [C3-S3] 的星系生成器，包括 `createStarSystem(...)` 与 `autogenerateHyperspaceJumpPoints(...)`；
- [C7-S1] 的 `onNewGame()`，新地点只在新档中生成；
- [C10-S1] 的 `SectorAPI` 与 `LocationAPI` 层级关系。

## 没有恒星的星系

[C3-S3] 的 Nipher 以恒星为中心，行星与中继站都绕着它公转。本节要创建的遗迹星系 `Ashen Verge` 没有中心恒星，初始化方式与标准恒星系有三处差异：

- 用 `initNonStarCenter()` 代替 `initStar(...)`，它会创建一个不可见的中心实体作为星系内其它实体的公转焦点；
- 没有恒星就没有默认光源，必须用 `setLightColor(...)` 与 `center.addTag(Tags.AMBIENT_LS)` 两行配套设置环境光；
- 星系在超空间中的坐标仍由 `getLocation().set(x, y)` 指定。Nipher 位于 `(-16000, -9000)`，`Ashen Verge` 取 `(-13500, -4500)`，两者相距约 `5100`，避免星图图标重叠。

```java
StarSystemAPI system = sector.createStarSystem(SYSTEM_NAME);
system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
system.getLocation().set(-13500f, -4500f);

SectorEntityToken center = system.initNonStarCenter();
system.setLightColor(new Color(120, 110, 100));
center.addTag(Tags.AMBIENT_LS);
```

::: warning StarSystemAPI.setLightColor(...) 的接口注释存在误导

`StarSystemAPI.setLightColor(...)` 的官方 Javadoc 注释声称该方法 "只适用于有光源 (即有恒星) 的地点"。**这条注释与引擎的实际实现不符。**

在底层渲染器中，无恒星星系不仅需要调用 `setLightColor(...)` 指定环境色调，而且**必须紧接着给 `initNonStarCenter()` 返回的中心点打上 `Tags.AMBIENT_LS` 标签**。渲染器绘制星系内实体时会检查光源是否带有 `Tags.AMBIENT_LS`：如果不打这个标签，那个看不见的空中心点仍会被当作一颗方向性点光源，导致周围实体背面被盖上一层生硬的黑影遮罩；打上标签后，星系才会呈现均匀的无方向环境光。原版的 `GateHaulerLocation` 与 `AbyssalRogueStellarObjectEPEC` 均采用这两行连写的做法。

:::

`SYSTEM_NAME` 为 `"Ashen Verge"`。星系名称既是玩家在星图上看到的名字，也是 `sector.getStarSystem(...)` 的查询键，修改时两处必须同步。

## 实体与地形的分工

放进 `LocationAPI` 的对象在实现上同源，但在玩法与取用方式上严格分成两路：

- **实体 (`SectorEntityToken`)**：是一个具体的交互目标，有坐标、轨道和所属势力，能被玩家鼠标点选并打开交互对话框。行星、空间站、遗迹船、信标、跳跃点都属于实体。
- **地形 (`CampaignTerrainAPI`)**：虽然也继承自 `SectorEntityToken`，但它表达的是一片带半径的区域 (`setRadius(...)` 定范围，`containsEntity(...)` 判包含)，对驶入其中的舰队施加持续效果，不能像实体那样被点选开对话框。残骸场、小行星带、星云、日冕、磁暴区都属于地形。

两套对象在 `LocationAPI` 上有各自的添加方法：

```java
SectorEntityToken createToken(Vector2f loc);
void addEntity(SectorEntityToken entity);
CustomCampaignEntityAPI addCustomEntity(String id, String name, String type, String factionId);

SectorEntityToken addTerrain(String terrainId, Object param);
SectorEntityToken addAsteroidBelt(SectorEntityToken focus, int numAsteroids,
        float orbitRadius, float width, float minOrbitDays, float maxOrbitDays);
SectorEntityToken addCorona(SectorEntityToken star, float extraRadius, float windBurnLevel,
        float flareProbability, float crLossMult);
```

- `createToken(...)` 创建一个纯粹的空坐标点，常用于充当局部轨道焦点；
- `addEntity(...)` 将已通过工厂造好的实体 (如手动创建的跳跃点) 放进地点；
- `addCustomEntity(...)` 按 `data/config/custom_entities.json` 中登记的类型 ID 创建自定义实体；
- `addTerrain(...)` 按 `data/campaign/terrain.json` 中的地形类型 ID 与对应的参数对象创建地形，小行星带、环带与日冕则有拆好参数的快捷方法。

## 放入可打捞的遗迹与战利品

原版 `custom_entities.json` 中的勘测船残骸 (`Entities.DERELICT_SURVEY_SHIP`，即 `"derelict_survey_ship"`) 与补给箱 (`Entities.SUPPLY_CACHE`，即 `"supply_cache"`) 已经自带 `"has_interaction_dialog"` 与 `"salvageable"` 标签。用 `addCustomEntity(...)` 把它们放进地点并指定轨道，玩家靠近就能直接触发打捞流程：

```java
SectorEntityToken ship = system.addCustomEntity(
        "GearIn_ashen_wreck", "灰烬残骸", Entities.DERELICT_SURVEY_SHIP, Factions.NEUTRAL);
ship.setCircularOrbitPointingDown(center, 40f, 900f, 80f);

SectorEntityToken cache = system.addCustomEntity(
        "GearIn_ashen_cache", "封存的补给箱", Entities.SUPPLY_CACHE, Factions.NEUTRAL);
cache.setCircularOrbit(center, 220f, 1400f, 120f);
```

`setCircularOrbit(focus, angle, orbitRadius, orbitDays)` 依次传入轨道焦点、初始角度、轨道半径和公转周期天数；`setCircularOrbitPointingDown(...)` 则让实体的贴图朝向始终指向轨道焦点 (更多轨道与单体交互配置见 [C10-S5])。

这里要注意区分两种不同来源的打捞实体：

1. **固定地点直接摆放的成品实体**：如上面的 `Entities.DERELICT_SURVEY_SHIP` 与 `Entities.SUPPLY_CACHE`，定义在 `data/config/custom_entities.json` 中，直接调 `system.addCustomEntity(...)` 即可；
2. **程序化生成星系使用的打捞模板**：定义在 `data/campaign/procgen/salvage_entity_gen_data.csv` 中，需通过 `BaseThemeGenerator.addSalvageEntity(...)` 创建。手工搭建固定星系时，用第一种更直观。

如果需要往某个可打捞实体上额外塞入战利品或打捞经验，`SectorEntityToken` 提供了直接挂载掉落组的方法：

```java
void addDropValue(String group, int value);
void addDropRandom(String group, int chances);
void setDiscoveryXP(Float discoveryXP);
void setSalvageXP(Float salvageXP);
```

其中 `group` 对应 `data/campaign/procgen/drop_groups.csv` 中的掉落组名称 (如 `"weapons2"`、`"fuel"`)。`addDropValue(...)` 按总价值生成掉落，`addDropRandom(...)` 按抽取次数生成掉落。

### 信标与残骸场

生成器中还顺手放入了一座归属齿轮工业的导航浮标 `"GearIn_ashen_beacon"` (这是 [C10-S5] 要用到的神奇妙妙工具)，并在勘测船残骸周围套上一圈残骸场地形：

```java
SectorEntityToken beacon = system.addCustomEntity(
        BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
beacon.setInteractionImage("illustrations", "comm_relay");

DebrisFieldParams params = new DebrisFieldParams(300f, -1f, 10000000f, 0f);
params.source = DebrisFieldSource.MIXED;
SectorEntityToken debris = Misc.addDebrisField(system, params, new Random(20261114L));
debris.setCircularOrbit(ship, 0f, 0f, 100f);
```

残骸场属于地形，使用 `Misc.addDebrisField(system, params, random)` 创建，并将其轨道焦点直接设为上面的勘测船残骸 `ship`，使残骸场始终包裹着勘测船一起公转。`DebrisFieldParams(radius, density, lastsDays, glowsDays)` 的密度传 `-1f` 表示采用默认密度，持续天数传极大值 `10000000f` 表示永久存在；残骸场半径不宜超过 `1000f`，否则会拖慢星系内的渲染与碰撞帧率。

### 进入星系的跳跃点

```java
system.autogenerateHyperspaceJumpPoints(false, true);
```

第一个参数控制是否在星系内的气态巨行星及引力井处生成单向入口，`Ashen Verge` 没有行星，因此传 `false`；第二个参数控制是否在星系边缘自动生成一个双向超空间跳跃点，传 `true`。**如果漏掉这一行，星图上虽然能看到 `Ashen Verge` 的名字，超空间里却没有任何入口可以跳进去。**

若不希望由引擎自动安排边缘跳跃点的位置，也可以通过 `Global.getFactory().createJumpPoint(...)` 手动创建并用 `system.addEntity(...)` 加入 (`LocationAPI` 上没有名为 `addJumpPoint` 的方法)：

```java
JumpPointAPI point = Global.getFactory().createJumpPoint(null, "Fringe Jump-point");
point.setStandardWormholeToHyperspaceVisual();
point.setCircularOrbit(center, 270f, 2600f, 100f);
system.addEntity(point);
```

## 从地点中检索实体

地点建好之后，后续脚本往往需要从 `LocationAPI` (或跨地点从 `SectorAPI`) 把特定的实体、行星或地形重新找出来：

| 检索目标 | 调用方式 | 返回结果与说明 |
| --- | --- | --- |
| 按全局唯一 ID 查找 | `sector.getEntityById(id)` 或 `location.getEntityById(id)` | 返回对应 `SectorEntityToken`，未找到返回 `null` |
| 按标签筛选实体 | `location.getEntitiesWithTag(tag)` | 返回该地点内带有指定标签 (如 `Tags.OBJECTIVE`、`Tags.SALVAGEABLE`) 的全部实体列表 |
| 按标签筛选自定义实体 | `location.getCustomEntitiesWithTag(tag)` | 仅在自定义实体 (`CustomCampaignEntityAPI`) 中按标签筛选 |
| 按接口或实现类筛选 | `location.getEntities(Class<T> c)` | 返回该地点内实现指定接口的实体列表 |
| 获取特定分类列表 | `location.getPlanets()`、`location.getFleets()`、`location.getJumpPoints()`、`location.getTerrainCopy()` | 分别取得该地点内的行星、舰队、跳跃点或地形列表 |

凡是由我们自己放进地点的关键实体 (如本节的 `"GearIn_ashen_beacon"` 与 `"GearIn_ashen_wreck"`)，创建时都应传入带 `GearIn_` 前缀的固定 ID，后续直接用 `sector.getEntityById(...)` 即可一步取回，不必遍历列表。

## 在新档中生成

在 `GearInModPlugin.onNewGame()` 中，于 Nipher 星系生成之后追加调用：

```java
new GearInSystemGenerator().generate(sector);
new GearInAshenVergeGenerator().generate(sector);
seedRelations(sector);
```

## 完整的生成器

`src/data/scripts/gearin/campaign/GearInAshenVergeGenerator.java`：

```java
package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;

import data.scripts.gearin.GearInModPlugin;

/** 创建没有恒星的 Ashen Verge：一处可以进入并打捞的遗迹地点。 */
public class GearInAshenVergeGenerator {

    public static final String SYSTEM_NAME = "Ashen Verge";

    public static final String BEACON_ID = "GearIn_ashen_beacon";

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem(SYSTEM_NAME);
        system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
        system.getLocation().set(-13500f, -4500f);

        // 没有恒星时，用空的中心点作为轨道焦点，光照颜色需要手动给定
        SectorEntityToken center = system.initNonStarCenter();
        system.setLightColor(new Color(120, 110, 100));
        center.addTag(Tags.AMBIENT_LS);

        // 遗迹主体：原版的勘测船残骸，自带交互对话与打捞
        SectorEntityToken ship = system.addCustomEntity(
                "GearIn_ashen_wreck", "灰烬残骸", Entities.DERELICT_SURVEY_SHIP, Factions.NEUTRAL);
        ship.setCircularOrbitPointingDown(center, 40f, 900f, 80f);

        SectorEntityToken cache = system.addCustomEntity(
                "GearIn_ashen_cache", "封存的补给箱", Entities.SUPPLY_CACHE, Factions.NEUTRAL);
        cache.setCircularOrbit(center, 220f, 1400f, 120f);

        // 信标：原版导航浮标，归属齿轮工业，用稳定 ID 供后续脚本查找
        SectorEntityToken beacon = system.addCustomEntity(
                BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
        beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
        beacon.setInteractionImage("illustrations", "comm_relay");

        // 残骸场围绕勘测船，提示玩家这里发生过战斗
        DebrisFieldParams params = new DebrisFieldParams(300f, -1f, 10000000f, 0f);
        params.source = DebrisFieldSource.MIXED;
        SectorEntityToken debris = Misc.addDebrisField(system, params, new Random(20261114L));
        debris.setCircularOrbit(ship, 0f, 0f, 100f);

        // 只生成通往超空间的跳跃点：第一个参数为气态巨行星入口，这里不需要
        system.autogenerateHyperspaceJumpPoints(false, true);
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 在控制台执行 `jump Ashen Verge`，舰队直接进入该无恒星星系，背景色调呈灰褐色且实体没有怪异的单侧黑影；
- 打开星图可以看到位于 Nipher 东北方向的 `Ashen Verge`，从超空间可经边缘跳跃点正常进出；
- 靠近灰烬残骸与封存的补给箱，点击可弹出原版打捞界面；
- 勘测船残骸周围环绕着一片残骸场，驶入其中时左下角状态区域会出现残骸场隐蔽加成提示。

## 常见问题

- **星图上看得到 `Ashen Verge`，但在超空间里找不到进入的跳跃点**：漏掉了 `autogenerateHyperspaceJumpPoints(false, true)`，或把第二个参数传成了 `false`。
    - **解决方案**：对无行星星系调用 `system.autogenerateHyperspaceJumpPoints(false, true)` 生成边缘跳跃点。

- **无恒星星系内实体一半亮一半黑，像有一颗看不见的太阳在照射**：调用了 `initNonStarCenter()` 与 `setLightColor(...)`，但漏掉了 `center.addTag(Tags.AMBIENT_LS)`。
    - **解决方案**：给 `initNonStarCenter()` 返回的中心实体加上 `Tags.AMBIENT_LS` 标签。

- **靠近遗迹实体后无法点击打开打捞对话**：`addCustomEntity(...)` 的第三个类型参数拼写错误，或选用了不带 `has_interaction_dialog` 标签的纯装饰实体类型。
    - **解决方案**：使用 `Entities.DERELICT_SURVEY_SHIP`、`Entities.SUPPLY_CACHE` 等原版可打捞常量。

## 建议参考项

- `com.fs.starfarer.api.campaign.StarSystemAPI`：`initNonStarCenter()`、`setLightColor(...)` 与 `autogenerateHyperspaceJumpPoints(...)`。
- `com.fs.starfarer.api.campaign.LocationAPI`：实体与地形的添加及检索方法。
- `com.fs.starfarer.api.impl.campaign.world.GateHaulerLocation`：原版无恒星星系的完整生成实现。
- `com.fs.starfarer.api.impl.campaign.ids.Entities`：原版自定义实体类型 ID 常量。
- `com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin`：残骸场地形参数与生成逻辑。
- `data/config/custom_entities.json`：原版自定义实体的标签与默认属性配置。
