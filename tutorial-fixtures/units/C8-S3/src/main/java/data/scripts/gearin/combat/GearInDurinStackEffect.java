package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 都林的叠层：连续命中同一目标时逐层提高伤害，超时或更换目标后归零。 */
public class GearInDurinStackEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private static final int MAX_STACKS = 5;
    private static final float DAMAGE_PER_STACK = 0.1f;
    private static final float RESET_SECONDS = 2f;

    private ShipAPI target = null;
    private int stacks = 0;
    private float sinceLastHit = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || stacks == 0) {
            return;
        }
        sinceLastHit += amount;
        if (sinceLastHit > RESET_SECONDS) {
            target = null;
            stacks = 0;
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (stacks > 0) {
            projectile.setDamageAmount(projectile.getDamageAmount() * (1f + stacks * DAMAGE_PER_STACK));
        }
    }

    /** 由命中效果在命中船体后调用，更新本武器的层数。 */
    public void registerHit(ShipAPI hit) {
        if (hit == target) {
            stacks = Math.min(MAX_STACKS, stacks + 1);
        } else {
            target = hit;
            stacks = 1;
        }
        sinceLastHit = 0f;
    }
}
