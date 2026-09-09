package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;

/**
 * 修改并记录一艘生涯成员的状态，随后保留成员供舰队界面观察。
 */
public class GearInFleetMemberStatus implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberStatus";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberStatus.class);

    private static final String VARIANT_ID = "hammerhead_Elite";

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

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = GearInFleetMemberBuilder.createMember(
                fleet,
                VARIANT_ID,
                false);

        FleetMemberStatusAPI status = member.getStatus();
        CrewCompositionAPI crew = member.getCrewComposition();
        RepairTrackerAPI tracker = member.getRepairTracker();

        float hullBefore = status.getHullFraction();
        float crBefore = tracker.getCR();
        float crewBefore = crew.getCrew();

        status.applyHullFractionDamage(0.2f);
        crew.setCrew(Math.max(1f, member.getMinCrew() * 0.5f));
        tracker.setCR(0.3f);
        tracker.setMothballed(true);

        float hullAfter = status.getHullFraction();
        float crAfter = tracker.getCR();
        float crewAfter = crew.getCrew();
        boolean deployableWhileMothballed = member.canBeDeployedForCombat();

        tracker.setMothballed(false);

        LOG.info("GearIn member status: id=" + member.getId()
                + " hull=" + hullBefore + "->" + hullAfter
                + " crew=" + crewBefore + "->" + crewAfter
                + " crewFraction=" + member.getCrewFraction()
                + " cr=" + crBefore + "->" + crAfter
                + " mothballed=false"
                + " deployableWhileMothballed=" + deployableWhileMothballed
                + " deployable=" + member.canBeDeployedForCombat()
                + " needsRepairs=" + member.needsRepairs()
                + " fleetMembers=" + data.getNumMembers());
    }
}
