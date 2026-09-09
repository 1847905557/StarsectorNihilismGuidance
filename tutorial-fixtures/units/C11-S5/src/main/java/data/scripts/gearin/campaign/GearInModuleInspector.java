package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 读取父装配的模块关系、来源和 JSON 序列化结果。
 */
public class GearInModuleInspector implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_ModuleInspector";

    private static final Logger LOG = Global.getLogger(GearInModuleInspector.class);

    private static final String VARIANT_ID = "wolf_Assault";

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                VARIANT_ID);
        ShipVariantAPI variant = member.getVariant();

        LOG.info("GearIn module inspector: variant="
                + variant.getHullVariantId()
                + " source=" + variant.getSource()
                + " moduleSlots=" + variant.getModuleSlots().size()
                + " stationModules=" + variant.getStationModules().size());

        for (String slotId : variant.getModuleSlots()) {
            ShipVariantAPI module = variant.getModuleVariant(slotId);
            LOG.info("GearIn module: slot=" + slotId
                    + " variant=" + (module == null ? "null" : module.getHullVariantId())
                    + " source=" + (module == null ? "null" : module.getSource()));
        }

        try {
            JSONObject json = variant.toJSONObject();
            LOG.info("GearIn module JSON: length=" + json.toString().length()
                    + " hasWeaponGroups=" + json.has("weaponGroups")
                    + " hasModules=" + json.has("modules"));
        } catch (JSONException ex) {
            LOG.error("GearIn module JSON failed", ex);
        }
    }
}
