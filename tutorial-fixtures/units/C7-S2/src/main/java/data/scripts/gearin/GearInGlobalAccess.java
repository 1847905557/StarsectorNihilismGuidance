package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class GearInGlobalAccess {

    private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

    private GearInGlobalAccess() {
    }

    public static boolean isInCombat() {
        return Global.getCurrentState() == GameState.COMBAT;
    }

    public static void logAccessorState() {
        GameState state = Global.getCurrentState();
        SectorAPI sector = Global.getSector();
        CombatEngineAPI engine = Global.getCombatEngine();
        LOG.info("GearIn state=" + state + " settings=" + (Global.getSettings() != null)
                + " sector=" + (sector != null) + " engine=" + (engine != null)
                + " engineIdentity=" + System.identityHashCode(engine)
                + " loadingSave=" + Global.LOADING_SAVE);
    }
}
