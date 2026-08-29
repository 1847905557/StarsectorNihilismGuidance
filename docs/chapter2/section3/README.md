# 制作最小战术系统

本节将以原版 `burn drive` 为基础，复制改造出一个新的最小战术系统，并解释 `.system`、`ship_systems.csv`、`ship_data.csv` 与效果脚本。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出战术系统从 `.system` 文件到效果脚本的完整流程：`.system` 文件 (类型与脚本)→ `ship_systems.csv`(名称与数值)→ `ship_data.csv`(挂载到舰船)→ 效果脚本类；
- 说出时间类参数各管什么：`charge up`(前摇)、`active`(全功率)、`down`(后摇)、`cooldown`(冷却)；
- 用 "效果强度" 理解效果脚本：前摇期间加成从 0 平滑升至满值，后摇期间回落；
- 通过修改三个参数 (时长、加成数值、冷却) 在模拟战中观察到行为差异。

## 前置知识

- [C1-S2] 的舰船数据与 `ship_data.csv` 登记规则；
- [C2-S1] 的 Java 模板、参数、注册与回调；
- [C2-S2] 的文件名、类名与注册类名一致规则。

## 战术系统基本链路总览

一个战术系统至少由四部分组成，按顺序搭建：

1. `data/shipsystems/<id>.system`：JSON 文件，声明系统类型、效果脚本类名与视觉参数；
2. `data/shipsystems/ship_systems.csv`：注册行，提供名称、时长、幅能等数值参数；
3. `data/hulls/ship_data.csv`：舰船行的 `system id` 列，把系统挂载到具体舰船上；
4. 效果脚本类：`.system` 的 `statsScript` 写它的完整类名，战斗期间由游戏按状态回调。

按下按键后的过程依次经过**前摇 (效果爬升)→ 全功率 (效果满值)→ 后摇 (效果回落)→ 冷却**。`ship_systems.csv` 里的 `charge up`/`active`/`down`/`cooldown` 四个数字就是各状态持续的秒数。

## 复制战术系统模板

把原版 `data/shipsystems/burndrive.system` 复制到 MOD 的 `data/shipsystems` 下，改名为 `GearIn_burstdrive.system`，并把 `id` 与 `statsScript` 两处改为本节的值：

```json {2,6}
{
    "id": "GearIn_burstdrive",
    "type": "ENGINE_MOD",
    "aiType": "BURN_DRIVE_TOGGLE",

    "statsScript": "data.scripts.gearin.shipsystems.GearInBurstDriveSystem",

    "engineGlowLengthMult": 2.5,
    "engineGlowWidthMult": 3,
    "engineGlowGlowMult": 2,

    "flameoutOnImpactChance": 0.5,

    "clampTurnRateAfter": true,
    "clampMaxSpeedAfter": true,
    "alwaysAccelerate": true,

    "useSound": "system_burn_drive_activate",
    "loopSound": "system_burn_drive_loop",
    "deactivateSound": "system_burn_drive_deactivate",
    "outOfUsesSound": "engine_disabled"
}
```

本节只关注三个字段，其余按模板使用：

- **`id`**：系统 ID，与 `ship_systems.csv` 的 `id` 必须一致。后续 `ship_data.csv` 引用时也必须一致。
- **`type`**：系统运作方式。`ENGINE_MOD` 是引擎类系统，体现为需要引擎上线才能启动；大多数情况下使用 `STAT_MOD`。
- **`statsScript`**：效果脚本完整类名。

其余字段是视觉 (引擎焰)、声音与物理细节，按需保留。文件中的 `aiType` 字段属于系统 AI 配置，本教程在 [C8-S2] 讲解。

## 注册战术系统参数

在 MOD 的 `data/shipsystems/ship_systems.csv` 里，从原版复制 `Burn Drive` 那一行，只改 `name` 与 `id`：

```text {5}
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
,,,,,,,,,,,,,,,,,,,,,,,,,,,
Burn Drive,burndrive,1,,,,,,,,,2,5,1,10,TRUE,,,,,TRUE,TRUE,TRUE,TRUE,,,movement,graphics/icons/hullsys/burn_drive.png
,,,,,,,,,,,,,,,,,,,,,,,,,,,
燃烧驱动,GearIn_burstdrive,1,,,,,,,,,2,5,1,10,TRUE,,,,,TRUE,TRUE,TRUE,TRUE,,,movement,graphics/icons/hullsys/burn_drive.png
```

### 常用参数速查表

最常用的五个参数 (后续微调数据也基本上是调这几个)：

| 列 | 作用 | 本行值 |
|---|---|---|
| `charge up` | 前摇：按下到全功率的秒数，期间效果从 0 爬升至满 | 2 |
| `active` | 全功率：维持满效果的秒数 | 5 |
| `down` | 后摇：效果回落到 0 的秒数 | 1 |
| `cooldown` | 冷却：结束后到能再次激活的秒数 | 10 |
| `flux/second` | 激活期间每秒产生的幅能 | 1 |

