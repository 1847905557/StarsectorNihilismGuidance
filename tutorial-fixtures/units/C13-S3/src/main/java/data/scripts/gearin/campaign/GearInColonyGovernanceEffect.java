package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.MarketSkillEffect;

public class GearInColonyGovernanceEffect implements MarketSkillEffect {
    public void apply(MarketAPI market, String id, float level) {
        market.getStability().modifyFlat(id, 1f, "Colony governance");
        market.getIncomeMult().modifyPercent(id, 10f, "Colony governance");
    }

    public void unapply(MarketAPI market, String id) {
        market.getStability().unmodifyFlat(id);
        market.getIncomeMult().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+1 stability and +10% income for the governed market";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.GOVERNED_OUTPOST;
    }
}
