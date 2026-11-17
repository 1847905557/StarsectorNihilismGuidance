package data.scripts.gearin.combat;

import java.nio.FloatBuffer;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/** 管理方向标的数据、绘制与资源生命周期。 */
public class GearInOpenGLMarker extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInOpenGLMarker.class);

    private static final int RING_SEGMENTS = 64;
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;
    private static final float RING_LINE_WIDTH = 2f;
    private static final float DIRECTION_LINE_WIDTH = 3f;

    private final ShipAPI trackedShip;

    private CombatEngineAPI engine;
    private FloatBuffer ringVertices;
    private FloatBuffer directionVertices;
    private boolean valid = true;
    private boolean cleanupLogged = false;

    public GearInOpenGLMarker(ShipAPI trackedShip) {
        this.trackedShip = trackedShip;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (trackedShip == null) {
            valid = false;
            return;
        }

        ringVertices = BufferUtils.createFloatBuffer(RING_SEGMENTS * 2);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            ringVertices.put((float) Math.cos(angle) * RING_RADIUS);
            ringVertices.put((float) Math.sin(angle) * RING_RADIUS);
        }
        ringVertices.flip();

        directionVertices = BufferUtils.createFloatBuffer(4);
        directionVertices.put(0f).put(0f);
        directionVertices.put(DIRECTION_LENGTH).put(0f);
        directionVertices.flip();

        entity.getLocation().set(trackedShip.getLocation());
        LOG.info("GearIn OpenGL marker init: ship=" + trackedShip.getId());
    }

    @Override
    public void advance(float amount) {
        if (!valid) {
            return;
        }
        if (engine == null || trackedShip == null
                || engine.isCombatOver()
                || !engine.isEntityInPlay(trackedShip)
                || !trackedShip.isAlive()) {
            valid = false;
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        entity.getLocation().set(trackedShip.getLocation());
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!valid) {
            return;
        }

        boolean vaoAvailable = GLContext.getCapabilities().OpenGL30;
        int previousVao = vaoAvailable ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) : 0;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean matrixPushed = false;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT
                | GL11.GL_LINE_BIT);
        if (vaoAvailable) {
            GL30.glBindVertexArray(0);
        }
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL20.glUseProgram(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;
            GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
            GL11.glRotatef(trackedShip.getFacing(), 0f, 0f, 1f);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0.1f, 0.9f, 1f, 0.75f * viewport.getAlphaMult());
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

            ringVertices.rewind();
            GL11.glVertexPointer(2, 0, ringVertices);
            GL11.glLineWidth(RING_LINE_WIDTH);
            GL11.glDrawArrays(GL11.GL_LINE_LOOP, 0, RING_SEGMENTS);

            directionVertices.rewind();
            GL11.glVertexPointer(2, 0, directionVertices);
            GL11.glLineWidth(DIRECTION_LINE_WIDTH);
            GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
        } finally {
            if (matrixPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            if (vaoAvailable) {
                GL30.glBindVertexArray(previousVao);
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL20.glUseProgram(previousProgram);
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        valid = false;
        ringVertices = null;
        directionVertices = null;
        if (!cleanupLogged) {
            cleanupLogged = true;
            LOG.info("GearIn OpenGL marker cleanup: ship="
                    + (trackedShip == null ? "null" : trackedShip.getId()));
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return engine == null || engine.getViewport() == null
                ? DIRECTION_LENGTH
                : DIRECTION_LENGTH + engine.getViewport().convertScreenWidthToWorldWidth(3f);
    }
}
