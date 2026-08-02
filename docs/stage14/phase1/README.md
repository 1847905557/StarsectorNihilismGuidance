# GLSL 基础：类型、坐标与数据流

本页把"着色器（shader）"讲透——这是教程对 shader 术语的首次完整讲解；GLSL（OpenGL Shading Language）是 shader 的编程语言，在 Starsector `0.98a-RC8` 里，MOD 用游戏自带的 LWJGL 绑定（`org.lwjgl.opengl`）编译、链接和提交 GLSL 程序，渲染所属对象是分层渲染专题页确立的分层渲染插件通道。本页覆盖 GLSL 基本类型、顶点/片元两端的坐标语义，以及从顶点数据到片元输出的一条完整数据流（attribute / uniform / varying）；shader 的**路径、加载与编译日志**、**uniform / 纹理 / 矩阵提交**、**渲染层接入与资源释放**由后续单元展开，本页只保留最简流程。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 说出 GLSL 1.10/1.20 方言的基本类型（标量、向量、矩阵、采样器）与构造器、swizzle、分量级运算，并指认本地成熟实例里的对应用法；
- 说出顶点与片元两端的坐标语义：attribute 位置数据的世界/裁剪/屏幕三种约定、`gl_Position` 的裁剪空间含义、`gl_FragCoord` 的窗口像素坐标、纹理坐标 [0,1] 约定；
- 说出顶点到片元的三条数据通道——attribute（每顶点）、uniform（每绘制）、varying（逐片元插值），以及 `gl_Position` → `gl_FragCoord` 这条特殊通道；
- 说出 Starsector 中 shader 的加载与使用上下文：游戏自带 LWJGL、OpenGL 2.0 能力门禁（GraphicsLib 硬措辞）、`SettingsAPI.loadText` 读取源文本、编译/链接状态检查与信息日志；
- 说出 GLSL 110 方言与 430 方言（现代 430 实例内部标准）的关键差异（`attribute`/`varying`/`gl_FragColor` vs `in`/`out`/`layout`/`fragColor`）与各自的硬件前提；
- 从症状定位典型故障：不写 `gl_Position`、attribute 位置未固定、varying 两侧不一致、uniform 名拼错、漏查编译/链接状态、方言版本与硬件不匹配。

## 前置知识

本页在既有 MOD 布局、战斗渲染与 shader 门禁上下文之上展开，以下先修内容请先就绪：

- MOD 目录结构与 `starsector.log` 的读取（S01-P01）：本页所有可观察结果都落在日志与画面里。
- 战役（mission）与生涯（campaign）概念、`GearIn_test` 测试战役（S03-P01）：本页用它做游戏内验证。
- 编译 JAR 链路（S06-P01）：本页验证需要重新编译 `jars\GearIndustry.jar`。
- `Global` 生命周期（S06-P03）：禁止跨战斗缓存引擎引用，本页的渲染插件同规。
- 战斗状态读取（S07-P01）：`getPlayerShip()` 与实体存活判定，本页用玩家舰船做演示锚点。
- 战斗插件生命周期（S07-P04）：本页的注册片段所在。
- 分层渲染插件（S07-P05）：世界坐标提交与 GL 资源清理必须遵守的规则，本页直接复用；shader 程序是其中"着色器（shader）程序"资源的一种。
- GraphicsLib 初始化与能力门禁（S12-P03）：`ShaderLib.init()` / `areShadersAllowed()` 的完整上下文。

## 文件与 API 链路

本页涉及的文件与调用链如下：

| 环节 | 文件 / API | 说明 |
|---|---|---|
| shader 源文本 | `data/shaders/GearIn_basic_shader.vert` / `.frag`（本页所有权）| MOD 根目录相对路径，`/` 分隔；示例资源用语义命名 |
| 文本读取 | `SettingsAPI.loadText(String)`（`com.fs.starfarer.api.SettingsAPI`）| GraphicsLib `org.dark.shaders.post.PostProcessShader` 同款调用；javadoc 必须遵守的规则：路径用 `/` |
| 编译 | LWJGL `GL20.glCreateShader` / `glShaderSource` / `glCompileShader` / `glGetShaderi(GL_COMPILE_STATUS)` / `glGetShaderInfoLog` | GraphicsLib `ShaderLib.createShader` 同款（失败抛 `"Error creating shader: " + 日志`）|
| 链接 | `GL20.glCreateProgram` / `glAttachShader` / `glBindAttribLocation` / `glLinkProgram` / `glGetProgrami(GL_LINK_STATUS)` / `glGetProgramInfoLog` | GraphicsLib `ShaderLib.loadShader` 同款（链接失败删 program 返回 0）|
| 能力门禁 | GraphicsLib `ShaderLib.areShadersAllowed()` | `org.dark.shaders.util.ShaderLib`；javadoc 硬措辞见"加载与使用上下文" |
| uniform | `GL20.glGetUniformLocation` / `glUniform2f` | GraphicsLib `org.dark.shaders.post.PostProcessShader` 同款 |
| 顶点属性 | `GL20.glVertexAttribPointer` / `glEnableVertexAttribArray` / `glDrawArrays` | VBO + long 偏移做法（本页完整最小例）|
| 渲染所属对象 | `CombatLayeredRenderingPlugin`（分层渲染插件通道）| `engine.addLayeredRenderingPlugin(...)` 注册，`render(layer, viewport)` 回调内绘制 |
| 可观察结果 | `starsector.log`（编译/链接失败打 ERROR）+ 战斗画面（渐变四边形）| 本页预期结果 |

## GLSL 类型速查

