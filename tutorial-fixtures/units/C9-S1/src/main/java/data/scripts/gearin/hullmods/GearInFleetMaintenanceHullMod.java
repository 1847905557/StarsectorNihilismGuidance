package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** 舰队维护：降低本舰燃料消耗，并按安装数量提高舰队传感范围。 */
public class GearInFleetMaintenanceHullMod extends BaseHullMod implements HullModFleetEffect {

    public static final String HULLMOD_ID = "GearIn_fleet_maintenance";

    /** 舰队传感范围修饰的来源 ID。 */
    private static final String SENSOR_SOURCE_ID = "GearIn_fleet_maintenance_sensor";

    /** 本舰燃料消耗倍率。 */
    private static final float FUEL_USE_MULT = 0.9f;

    /** 每艘舰船提供的传感范围。 */
    private static final float SENSOR_BONUS_PER_SHIP = 50f;

    /** 最多计入的舰船数。 */
    private static final int MAX_COUNTED_SHIPS = 3;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFuelUseMod().modifyMult(id, FUEL_USE_MULT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return Math.round((1f - FUEL_USE_MULT) * 100f) + "%";
        }
        if (index == 1) {
            return "" + (int) SENSOR_BONUS_PER_SHIP;
        }
        if (index == 2) {
            return "" + MAX_COUNTED_SHIPS;
        }
        return null;
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return false;
    }

    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
    }

    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        int count = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isMothballed() && member.getVariant().hasHullMod(HULLMOD_ID)) {
                count++;
            }
        }
        count = Math.min(count, MAX_COUNTED_SHIPS);
        if (count <= 0) {
            fleet.getSensorRangeMod().unmodifyFlat(SENSOR_SOURCE_ID);
        } else {
            fleet.getSensorRangeMod().modifyFlat(SENSOR_SOURCE_ID, count * SENSOR_BONUS_PER_SHIP, "舰队维护");
        }
    }
}
