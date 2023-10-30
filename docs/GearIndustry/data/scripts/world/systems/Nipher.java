package data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.impl.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.*;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

import data.scripts.util.MagicCampaign;

public class Nipher {

	public void generate(SectorAPI sector) {
        
        StarSystemAPI system = sector.createStarSystem("Nipher");
		system.getLocation().set(2600, -5200);
		system.setBackgroundTextureFilename("graphics/backgrounds/background3.jpg");
		system.setLightColor(new Color(200, 200, 210));

        PlanetAPI star = system.initStar("Nipher", "star_white", 600f, 550f, 3f, 0.75f, 1f);
		star.setCustomDescriptionId("GearIn_Nipher");


		// 增加一个星球，id确保独特，命名为Ubi，类型为arid，初始角度0，星球大小160，距离恒星2000距离，环绕周期140天
		PlanetAPI GearIn_planet1 = system.addPlanet("GearIn_planet1", star, "Ubi", "arid", 60, 160, 2000, 140);
		GearIn_planet1.setCustomDescriptionId("GearIn_planet1");

		// 再增加一个星球，id确保独特，命名为Cablim，类型为barren，初始角度160，星球大小85，距离恒星2200距离，环绕周期140天
		PlanetAPI GearIn_planet2 = system.addPlanet("GearIn_planet2", star, "Cablim", "barren", 160, 85, 2200, 140);
		GearIn_planet2.setCustomDescriptionId("GearIn_planet2");


		// 增加一个稳定点
		SectorEntityToken stable_1 = system.addCustomEntity(null, null, "stable_location", "neutral");
		// 初始角度0，距离恒星2750距离，环绕周期160天
		stable_1.setCircularOrbitPointingDown(star, 0, 2750, 160);

		// 增加一条尘埃带，宽度300，距离恒星3300距离，环绕周期220天
		system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 300f, 3300, 220f);
		// 增加一条小陨石带，宽度200，距离恒星3300距离，环绕周期220天
		system.addRingBand(star, "misc", "rings_asteroids0", 256f, 0, Color.white, 200f, 3300, 220f);
		// 增加一条真陨石带，80个陨石，距离恒星3800距离，宽度64，环绕周期245~280天
		system.addAsteroidBelt(star, 80, 3800, 64, 245, 280);



		// 增加一个通讯站，id确保独特，命名为Nipher 通讯站，初始角度240，距离恒星4600距离，环绕周期280天，所属gear_industry
		SectorEntityToken relay = system.addCustomEntity("GearIn_relay", "Nipher 通讯站", "comm_relay", "gear_industry");
		relay.setCircularOrbitPointingDown(star, 240, 4600, 280);

		// 增加一个传感站，id确保独特，命名为Nipher 传感站，初始角度0，距离恒星4800距离，环绕周期280天，所属gear_industry
		SectorEntityToken sensor = system.addCustomEntity("GearIn_sensor", "Nipher 传感站", "sensor_array", "gear_industry");
		sensor.setCircularOrbitPointingDown(star, 60, 4800, 280);

		// 增加一个导航站，id确保独特，命名为Nipher 导航站，初始角度120，距离恒星4800距离，环绕周期280天，所属gear_industry
		SectorEntityToken nav = system.addCustomEntity("GearIn_nav", "Nipher 导航站", "nav_buoy", "gear_industry");
		nav.setCircularOrbitPointingDown(star, 120, 4800, 280);


		// 再增加一个跳跃点，id确保独特，命名为Nipher 跳跃点，初始角度30，距离恒星1200距离，环绕周期120天
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("GearIn_jump_point", "Nipher 跳跃点");
		jumpPoint.setOrbit(Global.getFactory().createCircularOrbit(star, 30, 1200, 120));
		jumpPoint.setRelatedPlanet(GearIn_planet1); // 位于GearIn_planet1附近
		jumpPoint.setStandardWormholeToHyperspaceVisual();



		MarketAPI market_1 = addMarket(GearIn_planet1, "gear_industry", 0.35f, // 星球和势力和税率
				// 在 data/campaign/conditions里找各类星球特质。其中，population_x是必要的
				new ArrayList(Arrays.asList("population_4", "thin_atmosphere", "hot", "ore_rich", "rare_ore_abundant")),
				new ArrayList(Arrays.asList("population", "spaceport", "mining", "refining", "heavybatteries", "orbitalworks", "battlestation")),
				new ArrayList(Arrays.asList("open_market", "generic_military", "black_market", "storage")));

		MarketAPI market_2 = addMarket(GearIn_planet2, "gear_industry", 0.35f, // 星球和势力和税率
				// 在 data/campaign/conditions里找各类星球特质。其中，population_x是必要的
				new ArrayList(Arrays.asList("population_4", "no_atmosphere", "meteor_impacts", "ore_abundant")),
				new ArrayList(Arrays.asList("population", "spaceport", "heavyindustry", "militarybase", "grounddefenses", "battlestation")),
				new ArrayList(Arrays.asList("open_market", "generic_military", "black_market", "storage")));



		system.autogenerateHyperspaceJumpPoints(true, true);
	}

	private MarketAPI addMarket(SectorEntityToken entity, String faction, float tarrif, List<String> conditions, List<String> industries, List<String> submarkets) {
		int size = 0;
		for (String condition : conditions) {
			if (condition.startsWith("population_")) {
				String sub = condition.replace("population_", "");
				size = Integer.parseInt(sub);
			}
		}

		MarketAPI market = MagicCampaign.addSimpleMarket(entity, entity.getId(), entity.getName(),
				size, faction, false, false,
				conditions, industries, false, false, false, false, false, false);

		if (conditions.contains("free_market")) market.setFreePort(true);
		for (String submarket : submarkets) {
			market.addSubmarket(submarket);
		}

		market.getTariff().modifyFlat("generator", tarrif);
		Global.getSector().getEconomy().addMarket(market, true);

        entity.setMarket(market);
		entity.setFaction(faction);
		return market;
	}
}