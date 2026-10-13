package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/** 生成一支常规巡逻舰队并投放到来源市场附近。 */
public class GearInFleetGenerator implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFleetGenerator.class);

    private boolean done;
    private CampaignFleetAPI generatedFleet;

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
        done = true;

        MarketAPI source = pickSourceMarket();
        if (source == null) {
            LOG.warn("GearIn fleet generator: no source market");
            return;
        }

        LocationAPI location = source.getContainingLocation();
        SectorEntityToken anchor = source.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.warn("GearIn fleet generator: source has no location or anchor");
            return;
        }

        FleetParamsV3 params = new FleetParamsV3(
                source,
                FleetTypes.PATROL_MEDIUM,
                40f,
                12f,
                6f,
                0f,
                0f,
                0f,
                0f);

        generatedFleet = FleetFactoryV3.createFleet(params);
        if (generatedFleet == null || generatedFleet.isEmpty()) {
            LOG.warn("GearIn fleet generator: factory returned an empty fleet");
            return;
        }

        generatedFleet.setTransponderOn(true);
        location.spawnFleet(
                anchor,
                300f,
                0f,
                generatedFleet);

        LOG.info("GearIn fleet generator: market=" + source.getId()
                + " name=" + generatedFleet.getName()
                + " members=" + generatedFleet.getFleetData().getNumMembers()
                + " fleetPoints=" + generatedFleet.getFleetPoints()
                + " location=" + location.getId()
                + " alive=" + generatedFleet.isAlive()
                + " transponder=" + generatedFleet.isTransponderOn());
    }

    private MarketAPI pickSourceMarket() {
        MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
        if (jangala != null) {
            return jangala;
        }

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasSubmarket(Submarkets.SUBMARKET_OPEN)
                    && market.getContainingLocation() != null
                    && market.getPrimaryEntity() != null) {
                return market;
            }
        }
        return null;
    }
}
