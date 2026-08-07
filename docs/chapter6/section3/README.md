# 使用 Global 访问游戏服务

本节使用 `Global` 访问游戏提供的全局服务，并判断这些服务在加载、主菜单、生涯与战斗阶段是否可用。重点是选择正确的访问入口，避免在对象尚未建立时取得空值。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

* 列出 `Global` 的完整访问器清单 (七个 getter、两个 `@Deprecated` 别名、两个静态标志、一组 setter)，并说出每个成员的返回类型与用途；
* 按 "资源加载期 / 主菜单 / 生涯 / 战斗" 四个阶段说出各 getter 的 null 语义，并解释为什么 "用 `getSector()` / `getCombatEngine()` 是否为 null 判断当前阶段" 不可靠；
* 用 `Global.getCurrentState()`(返回 `GameState`) 正确判断当前阶段，并说出 `LOADING_SAVE` 标志的置位窗口；
* 用 `Global.getLogger(Class)` 记录日志并说出输出位置；
* 用 `Global.getSoundPlayer()` 播放 UI 音效、位置音效与循环音效，解释声道约定、`loc`/`vel` 非 null 约定与循环 "每帧续期" 约定；
* 从症状定位六类典型故障：加载期空指针、战斗外引擎误用、跨战斗缓存引用、退出主菜单后使用旧生涯引用、`vel` 为 null 的空指针、未注册 sound ID 的运行期异常。

## 前置知识

* [C1-S1] 的 MOD 目录结构与 `starsector.log` 使用方法；
* [C3-S1] 的生涯对象、测试战役与装配部署；
* [C5-S1] 的 OGG 资源规格；
* [C5-S2] 的声音注册、sound ID 匹配与合并规则；
* [C5-S3] 的声音消费入口与播放约定；
* [C6-S1] 的 Java 编译、JAR 输出与 MOD 声明链路；
* [C6-S2] 的 `modPlugin` 生命周期与 `onApplicationLoad` 阶段边界；
* [C7-S1] 的战斗插件与每帧回调模型。

## 文件与 API 链路

`Global` 是进程级静态门面，每个 getter 是一条链路的入口：

### Global 访问器速查表

| `Global` 入口 | 返回 | 链路下游 (本教程页面) |
|---|---|---|
| `getSettings()` | `SettingsAPI` | 设置、规则与静态数据 ([C6-S4] 精确聚焦；Chapter 14 页面消费规则) |
| `getSector()` | `SectorAPI` | 生涯域 ([C3-S1]、Chapter 9 页面) |
| `getCombatEngine()` | `CombatEngineAPI` | 战斗域 ([C7-S1] 起的战斗插件) |
| `getSoundPlayer()` | `SoundPlayerAPI` | 声音注册链 [C5-S2] 与使用方 [C5-S3] |
| `getFactory()` | `FactoryAPI` | 生涯对象工厂 (本节) |
| `getLogger(Class)` | log4j `Logger` | `starsector.log`[C1-S1] |
| `getCurrentState()` | `GameState` | 阶段判断 (本节) |

Global 的引用由内核在固定的时机登记 (调用点详见 "生命周期与 null 语义")：

```text
启动器.launch                   → setSettings(启动器设置.getSettingsAPI())
                                     (游戏循环启动前，任何 MOD 入口都非 null)
内核战斗入口.main               → setSoundPlayer(new 声音播放实现())
                                     (AppDriver 各状态注册之前)
生涯引擎.setInstance            → setSector(instance) + setFactory(instance)
                                     (新档/读档时；主菜单首次初始化也会临时创建一次)
战斗引擎.getInstance            → setCombatEngine(new 战斗引擎())   (用到时才创建)
战斗引擎.destroyInstance        → setCombatEngine(null)                  (销毁)
游戏管理器.loadGame             → Global.LOADING_SAVE = true / false     (读档窗口)
```

## 选择服务入口

不需要背诵 `Global` 的成员表。静态配置与注册表从 `getSettings()` 进入；生涯对象从 `getSector()` 或 `getFactory()` 进入；当前战斗对象从 `getCombatEngine()` 进入；阶段判断只读 `getCurrentState()`。日志和声音服务分别使用 `getLogger()` 与 `getSoundPlayer()`。

真正影响写法的是这些入口何时代表当前对象。下一张生命周期表把跨阶段差异集中在一起，排错时先找当前阶段，再检查所取对象。

## 判断服务生命周期

