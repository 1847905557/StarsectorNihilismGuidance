package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.DistortionEntity;
import org.boxutil.units.standard.entity.FlareEntity;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineLayers;

public final class GearInImpactSpecialFX {
    public DistortionEntity makeDistortion(Vector2f location) {
        DistortionEntity distortion = new DistortionEntity();
        distortion.setLocation(location);
        distortion.setSizeIn(20f, 20f);
        distortion.setSizeFull(120f, 120f);
        distortion.setSizeOut(180f, 180f);
        distortion.setPowerIn(0f);
        distortion.setPowerFull(0.8f);
        distortion.setPowerOut(0f);
        distortion.setInnerFull(0.1f, 0.1f);
        distortion.setRingHardness(0.75f);
        distortion.setGlobalTimer(0.05f, 0.1f, 0.55f);
        return distortion;
    }

    public FlareEntity makeFlare(Vector2f location, float facing) {
        FlareEntity flare = new FlareEntity();
        flare.setLocation(location);
        flare.setFacingScale(facing, 1f, 1f);
        flare.setSize(180f, 28f);
        flare.setCoreColor(Color.WHITE);
        flare.setFringeColor(new Color(70, 200, 255, 180));
        flare.setSmooth();
        flare.setAdditiveBlend();
        flare.setGlobalTimer(0.05f, 0.05f, 0.3f);
        flare.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        return flare;
    }
}
