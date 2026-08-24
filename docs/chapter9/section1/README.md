# 制作随存档运行的生涯脚本

本节注册一个随存档运行的生涯脚本，并用 `SectorAPI` 读取生涯时钟。示例会观察暂停、加速和战斗期间的推进差异；地点容器只保留本页所需的最小定义。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 说出星区 (sector) 的结构：`SectorAPI` 是生涯引擎根对象，名下挂哪些子容器 (地点、时钟、玩家舰队、经济、势力、脚本、监听器、persistent data、memory、UI 外壳)，以及每个子容器从哪个 getter 进入；
- 用 `SectorAPI.getClock()` 读生涯时钟：
    - `getCycle()` / `getMonth()` / `getDay()` / `getHour()` 的取值规则与纪元起点；
    - `getDateString()` / `getShortDate()` / `getTimestamp()` / `getElapsedDaysSince()` 的语义；
    - 记住换算基线 "10 真实秒 = 1 游戏日"；
- 从零写一个 `EveryFrameScript` 实现并通过 `SectorAPI.addScript` 注册：
    - 复述一次性注册标记 (persistent data `CLASS_MARK` 模式) 与每帧推进循环 (`isDone` → 移除；`runWhilePaused()` 或未暂停 → `advance`)；
    - 说出 "脚本随存档保存、transient 脚本不保存" 的序列化边界；
- 说出暂停与加速时哪些推进、哪些冻结：引擎暂停时脚本默认不推进、时钟不前进；按住 Shift 加速时每帧推进两次 (`campaignSpeedupMult`)；战斗期间生涯引擎完全不推进；
- 从症状定位九类典型故障：脚本没注册、重复注册、`isDone()` 恒 true、`runWhilePaused` 语义误用、把秒当游戏天、暂停时依赖时钟、战斗期 "脚本消失"、旧档不注册、脚本字段放了不可序列化对象。

## 前置知识

- [C1-S1] 的 MOD 目录结构与 `starsector.log` 日志位置；
- [C4-S1] 的生涯与战役域概念；
- [C6-S2] 的 `modPlugin` 生命周期回调与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义与生涯引擎建立边界；
- [C7-S1] 的每帧回调模型、暂停语义与战斗域和生涯域的差异；
- [C6-S4] 的 settings 读取与 [C15-S4] 的 common 文件机制。

## 术语

本节首次完整讲解两个词：

- **星区 (sector)**：API 名保留 `SectorAPI`。一个存档对应一个星区，`Global.getSector()` 返回当前存档的生涯引擎。星区是 "生涯域根对象"：时钟、地点、舰队、经济、势力、脚本、监听器、持久数据、记忆、UI 外壳都从它下面取。
- **地点容器 (location)**：API 名保留 `LocationAPI`。星区内部的空间容器 (超空间与各恒星系)，实体挂在容器里。容器内实体与访问见 [C9-S2]。

## 梳理生涯脚本关系

星区是生涯域的根对象，本节的所有读取与注册都从它开始。先看整体结构，再顺着时钟与脚本两条线深入。

### 星区结构 (SectorAPI)

`SectorAPI`(`com.fs.starfarer.api.campaign.SectorAPI`) 是纯接口，由生涯引擎实现。

### SectorAPI 成员分组速查表

按功能分组，本节涉及的成员如下：

