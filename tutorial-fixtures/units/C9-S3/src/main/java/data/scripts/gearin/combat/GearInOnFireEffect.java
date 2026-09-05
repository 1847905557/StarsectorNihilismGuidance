package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 开火回调 (.proj onFireEffect 注册列，每武器一个实例)。
 * 武器开火生成射弹后回调一次，参数是刚生成的那一颗射弹，
 * 禁止缓存跨发射使用。本类只观察并打日志，不修改任何游戏数值。
 */
public class GearInOnFireEffect implements OnFireEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInOnFireEffect.class);

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        // 参数是 "本发射弹" 上下文：射弹刚生成，速度 / 朝向已就绪。
        LOG.info("GearIn onFire " + projectile.getProjectileSpecId()
                + " weapon=" + (weapon == null ? "null" : weapon.getId())
                + " spawnType=" + projectile.getSpawnType()
                + " speed=" + projectile.getVelocity().length());
    }
}
