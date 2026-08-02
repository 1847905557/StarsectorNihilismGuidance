# MagicTrail 尾迹：创建、推进与渲染

本页把 MagicLib 1.5.1 的 `MagicTrailPlugin` 尾迹系统讲透，覆盖数据、创建、推进、渲染、清理五个面：`trail` CSV 行与跨 MOD 合并、`getUniqueID` + `addTrailMember*` 登记段、段生命周期与逐帧推进、分层渲染与纹理循环、段过期与切断。MagicLib 的概览、硬依赖与设置接入在前置页已述，本页补全其中"完整尾迹 API 不在本页范围"的留白。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 说出 MagicLib 1.5.1 的尾迹数据链路：`magicTrail_files` 列表 → `getMergedSpreadsheetDataForMod("trail", path, "MagicLib")` 跨 MOD 合并 → `MagicAutoTrails` 按弹体 ID 匹配，并背出 30 列 `trail` 数据行的每一列语义（含 `minLength` 负数 = 慢速模式、`renderBelowExplosions` 的层映射、`angleAdjustment` 的 `BALLISTIC_AS_BEAM` 禁令）；
- 说出 `MagicTrailPlugin` 的战斗注册链（MagicLib 自己的 `settings.json` `plugins` 段 → 每场战斗新实例 → `init` 创建 `MagicTrailRenderer` 并写入 `engine.getCustomData()`），解释为什么 `getPlugin()` 可能返回 null、此时 `addTrailMember*` 会**静默丢弃**；
- 用 `getUniqueID()` + `addTrailMemberSimple` / `addTrailMemberAdvanced` 写出给任意实体挂尾迹的最小调用，背出三条创建时必须遵守的规则：ID 生命周期内复用、同一条 trail 中途不得换 sprite、sprite 宽高必须为 2 的倍数；
- 说出段（`MagicTrailObject`）的推进语义：总寿命 = `inDuration + mainDuration + outDuration`、尺寸/颜色/速度/角速度线性插值、透明度三段曲线、寿命到期即移除，以及"暂停时整条尾迹冻结"（`MagicTrailPlugin.advance` 的暂停守卫）；
- 说出渲染链路：`MagicTrailRenderer` 是全层注册的分层渲染插件、每条 tracker 一个渲染层、混合模式取**最新**一段、纹理按 `textureLoopLength`（≤0 整条拉伸）与 `textureScrollSpeed`（1000 = 每秒整张）滚动、逐段视口剔除、首尾各两段透明度渐弱；
- 说出清理链路：段到期移除、tracker 空置 3 秒后过期、每秒清理计时、`cutTrailsOnEntity` 的切断语义与"传送前必须切断"这项必须遵守的规则、library-free cut 入口（customData 键含 `MagicTrailPlugin_LIB_FREE_TRAIL_CUT`），以及全部状态只属于当前这场战斗（禁止跨战斗缓存）；
- 用 `GearInMagicTrail` 完整最小例（Java 手动尾迹两条 + 数据驱动 `data/trails/trail_data.csv` 一行）走通"数据 → 创建 → 推进 → 渲染 → 清理"完整流程，并从 `starsector.log` 与战斗画面定位本页列出的典型故障。

## 前置知识

本页建立在以下前置主题之上：

- MagicLib 概览与 `MagicTrailPlugin` 最小纪律（S12-P04）：sprite 宽高为 2 的幂、ID 跨帧复用、中途不换 sprite；`MagicSettings.getList` 与 `modSettings.json` 合并读取（本页 `magicTrail_files` 列表走的就是这条链）。
- 硬依赖声明与运行时检测（S12-P01）：引用 `org.magiclib.plugins.MagicTrailPlugin` 前 `dependencies` 必须声明 `MagicLib`（用户缺依赖 → `NoClassDefFoundError`）。
- LazyLib 纪律（S12-P02）：`MagicTrailPlugin` 与 `MagicAutoTrails` 内部使用 `MathUtils` / `VectorUtils`；本页示例不直接依赖 LazyLib。
- 射弹运行态（S07-P03）：数据驱动尾迹的匹配对象是 `DamagingProjectileAPI.getProjectileSpecId()`；`GearIn_test_shot` 弹体 ID 由该页声明，本页只消费。
- 战斗插件生命周期（S07-P04）：settings `plugins` 段每场战斗 `newInstance`、`advance` 必须先判 `engine == null`、暂停守卫、战斗结束无清理回调。
- 分层渲染（S07-P05）：`CombatLayeredRenderingPlugin` 与 `CombatEngineLayers` 层序（本页 `MagicTrailRenderer` 就是一条分层渲染插件通道）。
- 战斗插件模型与 `addPlugin` 注册（S02-P01）：本页示例插件的骨架。
- `GearIn_test` 测试战役（S03-P01）：战斗侧冒烟入口。
- `Global` 生命周期（S06-P03）：战斗外 `getCombatEngine()` 是非 null 空引擎；战斗外调用 `addTrailMember*` 走 `getPlugin()` 的 null 分支，静默无效果。

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 尾迹数据 | `data/config/modFiles/magicTrail_data.csv`（MagicLib 自带默认，恒被追加读取）与 `data/trails/trail_data.csv`（`magicTrail_files` 默认列表项，兼容路径）| `Magic_modPlugin.onApplicationLoad` → `MagicAutoTrails.getTrailData` → `getMergedSpreadsheetDataForMod("trail", path, "MagicLib")` |
| 文件列表 | `modSettings.json` 的 `MagicLib` 段 `magicTrail_files`（默认 `["data/trails/trail_data.csv"]`）| `MagicSettings.getList(MagicVariables.MAGICLIB_ID, "magicTrail_files")`；列表恒追加 `data/config/modFiles/magicTrail_data.csv` |
| 尾迹插件 | `MagicTrailPlugin`（settings `plugins` 键 `MagicTrailPlugin`）| 每场战斗实例化；`init` 创建 `MagicTrailRenderer`、写入 customData 键 `MagicTrailPlugin` / `MagicTrailRenderer`、重置 ID 计数并清空地图 |
| 自动尾迹 | `MagicAutoTrails`（settings `plugins` 键 `MagicAutoTrails`）| 每场战斗按 `getProjectileSpecId()` 匹配数据行，为弹体自动生成尾迹段 |
| 段注册 | `MagicTrailPlugin.addTrailMemberSimple` / `addTrailMemberAdvanced`（静态）| `getPlugin()` 取当前战斗插件；null 时静默返回；默认层 `CONTRAILS_LAYER` |
| ID | `MagicTrailPlugin.getUniqueID()` | 静态计数；同一条尾迹生命周期内必须复用；负数 ID 保留给切断操作 |
| 切断 | `MagicTrailPlugin.cutTrailsOnEntity(entity)` | 把实体名下 tracker 换 key，旧段不再与新段相连；library-free cut 键 `MagicTrailPlugin_LIB_FREE_TRAIL_CUT` |
| 推进 | `MagicTrailPlugin.advance` → `MagicTrailTracker.tickTimersInTrail` → `MagicTrailObject.tick` | 暂停时整条停摆；段寿命到期移除；tracker 空置 3 秒过期、每秒清理 |
| 渲染 | `MagicTrailRenderer`（`MagicTrailPlugin` 内部类，`BaseCombatLayeredRenderingPlugin`）| `getActiveLayers()` = 全部层；`render` 按层取 tracker 绘制；段内视口剔除 |
| 贴图 | `Global.getSettings().getSprite("fx", key)`；MagicLib `settings.json` `graphics.fx` 分类 12 张 `base_trail_*` | `MagicAutoTrails.createTrailSegment`；示例类直接消费 |
| 可观察结果 | `starsector-core\starsector.log`；战斗画面 | 本页示例 `GearIn trail ...` 日志、舰船尾部两条尾迹、暂停冻结、切断行为 |

## MagicTrail 数据（trail CSV）

### 文件与合并加载链

MagicLib 1.5.1 的尾迹数据是 **CSV 行 + sprite 注册**两件套：

