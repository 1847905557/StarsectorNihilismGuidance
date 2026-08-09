package data.scripts.gearin.lib;

import java.util.List;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.EllipseUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEntityAPI;

public final class GearInSafeJumpPlanner {
    private static final int CANDIDATE_COUNT = 12;

    public GearInJumpCandidate find(Vector2f origin, float facing,
            List<CombatEntityAPI> blockers) {
        GearInJumpCandidate best = null;
        for (int i = 0; i < CANDIDATE_COUNT; i++) {
            float angle = -90f + i * 180f / (CANDIDATE_COUNT - 1f);
            Vector2f point = EllipseUtils.getPointOnEllipse(origin, 700f, 420f, facing, angle);
            if (crossesBlocker(origin, point, blockers)) {
                continue;
            }
            float score = MathUtils.getDistance(origin, point)
                    - Math.abs(MathUtils.getShortestRotation(facing, angle)) * 2f;
            if (best == null || score > best.getScore()) {
                best = new GearInJumpCandidate(point, score);
            }
        }
        return best;
    }

    private boolean crossesBlocker(Vector2f start, Vector2f end,
            List<CombatEntityAPI> blockers) {
        for (CombatEntityAPI blocker : blockers) {
            if (CollisionUtils.getCollisionPoint(start, end, blocker) != null) {
                return true;
            }
        }
        return false;
    }
}
