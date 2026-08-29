# 制作情报与交互对话框

本节制作自定义情报与交互对话框，让 MOD 能够登记消息并向玩家提供可选择的交互。两个示例会分别展示情报管理和对话框的入口，并沿用上一节的势力与货舱操作。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 区分情报与交互对话框两个服务入口，并复述各自的进入方式 (`SectorAPI.getIntelManager()`、`CampaignUIAPI.showInteractionDialog(plugin, target)`)；
- 区分 `addIntel` 与 `queueIntel` 的登记时机 (是否经过通信中继可见性检查)，并解释情报集合随存档保存的后果；
- 写一个 `BaseIntelPlugin` 子类与一条 `MessageIntel` 纯消息，走完创建、登记、可见、推进、移除的完整流程；
- 复述交互对话框的插件生命周期 (`init` → `optionSelected` → `advance` → `backFromEngagement` → `dismiss`) 与常用入口，并验证 `showInteractionDialog` 的返回值可能为 `false`；
- 从症状定位故障，排查时沿数据文件 → 注册入口 → 运行使用方 → 可观察结果四个位置复核。

## 前置知识

- [C6-S2] 的 `modPlugin` 生命周期与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义与生涯引擎建立边界；
- [C9-S1] 的 `SectorAPI` 星区入口、`onGameLoad` 注册与 `REGISTER_MARK` 判断；
- [C9-S5] 的 `FactionAPI` 关系读取与 `CargoAPI` 货舱读取。

## 术语

按术语约定，本节首次完整讲解两个词；API 类名保留源码大小写：

- **情报 (intel)**：API 名保留 `IntelInfoPlugin`(单条情报约定) 与 `IntelManagerAPI`(登记集合)。
    - 一条情报 = 一个实现了 `IntelInfoPlugin` 的插件实例，同时承载 Intel 页 UI 描述、消息更新与每帧推进 (`BaseIntelPlugin` 兼为 `EveryFrameScript`)。
- **交互对话框 (interaction dialog)**：API 名保留 `InteractionDialogAPI`(对话框本体) 与 `InteractionDialogPlugin`(对话框内容插件)。生涯 UI 承载的模态对话：文本面板 + 选项面板 + 视觉面板。

## 梳理情报对话关系

### 服务入口速查表

| 服务 | 进入方式 | 核心接口 |
|---|---|---|
| 情报 | `Global.getSector().getIntelManager()` | `IntelManagerAPI` / `IntelInfoPlugin` |
| 交互对话框 | `Global.getSector().getCampaignUI().showInteractionDialog(plugin, target)` | `InteractionDialogAPI` / `InteractionDialogPlugin` |

### 情报 (IntelManagerAPI / IntelInfoPlugin / BaseIntelPlugin / MessageIntel)

**入口**：`SectorAPI.getIntelManager()` → `IntelManagerAPI`(`com.fs.starfarer.api.campaign.comm.IntelManagerAPI`)。intel 集合随存档保存，读档后仍然可用。

为什么情报集合随存档保存

intel 是生涯状态的组成部分：玩家可见时间戳、`NEW` 标记窗口、结束倒计时都要跨存档存活，所以情报集合随存档一起保存。这也是 "演示情报必须当帧移除" 的原因 —— `addIntel` 进去不删，就会永远留在存档里。

**创建与登记两条链**：

1. **`addIntel(plugin)`**(`IntelManagerAPI`) 直接加入已知集合：置玩家可见时间戳、调 `reportMadeVisibleToPlayer()`、`autoAddCampaignMessage()`(默认 true) 时经 `CampaignUIAPI.addMessage` 弹左下消息 (`IntelManagerAPI.addIntel`)。
    - **`addIntel` 绕过通信中继可见性检查** —— `IntelInfoPlugin.canMakeVisibleToPlayer` "Only checked if adding using IntelManager.queueIntel(). addIntel() bypasses this and all other checks."。
    - `addIntel(plugin, forceNoMessage)` / `addIntel(plugin, forceNoMessage, textPanel)` 控制消息行为。
2. **`queueIntel(plugin[, maxCommQueueDays])`**(`IntelManagerAPI`) 加入通信队列；玩家进入通信中继范围或 `forceAddNextFrame()` 时，转为可见 intel 并出队。
    - 同批最多 4 条单独弹出，第 5 条起合并为一条 "New intel (N)" 汇总。

**每帧推进与清理**：未暂停且有玩家舰队时，情报管理器每帧推进，通信队列的可见性检查与 `removeAllThatShouldBeRemoved()` 都在这条路径上：`shouldRemoveIntel()` 返回 true 的情报被移除并调 `reportRemovedIntel()`；`IntelManagerAPI.removeIntel` 会同时移除并出队。

