# 生涯脚本：暂停、时间换算与存档

本节深入生涯脚本机制的三个底座：`EveryFrameScript` 的每帧推进约定、`amount` 的时间换算，以及脚本随存档的序列化边界，并据此给出 "每日/周期脚本" 的四种实现模式。前置：生涯脚本与时钟基础（C08-S01）。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 精确复述 `advance(float amount)` 的 `amount` 语义（上一帧真实秒数，API javadoc 原文），并说出正常帧、暂停、加速、新档推进、战斗五种时序下引擎如何调用脚本与时钟；
- 说出 `isDone()` 与 `runWhilePaused()` 的引擎执行约定（每帧最多两查、清理入口、脚本基类默认值、`EveryFrameScriptWithCleanup` 的边界），并用 `isDone()` 控制常驻脚本自停；
- 讲清 `CampaignClockAPI` 的内部换算（10 真实秒 = 1 游戏日、整游戏秒截断、时间戳与 `getElapsedDaysSince`），并区分 "以日历为准" 与 "以累加器为准" 两类时间判断及其各自适用场景；
- 讲清存档序列化边界：`scripts` 随存档保存、`transientScripts` 读档后必然清空、脚本字段的 XStream 可序列化要求与 `readResolve()` 恢复模式；
- 从零编写每日/周期脚本并正确注册（本节以 `GearInCalendarDayScript` 演示模式 A、`GearInDayAccumulatorScript` 演示模式 B），在四种周期模式（日历跨日、天数累加、间隔器、自注册倒计时）之间做选择；
- 从症状定位十一类典型故障。

## 前置知识

本节建立在前置章节的生涯机制之上：脚本注册与推进循环、时间与暂停语义、persistent data 守卫，以及阶段与战斗域边界。具体衔接如下：

- 星区结构、生涯时钟、`EveryFrameScript` 入门、脚本注册与推进循环、暂停与加速语义、persistent data `CLASS_MARK` 守卫 —— 本节的所有概念都从这里继续，不重复基础讲解（C08-S01）。
- `modPlugin` 生命周期回调与 persistent data 守卫模式；本节的注册沿用同一个守卫（C06-S02）。
- `Global.getSector()` 的 null 语义与 "用 `getCurrentState()` 判断阶段"；脚本注册必须发生在生涯引擎建立之后（C06-S03）。
- 战斗域每帧回调（`EveryFrameCombatPlugin`）—— 两边的暂停语义不同，注意对照（生涯脚本循环在暂停时默认完全跳过）（C07-S01）。

## 术语

按术语约定，API 名保留源码大小写。本节首次完整讲解两个页面级术语（不进入共享术语表）：

- **每日脚本（daily script）**：以 "游戏日" 为最小时间颗粒、每个游戏日执行一次（或每 N 个游戏日一次）的生涯脚本。判断 "新的一天" 有两种基准：日历日期变化，或游戏天累加器跨过整数。
- **周期脚本（periodic script）**：以固定或随机间隔反复执行某段逻辑的生涯脚本；间隔单位可以是真实秒（间隔器直接推进）或游戏天（先 `convertToDays` 再推进）。

## 文件与 API 链路

### EveryFrameScript：三方法约定再深入

`EveryFrameScript`（`com.fs.starfarer.api.EveryFrameScript`）只有三个方法，是生涯按帧脚本的完整约定。此前已讲过三方法是什么，本节讲引擎到底怎么用它们：

| 方法 | 引擎执行约定（生涯引擎 `advance` 的内部实现）|
|---|---|
| `boolean isDone()` | 每帧**最多查两次**：`advance` 前查一次 —— 为 `true` 直接移除（不调 `advance`）；`advance` 返回后再查一次 —— 为 `true` 移除。常驻脚本返回 `false` |
| `boolean runWhilePaused()` | 守卫条件 `runWhilePaused() || !isPaused()`：返回 `true` 时暂停期间 `advance` 仍被调用，但 `amount` 仍是真实秒数、时钟仍冻结（见 "时间换算" 节）|
| `void advance(float amount)` | `amount` 是**上一帧经过的真实秒数**（javadoc 原文 "seconds elapsed during the last frame"；"Use SectorAPI.getClock() to convert to campaign days"）。**必须**经时钟换算才是游戏天 |

执行细节：

