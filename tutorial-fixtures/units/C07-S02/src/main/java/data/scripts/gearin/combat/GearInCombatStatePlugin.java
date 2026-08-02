package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗状态读取——引擎状态、时间语义与舰队/舰船遍历。
 *
 * 本插件由战斗上下文注册（C07-S01 的插件模型），只在本场战斗内有效：
 * - 引擎引用在 init() 中保存，只在本场战斗使用；禁止跨战斗缓存（C06-S03）；
 * - advance() 在暂停时仍会被调用（combatMap.advance 照常执行），
 *   状态推进前必须先检查 engine.isPaused()；
 * - getShips() 包含残骸与等待移除的舰船，遍历必须用 isAlive() 过滤；
 * - 战机停在甲板时 isAlive() 仍为 true，但不在 getShips() 的列表里；
 * - getPlayerShip() 在没有玩家舰时返回惰性创建的“Command Shuttle”假舰，
 *   永远非 null，不能拿它判断玩家部署状态。
 */
public class GearInCombatStatePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatStatePlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn battle init: campaign=" + engine.isInCampaign()
                + " campaignSim=" + engine.isInCampaignSim()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫：暂停时 advance 仍会被调用，状态不得推进（C07-S01）
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logEngineTime();
            logFleetManagers();
            logShips();
        }
    }

    /** 引擎时间语义：暂停不计/计、当前帧 dt、时间倍率、接触时间与战斗结果。 */
    private void logEngineTime() {
        LOG.info("GearIn time: total=" + engine.getTotalElapsedTime(false)
                + " totalWithPause=" + engine.getTotalElapsedTime(true)
                + " lastFrame=" + engine.getElapsedInLastFrame()
                + " timeMult=" + engine.getTimeMult().getModifiedValue()
                + " contact=" + engine.getElapsedInContactWithEnemy()
                + " fleetsInContact=" + engine.isFleetsInContact()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }

    /** 两侧舰队管理器：部署/后备/瘫痪/击毁/撤退五份名单的大小，以及已部署成员遍历。 */
    private void logFleetManagers() {
        for (FleetSide side : FleetSide.values()) {
            CombatFleetManagerAPI manager = engine.getFleetManager(side);
            LOG.info("GearIn fleet " + side + ": owner=" + manager.getOwner()
                    + " deployed=" + manager.getDeployedCopy().size()
                    + " reserves=" + manager.getReservesCopy().size()
                    + " disabled=" + manager.getDisabledCopy().size()
                    + " destroyed=" + manager.getDestroyedCopy().size()
                    + " retreated=" + manager.getRetreatedCopy().size());

            int aliveShips = 0;
            for (DeployedFleetMemberAPI member : manager.getDeployedCopyDFM()) {
                if (member.isFighterWing() || member.isStationModule()) {
                    continue;
                }
                ShipAPI ship = member.getShip();
                if (ship != null && ship.isAlive()) {
                    aliveShips++;
                }
            }
            LOG.info("GearIn deployed " + side + ": aliveShips=" + aliveShips);
        }
    }

    /** 引擎对象表遍历：getShips() 含残骸，必须用 isAlive() 过滤；顺带核对玩家舰判据。 */
    private void logShips() {
        int alive = 0;
        int notAlive = 0;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isAlive()) {
                alive++;
            } else {
                notAlive++;
            }
        }
        ShipAPI player = engine.getPlayerShip();
        LOG.info("GearIn ships: alive=" + alive + " notAlive=" + notAlive
                + " playerShip=" + (player == null ? "null" : player.getHullSpec().getHullId())
                + " playerAlive=" + (player != null && player.isAlive())
                + " playerInPlay=" + (player != null && engine.isEntityInPlay(player)));
    }
}
