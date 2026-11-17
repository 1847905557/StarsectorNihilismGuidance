# 监听生涯事件

舰队交战、市场成交、打开对话乃至经济月结，星区会在这些节点发生时把事件广播给所有已注册的 `CampaignEventListener`。和 [C14-S1] 按帧推进的逐帧脚本不同，监听器平时完全静止，只在特定事件触发的那一刻执行一次回调。

用这套事件机制给齿轮工业挂上一份巡逻队战损统计：玩家击毁巡逻队时，立即记下累计战损、写入限时敌意标记并扣减势力关系。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C11-S3] 挂在单支舰队上的监听器，本节换成全局监听器；
- [C11-S1] 给巡逻队设置的 `$isPatrol` 标记，本节按它识别巡逻队；
- [C10-S2] 的全局 Memory 与限时值；
- [C10-S4] 的关系读写；
- [C14-S1] 中随存档保存与临时登记的区别，监听器同样适用。

## 全局监听与事件家族

[C11-S3] 的损伤监听器直接挂在单支舰队上，只能收到那一支舰队的消息。真要追踪全星区里所有属于某个势力的舰队 —— 包括 [C11-S1] 管理器生成的巡逻队，以及 Nipher Prime 按原版市场规则自发派出的巡逻队 —— 逐支去挂既麻烦也容易挂漏，这时应当把监听器直接登记到 `SectorAPI` 上。

全局监听器通常继承 `BaseCampaignEventListener`，它替 `CampaignEventListener` 的全部回调准备了空实现，您只需覆写关心的那一两个入口：

```java
public class GearInPatrolGrudgeListener extends BaseCampaignEventListener {

    public GearInPatrolGrudgeListener() {
        super(false);
    }
}
```

构造参数传 `true` 时，父类会在构造瞬间自动把自己作为持久监听器登记进星区；这里显式传 `false`，把登记方式与时机全权交给入口类掌控。

除战斗事件外，`BaseCampaignEventListener` 还覆盖了生涯里最常用的几类广播节点：

| 回调方法 | 触发时机 | 常见用途 |
| -------- | -------- | -------- |
| `reportBattleOccurred(...)` / `reportBattleFinished(...)` | 战斗产生结果或结束时 | 统计战果、触发势力仇恨或任务进度推进 |
| `reportPlayerMarketTransaction(...)` | 玩家在市场完成买卖交易后 | 追踪走私量、特定商品成交额或关税回扣 |
| `reportShownInteractionDialog(...)` | 玩家打开实体或市场对话时 | 检查特定遭遇或补充额外记忆键 |
| `reportFleetSpawned(...)` / `reportFleetDespawned(...)` | 舰队生成或离开星区消失时 | 监听事件舰队存活与回收 |
| `reportEconomyTick(...)` / `reportEconomyMonthEnd()` | 每个经济周期或月结算节点 | 挂载周期性财政或市场联动账目 |

## 选择登记方式

星区针对监听器同样提供了持久、临时与限时三种登记入口：

| 方法 | 是否随存档保存 | 适用场景 |
| ---- | -------------- | -------- |
| `addListener(listener)` | 是 | 监听器自身字段存有未入 Memory 的运行状态 |
| `addTransientListener(listener)` | 否，读档时丢弃 | 无字段或只读写 Memory 的事件记账器，由 `onGameLoad(...)` 重挂 |
| `addListenerWithTimeout(listener, days)` | 是 | 只想监听未来几十个生涯天的临时事件窗口 |

下面的巡逻队敌意监听器完全依靠全局 Memory 存账，自身没有任何可变字段，因此放在 `GearInModPlugin.onGameLoad(...)` 中做临时登记是最干净的选择：

```java
sector.addTransientListener(new GearInPatrolGrudgeListener());
```

注意 `GearInModPlugin.onGameLoad(boolean newGame)` 在玩家**开新档之后也会自动调用一次**，因此临时监听器只需要写在 `onGameLoad(...)` 里一处即可 —— 切忌在 `onNewGame()` 里也挂一遍，否则开新档的第一局里每次战斗都会记双倍战损。

## 筛选目标巡逻队与结算战果

战斗结束的回调之一是 `reportBattleOccurred(primaryWinner, battle)`。需要注意：AI 与 AI 之间的自动结算每一轮都会触发该方法，而玩家参与的战斗每次遭遇只会调用一次。如果只想追究玩家的手笔，开头第一件事就应当拦截非玩家战斗：

```java
if (!battle.isPlayerInvolved()) {
    return;
}
```

随后遍历 `battle.getNonPlayerSideSnapshot()`。引擎把交战前敌方阵营的所有舰队都记在这份快照里 —— 哪怕某支巡逻队已经在战斗中被打散并从地图上移除，依然能在这里查到它：

```java
for (CampaignFleetAPI fleet : battle.getNonPlayerSideSnapshot()) {
    if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
        continue;
    }
    if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
        continue;
    }
    if (battle.wasFleetDefeated(fleet, primaryWinner)) {
        defeated++;
    }
}
```

这里的筛选条件一个也不能少：**只检查势力会把齿轮工业的商船和补给船算成巡逻队，只检查 `MemFlags.MEMORY_KEY_PATROL_FLEET` (`$isPatrol`) 则会把其他势力的巡逻队混进来**。`battle.wasFleetDefeated(fleet, primaryWinner)` 用于核实 `fleet` 在本次交火中处于战败一方 (即与 `primaryWinner` 属于对立阵营；只要玩家方获胜而未撤退，即便敌方仍有余舰脱战也会返回 `true`)。