以下类型全部能在本地成熟实例的 shader 文件里直接指认：

| 类别 | 类型 | 实例用法 |
|---|---|---|
| 标量 | `float` / `int` / `bool` | `float axis = clamp(uv.x, 0.0, 1.0);` |
| 向量 | `vec2` / `vec3` / `vec4`（另有 `ivec*` / `bvec*`）| `uniform vec4 coreColor;`；`vec2` 遍布坐标计算 |
| 矩阵 | `mat2` / `mat3` / `mat4` | `uniform mat4 modelMatrix;` |
| 采样器 | `sampler2D` | `uniform sampler2D tex;`（GraphicsLib `bloom1-5.frag`）|
| 常量 | `const` | `const float TIP_WIDTH = 0.35;` |
| 内置输出 | `gl_Position`（顶点）/ `gl_FragColor`（片元）| `bloom1.vert`；`bloom1-5.frag` |
| 内置输入 | `gl_FragCoord`（片元窗口坐标）、`gl_TexCoord[0]`（内置 varying）| GraphicsLib `pre.frag` |

要点：

- **构造器与 swizzle**：`vec2(0.5, 0.5)`、`vertex * 0.5 + vec2(0.5, 0.5)`；swizzle 用 `.xy`（或 `.rgba` / `.stpq` 两套别名），可读可写，如 `vec4(c.bg, K.wz)`（rgb2hsv 的 swizzle 示例）。
- **运算是分量级的**：`vec3 * vec3` 逐分量相乘；`mix` / `smoothstep` / `clamp` / `pow` / `fract` / `abs` / `texture2D` 等内置函数遍布成熟实例。
- **限定符（qualifier）决定数据流方向**：GLSL 1.10/1.20 方言里是 `attribute`（每顶点输入）、`uniform`（绘制级常量）、`varying`（顶点输出 → 片元输入）、`const`（编译期常量）。GLSL 1.30+（要求 GL 3.0）改为 `in` / `out` / `layout`——`#version 430` shader 用 `layout (location = 0) in vec2 vertex;`、`out vec2 fragUV;`、`out vec4 fragColor;`。
- 430 方言还带来 uniform 块（`layout (std140, binding = 0) uniform BUtilGlobalData {...}`）——uniform 块的布局与提交方式由后续专题展开。

## 坐标语义

### 顶点坐标：从 attribute 到 gl_Position

attribute 里的位置数据**没有固有语义**——它是什么坐标，取决于提交方与投影约定。RC8 的本地实例里可见三种约定：

| 约定 | 含义 | 本地实例 |
|---|---|---|
| 世界坐标（战斗世界单位）| 分层渲染回调中提交世界单位几何；投影矩阵把世界坐标映射到裁剪空间 | 本页最小例：`glOrtho(viewport)` + `gl_ModelViewProjectionMatrix`；GraphicsLib `renderForeground` 的 `glOrtho(viewport.getLLX(), ...)`（`org.dark.shaders.util.ShaderLib`）|
| 裁剪空间 [-1,1] | 顶点已经是裁剪坐标，直接写 `gl_Position` | `gl_Position = vec4(vertex, 0.0, 1.0);`（全屏 pass 通用写法）|
| 屏幕像素 | 左下角原点、Y 向上；`glOrtho(0, displayWidth, 0, displayHeight, -1, 1)` | GraphicsLib `beginDraw`（`org.dark.shaders.util.ShaderLib`）；`bloom1.vert` 用 `ftransform()` 应用固定管线矩阵 |

关键事实：

- 顶点着色器**必须**写出 `gl_Position`（裁剪空间坐标）。光栅化器按它做裁剪、视口变换，最终换算成窗口像素坐标。不写 `gl_Position` → 顶点位置未定义（见规则 2）。
- shader 程序**不吃固定管线的变换**——投影必须由 shader 自己应用：`gl_ModelViewProjectionMatrix`（内置 uniform，反映当前 `GL_PROJECTION × GL_MODELVIEW`）、`ftransform()`（等价于 `gl_ModelViewProjectionMatrix * gl_Vertex`，GraphicsLib `bloom1.vert` 用法），或从 BoxUtil 的 UBO（`BUtilGlobalData.gameViewport`）取游戏视口矩阵。
- 本页最小例在 `render()` 里按 GraphicsLib `renderForeground` 的做法**自行** `glOrtho(viewport)` （世界坐标正交投影），不赌引擎固定管线矩阵在 shader 下的状态——分层渲染专题页只实证了固定管线顶点提交路径，未承诺矩阵状态（见规则 12）。

### 片元坐标：varying 插值与 gl_FragCoord

- 片元着色器**收不到 attribute**；它收到的是**光栅化插值后的 varying**。插值按图元做透视校正（正交投影下退化为线性）——本页最小例的四边形渐变就是 `v_uv` 插值的直接表现。
- `gl_FragCoord`：内置片元输入，窗口像素坐标（左下角原点、Y 向上、像素中心在 `x+0.5`）；由 `gl_Position` 经视口变换得到。它是"顶点 → 片元"的第二条数据通道：顶点着色器写 `gl_Position`，片元着色器读 `gl_FragCoord`。
- 屏幕尺寸类参数：本地实例**不直接读 `gl_FragCoord` 的尺寸**，而是用 uniform 传——`uniform vec2 screen;` + `1.0 / screen.x` 做像素步长（`bloom1-5.frag`），因为 GraphicsLib 的 RTT 纹理尺寸（2048 起）与窗口尺寸不同；430 方言实例同理用 `fwidth(fragUV)` 做像素化尺度。
- 纹理坐标约定 [0,1]：GraphicsLib `drawScreenQuad` 的 tex coords 是 0..1 （`org.dark.shaders.util.ShaderLib`）；430 实例把 [-1,1] 顶点映射到 [0,1] （`fragUV = vertex * 0.5 + vec2(0.5, 0.5)`）。

