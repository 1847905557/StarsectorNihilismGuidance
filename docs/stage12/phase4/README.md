# MagicLib 工具：设置与 HUD

本页讲解硬依赖外部库 MagicLib 1.5.1 的四件事——设置接入（`data/config/modSettings.json` 的合并读取与 `MagicSettings` 类型化访问）、FakeBeam（`MagicFakeBeam.spawnFakeBeam` 瞬时伤害伪光束与碰撞助手）、HUD 工具（`MagicUI` 战斗 HUD 状态条与文本/条绘制）与生涯工具（`MagicCampaign` 残骸、碎片场、跳跃点、市场、人物、舰队与目标选取）——并给出一个同时使用四族的完整最小例（五个小类，一角色一文件）。MagicBounty 数据赏金系统、`MagicTrailPlugin` 完整尾迹 API 与 `MagicRenderPlugin` 的精灵渲染（`addBattlespace` / `addObjectspace` / `addScreenspace`）不在本页范围。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 写出 `data/config/modSettings.json` 的段与键（段名 = 自己的 MOD id）。
- 说出 MagicLib 的合并加载链（`Magic_modPlugin.onApplicationLoad` → `MagicSettings.loadModSettings` → `getMergedJSONForMod`）与读取时机；
- 背出 `MagicSettings` 各类型读取方法的缺省值 （boolean `false` / string `""` / float、integer `0` / color `Color.RED` / list、map 空集合）与三类 warn 日志位置，识别"设置没生效"的隐蔽症状；
- 用 `MagicFakeBeam.spawnFakeBeam` 写出从任意坐标射向目标的最小调用，说出三条语义边界——伤害瞬时结算、光束不可被中途物体截断、HIGH_EXPLOSIVE 对装甲全额伤害——以及各自的可观察结果；
- 说出 `getShipCollisionPoint` / `getCollisionPointOnCircumference` 的返回值约定（null / 起点 / 偏移点）与判空要求；
- 用 `MagicUI.drawInterfaceStatusBar`（带 key 重载）注册战斗 HUD 状态条，说出完整渲染链 （谁绘制、什么时候绘制）、约 5 秒刷新纪律（含暂停时间）、key 唯一性与"只在玩家舰 + HUD 可见时输出"的守卫；
- 说出 `MagicCampaign` 生涯工具族（残骸 / 碎片场 / 跳跃点 / 市场 / 人物 / 舰队 / 目标选取）与"生涯上下文"要求，用 `createDerelict` / `loadVariant` 写出写侧与读侧写法及判空要求；
- 从 `starsector.log`、战斗 HUD、战斗画面、生涯存档四个可观察位置复核调用是否真的发生，并把"设置 → 战斗特效 → HUD → 生涯"的调用链走通。

## 前置知识

本页是教程工程第一次**真正调用** `org.magiclib.**` 的 API，建议先具备以下基础：

- 硬依赖声明与运行时检测（S12-P01）：`mod_info.json` 的 `dependencies` 已声明 `MagicLib` （该页示例带 `version: "1.5.1"`），MagicLib 自身依赖 `lw_lazylib`。
- LazyLib 查询纪律（S12-P02）：`AIUtils.getNearestEnemy` 等返回 `@Nullable` 必须判空、查询结果禁止跨战斗缓存。
- `MagicFakeBeam` 内部链路使用 LazyLib（`MathUtils` / `VectorUtils` / `CombatUtils` / `CollisionUtils`），本页示例也直接用 `AIUtils` / `VectorUtils`。
- 战斗插件模型与暂停守卫（S02-P01）：`addPlugin` 注册、`advance` 每帧回调、`engine.isPaused()` 守卫（本页示例插件的骨架）。
- MOD 主插件回调与 `onGameLoad` 时机（S06-P02）：本页生涯查询片段从这里接入。
- `Global` 生命周期（S06-P03）：`getSector()` 在生涯启动/读档后才非 null；战斗外判断不要用 `getCombatEngine() == null`。
- `GearIn_test` 测试战役（S03-P01）：战斗侧冒烟入口。
- `GearIn` 星系与 `GearIn_planet1`（S03-P03）：生涯写侧冒烟接入点。
- 生涯对象基础（S08 系列：S08-P01 生涯脚本、S08-P03 生涯舰队、S08-P07 生涯服务）：`MagicCampaign` 的产物就是这些对象。

## MagicLib 1.5.1 概览

MagicLib 是硬依赖外部库，公开类位于包 `org.magiclib`（`util` / `plugins` / `campaign` / `bounty` / `subsystems` 等）。

本地发行布局（`Starsector\mods\MagicLib`，1.5.1，官方源码固定 commit `21d158d6e1c27697906e6a2c83223e87dd07dfe3`）：

```text
Starsector\mods\MagicLib\
├── mod_info.json              # id "MagicLib"，gameVersion "0.98a-RC04"，
│                              # jars 两件（MagicLib.jar + MagicLib-Kotlin.jar），
│                              # modPlugin org.magiclib.Magic_modPlugin，依赖 lw_lazylib
├── LICENSE.txt                # cc-by-nc-sa-4.0.txt 代码/资源许可边界
├── magiclib.version           # 版本自检文件（master 指向官方仓库）
├── data\
│   └── config\
│       ├── settings.json      # plugins 段注册 MagicFakeBeamPlugin / MagicTrailPlugin /
│       │                      # MagicRenderPlugin 等战斗插件与 "fx"/"beams" 分类贴图
│       ├── modSettings.json   # MagicLib 自己的设置段（magicTrail_files / bounty_board_* 等）
│       └── sample_modSettings.json  # 给消费方 MOD 的设置样例与注释
├── src\                       # 官方源码（本页行为事实的授权来源）
└── jars\
    └── MagicLib.jar / MagicLib-Kotlin.jar
```

`org.magiclib.util` 的四个家族与职责：

| 类 | 职责 | 本页范围 |
|---|---|---|
| `MagicSettings` | `data/config/modSettings.json` 合并读取、类型转换、缺省值 | 设置接入 |
| `MagicFakeBeam` | 瞬时伤害伪光束与线段-圆/舰船碰撞助手 | FakeBeam |
| `MagicUI` | 战斗 HUD 状态条、系统条/框、LazyFont 文本 | HUD 工具 |
| `MagicCampaign` | 生涯侧残骸/碎片场/跳跃点/市场/人物/舰队/目标选取 | 生涯工具 |
| `MagicVariables` | 常量（`MAGICLIB_ID = "MagicLib"`）、sector 尺寸缓存、verbose 标志 | 全程使用 |
| `MagicTrailPlugin` | QUAD_STRIP 尾迹渲染（`spawnAdvancedFakeBeam` 的纹理光束底层）| 只在 FakeBeam 节作为依赖简述 |

## MagicSettings：data/config/modSettings.json 设置接入

### 文件与合并加载链

MagicLib 的设置文件是 `data/config/modSettings.json`。

它由 MagicLib 自己的 `Magic_modPlugin.onApplicationLoad()`（`org.magiclib.Magic_modPlugin`）调用 `MagicSettings.loadModSettings()` 读入一次：

```java
// `org.magiclib.util.MagicSettings` 的 `loadModSettings`（官方源码，行为事实）
public static void loadModSettings() {
    try {
        modSettings = Global.getSettings().getMergedJSONForMod(PATH, MAGICLIB_ID);
    } catch (IOException | JSONException ex) {
        LOG.fatal("unable to read modSettings.json", ex);
    }
    devmode = Global.getSettings().isDevMode();
}
```

要点：

- 路径常量 `PATH = "data/config/modSettings.json"`；`MAGICLIB_ID = "MagicLib"`（`org.magiclib.util.MagicVariables`）。
- `getMergedJSONForMod(path, masterMod)` 是 settings 的跨 MOD 合并读取（settings 合并读取的同族入口）：**全部已启用 MOD** 的同路径文件合并成一份 JSON（以 MagicLib 自己的副本为 master）。
- 使用方 MOD 只需要提交自己的 `data/config/modSettings.json`，在**自己的 MOD id 段**里写键，不要复制 MagicLib 的段，也不要写别人的段。
- 读取时机：**MagicLib 的 `onApplicationLoad` 时读一次**，结果缓存在静态字段 `modSettings`；dev 模式下 F8 重载（`onDevModeF8Reload`）会重新读取。
- 改 `modSettings.json` 后**重启游戏**（或 dev 模式 F8 重载 + 重进战斗）才生效。
- 若合并解析失败（文件损坏等），`loadModSettings` 写 `fatal` 日志且 `modSettings` 保持 null，`Magic_modPlugin.onApplicationLoad` 随后抛 `ClassNotFoundException`——**启动中止**。
- dev 模式下（`Global.getSettings().isDevMode()`）`MagicVariables.verbose = true`，各 `getX` 成功读到值时会额外输出 `INFO` 日志 `<modId> , <id> : <value>`——这是核对读取链的第一个日志位置。

::: tip "设置没生效"的排查顺序
先看 `starsector.log` 里的 warn 行——三类文案分别对应段缺失、键缺失、类型错误，据此定位写错的段名或键名；再对照下面的缺省值表确认哪些参数正在走缺省。dev 模式下可 F8 重载设置并重进战斗，不必每次都重启游戏。
:::

### 读取 API 速查

`MagicSettings` 全部是静态方法，第一参是 mod id（段名），第二参是段内键名：

| 方法 | 返回 | 缺省值 | 取值方式 |
|---|---|---|---|
| `getBoolean(String modId, String id)` | `boolean` | `false` | JSON 布尔 |
| `getString(...)` | `String` | `""`（空串）| JSON 字符串 |
| `getFloat(...)` | `float` | `0` | JSON 数字（`getDouble` 强转）|
| `getInteger(...)` | `Integer` | `0` | JSON 整数（`getInt`）|
| `getColorRGB(...)` | `Color` | `Color.RED` | `"[r,g,b]"` 字符串，分量 clamp 到 0–255，alpha 恒 255 |
| `getColorRGBA(...)` | `Color` | `Color.RED` | `"[r,g,b,a]"` 字符串，分量 clamp 到 0–255 |
| `getList(...)` | `List<String>` | 空 `ArrayList` | JSON 字符串数组 |
| `getFloatMap(...)` | `Map<String, Float>` | 空 `HashMap` | JSON 对象，值转 double |
| `getStringMap(...)` | `Map<String, String>` | 空 `HashMap` | JSON 对象 |
| `getColorMap(...)` | `Map<String, Color>` | 空 `HashMap` | JSON 对象，值按 `[r,g,b,a]` 解析 |

