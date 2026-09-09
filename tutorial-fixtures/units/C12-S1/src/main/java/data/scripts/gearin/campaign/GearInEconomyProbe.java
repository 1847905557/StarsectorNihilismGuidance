package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

public class GearInEconomyProbe implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInEconomyProbe.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        SectorAPI sector = Global.getSector();
        EconomyAPI economy = sector.getEconomy();
        MarketAPI market = economy.getMarket(MARKET_ID);
        if (market == null) {
            LOG.warn("GearIn market probe: market missing id=" + MARKET_ID);
            done = true;
            return;
        }
        logMarket(economy, market);
        logEconomy(economy, market);
        economy.addUpdateListener(new ProbeListener());
        LOG.info("GearIn market probe: economy listener registered");
        done = true;
    }

    private void logEconomy(EconomyAPI economy, MarketAPI market) {
        StarSystemAPI system = market.getStarSystem();
        LOG.info(String.format(
                "GearIn economy probe: sameGroup=%d locationMarkets=%d system=%s specs=%s",
                economy.getMarketsWithSameGroup(market).size(),
                economy.getMarkets(market.getContainingLocation()).size(),
                system == null ? "none" : system.getId(),
                economy.getCommoditySpec("supplies").getId()));
        economy.forceStockpileUpdate(market);
    }

    private void logMarket(EconomyAPI economy, MarketAPI market) {
        CommodityOnMarketAPI supplies = market.getCommodityData("supplies");
        MutableStat tariff = market.getTariff();
        LOG.info(String.format(
                "GearIn market probe: id=%s entity=%s faction=%s size=%d markets=%d commodities=%d tariff=%.3f stockpile=%.1f available=%.1f demandPrice=%.1f supplyPrice=%.1f people=%d admin=%s commEntries=%d freePort=%s submarkets=%d",
                market.getId(), market.getPrimaryEntity().getId(), market.getFactionId(),
                market.getSize(), economy.getNumMarkets(), economy.getAllCommodityIds().size(),
                tariff.getModifiedValue(), supplies.getStockpile(), supplies.getAvailable(),
                market.getDemandPrice("supplies", 10, true),
                market.getSupplyPrice("supplies", 10, true),
                market.getPeopleCopy().size(), market.getAdmin().getName(),
                market.getCommDirectory().getEntriesCopy().size(), market.isFreePort(),
                market.getSubmarketsCopy().size()));
    }

    private static class ProbeListener implements EconomyAPI.EconomyUpdateListener {
        private boolean expired;

        @Override
        public void commodityUpdated(String commodityId) {
            LOG.info("GearIn economy update: commodity=" + commodityId);
        }

        @Override
        public void economyUpdated() {
            LOG.info("GearIn economy update: complete");
            expired = true;
        }

        @Override
        public boolean isEconomyListenerExpired() {
            return expired;
        }
    }
}
