# 管理舰队活动情报

专用巡逻队已经有了事件日志，这次把活动状态放到玩家能打开的情报页面。Intel 跟随这支舰队的位置，显示剩余日数，地点变化时发送消息，活动结束后先保留结束状态，再延迟清理。情报列表与逐帧脚本分别登记，同一个对象才能既显示又继续推进。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 区分 `IntelInfoPlugin` 的界面职责与 `BaseIntelPlugin` 的默认生命周期；
- 解释 `IntelManagerAPI` 的立即登记、通信队列、文本面板渲染和移除路径；
- 将同一个 Intel 对象分别登记到 Intel 列表和生涯脚本列表；
- 实现地图位置、列表标签、剩余时间、更新消息和结束延迟；
- 根据 Intel 列表、脚本列表、日志和 UI 状态定位生命周期故障。

## 前置知识

- [C14-S1] 理解 `EveryFrameScript` 的逐帧推进、暂停门控和 `isDone()` 清理；
- [C14-S2] 理解生涯事件对象的保存边界和回调生命周期；
- [C14-S3] 理解 Campaign 对象、Memory 和稳定 ID 的选择与观察方式；
- [C11-S1] 能够创建、投放和移动一支生涯舰队；
- 能够阅读 Java 继承、覆盖方法和集合接口；
- 能够从 `starsector.log` 和游戏 Intel 界面核对稳定 ID、状态和日志。

## 把同一对象登记到情报与脚本

创建 `src/data/scripts/gearin/campaign/GearInFleetActivityIntel.java`。在 [C11-S1] 的 `GearInFleetGenerator` 添加该类的 import，把下面的局部片段放到基础舰队已经投放之后，当前使用 `generatedFleet`：

```java
generatedFleet.getMemoryWithoutUpdate().set(GearInFleetActivityIntel.TARGET_MEMORY_KEY, true);
GearInFleetActivityIntel intel = new GearInFleetActivityIntel(generatedFleet);
Global.getSector().getIntelManager().addIntel(intel);
Global.getSector().addScript(intel);
```

这支舰队沿新档入口只生成一次，Intel 保存它的引用并继续跟随。**一份对象分别登记到情报列表和脚本列表。** 前者提供界面，后者提供运行。

### 界面与推进各自负责什么

`IntelInfoPlugin` 描述玩家看到的条目。`getName()`、`getIcon()`、`createIntelInfo()`、`createSmallDescription()` 和 `createLargeDescription()` 决定标题、图标、列表行和详情面板；`getIntelTags(SectorMapAPI map)` 决定筛选和地图相关标签；`getMapLocation()` 提供地图定位；`getTimeRemainingFraction()` 提供倒计时环或进度显示。接口还包含重要性、新标记、排序、按钮和可见性等 UI 状态。

`BaseIntelPlugin` 将这份 UI 职责与 `EveryFrameScript` 合并。它提供结束状态字段、通信可见性、更新消息、默认声音和逐帧脚本方法。继承类通常只覆盖任务状态变化和显示内容，登记仍由创建方明确完成。这样可以看出对象究竟存在于哪个容器，而不是把 Intel 页面上的一行文字误认为完整生命周期。

## 选择登记入口

`IntelManagerAPI.addIntel(plugin)` 立即把对象放入可见 Intel 列表，设置玩家可见时间戳，并按 `autoAddCampaignMessage()` 发送左下角消息。对象身份参与查重，同一个实例重复调用不会得到第二行。`removeIntel(plugin)` 会同时尝试从可见列表和通信队列移除对象，然后调用 `reportRemovedIntel()`。

`queueIntel(plugin)` 只放入通信队列。Intel 管理器在未暂停的推进中检查 `canMakeVisibleToPlayer(playerInRange)` 或 `forceAddNextFrame()`；条件成立后才转入可见列表。`addIntelToTextPanel(plugin, textPanel)` 直接把一条 Intel 渲染到当前文本面板，它不会代替长期登记。

### IntelManagerAPI 方法速查表

