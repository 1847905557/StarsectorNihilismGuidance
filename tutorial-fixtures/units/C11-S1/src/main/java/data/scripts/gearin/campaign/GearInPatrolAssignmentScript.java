package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/** 巡逻舰队的任务安排：出港后巡逻，随后返回源点并解散。 */
public class GearInPatrolAssignmentScript implements EveryFrameScript {

    private static final float PATROL_DAYS = 8f;

    private final CampaignFleetAPI fleet;
    private final SectorEntityToken source;

    public GearInPatrolAssignmentScript(CampaignFleetAPI fleet, SectorEntityToken source) {
        this.fleet = fleet;
        this.source = source;
        assign();
    }

    private void assign() {
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, source, 2f, "preparing for patrol");
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, null, PATROL_DAYS, "patrolling");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 2f, "standing down");
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 5f);
    }

    @Override
    public boolean isDone() {
        return !fleet.isAlive();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (fleet.getCurrentAssignment() == null) {
            assign();
        }
    }
}
