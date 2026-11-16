package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** Foundry 的管理员 Selene Ward：按固定 ID 创建一次，指派为市场管理员后刷新治理效果与产业。 */
public final class GearInAdministratorRole {

    public static final String PERSON_ID = "GearIn_admin";

    private GearInAdministratorRole() {
    }

    /** Foundry 尚未建立时返回 null。 */
    public static PersonAPI assign(SectorAPI sector) {
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return null;
        }
        PersonAPI admin = sector.getImportantPeople().getPerson(PERSON_ID);
        if (admin == null) {
            admin = Global.getFactory().createPerson();
            admin.setId(PERSON_ID);
            admin.setName(new FullName("Selene", "Ward", FullName.Gender.FEMALE));
            admin.setFaction(GearInModPlugin.FACTION_ID);
            admin.setRankId(Ranks.CITIZEN);
            admin.setPostId(Ranks.POST_ADMINISTRATOR);
            admin.setPersonality(Personalities.STEADY);
            sector.getImportantPeople().addPerson(admin);
        }
        market.setAdmin(admin);
        admin.getStats().refreshGovernedOutpostEffects(market);
        market.reapplyIndustries();
        return admin;
    }
}
