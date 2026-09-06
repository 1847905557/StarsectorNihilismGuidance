# 详解战术系统

本节把 [C2-S3] 的战术系统数据继续展开，重点放在 `BaseShipSystemScript` 的回调时机、返回值和运行结果，并用独立的 `ShipSystemAIScript` 让 AI 舰船自动激活系统。

*本节预期阅读时间：~24 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 配置战术系统数据和效果脚本；
- 根据调用时机实现效果脚本方法；
- 接入独立的自定义系统 AI；
- 根据日志和战斗结果定位回调问题。

## 前置知识

- [C2-S3] 的 `.system`、`ship_systems.csv`、`ship_data.csv` 与 `apply` / `unapply`；
- [C8-S4] 的 `MutableShipStatsAPI`、modifier ID 与 `modifyPercent`；
- [C8-S1] 的战斗引擎状态和暂停语义；
- [C7-S2] 的全局服务访问阶段。

## 登记战术系统

本节先登记一个普通战术系统和一个用于观察待机回调的系统。两者沿用 [C2-S3] 的数据文件位置；本节只增加脚本入口和待机运行开关。

主系统的 `data/shipsystems/GearIn_range_system.system` 使用独立的 stats 脚本和 AI 脚本：

```json
{
    "id": "GearIn_range_system",
    "type": "STAT_MOD",
    "aiType": "CUSTOM",
    "statsScript": "data.scripts.gearin.shipsystems.GearInRangeSystem",
    "aiScript": "data.scripts.gearin.shipsystems.GearInRangeSystemAI"
}
```

`statsScript` 决定激活期间修改舰船数值的类，`aiType: "CUSTOM"` 让舰船 AI 进入自定义分支，`aiScript` 决定由哪个 `ShipSystemAIScript` 做激活判断。持续时间和开关方式属于 `ship_systems.csv`，不能写入 `.system`：

```text
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle
范围增幅,GearIn_range_system,1,,,,,,,1,0.1,1,5,1,10,FALSE,,,,,,,,,,,,
```

`ship_data.csv` 的 `system id` 填写 `GearIn_range_system` 后，舰船才会挂载这个系统。

待机演示系统 `data/shipsystems/GearIn_idle_probe.system` 只使用 stats 脚本，并把 `runScriptWhileIdle` 打开：

```json
{
    "id": "GearIn_idle_probe",
    "type": "STAT_MOD",
    "aiType": "NONE",
    "statsScript": "data.scripts.gearin.shipsystems.GearInIdleProbeSystem",
    "runScriptWhileIdle": true
}
```

这个系统只用于观察 stats 回调，持续时间仍由 `ship_systems.csv` 中的 `GearIn_idle_probe` 行提供。默认情况下，stats 脚本主要在 `IN`、`ACTIVE` 和 `OUT` 阶段收到 `apply(...)`。`runScriptWhileIdle: true` 会让 `apply(...)` 在 `IDLE` 和 `COOLDOWN` 阶段也继续被调用；原版行为是开启该选项后 `unapply(...)` 不会被调用，因此需要清理效果的脚本不能仅依赖这个回调。每帧调用范围扩大后，脚本本身必须保持轻量。

待机系统的数据行只负责提供名称和时间：

```text
待机观察,GearIn_idle_probe,1,,,,,,,1,0,1,2,1,4,FALSE,,,,,,,,,,,,
```

## 初始化效果脚本

`BaseShipSystemScript` 为 `ShipSystemStatsScriptAdvanced` 提供默认实现。继承它后，类只覆写当前效果需要的方法；未覆写的方法继续使用默认返回值。

系统创建舰船时取得 stats 脚本实例。该实例负责读取 `MutableShipStatsAPI`、写入系统修饰，并在系统结束后清理自己的 modifier。自定义 AI 由另一套实例负责，两个类的字段不会互相共享。

## 在效果阶段修改舰船属性

`apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)` 在效果阶段逐帧调用。`state` 表示前摇、全功率或后摇，`effectLevel` 在这些阶段从 0 变化到 1 再回落。本例使用固定的 `id` 修改武器射程倍率：

```java
@Override
public void apply(MutableShipStatsAPI stats, String id,
        State state, float effectLevel) {
    stats.getEnergyWeaponRangeBonus().modifyMult(id,
            1f + RANGE_MULT_BONUS * effectLevel);
}
```

前摇阶段的倍率会随 `effectLevel` 平滑增加，全功率阶段达到最大值，后摇阶段再逐渐恢复。`unapply(MutableShipStatsAPI stats, String id)` 在普通系统的效果归零时调用一次，必须使用同一个 `id` 撤销这次写入：

```java
@Override
public void unapply(MutableShipStatsAPI stats, String id) {
    stats.getEnergyWeaponRangeBonus().unmodify(id);
}
```

## 提供状态栏和激活许可

玩家舰船的系统状态栏通过 `getStatusData(int index, State state, float effectLevel)` 取得文字。返回 `StatusData` 时，第二个参数决定文字是否按减益样式显示；返回 `null` 表示当前索引没有内容。

```java
@Override
public StatusData getStatusData(int index, State state, float effectLevel) {
    if (index == 0 && state == State.ACTIVE) {
        return new StatusData("range amplified", false);
    }
    return null;
}
```

`isUsable(ShipSystemAPI system, ShipAPI ship)` 在激活请求被接受前查询。返回 `false` 时，按键请求会被丢弃；本例只在系统不处于激活状态且舰船仍在战斗中时允许激活。

```java
@Override
public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
    return ship != null && ship.isAlive()
            && !system.isActive() && !system.isCoolingDown();
}
```

`getInfoText(ShipSystemAPI system, ShipAPI ship)` 决定系统图标下方的信息文字。返回 `null` 时，界面使用默认的 `READY` 或 `ACTIVE`：

```java
@Override
public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
    if (system.isCoolingDown()) {
        return "RECHARGING";
    }
    return null;
}
```

## 覆盖系统参数

系统数据中的持续时间、恢复速度和使用次数可以由脚本覆盖。覆盖方法在系统建立运行参数时查询；返回负值表示继续使用数据文件中的值。

```java
@Override
public float getActiveOverride(ShipAPI ship) {
    return 6f;
}

@Override
public float getInOverride(ShipAPI ship) {
    return -1f;
}

@Override
public float getOutOverride(ShipAPI ship) {
    return -1f;
}

@Override
public float getRegenOverride(ShipAPI ship) {
    return -1f;
}

@Override
public int getUsesOverride(ShipAPI ship) {
    return -1;
}
```

`getDisplayNameOverride(State state, float effectLevel)` 可以根据当前阶段替换系统名称。它返回 `null` 时沿用数据文件名称：

```java
@Override
public String getDisplayNameOverride(State state, float effectLevel) {
    return state == State.ACTIVE ? "Range Amplified" : null;
}
```

## 接入自定义系统 AI

`ShipSystemAIScript` 与 stats 脚本分工不同：AI 只决定何时激活，数值效果仍由 `BaseShipSystemScript` 的 `apply` 处理。`.system` 中只有 `aiType: "CUSTOM"` 时，`aiScript` 才会被加载。

`init(...)` 在 AI 实例创建时调用一次，适合保存当前舰船、系统和战斗引擎。`advance(...)` 每帧调用，示例用 `IntervalUtil` 节流实际决策：

```java
@Override
public void init(ShipAPI ship, ShipSystemAPI system,
        ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.engine = engine;
    this.interval.randomize();
}

@Override
public void advance(float amount, Vector2f missileDangerDir,
        Vector2f collisionDangerDir, ShipAPI target) {
    if (engine == null || engine.isPaused()) {
        return;
    }
    interval.advance(amount);
    if (!interval.intervalElapsed() || target == null
            || !target.isAlive() || system.isActive()
            || system.isCoolingDown()) {
        return;
    }
    if (ship.getFluxTracker().isOverloadedOrVenting()) {
        return;
    }
    ship.useSystem();
}
```

