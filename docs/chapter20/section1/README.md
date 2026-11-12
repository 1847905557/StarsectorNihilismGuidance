# 使用 Java OpenGL

本节在玩家舰周围绘制圆环与朝向线，用一个固定形状说明 Java OpenGL 的完整调用。局部顶点经过平移与旋转成为世界位置，颜色和混合决定画面，绘制结束再恢复共享状态。分层插件提供入口，这些数据与状态由方向标自己安排。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 解释 OpenGL 上下文、状态与绘制命令之间的关系；
- 使用直接 `FloatBuffer` 和客户端顶点数组提交基础图元；
- 使用模型视图矩阵把局部顶点平移和旋转到世界坐标；
- 保存并恢复 OpenGL 状态，避免影响同一战斗帧中的其他绘制。

## 前置知识

- [C8-S5] 的分层渲染插件、渲染图层、承载实体和视口剔除；
- [C8-S1] 的每帧推进、暂停判断和战斗实例边界；
- 理解 Java 数组、循环、构造器和字段；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 保存资源并在战斗登记

将完整类保存到 `src/data/scripts/gearin/combat/GearInOpenGLMarker.java`。构建 JAR 并重启，进入测试战斗、部署并控制玩家舰后，通过 Console Commands 执行：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInOpenGLMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

**每场战斗登记一次当前版本。**`init(...)` 准备 CPU 缓冲并同步承载实体，`render(...)` 使用当前战斗的上下文。命令把实例放入当前战斗的 `GearIn_marker` 项，便于随后验证清理。比较各版本时分别进入新的战斗。

## 在渲染回调使用当前上下文

游戏通过 LWJGL 2 提供 OpenGL 接口。`GL11` 包含矩阵、颜色、混合、线宽、客户端数组和基础绘制命令；`GL15`、`GL20` 用于查询需要保存的缓冲区绑定与 shader program。OpenGL 命令作用于当前线程的渲染上下文，因此本页把绘制放在分层插件的 `render(...)` 中。

[C8-S5] 介绍了分层插件和承载实体。本例在 `init(...)` 准备可复用顶点，在 `advance(...)` 同步承载实体，在 `render(...)` 绘制，再由 `isExpired()` 与 `cleanup()` 结束实例。战斗入口把插件交给引擎：

```java
if (ship != null) {
    engine.addLayeredRenderingPlugin(new GearInOpenGLMarker(ship));
}
```

引擎会在 `ABOVE_SHIPS_AND_MISSILES_LAYER` 调用方向标的 `render(...)`。方向标与舰船共享世界坐标，`getRenderRadius()` 覆盖 `120 su` 与当前 `3 px` 世界边距，使引擎的视口剔除范围覆盖最长的朝向线。

方向标的几何固定，所以本例在初始化时准备顶点数组，用 `glDrawArrays(...)` 复用。`glBegin(...)`、逐顶点 `glVertex2f(...)`、`glEnd()` 也能表达绘制顺序，不过逐顶点调用会留在每个渲染帧；此处数组直接承担固定几何的提交。

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

缓冲区完成后，位置与朝向变化都无需重新计算圆环顶点。绘制阶段只使用当前模型视图矩阵放置它，再提交同一份数据；几何与舰船状态由此能够分别更新。

## 用矩阵放置方向标

局部顶点围绕原点建立，因此同一组数据可以绘制到任何位置和朝向。模型视图矩阵先把原点平移到承载实体的位置，再绕 Z 轴旋转到舰船朝向：

```java
GL11.glMatrixMode(GL11.GL_MODELVIEW);
GL11.glPushMatrix();
GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
GL11.glRotatef(trackedShip.getFacing(), 0f, 0f, 1f);
```

OpenGL 按当前矩阵解释随后提交的每个局部顶点。圆环围绕舰船中心，局部正 X 轴上的朝向线随 `trackedShip.getFacing()` 旋转。矩阵使用 `glPushMatrix()` 与 `glPopMatrix()` 成对管理；缺少恢复时，后续渲染器会继承方向标的平移与旋转，整个画面都可能开始追随同一艘船，OpenGL 对这种团结并没有意见。

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

绘制结束以后，还要把上下文交还给同一帧中的其他渲染器。**恢复的是调用前的状态，包括已经绑定的对象。** 方向标先记录矩阵模式、program 与 `GL_ARRAY_BUFFER`，属性栈保存混合、颜色、线宽、纹理启用和客户端数组。

支持 OpenGL 3.0 时还保存原 VAO，再绑定默认 VAO `0`。客户端指针在默认对象中建立，所以随后才保存它的数组布局：

```java
boolean vaoAvailable = GLContext.getCapabilities().OpenGL30;
int previousVao = vaoAvailable ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) : 0;
int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT
        | GL11.GL_CURRENT_BIT
        | GL11.GL_ENABLE_BIT
        | GL11.GL_LINE_BIT);
if (vaoAvailable) {
    GL30.glBindVertexArray(0);
}
GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
```

固定管线绘制使用 program `0`，客户端指针使用 VBO 绑定 `0`。结束时 `finally` 弹出矩阵和属性，恢复默认数组布局后再绑定原 VAO，最后恢复 VBO、program 与矩阵模式。OpenGL 2 兼容路径按同样职责处理数组和其他状态。

这份恢复使方向标能与其他画面共存。一个效果自身画得正确，仍可能把下一项绘制的颜色、布局或变换改掉；原有舰船、HUD 与粒子因此也属于本节验证的观察对象。

## OpenGL 职责速查表

| 对象或调用                                        | 当前职责                             |
| ------------------------------------------------- | ------------------------------------ |
| `BufferUtils.createFloatBuffer(...)`              | 创建 OpenGL 可读取的直接客户端缓冲区 |
| `glPushMatrix()` / `glPopMatrix()`                | 隔离当前模型视图矩阵                 |
| `glTranslatef(...)` / `glRotatef(...)`            | 把局部顶点放到舰船世界位置和朝向     |
| `glVertexPointer(...)`                            | 描述客户端缓冲区中的顶点布局         |
| `glDrawArrays(...)`                               | 按图元类型提交连续顶点               |
| `glBlendFunc(...)`                                | 设置源颜色与已有画面的混合比例       |
| `glPushAttrib(...)` / `glPopAttrib()`             | 保存并恢复固定管线状态               |
| `glPushClientAttrib(...)` / `glPopClientAttrib()` | 保存并恢复客户端数组状态             |

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
        if (!valid) {
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
        if (!valid) {
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
                | GL11.GL_LINE_BIT);
        if (vaoAvailable) {
            GL30.glBindVertexArray(0);
        }
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
        valid = false;
        ringVertices = null;
        directionVertices = null;
        if (!cleanupLogged) {
            cleanupLogged = true;
            LOG.info("GearIn OpenGL marker cleanup: ship="
                    + trackedShip.getId());
        }
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

## 验证方案

前置数据：显卡和游戏渲染上下文支持 OpenGL 2 兼容管线，Console Commands 已启用。

完成本方案中的移动、缩放和生命周期观察后，用下面的 Console Commands 命令连续清理当前实例两次；重新观察时进入新的战斗并登记：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

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
- 圆环位于舰船与导弹上方，离开有效视口后由 `120 su` 加当前 `3 px` 世界边距参与剔除；
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