（缺省值逐条对应官方源码相应方法的实现；颜色解析经 `toColor3` / `toColor4` 完成，分量经 `clamp255` 处理。）

### 规则 1：段名必须是自己 MOD 的 id，段内键不得与其他 MOD 冲突

`modSettings.json` 是**全局合并**的：段名（第一参）必须用自己 `mod_info.json` 的 id（本页示例 `gear_in`），不要用别人 MOD 的 id 段，也不要在段内使用会被其他 MOD 覆盖的通用键名。

违反的可观察结果：段名写错（如 `gear_industry`）时读取的是那个段的合并结果，本 MOD 的键全部落到"缺省值"分支，每键一条 warn；与别人共用段名时，本 MOD 的键可能与对方同名键互相覆盖——设置"时灵时不灵"且没有任何报错。

修正：段名与 `mod_info.json` 的 `id` 逐字一致，段内键加 MOD 专属前缀（示例 `beam_width` 等在 `gear_in` 段内即天然隔离）。

### 规则 2：键缺失/段缺失/类型错误都不抛异常，返回类型缺省值并写 warn 日志

三个失败分支各有固定 warn 文案（官方源码逐字）：

- 段不存在：`unable to find <modId> in modSettings.json`
- 段存在但键不存在：`unable to find <id> within <modId> in modSettings.json`
- 取值抛 `JSONException`（类型不匹配，如把 `[255,0,0]` 字符串当 `getFloat`）：`unable to read content of <modId> in modSettings.json`

**必须**认识到缺省值会参与后续运行语义：`getBoolean` 缺省 `false`、`getColorRGB` 缺省 `Color.RED`、`getFloat` 缺省 `0`。依赖"键存在才有功能"的业务逻辑，在键缺失时会以缺省值静默运行——光束变成红色、状态条消失、开关关闭，日志里只有 warn 行。

违反的可观察结果：漏写一个键，功能照常"运行"但参数是缺省值（颜色 `(255,0,0)`、数值 `0`、布尔 `false`）， `starsector.log` 出现对应 warn 行，没有 ERROR。

修正：对照缺省值表检查 warn 行；业务开关显式写进文件或对缺省结果做显式处理，不要把"缺省值"当真实配置。

### 规则 3：颜色必须写成 "[r,g,b]" / "[r,g,b,a]" 字符串

`getColorRGB` / `getColorRGBA` 用 `toColor3` / `toColor4` 解析：去掉首尾括号、按 `,` 分割、`Integer.parseInt`、分量 clamp 到 0–255。

写成 JSON 数字数组（`[255,0,0]` 会被 JSON 解析器读成 `JSONArray`）或写成分量数字，都会在 `reqSettings.getString(id)` 处抛 `JSONException` → warn + `Color.RED`。

违反的可观察结果：颜色配置"永远不生效"，读取结果恒为红；日志有 `unable to read content of ...` 或 `unable to find ...` warn 行。修正：值必须是带引号的字符串 `"[210,240,255]"`。

### 建议 1：不要在每帧解析，init 时读取一次缓存到字段

`MagicSettings` 的 javadoc 原文警告：虽然方法很快，但"I would advise against trying to parse a JSON file every frame"。

每次调用都做 `modSettings.has(modId)` → `getJSONObject` → `has(id)` → 类型转换的完整链路。

读取结果在本场战斗内不会变化（重新读取只在 MagicLib 的 `onApplicationLoad` / F8 重载），示例在接入类构造期（`addPlugin` 注册时）读一次缓存到字段。

## MagicFakeBeam：瞬时伤害伪光束

`MagicFakeBeam` 从任意世界坐标发射一条视觉光束并**即时结算命中伤害**。官方 javadoc 给出了三条语义边界（`org.magiclib.util.MagicFakeBeam` 官方 javadoc）：

- 伤害是**瞬时的**，因此只适合 burst 光束（"It deal damage instantly and is therefore only meant to be used for burst beams"）；
- 光束**不可被中途穿过的物体截断** （"It cannot be 'cut' by another object passing between the two ends, thus a very short duration is preferable"）；
- 与**原版光束不同，对装甲造成全额伤害** （"Unlike vanilla, it deals full damage to armor, be careful when using HIGH_EXPLOSIVE damage type"）。

::: info 三条语义边界的共同根源
伪光束是一条调用内完成的闭合过程：候选查询 → 命中点 → 瞬时结算 → 视觉注册，之后没有任何逐帧机制接管它。因此伤害只能瞬时结算、视觉只能是一段固定直线、伤害类型不经光束折减——三条边界来自同一种"无持续状态"的实现，理解这一点就无需逐条死记。
:::

### spawnFakeBeam 签名速查

```java
public static void spawnFakeBeam(
        CombatEngineAPI engine, Vector2f from, float range, float angle,
        float width, float full, float fading, float impactSize,
        Color core, Color fringe,
        float normalDamage, DamageType type, float emp, ShipAPI source)
```

| 参数 | 语义 |
|---|---|
| `from` | 光束起点（世界坐标）|
| `range` / `angle` | 最大射程与方向（度；`MathUtils.getPoint(from, range, angle)` 生成默认终点）|
| `width` / `full` / `fading` | 视觉宽度、全亮时长、淡出时长（秒）——只影响视觉 |
| `impactSize` | 命中光斑大小 |
| `core` / `fringe` | 光束核心色 / 边缘色 |
| `normalDamage` / `type` / `emp` | 伤害量与类型、EMP |
| `source` | 伤害归属的 `ShipAPI`（技能与舰船伤害加成按它计算；同阵营未熄火战机、自身被过滤）|

### 内部链路（行为事实）

1. 候选收集：`CombatUtils.getEntitiesWithinRange(from, range + 500)`（LazyLib）取附近实体。
2. 过滤：`CollisionClass.NONE`（相位等）跳过；舰船分支排除 `source` 自身、`getParentStation() == 自身` 的模块、同 owner 且未熄火的战机；小行星与**敌方**导弹也参与命中。
3. 命中点：舰船用 `getShipCollisionPoint`（护盾弧感知），其余用 `getCollisionPointOnCircumference`；取**最近**命中点替换光束终点。
4. 结算：命中时 `engine.applyDamage(target, end, damage, type, emp, false, true, source)` （`bypassShields=false`、`dealsSoftFlux=true`）+ 两枚 `addHitParticle` 命中光斑。
5. 视觉：`MagicFakeBeamPlugin.addBeam(full, fading, width, from, angle, 距离+10, core, fringe)`——注册光束 sprite。
   - 光束 sprite 由 MagicFakeBeamPlugin 在 `renderInWorldCoords` 里逐帧渲染（`org.magiclib.plugins.MagicFakeBeamPlugin`）。

### 规则 4：伤害在调用内瞬时结算，持续视觉时间 ≠ 持续伤害时间

`spawnFakeBeam` 的 `applyDamage` 在**调用返回前**已经完成；`full` / `fading` 只控制光束 sprite 的可见时长 （`MagicFakeBeamPlugin` 按 `FULL` 倒计时淡出）。

违反的可观察结果：光束"看起来还在"的 0.5 秒里敌人已经承受完全部伤害——把伪光束当持续伤害光束用，伤害节奏完全错误 （每 2 秒一发 60 伤害的伪光束 DPS 是 30 而不是视觉暗示的数值）。修正：需要持续伤害用真光束武器（原版 `BeamAPI`），伪光束只用于 burst / FX。

### 规则 5：光束视觉不可被中途物体截断，适合短促使用

伪光束是**单段直线** sprite，没有"被遮挡物截断"的概念；官方 javadoc 因此建议短时长。

违反的可观察结果：一发长视觉时长（如 `full=2`）的伪光束，舰船从中间穿过时光束视觉不中断、也没有二次命中 ——与玩家对原版光束的直觉冲突。修正：视觉时长保持短促（示例 `full=0.15` / `fading=0.4`）；需要可截断的持续光束用真光束。

### 规则 6：HIGH_EXPLOSIVE 对装甲全额伤害

原版光束对装甲有伤害折减，`spawnFakeBeam` 的 `applyDamage` 不折减——官方 javadoc 明确警告 HE 类型要小心。

违反的可观察结果：同一门标称 DPS 的 HE 伪光束比原版 HE 光束对重甲舰船的剥甲速度快得多（装甲段被更快击穿）；用 HE 伪光束做"看起来像光束"的武器会明显超模。修正：伪光束尽量用 ENERGY / KINETIC，或用 HE 时把标称伤害压到与设计 DPS 匹配。

::: warning 两大常见误用
**把伪光束当持续伤害光束**：视觉还在、伤害已全部结算，DPS 与视觉暗示不符（规则 4）。**把伪光束当可截断光束**：光束被舰船穿过时视觉不中断、也无二次命中，与直觉冲突（规则 5）。需要持续伤害或可截断视觉时，改用原版真光束（`BeamAPI`）。
:::

### 规则 7：视觉渲染依赖 MagicLib 注册的 MagicFakeBeamPlugin，使用方 MOD 不得重复注册

`MagicFakeBeamPlugin` 是 `BaseEveryFrameCombatPlugin`。

它由 MagicLib 自己的 `data\config\settings.json` 的 `plugins` 段注册（键 `ML_fakeBeamPlugin` → `org.magiclib.plugins.MagicFakeBeamPlugin`）。

它每场战斗 `init` 时**清空静态列表 `BEAMS`**，`renderInWorldCoords` 逐帧绘制。