## 顶点到片元的数据流

```
Java 侧                                   GPU 管线
顶点缓冲（位置 / UV）─────────────┐
                                 ↓
                           attribute（每顶点）
                                 ↓
  uniform（每绘制常量）──────→ 顶点着色器 ──→ gl_Position（裁剪空间）
  glGetUniformLocation +            │              │
  glUniform2f / glUniformMatrix4    └─── varying ──┤
                                                    ↓
                          图元组装 → 光栅化（裁剪 / 视口变换 / 插值）
                                                    ↓
                             片元着色器 ← varying（插值）+ gl_FragCoord
                                                    ↓
                                        gl_FragColor → 帧缓冲
```

三条输入通道：

1. **attribute（每顶点）**：来源是 Java 侧 `glVertexAttribPointer` + `glEnableVertexAttribArray`；位置必须在 `glLinkProgram` 前用 `glBindAttribLocation` 固定。成熟实例把位置、UV、颜色分别绑到 0/1/2。

::: warning 常见坑：attribute 提交必须走 VBO + long 偏移

游戏自带 LWJGL 的 `GL20.glVertexAttribPointer` 没有接收 `FloatBuffer` client 指针的重载（本单元用游戏 API 编译实证）—— 网上常见的"FloatBuffer 直接传"写法在本基线编译不过。正确做法是 `glBindBuffer(GL_ARRAY_BUFFER, vbo)` 后用 long 偏移，见本页完整最小例。

:::

2. **uniform（每绘制）**：一次绘制内所有顶点/片元共享。来源是 `glGetUniformLocation` + `glUniform*`，类型必须与 GLSL 声明匹配。GraphicsLib 一帧提交十几个 uniform（`org.dark.shaders.post.PostProcessShader`）。

::: tip uniform 名拼错时，返回值判 -1 是唯一早期诊断入口

`glGetUniformLocation` 拼错名字会返回 -1，后续 `glUniform*` 被静默忽略且没有任何日志（见故障分析 4）。给返回值做一次 -1 判断，成本极低、诊断价值高。

:::

3. **varying（逐片元插值）**：顶点着色器输出 → 光栅化插值 → 片元输入；**两侧必须同名同类型**（430 方言里 `fragUV` 在 `.vert` 的 `out` 与 `.frag` 的 `in` 成对出现；GLSL 110 里同名 `varying` 声明）。

特殊通道与输出：

- `gl_Position`（顶点输出）→ `gl_FragCoord`（片元输入）：第二条通道，携带的是变换后的窗口坐标，不是 attribute 原值。
- 输出通道：GLSL 110 方言里片元着色器**必须写 `gl_FragColor`**；430 方言实例固定输出名为 `fragColor`。

## Starsector 中 shader 的加载与使用上下文

- **LWJGL**：游戏自带 `Starsector\starsector-core\lwjgl.jar` 与 `lwjgl_util.jar`，MOD 代码直接调用 `org.lwjgl.opengl.GL20` 等（GraphicsLib 的 import 全部来自这两个 jar）。 `glShaderSource` 可直接传 String（GraphicsLib `org.dark.shaders.util.ShaderLib` 同款调用）。
- **能力基线**：OpenGL 2.0 是 GLSL 可用下限。GraphicsLib `ShaderLib.init()` 检测 `GLContext.getCapabilities().OpenGL20`，不满足时置 `shadersAllowed = false` 并打 ERROR `GPU does not support OpenGL 2.0! Shaders disabled!`（`org.dark.shaders.util.ShaderLib`）。MOD 插件在 `onApplicationLoad` 里 `ShaderLib.init()` 后先查门禁再注册光效数据。

::: caution 使用 shader 代码前必须通过能力门禁

`areShadersAllowed()` 返回 false 时不得使用任何 shader 代码——其 javadoc 是硬措辞：**"Do not use shader code if this returns false! Otherwise the game will crash!"**（规则 1）。

:::

- **加载**：`Global.getSettings().loadText(String)` 读取 MOD 内文本（`com.fs.starfarer.api.SettingsAPI`），路径必须用 `/`（javadoc 原文 "Use / instead of \ in paths for compatibility (required to work on OS X and Linux)"）。加载与日志处理的完整做法由后续单元展开；GraphicsLib 的用法是 `Global.getSettings().loadText("data/shaders/post/pre.vert")`（`org.dark.shaders.post.PostProcessShader`）。
- **编译与链接**：`glCreateShader → glShaderSource → glCompileShader`，随后检查 `GL_COMPILE_STATUS`；`glCreateProgram → glAttachShader → glLinkProgram`，随后检查 `GL_LINK_STATUS`；失败时用 `glGetShaderInfoLog` / `glGetProgramInfoLog` 取日志并删除对象（GraphicsLib `createShader` 编译失败抛 `"Error creating shader: " + 日志`；`loadShader` 链接失败删 program 返回 0）。编译错误日志包含行号与原因——**不检查状态，失败就不可见**（规则 7）。
- **使用**：`glUseProgram(program)` → `glUniform*` / 顶点属性绑定 → `glDrawArrays` → `glUseProgram(0)`（GraphicsLib `exitDraw` 同款收尾）。渲染回调里自行管理 GL 状态（S07-P05 建议 8 延续）。
- **资源生命周期**（S07-P05 规则 2/3 延续）：程序对象在 `cleanup()` 里 `glDeleteProgram` 并复位句柄；战斗结束 `destroyInstance` 不调 `cleanup()`，必须 `advance()` 主动失效驱动引擎移除。
- **方言**：GLSL 110 是 GL 2.0 上下文的标准方言（GraphicsLib 全部 shader 用 `#version 110`）。GLSL 130+ 需要 GL 3.0+；现代 430 实例用 `#version 430`（`layout` / `std140` / UBO 等核心特性），其内部规范要求"版本声明必须统一为 `#version 430`"——这是**实例内部标准**（面向现代 GPU），不是游戏基线。

