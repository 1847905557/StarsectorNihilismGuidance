# 登记 Nexerelin 势力与外交配置

齿轮工业已经是游戏中的势力，现在把它交给 Nexerelin 的开局与外交系统。先登记势力 ID 和同名 JSON，写入初始关系、关系边界与联盟倾向，再进入生涯读出实际配置身份。参数和对象对应以后，开局选项、外交事件与联盟状态才有明确的观察位置。

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

`playableFaction` 允许玩家选择该势力，`startingFaction` 允许它参与开局生成。`corvusCompatible` 表示支持固定星区模式，本例同时为后面的两种星区准备资格。`useConfigRelationshipsInNonRandomSector` 为 `true`，让固定模式采用本势力的配置关系。`disableDiplomacy` 为 `false` 时，势力可以参与外交；全局外交开关和具体事件条件也继续生效。

保存重启以后，在 Nexerelin 新档设置中确认齿轮工业出现在可选势力列表。外交行为在生涯中观察，配置登记和事件发生各有自己的条件。

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

**起点、边界与改变关系的入口分别确认。**本节观察 Nexerelin 的外交变化；直接用核心 `FactionAPI` 设置关系或其他 MOD 的行为，按各自调用处理。

`factionsLiked` 和 `factionsDisliked` 仍可被读取并标为弃用字段。本例用实际关系值表达偏好，读起来也能知道 "喜欢" 究竟到了哪个档位。编者建议先确定游戏中期望的关系，再写起点与上下限。

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

外交特性影响事件选择，alignment 参与联盟兼容性。本例偏重技术官僚倾向，再给外交与等级秩序一定权重。三项数值是配置的基础倾向；运行期修饰另在联盟状态中参与计算。

`getBaseAlignments()` 返回基础值的副本，适合对照 JSON。已有联盟用 `AllianceManager.areFactionsAllied(...)` 查询；读出了倾向，还需要按实际关系和联盟准入条件观察结果。

## 保存查询角色并进入生涯观察

将两个完整类保存到 `src/data/scripts/gearin/modcompat/`。在 Nexerelin 已启用、管理器已经建立的生涯中，用 Console Commands 运行：

```text
runcode com.fs.starfarer.api.Global.getLogger(data.scripts.gearin.modcompat.GearInNexDiplomacyProbe.class).info(new data.scripts.gearin.modcompat.GearInNexDiplomacyProbe().describe("gear_industry"));
```

搜索 `factionId=gear_industry`，同时确认 `playable`、`diplomacy`、基础 alignment 和霸主联盟状态。**先确认配置身份，再判断参数。**NexConfig 未找到注册记录时可以返回 `neutral` 默认配置，正常返回本身还需要对应到实际对象。

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

一行势力 ID 将齿轮工业的 Nexerelin 配置接到加载器，JSON 决定开局资格、外交边界与基础联盟倾向。查询角色在生涯中读出配置身份和实际联盟，数据与观察由此对应。

后面的生成和战略字段继续写进同一文件。每次新增参数，先确认使用的是自己的配置，再观察该参数所负责的行为。

## 建议参考项

- `exerelin.utilities.NexConfig`：`getFactionConfig` 查询势力配置与默认记录；
- `exerelin.utilities.NexFactionConfig`：开局资格、关系、特性与基础 alignment；
- `exerelin.campaign.AllianceManager`：`areFactionsAllied` 查询当前联盟；
- `data/config/exerelinFactionConfig/mod_factions.csv`：MOD 势力登记入口。
