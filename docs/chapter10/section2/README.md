# 构建生涯地点

`LocationAPI` 管的不是某一件东西，是一整个地点里的全部东西：实体、地形、轨道、跳跃点、市场挂点。

实体与地形在实现上是同一个父类，用法却分两路。实体是一件能被选中的东西，地形是一个半径，对进入其中的舰队起作用。加错类型的后果，玩家比作者先看见。

原版那 89 条自定义实体里，能开交互的只有 27 条。多数条目造出来只是摆在那里，从它身上拿不到任何东西。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~25 分钟。_

## 前置知识

- [C3-S3] 的星系生成器，包括 `createStarSystem(...)` 与 `autogenerateHyperspaceJumpPoints(...)`；
- [C7-S1] 的 `onNewGame()`，新地点只在新档中生成；
- [C10-S1] 的 `$GearIn_contacted`，本节的地点不依赖它，后续章节会把二者联系起来。

## 没有恒星的星系

[C3-S3] 的 Nipher 以恒星为中心，行星与中继站都绕着它转。Ashen Verge 没有恒星，创建方式有三处不同：

- 用 `initNonStarCenter()` 代替 `initStar(...)`，它返回一个空的中心点，实体可以把它当作轨道焦点；
- 没有恒星就没有光源，而光照设置是**两行配套**的：`setLightColor(...)` 给出颜色，中心点再打上 `Tags.AMBIENT_LS` 标签，见下；
- 星系在超空间中的位置仍由 `getLocation().set(x, y)` 指定，坐标应当避开已有星系。Nipher 位于 (-16000, -9000)，Ashen Verge 取 (-13500, -4500)，两者相距约 5100。

```java
StarSystemAPI system = sector.createStarSystem(SYSTEM_NAME);
system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
system.getLocation().set(-13500f, -4500f);

SectorEntityToken center = system.initNonStarCenter();
system.setLightColor(new Color(120, 110, 100));
center.addTag(Tags.AMBIENT_LS);
```

**`Tags.AMBIENT_LS` 这一行不能省。** `setLightColor(...)` 的接口注释写的是 "只适用于有光源的地点 (即有恒星)"，照字面看，无恒星星系根本没有光照可设。原版所有无恒星星系都绕过这句话：它们先 `initNonStarCenter()`，再设颜色，最后给中心点打上 `AMBIENT_LS`。

这个标签的作用在渲染代码里看得见。实体绘制时会判断光源有没有这个标签，没有就按方向计算并绘制阴影遮罩；有就跳过。中心点不是恒星，不打标签，它仍然会被当成光源，实体上会盖上按方向算出来的阴影。原版的 `GateHaulerLocation`、`AbyssalRogueStellarObjectEPEC` 与程序生成器删掉恒星的那条分支，都是这两行连着写。

`SYSTEM_NAME` 是 `"Ashen Verge"`。星系名既是玩家在星图上看到的名字，也是 `sector.getStarSystem(...)` 的查询键，改名时两处要一起改。

::: info 原版的写法

原版的无恒星星系大多出自 `GateHaulerLocation` 与 `AbyssalRogueStellarObjectEPEC`，它们在 `initNonStarCenter()` 之后同样设置光照颜色。本节沿用这一写法，但不放中心天体，焦点只用来定轨道。

:::

## 实体与地形

地点里放的东西都挂在 `LocationAPI` 上，取用方法分成两套。

**实体**是 `SectorEntityToken`，有坐标、有轨道、有归属势力，能被选中、能开交互界面。遗迹、信标、行星、跳跃点都是实体。

**地形**是 `CampaignTerrainAPI`，它继承 `SectorEntityToken`，同样有坐标和轨道，但多了一层半径：它是一片圆形区域，`setRadius(...)` 定大小，`containsEntity(...)` 判断某个实体在不在里面。残骸场、小行星带、星云、日冕、磁暴区都是地形。地形对进入其中的舰队起作用，没有实体那样的选中与交互。

两套各自的加入方法都在 `LocationAPI` 上：

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

`createToken(...)` 造一个空的实体，自己配上轨道再加进去。`addEntity(...)` 收的是已经造好的实体，跳跃点走这条。`addCustomEntity(...)` 按 `custom_entities.json` 里的类型条目造实体，贴图、插件、标签都来自那条条目。

地形用 `addTerrain(...)`，第一个参数是地形类型 ID，第二个是这种地形自己的参数对象。小行星带、日冕、环带另有专门的方法，参数已经拆开，比 `addTerrain(...)` 少一层包装。

**这一段的分工决定了后面每一件的写法。** 要能被选中、要有交互、要归属某个势力，就用实体；只对进入其中的舰队起作用，就用地形。残骸场按实体加，玩家点不到它，也看不到说明。

## 放入可打捞的遗迹

