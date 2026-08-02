package data.scripts.gearin.combat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

public class GearInFrameLogger extends BaseEveryFrameCombatPlugin {

    private static final float TICK_INTERVAL = 3f;

    private float elapsed;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }
        elapsed += amount;
        if (elapsed >= TICK_INTERVAL) {
            elapsed = 0f;
            Global.getLogger(GearInFrameLogger.class).info(
                "GearIn FrameLogger tick at battle time " + engine.getTotalElapsedTime(false));
        }
    }
}
