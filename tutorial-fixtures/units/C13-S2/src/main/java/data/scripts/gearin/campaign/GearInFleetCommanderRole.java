package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

public class GearInFleetCommanderRole {
    private static final Logger LOG = Global.getLogger(GearInFleetCommanderRole.class);

    public void run(CampaignFleetAPI fleet) {
        PersonAPI person = fleet.getCommander();
        if (!"GearIn_commander".equals(person.getId())) {
            person = createCommander();
        }
        fleet.setCommander(person);
        fleet.forceSync();
        LOG.info(String.format("commander=%s flagshipCommander=%s personality=%s",
                fleet.getCommander().getId(),
                fleet.getFlagship().getFleetCommander().getId(),
                person.getPersonalityAPI().getId()));
    }

    private PersonAPI createCommander() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setId("GearIn_commander");
        person.setName(new FullName("Rowan", "Hale", FullName.Gender.MALE));
        person.setFaction("gear_industry");
        person.setPersonality(Personalities.STEADY);
        return person;
    }
}
