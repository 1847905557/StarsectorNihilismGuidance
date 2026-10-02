# 使用 GLSL 进阶

本节沿用 [C20-S1] 的方向标，让 GLSL 1.20 在矩形内部生成圆环与朝向线。Java 提交顶点和舰船状态；您可以分别观察 vertex shader 如何放置矩形，以及 fragment shader 如何决定哪些像素可见。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `attribute`、`uniform` 和 `varying` 在两个 shader 阶段间的数据流；
- 用距离函数和 `smoothstep` 生成像素宽度稳定的圆环与朝向线；
- 编译、链接、使用并释放 shader program；
- 从客户端 `FloatBuffer` 提交覆盖矩形，并更新舰船状态和视口参数；
- 核对方向标生命周期和 OpenGL 状态恢复。

## 前置知识

- [C20-S1] 的局部顶点、客户端顶点数组、模型视图矩阵和方向标生命周期；
- [C8-S7] 的分层渲染插件、图层与视口剔除；
- 理解 Java 类、方法和直接缓冲区，并能从 `starsector.log` 定位加载错误。

## 让矩形覆盖方向标

方向标仍是半径 `80 su` 的圆环，以及局部正 X 轴上长 `120 su` 的朝向线。Java 提交由两个三角形组成的矩形，共 `6` 个局部顶点。矩形只规定片元计算的范围；圆环、线段及透明边缘由 fragment shader 在这个范围内生成。

Java 为矩形保留一个直接 `FloatBuffer`，在 `init(...)` 创建，在每帧绘制前按视口像素大小更新边界。它把顶点交给 `a_position`，把舰船位置、朝向、形状尺寸、线宽、像素比例和颜色交给 uniform，再发起一次 `GL_TRIANGLES` 绘制。放大的视口使 `1 px` 对应更少的世界单位；覆盖矩形随这一比例留出边距，圆环和朝向线的平滑边缘不会在矩形边界被裁掉。

## 组织两个 shader 阶段

`data/shaders/GearIn_glsl_marker.vert` 和 `data/shaders/GearIn_glsl_marker.frag` 是相对 MOD 根目录的资源路径。两个文件都以 `#version 120` 开头，`SettingsAPI.loadText(...)` 读取它们的源码。vertex shader 对每个顶点执行一次；三角形光栅化后，fragment shader 对覆盖区域内的片元执行。GLSL 1.20 用 `attribute` 接收逐顶点输入，用 `uniform` 接收一次绘制内共享的值，用两阶段同名同类型的 `varying` 传递插值后的局部坐标。

vertex shader 先把 `a_position` 赋给 `v_local`，再按舰船朝向旋转并平移到世界位置。`radians` 将角度转为三角函数需要的弧度，`vec2` 保存局部和世界二维坐标，`vec4` 将世界坐标交给内建矩阵转换到裁剪空间。以下是局部 GLSL 片段：

```glsl
v_local = a_position;
vec2 rotated = vec2(
        cosine * a_position.x - sine * a_position.y,
        sine * a_position.x + cosine * a_position.y);
vec2 world = u_center + rotated;
gl_Position = gl_ModelViewProjectionMatrix * vec4(world, 0.0, 1.0);
```

三角形光栅化时，`v_local` 在顶点之间插值，fragment shader 因而能取得每个片元对应的局部坐标。圆环转动后外形不变，朝向线随舰船旋转；两种形状共用这次顶点变换。

## 用局部距离确定形状

片元到圆环中心线的距离为 `abs(length(v_local) - u_geometry.x)`。`length` 求向量长度，`abs` 把圆环内外两侧都转为非负距离。朝向线限定在局部 `(0, 0)` 到 `(120, 0)`；`clamp` 将当前 X 坐标限制在线段两端之间，再用 `length` 求片元到最近点的距离。以下是局部 GLSL 片段：

```glsl
float ringDistance = abs(length(v_local) - u_geometry.x);
vec2 nearestOnLine = vec2(clamp(v_local.x, 0.0, u_geometry.y), 0.0);
float lineDistance = length(v_local - nearestOnLine);
```

`u_geometry` 的两个分量分别为半径 `80 su` 和长度 `120 su`。线段两端按距离形成圆头；圆环与线段在正 X 轴附近相交。两个形状的覆盖率取较大值，因此交点只混合一次。

