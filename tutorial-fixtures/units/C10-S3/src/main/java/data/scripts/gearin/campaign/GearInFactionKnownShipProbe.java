package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInFactionKnownShipProbe implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFactionKnownShipProbe.class);

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
        FactionAPI faction = sector.getFaction(Factions.HEGEMONY);
        String hullId = "lasher";
        Map<String, Long> timestamps = faction.getShipTimestamps();
        boolean wasKnown = faction.knowsShip(hullId);
        boolean wasPriority = faction.isShipPriority(hullId);
        Long previousTimestamp = timestamps.get(hullId);

        faction.addKnownShip(hullId, false);
        faction.addPriorityShip(hullId);
        LOG.info(String.format(
                "GearIn faction known: hull=%s known=%s priority=%s cacheTimestamp=%s",
                hullId,
                faction.knowsShip(hullId),
                faction.isShipPriority(hullId),
                faction.getShipTimestamps().get(hullId)));

        if (wasPriority) {
            faction.addPriorityShip(hullId);
        } else {
            faction.removePriorityShip(hullId);
        }

        if (wasKnown) {
            if (previousTimestamp == null) {
                timestamps.remove(hullId);
            } else {
                timestamps.put(hullId, previousTimestamp);
            }
        } else {
            faction.removeKnownShip(hullId);
        }

        LOG.info(String.format(
                "GearIn faction known restored: hull=%s known=%s priority=%s timestamp=%s",
                hullId,
                faction.knowsShip(hullId),
                faction.isShipPriority(hullId),
                faction.getShipTimestamps().get(hullId)));
        done = true;
    }
}
