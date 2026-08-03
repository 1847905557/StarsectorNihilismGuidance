# 渲染生命周期、性能与故障实例

本节把渲染接入的**架构面**讲透：渲染层接入的两种入口与常驻/一次性选择、GL 资源的释放纪律与战斗结束收尾、开关的三层语义与读取时机、性能开销来源与度量做法，以及一组可注入的故障案例。示例所有权为常驻渲染插件 `GearInShaderPlugin`，配套两个 shader 资源 (`data/shaders/GearIn_field_shader.vert/.frag`)。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 说出渲染层接入的两种入口与取舍：分层渲染插件自绘 (`engine.addLayeredRenderingPlugin`，前几节搭建的完整链路) 与 BoxUtil 渲染实体队列 (`CombatRenderingManager.addEntity`)，并说出 "常驻插件 vs 一次性视觉" 的注册入口、生命周期与层选择差异；
- 说出资源释放的完整纪律：只释放自己创建的 GL 资源 (实例规范 "资源清理归创建者")、`cleanup()` 释放并复位句柄 (幂等)、共享贴图不卸载 (`unloadTexture` 按文件名从全局纹理管理器移除)、常驻插件用 `engine.isCombatOver()` 主动失效驱动 `cleanup()`(战斗结束 `destroyInstance` 不调 `cleanup()`)；
- 说出开关的三层语义：能力门禁 (`BoxConfigs.isShaderEnable() && ShaderCore.isValid()`) 在 GL 上下文就绪后重新判定、运行时开关在每次使用点读取、开关只改变渲染路径与显示成本不改变玩法状态，以及 "关闭后仍走昂贵 shader 路径" 的失败模式；
- 说出绘制开销的五个来源与对应优化：draw call 数与合并 (`glMultiDrawArrays` / BoxUtil instance 批量)、剔除半径、每帧查询/分配、纹理状态切换、失效对象回收，并会用本节示例的 `drawCalls` 计数做前后对比；
- 从症状定位典型故障：常驻插件不判战斗结束导致泄漏、门禁缓存进 `static final`、运行时开关只读 init 快照、跨战斗缓存、render 内做重活、draw call 爆炸、共享纹理被卸载。

## 前置知识

本节收束渲染接入的架构面，建议先熟悉以下主题：

- GLSL 110/430 方言、attribute/uniform/varying 与编译/链接检查链、能力门禁 (C14-S01)—— 本节的 430 方言与 `#version 430` 文件延续。
- `.vert/.frag` 路径约定、加载与编译日志、资源生命周期四环节 (C14-S02)—— 本节的资源释放是它 "清理与重载" 环节的架构化。
- BoxUtil `ShaderProgram` 创建与 location 保存、`active()/close()/delete()`、UBO `BUtilGlobalData`、uniform 三形态、纹理与矩阵提交 (C14-S03)—— 本节示例直接复用它的提交段；`GearInShaderPlugin` 是 `GearInShaderVisual` 的常驻形态。
- 分层渲染插件通道、层序、剔除与 `cleanup()` 时机 (C07-S06)—— 本节的层接入与清理纪律直接承接，并补常驻插件的战斗结束收尾。
- 战斗插件生命周期 (C07-S05)—— 本节常驻插件的注册片段所在。
- BoxUtil 初始化两段式、可用性判据、渲染实体入队与 `delete()`(C12-S06)—— 本节 "开关" 与 "对照入口" 直接复用。
- `Global` 生命周期，禁止跨战斗缓存引擎引用与渲染对象 (C06-S03)—— 本节的 program/engine 同规。
- 战役概念与 `GearIn_test` 测试战役 (C03-S01)—— 本节验证方案用它承载战斗观察。
- MOD 目录结构与 `starsector.log`(C01-S01)—— 本节所有日志可观察结果都落在它里面。

## 文件与 API 链路

| 环节 | 文件 / API | 说明 |
|---|---|---|
| shader 源文件 | `data/shaders/GearIn_field_shader.vert` / `.frag`(本节所有权)| MOD 根目录相对路径，`/` 分隔，UTF-8 无 BOM(C14-S02 规则 1/2 延续)|
| 常驻注册 | C07-S05 的 `GearInCombatLifecyclePlugin.init(CombatEngineAPI)` 内 `engine.addLayeredRenderingPlugin(new GearInShaderPlugin())` | 每场战斗注册一次；成熟实例同款做法：在生命周期插件的 `init` 里 `engine.addLayeredRenderingPlugin(new 常驻渲染插件())` |
| 渲染插件 | `BaseCombatLayeredRenderingPlugin`(`CombatLayeredRenderingPlugin` 接口，C07-S06 通道)| `init` / `advance` / `render` / `isExpired` / `cleanup` / `getActiveLayers` / `getRenderRadius` 七个回调 |
| 层声明 | `CombatEngineLayers.ABOVE_PARTICLES_LOWER`(顶层三图层，粒子系统之后渲染)| 本节示例层；成熟实例同款 |
| 战斗结束判定 | `CombatEngineAPI.isCombatOver()` | 结束标志成立且淡出完成后才转 true—— 结束标志与淡出完成共同成立 |
| 淡出强度 | `ViewportAPI.getAlphaMult()` | 战斗结束淡出期间下降；本节示例把它送进 `intensity` uniform 实现视觉淡出 (成熟实例同款做法)|
| 程序创建 | `org.boxutil.util.ShaderUtil.createShaderVFFormPath` → `org.boxutil.units.standard.ShaderProgram` | C14-S03 全部延续 (location 保存、`isValid()`、`active()/close()`、`delete()`)|
| 门禁/开关 | `org.boxutil.config.BoxConfigs.isShaderEnable()`、`org.boxutil.manager.ShaderCore.isValid()` | 可用性判据 (C12-S06)；本节规定读取时机与缓存禁令 |
| 对照入口 | `org.boxutil.manager.CombatRenderingManager.addEntity(...)` | BoxUtil 渲染实体队列 (C12-S06)：批量 instance 渲染由后端处理，入队返回 `BoxEnum.STATE_*`，失败必须显式 `delete()` |
| 纹理 | `SettingsAPI.getSprite(String)`(单参形态要求文件已加载)/ `loadTexture` / `unloadTexture` | 本节示例用引擎启动期必载的 `graphics/fx/particlealpha32sq.png`(引擎启动期加载队列)；`unloadTexture` 按文件名从全局纹理管理器移除，只用于自己加载的纹理 |
| 可观察结果 | `starsector.log` + 战斗画面 | init/cleanup/开关切换日志行 (cleanup 含 `frames`/`drawCalls` 计数)；玩家舰船周围的能量场画面 |

### 四位置完整流程

| 位置 | 本节取值 |
|---|---|
| 资源文件 | `tutorial-fixtures\units\C14-S04\src\main\resources\data\shaders\GearIn_field_shader.vert` / `.frag`(fixture)；MOD 内 `data\shaders\GearIn_field_shader.vert` / `.frag` |
| 加载入口 | `ShaderUtil.createShaderVFFormPath(名称, "data/shaders/GearIn_field_shader.vert", "data/shaders/GearIn_field_shader.frag")`(Java 常量 `VERT_PATH`/`FRAG_PATH` 与资源文件路径逐字一致)|
| 运行使用方 | `GearInShaderPlugin`(C07-S06 通道注册，`render` 内 `program.active()` + 提交 + `close()`)|
| 可观察结果 | 日志行含 `vert=data/shaders/GearIn_field_shader.vert`、`frag=...`；画面出现跟随玩家舰船的能量场 |

另有 "名称一致性完整核对"：UBO 块名 `BUtilGlobalData` 在 GLSL 声明与 Java 常量 `UBO_BLOCK_NAME` 逐字一致；

