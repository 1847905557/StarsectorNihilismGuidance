package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInResistFieldSystem extends BaseShipSystemScript {

    public static final float HE_REDUCTION = 75f;
    public static final float EMP_REDUCTION = 75f;
    public static final float OTHER_REDUCTION = 50f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getHighExplosiveDamageTakenMult().modifyMult(id, 1f - HE_REDUCTION * 0.01f * effectLevel);
        stats.getKineticDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
        stats.getEnergyDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
        stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_REDUCTION * 0.01f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getHighExplosiveDamageTakenMult().unmodify(id);
        stats.getKineticDamageTakenMult().unmodify(id);
        stats.getEnergyDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("reduce damage taken", false);
        }
        return null;
    }
}
