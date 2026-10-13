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
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/** 管理方向标的数据、绘制与资源生命周期。 */
public class GearInSsboMarker extends BaseCombatLayeredRenderingPlugin {
    private static final Logger LOG = Global.getLogger(GearInSsboMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_ssbo_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_ssbo_marker.frag";
    private static final String BLOCK_NAME = "MarkerInstances";
    private static final int BINDING = 0;
    private static final int FLOATS_PER_MARKER = 8;
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;

    private CombatEngineAPI engine;
    private final ShipAPI trackedShip;
    private FloatBuffer markerBuffer;
    private FloatBuffer coverageVertices;
    private float lastWorldPerPixel = Float.NaN;
    private int vaoId;
    private int vboId;
    private int ssboId;
    private int vertexShader;
    private int fragmentShader;
    private int program;
    private int positionLocation = -1;
    private int worldPerPixelLocation = -1;
    private int geometryLocation = -1;
    private int viewportAlphaLocation = -1;
    private float renderRadius = DIRECTION_LENGTH;
    private boolean programAttempted;
    private boolean valid = true;
    private boolean cleaned;

    public GearInSsboMarker(ShipAPI trackedShip) {
        this.trackedShip = trackedShip;
        layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        markerBuffer = BufferUtils.createFloatBuffer(FLOATS_PER_MARKER);

        updateMarkerData();
        LOG.info("GearIn SSBO marker init: vao=" + vaoId + " vbo=" + vboId
                + " ssbo=" + ssboId);
    }

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn SSBO marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn SSBO marker: compile failed for " + path + ": "
                    + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private boolean buildProgram() throws IOException {
        vertexShader = compileShader(GL20.GL_VERTEX_SHADER,
                Global.getSettings().loadText(VERT_PATH), VERT_PATH);
        if (vertexShader == 0) {
            return false;
        }
        fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER,
                Global.getSettings().loadText(FRAG_PATH), FRAG_PATH);
        if (fragmentShader == 0) {
            return false;
        }
        program = GL20.glCreateProgram();
        if (program == 0) {
            return false;
        }
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn SSBO marker: link failed: "
                    + GL20.glGetProgramInfoLog(program, 4096));
            return false;
        }
        positionLocation = GL20.glGetAttribLocation(program, "a_position");
        worldPerPixelLocation = GL20.glGetUniformLocation(program, "u_worldPerPixel");
        geometryLocation = GL20.glGetUniformLocation(program, "u_geometry");
        viewportAlphaLocation = GL20.glGetUniformLocation(program, "u_viewportAlpha");
        int blockIndex = GL43.glGetProgramResourceIndex(program,
                GL43.GL_SHADER_STORAGE_BLOCK, BLOCK_NAME);
        if (positionLocation < 0 || worldPerPixelLocation < 0
                || geometryLocation < 0 || viewportAlphaLocation < 0 || blockIndex < 0) {
            LOG.error("GearIn SSBO marker: attribute, uniform or block missing");
            return false;
        }
        GL43.glShaderStorageBlockBinding(program, blockIndex, BINDING);
        return true;
    }

    private boolean buildGeometry() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousSsbo = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            coverageVertices = BufferUtils.createFloatBuffer(12);
            updateCoverageVertices(0f);
            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
            ssboId = GL15.glGenBuffers();
            if (vaoId == 0 || vboId == 0 || ssboId == 0) {
                return false;
            }
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(positionLocation);
            GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    FLOATS_PER_MARKER * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            return true;
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSsbo);
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

    private void updateMarkerData() {
        entity.getLocation().set(trackedShip.getLocation());
        markerBuffer.clear();
        markerBuffer.put(trackedShip.getLocation().x)
                .put(trackedShip.getLocation().y)
                .put(trackedShip.getFacing()).put(trackedShip.getAlphaMult());
        markerBuffer.put(0.1f).put(0.9f).put(1f).put(1f);
        markerBuffer.flip();
    }

    @Override
    public void advance(float amount) {
        if (!valid) {
            return;
        }
        if (engine.isCombatOver() || !engine.isEntityInPlay(trackedShip)
                || !trackedShip.isAlive()) {
            valid = false;
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        updateMarkerData();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!valid) {
            return;
        }
        if (!programAttempted) {
            programAttempted = true;
            if (!GLContext.getCapabilities().OpenGL43) {
                LOG.error("GearInSsboMarker: required OpenGL capability unavailable");
                valid = false;
                return;
            }
            try {
                if (!buildProgram() || !buildGeometry()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("GearInSsboMarker: GPU resource creation failed", ex);
                cleanup();
                return;
            }
            LOG.info("GearInSsboMarker GPU resources ready: vao=" + vaoId + " vbo=" + vboId + " ssbo=" + ssboId);
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousSsbo = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousSsboBase = GL30.glGetInteger(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING, BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            if (Float.compare(worldPerPixel, lastWorldPerPixel) != 0) {
                updateCoverageVertices(worldPerPixel);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, coverageVertices);
                lastWorldPerPixel = worldPerPixel;
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, markerBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
            GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
            GL20.glUniform1f(viewportAlphaLocation, viewport.getAlphaMult());
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING,
                    previousSsboBase);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSsbo);
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
        if (ssboId != 0) {
            GL15.glDeleteBuffers(ssboId);
            ssboId = 0;
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
        LOG.info("GearIn SSBO marker cleanup");
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return renderRadius + engine.getViewport().convertScreenWidthToWorldWidth(3f);
    }
}
