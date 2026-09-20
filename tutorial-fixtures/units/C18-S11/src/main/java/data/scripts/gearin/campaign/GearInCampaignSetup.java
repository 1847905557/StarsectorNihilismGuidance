package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import org.apache.log4j.Logger;
import org.magiclib.util.MagicCampaign;

public final class GearInCampaignSetup {

    private static final Logger LOGGER = Global.getLogger(GearInCampaignSetup.class);

    private static final String SYSTEM_ID = "GearIn";
    private static final String ORBIT_CENTER_ID = "GearIn_planet1";
    private static final String VARIANT_ID = "GearIn_artizan_combat";
    private static final String SETUP_DONE_KEY = "$GearIn_campaign_setup_done";

    private static final float ORBIT_START_ANGLE = 90f;
    private static final float ORBIT_ALTITUDE = 50f;
    private static final float ORBIT_DAYS = 100f;

    public void setup(SectorAPI sector) {
        MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();
        if (sectorMemory.getBoolean(SETUP_DONE_KEY)) {
            return;
        }

        StarSystemAPI system = sector.getStarSystem(SYSTEM_ID);
        if (system == null) {
            LOGGER.error("GearIn campaign setup skipped: missing star system " + SYSTEM_ID);
            return;
        }

        SectorEntityToken orbitCenter = sector.getEntityById(ORBIT_CENTER_ID);
        if (orbitCenter == null || orbitCenter.getStarSystem() != system) {
            LOGGER.error("GearIn campaign setup skipped: missing orbit center " + ORBIT_CENTER_ID);
            return;
        }

        if (!Global.getSettings().doesVariantExist(VARIANT_ID)) {
            LOGGER.error("GearIn campaign setup skipped: missing ship variant " + VARIANT_ID);
            return;
        }

        SectorEntityToken derelict = null;
        try {
            derelict = MagicCampaign.createDerelict(
                    VARIANT_ID,
                    ShipCondition.BATTERED,
                    true,
                    null,
                    true,
                    orbitCenter,
                    ORBIT_START_ANGLE,
                    orbitCenter.getRadius() + ORBIT_ALTITUDE,
                    ORBIT_DAYS,
                    true);
            MagicCampaign.hyperspaceCleanup(system);
            sectorMemory.set(SETUP_DONE_KEY, true);
        } catch (RuntimeException | LinkageError exception) {
            if (derelict != null) {
                system.removeEntity(derelict);
            }
            LOGGER.error("GearIn campaign setup failed while calling MagicLib", exception);
        }
    }
}
