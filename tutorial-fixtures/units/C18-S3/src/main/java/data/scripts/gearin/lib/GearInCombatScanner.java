package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * Scans the area around the player ship and marks representative query results.
 * The plugin only reads combat state and creates diagnostic particles.
 */
public final class GearInCombatScanner extends BaseEveryFrameCombatPlugin {
    private static final float SCAN_RANGE = 1600f;
    private static final float SCAN_INTERVAL = 0.20f;
    private static final float INTERCEPT_SPEED = 800f;
    private static final float PARTICLE_DURATION = 0.25f;

    private static final Color SHIP_COLOR = new Color(80, 160, 255, 220);
    private static final Color MISSILE_COLOR = new Color(255, 150, 40, 220);
    private static final Color PROJECTILE_COLOR = new Color(255, 225, 70, 220);
    private static final Color ASTEROID_COLOR = new Color(170, 170, 170, 220);
    private static final Color ENEMY_COLOR = new Color(255, 70, 70, 240);
    private static final Color INTERCEPT_COLOR = new Color(190, 90, 255, 240);

    private float elapsed;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        elapsed += amount;
        if (elapsed < SCAN_INTERVAL) {
            return;
        }
        elapsed = 0f;

        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            return;
        }

        Vector2f origin = player.getLocation();
        List<ShipAPI> ships = CombatUtils.getShipsWithinRange(origin, SCAN_RANGE);
        ships.remove(player);

        mark(engine, nearest(ships, origin), 18f, SHIP_COLOR);
        mark(engine, nearest(CombatUtils.getMissilesWithinRange(origin, SCAN_RANGE), origin),
                14f, MISSILE_COLOR);
        mark(engine, nearest(CombatUtils.getProjectilesWithinRange(origin, SCAN_RANGE), origin),
                10f, PROJECTILE_COLOR);
        mark(engine, nearest(CombatUtils.getAsteroidsWithinRange(origin, SCAN_RANGE), origin),
                18f, ASTEROID_COLOR);

        ShipAPI enemy = nearest(AIUtils.getNearbyEnemies(player, SCAN_RANGE), origin);
        mark(engine, enemy, 28f, ENEMY_COLOR);
        if (enemy != null) {
            Vector2f intercept = AIUtils.getBestInterceptPoint(
                    origin, INTERCEPT_SPEED, enemy.getLocation(), enemy.getVelocity());
            mark(engine, intercept, 22f, INTERCEPT_COLOR);
        }
    }

    private static <T extends CombatEntityAPI> T nearest(List<T> entities, Vector2f origin) {
        if (entities.isEmpty()) {
            return null;
        }
        List<T> sorted = new ArrayList<T>(entities);
        sorted.sort(new CollectionUtils.SortEntitiesByDistance(origin, true));
        return sorted.get(0);
    }

    private static void mark(
            CombatEngineAPI engine,
            CombatEntityAPI entity,
            float size,
            Color color) {
        if (entity != null) {
            mark(engine, entity.getLocation(), size, color);
        }
    }

    private static void mark(
            CombatEngineAPI engine,
            Vector2f location,
            float size,
            Color color) {
        if (location != null) {
            engine.addSmoothParticle(
                    location, new Vector2f(), size, 1f, PARTICLE_DURATION, color);
        }
    }
}
