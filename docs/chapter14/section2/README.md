# 着色器文件：路径、加载与编译

本节把着色器 (shader) 从源码接入战斗画面：确定 `.vert/.frag` 在 MOD 中的存放路径，走通读取、编译、链接与日志记录，再按加载、使用、清理、重载四个环节维护程序对象。上一节讲透的 GLSL 语法与编译检查链还停留在源码层面，磁盘上的 shader 不会自己进入游戏 —— 本节补上这条从文件到画面的路径。完成后，您将得到一个显示扩散脉冲环的渲染插件，并能在 `starsector.log` 里核对它的每一步加载与失败。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `.vert/.frag` 在 MOD 中的存放约定 (`data/shaders/` 目录级入口) 与路径规则：MOD 根目录相对、`/` 分隔、同基名成对、UTF-8 无 BOM；
- 说出 `SettingsAPI.loadText` 的两种形态 (单参全局、双参按 MOD 限定资源空间) 与读取端行为 (UTF-8 解码、`\r` 剥除、BOM 不剥)，以及 `validateFileName` 在读取前拒绝哪些路径；
- 说出 shader 加载的完整调用链：`loadText` → `glCreateShader`/`glShaderSource`/`glCompileShader` → `glCreateProgram`/`glAttachShader`/`glLinkProgram` → 状态检查 → InfoLog，并指出游戏侧对这个过程零日志、日志责任在 MOD 侧；
- 说出编译/链接失败的 InfoLog 里能看到什么 (行号、原因、version 错误)，成功时通常为空串，以及链接警告 (如 varying 不匹配) 不会让链接失败；
- 说出 shader 资源生命周期的四个环节：加载 (每场战斗 init)→ 使用 (render 回调内)→ 清理 (`cleanup()` 里 `glDeleteProgram`；战斗结束 `destroyInstance` 不调 `cleanup()`)→ 重载 (按同一路径删除重建，重建成功才替换)；
- 从症状定位典型故障：路径写错 (`\`/盘符/`..`/漏目录)、源文件带 BOM、漏查编译/链接状态、InfoLog 没进日志、重载先删后建、跨战斗缓存程序句柄。

## 前置知识

本节把上一节的 shader 能力接到文件与生命周期上，先确认以下基础：

- MOD 目录结构与 `starsector.log` 的读取 (C01-S01)：本节所有日志观察结果都落在它里面。
- 战役 (mission) 概念与 `GearIn_test` 测试战役 (C03-S01)：本节用它做游戏内验证。
- 编译 JAR 链路 (C06-S01)：shader 文本作为资源进入构建，本节沿用松散文件 + jar 副本的交付形态。
- `Global` 生命周期 (C06-S03)：禁止跨战斗缓存引擎引用与渲染对象，本节的程序/VBO 同规。
- `SettingsAPI` 资源读取三族与 `validateFileName` 必须遵守的规则 (C06-S04)：本节的 `loadText` 语义直接承接。
- 战斗插件生命周期 (C07-S05)：本节的注册片段加在它的 `init` 末尾。
- 分层渲染插件与 GL 资源清理必须遵守的规则 (C07-S06)：本节的 "加载/清理/失效" 环节直接复用。
- BoxUtil 初始化/实体/生命周期 (C12-S06)：对照的另一条加载入口 `ShaderUtil` 与同源的生命周期纪律。
- GLSL 110 方言、编译/链接检查链、OpenGL 2.0 能力门禁 (C14-S01)：本节的加载与日志环节是它的延续。

## 文件与 API 链路

从磁盘文件到战斗画面一共经过五类环节，下表先给每个环节的落点，再逐段解释为什么这样接：

| 环节 | 文件 / API | 说明 |
|---|---|---|
| shader 源文件 | `data/shaders/GearIn_pulse_shader.vert` / `.frag`(本节所有权)| MOD 根目录相对路径，`/` 分隔；同基名成对 (`GearIn_<语义>_shader.vert/.frag`)，语义命名 |
| 路径校验 | `validateFileName` | `loadText` 读取前执行：`..`、首字符 `/`、`\`、`:`(含 `c:`/`d:`) 直接抛 `RuntimeException` |
| 文本读取 | `SettingsAPI.loadText(String)` / `loadText(String, String)`(`com.fs.starfarer.api.SettingsAPI`)| 单参读全局资源空间；双参按 modId 限定 MOD 资源空间 (`setSourceFilter`)|
| 读取实现 | `readText`(`loadText` 底层的读取实现) | UTF-8 解码、`\r` 全部剥除、**BOM 不剥除**；`readString` 本身无日志 |
| 编译 | LWJGL `GL20.glCreateShader` / `glShaderSource` / `glCompileShader` / `glGetShaderi(GL_COMPILE_STATUS)` / `glGetShaderInfoLog` | GraphicsLib `ShaderLib.createShader`(编译失败抛 `"Error creating shader: " + 日志`)|
| 链接 | `GL20.glCreateProgram` / `glAttachShader` / `glBindAttribLocation` / `glLinkProgram` / `glGetProgrami(GL_LINK_STATUS)` / `glGetProgramInfoLog` | GraphicsLib `ShaderLib.loadShader`(链接失败删 program 返回 0)|
| 日志 | `Global.getLogger(...)`(log4j `Logger`)→ `starsector.log` | 编译/链接 InfoLog 与加载/重载/清理事件都由 MOD 侧写入 (游戏侧对 `loadText` 零日志)|
| 运行使用方 | `GearInPulseRenderer`(C07-S06 分层渲染通道)| 每场战斗 `init` 加载程序、`render` 内使用、`cleanup` 内删除、`advance` 内定时重载 |
| 实例对照 (加载入口 A)| GraphicsLib `org.dark.shaders.post.PostProcessShader` | `loadText("data/shaders/post/pre.vert")` → `ShaderLib.loadShader` → 失败置 `enabled = false` 并 ERROR |
| 实例对照 (加载入口 B)| `ShaderUtil.createShaderVFFormPath(类名, vert 路径, frag 路径)` | BoxUtil 封装的使用入口 (内部行为留给 C14-S03)|
| 可观察结果 | `starsector.log` + 战斗画面 | init/reload/cleanup/compile failed 日志行；玩家舰中心脉冲环 |

### 四位置完整流程

shader 是**无注册表 ID**的资源：它不经过任何 CSV/JSON 注册链 (与武器、战术系统、船插不同)，`data/shaders/...` 的路径与文件名就是它的身份。因此静态核对沿四个位置进行，四处必须一致：

| 位置 | 本节取值 |
|---|---|
| 资源文件 | MOD 内 `data\shaders\GearIn_pulse_shader.vert` / `.frag` |
| 加载入口 | `Global.getSettings().loadText("data/shaders/GearIn_pulse_shader.vert")`(Java 常量 `VERT_PATH` / `FRAG_PATH`，与资源文件路径逐字一致)|
| 运行使用方 | `GearInPulseRenderer`(C07-S06 通道注册，`render` 内 `glUseProgram` + 绘制)|
| 可观察结果 | 日志行含 `vert=data/shaders/GearIn_pulse_shader.vert`、`frag=...`(与前面三处一致)；画面出现脉冲环 |

页面与 Java 常量两处路径必须逐字一致。

## 路径约定

四个位置的第一处是资源文件本身，先解决它放在哪里、怎么书写。

### 目录与命名

- 引擎对 `.vert/.frag` 没有任何内置路径 —— 目录约定是**实例惯例而不是引擎强制**：GraphicsLib 用 `data/shaders/post/pre.vert`，现代 430 实例在 `data/shaders/` 下用语义命名文件，实例知识规范把 `data/shaders/` 定义为 "GLSL 顶点与片元资源的目录级入口"。教程沿用该惯例 (建议 10)。
- 文件命名语义化且同基名成对：`GearIn_pulse_shader.vert` 与 `GearIn_pulse_shader.frag` 是一对 (顶点/片元)，基名同时出现在 Java 的两个路径常量里。
- 本单元不注册任何游戏运行时 ID；工作单元编号不得进入资源名 (示例所有权 `GearIn_pulse_shader.vert/.frag`)。

::: info 为什么目录约定是 "实例惯例" 而不是引擎强制
原版管线是固定管线：游戏侧源码零 GL20/GLSL 引用，设备诊断只调用 `GL11.glGetString`——shader 的存放位置、加载方式与生命周期全部由 MOD 自组织。所以 "放哪里、怎么读" 没有引擎默认支持，约定来自实例知识规范与成熟 MOD 的共识。
:::

### 路径语法：`/` 分隔、MOD 根相对

`SettingsAPI.loadText` 的 javadoc 是必须遵守的规则：**"Use / instead of \ in paths for compatibility (required to work on OS X and Linux)"**(`com.fs.starfarer.api.SettingsAPI`)。

本节把它的可观察面加强到所有平台：`loadText` 在读取前调用 `validateFileName`，其实现对含 `..`、以 `/` 开头、含 `\`、含 `:`(盘符形式 `c:`/`d:` 也在其中) 的路径**直接抛 `RuntimeException`**，报错原文：

```text
.. and starting with a / are not allowed in filenames. Also, use / instead of \.
```

也就是说：

- 用 `\` 或盘符前缀写路径 → 在所有平台上于读取前立即 `RuntimeException`(不只是 OS X/Linux 加载失败)；
- 路径必须是 MOD 根目录相对 (`data/shaders/...`)，绝对路径 (首字符 `/`) 被拒；
- `..` 目录穿越被拒 (也是安全边界)。

::: caution 路径写错的四种形态会在读取前被直接拒绝
用 `\`、盘符前缀 (`c:`/`d:`)、`..`、或以 `/` 开头写 shader 路径，`validateFileName` 会在 `loadText` 读取前直接抛 `RuntimeException`(所有平台)。路径必须写成 MOD 根目录相对 + `/` 分隔 (规则 1)。
:::

### 双参形态：按 MOD 限定资源空间

`loadText(String filename, String modId)`(`com.fs.starfarer.api.SettingsAPI`) 在读取前取 `ModManagerAPI.getInstance().getModSpec(modId).getDirName()` 并 `setSourceFilter(dirName)`，把查找限定在该 MOD 的资源空间。

必须遵守的规则 (与资源读取三族的规则同源)：**modId 必须与 `mod_info.json` 的 `id` 逐字一致且该 MOD 已启用**，否则 `getModSpec` 返回 `null`，调用点直接 NPE。

### 交付形态：松散文件为准

MOD 根目录下的松散文件 (`data\shaders\...`) 是交付形态 (GraphicsLib 发行即松散文件，实机可加载)；构建阶段 (`gradle jar`) 会把 `src/main/resources` 一并打进 `jars\GearIndustry.jar` 作为构建产物。

资源空间的注册与解析发生在资源加载器；可定位的事实是每个 MOD 的目录经 `addResourceSpec(...)` 注册。

本节不承诺 "jar 内副本 vs 松散文件" 的解析次序，示例以松散文件为权威副本。

## 加载链路

路径正确只保证文件能被找到；文本能不能被编译，取决于读取端把它解码成什么。`loadText` 的行为由此成为第一条决定成败的规则。

### 读取端行为

`loadText` 的调用链：`validateFileName` → 文本读取工具的 `readString`(打开资源流 → `readText`)→ `readText` 按 **UTF-8** 解码并把全部 `\r` 字符剥除 (`replaceAll("\\r", "")`)**，不剥离 BOM**。

三个直接推论：

1. 源文件必须 **UTF-8 无 BOM**：BOM 字节 (`EF BB BF`) 会被解码成 `\uFEFF` 并原样进入 GLSL 源码字符串 ——`glShaderSource` 后 `glCompileShader` 在首行报错 (不同驱动报 "unexpected character" 或 version 位置错误，均可在 InfoLog 看到)。这与 MOD 文件基础规则中的 "MOD 文件必须 UTF-8 无 BOM" 是同一条读取端规则。
2. **CRLF 换行安全**：`\r` 被读取端剥除，Windows 换行不会进入 GLSL 源码 (数据资源读取的换行归一化同理)。
3. **读取无日志**：`readString`/`readText` 不写任何日志 ——"shader 加载成功的日志" 只可能来自 MOD 侧自己的 `Logger`。

::: warning 用带 BOM 的编辑器保存 `.frag` 是最常见的加载失败坑
BOM 字节会被解码成 `\uFEFF` 原样进入 GLSL 源码，编译首行报错，init 后无画面。保存 shader 源文件务必选 **UTF-8 无 BOM**；CRLF 换行完全安全 (`\r` 会被读取端剥除)。
:::

### 加载时机

程序对象在**战斗内**创建：渲染插件 `init`(分层渲染通道，GL 上下文就绪后；与 GLSL 基础主题同款)。不在类加载期、不在 `onApplicationLoad` 创建 (此时无战斗 GL 上下文)。每场战斗重新 `init`，禁止跨战斗缓存 (`Global` 生命周期禁令)。

### 编译与链接链

延续基础主题建立的检查链，本节强调 "失败即日志"：

```text
glCreateShader → glShaderSource(直接传 String) → glCompileShader → glGetShaderi(GL_COMPILE_STATUS)
glCreateProgram → glAttachShader → glBindAttribLocation(链接前固定) → glLinkProgram → glGetProgrami(GL_LINK_STATUS)
失败路径：glGetShaderInfoLog / glGetProgramInfoLog（InfoLog）→ LOG.error → 删除对象
```

## 编译日志 (InfoLog)

编译与链接失败的信息只存在于 InfoLog，而游戏侧不会把它写进任何日志 —— 省略记录，失败就不可见。这一节先确认谁负责写日志，再看 InfoLog 里能读到什么。

### 谁写日志

- **游戏侧零日志**：`loadText` 全程静默；引擎对 shader 编译/链接没有任何日志 (原版不加载 shader)。
- **MOD 侧必须写日志**：编译/链接状态检查后，失败时的 InfoLog 由 MOD 自己的 log4j `Logger`(`Global.getLogger(...)`) 以 ERROR 写入游戏日志 `starsector.log`**。不写日志 = 失败不可见**(规则 6)。

::: caution 不写日志 = 失败不可见
游戏侧对 `loadText`、编译与链接零日志 ——"shader 加载成功/失败的日志" 只可能由 MOD 侧写入。省略失败日志，失败会完全静默：画面无内容且没有任何诊断 (规则 6)。
:::

### InfoLog 里能看到什么

- **编译失败**：InfoLog 含行号与原因，如 `ERROR: 0:5: 'xxx' : syntax error`(`0` 是源索引、`5` 是源内行号)。GraphicsLib 把编译失败包装成 `"Error creating shader: " + InfoLog`(`org.dark.shaders.util.ShaderLib`)；本节示例的日志前缀是 `GearIn pulse shader: compile failed: ...`。
- **version 错误**：在 GL 2.0 基线声明 `#version 130+`，InfoLog 报 version 相关错误 (方言基线规则)。
- **链接失败**：`GL_LINK_STATUS == FALSE` 时 InfoLog 给出原因 (如 varying 两侧类型不一致的链接错误)；GraphicsLib 链接失败删 program 返回 0(`ShaderLib.loadShader` 做法)。
- **链接警告 (不失败)**：varying 两侧同名不同类、或一侧缺失，常见的是**警告**而不是失败 —— 程序链接成功，但片元读到未定义值 (画面恒 0/黑)，这是 "日志里有 Warning、画面却异常" 的典型场景 (与基础主题同类故障)。
- **成功**：InfoLog 通常为空串 (驱动相关，个别驱动会写一条 "no errors" 类提示)；本节示例成功路径不打印 InfoLog。

### 本节日志行格式 (写入 `starsector.log`)

| 事件 | 行格式 (`GearIn pulse shader` 前缀)|
|---|---|
| 加载成功 | `init: program=<id>, vbo=<id>, u_offset=<loc>, u_time=<loc>, vert=<vert 路径>, frag=<frag 路径>` |
| 编译失败 | `compile failed: <InfoLog>`(ERROR)|
| 链接失败 | `link failed: <InfoLog>`(ERROR)|
| 文本读取失败 | `loadText failed: <异常消息>`(ERROR)|
| 重载成功 | `reload: program=<新 id>` |
| 重载失败 | `reload failed: keeping old program=<旧 id>`(ERROR)|
| 清理 | `cleanup` |

::: tip 用日志行里的路径定位 "失败的是哪个文件"
init 日志行携带 `vert=`/`frag=` 路径：多对 shader 并存时，靠它区分 "失败的是哪个文件"。建议每条加载、重载与失败日志都带上路径 (建议 12)。
:::

## 资源生命周期

程序对象建好之后还不是终点：它绑定在战斗的 GL 上下文上，必须随战斗加载、使用、清理，并支持修改源文件后重载。

四个环节，全部可观察：

```text
加载（init：loadText → 编译 → 链接）→ 使用（render：glUseProgram + glUniform* + glDrawArrays）
→ 清理（cleanup：glDeleteProgram + 句柄复位）→ 重载（按同一路径删除重建，周期重复）
```

| 环节 | 时机与做法 | 依据 |
|---|---|---|
| 加载 | 每场战斗 `init` 内；失败即 `valid = false` 自废 (引擎随后移除插件)| 分层渲染通道；本节示例 |
| 使用 | 只在 `render(layer, viewport)` 回调内 `glUseProgram`/`glUniform*`/`glDrawArrays`，结束 `glUseProgram(0)` | 分层渲染使用纪律延续；GLSL 基础主题同款 |
| 清理 | `cleanup()` 里 `glDeleteProgram` 并复位句柄；**战斗结束 `destroyInstance` 不调 `cleanup()`**，靠 `advance()` 主动失效 (`isExpired() → true`) 驱动引擎移除 | 分层渲染清理纪律；成熟实例同款做法 |
| 重载 | 按同一路径删除旧程序、重新构建；**先构建成功再替换 (build-then-swap)**，构建失败保留旧程序 | 本节做法 (实例无重载先例)|

重载的意义：程序与 GL 上下文绑定，源文本 (`loadText` 的 String) 是瞬时值 —— 修改 `.frag` 后重新构建程序才能看到新效果；重载循环把 "删除 + 按同一路径重建" 变成可重复验证的生命周期完整流程 (本节每 10 秒一次，日志里 `program=<id>` 每次变化)。

实例知识规范的陷阱 "shader program 已失效仍继续绑定，会导致渲染输出错位或 OpenGL 状态污染" 直接对应 "清理/重载后继续使用旧句柄" 的失败模式 (规则 7)。

## 必须遵守的规则汇总

前几节的事实按强度收拢成下表，完整最小例会逐条兑现其中的必须项。

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | shader 资源路径用 MOD 根目录相对路径 + `/` 分隔 (`SettingsAPI.loadText` javadoc；`validateFileName` 实证)| `\`、盘符冒号、`..`、首字符 `/` → `loadText` 前 `validateFileName` 抛 `RuntimeException`(所有平台)；其余错写 → `IOException`(找不到资源)|
| 2 | **必须** | shader 源文件必须 UTF-8 无 BOM(读取端按 UTF-8 解码、只剥 `\r`、不剥 BOM)| BOM 字符进入 GLSL 源码 → `glCompileShader` 失败，InfoLog 报首行错误；CRLF 换行安全 (`\r` 被剥除)|
| 3 | **必须** | 加载必须捕获 `IOException` 并记 ERROR 日志 (GraphicsLib `PostProcessShader` 做法)| 路径缺失/读取失败时 `loadText` 抛 `IOException`；不捕获 → 异常从 `init` 传播；捕获但不记日志 → 失败不可见 |
| 4 | **必须** | 双参 `loadText(path, modId)` 的 modId 必须与 `mod_info.json` 的 `id` 逐字一致且 MOD 已启用 | `getModSpec` 返回 null → 调用点 NPE |
| 5 | **必须** | 编译/链接后检查 `GL_COMPILE_STATUS` / `GL_LINK_STATUS`，失败读取 InfoLog 并删除对象 (GLSL 基础主题必须遵守的规则延续)| 错误程序继续使用：内容不出现或渲染异常，失败原因不可见 |
| 6 | **必须** | 编译/链接失败的信息日志必须由 MOD 侧以 ERROR 写入 `starsector.log` | 游戏侧对 `loadText`/编译零日志；不写日志 → 失败完全静默 (画面无内容且无诊断)|
| 7 | **必须** | GL 资源生命周期完整流程：加载 (每场战斗 init)→ 使用 (render 内)→ 清理 (`cleanup()` 内 `glDeleteProgram`)→ 重载 (删除后按同一路径重建)；失效句柄不得继续绑定 (分层渲染纪律延续；实例陷阱)| 程序对象随战斗泄漏 (`destroyInstance` 不调 `cleanup()`)；已删句柄继续绑定 → 渲染输出错位或 OpenGL 状态污染 |
| 8 | **必须** | 重载采用 "重建成功才替换"：先构建新程序，成功才删除旧程序并换入 | 先删后建且构建失败 → 画面中断且无旧程序可回退 (本节故障注入 8 验证)|
| 9 | **必须** | 程序与 VBO 每场战斗重建，禁止跨战斗缓存 (`Global` 生命周期纪律延续)| 跨战斗引用已删句柄 → 未定义行为/渲染异常 |
| 10 | 建议 | `data/shaders/` 目录约定、同基名成对、语义命名 (GraphicsLib/430 实例惯例)| 无功能错误；维护性与实例一致性 |
| 11 | 建议 | `glGetUniformLocation` 返回值判 -1(GLSL 基础主题建议延续)| 无功能错误；只影响诊断 (uniform 拼错时静默不生效)|
| 12 | 建议 | 日志行携带 vert/frag 路径 (本节 init 行)| 无功能错误；多对 shader 时能定位 "失败的是哪个文件" |

## 完整最小例

规则是散的，下面用一个类把路径、加载、日志与生命周期四件事串成一个可运行的最小例。在既有 MOD 布局与战斗插件体系之上新增两个 shader 资源与一个插件类 (不新增任何注册项、不改动任何既有文件)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── shaders\
│       ├── GearIn_pulse_shader.vert      # 本节新增
│       └── GearIn_pulse_shader.frag      # 本节新增
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin              # MOD 插件主题建立；本节不改动
│       ├── GearInGlobalAccess           # 全局访问主题建立；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin          # 战斗状态主题建立；本节只消费
│           ├── GearInCombatLifecyclePlugin      # 生命周期主题建立；本节只消费
│           ├── GearInLayeredRenderer            # 分层渲染主题建立；本节只消费
│           └── GearInPulseRenderer              # 本节新增
└── jars\
```

shader 资源放在 `data\shaders\`(MOD 根目录相对路径，与 GraphicsLib `data/shaders/post/pre.vert` 同款布局)；构建阶段 (`gradle jar`) 会把 `src/main/resources` 下的同一对文件打进 `jars\GearIndustry.jar` 作为构建产物，运行以松散文件为准 (见 "交付形态")。

### GearIn_pulse_shader.vert

GLSL 1.10 方言 (`#version 110`)。与基础主题的顶点着色器 `GearIn_basic_shader.vert` 结构一致 (attribute/uniform/varying、`gl_ModelViewProjectionMatrix`)，本节侧重演示加载与生命周期，不引入新的数据流概念：

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

### GearIn_pulse_shader.frag

片元着色器以 `v_uv`(四边形内插值 UV) 与 `u_time`(每帧提交的秒数) 计算一个从中心向外扩散的脉冲环：

- `d = distance(v_uv, vec2(0.5, 0.5))` 是到四边形中心的距离；
- `ring = 1.0 - smoothstep(0.30, 0.45, d)` 在半径 0.30–0.45 的环带内从 1 渐变到 0；
- `pulse = 0.5 + 0.5 * sin(u_time * 4.0 - d * 24.0)` 是随 `u_time` 增大、随 `d` 减小的相位 ——`u_time * 4.0 - d * 24.0` 是一个向外传播的行波 (离中心越远相位越落后)：

```glsl
#version 110

uniform float u_time;

varying vec2 v_uv;

void main() {
    float d = distance(v_uv, vec2(0.5, 0.5));
    float ring = 1.0 - smoothstep(0.30, 0.45, d);
    float pulse = 0.5 + 0.5 * sin(u_time * 4.0 - d * 24.0);
    vec3 color = vec3(0.9, 0.3, 0.1) * ring * (0.3 + 0.7 * pulse);
    gl_FragColor = vec4(color, 1.0);
}
```

四边形外 (`d > 0.45`) 颜色为黑 (`ring = 0`)，环带内为橙色脉冲 —— 画面上的 "脉冲环向外扩散" 就是 `u_time` 每帧提交的**使用环节**表现。

### GearInPulseRenderer

Java 接入：在分层渲染插件通道上，四个环节各有一个落点 —— `init` 加载、`render` 使用、`cleanup` 清理、`advance` 内定时重载。

源码如下：

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
 * 完整最小例：.vert/.frag 的路径、加载、编译日志与资源生命周期。
 *
 * 路径与加载事实：
 * - shader 没有注册表 ID（区别于 CSV/JSON/spec 注册链）：.vert/.frag 以 MOD 根目录
 *   相对路径为身份，约定放在 data/shaders/（GraphicsLib data/shaders/post/pre.vert、
 *   data/shaders/ 同款布局），路径必须用 / 分隔（SettingsAPI.loadText javadoc
 *   硬规则）；反斜杠、盘符冒号、.. 与首字符 / 会在读取前被 validateFileName 直接拒绝
 *   （启动器设置的 validateFileName 抛 RuntimeException）；
 * - 读取端行为（文本读取工具的 readText）：按 UTF-8 解码并把全部 \r 剥除，不剥离
 *   BOM—— 源文件必须 UTF-8 无 BOM（BOM 字符会进入 GLSL 源码导致编译失败），CRLF 安全；
 *   loadText 本身不打日志，编译/链接日志由 MOD 侧写入 starsector.log；
 * - 编译/链接链（延续 C14-S01）：glCreateShader -> glShaderSource -> glCompileShader ->
 *   glGetShaderi(GL_COMPILE_STATUS)；glCreateProgram -> glAttachShader ->
 *   glBindAttribLocation（链接前固定）-> glLinkProgram -> glGetProgrami(GL_LINK_STATUS)；
 *   失败时读取 glGetShaderInfoLog / glGetProgramInfoLog（含行号与原因）并以 ERROR 写入
 *   starsector.log，然后删除对象（GraphicsLib ShaderLib.createShader/loadShader 同款）；
 * - 生命周期四环节：加载（每场战斗 init 内执行，不跨战斗缓存）-> 使用（render 回调内
 *   glUseProgram + glUniform* + glDrawArrays）-> 清理（cleanup() 内 glDeleteProgram，
 *   战斗结束 destroyInstance 不调 cleanup()，由 advance() 主动失效驱动引擎移除，
 *   C07-S06 硬规则延续）-> 重载（按同一路径删除后重建；本节采用"重建成功才替换"：
 *   新程序构建失败时保留旧程序、画面不中断，ERROR 日志给出 InfoLog 作为第一证据）。
 */
public class GearInPulseRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInPulseRenderer.class);

    /** shader 资源路径（MOD 根目录相对路径，必须用 /，SettingsAPI.loadText javadoc）。 */
    private static final String VERT_PATH = "data/shaders/GearIn_pulse_shader.vert";
    private static final String FRAG_PATH = "data/shaders/GearIn_pulse_shader.frag";

    /** 演示四边形半边长（世界单位）。 */
    private static final float QUAD_HALF_SIZE = 60f;

    /** 演示寿命（秒）：到时主动失效，驱动引擎移除并调用 cleanup()。 */
    private static final float LIFETIME = 30f;

    /** 重载间隔（秒）：按同一路径删除并重建 program，演示资源生命周期闭环。 */
    private static final float RELOAD_INTERVAL = 10f;

    /** 交错顶点布局：位置(2 float) + UV(2 float)，每顶点 4 个 float。 */
    private static final int FLOATS_PER_VERTEX = 4;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;
    private float reloadTimer = 0f;

    private int program = 0;
    private int vboId = 0;
    private int uOffsetLocation = -1;
    private int uTimeLocation = -1;

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        if (!buildQuad() || !loadProgram()) {
            valid = false; // 初始化失败：不渲染，等引擎正常移除
            return;
        }
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点（C07-S06 同款姿势）
        LOG.info("GearIn pulse shader init: program=" + program + ", vbo=" + vboId
                + ", u_offset=" + uOffsetLocation + ", u_time=" + uTimeLocation
                + ", vert=" + VERT_PATH + ", frag=" + FRAG_PATH);
    }

    /** 首次加载：按路径读取源文本并构建 program；失败时 renderer 自废。 */
    private boolean loadProgram() {
        program = buildProgram();
        if (program == 0) {
            LOG.error("GearIn pulse shader: loadProgram failed, renderer disabled");
            return false;
        }
        return true;
    }

    /**
     * 重载：按同一路径重新构建 program。新程序构建成功才删除并替换旧程序（build-then-swap）；
     * 构建失败（源文件被改坏、路径失效等）时保留旧程序继续绘制，ERROR 日志给出 InfoLog。
     */
    private void reloadProgram() {
        int newProgram = buildProgram();
        if (newProgram == 0) {
            LOG.error("GearIn pulse shader reload failed: keeping old program=" + program);
            return;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
        }
        program = newProgram;
        LOG.info("GearIn pulse shader reload: program=" + program);
    }

    /** 读取两个源文件、编译、链接并检查状态；成功返回 program，失败返回 0。 */
    private int buildProgram() {
        String vertSource;
        String fragSource;
        try {
            vertSource = Global.getSettings().loadText(VERT_PATH);
            fragSource = Global.getSettings().loadText(FRAG_PATH);
        } catch (IOException e) {
            LOG.error("GearIn pulse shader: loadText failed: " + e.getMessage(), e);
            return 0;
        }

        int vert = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        if (vert == 0) {
            return 0;
        }
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);
        if (frag == 0) {
            GL20.glDeleteShader(vert);
            return 0;
        }

        int prog = GL20.glCreateProgram();
        if (prog == 0) {
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            return 0;
        }

        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        // attribute 位置必须在链接前固定，之后 glVertexAttribPointer 用同一位置
        GL20.glBindAttribLocation(prog, 0, "a_position");
        GL20.glBindAttribLocation(prog, 1, "a_uv");
        GL20.glLinkProgram(prog);

        // 链接完成后着色器对象即可删除：程序已持有编译结果
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn pulse shader: link failed: " + getProgramLog(prog));
            GL20.glDeleteProgram(prog);
            return 0;
        }

        uOffsetLocation = GL20.glGetUniformLocation(prog, "u_offset");
        uTimeLocation = GL20.glGetUniformLocation(prog, "u_time");
        return prog;
    }

    /** 编译单个 shader 并检查 GL_COMPILE_STATUS；失败时记录 InfoLog、删除对象并返回 0。 */
    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            LOG.error("GearIn pulse shader: glCreateShader returned 0 (type=" + type + ")");
            return 0;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("GearIn pulse shader: compile failed: " + getShaderLog(shader));
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
            LOG.error("GearIn pulse shader: glGenBuffers returned 0");
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
            valid = false; // 主动失效：驱动引擎移除并调用 cleanup()（C07-S06 硬规则 3）
            return;
        }
        // 重载循环：按同一路径删除并重建 program（资源生命周期闭环）
        reloadTimer += amount;
        if (reloadTimer >= RELOAD_INTERVAL) {
            reloadTimer = 0f;
            reloadProgram();
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
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        LOG.info("GearIn pulse shader cleanup");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (program == 0 || vboId == 0) {
            return;
        }
        // 世界→屏幕正交投影：按 GraphicsLib renderForeground 姿势自行设置
        // （glOrtho(viewport) 以世界单位建投影，org.dark.shaders.util.ShaderLib.renderForeground() 同款姿势），
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

        // uniform：本次绘制内所有顶点/片元共享；u_offset 每帧更新为玩家舰船位置，
        // u_time 驱动片元侧脉冲相位（暂停时不累计，与 advance 暂停守卫一致）
        if (uOffsetLocation >= 0) {
            Vector2f center = engine.getPlayerShip().getLocation();
            GL20.glUniform2f(uOffsetLocation, center.x, center.y);
        }
        if (uTimeLocation >= 0) {
            GL20.glUniform1f(uTimeLocation, engine.getTotalElapsedTime(false));
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

- **加载 (init)**：`buildQuad()` 建 VBO；`loadProgram()` → `buildProgram()`：`loadText` 读两个源文件 (规则 1/2 的路径与编码做法)→ `compileShader` 逐个编译并查 `GL_COMPILE_STATUS`(规则 5)→ 链接前 `glBindAttribLocation` 固定 0/1(attribute 位置固定规则)→ 查 `GL_LINK_STATUS`(规则 5)→ 取 `u_offset`/`u_time` location(建议 11)。任何一步失败返回 0 并在 `starsector.log` 留下 ERROR(规则 6)；`loadProgram` 失败 → `valid = false` 自废。
- **重载 (advance)**：每 `RELOAD_INTERVAL` 秒调用一次 `reloadProgram()`：先 `buildProgram()` 构建新程序 (不删旧程序)，成功才 `glDeleteProgram` 旧程序并换入 (规则 8)；失败保留旧程序并 ERROR。
- **使用 (render)**：自行 `glOrtho(viewport)` 世界→屏幕正交投影 (矩阵成对 push/pop)→ `glUseProgram` + `glUniform2f(u_offset)` + `glUniform1f(u_time)`(`getTotalElapsedTime(false)`，暂停冻结)→ VBO + `glVertexAttribPointer`(long 偏移)+ `glDrawArrays` → 解除绑定、`glUseProgram(0)`。
- **清理 (cleanup)**：`glDeleteBuffers` 删 VBO、`glDeleteProgram` 删程序，句柄全部复位 (规则 7)；`advance` 在 `LIFETIME` 秒后 `valid = false` 主动失效驱动引擎移除 (`destroyInstance` 不调 `cleanup()`)。

注册片段 (工程片段 —— 本节不改动生命周期插件的任何文件，只演示使用方调用；把下面一行加进 `GearInCombatLifecyclePlugin.init(CombatEngineAPI engine)` 末尾)：

```java
    // 工程片段：GearInCombatLifecyclePlugin.init(CombatEngineAPI engine) 内追加
    engine.addLayeredRenderingPlugin(new GearInPulseRenderer());
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，与编译链路一致)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/shaders/GearIn_pulse_shader.vert`、`data/shaders/GearIn_pulse_shader.frag` 与 `data/scripts/gearin/combat/GearInPulseRenderer.class`(可用 `jar tf` 核对)。

## 验证方案

前置数据：编译链路建立的 MOD 布局 (C06-S01)、`GearIn_test` 测试战役 (C03-S01)、战斗插件体系 (C07-S05，含本节注册片段一行)、分层渲染插件 (C07-S06)、本节新增的 `GearInPulseRenderer` 类与 `data\shaders\GearIn_pulse_shader.vert/.frag`。

操作：

   确认以 `BUILD SUCCESSFUL` 结束 (退出码 0)。

   确认以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
1. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，再用 `jar tf jars\GearIndustry.jar` 核对产物清单。
2. 战役冒烟：进入 `GearIn_test` 一场战斗，在 `starsector.log` 中核对 init 行。
3. 画面：观察玩家舰船中心 120 世界单位见方的四边形。
4. 重载与暂停：记录战斗开始约 10 秒与 20 秒时的日志；按空格暂停约 10 秒，再恢复。
5. 寿命与跨战斗：等待约 30 秒；随后退出战斗，再进一场。
6. 故障注入：a) 把 `.vert` 路径改成反斜杠写法；b) 给 `.frag` 加 BOM 或删一行分号；c) Java 侧把 `"u_time"` 拼错；d) 把 `reloadProgram` 改成先删后建并破坏 `.frag`；e) 全部改回，确认恢复。

