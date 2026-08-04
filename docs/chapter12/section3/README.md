# 使用 GraphicsLib 制作光源与材质

本节初始化 GraphicsLib，并在战斗中加入动态光源与材质贴图。示例会同时留下日志和画面结果，用来确认资源已经注册并参与渲染。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 解释 GraphicsLib 的初始化完整流程：
  - `ShaderModPlugin.onApplicationLoad` → `ShaderLib.init()`(幂等、能力检测)；
  - `areShadersAllowed()` / `areBuffersAllowed()` 双门禁；
  - `LightData` / `TextureData` CSV 注册 → `ShaderAPI` 实例注册。
  - 每场战斗 `ShaderHook` 重新实例化并挂载。
- 解释使用方 MOD 为什么**必须**在自己的 `onApplicationLoad` 里先调 `ShaderLib.init()` 再读能力门禁 (`onApplicationLoad` 按 MOD 显示名排序执行，"zz GraphicsLib" 几乎总是最后)。
- 解释不这么做时可观察的结果："自己的 CSV 静默不注册"。
- 用 `StandardLight` + `LightShader.addLight()` 在战斗内创建点光源 (point)，解释 line / cone / directional 三种其他类型与挂载、`fadeOut`、`setLifetime`、`makePermanent` 的语义；
- 解释光源的适用边界：动态光源只在战斗内有效 (战斗 `LocalData` 不存在时 `addLight` 静默跳过)、光源列表随战斗销毁、未永久化的光源 60 秒 failsafe、每帧上限 (默认 100、硬上限 372)、暂停时不推进；
- 解释 `LightData` / `TextureData` CSV 的注册与查询面：两套读入方法 (覆盖式 vs 先到先得)、行字段、key 组成 (`id + type + frame`)、`TextureEntry.magnitude`。
- 解释贴图三遍 (material / normal / surface) 如何被 `LightShader` 在战斗内消费；
- 从症状定位典型故障：启动抗锯齿中止、光源不出现且无报错、挂载光源一分钟后消失、舰船死后残光驻留、自己的 CSV 不生效、大场面光效 pop-in/out、贴图查询返回 none。

## 前置知识

本节直接使用硬依赖库的 Java 接口，并沿用战斗插件与分层渲染的既有通道，请先熟悉以下内容：

- 硬依赖声明与运行时检测 (C12-S01)：`mod_info.json` 的 `dependencies` 写法与运行时自检流程；
- 分层渲染插件通道 (C07-S06)：`engine.addLayeredRenderingPlugin(...)` 的注册方式、`ABOVE_SHIPS_AND_MISSILES_LAYER` 在层序中的位置；
- 战斗插件模型与暂停检查 (C07-S01)：`EveryFrameCombatPlugin` 的 `advance` 生命周期与 `engine.isPaused()` 的用法；
- `modPlugin` 与 `onApplicationLoad()` 时机 (C06-S02)：资源加载完成后、主菜单前调用；
- `Global` 生命周期与战斗外引擎判空 (C06-S03)：战斗外 `getCombatEngine()` 是非 null 空引擎，"战斗内" 判据以 `LocalData` 是否存在为准；
- MOD 目录与 `mod_info.json`(C01-S01)；
- GLSL 语法 (C14-S01)：着色器专题负责 shader 源码，本节只调用 GraphicsLib 的 Java 接口，不编写 GLSL。

`GearInModPlugin` 由前置的 modPlugin 主题单元持有。

## 适用边界速览 (先记住边界再读细节)

本节的所有入口都以战斗引擎为所属对象。先把两类生命周期分清，后面读初始化与光源细节时才有参照：

| 能力 | 生命周期 | 所属对象 | 说明 |
|---|---|---|---|
| `ShaderLib.init()` 与能力检测 | 会话级 (进程内一次)| `onApplicationLoad` | 幂等；多个 MOD 调用只有第一次真正执行 |
| `LightData` / `TextureData` 数据注册 | 会话级 (进程内一次)| `onApplicationLoad` | 跨战斗复用；数据表在两次战斗之间不清空 |
| 光源渲染与贴图三遍渲染 | 战斗级 (每场战斗重建)| 战斗引擎 | `ShaderHook` 每场战斗新实例化，`LocalData` 每场战斗重建 |
| 动态光源列表 | 战斗级 | 战斗引擎 customData(键 `shaderlib_LightShader`)| 随战斗销毁；跨战斗引用全部失效 (会话级对象禁止跨战斗缓存)|
| 战役画面 | 不经过本节链路 | —— | 战役引擎不实例化 settings `plugins` 的 `EveryFrameCombatPlugin`(走 `CampaignPlugin` / `ListenerUtil` 通道)，本节所有入口都以战斗引擎为所属对象 |

一句话：**注册是会话级的，渲染是战斗级的**。`addLight`、CSV 自动光源、贴图替换都只在战斗画面里发生。

## GraphicsLib 初始化 (onApplicationLoad 接入)

边界已经建立：注册是会话级的，而初始化链就是注册发生的唯一位置。先看 GraphicsLib 自己在启动阶段做了什么，再看您的 MOD 应该在哪一步插入。

### GraphicsLib 自己的初始化链

GraphicsLib 是 utility MOD(`Starsector\mods\GraphicsLib\mod_info.json`：`id` `shaderLib`、`name` `"zz GraphicsLib"`)。

其 `modPlugin` 为 `org.dark.shaders.ShaderModPlugin`、依赖 `lw_lazylib`。

它的 `ShaderModPlugin.onApplicationLoad()` 依次做：

1. **`ShaderLib.init()`**：幂等初始化 (`initialized` 标志，javadoc 原文 "many mods may call this method but only the first will actually do anything")。它依次做：
  - 读取 `GRAPHICS_OPTIONS.ini`(`enableShaders` / `toggleKey` / `reloadKey` / `use64BitBuffer` / `extraScreenClear` / `aaCompatMode`)。
  - 检测 OpenGL 2.0(`shadersAllowed`) 与 framebuffer 能力 (`buffersAllowed`，Core/ARB/EXT 三选一)。
  - 按屏幕尺寸创建内部渲染纹理与 foreground / auxiliary 两个 FBO。
  - `enableShaders:false` 时 `shadersAllowed` 与 `buffersAllowed` 同时置 false。
2. **抗锯齿检查**：`getAASamples() > 1` 且 `aaCompatMode` 未开时**抛 `RuntimeException`**——"GraphicsLib shaders are not fully compatible with antialiasing!"，给出三个选项：
  - 启动器把抗锯齿设为 Off；
  - `GRAPHICS_OPTIONS.ini` 设 `aaCompatMode:true` 做部分抗锯齿；
  - `enableShaders:false` 关闭 shader。
3. **能力双通过后注册 shader 实例并读入核心数据**：
  - `ShaderLib.addShaderAPI(new PostProcessShader())`
  - 在 `areShadersAllowed() && areBuffersAllowed()` 内读入核心数据：
    - `LightData.readLightDataCSVNoOverwrite("data/lights/core_light_data.csv")`、`TextureData.readTextureDataCSVNoOverwrite("data/lights/core_texture_data.csv")`；
    - `ShaderLib.addShaderAPI(new LightShader())`、`addShaderAPI(new DistortionShader())`。

`ShaderLib.addShaderAPI` 对同一 class 保持唯一实例 (已存在同 class 即忽略)，`getShaderAPI(Class)` 按类取回实例。

### 战斗钩子：ShaderHook 与 LocalData

`Starsector\mods\GraphicsLib\data\config\settings.json` 的 `plugins` 段注册了 `"ShaderHook": "org.dark.shaders.util.ShaderHook"`。

settings `plugins` 的注册链：加载期把每个插件类名登记进脚本类表

→ 每场战斗开始时，settings `plugins` 里的每个 `EveryFrameCombatPlugin` 脚本类都被**新实例化**并加入战斗地图

(每次加载脚本类都新建实例)。

也就是说 **`ShaderHook` 是战斗级的**：每场战斗都有全新实例，战役画面没有它。

`ShaderHook.init(engine)` 做两件事：

- 对每个 `ShaderAPI` 调 `initCombat()`。`LightShader.initCombat()` 把 `LocalData`(光源列表 + beam/projectile 跟踪表) 写入 `engine.getCustomData()`，键 `shaderlib_LightShader`；
- `engine.addLayeredRenderingPlugin(new ShaderCombatLayerHook())`：一个覆盖**全部** `CombatEngineLayers`、`getRenderRadius()` 返回 `99999999f`、永不失效的分层渲染插件 (`org.dark.shaders.util.ShaderHook`，即分层渲染插件的注册通道)。
- 它在每个层渲染时把 `isCombat() && getCombatLayer() == layer` 的 shader 的 `renderInWorldCoords(viewport)` 派发出去。

