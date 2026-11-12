# 实现对话插件

对话中的一段文字、一个数值选项和一张插图，都由当前面板保存并显示。本节继续使用 Foundry 会话，按文本编辑、选项管理和视觉切换依次操作，说明方法调用怎样改变画面，以及这些显示状态怎样与插件中的业务结果配合。

_本节预期阅读时间：\~45 分钟。_

_本节预期操作时间：\~40 分钟。_

## 学习目标

完成后，您将能够：

- 解释对话插件回调与三个面板的对象归属；
- 使用 `TextPanelAPI` 写入、修改、追加、标记和排版文本；
- 使用 `OptionPanelAPI` 管理选项、提示、快捷键、确认和 `selector`；
- 使用 `VisualPanelAPI` 显示交互目标、玩家舰队、玩家本人和原版插图；
- 根据参数和状态字段判断调用结果。

## 前置知识

- [C15-S1] 的 `InteractionDialogAPI` 会话入口；
- [C7-S2] 的 `Global` 生涯服务；
- Java 接口、字段、重载和枚举；
- 能够启动测试生涯并观察对话界面。

## 打开本节的面板会话

保存 `src/data/scripts/gearin/campaign/GearInInteractionDialogPlugin.java`，在 [C14-S3] 的交互 `picker` 已命中 `Foundry` 后，把返回实例换为本节类：

```java
return new PluginPick<>(new GearInInteractionDialogPlugin(), PickPriority.MOD_SPECIFIC);
```

构建重启，从地图与 Foundry 交互。`init(...)` 取得同一 `dialog` 的三个面板，完整类用继续按钮切换当前主题，另用按钮保留输入、确认和视觉变换的中间状态。

选项回调进入 `optionSelected(...)`，悬停进入 `optionMousedOver(...)`，界面推进调用 `advance(...)`，生涯战斗结果经 `backFromEngagement(...)` 返回。面板保存画面内容，插件保存当前主题、检查结果与关闭状态，调用时分别使用它们。

## 写入文字，再编辑当前段落

从 `dialog.getTextPanel()` 取得文本区域，保存在字段，后续写入都作用于这份面板。局部取得如下：

```java
this.text = dialog.getTextPanel();
```

### 选择字体并创建段落

初始化使用 Insignia 字体，Orbitron 模式设为 `false`，再写入目标 ID：

```java
text.setFontInsignia();
text.setOrbitronMode(false);
text.addPara("Interaction target: " + target.getId());
```

字体设置作用于随后创建的段落，已有段落继续使用原样式。`setFontOrbitron()`、`setFontVictor()` 改变字体，`setOrbitronMode(...)` 与 `isOrbitronMode()` 则分别设置和读取模式；选择字体与改变模式有各自入口。

`addPara(...)` 与 `addParagraph(...)` 都创建段落，下面的颜色重载为整段指定颜色：

```java
text.addPara("Colored paragraph", Color.YELLOW);
text.addParagraph("Paragraph alias", Color.GREEN);
```

需要同时填入值并高亮时，使用格式化重载。段落颜色与高亮颜色分别传入，字符串参数替换 `%s`，这里的局部片段只有一个值：

```java
text.addPara("Format %s", Color.WHITE, Color.YELLOW, "value");
```

后续值按格式顺序提供，最终段落也成为接下来编辑的目标。完整例先写入这些段落，再切到编辑主题，最近一段正是格式化文本。

### 对最近一段替换、追加与高亮

`replaceLastParagraph(...)` 替换最近段落，`appendToLastParagraph(...)` 追加；带数量参数的追加先截去末尾字符。以下局部片段先替换，再追加，最后截去 8 个字符并追加新文本：

```java
text.replaceLastParagraph("Round 3: replaceLastParagraph changed this line.");
text.appendToLastParagraph(" appendToLastParagraph adds text.");
text.appendToLastParagraph(8, " trimmed prefix remains.");
```

**编辑目标由段落写入顺序决定。** 中间再添加一段，后面的替换与高亮就转到新段。因此这些调用围绕同一段连续完成，读者看到的变化也能对应到正在编辑的文本。

高亮同样取得最近段落，分别选择首个匹配、末个匹配或一组字符串：

