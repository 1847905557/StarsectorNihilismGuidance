package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.List;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
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

        private TrailTracker(DamagingProjectileAPI projectile) {
            this.projectile = projectile;
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
            MagicTrailPlugin.addTrailMemberSimple(
                    projectile, glowTrailId, glowSprite, location,
                    0f, facing, 22f, 6f, new Color(60, 120, 255),
                    0.55f, 0.05f, 0.15f, 0.6f, true);
        }

        private void cutAndRemove() {
            MagicTrailPlugin.cutTrailsOnEntity(projectile);
            engine.removePlugin(this);
        }
    }
}
