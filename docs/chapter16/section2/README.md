# 实现对话插件

[C16-S1] 的检查结束之后，要回到星图再点一次 Foundry 才能停靠。少了这一下，检查与停靠就是一段连贯的经过：检查员盖完章，港口照常开门。

原版自己处理这种事情的办法，是在同一个对话窗口里换一个插件接着往下走。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C16-S1] 的码头检查对话，本节只改其中 "继续" 的处理；
- [C15-S3] 中 Foundry 停靠时走的 `MarketPostOpen` 规则；
- [C14-S3] 中原版生涯插件返回的 `RuleBasedInteractionDialogPluginImpl`。

## 一个窗口，换一个插件

对话窗口与驱动它的插件是分开的。窗口还开着，插件可以换：

```java
private void continueDocking() {
    RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
    dialog.setPlugin(plugin);
    plugin.init(dialog);
}
```

`setPlugin(...)` 让窗口之后的选项、按键都交给新插件处理，`init(dialog)` 让新插件从头开始。原版 `SDSevenSons` 与 `DwellerCMD` 在战斗或剧情结束后接回规则对话，用的都是这两行。

`RuleBasedInteractionDialogPluginImpl` 就是 [C14-S3] 里原版生涯插件为停靠返回的那个插件，一切走规则表的对话都由它驱动。它在 `init(...)` 时按交互目标发起 `OpenInteractionDialog`，接下来 [C15-S3] 的 Foundry 港务、原版的交易菜单，全都按正常停靠的流程出现。

**两行的顺序不能反。** 先 `init(...)` 再 `setPlugin(...)`，新插件初始化时窗口还挂着旧插件，它添加的选项会交给旧插件处理。

## 改动只有一处

"继续" 从关闭对话改为调用 `continueDocking()`：

```java
case CONTINUE:
    continueDocking();
    break;
```

其余部分与 [C16-S1] 完全相同。检查留下的正文不会被清掉，玩家往上翻，还能看到罚款与盖章那几行；新的停靠文字接在下面。

"离开" 仍然直接关闭对话，玩家掉头走了，没有停靠可接。

## 插件接口里的其他方法

码头检查用不上的几个方法，什么时候才需要写：

| 方法 | 需要的时候 |
| ---- | ---------- |
| `advance(amount)` | 对话开着时要随时间变化，比如倒计时 |
| `backFromEngagement(result)` | 对话里能开打，打完要回到对话 |
| `getMemoryMap()` | 对话里要执行规则，需要提供各块 Memory |
| `getContext()` | 对话需要向外提供一个上下文对象 |
| `optionMousedOver(...)` | 鼠标悬停选项时要有反应 |

检查对话自己不执行规则，`getMemoryMap()` 返回 `null` 即可。交给规则对话之后，Memory 由 `RuleBasedInteractionDialogPluginImpl` 自己准备。

## 完整的检查对话

`src/data/scripts/gearin/campaign/GearInDockInspectionDialog.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;

/** Foundry 的码头检查：玩家货舱里有回火合金时先过这一关，处理完后交回原版的停靠对话。 */
public class GearInDockInspectionDialog implements InteractionDialogPlugin {

    /** 市场 Memory：近期检查过，期间不再拦截。 */
    public static final String INSPECTED_KEY = "$GearIn_inspected";

    private static final float INSPECTED_DAYS = 30f;

    /** 每件回火合金的罚款。 */
    private static final int FINE_PER_UNIT = 150;

    private enum Option {
        PAY_FINE,
        SURRENDER,
        LEAVE,
        CONTINUE
    }

    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private int quantity;
    private int fine;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        text = dialog.getTextPanel();
        options = dialog.getOptionPanel();
        dialog.getVisualPanel().showImagePortion("illustrations", "cargo_loading", 640, 400, 0, 0, 480, 300);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        quantity = (int) cargo.getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID);
        fine = quantity * FINE_PER_UNIT;

        text.addPara("港务的检查船贴了上来。扫描结果出来得很快：您的货舱里有 " + quantity
                + " 件回火合金，而它们没有一件是从 Foundry 的仓库里买的。");
        text.addPara("检查员的意思很清楚：要么按每件 " + FINE_PER_UNIT + " 星币交罚款，要么把货留下。");
        options.addOption("交罚款 (" + fine + " 星币)", Option.PAY_FINE);
        if (cargo.getCredits().get() < fine) {
            options.setEnabled(Option.PAY_FINE, false);
            options.setTooltip(Option.PAY_FINE, "星币不足。");
        }
        options.addOption("交出回火合金", Option.SURRENDER);
        options.addOption("离开", Option.LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionText != null) {
            dialog.addOptionSelectedText(optionData);
        }
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        switch ((Option) optionData) {
            case PAY_FINE:
                cargo.getCredits().subtract(fine);
                AddRemoveCommodity.addCreditsLossText(fine, text);
                text.addPara("检查员收了钱，在您的货单上盖了章。");
                passInspection();
                break;
            case SURRENDER:
                cargo.removeCommodity(GearInAlloyCargo.COMMODITY_ID, quantity);
                AddRemoveCommodity.addCommodityLossText(GearInAlloyCargo.COMMODITY_ID, quantity, text);
                text.addPara("合金被搬上了检查船，没有人多看您一眼。");
                passInspection();
                break;
            case LEAVE:
                dialog.dismiss();
                break;
            case CONTINUE:
                continueDocking();
                break;
        }
    }

    /** 记下这次检查，换成一个 "继续" 选项。 */
    private void passInspection() {
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        market.getMemoryWithoutUpdate().set(INSPECTED_KEY, true, INSPECTED_DAYS);
        options.clearOptions();
        options.addOption("继续", Option.CONTINUE);
    }

    /** 在同一个对话窗口里交给原版的规则对话，按正常流程停靠。 */
    private void continueDocking() {
        RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，带着回火合金停靠 Nipher Foundry。

- 交罚款或交出合金后选择 "继续"，对话窗口不关闭，下方接着出现 Foundry 港务念泊位号的那段话，随后是原版的交易菜单；
- 往上翻，检查的正文仍然在；
- 选择 "离开"，对话关闭。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **选择 "继续" 后选项点不动**：`setPlugin(...)` 与 `init(...)` 顺序写反了。
    - **解决方案**：先 `setPlugin(...)`，再 `init(dialog)`。

- **继续之后只有一片空白**：交互目标不是 Foundry，规则表里没有接得住的规则。
    - **解决方案**：确认插件只在 `GearIn_foundry` 上返回检查对话。

## 建议参考项

- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：`setPlugin(...)` 与 `getPlugin()`。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：驱动规则对话的插件。
- `com.fs.starfarer.api.impl.campaign.rulecmd.DwellerCMD`：原版在同一窗口切换插件的写法之一。
