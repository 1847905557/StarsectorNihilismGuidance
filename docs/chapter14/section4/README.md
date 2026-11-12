# 管理舰队活动情报

本节为巡逻队建立活动情报，让玩家从情报页面取得位置与剩余日数，地点变化时收到更新，活动结束后保留三日再移除。状态推进与界面显示分别登记，我们用同一份 Intel 对象连接它们，说明更新、结束与清理怎样完成。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 区分 `IntelInfoPlugin` 的界面职责与 `BaseIntelPlugin` 的默认生命周期；
- 解释 `IntelManagerAPI` 的立即登记、通信队列、文本面板渲染和移除路径；
- 将同一个 Intel 对象分别登记到 Intel 列表和生涯脚本列表；
- 实现地图位置、列表标签、剩余时间、更新消息和结束延迟；
- 根据 Intel 列表、脚本列表、日志和 UI 状态定位生命周期故障。

## 前置知识

- [C14-S1] 理解 `EveryFrameScript` 的逐帧推进、暂停条件和 `isDone()` 清理；
- [C14-S2] 理解生涯事件对象的保存边界和回调生命周期；
- [C14-S3] 理解 Campaign 对象、Memory 和稳定 ID 的选择与观察方式；
- [C11-S1] 能够创建、投放和移动一支生涯舰队；
- 能够阅读 Java 继承、覆盖方法和集合接口；
- 能够从 `starsector.log` 和游戏 Intel 界面核对稳定 ID、状态和日志。

## 让巡逻队拥有一份活动情报

创建 `src/data/scripts/gearin/campaign/GearInFleetActivityIntel.java`，继承 `BaseIntelPlugin`。在 [C11-S1] 的 `GearInFleetGenerator` 添加该类的 import，将以下局部调用放到基础舰队已经投放之后，使用当前 `generatedFleet`：

```java
generatedFleet.getMemoryWithoutUpdate().set(GearInFleetActivityIntel.TARGET_MEMORY_KEY, true);
GearInFleetActivityIntel intel = new GearInFleetActivityIntel(generatedFleet);
Global.getSector().getIntelManager().addIntel(intel);
Global.getSector().addScript(intel);
```

**同一个对象登记到情报列表与脚本列表。** `addIntel(...)` 让条目立即可见，设置可见时间戳并按默认规则发送消息；`addScript(...)` 让它按帧推进。`IntelInfoPlugin` 提供界面职责，`BaseIntelPlugin` 还实现了 `EveryFrameScript`，但创建方仍分别登记这两个入口。

新档生成只执行一次，Intel 保存这支舰队的引用，随普通脚本与情报对象进入存档。读档继续使用原条目；重新创建另一份同名实例，会成为另一条独立情报。

目标 Memory 标记供其他功能查询使用，局部写入如下，当前 Intel 则直接跟随构造器传入的舰队：

```java
generatedFleet.getMemoryWithoutUpdate().set(
        GearInFleetActivityIntel.TARGET_MEMORY_KEY, true);
```

## 推进活动并显示当前位置

基类的 `advance(...)` 处理默认结束状态，正常活动阶段再调用子类 `advanceImpl(...)`。本节在这个方法里用生涯时钟累计 `elapsedDays`，保存上次地点 ID，并观察舰队是否仍然有效。

界面从同一引用取得舰队 ID 与地点。`createIntelInfo(...)` 提供列表行，也用于消息和地图提示；`ListInfoMode` 区分 `INTEL`、`MESSAGES`、`MAP_TOOLTIP` 与 `IN_DESC`。详情采用基础小面板，并通过 `hasLargeDescription()` 与 `createLargeDescription(...)` 提供大面板。

`getIcon()` 沿用 `intel/fleet_log` 图标，`getIntelTags(...)` 在父类的重要、新、附近等标签后追加 `Tags.INTEL_FLEET_LOG`。`getSortString()` 返回稳定标题，供同层级文本排序使用。

### 地图位置与剩余比例

`getMapLocation(...)` 返回有效舰队，地图标记随其移动；舰队失效时返回 `null`，情报仍可继续显示结束状态。`setPostingLocation(fleet)` 另用于通信可见性，当前目标可以同时提供地图位置与消息发布位置。

`getTimeRemainingFraction()` 将剩余日数转换为 0 到 1 的比例，进入 ending 后返回 0。实际到期仍由活动逻辑判定，显示进度环取得这个结果，并没有替代计时。

::: warning 标签方法会收到空地图参数
列表排序与部分 UI 路径传入 `map == null`。本例先调用父类取得基础标签，再追加业务标签，自有实现使用地图前也要满足相应调用条件。
:::

## 处理位置变化和更新消息

舰队进入另一个地点时，先保存新 ID，再调用 `sendUpdateIfPlayerHasIntel(...)`。相同地点继续推进，ID 没有改变，也就不重复发送。以下局部片段把 `moved to <locationId>` 作为更新上下文传入：

