package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class GearInCaptainMobilityEliteEffect implements ShipSkillEffect {
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        stats.getTurnAcceleration().modifyPercent(id, 15f);
    }

    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getTurnAcceleration().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+15% turn acceleration for the piloted ship";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.PILOTED_SHIP;
    }
}