::: info
MagicLib 把尾迹参数拆成两条通道——数据驱动（CSV 行由 `MagicAutoTrails` 按弹体 id 自动匹配）与代码驱动（Java 每帧手动登记段）。数据驱动适合"弹体自带尾迹"，代码驱动适合"实体常驻尾迹"（舰船引擎、飞翼等）；两条通道互不干扰、可以并存——本页示例两者都演示。
:::

1. **文件**：默认读取两个来源——`MagicLib` 段 `magicTrail_files` 列表（MagicLib 自带 `modSettings.json` 的默认值 `["data/trails/trail_data.csv"]`，该路径是兼容路径，任何 MOD 都可以放自己的同名文件）恒被读取； `data/config/modFiles/magicTrail_data.csv`（MagicLib 自带默认文件，**恒追加**在列表末尾）。
2. **合并**：每个路径都走 `Global.getSettings().getMergedSpreadsheetDataForMod("trail", path, "MagicLib")`——`"trail"` 是键列，跨**全部已启用 MOD** 合并同路径文件，master 为 MagicLib（`getMergedSpreadsheetDataForMod` 的合并语义：master 行被非 master 同键行替换，见 S06-P04）。
3. **时机**：`Magic_modPlugin.onApplicationLoad()` 调用 `MagicAutoTrails.getTrailData()` 读一次；dev 模式 `onDevModeF8Reload` 重读，`MagicAutoTrails.init` 在 dev 模式下也会重读。**改尾迹数据必须重启游戏**（或 dev 模式 F8 重载 + 重进战斗）。
4. **缺失文件**：`data/trails/trail_data.csv` 不存在 → INFO 日志 `Deprecated 'data/trails/trail_data.csv' not found, skipping.`，不中止；其他路径读取失败 → warn。

::: warning
尾迹问题大多是静默的——行解析失败、`trail` 键撞车、插件未初始化都不会产生 `ERROR`；数据行改动还必须重启游戏（或 dev F8 重载 + 重进战斗）才生效。排查前先确认"已按新数据重启"与"日志无 `Invalid line, skipping`（dev 模式）"这两个前提。
:::

### 规则 1：trail 键必须全局唯一，弹体键与 sprite 键必须真实存在

数据行按 `trail` 列合并：与 MagicLib 或其他 MOD 撞 `trail` 键时，合并结果互相替换（master=MagicLib 优先），本 MOD 的行可能整个消失或参数变成对方的；

`projectile` 列必须对应一个真实存在的弹体 spec id（`.proj` 文件的 id）；`sprite` 列必须是 `settings.json` `graphics.fx` 分类里注册过的键。

违反的可观察结果：

- 撞 `trail` 键 → 两条尾迹只剩一条或参数错乱，无任何报错；
- `projectile` 写错 → 该行永远匹配不到任何射弹，尾迹不出现且无报错（`MagicAutoTrails.advance` 只是按键查表）；
- `sprite` 写错 → `getSprite("fx", key)` 在生成段时取不到贴图（资源键缺失在加载期或运行期报错）。

修正：`trail` 键用 MOD 专属前缀（示例 `GearIn_trail_test_shot`）；`projectile` 填弹体 spec id；自定义贴图先在自己的 `settings.json` 的 `graphics.fx` 分类注册。

### 规则 2：一行只配一条尾迹；一个弹体可以配多条尾迹（多行）

每行定义一条尾迹；同一 `projectile` 的多行按行序追加为多条尾迹（`PROJ_TRAILS` 按弹体 id 存列表）。

违反的可观察结果：想给一个弹体两段不同颜色/宽度的尾迹却只写一行 → 只有一条；把多条尾迹写进同一行（列错位）→ 整行解析失败被跳过（见规则 3）。

### 规则 3：行内任一字段类型错误或必填列缺失，整行被跳过

`getTrailData` 的整行解析包在 try/catch 里：

任何 `row.getString` / `row.getDouble` / `row.getBoolean` 抛 `JSONException`（列缺失、数字列写字母、颜色格式不对）→ 该行**整体跳过**，只有 dev/verbose 模式才输出 `Invalid line, skipping` warn。

可选列例外：`frameOffsetMult`（缺省 1）、`randomTextureOffset`（缺省 0）、`renderBelowExplosions`（缺省 false → `BELOW_INDICATORS_LAYER`）单独 try/catch，缺失不影响整行。

违反的可观察结果：一行尾迹"不出现"，日志无 ERROR（dev 模式才有 `Invalid line, skipping`）；把 `colorIn` 写成数字数组 `[255,0,0]` 而不是字符串 `"[255,0,0]"` → 该行被跳过。

修正：对照 30 列表头核对每一列；颜色必须是 `"[r,g,b]"` 带引号字符串（`MagicSettings.toColor3` 解析）。

### 30 列速查表（RC8 / MagicLib 1.5.1 表头）

表头取自 `Starsector\mods\MagicLib\data\config\modFiles\magicTrail_data.csv` 第一行（30 列；`frameOffsetMult` 位于 `drift` 之后、`fadeOnFadeOut` 之前，可省略）：

| 列 | 语义（官方注释 + 源码行为）|
|---|---|
| `trail` | 尾迹行键，**必须全局唯一**（合并键列）|
| `projectile` | 弹体 spec id（`.proj` 文件 id）；一个弹体可有多行 = 多条尾迹 |
| `sprite` | `settings.json` `graphics.fx` 分类的贴图键（MagicLib 自带 `base_trail_smooth` / `base_trail_contrail` / `base_trail_mild` / `base_trail_fuzzy` / `base_trail_rough` / `base_trail_smoke` / `base_trail_heavySmoke` / `base_trail_heavyBurn` / `base_trail_weave` / `base_trail_aura` / `base_trail_zap` / `base_trail_zapWithCore`，共 12 张）|
| `minLength` | 最小段间距（SU）。**负数 → 慢速模式**：专为速度低于 500 的弹体优化的节流生成（每 0.06–0.07 秒一段）|
| `fadeIn` | 每段渐入时长（秒）|
| `duration` | 每段满亮度时长（秒）|
| `fadeOut` | 每段渐出时长（秒）|
| `sizeIn` | 段最前端的宽度（SU）|
| `sizeOut` | 段最尾端的宽度（SU），从 `sizeIn` 线性渐变 |
| `colorIn` | 段最前端颜色，`"[r,g,b]"` 字符串 |
| `colorOut` | 段最尾端颜色，`"[r,g,b]"` 字符串，从 `colorIn` 线性渐变 |
| `opacity` | 段的最大透明度上限（0–1）|
| `textLength` | 纹理循环长度（SU）。≤0 → 整张纹理拉伸覆盖整条尾迹（示例 `-1`）|
| `textScroll` | 纹理滚动速度，1000 = 每秒滚动整张纹理一次（可负向）|
| `randomTextureOffset` | `TRUE` → 每条 tracker 固定一个随机纹理偏移（防重复纹理接缝）|
| `distance` | 段生成点相对弹体的偏移（沿弹体朝向，SU；源码用 `getPointOnCircumference(projLoc, distance, facing)`）|
| `dispersion` | 每段每秒随机化量（SU/s；叠加一个随机圆内向量）|
| `drift` | 继承自舰船的横向运动被应用到段的比例（0–1；与 `angleAdjustment` 配合使用）|
| `frameOffsetMult` | 每帧速度偏移倍率（可选，缺省 1；修正高速弹的偏移）|
| `fadeOnFadeOut` | `TRUE` → 弹体开始淡出时，段透明度按剩余伤害比例（`getDamageAmount/getBaseDamageAmount`）同步降低 |
| `angleAdjustment` | `TRUE` → 弹体朝向改为速度方向（**必须遵守的规则：`BALLISTIC_AS_BEAM` 弹体必须 `FALSE`**）|
| `additive` | `TRUE` → 叠加混合（dest blend `GL_ONE`）；`FALSE` → 普通混合（`GL_ONE_MINUS_SRC_ALPHA`）|
| `velocityIn` | 段生成时的速度（SU/s）|
| `velocityOut` | 段消亡时的速度（SU/s），线性渐变 |
| `randomVelocity` | 速度随机化比例（`±randomVelocity × rand` 作用于 velocityIn/Out）|
| `angle` | 尾迹相对弹体朝向的偏移角（度；默认 0 = 正后方；源码取 `facing - 180 + angle`）|
| `rotationIn` | 段生成时的角速度（度/秒）|
| `rotationOut` | 段消亡时的角速度（度/秒）|
| `randomRotation` | `TRUE` → 角速度随机化（与 `rotationIn/Out` 组合可做出非线性扩散）|
| `renderBelowExplosions` | `TRUE` → 渲染层 `ABOVE_SHIPS_LAYER`（爆炸之下，多为非 additive 尾迹用）；`FALSE`/缺省 → `BELOW_INDICATORS_LAYER` |

