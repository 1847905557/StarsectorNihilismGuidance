package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class GearInRangeSystem extends BaseShipSystemScript {

    public static final float RANGE_MULT_BONUS = 0.5f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id,
            State state, float effectLevel) {
        stats.getEnergyWeaponRangeBonus().modifyMult(id,
                1f + RANGE_MULT_BONUS * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getEnergyWeaponRangeBonus().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state == State.ACTIVE) {
            return new StatusData("range amplified", false);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship != null && ship.isAlive()
                && !system.isActive() && !system.isCoolingDown();
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isCoolingDown()) {
            return "RECHARGING";
        }
        return null;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        return 6f;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        return -1;
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        return state == State.ACTIVE ? "Range Amplified" : null;
    }
}
