# 接入酒吧事件

酒吧事件不是一段孤立的对话代码。`BarEventManager` 负责发现和保存候选事件，creator 决定什么时候生成事件，`BarCMD` 把可用事件变成酒吧选项，事件本身再把控制权交给对话插件。本节用一笔金属交易把这条链路接通：玩家提供 100 单位金属后获得 5000 credits，拒绝会结束事件，暂时离开则保留事件。

*本节预期阅读时间：~32 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

- 说明 `BarEventManager`、creator、`PortsideBarEvent` 与 `BarCMD` 的职责；
- 注册一个 Java creator 并控制事件权重和有效期；
- 让事件按照市场条件出现在酒吧；
- 通过稳定 `optionData` 区分接受、拒绝和离开；
- 用货舱金属和 credits 完成一次性交易；
- 区分拒绝结束、离开保留和未交互过期三种状态。

## 前置知识

- [C14-S3] 的生涯实体、市场对象和交互对话入口；
- [C15-S1] 的 `InteractionDialogAPI`、目标对象和关闭语义；
- Java 接口实现、继承和布尔状态；
- `CargoAPI` 的商品数量、移除商品和 credits 读写。

## 认识酒吧事件的对象关系

酒吧刷新时，`BarEventManager` 从 creator 集合中挑选一个 creator。creator 返回 `PortsideBarEvent`，管理器把实例加入活动列表；玩家进入有酒吧的市场后，`BarCMD` 检查事件是否应该在该市场显示，再调用事件的 `addPromptAndOption(...)`。玩家选择事件选项后，`BarEventDialogPlugin` 接管同一个 `InteractionDialogAPI`，把初始化和选项回调转发给事件。

这里的职责边界很具体：creator 只生成事件并提供抽取参数，事件只描述条件、文本和结果，`BarCMD` 只负责酒吧界面与事件插件交接。把注册代码写进事件类会让生命周期混在一起，也无法解释为什么同一个 creator 被重复加入后权重会增加。

### 从生涯入口注册 creator

注册发生在生涯已经建立的回调中。`BarEventManager.getInstance()` 取得存档中的管理器，`hasEventCreator(...)` 按类型检查当前集合，缺失时才加入新实例。

```java
BarEventManager manager = BarEventManager.getInstance();
if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
    manager.addEventCreator(new GearInBarEventCreator());
}
```

`addEventCreator` 本身直接追加集合；因此去重判断决定了抽取权重是否保持预期。重复注册不会让事件 "更可靠"，只会让同一个 creator 在 picker 中拥有多份机会。

### creator 提供抽取参数

creator 的 `createBarEvent()` 返回一个新的事件实例。四个时长方法使用游戏天：活跃时长决定事件留在活动跟踪器中的时间，普通超时用于事件未被交互便过期后的冷却，accepted timeout 用于玩家已经处理事件后的冷却，频率权重参与随机选择。

```java
@Override
public PortsideBarEvent createBarEvent() {
    return new GearInBarEvent();
}

@Override
public float getBarEventFrequencyWeight() {
    return 10f;
}
```

本例把活跃时长设为 30 天、未交互超时设为 30 天、交互后超时设为 60 天。事件被玩家处理后，管理器会移除活动实例并使用 accepted timeout；事件始终没有进入酒吧，则由普通超时回收。

## 让事件进入酒吧

事件是否出现在某个市场由 `shouldShowAtMarket(MarketAPI market)` 决定。示例要求市场存在空间站，并继续调用基类实现的 `shownAt` 约束；事件第一次被展示后，基类记录该市场，后续市场不会重新展示同一实例。

```java
@Override
public boolean shouldShowAtMarket(MarketAPI market) {
    return market != null && market.hasSpaceport() && super.shouldShowAtMarket(market);
}
```

`shouldRemoveEvent()` 默认返回 `false`，因此本例的过期回收由管理器的 active 跟踪器完成。需要事件自行消失时，才在该方法和 `advance(float amount)` 中维护额外倒计时。