四个阶段的可用性一览 (★ = 返回 `null`，● = 非 null)：

### 阶段可用性速查表

| 阶段 (此时 `getCurrentState()`) | `getSettings` | `getSoundPlayer` | `getSector` | `getFactory` | `getCombatEngine` |
|---|---|---|---|---|---|
| 资源加载期 (`null`，含 `onApplicationLoad`) | ● | ● | ★ | ★ | ★ |
| 主菜单 (`TITLE`) | ● | ● | ●(弃用引用，见规则 2) | ●(弃用引用) | ●(空引擎，见规则 3) |
| 生涯 (`CAMPAIGN`) | ● | ● | ● 当前生涯引擎 | ● 可用 | ●(空引擎) |
| 战斗 (`COMBAT`) | ● | ● | ● 当前生涯引擎 | ● 可用 | ● 当前战斗引擎 |

以下每条都是当前基线的现状：

1. **`getSettings()` 与 `getSoundPlayer()` 在游戏循环开始前就已就位**：内核在游戏循环启动前完成 `Global.setSettings(...)`，游戏启动入口再在注册各阶段状态之前完成 `Global.setSoundPlayer(...)`。因此任何 MOD 入口 —— 包括资源加载期的 `onApplicationLoad`—— 拿到两者都**非 null**。
2. **`getSector()` / `getFactory()` 只在第一个生涯引擎创建之前为 null**，即资源加载期 (`onApplicationLoad` 阶段，即 modPlugin 生命周期禁令所辖的窗口)；一旦创建，`Global` 的引用**只会被替换、不会被置回 null**。
    *   退出生涯回主菜单并不清空 `Global`：`Global` 对生涯引擎的引用在退出后仍然保留 (不会置回 null)，`getSector()` 仍返回旧对象。
    *   可观察结果：退出到主菜单后 `getSector()` 仍返回**旧生涯引擎对象 (非 null)**，其数据已随存档关闭而失效。
    *   因此 "是否在生涯中" **必须** 用 `Global.getCurrentState() == GameState.CAMPAIGN` 判断，不能依赖 `getSector() == null`。
3. **`getCombatEngine()` 的战斗外取值不是 null，而是空引擎**：战斗引擎的首次创建发生在主菜单初始化阶段 —— 首次初始化生涯引擎时会一并创建；每场战斗结束后，内核先销毁旧引擎 (用 `Global.setCombatEngine(null)` 清空引用)，**再立即重建**一个新引擎。
    *   可观察结果：战斗外 `getCombatEngine()` 非 null 而是刚重建的空引擎；战斗结束后缓存的旧引用是已清空的死对象 (`getShips()` 为空；`getPlayerShip()` 也不是 null—— 它**永不返回 null**，`playerShip` 未设置时延迟创建 "Command Shuttle" 假舰)。
    *   因此：**禁止**跨战斗缓存引擎引用，每次需要时现取；"是否在战斗中" 用 `getCurrentState() == GameState.COMBAT` 判断；"用户是否已部署" **禁止** 用 `getPlayerShip() == null` 判断 (战斗内该表达式恒为 `false`，依赖它的分支永远不执行、静默失效)，应读 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()` 是否非空 (判据细则见战斗域部署判断主题)。
4. **`getCurrentState()` 由当前所处阶段实时计算**：处于战斗状态 → `COMBAT`，处于生涯状态 → `CAMPAIGN`，处于主菜单 → `TITLE`，其余状态 (资源加载等)→ `null`。它是阶段判断的唯一可靠入口。

为什么 `getCurrentState()` 返回 null 也是有效信号？

`GameState` 枚举只有三个可枚举值 (`TITLE` / `CAMPAIGN` / `COMBAT`)，资源加载期、MOD 加载期都落在 null 区间 —— 返回 null 不是 "出错"，而是 "阶段不在可枚举状态内"。判断逻辑把 null 与三个枚举值并列处理，才能覆盖全部阶段。

5. **`LOADING_SAVE` 的置位窗口**：读档流程开始时置 `true`，读档流程结束置 `false`(错误路径与 finally 都会复位)；`onEnabled` / `onGameLoad` 回调位于该窗口内，因此这两个回调里 `LOADING_SAVE == true`。常用于跳过读档期间不合适的 UI/动画逻辑。
6. **setter 禁令**：五个 setter 的唯一调用点都在内核生命周期内部 (游戏启动、生涯引擎初始化、战斗引擎创建与销毁)，MOD 代码没有任何正当调用场景，**禁止**调用。可观察结果：手动改写 Global 引用后，所有依赖 `Global` 的代码 (包括原版) 读到与引擎实际状态不符的对象 —— 例如战斗外 `setCombatEngine(...)` 后 `getCombatEngine()` 指向一个不被推进的空壳，行为未定义。

本节三条必须遵守的规则

1. 阶段判断**必须**用 `Global.getCurrentState()`**，禁止**依赖 `getSector()` / `getCombatEngine()` 是否为 null。
2. **禁止**跨战斗、跨存档缓存 `Global` 返回的引用，每次需要时现取。
3. **禁止**调用 `Global` 的五个 setter—— 内核生命周期专用，调用后全局引用与引擎实际状态脱钩。

## 写入运行日志

`Global.getLogger(Class)` 是 `Global` 里最 "开箱即用" 的成员：任何时间、任何地点，传入类即返回该类的 log4j 日志器 (`Logger.getLogger(c)` 按类名建 category，同一类名同一日志器)。

```java
// 推荐：类内缓存一份
private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

