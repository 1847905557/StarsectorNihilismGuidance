# 制作分层渲染效果

本节制作第一个分层渲染效果，在指定战斗层级用世界坐标绘制，并在战斗结束时释放资源。分层渲染插件与每帧战斗插件的注册、暂停推进和收尾时机不同。

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

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C3-S1] 的模拟战入口与测试战役；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C6-S3] 的 `Global` 生命周期与跨战斗引用边界；
- [C7-S1] 的战斗插件注册与暂停判断；
- [C7-S2] 的实体存活性与 `isCombatOver()`；
- [C7-S5] 的战斗插件生命周期与清理边界；
- [C18-S1] 的 shader 与 GLSL 基础。

## 文件与 API 链路

本节涉及的接口与注册表面：

### 本节接口速查表

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `CombatLayeredRenderingPlugin` | 分层渲染插件接口 (`init` / `cleanup` / `isExpired` / `advance` / `getActiveLayers` / `getRenderRadius` / `render`) | `com.fs.starfarer.api.combat` |
| `BaseCombatLayeredRenderingPlugin` | 七个回调的空实现基类 (默认层 `BELOW_INDICATORS_LAYER`、默认剔除半径 `100`) | 建议继承 (成熟实例全部继承它) |
| `CombatEngineLayers` | 层级枚举 (23 个值，声明顺序即层序) | `getActiveLayers()` 的返回值与 `render` 的参数 |
| `CombatEngineAPI.addLayeredRenderingPlugin` | 战斗内注册入口 | 返回承载实体 `CombatEntityAPI`(引擎把插件包装成战斗实体) |
| `ViewportAPI` | 视口：可见区左下角、宽高 (世界单位)、缩放、双向转换 | `engine.getViewport()` |
| `CombatEntityAPI` | `init` 的参数与承载实体 (位置同步、存活/失效判定) | 引擎把插件包装成战斗实体 |
| `LayeredRenderer` | 引擎内部按层渲染器 (`com.fs.graphics`) | 引擎构造时创建，`renderer.add / remove / renderExcluding / renderOnly` |

### 注册与回调速查表

| 回调 | 时机 | 说明 |
|---|---|---|
| `init(CombatEntityAPI)` | `addLayeredRenderingPlugin` 后由引擎在 `executeAdds` 阶段调用 | 与 `EveryFrameCombatPlugin.init()` 不同，**这个 init 保证在渲染与推进前执行**；参数是承载实体，不是引擎 |
| `advance(float)` | 每帧非暂停分支的实体推进循环 | 暂停时**不调用** (与 `EveryFrameCombatPlugin.advance` 相反，见 "推进与暂停") |
| `render(CombatEngineLayers, ViewportAPI)` | 每帧渲染阶段，**每个激活层各调用一次** | 只有 `isNearViewport(entity.getLocation(), getRenderRadius())` 通过才调用 |
| `isExpired()` | 每次推进后由引擎检查 | 返回 true → 实体进移除队列 → 移除时调用 `cleanup()` |
| `cleanup()` | 实体被引擎移除时 | 见 "清理" 节：战斗结束的 `destroyInstance` 不调用它 |
| `getActiveLayers()` | 注册时 (决定进哪些层) 与每次渲染 (分派) | 返回 `EnumSet<CombatEngineLayers>` |
| `getRenderRadius()` | 每次渲染前 (剔除判定) | 绘制范围必须被它覆盖 |

### 层级表 (CombatEngineLayers，23 个值)

`CombatEngineLayers` 枚举 (公开 API 可直接读取) 按声明顺序即层间顺序；`PHASED_SHIPS_LAYER` 的枚举注释自证层序敏感 ("need to be below fighters to avoid artifacts when phasing back in")：

