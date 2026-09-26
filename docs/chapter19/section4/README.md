# 让 Nexerelin 的战略 AI 使用 GearIn 扩张

势力能出现在星区，不代表战略 AI 会替它作出行动。本节确认 GearIn 拥有 StrategicAI，检查当前 concern，并把势力入侵倍率与全局规模共同算成可解释的舰队规模。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用正确的 concern ID 调整自定义势力的战略优先级；
- 在 StrategicAI 尚未创建时安全地等待，在创建后主动刷新一次会议；
- 区分入侵规模倍率和复活全局条件，解释最终判断。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 让战略 AI 获得可验证的行动入口

随机星区运行数月后，GearIn 战略会议能产生经济或军事 concern；允许入侵时，其进攻舰队规模体现势力配置倍率，势力被消灭后仍遵守复活设置。

## 文件与 API 链路

`StrategicAI.getAI` 从 sector persistent-data map 读取已经创建的 AI；`StrategicAI.addAIIfNeeded` 在缺少实例时负责补建；`strategyPriorityMults` 使用 `strategicAIConfig.json` 中的 concern ID；`InvasionFleetManager.getInvasionSizeMult` 返回已组合的势力与全局入侵倍率；`SectorManager` 和 `NexConfig` 共同决定复活是否有机会执行。

### 允许 GearIn 参加战略系统

在 `gear_industry.json` 的战略字段中开启入侵，并按 concern ID 调整两个实际会被战略系统读取的关注项：

```json
"canInvade": true,
"strategyPriorityMults": {
  "vulnerableEnemyTarget": 1.15,
  "commodityCompetition": 0.9
}
```

`military` 和 `economy` 是 concern 标签，不是可供 `strategyPriorityMults` 查找的键；写入它们不会改变上述两个 concern 的优先级。

战略会议中的 Vulnerable Enemy Target 权重会上升，Competition 权重会下降。

### 确认 StrategicAI 已创建

`GearInStrategicAIProbe` 的 `concernCount()`、`forceMeetingIfReady()` 与 `ensureAI()` 先从 sector persistent-data map 读取，再用 `null` 守卫确认 AI 生命周期；需要补建时调用 `addAIIfNeeded()`：

```java
StrategicAI ai = StrategicAI.getAI(factionId);
return ai == null ? 0 : ai.getExistingConcerns().size();
```

`getAI()` 实际读取 `StrategicAI.getAIsMap()`；新档创建早期 map 尚未写入该势力时会返回 `null`。调用 `forceMeeting()` 只应发生在返回非空之后。

```java
public boolean ensureAI(String factionId) {
    StrategicAI before = StrategicAI.getAI(factionId);
    StrategicAI created = StrategicAI.addAIIfNeeded(factionId);
    return before == null && (created != null || StrategicAI.getAI(factionId) != null);
}
```

早期探针返回 `0`，进入生涯后可主动刷新并读取 concern 数量。

### 组合势力与全局入侵倍率

`GearInInvasionScaleProbe.estimate()` 让日志能解释最终规模来源，并避免重复乘全局值：

```java
float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
return baseSize * combined;
```

`getInvasionSizeMult()` 已经计算 `1 + invasionFleetSizeMod` 并乘上 `NexConfig.invasionFleetSizeMult`，调用方只再乘一次基础规模。

修改势力或全局倍率中的任意一项时，估算值只按该项变化一次。

### 拆开三种事件的后处理

`GearInInvasionScaleProbe.applyEventPostProcessing()` 先取得基础规模，再应用共同倍率和事件自己的后处理；invasion 不附加事件倍率，respawn 使用 `RESPAWN_SIZE_MULT`，blockade 先保证最小规模，再乘 `BLOCKADE_SIZE_MULT` 并加入固定 FP：

```java
float fp = baseSize;
if (type == InvasionFleetManager.EventType.BLOCKADE) {
    fp = Math.max(fp, InvasionFleetManager.MIN_BLOCKADE_SIZE);
}
fp *= InvasionFleetManager.getInvasionSizeMult(factionId);
if (type == InvasionFleetManager.EventType.RESPAWN) {
    fp *= InvasionFleetManager.RESPAWN_SIZE_MULT;
} else if (type == InvasionFleetManager.EventType.BLOCKADE) {
    fp = fp * InvasionFleetManager.BLOCKADE_SIZE_MULT
            + InvasionFleetManager.BLOCKADE_FLAT_FP_BONUS;
}
return fp;
```

目标筛选仍由事件类型自己的入口负责：blockade 检查 `canBlockade` 或伤害型 concern，respawn 额外受复活资格和目标权重影响。不能把这些资格条件折叠进 `getInvasionSizeMult()`。

相同基础规模下，三种事件的最终 FP 能分别解释共同倍率、最小规模、固定加成和事件倍率。

### 把复活作为独立策略验证

`GearInStrategicAIProbe.respawnAllowedByRules()` 把复活开关、事件开关、势力资格和次数上限分开判断：

```java
SectorManager manager = SectorManager.getManager();
boolean respawnPossible = manager != null
        && manager.isRespawnFactions()
        && NexConfig.enableHostileFleetEvents
        && NexConfig.enableInvasions;
```

`factionRespawnInterval` 只决定检查间隔，`maxFactionRespawns` 为负数表示不设上限；`noHomeworld` 是程序化星区的母星配置，不是复活开关。

探针只有在管理器已建立、复活和入侵事件均开启、势力可玩且未达到次数上限时返回 `true`。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInInvasionScaleProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.fleets.InvasionFleetManager;

public final class GearInInvasionScaleProbe {
    public float estimate(String factionId, float baseSize) {
        float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
        return baseSize * combined;
    }

