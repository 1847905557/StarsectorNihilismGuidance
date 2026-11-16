package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 过热冶炉：实弹武器幅能消耗固定提高，射速随幅能升高。 */
public class GearInOverheatForgeHullMod extends BaseHullMod {

    public static final String HULLMOD_ID = "GearIn_overheat_forge";
    private static final String SOURCE_ID = "GearIn_overheat_forge_rof";
    private static final String STATUS_KEY = "GearIn_overheat_forge_status";

    private static final float FLUX_COST_PENALTY = 10f;
    private static final float MAX_ROF_BONUS = 30f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponFluxCostMod().modifyPercent(id, FLUX_COST_PENALTY);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive()) {
            return;
        }
        float bonus = MAX_ROF_BONUS * ship.getFluxLevel();
        ship.getMutableStats().getBallisticRoFMult().modifyMult(SOURCE_ID, 1f + bonus / 100f);

        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == engine.getPlayerShip()) {
            String icon = Global.getSettings().getHullModSpec(HULLMOD_ID).getSpriteName();
            engine.maintainStatusForPlayerShip(STATUS_KEY, icon, "Overheat forge",
                    "ballistic rate of fire +" + (int) bonus + "%", false);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) FLUX_COST_PENALTY + "%";
        }
        if (index == 1) {
            return (int) MAX_ROF_BONUS + "%";
        }
        return null;
    }
}
