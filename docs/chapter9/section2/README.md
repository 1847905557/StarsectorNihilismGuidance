# 详解战术系统

系统的数值效果已经能运行，接下来决定它怎样显示、何时允许激活，以及 AI 怎样使用它。本节给工匠换上能量射程增幅系统，再用一个待机探针观察完整阶段。效果类与 AI 类各自接入，时间覆盖、界面文字和清理也沿着实际激活过程安排。

*本节预期阅读时间：\~24 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 配置战术系统数据和效果脚本；
- 根据调用时机实现效果脚本方法；
- 接入独立的自定义系统 AI；
- 根据日志和战斗结果定位回调问题。

## 前置知识

- [C2-S3] 的 `.system`、`ship_systems.csv`、`ship_data.csv` 与 `apply` / `unapply`；
- [C8-S4] 的 `MutableShipStatsAPI`、来源 ID 与 `modifyPercent`；
- [C8-S1] 的战斗引擎状态和暂停语义；
- [C7-S2] 的全局服务访问阶段。

## 登记战术系统

先保存两个定义文件，并向已有 CSV 追加两行。主系统增加能量武器射程，待机系统记录阶段；测试时依次把工匠的 `system id` 换成对应 ID，每次构建、重启后观察一种系统。文件与舰船挂载沿用 [C2-S3]。

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

`statsScript` 决定激活期间修改舰船数值的类，`aiType: "CUSTOM"` 让舰船 AI 进入自定义分支，`aiScript` 决定由哪个 `ShipSystemAIScript` 做激活判断。持续时间、使用次数与开关方式写进 `ship_systems.csv`。当前使用完整表头，已有表头时只追加数据行：

```csv
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
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

普通系统在 `IN`、`ACTIVE` 与 `OUT` 阶段调用 `apply(...)`。**开启 `runScriptWhileIdle` 后，`IDLE` 和 `COOLDOWN` 也继续调用它，清理则由 `apply(...)` 的阶段分支处理。** 该选项使 `unapply(...)` 停止调用，本节探针保留这个方法用于观察区别。

待机时也运行，就意味着即使玩家整场没按系统键，脚本仍在工作。编者建议按状态变化记录探针日志，实际效果也只处理当前阶段需要的动作。

待机系统的数据行只负责提供名称和时间：

```csv
待机观察,GearIn_idle_probe,1,,,,,,,1,0,1,2,1,4,FALSE,,,,,,,,,,,,
```

## 把效果与 AI 分别保存

`BaseShipSystemScript` 为 `ShipSystemStatsScriptAdvanced` 提供默认实现。继承它后，类只覆写当前效果需要的方法；未覆写的方法继续使用默认返回值。

将 `GearInRangeSystem`、`GearInRangeSystemAI` 和 `GearInIdleProbeSystem` 保存到 `src/data/scripts/gearin/shipsystems/`。系统创建时取得效果实例，自定义 AI 另行初始化。**效果类负责加成，AI 类负责提出激活请求。** 两个实例的字段分别保存，确需共享的数据通过参数或舰船 custom data 传递。

## 在效果阶段修改舰船属性

`apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)` 在效果阶段逐帧调用。`state` 表示前摇、全功率或后摇，`effectLevel` 在这些阶段从 0 变化到 1 再回落。本例使用传入的 `id` 修改能量武器射程倍率，局部方法如下：

```java
@Override
public void apply(MutableShipStatsAPI stats, String id,
        State state, float effectLevel) {
    stats.getEnergyWeaponRangeBonus().modifyMult(id,
            1f + RANGE_MULT_BONUS * effectLevel);
}
```

`RANGE_MULT_BONUS` 为 0.5，前摇从 1 倍升到 1.5 倍，全功率保持，后摇回落。射程收益随阶段变化，实际激活时机也就影响能否打到目标。

普通系统用 `unapply(MutableShipStatsAPI stats, String id)` 解除自己的来源，舰船创建时也会调用它。局部方法如下：

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

`isUsable(ShipSystemAPI system, ShipAPI ship)` 在激活请求被接受前查询。返回 `false` 时，按键请求会被丢弃；本例要求舰船存活，系统处于非激活、非冷却状态。这个返回值提供额外许可，系统自身的次数与幅能条件仍参与激活判断。

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

本例 CSV 的全功率时间为 5 秒，`getActiveOverride(...)` 返回 6，使实际全功率持续 6 秒。**脚本覆盖值参与运行参数，负值则沿用数据值。** 其余前后摇、恢复速度和次数都返回负值，便于在同一系统里比较数据与覆盖：

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
    if (engine.isPaused()) {
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

`target` 来自当前舰船 AI。每隔 0.2 到 0.3 秒，目标有效、系统非激活且非冷却、舰船未过载或散幅时，调用 `ship.useSystem()` 提出请求。它经过与玩家按键相同的激活入口。

这个最小判断只确认有目标；射程增幅是否值得现在使用，还可以结合目标距离与武器状态取舍。玩家手动驾驶时不会运行舰船 AI，交给自动驾驶或部署到 AI 一方时再观察这段逻辑。

## 观察待机回调

探针只观察阶段，不修改属性。在类字段区保存 `lastState`，`apply(...)` 每帧仍会执行，日志只在阶段切换时输出。记录的 `effectLevel` 是切换那一帧的值，便于把阶段和强度对应起来：

```java
@Override
public void apply(MutableShipStatsAPI stats, String id,
        State state, float effectLevel) {
    if (state != lastState) {
        LOG.info("GearIn idle probe: state=" + state
                + " effectLevel=" + effectLevel);
        lastState = state;
    }
}