::: info 为什么基线是 #version 110 而不是 430

GLSL 版本与 OpenGL 版本绑定：`#version 110` 是 GL 2.0 上下文的标准方言（GraphicsLib 全系同款），也是游戏基线硬件普遍可用的版本；`#version 430` 需要现代 GPU（GL 4.3），在低版本上下文声明必然编译失败。教程以"基线硬件可运行"为准则，430 只作为现代实例的内部标准出现（规则 9）。

:::

## 必须遵守的规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | 使用 GLSL 前做能力门禁：OpenGL 2.0 以下不得使用 shader 代码（GraphicsLib `areShadersAllowed()` javadoc 原文 "Do not use shader code if this returns false! Otherwise the game will crash!"）| 无 GL 2.0 上下文调用 GL20 编译 API：程序创建失败（返回 0）或渲染异常/崩溃 |
| 2 | **必须** | 顶点着色器必须写 `gl_Position`，且值是裁剪空间坐标 | 顶点位置未定义：图形不出现或几何损坏 |
| 3 | **必须** | attribute 位置必须在 `glLinkProgram` 前用 `glBindAttribLocation` 固定；之后 `glVertexAttribPointer` 用同一位置 | 链接器可自由分配位置：顶点数据喂错属性，几何错乱/花屏/空白，且不同驱动表现不同 |
| 4 | **必须** | varying 在顶点与片元两侧必须同名同类型 | 片元读到未定义值（链接器警告）：颜色恒为 0/黑或花屏 |
| 5 | **必须** | 片元着色器必须写 `gl_FragColor`（GLSL 110 方言输出）| 输出未定义：颜色随意或黑屏 |
| 6 | **必须** | uniform 名称与类型必须与 GLSL 声明一致（大小写敏感）| `glGetUniformLocation` 返回 -1 或类型不匹配：赋值被静默忽略（GL 错误不报），参数不生效且无日志 |
| 7 | **必须** | 编译/链接后检查 `GL_COMPILE_STATUS` / `GL_LINK_STATUS`，失败时读取信息日志并删除对象 | 错误程序继续使用：内容不出现或渲染异常，且失败原因不可见（日志里才有行号与原因）|
| 8 | **必须** | shader 源文本路径用 `/` 分隔（`SettingsAPI.loadText` javadoc 原文）| `\` 路径在 OS X / Linux 上无法加载（IOException）|
| 9 | **必须** | GLSL 版本声明必须与目标上下文匹配：`#version 130+` 需要 GL 3.0+；教程基线用 `#version 110` | 低版本上下文声明高版本：`glCompileShader` 后 `GL_COMPILE_STATUS = FALSE`，信息日志含 version 错误 |
| 10 | **必须** | GL 资源（program 等）在 `cleanup()` 释放；战斗结束 `destroyInstance` 不调 `cleanup()`，`advance()` 主动失效驱动引擎移除（S07-P05 规则 2/3 延续）| 程序对象随战斗泄漏；多场战斗后驱动报错/渲染异常 |
| 11 | **必须** | GL 命令只在 `render()` 回调内执行（S07-P05 规则 1 延续）| `advance()` 里的 GL 调用在渲染阶段之外：内容不出现/错帧/层序错乱 |
| 12 | 建议 | 渲染回调里自行设置投影矩阵（本页按 GraphicsLib `renderForeground` 做法 `glOrtho(viewport)`），不依赖引擎固定管线矩阵在 shader 下的状态 | 无确定结果：依赖引擎矩阵时位置/缩放错误或消失（引擎矩阵状态未承诺）|
| 13 | 建议 | `glGetUniformLocation` 返回值判 -1（诊断价值高；GraphicsLib 实例未判，但 -1 的静默行为是头号隐蔽故障）| 无功能错误，只有可诊断性差异 |
| 14 | 建议 | 参照实例规范：一个视觉参数只有一个所有者、颜色不硬编码、Java 只传外部状态、GLSL 内部形状/渐变/羽化自足 | 无功能错误；可维护性与风格问题 |

## 完整最小例

在既有 MOD 布局与战斗插件体系之上，本页新增两个 shader 资源与一个插件类（不新增任何注册项、不改动任何既有文件）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── shaders\
│       ├── GearIn_basic_shader.vert      # 本页新增
│       └── GearIn_basic_shader.frag      # 本页新增
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin              # 本页不改动
│       ├── GearInGlobalAccess           # 本页只消费
│       └── combat\
│           ├── GearInCombatStatePlugin        # 本页只消费
│           ├── GearInCombatLifecyclePlugin    # 本页只消费
│           ├── GearInLayeredRenderer          # 本页只消费
│           └── GearInBasicShaderRenderer      # 本页新增
└── jars\
```

shader 资源放在 `data\shaders\`（MOD 根目录相对路径，与 GraphicsLib `data/shaders/post/pre.vert` 同款布局）；shader 文本会随既有编译链路（`gradle jar`）原样打进 `jars\GearIndustry.jar`。

### GearIn_basic_shader.vert

GLSL 1.10 方言（`#version 110`，GL 2.0 上下文标准，GraphicsLib 全系同款）。`attribute` 每顶点输入、`uniform` 每绘制常量、`varying` 顶点输出：