```java
if (locationId != null && !locationId.equals(lastLocationId)) {
    lastLocationId = locationId;
    sendUpdateIfPlayerHasIntel("moved to " + locationId, false);
}
```

这个发送方法先确认可见时间戳，按 `onlyIfImportant` 与隐藏状态判断是否发消息，再将参数临时保存到 `listInfoParam`，调用 UI，返回后清空。在 `createIntelInfo(...)` 中读取这份参数，让本次消息显示移动或结束的具体原因：

```java
info.addPara(getName(), getTitleColor(mode), 0f);
Object update = getListInfoParam();
if (update != null) {
    info.addPara((String) update, 3f);
    return;
}
```

本例发送的参数都是字符串。更新消息显示标题和这次文字，常驻列表没有更新参数，继续显示舰队 ID、地点与剩余时间。**发送参数和绘制参数要接在一起，消息才会说明这次发生了什么。**

`sendUpdate(param, textPanel)` 则在当前文本面板渲染 Intel 行，参数仍只服务本次调用。可见消息、常驻条目与地图位置各从对应方法取得内容，位置变化时也各自对应新的舰队地点。

## 活动结束后保留三日

舰队失效或活动累计到 30 日时，调用 `endAfterDelay()`。下面的局部片段省去位置更新，只保留计时与结束条件：

```java
@Override
protected void advanceImpl(float amount) {
    elapsedDays += Global.getSector().getClock().convertToDays(amount);
    if (!fleet.isAlive() || elapsedDays >= ACTIVITY_DAYS) {
        endAfterDelay();
    }
}
```

完整类用 `finish(reason)` 发送一次更新再进入结束延迟。默认延迟 3 生涯日，第一次进入 ending 时调用 `notifyEnding()`，期限结束后调用 `notifyEnded()`。

**进入 ending 后，基类停止调用 `advanceImpl()`，只推进结束期限。** 当前活动计时与位置更新随之停止，界面显示结束状态，剩余比例为 0。以下局部读取比较刚开始延迟时的状态：

```java
intel.endAfterDelay();
boolean ending = intel.isEnding();
boolean scriptDone = intel.isDone();
boolean removeFromIntel = intel.shouldRemoveIntel();
```

已公开条目此时 `ending=true`，后两项为 `false`。三日后 `ended=true`，脚本列表按 `isDone()` 移除，Intel 管理器按 `shouldRemoveIntel()` 移除。需要即时结束时，`endImmediately()` 相当于 `endAfterDelay(0f)`。

::: info 尚未公开的情报提前退出队列
没有玩家可见时间戳的条目进入 ending 后，`shouldRemoveIntel()` 就返回 `true`。通信队列会清理这份过期活动，已公开条目则按结束延迟保留。
:::

## 选择立即发布、通信队列或面板显示

本例立即发布，另一处持有 `fleet` 的创建上下文可以用以下局部写法完成同样的双登记：

```java
GearInFleetActivityIntel intel = new GearInFleetActivityIntel(fleet);
Global.getSector().getIntelManager().addIntel(intel);
Global.getSector().addScript(intel);
```

这也用于原版 `DeliveryMissionIntel` 等需要持续推进的情报。`addIntel()` 处理可见列表与首次消息，`addScript()` 处理时间；读档后的引用和累计字段由同一对象保留。

需要等待通信条件时，将发布改为 `queueIntel(...)`，脚本仍按需要登记。以下局部片段读取当前通信可见性：

```java
Global.getSector().getIntelManager().queueIntel(intel);
boolean visible = intel.canMakeVisibleToPlayer(
        Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay());
```

队列在未暂停的推进中检查 `canMakeVisibleToPlayer(...)` 或 `forceAddNextFrame()`，成立后转入可见列表。发布位置、发布范围与玩家中继条件共同参与判断，`addIntel()` 则直接绕过这层通信检查。

::: warning 通信队列的期限参数没有自动超时作用
当前 `queueIntel(plugin, maxCommQueueDays)` 保留参数，但队列仍按可见性与 `shouldRemoveIntel()` 处理。本节活动由自己的三十日期限结束，等待时间也纳入条目生命周期。
:::

`addIntelToTextPanel(...)` 只把当前行渲染到对话面板并播放消息声音，长期列表另行登记。`removeIntel(...)` 同时处理可见列表与通信队列，成功移除后调用 `reportRemovedIntel()`；脚本推进仍由自己的登记负责。

### IntelManagerAPI 方法速查表

