package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * 在 30 个游戏日内记录带指定 Memory 标记的舰队生命周期事件。
 */
public class GearInFleetLifecycleListener extends BaseCampaignEventListener {

    public static final String PROBE_MEMORY_KEY = "$GearIn_listener_probe";
    public static final String REGISTERED_KEY = "GearIn_fleet_lifecycle_registered";
    public static final float ACTIVE_DAYS = 30.0f;

    private static final Logger LOG = Global.getLogger(GearInFleetLifecycleListener.class);

    private int spawnedCount = 0;
    private int reachedCount = 0;
    private int jumpedCount = 0;
    private int despawnedCount = 0;

    public GearInFleetLifecycleListener() {
        super(false);
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        spawnedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=spawned fleet=%s count=%d",
                fleet.getId(), spawnedCount));
    }

    @Override
    public void reportFleetReachedEntity(CampaignFleetAPI fleet, SectorEntityToken entity) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        reachedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=reached fleet=%s count=%d target=%s",
                fleet.getId(), reachedCount, entityLabel(entity)));
    }

    @Override
    public void reportFleetJumped(
            CampaignFleetAPI fleet,
            SectorEntityToken from,
            JumpDestination to) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        jumpedCount++;
        SectorEntityToken destination = to == null ? null : to.getDestination();
        LOG.info(String.format(
                "GearIn fleet listener: event=jumped fleet=%s count=%d from=%s to=%s",
                fleet.getId(), jumpedCount, entityLabel(from), entityLabel(destination)));
    }

    @Override
    public void reportFleetDespawned(
            CampaignFleetAPI fleet,
            FleetDespawnReason reason,
            Object param) {
        if (!isProbeFleet(fleet)) {
            return;
        }
        despawnedCount++;
        LOG.info(String.format(
                "GearIn fleet listener: event=despawned fleet=%s count=%d reason=%s %s",
                fleet.getId(), despawnedCount, String.valueOf(reason),
                despawnContext(reason, param)));
    }

    private static boolean isProbeFleet(CampaignFleetAPI fleet) {
        return fleet.getMemoryWithoutUpdate().getBoolean(PROBE_MEMORY_KEY);
    }

    private static String despawnContext(FleetDespawnReason reason, Object param) {
        if (reason == FleetDespawnReason.REACHED_DESTINATION
                && param instanceof SectorEntityToken destination) {
            return "destination=" + entityLabel(destination);
        }
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE
                && param instanceof BattleAPI battle) {
            return "battlePlayerInvolved=" + battle.isPlayerInvolved();
        }
        return param == null
                ? "param=null"
                : "paramType=" + param.getClass().getName();
    }

    private static String entityLabel(SectorEntityToken entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getId() + "(" + entity.getName() + ")";
    }
}