七个 uniform 名 (`modelMatrix`/`size`/`time`/`tex`/`intensity`/`coreColor`/`fringeColor`) 在 GLSL 声明与 Java `getUniformIndex` 参数逐字一致。

## 渲染层接入

### 两条渲染入口

战斗域的渲染入口有两条，取舍决定了资源的生命周期归属：

| 入口 | 形态 | 适用与代价 |
|---|---|---|
| 分层渲染插件自绘 (本节示例)| `engine.addLayeredRenderingPlugin(...)` 注册，`render(layer, viewport)` 回调内自己 `program.active()` → uniform/纹理提交 → `glDrawArrays` → `close()` | 完全控制绘制内容、顺序与 GL 状态；GL 资源 (program/VBO) 由插件自己创建、自己释放 (前几节搭建的完整链路)|
| BoxUtil 渲染实体队列 | 创建实现 `RenderDataAPI` 的实体 → 设置 layer/timer/实例数据 → `CombatRenderingManager.addEntity` → 后端按层绘制 | 大量同构对象 (粒子/贴片) 的批量 instance 渲染由后端处理；必须遵守提交链 (`setInstanceData` → refresh → `submitInstanceData` → `setRenderingCount`) 与入队返回码 (`STATE_FAILED`/`STATE_FAILED_OTHER` 必须显式 `delete()`)|

两条入口共用同一套 "渲染回调内提交、推进阶段不绘制" 的纪律。本节示例走第一条 (自绘)，因为它的主题是资源生命周期与开关 —— 自绘把所有 GL 资源的所有权与释放时机暴露给读者。

### 常驻插件 vs 一次性视觉

`addLayeredRenderingPlugin` 的调用点决定插件生命周期：

- **一次性视觉**：由效果、武器、战术系统等代码在需要时创建 (按需 `engine.addLayeredRenderingPlugin(visual)`；用 `if (simulator == null)` 守卫防重复生成，注册调用在守卫内)。失效靠寿命或承载实体死亡 (`advance()` 里 `isExpired()` 转 true)。前几节的示例都是这个形态。
- **常驻插件**：每场战斗注册一次，不按寿命/单个实体失效，跟随玩家舰船或全局常驻 (常驻模板：`init` 取引擎 + `advance(0f)`、`getActiveLayers` 返回全层 `EnumSet.allOf(...)`、`getRenderRadius` 返回 `Float.MAX_VALUE`)。成熟实例的常驻模板不持有 GL 资源 (`render` 体为空)，而**持有 GL 资源的常驻插件必须自己处理战斗结束收尾**—— 这正是本节示例要演示的 (见 "资源释放")。

两个注册做法都指向同一个事实：`addLayeredRenderingPlugin` 返回承载实体、`init` 保证在渲染与推进前执行，插件随后作为普通战斗实体参与推进与渲染。

### 层选择

层序与三个特例 (`CombatEngineLayers` 声明顺序即层序；顶层三图层在粒子系统之后单独渲染；`PHASED_SHIPS_LAYER` 必须低于战机层) 已在分层渲染通道的讲解中确立。本节示例的层选择依据：

- 视觉要盖在舰船/导弹与粒子之上 (能量场/光晕)→ 顶层三图层：`ABOVE_PARTICLES_LOWER` / `ABOVE_PARTICLES` / `JUST_BELOW_WIDGETS`(成熟实例同款)；
- 视觉在舰船与导弹之上、粒子之下 → `ABOVE_SHIPS_AND_MISSILES_LAYER`(前几节示例)；
- 常驻承载模板 → `EnumSet.allOf(CombatEngineLayers.class)`：所有层都收到 `render` 调用，只在需要预留层位或接管全层时才用；
- 声明多个激活层时，`render` 回调内必须按 `layer` 参数分支。

### 分层渲染与 shader 插件的组合形态

前几节逐层搭建的 "分层渲染插件 + BoxUtil `ShaderProgram`" 组合，在本节收敛为常驻形态的五环节完整流程：

```text
注册（生命周期插件 init，每场战斗一次）
→ 层声明（getActiveLayers，本节 ABOVE_PARTICLES_LOWER）
→ 提交（render 回调内：active → uniform/纹理/矩阵 → glDrawArrays → close）
→ 推进/失效（advance：位置同步 + 战斗结束主动失效）
→ 清理（cleanup：program.delete + glDeleteBuffers，句柄复位）
```

## 资源释放

### 所有权

实例规范：**"资源清理归持有 shader program、四边形网格对象、VAO、SDF、FBO 或渲染实体的创建者"**。推论：谁创建谁释放，共享资源不释放。本节示例的资源清单：

| 资源 | 创建 | 释放 |
|---|---|---|
| `ShaderProgram`(含 GL program)| `init` 内 `createShaderVFFormPath` + `new ShaderProgram` | `cleanup()` 内 `program.delete()`，句柄置 null |
| VBO(四边形顶点)| `init` 内 `glGenBuffers` + `glBufferData` | `cleanup()` 内 `glDeleteBuffers`，句柄置 0 |
| 采样贴图 (共享)| 引擎启动期 (启动期资源加载队列)| **不释放**——`getSprite` 只是取引用 |

### 释放时机：cleanup() 与战斗结束

此前确立的两个事实：实体被引擎移除时调用 `cleanup()`；战斗结束 `destroyInstance` 只清空对象表、**不调用** `cleanup()`。

对一次性视觉，正解是 `advance()` 里按寿命/存活主动失效；对**常驻插件**，正解是 `advance()` 里判 `engine.isCombatOver()`：

- `isCombatOver()` 在结束标志成立且淡出完成后才转 true—— 结束标志与淡出完成共同成立；淡出默认约 5 秒；
- 淡出窗口内 `isCombatOver()` 仍为 false：`advance()` 照常被调用 (实体推进循环不按 `combatOver` 分支)，渲染照常执行且 `viewport.getAlphaMult()` 持续下降 —— 把 `alphaMult` 送进 `intensity` uniform，能量场就随战斗结束淡出 (成熟实例 `alphaMult * getAlphaLevel() * extraAlphaMult` 同款做法)；
- 淡出完成后 `isCombatOver()` 转 true：`valid = false` → 引擎下一轮移除实体 → `cleanup()` 释放 GL 资源。资源在战斗内被释放，而不是随 `destroyInstance` 静默丢失。

::: warning 淡出窗口内的常见误判

淡出窗口 (约 5 秒) 内 `isCombatOver()` 仍为 false，`advance()` 与渲染照常执行。若在胜负判定一出现就判定 "战斗已结束" 并提前失效，能量场会在淡出中途硬切消失；正确做法是把 `viewport.getAlphaMult()` 送进 `intensity`，让视觉随淡出自然收缩，等 `isCombatOver()` 转 true 再主动失效。

:::

### 幂等与双路径

`cleanup()` 释放后必须把句柄置回哨兵值 (0/null)。必要性来自双路径：`init` 失败路径会手动调用一次 `cleanup()`(释放已创建的部分资源)，随后引擎移除实体时还会再调一次 —— 幂等保证第二次调用是空操作。成熟实例一致做法：释放后置 null/-1。

### 纹理归属

`SettingsAPI.unloadTexture(String)` **按文件名从全局纹理管理器移除**，不是引用计数。因此：

- 只对**自己** `loadTexture` 加载的纹理 (`SettingsAPI.loadTexture`) 调用 `unloadTexture`；
- 引擎启动期加载的共享贴图 (`particlealpha32sq.png` 等在启动期资源加载队列里的文件)**禁止**卸载 ——`getSprite` 返回的 `SpriteAPI` 只是一个共享引用，卸载会移除全进程的该条目，所有使用方 (原版粒子、其他 MOD、包括本插件自己) 都会受影响 (见故障 10)。

