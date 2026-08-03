# 分层渲染、视口与清理

本节把战斗域的 "分层渲染" 讲透：`CombatLayeredRenderingPlugin` 的注册与回调模型 (`init` / `advance` / `render` / `isExpired` / `cleanup`)、`CombatEngineLayers` 的层级顺序与引擎渲染管线、`ViewportAPI` 的视口坐标与缩放语义，以及分层渲染插件的清理纪律 (`cleanup()` 的调用时机与战斗结束的差异)。本节的分层渲染插件是**另一条独立的插件通道**：由 `engine.addLayeredRenderingPlugin(...)` 注册、以战斗实体为承载、按层渲染，推进语义与清理时机都和前置单元已讲解的 `EveryFrameCombatPlugin` 不同。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `CombatLayeredRenderingPlugin` 的七个接口方法各自的调用时机，以及它与 `EveryFrameCombatPlugin` 的三处关键差异 (注册通道、暂停时是否推进、清理时机)；
- 说出 `CombatEngineLayers` 的层序与三个特例 (顶层三图层在粒子系统之后单独渲染、`PHASED_SHIPS_LAYER` 的位置约束)，解释 `getActiveLayers()` 与 `render(layer, ...)` 的一对多关系；
- 说出渲染剔除机制 (`isNearViewport(entity.getLocation(), getRenderRadius())`) 并正确维护承载实体位置与渲染半径；
- 说出 `ViewportAPI` 的坐标语义 (世界单位、`getViewMult()`、双向转换、`setExternalControl` 接管视口的条件)；
- 说出 `cleanup()` 的两个事实：实体移除时由引擎调用、战斗结束 `destroyInstance` **不调用**，以及 "主动失效 → 引擎移除 → `cleanup()`" 的正确清理链；
- 从症状定位典型故障：在 `advance()` 里绘制、位置不同步导致视口内消失、`isExpired()` 恒为 false 导致残留、`cleanup()` 漏释放 GL 资源、多图层不按 `layer` 分支。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- MOD 目录结构、`starsector.log`(C01-S01)：本节所有可观察结果都落在日志与画面里。
- 插件模型与暂停守卫 (C07-S01)：本节的分层渲染插件同款 `engine.isPaused()` 守卫做法，但推进语义不同 —— 见 "推进与暂停"。
- 战役 (mission) 与生涯 (campaign) 概念、`GearIn_test` 测试战役 (C03-S01)：本节用它做验证。
- 编译 JAR 链路 (C06-S01)：本节示例编入同一个 `jars\GearIndustry.jar`。
- `Global` 生命周期 —— 禁止跨战斗缓存引擎引用 (C06-S03)：本节把同一条纪律延伸到渲染插件与它持有的舰船引用。
- 战斗状态读取 —— 实体存活性、`isCombatOver()`(C07-S02)：本节消费存活判定做失效。
- 战斗插件生命周期与清理纪律 (C07-S05)：本节把 "战斗结束没有清理回调" 拓展到渲染插件侧，并指出两条插件通道的差异。
- shader 与 GLSL(C14-S01)：完整讲解在该单元；本节只用 `GL11` 立即模式原语，不涉及 shader 资源。

## 文件与 API 链路

本节涉及的接口与注册表面：

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `CombatLayeredRenderingPlugin` | 分层渲染插件接口 (`init` / `cleanup` / `isExpired` / `advance` / `getActiveLayers` / `getRenderRadius` / `render`)| `com.fs.starfarer.api.combat` |
| `BaseCombatLayeredRenderingPlugin` | 七个回调的空实现基类 (默认层 `BELOW_INDICATORS_LAYER`、默认剔除半径 `100`)| 建议继承 (成熟实例全部继承它)|
| `CombatEngineLayers` | 层级枚举 (23 个值，声明顺序即层序)| `getActiveLayers()` 的返回值与 `render` 的参数 |
| `CombatEngineAPI.addLayeredRenderingPlugin` | 战斗内注册入口 | 返回承载实体 `CombatEntityAPI`(引擎把插件包装成战斗实体)|
| `ViewportAPI` | 视口：可见区左下角、宽高 (世界单位)、缩放、双向转换 | `engine.getViewport()` |
| `CombatEntityAPI` | `init` 的参数与承载实体 (位置同步、存活/失效判定)| 引擎把插件包装成战斗实体 |
| `LayeredRenderer` | 引擎内部按层渲染器 (`com.fs.graphics`)| 引擎构造时创建，`renderer.add / remove / renderExcluding / renderOnly` |