## 把像素线宽换算为透明度

`ViewportAPI.convertScreenWidthToWorldWidth(1f)` 给出水平方向 `1 px` 对应的世界单位数。Java 把这个值作为 `u_worldPerPixel` 提交，把 `2 px`、`3 px` 分别作为 `u_widthPixels.x`、`u_widthPixels.y` 提交。片元 shader 将半线宽和半像素边缘都换算成世界单位。以下是局部 GLSL 片段：

```glsl
float edge = 0.5 * u_worldPerPixel;
float ringHalfWidth = 0.5 * u_widthPixels.x * u_worldPerPixel;
float lineHalfWidth = 0.5 * u_widthPixels.y * u_worldPerPixel;
float ringCoverage = 1.0 - smoothstep(
        ringHalfWidth - edge, ringHalfWidth + edge, ringDistance);
float lineCoverage = 1.0 - smoothstep(
        lineHalfWidth - edge, lineHalfWidth + edge, lineDistance);
float coverage = max(ringCoverage, lineCoverage);
```

`smoothstep` 让边缘 Alpha 随距离平滑下降。覆盖率为零的片元由 `discard` 放弃，其余片元输出 `vec4(u_color.rgb, u_color.a * coverage)`。`.rgb` 选取颜色分量，`u_color.a` 包含方向标透明度和视口 Alpha；`GL_SRC_ALPHA` 与 `GL_ONE_MINUS_SRC_ALPHA` 把可见部分叠到战斗画面。

## 创建、编译和链接 program

`init(...)` 创建矩形缓冲区并同步承载实体位置。首次进入 `render(...)` 时，OpenGL 上下文已经用于本次绘制，`buildProgram()` 才读取 shader 文件并创建资源。编译 helper 为 vertex 和 fragment shader 分别提交源码；编译失败时记录资源路径与 InfoLog。以下是局部 Java 片段：

```java
int shader = GL20.glCreateShader(type);
GL20.glShaderSource(shader, source);
GL20.glCompileShader(shader);
if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
    LOG.error("GearIn GLSL marker: compile failed for " + path + ": "
            + GL20.glGetShaderInfoLog(shader, 4096));
    GL20.glDeleteShader(shader);
    return 0;
}
```

两份源码编译成功后，Java 把 shader 附到同一个 program，再检查链接状态。成功链接后查询一个 attribute 和六个 uniform 的 location，任何查询结果小于 `0` 都使本实例失效。以下是局部 Java 片段：

```java
GL20.glAttachShader(program, vertexShader);
GL20.glAttachShader(program, fragmentShader);
GL20.glLinkProgram(program);
positionLocation = GL20.glGetAttribLocation(program, "a_position");
centerLocation = GL20.glGetUniformLocation(program, "u_center");
facingLocation = GL20.glGetUniformLocation(program, "u_facing");
colorLocation = GL20.glGetUniformLocation(program, "u_color");
geometryLocation = GL20.glGetUniformLocation(program, "u_geometry");
widthsLocation = GL20.glGetUniformLocation(program, "u_widthPixels");
worldPerPixelLocation = GL20.glGetUniformLocation(program, "u_worldPerPixel");
```

链接失败的 InfoLog 指向 program；读取、编译、链接或位置查询失败时，当前实例释放已经创建的句柄并停止绘制。成功创建的 program 与两份 shader 由 `cleanup()` 释放，句柄随后归零；重复清理不会再次删除。

## 复用客户端矩形缓冲区

`init(...)` 只分配一次可容纳 `6` 个二维顶点的直接缓冲区。绘制时用 `3 px` 的世界单位长度作为边距，更新两个三角形的顶点；这个边距大于朝向线 `1.5 px` 的半宽和 `0.5 px` 的平滑过渡区。以下是局部 Java 片段：

```java
coverageVertices = BufferUtils.createFloatBuffer(12);

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
```

`clear()` 使缓冲区可重写，`flip()` 把有效范围设为刚写入的 `12` 个浮点数。`a_position` 是每个顶点的二维输入。提交客户端 `FloatBuffer` 前将 `GL_ARRAY_BUFFER` 绑定设为 `0`，再为 attribute 指定指针；`GL_TRIANGLES` 一次处理两个三角形。以下是局部 Java 片段：

