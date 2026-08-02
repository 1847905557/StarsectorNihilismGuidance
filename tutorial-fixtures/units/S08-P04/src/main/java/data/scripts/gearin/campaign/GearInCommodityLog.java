package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * 商品日志器：对演示商品输出库存、可得量、供需上限、市场买入/卖出价与合法性
 * （价格方向以市场为主体：demandPrice = 市场买入 = 玩家卖出）。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInCommodityLog {

    private static final Logger LOG = Global.getLogger(GearInCommodityLog.class);

    private static final String[] COMMODITY_IDS = {
            Commodities.SUPPLIES,
            Commodities.DRUGS,
    };

    private GearInCommodityLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        for (String commodityId : COMMODITY_IDS) {
            CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
            LOG.info(String.format(
                    "GearIn commodity %s: stockpile=%.1f available=%d maxSupply=%d maxDemand=%d illegal=%s demandPrice(100)=%.0f supplyPrice(100)=%.0f",
                    commodityId, commodity.getStockpile(), commodity.getAvailable(),
                    commodity.getMaxSupply(), commodity.getMaxDemand(),
                    market.isIllegal(commodityId),
                    market.getDemandPrice(commodityId, 100, true),
                    market.getSupplyPrice(commodityId, 100, true)));
        }
    }
}
