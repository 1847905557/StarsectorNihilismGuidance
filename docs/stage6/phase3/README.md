# Global 静态访问器与全局服务

本页把 `Global`（`com.fs.starfarer.api.Global`）讲透：RC8 的完整访问器清单、各 getter 在"资源加载期 / 主菜单 / 生涯 / 战斗"四阶段的可用性与 null 语义，以及日志与声音入口。`Global` 是进程级静态中枢，几乎所有 MOD 入口都会用到它；modPlugin 生命周期主题已消费 `getSector()` / `getSettings()` / `getLogger()` 的结论，本页给出它们的精确约定。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 背出 RC8 `Global` 的完整访问器清单（七个 getter、两个 `@Deprecated` 别名、两个静态标志、一组 setter），并说出每个成员的返回类型与用途；
- 按"资源加载期 / 主菜单 / 生涯 / 战斗"四个阶段说出各 getter 的 null 语义，并解释为什么 RC8 中"用 `getSector()` / `getCombatEngine()` 是否为 null 判断当前阶段"不可靠；
- 用 `Global.getCurrentState()`（返回 `GameState`）正确判断当前阶段，并说出 `LOADING_SAVE` 标志的置位窗口；
- 用 `Global.getLogger(Class)` 记录日志并说出输出位置；
- 用 `Global.getSoundPlayer()` 播放 UI 音效、位置音效与循环音效，复述声道约定、`loc`/`vel` 非 null 约定与循环"每帧续期"约定；
- 从症状定位六类典型故障：加载期空指针、战斗外引擎误用、跨战斗缓存引用、退出主菜单后使用旧生涯引用、`vel` 为 null 的空指针、未注册 sound ID 的运行期异常。

## 前置知识

开始本节前，您应已掌握以下基础：

- MOD 目录结构与 `starsector.log`（S01-P01）。
- 战斗插件与每帧回调模型——战斗域的代码入口（S02-P01）。
- 生涯概念：`SectorAPI` 属于生涯域；`GearIn_test` 测试战役（S03-P01）。
- 三个 GearIn 声音 ID 的资产规格、注册层与完整使用方讲解（S05-P01 / S05-P02 / S05-P03）。
- 编译 JAR 链路——本页示例编入同一个 `jars\GearIndustry.jar`（S06-P01）。
- `modPlugin` 生命周期回调与 `onApplicationLoad` 禁令（S06-P02）；本页从 `Global` 视角给出该禁令的精确 null 语义——"此刻 sector 尚未建立"到底意味着什么。

## 文件与 API 链路

`Global` 是进程级静态门面，每个 getter 是一条链路的入口：

| `Global` 入口 | 返回 | 链路下游（本教程页面）|
|---|---|---|
| `getSettings()` | `SettingsAPI` | 设置、规则与静态数据（S06-P04 精确聚焦；`S11` 系列消费规则）|
| `getSector()` | `SectorAPI` | 生涯域（S03-P01、`S08` 系列）|
| `getCombatEngine()` | `CombatEngineAPI` | 战斗域（S02-P01 起的战斗插件）|
| `getSoundPlayer()` | `SoundPlayerAPI` | 声音注册链（S05-P02）与使用方（S05-P03）|
| `getFactory()` | `FactoryAPI` | 生涯对象工厂（本页）|
| `getLogger(Class)` | log4j `Logger` | `starsector.log`（S01-P01）|
| `getCurrentState()` | `GameState` | 阶段判断（本页）|

Global 的引用由内核在固定的时机登记（调用点详见"生命周期与 null 语义"）：

```text
启动器.launch                   → setSettings(启动器设置.getSettingsAPI())
                                     （游戏循环启动前，任何 MOD 入口都非 null）
内核战斗入口.main               → setSoundPlayer(new 声音播放实现())
                                     （AppDriver 各状态注册之前）
生涯引擎.setInstance            → setSector(instance) + setFactory(instance)
                                     （新档/读档时；主菜单首次初始化也会临时创建一次）
战斗引擎.getInstance            → setCombatEngine(new 战斗引擎())   （用到时才创建）
战斗引擎.destroyInstance        → setCombatEngine(null)                  （销毁）
游戏管理器.loadGame             → Global.LOADING_SAVE = true / false     （读档窗口）
```

