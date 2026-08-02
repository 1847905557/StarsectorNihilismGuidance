# 最小战术系统：数据注册与效果脚本

船插是安装后持续的加成；战术系统是按下按键后触发的一段行为——爆发加速、护盾强化、相位隐身。本节从原版 `burn drive` 复制改造一个最小战术系统，覆盖 `.system`、`ship_systems.csv`、`ship_data.csv` 与效果脚本四段链路，并通过调整参数观察游戏内行为变化。后半部分给出常见错误的排查方法。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出战术系统的四段链路：`.system` 文件（类型与脚本）→ `ship_systems.csv`（名称与数值）→ `ship_data.csv`（挂载到舰船）→ 效果脚本类；
- 说出时间类参数各管什么：`charge up`（前摇）、`active`（全功率）、`down`（后摇）、`cooldown`（冷却）；
- 用"效果强度"理解效果脚本：前摇期间加成从 0 平滑升至满值，后摇期间回落；
- 通过修改三个参数（时长、加成数值、冷却）在模拟战中观察到行为差异。

## 前置知识

- 舰船与 `ship_data.csv`（C01-S02）：本节把系统挂载到 `工匠-级` 上；
- 模板与参数、类名三处一致（C02-S01、C02-S02）：本节的效果脚本类同样遵守。

## 四段链路总览

一个战术系统由四部分组成，按顺序搭建：

1. `data/shipsystems/<id>.system`：JSON 文件，声明系统类型、效果脚本类名与视觉参数；
2. `data/shipsystems/ship_systems.csv`：注册行，提供名称、时长、幅能等数值参数；
3. `data/hulls/ship_data.csv`：舰船行的 `system id` 列，把系统挂载到具体舰船上；
4. 效果脚本类：`.system` 的 `statsScript` 写它的完整类名，战斗期间由游戏按状态回调。

按下按键后的过程分为四段：**前摇（效果爬升）→ 全功率（效果满值）→ 后摇（效果回落）→ 冷却**。`ship_systems.csv` 里的 `charge up`/`active`/`down`/`cooldown` 四个数字就是这四段各持续几秒。

## 第一步：复制并改名 .system 文件

把原版 `Starsector\starsector-core\data\shipsystems\burndrive.system` 复制到 MOD 的 `data\shipsystems` 下，改名为 `GearIn_burstdrive.system`，内容整理为如下形式（`id` 与 `statsScript` 两处已改）：

```json
{
    "id": "GearIn_burstdrive",
    "type": "ENGINE_MOD",
    "aiType": "NONE",

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

- **`id`**：系统 ID，与 CSV 行、`ship_data.csv` 引用的是同一份键（类名三处一致的战术系统版：`.system` 的 `id`、`ship_systems.csv` 的 `id`、`ship_data.csv` 的 `system id` 三处必须一致）。
- **`type`**：系统运作方式。`ENGINE_MOD` 是引擎类系统（引擎焰视觉、撞击熄火由引擎处理）；纯属性类系统用 `STAT_MOD`。本节示例与第二个例子各演示一种。
- **`statsScript`**：效果脚本完整类名（三处一致的又一处：`statsScript`、`package` 行、文件路径）。

其余字段是视觉（引擎焰）、声音与物理细节，按需保留。文件中的 `aiType` 字段属于系统 AI 配置，本教程在 C07 讲解——示例用 `NONE`（无 AI 行为），玩家按键不受影响。

## 第二步：注册行（数值参数）

在 MOD 的 `data\shipsystems\ship_systems.csv` 里，从原版复制 `#Burn Drive` 那一行，只改 `name` 与 `id`：

```text
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
,,,,,,,,,,,,,,,,,,,,,,,,,,,
#Burn Drive,burndrive,1,,,,,,,,,2,5,1,10,TRUE,,,,,TRUE,TRUE,TRUE,TRUE,,,movement,graphics/icons/hullsys/burn_drive.png
,,,,,,,,,,,,,,,,,,,,,,,,,,,
燃烧驱动,GearIn_burstdrive,1,,,,,,,,,2,5,1,10,TRUE,,,,,TRUE,TRUE,TRUE,TRUE,,,movement,graphics/icons/hullsys/burn_drive.png
```

最常用的五个参数（修改它们即调整行为）：

| 列 | 作用 | 本行值 |
|---|---|---|
| `charge up` | 前摇：按下到全功率的秒数，期间效果从 0 爬升至满 | 2 |
| `active` | 全功率：维持满效果的秒数 | 5 |
| `down` | 后摇：效果回落到 0 的秒数 | 1 |
| `cooldown` | 冷却：结束后到能再次激活的秒数 | 10 |
| `flux/second` | 激活期间每秒产生的幅能（代价） | 1 |

其余参数：

- **`toggle`**：`TRUE` = 开关式，再按一次才关闭（本系统如此）；留空 = 按时开、松手关。
- **`max uses`**：充能次数，留空 = 无限。
- **`noAccel`/`noFiring`/`noTurning` 等 `no*` 列**：激活期间禁止的行为，`TRUE` = 禁止。本行禁止了开火、转向、平移与加速——激活期间无法进行这些操作。

