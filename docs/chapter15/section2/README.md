# 实现对话插件

本节先从对话插件的生命周期和面板归属开始，再按 TextPanelAPI、OptionPanelAPI、VisualPanelAPI 的实际调用顺序拆解方法。每个片段只负责一个 API 关系，代码后紧跟参数解释和直接结果；最后再把片段拼接成完整示例。

*本节预期阅读时间：~45 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

- 解释对话插件回调与三个面板的对象归属；
- 使用 TextPanelAPI 写入、修改、追加、标记和排版文本；
- 使用 OptionPanelAPI 管理选项、提示、快捷键、确认和 selector；
- 使用 VisualPanelAPI 显示交互目标、玩家舰队、玩家本人和原版插图；
- 根据参数和状态字段判断调用结果。

## 前置知识

- [C15-S1] 的 InteractionDialogAPI 会话入口；
- [C7-S2] 的 Global 生涯服务；
- Java 接口、字段、重载和枚举；
- 能够启动测试生涯并观察对话界面。

## 对话插件的生命周期

游戏创建插件后调用 init。插件从传入的 InteractionDialogAPI 取得三个面板并保存引用。玩家选择选项时调用 optionSelected，鼠标悬停调用 optionMousedOver，动画推进调用 advance，Engagement 返回调用 backFromEngagement。面板 API 只在对话对象建立后使用，业务状态由插件字段保存。

## TextPanelAPI

### 保存面板引用

`getTextPanel()` 返回当前对话的文本区域。插件把它保存到字段，后续段落操作都作用于同一对象。

```java
this.text = dialog.getTextPanel();
```

文本区域由对话实例创建；插件只保存引用，不负责构造面板。

### 设置字体和模式

字体方法改变随后创建的文本组件。`setOrbitronMode` 设置模式，`isOrbitronMode` 读取当前状态。

```java
text.setFontInsignia();
text.setOrbitronMode(false);
text.addPara("Interaction target: " + target.getId());
```

段落沿用当前字体与模式，读取结果可以直接写入说明文本。

### 写入普通段落

`addPara` 的基础重载创建新段落；颜色重载只改变整段颜色。

```java
text.addPara("Colored paragraph", Color.YELLOW);
text.addParagraph("Paragraph alias", Color.GREEN);
```

颜色参数不是字符串高亮，最近段落会变成后续编辑的目标。

### 写入格式化段落

格式化重载把段落颜色、高亮颜色和高亮字符串一并传入。示例只有一个 `%s` 格式标记，因此只提供一个 `value`。

```java
text.addPara("Format %s", Color.WHITE, Color.YELLOW, "value");
```

格式字符串中的标记由参数替换，高亮字符串按出现顺序匹配。

### 替换和追加

`replaceLastParagraph` 改写最近段落；两个 `appendToLastParagraph` 重载分别追加和先截断再追加。

```java
text.replaceLastParagraph("Round 3: replaceLastParagraph changed this line.");
text.appendToLastParagraph(" appendToLastParagraph adds text.");
text.appendToLastParagraph(8, " trimmed prefix remains.");
```

最近段落的选择由写入顺序决定；截断参数按字符数处理。

### 高亮文本

首个、末个和变长参数高亮入口都操作最近段落。

```java
text.highlightFirstInLastPara("prefix", Color.YELLOW);
text.highlightLastInLastPara("remains", Color.GREEN);
text.highlightInLastPara(Color.CYAN, "appendToLastParagraph", "remains");
```

多个字符串按段落出现顺序传入，颜色数组随后按相同顺序应用。

### 更新高亮颜色

`setHighlightColorsInLastPara` 改变最近段落的高亮颜色序列。

```java
text.setHighlightColorsInLastPara(Color.YELLOW, Color.GREEN);
```

颜色只影响标记显示，不会改写段落文字。

### 图片和布局

`addImage` 使用分类与键插入资源；`updateSize` 重新计算文本区域布局。

```java
text.addImage("illustrations", "space_wreckage");
text.updateSize();
```

图片资源来自 `illustrations/space_wreckage`，尺寸更新使后续内容按新高度排列。

## OptionPanelAPI

### 建立选项

`clearOptions` 清除旧集合；`addOption` 将显示文字与业务键绑定。

```java
options.clearOptions();
options.addOption("Continue to text fonts", NEXT);
options.addOption("Leave", LEAVE);
options.addOption("Cancel", CANCEL);
```

回调使用第二个参数定位选项，显示文字只是界面文案。

### 改名、启用和删除

`setOptionText` 只改显示文本，`setEnabled` 改可用状态，`removeOption` 按 data 删除。

```java
options.addOption("Temporary option", "TEMP", "Created with a tooltip.");
options.setOptionText("Renamed option", "TEMP");
options.setEnabled("TEMP", true);
options.removeOption("TEMP");
```

同一个 `TEMP` 键贯穿创建、改名、状态设置和删除。

### 提示和快捷键

提示高亮与快捷键都附着在 data 键上。