1. 引擎遍历的是**列表快照**（`new ArrayList<>(this.scripts)`），脚本在 `advance` 里 `addScript` / `removeScript` 安全；代价是 advance 里新加的脚本下一帧才推进。
2. 移除是 "遍历快照、改原列表"：
   - `isDone` 自动清理；
   - `removeScript(script)` 按**对象同一性**；
   - `removeScriptsOfClass(Class)` 按**精确类** `getClass() == clazz`；
   - `hasScript(Class)` 用 `isInstance`—— 查重用 `hasScript`，批量删用 `removeScriptsOfClass`，别混用。
3. **`addScript` 不去重**：同一实例或等价实例重复注册会各自推进 —— 一次性注册必须用 persistent data 守卫（必须遵守的规则，见 "注册与存档" 节）。
4. 脚本基类把 `runWhilePaused()` 默认设为 `false`；引擎内置脚本也走同一机制。
5. `EveryFrameScriptWithCleanup`（`com.fs.starfarer.api.EveryFrameScriptWithCleanup`）只多一个 `cleanup()`：API javadoc 约定是 "附着实体从生涯引擎移除时调用"（实体级脚本；引擎级脚本不触发）。该接口没有可观测的调用点，行为表述以 API javadoc 为准。

### amount 的取值：五种时序下引擎怎么调 advance

引擎在五种时序下推进脚本与时钟的方式各不相同，本节把调用方式与后果列全：

| 时序 | 引擎行为 | 脚本与时钟后果 |
|---|---|---|
| 正常帧 | 每渲染帧调一次 `engine.advance(var1)`，`var1` 是该帧真实秒数 | 脚本 `advance(amount)` 每帧一次，`amount` = 帧真实秒；时钟按 10 秒/游戏日前进 |
| 暂停（空格）| 引擎照常 advance，但脚本守卫 `runWhilePaused() || !isPaused()` 挡住、`clock.advance` 只在非暂停分支执行 | 默认脚本完全冻结、时钟冻结、`amount` 也拿不到；`runWhilePaused = true` 时 `advance` 仍被调用但时钟不动 |
| 加速（按住 Shift）| `fastForward` 时 `var35 = round(campaignSpeedupMult)`（`settings.json` 的 `campaignSpeedupMult`，RC8 = 2），同一渲染帧连调 `var35` 次 `engine.advance(var1)`，每次 `amount` 都是完整帧秒 | 脚本与时钟同帧推进两次，游戏日流速翻倍；`Global.getSector().isFastForwardIteration()` 可探测 "本次调用来自加速迭代"（`SectorAPI.isFastForwardIteration()`）。devMode 下还有 10x/100x 调试键 |
| 新档推进 | 新建生涯时有一段快速推进窗口（`isInFastAdvance()`），窗口内时钟与脚本连续推进，之后才调 `onNewGameAfterTimePass` 与 `onGameLoad(true)`（游戏管理器）| `onNewGame` 注册的脚本经历这次推进；`onGameLoad` 里注册的脚本**不**经历（窗口已关闭）|
| 战斗 | 进入战斗后运行的是战斗状态，生涯状态不再推进 | 生涯脚本、时钟、经济全部冻结，回生涯后恢复 |

### 时间换算：时钟内部机制

`CampaignClockAPI`（`com.fs.starfarer.api.campaign.CampaignClockAPI`）由引擎内部实现。此前已给出换算基线，本节讲换算的**内部机制**与两个推论：

- **基线：10 真实秒 = 1 游戏日**（`SECONDS_PER_GAME_DAY = 10.0F`）。`convertToDays(秒)` = 秒 ÷ 10；`convertToSeconds(天)` = 天 × 10；`convertToMonths(秒)` = 秒 ÷ 10 ÷ 30。
- **日历以 "整游戏秒" 推进**：`advance(float)` 的实作是 `(int)(秒 / secondsPerDay * 86400)` 游戏秒后加进日历 —— 每帧独立截断到整数游戏秒，**余数不结转**。
  - 推论一：`getDay()` / `getDateString()` / `getTimestamp()` 全部来自这个日历，因此是整秒粒度；而 `convertToDays(amount)` 的累加保留小数。
  - 推论二：长时间游玩后两者会错位 —— 每帧损失不足 1 游戏秒，60 FPS 下一小时约损失 1 个游戏天量级的日历时间（帧率越高损失越快）。
  - 结论：**"以日历为准"（跨日检测）与 "以累加器为准"（`convertToDays` 累加）是两种不同的时间基准**，短会话一致、长会话错位；统计型进度用累加器，日历事件用跨日检测。
