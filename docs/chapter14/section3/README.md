# 向着色器提交绘制数据

本节向着色器提交矩阵、uniform 与纹理数据，在战斗画面绘制一个跟随玩家舰船移动的发光四边形。示例使用 BoxUtil 的 `ShaderProgram` 管理程序对象，资源释放与性能控制留到下一节。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 解释 BoxUtil `ShaderProgram` 的完整使用流程：`ShaderUtil.createShaderVFFormPath(名称, vert 路径, frag 路径)` 创建 → `new ShaderProgram(programId)` 包装 → 创建后立即保存 uniform/UBO location → 渲染前 `isValid()` 检查 → `active()`/`close()` 配对提交 → `cleanup()` 里 `delete()`；
- 设置 UBO 全局矩阵块 `BUtilGlobalData` 的 GLSL 声明与 Java 侧绑定，并解释其版本要求 (uniform block 需要 GLSL 1.40+/GL 3.1+，BoxUtil 全管线基线 GL 4.3)；
- 区分 uniform 提交的三种形态：标量/向量 (`glUniform1f/2f/4f`)、采样器 (`glUniform1i` + 纹理单元)、矩阵 (`glUniformMatrix4` + 列主序 16 float `FloatBuffer`)；
- 设置纹理提交链：`SettingsAPI.getSprite` → `SpriteAPI.getTextureId()` → `glActiveTexture(GL_TEXTURE0)` + `glBindTexture(GL_TEXTURE_2D, id)` → `glUniform1i(texLoc, 0)` → 片元侧 `texture(tex, uv)`；
- 解释矩阵提交约定：`glUniformMatrix4(loc, false, buffer)` 的 `transpose` 必须为 `false`、缓冲为列主序 16 float、1D 刚性变换 (旋转+平移) 布局；
- 从症状定位典型故障：uniform 名拼错 (-1 静默)、采样器/纹理单元不配对、矩阵 `transpose=true`、UBO 块名拼错或未绑定、GLSL 版本与上下文不匹配、可用性判据或 `isValid()` 检查用错、跨战斗缓存程序句柄。

## 前置知识

本节内容建立在以下前置知识之上，建议先掌握：

- GLSL 110/430 方言、`attribute`/`uniform`/`varying`、编译/链接检查链与能力门禁 (C14-S01)：本节把 430 方言与 BoxUtil 的 GL 4.3 基线连接起来；其 "uniform 名/类型必须一致" 这条必须遵守的规则在本节延续为提交纪律。
- `.vert/.frag` 路径约定 (`data/shaders/`、`/` 分隔、UTF-8 无 BOM)、加载与编译日志、资源生命周期四环节 (C14-S02)：本节沿用 —— 每场战斗 `init` 内创建、`cleanup()` 内释放、禁止跨战斗缓存。
- BoxUtil 初始化两段式 (`initPre` → `initLater`) 与可用性判据 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(C12-S06)：本节的创建门禁直接复用；`isBaseGL43Supported()` 只是 GL 能力探测，不能替代 `ShaderCore.isValid()`。
- 硬依赖 (C12-S01)：GearIndustry 对 BoxUtil 的硬依赖声明 (`mod_info.json` 的 `dependencies`，C12-S06)。
- 分层渲染插件通道 (C07-S06)：本节渲染所属对象 ——`render(layer, viewport)` 回调内绘制、`advance()` 主动失效、`cleanup()` 释放 GL 资源。
- `Global` 生命周期 (C06-S03)：禁止跨战斗缓存引擎引用与渲染对象 —— 本节的 `ShaderProgram`/VBO 同规。

## 文件与 API 链路

本节要接的对象很多，先给一张总表：shader 资源、BoxUtil 程序入口、uniform/UBO 提交与渲染通道归属都按环节登记，后续小节逐个展开。

| 环节 | 文件 / API | 说明 |
|---|---|---|
| shader 源文件 | `data/shaders/GearIn_shader_visual.vert` / `.frag`(本节所有权)| MOD 根目录相对路径，`/` 分隔，UTF-8 无 BOM(路径与编码纪律，C14-S02)|
| 程序创建 | `org.boxutil.util.ShaderUtil.createShaderVFFormPath(String 名称, String vert 路径, String frag 路径)` → `int` | BoxUtil 封装入口：按路径加载、编译、链接并返回程序 id(成熟实例消费实证)；内部行为不读取 |
| 程序包装 | `org.boxutil.units.standard.ShaderProgram(int programId)` | 封装 GL program 句柄、uniform/UBO location 查询、`active()`/`close()` 与 valid 状态 (实例规范)|
| location 查询 | `getUniformIndex(String)` / `getUBOIndex(String 块名, int 绑定点)`；公共字段 `location` / `uboLocation`(`int[]`)| 创建后立即保存 (实例规范)；块名与 GLSL 逐字一致 |
| 矩阵 UBO | `org.boxutil.manager.ShaderCore.getMatrixUBOBinding()` / `isValid()` | BoxUtil 全局矩阵 UBO 的绑定点入口 (实例规范)；`BUtilGlobalData` 块由此绑定 |
| 配置开关 | `org.boxutil.config.BoxConfigs.isShaderEnable()` | 与 `ShaderCore.isValid()` 共同决定 shader 路径可用性 (既有可用性判据，C12-S06)|
| 纹理获取 | `SettingsAPI.getSprite(String filename)`(`com.fs.starfarer.api.SettingsAPI`，文件必须已加载)/ `getSprite(String category, String key)`(读 settings.json 的 `graphics` 分类)| 本节示例用引擎启动期必载的 `graphics/fx/particlealpha32sq.png`(引擎启动期加载队列实证；既有示例同款做法，C12-S06)|
| 纹理提交 | `SpriteAPI.getTextureId()` / `bindTexture()`；`GL13.glActiveTexture` + `GL11.glBindTexture(GL_TEXTURE_2D, id)` + `GL20.glUniform1i(texLoc, 0)` | 纹理单元绑定与采样器提交 (成熟实例同款做法)|
| uniform 提交 | LWJGL `GL20.glUniform1f/2f/4f/Matrix4` | 标量/向量/矩阵三形态 (成熟实例同款做法)|
| 矩阵构造 | 自建 16 float 列主序 `FloatBuffer`(旋转+平移)| 成熟实例 `maintainAnchoredFloatBuffer` 实证布局 |
| 顶点提交 | VBO + `glVertexAttribPointer`(long 偏移)+ `glDrawArrays` | 沿用既有自建 VBO 做法；`#version 430` 的 `layout (location = N)` 在 GLSL 内固定位置，替代 110 方言的链接前 `glBindAttribLocation` |
| 渲染所属对象 | `CombatLayeredRenderingPlugin`(分层渲染通道，C07-S06)| `engine.addLayeredRenderingPlugin(...)` 注册，`render(layer, viewport)` 回调内绘制 |
| 可观察结果 | `starsector.log` + 战斗画面 | init/门禁/清理日志行；玩家舰船位置随动的纹理滚动发光四边形 |