`LightShader.getCombatLayer()` 返回 `ABOVE_SHIPS_AND_MISSILES_LAYER`、`isCombat()` 返回 true—— 所以**光照绘制发生在分层渲染层序中位于粒子系统之前的那一层**；

`ShaderHook.advance` 每帧调 `shader.advance(amount, events)`，`LightShader.advance` 在非暂停时推进光源列表 (见 "光源" 节)。

键 `197`(Pause/Break) 切换 shader 开关、键 `68`(F10) 重载设置 (键位来自 `GRAPHICS_OPTIONS.ini` 的 `toggleKey` / `reloadKey`)。

### 使用方 MOD 的接入 (规则 1)

成熟实例的一致写法：

```java
ShaderLib.init();
if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
    LightData.readLightDataCSV("data/lights/<自己的CSV>.csv");
    TextureData.readTextureDataCSV("data/lights/<自己的CSV>.csv");
}
```

**为什么 "必须" 自己先调 `ShaderLib.init()`**：`onApplicationLoad` 的调用顺序由 MOD 显示名排序决定 —— `ModManagerAPI` 把全部 MOD 按 `name` 排序，`enabledMods` 按该顺序重建，

加载流程按 `getEnabledModPlugins()` 顺序逐个调 `onApplicationLoad()`。

GraphicsLib 的显示名是 `"zz GraphicsLib"`，几乎必然排在最后 —— 也就是说**本 MOD 的 `onApplicationLoad` 几乎总是先于 GraphicsLib 执行**。

此时若没有先调 `ShaderLib.init()`，`areShadersAllowed()` / `areBuffersAllowed()` 读到的是初始值 false，CSV 读取被跳过；等 GraphicsLib 初始化完成后，数据也回不来了。

**违反的可观察结果：shader 能力完全正常的机器上，自己的 light/texture CSV 静默未注册**—— 战斗里自动光源/贴图不生效，日志没有任何报错，只有 "查询返回 not registered / none" 这类痕迹。

`ShaderLib.init()` 幂等，先调无副作用，所以这是无成本的必须遵守的规则。

::: caution 先 `ShaderLib.init()` 再读能力门禁 (规则 1)
使用方 MOD 在 `onApplicationLoad` 里读取任何能力门禁或注册 `LightData` / `TextureData` 之前，**必须先调用 `ShaderLib.init()`**。

不先调用时能力判断读到初始 false，自己的 CSV 静默不注册 —— 能力全开的机器上自动光源/贴图不生效，且没有任何报错。

`ShaderLib.init()` 幂等，先调无副作用。
:::

**为什么 CSV 读取要放在能力判断内**：`LightData` / `TextureData` 的读入方法本身不检查能力 (无条件读入、行非法只打 ERROR 日志不抛出)，但数据只会在 shader 能力具备时被消费；

`TextureData` 的纹理加载还受 `GRAPHICS_OPTIONS.ini` 的 `loadMaterial` / `enableNormal` / `loadSurface` 门禁控制 (`org.dark.shaders.util.TextureData` 静态初始化块与加载门禁)。

把 CSV 读取放进能力判断是成熟实例的一致做法，语义是 "不做注定不会被消费的注册"。

### 能力门禁 API(规则 2)

| 方法 (`org.dark.shaders.util.ShaderLib`)| 语义 |
|---|---|
| `areShadersAllowed()` | OpenGL 2.0 支持 (shader 能力)；javadoc 原文 "Do not use shader code if this returns false! Otherwise the game will crash!" |
| `areBuffersAllowed()` | framebuffer object 支持；javadoc 原文 "Do not use FBOs if this returns false! Otherwise the game will crash!" |
| `getAuxiliaryBufferId()` / `getAuxiliaryBufferTexture()` | `buffersAllowed == false` 时返回 0 |
| `isAACompatMode()` | `GRAPHICS_OPTIONS.ini` 的 `aaCompatMode` |
| `getShaderAPI(Class)` / `getShaderAPIsCopy()` | 已注册 `ShaderAPI` 实例查询 |

这两条门禁是 "必须" 级：`areShadersAllowed()` 为 false 时不得使用任何 shader 程序与 shader 绘制入口，`areBuffersAllowed()` 为 false 时不得使用任何 FBO 相关入口 —— 违反的可观察结果是崩溃或空绘制 (javadoc 原话)。

本节示例不直接写 shader 程序，只消费 `LightShader` / `TextureData`，但同样的门禁语义贯穿整条链：

`LightShader` 构造器自己也会读 `GRAPHICS_OPTIONS.ini`(`enableLights` / `maximumLights` / `maximumLineLights` / `intensityScale` / `sizeScale` / `fighterBrightnessScale` / `enableBloom` / `enableNormal` / …)。

`enableLights:false` 时 `isEnabled()` 为 false，此后所有 `LightShader.addLight` 调用全部静默无效。

## 光源 (LightShader / LightAPI / StandardLight)

初始化与能力判断就绪后，进入战斗内最常用的消费面：光源。`LightShader` 既负责渲染也充当光源注册中心，先认识四种类型，再学注册与移除，最后看生命周期与上限。

### 类型速查 (LightAPI)

`org.dark.shaders.light.LightAPI` 是光源接口：
- `getLocation` / `getLocation2` / `getArcStart` / `getArcEnd` / `getDirection`
- `getSpecularIntensity` / `getSpecularMult` / `getHeight` / `getColor` / `getSize`
- `getIntensity` / `getType` / `advance`

`getType()` 定义四种类型 (接口 javadoc 原文)：

| 类型 | 取值 | 说明 |
|---|---|---|
| point(点光源)| 0(默认)| 圆形光斑，`getLocation` + `size` + `intensity` |
| line(线光源)| 1 | 两个端点 `getLocation` / `getLocation2`，主要用于光束 |
| cone(锥光源)| 2 | 可见弧 `getArcStart` / `getArcEnd`(度)|
| directional(方向光)| 3 | 3D 方向向量 `getDirection` + `getSpecularIntensity`(用于模拟太阳等)|

`org.dark.shaders.light.StandardLight` 是 "默认自动行为足够用" 的标准实现 (类 javadoc 原文)，提供全套 setter：
- `setColor(Color)` / `setColor(float,float,float)`、`setSize`、`setIntensity`、`setLifetime`、`fadeIn` / `fadeOut`、`makePermanent`。
- `setVelocity` / `setVelocity2`、`setHeight`(法线贴图用的世界高度)。
- `attachTo(CombatEntityAPI)` / `attachToBeam(BeamAPI)` / `linkToBeam(BeamAPI, boolean)`
- `setArc(start, end)`(自动规整到 0–360 度)、`setSpecularIntensity` / `setSpecularMult`。

构造器族覆盖四型 (点：location+velocity+offset+attach；线：双端点+双速度+beamAttach；锥：location+velocity+arc；方向：Vector3f 方向)。

**挂载语义**：光源挂载到实体后，`setLocation` / `setVelocity` 不再生效 (javadoc 原文 "Has no effect if the light is attached to something")。

位置每帧取自实体 (有非零 offset 时按实体朝向旋转 offset 再叠加，即 `computeOffset` 的逻辑)。

挂载是 "光源跟随舰船/武器" 的标准做法。

### 注册与移除：LightShader.addLight / removeLight

```java
LightShader.addLight(light);      // 把 LightAPI 加入当前战斗的光源列表
LightShader.removeLight(light);   // 强制移除
```

两个静态方法都是**静默**的：`addLight` 只有同时满足 "`ShaderLib.getShaderAPI(LightShader.class)` 返回已启用的 `LightShader` 实例" 且 "当前战斗引擎 customData 里的 `LocalData` 存在" 时才真正把光源写进列表。

其余情况直接返回 (null 参数同样直接返回)。

javadoc 原话："This function will do nothing if the light shader is not enabled."

由此得到规则 4：**动态光源必须在战斗内、战斗的 `LocalData` 创建之后添加**—— 即 `EveryFrameCombatPlugin` / 部署监听器 / 武器效果的推进回调里 (`ShaderHook.init` 在战斗初始化阶段就跑完了)。

::: warning "光源不出现且无任何报错" 的首选排查方向
在战役画面、主菜单或战斗初始化完成前调用 `addLight` 就是静默无效。不要用 `Global.getCombatEngine() == null` 当 "战斗内" 判据 (战斗外是非 null 空引擎)；判据是 "光源 shader 是否启用" + 调用时机。先看 `capabilities` 日志行。
:::

