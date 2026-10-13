# 接入酒吧任务

普通酒吧事件已经接入，这次让数据表创建一个长期任务。`bar_events.csv` 提供频率与期限，任务类提供状态，`wrapper` 在当前酒吧市场建立实例，再由 Rules 显示邀请和接受按钮。接受后运行一个生涯日便完成，整个入口从注册一直连到情报与结束。

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

## 从一行 CSV 建立 `spec`

将下面注册行写入 `data/campaign/bar_events.csv`，已有表头时只追加数据行。每行先由 `BarEventSpec(JSONObject row)` 读取。字段名称不是装饰文字：`bar event id` 形成 `spec` ID，`plugin` 保存脚本全类名，数值字段直接进入概率、权重和 `timeout` 计算。

```csv
bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin
GearIn_bar_hub,,10,1,30,40,30,40,60,90,data.scripts.gearin.campaign.GearInBarHubMission
```

这一行表示：事件 ID 是 `GearIn_bar_hub`；没有额外标签；抽取权重为 10；市场筛选概率为 1；活动时长在 30 到 40 游戏天之间；未交互超时在 30 到 40 天之间；交互后超时在 60 到 90 天之间；脚本由 `GearInBarHubMission` 提供。

`freq` 只参与 `creator` `picker` 的权重，`prob` 在任务 `wrapper` 按市场种子进行筛选，三组时长分别进入 `active`、普通 `timeout` 和 `accepted` `timeout`。字段顺序改变后，解析器会把数值写入错误的属性，酒吧中的出现频率和消失时间都会随之偏移。

## 让管理器自动生成 `creator`

`BarEventManager.readResolve()` 读取 `Global.getSettings().getAllBarEventSpecs()`。每个尚未拥有 `creator` 的 `spec` 都会得到一个 `SpecBarEventCreator`，其 `getBarEventId()` 返回 `spec` ID，频率和时长方法直接读取 `spec` 字段。

```java
SpecBarEventCreator creator = new SpecBarEventCreator("GearIn_bar_hub");
PortsideBarEvent event = creator.createBarEvent();
```

实际运行由管理器建立 `creator`，当前局部片段说明规格与事件对象的关系；本节直接使用 CSV 自动接入。`manager` 仍会跳过已有同 ID 的自动 `creator`，读档恢复后也会依据 `spec` 重建缺失的自动项。

### `plugin` 类型决定创建分支

`BarEventSpec.isMission()` 会调用 `Global.getSettings().getInstanceOfScript(pluginClass)`，检查实例是否为 `HubMissionWithBarEvent`。返回 `true` 时，`SpecBarEventCreator.createBarEvent()` 返回 `new HubMissionBarEventWrapper(specId)`；普通 `PortsideBarEvent` 则直接由 `spec.createEvent()` 返回事件实例。

```java
if (spec.isMission()) {
    return new HubMissionBarEventWrapper(specId);
}
return spec.createEvent();
```

因此 CSV 的 `plugin` 必须指向可实例化的 `HubMission` 子类。把普通事件类名填入这一行，会改变 `isMission()` 分支；把没有无参构造器的类名填入 `spec`，则实例化阶段无法建立可用对象。

## `wrapper` 如何取得酒吧上下文

`HubMissionBarEventWrapper` 先在 `shouldShowAtMarket(market)` 中根据 `spec` 概率和市场 ID 生成随机判断，再创建任务、设置 `spec` ID 和随机源，并调用任务自己的 `shouldShowAtMarket(...)`。任务通过筛选后，`wrapper` 在 `addPromptAndOption(...)` 中调用 `createAndAbortIfFailed(market, true)`。

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

**任务创建使用实际酒吧市场。** `market` 来自当前交互目标，`true` 表示酒吧路径。任务创建成功后，`wrapper` 把任务的触发前缀交给 Rules，Rules 再写酒吧 `blurb` 和 `option`；因此任务类负责状态，酒吧文本和选项仍由规则入口完成。

## 编写 `HubMission` 任务类

任务类需要无参构造器，因为 `BarEventSpec.createMission()` 通过脚本全类名实例化它。构造器设置任务 ID、名称与阶段枚举；`create(...)` 接收 `wrapper` 传入的市场和 `barEvent` 标记。

```java
public GearInBarHubMission() {
    setMissionId("GearIn_bar_hub");
    setName("GearIn dockside inspection");
    setStartingStage(STAGE_ACTIVE);
    setSuccessStage(STAGE_COMPLETED);
}
```