| 分组 | 成员 | 语义 |
|---|---|---|
| 时钟 | `getClock()` | 生涯时钟 (`CampaignClockAPI`) |
| 脚本 | `addScript` / `removeScript` / `removeScriptsOfClass(Class)` / `hasScript(Class)` / `getScripts()` | 引擎级每帧脚本的注册、移除、查询 |
| 脚本 (临时) | `addTransientScript` / `removeTransientScript` / `removeTransientScriptsOfClass(Class)` / `hasTransientScript(Class)` / `getTransientScripts()` | 不随存档保存的临时脚本 |
| 地点 | `getHyperspace()` / `getStarSystem(String name)` / `getStarSystems()` / `getCurrentLocation()` / `getAllLocations()` / `getEntityById(String id)` / `getEntitiesWithTag(String tag)` | 地点容器与实体查找，详见 [C9-S2] |
| 玩家 | `getPlayerFleet()` / `getPlayerPerson()` / `getPlayerFaction()` / `getPlayerStats()` | 玩家舰队、人物、势力、属性 |
| 经济与势力 | `getEconomy()` / `getFaction(String id)` / `getAllFactions()` | 经济入口与势力表 |
| 监听器 | `addListener` / `addTransientListener` / `addListenerWithTimeout` / `removeListener` / `getAllListeners()` / `getListenerManager()` | 生涯事件监听器，详见 [C12-S2] |
| 通用插件 | `getGenericPlugins()` | 通用插件注册表 |
| 持久数据 | `getPersistentData()` | 返回随存档保存的 `Map<String, Object>`，跨会话 MOD 数据入口 (前置生命周期例已用) |
| 记忆 | `getMemory()` / `getMemoryWithoutUpdate()` | 星区 "global" memory，详见 [C14-S4] |
| UI 外壳 | `getCampaignUI()` | `CampaignUIAPI`：生涯消息区、core UI 页签、确认框等入口 |
| 暂停 | `isPaused()` / `setPaused(boolean)` | 生涯暂停状态 |
| 状态标志 | `isInFastAdvance()` / `isInNewGameAdvance()` | 新档时间推进窗口内为 `true` |
| 工具 | `genUID()` | 返回本星区唯一递增的十六进制 id 字符串，脚本/实体造 id 时用它 |

结构要点：

1. 一个存档 = 一个生涯引擎实例；`Global.getSector()` 返回它，星区引用**禁止**跨存档缓存 (见前置知识中的 `Global` 阶段语义条目)。
2. 星区自身直接持有：时钟 (`CampaignClockAPI`)、`scripts` 与 `transientScripts` 两个脚本列表、`paused` 标志、经济、势力管理器、监听器、persistent data、memory、UI 外壳。
    - 也就是说，"脚本随不随存档保存" 由字段的 `transient` 修饰决定：**`scripts` 随存档保存，`transientScripts` 不随存档保存**。
    - 这是 `addScript` 与 `addTransientScript` 的唯一行为差别，注册入口的选择直接决定跨存档行为。
3. 实体级脚本是另一条平行线：实体对象也持有自己的 `scripts` 列表并逐实体推进，舰队脚本用 `CampaignFleetAPI.addScript(...)` 挂载 (成熟实例的通用做法)。
    - 实体级脚本随实体生命周期走；注意 `EveryFrameScriptWithCleanup.cleanup()` 不会被引擎调用。
    - **原版 API 文档缺陷**：该接口的 javadoc 声称 "附着实体被移除时调用 cleanup()"，引擎实际从不调用。实现该接口的 `cleanup()` 永远不会被执行。
    - 本节示例用引擎级 `addScript`；实体级脚本按对象生命周期注册。

### 时间系统 (CampaignClockAPI)

结构表里的 `getClock()` 指向整个生涯的时间基准。脚本要换算游戏时间、做跨日检测，必须先读懂这颗钟：`CampaignClockAPI`(`com.fs.starfarer.api.campaign.CampaignClockAPI`) 是生涯时钟的公开接口，由引擎内部时钟类实现。基线事实：

- **纪元起点是 206 年 1 月 1 日**：`new GregorianCalendar(206, 0, 1)`。
    - `getCycle()` 返回日历**年** (开档即 206，因此 `getCycleString()` 是 `"c206"` 这种写法)；
    - `getMonth()` 返回 1–12(1 = January)；
    - `getDay()` 返回 1–31；`getHour()` 返回 0–23。
- **换算基线：10 真实秒 = 1 游戏日**：`SECONDS_PER_GAME_DAY = 10.0F`。`convertToDays(真实秒)` = 秒 ÷ 10；`convertToSeconds(游戏天)` = 天 × 10；`convertToMonths(真实秒)` = 秒 ÷ 10 ÷ 30。
- **日期字符串**：`getDateString()` = `"Mar 2, c206"` 形态；`getShortDate()` = `"206.3.2"` 形态；`getShortMonthString()` = 三字母月份。
- **时间戳与间隔**：`getTimestamp()` 返回内部日历毫秒值；`getElapsedDaysSince(ts)` 返回从 `ts` 到现在的游戏天数 (`(now - ts) / 86400000`)。
    - **参数为 `0` 时返回 `Float.MAX_VALUE`**，计算 "未初始化" 的间隔时不要传 0。
