package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

public class GearInElementWorksIndustry extends BaseIndustry {
    public static final String INDUSTRY_ID = "GearIn_element_works";
    private static final Logger LOG = Global.getLogger(GearInElementWorksIndustry.class);

    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();
        int metals = Math.max(1, size);
        int fuel = Math.max(1, size / 2);
        int machinery = Math.max(1, size / 2);
        int output = Math.max(1, size - 1);

        demand(Commodities.METALS, metals, "Metal feedstock");
        demand(Commodities.FUEL, fuel, "Refinery fuel");
        demand(Commodities.HEAVY_MACHINERY, machinery, "Refinery equipment");
        supply(Commodities.RARE_METALS, output, "Rare element output");

        Pair<String, Integer> deficit = getMaxDeficit(
                Commodities.METALS, Commodities.FUEL, Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit, Commodities.RARE_METALS);
        if (!isFunctional()) {
            supply.clear();
        }

        LOG.info(String.format(
                "Element Works applied: market=%s size=%d output=%d income=%d upkeep=%d functional=%s disrupted=%s aiCore=%s improved=%s",
                market.getName(), size,
                (int) getSupply(Commodities.RARE_METALS).getQuantity().getModifiedValue(),
                (int) getIncome().getModifiedValue(), (int) getUpkeep().getModifiedValue(),
                isFunctional(), isDisrupted(), aiCoreId == null ? "none" : aiCoreId, isImproved()));
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        if (isFunctional()) {
            market.getStability().modifyFlat(getModId(2), 1,
                    "Alpha core (" + getNameForModifier() + ")");
        } else {
            applyNoAICoreModifiers();
        }
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }

    @Override
    protected void notifyDisrupted() {
        LOG.info(String.format("Element Works disrupted: market=%s days=%.1f",
                market.getName(), getDisruptedDays()));
    }

    @Override
    protected void disruptionFinished() {
        LOG.info("Element Works disruption finished: market=" + market.getName());
    }

}
