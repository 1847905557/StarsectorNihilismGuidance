# 组织生涯交互对话

本节为 Foundry 建立一次检查对话，显示目标、记录检查结果，并提供离开与取消。目标和面板由同一会话提供，插件处理玩家选择；完成这次会话后，再说明插件交接、布局与专用界面怎样继续使用当前上下文。

_本节预期阅读时间：\~28 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 使用 `showInteractionDialog(plugin, target)` 启动交互会话；
- 从 `getInteractionTarget()` 取得会话实际持有的 `SectorEntityToken`；
- 读写目标 Memory 中的 `$GearIn_dialog_target`；
- 从同一 `InteractionDialogAPI` 取得并协作使用三个面板；
- 用稳定 `optionData` 区分业务分支；
- 用 `dismiss()`、`dismissAsCancel()`、`checked` 和 `closed` 表达会话结果。

## 前置知识

- [C10-S4] 的生涯实体与目标 Memory；
- [C14-S3] 的 `BaseCampaignPlugin` `picker` 选取和桥接对象；
- Java 接口实现、对象字段和布尔状态；
- `MemoryAPI` 的键值读写和 `getMemoryWithoutUpdate()` 的读取语义。

## 从 Foundry 打开检查对话

保存 `src/data/scripts/gearin/campaign/GearInConversationDialog.java`。在 [C14-S3] 的 `GearInCampaignPlugin.pickInteractionDialogPlugin(...)` 已命中标记后，把返回的对话实例改为：

```java
return new PluginPick<>(new GearInConversationDialog(), PickPriority.MOD_SPECIFIC);
```

Foundry 主实体沿 [C14-S3] 的标记继续作为目标。重启后从地图交互，生涯插件选出 `GearInConversationDialog`，引擎建立会话并调用 `init(dialog)`。

需要由 Java 功能主动打开对话时，也可以直接传入插件与实体。以下局部入口使用 `showInteractionDialog(...)`：

```java
boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(
        new GearInConversationDialog(), target);
```

`shown` 表示是否接受了打开请求，已有其他对话或正在切换状态时可以返回 `false`。地图选择与直接调用两种入口都把插件交给实际会话，插件随后从 `InteractionDialogAPI` 取得当前目标和面板。

**初始化使用会话提供的目标与面板。** 入口选择实现，`init(...)` 建立这次会话的状态，后续选项与返回回调也继续使用这份状态。

## 确认本次会话的目标

在 `init(...)` 保存 `dialog`，再从 `getInteractionTarget()` 取得当前实体。目标不可用时，结束本次检查对话，局部判断如下：

```java
SectorEntityToken target = dialog.getInteractionTarget();
if (target == null) {
    dialog.dismissAsCancel();
    return;
}
```

Foundry 的目标准备代码使用下面的局部写入，把标记保存在实体自身：

```java
target.getMemoryWithoutUpdate().set("$GearIn_dialog_target", true);
```

初始化从实际会话目标读取，直接调用入口也采用相同条件：

```java
boolean marked = target.getMemoryWithoutUpdate()
        .getBoolean("$GearIn_dialog_target");
```

标记为假或缺席时，完整类记录原因并取消；成立后再建立选项。生涯 picker 筛选地图请求，插件也支持直接传入目标，所以初始化负责当前检查的适用条件。这里用 `getMemoryWithoutUpdate()` 读取已有布尔值，事实刷新仍按 [C14-S3] 的另一个入口处理。

## 建立检查内容与选项

文本面板写目标 ID，选项面板建立检查、离开与取消，视觉面板显示实体提供的交互图像。以下局部片段从同一 `dialog` 取得三者：

```java
TextPanelAPI text = dialog.getTextPanel();
OptionPanelAPI options = dialog.getOptionPanel();
VisualPanelAPI visual = dialog.getVisualPanel();

text.addPara("Target ID: " + target.getId());
options.clearOptions();
options.addOption("Continue inspection", "CHECK");
options.addOption("Leave", "LEAVE");
options.addOption("Cancel", "CANCEL");
dialog.setOptionOnEscape("Cancel", "CANCEL");

if (target.getCustomInteractionDialogImageVisual() != null) {
    visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
} else {
    dialog.hideVisualPanel();
}
```

文本与视觉从当前目标取得资料，选项的业务结果由插件字段处理。图像存在时调用 `showImageVisual(...)`，否则隐藏视觉区域，普通 `SectorEntityToken` 也就能沿同一入口使用。

### 文本显示当前目标与结果

目标 ID 写入文本后，可以与实际实体对应。以下局部行来自前面的初始化：

```java
text.addPara("Target ID: " + target.getId());
```

检查完成时继续向同一面板追加结果，是否已经完成由 `checked` 保存。面板提供显示，插件保存业务状态，重新进入这次回调时才能决定是否再次写入。

### 选项面板区分显示和分支键

`clearOptions()` 清理旧集合，`addOption(...)` 分别传入显示文字与业务数据。以下局部片段保留三个稳定键，改动显示文字也能继续进入原分支：

