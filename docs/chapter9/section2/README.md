# 详解战术系统

本节为工匠实现能量射程增幅，处理加成、实际持续时间、状态显示与激活许可，再让自定义 AI 提出使用请求。另用一个待机观察系统说明 `runScriptWhileIdle` 怎样扩大调用范围，以及它为什么会改变效果的清理方式。

_本节预期阅读时间：\~24 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 配置战术系统数据和效果脚本；
- 根据调用时机实现效果脚本方法；
- 接入独立的自定义系统 AI；
- 根据日志和战斗结果定位回调问题。

## 前置知识

- [C2-S3] 的 `.system`、`ship_systems.csv`、`ship_data.csv` 与 `apply` / `unapply`；
- [C8-S2] 的 `MutableShipStatsAPI`、来源 ID 与 `modifyPercent`；
- [C8-S1] 的战斗引擎状态和暂停语义；
- [C7-S2] 的全局服务访问阶段。

## 建立范围增幅系统

范围增幅使用 `GearIn_range_system`，满效果时将能量武器射程乘以 1.5。普通运行经过前摇、全功率和后摇，效果也随强度进入与退出。玩家手动操作和 AI 使用同一项效果，激活判断则分别由按键与 AI 提交。

创建 `src/data/scripts/gearin/shipsystems/GearInRangeSystem.java` 和 `GearInRangeSystemAI.java`。效果类继承 `BaseShipSystemScript`，基类已为 `ShipSystemStatsScriptAdvanced` 提供默认实现，未覆写的方法继续采用默认返回值。

在 `data/shipsystems/GearIn_range_system.system` 写入定义：

```json
{
    "id": "GearIn_range_system",
    "type": "STAT_MOD",
    "aiType": "CUSTOM",
    "statsScript": "data.scripts.gearin.shipsystems.GearInRangeSystem",
    "aiScript": "data.scripts.gearin.shipsystems.GearInRangeSystemAI"
}
```

`statsScript` 连接效果类，`aiType: "CUSTOM"` 与 `aiScript` 连接自定义 AI。两个类各自实例化：效果类应用数值，AI 提出激活请求。确需共同使用的数据，通过参数或舰船 custom data 传递，实例字段不会自动共享。

向 `ship_systems.csv` 追加注册行，已有表头时只追加数据：

```csv
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
范围增幅,GearIn_range_system,1,,,,,,,1,0.1,1,5,1,10,FALSE,,,,,,,,,,,,
```

这条记录设置使用次数、恢复、前后摇、全功率、冷却与开关方式。将工匠在 `ship_data.csv` 中的 `system id` 改为 `GearIn_range_system`，它才会挂载本系统。文件引用与舰船挂载沿用 [C2-S3]，下面继续完成效果和 AI。

## 让射程随效果强度变化

系统进入效果阶段时，`apply(...)` 每帧接收本舰 stats、来源 `id`、当前 `state` 与 `effectLevel`。普通系统在 `IN`、`ACTIVE` 和 `OUT` 中应用效果，强度由 0 升至 1，再随后摇回落。

在 `GearInRangeSystem` 中加入局部方法：

```java
@Override
public void apply(MutableShipStatsAPI stats, String id,
        State state, float effectLevel) {
    stats.getEnergyWeaponRangeBonus().modifyMult(id,
            1f + RANGE_MULT_BONUS * effectLevel);
}
```

`RANGE_MULT_BONUS` 为 0.5，因此前摇中的倍率逐渐接近 1.5，全功率保持 1.5，后摇回到 1。射程收益取决于本帧强度，不能将前摇开始就当作已经获得全部增幅。

来源使用引擎传入的 `id`，结束时在同一属性解除它：

```java
@Override
public void unapply(MutableShipStatsAPI stats, String id) {
    stats.getEnergyWeaponRangeBonus().unmodify(id);
}
```

普通系统用 `unapply(...)` 完成撤销，舰船创建时也会调用这个入口。沿用稳定来源，重复应用只更新本系统的记录，结束后其他射程加成继续保留。

### 指定实际持续时间

数据表的全功率时间是 5 秒，本例用 `getActiveOverride(...)` 返回 6，使实际全功率持续 6 秒。其余覆盖方法返回负值，沿用数据中的前后摇、恢复速度与次数：

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

**非负覆盖值参与运行参数，负值保留数据设置。** 以后调整持续时间时，先确认效果类是否提供覆盖；数据行改动以后，实际时长仍可能由脚本决定。

## 告诉玩家系统处于什么状态

射程已经按阶段变化，界面还要说明当前效果与冷却。玩家舰的系统状态栏从 `getStatusData(...)` 取得内容，本例只在全功率阶段提供一条说明：

```java
@Override
public StatusData getStatusData(int index, State state, float effectLevel) {
    if (index == 0 && state == State.ACTIVE) {
        return new StatusData("range amplified", false);
    }
    return null;
}
```

`index` 选择状态条目，返回 `null` 表示当前索引没有内容。`StatusData` 的第二个参数决定是否采用减益样式，这里为 `false`。

系统图标下方的信息文字由 `getInfoText(...)` 提供。本例在冷却时显示 `RECHARGING`，其他时候返回 `null`，沿用默认 `READY` 或 `ACTIVE`：

```java
@Override
public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
    if (system.isCoolingDown()) {
        return "RECHARGING";
    }
    return null;
}
```

系统名称也能随阶段变化。在全功率时显示 `Range Amplified`，其他阶段沿用数据名称：