| `IntelManagerAPI` 方法 | 对象状态与观察位置 |
| --- | --- |
| `addIntel(plugin)` | 立即进入可见列表，设置玩家可见时间戳并允许发送左下消息 |
| `addIntel(plugin, forceNoMessage, textPanel)` | `forceNoMessage` 抑制消息；非空 `textPanel` 将新条目同时渲染到该面板 |
| `queueIntel(plugin)` | 进入通信队列，等待可见性成立或 `shouldRemoveIntel()` 要求清理 |
| `queueIntel(plugin, maxCommQueueDays)` | 当前等待期限参数不产生自动超时；结束仍按条目状态处理 |
| `unqueueIntel(plugin)` | 只从通信队列移除指定实例，并返回是否找到对象 |
| `removeIntel(plugin)` | 同时检查可见列表和通信队列，成功移除后调用 `reportRemovedIntel()` |
| `hasIntel()`、`hasIntelQueued()` | 分别查询可见列表和通信队列中的对象身份 |
| `getIntel()`、`getCommQueue()` | 返回对应容器中的对象列表，可按类过滤 |
| `addIntelToTextPanel()` | 生成当前文本面板的 Intel 行并播放消息声音，不创建长期登记 |

```java
GearInFleetActivityIntel intel = new GearInFleetActivityIntel(fleet);
Global.getSector().getIntelManager().addIntel(intel);
Global.getSector().addScript(intel);
```

这里有两个独立写入。`addIntel()` 管理 Intel 列表、时间戳和 UI 消息；`addScript()` 管理 `advance(float amount)`。只做前者时，条目会显示，但 `elapsedDays` 不会增加；只做后者时，状态会推进，但玩家没有可见的 Intel 条目。官方任务类也采用这类双登记，例如 `DeliveryMissionIntel` 先加入 Intel，再加入生涯脚本列表。

通信队列适合等待通讯范围，不适合作为立即可见示例的隐藏替代。它还会调用 `canMakeVisibleToPlayer()`，而 `addIntel()` 绕过这层可见性检查。两条入口的验证结果应分别观察 `getIntel()` 和 `getCommQueue()`。

```java
Global.getSector().getIntelManager().queueIntel(intel);
boolean visible = intel.canMakeVisibleToPlayer(
        Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay());
```

## 让舰队成为目标

创建方把具体舰队传入 Intel，Memory 标记另用于其他功能查询这支目标。当前 Intel 自己按传入引用跟随，标记写入与构造调用各自表达作用：

```java
generatedFleet.getMemoryWithoutUpdate().set(
        GearInFleetActivityIntel.TARGET_MEMORY_KEY, true);
```

创建方随后把这支舰队传给 Intel。`setPostingLocation(fleet)` 影响通讯可见性；`getMapLocation()` 影响地图定位。两者可以返回同一个舰队，也可以在对象失效后分别降级。舰队失效后，地图方法返回 `null`，条目继续显示结束状态直到清理。

## 推进与结束状态

生涯引擎 先调用脚本的 `isDone()`，再根据暂停状态调用 `advance()`；调用后还会再次检查 `isDone()`。`BaseIntelPlugin.advance()` 自己先处理 `isEnded()`，把帧时间换算成游戏日；处于 ending 时只扣减 `endingTimeRemaining`，不会再调用 `advanceImpl()`。只有活动仍在运行时，子类的 `advanceImpl(amount)` 才负责业务状态。

完整类累计 `Global.getSector().getClock().convertToDays(amount)` 的结果。舰队的 `isAlive()` 变为 `false` 时，或累计达到 `30.0f` 游戏日时，调用 `endAfterDelay()`。默认结束延迟是 `3.0f` 游戏日：对象先进入 `isEnding()`，结束延迟归零后才进入 `isEnded()`。脚本引擎随后依据 `isDone()` 把对象从脚本列表移除，Intel 管理器在 `shouldRemoveIntel()` 返回 `true` 后把它从 Intel 列表移除。

```java
@Override
protected void advanceImpl(float amount) {
    elapsedDays += Global.getSector().getClock().convertToDays(amount);
    if (!fleet.isAlive() || elapsedDays >= ACTIVITY_DAYS) {
        endAfterDelay();
    }
}
```

`endImmediately()` 只是 `endAfterDelay(0f)` 的快捷入口。`endAfterDelay()` 触发一次 `notifyEnding()`，倒计时完成时触发 `notifyEnded()`。`shouldRemoveIntel()` 对尚未向玩家公开、但已经 ending 的对象立即返回 `true`；对已经结束的对象也返回 `true`。因此排队 Intel 和已公开 Intel 的移除时机会不同，验证时应分别记录。

