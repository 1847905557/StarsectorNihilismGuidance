# 使用 SSBO 传递参数

单艘船的位置和颜色用 uniform 传还算轻巧，真想一口气往显卡里塞上百艘船的数据，逐个设 uniform 就行不通了。

OpenGL 4.3 的 SSBO 允许按约定布局排开一整串结构体记录；虽然这页仍只画一艘船，先把存储块对齐、binding 槽位以及双重绑定还原理顺，下一页扩成全战场实例化就顺手多了。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C20-S2] 的 `#version 120` 距离场、覆盖矩形与 Alpha 混合；
- [C20-S3] 的 VBO、VAO 与独立 VAO 的状态保存；
- 熟悉 `FloatBuffer`、OpenGL `4.3` 上下文检查与分层渲染插件生命周期。

## 用两个 vec4 对齐 32 字节记录

在 `#version 430 compatibility` 之下，先在着色器里按 `std430` 声明存储块 (`compatibility` Profile 让着色器继续能直接读取游戏压好的 `gl_ModelViewProjectionMatrix`)：

```glsl
struct MarkerData {
    vec4 centerFacingAlpha;
    vec4 color;
};

layout (std430, binding = 0) buffer MarkerInstances {
    MarkerData markers[];
};
```

每个 `vec4` 严格占 `16` 字节，两条合起来恰好 `32` 字节 (`8` 个 `float`)，没有任何填充空洞。Java 这侧的 `updateMarkerData()` 按完全相同的字段顺序往 `markerBuffer` 写：

```java
markerBuffer.clear();
markerBuffer.put(trackedShip.getLocation().x)
        .put(trackedShip.getLocation().y)
        .put(trackedShip.getFacing()).put(trackedShip.getAlphaMult());
markerBuffer.put(0.1f).put(0.9f).put(1f).put(1f);
markerBuffer.flip();
```

`init(...)` 初始化完 `markerBuffer` 后就会先调一次 `updateMarkerData()`，因此哪怕插件是在战斗暂停瞬间登记的，第一帧也能拿到合法的坐标和颜色，暂停期间后续帧直接保留这份末帧快照，等解除暂停再在 `advance(...)` 刷新。

## 厘清 block 索引、binding 点与 SSBO 句柄

用 SSBO 时最容易绕晕的，是这三个编号各自对应哪一段：

1. **缓冲对象句柄 `ssboId`**：由 `GL15.glGenBuffers()` 分配的实际显存对象，上传数据时绑定到通用目标 `GL43.GL_SHADER_STORAGE_BUFFER` 并分配 `8 * Float.BYTES` 字节；
2. **block 索引 `blockIndex`**：program 链接成功后用 `GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, "MarkerInstances")` 查出着色器块在当前 program 里的编号 (缺失则返回 `-1`)；
3. **上下文绑定槽 `BINDING = 0`**：靠 `GL43.glShaderStorageBlockBinding(program, blockIndex, BINDING)` 把 program 里的块挂到 `0` 号槽，绘制时再靠 `GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId)` 把显存缓冲挂到同一号槽上。

```java
GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, markerBuffer);
GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
GL20.glUseProgram(program);
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
```

::: warning glBindBufferBase 会同时改写通用与索引两种绑定

`GL30.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BINDING, ssboId)` 除了更新索引槽 `0`(由 `GL30.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, BINDING)` 查询保存)，还会**连带修改通用绑定 `GL_SHADER_STORAGE_BUFFER`**。因此 `finally` 里必须先恢复索引槽 `previousSsboBase`，再调用 `GL15.glBindBuffer(..., previousSsbo)` 还原通用绑定；同时这里继续沿用自有 VAO 提交，不夹杂 `glPushClientAttrib`/`glPopClientAttrib`，免得把外部 VAO 状态盖进 `vaoId`。

:::

## 完整最小例

将 `GearInSsboMarker` 保存到 `src/data/scripts/gearin/combat/GearInSsboMarker.java`，并将 `.vert`/`.frag` 着色器保存到 `data/shaders/`：

