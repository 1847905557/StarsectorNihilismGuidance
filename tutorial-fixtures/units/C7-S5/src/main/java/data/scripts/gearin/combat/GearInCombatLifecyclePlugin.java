package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗插件与引擎级监听器的生命周期、移除与状态归属。
 *
 * 生命周期事实：
 * - init() 已废弃：不保证在 advance() 前被调用（CombatEnginePlugin 的 API 注释），
 *   advance() 必须先判 engine == null；
 * - advance() 在暂停时仍会被调用，状态推进前必须先检查 engine.isPaused()（C2-S1）；
 * - 战斗结束没有回调：插件必须在 advance() 里用 engine.isCombatOver() 自行识别收尾时机，
 *   此时引擎仍存活（结束淡出尚未完成，C7-S1）；
 * - 引擎级监听器不随插件移除而注销：插件调用 engine.removePlugin(this) 自移除时，
 *   必须同时把注册到 engine.getListenerManager() 的监听器 removeListener 掉；
 * - 引擎、插件、监听器与舰船引用禁止跨战斗缓存（C6-S3）：本类所有状态
 *   都只属于当前这场战斗，战斗结束由引擎销毁统一释放。
 */
public class GearInCombatLifecyclePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInCombatLifecyclePlugin.class);

    /** 引擎引用。init() 已废弃，advance() 中必须判 null。 */
    private CombatEngineAPI engine;

    /** 本插件在 init() 里注册的引擎级监听器；自移除时必须显式移除。 */
    private final GearInDeploymentListener deploymentListener = new GearInDeploymentListener();

    private boolean finalized = false;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        CombatListenerManagerAPI listeners = engine.getListenerManager();
        if (!listeners.hasListenerOfClass(GearInDeploymentListener.class)) {
            listeners.addListener(deploymentListener);
            LOG.info("GearIn lifecycle init: deployment listener registered");
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // init() 不保证先于 advance() 执行，必须判 null
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（C2-S1）
        }
        if (finalized) {
            return;
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            LOG.info("GearIn lifecycle: total=" + engine.getTotalElapsedTime(false)
                    + " combatOver=" + engine.isCombatOver());
        }
        if (engine.isCombatOver()) {
            finalizeBattle();
        }
    }

    /** 收尾：先移除自己注册的引擎级监听器，再移除插件自身。 */
    private void finalizeBattle() {
        engine.getListenerManager().removeListener(deploymentListener);
        engine.removePlugin(this);
        finalized = true;
        LOG.info("GearIn lifecycle finalize: deployments=" + deploymentListener.deployedCount
                + " listener removed, plugin removed");
    }
}
