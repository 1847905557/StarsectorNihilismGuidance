package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInStatsHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInStatsHullMod.class);
    private static final String SPEED_ID = "GearIn_stats_hullmod_speed";
    private static final String DYNAMIC_ID = "GearIn_stats_hullmod_dynamic";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(SPEED_ID, 10f);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(DYNAMIC_ID, 1f);
        LOG.info("GearIn hullmod stats: speed=" + stats.getMaxSpeed().getModifiedValue()
                + " dynamicMaxHullmods="
                + stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).computeEffective(2f));
    }
}