// 或按需直接调用
Global.getLogger(GearInGlobalAccess.class).info("任意内容");
```

* 输出位置：`Starsector\starsector-core\starsector.log`。
* 级别：`debug` / `info` / `warn` / `error`；生命周期与故障排查日志用 `info`，可恢复的异常情况用 `warn`/`error`。
* 常用形态：入口回调打一行 (同 modPlugin 生命周期示例)、数值检查、故障注入验证。

## 播放界面音效

`Global.getSoundPlayer()` 在**任何阶段都非 null**(见生命周期规则 1)，返回 `SoundPlayerAPI`。三条核心方法 (完整使用方族谱在声音消费主题，本节只给约定与可观察结果)：

### 声音播放方法速查表

| 方法 | 用途 | 约定 (违反后的可观察结果) |
|---|---|---|
| `playUISound(String id, float pitch, float volume)` | 一次性 UI 音效，无播放点 | 素材**应为双声道** (API 注释原文 "UI sound files should be stereo")；双声道素材用 `playSound` 播放时在引擎内无法正确播放 (API 注释原文 "Sound file must be *mono* for it to be properly played within the engine") |
| `playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel)` | 位置音效，在 `loc` 处播放并按方位与距离计算 | 素材**必须为 mono**(同上前提)**；`loc` 与 `vel` 都必须非 null**—— 播放声音时引擎直接解引用 `loc.x` / `vel.x`，传 null 在调用点抛 `NullPointerException` |
| `playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel)` | 循环音效 | **必须每帧调用，否则循环淡出** (API 注释原文 "Must be called every frame or the loop will fade out")；`playingEntity` **必填** —— 用于区分同一实体的循环 (同实体同循环只播一个)；素材建议 mono；调用方负责在每帧回调里续期 (完整讲解见声音消费主题) |

其他常用成员 (约定均出自 `SoundPlayerAPI` javadoc)：

* `playCustomMusic(int fadeOutIfAny, int fadeIn, String musicSetId)`：切换音乐集；**淡入淡出秒数必须是整数** (javadoc 原文 "have to be whole numbers")，`musicSetId` 传 `null` 停止当前曲且不换新曲。
* `getCurrentMusicId()`：返回当前曲目 ID；无播放时返回字符串 `"nothing"`(javadoc 原文)。
* `restartCurrentMusic()` / `getCurrentMusicId()` / `playCustomMusic` / `setSuspendDefaultMusicPlayback`：javadoc 标注线程安全，可从非主循环线程调用。

未注册 ID 的行为：引用未注册的 sound ID 时，播放调用抛 `RuntimeException`，消息为 `Sound set with id [...] not found`—— 这是运行期异常，在调用点抛出 (声音消费主题的故障分析有完整定位流程)。

注意声音播放实现对 `null` ID 是宽容的 (`playUISound`/`playSound` 返回 `null`、`playLoop` 忽略)，但宽容不是可以依赖的行为：ID 拼错或未注册照样抛异常。

声音入口的两个经典坑

`playSound` 的 `loc` / `vel` 传 null 会在调用点直接抛 `NullPointerException`；声道选错不会报错 —— 双声道素材用 `playSound` 播放时定位与衰减错误、单声道素材用 `playUISound` 表现异常，且都难以从日志定位。

## 实现全局服务访问

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 声明全局服务访问类

```java
// 插入位置：文件顶部的类声明
public class GearInGlobalAccess {
```

完成信号：类名与数据文件或入口配置中的完整类名一致。

### 保存服务引用

```java
// 插入位置：GearInGlobalAccess 的字段区
import org.apache.log4j.Logger;
```

完成信号：字段只保存可以随启动、战斗或存档稳定恢复的值。

### 接入服务调用

```java
// 插入位置：GearInGlobalAccess 的方法区
public static boolean isInCombat() {
    // 先记录当前动作，再补充实际业务
}
```

完成信号：对应日志或界面结果出现一次，重复进入相同场景时注册数量保持稳定。

## 完整最小例

在 MOD 工程布局与 modPlugin 生命周期类 `GearInModPlugin` 之上新增两个工具类文件 (不新增任何资源、不新增任何注册项)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin      # 既有 modPlugin 生命周期类(C6-S2)；本节只在其回调里调用示例方法
│       ├── GearInGlobalAccess   # 本节新增：访问器观测与阶段判断
│       └── GearInSoundPlayer    # 本节新增：声音播放入口(三契约)
└── jars\
```

### 核对全局访问源码

职责：Global 静态访问器观测与阶段判断工具 ——`isInCombat()` 用 `getCurrentState()` 判战斗；`logAccessorState()` 巡览四阶段访问器可用性，并对资源加载期的 null 入口告警。

绑定位置：静态工具类、无注册项；调用点为 `GearInModPlugin` 的两个生命周期回调 (见下方调用点片段)。

对应关系：

* `Global.getLogger(GearInGlobalAccess.class)`：类内缓存一份日志器 (见 "Logger" 节)。
* `isInCombat()`：阶段判断唯一可靠做法 ——`getCurrentState() == GameState.COMBAT`，不依赖 `getCombatEngine()` 是否为 null(生命周期规则 3)。
* `logAccessorState()`：把生命周期表变成日志；`sector == null` / `engine == null` 只在资源加载期出现 (规则 2、3)。
* `Global.LOADING_SAVE`：读档回调窗口内为 `true`(规则 5)。

### 核对音效访问源码

职责：SoundPlayerAPI 声音播放入口，覆盖 UI(`playUISound`)/ 位置 (`playPositionalSound`)/ 循环 (`playLoopSound`) 三约定，并持有两个声音 ID 常量。

绑定位置：静态工具类、无注册项；调用点示例为 `GearInFrameLogger.advance()` 每帧续期循环音 (见下方调用点片段)。

对应关系：

* `SOUND_ID_SYSTEM_LOOP` / `SOUND_ID_WEAPON_FIRE`：复用既有声音注册的 ID(声音资产与注册主题)，不新增 ID。
* `playUISound` / `playPositionalSound` / `playLoopSound`：对应 SoundPlayerAPI 三约定 (见上表)；`vel` 用拷贝构造避免引用被引擎后续改写；`playLoopSound` 保留引擎参数以承载 "战斗插件 advance 上下文" 的调用形态。

调用点 (工程片段 —— 方法所在类由既有章节持有，本节只演示调用，不改动那些类)：

```java
    // GearInModPlugin(modPlugin 生命周期类)内新增两行调用
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
    // GearInFrameLogger(战斗每帧日志类)的 advance() 内加一段(沿用每帧回调模型)
    // 战斗内 getPlayerShip() 永不返回 null(延迟重建"Command Shuttle"假舰)，无需判空
    if (GearInGlobalAccess.isInCombat()) {
        GearInSoundPlayer.playLoopSound(engine, engine.getPlayerShip());
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用既有编译链路)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/GearInGlobalAccess.class` 与 `data/scripts/gearin/GearInSoundPlayer.class`(可用 `jar tf` 核对)。

### 排查失效服务对象

把 `Global` 返回的生涯或战斗对象缓存到静态字段。

**局部故障片段：**

```java
// 故障片段
private static CombatEngineAPI cached = Global.getCombatEngine();
```

**局部修正片段：**

```java
// 修正片段
if (Global.getCurrentState() == GameState.COMBAT) { CombatEngineAPI engine = Global.getCombatEngine(); }
```

复验：进入一场战斗再退出，确认日志没有继续读取旧引擎。

## 验证方案

前置数据：MOD 工程布局与编译链路 [C6-S1]、`GearInModPlugin` 生命周期类与持久数据门 [C6-S2]，并已接入本节两个调用点片段、`GearInGlobalAccess` / `GearInSoundPlayer` 两个工具类 (本节，代码见上)、声音资产与注册 [C5-S1] / [C5-S2]：`GearIn_system_loop`、`GearIn_weapon_fire`、`GearIn_test` 测试战役 [C3-S1]。

1. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`，再用 `jar tf jars\GearIndustry.jar` 核对两个工具类。

2. **启动 (资源加载期)**：勾选 "齿轮工业" 启动。进入主菜单前 `starsector.log` 出现一行。

    ```text
    GearIn state=null settings=true soundPlayer=true sector=false engine=false loadingSave=false
    ```

    同时观察紧随其后的两条 `GearIn getSector() is null: 资源加载期访问生涯对象会空指针` / `GearIn getCombatEngine() is null: 资源加载期访问战斗对象会空指针` 的 `WARN`。

3. **新建生涯 / 读档 (生涯回调)**：新建生涯，`onGameLoad` 回调日志出现。

    ```text
    GearIn state=CAMPAIGN settings=true soundPlayer=true sector=true engine=true loadingSave=true
    ```

    (读档与新建都经过 `onGameLoad`；`loadingSave=true` 证明回调位于读档窗口内) 且没有步骤 1 的两条 `WARN`；读档重复确认。

4. **战斗**：进入 `GearIn_test` 测试战役的一场战斗，日志出现 `GearIn state=COMBAT ...`；若已接入 `playLoopSound` 调用点，战斗内持续听到 `GearIn_system_loop` 循环音，战斗结束 (回到生涯) 后循环淡出停止，日志回到 `state=CAMPAIGN`。

5. **退出到主菜单**：生涯中退出到主菜单，日志出现 `GearIn state=TITLE ...`，且 `sector=true` 仍成立 ——`getSector()` 返回的是已弃用的旧生涯引擎 (非 null)，与 `getCurrentState() == TITLE` 一起确认阶段，这正是 "不能靠 null 判断阶段" 的现场证明。

预期结果：

* 游戏构建：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/GearInGlobalAccess.class` 与 `data/scripts/gearin/GearInSoundPlayer.class`。
* 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

## 故障分析

- **`onApplicationLoad` 抛出 `NullPointerException`**：资源加载期解引用了尚未建立的 `Global.getSector()` 或 `Global.getCombatEngine()`。生涯和战斗数据分别延后到 `onGameLoad` 与战斗上下文。
- **战斗外调用引擎方法没有效果**：战斗外取得的是空引擎，`getShips()` 为空且 `addPlugin` 静默无效。先检查 `getCurrentState() == GameState.COMBAT`，只在战斗阶段操作引擎。
- **第二场战斗开始后引擎操作全部失效**：代码跨战斗缓存了旧 `CombatEngineAPI`。每次需要时重新调用 `Global.getCombatEngine()`，不要把它保存在跨战斗字段中。
- **退出到主菜单后仍在使用旧生涯对象**：缓存的 `SectorAPI` 仍指向已经关闭的存档。用 `getCurrentState()` 判断阶段，并在需要时重新获取生涯入口。
- **`playSound` 抛出 `NullPointerException`**：`loc` 或 `vel` 传入了 null。始终传实体位置和非 null 速度向量，再次播放确认异常消失。
- **循环音效自行停止**：`playLoop` 没有在每帧回调中续期。把调用移入 `advance`，并持续使用稳定的 `playingEntity`。
- **位置音效无法正确定位**：`playSound` 使用了双声道素材。位置音效改用 mono 素材后，在不同距离重新试听。
- **UI 音效播放异常**：`playUISound` 使用了单声道素材。替换为双声道素材并重新触发界面操作。
- **播放时报 `Sound set with id [...] not found`**：sound ID 未注册或大小写不一致。核对注册表后再次调用。
- **调用 `Global` setter 后全局行为错乱**：MOD 代码使用了 `setCombatEngine`、`setSector` 等内核入口。删除 setter 调用，改从正确 getter 获取对象；跨存档状态放入持久数据。

## 总结

`Global` 是进程级静态中枢，提供七个 getter、两个弃用别名、两个静态标志与一组 setter。记忆要点：

* **全程可用**：`getSettings()`、`getSoundPlayer()`、`getLogger()`(游戏循环启动前就已就位)。
* **资源加载期为 null**：`getSector()`、`getFactory()`、`getCombatEngine()`、`getCurrentState()`；`onApplicationLoad` 里访问生涯/战斗对象就是空指针 (modPlugin 生命周期禁令的精确语义)。
* **创建后不再回到 null**：sector/factory 只被替换；combatEngine 每场战斗结束销毁后立即重建。因此阶段判断**必须**用 `getCurrentState()`，引用**禁止**跨存档/跨战斗缓存。
* **`getPlayerShip()` 永不返回 null**：`playerShip` 未设置时延迟创建 "Command Shuttle" 假舰；判断 "用户是否已部署" 用 `getFleetManager(FleetSide.PLAYER).getDeployedCopy()`，**禁止**用 `getPlayerShip() == null`(判据细则见战斗域部署判断主题)。
* **setter 禁止调用**：内核生命周期专用，调用即脱钩。
* **声音入口三约定**：`playUISound` 素材应双声道；`playSound` 素材必须 mono 且 `loc`/`vel` 非 null； `playLoop` 必须每帧续期且 `playingEntity` 必填；未注册 ID 在调用点抛 `Sound set with id [...] not found`。

这些纪律的内核可以压缩为一句话：先问阶段 (`getCurrentState()`)，再取入口。沿同一条链路继续深挖，`getSettings()` 的注册与字段体系、以及声音消费在 UI / 位置 / 循环之外的管理方法，都是本节入口清单的自然延伸。

## 建议参考项

* `com.fs.starfarer.api.Global`：进程级静态中枢 —— 访问器、静态标志与 setter 组所在。
* `com.fs.starfarer.api.GameState`：`TITLE` / `CAMPAIGN` / `COMBAT` 阶段枚举。
* `com.fs.starfarer.api.SoundPlayerAPI`：`playUISound` / `playSound` / `playLoop` 三约定与音乐方法。
* `com.fs.starfarer.api.FactoryAPI`：生涯对象工厂 (`createFleetMember` / `createCargo` / `createPerson` / `createMemory`)。
* `com.fs.starfarer.api.combat.CombatEntityAPI`：`getLocation()` / `getVelocity()`。
* `org.apache.log4j.Logger`：`Global.getLogger(Class)` 返回的日志器类型。
* `data/scripts/gearin/`：本节两个示例类所在目录 (`GearInGlobalAccess` / `GearInSoundPlayer`)。
* `Starsector\starsector-core\starsector.log`：日志输出位置。

::: details 完整成品：GearInGlobalAccess

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
 * - 资源加载期(C6-S2 的 onApplicationLoad)：getSector()/getCombatEngine() 为 null，
 *   只能读 getSettings()/getSoundPlayer()/getLogger()；
 * - 生涯回调与生涯脚本：getSector() 可用；
 * - 战斗插件：getCombatEngine() 是当前战斗引擎，必须每帧现取，不得跨战斗缓存。
 *
 * 声音播放入口见 GearInSoundPlayer(声音 ID 由 C5-S1 声明、C5-S2 注册，本单元只消费)。
 */
public class GearInGlobalAccess {

