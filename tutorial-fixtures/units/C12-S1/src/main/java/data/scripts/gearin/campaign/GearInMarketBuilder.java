package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class GearInMarketBuilder implements EveryFrameScript {
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system == null) {
            done = true;
            return;
        }
        PlanetAPI planet = (PlanetAPI) system.getEntityById("GearIn_foundry");
        if (planet != null) {
            done = true;
            return;
        }
        planet = system.addPlanet("GearIn_foundry", system.getStar(),
                "Nipher Foundry", "barren", 135f, 110f, 3600f, 180f);
        MarketAPI market = Global.getFactory().createMarket(
                "GearIn_market_foundry", "Nipher Foundry", 3);
        market.setFactionId("gear_industry");
        market.setPrimaryEntity(planet);
        market.addCondition("trade_center");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.addSubmarket("storage");
        EconomyAPI economy = sector.getEconomy();
        economy.addMarket(market, true);
        planet.setMarket(market);
        planet.setFaction("gear_industry");
        done = true;
    }
}