- **`getElapsedDaysSince(ts)`**：`(now - ts) / 86400000`，返回真实天数；**`ts == 0` 返回 `Float.MAX_VALUE`**—— 计算 "未初始化" 间隔不要传 0。
- **`getTimestamp()`** 是日历毫秒值，暂停时冻结；`createClock(ts)` 按时间戳建独立时钟做局部计时。
- **读档重建**：`readResolve()` 按保存的时间戳重建内部日历，读档后时钟从存档时刻继续。

::: info 两种基准的分工

日历以整游戏秒前进，适合 "某一天做什么" 这类事件判定；`convertToDays` 累加保留小数、每帧追加，适合跨帧累计的统计。长会话里两者错位不是 bug，而是按需选基准的设计：日历事件用跨日检测、精确统计用累加器。

:::

时间换算速查表（高频方法；签名出处见文末「建议参考项」）：

| 方法 | 返回 | 语义 |
|---|---|---|
| `convertToDays(amount)` | `float` | 真实秒 → 游戏天（`advance` 里**必须**用它换算）|
| `convertToSeconds(days)` | `float` | 游戏天 → 真实秒 |
| `convertToMonths(amount)` | `float` | 真实秒 → 游戏月（÷10÷30）|
| `getTimestamp()` | `long` | 日历毫秒；暂停时冻结；跨存档安全的间隔基准 |
| `getElapsedDaysSince(ts)` | `float` | 从 `ts` 到现在的真实天数；`ts == 0` 返回 `Float.MAX_VALUE` |
| `createClock(ts)` | `CampaignClockAPI` | 按时间戳建独立时钟（局部计时）|
| `isFastForwardIteration()` | `boolean` | 本次推进来自加速迭代（`SectorAPI.isFastForwardIteration()`）|

### 注册与存档：序列化边界

**保存与读档的机制**（游戏管理器）：

- 保存：`saveGame(...)` 用 XStream 序列化**整个生涯引擎（星区）**—— 包括 `scripts` 列表里每个脚本的全部非 `transient` 字段。
- 读档：`fromXML` 还原生涯引擎（星区）与脚本字段，脚本状态从存档值继续。

**两个脚本列表的序列化边界**（生涯引擎）：

- `scripts` **非** `transient` → **随存档保存**：常驻脚本（管理器、周期脚本）跨读档继续运行，其非 `transient` 字段原样恢复；`readResolve` 只补 null 备用值。
- `transientScripts` 是 `transient` → **不随存档保存**，且 `readResolve` 直接重建空列表 → **transient 脚本读档后必然消失，需要重新注册**。
- 实体级脚本：实体的脚本列表也非 `transient` → 随实体保存。

由此得到三条必须遵守的规则与两条建议：

- **必须**：脚本字段只放可随 XStream 序列化的状态（基础类型、可序列化对象）。
  - 把引擎单例、`CampaignUIAPI`、声音播放器等句柄存进字段，会把整个可达对象图拖进存档 —— 可观察结果：存档文件异常膨胀、保存/读档异常，或读档后字段指向失效对象导致行为错乱。
- **必须**：一次性注册用随存档保存的 persistent data 守卫（C06-S02 的 `CLASS_MARK` 模式；本节示例用各自键 `REGISTER_MARK`）——`addScript` 不去重，守卫缺失的可观察结果是读档次数越多日志越成双、行为叠加。
- **必须**：transient 脚本的重新注册写在读档回调里，否则可观察结果是 "读档后脚本消失且无报错"。
- **建议**：跨会话的小数据放 `SectorAPI.getPersistentData()`（persistent data 随档保存；注意 XStream 存档边界）。
- **建议**：脚本内瞬态句柄（贴图、粒子、UI 引用）声明为 `transient` 字段并用 `readResolve()` 重建 —— 成熟实例：实体插件用 `readResolve()` 恢复 `SpriteAPI` 句柄。

### 每日/周期脚本模式（每日脚本、周期脚本）

四种经过验证的模式（判据：原版内部实现与成熟实例模组）：

| 模式 | 实现要点 | 适用场景 | 成熟实例 |
|---|---|---|---|
| A. 日历跨日检测 | `dateKey = cycle*10000 + month*100 + day`，与上次比较，变化即 "新的一天" | 日历事件（征兵、账单、节日）；每游戏日恰好一次 | 本节 `GearInCalendarDayScript`（常驻跨日检测）|
| B. 天数累加器 | `acc += clock.convertToDays(amount)`，`while (acc >= period) { acc -= period; ... }` | 精确天数统计；单帧跨多天也要逐日补触发 | 本节 `GearInDayAccumulatorScript`（含 `daysToRun` 自停演示）|
| C. 间隔器 | `IntervalUtil`（公开 API `com.fs.starfarer.api.util.IntervalUtil`）每帧 `advance`、`intervalElapsed()` 判定；原版游戏内置的同类间隔计时用法一致 | 固定/随机间隔的周期任务；喂秒就是真实时间周期，喂 `convertToDays(amount)` 就是游戏天周期 | 原版游戏的事故判定（按游戏天在 0.5–1.5 天区间内判定）|
| D. 自注册倒计时 | 构造器 `Global.getSector().addScript(this)` 自注册，`isDone()` 完成即被引擎清理；倒计时按天扣减（`isDay` 模式先把 `amount` 换成天）| 一次性延迟任务（剧情事件、临时效果）；少一处调用点 | — |