```java
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
GL20.glEnableVertexAttribArray(positionLocation);
GL20.glVertexAttribPointer(positionLocation, 2, false, 0, coverageVertices);
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
```

## 用 uniform 更新舰船状态

`u_center`、`u_facing`、`u_color`、`u_geometry`、`u_widthPixels` 和 `u_worldPerPixel` 在一次绘制调用内保持相同。`advance(...)` 在未暂停时同步承载实体位置；`render(...)` 使用该位置、当前舰船朝向和视口数据提交 uniform。`getAlphaMult()` 乘入颜色 Alpha，使方向标随战斗画面淡出。以下是局部 Java 片段：

```java
GL20.glUseProgram(program);
GL20.glUniform2f(centerLocation, entity.getLocation().x, entity.getLocation().y);
GL20.glUniform1f(facingLocation, trackedShip.getFacing());
GL20.glUniform4f(colorLocation, 0.1f, 0.9f, 1f,
        0.75f * viewport.getAlphaMult());
GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
GL20.glUniform2f(widthsLocation, RING_LINE_WIDTH, DIRECTION_LINE_WIDTH);
GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
```

## 绘制圆环与朝向线

一次矩形绘制运行两个 shader 阶段。圆环和朝向线的宽度由片元计算控制，`glDepthMask(false)` 防止透明边缘写入深度。渲染层保持 `ABOVE_SHIPS_AND_MISSILES_LAYER`，`getRenderRadius()` 返回 `120f`；矩形额外边距随视口变化，战斗内的可见性还需在不同缩放下观察。

`render(...)` 保存进入方法时的 program、`GL_ARRAY_BUFFER` 绑定和矩阵模式，属性栈保存混合、颜色、深度写入、纹理启用与客户端顶点数组状态。`finally` 恢复这些状态；同一战斗帧中的后续绘制因此继续使用它们原有的设置。`advance(...)` 先判断战斗结束、舰船离场或死亡，再处理暂停；实例失效后由 `isExpired()` 通知引擎清理。

## 纹理采样的独立关系

需要显示纹理时，fragment shader 可以声明 `sampler2D`，并以 UV 坐标采样：

```glsl
uniform sampler2D u_texture;
varying vec2 v_uv;

void main() {
    gl_FragColor = texture2D(u_texture, v_uv);
}
```

Java 先以 `glActiveTexture(GL_TEXTURE0)` 选择纹理单元 `0`，再用 `glBindTexture(GL_TEXTURE_2D, textureId)` 绑定贴图，并通过 `glUniform1i(textureLocation, 0)` 让 sampler 读取同一单元。顶点阶段还需要提供与纹理对应的 UV；本节方向标的完整成品使用颜色 uniform。

## 完整最小例

以下资源共同完成可运行的方向标。战斗入口取得有效 `ShipAPI` 后，调用 `engine.addLayeredRenderingPlugin(new GearInGlslMarker(ship))` 注册实例。

::: details 完整成品：GearInGlslMarker

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

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 使用 GLSL 1.20 和客户端顶点数组绘制跟随舰船的方向标。
 * init() 创建可复用的覆盖矩形缓冲区，首次 render() 创建 program，
 * cleanup() 释放本实例创建的全部 shader 资源。
 */
