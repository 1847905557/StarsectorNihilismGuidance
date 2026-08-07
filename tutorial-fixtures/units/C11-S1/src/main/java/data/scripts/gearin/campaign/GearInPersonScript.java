package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 人物专题一次性演示脚本(协调器)：编排各角色小类并负责演示成员与清理，自停脚本。
 * 注册入口：生涯回调 onGameLoad，persistent data 键 REGISTER_MARK 一次性守卫；全程同帧回滚。
 */
public class GearInPersonScript implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_PersonLesson";

    private static final Logger LOG = Global.getLogger(GearInPersonScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInPersonScript() {
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        run();
    }

    private void run() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = new GearInBaseline().run(sector, fleetData);
        // 演示成员 wolf / hammerhead(供 captain / officer 小节使用)
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        FleetMemberAPI hammerhead = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hammerhead_Elite");
        fleetData.addFleetMember(wolf);
        fleetData.addFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn person lesson members: added wolf=%s hammerhead=%s count=%d",
                wolf.getShipName(), hammerhead.getShipName(), fleetData.getNumMembers()));

        // 人物 A：创建/身份/标签内存 -> 对玩家关系 -> 市场与重要人物注册
        PersonAPI personA = new GearInPersonIdentity().run();
        new GearInPersonRelationship().run(personA);
        new GearInPersonRegistration().run(sector, personA);

        // 舰长；人物 B 与军官；势力关系(同帧回滚)
        new GearInCaptain().run(wolf, personA, fleetData);
        new GearInOfficer().run(sector, hammerhead, fleetData, sector.getPlayerFleet());
        new GearInFactionRelation().run(sector);

        // 清理：恢复初始舰队
        fleetData.removeFleetMember(wolf);
        fleetData.removeFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn person lesson done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
