# 登记 Nexerelin 势力、外交与开局配置

第三章把 `gear_industry` 写进原版势力定义后，切到 Nexerelin 开新档的势力列表里依然不见踪影 —— 开局可选资格、Corvus 出生归属、随机星区权重以及和海盗、独立势力的关系上下限，全由 Nexerelin 自己的配置目录接管。

这些文件只要漏掉一处键名就会在后台静默回落成默认设置，光盯着配置表往往看不出来；配置补齐以后顺手接上日志核对，您进生涯前就能一眼确认它到底有没有按预期生效。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C3-S1] 的 `gear_industry` 势力注册；
- [C3-S3] 的 Nipher 星系与主星 `GearIn_planet1`(显示名 `Nipher Prime`)；
- [C4-S1] 与 [C11-S2] 的 `GearIn_artizan_combat` 装配；
- [C10-S4] 的势力关系读写与关系等级；
- [C19-S1] 的可选依赖检测与调用边界。

## 登记势力名录并开启开局资格

在 MOD 内创建 `data/config/exerelinFactionConfig/mod_factions.csv`，只保留一列核心势力 ID：

```csv
faction
gear_industry
```

Nexerelin 读取这张合并表时，会跳过核心势力池尚未加载成功的 ID；拿到有效 ID 后，再去读同名路径 `data/config/exerelinFactionConfig/gear_industry.json`。先把开局资格与外交总开关写入该 JSON 的根对象：

```json
"playableFaction": true,
"startingFaction": true,
"corvusCompatible": true,
"useConfigRelationshipsInNonRandomSector": true,
"disableDiplomacy": false
```

- `playableFaction` 决定新档势力选择界面能不能挑齿轮工业；
- `startingFaction` 允许它在开局作为独立活跃势力出场；
- `corvusCompatible` 标记它支持固定星区 (非随机星区)；
- `useConfigRelationshipsInNonRandomSector` 让固定星区开局也套用下方配置好的关系；
- `disableDiplomacy` 设为 `false` 才允许该势力参与 Nexerelin 外交事件 (具体事件仍受全局外交设置与触发条件影响)。

## 设定关系起点、边界与外交倾向

为了让齿轮工业开局就带着清晰的阵营立场，但又不至于把后续所有外交事件都焊死，可以同时配置开局起点与变化上下限：

```json
"startRelationships": {
    "independent": 0.25,
    "pirates": -0.6
},
"minRelationships": {
    "independent": 0.0
},
"maxRelationships": {
    "pirates": -0.25
},
"diplomacyTraits": [
    "helps_allies",
    "law_and_order"
],
"alignments": {
    "technocratic": 0.75,
    "diplomatic": 0.4,
    "hierarchical": 0.25
}
```

这里各字段分工非常明确：`startRelationships` 决定默认关系开局下的初始数值 (选 "随机关系" 新档时走随机规则，不用这份起点)；`minRelationships` 与 `maxRelationships` 在全局开启 `useRelationshipBounds` 且非默认起点限制生效时作为上下限约束，遇到多方规则取更严边界，运行期额外修饰也会继续叠加。早期的 `factionsLiked` / `factionsDisliked` 虽仍可兼容读取但已经标为弃用，新配置直接写具体浮点数会清晰很多。

再往下看性格与联盟评分：`helps_allies` 会对友邦和盟友的敌人施压，`law_and_order` 会对持有自由港的势力产生负面评价；`alignments` 给出基础阵营倾向 (支持 `corporate`、`technocratic`、`militarist`、`hierarchical`、`diplomatic` 五个键，`ideological` 已弃用跳过)。注意 `NexFactionConfig.getBaseAlignments()` 返回的是 JSON 基础映射的拷贝，真正参与联盟合议的运行期倾向还会叠加动态修饰。

::: warning 未注册 ID 会回退到 neutral 默认配置

`NexConfig.getFactionConfig(factionId)` 查找势力时不区分大小写，但**找不到对应记录时默认不会返回 `null`，而是静默回退成 `neutral` 配置对象**。调试查询时一定要一并打印 `config.factionId`，确认拿到的真是 `"gear_industry"` 本身。另：查询同盟关系的 `AllianceManager.areFactionsAllied(f1, f2)` 在调用 `AllianceManager.getManager()` 且首参直接调 `equals` 时不做空保护，务必在生涯管理器就绪且传入非空势力 ID 后再调用。

:::

## 登记 Corvus 出生点、随机权重与起始舰队

接好外交身份之后，补齐两种星区模式的出生与开局舰队输入：

1. **固定 Corvus 星区出生点**：创建 `data/config/exerelin/corvus_spawnpoints.csv`，填入星系与实体 ID：

```csv
faction,system,entityID
gear_industry,Nipher,GearIn_planet1
```

