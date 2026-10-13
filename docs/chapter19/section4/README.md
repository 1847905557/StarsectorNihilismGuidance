# 让 Nexerelin 的战略 AI 使用 GearIn 扩张

齿轮工业已经拥有市场和外交配置，接下来让它参加战略会议，并比较入侵、封锁与复活的规模。先开启战略 AI 和关注项倍率，再用查询角色读回实际实例和参数。会议会推进真实行动，规模方法则解释当前采用的系数，各自在对应生涯条件下观察。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 用正确的 concern ID 调整自定义势力的战略优先级；
- 在 StrategicAI 尚未创建时安全地等待，在创建后主动刷新一次会议；
- 区分入侵规模倍率和复活全局条件，解释最终判断。

## 前置知识

- [C19-S2] 的势力配置与身份；
- [C19-S3] 的两种星区和已生成市场；
- [C14-S1] 的生涯时间推进；
- [C11-S1] 的舰队点数和生成规模。

## 开启战略 AI 与关注项

在 `gear_industry.json` 的战略字段中开启入侵，并按 concern ID 调整两个实际会被战略系统读取的关注项：

```json
"useStrategicAI": true,
"canInvade": true,
"invasionFleetSizeMod": 0.25,
"strategyPriorityMults": {
  "vulnerableEnemyTarget": 1.15,
  "commodityCompetition": 0.9
}
```

`military` 和 `economy` 是 concern 标签，不是可供 `strategyPriorityMults` 查找的键；写入它们不会改变上述两个 concern 的优先级。

`useStrategicAI` 允许库为存活势力建立战略 AI，`canInvade` 参与行动许可。脆弱敌方目标的基础优先级乘 1.15，商品竞争乘 0.9；当前战略条件仍决定能否形成行动。势力规模字段 0.25 对应共同系数中的 1.25。

## 在生涯中调用查询角色

将完整类保存到 `src/data/scripts/gearin/modcompat/`。在已启用 Nexerelin 的测试生涯中，通过 Console Commands 运行：

```text
runcode data.scripts.gearin.modcompat.GearInStrategicAIProbe p = new data.scripts.gearin.modcompat.GearInStrategicAIProbe(); com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn strategy: map=" + p.aiMapSize() + " concerns=" + p.concernCount("gear_industry") + " respawnChecks=" + p.respawnAllowedByRules("gear_industry"));
```

需要观察会议时，再执行：

```text
runcode new data.scripts.gearin.modcompat.GearInStrategicAIProbe().forceMeetingIfReady("gear_industry");
```

**查询读取状态，会议推进真实战略行为。**采用测试存档，执行后查看战略 Intel 中的 concern 与行动。实例不存在时会议返回 `false`；给当前已生成的势力补建时调用 `ensureAI(...)`，再比较 map。

固定基础规模 100，可以直接比较事件后处理：

```text
runcode data.scripts.gearin.modcompat.GearInInvasionScaleProbe p = new data.scripts.gearin.modcompat.GearInInvasionScaleProbe(); for (exerelin.campaign.fleets.InvasionFleetManager.EventType t : new exerelin.campaign.fleets.InvasionFleetManager.EventType[]{exerelin.campaign.fleets.InvasionFleetManager.EventType.INVASION, exerelin.campaign.fleets.InvasionFleetManager.EventType.BLOCKADE, exerelin.campaign.fleets.InvasionFleetManager.EventType.RESPAWN}) { com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn scale: " + t + " fp=" + p.applyEventPostProcessing("gear_industry", 100f, t)); }
```

## 谁建立 AI，谁决定行动

`StrategicAI.getAI` 从 sector persistent-data map 读取已经创建的 AI；`StrategicAI.addAIIfNeeded` 在缺少实例时负责补建；`strategyPriorityMults` 使用 `strategicAIConfig.json` 中的 concern ID；`InvasionFleetManager.getInvasionSizeMult` 返回已组合的势力与全局入侵倍率；`SectorManager` 和 `NexConfig` 共同决定复活是否有机会执行。

## 查询已有 AI

`GearInStrategicAIProbe` 的 `concernCount()`、`forceMeetingIfReady()` 与 `ensureAI()` 先从 sector persistent-data map 读取，再用 `null` 守卫确认 AI 生命周期；需要补建时调用 `addAIIfNeeded()`：

```java
StrategicAI ai = StrategicAI.getAI(factionId);
return ai == null ? 0 : ai.getExistingConcerns().size();
```