```java
text.highlightFirstInLastPara("prefix", Color.YELLOW);
text.highlightLastInLastPara("remains", Color.GREEN);
text.highlightInLastPara(Color.CYAN, "appendToLastParagraph", "remains");
```

一组高亮字符串按段落中的出现顺序提供，最终这次设置确定当前标记。需要每项不同颜色时，再使用颜色序列：

```java
text.setHighlightColorsInLastPara(Color.YELLOW, Color.GREEN);
```

颜色改变高亮的显示，文字本身保留。编辑完成后，完整例在文本区添加原版插图，再更新布局：

```java
text.addImage("illustrations", "space_wreckage");
text.updateSize();
```

资源使用 `illustrations/space_wreckage`，`updateSize()` 根据内容重新计算尺寸。文本图片与右侧视觉面板分别显示，本节后面还用同一资源观察视觉切换。

## 修改选项，保留同一个业务键

主题切换时清理旧选项，再创建本次可执行动作。初始化的局部集合如下：

```java
options.clearOptions();
options.addOption("Continue to text fonts", NEXT);
options.addOption("Leave", LEAVE);
options.addOption("Cancel", CANCEL);
```

回调使用第二个参数定位动作。这个关系在改名后也继续成立：下面的局部片段用 `TEMP` 创建、改名、启用与删除，完整类将删除留给独立按钮执行：

```java
options.addOption("Temporary option", "TEMP", "Created with a tooltip.");
options.setOptionText("Renamed option", "TEMP");
options.setEnabled("TEMP", true);
options.removeOption("TEMP");
```

### 给尚未删除的选项设置提示与快捷键

在完整例的选项主题，`TEMP` 改名后仍保留，提示与 O 快捷键也按这个键设置。以下局部调用用于这个阶段：

```java
options.setTooltip("TEMP", "Tooltip for the stable optionData key.");
options.setTooltipHighlights("TEMP", "stable optionData");
options.setTooltipHighlightColors("TEMP", Color.YELLOW);
options.setShortcut("TEMP", org.lwjgl.input.Keyboard.KEY_O, false, false, false, false);
```

提示字符串与颜色数组按对应顺序提供。快捷键前三个布尔参数控制 Ctrl、Alt、Shift，`putLast` 的公开约定为忽略；当前示例都传入 `false`。按 O 后回调仍取得 `TEMP`，显示文本变化并没有改变动作身份。

## 读取数值并确认一次结果

### 创建范围输入

`addSelector(...)` 在选项区加入可拖动数值条，以 `SELECTOR` 为数据键，范围 1 到 5，初始值设为 3：

```java
options.addSelector("Scan range", SELECTOR, Color.CYAN, 220f, 50f, 1f, 5f,
        ValueDisplayMode.VALUE, "Inspection radius");
options.setSelectorValue(SELECTOR, 3f);
```

宽度 220 包含数值区域，数值标签预留宽度 50，显示模式采用 `VALUE`。拖动后从相同键取得当前值，最小与最大范围继续保留。局部记录如下：

```java
text.addPara("Current=" + options.getSelectorValue(SELECTOR)
        + ", min=" + options.getMinSelectorValue(SELECTOR)
        + ", max=" + options.getMaxSelectorValue(SELECTOR));
```

完整例提供读取按钮，点击后记录拖动结果。输入条保存在选项面板，插件在实际动作回调时读取它，才能使用玩家当前选择。

### 确认后写入结果

`addOptionConfirmation(...)` 为 `CONFIRM` 加入确认对话。以下局部片段的前两行建立选项，后面的 `checked` 判断属于玩家确认后的回调：

```java
options.addOption("Record inspection", CONFIRM);
options.addOptionConfirmation(CONFIRM, "Record this inspection?", "Confirm", "Back");
if (!checked) {
    checked = true;
    text.addPara("Inspection result recorded once.");
    options.setEnabled(CONFIRM, false);
}
```

完整类在 `optionSelected(...)` 的 `CONFIRM` 分支写结果，设置 `checked` 并禁用按钮。确认面板决定是否提交这个选项，业务字段决定是否已经处理成功，两者分别完成本次动作。

`optionHasConfirmDelegate(...)`、`getOptionDataBeingConfirmed()` 与 `getSavedOptionList()` 还能读取确认与选项上下文。完整例记录这些入口所在阶段，当前的选项恢复或额外处理由调用方继续安排。