要点：

- **整条尾迹的视觉长度 ≈ 弹体速度 × 每段总时长**（`fadeIn + duration + fadeOut`）：段以弹体位置为起点、以自身速度移动，最老段在总时长后消失，新段在弹头持续生成——弹速越快或段寿命越长，尾迹越长。这是段参数与观感的换算关系（近似，段速不为 0 时按相对速度折算）。
- `minLength` 建议保持小值（MagicLib 注释的"最小段间距"语义，原版示例 10）。
  - 值过大 → 段间距大、段间直线连接，曲线弹道变成折线，时间加速时尤为明显。这是**建议**（旧教程的"不建议高于 10"在 RC8 语义下仍为软约束，不是引擎硬限制）。
- `additive` 建议开 `TRUE`（MagicLib 自带 `base_trail_*` 贴图大多按叠加混合设计；`renderBelowExplosions` 注释也说明该选项"mostly used with non additive trails"）。旧教程"极度建议写 true"在 RC8 仍为建议级。
- `angleAdjustment` 一般不建议用：它会改写弹体自身朝向（`proj.setFacing`），只应在需要尾迹与弹体朝向一致且 `drift<1` 时使用；`BALLISTIC_AS_BEAM` 弹体是**必须 `FALSE`**（数据文件注释以 `!!!! Must be FALSE for BALLISTIC_AS_BEAM projs !!!!` 强调，源码同样排除 `BALLISTIC_AS_BEAM`）。

### 数据驱动的使用端：MagicAutoTrails

`MagicAutoTrails` 是 MagicLib 自己注册的战斗插件（`settings.json` `plugins` 段的键 `MagicAutoTrails`）。

每场战斗 `init` 清空自己的追踪表， `advance` 里遍历 `engine.getProjectiles()`，按 `getProjectileSpecId()` 查表：

- **快速模式**（`minLength >= 0`）：该弹体移动超过 `minLength` 才生成一个新段——时间加速时自动省性能；
- **慢速模式**（`minLength < 0`）：按 0.06–0.07 秒定时器生成，注释说明"专为速度低于 500 的弹体优化"；
- 每个弹体、每条尾迹行各取一个 `getUniqueID()`，随后每段生成都复用同一 ID；
- 段生成时应用全部行参数：sprite、`distance` 偏移、`drift` 侧向速度、`dispersion` / `randomVelocity` / `randomRotation` 随机化、`fadeOnFadeOut` 透明度缩放、层与混合。

`MagicAutoTrails` 还提供代码注册入口：`createProjTrail(projId, trailData)` / `addProjTrail(projId, trailData)`，给不想写 CSV 的 MOD 用（`trailData` 是公开静态嵌套类，构造器与 CSV 列一一对应）。

## MagicTrail 创建（Java 手动尾迹）

### 插件与调用前置

`MagicTrailPlugin` 由 MagicLib 的 `data\config\settings.json` `plugins` 段注册（键 `MagicTrailPlugin` → `org.magiclib.plugins.MagicTrailPlugin`），每场战斗全新实例（settings 注册链见 S07-P04）。

`init(CombatEngineAPI)` 做四件事：

1. 创建 `MagicTrailRenderer` 并 `engine.addLayeredRenderingPlugin(renderer)`（渲染通道）；
2. 把自己与 renderer 写入 `engine.getCustomData()`（键 `MagicTrailPlugin` / `MagicTrailRenderer`）；
3. 重置 ID 计数（`usedIDs = 1f`、`usedCutterIDs = -1f`）；
4. 清空 `mainMap` / `cuttingMap`。

所有 `addTrailMember*` 与 `cutTrailsOnEntity` 都先 `getPlugin()`：从**当前战斗**引擎的 customData 取插件，引擎为 null 或插件尚未 init → 返回 null → 调用方**静默返回**。

### 规则 4：调用 addTrailMember* 前必须处于已初始化的战斗中；不要自己注册同名插件

`addTrailMember*` 的生效前提是 `MagicTrailPlugin` 实例已在本场战斗的 customData 里。

战斗外（主菜单、生涯画面）调用走 `getPlugin()` 的 null 分支，**静默无效果**；同场战斗中若另一个插件在 `MagicTrailPlugin.init` 之前调用，段同样被丢弃。

使用方 MOD 不需要也不应该在自己的 `settings.json` 里重复注册 `MagicTrailPlugin` / `MagicAutoTrails` 键。

违反的可观察结果：战斗外或过早调用 → 日志没有任何错误，尾迹不出现；把 MagicLib 的 `MagicTrailPlugin` settings 键覆盖成自己的类 → 渲染链缺失或重复实例化。

修正：只在战斗插件的 `advance`（或更晚的回调）里调用；不要动 MagicLib 的 settings `plugins` 键。

### getUniqueID：一条尾迹一个 ID，生命周期内必须复用

`getUniqueID()` 从静态计数 `usedIDs = 1f` 起每次返回 `+0.1f`；**负数 ID 是切断操作的保留区**（`usedCutterIDs` 从 `-1f` 递减）。

官方 javadoc 原文："must be saved in the function that generates the trail, since if it changes it counts as a new trail altogether"。

### 规则 5：同一尾迹的 ID 必须取一次并跨帧复用；同一条 trail 中途不得换 sprite

两件事互相独立：

- **每帧生成新 ID** → 每条都是独立的 1 片段尾迹，**无法连接成条**——画面是密集的短横条或毛刺，不是一条连续的尾迹（每帧一个 ID 的 tracker 各有一到两段，段与段不连锁）。
- **中途换 sprite** → `addOrGetTrailTracker` 按"层 + 纹理 id"分桶（`texID = sprite.getTextureId()` 参与分桶），同 ID 换贴图会落进不同的 `trailTrackerMap` → 一条尾迹被**拆成两条**（旧段按旧纹理淡出、新段从新纹理重新起条）。

违反的可观察结果：`base_trail_smooth` 与 `base_trail_contrail` 交替使用同一 ID → 尾迹断成两段纹理；每帧调 `getUniqueID()` → 尾部出现"点阵"。

修正：ID 在 `init`/构造器取一次存字段（成熟实例同款：构造器里 `this.uid = MagicTrailPlugin.getUniqueID()`）；sprite 恒定。

### 规则 6：参与尾迹的 sprite 宽高必须是 2 的倍数

类 javadoc 原文："any sprites that use this plugin must have a multiple of 2 as size (so 16, 32, 64, 128 etc.), both in width and height"。

MagicLib 自带 12 张贴图全部满足（`base_trail_*` 为 32×128，`heavySmoke` / `heavyBurn` 为 64×128），且为垂直长条——渲染沿纹理 V 轴拉伸（`glTexCoord2f(0/1, texLocator)`），长边即尾迹长度方向。

违反的可观察结果：非 2 的幂贴图出现纹理接缝、扭曲或渲染异常（OpenGL 纹理尺寸约束）。修正：自备贴图按 2 的幂切图（教程页无 PNG 资产，示例直接消费 MagicLib 自带贴图）。

### addTrailMemberSimple 与 addTrailMemberAdvanced

`addTrailMemberSimple(linkedEntity, ID, sprite, position, speed, angle, startSize, endSize, color, opacity, inDuration, mainDuration, outDuration, additive)`：

固定层 `CONTRAILS_LAYER`、段速度恒为 `speed`、颜色不变、无纹理参数。

