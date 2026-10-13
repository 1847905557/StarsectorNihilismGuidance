# 构建阶段任务

检查对话已经可以记录一次操作，这次让它推进一个长期任务。玩家接受后调查 `Foundry`，完成检查再提交报告，获得一次 50000 信用点奖励。任务保存阶段与结果，附属 Intel 读取同一任务，失败、放弃和显示条目的结束也分别处理。

*本节预期阅读时间：\~35 分钟。*

*本节预期操作时间：\~45 分钟。*

## 学习目标

完成后，您将能够：

- 创建并接受一个 `BaseHubMission` 任务；
- 用阶段和条件检查器组织调查、报告和完成路径；
- 处理失败、放弃和一次性 `credits` 奖励；
- 让附属 Intel 读取任务当前阶段；
- 验证保存、读档和任务/Intel 分离后的结果。

## 前置知识

- [C14-S4] 的 `BaseIntelPlugin`、Intel 注册和结束延迟；
- [C15-S1] 的 `SectorEntityToken`、目标 Memory 和生涯入口；
- Java 继承、构造器和布尔状态；
- `MemoryAPI.getBoolean(...)` 与 `Global.getSector()`。

## 让任务对象拥有长期状态

`BaseHubMission` 继承 `BaseIntelPlugin`，因此它可以出现在 Intel 列表并由生涯脚本推进；任务特有的阶段连接、接受、成功、失败和放弃仍由 `BaseHubMission` 管理。创建方先设置任务 ID 和起始阶段，再调用 `createAndAbortIfFailed(...)` 生成任务数据，成功后通过 `accept(...)` 把任务登记到 Intel 管理器和生涯脚本列表。

保存 `GearInHubMission.java` 与 `GearInHubMissionIntel.java` 到 `src/data/scripts/gearin/campaign/`。在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 追加下面的局部入口，目标使用当前市场主实体：

```java
SectorAPI sector = Global.getSector();
if (!sector.getPersistentData().containsKey("GearIn_hub_mission")) {
    GearInHubMission mission = new GearInHubMission(market.getPrimaryEntity());
    mission.createAndAbortIfFailed(market, false);
    mission.accept(null, Collections.emptyMap());
    GearInHubMissionIntel intel = new GearInHubMissionIntel(mission);
    sector.getIntelManager().addIntel(intel);
    sector.getPersistentData().put("GearIn_hub_mission", mission);
}
```

`createAndAbortIfFailed` 先调用子类的 `create(...)`。创建返回 `false` 时，基类执行 `abort()`，调用方按创建结果继续处理；本例传入当前已存在目标，创建成功。之后，`accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，然后调用 `IntelManagerAPI.addIntel(...)` 和 `Global.getSector().addScript(this)`。这两个登记分别负责可见条目和逐帧推进。

### 固定任务身份

构造器把任务 ID、名称和阶段终止入口固定下来。阶段使用枚举值，常量在任务、连接与显示之间传递同一个阶段对象。当前枚举成员保留各阶段名称，**任务交互与更新使用阶段枚举**：

五种阶段在类内声明，当前值使用 `Stage` 类型：

```java
public enum Stage {
    GearIn_stage_investigate,
    GearIn_stage_report,
    GearIn_stage_completed,
    GearIn_stage_failed,
    GearIn_stage_abandoned
}
```

`STAGE_INVESTIGATE` 等常量指向对应枚举成员，代码与显示使用同一个值。构造器再指定起始和终止阶段：

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
    connectWithMemoryFlag(STAGE_INVESTIGATE, STAGE_REPORT, target, TARGET_COMPLETE_KEY);
    return true;
}
```

本例 `connectWithMemoryFlag(...)` 明确连接调查与报告，接收起点、终点、目标和键名。需要所有阶段都共享的条件时，使用 `setStageOnMemoryFlag(...)` 的无起点形式。目标标记仍由外部交互或规则写入；任务只读取它，并在标记为真时把当前阶段推进到报告。**目标事实与任务阶段分别保存。** 本例还让任务管理这一标记的清理，任务结束时由基类解除相应状态。

### 阶段连接的实际结果

任务接受后 `currentStage` 是 `GearIn_stage_investigate`。玩家完成目标后，基类的 `advanceImpl(...)` 周期性调用阶段检查，条件成立时执行 `setCurrentStage(STAGE_REPORT, null, null)`。报告阶段不会自动发放奖励，它只是把任务推进到可以作出最终决定的状态。

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

成功入口由基类统一触发，子类在 `endSuccessImpl(...)` 中加入本任务的 50000 `credits` 奖励。`rewardGranted` 先判断再写入，避免重复进入成功实现时再次增加资金。

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

