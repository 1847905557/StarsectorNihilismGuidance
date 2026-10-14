# 接入酒吧任务

酒吧里的邀请可以通向一项离开对话后仍在运行的任务。本节制作码头检查：用 `bar_events.csv` 登记任务类，由 Rules 提供邀请和接受选项，接受后经过一个生涯日完成。我们沿这次交接组织代码，再说明酒吧的出现频率、冷却与任务自身的计时分别由谁处理。

*本节预期阅读时间：\~30 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 读懂 `bar_events.csv` 一行如何变成 `BarEventSpec`；
- 区分普通 `PortsideBarEvent` 与 `HubMissionWithBarEvent` 的自动创建分支；
- 解释 `SpecBarEventCreator` 和 `HubMissionBarEventWrapper` 的职责；
- 编写可由 `plugin` 全类名实例化的酒吧任务；
- 观察 CSV 概率、权重和时长字段对事件生命周期的影响。

## 前置知识

- [C15-S3] 的 `BarEventManager`、普通 `creator` 和酒吧选项；
- [C15-S4] 的 `BaseHubMission`、阶段和任务接受；
- Java 无参构造器、继承和全类名；
- CSV 表头、字段顺序和数值类型。

## 登记码头检查任务

将下面注册行写入 `data/campaign/bar_events.csv`，已有表头时只追加数据行。事件 ID 使用 `GearIn_bar_hub`，`plugin` 指向本节的任务类：

```csv
bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin
GearIn_bar_hub,,10,1,30,40,30,40,60,90,data.scripts.gearin.campaign.GearInBarHubMission
```

`BarEventSpec(JSONObject row)` 将这一行读成规格：抽取权重为 10，市场筛选概率为 1，没有额外标签。活动时长与未交互冷却各取 30 到 40 生涯日，交互后冷却取 60 到 90 日。这些时间属于酒吧事件管理；玩家接受的检查只运行一日，由任务类另行设置。

这次也沿用 [C15-S3] 的事件管理器。不过 CSV 已经提供自动建立 `creator` 的入口，我们直接完成注册、任务与 Rules。

## 编写 `HubMission` 任务类

保存 `src/data/scripts/gearin/campaign/GearInBarHubMission.java`，继承 `HubMissionWithBarEvent`。它沿用 [C15-S4] 的任务阶段，同时允许酒吧管理器通过无参构造器创建。构造器设置身份、显示名、接受后的起点与成功终点，局部实现如下：

```java
public GearInBarHubMission() {
    setMissionId("GearIn_bar_hub");
    setName("GearIn dockside inspection");
    setStartingStage(STAGE_ACTIVE);
    setSuccessStage(STAGE_COMPLETED);
}
```

本例的工作是确认酒吧邀请怎样成为已接受的任务，因此采用一日的阶段连接。向 `create(...)` 加入以下局部方法，起点和终点都使用完整类中的枚举常量：

```java
@Override
protected boolean create(MarketAPI createdAt, boolean barEvent) {
    if (createdAt == null || !barEvent) {
        return false;
    }
    connectWithDaysElapsed(STAGE_ACTIVE, STAGE_COMPLETED, 1f);
    return true;
}
```

`createdAt` 来自当前酒吧市场，`barEvent` 为 `true`。符合这条创建路径就返回成功，否则基类中止创建。`connectWithDaysElapsed(...)` 读取 `STAGE_ACTIVE` 的累计时间，接受后进入这个阶段，经过一日再转入成功阶段，由基类收尾。

::: warning 阶段计时需要明确起点
`connectWithDaysElapsed(...)` 的计时器读取起点阶段的进度。本例明确传入 `STAGE_ACTIVE`；无起点的 `setStageOnDaysElapsed(...)` 无法为这次检查提供有效的阶段计时对象。
:::

接受时在对话中写入提示，局部回调如下：

```java
@Override
public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    if (dialog != null) {
        dialog.getTextPanel().addPara("The dockside inspection task is now active.");
    }
}
```

