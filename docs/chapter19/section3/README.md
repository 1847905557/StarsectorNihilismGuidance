# 配置 Nexerelin 的两种星区开局

固定 Corvus 使用已有星系和出生实体，随机星区则使用本次生成的市场。我们给齿轮工业登记 Prime 出生点，再补两类起始舰队；进入两种生涯后，用规划器读出实际实体和装配列表。固定对象、程序化市场与舰队数据分别准备，开局结果也按这些来源比较。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 SectorManager 就绪后区分 Corvus 与随机星区；
- 登记固定出生实体，并从随机星区查询已有市场；
- 为同一势力配置起始舰队并读取装配列表；
- 区分固定类型、RANDOM 候选和未知类型的结果。

## 前置知识

- [C3-S4] 的 GearIn 星系、Prime 行星与 `GearIn_planet1`；
- [C4-S1] 的 `GearIn_artizan_combat` 装配；
- [C19-S2] 的势力登记、模式资格和兼容查询；
- [C7-S1] 的新档生成与生涯加载时机。

## 把固定出生点写到真实实体

在 MOD 的 `data/config/exerelin/corvus_spawnpoints.csv` 保存：

```csv
faction,system,entityID
gear_industry,GearIn,GearIn_planet1
```

`system` 是星系名称，`entityID` 是 [C3-S4] 创建的 Prime 行星 ID。**显示名和实体 ID 分别填写。**Prime 的显示名是 GearIn Prime，星系则是 GearIn；出生逻辑按这份星系与实体关系取得位置。

固定模式保留 `GearInSystemGenerator` 的生成调用，让该实体在玩家放置前存在。CSV 本身登记引用，实际对象仍由生成器创建。

## 让随机星区拥有生成资格

在 `gear_industry.json` 沿 [C19-S2] 的资格字段增加：

```json
"marketSpawnWeight": 1
```

随机星区生成设置中启用齿轮工业，权重参与本次程序化市场分配。生涯生成后，`NexUtilsFaction.getFactionMarkets("gear_industry")` 才取得当前拥有的市场。

本教程的自定义星系生成器也可能为随机模式追加自有市场。因此权重为 0 并不等于最终没有市场；观察时读取实际集合，再判断市场来自哪里。**模式决定来源，查询确认本次已经生成的对象。**

## 填写两类起始舰队

在同一势力 JSON 根对象追加以下局部字段：

```json
"startShipsSolo": [
    ["GearIn_artizan_combat"]
],
"startShipsCombatSmall": [
    ["GearIn_artizan_combat", "wolf_Starting"]
]
```

每个内层数组是一组装配 ID。`startShipsSolo` 对应 SOLO，`startShipsCombatSmall` 对应 COMBAT_SMALL；已有工匠装配负责示例身份，原版狼装配补一艘护卫舰。

Nexerelin 加载时确认装配能够取得。新档界面选择固定舰队类型，或者随机类型；开局舰船由库的实际生成流程建立。本节的规划器读取配置与地点，方便对照这些结果。

## 保存规划器，在已生成的生涯查询

将完整 `GearInNexStartPlanner` 保存到 `src/data/scripts/gearin/modcompat/GearInNexStartPlanner.java`。以下局部调用在 Sector、Nexerelin 管理器和经济已经建立后执行；可以追加到既有 `onGameLoad(...)` 的启用分支：

```java
if (GearInCompatBridge.isNexerelinEnabled()) {
    GearInNexStartPlanner planner = new GearInNexStartPlanner();
    Global.getLogger(GearInModPlugin.class).info(
            "GearIn start: corvus=" + GearInNexCompat.isNexCorvusMode()
                    + " entity=" + planner.chooseSpawn("gear_industry")
                    + " solo=" + planner.chooseFleet("gear_industry", "SOLO")
                    + " combatSmall=" + planner.chooseFleet("gear_industry", "COMBAT_SMALL"));
}
```

添加这些自有角色类的 import，保留原有加载代码。规划器只返回 ID 与列表，日志观察在生涯加载后执行；需要在自己的玩家放置逻辑中使用返回值时，则先等待相同前置对象就绪，再把实际实体交给放置代码。

### 固定与随机地点的查询关系

管理器尚未建立时，`chooseSpawn` 返回 `null`，调用方等待。Corvus 分支从 `ExerelinCorvusLocations` 取得出生行；随机分支遍历本势力市场，跳过无主实体的市场，再选择规模最大者。

规模相同时保留先遇到的市场，集合顺序会影响选择。编者建议把这一结果当作 "当前最大市场" 的查询：需要固定首都时，使用明确的市场或实体 ID。

### 固定舰队和 RANDOM

局部取值为：

```java
List<String> solo = planner.chooseFleet("gear_industry", "SOLO");
List<String> randomFleet = planner.chooseFleet("gear_industry", "RANDOM");
```

`getStartFleetForType(fleetType, false, 0)` 的索引 0 取该类型首组配置，`false` 关闭缺少类型时的备用舰队；缺少记录可返回 `null`。同一个固定类型在两种模式下使用相同配置列表。