public class GearInGlslMarker extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInGlslMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_glsl_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_glsl_marker.frag";
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;
    private static final float RING_LINE_WIDTH = 2f;
    private static final float DIRECTION_LINE_WIDTH = 3f;

    private final ShipAPI trackedShip;

    private CombatEngineAPI engine;
    private FloatBuffer coverageVertices;
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

    public GearInGlslMarker(ShipAPI trackedShip) {
        this.trackedShip = trackedShip;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (engine == null || trackedShip == null) {
            valid = false;
            LOG.error("GearIn GLSL marker: combat context or tracked ship missing");
            return;
        }

        coverageVertices = BufferUtils.createFloatBuffer(12);

        entity.getLocation().set(trackedShip.getLocation());
        LOG.info("GearIn GLSL marker init: ship=" + trackedShip.getId());
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

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn GLSL marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn GLSL marker: compile failed for " + path + ": "
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
            LOG.error("GearIn GLSL marker: could not create program");
            return false;
        }
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn GLSL marker: link failed: "
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
        if (positionLocation < 0 || centerLocation < 0
                || facingLocation < 0 || colorLocation < 0
                || geometryLocation < 0 || widthsLocation < 0
                || worldPerPixelLocation < 0) {
            LOG.error("GearIn GLSL marker: attribute or uniform location missing");
            return false;
        }
        LOG.info("GearIn GLSL marker program ready: position=" + positionLocation
                + " center=" + centerLocation + " facing=" + facingLocation
                + " color=" + colorLocation + " geometry=" + geometryLocation
                + " widths=" + widthsLocation + " worldPerPixel=" + worldPerPixelLocation);
        return true;
    }

    @Override
    public void advance(float amount) {
        if (!valid || engine == null || trackedShip == null || entity == null) {
            valid = false;
            return;
        }
        if (engine.isCombatOver()
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
        if (!valid || entity == null || coverageVertices == null || viewport == null) {
            return;
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);
        if (!Float.isFinite(worldPerPixel) || worldPerPixel <= 0f) {
            return;
        }
        updateCoverageVertices(worldPerPixel);
        if (!programAttempted) {
            programAttempted = true;
            try {
                if (!buildProgram()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException e) {
                LOG.error("GearIn GLSL marker: program creation failed", e);
                cleanup();
                return;
            }
        }
        if (program == 0) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean matrixPushed = false;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
            GL20.glEnableVertexAttribArray(positionLocation);
            GL20.glVertexAttribPointer(positionLocation, 2, false, 0, coverageVertices);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            if (matrixPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
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
        if (cleaned) {
            return;
        }
        cleaned = true;
        valid = false;
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
        coverageVertices = null;
        LOG.info("GearIn GLSL marker cleanup: ship="
                + (trackedShip == null ? "null" : trackedShip.getId()));
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return DIRECTION_LENGTH;
    }
}
```

:::

::: details 完整成品：GearIn_glsl_marker.vert

```glsl
#version 120

attribute vec2 a_position;

uniform vec2 u_center;
uniform float u_facing;
varying vec2 v_local;

void main() {
    v_local = a_position;
    float angle = radians(u_facing);
    float cosine = cos(angle);
    float sine = sin(angle);
    vec2 rotated = vec2(
            cosine * a_position.x - sine * a_position.y,
            sine * a_position.x + cosine * a_position.y);
    vec2 world = u_center + rotated;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(world, 0.0, 1.0);
}
```

:::

::: details 完整成品：GearIn_glsl_marker.frag

```glsl
#version 120

uniform vec4 u_color;
uniform vec2 u_geometry;
uniform vec2 u_widthPixels;
uniform float u_worldPerPixel;
varying vec2 v_local;

void main() {
    float ringDistance = abs(length(v_local) - u_geometry.x);
    vec2 nearestOnLine = vec2(clamp(v_local.x, 0.0, u_geometry.y), 0.0);
    float lineDistance = length(v_local - nearestOnLine);

    float edge = 0.5 * u_worldPerPixel;
    float ringHalfWidth = 0.5 * u_widthPixels.x * u_worldPerPixel;
    float lineHalfWidth = 0.5 * u_widthPixels.y * u_worldPerPixel;
    float ringCoverage = 1.0 - smoothstep(
            ringHalfWidth - edge, ringHalfWidth + edge, ringDistance);
    float lineCoverage = 1.0 - smoothstep(
            lineHalfWidth - edge, lineHalfWidth + edge, lineDistance);
    float coverage = max(ringCoverage, lineCoverage);
    if (coverage <= 0.0) {
        discard;
    }
    gl_FragColor = vec4(u_color.rgb, u_color.a * coverage);
}
```

:::

## 验证方案

前置数据：启用中的 GearIn MOD 包含 `GearInGlslMarker` 和两份 `GearIn_glsl_marker` shader 资源；`GearIn_test` 战斗入口能够取得当前舰船并注册该插件。

1. 在 MOD 工程执行 `gradle jar`，确认 JAR 包含 `data/scripts/gearin/combat/GearInGlslMarker.class`，两份 shader 资源位于 MOD 的 `data/shaders/`。
2. 启动游戏并进入 `GearIn_test` 战斗，为玩家舰注册 `GearInGlslMarker`。
3. 检查 program 创建日志中的 attribute 与 uniform location。
4. 移动并转向玩家舰，观察圆环中心和朝向线方向。
5. 放大和缩小视口，观察圆环与朝向线的线宽、平滑边缘及端点。
6. 暂停战斗并记录方向标位置，恢复后继续移动舰船。
7. 同时观察舰船 Sprite、HUD 与其他战场画面，再使舰船离场或死亡。
8. 结束战斗并重新进入一场战斗，核对清理日志与新实例的创建日志；对测试实例重复调用 `cleanup()`，检查日志与资源状态。

预期结果：

- 玩家舰中心出现半径 `80 su`、由片元 shader 生成的青色半透明圆环，线宽为 `2 px`；
- 长度 `120 su`、线宽 `3 px` 的朝向线随舰船转向，圆环中心随移动更新；
- 缩放后两种线宽保持对应的屏幕像素大小，边缘平滑，矩形边界没有裁掉圆环或线端；
- 暂停期间方向标保持最后位置，恢复后继续跟随舰船；
- 日志显示 attribute 与六个 uniform 的 location 均不小于 `0`，编译和链接成功；
- 舰船 Sprite、HUD 与其他战场画面的颜色、纹理、混合和位置保持原有表现；
- 舰船离场、死亡或战斗结束后方向标失效，每个实例只记录一次清理；重复 `cleanup()` 不重复删除 program 与 shader；
- 新战斗创建新的实例及 shader 资源，视口剔除使用 `120 su` 渲染半径。

## 故障分析

- **方向标不可见，日志出现 shader 编译或链接错误**：`.vert`、`.frag` 源码或资源路径使 program 无法建立。
    - **解决方案**：按日志中的资源路径与 InfoLog 核对源码、`#version 120` 和 MOD 内文件位置，修正后重新进入战斗。

- **program 已建立但方向标不可见**：attribute 或 uniform 的 location 无效，或者 `GL_ARRAY_BUFFER` 仍指向其他缓冲区。
    - **解决方案**：核对 GLSL 声明与 Java 查询名称，并在客户端 `FloatBuffer` 指针提交前绑定 `GL_ARRAY_BUFFER` 为 `0`。

- **圆环位置正确但朝向线固定**：`u_facing` 没有取得当前舰船朝向，或 vertex shader 旋转式中的正弦符号不正确。
    - **解决方案**：核对 `trackedShip.getFacing()` 的提交和 vertex shader 的二维旋转计算。

- **缩放后边缘被截断或线宽变化**：覆盖矩形的边距或 `u_worldPerPixel` 没有使用当前视口比例。
    - **解决方案**：核对 `convertScreenWidthToWorldWidth(1f)` 的返回值、矩形四边的 `3 px` 边距和片元 shader 的线宽换算。

- **其他战场画面随后变色或错位**：program、客户端数组、矩阵或混合状态在方向标绘制后仍保持修改值。
    - **解决方案**：核对 `finally` 中属性栈、program、`GL_ARRAY_BUFFER` 和矩阵模式的恢复。

- **舰船消失后仍有方向标或资源未释放**：`advance(...)` 没有将舰船失效转换为插件失效，或 `cleanup()` 未处理全部句柄。
    - **解决方案**：核对战斗、在场与存活判断，再逐项检查 program 和两个 shader 句柄归零。

## 总结

Java 保留 [C20-S1] 的局部坐标与分层插件生命周期，提交一个可复用的覆盖矩形及舰船、视口参数。vertex shader 变换顶点并传递局部坐标，fragment shader 根据距离生成圆环与朝向线，按像素线宽计算平滑的透明边缘。

program、shader 与 OpenGL 状态分别有明确的结束位置。编译和链接失败会留下可定位的日志，绘制结束恢复共享状态，插件失效时释放本实例的资源。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：按 MOD 相对路径读取两份 shader 源码；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：方向标的分层渲染生命周期；
- `com.fs.starfarer.api.combat.ViewportAPI`：提供画面 Alpha 与屏幕到世界单位的换算；
- `org.lwjgl.opengl.GL20`：编译、链接、uniform、attribute 与 program 接口；
- `org.lwjgl.opengl.GL15`：读取和设置 `GL_ARRAY_BUFFER` 绑定；
- `org.lwjgl.opengl.GL11`：三角形绘制、混合、深度写入及状态栈。