这句话出现在任务被接受之后。邀请和接受按钮由下面的 Rules 提供，当前阶段说明则由完整类的 `getStageDescriptionText()` 提供。三处文字各有实际发生时机。

## 把酒吧文字和接受接到 Rules

将下面五条记录写入 `data/campaign/rules.csv`，已有表头时只追加数据。CSV 基础沿 [C1-S7]，每一行当前使用七列：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_bar_hub_blurb,GearIn_bar_hub_blurbBar,,,一位码头工作人员正在整理船坞检查记录。,,码头邀请
GearIn_bar_hub_option,GearIn_bar_hub_optionBar,,,,GearIn_bar_hub_startBar:询问码头检查工作,酒吧选项
GearIn_bar_hub_offer,DialogOptionSelected,$option == GearIn_bar_hub_startBar,,这份检查将在一个生涯日后完成。是否接受？,"GearIn_bar_hub_acceptBar:接受检查
GearIn_bar_hub_declineBar:暂时拒绝",任务邀请
GearIn_bar_hub_accept,DialogOptionSelected,$option == GearIn_bar_hub_acceptBar,"BarCMD accept GearIn_bar_hub
BarCMD returnFromEvent true",,,接受与返回
GearIn_bar_hub_decline,DialogOptionSelected,$option == GearIn_bar_hub_declineBar,BarCMD returnFromEvent false,,,拒绝与返回
```

`GearIn_bar_hub_blurbBar` 与 `GearIn_bar_hub_optionBar` 分别写描述与邀请选项。玩家选择字符串选项后，酒吧恢复规则对话并触发 `DialogOptionSelected`，`offer` 行显示接受与拒绝。

**选项 ID 使用当前任务前缀。** `BarCMD` 按这一前缀保留对应 `wrapper` 的任务，其他候选可以中止。接受行的 `BarCMD accept GearIn_bar_hub` 从当前 `wrapper` 取得任务，通知事件已交互，再执行任务接受；`returnFromEvent` 把控制权交回酒吧。当前不需要额外手工创建 `creator`。

任务 ID 连接事件表、wrapper、规则 trigger 与选项前缀。显示文字可以随设计修改，这些调用键则保持对应。后面的规则专题会展开 Rules 字段与表达式，本节使用这组记录完成酒吧任务的入口。

## 从事件规格到当前任务

注册、任务和 Rules 已经接好，管理器怎样把它们送到玩家面前？`BarEventManager.readResolve()` 读取 `Global.getSettings().getAllBarEventSpecs()`，为缺少 `creator` 的规格建立 `SpecBarEventCreator`。它按 spec ID 识别自动项，读档后也会补齐缺失项，并从规格取得权重与时长。以下局部片段展示规格创建事件的关系，实际登记由管理器完成：

```java
SpecBarEventCreator creator = new SpecBarEventCreator("GearIn_bar_hub");
PortsideBarEvent event = creator.createBarEvent();
```

`BarEventSpec.isMission()` 实例化 `plugin`，判断它是否为 `HubMissionWithBarEvent`。`SpecBarEventCreator.createBarEvent()` 据此选择创建方式，局部逻辑如下：

```java
if (spec.isMission()) {
    return new HubMissionBarEventWrapper(specId);
}
return spec.createEvent();
```

普通 `PortsideBarEvent` 直接成为酒吧事件。任务则先由 `HubMissionBarEventWrapper` 代管酒吧入口，候选接受之后，任务才进入长期运行。**同样是 CSV 的 `plugin`，继承类型决定它实际接入哪一条流程。**

wrapper 在 `shouldShowAtMarket(market)` 中用市场 ID 与自己的种子建立随机源，按 `prob` 筛选，再创建任务、设置 spec ID 和随机源，并调用任务自己的市场条件。筛选通过后，`addPromptAndOption(...)` 使用当前交互目标的市场创建任务，再触发前面两类 Rules。下面的局部片段保留创建与规则交接：

```java
mission.createAndAbortIfFailed(market, true);
if (mission.isMissionCreationAborted()) {
    mission = null;
    return;
}
mission.updateInteractionData(dialog, memoryMap);
FireBest.fire(null, dialog, memoryMap,
        mission.getTriggerPrefix() + "_blurbBar true");