RANDOM 从配置已提供且 `canPickAsRandom` 为 `true` 的类型中选择，本例是 SOLO 与 COMBAT_SMALL。CUSTOM 和 RANDOM 自身不进入随机候选。不同新档或请求可以得到不同列表。

### 未知类型怎样报告

API 内部通过 `StartFleetType.getType(...)` 解析字符串，忽略大小写；未知字符串抛出 `IllegalArgumentException`。直接让这次请求报告拼写问题即可，调用方据此修正输入。

需要观察异常时，通过 Console Commands 请求：

```text
runcode new data.scripts.gearin.modcompat.GearInNexStartPlanner().chooseFleet("gear_industry", "GearIn_unknown_type");
```

该命令只查询数据，错误发生在类型解析，玩家位置和舰队保持原状态。查询不到有效出生实体时也返回 `null`，自有放置入口需要据此等待或报告。

## 完整最小例

出生点 CSV 与两类舰队字段采用前面的输入，规划器保存两种地点查询和固定索引的舰队取值：

::: details 完整成品：GearInNexStartPlanner

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.world.ExerelinCorvusLocations;

/** 在生涯已生成后查询出生实体与起始装配列表。 */
public final class GearInNexStartPlanner {
    public String chooseSpawn(String factionId) {
        SectorManager manager = SectorManager.getManager();
        if (manager == null) {
            return null;
        }
        if (manager.isCorvusMode()) {
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
        return NexConfig.getFactionConfig(factionId)
                .getStartFleetForType(fleetType, false, 0);
    }
}
```

:::

## 验证方案

前置数据：Console Commands 已启用，准备固定 Corvus 与随机星区两种新档。

1. 保存出生 CSV、市场权重、起始舰队字段、规划器与日志调用，构建并重启，选择齿轮工业的 SOLO 开局。
2. 创建 Corvus 新档，观察玩家出生位置，再搜索 `GearIn start` 中的实体与两类装配列表。
3. 创建随机星区新档并启用齿轮工业，查看其已有市场，比较日志选择的最大规模市场主实体。
4. 选择 COMBAT_SMALL 创建新档，再多次使用 RANDOM，核对舰队列表来自已配置的两类。
5. 执行正文未知类型命令，观察类型异常；用未配置的合法类型查询，观察返回 `null`。
6. 在没有齿轮工业市场的专用随机测试生涯查询出生点，确认返回 `null`；制作这类输入时也保持自定义生成器不追加市场。

预期结果：

- 固定模式登记解析到 GearIn 星系的 `GearIn_planet1`，玩家位置对应固定出生入口；
- 随机规划返回当前有效最大市场的主实体，规模并列时采用集合中先遇到者；
- SOLO 与 COMBAT_SMALL 分别返回工匠单舰、工匠和狼的列表，两种模式的固定配置一致；
- RANDOM 从已有可随机类型选择；未知类型产生解析异常，未配置合法类型可返回 `null`；
- 管理器或有效市场未就绪时出生查询返回 `null`。

## 故障分析

- **Corvus 找不到出生实体**：星系名称、实体 ID 或生成时机与 CSV 不一致。
    - **解决方案**：使用 GearIn 与 `GearIn_planet1`，确认生成器在玩家放置前完成。

- **规划器将尚未建立的生涯当作随机模式**：管理器未就绪时就选择地点。
    - **解决方案**：等待 SectorManager 建立，再查询出生点。

- **随机模式取得 null**：当前本势力市场集合为空，或候选没有主实体。
    - **解决方案**：核对本次生成中齿轮工业的启用、权重和实际市场，再进行放置。

- **起始列表缺少配置舰船**：字段名、装配 ID 或合法类型没有对应配置。
    - **解决方案**：核对 `startShipsSolo`、`startShipsCombatSmall` 与已加载装配。

- **类型查询抛出 IllegalArgumentException**：请求字符串不是 StartFleetType 能识别的类型。
    - **解决方案**：使用 SOLO、COMBAT_SMALL 或 RANDOM 等实际类型，修正调用输入。

## 总结

固定出生点由 CSV 指向已生成实体，随机地点查询来自本次市场集合；两种模式共享同一份起始舰队配置。规划器在生成后返回实际 ID 与列表，开局界面的放置与舰队生成再按库的流程执行。

先让输入存在，再在正确时机查询。两种模式的区别落实到对象来源以后，日志、地图和开局舰船可以分别对照。

## 建议参考项

- `exerelin.campaign.SectorManager`：管理器与 `isCorvusMode`；
- `exerelin.world.ExerelinCorvusLocations`：固定出生行查询；
- `exerelin.utilities.NexUtilsFaction`：`getFactionMarkets` 取得当前市场；
- `exerelin.utilities.NexFactionConfig`：`getStartFleetForType`、StartFleetType 和起始舰队集合；
- `data/config/exerelin/corvus_spawnpoints.csv`：固定出生点登记。