## 写入累计次数、限时敌意标记与声望惩罚

只要本次战斗至少击溃了一支齿轮工业巡逻队，就一并完成后续状态写入：

```java
memory.set(LOST_KEY, memory.getFloat(LOST_KEY) + defeated);
memory.set(GRUDGE_KEY, true, GRUDGE_DAYS);
sector.getFaction(GearInModPlugin.FACTION_ID)
        .adjustRelationship(sector.getPlayerFaction().getId(), -RELATION_PENALTY * defeated);
```

- `$GearIn_patrols_lost` 写入永久累计数量，键尚不存在时 `getFloat(...)` 默认返回 `0`；
- `$GearIn_patrol_grudge` 设为 `60` 天自动过期的限时标记，每再打掉一次巡逻队就重新刷新倒计时，后续 [C14-S3] 的拒绝停靠与 `C15` 的 Rules 对话都要读它判断齿轮工业最近有没有被惹火；
- 每击溃一支额外扣减 `0.05` 势力关系，并通过左下角消息提醒玩家齿轮工业已经把这笔账记下。

## 完整的监听器

`src/data/scripts/gearin/campaign/GearInPatrolGrudgeListener.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import data.scripts.gearin.GearInModPlugin;

/** 玩家击败齿轮工业的巡逻队后，势力记下这笔账：累计次数、限时敌意标记，并降低关系。 */
public class GearInPatrolGrudgeListener extends BaseCampaignEventListener {

    /** 全局 Memory：玩家累计击败的齿轮工业巡逻队数量。 */
    public static final String LOST_KEY = "$GearIn_patrols_lost";

    /** 全局 Memory：最近击败过齿轮工业的巡逻队，60 天后自动消失。 */
    public static final String GRUDGE_KEY = "$GearIn_patrol_grudge";

    private static final float GRUDGE_DAYS = 60f;

    /** 每击败一支巡逻队，关系额外下降的数值。 */
    private static final float RELATION_PENALTY = 0.05f;

    public GearInPatrolGrudgeListener() {
        super(false);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) {
            return;
        }
        int defeated = 0;
        for (CampaignFleetAPI fleet : battle.getNonPlayerSideSnapshot()) {
            if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
                continue;
            }
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
                continue;
            }
            if (battle.wasFleetDefeated(fleet, primaryWinner)) {
                defeated++;
            }
        }
        if (defeated == 0) {
            return;
        }

        SectorAPI sector = Global.getSector();
        MemoryAPI memory = sector.getMemoryWithoutUpdate();
        memory.set(LOST_KEY, memory.getFloat(LOST_KEY) + defeated);
        memory.set(GRUDGE_KEY, true, GRUDGE_DAYS);
        sector.getFaction(GearInModPlugin.FACTION_ID)
                .adjustRelationship(sector.getPlayerFaction().getId(), -RELATION_PENALTY * defeated);
        sector.getCampaignUI().addMessage("齿轮工业记下了被击败的巡逻队。");
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，在 Nipher 星系找一支齿轮工业巡逻队交战并取胜：

- 战斗结束后，界面左下角出现 `"齿轮工业记下了被击败的巡逻队。"`；
- 控制台执行 `runcode Global.getSector().getMemoryWithoutUpdate().getFloat("$GearIn_patrols_lost")` 返回 `1.0`；
- 外交界面中齿轮工业对玩家的关系进一步下降；
- 保存并重新读档后再次击败一支巡逻队，计数递增为 `2.0`。

## 常见问题

- **击败巡逻队后计数没有增加**：监听器未注册，或者目标舰队没有 `$isPatrol` 标记。
    - **解决方案**：核对 `onGameLoad(...)` 中的 `addTransientListener(...)`，并确认巡逻队已写入 `MemFlags.MEMORY_KEY_PATROL_FLEET`。

- **读档之后监听器突然失效**：把临时监听器写进了 `onNewGame()` 而没有写进 `onGameLoad(...)`。
    - **解决方案**：临时监听器统一放在 `onGameLoad(...)` 中注册。

- **同一场战斗被重复计入两次**：父类构造 `super(...)` 传了 `true` 自我登记一次，同时入口类又额外注册了一次；或者在 `onNewGame()` 与 `onGameLoad(...)` 两处同时调用了注册。
    - **解决方案**：确认 `super(false)` 且只在 `onGameLoad(...)` 中注册一遍。

- **玩家未参战，AI 之间的战斗也增加了计数**：忘记检查 `battle.isPlayerInvolved()`。
    - **解决方案**：在回调开头过滤掉玩家未参与的战斗。

## 建议参考项

- `com.fs.starfarer.api.campaign.BaseCampaignEventListener`：全部生涯事件回调的空实现与 `permaRegister` 构造参数。
- `com.fs.starfarer.api.campaign.CampaignEventListener`：`reportBattleOccurred(...)` 等事件接口定义与调用频次说明。
- `com.fs.starfarer.api.campaign.BattleAPI`：`getNonPlayerSideSnapshot()` 与 `wasFleetDefeated(...)`。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：`MEMORY_KEY_PATROL_FLEET` 等舰队标准标记。
- `com.fs.starfarer.api.impl.campaign.missions.askonia.SDSevenSons`：原版任务判断特定舰队战败的用法。
