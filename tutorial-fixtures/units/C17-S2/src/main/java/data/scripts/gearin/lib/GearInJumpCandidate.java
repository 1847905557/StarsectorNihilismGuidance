package data.scripts.gearin.lib;

import org.lwjgl.util.vector.Vector2f;

public final class GearInJumpCandidate {
    private final Vector2f point;
    private final float score;

    public GearInJumpCandidate(Vector2f point, float score) {
        this.point = new Vector2f(point);
        this.score = score;
    }

    public Vector2f getPoint() {
        return new Vector2f(point);
    }

    public float getScore() {
        return score;
    }
}