### 四位置完整流程

本节示例仍是无注册表 ID 的资源 + 代码对象：shader 文件路径、Java 常量、运行使用方、可观察结果四处必须一致：

| 位置 | 本节取值 |
|---|---|
| 资源文件 | MOD 内 `data/shaders/GearIn_shader_visual.vert` / `.frag` |
| 加载入口 | `ShaderUtil.createShaderVFFormPath(名称, "data/shaders/GearIn_shader_visual.vert", "data/shaders/GearIn_shader_visual.frag")`(Java 常量 `VERT_PATH`/`FRAG_PATH` 与资源文件路径逐字一致)|
| 运行使用方 | `GearInShaderVisual`(分层渲染通道注册，`render` 内 `program.active()` + 提交 + 绘制)|
| 可观察结果 | 日志行含 `vert=data/shaders/GearIn_shader_visual.vert`、`frag=...`；画面出现随舰船移动的纹理滚动发光四边形 |

另有两条 "名称一致性" 完整流程：UBO 块名 `BUtilGlobalData` 在 GLSL 声明 (`.vert`) 与 Java 常量 `UBO_BLOCK_NAME` 必须逐字一致；六个 uniform 名 (`modelMatrix`/`size`/`time`/`tex`/`coreColor`/`fringeColor`) 在 GLSL 声明与 Java `getUniformIndex` 参数也必须逐字一致。

## 程序对象：从裸 GL20 到 BoxUtil ShaderProgram

前两页用裸 LWJGL `GL20` 完成 "编译 → 链接 → 状态检查 → 手动记录 uniform location"，并手工 `glOrtho(viewport)` 做世界→屏幕投影。本节换用 BoxUtil 的 `ShaderProgram`，把这两段手工流程都收起来：程序句柄、uniform/UBO location 查询、`active()/close()` 状态与 valid 状态由它一个对象封装 (实例规范)，投影矩阵也由 BoxUtil 的全局矩阵 UBO(`BUtilGlobalData` 块) 每帧供给。

BoxUtil 是依赖库，本节只陈述其公开用法，写法与成熟实例的调用形态逐行同构 (见 "实例对照")。

### 创建 (成熟实例完全同构)

两个成熟实例的创建代码逐行同构：

```java
// 成熟实例创建程序并保存 location 的同构代码（shader 路径为占位示例）
int programId = ShaderUtil.createShaderVFFormPath(this.getClass().getName() + "Blade",
        "data/shaders/visual_pass.vert",
        "data/shaders/visual_pass.frag");
bladeProgram = new ShaderProgram(programId);
bladeProgram.location = new int[] {
        bladeProgram.getUniformIndex("modelMatrix"),
        bladeProgram.getUniformIndex("size"),
        bladeProgram.getUniformIndex("time"),
        bladeProgram.getUniformIndex("fade"),
        bladeProgram.getUniformIndex("coreColor"),
        bladeProgram.getUniformIndex("fringeColor")
};
bladeProgram.uboLocation = new int[] {
        bladeProgram.getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding())
};
```

要点：

- `ShaderUtil.createShaderVFFormPath(名称, vert 路径, frag 路径)` 是 BoxUtil 的 "按路径创建程序" 入口 (另一个入口 `createShaderVF` 直接收源码字符串)。它返回 GL 程序 id，您把它包装进 `ShaderProgram`。其内部 (路径读取、编译链接、失败日志) 属 BoxUtil 实现，本节只陈述公开用法。
- **创建后立即查询并保存 uniform/UBO location**(实例规范 "`ShaderProgram` 创建后必须保存后续渲染所需的 uniform、attribute 或 UBO location")：`getUniformIndex(名)` 逐个取、`getUBOIndex(块名, 绑定点)` 取全局矩阵块，结果存进 `program.location` / `program.uboLocation` 公共数组字段。渲染时不再查询，直接按下标提交。
- 名称是 Java 与 GLSL 的约定：uniform 名、UBO 块名都必须与 GLSL 声明逐字一致 (大小写敏感，名称/类型一致性纪律的延续)。

### 使用与收尾

- 渲染前检查 `program.isValid()`—— 程序创建失败 (GPU 不支持、编译失败等) 时 `isValid()` 为 false，直接跳过绘制。
- 提交段夹在 `program.active()` 与 `program.close()` 之间；实例规范原文 "`ShaderProgram.active()` 后必须按 `ShaderProgram.close()` 关闭 program 状态"。
- `cleanup()` 里 `program.delete()`，句柄置空；每场战斗 `init` 重新创建，禁止跨战斗缓存 (沿用跨战斗缓存禁令)。
- 前页的编译/链接状态检查与 InfoLog 职责在 BoxUtil 封装内，MOD 侧不再逐行检查 ——**失败可见性取决于 `isValid()` 检查与 BoxUtil 自己的日志**；本节示例把 "程序无效" 写成 ERROR 日志并自废 (规则 3)。

::: caution 不得以 `isBaseGL43Supported()` 替代 `ShaderCore.isValid()`

—— GL 能力探测通过不代表 BoxUtil 内部初始化成功；BoxUtil shader 路径的可用性判据必须是 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(规则 1)。判据误判可用 → 程序创建后不渲染、无报错；`isShaderEnable()` 为 false 仍提交 → 关闭设置失去效果且白耗 GPU。

:::

## UBO：BUtilGlobalData 全局矩阵块

::: info 背景 (为什么是 UBO)

