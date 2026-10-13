# 配置生涯势力

势力对象既提供名称与配色，也保存关系和军备知识。本节先比较直接关系调整与玩家声望事件，再临时把一艘船加入已知、优先集合，最后恢复实验前状态。所有变化都在日志里保留前后值，便于看清写入入口与实际结果。

*本节预期阅读时间：\~22 分钟。*

*本节预期操作时间：\~30 分钟。*

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

## 登记两项实验并取得势力

创建 `src/data/scripts/gearin/campaign/GearInFactionRelationProbe.java` 和 `GearInFactionKnownShipProbe.java`。在 [C7-S1] 的 `GearInModPlugin` 添加这两个类的 import，把局部登记加入 `onGameLoad(...)`，沿用其中已有的 `sector`：

```java
sector.addTransientScript(new GearInFactionRelationProbe());
sector.addTransientScript(new GearInFactionKnownShipProbe());
```

每次进入存档执行一轮实验，完成后 `isDone()` 返回 `true`，生涯引擎移除本次实例。保存前状态、修改和恢复都在同一轮完成。

玩家势力通过 `getPlayerFaction()` 取得，其他势力通过 `getFaction(factionId)` 取得。`FactionAPI` 同时提供稳定 ID、显示名称、颜色、memory 和关系读取入口。

```java
SectorAPI sector = Global.getSector();
FactionAPI player = sector.getPlayerFaction();
FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);

String playerId = player.getId();
String displayName = hegemony.getDisplayName();
Color baseColor = hegemony.getBaseUIColor();
```

**名称与颜色用于显示，关系方法使用稳定 ID。** 中文显示名可能被本地化或修改，`getRelationship(...)` 的参数仍填写 `Factions.HEGEMONY` 等 ID。

## 读取关系状态

当前同时记录数值、等级、敌对状态和读取方向，方便与界面比较。`source.getRelationship(targetId)` 表示 source 读取自己对 target 的关系；反向读取需要交换调用者和参数。

```java
float playerToHegemony = player.getRelationship(hegemony.getId());
float hegemonyToPlayer = hegemony.getRelationship(player.getId());
RepLevel level = player.getRelationshipLevel(hegemony.getId());
boolean hostile = player.isHostileTo(hegemony.getId());
```

关系等级由数值计算，敌对状态按等级判定。**普通势力对共享同一项双边关系**，交换两端读取会得到对应的同一数值。双向日志用于确认调用对象，等级与敌对字段则解释界面表现。

## 对照两种关系写入

`FactionAPI.setRelationship` 和 `adjustRelationship` 直接写入关系表；`SectorAPI.adjustPlayerReputation(action, factionId)` 先寻找 reputation action response plugin，再由插件决定关系变化和返回结果。两者的输入、触发链和观察结果不同。

```java
float before = player.getRelationship(hegemony.getId());
player.adjustRelationship(hegemony.getId(), 0.05f);
float directDuring = player.getRelationship(hegemony.getId());
player.setRelationship(hegemony.getId(), before);

CustomRepImpact impact = new CustomRepImpact();
impact.delta = 0.05f;
RepActionEnvelope envelope = new RepActionEnvelope(RepActions.CUSTOM, impact);
envelope.withMessage = false;
ReputationAdjustmentResult result = sector.adjustPlayerReputation(
        envelope,
        hegemony.getId());
```

`CustomRepImpact` 指定变化量，`RepActionEnvelope` 将它交给 `CUSTOM` 声望事件，`withMessage=false` 让本次观察集中在日志。完整类记录 `result.delta` 与关系实值，再恢复原始关系。

**恢复使用实验前的 float。** `setRelationship(String, RepLevel)` 会把等级转换为对应数值；一个等级内原有的具体关系值仍由保存的 float 保留。

::: tip 读取双方
交换调用者与目标参数，可以核对同一对势力。普通势力关系是双边数据，直接写入也会在双方读取中体现。
:::

## 恢复关系基线

本例先保存原值，做直接调整并恢复，再调用玩家声望事件并恢复。两次修改分别记录实际值，声望返回的 `delta` 记录插件处理结果。接近关系上限时，实际增量会受到范围限制；日志按结果比较。

编者建议从一开始就把恢复写进实验。关系影响生涯行为，测试结束后的状态也属于这次实验的成果。

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
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
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

        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = 0.05f;
        RepActionEnvelope envelope = new RepActionEnvelope(RepActions.CUSTOM, impact);
        envelope.withMessage = false;
        ReputationAdjustmentResult reputationResult = sector.adjustPlayerReputation(
                envelope,
                targetId);
        float actionDuring = player.getRelationship(targetId);
        logRelation("action", player, target);
        player.setRelationship(targetId, before);
        float actionAfter = player.getRelationship(targetId);
        logRelation("actionRestored", player, target);
        LOG.info(String.format(
                "GearIn faction action: faction=%s delta=%.3f during=%.3f restored=%.3f",
                targetId,
                reputationResult.delta,
                actionDuring,
                actionAfter));
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

