# 制作一个生涯星系

本节把 [C3-S4] 的余烬世界和 [C3-S5] 的导航锚放进一个真正可进入的新星系。Java 只保留已经存在的生成入口模板，读者主要通过替换类型 ID 和观察星图结果完成任务。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

* 说出新档生成入口的调用顺序；
* 把恒星、行星、自定义实体和跳跃点放进同一星系；
* 判断 `initStar`、`addPlanet`、`addCustomEntity` 与 `autogenerateHyperspaceJumpPoints` 的职责；
* 用星图、轨道和实体可见性验证生成结果。

## 前置知识

* [C3-S3] 的 GearIn 势力 ID；
* [C3-S4] 的 `GearIn_ember_world`；
* [C3-S5] 的 `GearIn_navigation_anchor`；
* [C6-S2] 的完整 ModPlugin 生命周期属于后续章节，本页只复用既有入口。

## 先把对象放入正确的顺序

生成器按这条链工作：

`createStarSystem → initStar → addPlanet → addCustomEntity → createJumpPoint → autogenerateHyperspaceJumpPoints`

每一步都会把一个可观察对象加入同一个 `StarSystemAPI` 容器。类型由数据文件提供，位置由生成器提供。

## 逐步接通生成入口

### 创建星系容器

```java
StarSystemAPI system = sector.createStarSystem("GearIn");
system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
system.setLightColor(new Color(200, 200, 210));
```

完成信号：新档星图出现 `GearIn`，星系内部有背景和基础光照。

### 配置星体类型

```java
PlanetAPI star = system.initStar("gearin", "star_white", 600f, 550f, 3f, 0.75f, 1f);
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "GearIn Prime",
        "GearIn_ember_world", 55, 150, 3200, 120);
```

完成信号：GearIn Prime 使用 [C3-S4] 定义的外观，恒星 ID `gearin` 与超空间位置键一致。

### 投放自定义实体

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_stable", "GearIn 导航锚",
        "GearIn_navigation_anchor", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

完成信号：地图上出现 GearIn 导航锚，实体归属 GearIn 势力。

### 补齐超空间入口补齐超空间入口

```java
JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
jump.setCircularOrbit(star, 300, 1800, 100);
jump.setRelatedPlanet(prime);
system.addEntity(jump);
system.autogenerateHyperspaceJumpPoints(true, true);
```

完成信号：从超空间进入 GearIn 星系，跳跃点与 GearIn Prime 的关联可见。

## 排查星系生成故障

下面是**局部故障片段**：

```java
JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
jump.setCircularOrbit(star, 300, 1800, 100);
// 遗漏 system.addEntity(jump)
system.autogenerateHyperspaceJumpPoints(true, true);
```

实际症状：生成器没有异常，但星系内找不到手动跳跃点，玩家无法通过预期入口观察关联行星。

原因：工厂创建的跳跃点尚未进入星系实体集合，自动生成阶段不会处理它。

下面是**局部修正片段**：

```java
system.addEntity(jump);
system.autogenerateHyperspaceJumpPoints(true, true);
```

复验：删除旧存档创建新档，从超空间进入 GearIn，确认跳跃点和导航锚都存在。

::: details 完整成品：GearInSystemGenPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 本节示例：最小形态的 ModPlugin，本节的生成入口类。
 * mod_info.json 的 modPlugin 字段指向本类；游戏在新建生涯时调用 onNewGame()。
 * ModPlugin 各回调的完整生命周期由 C6-S2 讲解（其所有权示例类 GearInModPlugin
 * 位于 data.scripts.gearin 包），本节只保留 onNewGame() 这一个生成入口。
 */
public class GearInSystemGenPlugin extends BaseModPlugin {