::: caution 共享贴图禁止卸载

`unloadTexture` 按文件名从全局纹理管理器移除条目，不是引用计数 —— 调用一次，全进程所有使用方都失去这张贴图。只对自己 `loadTexture` 加载的纹理调用它；引擎启动期加载的共享贴图 (本节的 `particlealpha32sq.png`) 禁止卸载 (规则 3；故障 10)。

:::

## 开关

### 三层语义

| 层 | 内容 | 读取时机 |
|---|---|---|
| 能力门禁 | `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(BoxUtil shader 路径可用性判据)| **每场战斗 `init`(GL 上下文就绪后) 重新判定**；门禁失败 → 不创建任何 GL 资源、插件保持注册但不渲染 (本节示例打 INFO `plugin idle`)|
| 运行时开关 | 本节示例的 `GearInShaderPlugin.setRuntimeEnabled(boolean)` | **每次使用点 (`render` 开头) 读取**，不缓存进 init 快照；关闭 → 零 GL 提交 |
| 语义边界 | 开关只改变渲染路径与显示成本，不改变玩法状态 (实例规范)| 常驻 |

### 读取时机与缓存陷阱

- **门禁禁止缓存进类加载期 `static final` 字段**：MOD 类加载发生在资源加载期/主菜单前，此时没有战斗 GL 上下文，`ShaderCore.isValid()` 不可评估 —— 缓存值恒错 (要么所有战斗都 `plugin idle`，要么低能力环境也创建程序)，且配置变更永远不生效 (运行期状态判据的语义)。必须在每场战斗 `init` 里重新判定 (规则 1)。
- **运行时开关每次使用点读取**：成熟实例的消费做法是每个使用点调用 `BoxConfigs.isShaderEnable()`(成熟实例每个粒子/爆炸 helper 每次调用先查开关，失败即回退原版粒子 `engine.addSmoothParticle`/`addSmokeParticle`/`spawnExplosion` 并返回 null)。本节示例的 `render` 每帧复核 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`，再检查 `runtimeEnabled`—— 任何一层关闭都走零提交路径。
- **失败模式**：开关结果被缓存 (init 快照或 static 字段) 后，运行时关闭不再生效 ——"图形开关关闭后仍走昂贵 shader 路径，会让关闭设置失去实际效果"(实例陷阱原话)。

::: caution 门禁判定禁止缓存进类加载期 static final 字段

类加载发生在资源加载期/主菜单前，此刻没有战斗 GL 上下文，`ShaderCore.isValid()` 不可评估 —— 缓存值恒错：要么所有战斗都 `plugin idle`，要么低能力环境也照常创建程序，且之后 BoxUtil 初始化与配置的任何变化都不再影响本插件。必须在 GL 上下文就绪后的每场战斗 `init` 里重新判定 (规则 1；故障 2)。

:::

### 降级

门禁失败/开关关闭时本节示例的降级是 "不创建资源、不渲染"(干净退出，无 ERROR)。

实例做法的另一条降级线是 "shader 路径关闭时用原版粒子或轻量路径"(实例降级做法：helper 回退 `engine.addSmoothParticle` 等)。

两条降级线共同点：**shader 实体不能作为唯一视觉输出**(实例规范)。

## 性能

::: info 为什么常驻插件是每帧固定成本

渲染系统按帧对所有已注册、未失效、落在剔除半径内的插件转发 `render` 回调，回调不因画面内容没有变化而跳过 —— 常驻插件只要还活着，每帧每激活层至少一次调用，成本与画面内容无关。这正是 "常驻插件 = 每帧固定成本" 的来源，也是本节用 `drawCalls` 计数做度量的基础。

:::

### 绘制开销的五个来源

| # | 来源 | 事实与成本 | 优化 |
|---|---|---|---|
| 1 | draw call 数 | 每帧每激活层一次 `render` 回调；一次 `glDrawArrays` = 1 个 draw call；常驻插件 = 每帧固定成本 (无论画面内容是否变化)| 多图元合并进一次调用：`glMultiDrawArrays`(成熟实例批量绘制做法) 或 BoxUtil instance 批量 (实体队列的 `setRenderingCount` + 提交链)|
| 2 | 剔除失效 | `render` 转发前判 `isNearViewport(entity.getLocation(), getRenderRadius())`(分层渲染通道的剔除判据)—— 半径过大/`Float.MAX_VALUE` → 离屏仍绘制 | `getRenderRadius()` 按实际覆盖范围取值；`Float.MAX_VALUE` 只留给全层承载模板 |
| 3 | 每帧查询/分配 | 每帧 `glGetUniformLocation` / `new FloatBuffer` 是驱动查询与 GC 压力 | location 创建后保存 (既有必须遵守的规则)；`FloatBuffer` 复用 (`clear`/`put`/`flip`)；render 内零分配 |
| 4 | 状态切换 | 纹理绑定/解绑、blend 状态切换有驱动成本 | 本节示例每帧一绑一解 (`glBindTexture` 后绘制、结束解回 0)；同纹理连续绘制时避免中间解绑 (延续既有收尾做法)|
| 5 | 失效对象回收 | `isExpired()` 恒 false 的残留插件每帧继续绘制；BoxUtil 实体入队失败不回收 → 渲染管理器内对象泄漏 | `advance()` 主动失效 (本节规则 2)；`CombatRenderingManager.addEntity` 返回非 `STATE_SUCCESS` 时必须显式 `delete()`(实体队列纪律)|

### 度量的做法

本节示例把 `framesRendered`/`drawCalls` 两个计数器在 `cleanup()` 日志输出：单层单四边形时两者相等 (每帧恰好 1 个 draw call)，这就是 "常驻插件每帧固定成本" 的度量面。可观测对比实验：

- 镜头拉远使玩家舰船离开视口外扩半径范围 → `drawCalls` 停止增长 (剔除生效的正面证明)；
- `setRuntimeEnabled(false)` → `drawCalls` 停止增长 (开关零提交的表现)；
- 故障 7 注入 (每帧多个 `glDrawArrays`)→ `drawCalls ≈ N × frames`(draw call 爆炸的度量)。

::: tip 用 drawCalls 的读数做快速自检

`cleanup` 日志的 `drawCalls` 与 `frames` 的比值就是每帧平均 draw call 数：两者相等说明每帧恰好一次提交；`drawCalls ≈ N × frames` 说明每帧有 N 次提交 (未合并)；`drawCalls` 停止增长说明某段时间根本没在绘制 —— 配合开关与镜头操作，剔除、零提交、爆炸三个实验都能在几秒内得到明确读数。

:::

### 日志纪律

`render` 回调内**禁止**打日志 (每帧日志会刷爆 `starsector.log` 并拖慢帧率)—— 日志只出现在 `init`、`cleanup` 与开关切换 (规则 7)。这是性能纪律里最容易踩、也最容易定位的一条。