@Override
public void unapply(MutableShipStatsAPI stats, String id) {
    LOG.info("GearIn idle probe: unapply");
}
```

需要撤销数值或释放状态的脚本，应在 `apply(...)` 的状态分支中自行维护。

## 完整最小例

三个类保存到前述路径，分别接入主系统效果、系统 AI 与待机探针。

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
        if (engine.isPaused()) {
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
    private State lastState;

    @Override
    public void apply(MutableShipStatsAPI stats, String id,
            State state, float effectLevel) {
        if (state != lastState) {
            LOG.info("GearIn idle probe: state=" + state
                    + " effectLevel=" + effectLevel);
            lastState = state;
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        LOG.info("GearIn idle probe: unapply");
    }

}
```

:::

## 验证方案

前置数据：[C4-S1] 的 `GearIn_test` 与工匠装配；已登记两个系统，并将工匠的 `system id` 设为 `GearIn_range_system`。

1. 执行 `gradle jar`，确认 JAR 包含三个示例类。
2. 进入模拟战，激活 `GearIn_range_system`，观察前摇、全功率、后摇和冷却阶段。
3. 核对主系统的武器射程倍率、`range amplified` 状态文字和 `RECHARGING` 信息文字。
4. 将工匠交给自动驾驶或 AI 一方，有存活目标时观察 `GearIn range AI init` 与 `GearIn range AI activated`；确认每次实际激活仍受次数、冷却与幅能条件影响。
5. 将工匠的 `system id` 改为 `GearIn_idle_probe`，重启进入战斗；激活并等待冷却结束，搜索 `GearIn idle probe` 比较阶段切换。
6. 暂停战斗并恢复，比较普通系统和待机演示系统的回调日志。
7. 结束战斗后开始第二场战斗，确认 stats 与 AI 的实例状态重新建立。

预期结果：

- 主系统能量射程倍率由 1 平滑增至 1.5 再回落，结束时解除来源；脚本把全功率时间覆盖为 6 秒。
- 待机探针每次阶段切换记录一次，`unapply` 的调用数量为 0。
- 玩家界面出现 `range amplified` 和 `RECHARGING`；
- AI 舰船按节流间隔调用 `ship.useSystem()`，而不是每帧重复尝试；
- 待机演示系统在 `IDLE`、`IN`、`ACTIVE`、`OUT` 和 `COOLDOWN` 阶段输出日志；
- 第二场战斗使用新的脚本实例和新的战斗对象。

## 故障分析

- **系统加载时报 `Problem loading class`**：`.system` 中的 `statsScript`、`aiScript` 或包名与 JAR 中的类不一致。
    - **解决方案**：逐字核对完整类名、`package` 声明和 JAR 条目后重启。

- **AI 舰船从不激活系统**：`aiType` 不是 `CUSTOM`，或 `advance(...)` 的目标、冷却和存活检查始终返回。
    - **解决方案**：将 `aiType` 改为 `CUSTOM`，确认目标有效并用日志检查节流后的决策时机。

- **系统结束后射程倍率仍然存在**：`unapply(...)` 没有用与 `apply(...)` 相同的 来源 ID 撤销。
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

主系统把能量射程效果、状态文字和自定义 AI 分别接到对应入口，实际持续时间同时受数据与脚本覆盖影响。AI 提出激活请求，效果类按阶段写入与解除数值，各自保存当前实例状态。

待机探针则展示另一种调用范围：`apply(...)` 接收完整周期，清理也由阶段分支承担。选择这个开关时，把运行范围、清理位置和每帧代价一起安排。

## 建议参考项

- `data/shipsystems/<id>.system`：战术系统数据文件，确定 stats 脚本、AI 脚本和待机调用范围；
- `data/shipsystems/ship_systems.csv`：战术系统注册表；
- `data/hulls/ship_data.csv`：舰船数据表，挂载系统的运行参数来源；
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：舰船数值修饰入口；
- `com.fs.starfarer.api.combat.MutableStat`：单条数值的修饰对象；
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：效果脚本基类；
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：状态、效果和状态栏接口；
- `com.fs.starfarer.api.plugins.ShipSystemStatsScriptAdvanced`：`isUsable` 与 `getInfoText`；
- `com.fs.starfarer.api.combat.ShipSystemAIScript`：`init` 与 `advance`；
- `com.fs.starfarer.api.combat.ShipSystemAPI`：状态、冷却和激活访问器。
