# 组织生涯交互对话

`Foundry` 已经能打开专用对话，这次把会话本身展开：目标怎样传入，三个面板怎样从同一会话取得，选项怎样记录一次检查，再怎样正常离开或取消。插件交接与布局也沿当前会话操作，您可以把画面里的实体、按钮和结果对应到实际对象。

*本节预期阅读时间：\~28 分钟。*

*本节预期操作时间：\~30 分钟。*

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

## 在 `Foundry` 选择本节对话

保存 `src/data/scripts/gearin/campaign/GearInConversationDialog.java`。在 [C14-S3] 的 `GearInCampaignPlugin.pickInteractionDialogPlugin(...)` 已命中标记后，把返回的对话实例改为：

```java
return new PluginPick<>(new GearInConversationDialog(), PickPriority.MOD_SPECIFIC);
```

`Foundry` 主实体继续沿前文标记。重启后从地图与它交互，会进入本节插件。

生涯交互可以从 `BaseCampaignPlugin` 的 `picker` 开始。`picker` 接收候选实体，按实体状态决定是否返回一个 `InteractionDialogPlugin`；它负责选择处理实现，不负责创建已经运行的对话。调用方拿到插件后，再把插件和同一个 `SectorEntityToken` 交给 `showInteractionDialog(plugin, target)`。

```java
boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(
        new GearInConversationDialog(), target);
```

**入口被接受后，引擎建立当前会话并调用 `init(dialog)`。** `shown` 表示这次对话是否真的打开，已有其他对话时可能为 `false`。引擎创建 `InteractionDialogAPI`，把目标、插件和三个面板放进同一会话，再调用插件的 `init(dialog)`。`plugin` 接收回调，`target` 是入口提供的实体，`dialog` 保存本次会话；插件从 `dialog` 取得目标和面板，因而不会把 `picker` 阶段的临时引用误当成会话状态。

## 读取目标并决定分支

`getInteractionTarget()` 返回通用的 `SectorEntityToken`。先取得它，再读取 Memory 或访问 ID；这样目标判断和后续显示使用同一对象。

```java
SectorEntityToken target = dialog.getInteractionTarget();
if (target == null) {
    dialog.dismissAsCancel();
    return;
}
```

目标准备阶段把 `$GearIn_dialog_target` 写进目标自己的 Memory。`picker` 在返回插件前读取同一个键筛选候选；通过筛选的实体才进入 `showInteractionDialog`，插件初始化时再从会话目标读取一次，确认实际进入会话的实体仍满足条件。

```java
target.getMemoryWithoutUpdate().set("$GearIn_dialog_target", true);
```

```java
boolean marked = target.getMemoryWithoutUpdate()
        .getBoolean("$GearIn_dialog_target");
```

标记为假或键不存在时，插件记录原因并调用 `dismissAsCancel()`；标记为真时才建立检查选项。写入方是目标准备代码，读取方先是 `picker`、后是 `init(dialog)`，读取时机分别对应候选筛选和会话初始化；`getMemoryWithoutUpdate()` 只读取当前值，不为筛选额外推进 `facts` 更新。

## 让三个面板在初始化时协作

三个面板由同一个 `dialog` 提供。文本面板先写目标说明，选项面板清掉进入会话前可能残留的选项并建立稳定分支，视觉面板根据目标是否提供自定义交互图像决定显示或隐藏。

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

`optionText` 只是玩家看到的文字；`optionData` 才是 `optionSelected` 用来判断分支的对象。ESC 映射到取消数据后，鼠标选项和键盘入口进入同一个取消路径。

### 文本面板承载会话状态

`TextPanelAPI` 的第一条内容来自会话目标，而不是 `picker`。目标 ID 写入文本后，您能把当前画面与 `getInteractionTarget()` 返回的实体对应起来；标记缺失分支写出的说明也属于同一文本面板，因此初始化结果在一个区域内完整呈现。

```java
text.addPara("Target ID: " + target.getId());
```

检查分支把业务结果追加到同一面板。`checked` 记录结果是否已经写入，文本面板本身只负责显示，不负责决定选项是否还能再次进入。

### 选项面板区分显示和分支键

`clearOptions()` 清理进入会话前可能遗留的内容，三个 `addOption` 调用分别建立继续、离开和取消。玩家看到的是 `optionText`，回调收到的 `optionData` 是稳定字符串；因此把 "`Continue` `inspection`" 改成别的语言不会改变分支判断。

```java
options.clearOptions();
options.addOption("Continue inspection", "CHECK");
options.addOption("Leave", "LEAVE");
options.addOption("Cancel", "CANCEL");
dialog.setOptionOnEscape("Cancel", "CANCEL");
```

