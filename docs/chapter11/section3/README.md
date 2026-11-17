# 维护舰队与成员状态

一场遭遇战打下来，几艘护卫舰拖着见底的船体完整度从玩家眼前撤出了战场。如果不给它们安排返航整修，这支受损的巡逻队就会一直带着这点残缺的船体留在星系里，直到在下一场遭遇中被海盗轻易击沉。

让 NPC 舰队自己回家修船用不着去算什么燃料和补给账本，在战后监听器里改派一趟返航任务就够了。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C10-S2] 的舰队级 `MemoryAPI` 与 `getMemoryWithoutUpdate()`；
- [C11-S1] 的巡逻舰队管理器与任务排程脚本；
- [C11-S2] 的 `FleetMemberAPI` 子对象结构。

## 成员状态的三个存放位置

单艘舰队成员的健康与战备数据并没有塞在同一个类里，而是按职责拆在了三个位置上：

| 状态类别 | 访问入口 | 常用方法与含义 |
| --- | --- | --- |
| 船体完整度与装甲损伤 | `member.getStatus()` (`FleetMemberStatusAPI`) | `getHullFraction()` 返回 `0` 至 `1` 的船体完整度比例；`getArmorCellFractions(index)` 按成员或模块状态槽索引 (单船传 `0`) 返回二维装甲格剩余比例 `float[][]` |
| 战备值 (CR)、封存与维修进度 | `member.getRepairTracker()` (`RepairTrackerAPI`) | `getCR()` 返回当前实际战备值，`getBaseCR()` 返回未乘船员比例的基础战备值，`getMaxCR()` 返回战备值上限 |
| 综合可战与可修判定 | `member` 自身 (`FleetMemberAPI`) | `needsRepairs()` 只判断船体/装甲损伤与模块拆离，`canBeRepaired()` 才排除已封存、暂停维修和战机联队，`canBeDeployedForCombat()` 判断能否出战 |

读写战备值时需要注意 `getCR()` 与 `getBaseCR()` 的差异：当舰船上的船员不足最低编制要求时，`getCR()` 返回的是按船员比例折算后的有效值，因此刚调用完 `setCR(maxCR)` 再立刻读 `getCR()`，在缺员的船上读回来的数字仍然可能低于写入值。

## 两种修复路径：瞬间回满与按天消耗补给

让一艘受损的 `FleetMemberAPI` 恢复状态有两条完全不同的 API 路径。

第一条是直接改写底层状态的**瞬间修复**：

```java
member.getStatus().repairFully();
member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
```

`repairFully()` 会把船体完整度与所有装甲格一次性补满，既不消耗补给，也不需要等待维修时间；但它不会改动战备值，因此需要再补一行 `setCR(getMaxCR())`。原版在殖民地船坞整修、模拟战结束恢复以及 NPC 舰队回港维修时，走的都是这条路。

第二条是玩家舰队在星图上航行时使用的**渐进式后勤维修**：

```java
float done = member.getRepairTracker().computeRepairednessFraction();
member.getRepairTracker().performRepairsFraction(fraction);
float remainingDays = member.getRepairTracker().getRemainingRepairTime();
```

这条路径依赖舰队货舱里的补给存量与每日维修速率按天推进。**不要对 NPC 舰队用第二条路径**：NPC 舰队并没有真实的日常补给消耗账户，调用渐进维修只会让它的修复进度停在半路。

反过来，如果需要在剧情或事件中主动给某艘成员制造战损，`FleetMemberStatusAPI` 也提供了对应的扣减方法：

```java
void applyDamage(float hitStrength);
void applyHullFractionDamage(float fraction);
void setHullFraction(float fraction);
void repairHullFraction(float fraction);
void disable();
```

其中 `setHullFraction(fraction)` 直接将船体设为指定比例，`applyHullFractionDamage(fraction)` 在现有比例上扣减，`disable()` 则让舰船进入战损瘫痪状态 (需调用 `repairDisabledABit()` 才能逐步脱困)。

## 监听战后损伤并改派返航任务

巡逻队的战损只会在战斗结算时发生变化，完全没有必要用每帧脚本去轮询船体血量。将检查逻辑写在 `com.fs.starfarer.api.campaign.listeners.FleetEventListener` 的战后回调里要干净得多：

- `reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)`：当挂载该监听器的舰队结束一场战斗时调用；
- `reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param)`：当舰队从星图移除时调用 (本节无需处理，留空即可)。

