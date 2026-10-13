package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInAdministratorRole {
    private static final Logger LOG = Global.getLogger(GearInAdministratorRole.class);

    public void run() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("GearIn_market_foundry");
        PersonAPI person = market.getAdmin();
        if (!"GearIn_admin".equals(person.getId())) {
            person = createAdministrator();
        }
        market.setAdmin(person);
                person.getStats().refreshGovernedOutpostEffects(market);
        market.reapplyIndustries();
        LOG.info(String.format("admin=%s market=%s personality=%s",
                market.getAdmin().getId(), person.getMarket().getId(),
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createAdministrator() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_admin");
        person.setName(new FullName("Selene", "Ward", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
