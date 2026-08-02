package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 本页示例：制作一个带恒星、行星、稳定点、跳跃点与市场的星系。
 * 实现 SectorGeneratorPlugin 只是采用生成器的"契约"写法（原版 data.scripts.world.SectorGen
 * 就是 SectorGeneratorPlugin）；实际调用入口是 ModPlugin.onNewGame，见 GearInSystemGenPlugin。
 * 市场部分委托给 GearInMarketFactory（代码路径建市场，见本页"链路三"）。
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
		PlanetAPI prime = system.addPlanet("GearIn_planet1", star, "GearIn Prime", "terran", 55, 150, 3200, 120);
		PlanetAPI second = system.addPlanet("GearIn_planet2", star, "GearIn Second", "barren", 200, 90, 5200, 200);

		// 自定义实体：type 是 data/config/custom_entities.json 里的键，factionId 缺省为 neutral
		SectorEntityToken stable = system.addCustomEntity("GearIn_stable", "GearIn 稳定点", "stable_location", "neutral");
		stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

		// 手动跳跃点：创建后必须 addEntity 加入星系，autogenerate 阶段才会为它生成超空间侧入口
		JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
		jump.setCircularOrbit(star, 300, 1800, 100);
		jump.setRelatedPlanet(prime);
		system.addEntity(jump);

		// 必须调用（或用带超空间坐标的 initStar 变体），否则星系没有通向超空间的跳跃点，玩家无法到达
		system.autogenerateHyperspaceJumpPoints(true, true);

		// 代码路径建市场：委托给 GearInMarketFactory，条件/产业/子市场 ID 全部来自原版 CSV
		// （市场数据路径见本页"链路三"）
		GearInMarketFactory marketFactory = new GearInMarketFactory();
		marketFactory.createMarket(prime, "gear_industry", 0.35f,
				List.of("population_4", "thin_atmosphere", "hot", "ore_rich", "rare_ore_abundant"),
				List.of("population", "spaceport", "mining", "refining", "heavybatteries", "orbitalworks"),
				List.of("open_market", "generic_military", "black_market", "storage"));
		marketFactory.createMarket(second, "gear_industry", 0.35f,
				List.of("population_4", "no_atmosphere", "meteor_impacts", "ore_rich", "rare_ore_abundant"),
				List.of("population", "spaceport", "heavyindustry", "militarybase", "grounddefenses"),
				List.of("open_market", "generic_military", "black_market", "storage"));
	}
}