```java
intel.endAfterDelay();
boolean ending = intel.isEnding();
boolean scriptDone = intel.isDone();
boolean removeFromIntel = intel.shouldRemoveIntel();
```

刚调用默认延迟结束时，已公开 Intel 的 `ending` 为 `true`，`scriptDone` 和 `removeFromIntel` 仍为 `false`。三个游戏日后，基类将 `ended` 置为 `true`，后两个判断才共同进入清理条件。

## 把状态投影到 Intel 界面

列表行由 `createIntelInfo()` 生成。它接收 `ListInfoMode`，可以依据 `INTEL`、`MESSAGES`、`MAP_TOOLTIP` 和 `IN_DESC` 调整文本颜色或密度。详情面板由 `createSmallDescription()` 和 `createLargeDescription()` 生成；小面板承担快速状态，大片面板承担上下文和完整说明。`getIcon()` 返回现有的 `intel/fleet_log` 图标，避免引入二进制资源。

`getIntelTags()` 先调用父类取得基础标签。父类会根据重要性、新状态和地图范围加入基础标签；完整类再加入 `Tags.INTEL_FLEET_LOG`。列表排序等路径可以传入 `map == null`，当前沿用父类处理并追加业务标签。`getSortString()` 返回稳定标题，使 Intel 管理器在相同排序层级下能得到确定的文本比较结果。

`getTimeRemainingFraction()` 返回 0 到 1 之间的剩余比例。活动运行时它随 `elapsedDays` 下降；进入 ending 后返回 0。这个值只描述显示状态，真正的结束判定仍由 `advanceImpl()` 和 `endAfterDelay()` 负责，UI 进度环不会替代脚本状态机。

### Intel UI 职责速查表

| UI 职责 | 当前用途 |
| --- | --- |
| `getName()`、`getIcon()` | 提供稳定标题与 40×40 图标路径 |
| `createIntelInfo()` | 为 Intel 列表、消息和地图提示生成紧凑信息 |
| `hasSmallDescription()`、`createSmallDescription()` | 控制并生成常规详情内容 |
| `hasLargeDescription()`、`createLargeDescription()` | 控制并生成自定义大详情面板 |
| `getIntelTags(map)` | 提供重要、新、附近和业务标签，必须接受空地图参数 |
| `getMapLocation(map)`、`getArrowData(map)` | 提供地图锚点与可选连线；目标失效时锚点返回 `null` |
| `isImportant()`、`setImportant()` | 控制重要标签与重要性按钮状态 |
| `isNew()`、`setNew()`、`reportPlayerClickedOn()` | 管理新条目标记；点击后基类清除新状态 |
| `isHidden()`、`setHidden()` | 控制已知条目是否显示以及是否发送普通更新 |
| `getSortTier()`、`getSortString()` | 决定排序层级和同层文本顺序；ending 条目进入完成层级 |
| `getTimeRemainingFraction()` | 向界面提供 0 到 1 的剩余比例 |
| `autoAddCampaignMessage()`、`getCommMessageSound()` | 控制自动消息与首次发布、更新时的声音 |
| `canMakeVisibleToPlayer()`、`setPostingLocation()`、`setPostingRangeLY()` | 共同决定通信队列中的条目何时可以被玩家收到 |

## 处理位置变化和更新消息

`BaseIntelPlugin` 使用 `listInfoParam` 暂存一次更新的上下文。`sendUpdateIfPlayerHasIntel(param, onlyIfImportant)` 先确认 Intel 已经有玩家可见时间戳，再调用 `CampaignUIAPI.addMessage()` 发送可点击的 Intel 消息。`sendUpdate(param, textPanel)` 则通过 `addIntelToTextPanel()` 直接渲染到当前对话面板。

完整类保存上一次 `LocationAPI.getId()`。舰队进入另一个地点时更新这个字段，并推送 `moved to <locationId>`；舰队消失或 30 日到期时推送结束原因。消息参数只在当前调用期间有效，基类会在发送后清除 `listInfoParam`，因此下一次列表渲染不会误把旧原因显示成当前状态。

```java
if (locationId != null && !locationId.equals(lastLocationId)) {
    lastLocationId = locationId;
    sendUpdateIfPlayerHasIntel("moved to " + locationId, false);
}
```

