# 编写生涯逐帧脚本

本节制作一个按游戏天累计、每游戏日执行一次并在 30 个游戏日后自动结束的生涯脚本。重点是理解引擎何时调用 `advance()`、如何处理暂停和脚本完成状态，以及普通脚本与 transient 脚本在存档中的不同边界。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

- 解释 `isDone()`、`runWhilePaused()` 和 `advance(float amount)` 在引擎中的调用顺序；
- 使用 `CampaignClockAPI.convertToDays()` 累计游戏天，并用 `while` 补齐单帧跨越的多个周期；
- 使用 `hasScript()` 防止重复注册，并区分普通脚本与 transient 脚本的读档结果；
- 根据暂停、战斗、读档和自动移除后的日志定位脚本生命周期问题。

## 前置知识

- [C7-S1] 的 MOD 插件生命周期与 `onGameLoad`；
- [C7-S2] 的 `Global.getSector()` 和生涯引擎建立时机；
- [C10-S1] 的 `SectorAPI`、生涯时钟、脚本注册和暂停语义；
- 能够编译 Java source set，并从 `starsector.log` 搜索稳定日志文本。

## 让引擎按规则推进脚本

`EveryFrameScript` 是一个由生涯引擎主动调用的接口，只有三个方法。它不规定业务周期，也不保存注册信息；脚本类自己保存状态，注册容器负责让引擎找到它。

### 接口方法速查表

| 方法 | 责任 | 本节脚本的实现 |
| --- | --- | --- |
| `isDone()` | 告诉引擎脚本是否已经完成，可以从当前列表移除 | 返回 `done`，达到 30 个游戏日后变为 `true` |
| `runWhilePaused()` | 决定暂停时是否仍调用 `advance()` | 返回 `false`，暂停时不累计游戏天 |
| `advance(float amount)` | 执行一轮推进；`amount` 是上一帧经过的真实秒数 | 先换算成游戏天，再累计并触发每日例程 |

`CampaignEngine` 对普通脚本列表和 transient 脚本列表分别建立快照。每个对象进入快照后，先检查一次 `isDone()`；已经完成的对象直接移除，未完成对象在游戏未暂停或 `runWhilePaused()` 返回 `true` 时收到 `advance(amount)`。回调返回后引擎再检查一次 `isDone()`，因此脚本在自己的 `advance()` 中把 `done` 设为 `true`，就能在同一轮推进结束时被移除。

快照解释了两个容易混淆的现象。脚本在 `advance()` 内新增或移除对象时，修改的是原列表，当前快照仍按开始这一轮时的内容遍历；新脚本要到下一轮才会推进，已经取出的对象仍可能完成当前回调。`addScript()` 也不承担业务去重，同一个实例重复加入会得到多次推进，等价的新实例也会分别推进，所以注册入口必须自行查重。

### 注册容器决定生命周期

`SectorAPI.addScript()` 把脚本放进随生涯保存的普通列表，适合周期管理器和需要跨读档继续的状态。`addTransientScript()` 放进不写入存档的 transient 列表，适合只存在于当前运行阶段的探针或临时动画；读档后它不会自动出现，注册入口需要再次执行。`removeScript()` 按对象实例移除，`removeScriptsOfClass()` 按精确类移除，`hasScript(Class)` 则用 `isInstance` 判断当前列表中是否存在匹配对象。

transient 脚本需要在每次 `onGameLoad()` 中按 transient 列表查重。普通列表与 transient 列表是两个独立容器，因此这里使用 `hasTransientScript()`。

```java
SectorAPI sector = Global.getSector();
if (!sector.hasTransientScript(GearInRuntimeProbe.class)) {
    sector.addTransientScript(new GearInRuntimeProbe());
}
```

地点、实体和舰队也有自己的脚本容器。把脚本挂在星区时，它的生命周期跟随整个生涯；把脚本挂在实体时，实体被移除会结束这条附着关系。`EveryFrameScriptWithCleanup` 在 `EveryFrameScript` 三个方法之外增加 `cleanup()`，它是实体从生涯引擎移除时的清理入口；它不是普通星区脚本在 `isDone()` 后的通用回调。

实现该接口的实体脚本在 `cleanup()` 中撤销自己建立的外部状态。`target.addScript(new GearInEntityProbe())` 决定它附着在哪个实体；实体离开生涯引擎时，下面的方法才会收到调用。

```java
@Override
public void cleanup() {
    Global.getLogger(GearInEntityProbe.class)
            .info("GearIn entity probe: attached entity removed");
}
```

## 选择时间基准

`advance(float amount)` 的 `amount` 单位是真实秒，不能直接当作游戏天。游戏时间换算必须使用 `Global.getSector().getClock().convertToDays(amount)`。正常速度下，当前版本约 10 个真实秒对应 1 个游戏日；暂停时生涯时钟冻结，返回 `false` 的脚本也不会收到推进。