## 必须遵守的规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 门禁判定 (`BoxConfigs.isShaderEnable() && ShaderCore.isValid()`) 在 GL 上下文就绪后的每场战斗 `init` 重新执行；禁止缓存进类加载期 `static final` 字段 (延续既有门禁纪律 + 读取时机)| 类加载期无 GL 上下文，`isValid()` 不可评估 → 缓存值恒错 (所有战斗禁用或低能力环境也创建程序)；配置变更永不生效 |
| 2 | **必须** | 持有 GL 资源的常驻插件必须在 `advance()` 里判 `engine.isCombatOver()` 主动失效 (`isExpired()` → true) 驱动引擎移除并调用 `cleanup()`；不能赌战斗结束的 `destroyInstance`(它不调 `cleanup()`)| program/VBO 随每场战斗泄漏 (`cleanup()` 永不执行)；多场战斗后驱动报错、渲染异常、卡顿 |
| 3 | **必须** | GL 资源只释放自己创建的：共享贴图 (引擎启动期加载) 禁止 `unloadTexture`(`unloadTexture` 按文件名从全局纹理管理器移除)| 共享贴图条目被移除 → 全进程所有使用方黑图/渲染异常；本插件自己后续战斗也受影响 |
| 4 | **必须** | `cleanup()` 释放后句柄复位 (置 0/null)，幂等：重复调用 (init 失败手动清理 + 引擎移除双路径) 安全 | 二次删除已释放句柄 (`glDeleteBuffers`/`program.delete()`)→ 未定义行为/崩溃 |
| 5 | **必须** | 运行时开关在每次使用点 (`render` 开头) 读取，不缓存进 init 快照或 static 字段 | 关闭后仍走昂贵 shader 路径：画面仍在、`drawCalls` 持续增长 —— 关闭设置失去实际效果 (实例陷阱)|
| 6 | **必须** | GL 提交只发生在 `render()` 回调内 (延续分层渲染通道的提交纪律)| `advance()` 里的 GL 命令在渲染阶段之外执行：内容不出现、错帧或层序错乱 |
| 7 | **必须** | `render` 内零日志、零分配、零 location 查询 (延续既有性能纪律)| 每帧日志刷屏 (一场战斗数万行) 拖慢帧率；每帧查询/分配 → 驱动查询开销与 GC 压力、帧率下降 |
| 8 | 建议 | `getRenderRadius()` 按实际覆盖范围取值，非必要不用 `Float.MAX_VALUE`(延续既有剔除半径建议)| 无功能错误；半径过大 → 离屏仍绘制，叠加多视觉时帧率下降 |
| 9 | 建议 | `render` 每帧复核 `BoxConfigs.isShaderEnable()`(成熟实例每个使用点读取的做法)| 无功能错误；让开关变更尽快生效、失败路径零提交 |
| 10 | 建议 | 多图元合并绘制 (`glMultiDrawArrays` 或 BoxUtil instance 批量) 减少 draw call 数 | 无功能错误；对象多时 draw call 开销线性增长 (性能)|

## 完整最小例

在既有 MOD 布局与战斗生命周期、分层渲染插件之上新增两个 shader 资源与一个插件类 (不新增任何注册项、不改动任何既有文件)。本节示例对 BoxUtil 的硬依赖沿用既有声明 (`mod_info.json` 的 `dependencies` 含 BoxUtil)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                        # 硬依赖 BoxUtil（C12-S06 既有声明）
├── data\
│   └── shaders\
│       ├── GearIn_shader_visual.vert    # C14-S03 所有；本节只消费
│       ├── GearIn_shader_visual.frag    # C14-S03 所有；本节只消费
│       ├── GearIn_field_shader.vert     # 本节新增
│       └── GearIn_field_shader.frag     # 本节新增
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin              # C06-S02 所有；本节不改动
│       ├── GearInGlobalAccess           # C06-S03 所有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin        # C07-S02 所有；本节只消费
│           ├── GearInCombatLifecyclePlugin    # C07-S05 所有；本节只消费
│           ├── GearInLayeredRenderer          # C07-S06 所有；本节只消费
│           ├── GearInPulseRenderer            # C14-S02 所有；本节只消费
│           ├── GearInShaderVisual             # C14-S03 所有；本节只消费
│           └── GearInShaderPlugin             # 本节新增
└── jars\
```

### GearIn_field_shader.vert

GLSL 4.30 方言 (`#version 430`)。结构与既有 `GearIn_shader_visual.vert` 同构 (attribute 位置在 GLSL 内固定、`BUtilGlobalData` UBO 供给世界→屏幕矩阵、`modelMatrix` 刚性变换、`size` 世界尺寸)：
```glsl
#version 430

layout (location = 0) in vec2 a_position;
layout (location = 1) in vec2 a_uv;

layout (std140) uniform BUtilGlobalData
{
    mat4 gameViewport;
    vec4 gameScreenBorder;
};

uniform mat4 modelMatrix;
uniform vec2 size;

out vec2 fragUV;

void main() {
    fragUV = a_uv;
    vec4 local = vec4(a_position * size * 0.5, 0.0, 1.0);
    gl_Position = gameViewport * vec4((modelMatrix * local).xy, 0.0, 1.0);
}
```

### GearIn_field_shader.frag

片元着色器绘制 "能量场"：

- `tex` 采样粒子贴图 (`particlealpha32sq.png`，白色径向渐变)，。
- `edgeFade` 把四边形外沿 (UV 距离 > 0.5) 羽化到零，。
- `pulse` 是随 `time` 呼吸、随 `d` 向外传播的脉冲 (周期约 π 秒)，。
- `intensity` 是总强度系数 (Java 侧送 `viewport.getAlphaMult()`，实现战斗结束淡出)：

```glsl
#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform float intensity;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.15));
    vec4 texSample = texture(tex, uv);
    float d = distance(fragUV, vec2(0.5, 0.5));
    float edgeFade = 1.0 - smoothstep(0.38, 0.50, d);
    float pulse = 0.55 + 0.45 * sin(time * 2.0 - d * 24.0);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a * edgeFade * pulse * intensity;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
```

- `fract(fragUV.y + time * 0.15)` 是纹理滚动 (与既有 shader 同款)；`mix(fringeColor.rgb, coreColor.rgb, texSample.r)` 用贴图红通道在边缘色与核心色之间插值；
- `edgeFade` 保证四边形外沿不可见 (能量场没有硬边)；透明度低于 `DISCARD_ALPHA` 的片元 `discard`(实例同款写法)。

### GearInShaderPlugin

Java 接入：常驻渲染插件，在分层渲染插件通道上完成注册 (层声明)→ 门禁与资源创建 (init)→ 提交 (render)→ 位置同步与战斗结束失效 (advance)→ 释放 (cleanup)。