    /** 日志器：进程级，任何阶段可用(org.apache.log4j.Logger，输出到 starsector.log)。 */
    private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

    private GearInGlobalAccess() {
        // 工具类：不实例化
    }

    /** 当前是否处于战斗：必须用 getCurrentState() 判断，不能依赖 getCombatEngine() 是否为 null。 */
    public static boolean isInCombat() {
        return Global.getCurrentState() == GameState.COMBAT;
    }

    /** 访问器巡览：记录当前阶段与各入口的可用性(注意资源加载期 sector/engine 为 null)。 */
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

:::

::: details 完整成品：GearInSoundPlayer

```java
package data.scripts.gearin;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;

/**
 * 角色：SoundPlayerAPI 声音播放入口(UI / 位置 / 循环三契约)。
 *
 * 全部方法是静态工具，可从任何已注册的脚本/插件调用；getSoundPlayer() 在
 * 任何阶段都非 null(Global 生命周期规则 1)，因此无需判空。
 *
 * 声音 ID GearIn_system_loop / GearIn_weapon_fire 由 C5-S1 声明、C5-S2 注册，
 * 本单元只消费(完整消费者族谱见 C5-S3)。
 */
public class GearInSoundPlayer {

    public static final String SOUND_ID_SYSTEM_LOOP = "GearIn_system_loop";
    public static final String SOUND_ID_WEAPON_FIRE = "GearIn_weapon_fire";

    private GearInSoundPlayer() {
        // 工具类：不实例化
    }

    /** UI 音效：素材应为双声道(API 注释 "UI sound files should be stereo")。 */
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

:::
