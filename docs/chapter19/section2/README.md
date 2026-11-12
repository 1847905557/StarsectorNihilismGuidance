# 登记 Nexerelin 势力与外交配置

Nexerelin 为势力另外读取开局资格、外交关系与联盟倾向。齿轮工业的核心定义已经存在，本节为同一个势力 ID 登记配置，分别设置初始关系、后续边界和外交特性，再通过生涯中的查询确认实际采用了哪份记录。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 用 `mod_factions.csv` 登记自定义势力；
- 配置初始关系、关系边界、外交特性和联盟倾向；
- 从运行期配置身份区分自有记录和默认配置。

## 前置知识

- [C3-S1] 的 `gear_industry` 势力与注册 ID；
- [C10-S3] 的势力关系、显示与联盟相关观察；
- [C19-S1] 的可选依赖检测、编译 classpath 与调用边界；
- [C17-S1] 的单文件和跨来源合并。

## 登记 ID，再保存同名配置

创建 MOD 的 `data/config/exerelinFactionConfig/mod_factions.csv`：

```csv
faction
gear_industry
```

这一列保存核心势力 ID，加载器随后读取 `data/config/exerelinFactionConfig/gear_industry.json`。在该位置保存本节完整 JSON；已有文件时，将下面字段合入同一根对象，保留自己已经定义的内容。

**核心势力、CSV 行与 JSON 文件围绕同一个 ID。**这份配置将由开局、外交、联盟和战略系统共同使用。先给齿轮工业自己的参数，后续各节在同一文件继续扩展。

## 开启开局资格与外交

以下为根对象中的局部字段：

```json
"playableFaction": true,
"startingFaction": true,
"corvusCompatible": true,
"useConfigRelationshipsInNonRandomSector": true,
"disableDiplomacy": false
```

`playableFaction` 允许玩家选择该势力，`startingFaction` 让它成为开局生成的候选，`corvusCompatible` 表示支持固定 Corvus 星区。`useConfigRelationshipsInNonRandomSector` 为 `true` 时，固定模式采用本势力的配置关系。两种模式的对象生成由下一节继续接入，这里先建立资格。

`disableDiplomacy` 为 `false` 允许参与外交，实际事件还受全局开关与具体条件影响。保存并重启后，新档势力列表可以确认选择资格；外交结果则到生涯中观察。

## 给关系设定起点与边界

先给两种实际关系：独立势力为 0.25，海盗为 -0.6。再为后续外交设置边界：

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
}
```

`startRelationships` 给出默认初始化关系；验证采用默认关系设置的新档，随机关系选项则按其初始化规则处理。启用全局 `useRelationshipBounds` 时，`minRelationships` 与 `maxRelationships` 参与关系边界；默认关系模式下，独立势力的配置下限为 0，海盗的配置上限为 -0.25。双方配置取更严格的边界，运行期上限修饰还会参与最终值。

**初始关系决定开局状态，边界约束 Nexerelin 后续采用的变化。** 直接使用核心 `FactionAPI` 设置关系，或由其他 MOD 修改关系时，结果按各自调用处理。

`factionsLiked` 与 `factionsDisliked` 是仍可读取的弃用字段。本例改用实际关系值表达起点，独立势力从 0.25 开始、下限为中立，海盗从 -0.6 开始、上限为 -0.25，两组关系各自保留一定变化范围。

## 用特性和 alignment 表达外交倾向

在同一根对象加入：

```json
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

`helps_allies` 使朋友与盟友的敌人获得不利倾向，`law_and_order` 对拥有自由港的势力施加不利倾向，外交判断由此使用当前星区的实际关系与市场。alignment 则参与联盟兼容性，本例以技术官僚倾向为主，另给外交与等级秩序基础值。

编者更倾向于让这些特性参与具体决策，同时给关系保留变化余地。起点、上下限与特性若把一切都固定下来，外交事件留下的结果也就很有限；本例各组数值承担不同职责，势力立场由它们共同表现。

`getBaseAlignments()` 返回基础值的副本，适合对照 JSON。已有联盟用 `AllianceManager.areFactionsAllied(...)` 查询；读出了倾向，还需要按实际关系和联盟准入条件观察结果。

## 保存查询角色并进入生涯观察

将两个完整类保存到 `src/data/scripts/gearin/modcompat/`。在 Nexerelin 已启用、管理器已经建立的生涯中，用 Console Commands 运行：

```text
runcode com.fs.starfarer.api.Global.getLogger(data.scripts.gearin.modcompat.GearInNexDiplomacyProbe.class).info(new data.scripts.gearin.modcompat.GearInNexDiplomacyProbe().describe("gear_industry"));
```