### 推进、生命周期与清理 (规则 5、6)

`LightShader.advance(amount, events)` 在**非暂停**时对列表逐个调 `light.advance(amount)`，返回 true 的光源被移除 (推进循环先做 `if (!engine.isPaused())` 检查)。

暂停时光源不推进 (静止但可见)，与分层渲染插件的暂停语义一致。

`StandardLight.advance` 的结束条件：type 0–2 为 `size <= 0 || intensity <= 0`；type 3 为 `intensity <= 0 && specularIntensity <= 0`。由此：

- **`fadeOut(seconds)` 是 "短命光源" 的标准做法**：正数按速率把 intensity 线性降到 0，归零后下一轮 `advance` 返回 true 被移除；`fadeOut(0)` 或负数立即置 0。
- 不要自己记秒数再 removeLight——fadeOut 一条链就走完了 (本节示例的周期闪光就是 fadeOut(0.5f) 自灭)。
- **未永久化的光源有 60 秒 failsafe**：`superLifetime` 默认 60 秒，耗尽即移除 ——javadoc 原文 "By default, lights disappear after one minute to avoid 'light leaks'"。
- 需要长期存在的光源 (如常驻的舰船灯、引擎灯)**必须 `makePermanent()`**(lifetime 与 superLifetime 均置 -1)。
- 违反的可观察结果：光源的 size/intensity 一切正常，却在约 60 秒后消失。
- **挂载在实体上的光源不会因实体死亡自动清理**：`LightShader` 只对 projectile 与 beam 维护附着关系清理，对普通 `CombatEntityAPI` 挂载没有任何生命周期跟踪。
- 实体死亡后必须自己 `fadeOut` 或 `removeLight`，否则残光留在原地 (这正是 failsafe 存在的理由)。规则 6。

::: caution 常驻必须 makePermanent，实体死亡必须自己清理 (规则 5、6)
未永久化的光源 60 秒后会被 failsafe 移除；挂载在普通实体上的光源，实体死亡后引擎不会自动清理。需要长期存在的光源**必须 `makePermanent()`**；实体死亡后**必须自己 `fadeOut` / `removeLight`**，否则残光驻留原地 ("光泄漏")。
:::

::: tip 短命光源的标准写法
要光源自灭就用 `fadeOut(秒)`：intensity 归零后由 `LightShader.advance` 自动移除，一条链走完，不需要自己记秒数再 `removeLight`。`fadeOut(0)` 或负数立即归零。
:::

**渲染与上限**：`LightShader.renderInWorldCoords` 先处理 projectile/beam 的自动光源 (见下)，再按 `LIGHTSIZE` 比较器对光源列表排序 (大光在前)。

`drawLights` 用 `ShaderLib.isOnScreen` 剔除屏外光源、按类型打包进 1D 纹理交给 shader。

每帧光源数上限 `min(maximumLights, 372)`(`GRAPHICS_OPTIONS.ini` 的 `maximumLights` 默认 100、最大 372，`maximumLineLights` 默认 50)——**超限光源被静默跳过**，大混战中光效可能 pop-in/out，属设计行为。

全局强度/大小由 `intensityScale`(默认 0.5) 与 `sizeScale`(默认 2.0) 缩放；战机来源的自动光源乘 `fighterBrightnessScale`(默认 1.0)。

### LightData CSV：数据驱动的自动光源

`org.dark.shaders.light.LightData` 维护 projectile 与 beam 两张光源数据表，供 `LightShader` 在战斗内**自动**为射弹/光束生成光源 (不需要写 addLight 代码)：

- 注册：`readLightDataCSV(path)`(**覆盖式**，后读覆盖先读) 与 `readLightDataCSVNoOverwrite(path)`(**先到先得**，重复 key 保留先读 entry)。
- 行格式 (表头即字段)：
  - `id, type, size, intensity, fadeout, color, offset, hit size, hit intensity, hit fadeout, hit color, flash size, flash intensity, flash fadeout, flash color, flash offset, chance, fighter dim`
  - `type` 只接受 `projectile` / `beam`，行缺 `id` 或 `type` 或 type 拼错 → **该行静默跳过**。
  - standard 组要求 `size > 0` 且 `intensity > 0` 且 `color` 非空 `[r,g,b]` 字符串 (0–255，越界 clamp)。
  - hit 组要求 `hit size` / `hit intensity` / `hit color` 同规则。
  - flash 组同理；
  - `color` 必须写成 `[255,225,50]` 形式。
- 查询：`LightData.getLightData(id, LightDataType.PROJECTILE / BEAM)` 返回 `LightEntry` 或 null。
  - `LightEntry` 的 public 字段：`hasStandard` / `standardSize` / `standardIntensity` / `standardColor` / `standardFadeout` / `standardOffset` / `hasHit` / `hasFlash` / `chance` / `fighterDim`。
- 消费：`LightShader.renderInWorldCoords` 按射弹/光束 id 查表自动建光：
  - 对每个新射弹按 `proj.getProjectileSpecId()`(`.proj` 文件的 id) 查表。
  - 对每个新光束按 `beam.getWeapon().getId()`(武器 id) 查表；
  - 命中且有数据时按 `chance` 随机决定是否创建 standard(附着射弹) 与 flash 光，boom 时创建 hit 光。
  - 光束数据对 standard/flash/hit 各建一盏并 `makePermanent`，随光束亮度归零清理。

### 适用边界小结 (光源)

- 动态光源 (`addLight`) 与自动光源 (`LightData`) 都**只在战斗内渲染**；战役画面没有光源。
- 光源列表是**战斗级**的 (`LocalData` 随 `initCombat` 重建)—— 禁止跨战斗持有 `LightAPI` / `StandardLight` 引用 (会话级对象禁止跨战斗缓存)。
- 暂停时光源静止不推进；战斗结束淡出等全局透明度由引擎管，光源本身随战斗销毁。
- 想要光源长期存在：`makePermanent()`；想要光源自灭：`fadeOut(秒)`；实体死了：自己 `fadeOut`。

## 纹理 (TextureData / TextureEntry)

光源决定了哪里亮，纹理三遍则决定亮起来像什么：材质、法线与表面贴图分别在 `LightShader` 同一渲染回调里被消费。先看注册与查询面，再看三遍如何发生。

`org.dark.shaders.util.TextureData` 维护 material(材质)、normal(法线)、surface(表面) 三张贴图表，供 `LightShader` 战斗内的三遍光照渲染消费，也可以由 MOD 代码查询 (本节示例的消费面)。

### 注册与查询速查

- 注册：`readTextureDataCSV(path)`(覆盖式)/ `readTextureDataCSVNoOverwrite(path)`(先到先得)。
  - 行格式：`id, type, frame, magnitude, map, path`。
  - `type` 只接受 `ship` / `turret` / `turretbarrel` / `turretunder` / 各种 `turretcover*` / `hardpoint*` / `missile` / `asteroid` 共 15 型。
  - `map` 只接受 `material` / `normal` / `surface`。
  - 行缺 `id` / `type` / `map` / `path` 之一 → 静默跳过。
  - `frame` 缺省 0(动画武器按帧注册)；`magnitude` 缺省 1.0。
- **注册键 = `id + 类型后缀 + frame`**(后缀如 `$$$ship`)—— 同一个 id 的不同 type 是互不干扰的独立条目。
- 查询：`TextureData.getTextureData(id, TextureDataType.MATERIAL_MAP / NORMAL_MAP / SURFACE_MAP, ObjectType.SHIP / TURRET / HARDPOINT / MISSILE / ASTEROID / …, frame)` 返回 `TextureEntry` 或 null。
- `TextureEntry` 只有两个 public 字段：`sprite`(`SpriteAPI`) 与 `magnitude`(贴图强度系数)。
- **加载门禁**：`enableShaders:false` → 全关；否则 `enableLights:false` → 全关；material 需 `loadMaterial`、normal 需 `enableNormal`、surface 需 `enableNormal && loadSurface`。
- 门禁未过时整类贴图 "查不到"(返回 null)**且无报错**—— 这是 "材质贴图不生效" 的首选排查方向。

::: warning 贴图查询 none 有两种原因
`loadMaterial` / `enableNormal` / `loadSurface` 门禁未开与 id 确实未注册都返回 null 且无报错。先查 `GRAPHICS_OPTIONS.ini` 的门禁状态，再确认 id 是否注册；两种原因无法从返回值区分。
:::