`target` 是当前 AI 传入的目标；目标有效、系统可激活且舰船没有过载时，`ship.useSystem()` 触发与玩家按键相同的系统激活入口。用户舰没有舰船 AI，因此不会调用这个 AI 类。

## 观察待机回调

`GearInIdleProbeSystem` 不修改舰船属性，只记录 `apply(...)` 收到的状态。由于其 `.system` 打开了 `runScriptWhileIdle`，日志会覆盖待机、激活和冷却阶段：

这个示例仍然实现 `unapply(...)`，但它用于验证一个重要特性：`runScriptWhileIdle=true` 时，游戏持续调用 `apply(...)`，不会再调用 `unapply(...)`。验证时应看到各状态的 `apply` 日志，却看不到 `GearIn idle probe: unapply`。

```java
@Override
public void apply(MutableShipStatsAPI stats, String id,
        State state, float effectLevel) {
    LOG.info("GearIn idle probe: state=" + state
            + " effectLevel=" + effectLevel);
}

@Override
public void unapply(MutableShipStatsAPI stats, String id) {
    LOG.info("GearIn idle probe: unapply");
}
```

需要撤销数值或释放状态的脚本，应在 `apply(...)` 的状态分支中自行维护。

## 验证方案

前置数据：已把两个系统分别挂载到测试舰船，并完成 Java 17 构建。

1. 执行 `gradle jar`，确认 JAR 包含三个示例类。
2. 进入模拟战，激活 `GearIn_range_system`，观察前摇、全功率、后摇和冷却阶段。
3. 核对主系统的武器射程倍率、`range amplified` 状态文字和 `RECHARGING` 信息文字。
4. 将主系统舰船放入 AI 一方，观察 `GearInRangeSystemAI` 的初始化日志和间隔激活日志。
5. 进入挂载 `GearIn_idle_probe` 的战斗，搜索 `GearIn idle probe`，确认 `IDLE` 与 `COOLDOWN` 也有记录。
6. 暂停战斗并恢复，比较普通系统和待机演示系统的回调日志。
7. 结束战斗后开始第二场战斗，确认 stats 与 AI 的实例状态重新建立。

预期结果：

- 主系统激活时射程倍率随 `effectLevel` 变化，结束后由 `unapply` 恢复；
- 玩家界面出现 `range amplified` 和 `RECHARGING`；
- AI 舰船按节流间隔调用 `ship.useSystem()`，而不是每帧重复尝试；
- 待机演示系统在 `IDLE`、`IN`、`ACTIVE`、`OUT` 和 `COOLDOWN` 阶段输出日志；
- 第二场战斗使用新的脚本实例和新的战斗对象。

## 故障分析

- **系统加载时报 `Problem loading class`**：`.system` 中的 `statsScript`、`aiScript` 或包名与 JAR 中的类不一致。

    - **解决方案**：逐字核对完整类名、`package` 声明和 JAR 条目后重启。

- **AI 舰船从不激活系统**：`aiType` 不是 `CUSTOM`，或 `advance(...)` 的目标、冷却和存活检查始终返回。

    - **解决方案**：将 `aiType` 改为 `CUSTOM`，确认目标有效并用日志检查节流后的决策时机。

- **系统结束后射程倍率仍然存在**：`unapply(...)` 没有用与 `apply(...)` 相同的 modifier ID 撤销。

    - **解决方案**：统一使用引擎传入的 `id`，重新激活并等待系统结束。

- **只看到激活阶段日志**：`runScriptWhileIdle` 未写在 `.system` 顶层，或值不是 `true`。

    - **解决方案**：在待机演示系统的 `.system` 顶层设置 `runScriptWhileIdle: true`，重新加载后搜索 `IDLE` 与 `COOLDOWN`。