FireBest.fire(null, dialog, memoryMap,
        mission.getTriggerPrefix() + "_optionBar true");
```

`updateInteractionData(...)` 准备任务前缀、人物与市场上下文，Rules 据此写入邀请。玩家接受时，`BarCMD` 从当前 wrapper 取得这份任务，`accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，并登记 Intel 与生涯脚本。现在离开酒吧，计时仍由已接受的任务推进。

::: info 构造器也会用于类型判断
`isMission()` 会实例化插件来判断类型，筛选时还会另外创建任务。编者建议把依赖市场、会改变世界的创建操作放在 `create(...)`，构造器只设置任务本身；类型判断同样会运行构造器。
:::

## 读取 CSV 生命周期字段

`freq` 决定管理器抽取 creator 时的权重，`prob` 决定 wrapper 是否在当前市场建立任务。活动期限限制事件留在候选列表的时间，两组 timeout 分别控制未交互与已交互后的冷却。CSV 的三组时长沿着下面的入口生效：

```text
prob → shouldShowAtMarket(market)
freq → WeightedRandomPicker
min/max dur → active tracker
min/max timeout → 未交互 timeout
min/max accepted timeout → 交互后 timeout
```

事件被抽取但没有进入酒吧时，`active` 时长耗尽后 `manager` 移除它并设置普通 `timeout`。玩家接受任务后，管理器收到已交互通知，设置 `accepted` `timeout`。本节拒绝行只返回酒吧，未接受候选继续按活动与普通 `timeout` 管理；两组期限按管理器收到的实际结束通知选择。

### 任务事件和普通事件的边界

普通 `plugin` 由 `spec` 直接创建 `PortsideBarEvent`，事件自己实现 `addPromptAndOption(...)` 和 `optionSelected(...)`。`HubMission` `plugin` 先创建 `wrapper`，`wrapper` 再创建任务并通过 Rules 生成酒吧入口；任务接受后，它成为 Intel 和脚本系统中的长期对象。两条分支共享 `manager` 的抽取与 `timeout`，但创建对象和后续生命周期不同。

## 酒吧任务字段与对象速查表

| 字段或对象 | 读取位置 | 作用 |
| --- | --- | --- |
| `bar event id` | `BarEventSpec.id` | `spec` 和 `wrapper` 身份 |
| `freq` | `getFreq()` | `creator` 抽取权重 |
| `prob` | `getProb()` | 市场概率筛选 |
| `min/max dur` | `getMinDur/getMaxDur` | `active` 生命周期 |
| `min/max timeout` | `getMinTimeout/getMaxTimeout` | 未交互冷却 |
| `min/max accepted timeout` | `getMinAcceptedTimeout/getMaxAcceptedTimeout` | 交互后冷却 |
| `plugin` | `getPluginClass()` | 普通事件或 `HubMission` 类型 |
| `SpecBarEventCreator` | `manager` 自动创建 | 从 `spec` 生成事件入口 |
| `HubMissionBarEventWrapper` | `isMission() == true` | 在酒吧上下文创建任务 |

## 完整最小例

保存下列类，事件表与 Rules 采用前述完整记录。

::: details 完整成品：GearInBarHubMission

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;

public class GearInBarHubMission extends HubMissionWithBarEvent {

    public enum Stage {
        GearIn_bar_stage_active,
        GearIn_bar_stage_completed
    }

    public static final Stage STAGE_ACTIVE = Stage.GearIn_bar_stage_active;
    public static final Stage STAGE_COMPLETED = Stage.GearIn_bar_stage_completed;

    public GearInBarHubMission() {
        setMissionId("GearIn_bar_hub");
        setName("GearIn dockside inspection");
        setStartingStage(STAGE_ACTIVE);
        setSuccessStage(STAGE_COMPLETED);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (createdAt == null || !barEvent) {
            return false;
        }
        connectWithDaysElapsed(STAGE_ACTIVE, STAGE_COMPLETED, 1f);
        return true;
    }