预期结果：

1. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/shaders/GearIn_pulse_shader.vert`、`data/shaders/GearIn_pulse_shader.frag` 与 `data/scripts/gearin/combat/GearInPulseRenderer.class`。
2. **战斗初始化 (加载 + 日志)**：`starsector.log` 出现一行：

   ```text
   GearIn pulse shader init: program=<id>, vbo=<id>, u_offset=<loc>, u_time=<loc>, vert=data/shaders/GearIn_pulse_shader.vert, frag=data/shaders/GearIn_pulse_shader.frag
   ```

   其中 `vert=`/`frag=` 与资源文件路径一致 (四位置完整流程)；没有由 `gear_in` 引起的 `ERROR`。
3. **画面 (使用)**：玩家舰船中心出现一个 120 世界单位见方的四边形，内部橙色脉冲环亮度随时间起伏，起伏呈 "从中心向外扩散" 的波纹 (`u_time * 4.0 - d * 24.0` 行波)；四边形位于舰船与导弹之上 (`ABOVE_SHIPS_AND_MISSILES_LAYER`，分层渲染层序)；舰船移动时四边形跟随 (`u_offset` 每帧更新)。
4. **重载循环 (生命周期)**：约 10 秒与 20 秒时各出现一行 `GearIn pulse shader reload: program=<新 id>`，两次的 `program=<id>` 都与 init 行不同、彼此也不同 (旧程序已被 `glDeleteProgram` 删除、按同一路径重建)；期间没有 ERROR。
5. **暂停行为**：按空格暂停 —— 脉冲波纹冻结 (`getTotalElapsedTime(false)` 不累计)、四边形静止但可见 (与分层渲染主题同款)；恢复后继续。
6. **寿命与清理**：约 30 秒后四边形消失，`starsector.log` 出现一行：

   ```text
   GearIn pulse shader cleanup
   ```

   即主动失效 → 引擎移除 → `cleanup()` 的正常链 (规则 7 的可观察面)，同一场战斗内不再出现。
7. **跨战斗**：退出战斗再进一场 —— init 行重新出现且 `program=<id>` 全新 (每场战斗重建，跨战斗缓存禁令)；无 ERROR。
8. **故障注入**：a 注入后 `loadText` 读取前抛 `RuntimeException` (`validateFileName` 拒绝，对应故障分析第 1 条)；b 注入后 `compile failed:` 带行号 (BOM 报首行，对应第 2、3 条)；c 注入后脉冲静止且无日志 (对应第 6 条)；d 注入后画面中断、无回退 (对应第 8 条)；e 全部改回后重新观察，结果与注入前一致。

清理：取消勾选测试 MOD；恢复代码与 shader 为原值

## 故障分析

1. **路径语法写错**：`loadText` 在读取前被 `validateFileName` 拒绝，抛 `RuntimeException` (所有平台，不只是 OS X/Linux)，异常从 `init` 传播；把路径改成 `"data/GearIn_pulse_shader.vert"` (漏目录) 则抛 `IOException`，日志出现 `GearIn pulse shader: loadText failed:`。触发条件：把 `VERT_PATH` 改成 `"data\\shaders\\GearIn_pulse_shader.vert"` (反斜杠) 或盘符前缀的路径。原因：`validateFileName` 对含 `\`、盘符冒号、`..` 或首字符 `/` 的路径直接拒绝 (规则 1)。修正：恢复 MOD 根相对 + `/` 分隔，对照四位置完整流程核对路径。
2. **源文件带 BOM**：编译失败，`starsector.log` 出现 `GearIn pulse shader: compile failed: ERROR: 0:1: ...` (首行 token/字符错误，驱动相关)，init 后无画面。触发条件：用带 BOM 的编辑器保存 `.frag`。原因：读取端按 UTF-8 解码但不剥 BOM，`\uFEFF` 进入 GLSL 源码。修正：保存为 UTF-8 无 BOM (规则 2；CRLF 换行则安全)。
3. **GLSL 语法错误**：编译失败，InfoLog 含行号与原因，如 `ERROR: 0:5: 'xxx' : syntax error` —— 行号直接指向出错行；init 后无画面，但游戏不崩溃 (状态检查生效，规则 5)。触发条件：删掉 `.frag` 某行分号。修正：按 InfoLog 行号定位出错行。
4. **方言版本与硬件不匹配**：GL 2.0 基线上下文编译失败，InfoLog 含 version 错误。触发条件：把 `#version 110` 改成 `#version 130` 或更高。修正：基线用 `#version 110` (方言基线规则)。
5. **不检查编译/链接状态**：错误程序继续使用 —— 内容不出现且失败原因不可见 (与基础主题同类)。触发条件：注释掉 `compileShader` 里的 `GL_COMPILE_STATUS` 检查。修正：状态检查 + InfoLog + 删除对象三连 (规则 5)。
6. **uniform 名拼错**：画面出现但脉冲完全静止，且没有任何日志 (GL 错误不报)。触发条件：把 `"u_time"` 改成 `"uTime"`。原因：`glGetUniformLocation` 返回 -1，`glUniform1f` 被静默忽略。修正：核对名称与类型 (uniform 命名与类型规则)，保留判 -1 (建议 11)。
7. **varying 两侧不一致**：链接只给警告而不失败，片元读到未定义值 —— 环带颜色恒为 0/黑 (与基础主题同类故障)。触发条件：把 `.frag` 的 `v_uv` 改名而 `.vert` 不改。修正：两侧同名同类型 (varying 匹配规则)。
8. **重载先删后建**：构建失败 (如源文件此时被改坏) 时旧程序已被删除 —— 画面中断 (四边形消失) 且无回退；本节的 build-then-swap 版本则画面保持，日志出现 `reload failed: keeping old program=<id>` ERROR。触发条件：把 `reloadProgram` 改成先 `glDeleteProgram(program)` 再 `buildProgram()`。修正：重建成功才替换 (规则 8)。
9. **重载后继续绑定旧句柄**：渲染输出错位或 OpenGL 状态污染 (实例陷阱原话)，且随每次重载恶化。触发条件：`reloadProgram` 成功后渲染仍 `glUseProgram` 旧的 program id。修正：重载后统一从字段取新 id (规则 7)。
10. **跨战斗缓存程序**：第二场战斗引用已删句柄 —— 渲染异常/未定义行为 (跨战斗缓存禁令)。触发条件：把 `program` 存进静态字段，第二场战斗不重建。修正：每场战斗 `init` 重建 (规则 9)。
11. **双参 loadText 的 modId 写错**：调用点直接 NPE。触发条件：把 `loadText(path, "gear_in_typo")`。原因：`getModSpec` 返回 null (与资源读取规则同规)。修正：modId 与 `mod_info.json` 的 `id` 逐字一致 (规则 4)。