其余参数：

- **`toggle`**：`TRUE` = 开关式，再按一次才关闭 (本系统如此)；留空 = 非开关式，按一次后完整运行 `charge up → active → down → cooldown` 周期。
- **`max uses`**：充能次数，留空 = 无限。
- **`noAccel`/`noFiring`/`noTurning` 等 `no*` 列**：激活期间禁止的行为，`TRUE` = 禁止。本行禁止了转向、平移、加速与护盾；而开火不受禁止。

`f/s`、`f/u` 系列与 `cr/u` 等列暂时不讲。

## 实现战术系统效果

在 `src/data/scripts/gearin/shipsystems/GearInBurstDriveSystem.java` 建类：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInBurstDriveSystem extends BaseShipSystemScript {

    public static final float SPEED_BONUS = 200f;
    public static final float ACCELERATION_BONUS = 200f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
            stats.getAcceleration().modifyFlat(id, ACCELERATION_BONUS * effectLevel);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("engine power increased", false);
        }
        return null;
    }
}
```

您应该已经能猜出 `SPEED_BONUS` 和 `ACCELERATION_BONUS` 分别是速度和加速度的加成了。除此之外，可以开始逐步介绍关键概念：

**启动 `apply`**

效果期间每帧被调用。在本例中，按当前 `effectLevel` 写入速度与加速度加成。`state == OUT`(后摇) 时额外处理立刻**撤销**速度加成，这样速度衰减会更可感知一点。

**结束 `unapply`**

效果结束时由游戏调用一次。一般来说用于清除 `apply` 里的效果。在舰船被部署/生成时其实也会调用一次。

**文本说明 `getStatusData`**

- 左下角状态栏文字 (`false` = 增益显示，`true` = 红色减益)。

**效果强度 `effectLevel`**

游戏在前摇期间把一个名为 `effectLevel` 的值从 **0 平滑升至 1**，全功率段保持 1，后摇期间从 1 落回 0。一般来说，效果脚本中的所有数值都**应该**乘以它：`SPEED_BONUS * effectLevel` = 200 × 0.5 = 100—— 因此前摇那两秒里速度加成 **从 0 平滑爬到 200**。

::: warning apply 与 unapply 必须成对

`apply` 里 `modify` 过什么，`unapply` 里就必须用同一个 `id` `unmodify` 什么。漏一项的后果：系统结束后那一项加成永远残留。

:::

## 挂载战术系统

打开 MOD 的 `data/hulls/ship_data.csv`，把 `工匠-级` 行的 `system id` 列 (第 5 列) 改为 `GearIn_burstdrive`：

```text {2}
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
工匠,GearIn_artizan,驱逐舰,未知,GearIn_burstdrive,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,5,12,360,0.25,10,10,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
```

不过，在进入游戏之前，我们先把另一个战术系统也做好。

## 第二个例子：右键系统 (抵抗力场)

右键系统与主系统是同一套机制，只是填写在 `ship_data.csv` 的另一列里。当 `shield type` 为 `PHASE` 时，`defense id` 列填写的系统 ID 就是右键系统。

把原版 `damper.system` 复制为 `GearIn_resistfield.system`，改 `id` 与 `statsScript`：

```json {2,6}
{
    "id": "GearIn_resistfield",
    "type": "STAT_MOD",
    "aiType": "DAMPER_FIELD",

    "statsScript": "data.scripts.gearin.shipsystems.GearInResistFieldSystem",

    "jitterUnderColor": [255, 165, 90, 155],
    "jitterUnderCopies": 25,
    "jitterUnderMinRange": 0,
    "jitterUnderRange": 7,
    "jitterUnderRangeRadiusFraction": 0,

    "jitterColor": [255, 165, 90, 55],
    "jitterCopies": 2,
    "jitterMinRange": 0,
    "jitterRange": 5,
    "jitterRangeRadiusFraction": 0,

    "soundFilterType": "LOWPASS",
    "soundFilterGain": 0.75,
    "soundFilterGainHF": 0.25,

    "fadeActivationSoundOnChargedown": true,
    "useSound": "system_damper",
    "loopSound": "system_damper_loop",
    "deactivateSound": "system_damper_off",
    "outOfUsesSound": "gun_out_of_ammo"
}
```

`ship_systems.csv` 加上从 `阻尼力场` 复制的一行，并修改改 `name` 与 `id`：

```text {3}
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
,,,,,,,,,,,,,,,,,,,,,,,,,,,
抵抗力场,GearIn_resistfield,1,,,,,,,2,0.05,0.25,3,0.25,0,,,,,TRUE,,,,TRUE,TRUE,,"uses_damper_ai, defensive",graphics/icons/hullsys/damper_field.png
```

效果脚本 `GearInResistFieldSystem` 令人感到十分眼熟：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInResistFieldSystem extends BaseShipSystemScript {

    public static final float HE_REDUCTION = 75f;
    public static final float EMP_REDUCTION = 75f;
    public static final float OTHER_REDUCTION = 50f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getHighExplosiveDamageTakenMult().modifyMult(id, 1f - HE_REDUCTION * 0.01f * effectLevel);
        stats.getKineticDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
        stats.getEnergyDamageTakenMult().modifyMult(id, 1f - OTHER_REDUCTION * 0.01f * effectLevel);
        stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_REDUCTION * 0.01f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getHighExplosiveDamageTakenMult().unmodify(id);
        stats.getKineticDamageTakenMult().unmodify(id);
        stats.getEnergyDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("reduce damage taken", false);
        }
        return null;
    }
}
```

