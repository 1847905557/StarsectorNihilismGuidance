# 使用 VBO 和 VAO 保存顶点

VBO 保存 GPU 使用的顶点数据，VAO 保存 shader 怎样读取这份数据。本节把方向标的六个矩形顶点迁入 VBO，建立一次布局，再在视口比例变化时更新边界。舰船位置、朝向与颜色继续通过 uniform 提交，形状计算沿用 GLSL 版本。

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~35 分钟。_

## 学习目标

完成后，您将能够：

- 区分客户端顶点数组、VBO 和 VAO 保存的数据与状态；
- 使用 `GL15` 创建、上传、绑定和释放 VBO；
- 使用 `GL30` 缓存 attribute 指针并在绘制时复用 VAO；
- 保持程序化方向标的移动、转向、透明度和生命周期行为。

## 前置知识

- [C20-S2] 的 `attribute`、`uniform`、`varying` 和程序化片元形状；
- [C20-S1] 的分层渲染插件、模型坐标和 OpenGL 状态恢复；
- 能够用 Java 17 编译示例，并从 `starsector.log` 定位资源路径和 program 错误。

## 保存资源并在战斗登记

将完整类保存到 `src/data/scripts/gearin/combat/GearInVboVaoMarker.java`，两份 shader 保存到本节列出的 `data/shaders/` 路径。构建 JAR 并重启，进入测试战斗、部署并控制玩家舰后，通过 Console Commands 执行：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInVboVaoMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

**每场战斗登记一次当前版本。**`init(...)` 取得当前战斗并设置承载实体，GPU 资源在首次 `render(...)` 的有效上下文中创建；能力不足时记录条件并结束实例。命令沿用 [C20-S1] 的 `GearIn_marker` 项保存实例，便于随后验证清理。比较各版本时分别进入新的战斗。

## 区分顶点数据与布局

方向标仍显示半径 `80 su` 的圆环和长度 `120 su` 的朝向线。vertex shader 负责把局部坐标放到舰船位置，fragment shader 负责生成线条。本节改变的是顶点来源：六个矩形顶点进入 VBO，VAO 记录 `a_position` 如何读取它们。客户端缓冲只在视口比例变化时更新 VBO，绘制指针始终读取 VBO。

## 从链接后的 program 取得输入位置

VBO 和 VAO 沿用原来的 shader 接口。Java 加载两份 `#version 120` 源码，完成编译、链接并记录 InfoLog，再查询一个 attribute 和六个 uniform：

```java
positionLocation = GL20.glGetAttribLocation(program, "a_position");
centerLocation = GL20.glGetUniformLocation(program, "u_center");
facingLocation = GL20.glGetUniformLocation(program, "u_facing");
colorLocation = GL20.glGetUniformLocation(program, "u_color");
geometryLocation = GL20.glGetUniformLocation(program, "u_geometry");
widthsLocation = GL20.glGetUniformLocation(program, "u_widthPixels");
worldPerPixelLocation = GL20.glGetUniformLocation(program, "u_worldPerPixel");
```

查询得到的 `a_position` location 用于建立布局。任何位置缺失时，本实例清理已创建资源；成功以后才继续创建几何。

## 在 VBO 中保存六个局部顶点

首次 `render(...)` 创建 program 后，`buildGeometry()` 准备一个可容纳 `12` 个浮点数的客户端缓冲，再把矩形上传到 VBO。`glBufferData` 分配并填入数据；视口比例变化时，`glBufferSubData` 更新含有像素边距的顶点。以下是 VBO 创建与上传的局部片段：

```java
vboId = GL15.glGenBuffers();
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
GL15.glBufferData(GL15.GL_ARRAY_BUFFER, coverageVertices, GL15.GL_DYNAMIC_DRAW);
```

`GL_DYNAMIC_DRAW` 对应可能更新的矩形顶点。边界在圆环和朝向线之外留出 `3 px` 换算得到的世界单位，片元 shader 仍决定哪些片元可见。

## 用 VAO 缓存 attribute 布局