- **系统每帧反复尝试激活**：自定义 AI 没有使用 `IntervalUtil`，或没有检查 `isActive()` 和 `isCoolingDown()`。

    - **解决方案**：保留决策间隔，并在调用 `ship.useSystem()` 前检查系统状态。

- **待机脚本造成战斗卡顿**：开启 `runScriptWhileIdle` 后仍在每帧执行全战场扫描或大量日志输出。

    - **解决方案**：缩短待机脚本逻辑，只读取必要状态，并将高成本工作改为间隔执行。

- **自定义 AI 保存的字段在 stats 脚本中为空**：stats 类与 AI 类是独立实例，实例字段不会互相共享。

    - **解决方案**：通过参数、静态工具方法或舰船 custom data 传递确需共享的信息。

## 总结

战术系统的 `.system` 文件先确定 stats 脚本、AI 脚本和待机调用范围。`BaseShipSystemScript` 的 `apply` 与 `unapply` 负责数值效果，状态文字和覆盖方法负责界面与运行参数；`ShipSystemAIScript` 只负责在合适时机调用系统。`runScriptWhileIdle` 让 stats 脚本能够观察完整状态周期，但也扩大了每帧工作范围。

## 建议参考项

- `data/shipsystems/<id>.system`、`ship_systems.csv` 和 `ship_data.csv`：战术系统数据文件、脚本入口和运行参数；
- `MutableShipStatsAPI` 与 `MutableStat`：舰船数值修饰入口；
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：效果脚本基类；
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：状态、效果和状态栏接口；
- `com.fs.starfarer.api.plugins.ShipSystemStatsScriptAdvanced`：`isUsable` 与 `getInfoText`；
- `com.fs.starfarer.api.combat.ShipSystemAIScript`：`init` 与 `advance`；
- `com.fs.starfarer.api.combat.ShipSystemAPI`：状态、冷却和激活访问器；

## 完整最小例

::: details 完整成品：GearInRangeSystem

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class GearInRangeSystem extends BaseShipSystemScript {

    public static final float RANGE_MULT_BONUS = 0.5f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id,
            State state, float effectLevel) {
        stats.getEnergyWeaponRangeBonus().modifyMult(id,
                1f + RANGE_MULT_BONUS * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getEnergyWeaponRangeBonus().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state == State.ACTIVE) {
            return new StatusData("range amplified", false);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship != null && ship.isAlive()
                && !system.isActive() && !system.isCoolingDown();
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isCoolingDown()) {
            return "RECHARGING";
        }
        return null;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        return 6f;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        return -1f;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        return -1;
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        return state == State.ACTIVE ? "Range Amplified" : null;
    }
}
```

:::

::: details 完整成品：GearInRangeSystemAI

```java
package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;

public class GearInRangeSystemAI implements ShipSystemAIScript {

    private static final Logger LOG = Global.getLogger(GearInRangeSystemAI.class);
    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private final IntervalUtil interval = new IntervalUtil(0.2f, 0.3f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system,
            ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;
        interval.randomize();
        LOG.info("GearIn range AI init: " + ship.getHullSpec().getHullId());
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir,
            Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        interval.advance(amount);
        if (!interval.intervalElapsed() || target == null
                || !target.isAlive() || system.isActive()
                || system.isCoolingDown()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }
        ship.useSystem();
        LOG.info("GearIn range AI activated");
    }
}
```

:::

::: details 完整成品：GearInIdleProbeSystem

```java
package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class GearInIdleProbeSystem extends BaseShipSystemScript {

    private static final Logger LOG = Global.getLogger(GearInIdleProbeSystem.class);

    @Override
    public void apply(MutableShipStatsAPI stats, String id,
            State state, float effectLevel) {
        LOG.info("GearIn idle probe: state=" + state
                + " effectLevel=" + effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        LOG.info("GearIn idle probe: unapply");
    }

}
```

:::
