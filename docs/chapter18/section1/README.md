# 用 GLSL 制作脉冲扫描环

扫描环是一个适合建立 shader 数据流的独立效果：顶点阶段传递 UV，片元阶段只凭 UV 和时间生成移动圆环。本节从局部四边形到透明度输出逐步完成画面。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 建立 Java、顶点 shader 与片元 shader 之间的完整数据流；
- 用累计战斗时间生成周期半径，并在暂停时冻结；
- 用中心距离和柔边宽度生成透明扫描环。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 建立扫描环的数据流

玩家舰周围出现从内向外扩张并周期重置的青色扫描环；相机移动时效果跟随世界坐标，暂停时动画冻结。

## 文件与 API 链路

### 扫描环数据流职责速查表

| 阶段 | 职责 |
| --- | --- |
| Java | 提交四边形、时间和颜色 |
| 顶点 shader | 把局部坐标转换到裁剪空间并传递 UV |
| 片元 shader | 计算中心距离、环宽和透明度 |

### 从顶点阶段传递 UV

**插入位置：\[GearIn\_basic\_shader.vert 的 main()\]。** 让每个片元取得四边形内位置。

```glsl
v_uv = a_uv;
gl_Position = vec4(a_position, 0.0, 1.0);
```

完整顶点 shader 还会用 `u_offset` 把局部四边形移动到玩家舰世界坐标，并交给当前正交投影矩阵。

完成信号：片元阶段能读取 0–1 范围的 `v_uv`。

### 计算片元到中心的距离

**插入位置：\[GearIn\_basic\_shader.frag 的 main() 开头\]。** 把二维 UV 压成一个径向坐标。

```glsl
float distanceToCenter = distance(v_uv, vec2(0.5, 0.5));
```

完成信号：中心为 0，四边形边缘接近 0.5。

### 让目标半径随时间循环

**插入位置：\[片元 shader 的 pulse 计算处\]。** 用 fract 生成 0–0.5 的周期半径。

```glsl
float radius = fract(u_time * 0.35) * 0.5;
```

完成信号：扫描环持续向外扩张并回到中心。

### 用 smoothstep 形成窄环

**插入位置：\[片元 shader 输出前\]。** 根据距离与半径差生成柔和边缘。

```glsl
float ring = 1.0 - smoothstep(0.0, 0.025,
        abs(distanceToCenter - radius));
gl_FragColor = vec4(u_color.rgb, ring * u_color.a);
```

完成信号：画面出现窄环而非实心圆。

## 排查关键故障

下面是**局部故障片段**：

```glsl
float radius = u_time * 0.35;
```

实际症状：扫描环只出现一次，约两秒后永久离开四边形。

原因：半径持续增长，没有回到 0–0.5 的有效 UV 范围。

下面是**局部修正片段**：

```glsl
float radius = fract(u_time * 0.35) * 0.5;
```

复验：观察十秒，确认扫描环至少完成三次循环。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

## 验证方案

1. 编译 Java fixture 并验证两个 shader 文件。
2. 进入战斗，让渲染器跟随玩家舰运行 10 秒，记录至少 3 次扫描循环。
3. 暂停 3 秒，确认环半径冻结；恢复后从原相位继续。
4. 移动玩家舰和相机，确认四边形仍以舰船世界坐标为中心。

预期结果：

- 片元阶段能读取 0–1 范围 fragUV；
- 中心为 0，边中点距离为 0.5，四角距离约为 0.7071；当前最大半径约 0.525，只覆盖圆形径向区域；
- 扫描环持续向外扩张并回到中心；
- 画面出现窄环而非实心圆；
- shader 编译、程序链接和 uniform 查询均没有错误日志。

## 故障分析

- **扫描环只出现一次**：半径持续增长，没有回到有效 UV 范围。

    - **解决方案**：恢复 `fract`。

- **画面显示为实心圆**：片元条件使用 `distanceToCenter < radius`，生成了圆盘。

    - **解决方案**：改为比较 `abs(distanceToCenter - radius)`。

- **扫描环变成椭圆**：四边形宽高不同，UV 距离没有按比例修正。

    - **解决方案**：使用正方形 VBO 或校正 UV 比例。

- **暂停时扫描仍在动画**：Java 继续提交真实时间或累加 `elapsed`。

    - **解决方案**：只在战斗未暂停时推进 `u_time`。

## 总结

这个效果的数据流只有三项：顶点提供 UV，Java 提供时间与颜色，片元阶段把中心距离变成环形透明度。每个值都有明确坐标范围，故障也能沿数据流定位。

## 建议参考项

- GLSL `varying`／`in`：顶点到片元插值；
- `fract`：生成周期相位；
- `smoothstep`：建立柔和环边。
::: details 完整成品：GearInBasicShaderRenderer