## 访问器完整清单（0.98a-RC8）

以 `com.fs.starfarer.api.Global` 为准，公开成员共 16 项（getter 7 个、`@Deprecated` 别名 2 个、静态标志 2 个、setter 5 个；下表把 setter 组合并为一行，共 12 行）：

| 成员 | 类型 | 语义 |
|---|---|---|
| `getLogger(Class c)` | `Logger` | 按类名取 log4j 日志器（`org.apache.log4j.Logger`），见"Logger"节 |
| `getSettings()` | `SettingsAPI` | 设置、规则、静态数据入口 |
| `getSoundPlayer()` | `SoundPlayerAPI` | 声音播放入口，见"SoundPlayerAPI"节 |
| `getSector()` | `SectorAPI` | 当前生涯引擎 |
| `getCombatEngine()` | `CombatEngineAPI` | 战斗引擎 |
| `getFactory()` | `FactoryAPI` | 生涯对象工厂；API javadoc 原文："Should only be used in the campaign." |
| `getCurrentState()` | `GameState` | 当前阶段：`TITLE` / `CAMPAIGN` / `COMBAT`，其余状态返回 `null` |
| `getSettingsAPI()` | `SettingsAPI` | `@Deprecated` 旧名，等价 `getSettings()` |
| `getSectorAPI()` | `SectorAPI` | `@Deprecated` 旧名，等价 `getSector()` |
| `LOADING_SAVE` | `boolean` | 读档期间为 `true`（静态字段，见生命周期节）|
| `CODEX_TOOLTIP_MODE` | `boolean` | Codex 提示模式标志（原版内部与存档读取使用，MOD 不写它）|
| setter 组（`setSettings` / `setSoundPlayer` / `setSector` / `setCombatEngine` / `setFactory`）| `void` | 内核生命周期专用，MOD **禁止**调用（见下）|

两个注意点：

1. RC8 的 `Global` **没有** `getModManager()`、`getEngine()`、`getRules()` 之类的"便利总线"——规则、战斗设置、经济等全部从 `getSettings()` 或 `getSector()` 往下走（settings 的精确聚焦与规则消费在各自主题展开）。缺 getter 时先想"这个对象属于哪个域"，再决定从 `settings`、`sector` 还是 `combatEngine` 取。
2. 旧教程曾建议"`getFactory()` 可以无视"。RC8 中 `FactoryAPI` 是创建生涯对象的正式途径（`createFleetMember`、`createCargo`、`createPerson`、`createMemory` 等），成熟实例确实在用（赏金奖励与剧情事件等场景）；它只在生涯域可用（javadoc 原文），战斗域不需要它。

## 生命周期与 null 语义（0.98a-RC8）

四个阶段的可用性一览（★ = 返回 `null`，● = 非 null）：

| 阶段（此时 `getCurrentState()`）| `getSettings` | `getSoundPlayer` | `getSector` | `getFactory` | `getCombatEngine` |
|---|---|---|---|---|---|
| 资源加载期（`null`，含 `onApplicationLoad`）| ● | ● | ★ | ★ | ★ |
| 主菜单（`TITLE`）| ● | ● | ●（弃用引用，见规则 2）| ●（弃用引用）| ●（空引擎，见规则 3）|
| 生涯（`CAMPAIGN`）| ● | ● | ● 当前生涯引擎 | ● 可用 | ●（空引擎）|
| 战斗（`COMBAT`）| ● | ● | ● 当前生涯引擎 | ● 可用 | ● 当前战斗引擎 |

以下每条都是 RC8 现状：