### 注册与回调速查 (RC8)

| 回调 | 时机 | 说明 |
|---|---|---|
| `init(CombatEntityAPI)` | `addLayeredRenderingPlugin` 后由引擎在 `executeAdds` 阶段调用 | 与 `EveryFrameCombatPlugin.init()` 不同，**这个 init 保证在渲染与推进前执行**；参数是承载实体，不是引擎 |
| `advance(float)` | 每帧非暂停分支的实体推进循环 | 暂停时**不调用**(与 `EveryFrameCombatPlugin.advance` 相反，见 "推进与暂停")|
| `render(CombatEngineLayers, ViewportAPI)` | 每帧渲染阶段，**每个激活层各调用一次** | 只有 `isNearViewport(entity.getLocation(), getRenderRadius())` 通过才调用 |
| `isExpired()` | 每次推进后由引擎检查 | 返回 true → 实体进移除队列 → 移除时调用 `cleanup()` |
| `cleanup()` | 实体被引擎移除时 | 见 "清理" 节：战斗结束的 `destroyInstance` 不调用它 |
| `getActiveLayers()` | 注册时 (决定进哪些层) 与每次渲染 (分派)| 返回 `EnumSet<CombatEngineLayers>` |
| `getRenderRadius()` | 每次渲染前 (剔除判定)| 绘制范围必须被它覆盖 |

### 层级表 (CombatEngineLayers，23 个值)

`CombatEngineLayers` 枚举 (公开 API 可直接读取) 按声明顺序即层间顺序；`PHASED_SHIPS_LAYER` 的枚举注释自证层序敏感 ("need to be below fighters to avoid artifacts when phasing back in")：

```text
BELOW_PLANETS            （行星下方）
PLANET_LAYER
ABOVE_PLANETS
CLOUD_LAYER
BELOW_SHIPS_LAYER
UNDER_SHIPS_LAYER
ASTEROIDS_LAYER
CAPITAL_SHIPS_LAYER
CRUISERS_LAYER
DESTROYERS_LAYER
FRIGATES_LAYER
BELOW_PHASED_SHIPS_LAYER
PHASED_SHIPS_LAYER       （必须低于战机层：相位解除瞬间避免伪影）
STATION_WEAPONS_LAYER
CONTRAILS_LAYER
FIGHTERS_LAYER
BELOW_INDICATORS_LAYER
FF_INDICATORS_LAYER
ABOVE_SHIPS_LAYER
ABOVE_SHIPS_AND_MISSILES_LAYER
ABOVE_PARTICLES_LOWER    ─┐
ABOVE_PARTICLES           ├─ 顶层三图层：引擎在粒子系统之后单独渲染
JUST_BELOW_WIDGETS       ─┘
```

渲染管线事实：`renderer.renderExcluding(viewport, {ABOVE_PARTICLES, ABOVE_PARTICLES_LOWER, JUST_BELOW_WIDGETS})` 先按层渲染除顶层三图层外的全部层；

随后引擎渲染全部粒子系统 (爆炸、烟雾、星云等)；

最后 `renderOnly` 依次渲染 `ABOVE_PARTICLES_LOWER`、`ABOVE_PARTICLES`、`JUST_BELOW_WIDGETS`。

所以**顶层三图层永远在所有粒子之上**，其余层按枚举顺序被粒子系统穿插。

### 视口速查 (ViewportAPI)

| 方法 | 语义 |
|---|---|
| `getLLX()` / `getLLY()` / `getVisibleWidth()` / `getVisibleHeight()` | 可见区左下角与宽高，**世界单位**(`ViewportAPI` javadoc 原文)|
| `getViewMult()` | 当前层级缩放倍数 (`visibleWidth * worldXtoScreenX / screenWidth`)|
| `getAlphaMult()` | 全局透明度倍数 (战斗结束淡出等)|
| `convertWorldXtoScreenX / convertWorldYtoScreenY` | 世界坐标 → 屏幕坐标 (`(world - llx) / viewMult * worldXtoScreenX`)|
| `convertScreenXToWorldX / convertScreenYToWorldY` | 屏幕坐标 → 世界坐标 (反向)|
| `convertWorldWidthToScreenWidth / convertScreenWidthToWorldWidth` 等 | 长度双向换算 |
| `isNearViewport(loc, nearDistance)` | 位置是否在可见区外扩 `nearDistance` 的范围内 (剔除判定)|
| `setExternalControl(true)` | 告诉引擎每帧不要设置视口参数，允许 MOD 接管视口行为 (API javadoc 原文 "Tells the game not to set the viewport parameters every frame. Allows a mod to override viewport behavior.")|

