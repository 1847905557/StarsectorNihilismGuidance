# 维护舰队与成员状态

舰队被打残之后不会自己好起来。原版让它回家、停留、修满，靠的是一次战斗回调加一串任务。

修复本身有两条路：一条立刻修满，一条按天推进并消耗补给。给 NPC 用错了后一条，修复进度会停在半路。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C11-S1] 的巡逻舰队管理器与任务脚本，本节的监听器由管理器挂到每支舰队上；
- [C11-S2] 的旗舰与军官，修复时它们随成员一起恢复；
- [C10-S1] 的 Memory，返航标记写在舰队自己的 Memory 里。

## 状态存在三个地方

成员的状态不集中在一个对象上，读之前要知道去哪找。

| 要读什么 | 从哪里取 |
| --- | --- |
| 船体完整度、装甲损伤 | `getStatus()` |
| CR、封存、修复进度 | `getRepairTracker()` |
| 是否需要修、能不能部署 | 成员自己 |

船体完整度读 `getStatus().getHullFraction()`，0 到 1。模块船取本体。装甲按格读 `getArmorCellFractions(index)`，每一格一个比例。

CR 在维修跟踪器上。`getCR()` 是已经乘过船员比例的有效值，`getBaseCR()` 是未乘的，`getMaxCR()` 是上限。**写入用 `setCR(...)`，读回来的 `getCR()` 可能比写入值小**，船员不足时就是这样。

`needsRepairs()` 在成员与状态对象上都有。成员上的那个会连带判断封存与能否修，状态对象上的只看船体。

## 修复有两条路

改状态有两种写法，用途不同。

要立刻修满，直接用状态对象上的方法：

```java
member.getStatus().repairFully();
member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
```

`repairFully()` 把船体与装甲一起补满，不消耗补给、不占用时间。原版在战斗结束后的恢复、修船厂的整修、以及调试命令里都用它。`setCR(...)` 要另写一行，`repairFully()` 不动 CR。

要按进度慢慢修，用维修跟踪器：

```java
float done = member.getRepairTracker().computeRepairednessFraction();
member.getRepairTracker().performRepairsFraction(fraction);
member.getRepairTracker().getRemainingRepairTime();
```

这条路径按天推进、消耗补给，是玩家舰队在生涯里的修法。`computeRepairednessFraction()` 综合船体与装甲给出修复程度。

**给 NPC 舰队用第一条，不要用第二条。** NPC 舰队没有补给账户，走第二条只会让它停在修复进度上。原版给 NPC 修船时直接调 `repairFully()`。

## 损伤也能直接写

除了修，还能造伤。这几个方法在状态对象上：

```java
void applyDamage(float hitStrength);
void applyHullFractionDamage(float fraction);
void setHullFraction(float fraction);
void repairHullFraction(float fraction);
void disable();
```

`setHullFraction(...)` 直接给一个比例，`applyHullFractionDamage(...)` 在这个比例上扣。`disable()` 让成员进入失效状态，之后要靠 `repairDisabledABit()` 慢慢恢复。

上面这些是状态层的东西。接下来让巡逻队受伤后返航。

## 在战斗之后检查伤情

舰队的状态在战斗结束时才发生变化，所以检查放在战斗结束的回调里，不必每帧轮询。回调来自 `FleetEventListener`：

- `reportBattleOccurred(fleet, primaryWinner, battle)`：舰队参与的战斗结束后调用；
- `reportFleetDespawnedToListener(fleet, reason, param)`：舰队从世界中移除时调用，本例用不到，方法体留空。

监听器通过 `fleet.addEventListener(...)` 挂到单支舰队上，[C11-S1] 的管理器在 `spawnFleet()` 里完成这一步。**直接挂在舰队上的监听器，回调里的 `fleet` 参数不为空**；只有向全局监听器管理器登记的监听器，才会收到 `null`。

检查的内容是任一成员的船体完整度是否低于 60%：

```java
for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
    if (member.getStatus().getHullFraction() < RETURN_HULL_FRACTION) {
        return true;
    }
}
return false;
```

阈值取 60%：巡逻队是用来守在轨道上的，伤到一半以上还在巡逻，玩家只会当作靶子。需要更谨慎的舰队，把它调高即可。

## 返航与修复

发现伤势过重后，监听器在舰队 Memory 写入返航标记，清空任务，再改派返航任务：

```java
fleet.getMemoryWithoutUpdate().set(DAMAGED_KEY, true);
fleet.clearAssignments();
fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source, 30f, "returning for repairs");
fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 3f, "repairing", new RepairScript(fleet));
```

标记 `$GearIn_patrol_damaged` 的作用是防止重复处理：一场战斗之后可能连续触发回调，已经在返航的舰队不应再次清空任务。

