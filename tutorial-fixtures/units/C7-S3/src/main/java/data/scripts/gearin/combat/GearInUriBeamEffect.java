package data.scripts.gearin.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;

/** 乌里满亮度照射船体时累计充能，每满一个间隔放出 EMP 电弧，充能声随进度升高。 */
public class GearInUriBeamEffect implements BeamEffectPlugin {

    private static final float ARC_INTERVAL = 0.5f;
    private static final float ARC_DAMAGE = 40f;
    private static final float ARC_EMP = 200f;

    private float elapsed = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (!(target instanceof ShipAPI) || beam.getBrightness() < 1f) {
            return;
        }
        Vector2f point = beam.getRayEndPrevFrame();
        if (target.getShield() != null && target.getShield().isWithinArc(point)) {
            return;
        }
        elapsed += amount;

        float progress = Math.min(1f, elapsed / ARC_INTERVAL);
        Global.getSoundPlayer().playLoop("GearIn_uri_charge", beam, 0.8f + 0.6f * progress, 1f,
                point, target.getVelocity());

        if (elapsed < ARC_INTERVAL) {
            return;
        }
        elapsed = 0f;
        engine.spawnEmpArc(beam.getSource(), point, target, target, DamageType.ENERGY, ARC_DAMAGE, ARC_EMP,
                100000f, "tachyon_lance_emp_impact", beam.getWidth() + 9f, beam.getFringeColor(),
                beam.getCoreColor());
    }
}