段生成后留在生成点按自己的速度/角度移动，靠"每帧在实体当前位置生成新段"连成条带——**speed 传 0f** 是最常见的"实体尾迹"做法（成熟实例均传 0f）。

`addTrailMemberAdvanced(...)`（布尔重载 / 混合模式重载两种形态）额外提供：
- startSpeed/endSpeed、角速度、startColor/endColor；
- 纹理三参数（`textureLoopLength` / `textureScrollSpeed` / `textureOffset`）；
- `offsetVelocity`（不受旋转影响、终身不变的附加速度）；
- `advancedOptions`（`SIZE_PULSE_WIDTH` Float / `SIZE_PULSE_COUNT` Integer / `FORWARD_PROPAGATION` Boolean）；
- `layerToRenderOn`（**中途不可改**，javadoc "CANNOT change mid-trail, under any circumstance"）；
- `frameOffsetMult`。

进阶重载还做了弹体横向偏移修正：`linkedEntity instanceof DamagingProjectileAPI` 时从段位置减去发射舰本帧位移 × `frameOffsetMult`。

`textureOffset` 三态：`-1` → 每条 tracker 固定随机偏移（防重复纹理接缝）；`0` → 无偏移；其他值 → 显式偏移。

### 规则 7：一段不算尾迹——至少两段才渲染

`MagicTrailTracker.renderTrail` 首行 `if (size <= 1) return;`；类 javadoc 原文："the trail will not render without having at least 2 pieces link together"。

违反/误解的可观察结果：第一帧只有 1 段 → 什么都不画；从第 2 段起突然出现一条条带。这不是故障，是设计——调试时"第一帧没东西"是正常现象。

### linkedEntity 与切断登记

`addOrGetTrailTracker` 把 `linkedEntity` 写进 `cuttingMap`——这是 `cutTrailsOnEntity` 的索引。

javadoc 允许 null，但"that should really only be done in weird, edge-case scenarios"：null 实体无法被切断，传送/死亡时尾迹会拖长。

示例与成熟实例都传真实实体（舰船或弹体）。

## MagicTrail 推进（段生命周期）

### 段对象 MagicTrailObject

每次 `addTrailMember*` 生成一个 `MagicTrailObject`，推进逻辑全在 `tick(amount)`：

- **寿命**：`spentLifetime += amount`，钳制到 `getTotalLifetime() = inDuration + mainDuration + outDuration`；
- **尺寸**：`currentSize = startSize × (1 − t) + endSize × t`（+ 可选 `SIZE_PULSE` 脉动），`t = spentLifetime / totalLifetime`；
- **颜色**：`startColor` → `endColor` 线性插值，**alpha 分量被忽略**（javadoc 原文 "Ignores alpha component entirely"——透明度单独走 `opacity` 曲线）；
- **透明度三段曲线**：`spent < inDuration` → `opacity × spent / inDuration` 爬升；`in..in+main` → 满 `opacity`；`> in+main` → 按 `(1 − (spent − in − main)/out)` 衰减；`inDuration = 0` 时生成即满；
- **运动**：`angle += currentAngularVelocity × amount`；`position += (cos/sin(angle) × currentSpeed + offsetVelocity) × amount`——段自己带速度、角速度与恒定偏移速度漂移，**不跟随实体**（实体只是生成点的来源）。

### 插件逐帧处理（MagicTrailPlugin.advance）

`advance`：

1. **暂停守卫**：`engine == null || engine.isPaused()` → 直接返回——暂停时整条尾迹冻结（段不移动、不淡出），恢复后继续；
2. 每秒清理计时（`cleanTimer`）到时：把 `isExpired()` 的 tracker 从主表移除、把 `!engine.isEntityInPlay(entity)` 的 cutting map 项删除；
3. 每个 tracker `tickTimersInTrail(amount)`：遍历段，`spentLifetime >= totalLifetime` 的段移除，其余 `tick`；tracker 空置累计 3 秒 → `isExpired = true`——**旧段全部消失后约 3–4 秒，tracker 本体才被回收**（下个清理秒）；
4. library-free cut 检查：遍历 customData，键含 `MagicTrailPlugin_LIB_FREE_TRAIL_CUT` 且值为 `CombatEntityAPI` → 执行 `cutTrailsOnEntity` 并删除该键。

### 规则 8：暂停时尾迹冻结；advance 推进不依赖使用方代码

段的推进、淡出、移除全部由 MagicLib 自己的 `MagicTrailPlugin.advance` 完成——使用方只负责"每帧登记新段"。暂停时插件不推进任何段，画面上的尾迹**静止但可见**（与分层渲染的暂停行为同侧：渲染不停、推进停，见 S07-P05）。

违反的可观察结果：暂停 10 秒后恢复，尾迹位置与暂停前完全一致（没有偷偷移动或淡出）；如果您在自己的 `advance` 里做了暂停守卫，只是不再登记新段——旧段照常由 MagicLib 管理。没有需要您手动推进的东西。

## MagicTrail 渲染

### 渲染链与层选择

`MagicTrailRenderer`（`MagicTrailPlugin` 的内部类）是 `BaseCombatLayeredRenderingPlugin`（分层渲染通道，见 S07-P05）：

- `init` 时 `engine.addLayeredRenderingPlugin(renderer)`；
- `getActiveLayers()` 返回 `EnumSet.allOf(CombatEngineLayers.class)`——**全部层**（注释说明"只渲染一次检查无法按层过滤，开销可忽略"）；
- `render(layer, view)` 按传入的层查 `mainMap.get(layer)`，逐 tracker `renderTrail(texID)`；
- `getRenderRadius()` 返回超大值——不靠承载实体剔除，靠**段级**视口剔除。

每条 tracker 一个渲染层：

- `addTrailMemberSimple` 固定 `CONTRAILS_LAYER`；
- `addTrailMemberAdvanced` 的 `layerToRenderOn` 覆盖（缺省仍 `CONTRAILS_LAYER`）；
- 数据驱动行由 `renderBelowExplosions` 映射（`TRUE` → `ABOVE_SHIPS_LAYER`，否则 `BELOW_INDICATORS_LAYER`）。

**层一旦确定不可中途更改**（javadoc 原文 "CANNOT change mid-trail, under any circumstance"）。

### 纹理与混合

- **混合模式**：段记录 `blendModeSRC` / `blendModeDEST`；渲染时取**最新一段**的混合模式（注释原文 "uses the most recent blend mode added to the trail"）；`additive` 简单重载映射为 `GL_SRC_ALPHA, GL_ONE` / `GL_ONE_MINUS_SRC_ALPHA`。additive 与普通混合**不可中途混用**（javadoc "Does not support being changed in the middle of a trail"）。
- **纹理循环**：`textureLoopLength > 0` 时纹理按段距循环（`texDistTracker += partDistance / loopLength`）；`<= 0` 时整张纹理拉伸覆盖整条尾迹。
- **纹理滚动**：`scrollingTextureOffset -= amount × scrollSpeed / 1000`——`scrollSpeed = 1000` 即每秒滚动整张纹理；滚动对整条尾迹统一（每条 tracker 一个偏移量），不是逐段。
- **透明渐弱**：首尾各 2 段有额外的 `opacityMult` 渐弱，让尾迹两端圆滑收尾。
- **视口剔除**：逐段 `engine.getViewport().isNearViewport(part.currentLocation, partDistance * 2f)`，不在视口的段跳过绘制但**继续推进**。

## MagicTrail 清理

### 三段式清理

| 时机 | 行为 | 机制 |
|---|---|---|
| 段到期 | `spentLifetime >= totalLifetime` 的段在下次 `tickTimersInTrail` 被移除 | `tickTimersInTrail` 遍历段时按剩余寿命过滤 |
| tracker 过期 | 段全部消失后空置累计 3 秒 → `isExpired = true` → 下一个清理秒从主表移除 | tracker 内部空置计时 + 插件每秒清理计时 |
| cutting map | 关联实体 `!isEntityInPlay` → 清理秒删除 | 插件清理秒遍历 cutting map 按实体在场状态过滤 |

### 规则 9：传送 / 瞬移 / 死亡前必须 cutTrailsOnEntity

