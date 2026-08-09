# 制作可保存进度的生涯脚本

本节制作能够保存进度的每日与周期生涯脚本。脚本会按游戏时间推进并随存档序列化，日志用于确认注册、时间换算和读档后的进度。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `advance(float amount)` 的 `amount` 语义，并说出正常帧、暂停、加速、新档推进、战斗五种时序下引擎如何调用脚本与时钟；
- 解释 `isDone()` 与 `runWhilePaused()` 的引擎执行约定 (每帧最多两查、清理入口、脚本基类默认值、`EveryFrameScriptWithCleanup` 的边界)，并用 `isDone()` 控制常驻脚本自停；
- 解释 `CampaignClockAPI` 的内部换算 (10 真实秒 = 1 游戏日、整游戏秒截断、时间戳与 `getElapsedDaysSince`)，并区分以日历为准与以累加器为准两类时间判断的适用场景；
- 解释存档序列化边界：`scripts` 随存档保存、`transientScripts` 读档后必然清空、脚本字段的 XStream 可序列化要求与 `readResolve()` 恢复模式；
- 编写并注册每日/周期脚本 (本节以 `GearInCalendarDayScript` 演示模式 A、`GearInDayAccumulatorScript` 演示模式 B)，并在四种周期模式 (日历跨日、天数累加、间隔器、自注册倒计时) 之间做选择；
- 从症状定位十二类典型故障。

## 前置知识

- [C6-S2] 的 `modPlugin` 生命周期与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义与生涯引擎建立边界；
- [C7-S1] 的战斗域每帧回调、暂停语义与生涯域的差异；
- [C9-S1] 的星区结构、生涯时钟、`EveryFrameScript` 注册、推进循环、暂停与加速语义。

## 梳理生涯脚本关系

脚本要能稳定运行，先得看清引擎的四个约定：每帧怎么调脚本、`amount` 是什么、时间怎么换算、进度存在哪里。本节依次讲完这四个机制，再据此给出四种实现模式。

### EveryFrameScript 三方法约定速查表

`EveryFrameScript`(`com.fs.starfarer.api.EveryFrameScript`) 只有三个方法，是生涯按帧脚本的完整约定。此前已讲过三方法是什么，本节讲引擎到底怎么用它们：

| 方法 | 引擎执行约定 |
|---|---|
| `boolean isDone()` | 每帧**最多查两次**：`advance` 前查一次 —— 为 `true` 直接移除 (不调 `advance`)；`advance` 返回后再查一次 —— 为 `true` 移除。常驻脚本返回 `false` |
| `boolean runWhilePaused()` | 暂停时 `advance` 只对返回 `true` 的脚本调用；脚本基类默认 `false` |
| `void advance(float amount)` | `amount` 是**上一帧经过的真实秒数** ("seconds elapsed during the last frame"；"Use SectorAPI.getClock() to convert to campaign days")。**必须**经时钟换算才是游戏天 |

执行细节：

1. 引擎遍历的是**列表快照** (`new ArrayList<>(this.scripts)`)，脚本在 `advance` 里 `addScript` / `removeScript` 安全；代价是 advance 里新加的脚本下一帧才推进。
2. 移除是 "遍历快照、改原列表"：
    - `isDone` 自动清理；
    - `removeScript(script)` 按**对象同一性**；
    - `removeScriptsOfClass(Class)` 按**精确类** `getClass() == clazz`；
    - `hasScript(Class)` 用 `isInstance`—— 查重用 `hasScript`，批量删用 `removeScriptsOfClass`，别混用。
3. **`addScript` 不去重**：同一实例或等价实例重复注册会各自推进 —— 一次性注册必须用 persistent data 标记 (必须遵守的规则，见 "注册与存档" 节)。
4. 脚本基类把 `runWhilePaused()` 默认设为 `false`；引擎内置脚本也走同一机制。
5. `EveryFrameScriptWithCleanup`(`com.fs.starfarer.api.EveryFrameScriptWithCleanup`) 只多一个 `cleanup()`："附着实体从生涯引擎移除时调用"(实体级脚本；引擎级脚本不触发)。该接口没有可观测的调用点，行为表述即上述语义。

### amount 五种时序取值速查表