```text
BELOW_PLANETS            (行星下方)
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
PHASED_SHIPS_LAYER       (必须低于战机层：相位解除瞬间避免伪影)
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

渲染管线先绘制底部尾迹粒子，再由 `renderer.renderExcluding(viewport, {ABOVE_PARTICLES, ABOVE_PARTICLES_LOWER, JUST_BELOW_WIDGETS})` 按层绘制非顶层图层；随后依次绘制碎片和顶部粒子，最后由 `renderOnly` 绘制 `ABOVE_PARTICLES_LOWER`、`ABOVE_PARTICLES`、`JUST_BELOW_WIDGETS`。

所以**顶层三图层位于顶部粒子之后**；其余图层不能概括为统一位于全部粒子的同一侧。

### 用视口回答两个绘制问题

绘制前只需要回答目标是否在视口内，以及世界尺寸应换算成多少屏幕像素。前者用于剔除，后者用于缩放；相机中心、可见宽高和缩放倍率直接从 `ViewportAPI` 读取。具体 getter 在编写对应表达式时查接口即可。

## 渲染管线与生命周期

接口形状已经列全，接下来按注册、推进、渲染与清理四个环节，看引擎在每个环节如何使用它们。

### 注册与承载

`engine.addLayeredRenderingPlugin(plugin)` 在内部把插件包装成战斗实体并作为战斗对象加入引擎。

`executeAdds` 时 `addObjectToEngine` 调用 `entity.init()`(→ `plugin.init(entity)`)，并按 `getActiveLayers()` 把实体注册进 `LayeredRenderer`。

此后插件就作为普通战斗实体参与推进与渲染。

### 为什么是独立的插件通道

分层渲染插件以战斗实体为承载 (引擎注册时把它包装成战斗实体)，天然获得战斗实体的位置、存活与剔除语义，还能按层参与渲染分派；`EveryFrameCombatPlugin` 只是每帧回调，没有这些实体语义 —— 这正是两条通道推进与清理时机不同的根源。

### 推进与暂停 (与 EveryFrameCombatPlugin 的关键差异)

引擎 `advanceInner` 分两枝：**暂停分支**只推进地形 (含 `PluginAdapter` 承载的 `EveryFrameCombatPlugin`)、玩家舰船与结束判定。

**非暂停分支**才跑 "Advancing entities" 循环，逐个 `entity.advance(amount)` 并检查 `isExpired()`。

由此得到两条对照事实：

- `EveryFrameCombatPlugin.advance` 暂停时仍被调用 (前置单元必须遵守的规则：必须自行判断 `engine.isPaused()`)；
- `CombatLayeredRenderingPlugin.advance` 暂停时**不被调用** (引擎暂停分支不推进实体)，但渲染照常执行 —— 暂停时画面上的自绘内容静止但可见。

成熟实例的 `advance` 仍保留 `engine.isPaused()` 判断：这是防御性写法，本教程不作必须遵守的规则，但建议保留以抵抗基线变动。`advance` 里也照例判 `engine == null`(`init` 虽然保证先执行，成熟实例一致做法是判空)。

### 渲染与剔除

每帧渲染阶段 `CombatEngineAPI.render` 让 `LayeredRenderer` 按层分派；承载实体把 `render` 转发给插件时带剔除：

```java
if (viewport.isNearViewport(this.getLocation(), this.plugin.getRenderRadius())) {
    this.plugin.render(engine, viewport);
}
```

**剔除依据是承载实体的位置，不是插件自己记住的任何坐标。**

因此必须把实际绘制位置同步到 `entity.getLocation()`(成熟实例在 `advance` 里 `entity.getLocation().set(...)`)。

并且 `getRenderRadius()` 必须覆盖绘制范围 (按绘制范围取 `ship.getCollisionRadius()` 或 `bladeLength + 检测范围 + ship.getCollisionRadius()`)。

需要全局常驻的插件用 `Float.MAX_VALUE`。

`render` 对**每个激活层各调用一次** (成熟实例声明多个层时，回调内按 `layer` 参数分支绘制)。一个插件声明多层时，同一帧会收到多次 `render` 调用，必须在回调内按 `layer` 参数分支，而不是每次都画全套。

### 图形 "凭空消失" 时查两处

图形在视口内 "凭空消失"(或被画到战场原点) 时，先查两处：承载实体位置是否每帧同步到 `entity.getLocation()`、`getRenderRadius()` 是否覆盖绘制范围 —— 这两处正是 `isNearViewport` 剔除判定的输入 (规则 4 的可观察表现)。

### 清理

两条实现事实构成清理纪律的骨架：

1. **实体移除时引擎调用 `cleanup()`**：`executeRemoves` → `removeObjectFromEngine` → `renderer.remove(...)` + `entity.cleanup()` 转发 `plugin.cleanup()`。触发实体移除的唯一主动途径是 `isExpired()` 返回 true 排队或 `engine.removeEntity(...)`。
2. **战斗结束 `destroyInstance` 不调用 `cleanup()`**：它只是 `objects.clear()` 并把 `renderer`、`combatMap` 等字段置 null，对任何实体都不调 `cleanup()`。

所以 "必须随战斗清理" 的正解是：**在 `advance()` 里主动失效 (`isExpired()` 转 true)，让引擎走正常移除路径调用 `cleanup()`**。

把 GL 资源 (VAO/VBO/着色器程序等) 的释放放进 `cleanup()`，并把句柄置回哨兵值防止重复删除。这与前置单元的结论同构：两条插件通道都没有 "战斗结束回调"，收尾必须由插件自己驱动。

### 不要赌 "战斗结束时 cleanup() 会被调用"

战斗结束的 `destroyInstance` 只清空对象表、不调用 `cleanup()`—— 把 GL 资源释放寄托在 "战斗结束回调" 上，资源会静默丢失。正确做法：在 `advance()` 里主动失效 (`isExpired()` 转 true)，让引擎走正常移除路径调用 `cleanup()`。

## 必须遵守的规则速查表

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 绘制只用 GL 原语，且**只在 `render()` 回调内进行**；`advance()` 属于推进阶段，不绘制 | `advance()` 里发出的 GL 命令在渲染阶段之外执行：内容不出现、错帧或叠加在错误图层上 (随后被渲染阶段的状态切换与清屏覆盖)，且无法参与本层的层序 |
| 2 | **必须** | GL 资源 (VAO / VBO / 着色器 (shader) 程序等) **必须随战斗清理**：在 `cleanup()` 中释放，句柄复位防重复删除 | 句柄与显存随每场战斗累积泄漏；多场战斗后渲染异常 (驱动报错、画面损坏、卡顿) |
| 3 | **必须** | 不能在 `cleanup()` 上赌 "战斗结束时会被调用"：`destroyInstance` 只清空对象表不调 `cleanup()`；插件必须在 `advance()` 里主动失效 (`isExpired()` → true) 驱动引擎移除 | 舰船已死/已脱离的插件继续渲染到战斗结束；战斗结束时资源静默丢失、`cleanup()` 永不执行 |
| 4 | **必须** | 渲染位置同步到承载实体 `entity.getLocation()`，且 `getRenderRadius()` 覆盖绘制范围 | 位置不同步 → 图形画在战场原点或旧位置；半径过小 → 图形在视口内 "凭空消失"(`isNearViewport` 剔除)；半径过大 → 离屏仍绘制，性能损耗 |
| 5 | **必须** | `getActiveLayers()` 精确声明需要的层，`render` 回调内按 `layer` 参数分支 | 漏层 → 图形不出现或被下层遮挡；多激活层不分支 → 同一图形每帧重复绘制 (叠加变亮/闪烁)，同帧多图层内容互相污染 |
| 6 | **必须** | 插件、引擎与舰船引用禁止跨战斗缓存 (前置单元的跨战斗缓存纪律同规；每场战斗用 `init` 里 `Global.getCombatEngine()` 取引擎) | 第二场战斗起引用全部失效且无报错：旧引擎已被 `destroyInstance` 丢弃、旧舰 `isAlive()` 恒为 false，插件被自己的失效逻辑立刻清掉或绘制异常 |
| 7 | 建议 | `advance()` 保留 `engine == null` 与 `engine.isPaused()` 判断 (成熟实例一致做法) | 无功能错误 (init 保证先执行、暂停分支不推进实体)；判断是防基线变动的防御 |
| 8 | 建议 | `render()` 开头设置自己需要的 GL 状态 (如 `GL_BLEND` 与混合函数)；结束时解除 VAO / VBO 绑定、关闭自己激活的程序 | 不解除绑定会让后续图元沿用插件设置的状态；本教程不把 "引擎每层重置 GL 状态" 当作承诺，成熟实例全部自己收尾 |
| 9 | 建议 | `getRenderRadius()` 按实际覆盖范围取值；需要全局常驻时才用 `Float.MAX_VALUE`(调试用途) | 无功能错误，只有性能成本 |

## 实现分层渲染效果

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 选择渲染层与追踪对象

```java
// 插入位置：GearInLayeredRenderer 的字段区与构造器
private static final float MARKER_SIZE = 120f;
private final ShipAPI trackedShip;
private final Color color;

