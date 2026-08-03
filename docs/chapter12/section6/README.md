# BoxUtil 工具：实体、字体与绘制

本节讲解 GPU 渲染依赖库 BoxUtil 1.5.4 的最小消费面。两段式初始化 (`initPre` / `initLater`) 与可用性判据、渲染实体 (render entity) 的创建与实例数据提交、字体 (`.fnt` 位图字体与 `TextFieldEntity`)、绘制 (渲染队列、图层与混合) 和生命周期 (control data、全局计时与删除)。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 说出 BoxUtil 1.5.4 的两段式初始化 (`BoxUtilModPlugin.initPre()` / `initLater()`) 与各自时机 (`onApplicationLoad` vs OpenGL context 创建后)。
- 说出初始化状态查询 (`isPreInitialized` / `isGlobalInitialized`)；
- 说出 shader 渲染入口的可用性判据为什么必须是 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`，以及用 `isBaseGL43Supported()` 代替它为什么是错的；
- 说出渲染实体的入队链 (创建 → 设置 → `CombatRenderingManager.addEntity`) 与返回码语义 (`BoxEnum.STATE_*`)。
- 说出实例数据的完整提交链 (`setInstanceData` → refresh → `submitInstanceData` → `setRenderingCount`)；
- 说出字体链 (`FontDataManager.tryFont` → `FontMapData` → `TextFieldEntity` → `submitText` → `directDraw`) 与 `.fnt` 数据来源，解释文本修改后为什么必须重新 `submitText()`；
- 说出 `TextFieldEntity` 的屏幕空间绘制写法 (窗口正交矩阵 + `directDraw()`) 与调用时机约束 (只能在渲染回调内)；
- 说出生命周期的三个环节：control data 回调 (`controlInit` / `controlAdvance` / `controlRemove`)。
- 全局计时 (`setGlobalTimer` 与 `BoxEnum.TIMER_*` 状态集)、删除 (`delete()` / `hasDelete()`)；
- 从症状定位典型故障：`available=false` 降级、提交链缺失、`addEntity` 返回码被忽略、文本不刷新、跨战斗缓存、非渲染回调里 `directDraw`。

## 前置知识

本节建议在完成前置单元后阅读，您需要已经理解：

- 硬依赖 (hard dependency) 声明与运行时检测 (C12-S01)：引用依赖库的类前 `dependencies` 必须声明对应 MOD，否则启动期 `NoClassDefFoundError`；
- 战斗图层层序与渲染回调纪律 (C07-S06)：GL 原语只在渲染回调内使用 —— 本节 `setLayer(CombatEngineLayers)` 与 `directDraw()` 的时机约束延续同一条纪律；
- settings `plugins` 段注册与战斗插件模型、暂停守卫 (C07-S01)：BoxUtil 自己的 `BUtil_CombatEFS` 就是按这个模型每场战斗实例化；
- `modPlugin` 与 `onApplicationLoad()` 时机 (C06-S02)：`initPre()` 的接入点；
- `Global` 生命周期 (C06-S03)：禁止跨战斗缓存引擎引用与渲染对象 (本节文本实体每场战斗重建)；
- 战斗插件生命周期与 "战斗结束没有清理回调"(C07-S05)：BoxUtil 实体靠计时到期 / 显式 `delete()` 收尾，不赌战斗结束回调；
- 战役 (mission) 与生涯 (campaign) 概念、`GearIn_test` 测试战役 (C03-S01)：本节用它做验证；
- 对照 GraphicsLib(C12-S03)：同为 GPU 渲染依赖库，但本节只讲 BoxUtil 的战斗渲染队列；生涯侧 `CampaignRenderingManager` 只记录边界 (对照结论：战役画面不经过本节链路)。

## BoxUtil 1.5.4 概览

BoxUtil 是 utility 类 MOD(`mod_info.json` 的 `utility: true`，自身不含实质游戏内容)。

它侧重 GPU 渲染，提供 WaveFront OBJ 三维模型导入渲染、sprite / flare / curve / trail / segment / text 等渲染实体、instance 批量渲染、自定义着色器 (shader) 与 FBO、OpenCL 内核等扩展能力。

本节只覆盖战斗侧实体与字体绘制的最小消费面。

本地发行事实 (`Starsector\mods\BoxUtil`)：

| 文件 | 内容 |
|---|---|
| `mod_info.json` | `id` `BoxUtil`、`version` 1.5.4、`gameVersion` `0.98a-RC8`、`jars` 两件 (`jars/BoxUtilMod.jar` + `jars/backends/BoxUtilImpl.jar`)、`modPlugin` `org.boxutil.BoxUtilModPlugin`、`utility: true` |
| `data\config\settings.json` | `plugins` 段注册 `BoxConfigGUI`(配置界面，`org.boxutil.config.BoxConfigGUI`) 与 `BUtil_CombatEFS`(战斗插件，`org.boxutil.backends.core.BUtil_CombatEFS`)；`graphics.fonts` 段注册字体 (如 `BUtil_IS` → `graphics/fonts/insignia15LTaa_0.png`)|
| `data\config\custom_entities.json` | `BUtil_CampaignRenderingManager` 生涯侧承载实体 (`org.boxutil.backends.core.BUtil_CampaignRenderingPlugin`)—— 生涯侧边界，本节不展开 |
| `data\missions\mission_list.csv` + `data\missions\BUtilTestMission\descriptor.json` | 自带测试战役 "BoxUtil Test"，可用来核对 BoxUtil 后端自身是否正常 |
| `<saves>\common\BUtil_Configs.json.data` | BoxUtil 配置的持久化文件 |

`BUtil_CombatEFS` 按 settings `plugins` 段登记的战斗插件模型每场战斗实例化并附加到战斗引擎；`CombatRenderingManager` 的实体队列由 BoxUtil 后端消费，本节示例只负责 "创建 → 入队 → 生命周期"，不自己写分层渲染插件。

BoxUtil API 按能力域划分边界：实体族归属 `CombatRenderingManager`(战斗渲染队列)、`CampaignRenderingManager` 归属生涯渲染队列、`ShaderCore` / `PublicFBO` / `ShaderProgram` 归属自定义 shader 状态。

`BaseControlData` / `SimpleParticleControlData` 归属实体控制器回调。

## 初始化：initPre / initLater 与可用性判据

### 两段式初始化

BoxUtil 的初始化分两段：`BoxUtilModPlugin.initPre()` 先于配置、GL state 或 render entity 读取执行；`initLater()` 在 OpenGL context 创建后执行。两段都必须在任何 BoxUtil 实体入队之前完成。

::: info 为什么分两段？

两段的职责不同：`initPre()` 只做不依赖 GL 的准备 (读取配置、登记 render entity 定义)，在应用加载期即可完成。

`initLater()` 负责依赖 GL 状态的资源创建，OpenGL context 未创建时没有可挂靠的渲染状态。

把这两件事拆开，才能让不依赖 GL 的准备尽量提前、与 GL 相关的初始化严格落在 context 创建之后。

:::

成熟实例的接入写法：

- 成熟实例在 `onApplicationLoad` 里调用 `BoxUtilModPlugin.initPre();`(`onApplicationLoad` 是资源加载完成后、主菜单前的时机)；
- 成熟实例在自己的常驻战斗渲染插件 `init` 里调用 `BoxUtilModPlugin.initLater();`(此时 OpenGL context 已创建)。

初始化状态查询 (公开签名)：`isPreInitialized()` / `isGlobalInitialized()`。

本节示例在战斗插件 `init` 里用 `isGlobalInitialized()` 守卫调用一次 `initLater()`，效果与成熟实例 "常驻渲染插件 init 里直接调用" 同为 "OpenGL context 创建后的初始化"。

### 可用性判据

shader 渲染入口是否可用由两个条件**共同**决定：

```text
BoxConfigs.isShaderEnable()   —— 配置开关（用户/配置界面控制的启用状态）
&&
ShaderCore.isValid()          —— program / FBO / VAO / 矩阵程序是否全部初始化成功
```

`BoxConfigs.isShaderEnable()` 为 false 时，**BoxUtil shader 实体不能作为唯一视觉输出**。

必须提供降级分支 (成熟实例回退 `engine.addSmoothParticle` / `addSmokeParticle` / `spawnExplosion` 原版粒子并返回 null)。

`ShaderCore.isValid()` 覆盖的是 "所有主程序是否有效"。

`BoxConfigs.isBaseGL43Supported()` 只是 GL 能力探测 (是否支持 GL 4.3 基线)，用它代替 `ShaderCore.isValid()` 会漏掉 program、FBO、VAO 或 matrix program 初始化失败。

::: tip 一行日志核对三件事

示例在 `init` 里输出 `GearIn box init: shaderEnable=… shaderCoreValid=… available=… font=…`：前两项分别对应配置开关与 GPU 资源有效性，`available` 是两者的合取，`font` 附带实际字号。

出问题时先看这一行，能直接区分 "配置关闭""初始化失败""字体加载失败" 三类原因。

:::

## 渲染实体与实例数据 (Entity)

### 实体族速查

BoxUtil 渲染实体全部实现 `RenderDataAPI`(`org.boxutil.base.api`)，按 `CombatRenderingManager.addEntity(...)` 入队后由 BoxUtil 后端按层绘制：

| 实体类 (`org.boxutil.units.standard.entity`)| 用途 |
|---|---|
| `SpriteEntity` | 贴图精灵 (sprite / tile / UV / 随机 tile / instance 批量)，本节示例主角 |
| `FlareEntity` | 光斑 (size / style / flick / core-fringe 颜色 / glow / noise)|
| `CurveEntity` / `TrailEntity` / `SegmentEntity` | 曲线 / 尾迹 / 线段节点渲染 (`NodeData` + `submitNodes()`)|
| `DistortionEntity` | 局部扭曲 (size / power in-full-out / inner ratio / ring hardness)|
| `CommonEntity` | 3D 模型渲染 (OBJ 导入，`graphics\objs\BUtil_*.obj` 与 `BUtil_obj_data.csv`)|
| `TextFieldEntity` | 位图字体文本 (见 "字体" 节)|

`org.boxutil.units.standard.misc` 下的 `ArcObject` / `LineObject` / `QuadObject` / `NumberObject` / `TextFieldObject` 等是 immediate draw 对象。

它们不进入渲染队列，生命周期由调用方所在 render callback 管理。

### SpriteEntity 创建与实例数据提交链

创建与设置 (成熟实例写法)：

```text
new SpriteEntity(SpriteAPI 或贴图路径)
→ getMaterialData().setColor(...)  / setAlphaToEmissive(...)   （材质颜色）
→ setLayer(CombatEngineLayers.XXX)                              （绘制层）
→ setAdditiveBlend() / setBlendFunc(...) / setBlendEquation(...)（混合）
→ 实例数据提交链（见下）
→ setControlData(...) / setGlobalTimer(in, full, out)          （生命周期）
→ CombatRenderingManager.addEntity(entity)                      （入队，检查返回码）
```

实例数据 (`Instance2Data`，实现 `InstanceDataAPI`) 的字段与提交链：

| 方法 | 语义 |
|---|---|
| `setLocation(x, y)` / `setVelocity(x, y)` | 位置 / 速度 |
| `setFacing(float)` / `setTurnRate(float)` | 朝向 (度)/ 转向速率 (度/秒)|
| `setScale(x, y)` / `setScaleRate(x, y)` | 缩放 / 缩放速率 |
| `setTimer(in, full, out)` | 实例计时 (dynamic instance；fixed instance 用 `setFixedInstanceAlpha`，两族方法不得混用)|
| `setColor(Color)` / `setEmissiveColor(Color)` | 颜色 / 发光色 |

提交链：`entity.setInstanceData(list)` → `setInstanceDataRefreshAllFromCurrentIndex()` → `submitInstanceData()`(正式提交点)→ `setRenderingCount(n)` →(需要持续刷新时)`setAlwaysRefreshInstanceData(true)`。

**把 `addInstanceData(...)` 成功当成 GPU 已上传会漏掉 refresh、submit 和 rendering count**—— 实例字段只写客户端状态，提交链才是数据真正到达 GPU 的路径。

## 字体：FontDataManager 与 TextFieldEntity

### .fnt 字体数据

BoxUtil 用 AngelCode 位图字体：`.fnt` 文本描述 + 同目录 `_0.png` 贴图。

原版字体在 `Starsector\starsector-core\graphics\fonts\`，例如 `insignia15LTaa.fnt`(`info` 行 `size=15`、`unicode=1`、`aa=4`；`page` 行指向 `insignia15LTaa_0.png`)。

BoxUtil 发行自己的 `settings.json` 也注册同一字体 (`BUtil_IS` → `graphics/fonts/insignia15LTaa_0.png`)。本节示例直接用原版路径，不新增资产。

### 加载链与文本实体

```text
FontDataManager.tryFont("graphics/fonts/insignia15LTaa.fnt")
→ FontMapData（isValid() / getFontSize() / getName()）
→ TextFieldEntity（setFieldSize / addText / setGlobalColor）
→ setTextDataRefreshIndex / setTextDataRefreshAllFromCurrentIndex + submitText()   （提交点）
→ setLocation + setFacingScale + directDraw()                                       （渲染回调内绘制）
```

要点 (成熟实例写法)：

- `FontDataManager.tryFont(path)` 解析 `.fnt` 并登记字体表；返回的 `FontMapData` 用 `isValid()` 核对，失败必须判空处理；
- 文本程序可用性：`ShaderCore.isInitialized()` 与 `ShaderCore.getTextProgram()`(`BaseShaderData`，`isValid()`)—— 文本实体创建前先核对，不可用时禁用文本而不是创建无效实体；
- `TextFieldEntity` 构造后可 `setFieldSize(w, h)`、`addText(text, size, color, ...)`、`setGlobalColor(color)`；
- **文本修改后必须重新提交**：改 `TextData.text` → `setTextDataRefreshIndex(0)` + `setTextDataRefreshAllFromCurrentIndex()` → `submitText()`。
- `submitText()` 返回非 `BoxEnum.STATE_SUCCESS` 时置 `setShouldRenderingCharCount(0)`("把 node 或 text 数据修改后直接绘制，会漏掉 submitNodes() 或 submitText()")。
- 字号缩放：`fontScale = 目标字号 / |FontMapData.getFontSize()|`，绘制时 `setFacingScale(0f, fontScale, fontScale)`。

::: warning 字号缩放别漏绝对值与判零

字体数据的字号可能为负，直接相除会得到负缩放；除零也要先判 `|FontMapData.getFontSize()| > 0` 再算。可观察结果：字号与预期不符，甚至出现反向缩放的观感。示例的 `getFontScale()` 就是这个做法：先判绝对值大于 0，再取 `目标字号 / |字号|`。

:::

## 绘制：渲染队列、图层与混合

### 入队与返回码

`CombatRenderingManager.addEntity(RenderDataAPI)`(另有带实体类型的重载) 返回 `byte`。

返回语义为 `BoxEnum` 的 `STATE_SUCCESS` / `STATE_FAILED` / `STATE_FAILED_OTHER`("STATE_SUCCESS、STATE_FAILED 和 STATE_FAILED_OTHER 是 manager 与 submit 类入口的正式返回语义")。

**返回非 `STATE_SUCCESS` 时本地对象必须 `delete()`**—— 忽略 `STATE_FAILED` 会让本地对象继续按已入队状态推进。`containsEntity(layer, entity)` 可查入队状态。

### 图层与混合

实体通过 `setLayer(CombatEngineLayers)` 声明绘制层，层语义与战斗渲染章节的 `CombatEngineLayers` 层序一致 (例如 `ABOVE_PARTICLES_LOWER` 属于顶层三图层，在全部粒子系统之后渲染)。

混合：`setAdditiveBlend()` / `setNormalBlend()` / `setNegativeBlend()` / `setDisableBlend()` / `setBlendFunc(srgb, dstrgb)` / `setBlendEquation(...)`(成熟实例做法)。

### directDraw 的时机约束

`TextFieldEntity.directDraw()` 与 misc 立即绘制对象 (`ArcObject` 等)**只能在渲染回调内调用**(`renderInUICoords` / `renderInWorldCoords` 等)。

这是 "GL 原语只在渲染阶段" 纪律在 BoxUtil 侧的同一形态；`directDraw` 后的 `delete()` 或 cleanup 由调用方负责。

::: caution 禁止在渲染回调之外调用 directDraw()

`directDraw()` 与 misc 立即绘制对象只能在渲染回调内使用。可观察结果：GL 命令在渲染阶段之外执行 —— 内容不显示、错帧或污染后续绘制。调用方对 `directDraw` 之后的生命周期 (`delete()` 或 cleanup) 负责。

:::

## 生命周期：control data、计时与删除

### ControlDataAPI 回调

`ControlDataAPI`(`org.boxutil.base.api`) 是实体的控制器回调接口，`BaseControlData` 提供全部方法的空实现基类。

`SimpleParticleControlData` 是内置的单实体粒子池控制 (`maxParticles` / `maxDur` 是池容量与保留时间上限，`addParticle(...)` / `clearParticles()` / `forceDelete()`)。

`setControlData(...)` 会立即执行 `controlInit(...)`。

| 回调 | 时机 |
|---|---|
| `controlInit(RenderDataAPI)` | `setControlData` 时立即调用 |
| `controlBeforeRenderingAdvance` / `controlAfterRenderingAdvance` | 渲染前后 |
| `controlAdvance(RenderDataAPI, amount)` | BoxUtil 逻辑线程推进 |
| `controlRemove(RenderDataAPI)` | 实体删除或 timer out 时 |

### 全局计时与状态集

实体有独立的全局计时：`setGlobalTimer(in, full, out)`(秒)，`getGlobalTimerState()` 返回 `BoxEnum` 状态。

状态集为 `TIMER_IN` / `TIMER_FULL` / `TIMER_OUT` / `TIMER_ONCE` / `TIMER_INVALID`；`isGlobalTimerOver()` / `isGlobalTimerOnce()` 可查终态。

实体在计时走完 (timer out) 后由 BoxUtil 回收并调用 `controlRemove`—— 这是 "实体自动结束" 的标准路径。

### 删除与失效

- `RenderDataAPI.delete()` 后 `hasDelete()` 为 true，实体退出 active render object 语义；**delete() 后的实体不得继续作为 active 对象使用**；
- 跟随目标的实体在目标失效时主动结束：成熟实例 `EntityBasedControlData` 在 `controlAdvance` 里每帧把位置/朝向同步到目标实体。
- 目标失效 (`!isEntityInPlay` / `!isAlive()`) 时把全局计时压到 1 以内 (`timer[0] = Math.min(timer[0], 1)`)，让实体尽快走到终点；
- 跨战斗：文本实体与引擎引用每场战斗重新创建，旧实体 `hasDelete()` 为 true 后不再绘制。

::: warning 把上一场战斗的文本实体留给下一场是常见坑

文本实体与引擎引用每场战斗重新创建。继续使用上一场的旧文本实体时，其 `hasDelete()` 已为 true，绘制被守卫跳过 —— 画面表现为 "文本消失且无报错"。已删除的实体不得继续作为 active 对象使用。

:::

## 规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 引用 `org.boxutil.**` 前在 `mod_info.json` 的 `dependencies` 声明 `BoxUtil`(继承硬依赖规则)| 用户缺依赖时启动报 `NoClassDefFoundError`(加载直接失败，不会静默忽略)|
| 2 | **必须** | 初始化两段式：`initPre()` 在 `onApplicationLoad` 调用；`initLater()` 在 OpenGL context 创建后调用一次 (成熟实例在常驻渲染插件 `init`；本节示例用 `isGlobalInitialized()` 守卫)| `initLater()` 未调用或初始化失败 → `ShaderCore.isValid()` 为 false → 可用性判据不成立，所有实体入口走降级，画面没有任何 BoxUtil 输出 |
| 3 | **必须** | 可用性判据 = `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`；不得用 `isBaseGL43Supported()` 代替后者 | GL 能力探测通过但 program / FBO / VAO 初始化失败时，判据误判为可用 —— 实体照常入队但画面无渲染，且无报错 |
| 4 | **必须** | `BoxConfigs.isShaderEnable()` 为 false 时，BoxUtil shader 实体不能作为唯一视觉输出，必须提供降级分支 | 用户在 BoxUtil 配置界面关闭 shader 后，所有视觉效果全空 |
| 5 | **必须** | 实例数据必须走完整提交链：`setInstanceData` → `setInstanceDataRefreshAllFromCurrentIndex` → `submitInstanceData` → `setRenderingCount` | `addInstanceData` 成功 ≠ GPU 已上传：实体不显示或数量/状态与预期不符，日志无任何报错 |
| 6 | **必须** | `CombatRenderingManager.addEntity` 返回码必须检查 (`== BoxEnum.STATE_SUCCESS`)，失败显式 `delete()` | 忽略 `STATE_FAILED` → 本地对象继续按已入队状态推进 (计时/控制数据照跑)，画面却没有实体；重复入队 → 双重绘制 |
| 7 | **必须** | 文本 / 节点修改后必须 `submitText()` / `submitNodes()` 重新提交 | 改了 `TextData.text` 或节点后直接绘制 → 画面停留在旧内容 |
| 8 | **必须** | `directDraw()` 与 misc 立即绘制对象只能在渲染回调内调用 (`renderInUICoords` / `renderInWorldCoords` 等)| GL 命令在渲染阶段之外执行 → 内容不显示、错帧或污染后续绘制 (GL 原语纪律同构)|
| 9 | **必须** | `delete()` 后的实体不得继续作为 active 对象使用，绘制与推进前判 `hasDelete()` | 绕过 `hasDelete()` 与 control remove 语义 → 已回收实体被继续引用，行为未定义 |
| 10 | **必须** | `Instance2Data.setTimer` 只用于 dynamic instance；`setFixedInstanceAlpha` 只用于 fixed instance | 两族方法混用 → 计时 / 透明度语义错位 |
| 11 | 建议 | `controlAdvance` 由 BoxUtil 逻辑线程调用：回调内只做同步与判定，不创建 / 入队 / 删除实体；实体写侧 (创建、`addEntity`) 只在主线程 | 竞态与时序错乱 (无稳定复现；成熟实例只在回调里读舰船状态做同步)|
| 12 | 建议 | 字体与可用性在 `init` 缓存一次，不在每帧 `tryFont` / 重复判空 | 无功能错误，只有重复解析与日志噪音 |
| 13 | 建议 | 文本字号用 `fontScale = 目标字号 / |FontMapData.getFontSize()|` 缩放 | 字号与预期不符 |

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 依赖声明 | `mod_info.json` 的 `dependencies` 数组 | 声明 `BoxUtil`(C12-S01 硬依赖语义；缺依赖 → `NoClassDefFoundError`)|
| 初始化 | `org.boxutil.BoxUtilModPlugin.initPre()` / `initLater()` / `isPreInitialized()` / `isGlobalInitialized()` | `onApplicationLoad` 调 `initPre`；OpenGL context 创建后调 `initLater`(成熟实例在常驻渲染插件 `init`)|
| 可用性 | `org.boxutil.config.BoxConfigs.isShaderEnable()` / `isBaseGL43Supported()`；`org.boxutil.manager.ShaderCore.isValid()` / `isInitialized()` | 共同决定 shader 渲染入口可用性；`isBaseGL43Supported` 只是 GL 能力探测 |
| 实体创建 | `org.boxutil.units.standard.entity.SpriteEntity`(`FlareEntity` / `CurveEntity` / `TrailEntity` / `SegmentEntity` / `DistortionEntity` / `CommonEntity` / `TextFieldEntity`)| 实现 `RenderDataAPI`；设置后入队 |
| 实例数据 | `Instance2Data` / `InstanceDataAPI`(`setInstanceData` / `setInstanceDataRefreshAllFromCurrentIndex` / `submitInstanceData` / `setRenderingCount` / `setAlwaysRefreshInstanceData`)| refresh + submit + count 三连才是提交点 |
| 入队 | `org.boxutil.manager.CombatRenderingManager.addEntity` / `containsEntity` | 返回 `byte`(`BoxEnum.STATE_*`)；队列由 BoxUtil 注册的 `BUtil_CombatEFS` 后端消费 |
| 字体 | `org.boxutil.manager.FontDataManager.tryFont` → `FontMapData`(`isValid` / `getFontSize` / `getName`)| `.fnt` 解析；字体数据来源 `starsector-core\graphics\fonts\*.fnt` |
| 文本 | `TextFieldEntity`(`addText` / `submitText` / `directDraw`)、`org.boxutil.util.TransformUtil.createWindowOrthoMatrix` | `submitText` 是提交点；`directDraw` 在渲染回调内绘制 |
| 图层与混合 | `setLayer(CombatEngineLayers)`、`setAdditiveBlend` / `setBlendFunc` / `setBlendEquation` | 层序与混合由 BoxUtil 后端按层提交 |
| 生命周期 | `BaseControlData` / `ControlDataAPI`(`controlInit` / `controlBeforeRenderingAdvance` / `controlAfterRenderingAdvance` / `controlAdvance` / `controlRemove`)、`RenderDataAPI`(`setGlobalTimer` / `getGlobalTimerState` / `hasDelete` / `delete`)、`SimpleParticleControlData` | 创建 (`setControlData` → `controlInit`)、推进 (逻辑线程 `controlAdvance` + 全局计时)、清理 (timer out → `controlRemove`；显式 `delete`)|
| 状态集 | `org.boxutil.define.BoxEnum`(`STATE_SUCCESS` / `STATE_FAILED` / `STATE_FAILED_OTHER`；`TIMER_IN` / `TIMER_FULL` / `TIMER_OUT` / `TIMER_ONCE` / `TIMER_INVALID`)| manager 与 submit 类入口的返回语义、计时状态集 |
| 后端接入 | BoxUtil `data\config\settings.json` 的 `plugins` 段 (`BUtil_CombatEFS`)| 每场战斗实例化的战斗插件 (C07-S01 模型)，消费 `CombatRenderingManager` 队列 |
| 配置持久化 | `<saves>\common\BUtil_Configs.json.data` | BoxUtil 配置落盘 (C12-S02 同目录实证)；`BoxConfigGUI` 配置界面写入 |

## 完整最小例

在既有 MOD 布局上新增四个类文件 (不新增任何资源、不新增注册项；贴图与字体复用原版既有路径)。

四个类同在 `data.scripts.gearin.lib` 包内协作，位于 `src/data/scripts/gearin/lib/`(相对 MOD 根目录)。每个类只承担一个角色：接入、实体生成、字体文本、跟随控制数据。

`mod_info.json` 的 `dependencies` 追加一项 (硬依赖格式；`id` 逐字 `BoxUtil`)：

```json
    {
      "id": "BoxUtil",
      "name": "BoxUtil"
    }