使用方 MOD 不需要也不应该注册同名插件；若该插件未被实例化（settings 合并被覆盖、脚本加载失败），光束视觉不出现但**伤害照常结算**。

违反的可观察结果：`starsector.log` 显示光束调用与伤害都发生了（敌人掉血），画面上没有光束；或把 MagicLib 的 `ML_fakeBeamPlugin` 键覆盖成自己的类后出现重复/丢失渲染。

修正：不要动 MagicLib 的 settings `plugins` 键；确认 `ML_fakeBeamPlugin` 键存在且指向 `org.magiclib.plugins.MagicFakeBeamPlugin`。

### 碰撞助手速查

| 方法 | 约定 |
|---|---|
| `getShipCollisionPoint(Vector2f segStart, Vector2f segEnd, ShipAPI ship, float aim)` | 线段对**舰体 + 护盾弧**的命中点：护盾关闭/无盾 → 直接返回 `CollisionUtils.getCollisionPoint(...)` 的结果（**可能为 null**）；护盾开启时起点在盾内且处于弧内 → 起点沿 `aim` 方向偏移 15；圆周碰撞点在弧内 → 偏移 1；否则按舰体碰撞点偏移 1（`getCollisionPoint` 返回 null 后继续偏移会**传递 null 风险**）|
| `getCollisionPointOnCircumference(segStart, segEnd, circleCenter, circleRadius)` | 线段对圆周的第一个交点；起点在圆内 → **返回起点**；线段够不到圆周 → 返回 null |

### 规则 8：碰撞助手的返回必须判空

`getShipCollisionPoint` 的 javadoc 约定："if segment can not hit the ship, will return null"； `getCollisionPointOnCircumference` 在起点在圆外且线段够不到圆周时返回 null。

护盾关闭分支更是把 `CollisionUtils.getCollisionPoint` 的 nullable 结果原样透传 （与 LazyLib 查询纪律同款的 `@Nullable` 判空要求）。

违反的可观察结果：目标在射程外或线段不穿过碰撞边界时，返回 null 被直接 `.length()` / 参与 `MathUtils.getPoint` 运算 → NPE（护盾开启分支里 `MathUtils.getPoint(null, ...)` 即炸）或产生 NaN 坐标，日志 `ERROR` + 堆栈。

修正：使用前判空（示例 `fireBeam` 的 `enemy == null` 分支同款写法）。

### spawnAdvancedFakeBeam 与尾迹纹理（MagicTrailPlugin 依赖）

`spawnAdvancedFakeBeam(engine, from, range, angle, widthIn, widthOut, growth, textureCore, textureFringe, textureLength, textureScroll, smoothIn, smoothOut, full, fading, impactSize, core, fringe, normalDamage, type, emp, source)` 是纹理版：

用 `fx` 分类的两张贴图（`Global.getSettings().getSprite("fx", textureCore)`）做核心/边缘纹理、宽度渐变与滚动。

尾迹通过 `MagicTrailPlugin.addTrailMemberAdvanced` 在 `BELOW_INDICATORS_LAYER` 层渲染。

由此带出 `MagicTrailPlugin` 的最小纪律（完整尾迹 API 不在本页范围）：

- **纹理要求**：`MagicTrailPlugin` 类 javadoc 要求参与尾迹的 sprite 宽高都必须是 2 的倍数（16 / 32 / 64 / 128 等），且是垂直方向的贴图。
- **ID 复用纪律（规则 9）**：`getUniqueID()` 返回的 ID 必须在同一条尾迹生命周期内**保存并复用**—— 每帧生成新 ID → 每条都是独立的 1 片段尾迹，**表现为多条无法连接的短条**；同一条 trail 中途更换 sprite 会拆成不同的 texture tracker。
- `spawnAdvancedFakeBeam` 内部对每条光束取两次 `getUniqueID()`（核心与边缘各一），不需要自己管理 ID——但若您直接用 `MagicTrailPlugin` 做尾迹，必须自己遵守上述纪律。

## MagicUI：战斗 HUD 工具

`MagicUI`（`org.magiclib.util.MagicUI`）提供战斗 HUD 的状态条（status bar）、系统条/框、文本绘制。

它基于 LazyLib 的 `LazyFont`（Victor 字体，静态块加载 `graphics/fonts/victor14.fnt` / `victor10.fnt`）。

### 状态条渲染链（先记住"谁在画"）

`drawInterfaceStatusBar`（带 key 重载）**只注册数据**：把 `StatusBarData` 写进静态表 `statusBars`。真正的绘制由 MagicLib 的另一条链完成：

1. `MagicRenderPlugin`（settings `plugins` 段的 `ML_spriteRenderManager`，`org.magiclib.plugins.MagicRenderPlugin`）每帧 `advance` 驱动绘制链。
   - 调用入口：`MagicUIInternal.callRenderMethods$MagicLib()`。
2. `MagicUIInternal.callRenderMethods()`（`org.magiclib.util.MagicUIInternal`）调用 `MagicUI.drawStatusBarMap()`；
3. `drawStatusBarMap` 遍历 `statusBars`，绘制属于当前玩家舰的条，并**移除未刷新的条**。
   - 移除判据：`data.getShip() != playerShip || getTotalElapsedTime(true) - data.getLastRefreshed() >= 5f`。

`MagicRenderPlugin.advance` 是 `EveryFrameCombatPlugin.advance`（暂停时也照常调用），且移除判据用 `getTotalElapsedTime(true)`（含暂停的时间）——所以**暂停期间时间照算**。

### 速查表

| 方法 | 语义 |
|---|---|
| `drawInterfaceStatusBar(ShipAPI, String key, float fill, Color inner, Color border, float secondFill, String text, int number)` | 注册一条状态条（key 显式）；fill 0–1、secondFill 类硬幅能 0–1、number 0–999999 |
| `drawInterfaceStatusBar(ShipAPI, float fill, ...)`（无 key 重载）| 用 `text` 兼作 key——javadoc 明确不建议改变文本，否则生成新条 |
| `drawInterfaceStatusBar(ShipAPI, String key, Vector2f statusBarLoc, ...)` | 显式屏幕坐标版本（`statusBarLoc` 未缩放）|
| `drawHUDStatusBar(ShipAPI, float fill, Color, Color, float secondfill, String bottext, String toptext, boolean offset)` | 玩家舰屏幕位置旁的短条 + 两行文本（自动换算世界坐标→屏幕坐标）|
| `drawSystemBar(ShipAPI, Color, float fill, float blendTime)` / `drawSystemBox(...)` | 战术系统旁的小条/小框（`blendTime` 控制颜色渐变）|
| `addBar(ShipAPI, float fill, Color, Color, float secondFill, Vector2f screenPos[, boxHeight, boxWidth, drawBorders])` | 任意屏幕位置绘条（立即绘制）|
| `addText(ShipAPI, String, Color, Vector2f screenPos, boolean openGLStack)` / `addTextNonScaling(...)` / `addStatusText(...)` | Victor14/10 文本（`openGLStack=true` 时方法内自开自关 GL 栈）|
| `shouldDrawHUD(ShipAPI)` | 当前是否应绘制 HUD（战斗 UI 非 null、非指挥界面、HUD 显示中、是玩家舰）|
| `getInterfaceOffsetFromSystemBar(...)` / `getUIElementOffset(...)` | 按武器组/联队数计算系统条与状态条偏移 |
| `UI_SCALING` / `scale(...)` / `scaledVector(...)` / `scaledCopy(...)` | 屏幕缩放系数（`getScreenScaleMult()`）与坐标缩放助手 |
| `openGL11ForText()` / `closeGL11ForText()` / `openGLForMiscWithinViewport()` / `closeGLForMiscWithinViewport()` / `setViewportOpenGLCalls(boolean)` | 自管 OpenGL 栈的文本/绘制入口（必须成对）|

### 规则 10：状态条 key 必须对本 MOD 唯一，且必须定期刷新（约 5 秒内，含暂停时间）

javadoc 原文：key "Should be unique to your mod and whatever this status bar is displaying"；

"This must be called every couple of seconds to remain active" （`drawStatusBarMap` 的实际移除阈值为 `getTotalElapsedTime(true)` 差值 ≥ 5 秒）。

违反的可观察结果：key 与其他 MOD 撞车 → 两条数据互相覆盖，数字/填充"互相打架"；停止刷新（或暂停守卫把刷新挡掉）约 5 秒后状态条**消失**，恢复刷新才回来——暂停 10 秒再恢复，条已经没了。

修正：key 用本 MOD 专属前缀（示例 `gear_in_beam_charge`）；刷新调用放在暂停守卫**之前**（示例 `advance` 写法），暂停期间也保持刷新。

::: warning 状态条"过一会儿消失"
最常见的触发是刷新调用被暂停守卫挡住：暂停超过约 5 秒（含暂停的游戏时间）后 `drawStatusBarMap` 把该条移除，恢复刷新才回来（规则 10）。

另一个常见触发是 key 撞车——两条数据共用同一 key，后写覆盖先写。修正：刷新调用放在暂停守卫**之前**（示例写法）；key 用本 MOD 专属前缀。
:::

### 规则 11：绘制方法只在"ship 是玩家舰且 HUD 可见"时输出，非玩家舰静默跳过

`drawInterfaceStatusBar` / `drawSystemBar` / `drawHUDStatusBar` / `addStatusText` 等开头都有双重守卫：

`ship != Global.getCombatEngine().getPlayerShip()` → 直接 return；

`getCombatUI() == null || !isUIShowingHUD()` → 直接 return （如 `drawInterfaceStatusBar`；只在玩家舰且 HUD 可见时输出）。

违反的可观察结果：给非玩家舰传 `ship`，状态条/系统条完全不出现，**没有任何报错**——"别人舰上画了条"是不可能的。修正：只对玩家舰调用；需要给敌方舰显示信息时，用 `drawHUDStatusBar` 的 offset 参数锚定在玩家舰屏位置（示例没有这个需求）。

### 规则 12：自管 OpenGL 栈的入口必须成对使用

