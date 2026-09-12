package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * 创建或读取 Aster Vance，并把人物加入 GearIn Foundry 的通信目录。
 * 固定人物 ID 与目录查询使重复调用保持同一个人物和目录条目。
 */
public class GearInPersonScript {

    public static final String PERSON_ID = "GearIn_aster_vance";

    private static final Logger LOG = Global.getLogger(GearInPersonScript.class);
    private static final String MARKET_ID = "GearIn_market_foundry";
    private static final String PERSON_TAG = "GearIn_aster_vance";
    private static final String PERSON_READY = "$GearIn_aster_vance_ready";

    public void run() {
        ImportantPeopleAPI importantPeople = Global.getSector().getImportantPeople();
        boolean existed = importantPeople.getPerson(PERSON_ID) != null;
        PersonAPI person = getOrCreatePerson();

        MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
        if (!market.getPeopleCopy().contains(person)) {
            market.addPerson(person);
        }
        person.setMarket(market);

        CommDirectoryAPI directory = market.getCommDirectory();
        int entriesBefore = directory.getEntriesCopy().size();
        if (directory.getEntryForPerson(person) == null) {
            directory.addPerson(person);
        }
        int entriesAfter = directory.getEntriesCopy().size();

        ImportantPeopleAPI.PersonDataAPI data = importantPeople.getData(person);
        LOG.info(String.format(
                "GearIn person: id=%s created=%s name=%s gender=%s faction=%s rank=%s post=%s "
                        + "tag=%s ready=%s important=%s dataLinked=%s market=%s directory=%s entries=%d->%d",
                person.getId(), !existed, person.getNameString(), person.getGender(),
                person.getFaction().getId(), person.getRankId(), person.getPostId(),
                person.hasTag(PERSON_TAG), person.getMemoryWithoutUpdate().getBoolean(PERSON_READY),
                importantPeople.containsPerson(person), data != null && data.getPerson() == person,
                market.getPeopleCopy().contains(person), person.getMarket() == market,
                directory.getEntryForPerson(person) != null,
                entriesBefore, entriesAfter));
    }

    private PersonAPI getOrCreatePerson() {
        ImportantPeopleAPI importantPeople = Global.getSector().getImportantPeople();
        PersonAPI person = importantPeople.getPerson(PERSON_ID);
        if (person != null) {
            return person;
        }

        person = Global.getFactory().createPerson();
        person.setId(PERSON_ID);
        person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setRankId(Ranks.CITIZEN);
        person.setPostId(Ranks.POST_TRADER);
        person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");
        person.addTag(PERSON_TAG);
        person.getMemoryWithoutUpdate().set(PERSON_READY, true);
        importantPeople.addPerson(person);
        return person;
    }
}
