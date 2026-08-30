# 用 BoxUtil 制作可随时关闭的常驻能量场

常驻效果比一次性爆闪更考验资源管理：它要选择正确图层、参与离屏剔除、响应运行时开关，并在战斗结束释放 program 与 VBO。本节把这些职责集中到一个分层渲染插件。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 选择常驻插件的渲染层和有效剔除半径；
- 在每次 render 使用点读取 BoxUtil 能力和运行时开关；
- 在战斗结束和重复清理时准确统计并释放 GL 资源。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 管理常驻能量场生命周期

玩家舰周围持续显示脉动能量场；运行时关闭后下一帧停止所有 GL 提交，再开启即可恢复。战斗结束日志给出帧数和 draw call，并且资源只删除一次。

## 文件与 API 链路

### 常驻能量场职责速查表

| 对象或概念 | 职责 |
| --- | --- |
| `BaseCombatLayeredRenderingPlugin` | 提供图层、剔除半径和 cleanup |
| BoxUtil `ShaderProgram` | 管理程序 |
| 运行时开关 | 在 render 使用点读取 |
| VBO 与程序句柄 | 删除后归零 |

### 选择粒子之上的渲染层

**插入位置：\[GearInShaderPlugin.getActiveLayers()\]。** 让能量场与舰船和粒子的层序稳定。

```java
return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
```

完成信号：画面不被舰船主体错误遮挡。

### 让剔除半径覆盖能量场

**插入位置：\[getRenderRadius()\]。** 使用实际世界尺寸而非无限半径。

```java
return Math.max(QUAD_WIDTH, QUAD_HEIGHT);
```

完成信号：离开视口后插件停止渲染，回到视口后恢复。

### 在使用点读取运行时开关

**插入位置：\[render() 的 GL 调用之前\]。** 关闭时直接返回，产生零 GL 提交。

```java
if (!runtimeEnabled || !BoxConfigs.isShaderEnable()) {
    return;
}
```

完成信号：切换关闭后 drawCalls 停止增长。

### 幂等释放 program 与 VBO

**插入位置：\[cleanup()\]。** 删除后把句柄和引用复位。

```java
if (vboId != 0) { GL15.glDeleteBuffers(vboId); vboId = 0; }
if (program != null) { program.delete(); program = null; }
```

完成信号：重复 cleanup 不产生二次删除错误。

## 排查关键故障

下面是**局部故障片段**：

```java
private static final boolean ENABLED = BoxConfigs.isShaderEnable();
```

实际症状：玩家在运行期关闭 shader 后，能量场仍继续提交和绘制。

原因：开关在类加载时被永久缓存，render 没有读取当前值。

下面是**局部修正片段**：

```java
if (!BoxConfigs.isShaderEnable()) return;
```

复验：战斗中连续关闭、开启两次，确认 drawCalls 在关闭区间保持不变。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInShaderPlugin

```java
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
 *
 * - 渲染层接入：常驻插件每场战斗注册一次 (成熟实例同款姿势：生命周期插件 init 内
 *   addLayeredRenderingPlugin(常驻渲染插件))，不按承载实体或
 *   寿命失效，跟随玩家舰船；层声明用顶层三图层之一的 ABOVE_PARTICLES_LOWER(粒子系统
 *   之后渲染，成熟实例同款)；渲染剔除依据是承载实体位置
 *   与 getRenderRadius()(C7-S6 硬规则 4)，位置在 advance 里同步；
 * - 资源释放：GL 资源 (program/VBO) 在 cleanup() 释放并复位句柄 (幂等，重复调用安全)；
 *   战斗结束 destroyInstance 不调 cleanup() —— 常驻插件必须在 advance() 里判
 *   engine.isCombatOver()(= 结束标志 && 淡出完成)
 *   主动失效 (isExpired() -> true) 驱动引擎移除，否则 program/VBO 随每场战斗泄漏；
 *   战斗 viewport.getAlphaMult() 保持 1.0，不提供战斗结束淡出值；本示例把它作为
 *   常量透明度输入，战斗淡出由引擎的全屏变暗处理；共享贴图 (引擎启动期加载的
 *   particlealpha32sq.png) 不卸载 ——
 *   SettingsAPI.unloadTexture 按文件名从全局纹理管理器移除，卸载共享贴图会伤及所有消费方；
 * - 开关：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 是 BoxUtil shader 路径
 *   可用性判据见 C16-S10，必须在 GL 上下文就绪后的 init 里重新判定，禁止缓存进
 *   类加载期 static final 字段 (此时无 GL 上下文，isValid() 不可评估)；render 每帧
 *   复核 (成熟实例每个使用点读取 BoxConfigs.isShaderEnable() 的姿势)；本示例另有运行时开关
 *   setRuntimeEnabled —— 只改变渲染路径与显示成本，不改变玩法状态 (实例规范
 *   "图形开关只改变渲染路径和显示成本")，关闭时 render 零 GL 提交；
 * - 性能：一次 glDrawArrays = 一个 draw call，本示例每帧恰好 1 个 (frames/drawCalls
 *   计数在 cleanup 日志输出)；uniform location 创建后保存、FloatBuffer 复用
 *   (clear/put/flip)、render 内零分配零日志；getRenderRadius 覆盖绘制范围即可，
 *   常驻插件也不需要 Float.MAX_VALUE(那是全层宿主模板的例外姿势，代价是永不离屏剔除)；
 *   批处理方法见 glMultiDrawArrays 批量绘制与 C16-S16 的实例提交。
 */
public class GearInShaderPlugin extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderPlugin.class);

    /** shader 资源路径 (MOD 根目录相对路径，必须用 /；C18-S2 硬规则 1 延续)。 */
    private static final String VERT_PATH = "data/shaders/GearIn_field_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_field_shader.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致 (大小写敏感)。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 能量场世界尺寸 (局部坐标 [-1,1] × size/2)。 */
    private static final float QUAD_WIDTH = 220f;
    private static final float QUAD_HEIGHT = 220f;

    /** 交错顶点布局：位置 (2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    /**
     * 运行时开关：只改变渲染路径与显示成本，不改变玩法状态 (实例规范)。
     * 读取时机是每次使用点 (render 开头)，不缓存进 init 快照。
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
        // BoxUtil shader 路径可用性判据见 C16-S10。必须在 GL 上下文就绪后的
        // init 里重新判定，禁止缓存进类加载期 static final 字段 —— 类加载期无 GL 上下文，
        // ShaderCore.isValid() 不可评估 (C18-S3 硬规则 1 延续)
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            LOG.info("GearIn shader plugin: BoxUtil shader path unavailable (shaderEnable="
                    + BoxConfigs.isShaderEnable() + ", shaderCoreValid="
                    + ShaderCore.isValid() + "), plugin idle");
            return; // 不创建任何 GL 资源；插件保持注册但不渲染，战斗结束时照常清理 (空清理)
        }
        if (!createProgram() || !buildQuad() || !loadSprite()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            cleanup();     // 已创建的部分资源立即释放；cleanup 幂等，引擎移除时再调一次也安全
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点 (C7-S6 同款姿势)
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
        // 创建后立即保存 uniform/UBO location(实例规范)
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

    /**
     * 取采样贴图；文件未加载时 getTextureId() 返回 0，投影渲染为黑/不可见。
     * 原版单参 getSprite 永不返回 null，缺键时返回纹理为 null 的 Sprite —— 原版用返回 0 代替失败信号。
     */
    private boolean loadSprite() {
        sprite = Global.getSettings().getSprite(SPRITE_PATH);
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        // 顶层三图层之一：粒子系统之后渲染 (C7-S6 层序)；
        // 成熟实例同款层选择
        return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
    }

    @Override
    public float getRenderRadius() {
        // 覆盖绘制范围即可；常驻插件也不需要 Float.MAX_VALUE(那是全层宿主模板的例外，
        // 全层宿主模板，代价是永不离屏剔除)
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
        // 剔除依据是承载实体的位置 (C7-S6 硬规则 4)：常驻插件跟随玩家舰船
        entity.getLocation().set(engine.getPlayerShip().getLocation());
        // 战斗结束收尾：isCombatOver() = 结束标志 && 淡出完成。
        // 常驻插件必须在 advance 里主动失效驱动引擎移除并调用
        // cleanup() —— 战斗结束 destroyInstance 不调 cleanup()，等它等于每场泄漏
        // (C7-S6 硬规则 3 的常驻版)
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
            vboId = 0; // 句柄复位：cleanup 幂等，重复调用安全 (防二次删除)
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
        // 运行时开关：每次使用点读取 (不缓存) —— 关闭 → 零 GL 提交 (开关只改变渲染路径
        // 与显示成本；实例规范与陷阱 "关闭后仍走昂贵路径")
        if (!runtimeEnabled) {
            return;
        }
        // 每帧复核 shader 路径可用性：成熟实例每个使用点读取 BoxConfigs.isShaderEnable()
        // 的姿势 (成熟实例各 helper)，失败路径零提交
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            return;
        }
        if (!program.isValid()) {
            return; // 程序失效不得继续绑定 (实例陷阱)
        }
        // 战斗 viewport 的 alpha 保持 1.0；战斗结束淡出由引擎全屏变暗处理
        float alpha = viewport.getAlphaMult();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换 (旋转 + 平移)，列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = entity.getLocation();
        float facing = engine.getPlayerShip().getFacing();
        matrixBuffer = buildAnchoredMatrix(center, facing, matrixBuffer);

        // attribute：VBO 绑定 + long 偏移 (C18-S2 同款姿势；另有 FloatBuffer
        // client 指针重载，本单元绑定 VBO 后使用 long 偏移)
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        program.active(); // 提交段开始；必须与 close() 配对 (实例规范)

        // 矩阵提交：modelMatrix(uniform matrix4，列主序)
        GL20.glUniformMatrix4(program.location[0], false, matrixBuffer);
        // 标量/向量 uniform：size(世界尺寸)、time(暂停冻结的战斗总时间)
        GL20.glUniform2f(program.location[1], QUAD_WIDTH, QUAD_HEIGHT);
        GL20.glUniform1f(program.location[2], engine.getTotalElapsedTime(false));

        // 纹理提交：单元 0 绑定 + 采样器指向单元 0(值必须等于单元号)
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
        GL20.glUniform1i(program.location[3], 0);

        // intensity 使用战斗视口的常量透明度输入；颜色 uniform：外橙内蓝
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
        drawCalls++; // 一次 glDrawArrays = 一个 draw call(性能度量，见页面 "性能" 节)
    }

    /** 2D 刚性变换矩阵 (旋转 + 平移)，列主序 16 float；每帧复用缓冲 (clear/put/flip)。 */
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
```