```java
options.clearOptions();
options.addOption("Continue inspection", "CHECK");
options.addOption("Leave", "LEAVE");
options.addOption("Cancel", "CANCEL");
dialog.setOptionOnEscape("Cancel", "CANCEL");
```

玩家看到 `optionText`，`optionSelected(...)` 则按 `optionData` 选择动作。ESC 提交 `CANCEL`，与取消按钮共用分支；启用、删除与提示由 `OptionPanelAPI` 提供，[C16-S2] 再展开这些操作。

## 记录一次检查，再结束会话

选择检查时，先确认会话仍活动，再按 `checked` 判断是否已写入结果。局部处理如下：

```java
if (closed) {
    return;
}
if (OPTION_CHECK.equals(optionData) && !checked) {
    checked = true;
    dialog.getTextPanel().addPara("The target responds to the inspection.");
}
```

**检查完成与会话关闭分别保存。** 检查后玩家还可以继续停留，`checked` 为真，`closed` 仍为假；离开后两者可以同时为真。关闭分支先写 `closed`，再调用对应结束方法：

```java
if (OPTION_LEAVE.equals(optionData)) {
    closed = true;
    dialog.dismiss();
    return;
}
if (OPTION_CANCEL.equals(optionData)) {
    closed = true;
    dialog.dismissAsCancel();
}
```

`dismiss()` 表示正常离开，`dismissAsCancel()` 使用取消结果。两者负责会话关闭，已完成的检查结果是否保留则由当前功能决定，本例单独记录这个结果。

::: info 关闭请求与画面消失之间仍有界面推进
`dismiss()` 请求关闭后，界面可以等待文本滚动与淡入完成。插件先设置 `closed`，后续回调据此结束业务写入，关闭过程也能继续完成。
:::

## 交接当前插件

需要让另一份实现继续当前会话时，用 `getPlugin()` 确认接收者，再用 `setPlugin(...)` 替换。目标与面板继续属于当前对话，新插件的字段则另行建立。**替换不会自动调用 `init(...)`**，局部交接因此显式初始化：

```java
InteractionDialogPlugin current = dialog.getPlugin();
if (current == this) {
    dialog.setPlugin(nextPlugin);
    nextPlugin.init(dialog);
}
```

随后选项回调交给新插件。需要延续的检查结果或其他字段，由交接方传递，目标和面板并没有代替这些业务数据。[C16-S3] 的酒吧事件会采用当前对话上的插件交接。

## 调整会话显示层

布局作用于当前会话的显示区域。文本宽高、偏移、提示、透明度与背景暗度可以分别设置，以下局部片段给出一组观察参数：

```java
dialog.setTextWidth(560f);
dialog.setTextHeight(380f);
dialog.setXOffset(24f);
dialog.setYOffset(12f);
dialog.setPromptText("Inspection workspace");
dialog.setOpacity(0.96f);
dialog.setBackgroundDimAmount(0.65f);
```

这些设置保留目标与插件状态，`showTextPanel()`、`hideTextPanel()` 和 `hideVisualPanel()` 控制可见区域。验证时观察文本、选项是否遮挡，以及偏移后的内容是否仍在屏幕内，尺寸按实际阅读区域选择。

## 在当前会话中打开专用界面

已经拥有奖励 Cargo 时，`showLoot(...)` 可以把它交给战利品界面，结束结果通过相应监听器接回。当前插件再根据结果安排后续文字和选项。

货物、舰队成员、产业、实体与生产方案分别用 `showCargoPickerDialog(...)`、`showFleetMemberPickerDialog(...)`、`showIndustryPicker(...)`、`showCampaignEntityPicker(...)` 和 `showCustomProductionPicker(...)` 选择。回调提供选中的对象，对话据此继续流程并重建需要的内容。

`showCustomDialog(...)`、`showCustomVisualDialog(...)`、`showCommDirectoryDialog(...)` 和 `makeOptionOpenCore(...)` 接到自定义、目录或核心界面。各入口按自己的流程返回，业务字段继续保留，需要交接插件时确认当前 `getPlugin()`，再安排返回后的面板内容。

`startBattle(...)` 开始生涯战斗，结果通过 `backFromEngagement(...)` 返回。完整类在返回回调检查 `closed`，活动会话再追加说明；实际功能根据战斗结果决定任务动作，关闭与检查状态仍由各自分支处理。

## 完整最小例

保存下列类，`picker` 选择采用前面的替换片段。

::: details 完整成品：GearInConversationDialog