`MagicUI` 的文本/绘制方法直接操作 OpenGL 栈：`addText(..., openGLStack=true)` 方法内成对调用 `openGL11ForText` / `closeGL11ForText`；

您自己调用 `openGL11ForText` / `openGLForMiscWithinViewport` 时 **必须**成对调用对应的 close 方法；

`setViewportOpenGLCalls(true)` 后必须复位 `false` （javadoc 原话 "Remember to UNSET THIS after your rendering"）。

违反的可观察结果：开栈后没有关闭，后续 HUD 或世界渲染的投影/混合状态被污染——画面出现错位、全屏闪烁或异常混合的"脏渲染"，且只在某些界面状态下出现。修正：一律成对使用；优先用方法内自管栈的重载（示例只用 `drawInterfaceStatusBar`，不需要手动开栈）。

## MagicCampaign：生涯工具

`MagicCampaign`（`org.magiclib.util.MagicCampaign`）提供生涯层的辅助工具族。

所有写侧入口都以 `Global.getSector()` 与**已加入星区**的实体为操作对象，因此**只能在生涯（campaign）上下文调用**——战役（mission）战斗与主菜单没有生涯数据 （`Global` 生命周期：生涯启动/读档后才非 null）。

### 速查表

| 入口 | 语义 | 生涯上下文要求 |
|---|---|---|
| `createFleetBuilder()` | `MagicFleetBuilder` 链式生成舰队（`setFleetName` / `setFleetFaction` / `setFlagshipVariant` / `setCaptain` / `setAssignment` / `create()` 等）| 需要 sector（生成后 `spawnFleet` 式加入）|
| `createCaptainBuilder(String factionId)` | `MagicCaptainBuilder` 链式生成人物（`setFirstName` / `setGender` / `setRankId` / `setPostId` / `create()` 等）| 无硬性 sector 依赖，但产物供生涯对象使用 |
| `loadVariant(String path)` | 从 `.variant` 文件解析 `ShipVariantAPI`（用于"需要不同 MOD 加载"的变体）；解析失败 → **warn 日志 + 返回 null**；成功时自动加 `Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE` | 只依赖 `SettingsAPI`，任意场景可调 |
| `spawnFleet(fleet, spawnLocation, assignment, target, isImportant, transponderOn, verbose)` | 舰队入点 + 任务 + 重要/应答机标记（verbose 时打印两行日志）| sector 必选 |
| `hyperspaceCleanup(StarSystemAPI)` | 清除星系周围超空间云（`NebulaEditor.clearArc`）| 星系存在即可 |
| `placeOnStableOrbit(object, spin)` | 就近匹配已有轨道（无参照时绕星系中心）；超空间内退化为固定位置 | sector 实体 |
| `createDerelict(variantId, condition, discoverable, discoveryXp, recoverable, orbitCenter, orbitStartAngle, orbitRadius, orbitDays)` | 生成残骸舰（`BaseThemeGenerator.addSalvageEntity` + `setDiscoverable` + 圆形轨道；recoverable=true 时写打捞 special）| orbitCenter 必须已加入星区 |
| `createDerelict(..., boolean isRecoverable)`（十参重载）| 同上；`isRecoverable == false` → 追加 `Tags.UNRECOVERABLE` | 同上 |
| `createDebrisField(id, radius, density, duration, glowDuration, salvageXp, defenderProbability, defenderFaction, defenderFP, detectionMult, discoverable, discoveryXp, orbitCenter, ...)` | 碎片场 + 打捞物/守军/发现 XP（radius clamp 1000，duration ≤0 → 永久）| orbitCenter 已加入星区 |
| `addSalvage(cargo, carrier, lootType, lootID, amount)` | 给可打捞实体追加补给/燃料/船员/陆战队/商品/武器/战机/船插/特殊物品 | carrier 为生涯实体 |
| `addJumpPoint(id, name, linkedPlanet, orbitCenter, angle, radius, days)` | 快捷建跳跃点（`createJumpPoint` + 标准虫洞外观 + 轨道）| orbitCenter 已加入星区 |
| `addSimpleMarket(entity, id, name, size, faction, isFreeport, isHidden, conditions, industries, hasStorage, ...)` | 快捷建市场（子市场/条件/产业/调查度 FULL）| entity 为生涯实体 |
| `addCustomPerson(market, firstName, lastName, portraitId, gender, factionId, rankId, postId, isMarketAdmin, ...)` | 快捷加人物到市场（管理员/通信目录）| market 为生涯市场 |
| `isAvailableAtMarket(market, ...)` / `isAvailableToPlayer(...)` | 赏金式触发条件检查（市场 id/势力/规模；玩家等级/天数/舰队 FP/关系/memKeys）| 只读，需要 sector |
| `findSuitableTarget(entityIDs, marketFactions, distance, seek_themes, avoid_themes, entities, defaultToAnyEntity, prioritizeUnexplored, verbose)` | 按距离带（`CORE`/`CLOSE`/`FAR`）与主题标签随机挑选目标实体；`@Nullable`，找不到返回 null | 需要 sector |
| `PlayerFleetSizeMultiplier(float)` / `RelativeEffectiveStrength(CampaignFleetAPI)` | 玩家舰队 FP 倍数 / 有效强度比（null 判空）| 需要玩家舰队 |

::: caution 生涯工具必须在生涯上下文调用，先判 `Global.getSector()`（规则 13）
- 写侧入口（`createDerelict` / `createDebrisField` / `addJumpPoint` / `addSimpleMarket` / `spawnFleet`）与查询入口（`findSuitableTarget` / `isAvailableToPlayer`）。
- 它们都以 `Global.getSector()` 返回的生涯数据为所属对象。

战役（mission）战斗与主菜单场景没有生涯数据（`Global` 生命周期：生涯启动/读档后才非 null），不判空直接调用会在第一行 `Global.getSector().xxx` 抛 NPE。

违反的可观察结果：在战役战斗或主菜单回调里调用 `findSuitableTarget` / `createDerelict` → NPE 堆栈（日志 `ERROR`），流程中止；传入的 `orbitCenter` 若不属于当前星区，残骸可能生成在错误位置或找不到星区。

修正：调用前判 `Global.getSector() == null`（示例 `logCampaignInfo` / `spawnDerelictExample` 的判空写法）；写侧入口只在生涯脚本（新档生成、事件链）里调用。
:::

### 规则 14：createDerelict 十参重载的 isRecoverable=false 会加 Tags.UNRECOVERABLE

九参重载的 `recoverable` 参数控制**打捞 special** 是否写入（能否在打捞面板回收舰船）；十参重载追加的 `isRecoverable == false` 会 `addTag(Tags.UNRECOVERABLE)`。

违反的可观察结果：想生成"不可打捞"的残骸只传 `recoverable=false` 时，舰船本体不可回收但可能仍保留其他可打捞内容； `isRecoverable=false` 的残骸在打捞流程中按原版 `UNRECOVERABLE` 语义处理（不可作为舰船回收）。

修正：两个布尔分清——`recoverable` 控制打捞 special 写入，`isRecoverable` 控制不可回收 tag。

### 规则 15：loadVariant 解析失败返回 null，使用前必须判空

`loadVariant` 把整个解析包进 try/catch，任何字段缺失/类型错误 → `log.warn("could not load ship variant at " + path, e)` + 返回 null。

违反的可观察结果：`.variant` 文件 JSON 损坏或字段不全时，返回值 null 被直接 `.getHullId()` / 传给 `createDerelict` → NPE；日志只有 warn 行（"could not load ship variant at ..."），没有 ERROR。

修正：判空 + warn 日志（示例 `spawnDerelictExample` 的调用方写法：传入的 variantId 是注册过的装配 id 时不会走到失败分支，但读侧必须判空）。

成熟实例：`MagicCampaign.hyperspaceCleanup(system)` 用于清理自建星系周围的超空间云；

`MagicSettings.getList(MagicVariables.MAGICLIB_ID, "magicTrail_files")` 用于读取 MagicLib 段的尾迹文件列表；

`MagicFakeBeam.getCollisionPointOnCircumference` 用于线段-圆命中点计算。

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 依赖声明 | `mod_info.json` 的 `dependencies`（`MagicLib`，S12-P01 已有）| 启动器强制检查（缺装红色不可勾选）；代码侧 `org.magiclib.**` 引用 |
| 设置文件 | `data/config/modSettings.json`（本页新增，段 `gear_in`）| `Magic_modPlugin.onApplicationLoad` → `MagicSettings.loadModSettings` → `getMergedJSONForMod("data/config/modSettings.json", "MagicLib")`（跨 MOD 合并）|
| 设置读取 | `MagicSettings.getBoolean/getString/getFloat/getInteger/getColorRGB/getColorRGBA/getList/get*Map` | 段内按键取值；缺省 false / `""` / 0 / `Color.RED` / 空集合；三类 warn 文案 |
| 伪光束 | `MagicFakeBeam.spawnFakeBeam` / `spawnAdvancedFakeBeam` | 候选范围查询（LazyLib）→ 最近命中点 → `applyDamage` 瞬时结算 + 命中粒子 → `MagicFakeBeamPlugin.addBeam` 视觉 |
| 光束视觉 | `MagicFakeBeamPlugin`（settings `plugins` 键 `ML_fakeBeamPlugin`）| `renderInWorldCoords` 逐帧绘制；`init` 清空 `BEAMS`；暂停时视觉冻结（`org.magiclib.plugins.MagicFakeBeamPlugin`）|
| 碰撞助手 | `getShipCollisionPoint` / `getCollisionPointOnCircumference` | 护盾弧/圆周命中点；null 约定 |
| HUD 状态条 | `MagicUI.drawInterfaceStatusBar(keyed)` → `statusBars` 静态表 | `MagicRenderPlugin.advance` → `MagicUIInternal.callRenderMethods` → `drawStatusBarMap` 绘制 + 5 秒超时移除（`org.magiclib.util.MagicUI` / `org.magiclib.util.MagicUIInternal` / `org.magiclib.plugins.MagicRenderPlugin`）|
| HUD 守卫 | 玩家舰 + `isUIShowingHUD()` + `getCombatUI() != null` | 绘制方法首部守卫，非玩家舰静默跳过 |
| 生涯工具 | `MagicCampaign.createDerelict/createDebrisField/addJumpPoint/addSimpleMarket/addCustomPerson/createFleetBuilder/findSuitableTarget/...` | 以 `Global.getSector()` 为所属对象；生涯上下文之外调用 NPE |
| 变体读取 | `MagicCampaign.loadVariant(path)` | 解析失败 warn + null；成功自动加 `VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE` |
| 常量/尺寸 | `MagicVariables.MAGICLIB_ID` / `getSectorSize()` / `verbose` | `"MagicLib"` 常量、星区范围缓存（按星系位置最大距离）、dev 模式标志 |
| 可观察结果 | `starsector-core\starsector.log`；战斗 HUD；战斗画面；生涯存档 | 本页示例的 `GearIn magic ...` 日志行、`Beam charge` 状态条、光束画面、生涯日志/残骸 |

