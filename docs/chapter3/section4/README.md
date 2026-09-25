# 制作一个生涯星系

本节使用原版恒星、行星和通讯中继建立一个真正可进入的新星系，再给主行星接入能够交易的基础市场。读者沿生成顺序放置对象，并从星图、实体详情和交易界面确认结果。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 列出新档生成入口的调用顺序；
- 把恒星、行星、原版生涯实体和跳跃点放进同一星系；
- 判断 `initStar`、`addPlanet`、`addCustomEntity` 与 `autogenerateHyperspaceJumpPoints` 的职责；
- 把原版市场、产业和子市场绑定到主行星；
- 用星图、轨道、实体和交易界面验证生成结果。

## 前置知识

- [C3-S1] 的 GearIn 势力 ID；
- [C3-S3] 的 `GearInModPlugin` 与 `onNewGame()` 入口；
- 理解原版对象 ID 可以直接用于星系生成；
- [C7-S1] 的完整 ModPlugin 生命周期属于后续章节，本页只使用新档回调。

## 先把对象放入正确的顺序

生成器按这条链工作：

`createStarSystem → initStar → addPlanet → addCustomEntity → createJumpPoint → autogenerateHyperspaceJumpPoints`

链上各步创建的实体最终要加入星系容器：`addPlanet` / `addCustomEntity` 会自动加入容器，但 `createJumpPoint` 只创建不加入，需要显式 `system.addEntity(...)`。类型由数据文件提供，位置由生成器提供。

## 逐步接通生成入口

### 创建星系容器

```java
StarSystemAPI system = sector.createStarSystem("GearIn");
system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
system.setLightColor(new Color(200, 200, 210));
```

新档星图出现 `GearIn`，星系内部有背景和基础光照。

### 配置星体类型

```java
PlanetAPI star = system.initStar("gearin", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "GearIn Prime",
        "terran", 55, 150, 3200, 120);
```

GearIn Prime 使用原版 `terran` 外观，恒星使用原版 `star_yellow`，恒星 ID `gearin` 与超空间位置键一致。

### 投放原版实体

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_stable", "GearIn 通讯中继",
        "comm_relay", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

地图上出现使用原版 `comm_relay` 类型的 GearIn 通讯中继，实体归属 GearIn 势力。

### 接入基础市场

```java
MarketAPI market = Global.getFactory().createMarket(prime.getId(), prime.getName(), 4);
market.setFactionId("gear_industry");
market.setPrimaryEntity(prime);
market.addCondition("population_4");
market.addIndustry("population");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("storage");
sector.getEconomy().addMarket(market, true);
prime.setMarket(market);
prime.setFaction("gear_industry");
```

GearIn Prime 可以打开公开市场和仓储，交易界面显示原版商品。完整市场配置与自定义商品由 [C12-S1] 和 [C12-S3] 继续讲解。

### 补齐超空间入口

```java
JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
jump.setCircularOrbit(star, 300, 1800, 100);
jump.setRelatedPlanet(prime);
system.addEntity(jump);
system.autogenerateHyperspaceJumpPoints(true, true);
```

从超空间进入 GearIn 星系，跳跃点与 GearIn Prime 的关联可见。

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import data.scripts.gearin.campaign.GearInSystemGenerator;

/**
 * GearIn 的 MOD 入口类。
 * mod_info.json 的 modPlugin 字段指向本类；游戏在新建生涯时调用 onNewGame()。
 */
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

/**
 * 本节示例：使用原版类型制作一个带市场、恒星、行星、生涯实体与跳跃点的星系。
 * 实现 SectorGeneratorPlugin 是采用生成器接口的写法 (原版 data.scripts.world.SectorGen
 * 就是 SectorGeneratorPlugin)；实际调用入口是 GearInModPlugin.onNewGame。
 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

	@Override
	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("GearIn");
		system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
		system.setLightColor(new Color(200, 200, 210));

		// 恒星 id "gearin" 同时是星系在 starmap.json 里的注册键：initStar 会用它查
		// data/campaign/starmap.json 的 starSystemLocations，把结果设为星系在超空间中的坐标。
		// 不提供该键 (又不手动 setLocation) 时，星系会定位在超空间原点 (0,0)。
		PlanetAPI star = system.initStar("gearin", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);

		// addPlanet(id, 轨道焦点, 显示名, 类型id, 轨道角, 半径, 轨道半径, 轨道周期天数)
		PlanetAPI prime = system.addPlanet("GearIn_planet1", star, "GearIn Prime", "terran", 55, 150, 3200, 120);
		PlanetAPI second = system.addPlanet("GearIn_planet2", star, "GearIn Second", "barren", 200, 90, 5200, 200);

		// 使用原版通讯中继类型，factionId 仍指向本章注册的 GearIn 势力。
		SectorEntityToken stable = system.addCustomEntity("GearIn_stable", "GearIn 通讯中继", "comm_relay", "gear_industry");
		stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

		// 最小市场只使用原版条件、产业与子市场，让新星系能够打开交易界面。
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

		// 手动跳跃点：创建后必须 addEntity 加入星系，autogenerate 阶段才会为它生成超空间侧入口
		JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
		jump.setCircularOrbit(star, 300, 1800, 100);
		jump.setRelatedPlanet(prime);
		system.addEntity(jump);

		// 必须调用，否则星系没有通向超空间的跳跃点，玩家无法到达
		system.autogenerateHyperspaceJumpPoints(true, true);

	}
}
```

:::

## 验证方案

前置数据：已启用包含 [C3-S3] 入口类和本节 `GearInSystemGenerator` 的 GearIn MOD。

1. 确认 `mod_info.json` 的 `modPlugin` 仍指向 `data.scripts.gearin.GearInModPlugin`，创建新档。
2. 在星图中定位 GearIn，进入星系并观察恒星、GearIn Prime、通讯中继和跳跃点。
3. 靠近 GearIn Prime，打开市场并进入公开市场或仓储。
4. 从超空间退出并重新进入，确认入口和轨道关系仍然存在。

预期结果：

- 新档创建后出现 GearIn 星系；
- GearIn Prime 使用原版 `terran` 类型，恒星使用 `star_yellow`；
- 通讯中继使用原版 `comm_relay` 类型并属于 GearIn；
- GearIn Prime 的公开市场和仓储可以打开，并显示原版商品；
- 手动跳跃点进入实体集合并生成超空间侧入口。

## 故障分析

- **新星系位于超空间原点**：`starmap.json` 的 `starSystemLocations` 没有恒星 ID `gearin`，且生成器没有显式设置位置。
    - **解决方案**：补齐位置数据或生成调用后创建新档。

- **行星或实体没有出现**：类型 ID 已加载，但对象没有加入同一个 `system` 容器时，生成器不会自动补救。
    - **解决方案**：检查 `addPlanet` 和 `addCustomEntity` 的容器参数，重新创建新档。

- **新档没有任何变化**：`onNewGame()` 只在新档生成阶段调用，旧存档不会重新执行。
    - **解决方案**：删除测试存档，确认 `modPlugin` 已启用并重新创建新档。

## 总结

星系生成器负责建立容器、放置原版对象、生成入口并把基础市场绑定到主行星。所有使用的类型和市场组件已经由原版注册，因此本节只需要选择 ID、位置和归属。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorGeneratorPlugin`：星系生成器接口；
- `com.fs.starfarer.api.campaign.StarSystemAPI`：恒星系对象与实体容器；
- `data/config/planets.json`：原版恒星与行星类型；
- `data/config/custom_entities.json`：原版生涯实体类型；
- `starmap.json`：超空间位置注册。
