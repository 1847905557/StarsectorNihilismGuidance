package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class GearInIndustryProbe {
    private static final Logger LOG = Global.getLogger(GearInIndustryProbe.class);

    private GearInIndustryProbe() {
    }

    public static void log(MarketAPI market) {
        Industry industry = market.getIndustry(GearInElementWorksIndustry.INDUSTRY_ID);
        if (industry == null) {
            LOG.info("Industry probe: Element Works not present at " + market.getName());
            return;
        }
        LOG.info(String.format(
                "Industry probe: market=%s id=%s name=%s size=%d income=%d upkeep=%d building=%s upgrading=%s functional=%s improved=%s disrupted=%s aiCore=%s",
                market.getName(), industry.getId(), industry.getCurrentName(), market.getSize(),
                (int) industry.getIncome().getModifiedValue(), (int) industry.getUpkeep().getModifiedValue(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(), industry.isImproved(),
                industry.isDisrupted(), industry.getAICoreId() == null ? "none" : industry.getAICoreId()));
        LOG.info(String.format("Industry probe: metals=%d fuel=%d heavyMachinery=%d rareMetals=%d",
                (int) industry.getDemand(Commodities.METALS).getQuantity().getModifiedValue(),
                (int) industry.getDemand(Commodities.FUEL).getQuantity().getModifiedValue(),
                (int) industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().getModifiedValue(),
                (int) industry.getSupply(Commodities.RARE_METALS).getQuantity().getModifiedValue()));
    }
}