- **`createClock(long timestamp)`**：按时间戳新建一个时钟实例 (对齐 "从某时刻起算" 的局部计时)。
- **时钟只在未暂停时前进**：引擎的每帧推进里 `clock.advance(amount)` 只在 `!isPaused()` 分支执行；暂停时 `getTimestamp()` / 日期全部冻结。
    - `readResolve` 在读档时按保存的时间戳重建内部日历，读档后时钟从存档时刻继续。
- **新档时间推进**：`CharacterCreationData.isWithTimePass()` 为 true 时，引擎进入 `isInFastAdvance()` 窗口；关闭该选项则不执行这段预推进。
    - 开启时以 `2.5` 秒步长连推约 600 真实秒 + 收尾约 2 秒，合计约 **60.2 游戏日 (约两个月)**；随后才调用 `onNewGameAfterTimePass` 与 `onGameLoad(true)`。
    - 开启时，`onGameLoad` 读到的时钟大约在 **206.3.2**。

把秒当游戏天

`advance(float amount)` 的 `amount` 是上一帧经过的真实秒数 ("Use SectorAPI.getClock() to convert to campaign days.")。

凡需要游戏时间，**必须**经 `clock.convertToDays(amount)` 换算 —— 直接把秒当游戏天，时间流速会偏差 10 倍 (10 真实秒被当成 10 游戏天，正确应为 1 游戏天)。

别用 0 占位

`getElapsedDaysSince(ts)` 的参数为 `0` 时返回 `Float.MAX_VALUE`。计算 "未初始化" 的间隔时先检查时间戳是否已写入，不要传 0。

### 时钟方法速查表

| 方法 | 返回 | 语义 |
|---|---|---|
| `getCycle()` | `int` | 年 (纪元 206 起；存档界面写作 `Cycle 206, Mar 2` 格式，`getCycleString()` 写作 `c206`) |
| `getMonth()` | `int` | 月，1–12(1 = January) |
| `getDay()` | `int` | 日，1–31 |
| `getHour()` | `int` | 时，0–23 |
| `getDateString()` | `String` | `"Mar 2, c206"` 形态 |
| `getShortDate()` | `String` | `"206.3.2"` 形态 (年.月.日) |
| `getTimestamp()` | `long` | 内部日历毫秒；暂停时冻结 |
| `getElapsedDaysSince(ts)` | `float` | 从 `ts` 到现在的游戏天数；`ts == 0` 返回 `Float.MAX_VALUE` |
| `convertToDays(真实秒)` | `float` | 秒 ÷ 10(`amount` 参数的正确换算) |
| `convertToSeconds(游戏天)` | `float` | 天 × 10 |
| `convertToMonths(真实秒)` | `float` | 秒 ÷ 10 ÷ 30 |
| `createClock(ts)` | `CampaignClockAPI` | 按时间戳建独立时钟 (局部计时) |

### 脚本生命周期 (EveryFrameScript)

时钟给出当前时刻，脚本决定每帧做什么。

### EveryFrameScript 方法速查表

`EveryFrameScript`(`com.fs.starfarer.api.EveryFrameScript`) 只有三个方法，这是生涯按帧脚本的完整约定：

| 方法 | 约定 |
|---|---|
| `boolean isDone()` | 返回 `true` 表示脚本结束，引擎负责清理 (从脚本列表移除)。常驻脚本返回 `false` |
| `boolean runWhilePaused()` | 返回 `true` 时，引擎暂停期间 `advance()` 仍会被调用 |
| `void advance(float amount)` | 每帧回调；**`amount` 是上一帧经过的真实秒数** ("Use SectorAPI.getClock() to convert to campaign days.") |

推进循环的精确语义 (引擎级脚本与 transient 脚本两条循环完全一致)：