- 高级查询与改写：
  - `ShaderLib.getShipTexture(ship, type)` 按 hullId → D-parent hullId → base hullId 三级回退解析；`ShaderLib.getWeaponTexture(weapon, type, objType, frame)`(1.10.0 新增，含 weapon skin 解析)。
  - `ShaderLib.overrideShipTexture(ship, id)` / `overrideWeaponTexture(weapon, id)` 改写解析源 (引擎 customData 键 `SL_shipTexOvd` / `SL_wpnTexOvd`)。

### 消费面 (与分层渲染的关系)

`LightShader.renderInWorldCoords`(`ABOVE_SHIPS_AND_MISSILES_LAYER` 层，经 `ShaderCombatLayerHook` 派发) 内部跑三遍：

1. **material 遍**(`ShaderLib.renderForeground`)：把材质贴图 (存在时) 替换渲染进 foreground framebuffer。
   - 材质贴图控制反照率/高光细节，是所有 `getForegroundTexture` / `isForegroundEmpty` 使用方看到的前景内容。
2. **normal 遍**(`drawNormalMaps`)：法线贴图参与 Blinn-Phong 光照计算 (`enableNormal` 时)；
3. **surface 遍**(`drawSurfaceMaps`)：表面贴图控制高光硬度/强度。

三遍都按 "查表命中 → 用 `entry.sprite` 替换原贴图、`magnitude` 参与深度/强度" 的写法处理舰船、武器槽、导弹、小行星与 `MapObjectAPI`。

把任意键 (如 `LightShader.DO_NOT_RENDER` = `"shaderlib_do_not_render"`) 写进实体 customData → 该实体跳过 normal/surface/lighting 相关渲染 (舰船/导弹/小行星三处 `containsKey` 判断)。

**与分层渲染的关系**：三遍渲染发生在 `LightShader` 这一条 `ShaderAPI` 渲染回调内，而回调本身由 `ShaderCombatLayerHook`(`CombatLayeredRenderingPlugin`，全层注册) 按层派发 —— 不是自己写的分层渲染插件。

自己写的光源 `StandardLight` 也只是数据，绘制由 `LightShader` 完成。

换句话说，**本节的消费面不涉及直接 GL 绘制**(那是分层渲染与着色器专题的领域)，`addLight` 与 `TextureData` 查询在任何战斗推进回调里都能安全调用。

## 文件与 API 链路

上面各小节的机制分散在正文里，把整条链收进一张表，方便回查与核对：

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 依赖声明 | `mod_info.json` 的 `dependencies` 追加 `{"id":"shaderLib","name":"GraphicsLib"}` | 硬依赖自检 (C12-S01) 的启动器检查 (缺装即红色不可勾选)|
| 初始化 | `ShaderModPlugin.onApplicationLoad` → `ShaderLib.init()`(幂等)| 读 `GRAPHICS_OPTIONS.ini`、检测 OpenGL 2.0 / FBO、建内部纹理与 FBO(`org.dark.shaders.util.ShaderLib`)|
| 启动检查 | AA 检查 (`getAASamples()>1 && !aaCompatMode`)| 抛 `RuntimeException`，启动中止 (`org.dark.shaders.ShaderModPlugin`)|
| 能力门禁 | `ShaderLib.areShadersAllowed()` / `areBuffersAllowed()` | 所有 shader / FBO 入口的必须前置检查 (javadoc 明确违反会崩溃)|
| 数据注册 | `LightData.readLightDataCSV(NoOverwrite)` / `TextureData.readTextureDataCSV(NoOverwrite)` | 覆盖式 vs 先到先得；行非法静默跳过 (`org.dark.shaders.light.LightData` / `org.dark.shaders.util.TextureData`)|
| 战斗钩子 | settings `plugins` 的 `ShaderHook` → `CombatEngineAPI.setCombatMap` 每场实例化 | `initCombat()` 建 `LocalData`(键 `shaderlib_LightShader`)；`addLayeredRenderingPlugin` 注册全层钩子 (`org.dark.shaders.util.ShaderHook`)|
| 动态光源 | `LightShader.addLight(LightAPI)` / `removeLight` | 光源 shader 启用且 `LocalData` 存在才写入；否则静默 (`org.dark.shaders.light.LightShader`)|
| 光源推进 | `LightShader.advance` → 每个 `LightAPI.advance` | 非暂停推进；返回 true 即移除；failsafe 60 秒 (`org.dark.shaders.light.StandardLight`)|
| 自动光源 | `LightData` 表 → `renderInWorldCoords` | 射弹按 `.proj` id、光束按武器 id 查表自动建光 (`org.dark.shaders.light.LightShader`)|
| 贴图消费 | `TextureData` 表 → material / normal / surface 三遍 | 命中替换 `entry.sprite` 参与光照 (`org.dark.shaders.light.LightShader`)|
| 渲染层 | `LightShader.getCombatLayer()` = `ABOVE_SHIPS_AND_MISSILES_LAYER` | 分层渲染层序：粒子系统之前 (C07-S06)|

## 规则汇总

机制讲完，把必须遵守的边界整理成规则表。必须级条目都带违反后的可观察结果；建议级两条是编者的取舍判断：

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 使用方 MOD 在 `onApplicationLoad` 里读取任何能力门禁或注册 `LightData` / `TextureData` 前，必须先调用 `ShaderLib.init()`(幂等；`onApplicationLoad` 按 MOD 显示名排序，"zz GraphicsLib" 几乎总是最后执行)| 能力正常的环境里自己的 CSV 静默未注册：自动光源/贴图不生效、查询返回 not registered / none，无任何报错 |
| 2 | **必须** | `areShadersAllowed()` / `areBuffersAllowed()` 未通过时不得使用对应 shader / FBO 入口 (javadoc 原话 "Otherwise the game will crash!")| 崩溃或空绘制 |
| 3 | **必须** | 启动器抗锯齿开启且 `aaCompatMode` 未开时游戏无法启动 (GraphicsLib 自己的 `onApplicationLoad` 抛异常)| 加载末段中止，日志出现 "GraphicsLib shaders are not fully compatible with antialiasing!"；三选项 (关 AA / `aaCompatMode:true` / `enableShaders:false`)|
| 4 | **必须** | 动态光源只能在战斗内、战斗 `LocalData` 创建后添加 (`addLight` 静默跳过其余情况)| 战役画面/战斗早期调用 `addLight`：光源不出现且无任何报错 |
| 5 | **必须** | 需要长期存在的光源必须 `makePermanent()`(否则 60 秒 superLifetime failsafe 移除)| 光源一切参数正常却在约 1 分钟后消失 |
| 6 | **必须** | 挂载在实体上的光源在实体死亡后必须自己 `fadeOut` / `removeLight`(`LightShader` 只自动清理 projectile / beam 附着)| 残光驻留在尸体位置 ("光泄漏")|
| 7 | **必须** | 光源 / `TextureEntry` 引用禁止跨战斗缓存 (`LocalData` 随战斗重建；会话级对象禁止跨战斗缓存)| 第二场战斗起引用静默失效 |
| 8 | **必须** | `LightData` 行 `type` 只接受 `projectile` / `beam`；`TextureData` 行 `type` / `map` 必须是合法枚举名；两套 CSV 行缺必填字段都静默跳过 | 行未注册：该 id 的光源/贴图不生效，无报错 |
| 9 | **必须** | `LightData` 覆盖式加载是整行替换：覆盖行未写的组 (standard / hit / flash) 会变成 "无此组" | 用只写 standard 组的行覆盖原行后，命中/闪光光效丢失 |
| 10 | 建议 | 大场面按 `maximumLights`(默认 100、上限 372) 与屏外剔除设计光源数量 | 超限光源被静默跳过：光效 pop-in/out(设计行为)|
| 11 | 建议 | 贴图查询把 "none" 当两种原因处理：ini 门禁未开 vs id 未注册 | 两者都返回 null，需按配置区分 |
| 12 | **必须**(继承硬依赖规则)| 引用 `org.dark.shaders.**` 前必须在 `dependencies` 声明 `shaderLib` | 用户缺依赖时 `NoClassDefFoundError` |

## 完整最小例