选型建议（建议强度）：日历事件用模式 A；需要精确累计（含小数）或单帧可能跨多天时用模式 B（用 `while` 而不是 `if`）；固定/随机真实时间节奏用模式 C；一次性倒计时任务用模式 D。模式 A 与 B 混用时记住 "时间换算" 节的漂移结论。

## 完整最小例

在已有 MOD 布局（C06-S01）与 `GearInModPlugin`（C06-S02）之上新增两个脚本类（不新增任何资源、不新增任何注册项）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInCalendarDayScript     # 模式 A：日历跨日检测（本节新增）
│       └── GearInDayAccumulatorScript  # 模式 B：天数累加器 + daysToRun 自停（本节新增）
└── jars\
```

### GearInCalendarDayScript：模式 A（日历跨日检测，常驻）

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 A：日历跨日检测。dateKey = cycle * 10000 + month * 100 + day，
 * 把日历日期折叠为整数键，与上次比较，变化即"新的一天"；每个游戏日恰好触发一次
 * （首帧只初始化不打印）。单帧跨多个游戏日（加速、新档推进）时一帧只报一次 ——
 * 日历事件一帧至多一次是正常行为，需要逐日补触发的逻辑请改用天数累加器
 * （本单元 GearInDayAccumulatorScript）。runWhilePaused() 返回 false；字段只有
 * int，可随存档序列化（XStream 存档边界），脚本随存档保存、读档后继续。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），注册前用 persistent data
 * 键 REGISTER_MARK 做一次性门控（C06-S02 的 CLASS_MARK 模式），键与调用点片段逐字一致。
 */
public class GearInCalendarDayScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CalendarDayScript";

    private static final Logger LOG = Global.getLogger(GearInCalendarDayScript.class);

    /** 跨日判定键（cycle * 10000 + month * 100 + day）；-1 表示尚未初始化。 */
    private int lastDateKey = -1;

    @Override
    public boolean isDone() {
        return false; // 常驻脚本：日历事件逐日触发，永不结束
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        // 本模式只看日历日期，不直接用 amount（时间换算与逐日补触发见 GearInDayAccumulatorScript）。
        int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
        if (dateKey != lastDateKey) {
            if (lastDateKey != -1) {
                LOG.info(String.format("GearIn calendar: new day %s", clock.getShortDate()));
            }
            lastDateKey = dateKey;
        }
    }
}
```

对应关系：

- `implements EveryFrameScript`：三个方法全来自接口（`isDone` / `runWhilePaused` / `advance`）；`isDone()` 恒返回 `false`—— 常驻脚本永不结束，日历事件逐日触发。
- `REGISTER_MARK`：persistent data 守卫键（`"GearIn_CalendarDayScript"`），**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
- `dateKey` 跨日检测（模式 A）：`getCycle() * 10000 + getMonth() * 100 + getDay()` 折叠日期为整数键；首帧只初始化不打印，之后每个游戏日打印一次 `GearIn calendar: new day ...`。
- 时间换算与逐日补触发不在本类：本模式只看日历日期、不直接用 `amount`—— 那是模式 B（`GearInDayAccumulatorScript`）的职责；两个类可同档共存，各自守卫注册。
- 字段只有 `int`（`lastDateKey`），可随存档序列化；`runWhilePaused()` 恒 `false`（暂停时本脚本与时钟一并冻结）。

::: warning 模式 A 在单帧跨多个游戏日（加速、新档推进）时一帧只报一次 "新的一天"

这是正常行为而非漏触发；需要逐日补触发的逻辑应改用模式 B 的 `while` 累加器。

:::

