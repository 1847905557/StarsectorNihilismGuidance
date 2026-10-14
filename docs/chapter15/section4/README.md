# 构建阶段任务

本节把 Foundry 检查连接到长期任务：接受后调查目标，完成检查再提交报告，获得 50000 信用点。对话结束以后任务继续存在，阶段、奖励与终止结果都由任务保存；附属情报读取它的当前阶段，用来比较任务状态与显示对象的职责。

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

## 从已有市场创建并接受任务

保存 `GearInHubMission.java` 与 `GearInHubMissionIntel.java` 到 `src/data/scripts/gearin/campaign/`。任务继承 `BaseHubMission`，它已经提供阶段、接受与终止流程，也通过 `BaseIntelPlugin` 提供情报和脚本职责。

在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 追加以下局部入口，目标使用当前市场主实体：

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

当前目标已经存在，子类 `create(...)` 建立阶段条件并返回成功，随后 `accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，登记任务情报与生涯脚本。创建返回 `false` 时，`createAndAbortIfFailed(...)` 会中止任务；本例的有效目标沿成功路径接受。

创建后把任务保存到 persistent data。后续对话取得这份实例，读档观察入口也会沿已有键保留它。附属 Intel 另行登记，读取同一任务供阶段核对。

## 为调查与终止结果定义阶段

类内使用一组枚举表示调查、报告、完成、失败与放弃。**条件连接、对话选项与阶段查询都使用这些值**，局部声明如下：

```java
public enum Stage {
    GearIn_stage_investigate,
    GearIn_stage_report,
    GearIn_stage_completed,
    GearIn_stage_failed,
    GearIn_stage_abandoned
}
```

`STAGE_INVESTIGATE` 等常量指向对应成员。构造器设置 ID 与显示名，再声明接受后的起点和终止阶段：

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

`setStartingStage(...)` 选择调查起点，完成、失败与放弃各接到基类的结束方法。任务 ID 是稳定身份，名称用于情报列表；进入报告仍是活动状态，进入已声明的终止阶段才产生结束结果。

## 用目标条件推进阶段

在 `create(...)` 中，把调查阶段连接到报告，条件是目标自身的 `$GearIn_hub_target_complete`：

```java
@Override
protected boolean create(MarketAPI createdAt, boolean barEvent) {
    connectWithMemoryFlag(STAGE_INVESTIGATE, STAGE_REPORT, target, TARGET_COMPLETE_KEY);
    return true;
}
```

连接明确给出起点、终点、Memory 对象与键。无起点的 `setStageOnMemoryFlag(...)` 可用于跨阶段条件，本节将检查结果限定在调查阶段使用。任务也登记这项 Memory 变更的清理，结束时基类解除目标标记。

`advanceImpl(...)` 累计当前阶段的生涯日数，并周期性检查条件。调查中读到 `true` 后进入报告，关系如下：

```text
GearIn_stage_investigate
    → 目标 Memory: $GearIn_hub_target_complete == true
    → GearIn_stage_report
```

报告本身还没有发放奖励，它表示玩家已经完成调查，可以提交结果。当前阶段与阶段计时保存在任务中，保存读档后继续推进。

## 从检查对话进入报告

在 [C15-S1] 的 `GearInConversationDialog` 检查分支已经设置 `checked=true` 后，加入以下局部写入：

```java
dialog.getInteractionTarget().getMemoryWithoutUpdate().set(
        GearInHubMission.TARGET_COMPLETE_KEY, true);
```

**关闭对话，让生涯推进，再确认报告阶段。** 检查文字在当前会话中写入，任务条件在自己的脚本推进中读取，对话打开时生涯通常处于暂停。

再次打开检查对话，`init(...)` 建立基础选项后，从存档取得任务。报告阶段追加提交按钮，局部片段如下：

```java
GearInHubMission mission = (GearInHubMission) Global.getSector()
        .getPersistentData().get("GearIn_hub_mission");
