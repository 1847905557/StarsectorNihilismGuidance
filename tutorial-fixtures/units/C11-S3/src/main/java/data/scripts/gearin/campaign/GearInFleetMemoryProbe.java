package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class GearInFleetMemoryProbe implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemoryProbe";
    private static final Logger LOG = Global.getLogger(GearInFleetMemoryProbe.class);

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
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();

        boolean hadBusy = memory.contains(MemFlags.FLEET_BUSY);
        Object oldBusy = memory.get(MemFlags.FLEET_BUSY);
        float busyExpire = memory.getExpire(MemFlags.FLEET_BUSY);
        boolean hadPatrolDistance = memory.contains(MemFlags.FLEET_PATROL_DISTANCE);
        Object oldPatrolDistance = memory.get(MemFlags.FLEET_PATROL_DISTANCE);
        float patrolExpire = memory.getExpire(MemFlags.FLEET_PATROL_DISTANCE);

        try {
            memory.set(MemFlags.FLEET_BUSY, true, 0.25f);
            memory.set(MemFlags.FLEET_PATROL_DISTANCE, 5f, 0.25f);
            LOG.info("GearIn fleet memory: busy=" + memory.getBoolean(MemFlags.FLEET_BUSY)
                    + " patrolDistance=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE));
        } finally {
            if (hadBusy) {
                memory.set(MemFlags.FLEET_BUSY, oldBusy, busyExpire);
            } else {
                memory.unset(MemFlags.FLEET_BUSY);
            }
            if (hadPatrolDistance) {
                memory.set(MemFlags.FLEET_PATROL_DISTANCE, oldPatrolDistance, patrolExpire);
            } else {
                memory.unset(MemFlags.FLEET_PATROL_DISTANCE);
            }
            LOG.info("GearIn fleet memory restored: busyPresent="
                    + memory.contains(MemFlags.FLEET_BUSY)
                    + " busy=" + memory.get(MemFlags.FLEET_BUSY)
                    + " busyExpire=" + memory.getExpire(MemFlags.FLEET_BUSY)
                    + " patrolPresent=" + memory.contains(MemFlags.FLEET_PATROL_DISTANCE)
                    + " patrol=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE)
                    + " patrolExpire=" + memory.getExpire(MemFlags.FLEET_PATROL_DISTANCE));
        }
    }
}
