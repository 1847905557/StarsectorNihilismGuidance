package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.TrailEntity;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineLayers;

public final class GearInBoxTrailEffect {
    public TrailEntity make(Vector2f first, Vector2f second) {
        TrailEntity trail = new TrailEntity();
        trail.setStartWidth(28f);
        trail.setEndWidth(2f);
        trail.setStartColor(new Color(80, 230, 255, 220));
        trail.setEndColor(new Color(20, 80, 255, 0));
        trail.addNode(first);
        trail.addNode(second);
        trail.setFlowWhenPaused(false);
        trail.setGlobalTimer(0.05f, 0.15f, 0.8f);
        trail.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        trail.submitNodes();
        return trail;
    }
}
