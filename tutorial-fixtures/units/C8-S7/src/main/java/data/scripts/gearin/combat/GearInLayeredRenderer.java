package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.EnumSet;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 跟随当前舰船，在指定图层绘制 Sprite 标记。 */
public class GearInLayeredRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInLayeredRenderer.class);

    private static final String SPRITE_CATEGORY = "gearin";
    private static final String SPRITE_KEY = "layered_marker";
    private static final float MARKER_SIZE = 120f;

    private final ShipAPI trackedShip;
    private final Color color;

    private CombatEngineAPI engine;
    private SpriteAPI sprite;
    private boolean valid = true;
    private float elapsed = 0f;

    public GearInLayeredRenderer(ShipAPI trackedShip, Color color) {
        this.trackedShip = trackedShip;
        this.color = color;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        entity.getLocation().set(trackedShip.getLocation());
        engine = Global.getCombatEngine();
        sprite = Global.getSettings().getSprite(SPRITE_CATEGORY, SPRITE_KEY);
        sprite.setSize(MARKER_SIZE, MARKER_SIZE);
        sprite.setColor(color);
        LOG.info("GearIn layered renderer init: layers=" + getActiveLayers());
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return MARKER_SIZE;
    }

    @Override
    public void advance(float amount) {
        if (engine.isPaused()) {
            return;
        }
        if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
            valid = false;
            return;
        }
        elapsed += amount;
        entity.getLocation().set(trackedShip.getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        sprite.setColor(color);
        sprite.setAlphaMult(1f);
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }
}