源码与 fixture 逐字一致 (`GearInShaderPlugin` 与两个 shader 资源位于本单元 fixture，路径见上文 "四位置完整流程")：
```java
package data.scripts.gearin.combat;

import java.nio.FloatBuffer;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.ShaderProgram;
import org.boxutil.util.ShaderUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 完整最小例：常驻渲染插件的渲染层接入、资源释放、开关与性能。
 *
 * 事实（0.98a-RC8，BoxUtil 1.5.4 pinned commit d0423e5...，全部经成熟实例消费侧核验）：
 * - 渲染层接入：常驻插件每场战斗注册一次（成熟实例同款姿势：生命周期插件 init 内
 *   addLayeredRenderingPlugin(常驻渲染插件)），不按承载实体或
 *   寿命失效，跟随玩家舰船；层声明用顶层三图层之一的 ABOVE_PARTICLES_LOWER（粒子系统
 *   之后渲染，成熟实例同款）；渲染剔除依据是承载实体位置
 *   与 getRenderRadius()（C07-S06 硬规则 4），位置在 advance 里同步；
 * - 资源释放：GL 资源（program/VBO）在 cleanup() 释放并复位句柄（幂等，重复调用安全）；
 *   战斗结束 destroyInstance 不调 cleanup()—— 常驻插件必须在 advance() 里判
 *   engine.isCombatOver()（= 结束标志 && 淡出完成）
 *   主动失效（isExpired() -> true）驱动引擎移除，否则 program/VBO 随每场战斗泄漏；
 *   淡出期间 viewport.getAlphaMult() 下降，本示例把它送进 intensity uniform 实现视觉
 *   淡出（成熟实例同款姿势）；共享贴图（引擎启动期加载的
 *   particlealpha32sq.png）不卸载 ——
 *   SettingsAPI.unloadTexture 按文件名从全局纹理管理器移除，卸载共享贴图会伤及所有消费方；
 * - 开关：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 是 BoxUtil shader 路径
 *   可用性判据（K06/C12-S06），必须在 GL 上下文就绪后的 init 里重新判定，禁止缓存进
 *   类加载期 static final 字段（此时无 GL 上下文，isValid() 不可评估）；render 每帧
 *   复核（成熟实例每个使用点读取 BoxConfigs.isShaderEnable() 的姿势）；本示例另有运行时开关
 *   setRuntimeEnabled—— 只改变渲染路径与显示成本，不改变玩法状态（实例规范
 *   "图形开关只改变渲染路径和显示成本"），关闭时 render 零 GL 提交；
 * - 性能：一次 glDrawArrays = 一个 draw call，本示例每帧恰好 1 个（frames/drawCalls
 *   计数在 cleanup 日志输出）；uniform location 创建后保存、FloatBuffer 复用
 *   （clear/put/flip）、render 内零分配零日志；getRenderRadius 覆盖绘制范围即可，
 *   常驻插件也不需要 Float.MAX_VALUE（那是全层宿主模板的例外姿势，代价是永不离屏剔除）；
 *   批处理姿势见 glMultiDrawArrays 批量绘制与 C12-S06 的 instance 提交链。
 */
public class GearInShaderPlugin extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderPlugin.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /；C14-S02 硬规则 1 延续）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_field_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_field_shader.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致（大小写敏感）。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 能量场世界尺寸（局部坐标 [-1,1] × size/2）。 */
    private static final float QUAD_WIDTH = 220f;
    private static final float QUAD_HEIGHT = 220f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    /**
     * 运行时开关：只改变渲染路径与显示成本，不改变玩法状态（实例规范）。
     * 读取时机是每次使用点（render 开头），不缓存进 init 快照。
     */
    private static volatile boolean runtimeEnabled = true;

    public static void setRuntimeEnabled(boolean enabled) {
        if (runtimeEnabled == enabled) {
            return;
        }
        runtimeEnabled = enabled;
        LOG.info("GearIn shader plugin: runtime switch " + (enabled ? "on" : "off"));
    }

    public static boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    private CombatEngineAPI engine;
    private boolean valid = true;
    private long framesRendered = 0L;
    private long drawCalls = 0L;

    private ShaderProgram program;
    private SpriteAPI sprite;
    private int vboId = 0;
    private FloatBuffer matrixBuffer = null;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        // 门禁：BoxUtil shader 路径可用性判据（K06/C12-S06）。必须在 GL 上下文就绪后的
        // init 里重新判定，禁止缓存进类加载期 static final 字段 —— 类加载期无 GL 上下文，
        // ShaderCore.isValid() 不可评估（C14-S03 硬规则 1 延续）
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            LOG.info("GearIn shader plugin: BoxUtil shader path unavailable (shaderEnable="
                    + BoxConfigs.isShaderEnable() + ", shaderCoreValid="
                    + ShaderCore.isValid() + "), plugin idle");
            return; // 不创建任何 GL 资源；插件保持注册但不渲染，战斗结束时照常清理（空清理）
        }
        if (!createProgram() || !buildQuad() || !loadSprite()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            cleanup();     // 已创建的部分资源立即释放；cleanup 幂等，引擎移除时再调一次也安全
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（C07-S06 同款姿势）
        LOG.info("GearIn shader plugin init: layers=" + getActiveLayers()
                + ", u_model=" + program.location[0]
                + ", u_size=" + program.location[1]
                + ", u_time=" + program.location[2]
                + ", u_tex=" + program.location[3]
                + ", u_intensity=" + program.location[4]
                + ", u_core=" + program.location[5]
                + ", u_fringe=" + program.location[6]
                + ", ubo=" + program.uboLocation[0]
                + ", vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
    }

    /** 创建程序并保存 uniform/UBO location；失败时删除程序并返回 false。 */
    private boolean createProgram() {
        int programId = ShaderUtil.createShaderVFFormPath(getClass().getName(),
                VERT_PATH, FRAG_PATH);
        program = new ShaderProgram(programId);
        // 创建后立即保存 uniform/UBO location（实例规范）
        program.location = new int[] {
                program.getUniformIndex("modelMatrix"),
                program.getUniformIndex("size"),
                program.getUniformIndex("time"),
                program.getUniformIndex("tex"),
                program.getUniformIndex("intensity"),
                program.getUniformIndex("coreColor"),
                program.getUniformIndex("fringeColor")
        };
        program.uboLocation = new int[] {
                program.getUBOIndex(UBO_BLOCK_NAME, ShaderCore.getMatrixUBOBinding())
        };
        if (!program.isValid()) {
            LOG.error("GearIn shader plugin: program invalid after load");
            program.delete();
            program = null;
            return false;
        }
        return true;
    }

    /** 两个三角形组成的四边形：6 个顶点，局部坐标 [-1,1]，位置与 UV 交错写入一个 VBO。 */
    private boolean buildQuad() {
        FloatBuffer quadBuffer = BufferUtils.createFloatBuffer(6 * FLOATS_PER_VERTEX);
        quadBuffer.put(new float[]{
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,
                -1f, -1f, 0f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f
        });
        quadBuffer.flip();

        vboId = GL15.glGenBuffers();
        if (vboId == 0) {
            LOG.error("GearIn shader plugin: glGenBuffers returned 0");
            return false;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return true;
    }

    /** 取采样贴图；文件未加载时返回 false（getSprite 单参形态要求文件已加载）。 */
    private boolean loadSprite() {
        sprite = Global.getSettings().getSprite(SPRITE_PATH);
        if (sprite == null) {
            LOG.error("GearIn shader plugin: sprite not loaded: " + SPRITE_PATH);
            return false;
        }
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        // 顶层三图层之一：粒子系统之后渲染（C07-S06 层序）；
        // 成熟实例同款层选择
        return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
    }

    @Override
    public float getRenderRadius() {
        // 覆盖绘制范围即可；常驻插件也不需要 Float.MAX_VALUE（那是全层宿主模板的例外，
        // 全层宿主模板，代价是永不离屏剔除）
        return Math.max(QUAD_WIDTH, QUAD_HEIGHT);
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        // 剔除依据是承载实体的位置（C07-S06 硬规则 4）：常驻插件跟随玩家舰船
        entity.getLocation().set(engine.getPlayerShip().getLocation());
        // 战斗结束收尾：isCombatOver() = 结束标志 && 淡出完成。
        // 常驻插件必须在 advance 里主动失效驱动引擎移除并调用
        // cleanup()—— 战斗结束 destroyInstance 不调 cleanup()，等它等于每场泄漏
        // （C07-S06 硬规则 3 的常驻版）
        if (engine.isCombatOver()) {
            valid = false;
        }
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        boolean hadResources = vboId != 0 || program != null;
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0; // 句柄复位：cleanup 幂等，重复调用安全（防二次删除）
        }
        if (program != null) {
            program.delete();
            program = null;
        }
        if (hadResources || framesRendered > 0) {
            LOG.info("GearIn shader plugin cleanup: frames=" + framesRendered
                    + ", drawCalls=" + drawCalls);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == null || sprite == null || vboId == 0) {
            return;
        }
        // 运行时开关：每次使用点读取（不缓存）—— 关闭 → 零 GL 提交（开关只改变渲染路径
        // 与显示成本；实例规范与陷阱"关闭后仍走昂贵路径"）
        if (!runtimeEnabled) {
            return;
        }
        // 每帧复核 shader 路径可用性：成熟实例每个使用点读取 BoxConfigs.isShaderEnable()
        // 的姿势（成熟实例各 helper），失败路径零提交
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            return;
        }
        if (!program.isValid()) {
            return; // 程序失效不得继续绑定（实例陷阱）
        }
        // 淡出期间的 alphaMult 收缩：intensity uniform 用它实现能量场随战斗结束淡出；
        // alpha 为 0（淡出完成）时跳过整段提交
        float alpha = viewport.getAlphaMult();
        if (alpha <= 0f) {
            return;
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换（旋转 + 平移），列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = entity.getLocation();
        float facing = engine.getPlayerShip().getFacing();
        matrixBuffer = buildAnchoredMatrix(center, facing, matrixBuffer);

        // attribute：VBO 绑定 + long 偏移（C14-S02 同款姿势；游戏自带 LWJGL 的
        // glVertexAttribPointer 没有 FloatBuffer client 指针重载）
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        program.active(); // 提交段开始；必须与 close() 配对（实例规范）

        // 矩阵提交：modelMatrix（uniform matrix4，列主序）
        GL20.glUniformMatrix4(program.location[0], false, matrixBuffer);
        // 标量/向量 uniform：size（世界尺寸）、time（暂停冻结的战斗总时间）
        GL20.glUniform2f(program.location[1], QUAD_WIDTH, QUAD_HEIGHT);
        GL20.glUniform1f(program.location[2], engine.getTotalElapsedTime(false));

        // 纹理提交：单元 0 绑定 + 采样器指向单元 0（值必须等于单元号）
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sprite.getTextureId());
        GL20.glUniform1i(program.location[3], 0);

        // intensity（alphaMult 驱动的淡出强度）与颜色 uniform：外橙内蓝
        GL20.glUniform1f(program.location[4], alpha);
        GL20.glUniform4f(program.location[5], 1.0f, 0.55f, 0.2f, alpha);
        GL20.glUniform4f(program.location[6], 0.15f, 0.45f, 1.0f, alpha);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        program.close(); // 提交段结束

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); // 解绑，避免影响后续绘制者的纹理状态

        framesRendered++;
        drawCalls++; // 一次 glDrawArrays = 一个 draw call（性能度量，见页面"性能"节）
    }

    /** 2D 刚性变换矩阵（旋转 + 平移），列主序 16 float；每帧复用缓冲（clear/put/flip）。 */
    private static FloatBuffer buildAnchoredMatrix(Vector2f location, float angle,
            FloatBuffer buffer) {
        if (buffer == null || buffer.capacity() < 16) {
            buffer = BufferUtils.createFloatBuffer(16);
        }
        float radians = (float) Math.toRadians(angle);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);

        buffer.clear();
        buffer.put(cos).put(sin).put(0f).put(0f);   // 列 0
        buffer.put(-sin).put(cos).put(0f).put(0f);  // 列 1
        buffer.put(0f).put(0f).put(1f).put(0f);     // 列 2
        buffer.put(location.x).put(location.y).put(0f).put(1f); // 列 3（平移）
        buffer.flip();
        return buffer;
    }
}
```

