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
 * 环节二 (舰队向)：舰队指挥官 -> 成员 stats 交接 (applyFleetwideToStats)。
 *
 * 注册入口：生涯回调 onGameLoad，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 建成员并入队：stats 只含装配基值 (无任何技能加成)；
 *   2. setFleetCommanderForStats 挂 crew_training(ALL_SHIPS_IN_FLEET)，getStats()
 *      惰性重建后 maxCombatReadiness 上升；
 *   3. 置回 null：舰队向加成撤销，数值恢复。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键与演示成员的增删)，不遗留任何持久状态。
 */
public class GearInCommanderToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CommanderToMember";

    private static final Logger LOG = Global.getLogger(GearInCommanderToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCommanderToMember() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内)
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
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn commanderToMember start: members=%d", initialCount));

        // --- 1. 建成员并入队：stats 只含装配基值 (无任何技能加成) ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleetData.addFleetMember(wolf);
        float maxCRBefore = wolf.getStats().getMaxCombatReadiness().getModifiedValue();

        // --- 2. 舰队指挥官 -> 成员 stats(ALL_SHIPS_IN_FLEET 经 applyFleetwideToStats 重放) ---
        PersonAPI commander = Global.getFactory().createPerson();
        commander.getStats().setSkillLevel("crew_training", 1f);
        wolf.setFleetCommanderForStats(commander, fleetData); // 人物变更 -> 标记 statUpdateNeeded
        float maxCRWithCommander = wolf.getStats().getMaxCombatReadiness().getModifiedValue();

        // --- 3. 置回 null：舰队向加成撤销，数值恢复 ---
        wolf.setFleetCommanderForStats(null, null);
        float maxCRRestored = wolf.getStats().getMaxCombatReadiness().getModifiedValue();
        LOG.info(String.format(
                "GearIn commanderToMember: maxCR %.3f -> %.3f (crewTraining) -> %.3f (reset)",
                maxCRBefore, maxCRWithCommander, maxCRRestored));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn commanderToMember done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