    public float applyEventPostProcessing(String factionId, float baseSize,
            InvasionFleetManager.EventType type) {
        float fp = baseSize;
        if (type == InvasionFleetManager.EventType.BLOCKADE) {
            fp = Math.max(fp, InvasionFleetManager.MIN_BLOCKADE_SIZE);
        }
        fp *= InvasionFleetManager.getInvasionSizeMult(factionId);
        if (type == InvasionFleetManager.EventType.RESPAWN) {
            fp *= InvasionFleetManager.RESPAWN_SIZE_MULT;
        } else if (type == InvasionFleetManager.EventType.BLOCKADE) {
            fp = fp * InvasionFleetManager.BLOCKADE_SIZE_MULT
                    + InvasionFleetManager.BLOCKADE_FLAT_FP_BONUS;
        }
        return fp;
    }
}
```

:::

::: details 完整成品：GearInStrategicAIProbe

```java
package data.scripts.gearin.modcompat;

import java.util.Map;

import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexConfig;

public final class GearInStrategicAIProbe {
    public int aiMapSize() {
        Map<String, StrategicAI> ais = StrategicAI.getAIsMap();
        return ais.size();
    }

    public int concernCount(String factionId) {
        StrategicAI ai = StrategicAI.getAI(factionId);
        return ai == null ? 0 : ai.getExistingConcerns().size();
    }

    public boolean ensureAI(String factionId) {
        StrategicAI before = StrategicAI.getAI(factionId);
        StrategicAI created = StrategicAI.addAIIfNeeded(factionId);
        return before == null && (created != null || StrategicAI.getAI(factionId) != null);
    }

    public boolean forceMeetingIfReady(String factionId) {
        StrategicAI ai = StrategicAI.getAI(factionId);
        if (ai == null) {
            return false;
        }
        ai.forceMeeting();
        return true;
    }

    public boolean respawnAllowedByRules(String factionId) {
        SectorManager manager = SectorManager.getManager();
        if (manager == null || !manager.isRespawnFactions()) {
            return false;
        }
        int maxRespawns = NexConfig.maxFactionRespawns;
        boolean hasAttempt = maxRespawns < 0
                || manager.getNumRespawns(factionId) < maxRespawns;
        return NexConfig.enableHostileFleetEvents
                && NexConfig.enableInvasions
                && NexConfig.getFactionConfig(factionId).playableFaction
                && hasAttempt;
    }
}
```

:::

## 验证方案

前置数据：Nexerelin 已启用，启用中的 GearIn MOD 已包含 [C19-S2] 的势力配置、[C19-S3] 的开局生成结果、本节 `gear_industry.json` 战略字段和两个探针类。

1. 构建示例 MOD，确认两个探针的方法签名与当前 `ExerelinCore.jar` 匹配。
2. 在新档、读档和 AI 缺失时机分别记录 persistent-data map 的条目数、`getAI()` 返回值和 `ensureAI()` 结果。
3. 进入战略 AI 已建立的生涯，刷新一次会议并记录 concern 数量。
4. 分别修改 `invasionFleetSizeMod` 与全局 `invasionFleetSizeMult`，比较估算值变化。
5. 对 invasion、blockade 和 respawn 使用同一基础规模，分别记录最小规模、事件倍率和固定 FP。
6. 打开复活设置，记录 `respawnAllowedByRules()` 在达到次数上限前后的返回值，并核对目标资格。

预期结果：

- 两个配置中的 concern ID 能影响战略优先级；
- AI 未建立时探针返回安全值，建立后可以刷新会议；
- AI 缺失时 `ensureAI()` 补建实例，map 条目数增加 1；
- `getInvasionSizeMult()` 的组合倍率只被应用一次；
- blockade 的最小规模、1.2 倍事件倍率和 120 FP 固定加成分别可见；
- respawn 的 1.2 倍事件倍率不会被误写成 invasion 的共同倍率；
- 复活探针准确反映管理器、事件开关、势力资格和次数上限。

## 故障分析

- **新档创建早期出现 `NullPointerException`**：Nexerelin 尚未建立该势力的 `StrategicAI`。
    - **解决方案**：保留 null 守卫并延迟到进入生涯后调用 `forceMeeting()`。

- **战略 AI 存在但不扩张**：`canInvade`、全局 `enableInvasions` 或 concern 的 `canInvade` 标签未满足。
    - **解决方案**：逐项启用后推进战略周期。

- **入侵舰队规模异常大**：调用方在 `getInvasionSizeMult()` 结果上又乘了一次全局倍率。
    - **解决方案**：移除重复乘法。

- **封锁舰队规模偏小**：只应用了共同倍率，没有先执行 `MIN_BLOCKADE_SIZE` 和 blockade 的固定后处理。
    - **解决方案**：按事件类型逐项记录。

- **复活舰队被当作普通入侵**：没有应用 `RESPAWN_SIZE_MULT` 或复活目标筛选。
    - **解决方案**：使用 `EventType.RESPAWN` 分支。

- **被消灭势力没有复活**：复活开关、敌对舰队事件、入侵开关、次数上限、势力资格或检查间隔未满足。
    - **解决方案**：逐项核对并推进到下一检查周期。

## 总结

战略接入要分别确认 AI 生命周期、concern ID、行动许可和规模计算。入侵倍率是一次组合运算，复活则是管理器、事件开关、势力资格和次数上限的联合条件；仅看到势力配置文件被读取，不能代替这些运行期验证。

## 建议参考项

- `exerelin.campaign.ai.StrategicAI`：`getAI` 取得势力战略 AI；
- `exerelin.campaign.fleets.InvasionFleetManager`：`getInvasionSizeMult` 读取入侵规模倍率，`EventType` 区分 invasion、blockade 和 respawn；
- `exerelin.utilities.NexConfig`：全局战略、入侵与复活设置。