遗迹不必自己制作。原版 `custom_entities.json` 中，勘测船残骸 `derelict_survey_ship` 的 `tags` 写着 `["has_interaction_dialog", "salvageable", "neutrino"]`，补给箱 `supply_cache` 同样带 `salvageable`。放进地点，玩家靠近就能打捞，不需要另写交互。一处没有恒星的地点，留得住玩家的也就是这点能捡的东西，两样足够，再多就成了垃圾场。

**打捞有两种制作者常见的来源，容易混。** 一种是上面这种在 `custom_entities.json` 里写好标签的成品实体，`addCustomEntity(...)` 时给对 ID 就带打捞功能；另一种是 `salvage_entity_gen_data.csv` 里的打捞规格，原版程序生成星系时按它随机撒遗迹，通过 `BaseThemeGenerator.addSalvageEntity(...)` 创建。本节要的是一处固定的遗迹，用第一种。

`addCustomEntity(id, name, type, factionId)` 创建实体，类型取自 `Entities` 中的常量，势力用 `Factions.NEUTRAL`。位置用轨道指定：

```java
SectorEntityToken ship = system.addCustomEntity(
        "GearIn_ashen_wreck", "灰烬残骸", Entities.DERELICT_SURVEY_SHIP, Factions.NEUTRAL);
ship.setCircularOrbitPointingDown(center, 40f, 900f, 80f);

SectorEntityToken cache = system.addCustomEntity(
        "GearIn_ashen_cache", "封存的补给箱", Entities.SUPPLY_CACHE, Factions.NEUTRAL);
cache.setCircularOrbit(center, 220f, 1400f, 120f);
```

`setCircularOrbit(focus, angle, orbitRadius, orbitDays)` 的四个参数依次是焦点、起始角度、轨道半径与公转天数。`PointingDown` 的版本让实体的贴图始终朝向焦点。

这个重载有四种写法。带 `radius, spriteWidth, spriteHeight` 的版本覆盖条目里的尺寸；带 `pluginParams` 的版本把一个对象传给条目上登记的插件，正文用不到，做交互界面时才有用：

```java
CustomCampaignEntityAPI addCustomEntity(String id, String name, String type, String factionId,
        Object pluginParams);
```

**在自己的 `custom_entities.json` 里新增条目时，`baseId` 会让新条目继承父条目的全部字段。** 补给箱那一族都这么写：`weapons_cache`、`equipment_cache`、`technology_cache` 各自只写自己不同的几行，`tags` 一个都不重复，全部从 `supply_cache` 继承。要改其中一条的标签，写一遍 `tags` 就会整体覆盖继承来的那一份，不是追加。

### 信标

生成器里还放了一座信标，用原版的导航浮标 `Entities.NAV_BUOY`，归属齿轮工业。它的创建方式与遗迹相同，细节留给 [C10-S4]：

```java
SectorEntityToken beacon = system.addCustomEntity(
        BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
beacon.setInteractionImage("illustrations", "comm_relay");
```

`BEACON_ID` 为 `"GearIn_ashen_beacon"`，是后续脚本查找这座信标的稳定 ID。

### 残骸场

残骸场是地形，不是实体，用 `Misc.addDebrisField(...)` 加入，再把轨道焦点设为勘测船：

```java
DebrisFieldParams params = new DebrisFieldParams(300f, -1f, 10000000f, 0f);
params.source = DebrisFieldSource.MIXED;
SectorEntityToken debris = Misc.addDebrisField(system, params, new Random(20261114L));
debris.setCircularOrbit(ship, 0f, 0f, 100f);
```

构造参数依次是场的半径、视觉密度、持续天数与发光天数。密度传 `-1` 由游戏随机取值，持续天数取极大值表示不会消散。半径不要超过 1000，再大就会拖慢性能。

### 进入星系的跳跃点

```java
system.autogenerateHyperspaceJumpPoints(false, true);
```

第一个参数控制是否在气态巨行星处生成单向入口，这里没有行星，传 `false`；第二个参数在星系边缘生成一个双向跳跃点，Ashen Verge 靠它进出，传 `true`。

**忘记这一行，星系存在，玩家却进不去。** 星图上看得到它，跳跃点列表里没有。

要自己指定跳跃点的位置、名字与方向，不用这个自动方法，改用工厂造再加进地点。**`LocationAPI` 与 `StarSystemAPI` 上都没有 `addJumpPoint`。** 原版四处做法一致，先 `createJumpPoint(...)`，再 `addEntity(...)`：

```java
JumpPointAPI point = Global.getFactory().createJumpPoint(null, "Fringe Jump-point");
point.setStandardWormholeToHyperspaceVisual();
point.setCircularOrbit(center, 270f, 2600f, 100f);
system.addEntity(point);
```

第一个参数是实体 ID，传 `null` 由游戏生成。`setStandardWormholeToHyperspaceVisual()` 给出默认的跳跃点外观，不调用它，跳跃点画出来是空的。

