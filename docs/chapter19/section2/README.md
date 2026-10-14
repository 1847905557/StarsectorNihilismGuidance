# 登记 Nexerelin 势力与外交配置

Nexerelin 为势力另外读取开局资格、外交关系与联盟倾向。齿轮工业的核心定义已经存在，本节为同一个势力 ID 登记配置，分别设置初始关系、后续边界和外交特性，再通过生涯中的查询确认实际采用了哪份记录。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `mod_factions.csv` 登记自定义势力；
- 配置初始关系、关系边界、外交特性和联盟倾向；
- 从运行期配置身份区分自有记录和默认配置。

## 前置知识

- [C3-S1] 的 `gear_industry` 势力与注册 ID；
- [C10-S3] 的势力关系、显示与联盟相关观察；
- [C19-S1] 的可选依赖检测、编译 classpath 与调用边界；
- [C17-S3] 的单文件和跨来源合并。

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