`getKnownShips()` 取得已知舰船集合，priority 集合参与舰船挑选优先级。使用 `addKnownShip(...)`、`removeKnownShip(...)` 与 priority 方法修改时，游戏也会清理舰船角色挑选缓存；后续生成按新集合重新选择。武器、战机与船插各有对应知识集合，当前只展开舰船实验。

```java
Set<String> knownShips = faction.getKnownShips();
boolean wasKnown = faction.knowsShip(hullId);
boolean wasPriority = faction.isShipPriority(hullId);

faction.addKnownShip(hullId, false);
faction.addPriorityShip(hullId);
faction.removePriorityShip(hullId);
faction.removeKnownShip(hullId);
```

先保存是否已知、是否优先与原时间戳，再加入原版 `lasher` 并记录。`addKnownShip(hullId, false)` 中的 `false` 表示本次新增时不写学习时间戳。恢复按保存的三个状态处理，势力原本拥有的舰船继续保留。

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
                "GearIn faction known: hull=%s known=%s priority=%s timestamp=%s",
                hullId,
                faction.knowsShip(hullId),
                faction.isShipPriority(hullId),
                faction.getShipTimestamps().get(hullId)));

        if (!wasKnown) {
            faction.removeKnownShip(hullId);
        }
        if (wasPriority) {
            faction.addPriorityShip(hullId);
        } else {
            faction.removePriorityShip(hullId);
        }
        if (previousTimestamp == null) {
            timestamps.remove(hullId);
        } else {
            timestamps.put(hullId, previousTimestamp);
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

1. 保存两个类与生涯登记，构建并重启，进入存档，记录玩家与霸主原始关系。
2. 搜索 `GearIn faction relation`，核对 forward、reverse、level 和 hostile。
3. 比较直接调整的 before/during/after，再查看 `GearIn faction action` 的 delta、during 与 restored，核对两次修改各自恢复。
4. 检查 `GearIn faction known` 与 `restored`，确认 known、priority 和 timestamp 回到实验前状态。
5. 保存并重新进入存档，执行第二轮，比较两轮起始关系、known、priority 与时间戳。

预期结果：

- 日志出现 `GearIn faction relation`，forward、reverse、level 和 hostile 与目标势力关系一致；
- 直接调整后 `after` 恢复原值，声望事件后的 `restored` 同样恢复；`delta` 记录实际声望增量；
- `GearIn faction known` 与 `restored` 显示 known、priority 和 timestamp 回到实验前状态；
- 重复运行示例不再改变关系或蓝图集合。

## 故障分析

- **关系变化与预期符号相反**：直接调整或声望事件的变化量符号与设计相反。
    - **解决方案**：核对 `adjustRelationship(...)` 的增量和 `CustomRepImpact.delta`，正值提高关系，负值降低关系，再比较双方日志。

- **关系等级变化但数值恢复不准确**：用 `RepLevel` 枚举恢复而不是保存原始 float。
    - **解决方案**：保存实验前的具体关系值，恢复时调用 `setRelationship(String, float)`。

- **玩家声望调用没有产生预期变化**：action response plugin 没有返回关系调整，或把它当成直接关系写入。
    - **解决方案**：单独记录 `ReputationAdjustmentResult`，不要用它替代直接关系实验。

- **known ship 修改后舰队挑选结果仍旧**：priority 或 role cache 没有按同一对象恢复和重建。
    - **解决方案**：通过 `addKnownShip`、`removeKnownShip` 和 priority 方法修改，让实现负责清理缓存。

- **示例结束后蓝图仍被删除**：没有区分实验前 known 状态和本次新增状态。
    - **解决方案**：只撤销本次新增的 hull ID，原本已知的对象保持原状态。

## 总结

身份信息服务显示，稳定 ID 连接关系与知识查询。关系实验分别使用直接写入与声望事件，已知舰船实验通过对应方法更新集合与缓存，最后按保存的基线恢复。

这里可复用的方法是把原值、修改入口、实际结果和恢复放在一起。后面用这些状态安排生涯行为时，读取对象与保存范围仍按当前势力确认。

## 建议参考项

- `com.fs.starfarer.api.campaign.FactionAPI`：势力身份、关系和 known 集合入口，关系表与等级转换由其承载；
- `com.fs.starfarer.api.campaign.RepLevel`：关系等级和敌对边界；
- `com.fs.starfarer.api.campaign.SectorAPI`：`adjustPlayerReputation` 玩家声望 action 入口；
- `data/campaign/factions/`：势力配置和已知舰船数据。
