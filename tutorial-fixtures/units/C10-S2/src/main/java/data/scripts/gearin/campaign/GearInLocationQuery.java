package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class GearInLocationQuery implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInLocationQuery.class);

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
        LocationAPI location = sector.getStarSystem("GearIn_observation_system");
        if (!(location instanceof StarSystemAPI)) {
            LOG.warn("GearIn location query: system missing");
            done = true;
            return;
        }

        StarSystemAPI system = (StarSystemAPI) location;
        SectorEntityToken relay = system.getEntityById("GearIn_observation_relay");
        List<SectorEntityToken> tagMatches = system.getEntitiesWithTag(
                "GearIn_observation_tag");
        List<?> tokenMatches = system.getEntities(SectorEntityToken.class);
        float daysSinceVisit = system.getDaysSinceLastPlayerVisit();
        String relayLocation = relay == null || relay.getContainingLocation() == null
                ? "null"
                : relay.getContainingLocation().getId();

        LOG.info(String.format(
                "GearIn location query: id=%s all=%d typed=%d tags=%d current=%s entered=%s daysSinceVisit=%s relayLocation=%s",
                system.getId(),
                system.getAllEntities().size(),
                tokenMatches.size(),
                tagMatches.size(),
                system.isCurrentLocation(),
                system.isEnteredByPlayer(),
                daysSinceVisit,
                relayLocation));
        done = true;
    }
}