规则有了，还需要一个能编译、能运行、能观察的落点。您将在累计工程上追加一个 CSV 与四个类，全部挂在既有工程布局 (MOD 骨架与战斗演示) 上，根目录为 `Starsector\mods\GearIndustry`：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                      # 本节在 dependencies 追加 shaderLib
├── data\
│   ├── config\
│   │   └── settings.json              # 硬依赖自检主题所有，本节不改
│   └── lights\
│       └── GearIn_light_data.csv      # 本节新增（自动光源数据，注册入口）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # 前置 modPlugin 主题所有，本节追加接入片段
│       ├── modcompat\
│       │   └── GearInDependencyCheck  # 硬依赖自检（前置单元所有，本节只消费）
│       └── visuals\
│           ├── GearInLightCombatPlugin  # 本节新增：战斗接线插件
│           ├── GearInShipLight          # 本节新增：挂接光源实体
│           ├── GearInFlashSpawner       # 本节新增：周期闪光生成器
│           └── GearInLightReporter      # 本节新增：门禁与数据查询报告
└── jars\
```

四个类里，`GearInLightCombatPlugin` 是唯一需要注册的入口；编者把查询与日志单独拆给 `GearInLightReporter`，让您不看画面也能核对状态。按顺序追加：

### mod_info.json(在既有 dependencies 上追加一项)

```json
  "dependencies": [
    {
      "id": "lw_lazylib",
      "name": "LazyLib"
    },
    {
      "id": "shaderLib",
      "name": "GraphicsLib"
    }
  ]
```

`id` 必须逐字为 `shaderLib`(打开 `Starsector\mods\GraphicsLib\mod_info.json` 复制，不要写 `GraphicsLib`)。`name` 只影响启动器显示。

### data/lights/GearIn_light_data.csv(本节所有)

表头与 `Starsector\mods\GraphicsLib\data\lights\core_light_data.csv` 一致。

下面两行键为原版既有 ID(`lightac_shot` 是 `data\weapons\proj\lightac_shot.proj` 的射弹 id，`pdlaser` 是 `weapon_data.csv` 里的光束武器 id)，用**覆盖式**读入后会整行替换 GraphicsLib 核心的对应条目：

```csv
id,type,size,intensity,fadeout,color,offset,hit size,hit intensity,hit fadeout,hit color,flash size,flash intensity,flash fadeout,flash color,flash offset,chance,fighter dim
lightac_shot,projectile,90,0.4,0.25,"[255,235,190]",,250,0.4,1,"[255,225,50]",,,,,,1,TRUE
pdlaser,beam,50,0.7,0.2,"[255,180,160]",,110,0.25,,"[155,10,10]",55,0.25,,"[155,10,10]",,1,TRUE
```

要点：

- 核心的 `lightac_shot` 行只有 standard 与 flash 组 (`hit size` 等空 → 无 hit 光，`org.dark.shaders.light.LightData` 的条件不满足)。
- 本节覆盖行改写 standard 组并新增 hit 组，同时丢掉 flash 组 —— 演示 "覆盖行是整行替换，想保留的组必须一起带上"(规则 9)。
- `color` 必须是 `[r,g,b]` 字符串形式；standard 组要求 `size > 0`、`intensity > 0`、`color` 非空，否则该行静默跳过。
- 本节不新增纹理数据文件：材质/法线/表面贴图条目直接消费 GraphicsLib 核心 `core_texture_data.csv` (GraphicsLib 自己以 NoOverwrite 注册，覆盖全部原版船体/武器)，示例只演示查询面。

### 例 1：GearInLightCombatPlugin—— 战斗接入插件

```java
package data.scripts.gearin.visuals;

import java.util.List;

import org.dark.shaders.light.LightData.LightDataType;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例：GraphicsLib 光源与纹理的最小消费面（战斗内）的接线插件。
 *
 * 适用边界：
 * - GraphicsLib 的初始化与 LightData/TextureData CSV 注册发生在 onApplicationLoad
 *   （本 MOD 的接入片段见页面正文，本节示例类不含该片段）；
 * - ShaderHook（settings plugins 注册的 EveryFrameCombatPlugin）在每场战斗开始时
 *   创建 LightShader 的战斗 LocalData（engine customData 键 "shaderlib_LightShader"），
 *   并注册一个覆盖全部层的分层渲染钩子；LightShader 声明在
 *   ABOVE_SHIPS_AND_MISSILES_LAYER 层渲染（ShaderAPI.isCombat() == true）；
 * - LightShader.addLight 只在光源 shader 已启用且战斗 LocalData 已存在时把光源写进
 *   当前战斗的光源列表，其余情况静默跳过 —— 动态光源必须在战斗内添加；
 * - 光源列表随战斗销毁；未永久化的光源在 superLifetime（60 秒）耗尽后被 failsafe
 *   移除；advance 返回 true（size 或 intensity 归零）的光源由 LightShader.advance
 *   逐帧移除，fadeOut 是标准自灭姿势；
 * - 暂停时 LightShader.advance 不推进光源（光源静止但保持可见）。
 *
 * 本类只做接线：光源实体（挂接光 / 周期闪光）与 GraphicsLib 状态报告分别在
 * GearInShipLight / GearInFlashSpawner / GearInLightReporter。
 */
public class GearInLightCombatPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private final GearInShipLight shipLight = new GearInShipLight();
    private final GearInFlashSpawner flashSpawner = new GearInFlashSpawner();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        GearInLightReporter.reportCapabilities();
        GearInLightReporter.reportLightData("lightac_shot", LightDataType.PROJECTILE);
        GearInLightReporter.reportLightData("pdlaser", LightDataType.BEAM);
        GearInLightReporter.reportShipTextures(engine);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（C07-S01 同款姿势）
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（C07-S01）：暂停时 advance 仍会被调用，光源静止但保持可见
        }
        ShipAPI player = engine.getPlayerShip();
        shipLight.update(player);
        flashSpawner.update(amount, player);
    }
}
```

- **职责**：战斗侧的薄接入入口类。`init` 时驱动 `GearInLightReporter` 的三面报告 (能力门禁、两行 `LightData` 查询、`TextureData` 贴图查询)。
- `advance` 先做防御性判空与暂停检查，再把玩家舰船分别交给 `GearInShipLight` 与 `GearInFlashSpawner` 逐帧推进。自己不创建光源，也不直接查询数据。
- **协作**：包内协作三个类 ——`GearInShipLight`(挂载光源实体)、`GearInFlashSpawner`(周期闪光生成)、`GearInLightReporter`(门禁与数据查询报告)；无跨单元引用。
- **绑定位置**：战斗引擎插件注册 (`engine.addPlugin(new GearInLightCombatPlugin())`，见下文注册片段)—— 四个类中唯一由外部注册的入口。插件实例每场战斗新建，`flash` 计数等状态随实例自然重置。

### 例 2：GearInShipLight—— 挂载光源实体

```java
package data.scripts.gearin.visuals;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 挂接到玩家舰船的常驻点光源实体（光源实体类）。
 *
 * 生命周期：战斗首帧由 GearInLightCombatPlugin 驱动 update 时创建（此时战斗
 * LocalData 已存在，addLight 不再静默跳过），makePermanent 关闭 60 秒 failsafe；
 * 玩家舰船死亡后 fadeOut(1f) 自灭，由 LightShader.advance 在 intensity 归零后移除。
 */
public class GearInShipLight {

    private static final Logger LOG = Global.getLogger(GearInShipLight.class);

    /** 挂接到玩家舰船的暖色点光源参数。 */
    private static final Vector2f OFFSET = new Vector2f(0f, 60f);
    private static final float INTENSITY = 1.0f;
    private static final float SIZE = 150f;

    private StandardLight light;
    private boolean added;

    /** 每帧推进：首帧创建挂接光，舰船死亡后主动 fadeOut 让引擎移除。 */
    public void update(ShipAPI player) {
        if (player == null) {
            return;
        }
        if (light == null) {
            if (!added && player.isAlive()) {
                add(player); // 延迟到战斗首帧：保证战斗 LocalData 已存在
            }
            return;
        }
        if (!player.isAlive() && !light.isFadingOut()) {
            light.fadeOut(1f);
            LOG.info("GearIn light ship: player destroyed, fading out attached light");
        }
    }

    /** 创建并挂接常驻点光源：跟随舰船移动，关闭 60 秒 failsafe。 */
    private void add(ShipAPI player) {
        added = true;
        light = new StandardLight(player.getLocation(), new Vector2f(), OFFSET, player,
                INTENSITY, SIZE);
        light.setColor(new Color(255, 210, 130));
        light.makePermanent(); // lifetime 与 superLifetime 均置 -1，不再受 failsafe 限制
        LightShader.addLight(light);
        LOG.info("GearIn light ship: attached light added offset=" + OFFSET
                + " size=" + SIZE + " intensity=" + INTENSITY);
    }
}
```

- **职责**：玩家舰船常驻暖色点光源的生命周期。每帧 `update`：战斗首帧 (`added` 标志) 创建挂载光源。
- 创建写法：`StandardLight(location, velocity, offset, attach, intensity, size)` 六参构造 + `setColor` + `makePermanent()`(规则 5)+ `LightShader.addLight`。
- 挂载后光源位置每帧取自舰船，offset 按朝向旋转，`setLocation` 对挂载光源无效。
- 玩家舰船死亡后 `fadeOut(1f)` 自灭 (规则 6)。
- **协作**：由 `GearInLightCombatPlugin.advance` 在暂停检查之后驱动。
- **绑定位置**：无独立注册入口 —— 普通实体类，由接入插件持有并驱动。

### 例 3：GearInFlashSpawner—— 周期闪光生成器

```java
package data.scripts.gearin.visuals;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 周期闪光生成器（光源实体类）：按固定间隔在玩家舰船周围随机位置生成一盏
 * fadeOut 自灭的短命点光源。闪光的间隔节奏与计数都归本类；由
 * GearInLightCombatPlugin 在非暂停时驱动 update。
 */