对应关系：

- **层接入 (init/字段)**：`getActiveLayers()` 返回 `EnumSet.of(ABOVE_PARTICLES_LOWER)`(顶层三图层，成熟实例同款)；`getRenderRadius()` 返回四边形尺寸 (覆盖绘制范围，建议 8)；`advance` 里 `entity.getLocation().set(engine.getPlayerShip().getLocation())` 位置同步 (剔除依据是承载实体位置)。
- **资源释放 (init/cleanup)**：门禁失败 → 不创建任何 GL 资源 (INFO `plugin idle`，干净降级)；创建部分失败 → `valid = false` + 手动 `cleanup()`(已建资源立即释放)，引擎移除时再调一次也安全 (规则 4 的幂等演示)；`cleanup()` 释放 program/VBO 并复位句柄；`advance` 里 `isCombatOver()` 转 true → `valid = false`(规则 2 的常驻版：不赌 `destroyInstance`)。
- **开关 (init/render)**：门禁在 `init` 重新判定 (规则 1)；`render` 每帧复核 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()` 与 `runtimeEnabled`(规则 5，建议 9)—— 任何一层关闭都零提交；`setRuntimeEnabled` 只改渲染路径，不改玩法状态。
- **提交 (render)**：`program.active()`(提交段配对纪律)→ `glUniformMatrix4`(规则 8)→ `glUniform2f/1f`(标量/向量)→ 纹理单元 0 绑定 + `glUniform1i(texLoc, 0)`(规则 6)→ `intensity`(`alphaMult` 驱动淡出) 与颜色 uniform → `glDrawArrays` → `program.close()`；结束解绑与 `drawCalls++`(性能度量)。
- **性能 (render/cleanup)**：location 创建后保存、`FloatBuffer` 复用 (规则 7 与既有零分配纪律)；`framesRendered`/`drawCalls` 计数进 `cleanup` 日志 —— 单层单四边形时两者相等 (每帧 1 个 draw call)。

注册片段 (工程片段 —— 本节不改动任何既有文件，只演示使用方调用；把下面一行加进生命周期插件的 `init(CombatEngineAPI engine)` 末尾)：

```java
    // 工程片段：生命周期插件的 init(CombatEngineAPI engine) 内追加
    engine.addLayeredRenderingPlugin(new GearInShaderPlugin());
