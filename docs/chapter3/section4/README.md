# 制作一个生涯星系

势力已经注册，入口类也能在新档中执行代码。本节继续通过这个入口生成 GearIn 星系：放入恒星、两颗行星和通讯中继，给主行星建立基础市场，再生成超空间入口。原版已经提供这些对象的类型，我们主要决定名称、位置与归属。

_本节预期阅读时间：\~14 分钟。_

_本节预期操作时间：\~15 分钟。_

## 学习目标

完成后，您将能够：

- 从新档回调调用星系生成器；
- 放置恒星、行星、通讯中继与跳跃点；
- 区分创建星系、创建实体与加入实体的操作；
- 给主行星绑定基础市场、产业与子市场；
- 在星图、轨道、交互和交易界面核对生成结果。

## 前置知识

- [C3-S1] 的 `gear_industry` 势力；
- [C3-S3] 的 `GearInModPlugin` 与新档回调；
- [C2-S1] 的 Java 文件、包名与类名对应关系；
- 能够在生涯星图中定位星系、通过跳跃点移动并与行星交互。

## 创建星系生成器并接入入口

创建 `data/scripts/gearin/campaign/GearInSystemGenerator.java`。这个类采用 `SectorGeneratorPlugin` 的生成器结构，操作都写在 `generate(SectorAPI sector)` 方法中，完整文件在后面给出。

回到上一节的 `GearInModPlugin.java`，在 import 区加入：

```java
import data.scripts.gearin.campaign.GearInSystemGenerator;
```

再把新档回调改为下面的局部片段：

```java
@Override
public void onNewGame() {
    new GearInSystemGenerator().generate(Global.getSector());
    seedRelations(Global.getSector());
}
```

第一条语句创建生成器并调用 `generate`，第二条继续设置关系。`mod_info.json` 的入口值仍指向 `GearInModPlugin`。**生成器由入口主动调用**，因此保存生成器类文件后，还要在入口中保留这条调用。

接下来依次向 `generate` 中加入代码。每段创建的对象会供后面的操作使用，因此按当前顺序排列。

## 建立星系、恒星与超空间位置

先创建星系容器：

```java
StarSystemAPI system = sector.createStarSystem("GearIn");
system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
system.setLightColor(new Color(200, 200, 210));
```

`system` 保存刚创建的星系，背景路径和光照颜色采用原版资源。之后的行星、实体和入口都加入这个 `system`，让它们属于同一个地点。

再初始化恒星并设置超空间位置：

```java
PlanetAPI star = system.initStar("gearin", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
system.getLocation().set(-16000f, -9000f);
```

`gearin` 是恒星 ID，`star_yellow` 是原版黄色恒星类型。`star` 保存创建得到的恒星，行星和通讯中继随后以它作为轨道焦点，因此这一步放在行星创建之前。

### initStar 参数速查表

| 参数             | 当前填写      |
| ---------------- | ------------- |
| 恒星 ID          | `gearin`      |
| 恒星类型         | `star_yellow` |
| 恒星半径         | 600           |
| 日冕范围         | 550           |
| 恒星风移动影响   | 3             |
| 耀斑概率         | 0.75          |
| 日冕 CR 损耗倍率 | 1             |

**星系内部的轨道位置与星系在超空间中的位置分别设置。** `system.getLocation().set(...)` 使用超空间坐标，本例选取 `(-16000,-9000)`。它决定 GearIn 在星图中的位置，调整时可以先以原版核心区作为参照，再留出希望的航程。

恒星初始化也会按恒星 ID 查询 `data/campaign/starmap.json` 中的 `starSystemLocations`。本例直接写超空间坐标；采用数据表定位时，登记 `gearin` 对应的位置即可。

## 放置两颗行星

在恒星之后添加主行星与外侧行星：

```java
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "GearIn Prime",
        "terran", 55, 150, 3200, 120);
PlanetAPI second = system.addPlanet(
        "GearIn_planet2", star, "GearIn Second",
        "barren", 200, 90, 5200, 200);
```

`GearIn_planet1`、`GearIn_planet2` 是实体 ID，Prime 与 Second 是显示名。第二个参数 `star` 指定轨道焦点，`terran` 和 `barren` 则选择原版行星类型。

