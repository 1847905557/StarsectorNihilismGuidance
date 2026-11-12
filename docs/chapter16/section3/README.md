# 接入酒吧事件

本节把金属交易放进市场酒吧：工人收购 100 金属，支付 5000 信用点，玩家可以接受、拒绝或暂时离开。交易在一次对话中完成，邀请却可能继续留在酒吧中。我们分别处理事件生成、对话结束和活动期限，让暂时离开后的再次交互继续使用原事件。

_本节预期阅读时间：\~32 分钟。_

_本节预期操作时间：\~40 分钟。_

## 学习目标

完成后，您将能够：

- 说明 `BarEventManager`、`creator`、`PortsideBarEvent` 与 `BarCMD` 的职责；
- 注册一个 Java `creator` 并控制事件权重和有效期；
- 让事件按照市场条件出现在酒吧；
- 通过稳定 `optionData` 区分接受、拒绝和离开；
- 用货舱金属和 `credits` 完成一次性交易；
- 区分拒绝结束、离开保留和未交互过期三种状态。

## 前置知识

- [C14-S3] 的生涯实体、市场对象和交互对话入口；
- [C16-S1] 的 `InteractionDialogAPI`、目标对象和关闭语义；
- Java 接口实现、继承和布尔状态；
- `CargoAPI` 的商品数量、移除商品和 `credits` 读写。

## 登记生成器，让管理器创建事件

创建 `src/data/scripts/gearin/campaign/GearInBarEvent.java` 与 `GearInBarEventCreator.java`。事件类实现当前交易，creator 类返回事件并提供权重与期限。在 `GearInModPlugin` 添加对应类及 `BarEventManager` 的 import，向已有 `onGameLoad(...)` 追加局部登记：

```java
BarEventManager manager = BarEventManager.getInstance();
if (!manager.hasEventCreator(GearInBarEventCreator.class)) {
    manager.addEventCreator(new GearInBarEventCreator());
}
```

`getInstance()` 取得存档中的管理器，`hasEventCreator(...)` 按类型查询。已有生成器继续保留，缺失时才添加。**`addEventCreator(...)` 直接追加候选项，重复登记会改变抽取机会。** 因此登记放在生涯入口，事件每次对话只处理自己的状态。

### `creator` 提供抽取参数

管理器在生涯推进中按权重选择可用 creator，调用 `createBarEvent()`，将返回的 `PortsideBarEvent` 放入活动跟踪器与酒吧事件集合。局部创建与权重如下：

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

权重 10 只参与当前候选中的相对抽取，市场显示还要经过酒吧筛选。本例采用普通候选，保留基类默认优先级。活跃期限为 30 生涯日，未处理过期后的冷却为 30 日，通知已交互后的冷却为 60 日，完整 creator 分别返回这三个值。

::: info 活动期限与 creator 冷却分别计时
30 日活动期限跟随生成的事件实例。事件过期或被处理后，creator 再进入对应冷却；冷却期间停止生成下一份事件。
:::

## 在太空港市场显示邀请

玩家进入市场酒吧时，`BarCMD` 从酒吧事件集合取得候选，调用 `shouldShowAtMarket(...)`。本例要求有太空港，并沿用基类 `shownAt` 约束，局部判断如下：

```java
@Override
public boolean shouldShowAtMarket(MarketAPI market) {
    return market != null && market.hasSpaceport() && super.shouldShowAtMarket(market);
}
```

首次实际展示后，`BarCMD` 调用 `wasShownAtMarket(...)`，基类保存该市场。同一实例此后留在这里，所以玩家补充货物后返回同一酒吧，可以继续原邀请。

筛选通过后，用 `addPromptAndOption(...)` 写描述与选项。**这里的 `optionData` 使用事件对象本身**，酒吧从这个对象识别需要交接的事件：

```java
@Override
public void addPromptAndOption(InteractionDialogAPI dialog,
        Map<String, MemoryAPI> memoryMap) {
    dialog.getTextPanel().addPara(
            "A dockworker offers a credit payment for a shipment of refined metals.");
    dialog.getOptionPanel().addOption("Trade 100 metals for 5000 credits", this);
}
```

