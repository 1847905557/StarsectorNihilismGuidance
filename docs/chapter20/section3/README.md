# 让 Nexerelin 在两种星区模式生成 GearIn 开局

固定 Corvus 与随机星区使用不同生成入口；只测其中一种，另一种往往会把玩家送到空坐标或没有 GearIn 市场的星区。本节为两种模式分别选择出生点，并从同一势力配置读取初始舰队。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 `SectorManager` 可用后区分 Corvus 与随机星区；
- 为 Corvus 模式登记固定出生实体，为随机星区选择已生成市场；
- 从同一份 Nexerelin 势力配置读取起始舰队变体。
- 区分固定 `fleetType`、`RANDOM` 候选和无法解析的舰队类型。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 分开处理两种星区

Corvus 模式把玩家放在 `corvus_spawnpoints.csv` 登记的固定实体旁，前提是该实体已由 Corvus 星区生成器创建；随机星区先生成 GearIn 市场，再选择规模最大的已生成市场主实体作为出生点，前提是市场已经生成且包含主实体。两种模式都获得相同类型的起始舰队。

## 文件与 API 链路

`SectorManager.isCorvusMode` 选择分支；`ExerelinCorvusLocations` 读取固定出生实体；`NexUtilsFaction.getFactionMarkets` 返回随机星区已经生成的本势力市场；`NexFactionConfig.getStartFleetForType` 读取起始舰队变体 ID。

### 先判断当前星区模式

`GearInNexStartPlanner.chooseSpawn()` 只在 manager 已建立后读取 Corvus 状态：

```java
if (GearInNexCompat.isNexCorvusMode()) {
    return GearInNexCompat.getFactionSpawnPointEntityId(factionId);
}
```

两种模式随后进入不同日志分支。

### 为 Corvus 提供固定实体

在 `data/config/exerelin/corvus_spawnpoints.csv` 的 GearIn 行写入三列，依次为势力 ID、星系名称和已存在实体 ID：

```csv
faction,system,entityID
gear_industry,GearIn Prime,GearIn_prime
```

`system` 是便于定位的星系名称，真正交给开局逻辑的是 `entityID`。该实体必须已经由 Corvus 星区生成器创建。

固定模式会解析到 `GearIn_prime`，玩家出生在该实体附近。

### 为随机星区选择本势力市场

`chooseSpawn()` 的随机分支在随机星区经济已经建立后，读取 GearIn 当前拥有的市场：

```java
for (MarketAPI market : NexUtilsFaction.getFactionMarkets(factionId)) {
    if (largest == null || market.getSize() > largest.getSize()) {
        largest = market;
    }
}
```

完整实现还会排除没有主实体的市场。选择规模最大的市场能让开局位置保持稳定，也避免引用只存在于 Corvus 模式的实体 ID。

随机模式返回一个实际市场主实体 ID；GearIn 没有市场时明确返回 `null`。

### 从配置取得初始舰队

`GearInNexStartPlanner.chooseFleet()` 按 `fleetType` 读取变体列表：

```java
return NexConfig.getFactionConfig(factionId)
        .getStartFleetForType(fleetType, false, 0);
```

第二个参数为 `false`，因此配置缺少该舰队类型时不会偷偷补一艘后备舰；索引 `0` 固定选择该类型的首组配置，便于两种模式做一致性复验。

两个模式对同一个 `fleetType` 会返回相同的变体 ID 列表。

### 让配置选择随机开局舰队

`GearInNexStartPlanner.chooseFleet()` 的调用方传入 `RANDOM` 时，由 Nexerelin 从允许随机选择的舰队类型中挑选；固定类型仍直接读取对应配置：

```java
List<String> fleet = planner.chooseFleet(factionId, "RANDOM");
```

多次新档得到的舰队类型可能不同，但每次结果都来自允许的起始舰队集合。

### 先拒绝无法解析的舰队类型

在请求进入 `getStartFleetForType()` 之前，用 try/catch 接住 `StartFleetType.getType()` 对未知键抛出的 `IllegalArgumentException`，避免把拼写错误当成随机类型：

