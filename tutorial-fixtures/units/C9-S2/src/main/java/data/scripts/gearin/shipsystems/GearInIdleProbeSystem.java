package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class GearInIdleProbeSystem extends BaseShipSystemScript {

    private static final Logger LOG = Global.getLogger(GearInIdleProbeSystem.class);

    @Override
    public void apply(MutableShipStatsAPI stats, String id,
            State state, float effectLevel) {
        LOG.info("GearIn idle probe: state=" + state
                + " effectLevel=" + effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        LOG.info("GearIn idle probe: unapply");
    }

}