日历跨日检测和精确累计适合不同任务。需要在日期变化时执行一次的事件，可以把 `cycle`、`month` 和 `day` 折叠成日期键；它只回答本次调用是否跨日。需要累计精确游戏天数的任务，应保存浮点余量，每次把 `convertToDays(amount)` 加入累加器。若一次调用跨过多个周期，使用 `while` 而不是 `if`，才能逐个补触发。

```java
CampaignClockAPI clock = Global.getSector().getClock();
float daysThisFrame = clock.convertToDays(amount);
daysSinceMount += daysThisFrame;
daysAccumulator += daysThisFrame;

while (daysAccumulator >= tickEveryDays) {
    daysAccumulator -= tickEveryDays;
    routineCount++;
}
```

日历键的局部写法如下。首帧只建立基线；如果一次推进跳过了几天，它仍然只产生一次日期改变，不会补发中间日期。

```java
int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
if (dateKey != lastDateKey) {
    if (lastDateKey != -1) {
        LOG.info("GearIn calendar: new day " + clock.getShortDate());
    }
    lastDateKey = dateKey;
}
```

## 注册并恢复周期脚本

本节完整例使用 `hasScript()` 做实际列表查重。注册代码应放在 `onGameLoad()` 或 `onNewGame()`，此时 `Global.getSector()` 已经存在。每次进入存档都执行检查：列表中已有实例时不再加入；普通脚本因为随档保存，读档后通常会命中；实例因故缺失时，门禁可以补回它。

```java
SectorAPI sector = Global.getSector();
if (!sector.hasScript(GearInDayAccumulatorScript.class)) {
    sector.addScript(new GearInDayAccumulatorScript());
}
```

这段门禁检查当前列表是否已有对象。功能若要在一个存档中只执行一次，即使脚本已经结束也不能重新创建，应另用独立的 persistent data 标记；本节周期脚本需要在缺失时恢复，所以结束历史不作为注册条件。

## 让脚本累计 30 个游戏日后结束

完整类只负责四件事：把帧秒换成游戏天、保留周期余量、记录每日例程次数，以及在 30 个游戏日后设置完成标记。`tickEveryDays` 和 `daysToRun` 固定为 `1.0f` 与 `30.0f`，这样每个游戏日触发一次，累计满 30 天后由引擎自动清理。

`daysAccumulator` 保存当前周期的未用余量，不能在每次回调后归零；否则帧边界会吞掉小数，周期会随帧率漂移。`daysSinceMount` 记录脚本挂载以来的累计天数，并作为结束条件。所有字段都是基础类型，能够随普通脚本进入存档；不把 `SectorAPI`、UI 或声音对象存进字段，避免把瞬态引擎句柄带入序列化数据。

### 观察自动移除

当 `daysSinceMount >= daysToRun` 时，脚本先写出完成日志，再把 `done` 设为 `true`。这一帧的 `advance()` 返回后，`CampaignEngine` 的第二次 `isDone()` 检查会移除脚本；下一帧不会再进入该对象。`hasScript(GearInDayAccumulatorScript.class)` 随后应返回 `false`。

## 验证方案

前置数据：已启用包含 [C14-S1] 代码的 GearIn MOD，拥有可保存的测试存档；游戏验证使用日志和脚本列表观察结果。

1. 编译包含 [C14-S1] 代码的 MOD，确认 Java 编译完成。
2. 启动游戏并进入测试存档，在 `onGameLoad()` 注册脚本后搜索 `GearIn daily` 日志。
3. 正常推进约一个游戏日，记录一次 `routine #` 日志及 `since mount` 数值。
4. 按空格暂停至少 20 个真实秒，观察日志和生涯日期；随后解除暂停。
5. 使用加速或调试推进制造一次跨越多个游戏日的 `advance()` 调用，比较该次 `routine #` 增量。
6. 保存并重新载入存档，比较读档前后的 `since mount` 与 `routine #`。
7. 继续推进到累计 30 个游戏日，搜索 `GearIn daily: done`，再检查 `hasScript(GearInDayAccumulatorScript.class)`。
8. 使用独立 transient 脚本片段注册一个探针，保存并重新载入，确认它需要在读档回调中重新注册。
9. 在实体脚本测试中移除附着实体，观察 `cleanup()` 的调用边界，并与 `isDone()` 自动移除区分。

预期结果：

- Java 编译完成，且没有加载期类缺失或方法签名错误；
- 未暂停时每累计 `1.0` 个游戏日增加一次 `routineCount`，日志包含 `GearIn daily: routine #N`；
- 暂停期间日期、`since mount` 和例程日志均停止；
- 单帧跨越多个周期时，`while` 为每个完整周期各增加一次次数，余量继续保留；
- 读档后累计字段从保存值继续，不重新产生 `routine #1`；
- 达到 `30.0` 个游戏日后出现完成日志，`isDone()` 变为 `true`，脚本从普通列表移除；
- transient 探针读档后不自动存在，重新注册后才继续推进；
- `cleanup()` 只对应实体移除路径，普通星区脚本结束时不调用该方法。