```

### GearInBoxEntity：接入入口类

初始化与调度中枢 (薄入口类，只做接入)：`init` 里以 `isGlobalInitialized()` 守卫调用 `initLater()`(成熟实例在常驻渲染插件 `init` 里直接调用)。

`boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(规则 3)。

`advance` 维护生成计时并加暂停守卫，每 2 秒委托一次生成；`renderInUICoords` 委托屏幕文本绘制。初始化日志输出四元组，供对照核对。

```java
package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例的接线载体：BoxUtil 初始化（initLater 守卫）、可用性判据与生成
 * 计时在这里，实体生成与字体文本分别委托给 GearInBoxSpawner / GearInTextField；
 * 由既有战斗插件在 init() 中 addPlugin 注册（C07-S01 模型），只在本场战斗内有效。
 */
public class GearInBoxEntity extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInBoxEntity.class);

    /** 生成间隔（秒）。 */
    private static final float SPAWN_INTERVAL = 2f;

    private CombatEngineAPI engine;

    /** 初始化可用性：配置开关 && GPU 资源有效，二者共同决定 shader 渲染入口可用性。 */
    private boolean boxAvailable;

    private GearInTextField textField;
    private GearInBoxSpawner spawner;
    private float spawnTimer;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        // initLater 在 OpenGL context 创建后调用；isGlobalInitialized() 守卫保证只初始化一次
        if (!BoxUtilModPlugin.isGlobalInitialized()) {
            BoxUtilModPlugin.initLater();
        }
        boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid();
        textField = new GearInTextField();
        spawner = new GearInBoxSpawner(engine, boxAvailable, textField);
        LOG.info("GearIn box init: shaderEnable=" + BoxConfigs.isShaderEnable()
                + " shaderCoreValid=" + ShaderCore.isValid()
                + " available=" + boxAvailable
                + " font=" + textField.getFontInfo());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（C02-S01 同款姿势）
        }
        if (engine.isPaused()) {
            return; // 暂停守卫：实体生成只在非暂停推进
        }
        spawnTimer += amount;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            spawner.spawnBox();
        }
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        if (textField == null) {
            return; // 防御性判空（init 未执行时不绘制）
        }
        // 屏幕空间文本的 GL 命令只能在渲染回调里发出（directDraw 责任在调用方）
        textField.drawAt(20f, Global.getSettings().getScreenHeightPixels() - 40f);
    }
}

```

