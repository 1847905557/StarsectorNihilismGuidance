package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInWarfrontCondition extends BaseMarketConditionPlugin {
    public static final String CONDITION_ID = "GearIn_warfront";
    private static final Logger LOG = Global.getLogger(GearInWarfrontCondition.class);
    private float multiplier = 1f;
    private float elapsed;

    @Override
    public void init(MarketAPI market, MarketConditionAPI condition) {
        super.init(market, condition);
        multiplier = 1f;
        elapsed = 0f;
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Float && ((Float) param) > 0f) {
            multiplier = (Float) param;
        } else {
            multiplier = 1f;
        }
    }

    @Override
    public void apply(String id) {
        String source = getModId();
        market.getHazard().modifyFlat(source, 0.15f * multiplier, getName());
        market.getAccessibilityMod().modifyFlat(source, -0.05f * multiplier, getName());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(source, 0.15f * multiplier, getName());
        LOG.info(String.format("Warfront applied: market=%s token=%s multiplier=%.2f hazard=%.2f accessibility=%.2f fleetQuality=%.2f",
                market.getName(), source, multiplier,
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f)));
    }

    @Override
    public void unapply(String id) {
        String source = getModId();
        market.getHazard().unmodifyFlat(source);
        market.getAccessibilityMod().unmodifyFlat(source);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(source);
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public float getElapsed() {
        return elapsed;
    }
}
