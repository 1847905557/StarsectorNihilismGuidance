package data.scripts.gearin.hullmods;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInArmorTrainingHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInArmorTrainingHullMod.class);
    private static final float ARMOR_BONUS_PERCENT = 10f;
    private static final float SMOD_ADDITIONAL_PERCENT = 15f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize,
            MutableShipStatsAPI stats, String id) {
        float bonus = ARMOR_BONUS_PERCENT;
        if (isSMod(stats)) {
            bonus += SMOD_ADDITIONAL_PERCENT;
        }
        stats.getArmorBonus().modifyPercent(id, bonus);
        stats.getEffectiveArmorBonus().modifyPercent(id, bonus);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getVariant().hasHullMod("unstable_injector")) {
            ship.getVariant().removeMod("unstable_injector");
            LOG.info("GearIn armor training: removed incompatible unstable_injector");
        }
        LOG.info("GearIn armor training: ship created " + ship.getName());
    }

    @Override
    public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        engine.addFloatingText(ship.getLocation(),
                "GearIn armor training", 14f, Color.CYAN,
                ship, 0f, 1f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive() || ship.isHulk()) {
            return;
        }
        repairArmor(ship, amount * (isSMod(ship) ? 8f : 4f));
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter,
            ShipAPI ship, String id) {
        LOG.info("GearIn armor training: fighter spawned by " + ship.getName());
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().hasHullMod("unstable_injector");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getVariant().hasHullMod("unstable_injector")) {
            return "不兼容 不稳定推进器";
        }
        return null;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) ARMOR_BONUS_PERCENT + "%";
        }
        if (index == 1) {
            return "4";
        }
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) (ARMOR_BONUS_PERCENT + SMOD_ADDITIONAL_PERCENT) + "%";
        }
        if (index == 1) {
            return "8";
        }
        return null;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize,
            ShipAPI ship, boolean isForModSpec) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip,
            HullSize hullSize, ShipAPI ship, float width,
            boolean isForModSpec) {
        tooltip.addPara("S-mod 会把每个装甲格的修复速度提高到每秒 8 点。", 10f);
    }

    private static void repairArmor(ShipAPI ship, float amount) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float[][] values = grid.getGrid();
        float maxCell = grid.getMaxArmorInCell();
        for (int x = 0; x < values.length; x++) {
            for (int y = 0; y < values[x].length; y++) {
                float repaired = Math.min(maxCell, values[x][y] + amount);
                grid.setArmorValue(x, y, repaired);
            }
        }
        ship.syncWithArmorGridState();
    }
}
