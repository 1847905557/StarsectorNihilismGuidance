package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 过热冶炉的热量光环：画在舰船下方，亮度随幅能升高。 */
public class GearInHeatGlowRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Color GLOW_COLOR = new Color(255, 110, 40);
    private static final float SIZE_MULT = 3f;

    private final ShipAPI ship;
    private final float size;
    private SpriteAPI sprite;
    private boolean expired;

    public GearInHeatGlowRenderer(ShipAPI ship) {
        super(CombatEngineLayers.BELOW_SHIPS_LAYER);
        this.ship = ship;
        this.size = ship.getCollisionRadius() * SIZE_MULT;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        sprite = Global.getSettings().getSprite("fx", "GearIn_heat_glow");
        sprite.setSize(size, size);
        sprite.setColor(GLOW_COLOR);
        sprite.setAdditiveBlend();
        entity.getLocation().set(ship.getLocation());
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) {
            return;
        }
        if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
            expired = true;
            return;
        }
        entity.getLocation().set(ship.getLocation());
    }

    @Override
    public float getRenderRadius() {
        return size;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float level = ship.getFluxLevel();
        if (level <= 0f) {
            return;
        }
        sprite.setAlphaMult(level * viewport.getAlphaMult());
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public boolean isExpired() {
        return expired;
    }
}
