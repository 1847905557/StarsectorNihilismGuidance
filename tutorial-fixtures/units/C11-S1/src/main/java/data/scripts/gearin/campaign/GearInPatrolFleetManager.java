package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 齿轮工业巡逻舰队管理器：从源点出动，在所在星系巡逻，被击毁后按间隔补充。 */
public class GearInPatrolFleetManager extends SourceBasedFleetManager {

    /** 玩家离源点超过这个距离 (光年) 时，数量降到最小值。 */
    private static final float THRESHOLD_LY = 3f;

    private static final int MIN_FLEETS = 1;
    private static final int MAX_FLEETS = 2;

    /** 被击毁后补充一支舰队所需的生涯天数。 */
    private static final float RESPAWN_DAYS = 20f;

    private static final float COMBAT_POINTS = 30f;

    /** 巡逻队旗舰的装配 ID，沿用 C3-S2 配置的工匠。 */
    private static final String FLAGSHIP_VARIANT = "GearIn_artizan_combat";

    /** 指挥官等级。 */
    private static final int COMMANDER_LEVEL = 4;

    /** 僚舰军官等级。 */
    private static final int OFFICER_LEVEL = 2;

    public GearInPatrolFleetManager(SectorEntityToken source) {
        super(source, THRESHOLD_LY, MIN_FLEETS, MAX_FLEETS, RESPAWN_DAYS);
    }

    @Override
    protected CampaignFleetAPI spawnFleet() {
        if (source == null) {
            return null;
        }
        Random random = new Random();
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                GearInModPlugin.FACTION_ID,
                1f,
                FleetTypes.PATROL_SMALL,
                COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.random = random;
        params.withOfficers = false;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) {
            return null;
        }

        // 旗舰与指挥官由我们自己指定。setFlagship 会重置其他成员的舰长，所以放在最前面
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
        flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random));
        fleet.getFleetData().addFleetMember(flagship);
        fleet.getFleetData().setFlagship(flagship);
        PersonAPI commander = createCommander(random);
        fleet.setCommander(commander);
        flagship.setCaptain(commander);
        addWingOfficer(fleet, flagship, random);
        fleet.forceSync();

        source.getContainingLocation().addEntity(fleet);
        fleet.setLocation(source.getLocation().x, source.getLocation().y);
        fleet.setFacing(random.nextFloat() * 360f);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);

        fleet.addScript(new GearInPatrolAssignmentScript(fleet, source));
        return fleet;
    }

    /** 创建巡逻队指挥官：等级与技能由原版抽选，姓名、性格与职务自行指定。 */
    private PersonAPI createCommander(Random random) {
        PersonAPI person = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                COMMANDER_LEVEL, SkillPickPreference.ANY, random);
        person.setName(new FullName("Brann", "Hale", FullName.Gender.ANY));
        person.setPersonality(Personalities.AGGRESSIVE);
        person.setRankId(Ranks.SPACE_COMMANDER);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);
        return person;
    }

    /** 给点数最高的非旗舰成员配一名军官：先登记进舰队的军官名单，再派到这艘舰船上。 */
    private void addWingOfficer(CampaignFleetAPI fleet, FleetMemberAPI flagship, Random random) {
        FleetMemberAPI target = null;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == flagship || member.isFighterWing() || member.isCivilian()) {
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
