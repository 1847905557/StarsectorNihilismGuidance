# 监听生涯事件

游戏里凡是 "某件事发生了，我要跟着做点什么" 的需求，都归生涯事件。舰队被击毁、市场完成交易、玩家打开某个界面，星区会把这些通知给登记过的监听器。

它和 [C14-S1] 的脚本不同：脚本是自己数着时间推进，监听器什么都不做，只在被叫到的时候响应。需要记录 "谁做了什么" 以及 "做了多少次" 时，用监听器；需要连续推进的节奏，才用脚本。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C11-S3] 挂在单支舰队上的监听器，本节换成全局的监听器；
- [C11-S1] 给巡逻队设置的 `$isPatrol` 标记，本节按它识别巡逻队；
- [C10-S1] 的全局 Memory 与限时值；
- [C10-S3] 的关系读写；
- [C14-S1] 中随存档保存与临时登记的区别，监听器同样适用。

## 监听器挂在哪里

[C11-S3] 的伤情监听器挂在巡逻队自己身上，只收到这一支舰队的事件。这次要记的是齿轮工业的所有巡逻队：除了 [C11-S1] 管理器生成的那支，Nipher Prime 的市场也会按原版规则派出自己的巡逻队。一支一支去挂监听器既麻烦，也挂不全。

全局的生涯监听器登记在星区上，所有战斗、舰队消失、市场交易等事件都会通知它。继承原版的 `BaseCampaignEventListener`，它为每个回调都提供了空实现，只覆写需要的那一个：

```java
public class GearInPatrolGrudgeListener extends BaseCampaignEventListener {

    public GearInPatrolGrudgeListener() {
        super(false);
    }
}
```

构造参数为 `true` 时，父类会在构造时直接把自己登记为随存档保存的监听器。这里传 `false`，登记方式由入口类自己决定。

## 选择登记方式

星区提供三种登记：

| 方法 | 是否随存档保存 |
| ---- | -------------- |
| `addListener(listener)` | 是 |
| `addTransientListener(listener)` | 否，读档后需要重新登记 |
| `addListenerWithTimeout(listener, days)` | 是，经过指定生涯天数后移除 |

这个监听器只负责记账，账本写在全局 Memory 里，它自己一个字段也没有，随存档保存没有任何好处。所以用临时登记，在 `GearInModPlugin.onGameLoad(...)` 中每次读档登记一份：

```java
sector.addTransientListener(new GearInPatrolGrudgeListener());
```

一次读档只会调用一次 `onGameLoad(...)`，临时监听器在读档时随旧的生涯一起丢弃，所以这里不需要检查重复。

## 判断被击败的是不是巡逻队

战斗回调是 `reportBattleOccurred(primaryWinner, battle)`。原版注释写明，AI 之间的自动结算每一轮都会调用它，玩家参与的战斗则每次遭遇只调用一次。先排除与玩家无关的战斗：

```java
if (!battle.isPlayerInvolved()) {
    return;
}
```

不排除的话，星区里每一场 AI 之间的小冲突都会进来走一遍。

敌方舰队从 `getNonPlayerSideSnapshot()` 取。快照记录的是战斗开始前的阵容，战斗中被全歼、已经从地图上消失的舰队也在里面：

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

势力与 `$isPatrol` 两项都要核对。只看势力，商船队和运输队被打也会算进来；只看标记，别的势力的巡逻队也会算进来。`wasFleetDefeated(...)` 是原版任务判断某支舰队是否战败的写法。

## 记账

一场战斗里打掉了几支巡逻队，就记几笔：

```java
memory.set(LOST_KEY, memory.getFloat(LOST_KEY) + defeated);
memory.set(GRUDGE_KEY, true, GRUDGE_DAYS);
sector.getFaction(GearInModPlugin.FACTION_ID)
        .adjustRelationship(sector.getPlayerFaction().getId(), -RELATION_PENALTY * defeated);
```

- `$GearIn_patrols_lost` 是累计数，永不过期，键不存在时 `getFloat(...)` 读到 0；
- `$GearIn_patrol_grudge` 是 60 天的限时标记，每被打一次就重新计时，后面 Rules 章节的对话会用它判断玩家最近有没有动过手；
- 关系每支扣 0.05。一支巡逻队算不上什么大事，每次只扣一点；可要是一直打下去，关系照样会一路掉到底。

最后在界面左侧留一条消息，让玩家知道齿轮工业已经记了一笔。

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

/** 玩家击败齿轮工业的巡逻队后，势力记下这笔账：累计次数、限时记仇标记，并降低关系。 */
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

执行 `gradle jar`，重启游戏，**新建一份存档**，到 Nipher 星系找一支齿轮工业的巡逻队，主动攻击并取胜。

- 战斗结束后，界面左侧出现 "齿轮工业记下了被击败的巡逻队"；
- 在控制台执行 `runcode Global.getSector().getMemoryWithoutUpdate().getFloat("$GearIn_patrols_lost")`，结果为 `1.0`；
- 外交界面中，齿轮工业对玩家的关系比战前更低；
- 保存并重新读档，再击败一支巡逻队，计数变为 `2.0`。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **击败巡逻队之后计数没有变化**：监听器没有登记，或者被击败的舰队没有 `$isPatrol` 标记。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记，再确认 [C11-S1] 的巡逻行为标记已经写入。

- **读档之后不再记账**：临时监听器只在 `onNewGame()` 中登记了一次。
    - **解决方案**：登记放进 `onGameLoad(...)`，每次读档都执行。

- **一场战斗记了两笔**：监听器被登记了两份，常见于构造时传了 `true`，入口类又登记了一次。
    - **解决方案**：构造参数传 `false`，只保留入口类的登记。

- **玩家没参战，计数也在涨**：没有检查 `isPlayerInvolved()`，AI 之间的战斗也被算了进来。
    - **解决方案**：回调开头先排除与玩家无关的战斗。

## 建议参考项

- `com.fs.starfarer.api.campaign.BaseCampaignEventListener`：全部生涯事件回调的空实现与 `permaRegister` 参数。
- `com.fs.starfarer.api.campaign.CampaignEventListener`：`reportBattleOccurred(...)` 的调用次数说明。
- `com.fs.starfarer.api.campaign.BattleAPI`：战前快照与 `wasFleetDefeated(...)`。
- `com.fs.starfarer.api.impl.campaign.missions.askonia.SDSevenSons`：原版任务判断舰队战败的写法。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：`MEMORY_KEY_PATROL_FLEET` 等舰队标记。
