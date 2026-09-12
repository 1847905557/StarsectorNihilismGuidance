package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInCaptainRole {
    private static final Logger LOG = Global.getLogger(GearInCaptainRole.class);

    public void run() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI flagship = fleet.getFlagship();
        PersonAPI person = flagship.getCaptain();
        if (person == null || !"GearIn_officer".equals(person.getId())) {
            person = createCaptain();
        }
        flagship.setCaptain(person);
        flagship.setStatUpdateNeeded(true);
        FleetDataAPI data = fleet.getFleetData();
        OfficerDataAPI officer = data.getOfficerData(person);
        if (officer == null) {
            data.addOfficer(person);
            officer = data.getOfficerData(person);
        }
        LOG.info(String.format("captain=%s officer=%s personality=%s",
                flagship.getCaptain().getId(), officer != null,
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createCaptain() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_officer");
        person.setName(new FullName("Mira", "Vale", FullName.Gender.FEMALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