`setExternalControl` 的引擎侧行为：`isExternalControl()` 为 true 时不再调用 `engine.setViewport(...)`、相机中心跟随 `viewport.getCenter()`、屏幕叠加范围取 `viewport` 自己的参数。

注意渲染回调里的顶点**直接以世界坐标提交**(视口变换已由渲染器应用)，屏幕转换方法只在需要屏幕空间时才用。

::: warning 视口转换方向搞反
在世界坐标绘制里拿 `convertWorldXtoScreenX` / `convertWorldYtoScreenY` 的结果当顶点坐标，或漏乘 / 多乘 `getViewMult()`，元素尺寸会随镜头缩放错误变化、位置偏移。记住：世界坐标绘制直接提交世界坐标，只有需要屏幕空间时才用转换方法，方向为 world→screen。
:::

## 渲染管线与生命周期

### 注册与承载

`engine.addLayeredRenderingPlugin(plugin)` 在内部把插件包装成战斗实体并作为战斗对象加入引擎。

`executeAdds` 时 `addObjectToEngine` 调用 `entity.init()`(→ `plugin.init(entity)`)，并按 `getActiveLayers()` 把实体注册进 `LayeredRenderer`。

此后插件就作为普通战斗实体参与推进与渲染。

::: info 为什么是独立的插件通道
分层渲染插件以战斗实体为承载 (引擎注册时把它包装成战斗实体)，天然获得战斗实体的位置、存活与剔除语义，还能按层参与渲染分派；`EveryFrameCombatPlugin` 只是每帧回调，没有这些实体语义 —— 这正是两条通道推进与清理时机不同的根源。
:::

### 推进与暂停 (与 EveryFrameCombatPlugin 的关键差异)

引擎 `advanceInner` 分两枝：**暂停分支**只推进地形 (含 `PluginAdapter` 承载的 `EveryFrameCombatPlugin`)、玩家舰船与结束判定。

**非暂停分支**才跑 "Advancing entities" 循环，逐个 `entity.advance(amount)` 并检查 `isExpired()`。

由此得到两条对照事实：

- `EveryFrameCombatPlugin.advance` 暂停时仍被调用 (前置单元必须遵守的规则：必须自行守卫 `engine.isPaused()`)；
- `CombatLayeredRenderingPlugin.advance` 暂停时**不被调用**(引擎暂停分支不推进实体)，但渲染照常执行 —— 暂停时画面上的自绘内容静止但可见。

成熟实例的 `advance` 仍保留 `engine.isPaused()` 守卫：在 RC8 下这是防御性写法，本教程不作必须遵守的规则，但建议保留以抵抗基线变动。`advance` 里也照例判 `engine == null`(`init` 虽然保证先执行，成熟实例一致做法是判空)。

### 渲染与剔除

每帧渲染阶段 `CombatEngineAPI.render` 让 `LayeredRenderer` 按层分派；承载实体把 `render` 转发给插件时带剔除：

```java
if (viewport.isNearViewport(this.getLocation(), this.plugin.getRenderRadius())) {
    this.plugin.render(var1, var2);
}
```

**剔除依据是承载实体的位置，不是插件自己记住的任何坐标。**

因此必须把实际绘制位置同步到 `entity.getLocation()`(成熟实例在 `advance` 里 `entity.getLocation().set(...)`)。

并且 `getRenderRadius()` 必须覆盖绘制范围 (按绘制范围取 `ship.getCollisionRadius()` 或 `bladeLength + 检测范围 + ship.getCollisionRadius()`)。

需要全局常驻的插件用 `Float.MAX_VALUE`。

`render` 对**每个激活层各调用一次**(成熟实例声明多个层时，回调内按 `layer` 参数分支绘制)。一个插件声明多层时，同一帧会收到多次 `render` 调用，必须在回调内按 `layer` 参数分支，而不是每次都画全套。

::: tip 图形 "凭空消失" 时查两处
图形在视口内 "凭空消失"(或被画到战场原点) 时，先查两处：承载实体位置是否每帧同步到 `entity.getLocation()`、`getRenderRadius()` 是否覆盖绘制范围 —— 这两处正是 `isNearViewport` 剔除判定的输入 (规则 4 的可观察面)。
:::

### 清理