```java
package data.scripts.gearin.campaign;

import java.util.Collections;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

public class GearInConversationDialog implements InteractionDialogPlugin {

    private static final Logger LOG = Global.getLogger(GearInConversationDialog.class);
    private static final String TARGET_KEY = "$GearIn_dialog_target";
    private static final String OPTION_CHECK = "CHECK";
    private static final String OPTION_LEAVE = "LEAVE";
    private static final String OPTION_CANCEL = "CANCEL";

    private InteractionDialogAPI dialog;
    private boolean checked;
    private boolean closed;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null) {
            LOG.info("GearIn conversation cancelled: target unavailable");
            closed = true;
            dialog.dismissAsCancel();
            return;
        }

        TextPanelAPI text = dialog.getTextPanel();
        if (!target.getMemoryWithoutUpdate().getBoolean(TARGET_KEY)) {
            LOG.info("GearIn conversation cancelled: unmarked target=" + target.getId());
            closed = true;
            dialog.dismissAsCancel();
            return;
        }

        text.addPara("Target ID: " + target.getId());
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Continue inspection", OPTION_CHECK);
        options.addOption("Leave", OPTION_LEAVE);
        options.addOption("Cancel", OPTION_CANCEL);
        dialog.setOptionOnEscape("Cancel", OPTION_CANCEL);
        VisualPanelAPI visual = dialog.getVisualPanel();
        if (target.getCustomInteractionDialogImageVisual() != null) {
            visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
        } else {
            dialog.hideVisualPanel();
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (closed) {
            return;
        }
        if (OPTION_CHECK.equals(optionData)) {
            if (!checked) {
                checked = true;
                dialog.getTextPanel().addPara("The target responds to the inspection.");
            }
        } else if (OPTION_LEAVE.equals(optionData)) {
            closed = true;
            dialog.dismiss();
        } else if (OPTION_CANCEL.equals(optionData)) {
            closed = true;
            dialog.dismissAsCancel();
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        if (closed) {
            return;
        }
        dialog.getTextPanel().addPara("The inspection continues after Engagement.");
    }

    @Override
    public Object getContext() {
        return dialog == null ? null : dialog.getInteractionTarget();
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }

}
```

:::

## 验证方案

前置数据：准备一个带 `$GearIn_dialog_target` 的实体、一个未写入该键的实体，以及一个能够重复选择继续选项的交互入口。

1. 保存类与 `picker` 替换，构建重启，从地图与已标记的 `Foundry` 交互。
2. 核对 `getInteractionTarget()` 返回的目标 ID。
3. 核对文本区域显示目标说明。
4. 核对选项区域显示继续、离开和取消。
5. 核对视觉区域显示目标图像或隐藏视觉区域。
6. 选择继续两次，比较两次文本结果。
7. 选择离开，观察会话关闭结果。
8. 重新打开会话并选择取消，观察取消关闭结果。
9. 重新打开会话并按 ESC，观察 ESC 的取消结果。
10. 在直接调用入口传入未标记实体，查看取消日志，确认没有建立检查选项。
11. 在直接入口传入 `null` 目标，查看 `target unavailable` 取消日志。
12. 在返回战斗或子界面时，按会话 `closed` 状态处理写入，确认已经结束的会话保持关闭。

预期结果：

- 会话目标 ID 与实际交互实体一致；
- 文本区域显示目标 ID 和检查说明；
- 选项区域显示三个稳定业务分支；
- 视觉区域根据目标图像可用性显示或隐藏；
- 第二次继续不追加第二份检查结果；
- 离开使用正常关闭语义；
- 取消和 ESC 使用取消关闭语义；
- 未标记与空目标通过日志记录取消原因，检查选项没有建立；
- 空目标不会发生目标解引用异常；
- 关闭后没有新的文本、选项或视觉写入。

## 故障分析

- **带标记实体没有检查选项**：目标 Memory 的键名或写入对象与会话读取对象不一致。
    - **解决方案**：核对目标自己的 `getMemoryWithoutUpdate()`、`$GearIn_dialog_target` 拼写和布尔值，再重新进入 `showInteractionDialog`。

- **未标记实体进入检查分支**：初始化阶段没有在建立选项前读取目标标记。
    - **解决方案**：把目标判定放在 `getOptionPanel()` 和 `addOption(...)` 之前，并在标记为假时调用 `dismissAsCancel()`。

- **视觉区域显示错误实体**：通用 `SectorEntityToken` 被直接按特定实体类型展示。
    - **解决方案**：先检查自定义交互图像，存在时调用 `showImageVisual(...)`，否则调用 `hideVisualPanel()`。

- **关闭后仍追加文本**：迟到回调只检查面板引用，没有检查 `closed`。
    - **解决方案**：在每个可能写入面板的回调入口先检查 `closed`，并在关闭分支设置状态后调用对应 `dismiss` 方法。

## 总结

Foundry 对话从会话取得目标与面板，标记决定检查是否适用，选项用稳定数据进入检查、离开或取消。`checked` 保存结果，`closed` 表示业务已经结束，画面关闭与返回回调都能按这个状态处理。

交接插件时保留当前会话，再显式初始化接收者并传递所需结果。布局、选择器与战斗各有显示或返回入口，会话继续围绕同一个目标完成实际操作。

## 建议参考项

- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：交互目标、插件、`TextPanelAPI`、`OptionPanelAPI`、`VisualPanelAPI`、布局和关闭入口；
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互会话的初始化、选项和返回回调；
- `com.fs.starfarer.api.campaign.TextPanelAPI`：当前会话的文本写入区域；
- `com.fs.starfarer.api.campaign.OptionPanelAPI`：当前会话的选项区域；
- `com.fs.starfarer.api.campaign.VisualPanelAPI`：当前会话的视觉区域；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标标记的读取和写入；
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：从目标条件返回交互插件的 `picker` 基类。