## 实体放进地点之后能改什么

`SectorEntityToken` 上有几组方法，放进地点之后随时可以用。做固定地点的人迟早会碰到它们。

**轨道**有四个写法，选哪个决定了实体怎么动：

```java
void setFixedLocation(float x, float y);
void setCircularOrbit(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays);
void setCircularOrbitPointingDown(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays);
void setCircularOrbitWithSpin(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays,
        float minSpin, float maxSpin);
```

`setFixedLocation(...)` 钉在一个坐标上，并清掉已有轨道。`setCircularOrbit(...)` 绕焦点公转，贴图角度不变。`PointingDown` 让它始终朝向焦点，行星、残骸、信标都用这个。`WithSpin` 额外加自转，范围是 `minSpin` 到 `maxSpin` 之间的随机角速度。

**交互**决定玩家点开它时看到什么。`setInteractionImage(category, key)` 换交互界面的插图；`setCustomDescriptionId(...)` 换成自己的描述文本；`addFloatingText(...)` 在实体上方挂一行提示。**能开交互的前提是条目带 `has_interaction_dialog` 标签**，`custom_entities.json` 里 89 条只有 27 条带它。没有这个标签的实体，调用上面这些方法不会让玩家点开它。

**传感器**决定玩家多久能发现它：

```java
float getSensorProfile();
void setSensorProfile(Float sensorProfile);
void setSensorStrength(Float sensorStrength);
void setDiscoverable(Boolean discoverable);
void setDetectionRangeDetailsOverrideMult(Float mult);
```

`setSensorProfile(...)` 改它有多容易被发现，改小之后玩家要靠近才看得见。`setDiscoverable(false)` 让它完全无法被发现，做隐藏内容时用。`setDetectionRangeDetailsOverrideMult(...)` 乘在计算出的大致探测距离上，只影响提示里那个距离数字，不改变实际探测。

**战利品**挂在实体上，打捞时由游戏读取：

```java
void addDropValue(String group, int value);
void addDropRandom(String group, int chances);
void setDiscoveryXP(Float discoveryXP);
void setSalvageXP(Float salvageXP);
```

`group` 取自 `data/campaign/procgen/drop_groups.csv` 里的组名。`addDropValue(...)` 加一份固定量的战利品，`addDropRandom(...)` 按概率加。两个 XP 方法给首次发现和首次打捞的经验。原版成品遗迹大多已经在 `custom_entities.json` 里配好，自己做遗迹时才需要这些。

## 在新档中生成

在 `GearInModPlugin.onNewGame()` 中，于 Nipher 之后调用：

```java
new GearInSystemGenerator().generate(sector);
new GearInAshenVergeGenerator().generate(sector);
seedRelations(sector);
```

与 Nipher 相同，旧存档不会生成这个星系，见 [C7-S1]。

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

- 控制台执行 `jump Ashen Verge`，舰队出现在星系的中心点附近；
- 在星图上能看到 Ashen Verge，从超空间可以经边缘跳跃点进入；
- 靠近灰烬残骸与封存的补给箱，弹出打捞对话；
- 残骸场围绕在勘测船周围。进入其中后，悬停或慢速移动的舰队更不容易被探测到，鼠标悬停在残骸场上可以看到这段说明。

## 常见问题

- **星图上有 Ashen Verge，却找不到进入的跳跃点**：没有调用 `autogenerateHyperspaceJumpPoints(...)`，或第二个参数为 `false`。
    - **解决方案**：传 `(false, true)`，让星系边缘生成双向跳跃点。

- **星系内一片漆黑**：没有恒星，也没有设置光照颜色。
    - **解决方案**：在 `initNonStarCenter()` 之后调用 `setLightColor(...)`。

- **两个星系在超空间中重叠**：坐标与已有星系过近。
    - **解决方案**：与 Nipher 的 (-16000, -9000) 拉开距离，本节取 (-13500, -4500)。

- **旧存档中看不到这个星系**：生成放在 `onNewGame()`，旧存档不会再触发。
    - **解决方案**：使用新档测试。

- **实体靠近后没有打捞对话**：类型字符串写错，或写成了自定义类型而非原版 `Entities` 常量。
    - **解决方案**：使用 `Entities.DERELICT_SURVEY_SHIP` 与 `Entities.SUPPLY_CACHE`。

## 建议参考项

- `com.fs.starfarer.api.campaign.StarSystemAPI`：`initNonStarCenter()` 与 `autogenerateHyperspaceJumpPoints(...)`，本节创建星系用的就是这两个。
- `com.fs.starfarer.api.impl.campaign.world.GateHaulerLocation`：原版无恒星星系的完整写法。
- `com.fs.starfarer.api.impl.campaign.ids.Entities`：原版自定义实体类型常量。
- `com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin`：残骸场参数类。
- `data/config/custom_entities.json`：各实体的标签与交互设置。
