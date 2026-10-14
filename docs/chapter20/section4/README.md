# 使用 SSBO 传递参数

SSBO 可以按约定布局保存 shader 使用的一组记录。本节把方向标的位置、朝向、Alpha 和颜色写成一条记录，Java 上传缓冲，顶点 shader 读取并变换共享矩形。单条记录先明确字段与绑定关系，后面再扩展到舰船集合。

*本节预期阅读时间：\~25 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `std430`、shader storage block 和 binding `0` 的关系；
- 使用两个 `vec4` 组织一个方向标的 GPU 参数；
- 在 `advance(...)` 和 `render(...)` 之间传递可复用的 `FloatBuffer`；
- 使用一次 `glDrawArrays` 绘制圆环和朝向线；
- 处理暂停、舰船失效、战斗结束和重复清理。

## 前置知识

- [C20-S3] 的 VBO、VAO、attribute 布局和六顶点矩形；
- [C20-S2] 的程序化圆环、朝向线、距离函数和 Alpha 混合；
- Java 的 `FloatBuffer`、OpenGL program 生命周期和战斗渲染插件。

## 保存资源并在战斗登记

将完整类保存到 `src/data/scripts/gearin/combat/GearInSsboMarker.java`，两份 shader 保存到本节列出的 `data/shaders/` 路径。构建 JAR 并重启，进入测试战斗、部署并控制玩家舰后，通过 Console Commands 执行：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInSsboMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

**每场战斗登记一次当前版本。**`init(...)` 取得当前战斗并设置承载实体，GPU 资源在首次 `render(...)` 的有效上下文中创建；能力不足时记录条件并结束实例。命令沿用 [C20-S1] 的 `GearIn_marker` 项保存实例，便于随后验证清理。比较各版本时分别进入新的战斗。

## 确定参数由谁提供

战斗入口把目标舰船交给 `GearInSsboMarker` 构造器，再把插件注册到引擎。`init(entity)` 收到的是引擎创建的承载实体，用于定位和视口剔除。舰船移动或转向时，方向标同步改变位置和朝向；暂停时保留上一帧参数。SSBO 保存状态，六顶点矩形由 VBO 和 VAO 提供，圆环与朝向线由 fragment shader 计算。

## 用两个 vec4 组织记录

一条记录占用 `8` 个 `float`，即 `32` 字节。第一个 `vec4` 保存中心 `x/y`、朝向和 Alpha；第二个 `vec4` 保存 RGBA 颜色。两个向量各占 `16` 字节，Java 按 shader 声明的顺序写入：

```java
markerBuffer.clear();
markerBuffer.put(trackedShip.getLocation().x)
        .put(trackedShip.getLocation().y)
        .put(trackedShip.getFacing()).put(trackedShip.getAlphaMult());
markerBuffer.put(0.1f).put(0.9f).put(1f).put(1f);
markerBuffer.flip();
```

shader storage block 使用相同字段顺序。若 Java 把位置、朝向或颜色写错位置，shader 仍会照既定布局读取，方向标的位置或颜色就会异常。

本节选择两个 `vec4`，每个向量占 16 字节，记录跨度为 32 字节。它让 Java 的连续写入顺序能够直接对应 shader 字段；改用其他字段组合时，还要按实际布局处理对齐与间距。

## 创建并绑定 SSBO

首次 `render(...)` 创建 SSBO 并分配 `8 * Float.BYTES` 字节。下面是 `buildGeometry()` 中的局部片段，调用结束后恢复原通用绑定：

```java
ssboId = GL15.glGenBuffers();
GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
        FLOATS_PER_MARKER * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
```

缓冲已经分配，还要让 program 找到它。`glGetProgramResourceIndex(...)` 查询链接后 `MarkerInstances` 的 block 索引，`glShaderStorageBlockBinding(...)` 把这个 block 对应到 binding `0`，实际绘制时 `glBindBufferBase(...)` 再把 SSBO 对象放到该绑定点。shader 的声明因而能够读到上传的记录。

**block 索引标识 program 内的块，binding 索引标识上下文中的位置，缓冲 ID 标识实际对象。** 这几项即使恰好出现相同数字，也各有对应参数。

## 在两个生命周期阶段传递参数

`advance(...)` 在战斗更新阶段写入 CPU 缓冲；`render(...)` 在 OpenGL 上下文中调用 `glBufferSubData(...)` 上传这条记录。暂停时保留上次写入的值，恢复后继续取舰船状态：

```java
GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, markerBuffer);
GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssboId);
GL20.glUseProgram(program);
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
```

一次 `glDrawArrays` 只提交六个顶点。vertex shader 读取 `markers[0]`，旋转并平移矩形；fragment shader 根据插值后的局部坐标计算圆周距离和有限线段距离。

## 让状态恢复覆盖整个绘制调用

`buildGeometry()` 保存并恢复原 VAO、VBO 与 SSBO 通用绑定，绘制阶段则另外处理 binding `0`。上传用通用绑定确定操作对象，shader 用索引绑定选择记录来源，两项状态都由本次调用改变。

::: warning bindBufferBase 同时修改两种绑定
`glBindBufferBase(...)` 修改指定索引，也修改 SSBO 通用绑定。结束绘制时先恢复索引，再恢复通用绑定，才能保留两项各自的原值。
:::