`setOptionOnEscape` 让 ESC 直接提交取消键，键盘路径与取消选项共享同一个业务结果。选项的增删改和启用状态属于 `OptionPanelAPI`，关闭结果仍由插件根据 `optionData` 决定。

## 交接当前插件

`getPlugin()` 用于读取当前回调接收者，`setPlugin(nextPlugin)` 只替换接收者。面板和目标仍属于原会话，新插件字段则由自己的初始化建立。**`setPlugin(...)` 不自动调用 `init(...)`。** 需要交接时，创建方显式初始化新接收者：

```java
InteractionDialogPlugin current = dialog.getPlugin();
if (current == this) {
    dialog.setPlugin(nextPlugin);
    nextPlugin.init(dialog);
}
```

身份比较针对当前实际接收者，交接完成后回调再进入新插件。交接完成后，旧插件不再接收后续选项回调，仍需传递的数据由交接方显式保存。

## 调整会话显示层

布局调用只改变当前会话的显示层。文本宽高和偏移决定可读区域，提示文字、透明度和背景暗度决定界面呈现；它们不改变目标、插件或业务状态。

```java
dialog.setTextWidth(560f);
dialog.setTextHeight(380f);
dialog.setXOffset(24f);
dialog.setYOffset(12f);
dialog.setPromptText("Inspection workspace");
dialog.setOpacity(0.96f);
dialog.setBackgroundDimAmount(0.65f);
```

`showTextPanel()`、`hideTextPanel()` 和 `hideVisualPanel()` 也只改变可见区域。调整后应观察文本是否被选项遮挡、偏移是否仍在屏幕内，再决定参数是否合适。

## 处理专用入口

交互对话还可以暂时承载几类专用界面。`showLoot(...)` 接收战利品和相应监听器，适合把奖励对象交给已有界面呈现；显示结束后，当前插件仍负责把结果写回文本或选项。

`showCargoPickerDialog(...)`、`showFleetMemberPickerDialog(...)`、`showIndustryPicker(...)`、`showCampaignEntityPicker(...)` 和 `showCustomProductionPicker(...)` 把货物、舰队成员、行业、生涯实体或生产方案的选择过程交给专用 `picker`。选择器返回的对象由回调提供，当前对话的选项不会自动替换成选择结果；返回后依据结果重建需要显示的文本和分支。

`showCustomDialog(...)`、`showCustomVisualDialog(...)`、`showCommDirectoryDialog(...)` 和 `makeOptionOpenCore(...)` 会改变当前可见界面，有些入口还会把回调交给临时插件。调用方需要保留仍要继续使用的业务字段，并以 `getPlugin()` 确认返回后的回调接收者，再恢复面板内容。

`startBattle(...)` 把控制权交给 `Engagement`。战斗结束后由 `backFromEngagement(...)` 接收结果，插件继续使用原会话目标和业务状态；战斗结果本身不会自动完成检查，也不会替代关闭调用。

## 区分关闭结果并保护业务状态

`dismiss()` 表示正常离开，`dismissAsCancel()` 表示取消或中止。ESC 使用取消数据，因此和取消选项共享同一结果。关闭函数先记录 `closed`，再调用对应 API；检查结果则由 `checked` 单独记录。

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

**检查结果与会话结束分别记录。** `checked` 保证同一检查只写一次，`closed` 表示当前会话已经结束。先检查再离开时，两项状态都成立。

```java
if (closed) {
    return;
}
if (OPTION_CHECK.equals(optionData) && !checked) {
    checked = true;
    dialog.getTextPanel().addPara("The target responds to the inspection.");
}
```

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

`Foundry` 会话由 `picker` 选择插件，初始化从同一 `dialog` 取得实体与面板，Memory 标记决定检查是否适用。按钮显示与稳定分支键分别处理，一次检查与会话关闭也各有字段。

编者建议先确认这次对话的实际目标，再追面板与选项。交接时显式初始化新插件，关闭时记录对应结果，当前会话的对象与状态就能连续读下来。

## 建议参考项

- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：交互目标、插件、`TextPanelAPI`、`OptionPanelAPI`、`VisualPanelAPI`、布局和关闭入口；
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互会话的初始化、选项和返回回调；
- `com.fs.starfarer.api.campaign.TextPanelAPI`：当前会话的文本写入区域；
- `com.fs.starfarer.api.campaign.OptionPanelAPI`：当前会话的选项区域；
- `com.fs.starfarer.api.campaign.VisualPanelAPI`：当前会话的视觉区域；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标标记的读取和写入；
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：从目标条件返回交互插件的 `picker` 基类。