### GearInBoxSpawner：实体生成与实例数据提交

方框实体生成 (Entity / 实例数据工具族)：按 "创建 → 材质/层/混合 → 实例提交链 → 控制数据/计时 → 入队" 顺序执行。

实例提交链为 `setInstanceData` → `setInstanceDataRefreshAllFromCurrentIndex` → `submitInstanceData` → `setRenderingCount(1)` → `setAlwaysRefreshInstanceData(true)`(规则 5)。

`addEntity` 返回码不等于 `STATE_SUCCESS` 时 `delete()`(规则 6)；BoxUtil 不可用时回退原版粒子 (规则 4 的降级分支)。生成日志输出 `hull` 与 `layer`。

```java
package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 方框实体生成（BoxUtil Entity / 实例数据工具族）：SpriteEntity + Instance2Data，
 * 写入实例字段只是客户端状态，必须 setInstanceDataRefreshAllFromCurrentIndex() +
 * submitInstanceData() + setRenderingCount() 完成提交（addInstanceData 成功
 * 不等于 GPU 已上传）；addEntity 返回非 STATE_SUCCESS 时显式 delete()；
 * BoxUtil 不可用时回退原版粒子（降级分支）。
 */
public class GearInBoxSpawner {

    private static final Logger LOG = Global.getLogger(GearInBoxSpawner.class);
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";
    private static final float BOX_IN = 0.2f;
    private static final float BOX_FULL = 1.5f;
    private static final float BOX_OUT = 0.5f;

    private final CombatEngineAPI engine;
    private final boolean boxAvailable;
    private final GearInTextField textField;
    private int boxCount;

    public GearInBoxSpawner(CombatEngineAPI engine, boolean boxAvailable, GearInTextField textField) {
        this.engine = engine;
        this.boxAvailable = boxAvailable;
        this.textField = textField;
    }

    public void spawnBox() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!boxAvailable) {
            // 降级分支：shader 渲染入口不可用时，BoxUtil 实体不能作为唯一视觉输出
            engine.addSmoothParticle(player.getLocation(), player.getVelocity(),
                    24f, 0.6f, BOX_IN + BOX_FULL + BOX_OUT, Color.CYAN);
            LOG.info("GearIn box: box unavailable, vanilla fallback particle");
            return;
        }
        SpriteAPI sprite = Global.getSettings().getSprite(SPRITE_PATH);
        SpriteEntity box = new SpriteEntity(sprite);
        box.getMaterialData().setColor(Color.CYAN);
        box.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        box.setAdditiveBlend();
        // 实例数据提交链：写字段只是客户端状态，refresh + submit + count 才是提交点
        Instance2Data data = new Instance2Data();
        data.setFacing(player.getFacing());
        data.setTurnRate(90f);
        data.setScale(1f, 1f);
        data.setScaleRate(0.5f, 0.5f);
        data.setTimer(BOX_IN, BOX_FULL, BOX_OUT);
        List<InstanceDataAPI> instances = new ArrayList<>();
        instances.add(data);
        box.setInstanceData(instances);
        box.setInstanceDataRefreshAllFromCurrentIndex();
        box.submitInstanceData();
        box.setRenderingCount(instances.size());
        box.setAlwaysRefreshInstanceData(true);
        // 生命周期：跟随控制数据每帧同步位置/朝向，全局计时到期后实体退出渲染
        box.setControlData(new GearInFollowControlData(player));
        box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);
        byte result = CombatRenderingManager.addEntity(box);
        if (result != BoxEnum.STATE_SUCCESS) {
            box.delete(); // 入队失败：显式删除，不能当已入队继续推进
            LOG.warn("GearIn box: addEntity failed result=" + result);
            return;
        }
        boxCount++;
        textField.setText("box #" + boxCount);
        LOG.info("GearIn box: #" + boxCount
                + " hull=" + player.getHullSpec().getHullName()
                + " layer=" + box.getCombatLayer());
    }
}

```

