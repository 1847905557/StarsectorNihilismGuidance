# 接入酒吧任务

S3 的普通酒吧事件由 Java creator 主动注册；本节把一个 `HubMissionWithBarEvent` 任务交给 `bar_events.csv`。游戏读取 spec 后会自动建立 creator，之后由 wrapper 在真实酒吧市场中创建任务实例。页面重点放在这条类型和数据的转换链，任务内容只保留一个可以观察的完成阶段。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

- 读懂 `bar_events.csv` 一行如何变成 `BarEventSpec`；
- 区分普通 `PortsideBarEvent` 与 `HubMissionWithBarEvent` 的自动创建分支；
- 解释 `SpecBarEventCreator` 和 `HubMissionBarEventWrapper` 的职责；
- 编写可由 plugin 全类名实例化的酒吧任务；
- 观察 CSV 概率、权重和时长字段对事件生命周期的影响。

## 前置知识

- [C15-S3] 的 `BarEventManager`、普通 creator 和酒吧选项；
- [C15-S4] 的 `BaseHubMission`、阶段和任务接受；
- Java 无参构造器、继承和全类名；
- CSV 表头、字段顺序和数值类型。

## 从一行 CSV 建立 spec

`bar_events.csv` 的每一行先由 `BarEventSpec(JSONObject row)` 读取。字段名称不是装饰文字：`bar event id` 形成 spec ID，`plugin` 保存脚本全类名，数值字段直接进入概率、权重和 timeout 计算。

```csv
bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin
GearIn_bar_hub,,10,1,30,40,30,40,60,90,data.scripts.gearin.campaign.GearInBarHubMission
```

这一行表示：事件 ID 是 `GearIn_bar_hub`；没有额外标签；抽取权重为 10；市场筛选概率为 1；活动时长在 30 到 40 游戏天之间；未交互超时在 30 到 40 天之间；交互后超时在 60 到 90 天之间；脚本由 `GearInBarHubMission` 提供。

`freq` 只参与 creator picker 的权重，`prob` 在任务 wrapper 按市场种子进行筛选，三组时长分别进入 active、普通 timeout 和 accepted timeout。字段顺序改变后，解析器会把数值写入错误的属性，酒吧中的出现频率和消失时间都会随之偏移。

## 让管理器自动生成 creator

`BarEventManager.readResolve()` 读取 `Global.getSettings().getAllBarEventSpecs()`。每个尚未拥有 creator 的 spec 都会得到一个 `SpecBarEventCreator`，其 `getBarEventId()` 返回 spec ID，频率和时长方法直接读取 spec 字段。

```java
SpecBarEventCreator creator = new SpecBarEventCreator("GearIn_bar_hub");
PortsideBarEvent event = creator.createBarEvent();
```

实际运行中这段创建由 manager 完成，页面代码只用于说明对象关系：CSV 不需要为每行手写 `addEventCreator`。manager 仍会跳过已有同 ID 的自动 creator，读档恢复后也会依据 spec 重建缺失的自动项。

### plugin 类型决定创建分支

`BarEventSpec.isMission()` 会调用 `Global.getSettings().getInstanceOfScript(pluginClass)`，检查实例是否为 `HubMissionWithBarEvent`。返回 `true` 时，`SpecBarEventCreator.createBarEvent()` 返回 `new HubMissionBarEventWrapper(specId)`；普通 `PortsideBarEvent` 则直接由 `spec.createEvent()` 返回事件实例。

```java
if (spec.isMission()) {
    return new HubMissionBarEventWrapper(specId);
}
return spec.createEvent();
```

因此 CSV 的 `plugin` 必须指向可实例化的 HubMission 子类。把普通事件类名填入这一行，会改变 `isMission()` 分支；把没有无参构造器的类名填入 spec，则实例化阶段无法建立可用对象。

## wrapper 如何取得酒吧上下文

`HubMissionBarEventWrapper` 先在 `shouldShowAtMarket(market)` 中根据 spec 概率和市场 ID 生成随机判断，再创建任务、设置 spec ID 和随机源，并调用任务自己的 `shouldShowAtMarket(...)`。任务通过筛选后，wrapper 在 `addPromptAndOption(...)` 中调用 `createAndAbortIfFailed(market, true)`。

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