public class GearInFlashSpawner {

    private static final Logger LOG = Global.getLogger(GearInFlashSpawner.class);

    /** 周期闪光的间隔（秒）。 */
    private static final float FLASH_INTERVAL = 2f;

    private final IntervalUtil flashInterval = new IntervalUtil(FLASH_INTERVAL, FLASH_INTERVAL);
    private int flashCount;

    /** 每帧推进（须在非暂停时调用）：间隔到点即生成一盏闪光。 */
    public void update(float amount, ShipAPI player) {
        flashInterval.advance(amount);
        if (flashInterval.intervalElapsed()) {
            spawn(player);
        }
    }

    /** 在玩家舰船周围随机位置生成一盏 fadeOut(0.5f) 自灭的点光源（短命光源的标准姿势）。 */
    private void spawn(ShipAPI player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        double angle = Math.random() * Math.PI * 2.0;
        float radius = 60f + (float) (Math.random() * 120f);
        Vector2f loc = new Vector2f(player.getLocation().x + (float) (Math.cos(angle) * radius),
                player.getLocation().y + (float) (Math.sin(angle) * radius));
        StandardLight flash = new StandardLight(loc, new Vector2f(), new Vector2f(), null, 0.8f, 80f);
        flash.setColor(new Color(120, 220, 255));
        flash.fadeOut(0.5f); // intensity 归零后 LightShader.advance 会从光源列表移除该光源
        LightShader.addLight(flash);
        flashCount++;
        LOG.info("GearIn light flash: #" + flashCount + " at (" + (int) loc.x + ", " + (int) loc.y + ")");
    }
}
```

- **职责**：按固定间隔 (2 秒) 在玩家舰船周围随机位置生成一盏 `fadeOut(0.5f)` 自灭的淡蓝短命点光源并计数 (`GearIn light flash: #N` 日志；计数随插件实例每场战斗从 `#1` 重置，规则 7 的观察面)。
- **协作**：由 `GearInLightCombatPlugin.advance` 在暂停检查之后驱动 (暂停时不推进、间隔不累计)。
- **绑定位置**：无独立注册入口 —— 普通实体类，由接入插件持有并驱动。

### 例 4：GearInLightReporter—— 门禁与数据查询报告

```java
package data.scripts.gearin.visuals;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightData;
import org.dark.shaders.light.LightData.LightDataType;
import org.dark.shaders.light.LightEntry;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.util.ShaderAPI;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.dark.shaders.util.TextureData.ObjectType;
import org.dark.shaders.util.TextureData.TextureDataType;
import org.dark.shaders.util.TextureEntry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * GraphicsLib 初始化状态与数据注册状态的日志报告（查询面，注册/初始化报告类）。
 *
 * 报告内容：能力门禁与光源 shader 可用性（必须在 ShaderLib.init 之后读取）、
 * LightData 注册表查询（projectile/beam 自动光源数据）、TextureData 注册表查询
 * （玩家舰船船体的 material/normal/surface 贴图条目）。只做查询与日志，不创建光源。
 */
public class GearInLightReporter {

    private static final Logger LOG = Global.getLogger(GearInLightReporter.class);

    /** 能力门禁与光源 shader 可用性报告（必须在 ShaderLib.init 之后读取）。 */
    public static void reportCapabilities() {
        boolean shaders = ShaderLib.areShadersAllowed();
        boolean buffers = ShaderLib.areBuffersAllowed();
        ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);
        boolean lightReady = lightShader != null && lightShader.isEnabled();
        LOG.info("GearIn light capabilities: shaders=" + shaders + " buffers=" + buffers
                + " lightShaderEnabled=" + lightReady);
    }

    /** 查询 LightData 注册表（projectile/beam 自动光源数据），报告消费侧查询面。 */
    public static void reportLightData(String id, LightDataType type) {
        LightEntry entry = LightData.getLightData(id, type);
        if (entry == null) {
            LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ") not registered");
            return;
        }
        LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ")"
                + " standard=" + entry.hasStandard
                + " size=" + entry.standardSize
                + " intensity=" + entry.standardIntensity
                + " chance=" + entry.chance
                + " fighterDim=" + entry.fighterDim);
    }

    /** 查询 TextureData 注册表：玩家舰船船体的 material/normal/surface 贴图条目。 */
    public static void reportShipTextures(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        String hullId = player.getHullSpec().getHullId();
        LOG.info("GearIn light texture: hull=" + hullId
                + " material=" + describeTexture(hullId, TextureDataType.MATERIAL_MAP)
                + " normal=" + describeTexture(hullId, TextureDataType.NORMAL_MAP)
                + " surface=" + describeTexture(hullId, TextureDataType.SURFACE_MAP)
                + " shipTex=" + describeEntry(
                        ShaderLib.getShipTexture(player, TextureDataType.MATERIAL_MAP)));
    }

    private static String describeTexture(String id, TextureDataType map) {
        return describeEntry(TextureData.getTextureData(id, map, ObjectType.SHIP, 0));
    }

    private static String describeEntry(TextureEntry entry) {
        if (entry == null) {
            return "none";
        }
        return "mag=" + entry.magnitude + " tex=" + entry.sprite.getTextureId();
    }
}
```

- **职责**：纯静态查询与日志报告。
  - `reportCapabilities` 读能力门禁与 `LightShader` 可用性 (必须在 `ShaderLib.init()` 之后读取，规则 1/2 的观察面)。
  - `reportLightData` 演示 `LightData.getLightData` 查询面 (本节 CSV 覆盖核心后应读到 `standard=true` 且 `size=90.0` / `size=50.0`)。
  - `reportShipTextures` 演示 `TextureData.getTextureData` 与 `ShaderLib.getShipTexture` 查询面 (玩家船体 hullId 在核心表里查 material / normal / surface 条目)。
- **协作**：由 `GearInLightCombatPlugin.init` 一次性驱动。
- **绑定位置**：无独立注册入口 —— 静态工具方法，由接入插件调用。

### 日志前缀 (逐字)

五条日志前缀逐字如下 ——`GearIn light ...:` 不是 strings key，只进 `starsector.log`：

- `GearIn light capabilities:` —— 由 `GearInLightReporter.reportCapabilities()` 输出，格式 `shaders=<s> buffers=<b> lightShaderEnabled=<e>`；
- `GearIn light data:` —— 由 `GearInLightReporter.reportLightData(...)` 输出两行 (`lightac_shot` / `pdlaser`)。
  - 命中时格式 `standard=<b> size=<f> intensity=<f> chance=<f> fighterDim=<b>`；未注册时 `not registered`。
- `GearIn light texture:` —— 由 `GearInLightReporter.reportShipTextures(...)` 输出，格式 `hull=<id> material=<m> normal=<m> surface=<m> shipTex=<m>`；
- `GearIn light ship:` —— 由 `GearInShipLight` 输出两处：
  - 挂载成功：`attached light added offset=(0.0, 60.0) size=150.0 intensity=1.0`。
  - 玩家舰船死亡：`player destroyed, fading out attached light`。
- `GearIn light flash:` —— 由 `GearInFlashSpawner` 每次生成输出，格式 `#<n> at (<x>, <y>)`(编号随插件实例每场战斗从 `#1` 重置)。

### 接入片段 (在既有 modPlugin 中追加)

```java
    @Override
    public void onApplicationLoad() {
        // GraphicsLib 初始化：必须先于任何能力门禁读取。
        // 幂等；onApplicationLoad 按 MOD 显示名排序，"zz GraphicsLib" 几乎总是最后执行，
        // 不先调用 ShaderLib.init() 的话守卫会读到初始 false，下面的 CSV 静默不注册。
        ShaderLib.init();
        if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
            LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
        }
        GearInDependencyCheck.reportDeclaredDependencies(); // 依赖自检报告（硬依赖主题的接入点）
        // ... 既有回调内容 ...
    }
```

