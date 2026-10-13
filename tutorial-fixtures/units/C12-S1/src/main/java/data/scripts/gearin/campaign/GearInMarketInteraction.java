package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class GearInMarketInteraction implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInMarketInteraction.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private static final String TARIFF_ID = "GearIn_market_interaction_tariff";
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
        MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
        if (market == null) {
            done = true;
            return;
        }
        recordPersonAndDirectory(market);
        recordMemory(market);
        restoreMarketState(market);
        done = true;
    }

    private void recordPersonAndDirectory(MarketAPI market) {
        PersonAPI person = Global.getFactory().createPerson();
        market.addPerson(person);
        CommDirectoryAPI directory = market.getCommDirectory();
        String entryId = directory.addPerson(person);
        LOG.info(String.format("GearIn directory: marketPeople=%d entries=%d entryId=%s found=%s",
                market.getPeopleCopy().size(), directory.getEntriesCopy().size(), entryId,
                directory.getEntryForPerson(person) != null));
        directory.removePerson(person);
        market.removePerson(person);
    }

    private void recordMemory(MarketAPI market) {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        boolean hadPatrol = memory.contains(MemFlags.MARKET_PATROL);
        Object oldPatrol = memory.get(MemFlags.MARKET_PATROL);
        float oldExpire = memory.getExpire(MemFlags.MARKET_PATROL);
        try {
            memory.set(MemFlags.MARKET_PATROL, true);
            LOG.info("GearIn market memory: patrol="
                    + memory.getBoolean(MemFlags.MARKET_PATROL));
        } finally {
            if (hadPatrol) {
                memory.set(MemFlags.MARKET_PATROL, oldPatrol, oldExpire);
            } else {
                memory.unset(MemFlags.MARKET_PATROL);
            }
            LOG.info("GearIn market memory restored: present="
                    + memory.contains(MemFlags.MARKET_PATROL)
                    + " value=" + memory.get(MemFlags.MARKET_PATROL)
                    + " expire=" + memory.getExpire(MemFlags.MARKET_PATROL));
        }
    }

    private void restoreMarketState(MarketAPI market) {
        MutableStat tariff = market.getTariff();
        float oldTariff = tariff.getModifiedValue();
        boolean oldFreePort = market.isFreePort();
        boolean hadStorage = market.hasSubmarket("storage");
        LOG.info(String.format("GearIn market interaction before: tariff=%.3f freePort=%s storage=%s",
                oldTariff, oldFreePort, hadStorage));
        tariff.modifyFlat(TARIFF_ID, 0.05f);
        market.setFreePort(!oldFreePort);
        market.addSubmarket("storage");
        LOG.info(String.format("GearIn market interaction during: tariff=%.3f freePort=%s storage=%s",
                tariff.getModifiedValue(), market.isFreePort(), market.hasSubmarket("storage")));
        tariff.unmodify(TARIFF_ID);
        market.setFreePort(oldFreePort);
        if (!hadStorage) {
            market.removeSubmarket("storage");
        }
        LOG.info(String.format("GearIn market interaction restored: tariff=%.3f freePort=%s storage=%s",
                tariff.getModifiedValue(), market.isFreePort(), market.hasSubmarket("storage")));
    }
}