两条实现事实构成清理纪律的骨架：

1. **实体移除时引擎调用 `cleanup()`**：`executeRemoves` → `removeObjectFromEngine` → `renderer.remove(...)` + `entity.cleanup()` 转发 `plugin.cleanup()`。触发实体移除的唯一主动途径是 `isExpired()` 返回 true 排队或 `engine.removeEntity(...)`。
2. **战斗结束 `destroyInstance` 不调用 `cleanup()`**：它只是 `objects.clear()` 并把 `renderer`、`combatMap` 等字段置 null，对任何实体都不调 `cleanup()`。

所以 "必须随战斗清理" 的正解是：**在 `advance()` 里主动失效 (`isExpired()` 转 true)，让引擎走正常移除路径调用 `cleanup()`**。

把 GL 资源 (VAO/VBO/着色器程序等) 的释放放进 `cleanup()`，并把句柄置回哨兵值防止重复删除。这与前置单元的结论同构：两条插件通道都没有 "战斗结束回调"，收尾必须由插件自己驱动。

::: caution 不要赌 "战斗结束时 cleanup() 会被调用"
战斗结束的 `destroyInstance` 只清空对象表、不调用 `cleanup()`—— 把 GL 资源释放寄托在 "战斗结束回调" 上，资源会静默丢失。正确做法：在 `advance()` 里主动失效 (`isExpired()` 转 true)，让引擎走正常移除路径调用 `cleanup()`。
:::

## 必须遵守的规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 绘制只用 GL 原语，且**只在 `render()` 回调内进行**；`advance()` 属于推进阶段，不绘制 | `advance()` 里发出的 GL 命令在渲染阶段之外执行：内容不出现、错帧或叠加在错误图层上 (随后被渲染阶段的状态切换与清屏覆盖)，且无法参与本层的层序 |
| 2 | **必须** | GL 资源 (VAO / VBO / 着色器 (shader) 程序等)**必须随战斗清理**：在 `cleanup()` 中释放，句柄复位防重复删除 | 句柄与显存随每场战斗累积泄漏；多场战斗后渲染异常 (驱动报错、画面损坏、卡顿)|
| 3 | **必须** | 不能在 `cleanup()` 上赌 "战斗结束时会被调用"：`destroyInstance` 只清空对象表不调 `cleanup()`；插件必须在 `advance()` 里主动失效 (`isExpired()` → true) 驱动引擎移除 | 舰船已死/已脱离的插件继续渲染到战斗结束；战斗结束时资源静默丢失、`cleanup()` 永不执行 |
| 4 | **必须** | 渲染位置同步到承载实体 `entity.getLocation()`，且 `getRenderRadius()` 覆盖绘制范围 | 位置不同步 → 图形画在战场原点或旧位置；半径过小 → 图形在视口内 "凭空消失"(`isNearViewport` 剔除)；半径过大 → 离屏仍绘制，性能损耗 |
| 5 | **必须** | `getActiveLayers()` 精确声明需要的层，`render` 回调内按 `layer` 参数分支 | 漏层 → 图形不出现或被下层遮挡；多激活层不分支 → 同一图形每帧重复绘制 (叠加变亮/闪烁)，同帧多图层内容互相污染 |
| 6 | **必须** | 插件、引擎与舰船引用禁止跨战斗缓存 (前置单元的跨战斗缓存纪律同规；每场战斗用 `init` 里 `Global.getCombatEngine()` 取引擎)| 第二场战斗起引用全部失效且无报错：旧引擎已被 `destroyInstance` 丢弃、旧舰 `isAlive()` 恒为 false，插件被自己的失效逻辑立刻清掉或绘制异常 |
| 7 | 建议 | `advance()` 保留 `engine == null` 与 `engine.isPaused()` 守卫 (成熟实例一致做法)| 无功能错误 (RC8 下 init 保证先执行、暂停分支不推进实体)；守卫是防基线变动的防御 |
| 8 | 建议 | `render()` 开头设置自己需要的 GL 状态 (如 `GL_BLEND` 与混合函数)；结束时解除 VAO / VBO 绑定、关闭自己激活的程序 | 不解除绑定会让后续图元沿用插件设置的状态；本教程不把 "引擎每层重置 GL 状态" 当作承诺，成熟实例全部自己收尾 |
| 9 | 建议 | `getRenderRadius()` 按实际覆盖范围取值；需要全局常驻时才用 `Float.MAX_VALUE`(调试用途)| 无功能错误，只有性能成本 |