方法约定说明脚本自身怎么写，时序约定决定引擎每次怎么调、传多少 `amount`。引擎在五种时序下推进脚本与时钟的方式各不相同：

| 时序 | 引擎行为 | 脚本与时钟后果 |
|---|---|---|
| 正常帧 | 每渲染帧调一次 `engine.advance(amount)`，`amount` 是该帧真实秒数 | 脚本 `advance(amount)` 每帧一次，`amount` = 帧真实秒；时钟按 10 秒/游戏日前进 |
| 暂停 (空格) | 引擎照常每帧调 `advance`，但暂停时只有 `runWhilePaused()` 为 `true` 的脚本被推进 | 时钟冻结；`runWhilePaused()` 为 `false` 的脚本一并停住，为 `true` 的脚本继续收到 `amount` |
| 加速 (按住 Shift) | `fastForward` 时按 `round(campaignSpeedupMult)`(`settings.json` 的 `campaignSpeedupMult`，取值为 2) 的次数，同一渲染帧连调 `engine.advance(amount)`，每次 `amount` 都是完整帧秒 | 脚本与时钟同帧推进两次，游戏日流速翻倍；`Global.getSector().isFastForwardIteration()` 可探测 "本次调用来自加速帧的第 2 次及以后的迭代"(`SectorAPI.isFastForwardIteration()`；第 1 次连调时标志尚未置位)。devMode 下还有 10x/100x 调试键 |
| 新档推进 | 新建生涯时有一段快速推进窗口 (`isInFastAdvance()`)，窗口内时钟与脚本连续推进，之后才调 `onNewGameAfterTimePass` 与 `onGameLoad(true)`(游戏管理器) | `onNewGame` 注册的脚本经历这次推进；`onGameLoad` 里注册的脚本**不**经历 (窗口已关闭) |
| 战斗 | 进入战斗后运行的是战斗状态，生涯状态不再推进 | 生涯脚本、时钟、经济全部冻结，回生涯后恢复 |

### 时间换算速查表

`CampaignClockAPI`(`com.fs.starfarer.api.campaign.CampaignClockAPI`) 由引擎内部实现。此前已给出换算基线，本节把高频换算方法收成速查表；换算的**内部机制**与两个推论列在表格之后 (签名出处见文末建议参考项)：

| 方法 | 返回 | 语义 |
|---|---|---|
| `convertToDays(amount)` | `float` | 真实秒 → 游戏天 (`advance` 里**必须**用它换算) |
| `convertToSeconds(days)` | `float` | 游戏天 → 真实秒 |
| `convertToMonths(amount)` | `float` | 真实秒 → 游戏月 (÷10÷30) |
| `getTimestamp()` | `long` | 日历毫秒；暂停时冻结；跨存档安全的间隔基准 |
| `getElapsedDaysSince(ts)` | `float` | 从 `ts` 到现在的游戏天数；`ts == 0` 返回 `Float.MAX_VALUE` |
| `createClock(ts)` | `CampaignClockAPI` | 按时间戳建独立时钟 (局部计时) |
| `isFastForwardIteration()` | `boolean` | 本次推进来自加速帧的第 2 次及以后的迭代 (第 1 次连调时为 false；`SectorAPI.isFastForwardIteration()`) |

- **基线：10 真实秒 = 1 游戏日** (`SECONDS_PER_GAME_DAY = 10.0F`)。`convertToDays(秒)` = 秒 ÷ 10；`convertToSeconds(天)` = 天 × 10；`convertToMonths(秒)` = 秒 ÷ 10 ÷ 30。
- **日历按整游戏秒推进**：`advance(float)` 的实作是 `(int)(秒 / secondsPerDay * 86400)`，把整游戏秒加进日历；每帧独立截断，余数不结转。
    - 推论一：`getDay()` / `getDateString()` / `getTimestamp()` 全部来自这个日历，因此是整秒粒度；而 `convertToDays(amount)` 的累加保留小数。
    - 推论二：长期漂移等于各帧被截去的小数游戏秒之和，取决于实际帧时分布；帧率本身不能确定漂移量或趋势。
    - 结论：**以日历为准 (跨日检测) 与以累加器为准 (`convertToDays` 累加) 是两种不同的时间基准**：短会话一致、长会话错位；统计型进度用累加器，日历事件用跨日检测。