```glsl
#version 110

attribute vec2 a_position;

attribute vec2 a_uv;

uniform vec2 u_offset;

varying vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(a_position + u_offset, 0.0, 1.0);
}
```

### GearIn_basic_shader.frag

片元着色器读插值后的 `v_uv`，写 `gl_FragColor`：

```glsl
#version 110

varying vec2 v_uv;

void main() {
    gl_FragColor = vec4(v_uv.x, v_uv.y, 0.0, 1.0);
}
```

`v_uv` 在四边形内连续插值：红分量随水平位置、绿分量随垂直位置变化——画面上的红→绿渐变就是"顶点数据 → varying → 片元"数据流的直接表现。

扩展练习：把 `gl_FragColor` 改为 `vec4(gl_FragCoord.xy / 800.0, 0.0, 1.0)`，可观察到片元窗口坐标（左下原点）随屏幕位置变化。

### GearInBasicShaderRenderer

Java 接入：在分层渲染插件通道上加载、编译、链接程序，每帧提交 uniform 并绘制四边形。源码与 fixture 逐字一致（`tutorial-fixtures\units\S14-P01\src\main\java\data\scripts\gearin\combat\`）：

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
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 完整最小例：GLSL 顶点到片元数据流（attribute / uniform / varying / gl_Position / gl_FragColor）。
 *
 * 数据流事实（0.98a-RC8）：
 * - 顶点着色器输入分两类：attribute（每顶点，Java 侧 VBO + glVertexAttribPointer
 *   喂入，位置必须在 glLinkProgram 前用 glBindAttribLocation 固定）与 uniform（本次
 *   绘制内所有顶点共享，Java 侧 glGetUniformLocation + glUniform* 提交）；
 * - 顶点着色器输出 gl_Position（裁剪空间坐标）与 varying（每顶点写出、光栅化时在
 *   片元间插值）；片元着色器读插值后的 varying 并写 gl_FragColor（GLSL 1.10 方言）；
 * - 本页 render() 里按 GraphicsLib renderForeground 的姿势（org.dark.shaders.util.ShaderLib 第
 *   1485-1489 行）用 glOrtho(viewport) 设置世界→屏幕正交投影，顶点着色器用
 *   gl_ModelViewProjectionMatrix 应用它；
 * - 顶点数据走 VBO + long 偏移：游戏自带 LWJGL 的 GL20.glVertexAttribPointer
 *   没有 FloatBuffer client 指针重载（本单元用游戏 API jar 编译实证），必须
 *   glBindBuffer(GL_ARRAY_BUFFER, vbo) 后用 long 偏移；
 * - 能力基线：GLSL 需要 OpenGL 2.0 上下文（GraphicsLib ShaderLib.init() 以
 *   capabilities.OpenGL20 作为 shader 门禁，第 602-607 行；areShadersAllowed() 的
 *   javadoc 硬措辞 "Do not use shader code if this returns false! Otherwise the
 *   game will crash!"，第 197-198 行）；
 * - 编译/链接失败检查：GL_COMPILE_STATUS / GL_LINK_STATUS 为 FALSE 时读取
 *   glGetShaderInfoLog / glGetProgramInfoLog 并删除对象（GraphicsLib createShader
 *   第 1020-1041 行、loadShader 第 808-846 行同款姿势）；
 * - 资源生命周期沿用 S07-P05 硬规则 2/3：GL 资源在 cleanup() 释放；战斗结束
 *   destroyInstance 不调用 cleanup()，插件在 advance() 里主动失效（isExpired() ->
 *   true）驱动引擎移除；本示例设 LIFETIME 秒寿命后自清；
 * - 插件、引擎引用禁止跨战斗缓存（S06-P03 / S07-P04 / S07-P05 同规）。
 */
public class GearInBasicShaderRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInBasicShaderRenderer.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /，SettingsAPI.loadText javadoc）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_basic_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_basic_shader.frag";

    /** 演示四边形半边长（世界单位）。 */
    private static final float QUAD_HALF_SIZE = 60f;

    /** 演示寿命（秒）：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 20f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    private int program = 0;
    private int vboId = 0;
    private int uOffsetLocation = -1;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (!createProgram() || !buildQuad()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（S07-P05 同款姿势）
        LOG.info("GearIn basic shader init: program=" + program + ", vbo=" + vboId
                + ", u_offset=" + uOffsetLocation);
    }

    /** 加载、编译、链接 GearIn_basic_shader 的 vert/frag，成功返回 true。 */
    private boolean createProgram() {
        String vertSource;
        String fragSource;
        try {
            vertSource = Global.getSettings().loadText(VERT_PATH);
            fragSource = Global.getSettings().loadText(FRAG_PATH);
        } catch (IOException e) {
            LOG.error("GearIn basic shader: loadText failed: " + e.getMessage(), e);
            return false;
        }

        int vert = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        if (vert == 0) {
            return false;
        }
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);
        if (frag == 0) {
            GL20.glDeleteShader(vert);
            return false;
        }

        program = GL20.glCreateProgram();
        if (program == 0) {
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            return false;
        }

        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        // attribute 位置必须在链接前固定，之后 glVertexAttribPointer 用同一位置
        GL20.glBindAttribLocation(program, 0, "a_position");
        GL20.glBindAttribLocation(program, 1, "a_uv");
        GL20.glLinkProgram(program);

        // 链接完成后着色器对象即可删除：程序已持有编译结果
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn basic shader: link failed: " + getProgramLog(program));
            GL20.glDeleteProgram(program);
            program = 0;
            return false;
        }

        uOffsetLocation = GL20.glGetUniformLocation(program, "u_offset");
        return true;
    }

    /** 编译单个 shader 并检查 GL_COMPILE_STATUS；失败时删除对象并返回 0。 */
    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn basic shader: glCreateShader returned 0 (type=" + type + ")");
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn basic shader: compile failed: " + getShaderLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static String getShaderLog(int shader) {
        return GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
    }

    private static String getProgramLog(int program) {
        return GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH));
    }

    /** 两个三角形组成的四边形：6 个顶点，位置与 UV 交错写入一个 VBO。 */
    private boolean buildQuad() {
        float half = QUAD_HALF_SIZE;
        FloatBuffer quadBuffer = BufferUtils.createFloatBuffer(6 * FLOATS_PER_VERTEX);
        quadBuffer.put(new float[]{
                -half, -half, 0f, 0f,
                half, -half, 1f, 0f,
                half, half, 1f, 1f,
                -half, -half, 0f, 0f,
                half, half, 1f, 1f,
                -half, half, 0f, 1f
        });
        quadBuffer.flip();

        vboId = GL15.glGenBuffers();
        if (vboId == 0) {
            LOG.error("GearIn basic shader: glGenBuffers returned 0");
            return false;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return QUAD_HALF_SIZE * 2f;
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        elapsed += amount;
        if (elapsed >= LIFETIME) {
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()（S07-P05 硬规则 3）
            return;
        }
        // 剔除依据是承载实体的位置（S07-P05 硬规则 4）：跟随玩家舰船
        entity.getLocation().set(engine.getPlayerShip().getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        LOG.info("GearIn basic shader cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == 0 || vboId == 0) {
            return;
        }
        // 世界→屏幕正交投影：按 GraphicsLib renderForeground 姿势自行设置
        // （glOrtho(viewport) 以世界单位建投影，org.dark.shaders.util.ShaderLib 第 1485-1489 行），
        // 顶点着色器用 gl_ModelViewProjectionMatrix 应用它；矩阵成对 push/pop 复原
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(),
                viewport.getLLY(), viewport.getLLY() + viewport.getVisibleHeight(), -1f, 1f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL20.glUseProgram(program);

        // uniform：本次绘制内所有顶点/片元共享，提交为玩家舰船当前位置（每帧更新）
        if (uOffsetLocation >= 0) {
            Vector2f center = engine.getPlayerShip().getLocation();
            GL20.glUniform2f(uOffsetLocation, center.x, center.y);
        }

        // attribute：VBO 绑定 + long 偏移（游戏自带 LWJGL 的 glVertexAttribPointer
        // 没有 FloatBuffer client 指针重载，必须走 VBO + long 偏移姿势）
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
    }
}
```

