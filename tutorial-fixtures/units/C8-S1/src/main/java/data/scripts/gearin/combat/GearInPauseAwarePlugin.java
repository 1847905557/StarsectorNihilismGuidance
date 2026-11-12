package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 比较非暂停时间、总回调次数与暂停回调次数。 */
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {

    /** 非暂停时间每累计多少秒输出一次报告。 */
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        calls++;
        if (engine == null) {
            engine = Global.getCombatEngine();
        }
        if (engine.isPaused()) {
            pausedCalls++;
            return;
        }
        elapsed += amount;
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            report(engine);
        }
    }

    private void report(CombatEngineAPI engine) {
        String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
                + " (paused " + pausedCalls + ")";
        Global.getLogger(GearInPauseAwarePlugin.class).info(text);
        if (engine.getPlayerShip() != null) {
            engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
                    14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
        }
    }
}