UBO(uniform buffer object) 是 uniform 的批量容器：一次写入一块数据，多个程序共享；块内成员的偏移由 `std140` 布局规则固定 (`mat4` 按 16 字节对齐、`vec4` 按 16 字节对齐)，因此 GLSL 声明必须与写入方布局一致。

:::

### GLSL 侧声明

两个成熟实例的通用顶点着色器声明同一块 (逐字相同)：

```glsl
layout (std140, binding = 0) uniform BUtilGlobalData
{
    mat4 gameViewport;
    vec4 gameScreenBorder;
};
```

这里把 `gameViewport` 当作**世界 → 屏幕矩阵**使用：顶点着色器先算局部坐标 (`a_position * size * 0.5`)，乘 `modelMatrix`(本节提交的模型矩阵) 得到世界坐标，再乘 `gameViewport` 得到裁剪坐标：

```glsl
gl_Position = gameViewport * vec4((modelMatrix * local).xy, 0.0, 1.0);
```

这就是 "UBO 取代手工投影" 的实质：前页建议的手动 `glOrtho(viewport)` 之所以存在，是因为裸 GL20 程序拿不到引擎的投影；BoxUtil 路径下投影矩阵由全局 UBO 每帧供给 (其填充来源属 BoxUtil 内部，本节不读取)，shader 程序直接取用。

`gameScreenBorder` 是块内第二个成员，两个实例都声明但本节示例不使用 (成员属于 BoxUtil 定义的块布局，声明必须保留以对齐)。

### Java 侧绑定

```java
program.uboLocation = new int[] {
        program.getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding())
};
```

- 块名必须与 GLSL 声明逐字一致 (大小写敏感)；`ShaderCore.getMatrixUBOBinding()` 返回 BoxUtil 全局矩阵 UBO 所在的绑定点 (实例规范："`org.boxutil.manager.ShaderCore`：提供 BoxUtil 全局矩阵 UBO 绑定入口")。
- 两种实例变体：430 文件 shader 在 GLSL 里写 `binding = 0` 限定符，330 内嵌 shader 不写 binding，两种情况 Java 侧都调用 `getUBOIndex`——**Java 侧 `getUBOIndex(块名, ShaderCore.getMatrixUBOBinding())` 是唯一的正式绑定动作**，本节示例的 GLSL 不写 `binding` 限定符，绑定点单一来源是 Java 侧。

### 版本要求 (规则 4)

uniform block 是 GLSL 1.40 引入的核心特性 (需要 GL 3.1+)；`layout(std140)` 从 1.40 可用；`binding` 限定符需要 GLSL 4.2+；attribute 的 `layout (location = N)` 需要 GLSL 3.30+(GL 3.3+)；`#version 430` 需要 GL 4.3+。

BoxUtil 全管线的能力基线是 GL 4.3(`isBaseGL43Supported()`)。因此：