### 把事件写成酒吧选项

`addPromptAndOption(...)` 同时写入酒吧描述和选项。关键在于把事件实例本身作为 `optionData`：`BarCMD.optionSelected(...)` 正是通过这个对象识别 `PortsideBarEvent`，然后创建 `BarEventDialogPlugin`。

```java
@Override
public void addPromptAndOption(InteractionDialogAPI dialog,
        Map<String, MemoryAPI> memoryMap) {
    dialog.getTextPanel().addPara(
            "A dockworker offers a credit payment for a shipment of refined metals.");
    dialog.getOptionPanel().addOption("Trade 100 metals for 5000 credits", this);
}
```

显示文字只负责告诉玩家交易内容，事件对象负责把回调送进正确的事件实例。把 `this` 换成普通字符串后，酒吧仍然能显示一行选项，但事件对话交接不会发生。

## 让事件插件处理对话

`BarEventDialogPlugin` 保存原来的 `BarCMD` 和事件对象。进入事件时，它调用 `event.init(dialog, memoryMap)`；玩家选择选项时，它先回显选项文本，再调用 `event.optionSelected(...)`。事件把 `done` 设为 `true` 后，外壳执行 `endEvent()`，将原来的插件交还给 `BarCMD`。

事件的初始化只负责建立当前选项集合：

```java
@Override
public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    super.init(dialog, memoryMap);
    options.clearOptions();
    options.addOption("Provide 100 metals", OPTION_ACCEPT);
    options.addOption("Refuse the trade", OPTION_DECLINE);
    options.addOption("Leave for now", OPTION_LEAVE);
}
```

`BaseBarEvent.init(...)` 已经把 `dialog`、文本面板、选项面板和 memory map 保存到受保护字段；子类只需要建立自己的分支。事件完成后交还 `BarCMD`，酒吧选项才会重新成为当前界面。

## 处理金属交易

接受分支先读取玩家舰队货舱中的 `metals` 数量。数量不足时只写出原因并保留当前事件，玩家可以补充货物后再次进入；数量满足时才移除金属、增加 credits 和记录奖励状态。

```java
if (OPTION_ACCEPT.equals(optionData)) {
    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
    if (cargo.getCommodityQuantity(METALS) < METALS_REQUIRED) {
        text.addPara("You need 100 metals before this trade can be accepted.");
        return;
    }
    if (!rewardGranted) {
        cargo.removeCommodity(METALS, METALS_REQUIRED);
        cargo.getCredits().add(CREDIT_REWARD);
        rewardGranted = true;
        text.addPara("The dockworker takes the metals and pays you 5000 credits.");
        done = true;
        BarEventManager.getInstance().notifyWasInteractedWith(this);
    }
}
```

`getCommodityQuantity("metals")` 返回当前货舱数量，`removeCommodity` 扣除实际交易量，`getCredits().add(5000)` 增加玩家资金。`rewardGranted` 让交易写入和 manager 通知保持一次；通知发生后，管理器从活动列表移除事件并设置交互后超时。

## 区分拒绝与离开

拒绝代表玩家已经处理了这条酒吧事件，因此事件写入拒绝文本、设置 `done`，并调用 `notifyWasInteractedWith(this)`。这条路径不会发放奖励，但会让 creator 进入交互后的冷却。

```java
if (OPTION_DECLINE.equals(optionData)) {
    text.addPara("You refuse the trade and leave the dockworker behind.");
    done = true;
    noContinue = true;
    BarEventManager.getInstance().notifyWasInteractedWith(this);
}
```

离开表示玩家尚未作出决定。事件写出离开文本并结束当前事件对话，但不调用 manager 通知；活动实例仍由 manager 跟踪，后续酒吧刷新可以再次显示它。

