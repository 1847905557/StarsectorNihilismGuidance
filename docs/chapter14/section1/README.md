# 编写生涯逐帧脚本

前面的生涯观察已经在逐帧脚本里运行，现在把登记、时间和结束方式展开。本节制作一个每日记录、持续三十日的普通脚本，保存后继续累计，暂停时等待，完成后由引擎移除。再用同一份观察代码比较 transient 登记与实体清理，进度属于哪个对象、什么时候结束，都有实际入口。

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

## 先登记每日脚本

创建 `src/data/scripts/gearin/campaign/GearInDayAccumulatorScript.java`，并加入 `GearInModPlugin` 的 import。向 [C7-S1] 已有的 `onGameLoad(...)` 追加局部登记，沿用 `sector`：

```java
if (!sector.hasScript(GearInDayAccumulatorScript.class)) {
    sector.addScript(new GearInDayAccumulatorScript());
}
```

已有普通脚本时继续使用它，读档恢复剩余状态；三十日完成后，下一次读档发现列表缺席，会建立新一轮。当前设计选择持续补回，结束历史也就不作为登记条件。

**登记容器决定保存范围，脚本字段保存本次进度。** `EveryFrameScript` 提供三个方法，实际周期由我们的时间累计实现。

### 接口方法速查表

| 方法 | 责任 | 本节脚本的实现 |
| --- | --- | --- |
| `isDone()` | 告诉引擎脚本是否已经完成，可以从当前列表移除 | 返回 `done`，达到 30 个游戏日后变为 `true` |
| `runWhilePaused()` | 决定暂停时是否仍调用 `advance()` | 返回 `false`，暂停时不累计游戏天 |
| `advance(float amount)` | 执行一轮推进；`amount` 是上一帧经过的真实秒数 | 先换算成游戏天，再累计并触发每日例程 |

生涯引擎 对普通脚本列表和 transient 脚本列表分别建立快照。每个对象进入快照后，先检查一次 `isDone()`；已经完成的对象直接移除，未完成对象在游戏未暂停或 `runWhilePaused()` 返回 `true` 时收到 `advance(amount)`。回调返回后引擎再检查一次 `isDone()`，因此脚本在自己的 `advance()` 中把 `done` 设为 `true`，就能在同一轮推进结束时被移除。

快照解释了两个容易混淆的现象。脚本在 `advance()` 内新增或移除对象时，修改的是原列表，当前快照仍按开始这一轮时的内容遍历；新脚本要到下一轮才会推进，已经取出的对象仍可能完成当前回调。`addScript()` 也不承担业务去重，同一个实例重复加入会得到多次推进，等价的新实例也会分别推进，所以登记入口按实际类查重。

### 注册容器决定生命周期

`SectorAPI.addScript()` 把脚本放进随生涯保存的普通列表，适合周期管理器和需要跨读档继续的状态。`addTransientScript()` 放进不写入存档的 transient 列表，适合只存在于当前运行阶段的探针或临时动画；读档后它不会自动出现，注册入口需要再次执行。`removeScript()` 按对象实例移除，`removeScriptsOfClass()` 按精确类移除，`hasScript(Class)` 则用 `isInstance` 判断当前列表中是否存在匹配对象。

需要让同一个观察类只存在于当前运行阶段时，把前面的普通登记替换为以下局部片段。两种登记选择一种；当前已有普通实例时，先从它的列表移除：

```java
sector.removeScriptsOfClass(GearInDayAccumulatorScript.class);
if (!sector.hasTransientScript(GearInDayAccumulatorScript.class)) {
    sector.addTransientScript(new GearInDayAccumulatorScript());
}
```

地点、实体和舰队也有自己的脚本容器。把脚本挂在星区时，它的生命周期跟随整个生涯；把脚本挂在实体时，实体被移除会结束这条附着关系。`EveryFrameScriptWithCleanup` 在 `EveryFrameScript` 三个方法之外增加 `cleanup()`，它是实体从生涯引擎移除时的清理入口；它不是普通星区脚本在 `isDone()` 后的通用回调。

例如在 [C10-S4] 的实体放置类中，创建一个用于清理观察的坐标 token，并附上局部匿名脚本。这里需要 `EveryFrameScriptWithCleanup` 的 import，片段放在已取得 `system` 之后：

