# 用 BoxUtil 制作跟随舰船旋转的纹理投影

纹理投影需要同时提交模型矩阵、世界视口矩阵和纹理单元。本节用 BoxUtil 的 BUtilGlobalData UBO 取得视口变换，再把舰船位置与 facing 写入 modelMatrix。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 绑定 BoxUtil 的全局矩阵 UBO，并检查 block 名称与 binding；
- 用可复用的模型矩阵让投影跟随舰船位置和 facing；
- 把纹理绑定到明确的纹理单元，并在错误时定位 sampler 状态。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 建立跟随舰船的纹理投影

青色纹理投影固定在玩家舰下方，舰船转向时投影同步旋转；相机缩放和移动不会让投影滑离世界坐标。

## 文件与 API 链路

### 纹理投影提交链职责速查表

| 对象或 API | 职责 |
| --- | --- |
| `ShaderProgram.getUBOIndex` | 绑定全局矩阵块 |
| `modelMatrix` | 把局部坐标变换到世界坐标 |
| `sampler2D` | 读取纹理单元 |
| `FloatBuffer` | 复用，避免 render 内分配 |

### 声明 BoxUtil 全局矩阵块

**插入位置：\[GearIn\_shader\_visual.vert 顶部\]。** 让顶点阶段取得游戏视口矩阵。

```glsl
layout (std140) uniform BUtilGlobalData {
    mat4 gameViewport;
    vec4 gameScreenBorder;
};
```

完成信号：相机移动与缩放后投影仍锚定世界。

### 缓存 UBO 与 uniform location

**插入位置：\[GearInShaderVisual.createProgram()\]。** 创建程序后一次查询所有位置。

```java
program.uboLocation = new int[] {
    program.getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding())
};
```

完成信号：日志中的 location 均非 -1。

### 按舰船坐标建立模型矩阵

**插入位置：\[render() 提交前\]。** 把 location 与 facing 写进复用缓冲。

```java
matrixBuffer = buildAnchoredMatrix(ship.getLocation(), ship.getFacing(), matrixBuffer);
```

完成信号：舰船转向时纹理同角度旋转。

### 绑定纹理单元并提交采样器

**插入位置：\[render() 的 program.active() 内\]。** 让 sampler 值与实际纹理单元一致。

```java
GL13.glActiveTexture(GL13.GL_TEXTURE0);
GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
GL20.glUniform1i(program.location[3], 0);
```

完成信号：投影显示纹理内容而非纯色或黑块。

## 排查关键故障

下面是**局部故障片段**：

```java
GL13.glActiveTexture(GL13.GL_TEXTURE1);
GL20.glUniform1i(texLocation, 0);
```

实际症状：程序有效、几何可见，但采样结果为黑色或来自其他效果的纹理。

原因：纹理绑定在单元 1，sampler 却读取单元 0。

下面是**局部修正片段**：

```java
GL13.glActiveTexture(GL13.GL_TEXTURE0);
GL20.glUniform1i(texLocation, 0);
```

复验：统一单元号后旋转舰船，确认纹理内容与投影一起转动。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInShaderVisual

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
 * 完整最小例：ShaderProgram、UBO、uniform、纹理与矩阵提交。
 *
 *
 * - 程序对象：ShaderUtil.createShaderVFFormPath(名称, vert 路径, frag 路径) 按路径创建
 *   程序 (成熟实例消费实证)，包装进 org.boxutil.units.standard.ShaderProgram；创建后立即用
 *   getUniformIndex / getUBOIndex 查询并保存 location(实例规范
 *   "创建后必须保存后续渲染所需的 uniform、attribute 或 UBO location")；
 * - UBO：GLSL 侧声明 layout(std140) uniform BUtilGlobalData { mat4 gameViewport;
 *   vec4 gameScreenBorder; }(成熟实例同款声明)，Java 侧
 *   getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding()) 绑定；gameViewport
 *   是 BoxUtil 全局矩阵 UBO 供给的世界→屏幕矩阵，取代 C19-S1 的手动 glOrtho；
 *   块名必须与 GLSL 逐字一致 (大小写敏感)；
 * - uniform：标量/向量 (glUniform1f/2f/4f)、采样器 (glUniform1i 值=纹理单元号)、
 *   矩阵 (glUniformMatrix4(loc, false, 列主序 FloatBuffer)，transpose 必须 false)；
 *   名称与类型必须与 GLSL 声明一致 (C19-S1 硬规则 6)：拼错 → getUniformIndex 返回
 *   -1 → 提交被静默忽略且无日志；
 * - 纹理：SettingsAPI.getSprite 取引擎必载贴图 graphics/fx/particlealpha32sq.png
 *   (引擎启动期进入加载队列；贴图取得方式见 C17-S10)，
 *   SpriteAPI.getTextureId() 取 GL 纹理 id，glActiveTexture(GL_TEXTURE0) +
 *   glBindTexture(GL_TEXTURE_2D, id) + glUniform1i(texLoc, 0) 完成单元绑定与采样器
 *   提交 (成熟实例实证)；
 * - 矩阵：modelMatrix 是 2D 刚性变换 (旋转+平移)，列主序 16 float，布局同成熟实例
 *   maintainAnchoredFloatBuffer；每帧复用
 *   FloatBuffer(clear/put/flip)；
 * - 门禁：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 共同决定 shader 路径
 *   可用性 (C17-S10)；isBaseGL43Supported() 只是 GL 能力探测，不能替代
 *   ShaderCore.isValid()(实例陷阱)；门禁失败 → 记录日志并自废，
 *   不创建程序；
 * - 生命周期：每场战斗 init 创建程序与 VBO(GL 上下文就绪)，render 内 active()/
 *   close() 配对提交 (实例规范 "active() 后必须按 close() 关闭")，
 *   cleanup() 内 program.delete() + glDeleteBuffers，禁止跨战斗缓存 (C7-S2/C8-S6
 *   延续；实例规范 "资源清理归创建者")；isValid() 使用前检查
 *   (实例陷阱：失效程序继续绑定 → 输出错位或状态污染)。
 */