```java
if (OPTION_LEAVE.equals(optionData)) {
    text.addPara("You leave before making a decision.");
    done = true;
    noContinue = true;
}
```

`noContinue = true` 让事件外壳返回 `BarCMD` 时不再显示事件自己的继续入口。它只影响对话收尾，不改变事件是否已经被 manager 认定为已交互。

## 观察事件状态如何结束

事件实例进入 manager 后，会经历 "活动跟踪 → 市场筛选 → 酒吧显示 → 事件对话" 这条路径。接受和拒绝调用 `notifyWasInteractedWith`，因此活动列表移除事件并建立 accepted timeout；离开没有通知，事件仍留在活动列表；任何路径都没有被玩家处理并且 active 时长耗尽时，manager 建立普通 timeout。

事件自己的 `rewardGranted` 是奖励门控，`done` 是对话完成标记，manager 的 active 和 timeout 跟踪器则负责全局生命周期。它们描述不同层次的状态，不能用一个字段替代另一个。

### 常见状态症状

- 事件在酒吧完全不出现：先检查市场是否有空间站，再检查 `shouldShowAtMarket` 是否受 `shownAt` 限制；
- 同一个事件出现多份：检查 creator 注册是否绕过 `hasEventCreator`；
- 接受后金属没有减少：确认货舱中 `metals` 数量达到 100，且分支使用的是 `getCommodityQuantity` 与 `removeCommodity`；
- 拒绝后仍能立即看到事件：确认拒绝分支调用了 `notifyWasInteractedWith(this)`；
- 离开后事件消失：检查离开分支是否误调用了 manager 通知。

## 速查表

| 对象或方法 | 作用 | 本例中的结果 |
| --- | --- | --- |
| `BarEventManager` | 抽取、跟踪和回收事件 | 管理 active 与 timeout |
| `BaseBarEventCreator` | 提供事件实例和抽取参数 | 生成 `GearInBarEvent` |
| `shouldShowAtMarket` | 判断市场是否显示事件 | 仅空间站市场显示 |
| `addPromptAndOption` | 写酒吧文本和选项 | `optionData` 使用事件实例 |
| `notifyWasInteractedWith` | 移除已处理事件并设置冷却 | 接受、拒绝调用 |
| `BarEventDialogPlugin` | 在 `BarCMD` 与事件之间转发回调 | 结束后交还 `BarCMD` |

## 完整最小例

下面两个类分别承担事件和 creator 职责。页面中的局部片段对应这两个类的真实实现，运行时由生涯回调完成 creator 注册。

::: details 完整成品：GearInBarEvent

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

public class GearInBarEvent extends BaseBarEvent {

    private static final String OPTION_ACCEPT = "GEARIN_BAR_ACCEPT";
    private static final String OPTION_DECLINE = "GEARIN_BAR_DECLINE";
    private static final String OPTION_LEAVE = "GEARIN_BAR_LEAVE";
    private static final String METALS = "metals";
    private static final float METALS_REQUIRED = 100f;
    private static final int CREDIT_REWARD = 5000;