| 方法                                          | 当前作用                                                                                 |
| --------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `addIntel(plugin)`                            | 进入可见列表，设置时间戳并按默认规则发送消息                                             |
| `addIntel(plugin, forceNoMessage, textPanel)` | `forceNoMessage` 为 `false` 时，非空面板渲染条目；为 `true` 时同时抑制这次面板显示与消息 |
| `queueIntel(plugin)`                          | 进入通信队列，等到可见或条目要求移除                                                     |
| `queueIntel(plugin, maxCommQueueDays)`        | 当前按前述队列规则处理，期限参数不自动移除                                               |
| `unqueueIntel(plugin)`                        | 仅从通信队列移除，返回是否找到实例                                                       |
| `removeIntel(plugin)`                         | 处理可见与排队对象，并报告已移除                                                         |
| `hasIntel()` / `hasIntelQueued()`             | 分别查询可见列表与通信队列的对象                                                         |
| `getIntel()` / `getCommQueue()`               | 取得对应容器中的列表，并支持按类查询                                                     |
| `addIntelToTextPanel()`                       | 仅渲染当前文本面板                                                                       |

### Intel UI 职责速查表

| UI 职责                                                                   | 当前用途                                          |
| ------------------------------------------------------------------------- | ------------------------------------------------- |
| `getName()`、`getIcon()`                                                  | 提供稳定标题与 40×40 图标路径                     |
| `createIntelInfo()`                                                       | 为列表、消息与地图提示生成紧凑信息                |
| `hasSmallDescription()`、`createSmallDescription()`                       | 控制并生成常规详情                                |
| `hasLargeDescription()`、`createLargeDescription()`                       | 控制并生成自定义大面板                            |
| `getIntelTags(map)`                                                       | 提供重要、新、附近与业务标签，处理空地图参数      |
| `getMapLocation(map)`、`getArrowData(map)`                                | 提供地图锚点与可选连线，目标失效时锚点返回 `null` |
| `isImportant()`、`setImportant()`                                         | 控制重要标签与按钮状态                            |
| `isNew()`、`setNew()`、`reportPlayerClickedOn()`                          | 管理新标记，点击后基类清除新状态                  |
| `isHidden()`、`setHidden()`                                               | 控制已知条目的显示与普通更新消息                  |
| `getSortTier()`、`getSortString()`                                        | 提供层级与文本排序，ending 条目进入完成层级       |
| `getTimeRemainingFraction()`                                              | 提供 0 到 1 的剩余比例                            |
| `autoAddCampaignMessage()`、`getCommMessageSound()`                       | 提供自动消息规则与声音                            |
| `canMakeVisibleToPlayer()`、`setPostingLocation()`、`setPostingRangeLY()` | 判断通信队列中的条目何时可见                      |

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
        Object update = getListInfoParam();
        if (update != null) {
            info.addPara((String) update, 3f);
            return;
        }
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
3. 核对已登记的活动对象在情报列表与脚本列表各有一份，舰队 ID 与生成器投放的巡逻队相同。
4. 让生涯时间推进，比较剩余日数，确认同一对象也在普通脚本列表推进。
5. 检查 Intel 图标、舰队标签、列表文本、地图位置和左下消息。
6. 让舰队跨地点移动，确认出现一次位置更新，消息正文为 `moved to <locationId>`。
7. 保存并读档，确认累计天数、舰队引用和上次地点 ID 继续。
8. 通过 [C14-S2] 的实际消失入口结束测试舰队，查看情报的 ending 状态；另一轮让活动自然达到三十日，比较到期结束。
9. 在结束延迟期间检查 `isEnding()`、剩余比例和 Intel 列表。
10. 等待默认 3 个游戏日，确认脚本引擎依据 `isDone()` 清理对象。
11. 确认 Intel 管理器依据 `shouldRemoveIntel()` 清理同一对象。
12. 对照 `queueIntel()`、`addIntelToTextPanel()`、`removeIntel()` 和重复 `addIntel()` 验证登记边界。

预期结果：

- `getIntel()` 与 `getScripts()` 分别包含同一个 `GearInFleetActivityIntel` 实例；
- Intel 页面显示 `intel/fleet_log` 图标、舰队 ID、地点、剩余天数和舰队日志标签；
- 舰队每次跨越地点发送一次 `moved to <locationId>` 更新，消息中的地点与地图位置随目标变化；失效和到期分别显示 `fleet unavailable` 与 `activity expired`；
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

活动情报持有巡逻队引用，脚本累计生涯日，列表与地图显示位置和状态，地点变化时发送更新。失效或三十日到期后，活动逻辑停止，已公开条目保留三日结束状态，再由两份登记各自清理。

立即发布、通信队列与面板渲染各有入口。发布条件与实际推进分别完成，同一对象的状态才能同时体现在计时、列表与消息中。

## 建议参考项

- `com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`：Intel UI、可见性、排序和结束职责；
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：立即登记、通信队列、文本面板渲染和移除；
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：Intel 与 `EveryFrameScript` 的默认实现；
- `com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel`：舰队日志图标、标签和地图定位的原版实现。