搜索 `factionId=gear_industry`，核对 `playable`、`diplomacy`、基础 alignment 与当前是否和霸主同盟。`getBaseAlignments()` 返回 JSON 基础值的副本，运行期修饰参与的倾向由另一份状态计算；实际同盟关系则由 `areFactionsAllied(...)` 查询。

::: warning 查询可以返回 neutral 默认配置
`NexConfig.getFactionConfig(...)` 未命中注册记录时，会返回 `neutral` 配置。核对返回对象的 `factionId`，才能确认查询取得齿轮工业的参数。
:::

`getFactionConfig(factionId)` 的配置查找忽略大小写；核心势力 ID、资源路径和其他调用继续沿用实际拼写。本节各处使用 `gear_industry`，并通过返回对象的 `factionId` 确认命中记录。

### 生涯模式与数据来源

`GearInNexCompat` 提供启用检测、Corvus 状态、市场生成权重与出生实体查询。模式方法在生涯已有 Sector 后调用；Nexerelin 启用而 SectorManager 尚未建立时，返回 `false`，因此选择出生点仍要等待管理器就绪。

市场权重从该势力配置取得，未注册时可能采用默认配置；未启用 Nexerelin 时返回 1。出生点从 `corvus_spawnpoints.csv` 读取，缺少登记行时返回 `null`，后面的开局逻辑按其 homeworld 或默认位置规则继续处理。这两个来源在下一节接到实际输入。

## 完整最小例

CSV 使用上面的单列登记，完整 JSON 保存到 `data/config/exerelinFactionConfig/gear_industry.json`：

