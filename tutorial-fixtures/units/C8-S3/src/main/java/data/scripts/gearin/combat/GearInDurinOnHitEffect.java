package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 都林命中船体时通知武器叠层，并按概率追加一次带音效的高爆爆炸。 */
public class GearInDurinOnHitEffect implements OnHitEffectPlugin {

    private static final float CHANCE = 0.25f;
    private static final float BONUS_DAMAGE = 20f;
    private static final Color EXPLOSION_COLOR = new Color(255, 120, 60, 255);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        WeaponAPI weapon = projectile.getWeapon();
        if (!shieldHit && target instanceof ShipAPI && weapon != null
                && weapon.getEffectPlugin() instanceof GearInDurinStackEffect) {
            ((GearInDurinStackEffect) weapon.getEffectPlugin()).registerHit((ShipAPI) target);
        }
        if (shieldHit || !(target instanceof ShipAPI) || Math.random() > CHANCE) {
            return;
        }
        engine.spawnExplosion(point, new Vector2f(), EXPLOSION_COLOR, 40f, 0.3f);
        Global.getSoundPlayer().playSound("GearIn_durin_burst", 1f, 1f, point, target.getVelocity());
        engine.applyDamage(target, point, BONUS_DAMAGE, DamageType.HIGH_EXPLOSIVE, 0f, false, false,
                projectile.getSource());
    }
}