对应关系：

- `init` 三件事：`super.init(entity)` 存承载实体、`engine = Global.getCombatEngine()`（每场战斗新取，S06-P03/S07-P05 同规）、`createProgram()` + `buildQuad()` 建 GL 资源；失败即 `valid = false` 等引擎移除。 `advance(0f)` 立即同步位置（S07-P05 同款做法）。
- `createProgram` 是数据流入口：`loadText` 读两个源文件（规则 8 的路径做法）→ `compileShader` 逐个编译并查 `GL_COMPILE_STATUS`（规则 7）→ `glBindAttribLocation` 在链接前固定位置 0/1（规则 3）→ 链接并查 `GL_LINK_STATUS`（规则 7）→ `glGetUniformLocation` 取 `u_offset`（规则 6）。
- `render` 四段：自行 `glOrtho(viewport)` 世界→屏幕正交投影（建议 12，矩阵成对 push/pop）→ `glUseProgram` + `glUniform2f` 提交 uniform → VBO 绑定 + `glVertexAttribPointer`（long 偏移）+ `glDrawArrays(GL_TRIANGLES)` 绘制 6 顶点四边形 → 解除绑定、`glUseProgram(0)`（S07-P05 建议 8 的收尾做法）。
- `advance`：`LIFETIME` 秒后 `valid = false` 主动失效（规则 10）；位置同步 `entity.getLocation().set(...)`（S07-P05 规则 4）。
- `cleanup`：`glDeleteBuffers` 删 VBO、`glDeleteProgram` 删程序，句柄全部复位（规则 10）。

注册片段（工程片段——本页不改动战斗生命周期插件的任何既有文件，只演示使用方调用；把下面一行加进战斗生命周期插件 `GearInCombatLifecyclePlugin` 的 `init(CombatEngineAPI engine)` 方法末尾）：

```java
    // 工程片段：战斗生命周期插件 init(CombatEngineAPI engine) 末尾追加
    engine.addLayeredRenderingPlugin(new GearInBasicShaderRenderer());
```