```

与既有示例的注册片段同一做法，区别是本节插件**常驻**：不设寿命、跟随玩家舰船、战斗结束由 `isCombatOver()` 主动失效。运行时开关的调用方示例 (调试/演示用，任何战斗插件都可调用)：

```java
    // 工程片段：任意战斗插件内调用，切换本节示例的运行时开关
    GearInShaderPlugin.setRuntimeEnabled(false);
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用既有 MOD 构建流程)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/shaders/GearIn_field_shader.vert`、`data/shaders/GearIn_field_shader.frag` 与 `data/scripts/gearin/combat/GearInShaderPlugin.class`(可用 `jar tf` 核对)。

本节源码与 fixture 逐字一致 (shader 两个文件在 `tutorial-fixtures\units\C14-S04\src\main\resources\data\shaders\`，Java 类在 `tutorial-fixtures\units\C14-S04\src\main\java\data\scripts\gearin\combat\GearInShaderPlugin`)。

### 实例对照

成熟实例与本节描述同构的做法：

- 常驻注册 (生命周期插件 `init` 内 `addLayeredRenderingPlugin(常驻渲染插件)`)、。
- 全层承载模板 (`EnumSet.allOf(...)` + `Float.MAX_VALUE`，代价是永不离屏剔除)、。
- 顶层三图层层选择 (`ABOVE_PARTICLES_LOWER`)、；
- 淡出强度 (`alphaMult` 合成)、；
- 开关使用点读取与降级、；
- 清理释放并复位句柄、；
- `glMultiDrawArrays` 批处理。

### 验证方案 (基于本例)

以本节 `GearInShaderPlugin` 与两个 shader 资源为对象，验证从常驻注册到战斗结束清理的完整链路。

**前置数据**

- 既有 MOD 布局与硬依赖声明：`mod_info.json` 的 `dependencies` 含 BoxUtil(C12-S06)；
- 既有插件与注册片段：战斗生命周期插件 (含本节注册片段一行，C07-S05)、分层渲染插件 (C07-S06)、前两节的 shader 视觉插件 (C14-S02、C14-S03)；
- 本节新增：`GearInShaderPlugin` 与 `data\shaders\GearIn_field_shader.vert/.frag`；
- 测试战役 `GearIn_test`(C03-S01)；环境需满足 BoxUtil 的 GL 4.3 能力基线 (`ShaderCore.isValid()` 为 true)。

**操作**

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC14s04Java --no-daemon`，确认 `BUILD SUCCESSFUL`(退出码 0)。
2. fixture 资源：在 Git Bash 执行 `gradle -p tutorial-fixtures validateShaders --no-daemon`，确认 `BUILD SUCCESSFUL`(退出码 0)。
3. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含两个 shader 与 `GearInShaderPlugin.class`。
4. 战役冒烟：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 init 行 (含 `layers=[ABOVE_PARTICLES_LOWER]`、`vert=`/`frag=` 路径与七个 `u_*` location、`ubo=`)；`ubo=<loc>` 非 -1；没有 `gear_in` 引起的 `ERROR`。
5. 画面：观察玩家舰船周围 220×220 世界单位能量场：外橙内蓝、柔和光晕、外沿羽化、亮度呼吸脉冲、纹理滚动；位于所有粒子之上；跟随舰船移动。
6. 常驻：战斗持续 60 秒以上 —— 能量场全程存在 (对比一次性视觉的寿命)；按空格暂停约 10 秒 —— 能量场冻结但可见，恢复后继续。
7. 开关：战斗中调用 `GearInShaderPlugin.setRuntimeEnabled(false)`—— 日志 `GearIn shader plugin: runtime switch off`，能量场立即消失；战斗中继续推进若干秒后调回 `true`——`runtime switch on`，能量场恢复。
8. 剔除度量：镜头拉远使舰船离开视口外扩 220 单位范围 —— 能量场消失；拉回出现。
9. 淡出清理：打出胜负判定 —— 能量场随淡出约 5 秒消失，`starsector.log` 出现 `GearIn shader plugin cleanup: frames=<n>, drawCalls=<n>`(两者相等或接近)。
10. 跨战斗：退出战斗再进一场 ——init 行重新出现、句柄全新；每场战斗结束都有 cleanup 行 (无泄漏)；连续 5 场后游戏无卡顿/报错。
11. 门禁降级：在 BoxUtil 配置关闭 shader 路径后进战斗 ——`starsector.log` 出现 `GearIn shader plugin: BoxUtil shader path unavailable (...), plugin idle`，无画面、无 ERROR、无资源创建 (shader 路径干净降级)。
12. 故障注入：a) 注释掉 `advance` 里的 `isCombatOver()` 分支 —— 战斗结束无 cleanup 行 (泄漏，对应故障 1)；b) 把门禁结果存 `static final`——init 行不再随环境变化 (对应故障 2)；c) 把 `cleanup()` 的句柄复位删掉并手动触发 init 失败路径 —— 二次删除风险 (对应故障 9)；d) `cleanup()` 加 `unloadTexture(SPRITE_PATH)`—— 共享贴图黑图/异常 (对应故障 10)；e) 改回后确认恢复。

**通过信号**

- 操作 1–11 全部符合；
- 操作 12 的注入与恢复都符合预期。

**清理**

- 取消勾选测试 MOD；
- 恢复代码与 shader 为原值；
- 确认没有对共享文件 (`tutorial-fixtures\build.gradle` 等) 做过修改。

## 预期结果

1. **fixture 编译**：在 Git Bash 执行。
```text
gradle -p tutorial-fixtures compileC14s04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束 (退出码 0)。该任务编译 `units/C14-S04/src/main/java`，编译类路径已含 BoxUtil jars(`tutorial-fixtures\build.gradle` 的 `fixtureJars`)—— 本节 Java 代码的 `ShaderUtil`/`ShaderProgram`/`ShaderCore`/`BoxConfigs` 调用由此完成 "本地发行签名" 核对。
2. **fixture 资源**：在 Git Bash 执行。
```text
gradle -p tutorial-fixtures validateShaders --no-daemon
```
以 `BUILD SUCCESSFUL` 结束 (退出码 0)。该任务检查 `units/C14-S04/src/main/resources` 下的所有 `.vert/.frag` 非空。
3. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/shaders/GearIn_field_shader.vert`、`data/shaders/GearIn_field_shader.frag` 与 `data/scripts/gearin/combat/GearInShaderPlugin.class`。
4. **战斗初始化 (常驻注册 + 门禁 + 资源创建)**：进入测试战役 `GearIn_test` 一场战斗，`starsector.log` 出现一行。
```text
GearIn shader plugin init: layers=[ABOVE_PARTICLES_LOWER], u_model=<loc>, u_size=<loc>, u_time=<loc>, u_tex=<loc>, u_intensity=<loc>, u_core=<loc>, u_fringe=<loc>, ubo=<loc>, vert=data/shaders/GearIn_field_shader.vert, frag=data/shaders/GearIn_field_shader.frag
```
其中 `layers=` 与 `getActiveLayers()` 一致、`vert=`/`frag=` 与资源文件路径一致 (四位置完整流程)、`ubo=<loc>` 非 -1；没有由 `gear_in` 引起的 `ERROR`。若 BoxUtil 未初始化/被关闭，则出现 `GearIn shader plugin: BoxUtil shader path unavailable (...), plugin idle` 且无画面 (门禁生效、零资源)。
5. **画面 (常驻效果)**：玩家舰船周围出现一个 220×220 世界单位的能量场 —— 中心偏橙 (`coreColor`)、边缘偏蓝 (`fringeColor`)、柔和光晕 (径向渐变 + `discard` 剔除透明片元)、外沿羽化无硬边 (`edgeFade`)、亮度呼吸脉冲 (约 π 秒周期) 并伴随纹理滚动；能量场位于**所有粒子之上**(`ABOVE_PARTICLES_LOWER` 顶层三图层，层选择见 "渲染层接入")；舰船移动时能量场跟随。
6. **常驻语义**：能量场**不按寿命消失**(对比前几节一次性视觉示例的 20–30 秒寿命)—— 战斗持续多久它就在多久；`advance` 暂停守卫下按空格暂停 —— 能量场冻结但可见，恢复后继续。
7. **淡出与清理 (战斗结束收尾)**：胜负判定后能量场随 `viewport.getAlphaMult()` 约 5 秒淡出 (`isCombatOver()` 在淡出完成前为 false，渲染继续、强度收缩)；淡出完成后 `starsector.log` 出现一行。
```text
GearIn shader plugin cleanup: frames=<n>, drawCalls=<n>
```
即 `isCombatOver()` → 主动失效 → 引擎移除 → `cleanup()` 的正常链 (规则 2 的可观察面)；`frames` 与 `drawCalls` 相等 (每帧恰好 1 个 draw call)。
8. **跨战斗**：退出战斗再进一场 ——init 行重新出现且 location 值重新查询、句柄全新 (每场战斗重建 program/VBO，与既有 `Global` 生命周期纪律同规)；无 ERROR、无泄漏迹象 (每场战斗结束都有 cleanup 行)。
9. **开关 (运行时)**：战斗中调用 `GearInShaderPlugin.setRuntimeEnabled(false)`——`starsector.log` 出现 `GearIn shader plugin: runtime switch off`，能量场立即消失且 `drawCalls` 停止增长 (零提交)；调回 `true` 出现 `runtime switch on`，能量场恢复。
10. **剔除 (性能度量)**：把镜头拉远使玩家舰船 (连同 220 单位半径) 离开视口 —— 能量场消失且 `drawCalls` 停止增长 (`isNearViewport` 剔除生效)；拉回后恢复增长。
11. **故障注入**：见 "实例驱动故障分析" 第 1、2、3、4、7、8、9、10 条对应的注入动作与恢复。

静态自查：