```java
options.setTooltip("TEMP", "Tooltip for the stable optionData key.");
options.setTooltipHighlights("TEMP", "stable optionData");
options.setTooltipHighlightColors("TEMP", Color.YELLOW);
options.setShortcut("TEMP", org.lwjgl.input.Keyboard.KEY_O, false, false, false, false);
```

快捷键的四个布尔参数依次控制 Ctrl、Alt、Shift 和位置标志。

### selector

`addSelector` 定义范围和显示模式，`setSelectorValue` 写入初始值。

```java
options.addSelector("Scan range", SELECTOR, Color.CYAN, 220f, 50f, 1f, 5f,
        ValueDisplayMode.VALUE, "Inspection radius");
options.setSelectorValue(SELECTOR, 3f);
```

当前值为 3，边界由 1f 和 5f 决定；读取时继续使用 `SELECTOR`。

### 读取 selector 边界

三个读取方法分别返回当前值、最小值和最大值。

```java
text.addPara("Current=" + options.getSelectorValue(SELECTOR)
        + ", min=" + options.getMinSelectorValue(SELECTOR)
        + ", max=" + options.getMaxSelectorValue(SELECTOR));
```

拖动只改变当前值，创建时的最小值和最大值保持不变。

### 确认和一次性结果

确认文本由面板显示，业务结果由插件写入；`checked` 防止重复写入。

```java
options.addOption("Record inspection", CONFIRM);
options.addOptionConfirmation(CONFIRM, "Record this inspection?", "Confirm", "Back");
if (!checked) {
    checked = true;
    text.addPara("Inspection result recorded once.");
    options.setEnabled(CONFIRM, false);
}
```

确认键仍是 `CONFIRM`，禁用选项和业务状态共同保持一次性语义。

## VisualPanelAPI

### 显示交互目标

初始化时从 `getInteractionTarget()` 取得对话对象，并显示其自定义交互图像。

```java
SectorEntityToken target = dialog.getInteractionTarget();
if (target.getCustomInteractionDialogImageVisual() != null) {
    visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
}
```

这一步只建立对话起点，后续视觉对象不从目标类型推断。

### 显示玩家舰队

玩家舰队固定来自 `Global.getSector().getPlayerFleet()`。

```java
CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
visual.saveCurrentVisual();
visual.showFleetInfo("Your fleet", playerFleet, null, null);
```

第二个舰队参数为 `null`，因此视觉面板只展示玩家舰队。

### 显示玩家本人

玩家人物固定来自 `Global.getSector().getPlayerPerson()`，三个重载展示不同模式。

```java
PersonAPI player = Global.getSector().getPlayerPerson();
visual.showPersonInfo(player);
visual.showPersonInfo(player, true, true);
visual.showFirstPerson();
visual.hideFirstPerson();
```

人物显示与交互目标无关，`minimalMode` 和 `withRelBar` 由布尔参数控制。

### 显示原版插图

`space_wreckage` 是原版 `illustrations` 分类中已有的键。

```java
visual.showImagePortion("illustrations", "space_wreckage", 640f, 400f,
        0f, 0f, 480f, 300f);
```

源区域尺寸、偏移和显示尺寸共同决定裁剪后的插图。

### 淡出、快速完成和恢复

视觉切换前保存状态，淡出完成后恢复保存快照。

```java
visual.setVisualFade(0.2f, 0.2f);
visual.fadeVisualOut();
visual.finishFadeFast();
visual.restoreSavedVisual();
```

恢复读取此前 `saveCurrentVisual()` 保存的画面。

## InteractionDialogPlugin

### 推进和关闭

`advance` 可转发动画时间；关闭入口区分正常离开与取消。

```java
@Override public void advance(float amount) { if (text != null) text.advance(amount); }
if (closed || dialog == null) return;
if (LEAVE.equals(optionData)) { close(false); return; }
if (CANCEL.equals(optionData)) { close(true); return; }
```

`dismiss()` 和 `dismissAsCancel()` 表示不同结束原因，`closed` 防止重复关闭。

## 复杂方法的边界

TextPanelAPI 的 `addCostPanel` 和 `addSkillPanel` 都是在文本区域嵌入结构化信息：前者接收费用条目，后者接收人物及其技能数据。它们适合已经拥有对应经济或人物对象的界面，普通段落不需要为了调用它们而伪造这些对象。

OptionPanelAPI 的 `setStoryOptionParams` 把一个选项接入规则系统，需要 `StoryOptionParams` 和 `StoryPointActionDelegate`。它处理的是故事节点的参数与确认委托，普通 `addOption` 的 `optionData` 不能替代这条规则链。

VisualPanelAPI 的实体展示方法要求传入匹配的对象：`showPlanetInfo` 与 `showLargePlanet` 使用行星，`showFleetMemberInfo` 使用舰队成员，带 `FleetEncounterContextPlugin` 的 `showFleetInfo` 使用遭遇战上下文。`showLoot` 需要货物和交互监听器；`showCore`、`hideCore`、`closeCoreUI` 依赖核心界面；`showCustomPanel` 依赖自定义面板插件；`showMapMarker` 依赖地图标记及 Intel 标签；`showPreBattleJoinInfo` 依赖战斗上下文；`showNewGameOptionsPanel` 依赖角色创建数据。