### GearInTextField：字体与屏幕文本

字体与绘制工具族：`FontDataManager.tryFont` 加载 `.fnt` 并判 `isValid()`(失败 warn，不抛)。

文本实体创建前先核对 `ShaderCore.isInitialized()` 与 `getTextProgram().isValid()`，任一环节失败都禁用文本 (规则 4 的文本侧降级)。

`drawAt` 以窗口正交矩阵 + `directDraw()` 在渲染回调内绘制屏幕文本 (规则 8)。

`setText` 改 `TextData.text` 后必须 refresh + `submitText()`(规则 7，返回非 `STATE_SUCCESS` 时置 `setShouldRenderingCharCount(0)`)。

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.boxutil.base.BaseShaderData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.FontDataManager;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.attribute.FontMapData;
import org.boxutil.units.standard.entity.TextFieldEntity;
import org.boxutil.util.TransformUtil;

import com.fs.starfarer.api.Global;

/**
 * 字体与屏幕文本（BoxUtil 字体/绘制工具族）：FontDataManager.tryFont 解析 .fnt
 * 得到 FontMapData，TextFieldEntity 以窗口正交矩阵 + directDraw() 在渲染回调里
 * 绘制屏幕文本；文本修改后必须 setTextDataRefresh* + submitText() 才会上传。
 */
