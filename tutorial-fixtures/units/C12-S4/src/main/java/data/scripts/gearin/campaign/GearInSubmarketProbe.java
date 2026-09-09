package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

public class GearInSubmarketProbe {
    private static final Logger LOG = Global.getLogger(GearInSubmarketProbe.class);
    private static final String[] SPEC_IDS = {
            Submarkets.SUBMARKET_OPEN,
            Submarkets.SUBMARKET_BLACK,
            Submarkets.SUBMARKET_STORAGE,
            "GearIn_refinery_depot"
    };

    private GearInSubmarketProbe() {
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
            CargoAPI cargo = submarket.getCargoNullOk();
            LOG.info(String.format(
                    "GearIn submarket %s: name=%s faction=%s tariff=%.2f plugin=%s cargo=%s open=%s black=%s freeTransfer=%s military=%s participates=%s impactMode=%s impactMult=%.1f",
                    submarket.getSpecId(), submarket.getNameOneLine(),
                    submarket.getFaction().getId(), plugin.getTariff(),
                    plugin.getClass().getSimpleName(), cargo == null ? "null" : "created",
                    plugin.isOpenMarket(), plugin.isBlackMarket(), plugin.isFreeTransfer(),
                    plugin.isMilitaryMarket(), plugin.isParticipatesInEconomy(),
                    plugin.getPlayerEconomyImpactMode(), plugin.getPlayerTradeImpactMult()));
        }
    }
}