`market` 来自当前酒吧交互目标，`true` 明确表示这是酒吧任务创建。任务创建成功后，wrapper 把任务的触发前缀交给 Rules，Rules 再写酒吧 blurb 和 option；因此任务类负责状态，酒吧文本和选项仍由规则入口完成。

## 编写 HubMission 任务类

任务类需要无参构造器，因为 `BarEventSpec.createMission()` 通过脚本全类名实例化它。构造器设置 mission ID、名称、起始阶段和成功阶段；`create(...)` 接收 wrapper 传入的市场和 `barEvent` 标记。

```java
public GearInBarHubMission() {
    setMissionId("GearIn_bar_hub");
    setName("GearIn dockside inspection");
    setStartingStage(STAGE_ACTIVE);
    setSuccessStage(STAGE_COMPLETED);
}
```

本节任务只建立 active 到 completed 的一条时间连接。`setStageOnDaysElapsed(STAGE_COMPLETED, 1f)` 让任务运行一个游戏天后进入成功阶段，基类随即执行成功收尾；S4 的复杂目标、奖励和附属 Intel 不在这里重复。

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

`createdAt` 说明任务由哪个市场创建，`barEvent` 说明当前调用来自 wrapper 的酒吧路径。返回 `true` 后 wrapper 才会继续更新交互数据；返回 `false` 会让 `createAndAbortIfFailed(...)` 调用 `abort()`。

## 观察 Rules 与任务的交接

`updateInteractionData(dialog, memoryMap)` 把任务对象提供的触发前缀、人物和市场上下文准备给 Rules。wrapper 随后分别触发 blurb 和 option 分类；玩家在酒吧看到的文字来自这些规则，而不是 `GearInBarHubMission.acceptImpl(...)`。

任务真正被接受后，`accept(...)` 设置起始阶段、执行 `acceptImpl(...)`，并把任务加入 Intel 管理器和生涯脚本列表。任务文本可以在接受对话中出现：

```java
@Override
public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    if (dialog != null) {
        dialog.getTextPanel().addPara("The dockside inspection task is now active.");
    }
}
```

酒吧 blurb、酒吧 option 和任务接受文本属于不同入口：前两者由 wrapper 的 Rules 触发，后者由任务接受回调写入。把任务接受文本写进 `addPromptAndOption(...)` 会让它在玩家尚未接受任务时提前出现。

## 读取 CSV 生命周期字段

spec 的概率、权重和三个时长在不同阶段生效。概率决定 wrapper 是否在当前市场建立任务；权重只影响 manager 从多个 creator 中抽取谁；active duration 决定事件保持在活动列表的时间；普通 timeout 处理未交互过期；accepted timeout 处理玩家已经交互后的冷却。

```text
prob → shouldShowAtMarket(market)
freq → WeightedRandomPicker
min/max dur → active tracker
min/max timeout → 未交互 timeout
min/max accepted timeout → 交互后 timeout
```

事件被抽取但没有进入酒吧时，active 时长耗尽后 manager 移除它并设置普通 timeout。玩家接受任务后，wrapper 代表的事件完成交互，manager 设置 accepted timeout；CSV 的两组 timeout 因而对应两种不同的消失原因。

### 任务事件和普通事件的边界

普通 plugin 由 spec 直接创建 `PortsideBarEvent`，事件自己实现 `addPromptAndOption(...)` 和 `optionSelected(...)`。HubMission plugin 先创建 wrapper，wrapper 再创建任务并通过 Rules 生成酒吧入口；任务接受后，它成为 Intel 和脚本系统中的长期对象。两条分支共享 manager 的抽取与 timeout，但创建对象和后续生命周期不同。

## 速查表

| 字段或对象 | 读取位置 | 作用 |
| --- | --- | --- |
| `bar event id` | `BarEventSpec.id` | spec 和 wrapper 身份 |
| `freq` | `getFreq()` | creator 抽取权重 |
| `prob` | `getProb()` | 市场概率筛选 |
| `min/max dur` | `getMinDur/getMaxDur` | active 生命周期 |
| `min/max timeout` | `getMinTimeout/getMaxTimeout` | 未交互冷却 |
| `min/max accepted timeout` | `getMinAcceptedTimeout/getMaxAcceptedTimeout` | 交互后冷却 |
| `plugin` | `getPluginClass()` | 普通事件或 HubMission 类型 |
| `SpecBarEventCreator` | manager 自动创建 | 从 spec 生成事件入口 |
| `HubMissionBarEventWrapper` | `isMission() == true` | 在酒吧上下文创建任务 |

