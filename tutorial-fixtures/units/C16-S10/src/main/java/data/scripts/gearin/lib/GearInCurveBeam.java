package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.CurveEntity;
import org.lwjgl.util.vector.Vector2f;

public final class GearInCurveBeam {
    public CurveEntity make(Vector2f start, float length) {
        CurveEntity beam = new CurveEntity();
        beam.initSmoothLine(start, length, Color.CYAN, Color.WHITE, 9f, 0.35f);
        beam.setTextureSpeed(0.4f);
        beam.setFlowWhenPaused(false);
        beam.submitNodes();
        return beam;
    }
}
