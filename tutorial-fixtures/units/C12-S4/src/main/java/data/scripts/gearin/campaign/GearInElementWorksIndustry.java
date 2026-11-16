package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/** Element Works：消耗金属与重型机械，生产回火合金；输入短缺时按最大缺口削减产出。 */
public class GearInElementWorksIndustry extends BaseIndustry {

    public static final String INDUSTRY_ID = "GearIn_element_works";

    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();
        int metals = Math.max(1, size);
        int machinery = Math.max(1, size / 2);
        int output = Math.max(1, size - 1);

        demand(Commodities.METALS, metals, "Metal feedstock");
        demand(Commodities.HEAVY_MACHINERY, machinery, "Foundry equipment");
        supply(GearInAlloyCargo.COMMODITY_ID, output, "Tempered alloy output");

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.METALS, Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit, GearInAlloyCargo.COMMODITY_ID);
        if (!isFunctional()) {
            supply.clear();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        if (isFunctional()) {
            market.getStability().modifyFlat(getModId(2), 1, "Alpha core (" + getNameForModifier() + ")");
        } else {
            applyNoAICoreModifiers();
        }
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }
}
