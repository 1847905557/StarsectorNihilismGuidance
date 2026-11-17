package data.scripts.gearin.combat;

import java.io.IOException;
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
public class GearInVboVaoMarker extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInVboVaoMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_vbo_vao_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_vbo_vao_marker.frag";
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;
    private static final float RING_LINE_WIDTH = 2f;
    private static final float DIRECTION_LINE_WIDTH = 3f;

    private final ShipAPI trackedShip;

    private CombatEngineAPI engine;
    private FloatBuffer coverageVertices;
    private float lastWorldPerPixel = Float.NaN;
    private int vaoId;
    private int vboId;
    private int vertexShader;
    private int fragmentShader;
    private int program;
    private int positionLocation = -1;
    private int centerLocation = -1;
    private int facingLocation = -1;
    private int colorLocation = -1;
    private int geometryLocation = -1;
    private int widthsLocation = -1;
    private int worldPerPixelLocation = -1;
    private boolean programAttempted;
    private boolean valid = true;
    private boolean cleaned;

    public GearInVboVaoMarker(ShipAPI trackedShip) {
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

        entity.getLocation().set(trackedShip.getLocation());
        LOG.info("GearIn VBO/VAO marker init: ship=" + trackedShip.getId()
                + " vao=" + vaoId + " vbo=" + vboId);
    }

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn VBO/VAO marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn VBO/VAO marker: compile failed for " + path + ": "
                    + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private boolean buildProgram() throws IOException {
        String vertexSource = Global.getSettings().loadText(VERT_PATH);
        String fragmentSource = Global.getSettings().loadText(FRAG_PATH);
        vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, VERT_PATH);
        if (vertexShader == 0) {
            return false;
        }
        fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, FRAG_PATH);
        if (fragmentShader == 0) {
            return false;
        }
        program = GL20.glCreateProgram();
        if (program == 0) {
            LOG.error("GearIn VBO/VAO marker: could not create program");
            return false;
        }
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn VBO/VAO marker: link failed: "
                    + GL20.glGetProgramInfoLog(program, 4096));
            return false;
        }
        positionLocation = GL20.glGetAttribLocation(program, "a_position");
        centerLocation = GL20.glGetUniformLocation(program, "u_center");
        facingLocation = GL20.glGetUniformLocation(program, "u_facing");
        colorLocation = GL20.glGetUniformLocation(program, "u_color");
        geometryLocation = GL20.glGetUniformLocation(program, "u_geometry");
        widthsLocation = GL20.glGetUniformLocation(program, "u_widthPixels");
        worldPerPixelLocation = GL20.glGetUniformLocation(program, "u_worldPerPixel");
        if (positionLocation < 0 || centerLocation < 0 || facingLocation < 0
                || colorLocation < 0 || geometryLocation < 0 || widthsLocation < 0
                || worldPerPixelLocation < 0) {
            LOG.error("GearIn VBO/VAO marker: attribute or uniform location missing");
            return false;
        }
        return true;
    }

    private boolean buildGeometry() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            coverageVertices = BufferUtils.createFloatBuffer(12);
            updateCoverageVertices(0f);

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
            if (vaoId == 0 || vboId == 0) {
                LOG.error("GearIn VBO/VAO marker: could not create VAO or VBO");
                return false;
            }
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(positionLocation);
            GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
            return true;
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
        }
    }

    private void updateCoverageVertices(float worldPerPixel) {
        float padding = 3f * worldPerPixel;
        float left = -RING_RADIUS - padding;
        float right = DIRECTION_LENGTH + padding;
        float bottom = -RING_RADIUS - padding;
        float top = RING_RADIUS + padding;
        coverageVertices.clear();
        coverageVertices.put(left).put(bottom);
        coverageVertices.put(right).put(bottom);
        coverageVertices.put(right).put(top);
        coverageVertices.put(left).put(bottom);
        coverageVertices.put(right).put(top);
        coverageVertices.put(left).put(top);
        coverageVertices.flip();
    }

    @Override
    public void advance(float amount) {
        if (!valid) {
            return;
        }
        if (engine == null || trackedShip == null
                || engine.isCombatOver() || !engine.isEntityInPlay(trackedShip)
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
        if (!programAttempted) {
            programAttempted = true;
            if (!GLContext.getCapabilities().OpenGL30) {
                LOG.error("GearInVboVaoMarker: required OpenGL capability unavailable");
                valid = false;
                return;
            }
            try {
                if (!buildProgram() || !buildGeometry()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("GearInVboVaoMarker: GPU resource creation failed", ex);
                cleanup();
                return;
            }
            LOG.info("GearInVboVaoMarker GPU resources ready: vao=" + vaoId + " vbo=" + vboId);
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);

        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean matrixPushed = false;
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            if (Float.compare(worldPerPixel, lastWorldPerPixel) != 0) {
                updateCoverageVertices(worldPerPixel);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, coverageVertices);
                lastWorldPerPixel = worldPerPixel;
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;
            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUniform2f(centerLocation, entity.getLocation().x, entity.getLocation().y);
            GL20.glUniform1f(facingLocation, trackedShip.getFacing());
            GL20.glUniform4f(colorLocation, 0.1f, 0.9f, 1f,
                    0.75f * viewport.getAlphaMult());
            GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
            GL20.glUniform2f(widthsLocation, RING_LINE_WIDTH, DIRECTION_LINE_WIDTH);
            GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            if (matrixPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
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
        if (cleaned) {
            return;
        }
        cleaned = true;
        valid = false;
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
        coverageVertices = null;
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        if (vertexShader != 0) {
            GL20.glDeleteShader(vertexShader);
            vertexShader = 0;
        }
        if (fragmentShader != 0) {
            GL20.glDeleteShader(fragmentShader);
            fragmentShader = 0;
        }
        LOG.info("GearIn VBO/VAO marker cleanup: ship="
                + (trackedShip == null ? "null" : trackedShip.getId()));
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
