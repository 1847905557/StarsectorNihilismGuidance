package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/** Warfront：交战地带的市场条件，危险度上升、流通性下降，但舰队质量提高。 */
public class GearInWarfrontCondition extends BaseMarketConditionPlugin {

    public static final String CONDITION_ID = "GearIn_warfront";

    private static final float HAZARD_BONUS = 0.15f;
    private static final float ACCESSIBILITY_PENALTY = -0.05f;
    private static final float FLEET_QUALITY_BONUS = 0.15f;

    @Override
    public void apply(String id) {
        String source = getModId();
        market.getHazard().modifyFlat(source, HAZARD_BONUS, getName());
        market.getAccessibilityMod().modifyFlat(source, ACCESSIBILITY_PENALTY, getName());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(source, FLEET_QUALITY_BONUS, getName());
    }

    @Override
    public void unapply(String id) {
        String source = getModId();
        market.getHazard().unmodifyFlat(source);
        market.getAccessibilityMod().unmodifyFlat(source);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(source);
    }
}