1. **`getSettings()` 与 `getSoundPlayer()` 在游戏循环开始前就已就位**：内核在游戏循环启动前完成 `Global.setSettings(...)`，游戏启动入口再在注册各阶段状态之前完成 `Global.setSoundPlayer(...)`。因此任何 MOD 入口——包括资源加载期的 `onApplicationLoad`——拿到两者都**非 null**。
2. **`getSector()` / `getFactory()` 只在第一个生涯引擎创建之前为 null**，即资源加载期（`onApplicationLoad` 阶段，即 modPlugin 生命周期禁令所辖的窗口）；一旦创建，`Global` 的引用**只会被替换、不会被置回 null**。
   - 退出生涯回主菜单并不清空 `Global`：`Global` 对生涯引擎的引用在退出后仍然保留（不会置回 null），`getSector()` 仍返回旧对象。
   - 可观察结果：退出到主菜单后 `getSector()` 仍返回**旧生涯引擎对象（非 null）**，其数据已随存档关闭而失效。
   - 因此"是否在生涯中"**必须**用 `Global.getCurrentState() == GameState.CAMPAIGN` 判断，不能依赖 `getSector() == null`。
3. **`getCombatEngine()` 的战斗外取值不是 null，而是空引擎**：战斗引擎的首次创建发生在主菜单初始化阶段——首次初始化生涯引擎时会一并创建；每场战斗结束后，内核先销毁旧引擎（用 `Global.setCombatEngine(null)` 清空引用）**再立即重建**一个新引擎。
   - 可观察结果：战斗外 `getCombatEngine()` 非 null 而是刚重建的空引擎；战斗结束后缓存的旧引用是已清空的死对象（`getShips()` 为空；`getPlayerShip()` 也不是 null——RC8 中它**永不返回 null**，`playerShip` 未设置时延迟创建"Command Shuttle"假舰）。
   - 因此：**禁止**跨战斗缓存引擎引用，每次需要时现取；"是否在战斗中"用 `getCurrentState() == GameState.COMBAT` 判断；"用户是否已部署"**禁止**用 `getPlayerShip() == null` 判断（战斗内该表达式恒为 `false`，依赖它的分支永远不执行、静默失效），应读 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()` 是否非空（判据细则见战斗域部署判断主题）。
4. **`getCurrentState()` 由当前所处阶段实时计算**：处于战斗状态 → `COMBAT`，处于生涯状态 → `CAMPAIGN`，处于主菜单 → `TITLE`，其余状态（资源加载等）→ `null`。它是阶段判断的唯一可靠入口。

::: info 为什么 `getCurrentState()` 返回 null 也是有效信号？

`GameState` 枚举只有三个可枚举值（`TITLE` / `CAMPAIGN` / `COMBAT`），资源加载期、MOD 加载期都落在 null 区间—— 返回 null 不是"出错"，而是"阶段不在可枚举状态内"。判断逻辑把 null 与三个枚举值并列处理，才能覆盖全部阶段。

:::

5. **`LOADING_SAVE` 的置位窗口**：读档流程开始时置 `true`，读档流程结束置 `false`（错误路径与 finally 都会复位）；`onEnabled` / `onGameLoad` 回调位于该窗口内，因此这两个回调里 `LOADING_SAVE == true`。常用于跳过读档期间不合适的 UI/动画逻辑。
6. **setter 禁令**：五个 setter 的唯一调用点都在内核生命周期内部（游戏启动、生涯引擎初始化、战斗引擎创建与销毁），MOD 代码没有任何正当调用场景，**禁止**调用。可观察结果：手动改写 Global 引用后，所有依赖 `Global` 的代码（包括原版）读到与引擎实际状态不符的对象—— 例如战斗外 `setCombatEngine(...)` 后 `getCombatEngine()` 指向一个不被推进的空壳，行为未定义。

::: caution 本页三条必须遵守的规则

1. 阶段判断**必须**用 `Global.getCurrentState()`，**禁止**依赖 `getSector()` / `getCombatEngine()` 是否为 null。
2. **禁止**跨战斗、跨存档缓存 `Global` 返回的引用，每次需要时现取。
3. **禁止**调用 `Global` 的五个 setter——内核生命周期专用，调用后全局引用与引擎实际状态脱钩。

:::

## Logger：getLogger

`Global.getLogger(Class)` 是 `Global` 里最"开箱即用"的成员：任何时间、任何地点，传入类即返回该类的 log4j 日志器 （`Logger.getLogger(c)` 按类名建 category，同一类名同一日志器）。

```java
// 推荐：类内缓存一份
private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

