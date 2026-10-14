# 使用 Global 访问游戏服务

`Global` 集中提供设置、星区、战斗引擎、播放器与日志器等入口。使用这些静态方法很简单，真正需要区分的是返回对象属于哪个运行阶段。本节分别在应用、生涯和战斗回调中取得服务，并用日志比较两场战斗中的引擎引用。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 用 GameState 判断当前场景；
- 在对应回调取得设置、生涯与战斗服务；
- 记录当前服务和加载状态；
- 按战斗实例范围保存和释放引用。

## 前置知识

- [C6-S4] 的 JAR 构建与加载；
- [C7-S1] 的入口生命周期；
- [C5-S3] 的声音使用；
- [C2-S4] 的命中与光束回调；
- Java 静态方法、对象引用与字符串。

## Global 提供哪些服务

`Global` 的方法返回游戏当前提供的对象。调用 `getSector()` 不会创建生涯，调用 `getCombatEngine()` 也不会开始战斗。操作需要什么对象，就在提供该对象的运行阶段取得它。

### 全局服务速查表

| 入口 | 当前职责与使用位置 |
|---|---|
| getCurrentState | 当前 GameState，判断实际场景 |
| getSettings | 已加载的设置与资源，在应用加载后读取 |
| getSector | 当前生涯，新档、读档与生涯回调使用 |
| getCombatEngine | 当前战斗引用，在战斗回调使用 |
| getSoundPlayer | 初始化后的播放服务，在对应运行回调使用 |
| getLogger | 按类取得日志器，记录加载与运行 |
| getFactory | 创建游戏对象，前文在生涯生成中使用 |

前面的 MOD 入口已经给出明确的应用和生涯回调。通用方法需要判断场景时，可以使用 `GameState`，例如下面的局部方法：

```java
public static boolean isInCombat() {
    return Global.getCurrentState() == GameState.COMBAT;
}
```

**引用存在，并不代表当前就处于它所服务的场景。** 战斗引擎引用在其他场景也可能存在，判断是否正在战斗时使用 `GameState.COMBAT`。后面的日志因此同时记录场景与引用状态。

## 从应用加载回调读取设置