VAO 保存 attribute 的启用状态、分量数和指向的 VBO。配置时先绑定 VAO 和 VBO，再指定 `a_position` 的读取方式；绘制时绑定 VAO，即可取回这套布局：

```java
vaoId = GL30.glGenVertexArrays();
GL30.glBindVertexArray(vaoId);
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
GL20.glEnableVertexAttribArray(positionLocation);
GL20.glVertexAttribPointer(positionLocation, 2, GL11.GL_FLOAT,
        false, 0, 0L);
```

位置由两个连续 `float` 组成，stride 为 `0` 表示紧密排列。这里的 `0L` 是当前 VBO 内的字节偏移。`glVertexAttribPointer` 把当时绑定的 VBO 记入 attribute 关系，随后解除 `GL_ARRAY_BUFFER` 绑定，VAO 中的关系仍然保留。

**VAO 记录顶点从哪里读、怎样读，uniform 继续传递舰船状态。** 位置、朝向与颜色各有自己的参数，尚未因建立 VAO 而保存到顶点布局中。

## 绘制时更新 uniform

绘制阶段保存进入方法时的 VAO、VBO、program 和矩阵模式。若当前视口的像素换算值改变，先用 `3 px` 边距更新矩形；随后提交舰船位置、朝向、颜色、几何尺寸和像素换算值，再发起一次 `GL_TRIANGLES`：

```java
GL30.glBindVertexArray(vaoId);
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
if (Float.compare(worldPerPixel, lastWorldPerPixel) != 0) {
    updateCoverageVertices(worldPerPixel);
    GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, coverageVertices);
    lastWorldPerPixel = worldPerPixel;
}
GL20.glUseProgram(program);
GL20.glUniform2f(centerLocation, entity.getLocation().x, entity.getLocation().y);
GL20.glUniform1f(facingLocation, trackedShip.getFacing());
GL20.glUniform2f(geometryLocation, RING_RADIUS, DIRECTION_LENGTH);
GL20.glUniform2f(widthsLocation, RING_LINE_WIDTH, DIRECTION_LINE_WIDTH);
GL20.glUniform1f(worldPerPixelLocation, worldPerPixel);
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
```

`u_center` 和 `u_facing` 让方向标跟随舰船，`u_worldPerPixel` 让片元线宽在缩放时保持 `2 px` 和 `3 px`。更新矩形只发生在换算值变化时；混合、矩阵、深度写入、纹理启用和 attribute 状态在 `finally` 中恢复。

## 释放 VBO 与 VAO

`buildGeometry()` 首次创建对象时，也会修改当前 VAO 和 VBO 绑定。它在配置之前保存原值，结束时通过 `finally` 恢复；每次绘制则另外恢复本次使用的 program、混合、矩阵与缓冲绑定。

::: warning VAO 不保存普通 ARRAY_BUFFER 绑定
attribute 指针记录自己的 VBO 关系，普通 `GL_ARRAY_BUFFER` 绑定仍是另一项上下文状态。创建和绘制结束后，各自恢复原 VAO 与原 ARRAY_BUFFER。
:::

VAO 记录 VBO 的 attribute 关系。`cleanup()` 先删除 VAO，再删除 VBO、program 和两个 shader；资源创建失败也进入这条路径，已创建的句柄删除后归零。重复清理时，这些句柄已经是 `0`。

## shader 与其他状态

vertex shader 通过 `varying v_local` 把局部坐标传给 fragment shader。fragment shader 使用 `length`、`clamp` 和 `smoothstep` 计算圆环与有限线段的覆盖率；VBO/VAO 只改变顶点数据的存放位置，不改变这段形状算法。当前方向标使用颜色 uniform，不依赖纹理；如果使用 `sampler2D`，Java 仍需绑定纹理单元并提交单元编号。Alpha 混合属于绘制状态，也不由 VAO 保存。

## 完整最小例

以下资源共同完成 VBO/VAO 版本的方向标。

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
        if (engine.isCombatOver() || !engine.isEntityInPlay(trackedShip)
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
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
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
            GL11.glPopClientAttrib();
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
                + trackedShip.getId());
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return DIRECTION_LENGTH + engine.getViewport().convertScreenWidthToWorldWidth(3f);
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

