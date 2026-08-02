package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import org.lwjgl.util.vector.Vector2f;

/**
 * 光束效果：光束武器激活期间每帧被调用，每累计 1 秒打一行日志并在光束末端放一个青色光点。
 * 注册在光束武器 .wpn 的 beamEffect 键；REPORT_INTERVAL 与日志文字是参数。
 */
public class GearInBeamLogger implements BeamEffectPlugin {

    /** 每累计多少秒打一行日志（参数）。 */
    private static final float REPORT_INTERVAL = 1f;

    private float timeSinceLastReport;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            Global.getLogger(GearInBeamLogger.class).info("GearIn beam tick");
            engine.addHitParticle(beam.getRayEndPrevFrame(), new Vector2f(), 20f, 1f, 0.3f, Color.CYAN);
        }
    }
}