绘制前记录 VAO、`GL_ARRAY_BUFFER`、SSBO 的通用绑定与 binding `0` 的索引绑定、program 和矩阵模式，并用属性栈保存混合、深度、纹理和客户端 attribute 状态。`finally` 恢复本次绘制改动的状态；后续渲染器才能继续使用自己的配置。

## 纹理采样只承担纹理职责

方向标颜色来自 SSBO。需要采样纹理时，sampler 只保存纹理单元编号，Java 绑定纹理并提交单元：

```java
GL20.glUniform1i(samplerLocation, 0);
GL13.glActiveTexture(GL13.GL_TEXTURE0);
GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
```

采样器、纹理单元和纹理对象共同决定采样来源，它们不改变 SSBO 的数据布局。

## 让兼容声明与画面淡出对应

两份资源采用 `#version 430 compatibility`，让 shader 继续使用游戏设置的 `gl_ModelViewProjectionMatrix`。`GLContext.getCapabilities().OpenGL43` 决定是否具备本例所需的 OpenGL 功能；兼容上下文则提供这套内建矩阵。只提高版本数字还不能解决矩阵来源，本例选择兼容声明，是为了沿用此前已经讲清的世界坐标变换。

舰船 Alpha 随记录进入 `v_color.a`，视口 Alpha 由 Java 每次绘制提交给 `u_viewportAlpha`。fragment shader 将两者和形状覆盖率相乘，使方向标既服从舰船透明度，也服从画面淡出。

## 完整最小例

下面的 Java、vertex shader 和 fragment shader 共同完成单记录 SSBO 方向标。

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
    private float renderRadius = DIRECTION_LENGTH;
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
        if (engine.isCombatOver() || !engine.isEntityInPlay(trackedShip)
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
        LOG.info("GearIn SSBO marker cleanup");
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return renderRadius + engine.getViewport().convertScreenWidthToWorldWidth(3f);
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

## 验证方案

前置数据：显卡和游戏渲染上下文支持 OpenGL 4.3 兼容上下文，Console Commands 已启用。

完成本方案中的移动、缩放和生命周期观察后，用下面的 Console Commands 命令连续清理当前实例两次；重新观察时进入新的战斗并登记：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

1. 在 MOD 工程执行 `gradle jar`，确认插件类和两份 shader 资源已进入对应的 MOD 目录。
2. 启动游戏并进入测试战斗，以目标舰船创建 `new GearInSsboMarker(ship)`，再调用 `engine.addLayeredRenderingPlugin(...)` 注册插件。
3. 移动并转向目标舰船，观察方向标中心和朝向线同步变化。
4. 暂停并恢复战斗，观察暂停期间方向标保留最后一帧。
5. 让舰船离场或失效，结束战斗，再进入另一场战斗。
6. 对同一插件重复调用 `cleanup()`，观察资源释放日志。
7. 缩放和横移视口，观察圆环半径、朝向线长度和屏幕线宽。

预期结果：

- 方向标圆环半径为 `80 su`，朝向线长度为 `120 su`；
- 圆环线宽为 `2 px`，朝向线线宽为 `3 px`；
- 移动、转向和 Alpha 变化通过一条 SSBO 记录进入 shader；
- 暂停、舰船失效和战斗结束后，插件按生命周期停止绘制；
- 重复清理不会二次删除 VAO、SSBO、VBO、program 或 shader；
- 其他战场画面的矩阵、混合、深度、纹理和 attribute 状态保持原有表现。

## 故障分析

- **方向标位置或颜色整体错位**：CPU 写入顺序与 `MarkerData` 字段顺序不同。
    - **解决方案**：按中心/朝向/Alpha、RGBA 的顺序检查两个 `vec4`，并确认 Java 与 shader 都使用 binding `0`。

- **只显示旧位置**：暂停状态一直保留，或 `advance(...)` 没有更新 `FloatBuffer`。
    - **解决方案**：确认暂停分支只保留上一帧，恢复运行后重新执行 `clear()`、写入和 `flip()`。

- **其他战场画面出现状态污染**：绘制结束路径没有执行完整恢复。
    - **解决方案**：把所有恢复动作放入 `finally`，同时恢复 VAO、缓冲、program、矩阵、属性和客户端 attribute 状态。

## 总结

两个 `vec4` 让 Java 写入与 shader 读取使用同一条 32 字节记录，block、绑定点和缓冲对象把这份数据接到 program。几何仍由 VBO 提供，形状仍由片元计算；SSBO 在此承担参数来源，为后面的多记录绘制建立布局。

对单个方向标，前一节的 uniform 已足以传递这些值。编者在这里采用 SSBO，是为了展开记录与集合的组织；是否值得增加这套存储，应由实际需要共享、索引或批量处理的数据决定。

## 建议参考项

- `org.lwjgl.opengl.GL43`：shader storage block 查询和 SSBO 常量；
- `org.lwjgl.opengl.GL30`：VAO 与 `glBindBufferBase`；
- `org.lwjgl.opengl.GL15`：VBO、SSBO 和数据上传；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：战斗渲染生命周期。
