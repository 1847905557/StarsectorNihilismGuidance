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

        boolean oldBusy = memory.getBoolean(MemFlags.FLEET_BUSY);
        Object oldPatrolDistance = memory.get(MemFlags.FLEET_PATROL_DISTANCE);

        memory.set(MemFlags.FLEET_BUSY, true, 0.25f);
        memory.set(MemFlags.FLEET_PATROL_DISTANCE, 5f, 0.25f);

        LOG.info("GearIn fleet memory: busy=" + memory.getBoolean(MemFlags.FLEET_BUSY)
                + " patrolDistance=" + memory.get(MemFlags.FLEET_PATROL_DISTANCE));

        if (oldBusy) {
            memory.set(MemFlags.FLEET_BUSY, true);
        } else {
            memory.unset(MemFlags.FLEET_BUSY);
        }
        if (oldPatrolDistance == null) {
            memory.unset(MemFlags.FLEET_PATROL_DISTANCE);
        } else {
            memory.set(MemFlags.FLEET_PATROL_DISTANCE, oldPatrolDistance);
        }
    }
}