1. 引擎遍历的是**列表快照** (`new ArrayList<>(this.scripts)`)，因此脚本在 `advance()` 里安全地 `addScript` / `removeScript` 不会抛 `ConcurrentModificationException`。
    - 代价是**在 advance 里新加的脚本要到下一帧才开始推进**。
2. 对每个脚本：先查 `isDone()`—— 为真直接移除 (不调 `advance`)；否则查 `runWhilePaused() || !isPaused()`—— 为真才调 `advance(amount)`；`advance` 返回后再查一次 `isDone()`，为真则移除。
3. 移除的三个入口：`isDone()` 自动清理 (引擎在 `advance` 返回后同帧移除)；`removeScript(script)` 按**对象同一性**移除；`removeScriptsOfClass(Class)` 按**精确类** (`getClass() == clazz`，子类不算) 批量移除。
4. `hasScript(Class)` 用 `isInstance` 判断 (**子类实例也算命中**)，与 `removeScriptsOfClass` 的精确类语义不同 —— 查重用 `hasScript`，批量删用 `removeScriptsOfClass`，别混用。
5. **`addScript` 不去重**：直接 `scripts.add(...)`。同一实例或等价实例重复注册会各自推进，这是 "日志成双、行为叠加" 的根源。
6. 引擎自身也会注册自己的内置脚本 —— 引擎级脚本不是 MOD 专属机制。

序列化边界 (脚本字段随存档)：

- `scripts` 非 `transient` → **随存档保存**：常驻脚本 (管理器、星系防御舰队脚本 (SDF, Starsystem Defense Fleet) 等) 跨读档继续运行，其非 `transient` 字段原样恢复。
- `transientScripts` 是 `transient` → **不随存档保存**：读档后需要重新注册。
- 因此脚本字段里**不要**放不能随存档序列化的对象 (UI 外壳引用、声音播放器等引擎单例或瞬态句柄)；跨会话的小数据放 `getPersistentData()`，脚本字段只保留可序列化状态。`SectorAPI.getPersistentData()` 保存运行时对象时同样要考虑 XStream 存档边界。

脚本字段随存档序列化

`scripts` 里的脚本字段会随存档保存 (非 `transient` 字段原样恢复)。不要存放不能序列化的对象 (UI 外壳引用、声音播放器句柄等引擎单例或瞬态句柄)；跨会话的小数据放 `getPersistentData()`，纯临时推进逻辑用 `addTransientScript`。

### 暂停与加速语义

推进循环的步频不是恒定的。正常游玩时，生涯引擎每帧推进一次；时间加速时同帧连调多次：

- **暂停 (空格键)**：按空格键切换 `engine.setPaused(...)`。
    - 暂停时引擎照常 `advance`(时钟、经济、脚本循环都在跑)—— 但时钟只在非暂停分支推进，脚本只在 `runWhilePaused() || !isPaused()` 时推进。
    - **结论：默认 (`runWhilePaused() == false`) 脚本在暂停时完全冻结，`amount` 也拿不到。**
- **加速 (按住 Shift)**：按住 Shift 键加速 (`campaignSpeedUpTimeIsToggle` 默认 `false`，即按住生效不是切换)。
    - 加速时按 `campaignSpeedupMult`(`settings.json`，取值为 2) 连调 `engine.advance`，脚本与时钟同帧推进两次 —— 游戏日流速翻倍。
- **新档时间推进窗口**：`isInFastAdvance()` 为 `true` 时引擎以 2.5 秒步长连推，`onNewGame` 阶段注册的脚本也会被这次推进逐帧调用。
- **战斗期间生涯冻结**：进入战斗后生涯引擎不再推进 —— 脚本、时钟、经济全部冻结，回生涯后恢复。

### 注册入口与持久数据标记

循环怎么跑已经清楚，剩下的是注册时机：脚本注册**必须**发生在生涯引擎建立之后，只有两条合法入口：

