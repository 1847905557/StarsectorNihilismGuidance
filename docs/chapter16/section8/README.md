# 用 MagicLib 投放可回收残骸

任务奖励若直接发放舰船，玩家看不到发现和打捞过程。本节把舰船变体 ID、损伤状态、发现奖励和轨道参数交给 `MagicCampaign`，在指定实体旁生成一艘可发现、可回收的残骸。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 判断 `MagicCampaign` 工具是否处于有效生涯上下文；
- 用 `MagicVariables` 和原版星区服务记录生成前的环境；
- 为残骸选择变体 ID、损伤状态、发现奖励和轨道；
- 读取 `createDerelict()` 返回的实体 ID，交给任务或生成器继续管理；
- 从回收 special、轨道中心和调用场景定位残骸故障。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

玩家完成扫描后，目标实体外侧 200 su 出现一艘 GearIn 护航舰残骸；它以 30 天为周期运行，首次发现可以获得探索奖励，打开打捞界面时提供恢复流程。

## 文件与 API 链路

`Global.getSector()` 确认当前处于生涯；`MagicVariables` 提供 MagicLib 计算的星区尺寸；`MagicCampaign.createDerelict()` 根据 variant ID 创建原版残骸实体、设置发现属性、配置圆形轨道，并按 `recoverable` 参数挂接回收 special。

### 拒绝错误的调用场景

**插入位置：`GearInCampaignTools.spawnDerelictExample()` 开头。** 生涯服务不存在时记录原因并停止，避免把战斗测试当成生涯生成入口。

```java
if (Global.getSector() == null) {
    LOG.warn("GearIn magic campaign: createDerelict called outside campaign, skipped");
    return;
}
```

完成信号：战役或主菜单场景调用时只出现一条警告，不创建实体。

### 记录当前星区环境

**插入位置：`GearInCampaignTools.logCampaignInfo()` 的有效生涯分支。** 在写侧操作前报告星区尺寸、星系数和市场数。

```java
LOG.info("GearIn magic campaign: sectorSize=" + MagicVariables.getSectorSize()
        + " systems=" + sector.getStarSystems().size()
        + " markets=" + sector.getEconomy().getMarketsCopy().size());
```

完成信号：读档后日志给出三个非负统计值，调用位置确实处于生涯。

### 选择残骸状态与发现规则

**插入位置：`spawnDerelictExample()` 的 `createDerelict()` 参数。** 使用 `AVERAGE` 状态，允许发现奖励，并让 `null` 采用默认发现经验。

```java
ShipRecoverySpecial.ShipCondition condition =
        ShipRecoverySpecial.ShipCondition.AVERAGE;
boolean discoverable = true;
Integer discoveryXp = null;
boolean recoverable = true;
```

完成信号：残骸在靠近前保持可发现状态，发现后进入正常交互，并具备回收 special。

### 把残骸放上轨道

**插入位置：`spawnDerelictExample()` 的生涯分支。** 传入 variant ID、状态、发现参数和围绕目标实体的轨道。

```java
SectorEntityToken wreck = MagicCampaign.createDerelict(
        variantId, ShipRecoverySpecial.ShipCondition.AVERAGE,
        true, null, true, orbitCenter,
        0f, 200f, 30f);
```

完成信号：残骸围绕 `orbitCenter` 运行，半径为 200 su，周期为 30 天。

### 保存生成结果的身份

**插入位置：`createDerelict()` 返回后。** 先记录返回实体 ID；任务入口需要清理时，再把同一 token 写入自己的任务状态。

```java
LOG.info("GearIn magic campaign: derelict " + wreck.getId()
        + " orbiting " + orbitCenter.getId());
```

完成信号：日志中的残骸 ID 能在星区实体与任务状态中对应到同一个对象。

## 排查关键故障

下面是**局部故障片段**：

```java
MagicCampaign.createDerelict(variantId, condition,
        true, null, false, orbitCenter, 0f, 200f, 30f);
```

实际症状：残骸正常出现，但打捞界面只提供故事点回收选项，没有常规回收流程。

原因：九参数重载的第五个布尔值是 `recoverable`。传入 `false` 时，MagicLib 仍创建并配置残骸，但不会通过 `Misc.setSalvageSpecial()` 挂接舰船回收 special。

下面是**局部修正片段**：

```java
MagicCampaign.createDerelict(variantId, condition,
        true, null, true, orbitCenter, 0f, 200f, 30f);
```

复验：使用新存档或新的实体 ID 重新生成残骸，打开交互并确认舰船回收流程出现。已经创建的旧实体不会因修改参数自动补上 special。

## 完整最小例

完整工具类同时保留只读环境报告与写侧残骸生成。调用方提供已经加入星区的轨道中心和有效 variant ID，并负责在任务需要时保存返回实体的身份。

## 验证方案

