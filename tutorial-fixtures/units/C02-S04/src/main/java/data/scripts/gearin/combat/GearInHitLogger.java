package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/**
 * 射弹命中效果：命中时打一行日志，并在命中点放一个橙色光点。
 * 注册在弹体文件的 onHitEffect 键；方法签名是模板，只有日志文字是参数。
 */
public class GearInHitLogger implements OnHitEffectPlugin {

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Global.getLogger(GearInHitLogger.class).info("GearIn hit! shieldHit=" + shieldHit);
        engine.addHitParticle(point, new Vector2f(), 30f, 1f, 0.4f, Color.ORANGE);
    }
}
