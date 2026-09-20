package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicUI;

public final class GearInTargetProbe extends BaseEveryFrameCombatPlugin {

    private static final float DISPLAY_RANGE = 2000f;
    private static final float PARTICLE_INTERVAL = 0.05f;
    private static final float PARTICLE_DURATION = 0.08f;
    private static final float PARTICLE_SIZE = 18f;
    private static final Color PARTICLE_COLOR = new Color(90, 255, 140, 220);
    private static final Vector2f ZERO = new Vector2f();

    private CombatEngineAPI engine;
    private ShipAPI previousTarget;
    private float particleElapsed;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            resetTarget();
            return;
        }

        ShipAPI target = player.getShipTarget();
        if (target == null || !target.isAlive() || !engine.isEntityInPlay(target)) {
            resetTarget();
            return;
        }

        if (target != previousTarget) {
            previousTarget = target;
            particleElapsed = 0f;
        }

        float distance = MathUtils.getDistance(player, target);
        float linearProximity = 1f - Math.min(distance / DISPLAY_RANGE, 1f);
        float smoothProximity = MagicAnim.smooth(linearProximity);

        MagicUI.drawHUDStatusBar(
                player,
                smoothProximity,
                MagicUI.GREENCOLOR,
                MagicUI.GREENCOLOR,
                linearProximity,
                Math.round(distance) + " su",
                "目标接近度",
                false);

        particleElapsed += amount;
        if (particleElapsed < PARTICLE_INTERVAL) {
            return;
        }
        particleElapsed %= PARTICLE_INTERVAL;

        Vector2f collisionPoint = MagicFakeBeam.getCollisionPointOnCircumference(
                player.getLocation(),
                target.getLocation(),
                target.getLocation(),
                target.getCollisionRadius());
        if (collisionPoint != null) {
            engine.addSmoothParticle(
                    collisionPoint, ZERO, PARTICLE_SIZE, 1f,
                    PARTICLE_DURATION, PARTICLE_COLOR);
        }
    }

    private void resetTarget() {
        previousTarget = null;
        particleElapsed = 0f;
    }
}
