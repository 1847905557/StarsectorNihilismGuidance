package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import data.scripts.gearin.GearInSettings;

/** 突击填弹器：实弹射速与机动同时提升的主动系统，数值来自 settings.json。 */
public class GearInAssaultFeedSystem extends BaseShipSystemScript {

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        float rofBonus = GearInSettings.getAssaultFeedRofBonus();
        float fluxReduction = GearInSettings.getAssaultFeedFluxReduction();
        float speedBonus = GearInSettings.getAssaultFeedSpeedBonus();
        float maneuverBonus = GearInSettings.getAssaultFeedManeuverBonus();

        stats.getBallisticRoFMult().modifyMult(id, 1f + rofBonus * effectLevel);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - fluxReduction * 0.01f);
        stats.getMaxSpeed().modifyFlat(id, speedBonus * effectLevel);
        stats.getAcceleration().modifyPercent(id, maneuverBonus * effectLevel);
        stats.getDeceleration().modifyPercent(id, maneuverBonus * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, maneuverBonus * effectLevel);
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
            int speed = (int) (GearInSettings.getAssaultFeedSpeedBonus() * effectLevel);
            return new StatusData("+" + speed + " top speed", false);
        }
        if (index == 1) {
            return new StatusData("improved maneuverability", false);
        }
        if (index == 2) {
            int rof = (int) (GearInSettings.getAssaultFeedRofBonus() * effectLevel * 100f);
            return new StatusData("ballistic rate of fire +" + rof + "%", false);
        }
        if (index == 3) {
            int flux = (int) GearInSettings.getAssaultFeedFluxReduction();
            return new StatusData("ballistic flux use -" + flux + "%", false);
        }
        return null;
    }
}