- 本节 shader 必须用 `#version 430`(两个成熟实例的视觉 shader 同款)，不能沿用 `#version 110`——110 不支持 uniform block，声明即编译失败；
- 在低于 GL 4.3 的上下文使用本节示例 → `glCompileShader` 失败，InfoLog 含 version 错误；
- BoxUtil shader 路径的可用性以 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()` 为准 (规则 1)，不是 `isBaseGL43Supported()`。

## uniform 提交

uniform 是 "一次绘制内所有顶点/片元共享" 的常量 (数据流语义见前置知识)。本节的提交做法与前页相同 (`glGetUniformLocation`/`glUniform*` 家族)，区别是 location 来自 `ShaderProgram.getUniformIndex`，且出现了三种形态：

| 形态 | 调用 | 本节示例 |
|---|---|---|
| 标量 | `GL20.glUniform1f(loc, value)` | `time`(战斗总时间，暂停冻结)|
| 向量 | `GL20.glUniform2f(loc, x, y)` / `glUniform4f(loc, r, g, b, a)` | `size`(四边形世界尺寸)、`coreColor`/`fringeColor`(RGBA)|
| 矩阵 | `GL20.glUniformMatrix4(loc, false, FloatBuffer)` | `modelMatrix`(刚性变换)|
| 采样器 | `GL20.glUniform1i(loc, 0)` | `tex`(纹理单元 0)|

纪律 (名称/类型一致性纪律的延续)：

- **名称与类型必须与 GLSL 声明一致**(大小写敏感)：名称拼错 → `getUniformIndex` 返回 -1 → `glUniform*` 被静默忽略 (GL 错误不报)——uniform 不生效且无日志；
- 类型不匹配 (如 GLSL 声明 `float` 却提交 `glUniform2f`)→ 赋值失败/未定义，同样是静默路径；
- 提交时机在 `program.active()` 之后、`glDrawArrays` 之前 (绘制内的状态在 `close()` 后失效 —— 这也解释了为什么每帧都要重新提交)。

::: warning 名称/类型不匹配是静默失败

—— 名称拼错或类型不匹配时，`getUniformIndex` 返回 -1(或赋值失败/未定义)，`glUniform*` 调用被静默忽略：uniform 不生效且**没有任何日志**。GPU 不会对拼错的名字给出任何提示，画面只是安静地缺少这块效果；init 日志里打印的 location 值 (`u_*`) 能直接看出哪个是 -1，这是定位拼错的唯一线索 (见故障分析第 5 条)。

:::

## 纹理提交 (纹理单元绑定与采样)

纹理提交链：

```text
SettingsAPI.getSprite(路径) → SpriteAPI.getTextureId()
→ GL13.glActiveTexture(GL13.GL_TEXTURE0)     // 选定纹理单元 0
→ GL11.glBindTexture(GL11.GL_TEXTURE_2D, id) // 把纹理绑定到单元 0
→ GL20.glUniform1i(texLoc, 0)                // 采样器 tex 指向单元 0
→ 片元侧 texture(tex, uv) 采样
```

::: warning 采样器值必须等于纹理单元号

(规则 6)——`glUniform1i(texLoc, 0)` 的 `0` 与 `glActiveTexture(GL_TEXTURE0)` 的单元号必须一致。不一致 → 采样器指向的单元上没有绑定期望纹理 → 采样结果错误/黑，且无日志。

:::

要点：

- 纹理来源：`SettingsAPI.getSprite` 两个形态 —— 单参 `getSprite(String filename)` 要求 "文件必须已加载"(javadoc)，双参 `getSprite(String category, String key)` 读 settings.json 的 `graphics` 分类。本节示例用单参 + 引擎启动期必载的 `graphics/fx/particlealpha32sq.png`(引擎启动期把该纹理加入加载队列，与既有示例同款做法)，不引入新纹理资产。
- `SpriteAPI.getTextureId()` 返回 GL 纹理 id；`bindTexture()` 等价于 `glBindTexture(GL_TEXTURE_2D, getTextureId())`—— 两种提交做法都见诸实例 (`sprite.bindTexture()` 与 `getTextureId()` + `glBindTexture` 两条等价链)。
- shader 程序不吃固定管线纹理状态：`GL_TEXTURE_2D` 的当前绑定由我们自己设置；绘制结束后把绑定解回 0，避免影响后续绘制者的纹理状态。
- 片元侧采样：`texture(tex, uv)`(GLSL 1.30+ 语法；110 方言是 `texture2D`)。

## 矩阵提交 (uniform matrix4)

### 调用形态

`GL20.glUniformMatrix4(int location, boolean transpose, FloatBuffer matrix)`(LWJGL 2.x 签名)：

- **`transpose` 必须传 `false`**(规则 8)：`true` 表示上传前转置 —— 非列主序缓冲配 `true` 才等价于正确结果，配合列主序数据会得到转置矩阵，位置/旋转全部错乱；
- 缓冲是**列主序 16 个 float**(OpenGL 约定)，即第 0、3、7、11 个元素是第一列...(`m00, m10, m20, m30, m01, ...`)；
- `FloatBuffer` 每帧复用：`clear()` → `put` 16 个值 → `flip()`，避免每帧分配 (成熟实例 `maintainAnchoredFloatBuffer` 同款)。

::: tip 每帧复用 `FloatBuffer`

—— 用 `clear()` → `put` 16 个值 → `flip()` 复用同一个缓冲 (本节 `buildAnchoredMatrix` 即此做法)，不要在渲染循环里每帧分配 (建议规则 12)。

:::

### 内容：刚性变换

本节 `modelMatrix` 是 2D 刚性变换 (旋转 + 平移)，布局 (成熟实例 `maintainAnchoredFloatBuffer` 实证)：

```text
列 0: ( cos, sin, 0, 0)     列 1: (-sin, cos, 0, 0)
列 2: (   0,   0, 1, 0)     列 3: ( x,   y,   0, 1)
```

角度为度、按 `Math.toRadians` 转弧度；`(x, y)` 是锚点世界坐标。

顶点着色器把它乘到局部坐标上 (`modelMatrix * local`)，再交给 `gameViewport`(UBO) 做世界 → 屏幕。

**shader 程序不吃固定管线矩阵**：`gl_ModelViewProjectionMatrix` 那套固定管线状态在本节不再使用 —— 模型矩阵是显式 uniform，投影来自 UBO。

## 必须遵守的规则汇总

正文按任务顺序解释了这些约束，这里逐条汇总成表，规则编号与正文和故障分析共用，排错时按编号回查。

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | BoxUtil shader 路径的可用性判据是 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`；`isBaseGL43Supported()` 只是 GL 能力探测，不能替代 `ShaderCore.isValid()`(既有纪律要点)| 判据误判可用 → 实体/程序创建后不渲染、无报错；`isShaderEnable()` 为 false 仍提交 → 关闭设置失去效果且白耗 GPU |
| 2 | **必须** | `program.active()` 后必须 `program.close()` 收尾，active/close 配对 (实例规范)| 漏 close → 后续绘制继续使用本程序状态，OpenGL 状态污染、渲染输出错位 |
| 3 | **必须** | 渲染前检查 `program.isValid()`；创建失败 (GPU 不支持、编译/链接失败) 不得继续绑定 (实例陷阱)| 失效程序继续绑定 → 渲染输出错位或 OpenGL 状态污染；失败原因不可见 |
| 4 | **必须** | GLSL 版本声明必须与目标上下文匹配：uniform block 需要 GLSL 1.40+/GL 3.1+，`#version 430` 需要 GL 4.3；BoxUtil 全管线基线 GL 4.3(版本声明纪律的延续)| 低版本上下文声明 430 → 编译失败，InfoLog 含 version 错误，无画面 |
| 5 | **必须** | UBO 块名必须与 GLSL 声明逐字一致 (大小写敏感)，块成员布局 (`std140`) 必须与写入方一致 | 块名拼错 → `getUBOIndex` 查不到，块未绑定 → `gameViewport` 未定义，画面不出现/位置错乱 |
| 6 | **必须** | 采样器 uniform 值必须等于纹理单元号 (`glUniform1i(texLoc, 0)` ↔ `glActiveTexture(GL_TEXTURE0)`)| 不一致 → 采样器指向的单元无期望纹理，颜色错误/黑，且无日志 |
| 7 | **必须** | uniform 名称与类型必须与 GLSL 声明一致 (大小写敏感)(名称/类型一致性纪律的延续)| 名拼错 → `getUniformIndex` 返回 -1，提交被静默忽略 (无日志)；类型不匹配 → 赋值失败/未定义 |
| 8 | **必须** | `glUniformMatrix4` 的 `transpose` 必须为 `false`，缓冲为列主序 16 float(OpenGL 约定)| `true` 或非列主序 → 矩阵转置，四边形位置/朝向/尺寸错乱 |
| 9 | **必须** | GL 资源生命周期完整流程：`ShaderProgram`/VBO 每场战斗 `init` 创建、`cleanup()` 里 `program.delete()` + 删 VBO、句柄置空；禁止跨战斗缓存 (生命周期纪律延续；实例规范 "资源清理归创建者")| 程序/缓冲随战斗泄漏 (`destroyInstance` 不调 `cleanup()`)；跨战斗引用已删句柄 → 渲染异常/未定义行为 |
| 10 | **必须** | `ShaderProgram` 创建后必须保存后续渲染所需的 uniform/UBO location(实例规范)| 未保存 → 渲染时无 location 可提交，uniform 全部不生效；或每帧重复查询 (浪费)|
| 11 | 建议 | attribute 位置在 GLSL 内用 `layout (location = N)` 固定 (430 方言)，Java 侧 `glVertexAttribPointer` 用同一位置 | 位置未固定 → 链接器自由分配，顶点数据喂错属性，几何错乱 (驱动相关；attribute 位置固定纪律的 430 对应物)|
| 12 | 建议 | 每帧复用 `FloatBuffer`(`clear`/`put`/`flip`)，不在渲染循环里分配 | 无功能错误；避免每帧 GC 压力 |
| 13 | 建议 | 日志行携带 uniform location 与 vert/frag 路径 | 无功能错误；uniform 拼错时能定位 "哪个文件哪一笔提交"|

