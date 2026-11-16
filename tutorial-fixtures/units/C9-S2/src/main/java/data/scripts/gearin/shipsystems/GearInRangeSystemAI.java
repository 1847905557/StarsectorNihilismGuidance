package data.scripts.gearin.shipsystems;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** 范围增幅的 AI：目标刚好在实弹武器射程之外、增幅后能够到时才使用。 */
public class GearInRangeSystemAI implements ShipSystemAIScript {

    /** 幅能高于这个比例时不使用，舰船很快就要后撤或散幅。 */
    private static final float MAX_FLUX_LEVEL = 0.85f;

    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.35f);

    private ShipAPI ship;
    private ShipSystemAPI system;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        interval.randomize();
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        if (system.isActive() || system.getCooldownRemaining() > 0f || system.isOutOfAmmo()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting() || ship.getFluxLevel() > MAX_FLUX_LEVEL) {
            return;
        }
        if (target == null || !target.isAlive() || target.isHulk()) {
            return;
        }
        if (countWeaponsBroughtIntoRange(target) > 0) {
            ship.useSystem();
        }
    }

    /** 统计现在够不到目标、增幅后能够到的武器数量。 */
    private int countWeaponsBroughtIntoRange(ShipAPI target) {
        int count = 0;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!GearInRangeSystem.isRangeWeapon(weapon)) {
                continue;
            }
            float distance = Misc.getDistance(weapon.getLocation(), target.getLocation())
                    - Misc.getTargetingRadius(weapon.getLocation(), target, false);
            float range = weapon.getRange();
            if (distance > range && distance <= range * (1f + GearInRangeSystem.RANGE_BONUS)) {
                count++;
            }
        }
        return count;
    }
}
