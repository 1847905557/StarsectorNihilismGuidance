# 使用实例化进行绘制

本节让一份矩形几何服务多艘舰船。Java 收集在场舰船并写入 SSBO，vertex shader 用 `gl_InstanceID` 取出各自的位置、朝向和颜色；一次 `glDrawArraysInstanced` 调用提交这些方向标。视口比例变化时，Java 更新矩形边界，为线条保留像素边距。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

- 使用两个 `vec4` 保存每个实例的中心、朝向、Alpha 和颜色；
- 解释 SSBO binding `0`、`std430` 和 `gl_InstanceID` 的关系；
- 使用一次 `glDrawArraysInstanced` 绘制多艘舰船的方向标；
- 处理容量截断、阵营颜色、暂停、视口变化和资源清理。

## 前置知识

- [C21-S4] 的单记录 SSBO、shader storage block 和状态恢复；
- [C21-S3] 的 VBO、VAO 和六顶点矩形；
- `CombatEngineAPI.getShips()`、舰船位置、朝向、阵营和存活状态。

## 确定每艘舰船的参数

在场且存活的舰船各自需要一个方向标。玩家舰使用青色，盟友使用绿色，其他舰船使用橙红色；移动和转向只更新对应记录。VBO 保存一份矩形几何，SSBO 保存每艘舰船的中心、朝向、Alpha 和颜色。

## 让一条记录对应一个实例

每个实例占用 `8` 个 `float`、`32` 字节。两个 `vec4` 按 `std430` 的 `16` 字节对齐排列：

```java
instanceBuffer.put(x).put(y).put(ship.getFacing()).put(ship.getAlphaMult());
instanceBuffer.put(red).put(green).put(blue).put(1f);
```

Java 按遍历顺序写入记录，shader 根据实例编号读取。`instanceCount` 决定本次绘制多少个方向标，缓冲区容量为 `128` 条记录；两者分别管理。

## 收集实例并处理容量

`advance(...)` 遍历 `engine.getShips()`，过滤不在场或已死亡的舰船，再写入可复用的 CPU 缓冲。达到 `128` 条时保留先取得的记录，并记录一次容量日志：

```java
for (ShipAPI ship : engine.getShips()) {
    if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
        continue;
    }
    if (instanceCount >= MAX_INSTANCES) {
        break;
    }
    instanceBuffer.put(ship.getLocation().x).put(ship.getLocation().y)
            .put(ship.getFacing()).put(ship.getAlphaMult());
    instanceCount++;
}
instanceBuffer.flip();
```

暂停期间沿用上一帧的实例数量和缓冲内容。恢复后重新收集舰船；战斗结束时插件失效，清理共享的 GPU 资源。

## 一次调用绘制所有实例

render 阶段上传当前缓冲，绑定 SSBO binding `0` 和 VAO，再把实例数量传给一次绘制调用：

```java
GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, instanceBuffer);
GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
GL30.glBindVertexArray(vaoId);
GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, instanceCount);
```

VAO 提供 `a_position` 的布局，SSBO 提供逐实例参数。视口缩放改变像素对应的世界宽度时，VBO 中的矩形边界也随之更新，避免裁掉外缘。绘制还会修改 SSBO binding `0`、混合、深度和纹理状态；`finally` 恢复索引绑定和其他共享状态，再把上下文交给后续渲染器。

## 用 gl_InstanceID 选择数据

vertex shader 中的 `gl_InstanceID` 从 `0` 开始递增。它索引当前实例的 `MarkerData`，再用该记录的中心和朝向变换同一个矩形：

```glsl
MarkerData marker = markers[gl_InstanceID];
float angle = radians(marker.centerFacingAlpha.z);
vec2 world = marker.centerFacingAlpha.xy + rotated;
```

fragment shader 继续使用圆周距离和有限线段距离生成方向标，因此实例化改变的是参数来源和绘制次数，形状算法保持一致。

## 释放共享资源

所有实例共享一个 VAO、VBO、program 和两份 shader，SSBO 保存逐实例数据。清理顺序为 VAO、SSBO、VBO、program、shader；初始化失败、战斗结束和重复清理都进入同一个幂等路径。

## 完整最小例

