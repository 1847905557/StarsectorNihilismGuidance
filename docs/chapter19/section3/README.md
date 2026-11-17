# 配置 Nexerelin 战略 AI 与扩张规模

Nexerelin 里的势力扩张背后由一套 `StrategicAI` 定时挑选行动关注项，但入侵、封锁与复活三路舰队偏偏共用着同一个规模倍率入口。很多时候您只想稍微调高某一类行动的派兵规模，改完却发现另外两路一并跟着膨胀，或者权重调了一圈毫无波澜 —— 问题多半出在没把 concern 键名与规模公式的共用关系拆开看。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C11-S1] 的舰队点数与生成规模；
- [C14-S1] 的生涯时间推进与调度概念；
- [C19-S2] 的 `gear_industry` 势力配置与星区市场。

## 开启战略 AI 与填写正确的 concern ID

继续在 `data/config/exerelinFactionConfig/gear_industry.json` 根对象补充战略配置：

```json
"useStrategicAI": true,
"canInvade": true,
"invasionFleetSizeMod": 0.25,
"strategyPriorityMults": {
    "vulnerableEnemyTarget": 1.15,
    "commodityCompetition": 0.9
}
```

调 `strategyPriorityMults` 时有一个极易踩空的细节：键名必须是 `Nexerelin/data/config/exerelin/strategicAIConfig.json` 里 `concerns` 下的**具体关注项 ID**(如本例的 `vulnerableEnemyTarget` 与 `commodityCompetition`)，而 `military`、`economy` 只是挂在 concern 里的**分类标签 (tag)** —— 写成标签名不会被 `BaseStrategicConcern.reapplyPriorityModifiers()` 查找命中。

至于其余字段：`useStrategicAI` 会在全局战略 AI 开启且势力存活时让库建出对应实例；`canInvade` 参与入侵行动许可；`invasionFleetSizeMod` 填 `0.25`，稍后共同倍率中的势力系数就会变成 `1.25`。

## 查询、补建并推进战略 AI 会议

把后文完整的 `GearInStrategicAIProbe` 保存到 `src/data/scripts/gearin/modcompat/`。它从星区持久化数据 (`"nex_strategicAI"`) 里读出当前势力的 `StrategicAI` 实例：

- **查存量**：`StrategicAI.getAIsMap()` 会在持久化字典不存在时按需建空表；如果某势力还没创建实例，`getAI(factionId)` 返回 `null`，切记先判空再去拿 `getExistingConcerns()`。
- **按需补建**：`ensureAI(factionId)` 调用 `StrategicAI.addAIIfNeeded(factionId)`，缺实例时才注册战略 Intel 与脚本并返回 `true`(常规开局由 Nexerelin 按存活势力与 `useStrategicAI` 自动创建，这个方法主要服务测试场景)。
- **主动开会**：拿到非空实例后调 `forceMeeting()`，它会提前触发更新间隔、扫新关注项并做行动决策 —— 注意 `StrategicAI.advanceImpl(...)` 开头会检查 `Global.getSector().isInNewGameAdvance()`，因此新档后台推进期内调会议会被跳过，进入正常生涯后再调用才会在战略 Intel 里看到变化。

::: warning 会议会修改实际战略状态

`forceMeeting()` 不是只读查询，它会真的刷新关注项并可能立即发起行动，建议只在测试存档上触发。

:::

通过 Console Commands 分别查看当前表状态并主动推一次会议：

```text
runcode data.scripts.gearin.modcompat.GearInStrategicAIProbe p = new data.scripts.gearin.modcompat.GearInStrategicAIProbe(); com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn strategy: map=" + p.aiMapSize() + " concerns=" + p.concernCount("gear_industry") + " respawnChecks=" + p.respawnAllowedByRules("gear_industry"));
```

```text
runcode new data.scripts.gearin.modcompat.GearInStrategicAIProbe().forceMeetingIfReady("gear_industry");
```

## 拆解共同倍率与三类事件规模

保存 `GearInInvasionScaleProbe` 到同一目录。计算入侵规模时最常犯的错，是看到 `getInvasionSizeMult(factionId)` 后自己在外面又乘了一遍 `NexConfig.invasionFleetSizeMult`。实际进字节码看一眼就会发现，这个方法**内部已经把势力加成与全局倍率合在一起了**：

$$\text{combined} = (1 + \text{invasionFleetSizeMod}) \times \text{NexConfig.invasionFleetSizeMult}$$

在本例中势力侧是 `1 + 0.25 = 1.25`，当全局 `invasionFleetSizeMult = 1` 时基础 `100` FP 得到 `125`；把全局调成 `1.2` 时则变成 `150`。调用方只需对基础规模乘一次即可。

另外，虽然方法名叫 `getInvasionSizeMult`，但它其实**同时被入侵、封锁和复活三类事件共用**；改动势力加成 `invasionFleetSizeMod` 时最好把三者一并算一遍，避免单看入侵而忽略另外两路舰队跟着变大：

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

