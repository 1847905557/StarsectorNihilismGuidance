# 组织生涯交互对话

[C12-S2] 把回火合金定成了垄断物资，公开市场不交易它，黑市不受此限。从黑市买来的合金，带回 Foundry 的港口时，港务不会装作没看见。

[C14-S3] 的拒绝停靠只有一句话。码头检查要多几步：说明情况，给出几条路，按玩家选的那一条处理，对话窗口才算真正派上了用场。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C14-S3] 的生涯插件与拒绝停靠对话，本节在同一个插件里加一个分支；
- [C12-S2] 的 `GearInAlloyCargo.COMMODITY_ID` 与玩家货舱的读写；
- [C15-S4] 中用过的 `AddRemoveCommodity` 文字提示；
- [C10-S1] 的市场 Memory 与限时值。

## 什么时候拦下玩家

[C14-S3] 的生涯插件再加一个分支：不记仇，但货舱里有回火合金，Foundry 最近也没检查过，就换成码头检查。

```java
MarketAPI market = interactionTarget.getMarket();
boolean carrying = Global.getSector().getPlayerFleet().getCargo()
        .getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID) > 0f;
if (carrying && market != null
        && !market.getMemoryWithoutUpdate().getBoolean(GearInDockInspectionDialog.INSPECTED_KEY)) {
    return new PluginPick<InteractionDialogPlugin>(new GearInDockInspectionDialog(), PickPriority.MOD_SPECIFIC);
}
```

记仇的判断排在前面。拒绝停靠的人连港都进不了，也就谈不上检查。

"最近检查过" 记在 Foundry 市场的 Memory 上，30 天后过期。检查只属于这座港口，换一座齿轮工业的市场，规矩可以另写；30 天之内再带着合金回来，港务就当没看见。不加这个期限，玩家每次停靠都要被拦一次，那就不是检查，是骚扰了。

插件里只放判断，检查的过程全在对话类里。

## 对话窗口的三块面板

`init(dialog)` 收到的对话窗口有三块常用的面板：

| 面板 | 取得方式 | 用途 |
| ---- | -------- | ---- |
| 文字 | `dialog.getTextPanel()` | 一段一段往下写的正文 |
| 选项 | `dialog.getOptionPanel()` | 底部的按钮 |
| 画面 | `dialog.getVisualPanel()` | 左上方的插图或肖像 |

三块面板在 `init(...)` 里取一次，存在字段里反复用。画面借原版的装货插图：

```java
dialog.getVisualPanel().showImagePortion("illustrations", "cargo_loading", 640, 400, 0, 0, 480, 300);
```

插图键取自原版 `settings.json` 的 `graphics.illustrations`。后面几个数字是图片尺寸与截取的范围，照抄原版教程对话 `TutorialRespawnDialogPluginImpl` 的写法即可。

## 摆出几条路

扫描结果写进正文，罚款按件数算，每件 150 星币：

```java
quantity = (int) cargo.getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID);
fine = quantity * FINE_PER_UNIT;
```

[C12-S2] 定的基础价格是 180，罚款压在它下面一点。交罚款比交出合金划算一些，可也没划算到让人想故意带着合金来交钱。

三个选项：交罚款、交出合金、离开。钱不够时，交罚款这一项变灰，悬停说明原因：

```java
options.addOption("交罚款 (" + fine + " 星币)", Option.PAY_FINE);
if (cargo.getCredits().get() < fine) {
    options.setEnabled(Option.PAY_FINE, false);
    options.setTooltip(Option.PAY_FINE, "星币不足。");
}
```

**做不到的选项变灰，不要藏起来。** 藏起来，玩家不知道还有交钱这条路；变灰并说明原因，玩家知道下次带够钱再来。

## 按选项处理

玩家选中一个选项，`optionSelected(...)` 收到当初传进去的枚举值：

