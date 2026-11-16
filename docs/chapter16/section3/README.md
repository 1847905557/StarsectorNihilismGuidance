# 接入酒吧事件

Foundry 的仓库对回火合金看得很紧，可再紧的仓库，清点时也总会多出来几件。酒吧里坐着的那个码头工人，就是来处理这几件的。

酒吧事件就是这种东西：不是任务，也不留下什么后续，只是玩家在某个地方坐一坐，偶尔能碰上的一桩小事。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C12-S2] 的 `GearInAlloyCargo.give(...)`，它按剩余货舱空间加入回火合金；
- [C16-S1] 的码头检查，带着这批合金回 Foundry 的人会碰上它；
- [C16-S1] 中选项变灰、`AddRemoveCommodity` 提示的写法。

## 事件与创建器

原版的酒吧事件由两个类组成：

| 类 | 负责 |
| -- | ---- |
| 事件，继承 `BaseBarEvent` | 在哪个酒吧出现，玩家点进去之后说什么 |
| 创建器，继承 `BaseBarEventCreator` | 交给原版的酒吧事件管理器，由它决定什么时候生成一个事件 |

管理器隔一段时间从登记过的创建器里按权重挑一个，生成一个事件放进全局的酒吧列表。每个酒吧打开时，再逐个询问列表里的事件愿不愿意在这里出现。所以一个酒吧事件不会一直在，有时要过一阵才碰得上，这正是原版酒吧的感觉。

创建器的默认参数取自 `BaseBarEventCreator`：权重 10，生成后存在 30 到 40 天，过期后隔 30 到 40 天才可能再次生成，玩家处理过之后隔 60 到 90 天。码头工人这桩买卖不必经常出现，默认值就很合适。

## 只在 Foundry 出现

`shouldShowAtMarket(...)` 决定事件在哪个市场的酒吧里出现：

```java
@Override
public boolean shouldShowAtMarket(MarketAPI market) {
    if (!super.shouldShowAtMarket(market)) {
        return false;
    }
    return GearInFoundryGenerator.MARKET_ID.equals(market.getId());
}
```

父类会记住事件第一次在哪个市场出现，之后不再出现在别处，先保留父类的判断。

## 在酒吧里露个脸

酒吧的列表里，每个事件只占一段描述和一个选项，由 `addPromptAndOption(...)` 写入：

```java
dialog.getTextPanel().addPara("角落里有个穿着码头工装的人，面前的杯子早就空了，眼睛却一直往门口瞟。");
dialog.getOptionPanel().addOption("走过去坐下", this);
```

**选项的数据要传事件自己。** 原版的酒吧对话靠这个数据认出玩家选的是哪个事件，再调用它的 `init(...)`。这一点与 [C16-S1] 的枚举选项不同。

## 事件里的对话

玩家走过去之后，`init(...)` 写出报价。父类已经把文字与选项面板存在 `text`、`options` 字段里，直接用：

```java
text.addPara("他压低声音：仓库清点时 \"多出来\" " + QUANTITY + " 件回火合金，每件 " + UNIT_PRICE
        + " 星币，一共 " + total + "。不走仓库的账，也不开单据。");
options.addOption("买下来", Option.BUY);
```

10 件，每件 100 星币，比 [C12-S2] 的基础价格 180 便宜不少。便宜是有代价的：货装上船，玩家什么时候带回 Foundry，什么时候就要过 [C16-S1] 的码头检查，按每件 150 交罚款。在这里贪了便宜又不打算跑远的人，回港时会把差价原样吐回去。

`optionSelected(...)` 处理选择。买下时按实际装下的数量收钱，货舱满了就不收：

```java
int bought = GearInAlloyCargo.give(QUANTITY);
if (bought <= 0) {
    text.addPara("他看了一眼您的货单：货舱已经满了，这笔生意改天再说。");
    done = true;
    return;
}
```

## 结束事件

把 `done` 设为 `true`，原版的酒吧对话就知道事件结束了，回到酒吧菜单。成交之后再调用一次：

```java
BarEventManager.getInstance().notifyWasInteractedWith(this);
```

它把事件从酒吧列表里移除，并让创建器进入那段 60 到 90 天的冷却。只拒绝不成交的话不调用它，事件仍留在酒吧列表里，再点开还能继续这次对话。

## 登记创建器

