package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 军官(OfficerDataAPI)：势力随机人物 -> 包装入 roster -> XP/技能候选/升级 -> 上舰 -> 移除。
 */
public class GearInOfficer {

    private static final Logger LOG = Global.getLogger(GearInOfficer.class);

    public void run(SectorAPI sector, FleetMemberAPI hammerhead, FleetDataAPI fleetData,
                    CampaignFleetAPI playerFleet) {
        PersonAPI personB = sector.getFaction("hegemony").createRandomPerson();
        LOG.info(String.format(
                "GearIn person lesson personB random: name=%s female=%s faction=%s personality=%s portraitIsDefault=%s",
                personB.getNameString(), personB.isFemale(), personB.getFaction().getId(),
                personB.getPersonalityAPI().getId(), personB.isDefault()));

        OfficerDataAPI officer = Global.getFactory().createOfficerData(personB);
        fleetData.addOfficer(officer);
        LOG.info(String.format(
                "GearIn person lesson officer add: count=%d fleetLinked=%s",
                fleetData.getOfficersCopy().size(), personB.getFleet() == playerFleet));

        long xpBefore = personB.getStats().getXP();
        officer.addXP(100000);
        boolean canLevelUp = officer.canLevelUp();
        officer.makeSkillPicks();
        List<String> picks = officer.getSkillPicks();
        LOG.info(String.format(
                "GearIn person lesson officer xp: xp=%d -> %d canLevelUp=%s picks=%d",
                xpBefore, personB.getStats().getXP(), canLevelUp, picks.size()));
        if (!picks.isEmpty()) {
            String pick = picks.get(0);
            officer.levelUp(pick);
            LOG.info(String.format(
                    "GearIn person lesson officer levelUp: level=%d skill=%s skillLevel=%s",
                    personB.getStats().getLevel(), pick, personB.getStats().getSkillLevel(pick)));
        }

        hammerhead.setCaptain(personB);
        LOG.info(String.format(
                "GearIn person lesson officer captain: sameCaptain=%s memberWithCaptainFound=%s officerInRoster=%s",
                hammerhead.getCaptain() == personB,
                fleetData.getMemberWithCaptain(personB) == hammerhead,
                fleetData.getOfficersCopy().contains(officer)));

        fleetData.removeOfficer(personB);
        LOG.info(String.format(
                "GearIn person lesson officer remove: count=%d fleetLinked=%s",
                fleetData.getOfficersCopy().size(), personB.getFleet() == null));
    }
}