重复推进同一地点不会重复发送消息，因为位置 ID 没有变化。舰队跨地点后，Intel 列表、左下消息和地图位置应指向同一个新地点；只更新文字而不更新 `getMapLocation()` 会留下旧地图标记。

## 完整最小例

保存下列类，当前舰队引用、累计日数与上次地点 ID 随普通脚本保存，登记采用前面的两处入口。

::: details 完整成品：GearInFleetActivityIntel

```java
package data.scripts.gearin.campaign;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInFleetActivityIntel extends BaseIntelPlugin {
    public static final String TARGET_MEMORY_KEY = "$GearIn_intel_target";
    public static final float ACTIVITY_DAYS = 30.0f;

    private CampaignFleetAPI fleet;
    private float elapsedDays;
    private String lastLocationId;

    public GearInFleetActivityIntel(CampaignFleetAPI fleet) {
        this.fleet = fleet;
        setPostingLocation(fleet);
        lastLocationId = getLocationId(fleet.getContainingLocation());
        setImportant(true);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (!fleet.isAlive()) {
            finish("fleet unavailable");
            return;
        }

        elapsedDays += Global.getSector().getClock().convertToDays(amount);

        LocationAPI location = fleet.getContainingLocation();
        String locationId = getLocationId(location);
        if (locationId != null && !locationId.equals(lastLocationId)) {
            lastLocationId = locationId;
            sendUpdateIfPlayerHasIntel("moved to " + locationId, false);
        }

        if (elapsedDays >= ACTIVITY_DAYS) {
            finish("activity expired");
        }
    }

    private void finish(String reason) {
        sendUpdateIfPlayerHasIntel(reason, false);
        endAfterDelay();
    }

    private String getLocationId(LocationAPI location) {
        return location == null ? null : location.getId();
    }

    @Override
    public String getName() {
        return "GearIn fleet activity";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "fleet_log");
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>(super.getIntelTags(map));
        tags.add(Tags.INTEL_FLEET_LOG);
        return tags;
    }

    @Override
    public CampaignFleetAPI getMapLocation(SectorMapAPI map) {
        return fleet.isAlive() ? fleet : null;
    }

    @Override
    public float getTimeRemainingFraction() {
        if (isEnding() || isEnded()) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, 1f - elapsedDays / ACTIVITY_DAYS));
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        info.addPara("Fleet ID: " + getFleetId(), 3f);
        info.addPara("Location: " + getLocationText(), 3f);
        info.addPara(getStatusText(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(getStatusText(), 0f);
        info.addPara("Fleet ID: " + getFleetId(), 3f);
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI description = panel.createUIElement(width, height, true);
        description.addPara(getName(), 0f);
        description.addPara("This Intel follows a marked campaign fleet and updates as its location changes.", 10f);
        description.addPara("Fleet ID: " + getFleetId(), 3f);
        description.addPara("Location: " + getLocationText(), 3f);
        description.addPara(getStatusText(), 3f);
        panel.addUIElement(description).inTL(0, 0);
    }

    private String getFleetId() {
        return fleet.getId();
    }

    private String getLocationText() {
        LocationAPI location = fleet.getContainingLocation();
        return location == null ? "unknown" : location.getId();
    }

    private String getStatusText() {
        if (isEnded()) {
            return "Activity ended.";
        }
        if (isEnding()) {
            return "Activity ended; removing this entry after the delay.";
        }
        return "Remaining days: " + Math.max(0, Math.round(ACTIVITY_DAYS - elapsedDays));
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        Global.getLogger(GearInFleetActivityIntel.class).info("GearIn fleet activity is ending: " + getFleetId());
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getLogger(GearInFleetActivityIntel.class).info("GearIn fleet activity ended: " + getFleetId());
    }
}
```

:::

## 验证方案

前置数据：使用一份可以推进 35 个游戏日的测试存档，并准备 [C11-S1] 的舰队生成入口。