## 完整最小例

完整任务类只提供 HubMission 类型判断和一个简单完成阶段。CSV 行仍由游戏的 `bar_events.csv` 加载器读取。

::: details 完整成品：GearInBarHubMission

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;

public class GearInBarHubMission extends HubMissionWithBarEvent {

    public static final String STAGE_ACTIVE = "GearIn_bar_stage_active";
    public static final String STAGE_COMPLETED = "GearIn_bar_stage_completed";

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
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The dockside inspection task is now active.");
        }
    }
}
```

:::

## 验证方案

前置数据：启用包含 `GearInBarHubMission` 全类名和对应 `bar_events.csv` 行的 MOD，进入一个有空间站的市场酒吧。

1. 载入生涯并进入有空间站的市场，确认 `BarEventManager` 从 spec 建立自动 creator；
2. 观察事件抽取，确认 `GearIn_bar_hub` 通过 `isMission()` 进入 wrapper 分支；
3. 进入酒吧，确认 Rules 显示由 wrapper 提供的任务 blurb 和 option；
4. 选择任务选项，确认 wrapper 调用 `createAndAbortIfFailed(market, true)`；
5. 接受任务，确认 Intel 列表出现 `GearIn dockside inspection`，当前阶段为 `GearIn_bar_stage_active`；
6. 推进 1 个游戏天，确认任务进入 `GearIn_bar_stage_completed` 并执行成功结束；
7. 对照普通 BarEvent plugin，确认普通分支直接创建事件实例，HubMission 分支先创建 wrapper；
8. 观察未交互事件和已交互任务的冷却时间，确认分别使用普通 timeout 与 accepted timeout。

预期结果：

- CSV 行被解析为 `GearIn_bar_hub` spec；
- manager 自动生成 `SpecBarEventCreator`；
- plugin 类型识别为 `HubMissionWithBarEvent`；
- 酒吧入口由 `HubMissionBarEventWrapper` 创建；
- 任务接受后进入 active 阶段；
- 一个游戏天后进入 completed 阶段；
- 生命周期数值来自 CSV 字段。

## 故障分析

- **事件没有出现**：`plugin` 全类名无法实例化或 `prob` 筛选未命中。

    - **解决方案**：核对全类名、无参构造器和概率字段。

- **任务被当作普通事件创建**：plugin 没有继承 `HubMissionWithBarEvent`。

    - **解决方案**：让任务类继承正确的 HubMission 酒吧基类。

- **酒吧出现选项但接受后没有任务**：任务的 `create(...)` 返回了 `false`。

    - **解决方案**：确认 wrapper 传入 `barEvent=true` 且市场条件满足。

- **事件消失时间不符合预期**：CSV 三组时长列顺序错误。

    - **解决方案**：按表头重新核对 dur、timeout 和 accepted timeout。

## 总结

`bar_events.csv` 先产生 `BarEventSpec`，`BarEventManager` 再为 spec 建立 `SpecBarEventCreator`。plugin 被识别为 `HubMissionWithBarEvent` 后，creator 返回 `HubMissionBarEventWrapper`；wrapper 在市场筛选和酒吧显示阶段创建任务、传递 Rules 上下文，任务接受后进入 Intel 和生涯脚本生命周期。CSV 的概率、权重和时长分别控制筛选、抽取和冷却，任务类只负责自己的阶段状态。

## 建议参考项

- `com.fs.starfarer.api.loading.BarEventSpec`：CSV 行的读取结果，保存概率、权重和时长字段并判断 plugin 类型；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator`：manager 为每个 spec 自动建立的 creator，按类型返回 wrapper 或普通事件；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：本页 `GearInBarHubMission` 的基类，也是自动创建分支的类型判据；
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionBarEventWrapper`：在酒吧上下文中创建任务并传递 spec ID 和随机源；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：读取全部 spec，并为尚未拥有 creator 的 spec 建立 creator；
- `data/campaign/bar_events.csv`：原版酒吧事件数据表，本页 MOD 行的格式基准。