```java
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
 * 完整最小例：GLSL 顶点到片元数据流 (attribute / uniform / varying / gl_Position / gl_FragColor)。
 *
 * 数据流事实：
 * - 顶点着色器输入分两类：attribute(每顶点，Java 侧 VBO + glVertexAttribPointer
 *   喂入，位置必须在 glLinkProgram 前用 glBindAttribLocation 固定) 与 uniform(本次
 *   绘制内所有顶点共享，Java 侧 glGetUniformLocation + glUniform* 提交)；
 * - 顶点着色器输出 gl_Position(裁剪空间坐标) 与 varying(每顶点写出、光栅化时在
 *   片元间插值)；片元着色器读插值后的 varying 并写 gl_FragColor(GLSL 1.10 方言)；
 * - 本节 render() 里按 GraphicsLib renderForeground() 的同款姿势 (org.dark.shaders.util.ShaderLib)
 *   用 glOrtho(viewport) 设置世界→屏幕正交投影，顶点着色器用
 *   gl_ModelViewProjectionMatrix 应用它；
 * - 顶点数据走 VBO + long 偏移：游戏自带 LWJGL 的 GL20.glVertexAttribPointer
 *   提供 FloatBuffer client 指针重载，但要求当前没有绑定 VBO；本单元为避免
 *   client 指针与 VBO 状态冲突，使用 glBindBuffer(GL_ARRAY_BUFFER, vbo) 后的 long 偏移；
 * - 能力基线：GLSL 需要 OpenGL 2.0 上下文 (GraphicsLib ShaderLib.init() 的 OpenGL20
 *   门禁；areShadersAllowed() 的 javadoc 硬措辞 "Do not use shader code if this
 *   returns false! Otherwise the game will crash!")；
 * - 编译/链接失败检查：GL_COMPILE_STATUS / GL_LINK_STATUS 为 FALSE 时读取
 *   glGetShaderInfoLog / glGetProgramInfoLog 并删除对象 (GraphicsLib createShader() /
 *   loadShader() 同款姿势)；
 * - 资源生命周期沿用 C7-S6 硬规则 2/3：GL 资源在 cleanup() 释放；战斗结束
 *   destroyInstance 不调用 cleanup()，插件在 advance() 里主动失效 (isExpired() ->
 *   true) 驱动引擎移除；本示例设 LIFETIME 秒寿命后自清；
 * - 插件、引擎引用禁止跨战斗缓存 (C6-S3 / C7-S5 / C7-S6 同规)。
 */
public class GearInBasicShaderRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInBasicShaderRenderer.class);

    /** shader 资源路径 (MOD 根目录相对路径，必须用 /)。 */
    private static final String VERT_PATH = "data/shaders/GearIn_basic_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_basic_shader.frag";

    /** 演示四边形半边长 (世界单位)。 */
    private static final float QUAD_HALF_SIZE = 60f;

    /** 演示寿命 (秒)：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 20f;

    /** 交错顶点布局：位置 (2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    private int program = 0;
    private int vboId = 0;
    private int uOffsetLocation = -1;
    private int uTimeLocation = -1;
    private int uColorLocation = -1;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (!createProgram() || !buildQuad()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点 (C7-S6 同款姿势)
        LOG.info("GearIn basic shader init: program=" + program + ", vbo=" + vboId
                + ", u_offset=" + uOffsetLocation + ", u_time=" + uTimeLocation);
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
        uTimeLocation = GL20.glGetUniformLocation(program, "u_time");
        uColorLocation = GL20.glGetUniformLocation(program, "u_color");
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
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()(C7-S6 硬规则 3)
            return;
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
        // 世界→屏幕正交投影：按 GraphicsLib renderForeground() 同款姿势自行设置
        // (glOrtho(viewport) 以世界单位建投影，org.dark.shaders.util.ShaderLib)，
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

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // uniform：本次绘制内所有顶点/片元共享，提交为玩家舰船当前位置 (每帧更新)
        if (uOffsetLocation >= 0) {
            Vector2f center = engine.getPlayerShip().getLocation();
            GL20.glUniform2f(uOffsetLocation, center.x, center.y);
        }
        if (uTimeLocation >= 0) {
            GL20.glUniform1f(uTimeLocation, elapsed);
        }
        if (uColorLocation >= 0) {
            GL20.glUniform4f(uColorLocation, 0.1f, 0.9f, 1f, 0.9f);
        }

        // attribute：VBO 绑定 + long 偏移 (游戏自带 LWJGL 的 glVertexAttribPointer
        // 虽有 FloatBuffer client 指针重载，但本单元走 VBO + long 偏移姿势)
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
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
    }
}
```

:::

::: details 完整成品：GearIn_basic_shader

```glsl
#version 110

varying vec2 v_uv;

uniform float u_time;
uniform vec4 u_color;

void main() {
    float distanceToCenter = distance(v_uv, vec2(0.5, 0.5));
    float radius = fract(u_time * 0.35) * 0.5;
    float ring = 1.0 - smoothstep(0.0, 0.025, abs(distanceToCenter - radius));
    gl_FragColor = vec4(u_color.rgb, ring * u_color.a);
}
```

:::

::: details 完整成品：GearIn_basic_shader

```glsl
#version 110

attribute vec2 a_position;

attribute vec2 a_uv;

uniform vec2 u_offset;

varying vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(a_position + u_offset, 0.0, 1.0);
}
```

:::