if (mission.getCurrentStage() == GearInHubMission.STAGE_REPORT) {
    options.addOption("Submit the inspection report", GearInHubMission.STAGE_COMPLETED);
}
```

按钮数据直接使用阶段枚举。将以下局部处理放在 `optionSelected(...)` 的业务分支中，仍确认当前报告状态，再进入完成阶段：

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

检查目标、条件读取与最终提交分别完成，玩家确认报告后才进入成功收尾。文字可以调整，阶段对象与保存任务的键继续保持对应。

## 让报告阶段产生结果

`setCurrentStage(...)` 根据构造器声明的终止阶段选择 `endSuccess(...)`、`endFailure(...)` 或 `endAbandon()`。子类在对应的 `Impl` 回调中补充专属结果，基类继续完成标准收尾。失败与放弃的局部实现如下：

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

本例两条路径都记录 `missionEnded=true`。失败按任务配置处理声望，放弃还根据 `canAbandonWithoutPenalty()` 决定惩罚。测试时分别进入 `STAGE_FAILED` 与 `STAGE_ABANDONED`，当前阶段保留各自结果。

### 一次性完成奖励

成功回调增加固定的 50000 信用点，先检查 `rewardGranted`，再记录奖励已经写入。局部实现如下：

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

基类建立 `HubMissionResult`，处理已配置的标准奖励与声望，调用成功实现，再发送结束更新、进入结束延迟并执行清理。本例固定金额由子类写入，`rewardGranted` 保存这次结果，其他结束动作继续沿基类。

## 让附属 Intel 读取任务

`GearInHubMissionIntel` 保存任务引用，显示时直接读取 `mission.getCurrentStage()`。局部取值如下：

```java
private String getStageText() {
    return String.valueOf(mission.getCurrentStage());
}
```

任务进入报告后，下一次绘制就取得报告值，终止时也读取最终枚举。阶段只由任务修改，附属 Intel 从同一对象生成列表与详情文本。

### 分开登记 Intel 和脚本

任务接受时已经登记为情报与脚本，附属条目另行登记。本例附属 Intel 只在绘制时读取任务阶段，创建方采用下面的局部操作：

```java
GearInHubMissionIntel intel = new GearInHubMissionIntel(mission);
Global.getSector().getIntelManager().addIntel(intel);
```

需要附属条目按时间结束时，再按 [C14-S4] 添加自己的脚本推进。本例没有这项登记，结束附属条目用 `endImmediately()` 与 `removeIntel(intel)`，不采用需要继续推进的延迟结束。

附属条目结束只改变显示，任务阶段保持原值。任务结束后，这条情报也可以继续读取最终阶段，直到创建方显式移除。

::: details 为每一条情报确定用途
任务本身已经是 Intel，本节另外保留一条阶段观察情报，用来比较任务状态与独立显示对象。正式设计若只需要一个任务页面，直接在任务的显示方法中提供完整内容即可。

编者倾向于为额外条目提供明确的玩家用途。同一个任务重复占据两行情报，并不会因此多出一份可用信息；独立提示或需要长期保留的记录，才有单独登记的理由。
:::

## 保存阶段，并核对当前结果

当前阶段、阶段计时、`rewardGranted`、`missionEnded` 与目标引用都属于任务对象。情报与普通脚本登记保存它，persistent data 还提供后续对话的稳定入口，读档继续使用已保存的状态。

附属条目通过当前引用重新绘制，不另存阶段副本。本节从 Java 市场入口直接接受，酒吧邀请与接受可以改用 `HubMissionWithBarEvent`，由 [C15-S5] 连接发现流程。

### 方法速查表

| 方法 | 责任 | 结果 |
| --- | --- | --- |
| `createAndAbortIfFailed` | 创建任务并处理创建失败 | 成功后可接受 |
| `accept` | 设置起始阶段并登记任务 | Intel 与脚本开始运行 |
| `connectWithMemoryFlag` / `setStageOnMemoryFlag` | 连接指定起点或不限起点的 Memory 条件 | 标记成立后选择目标阶段 |
| `setCurrentStage` | 改变当前阶段 | 触发阶段更新或终止逻辑 |
| `setSuccessStage` / `setFailureStage` | 绑定终止阶段 | 成功或失败入口生效 |
| `setAbandonStage` | 绑定放弃阶段 | 放弃路径结束任务 |
| `endSuccessImpl` | 添加任务专属成功结果 | 增加一次 50000 `credits` |
| `getCurrentStage` | 供 Intel 读取阶段 | 列表文本与任务一致 |

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
2. 打开附属的 `GearIn inspection task` 条目，确认当前阶段为 `GearIn_stage_investigate`。
3. 在检查对话选择继续，关闭对话让生涯推进，再打开任务条目，确认报告阶段。
4. 在附属条目确认 `GearIn_stage_report`，保存一份报告阶段测试存档，供三种终止分支分别使用。
5. 重新打开检查对话，提交报告，比较玩家信用增加 50000 与终止阶段。
6. 重复触发完成路径，确认 `rewardGranted` 阻止第二次奖励。
7. 保存并读档，确认当前阶段、`rewardGranted` 和 `missionEnded` 保持。
8. 在提交按钮与处理片段中，将完成阶段的选项数据、分支比较与 `setCurrentStage(...)` 参数统一改为 `STAGE_FAILED`，构建重启并载入报告阶段测试档，提交后确认失败阶段与结束标记。
9. 用同样方式改为 `STAGE_ABANDONED`，构建重启并再次载入报告阶段测试档，确认放弃结果；完成验证后将三处阶段恢复为 `STAGE_COMPLETED`。
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

检查对话写入目标结果，任务条件在生涯推进中进入报告，玩家提交后才触发成功。阶段枚举连接调查与终止，成功奖励保存一次性状态，失败与放弃各进入对应收尾。

任务拥有阶段和结果，附属情报读取当前状态。两者分别结束，保存读档后也沿原任务继续，交互画面结束便不会带走长期任务的进度。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`：本页 `GearInHubMission` 的基类，提供接受、阶段连接和成功、失败、放弃入口；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMission`：任务行为接口，`BaseHubMission` 按它实现任务契约；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：把任务接入酒吧的专用子类，适合需要酒吧发现和接受入口的场景；
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`：`GearInHubMissionIntel` 的基类，任务对象也继承它进入 Intel 列表；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标记忆接口，阶段连接用它检查调查条件；
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`：附属 Intel 的登记入口，本页在任务接受后单独调用 `addIntel`。
