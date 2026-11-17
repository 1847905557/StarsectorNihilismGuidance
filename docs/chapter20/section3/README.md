# 使用 VBO 和 VAO 保存顶点

上一节每到渲染帧，都得把矩形顶点重新从内存递给显卡、顺带重设一遍属性格式；只画几十个简单图元时您可能感觉得不出来，一旦同屏对象多了，这套每帧现搬现算的开销很快就会变成拖累。把静态顶点与读取布局提前封进 VBO 和 VAO 里，真正留在每帧里更新的，就只剩随镜头缩放变化的那一点边界数据了。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C20-S1] 的分层渲染插件与 OpenGL 状态保存；
- [C20-S2] 的 `#version 120` 方向标着色器、覆盖矩形与 `u_worldPerPixel`；
- 熟悉 `starsector.log` 报错排查。

## 把顶点搬入 VBO 并用 VAO 缓存布局

在首次进入 `render(...)` 并检查 `GLContext.getCapabilities().OpenGL30` 通过、链接好 program 后，`buildGeometry()` 创建 VBO 与 VAO 并配置布局：

```java
vaoId = GL30.glGenVertexArrays();
vboId = GL15.glGenBuffers();
if (vaoId == 0 || vboId == 0) {
    LOG.error("GearIn VBO/VAO marker: could not create VAO or VBO");
    return false;
}
GL30.glBindVertexArray(vaoId);
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
GL20.glEnableVertexAttribArray(positionLocation);
GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT, false, 0, 0L);
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
GL30.glBindVertexArray(0);
```

注意三个关键点：

1. 因为镜头缩放时需要更新 `3 px` 边距，这里使用 `GL15.GL_DYNAMIC_DRAW` 上传，且 `glVertexAttribPointer` 末尾的 `0L` 代表 "从当前绑定的 `vboId` 第 0 个字节开始读"；
2. 一旦配置好 `glVertexAttribPointer`，`vaoId` 就已经记住了 "去哪块 VBO、按什么格式读 `a_position`"，所以紧接着解绑 `GL_ARRAY_BUFFER`(设为 `0`) 并不会破坏 VAO 内的布局；
3. 相反，**VAO 并不会保存上下文的通用 `GL_ARRAY_BUFFER` 绑定** —— 因此创建和绘制前仍要先记下 `previousVao` 与 `previousVbo`，并在 `finally` 里恢复它们。

## 绘制阶段只更新缩放边框与 uniform

每次 `render(...)` 绑定 `vaoId` 后，用 `Float.compare(worldPerPixel, lastWorldPerPixel) != 0` 判断镜头比例是否变化，只有变了才 `glBufferSubData(...)` 重传 12 个浮点数，随后照常提交舰船与视口 uniform 并发起一次绘制：

```java
GL30.glBindVertexArray(vaoId);
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
if (Float.compare(worldPerPixel, lastWorldPerPixel) != 0) {
    updateCoverageVertices(worldPerPixel);
    GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, coverageVertices);
    lastWorldPerPixel = worldPerPixel;
}
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
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
```

::: warning 使用自有 VAO 时不要夹杂 glPushClientAttrib

由于本例全程只修改 `vaoId` 自身绑定的属性布局、并未改动外部 VAO 或默认 VAO 上的客户端数组状态，绘制时**不要**在 `previousVao` 下调用 `glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT)` 再在 `vaoId` 绑定期间调用 `glPopClientAttrib()` —— 否则 `glPopClientAttrib()` 会把外部 `previousVao` 的顶点数组状态直接写回我们的 `vaoId`，造成第 2 帧起 `a_position` 指针失效。

:::

## 按依赖逆序释放 GPU 句柄

资源创建半途失败或插件失效时，`cleanup()` 依次删除 `vaoId`、`vboId`、`program` 以及两个 shader 对象并把各自句柄清零，重复调用直接早退。

## 完整最小例

将 `GearInVboVaoMarker` 保存到 `src/data/scripts/gearin/combat/GearInVboVaoMarker.java`，并将配套着色器保存到 `data/shaders/`：