显示文字提供交易内容，`this` 把选择对应到当前实例。字符串选项会进入酒吧的另一条规则路径，[C16-S5] 使用那种入口接任务；这里让 Java 事件接收玩家的选择。

## 进入事件，重新建立本次选项

选择邀请后，`BarCMD` 创建 `BarEventDialogPlugin` 并接管当前会话，显式初始化。外壳保存原酒吧插件与事件，把初始化和选项回调转发给这份事件；事件结束后，再交回原插件。

事件的初始化只负责建立当前选项集合：

```java
@Override
public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    super.init(dialog, memoryMap);
    done = false;
    noContinue = false;
    options.clearOptions();
    options.addOption("Provide 100 metals", OPTION_ACCEPT);
    options.addOption("Refuse the trade", OPTION_DECLINE);
    options.addOption("Leave for now", OPTION_LEAVE);
}
```

`super.init(...)` 保存这次对话、面板与 Memory map，子类清理旧选项并建立交易分支。**每次进入都将 `done` 与 `noContinue` 重置。** 暂时离开保留事件实例，原对话已经完成，下一次进入要建立新的活动会话；实例中的 `rewardGranted` 则继续表示交易结果。

## 接受时检查货物，再完成交易

接受分支从玩家舰队取得货舱，确认 `metals` 至少为 100。满足条件后扣除货物、增加资金，并通知管理器；局部操作如下：

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

`getCommodityQuantity(...)` 取得当前数量，`removeCommodity(...)` 扣除 100，信用通过独立 `MutableValue` 增加 5000。[C12-S2] 已说明移除不足时可以部分扣除，所以数量判断放在写入前。货物不足只追加说明，交易与事件仍可继续。

成功后设置 `rewardGranted`，本次货物、资金和管理器通知只执行一次。`notifyWasInteractedWith(this)` 移除活动实例，并让对应 creator 进入交互后的冷却。外壳读取 `done=true` 后交还酒吧，当前成功分支保留默认的继续选项，让玩家读完结果再返回。

## 拒绝与暂时离开产生不同结果

拒绝表示邀请已经处理，写入结果后通知管理器并进入 60 日冷却，资金与货物保持原值。局部分支如下：

```java
if (OPTION_DECLINE.equals(optionData)) {
    text.addPara("You refuse the trade and leave the dockworker behind.");
    done = true;
    noContinue = true;
    BarEventManager.getInstance().notifyWasInteractedWith(this);
}
```

暂时离开只结束这次会话，活动事件仍留在管理器中。这个分支没有已交互通知，局部处理如下：

```java
if (OPTION_LEAVE.equals(optionData)) {
    text.addPara("You leave before making a decision.");
    done = true;
    noContinue = true;
}
```

`noContinue=true` 让外壳直接重建酒吧选项，省去继续按钮。它控制会话收尾，是否开始交互后冷却则取决于管理器通知。所以两种离开都能返回酒吧，后续邀请是否保留却不同。

## 让活动期限负责未处理的邀请

本例 `shouldRemoveEvent()` 沿用默认的 `false`，期限由管理器的 `active` 跟踪器处理。30 日耗尽后，实例离开活动集合，管理器回收对应关系并开始普通的 30 日冷却。事件自己的 `advance(...)` 与移除判断可用于额外状态，本次沿用管理器时限即可。

**完成对话、完成交易与结束活动分别记录。** `done` 交还当前对话，`rewardGranted` 保存交易已经写入，活动与冷却跟踪器控制邀请还能存在多久。暂时离开后重入，要重置会话状态，交易状态与剩余活动期限则继续保留。

### 酒吧接入速查表