通过 `fleet.addEventListener(...)` 直接挂在单支舰队身上的监听器，回调收到的第一个参数 `fleet` 就是这支舰队本身 (只有注册到全局 `ListenerManager` 的监听器才会在这里收到 `null`)。

我们在 `reportBattleOccurred(...)` 中遍历幸存成员，只要发现任意一艘船的 `getHullFraction()` 降到 `0.6f` (60%) 以下，就中断当前巡逻、改派返航维修任务：

```java
fleet.getMemoryWithoutUpdate().set(DAMAGED_KEY, true);
fleet.clearAssignments();
fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source, 30f, "returning for repairs");
fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 3f, "repairing", new RepairScript(fleet));
```

这里有三处环环相扣的设计：

1. **用舰队 Memory 标记 `$GearIn_patrol_damaged` 防重入**：一场大规模接战可能连续触发多次战斗结算回调；在首次触发返航时写下 `$GearIn_patrol_damaged = true`，后续回调读到该标记就直接跳过，避免反复清空正在执行的返航任务。
2. **用任务完成回调 `RepairScript` 兑现修复**：`addAssignment(...)` 的最后一个参数传入了一个实现 `com.fs.starfarer.api.Script` 的内部类。当舰队飞回 Nipher Prime 并在轨道上完成 3 天的 `"repairing"` 环绕后，引擎自动调用 `RepairScript.run()`，对全队成员执行 `repairFully()` 与 `setCR(...)`，随后调用 `fleet.getFleetData().setSyncNeeded()` 刷新舰队状态并清除 `$GearIn_patrol_damaged` 标记。
3. **自动接回常规巡逻**：当 `"repairing"` 任务走完时，舰队的任务队列正好变空 (`getCurrentAssignment() == null`)。[C11-S1] 挂在舰队上的 `GearInPatrolAssignmentScript` 在下一帧检测到空队列，就会自动为修葺一新的巡逻队排入新一轮巡逻任务，两边无需任何额外通信。

## 完整的损伤监听器：GearInPatrolDamageListener

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

### 接入巡逻队管理器

在 [C11-S1] 的 `GearInPatrolFleetManager.spawnFleet()` 中，于挂载 `GearInPatrolAssignmentScript` 之前为舰队挂上该监听器：

```java
fleet.addEventListener(new GearInPatrolDamageListener(source));
fleet.addScript(new GearInPatrolAssignmentScript(fleet, source));
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，执行 `jump Nipher` 找到巡逻队。

- 吸引一支敌方舰队与巡逻队交火 (或主动攻击巡逻队后脱离)，使巡逻队中至少一艘舰船的船体完整度低于 60%；
- 战斗结束返回星图后，巡逻队的行动描述立即变为 `returning for repairs`，飞抵 Nipher Prime 后变为 `repairing`；
- 环绕 3 天结束后，受损舰船的船体与装甲完全恢复，行动描述重新切回 `patrolling`。

## 常见问题

- **巡逻队成员被打成重伤后仍然继续巡逻，不返航**：`spawnFleet()` 中漏挂了 `fleet.addEventListener(...)`，或受损舰船在战斗中已被直接击毁移出了成员列表。
    - **解决方案**：核对 `addEventListener(...)` 调用位置，并确保测试时舰船是受损幸存而非被击沉。

- **在 `reportBattleOccurred(...)` 中收到的 `fleet` 参数为 `null`**：将监听器注册到了 `Global.getSector().getListenerManager()`，而非单支舰队的 `fleet.addEventListener(...)`。
    - **解决方案**：针对特定舰队的战后监听直接挂在 `CampaignFleetAPI` 实例上。

- **返航修好之后，巡逻队停在行星轨道上不再恢复巡逻**：舰队上没有挂载检查 `getCurrentAssignment() == null` 的任务重排脚本。
    - **解决方案**：确认 [C11-S1] 的 `GearInPatrolAssignmentScript` 已正常挂载。

## 建议参考项

- `com.fs.starfarer.api.campaign.listeners.FleetEventListener`：单支舰队的战后与解散事件监听接口。
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体完整度、装甲格状态与 `repairFully()`。
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：战备值 (`CR`)、封存状态与维修进度跟踪器。
- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager`：原版源点管理器自身对 `FleetEventListener` 的实现。
