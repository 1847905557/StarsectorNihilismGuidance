package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInEntityPlacement implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInEntityPlacement.class);
    private static final boolean USE_FIXED_LOCATION = false;

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

        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system == null) {
            LOG.warn("GearIn entity placement: system missing");
            done = true;
            return;
        }

        SectorEntityToken entity = system.getEntityById(
                "GearIn_entity_beacon");
        if (entity == null) {
            entity = system.addCustomEntity(
                    "GearIn_entity_beacon",
                    "观测信标",
                    "comm_relay",
                    Factions.INDEPENDENT);
        }

        entity.setName("观测信标");
        entity.setFaction(Factions.INDEPENDENT);
        entity.addTag("GearIn_entity_probe");
        entity.setFacing(90f);
        entity.getCustomData().put("GearIn_entity_role", "sensor");
        entity.getMemoryWithoutUpdate().set(
                "$GearIn_entity_ready",
                true);
        entity.setInteractionImage(
                "illustrations",
                "GearIn_entity_interaction");
        if (USE_FIXED_LOCATION) {
            entity.setFixedLocation(1600f, -900f);
        } else {
            entity.setCircularOrbit(
                    system.getStar(),
                    45f,
                    2600f,
                    160f);
        }
        entity.setSensorStrength(1f);
        entity.setSensorProfile(1f);

        CustomCampaignEntityAPI customEntity =
                (CustomCampaignEntityAPI) entity;
        SectorEntityToken globalEntity = sector.getEntityById(
                "GearIn_entity_beacon");
        String locationId = entity.getContainingLocation() == null
                ? "null"
                : entity.getContainingLocation().getId();

        LOG.info(String.format(
                "GearIn entity: id=%s location=%s globalSame=%s tag=%s ready=%s plugin=%s strength=%.2f profile=%.2f visible=%s",
                entity.getId(),
                locationId,
                entity == globalEntity,
                entity.hasTag("GearIn_entity_probe"),
                entity.getMemoryWithoutUpdate().getBoolean(
                        "$GearIn_entity_ready"),
                customEntity.getCustomPlugin() != null,
                entity.getSensorStrength(),
                entity.getSensorProfile(),
                entity.isVisibleToPlayerFleet()));
        done = true;
    }
}