基类的 `endSuccess(...)` 负责建立 `HubMissionResult`、发放标准奖励、调用 `endSuccessImpl(...)`、发送结束更新并执行 `abort()`。本例只在子类实现中增加固定 `credits`，因此任务逻辑不会重复接管基类的结束流程。

## 让附属 Intel 读取任务

附属 Intel 接收 `GearInHubMission` 引用，每次生成列表摘要或小型描述时调用 `mission.getCurrentStage()`。它不保存 `currentStage` 副本，也不负责调用 `setCurrentStage(...)`。

```java
private String getStageText() {
    return String.valueOf(mission.getCurrentStage());
}
```

当任务从调查进入报告，Intel 下一次刷新显示的阶段文本也随之变化；任务完成、失败或放弃后，Intel 读取到对应终止阶段。阶段的唯一来源仍然是任务对象，Intel 的职责是把结果放进 Intel 列表和描述区域。

### 分开登记 Intel 和脚本

任务接受时，`BaseHubMission.accept(...)` 同时执行 Intel 登记和脚本登记。附属 Intel 若需要逐帧过期或结束逻辑，则由创建方按 [C14-S4] 的方式单独调用 `IntelManagerAPI.addIntel(...)` 与 `Global.getSector().addScript(intel)`；本例的附属 Intel 只显示任务阶段。结束它时使用 `endImmediately()` 并 `removeIntel(intel)`，当前没有额外脚本推进它的延迟。

```java
GearInHubMissionIntel intel = new GearInHubMissionIntel(mission);
Global.getSector().getIntelManager().addIntel(intel);
```

附属条目结束不改变任务阶段，任务结束后它继续从同一对象读取最终阶段。需要移除附属条目时显式调用结束与移除。两个对象分别由自己的结束入口管理，页面和日志可以据此定位是任务状态还是可见条目出了问题。

## 保存、读档和边界

`currentStage`、`rewardGranted`、`missionEnded` 和目标引用属于任务对象字段。任务被 `BaseHubMission` 作为 Intel 和生涯脚本保存后，读档恢复的是同一个阶段状态；Intel 只重新读取任务字段并生成显示内容。

`HubMissionWithBarEvent` 是把任务接入酒吧的专用子类，适合任务本身需要酒吧发现和接受入口的场景。本节使用独立 Java 入口创建普通 `BaseHubMission`，酒吧事件到任务的交接仍归 [C15-S3]；这样可以先观察阶段任务，再单独学习酒吧入口。

### 方法速查表

| 方法 | 责任 | 结果 |
| --- | --- | --- |
| `createAndAbortIfFailed` | 创建任务并处理创建失败 | 成功后可接受 |
| `accept` | 设置起始阶段并登记任务 | Intel 与脚本开始运行 |
| `setStageOnMemoryFlag` | 用目标 Memory 连接阶段 | 标记成立后进入报告 |
| `setCurrentStage` | 改变当前阶段 | 触发阶段更新或终止逻辑 |
| `setSuccessStage` / `setFailureStage` | 绑定终止阶段 | 成功或失败入口生效 |
| `setAbandonStage` | 绑定放弃阶段 | 放弃路径结束任务 |
| `endSuccessImpl` | 添加任务专属成功结果 | 增加一次 50000 `credits` |
| `getCurrentStage` | 供 Intel 读取阶段 | 列表文本与任务一致 |

## 从检查对话进入报告

在 [C15-S1] 的 `GearInConversationDialog` 检查分支、已经设置 `checked = true` 后加入局部片段：

```java
dialog.getInteractionTarget().getMemoryWithoutUpdate().set(
        GearInHubMission.TARGET_COMPLETE_KEY, true);
```

对话打开时生涯通常暂停。**关闭检查对话，让任务推进一次，再查看报告阶段。** 这次不是文字写入后立刻完成任务，任务检查器需要收到生涯推进。

在该对话的 `init(...)` 已建立选项后，读取存档中本节任务，报告阶段显示提交按钮：

```java
GearInHubMission mission = (GearInHubMission) Global.getSector()
        .getPersistentData().get("GearIn_hub_mission");
if (mission.getCurrentStage() == GearInHubMission.STAGE_REPORT) {
    options.addOption("Submit the inspection report", GearInHubMission.STAGE_COMPLETED);
}
```

在 `optionSelected(...)` 的实际业务分支前处理这个阶段对象：

```java
if (GearInHubMission.STAGE_COMPLETED.equals(optionData)) {
    GearInHubMission mission = (GearInHubMission) Global.getSector()
            .getPersistentData().get("GearIn_hub_mission");
    if (mission.getCurrentStage() == GearInHubMission.STAGE_REPORT) {
        mission.setCurrentStage(GearInHubMission.STAGE_COMPLETED, dialog, getMemoryMap());
    }
    return;
}
```

