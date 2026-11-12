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

/** 创建齿轮工业星系，使用自定义行星、导航锚与独立描述。 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("Nipher");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.setLightColor(new Color(200, 200, 210));

        PlanetAPI star = system.initStar("nipher", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
        system.getLocation().set(-16000f, -9000f);

        PlanetAPI prime = system.addPlanet(
                "GearIn_planet1", star, "Nipher Prime", "GearIn_ember_world", 55, 150, 3200, 120);
        PlanetAPI second = system.addPlanet(
                "GearIn_planet2", star, "Nipher Second", "barren", 200, 90, 5200, 200);
        second.setCustomDescriptionId("GearIn_description_second");

        SectorEntityToken stable = system.addCustomEntity(
                "GearIn_stable", "Nipher 导航锚", "GearIn_navigation_anchor", "gear_industry");
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

        JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "Nipher 跳跃点");
        jump.setCircularOrbit(star, 300, 1800, 100);
        jump.setRelatedPlanet(prime);
        system.addEntity(jump);
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}
