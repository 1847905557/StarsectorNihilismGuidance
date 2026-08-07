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
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 完整最小例：常驻渲染插件的渲染层接入、资源释放、开关与性能。
 *
 * 事实（BoxUtil pinned commit d0423e5...，全部经成熟实例消费侧核验）：
 * - 渲染层接入：常驻插件每场战斗注册一次（成熟实例同款姿势：生命周期插件 init 内
 *   addLayeredRenderingPlugin(常驻渲染插件)），不按承载实体或
 *   寿命失效，跟随玩家舰船；层声明用顶层三图层之一的 ABOVE_PARTICLES_LOWER（粒子系统
 *   之后渲染，成熟实例同款）；渲染剔除依据是承载实体位置
 *   与 getRenderRadius()（C7-S6 硬规则 4），位置在 advance 里同步；
 * - 资源释放：GL 资源（program/VBO）在 cleanup() 释放并复位句柄（幂等，重复调用安全）；
 *   战斗结束 destroyInstance 不调 cleanup()—— 常驻插件必须在 advance() 里判
 *   engine.isCombatOver()（= 结束标志 && 淡出完成）
 *   主动失效（isExpired() -> true）驱动引擎移除，否则 program/VBO 随每场战斗泄漏；
 *   淡出期间 viewport.getAlphaMult() 下降，本示例把它送进 intensity uniform 实现视觉
 *   淡出（成熟实例同款姿势）；共享贴图（引擎启动期加载的
 *   particlealpha32sq.png）不卸载 ——
 *   SettingsAPI.unloadTexture 按文件名从全局纹理管理器移除，卸载共享贴图会伤及所有消费方；
 * - 开关：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 是 BoxUtil shader 路径
 *   可用性判据见 C16-S9，必须在 GL 上下文就绪后的 init 里重新判定，禁止缓存进
 *   类加载期 static final 字段（此时无 GL 上下文，isValid() 不可评估）；render 每帧
 *   复核（成熟实例每个使用点读取 BoxConfigs.isShaderEnable() 的姿势）；本示例另有运行时开关
 *   setRuntimeEnabled—— 只改变渲染路径与显示成本，不改变玩法状态（实例规范
 *   "图形开关只改变渲染路径和显示成本"），关闭时 render 零 GL 提交；
 * - 性能：一次 glDrawArrays = 一个 draw call，本示例每帧恰好 1 个（frames/drawCalls
 *   计数在 cleanup 日志输出）；uniform location 创建后保存、FloatBuffer 复用
 *   （clear/put/flip）、render 内零分配零日志；getRenderRadius 覆盖绘制范围即可，
 *   常驻插件也不需要 Float.MAX_VALUE（那是全层宿主模板的例外姿势，代价是永不离屏剔除）；
 *   批处理方法见 glMultiDrawArrays 批量绘制与 C16-S15 的实例提交。
 */
