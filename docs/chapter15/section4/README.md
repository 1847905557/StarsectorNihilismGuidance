# 构建阶段任务

`BaseHubMission` 把一次接受动作延长为可保存的任务状态。任务对象保存当前阶段、目标和奖励结果，附属 Intel 只把这些状态呈现给玩家；两者通过明确的读取关系协作，而不是各自维护一套阶段。本文用一个需要检查目标实体的任务说明这条边界。

*本节预期阅读时间：~35 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

- 创建并接受一个 `BaseHubMission` 任务；
- 用阶段和条件检查器组织调查、报告和完成路径；
- 处理失败、放弃和一次性 credits 奖励；
- 让附属 Intel 读取任务当前阶段；
- 验证保存、读档和任务/Intel 分离后的结果。

## 前置知识

- [C14-S4] 的 `BaseIntelPlugin`、Intel 注册和结束延迟；
- [C15-S1] 的 `SectorEntityToken`、目标 Memory 和生涯入口；
- Java 继承、构造器和布尔状态；
- `MemoryAPI.getBoolean(...)` 与 `Global.getSector()`。

## 让任务对象拥有长期状态

`BaseHubMission` 继承 `BaseIntelPlugin`，因此它可以出现在 Intel 列表并由生涯脚本推进；任务特有的阶段连接、接受、成功、失败和放弃仍由 `BaseHubMission` 管理。创建方先设置任务 ID 和起始阶段，再调用 `createAndAbortIfFailed(...)` 生成任务数据，成功后通过 `accept(...)` 把任务登记到 Intel 管理器和生涯脚本列表。

独立入口只负责提供任务依赖的市场和目标。它不直接修改当前阶段，也不把目标状态复制到全局 Memory。

```java
GearInHubMission mission = new GearInHubMission(target);
mission.createAndAbortIfFailed(createdAt, false);
mission.accept(dialog, memoryMap);
```

`createAndAbortIfFailed` 先调用子类的 `create(...)`。创建返回 `false` 时，基类执行 `abort()`，任务不会进入 Intel 列表；返回 `true` 后，`accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，然后调用 `IntelManagerAPI.addIntel(...)` 和 `Global.getSector().addScript(this)`。这两个登记分别负责可见条目和逐帧推进。

### 固定任务身份

构造器把任务 ID、名称和阶段终止入口固定下来。阶段 ID 使用字符串常量，能够同时出现在任务对象、条件连接和 Intel 文本中。

```java
public GearInHubMission(SectorEntityToken target) {
    this.target = target;
    setMissionId("GearIn_hub_mission");
    setName("GearIn field inspection");
    setStartingStage(STAGE_INVESTIGATE);
    setSuccessStage(STAGE_COMPLETED);
    setFailureStage(STAGE_FAILED);
    setAbandonStage(STAGE_ABANDONED);
}
```

`setStartingStage` 决定接受后的第一阶段；`setSuccessStage` 和 `setFailureStage` 将阶段 ID 绑定到基类的结束逻辑；`setAbandonStage` 让任务能够沿放弃路径结束。任务名称用于 Intel 列表，任务 ID 用于稳定身份和基类保存的阶段键。

## 用目标条件推进阶段

调查阶段的完成条件来自目标实体自身的 Memory。`create(...)` 把报告阶段绑定到 `$GearIn_hub_target_complete`，基类的条件检查器会在任务推进时读取该标记。

```java
@Override
protected boolean create(MarketAPI createdAt, boolean barEvent) {
    if (target == null) {
        return false;
    }
    setStageOnMemoryFlag(STAGE_REPORT, target, TARGET_COMPLETE_KEY);
    return true;
}
```

`setStageOnMemoryFlag` 接收下一阶段、实现 `HasMemory` 的目标和键名。目标标记仍由外部交互或规则写入；任务只读取它，并在标记为真时把当前阶段推进到报告。任务因此不会把 "目标已完成" 伪装成任务自身字段。

### 阶段连接的实际结果

任务接受后 `currentStage` 是 `GearIn_stage_investigate`。玩家完成目标后，基类的 `advanceImpl(...)` 周期性调用阶段检查，条件成立时执行 `setCurrentStage(GearIn_stage_report, null, null)`。报告阶段不会自动发放奖励，它只是把任务推进到可以作出最终决定的状态。

```text
GearIn_stage_investigate
    → 目标 Memory: $GearIn_hub_target_complete == true
    → GearIn_stage_report
```

阶段数据还记录各阶段已经经过的游戏天数。读档后，基类从任务对象恢复当前阶段和阶段计时，生涯脚本继续推进同一个任务实例。

## 让报告阶段产生结果

完成、失败和放弃都通过目标阶段 ID 进入基类的终止逻辑。设置完成阶段时，`BaseHubMission` 调用 `endSuccess(...)`；设置失败阶段时调用 `endFailure(...)`；设置放弃阶段时调用 `endAbandon()`。子类只覆写实现方法，补充本任务自己的结果字段。

```java
@Override
protected void endFailureImpl(InteractionDialogAPI dialog,
        Map<String, MemoryAPI> memoryMap) {
    missionEnded = true;
}

