package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
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
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
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

    public static void logState(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("Element Works state: not present at " + market.getName());
            return;
        }
        StringBuilder supply = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllSupply()) {
            if (supply.length() > 0) supply.append(", ");
            supply.append(quantity.getCommodityId()).append("=")
                    .append((int) quantity.getQuantity().getModifiedValue());
        }
        StringBuilder demand = new StringBuilder();
        for (MutableCommodityQuantity quantity : industry.getAllDemand()) {
            if (demand.length() > 0) demand.append(", ");
            demand.append(quantity.getCommodityId()).append("=")
                    .append((int) quantity.getQuantity().getModifiedValue());
        }
        LOG.info(String.format(
                "Element Works state: id=%s spec=%s supply=[%s] demand=[%s] income=%d upkeep=%d building=%s upgrading=%s functional=%s improved=%s disrupted=%s aiCore=%s",
                industry.getId(), industry.getSpec().getId(), supply, demand,
                (int) industry.getIncome().getModifiedValue(), (int) industry.getUpkeep().getModifiedValue(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(), industry.isImproved(),
                industry.isDisrupted(), industry.getAICoreId() == null ? "none" : industry.getAICoreId()));
    }

    public static void runDisruptionProbe(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) return;
        LOG.info(String.format("Element Works disruption before: disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
        industry.setDisrupted(10f, false);
        LOG.info(String.format("Element Works disruption during: disrupted=%s days=%.1f functional=%s",
                industry.isDisrupted(), industry.getDisruptedDays(), industry.isFunctional()));
        industry.setDisrupted(0f);
        LOG.info(String.format("Element Works disruption after: disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
    }
}