## 切换视觉对象与原版插图

### 从目标图像切到玩家舰队

初始视觉从会话实体取得自定义交互图像，局部读取如下：

```java
SectorEntityToken target = dialog.getInteractionTarget();
if (target.getCustomInteractionDialogImageVisual() != null) {
    visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
}
```

进入舰队主题时，从 `Global.getSector().getPlayerFleet()` 取得玩家舰队。下面的局部片段先显示舰队，再保存这份视觉，供后面的恢复按钮使用：

```java
CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
visual.showFleetInfo("Your fleet", playerFleet, null, null);
visual.saveCurrentVisual();
```

第二个舰队为空，当前只展示玩家舰队。`saveCurrentVisual()` 保存调用时的面板引用；此时舰队面板已经建立，Foundry 是否提供初始交互图像就不会影响本次恢复。

### 显示玩家人物

人物主题从 `getPlayerPerson()` 取得玩家本人，普通显示、紧凑显示与人物区域显隐分别调用：

```java
PersonAPI player = Global.getSector().getPlayerPerson();
visual.showPersonInfo(player);
visual.showPersonInfo(player, true, true);
visual.showFirstPerson();
visual.hideFirstPerson();
```

`showPersonInfo(player, true, true)` 选择紧凑模式并请求关系条，`showFirstPerson()` 与 `hideFirstPerson()` 改变第一人物区域的显示。这里明确提供玩家对象，当前会话的实体继续留作交互目标。

插图主题使用原版 `illustrations` 中的 `space_wreckage`：

```java
visual.showImagePortion("illustrations", "space_wreckage", 640f, 400f,
        0f, 0f, 480f, 300f);
```

源区域采用 640×400，偏移为 0，显示区域为 480×300。区域尺寸、偏移与显示尺寸共同决定插图呈现，文本区的 `addImage(...)` 仍是另一个显示入口。

### 保留淡出过程，再恢复已保存视觉

完整类将淡出、快速完成与恢复放在不同按钮中。以下局部方法分别由这些分支调用：

```java
visual.setVisualFade(0.2f, 0.2f);
visual.fadeVisualOut();
visual.finishFadeFast();
visual.restoreSavedVisual();
```

`setVisualFade(0.2f, 0.2f)` 设置淡入淡出时长，`fadeVisualOut()` 开始淡出，`finishFadeFast()` 快速推进到结束。`restoreSavedVisual()` 使用保存的有效面板引用，完成一次恢复后清空该保存引用，因此它不是多次撤销的历史列表。

**变换分开触发，中间画面才有观察机会。** 同一次回调里连续显示和恢复，后一个结果可以覆盖前一步。本例从插图恢复到此前保存的玩家舰队面板，保存引用随后清空。

## 推进回调与关闭状态

`advance(...)` 供插件按界面帧时间处理自己的动画与状态。文本面板由对话界面推进，本例的操作都由选项触发，因此这个回调保留空实现：

```java
@Override
public void advance(float amount) {
}
```

需要持续更新自己的动画时，再把业务推进写入这个回调。额外调用 `text.advance(amount)` 会让同一面板重复推进。

离开与取消沿 [C15-S1] 的两种关闭方法，先设置 `closed`，后续选项与面板写入按业务结束状态处理。完整类把关闭统一放在 `close(boolean cancel)`，按钮与 ESC 共用对应结果。

## 为结构化面板准备实际上下文

需要费用或人物技能信息时，`addCostPanel(...)` 与 `addSkillPanel(...)` 在文本区域嵌入对应面板。调用方提供实际费用条目或人物，面板才能显示当前业务数据。

故事点选项通过 `setStoryOptionParams(...)` 接入，需要 `StoryOptionParams` 与 `StoryPointActionDelegate`，参数与确认委托共同管理这项操作。普通选项仍通过当前 `optionData` 处理回调。