下面的 Java、vertex shader 和 fragment shader 共同完成 SSBO 实例化方向标。

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

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 使用 SSBO 和实例化绘制显示多艘舰船的程序化方向标。
 * advance() 收集实例数据，render() 上传共享缓冲并执行一次实例化绘制。
 */
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
    private float renderRadius = DIRECTION_LENGTH;
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
        try {
            if (!buildProgram() || !buildGeometry()) {
                valid = false;
                cleanup();
                return;
            }
        } catch (IOException | RuntimeException e) {
            valid = false;
            LOG.error("GearIn instanced marker: initialization failed", e);
            cleanup();
            return;
        }
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
        int blockIndex = GL43.glGetProgramResourceIndex(program,
                GL43.GL_SHADER_STORAGE_BLOCK, BLOCK_NAME);
        if (positionLocation < 0 || worldPerPixelLocation < 0
                || geometryLocation < 0 || blockIndex < 0) {
            LOG.error("GearIn instanced marker: attribute, uniform or block missing");
            return false;
        }
        GL43.glShaderStorageBlockBinding(program, blockIndex, BINDING);
        return true;
    }

    private boolean buildGeometry() {
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
        if (!valid || engine == null || instanceBuffer == null) {
            valid = false;
            return;
        }
        if (engine.isCombatOver()) {
            valid = false;
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        instanceBuffer.clear();
        instanceCount = 0;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
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
            float red = ship == engine.getPlayerShip() ? 0.1f : ship.isAlly() ? 0.2f : 1f;
            float green = ship == engine.getPlayerShip() ? 0.9f : ship.isAlly() ? 0.7f : 0.25f;
            float blue = ship == engine.getPlayerShip() ? 1f : ship.isAlly() ? 0.2f : 0.1f;
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
        if (!valid || viewport == null || instanceCount == 0 || vaoId == 0
                || ssboId == 0 || program == 0) {
            return;
        }
        float worldPerPixel = viewport.convertScreenWidthToWorldWidth(1f);
        if (!Float.isFinite(worldPerPixel) || worldPerPixel <= 0f) {
            return;
        }
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousSsbo = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousSsboBase = GL30.glGetInteger(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING, BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
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
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, instanceCount);
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING,
                    previousSsboBase);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSsbo);
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
        return renderRadius;
    }
}
```

:::

::: details 完整成品：GearIn_instanced_marker.vert

```glsl
#version 430

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
#version 430

uniform vec2 u_geometry;
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
    fragColor = vec4(v_color.rgb, v_color.a * coverage);
}
```

:::

## 验证方案

前置数据：已经启用包含实例化方向标插件的 GearIn MOD，并进入能够生成多艘舰船的测试战斗。

1. 在 MOD 工程执行 `gradle jar`，确认插件类和两份 shader 资源已进入对应的 MOD 目录。
2. 启动游戏并进入测试战斗，注册 `GearInInstancedMarker`。
3. 移动并转向玩家、盟友和敌方舰船，观察各自方向标同步变化。
4. 缩放和横移视口，观察圆环半径、朝向线长度和屏幕线宽。
5. 暂停并恢复战斗，再让部分舰船离场或死亡。
6. 使用超过 `128` 艘有效舰船的战斗，观察容量日志和截断顺序。
7. 结束战斗并重新进入另一场战斗，对插件重复调用 `cleanup()`。

预期结果：

- 一次 `glDrawArraysInstanced` 绘制多个半径 `80 su`、长度 `120 su` 的方向标；
- 玩家、盟友和敌人分别显示青色、绿色和橙红色；
- 移动、转向和 Alpha 变化由对应的 SSBO 记录独立驱动；
- 超过 `128` 个实例时保留先取得的记录并记录一次容量日志；
- 暂停、舰船失效和战斗结束后实例数量与生命周期状态一致；
- 重复清理不会二次删除 VAO、SSBO、VBO、program 或 shader；
- 其他战场画面的矩阵、混合、深度、纹理和 attribute 状态保持原有表现。

## 故障分析

- **只显示一个方向标**：绘制调用使用了 `glDrawArrays`，或实例数量为 `0`。
    - **解决方案**：确认使用 `glDrawArraysInstanced`，并检查 `instanceCount` 与 `FloatBuffer` 的有效记录数量一致。

- **实例方向或颜色错位**：CPU 记录顺序与 `std430` 的两个 `vec4` 顺序不一致。
    - **解决方案**：按中心/朝向/Alpha、RGBA 的顺序检查写入与 shader 结构体，并确认 binding `0` 一致。

- **超过容量后出现越界**：容量判断发生在写入之后。
    - **解决方案**：在写入下一条记录前判断 `MAX_INSTANCES`，并在首次截断时记录日志。

## 总结

本节把一份 SSBO 记录扩展成舰船集合。VAO 和 VBO 提供共享矩形，SSBO 提供逐实例参数，`gl_InstanceID` 选择当前记录，`glDrawArraysInstanced` 让 GPU 一次重复执行同一套顶点和片元逻辑。

## 建议参考项

- `org.lwjgl.opengl.GL31`：实例化数组绘制；
- `org.lwjgl.opengl.GL43`：shader storage block 查询和 binding；
- `org.lwjgl.opengl.GL30`：VAO 与 `glBindBufferBase`；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：读取战斗舰船集合。