### GearInDayAccumulatorScript：模式 B（天数累加器 + 自停）

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 B：天数累加器。amount 是上一帧真实秒数，必须经
 * clock.convertToDays(amount) 换算成游戏天（10 真实秒 = 1 游戏日）；
 * 余数跨帧保留；单帧跨多个游戏日（加速、新档推进）时 while 逐日补触发；
 * daysToRun >= 0 时累计满该游戏天数后 isDone() 返回 true，引擎下一帧
 * 自动移除；全部字段可随存档序列化（XStream 存档边界），读档后继续。
 * 注册入口：onGameLoad 的 persistent data 门内（REGISTER_MARK 一次性门控，C06-S02 的 CLASS_MARK 模式）。
 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_DayAccumulatorScript";
    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    /** 挂载以来的游戏天数（精确累加，保留小数）；随存档序列化，读档后继续累计。 */
    private float daysSinceMount = 0f;
    /** 例程周期余数：累计满 tickEveryDays 个游戏天触发一次每日例程。 */
    private float daysAccumulator = 0f;
    /** 已执行的每日例程次数（跨存档累计）。 */
    private int routineCount = 0;
    /** 演示参数：每多少个游戏天执行一次例程；默认 1（每日一次）。 */
    private int tickEveryDays = 1;
    /** 演示参数：最多运行多少游戏天后自停；< 0 表示常驻不停止。 */
    private int daysToRun = -1;
    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;
    /** 修改例程周期；周期至少为 1 个游戏天。 */
    public void setTickEveryDays(int tickEveryDays) {
        this.tickEveryDays = Math.max(1, tickEveryDays);
    }
    /** 修改运行天数上限；改回 -1 恢复常驻。 */
    public void setDaysToRun(int daysToRun) {
        this.daysToRun = daysToRun;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        // amount 是上一帧真实秒数；经时钟换算才是本帧的游戏天数（10 真实秒 = 1 游戏日）。
        float daysThisFrame = clock.convertToDays(amount);
        daysSinceMount += daysThisFrame;
        daysAccumulator += daysThisFrame;

        // while 而非 if：单帧跨多个游戏日时逐日补触发；余数保留在 daysAccumulator 里继续累计。
        while (daysAccumulator >= tickEveryDays) {
            daysAccumulator -= tickEveryDays;
            routineCount++;
            LOG.info(String.format(
                    "GearIn daily: routine #%d at %s | since mount=%.1f game days",
                    routineCount, clock.getShortDate(), daysSinceMount));
        }

        if (daysToRun >= 0 && daysSinceMount >= daysToRun) {
            LOG.info(String.format(
                    "GearIn daily: done, isDone()=true after %.1f game days", daysSinceMount));
            done = true;
        }
    }
}
```

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回字段 `done`—— 累计满 `daysToRun` 后引擎在下一帧自动移除本脚本；`daysToRun < 0` 时恒 `false`，常驻。
- `REGISTER_MARK`：persistent data 守卫键（`"GearIn_DayAccumulatorScript"`），**必须**与调用点片段里的键逐字一致（与模式 A 各自独立，互不复用）。
- `convertToDays(amount)`：`amount` 是上一帧真实秒数，`clock.convertToDays(amount)` 才是本帧折合的游戏天数 —— 本类 `advance` 第一行就是换算。
- `daysAccumulator` / `while`（模式 B）：累计满 `tickEveryDays` 个游戏天触发一次例程（日志 `GearIn daily: routine #N ...`），余数跨帧保留；单帧跨多个游戏日时逐日补触发。
- `daysSinceMount`：挂载以来的精确游戏天数，兼作 `daysToRun` 自停判据与日志统计；全部字段（`int` / `float` / `boolean`）都是可随存档序列化的基础类型，读档后从存档值继续。

调用点（工程片段 —— 方法所在类归 C06-S02 所有，本节只演示调用，不改动那些文件）：

```java
    // onGameLoad(boolean newGame) 内新增本节注册块（GearInModPlugin 归 C06-S02 所有）。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInCalendarDayScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCalendarDayScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCalendarDayScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInDayAccumulatorScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInDayAccumulatorScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInDayAccumulatorScript());
    }
    // 可选冗余查重（C06-S02 建议：hasScript 与 persistent data 门互为冗余）：
    if (!Global.getSector().hasScript(GearInCalendarDayScript.class)) {
        Global.getSector().addScript(new GearInCalendarDayScript());
    }
    if (!Global.getSector().hasScript(GearInDayAccumulatorScript.class)) {
        Global.getSector().addScript(new GearInDayAccumulatorScript());
    }
```

（守卫段与查重段只取一段即满足 "不重复注册"；两段同留时查重段会在守卫段之后补注册，不会叠加 —— 但演示时建议只保留守卫段，避免混淆。）

::: caution 每个注册项必须用各自的 persistent data 键守卫