`f/s`、`f/u` 系列与 `cr/u` 等列按模板使用，涉及幅能费率的细节后续章节展开。复制原版行时保留表头与 `#` 注释行，只改目标行的 `name` 与 `id`。

## 第三步：效果脚本

在 `src\data\scripts\gearin\shipsystems\GearInBurstDriveSystem.java` 建类，完整内容如下：

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

本节展开两个概念，其余按模板使用：

**① 效果强度 `effectLevel`**

游戏在前摇期间把一个名为 `effectLevel` 的值从 **0 平滑升至 1**，全功率段保持 1，后摇期间从 1 落回 0。效果脚本中的所有数值都乘以它：`SPEED_BONUS * effectLevel` = 200 × 0.5 = 100——因此前摇那两秒里速度加成从 0 平滑爬到 200。**参数是 `SPEED_BONUS` 与 `ACCELERATION_BONUS` 两个数字**，修改它们即修改爆发强度。

**② 三个回调各管一件事**

- `apply`：效果期间每帧被调用，按当前 `effectLevel` 写入速度与加速度加成。`state == OUT`（后摇）时**撤销**速度加成——让船在后摇期间按基础速度减速，而不是带着加成跑完整个后摇。
- `unapply`：效果结束时由游戏调用一次，撤销 `apply` 写入的每一项（`modifyFlat` 与 `unmodify` 必须成对，用同一个 `id`）。
- `getStatusData`：左下角状态栏文字（`false` = 增益显示，`true` = 红色减益）。

::: warning apply 与 unapply 必须成对

`apply` 里 `modify` 过什么，`unapply` 里就必须用同一个 `id` `unmodify` 什么。漏一项的后果：系统结束后那一项加成永远残留（见故障分析第 4 条）。

:::

## 第四步：挂载到舰船

打开 MOD 的 `data\hulls\ship_data.csv`，把 `工匠-级` 行的 `system id` 列（第 5 列）改为 `GearIn_burstdrive`：

```text
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
工匠,GearIn_artizan,驱逐舰,未知,GearIn_burstdrive,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,5,12,360,0.25,10,10,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
```

至此四段链路完成。重启游戏验证：

1. 日志出现 `Loading ship system [data/shipsystems/GearIn_burstdrive.system]` 与 `Applying data from ship_systems.csv to [GearIn_burstdrive]`，无 `ERROR`；
2. 控制台 `Addship GearIn_artizan` 进模拟战，按 U：约 2 秒前摇内速度平滑升至 +200，左下角出现 `engine power increased`；本系统是开关式，再按 U 关闭；
3. 关闭后约 1 秒后摇内按基础速度减速，随后约 10 秒冷却，HUD 显示冷却进度。

## 参数调整实验

三个实验，每次只改一个地方，重启进模拟战观察差异：

1. **调整爆发强度**：`SPEED_BONUS` 从 `200f` 改 `350f`——前摇爬升更陡，全功率速度更高；
2. **调整持续时间**：CSV 的 `active` 从 `5` 改 `2`——全功率段明显缩短；改 `10` 则长时间维持；
3. **调整冷却节奏**：`cooldown` 从 `10` 改 `3`——激活间隔明显缩短，可连续使用。

把 `cooldown` 从 10 改到 3，激活间隔明显缩短——参数改变行为，本节到此为止的结论也就这些。爆发类系统通常用短 `charge up` + 高 `SPEED_BONUS` 做出快速弹射；持续类系统通常用长 `active` + 较低数值维持稳定输出。后续章节制作自己的系统时，这些数字就是调整行为的全部工具。

## 第二个例子：右键系统（抵抗力场）

右键系统与主系统是同一套机制，只是由 `ship_data.csv` 的另一列挂载（`shield type` 为 `PHASE` 时，`defense id` 列填写的系统 ID 就是右键系统）。把原版 `damper.system` 复制为 `GearIn_resistfield.system`，改 `id` 与 `statsScript`（`type` 用 `STAT_MOD`——纯属性类系统；其余字段按模板使用）：

