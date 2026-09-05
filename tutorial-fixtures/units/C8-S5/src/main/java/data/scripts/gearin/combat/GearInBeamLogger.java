package data.scripts.gearin.combat;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class GearInBeamLogger implements BeamEffectPlugin {

    private static final Logger LOG = Global.getLogger(GearInBeamLogger.class);
    private static final float REPORT_INTERVAL = 1f;

    private float timeSinceLastReport;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        timeSinceLastReport += amount;
        if (timeSinceLastReport < REPORT_INTERVAL) {
            return;
        }
        timeSinceLastReport = 0f;
        String target = beam.getDamageTarget() == null
                ? "null"
                : beam.getDamageTarget().getClass().getSimpleName();
        LOG.info("GearIn beam tick target=" + target
                + " didDamageThisFrame=" + beam.didDamageThisFrame()
                + " length=" + beam.getLength());
        engine.addHitParticle(beam.getRayEndPrevFrame(), new Vector2f(),
                20f, 1f, 0.3f, Color.CYAN);
    }
}