**`BaseIntelPlugin`**(`com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`) 是自带的完整默认实现 (同时实现 `IntelInfoPlugin` / `EveryFrameScript` / `CallableEvent` / `CommSnifferReadableIntel`)，MOD 自定义情报的标准父类。

### BaseIntelPlugin 常用行为速查表

| 成员 | 语义 |
|---|---|
| `advance(amount)` | 把秒数换算成游戏日推进 ending 倒计时；`isDone()` = `isEnded()` |
| `runWhilePaused()` | 默认 `false`(暂停不推进) |
| `endImmediately()` / `endAfterDelay()` / `endAfterDelay(days)` | 结束流程；`getBaseDaysAfterEnd()` 默认 **3 游戏日** |
| `createIntelInfo` / `getName()` | 列表条目 = 名称 + 子弹行；`getName()` 返回 null 则不显示 |
| `createSmallDescription` | 小描述 (默认 "请覆写")；`hasSmallDescription()` 默认 true |
| `getIcon()` | null = 无图标 (`IntelInfoPlugin` "40x40, no icon if null.") |
| `getIntelTags(map)` | 默认含 `INTEL_IMPORTANT` / `INTEL_NEW` / 中继范围内的 `INTEL_LOCAL`；**map 为 null 时安全返回** (基类内部已做 null 判断) |
| `sendUpdate` / `sendUpdateIfPlayerHasIntel` | 更新消息；后者要求已有玩家可见时间戳 |
| `BUTTON_DELETE` / `addDeleteButton` / `buttonPressConfirmed` | 删除按钮 (确认框 + `endImmediately`) |
| `getCommMessageSound` | 发布/更新音效 (`ui_intel_something_posted` / `ui_intel_update` 等常量) |

**`MessageIntel`**(`com.fs.starfarer.api.impl.campaign.intel.MessageIntel`) 是左下消息区的**纯消息**类，"For campaign messages that show up in the lower-left corner, but are not tied to an actual piece of intel." —— 它不绑定任何情报页面。构造与追加入口：`new MessageIntel(text[, color[, highlights]])`、`addLine(...)` 多行追加、`getCommMessageSound` 默认 `ui_intel_minor_message`；用 `CampaignUIAPI.addMessage(IntelInfoPlugin)` 发送。

**`IntelInfoPlugin` 约定要点** (`com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`)：`getIntelTags(map)` **必须处理 map 为 null**("Method NEEDS to handle map being null gracefully.")；`NEW_DAYS = 5` 是新情报 "NEW" 标记窗口；`IntelSortTier` 是排序档；`getMapLocation(map)` 与 `getArrowData(map)` 控制地图定位与箭头；`isHidden()` 让已可见情报在 UI 与消息中隐藏 (`BaseIntelPlugin` 默认实现含教程状态判断)。

### 交互对话框 (InteractionDialogAPI / InteractionDialogPlugin)

**打开**：`CampaignUIAPI.showInteractionDialog(plugin, interactionTarget)`，"Returns true if dialog was actually shown, false otherwise (if, for example, UI is already showing another dialog)."、"interactionTarget can be null."。

**插件生命周期 (`InteractionDialogPlugin`)**：`init(dialog)` → `optionSelected(optionText, optionData)` / `optionMousedOver` → 每帧 `advance(amount)` → 战斗返回 `backFromEngagement(result)`；另有 `getContext()` / `getMemoryMap()`(Rules 侧用)。

### InteractionDialogAPI 入口分组速查表

| 分组 | 成员 | 语义 |
|---|---|---|
| 面板 | `getOptionPanel()`、`getTextPanel()`、`getVisualPanel()` | 三个面板的访问入口 |
| 内容写入 | `setPromptText`、`setOptionOnEscape(text, optionId)`、`setOptionOnConfirm`、`addOptionSelectedText`、`setOptionColor`、`makeStoryOption` | 提示文本、ESC/确认键行为、选项点击后的回显 |
| 布局 | `setTextWidth`/`setTextHeight`/`setXOffset`/`setYOffset`、`setOpacity`、`setBackgroundDimAmount`、`hideTextPanel`/`showTextPanel`、`hideVisualPanel`/`showVisualPanel` | 尺寸、透明度与面板显隐 |
| 关闭 | `dismiss()`、`dismissAsCancel()` | 正常关闭 / 以取消语义关闭 |
| picker | `showCargoPickerDialog`、`showFleetMemberPickerDialog`、`showFleetMemberRecoveryDialog`、`showIndustryPicker`、`showCustomDialog`、`showCustomVisualDialog`、`showCampaignEntityPicker`、`showGroundRaidTargetPicker`、`showCommDirectoryDialog` | 各类选择器 |
| core UI 与战斗 | `makeOptionOpenCore(optionId, tabId, mode)`、`startBattle(context)` | 打开 core UI 页签 / 从对话框开战 |
| 上下文 | `getInteractionTarget()`、`getPlugin()`、`setPlugin`、`setInteractionTarget` | 交互目标与插件互写 |