在 `GearInModPlugin.onGameLoad(...)` 中加入：

```java
BarEventManager bar = BarEventManager.getInstance();
if (!bar.hasEventCreator(GearInDockworkerBarEventCreator.class)) {
    bar.addEventCreator(new GearInDockworkerBarEventCreator());
}
```

酒吧事件管理器随存档保存，登记过的创建器也在其中。原版在每次读档时都这样检查一遍再补登记，新档与旧存档都能用同一段代码。

## 完整的事件与创建器

`src/data/scripts/gearin/campaign/GearInDockworkerBarEvent.java`：

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
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;

/** Foundry 酒吧里的码头工人：私下低价卖出一小批回火合金。只在 Foundry 出现。 */
public class GearInDockworkerBarEvent extends BaseBarEvent {

    private static final int QUANTITY = 10;

    private static final int UNIT_PRICE = 100;

    private enum Option {
        BUY,
        DECLINE
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) {
            return false;
        }
        return GearInFoundryGenerator.MARKET_ID.equals(market.getId());
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara("角落里有个穿着码头工装的人，面前的杯子早就空了，眼睛却一直往门口瞟。");
        dialog.getOptionPanel().addOption("走过去坐下", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        int total = QUANTITY * UNIT_PRICE;
        text.addPara("他压低声音：仓库清点时 \"多出来\" " + QUANTITY + " 件回火合金，每件 " + UNIT_PRICE
                + " 星币，一共 " + total + "。不走仓库的账，也不开单据。");
        options.addOption("买下来", Option.BUY);
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < total) {
            options.setEnabled(Option.BUY, false);
            options.setTooltip(Option.BUY, "星币不足。");
        }
        options.addOption("不感兴趣", Option.DECLINE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.BUY) {
            int bought = GearInAlloyCargo.give(QUANTITY);
            if (bought <= 0) {
                text.addPara("他看了一眼您的货单：货舱已经满了，这笔生意改天再说。");
                done = true;
                return;
            }
            int cost = bought * UNIT_PRICE;
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.getCredits().subtract(cost);
            AddRemoveCommodity.addCreditsLossText(cost, text);
            text.addPara("货会在您离港前装上船。他临走前提醒了一句：带着这些东西回 Foundry 的时候，记得自己想好怎么跟港务解释。");
            BarEventManager.getInstance().notifyWasInteractedWith(this);
        } else {
            text.addPara("他耸耸肩，重新盯着门口。");
        }
        done = true;
    }
}
```

`src/data/scripts/gearin/campaign/GearInDockworkerBarEventCreator.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/** 码头工人酒吧事件的创建器：交给原版的酒吧事件管理器调度。 */
public class GearInDockworkerBarEventCreator extends BaseBarEventCreator {

    @Override
    public PortsideBarEvent createBarEvent() {
        return new GearInDockworkerBarEvent();
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。酒吧事件按权重随机生成，可以多推进几天、反复进出 Foundry 的酒吧。

- Foundry 的酒吧里出现码头工人的描述与 "走过去坐下"，其他市场的酒吧里没有；
- 进去之后报价为 10 件、每件 100 星币，星币不足时 "买下来" 变灰；
- 买下后货舱增加 10 件回火合金，扣去 1000 星币，回到酒吧菜单时码头工人已经不在；
- 离港再回来停靠，碰上 [C16-S1] 的码头检查。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **怎么也碰不上码头工人**：创建器没有登记，或者事件刚好处在冷却中。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记；多推进一段时间再看。

- **点了 "走过去坐下" 没有反应**：`addOption(...)` 的数据不是事件自己。
    - **解决方案**：数据传 `this`。

- **对话结束后卡在事件里**：没有把 `done` 设为 `true`。
    - **解决方案**：每个结束的分支都设置 `done`。

- **其他市场也出现了码头工人**：`shouldShowAtMarket(...)` 没有比对市场 ID。
    - **解决方案**：只在 `GearIn_market_foundry` 返回 `true`。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent`：事件的基类与 `done` 字段。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator`：创建器的默认时长与权重。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager`：创建器的登记与调度。
- `com.fs.starfarer.api.impl.campaign.intel.bar.events.LuddicFarmerBarEvent`：原版在酒吧里低价卖货的事件。
- `com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`：原版每次读档补登记创建器的写法。