`cutTrailsOnEntity(entity)` 的 javadoc："Should be used before teleporting any entity, since it may have trails attached to it which will otherwise stretch in unintended ways."

实现：把该实体名下所有 tracker 从原 ID **换 key 到新的负数切断 ID**——此后按原 ID 登记的新段进入新的空 tracker，与旧段不再相连；旧段继续按自身寿命淡出。

::: caution 传送、瞬移或实体失效之前必须调用 `cutTrailsOnEntity`（或 library-free cut），否则旧位置与新位置的段会被连成一条跨战场直线，死亡后拖尾残留到段寿命结束。
:::

违反的可观察结果：实体被传送后，旧位置的尾迹段与新位置的段被强行连成一条直线（"拉伸"），画面出现跨战场的长条；实体死亡/移除时尾迹拖尾残留到段寿命结束。修正：传送/瞬移前、实体失效时调用 `cutTrailsOnEntity(entity)`（示例在舰船死亡与战斗结束时调用）。

### library-free cut（不依赖 MagicLib 编译的切断入口）

不想编译期依赖 MagicLib 的 MOD，可以把实体写进 `engine.getCustomData()`，键**包含** `MagicTrailPlugin_LIB_FREE_TRAIL_CUT`（javadoc 要求键必须比这个短语长、建议用 mod id + 实体 id 组成），值为 `CombatEntityAPI`；

`MagicTrailPlugin.advance` 每帧检查并在切断后删除该键。

代价是最多 1 帧延迟。

### 战斗级状态与跨战斗缓存禁令

`init` 每次战斗重置 ID 计数并清空 `mainMap` / `cuttingMap`；插件实例、renderer、customData 全部随战斗结束释放（战斗插件的 `destroyInstance` 语义，见 S07-P04）。

::: caution
禁止跨战斗缓存尾迹 ID 或实体引用——`usedIDs` 每场战斗从 1f 重新计数，缓存的旧 ID 会与新一轮计数冲突（比如撞上别的尾迹）；旧实体 `isEntityInPlay` 恒为 false，cutting map 会在清理秒被删。全部尾迹状态必须在战斗内重建。
:::

## 规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | `trail` 键全局唯一（跨 MOD 合并键列）；`projectile` / `sprite` 必须是真实存在的弹体 id / `fx` 分类贴图键 | 撞键 → 行被替换或消失；弹体 id 错 → 尾迹永不出现，无报错；贴图键错 → 取贴图失败 |
| 2 | **必须** | 一个弹体多条尾迹要写多行；一行只配一条尾迹 | 想多段却只写一行 → 只有一条；写进同一行 → 列错位整行跳过 |
| 3 | **必须** | 行内必填列类型正确、颜色用 `"[r,g,b]"` 字符串；缺列/类型错 → 整行跳过 | 尾迹"不出现"且无 ERROR（dev 才见 `Invalid line, skipping`）|
| 4 | **必须** | `addTrailMember*` 只能在 MagicLib 插件已初始化的战斗中调用；不要重复注册 `MagicTrailPlugin` / `MagicAutoTrails` settings 键 | 战斗外/过早调用 → 静默无效果；覆盖 settings 键 → 渲染链缺失 |
| 5 | **必须** | 同一条尾迹的 `getUniqueID()` 只取一次并跨帧复用；同一条 trail 不得中途换 sprite | 每帧新 ID → 无法连接的点状短条；换 sprite → 拆成两条尾迹 |
| 6 | **必须** | 尾迹 sprite 宽高必须为 2 的倍数（16/32/64/128…）| 纹理接缝、扭曲或渲染异常 |
| 7 | **必须** | 至少 2 段才渲染（第一段不可见是设计）| 首帧无画面——不是故障 |
| 8 | **必须** | 暂停时尾迹冻结（MagicLib 自己的 advance 守卫）；段的推进与移除不依赖使用方代码 | 暂停后恢复位置不变；段照常由 MagicLib 管理 |
| 9 | **必须** | 传送 / 瞬移 / 实体失效前调用 `cutTrailsOnEntity`（或 library-free cut）| 传送后新旧段被强行连成跨战场长条；死亡后拖尾残留 |
| 10 | **必须** | 尾迹 ID、实体引用禁止跨战斗缓存（S06-P03 / S07-P04 同规）| 第二场战斗 ID 撞计数、旧实体恒失效、cutting map 项被清 |
| 11 | **必须**（数据注释硬约束）| `BALLISTIC_AS_BEAM` 弹体的 `angleAdjustment` 必须 `FALSE` | 弹体朝向被强制改为速度方向，射线贴图旋转、视觉异常 |
| 12 | **必须** | `layerToRenderOn` 与 additive/非 additive 一旦确定不可中途改变 | 中途变更被忽略或拆条（javadoc "CANNOT change mid-trail"）|
| 13 | 建议 | `minLength` 保持小值（默认示例 10）；需要时间加速省性能才用负数慢速模式 | 值过大 → 曲线尾迹变折线、加速下失真 |
| 14 | 建议 | `additive` 开 `TRUE`（自带贴图按叠加设计）| 非 additive 尾迹易被爆炸等粒子遮挡（`renderBelowExplosions` 注释同侧）|
| 15 | 建议 | `angleAdjustment` 一般不用；确实要用须配 `drift<1` | 尾迹与弹体朝向错位（软约束，非硬错误）|

强度核对（迁移原文 → RC8）：
- 旧文"trail ID 必须互相独特"升格为本表规则 1（合并键列语义，RC8 有效）；
- "minLength 不建议高于 10"维持建议（规则 13，理由按段间距语义改写）；
- "angleAdjustment 极度不建议写 true"拆成规则 11（`BALLISTIC_AS_BEAM` 必须 FALSE，数据注释原文）+ 建议 15（一般情形）；
- "additive 极度建议写 true"维持建议（规则 14）。

## 完整最小例

在前置页累计的工程布局上追加（MOD 根目录 `Starsector\mods\GearIndustry`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                      # dependencies 已含 MagicLib（S12-P01）
├── data\
│   ├── trails\
│   │   └── trail_data.csv             # 本页新增（数据驱动尾迹一行）
│   └── config\
│       └── modSettings.json           # S12-P04 所有；本页不改动
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # S06-P02 所有，本页只消费
│       ├── combat\                    # S07/S12 既有战斗插件目录
│       ├── lib\                       # S12-P04 的 GearInMagicWiring 等五个小类
│       └── visuals\
│           └── GearInMagicTrail       # 本页新增
└── jars\
```

### data/trails/trail_data.csv（本页所有，一行一条尾迹）

`data/trails/trail_data.csv` 是 MagicLib 默认 `magicTrail_files` 列表里的兼容路径（无需任何配置即被读取合并）；弹体 `GearIn_test_shot` 由 S07-P03 声明，本页只消费。表头与 MagicLib 自带默认文件一致（30 列）：

```csv
trail,projectile,sprite,minLength,fadeIn,duration,fadeOut,sizeIn,sizeOut,colorIn,colorOut,opacity,textLength,textScroll,randomTextureOffset,distance,dispersion,drift,frameOffsetMult,fadeOnFadeOut,angleAdjustment,additive,velocityIn,velocityOut,randomVelocity,angle,rotationIn,rotationOut,randomRotation,renderBelowExplosions
GearIn_trail_test_shot,GearIn_test_shot,base_trail_smooth,10,0.05,0.1,0.6,10,4,"[120,220,255]","[120,220,255]",0.6,-1,0,TRUE,8,0,0,1,FALSE,FALSE,TRUE,0,0,0,0,0,0,false,false
```

要点：

- `trail` 键 `GearIn_trail_test_shot` 本页所有且全局唯一（规则 1）；
- `sprite` 用 MagicLib 自带 `base_trail_smooth`（无新资产）；
- `textLength=-1` 整条拉伸、`textScroll=0` 不滚动；
- `angleAdjustment=FALSE`（非 `BALLISTIC_AS_BEAM` 的弹体若需要也建议保持 FALSE，规则 15）；
- `additive=TRUE`（规则 14）；
- `renderBelowExplosions=false` → 默认 `BELOW_INDICATORS_LAYER`。

改数据必须重启游戏（或 dev F8 重载）。

### GearInMagicTrail（本页新增，与 fixture 逐字一致）

```java
package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例：MagicLib 1.5.1 MagicTrailPlugin 的数据、创建、推进、渲染与清理。
 *
 * 由既有战斗插件在 init() 中 addPlugin 注册（S02-P01 模型），只在本场战斗内有效：
 * - 数据：尾迹 sprite 必须是 settings.json "fx" 分类注册的键（MagicLib 自带
 *   base_trail_smooth / base_trail_contrail 等 12 张，宽高为 2 的倍数、垂直长条）；
 *   本示例不新增 PNG 资产；
 * - 创建：每条尾迹从 MagicTrailPlugin.getUniqueID() 取一次 ID，此后每帧复用——
 *   每帧新 ID 会变成多条无法连接的短条（硬规则 5）；同一条 trail 中途换 sprite
 *   会拆成两条；段不足 2 段不渲染（硬规则 7，首帧无画面是正常现象）；
 * - 推进：addTrailMember* 只负责登记段，段的移动/淡出/移除由 MagicTrailPlugin.
 *   advance 完成（暂停时整条冻结，硬规则 8）；每段总寿命 = in + main + out；
 * - 渲染：渲染由 MagicTrailPlugin.init 注册的 MagicTrailRenderer（分层渲染插件，
 *   S07-P05 通道）完成；本示例两条尾迹分别用默认 CONTRAILS_LAYER 与显式
 *   ABOVE_SHIPS_AND_MISSILES_LAYER；
 * - 清理：舰船死亡/战斗结束时 cutTrailsOnEntity 切断旧段（旧段按自身寿命淡出，
 *   新段不再与之相连，硬规则 9）；战斗内全部状态随战斗结束释放，禁止跨战斗缓存
 *   （硬规则 10）。
 */