需要的 import(在 `GearInModPlugin` 里追加)：`org.dark.shaders.light.LightData`、`org.dark.shaders.util.ShaderLib`。

`GearInModPlugin` 类由前置的 modPlugin 主题单元持有，本节只追加这两处；本节示例类不含该片段。

### 注册片段 (战斗侧，工程片段)

```java
    // 工程片段：在测试战役的 MissionDefinition.defineMission 中（或既有战斗插件
    // init() 里）追加：
    engine.addPlugin(new GearInLightCombatPlugin());
```

### 构建

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/visuals` 下的四个新类 (可用 `jar tf` 核对)

### 验证方案

前置数据：确认以下内容已就绪 —— 累计工程 (C12-S02)、`mod_info.json` 追加 `shaderLib` 依赖、`data\lights\GearIn_light_data.csv` 与四个示例类；`GearInModPlugin.onApplicationLoad` 内追加本节接入片段 (C06-S02)；测试战役 `GearIn_test`(C03-S01) 含本节注册片段一行；本机已安装 GraphicsLib(启动器勾选)。

操作步骤：

1. 构建与产物核对：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/visuals` 下四个新类。
2. 启动检查：确认启动器抗锯齿为 Off(或 `GRAPHICS_OPTIONS.ini` 的 `aaCompatMode:true`) 后启动游戏进入主菜单 —— 无 "not fully compatible with antialiasing" 中止。
3. 战斗冒烟与画面：进入 `GearIn_test` 一场战斗，`starsector.log` 核对五个报告行 (`capabilities` / 两行 `light data` / `texture` / `ship`)，无 `gear_in` 引起的 `ERROR`；随后观察玩家舰船暖色光晕 (跟随移动、随朝向偏移)、每 2 秒淡蓝闪光 (约 0.5 秒自灭)、轻型自动炮暖白曳光与点防御光束光晕 (覆盖行生效)。
4. 暂停：按空格暂停约 10 秒 —— 光源静止但可见；恢复后继续 (暂停期间不产生新闪光)。
5. 生命周期：把 `makePermanent()` 注释掉后进战斗 —— 挂载光晕约 60 秒后消失 (failsafe)，改回后确认光晕长期存在；随后击毁玩家舰船 —— 日志出现 `GearIn light ship: player destroyed, fading out attached light`，光晕约 1 秒淡出消失。
6. 跨战斗：退出再进一场 —— 报告行重现、`flash` 编号从 `#1` 重置、无显存/画面异常累积。
7. 故障注入·初始化顺序与覆盖语义：① 注释接入片段里的 `ShaderLib.init();` 后重启 —— `capabilities` 仍 true 但两行 `light data` 变 `not registered`(对应故障 5)，改回后恢复；② 把 CSV 的 `lightac_shot` 行删掉 hit 组三列 (`hit size` 等留空) 后重启 —— 命中爆炸光消失 (对应故障 7)，改回后恢复。
8. 故障注入·能力门禁，可选：把 `GRAPHICS_OPTIONS.ini` 的 `enableShaders` 改 `false` 后重启 —— `capabilities` 全 false、光源全无；改回后恢复。

补充：改 `enableLights` 只影响 `lightShaderEnabled` 与光源，不影响 CSV 注册行，两者可观察结果不同。

预期结果：

1. **启动检查**：启动器抗锯齿处于 Off(或 `GRAPHICS_OPTIONS.ini` 的 `aaCompatMode:true`)；游戏正常进入主菜单，`starsector.log` 没有 "GraphicsLib shaders are not fully compatible with antialiasing!"(规则 3 的正面)。
2. **战斗初始化**：进入 `GearIn_test` 测试战役一场战斗，`starsector.log` 依次出现：
```text
GearIn light capabilities: shaders=true buffers=true lightShaderEnabled=true GearIn light data: lightac_shot (projectile) standard=true size=90.0 intensity=0.4 chance=1.0 fighterDim=true GearIn light data: pdlaser (beam) standard=true size=50.0 intensity=0.7 chance=1.0 fighterDim=true GearIn light texture: hull=<玩家船体id> material=mag=<m> tex=<id> normal=mag=<m> tex=<id> surface=... shipTex=mag=<m> tex=<id> GearIn light ship: attached light added offset=(0.0, 60.0) size=150.0 intensity=1.0
```
没有 `gear_in` 引起的 `ERROR`。`texture` 行的 `material` / `normal` 取决于核心 `core_texture_data.csv` 是否注册了该船体 (原版船体通常都有)；`surface` 可能为 `none`(surface 表覆盖较少且依赖 `loadSurface` 门禁)。
3. **画面结果**：玩家舰船被暖色 (255,210,130) 光晕笼罩，光晕随舰船移动、随朝向偏移。
   - 每 2 秒舰船周围随机位置闪一次淡蓝 (120,220,255) 闪光并在约 0.5 秒内消失。
   - 轻型自动炮 (lightac) 飞行中带持续的暖白曳光、命中点出现爆炸光 (核心行无 hit 组，覆盖行新增；核心原有 flash 组被整行替换丢弃)。
   - 点防御光束 (pdlaser) 带暖色光晕。
4. **覆盖生效与暂停**：把本节 CSV 的 `lightac_shot` 行 `size` 改回 `50`(与核心一致) 再对比 —— 曳光明显变小；`90` 时更大。注意 `readLightDataCSV` 是**覆盖式**：改 CSV 后重启游戏即可 (数据在 `onApplicationLoad` 注册)。按空格暂停 —— 光源静止 (闪光停在原地、光晕不随舰船淡出变化) 但**仍然可见**；恢复后继续。
5. **生命期与失效清理**：闪光每次都在约 0.5 秒内消失 (fadeOut 自灭)；挂载光晕持续存在 —— 等待超过 60 秒仍在 (`makePermanent` 生效，规则 5 的正面)。若把 `makePermanent()` 注释掉 (故障注入)，光晕在第 60 秒附近消失。击毁玩家舰船 ——`starsector.log` 出现 `GearIn light ship: player destroyed, fading out attached light`，光晕约 1 秒内淡出消失 (规则 6)；闪光停止产生 (`player.isAlive()` 检查)。
6. **战斗结束 (跨战斗)**：退出战斗再进一场 —— 五个日志行重新出现、`flash` 编号从 `#1` 重新开始 (每场战斗全新 `LocalData`，规则 7 的正面)；连续多场战斗后画面与显存无异常累积。
7. **变更验证 (故障注入)**：
   - **能力门禁注入**：把 `GRAPHICS_OPTIONS.ini` 的 `enableShaders` 改为 `false` 后重启游戏。`capabilities` 行变 `shaders=false buffers=false lightShaderEnabled=false`，两行 `light data` 变 `not registered`(接入片段里的能力判断把 CSV 读取跳过了)，战斗中无任何光源。恢复：改回 `true` 后恢复。对照：`enableLights:false` 时 `lightShaderEnabled=false`、光源全无，但 CSV 仍注册 (能力判断只查 shaders/buffers)—— 两条门禁的可观察结果不同，注意区分。
   - **初始化顺序注入**：把接入片段里的 `ShaderLib.init();` 注释掉后重启游戏。`capabilities` 行**仍然** `lightShaderEnabled=true`(战斗时 GraphicsLib 早已初始化)，但两行 `light data` 变 `not registered`(本 MOD 的 CSV 在能力判断 false 时被跳过)。"能力全开但自己的数据没注册" 正是规则 1 描述的迷惑症状；改回后恢复。

通过信号：步骤 1–6 全部符合；步骤 7–8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；恢复 `GRAPHICS_OPTIONS.ini` 与 CSV 为原值

## 故障分析

以下故障按您在日志和画面上看到的症状组织，每条给出触发条件、断链原因和核对顺序。

1. **光源不出现，代码看起来完全没问题**。
  - 触发：`GRAPHICS_OPTIONS.ini` 的 `enableLights:false`(或 `enableShaders:false` / GPU 不支持 FBO)。
  - 原因：`LightShader` 构造器读入 `enableLights`，关闭时 `isEnabled()` 为 false，此后所有 `addLight` 调用全部静默无效；`capabilities` 行会显示 `lightShaderEnabled=false`。
  - 修正：开 `enableLights` / `enableShaders`；GPU 不支持时无解 (门禁本意)。排查入口永远是先看 `capabilities` 行。
