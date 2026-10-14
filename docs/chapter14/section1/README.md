# 编写生涯逐帧脚本

本节制作一个每日记录、持续三十日的生涯脚本：暂停时等待，保存读档后继续计数，到期后由引擎移除。时间累计、登记方式与结束状态共同决定它怎样运行，我们分别处理这些关系，再比较 transient 登记与实体附着的清理职责。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `isDone()`、`runWhilePaused()` 和 `advance(float amount)` 在引擎中的调用顺序；
- 使用 `CampaignClockAPI.convertToDays()` 累计游戏天，并用 `while` 补齐单帧跨越的多个周期；
- 使用 `hasScript()` 防止重复注册，并区分普通脚本与 transient 脚本的读档结果；
- 根据暂停、战斗、读档和自动移除后的日志定位脚本生命周期问题。

## 前置知识

- [C7-S1] 的 MOD 插件生命周期与 `onGameLoad`；
- [C7-S2] 的 `Global.getSector()` 和生涯引擎建立时机；
- [C10-S1] 的 `SectorAPI`、生涯时钟、脚本注册和暂停语义；
- 能够构建 Java MOD，并从 `starsector.log` 搜索稳定日志文本。

## 先让每日记录按生涯时间运行

创建 `src/data/scripts/gearin/campaign/GearInDayAccumulatorScript.java`，实现 `EveryFrameScript`。引擎每帧调用 `advance(amount)`，我们把传入的秒数转换成生涯日，再累计到下一次每日操作。

**周期按生涯日计算。** `Global.getSector().getClock().convertToDays(amount)` 使用生涯时钟的换算，正常速度下 10 秒对应一日。加速等推进条件由引擎提供的帧时间体现，脚本继续使用同一换算。当前 `runWhilePaused()` 返回 `false`，暂停时没有时间推进回调。

局部累计片段如下，`daysSinceMount` 保存总进度，`daysAccumulator` 保存尚未处理的周期余量：

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

本次回调可能跨过多个完整周期，`while` 逐次处理并扣除对应天数。余下的小数留给下一帧，每日记录才能在不同帧时间下继续保持周期。

日历变化适合另一个目的：某个动作只需要知道日期是否改变，可以将年、月、日组成日期键。下面的局部片段首帧建立基线，随后记录日期变化：

```java
int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
if (dateKey != lastDateKey) {
    if (lastDateKey != -1) {
        LOG.info("GearIn calendar: new day " + clock.getShortDate());
    }
    lastDateKey = dateKey;
}
```

跨过几天后，日期键也只产生一次变化；要补齐每个周期，继续使用前面的累计方式。本节完整脚本按周期逐次记录，日历只用于输出日期。

## 登记脚本，并让进度进入存档

在 `GearInModPlugin` 添加该类的 import，向已有的 `onGameLoad(...)` 追加局部登记，沿用 `sector`：

```java
if (!sector.hasScript(GearInDayAccumulatorScript.class)) {
    sector.addScript(new GearInDayAccumulatorScript());
}
```

`addScript(...)` 放入随生涯保存的普通脚本列表，字段也随实例保存。读档后已有实例继续累计，`hasScript(...)` 根据类型判断列表中是否已经存在，入口只补入缺失的脚本。

如果登记方法自己取得星区，等价的局部写法如下；同一入口采用一份登记即可：

```java
SectorAPI sector = Global.getSector();
if (!sector.hasScript(GearInDayAccumulatorScript.class)) {
    sector.addScript(new GearInDayAccumulatorScript());
}
```

`addScript(...)` 本身不去重，相同对象重复加入也可以多次推进。当前按类登记，适合整份功能只需要一个周期脚本的情况；本次字段保存进度，登记列表决定它是否在运行。

## 让脚本累计 30 个游戏日后结束

完整类采用周期 1 日、总时长 30 日。累计时先将本帧采用的时间限制在剩余时长内，再用总天数减去已执行周期计算余量。跨过终点的一帧也只处理到第 30 日，总例程保持三十次。

达到终点后写完成日志，将 `done` 设为 `true`，`isDone()` 返回这个字段。引擎在回调后再次检查完成状态并移除实例，随后 `hasScript(...)` 返回 `false`。字段都是基础业务数据，UI 与声音播放器等瞬态对象在运行时取得，保存继续累计所需的内容即可。

### 脚本回调速查表

| 方法 | 引擎使用方式 | 本节实现 |
|---|---|---|
| `isDone()` | 推进前与回调返回后检查，成立时移除实例 | 返回 `done` |
| `runWhilePaused()` | 暂停时决定是否调用 `advance(...)` | 返回 `false` |
| `advance(amount)` | 按帧提供秒数，执行当前逻辑 | 转为生涯日，补齐每日周期并检查终点 |

**本轮结束后，下次读档会按登记策略开始新一轮。** 当前登记只查询列表，已完成实例被移除后，缺失条件又成立。希望每份存档永久只执行一次时，还要保存独立的完成标记；本节采用缺失时重新开始的周期观察。

## 当前回调使用哪一份列表

星区先为普通脚本列表建立快照，遍历时检查完成状态，再按暂停条件推进。当前普通列表结束后，transient 列表另行建立快照并推进。

回调中修改原列表，当前快照保持原有内容。向同一列表新增脚本，它到后续快照才会推进；移除已经取出的对象，也仍可能让该对象完成当前一轮。跨普通与 transient 列表操作时，还要考虑两份快照建立的先后。

`removeScript(...)` 按对象移除，`removeScriptsOfClass(...)` 按精确类移除；`hasScript(Class)` 则使用 `isInstance`，可以匹配该类的子类。登记与清理按当前功能需要的范围选择。

