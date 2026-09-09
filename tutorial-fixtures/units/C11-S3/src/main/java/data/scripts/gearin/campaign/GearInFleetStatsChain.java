package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;

public class GearInFleetStatsChain implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetStatsChain";
    private static final Logger LOG = Global.getLogger(GearInFleetStatsChain.class);

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
        done = true;

        SectorAPI sector = Global.getSector();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                "wolf_Assault");

        if (member == null) {
            LOG.warn("GearIn fleet stats: member creation failed");
            return;
        }

        data.addFleetMember(member);

        ShipVariantAPI original = member.getVariant();
        MutableShipStatsAPI before = member.getStats();
        MutableFleetStatsAPI fleetStats = fleet.getStats();
        float speedBefore = before.getMaxSpeed().getModifiedValue();
        float burnBefore = data.getBurnLevel();
        PersonAPI captain = member.getCaptain();
        PersonAPI commander = fleet.getCommander();

        ShipVariantAPI refit = original.clone();
        refit.setNumFluxCapacitors(
                original.getNumFluxCapacitors() + 1);
        member.setVariant(refit, false, true);
        member.updateStats();

        MutableShipStatsAPI after = member.getStats();
        LOG.info("GearIn fleet stats: member=" + member.getId()
                + " captain=" + (captain == null
                        ? "none"
                        : captain.getNameString())
                + " commander=" + (commander == null
                        ? "none"
                        : commander.getNameString())
                + " speedBefore=" + speedBefore
                + " speedAfter=" + after.getMaxSpeed().getModifiedValue()
                + " burnBefore=" + burnBefore
                + " burnAfter=" + data.getBurnLevel()
                + " statsRebuilt=" + (before != after)
                + " sensorRange="
                + fleetStats.getSensorRangeMod().computeEffective(
                        fleet.getSensorStrength())
                + " fleetPoints=" + fleet.getFleetPoints());

        member.setVariant(original, false, true);
        member.updateStats();
        data.removeFleetMember(member);
        data.syncIfNeeded();
    }
}
