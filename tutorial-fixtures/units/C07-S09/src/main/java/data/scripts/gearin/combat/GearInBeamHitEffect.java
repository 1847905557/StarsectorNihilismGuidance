package data.scripts.gearin.combat;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.BeamEffectPluginWithReset;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 光束效果（.wpn beamEffect 注册列，仅光束武器；每条光束射线一个实例）。
 * 射线创建与销毁时调 reset()（实现 BeamEffectPluginWithReset 时），开火 / 充能
 * 期间每帧回调 advance。本类只消费"本帧"光束命中状态并生成纯视觉命中粒子，
 * 不修改任何游戏数值。
 */
public class GearInBeamHitEffect implements BeamEffectPlugin, BeamEffectPluginWithReset {

    private static final Logger LOG = Global.getLogger(GearInBeamHitEffect.class);

    /** 命中粒子节流器（光束 0.1 秒结算一次，逐帧撒粒子会过密）。 */
    private final IntervalUtil sparkInterval = new IntervalUtil(0.15f, 0.15f);
    /** 上一帧命中状态（仅用于命中边沿日志，不跨实例复用）。 */
    private boolean lastDidDamage = false;

    @Override
    public void reset() {
        // 射线创建与销毁各调一次（光束射线构造器 / 光束武器.destroyBeamRays）：
        // 跨射线状态在这里复位。
        lastDidDamage = false;
        LOG.info("GearIn beam fx reset (ray created or destroyed)");
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // 每帧只消费本帧光束状态（规则 2：didDamageThisFrame / getDamageTarget
        // 只对本帧结算语义有效，禁止缓存跨帧使用）。
        boolean didDamage = beam.didDamageThisFrame();
        CombatEntityAPI target = beam.getDamageTarget();
        if (didDamage && !lastDidDamage) {
            LOG.info("GearIn beam fx hit start:"
                    + " target=" + (target == null ? "null" : target.getClass().getSimpleName())
                    + " brightness=" + beam.getBrightness());
        }
        if (didDamage) {
            // 纯视觉命中火花：以本帧射线端点（上一帧端点）为基准撒粒子，不造成伤害。
            // 光束伤害按 0.1 秒间隔结算（C07-S04）；IonBeamEffect 的替代写法是用
            // beam.getDamage().getDpsDuration() 推进节流器，让粒子节奏跟随真实结算。
            sparkInterval.advance(amount);
            if (sparkInterval.intervalElapsed()) {
                Vector2f point = beam.getRayEndPrevFrame();
                engine.addHitParticle(point, new Vector2f(0f, 0f),
                        6f, 1f, 0.4f, new Color(130, 200, 255, 200));
            }
        }
        lastDidDamage = didDamage;
    }
}