```java
SectorEntityToken cleanupTarget = system.createToken(1200f, 400f);
system.addEntity(cleanupTarget);
cleanupTarget.addScript(new EveryFrameScriptWithCleanup() {
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
                .info("GearIn entity script cleanup: entity removed");
    }
});
system.removeEntity(cleanupTarget);
```

## 选择时间基准

`advance(float amount)` 的 `amount` 单位是真实秒，不能直接当作游戏天。时间换算使用 `Global.getSector().getClock().convertToDays(amount)`。正常速度下，当前版本约 10 个真实秒对应 1 个游戏日；暂停时生涯时钟冻结，返回 `false` 的脚本也不会收到推进。

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

完整类将回调时间换成生涯日，保留周期余量、记录次数，到三十日设置完成标记。`tickEveryDays` 和 `daysToRun` 固定为 `1.0f` 与 `30.0f`，这样每个游戏日触发一次，累计满 30 天后由引擎自动清理。

**累计量保留实际进度，周期余量只扣已经执行的部分。** 完整类用累计天数减去已执行周期计算余量，单帧跨过多个周期时逐个处理。跨过三十日终点的那一帧只采用剩余天数，总例程因此保持三十次。所有字段都是基础类型，能够随普通脚本进入存档；不把 `SectorAPI`、UI 或声音对象存进字段，避免把瞬态引擎句柄带入序列化数据。

### 观察自动移除

当 `daysSinceMount >= daysToRun` 时，脚本先写出完成日志，再把 `done` 设为 `true`。这一帧的 `advance()` 返回后，生涯引擎 的第二次 `isDone()` 检查会移除脚本；下一帧不会再进入该对象。`hasScript(GearInDayAccumulatorScript.class)` 随后应返回 `false`。

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
2. 启动游戏并进入测试存档，在 `onGameLoad()` 注册脚本后搜索 `GearIn daily` 日志。
3. 正常推进约一个游戏日，记录一次 `routine #` 日志及 `since mount` 数值。
4. 按空格暂停至少 20 个真实秒，观察日志和生涯日期；随后解除暂停。
5. 继续推进，比较每个完整周期与余量；使用局部时间换算片段理解一次推进跨过多个周期时，`while` 如何逐次扣除。
6. 保存并重新载入存档，比较读档前后的 `since mount` 与 `routine #`。
7. 继续推进到累计 30 个游戏日，搜索 `GearIn daily: done`，再检查 `hasScript(GearInDayAccumulatorScript.class)`。
8. 把登记改为前述 transient 片段，构建重启，再保存读档；比较新的运行期计数，完成后将登记恢复为普通方式。
9. 运行前述实体附着与移除片段，查看 `GearIn entity script cleanup`，比较实体清理与普通周期脚本的完成日志。

预期结果：

- Java 编译完成，且没有加载期类缺失或方法签名错误；
- 未暂停时每累计 `1.0` 个游戏日增加一次 `routineCount`，日志包含 `GearIn daily: routine #N`；
- 暂停期间日期、`since mount` 和例程日志均停止；
- 单帧跨越多个周期时，`while` 为每个完整周期各增加一次次数，余量继续保留；
- 读档后累计字段从保存值继续，不重新产生 `routine #1`；
- 三十日后总例程为三十次，完成日志出现，普通列表移除该实例；下一次读档按登记策略开始新一轮；
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

每日脚本通过普通列表保存本次进度，生涯时钟提供日数，周期余量与 `while` 处理完整周期，三十日边界再连接到完成与自动移除。

普通、transient 与实体附着各有保存和清理范围。编者建议把登记与结束一起安排：希望读档继续哪一段状态，结束后是否开始下一轮，代码都在这些入口表达。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：`isDone()`、`runWhilePaused()`、`advance(float amount)` 的公开契约；
- `com.fs.starfarer.api.EveryFrameScriptWithCleanup`：实体移除时的 `cleanup()` 钩子；
- `com.fs.starfarer.api.campaign.SectorAPI`：`addScript`、`addTransientScript`、`removeScript`、`hasScript` 和脚本列表；
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`convertToDays` 与日历读取；
- `com.fs.starfarer.api.Global`：取得当前生涯 `SectorAPI` 的全局入口；