```java
boolean valid = true;
try {
    NexFactionConfig.StartFleetType.getType(fleetType);
} catch (IllegalArgumentException e) {
    valid = false;
}
```

固定类型和 `RANDOM` 照常进入配置读取，未知类型在玩家放置前返回明确错误。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInNexStartPlanner

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.world.ExerelinCorvusLocations;

public final class GearInNexStartPlanner {
    public String chooseSpawn(String factionId) {
        SectorManager manager = SectorManager.getManager();
        if (manager != null && manager.isCorvusMode()) {
            ExerelinCorvusLocations.SpawnPointEntry entry =
                    ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
            return entry == null ? null : entry.entityId;
        }

        MarketAPI largest = null;
        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(factionId)) {
            if (market.getPrimaryEntity() == null) {
                continue;
            }
            if (largest == null || market.getSize() > largest.getSize()) {
                largest = market;
            }
        }
        return largest == null ? null : largest.getPrimaryEntity().getId();
    }

    public List<String> chooseFleet(String factionId, String fleetType) {
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
        try {
            NexFactionConfig.StartFleetType.getType(fleetType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown start fleet type: " + fleetType);
        }
        return config.getStartFleetForType(fleetType, false, 0);
    }
}
```

:::

## 验证方案

前置数据：Nexerelin 已启用，启用中的 GearIn MOD 已包含 [C20-S2] 的势力接入结果、本节 `corvus_spawnpoints.csv` 登记行和 `GearInNexStartPlanner`。

1. 构建示例 MOD，确认 `MarketAPI`、`NexUtilsFaction` 与开局配置方法签名匹配。
2. 创建 Corvus 新档，记录出生实体 ID 和起始舰队变体列表。
3. 创建随机星区新档，再次记录实体 ID 和同类型舰队列表。
4. 以固定类型创建 1 次开局，再以 `RANDOM` 创建多次开局，记录舰队规模与类型来源。
5. 传入未知 `fleetType`，确认规划器在玩家放置前返回错误。
6. 临时把 GearIn 的随机星区生成权重设为 `0`，确认规划器返回 `null`，而不是一个伪造实体 ID。

预期结果：

- 两种模式进入不同日志分支；
- 固定模式返回 `corvus_spawnpoints.csv` 登记的实体；
- 随机模式返回已生成 GearIn 市场的主实体；
- 两个模式为同一舰队类型返回相同变体列表；
- `RANDOM` 只从允许随机的起始舰队类型中选择；
- 未知舰队类型不会静默退回 `RANDOM`；
- 没有有效市场时返回 `null`，调用方能够在放置玩家前中止开局并报告原因。

## 故障分析

- **随机星区查询不到 Corvus 实体**：模式判断发生在固定实体查询之后。
    - **解决方案**：先确认 `SectorManager` 已创建并判断模式，随机分支改从当前 GearIn 市场取得主实体。

- **Corvus 模式出生在星区中心**：CSV 路径、表头或 `entityID` 与已创建 token 不一致。
    - **解决方案**：改用真实实体 ID，创建固定模式新档。

- **随机模式没有 GearIn 市场**：生成权重、势力启用状态或市场生成条件不满足。
    - **解决方案**：检查 `getFactionMarkets(factionId)` 的数量，修正配置。

- **初始舰队为空**：请求的 `fleetType` 与 `startShips` 键不一致，且 fallback 已关闭。
    - **解决方案**：记录请求类型和返回列表，修正配置键。

- **随机开局出现不允许的舰队类型**：调用方把任意字符串直接交给随机分支。
    - **解决方案**：先用 `StartFleetType.getType()` 和候选标志过滤。

## 总结

两种模式共享势力与舰队配置，只在空间对象来源上分支。固定模式使用 CSV 登记的现有 token，随机模式使用本次生成后实际存在的市场主实体；模式判断必须早于固定实体查询，市场存在性必须早于玩家放置。

## 建议参考项

- `exerelin.campaign.SectorManager`：`isCorvusMode` 判断星区生成模式；
- `corvus_spawnpoints.csv`：固定模式出生点；
- `exerelin.utilities.NexFactionConfig`：`getStartFleetForType` 读取起始舰队，`StartFleetType` 判断固定类型与 `RANDOM` 候选。