public class GearInMagicTrail extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInMagicTrail.class);

    /** settings.json "fx" 分类的尾迹贴图键（MagicLib 自带，无需新增资产）。 */
    private static final String SPRITE_BODY = "base_trail_smooth";
    private static final String SPRITE_GLOW = "base_trail_contrail";

    /** 登记新段的时间间隔（秒）：每 0.05 秒在舰船当前位置登记一段。 */
    private static final float SEGMENT_INTERVAL = 0.05f;

    private CombatEngineAPI engine;
    private ShipAPI ship;
    private SpriteAPI bodySprite;
    private SpriteAPI glowSprite;

    /** 两条尾迹各自的 ID：getUniqueID() 只取一次，生命周期内跨帧复用（硬规则 5）。 */
    private float bodyTrailID;
    private float glowTrailID;

    private float segmentTimer;
    private float reportTimer;
    private int segmentCount;
    private boolean cut;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        this.ship = engine.getPlayerShip(); // 战斗内永不 null（S07-P01）
        this.bodySprite = Global.getSettings().getSprite("fx", SPRITE_BODY);
        this.glowSprite = Global.getSettings().getSprite("fx", SPRITE_GLOW);
        this.bodyTrailID = MagicTrailPlugin.getUniqueID();
        this.glowTrailID = MagicTrailPlugin.getUniqueID();
        LOG.info("GearIn trail init: body=" + SPRITE_BODY + " glow=" + SPRITE_GLOW
                + " bodyID=" + bodyTrailID + " glowID=" + glowTrailID);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || ship == null) {
            return; // init() 不保证先于 advance()（S07-P04），必须判空
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）：暂停时 MagicTrailPlugin 不推进任何段（硬规则 8）
        }
        if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
            cutTrails();
            return;
        }
        segmentTimer += amount;
        if (segmentTimer >= SEGMENT_INTERVAL) {
            segmentTimer = 0f;
            spawnSegments();
        }
        reportTimer += amount;
        if (reportTimer >= 1f) {
            reportTimer = 0f;
            LOG.info("GearIn trail: segments=" + segmentCount
                    + " elapsed=" + Math.round(engine.getTotalElapsedTime(false)));
        }
        if (engine.isCombatOver()) {
            cutTrails(); // 战斗结束收尾：切断尾迹，旧段随各自寿命自然淡出（S07-P04 的收尾姿势）
        }
    }

    /** 每帧向两条尾迹各登记一个新段：旧段留在原地、新段在舰船当前位置 → 连续条带。 */
    private void spawnSegments() {
        Vector2f location = ship.getLocation();
        float facing = ship.getFacing();
        // 简单版：默认渲染层 CONTRAILS_LAYER（MagicTrailPlugin 源码）
        MagicTrailPlugin.addTrailMemberSimple(
                ship,            // linkedEntity：cutTrailsOnEntity 的切断对象（硬规则 9）
                bodyTrailID,     // 同一尾迹复用同一 ID（硬规则 5）
                bodySprite,      // 中途不得换 sprite（硬规则 5）
                location,
                0f,              // speed：段留在生成点，靠"每帧新段 + 舰船移动"连成尾迹
                facing,          // angle（度）：段的移动方向与宽度测量方向
                14f,             // startSize（SU）
                3f,              // endSize（SU）
                new Color(120, 220, 255),
                0.8f,            // opacity 0..1（透明度上限）
                0.15f,           // inDuration（渐入秒）
                0.35f,           // mainDuration（满亮秒）
                1.2f,            // outDuration（渐出秒）
                true);           // additive：叠加混合（规则 14）
        // 进阶版：显式层 + 纹理循环长度/滚动速度 + 宽度脉动选项
        Map<String, Object> options = new HashMap<>();
        options.put("SIZE_PULSE_WIDTH", 4f);   // Float：每次脉动增加的宽度（SU）
        options.put("SIZE_PULSE_COUNT", 2);    // Integer：整条寿命内的脉动次数
        MagicTrailPlugin.addTrailMemberAdvanced(
                ship,
                glowTrailID,
                glowSprite,
                location,
                0f, 0f,          // startSpeed / endSpeed
                facing,          // angle（度）
                0f, 0f,          // startAngularVelocity / endAngularVelocity
                22f, 6f,         // startSize / endSize
                new Color(60, 120, 255),
                new Color(180, 230, 255),
                0.6f,            // opacity
                0.1f, 0.2f, 0.8f, // in / main / out（总寿命 1.1 秒）
                true,            // additive
                120f,            // textureLoopLength（SU）：纹理循环长度，≤0 则整条拉伸
                200f,            // textureScrollSpeed：200 = 每秒滚动整张纹理的 1/5
                0f,              // textureOffset：0 无偏移；-1 为每条 tracker 固定随机偏移
                null,            // offsetVelocity：附加恒定速度
                options,         // advancedOptions（SIZE_PULSE_WIDTH/COUNT/FORWARD_PROPAGATION）
                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER, // 显式层（中途不可改，硬规则 12）
                1f);             // frameOffsetMult：每帧速度偏移倍率
        segmentCount++;
    }

    /** 切断两条尾迹：旧段不再与新段相连，按自身寿命淡出（传送/死亡/战斗结束前调用）。 */
    private void cutTrails() {
        if (cut) {
            return;
        }
        cut = true;
        MagicTrailPlugin.cutTrailsOnEntity(ship);
        LOG.info("GearIn trail cut: segments=" + segmentCount);
    }
}
```

对应关系：

- **创建链**：`init` 里 `getUniqueID()` 各取一次存字段（`bodyTrailID` / `glowTrailID`，规则 5）；两条尾迹的 ID 分别登记进同一 `cuttingMap` 实体项（`ship`，规则 9 的切断索引）。
- **推进链**：`advance` 每 0.05 秒调 `spawnSegments` 登记两段；段的时间推进、淡出与移除全部由 `MagicTrailPlugin.advance` 完成（规则 8），使用方不接触 `MagicTrailObject` / `MagicTrailTracker`。
- **渲染链**：主体尾迹走 `addTrailMemberSimple` 的默认 `CONTRAILS_LAYER`；泛光尾迹走 `addTrailMemberAdvanced` 的显式 `ABOVE_SHIPS_AND_MISSILES_LAYER`（舰船之上、粒子之下，S07-P05 层序），并演示 `textureLoopLength` / `textureScrollSpeed` / `SIZE_PULSE_*`。
- **清理链**：舰船死亡（`!isEntityInPlay || !isAlive`）与 `engine.isCombatOver()` 都调 `cutTrails()`——先切断再让旧段自然淡出（规则 9）；`cut` 标志防重复切断。
- **数据链**：同目录的 `trail_data.csv` 行由 `MagicAutoTrails` 为 `GearIn_test_shot` 弹体自动生成尾迹（与 Java 侧两条尾迹并存，互不干扰——Java 侧是实体尾迹，数据侧是弹体尾迹）。

注册片段（工程片段——本页不改动 S12-P04 / S07-P04 的所有文件，只演示调用）：

```java
    // 已有战斗插件（S02-P01 的所有者类）的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInMagicTrail());
        // ... 原有逻辑
    }