```json
{
    "id": "GearIn_resistfield",
    "type": "STAT_MOD",
    "aiType": "NONE",

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

`ship_systems.csv` 加上从 `阻尼力场` 复制的一行（只改 `name` 与 `id`；`tags` 字段含逗号，必须用引号包住整个字段）：

```text
name,id,flux/second,f/s (base rate),f/s (base cap),flux/use,f/u (base rate),f/u (base cap),cr/u,max uses,regen,charge up,active,down,cooldown,toggle,noDissipation,noHardDissipation,hardFlux,noFiring,noTurning,noStrafing,noAccel,noShield,noVent,isPhaseCloak,tags,icon
,,,,,,,,,,,,,,,,,,,,,,,,,,,
抵抗力场,GearIn_resistfield,1,,,,,,,2,0.05,0.25,3,0.25,0,,,,,TRUE,,,,TRUE,TRUE,,"uses_damper_ai, defensive",graphics/icons/hullsys/damper_field.png
```

效果脚本 `GearInResistFieldSystem` 与燃烧驱动同构——`apply` 按 `effectLevel` 写入四类减伤倍率，`unapply` 逐一撤销，`getStatusData` 返回状态文字（`modifyMult` 是"乘以多少"的句式：`1f - 75% × effectLevel` = 满效果时受伤 ×0.25）：

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

最后把 `工匠-级` 的 `shield type` 改为 `PHASE`、`defense id` 填 `GearIn_resistfield`，这艘船就同时拥有主系统与右键系统。右键验证：进模拟战按右键，出现 `reduce damage taken` 状态文字与阻尼力场同款视觉，高爆/EMP 减伤最高 75%、动能/能量最高 50%。

## 错误对照表

| 症状 | 原因 | 修法 |
|---|---|---|
| 游戏无法启动，日志 `Fatal: ship_systems.csv is missing systems` | `.system` 已建但 CSV 没有对应 `id` 行（或行首残留 `#`） | 在 CSV 补同名 `id` 行；检查行首无 `#` |
| 系统存在但数值全为 0，按键瞬间结束 | CSV 的 `id` 与 `.system` 的 `id` 不一致 | 两个 `id` 逐字符一致（注意大小写） |
| 舰船部署时报 `System with id [...] not found` | `ship_data.csv` 的 `system id` 打错或系统未注册 | 核对三处 `id`：`.system`、CSV 行、`system id` 列 |
| 系统结束后效果残留 | `unapply` 漏掉了 `apply` 里的某项 `unmodify` | `apply` 里每个 `modify` 都有对应 `unmodify` |
| `Problem loading class [...]` | `statsScript`、包声明、文件路径不一致 | 按类名三处一致核对 |

## 预期结果

1. 日志出现 `Loading ship system [data/shipsystems/GearIn_burstdrive.system]` 与 `Applying data from ship_systems.csv to [GearIn_burstdrive]`，无 `ERROR`；
2. 模拟战按 U：前摇约 2 秒内速度与加速度平滑升至 +200；左下角状态栏出现 `engine power increased`；开关式（`toggle=TRUE`）再按 U 关闭；
3. 关闭后约 1 秒后摇内按基础速度减速；随后约 10 秒冷却，冷却结束可再次激活；
4. 参数实验（`SPEED_BONUS`、`active`、`cooldown`）各自产生可感知的行为差异；
5. 右键抵抗力场：状态文字、时长与减伤数值（高爆/EMP 最高 75%、动能/能量最高 50%）符合预期。

## 实例驱动故障分析

1. **游戏加载直接失败（`Fatal: ship_systems.csv is missing systems`）**。触发：`.system` 已建但 CSV 无对应行，或行首残留 `#`。可观察：`Ship system [GearIn_burstdrive] not found in ship_systems.csv` 后跟 Fatal。修正：CSV 补行、去 `#`。
2. **系统表现异常（数值全 0）**。触发：CSV 行 `id` 与 `.system` `id` 不一致。可观察：警告 `not found in store`，系统时长全 0、按键瞬间结束。修正：两处 `id` 逐字符一致。
3. **舰船无法部署（`System with id [...] not found`）**。触发：`system id` 打错或系统未注册。修正：三处 `id` 一致。
4. **效果残留**。触发：`unapply` 漏撤销。可观察：冷却结束后加速度仍异常。修正：`apply`/`unapply` 成对。
5. **效果脚本不生效（`Problem loading class [...]`）**。触发：`statsScript` 类名、包声明、文件路径不一致，或文件放进了 `src\`（游戏不按 `src\` 前缀查找）。修正：三者一致并保持在 MOD 根目录下。

## 总结

战术系统 = 四段链路：`.system`（类型与脚本）→ `ship_systems.csv`（名称与数值）→ `ship_data.csv`（挂载）→ 效果脚本（响应状态回调）。

脚本侧只需要掌握"效果强度"一个概念：`effectLevel` 从 0 升至 1 再落回 0，所有数值乘以它；`apply`/`unapply` 成对撤销是唯一的纪律。

这四段链路是后续所有系统的共同骨架——武器类、护盾类、相位类系统都只是更换数据与脚本内容。系统 AI（`aiType`）与自定义使用时机在本教程 C07 展开。

## 建议参考项

- `data/shipsystems/<id>.system`、`data/shipsystems/ship_systems.csv`、`data/hulls/ship_data.csv`：三段式注册链路。
- `data/shipsystems/burndrive.system`、`damper.system`（原版）：本节两个示例的复制来源。
- `com.fs.starfarer.api.impl.combat.BaseShipSystemScript`：效果脚本模板基类。
- `com.fs.starfarer.api.plugins.ShipSystemStatsScript`：效果脚本接口（`State` 枚举、`apply`/`unapply`/`getStatusData`）。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：数值入口（`getMaxSpeed()`、`getHighExplosiveDamageTakenMult()` 等）。
- `data/scripts/gearin/shipsystems`：本节两个效果脚本类的存放目录。