::: details 完整成品：GearInSsboMarker

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
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/** 管理方向标的数据、绘制与资源生命周期。 */
public class GearInSsboMarker extends BaseCombatLayeredRenderingPlugin {
    private static final Logger LOG = Global.getLogger(GearInSsboMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_ssbo_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_ssbo_marker.frag";
    private static final String BLOCK_NAME = "MarkerInstances";
    private static final int BINDING = 0;
    private static final int FLOATS_PER_MARKER = 8;
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;

    private CombatEngineAPI engine;
    private final ShipAPI trackedShip;
    private FloatBuffer markerBuffer;
    private FloatBuffer coverageVertices;
    private float lastWorldPerPixel = Float.NaN;
    private int vaoId;
    private int vboId;
    private int ssboId;
    private int vertexShader;
    private int fragmentShader;
    private int program;
    private int positionLocation = -1;
    private int worldPerPixelLocation = -1;
    private int geometryLocation = -1;
    private int viewportAlphaLocation = -1;
    private boolean programAttempted;
    private boolean valid = true;
    private boolean cleaned;

    public GearInSsboMarker(ShipAPI trackedShip) {
        this.trackedShip = trackedShip;
        layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (trackedShip == null) {
            valid = false;
            return;
        }
        markerBuffer = BufferUtils.createFloatBuffer(FLOATS_PER_MARKER);

        updateMarkerData();
        LOG.info("GearIn SSBO marker init: vao=" + vaoId + " vbo=" + vboId
                + " ssbo=" + ssboId);
    }

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn SSBO marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn SSBO marker: compile failed for " + path + ": "
                    + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private boolean buildProgram() throws IOException {
        vertexShader = compileShader(GL20.GL_VERTEX_SHADER,
                Global.getSettings().loadText(VERT_PATH), VERT_PATH);
        if (vertexShader == 0) {
            return false;
        }
        fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER,
                Global.getSettings().loadText(FRAG_PATH), FRAG_PATH);
        if (fragmentShader == 0) {
            return false;
        }
        program = GL20.glCreateProgram();
        if (program == 0) {
            return false;
        }
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn SSBO marker: link failed: "
                    + GL20.glGetProgramInfoLog(program, 4096));
            return false;
        }
        positionLocation = GL20.glGetAttribLocation(program, "a_position");
        worldPerPixelLocation = GL20.glGetUniformLocation(program, "u_worldPerPixel");
        geometryLocation = GL20.glGetUniformLocation(program, "u_geometry");
        viewportAlphaLocation = GL20.glGetUniformLocation(program, "u_viewportAlpha");
        int blockIndex = GL43.glGetProgramResourceIndex(program,
                GL43.GL_SHADER_STORAGE_BLOCK, BLOCK_NAME);
        if (positionLocation < 0 || worldPerPixelLocation < 0
                || geometryLocation < 0 || viewportAlphaLocation < 0 || blockIndex < 0) {
            LOG.error("GearIn SSBO marker: attribute, uniform or block missing");
            return false;
        }
        GL43.glShaderStorageBlockBinding(program, blockIndex, BINDING);
        return true;
    }

    private boolean buildGeometry() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousSsbo = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            coverageVertices = BufferUtils.createFloatBuffer(12);
            updateCoverageVertices(0f);
            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
            ssboId = GL15.glGenBuffers();
            if (vaoId == 0 || vboId == 0 || ssboId == 0) {
                return false;
            }
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(positionLocation);
            GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    FLOATS_PER_MARKER * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            return true;
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSsbo);
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

    private void updateMarkerData() {
        entity.getLocation().set(trackedShip.getLocation());
        markerBuffer.clear();
        markerBuffer.put(trackedShip.getLocation().x)
                .put(trackedShip.getLocation().y)
                .put(trackedShip.getFacing()).put(trackedShip.getAlphaMult());
        markerBuffer.put(0.1f).put(0.9f).put(1f).put(1f);
        markerBuffer.flip();
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
        updateMarkerData();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!valid) {
            return;
        }
        if (!programAttempted) {
            programAttempted = true;
            if (!GLContext.getCapabilities().OpenGL43) {
                LOG.error("GearInSsboMarker: required OpenGL capability unavailable");
                valid = false;
                return;
            }
            try {
                if (!buildProgram() || !buildGeometry()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("GearInSsboMarker: GPU resource creation failed", ex);
                cleanup();
                return;
            }
            LOG.info("GearInSsboMarker GPU resources ready: vao=" + vaoId + " vbo=" + vboId + " ssbo=" + ssboId);
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousSsbo = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousSsboBase = GL30.glGetInteger(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING, BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
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
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, markerBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
            GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
            GL20.glUniform1f(viewportAlphaLocation, viewport.getAlphaMult());
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING,
                    previousSsboBase);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSsbo);
            GL20.glUseProgram(previousProgram);
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
        if (ssboId != 0) {
            GL15.glDeleteBuffers(ssboId);
            ssboId = 0;
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
        LOG.info("GearIn SSBO marker cleanup");
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

::: details 完整成品：GearIn_ssbo_marker.vert

```glsl
#version 430 compatibility

layout (location = 0) in vec2 a_position;

struct MarkerData {
    vec4 centerFacingAlpha;
    vec4 color;
};

layout (std430, binding = 0) buffer MarkerInstances {
    MarkerData markers[];
};

out vec2 v_local;
out vec4 v_color;

void main() {
    MarkerData marker = markers[0];
    v_local = a_position;
    v_color = vec4(marker.color.rgb, marker.color.a * marker.centerFacingAlpha.w);
    float angle = radians(marker.centerFacingAlpha.z);
    float cosine = cos(angle);
    float sine = sin(angle);
    vec2 rotated = vec2(
            cosine * a_position.x - sine * a_position.y,
            sine * a_position.x + cosine * a_position.y);
    vec2 world = marker.centerFacingAlpha.xy + rotated;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(world, 0.0, 1.0);
}
```

:::

::: details 完整成品：GearIn_ssbo_marker.frag

```glsl
#version 430 compatibility

uniform vec2 u_geometry;
uniform float u_viewportAlpha;
uniform float u_worldPerPixel;
in vec2 v_local;
in vec4 v_color;
out vec4 fragColor;

void main() {
    float ringDistance = abs(length(v_local) - u_geometry.x);
    vec2 nearestOnLine = vec2(clamp(v_local.x, 0.0, u_geometry.y), 0.0);
    float lineDistance = length(v_local - nearestOnLine);
    float edge = 0.5 * u_worldPerPixel;
    float ringHalfWidth = u_worldPerPixel;
    float lineHalfWidth = 1.5 * u_worldPerPixel;
    float ringCoverage = 1.0 - smoothstep(
            ringHalfWidth - edge, ringHalfWidth + edge, ringDistance);
    float lineCoverage = 1.0 - smoothstep(
            lineHalfWidth - edge, lineHalfWidth + edge, lineDistance);
    float coverage = max(ringCoverage, lineCoverage);
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(v_color.rgb, v_color.a * coverage * u_viewportAlpha);
}
```

:::

### 进游戏核对

1. **在 OpenGL 4.3 兼容上下文登记插件**：构建后进测试战斗，部署玩家舰并执行 Console Commands 命令：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInSsboMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

2. **核对多帧、缩放与 SSBO 字段对齐**：移动、转向、暂停与镜头缩放时行为应当与 [C20-S3] 完全一致 (没有位置/颜色错位、没有第二帧失效)；重复调用 `cleanup()` 仅记录一次销毁日志：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

## 常见问题

- **方向标位置或颜色整体错位**：CPU 端 `markerBuffer` 写入顺序与着色器 `MarkerData` 结构体的两个 `vec4` 没对齐，或 binding 槽位号不一致。
    - **解决方案**：按 `center.x / center.y / facing / alpha` 与 `r / g / b / a` 顺序对照写入和读取，确认 `glShaderStorageBlockBinding` 与 `glBindBufferBase` 同为 `0`。

- **第一帧正常，第二帧起忽然消失或显卡状态出错**：误在自建 VAO 下调用了 `glPushClientAttrib`/`glPopClientAttrib`，或 `glBindBufferBase` 后先恢复通用 SSBO 再恢复索引槽 `0` 导致通用绑定再次被冲掉。
    - **解决方案**：移除 `glPushClientAttrib`/`glPopClientAttrib`，并在 `finally` 先还原 `glBindBufferBase(..., previousSsboBase)` 再还原 `GL15.glBindBuffer(..., previousSsbo)`。

- **方向标停在老位置不动**：`advance(...)` 中暂停早退后没恢复写入，或忘记每次重填前调 `markerBuffer.clear()`、填完调 `markerBuffer.flip()`。
    - **解决方案**：核对 `updateMarkerData()` 的缓冲复位和 `advance(...)` 分支。

## 建议参考项

- `org.lwjgl.opengl.GL43`：shader storage block 查询和 SSBO 常量；
- `org.lwjgl.opengl.GL30`：VAO 与 `glBindBufferBase`；
- `org.lwjgl.opengl.GL15`：VBO、SSBO 和数据上传；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：战斗渲染生命周期。