## 规则汇总

| # | 强度 | 规则 | 违反的可观察结果 |
|---|---|---|---|
| 1 | **必须** | `modSettings.json` 段名必须是自己 MOD 的 id，段内键不与他人冲突（文件是全局合并的）| 段名写错 → 键全部缺省值 + 每键一条 warn；与他段共用 → 键互相覆盖、设置"时灵时不灵"，无报错 |
| 2 | **必须** | 键/段缺失或类型错误不抛异常，返回类型缺省值并写 warn；缺省值会参与运行语义，不得依赖缺省值表达业务意图 | 漏键时功能以缺省值静默运行（颜色红、数值 0、布尔 false），日志只有 warn 行 |
| 3 | **必须** | 颜色值必须是 `"[r,g,b]"` / `"[r,g,b,a]"` 字符串 | 写成数字数组 → `JSONException` → warn + 恒为 `Color.RED` |
| 4 | **必须** | `spawnFakeBeam` 伤害在调用内瞬时结算；持续视觉时间 ≠ 持续伤害时间 | 光束"还在"时伤害已全部结算，持续伤害预期落空 |
| 5 | **必须** | 伪光束视觉不可被中途物体截断，适合短促 burst / FX | 长视觉时长光束被穿过时不中断、无二次命中，与直觉冲突 |
| 6 | **必须** | HIGH_EXPLOSIVE 对装甲全额伤害（官方 javadoc 警告）| HE 伪光束剥甲速度显著高于同标称原版光束，容易超模 |
| 7 | **必须** | 视觉由 MagicLib 注册的 `MagicFakeBeamPlugin` 渲染；使用方 MOD 不得重复注册同名插件 | 插件未实例化时伤害照常、光束视觉缺失；覆盖 settings 键后渲染异常 |
| 8 | **必须** | `getShipCollisionPoint` / `getCollisionPointOnCircumference` 可能返回 null，使用前必须判空 | 线段不命中时 NPE（`MathUtils.getPoint(null, ...)`）或 NaN 坐标 |
| 9 | **必须** | `MagicTrailPlugin.getUniqueID()` 的 ID 必须跨帧复用；同一条 trail 不得中途换 sprite | 每帧新 ID → 多条无法连接的短条；换 sprite → 拆成两条 trail |
| 10 | **必须** | 状态条 key 对本 MOD 唯一；必须约 5 秒内刷新一次（含暂停时间，`getTotalElapsedTime(true)`）| 撞 key → 数据互相覆盖；停止刷新（含暂停守卫挡住）约 5 秒后条消失 |
| 11 | **必须** | HUD 绘制只在"玩家舰 + HUD 可见"时输出，非玩家舰静默跳过 | 非玩家舰上状态条/系统条不出现，无任何报错 |
| 12 | **必须** | 自管 OpenGL 栈入口（`openGL11ForText` / `openGLForMiscWithinViewport` / `setViewportOpenGLCalls(true)`）必须成对/复位 | 后续 HUD 与世界渲染投影/混合状态被污染，画面错位或异常混合 |
| 13 | **必须** | 生涯工具只能在生涯上下文调用，先判 `Global.getSector() == null` | 战役/主菜单调用 `findSuitableTarget` / `createDerelict` → NPE |
| 14 | **必须** | `createDerelict` 十参重载 `isRecoverable=false` → 追加 `Tags.UNRECOVERABLE`（recoverable 与 isRecoverable 是两个布尔）| 只传 `recoverable=false` 时残骸仍可被回收/语义错位 |
| 15 | **必须** | `loadVariant` 解析失败返回 null（warn 日志），使用前必须判空 | `.variant` 损坏 → NPE；日志只有 warn 行 |
| 16 | 建议 | `MagicSettings` 不要在每帧解析，init 时读取一次缓存到字段 | 每帧重复 `has` → `getJSONObject` → 类型转换链路，纯浪费 |
| 17 | **必须**（继承 S12-P01）| 引用 `org.magiclib.**` 前必须在 `dependencies` 声明 `MagicLib` | 用户缺依赖时 `NoClassDefFoundError` |

## 完整最小例

本页示例由五个小类组成：原混装多个职责的类按"一文件一角色"约定拆分为设置接入、伪光束、状态条、生涯工具与薄入口类接入五份（全部位于包 `data.scripts.gearin.lib`，归本页所有）。

新增数据文件 `data/config/modSettings.json`（归本页所有，段 `gear_in`）；新增战斗 HUD 状态条 key `gear_in_beam_charge`（归本页所有）。不新增 PNG 资产。

`GearInModPlugin` 属于既有 MOD 主插件示例，本页只追加一行接入片段。

在既有累计工程布局上追加（MOD 根目录 `Starsector\mods\GearIndustry`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                      # dependencies 已含 MagicLib
├── data\
│   └── config\
│       └── modSettings.json           # 本页新增（MagicLib 设置段，注册入口）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # 既有 MOD 主插件，本页追加一行接入
│       ├── combat\                    # 既有战斗插件目录
│       └── lib\                       # 本页五个类（GearInMagicWiring / GearInMagicSettings /
│                                      # GearInBeamCaster / GearInHudBar / GearInCampaignTools）
└── jars\
```

### data/config/modSettings.json（本页所有）

MagicLib 按已启用 MOD 合并同路径文件，本 MOD 只提交自己的 `gear_in` 段。颜色必须是 `"[r,g,b]"` 字符串：

```json
{
  "gear_in": {
    "hud_enabled": true,
    "beam_width": 12.0,
    "beam_range": 900.0,
    "beam_damage": 60.0,
    "beam_emp": 25.0,
    "beam_full": 0.15,
    "beam_fading": 0.4,
    "beam_impact_size": 40.0,
    "beam_color_core": "[210,240,255]",
    "beam_color_fringe": "[60,120,255]",
    "beam_tags": ["gear_in", "demo"]
  }
}
```

要点：段名 `gear_in` 与 `mod_info.json` 的 `id` 逐字一致（规则 1）；`beam_full` / `beam_fading` 只控制视觉时长（规则 4）；颜色键为字符串形式（规则 3）。

### GearInMagicSettings：设置接入（MagicSettings）

职责：从 `data/config/modSettings.json` 的 `gear_in` 段读取光束与 HUD 配置，构造时缓存一次、不在每帧解析（建议 1）；键缺失返回类型缺省值并写 warn 日志，不抛异常——缺省值会参与后续运行语义。

绑定：由接入类在字段初始化时创建（`new GearInMagicSettings()`），不需要注册入口；设置读取发生在 MagicLib 的 `onApplicationLoad` 之后（游戏启动时已缓存），因此构造期读取与战斗 `init` 时序等价。

```java
package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.List;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicSettings;

import com.fs.starfarer.api.Global;

/** 设置接入（MagicSettings）：从 data/config/modSettings.json 的 "gear_in" 段读取
 * 光束与 HUD 配置，构造时缓存一次、不在每帧解析（MagicSettings javadoc 建议）。
 * 键缺失返回类型缺省值（false / 0 / Color.RED / 空集合）并写 warn 日志，不抛
 * 异常——缺省值会参与后续运行语义，业务开关不要依赖缺省值。 */
public class GearInMagicSettings {

    private static final Logger LOG = Global.getLogger(GearInMagicSettings.class);

    /** data/config/modSettings.json 中本 MOD 的段名（= MOD id "gear_in"）。 */
    private static final String CONFIG_SECTION = "gear_in";

    /** HUD 状态条总开关。 */
    public final boolean hudEnabled;
    /** 光束宽度 / 射程 / 伤害 / EMP（数值键缺省 0）。 */
    public final float beamWidth;
    public final float beamRange;
    public final float beamDamage;
    public final float beamEmp;
    /** 全亮与淡出时长（秒）：只控制视觉，不影响伤害结算。 */
    public final float beamFull;
    public final float beamFading;
    /** 命中光斑尺寸。 */
    public final float beamImpactSize;
    /** 光束核心 / 边缘颜色（缺省 Color.RED）。 */
    public final Color beamCoreColor;
    public final Color beamFringeColor;
    /** 光束标签列表（缺省空集合）。 */
    public final List<String> beamTags;