前置数据：目标实体已经加入一个星系，variant ID 能由游戏设置服务解析，生成调用发生在新档生成器或读档后的任务脚本中。

1. 调用 `logCampaignInfo()`，核对星区尺寸、星系数和市场数。
2. 以目标实体和有效 variant ID 调用 `spawnDerelictExample()`，记录返回日志中的两个实体 ID。
3. 在星图中确认残骸围绕目标运行，并靠近触发发现提示。
4. 打开残骸交互，确认可以进入舰船回收流程，预览状态与 `AVERAGE` 参数一致。
5. 把 `recoverable` 临时改为 `false`，用另一实体 ID 生成第二艘残骸并比较交互结果。
6. 在战役场景调用工具方法，确认场景守卫只记录警告。

预期结果：

- 有效生涯中生成一艘处于 200 su、30 天圆形轨道的残骸；
- 残骸可被发现，发现经验采用 MagicLib 默认值；
- `recoverable=true` 的实体具备舰船回收 special，`false` 的对照实体没有该流程；
- 日志同时记录残骸 ID 与轨道中心 ID，便于任务后续定位；
- 非生涯上下文只记录警告，不尝试访问星区对象。

## 故障分析

- **残骸出现但只有故事点回收流程**：九参数重载的 `recoverable` 为 false，没有挂载回收 special，但故事点回收选项仍会出现。改为 true 可获得常规回收流程。
- **残骸没有出现**：variant ID 无法解析，或对应 variant 没有随 MOD 发布。核对注册 ID 和加载日志，再运行创建调用确认实体返回非 null。
- **残骸出现在错误星系**：`orbitCenter` 来自另一个星系，或实体尚未加入星区。使用任务目标实体作为轨道中心，进入星图核对位置。
- **任务失败后残骸仍然存在**：清理阶段没有保存并移除创建的 token。把实体加入任务清理列表，失败任务结束并读档确认 token 消失。

## 总结

`MagicCampaign.createDerelict()` 把原版残骸创建、发现属性、轨道与回收 special 接在一次调用中。调用方仍要保证生涯场景、轨道中心和 variant ID 有效，并保存返回实体的身份供任务阶段管理。

## 建议参考项

- `MagicCampaign.createDerelict`：创建残骸、设置发现属性、轨道与回收 special；
- `ShipRecoverySpecial.ShipCondition`：选择残骸的舰船状态；
- `MagicVariables.getSectorSize`：读取 MagicLib 计算的星区尺寸。
::: details 完整成品：GearInCampaignTools

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicCampaign;
import org.magiclib.util.MagicVariables;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;

/** 生涯工具 (MagicCampaign)：查询面与写侧的参考消费姿势。
 * 生涯工具以 Global.getSector() 为宿主，只能在生涯上下文调用，调用前先判空；
 * logCampaignInfo 是查询面演示 (战役场景输出 "not in campaign")，
 * spawnDerelictExample 是写侧参考姿势 (调用方是生涯脚本，战斗插件不调用)。 */
public class GearInCampaignTools {

    private static final Logger LOG = Global.getLogger(GearInCampaignTools.class);

    private GearInCampaignTools() {
        // 工具类：不实例化
    }

    /** 生涯上下文查询演示：只在 Global.getSector() 非 null(生涯启动/读档后) 时输出统计。 */
    public static void logCampaignInfo() {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            LOG.info("GearIn magic campaign: no sector (not in campaign)");
            return;
        }
        LOG.info("GearIn magic campaign: sectorSize=" + MagicVariables.getSectorSize()
                + " systems=" + sector.getStarSystems().size()
                + " markets=" + sector.getEconomy().getMarketsCopy().size());
    }

    /** 生涯写侧参考姿势：在生涯脚本 (如 C3-S3 的 GearInSystemGenerator 末尾) 调用，
     * 把一艘残骸舰放到轨道上。只有生涯上下文有效 (orbitCenter 必须已加入星区)；
     * 场景错误时写 warn 而不是抛错。 */
    public static void spawnDerelictExample(SectorEntityToken orbitCenter, String variantId) {
        if (Global.getSector() == null) {
            LOG.warn("GearIn magic campaign: createDerelict called outside campaign, skipped");
            return;
        }
        SectorEntityToken wreck = MagicCampaign.createDerelict(
                variantId,
                ShipRecoverySpecial.ShipCondition.AVERAGE,
                true,  // discoverable：发现时奖励探索 XP
                null,  // discoveryXp：null 使用默认
                true,  // recoverable：写入打捞 special(可回收舰船)
                orbitCenter,
                0f,    // orbitStartAngle
                200f,  // orbitRadius
                30f);  // orbitDays
        LOG.info("GearIn magic campaign: derelict " + wreck.getId()
                + " orbiting " + orbitCenter.getId());
    }
}
```

:::