- `INVASION`：只乘共同倍率，不叠额外事件倍率；
- `RESPAWN`：在共同倍率后再乘 `RESPAWN_SIZE_MULT`(`1.2`)；
- `BLOCKADE`：先夹到不低于 `MIN_BLOCKADE_SIZE`(约 `200` FP)，乘完共同倍率后再乘 `BLOCKADE_SIZE_MULT`(`1.2`) 并加上 `BLOCKADE_FLAT_FP_BONUS`(`120`)。

真正生成舰队时，还会叠加调用方传入的 `sizeMult`、预算池与目标许可条件 (例如封锁要求 `canBlockade` 或 `wantCauseHarm` 标签)。用控制台对 `100` FP 跑一遍三条分支即可直观对照：

```text
runcode data.scripts.gearin.modcompat.GearInInvasionScaleProbe p = new data.scripts.gearin.modcompat.GearInInvasionScaleProbe(); for (exerelin.campaign.fleets.InvasionFleetManager.EventType t : new exerelin.campaign.fleets.InvasionFleetManager.EventType[]{exerelin.campaign.fleets.InvasionFleetManager.EventType.INVASION, exerelin.campaign.fleets.InvasionFleetManager.EventType.BLOCKADE, exerelin.campaign.fleets.InvasionFleetManager.EventType.RESPAWN}) { com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn scale: " + t + " fp=" + p.applyEventPostProcessing("gear_industry", 100f, t)); }
```

## 核对势力复活资格

`GearInStrategicAIProbe.respawnAllowedByRules()` 把复活所需的管理器与配置前置条件拆成清晰几项：

```java
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
```

这里有三个字段容易误读：`factionRespawnInterval` 只控制检查周期而不当总开关；`maxFactionRespawns` 为负数时表示不限次数；而势力 JSON 里的 `noHomeworld` 控制程序化星区是否禁止生成母星，跟复活完全无关。探针返回 `true` 代表前置规则通过，实际能否抽中复活还要再过存活状态、冷却、排除列表和可用目标检查。

## 完整最小例

两份探针各自负责事件规模演算与战略 AI / 复活规则检测：

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

### 进游戏核对

1. **查看战略 AI 与手动开会**：在启用了 Nexerelin、齿轮工业持有市场且 Nexerelin 设置开启 `enableStrategicAI` 与 `showStrategicAI` 的生涯里，先后跑完正文两条 `GearInStrategicAIProbe` 命令。确认 AI 存在时 `concernCount` 正常返回且 `forceMeetingIfReady` 能刷新关注项与战略 Intel；对一个确认尚无实例的有效势力试一次 `ensureAI(...)`，`aiMapSize()` 应加 `1`。
2. **比对三类事件规模**：用正文规模命令给 `INVASION`、`BLOCKADE`、`RESPAWN` 同喂 `100` FP，然后把全局 `invasionFleetSizeMult` 暂改为 `1.2` 再跑一遍 (测完记得改回原值)，核对共同倍率只乘一次、`BLOCKADE` 含最小规模/1.2 倍/120 FP 固定加成、`RESPAWN` 单独乘 `1.2`。
3. **核对复活规则门限**：打开复活设置，确认未达上限时 `respawnAllowedByRules("gear_industry")` 为 `true`、达到次数上限后切为 `false`。

## 常见问题

- **战略关注项已经很多，却迟迟不见入侵舰队**：势力许可、全局开关、开局保护期或选中 concern 的标签未通过。
    - **解决方案**：核对势力 `canInvade`、全局事件与入侵开关、保护期、入侵点数以及 concern 是否带有 `canInvade` 或 `wantCauseHarm` 标签。

- **修改 `strategyPriorityMults` 后优先级没有变化**：配置里把 `military`、`economy` 这类 tag 当成了 concern ID。
    - **解决方案**：对照 Nexerelin 的 `strategicAIConfig.json` 中 `concerns` 对象的键名重新填写。

- **开局早期调用战略 AI 抛 `NullPointerException`**：实例尚未创建。
    - **解决方案**：保留 `getAI(...) == null` 守卫，等进入正常生涯后再查或调用 `forceMeeting()`。

- **入侵或封锁舰队规模明显偏离预期**：在外部对 `getInvasionSizeMult()` 结果多乘了一次全局倍率，或算封锁时漏了最小规模与固定加成。
    - **解决方案**：只乘一次 `getInvasionSizeMult()`，并按事件类型分别处理 `MIN_BLOCKADE_SIZE`、`BLOCKADE_FLAT_FP_BONUS` 与 `RESPAWN_SIZE_MULT`。

## 建议参考项

- `exerelin.campaign.ai.StrategicAI`：`getAI`、`getAIsMap`、`addAIIfNeeded` 与 `forceMeeting`；
- `exerelin.campaign.fleets.InvasionFleetManager`：`getInvasionSizeMult` 与 `EventType`；
- `exerelin.utilities.NexConfig`：全局战略、入侵与复活配置；
- `data/config/exerelin/strategicAIConfig.json`：`concerns` 关注项键名与标签定义。