- **`getElapsedDaysSince(ts)`**：`(now - ts) / 86400000`，返回游戏天数；`ts == 0` 时返回 `Float.MAX_VALUE` —— 计算未初始化间隔时不要传 0。
- **`getTimestamp()`** 是日历毫秒值，暂停时冻结；`createClock(ts)` 按时间戳建独立时钟做局部计时。
- **读档重建**：`readResolve()` 按保存的时间戳重建内部日历，读档后时钟从存档时刻继续 —— 您在读档前度过的真实时间，游戏日历一概不认。

两种基准的分工

日历以整游戏秒前进，适合 "某一天做什么" 这类事件判定；`convertToDays` 累加保留小数、每帧追加，适合跨帧累计的统计。长会话里两者错位不是 bug，而是按需选基准的设计：日历事件用跨日检测、精确统计用累加器。

### 注册与存档：序列化边界

时间换算解决怎么算时间，序列化解决算出来的进度怎么保留。

**保存与读档的机制** (游戏管理器)：

- 保存：`saveGame(...)` 用 XStream 序列化**整个生涯引擎 (星区)**—— 包括 `scripts` 列表里每个脚本的全部非 `transient` 字段。
- 读档：`fromXML` 还原生涯引擎 (星区) 与脚本字段，脚本状态从存档值继续。

**两个脚本列表的序列化边界** (生涯引擎)：

- `scripts` **非** `transient` → **随存档保存**：常驻脚本 (管理器、周期脚本) 跨读档继续运行，其非 `transient` 字段原样恢复；`readResolve` 只补 null 备用值。
- `transientScripts` 是 `transient` → **不随存档保存**，且 `readResolve` 直接重建空列表 → **transient 脚本读档后必然消失，需要重新注册**。
- 实体级脚本：实体的脚本列表也非 `transient` → 随实体保存。

由此得到三条必须遵守的规则与两条建议：

- **必须**：脚本字段只放可随 XStream 序列化的状态 (基础类型、可序列化对象)。
    - 把引擎单例、`CampaignUIAPI`、声音播放器等句柄存进字段，会把整个可达对象图拖进存档 —— 可观察结果：存档文件异常膨胀、保存/读档异常，或读档后字段指向失效对象导致行为错乱。
- **必须**：一次性注册用随存档保存的 persistent data 标记 ([C6-S2] 的 `CLASS_MARK` 模式；本节示例用各自键 `REGISTER_MARK`)——`addScript` 不去重，标记缺失的可观察结果是读档次数越多日志越成双、行为叠加。
- **必须**：transient 脚本的重新注册写在读档回调里，否则可观察结果是读档后脚本消失且无报错。
- **建议**：跨会话的小数据放 `SectorAPI.getPersistentData()`(persistent data 随档保存；注意 XStream 存档边界)。
- **建议**：脚本内瞬态句柄 (贴图、粒子、UI 引用) 声明为 `transient` 字段并用 `readResolve()` 重建 —— 成熟实例：实体插件用 `readResolve()` 恢复 `SpriteAPI` 句柄。

### 每日/周期脚本模式速查表

机制已经齐备，剩下的是把它们组合成完整写法。本节把按时间反复执行的生涯脚本分成两类：以游戏日为最小时间颗粒、每个游戏日执行一次 (或每 N 个游戏日一次) 的每日脚本 (daily script)，以及按固定或随机间隔反复执行某段逻辑的周期脚本 (periodic script)。判断新的一天有两种基准：日历日期变化，或游戏天累加器跨过整数；间隔单位可以是真实秒 (间隔器直接推进) 或游戏天 (先 `convertToDays` 再推进)。四种经过验证的模式 (判据：原版内部实现与成熟实例模组) 如下：

