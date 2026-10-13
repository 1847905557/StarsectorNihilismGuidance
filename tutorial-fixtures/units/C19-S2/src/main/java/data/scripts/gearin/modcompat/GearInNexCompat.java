package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.world.ExerelinCorvusLocations;

/** 查询 Nexerelin 启用状态、星区模式与势力配置。 */
public class GearInNexCompat {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String GEAR_FACTION_ID = "gear_industry";

    private GearInNexCompat() {
    }

    public static boolean isNexEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    public static boolean isNexCorvusMode() {
        if (!isNexEnabled()) {
            return false;
        }
        SectorManager manager = SectorManager.getManager();
        return manager != null && manager.isCorvusMode();
    }

    public static float getFactionMarketSpawnWeight(String factionId) {
        if (!isNexEnabled()) {
            return 1f;
        }
        return NexConfig.getFactionConfig(factionId).marketSpawnWeight;
    }

    public static String getFactionSpawnPointEntityId(String factionId) {
        if (!isNexEnabled()) {
            return null;
        }
        ExerelinCorvusLocations.SpawnPointEntry entry =
                ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
        return entry == null ? null : entry.entityId;
    }
}