    public GearInMagicSettings() {
        hudEnabled = MagicSettings.getBoolean(CONFIG_SECTION, "hud_enabled");
        beamWidth = MagicSettings.getFloat(CONFIG_SECTION, "beam_width");
        beamRange = MagicSettings.getFloat(CONFIG_SECTION, "beam_range");
        beamDamage = MagicSettings.getFloat(CONFIG_SECTION, "beam_damage");
        beamEmp = MagicSettings.getFloat(CONFIG_SECTION, "beam_emp");
        beamFull = MagicSettings.getFloat(CONFIG_SECTION, "beam_full");
        beamFading = MagicSettings.getFloat(CONFIG_SECTION, "beam_fading");
        beamImpactSize = MagicSettings.getFloat(CONFIG_SECTION, "beam_impact_size");
        beamCoreColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_core");
        beamFringeColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_fringe");
        beamTags = MagicSettings.getList(CONFIG_SECTION, "beam_tags");
        LOG.info("GearIn magic settings: hud=" + hudEnabled
                + " width=" + beamWidth + " range=" + beamRange
                + " damage=" + beamDamage + " emp=" + beamEmp
                + " full=" + beamFull + " fading=" + beamFading
                + " impact=" + beamImpactSize
                + " core=(" + beamCoreColor.getRed() + "," + beamCoreColor.getGreen() + "," + beamCoreColor.getBlue() + ")"
                + " tags=" + beamTags);
    }
}
```

### GearInBeamCaster：伪光束发射（MagicFakeBeam）

职责：每 `BEAM_INTERVAL` 秒向最近可见敌舰发射一条伪光束——伤害在调用内瞬时结算，视觉时长独立（规则 4）；持有充能进度与发射计数，供状态条读取。

绑定：由接入类创建并注入引擎（`beam.init(engine)`），`advance(amount)` 由接入类在暂停守卫之后每帧转发；发射目标用 `AIUtils.getNearestEnemy` 并判空。

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

/** 伪光束（MagicFakeBeam）：每 BEAM_INTERVAL 秒向最近可见敌舰发射一条伪光束。
 * 伤害瞬时结算，持续视觉时间不等于持续伤害时间；光束不会被中途物体截断；
 * HIGH_EXPLOSIVE 对装甲全额伤害（原版有折减），本类用 ENERGY 规避。 */
public class GearInBeamCaster {
    private static final Logger LOG = Global.getLogger(GearInBeamCaster.class);
    private static final float BEAM_INTERVAL = 2f; // 光束发射间隔（秒）；伤害瞬时，视觉时长由 modSettings.json 控制

    private final GearInMagicSettings settings;
    private CombatEngineAPI engine;
    private float beamCharge; // 0..1：充能进度，HUD 状态条显示它
    private int beamCount;

    public GearInBeamCaster(GearInMagicSettings settings) {
        this.settings = settings;
    }

    /** 战斗 init 时注入引擎（由接线类调用）。 */
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    /** 每帧推进充能，满 1 发射并清零；只在非暂停推进（暂停守卫由接线类负责）。 */
    public void advance(float amount) {
        beamCharge += amount / BEAM_INTERVAL;
        if (beamCharge >= 1f) {
            beamCharge = 0f;
            fireBeam();
        }
    }

    public float getBeamCharge() { return beamCharge; }
    public int getBeamCount() { return beamCount; }

    /** 向最近可见敌舰发射一条伪光束：视觉立即生成，伤害在调用内瞬时结算。 */
    private void fireBeam() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        ShipAPI enemy = AIUtils.getNearestEnemy(player); // @Nullable：必须判空（S12-P02 同规）
        if (enemy == null) {
            LOG.info("GearIn magic beam: no enemy in sight");
            return;
        }
        Vector2f from = player.getLocation();
        float angle = VectorUtils.getAngle(from, enemy.getLocation());
        MagicFakeBeam.spawnFakeBeam(
                engine,
                from,
                settings.beamRange,
                angle,
                settings.beamWidth,
                settings.beamFull,
                settings.beamFading,
                settings.beamImpactSize,
                settings.beamCoreColor,
                settings.beamFringeColor,
                settings.beamDamage,
                DamageType.ENERGY,
                settings.beamEmp,
                player);
        beamCount++;
        LOG.info("GearIn magic beam: #" + beamCount + " damage=" + settings.beamDamage
                + " emp=" + settings.beamEmp + " width=" + settings.beamWidth
                + " range=" + settings.beamRange + " tags=" + settings.beamTags);
    }
}
```

### GearInHudBar：HUD 状态条（MagicUI）

职责：用固定 key `gear_in_beam_charge` 刷新玩家舰状态条——只注册数据，绘制由 MagicLib 的渲染链完成 （`MagicRenderPlugin` → `drawStatusBarMap`）；填充进度 = 光束充能，颜色复用光束颜色（改配置 → 光束与状态条一起变色）。

绑定：由接入类创建（持有光束发射器与设置的引用），`refresh(engine)` 由接入类在暂停守卫**之前**每帧调用（规则 10 的正面写法）。

```java
package data.scripts.gearin.lib;

import org.magiclib.util.MagicUI;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/** 战斗 HUD 状态条（MagicUI）：以固定 key 刷新玩家舰状态条。
 * 只注册数据，绘制由 MagicLib 的 MagicRenderPlugin（settings plugins 注册的
 * 战斗插件）每帧完成；状态条约 5 秒不被刷新就会被移除（含暂停时间），因此
 * 刷新调用必须放在暂停守卫之前。 */
public class GearInHudBar {

    /** 战斗 HUD 状态条 key：MagicLib 要求对本 MOD 唯一；换 key 会生成另一条状态条。 */
    private static final String HUD_BAR_KEY = "gear_in_beam_charge";

    private final GearInBeamCaster beam;
    private final GearInMagicSettings settings;

    public GearInHudBar(GearInBeamCaster beam, GearInMagicSettings settings) {
        this.beam = beam;
        this.settings = settings;
    }

    /** 刷新状态条：填充进度 = 光束充能，颜色复用光束颜色（改配置 → 光束与状态条一起变色）。 */
    public void refresh(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        MagicUI.drawInterfaceStatusBar(
                player,
                HUD_BAR_KEY,            // 固定 key：改变文本不会生成新条（无 key 重载用文本兼作 key）
                beam.getBeamCharge(),   // 0..1 的填充进度
                settings.beamCoreColor,   // 填充色；null 时用原版 HUD 绿
                settings.beamFringeColor, // 边框色；null 时用原版 HUD 绿
                0f,                     // 第二段填充（类硬幅能），0 即关闭
                "Beam charge",
                beam.getBeamCount());   // 右侧数字，0..999999
    }
}
```

### GearInCampaignTools：生涯工具消费（MagicCampaign）

职责：静态生涯工具消费面——`logCampaignInfo` 是查询面演示 （先判 `Global.getSector()`，战役场景输出 `no sector (not in campaign)`）；

`spawnDerelictExample` 是写侧参考写法（`createDerelict` 九参，`recoverable=true` 写打捞 special，判空 + warn 而非抛错）。

绑定：`logCampaignInfo` 由接入类 `init` 与 `GearInModPlugin.onGameLoad` 调用（生涯侧接入）； `spawnDerelictExample` 由生涯脚本（如星系生成器 `GearInSystemGenerator.generate` 末尾）调用，战斗插件不调用。

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicCampaign;
import org.magiclib.util.MagicVariables;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;

/** 生涯工具（MagicCampaign）：查询面与写侧的参考消费姿势。
 * 生涯工具以 Global.getSector() 为宿主，只能在生涯上下文调用，调用前先判空；
 * logCampaignInfo 是查询面演示（战役场景输出 "not in campaign"），
 * spawnDerelictExample 是写侧参考姿势（调用方是生涯脚本，战斗插件不调用）。 */
public class GearInCampaignTools {

    private static final Logger LOG = Global.getLogger(GearInCampaignTools.class);

    private GearInCampaignTools() {
        // 工具类：不实例化
    }

    /** 生涯上下文查询演示：只在 Global.getSector() 非 null（生涯启动/读档后）时输出统计。 */
    public static void logCampaignInfo() {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            LOG.info("GearIn magic campaign: no sector (not in campaign)");
            return;
        }
        LOG.info("GearIn magic campaign: sectorSize=" + MagicVariables.getSectorSize()
                + " systems=" + sector.getStarSystems().size()
                + " markets=" + sector.getEconomy().getMarketsCopy().size());
    }

    /** 生涯写侧参考姿势：在生涯脚本（如 S03-P03 的 GearInSystemGenerator 末尾）调用，
     * 把一艘残骸舰放到轨道上。只有生涯上下文有效（orbitCenter 必须已加入星区）；
     * 场景错误时写 warn 而不是抛错。 */
    public static void spawnDerelictExample(SectorEntityToken orbitCenter, String variantId) {
        if (Global.getSector() == null) {
            LOG.warn("GearIn magic campaign: createDerelict called outside campaign, skipped");
            return;
        }
        SectorEntityToken wreck = MagicCampaign.createDerelict(
                variantId,
                ShipRecoverySpecial.ShipCondition.AVERAGE,
                true,  // discoverable：发现时奖励探索 XP
                null,  // discoveryXp：null 使用默认
                true,  // recoverable：写入打捞 special（可回收舰船）
                orbitCenter,
                0f,    // orbitStartAngle
                200f,  // orbitRadius
                30f);  // orbitDays
        LOG.info("GearIn magic campaign: derelict " + wreck.getId()
                + " orbiting " + orbitCenter.getId());
    }
}
```

### GearInMagicWiring：薄入口类接入

职责：把设置、伪光束与 HUD 状态条三个工具族接进一场战斗——创建三个工具类、把引擎注入光束发射器、按帧转发 `advance` 并执行暂停守卫；生涯工具不在这里接入（调用方是生涯脚本）。

绑定：注册入口——由既有战斗插件在 `init()` 中 `engine.addPlugin(new GearInMagicWiring())` 注册（战斗插件模型）；

`init` 里同时把引擎注入光束发射器并调用 `GearInCampaignTools.logCampaignInfo()`； `advance` 中先刷新状态条（暂停守卫之前）、再执行暂停守卫、最后转发光束充能。

```java
package data.scripts.gearin.lib;

import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 薄载体接线类：把设置、伪光束与 HUD 状态条三个工具族接进一场战斗。
 * 由既有战斗插件在 init() 中 addPlugin 注册（S02-P01 模型），本身不含工具
 * 逻辑，只做接线：创建三个工具类、把引擎注入光束发射器、按帧转发 advance
 * 并执行暂停守卫。生涯工具不在这里接线（调用方是生涯脚本）。 */
public class GearInMagicWiring extends BaseEveryFrameCombatPlugin {

