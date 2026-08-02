package data.scripts.gearin.lib;

import org.boxutil.base.BaseControlData;
import org.boxutil.base.api.RenderDataAPI;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 生命周期控制数据（BoxUtil ControlData 工具族）：把实体钉在被追踪舰船上
 * （位置/朝向每帧同步），舰船失效（死亡/出局）时把全局计时压到 1 以内，
 * 让实体尽快退出渲染；controlAdvance 由 BoxUtil 的逻辑线程调用，
 * 回调内只做同步与判定，不创建/入队/删除实体。
 */
public class GearInFollowControlData extends BaseControlData {

    private final ShipAPI ship;

    public GearInFollowControlData(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void controlAdvance(RenderDataAPI renderEntity, float amount) {
        if (ship == null) {
            return;
        }
        renderEntity.setLocation(ship.getLocation());
        renderEntity.setFacingScale(ship.getFacing(), 1f, 1f);
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isEntityInPlay(ship) || !ship.isAlive()) {
            float[] timer = renderEntity.getGlobalTimer();
            timer[0] = Math.min(timer[0], 1);
        }
    }
}
