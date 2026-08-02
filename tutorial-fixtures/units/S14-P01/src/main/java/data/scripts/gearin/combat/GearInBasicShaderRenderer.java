package data.scripts.gearin.combat;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 完整最小例：GLSL 顶点到片元数据流（attribute / uniform / varying / gl_Position / gl_FragColor）。
 *
 * 数据流事实（0.98a-RC8）：
 * - 顶点着色器输入分两类：attribute（每顶点，Java 侧 VBO + glVertexAttribPointer
 *   喂入，位置必须在 glLinkProgram 前用 glBindAttribLocation 固定）与 uniform（本次
 *   绘制内所有顶点共享，Java 侧 glGetUniformLocation + glUniform* 提交）；
 * - 顶点着色器输出 gl_Position（裁剪空间坐标）与 varying（每顶点写出、光栅化时在
 *   片元间插值）；片元着色器读插值后的 varying 并写 gl_FragColor（GLSL 1.10 方言）；
 * - 本页 render() 里按 GraphicsLib renderForeground 的姿势（org.dark.shaders.util.ShaderLib 第
 *   1485-1489 行）用 glOrtho(viewport) 设置世界→屏幕正交投影，顶点着色器用
 *   gl_ModelViewProjectionMatrix 应用它；
 * - 顶点数据走 VBO + long 偏移：游戏自带 LWJGL 的 GL20.glVertexAttribPointer
 *   没有 FloatBuffer client 指针重载（本单元用游戏 API jar 编译实证），必须
 *   glBindBuffer(GL_ARRAY_BUFFER, vbo) 后用 long 偏移；
 * - 能力基线：GLSL 需要 OpenGL 2.0 上下文（GraphicsLib ShaderLib.init() 以
 *   capabilities.OpenGL20 作为 shader 门禁，第 602-607 行；areShadersAllowed() 的
 *   javadoc 硬措辞 "Do not use shader code if this returns false! Otherwise the
 *   game will crash!"，第 197-198 行）；
 * - 编译/链接失败检查：GL_COMPILE_STATUS / GL_LINK_STATUS 为 FALSE 时读取
 *   glGetShaderInfoLog / glGetProgramInfoLog 并删除对象（GraphicsLib createShader
 *   第 1020-1041 行、loadShader 第 808-846 行同款姿势）；
 * - 资源生命周期沿用 S07-P05 硬规则 2/3：GL 资源在 cleanup() 释放；战斗结束
 *   destroyInstance 不调用 cleanup()，插件在 advance() 里主动失效（isExpired() ->
 *   true）驱动引擎移除；本示例设 LIFETIME 秒寿命后自清；
 * - 插件、引擎引用禁止跨战斗缓存（S06-P03 / S07-P04 / S07-P05 同规）。
 */
public class GearInBasicShaderRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInBasicShaderRenderer.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /，SettingsAPI.loadText javadoc）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_basic_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_basic_shader.frag";

    /** 演示四边形半边长（世界单位）。 */
    private static final float QUAD_HALF_SIZE = 60f;

    /** 演示寿命（秒）：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 20f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    private int program = 0;
    private int vboId = 0;
    private int uOffsetLocation = -1;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (!createProgram() || !buildQuad()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（S07-P05 同款姿势）
        LOG.info("GearIn basic shader init: program=" + program + ", vbo=" + vboId
                + ", u_offset=" + uOffsetLocation);
    }

    /** 加载、编译、链接 GearIn_basic_shader 的 vert/frag，成功返回 true。 */
    private boolean createProgram() {
        String vertSource;
        String fragSource;
        try {
            vertSource = Global.getSettings().loadText(VERT_PATH);
            fragSource = Global.getSettings().loadText(FRAG_PATH);
        } catch (IOException e) {
            LOG.error("GearIn basic shader: loadText failed: " + e.getMessage(), e);
            return false;
        }

        int vert = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        if (vert == 0) {
            return false;
        }
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);
        if (frag == 0) {
            GL20.glDeleteShader(vert);
            return false;
        }

        program = GL20.glCreateProgram();
        if (program == 0) {
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            return false;
        }

        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        // attribute 位置必须在链接前固定，之后 glVertexAttribPointer 用同一位置
        GL20.glBindAttribLocation(program, 0, "a_position");
        GL20.glBindAttribLocation(program, 1, "a_uv");
        GL20.glLinkProgram(program);

        // 链接完成后着色器对象即可删除：程序已持有编译结果
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn basic shader: link failed: " + getProgramLog(program));
            GL20.glDeleteProgram(program);
            program = 0;
            return false;
        }

        uOffsetLocation = GL20.glGetUniformLocation(program, "u_offset");
        return true;
    }

    /** 编译单个 shader 并检查 GL_COMPILE_STATUS；失败时删除对象并返回 0。 */
    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn basic shader: glCreateShader returned 0 (type=" + type + ")");
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn basic shader: compile failed: " + getShaderLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static String getShaderLog(int shader) {
        return GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
    }

    private static String getProgramLog(int program) {
        return GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH));
    }

    /** 两个三角形组成的四边形：6 个顶点，位置与 UV 交错写入一个 VBO。 */
    private boolean buildQuad() {
        float half = QUAD_HALF_SIZE;
        FloatBuffer quadBuffer = BufferUtils.createFloatBuffer(6 * FLOATS_PER_VERTEX);
        quadBuffer.put(new float[]{
                -half, -half, 0f, 0f,
                half, -half, 1f, 0f,
                half, half, 1f, 1f,
                -half, -half, 0f, 0f,
                half, half, 1f, 1f,
                -half, half, 0f, 1f
        });
        quadBuffer.flip();

        vboId = GL15.glGenBuffers();
        if (vboId == 0) {
            LOG.error("GearIn basic shader: glGenBuffers returned 0");
            return false;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return QUAD_HALF_SIZE * 2f;
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        elapsed += amount;
        if (elapsed >= LIFETIME) {
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()（S07-P05 硬规则 3）
            return;
        }
        // 剔除依据是承载实体的位置（S07-P05 硬规则 4）：跟随玩家舰船
        entity.getLocation().set(engine.getPlayerShip().getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        LOG.info("GearIn basic shader cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == 0 || vboId == 0) {
            return;
        }
        // 世界→屏幕正交投影：按 GraphicsLib renderForeground 姿势自行设置
        // （glOrtho(viewport) 以世界单位建投影，org.dark.shaders.util.ShaderLib 第 1485-1489 行），
        // 顶点着色器用 gl_ModelViewProjectionMatrix 应用它；矩阵成对 push/pop 复原
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(),
                viewport.getLLY(), viewport.getLLY() + viewport.getVisibleHeight(), -1f, 1f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL20.glUseProgram(program);

        // uniform：本次绘制内所有顶点/片元共享，提交为玩家舰船当前位置（每帧更新）
        if (uOffsetLocation >= 0) {
            Vector2f center = engine.getPlayerShip().getLocation();
            GL20.glUniform2f(uOffsetLocation, center.x, center.y);
        }

        // attribute：VBO 绑定 + long 偏移（游戏自带 LWJGL 的 glVertexAttribPointer
        // 没有 FloatBuffer client 指针重载，必须走 VBO + long 偏移姿势）
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
    }
}
