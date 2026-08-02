package data.scripts.gearin.combat;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 * 命中回调（.proj onHitEffect 注册列，每射弹一个实例）。
 * 射弹命中结算时回调一次，参数是本次命中结算结果，
 * 禁止缓存跨命中使用。本类只观察并打日志，不修改任何游戏数值。
 */
public class GearInOnHitEffect implements OnHitEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInOnHitEffect.class);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        LOG.info("GearIn onHit " + projectile.getProjectileSpecId()
                + " target=" + target.getClass().getSimpleName()
                + " shieldHit=" + shieldHit
                + " hull=" + damageResult.getDamageToHull()
                + " armor=" + damageResult.getTotalDamageToArmor()
                + " shields=" + damageResult.getDamageToShields()
                + " emp=" + damageResult.getEmpDamage());
    }
}