## 改为本次运行中的 transient 脚本

只需要当前运行期观察时，使用 `addTransientScript(...)`。这个列表不保存，读档后重新登记的实例从初始字段开始。以下局部片段替换普通登记，先移除原普通实例，再按 transient 列表查重：

```java
sector.removeScriptsOfClass(GearInDayAccumulatorScript.class);
if (!sector.hasTransientScript(GearInDayAccumulatorScript.class)) {
    sector.addTransientScript(new GearInDayAccumulatorScript());
}
```

普通登记用在跨读档的周期功能，transient 登记用在当前运行期的观察或临时动画。本节的时间逻辑相同，保存结果由登记方式决定。

## 实体附着与清理接口

地点、实体和舰队也提供脚本入口，进度随实际登记的持有对象存在。`EveryFrameScriptWithCleanup` 额外声明 `cleanup()`，用于解除脚本持有的资源或外部登记。接口声明了清理方法，具体调用仍取决于持有者的移除流程；自己控制实体寿命时，也应明确安排这一步。

下面是 [C10-S4] 实体放置上下文中的局部实验，需要 `EveryFrameScriptWithCleanup` 的 import，并使用已取得的 `system`。创建一个实际的中继站，附着脚本，再显式清理、解除附着并移除实体：

```java
SectorEntityToken cleanupTarget = system.addCustomEntity(
        "GearIn_cleanup_relay", "清理实验中继站", "comm_relay", "independent");
cleanupTarget.setLocation(1200f, 400f);
EveryFrameScriptWithCleanup cleanupScript = new EveryFrameScriptWithCleanup() {
    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void cleanup() {
        Global.getLogger(GearInDayAccumulatorScript.class)
                .info("GearIn entity script cleanup: explicit cleanup");
    }
};
cleanupTarget.addScript(cleanupScript);
cleanupScript.cleanup();
cleanupTarget.removeScript(cleanupScript);
system.removeEntity(cleanupTarget);
```

这次日志来自显式的 `cleanup()` 调用，随后脚本和实体分别从持有者中移除。坐标 token 只承担位置引用，附着实验使用具有脚本实现的实体；普通星区列表则依据 `isDone()` 移除脚本，两种清理职责分别由各自入口完成。

## 完整最小例

保存下列类，普通登记保留在已有 `onGameLoad(...)` 中。

::: details 完整成品：GearInDayAccumulatorScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/** 每个生涯日记录一次，累计三十日后结束并保留周期余量。 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    private double daysSinceMount;
    private double daysAccumulator;
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
        double daysThisFrame = Math.min(clock.convertToDays(amount), daysToRun - daysSinceMount);
        daysSinceMount += daysThisFrame;
        daysAccumulator = daysSinceMount - routineCount * tickEveryDays;

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

## 验证方案

前置数据：能够保存、读档并推进三十个生涯日的测试存档。

1. 保存类和普通登记，构建并重启，进入测试存档，搜索 `GearIn daily`。
2. 正常推进约一个游戏日，记录 `routine #1` 与 `since mount`，确认普通登记已经开始推进。
3. 继续推进，比较后续 `routine #` 与累计日数，确认例程按每日周期执行。
4. 按空格暂停至少 20 个真实秒，观察日志和生涯日期；随后解除暂停。
5. 继续推进，比较每个完整周期与余量；使用局部时间换算片段理解一次推进跨过多个周期时，`while` 如何逐次扣除。
6. 保存并重新载入存档，比较读档前后的 `since mount` 与 `routine #`。
7. 继续推进到累计 30 个游戏日，搜索 `GearIn daily: done`，再检查 `hasScript(GearInDayAccumulatorScript.class)`。
8. 把登记改为前述 transient 片段，构建重启，再保存读档；比较新的运行期计数，完成后将登记恢复为普通方式。
9. 运行前述实体清理片段，搜索 `GearIn entity script cleanup: explicit cleanup`，确认显式调用产生日志，并确认实验中继站已移除。

预期结果：

- Java 编译完成，且没有加载期类缺失或方法签名错误；
- 未暂停时每累计 `1.0` 个游戏日增加一次 `routineCount`，日志包含 `GearIn daily: routine #N`；
- 暂停期间日期、`since mount` 和例程日志均停止；
- 单帧跨越多个周期时，`while` 为每个完整周期各增加一次次数，余量继续保留；
- 读档后累计字段从保存值继续，不重新产生 `routine #1`；
- 三十日后总例程为三十次，完成日志出现，普通列表移除该实例；下一次读档按登记策略开始新一轮；
- transient 探针读档后不自动存在，重新注册后才继续推进；
- 实体实验显式调用 `cleanup()` 后记录清理日志，再解除脚本并移除中继站；普通星区脚本按 `isDone()` 结束。

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

每日脚本将帧时间换成生涯日，保存总进度与周期余量，逐次处理完整周期，到三十日设置完成状态。普通列表保存这份实例，读档后从原进度继续，结束后引擎移除它。

登记也决定下一轮何时开始。当前列表缺失就创建新实例，永久一次性功能另存完成状态；transient 列表则每次载入重新建立。时间、实例字段与登记条件共同形成脚本的实际寿命。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：`isDone()`、`runWhilePaused()`、`advance(float amount)` 的公开契约；
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：声明 `cleanup()`，用于安排附着脚本的资源清理；
- `com.fs.starfarer.api.campaign.SectorAPI`：`addScript`、`addTransientScript`、`removeScript`、`hasScript` 和脚本列表；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`convertToDays` 与日历读取；
- `com.fs.starfarer.api.Global`：取得当前生涯 `SectorAPI` 的全局入口；
