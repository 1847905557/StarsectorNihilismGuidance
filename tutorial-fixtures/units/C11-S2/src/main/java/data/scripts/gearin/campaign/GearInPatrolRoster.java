package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 为巡逻舰队增补工匠旗舰、指派指挥官，并为尚无军官的战斗舰船增派一名军官。 */
public final class GearInPatrolRoster {

    /** 巡逻队旗舰的装配 ID，沿用 C3-S2 配置的工匠。 */
    public static final String FLAGSHIP_VARIANT = "GearIn_artizan_combat";

    /** 指挥官等级。 */
    private static final int COMMANDER_LEVEL = 4;

    /** 增派军官等级。 */
    private static final int OFFICER_LEVEL = 2;

    private GearInPatrolRoster() {
    }

    public static FleetMemberAPI configure(CampaignFleetAPI fleet, Random random) {
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
        flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random));
        fleet.getFleetData().addFleetMember(flagship);

        PersonAPI commander = createCommander(random);
        fleet.setCommander(commander);
        fleet.getFleetData().setFlagship(flagship);

        addWingOfficerIfNeeded(fleet, flagship, random);
        fleet.forceSync();
        return flagship;
    }

    /** 创建巡逻队指挥官：等级与技能由原版抽选，姓名、性格与职务自行指定。 */
    public static PersonAPI createCommander(Random random) {
        PersonAPI person = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                COMMANDER_LEVEL, SkillPickPreference.ANY, random);
        person.setName(new FullName("Brann", "Hale", FullName.Gender.ANY));
        person.setPersonality(Personalities.AGGRESSIVE);
        person.setRankId(Ranks.SPACE_COMMANDER);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);
        return person;
    }

    /** 给点数最高且尚未配备舰长的非旗舰战斗成员增派一名军官。 */
    private static void addWingOfficerIfNeeded(CampaignFleetAPI fleet, FleetMemberAPI flagship, Random random) {
        FleetMemberAPI target = null;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == flagship || member.isFighterWing() || member.isCivilian()) {
                continue;
            }
            if (member.getCaptain() != null && !member.getCaptain().isDefault()) {
                continue;
            }
            if (target == null || member.getFleetPointCost() > target.getFleetPointCost()) {
                target = member;
            }
        }
        if (target == null) {
            return;
        }
        PersonAPI officer = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                OFFICER_LEVEL, SkillPickPreference.ANY, random);
        officer.setPostId(Ranks.POST_OFFICER);
        fleet.getFleetData().addOfficer(officer);
        target.setCaptain(officer);
    }
}