`addPlanet` 后四个数字依次为轨道角、行星半径、轨道半径和轨道周期天数。主行星半径为 150，轨道半径为 3200，周期为 120 天；外侧行星半径 90，轨道半径 5200，周期 200 天。**轨道焦点与轨道参数共同确定行星的位置关系**，可以按代码中的对象与数值核对星图。

`addPlanet` 同时把行星加入当前星系。它返回的对象分别保存在 `prime` 与 `second` 中，后面的市场、描述等配置继续使用这两个引用。

## 加入原版通讯中继

在同一个 `system` 中添加中继：

```java
SectorEntityToken stable = system.addCustomEntity(
        "GearIn_stable", "GearIn 通讯中继",
        "comm_relay", "gear_industry");
stable.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

`comm_relay` 是原版实体类型，`gear_industry` 指定所属势力。返回的中继实体保存在 `stable` 中，随后通过它设置围绕恒星的轨道，仍然使用刚才的 `star` 作为焦点。

轨道角为 90，半径为 2200，周期为 100 天，`PointingDown` 采用该类实体常用的朝向方式。原版实体类型可以直接用于本节制作，自定义类型则由 [C4-S6] 展开。

## 给主行星建立基础市场

行星的外观类型与它的市场分别配置，`terran` 类型本身不会完成这里的市场创建。本节给 Prime 创建规模 4 的市场，使用人口、太空港、公开市场和仓储这些原版组件。

先建立市场与行星的归属，局部片段如下：

```java
MarketAPI market = Global.getFactory().createMarket(prime.getId(), prime.getName(), 4);
market.setFactionId("gear_industry");
market.setPrimaryEntity(prime);
market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
```

`createMarket` 使用行星 ID、名称与规模，创建得到的市场保存在 `market` 中。`setPrimaryEntity` 将 Prime 指定为主实体，`FULL` 把勘测状态设为已完成。归属关系设置后，再添加市场内容：

```java
market.addCondition("population_4");
market.addIndustry("population");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("storage");
```

这些字符串都是原版已注册的 ID，分别写入市场的条件、产业与子市场。市场条件描述规模 4 的人口，产业提供人口与太空港，子市场提供公开交易与仓储。

最后把它加入星区经济，并连接回行星：

```java
sector.getEconomy().addMarket(market, true);
prime.setMarket(market);
prime.setFaction("gear_industry");
```

**创建市场、加入经济与绑定实体都要完成。** 使用行星的 ID 和名称创建市场之后，仍要通过上面的调用建立实际连接。完成后，玩家靠近 Prime 就可以打开对应市场。当前配置用于验证交易与归属，市场的完整行为与自定义商品由 [C12-S1]、[C12-S3] 继续讲解。

## 生成往返星系的入口

创建跳跃点并设置轨道：

```java
JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
jump.setCircularOrbit(star, 300, 1800, 100);
jump.setRelatedPlanet(prime);
system.addEntity(jump);
system.autogenerateHyperspaceJumpPoints(true, true);
```

`createJumpPoint` 先返回跳跃点对象，`system.addEntity(jump)` 再把它加入星系。与前面的 `addPlanet`、`addCustomEntity` 相比，这个创建方法需要单独完成加入操作。

`autogenerateHyperspaceJumpPoints` 根据星系内部的入口生成超空间端，并连接目的地。两个 `true` 分别允许生成气态巨行星入口与边缘跳跃点。**在调整星系时，入口位置也属于设计的一部分**：玩家抵达后还需要从这里驶向行星，轨道间距直接影响这段航程。

## 完整最小例

入口类保存到 `data/scripts/gearin/GearInModPlugin.java`，生成器保存到 `data/scripts/gearin/campaign/GearInSystemGenerator.java`。入口声明继续采用 [C3-S3] 的完整类名。

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** 在新档生成时建立齿轮工业星系与初始关系。 */
public class GearInModPlugin extends BaseModPlugin {

    @Override
    public void onNewGame() {
        new GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
```

:::