`VisualPanelAPI` 的实体展示方法要求传入匹配的对象：`showPlanetInfo` 与 `showLargePlanet` 使用行星，`showFleetMemberInfo` 使用舰队成员，带 `FleetEncounterContextPlugin` 的 `showFleetInfo` 使用遭遇战上下文。`showLoot` 需要货物和交互监听器；`showCore`、`hideCore`、`closeCoreUI` 依赖核心界面；`showCustomPanel` 依赖自定义面板插件；`showMapMarker` 依赖地图标记及 Intel 标签；`showPreBattleJoinInfo` 依赖战斗上下文；`showNewGameOptionsPanel` 依赖角色创建数据。

这些入口延伸到费用、规则、战斗、核心界面、地图与角色创建。先从对应流程取得对象，再调用面板方法，当前会话也就能显示实际结果。[C15-S3] 将对话接到酒吧，后续任务再使用这些上下文。

## 完整最小例

保存下列类，`picker` 沿前述入口选择它。当前主题和独立按钮共同组织观察。

::: details 完整成品：GearInInteractionDialogPlugin

```java
package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Collections;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;

public class GearInInteractionDialogPlugin implements InteractionDialogPlugin {
    private static final String NEXT = "NEXT";
    private static final String REMOVE_TEMP = "REMOVE_TEMP";
    private static final String READ_SELECTOR = "READ_SELECTOR";
    private static final String PERSON_COMPACT = "PERSON_COMPACT";
    private static final String PERSON_HIDE = "PERSON_HIDE";
    private static final String PERSON_SHOW = "PERSON_SHOW";
    private static final String VISUAL_FADE = "VISUAL_FADE";
    private static final String VISUAL_FINISH_FADE = "VISUAL_FINISH_FADE";
    private static final String VISUAL_RESTORE = "VISUAL_RESTORE";
    private static final String CONFIRM = "CONFIRM";
    private static final String SELECTOR = "SELECTOR";
    private static final String LEAVE = "LEAVE";
    private static final String CANCEL = "CANCEL";

    private InteractionDialogAPI dialog;
    private OptionPanelAPI options;
    private TextPanelAPI text;
    private VisualPanelAPI visual;
    private int round;
    private boolean checked;
    private boolean closed;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        options = dialog.getOptionPanel();
        text = dialog.getTextPanel();
        visual = dialog.getVisualPanel();
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null) {
            text.addPara("No interaction target is available.");
            close(true);
            return;
        }
        text.setFontInsignia();
        text.setOrbitronMode(false);
        text.addPara("Interaction target: " + target.getId());
        text.addParagraph("The target is shown first; later rounds use fixed player objects.", Color.LIGHT_GRAY);
        if (target.getCustomInteractionDialogImageVisual() != null) {
            visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
        }
        rebuildOptions("Continue to text fonts");
        dialog.setOptionOnEscape("Cancel", CANCEL);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (closed) {
            return;
        }
        if (LEAVE.equals(optionData)) {
            close(false);
            return;
        }
        if (CANCEL.equals(optionData)) {
            close(true);
            return;
        }
        if ("TEMP".equals(optionData)) {
            text.addPara("Renamed display text still has optionData TEMP.");
            return;
        }
        if (REMOVE_TEMP.equals(optionData)) {
            options.removeOption("TEMP");
            options.removeOption(REMOVE_TEMP);
            return;
        }
        if (PERSON_COMPACT.equals(optionData)) {
            visual.showPersonInfo(Global.getSector().getPlayerPerson(), true, true);
            return;
        }
        if (PERSON_HIDE.equals(optionData)) {
            visual.hideFirstPerson();
            return;
        }
        if (PERSON_SHOW.equals(optionData)) {
            visual.showFirstPerson();
            return;
        }
        if (VISUAL_FADE.equals(optionData)) {
            visual.setVisualFade(0.2f, 0.2f);
            visual.fadeVisualOut();
            return;
        }
        if (VISUAL_FINISH_FADE.equals(optionData)) {
            visual.finishFadeFast();
            return;
        }
        if (VISUAL_RESTORE.equals(optionData)) {
            visual.restoreSavedVisual();
            return;
        }
        if (READ_SELECTOR.equals(optionData)) {
            text.addPara("Selected value=" + options.getSelectorValue(SELECTOR));
            return;
        }
        if (CONFIRM.equals(optionData)) {
            if (!checked) {
                checked = true;
                text.addPara("Inspection result recorded once.");
                options.setEnabled(CONFIRM, false);
            }
            return;
        }
        if (!NEXT.equals(optionData) || round >= 9) {
            return;
        }
        round++;
        options.clearOptions();
        switch (round) {
            case 1:
                text.setFontOrbitron();
                text.addPara("Round 1: font and mode selection.", Color.CYAN);
                text.addPara("Orbitron mode = " + text.isOrbitronMode());
                options.addOption("Continue to paragraph writing", NEXT);
                break;
            case 2:
                text.setFontVictor();
                text.addPara("Round 2: addPara and addParagraph overloads.");
                text.addPara("Colored paragraph", Color.YELLOW);
                text.addParagraph("Paragraph alias", Color.GREEN);
                text.addPara("Format %s", Color.WHITE, Color.YELLOW, "value");
                options.addOption("Continue to paragraph editing", NEXT);
                break;
            case 3:
                text.replaceLastParagraph("Round 3: replaceLastParagraph changed this line.");
                text.appendToLastParagraph(" appendToLastParagraph adds text.");
                text.appendToLastParagraph(8, " trimmed prefix remains.");
                text.highlightFirstInLastPara("prefix", Color.YELLOW);
                text.highlightLastInLastPara("remains", Color.GREEN);
                text.highlightInLastPara(Color.CYAN, "appendToLastParagraph", "remains");
                text.setHighlightColorsInLastPara(Color.YELLOW, Color.GREEN);
                text.addImage("illustrations", "space_wreckage");
                text.updateSize();
                options.addOption("Continue to option management", NEXT);
                break;
            case 4:
                text.addPara("Round 4: add, rename, enable and remove options.");
                options.addOption("Temporary option", "TEMP", "Created with a tooltip.");
                options.setOptionText("Renamed option", "TEMP");
                options.setTooltip("TEMP", "Tooltip for the stable optionData key.");
                options.setTooltipHighlights("TEMP", "stable optionData");
                options.setTooltipHighlightColors("TEMP", Color.YELLOW);
                options.setShortcut("TEMP", org.lwjgl.input.Keyboard.KEY_O, false, false, false, false);
                options.setEnabled("TEMP", true);
                options.addOption("Remove the renamed option", REMOVE_TEMP);
                options.addOption("Continue to selector", NEXT);
                break;
            case 5:
                text.addPara("Round 5: selector creation, value and bounds.");
                options.addSelector("Scan range", SELECTOR, Color.CYAN, 220f, 50f, 1f, 5f,
                        ValueDisplayMode.VALUE, "Inspection radius");
                options.setSelectorValue(SELECTOR, 3f);
                text.addPara("Current=" + options.getSelectorValue(SELECTOR)
                        + ", min=" + options.getMinSelectorValue(SELECTOR)
                        + ", max=" + options.getMaxSelectorValue(SELECTOR));
                options.addOption("Read the selector value", READ_SELECTOR);
                options.addOption("Continue to confirmation", NEXT);
                break;
            case 6:
                text.addPara("Round 6: confirmation and saved option state.");
                options.addOption("Record inspection", CONFIRM);
                options.addOptionConfirmation(CONFIRM, "Record this inspection?", "Confirm", "Back");
                options.optionHasConfirmDelegate(CONFIRM);
                options.getOptionDataBeingConfirmed();
                options.getSavedOptionList();
                options.addOption("Continue to player fleet", NEXT);
                break;
            case 7:
                text.addPara("Round 7: show the player's fleet.");
                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                visual.showFleetInfo("Your fleet", playerFleet, null, null);
                visual.saveCurrentVisual();
                options.addOption("Continue to player person", NEXT);
                break;
            case 8:
                text.addPara("Round 8: show the player person and person slots.");
                PersonAPI player = Global.getSector().getPlayerPerson();
                visual.showPersonInfo(player);
                options.addOption("Show compact person view", PERSON_COMPACT);
                options.addOption("Hide the first person", PERSON_HIDE);
                options.addOption("Show the first person", PERSON_SHOW);
                options.addOption("Continue to original illustration", NEXT);
                break;
            case 9:
                text.addPara("Round 9: show, fade and restore an original illustration.");
                visual.showImagePortion("illustrations", "space_wreckage", 640f, 400f,
                        0f, 0f, 480f, 300f);
                options.addOption("Fade the visual out", VISUAL_FADE);
                options.addOption("Finish the fade immediately", VISUAL_FINISH_FADE);
                options.addOption("Restore the saved visual", VISUAL_RESTORE);
                options.addOption("Finish", LEAVE);
                options.addOption("Cancel", CANCEL);
                break;
            default:
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        if (!closed && NEXT.equals(optionData)) {
            options.setTooltip(NEXT, "Advance one tutorial round.");
        }
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return dialog == null ? null : dialog.getInteractionTarget();
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }

    private void rebuildOptions(String nextText) {
        options.clearOptions();
        options.addOption(nextText, NEXT);
        options.addOption("Leave", LEAVE);
        options.addOption("Cancel", CANCEL);
    }

    private void close(boolean cancel) {
        if (closed) {
            return;
        }
        closed = true;
        if (cancel) {
            dialog.dismissAsCancel();
        } else {
            dialog.dismiss();
        }
    }
}
```