## 完整最小例

在前置单元建立的 MOD 布局与战斗插件之上新增一个插件类文件 (不新增任何资源、不新增任何注册项)，包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearIn<Subject>`)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # C06-S02 所有；本节不改动
│       ├── GearInGlobalAccess         # C06-S03 所有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin      # C07-S02 所有；本节只消费
│           ├── GearInCombatLifecyclePlugin  # C07-S05 所有；本节只消费
│           └── GearInLayeredRenderer        # 本节新增
└── jars\
```

### GearInLayeredRenderer

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 完整最小例：分层渲染插件（CombatLayeredRenderingPlugin）的层级、视口与清理。
 *
 * 渲染管线事实（0.98a-RC8）：
 * - engine.addLayeredRenderingPlugin(plugin) 用自定义战斗实体包装插件并加入引擎；
 *   executeAdds 时调用 plugin.init(entity) 并按 getActiveLayers() 注册进 LayeredRenderer；
 * - 每帧（非暂停分支）推进 entity.advance(amount)，随后 isExpired() 为 true 的实体
 *   进移除队列；移除时引擎调用 entity.cleanup() -> plugin.cleanup()——
 *   cleanup() 是释放 GL 资源的正常时机；
 * - 战斗结束 destroyInstance 只清空对象表、不调用 cleanup()——
 *   插件必须在 advance() 里主动失效（isExpired() -> true），不能等"战斗结束回调"；
 * - 渲染阶段：自定义战斗实体的 render 先判 viewport.isNearViewport(entity.getLocation(),
 *   plugin.getRenderRadius())，通过才调 plugin.render(layer, viewport)——
 *   剔除依据是承载实体的位置与 getRenderRadius()，位置必须同步到 entity；
 * - 渲染回调在渲染阶段被调用，绘制必须在这里用 GL 原语完成；advance() 属于推进阶段，
 *   不做任何绘制；
 * - 暂停时引擎不推进实体（advanceInner 的暂停分支只 advance 地形与玩家舰船），
 *   但渲染循环照常执行（渲染不暂停）；isPaused() 守卫为防御性写法（成熟实例一致做法）；
 * - 插件、引擎与舰船引用禁止跨战斗缓存（C06-S03 / C07-S05 同规）。
 */