这些调用分别属于费用、规则、战斗、核心界面、地图或角色创建流程。当前页面只演示能由普通对话直接提供的对象，其他方法应在其所属系统已经准备好上下文后再接入。

## 片段组合

完整示例按照初始化、文本、选项、selector、确认、玩家舰队、玩家本人、原版插图和关闭顺序组合。每个分支只修改当前负责的 API 对象；轮次字段把这些片段串成可观察的连续状态。

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
        if (dialog == null) { closed = true; return; }
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
        if (closed || dialog == null) return;
        if (LEAVE.equals(optionData)) { close(false); return; }
        if (CANCEL.equals(optionData)) { close(true); return; }
        if (CONFIRM.equals(optionData)) {
            if (!checked) {
                checked = true;
                text.addPara("Inspection result recorded once.");
                options.setEnabled(CONFIRM, false);
            }
            return;
        }
        if (!NEXT.equals(optionData) || round >= 9) return;
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
                options.removeOption("TEMP");
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
                visual.saveCurrentVisual();
                visual.showFleetInfo("Your fleet", playerFleet, null, null);
                options.addOption("Continue to player person", NEXT);
                break;
            case 8:
                text.addPara("Round 8: show the player person and person slots.");
                PersonAPI player = Global.getSector().getPlayerPerson();
                visual.showPersonInfo(player);
                visual.showPersonInfo(player, true, true);
                visual.showFirstPerson();
                visual.hideFirstPerson();
                options.addOption("Continue to original illustration", NEXT);
                break;
            case 9:
                text.addPara("Round 9: show, fade and restore an original illustration.");
                visual.showImagePortion("illustrations", "space_wreckage", 640f, 400f,
                        0f, 0f, 480f, 300f);
                visual.setVisualFade(0.2f, 0.2f);
                visual.fadeVisualOut();
                visual.finishFadeFast();
                visual.restoreSavedVisual();
                options.addOption("Finish", LEAVE);
                options.addOption("Cancel", CANCEL);
                break;
            default:
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        if (!closed && NEXT.equals(optionData)) options.setTooltip(NEXT, "Advance one tutorial round.");
    }

    @Override public void advance(float amount) { if (text != null) text.advance(amount); }
    @Override public void backFromEngagement(EngagementResultAPI battleResult) { }
    @Override public Object getContext() { return dialog == null ? null : dialog.getInteractionTarget(); }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return Collections.emptyMap(); }

    private void rebuildOptions(String nextText) {
        options.clearOptions();
        options.addOption(nextText, NEXT);
        options.addOption("Leave", LEAVE);
        options.addOption("Cancel", CANCEL);
    }

    private void close(boolean cancel) {
        if (closed) return;
        closed = true;
        if (cancel) dialog.dismissAsCancel(); else dialog.dismiss();
    }
}
```

:::

## 验证方案

前置数据：新建一个带交互目标的生涯，确保玩家舰队、玩家人物和原版 illustration 资源可用。

1. 打开对话，核对交互目标 ID 和初始选项。
2. 依次执行文本片段，核对字体、段落、替换、追加、高亮、图片和布局。
3. 执行选项片段，核对改名、tooltip、快捷键、selector 当前值 3、最小值 1 和最大值 5。
4. 触发确认，核对结果只写入一次并保持禁用状态。
5. 核对玩家舰队、玩家本人和人物视图入口。
6. 核对 illustrations/space_wreckage 显示、淡出、快速完成和恢复。
7. 分别选择离开、取消和 ESC，核对关闭结果。

预期结果：

- 每个方法片段都产生对应面板变化；
- optionText 改变不会改变 optionData；
- 玩家舰队、玩家本人和 space_wreckage 来自固定来源；
- 重复确认不会重复写入结果；
- 关闭后不再修改面板。

## 故障分析

- **选项改名后分支失效**：回调比较了 optionText 而不是稳定 data。
    - **解决方案**：使用 setOptionText 改显示文字，分支始终比较 optionData。
- **视觉显示成了交互目标**：视觉分支从 getInteractionTarget 推断对象类型。
    - **解决方案**：交互目标只负责初始展示，玩家对象分别从 Global 生涯服务取得。
- **插图没有出现**：illustrations 键或 sprite 名称不在原版设置中。
    - **解决方案**：使用 space_wreckage，并保持分类和键逐字一致。
- **结果重复写入**：确认分支没有 checked 状态门槛。
    - **解决方案**：写入前检查 checked，成功后立即设置为 true。

## 总结

TextPanelAPI 管理文本组件，OptionPanelAPI 管理玩家输入，VisualPanelAPI 管理右侧展示。教学片段先分别说明 API 契约，再通过轮次状态组合成可运行的对话插件。

## 建议参考项

- TextPanelAPI、OptionPanelAPI、VisualPanelAPI 本地接口声明；
- 原版 TutorialWelcomeDialogPluginImpl、StandardRespawnDialogPluginImpl；
- [C15-S1] 的对话入口和关闭语义。
