package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class GearInSectorStateSampler implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_SectorStateSampler";

    private static final String PERSISTENT_SAMPLE_KEY = "GearIn_sector_sampled";
    private static final String MEMORY_PROBE_KEY = "$GearIn_sector_probe";
    private static final float SAMPLE_INTERVAL_DAYS = 5f;
    private static final Logger LOG = Global.getLogger(GearInSectorStateSampler.class);

    private long lastSampleTimestamp;
    private boolean initialized;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getClock() == null) {
            return;
        }

        CampaignClockAPI clock = sector.getClock();
        if (!initialized) {
            lastSampleTimestamp = clock.getTimestamp();
            initialized = true;
        }

        float daysSinceSample = clock.getElapsedDaysSince(lastSampleTimestamp);
        if (daysSinceSample < SAMPLE_INTERVAL_DAYS) {
            return;
        }

        writeStateLog(sector, clock);
        lastSampleTimestamp = clock.getTimestamp();
        sector.getPersistentData().put(PERSISTENT_SAMPLE_KEY, true);
    }

    private void writeStateLog(SectorAPI sector, CampaignClockAPI clock) {
        LocationAPI currentLocation = sector.getCurrentLocation();
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        FactionAPI playerFaction = sector.getPlayerFaction();
        Map<String, Object> persistentData = sector.getPersistentData();
        MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();
        Object probeValue = sectorMemory.get(MEMORY_PROBE_KEY);

        String locationId = currentLocation == null
                ? "null"
                : currentLocation.getId();
        String fleetId = playerFleet == null
                ? "null"
                : playerFleet.getId();
        String factionId = playerFaction == null
                ? "null"
                : playerFaction.getId();

        LOG.info(String.format(
                "GearIn sector sample: date=%s timestamp=%d paused=%s locations=%d location=%s fleet=%s faction=%s persistent=%s probe=%s",
                clock.getDateString(),
                clock.getTimestamp(),
                sector.isPaused(),
                sector.getAllLocations().size(),
                locationId,
                fleetId,
                factionId,
                Boolean.TRUE.equals(persistentData.get(PERSISTENT_SAMPLE_KEY)),
                probeValue));
    }
}