最后把 `工匠-级` 的 `shield type` 改为 `PHASE`、`defense id` 填 `GearIn_resistfield`，这艘船就同时拥有主系统与右键系统。

## 验证方案

前置数据：本节注册并挂载的 `GearIn_burstdrive` 与 `GearIn_resistfield` 战术系统。

1. 重启游戏，在 `starsector.log` 中观察战术系统加载记录。
2. 进入模拟战，按 F 激活和关闭燃烧驱动，观察前摇、后摇与冷却。
3. 调整 `SPEED_BONUS`、`active` 或 `cooldown` 后重启，比较行为差异。
4. 使用右键抵抗力场，观察状态文字、持续时间与减伤效果。

预期结果：

- 日志出现 `Loading ship system [data/shipsystems/GearIn_burstdrive.system]` 与 `Applying data from ship_systems.csv to [GearIn_burstdrive]`，且没有相关 `ERROR`。
- 前摇约 2 秒内速度与加速度平滑升至 +200；左下角状态栏出现 `engine power increased`；开关式 (`toggle=TRUE`) 再按 F 关闭。
- 关闭后约 1 秒后摇内按基础速度减速；随后约 10 秒冷却，冷却结束可再次激活。
- 参数实验 (`SPEED_BONUS`、`active`、`cooldown`) 各自产生可感知的行为差异。
- 状态文字、时长与减伤数值 (高爆/EMP 最高 75%、动能/能量最高 50%) 符合预期。

## 故障分析

- **游戏加载时报 `Fatal: ship_systems.csv is missing systems`**：`.system` 已创建，但 CSV 没有对应行或行首仍有 `#`。

    - **解决方案**：补回 `GearIn_burstdrive` 数据行并取消注释，重启。

- **系统数值全为零、按键瞬间结束**：CSV 行与 `.system` 两处 ID 一致、但 `charge up`/`active`/`down`/`cooldown` 数值为空时，状态机一帧内跑完整个周期。注意：两处 ID 不一致不会造成 "瞬间结束"—— 加载期即抛 `Fatal: ship_systems.csv is missing systems` 中断 (日志至多先出现一行 `not found in store` 警告)。

    - **解决方案**：统一两处 ID 并填上数值，再重新进入战斗。

- **舰船部署时报 `System with id [...] not found`**：船体的 `system id` 拼错，或对应系统尚未注册。

    - **解决方案**：核对 `.ship`、`.system` 与 CSV 三处 ID，修正后重新部署。

- **冷却结束后效果仍然残留**：`unapply` 没有撤销 `apply` 写入的修饰。

    - **解决方案**：使用同一 source id 成对撤销，再次完整激活并等待冷却结束。

- **日志出现 `Error loading [...]`**：`statsScript` 类名、包声明和文件路径不一致，或文件错误地放在 `src\` 前缀下。

    - **解决方案**：将类放在 MOD 根目录对应包路径，统一三处名称后重启。

## 总结

战术系统的生效流程：`.system`(类型与脚本)→ `ship_systems.csv`(名称与数值)→ `ship_data.csv`(挂载)→ 效果脚本 (响应状态回调)。

脚本侧只需要掌握 "效果强度" 一个概念：`effectLevel` 从 0 升至 1 再落回 0，所有数值乘以它；`apply`/`unapply` 成对撤销是唯一的纪律。

这条流程是各类系统的共同骨架 —— 武器类、护盾类、相位类系统都只是更换数据与脚本内容。系统 AI(`aiType`) 与自定义使用时机见 [C8-S2]。

## 建议参考项

- `data/shipsystems/<id>.system`、`data/shipsystems/ship_systems.csv`、`data/hulls/ship_data.csv`：注册战术系统需要的文件。
- `data/shipsystems/burndrive.system`、`damper.system`(原版)：本节两个示例的复制来源。
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：效果脚本模板基类。
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：效果脚本接口 (`State` 枚举、`apply`/`unapply`/`getStatusData`)。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：数值入口 (`getMaxSpeed()`、`getHighExplosiveDamageTakenMult()` 等)。
- `data/scripts/gearin/shipsystems`：本节两个效果脚本类的存放目录。