## 完整最小例

在既有 MOD 布局与分层渲染插件通道之上新增两个 shader 资源、一个插件类 (不新增任何注册项、不改动任何既有文件)。本节示例对 BoxUtil 的硬依赖沿用既有声明 (`mod_info.json` 的 `dependencies` 含 BoxUtil)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                        # 硬依赖 BoxUtil（既有声明）
├── data\
│   └── shaders\
│       ├── GearIn_shader_visual.vert    # 本节新增
│       └── GearIn_shader_visual.frag    # 本节新增
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin              # 既有；本节不改动
│       ├── GearInGlobalAccess           # 既有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin          # 既有；本节只消费
│           ├── GearInCombatLifecyclePlugin      # 既有；本节只消费
│           ├── GearInLayeredRenderer            # 既有；本节只消费
│           ├── GearInPulseRenderer              # 既有；本节只消费
│           └── GearInShaderVisual               # 本节新增
└── jars\
```

### GearIn_shader_visual.vert

GLSL 4.30 方言 (`#version 430`)。

与既有 110 方言文件相比，这处文件有四处分方言升级：

- attribute 位置用 `layout (location = N)` 在 GLSL 内固定，替代链接前 `glBindAttribLocation`；
- uniform block(UBO) 声明；
- `out vec2` 替代 `varying`；
- 投影矩阵来自 UBO 的 `gameViewport`，不再用 `gl_ModelViewProjectionMatrix`：

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

`a_position` 是 [-1,1] 的局部坐标 (本节自建 VBO 的四边形顶点)，`size` uniform 给出世界尺寸 (`a_position * size * 0.5` 把局部坐标缩放成以锚点为中心的世界单位矩形)，`modelMatrix`(本节提交的刚性变换) 把锚点平移到玩家舰船，`gameViewport`(UBO) 完成世界 → 屏幕。

### GearIn_shader_visual.frag

片元着色器提交纹理采样：`tex` 采样粒子贴图 `graphics/fx/particlealpha32sq.png`(32×32 白色径向渐变、中心不透明、角上透明)，`time` uniform 驱动 V 方向滚动，`coreColor`/`fringeColor` 控制颜色：

```glsl
#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.25));
    vec4 texSample = texture(tex, uv);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
```

- `fract(fragUV.y + time * 0.25)` 是纹理滚动：`time` 每秒 +0.25 个 V 周期，`fract` 保证滚动循环 (贴图区域外不越界采样)；
- `mix(fringeColor.rgb, coreColor.rgb, texSample.r)` 用贴图红通道在边缘色与核心色之间插值 (贴图是白色渐变，`texSample.r` 从中心 1 衰减到边缘 0—— 中心偏核心色、边缘偏边缘色，配合加法混合成 "外蓝内橙" 的发光贴片)；
- 透明度低于 `DISCARD_ALPHA` 的片元 `discard`(实例同款写法)。

### GearInShaderVisual(Java 接入)

Java 接入在分层渲染插件通道上完成四个环节：init 内的可用性判断与程序创建、render 内的状态提交、cleanup 内的资源清理。

源码如下：

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
 * 完整最小例：ShaderProgram、UBO、uniform、纹理与矩阵提交。
 *
 * 事实（BoxUtil pinned commit d0423e5...，全部经成熟实例消费侧核验）：
 * - 程序对象：ShaderUtil.createShaderVFFormPath(名称, vert 路径, frag 路径) 按路径创建
 *   程序（成熟实例消费实证），包装进 org.boxutil.units.standard.ShaderProgram；创建后立即用
 *   getUniformIndex / getUBOIndex 查询并保存 location（实例规范
 *   "创建后必须保存后续渲染所需的 uniform、attribute 或 UBO location"）；
 * - UBO：GLSL 侧声明 layout(std140) uniform BUtilGlobalData { mat4 gameViewport;
 *   vec4 gameScreenBorder; }（成熟实例同款声明），Java 侧
 *   getUBOIndex("BUtilGlobalData", ShaderCore.getMatrixUBOBinding()) 绑定；gameViewport
 *   是 BoxUtil 全局矩阵 UBO 供给的世界→屏幕矩阵，取代 C14-S01 的手动 glOrtho；
 *   块名必须与 GLSL 逐字一致（大小写敏感）；
 * - uniform：标量/向量（glUniform1f/2f/4f）、采样器（glUniform1i 值=纹理单元号）、
 *   矩阵（glUniformMatrix4(loc, false, 列主序 FloatBuffer)，transpose 必须 false）；
 *   名称与类型必须与 GLSL 声明一致（C14-S01 硬规则 6）：拼错 → getUniformIndex 返回
 *   -1 → 提交被静默忽略且无日志；
 * - 纹理：SettingsAPI.getSprite 取引擎必载贴图 graphics/fx/particlealpha32sq.png
 *   （引擎启动期入加载队列；C12-S06 同款姿势），
 *   SpriteAPI.getTextureId() 取 GL 纹理 id，glActiveTexture(GL_TEXTURE0) +
 *   glBindTexture(GL_TEXTURE_2D, id) + glUniform1i(texLoc, 0) 完成单元绑定与采样器
 *   提交（成熟实例实证）；
 * - 矩阵：modelMatrix 是 2D 刚性变换（旋转+平移），列主序 16 float，布局同成熟实例
 *   maintainAnchoredFloatBuffer；每帧复用
 *   FloatBuffer（clear/put/flip）；
 * - 门禁：BoxConfigs.isShaderEnable() && ShaderCore.isValid() 共同决定 shader 路径
 *   可用性（K06/C12-S06）；isBaseGL43Supported() 只是 GL 能力探测，不能替代
 *   ShaderCore.isValid()（实例陷阱）；门禁失败 → 记录日志并自废，
 *   不创建程序；
 * - 生命周期：每场战斗 init 创建程序与 VBO（GL 上下文就绪），render 内 active()/
 *   close() 配对提交（实例规范"active() 后必须按 close() 关闭"），
 *   cleanup() 内 program.delete() + glDeleteBuffers，禁止跨战斗缓存（C06-S03/C07-S06
 *   延续；实例规范"资源清理归创建者"）；isValid() 使用前检查
 *   （实例陷阱：失效程序继续绑定 → 输出错位或状态污染）。
 */
