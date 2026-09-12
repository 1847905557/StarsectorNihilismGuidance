package data.scripts.gearin.campaign;

import com.fs.starfarer.api.characters.FleetStatsSkillEffect;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;

public class GearInFleetCoordinationEffect implements FleetStatsSkillEffect, FleetTotalSource {
    public void apply(MutableFleetStatsAPI stats, String id, float level) {
        stats.getFleetwideMaxBurnMod().modifyFlat(id, 1f);
        stats.getFuelUseHyperMult().modifyMult(id, 0.9f);
    }

    public void unapply(MutableFleetStatsAPI stats, String id) {
        stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
        stats.getFuelUseHyperMult().unmodify(id);
    }

    public String getEffectDescription(float level) {
        return "+1 maximum burn level and -10% hyperspace fuel use for the fleet";
    }

    public String getEffectPerLevelDescription() {
        return null;
    }

    public ScopeDescription getScopeDescription() {
        return ScopeDescription.FLEET;
    }

    public FleetTotalItem getFleetTotalItem() {
        FleetTotalItem item = new FleetTotalItem();
        item.label = "Fleet coordination";
        item.value = "+1 burn, -10% fuel";
        item.sortOrder = 50f;
        return item;
    }
}
