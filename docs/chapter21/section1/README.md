# 使用 Java OpenGL

本节使用 Java 和 LWJGL 2 绘制一个跟随舰船的方向标。圆环与朝向线从局部顶点进入客户端顶点数组，经过模型视图矩阵落到战场世界坐标，并在绘制结束后把 OpenGL 状态完整交还给游戏。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 解释 OpenGL 上下文、状态与绘制命令之间的关系；
- 使用直接 `FloatBuffer` 和客户端顶点数组提交基础图元；
- 使用模型视图矩阵把局部顶点平移和旋转到世界坐标；
- 保存并恢复 OpenGL 状态，避免影响同一战斗帧中的其他绘制。

## 前置知识

- [C8-S7] 的分层渲染插件、渲染图层、承载实体和视口剔除；
- [C8-S3] 的每帧推进、暂停判断和战斗实例边界；
- 理解 Java 数组、循环、构造器和字段；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 理解 Java 与 OpenGL 的边界

Starsector `0.98a-RC8` 通过 LWJGL 2 暴露 OpenGL 接口，并提供能够使用固定管线和客户端顶点数组的兼容模式上下文。`GL11` 包含矩阵、颜色、混合、线宽、客户端数组和基础绘制命令；`GL15`、`GL20` 提供本节用于隔离状态的缓冲区绑定与着色器程序查询。调用这些方法时，命令作用于当前线程已经激活的 OpenGL 上下文，因此绘制代码必须在分层插件的 `render(...)` 中执行。

[C8-S7] 已经完成分层插件生命周期。本节只保留它与 OpenGL 有关的职责：`init(...)` 准备复用数据，`advance(...)` 同步承载实体，`render(...)` 进入当前渲染上下文，`isExpired()` 与 `cleanup()` 结束实例。战斗入口仍然把实例交给引擎：

```java
if (ship != null) {
    engine.addLayeredRenderingPlugin(new GearInOpenGLMarker(ship));
}
```

引擎会在 `ABOVE_SHIPS_AND_MISSILES_LAYER` 调用方向标的 `render(...)`。方向标与舰船共享世界坐标，`getRenderRadius()` 返回 `120f`，使引擎的视口剔除范围覆盖最长的朝向线。

::: warning 立即模式只用于认识绘制过程
`glBegin(...)`、逐顶点 `glVertex2f(...)`、`glEnd()` 能直观展示选择图元、提交顶点和结束提交的关系，但实际 MOD 渲染严禁采用这种逐顶点立即模式。它把大量 Java 调用放进每个渲染帧，也难以复用已经准备好的顶点数据。本节完整成品使用客户端顶点数组和 `glDrawArrays(...)`。
:::

## 把几何保存在局部坐标

方向标由半径 `80 su` 的圆环和长度 `120 su` 的朝向线组成。圆环使用 `64` 个顶点，在常见缩放距离下已经足够平滑，同时把每次绘制的顶点数量保持在一个明确的小范围。朝向线从 `(0, 0)` 延伸到 `(120, 0)`；局部正 X 轴就是舰船朝向。

`BufferUtils.createFloatBuffer(...)` 创建可由 OpenGL 读取的直接缓冲区。下面的局部片段在 `init(...)` 中一次写入圆环顶点，每个顶点依次保存 X、Y 两个 `float`：

```java
ringVertices = BufferUtils.createFloatBuffer(RING_SEGMENTS * 2);
for (int i = 0; i < RING_SEGMENTS; i++) {
    double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
    ringVertices.put((float) Math.cos(angle) * RING_RADIUS);
    ringVertices.put((float) Math.sin(angle) * RING_RADIUS);
}
ringVertices.flip();
```

`flip()` 把缓冲区从写入状态切换到读取状态。朝向线只需要两个顶点：

```java
directionVertices = BufferUtils.createFloatBuffer(4);
directionVertices.put(0f).put(0f);
directionVertices.put(DIRECTION_LENGTH).put(0f);
directionVertices.flip();
```

两个缓冲区随后由每一帧的 `render(...)` 复用。几何固定时，把三角函数和内存分配留在初始化阶段，可以让渲染循环只处理状态、变换和提交。