public class GearInShaderPlugin extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderPlugin.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /；C18-S2 硬规则 1 延续）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_field_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_field_shader.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致（大小写敏感）。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 能量场世界尺寸（局部坐标 [-1,1] × size/2）。 */
    private static final float QUAD_WIDTH = 220f;
    private static final float QUAD_HEIGHT = 220f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    /**
     * 运行时开关：只改变渲染路径与显示成本，不改变玩法状态（实例规范）。
     * 读取时机是每次使用点（render 开头），不缓存进 init 快照。
     */
    private static volatile boolean runtimeEnabled = true;

    public static void setRuntimeEnabled(boolean enabled) {
        if (runtimeEnabled == enabled) {
            return;
        }
        runtimeEnabled = enabled;
        LOG.info("GearIn shader plugin: runtime switch " + (enabled ? "on" : "off"));
    }

    public static boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    private CombatEngineAPI engine;
    private boolean valid = true;
    private long framesRendered = 0L;
    private long drawCalls = 0L;

    private ShaderProgram program;
    private SpriteAPI sprite;
    private int vboId = 0;
    private FloatBuffer matrixBuffer = null;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        // BoxUtil shader 路径可用性判据见 C16-S9。必须在 GL 上下文就绪后的
        // init 里重新判定，禁止缓存进类加载期 static final 字段 —— 类加载期无 GL 上下文，
        // ShaderCore.isValid() 不可评估（C18-S3 硬规则 1 延续）
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            LOG.info("GearIn shader plugin: BoxUtil shader path unavailable (shaderEnable="
                    + BoxConfigs.isShaderEnable() + ", shaderCoreValid="
                    + ShaderCore.isValid() + "), plugin idle");
            return; // 不创建任何 GL 资源；插件保持注册但不渲染，战斗结束时照常清理（空清理）
        }
        if (!createProgram() || !buildQuad() || !loadSprite()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            cleanup();     // 已创建的部分资源立即释放；cleanup 幂等，引擎移除时再调一次也安全
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（C7-S6 同款姿势）
        LOG.info("GearIn shader plugin init: layers=" + getActiveLayers()
                + ", u_model=" + program.location[0]
                + ", u_size=" + program.location[1]
                + ", u_time=" + program.location[2]
                + ", u_tex=" + program.location[3]
                + ", u_intensity=" + program.location[4]
                + ", u_core=" + program.location[5]
                + ", u_fringe=" + program.location[6]
                + ", ubo=" + program.uboLocation[0]
                + ", vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
    }

    /** 创建程序并保存 uniform/UBO location；失败时删除程序并返回 false。 */
    private boolean createProgram() {
        int programId = ShaderUtil.createShaderVFFormPath(getClass().getName(),
                VERT_PATH, FRAG_PATH);
        program = new ShaderProgram(programId);
        // 创建后立即保存 uniform/UBO location（实例规范）
        program.location = new int[] {
                program.getUniformIndex("modelMatrix"),
                program.getUniformIndex("size"),
                program.getUniformIndex("time"),
                program.getUniformIndex("tex"),
                program.getUniformIndex("intensity"),
                program.getUniformIndex("coreColor"),
                program.getUniformIndex("fringeColor")
        };
        program.uboLocation = new int[] {
                program.getUBOIndex(UBO_BLOCK_NAME, ShaderCore.getMatrixUBOBinding())
        };
        if (!program.isValid()) {
            LOG.error("GearIn shader plugin: program invalid after load");
            program.delete();
            program = null;
            return false;
        }
        return true;
    }

    /** 两个三角形组成的四边形：6 个顶点，局部坐标 [-1,1]，位置与 UV 交错写入一个 VBO。 */
    private boolean buildQuad() {
        FloatBuffer quadBuffer = BufferUtils.createFloatBuffer(6 * FLOATS_PER_VERTEX);
        quadBuffer.put(new float[]{
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,
                -1f, -1f, 0f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f
        });
        quadBuffer.flip();

        vboId = GL15.glGenBuffers();
        if (vboId == 0) {
            LOG.error("GearIn shader plugin: glGenBuffers returned 0");
            return false;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return true;
    }

    /** 取采样贴图；文件未加载时返回 false（getSprite 单参形态要求文件已加载）。 */
    private boolean loadSprite() {
        sprite = Global.getSettings().getSprite(SPRITE_PATH);
        if (sprite == null) {
            LOG.error("GearIn shader plugin: sprite not loaded: " + SPRITE_PATH);
            return false;
        }
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        // 顶层三图层之一：粒子系统之后渲染（C7-S6 层序）；
        // 成熟实例同款层选择
        return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
    }

    @Override
    public float getRenderRadius() {
        // 覆盖绘制范围即可；常驻插件也不需要 Float.MAX_VALUE（那是全层宿主模板的例外，
        // 全层宿主模板，代价是永不离屏剔除）
        return Math.max(QUAD_WIDTH, QUAD_HEIGHT);
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        // 剔除依据是承载实体的位置（C7-S6 硬规则 4）：常驻插件跟随玩家舰船
        entity.getLocation().set(engine.getPlayerShip().getLocation());
        // 战斗结束收尾：isCombatOver() = 结束标志 && 淡出完成。
        // 常驻插件必须在 advance 里主动失效驱动引擎移除并调用
        // cleanup()—— 战斗结束 destroyInstance 不调 cleanup()，等它等于每场泄漏
        // （C7-S6 硬规则 3 的常驻版）
        if (engine.isCombatOver()) {
            valid = false;
        }
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        boolean hadResources = vboId != 0 || program != null;
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0; // 句柄复位：cleanup 幂等，重复调用安全（防二次删除）
        }
        if (program != null) {
            program.delete();
            program = null;
        }
        if (hadResources || framesRendered > 0) {
            LOG.info("GearIn shader plugin cleanup: frames=" + framesRendered
                    + ", drawCalls=" + drawCalls);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == null || sprite == null || vboId == 0) {
            return;
        }
        // 运行时开关：每次使用点读取（不缓存）—— 关闭 → 零 GL 提交（开关只改变渲染路径
        // 与显示成本；实例规范与陷阱"关闭后仍走昂贵路径"）
        if (!runtimeEnabled) {
            return;
        }
        // 每帧复核 shader 路径可用性：成熟实例每个使用点读取 BoxConfigs.isShaderEnable()
        // 的姿势（成熟实例各 helper），失败路径零提交
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            return;
        }
        if (!program.isValid()) {
            return; // 程序失效不得继续绑定（实例陷阱）
        }
        // 淡出期间的 alphaMult 收缩：intensity uniform 用它实现能量场随战斗结束淡出；
        // alpha 为 0（淡出完成）时跳过整段提交
        float alpha = viewport.getAlphaMult();
        if (alpha <= 0f) {
            return;
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换（旋转 + 平移），列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = entity.getLocation();
        float facing = engine.getPlayerShip().getFacing();
        matrixBuffer = buildAnchoredMatrix(center, facing, matrixBuffer);

        // attribute：VBO 绑定 + long 偏移（C18-S2 同款姿势；游戏自带 LWJGL 的
        // glVertexAttribPointer 没有 FloatBuffer client 指针重载）
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        program.active(); // 提交段开始；必须与 close() 配对（实例规范）

        // 矩阵提交：modelMatrix（uniform matrix4，列主序）
        GL20.glUniformMatrix4(program.location[0], false, matrixBuffer);
        // 标量/向量 uniform：size（世界尺寸）、time（暂停冻结的战斗总时间）
        GL20.glUniform2f(program.location[1], QUAD_WIDTH, QUAD_HEIGHT);
        GL20.glUniform1f(program.location[2], engine.getTotalElapsedTime(false));

        // 纹理提交：单元 0 绑定 + 采样器指向单元 0（值必须等于单元号）
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
        GL20.glUniform1i(program.location[3], 0);

        // intensity（alphaMult 驱动的淡出强度）与颜色 uniform：外橙内蓝
        GL20.glUniform1f(program.location[4], alpha);
        GL20.glUniform4f(program.location[5], 1.0f, 0.55f, 0.2f, alpha);
        GL20.glUniform4f(program.location[6], 0.15f, 0.45f, 1.0f, alpha);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        program.close(); // 提交段结束

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); // 解绑，避免影响后续绘制者的纹理状态

        framesRendered++;
        drawCalls++; // 一次 glDrawArrays = 一个 draw call（性能度量，见页面"性能"节）
    }

    /** 2D 刚性变换矩阵（旋转 + 平移），列主序 16 float；每帧复用缓冲（clear/put/flip）。 */
    private static FloatBuffer buildAnchoredMatrix(Vector2f location, float angle,
            FloatBuffer buffer) {
        if (buffer == null || buffer.capacity() < 16) {
            buffer = BufferUtils.createFloatBuffer(16);
        }
        float radians = (float) Math.toRadians(angle);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);

        buffer.clear();
        buffer.put(cos).put(sin).put(0f).put(0f);   // 列 0
        buffer.put(-sin).put(cos).put(0f).put(0f);  // 列 1
        buffer.put(0f).put(0f).put(1f).put(0f);     // 列 2
        buffer.put(location.x).put(location.y).put(0f).put(1f); // 列 3（平移）
        buffer.flip();
        return buffer;
    }
}