1. **`onNewGame()`**：新档生成期 (此时生涯引擎已建立，早于生命周期回调 `onNewGame` 完成)。注意新档的 "时间推进" 发生在 `onNewGame` 之后，此入口注册的脚本会经历那约 60 游戏日的推进。
2. **`onGameLoad(boolean newGame)`**：新档与读档共用；新档调用时已在时间推进之后 (由游戏管理器驱动)。本节示例用这个入口。

注册的必须与禁止

脚本注册**必须**发生在生涯引擎建立之后 —— **禁止**在 `onApplicationLoad` 里注册 (此刻 `Global.getSector()` 为 null，解引用即 NPE，启动中止)。

一次性注册**必须**用随存档保存的 persistent data 标记，且每个注册项用**各自的键**，不要并入插件既有的 `CLASS_MARK` 标记 —— 否则在插件标记已置位的旧档里，本脚本永远不会注册。

无论哪个入口，一次性注册**必须**用随存档保存的 persistent data 标记 (前置生命周期例的 `CLASS_MARK` 模式)。

本节示例给每个注册项**各自的键** (`REGISTER_MARK`)。

## 实现生涯时钟脚本

本页先处理一个能被观察的结果，再把它放进正确的生命周期。以下代码均为局部片段；把它放入指定类或方法后再继续，日志或界面出现对应信号时才进入下一段。

### 按存档注册一次

```java
// 插入位置：GearInModPlugin.onGameLoad()
SectorAPI sector = Global.getSector();
if (!sector.getPersistentData().containsKey(GearInSectorClockScript.REGISTER_MARK)) {
    sector.getPersistentData().put(GearInSectorClockScript.REGISTER_MARK, true);
    sector.addScript(new GearInSectorClockScript());
}
```

新增行为：每个存档首次加载时注册一个常驻脚本，标记随存档保存。完成信号：首次进入旧档或新档出现挂载日志；同一存档再次读档不会增加第二个实例。

### 保存首次推进时刻

```java
// 插入位置：GearInSectorClockScript.advance() 取得 clock 之后
if (startTimestamp == 0L) {
    startTimestamp = clock.getTimestamp();
    LOG.info("GearIn clock script mounted at " + clock.getDateString());
}
```

新增行为：脚本第一次真正推进时记录生涯时间戳，该字段随存档恢复。完成信号：首次挂载只出现一次日志；保存并读档后累计时间从保存点继续。

### 检测跨日并换算帧长

```java
// 插入位置：advance() 完成首次时刻初始化之后
int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
if (dateKey != lastDateKey) {
    lastDateKey = dateKey;
    LOG.info(String.format(
            "GearIn clock day change: %s | this frame=%.4f game days | since mount=%.1f game days",
            clock.getDateString(),
            clock.convertToDays(amount),
            clock.getElapsedDaysSince(startTimestamp)));
}
```

新增行为：真实帧长先换算为游戏天，再在日期键变化时报告。完成信号：正常速度约每 10 真实秒出现一条跨日日志，暂停时停止，加速时频率约翻倍。

### 到期后交给引擎移除

```java
// 插入位置：advance() 末尾，以及 isDone()
if (daysToRun >= 0 && clock.getElapsedDaysSince(startTimestamp) >= daysToRun) {
    LOG.info("GearIn clock script done: " + daysToRun + " game days elapsed");
    done = true;
}

return done;
```

新增行为：可选运行上限满足后只设置完成标志，由脚本循环在下一次检查时移除实例。完成信号：`daysToRun=30` 时约 30 个游戏日后出现完成日志，随后 `sector.hasScript(GearInSectorClockScript.class)` 返回 `false`。

## 组装生涯脚本实现

入口与一次性注册标记都确认了，该让脚本真正跑起来。在既有 MOD 布局与前置的 `GearInModPlugin` 生命周期例之上，本节只新增一个脚本类 (不新增任何资源、不新增任何注册项)：

```text {8}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       └── GearInSectorClockScript   # 本节新增 (归本节所有)
└── jars\
```

### GearInSectorClockScript

对应关系：

