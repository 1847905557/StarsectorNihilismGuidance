package data.scripts.gearin.lib;

import java.awt.Color;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.EllipseUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Guides one missile toward the launching ship's selected target.
 * LazyLib supplies the lead, turn, prediction, collision, and debug geometry.
 */
public final class GearInGeometryMissileAI implements MissileAIPlugin, GuidedMissileAI {
    private static final boolean DEBUG = true;
    private static final float MAX_LEAD_TIME = 2f;
    private static final float PREDICTION_LENGTH = 900f;
    private static final Vector2f ZERO = new Vector2f();

    private static final Color LINE_COLOR = new Color(80, 220, 255, 220);
    private static final Color HIT_COLOR = new Color(255, 140, 60, 240);
    private static final Color CIRCLE_COLOR = new Color(255, 220, 80, 220);
    private static final Color ELLIPSE_COLOR = new Color(210, 100, 255, 220);

    private final MissileAPI missile;
    private final IntervalUtil debugInterval = new IntervalUtil(0.08f, 0.08f);
    private CombatEntityAPI target;

    public GearInGeometryMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        this.missile = missile;
        target = launchingShip == null ? null : launchingShip.getShipTarget();
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || missile.isFading() || missile.isFizzling()) {
            return;
        }

        missile.giveCommand(ShipCommand.ACCELERATE);
        if (!isTrackable(engine)) {
            return;
        }

        Vector2f leadPoint = calculateLeadPoint();
        steerToward(leadPoint);

        Vector2f predictionEnd = createPredictionEnd();
        Vector2f collisionPoint = CollisionUtils.getCollisionPoint(
                missile.getLocation(), predictionEnd, target);
        drawDebugGeometry(engine, amount, predictionEnd, collisionPoint);
    }

    private boolean isTrackable(CombatEngineAPI engine) {
        if (target == null || !engine.isEntityInPlay(target)
                || target.getCollisionClass() == CollisionClass.NONE) {
            return false;
        }
        return !(target instanceof ShipAPI) || !((ShipAPI) target).isPhased();
    }

    private Vector2f calculateLeadPoint() {
        float distance = MathUtils.getDistance(missile.getLocation(), target.getLocation());
        float speed = Math.max(1f, missile.getMaxSpeed());
        float leadTime = MathUtils.clamp(distance / speed, 0f, MAX_LEAD_TIME);

        Vector2f leadOffset = new Vector2f(target.getVelocity());
        leadOffset.scale(leadTime);
        return Vector2f.add(target.getLocation(), leadOffset, new Vector2f());
    }

    private void steerToward(Vector2f leadPoint) {
        float desiredFacing = VectorUtils.getAngle(missile.getLocation(), leadPoint);
        float turn = MathUtils.getShortestRotation(missile.getFacing(), desiredFacing);
        if (turn < 0f) {
            missile.giveCommand(ShipCommand.TURN_RIGHT);
        } else if (turn > 0f) {
            missile.giveCommand(ShipCommand.TURN_LEFT);
        }

        if (!VectorUtils.isZeroVector(missile.getVelocity())) {
            float velocityFacing = VectorUtils.getFacing(missile.getVelocity());
            float slip = MathUtils.getShortestRotation(missile.getFacing(), velocityFacing);
            if (slip < -20f) {
                missile.giveCommand(ShipCommand.STRAFE_LEFT);
            } else if (slip > 20f) {
                missile.giveCommand(ShipCommand.STRAFE_RIGHT);
            }
        }
    }

    private Vector2f createPredictionEnd() {
        Vector2f direction = new Vector2f(1f, 0f);
        VectorUtils.rotate(direction, missile.getFacing());
        VectorUtils.resize(direction, PREDICTION_LENGTH);
        return Vector2f.add(missile.getLocation(), direction, new Vector2f());
    }

    private void drawDebugGeometry(CombatEngineAPI engine, float amount,
            Vector2f predictionEnd, Vector2f collisionPoint) {
        if (!DEBUG) {
            return;
        }
        debugInterval.advance(amount);
        if (!debugInterval.intervalElapsed()) {
            return;
        }

        addMarker(engine, MathUtils.getRandomPointOnLine(
                missile.getLocation(), predictionEnd), LINE_COLOR);
        if (collisionPoint != null) {
            addMarker(engine, MathUtils.getRandomPointInCircle(
                    collisionPoint, 12f), HIT_COLOR);
        }

        float radius = target.getCollisionRadius();
        addMarker(engine, MathUtils.getRandomPointOnCircumference(
                target.getLocation(), radius), CIRCLE_COLOR);
        addMarker(engine, EllipseUtils.getRandomPointOnEllipse(
                target.getLocation(), radius * 1.6f, radius * 0.75f,
                target.getFacing()), ELLIPSE_COLOR);
    }

    private void addMarker(CombatEngineAPI engine, Vector2f point, Color color) {
        float size = MathUtils.getRandomNumberInRange(4f, 8f);
        engine.addSmoothParticle(point, ZERO, size, 1f, 0.2f, color);
    }
}