`getAIsMap()` 按需在 Sector PersistentData 建立 map，当前势力尚无实例时 `getAI()` 返回 `null`。`addAIIfNeeded()` 创建实例后登记 Intel、脚本与 map；正常接入由库按存活势力和 `useStrategicAI` 进行。已有实例后再调用会议。

```java
public boolean ensureAI(String factionId) {
    StrategicAI before = StrategicAI.getAI(factionId);
    StrategicAI created = StrategicAI.addAIIfNeeded(factionId);
    return before == null && (created != null || StrategicAI.getAI(factionId) != null);
}
```

早期探针返回 `0`，进入生涯后可主动刷新并读取 concern 数量。

## 从共同倍率读出规模

`GearInInvasionScaleProbe.estimate()` 让日志能解释最终规模来源，并避免重复乘全局值：

```java
float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
return baseSize * combined;
```

`getInvasionSizeMult()` 已经计算 `1 + invasionFleetSizeMod` 并乘上 `NexConfig.invasionFleetSizeMult`，调用方只再乘一次基础规模。

本例势力部分为 1.25，全局 `invasionFleetSizeMult` 为 1 时，基础 100 得到 125；全局改为 1.2 后得到 150。每个系数只乘一次。

## 比较入侵、封锁与复活

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

**本方法估算当前片段的规模后处理。**实际生成还会使用调用方 `sizeMult`、资金池和目标条件，最终舰队按本次事件确认。这里固定基础值，比较共同倍率、封锁最小规模、事件倍率和固定 FP。

## 分别观察复活开关与资格

`GearInStrategicAIProbe.respawnAllowedByRules()` 把复活开关、事件开关、势力资格和次数上限分开判断：

```java
SectorManager manager = SectorManager.getManager();
boolean respawnPossible = manager != null
        && manager.isRespawnFactions()
        && NexConfig.enableHostileFleetEvents
        && NexConfig.enableInvasions;
```

`factionRespawnInterval` 只决定检查间隔，`maxFactionRespawns` 为负数表示不设上限；`noHomeworld` 是程序化星区的母星配置，不是复活开关。

这些条件满足时探针返回 `true`。实际候选还受起始势力限制、存活状态、复活冷却、排除势力和可选目标影响，返回值表示本方法检查的条件通过。

## 完整最小例

两个完整角色分别读取 AI 状态和计算事件规模：

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

前置数据：Nexerelin 与 Console Commands 已启用，准备齿轮工业拥有市场的随机测试新档与测试存档。

1. 保存战略字段与两个类，构建重启，进入测试生涯并执行正文状态查询。
2. 比较 map 与 concern；补建实验采用当前有效势力确无实例的输入，调用 `ensureAI` 后再查询。
3. 在 AI 已建立的生涯执行正文会议命令，观察 concern 和实际行动。
4. 用基础 100 比较势力 0.25、全局 1 与 1.2 的估算；实验完成后恢复全局原值。
5. 对 invasion、blockade 和 respawn 使用同一基础规模，分别记录最小规模、事件倍率和固定 FP。
6. 打开复活设置，记录 `respawnAllowedByRules()` 在达到次数上限前后的返回值，并核对目标资格。

预期结果：

- 两个配置中的 concern ID 能影响战略优先级；
- AI 未建立时探针返回安全值，建立后可以刷新会议；
- AI 缺失时 `ensureAI()` 补建实例，map 条目数增加 1；
- `getInvasionSizeMult()` 的组合倍率只被应用一次；
- blockade 的最小规模、1.2 倍事件倍率和 120 FP 固定加成分别可见；
- respawn 的 1.2 倍事件倍率不会被误写成 invasion 的共同倍率；
- 复活查询反映其检查的管理器、事件开关、可玩性和次数；实际候选继续按冷却、存活和目标处理。

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

战略实例承担会议和行动，关注项 ID 对应优先级，规模方法对应本次采用的系数。复活的开关、次数和候选条件继续分别判断。

编者建议先读回实例与参数，再推进一次会议。配置倍率表达取舍，行动是否发生还取决于当前星区，两种结果各自放在实际观察位置。

## 建议参考项

- `exerelin.campaign.ai.StrategicAI`：`getAI` 取得势力战略 AI；
- `exerelin.campaign.fleets.InvasionFleetManager`：`getInvasionSizeMult` 读取入侵规模倍率，`EventType` 区分 invasion、blockade 和 respawn；
- `exerelin.utilities.NexConfig`：全局战略、入侵与复活设置。