// 或按需直接调用
Global.getLogger(GearInGlobalAccess.class).info("任意内容");
```

- 输出位置：`Starsector\starsector-core\starsector.log`。
- 级别：`debug` / `info` / `warn` / `error`；生命周期与故障排查日志用 `info`，可恢复的异常情况用 `warn`/`error`。
- 常用形态：入口回调打一行（同 modPlugin 生命周期示例）、数值检查、故障注入验证。

## SoundPlayerAPI：getSoundPlayer()

`Global.getSoundPlayer()` 在**任何阶段都非 null**（见生命周期规则 1），返回 `SoundPlayerAPI`。三条核心方法（完整使用方族谱在声音消费主题，本页只给约定与可观察结果）：

| 方法 | 用途 | 约定（违反后的可观察结果）|
|---|---|---|
| `playUISound(String id, float pitch, float volume)` | 一次性 UI 音效，无播放点 | 素材**应为双声道**（API 注释原文 "UI sound files should be stereo"）；双声道素材用 `playSound` 播放时在引擎内无法正确播放（API 注释原文 "Sound file must be *mono* for it to be properly played within the engine"）|
| `playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel)` | 位置音效，在 `loc` 处播放并按方位与距离计算 | 素材**必须为 mono**（同上前提）；**`loc` 与 `vel` 都必须非 null**——播放声音时引擎直接解引用 `loc.x` / `vel.x`，传 null 在调用点抛 `NullPointerException` |
| `playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel)` | 循环音效 | **必须每帧调用，否则循环淡出**（API 注释原文 "Must be called every frame or the loop will fade out"）；`playingEntity` **必填**——用于区分同一实体的循环（同实体同循环只播一个）；素材建议 mono；调用方负责在每帧回调里续期（完整讲解见声音消费主题）|

其他常用成员（约定均出自 `SoundPlayerAPI` javadoc）：

- `playCustomMusic(int fadeOutIfAny, int fadeIn, String musicSetId)`：切换音乐集；**淡入淡出秒数必须是整数**（javadoc 原文 "have to be whole numbers"），`musicSetId` 传 `null` 停止当前曲且不换新曲。
- `getCurrentMusicId()`：返回当前曲目 ID；无播放时返回字符串 `"nothing"`（javadoc 原文）。
- `restartCurrentMusic()` / `getCurrentMusicId()` / `playCustomMusic` / `setSuspendDefaultMusicPlayback`：javadoc 标注线程安全，可从非主循环线程调用。

未注册 ID 的行为：引用未注册的 sound ID 时，播放调用抛 `RuntimeException`，消息为 `Sound set with id [...] not found`——这是运行期异常，在调用点抛出（声音消费主题的故障分析有完整定位流程）。

注意声音播放实现对 `null` ID 是宽容的（`playUISound`/`playSound` 返回 `null`、`playLoop` 忽略），但这不是可用性，ID 拼错/未注册照样抛异常。

::: warning 声音入口的两个经典坑

`playSound` 的 `loc` / `vel` 传 null 会在调用点直接抛 `NullPointerException`；声道选错不会报错—— 双声道素材用 `playSound` 播放时定位与衰减错误、单声道素材用 `playUISound` 表现异常，且都难以从日志定位。

:::

## 完整最小例

在 MOD 工程布局与 modPlugin 生命周期类 `GearInModPlugin` 之上新增两个工具类文件（不新增任何资源、不新增任何注册项）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin      # 既有 modPlugin 生命周期类（S06-P02）；本页只在其回调里调用示例方法
│       ├── GearInGlobalAccess   # 本页新增：访问器观测与阶段判断
│       └── GearInSoundPlayer    # 本页新增：声音播放入口（三契约）
└── jars\
```

### GearInGlobalAccess

职责：Global 静态访问器观测与阶段判断工具——`isInCombat()` 用 `getCurrentState()` 判战斗；`logAccessorState()` 巡览四阶段访问器可用性，并对资源加载期的 null 入口告警。

绑定位置：静态工具类、无注册项；调用点为 `GearInModPlugin` 的两个生命周期回调（见下方调用点片段）。

