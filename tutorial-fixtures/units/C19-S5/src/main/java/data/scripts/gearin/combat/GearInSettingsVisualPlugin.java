package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.gearin.lib.GearInLunaSettingsReader;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/** 使用当前设置生成附近敌舰标记和玩家状态标签。 */
public final class GearInSettingsVisualPlugin extends BaseEveryFrameCombatPlugin {
    private CombatEngineAPI engine;
    private float elapsed;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
            return;
        }
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            return;
        }

        boolean enabled = GearInLunaSettingsReader.isTrailsEnabled();
        int maxEntries = GearInLunaSettingsReader.getMaxEntries();
        float delay = GearInLunaSettingsReader.getPulseDelay();
        Color color = GearInLunaSettingsReader.getMarkerColor();
        String label = GearInLunaSettingsReader.getLabelText();
        engine.maintainStatusForPlayerShip(this, null, label,
                "markers=" + enabled + " limit=" + maxEntries + " delay=" + delay, false);
        if (!enabled) {
            elapsed = 0f;
            return;
        }

        elapsed += amount;
        if (elapsed < delay) {
            return;
        }
        elapsed = 0f;
        List<ShipAPI> enemies = AIUtils.getNearbyEnemies(player, 1600f);
        int count = Math.min(maxEntries, enemies.size());
        for (int index = 0; index < count; index++) {
            engine.addSmoothParticle(enemies.get(index).getLocation(), new Vector2f(),
                    24f, 1f, 0.4f, color);
        }
    }
}