CSV 只登记坐标引用 —— `GearInSystemGenerator` 仍要在开局放置前生成 `Nipher` 与 `GearIn_planet1`，Nexerelin 进入普通 Corvus 分支后按这里登记的 `entityID` 查实体放玩家。

2. **随机星区权重与两套起始舰队**：在 `gear_industry.json` 根对象补充：

```json
"marketSpawnWeight": 1,
"startShipsSolo": [
    ["GearIn_artizan_combat"]
],
"startShipsCombatSmall": [
    ["GearIn_artizan_combat", "wolf_Starting"]
]
```

随机星区打开势力权重并采用配置权重时，`marketSpawnWeight` 参与程序化市场分配 (若开局选项关了权重或选用随机权重则各自按对应设置运行)。`startShipsSolo` 与 `startShipsCombatSmall` 分别对应 `SOLO` 与 `COMBAT_SMALL` 两档开局，内层每个字符串数组就是一组装配 ID；另外，勾选 `RANDOM` 开局时会从已配好的且 `canPickAsRandom` 为 `true` 的档位里抽取 (`CUSTOM` 与 `RANDOM` 自身不会进随机候选)。

## 用兼容类、探针与规划器核对配置

在 `src/data/scripts/gearin/modcompat/` 分别保存后文给出的 `GearInNexCompat`、`GearInNexDiplomacyProbe` 与 `GearInNexStartPlanner`：

- `GearInNexCompat`：提供 `isNexEnabled()`、`isNexCorvusMode()`(`SectorManager.getManager()` 尚为空时安全返回 `false`)、`getFactionMarketSpawnWeight(...)` 与 `getFactionSpawnPointEntityId(...)`。
- `GearInNexDiplomacyProbe`：一次性格式化 `factionId`、`playable`、`diplomacy`、基础 `alignments` 以及是否与 `"hegemony"` 同盟。
- `GearInNexStartPlanner`：在生涯建立后分别读取两种星区模式的出生实体 ID 与起始装配列表：
  - `chooseSpawn(factionId)`：管理器就绪后，Corvus 模式读 `ExerelinCorvusLocations.getFactionSpawnPoint(factionId)` 的 `entityId`(只拿登记值，调用方自建放置前需自行检空与查实体)；随机模式遍历 `NexUtilsFaction.getFactionMarkets(factionId)`，跳过无主实体的市场后取规模最大者 (并列保留先遇到的市场，作为本例演示策略 —— Nexerelin 正式开局自身还会受母星与玩家所选起点影响)。
  - `chooseFleet(factionId, fleetType)`：调用 `getStartFleetForType(fleetType, false, 0)` 取指定类型第 0 组装配，关闭未配置时退回备用舰队，缺项返回 `null`。内部通过 `StartFleetType.getType(...)` 忽略大小写解析名称；遇到不认识的字符串会抛出 `IllegalArgumentException`，可直接用于暴露拼写错误。

最后在既有 `GearInModPlugin.onGameLoad(...)` 启用 Nexerelin 的分支里追加一行开局对照日志 (保留原有初始化)：

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

## 完整最小例

完整 `data/config/exerelinFactionConfig/gear_industry.json` 合入了开局资格、外交关系、随机权重与两套起始舰队 (配套的 `mod_factions.csv` 与 `corvus_spawnpoints.csv` 见前文单列/三列表)：

```json
{
    "playableFaction": true,
    "startingFaction": true,
    "corvusCompatible": true,
    "marketSpawnWeight": 1,
    "startShipsSolo": [
        ["GearIn_artizan_combat"]
    ],
    "startShipsCombatSmall": [
        ["GearIn_artizan_combat", "wolf_Starting"]
    ],
    "useConfigRelationshipsInNonRandomSector": true,
    "disableDiplomacy": false,
    "startRelationships": {
        "independent": 0.25,
        "pirates": -0.6
    },
    "minRelationships": {
        "independent": 0.0
    },
    "maxRelationships": {
        "pirates": -0.25
    },
    "diplomacyTraits": [
        "helps_allies",
        "law_and_order"
    ],
    "alignments": {
        "technocratic": 0.75,
        "diplomatic": 0.4,
        "hierarchical": 0.25
    }
}
```

三个 Java 类分别封装兼容查询、外交状态描述与开局策略读取：

::: details 完整成品：GearInNexCompat

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.world.ExerelinCorvusLocations;

/** 查询 Nexerelin 启用状态、星区模式与势力配置。 */
public class GearInNexCompat {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String GEAR_FACTION_ID = "gear_industry";

    private GearInNexCompat() {
    }

    public static boolean isNexEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    public static boolean isNexCorvusMode() {
        if (!isNexEnabled()) {
            return false;
        }
        SectorManager manager = SectorManager.getManager();
        return manager != null && manager.isCorvusMode();
    }