保存 `src/data/scripts/gearin/campaign/GearInBarHubMission.java`。任务连接 `active` 与 `completed` 两个枚举阶段。`setStageOnDaysElapsed(STAGE_COMPLETED, 1f)` 让任务运行一个游戏天后进入成功阶段，基类随即执行成功收尾；已有调查、报告与奖励按 [C15-S4] 的任务对象安排，当前集中观察酒吧接入。

```java
@Override
protected boolean create(MarketAPI createdAt, boolean barEvent) {
    if (createdAt == null || !barEvent) {
        return false;
    }
    setStageOnDaysElapsed(STAGE_COMPLETED, 1f);
    return true;
}
```

`createdAt` 说明任务由哪个市场创建，`barEvent` 说明当前调用来自 `wrapper` 的酒吧路径。条件符合后返回 `true`，`wrapper` 继续准备规则；创建失败返回 `false`，基类标记中止。

## 观察 Rules 与任务的交接

`updateInteractionData(dialog, memoryMap)` 把任务对象提供的触发前缀、人物和市场上下文准备给 Rules。`wrapper` 随后分别触发 `blurb` 和 `option` 分类；玩家在酒吧看到的文字来自这些规则，而不是 `GearInBarHubMission.acceptImpl(...)`。

接受行通过 `BarCMD` 从当前 `wrapper` 取得任务，`accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，并加入 Intel 与生涯脚本列表。任务文本可以在接受对话中出现：

```java
@Override
public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    if (dialog != null) {
        dialog.getTextPanel().addPara("The dockside inspection task is now active.");
    }
}
```

酒吧 `blurb`、酒吧 `option` 和任务接受文本属于不同入口：前两者由 `wrapper` 的 Rules 触发，后者由任务接受回调写入。把任务接受文本写进 `addPromptAndOption(...)` 会让它在玩家尚未接受任务时提前出现。

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

`GearIn_bar_hub_blurbBar` 与 `GearIn_bar_hub_optionBar` 由 `wrapper` 触发，分别写描述与邀请选项。玩家选择字符串选项后，酒吧恢复规则对话并触发 `DialogOptionSelected`，`offer` 行显示接受与拒绝。

**选项 ID 使用当前任务前缀。** `BarCMD` 按这一前缀保留对应 `wrapper` 的任务，其他候选可以中止。接受行的 `BarCMD accept GearIn_bar_hub` 从当前 `wrapper` 取得任务，通知事件已交互，再执行任务接受；`returnFromEvent` 把控制权交回酒吧。当前不需要额外手工创建 `creator`。

任务 ID 连接事件表、`wrapper`、规则 `trigger` 与选项前缀。文字可以随设计修改，调用入口继续按这些稳定键对应。Rules 字段与表达式由后面的规则专题完整展开，本节先把这条酒吧任务运行起来。

## 读取 CSV 生命周期字段

`spec` 的概率、权重和三个时长在不同阶段生效。概率决定 `wrapper` 是否在当前市场建立任务；权重只影响 `manager` 从多个 `creator` 中抽取谁；`active` `duration` 决定事件保持在活动列表的时间；普通 `timeout` 处理未交互过期；`accepted` `timeout` 处理玩家已经交互后的冷却。

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
        setStageOnDaysElapsed(STAGE_COMPLETED, 1f);
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

- **事件消失时间不符合预期**：CSV 三组时长列顺序错误。
    - **解决方案**：按表头重新核对 `dur`、`timeout` 和 `accepted` `timeout`。

## 总结

酒吧任务从事件表取得规格与 `creator`，`wrapper` 使用当前市场创建实例，Rules 提供邀请、选项与接受动作。任务接受后进入情报和脚本，一日连接再推进到完成。

编者建议把酒吧入口与任务状态分别验证。概率、权重和冷却决定何时看到邀请，实际阶段与计时决定接受后发生什么，两者组合才是完整的任务接入。

## 建议参考项

- `com.fs.starfarer.api.loading.BarEventSpec`：CSV 行的读取结果，保存概率、权重和时长字段并判断 `plugin` 类型；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：`manager` 为每个 `spec` 自动建立的 `creator`，按类型返回 `wrapper` 或普通事件；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：本页 `GearInBarHubMission` 的基类，也是自动创建分支的类型判据；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：在酒吧上下文中创建任务并传递 `spec` ID 和随机源；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：读取全部 `spec`，并为尚未拥有 `creator` 的 `spec` 建立 `creator`；
- `data/campaign/bar_events.csv`：原版酒吧事件数据表，本页 MOD 行的格式基准。
