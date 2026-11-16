package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** Foundry 的联系人 Aster Vance：按固定 ID 创建一次，登记到重要人物服务、市场与通信目录。 */
public final class GearInAsterContact {

    public static final String PERSON_ID = "GearIn_aster_vance";

    private GearInAsterContact() {
    }

    /** 已存在则直接返回；Foundry 尚未建立时返回 null。 */
    public static PersonAPI create(SectorAPI sector) {
        ImportantPeopleAPI people = sector.getImportantPeople();
        PersonAPI person = people.getPerson(PERSON_ID);
        if (person != null) {
            return person;
        }
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return null;
        }

        person = Global.getFactory().createPerson();
        person.setId(PERSON_ID);
        person.setName(new FullName("Aster", "Vance", FullName.Gender.FEMALE));
        person.setFaction(GearInModPlugin.FACTION_ID);
        person.setRankId(Ranks.CITIZEN);
        person.setPostId(Ranks.POST_TRADER);
        person.setPortraitSprite("graphics/portraits/portrait_hegemony01.png");

        people.addPerson(person);
        market.addPerson(person);
        market.getCommDirectory().addPerson(person);
        people.getData(person).getLocation().setMarket(market);
        return person;
    }
}
