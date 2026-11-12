package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;

/** 乌里满亮度照射舰船船体时，每隔一段时间放出 EMP 电弧。 */
public class GearInUriBeamEffect implements BeamEffectPlugin {

    /** 两次电弧之间的秒数。 */
    private static final float ARC_INTERVAL = 0.5f;

    private static final float ARC_DAMAGE = 40f;
    private static final float ARC_EMP = 200f;

    /** 距离上一次电弧累计的秒数，在多次调用之间保留。 */
    private float elapsed = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        // 照射对象不是舰船，或光束未达到满亮度时，不计时。
        if (!(target instanceof ShipAPI) || beam.getBrightness() < 1f) {
            return;
        }
        Vector2f point = beam.getRayEndPrevFrame();
        // 光束末端落在护盾范围内，说明照的是护盾，同样不计时。
        if (target.getShield() != null && target.getShield().isWithinArc(point)) {
            return;
        }
        elapsed += amount;
        if (elapsed < ARC_INTERVAL) {
            return;
        }
        elapsed = 0f;
        engine.spawnEmpArc(beam.getSource(), point, target, target, DamageType.ENERGY, ARC_DAMAGE, ARC_EMP,
                100000f, "tachyon_lance_emp_impact", beam.getWidth() + 9f, beam.getFringeColor(),
                beam.getCoreColor());
    }
}
