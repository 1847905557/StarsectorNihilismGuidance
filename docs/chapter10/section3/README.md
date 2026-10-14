# 配置生涯势力

本节通过势力对象调整关系与军备知识。关系实验比较直接写入和玩家声望事件，舰船实验比较已知、优先与学习时间；两项操作都保存原状态并恢复。我们将从这些具体结果说明，界面关系、知识集合与后续舰船挑选怎样使用它们。

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

## 取得当前势力对象

`FactionAPI` 提供显示资料、关系和军备知识。玩家势力从当前 Sector 取得，指定势力则按稳定 ID 查询。以下局部片段使用玩家与霸主：

```java
SectorAPI sector = Global.getSector();
FactionAPI player = sector.getPlayerFaction();
FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);

String playerId = player.getId();
String displayName = hegemony.getDisplayName();
Color baseColor = hegemony.getBaseUIColor();
```

`getDisplayName()` 和 `getBaseUIColor()` 用于界面显示，关系查询使用 `getId()` 或 `Factions.HEGEMONY`。显示名称可以本地化，调用键继续按势力 ID 对应。

本节分别做关系与已知舰船两项实验，每次写入之后恢复原状态。创建 `src/data/scripts/gearin/campaign/GearInFactionRelationProbe.java` 和 `GearInFactionKnownShipProbe.java`，将类的 import 加入 `GearInModPlugin`，在已有 `onGameLoad(...)` 中登记：

```java
sector.addTransientScript(new GearInFactionRelationProbe());
sector.addTransientScript(new GearInFactionKnownShipProbe());
```

两个脚本各执行一轮，完成后 `isDone()` 返回 `true`，由生涯引擎移除。读取、修改与恢复都在这一轮中完成，下一次进入存档再建立本次实验。

## 关系数值怎样成为界面状态

当前关系同时可以表示为 float、`RepLevel` 与敌对状态。读取时指定调用势力与目标 ID，反向读取则交换两端：

```java
float playerToHegemony = player.getRelationship(hegemony.getId());
float hegemonyToPlayer = hegemony.getRelationship(player.getId());
RepLevel level = player.getRelationshipLevel(hegemony.getId());
boolean hostile = player.isHostileTo(hegemony.getId());
```

`playerToHegemony` 与 `hegemonyToPlayer` 来自同一项普通双边关系。日志保留两种方向，是为了确认调用的双方；`RepLevel` 根据数值计算，敌对状态再按等级判定。

这几个结果可以一起解释界面：数值记录具体变化，等级提供当前关系档位，敌对状态提供实际行为判断。只是等级相同，并不说明档位内的数值也完全相同。

## 直接调整关系，或提交玩家声望事件

要直接写入两势力的关系，使用 `setRelationship(...)` 或 `adjustRelationship(...)`。前者设值，后者按增量调整。玩家声望事件则从 `SectorAPI.adjustPlayerReputation(...)` 提交，游戏选择对应的响应插件，再由插件处理变化并返回结果。

本例让两种方式各增加 `0.05f`，在它们之间先恢复关系。局部过程如下：

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

前半段直接调整并写回 `before`。后半段用 `CustomRepImpact.delta` 提供请求增量，`RepActionEnvelope` 将它包装为 `CUSTOM` 声望事件；`withMessage=false` 让本次记录集中到日志。

`ReputationAdjustmentResult.delta` 保存事件处理结果，当前关系还要从势力重新读取。接近关系上限时，实际增量受到范围限制，响应插件也参与声望处理，因此比较请求值、返回值与关系实值。

### 精确恢复实验前的关系

完整例分别记录直接调整的 `before`、`during` 与 `after`，以及声望事件的 `delta`、`during` 与 `restored`。每项修改结束后，都用实验前保存的 float 恢复。

::: warning 关系等级不足以恢复具体原值

`setRelationship(String, RepLevel)` 会将等级转换为对应数值。原值可能位于等级中的任意位置，恢复实验时使用 `setRelationship(String, float)`，才能保留原来那一项具体关系。

:::

这次恢复针对关系表。声望事件的调用与返回仍按自己的路径记录，后续正式功能选择入口时，也依据需要直接设定关系还是产生声望响应来决定。

## 修改势力可以使用的舰船

势力的已知舰船集合与优先集合参与舰船挑选。`getKnownShips()` 读取知识，`knowsShip(...)` 与 `isShipPriority(...)` 分别查询指定船体。局部修改方法为：

```java
Set<String> knownShips = faction.getKnownShips();
boolean wasKnown = faction.knowsShip(hullId);
boolean wasPriority = faction.isShipPriority(hullId);

faction.addKnownShip(hullId, false);
faction.addPriorityShip(hullId);
faction.removePriorityShip(hullId);
faction.removeKnownShip(hullId);
```

本例使用原版 `lasher`。加入之前保存是否已知、是否优先和学习时间戳，再写入 known 与 priority，记录之后按原状态恢复。

`addKnownShip(hullId, false)` 的第二个参数控制新增时是否记录学习时间。这里传入 `false`，本次新增不写时间戳。若船体原来已经已知，完成实验后仍然保留；优先状态与原时间戳也分别写回。

### 集合变化与挑选缓存

`addKnownShip(...)`、`removeKnownShip(...)` 与 priority 修改方法还会清理舰船角色挑选缓存，后续生成才能依据新集合重新选择。移除已知舰船也涉及优先状态与学习时间，所以恢复不能只记住一个 known 布尔值。

**编者建议通过这些修改方法调整知识集合。** getter 返回的集合可以用于读取，直接改集合却不会替您执行配套的缓存清理与状态处理。集合中的 ID 已经变化而后续挑选仍用旧结果，正是这种差别难以从界面直接辨认的地方。

武器、战机与船插还有各自的知识集合。本节只完整处理舰船，后续使用其他对象时，也核对它对应的修改入口与配套状态。

## 完整最小例

将两类保存到前述源码目录，保留生涯登记，再构建 JAR。关系脚本完成两种写入与恢复，舰船脚本完成 known、priority 与时间戳的比较。

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

关系与军备知识都属于当前势力，显示文本则由身份资料提供。直接写入关系表与提交玩家声望事件经过不同处理，已知舰船方法还维护优先、时间戳与挑选缓存。

两个实验都回到保存的原状态。正式功能使用这些方法时，再按当前目的选择写入路径，实际结果从对应势力与返回对象取得。

## 建议参考项

- `com.fs.starfarer.api.campaign.FactionAPI`：势力身份、关系和 known 集合入口，关系表与等级转换由其承载；
- `com.fs.starfarer.api.campaign.RepLevel`：关系等级和敌对边界；
- `com.fs.starfarer.api.campaign.SectorAPI`：`adjustPlayerReputation` 玩家声望 action 入口；
- `data/world/factions/`：势力配置和已知舰船数据。