交互目标与插件互写

选项分支判断用 `optionData`，不要用 `optionText`

`optionSelected(optionText, optionData)` 的第一个参数是用户看到的显示文本 (可随 UI 文案变化)，第二个才是 `addOption` 时传入的 id。按 `optionText` 做分支会在文案改动后静默失效。

把 `showInteractionDialog` 的返回值写进日志

对话框在已有对话框显示时会静默返回 `false` —— 不记录返回值，故障就只剩 "对话框没弹出来" 这个没有报错的现象。本节演示正是把 `shown` 布尔值记入日志，`shown=false` 时其余演示不受影响。

**面板常用成员**：`OptionPanelAPI`(`com.fs.starfarer.api.campaign.OptionPanelAPI`) —— `addOption(text, data[, tooltip])`、`addOption(text, data, color, tooltip)`、`clearOptions`；`setEnabled(data, enabled)`("Only works for options, not selectors.")、`setShortcut`、`addSelector`、`addOptionConfirmation`、`removeOption`、`setStoryOptionParams`。

`TextPanelAPI`(`com.fs.starfarer.api.campaign.TextPanelAPI`) —— `addPara(text[, color])`、`addPara(format, hl, highlights)`、`addParagraph`、`highlightInLastPara`、`setHighlightColorsInLastPara`、`clear`；另有 `getDialog`、`beginTooltip`/`addTooltip`。

本节的 `GearInServicesDialog` 只用到其中两个入口：`addOption` 建立选项、`addPara` 追加文本行；其余成员在需要时再查。

## 实现情报与交互服务

情报和对话框共用生涯 UI，但有各自的创建、登记和清理链。下面用 6 个局部片段分别接通两条链，每段都能从消息区、Intel 页、对话框或日志观察结果。

### 登记并移除自定义情报

插入位置：`GearInIntelServicesScript.runDemo` 开头。先直接登记自定义情报，核对管理器持有它，再于同一演示中移除，避免测试对象进入存档。

```java
SectorAPI sector = Global.getSector();
IntelManagerAPI intelManager = sector.getIntelManager();
GearInServicesIntel intel = new GearInServicesIntel();

intelManager.addIntel(intel);
boolean present = intelManager.hasIntel(intel);
int intelCount = intelManager.getIntel(GearInServicesIntel.class).size();

intelManager.removeIntel(intel);
boolean absent = !intelManager.hasIntel(intel);
```

完成信号：第一条日志显示 `present=true` 且计数增加，第二条显示 `absent=true` 且计数恢复；左下消息区仍保留登记时发布的消息。

### 发布不绑定页面的纯消息

插入位置：自定义情报移除之后。`MessageIntel` 只进入左下消息区，可以追加多行，但不会在 Intel 页留下条目。

```java
MessageIntel message = new MessageIntel(
		"GearIn 生涯服务速览：纯消息情报演示", Misc.getTextColor());
message.addLine("这条追加内容不绑定情报页面。", Misc.getGrayColor());
sector.getCampaignUI().addMessage(message);
```

完成信号：消息区出现两行文本，点击后不跳转到 Intel 页；`getIntel(GearInServicesIntel.class)` 的计数不变。

### 为自定义情报提供列表与标签

插入位置：`GearInServicesIntel`。名称决定列表文本，小描述负责详情内容；标签实现先调用父类以继承 null map 处理，再追加探索分类。

```java
@Override
public String getName() {
	return "GearIn 服务速览 (自定义情报演示)";
}

@Override
public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
	info.addPara("这是一条用于核对登记与移除流程的演示情报。",
			Misc.getTextColor(), 0f);
}

@Override
public Set<String> getIntelTags(SectorMapAPI map) {
	Set<String> tags = super.getIntelTags(map);
	tags.add(Tags.INTEL_EXPLORATION);
	return tags;
}
```

完成信号：情报登记期间能按名称显示，并归入探索标签；以 null 调用 `getIntelTags(null)` 不抛异常。

### 打开对话框并记录结果

插入位置：`GearInDialogServicesScript.runDemo`。普通对话不需要交互目标，因此传 null；返回值必须进入日志，才能区分 "已打开" 和 "当前 UI 拒绝打开"。

```java
CampaignUIAPI ui = Global.getSector().getCampaignUI();
boolean shown = ui.showInteractionDialog(new GearInServicesDialog(), null);
LOG.info("GearIn campaign services dialog: shown=" + shown);
```

完成信号：UI 空闲时日志为 `shown=true` 并弹出对话框；已有模态对话时日志为 `shown=false`，且没有异常。

### 初始化文本与选项

