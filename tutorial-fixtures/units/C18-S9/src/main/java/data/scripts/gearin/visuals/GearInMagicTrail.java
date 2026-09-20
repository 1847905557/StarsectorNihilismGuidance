package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;

public class GearInMagicTrail implements OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon,
            CombatEngineAPI engine) {
        engine.addPlugin(new TrailTracker(projectile));
        Vector2f detachedVelocity = new Vector2f(projectile.getVelocity());
        detachedVelocity.scale(0.65f);
        spawnDetachedTrail(engine, projectile.getLocation(), detachedVelocity);
    }

    public static void spawnDetachedTrail(CombatEngineAPI engine,
            Vector2f location, Vector2f velocity) {
        engine.addPlugin(new DetachedTrailTracker(location, velocity));
    }

    private static final class TrailTracker extends BaseEveryFrameCombatPlugin {
        private static final float SEGMENT_INTERVAL = 0.05f;
        private static final float TELEPORT_DISTANCE = 250f;

        private final DamagingProjectileAPI projectile;
        private CombatEngineAPI engine;
        private SpriteAPI bodySprite;
        private SpriteAPI glowSprite;
        private float bodyTrailId;
        private float glowTrailId;
        private float segmentTimer;
        private Vector2f lastLocation;
        private final Map<String, Object> glowOptions = new HashMap<>();

        private TrailTracker(DamagingProjectileAPI projectile) {
            this.projectile = projectile;
            glowOptions.put("SIZE_PULSE_WIDTH", 3f);
            glowOptions.put("SIZE_PULSE_COUNT", 2);
            glowOptions.put("FORWARD_PROPAGATION", false);
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
            bodySprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
            glowSprite = Global.getSettings().getSprite("fx", "base_trail_contrail");
            bodyTrailId = MagicTrailPlugin.getUniqueID();
            glowTrailId = MagicTrailPlugin.getUniqueID();
            lastLocation = new Vector2f(projectile.getLocation());
            addSegments();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine == null || engine.isPaused()) {
                return;
            }
            if (!engine.isEntityInPlay(projectile)
                    || projectile.isFading() || projectile.didDamage()) {
                cutAndRemove();
                return;
            }
            if (MathUtils.getDistance(lastLocation, projectile.getLocation())
                    > TELEPORT_DISTANCE) {
                MagicTrailPlugin.cutTrailsOnEntity(projectile);
                bodyTrailId = MagicTrailPlugin.getUniqueID();
                glowTrailId = MagicTrailPlugin.getUniqueID();
            }
            segmentTimer += amount;
            if (segmentTimer >= SEGMENT_INTERVAL) {
                segmentTimer = 0f;
                addSegments();
            }
            lastLocation.set(projectile.getLocation());
        }

        private void addSegments() {
            Vector2f location = projectile.getLocation();
            float facing = projectile.getFacing();
            MagicTrailPlugin.addTrailMemberSimple(
                    projectile, bodyTrailId, bodySprite, location,
                    0f, facing, 14f, 3f, new Color(120, 220, 255),
                    0.8f, 0.05f, 0.2f, 0.8f, true);
            MagicTrailPlugin.addTrailMemberAdvanced(
                    projectile, glowTrailId, glowSprite, location,
                    0f, 0f, facing, 0f, 0f, 22f, 6f,
                    new Color(90, 170, 255), new Color(30, 70, 180),
                    0.55f, 0.05f, 0.15f, 0.6f, true,
                    192f, 600f, -1f, new Vector2f(), glowOptions,
                    CombatEngineLayers.CONTRAILS_LAYER, 1f);
        }

        private void cutAndRemove() {
            MagicTrailPlugin.cutTrailsOnEntity(projectile);
            engine.removePlugin(this);
        }
    }

    private static final class DetachedTrailTracker
            extends BaseEveryFrameCombatPlugin {
        private static final float SEGMENT_INTERVAL = 0.05f;
        private static final float LIFETIME = 0.75f;

        private final SimpleEntity anchor;
        private final Vector2f velocity;
        private CombatEngineAPI engine;
        private SpriteAPI sprite;
        private float trailId;
        private float segmentTimer;
        private float elapsed;

        private DetachedTrailTracker(Vector2f location, Vector2f velocity) {
            anchor = new SimpleEntity(new Vector2f(location));
            this.velocity = new Vector2f(velocity);
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
            sprite = Global.getSettings().getSprite("fx", "base_trail_smooth");
            trailId = MagicTrailPlugin.getUniqueID();
            addSegment();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine == null || engine.isPaused()) {
                return;
            }
            elapsed += amount;
            if (elapsed >= LIFETIME) {
                MagicTrailPlugin.cutTrailsOnEntity(anchor);
                engine.removePlugin(this);
                return;
            }
            Vector2f movement = new Vector2f(velocity);
            movement.scale(amount);
            Vector2f.add(anchor.getLocation(), movement, anchor.getLocation());

            segmentTimer += amount;
            if (segmentTimer >= SEGMENT_INTERVAL) {
                segmentTimer -= SEGMENT_INTERVAL;
                addSegment();
            }
        }

        private void addSegment() {
            MagicTrailPlugin.addTrailMemberSimple(
                    anchor, trailId, sprite, anchor.getLocation(),
                    0f, VectorUtils.getFacing(velocity), 10f, 2f,
                    new Color(255, 90, 220), 0.65f,
                    0.02f, 0.08f, 0.35f, true);
        }
    }
}