## 总结

本节把 shader 从磁盘文件接到了战斗画面，整条链可以用一句话收拢：shader 没有注册表 ID，路径就是身份。排错时先沿四个位置核对路径 (资源文件、加载入口常量、使用方、日志)，再看 `starsector.log`：init 行证明文件被读到，`compile failed` / `link failed` 带行号直接指向源文件问题；画面异常但日志干净时，才需要怀疑 uniform 拼写、varying 匹配这类不报错的问题。

生命周期的判断同样可以复用：程序对象随战斗加载与清理，重载按同一路径重建，且只在新程序构建成功后才替换旧程序。按这个顺序检查，改坏 shader 时损失的是效果而不是画面，跨战斗也不会带着已删句柄继续绘制。

从逻辑上看，本节停在 "程序建好、按生命周期维护" 这一环。要让程序承担更完整的视觉效果，剩下两件事：把数据送进程序 (uniform、纹理与矩阵提交)，以及把渲染插件放进更大的工程结构 (渲染层接入、资源释放、开关与性能)。二者都建立在本节 "路径即身份、生命周期完整流程" 的底座上。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadText(String)` 与 `loadText(String, String)` 的文本读取入口；双参按 modId 限定资源空间。
- `com.fs.starfarer.api.Global`：`getSettings()`、`getLogger(...)`、`getCombatEngine()` 的全局入口。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`getTotalElapsedTime(boolean)`、`getPlayerShip()`、`isPaused()`、`addLayeredRenderingPlugin(...)` 的战斗内使用面。
- `com.fs.starfarer.api.combat.ViewportAPI`：`getLLX()` / `getLLY()` / `getVisibleWidth()` / `getVisibleHeight()` 视口取值。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：渲染层枚举，本例使用 `ABOVE_SHIPS_AND_MISSILES_LAYER`。
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层渲染插件基类 (本例继承)。
- `org.lwjgl.opengl.GL20` / `GL15` / `GL11`：编译、链接、状态检查、VBO 与绘制调用。
- `data/shaders/GearIn_pulse_shader.vert` / `data/shaders/GearIn_pulse_shader.frag`：本节 shader 资源，与 Java 常量 `VERT_PATH` / `FRAG_PATH` 逐字一致 (MOD 根相对路径)。
- `data/shaders/`：GLSL 顶点与片元资源的目录约定。
- `starsector.log`：本节所有日志可观察结果 (游戏安装根相对)。
- `org.dark.shaders.util.ShaderLib` / `org.dark.shaders.post.PostProcessShader`(GraphicsLib)：加载/链接失败做法的实例对照。
- `org.boxutil.util.ShaderUtil`：另一条加载入口 `createShaderVFFormPath(...)`(依赖库使用入口)。
