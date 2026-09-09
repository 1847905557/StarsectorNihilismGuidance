package data.scripts.gearin.campaign;

import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

/**
 * 生成、进行装配随机化并投放一支使用高级参数的巡逻舰队。
 *
 * <p>本例固定随机源，记录旗舰、指挥官、装配来源、D 插和生成规模。</p>
 */
public class GearInFleetGeneratorAdvanced implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFleetGeneratorAdvanced.class);

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
            LOG.warn("GearIn advanced fleet: no source market");
            return;
        }

        LocationAPI location = source.getContainingLocation();
        SectorEntityToken anchor = source.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.warn("GearIn advanced fleet: source has no location or anchor");
            return;
        }

        FleetParamsV3 params = new FleetParamsV3(
                source,
                FleetTypes.PATROL_LARGE,
                80f,
                20f,
                12f,
                0f,
                0f,
                0f,
                0f);
        params.qualityOverride = 0.8f;
        params.averageSMods = 1;
        params.flagshipVariantId = "tempest_Attack";
        params.officerLevelBonus = 1;
        params.maxNumShips = 18;
        params.random = new Random(104729L);
        params.withOfficers = true;

        generatedFleet = FleetFactoryV3.createFleet(params);
        if (generatedFleet == null || generatedFleet.isEmpty()) {
            LOG.warn("GearIn advanced fleet: factory returned an empty fleet");
            return;
        }

        boolean inflatedBefore = generatedFleet.isInflated();
        boolean inflaterAttachedBefore = generatedFleet.getInflater() != null;
        generatedFleet.inflateIfNeeded();

        List<FleetMemberAPI> members = generatedFleet.getFleetData().getMembersListCopy();
        int refitCount = 0;
        int dModCount = 0;
        for (FleetMemberAPI member : members) {
            if (member.getVariant().getSource() == VariantSource.REFIT) {
                refitCount++;
            }
            dModCount += DModManager.getNumDMods(member.getVariant());
        }

        float spawnFPMult = Misc.getSpawnFPMult(generatedFleet);
        String commanderName = generatedFleet.getCommander() == null
                ? "none"
                : generatedFleet.getCommander().getNameString();

        generatedFleet.setTransponderOn(true);
        location.spawnFleet(
                anchor,
                -300f,
                0f,
                generatedFleet);

        LOG.info("GearIn advanced fleet: market=" + source.getId()
                + " requestedPoints=" + params.getTotalPts()
                + " actualPoints=" + generatedFleet.getFleetPoints()
                + " spawnFPMult=" + spawnFPMult
                + " inflatedBefore=" + inflatedBefore
                + " inflatedAfter=" + generatedFleet.isInflated()
                + " inflaterBefore=" + inflaterAttachedBefore
                + " inflaterAfter=" + (generatedFleet.getInflater() != null)
                + " members=" + members.size()
                + " refitMembers=" + refitCount
                + " dMods=" + dModCount
                + " commander=" + commanderName
                + " flagship=" + (generatedFleet.getFlagship() == null
                        ? "none"
                        : generatedFleet.getFlagship().getHullId())
                + " location=" + location.getId()
                + " alive=" + generatedFleet.isAlive());
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
