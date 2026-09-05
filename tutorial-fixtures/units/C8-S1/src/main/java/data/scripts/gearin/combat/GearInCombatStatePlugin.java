package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗引擎状态读取 —— 上下文、时间、暂停与战斗结果。
 *
 * 本插件由战斗上下文注册，只在本场战斗内有效：
 * - 状态读取只使用当前战斗的引擎引用；
 * - advance() 在暂停时仍可能被调用，状态推进前必须检查 engine.isPaused()；
 * - getWinningSideId() 只表示胜者侧，战斗结束判定使用 isCombatOver()。
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
            return;
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logEngineTime();
            logContext();
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

    /** 记录适合决定逻辑分支的引擎状态。 */
    private void logContext() {
        LOG.info("GearIn context: campaign=" + engine.isInCampaign()
                + " simulation=" + engine.isSimulation()
                + " mission=" + engine.isMission()
                + " missionId=" + engine.getMissionId()
                + " paused=" + engine.isPaused()
                + " dialog=" + engine.isUIShowingDialog()
                + " hud=" + engine.isUIShowingHUD()
                + " fastTime=" + engine.isInFastTimeAdvance()
                + " combatOver=" + engine.isCombatOver()
                + " winner=" + engine.getWinningSideId());
    }
}