:::

## 验证方案

前置数据：新建一个带交互目标的生涯，确保玩家舰队、玩家人物和原版 `illustration` 资源可用。

1. 保存类与 `picker` 替换，构建重启，与 `Foundry` 交互，核对目标 ID 和初始选项。
2. 依次执行文本片段，核对字体、段落、替换、追加、高亮、图片和布局。
3. 在选项主题悬停改名按钮，按 O 触发并查看稳定 `data`；选择删除后再继续到 `selector`，拖动并读取当前值，核对范围 1 到 5、初始 3。
4. 触发确认，核对结果只写入一次并保持禁用状态。
5. 核对玩家舰队、玩家本人和人物视图入口。
6. 在插图主题分别点击淡出、快速完成与恢复，确认 `illustrations/space_wreckage` 淡出，恢复按钮重新显示此前保存的玩家舰队。
7. 分别选择离开、取消和 ESC，核对关闭结果。

预期结果：

- 每个方法片段都产生对应面板变化；
- `optionText` 改变不会改变 `optionData`；
- 玩家舰队、玩家本人和 `space_wreckage` 来自固定来源；
- 恢复按钮返回玩家舰队面板，即使 Foundry 没有初始交互图像也能完成恢复；
- 重复确认不会重复写入结果；
- 关闭后不再修改面板。

