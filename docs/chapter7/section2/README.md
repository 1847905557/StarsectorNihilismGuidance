# 使用 Global 访问游戏服务

本节说明如何从 Java 代码访问 `Global` 提供的设置、生涯、战斗和日志服务，并根据当前阶段选择可用入口。

*本节预期阅读时间：约 10 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

- 选择与当前阶段对应的 `Global` 访问器；
- 判断 `getSector()` 和 `getCombatEngine()` 的可用时机；
- 记录阶段和服务状态，定位空对象访问。

## 前置知识

- [C6-S1] 的 Java 工具链和 JAR 构建；
- [C7-S1] 的 `GearInModPlugin` 生命周期；
- [C5-S2] 的资源注册方法。

## 选择服务入口

从 `Global.getSettings()` 读取静态设置，从 `Global.getSector()` 读取生涯对象，从 `Global.getCombatEngine()` 读取当前战斗引擎，从 `Global.getFactory()` 创建游戏对象，从 `Global.getLogger(...)` 写入日志。当前状态使用 `Global.getCurrentState()` 判断。

资源加载期只允许读取设置、日志等已建立的服务；生涯回调可以读取 `getSector()`；战斗插件必须在当前战斗中取得 `getCombatEngine()`，不得跨战斗保存引用。

## 判断服务生命周期

`getSettings()`、`getLogger(...)` 在游戏循环开始前可用。`getSector()` 在生涯建立后可用，`getCombatEngine()` 只在战斗期间代表当前引擎。不能用引擎是否为 `null` 代替 `getCurrentState()` 的阶段判断。

## 写入运行日志

使用 `Global.getLogger(GearInGlobalAccess.class)` 建立日志器，并记录状态、服务可用性和存档加载标记。日志会写入 `starsector.log`，空的生涯或战斗对象必须对应明确的警告。

## 实现全局访问工具

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

## 验证方案

1. 在入口插件或脚本中调用 `GearInGlobalAccess.logAccessorState()`；
2. 分别在资源加载、生涯和战斗阶段查看 `starsector.log`；
3. 确认每个阶段只读取已建立的服务；
4. 进入第二场战斗，确认没有复用上一场战斗的引擎对象。

预期结果：日志显示正确阶段和服务可用性，错误阶段访问得到明确警告，战斗对象随当前战斗更新。

## 故障分析

- **生涯加载期出现空指针**：过早调用 `getSector()`。

    - **解决方案**：把读取放到生涯回调。

- **第二场战斗仍使用旧引擎**：跨战斗保存 `CombatEngineAPI`。

    - **解决方案**：在当前回调中重新获取。

- **日志没有输出**：没有使用 `Global.getLogger(...)` 或未查看 `starsector.log`。

    - **解决方案**：修正日志器和日志路径。

## 总结

`Global` 提供按阶段使用的游戏服务。设置和日志可以在早期读取，生涯和战斗对象必须等待对应阶段；状态判断使用 `getCurrentState()`，战斗引擎只在当前战斗中取得。

## 建议参考项

- [C7-S1]：入口插件的生命周期；
- [C7-S3]：通过 `Global.getSettings()` 读取设置；
- [C7-S4]：通过 `Global.getSoundPlayer()` 播放声音。