public class GearInLayeredRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInLayeredRenderer.class);

    /** 半透明方框的边长（世界单位）。 */
    private static final float MARKER_SIZE = 120f;

    /** 被追踪的舰船：每帧读取它的实时位置，不缓存坐标副本。 */
    private final ShipAPI trackedShip;

    private final Color color;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    public GearInLayeredRenderer(ShipAPI trackedShip, Color color) {
        this.trackedShip = trackedShip;
        this.color = color;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点
        LOG.info("GearIn layered renderer init: layers=" + getActiveLayers());
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return MARKER_SIZE;
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return; // 防御：init() 先于 advance() 被引擎调用，保留与成熟实例一致的判空
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（防御性；RC8 引擎的暂停分支本就不推进实体）
        }
        if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
            valid = false; // 失效：下一轮 executeRemoves 移除实体并调用 cleanup()
            return;
        }
        elapsed += amount;
        // 渲染剔除依据的是承载实体的位置：必须与被追踪位置保持同步
        entity.getLocation().set(trackedShip.getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (engine == null) {
            return;
        }
        float alpha = viewport.getAlphaMult() * color.getAlpha() / 255f;
        if (alpha <= 0f) {
            return;
        }
        // 渲染回调内用 GL 原语绘制：视口变换已由渲染器应用，直接提交世界坐标
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(),
                (byte) (alpha * 255f));

        Vector2f loc = entity.getLocation();
        float half = MARKER_SIZE * 0.5f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(loc.x - half, loc.y - half);
        GL11.glVertex2f(loc.x - half, loc.y + half);
        GL11.glVertex2f(loc.x + half, loc.y + half);
        GL11.glVertex2f(loc.x + half, loc.y - half);
        GL11.glEnd();
    }
}
```

对应关系：

- 构造器设 `layer = ABOVE_SHIPS_AND_MISSILES_LAYER`：`BaseCombatLayeredRenderingPlugin` 的受保护字段，`getActiveLayers()` 返回 `EnumSet.of(layer)`(规则 5 的单一图层版本)。
- `init` 三件事：`super.init(entity)` 存承载实体、`engine = Global.getCombatEngine()` 取引擎 (每场战斗新取，规则 6)、`advance(0f)` 立即同步位置 (成熟实例同款做法)。
- `advance` 的失效链：`!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()` → `valid = false` → `isExpired()` 转 true → 引擎下一轮移除实体并调 `cleanup()`(规则 3)。位置同步 `entity.getLocation().set(...)`(规则 4)。
- `getRenderRadius()` 返回 `MARKER_SIZE`：覆盖 120 世界单位的方框 (规则 4)。
- `render`：**只在这里**用 GL 原语绘制 (规则 1)；开头设置 `GL_BLEND` 与混合函数 (建议 8 的开启侧)；`viewport.getAlphaMult()` 参与透明度 (战斗结束淡出时方框跟着淡出)；顶点直接提交世界坐标。
- `cleanup()`：本最小例没有分配 GL 资源 (立即模式无句柄)，所以 cleanup 只留日志；有 VAO/VBO/程序资源的插件在这里释放并复位句柄 (规则 2)。

注册片段 (工程片段 —— 本节不改动战斗生命周期插件单元的任何文件，只演示使用方如何调用；把下面两行加进部署监听器 `GearInDeploymentListener.reportFleetMemberDeployed` 的末尾)：

```java
    // 工程片段：在部署监听器 GearInDeploymentListener.reportFleetMemberDeployed 内追加
    ShipAPI ship = member.getShip();
    if (ship != null) {
        engine.addLayeredRenderingPlugin(new GearInLayeredRenderer(ship, new Color(0, 255, 255, 80)));
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元建立的编译链路)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`(可用 `jar tf` 核对)。

本节源码与 fixture 逐字一致 (fixture 位于 `tutorial-fixtures/units/C07-S06` 的 `data/scripts/gearin/combat` 包内)，fixture 编译验收命令见 "预期结果"。

### 验证方案 (基于本例)

前置数据：C06-S01 的 MOD 布局 + C02-S01 战斗插件接入 + C07-S05 的战斗生命周期插件 (其部署监听器已含本节注册片段两行)+ 本节的 `GearInLayeredRenderer` + C03-S01 的 `GearIn_test` 测试战役。

操作步骤：

1. **fixture 编译**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC07s06Java --no-daemon`，确认以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。
3. **战役验证**：进入 `GearIn_test` 一场战斗，在 `Starsector/starsector-core/starsector.log` 核对 `GearIn layered renderer init: layers=[ABOVE_SHIPS_AND_MISSILES_LAYER]`；没有 `gear_in` 引起的 `ERROR`。
4. **画面**：观察每个已部署成员身上的半透明青色方框：跟随移动、位于舰船与导弹之上、粒子之下。
5. **暂停**：按空格暂停约 10 秒 —— 方框静止但可见；恢复后继续跟随。
6. **剔除**：镜头拉远使方框离开可见区外扩 120 单位范围 —— 方框消失；拉回出现。
7. **失效清理**：击毁一艘被追踪舰船 —— 方框消失且 `Starsector/starsector-core/starsector.log` 出现 `GearIn layered renderer cleanup: elapsed=<秒数>`；其余舰船方框正常。
8. **跨战斗**：退出战斗再进一场 ——`GearIn layered renderer init:` 重新出现，新方框从零开始跟随。
9. **故障注入**：把 `advance()` 里的 `entity.getLocation().set(trackedShip.getLocation());` 注释掉 —— 方框画在战场原点 (对应故障 2)；把 `valid = false;` 注释掉 —— 舰船被击毁后方框残留到战斗结束且无 `cleanup` 日志 (对应故障 3)；改回后确认恢复。

通过信号：操作 1–8 全部符合；操作 9 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件 (`tutorial-fixtures` 构建配置等) 做过修改。

::: tip 日志核对技巧
在 `Starsector/starsector-core/starsector.log` 中按 `GearIn layered renderer` 前缀检索即可定位本插件的全部输出行 (只有 init / cleanup 两处打日志)；出现 `ERROR` 时优先查看 `gear_in` 包相关的堆栈。
:::

## 预期结果

1. **fixture 编译 (验收 J)**：在 Git Bash 执行：

   ```text
   gradle -p tutorial-fixtures compileC07s06Java --no-daemon
   ```

   以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。
3. **战斗初始化**：进入 `GearIn_test` 测试战役一场战斗，`Starsector/starsector-core/starsector.log` 出现一行：

   ```text
   GearIn layered renderer init: layers=[ABOVE_SHIPS_AND_MISSILES_LAYER]
   ```

   没有 `ERROR`。