    private final GearInMagicSettings settings = new GearInMagicSettings();
    private final GearInBeamCaster beam = new GearInBeamCaster(settings);
    private final GearInHudBar hud = new GearInHudBar(beam, settings);

    private CombatEngineAPI engine;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        beam.init(engine);
        GearInCampaignTools.logCampaignInfo(); // 战役（mission）场景：sector 为 null → "not in campaign" 分支
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（S02-P01 同款姿势）
        }
        if (settings.hudEnabled) {
            hud.refresh(engine); // 放在暂停守卫之前：暂停期间也刷新，状态条才不会超时被移除
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）：光束与充能只在非暂停推进
        }
        beam.advance(amount);
    }
}
```

数据流完整流程：`GearInMagicSettings` 构造期读设置（设置链）；

`GearInBeamCaster` 每 2 秒向最近敌舰发射伪光束（FakeBeam 链，`full` / `fading` 来自设置——伤害瞬时、视觉时长独立，规则 4；`source` 传玩家舰）；

`GearInHudBar` 用固定 key 刷新状态条，填充进度 = 充能值、颜色复用光束颜色，刷新在暂停守卫之前（规则 10 的正面写法）；

`GearInCampaignTools.logCampaignInfo` 先判 `Global.getSector()`（规则 13）——战役场景输出 `no sector (not in campaign)`； `spawnDerelictExample` 是写侧参考写法，由生涯脚本调用，战斗插件本身不调用。

日志前缀 `GearIn magic ...` 不是 strings key，只进 `starsector.log`。

### 绑定与接入片段（工程片段——本页不改动所有者文件）

```java
    // 战斗侧：在测试战役的战斗入口中（或既有战斗插件 init() 里）追加：
    engine.addPlugin(new GearInMagicWiring());
```

```java
    // 生涯侧：在既有 MOD 主插件 GearInModPlugin 的 onGameLoad 内追加一行（新档与读档都会触发）
    @Override
    public void onGameLoad(boolean newGame) {
        GearInCampaignTools.logCampaignInfo(); // MagicLib 生涯工具查询面（内部先判 sector）
        // ... 既有回调内容 ...
    }
```

生涯写侧的接入写法（可选）：在星系生成器 `GearInSystemGenerator.generate` 末尾（新档执行一次）追加两行：

```java
SectorEntityToken prime = Global.getSector().getStarSystem("GearIn").getEntityById("GearIn_planet1");
GearInCampaignTools.spawnDerelictExample(prime, "hound_Standard");
```

`hound_Standard` 是原版装配链中的注册装配 id；`GearInModPlugin` 与 `GearInSystemGenerator` 属于既有示例（本页不改动），fixture 例不含以上接入片段（fixture 只含本页所有的五个类）。

### 构建

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/lib/` 下的五个类（可用 `jar tf` 核对）。

本页源码与 fixture 逐字一致（`tutorial-fixtures/units/S12-P04/` 下五个类），fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

以本页五个类 + `data/config/modSettings.json` + 两处接入片段（战斗 `addPlugin`、`GearInModPlugin.onGameLoad` 一行，可选加生成器残骸两行）为对象。

前置数据：既有累计工程 + `GearIn_test` 测试战役（S03-P01）；本机已安装 MagicLib 1.5.1（启动器勾选）。

**操作**

1. fixture 编译：在 Git Bash 执行 `gradle -p tutorial-fixtures compileS12p04Java --no-daemon`，确认 `BUILD SUCCESSFUL`（退出码 0）。
2. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`。
   - 核对产物：`jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/lib/GearInMagicWiring.class` 等五个类。
3. 设置与战役场景：进入 `GearIn_test` 一场战斗。
   - 核对 `starsector.log` 的 `GearIn magic settings: ...`（与 `modSettings.json` 一致）。
   - 核对 `GearIn magic campaign: no sector (not in campaign)`，无 `gear_in` 引起的 `ERROR`。
4. 光束与 HUD：观察每 2 秒一条蓝色光束射向最近敌舰（约 0.55 秒内消失）、命中光斑；HUD 出现 `Beam charge` 状态条，填充 0→1 循环、右侧数字递增；`GearIn magic beam: #N ...` 日志同步。
5. 暂停：按空格暂停 10 秒——无新光束日志，状态条保持可见（暂停中也刷新）；恢复后充能与发射继续。
6. 变更验证·设置：改 `beam_width` / `beam_color_core` / `beam_color_fringe` 后重启进战斗—— 光束粗细/颜色、状态条颜色、`settings` 日志同步变化；改回后恢复（改设置必须重启游戏）。
7. 变更验证·HUD 开关：`hud_enabled=false` 重启进战斗——状态条消失、光束照常；改回后恢复。
8. 敌全灭：清空敌方后输出 `GearIn magic beam: no enemy in sight`，无 `ERROR`。
9. 生涯查询：新开一局生涯或读档——`starsector.log` 出现 `GearIn magic campaign: sectorSize=... systems=... markets=...`。
10. 生涯写侧（可选）：接入生成器片段后新档——`GearIn` 星系 `GearIn Prime` 轨道出现 `hound` 残骸（可打捞回收），日志 `GearIn magic campaign: derelict ... orbiting GearIn_planet1`。
11. 故障注入·段名：`modSettings.json` 段名改为 `gear_industry` 后重启进战斗。
   - 11 条 warn + `settings` 行全缺省值（`hud=false`、`width=0.0`、`core=(255,0,0)`、`tags=[]`），状态条消失、光束变红变细。
   - 改回后恢复。
12. 故障注入·不刷新：注释接入类中的 `hud.refresh(...)` 调用后进战斗——状态条约 5 秒后消失；改回后恢复。

**通过信号**

操作 1–9 全部符合；操作 10–12 的注入与恢复都符合预期。

**清理**

取消勾选测试 MOD；恢复 `modSettings.json` 与代码为原值；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileS12p04Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/lib/GearInMagicWiring.class` 等五个类。
3. **战斗初始化**：进入 `GearIn_test` 测试战役一场战斗，`starsector.log` 出现：
```text
GearIn magic settings: hud=true width=12.0 range=900.0 damage=60.0 emp=25.0 full=0.15 fading=0.4 impact=40.0 core=(210,240,255) tags=[gear_in, demo] GearIn magic campaign: no sector (not in campaign)
```
`core=(210,240,255)` 与 `modSettings.json` 的 `"[210,240,255]"` 一致；战役场景下 `sector` 为 null → `not in campaign` 分支（规则 13 的正面）。没有 `gear_in` 引起的 `ERROR`。
4. **周期光束**：战斗（未暂停）中每 2 秒出现一行 `GearIn magic beam: #N damage=60.0 emp=25.0 width=12.0 range=900.0 tags=[gear_in, demo]`，`#N` 递增。
   - 画面上从玩家舰到最近敌舰出现一条蓝色（210,240,255 核心 / 60,120,255 边缘）光束，命中点有光斑。
   - 光束在约 0.55 秒内（0.15 全亮 + 0.4 淡出）消失。
5. **HUD 状态条**：HUD 上出现一条 `Beam charge` 状态条（左侧文字 + 右侧递增数字），填充从 0 平滑涨到 1 后归零（每次光束发射）；填充色与边框色与光束颜色一致。
6. **暂停行为**：按空格暂停——**没有**新的 `GearIn magic beam:` 日志（充能停摆），状态条**仍然可见** （示例在暂停时也刷新；与 LazyLib 主题示例的"暂停即停摆"姿态相反，这里是"暂停保持刷新"的刻意设计）；恢复后充能与发射继续。
7. **变更验证（设置）**：把 `modSettings.json` 的 `beam_width` 改为 `24.0`、`beam_color_core` 改为 `"[255,0,0]"`、`beam_color_fringe` 改为 `"[200,0,0]"`，重启游戏进战斗。
   - `settings` 日志行的 `width=24.0`、`core=(255,0,0)`，光束变粗变红，状态条填充/边框变红。
   - 改回后恢复。
   - 注意：设置只在 MagicLib 的 `onApplicationLoad` 读取，**必须重启游戏**（或 dev 模式 F8 重载 + 重进战斗）。
8. **变更验证（HUD 开关）**：把 `hud_enabled` 改为 `false` 重启进战斗——状态条消失，但光束照常发射（`hud_enabled` 只控制状态条，`fireBeam` 不受它影响）；改回后恢复。
9. **敌全灭**：清空敌方后 `GearIn magic beam: no enemy in sight`，无 `ERROR`；状态条仍在（填充照常循环）。
10. **生涯查询**：新开一局生涯或读档，`starsector.log` 出现一行 `GearIn magic campaign: sectorSize=<星区范围> systems=<星系数> markets=<市场数>`（数值随存档变化）。
   - 战役场景没有这行（只有 `no sector (not in campaign)`）。
11. **生涯写侧（可选接入后）**：接入生成器片段后新档——`GearIn` 星系 `GearIn Prime` 轨道上出现一艘 `hound` 残骸。
   - `starsector.log` 有 `GearIn magic campaign: derelict <id> orbiting GearIn_planet1`；靠近残骸可打捞回收。
12. **故障注入（段名）**：把 `modSettings.json` 的段名 `gear_in` 改成 `gear_industry` 后重启进战斗。
   - 日志出现 11 条 warn（每键一条 `unable to find <id> within gear_industry in modSettings.json`）。
   - `settings` 行输出全缺省值（`hud=false`、`width=0.0`、`core=(255,0,0)`、`tags=[]`），状态条消失（`hudEnabled=false`）、光束变红变细（width 0）。
   - 改回后恢复（规则 1/2 的注入验证）。
13. **故障注入（不刷新）**：注释接入类中的 `hud.refresh(...)` 调用后进战斗——状态条出现后约 5 秒消失（`drawStatusBarMap` 超时移除），恢复调用后条回来（规则 10 的注入验证）。

静态自查：`modSettings.json` 为 UTF-8 无 BOM；

段名 `gear_in` 与 `mod_info.json` 的 `id` 一致；

颜色键为 `"[r,g,b]"` 字符串；

