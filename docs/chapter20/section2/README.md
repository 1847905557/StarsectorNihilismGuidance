# 使用 GLSL 进阶

用几十条首尾相接的线段拼出来的圆环，镜头一拉近就会露出一圈棱角，缩放时屏幕线宽也忽粗忽细。

把轮廓算式搬进 GLSL 着色器 (shader) 后，Java 端只管递出一个包住图形的矩形，剩下的圆环边缘与抗锯齿过渡全交给片元距离场现算。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C8-S5] 的分层渲染插件、图层与视口剔除；
- [C20-S1] 的局部顶点、客户端顶点数组、OpenGL 状态保存与生命周期；
- 熟悉直接 `FloatBuffer`，能通过 `starsector.log` 定位资源加载与编译报错。

## 让矩形边距跟随视口缩放并将坐标转给片元

方向标的尺寸依然是圆环半径 `80 su`、朝向线长 `120 su`，但这次不再提交圆弧顶点，而是把它们罩在一个本地坐标包围矩形里。由于线条带抗锯齿半像素边缘 (半线宽最高 `1.5 px` 再加 `0.5 px` 过渡区)，覆盖矩形的四条边需要额外留出 `3f * worldPerPixel` 的边距，免得放缩镜头时端点被三角形边界裁扁：

```java
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

顶点着色器 `data/shaders/GearIn_glsl_marker.vert` 用 `#version 120` 声明：`attribute vec2 a_position` 接收矩形六个局部顶点，先原封不动写进 `varying vec2 v_local` 交给光栅化插值，再用 `uniform vec2 u_center` 与 `uniform float u_facing` 将局部顶点绕 Z 轴旋转并平移到世界坐标，交由内建 `gl_ModelViewProjectionMatrix` 转成裁剪空间位置：

```glsl
v_local = a_position;
vec2 rotated = vec2(
        cosine * a_position.x - sine * a_position.y,
        sine * a_position.x + cosine * a_position.y);
vec2 world = u_center + rotated;
gl_Position = gl_ModelViewProjectionMatrix * vec4(world, 0.0, 1.0);
```

## 用片元距离场与 smoothstep 生成轮廓

光栅化插值后，片元着色器 `data/shaders/GearIn_glsl_marker.frag` 里的每个像素都拿到了自己精确的局部坐标 `v_local`。分别算它到圆环中心线的距离，以及到有限线段 `(0, 0)`–`(120, 0)` 的最近距离：

```glsl
float ringDistance = abs(length(v_local) - u_geometry.x);
vec2 nearestOnLine = vec2(clamp(v_local.x, 0.0, u_geometry.y), 0.0);
float lineDistance = length(v_local - nearestOnLine);
```

拿到世界空间下的 `u_worldPerPixel`(来自 `viewport.convertScreenWidthToWorldWidth(1f)`) 之后，把 `2 px` 圆环和 `3 px` 朝向线各自的一半宽度与半像素平滑边 `0.5 * u_worldPerPixel` 换算成世界长度，用 `smoothstep` 计算透明覆盖率，最后对两者取 `max(ringCoverage, lineCoverage)`：

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

取 `max` 而不是相加，能确保圆环和朝向线相交处的透明度只结算一次而不会叠出亮斑；覆盖率 `<= 0.0` 直接 `discard`，其余乘上 `u_color`(其中 `u_color.a` 已经乘了 `viewport.getAlphaMult()`)。

## 在首次渲染时编译、链接并检查句柄

`Global.getSettings().loadText(...)` 在首次进入 `render(...)`(确保当前线程已有有效 OpenGL 上下文) 时读取 `.vert`/`.frag` 源码，随后编译、附着、链接并在出错时打印 InfoLog：

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

链接通过后紧接着查 `a_position` 与六个 uniform 的 location，并检查它们是否都 `>= 0`。注意一点：**即使着色器里声明了某个 uniform，只要没参与最终像素输出就可能被驱动编译器优化掉，查询返回 `-1`**。

