# 配置生涯势力

本节聚焦 `FactionAPI` 的身份和关系状态。第一个示例读取关系、比较直接写入与玩家声望入口并恢复原值；第二个示例只处理舰船 known/priority 集合及其缓存恢复。

*本节预期阅读时间：~22 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 取得玩家势力和指定势力并读取显示属性；
- 同时判断关系数值、关系等级、敌对状态和双向读取；
- 区分 `FactionAPI` 直接关系写入与 `SectorAPI.adjustPlayerReputation`；
- 临时修改关系并恢复原值；
- 修改舰船 known/priority 集合后恢复状态并理解 role cache 清理。

## 前置知识

- [C10-S1] 的 `SectorAPI.getPlayerFaction()` 和势力入口；
- [C7-S2] 的全局对象取得方式；
- [C1-S1] 的日志搜索和稳定前缀定位；
- 能够保存运行前状态，并在实验结束后恢复它。

## 取得势力身份

玩家势力通过 `getPlayerFaction()` 取得，其他势力通过 `getFaction(factionId)` 取得。`FactionAPI` 同时提供稳定 ID、显示名称、颜色、memory 和关系读取入口。

```java
SectorAPI sector = Global.getSector();
FactionAPI player = sector.getPlayerFaction();
FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);

String playerId = player.getId();
String displayName = hegemony.getDisplayName();
Color baseColor = hegemony.getBaseUIColor();
```

显示名称和颜色用于 UI 或日志展示，关系判断必须继续使用势力 ID。不要用显示名作为 `getRelationship` 的参数，显示名可能被本地化或覆盖。

## 读取关系状态

关系读取至少保留四个字段：数值、等级、敌对状态和读取方向。`source.getRelationship(targetId)` 表示 source 读取自己对 target 的关系；反向读取需要交换调用者和参数。

```java
float playerToHegemony = player.getRelationship(hegemony.getId());
float hegemonyToPlayer = hegemony.getRelationship(player.getId());
RepLevel level = player.getRelationshipLevel(hegemony.getId());
boolean hostile = player.isHostileTo(hegemony.getId());
```

关系等级由关系数值重新计算，敌对状态也从等级判定。只记录一个 float 无法解释 UI 中的等级变化或敌对标记。

## 对照两种关系写入

`FactionAPI.setRelationship` 和 `adjustRelationship` 直接写入关系表；`SectorAPI.adjustPlayerReputation(action, factionId)` 先寻找 reputation action response plugin，再由插件决定关系变化和返回结果。两者的输入、触发链和观察结果不同。

```java
float before = player.getRelationship(hegemony.getId());
player.adjustRelationship(hegemony.getId(), 0.05f);
float directDuring = player.getRelationship(hegemony.getId());
player.setRelationship(hegemony.getId(), before);

ReputationAdjustmentResult result = sector.adjustPlayerReputation(
        null,
        hegemony.getId());
```

关系实验必须保存原值。`setRelationship(String, RepLevel)` 不是把枚举对象保存起来，而是把等级转换为对应的关系数值；恢复时直接使用实验前的 float 更准确。

::: tip 关系方向
关系表按调用者保存读取方向。读取玩家对势力的关系时调用玩家对象；读取势力对玩家的关系时调用目标势力对象。
:::

## 恢复关系基线

恢复操作放在同一个示例中完成：先记录关系值，再做直接调整，最后写回原值并重新读取等级和敌对状态。玩家声望 action 的结果只记录，不把它误当成直接关系写入结果。

## 完整最小例

::: details 完整成品：GearInFactionRelationProbe
```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInFactionRelationProbe implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFactionRelationProbe.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }

        SectorAPI sector = Global.getSector();
        FactionAPI player = sector.getPlayerFaction();
        FactionAPI target = sector.getFaction(Factions.HEGEMONY);
        String targetId = target.getId();

        float before = player.getRelationship(targetId);
        logRelation("before", player, target);

        player.adjustRelationship(targetId, 0.05f);
        float during = player.getRelationship(targetId);
        logRelation("direct", player, target);

        player.setRelationship(targetId, before);
        float after = player.getRelationship(targetId);
        logRelation("after", player, target);

        ReputationAdjustmentResult reputationResult = sector.adjustPlayerReputation(
                null,
                targetId);
        LOG.info(String.format(
                "GearIn faction action: faction=%s result=%s restored=%.3f",
                targetId,
                reputationResult,
                after));
        LOG.info(String.format(
                "GearIn faction values: before=%.3f during=%.3f after=%.3f",
                before,
                during,
                after));
        done = true;
    }

    private void logRelation(String stage, FactionAPI source, FactionAPI target) {
        String targetId = target.getId();
        float forward = source.getRelationship(targetId);
        float reverse = target.getRelationship(source.getId());
        RepLevel level = source.getRelationshipLevel(targetId);
        boolean hostile = source.isHostileTo(targetId);
        LOG.info(String.format(
                "GearIn faction relation: stage=%s source=%s target=%s forward=%.3f reverse=%.3f level=%s hostile=%s",
                stage,
                source.getId(),
                targetId,
                forward,
                reverse,
                level,
                hostile));
    }
}
```
:::