    public static float getFactionMarketSpawnWeight(String factionId) {
        if (!isNexEnabled()) {
            return 1f;
        }
        return NexConfig.getFactionConfig(factionId).marketSpawnWeight;
    }

    public static String getFactionSpawnPointEntityId(String factionId) {
        if (!isNexEnabled()) {
            return null;
        }
        ExerelinCorvusLocations.SpawnPointEntry entry =
                ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
        return entry == null ? null : entry.entityId;
    }
}
```

:::

::: details 完整成品：GearInNexDiplomacyProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.AllianceManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

public final class GearInNexDiplomacyProbe {
    public String describe(String factionId) {
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
        return "factionId=" + config.factionId
                + ", playable=" + config.playableFaction
                + ", diplomacy=" + !config.disableDiplomacy
                + ", alignments=" + config.getBaseAlignments()
                + ", alliedWithHegemony="
                + AllianceManager.areFactionsAllied(factionId, "hegemony");
    }
}
```

:::

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

### 进游戏核对

1. **Corvus 固定星区新档 (默认关系 + SOLO)**：在启动器启用 Nexerelin 与 Console Commands，建档界面应当可选 "齿轮工业"；进入生涯后核对玩家出生在 Nipher Prime(`GearIn_planet1`)、初始舰为工匠，并在日志搜索 `GearIn start`，确认 `corvus=true entity=GearIn_planet1 solo=[GearIn_artizan_combat]`。
2. **核对外交快照与边界**：进生涯后执行控制台命令，核对日志里 `factionId=gear_industry, playable=true, diplomacy=true`、三项基础 `alignments` 分别为 `0.75/0.4/0.25`，以及后续外交事件里对独立与海盗的关系上下限：

```text
runcode com.fs.starfarer.api.Global.getLogger(data.scripts.gearin.modcompat.GearInNexDiplomacyProbe.class).info(new data.scripts.gearin.modcompat.GearInNexDiplomacyProbe().describe("gear_industry"));
```

3. **随机星区新档 (启用配置权重 + COMBAT_SMALL / RANDOM)**：建随机新档并开启齿轮工业，检查 `GearIn start` 中 `corvus=false` 时返回的 `entity` 是否对应齿轮工业规模最大的市场主实体，`combatSmall` 是否包含工匠与狼；若故意做一局不含齿轮工业市场的测试星区，`chooseSpawn` 应安全返回 `null`。
4. **测试未知与未配舰队类型**：执行下面的控制台命令应抛出 `IllegalArgumentException`，而查询如 `"SUPER"` 这类已定义但未填入 JSON 的类型则返回 `null`：

```text
runcode new data.scripts.gearin.modcompat.GearInNexStartPlanner().chooseFleet("gear_industry", "GearIn_unknown_type");
```

## 常见问题

- **开局选项里找不到齿轮工业**：势力尚未完成登记，或 `playableFaction` 与当前模式资格没有打开。
    - **解决方案**：检查 `mod_factions.csv` 单列是否准确写着 `gear_industry`、核心势力是否已注册成功，并核对 `gear_industry.json` 的 `playableFaction` 与 `corvusCompatible`。

- **探针日志打出 `factionId=neutral`**：说明自有配置未被成功读入，`NexConfig.getFactionConfig(...)` 悄悄回退成了中立默认项。
    - **解决方案**：顺着 `data/config/exerelinFactionConfig/gear_industry.json` 与 `mod_factions.csv` 重查加载日志。

- **Corvus 开局报错或出生点漂移**：`corvus_spawnpoints.csv` 的星系名或实体 ID 与星系生成器不一致。
    - **解决方案**：核对 `Nipher`、`GearIn_planet1` 拼写，并确认星系生成已在玩家放置前完成。

- **刚建档就查地点拿到 `false`/`null`**：`SectorManager` 尚未完成构建。
    - **解决方案**：所有模式与地点查询必须等管理器就绪后再调用。

## 建议参考项

- `exerelin.utilities.NexConfig`：`getFactionConfig` 查询势力配置与回退逻辑；
- `exerelin.utilities.NexFactionConfig`：资格、关系、倾向与 `getStartFleetForType`；
- `exerelin.campaign.AllianceManager`：`areFactionsAllied` 同盟判断；
- `exerelin.campaign.SectorManager`：星区管理器与 `isCorvusMode`；
- `exerelin.world.ExerelinCorvusLocations`：固定 Corvus 出生点查询；
- `exerelin.utilities.NexUtilsFaction`：`getFactionMarkets` 获取当前势力市场；
- `data/config/exerelinFactionConfig/mod_factions.csv`：MOD 势力登记入口；
- `data/config/exerelin/corvus_spawnpoints.csv`：Corvus 固定出生点登记表。