```java
if (optionText != null) {
    dialog.addOptionSelectedText(optionData);
}
switch ((Option) optionData) {
    case PAY_FINE:
        // 扣钱、提示、通过检查
    case SURRENDER:
        // 移除合金、提示、通过检查
    case LEAVE:
        dialog.dismiss();
        break;
}
```

`addOptionSelectedText(...)` 把玩家点过的选项文字留在正文里，原版的对话都这样做，读回去能看出这段对话是怎么走过来的。

扣钱与移除货物之后，分别用 `addCreditsLossText(...)` 与 `addCommodityLossText(...)` 留一行原版样式的提示。两条路最后都走 `passInspection()`：在市场 Memory 上写下检查标记，把选项换成一个 "继续"。

"离开" 直接关闭对话，不写检查标记，下次停靠还要重新接受检查。

## 处理完之后

这一版的 "继续" 只是关闭对话：

```java
case CONTINUE:
    dialog.dismiss();
    break;
```

玩家回到星图，再点一次 Foundry。检查标记已经写下，插件不再拦截，这一次出现的是原版的停靠对话。能用，只是多点了一次。[C16-S2] 让检查完之后直接在同一个窗口里接上原版的停靠。

## 完整的生涯插件与检查对话

`src/data/scripts/gearin/campaign/GearInCampaignPlugin.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 齿轮工业的生涯插件：记仇时 Foundry 拒绝停靠；货舱里有回火合金时，先过码头检查。 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId() {
        return "GearIn_campaign_plugin";
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (!GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
            return null;
        }
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
            return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
        }
        MarketAPI market = interactionTarget.getMarket();
        boolean carrying = Global.getSector().getPlayerFleet().getCargo()
                .getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID) > 0f;
        if (carrying && market != null
                && !market.getMemoryWithoutUpdate().getBoolean(GearInDockInspectionDialog.INSPECTED_KEY)) {
            return new PluginPick<InteractionDialogPlugin>(new GearInDockInspectionDialog(), PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}
```

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
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;

/** Foundry 的码头检查：玩家货舱里有回火合金时先过这一关，处理完后关闭对话。 */
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
                dialog.dismiss();
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

执行 `gradle jar`，重启游戏，**新建一份存档**。在别处的黑市买进一些回火合金，或者用控制台执行 `addcommodity GearIn_tempered_alloy 20`。

- 停靠 Nipher Foundry，出现码头检查，画面为装货插图，正文写明合金件数与每件 150 星币的罚款；
- 星币不足时，"交罚款" 一项变灰，悬停显示 "星币不足"；
- 选择 "交出回火合金"，货舱中的合金被移除，正文下方出现原版样式的失去货物提示；
- 选择 "继续" 关闭对话，再点 Foundry，出现原版的停靠对话；
- 30 天内带着合金再来，不再被检查。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **带着合金停靠，没有出现检查**：插件没有登记，或者市场 Memory 里已有检查标记。
    - **解决方案**：核对 [C14-S3] 的 `registerPlugin(...)`；用控制台清除 `$GearIn_inspected` 后再试。

- **每次停靠都被检查**：检查标记写在了别的 Memory 上，或者没有写入。
    - **解决方案**：写在交互目标的市场 Memory 上，与插件读取的位置一致。

- **点击选项后报类型转换错误**：`addOption(...)` 传入的数据不是同一个枚举。
    - **解决方案**：添加与比对都使用 `Option` 枚举。

- **"交罚款" 一直是灰的**：判断时用错了数量，或者罚款按错误的件数计算。
    - **解决方案**：先读货舱数量，再计算罚款与比较星币。

## 建议参考项

- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：三块面板与 `addOptionSelectedText(...)`。
- `com.fs.starfarer.api.campaign.OptionPanelAPI`：`setEnabled(...)`、`setTooltip(...)` 与 `clearOptions()`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity`：原版的星币与货物增减提示。
- `com.fs.starfarer.api.impl.campaign.tutorial.TutorialRespawnDialogPluginImpl`：插图与枚举选项的原版写法。
- `data/config/settings.json`：`graphics.illustrations` 中的原版插图。