每次绘制时先把 VAO 切到 `0`、压栈客户端属性、把 `GL_ARRAY_BUFFER` 绑定设回 `0`，再开启 `program`、关深度写入 (`glDepthMask(false)`) 免得透明边缘挡住后面物体、传齐所有 uniform、用 `GL20.glEnableVertexAttribArray` 与 `glVertexAttribPointer` 提交矩形六个顶点并 `glDrawArrays(GL11.GL_TRIANGLES, 0, 6)`。如果日后还要读贴图，则在着色器声明 `sampler2D`，Java 端配好 `glActiveTexture`、`glBindTexture` 与 `glUniform1i(samplerLoc, 0)` 即可。

## 完整最小例

分别保存 Java 类到 `src/data/scripts/gearin/combat/GearInGlslMarker.java`，以及两份着色器到 `data/shaders/` 下对应路径：

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
        if (trackedShip == null) {
            valid = false;
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
        if (!valid) {
            return;
        }
        if (engine == null || trackedShip == null
                || engine.isCombatOver()
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
        if (!valid) {
            return;
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);
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

        boolean vaoAvailable = GLContext.getCapabilities().OpenGL30;
        int previousVao = vaoAvailable ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) : 0;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean matrixPushed = false;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        if (vaoAvailable) {
            GL30.glBindVertexArray(0);
        }
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
            if (vaoAvailable) {
                GL30.glBindVertexArray(previousVao);
            }
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
        return engine == null || engine.getViewport() == null
                ? DIRECTION_LENGTH
                : DIRECTION_LENGTH + engine.getViewport().convertScreenWidthToWorldWidth(3f);
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

### 进游戏核对

1. **构建并登记方向标**：执行 `gradle jar` 确认两份着色器留在 MOD 的 `data/shaders/` 路径，进 `GearIn_test` 战斗后在 Console Commands 登记：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInGlslMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

2. **检查编译与句柄日志**：在 `starsector.log` 核对 `GearIn GLSL marker program ready`，所有 attribute 与 uniform location 均须 `>= 0`。
3. **测试缩放与抗锯齿边缘**：大幅拉远、拉近镜头，圆环应当始终保持 `2 px`、朝向线 `3 px`，边缘平滑、交点没有叠加变亮、四端没有被矩形边裁平；暂停/恢复与重复 `cleanup()` 行为与 [C20-S1] 一致：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

## 常见问题

- **日志提示着色器编译或链接失败**：`.vert`/`.frag` 路径拼写、GLSL `#version 120` 语法有误。
    - **解决方案**：顺着日志里的资源路径与 `InfoLog` 报错行核对源码后重进战斗验证。

- **program 创建成功但屏幕上看不见方向标**：某项 location 为 `-1`，或 `glVertexAttribPointer` 提交前没有把 `GL_ARRAY_BUFFER` 设为 `0`。
    - **解决方案**：核对 Java 查询的名称拼写与着色器中是否真正用到了该变量，并确保 `GL_ARRAY_BUFFER` 先绑定 `0`。

- **镜头缩放时圆环外缘被裁掉一块或线宽不稳定**：`u_worldPerPixel` 没读自 `ViewportAPI`，或覆盖矩形未随它放大 `3 px` 边距。
    - **解决方案**：每帧用 `viewport.convertScreenWidthToWorldWidth(1f)` 更新 `u_worldPerPixel` 与覆盖矩形四边。

- **其他画面随后发黑或偏移**：`finally` 里没有完整还原 VAO、`GL_ARRAY_BUFFER`、program 或矩阵与混合状态。
    - **解决方案**：核对压栈与 `finally` 还原顺序，确保 `glPopClientAttrib()` 在还原 `previousVao` 之前执行。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：按 MOD 相对路径读取两份着色器源码；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：方向标的分层渲染生命周期；
- `com.fs.starfarer.api.combat.ViewportAPI`：提供画面 Alpha 与屏幕到世界单位的换算；
- `org.lwjgl.opengl.GL20`：编译、链接、uniform、attribute 与 program 接口；
- `org.lwjgl.opengl.GL15`：读取和设置 `GL_ARRAY_BUFFER` 绑定；
- `org.lwjgl.opengl.GL11`：三角形绘制、混合、深度写入及状态栈。