public GearInLayeredRenderer(ShipAPI trackedShip, Color color) {
    this.trackedShip = trackedShip;
    this.color = color;
    this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
}
```

新增行为：每个渲染器实例绑定一艘舰船、一种颜色和一个明确图层。完成信号：`getActiveLayers()` 只返回 `ABOVE_SHIPS_AND_MISSILES_LAYER`，不同舰船拥有独立实例。

### 接收承载实体

```java
// 插入位置：GearInLayeredRenderer.init()
super.init(entity);
engine = Global.getCombatEngine();
advance(0f);
LOG.info("GearIn layered renderer init: layers=" + getActiveLayers());
```

新增行为：插件取得引擎和承载实体，并在首次渲染前同步位置。完成信号：初始化日志列出唯一图层，首帧方框出现在目标舰船处而不是战场原点。

### 同步位置并触发失效

```java
// 插入位置：GearInLayeredRenderer.advance()
if (engine == null || engine.isPaused()) {
    return;
}
if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
    valid = false;
    return;
}
elapsed += amount;
entity.getLocation().set(trackedShip.getLocation());
```

新增行为：承载实体每帧跟随舰船，舰船离场或被击毁时插件转为过期。完成信号：移动时方框持续跟随；目标死亡后 `isExpired()` 返回 `true`，方框在下一轮移除。

### 声明剔除范围

```java
// 插入位置：GearInLayeredRenderer 的查询方法区
@Override
public EnumSet<CombatEngineLayers> getActiveLayers() {
    return EnumSet.of(layer);
}

