package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 在 Nipher 星系中建立 Foundry 空间站及其市场，并让一名港务官出现在通信目录。 */
public class GearInFoundryGenerator {

    public static final String STATION_ID = "GearIn_foundry";
    public static final String MARKET_ID = "GearIn_market_foundry";

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.getStarSystem("Nipher");
        if (system == null || sector.getEconomy().getMarket(MARKET_ID) != null) {
            return;
        }

        // 空间站是自定义实体，不是行星；轨道焦点为 Nipher 的恒星
        SectorEntityToken station = system.addCustomEntity(
                STATION_ID, "Nipher Foundry", "station_side00", GearInModPlugin.FACTION_ID);
        station.setCircularOrbitPointingDown(system.getStar(), 200f, 4200f, 150f);

        // 市场：创建、设置势力与主实体、加入经济，三步缺一不可
        MarketAPI market = Global.getFactory().createMarket(MARKET_ID, "Nipher Foundry", 3);
        market.setFactionId(GearInModPlugin.FACTION_ID);
        market.setPrimaryEntity(station);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_3");
        market.addCondition("GearIn_warfront");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addIndustry("GearIn_element_works");
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.addSubmarket("storage");
        market.addSubmarket("GearIn_alloy_depot");
        sector.getEconomy().addMarket(market, true);
        station.setMarket(market);

        // 通信目录：先加入市场人物，再登记目录条目
        PersonAPI officer = Global.getFactory().createPerson();
        officer.setFaction(GearInModPlugin.FACTION_ID);
        officer.setName(new FullName("Doran", "Pike", FullName.Gender.MALE));
        officer.setRankId(Ranks.SPACE_COMMANDER);
        officer.setPostId(Ranks.POST_OFFICER);
        market.addPerson(officer);
        market.getCommDirectory().addPerson(officer);
    }
}
