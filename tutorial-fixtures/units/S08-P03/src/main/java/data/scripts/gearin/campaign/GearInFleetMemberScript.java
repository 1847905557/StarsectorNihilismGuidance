package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 完整最小例：生涯舰队成员（FleetMemberAPI）与舰队数据（FleetDataAPI）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK 一次性门控
 * （沿用既有 CLASS_MARK 门控模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停（isDone()=true，引擎下一帧自动移除本脚本）：
 *   1. 读取玩家舰队初始成员数与 fleet points、cargo 容量；
 *   2. 用 Global.getFactory().createFleetMember 创建 wolf_Assault 与 hammerhead_Elite 两个 SHIP 成员；
 *   3. 分别 addFleetMember 加入玩家舰队，记录加入前后成员数、fleet points、cargo 容量变化；
 *   4. 遍历 getMembersListCopy() 验证新成员在列；
 *   5. 船体状态：applyHullFractionDamage(0.3) -> getStatus().getHullFraction() / needsRepairs() -> repairFully()；
 *   6. CR：setCR(0.3f) 压低 CR 再恢复；
 *   7. 封存：setMothballed(true) 对 canBeDeployedForCombat() 与 getMembersInPriorityOrder() 的影响；
 *   8. 装配：setVariant 换成 wolf 装配 -> getHullId() 变化 -> 换回原装配；
 *   9. 清理：removeFleetMember 移除两个演示成员，恢复初始状态。
 *
 * 演示全程只操作演示成员；结束后玩家舰队成员数与初始一致。
 */
public class GearInFleetMemberScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_FleetMemberScript";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetMemberScript() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
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
        runSequence();
    }

    private void runSequence() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetDataAPI data = playerFleet.getFleetData();

        int initialCount = data.getNumMembers();
        float initialFleetPoints = playerFleet.getFleetPoints();
        float initialCargo = playerFleet.getCargo().getMaxCapacity();
        LOG.info(String.format(
                "GearIn fleet member start: members=%d fleetPoints=%.0f cargoCapacity=%.0f",
                initialCount, initialFleetPoints, initialCargo));

        // --- 创建成员（先不入队）---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        FleetMemberAPI hammerhead = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hammerhead_Elite");
        LOG.info(String.format(
                "GearIn fleet member created: wolf id=%s hull=%s variant=%s fp=%d | hammerhead id=%s hull=%s variant=%s fp=%d",
                wolf.getId(), wolf.getHullId(), wolf.getVariant().getHullVariantId(), wolf.getFleetPointCost(),
                hammerhead.getId(), hammerhead.getHullId(),
                hammerhead.getVariant().getHullVariantId(), hammerhead.getFleetPointCost()));

        // --- 加入舰队 ---
        data.addFleetMember(wolf);
        data.addFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn fleet member added: members=%d (was %d) fleetPoints=%.0f (was %.0f) cargoCapacity=%.0f (was %.0f)",
                data.getNumMembers(), initialCount,
                playerFleet.getFleetPoints(), initialFleetPoints,
                playerFleet.getCargo().getMaxCapacity(), initialCargo));

        // --- 遍历验证 ---
        List<FleetMemberAPI> members = data.getMembersListCopy();
        boolean wolfFound = false;
        boolean hammerheadFound = false;
        for (FleetMemberAPI member : members) {
            if (member.getId().equals(wolf.getId())) {
                wolfFound = true;
            }
            if (member.getId().equals(hammerhead.getId())) {
                hammerheadFound = true;
            }
        }
        LOG.info(String.format(
                "GearIn fleet member iterate: count=%d wolfFound=%s hammerheadFound=%s",
                members.size(), wolfFound, hammerheadFound));

        // --- 船体状态：损伤与修复 ---
        float hullBefore = hammerhead.getStatus().getHullFraction();
        hammerhead.getStatus().applyHullFractionDamage(0.3f);
        LOG.info(String.format(
                "GearIn fleet member damage: hullFraction %.2f -> %.2f needsRepairs=%s",
                hullBefore, hammerhead.getStatus().getHullFraction(), hammerhead.needsRepairs()));
        hammerhead.getStatus().repairFully();
        LOG.info(String.format(
                "GearIn fleet member repair: hullFraction=%.2f needsRepairs=%s",
                hammerhead.getStatus().getHullFraction(), hammerhead.needsRepairs()));

        // --- CR 与战斗准备 ---
        float crBefore = hammerhead.getRepairTracker().getCR();
        hammerhead.getRepairTracker().setCR(0.3f);
        LOG.info(String.format(
                "GearIn fleet member CR: %.2f -> %.2f canBeDeployedForCombat=%s",
                crBefore, hammerhead.getRepairTracker().getCR(), hammerhead.canBeDeployedForCombat()));
        hammerhead.getRepairTracker().setCR(crBefore);

        // --- 封存与遍历顺序 ---
        boolean deployBefore = hammerhead.canBeDeployedForCombat();
        hammerhead.getRepairTracker().setMothballed(true);
        List<FleetMemberAPI> priorityOrder = data.getMembersInPriorityOrder();
        String firstPriority = priorityOrder.isEmpty() ? "none" : priorityOrder.get(0).getShipName();
        LOG.info(String.format(
                "GearIn fleet member mothball: canBeDeployedForCombat=%s->%s firstPriority=%s mothballed=%s",
                deployBefore, hammerhead.canBeDeployedForCombat(), firstPriority, hammerhead.isMothballed()));
        hammerhead.getRepairTracker().setMothballed(false);

        // --- 装配写入：换装配 -> hull 变化 -> 换回 ---
        ShipVariantAPI originalVariant = hammerhead.getVariant();
        hammerhead.setVariant(wolf.getVariant(), true, true);
        LOG.info(String.format(
                "GearIn fleet member variant swap: hull=%s variant=%s",
                hammerhead.getHullId(), hammerhead.getVariant().getHullVariantId()));
        hammerhead.setVariant(originalVariant, true, true);
        LOG.info(String.format(
                "GearIn fleet member variant restore: hull=%s variant=%s",
                hammerhead.getHullId(), hammerhead.getVariant().getHullVariantId()));

        // --- 清理：恢复初始舰队 ---
        data.removeFleetMember(wolf);
        data.removeFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn fleet member done: members=%d (initial %d) fleetPoints=%.0f (initial %.0f)",
                data.getNumMembers(), initialCount, playerFleet.getFleetPoints(), initialFleetPoints));
    }
}
