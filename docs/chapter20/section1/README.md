# 使用 Java OpenGL

原版的分层渲染插件只负责给出回调时机和绘图层级，真要把线条或几何图形画上屏幕，还得自己调 LWJGL 的 OpenGL 接口。

这页从一只绕着玩家舰画圈和朝向线的简单方向标开始：局部顶点放进直接 `FloatBuffer`，借助模型视图矩阵摆到舰船位置与朝向，画完再把改动过的上下文状态原封不动还给游戏。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C8-S1] 的每帧推进、暂停判断与战斗实例边界；
- [C8-S5] 的分层渲染插件、渲染图层、承载实体与视口剔除；
- 熟悉 Java 数组、循环、构造器和字段，能在 `starsector.log` 定位类名。

## 把分层插件与直接缓冲接进战斗

先准备一份围绕局部原点 `(0, 0)` 建立的固定几何。方向标由两部分组成：半径 `80 su`、用 `64` 段拟合的闭合圆环，以及从 `(0, 0)` 沿局部正 X 轴伸出 `120 su` 的朝向线。因为顶点坐标只随舰船位置和朝向整体搬动，不需要每帧重新算三角函数，在 `init(...)` 里用 `BufferUtils.createFloatBuffer(...)` 分配两块 OpenGL 可直接读取的堆外缓冲即可：

```java
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
```

`flip()` 会把缓冲区从写入模式切回读取状态，之后每次绘制只需 `rewind()` 回到开头位置就能直接复用。分层插件挂在 `ABOVE_SHIPS_AND_MISSILES_LAYER` 上，`getRenderRadius()` 返回 `120 su` 加上当前 `3 px` 换算出的世界边距，确保视口剔除覆盖完整朝向线；`init(...)` 和 `advance(...)` 顺手做好 `engine == null` 与 `trackedShip == null` 守卫并持续同步承载实体坐标。

## 用模型视图矩阵定位并用客户端数组绘制

有了围绕原点建好的顶点，绘制时用模型视图矩阵把局部坐标系先平移到 `entity.getLocation()`，再绕 Z 轴旋转到 `trackedShip.getFacing()`：

```java
GL11.glMatrixMode(GL11.GL_MODELVIEW);
GL11.glPushMatrix();
matrixPushed = true;
GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
GL11.glRotatef(trackedShip.getFacing(), 0f, 0f, 1f);
```

`glPushMatrix()` 与 `finally` 里的 `glPopMatrix()` 成对出现，避免方向标的平移和旋转顺着状态机一路流给后续绘制的舰船与 HUD。提交顶点前，还要把 `GL_ARRAY_BUFFER` 显式绑定到 `0`(告诉 `glVertexPointer` 把我们的 `FloatBuffer` 当客户端内存而不是某个 VBO 里的偏移量)，同时关闭 `GL_TEXTURE_2D` 防止固定管线把上一张贴图混进纯色线条里，然后启用标准 Alpha 混合并将颜色乘上 `viewport.getAlphaMult()`：

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

## 保存并还原进入上下文前的全部状态

游戏和各 MOD 的着色器 (shader)、VAO 与缓冲对象全都跑在同一个 OpenGL 上下文里。虽然本例用的是固定管线 (绘制前设 `GL20.glUseProgram(0)`)，仍可能被外层留下的 VAO 或着色器程序干扰；反之，自己改完线宽、混合和客户端顶点数组状态也必须原样交还。

如果显卡支持 OpenGL 3.0，还要格外留神 VAO 与 `glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)` 的顺序：先记录原 VAO `previousVao` 并绑定默认 VAO `0`，再压栈客户端属性；绘制完毕后在 `finally` 先 `glPopClientAttrib()` 还原 VAO `0` 的状态，再绑定回 `previousVao`，最后依次恢复 `previousArrayBuffer`、`previousProgram` 和 `previousMatrixMode`：

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

| 对象或调用                                        | 当前职责                              |
| ------------------------------------------------- | ------------------------------------- |
| `BufferUtils.createFloatBuffer(...)`              | 分配 OpenGL 可读取的直接 `FloatBuffer` |
| `glPushMatrix()` / `glPopMatrix()`                | 隔离本次模型视图矩阵变换              |
| `glTranslatef(...)` / `glRotatef(...)`            | 把局部顶点放到舰船位置与朝向          |
| `glVertexPointer(...)`                            | 指定客户端顶点的分量数与步幅          |
| `glDrawArrays(...)`                               | 按图元类型提交连续顶点                |
| `glBlendFunc(...)`                                | 配置标准透明混合比例                  |
| `glPushAttrib(...)` / `glPopAttrib()`             | 保存与还原固定管线状态                |
| `glPushClientAttrib(...)` / `glPopClientAttrib()` | 在默认 VAO 上保存与还原客户端顶点数组 |