当前使用存档中已接受的任务。提交按钮提供阶段对象，显示文字继续只服务界面；成功实现增加一次奖励，基类安排其余结束过程。

失败与放弃分别调用 `setCurrentStage(STAGE_FAILED, ...)` 与 `setCurrentStage(STAGE_ABANDONED, ...)`。用测试存档的不同分支观察它们，当前阶段和结束结果都从任务读取；两种终止入口也各自保存实际状态。

## 完整最小例

两个类保存到前述路径，任务接受和附属条目采用实际市场入口。

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

    public enum Stage {
        GearIn_stage_investigate,
        GearIn_stage_report,
        GearIn_stage_completed,
        GearIn_stage_failed,
        GearIn_stage_abandoned
    }

    public static final Stage STAGE_INVESTIGATE = Stage.GearIn_stage_investigate;
    public static final Stage STAGE_REPORT = Stage.GearIn_stage_report;
    public static final Stage STAGE_COMPLETED = Stage.GearIn_stage_completed;
    public static final Stage STAGE_FAILED = Stage.GearIn_stage_failed;
    public static final Stage STAGE_ABANDONED = Stage.GearIn_stage_abandoned;
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
        connectWithMemoryFlag(STAGE_INVESTIGATE, STAGE_REPORT, target, TARGET_COMPLETE_KEY);
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
        return Global.getSettings().getSpriteName("intel", "important_accepted_mission");
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
        return String.valueOf(mission.getCurrentStage());
    }
}
```

:::

## 验证方案

前置数据：准备一个可交互目标实体，并能在其 Memory 中写入 `$GearIn_hub_target_complete`；准备一个可以显示 Intel 的新档或测试存档。

1. 保存类与经济观察器入口，构建重启，进入 `Foundry` 存档并推进经济更新，查看任务与附属 Intel。
2. 打开任务条目，确认当前阶段为 `GearIn_stage_investigate`。
3. 在检查对话选择继续，关闭对话让生涯推进，再打开任务条目，确认报告阶段。
4. 重新打开 Intel 条目，确认阶段文本从调查变为报告。
5. 重新打开检查对话，提交报告，比较玩家信用增加 50000 与终止阶段。
6. 重复触发完成路径，确认 `rewardGranted` 阻止第二次奖励。
7. 保存并读档，确认当前阶段、`rewardGranted` 和 `missionEnded` 保持。
8. 在报告阶段触发失败，确认任务显示 `GearIn_stage_failed` 并结束。
9. 在报告阶段触发放弃，确认任务显示 `GearIn_stage_abandoned` 并结束。
10. 分别结束附属 Intel，确认 Intel 消失不会把任务阶段改回调查。

预期结果：

- 任务接受后进入调查阶段；
- 目标 Memory 标记成立后进入报告阶段；
- Intel 文本始终读取任务当前阶段；
- 完成只增加一次 50000 `credits`；
- 保存和读档保留任务字段；
- 失败和放弃使用各自终止阶段；
- 任务与附属 Intel 可以分别结束。

## 故障分析

- **任务没有进入调查阶段**：入口没有先设置 `setStartingStage(...)` 或没有调用 `accept(...)`。
    - **解决方案**：检查构造器和任务接受顺序。

- **目标完成后阶段不变**：`setStageOnMemoryFlag(...)` 读取的目标或键名不一致。
    - **解决方案**：核对目标对象和 `$GearIn_hub_target_complete`。

- **完成奖励重复增加**：成功实现没有使用 `rewardGranted` 门控。
    - **解决方案**：在增加 `credits` 前检查并设置该字段。

- **Intel 显示旧阶段**：Intel 自己保存了阶段副本。
    - **解决方案**：在显示方法中直接读取 `mission.getCurrentStage()`。

## 总结

调查任务从当前市场与目标创建，接受后保存枚举阶段。检查写入目标 Memory，连接条件把任务推进到报告，再由明确终止阶段处理奖励、失败或放弃。

编者建议始终让任务保存阶段、显示对象读取阶段。目标事实、一次性奖励和条目结束各自有归属，保存读档后也能继续从同一个任务对象解释结果。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`：本页 `GearInHubMission` 的基类，提供接受、阶段连接和成功、失败、放弃入口；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMission`：任务行为接口，`BaseHubMission` 按它实现任务契约；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：把任务接入酒吧的专用子类，适合需要酒吧发现和接受入口的场景；
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：`GearInHubMissionIntel` 的基类，任务对象也继承它进入 Intel 列表；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标记忆接口，阶段连接用它检查调查条件；
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：附属 Intel 的登记入口，本页在任务接受后单独调用 `addIntel`。
