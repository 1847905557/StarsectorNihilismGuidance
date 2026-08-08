package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节四：MutableStat 重建与惰性重算 (updateStats 整体新建舰船 stats)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 运行时 transient modifier 不跨重建：直接 modifyFlat 到 getStats() 返回值，
 *      updateStats 后 stats 对象整体重建 (对象同一性为 false)、修改被丢弃；
 *   2. 惰性重建：setStatUpdateNeeded(true) 后下一次 getStats() 才重建。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键与演示成员的增删)，不遗留任何持久状态。
 */
public class GearInStatsRebuild implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_StatsRebuild";

    private static final Logger LOG = Global.getLogger(GearInStatsRebuild.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInStatsRebuild() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
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
        LOG.info(String.format("GearIn statsRebuild start: members=%d", initialCount));

        // --- 1. 建成员并入队 (无舰长：updateStats 重放时无技能加成干扰) ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleetData.addFleetMember(wolf);
        wolf.updateStats();

        // --- 2. 运行时 transient modifier 不跨重建：updateStats 整体新建舰船 stats ---
        MutableShipStatsAPI before = wolf.getStats();
        float speedBefore = before.getMaxSpeed().getModifiedValue();
        before.getMaxSpeed().modifyFlat("GearIn_transient_speed", 25f);
        float speedWithTransient = wolf.getStats().getMaxSpeed().getModifiedValue();
        wolf.updateStats();
        MutableShipStatsAPI after = wolf.getStats();
        LOG.info(String.format(
                "GearIn statsRebuild transient: speed %.1f -> %.1f (with transient), "
                        + "rebuiltNewObject=%s speedAfterRebuild=%.1f",
                speedBefore, speedWithTransient, before != after, after.getMaxSpeed().getModifiedValue()));

        // --- 3. 惰性重建：setStatUpdateNeeded(true) 后下一次 getStats() 才重建 ---
        wolf.setStatUpdateNeeded(true);
        MutableShipStatsAPI lazy = wolf.getStats();
        LOG.info(String.format("GearIn statsRebuild lazy: sameObjectAsBefore=%s", after == lazy));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn statsRebuild done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
