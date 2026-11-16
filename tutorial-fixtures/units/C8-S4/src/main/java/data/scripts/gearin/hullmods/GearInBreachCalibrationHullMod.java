package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;

import org.lwjgl.util.vector.Vector2f;

/** 破甲校准：小型实弹武器命中装甲与船体时伤害提高，射程降低。 */
public class GearInBreachCalibrationHullMod extends BaseHullMod {

    public static final String HULLMOD_ID = "GearIn_breach_calibration";

    private static final float DAMAGE_BONUS = 15f;
    private static final float RANGE_PENALTY = 15f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.addListener(new BreachCalibrationListener());
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) DAMAGE_BONUS + "%";
        }
        if (index == 1) {
            return (int) RANGE_PENALTY + "%";
        }
        return null;
    }

    private static boolean isAffected(WeaponAPI weapon) {
        return weapon != null && weapon.getSize() == WeaponSize.SMALL
                && weapon.getType() == WeaponType.BALLISTIC;
    }

    /** 同时修改伤害与射程的监听器，每艘舰船一个实例。 */
    public static class BreachCalibrationListener implements DamageDealtModifier, WeaponRangeModifier {

        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                Vector2f point, boolean shieldHit) {
            if (shieldHit) {
                return null;
            }
            WeaponAPI weapon = null;
            if (param instanceof DamagingProjectileAPI) {
                weapon = ((DamagingProjectileAPI) param).getWeapon();
            } else if (param instanceof BeamAPI) {
                weapon = ((BeamAPI) param).getWeapon();
            }
            if (!isAffected(weapon)) {
                return null;
            }
            damage.getModifier().modifyPercent(HULLMOD_ID, DAMAGE_BONUS);
            return HULLMOD_ID;
        }

        @Override
        public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f;
        }

        @Override
        public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return isAffected(weapon) ? 1f - RANGE_PENALTY / 100f : 1f;
        }

        @Override
        public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f;
        }
    }
}