## 用矩阵放置方向标

局部顶点围绕原点建立，因此同一组数据可以绘制到任何位置和朝向。模型视图矩阵先把原点平移到承载实体的位置，再绕 Z 轴旋转到舰船朝向：

```java
GL11.glMatrixMode(GL11.GL_MODELVIEW);
GL11.glPushMatrix();
GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
GL11.glRotatef(trackedShip.getFacing(), 0f, 0f, 1f);
```

OpenGL 按当前矩阵解释随后提交的每个局部顶点。圆环围绕舰船中心，局部正 X 轴上的朝向线随 `trackedShip.getFacing()` 旋转。矩阵必须使用 `glPushMatrix()` 与 `glPopMatrix()` 成对管理；缺少恢复时，后续渲染器会继承方向标的平移与旋转，整个画面都可能开始追随同一艘船，OpenGL 对这种团结并没有意见。

## 提交客户端顶点数组

客户端顶点数组让 Java 一次指定一段连续顶点，再由 `glDrawArrays(...)` 完成绘制。提交前把 `GL_ARRAY_BUFFER` 绑定设为 `0`，这样 `glVertexPointer(..., FloatBuffer)` 会把参数解释为客户端内存，而不是当前 VBO 内的字节偏移。

```java
GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

ringVertices.rewind();
GL11.glVertexPointer(2, 0, ringVertices);
GL11.glLineWidth(RING_LINE_WIDTH);
GL11.glDrawArrays(GL11.GL_LINE_LOOP, 0, RING_SEGMENTS);

directionVertices.rewind();
GL11.glVertexPointer(2, 0, directionVertices);
GL11.glLineWidth(DIRECTION_LINE_WIDTH);
GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
```

`glVertexPointer(2, 0, buffer)` 表示每个顶点有两个分量，连续顶点之间没有额外字段。`GL_LINE_LOOP` 会把最后一个圆环顶点连接回第一个顶点；`GL_LINES` 把两个顶点解释成一条独立线段。圆环使用 `2 px` 线宽，朝向线使用 `3 px` 线宽，使方向在缩放后仍然容易辨认。

## 设置颜色与透明混合

方向标使用青色和 `0.75` 基础 Alpha。视口的 `getAlphaMult()` 参与最终 Alpha，使效果服从游戏的画面淡出：

```java
GL11.glDisable(GL11.GL_TEXTURE_2D);
GL11.glEnable(GL11.GL_BLEND);
GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
GL11.glColor4f(0.1f, 0.9f, 1f, 0.75f * viewport.getAlphaMult());
```

标准 Alpha 混合使用源颜色的 Alpha 决定覆盖比例。方向标只绘制纯色线条，因此绘制期间关闭二维纹理；否则当前纹理状态可能继续参与固定管线输出，让线条颜色取决于之前恰好绑定了什么纹理。

## 恢复进入方法前的状态

OpenGL 使用共享状态。同一战斗帧中的多个渲染器会依次使用当前上下文，方向标必须恢复自己修改过的内容。完整类在绘制前保存当前矩阵模式、shader program 和 `GL_ARRAY_BUFFER` 绑定，再使用属性栈保存混合、颜色、线宽、纹理启用状态及客户端顶点数组状态：

```java
int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
        | GL11.GL_CURRENT_BIT
        | GL11.GL_ENABLE_BIT
        | GL11.GL_LINE_BIT);
GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
```

固定管线的颜色和客户端顶点数组需要在 program `0`、VBO 绑定 `0` 的状态下提交。绘制结束后，`finally` 按对应关系弹出矩阵与属性，并恢复之前的 program、VBO 绑定和矩阵模式。即使绘制过程抛出 Java 异常，已经进入的状态恢复步骤仍会执行。

## OpenGL 职责速查表

