package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 子市场差异日志器：遍历 open_market/black_market/storage 三种原版子市场，
 * 输出税率、势力、plugin 类、cargo 惰性创建语义与开放/黑市/免费转移标志。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInSubmarketLog {

    private static final Logger LOG = Global.getLogger(GearInSubmarketLog.class);

    private static final String[] SPEC_IDS = {
            Submarkets.SUBMARKET_OPEN,
            Submarkets.SUBMARKET_BLACK,
            Submarkets.SUBMARKET_STORAGE,
    };

    private GearInSubmarketLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        for (String specId : SPEC_IDS) {
            if (!market.hasSubmarket(specId)) {
                LOG.info("GearIn submarket " + specId + ": present=false");
                continue;
            }
            SubmarketAPI submarket = market.getSubmarket(specId);
            SubmarketPlugin plugin = submarket.getPlugin();
            CargoAPI cargoNullOk = submarket.getCargoNullOk();
            float supplies = 0f;
            if (cargoNullOk != null) {
                supplies = cargoNullOk.getCommodityQuantity(Commodities.SUPPLIES);
            }
            LOG.info(String.format(
                    "GearIn submarket %s: present=true name=%s tariff=%.3f faction=%s plugin=%s cargoNullOk=%s supplies=%.1f open=%s black=%s freeTransfer=%s",
                    specId, submarket.getNameOneLine(), submarket.getTariff(),
                    submarket.getFaction().getId(), plugin.getClass().getSimpleName(),
                    cargoNullOk == null ? "null" : "created", supplies,
                    plugin.isOpenMarket(), plugin.isBlackMarket(), plugin.isFreeTransfer()));
        }
    }
}