插入位置：`GearInServicesDialog.init`。保存对话框引用，写入说明文本，再用稳定的 option ID 建立两个查询动作和一个关闭动作。

```java
this.dialog = dialog;
dialog.getTextPanel().addPara("GearIn 生涯服务速览：选择要查询的内容。");
OptionPanelAPI options = dialog.getOptionPanel();
options.addOption("查看势力关系", OPTION_FACTION);
options.addOption("查看舰队货物", OPTION_CARGO);
options.addOption("关闭演示", OPTION_CLOSE);
dialog.setOptionOnEscape("关闭演示", OPTION_CLOSE);
```

完成信号：对话框固定显示 3 个选项，ESC 与 "关闭演示" 进入同一个 `OPTION_CLOSE` 分支。

### 按 optionData 处理选择

插入位置：`GearInServicesDialog.optionSelected`。显示文本只用于回显，分支始终比较 `optionData`；关闭分支显式调用 `dismiss()`。

```java
if (OPTION_FACTION.equals(optionData)) {
	FactionAPI faction = Global.getSector().getPlayerFaction();
	dialog.getTextPanel().addPara("与霸主关系："
			+ faction.getRelationship(Factions.HEGEMONY));
} else if (OPTION_CARGO.equals(optionData)) {
	CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
	dialog.getTextPanel().addPara("舰队补给：" + cargo.getSupplies());
} else if (OPTION_CLOSE.equals(optionData)) {
	dialog.dismiss();
}
```

完成信号：两个查询选项各追加一行对应数据；关闭选项和 ESC 都关闭对话框并恢复生涯界面。

## 组装情报对话实现

在既有 MOD 工程 (布局与插件入口 `GearInModPlugin`) 之上，沿用 [C9-S5] 定义的抽象基类 `GearInOneShotScript`，本节在其上新增两个服务演示脚本与两个插件类，共五个类。一文件一角色，不新增任何资源文件；两个脚本在 `onGameLoad` 各自注册，首个 `advance()` 各执行一次演示并自停：

```text {9-12}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       ├── GearInOneShotScript          # 沿用 C9-S5 的抽象基类 (不参与注册)
│       ├── GearInIntelServicesScript    # 本节新增 (情报段脚本)
│       ├── GearInDialogServicesScript   # 本节新增 (对话框段脚本)
│       ├── GearInServicesIntel          # 本节新增 (情报插件)
│       └── GearInServicesDialog         # 本节新增 (对话框插件)
└── jars\
```

### 复用一次性脚本基类

**职责**：一次性演示脚本骨架 —— 封装 `EveryFrameScript` 三方法 (`isDone` / `runWhilePaused` / `advance`)，首次 `advance()` 执行一次子类实现的 `runDemo()` 后自停。

**绑定注册入口**：不参与任何注册；两个服务演示脚本继承它，各自负责自己的注册。

### GearInIntelServicesScript(情报段脚本)

**职责**：`IntelManagerAPI` 常用入口演示 —— 创建自定义情报 `GearInServicesIntel`(`BaseIntelPlugin` 子类)，依次 `addIntel` 登记、`hasIntel` 验证、`removeIntel` 移除 (不留存档残留)；再用 `MessageIntel` 发一条纯消息 (左下消息区)。

**绑定注册入口**：onGameLoad 的一次性注册判断内执行 `addScript` 注册，persistent data 键 `REGISTER_MARK = "GearIn_IntelServices"` 与调用点片段逐字一致。

### GearInDialogServicesScript(对话框段脚本)

**职责**：`InteractionDialogAPI` 常用入口演示 —— 用 `CampaignUIAPI.showInteractionDialog(plugin, null)` 打开演示对话框 (`GearInServicesDialog`)，记录返回的 `shown` 布尔值 (已有对话框显示时返回 false)。

**绑定注册入口**：onGameLoad 的一次性注册判断内执行 `addScript` 注册，persistent data 键 `REGISTER_MARK = "GearIn_DialogServices"` 与调用点片段逐字一致。

### GearInServicesIntel(情报插件)

**职责**：自定义情报插件 (`BaseIntelPlugin` 子类) —— 实现名称/图标/小描述/Intel 标签；`getIcon()` 返回 null 时不显示图标；`getIntelTags(map)` 先调 `super.getIntelTags(map)`(基类默认实现内部已做 null 判断) 再追加自定义 tag。

**绑定注册入口**：由 `GearInIntelServicesScript` 经 `IntelManagerAPI.addIntel(plugin)` 登记 (`addIntel` 绕过可见性检查，立即置玩家可见时间戳并弹出左下消息)。

### GearInServicesDialog(对话框插件)

**职责**：演示对话框插件 (`InteractionDialogPlugin`) —— `init` 写入文本面板与三个选项 (查看势力关系 / 查看舰队货物 / 关闭演示) 并绑定 ESC；`optionSelected` 按 `optionData` 分支展示对应信息，关闭选项与 ESC 走 `dismiss()`；未使用的回调保留空实现。