本节两个类各持一个 `REGISTER_MARK`，且不要挂进插件已有的 `CLASS_MARK` 守卫（该键在新档首次进入时就已置位，守卫内的注册对旧档永远不会再执行）。

:::

想演示周期与自停，在注册前先设参数（模式 B 专用；模式 A 无参数）：

```java
    GearInDayAccumulatorScript script = new GearInDayAccumulatorScript();
    script.setTickEveryDays(1);  // 每 1 个游戏日一次例程（默认）
    script.setDaysToRun(30);     // 约 30 个游戏日（约 300 真实秒）后自停
    Global.getSector().addScript(script);
```

::: tip 演示周期与自停

先 `setTickEveryDays` / `setDaysToRun` 再 `addScript`；该实例绕过守卫仅用于演示，看完记得移除，避免与常驻实例的日志混淆。

:::

构建命令（在 `Starsector\mods\GearIndustry\` 执行，沿用 C06-S01）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/` 下两个脚本类的 class 条目（可用 `jar tf` 核对）。

本节两段源码与 fixture 逐字一致（fixture 位于 `tutorial-fixtures/units/C10-S01/src/main/java/data/scripts/gearin/campaign/`），fixture 编译验收命令见 "预期结果"。

### 验证方案（基于本例）

- 前置数据：已有 MOD 布局（C06-S01）与 `GearInModPlugin`（C06-S02，含本节调用点片段）、两个脚本类。
- 操作 1（fixture 编译）：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC10s01Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
- 操作 2（游戏构建）：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含两个脚本类的 class 条目。
- 操作 3（新建生涯）：确认 `starsector.log` 出现 `GearIn calendar: new day ...` 与 `GearIn daily: routine #1 ...`，随后每 10 真实秒各出现一次；启动与游玩全程无 `gear_in` 引起的 `ERROR`。
- 操作 4（暂停）：生涯中按空格暂停，等待 20 秒以上 —— 无新日志；恢复后继续（预期结果第 4 条）。
- 操作 5（加速）：按住 Shift 约 10 秒，确认日志频率约翻倍（预期结果第 5 条）。
- 操作 6（读档）：保存后读档，确认无重复注册、`routine #` 与 `since mount` 从存档累计值继续（预期结果第 6 条）。
- 操作 7（战斗）：进入任意遭遇战，战斗期间无日志；回生涯后继续（预期结果第 7 条）。
- 操作 8（故障注入 1）：注释掉调用点片段的 `addScript` 行，重启新档：无任何日志（对应故障分析第 1 条）；改回后确认恢复。
- 操作 9（故障注入 2）：注释掉两个 `put(REGISTER_MARK, true)` 守卫调用（保留 `addScript`），读档两次：日志与例程逐次叠加（对应故障分析第 9 条）；改回后确认恢复。
- 操作 10（参数演示）：把 `daysToRun` 设为 30 重新构建：挂载约 300 真实秒后出现 `done` 日志并停止（对应预期结果第 8 条）；改回 -1 恢复常驻。
- 通过信号：操作 1–8 全部符合；操作 9、10 的注入与恢复都符合预期。
- 清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行

   ```text
   gradle -p tutorial-fixtures compileC10s01Java --no-daemon
   ```

   以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInCalendarDayScript` 与 `GearInDayAccumulatorScript` 两个脚本类的 class 条目。
3. **新建生涯**：`starsector.log` 出现

   ```text
   GearIn calendar: new day 206.3.3 GearIn daily: routine #1 at 206.3.3 | since mount=1.0 game days
   ```

   随后**每 10 真实秒**（未暂停、未加速）各出现一次：`GearIn calendar: new day ...`（模式 A，日期逐日前进）与 `GearIn daily: routine #N ...`（模式 B， `since mount` 逐日 +1.0）。挂载点约为 206.3.2（`onGameLoad` 时新档时间推进约 60.2 游戏日已结束）。
4. **暂停（空格）**：日志停止（脚本冻结、时钟冻结）；恢复后继续，`since mount` 不因暂停丢失进度。
5. **加速（按住 Shift）**：日志频率约翻倍（`campaignSpeedupMult = 2`，每帧推进两次），约每 5 真实秒各一次。
6. **读档**：不重复注册（两个 `REGISTER_MARK` 守卫已置位、`hasScript` 命中）；`since mount` / `routine #` 从存档时的累计值继续（字段随档），不出现新的 `routine #1`。
7. **战斗**：进入战斗期间无任何日志（生涯引擎冻结）；回生涯后继续。
8. **`daysToRun = 30`**：挂载约 300 真实秒后出现

   ```text
   GearIn daily: done, isDone()=true after 30.0 game days
   ```

   之后不再有日志 —— 引擎在置位后的下一帧从 `scripts` 列表移除了该脚本（可验证 `Global.getSector().hasScript(GearInDayAccumulatorScript.class)` 为 `false`）。
