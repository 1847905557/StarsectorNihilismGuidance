package data.scripts.gearin.combat;

import java.nio.FloatBuffer;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.ShaderProgram;
import org.boxutil.util.ShaderUtil;
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
 * 完整最小例：.vert/.frag 的路径、加载、编译日志与资源生命周期。
 *
 * 路径与加载事实：
 * - shader 没有注册表 ID(区别于 CSV/JSON/spec 注册链)：.vert/.frag 以 MOD 根目录
 *   相对路径为身份，约定放在 data/shaders/(GraphicsLib data/shaders/post/pre.vert、
 *   data/shaders/ 同款布局)，路径必须用 / 分隔 (SettingsAPI.loadText javadoc
 *   硬规则)；反斜杠、盘符冒号、.. 与首字符 / 会在读取前被直接拒绝并抛 RuntimeException；
 * - 读取端行为 (文本读取工具的 readText)：按 UTF-8 解码并把全部 \r 剥除，不剥离
 *   BOM —— 源文件必须 UTF-8 无 BOM(BOM 字符会进入 GLSL 源码导致编译失败)，CRLF 安全；
 *   loadText 本身不打日志，编译/链接日志由 MOD 侧写入 starsector.log；
 * - 编译/链接链 (延续 C18-S1)：glCreateShader -> glShaderSource -> glCompileShader ->
 *   glGetShaderi(GL_COMPILE_STATUS)；glCreateProgram -> glAttachShader ->
 *   glLinkProgram -> glGetProgrami(GL_LINK_STATUS)；
 *   失败时读取 glGetShaderInfoLog / glGetProgramInfoLog(含行号与原因) 并以 ERROR 写入
 *   starsector.log，然后删除对象 (GraphicsLib ShaderLib.createShader/loadShader 同款)；
 * - 生命周期四环节：加载 (每场战斗 init 内执行，不跨战斗缓存)-> 使用 (render 回调内
 *   glUseProgram + glUniform* + glDrawArrays)-> 清理 (cleanup() 内 glDeleteProgram，
 *   战斗结束 destroyInstance 不调 cleanup()，由 advance() 主动失效驱动引擎移除，
 *   C7-S6 硬规则延续)-> 重载 (按同一路径删除后重建；本节采用 "重建成功才替换"：
 *   新程序构建失败时保留旧程序、画面不中断，ERROR 日志给出 InfoLog 作为第一证据)。
 */
public class GearInPulseRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInPulseRenderer.class);

    /** shader 资源路径 (MOD 根目录相对路径，必须用 /，SettingsAPI.loadText javadoc)。 */
    private static final String VERT_PATH = "data/shaders/GearIn_pulse_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_pulse_shader.frag";

    /** 演示四边形半边长 (世界单位)。 */
    private static final float QUAD_HALF_SIZE = 60f;

    /** 演示寿命 (秒)：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 30f;

    /** 重载间隔 (秒)：按同一路径删除并重建 program，演示资源生命周期闭环。 */
    private static final float RELOAD_INTERVAL = 10f;

    /** 交错顶点布局：位置 (2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;
    private float reloadTimer = 0f;

    private ShaderProgram program;
    private int vboId = 0;
    private int uOffsetLocation = -1;
    private int uTimeLocation = -1;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()
                || !buildQuad() || !loadProgram()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点 (C7-S6 同款姿势)
        LOG.info("GearIn pulse shader init: program=" + program + ", vbo=" + vboId
                + ", u_offset=" + uOffsetLocation + ", u_time=" + uTimeLocation
                + ", vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
    }

    /** 首次加载：按路径读取源文本并构建 program；失败时 renderer 自废。 */
    private boolean loadProgram() {
        LOG.info("GearIn pulse shader paths: vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
        int programId = ShaderUtil.createShaderVFFormPath(getClass().getName(),
                VERT_PATH, FRAG_PATH);
        program = new ShaderProgram(programId);
        program.location = new int[] {
                program.getUniformIndex("u_offset"),
                program.getUniformIndex("u_time")
        };
        if (!program.isValid()) {
            program.delete();
            program = null;
            LOG.error("GearIn pulse shader: loadProgram failed, renderer disabled");
            return false;
        }
        uOffsetLocation = program.location[0];
        uTimeLocation = program.location[1];
        return true;
    }

    /**
     * 重载：按同一路径重新构建 program。新程序构建成功才删除并替换旧程序 (build-then-swap)；
     * 构建失败 (源文件被改坏、路径失效等) 时保留旧程序继续绘制，ERROR 日志给出 InfoLog。
     */
    private void reloadProgram() {
        LOG.info("GearIn pulse shader paths: vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
        int newProgramId = ShaderUtil.createShaderVFFormPath(getClass().getName(),
                VERT_PATH, FRAG_PATH);
        ShaderProgram newProgram = new ShaderProgram(newProgramId);
        newProgram.location = new int[] {
                newProgram.getUniformIndex("u_offset"),
                newProgram.getUniformIndex("u_time")
        };
        if (!newProgram.isValid()) {
            newProgram.delete();
            LOG.error("GearIn pulse shader reload failed: keeping old program=" + program);
            return;
        }
        if (program != null) {
            program.delete();
        }
        program = newProgram;
        uOffsetLocation = program.location[0];
        uTimeLocation = program.location[1];
        LOG.info("GearIn pulse shader reload: program=" + program);
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
            LOG.error("GearIn pulse shader: glGenBuffers returned 0");
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
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()(C7-S6 硬规则 3)
            return;
        }
        // 重载循环：按同一路径删除并重建 program(资源生命周期闭环)
        reloadTimer += amount;
        if (reloadTimer >= RELOAD_INTERVAL) {
            reloadTimer = 0f;
            reloadProgram();
        }
        // 剔除依据是承载实体的位置 (C7-S6 硬规则 4)：跟随玩家舰船
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
        if (program != null) {
            program.delete();
            program = null;
        }
        LOG.info("GearIn pulse shader cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == null || !program.isValid() || vboId == 0) {
            return;
        }
        // 世界→屏幕正交投影：按 GraphicsLib renderForeground 姿势自行设置
        // (glOrtho(viewport) 以世界单位建投影，org.dark.shaders.util.ShaderLib.renderForeground() 同款姿势)，
        // 顶点着色器用 gl_ModelViewProjectionMatrix 应用它；矩阵成对 push/pop 复原
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(),
                viewport.getLLY(), viewport.getLLY() + viewport.getVisibleHeight(), -1f, 1f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        program.active();

        // uniform：本次绘制内所有顶点/片元共享；u_offset 每帧更新为玩家舰船位置，
        // u_time 驱动片元侧脉冲相位 (暂停时不累计，与 advance 暂停守卫一致)
        if (uOffsetLocation >= 0) {
            Vector2f center = engine.getPlayerShip().getLocation();
            GL20.glUniform2f(uOffsetLocation, center.x, center.y);
        }
        if (uTimeLocation >= 0) {
            GL20.glUniform1f(uTimeLocation, engine.getTotalElapsedTime(false));
        }

        // attribute：VBO 绑定 + long 偏移 (另有 FloatBuffer client 指针重载，
        // 但本单元绑定 VBO 后使用 long 偏移)
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
        program.close();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
    }
}
