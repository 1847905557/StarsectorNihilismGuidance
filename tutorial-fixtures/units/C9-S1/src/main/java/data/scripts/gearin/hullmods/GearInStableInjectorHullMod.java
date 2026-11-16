package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/** 稳定化推进器：按船体级别提高最高航速；S 插后提高加速度；与不稳定喷射器双向互斥。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

    /** 原版不稳定喷射器的 id，用于互斥检查。 */
    private static final String UNSTABLE_INJECTOR = "unstable_injector";

    /** S 插后加速度与减速度的提高百分比。 */
    private static final float SMOD_ACCELERATION_BONUS = 25f;

    /** 船体级别与航速加成的对应表。 */
    private static final Map SPEED_BONUS = new HashMap();

    static {
        SPEED_BONUS.put(HullSize.FRIGATE, 15f);
        SPEED_BONUS.put(HullSize.DESTROYER, 10f);
        SPEED_BONUS.put(HullSize.CRUISER, 5f);
        SPEED_BONUS.put(HullSize.CAPITAL_SHIP, 5f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BONUS.get(hullSize));
        if (isSMod(stats)) {
            stats.getAcceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
            stats.getDeceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShipVariantAPI variant = ship.getVariant();
        if (variant.hasHullMod(UNSTABLE_INJECTOR)
                && !ship.getHullSpec().isBuiltInMod(UNSTABLE_INJECTOR)) {
            variant.removeMod(UNSTABLE_INJECTOR);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.FRIGATE)).intValue();
        }
        if (index == 1) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.DESTROYER)).intValue();
        }
        if (index == 2) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CRUISER)).intValue();
        }
        if (index == 3) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CAPITAL_SHIP)).intValue();
        }
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) SMOD_ACCELERATION_BONUS + "%";
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "不兼容 不稳定喷射器";
    }
}