```java
package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;

/**
 * 角色：Global 静态访问器观测与阶段判断工具。
 *
 * 全部方法是静态工具，可从任何已注册的脚本/插件调用：
 * - 资源加载期（S06-P02 的 onApplicationLoad）：getSector()/getCombatEngine() 为 null，
 *   只能读 getSettings()/getSoundPlayer()/getLogger()；
 * - 生涯回调与生涯脚本：getSector() 可用；
 * - 战斗插件：getCombatEngine() 是当前战斗引擎，必须每帧现取，不得跨战斗缓存。
 *
 * 声音播放入口见 GearInSoundPlayer（声音 ID 由 S05-P01 声明、S05-P02 注册，本单元只消费）。
 */
public class GearInGlobalAccess {

    /** 日志器：进程级，任何阶段可用（org.apache.log4j.Logger，输出到 starsector.log）。 */
    private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

    private GearInGlobalAccess() {
        // 工具类：不实例化
    }

    /** 当前是否处于战斗：必须用 getCurrentState() 判断，不能依赖 getCombatEngine() 是否为 null。 */
    public static boolean isInCombat() {
        return Global.getCurrentState() == GameState.COMBAT;
    }

    /** 访问器巡览：记录当前阶段与各入口的可用性（注意资源加载期 sector/engine 为 null）。 */
    public static void logAccessorState() {
        GameState state = Global.getCurrentState();
        SectorAPI sector = Global.getSector();
        CombatEngineAPI engine = Global.getCombatEngine();
        LOG.info("GearIn state=" + state
                + " settings=" + (Global.getSettings() != null)
                + " soundPlayer=" + (Global.getSoundPlayer() != null)
                + " sector=" + (sector != null)
                + " engine=" + (engine != null)
                + " loadingSave=" + Global.LOADING_SAVE);
        if (sector == null) {
            LOG.warn("GearIn getSector() is null: 资源加载期访问生涯对象会空指针");
        }
        if (engine == null) {
            LOG.warn("GearIn getCombatEngine() is null: 资源加载期访问战斗对象会空指针");
        }
    }
}
```

对应关系：

- `Global.getLogger(GearInGlobalAccess.class)`：类内缓存一份日志器（见"Logger"节）。
- `isInCombat()`：阶段判断唯一可靠做法——`getCurrentState() == GameState.COMBAT`，不依赖 `getCombatEngine()` 是否为 null（生命周期规则 3）。
- `logAccessorState()`：把生命周期表变成日志；`sector == null` / `engine == null` 只在资源加载期出现（规则 2、3）。
- `Global.LOADING_SAVE`：读档回调窗口内为 `true`（规则 5）。

### GearInSoundPlayer

职责：SoundPlayerAPI 声音播放入口，覆盖 UI（`playUISound`）/ 位置（`playPositionalSound`）/ 循环（`playLoopSound`）三约定，并持有两个声音 ID 常量。

绑定位置：静态工具类、无注册项；调用点示例为 `GearInFrameLogger.advance()` 每帧续期循环音（见下方调用点片段）。

```java
package data.scripts.gearin;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;

/**
 * 角色：SoundPlayerAPI 声音播放入口（UI / 位置 / 循环三契约）。
 *
 * 全部方法是静态工具，可从任何已注册的脚本/插件调用；getSoundPlayer() 在
 * 任何阶段都非 null（Global 生命周期规则 1），因此无需判空。
 *
 * 声音 ID GearIn_system_loop / GearIn_weapon_fire 由 S05-P01 声明、S05-P02 注册，
 * 本单元只消费（完整消费者族谱见 S05-P03）。
 */
public class GearInSoundPlayer {

    public static final String SOUND_ID_SYSTEM_LOOP = "GearIn_system_loop";
    public static final String SOUND_ID_WEAPON_FIRE = "GearIn_weapon_fire";

    private GearInSoundPlayer() {
        // 工具类：不实例化
    }

    /** UI 音效：素材应为双声道（API 注释 "UI sound files should be stereo"）。 */
    public static void playUISound(float pitch, float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_WEAPON_FIRE, pitch, volume);
    }

    /** 位置音效：素材必须为 mono；loc 与 vel 都必须非 null，否则在调用点抛 NPE。 */
    public static void playPositionalSound(CombatEntityAPI source) {
        Vector2f loc = source.getLocation();
        Vector2f vel = new Vector2f(source.getVelocity()); // 复制，避免被引擎后续修改
        Global.getSoundPlayer().playSound(SOUND_ID_WEAPON_FIRE, 1f, 1f, loc, vel);
    }

    /** 循环音效：必须每帧调用否则淡出；playingEntity 必填，同实体同循环只播一个。 */
    public static void playLoopSound(CombatEngineAPI engine, CombatEntityAPI source) {
        Global.getSoundPlayer().playLoop(
                SOUND_ID_SYSTEM_LOOP, source, 1f, 1f, source.getLocation(), source.getVelocity());
    }
}
```