**绑定注册入口**：由 `GearInDialogServicesScript` 经 `CampaignUIAPI.showInteractionDialog(plugin, null)` 打开 (target 传 null，`getInteractionTarget()` 返回 null)。

### 绑定位置一览速查表

| 类 | 角色 | 绑定注册入口 |
|---|---|---|
| `GearInOneShotScript` | 抽象基类：一次性演示脚本骨架 (沿用 [C9-S5]) | 不参与注册 |
| `GearInIntelServicesScript` | 情报段演示脚本 | onGameLoad `addScript`，persistent data 键 `GearIn_IntelServices` |
| `GearInDialogServicesScript` | 对话框段演示脚本 | onGameLoad `addScript`，persistent data 键 `GearIn_DialogServices` |
| `GearInServicesIntel` | 自定义情报插件 (`BaseIntelPlugin` 子类) | `IntelManagerAPI.addIntel(plugin)`(由情报段脚本调用) |
| `GearInServicesDialog` | 演示对话框插件 (`InteractionDialogPlugin`) | `CampaignUIAPI.showInteractionDialog(plugin, null)`(由对话框段脚本调用) |

### 行为要点

- `extends GearInOneShotScript`：沿用脚本节的一次性演示模式 —— 首帧 `advance()` 执行一次 `runDemo()`，`isDone()` 置位后引擎下一帧自动移除。
- `REGISTER_MARK`：注册判断用的 persistent data 键，两个脚本各自独立 (`GearIn_IntelServices` / `GearIn_DialogServices`)。
    - **必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不并入既有插件的 `CLASS_MARK` 标记。
- 情报段：`addIntel` → `hasIntel` → `removeIntel` 同帧形成完整流程，不留存档残留；`addIntel` 触发 `autoAddCampaignMessage()`(左下消息)；`MessageIntel` 走 `CampaignUIAPI.addMessage` 发纯消息。
- 对话框段：`showInteractionDialog(plugin, null)` 返回 `shown`；对话框由用户点选驱动 (`optionSelected` 写文本与日志)，`OPTION_CLOSE` 调 `dismiss()` 关闭。

### 调用点片段

在既有 MOD 插件的 `onGameLoad(boolean newGame)` 内新增本节注册块 (该插件类归前置单元所有，本节只演示调用，不改动那个文件)：

```java
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInIntelServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInIntelServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInIntelServicesScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInDialogServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInDialogServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInDialogServicesScript());
    }
```

### 构建命令

在 `Starsector/mods/GearIndustry/` 执行：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含五个类的 `.class` 条目 (可用 `jar tf` 核对)。

## 关键故障实验：对话框选项可以点击但没有响应

选项正常显示，回调也被调用，但分支拿显示文本和内部 ID 比较。

**局部故障片段：**

```java
// 故障片段
public void optionSelected(String optionText, Object optionData) {
	if (OPTION_FACTION.equals(optionText)) {
		dialog.getTextPanel().addPara("显示势力关系");
	}
}
```

**局部修正片段：**

```java {3}
// 修正片段
public void optionSelected(String optionText, Object optionData) {
	if (OPTION_FACTION.equals(optionData)) {
		dialog.getTextPanel().addPara("显示势力关系");
	}
}
```

实际症状：点击 "查看势力关系" 后选项会产生正常的点击反馈，但文本面板没有新增内容，日志中的分支记录也缺失。

原因：`optionText` 的值是 "查看势力关系"，`OPTION_FACTION` 的值是内部 ID `faction`；文案与 ID 从设计上就是两套数据。

复验：依次点击势力、货舱和关闭选项，三次回调应分别命中 `faction`、`cargo`、`close`；修改任一显示文案后，分支行为仍保持不变。

## 验证方案

前置数据：既有 MOD 工程：`mod_info.json`、构建脚本与插件入口 `GearInModPlugin`[C6-S2]，以及脚本注册判断模式 [C9-S1]、本节五个类与调用点片段中的两个 `addScript` 注册块 (各带独立的 `REGISTER_MARK` 键)。

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含五个类的 `.class` 条目。
2. **新建生涯 (首次进入存档后第一帧)**：确认 `starsector.log` 按顺序出现两个脚本的日志行 (情报段 ×3、对话框段 1 行)，且**没有**旧复合演示的收尾 `done` 行。
3. **消息区**：演示帧后左下消息区出现两条 `GearIn` 消息 —— 点击第一条跳转 Intel 页签 (`addIntel` 的自动发布消息)、点击第二条无跳转 (`MessageIntel` 纯消息)。
4. **对话框**：游戏暂停并弹出演示对话框；依次点 "查看势力关系"(文本面板追加关系行、日志 `option selected: faction`)、"查看舰队货物"(追加货物行、日志 `option selected: cargo`)。
    - "关闭演示" 或按 ESC(日志 `option selected: close`、对话框关闭、游戏恢复)。