public class GearInTextField {

    private static final Logger LOG = Global.getLogger(GearInTextField.class);
    private static final String FONT_PATH = "graphics/fonts/insignia15LTaa.fnt";
    private static final float TEXT_SIZE = 12f;

    private FontMapData font;
    private TextFieldEntity textEntity;

    /** 创建时加载字体并创建文本实体；任一环节失败则文本功能禁用（内部判空）。 */
    public GearInTextField() {
        initFont();
        initTextEntity();
    }

    /** 字体信息（供 init 日志）：未加载返回 "null"，否则 "路径/字号"。 */
    public String getFontInfo() {
        return font == null ? "null" : FONT_PATH + "/" + font.getFontSize();
    }

    /** 更新屏幕文本：改 TextData.text 后必须 refresh + submitText() 才会上传。 */
    public void setText(String text) {
        if (textEntity == null) {
            return;
        }
        TextFieldEntity.TextData message = textEntity.getTextDataList().get(0);
        if (text.equals(message.text)) {
            return;
        }
        message.text = text;
        textEntity.setTextDataRefreshIndex(0);
        textEntity.setTextDataRefreshAllFromCurrentIndex();
        byte result = textEntity.submitText();
        if (result != BoxEnum.STATE_SUCCESS) {
            textEntity.setShouldRenderingCharCount(0);
        }
    }

    /** 屏幕空间绘制：只能在渲染回调（renderInUICoords）里调用（GL 命令时机）。 */
    public void drawAt(float x, float y) {
        if (textEntity == null || textEntity.hasDelete()) {
            return;
        }
        textEntity.setLocation(x, y);
        textEntity.setFacingScale(0f, getFontScale(), getFontScale());
        textEntity.directDraw();
    }

    /** 字体数据加载：FontDataManager.tryFont 解析 .fnt 并登记到字体表。 */
    private void initFont() {
        font = FontDataManager.tryFont(FONT_PATH);
        if (font == null || !font.isValid()) {
            LOG.warn("GearIn box: font load failed: " + FONT_PATH);
        }
    }

