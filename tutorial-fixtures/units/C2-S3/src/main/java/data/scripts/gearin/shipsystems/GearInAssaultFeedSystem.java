package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

/** 突击填弹器：实弹射速与机动同时提升的主动系统。 */
public class GearInAssaultFeedSystem extends BaseShipSystemScript {

    public static final float ROF_BONUS = 1f;
    public static final float FLUX_REDUCTION = 50f;
    public static final float SPEED_BONUS = 50f;
    public static final float MANEUVER_BONUS = 200f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getBallisticRoFMult().modifyMult(id, 1f + ROF_BONUS * effectLevel);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - FLUX_REDUCTION * 0.01f);
        stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
        stats.getAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getDeceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, 15f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("+" + (int) (SPEED_BONUS * effectLevel) + " top speed", false);
        }
        if (index == 1) {
            return new StatusData("improved maneuverability", false);
        }
        if (index == 2) {
            return new StatusData("ballistic rate of fire +" + (int) (ROF_BONUS * effectLevel * 100f) + "%", false);
        }
        if (index == 3) {
            return new StatusData("ballistic flux use -" + (int) FLUX_REDUCTION + "%", false);
        }
        return null;
    }
}