```

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用 S06-P01）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/visuals/GearInMagicTrail.class`（可用 `jar tf` 核对）； `data\trails\trail_data.csv` 随资源进包。

本页源码与 fixture 逐字一致，fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

以本页示例（`GearInMagicTrail` + `data/trails/trail_data.csv` 一行）为对象，验证"数据 → 创建 → 推进 → 渲染 → 清理"完整流程。

**操作**

前置数据：S12-P04 的累计工程 + `data\trails\trail_data.csv`（本页新增）+ `GearInMagicTrail` + 注册片段一行 + S03-P01 的 `GearIn_test` 测试战役；本机已安装 MagicLib 1.5.1（启动器勾选）。

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileS13p03Java --no-daemon`，确认 `BUILD SUCCESSFUL`（退出码 0）。
2. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/visuals/GearInMagicTrail.class`。
3. 战役冒烟：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 `GearIn trail init: ...`；每 1 秒一行 `GearIn trail: segments=<n> elapsed=<秒数>`；没有 `gear_in` 引起的 `ERROR`。
4. 画面：观察玩家舰尾部两条尾迹（青色主体条在 `CONTRAILS_LAYER`、蓝色泛光条在舰船与导弹之上），泛光宽度脉动两次、纹理滚动；首帧无画面、第 2 段起成条。
5. 暂停：按空格暂停约 10 秒——两条尾迹静止、无周期报告行；恢复后继续生长。
6. 切断：击毁玩家舰或等战斗结束——`GearIn trail cut: segments=<n>` 出现，尾迹不再变长、旧段在约 1.1–1.7 秒内淡出消失。
7. 跨战斗：退出再进一场——`init` 重新出现、ID 重新分配、尾迹从零开始。
8. 数据驱动（可选）：接入 S07-P03 的 `GearIn_test_shot` 弹体后发射该武器，核对弹体尾部出现按 `trail_data.csv` 行生成的尾迹；dev 模式见 `Merging trails from data/trails/trail_data.csv` warn，或（该文件缺失时）`Deprecated 'data/trails/trail_data.csv' not found, skipping.` 正常 INFO。
9. 故障注入：把 `spawnSegments()` 改为每帧新 ID——尾部出现点状毛刺；注释 `cutTrailsOnEntity`——死亡/瞬移时尾迹被拉伸；`colorIn` 去掉引号——数据行整行消失（dev 日志 `Invalid line, skipping`）；改回后确认恢复。

**通过信号**

- 操作 1–7 全部符合；操作 8 在接入后符合；操作 9 的注入与恢复都符合预期。

**清理**

