# 管理舰队活动情报

情报栏里的态势提醒与势力动态，底层都由 `BaseIntelPlugin` 交给 `IntelManagerAPI` 托管。它把摘要、详情、定位与分类标签拆成独立覆写点，还自带推进与过期归档状态机，无论状态实时查询还是限时通告都能胜任。

这节写一条轻量的常驻巡逻情报：接触前隐藏，照面后在每次点开时现场列出 Nipher 星系的巡逻队动向。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C10-S2] 的 `$GearIn_contacted`，本节按它控制情报何时显形；
- [C11-S1] 的巡逻队及其英文任务文字；
- [C11-S2] 分配给巡逻队的指挥官，详情页会展示他的军衔与姓名；
- [C14-S2] 按势力与 `$isPatrol` 识别巡逻队的写法；
- [C14-S1] 的逐帧脚本登记与存档机制，限时情报会用到它。

## BaseIntelPlugin 的界面结构

自定义情报一般继承 `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`，UI 界面上的每个位置各对应一个覆写点：

| 方法 | 控制 UI 哪一块 |
| ---- | -------------- |
| `getName()` | 左侧情报条目与右侧详情的标题 |
| `getIcon()` | 标题左侧图标，一般从 `settings.json` 的 `graphics.intel` 读取 |
| `createIntelInfo(info, mode)` | 左侧列表条目下的简要副标题或摘要说明 |
| `createSmallDescription(info, width, height)` | 选中条目后右侧弹出的默认详情栏 |
| `hasLargeDescription()` / `createLargeDescription(...)` | 需要更宽大的自定义面板、报表或交互按钮时启用大详情页 |
| `getMapLocation(map)` | 点击定位图标时星图镜头飞往的实体 |
| `getIntelTags(map)` / `getSortTier()` | 左侧分类筛选标签与排序权重 (例如叠加齿轮工业的势力 ID) |
| `getFactionForUIColors()` | 右侧详情区标题、分隔线与底部操作按钮采用的势力主色调 (列表左侧标题与正文高亮色不自动沿用此色) |

如果某条情报只需要简明列出当下状态或几行文本，`createSmallDescription(...)` 加上这几个基础元信息方法就已经够用；需要给玩家呈现图表或可点击管理按钮时，才开启 `hasLargeDescription()`。

## 现场查询型与推进型情报

在情报的数据来源与存续时长上，Starsector 通常分成两种做法：

1. **实时查询型 (不登记为脚本)**：例如本节的 Nipher 巡逻情报，随新档加入 `IntelManagerAPI` 后长期存在，自身不缓存舰队列表；每当玩家选中它触发 `createSmallDescription(...)`，现场遍历 Nipher 星系的舰队再拼接文本：

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

识别口径与 [C14-S2] 保持一致：**同时检查所属势力与 `MemFlags.MEMORY_KEY_PATROL_FLEET` (`$isPatrol`)**，既把 [C11-S1] 管理器生成的巡逻队收进来，也把 Nipher Prime 按原版规则自发派出的巡逻队一并列出。读取 `fleet.getCommander()` 与 `fleet.getCurrentAssignment()` 前要先判空，否则刚生成或刚刚清空任务队列的舰队会抛出空指针。

2. **定时推进 / 自动结束型 (`addScript` 联动 `endAfterDelay()`)**：如果做的是一条有明确起止期限的动态情报 (如原版 `PirateActivityIntel` 或某次限时入侵)，除了加入 `IntelManagerAPI`，还要把同一对象传给 `Global.getSector().addScript(this)` —— 因为 **`BaseIntelPlugin` 本身已经实现了 `EveryFrameScript` 并内置 `endingTimeRemaining` 状态机**：条件结束后在 `advanceImpl(...)` 调一次 `endAfterDelay()`，插件会在界面上标为已结束并在倒计时归零后触发 `notifyEnded()`，届时在 `notifyEnded()` 中调用 `removeScript(this)` 即可干净退出。

## 接触之前隐藏与静默入栏

只要覆写 `isHidden()`，就能让情报在特定条件达成前彻底从列表隐身：

```java
@Override
public boolean isHidden() {
    return super.isHidden()
            || !Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY);
}
```

先保留 `super.isHidden()` 以免在新手教程期间或外部主动隐藏时穿帮，再叠加 `$GearIn_contacted` 判断 —— 一旦玩家在 [C10-S2] 靠近 Nipher 星系完成首次接触，情报便会立即亮出。

在 `GearInModPlugin.onNewGame()` 中，紧接在 [C14-S1] 的循环脚本之后注册：

```java
sector.getIntelManager().addIntel(new GearInPatrolIntel(), true);
```

第二个参数 `forceNoMessage` 设为 `true` 时，入栏瞬间**不会**在左下角弹窗通知 —— 开局时玩家连齿轮工业还没见过，弹出新情报提示既突兀也毫无意义；而对于玩家打完战斗或接下任务后需要当场提醒的新情报，再改传 `false` 即可。`IntelManagerAPI` 本身随存档保存，因此只需在新档初始化时加入一次。

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

执行 `gradle jar`，重启游戏，**新建一份存档**：

- 刚开局在星图里直接打开情报界面，列表中不会出现 `"Nipher 的巡逻活动"`；
- 靠近 Nipher Prime 触发接触脚本后再次查看，情报显现并带舰队日志图标；
- 选中该情报，右侧会现场列出 Nipher 星系内各支齿轮工业巡逻队的指挥官姓名 (包括 Brann Hale) 与当前任务文字；
- 点击地图定位按钮，镜头居中定位到 Nipher Prime，且左侧勾选齿轮工业分类能筛出该条目。

## 常见问题

- **完成接触后依然看不到情报**：`onNewGame()` 漏调 `addIntel(...)`，或者是在未包含该情报实例的旧存档中测试。
    - **解决方案**：确认 `addIntel(new GearInPatrolIntel(), true)` 已写进 `onNewGame()`，并用新档测试。

- **开局新手教程阶段提前露出了情报**：覆写 `isHidden()` 时漏掉了 `super.isHidden()`。
    - **解决方案**：始终先保留 `super.isHidden() || ...` 判定。

- **详情面板显示暂时没有巡逻队**：星系名称写错、巡逻队还未生成，或巡逻队缺少 `$isPatrol` 标记。
    - **解决方案**：确认星系名 `"Nipher"` 与 `MemFlags.MEMORY_KEY_PATROL_FLEET` 一致。

- **点击情报条目瞬间抛出空指针**：某个巡逻队没有指挥官，或当前任务队列恰好为空。
    - **解决方案**：读取 `fleet.getCommander()` 与 `fleet.getCurrentAssignment()` 前做好空值检查。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：默认 UI 回调、`EveryFrameScript` 内置生命周期与 `endAfterDelay()` 状态机。
- `com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivityIntel`：需要主动推进和延迟结束的情报实现。
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：`addIntel(...)`、`forceNoMessage` 与全局情报查询方法。
- `com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI`：读取舰队当前任务类型与行动文本。
- `data/config/settings.json`：`graphics.intel` 下预置的原版情报图标键值。