## 完整最小例

将完整类保存到 `src/data/scripts/gearin/combat/GearInOpenGLMarker.java`(战斗接入处调用 `engine.addLayeredRenderingPlugin(new GearInOpenGLMarker(ship))`)：

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
        if (trackedShip == null) {
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
                    + (trackedShip == null ? "null" : trackedShip.getId()));
        }
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

### 进游戏核对

1. **在测试战斗中登记方向标**：执行 `gradle jar` 构建并重启进 `GearIn_test` 战斗，部署玩家舰后运行下面的 Console Commands 命令登记一次：

```text
runcode com.fs.starfarer.api.combat.CombatEngineAPI e = com.fs.starfarer.api.Global.getCombatEngine(); com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = new data.scripts.gearin.combat.GearInOpenGLMarker(e.getPlayerShip()); e.addLayeredRenderingPlugin(marker); e.getCustomData().put("GearIn_marker", marker);
```

2. **核对移动、转向、暂停与状态共存**：驾驶舰船旋转与移动时，青色半透明 `80 su` 圆环与 `120 su` 朝向线应当严丝合缝跟随，暂停保持末帧状态；舰船贴图、HUD 与战场粒子不应受线宽、纹理关闭或模型视图矩阵污染。
3. **核对失效与幂等清理**：让舰船离场/阵亡或在当前战斗执行两次 `cleanup()` 命令，核对日志仅打一条 `GearIn OpenGL marker cleanup`，再次开启新战斗并登记时重打 `init`：

```text
runcode com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin marker = (com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin) com.fs.starfarer.api.Global.getCombatEngine().getCustomData().get("GearIn_marker"); marker.cleanup(); marker.cleanup();
```

## 常见问题

- **方向标完全不显示**：客户端顶点指针提交时仍然绑定着其他渲染器的 VBO，导致 `FloatBuffer` 被当成了显存偏移量。
    - **解决方案**：在调用 `glVertexPointer(...)` 前确保执行 `glBindBuffer(GL_ARRAY_BUFFER, 0)` 并在 `finally` 还原原绑定。

- **方向标始终停在地图原点或朝向卡死**：`entity.getLocation()` 没同步舰船位置，或模型视图矩阵没有先平移再绕 Z 轴旋转。
    - **解决方案**：在 `init(...)` 与 `advance(...)` 中更新承载实体位置，并在 `render(...)` 里使用 `glTranslatef(...)` 与 `glRotatef(trackedShip.getFacing(), 0f, 0f, 1f)`。

- **后续舰船、UI 或粒子全部跟着旋转或变色**：`finally` 中漏了弹出矩阵、属性栈、program 或原 VAO/VBO 绑定。
    - **解决方案**：先 `glPopMatrix()`、`glPopClientAttrib()`、`glPopAttrib()`，再还原 `previousVao`、`previousArrayBuffer`、`previousProgram` 与 `previousMatrixMode`。

- **舰船阵亡后圆环仍残留在场上**：`advance(...)` 没有检测战斗结束或目标失效。
    - **解决方案**：在 `advance(...)` 核对 `isCombatOver()`、`isEntityInPlay(trackedShip)` 与 `isAlive()`，将 `valid` 置为 `false` 通知引擎回收。

## 建议参考项

- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层渲染插件的生命周期基础类；
- `com.fs.starfarer.api.combat.CombatEngineLayers`：战斗渲染图层枚举；
- `com.fs.starfarer.api.combat.ViewportAPI`：当前战斗视口与画面 Alpha；
- `org.lwjgl.BufferUtils`：创建 OpenGL 可读取的直接缓冲区；
- `org.lwjgl.opengl.GL11`：固定管线、矩阵、客户端数组与基础绘制入口；
- `org.lwjgl.opengl.GL15`：缓冲区绑定状态入口；
- `org.lwjgl.opengl.GL20`：当前着色器 program 状态入口。