`addAssignment(...)` 的最后一个参数是 `Script`，任务完成时执行。本例的 `RepairScript` 在 "repairing" 任务结束时运行，对每个成员调用 `repairFully()`，再把 CR 设为 `getMaxCR()`，最后通知舰队数据重新同步并清除标记：

```java
for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
    member.getStatus().repairFully();
    member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
}
fleet.getFleetData().setSyncNeeded();
fleet.getMemoryWithoutUpdate().unset(DAMAGED_KEY);
```

**修复之后，任务队列已经走空。** [C11-S1] 的任务脚本在 `getCurrentAssignment() == null` 时重新安排巡逻，所以舰队修好后自动回到巡逻，监听器不必再管。

::: warning 直接写入的状态不等于游戏的维修流程

`repairFully()` 与 `setCR(...)` 直接改写成员状态，不消耗补给，也不经过维修时间。这对 NPC 舰队是合适的，玩家舰队的维修由游戏自己的流程处理，不要用同样的方式改写。

:::

## 完整的伤情监听器

`src/data/scripts/gearin/campaign/GearInPatrolDamageListener.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** 巡逻队被击伤后返航修复：战斗后检查船体，低于阈值就改道回源点，到达后修复并清除标记。 */
public class GearInPatrolDamageListener implements FleetEventListener {

    /** 舰队 Memory：正在返航修复。 */
    public static final String DAMAGED_KEY = "$GearIn_patrol_damaged";

    /** 任一成员船体低于这个比例就返航。 */
    private static final float RETURN_HULL_FRACTION = 0.6f;

    private final SectorEntityToken source;

    public GearInPatrolDamageListener(SectorEntityToken source) {
        this.source = source;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (fleet == null || !fleet.isAlive() || source == null) {
            return;
        }
        if (fleet.getMemoryWithoutUpdate().getBoolean(DAMAGED_KEY)) {
            return;
        }
        if (!isBadlyDamaged(fleet)) {
            return;
        }
        fleet.getMemoryWithoutUpdate().set(DAMAGED_KEY, true);
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source, 30f, "returning for repairs");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 3f, "repairing", new RepairScript(fleet));
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    }

    private boolean isBadlyDamaged(CampaignFleetAPI fleet) {
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getStatus().getHullFraction() < RETURN_HULL_FRACTION) {
                return true;
            }
        }
        return false;
    }

    /** 修复完成后执行：恢复船体与 CR，清除返航标记，任务队列走空后由任务脚本重新安排巡逻。 */
    private static class RepairScript implements Script {

        private final CampaignFleetAPI fleet;

        RepairScript(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public void run() {
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                member.getStatus().repairFully();
                member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            }
            fleet.getFleetData().setSyncNeeded();
            fleet.getMemoryWithoutUpdate().unset(DAMAGED_KEY);
        }
    }
}
```

在 [C11-S1] 管理器的 `spawnFleet()` 中，紧接在 `addScript(...)` 之前挂上监听器：

```java
fleet.addEventListener(new GearInPatrolDamageListener(source));
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，用 `jump Nipher` 到巡逻队附近。

- 让巡逻队与其他舰队交战，使其中一艘舰船的船体低于 60%；
- 战斗结束后，舰队信息中的任务描述变为 `returning for repairs`，随后是 `repairing`；
- 回到源点停留之后，船体恢复，任务描述回到 `patrolling`；
- 控制台执行 `runcode` 读取该舰队 Memory 中的 `$GearIn_patrol_damaged`，返航期间为 `true`，修复后为 `false`。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **巡逻队受伤后仍在巡逻**：监听器没有挂到舰队上，或阈值过低。
    - **解决方案**：确认 `spawnFleet()` 中调用了 `addEventListener(...)`，并核对 `RETURN_HULL_FRACTION`。

- **回调里的 `fleet` 为 `null`**：监听器登记到了全局监听器管理器，而不是舰队本身。
    - **解决方案**：用 `fleet.addEventListener(...)` 直接挂在舰队上。

- **修复后舰队停在原地**：任务脚本没有在队列走空后重排任务。
    - **解决方案**：在 `advance(...)` 中检查 `getCurrentAssignment() == null`。

- **CR 设置之后读回来偏低**：`getCR()` 乘过船员比例，船员不足时低于写入值。
    - **解决方案**：核对 `getBaseCR()`，并检查成员的船员配置。

- **返航任务被反复清空**：没有用 Memory 标记过滤重复回调。
    - **解决方案**：处理之前先读 `DAMAGED_KEY`，已为 `true` 就返回。

## 建议参考项

- `com.fs.starfarer.api.campaign.listeners.FleetEventListener`：舰队监听器，本节只用到 `reportBattleOccurred(...)`。
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体损伤与修复的全部方法。
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR、封存与修复进度。
- `com.fs.starfarer.api.campaign.FleetAssignment`：各任务类型的含义。
- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager`：原版管理器怎样把自己登记为舰队监听器。
