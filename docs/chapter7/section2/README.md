# 使用 Global 访问游戏服务

本节按游戏运行阶段取得 `Global` 提供的服务，并把设置、生涯和战斗对象交给合适的 Java 代码。完成后可以从日志判断服务何时可用，并避免跨战斗保存失效的对象引用。

*本节预期阅读时间：约 15 分钟。*

*本节预期操作时间：约 15 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `Global.getCurrentState()` 判断当前运行阶段；
- 在资源加载、生涯和战斗回调中取得对应的服务对象；
- 通过日志确认服务可用性，并避免复用上一场战斗的引擎引用。

## 前置知识

- [C6-S1] 的 Java 工具链和 JAR 构建；
- [C7-S1] 的 `GearInModPlugin` 生命周期；
- [C5-S2] 的资源注册方法；
- [C7-S5] 的日志器、级别和稳定前缀。

## 判断当前运行阶段

`Global.getCurrentState()` 返回当前 `GameState`。先根据它判断代码处于资源加载、生涯还是战斗，再取得对应服务；用某个对象是否为 `null` 作为阶段判断会把 `服务尚未建立` 和 `当前场景确实没有对象` 混为一谈。

```java
public static boolean isInCombat() {
    return Global.getCurrentState() == GameState.COMBAT;
}
```

这个判断适合放在需要区分战斗与其他场景的通用工具中。它只报告阶段，具体服务仍需在实际回调中重新取得。

## 在资源加载阶段读取设置

资源加载和 `onApplicationLoad()` 阶段可以使用 `Global.getSettings()` 读取合并后的设置，也可以使用 `Global.getLogger(...)` 写入日志。设置对象适合读取版本、音量和开关等静态配置；具体键的读取方式见 [C7-S3]。

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
String version = settings.getGameVersion();
Global.getLogger(GearInGlobalAccess.class).info("GearIn settings version=" + version);
```

日志服务也在早期可用，记录阶段和服务状态后可以直接在 `starsector.log` 中搜索。日志器的级别选择和上下文格式统一由 [C7-S5] 说明。

## 在生涯阶段取得 SectorAPI

`Global.getSector()` 返回当前生涯的 `SectorAPI`。生涯对象必须等生涯建立后再读取，因此新档和读档相关代码放在 `onNewGame()`、`onGameLoad()` 或其他生涯回调中。资源加载阶段直接读取它会得到空对象并在后续调用中触发空指针。

```java
SectorAPI sector = Global.getSector();
Object marker = sector.getPersistentData().get("GearInGlobalAccess");
```

`SectorAPI` 还提供存档持久数据；标记、关系或生成结果需要随存档保存时，应把值写入 `getPersistentData()`，而不是保存在静态字段中。

## 在战斗回调中取得 CombatEngineAPI

`Global.getCombatEngine()` 只代表当前战斗的 `CombatEngineAPI`。战斗插件或战斗脚本在当前回调中取得它，用完后随着战斗结束释放引用；第二场战斗必须重新调用访问器。

```java
CombatEngineAPI engine = Global.getCombatEngine();
boolean active = engine != null && !engine.isPaused();
Global.getLogger(GearInGlobalAccess.class).info("GearIn combat active=" + active);
```

引擎对象包含战斗实体、时间和暂停状态。把它保存到跨战斗的静态字段会让下一场战斗继续指向旧引擎，表现为实体查询失败或调用没有效果。

## 了解其他全局入口

`Global.getFactory()` 用于创建游戏对象；**目前暂不深化 `Global.getFactory()` 的对象创建 API 和具体用法**。本节只使用设置、生涯、战斗和日志服务完成阶段判断与验证。

## 完整最小例

下面的工具类把阶段判断和服务状态记录集中起来。`isInCombat()` 提供布尔判断，`logAccessorState()` 在一个调用点读取当前阶段、设置、生涯和战斗服务，便于比较不同运行阶段的结果。

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

## 验证方案

1. 在 `GearInModPlugin.onApplicationLoad()` 或其他入口脚本中调用 `GearInGlobalAccess.logAccessorState()`，重新构建 JAR。
2. 启动游戏并查看 `Starsector/starsector-core/starsector.log`，记录资源加载阶段的 `state`、`settings`、`sector` 和 `engine` 值。
3. 新建生涯后再次调用工具类，核对 `state` 已进入生涯且 `sector=true`。
4. 进入第一场战斗，在战斗回调中取得 `Global.getCombatEngine()` 并记录当前引擎状态。
5. 结束第一场战斗、进入第二场战斗并重新调用访问器，核对第二场使用的是当前引擎对象。
6. 对照日志中的 `GearIn getSector() is null` 和 `GearIn getCombatEngine() is null`，确认警告只出现在对应服务尚未建立的阶段。

预期结果：

- 资源加载阶段可以读取设置和日志服务；
- 生涯建立后 `SectorAPI` 可用，持久数据读取指向当前存档；
- 战斗阶段取得的引擎只对应当前战斗，第二场战斗重新取得对象；
- 阶段、服务状态和加载标记均写入 `starsector.log`，过早访问能够定位到具体服务。

## 故障分析

- **生涯加载期出现空指针**：代码在生涯建立前调用 `getSector()` 或继续使用返回对象。

    - **解决方案**：把生涯访问移到 `onNewGame()`、`onGameLoad()` 或其他生涯回调，并在调用前记录当前 `GameState`。

- **第二场战斗仍使用旧引擎**：`CombatEngineAPI` 被保存到跨战斗字段或静态状态中。

    - **解决方案**：在每个战斗回调中重新调用 `Global.getCombatEngine()`，不要把上一场战斗的引用带入下一场。

- **日志无法判断服务阶段**：日志行缺少 `state`、服务布尔值或 `loadingSave` 信息。

    - **解决方案**：调用 `logAccessorState()`，使用 `Global.getLogger(GearInGlobalAccess.class)` 写出完整状态，并在 `starsector.log` 中搜索 `GearIn` 前缀。

## 总结

`Global` 访问器必须与运行阶段配合使用：设置和日志服务用于早期检查，生涯对象等待生涯建立，战斗引擎在当前战斗回调中取得。`getCurrentState()` 负责阶段判断，`SectorAPI.getPersistentData()` 负责存档范围的状态，战斗引擎则在每场战斗重新获取。`Global.getFactory()` 目前暂不深化。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局服务访问器；
- `com.fs.starfarer.api.campaign.SectorAPI`：生涯对象和持久数据；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：当前战斗引擎；
- `Starsector/starsector-core/starsector.log`：核对各阶段服务状态的运行日志。