## 故障分析

- **脚本每次读档后出现两份日志**：注册入口无条件调用 `addScript()`，而普通脚本实例已经随存档恢复。
    - **解决方案**：在 `onGameLoad()` 使用 `hasScript(GearInDayAccumulatorScript.class)`，只在列表缺少实例时加入新对象。

- **脚本没有任何日志**：脚本没有进入星区脚本列表，或注册发生在 `Global.getSector()` 尚未建立的应用加载阶段。
    - **解决方案**：把注册放在 `onGameLoad()` 或 `onNewGame()`，并在注册后检查 `hasScript()`。

- **周期速度约快十倍**：`advance()` 把真实秒 `amount` 直接当成游戏天。
    - **解决方案**：先调用 `Global.getSector().getClock().convertToDays(amount)`，再把结果加入天数累加器。

- **单帧跨过三天却只执行一次**：周期判断使用 `if`，一次回调只消耗一个周期。
    - **解决方案**：使用 `while (daysAccumulator >= tickEveryDays)`，每次循环扣除一个周期并保留余量。

- **暂停时仍然累计游戏天**：脚本的 `runWhilePaused()` 返回了 `true`，或逻辑累计的是原始 `amount`。
    - **解决方案**：暂停敏感的生涯逻辑返回 `false`，并始终通过生涯时钟换算时间。

- **脚本一直存在，完成日志却出现了**：`advance()` 设置了完成条件，但 `isDone()` 没有返回同一个 `done` 字段。
    - **解决方案**：让 `isDone()` 返回 `done`，由引擎在回调返回后自动移除脚本。

- **transient 脚本读档后消失**：脚本注册在 transient 列表，而该列表不写入存档。
    - **解决方案**：把需要跨档保存的逻辑注册到 `addScript()`；临时逻辑在每次读档回调中重新调用 `addTransientScript()`。

- **保存或读档时出现序列化错误**：脚本字段持有 UI、声音播放器或其他瞬态引擎句柄。
    - **解决方案**：字段只保存可序列化的业务状态，瞬态对象使用 `transient` 字段并在运行时重建。

## 总结

`EveryFrameScript` 的行为由三层共同决定：引擎按帧调用接口，脚本用时钟换算和累加器保存进度，注册容器决定对象是否跨存档存在。周期脚本需要保留小数余量，用 `while` 处理单帧跨周期，并在完成后通过 `isDone()` 让引擎清理。排查时沿注册列表、时间单位、暂停门控、完成字段和存档容器依次核对，就能区分没有运行、运行过快、读档消失和重复运行。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：`isDone()`、`runWhilePaused()`、`advance(float amount)` 的公开契约；
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：实体移除时的 `cleanup()` 钩子；
- `com.fs.starfarer.api.campaign.SectorAPI`：`addScript`、`addTransientScript`、`removeScript`、`hasScript` 和脚本列表；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`convertToDays` 与日历读取；
- `com.fs.starfarer.api.campaign.Global`：取得当前生涯 `SectorAPI` 的全局入口；
- `D:\Starsector\starsector-core\deobf\sources\com\fs\starfarer\campaign\CampaignEngine.java`：脚本快照遍历、暂停门控和前后两次 `isDone()` 检查。

## 完整最小例

::: details 完整成品：GearInDayAccumulatorScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 按游戏天累计的周期脚本：每 1 个游戏日执行一次，累计 30 个游戏日后结束。
 * amount 是上一帧真实秒数，必须经 clock.convertToDays(amount) 换算；
 * daysAccumulator 保留周期余量，while 负责补齐单帧跨越的多个周期。
 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    private float daysSinceMount = 0f;
    private float daysAccumulator = 0f;
    private int routineCount = 0;
    private final float tickEveryDays = 1.0f;
    private final float daysToRun = 30.0f;
    private boolean done = false;

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
        float daysThisFrame = clock.convertToDays(amount);
        daysSinceMount += daysThisFrame;
        daysAccumulator += daysThisFrame;

        while (daysAccumulator >= tickEveryDays) {
            daysAccumulator -= tickEveryDays;
            routineCount++;
            LOG.info(String.format(
                    "GearIn daily: routine #%d at %s | since mount=%.1f game days",
                    routineCount, clock.getShortDate(), daysSinceMount));
        }

        if (daysSinceMount >= daysToRun) {
            LOG.info(String.format(
                    "GearIn daily: done, isDone()=true after %.1f game days", daysSinceMount));
            done = true;
        }
    }
}
```

:::