@Override
protected void endAbandonImpl() {
    missionEnded = true;
}
```

失败和放弃都把 `missionEnded` 设为真，但它们的基类语义不同：失败可以产生声望惩罚，放弃遵循 `canAbandonWithoutPenalty()` 的规则。任务对象只记录最终状态，具体界面文字由任务和 Intel 的显示方法提供。

### 一次性完成奖励

成功入口由基类统一触发，子类在 `endSuccessImpl(...)` 中加入本任务的 50000 credits 奖励。`rewardGranted` 先判断再写入，避免重复进入成功实现时再次增加资金。

```java
@Override
protected void endSuccessImpl(InteractionDialogAPI dialog,
        Map<String, MemoryAPI> memoryMap) {
    if (!rewardGranted) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(50000);
        rewardGranted = true;
    }
    missionEnded = true;
}
```

基类的 `endSuccess(...)` 负责建立 `HubMissionResult`、发放标准奖励、调用 `endSuccessImpl(...)`、发送结束更新并执行 `abort()`。本例只在子类实现中增加固定 credits，因此任务逻辑不会重复接管基类的结束流程。

## 让附属 Intel 读取任务

附属 Intel 接收 `GearInHubMission` 引用，每次生成列表摘要或小型描述时调用 `mission.getCurrentStage()`。它不保存 `currentStage` 副本，也不负责调用 `setCurrentStage(...)`。

```java
private String getStageText() {
    return mission == null ? "unavailable" : String.valueOf(mission.getCurrentStage());
}
```

当任务从调查进入报告，Intel 下一次刷新显示的阶段文本也随之变化；任务完成、失败或放弃后，Intel 读取到对应终止阶段。阶段的唯一来源仍然是任务对象，Intel 的职责是把结果放进 Intel 列表和描述区域。

### 分开登记 Intel 和脚本

任务接受时，`BaseHubMission.accept(...)` 同时执行 Intel 登记和脚本登记。附属 Intel 若需要逐帧过期或结束逻辑，则由创建方按 [C14-S4] 的方式单独调用 `IntelManagerAPI.addIntel(...)` 与 `Global.getSector().addScript(intel)`；本例的附属 Intel 只显示任务阶段，因此不复制任务计时器。

```java
GearInHubMissionIntel intel = new GearInHubMissionIntel(mission);
Global.getSector().getIntelManager().addIntel(intel);
```

Intel 条目结束不会把任务退回调查阶段，任务结束也不会自动替代 Intel 的结束调用。两个对象分别由自己的结束入口管理，页面和日志可以据此定位是任务状态还是可见条目出了问题。

## 保存、读档和边界

`currentStage`、`rewardGranted`、`missionEnded` 和目标引用属于任务对象字段。任务被 `BaseHubMission` 作为 Intel 和生涯脚本保存后，读档恢复的是同一个阶段状态；Intel 只重新读取任务字段并生成显示内容。

`HubMissionWithBarEvent` 是把任务接入酒吧的专用子类，适合任务本身需要酒吧发现和接受入口的场景。本节使用独立 Java 入口创建普通 `BaseHubMission`，酒吧事件到任务的交接仍归 C15-S3；这样可以先观察阶段任务，再单独学习酒吧入口。

### 方法索引

| 方法 | 责任 | 结果 |
| --- | --- | --- |
| `createAndAbortIfFailed` | 创建任务并处理创建失败 | 成功后可接受 |
| `accept` | 设置起始阶段并登记任务 | Intel 与脚本开始运行 |
| `setStageOnMemoryFlag` | 用目标 Memory 连接阶段 | 标记成立后进入报告 |
| `setCurrentStage` | 改变当前阶段 | 触发阶段更新或终止逻辑 |
| `setSuccessStage` / `setFailureStage` | 绑定终止阶段 | 成功或失败入口生效 |
| `setAbandonStage` | 绑定放弃阶段 | 放弃路径结束任务 |
| `endSuccessImpl` | 添加任务专属成功结果 | 增加一次 50000 credits |
| `getCurrentStage` | 供 Intel 读取阶段 | 列表文本与任务一致 |

## 完整最小例

两个类分别承担任务状态和可见展示。任务类保存阶段与奖励字段，Intel 类只通过任务引用读取当前阶段。

::: details 完整成品：GearInHubMission

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;

public class GearInHubMission extends BaseHubMission {

    public static final String STAGE_INVESTIGATE = "GearIn_stage_investigate";
    public static final String STAGE_REPORT = "GearIn_stage_report";
    public static final String STAGE_COMPLETED = "GearIn_stage_completed";
    public static final String STAGE_FAILED = "GearIn_stage_failed";
    public static final String STAGE_ABANDONED = "GearIn_stage_abandoned";
    public static final String TARGET_COMPLETE_KEY = "$GearIn_hub_target_complete";

    private SectorEntityToken target;
    private boolean rewardGranted;
    private boolean missionEnded;

    public GearInHubMission(SectorEntityToken target) {
        this.target = target;
        setMissionId("GearIn_hub_mission");
        setName("GearIn field inspection");
        setStartingStage(STAGE_INVESTIGATE);
        setSuccessStage(STAGE_COMPLETED);
        setFailureStage(STAGE_FAILED);
        setAbandonStage(STAGE_ABANDONED);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (target == null) {
            return false;
        }
        setStageOnMemoryFlag(STAGE_REPORT, target, TARGET_COMPLETE_KEY);
        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The inspection task is now active.");
        }
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        if (!rewardGranted) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(50000);
            rewardGranted = true;
        }
        missionEnded = true;
    }

    @Override
    protected void endFailureImpl(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        missionEnded = true;
    }

    @Override
    protected void endAbandonImpl() {
        missionEnded = true;
    }

    public boolean isRewardGranted() {
        return rewardGranted;
    }

    public boolean isMissionEnded() {
        return missionEnded;
    }
}
```