	@Override
	public void onNewGame() {
		// 生成入口：新档时序里，所有 MOD 的 onNewGame 在原版 SectorGen、
		// generators.csv 插件之后、程序化生成（SectorProcGen）与经济加载（economy.json）之前调用，
		// 因此这里生成的星系与实体能被后续步骤消费（市场数据路径、程序化生成）。
		new GearInSystemGenerator().generate(Global.getSector());

		// 关系播种的挂载点：faction 文件不定义势力间关系，只能由代码设置（见势力单元的关系小节）
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

/**
 * 本节示例：制作一个带恒星、行星、自定义实体与跳跃点的星系。
 * 实现 SectorGeneratorPlugin 只是采用生成器的"契约"写法（原版 data.scripts.world.SectorGen
 * 就是 SectorGeneratorPlugin）；实际调用入口是 ModPlugin.onNewGame，见 GearInSystemGenPlugin。
 * 市场绑定由 C3-S7 的 GearInMarketFactory 负责，本类只保留世界对象生成。
 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

	@Override
	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("GearIn");
		system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
		system.setLightColor(new Color(200, 200, 210));

		// 恒星 id "gearin" 同时是星系在 starmap.json 里的注册键：initStar 会用它查
		// data/campaign/starmap.json 的 starSystemLocations，把结果设为星系在超空间中的坐标。
		// 不提供该键（又不手动 setLocation）时，星系会定位在超空间原点 (0,0)。
		PlanetAPI star = system.initStar("gearin", "star_white", 600f, 550f, 3f, 0.75f, 1f);

		// addPlanet(id, 轨道焦点, 显示名, 类型id, 轨道角, 半径, 轨道半径, 轨道周期天数)
		PlanetAPI prime = system.addPlanet("GearIn_planet1", star, "GearIn Prime", "GearIn_ember_world", 55, 150, 3200, 120);
		PlanetAPI second = system.addPlanet("GearIn_planet2", star, "GearIn Second", "barren", 200, 90, 5200, 200);

		// 自定义实体：type 是 data/config/custom_entities.json 里的键，factionId 缺省为 neutral
		SectorEntityToken stable = system.addCustomEntity("GearIn_stable", "GearIn 导航锚", "GearIn_navigation_anchor", "gear_industry");
		stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

		// 手动跳跃点：创建后必须 addEntity 加入星系，autogenerate 阶段才会为它生成超空间侧入口
		JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
		jump.setCircularOrbit(star, 300, 1800, 100);
		jump.setRelatedPlanet(prime);
		system.addEntity(jump);

		// 必须调用（或用带超空间坐标的 initStar 变体），否则星系没有通向超空间的跳跃点，玩家无法到达
		system.autogenerateHyperspaceJumpPoints(true, true);

	}
}
```

:::

## 验证方案

1. 确认 `mod_info.json` 的 `modPlugin` 仍指向 `GearInSystemGenPlugin`，创建新档。
2. 在星图中定位 GearIn，进入星系并观察恒星、GearIn Prime、导航锚和跳跃点。
3. 从超空间退出并重新进入，确认入口和轨道关系仍然存在。

预期结果：

* 新档创建后出现 GearIn 星系；
* GearIn Prime 使用 [C3-S4] 的余烬世界类型；
* 导航锚使用 [C3-S5] 的自定义实体类型并属于 GearIn；
* 手动跳跃点进入实体集合并生成超空间侧入口。

## 故障分析

- **新星系位于超空间原点**：`starmap.json` 的 `starSystemLocations` 没有恒星 ID `gearin`，且生成器没有显式设置位置。补齐位置数据或生成调用后创建新档，星系应出现在设定坐标。
- **行星或实体没有出现**：类型 ID 已加载，但对象没有加入同一个 `system` 容器时，生成器不会自动补救。检查 `addPlanet` 和 `addCustomEntity` 的容器参数，重新创建新档后，目标对象应出现在星图和星系内。
- **新档没有任何变化**：`onNewGame()` 只在新档生成阶段调用，旧存档不会重新执行。删除测试存档，确认 `modPlugin` 已启用并重新创建新档；日志应出现生成入口，星系应可定位。

## 总结

星系生成器只负责容器、顺序和位置；行星与实体的外观由数据页定义。把这两层分开后，新增一个世界对象通常只需要替换一个类型 ID 和一个轨道参数。

## 建议参考项

* `SectorGeneratorPlugin`：星系生成器契约；
* `StarSystemAPI`：恒星系对象与实体容器；
* [C3-S4]：行星/恒星类型；
* [C3-S5]：自定义实体类型；
* `starmap.json`：超空间位置注册。