预期结果：

- 点 "查看势力关系"：文本面板追加一行 `势力关系：霸主 ... 海盗 ... 独立 ...`，日志出现 `dialog option selected: faction`；

- 点 "查看舰队货物"：文本面板追加 `舰队货物：补给 ...` 行，日志出现 `dialog option selected: cargo`；

- 点 "关闭演示"(或按 ESC)：对话框关闭，日志出现 `dialog option selected: close`。

    **读档**：不出现任何 `GearIn campaign services` 日志 (两个 `REGISTER_MARK` 标记均已置位、脚本不再注册)；演示遗留的对话框与消息不会随存档保存。

    **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。

## 故障分析

- **`showInteractionDialog` 返回 false 且对话框没有出现**：生涯 UI 已有对话框时，后续打开请求会被拒绝。

    - **解决方案**：调用前检查 `CampaignUIAPI.isShowingDialog()`，并记录返回值，在 UI 空闲时重试。

- **日志已有情报记录但 Intel 页没有条目**：`queueIntel` 会让情报等待通信中继可见性检查，舰队不在范围内且未设置 `forceAddNextFrame` 时，`getIntel()` 暂时查不到它。

    - **解决方案**：需要立即显示时改用 `addIntel`；需要延迟可见时配置 `setPostingLocation` 和 `setPostingRangeLY`。

- **情报始终不消失**：直接实现 `IntelInfoPlugin` 而未提供结束语义时，情报会一直保留在页面和存档中。

    - **解决方案**：继承 `BaseIntelPlugin`，并用 `endAfterDelay()` 或 `endImmediately()` 结束。

- **`getIntelTags(map)` 抛出 NPE**：该方法可能在 `map` 为 null 时调用，直接解引用 `map.getPlayerFleet()` 会使地图提示或 Intel 页渲染中止。

    - **解决方案**：先判 null，或调用已处理 null 的 `super.getIntelTags(map)`。

- **点击对话框选项后没有反应**：`optionSelected` 的 `optionText` 是显示文本，`optionData` 才是 `addOption` 传入的 ID。

    - **解决方案**：改用 `OPTION_FACTION.equals(optionData)` 判断分支。

- **对话框无法关闭**：未绑定关闭选项或 `setOptionOnEscape` 时，点击关闭和 ESC 都无法进入退出分支。

    - **解决方案**：用 `setOptionOnEscape(text, optionId)` 绑定 ESC，并在关闭回调中调用 `dismiss()`。

- **游戏在进入生涯前因服务注册中止**：`onApplicationLoad` 阶段的 `Global.getSector()` 仍为 null，在此访问生涯服务会抛出 NPE。

    - **解决方案**：把注册移到 `onGameLoad` 的一次性注册判断内。

- **`MessageIntel` 点击后跳转到意外页面**：把纯消息绑定 `MessageClickAction` 或加入情报管理器，会赋予它不符合用途的交互路径。

    - **解决方案**：纯消息只通过 `CampaignUIAPI.addMessage(intel)` 投入左下消息区；需要 Intel 页面条目时改用 `BaseIntelPlugin` 子类和 `addIntel`。

## 总结

本节把两个生涯服务入口做成两条可复用链，并在示例里组合验证：情报经 `getIntelManager()` 登记并随存档存活，对话框经 `showInteractionDialog()` 打开并受其返回值约束。两条链都挂在 `REGISTER_MARK` 一次性注册判断与 `Global` 阶段语义这套底座上。

记忆要点：

- `addIntel` 立即登记 (弹消息、绕过通信中继可见性检查)；`queueIntel` 才走中继可见性链路；intel 随存档保存。
- `showInteractionDialog` 可能返回 `false`；对话框选项比较用 `optionData` 不用 `optionText`。
- `getIntelTags(map)` 必须处理 map 为 null。

排查时沿数据文件 → 注册入口 → 运行使用方 → 可观察结果四个位置复核，比对着症状猜接口更快。

本节只覆盖两个入口的 "常用高频面"：Rules 侧对话框、`BarEventPlugin` 对话框入口与任务类情报 (`BaseMissionIntel`) 在逻辑上属于更深的主题，本节不展开。

## 建议参考项

- `com.fs.starfarer.api.Global`，全局入口：`getSector()`、`getLogger()`。
- `com.fs.starfarer.api.campaign.SectorAPI`：生涯引擎入口。
    - 情报与对话框入口都能从这里进入：`getIntelManager` / `getCampaignUI` / `getPersistentData` / `addScript`。