    /** 文本实体创建：字体或文本程序不可用时禁用文本并写 warn。 */
    private void initTextEntity() {
        if (font == null) {
            return;
        }
        if (!ShaderCore.isInitialized()) {
            LOG.warn("GearIn box: shader core not initialized, text disabled");
            return;
        }
        BaseShaderData program = ShaderCore.getTextProgram();
        if (program == null || !program.isValid()) {
            LOG.warn("GearIn box: text program invalid, text disabled");
            return;
        }
        textEntity = new TextFieldEntity(font, true);
        if (!textEntity.isValid()) {
            textEntity.delete();
            textEntity = null;
            LOG.warn("GearIn box: text field entity invalid, text disabled");
            return;
        }
        textEntity.setFieldSize(1024f, 128f);
        textEntity.addText("", TEXT_SIZE, Color.WHITE, false, false, false, false, 0);
        textEntity.setMappingModeSubmitData(false);
        textEntity.setCustomPrimeMatrix();
        textEntity.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
        textEntity.setGlobalColor(Color.WHITE);
    }

    private float getFontScale() {
        return Math.abs(font.getFontSize()) > 0f ? TEXT_SIZE / Math.abs(font.getFontSize()) : 1f;
    }
}

```

### GearInFollowControlData：跟随控制数据

生命周期控制数据 (ControlData 工具族)：`controlAdvance` 每帧把位置/朝向同步到被追踪舰船，舰船失效 (死亡/出局) 时把全局计时压到 1 以内，让实体尽快退出渲染；`controlAdvance` 由 BoxUtil 逻辑线程调用，回调内只做同步与判定 (规则 11)。

```java
package data.scripts.gearin.lib;

import org.boxutil.base.BaseControlData;
import org.boxutil.base.api.RenderDataAPI;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 生命周期控制数据（BoxUtil ControlData 工具族）：把实体钉在被追踪舰船上
 * （位置/朝向每帧同步），舰船失效（死亡/出局）时把全局计时压到 1 以内，
 * 让实体尽快退出渲染；controlAdvance 由 BoxUtil 的逻辑线程调用，
 * 回调内只做同步与判定，不创建/入队/删除实体。
 */
public class GearInFollowControlData extends BaseControlData {

    private final ShipAPI ship;

    public GearInFollowControlData(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void controlAdvance(RenderDataAPI renderEntity, float amount) {
        if (ship == null) {
            return;
        }
        renderEntity.setLocation(ship.getLocation());
        renderEntity.setFacingScale(ship.getFacing(), 1f, 1f);
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isEntityInPlay(ship) || !ship.isAlive()) {
            float[] timer = renderEntity.getGlobalTimer();
            timer[0] = Math.min(timer[0], 1);
        }
    }
}

```

### 协作关系

`GearInBoxEntity` 是注册与调度中枢：`init` 先完成初始化与可用性判定，再创建 `GearInTextField` 与 `GearInBoxSpawner`(把引擎、可用性与文本实体注入生成器)。

`advance` 每 2 秒触发一次 `spawnBox`；`spawnBox` 创建并提交方框实体、挂上 `GearInFollowControlData`，入队成功后让文本实体显示 `box #N`。

`renderInUICoords` 委托 `drawAt` 完成屏幕文本的 GL 绘制。`GearInBoxSpawner` 与 `GearInTextField` 只被入口类引用，`GearInFollowControlData` 只被生成器引用 —— 四个类合起来就是完整示例的全部行为。

日志前缀 `GearIn box ...` 不是 strings key，只进 `starsector.log`。

### 接入片段 (工程片段 —— 本节不改动所有者文件)

```java
    // 接入 1：在 GearInModPlugin.onApplicationLoad() 内追加一行（BoxUtil 预初始化）
    @Override
    public void onApplicationLoad() {
        BoxUtilModPlugin.initPre(); // BoxUtil 预初始化（OpenGL context 创建前，成熟实例同款）
        // ... 既有回调内容 ...
    }
```

```java
    // 接入 2：在测试战役 MissionDefinition.defineMission 中（或既有战斗插件
    // init() 里，战斗插件模型）追加：
    engine.addPlugin(new GearInBoxEntity());
```

`GearInModPlugin` 与 `MissionDefinitionAPI` 分别由 MOD 生命周期与测试战役页面所有，本节只消费这两个注册点；fixture 例不含以上接入片段 (fixture 只含本节所有的四个类)。

### 构建

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含四个类的 `.class` 条目 (`data/scripts/gearin/lib/` 下，可用 `jar tf` 核对)。

本节四个代码块与磁盘 fixture 逐字一致 (fixture 位于 `tutorial-fixtures/units/C12-S06/src/main/java/data/scripts/gearin/lib/`)。

`dependencies` 的 `id` 与 BoxUtil 发行 `mod_info.json` 的 `id` 逐字一致；fixture 编译验收命令见 "预期结果"。

### 验证方案 (基于本例)

- 前置数据：C06-S01 的 MOD 布局 + C12-S01 的 `dependencies`(追加 `BoxUtil` 项)+ 本节四个类 + 两处接入片段。
  - 接入片段：`GearInModPlugin.onApplicationLoad` 一行 `initPre`、战斗插件 `init` 一行 `addPlugin`。
  - C03-S01 的 `GearIn_test` 测试战役；本机已安装并勾选 BoxUtil 1.5.4。
- 操作 1(fixture 编译)：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC12s06Java --no-daemon`，确认 `BUILD SUCCESSFUL`(退出码 0)。
- 操作 2(游戏构建)：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含四个类的 `.class` 条目。
- 操作 3(战役冒烟)：进入 `GearIn_test` 一场战斗，`starsector.log` 核对一行。
  - `GearIn box init: shaderEnable=true shaderCoreValid=true available=true font=graphics/fonts/insignia15LTaa.fnt/15`
  - 没有 `gear_in` 引起的 `ERROR`。
- 操作 4(画面)：观察每 2 秒玩家舰位置出现青色方框：跟随移动、旋转放大、约 2.2 秒淡出；`GearIn box: #N hull=<船体名> layer=ABOVE_PARTICLES_LOWER` 日志同步递增。
- 操作 5(文本)：确认屏幕顶部靠左白色文本 `box #1` → `box #2` → `box #3`...递增；记录文本的实际屏幕位置与字号是否符合预期 (以观察记录为准)。
- 操作 6(暂停)：按空格暂停约 10 秒 —— 无新方框与新日志；已存在方框静止、文本不变；恢复后继续。
- 操作 7(失效)：击毁玩家舰 —— 方框停止生成，已存在方框快速淡出。
- 操作 8(降级注入)：在 BoxUtil 配置界面 (`BoxConfigGUI`，BoxUtil 发行注册) 关闭 shader 渲染相关选项 (或直接编辑 `<saves>\common\BUtil_Configs.json.data` 对应项) 后重启进战斗。
  - `init` 日志 `shaderEnable=false`、`available=false`，画面只有青色原版平滑粒子、无方框无文本。
  - 日志每 2 秒一行 `box unavailable, vanilla fallback particle`；改回后恢复。
- 操作 9(漏提交注入)：注释 `box.submitInstanceData();` 后重编译进战斗 ——`GearIn box: #N` 日志照常、方框不出现；恢复后确认方框回来。
- 操作 10(漏刷新注入)：注释 `GearInTextField.setText` 里的 `textEntity.submitText();` 后进战斗 —— 文本停留在 `box #1`；恢复后确认递增。
- 操作 11(跨战斗)：退出战斗再进一场 ——`GearIn box init:` 重新出现，计数从 `#1` 重新开始。
- 操作 12(辅助)：在启动器勾选 BoxUtil 自带的 `BoxUtil Test` 测试战役并进入，确认 BoxUtil 后端自身渲染正常 (区分 "BoxUtil 能力问题" 与 "示例代码问题")。
- 通过信号：操作 1–7、11、12 全部符合；操作 8–10 的注入与恢复都符合预期。
- 清理：取消勾选测试 MOD；恢复代码与 BoxUtil 配置为原值。

