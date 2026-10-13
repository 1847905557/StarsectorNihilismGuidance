package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 记录本次生成弹体的规格、来源与伤害。 */
public class GearInShotOnFireEffect implements OnFireEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInShotOnFireEffect.class);

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        LOG.info("GearIn onFire " + projectile.getProjectileSpecId()
                + " weapon=" + (weapon == null ? "null" : weapon.getId())
                + " source=" + (projectile.getSource() == null ? "null" : projectile.getSource().getHullSpec().getHullId())
                + " spawnType=" + projectile.getSpawnType()
                + " damage=" + projectile.getDamageAmount()
                + " emp=" + projectile.getEmpAmount());
    }
}