与分层渲染专题的注册片段同一做法；渲染层接入的完整设计（开关、常驻、性能）由后续专题展开。

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用既有编译链路）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/shaders/GearIn_basic_shader.vert`、 `data/shaders/GearIn_basic_shader.frag` 与 `data/scripts/gearin/combat/GearInBasicShaderRenderer.class`（可用 `jar tf` 核对）。

本页源码与 fixture 逐字一致（shader 两个文件在 `tutorial-fixtures\units\S14-P01\src\main\resources\data\shaders\`，Java 在 `tutorial-fixtures\units\S14-P01\src\main\java\data\scripts\gearin\combat\`）。

### 验证方案（基于本例）

前置数据：既有 MOD 布局与编译链路（S06-P01）、`GearIn_test` 测试战役（S03-P01）、战斗生命周期与分层渲染插件（S07-P04、S07-P05），加上本页的 `GearInBasicShaderRenderer` 与 `data\shaders\GearIn_basic_shader.vert/.frag`。

- 操作 1（fixture 资源校验）：在 Git Bash 执行
```text
gradle -p tutorial-fixtures validateShaders --no-daemon
``` 以 `BUILD SUCCESSFUL` 结束（退出码 0）。
- 操作 2（游戏构建）：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/shaders/GearIn_basic_shader.vert`、 `data/shaders/GearIn_basic_shader.frag`、 `data/scripts/gearin/combat/GearInBasicShaderRenderer.class`。
- 操作 3（战役冒烟）：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 `GearIn basic shader init: program=<id>, vbo=<id>, u_offset=<loc>`；没有由 `gear_in` 引起的 `ERROR`。
- 操作 4（画面）：观察玩家舰船中心 120 世界单位的红→绿渐变四边形：颜色连续、位于舰船与导弹之上。
- 操作 5（uniform 数据流）：移动舰船——四边形跟随；四边形内渐变样式不随移动改变。
- 操作 6（寿命与清理）：约 20 秒后四边形消失，`starsector.log` 出现 `GearIn basic shader cleanup`。
- 操作 7（暂停）：按空格暂停约 10 秒——四边形静止但可见；恢复后继续跟随。
- 操作 8（故障注入）：a) 删掉 `.vert` 的 `gl_Position` 行——四边形不出现或位置错乱（故障 1）；b) 把 `.vert` 的 uniform 名 `u_offset` 在 Java 侧拼错——四边形不跟随移动且无日志（故障 4）；c) 把 `#version 110` 改成 `#version 130`——编译失败，日志出现 `GearIn basic shader: compile failed:` 与 version 错误（故障 6）；d) 改回后确认恢复。

通过信号：操作 1–7 全部符合；操作 8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 资源（验收 G）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures validateShaders --no-daemon
``` 以 `BUILD SUCCESSFUL` 结束（退出码 0）。该任务检查 `units/S14-P01/src/main/resources` 下的所有 `.vert/.frag` 非空。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/shaders/GearIn_basic_shader.vert`、`data/shaders/GearIn_basic_shader.frag` 与 `data/scripts/gearin/combat/GearInBasicShaderRenderer.class`。
3. **战斗初始化**：进入 `GearIn_test` 战役（S03-P01）一场战斗，`starsector.log` 出现一行
```text
GearIn basic shader init: program=<id>, vbo=<id>, u_offset=<loc>
``` 没有由 `gear_in` 引起的 `ERROR`（编译/链接失败会在此处打 `GearIn basic shader: compile failed:` 或 `link failed:` 的 ERROR 行）。
4. **画面**：玩家舰船中心出现一个 120 世界单位见方的四边形，颜色从左下角到右上角红/绿渐变——四边形内颜色连续变化（红分量随水平位置、绿分量随垂直位置），这是 `v_uv` 逐片元插值的直接表现；四边形位于舰船与导弹之上（`ABOVE_SHIPS_AND_MISSILES_LAYER`，S07-P05 层序）。
5. **uniform 数据流**：舰船移动时四边形跟着移动（`u_offset` 每帧更新）；移动过程中四边形内的渐变样式不变（`varying` 相对局部坐标插值）。
6. **寿命与清理**：约 20 秒后四边形消失，`starsector.log` 出现一行
```text
GearIn basic shader cleanup
``` 即主动失效 → 引擎移除 → `cleanup()` 的正常链（规则 10 的可观察面）。同一场战斗内不再出现。
7. **暂停行为**：按空格暂停——四边形静止但可见（`advance` 不推进、渲染不暂停，S07-P05 同款）；恢复后继续跟随。
8. **故障注入**：见"实例驱动故障分析"第 1、4、6、9 条对应的注入动作与恢复。

静态自查：