对应关系：

- `SOUND_ID_SYSTEM_LOOP` / `SOUND_ID_WEAPON_FIRE`：复用既有声音注册的 ID（声音资产与注册主题），不新增 ID。
- `playUISound` / `playPositionalSound` / `playLoopSound`：对应 SoundPlayerAPI 三约定（见上表）；`vel` 用拷贝构造避免引用被引擎后续改写； `playLoopSound` 保留引擎参数以承载"战斗插件 advance 上下文"的调用形态。

调用点（工程片段——方法所在类由既有章节持有，本页只演示调用，不改动那些类）：

```java
    // GearInModPlugin（modPlugin 生命周期类）内新增两行调用
    @Override
    public void onApplicationLoad() throws Exception {
        GearInGlobalAccess.logAccessorState(); // 预期：state=null, sector=false, engine=false
        // ... 原有版本检查
    }

    @Override
    public void onGameLoad(boolean newGame) {
        GearInGlobalAccess.logAccessorState(); // 预期：state=CAMPAIGN, sector=true, engine=true, loadingSave=true
        // ... 原有 CLASS_MARK 门
    }
```

```java
    // GearInFrameLogger（战斗每帧日志类）的 advance() 内加一段（沿用每帧回调模型）
    // 战斗内 getPlayerShip() 永不返回 null（RC8 延迟重建"Command Shuttle"假舰），无需判空
    if (GearInGlobalAccess.isInCombat()) {
        GearInSoundPlayer.playLoopSound(engine, engine.getPlayerShip());
    }
```

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用既有编译链路）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/GearInGlobalAccess.class` 与 `data/scripts/gearin/GearInSoundPlayer.class`（可用 `jar tf` 核对）。

两个代码块与 fixture 逐字一致（fixture 位于 `tutorial-fixtures\units\S06-P03\src\main\java\data\scripts\gearin\`），编译验收见"预期结果"。

### 验证方案（基于本例）

前置数据：

- MOD 工程布局与编译链路（S06-P01）。
- `GearInModPlugin` 生命周期类与持久数据门（S06-P02），并已接入本页两个调用点片段。
- `GearInGlobalAccess` / `GearInSoundPlayer` 两个工具类（本页，代码见上）。
- 声音资产与注册（S05-P01 / S05-P02）：`GearIn_system_loop`、`GearIn_weapon_fire`。
- `GearIn_test` 测试战役（S03-P01）。

操作步骤：

1. **启动（资源加载期）**：勾选"齿轮工业"启动。进入主菜单前 `starsector.log` 出现一行

```text
GearIn state=null settings=true soundPlayer=true sector=false engine=false loadingSave=false
```

并紧接两条 `GearIn getSector() is null: 资源加载期访问生涯对象会空指针` / `GearIn getCombatEngine() is null: 资源加载期访问战斗对象会空指针` 的 `WARN` （只有这一阶段才有这两条警告）；启动正常完成，没有 `ERROR`。
2. **新建生涯 / 读档（生涯回调）**：新建生涯，`onGameLoad` 回调日志出现

```text
GearIn state=CAMPAIGN settings=true soundPlayer=true sector=true engine=true loadingSave=true
```

（读档与新建都经过 `onGameLoad`；`loadingSave=true` 证明回调位于读档窗口内）且没有步骤 1 的两条 `WARN`；读档重复确认。
3. **战斗**：进入 `GearIn_test` 测试战役的一场战斗，日志出现 `GearIn state=COMBAT ...`；若已接入 `playLoopSound` 调用点，战斗内持续听到 `GearIn_system_loop` 循环音，战斗结束（回到生涯）后循环淡出停止，日志回到 `state=CAMPAIGN`。
4. **退出到主菜单**：生涯中退出到主菜单，日志出现 `GearIn state=TITLE ...`，且 `sector=true` 仍成立——`getSector()` 返回的是已弃用的旧生涯引擎（非 null），与 `getCurrentState() == TITLE` 一起确认阶段，这正是"不能靠 null 判断阶段"的现场证明。
5. **故障注入 1（对应故障 1）**：在 `GearInModPlugin.onApplicationLoad` 里加一行 `Global.getSector().getClock();` 后重启：启动中止、日志 NPE；改回后确认恢复。
6. **故障注入 2（对应故障 6）**：把 `playLoopSound` 的调用从 `advance` 移到 `init`（只调一次）：战斗中循环音约一帧后淡出；改回后确认恢复。

::: tip 分阶段验证，隔离信号

先只接入 `logAccessorState()`，把四个阶段的日志逐条确认后，再接声音调用点——"阶段日志"与"声音行为"两类信号分开验证，问题出现时能立刻定位是哪一半。

:::

通过信号：步骤 1–4 的日志与表现全部符合（日志字符串逐字匹配）；步骤 5、6 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；删除临时加上的故障注入行；确认没有对共享文件（如 `tutorial-fixtures\build.gradle`）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileS06p03Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；
`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/GearInGlobalAccess.class` 与 `data/scripts/gearin/GearInSoundPlayer.class`。
3. **静态自查**：页面两个代码块与 fixture 逐字一致（含 import 顺序与注释）；`SOUND_ID_*` 与既有声音注册的 ID 逐字一致；没有把工作单元编号写进任何运行时名称。

## 实例驱动故障分析

1. **启动中止：`NullPointerException`，堆栈指向 `onApplicationLoad`**。触发：在资源加载期访问 `Global.getSector()` 或
`Global.getCombatEngine()`（此刻两者为 null）并解引用。可观察：启动中止，日志 NPE。修正：生涯/战斗数据一律延后到 `onGameLoad` / 战斗上下文；资源加载期只读 `getSettings()` / `getSoundPlayer()` / `getLogger()`。
2. **战斗外调用引擎方法"没有效果"但不报错**。触发：在生涯脚本里用 `Global.getCombatEngine()` 拿引擎后调 `getPlayerShip()` / `addPlugin(...)` 等。
可观察：`getPlayerShip()` 返回"Command Shuttle"假舰（非 null，延迟创建）、`getShips()` 为空集合、`addPlugin` 静默无效—— 因为战斗外拿到的是空引擎而不是 null（生命周期规则 3）。修正：先用 `isInCombat()`（`getCurrentState() == GameState.COMBAT`）守卫，只在战斗上下文操作引擎。
3. **跨战斗缓存战斗引擎，第二场战斗后一切静默失效**。触发：把 `Global.getCombatEngine()` 的返回值存入字段跨战斗复用。
可观察：战斗结束后旧引擎被销毁并立即重建，缓存引用是已清空的死对象——`getShips()` 恒空、`isPaused()` 无意义、`addPlugin` 不生效，且无任何报错。修正：每帧 / 每次需要时现取，不缓存（成熟实例在 `advance()` 里对缓存引擎判空也是同一防御做法）。
4. **退出到主菜单后继续使用旧生涯引擎**。触发：把 `Global.getSector()` 缓存进字段（或认为"退出后 sector 为 null"而在退出后无条件重取/复用）。
可观察：退出生涯后 `getSector()` 仍返回旧对象（`Global` 的引用不被清空），其上实体、市场、持久数据已随存档关闭失效，行为错乱且难排查。修正：跨存档状态放持久数据，阶段判断用 `getCurrentState()`；生涯引用每次从 `Global.getSector()` 现取。
5. **`playSound` 调用点抛 `NullPointerException`**。触发：`loc` 或 `vel` 传了 null。
可观察：异常在播放调用点直接抛出（声音播放内部直接解引用 `loc.x` / `vel.x`）。修正：位置音效永远传实体的 `getLocation()` 与一个非 null 速度向量（如拷贝 `getVelocity()` 或 `new Vector2f()`）。
6. **循环音效"自己停了"**。触发：`playLoop` 没有在每帧回调里续期（比如只在 `init` 里调了一次）。
可观察：循环播放约一帧后淡出停止（API 约定 "Must be called every frame or the loop will fade out"）。修正：把 `playLoop` 放进 `advance` 每帧调用（示例的 `playLoopSound` 调用点）。
7. **`playSound` 用双声道素材 / `playUISound` 用单声道素材**。触发：无视声道约定。
可观察：双声道素材在引擎内无法正确播放（定位与衰减错误），UI 音效用单声道素材表现异常。修正：按资产规格准备素材（`playUISound` 双声道、`playSound` mono）；声音消费主题有完整排查。
8. **播放调用点抛 `RuntimeException: Sound set with id [...] not found`**。触发：引用了未注册或拼错的 sound ID（含大小写不一致）。
可观察：异常在调用点抛出，日志定位到播放行。修正：核对既有声音注册的 ID 逐字一致（含大小写）；声音消费主题有完整故障定位流程。
9. **代码里出现 `Global.setCombatEngine(...)` / `Global.setSector(...)` 等 setter 调用**。触发：误以为可以手动"注入"对象（常见于照抄老代码或测试代码）。
可观察：Global 引用与引擎实际状态脱钩，依赖 `Global` 的原版与 MOD 代码读到错误对象，行为未定义。修正：删除 setter 调用，改用正确入口——settings 类注册走注册主题的流程，跨存档状态走持久数据。

## 总结

`Global` 是进程级静态中枢，RC8 提供七个 getter、两个弃用别名、两个静态标志与一组 setter。记忆要点：

- **全程可用**：`getSettings()`、`getSoundPlayer()`、`getLogger()`（游戏循环启动前就已就位）。
- **资源加载期为 null**：`getSector()`、`getFactory()`、`getCombatEngine()`、`getCurrentState()`——
`onApplicationLoad` 里访问生涯/战斗对象就是空指针（modPlugin 生命周期禁令的精确语义）。
- **创建后不再回到 null**：sector/factory 只被替换；combatEngine 每场战斗结束销毁后立即重建。因此阶段判断**必须**用 `getCurrentState()`，引用**禁止**跨存档/跨战斗缓存。
- **`getPlayerShip()` 永不返回 null**：`playerShip` 未设置时延迟创建"Command Shuttle"假舰；
判断"用户是否已部署"用 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()`，**禁止**用 `getPlayerShip() == null`（判据细则见战斗域部署判断主题）。
- **setter 禁止调用**：内核生命周期专用，调用即脱钩。
- **声音入口三约定**：`playUISound` 素材应双声道；`playSound` 素材必须 mono 且 `loc`/`vel` 非 null；
`playLoop` 必须每帧续期且 `playingEntity` 必填；未注册 ID 在调用点抛 `Sound set with id [...] not found`。

