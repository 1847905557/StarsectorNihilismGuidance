package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 为舰船提供固定百分比的最大速度加成。 */
public class GearInSpeedBooster extends BaseHullMod {

    /** 最大速度加成百分比。 */
    private static final float SPEED_PERCENT = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
        Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
    }
}
