package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI.PersonDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 人物注册（归属入口）：市场通讯录 addPerson/removePerson 双向交接，重要人物 addPerson/contains/getData/removePerson。
 */
public class GearInPersonRegistration {

    private static final Logger LOG = Global.getLogger(GearInPersonRegistration.class);

    public void run(SectorAPI sector, PersonAPI personA) {
        EconomyAPI economy = sector.getEconomy();
        MarketAPI market = economy.getMarket("jangala");
        if (market == null) {
            for (MarketAPI candidate : economy.getMarketsCopy()) {
                if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                    market = candidate;
                    break;
                }
            }
        }
        if (market != null) {
            market.addPerson(personA);
            boolean inMarketAfterAdd = market.getPeopleCopy().contains(personA);
            boolean personMarketLinked = personA.getMarket() == market;
            market.removePerson(personA);
            boolean inMarketAfterRemove = market.getPeopleCopy().contains(personA);
            LOG.info(String.format(
                    "GearIn person lesson market: id=%s inAfterAdd=%s personMarketLinked=%s inAfterRemove=%s",
                    market.getId(), inMarketAfterAdd, personMarketLinked, inMarketAfterRemove));
        } else {
            LOG.info("GearIn person lesson market: none found, skipped");
        }

        sector.getImportantPeople().addPerson(personA);
        boolean importantAfterAdd = sector.getImportantPeople().containsPerson(personA);
        PersonDataAPI personData = sector.getImportantPeople().getData(personA);
        boolean personDataLinked = personData != null && personData.getPerson() == personA;
        sector.getImportantPeople().removePerson(personA);
        boolean importantAfterRemove = sector.getImportantPeople().containsPerson(personA);
        LOG.info(String.format(
                "GearIn person lesson importantPeople: containsAfterAdd=%s dataLinked=%s containsAfterRemove=%s",
                importantAfterAdd, personDataLinked, importantAfterRemove));
    }
}
