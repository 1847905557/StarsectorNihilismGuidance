package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 稳定化推进器：按船体级别提高最高航速。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

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
        // 按当前船体级别查表，把结果加到最高航速上。
        stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BONUS.get(hullSize));
    }
}
