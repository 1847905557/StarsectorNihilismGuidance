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
 * 完整最小例：ShaderProgram、UBO、uniform、纹理与矩阵提交。
 *
 * 事实(BoxUtil pinned commit d0423e5...，全部经成熟实例消费侧核验)：
 * - 程序对象：ShaderUtil.createShaderVFFormPath(名称, vert 路径, frag 路径) 按路径创建
 *   程序(成熟实例消费实证)，包装进 org.boxutil.units.standard.ShaderProgram；创建后立即用
 *   getUniformIndex / getUBOIndex 查询并保存 location(实例规范
 *   "创建后必须保存后续渲染所需的 uniform、attribute 或 UBO location")；
 * - UBO：GLSL 侧声明 layout(std140) uniform BUtilGlobalData { mat4 gameViewport;
 *   vec4 gameScreenBorder; }(成熟实例同款声明)，Java 侧
 *   getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding()) 绑定；gameViewport
 *   是 BoxUtil 全局矩阵 UBO 供给的世界→屏幕矩阵，取代 C18-S1 的手动 glOrtho；
 *   块名必须与 GLSL 逐字一致(大小写敏感)；
 * - uniform：标量/向量(glUniform1f/2f/4f)、采样器(glUniform1i 值=纹理单元号)、
 *   矩阵(glUniformMatrix4(loc, false, 列主序 FloatBuffer)，transpose 必须 false)；
 *   名称与类型必须与 GLSL 声明一致(C18-S1 硬规则 6)：拼错 → getUniformIndex 返回
 *   -1 → 提交被静默忽略且无日志；
 * - 纹理：SettingsAPI.getSprite 取引擎必载贴图 graphics/fx/particlealpha32sq.png
 *   (引擎启动期进入加载队列；贴图取得方式见 C16-S9)，
 *   SpriteAPI.getTextureId() 取 GL 纹理 id，glActiveTexture(GL_TEXTURE0) +
 *   glBindTexture(GL_TEXTURE_2D, id) + glUniform1i(texLoc, 0) 完成单元绑定与采样器
 *   提交(成熟实例实证)；
 * - 矩阵：modelMatrix 是 2D 刚性变换(旋转+平移)，列主序 16 float，布局同成熟实例
 *   maintainAnchoredFloatBuffer；每帧复用
 *   FloatBuffer(clear/put/flip)；
 * - 门禁：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 共同决定 shader 路径
 *   可用性(C16-S9)；isBaseGL43Supported() 只是 GL 能力探测，不能替代
 *   ShaderCore.isValid()(实例陷阱)；门禁失败 → 记录日志并自废，
 *   不创建程序；
 * - 生命周期：每场战斗 init 创建程序与 VBO(GL 上下文就绪)，render 内 active()/
 *   close() 配对提交(实例规范"active() 后必须按 close() 关闭")，
 *   cleanup() 内 program.delete() + glDeleteBuffers，禁止跨战斗缓存(C6-S3/C7-S6
 *   延续；实例规范"资源清理归创建者")；isValid() 使用前检查
 *   (实例陷阱：失效程序继续绑定 → 输出错位或状态污染)。
 */