```java
@Override
public String getDisplayNameOverride(State state, float effectLevel) {
    return state == State.ACTIVE ? "Range Amplified" : null;
}
```

名称、图标信息与状态条目有各自的显示位置。这些方法返回文字，实际射程仍由前面的数值修饰提供。

## 在激活之前判断是否可用

玩家按键或 AI 提出激活请求后，`isUsable(...)` 提供额外许可。本例要求舰船有效且存活，系统尚未激活、也未冷却：

```java
@Override
public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
    return ship != null && ship.isAlive()
            && !system.isActive() && !system.isCoolingDown();
}
```

返回 `false` 时，本次请求被丢弃。返回 `true` 则允许继续处理，使用次数与幅能等系统条件仍然参与判断。因此它适合补充本功能的限制，不能将它的返回值当成最终激活结果。

## 给 AI 提供使用入口

`aiScript` 只有在 `aiType` 为 `CUSTOM` 时才进入自定义分支。`ShipSystemAIScript.init(...)` 在 AI 实例建立时保存当前舰船、系统和引擎，`advance(...)` 再根据当前目标决定是否提出请求。

在 `GearInRangeSystemAI` 中采用以下局部实现：

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

`target` 来自舰船 AI。`IntervalUtil` 将实际判断放到每隔 0.2 至 0.3 秒执行；暂停、无有效目标、系统已激活或冷却、舰船过载或散幅时结束本次判断。满足条件后调用 `ship.useSystem()`，提交与玩家按键相同的激活请求。

完整类的 `GearIn range AI activated` 日志记录调用发生，实际系统是否启动仍要结合次数、冷却、幅能与界面状态确认。玩家手动驾驶时不运行舰船 AI，将舰船交给自动驾驶或部署到 AI 一方，才能观察这条使用路径。

::: details 射程系统的使用判断

编者不会将 "有目标" 作为射程系统决策的终点。目标本就在当前武器的有效射程内，或武器尚未能够射击时，额外射程未必带来本次收益；目标距离与武器状态应当参与取舍。本例先验证自定义 AI 能提出请求，进一步的使用策略再根据实际舰船与武器组合设计。

:::

## 让脚本在待机时继续运行

普通效果在激活周期中执行。有些功能还需要处理待机和冷却，这时才采用 `runScriptWhileIdle`。创建 `GearInIdleProbeSystem.java`，先用它记录阶段，避免把观察逻辑与射程效果混在同一系统里。

在 `data/shipsystems/GearIn_idle_probe.system` 写入：

```json
{
    "id": "GearIn_idle_probe",
    "type": "STAT_MOD",
    "aiType": "NONE",
    "statsScript": "data.scripts.gearin.shipsystems.GearInIdleProbeSystem",
    "runScriptWhileIdle": true
}
```

注册行使用前面的 CSV 表头，追加以下数据：

```csv
待机观察,GearIn_idle_probe,1,,,,,,,1,0,1,2,1,4,FALSE,,,,,,,,,,,,
```

测试这项功能时，将工匠的 `system id` 改为 `GearIn_idle_probe`，重启后观察一轮。两个系统依次挂载，每次使用一个。

探针在字段中保存 `lastState`，只在阶段变化时输出日志：

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

`apply(...)` 仍然每帧执行，日志则只记录切换那一帧的阶段与强度。开启待机执行后，即使整场没有按系统键，也会收到 `IDLE` 调用；激活之后继续经过 `IN`、`ACTIVE`、`OUT` 与 `COOLDOWN`。

::: warning 待机执行改变了清理方式

**`runScriptWhileIdle` 不只是增加调用阶段，也会使普通推进不再调用 `unapply(...)`。** 需要撤销修饰或释放状态时，在 `apply(...)` 的待机与冷却分支处理。本探针保留 `unapply(...)`，用于比较它没有进入普通清理路径。

:::

这项开关扩大了持续工作的范围。全战场扫描或大量日志如果仍放在每帧中，待机期间也会支付相应代价。本例只记录阶段变化，其他高成本工作则按真实需要采用间隔执行。

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

- **仅出现激活阶段日志**：`runScriptWhileIdle` 未写在 `.system` 顶层，或值不是 `true`。
    - **解决方案**：在待机演示系统的 `.system` 顶层设置 `runScriptWhileIdle: true`，重新加载后搜索 `IDLE` 与 `COOLDOWN`。

- **系统每帧反复尝试激活**：自定义 AI 没有使用 `IntervalUtil`，或没有检查 `isActive()` 和 `isCoolingDown()`。
    - **解决方案**：保留决策间隔，并在调用 `ship.useSystem()` 前检查系统状态。

- **待机脚本造成战斗卡顿**：开启 `runScriptWhileIdle` 后仍在每帧执行全战场扫描或大量日志输出。
    - **解决方案**：缩短待机脚本逻辑，只读取必要状态，并将高成本工作改为间隔执行。

- **自定义 AI 保存的字段在 stats 脚本中为空**：stats 类与 AI 类是独立实例，实例字段不会互相共享。
    - **解决方案**：通过参数、静态工具方法或舰船 custom data 传递确需共享的信息。

## 总结

范围增幅从舰船挂载进入系统：效果类按阶段修改射程，覆盖方法决定实际时长，界面方法呈现状态，激活许可与系统条件共同处理请求。AI 的职责是选择何时提出这个请求，它使用独立实例。

待机观察则说明了另一种执行方式。开启 `runScriptWhileIdle` 后，`apply(...)` 继续处理待机和冷却，清理也随之进入阶段分支。采用这个开关时，持续范围与撤销方式需要一同设计。

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