::: details 完整成品：GearInSystemGenerator

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 创建包含两颗行星、通讯中继、基础市场与跳跃点的齿轮工业星系。 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("GearIn");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.setLightColor(new Color(200, 200, 210));

        PlanetAPI star = system.initStar("gearin", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
        system.getLocation().set(-16000f, -9000f);

        PlanetAPI prime = system.addPlanet(
                "GearIn_planet1", star, "GearIn Prime", "terran", 55, 150, 3200, 120);
        PlanetAPI second = system.addPlanet(
                "GearIn_planet2", star, "GearIn Second", "barren", 200, 90, 5200, 200);

        SectorEntityToken stable = system.addCustomEntity(
                "GearIn_stable", "GearIn 通讯中继", "comm_relay", "gear_industry");
        stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

        MarketAPI market = Global.getFactory().createMarket(prime.getId(), prime.getName(), 4);
        market.setFactionId("gear_industry");
        market.setPrimaryEntity(prime);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_4");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("storage");
        sector.getEconomy().addMarket(market, true);
        prime.setMarket(market);
        prime.setFaction("gear_industry");

        JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
        jump.setCircularOrbit(star, 300, 1800, 100);
        jump.setRelatedPlanet(prime);
        system.addEntity(jump);
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}
```

:::

## 验证方案

1. 保存入口和生成器，重启游戏，确认 `modPlugin` 指向 `data.scripts.gearin.GearInModPlugin`，创建新生涯。
2. 在星图定位 GearIn，从超空间入口进入，核对黄色恒星、Prime、Second 和通讯中继。
3. 查看轨道焦点与相对距离，核对中继归属齿轮工业。
4. 靠近 Prime，进入公开市场和仓储，观察规模、商品与所属势力。
5. 返回超空间再进入，确认星系入口与跳跃点连接正常。

预期结果：

- GearIn 位于设置的超空间坐标，新档中可以进入。
- 恒星使用 `star_yellow`，Prime 使用 `terran`，Second 使用 `barren`，轨道分别围绕恒星。
- 中继采用 `comm_relay`，归属 `gear_industry`。
- Prime 的规模 4 市场可以打开公开交易与仓储，勘测状态为已完成。
- 星系内的手动跳跃点与超空间端连接，玩家可以往返。

## 故障分析

- **星系位于超空间原点**：位置表缺少恒星 ID，生成器也未写入超空间位置。
    - **解决方案**：在恒星初始化后设置 `system.getLocation()`，或登记 `starmap.json` 位置，保存后创建新档。

- **行星或实体没有出现**：创建使用了不同星系容器，或跳跃点尚未加入实体集合。
    - **解决方案**：核对当前 `system`，为工厂创建的跳跃点调用 `addEntity`，重新创建生涯。

- **新档没有生成星系**：入口未加载，或回调未调用生成器。
    - **解决方案**：核对入口声明、import 和 `generate` 调用，重启并创建测试生涯。

- **行星没有可交易市场**：市场未加入经济、主实体未指定或子市场 ID 填错。
    - **解决方案**：核对市场创建、经济登记、实体绑定与 `open_market`、`storage`，创建新档验证。

## 总结

本节从新档回调调用生成器，建立星系与实体，再为 Prime 接入基础市场，并生成超空间入口。对象类型来自原版定义，代码提供位置、轨道与归属，各步骤返回的对象用于后续配置。

现在已经有了一个能进入、交互与交易的星系。继续扩充时，沿着同样的对象关系加入内容，再分别核对星图、轨道和交互结果；下一节为势力、系统和行星补上各自的描述。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorGeneratorPlugin`：生成器的 `generate` 方法。
- `com.fs.starfarer.api.campaign.SectorAPI`：创建星系和取得星区经济。
- `com.fs.starfarer.api.campaign.StarSystemAPI`：初始化恒星、放置实体、超空间位置与入口生成。
- `com.fs.starfarer.api.campaign.JumpPointAPI`：跳跃点对象与行星关联。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场组件、主实体与勘测状态。
- `data/config/planets.json`：原版恒星与行星类型。
- `data/config/custom_entities.json`：原版生涯实体类型。
- `data/campaign/starmap.json`：`starSystemLocations` 超空间位置数据。
