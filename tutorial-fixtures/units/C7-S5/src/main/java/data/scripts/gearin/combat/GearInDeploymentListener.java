package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener;

/**
 * 引擎级部署监听器：FleetMemberDeploymentListener 只能注册到引擎，
 * 注册到舰船不会生效 (CombatListenerUtil 只查引擎级表)。
 * 部署计数是本场战斗的状态，随战斗结束与监听器一起释放。
 * 绑定位置：由 GearInCombatLifecyclePlugin.init() 注册到 engine.getListenerManager()。
 */
public class GearInDeploymentListener implements FleetMemberDeploymentListener {

    private static final Logger LOG = Global.getLogger(GearInDeploymentListener.class);

    int deployedCount = 0;

    @Override
    public void reportFleetMemberDeployed(DeployedFleetMemberAPI member) {
        deployedCount++;
        ShipAPI ship = member.getShip();
        LOG.info("GearIn deployed: ally=" + member.isAlly()
                + " fighterWing=" + member.isFighterWing()
                + " ship=" + (ship == null ? "null" : ship.getHullSpec().getHullId()));
    }
}
