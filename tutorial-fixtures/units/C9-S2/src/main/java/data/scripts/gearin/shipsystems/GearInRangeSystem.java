package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

/** 范围增幅：实弹武器射程提高，代价是转向变慢；效果同步到模块。 */
public class GearInRangeSystem extends BaseShipSystemScript {

    /** 满效果时的实弹射程加成，0.3 即 30%。 */
    public static final float RANGE_BONUS = 0.3f;

    /** 满效果时的转向减益，0.5 即 50%。 */
    public static final float TURN_PENALTY = 0.5f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        applyTo(stats, id, effectLevel);
        if (stats.getEntity() instanceof ShipAPI ship) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                applyTo(module.getMutableStats(), id, effectLevel);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        unapplyFrom(stats, id);
        if (stats.getEntity() instanceof ShipAPI ship) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                unapplyFrom(module.getMutableStats(), id);
            }
        }
    }

    private static void applyTo(MutableShipStatsAPI stats, String id, float effectLevel) {
        stats.getBallisticWeaponRangeBonus().modifyMult(id, 1f + RANGE_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
        stats.getTurnAcceleration().modifyMult(id, 1f - TURN_PENALTY * effectLevel);
    }

    private static void unapplyFrom(MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            int range = Math.round(RANGE_BONUS * effectLevel * 100f);
            return new StatusData("ballistic range +" + range + "%", false);
        }
        if (index == 1) {
            int turn = Math.round(TURN_PENALTY * effectLevel * 100f);
            return new StatusData("turn rate -" + turn + "%", true);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return hasRangeWeapon(ship);
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (!system.isActive() && !hasRangeWeapon(ship)) {
            return "NO BALLISTICS";
        }
        return null;
    }

    /** 能从本系统获益的武器：未永久损毁、不以点防御为主的实弹武器。 */
    public static boolean isRangeWeapon(WeaponAPI weapon) {
        return weapon.getType() == WeaponType.BALLISTIC
                && !weapon.isDecorative()
                && !weapon.isPermanentlyDisabled()
                && !weapon.hasAIHint(AIHints.PD);
    }

    private static boolean hasRangeWeapon(ShipAPI ship) {
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (isRangeWeapon(weapon)) {
                return true;
            }
        }
        return false;
    }
}