这些纪律的内核可以压缩为一句话：先问阶段（`getCurrentState()`），再取入口。沿同一条链路继续深挖，`getSettings()` 的注册与字段体系、以及声音消费在 UI / 位置 / 循环之外的管理方法，都是本页入口清单的自然延伸。

## 建议参考项

- `com.fs.starfarer.api.Global`：进程级静态中枢——访问器、静态标志与 setter 组所在。
- `com.fs.starfarer.api.GameState`：`TITLE` / `CAMPAIGN` / `COMBAT` 阶段枚举。
- `com.fs.starfarer.api.SoundPlayerAPI`：`playUISound` / `playSound` / `playLoop` 三约定与音乐方法。
- `com.fs.starfarer.api.FactoryAPI`：生涯对象工厂（`createFleetMember` / `createCargo` / `createPerson` / `createMemory`）。
- `com.fs.starfarer.api.combat.CombatEntityAPI`：`getLocation()` / `getVelocity()`。
- `org.apache.log4j.Logger`：`Global.getLogger(Class)` 返回的日志器类型。
- `data/scripts/gearin/`：本页两个示例类所在目录（`GearInGlobalAccess` / `GearInSoundPlayer`）。
- `tutorial-fixtures\units\S06-P03\src\main\java\data\scripts\gearin\`：fixture 目录，页面代码块与其逐字一致。
- `Starsector\starsector-core\starsector.log`：日志输出位置。