## 预期结果

1. **fixture 编译 (验收 J)**：在 Git Bash 执行。
```text
gradle -p tutorial-fixtures compileC12s06Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/lib/` 下四个类的条目 (`GearInBoxEntity`、`GearInBoxSpawner`、`GearInTextField`、`GearInFollowControlData`)。
3. **战斗初始化**：进入前置知识提到的 `GearIn_test` 战役一场战斗，`starsector.log` 出现：
```text
GearIn box init: shaderEnable=true shaderCoreValid=true available=true font=graphics/fonts/insignia15LTaa.fnt/15
```
`fontSize=15` 与 `insignia15LTaa.fnt` 的 `info` 行 `size=15` 一致；没有 `gear_in` 引起的 `ERROR`。若 BoxUtil 的 `BUtil_CombatEFS` 已按 settings plugins 注册 (BoxUtil 发行自带)，战斗中同时能看到 BoxUtil 后端正常工作的其他迹象 (如 BoxUtil 自带测试战役可用)。
4. **周期方框**：战斗 (未暂停) 中每 2 秒在玩家舰位置出现一个青色方框 (`ABOVE_PARTICLES_LOWER` 层，粒子系统之上)，跟随玩家舰移动、缓慢旋转并放大 (实例数据 facing/turnRate/scaleRate 驱动)，约 2.2 秒 (0.2 in + 1.5 full + 0.5 out) 内淡出消失；`starsector.log` 同步出现：
```text
GearIn box: #1 hull=Wolf layer=ABOVE_PARTICLES_LOWER
```
`#N` 递增、`hull` 为玩家舰船体名 (示例用 `GearIn_test` 战役的玩家舰，如 `Wolf`)。
5. **屏幕文本**：战斗画面顶部靠左出现 `box #1`，之后随每次生成递增为 `box #2`、`box #3`...文本为白色屏幕空间文字 (窗口正交矩阵 + `directDraw`，不受镜头缩放影响)；文本刷新与方框生成同步 (规则 7 的正面)。
6. **暂停行为**：按空格暂停 —— 没有新的 `GearIn box:` 生成日志与方框 (`advance` 暂停守卫)；屏幕上已存在的方框静止 (渲染不暂停)，文本保持最后内容；恢复后继续生成。
7. **舰船失效**：击毁玩家舰 —— 不再生成新方框 (`spawnBox` 判 `isAlive`)；已存在的方框在下一帧起被 `GearInFollowControlData` 压计时，快速淡出消失。
8. **变更验证 (降级)**：在 BoxUtil 配置界面 (`BoxConfigGUI`，BoxUtil 发行注册) 关闭 shader 渲染相关选项 (或直接编辑 `<saves>\common\BUtil_Configs.json.data` 对应项) 后重启进战斗。
   - `init` 日志 `shaderEnable=false`、`available=false`，方框分支每 2 秒输出 `GearIn box: box unavailable, vanilla fallback particle`，画面上出现原版平滑粒子 (青色) 而非方框。
   - 屏幕文本不显示 (文本程序不可用分支)；改回后恢复。
9. **变更验证 (文本)**：把 `TEXT_SIZE` 改为 `24f` 重编译进战斗 —— 文本放大一倍 (`fontScale` 从 0.8 变 1.6)；改回后恢复。
10. **故障注入 (漏提交)**：注释 `box.submitInstanceData();` 一行后进战斗 ——`GearIn box: #N` 日志照常输出，但方框不出现 (实例数据只写了客户端字段，没有提交到 GPU；规则 5 的注入验证)；恢复后方框回来。
11. **故障注入 (漏刷新文本)**：注释 `GearInTextField.setText` 里的 `textEntity.submitText();` 一行后进战斗 —— 文本停留在 `box #1` 不再更新 (规则 7 的注入验证)；恢复后递增。
12. **跨战斗**：退出战斗再进一场 ——`GearIn box init:` 重新出现，方框与文本从 `#1` 重新开始 (每场战斗全新实例)；无 `ERROR`。

## 实例驱动故障分析

1. **日志 `available=false`，方框全部变成原版粒子**。
  - 触发：BoxUtil 配置界面关闭了 shader 选项 (配置持久化在 `<saves>\common\BUtil_Configs.json.data`)，或 `initLater()` 未接入/初始化失败。
  - 可观察：`GearIn box init:` 的 `shaderEnable=false` 或 `shaderCoreValid=false`；每 2 秒一行 `box unavailable, vanilla fallback particle`，画面没有方框，屏幕文本不显示。
  - 原因：可用性判据 (规则 3) 不成立；`ShaderCore.isValid()` 覆盖 program / FBO / VAO / 矩阵程序初始化结果。
  - 修正：检查 BoxUtil 配置开关与两段式初始化接入 (规则 2)。
2. **日志 `available=true` 但画面没有任何方框**。
  - 触发：用 `BoxConfigs.isBaseGL43Supported()` 代替 `ShaderCore.isValid()` 判可用性，而程序初始化实际失败。
  - 可观察：判据自以为是可用，实体照常入队，画面无渲染且无报错。
  - 原因：GL 能力探测 ≠ 初始化结果。
  - 修正：判据改为 `isShaderEnable() && ShaderCore.isValid()`。
3. **方框不出现，但 `GearIn box: #N` 日志与 `available=true` 都正常**。
  - 触发：实例提交链缺环 —— 最常见是漏 `submitInstanceData()` 或漏 `setRenderingCount(...)`。
  - 可观察：实体入队成功 (返回码没查或返回成功)，客户端字段写了不少，GPU 侧没有数据可画。
  - 原因：`addInstanceData` / `setInstanceData` 成功 ≠ GPU 已上传 (规则 5)。
  - 修正：按 "提交链" 逐环核对 (预期结果第 10 条就是这条的注入验证)。
4. **画面没有实体，但日志显示实体已入队、控制数据照跑**。
  - 触发：`addEntity` 返回 `STATE_FAILED` 被忽略。
  - 可观察：本地对象计时/控制数据继续推进 (对象还 "活着")，画面没有对应绘制；对同一实体再次 `addEntity` 还会双重入队。
  - 原因：返回码语义未处理 (规则 6)。
  - 修正：返回码必须 `== BoxEnum.STATE_SUCCESS`，失败显式 `delete()`。
5. **文本停留在旧内容，画面与日志不同步**。
  - 触发：改了 `TextData.text` 但没调 `submitText()`(或没先 `setTextDataRefresh*`)。
  - 可观察：`box #3` 生成日志已到 3，屏幕还显示 `box #2`。
  - 原因：文本修改只是客户端状态，`submitText()` 才是提交点 (规则 7)。
  - 修正：refresh + `submitText()`(预期结果第 11 条是注入验证)。