2. **战役画面 (或战斗初始化前) 调 `addLight` 没反应**。
  - 触发：在 `onApplicationLoad`、战役脚本、战斗 `MissionDefinitionAPI.defineMission` 早期调用 `addLight`。
  - 原因：`LocalData` 由 `ShaderHook.init` 在战斗初始化阶段才创建 (`LightShader.initCombat`)，光源列表不存在时 `addLight` 直接返回，没有任何报错。
  - 修正：光源创建放到战斗推进回调 (`EveryFrameCombatPlugin.advance` / 部署监听器 / 武器效果)，本节示例延迟到战斗首帧 (规则 4)。
3. **挂载光源一分钟后消失**。
  - 触发：没调 `makePermanent()`。
  - 原因：`StandardLight` 的 `superLifetime` 默认 60 秒，耗尽即被 failsafe 移除；此时 `size` / `intensity` 一切正常，最容易误判。
  - 修正：常驻光源 `makePermanent()`(规则 5)。
4. **舰船被击毁后残光驻留原地**。
  - 触发：挂载光源没在舰船死亡时清理。
  - 原因：`LightShader` 只自动清理 projectile / beam 的附着，普通实体挂载没有生命周期跟踪，残光继续在原地渲染。
  - 修正：自己的 `advance` 里判 `isAlive()` 并 `fadeOut` / `removeLight`(规则 6，本节示例 `GearInShipLight.update`)。
5. **自己的 light CSV 不生效，但 `capabilities` 全 true**。
  - 触发：`onApplicationLoad` 里没先调 `ShaderLib.init()` 就读能力门禁。
  - 原因：`onApplicationLoad` 按 MOD 显示名排序执行，"zz GraphicsLib" 几乎总是最后；本 MOD 先执行时门禁读到初始 false，CSV 读取被跳过，等 GraphicsLib 初始化完成后数据也回不来了。
  - 可观察：`light data` 行 `not registered`，战斗内该武器无自动光源，无任何报错。
  - 修正：接入片段先 `ShaderLib.init()`(幂等) 再读门禁 (规则 1)。
6. **CSV 行写了但没注册**。
  - 触发：`type` 拼错 (如 `projectile` 写成 `Projectile`)、行缺 `id` / `type`、standard 组缺 `size` / `intensity` / `color` 之一。
  - 原因：`LightData` 的行校验不通过时该行静默跳过，日志通常只有读入函数捕获异常时的 ERROR(一般没有)。
  - 修正：对照核心 `core_light_data.csv` 的表头与格式逐列核对 (规则 8)。
7. **覆盖式加载后命中/闪光光效丢了**。
  - 触发：用只写 standard 组的行覆盖了一个原本有 hit / flash 组的 id。
  - 原因：覆盖式加载是整行替换，未写的组就是 "无此组"(`LightEntry.hasHit` / `hasFlash` 为 false)，武器的命中爆炸光/闪光随之消失。
  - 修正：覆盖行带上想保留的全部组 (规则 9，本节示例行的写法)。
8. **贴图查询全部返回 none**。
  - 触发：`GRAPHICS_OPTIONS.ini` 的 `loadMaterial` / `enableNormal` / `loadSurface` 被关，或 id 确实未注册。
  - 原因：两种原因都返回 null 且无报错，`texture` 行 `material=none ...`，渲染里该船没有材质/法线效果；从返回值无法区分。
  - 修正：先开对应门禁再确认 id 是否注册，用 `GRAPHICS_OPTIONS.ini` 状态区分 (建议 11)。
9. **大混战时光效忽隐忽现**。
  - 触发：单帧光源数超过 `maximumLights`(默认 100，硬上限 372) 或超出屏外剔除范围。
  - 原因：超限光源被静默跳过 (设计行为)，离屏光源本就不渲染，不算故障。
  - 修正：调高 `maximumLights`(≤372) 或接受设计行为 (建议 10)。
10. **启动到加载末段中止，日志有 "not fully compatible with antialiasing"**。
  - 触发：启动器开了抗锯齿且 `aaCompatMode` 未开。
  - 原因：`ShaderModPlugin.onApplicationLoad` 的抗锯齿检查发现 `getAASamples() > 1` 且未开兼容模式，抛 `RuntimeException`，游戏启动失败。
  - 修正：按异常文案三选项之一处理：关 AA / `aaCompatMode:true` / `enableShaders:false`(规则 3)。
11. **跨战斗引用光源/贴图对象**。
  - 触发：把 `StandardLight` 或 `TextureEntry` 存进静态字段跨战斗复用。
  - 原因：`LocalData` 随战斗重建 (`LightShader.initCombat`)，第二场战斗起引用全部失效，静默无报错。
  - 修正：每场战斗重新创建 (规则 7)。

## 总结

本节把 GraphicsLib 的消费面从启动阶段走到战斗画面：`onApplicationLoad` 里先 `ShaderLib.init()` 再读能力门禁，会话级完成 `LightData` / `TextureData` 的 CSV 注册；每场战斗由 `ShaderHook` 重建光源列表，`LightShader` 按射弹与武器 id 自动建光，并用 material / normal / surface 三遍替换贴图。整条链可以用一句话收住：注册是会话级的，渲染是战斗级的；战役画面不经过这条链路。

可复用的判断有三条。第一，初始化顺序是零成本规则：`ShaderLib.init()` 幂等，先调再读门禁，能直接避开 "能力全开但自己的 CSV 静默未注册" 这种最迷惑的症状。第二，光源生命周期由调用方负责：短命用 `fadeOut` 自灭，常驻用 `makePermanent()` 关掉 60 秒 failsafe，挂载在实体上的光源在实体死亡后自己清理。第三，覆盖式 CSV 是整行替换，想保留的组必须一起带上，否则命中与闪光光效会静默丢失。

排查顺序也可以带走：先看 `capabilities` 日志行确定能力与开关，再查注册状态区分 "没注册" 与 "门禁未开"，最后核对生命周期 (暂停、60 秒、战斗边界)。更上层的视觉库如 MagicLib 把同一套 "初始化 → 门禁 → 生命周期内消费" 的模式推广到设置、HUD 与生涯工具，这套判断顺序可以原样迁移。

## 建议参考项

- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`—— 战斗插件基类，例集接入的所属对象；
- `com.fs.starfarer.api.combat.CombatEngineAPI`——`addLayeredRenderingPlugin` / `getCustomData` / `getPlayerShip` / `isPaused`；
- `com.fs.starfarer.api.combat.ShipAPI`——`getHullSpec` / `isAlive` / `getLocation`(实体位置经 `CombatEntityAPI`)；
- `com.fs.starfarer.api.graphics.SpriteAPI`——`getTextureId`(`TextureEntry.sprite` 的消费面)；
- `com.fs.starfarer.api.util.IntervalUtil`—— 周期节奏计时 (例集闪光间隔)；
- `com.fs.starfarer.api.Global`——`getLogger`；
- `org.dark.shaders.util.ShaderLib`——`init` / `areShadersAllowed` / `areBuffersAllowed` / `getShaderAPI` / `getShipTexture` / `getWeaponTexture` / `overrideShipTexture` / `overrideWeaponTexture`；
- `org.dark.shaders.util.ShaderAPI`——shader 渲染回调接口 (`isEnabled` / `getCombatLayer` / `isCombat` / `initCombat`)；
- `org.dark.shaders.light.LightAPI` / `StandardLight`—— 光源接口与标准实现 (四型构造、`fadeOut` / `makePermanent` / `attachTo` 等 setter)；
- `org.dark.shaders.light.LightShader`——`addLight` / `removeLight` / `advance`，`DO_NOT_RENDER` customData 键；
- `org.dark.shaders.light.LightData` / `LightEntry`—— 自动光源数据注册与查询 (`readLightDataCSV` / `getLightData`)；
- `org.dark.shaders.util.TextureData` / `TextureEntry`—— 贴图数据注册与查询 (`readTextureDataCSV` / `getTextureData`)；
- `mod_info.json`——`dependencies` 追加 `shaderLib`；
- `data/lights/GearIn_light_data.csv`—— 本节新增的自动光源数据；
- `data/weapons/proj/lightac_shot.proj` / `data/weapons/weapon_data.csv`—— 示例 CSV 两行键 (`lightac_shot` / `pdlaser`) 的原版来源；
- `Starsector\mods\GraphicsLib\data\lights\core_light_data.csv` / `core_texture_data.csv` ——GraphicsLib 核心数据 (表头格式与覆盖语义的对照)；
- `Starsector\mods\GraphicsLib\data\config\settings.json`——`plugins` 段注册 `ShaderHook`；
- `Starsector\mods\GraphicsLib\GRAPHICS_OPTIONS.ini`—— 能力与性能开关 (`enableShaders` / `enableLights` / `maximumLights` / `loadMaterial` / `enableNormal` / `loadSurface` / `aaCompatMode`)。
