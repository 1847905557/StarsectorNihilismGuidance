# 使用实例化进行绘制

把方向标从单艘玩家舰放大到满战场的友军与敌军之后，逐船发绘制指令立刻变成性能包袱，换成 `glDrawArraysInstanced` 一次拉取所有记录会干脆得多。

顺带别忘了跟着更新承载实体的位置与剔除半径 —— 整批实例散开各处时，还拿单舰半径去裁视口，中心刚出屏幕就会让边缘舰船的标记整批熄灭。

---

_本节预期阅读时间：\~28 分钟。_

_本节预期操作时间：\~40 分钟。_

## 前置知识

- [C20-S3] 的 VBO、VAO 与独立 VAO 的状态保存；
- [C20-S4] 的 `std430`、SSBO binding `0` 与双重绑定还原；
- 了解 `CombatEngineAPI.getShips()`、舰船阵营侧 `owner`、坐标与存活状态。

## 收集在场舰船并动态计算包围盒剔除

在 `GearInInstancedMarker` 里，每个实例依然占用 `8` 个 `float`(两个 `vec4`)，预分配 `128 * 8` 容量的 `instanceBuffer`。收集数据时顺带记录颜色侧 (玩家青色 `0.1, 0.9, 1.0`、同阵营绿 `0.2, 0.7, 0.2`、其余敌对橙红 `1.0, 0.25, 0.1`) 以及整个集合的 `minX`/`maxX`/`minY`/`maxY`：

```java
for (ShipAPI ship : ships) {
    if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
        continue;
    }
    if (instanceCount >= MAX_INSTANCES) {
        if (!overflowLogged) {
            overflowLogged = true;
            LOG.warn("GearIn instanced marker: capacity reached "
                    + MAX_INSTANCES);
        }
        break;
    }
    float x = ship.getLocation().x;
    float y = ship.getLocation().y;
    instanceBuffer.put(x).put(y).put(ship.getFacing()).put(ship.getAlphaMult());
    // 紧接着写入 4 个颜色 float
    instanceCount++;
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
}
instanceBuffer.flip();
if (instanceCount > 0) {
    entity.getLocation().set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f);
    renderRadius = (float) Math.hypot(maxX - minX, maxY - minY) * 0.5f
            + DIRECTION_LENGTH;
}
```

一旦场上存活舰船超过 `128` 艘，循环只打一次容量警告并保留先遍历到的记录。如果当帧 `instanceCount == 0`，`render(...)` 直接早退跳过 OpenGL 调用，避免提交空实例化请求。

## 用 glDrawArraysInstanced 与 gl_InstanceID 批量画完

上传与绑定逻辑跟 [C20-S4] 几乎一致，唯一的绘制入口换成带实例数的 `glDrawArraysInstanced`：

```java
GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, instanceBuffer);
GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
GL20.glUseProgram(program);
GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, instanceCount);
```

顶点着色器只把原本写死的 `markers[0]` 改成 `markers[gl_InstanceID]`，每个实例独立读取自己的中心、朝向、Alpha 和颜色；共享参数如 `u_geometry`、`u_worldPerPixel` 和 `u_viewportAlpha` 则仍通过 uniform 一次送完。

注意权衡：实例化能省掉上百次 Java→GPU 状态切换和 draw call 开销，但每个覆盖矩形内未被命中的透明片元仍然会经过片元着色器 `discard`；若标记数量或覆盖面积很大，裁剪覆盖矩形边距同样影响整体片元耗时。

## 完整最小例

将 `GearInInstancedMarker` 保存到 `src/data/scripts/gearin/combat/GearInInstancedMarker.java`，并将两份着色器保存到 `data/shaders/`：

::: details 完整成品：GearInInstancedMarker