9. **退出到主菜单**：脚本停止推进（生涯状态不再运行）；回到主菜单后再进档（读档）按第 6 条继续。

静态自查：两个脚本类与 fixture 逐字一致（包括 import 顺序）；各自的 `REGISTER_MARK` 与调用点片段的键逐字一致；没有把工作单元编号写进任何运行时名称（类名、包名、persistent data 键均不含 C10-S01 变体）。

## 实例驱动故障分析

1. **脚本完全没有日志**。
   - 触发：忘了调用 `addScript`，或把注册写进 `onApplicationLoad`（此刻 `Global.getSector()` 为 null，解引用即 NPE）。
   - 可观察：前者无任何 `GearIn calendar:` / `GearIn daily:` 日志且无报错；后者启动中止、日志 NPE 堆栈指向 `onApplicationLoad`。
   - 修正：注册放 `onGameLoad` / `onNewGame` 守卫块内（C06-S03 的 null 语义）。
2. **把 `amount` 当游戏天数用**。
   - 触发：忽略接口 javadoc（"Use SectorAPI.getClock() to convert to campaign days"）。
   - 可观察：一切按天数计算的逻辑比真实快 10 倍（10 真实秒被当成 10 游戏天；`daysToRun = 30` 的脚本 30 真实秒就自停）。
   - 修正：一律 `clock.convertToDays(amount)`—— 本节示例 `advance` 第一行就是换算。
3. **`isDone()` 恒返回 true**。
   - 触发：完成标记写反或占位返回 `true`。
   - 可观察：引擎第一帧先查 `isDone` 就移除，`advance` 一次都不执行、连日志都没有。
   - 修正：初始 `false`，仅完成条件满足时置 `true`。
4. **`isDone()` 恒 false 且想用 `removeScript` 移除别的实例**。
   - 触发：把 `removeScript` 用在 "等价但不同实例" 上。
   - 可观察：引擎按**对象同一性**移除，不同实例删不掉，日志持续；`removeScriptsOfClass` 又只按**精确类**删（子类不算）。
   - 修正：移除用注册时的同一实例，或改 `removeScriptsOfClass`；常驻脚本想自停就置 `done = true` 让引擎自动清理。
5. **`runWhilePaused()` 语义误用**。
   - 触发：误以为 "返回 true 就能让时钟在暂停时前进"。
   - 可观察：`runWhilePaused = true` 时暂停期间 `advance` 仍被调用（`amount` 仍是真实秒数），但 `getClock()` 的日期与时间戳**不变**。以 `amount` 自累计的逻辑在暂停时继续走、以时钟为准的逻辑冻结，两者分道扬镳。
   - 修正：暂停敏感的进度用时钟或 `convertToDays` 累加（两者都随暂停冻结）；必须暂停期间推进的（如 UI 覆盖动画）才用 `runWhilePaused = true`，且不依赖时钟。
6. **跨日检测漏触发**。
   - 触发：单帧跨多个游戏日（devMode 10x/100x 调试键、MOD 自定义大步长推进）时，`dateKey` 一帧只比较一次。
   - 可观察：模式 A 在跨多天的帧里只报一次 "新的一天"，中间的日子没有日志。
   - 修正：日历事件一帧至多一次是正常行为；需要逐日补触发的逻辑用模式 B 的 `while` 累加器。
7. **脚本字段放了引擎单例/UI 引用**。
   - 触发：把 `getCampaignUI()`、声音播放器等存进脚本字段（脚本字段会随存档一起序列化）。
   - 可观察：存档文件异常膨胀、保存/读档异常，或读档后字段指向失效对象导致行为错乱。
   - 修正：字段只放可序列化状态；瞬态句柄用 `transient` 字段 + `readResolve()` 重建；跨会话小数据放 `getPersistentData()`。
8. **transient 脚本读档后消失**。
   - 触发：用 `addTransientScript` 注册常驻逻辑，没在读档回调里重新注册。
   - 可观察：读档后逻辑静默缺失（`transientScripts` 被 `readResolve` 清空）。
   - 修正：常驻逻辑用 `addScript`；`addTransientScript` 只用于纯临时推进逻辑并在读档回调重挂。