- `GearInShaderPlugin` 与 fixture 逐字一致 (包括 import 顺序与注释)；
- `GearIn_field_shader.vert/.frag` 与 fixture 逐字一致；
- 四位置完整流程中资源文件、加载入口 (Java 常量) 与使用方日志三处路径逐字一致；
- UBO 块名 `BUtilGlobalData` 与七个 uniform 名在 GLSL 声明与 Java 参数中逐字一致；
- 任何包名、类名、资源名或字符串 key 中都不含单元编号 (包 `data.scripts.gearin.combat`、类 `GearInShaderPlugin`、资源 `data/shaders/GearIn_field_shader.vert/.frag`、日志前缀 `GearIn shader plugin`)；
- 除本节目录与 fixture 目录外没有改动任何共享文件。

## 实例驱动故障分析

1. **常驻插件不判战斗结束 (资源随战斗泄漏)**。触发：注释掉 `advance` 里的 `if (engine.isCombatOver()) { valid = false; }`。可观察：战斗结束时 `cleanup()` **永不执行**(`destroyInstance` 只清空对象表、不调 `cleanup()`)—— 每场战斗泄漏一个 program + VBO；多场战斗后驱动报错、渲染异常或卡顿；`starsector.log` 里没有对应的 `cleanup` 行。修正：`advance()` 里判 `isCombatOver()` 主动失效 (规则 2)。
2. **门禁结果缓存进 `static final`**。触发：在类加载期求值 `private static final boolean GATE_OK = BoxConfigs.isShaderEnable() && ShaderCore.isValid();` 并在 `init` 里只用 `GATE_OK`。可观察：类加载发生在资源加载期/主菜单前，此时没有战斗 GL 上下文 ——`ShaderCore.isValid()` 不可评估，缓存值恒错：所有战斗都 `plugin idle`(值恒 false)，或低能力环境也照常创建程序 (值恒 true)；且此后 BoxUtil 初始化/配置的任何变化都不再影响本插件。修正：每场战斗 `init` 重新判定 (规则 1)。
3. **运行时开关只读 init 快照**。触发：`init` 里存 `boolean switchSnapshot = runtimeEnabled`，`render` 只用快照。可观察：`setRuntimeEnabled(false)` 后画面仍在、`drawCalls` 持续增长 —— 关闭设置失去实际效果 (实例陷阱原话)，且无任何日志提示。修正：每次使用点读取 (规则 5)。
4. **跨战斗缓存 GL 对象**。触发：把 `program`(或 `engine`) 存进 `static` 字段，第二场战斗不重建。可观察：第一场战斗结束 `cleanup()` 已 `program.delete()`—— 第二场战斗引用已删句柄，渲染异常/未定义行为 (既有跨战斗缓存禁令的延续)。修正：每场战斗 `init` 重建。
5. **`render` 内每帧查询 location**。触发：把 `program.getUniformIndex(...)` 调用搬进 `render`。可观察：每帧驱动级查询 (无功能错误)，多插件叠加时帧率下降；init 日志里的 location 值失去 "拼错排查" 价值 (既有零分配纪律违反的可观察面)。修正：创建后保存 (规则 7)。
6. **`getRenderRadius()` 用 `Float.MAX_VALUE`**。触发：半径改成 `Float.MAX_VALUE`(本节示例不需要)。可观察：玩家舰船在屏幕外时能量场仍被渲染 (永不离屏剔除)—— 单四边形成本不明显，但叠加多视觉时帧率下降 (本节建议 8，延续既有剔除半径建议)。修正：按实际覆盖范围取值；全层承载模板才用 `Float.MAX_VALUE`。
7. **draw call 过多未合并**。触发：让插件每帧画 8 个独立四边形、每个一次 `glDrawArrays`。可观察：`cleanup` 日志 `drawCalls ≈ 8 × frames`—— 每帧 8 个 draw call；对象数增多时开销线性增长 (性能节来源 1)。修正：`glMultiDrawArrays` 合并或 BoxUtil instance 批量提交。
8. **`render` 内打日志**。触发：`render` 里加一行 `LOG.info(...)`。可观察：`starsector.log` 每帧刷行 —— 一场战斗数万行、帧率明显下降；日志文件膨胀。修正：日志只放 `init`/`cleanup`/开关切换 (规则 7)。
9. **`cleanup()` 不幂等 (句柄不复位)**。触发：删掉 `cleanup()` 里的句柄复位 (`vboId = 0` / `program = null`)，保留 init 失败路径的手动 `cleanup()`。可观察：初始化部分失败时手动 `cleanup()` 删了一次，引擎移除时再删一次 —— 对已释放句柄二次 `glDeleteBuffers`/`program.delete()`，未定义行为/崩溃。修正：释放后句柄复位 (规则 4)。
10. **共享贴图被 `unloadTexture`**。触发：`cleanup()` 里加 `Global.getSettings().unloadTexture(SPRITE_PATH)`。可观察：`unloadTexture` 按文件名从全局纹理管理器移除该条目 —— 全进程所有使用方 (原版粒子、其他 MOD、本插件自己) 的该贴图黑图或渲染异常；本插件第二场战斗 `getSprite` 仍可能取到对象但纹理已失效。修正：共享贴图不卸载 (规则 3)；只对自己 `loadTexture` 的纹理调用 `unloadTexture`。

## 总结

本节把渲染接入的架构面收成完整流程：两条渲染入口与常驻/一次性选择决定生命周期归属；资源释放纪律 (创建者清理、`cleanup()` 幂等复位、战斗结束主动失效、共享贴图不卸载) 保证 GL 资源不泄漏；开关的三层语义与读取时机避免 "关了等于没关"；五个开销来源与 `drawCalls` 计数让性能问题可量化；十个故障案例把每条必须遵守的规则的违反路径变成可注入、可观察、可修正的对照实验。

从 GLSL 数据流到常驻生命周期，渲染接入的完整设计在此收束。

逻辑层面的展望：这套 "注册 → 层声明 → 提交 → 推进/失效 → 清理" 五环节框架不限于本节形态 —— 批量 instance 渲染、后期处理、UI 覆盖等更复杂的视觉形态，同样落在这套 "入口选择 → 资源归属 → 性能度量" 的坐标系上，可以直接套用本节的判据与度量做法。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局访问入口，`getLogger`/`getSettings`/`getCombatEngine`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：战斗引擎，`addLayeredRenderingPlugin`/`isCombatOver`/`getTotalElapsedTime`/`getPlayerShip`/`isPaused`。
- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin`：渲染插件接口，七个回调的约定。
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：渲染插件基类，本节插件继承它。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：渲染层枚举，`ABOVE_PARTICLES_LOWER` 所在。
- `com.fs.starfarer.api.combat.ViewportAPI`：视口 API，`getAlphaMult()` 提供战斗结束淡出强度。
- `com.fs.starfarer.api.SettingsAPI`：`getSprite(String)`/`loadTexture`/`unloadTexture` 的声明处。
- `com.fs.starfarer.api.graphics.SpriteAPI`：贴图引用，`getTextureId()`。
- `org.boxutil.config.BoxConfigs`：shader 路径开关，`isShaderEnable()`。
- `org.boxutil.manager.ShaderCore`：shader 核心状态，`isValid()`/`getMatrixUBOBinding()`。
- `org.boxutil.manager.CombatRenderingManager`：渲染实体队列，`addEntity`/`delete` 的对照入口。
- `org.boxutil.units.standard.ShaderProgram`：程序封装，`location`/`uboLocation`/`active`/`close`/`delete`。
- `org.boxutil.util.ShaderUtil`：`createShaderVFFormPath` 程序创建入口。
- `data/shaders/GearIn_field_shader.vert` / `.frag`：本节 shader 资源 (本节所有权)。
- `graphics/fx/particlealpha32sq.png`：引擎启动期加载的共享粒子贴图 (采样纹理)。
- `Starsector/starsector-core/starsector.log`：游戏日志，本节所有可观察结果都落在里面。