| 模式 | 实现要点 | 适用场景 | 成熟实例 |
|---|---|---|---|
| A. 日历跨日检测 | `dateKey = cycle*10000 + month*100 + day`(示例自用辅助键，非原版机制)，与上次比较，变化即 "新的一天" | 日历事件 (征兵、账单、节日)；每游戏日恰好一次 | 本节 `GearInCalendarDayScript`(常驻跨日检测) |
| B. 天数累加器 | `acc += clock.convertToDays(amount)`，`while (acc >= period) { acc -= period; ... }` | 精确天数统计；单帧跨多天也要逐日补触发 | 本节 `GearInDayAccumulatorScript`(含 `daysToRun` 自停演示) |
| C. 间隔器 | `IntervalUtil`(公开 API `com.fs.starfarer.api.util.IntervalUtil`) 每帧 `advance`、`intervalElapsed()` 判定；原版游戏内置的同类间隔计时用法一致 | 固定/随机间隔的周期任务；喂秒就是真实时间周期，喂 `convertToDays(amount)` 就是游戏天周期 | 原版游戏的事故判定 (按游戏天在 0.5–1.5 天区间内判定) |
| D. 自注册倒计时 | 构造器 `Global.getSector().addScript(this)` 自注册，`isDone()` 完成即被引擎清理；倒计时按天扣减 (`isDay` 模式先把 `amount` 换成天) | 一次性延迟任务 (剧情事件、临时效果)；少一处调用点 | — |

选型建议：日历事件用模式 A；需要精确累计 (含小数) 或单帧可能跨多天时用模式 B (用 `while` 而不是 `if`)；固定/随机真实时间节奏用模式 C；一次性倒计时任务用模式 D。模式 A 与 B 混用时，注意时间换算一节的漂移结论。

## 实现可保存生涯脚本

先让脚本只执行并保存，再加入时间判断。这样可以分别验证注册、换算与停止条件，避免把 "没有注册" 和 "周期尚未到" 混成同一种无日志症状。

### 注册常驻脚本

把以下判断放入 `GearInModPlugin.onGameLoad()`。标记与脚本列表都随存档保存，因此新档和读档共用同一个一次性门。

```java
SectorAPI sector = Global.getSector();
if (!sector.getPersistentData().containsKey(GearInCalendarDayScript.REGISTER_MARK)) {
    sector.getPersistentData().put(GearInCalendarDayScript.REGISTER_MARK, true);
    sector.addScript(new GearInCalendarDayScript());
}
```

完成信号：首次进入存档时 `hasScript(GearInCalendarDayScript.class)` 为 `true`；保存并读档后，脚本实例数量仍为 1。

### 检测日历跨日

在 `GearInCalendarDayScript.advance()` 中把年月日折叠为键。首帧只建立基线，后续日期变化才执行事件。

```java
CampaignClockAPI clock = Global.getSector().getClock();
int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
if (dateKey != lastDateKey) {
    if (lastDateKey != -1) {
        LOG.info("GearIn calendar: new day " + clock.getShortDate());
    }
    lastDateKey = dateKey;
}
```

完成信号：正常速度下约每 10 真实秒出现一次新日期；暂停期间日期与日志同时冻结。

### 把帧时间换成游戏天

在 `GearInDayAccumulatorScript.advance()` 开头换算 `amount`。这条路径保留小数余量，适合统计累计进度。

```java
CampaignClockAPI clock = Global.getSector().getClock();
float daysThisFrame = clock.convertToDays(amount);
daysSinceMount += daysThisFrame;
daysAccumulator += daysThisFrame;
```

完成信号：正常速度运行约 10 真实秒后，`daysSinceMount` 增加约 1.0；按住 Shift 时增长速度约翻倍。

### 补齐跨帧周期

继续在同一 `advance()` 中使用 `while` 消耗累加值。若调试推进一次跨过多天，循环会补齐每一个完整周期。

```java
while (daysAccumulator >= tickEveryDays) {
    daysAccumulator -= tickEveryDays;
    routineCount++;
    LOG.info(String.format("GearIn daily: routine #%d at %s",
            routineCount, clock.getShortDate()));
}
```

完成信号：把 `tickEveryDays` 设为 1 后，每累计一个完整游戏日增加一次 `routineCount`，单帧跨过 3 天时连续增加 3 次。

### 让脚本按条件自停

在 `advance()` 末尾设置完成标记，并让 `isDone()` 返回该字段。引擎会在回调返回后再次检查并移除脚本。

```java
if (daysToRun >= 0 && daysSinceMount >= daysToRun) {
    done = true;
}

@Override
public boolean isDone() {
    return done;
}
```