在 `GearInModPlugin.onApplicationLoad()` 中，设置已经加载完成，可以取得 `SettingsAPI`。下面的局部片段读取版本并输出日志：

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
String version = settings.getGameVersion();
Global.getLogger(GearInGlobalAccess.class).info("GearIn settings version=" + version);
```

设置还包含 MOD 自有键、数值与颜色，下一节将实际读取这些内容。这里先确认应用回调能够取得服务；日志器从 `Global.getLogger(...)` 获得，记录方式在 [C7-S5] 展开。

## 从当前星区取得存档数据

进入新档或读档回调后，通过 `Global.getSector()` 取得这次生涯的 `SectorAPI`。上一节的初始化标记也从这个对象读取：

```java
SectorAPI sector = Global.getSector();
Object marker = sector.getPersistentData().get("GearInModPlugin");
```

键名沿用 `GearInModPlugin.CLASS_MARK` 的值。关系、生成的星系与 persistent data 都属于当前 Sector，载入另一份存档之后，应当从新的 Sector 读取它们。

静态方法提供入口，返回的数据却仍有自己的归属。**需要随生涯保存的状态，写入这份生涯的数据。** 用 `static` 字段缓存标记，无法代替每份存档中的初始化结果。

## 使用本场战斗的引擎

命中效果的 `onHit(...)` 已经处于战斗中，可以使用当前引擎。以下局部片段通过 `Global` 取得它并记录暂停状态：

```java
CombatEngineAPI currentEngine = Global.getCombatEngine();
boolean active = !currentEngine.isPaused();
Global.getLogger(GearInGlobalAccess.class).info("GearIn combat active=" + active);
```

`onHit(...)` 本身也传入 `engine`，效果代码可以直接使用参数。这里展示的访问器用于已有战斗上下文中的通用调用；暂停状态、实体与计时都来自本场引擎。

下一场战斗会建立新的引擎。若把旧引用留在跨战斗的 `static` 字段中，即使 `Global.getCombatEngine()` 已经返回新对象，字段也仍指向上一场。**本场插件保存本场引擎，通用方法在调用时取得当前对象。** 战斗结束后结束相应使用，下一场重新取得。

`Global.getFactory()` 则提供游戏对象的创建方法，前文生成星系时已经使用过。具体实体还需要相应的生涯或战斗上下文，后续结合创建任务展开。

## 在不同回调记录访问结果

创建 `src/data/scripts/gearin/GearInGlobalAccess.java`，将场景、服务引用与加载状态放到同一条日志。`engineIdentity` 用于比较引擎实例，`Global.LOADING_SAVE` 记录读取存档的状态。

::: details 完整成品：GearInGlobalAccess

```java
package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class GearInGlobalAccess {

    private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

    private GearInGlobalAccess() {
    }

    public static boolean isInCombat() {
        return Global.getCurrentState() == GameState.COMBAT;
    }

    public static void logAccessorState() {
        GameState state = Global.getCurrentState();
        SectorAPI sector = Global.getSector();
        CombatEngineAPI engine = Global.getCombatEngine();
        LOG.info("GearIn state=" + state + " settings=" + (Global.getSettings() != null)
                + " sector=" + (sector != null) + " engine=" + (engine != null)
                + " engineIdentity=" + System.identityHashCode(engine)
                + " loadingSave=" + Global.LOADING_SAVE);
    }
}
```

:::

在 `GearInModPlugin` 的应用加载与读档回调中加入：

```java
GearInGlobalAccess.logAccessorState();
```

然后在 `GearInHitLogger.onHit(...)` 中调用同一方法。这里使用完整类名：

```java
data.scripts.gearin.GearInGlobalAccess.logAccessorState();
```

同一份记录现在分别来自应用加载、进入生涯与弹体命中。`state` 说明实际场景，服务布尔值说明引用是否存在；进入第二场战斗后，再比较引擎标识。这样，调用位置与返回对象都能对应到实际过程。

## 验证方案

1. 保存工具类与三个调用点，构建 JAR 并重启游戏。
2. 查看应用加载的 GearIn state 记录，核对设置服务。
3. 进入生涯，查看 Sector 和 loadingSave 记录。
4. 第一场战斗中用都林命中目标，记录 engineIdentity 与场景。
5. 结束后进入第二场，再触发命中，比较当前引擎与上一场的实例标识。
6. 在对应生涯回调读取 CLASS_MARK，核对当前存档标记。

预期结果：

- 应用加载后设置和日志可用。
- 生涯回调取得当前 Sector，持久数据随当前存档读取。
- 战斗记录 GameState.COMBAT 和当前引擎信息，两场分别使用各自实例。
- 阶段、引用状态与 loadingSave 同一行输出，能对照实际调用位置。

## 故障分析

- **应用加载时使用 Sector 抛空指针**：生涯尚未创建或读取。
    - **解决方案**：将生涯操作放在对应回调，核对当前 GameState。

- **下一场仍使用旧实体或引擎**：跨战斗字段保留了上一场引用。
    - **解决方案**：结束该场使用，在新的战斗实例或当前回调中取得服务。

- **日志无法比较阶段**：缺少场景、服务状态或调用位置。
    - **解决方案**：使用 logAccessorState 并结合所在回调查看记录。

## 总结

`Global` 让各类服务容易取得，返回对象仍然属于当前加载、存档或战斗。设置在应用加载后读取，存档状态从当前 Sector 取得，战斗逻辑使用本场引擎。

日志同时保留场景与引用状态，正是为了区分对象存在和场景适用。后续使用这些入口时，回调已经提供对象的地方可以直接沿用参数，需要访问全局服务时再取得当前引用。

## 建议参考项

- `com.fs.starfarer.api.Global`：服务访问器。
- `com.fs.starfarer.api.GameState`：场景枚举。
- `com.fs.starfarer.api.campaign.SectorAPI`：当前生涯和 persistent data。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：战斗状态与对象。
- `Starsector/starsector-core/starsector.log`：阶段与服务记录。
