package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.misc.NumberObject;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;

public final class GearInStatusPanel {
    private static final float PANEL_WIDTH = 240f;
    private static final float PANEL_HEIGHT = 104f;
    private static final float SCREEN_MARGIN = 20f;

    private final GearInTextField labels = new GearInTextField();
    private final NumberObject number = new NumberObject();
    private final UIBorderObject border = new UIBorderObject(false, true);

    private int batches;
    private int activeEntities;
    private int lastSubmitState;

    public GearInStatusPanel() {
        border.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        border.setColor(0.25f, 0.85f, 1f, 0.9f);
        number.setColor(new Color(110, 225, 255));
        number.setIntegerLength(4);
        number.setDecimalLength(0);
        labels.setText("BATCHES\nACTIVE\nSUBMIT");
    }

    public void update(int batches, int activeEntities, int lastSubmitState) {
        this.batches = batches;
        this.activeEntities = activeEntities;
        this.lastSubmitState = lastSubmitState;
    }

    public void render() {
        float x = Global.getSettings().getScreenWidthPixels()
                - PANEL_WIDTH - SCREEN_MARGIN;
        float y = Global.getSettings().getScreenHeightPixels()
                - PANEL_HEIGHT - SCREEN_MARGIN;
        border.render(x, y);
        labels.drawAt(x + 16f, y + PANEL_HEIGHT - 24f);
        number.render(batches, new Vector2f(x + 160f, y + 68f),
                0f, 12f, 18f, false);
        number.render(activeEntities, new Vector2f(x + 160f, y + 42f),
                0f, 12f, 18f, false);
        number.render(lastSubmitState, new Vector2f(x + 160f, y + 16f),
                0f, 12f, 18f, false);
    }
}
