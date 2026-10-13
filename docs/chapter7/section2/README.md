# 使用 Global 访问游戏服务

前面的代码已经通过 Global 取得星区、播放器和日志器。本节把这些入口放回各自的运行阶段：设置用于应用加载，Sector 用于当前生涯，CombatEngine 用于当前战斗。服务存在与代码处于哪个场景分别观察，再把对象交给实际功能使用。

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

## 按运行阶段选择入口

Global 的静态方法提供当前服务，不会替调用方改变运行阶段。**业务需要生涯对象时，在生涯回调取得；需要当前战斗时，在当前战斗回调取得。**

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

当前阶段可以按 GameState 判断，局部方法如下：

```java
public static boolean isInCombat() {
    return Global.getCurrentState() == GameState.COMBAT;
}
```

一个服务引用是否为空，是当前引用状态；GameState 则表示当前场景。战斗引擎引用也可能在其他场景存在，因此需要按场景决定业务逻辑是否使用它。

## 在应用加载后读取设置

在 GearInModPlugin.onApplicationLoad 使用：

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
String version = settings.getGameVersion();
Global.getLogger(GearInGlobalAccess.class).info("GearIn settings version=" + version);
```

这里读取当前游戏版本并写日志。自有键、数值和颜色的读取由下一节处理，日志级别和上下文由 [C7-S5] 继续展开。

## 在当前生涯访问 Sector

新档或读档回调中取得：

```java
SectorAPI sector = Global.getSector();
Object marker = sector.getPersistentData().get("GearInModPlugin");
```

标记沿用上一节入口的 CLASS_MARK 值，结果属于当前存档。关系、生成对象和持久状态都通过这个 Sector 访问。

**存档范围与进程范围分别管理。** 当前生涯的数据保存到 persistent data，静态字段不能代替存档数据；读取另一份生涯时也要按当前 Sector 取得状态。

## 在战斗回调访问引擎

例如在命中效果的 onHit 中取得当前引擎，局部片段如下：

```java
CombatEngineAPI currentEngine = Global.getCombatEngine();
boolean active = !currentEngine.isPaused();
Global.getLogger(GearInGlobalAccess.class).info("GearIn combat active=" + active);
```

onHit 本身也传入 engine，业务可以直接使用该参数。Global 访问器用于已有战斗上下文中取得同一场景的服务，暂停、实体和时间都由当前引擎提供。

跨战斗的 static 字段会保留旧引用。**战斗插件实例保存本场引擎，通用方法在当前调用点取得引用**；离开战斗后结束相应使用，下一场重新取得。工厂方法暂按前文对象创建使用，具体实体构建结合后面的场景讲解。

## 记录阶段与服务状态

创建 `src/data/scripts/gearin/GearInGlobalAccess.java`。完整类记录 GameState、settings、sector、engine 和 LOADING_SAVE，engineIdentity 提供实例比较。

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

在上一节的应用加载和读档回调加入局部调用：

```java
GearInGlobalAccess.logAccessorState();
```

再在 GearInHitLogger.onHit 中加入同一调用，必要时使用完整类名：

```java
data.scripts.gearin.GearInGlobalAccess.logAccessorState();
```

这三个位置分别观察应用、生涯和战斗的实际上下文。输出是诊断信息，服务布尔值按当前阶段解释，避免把正常加载阶段当成业务失败。

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

Global 把服务入口集中起来，实际使用仍由生命周期和对象范围决定。设置、当前存档和当前战斗各自提供对应数据，GameState、持久标记和引擎实例信息用于核对这些范围。

后续代码继续从具体任务出发选择服务。先明确当前回调与对象归属，再取得所需入口，数据和结果就能沿实际调用关系追踪。

## 建议参考项

- `com.fs.starfarer.api.Global`：服务访问器。
- `com.fs.starfarer.api.GameState`：场景枚举。
- `com.fs.starfarer.api.campaign.SectorAPI`：当前生涯和 persistent data。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：战斗状态与对象。
- `Starsector/starsector-core/starsector.log`：阶段与服务记录。