## 验证方案

前置数据：显卡和游戏渲染上下文支持 OpenGL 3.0 兼容上下文，Console Commands 已启用。

完成本方案中的移动、缩放和生命周期观察后，用下面的 Console Commands 命令连续清理当前实例两次；重新观察时进入新的战斗并登记：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

1. 执行 `gradle jar`，启动游戏并进入 `GearIn_test` 战斗，注册 `GearInVboVaoMarker`。
2. 移动并转向玩家舰，观察圆环中心、朝向线和屏幕线宽。
3. 缩放并横移视口，观察方向标是否仍固定在世界坐标，边缘是否完整。
4. 暂停并恢复战斗，记录方向标位置变化；再使舰船离场或死亡。
5. 结束战斗并重新进入另一场战斗，对同一测试实例重复调用 `cleanup()`，观察资源清理日志。

预期结果：

- 玩家舰中心出现半径 `80 su` 的程序化圆环和长度 `120 su` 的朝向线；
- 视口缩放后圆环线宽保持 `2 px`，朝向线保持 `3 px`，边缘没有被矩形裁掉；
- 移动和转向时方向标同步，暂停期间保持最后位置；
- VAO 和 VBO 在首次渲染创建，视口比例变化时 VBO 中的矩形边界随之更新；
- 舰船离场、死亡或战斗结束后方向标失效，重复 `cleanup()` 不产生二次删除；
- 舰船 Sprite、HUD 和其他战场画面的颜色、纹理、混合与位置保持原有表现。

## 故障分析

- **方向标不可见**：绘制阶段没有绑定正确的 VAO，或 VAO 记录的 VBO 已被删除。
    - **解决方案**：核对 `buildGeometry()` 中的 VAO、VBO 绑定顺序和绘制阶段的 `glBindVertexArray(vaoId)`。

- **顶点位置异常**：`glVertexAttribPointer` 的分量数、stride 或偏移与 VBO 数据布局不一致。
    - **解决方案**：确认每个顶点由两个 `GL_FLOAT` 组成，stride 为 `0`，偏移为 `0L`。

- **视口缩放后线宽变化**：`u_worldPerPixel` 没有使用当前视口的世界单位换算值。
    - **解决方案**：核对 `convertScreenWidthToWorldWidth(1f)` 的提交和 fragment shader 的半线宽计算。

- **其他画面状态异常**：VAO、VBO、program、矩阵或混合状态没有在 `finally` 中恢复。
    - **解决方案**：按进入顺序保存状态，并在绘制结束后恢复之前的 VAO、VBO、program 和矩阵模式。

- **战斗结束后资源仍存在**：插件没有失效，或 `cleanup()` 没有按顺序删除 VAO、VBO、program 和 shader。
    - **解决方案**：检查 `advance(...)` 的战斗状态判断与 `cleanup()` 的句柄归零逻辑。

## 总结

VBO 保存可复用的局部顶点，VAO 保存 attribute 与 VBO 的布局关系，uniform 继续承载每帧变化的舰船和视口参数。绘制阶段绑定 VAO，按视口比例更新矩形边界，再提交图元；[C20-S2] 的程序化片元形状保持一致。

共享 OpenGL 上下文要求 VAO、VBO、program、矩阵、混合和深度状态都有明确边界。资源创建失败和战斗结束都进入同一个清理方法；创建与绘制分别恢复调用前的绑定，方向标才能与其他战场画面共存。

## 建议参考项

- `org.lwjgl.opengl.GL15`：创建、上传、绑定和删除 VBO；
- `org.lwjgl.opengl.GL30`：创建、绑定和删除 VAO；
- `org.lwjgl.opengl.GL20`：配置 attribute、uniform 和 shader program；
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层渲染插件生命周期；
- `com.fs.starfarer.api.combat.ViewportAPI`：获取 Alpha 和屏幕到世界单位的换算。