4. **画面结果**：每个被注册的已部署成员身上出现一个半透明青色方框 (120 世界单位边长)，跟随舰船移动；方框画在舰船与导弹之上、粒子之下 ——`ABOVE_SHIPS_AND_MISSILES_LAYER` 位于粒子系统之前 (层序表)。
5. **暂停行为**：按空格暂停约 10 秒再恢复 —— 暂停期间方框静止不动 (`advance` 不被调用，位置不更新) 但**仍然可见**(渲染不暂停)；恢复后继续跟随。
6. **视口剔除**：把镜头拉远到方框边缘超出可见区外扩 120 单位之外 —— 方框消失；拉回后重新出现 (`isNearViewport` 剔除，规则 4 的可观察面)。战斗中正常推进时不会产生 `GearIn layered renderer` 的推进日志 (render 与推进都不打日志，只有 init/cleanup 打)。
7. **失效与清理**：击毁被追踪的舰船 —— 方框立即消失 (下一帧起)，`Starsector/starsector-core/starsector.log` 出现一行：

   ```text
   GearIn layered renderer cleanup: elapsed=<秒数>
   ```

   此后该舰的方框不再出现；同一场战斗内其他舰船的方框不受影响。
8. **战斗结束**：胜负判定后淡出期间方框随 `viewport.getAlphaMult()` 淡出；退出战斗再进一场 ——`GearIn layered renderer init:` 重新出现，第二场战斗每艘新部署成员都有新方框 (每场战斗全新实例，规则 6)。

静态自查：`GearInLayeredRenderer` 与 fixture 逐字一致 (包括 import 顺序与注释)。

没有把工作单元编号写进任何包名、类名、资源名或字符串 key(包 `data.scripts.gearin.combat`、类 `GearInLayeredRenderer`)；除本节目录与 fixture 目录外没有改动任何共享文件。

## 实例驱动故障分析

1. **在 `advance()` 里绘制**。触发：把 `GL11.glBegin(...)` 等绘制调用放进 `advance()`。可观察：`advance()` 属于推进阶段，不在渲染阶段执行 —— 发出的 GL 命令随后被渲染阶段的状态切换与清屏覆盖，绘制内容不出现、错帧或出现在错误图层。修正：绘制只放 `render()` 回调 (规则 1)；`advance()` 只推进状态与同步位置。
2. **位置不同步到承载实体**。触发：`advance()` 里更新自己的坐标字段，但不 `entity.getLocation().set(...)`；或 `getRenderRadius()` 小于绘制范围。可观察：方框画在战场原点 (承载实体的默认位置) 而不是舰船位置；半径过小时方框在视口内 "凭空消失"—— `render` 转发前先判 `isNearViewport(entity.getLocation(), getRenderRadius())`。修正：每次 `advance` 同步实体位置，半径覆盖绘制范围 (规则 4)。
3. **`isExpired()` 恒为 false**。触发：失效逻辑缺失或 `valid` 从不置 false。可观察：舰船被击毁后方框继续渲染到战斗结束；战斗结束时 `cleanup()` **永不执行**——`destroyInstance` 只 `objects.clear()` 不调 `cleanup()`。修正：`advance()` 里判 `isEntityInPlay` / `isAlive`，失效即置 `valid = false`(规则 3)。
4. **`cleanup()` 漏释放 GL 资源**。触发：插件分配了 VAO / VBO / 着色器程序，`cleanup()` 不删。可观察：句柄与显存随每场战斗泄漏；多场战斗后渲染异常、卡顿或驱动报错。修正：`cleanup()` 里删除并把句柄置回哨兵值 (规则 2)。
5. **声明多个激活层但不按 `layer` 分支**。触发：`getActiveLayers()` 返回两层 (如 `UNDER_SHIPS_LAYER` + `ABOVE_SHIPS_LAYER`)，`render` 里无视 `layer` 参数每帧画全套。可观察：同一图形每帧绘制两次，画面叠加变亮或闪烁；层间关系被破坏 (该在舰船下的内容画到舰船上)。修正：`render` 内按 `layer` 分支 (规则 5)。
6. **跨战斗缓存插件实例**。触发：把 `GearInLayeredRenderer` 存进静态字段或生涯侧对象，第二场战斗继续引用。可观察：旧实例的 `trackedShip` 是上一场的舰船，`isAlive()` 恒为 false(跨战斗引用失效的表现)，插件被自己的失效逻辑立刻清掉，第二场战斗没有任何方框且无报错。修正：每场战斗从部署/推进处新建并 `addLayeredRenderingPlugin`(规则 6)。
7. **把 `EveryFrameCombatPlugin` 的暂停语义套到渲染插件上**。触发：认为 `advance` 暂停时也会被调用，不加守卫直接推进累计时间；或反过来认为暂停时渲染也停。可观察：前者在暂停后恢复时计时多出暂停时长 (实际 RC8 暂停分支不推进实体)；后者以为画面停了 —— 渲染循环照常执行，自绘内容静止但可见。修正：按本节 "推进与暂停" 的对照表理解两条通道 (战斗生命周期插件的 `advance` 暂停时调用、本节插件的 `advance` 暂停时不调用；两者渲染回调都不随暂停停摆)。
8. **视口转换方向搞反**。触发：想在屏幕上画固定大小的 UI 元素，却用 `convertWorldXtoScreenX` 拿到的值当顶点坐标，或在世界坐标绘制里漏乘/多乘 `getViewMult()`。可观察：元素尺寸随镜头缩放错误变化、位置偏移。修正：世界坐标绘制直接提交世界坐标 (渲染器已应用视口变换)；只有需要屏幕空间时才用转换方法，方向为 world→screen。