6. **屏幕文本消失且无报错**。
  - 触发：文本程序不可用分支 (`ShaderCore.getTextProgram()` 无效) 或 `TextFieldEntity` 无效；或跨战斗继续用上一场的 `textEntity`。
  - 可观察：`init` 日志正常、warn 行 `text program invalid` / `text field entity invalid`(或不出现)；第二场战斗起 `hasDelete()` 为 true，文本不再绘制。
  - 原因：规则 4 的文本侧降级 + 跨战斗缓存禁令 (规则 9)。
  - 修正：文本实体每场战斗在 `init` 重建，绘制前判 `hasDelete()`。
7. **在 `advance()` 里调 `textEntity.directDraw()`**。
  - 触发：把直接绘制当成普通方法调用。
  - 可观察：GL 命令在渲染阶段之外执行 —— 文本不出现、错帧或污染后续绘制。
  - 修正：`directDraw()` 只在 `renderInUICoords` / `renderInWorldCoords` 等渲染回调内调用 (规则 8)。
8. **在 `controlAdvance` 里创建/入队实体**。
  - 触发：把 `spawnBox` 的调用放进控制数据回调。
  - 可观察：竞态与时序错乱 (`controlAdvance` 由 BoxUtil 逻辑线程调用，"把 control data 或 background 回调当主线程回调，会把跨线程状态和 Starsector 主线程对象混写")。
  - 修正：实体写侧 (创建、`addEntity`、`delete`) 只在主线程 (`init` / `advance`)；control data 只做同步与判定 (规则 11)。
9. **文本位置/字号与预期不符**。
  - 触发：把世界坐标当屏幕坐标用，或漏了字体缩放。
  - 可观察：文本画在屏幕外或大小明显错误。
  - 修正：`renderInUICoords` 里用屏幕像素坐标 (`getScreenHeightPixels`)；字号用 `fontScale = 目标字号 / |getFontSize()|`(规则 13)。
10. **代码引用了 `org.boxutil.**`，启动或战斗报 `NoClassDefFoundError`**。
  - 触发：`dependencies` 没声明 `BoxUtil`(或声明了但用户没装/没启用)。
  - 可观察：启动期或首次解析相关类时 `NoClassDefFoundError`(缺依赖直接导致加载失败)。
  - 修正：`dependencies` 声明 `BoxUtil`(规则 1)。
11. **BoxUtil 自己的测试战役也异常/黑屏**。
  - 触发：显卡或驱动不支持 BoxUtil 要求的 GL 能力 (配置界面的能力检测项)。
  - 可观察：`available=false` 且 BoxUtil 测试战役渲染异常。
  - 原因：BoxUtil 是 GPU 渲染库，能力不足时无法提供 shader 渲染入口 (这是 "不能作为唯一视觉输出" 要降级的原因)。
  - 修正：关掉 shader 选项走原版降级，或按 BoxUtil 发行说明调整渲染设置。

## 总结

本节把 BoxUtil 1.5.4 的最小消费面完整走了一遍：可用性判据决定 "能不能用"，实体入队与实例提交链决定 "怎么提交"，字体与屏幕文本决定 "怎么画"，control data、全局计时与删除决定 "怎么结束"，四个示例类各管一段、由接入入口类串成一条完整链路。

- **初始化两段式**：`initPre()`(`onApplicationLoad` 接入)→ `initLater()`(OpenGL context 创建后；示例用 `isGlobalInitialized()` 守卫)。
- 可用性判据 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()` 共同决定 shader 渲染入口，`isBaseGL43Supported()` 只是能力探测。
- **实体**：`SpriteEntity` 等实现 `RenderDataAPI` 的实体，设置后经 `CombatRenderingManager.addEntity` 入队。
- 实例数据 (`Instance2Data`) 必须走 "set → refresh → `submitInstanceData` → `setRenderingCount`" 提交链；返回码 `STATE_*` 必须检查。
- **字体**：`FontDataManager.tryFont` 解析 `.fnt` → `FontMapData`；`TextFieldEntity` 以窗口正交矩阵 + `directDraw()` 在渲染回调内画屏幕文本；改文本后必须 refresh + `submitText()`。
- **绘制**：`setLayer(CombatEngineLayers)` 声明层、`setAdditiveBlend` 等设置混合；`directDraw` 与 misc 立即对象只能在渲染回调内使用。
- **生命周期**：`setControlData` → `controlInit` → 每帧 `controlAdvance`(逻辑线程，同步/判定)→计时到期或删除时 `controlRemove`。
- 全局计时 `in/full/out` 是自动结束的主路径，`delete()` / `hasDelete()` 是显式路径；跨战斗禁止缓存。

依赖有强弱之分：硬依赖缺了就不能启动，而本节的降级分支 (`boxAvailable=false` 回退原版粒子) 已经演示了 "按运行期能力走不同路径" 的形态。

把依赖声明为可选、在加载与渲染两处按可用性分支适配，是同一决策面的自然延伸：依赖的强弱声明与检测时机，决定了一个 MOD 在依赖缺席时是整体不可用，还是仅失去一部分效果。

## 建议参考项

- `org.boxutil.BoxUtilModPlugin`：两段式初始化入口与状态查询 (`initPre` / `initLater` / `isPreInitialized` / `isGlobalInitialized`)。
- `org.boxutil.config.BoxConfigs`：shader 启用开关 `isShaderEnable()` 与 GL 能力探测 `isBaseGL43Supported()`。
- `org.boxutil.manager.ShaderCore`：`isValid()` / `isInitialized()` / `getTextProgram()`。
- `org.boxutil.manager.CombatRenderingManager`：战斗渲染队列，`addEntity(...)` 返回 `STATE_*`，`containsEntity(...)` 查入队状态。
- `org.boxutil.manager.FontDataManager`：`tryFont(...)` 解析 `.fnt` 并登记字体表。
- `org.boxutil.units.standard.attribute.FontMapData`：`isValid()` / `getFontSize()` / `getName()`。
- `org.boxutil.units.standard.attribute.Instance2Data` 与 `org.boxutil.base.api.InstanceDataAPI`：实例数据字段与提交。
- `org.boxutil.units.standard.entity.SpriteEntity` / `TextFieldEntity`：渲染实体与位图字体文本实体。
- `org.boxutil.base.BaseControlData` 与 `org.boxutil.base.api.ControlDataAPI`：实体控制器回调基类与接口。
- `org.boxutil.base.api.RenderDataAPI`：`setControlData` / `setGlobalTimer` / `hasDelete` / `delete` / `setLayer` / 混合设置。
- `org.boxutil.define.BoxEnum`：`STATE_*` 返回语义与 `TIMER_*` 计时状态集。
- `org.boxutil.util.TransformUtil`：`createWindowOrthoMatrix(...)` 窗口正交矩阵。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：每帧战斗插件基类，示例接入入口类继承它。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`addSmoothParticle(...)`(降级分支)、`isEntityInPlay(...)`、`getPlayerShip()`、`isPaused()`。
- `data.scripts.gearin.lib`：本节四个教学类 (`GearInBoxEntity`、`GearInBoxSpawner`、`GearInTextField`、`GearInFollowControlData`) 所在包。
- `mod_info.json`：`dependencies` 数组声明 `BoxUtil`(硬依赖)。
- `data/config/settings.json`：BoxUtil 发行 `plugins` 段 (`BUtil_CombatEFS`) 与 `graphics.fonts` 注册。
- `Starsector/starsector-core/graphics/fonts/insignia15LTaa.fnt`：原版 `.fnt` 字体数据 (`info` 行 `size=15`)。
- `Starsector/starsector-core/graphics/fx/particlealpha32sq.png`：原版方形软粒子贴图 (示例方框贴图)。
