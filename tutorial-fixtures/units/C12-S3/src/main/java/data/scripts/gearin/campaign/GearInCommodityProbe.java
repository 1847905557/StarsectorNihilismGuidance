package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

public class GearInCommodityProbe {
    private static final Logger LOG = Global.getLogger(GearInCommodityProbe.class);
    private static final String[] COMMODITY_IDS = {
            "GearIn_tempered_alloy",
            "supplies",
            "drugs"
    };

    private GearInCommodityProbe() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        LOG.info("GearIn commodity context: market=" + market.getId());
        for (String commodityId : COMMODITY_IDS) {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
            CommodityOnMarketAPI onMarket = market.getCommodityData(commodityId);
            LOG.info(String.format(
                    "GearIn commodity %s: name=%s demand=%s base=%.0f stack=%d space=%.1f tags=%s stockpile=%.1f available=%d maxSupply=%d maxDemand=%d utility=%.2f deficit=%d excess=%d tradeMod=%.1f illegal=%s supplyLegal=%s demandLegal=%s demandPrice(100)=%.0f supplyPrice(100)=%.0f",
                    spec.getId(), spec.getName(), spec.getDemandClass(), spec.getBasePrice(),
                    spec.getStackSize(), spec.getCargoSpace(), spec.getTags(),
                    onMarket.getStockpile(), onMarket.getAvailable(), onMarket.getMaxSupply(),
                    onMarket.getMaxDemand(), onMarket.getUtilityOnMarket(),
                    onMarket.getDeficitQuantity(), onMarket.getExcessQuantity(),
                    onMarket.getCombinedTradeModQuantity(), onMarket.isIllegal(),
                    onMarket.isSupplyLegal(), onMarket.isDemandLegal(),
                    market.getDemandPrice(commodityId, 100, true),
                    market.getSupplyPrice(commodityId, 100, true)));
        }
    }
}