@Override
public float getRenderRadius() {
    return MARKER_SIZE;
}
```

新增行为：引擎只在目标层派发渲染，并以 120 世界单位作为视口外扩范围。完成信号：图形离开视口外扩范围后停止绘制，回到视口时重新出现。

### 在渲染阶段提交方框

```java
// 插入位置：GearInLayeredRenderer.render() 的透明度检查之后
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
```

新增行为：渲染回调按世界坐标提交半透明方框，并服从视口整体透明度。完成信号：方框位于舰船与导弹之上、粒子之下，战斗退出过渡时随画面一起淡出。

### 确认正常清理路径

```java
// 插入位置：GearInLayeredRenderer.isExpired() 与 cleanup()
@Override
public boolean isExpired() {
    return !valid;
}

@Override
public void cleanup() {
    LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
}
```

新增行为：主动失效驱动引擎移除承载实体并调用 `cleanup()`。完成信号：目标舰船死亡后只出现一次清理日志；将来增加 VAO、VBO 或程序句柄时，在同一方法释放并复位。

## 完整最小例

规则只说明约束，下面用一个完整最小例把注册、推进、渲染与清理全部落进代码。在前置单元建立的 MOD 布局与战斗插件之上新增一个插件类文件 (不新增任何资源、不新增任何注册项)，包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearIn<Subject>`)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # C6-S2 所有；本节不改动
│       ├── GearInGlobalAccess         # C6-S3 所有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin      # C7-S2 所有；本节只消费
│           ├── GearInCombatLifecyclePlugin  # C7-S5 所有；本节只消费
│           └── GearInLayeredRenderer        # 本节新增
└── jars\
```

### GearInLayeredRenderer

对应关系：

- 构造器设 `layer = ABOVE_SHIPS_AND_MISSILES_LAYER`：`BaseCombatLayeredRenderingPlugin` 的受保护字段，`getActiveLayers()` 返回 `EnumSet.of(layer)`(规则 5 的单一图层版本)。
- `init` 三件事：`super.init(entity)` 存承载实体、`engine = Global.getCombatEngine()` 取引擎 (每场战斗新取，规则 6)、`advance(0f)` 立即同步位置 (成熟实例同款做法)。
- `advance` 的失效链：`!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()` → `valid = false` → `isExpired()` 转 true → 引擎下一轮移除实体并调 `cleanup()`(规则 3)。位置同步 `entity.getLocation().set(...)`(规则 4)。
- `getRenderRadius()` 返回 `MARKER_SIZE`：覆盖 120 世界单位的方框 (规则 4)。
- `render`：**只在这里**用 GL 原语绘制 (规则 1)；开头设置 `GL_BLEND` 与混合函数 (建议 8 的开启侧)；战斗 viewport 的 `getAlphaMult()` 保持 1.0，战斗结束淡出由引擎全屏变暗处理；顶点直接提交世界坐标。
- `cleanup()`：本最小例没有分配 GL 资源 (立即模式无句柄)，所以 cleanup 只留日志；有 VAO/VBO/程序资源的插件在这里释放并复位句柄 (规则 2)。

注册片段 (工程片段 —— 本节不改动战斗生命周期插件单元的任何文件，只演示使用方如何调用；在部署监听器 `GearInDeploymentListener.reportFleetMemberDeployed` 内追加。追加时注意：示例监听器的方法体里已经声明过 `ShipAPI ship = member.getShip()`，不要重复声明；片段里的 `engine` 与 `Color` 也需按您的监听器上下文自行提供和导入)：

```java
    // 工程片段：在部署监听器 GearInDeploymentListener.reportFleetMemberDeployed 内追加
    if (ship != null) {
        engine.addLayeredRenderingPlugin(new GearInLayeredRenderer(ship, new Color(0, 255, 255, 80)));
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用前置单元建立的编译链路)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`(可用 `jar tf` 核对)。

## 关键故障实验：渲染效果在错误层级或暂停状态消失

把推进逻辑写入只在渲染阶段调用的方法。

**局部故障片段：**

```java
// 故障片段
public void render(CombatEngineLayers layer, ViewportAPI viewport) { advanceState(); draw(); }
```

**局部修正片段：**

```java
// 修正片段
public void advance(float amount) { advanceState(amount); } public void render(CombatEngineLayers layer, ViewportAPI viewport) { draw(); }
```

复验：暂停、缩放和退出战斗各观察一次状态与绘制。

## 验证方案

前置数据：[C6-S1] 的 MOD 布局 + [C7-S1] 的战斗插件接入 + [C7-S5] 的战斗生命周期插件 (其部署监听器已加入本节注册片段) + 本节的 `GearInLayeredRenderer` + [C3-S1] 的 `GearIn_test` 测试战役。

1. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。
2. **战役验证**：进入 `GearIn_test` 一场战斗，在 `Starsector/starsector-core/starsector.log` 中观察分层渲染初始化记录。
3. **画面**：观察每个已部署成员身上的半透明青色方框：跟随移动、位于舰船与导弹之上、粒子之下。
4. **暂停**：按空格暂停约 10 秒 —— 方框静止但可见；恢复后继续跟随。
5. **剔除**：镜头拉远使方框离开可见区外扩 120 单位范围 —— 方框消失；拉回出现。
6. **失效清理**：击毁一艘被追踪舰船 —— 方框消失且 `Starsector/starsector-core/starsector.log` 出现 `GearIn layered renderer cleanup: elapsed=<秒数>`；其余舰船方框正常。
7. **跨战斗**：退出战斗再进一场 ——`GearIn layered renderer init:` 重新出现，新方框从零开始跟随。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。

- **战斗初始化**：进入 `GearIn_test` 测试战役一场战斗，`Starsector/starsector-core/starsector.log` 出现一行：

    ```text
       GearIn layered renderer init: layers=[ABOVE_SHIPS_AND_MISSILES_LAYER]
    ```

    没有 `ERROR`。

- **画面结果**：每个被注册的已部署成员身上出现一个半透明青色方框 (120 世界单位边长)，跟随舰船移动；方框画在舰船与导弹之上、粒子之下 ——`ABOVE_SHIPS_AND_MISSILES_LAYER` 位于粒子系统之前 (层序表)。

- **暂停行为**：按空格暂停约 10 秒再恢复 —— 暂停期间方框静止不动 (`advance` 不被调用，位置不更新) 但**仍然可见** (渲染不暂停)；恢复后继续跟随。

- **视口剔除**：把镜头拉远到方框边缘超出可见区外扩 120 单位之外 —— 方框消失；拉回后重新出现 (`isNearViewport` 剔除，规则 4 的可观察表现)。战斗中正常推进时不会产生 `GearIn layered renderer` 的推进日志 (render 与推进都不打日志，只有 init/cleanup 打)。

- **失效与清理**：击毁被追踪的舰船 —— 方框立即消失 (下一帧起)，`Starsector/starsector-core/starsector.log` 出现一行：

    ```text
       GearIn layered renderer cleanup: elapsed=<秒数>
    ```

    此后该舰的方框不再出现；同一场战斗内其他舰船的方框不受影响。

- **战斗结束**：胜负判定后引擎以全屏变暗完成淡出；退出战斗再进一场 ——`GearIn layered renderer init:` 重新出现，第二场战斗每艘新部署成员都有新方框 (每场战斗全新实例，规则 6)。

## 故障分析

- **绘制内容不出现或落在错误图层**：GL 调用写在 `advance()`，随后被渲染阶段覆盖。绘制只放入 `render()`，推进回调只更新状态。
- **图形出现在原点**：自定义坐标没有同步到承载实体。每次 `advance()` 更新 `entity.getLocation()`，再次移动目标后观察图形位置。
- **图形在视口内突然消失**：`getRenderRadius()` 小于实际绘制范围，剔除判断过早生效。扩大半径，使其覆盖完整图形。
- **舰船被击毁后图形仍然存在**：`isExpired()` 始终返回 false。检测 `isEntityInPlay` 与 `isAlive`，失效时将 `valid` 置为 false。
- **多场战斗后渲染异常或卡顿**：`cleanup()` 没有释放 VAO、VBO 或着色器程序。删除所有 GL 句柄并恢复哨兵值，再连续进入多场战斗复验。
- **同一图形叠加变亮或闪烁**：插件声明多个激活层，却在每次 `render` 中绘制全部内容。按 `layer` 参数分支，每层只提交对应图形。
- **第二场战斗不再显示图形**：分层渲染插件跨战斗缓存了上一场的舰船。每场战斗重新创建并注册插件。
- **暂停恢复后计时不符合预期**：混用了每帧插件与分层渲染插件的暂停语义。分层插件暂停时不推进，但仍会渲染；按这一边界重新安排计时。
- **元素缩放或位置偏移**：世界坐标和屏幕坐标转换方向用反。世界绘制直接提交世界坐标，只有屏幕空间元素才使用 world→screen 转换。

## 总结

- **独立插件通道**：`engine.addLayeredRenderingPlugin(plugin)` → 包装成战斗实体 → `executeAdds` 时 `init(entity)` + 按 `getActiveLayers()` 注册进 `LayeredRenderer`；与 `EveryFrameCombatPlugin` 相比：init 保证先执行、暂停时不推进、移除时由引擎调 `cleanup()`。
- **层序**：`CombatEngineLayers` 枚举声明顺序即层序；管线依次处理底部尾迹粒子、非顶层图层、碎片、顶部粒子和顶层三图层 (`ABOVE_PARTICLES_LOWER` / `ABOVE_PARTICLES` / `JUST_BELOW_WIDGETS`)；`PHASED_SHIPS_LAYER` 必须低于战机层 (枚举注释)。
- **渲染与剔除**：`render` 每激活层调用一次、按 `layer` 分支；转发前先判 `isNearViewport(entity.getLocation(), getRenderRadius())`—— 位置同步与半径覆盖是 "图形在视口内消失" 的排查入口。
- **视口**：`ViewportAPI` 全是世界单位 (`getLLX/getLLY/getVisibleWidth/getVisibleHeight`)；`getViewMult()` 是当前缩放；转换方法 world↔screen 双向；`setExternalControl(true)` 让引擎每帧不再设置视口参数，是 MOD 接管相机的入口。
- **清理一条线**：`advance()` 主动失效 (`isExpired()` → true)→ 引擎移除 → `cleanup()` 释放 GL 资源；战斗结束 `destroyInstance` **不调** `cleanup()`，与前置单元 "战斗结束没有清理回调" 的结论同构；跨战斗缓存禁令 (前置纪律) 原样适用。

本节的分层渲染语义与视口坐标模型，是视觉类工作的地基：需要屏幕空间元素、按层自绘或相机接管 (`setExternalControl`) 的场合，都以这里的层序与坐标语义为起点；"没有战斗结束回调、收尾由插件自己驱动" 的清理纪律，与战斗插件通道一脉相承。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin`：分层渲染插件接口 (`init` / `cleanup` / `isExpired` / `advance` / `getActiveLayers` / `getRenderRadius` / `render`)。
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：七个回调的空实现基类 (默认层 `BELOW_INDICATORS_LAYER`、默认剔除半径 100)。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：层级枚举 (23 个值，声明顺序即层序)。
- `com.fs.starfarer.api.combat.ViewportAPI`：视口坐标 (世界单位)、`getViewMult()`、双向转换、`setExternalControl`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addLayeredRenderingPlugin` 注册入口、`getViewport`、`isEntityInPlay`。
- `com.fs.starfarer.api.combat.CombatEntityAPI`：承载实体 (`getLocation`、`isExpired`)。
- `com.fs.graphics.LayeredRenderer`：引擎内部按层渲染器 (`renderExcluding` / `renderOnly` 分派)。
- `src/data/scripts/gearin/combat`：MOD 内本节类文件所在目录 (以 MOD 根目录为基准)。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。
::: details 完整成品：GearInLayeredRenderer

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
 * 完整最小例：分层渲染插件 (CombatLayeredRenderingPlugin) 的层级、视口与清理。
 *
 * 渲染管线事实：
 * - engine.addLayeredRenderingPlugin(plugin) 用自定义战斗实体包装插件并加入引擎；
 *   executeAdds 时调用 plugin.init(entity) 并按 getActiveLayers() 注册进 LayeredRenderer；
 * - 每帧 (非暂停分支) 推进 entity.advance(amount)，随后 isExpired() 为 true 的实体
 *   进移除队列；移除时引擎调用 entity.cleanup() -> plugin.cleanup()——
 *   cleanup() 是释放 GL 资源的正常时机；
 * - 战斗结束 destroyInstance 只清空对象表、不调用 cleanup()——
 *   插件必须在 advance() 里主动失效 (isExpired() -> true)，不能等 "战斗结束回调"；
 * - 渲染阶段：自定义战斗实体的 render 先判 viewport.isNearViewport(entity.getLocation(),
 *   plugin.getRenderRadius())，通过才调 plugin.render(layer, viewport)——
 *   剔除依据是承载实体的位置与 getRenderRadius()，位置必须同步到 entity；
 * - 渲染回调在渲染阶段被调用，绘制必须在这里用 GL 原语完成；advance() 属于推进阶段，
 *   不做任何绘制；
 * - 暂停时引擎不推进实体 (advanceInner 的暂停分支只 advance 地形与玩家舰船)，
 *   但渲染循环照常执行 (渲染不暂停)；isPaused() 守卫为防御性写法 (成熟实例一致做法)；
 * - 插件、引擎与舰船引用禁止跨战斗缓存 (C6-S3 / C7-S4 同规)。
 */
public class GearInLayeredRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInLayeredRenderer.class);

    /** 半透明方框的边长 (世界单位)。 */
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
            return; // 暂停守卫 (防御性；引擎的暂停分支本就不推进实体)
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

:::
