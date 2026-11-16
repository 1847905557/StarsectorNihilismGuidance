package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** 巡逻队被击伤后返航修复：战斗后检查船体，低于阈值就改道回源点，到达后修复并清除标记。 */
public class GearInPatrolDamageListener implements FleetEventListener {

    /** 舰队 Memory：正在返航修复。 */
    public static final String DAMAGED_KEY = "$GearIn_patrol_damaged";

    /** 任一成员船体低于这个比例就返航。 */
    private static final float RETURN_HULL_FRACTION = 0.6f;

    private final SectorEntityToken source;

    public GearInPatrolDamageListener(SectorEntityToken source) {
        this.source = source;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (fleet == null || !fleet.isAlive() || source == null) {
            return;
        }
        if (fleet.getMemoryWithoutUpdate().getBoolean(DAMAGED_KEY)) {
            return;
        }
        if (!isBadlyDamaged(fleet)) {
            return;
        }
        fleet.getMemoryWithoutUpdate().set(DAMAGED_KEY, true);
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source, 30f, "returning for repairs");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 3f, "repairing", new RepairScript(fleet));
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    }

    private boolean isBadlyDamaged(CampaignFleetAPI fleet) {
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getStatus().getHullFraction() < RETURN_HULL_FRACTION) {
                return true;
            }
        }
        return false;
    }

    /** 修复完成后执行：恢复船体与 CR，清除返航标记，任务队列走空后由任务脚本重新安排巡逻。 */
    private static class RepairScript implements Script {

        private final CampaignFleetAPI fleet;

        RepairScript(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public void run() {
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                member.getStatus().repairFully();
                member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            }
            fleet.getFleetData().setSyncNeeded();
            fleet.getMemoryWithoutUpdate().unset(DAMAGED_KEY);
        }
    }
}
