package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.world.ExerelinCorvusLocations;

/** 在生涯已生成后查询出生实体与起始装配列表。 */
public final class GearInNexStartPlanner {
    public String chooseSpawn(String factionId) {
        SectorManager manager = SectorManager.getManager();
        if (manager == null) {
            return null;
        }
        if (manager.isCorvusMode()) {
            ExerelinCorvusLocations.SpawnPointEntry entry =
                    ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
            return entry == null ? null : entry.entityId;
        }

        MarketAPI largest = null;
        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(factionId)) {
            if (market.getPrimaryEntity() == null) {
                continue;
            }
            if (largest == null || market.getSize() > largest.getSize()) {
                largest = market;
            }
        }
        return largest == null ? null : largest.getPrimaryEntity().getId();
    }

    public List<String> chooseFleet(String factionId, String fleetType) {
        return NexConfig.getFactionConfig(factionId)
                .getStartFleetForType(fleetType, false, 0);
    }
}