public class GearInShaderVisual extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderVisual.class);

    /** shader 资源路径(MOD 根目录相对路径，必须用 /；C18-S2 硬规则 1 延续)。 */
    private static final String VERT_PATH = "data/shaders/GearIn_shader_visual.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_shader_visual.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致(大小写敏感)。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 四边形世界尺寸(局部坐标 [-1,1] × size/2)。 */
    private static final float QUAD_WIDTH = 160f;
    private static final float QUAD_HEIGHT = 80f;

    /** 演示寿命(秒)：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 30f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    private ShaderProgram program;
    private SpriteAPI sprite;
    private int vboId = 0;
    private FloatBuffer matrixBuffer = null;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        // BoxUtil shader 路径可用性判据见 C16-S9；失败即自废，不创建程序
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            LOG.info("GearIn shader visual: BoxUtil shader path unavailable (shaderEnable="
                    + BoxConfigs.isShaderEnable() + ", shaderCoreValid="
                    + ShaderCore.isValid() + "), visual disabled");
            valid = false;
            return;
        }
        if (!createProgram() || !buildQuad() || !loadSprite()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点(C7-S6 同款姿势)
        LOG.info("GearIn shader visual init: u_model=" + program.location[0]
                + ", u_size=" + program.location[1]
                + ", u_time=" + program.location[2]
                + ", u_tex=" + program.location[3]
                + ", u_core=" + program.location[4]
                + ", u_fringe=" + program.location[5]
                + ", ubo=" + program.uboLocation[0]
                + ", vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
    }

    /** 创建程序并保存 uniform/UBO location；失败时删除程序并返回 false。 */
    private boolean createProgram() {
        int programId = ShaderUtil.createShaderVFFormPath(getClass().getName(),
                VERT_PATH, FRAG_PATH);
        program = new ShaderProgram(programId);
        // 创建后立即保存 uniform/UBO location(实例规范)
        program.location = new int[] {
                program.getUniformIndex("modelMatrix"),
                program.getUniformIndex("size"),
                program.getUniformIndex("time"),
                program.getUniformIndex("tex"),
                program.getUniformIndex("coreColor"),
                program.getUniformIndex("fringeColor")
        };
        program.uboLocation = new int[] {
                program.getUBOIndex(UBO_BLOCK_NAME, ShaderCore.getMatrixUBOBinding())
        };
        if (!program.isValid()) {
            LOG.error("GearIn shader visual: program invalid after load");
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
            LOG.error("GearIn shader visual: glGenBuffers returned 0");
            return false;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return true;
    }

    /** 取采样贴图；文件未加载时返回 false(getSprite 单参形态要求文件已加载)。 */
    private boolean loadSprite() {
        sprite = Global.getSettings().getSprite(SPRITE_PATH);
        if (sprite == null) {
            LOG.error("GearIn shader visual: sprite not loaded: " + SPRITE_PATH);
            return false;
        }
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
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
        elapsed += amount;
        if (elapsed >= LIFETIME) {
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()(C7-S6 硬规则 3)
            return;
        }
        // 剔除依据是承载实体的位置(C7-S6 硬规则 4)：跟随玩家舰船
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
        LOG.info("GearIn shader visual cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == null || sprite == null || vboId == 0) {
            return;
        }
        if (!program.isValid()) {
            return; // 程序失效不得继续绑定(实例陷阱)
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换(旋转 + 平移)，列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = engine.getPlayerShip().getLocation();
        float facing = engine.getPlayerShip().getFacing();
        matrixBuffer = buildAnchoredMatrix(center, facing, matrixBuffer);

        // attribute：VBO 绑定 + long 偏移(C18-S2 同款姿势；游戏自带 LWJGL 的
        // glVertexAttribPointer 没有 FloatBuffer client 指针重载)
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        program.active(); // 提交段开始；必须与 close() 配对(实例规范)

        // 矩阵提交：modelMatrix(uniform matrix4，列主序)
        GL20.glUniformMatrix4(program.location[0], false, matrixBuffer);
        // 标量/向量 uniform：size(世界尺寸)、time(暂停冻结的战斗总时间)
        GL20.glUniform2f(program.location[1], QUAD_WIDTH, QUAD_HEIGHT);
        GL20.glUniform1f(program.location[2], engine.getTotalElapsedTime(false));

        // 纹理提交：单元 0 绑定 + 采样器指向单元 0(值必须等于单元号)
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
        GL20.glUniform1i(program.location[3], 0);

        // 颜色 uniform：外蓝内橙(贴图红通道在两者之间插值)
        float alpha = viewport.getAlphaMult();
        GL20.glUniform4f(program.location[4], 1.0f, 0.55f, 0.2f, alpha);
        GL20.glUniform4f(program.location[5], 0.15f, 0.45f, 1.0f, alpha);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        program.close(); // 提交段结束

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); // 解绑，避免影响后续绘制者的纹理状态
    }

    /** 2D 刚性变换矩阵(旋转 + 平移)，列主序 16 float；每帧复用缓冲(clear/put/flip)。 */
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
        buffer.put(location.x).put(location.y).put(0f).put(1f); // 列 3(平移)
        buffer.flip();
        return buffer;
    }
}
