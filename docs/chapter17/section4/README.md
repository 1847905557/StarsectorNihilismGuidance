# 让 Nexerelin 的战略 AI 使用 GearIn 扩张

势力能出现在星区，不代表战略 AI 会替它作出行动。本节确认 GearIn 拥有 StrategicAI，检查当前 concern，并把势力入侵倍率与全局规模共同算成可解释的舰队规模。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

* 用正确的 concern ID 调整 GearIn 的战略优先级；
* 在 StrategicAI 尚未创建时安全地等待，在创建后主动刷新一次会议；
* 区分入侵规模倍率和复活全局条件，解释最终判断。

## 前置知识

* [C6-S2] 理解 Java 类、方法、字段与入口插件；
* [C7-S1] 理解每帧回调、暂停判断与运行时对象；
* 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 让战略 AI 获得可验证的行动入口

随机星区运行数月后，GearIn 战略会议能产生经济或军事 concern；允许入侵时，其进攻舰队规模体现势力配置倍率，势力被消灭后仍遵守复活设置。

## 文件与 API 链路

`StrategicAI.getAI` 从势力内存读取已经创建的 AI；`strategyPriorityMults` 使用 `strategicAIConfig.json` 中的 concern ID；`InvasionFleetManager.getInvasionSizeMult` 返回已组合的势力与全局入侵倍率；`SectorManager` 和 `NexConfig` 共同决定复活是否有机会执行。

### 允许 GearIn 参加战略系统

**插入位置：`gear_industry.json` 战略字段。** 开启入侵，并按 concern ID 调整两个实际会被战略系统读取的关注项。

```json
"canInvade": true,
"strategyPriorityMults": {
  "vulnerableEnemyTarget": 1.15,
  "commodityCompetition": 0.9
}
```

`military` 和 `economic` 是 concern 标签，不是可供 `strategyPriorityMults` 查找的键；写入它们不会改变上述两个 concern 的优先级。

完成信号：战略会议中的 Vulnerable Enemy Target 权重上升，Competition 权重下降。

### 确认 StrategicAI 已创建

**插入位置：`GearInStrategicAIProbe.concernCount()` 与 `forceMeetingIfReady()`。** 先用 `null` 守卫确认 AI 生命周期，再在安全时机强制刷新会议。

```java
StrategicAI ai = StrategicAI.getAI(factionId);
return ai == null ? 0 : ai.getExistingConcerns().size();
```

调用 `forceMeeting()` 只应发生在 `getAI()` 返回非空之后；新档创建早期返回 `0` 是 "尚未建立" 的可观察状态，不等于没有战略 concern。

完成信号：早期探针返回 `0`，进入生涯后可主动刷新并读取 concern 数量。

### 组合势力与全局入侵倍率

**插入位置：`GearInInvasionScaleProbe.estimate()`。** 让日志能解释最终规模来源，并避免重复乘全局值。

```java
float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
return baseSize * combined;
```

`getInvasionSizeMult()` 已经计算 `1 + invasionFleetSizeMod` 并乘上 `NexConfig.invasionFleetSizeMult`，调用方只再乘一次基础规模。

完成信号：修改势力或全局倍率中的任意一项，估算值只按该项变化一次。

### 把复活作为独立策略验证

**插入位置：`GearInStrategicAIProbe.respawnAllowedByRules()`。** 把复活开关、事件开关、势力资格和次数上限分开判断。

```java
SectorManager manager = SectorManager.getManager();
boolean respawnPossible = manager != null
        && manager.isRespawnFactions()
        && NexConfig.enableHostileFleetEvents
        && NexConfig.enableInvasions;
```

`factionRespawnInterval` 只决定检查间隔，`maxFactionRespawns` 为负数表示不设上限；`noHomeworld` 是程序化星区的母星配置，不是复活开关。

完成信号：探针只有在管理器已建立、复活和入侵事件均开启、势力可玩且未达到次数上限时返回 `true`。

## 排查关键故障

下面是**局部故障片段**：

```java
StrategicAI ai = StrategicAI.getAI("gear_industry");
ai.forceMeeting();
```

实际症状：新档创建早期偶发 NullPointerException。

原因：新档创建早期 `StrategicAI.getAI()` 可能返回 `null`，AI 由 Nexerelin 在势力进入活动列表后建立。

下面是**局部修正片段**：

```java
if (ai != null) {
    ai.forceMeeting();
}
```

复验：在新档创建阶段调用 `forceMeetingIfReady()` 应返回 `false`；进入生涯后再次调用，返回 `true` 且 concern 数量可以重新读取。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

## 验证方案

1. 构建示例 MOD，确认两个探针的方法签名与 Nexerelin 0.11.3x 匹配。
2. 进入战略 AI 已建立的生涯，刷新一次会议并记录 concern 数量。
3. 分别修改 `invasionFleetSizeMod` 与全局 `invasionFleetSizeMult`，比较估算值变化。
4. 打开复活设置，记录 `respawnAllowedByRules()` 在达到次数上限前后的返回值。

预期结果：

* 两个配置中的 concern ID 能影响战略优先级；
* AI 未建立时探针返回安全值，建立后可以刷新会议；
* `getInvasionSizeMult()` 的组合倍率只被应用一次；
* 复活探针准确反映管理器、事件开关、势力资格和次数上限。

## 故障分析

- **新档创建早期出现 `NullPointerException`**：Nexerelin 尚未建立该势力的 `StrategicAI`。保留 null 守卫并延迟到进入生涯后调用 `forceMeeting()`，确认探针能够取得 AI。
- **战略 AI 存在但不扩张**：`canInvade`、全局 `enableInvasions` 或 concern 的 `canInvade` 标签未满足。逐项启用后推进战略周期，确认生成入侵行动。
- **入侵舰队规模异常大**：调用方在 `getInvasionSizeMult()` 结果上又乘了一次全局倍率。移除重复乘法，再比较基础规模与最终规模。
- **被消灭势力没有复活**：复活开关、敌对舰队事件、入侵开关、次数上限、势力资格或检查间隔未满足。逐项核对并推进到下一检查周期，确认复活事件生成。

## 总结

战略接入要分别确认 AI 生命周期、concern ID、行动许可和规模计算。入侵倍率是一次组合运算，复活则是管理器、事件开关、势力资格和次数上限的联合条件；仅看到势力配置文件被读取，不能代替这些运行期验证。

## 建议参考项

* `StrategicAI.getAI`：取得势力战略 AI；
* `InvasionFleetManager.getInvasionSizeMult`：读取入侵规模倍率；
* `NexConfig`：全局战略、入侵与复活设置。
::: details 完整成品：GearInInvasionScaleProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.fleets.InvasionFleetManager;

public final class GearInInvasionScaleProbe {
    public float estimate(String factionId, float baseSize) {
        float combined = InvasionFleetManager.getInvasionSizeMult(factionId);
        return baseSize * combined;
    }
}
```

:::

::: details 完整成品：GearInStrategicAIProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexConfig;

public final class GearInStrategicAIProbe {
    public int concernCount(String factionId) {
        StrategicAI ai = StrategicAI.getAI(factionId);
        return ai == null ? 0 : ai.getExistingConcerns().size();
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