## 故障分析

- **选项改名后分支失效**：回调比较了 `optionText` 而不是稳定 `data`。
    - **解决方案**：使用 `setOptionText` 改显示文字，分支始终比较 `optionData`。

- **视觉显示成了交互目标**：视觉分支从 `getInteractionTarget` 推断对象类型。
    - **解决方案**：交互目标只负责初始展示，玩家对象分别从 `Global` 生涯服务取得。

- **插图没有出现**：`illustrations` 键或 `sprite` 名称不在原版设置中。
    - **解决方案**：使用 `space_wreckage`，并保持分类和键逐字一致。

- **结果重复写入**：确认分支没有 `checked` 状态门槛。
    - **解决方案**：写入前检查 `checked`，成功后立即设置为 `true`。

## 总结

文字编辑围绕最近段落，选项修改和数值输入使用稳定数据键，视觉切换使用实际人物、舰队与资源。面板显示当前状态，插件字段决定是否已经完成业务动作。

同一会话把这些动作连接起来，独立按钮保留变化的中间状态，确认与关闭继续按对应回调处理。显示结果、输入值与一次性状态各自明确，对话才能继续接到实际交易或任务。

## 建议参考项

- `com.fs.starfarer.api.campaign.TextPanelAPI`：本页写入、修改和排版文本都作用于该面板对象；
- `com.fs.starfarer.api.campaign.OptionPanelAPI`：选项、提示和确认门槛的实现接口，分支判断依赖 `optionData`；
- `com.fs.starfarer.api.campaign.VisualPanelAPI`：交互目标、玩家舰队和原版插图的显示入口；
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：插件在 `init` 阶段从它取得三个面板引用的会话入口；
- `com.fs.starfarer.api.impl.campaign.tutorial.TutorialWelcomeDialogPluginImpl`：原版教学对话插件，可对照入口与关闭流程；
- `com.fs.starfarer.api.impl.campaign.StandardRespawnDialogPluginImpl`：原版重生对话插件，可对照关闭语义和状态门槛。