- 取消勾选测试 MOD；恢复 `trail_data.csv` 与代码为原值；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行

    ```text
    gradle -p tutorial-fixtures compileS13p03Java --no-daemon
    ```

    以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/visuals/GearInMagicTrail.class`。
3. **战斗初始化**：进入 `GearIn_test` 战役（S03-P01）一场战斗，`starsector.log` 出现一行

    ```text
    GearIn trail init: body=base_trail_smooth glow=base_trail_contrail bodyID=<id> glowID=<id>
    ```

    `bodyID` / `glowID` 是两个不同的正数（`getUniqueID()` 计数，规则 5 的观察面）；没有 `gear_in` 引起的 `ERROR`。
4. **画面（创建 + 渲染）**：玩家舰尾部出现两条尾迹——青色主体条（`CONTRAILS_LAYER`，随舰船机动弯曲）与蓝色泛光条（`ABOVE_SHIPS_AND_MISSILES_LAYER`，覆盖在舰船与导弹之上）；泛光条宽度呈两次脉动（`SIZE_PULSE_COUNT=2`），纹理沿尾迹滚动（`textureScrollSpeed=200`）。首帧只有 1 段 → 什么都不画，第 2 段起出现条带（规则 7）。
5. **周期报告（推进）**：战斗中每 1 秒出现一行

    ```text
    GearIn trail: segments=<n> elapsed=<秒数>
    ```

    `segments` 稳步增长（每 0.05 秒两段）；暂停期间**没有**新报告行（暂停守卫 + MagicLib 不推进）。
6. **暂停冻结（规则 8）**：按空格暂停约 10 秒——画面上的两条尾迹完全静止（不移动、不淡出、不新增段）；恢复后尾迹从暂停前的位置继续生长。
7. **数据驱动尾迹（数据链）**：若 S07-P03 的 `GearIn_test_shot` 弹体数据已接入并被武器发射，该弹体后方出现一条按 `trail_data.csv` 行参数生成的尾迹（`base_trail_smooth`、青色、`textLength=-1` 整条拉伸、默认 `BELOW_INDICATORS_LAYER` 层）；日志出现 MagicLib 合并期 warn 行 `Merging trails from data/trails/trail_data.csv`（dev 模式，`MagicAutoTrails.getTrailData` 的 `LOG.warn`）或读取失败时 `Deprecated 'data/trails/trail_data.csv' not found, skipping.`（该路径文件缺失时的正常 INFO，不是错误）。
8. **切断与清理（规则 9）**：击毁玩家舰（或战斗结束）——`starsector.log` 出现一行

    ```text
    GearIn trail cut: segments=<n>
    ```

    随后两条尾迹**不再变长**，旧段在各自总寿命（主体 1.7 秒 / 泛光 1.1 秒）内淡出消失，尾部不会出现"新段拖着旧段"的延伸；战斗结束淡出期间旧段照常淡出（渲染不暂停）。
9. **跨战斗（规则 10）**：退出战斗再进一场——`GearIn trail init:` 重新出现且 `bodyID` 重新从计数起点分配（每场战斗 `usedIDs` 重置为 1f）；新战斗的尾迹从零开始。
10. **数据变更验证（规则 3 的观察面）**：把 `trail_data.csv` 的 `opacity` 改为 `1.0`、`sizeIn` 改为 `20` 后重启游戏（或 dev F8 重载 + 重进战斗）——弹体尾迹变粗变亮；把 `colorIn` 写成 `[255,0,0]`（无引号数字数组）后重启——该行尾迹**消失**（整行跳过，dev 模式日志 `Invalid line, skipping`），Java 侧两条尾迹不受影响。
11. **故障注入（ID 不复用）**：把 `spawnSegments()` 里两处调用换成每帧新 ID（`MagicTrailPlugin.getUniqueID()`）后进战斗——舰船尾部出现大量无法连接的短横条/毛刺，而不是连续尾迹（规则 5 的注入验证，S12-P04 陷阱条同款）；改回后恢复。
12. **故障注入（传送不切断）**：注释 `cutTrails()` 里的 `MagicTrailPlugin.cutTrailsOnEntity(ship);` 后，让玩家舰瞬移（或击毁）——旧位置与新位置之间被一条直线强行连接（"拉伸"），直到旧段寿命结束（规则 9 的注入验证）；改回后恢复。

静态自查：`trail_data.csv` 为 UTF-8 无 BOM、30 列与表头一致、`trail` 键 `GearIn_trail_test_shot` 全局唯一；

`GearInMagicTrail` 与 fixture 逐字一致（包括 import 顺序与注释）；

没有把工作单元编号写进任何包名、类名、资源名或字符串 key（包 `data.scripts.gearin.visuals`、类 `GearInMagicTrail`、日志前缀 `GearIn trail ...`、数据键 `GearIn_trail_test_shot`）；

除本页目录与 fixture 目录外没有改动任何共享文件。

## 实例驱动故障分析

::: tip
排查顺序——先看 `starsector.log` 有没有 `GearIn trail init:`（插件是否初始化、ID 是否分配），再看 dev 模式有没有 `Invalid line, skipping`（数据行是否被跳过），最后对照"首帧只有 1 段不渲染"的预期（规则 7）检查画面。

三个观察点即可二分定位数据 / 创建 / 渲染环节。
:::

1. **尾迹完全不出现，也没有任何报错**。
  - 触发：`addTrailMember*` 在战斗外调用、在 `MagicTrailPlugin.init` 之前调用、或数据驱动行的 `projectile` 写错。
  - 可观察：`getPlugin()` 返回 null 静默返回；数据行匹配不到弹体（查表时直接跳过）。
  - 修正：确认在战斗插件 `advance` 里调用；`projectile` 填真实弹体 spec id；确认 MagicLib 已启用且 settings `plugins` 键没被动过（规则 4）。
2. **尾迹是点状/毛刺，不是连续条带**。
  - 触发：每帧 `getUniqueID()` 新 ID，或同一条 trail 中途换 sprite。
  - 可观察：每帧新 ID → 每段各自成 tracker，段与段不连锁（trailer 少于 2 段不渲染，规则 7）；换 sprite → `addOrGetTrailTracker` 按纹理 id 分桶拆成两条。
  - 修正：ID 取一次存字段、sprite 恒定（规则 5；成熟实例同款做法）。
3. **首帧/第一段看不见**。
  - 触发：以为是 bug 在排查。
  - 可观察：`size <= 1` 直接 return——一段不算尾迹是设计（规则 7）。
  - 修正：不需要修；段生成频率够快即可。
4. **传送/死亡后尾迹"拉伸"成跨战场长条**。
  - 触发：实体瞬移或失效前没切断。
  - 可观察：新旧位置之间被一条直线连接（旧段在旧位置、新段在新位置，tracker 把它们当同一条尾迹渲染）。
  - 修正：传送/死亡前 `cutTrailsOnEntity(entity)`（规则 9；javadoc 原文）；不想依赖 MagicLib 编译用 library-free cut。
5. **暂停后尾迹"跳变"或"提前消失"**。
  - 触发：以为暂停也推进。
  - 可观察：暂停时 `MagicTrailPlugin.advance` 直接 return——段不 tick；恢复后继续。如果您的计时用了自己的暂停守卫，恢复后新段从当前位置接上，旧段位置没动——正常。
  - 修正：不要自己推进段；需要"暂停时也淡出"的效果只能自己做粒子，不能用 MagicTrail 段。
6. **数据行"不生效"但日志没有 ERROR**。
  - 触发：列类型错（数字列写字母）、必填列缺失、颜色写成数字数组。
  - 可观察：整行跳过，dev 模式才见 `Invalid line, skipping` warn。
  - 修正：对照 30 列表头核对；颜色必须是 `"[r,g,b]"` 字符串（规则 3；成熟实例数据文件的合法写法）。
7. **两条尾迹互相"打架"（颜色/粗细串线）**。
  - 触发：撞了 `trail` 键或复用了别人的 ID。
  - 可观察：同 `trail` 键跨 MOD 合并互相替换（行消失或参数错乱）；Java 侧与别的 MOD 复用同一个 `getUniqueID()` 结果不可能（计数全局唯一），但缓存了上一场的 ID 会在新场次撞车（`usedIDs` 每场重置，规则 10）。
  - 修正：`trail` 键加 MOD 前缀；ID 每场重新取、禁止缓存。
8. **尾迹在视口外"消失"，拉回镜头又出现**。
  - 触发：不是故障。
  - 可观察：段级视口剔除 `isNearViewport(part, partDistance * 2)`，离屏段不绘制但继续推进。
  - 修正：不需要修；这是性能设计（与分层渲染页的剔除同族）。
9. **弹体尾迹朝向异常、射线武器贴图旋转**。
  - 触发：`BALLISTIC_AS_BEAM` 弹体的数据行 `angleAdjustment` 写成 `TRUE`。
  - 可观察：段生成时 `proj.setFacing(速度方向)`——射线类弹体的朝向被改写、贴图旋转；数据文件注释以 `!!!! Must be FALSE for BALLISTIC_AS_BEAM projs !!!!` 强调。
  - 修正：`BALLISTIC_AS_BEAM` 行必须 `FALSE`（规则 11）；一般弹道弹要用 `angleAdjustment` 须配 `drift<1`。
10. **第二场战斗尾迹 ID 冲突 / 旧尾迹引用失效**。
  - 触发：把 ID 或实体引用缓存到静态字段/生涯侧对象。
  - 可观察：`init` 每场重置 `usedIDs`——缓存的旧 ID 可能与新场次的计数撞车；旧实体 `isEntityInPlay` 恒 false，cutting map 项在清理秒被删。
  - 修正：全部状态在 `init` 里重建（规则 10）。

## 总结

本页走通了 MagicTrail 尾迹的完整流程：数据侧由 `magicTrail_files` 列表经跨 MOD 合并读入 30 列 `trail` 行，`MagicAutoTrails` 按弹体 id 自动匹配；

代码侧由 `getUniqueID()` + `addTrailMember*` 手动登记段——段的推进、渲染与清理全部由 MagicLib 自己完成，使用方只负责登记新段，并在传送、死亡或战斗结束前用 `cutTrailsOnEntity` 切断。

逻辑上，本页与软依赖 / 可选依赖的处理同属"依赖库主题"的侧面：前者解决缺少库时的降级，本页解决库核心能力的使用纪律；MagicLib 其余工具族（FakeBeam / HUD / 生涯工具）的用法在 MagicLib 系列前置页已述。

若继续深入，本页的渲染链正是分层渲染插件通道的实例——把"登记段 + 交给渲染插件绘制"的思路迁移到自定义渲染插件，是这条知识的自然延伸。

## 建议参考项

- `org.magiclib.plugins.MagicTrailPlugin`：尾迹插件——ID 分配（`getUniqueID`）、段登记（`addTrailMemberSimple` / `addTrailMemberAdvanced`）、切断（`cutTrailsOnEntity`）与逐帧推进（`advance`）。
- `org.magiclib.plugins.MagicAutoTrails`：数据驱动使用端——按弹体 id 匹配 CSV 行自动生成尾迹。
- `org.magiclib.util.MagicTrailTracker`：段容器与渲染（层、纹理循环/滚动、视口剔除、3 秒过期）。
- `org.magiclib.util.MagicTrailObject`：单段推进（寿命、插值、透明度三段曲线）。
- `org.magiclib.Magic_modPlugin` / `org.magiclib.util.MagicSettings`：尾迹数据读取时机与 `magicTrail_files` 列表访问。
- `com.fs.starfarer.api.SettingsAPI`：`getSprite`（按分类取贴图）、`getMergedSpreadsheetDataForMod`（跨 MOD CSV 合并）。
- `com.fs.starfarer.api.graphics.SpriteAPI`：`getTextureId`（tracker 分桶依据）。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`getCustomData`（插件与 renderer 存放处）、`getProjectiles`、`isEntityInPlay`、`getTotalElapsedTime`。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：层枚举（`CONTRAILS_LAYER` / `ABOVE_SHIPS_LAYER` / `BELOW_INDICATORS_LAYER` 等）。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：本页示例插件基类。
- `data/trails/trail_data.csv`：本页所有，数据驱动尾迹一行（`trail` 键 `GearIn_trail_test_shot`）。
- `data/config/modFiles/magicTrail_data.csv`：MagicLib 自带默认文件，30 列表头与逐列注释。
- MagicLib 发行内 `data/config/modSettings.json`（`magicTrail_files` 默认列表）与 `data/config/settings.json`（`plugins` 段、`graphics.fx` 分类贴图键）。
- `data/scripts/gearin/visuals/GearInMagicTrail`：本页示例战斗插件（两条尾迹的登记与切断）。