```json
{
    "playableFaction": true,
    "startingFaction": true,
    "corvusCompatible": true,
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

两个角色分别查询兼容状态与描述外交配置：

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

## 验证方案

前置数据：Nexerelin 与 Console Commands 已启用，准备采用默认关系初始化的测试新档。

1. 保存 CSV、完整 JSON 与两个类，构建 JAR 并重启；在 Nexerelin 新档选项中确认齿轮工业可选。
2. 创建测试生涯，执行正文观察命令，记录配置身份、开关、三项基础 alignment 与联盟结果。
3. 推进生涯并观察 Nexerelin 的外交事件，比较独立势力和海盗关系与各自边界。
4. 在情报界面的势力与联盟信息中核对齿轮工业和霸主是否同盟，再执行查询比较。

预期结果：

- 查询返回 `factionId=gear_industry`，`playable=true` 与 `diplomacy=true`；
- 新档势力列表包含齿轮工业，固定模式资格已开启；
- 默认初始化采用配置的初始关系，后续 Nexerelin 外交按相应边界处理；
- 基础倾向包括 0.75、0.4 和 0.25，当前联盟查询与界面一致。

## 故障分析

- **新档没有齿轮工业选项**：势力登记、配置文件、可玩开关或当前模式资格不符。
    - **解决方案**：核对核心势力、单列 CSV、同名 JSON 与本节开关，重启后重新查看列表。

- **探针显示 neutral 配置**：当前查询没有命中已注册的自有记录。
    - **解决方案**：核对 CSV 行、文件位置与加载日志，再通过返回的 `factionId` 确认。

- **后续外交事件没有改变关系**：势力或全局外交关闭，或者当前事件条件未成立。
    - **解决方案**：核对 `disableDiplomacy`、全局外交设置和当前外交情报，推进生涯观察事件。

- **开局关系与 startRelationships 不同**：新档采用了随机关系初始化。
    - **解决方案**：用默认关系设置的新档比较本节起点，再按随机模式观察其规则。

- **满足关系后仍未建立联盟**：基础倾向、运行期修饰或其他准入条件未满足。
    - **解决方案**：对照 `getBaseAlignments()` 和联盟界面中的实际条件，核对目标势力。

## 总结

势力 ID 将核心对象与 Nexerelin 配置联系起来，开局资格、关系起点、后续边界和外交倾向分别服务自己的使用方。查询确认自有配置身份，再把基础参数与当前联盟、外交变化对应；后续生成和战略字段继续扩展同一份文件。

## 建议参考项

- `exerelin.utilities.NexConfig`：`getFactionConfig` 查询势力配置与默认记录；
- `exerelin.utilities.NexFactionConfig`：开局资格、关系、特性与基础 alignment；
- `exerelin.campaign.AllianceManager`：`areFactionsAllied` 查询当前联盟；
- `data/config/exerelinFactionConfig/mod_factions.csv`：MOD 势力登记入口。

## 配置 Nexerelin 的两种星区开局

Corvus 的出生登记可以指向固定实体，随机星区的出生位置则来自本次生成的对象。本节为齿轮工业登记 Prime 和两组起始装配，再用规划器比较配置 ID、当前市场与舰队列表。规划器负责查询，Nexerelin 的开局流程负责实际放置和生成，两者的结果分别确认。

## 学习目标

完成后，您将能够：

- 在 SectorManager 就绪后区分 Corvus 与随机星区；
- 登记固定出生实体，并从随机星区查询已有市场；
- 为同一势力配置起始舰队并读取装配列表；
- 区分固定类型、RANDOM 候选和未知类型的结果。

## 前置知识

- [C3-S3] 的 GearIn 星系、Prime 行星与 `GearIn_planet1`；
- [C4-S1] 的 `GearIn_artizan_combat` 装配；
- [C19-S2] 的势力登记、模式资格和兼容查询；
- [C7-S1] 的新档生成与生涯加载时机。

## 把固定出生点写到真实实体

在 MOD 的 `data/config/exerelin/corvus_spawnpoints.csv` 保存：

```csv
faction,system,entityID
gear_industry,GearIn,GearIn_planet1
```

`system` 保存星系名 GearIn，`entityID` 保存 [C3-S3] 创建的 `GearIn_planet1`；Prime 的显示名则是 GearIn Prime。库读取这份登记以后，普通 Corvus 放置分支通过实体 ID 从星区查询对象。**出生入口使用实际实体 ID。**

固定模式保留 `GearInSystemGenerator` 的生成调用，让该实体在玩家放置前存在。CSV 本身登记引用，实际对象仍由生成器创建。

## 让随机星区拥有生成资格

在 `gear_industry.json` 沿 [C19-S2] 的资格字段增加：

```json
"marketSpawnWeight": 1
```

随机星区生成设置中启用齿轮工业，再启用势力权重并采用配置权重。`marketSpawnWeight` 此时参与程序化市场分配；关闭权重或采用随机权重时，分配使用对应设置。生成完成以后，`NexUtilsFaction.getFactionMarkets("gear_industry")` 才取得当前市场。

自定义星系生成器也可能为随机模式追加市场，程序化生成还有母星等分配步骤。因此，权重为 0 只改变相应分配，最终市场集合仍由实际生成过程确定。

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

## 按模式查询地点与装配

保存完整 `GearInNexStartPlanner` 到 `src/data/scripts/gearin/modcompat/GearInNexStartPlanner.java`。它在生涯建立后查询数据，返回地点 ID 和装配列表。

### 地点查询与玩家实际出生

管理器尚未建立时，`chooseSpawn` 返回 `null`。Corvus 分支返回登记行中的实体 ID，登记行缺失时返回 `null`；随机分支遍历本势力市场，跳过没有主实体的市场，再选择规模最大者。

规模相同时保留先遇到的市场，集合顺序影响选择。这是本例自行采用的最大市场策略。库的普通随机开局还会使用母星或随机市场，背景指定位置、玩家选择的起点与随机出生开关也会改变实际放置。

::: warning 规划器只返回地点 ID
Corvus 查询返回 CSV 里的 ID，尚未检查该实体是否存在；随机查询返回当前最大市场主实体。自有放置代码需要再取得实体，库的正常开局则继续使用自己的放置规则。
:::

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

这个命令在类型解析时报告输入问题，查询过程保留玩家当前的位置和舰队。自有放置入口根据地点查询结果取得实体，登记 ID 对应不到对象时再处理缺失状态。

## 在生涯加载后比较查询结果

Sector、Nexerelin 管理器与经济已建立后，在既有 `onGameLoad(...)` 的启用分支追加局部调用，添加自有角色类的 import，保留原初始化：

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

固定类型读取同一份配置，地点查询按模式读取不同来源。日志确认这份输入，地图与开局舰队再确认实际生成结果，便能区分查询策略与库的开局规则。

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

前置数据：Console Commands 已启用，准备 Corvus 与随机星区两种新档；固定验证采用普通背景、默认出生点并关闭随机出生，权重验证启用配置势力权重。

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

出生登记、市场权重与起始装配分别提供开局输入。规划器在生成后返回配置 ID 或当前最大市场，并读取固定类型的装配列表；Nexerelin 再按实际开局选择完成放置与舰队生成。查询和生成各自对应来源，两个模式的结果也就能分别解释。

## 建议参考项

- `exerelin.campaign.SectorManager`：管理器与 `isCorvusMode`；
- `exerelin.world.ExerelinCorvusLocations`：固定出生行查询；
- `exerelin.utilities.NexUtilsFaction`：`getFactionMarkets` 取得当前市场；
- `exerelin.utilities.NexFactionConfig`：`getStartFleetForType`、StartFleetType 和起始舰队集合；
- `data/config/exerelin/corvus_spawnpoints.csv`：固定出生点登记。