:::

::: details 完整成品：GearIn_field_shader.frag

```glsl
#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform float intensity;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.15));
    vec4 texSample = texture(tex, uv);
    float d = distance(fragUV, vec2(0.5, 0.5));
    float edgeFade = 1.0 - smoothstep(0.38, 0.50, d);
    float pulse = 0.55 + 0.45 * sin(time * 2.0 - d * 24.0);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a * edgeFade * pulse * intensity;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
```

:::

::: details 完整成品：GearIn_field_shader.vert

```glsl
#version 430

layout (location = 0) in vec2 a_position;
layout (location = 1) in vec2 a_uv;

layout (std140) uniform BUtilGlobalData
{
    mat4 gameViewport;
    vec4 gameScreenBorder;
};

uniform mat4 modelMatrix;
uniform vec2 size;

out vec2 fragUV;

void main() {
    fragUV = a_uv;
    vec4 local = vec4(a_position * size * 0.5, 0.0, 1.0);
    gl_Position = gameViewport * vec4((modelMatrix * local).xy, 0.0, 1.0);
}
```

:::

## 验证方案

1. 编译 Java fixture 与两个 GLSL 文件，确认常驻插件创建一次程序和 VBO。
2. 在战斗中切换运行时开关两次，记录关闭区间的 draw call 计数。
3. 把玩家舰移出并移回视口，观察剔除和恢复。
4. 结束战斗并重复调用 cleanup，核对资源归零且只输出一次有效统计。

预期结果：

- 画面不被舰船主体错误遮挡；
- 离开视口后插件停止渲染，回到视口后恢复；
- 切换关闭后 drawCalls 停止增长；
- 重复 cleanup 不产生二次删除错误；
- 关闭区间没有 GL 提交，重新开启后渲染恢复；
- cleanup 日志的 frames 与 drawCalls 能解释实际渲染次数。

## 故障分析

- **关闭 shader 后能量场仍在绘制**：开关只在类加载时读取，`render` 没有查询当前值。

    - **解决方案**：让 `render` 查询当前开关值。

- **能量场离开屏幕后仍持续渲染**：`renderRadius` 使用了 `Float.MAX_VALUE`，剔除永远不会发生。

    - **解决方案**：按实际效果范围设置半径。

- **战斗结束后显存持续增长**：插件没有在 `isCombatOver` 后失效并执行 cleanup。

    - **解决方案**：让结束条件触发幂等删除。

- **关闭图形效果后玩法数值变化**：shader 开关参与了伤害或数值计算。

    - **解决方案**：把玩法结算移出渲染分支。

## 总结

常驻能量场的完成条件包括画面、开关、剔除和释放。render 只在当前设置允许时提交，插件位置决定剔除，cleanup 负责幂等删除；性能计数才有可信含义。

## 建议参考项

- `BaseCombatLayeredRenderingPlugin`：常驻分层渲染生命周期；
- `BoxConfigs.isShaderEnable`：当前 shader 设置；
- `ShaderProgram.delete` 与 `glDeleteBuffers`：释放 GL 资源。