五个示例类与 fixture 逐字一致（包括 import 顺序与注释）；

没有把工作单元编号写进任何包名、类名、资源名或字符串 key：
- 包 `data.scripts.gearin.lib`
- 类 `GearInMagicWiring` / `GearInMagicSettings` / `GearInBeamCaster` / `GearInHudBar` / `GearInCampaignTools`
- 段 `gear_in`、状态条 key `gear_in_beam_charge`、日志前缀 `GearIn magic ...`

除本页目录与 fixture 目录外没有改动任何共享文件。

## 实例驱动故障分析

1. **设置没生效，但没有任何报错**。
  - 触发：`modSettings.json` 的段名或键名拼错。
  - 可观察：功能照常"运行"，但参数是缺省值（光束红色、宽度 0、`hud=false` 状态条消失）。
  - 可观察：`starsector.log` 有 warn 行 `unable to find <id> within <modId> in modSettings.json`（段名错时是 `unable to find <modId> in modSettings.json`）。
  - 原因：`MagicSettings` 缺键不抛异常、返回类型缺省值（规则 2）。
  - 修正：先看 warn 行定位段/键，再对照缺省值表确认哪些参数在走缺省。
2. **颜色配置"永远不生效"，读取结果恒为红**。
  - 触发：颜色值写成 JSON 数字数组（`[210,240,255]` 不带引号）或写成多个键。
  - 可观察：`settings` 日志行 `core=(255,0,0)`，warn 行 `unable to read content of gear_in in modSettings.json` 或 `unable to find ...`。
  - 原因：`getColorRGB` 用 `getString` 读、`toColor3` 按 `"[r,g,b]"` 字符串解析（规则 3，`org.magiclib.util.MagicSettings`）。
  - 修正：写成带引号的 `"[210,240,255]"`。
3. **光束视觉还在，但敌人已经吃完了全部伤害**。
  - 触发：把伪光束当持续伤害光束用（长 `full`/`fading`）。
  - 可观察：0.5 秒的光束"持续期"里伤害在发射瞬间全部结算，后续帧没有伤害。
  - 原因：`spawnFakeBeam` 在调用内 `applyDamage` 一次（规则 4；`org.magiclib.util.MagicFakeBeam`）。
  - 修正：伪光束只用于 burst / FX；持续伤害用原版真光束（`BeamAPI`）。
4. **HE 伪光束打装甲"太猛"**。
  - 触发：`DamageType.HIGH_EXPLOSIVE` + 高伤害。
  - 可观察：重甲敌舰被快速剥甲，装甲伤害明显高于同标称原版光束。
  - 原因：伪光束对装甲全额伤害、无原版折减（规则 6，官方 javadoc 警告）。
  - 修正：用 ENERGY / KINETIC，或用 HE 时压低标称伤害。
5. **光束被穿过的舰船"截断"，与预期不符**。
  - 触发：长视觉时长光束 + 敌我舰船横穿。
  - 可观察：光束视觉不中断，穿过物体不产生二次命中。
  - 原因：伪光束是单段直线 sprite，没有截断语义（规则 5）。
  - 修正：保持短视觉时长；需要可截断的持续光束用真光束。
6. **HUD 状态条过一会儿消失，恢复刷新才回来**。
  - 触发：刷新调用放在暂停守卫之后，或某段代码停止调用 `drawInterfaceStatusBar`。
  - 可观察：暂停超过约 5 秒（游戏时间含暂停）条消失；或离开某状态后条消失。
  - 原因：`drawStatusBarMap` 按 `getTotalElapsedTime(true) - getLastRefreshed() >= 5f` 移除 （`org.magiclib.util.MagicUI` 的移除判据，规则 10）。
  - 修正：每帧（含暂停）刷新；刷新放在暂停守卫之前（示例写法）。
7. **状态条 key 撞车，两条数据互相覆盖**。
  - 触发：直接抄别的 MOD 示例的 key，或用了通用词（如 `"bar"`）。
  - 可观察：两条状态条共用同一 key 时后写覆盖先写，数字/填充"打架"或其中一条消失。
  - 原因：`statusBars` 是全局静态表、按 key 去重。
  - 修正：key 用本 MOD 专属前缀（`gear_in_beam_charge`）。
8. **给非玩家舰传 ship，状态条不出现**。
  - 触发：想给敌方舰或友军舰画状态条。
  - 可观察：什么也不画，无报错。
  - 原因：绘制入口首部 `ship != getPlayerShip()` 直接 return（规则 11；`org.magiclib.util.MagicUI` 的入口守卫）。
  - 修正：只对玩家舰调用；`drawHUDStatusBar` 的 offset 参数用于锚定其他对象的显示。
9. **战役/主菜单调用生涯工具 NPE**。
  - 触发：在 `onApplicationLoad`、战役战斗插件里调 `findSuitableTarget` / `createDerelict`。
  - 可观察：`ERROR` + NPE 堆栈（第一行 `Global.getSector()` 为 null）。
  - 原因：生涯工具以 sector 为所属对象（规则 13；`Global` 生命周期）。
  - 修正：调用前判 `Global.getSector() == null`；写侧入口只在生涯脚本调用。
10. **残骸"不可打捞"预期落空，或反过来**。
  - 触发：十参重载的 `isRecoverable=false` 与九参的 `recoverable=false` 混用。
  - 可观察：`recoverable=false` 只影响打捞 special 写入；`isRecoverable=false` 追加 `Tags.UNRECOVERABLE` （规则 14，`org.magiclib.util.MagicCampaign`）。
  - 修正：两个布尔分开使用，语义见速查表。
11. **loadVariant 返回 null 导致 NPE**。
  - 触发：`.variant` 文件 JSON 损坏/缺字段，返回值直接 `.getHullId()`。
  - 可观察：NPE 堆栈，日志只有 warn 行 `could not load ship variant at <path>`。
  - 原因：`loadVariant` 全解析包 try/catch、失败 warn + null（规则 15，warn + null 分支）。
  - 修正：判空 + warn。
12. **光束有伤害没有视觉**。
  - 触发：MagicLib 的 `ML_fakeBeamPlugin` settings 键被覆盖/缺失，`MagicFakeBeamPlugin` 未实例化。
  - 可观察：敌人掉血、`GearIn magic beam:` 日志正常，画面上没有光束。
  - 原因：视觉渲染在 `MagicFakeBeamPlugin.renderInWorldCoords`，它由 MagicLib 的 settings `plugins` 段注册（规则 7）。
  - 补充：`spawnFakeBeam` 先结算伤害再 `addBeam`（`org.magiclib.util.MagicFakeBeam`）。
  - 修正：不要动 MagicLib 的 settings 键；核对 `ML_fakeBeamPlugin` 存在。

## 总结

本页把 MagicLib 1.5.1 的四族工具走了一个完整流程：`MagicSettings` 的合并加载链、缺省值表与三类 warn 文案构成"设置没生效"的排查入口；

`MagicFakeBeam` 的瞬时结算、不可截断与 HE 全额伤害三条边界，加上碰撞助手的 null 约定，定义了伪光束能做什么、不能做什么；

`MagicUI` 状态条的"注册—绘制"分离、key 唯一与约 5 秒刷新（含暂停）纪律，说明 HUD 数据谁注册、谁绘制、何时被移除；

`MagicCampaign` 的生涯上下文要求与写侧双布尔 / null 约定，划定了生涯工具的调用边界。

示例以五个小类分别承载四族与接入，`starsector.log`、战斗 HUD、战斗画面、生涯存档四个可观察位置验证调用真的发生。

在逻辑上，本页有两条延伸线：设置链上，另一套设置系统（LunaLib 的读取、保存与通知链）将与本页的 `MagicSettings` 形成对照—— 两套链的合并入口与 ID 归属不同，`gear_in` 段已在本页定义，后续设置主题不得重复定义该段；

战斗效果链上，伪光束与真光束（`BeamAPI`）的"瞬时 vs 持续"边界、生涯工具与生涯对象链的衔接，是后续武器与生涯内容设计的直接基础。

## 建议参考项

- `org.magiclib.util.MagicSettings`：`modSettings.json` 合并读取与类型化取值、缺省值表。
- `org.magiclib.util.MagicFakeBeam`：瞬时伤害伪光束（`spawnFakeBeam` / `spawnAdvancedFakeBeam`）与碰撞助手。
- `org.magiclib.plugins.MagicFakeBeamPlugin`：伪光束视觉渲染插件（由 MagicLib 注册，使用方不得重复注册）。
- `org.magiclib.util.MagicUI`：战斗 HUD 状态条、系统条/框与文本绘制入口。
- `org.magiclib.util.MagicUIInternal`：状态条实际绘制链（`callRenderMethods`）。
- `org.magiclib.plugins.MagicRenderPlugin`：每帧驱动 HUD 绘制的战斗插件。
- `org.magiclib.util.MagicCampaign`：生涯工具族（残骸 / 碎片场 / 跳跃点 / 市场 / 人物 / 舰队 / 目标选取）。
- `org.magiclib.util.MagicVariables`：`MAGICLIB_ID` 常量、星区尺寸缓存与 dev 模式标志。
- `org.magiclib.plugins.MagicTrailPlugin`：尾迹渲染（纹理 2 的幂/垂直要求与 `getUniqueID` 复用纪律）。
- `org.magiclib.Magic_modPlugin`：`onApplicationLoad` 读取设置、F8 重载的时机。
- `com.fs.starfarer.api.SettingsAPI`：`getMergedJSONForMod` 跨 MOD 合并读取、`isDevMode`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`applyDamage` / `addPlugin` / `isUIShowingHUD` 等战斗入口。
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial`：`ShipCondition` 枚举（残骸状态）。
- `data/config/modSettings.json`：本页所有，`gear_in` 段（设置注册入口）。
- `mods/MagicLib/data/config/sample_modSettings.json`：MagicLib 给使用方 MOD 的设置样例与注释。
- `Starsector/starsector-core/starsector.log`：运行日志，三类 warn 文案与示例日志行的观察位置。
