package data.scripts.gearin.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 战斗插件观察者：由战斗上下文 addPlugin 注册（C07-S01 模型）。
 * init() 把伤害监听器接入引擎，每帧识别新生成的射弹，并按 3 秒节奏
 * 触发武器 / 射弹 / 导弹 / 光束的运行态报告（实现见 GearInStateReport）。
 * 暂停守卫：暂停时武器 / 射弹 / 光束都不推进，只有插件 advance 仍被调用（C07-S01）。
 */
public class GearInFlowObserverPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInFlowObserverPlugin.class);

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(3f, 3f);
    /** 已见过的射弹（specId + 实体身份码），用于逐帧识别"新生成"的射弹。 */
    private final Set<String> seenProjectiles = new HashSet<>();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        engine.getListenerManager().addListener(new GearInDamageObserverListener());
        LOG.info("GearIn flow init: damage listeners registered on engine");
        GearInStateReport.logWeapons(engine);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return;
        }
        trackNewProjectiles();
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            GearInStateReport.logWeapons(engine);
            GearInStateReport.logProjectiles(engine);
            GearInStateReport.logBeams(engine);
        }
    }

    /** 逐帧识别新生成的射弹（含导弹与光束脉冲），打印生成时刻的运行态。 */
    private void trackNewProjectiles() {
        for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
            if (seenProjectiles.add(keyOf(projectile))) {
                LOG.info("GearIn spawn " + projectile.getProjectileSpecId()
                        + " spawnType=" + projectile.getSpawnType()
                        + " fromMissile=" + projectile.isFromMissile()
                        + " weapon=" + (projectile.getWeapon() == null ? "null" : projectile.getWeapon().getId())
                        + " source=" + (projectile.getSource() == null ? "null" : projectile.getSource().getHullSpec().getHullId())
                        + " damage=" + projectile.getDamageAmount()
                        + " emp=" + projectile.getEmpAmount());
            }
        }
    }

    private static String keyOf(DamagingProjectileAPI projectile) {
        return projectile.getProjectileSpecId() + "@" + System.identityHashCode(projectile);
    }
}