    private boolean rewardGranted;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market != null && market.hasSpaceport() && super.shouldShowAtMarket(market);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        dialog.getTextPanel().addPara(
                "A dockworker offers a credit payment for a shipment of refined metals.");
        dialog.getOptionPanel().addOption("Trade 100 metals for 5000 credits", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        options.clearOptions();
        options.addOption("Provide 100 metals", OPTION_ACCEPT);
        options.addOption("Refuse the trade", OPTION_DECLINE);
        options.addOption("Leave for now", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_ACCEPT.equals(optionData)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            if (cargo.getCommodityQuantity(METALS) < METALS_REQUIRED) {
                text.addPara("You need 100 metals before this trade can be accepted.");
                return;
            }
            if (!rewardGranted) {
                cargo.removeCommodity(METALS, METALS_REQUIRED);
                cargo.getCredits().add(CREDIT_REWARD);
                rewardGranted = true;
                text.addPara("The dockworker takes the metals and pays you 5000 credits.");
                done = true;
                BarEventManager.getInstance().notifyWasInteractedWith(this);
            }
        } else if (OPTION_DECLINE.equals(optionData)) {
            text.addPara("You refuse the trade and leave the dockworker behind.");
            done = true;
            noContinue = true;
            BarEventManager.getInstance().notifyWasInteractedWith(this);
        } else if (OPTION_LEAVE.equals(optionData)) {
            text.addPara("You leave before making a decision.");
            done = true;
            noContinue = true;
        }
    }
}
```

:::

::: details 完整成品：GearInBarEventCreator

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

public class GearInBarEventCreator extends BaseBarEventCreator {

    @Override
    public PortsideBarEvent createBarEvent() {
        return new GearInBarEvent();
    }

    @Override
    public float getBarEventFrequencyWeight() {
        return 10f;
    }

    @Override
    public float getBarEventActiveDuration() {
        return 30f;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 30f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 60f;
    }
}
```

:::

## 验证方案

前置数据：启用包含两个 Java 类和注册片段的 MOD，进入一个有空间站的市场酒吧，并准备一份金属不足 100 的存档和一份至少有 100 金属的存档。

1. 载入生涯，执行 creator 注册片段两次，确认管理器中只有一个 `GearInBarEventCreator`；
2. 进入有空间站的市场酒吧，确认出现 "Trade 100 metals for 5000 credits"；
3. 在金属不足的存档中选择接受，确认文本提示需要 100 金属，货舱和 credits 数值不变；
4. 在至少有 100 金属的存档中选择接受，确认金属减少 100、credits 增加 5000，事件结束并离开活动列表；
5. 重复触发接受回调，确认 `rewardGranted` 阻止第二次扣货和加钱；
6. 在另一份事件实例中选择拒绝，确认事件结束并进入交互后 timeout；
7. 在另一份事件实例中选择离开，确认当前事件对话结束，事件仍可由酒吧刷新显示；
8. 等待未交互事件的 active 时长耗尽，确认事件从活动列表移除并进入普通 timeout；
9. 返回 `BarCMD` 后确认酒吧选项恢复，事件对话没有残留选项。

预期结果：

- creator 只注册一次；
- 空间站市场显示事件；
- 金属不足不会完成接受分支；
- 接受成功只扣除 100 金属并增加 5000 credits；
- 拒绝结束事件并触发交互后冷却；
- 离开结束当前对话但保留事件；
- 未交互过期事件进入普通冷却；
- 返回酒吧后回调归属恢复到 `BarCMD`。

## 故障分析

- **事件从未进入酒吧**：市场没有空间站或事件实例已经被 `shownAt` 锁定到另一市场。
    - **解决方案**：检查市场条件和事件是否已经在其他市场展示。
- **同一事件重复注册**：注册入口没有先调用 `hasEventCreator(...)`。
    - **解决方案**：把 `addEventCreator(...)` 放在类型去重判断之后。
- **接受分支没有奖励**：货舱中的 `metals` 少于 100。
    - **解决方案**：补充金属后重新进入事件。
- **拒绝后事件再次出现**：拒绝分支没有通知 `BarEventManager`。
    - **解决方案**：在设置 `done` 后调用 `notifyWasInteractedWith(this)`。

## 总结

酒吧事件由 creator 生成、由 `BarEventManager` 跟踪、由 `BarCMD` 展示，再由 `BarEventDialogPlugin` 转发给事件对象。事件只需要实现市场条件、酒吧文本、选项分支和结束状态；manager 负责活动时长、超时和交互后的回收。接受交易使用货舱数量作为前置条件，拒绝通过 manager 通知结束事件，离开则保留事件等待下一次酒吧刷新。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent`；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent`；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator`；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`；
- `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`；
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`；
- `com.fs.starfarer.api.campaign.CargoAPI`。