- `implements EveryFrameScript`：三个方法全来自接口 (`isDone` / `runWhilePaused` / `advance`)；`isDone()` 返回字段 `done`，演示 "引擎自动清理"。
- `REGISTER_MARK`：persistent data 标记键 (`"GearIn_SectorClockScript"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
- `advance(float amount)`：`amount` 是上一帧真实秒数；`clock.convertToDays(amount)` 才是本帧折合的游戏天数。
- `getCycle() * 10000 + getMonth() * 100 + getDay()`：把日期折叠成整数键做跨日检测 (206.3.2 → 2060302)。
- `getElapsedDaysSince(startTimestamp)`：挂载以来经过的游戏天数；暂停时不增长。
- `daysToRun` / `setRunWhilePaused`：两个演示参数，对应 "修改参数看行为变化" 的演示步骤。

调用点 (工程片段 —— 方法所在类由前置生命周期例持有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(前置生命周期例持有) 的 onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInSectorClockScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInSectorClockScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInSectorClockScript());
    }
    // 可选冗余查重 (建议：hasScript 与 persistent data 门互为冗余)：
    if (!Global.getSector().hasScript(GearInSectorClockScript.class)) {
        Global.getSector().addScript(new GearInSectorClockScript());
    }
```

(上面两段只取一段即满足 "不重复注册"；两段同留时，第二段被自己的 `hasScript` 检查 (isInstance 命中) 拦截而不补注册；`addScript` 本身不去重，移除 hasScript 门后会叠加运行 —— 但演示时建议只保留标记段，避免混淆。)

想演示 `daysToRun`，在注册前先设参数：

```java
    GearInSectorClockScript script = new GearInSectorClockScript();
    script.setDaysToRun(30); // 约 30 个游戏日 (约 5 分钟游戏时间) 后自停
    Global.getSector().addScript(script);
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInSectorClockScript.class`(可用 `jar tf` 核对)。

## 关键故障实验：读档后脚本进度重新开始

将脚本加入 transient scripts 或只依赖实例字段。

**局部故障片段：**

```java
// 故障片段
sector.addTransientScript(new GearInSectorClockScript());
```

**局部修正片段：**

```java
// 修正片段
sector.addScript(new GearInSectorClockScript());
```

复验：保存、退出、读档并核对同一进度键。

## 验证方案

前置数据：既有 MOD 布局 [C6-S1]、`GearInModPlugin` ([C6-S2]，含本节调用点片段) 与本节类 `GearInSectorClockScript`。累计工程的 `GearInSystemGenerator` 非必需。

1. 构建：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`；用 `jar tf jars/GearIndustry.jar` 核对产物。
2. 新建生涯：启动游戏进入新档，持续观察 `starsector.log`，直到确认脚本挂载与跨日日志。
3. 暂停：生涯中按空格暂停，等待 20 秒以上，再恢复。
4. 加速：按住 Shift 约 10 秒，再松开。
5. 读档：保存后读档，继续观察日志。
6. 战斗：进入前置战役 `GearIn_test` 或任意遭遇战，观察战斗期间的日志；退出战斗回生涯后继续观察。
7. 参数演示：把 `daysToRun` 设为 30 重新构建，观察自停行为。

预期结果：

- 游戏构建以 `BUILD SUCCESSFUL` 结束 (退出码 0)；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInSectorClockScript.class`。

- 新建生涯：`starsector.log` 依次出现。

    ```text
    GearIn clock script mounted at Mar 2, c206 GearIn clock day change: Mar 2, c206 (short=206.3.2) | this frame=0.0017 game days | since mount=0.0 game days
    ```

    随后**每 10 真实秒** (未暂停、未加速) 出现一条新的跨日日志，日期逐日前进、`since mount` 逐日 +1.0。启用 `withTimePass` 时，挂载日期约为 206.3.2；启动与游玩全程无 `gear_in` 引起的 `ERROR`。

- 暂停 (空格)：跨日日志停止 (脚本冻结、时钟冻结)；恢复后继续，`since mount` 不因暂停丢失进度。

- 加速 (按住 Shift)：跨日频率约翻倍 (`campaignSpeedupMult = 2`，每帧推进两次)，约每 5 真实秒一条。

- 读档：不出现 `mounted` 日志 (`startTimestamp` 随存档恢复)；不重复注册 (`REGISTER_MARK` 标记已置位、`hasScript` 命中)；跨日日志从存档时刻继续。

- 战斗：进入战斗期间无任何跨日日志 (生涯引擎冻结)；回生涯后继续。

- `daysToRun = 30`：挂载约 300 真实秒后出现。

    ```text
    GearIn clock script done: 30 game days elapsed, isDone()=true
    ```

    之后不再有跨日日志 —— 引擎在置位后的同一帧 (`advance` 返回后的复查) 从 `scripts` 列表移除了脚本 (可验证 `Global.getSector().hasScript(GearInSectorClockScript.class)` 为 `false`)。

- 退出到主菜单：脚本停止推进 (生涯引擎不再推进)；回到主菜单后再进档 (读档) 按第 5 条继续。

清理：把 `daysToRun` 恢复为 `-1`，使脚本保持常驻。

## 故障分析

- **脚本完全没有日志**：脚本没有注册，或注册错误地放在 `onApplicationLoad`。

    - **解决方案**：将一次性注册放入 `onGameLoad` 或 `onNewGame`。

- **每次读档后跨日日志成倍增加**：`addScript` 没有持久标记和查重。

    - **解决方案**：使用 `REGISTER_MARK` 与 `hasScript`。

- **脚本首帧即被移除**：`isDone()` 初始就返回 true。

    - **解决方案**：让 `done` 初始为 false，只在完成条件满足时置 true。

- **暂停时脚本推进但日期不变**：`runWhilePaused()` 只允许 `advance()` 继续调用，不会推进生涯时钟。

    - **解决方案**：暂停敏感进度使用时钟差值；需要真实时间时才累计 `amount`。

- **时间流速快了十倍**：代码把真实秒 `amount` 直接当成游戏日。

    - **解决方案**：改用 `clock.convertToDays(amount)`。

- **暂停期间倒计时始终不触发**：倒计时依赖冻结的生涯时钟。

    - **解决方案**：需要真实时间倒计时就自行累计 `amount`；需要游戏时间则等待解除暂停。

- **战斗期间没有生涯脚本日志**：战斗阶段会暂停生涯引擎与时钟，生涯脚本在战斗期间不输出日志，属预期行为。

    - **解决方案**：战斗期需要日志时，把输出移到战斗侧插件。

- **旧存档始终没有注册脚本**：新注册项错误地复用了入口插件的 `CLASS_MARK`。

    - **解决方案**：为脚本使用独立的 `REGISTER_MARK`，读入缺少该键的旧档后完成首次注册。

- **读档后脚本行为错乱或保存异常**：脚本字段保存了 UI、声音播放器等瞬态对象。

    - **解决方案**：字段只保存可序列化状态；跨会话数据进入 `getPersistentData()`，纯临时脚本使用 `addTransientScript`。

## 总结

本节把星区、时钟与脚本讲成一条链：**注册入口** (`onGameLoad` + persistent data 标记)→ **每帧推进** (引擎遍历快照：`isDone` 先清、`runWhilePaused() || !isPaused()` 才调 `advance(amount)`)。

链条的后半是**时钟换算** (`amount` 是真实秒，`convertToDays` 按 10 秒/游戏日折算；暂停时时钟冻结) 与**清理** (`isDone` 自动移除、`removeScript` 按同一性、`removeScriptsOfClass` 按精确类、`hasScript` 按 `isInstance`)。

记忆要点：

- `scripts` 随存档保存、`transientScripts` 不随存档保存；`addScript` 不去重 —— **一次性注册必须用 persistent data 标记**，查重用 `hasScript`。
- 引擎遍历快照：`advance` 里 `addScript` 的新脚本**下一帧**才推进。
- 暂停 (空格) 时：脚本默认冻结、时钟冻结、经济冻结；加速 (按住 Shift) 时每帧推进两次；**战斗期间生涯引擎完全不推进**。
- 纪元 206-01-01；`getCycle()` 是年、`getMonth()` 1–12、`getDay()` 1–31；`getElapsedDaysSince(0)` 返回 `Float.MAX_VALUE`。
- 启用 `withTimePass` 时，新档在 `onGameLoad` 前推进约 60.2 游戏日；关闭时不执行这段预推进。

引擎级脚本只是生涯异步逻辑的一半：实体级脚本随实体生命周期走、事件监听器挂在星区上，它们是这条链的逻辑延伸；而贯穿始终的两块底座是 "首次进入存档" 的 `CLASS_MARK` 标记与 `Global` 阶段语义 —— 所有注册与读取都建立在生涯引擎建立之后。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorAPI`：星区根对象 —— 时钟、脚本、持久数据、记忆、暂停等入口。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：生涯时钟 —— 日期/时间戳读取与秒 ⇄ 游戏日换算。
- `com.fs.starfarer.api.EveryFrameScript`：生涯按帧脚本接口 ——`isDone` / `runWhilePaused` / `advance` 三方法约定。
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：带 `cleanup()` 的脚本接口；引擎从不调用 `cleanup()`。
- `com.fs.starfarer.api.Global`：`getSector()` / `getLogger()` 全局入口。
- `data.scripts.gearin.campaign.GearInSectorClockScript`：本节示例类 (完整源码)。
- `data/config/settings.json`：`campaignSpeedupMult` 等生涯设置 (加速倍数)。

## 完整最小例

::: details 完整成品：GearInSectorClockScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 完整最小例：SectorAPI 注册的生涯每帧脚本 (EveryFrameScript)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，注册前用 hasScript() 查重，
 * 并用 sector persistent data 键 REGISTER_MARK 做一次性门控 (C6-S2 的 CLASS_MARK 模式)。
 *
 * 行为：每个游戏日跨日时打印一行日志 (当前日期、本帧折合的游戏天数、挂载以来经过的游戏天数)；
 * 默认 runWhilePaused() 返回 false：引擎暂停时脚本不推进、生涯时钟也不前进；
 * daysToRun >= 0 时挂载满该天数后 isDone() 返回 true，引擎在 isDone() 置位的同一次推进中从 scripts 列表移除本脚本。
 */
public class GearInSectorClockScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_SectorClockScript";

    private static final Logger LOG = Global.getLogger(GearInSectorClockScript.class);

    /** 跨日判定键 (cycle * 10000 + month * 100 + day)；-1 表示尚未初始化。 */
    private int lastDateKey = -1;

    /** 脚本第一次 advance() 时的时钟时间戳；随存档序列化，读档后继续累计。 */
    private long startTimestamp = 0L;

    /** 演示参数：最多运行多少游戏天后自停；< 0 表示常驻不停止。 */
    private int daysToRun = -1;

    /** 演示参数：是否在引擎暂停时仍推进；默认 false(暂停即停表)。 */
    private boolean runWhilePaused = false;

    /** 完成标记：isDone() 返回 true 后引擎在置位同一次推进中自动移除本脚本。 */
    private boolean done = false;

    public GearInSectorClockScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    /** 修改运行天数上限；改回 -1 恢复常驻。 */
    public void setDaysToRun(int daysToRun) {
        this.daysToRun = daysToRun;
    }

    /** 修改暂停语义；改为 true 后暂停期间 advance() 仍会被调用。 */
    public void setRunWhilePaused(boolean runWhilePaused) {
        this.runWhilePaused = runWhilePaused;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return runWhilePaused;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        if (startTimestamp == 0L) {
            startTimestamp = clock.getTimestamp();
            LOG.info("GearIn clock script mounted at " + clock.getDateString());
        }

        int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
        if (dateKey != lastDateKey) {
            lastDateKey = dateKey;
            LOG.info(String.format(
                    "GearIn clock day change: %s (short=%s) | this frame=%.4f game days | since mount=%.1f game days",
                    clock.getDateString(),
                    clock.getShortDate(),
                    clock.convertToDays(amount),
                    clock.getElapsedDaysSince(startTimestamp)));
        }

        if (daysToRun >= 0 && clock.getElapsedDaysSince(startTimestamp) >= daysToRun) {
            LOG.info("GearIn clock script done: " + daysToRun + " game days elapsed, isDone()=true");
            done = true;
        }
    }
}
```

:::
