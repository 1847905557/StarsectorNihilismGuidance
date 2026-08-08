package data.scripts.gearin.modcompat;

import exerelin.campaign.fleets.InvasionFleetManager;

public final class GearInInvasionScaleProbe {
    public float estimate(String factionId, float baseSize) {
        float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
        return baseSize * combined;
    }

    public float applyEventPostProcessing(String factionId, float baseSize,
            InvasionFleetManager.EventType type) {
        float fp = baseSize;
        if (type == InvasionFleetManager.EventType.BLOCKADE) {
            fp = Math.max(fp, InvasionFleetManager.MIN_BLOCKADE_SIZE);
        }
        fp *= InvasionFleetManager.getInvasionSizeMult(factionId);
        if (type == InvasionFleetManager.EventType.RESPAWN) {
            fp *= InvasionFleetManager.RESPAWN_SIZE_MULT;
        } else if (type == InvasionFleetManager.EventType.BLOCKADE) {
            fp = fp * InvasionFleetManager.BLOCKADE_SIZE_MULT
                    + InvasionFleetManager.BLOCKADE_FLAT_FP_BONUS;
        }
        return fp;
    }
}
