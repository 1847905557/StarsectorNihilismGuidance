package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class GearInFleetMaintenanceHullMod extends BaseHullMod
        implements HullModFleetEffect {

    public static final String ID = "GearIn_fleet_maintenance";
    private static final String SENSOR_MOD_ID = "GearIn_fleet_maintenance_sensor";
    private static final float FUEL_USE_MULT = 0.9f;
    private static final float SENSOR_RANGE_BONUS = 50f;
    private static final Logger LOG = Global.getLogger(GearInFleetMaintenanceHullMod.class);

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize,
            MutableShipStatsAPI stats, String id) {
        stats.getFuelUseMod().modifyMult(id, FUEL_USE_MULT);
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (amount <= 0f) {
            LOG.info("GearIn fleet maintenance sync: " + member.getShipName());
        }
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return false;
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
    }

    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        int count = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(ID)) {
                count++;
            }
        }
        if (count == 0) {
            fleet.getStats().getSensorRangeMod().unmodify(SENSOR_MOD_ID);
            return;
        }
        fleet.getStats().getSensorRangeMod().modifyFlat(SENSOR_MOD_ID,
                count * SENSOR_RANGE_BONUS);
    }
}
