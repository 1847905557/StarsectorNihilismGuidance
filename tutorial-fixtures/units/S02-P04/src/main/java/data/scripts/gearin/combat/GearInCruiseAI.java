package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;

/**
 * 最小巡航 AI：不瞄准、不转向，只在直行方向上加减速。
 *
 * <p>挂接：在 MOD 的 ModPlugin.pickShipAI(FleetMemberAPI, ShipAPI) 里对本船返回
 * new PluginPick&lt;&gt;(new GearInCruiseAI(ship), PickPriority.MOD_GENERAL)，其余船返回
 * null 继续使用默认 AI（舰船 AI / 战机 AI）。ModPlugin 的完整实装见 S06-P01、
 * S06-P02。玩家直接控制的舰船不建议挂自定义 AI：玩家输入优先。</p>
 *
 * <p>两个必须自己维护的点：</p>
 * <ul>
 *   <li>ShipwideAIFlags 的自动过期由 advance() 驱动（默认 AI 在自己的 advance() 里调用
 *       flags.advance(amount)），自定义 AI 必须每帧自己调用，
 *       否则 setFlag 的 flag 永不自动清除；</li>
 *   <li>指令只能通过 ShipAPI.giveCommand(...) 发出，该方法只应被 ShipAIPlugin 调用。</li>
 * </ul>
 */
public class GearInCruiseAI implements ShipAIPlugin {

    private final ShipAPI ship;
    private final ShipAIConfig config = new ShipAIConfig();
    private final ShipwideAIFlags flags = new ShipwideAIFlags();

    public GearInCruiseAI(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount) {
        flags.advance(amount);
        if (!ship.isAlive() || ship.isHulk()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
            return;
        }
        flags.setFlag(AIFlags.CUSTOM1);
        ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
    }

    @Override
    public void forceCircumstanceEvaluation() {
        // 本 AI 每帧评估，无需周期外的强制刷新
    }

    @Override
    public void setDoNotFireDelay(float amount) {
        // 本 AI 不涉及开火
    }

    @Override
    public boolean needsRefit() {
        // 仅战机联队成员查询；本 AI 用于普通舰船
        return false;
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void cancelCurrentManeuver() {
        // 本 AI 没有需要取消的机动
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
}