## 总结

- **独立插件通道**：`engine.addLayeredRenderingPlugin(plugin)` → 包装成战斗实体 → `executeAdds` 时 `init(entity)` + 按 `getActiveLayers()` 注册进 `LayeredRenderer`；与 `EveryFrameCombatPlugin` 相比：init 保证先执行、暂停时不推进、移除时由引擎调 `cleanup()`。
- **层序**：`CombatEngineLayers` 枚举声明顺序即层序；顶层三图层 (`ABOVE_PARTICLES_LOWER` / `ABOVE_PARTICLES` / `JUST_BELOW_WIDGETS`) 在粒子系统之后单独渲染 (`renderExcluding` / `renderOnly`)；`PHASED_SHIPS_LAYER` 必须低于战机层 (枚举注释)。
- **渲染与剔除**：`render` 每激活层调用一次、按 `layer` 分支；转发前先判 `isNearViewport(entity.getLocation(), getRenderRadius())`—— 位置同步与半径覆盖是 "图形在视口内消失" 的排查入口。
- **视口**：`ViewportAPI` 全是世界单位 (`getLLX/getLLY/getVisibleWidth/getVisibleHeight`)；`getViewMult()` 是当前缩放；转换方法 world↔screen 双向；`setExternalControl(true)` 让引擎每帧不再设置视口参数，是 MOD 接管相机的入口。
- **清理一条线**：`advance()` 主动失效 (`isExpired()` → true)→ 引擎移除 → `cleanup()` 释放 GL 资源；战斗结束 `destroyInstance` **不调** `cleanup()`，与前置单元 "战斗结束没有清理回调" 的结论同构；跨战斗缓存禁令 (前置纪律) 原样适用。

本节的分层渲染语义与视口坐标模型，是后续视觉类工作的地基：需要屏幕空间元素、按层自绘或相机接管 (`setExternalControl`) 的场合，都以这里的层序与坐标语义为起点；"没有战斗结束回调、收尾由插件自己驱动" 的清理纪律，与战斗插件通道一脉相承。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin`：分层渲染插件接口 (`init` / `cleanup` / `isExpired` / `advance` / `getActiveLayers` / `getRenderRadius` / `render`)。
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：七个回调的空实现基类 (默认层 `BELOW_INDICATORS_LAYER`、默认剔除半径 100)。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：层级枚举 (23 个值，声明顺序即层序)。
- `com.fs.starfarer.api.combat.ViewportAPI`：视口坐标 (世界单位)、`getViewMult()`、双向转换、`setExternalControl`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addLayeredRenderingPlugin` 注册入口、`getViewport`、`isEntityInPlay`。
- `com.fs.starfarer.api.combat.CombatEntityAPI`：承载实体 (`getLocation`、`isExpired`)。
- `com.fs.graphics.LayeredRenderer`：引擎内部按层渲染器 (`renderExcluding` / `renderOnly` 分派)。
- `tutorial-fixtures/units/C07-S06/src/main/java/data/scripts/gearin/combat`：`GearInLayeredRenderer` 源码目录 (页面代码块的磁盘对照)。
- `src/data/scripts/gearin/combat`：MOD 内本节类文件所在目录 (以 MOD 根目录为基准)。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。