## 读取与恢复舰船 known 状态

`getKnownShips()` 返回势力已知舰船集合，priority 集合决定舰船挑选时的优先级。添加或移除 known ship 会清理 ship role cache；priority 修改也会清理对应缓存。武器、战机和船插集合遵循同一类读写规律，本页只展开舰船。

```java
Set<String> knownShips = faction.getKnownShips();
boolean wasKnown = faction.knowsShip(hullId);
boolean wasPriority = faction.isShipPriority(hullId);

faction.addKnownShip(hullId, false);
faction.addPriorityShip(hullId);
faction.removePriorityShip(hullId);
faction.removeKnownShip(hullId);
```

示例必须记录实验前是否已知、是否 priority 和 timestamp 是否存在。恢复时只撤销本示例新增的状态，不删除原本属于势力的蓝图。

::: details 完整成品：GearInFactionKnownShipProbe
```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInFactionKnownShipProbe implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFactionKnownShipProbe.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }

        SectorAPI sector = Global.getSector();
        FactionAPI faction = sector.getFaction(Factions.HEGEMONY);
        String hullId = "lasher";
        Map<String, Long> timestamps = faction.getShipTimestamps();
        boolean wasKnown = faction.knowsShip(hullId);
        boolean wasPriority = faction.isShipPriority(hullId);
        Long previousTimestamp = timestamps.get(hullId);

        faction.addKnownShip(hullId, false);
        faction.addPriorityShip(hullId);
        LOG.info(String.format(
                "GearIn faction known: hull=%s known=%s priority=%s cacheTimestamp=%s",
                hullId,
                faction.knowsShip(hullId),
                faction.isShipPriority(hullId),
                faction.getShipTimestamps().get(hullId)));

        if (wasPriority) {
            faction.addPriorityShip(hullId);
        } else {
            faction.removePriorityShip(hullId);
        }

        if (wasKnown) {
            if (previousTimestamp == null) {
                timestamps.remove(hullId);
            } else {
                timestamps.put(hullId, previousTimestamp);
            }
        } else {
            faction.removeKnownShip(hullId);
        }

        LOG.info(String.format(
                "GearIn faction known restored: hull=%s known=%s priority=%s timestamp=%s",
                hullId,
                faction.knowsShip(hullId),
                faction.isShipPriority(hullId),
                faction.getShipTimestamps().get(hullId)));
        done = true;
    }
}
```
:::

## 验证方案

前置数据：包含本节两个示例脚本类的 GearIn MOD 已构建并启用，并准备一个可进入的生涯存档。

1. 启用示例 MOD，进入生涯并记录目标势力原始关系。
2. 搜索 `GearIn faction relation`，核对 forward、reverse、level 和 hostile。
3. 检查 direct 调整的 before/during/after，确认 after 恢复原值。
4. 检查 `GearIn faction known` 与 `restored`，确认 known、priority 和 timestamp 回到实验前状态。
5. 在游戏中重复运行一次，确认示例不会继续污染关系或蓝图集合。

预期结果：

- 日志出现 `GearIn faction relation`，forward、reverse、level 和 hostile 与目标势力关系一致；
- direct 调整的 before/during/after 中，after 恢复原值；
- `GearIn faction known` 与 `restored` 显示 known、priority 和 timestamp 回到实验前状态；
- 重复运行示例不再改变关系或蓝图集合。

## 故障分析

- **关系值变化方向相反**：把来源势力和目标势力的位置写反。

    - **解决方案**：确认调用者是读取关系的一方，参数是目标势力 ID，并同时记录反向读取。

- **关系等级变化但数值恢复不准确**：用 `RepLevel` 枚举恢复而不是保存原始 float。

    - **解决方案**：保存实验前的具体关系值，恢复时调用 `setRelationship(String, float)`。

- **玩家声望调用没有产生预期变化**：action response plugin 没有返回关系调整，或把它当成直接关系写入。

    - **解决方案**：单独记录 `ReputationAdjustmentResult`，不要用它替代直接关系实验。

- **known ship 修改后舰队挑选结果仍旧**：priority 或 role cache 没有按同一对象恢复和重建。

    - **解决方案**：通过 `addKnownShip`、`removeKnownShip` 和 priority 方法修改，让实现负责清理缓存。

- **示例结束后蓝图仍被删除**：没有区分实验前 known 状态和本次新增状态。

    - **解决方案**：只撤销本次新增的 hull ID，原本已知的对象保持原状态。

## 总结

势力页先读取身份，再把关系数值、等级、敌对状态和双向方向放在同一观察结果中。直接关系写入与玩家声望 action 需要分别理解；known ship 示例则重点验证集合、priority、role cache 和完整恢复。

## 建议参考项

- `com.fs.starfarer.api.campaign.FactionAPI`：势力身份、关系和 known 集合入口，关系表与等级转换由其承载；
- `com.fs.starfarer.api.campaign.RepLevel`：关系等级和敌对边界；
- `com.fs.starfarer.api.campaign.SectorAPI`：`adjustPlayerReputation` 玩家声望 action 入口；
- `data/campaign/factions/`：势力配置和已知舰船数据。
