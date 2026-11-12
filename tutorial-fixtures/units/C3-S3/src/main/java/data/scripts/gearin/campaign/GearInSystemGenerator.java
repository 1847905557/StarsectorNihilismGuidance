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
        // 星系 ID、背景图与星系内的光照颜色
        StarSystemAPI system = sector.createStarSystem("Nipher");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.setLightColor(new Color(200, 200, 210));

        // 恒星：ID、类型、半径、日冕大小、恒星风强度、耀斑概率、战备值损失倍率
        PlanetAPI star = system.initStar("nipher", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
        // 星系在超空间中的坐标
        system.getLocation().set(-16000f, -9000f);

        // 行星：ID、轨道焦点、名称、类型、轨道角、半径、轨道半径、轨道周期 (天)
        PlanetAPI prime = system.addPlanet(
                "GearIn_planet1", star, "Nipher Prime", "terran", 55, 150, 3200, 120);
        PlanetAPI second = system.addPlanet(
                "GearIn_planet2", star, "Nipher Second", "barren", 200, 90, 5200, 200);

        // 原版通讯中继：ID、名称、实体类型、所属势力
        SectorEntityToken stable = system.addCustomEntity(
                "GearIn_stable", "Nipher 通讯中继", "comm_relay", "gear_industry");
        stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

        // 市场：ID 与名称沿用行星，规模 4
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

        // 星系内的跳跃点，关联主行星
        JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "Nipher 跳跃点");
        jump.setCircularOrbit(star, 300, 1800, 100);
        jump.setRelatedPlanet(prime);
        system.addEntity(jump);
        // 生成超空间一侧的入口：气态巨行星入口、星系边缘跳跃点
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}
