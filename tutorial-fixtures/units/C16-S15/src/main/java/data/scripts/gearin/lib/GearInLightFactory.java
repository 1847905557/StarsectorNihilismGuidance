package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.base.BaseIlluminantData;
import org.boxutil.units.standard.light.AreaLight;
import org.boxutil.units.standard.light.InfiniteLight;
import org.boxutil.units.standard.light.LinearLight;
import org.boxutil.units.standard.light.PointLight;
import org.boxutil.units.standard.light.SpotLight;
import org.lwjgl.util.vector.Vector2f;

public final class GearInLightFactory {
    public BaseIlluminantData[] createFiveKinds(Vector2f location, float facing) {
        PointLight point = new PointLight();
        configure(point, location, new Color(90, 220, 255), 1.2f, 180f);
        AreaLight area = new AreaLight();
        area.setSize(180f, 70f);
        configure(area, location, new Color(80, 170, 255), 0.7f, 260f);
        LinearLight linear = new LinearLight();
        linear.setLength(240f);
        linear.setCylinderRadius(18f);
        configure(linear, location, new Color(100, 230, 255), 1f, 220f);
        SpotLight spot = new SpotLight();
        spot.setCone(18f);
        spot.setFacingScale(facing, 1f, 1f);
        configure(spot, location, new Color(180, 235, 255), 0.9f, 320f);
        InfiniteLight infinite = new InfiniteLight();
        infinite.setColor(new Color(90, 110, 150));
        infinite.setStrength(0.12f);
        infinite.setFacingScale(facing, 1f, 1f);
        infinite.setNoneAttenuation();
        infinite.setGlobalTimer(0.1f, 0.3f, 0.6f);
        return new BaseIlluminantData[] {point, linear, spot, area, infinite};
    }

    private void configure(BaseIlluminantData light, Vector2f location,
            Color color, float strength, float radius) {
        light.setLocation(location);
        light.setColor(color);
        light.setStrength(strength);
        light.setAttenuationRadius(radius);
        light.setGlobalTimer(0.1f, 0.3f, 0.6f);
    }
}