    @Override
    public String getStageDescriptionText() {
        return "Current stage: " + getCurrentStage();
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The dockside inspection task is now active.");
        }
    }
}
```

:::

## 验证方案

前置数据：包括任务类、`bar_events` 注册行和本节五条 Rules 的 MOD；使用含太空港和酒吧入口的测试存档。

1. 保存类与两份 CSV，构建重启，进入 `Foundry` 或其他太空港市场。
2. 观察事件抽取，确认 `GearIn_bar_hub` 通过 `isMission()` 进入 `wrapper` 分支。
3. 进入酒吧，确认 Rules 显示由 `wrapper` 提供的任务 `blurb` 和 `option`。
4. 选择码头检查邀请，查看接受与拒绝按钮；选择接受后确认任务进入 Intel 列表。
5. 接受任务，确认 Intel 列表出现 `GearIn dockside inspection`，当前阶段为 `GearIn_bar_stage_active`。
6. 推进 1 个游戏天，确认任务进入 `GearIn_bar_stage_completed` 并执行成功结束。
7. 对照普通 `BarEvent` `plugin`，确认普通分支直接创建事件实例，`HubMission` 分支先创建 `wrapper`。
8. 观察未交互事件和已交互任务的冷却时间，确认分别使用普通 `timeout` 与 `accepted` `timeout`。

预期结果：

- CSV 行被解析为 `GearIn_bar_hub` `spec`；
- `manager` 自动生成 `SpecBarEventCreator`；
- `plugin` 类型识别为 `HubMissionWithBarEvent`；
- 酒吧入口由 `HubMissionBarEventWrapper` 创建；
- 任务接受后进入 `active` 阶段；
- 一个游戏天后进入 `completed` 阶段；
- 生命周期数值来自 CSV 字段。

## 故障分析

- **事件没有出现**：`plugin` 全类名无法实例化或 `prob` 筛选未命中。
    - **解决方案**：核对全类名、无参构造器和概率字段。

- **任务被当作普通事件创建**：`plugin` 没有继承 `HubMissionWithBarEvent`。
    - **解决方案**：让任务类继承正确的 `HubMission` 酒吧基类。

- **酒吧出现选项但接受后没有任务**：任务的 `create(...)` 返回了 `false`。
    - **解决方案**：确认 `wrapper` 传入 `barEvent=true` 且市场条件满足。

- **接受后阶段计时发生异常**：计时连接没有提供有效的起点阶段。
    - **解决方案**：在 `create(...)` 使用 `connectWithDaysElapsed(STAGE_ACTIVE, STAGE_COMPLETED, 1f)`，确认构造器也将起始阶段设为 `STAGE_ACTIVE`。

- **事件消失时间不符合预期**：CSV 三组时长列顺序错误。
    - **解决方案**：按表头重新核对 `dur`、`timeout` 和 `accepted` `timeout`。

## 总结

酒吧任务从事件表取得规格与 `creator`，`wrapper` 使用当前市场创建实例，Rules 提供邀请、选项与接受动作。任务接受后进入情报和脚本，一日连接再推进到完成。

概率、权重和冷却控制玩家何时收到邀请，任务阶段与计时控制接受之后的过程。前者由事件管理器处理，后者由已接受的任务推进，观察时分别对应酒吧入口与情报状态。

## 建议参考项

- `com.fs.starfarer.api.loading.BarEventSpec`：CSV 行的读取结果，保存概率、权重和时长字段并判断 `plugin` 类型；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：`manager` 为每个 `spec` 自动建立的 `creator`，按类型返回 `wrapper` 或普通事件；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：本页 `GearInBarHubMission` 的基类，也是自动创建分支的类型判据；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：在酒吧上下文中创建任务并传递 `spec` ID 和随机源；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：读取全部 `spec`，并为尚未拥有 `creator` 的 `spec` 建立 `creator`；
- `data/campaign/bar_events.csv`：原版酒吧事件数据表，本页 MOD 行的格式基准。
