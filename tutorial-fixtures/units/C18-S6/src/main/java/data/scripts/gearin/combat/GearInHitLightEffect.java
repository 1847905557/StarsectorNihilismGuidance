package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Creates a finite GraphicsLib point light for each projectile hit.
 * Shield hits fade at the world-space impact point; hull hits follow the
 * target at the original impact offset.
 */
public class GearInHitLightEffect implements OnHitEffectPlugin {
    private static final Color SHIELD_COLOR = new Color(120, 200, 255);
    private static final Color HULL_COLOR = new Color(255, 120, 70);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        StandardLight light = new StandardLight();
        light.setHeight(100f);
        light.setSpecularMult(0.8f);

        if (shieldHit) {
            light.setLocation(point);
            light.setColor(SHIELD_COLOR);
            light.setSize(220f);
            light.setIntensity(0.8f);
            light.fadeOut(0.3f);
        } else {
            Vector2f localOffset = Vector2f.sub(point, target.getLocation(), null);
            VectorUtils.rotate(localOffset, -target.getFacing());

            light.attachTo(target);
            light.setOffset(localOffset);
            light.setColor(HULL_COLOR);
            light.setSize(280f);
            light.setIntensity(1f);
            light.fadeIn(0.06f);
            light.setLifetime(0.08f);
            light.setAutoFadeOutTime(0.45f);
        }

        LightShader.addLight(light);
    }
}