- `com.fs.starfarer.api.campaign.comm.IntelManagerAPI`，情报登记集合：`addIntel` / `queueIntel` / `removeIntel` / `removeAllThatShouldBeRemoved`。
- `com.fs.starfarer.api.campaign.comm.IntelInfoPlugin`，单条情报约定：`getIntelTags(map)` 必须容忍 null map。
- `com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`，MOD 自定义情报标准父类：结束倒计时、删除按钮、更新消息、默认图标语义。
- `com.fs.starfarer.api.impl.campaign.intel.MessageIntel`，左下消息区纯消息类：`new MessageIntel(...)` + `addLine(...)`。
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`，对话框本体：面板、选项行为 (ESC/确认/回显)、picker 族、`dismiss()`。
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`，对话框内容插件：七方法生命周期 (`init` → `optionSelected` → `advance` → `backFromEngagement`)。
- `com.fs.starfarer.api.campaign.OptionPanelAPI`，选项面板：`addOption` / `setEnabled` / `addSelector` / `addOptionConfirmation`。
- `com.fs.starfarer.api.campaign.TextPanelAPI`，文本面板：`addPara` / `highlightInLastPara` / `clear`。
- `com.fs.starfarer.api.campaign.CampaignUIAPI`，生涯 UI：`showInteractionDialog` / `addMessage` / `isShowingDialog`。
- `com.fs.starfarer.api.util.Misc`：文本与高亮颜色常量 (`getTextColor` / `getGrayColor` 等)。
- `com.fs.starfarer.api.impl.campaign.ids.Tags`：intel 标签常量。
- `data/scripts/gearin/campaign/`：本节五个示例类所在目录 (MOD 内相对路径)。

## 完整最小例

::: details 完整成品：GearInOneShotScript

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * 一次性演示脚本基类：挂载后第一次 advance() 执行一次子类实现的 runDemo() 并自停
 * (isDone() 返回 true，引擎在下一帧自动从脚本列表移除本脚本)。
 *
 * 两个服务演示脚本 (情报 / 对话框) 都继承本类，各自只实现 runDemo()，
 * 避免每个演示类重复 EveryFrameScript 的骨架方法。本类是抽象基类，不参与任何注册；
 * 各子类的注册在 onGameLoad 的 persistent data 门内各自执行 (见页面调用点片段)。
 */
public abstract class GearInOneShotScript implements EveryFrameScript {

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInOneShotScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runDemo();
    }

    /** 子类实现一次演示：只在第一次 advance() 时被调用，全部行为在一个调用内完成。 */
    protected abstract void runDemo();
}
```

:::

::: details 完整成品：GearInIntelServicesScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例 (情报段)：IntelManagerAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK
 * 一次性门控 (每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：创建自定义情报 GearInServicesIntel(extends BaseIntelPlugin)addIntel 登记、
 * hasIntel 验证、removeIntel 移除 (不留存档残留)；再用 MessageIntel 发一条纯消息
 * (左下消息区)。演示全程不遗留持久状态。
 */
public class GearInIntelServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_IntelServices";

    private static final Logger LOG = Global.getLogger(GearInIntelServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        IntelManagerAPI intelManager = sector.getIntelManager();
        GearInServicesIntel intel = new GearInServicesIntel();
        intelManager.addIntel(intel);
        boolean present = intelManager.hasIntel(intel);
        int intelCount = intelManager.getIntel(GearInServicesIntel.class).size();
        LOG.info(String.format(
                "GearIn campaign services intel: added present=%s count=%d (campaign message posted)",
                present, intelCount));
        intelManager.removeIntel(intel);
        boolean absent = !intelManager.hasIntel(intel);
        LOG.info(String.format(
                "GearIn campaign services intel: removed absent=%s count=%d",
                absent, intelManager.getIntel(GearInServicesIntel.class).size()));

        MessageIntel message = new MessageIntel(
                "GearIn 生涯服务速览：纯消息情报 (MessageIntel) 演示", Misc.getTextColor());
        message.addLine("addLine 追加的第二行：消息不绑定情报页面，只在左下消息区显示。",
                Misc.getGrayColor());
        CampaignUIAPI ui = sector.getCampaignUI();
        ui.addMessage(message);
        LOG.info("GearIn campaign services intel: message-only intel posted");
    }
}
```

:::

::: details 完整成品：GearInDialogServicesScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;

/**
 * 完整最小例 (对话框段)：InteractionDialogAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK
 * 一次性门控 (每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：用 CampaignUIAPI.showInteractionDialog(plugin, null) 打开演示对话框
 * (GearInServicesDialog implements InteractionDialogPlugin)，记录返回的 shown 布尔值
 * (已有对话框显示时返回 false，见 javadoc)；对话框由玩家点选选项后自行关闭，
 * 本脚本不留持久状态。
 */
