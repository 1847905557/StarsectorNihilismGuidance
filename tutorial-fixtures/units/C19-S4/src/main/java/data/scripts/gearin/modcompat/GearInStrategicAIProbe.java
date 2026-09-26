package data.scripts.gearin.modcompat;

import java.util.Map;

import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexConfig;

public final class GearInStrategicAIProbe {
    public int aiMapSize() {
        Map<String, StrategicAI> ais = StrategicAI.getAIsMap();
        return ais.size();
    }

    public int concernCount(String factionId) {
        StrategicAI ai = StrategicAI.getAI(factionId);
        return ai == null ? 0 : ai.getExistingConcerns().size();
    }

    public boolean ensureAI(String factionId) {
        StrategicAI before = StrategicAI.getAI(factionId);
        StrategicAI created = StrategicAI.addAIIfNeeded(factionId);
        return before == null && (created != null || StrategicAI.getAI(factionId) != null);
    }

    public boolean forceMeetingIfReady(String factionId) {
        StrategicAI ai = StrategicAI.getAI(factionId);
        if (ai == null) {
            return false;
        }
        ai.forceMeeting();
        return true;
    }

    public boolean respawnAllowedByRules(String factionId) {
        SectorManager manager = SectorManager.getManager();
        if (manager == null || !manager.isRespawnFactions()) {
            return false;
        }
        int maxRespawns = NexConfig.maxFactionRespawns;
        boolean hasAttempt = maxRespawns < 0
                || manager.getNumRespawns(factionId) < maxRespawns;
        return NexConfig.enableHostileFleetEvents
                && NexConfig.enableInvasions
                && NexConfig.getFactionConfig(factionId).playableFaction
                && hasAttempt;
    }
}