完成信号：`daysToRun=30` 时累计满约 30 个游戏日后，`hasScript(GearInDayAccumulatorScript.class)` 变为 `false`，日志不再增长。

## 组装生涯脚本实现

选型之后落到代码。在已有 MOD 布局 [C6-S1] 与 `GearInModPlugin`[C6-S2] 之上新增两个脚本类 (不新增任何资源、不新增任何注册项 —— 指不新增数据/资源注册项；脚本经 `onGameLoad` 注册属于代码注册)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInCalendarDayScript     # 模式 A：日历跨日检测 (本节新增)
│       └── GearInDayAccumulatorScript  # 模式 B：天数累加器 + daysToRun 自停 (本节新增)
└── jars\
```

### GearInCalendarDayScript：模式 A(日历跨日检测，常驻)

对应关系：

- `implements EveryFrameScript`：三个方法全来自接口 (`isDone` / `runWhilePaused` / `advance`)；`isDone()` 恒返回 `false`—— 常驻脚本永不结束，日历事件逐日触发。
- `REGISTER_MARK`：persistent data 注册标记的键 (`"GearIn_CalendarDayScript"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
- `dateKey` 跨日检测 (模式 A)：`getCycle() * 10000 + getMonth() * 100 + getDay()` 折叠日期为整数键；首帧只初始化不打印，之后每个游戏日打印一次 `GearIn calendar: new day ...`。
- 时间换算与逐日补触发不在本类：本模式只看日历日期、不直接用 `amount` —— 那是模式 B(`GearInDayAccumulatorScript`) 的职责；两个类可同档共存，各自用独立的 persistent data 标记注册。
- 字段只有 `int`(`lastDateKey`)，可随存档序列化；`runWhilePaused()` 恒 `false`(暂停时本脚本与时钟一并冻结)。

模式 A 在单帧跨多个游戏日 (加速、新档推进) 时一帧只报一次 "新的一天"

这是正常行为而非漏触发；需要逐日补触发的逻辑应改用模式 B 的 `while` 累加器。

模式 A 只回答是不是新的一天，回答不了累计了多少天；模式 B 用累加器补上这一半，并演示运行天数上限自停。

### GearInDayAccumulatorScript：模式 B(天数累加器 + 自停)

对应关系：

- `implements EveryFrameScript`：`isDone()` 返回字段 `done`—— 累计满 `daysToRun` 后引擎在 `advance` 返回后的同一次推进中移除本脚本 (每帧最多查两次 `isDone()`，见上)；`daysToRun < 0` 时恒 `false`，常驻。
- `REGISTER_MARK`：persistent data 注册标记的键 (`"GearIn_DayAccumulatorScript"`)，**必须**与调用点片段里的键逐字一致 (与模式 A 各自独立，互不复用)。
- `convertToDays(amount)`：`amount` 是上一帧真实秒数，`clock.convertToDays(amount)` 才是本帧折合的游戏天数 —— 本类 `advance` 第一行就是换算。
- `daysAccumulator` / `while`(模式 B)：累计满 `tickEveryDays` 个游戏天触发一次例程 (日志 `GearIn daily: routine #N ...`)，余数跨帧保留；单帧跨多个游戏日时逐日补触发。
- `daysSinceMount`：挂载以来的精确游戏天数，兼作 `daysToRun` 自停判据与日志统计；全部字段 (`int` / `float` / `boolean`) 都是可随存档序列化的基础类型，读档后从存档值继续。

调用点 (工程片段 —— 方法所在类归 [C6-S2] 所有，本节只演示调用，不改动那些文件)：

```java
    // onGameLoad(boolean newGame) 内新增本节注册块 (GearInModPlugin 归 C6-S2 所有)。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInCalendarDayScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCalendarDayScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCalendarDayScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInDayAccumulatorScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInDayAccumulatorScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInDayAccumulatorScript());
    }
    // 可选冗余查重 (C6-S2 建议：hasScript 与 persistent data 门互为冗余)：
    if (!Global.getSector().hasScript(GearInCalendarDayScript.class)) {
        Global.getSector().addScript(new GearInCalendarDayScript());
    }
    if (!Global.getSector().hasScript(GearInDayAccumulatorScript.class)) {
        Global.getSector().addScript(new GearInDayAccumulatorScript());
    }
```