public class GearInDialogServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_DialogServices";

    private static final Logger LOG = Global.getLogger(GearInDialogServicesScript.class);

    @Override
    protected void runDemo() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(new GearInServicesDialog(), null);
        LOG.info("GearIn campaign services dialog: shown=" + shown
                + " (interactionTarget=null; 玩家在对话框内点选选项后由选项回调关闭)");
    }
}
```

:::

::: details 完整成品：GearInServicesIntel

```java
package data.scripts.gearin.campaign;

import java.util.Set;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例 (情报插件)：自定义情报 (IntelInfoPlugin 的默认实现 BaseIntelPlugin 子类)。
 *
 * 绑定位置：由 GearInIntelServicesScript 经 IntelManagerAPI.addIntel(plugin) 登记
 * (addIntel 绕过可见性检查，立即置玩家可见时间戳并弹出左下消息)。
 *
 * 契约要点：getIcon() 返回 null 时 Intel 页面不显示图标 (IntelInfoPlugin
 * "40x40, no icon if null.")；getIntelTags(map) 必须处理 map 为 null
 * ("Method NEEDS to handle map being null gracefully.")—— 本类先调
 * super.getIntelTags(map)，基类默认实现内部已做 null 判断，再追加自定义 tag。
 */
public class GearInServicesIntel extends BaseIntelPlugin {

    @Override
    public String getName() {
        return "GearIn 服务速览 (自定义情报演示)";
    }

    @Override
    public String getIcon() {
        return null; // null = 无图标，Intel 页面使用默认图标
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("这是一条由 GearInIntelServicesScript 创建并立即移除的演示情报。", Misc.getTextColor(), 0f);
        info.addPara("登记入口 addIntel(plugin) 会直接置玩家可见时间戳并弹出左下消息；"
                + "本演示在同一帧调用 removeIntel(plugin) 移除，不留存档残留。", Misc.getGrayColor(), 10f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        // 基类默认实现已处理 map == null(map 非 null 时才计算 INTEL_LOCAL 距离)
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        return tags;
    }
}
```

:::

::: details 完整成品：GearInServicesDialog

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例 (对话框插件)：演示交互对话框 (InteractionDialogPlugin)。
 * 绑定位置：GearInDialogServicesScript 经 CampaignUIAPI.showInteractionDialog(plugin,
 * target) 打开 (target 传 null)。optionSelected 的 optionText 是显示文本、optionData 是
 * addOption 传入的 id，分支判断必须用 optionData；未使用的回调保留空实现。
 */
public class GearInServicesDialog implements InteractionDialogPlugin {

    public static final String OPTION_FACTION = "faction";
    public static final String OPTION_CARGO = "cargo";
    public static final String OPTION_CLOSE = "close";

    private static final Logger LOG = Global.getLogger(GearInServicesDialog.class);

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        String target = dialog.getInteractionTarget() == null ? "null(本演示未绑定交互目标)"
                : dialog.getInteractionTarget().getName();
        dialog.getTextPanel().addPara("GearIn 生涯服务速览：交互对话框演示。交互目标=" + target
                + "。选择下方选项查看对应服务信息，或选择关闭结束对话框。");
        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption("查看势力关系", OPTION_FACTION, "显示玩家与霸主、海盗、独立势力的关系数值与等级");
        options.addOption("查看舰队货物", OPTION_CARGO, "显示玩家舰队补给、燃料、船员与信用状态");
        options.addOption("关闭演示", OPTION_CLOSE, "关闭本演示对话框");
        dialog.setOptionOnEscape("关闭演示", OPTION_CLOSE);
        LOG.info("GearIn campaign services dialog init: interactionTarget=" + target);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_FACTION.equals(optionData)) {
            FactionAPI playerFaction = Global.getSector().getPlayerFaction();
            dialog.getTextPanel().addPara(String.format(
                    "势力关系：霸主 %s(%s) 海盗 %s(%s) 独立 %s(%s)",
                    fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                    playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.PIRATES)),
                    playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                    playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_FACTION);
        } else if (OPTION_CARGO.equals(optionData)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            dialog.getTextPanel().addPara(String.format(
                    "舰队货物：补给 %.0f 燃料 %.0f 船员 %d 陆战队员 %d 信用 %.0f 已用空间 %.0f/%.0f",
                    cargo.getSupplies(), cargo.getFuel(), cargo.getCrew(), cargo.getMarines(),
                    cargo.getCredits().get(), cargo.getSpaceUsed(), cargo.getMaxCapacity()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CARGO);
        } else if (OPTION_CLOSE.equals(optionData)) {
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CLOSE);
            dialog.dismiss();
        }
    }

    // 未使用的回调保留空实现 (InteractionDialogPlugin 要求全部实现)。
    @Override public void optionMousedOver(String optionText, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }
}
```

:::
