package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class GearInCaptainMobilityEffect implements ShipSkillEffect {
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        stats.getMaxSpeed().modifyPercent(id, 8f);
        stats.getAcceleration().modifyPercent(id, 12f);
    }

    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+8% top speed and +12% acceleration for the piloted ship";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.PILOTED_SHIP;
    }
}