public class GearInShaderVisual extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderVisual.class);

    /** shader 资源路径 (MOD 根目录相对路径，必须用 /；C19-S2 硬规则 1 延续)。 */
    private static final String VERT_PATH = "data/shaders/GearIn_shader_visual.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_shader_visual.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致 (大小写敏感)。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 四边形世界尺寸 (局部坐标 [-1,1] × size/2)。 */
    private static final float QUAD_WIDTH = 160f;
    private static final float QUAD_HEIGHT = 80f;

    /** 演示寿命 (秒)：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 30f;

    /** 交错顶点布局：位置 (2 float) + UV(2 float)，每顶点 4 个 float。 */
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
        // BoxUtil shader 路径可用性判据见 C17-S10；失败即自废，不创建程序
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
        advance(0f); // 立即同步一次位置，避免首帧被剔除 (剔除依据是承载实体的位置)
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
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()(C8-S6 硬规则 3)
            return;
        }
        // 剔除依据是承载实体的位置 (C8-S6 硬规则 4)：跟随玩家舰船
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
            return; // 程序失效不得继续绑定 (实例陷阱)
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换 (旋转 + 平移)，列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = engine.getPlayerShip().getLocation();
        float facing = engine.getPlayerShip().getFacing();
        matrixBuffer = buildAnchoredMatrix(center, facing, matrixBuffer);

        // attribute：VBO 绑定 + long 偏移 (C19-S2 同款姿势；游戏自带 LWJGL 的
        // glVertexAttribPointer 另有 FloatBuffer client 指针重载；本单元使用 VBO 偏移)
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

        // 颜色 uniform：外蓝内橙 (贴图红通道在两者之间插值)
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

::: details 完整成品：GearIn_shader_visual

```glsl
#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.25));
    vec4 texSample = texture(tex, uv);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
```

:::

::: details 完整成品：GearIn_shader_visual

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

1. 编译 Java fixture 与两个 GLSL 文件，确认 UBO 和 sampler 名称一致。
2. 在战斗中移动并旋转玩家舰，观察投影中心和方向。
3. 缩放相机并横移视口，确认投影仍固定在世界坐标。
4. 故意把纹理切换到错误单元，记录黑块症状后恢复单元 0。

预期结果：

- 相机移动与缩放后投影仍锚定世界；
- 日志中的 location 均非 -1；
- 舰船转向时纹理同角度旋转；
- 投影显示纹理内容而非纯色或黑块；
- UBO、uniform 和 sampler 提交没有 `-1` location 或 GL 错误日志。

## 故障分析

- **几何可见但采样结果为黑色**：纹理绑定单元与 sampler 读取单元不一致。

    - **解决方案**：统一单元号。

- **投影随屏幕移动而漂移**：shader 没有使用 `BUtilGlobalData` 的 `gameViewport`。

    - **解决方案**：绑定正确 UBO。

- **投影旋转方向相反**：模型矩阵的正弦项符号或列主序提交错误。

    - **解决方案**：修正矩阵布局。

- **投影首帧出现在原点**：注册后没有立即同步宿主实体位置。

    - **解决方案**：创建时先提交一次位置和矩阵。

## 总结

局部四边形经 modelMatrix 到世界坐标，再经 gameViewport 到裁剪空间，三个坐标阶段必须一致；纹理单元只负责给片元提供内容。

## 建议参考项

- `BUtilGlobalData`：BoxUtil 全局视口 UBO；
- `ShaderProgram.getUBOIndex`：绑定 uniform block；
- `glUniformMatrix4`：提交列主序模型矩阵。
