# 管理舰队活动情报

玩家还没见过齿轮工业，自然也不该知道它的巡逻队在哪里。见过之后，情报栏里就多了一页 Nipher 的巡逻活动：哪几支巡逻队在外面，谁在带队，正在做什么。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C10-S1] 的 `$GearIn_contacted`，本节按它决定情报是否显示；
- [C11-S1] 的巡逻队与它的任务文字；
- [C14-S2] 按势力与 `$isPatrol` 识别巡逻队的写法，本节照用；
- [C14-S1] 的逐帧脚本，情报的生命周期与它有关。

## 情报由哪几部分组成

情报继承原版的 `BaseIntelPlugin`，界面上的每一处都对应一个方法：

| 方法 | 显示在哪里 |
| ---- | ---------- |
| `getName()` | 情报列表中的标题 |
| `getIcon()` | 标题旁的图标 |
| `createSmallDescription(...)` | 选中后右侧的详情 |
| `getMapLocation(...)` | 地图上定位到的实体 |
| `getIntelTags(...)` | 情报栏左侧的分类筛选 |
| `getFactionForUIColors()` | 标题与边框使用的势力颜色 |

图标从 `settings.json` 的 `graphics.intel` 中取，`fleet_log` 是原版舰队日志用的那一个，直接借用。分类标签在父类的基础上加入齿轮工业的势力 ID，玩家按势力筛选情报时能找到它。

## 打开时现场读取

这条情报不保存任何数据。玩家每次选中它，`createSmallDescription(...)` 都去 Nipher 星系里现场找一遍巡逻队：

```java
for (CampaignFleetAPI fleet : system.getFleets()) {
    if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
        continue;
    }
    if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
        continue;
    }
    count++;
    // 写出指挥官与当前任务
}
```

识别方式与 [C14-S2] 相同：齿轮工业的势力，加上 `$isPatrol` 标记。[C11-S1] 管理器生成的巡逻队和 Nipher Prime 按原版规则派出的巡逻队都会列出来。

每支巡逻队写一行：指挥官的名字，加上 `getCurrentAssignment()` 的任务文字。任务文字就是 [C11-S1] 用 `addAssignment(...)` 安排任务时填的那一句，原版舰队的任务文字同样是英文。指挥官或任务可能为空，读取前都要判断。

情报里的内容都是现场查出来的，舰队来来去去，它不需要跟着更新任何东西。

## 接触之前隐藏

`isHidden()` 返回 `true` 时，情报不出现在列表中：

```java
@Override
public boolean isHidden() {
    return super.isHidden()
            || !Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY);
}
```

父类的 `isHidden()` 还处理着教程进行中与 `setHidden(...)` 的情况，所以先保留父类的判断，再加上接触标记。玩家靠近 Nipher Prime、[C10-S1] 的接触脚本写下标记之后，情报就出现了。

## 一条不会结束的情报

这条情报从新档开始就一直在，Nipher 的巡逻也一直在。它不需要逐帧推进，也就没有登记成脚本。

需要结束的情报，原版的写法以 `PirateActivityIntel` 为例：

- 构造时 `Global.getSector().addScript(this)`，让它像 [C14-S1] 的脚本一样逐帧推进；
- 条件不再成立时，在 `advanceImpl(...)` 里调用 `endAfterDelay()`，情报标为结束，几天后从列表中移除；
- 在 `notifyEnded()` 里 `removeScript(this)`，不再推进。

如果想让每一次出动都单独发一条情报，就得在 [C11-S1] 管理器生成舰队的地方创建它，再按上面的方式在舰队消失时结束。本节只做一条汇总的情报，管理器保持不动。

## 加入情报管理器

在 `GearInModPlugin.onNewGame()` 中，紧接在 [C14-S1] 的循环脚本之后加入：

```java
sector.getIntelManager().addIntel(new GearInPatrolIntel(), true);
```

第二个参数为 `true` 时，加入情报时不弹出消息。开局时玩家还没接触齿轮工业，情报是隐藏的，这时弹消息没有意义。情报管理器随存档保存，所以只在新档里加入一次。

## 完整的巡逻情报

`src/data/scripts/gearin/campaign/GearInPatrolIntel.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.gearin.GearInModPlugin;

/** Nipher 的巡逻活动：列出星系内齿轮工业巡逻队的指挥官与当前任务。玩家接触齿轮工业之前不显示。 */
public class GearInPatrolIntel extends BaseIntelPlugin {

    private static final String SYSTEM_NAME = "Nipher";

    private static final String PRIME_ID = "GearIn_planet1";

    @Override
    public boolean isHidden() {
        return super.isHidden()
                || !Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY);
    }

    @Override
    protected String getName() {
        return "Nipher 的巡逻活动";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "fleet_log");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(GearInModPlugin.FACTION_ID);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(GearInModPlugin.FACTION_ID);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return Global.getSector().getEntityById(PRIME_ID);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        StarSystemAPI system = Global.getSector().getStarSystem(SYSTEM_NAME);
        if (system == null) {
            info.addPara("这个存档中没有 Nipher 星系。", opad);
            return;
        }
        int count = 0;
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
                continue;
            }
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
                continue;
            }
            count++;
            String commander = fleet.getCommander() != null ? fleet.getCommander().getNameString() : "无名指挥官";
            FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
            String action = assignment != null && assignment.getActionText() != null
                    ? assignment.getActionText() : "待命";
            info.addPara("%s 指挥的巡逻队：%s", opad, Misc.getHighlightColor(), commander, action);
        }
        if (count == 0) {
            info.addPara("Nipher 星系中暂时没有齿轮工业的巡逻队。", opad);
        }
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 打开情报界面，找不到 "Nipher 的巡逻活动"；
- 靠近 Nipher Prime 完成接触之后再打开，情报出现，图标为舰队日志；
- 选中它，右侧列出 Nipher 中每一支齿轮工业巡逻队的指挥官与任务，其中一支由 Brann Hale 指挥；
- 点击地图定位，镜头移到 Nipher Prime；
- 在左侧按势力筛选齿轮工业，情报仍在列表中。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **接触之后仍然看不到情报**：情报没有加入管理器，或者只在旧存档中测试。
    - **解决方案**：核对 `onNewGame()` 中的 `addIntel(...)`，使用新档测试。

- **教程期间也能看到这条情报**：覆写 `isHidden()` 时没有保留父类的判断。
    - **解决方案**：先调用 `super.isHidden()`，再加上自己的条件。

- **详情里没有任何巡逻队**：巡逻队尚未生成，或者筛选条件与 [C14-S2] 不一致。
    - **解决方案**：确认星系名为 `Nipher`，筛选同时检查势力与 `$isPatrol`。

- **打开详情时报空引用**：舰队没有指挥官，或者当前没有任务。
    - **解决方案**：读取指挥官与任务文字之前判断是否为 `null`。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：各个显示方法的默认实现、`isHidden()` 与结束流程。
- `com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivityIntel`：原版会结束的情报，登记脚本与 `endAfterDelay()` 的写法。
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：`addIntel(...)` 的各个重载。
- `com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI`：舰队当前任务与任务文字。
- `data/config/settings.json`：`graphics.intel` 中的原版情报图标。