- `GearInBasicShaderRenderer` 与 fixture 逐字一致（包括 import 顺序与注释）；
- `GearIn_basic_shader.vert/.frag` 与 fixture 逐字一致；
- 没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.combat`、类 `GearInBasicShaderRenderer`、资源 `data/shaders/GearIn_basic_shader.vert/.frag`、日志前缀 `GearIn basic shader`）；
- 除本页目录与 fixture 目录外没有改动任何共享文件。

## 实例驱动故障分析

1. **顶点着色器不写 `gl_Position`**。触发：把 `gl_Position = ...` 一行删掉或注释掉。可观察：顶点位置未定义（GLSL 规范），四边形不出现或出现在随机/错乱位置。修正：必须写 `gl_Position`，且值是裁剪空间坐标（规则 2）。
2. **attribute 位置未固定**。触发：删掉 `glBindAttribLocation` 两行，只留 `glVertexAttribPointer`。可观察：链接器自由分配位置——位置与 UV 数据喂错属性，四边形形状错乱、拉伸或空白；且部分驱动正常、部分驱动花屏（分配结果实现相关），是最隐蔽的一类故障。修正：链接前固定位置，指针用同一位置（规则 3）。
3. **varying 两侧不一致**。触发：把 `.frag` 里的 `v_uv` 改名（如 `v_uv2`）而 `.vert` 不改。可观察：片元读到未定义值（链接器警告），渐变消失、颜色恒为 0/黑或花屏。修正：两侧同名同类型（规则 4）。
4. **uniform 名拼错**。触发：把 `"u_offset"` 改成 `"uOffset"`。可观察：`glGetUniformLocation` 返回 -1，`glUniform2f` 被静默忽略（GL 错误不报）——四边形不跟随舰船移动、停在初始位置，且**没有任何日志**。修正：核对名称/类型（规则 6），建议判 -1（建议 13）。
5. **不检查编译/链接状态**。触发：注释掉 `compileShader` 里的 `GL_COMPILE_STATUS` 检查与日志读取。可观察：错误程序继续使用——内容不出现，失败原因完全不可见；而检查日志时，语法错误的行号与原因（如 `ERROR: 0:5: 'xxx' : syntax error`）就在 `starsector.log` 里。修正：编译/链接后必查状态与日志（规则 7）；完整日志处理在后续专题展开。
6. **方言版本与硬件不匹配**。触发：把 `#version 110` 改成 `#version 130` 或更高。可观察：在 GL 2.0 基线上下文编译失败——`GL_COMPILE_STATUS = FALSE`，信息日志含 version 错误（GLSL 版本与 GL 版本绑定）。修正：教程基线用 `#version 110`；需要 430 特性（`layout`/`std140`/UBO）时按目标硬件声明（430 方言做法，规则 9）。
7. **GL 资源不释放**。触发：`cleanup()` 里不删 program，或指望"战斗结束自动清理"。可观察：程序对象随战斗泄漏（`destroyInstance` 不调 `cleanup()`，S07-P05 规则 3）；多场战斗后驱动报错、渲染异常或卡顿。修正：`advance()` 主动失效 + `cleanup()` 里 `glDeleteProgram` 并复位句柄（规则 10，同款做法）。
8. **在 `advance()` 里提交 GL 命令**。触发：把 `glUseProgram` / `glDrawArrays` 放进 `advance()`。可观察：`advance()` 属于推进阶段（S07-P05），命令在渲染阶段之外执行——内容不出现、错帧或层序错乱。修正：GL 命令只在 `render()` 回调（规则 11）。
9. **投影依赖"引擎已设置好"的矩阵**。触发：用 `ftransform()` 或 `gl_ModelViewProjectionMatrix` 但不自行设置投影矩阵。可观察：分层渲染专题页只实证了固定管线提交路径，未承诺 shader 下的引擎矩阵状态——四边形位置/缩放错误或消失。修正：按本页做法自行 `glOrtho(viewport)`（建议 12，GraphicsLib `renderForeground` 同款）。
10. **把 430 方言写法直接搬进 110**。触发：把 430 方言的 `in vec2 fragUV; out vec4 fragColor;` 写法抄进 `#version 110` 文件。可观察：编译失败（`in`/`out` 不是 110 关键字，信息日志报语法错误）。修正：110 方言用 `varying` 与 `gl_FragColor`；要搬 430 写法就连同版本声明与硬件前提一起搬（规则 9）。

## 总结

本页建立了 GLSL 的最简流程：从 `#version 110` 的类型与限定符体系，到 attribute / uniform / varying 三条数据通道，再到顶点坐标三种约定与 `gl_Position` → `gl_FragCoord` 的变换链，最后落在一个能编译、能链接、能出画面的四边形渲染插件上；

能力门禁、编译/链接状态检查、`/` 路径与资源清理这几条必须遵守的规则，决定了这个完整流程在游戏内是否可观察、可诊断。

这个完整流程的每一环——类型、坐标、数据流、加载做法——都是后续更复杂 shader 工作的地基：加载与编译日志的完整处理、uniform 与纹理/矩阵的提交方式、渲染层接入与性能设计，都只是在本页这一条数据流上补充实现细节与工程化手段。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadText(String)` 读取 MOD 内文本（shader 源文件），路径必须用 `/`。
- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin`：分层渲染插件接口，shader 绘制的所属对象（`BaseCombatLayeredRenderingPlugin` 提供基础实现）。
- `com.fs.starfarer.api.combat.ViewportAPI`：渲染回调中的视口信息（`getLLX`/`getLLY`/`getVisibleWidth`/`getVisibleHeight`），用于构造正交投影。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`/`getPlayerShip()` 等战斗状态查询。
- `org.lwjgl.opengl.GL20`：shader 编译/链接、程序、uniform 与顶点属性 API（`glCreateShader`/`glCompileShader`/`glLinkProgram`/`glGetUniformLocation`/`glUniform*`/`glVertexAttribPointer`）。
- `org.lwjgl.opengl.GL15`：VBO API（`glGenBuffers`/`glBindBuffer`/`glBufferData`/`glDeleteBuffers`）。
- `org.lwjgl.opengl.GL11`：基础绘制与矩阵 API（`glDrawArrays`/`glOrtho`/矩阵 push/pop）。
- `org.dark.shaders.util.ShaderLib`（GraphicsLib）：`areShadersAllowed()` 能力门禁与 `createShader`/`loadShader` 参考实现。
- `data/shaders/GearIn_basic_shader.vert` / `data/shaders/GearIn_basic_shader.frag`：本页示例 shader 资源（MOD 根目录相对路径）。
- `data/shaders/post/pre.vert`（GraphicsLib）：`#version 110` 方言的参考文件（相对 GraphicsLib MOD 根目录）。
- `Starsector/starsector-core/starsector.log`：编译/链接失败与清理日志的可观察落点。