9. **旧档永远不注册脚本**。
   - 触发：把注册放进既有 `CLASS_MARK` 守卫内（C06-S02 的 persistent data 守卫模式；该键在新档首次进入时就已置位）。
   - 可观察：旧档读档后无任何日志、脚本缺失且无报错。
   - 修正：每个注册项用各自的 persistent data 键（本节 `REGISTER_MARK`），旧档缺键 = 视为首次注册，正好覆盖 "中途加脚本" 的场景。
10. **暂停时等待时钟推进的倒计时永不触发**。
   - 触发：脚本用 `clock.getElapsedDaysSince(ts) >= N` 做倒计时，又没改 `runWhilePaused`。
   - 可观察：暂停期间 `since mount` 冻结、`done` 永不置位，恢复后继续。
   - 修正：这是设计行为（暂停 = 停表）；需要真实时间倒计时就自己累计 `amount`。
11. **战斗期间脚本 "消失"**。
   - 触发：在战斗里等生涯日志。
   - 可观察：战斗期间无任何日志、时钟冻结；回生涯后日志继续。
   - 修正：无需修复 —— 生涯推进循环只在生涯状态下运行，战斗域由战斗状态接管；生涯脚本天然不跨战斗推进。

## 总结

本节把 `EveryFrameScript` 从 "会用" 推进到 "讲透"：

- **amount 约定**（真实秒 → `convertToDays` 换算；加速时同帧多次调用、每次都是完整帧秒；暂停时默认不推进；新档推进只影响 `onNewGame` 注册的脚本；战斗期冻结）
- **isDone / runWhilePaused 执行约定**（每帧两查、快照遍历、`hasScript` 用 `isInstance`、`removeScriptsOfClass` 用精确类、 `addScript` 不去重）
- **时间换算内部**（整游戏秒截断 → 日历与累加器长会话漂移； `getElapsedDaysSince(0)` 返回 `Float.MAX_VALUE`）
- **序列化边界**（`scripts` 随档、`transientScripts` 读档清空、字段必须可序列化、 `readResolve()` 恢复模式）
- **每日/周期脚本模式**（日历跨日 / 天数累加 / 间隔器 / 自注册倒计时，本节以两个小类分别演示前两种）。

记忆要点：

- `advance(float amount)` 的 `amount` 是真实秒；**必须**经 `getClock().convertToDays(amount)` 换成游戏天。
- 日历以整游戏秒前进、累加器保留小数 —— 短会话一致、长会话漂移；日历事件用跨日检测、精确统计用累加器。
- `scripts` 随存档保存、`transientScripts` 不保存且读档后必然清空；一次性注册必须用 persistent data 守卫，脚本字段必须可序列化。
- 暂停 = 停表：时钟、日历、`convertToDays` 累加全部冻结；真实时间倒计时只能靠 `amount` 累计。
- `isFastForwardIteration()` 可探测加速迭代（`campaignSpeedupMult = 2`）。

再往深处看，本节的全部机制都以 "时间推进" 为驱动轴：脚本在每帧被唤醒、按时钟与累加器决策、随存档往返。

同一套生涯对象还有另一条驱动轴 —— 事件在状态变化时被同步唤醒（监听器与事件派发）。

两条轴的推进时机、存档边界与生命周期清理如何对齐，是后续内容的自然落点；本节的 persistent data 守卫与阶段语义则始终是贯穿各页的底座。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：生涯按帧脚本的三方法约定（`isDone` / `runWhilePaused` / `advance`）。
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：实体级脚本的清理钩子（附着实体从生涯引擎移除时调用）。
- `com.fs.starfarer.api.campaign.SectorAPI`：脚本注册、查重与 persistent data 入口（`addScript` / `addTransientScript` / `hasScript` / `getScripts` / `getPersistentData` / `isFastForwardIteration`）。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：时间换算与日历接口（`convertToDays` / `convertToMonths` / `convertToSeconds` / `getTimestamp` / `getElapsedDaysSince` / `createClock`）。
- `com.fs.starfarer.api.util.IntervalUtil`：公开间隔器（`advance` 每帧调用、`intervalElapsed` 仅真一次）。
- `data/config/settings.json`：`campaignSpeedupMult`（加速倍率，RC8 = 2）。
- `tutorial-fixtures/units/C10-S01/src/main/java/data/scripts/gearin/campaign/`：本节两个脚本类（`GearInCalendarDayScript`、`GearInDayAccumulatorScript`）的 fixture 位置。
- `data/scripts/gearin/campaign/`：示例 MOD 内两个脚本类的源码目录。