(标记段与查重段只取一段即满足 "不重复注册"；两段同留时查重段会在标记段之后补注册，不会叠加 —— 但演示时建议只保留标记段，避免混淆。)

每个注册项必须用各自的 persistent data 键做标记

本节两个类各持一个 `REGISTER_MARK`，且不要并入插件已有的 `CLASS_MARK` 标记 (该键在新档首次进入时就已置位，标记内的注册对旧档永远不会再执行)。

想演示周期与自停，在注册前先设参数 (模式 B 专用；模式 A 无参数)：

```java
    GearInDayAccumulatorScript script = new GearInDayAccumulatorScript();
    script.setTickEveryDays(1);  // 每 1 个游戏日一次例程 (默认)
    script.setDaysToRun(30);     // 约 30 个游戏日 (约 300 真实秒) 后自停
    Global.getSector().addScript(script);
```

演示周期与自停

先 `setTickEveryDays` / `setDaysToRun` 再 `addScript`；该实例不经过 persistent data 标记，仅用于演示，看完记得移除，避免与常驻实例的日志混淆。

构建命令 (在 `Starsector\mods\GearIndustry\` 执行，沿用 [C6-S1])：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/` 下两个脚本类的 class 条目 (可用 `jar tf` 核对)。

### 关键故障实验：旧档读入后脚本重复注册

把注册状态放在 `ModPlugin` 实例字段中。**ModPlugin 实例由 `ModManager` 缓存，会话内保持同一实例**，读档回调仍见 `registered=true`，不会重复注册。**重复只发生在应用重启后**：新实例看到 `registered=false`，向已随档恢复的脚本列表追加第二个。

**局部故障片段：**

```java
// 故障片段
if (!registered) { sector.addScript(new GearInCalendarDayScript()); registered = true; }
```

**局部修正片段：**

```java
// 修正片段
if (!sector.getPersistentData().containsKey(REGISTER_MARK)) {
    sector.getPersistentData().put(REGISTER_MARK, true);
    sector.addScript(new GearInCalendarDayScript());
}
```

原因是实例字段的生命周期与存档内 `scripts` 列表不同，而 persistent data 与脚本列表一起随档恢复。复验：记录新档中的脚本数量，保存并连续读档两次；每次都应保持 1 个实例，同一游戏日只出现一条日历日志。

## 验证方案

前置数据：已有 MOD 布局 [C6-S1]、`GearInModPlugin` ([C6-S2]，含本节调用点片段) 与两个脚本类。

1. 构建游戏 MOD：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`，并核对 `jar tf jars\GearIndustry.jar` 含两个脚本类的 class 条目。
2. 新建生涯，在 `starsector.log` 中观察两个脚本的周期记录。
3. 暂停与加速对照：生涯中按空格暂停 20 秒以上，再按住 Shift 约 10 秒。
4. 保存并读档：确认注册不重复、累计值从存档继续。
5. 进入任意遭遇战再返回：确认战斗期间日志停止、回生涯后继续。
6. 自停演示：把 `daysToRun` 设为 30 重新构建，挂载约 300 真实秒。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInCalendarDayScript` 与 `GearInDayAccumulatorScript` 两个脚本类的 class 条目。
- **新建生涯**：`starsector.log` 出现 `GearIn calendar: new day 206.3.3` 与 `GearIn daily: routine #1 at 206.3.3 | since mount=1.0 game days`；随后每 10 真实秒 (未暂停、未加速) 各出现一次：`GearIn calendar: new day ...` (模式 A，日期逐日前进) 与 `GearIn daily: routine #N ...` (模式 B，`since mount` 逐日 +1.0)。挂载点约为 206.3.2 (新档初始 206.1.1，推进窗口 60.2 游戏日)。
- **暂停 (空格)**：等待 20 秒以上无新日志 (脚本冻结、时钟冻结)；恢复后继续，`since mount` 不因暂停丢失进度。
- **加速 (按住 Shift)**：日志频率约翻倍 (`campaignSpeedupMult = 2`，每帧推进两次)，约每 5 真实秒各一次。
- **读档**：不重复注册 (两个 `REGISTER_MARK` 标记已置位、`hasScript` 命中)；`since mount` / `routine #` 从存档时的累计值继续 (字段随档)，不出现新的 `routine #1`。
- **战斗**：进入战斗期间无任何日志 (生涯引擎冻结)；回生涯后继续。
- **自停 (`daysToRun = 30`)**：挂载约 300 真实秒后出现 `GearIn daily: done, isDone()=true after 30.0 game days`；之后不再有日志 —— 引擎在置位后的同一帧从 `scripts` 列表移除了该脚本 (可验证 `Global.getSector().hasScript(GearInDayAccumulatorScript.class)` 为 `false`)。
- **退出到主菜单**：脚本停止推进 (生涯状态不再运行)；回到主菜单后再进档 (读档)，行为与读档一行相同。
- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

清理：把 `daysToRun` 恢复为 `-1`，使脚本保持常驻。

## 故障分析

- **脚本没有产生任何日志**：脚本没有通过 `addScript` 注册。把注册放在 `onGameLoad` 或 `onNewGame` 的独立持久标记块中，重新载入存档后确认首条推进日志出现。
- **`onApplicationLoad` 抛出空指针异常**：该阶段尚无生涯 sector，注册代码解引用了 `Global.getSector()`。将生涯注册移到游戏载入回调，重启后确认应用加载完成。
- **按天计算的逻辑快了约 10 倍**：代码直接把 `advance` 的 `amount` 当作游戏天数。先调用 `clock.convertToDays(amount)`，再运行相同时间并比较游戏日期。
- **`advance` 从未执行**：`isDone()` 初始就返回 true，引擎在推进前移除了脚本。初始返回 false，只在完成条件成立时置位；重新注册后检查推进日志。
- **调用 `removeScript` 后日志仍在继续**：传入对象不是注册时的同一实例。保存并移除原实例，或按精确类调用 `removeScriptsOfClass`；下一帧确认日志停止。
- **暂停后两种计时结果分离**：`runWhilePaused()` 允许 `advance` 继续收到真实秒数，但生涯时钟保持冻结。暂停敏感逻辑使用时钟换算，暂停期间必须推进的界面逻辑才累计原始 `amount`；暂停一次比较两侧结果。
- **单帧跨越多天却只触发一次**：日期键每帧只比较一次，不会补齐中间日期。需要逐日执行时改用带 `while` 的天数累加器，再用加速推进复验触发次数。
- **保存或读档时脚本字段报错**：脚本保存了 UI、播放器等引擎句柄。字段只保留可序列化状态，瞬态句柄标记为 `transient` 并在读档后重建；完成一次保存与读档测试。
- **读档后 transient 脚本消失**：`addTransientScript` 注册项不会随存档恢复。常驻逻辑改用 `addScript`，临时逻辑则在读档回调重新注册；读档后确认脚本列表和日志恢复。
- **旧存档始终没有新脚本**：注册复用了已经置位的其他功能标记。为该脚本使用独立的 `REGISTER_MARK`，载入旧档后确认标记和脚本同时存在。
- **暂停期间倒计时停住**：倒计时读取生涯时钟，暂停时该时钟不会前进。需要真实时间倒计时就累计 `amount`；暂停到阈值后确认回调触发。
- **进入战斗后生涯脚本停止输出**：生涯推进循环在战斗期间暂停。返回生涯后确认日志继续；战斗期逻辑应由战斗插件承担。

## 总结

本节把 `EveryFrameScript` 从会用推进到讲透，全部机制围绕一条主线：脚本在每帧被唤醒、按时钟与累加器决策、随存档往返。排错时只需核对三个问题：脚本注册了吗 (persistent data 标记是否置位)、换算对了吗 (`amount` 是否经 `convertToDays`)、字段能随档吗 (是否全部可序列化)。三个答案定位完毕，剩下的通常只是时序或数值问题。

值得记住的判断：暂停就是停表，时钟、日历与 `convertToDays` 累加全部冻结，真实时间倒计时只能靠 `amount` 累计；日历按整游戏秒前进、累加器保留小数，短会话一致、长会话漂移，日历事件用跨日检测、精确统计用累加器；`scripts` 随存档保存、`transientScripts` 读档后必然清空，一次性注册必须用 persistent data 标记；`isFastForwardIteration()` 可探测加速帧的第 2 次及以后的迭代 (`campaignSpeedupMult = 2`)。

同一套生涯对象还有另一条驱动轴 —— 事件在状态变化时被同步唤醒 (监听器与事件派发)。两条轴的推进时机、存档边界与生命周期清理需要与本节的 persistent data 标记和阶段语义对齐。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：生涯按帧脚本的三方法约定 (`isDone` / `runWhilePaused` / `advance`)。
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：实体级脚本的清理钩子 (附着实体从生涯引擎移除时调用)。
- `com.fs.starfarer.api.campaign.SectorAPI`：脚本注册、查重与 persistent data 入口 (`addScript` / `addTransientScript` / `hasScript` / `getScripts` / `getPersistentData` / `isFastForwardIteration`)。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：时间换算与日历接口 (`convertToDays` / `convertToMonths` / `convertToSeconds` / `getTimestamp` / `getElapsedDaysSince` / `createClock`)。
- `com.fs.starfarer.api.util.IntervalUtil`：公开间隔器 (`advance` 每帧调用、`intervalElapsed` 仅真一次)。
- `data/config/settings.json`：`campaignSpeedupMult`(加速倍率，取值为 2)。
- `data/scripts/gearin/campaign/`：示例 MOD 内两个脚本类的源码目录。

## 完整最小例

::: details 完整成品：GearInCalendarDayScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 A：日历跨日检测。dateKey = cycle * 10000 + month * 100 + day，
 * 把日历日期折叠为整数键，与上次比较，变化即 "新的一天"；每个游戏日恰好触发一次
 * (首帧只初始化不打印)。单帧跨多个游戏日 (加速、新档推进) 时一帧只报一次 ——
 * 日历事件一帧至多一次是正常行为，需要逐日补触发的逻辑请改用天数累加器
 * (本单元 GearInDayAccumulatorScript)。runWhilePaused() 返回 false；字段只有
 * int，可随存档序列化 (XStream 存档边界)，脚本随存档保存、读档后继续。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，注册前用 persistent data
 * 键 REGISTER_MARK 做一次性门控 (C6-S2 的 CLASS_MARK 模式)，键与调用点片段逐字一致。
 */
public class GearInCalendarDayScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CalendarDayScript";

    private static final Logger LOG = Global.getLogger(GearInCalendarDayScript.class);

    /** 跨日判定键 (cycle * 10000 + month * 100 + day)；-1 表示尚未初始化。 */
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

        // 本模式只看日历日期，不直接用 amount(时间换算与逐日补触发见 GearInDayAccumulatorScript)。
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

:::

::: details 完整成品：GearInDayAccumulatorScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 B：天数累加器。amount 是上一帧真实秒数，必须经
 * clock.convertToDays(amount) 换算成游戏天 (10 真实秒 = 1 游戏日)；
 * 余数跨帧保留；单帧跨多个游戏日 (加速、新档推进) 时 while 逐日补触发；
 * daysToRun >= 0 时累计满该游戏天数后 isDone() 返回 true，引擎在置位同一次推进中
 * 自动移除；全部字段可随存档序列化 (XStream 存档边界)，读档后继续。
 * 注册入口：onGameLoad 的 persistent data 门内 (REGISTER_MARK 一次性门控，C6-S2 的 CLASS_MARK 模式)。
 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_DayAccumulatorScript";
    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    /** 挂载以来的游戏天数 (精确累加，保留小数)；随存档序列化，读档后继续累计。 */
    private float daysSinceMount = 0f;
    /** 例程周期余数：累计满 tickEveryDays 个游戏天触发一次每日例程。 */
    private float daysAccumulator = 0f;
    /** 已执行的每日例程次数 (跨存档累计)。 */
    private int routineCount = 0;
    /** 演示参数：每多少个游戏天执行一次例程；默认 1(每日一次)。 */
    private int tickEveryDays = 1;
    /** 演示参数：最多运行多少游戏天后自停；< 0 表示常驻不停止。 */
    private int daysToRun = -1;
    /** 完成标记：isDone() 返回 true 后引擎在置位同一次推进中自动移除本脚本。 */
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

        // amount 是上一帧真实秒数；经时钟换算才是本帧的游戏天数 (10 真实秒 = 1 游戏日)。
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

:::