```java
package data.scripts.gearin.combat;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.List;

import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
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
public class GearInInstancedMarker extends BaseCombatLayeredRenderingPlugin {
    private static final Logger LOG = Global.getLogger(GearInInstancedMarker.class);
    private static final String VERT_PATH = "data/shaders/GearIn_instanced_marker.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_instanced_marker.frag";
    private static final String BLOCK_NAME = "MarkerInstances";
    private static final int BINDING = 0;
    private static final int MAX_INSTANCES = 128;
    private static final int FLOATS_PER_INSTANCE = 8;
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;

    private CombatEngineAPI engine;
    private FloatBuffer instanceBuffer;
    private FloatBuffer coverageVertices;
    private float lastWorldPerPixel = Float.NaN;
    private int instanceCount;
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
    private float renderRadius = DIRECTION_LENGTH;
    private boolean programAttempted;
    private boolean valid = true;
    private boolean overflowLogged;
    private boolean cleaned;

    public GearInInstancedMarker() {
        layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (engine == null) {
            valid = false;
            return;
        }
        instanceBuffer = BufferUtils.createFloatBuffer(
                MAX_INSTANCES * FLOATS_PER_INSTANCE);
        updateInstances();
    }

    private int compileShader(int type, String source, String path) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn instanced marker: could not create shader " + path);
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn instanced marker: compile failed for " + path + ": "
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
            LOG.error("GearIn instanced marker: link failed: "
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
            LOG.error("GearIn instanced marker: attribute, uniform or block missing");
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
                    MAX_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES,
                    GL15.GL_DYNAMIC_DRAW);
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

    @Override
    public void advance(float amount) {
        if (!valid) {
            return;
        }
        if (engine == null || engine.isCombatOver()) {
            valid = false;
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        updateInstances();
    }

    private void updateInstances() {
        instanceBuffer.clear();
        instanceCount = 0;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        ShipAPI player = engine.getPlayerShip();
        int playerSide = player == null ? 0 : player.getOwner();
        List<ShipAPI> ships = engine.getShips();
        for (ShipAPI ship : ships) {
            if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
                continue;
            }
            if (instanceCount >= MAX_INSTANCES) {
                if (!overflowLogged) {
                    overflowLogged = true;
                    LOG.warn("GearIn instanced marker: capacity reached "
                            + MAX_INSTANCES);
                }
                break;
            }
            float x = ship.getLocation().x;
            float y = ship.getLocation().y;
            instanceBuffer.put(x).put(y).put(ship.getFacing()).put(ship.getAlphaMult());
            float red = ship == player ? 0.1f : ship.getOwner() == playerSide ? 0.2f : 1f;
            float green = ship == player ? 0.9f : ship.getOwner() == playerSide ? 0.7f : 0.25f;
            float blue = ship == player ? 1f : ship.getOwner() == playerSide ? 0.2f : 0.1f;
            instanceBuffer.put(red).put(green).put(blue).put(1f);
            instanceCount++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        instanceBuffer.flip();
        if (instanceCount > 0) {
            entity.getLocation().set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f);
            renderRadius = (float) Math.hypot(maxX - minX, maxY - minY) * 0.5f
                    + DIRECTION_LENGTH;
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!valid || instanceCount == 0) {
            return;
        }
        if (!programAttempted) {
            programAttempted = true;
            if (!GLContext.getCapabilities().OpenGL43) {
                LOG.error("GearInInstancedMarker: required OpenGL capability unavailable");
                valid = false;
                return;
            }
            try {
                if (!buildProgram() || !buildGeometry()) {
                    cleanup();
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("GearInInstancedMarker: GPU resource creation failed", ex);
                cleanup();
                return;
            }
            LOG.info("GearInInstancedMarker GPU resources ready: vao=" + vaoId + " vbo=" + vboId + " ssbo=" + ssboId);
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
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, instanceBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
            GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
            GL20.glUniform1f(viewportAlphaLocation, viewport.getAlphaMult());
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, instanceCount);
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
        LOG.info("GearIn instanced marker cleanup");
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return engine == null || engine.getViewport() == null
                ? renderRadius
                : renderRadius + engine.getViewport().convertScreenWidthToWorldWidth(3f);
    }
}
```

:::

::: details 完整成品：GearIn_instanced_marker.vert

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
    MarkerData marker = markers[gl_InstanceID];
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

::: details 完整成品：GearIn_instanced_marker.frag

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

1. **一次登记并查看三阵营分色**：在测试战斗用控制台登记 `GearInInstancedMarker`，场上玩家舰显示青色、友军显示绿色、敌舰显示橙红色；把视角拖拽到战场边缘时，远端边缘舰船的圆环仍然留在画面内，不会因中心移出视口就整体消失：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInInstancedMarker(); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

2. **检查容量上限与重复清理**：找一场超过 `128` 艘在场舰船的战斗，确认 `starsector.log` 仅记录一条 `capacity reached 128` 且无越界崩溃；执行两次 `cleanup()` 命令确认 GPU 资源幂等销毁：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

## 常见问题

- **场上所有船加起来只画出中心那一个方向标**：绘制调用写成了 `glDrawArrays`，或者着色器里仍写着 `markers[0]` 而没用 `gl_InstanceID`。
    - **解决方案**：确认 Java 端使用 `GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, instanceCount)`，顶点着色器按 `markers[gl_InstanceID]` 索引。

- **镜头移向部分在场舰船边缘时，场上其余圆环整批突然闪烁消失**：承载实体或 `renderRadius` 还按单舰写死。
    - **解决方案**：每次更新实例时用有效舰船的包围盒中心与半对角线加 `120 su` 重算承载实体坐标与 `renderRadius`。

- **舰船超过 `128` 艘时抛出 `BufferOverflowException`**：先往 `instanceBuffer` 写数据才做容量截断判断。
    - **解决方案**：在写入当前记录之前判断 `instanceCount >= MAX_INSTANCES`，打一次日志后直接 `break`。

## 建议参考项

- `org.lwjgl.opengl.GL31`：实例化数组绘制；
- `org.lwjgl.opengl.GL43`：shader storage block 查询和 binding；
- `org.lwjgl.opengl.GL30`：VAO 与 `glBindBufferBase`；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：读取战斗舰船集合。