::: details 完整成品：GearInVboVaoMarker

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
public class GearInVboVaoMarker extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInVboVaoMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_vbo_vao_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_vbo_vao_marker.frag";
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;
    private static final float RING_LINE_WIDTH = 2f;
    private static final float DIRECTION_LINE_WIDTH = 3f;

    private final ShipAPI trackedShip;

    private CombatEngineAPI engine;
    private FloatBuffer coverageVertices;
    private float lastWorldPerPixel = Float.NaN;
    private int vaoId;
    private int vboId;
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

    public GearInVboVaoMarker(ShipAPI trackedShip) {
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

        entity.getLocation().set(trackedShip.getLocation());
        LOG.info("GearIn VBO/VAO marker init: ship=" + trackedShip.getId()
                + " vao=" + vaoId + " vbo=" + vboId);
    }

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn VBO/VAO marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn VBO/VAO marker: compile failed for " + path + ": "
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
            LOG.error("GearIn VBO/VAO marker: could not create program");
            return false;
        }
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn VBO/VAO marker: link failed: "
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
        if (positionLocation < 0 || centerLocation < 0 || facingLocation < 0
                || colorLocation < 0 || geometryLocation < 0 || widthsLocation < 0
                || worldPerPixelLocation < 0) {
            LOG.error("GearIn VBO/VAO marker: attribute or uniform location missing");
            return false;
        }
        return true;
    }

    private boolean buildGeometry() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            coverageVertices = BufferUtils.createFloatBuffer(12);
            updateCoverageVertices(0f);

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
            if (vaoId == 0 || vboId == 0) {
                LOG.error("GearIn VBO/VAO marker: could not create VAO or VBO");
                return false;
            }
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(positionLocation);
            GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
            return true;
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
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

    @Override
    public void advance(float amount) {
        if (!valid) {
            return;
        }
        if (engine == null || trackedShip == null
                || engine.isCombatOver() || !engine.isEntityInPlay(trackedShip)
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
        if (!programAttempted) {
            programAttempted = true;
            if (!GLContext.getCapabilities().OpenGL30) {
                LOG.error("GearInVboVaoMarker: required OpenGL capability unavailable");
                valid = false;
                return;
            }
            try {
                if (!buildProgram() || !buildGeometry()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("GearInVboVaoMarker: GPU resource creation failed", ex);
                cleanup();
                return;
            }
            LOG.info("GearInVboVaoMarker GPU resources ready: vao=" + vaoId + " vbo=" + vboId);
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);

        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean matrixPushed = false;
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            if (Float.compare(worldPerPixel, lastWorldPerPixel) != 0) {
                updateCoverageVertices(worldPerPixel);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, coverageVertices);
                lastWorldPerPixel = worldPerPixel;
            }
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
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            if (matrixPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
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
        LOG.info("GearIn VBO/VAO marker cleanup: ship="
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

::: details 完整成品：GearIn_vbo_vao_marker.vert

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

::: details 完整成品：GearIn_vbo_vao_marker.frag

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

1. **在 OpenGL 3.0 兼容上下文运行**：执行 `gradle jar` 确认类与着色器到位后进入测试战斗，通过 Console Commands 登记并在日志确认 `GearInVboVaoMarker GPU resources ready: vao=... vbo=...`：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInVboVaoMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

2. **核对多帧、缩放与清理**：保持第 2 帧之后方向标持续稳定绘制 (验证 VAO 状态未被外部覆写)、缩放视口时 `2 px`/`3 px` 边缘不截断、重复调用两次 `cleanup()` 只释放一次 VAO/VBO/program/shader：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

## 常见问题

- **第一帧看得见，第二帧起突然消失或报错**：`render(...)` 在 `vaoId` 绑定期间误调用了 `glPopClientAttrib()`，把外部 VAO 的顶点数组状态覆盖进了 `vaoId`。
    - **解决方案**：使用独立 VAO 时直接绑定 `vaoId` 并还原 `previousVao`，移除 `glPushClientAttrib`/`glPopClientAttrib`。

- **方向标完全不显示或顶点偏移**：VAO 绑定时指向的 `vboId` 已被删除，或者 `glVertexAttribPointer` 的步幅、偏移量和分量数对不上。
    - **解决方案**：确认先 `glBindVertexArray(vaoId)` 再绑定 `vboId` 配置 `2` 个 `GL_FLOAT`、stride `0`、偏移 `0L`，且绘制期间不改动该 VAO。

- **缩放镜头时线宽变化或边缘被裁切**：`u_worldPerPixel` 没跟随当前视口，或 `glBufferSubData` 没在 `worldPerPixel` 改变时刷新矩形顶点。
    - **解决方案**：对比 `worldPerPixel` 与 `lastWorldPerPixel`，变化时重新 `updateCoverageVertices` 并上传 VBO。

- **其他画面出现错位或资源泄漏**：`finally` 漏了恢复原 VAO、原 `GL_ARRAY_BUFFER`、program 或矩阵模式，或者 `cleanup()` 漏了删除句柄并归零。
    - **解决方案**：按 `buildGeometry()` 和 `render()` 的 `finally` 检查恢复顺序，在 `cleanup()` 统一释放全部 GPU 对象。

## 建议参考项

- `org.lwjgl.opengl.GL15`：创建、上传、绑定和删除 VBO；
- `org.lwjgl.opengl.GL30`：创建、绑定和删除 VAO；
- `org.lwjgl.opengl.GL20`：配置 attribute、uniform 和 shader program；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层渲染插件生命周期；
- `com.fs.starfarer.api.combat.ViewportAPI`：获取 Alpha 和屏幕到世界单位的换算。