public class GearInShaderVisual extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInShaderVisual.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /；C14-S02 硬规则 1 延续）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_shader_visual.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_shader_visual.frag";

    /** UBO 块名：必须与 GLSL 声明逐字一致（大小写敏感）。 */
    private static final String UBO_BLOCK_NAME = "BUtilGlobalData";

    /** 采样贴图：引擎启动期必载的原版粒子贴图。 */
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";

    /** 四边形世界尺寸（局部坐标 [-1,1] × size/2）。 */
    private static final float QUAD_WIDTH = 160f;
    private static final float QUAD_HEIGHT = 80f;

    /** 演示寿命（秒）：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 30f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    private ShaderProgram program;
    private SpriteAPI sprite;
    private int vboId = 0;
    private FloatBuffer matrixBuffer = null;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        // 门禁：BoxUtil shader 路径可用性判据（K06/C12-S06）；失败即自废，不创建程序
        if (!BoxConfigs.isShaderEnable() || !ShaderCore.isValid()) {
            LOG.info("GearIn shader visual: BoxUtil shader path unavailable (shaderEnable="
                    + BoxConfigs.isShaderEnable() + ", shaderCoreValid="
                    + ShaderCore.isValid() + "), visual disabled");
            valid = false;
            return;
        }
        if (!createProgram() || !buildQuad() || !loadSprite()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（C07-S06 同款姿势）
        LOG.info("GearIn shader visual init: u_model=" + program.location[0]
                + ", u_size=" + program.location[1]
                + ", u_time=" + program.location[2]
                + ", u_tex=" + program.location[3]
                + ", u_core=" + program.location[4]
                + ", u_fringe=" + program.location[5]
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
                program.getUniformIndex("coreColor"),
                program.getUniformIndex("fringeColor")
        };
        program.uboLocation = new int[] {
                program.getUBOIndex(UBO_BLOCK_NAME, ShaderCore.getMatrixUBOBinding())
        };
        if (!program.isValid()) {
            LOG.error("GearIn shader visual: program invalid after load");
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
            LOG.error("GearIn shader visual: glGenBuffers returned 0");
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
            LOG.error("GearIn shader visual: sprite not loaded: " + SPRITE_PATH);
            return false;
        }
        return true;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
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
        elapsed += amount;
        if (elapsed >= LIFETIME) {
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()（C07-S06 硬规则 3）
            return;
        }
        // 剔除依据是承载实体的位置（C07-S06 硬规则 4）：跟随玩家舰船
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
        if (program != null) {
            program.delete();
            program = null;
        }
        LOG.info("GearIn shader visual cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == null || sprite == null || vboId == 0) {
            return;
        }
        if (!program.isValid()) {
            return; // 程序失效不得继续绑定（实例陷阱）
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 矩阵 uniform：刚性变换（旋转 + 平移），列主序 16 float，transpose 必须 false；
        // 布局同成熟实例 maintainAnchoredFloatBuffer
        Vector2f center = engine.getPlayerShip().getLocation();
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

        // 颜色 uniform：外蓝内橙（贴图红通道在两者之间插值）
        float alpha = viewport.getAlphaMult();
        GL20.glUniform4f(program.location[4], 1.0f, 0.55f, 0.2f, alpha);
        GL20.glUniform4f(program.location[5], 0.15f, 0.45f, 1.0f, alpha);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        program.close(); // 提交段结束

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); // 解绑，避免影响后续绘制者的纹理状态
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

- **可用性判断 (init)**：`BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(规则 1) 失败 → INFO 日志 + `valid = false` 自废，不创建任何 GL 对象 ——"关闭 shader 路径后示例干净退出"。
- **程序创建 (init)**：`createShaderVFFormPath` → `new ShaderProgram` → `location`/`uboLocation` 保存 (规则 5/10)→ `isValid()` 失败则 `delete()` 并返回 false(规则 3)。
- **提交 (render)**：`program.active()`(规则 2)→ `glUniformMatrix4`(矩阵，规则 8)→ `glUniform2f/1f`(向量/标量，规则 7)→ `glActiveTexture(GL_TEXTURE0)` + `glBindTexture` + `glUniform1i(texLoc, 0)`(纹理，规则 6)→ `glUniform4f` 颜色 → `glDrawArrays` → `program.close()`。
- **清理 (cleanup)**：`glDeleteBuffers` 删 VBO、`program.delete()` 删程序 (规则 9)；`advance` 在 `LIFETIME` 秒后主动失效驱动引擎移除。
- 顶点数据沿用既有的自建 VBO 做法 (本节主题不含几何对象，不引入 BoxUtil `QuadObject`—— 它是 BoxUtil 提供的等价矩形几何，顶点范围属 BoxUtil 内部不可读，教程不依赖其语义；成熟实例用它配合同款 430 shader)。

注册片段 (工程片段 —— 本节不改动战斗生命周期插件的任何文件，只演示使用方调用；把下面一行加进 `GearInCombatLifecyclePlugin.init(CombatEngineAPI engine)` 末尾)：

```java
    // 工程片段：战斗生命周期插件 GearInCombatLifecyclePlugin.init(CombatEngineAPI engine) 内追加
    engine.addLayeredRenderingPlugin(new GearInShaderVisual());
```

与上一页的注册片段同一做法；渲染层接入的完整设计 (开关、常驻、性能) 在本章后续展开。

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用既有 MOD 工程)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/shaders/GearIn_shader_visual.vert`、`data/shaders/GearIn_shader_visual.frag` 与 `data/scripts/gearin/combat/GearInShaderVisual.class`(可用 `jar tf` 核对)。

### 实例对照

成熟实例的用法与本节描述逐行同构：程序创建 (`createShaderVFFormPath` + `new ShaderProgram`)、创建后立即保存 uniform/UBO location、渲染前 `isValid()`、`active()/close()` 配对、矩阵/纹理提交做法、`cleanup()` 里 `program.delete()`。

## 验证方案

本节只有一个待验证成果：`GearInShaderVisual` 接入后的完整渲染链。它同时覆盖 Java 编译与 shader 资源两处校验，因此先跑构建侧，再进战斗看画面。

前置数据：既有 MOD 布局 (`mod_info.json` 硬依赖 BoxUtil)+ 战斗生命周期插件 (含本节注册片段一行)+ 分层渲染插件 + 上一页的脉冲渲染插件 + 本节的 `GearInShaderVisual`、`data/shaders/GearIn_shader_visual.vert/.frag` + `GearIn_test` 测试战役 (C03-S01)。

环境需满足 BoxUtil 的 GL 4.3 能力基线 (`ShaderCore.isValid()` 为 true)。

**操作步骤**：

1. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/shaders/GearIn_shader_visual.vert`、`data/shaders/GearIn_shader_visual.frag`、`data/scripts/gearin/combat/GearInShaderVisual.class`。
2. 战役冒烟：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 init 行 (含 `vert=`/`frag=` 路径与六个 `u_*` location、`ubo=`)；`ubo=<loc>` 非 -1；没有 `gear_in` 引起的 `ERROR`。
3. 画面：观察玩家舰船位置 160×80 世界单位的发光四边形：中心偏橙、边缘偏蓝、柔和光晕；跟随舰船移动并随舰船朝向旋转；纹理沿 V 方向滚动 (约 4 秒一周期)。
4. 暂停与寿命清理：按空格暂停约 10 秒 —— 纹理滚动冻结、四边形静止但可见；恢复后继续；约 30 秒后四边形消失且 `starsector.log` 出现 `GearIn shader visual cleanup`。
5. 跨战斗：退出战斗再进一场 ——init 行重新出现，无 ERROR。
6. 门禁降级：在 `LunaSettings`/`BoxConfigs` 把 shader 路径关闭后进战斗 ——`starsector.log` 出现 `GearIn shader visual: BoxUtil shader path unavailable (...), visual disabled`，无画面、无 ERROR(shader 路径干净降级)。
7. 故障注入：a) Java 侧把 `getUniformIndex("time")` 拼成 `"Time"`—— 纹理滚动停止且无日志 (init 行 `u_time=-1`)；b) 把 `glUniformMatrix4` 的 `false` 改成 `true`—— 四边形位置/朝向错乱；c) 把 `glUniform1i(texLoc, 0)` 的 `0` 改成 `1`—— 四边形颜色错误/变黑；d) 把 `#version 430` 改成 `#version 140`—— 编译失败、无画面 (BoxUtil 侧日志)；e) 改回后确认恢复。

**预期结果**：

8. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/shaders/GearIn_shader_visual.vert`、`data/shaders/GearIn_shader_visual.frag` 与 `data/scripts/gearin/combat/GearInShaderVisual.class`。
9. **战斗初始化 (可用性判断 + 程序创建)**：进入 `GearIn_test` 测试战役一场战斗，`starsector.log` 出现一行。
```text
GearIn shader visual init: u_model=<loc>, u_size=<loc>, u_time=<loc>, u_tex=<loc>, u_core=<loc>, u_fringe=<loc>, ubo=<loc>, vert=data/shaders/GearIn_shader_visual.vert, frag=data/shaders/GearIn_shader_visual.frag
```
其中 `vert=`/`frag=` 与资源文件路径一致 (四位置完整流程)；`ubo=<loc>` 非 -1(UBO 块绑定成功)；没有由 `gear_in` 引起的 `ERROR`。若 BoxUtil 未初始化/被关闭，则出现 `GearIn shader visual: BoxUtil shader path unavailable (...), visual disabled` 且无画面 (可用性判断生效)。
10. **画面 (提交生效)**：玩家舰船位置出现一个 160×80 世界单位的发光四边形 —— 中心偏橙 (`coreColor`)、边缘偏蓝 (`fringeColor`)，加法混合、柔和光晕 (`particlealpha32sq` 的径向渐变 + `discard` 剔除透明片元)；四边形跟随舰船移动并随舰船朝向旋转 (`modelMatrix` 提交)；纹理沿 V 方向向下滚动 (`time` 驱动，约 4 秒一个周期)；四边形位于舰船与导弹之上 (分层渲染通道层序)。
11. **暂停行为**：按空格暂停 —— 纹理滚动冻结 (`getTotalElapsedTime(false)` 不累计)、四边形静止但可见 (与分层渲染插件同款)；恢复后继续滚动。
12. **寿命与清理**：约 30 秒后四边形消失，`starsector.log` 出现一行。
```text
GearIn shader visual cleanup
```
即主动失效 → 引擎移除 → `cleanup()` 的正常链 (规则 9 的可观察面)。同一场战斗内不再出现。
13. **跨战斗**：退出战斗再进一场 ——init 行重新出现且 location 值重新查询 (每场战斗重建程序与 VBO，遵循既有生命周期纪律)；无 ERROR。
14. **故障注入**：见故障分析第 4、4、5、6 条对应的注入动作与恢复。

**通过信号**：操作 1–5 全部符合；操作 6 的降级与操作 7 的注入/恢复都符合预期。

**清理**：取消勾选测试 MOD；恢复代码与 shader 为原值

## 故障分析

以下十个故障按 "先看症状、再查原因" 组织，注入动作、可观察结果与修正位置都针对 `GearInShaderVisual` 的修改；每项改回后重启战斗即可复验。

1. **程序创建后不渲染，且没有任何报错 (可用性判据用错)**。触发：把可用性判据改成 `BoxConfigs.isShaderEnable() && BoxConfigs.isBaseGL43Supported()`。原因：`isBaseGL43Supported()` 只是 GL 能力探测，BoxUtil 内部初始化失败 (program/FBO/VAO/matrix 初始化失败，实例陷阱) 时它仍返回真，于是程序创建后不渲染，静默失败。修正：用回 `BoxConfigs.isShaderEnable() && ShaderCore.isValid()`(规则 1)。
2. **程序失效时仍继续绑定，渲染输出错位或污染 OpenGL 状态，且失败原因不可见**。触发：注释掉 `render` 里的 `if (!program.isValid()) return;`。原因：程序创建失败 (如 GPU 不支持 430) 后仍走完绑定与绘制。修正：渲染前检查 `isValid()`(规则 3)。
3. **四边形不出现或位置错乱，且没有日志 (UBO 块名拼错)**。触发：把 `UBO_BLOCK_NAME` 改成 `"BUtilGlobalDataX"`(GLSL 声明不改)。原因：块名不一致时 `getUBOIndex` 查不到块，块未绑定，`gameViewport` 保持未定义 (GL 错误不报)。修正：块名与 GLSL 声明逐字一致 (规则 5)，对照 "名称一致性完整核对" 检查。
4. **编译失败、无画面，InfoLog 报语法或版本错误**。触发：把 `#version 430` 改成 `#version 140`(保留 uniform block；`layout (location = N)` 的保留与否是另一处修改)。原因：`layout (location = N)` 在 140 下非法；反过来在 GL 2.0 上下文声明 430 同样编译失败。修正：版本声明与目标上下文匹配 (规则 4)。
5. **纹理滚动停止、画面其余正常，且没有任何日志 (uniform 名拼错)**。触发：把 Java 侧 `getUniformIndex("time")` 改成 `getUniformIndex("Time")`。原因：location 为 -1，`glUniform1f` 被静默忽略，`time` 恒为 0。修正：名称/类型与 GLSL 声明一致 (规则 7)；init 日志里的 location 值能直接看出 -1。
6. **四边形颜色错误或变黑，无日志 (采样器与纹理单元不配对)**。触发：把 `glUniform1i(program.location[3], 0)` 改成 `glUniform1i(program.location[3], 1)`(`glActiveTexture(GL_TEXTURE0)` 不改)。原因：采样器指向单元 1，单元 1 上没有绑定贴图。修正：采样器值必须等于纹理单元号 (规则 6)。
7. **旋转方向反转、平移错位，四边形位置/朝向错乱**。触发：把 `glUniformMatrix4(..., false, matrixBuffer)` 的 `false` 改成 `true`。原因：矩阵被转置上传，舰船移动时四边形按错误方向漂移。修正：`transpose=false` + 列主序 16 float(规则 8)。
8. **本程序绘制后，后续绘制者继续使用它的状态，输出错位或污染**。触发：注释掉 `program.close()`。原因：`glDrawArrays` 后本程序仍保持 active，原版管线或其他 MOD 的绘制被它的状态影响 (实例规范)。修正：`active()`/`close()` 配对 (规则 2)。
9. **第二场战斗渲染异常或未定义行为 (跨战斗缓存程序)**。触发：把 `program` 存进静态字段，第二场战斗不重建。原因：第二场战斗引用了已经删除的句柄。修正：每场战斗 `init` 重建 (规则 9)。
10. **启动即报缺纹理、无画面 (纹理路径取不到)**。触发：把 `SPRITE_PATH` 改成未加载文件的路径 (如 `"graphics/fx/not_loaded.png"`)。原因：`getSprite` 单参形态要求文件已经加载 (javadoc："File must already have been loaded")，未加载时返回 null，`loadSprite` 记 ERROR `sprite not loaded:` 并自废。修正：用已加载文件 (引擎启动期入队列的贴图)，或改用双参形态并确认 settings.json `graphics` 分类注册。

## 总结

本节把绘制前的状态提交走成了一条完整的数据链：两个 shader 资源文件定义程序，`ShaderProgram` 在创建后立即保存 location，render 内按矩阵、uniform、纹理、颜色的顺序把状态送进程序，UBO 补上投影，战斗画面里那枚跟随舰船移动的发光四边形就是整条链走通后的可见结果。这条链的每一环都有明确的失败表现：名称拼错是 -1 静默、采样器错位是变黑、转置矩阵是位置错乱、块名拼错是画面缺失；init 日志里打印的 location 值能直接区分前几类问题 —— 排错时先看 init 行，再沿 "名称一致性 → 纹理单元号 → transpose → UBO 块名 → 生命周期" 的顺序核对，比在渲染代码里逐行猜测更快。

对您来说，这套流程同时收敛成一段可复用的样板：创建 → 保存 location → `isValid()` → `active()`/提交/`close()` → `cleanup()` 释放。任何新的视觉 shader 都可以沿用同一节奏；下一步的逻辑延伸是把这段样板接入渲染层的常驻开关与生命周期管理，让程序对象从一次性演示走向随配置启停的常驻渲染资源，并为多程序复用与性能取舍留出空间。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`getSprite(String)` 与 `getSprite(String, String)` 两种纹理获取形态 (javadoc：单参要求 "文件必须已加载"，双参读 settings.json 的 `graphics` 分类)。
- `com.fs.starfarer.api.graphics.SpriteAPI`：`getTextureId()`、`bindTexture()` 等纹理句柄接口。
- `com.fs.starfarer.api.combat.ViewportAPI`：`getAlphaMult()`(绘制透明度)。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`getTotalElapsedTime(boolean)`、`getPlayerShip()`、`addLayeredRenderingPlugin(...)`。
- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin` / `BaseCombatLayeredRenderingPlugin`：分层渲染插件接口与基类。
- `org.boxutil.util.ShaderUtil`：`createShaderVFFormPath(...)` 按路径创建程序 (BoxUtil 消费形状)。
- `org.boxutil.units.standard.ShaderProgram`：程序句柄封装 ——`getUniformIndex`/`getUBOIndex`/`isValid`/`active`/`close`/`delete` 与公共字段 `location`/`uboLocation`。
- `org.boxutil.manager.ShaderCore`：`getMatrixUBOBinding()`、`isValid()`(全局矩阵 UBO 绑定点与库可用性)。
- `org.boxutil.config.BoxConfigs`：`isShaderEnable()`(shader 路径开关)。
- `data/shaders/GearIn_shader_visual.vert` / `data/shaders/GearIn_shader_visual.frag`：本节新增的两个 shader 资源 (MOD 根目录相对路径)。
- `graphics/fx/particlealpha32sq.png`：引擎启动期必载的原版粒子贴图 (本节采样纹理来源，不引入新资产)。
- `Starsector/mods/GearIndustry`：示例 MOD 工程目录 (`gradle jar` 构建，产物核对用 `jar tf`)。
