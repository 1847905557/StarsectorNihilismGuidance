package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import org.lwjgl.util.vector.Vector2f;

/** 光束激活期间计时，每秒记录日志并创建末端粒子。 */
public class GearInBeamLogger implements BeamEffectPlugin {

    /** 两次报告之间的秒数。 */
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