1. 保存类与生成器中的两处登记，构建并重启，新建测试存档，等待专用舰队生成。
2. 搜索新情报消息，打开舰队活动条目，核对当前舰队 ID、图标和位置。
3. 创建 `GearInFleetActivityIntel` 并调用 `IntelManagerAPI.addIntel()`。
4. 让生涯时间推进，比较剩余日数，确认同一对象也在普通脚本列表推进。
5. 检查 Intel 图标、舰队标签、列表文本、地图位置和左下消息。
6. 让舰队跨地点移动，确认出现一次位置更新。
7. 保存并读档，确认累计天数、舰队引用和上次地点 ID 继续。
8. 通过 [C14-S2] 的实际消失入口结束测试舰队，查看情报的 ending 状态；另一轮让活动自然达到三十日，比较到期结束。
9. 在结束延迟期间检查 `isEnding()`、剩余比例和 Intel 列表。
10. 等待默认 3 个游戏日，确认脚本引擎依据 `isDone()` 清理对象。
11. 确认 Intel 管理器依据 `shouldRemoveIntel()` 清理同一对象。
12. 对照 `queueIntel()`、`addIntelToTextPanel()`、`removeIntel()` 和重复 `addIntel()` 验证登记边界。

预期结果：

- `getIntel()` 与 `getScripts()` 分别包含同一个 `GearInFleetActivityIntel` 实例；
- Intel 页面显示 `intel/fleet_log` 图标、舰队 ID、地点、剩余天数和舰队日志标签；
- 舰队每次跨越地点只发送一次 `moved to <locationId>` 更新，地图位置随目标变化；
- 保存并读档后，`elapsedDays`、`fleet` 和 `lastLocationId` 延续保存值；
- 舰队失效或累计达到 30 个游戏日后，`isEnding()` 返回 `true`，剩余比例为 `0`；
- 默认 3 个游戏日结束延迟期间，Intel 列表仍显示结束状态；
- 结束延迟归零后，脚本列表和 Intel 列表分别移除该对象；
- `queueIntel()` 将对象留在通信队列直至可见，`addIntelToTextPanel()` 只渲染当前文本面板，`removeIntel()` 同时处理已登记与排队对象。

## 故障分析

- **条目显示而剩余日数保持原值**：只登记了 Intel 列表。
    - **解决方案**：确认创建方同时调用 `Global.getSector().addScript(intel)`。`addIntel()` 只写入 Intel 管理器，不会自动把对象加入生涯脚本列表。

- **地图位置变化而没有更新消息**：玩家可见时间戳或地点变化条件不满足。
    - **解决方案**：确认对象已经通过 `addIntel()` 获得玩家可见时间戳，并且位置 ID 与 `lastLocationId` 的比较只在发生变化时发送更新。

- **列表出现两条相同情报**：创建了两个不同实例。
    - **解决方案**：确认复用同一个 Intel 实例；管理器按对象身份查重，反复创建新实例会产生多条独立记录。

- **舰队失效后条目持续显示**：结束或延迟推进没有接入。
    - **解决方案**：确认 `advanceImpl()` 调用了 `endAfterDelay()`，并等待结束延迟归零；`isEnding()` 期间 `shouldRemoveIntel()` 仍然可以保持条目显示。

- **结束延迟完成后脚本仍在列表**：完成状态与实际结束状态不一致。
    - **解决方案**：确认 `BaseIntelPlugin.advance()` 已进入 ending，并检查引擎是否依据 `isDone()` 在下一轮移除了脚本对象。

- **空地图参数触发异常**：标签实现直接访问了 `map`。
    - **解决方案**：保留父类标签计算并保证实现不直接解引用 `map`；地图参数在列表排序和部分 UI 路径中可以为空。

- **排队后条目尚未显示**：通信可见性还未满足。
    - **解决方案**：检查通信队列中的对象、玩家是否处于中继范围，以及 `canMakeVisibleToPlayer()` 的结果；需要立即可见时使用 `addIntel()`。

## 总结

舰队活动 Intel 从同一引用取得位置与身份，脚本推进剩余日数，界面显示当前状态，位置变化通过可见消息发布。活动结束后进入三日延迟，再由两个容器各自清理。

编者建议先核对登记与推进，再调整面板内容。条目能够显示、时间能够变化、消息与地图能够对应，情报才具备完整的活动过程。

## 建议参考项

- `com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`：Intel UI、可见性、排序和结束职责；
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：立即登记、通信队列、文本面板渲染和移除；
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：Intel 与 `EveryFrameScript` 的默认实现；
- `com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel`：舰队日志图标、标签和地图定位的原版实现。