:::

::: details 完整成品：GearInHubMissionIntel

```java
package data.scripts.gearin.campaign;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInHubMissionIntel extends BaseIntelPlugin {

    private GearInHubMission mission;

    public GearInHubMissionIntel(GearInHubMission mission) {
        this.mission = mission;
    }

    @Override
    public String getName() {
        return "GearIn inspection task";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "mission");
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>(super.getIntelTags(map));
        tags.add(Tags.INTEL_MISSIONS);
        return tags;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        info.addPara("Current stage: " + getStageText(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("Current stage: " + getStageText(), 0f);
    }

    private String getStageText() {
        return mission == null ? "unavailable" : String.valueOf(mission.getCurrentStage());
    }
}
```

:::

## 验证方案

前置数据：准备一个可交互目标实体，并能在其 Memory 中写入 `$GearIn_hub_target_complete`；准备一个可以显示 Intel 的新档或测试存档。

1. 通过独立 Java 入口创建任务并调用 `accept(...)`，确认 Intel 列表出现 "GearIn inspection task"；
2. 打开任务条目，确认当前阶段为 `GearIn_stage_investigate`；
3. 在目标实体 Memory 写入 `$GearIn_hub_target_complete = true`，推进生涯时间，确认任务进入 `GearIn_stage_report`；
4. 重新打开 Intel 条目，确认阶段文本从调查变为报告；
5. 将任务设置为完成阶段，确认 credits 增加 50000 且任务进入结束状态；
6. 重复触发完成路径，确认 `rewardGranted` 阻止第二次奖励；
7. 保存并读档，确认当前阶段、`rewardGranted` 和 `missionEnded` 保持；
8. 在报告阶段触发失败，确认任务显示 `GearIn_stage_failed` 并结束；
9. 在报告阶段触发放弃，确认任务显示 `GearIn_stage_abandoned` 并结束；
10. 分别结束附属 Intel，确认 Intel 消失不会把任务阶段改回调查。

预期结果：

- 任务接受后进入调查阶段；
- 目标 Memory 标记成立后进入报告阶段；
- Intel 文本始终读取任务当前阶段；
- 完成只增加一次 50000 credits；
- 保存和读档保留任务字段；
- 失败和放弃使用各自终止阶段；
- 任务与附属 Intel 可以分别结束。

## 故障分析

- **任务没有进入调查阶段**：入口没有先设置 `setStartingStage(...)` 或没有调用 `accept(...)`。
    - **解决方案**：检查构造器和任务接受顺序。
- **目标完成后阶段不变**：`setStageOnMemoryFlag(...)` 读取的目标或键名不一致。
    - **解决方案**：核对目标对象和 `$GearIn_hub_target_complete`。
- **完成奖励重复增加**：成功实现没有使用 `rewardGranted` 门控。
    - **解决方案**：在增加 credits 前检查并设置该字段。
- **Intel 显示旧阶段**：Intel 自己保存了阶段副本。
    - **解决方案**：在显示方法中直接读取 `mission.getCurrentStage()`。

## 总结

`BaseHubMission` 把接受、阶段连接、条件检查、成功/失败/放弃和保存恢复放进任务对象。目标 Memory 只提供调查条件，任务字段保存当前阶段和奖励门控，附属 Intel 读取任务阶段并负责显示。完成、失败和放弃由不同终止阶段表达，任务与 Intel 的结束入口分别执行。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMission`；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`；
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`；
- [C14-S4] 的 Intel 登记与结束延迟。