| 对象或方法                | 作用                           | 本例中的结果               |
| ------------------------- | ------------------------------ | -------------------------- |
| `BarEventManager`         | 抽取、跟踪和回收事件           | 管理 `active` 与 `timeout` |
| `BaseBarEventCreator`     | 提供事件实例和抽取参数         | 生成 `GearInBarEvent`      |
| `shouldShowAtMarket`      | 判断市场是否显示事件           | 仅太空港市场显示           |
| `addPromptAndOption`      | 写酒吧文本和选项               | `optionData` 使用事件实例  |
| `notifyWasInteractedWith` | 移除已处理事件并设置冷却       | 接受、拒绝调用             |
| `BarEventDialogPlugin`    | 在 `BarCMD` 与事件之间转发回调 | 结束后交还 `BarCMD`        |

## 完整最小例

两个类保存到前述路径，生涯入口登记 `creator`，酒吧再取得对应事件。

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
        done = false;
        noContinue = false;
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

前置数据：启用包含两个 Java 类和注册片段的 MOD，进入一个有太空港的市场酒吧，并准备一份金属不足 100 的存档和一份至少有 100 金属的存档。

1. 保存两类和生涯登记，构建重启，载入存档；重复读档后核对 `creator` 仍只有一份。
2. 进入有太空港的市场酒吧，确认出现 "Trade 100 metals for 5000 credits"。
3. 在金属不足的存档中选择接受，确认文本提示需要 100 金属，货舱和 `credits` 数值不变。
4. 在至少有 100 金属的存档中选择接受，确认金属减少 100、`credits` 增加 5000，事件结束并离开活动列表。
5. 观察交易成功后回到酒吧，当前事件已处理，比较货舱与信用仅发生一次本次交易。
6. 在另一份事件实例中选择拒绝，确认事件结束并进入交互后 `timeout`。
7. 在另一份事件实例中选择暂时离开，再从同一市场进入，确认重新建立选项；补足金属后完成接受。
8. 等待未交互事件的 `active` 时长耗尽，确认事件从活动列表移除并进入普通 `timeout`。
9. 返回 `BarCMD` 后确认酒吧选项恢复，事件对话没有残留选项。

预期结果：

- `creator` 只注册一次；
- 太空港市场显示事件；
- 金属不足不会完成接受分支；
- 接受成功只扣除 100 金属并增加 5000 `credits`；
- 拒绝结束事件并触发交互后冷却；
- 离开结束当前对话但保留事件；
- 未交互过期事件进入普通冷却；
- 返回酒吧后回调归属恢复到 `BarCMD`。

## 故障分析

- **事件从未进入酒吧**：市场没有太空港或事件实例已经被 `shownAt` 锁定到另一市场。
    - **解决方案**：检查市场条件和事件是否已经在其他市场展示。

- **同一事件重复注册**：注册入口没有先调用 `hasEventCreator(...)`。
    - **解决方案**：把 `addEventCreator(...)` 放在类型去重判断之后。

- **接受分支没有奖励**：货舱中的 `metals` 少于 100。
    - **解决方案**：补充金属后重新进入事件。

- **拒绝后事件再次出现**：拒绝分支没有通知 `BarEventManager`。
    - **解决方案**：在设置 `done` 后调用 `notifyWasInteractedWith(this)`。

## 总结

creator 生成金属交易，酒吧按市场筛选并展示邀请，外壳将当前会话交给事件。接受先确认货物再完成资金写入，接受与拒绝通知管理器进入冷却，暂时离开则保留原实例。

会话与活动有各自的寿命，交易结果也单独保存。重入恢复对话状态，活动期限继续推进，玩家才能在暂时离开、正式拒绝与交易完成之后得到对应结果。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent`：本页事件的声明类型，`createBarEvent` 返回它并被 `BarCMD` 识别；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent`：`GearInBarEvent` 的基类，`init` 已把对话与面板引用保存到受保护字段；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator`：`GearInBarEventCreator` 的基类，只负责生成事件实例和抽取参数；
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：从存档取得的管理器，负责 `creator` 注册、活动跟踪和交互后冷却；
- `com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin`：在 `BarCMD` 与事件对象之间转发初始化和选项回调；
- `com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD`：酒吧场景入口，检查市场显示条件并把回调归属交还酒吧；
- `com.fs.starfarer.api.campaign.CargoAPI`：玩家货舱接口，接受分支用它核对 `metals` 数量并写入 `credits`。