| 对象或调用 | 当前职责 |
| --- | --- |
| `BufferUtils.createFloatBuffer(...)` | 创建 OpenGL 可读取的直接客户端缓冲区 |
| `glPushMatrix()` / `glPopMatrix()` | 隔离当前模型视图矩阵 |
| `glTranslatef(...)` / `glRotatef(...)` | 把局部顶点放到舰船世界位置和朝向 |
| `glVertexPointer(...)` | 描述客户端缓冲区中的顶点布局 |
| `glDrawArrays(...)` | 按图元类型提交连续顶点 |
| `glBlendFunc(...)` | 设置源颜色与已有画面的混合比例 |
| `glPushAttrib(...)` / `glPopAttrib()` | 保存并恢复固定管线状态 |
| `glPushClientAttrib(...)` / `glPopClientAttrib()` | 保存并恢复客户端数组状态 |

## 完整最小例

下面的完整类把固定几何、分层生命周期、局部坐标变换、客户端顶点数组和状态恢复组合为一个可直接接入战斗的方向标。

::: details 完整成品：GearInOpenGLMarker

```java
package data.scripts.gearin.combat;

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
 * 使用客户端顶点数组绘制跟随舰船的圆环与朝向线。
 * 顶点缓冲只在初始化时创建，render() 负责局部坐标变换和绘制，
 * 并在返回前恢复进入方法时的 OpenGL 状态。
 */
public class GearInOpenGLMarker extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInOpenGLMarker.class);

    private static final int RING_SEGMENTS = 64;
    private static final float RING_RADIUS = 80f;
    private static final float DIRECTION_LENGTH = 120f;
    private static final float RING_LINE_WIDTH = 2f;
    private static final float DIRECTION_LINE_WIDTH = 3f;

    private final ShipAPI trackedShip;

    private CombatEngineAPI engine;
    private FloatBuffer ringVertices;
    private FloatBuffer directionVertices;
    private boolean valid = true;
    private boolean cleanupLogged = false;

    public GearInOpenGLMarker(ShipAPI trackedShip) {
        this.trackedShip = trackedShip;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (engine == null || trackedShip == null) {
            valid = false;
            return;
        }

        ringVertices = BufferUtils.createFloatBuffer(RING_SEGMENTS * 2);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            ringVertices.put((float) Math.cos(angle) * RING_RADIUS);
            ringVertices.put((float) Math.sin(angle) * RING_RADIUS);
        }
        ringVertices.flip();

        directionVertices = BufferUtils.createFloatBuffer(4);
        directionVertices.put(0f).put(0f);
        directionVertices.put(DIRECTION_LENGTH).put(0f);
        directionVertices.flip();

        entity.getLocation().set(trackedShip.getLocation());
        LOG.info("GearIn OpenGL marker init: ship=" + trackedShip.getId());
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
        if (!valid || entity == null || ringVertices == null || directionVertices == null) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean matrixPushed = false;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_ENABLE_BIT
                | GL11.GL_LINE_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL20.glUseProgram(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;
            GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
            GL11.glRotatef(trackedShip.getFacing(), 0f, 0f, 1f);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0.1f, 0.9f, 1f, 0.75f * viewport.getAlphaMult());
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

            ringVertices.rewind();
            GL11.glVertexPointer(2, 0, ringVertices);
            GL11.glLineWidth(RING_LINE_WIDTH);
            GL11.glDrawArrays(GL11.GL_LINE_LOOP, 0, RING_SEGMENTS);

            directionVertices.rewind();
            GL11.glVertexPointer(2, 0, directionVertices);
            GL11.glLineWidth(DIRECTION_LINE_WIDTH);
            GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
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
        ringVertices = null;
        directionVertices = null;
        if (!cleanupLogged) {
            cleanupLogged = true;
            LOG.info("GearIn OpenGL marker cleanup: ship="
                    + (trackedShip == null ? "null" : trackedShip.getId()));
        }
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

## 验证方案

前置数据：启用中的 GearIn MOD 已包含 `GearInOpenGLMarker`，战斗入口能够取得一艘当前战斗中的 `ShipAPI` 并注册方向标。

1. 在 MOD 目录执行 `gradle jar`，确认 JAR 中包含 `data/scripts/gearin/combat/GearInOpenGLMarker.class`。
2. 启动游戏并进入 `GearIn_test` 战斗，为玩家舰注册一个 `GearInOpenGLMarker`。
3. 移动并转向玩家舰，观察圆环中心和朝向线。
4. 暂停战斗，记录方向标位置；恢复战斗后继续移动舰船。
5. 同时观察舰船 Sprite、HUD 和战场粒子，再结束当前舰船或战斗。
6. 进入第二场战斗并重新注册方向标，观察初始化与清理日志。

预期结果：

- 玩家舰中心出现半径 `80 su` 的青色半透明圆环；
- `120 su` 朝向线与玩家舰朝向同步；
- 舰船移动时圆环中心持续跟随，暂停期间保持最后位置；
- 圆环位于舰船与导弹上方，离开有效视口后由 `120 su` 渲染半径参与剔除；
- 舰船 Sprite、HUD 和战场粒子的颜色、纹理、混合与位置保持原有表现；
- 舰船离场、死亡或战斗结束后方向标消失，日志出现一次包含舰船 ID 的 `GearIn OpenGL marker cleanup: ship=...`；
- 第二场战斗创建新的缓冲区和插件状态，日志重新出现 `GearIn OpenGL marker init`。

## 故障分析

- **方向标完全不可见**：客户端顶点指针建立时仍绑定着其他渲染器的 VBO，`FloatBuffer` 没有被解释为客户端内存。
    - **解决方案**：在 `glVertexPointer(...)` 前调用 `glBindBuffer(GL_ARRAY_BUFFER, 0)`，绘制后恢复原绑定。

- **方向标出现在战场原点**：承载实体没有在初始化和有效推进帧同步到舰船位置。
    - **解决方案**：在 `init(...)` 与 `advance(...)` 中把 `entity.getLocation()` 设置为 `trackedShip.getLocation()`。

- **朝向线角度固定**：模型视图矩阵没有使用当前舰船朝向，或旋转轴不是 Z 轴。
    - **解决方案**：在平移后调用 `glRotatef(trackedShip.getFacing(), 0f, 0f, 1f)`。

- **其他战斗画面随后变色或错位**：方向标修改的 program、缓冲区绑定、矩阵或固定管线状态没有恢复。
    - **解决方案**：核对 `finally` 中的矩阵、属性栈、program、`GL_ARRAY_BUFFER` 和矩阵模式恢复顺序。

- **舰船失效后方向标仍存在**：`advance(...)` 没有把离场、死亡或战斗结束转换为插件失效状态。
    - **解决方案**：检查 `isCombatOver()`、`isEntityInPlay(...)` 与 `isAlive()`，并让 `isExpired()` 返回失效结果。

- **每帧产生内存分配**：顶点缓冲区在 `render(...)` 中重复创建。
    - **解决方案**：只在 `init(...)` 中创建两个直接 `FloatBuffer`，绘制前使用 `rewind()` 复用已有数据。

## 总结

Java OpenGL 绘制可以拆成稳定的数据流：初始化阶段准备局部顶点，推进阶段同步生命周期和承载实体，渲染阶段设置矩阵与固定管线状态，再用客户端顶点数组提交图元。方向标只保存一份几何数据，位置和朝向由模型视图矩阵逐帧决定。

共享 OpenGL 上下文要求每个渲染器管理自己的状态边界。保存 program、VBO 绑定、矩阵、颜色、混合、线宽和客户端数组状态，再在 `finally` 中恢复，是这个成品能够与游戏其他画面共存的关键。

## 建议参考项

- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层渲染插件的生命周期基础类；
- `com.fs.starfarer.api.combat.CombatEngineLayers`：战斗渲染图层枚举；
- `com.fs.starfarer.api.combat.ViewportAPI`：当前战斗视口与画面 Alpha；
- `org.lwjgl.BufferUtils`：创建 OpenGL 可读取的直接缓冲区；
- `org.lwjgl.opengl.GL11`：固定管线、矩阵、客户端数组与基础绘制入口；
- `org.lwjgl.opengl.GL15`：缓冲区绑定状态入口；
- `org.lwjgl.opengl.GL20`：当前 shader program 状态入口。
