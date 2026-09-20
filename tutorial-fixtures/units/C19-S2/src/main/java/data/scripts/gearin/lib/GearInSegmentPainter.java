package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.SegmentEntity;
import org.lwjgl.util.vector.Vector2f;

public final class GearInSegmentPainter {
    public SegmentEntity makeRangeRing(Vector2f center, float radius) {
        SegmentEntity ring = new SegmentEntity();
        ring.initCircle(center, radius, new Color(30, 210, 255, 220), Color.WHITE, 5f);
        ring.setInterpolation((short) 64);
        ring.setTextureSpeed(0.15f);
        ring.setFlowWhenPaused(false);
        ring.submitNodes();
        return ring;
    }
}
