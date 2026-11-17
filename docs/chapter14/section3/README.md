# 选择生涯插件

空间站对话、战斗创建、自动结算乃至 AI 核心插件的调度，都由星区向注册过的 `CampaignPlugin` 逐个问询后按 `PickPriority` 裁决。只要守住优先级的让位规矩，就能在不改动原版规则的前提下精准接管某一类或某一座实体。

这节从最常用的对话入口切入：齿轮工业对玩家挂着敌意标记期间，把 Nipher Foundry 的默认停靠菜单换成拒客的闭门羹。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C14-S2] 的 `$GearIn_patrol_grudge` 敌意标记，本节按它决定是否拦截停靠；
- [C12-S1] 的 Foundry 空间站，实体 ID 为 `GearIn_foundry`；
- [C14-S1] 中持久登记与临时登记的选择原则。

## CampaignPlugin 的仲裁体系与扩展点

当需要决定某项核心玩法交由哪个插件处理时，星区会依次询问所有注册过的 `CampaignPlugin`。原版提供了基类 `BaseCampaignPlugin`，默认将 `isTransient()` 设为 `true`、把 `getId()` 和各路 `pick...` 方法默认返回 `null`，MOD 继承它之后，只覆写自己关心的 `pick...` 方法即可 —— **不想插手的情况必须返回 `null`，把控制权留给其他 MOD 和原版**。

除了最常用的对话入口，`CampaignPlugin` 还覆盖了一整套系统级扩展接口：

| 方法 | 接管的能力 |
| ---- | ---------- |
| `pickInteractionDialogPlugin(...)` | 与星图实体交互时弹出的对话框 |
| `pickBattleCreationPlugin(...)` | 进入战术战斗时如何铺设战场 |
| `pickBattleAutoresolverPlugin(...)` | 双方不见面时的自动战斗结算算法 |
| `pickFleetInflater(...)` / `pickAutofitPlugin(...)` | 舰队自动配装生成与改装界面的自动配船策略 |
| `pickAICoreAdminPlugin(...)` / `pickAICoreOfficerPlugin(...)` | AI 核心作为殖民地管理员或舰队军官时的插件行为 |
| `pickImmigrationPlugin(...)` | 市场人口增长与移民计算模型 |

## PickPriority 优先级规矩

多个插件都对同一件事返回了非 `null` 的 `PluginPick`，就靠 `PickPriority` 分胜负。从低到高常用的几档分别为：

| 优先级 | 定位 | 适用场景 |
| ------ | ---- | -------- |
| `CORE_GENERAL` | 原版回退 | `CoreCampaignPluginImpl` 默认走 Rules 对话时使用的最低级别 |
| `MOD_GENERAL` | MOD 全局替换 | 打算整体改写原版所有行星或所有战斗流程时使用 |
| `MOD_SET` | MOD 分类替换 | 接管符合同一条件的集合，例如 "所有特定气候行星" |
| `MOD_SPECIFIC` | MOD 单点接管 | 针对某座具体空间站、某支事件舰队或特定前置条件接手 |

原版的 Rules 系统靠最低的 `CORE_GENERAL` 接住绝大多数常规停靠与对话，因此您的 MOD 返回任何 `MOD_*` 级别都能明确盖过它。**不要图省事一律写 `HIGHEST`**：同优先级之间选中谁是未定义的，按实际作用范围老实填等级，`HIGHEST` 留给玩家自制整合包消解冲突用。

## 按条件接管停靠对话

Foundry 是一座固定 ID 的空间站，且只在玩家最近击败过齿轮工业巡逻队 (`$GearIn_patrol_grudge` 为 `true`) 时拒客，因此范围属于精确单点，返回 `PickPriority.MOD_SPECIFIC`：

```java
if (interactionTarget == null || !GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
    return null;
}
if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
    return null;
}
return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
```

这段逻辑还有几处运行细节需要注意：

- 敌意标记是 60 天自动过期的限时键。期限一到 `GRUDGE_KEY` 变回 `false`，插件立刻自动返回 `null`，停靠对话无需额外写回切逻辑就会恢复为原版 Rules 菜单；
- **每次都应当 `new` 一个全新的对话插件实例**，不要把对话插件缓存成成员变量复用 —— `InteractionDialogPlugin` 内部通常会持有当次弹出的 `dialog` 窗口和选项上下文，复用旧实例会把上一轮的面板引用带进下一场交互。

## 对话界面插件：InteractionDialogPlugin

`InteractionDialogPlugin` 掌控着一个弹窗的完整交互生命周期，而 `InteractionDialogAPI` 把界面拆成了几块可直接操控的面板：

- `dialog.getTextPanel()`：主文本流区域，用 `addPara(...)` 逐段输出港务广播或剧情文字；
- `dialog.getOptionPanel()`：下方选项列表，用 `addOption(text, data)` 添加按钮，其中第二个参数 `data` 推荐传自定义枚举，在 `optionSelected(text, data)` 里用 `==` 比对，比拼字符串可靠得多；
- `dialog.getVisualPanel()`：左侧视觉框，可按需展示星球预览、人物立绘或舰船模型 (如 `showPersonInfo(...)`、`showPlanetInfo(...)`)；
- `optionMousedOver(...)`、`advance(...)` 与 `backFromEngagement(...)`：分别响应鼠标悬停预览、逐帧动画以及在对话里触发战斗后返回弹窗续接剧情。

本节只先演示最极简的一句拒绝与关闭窗口 (`dialog.dismiss()`)；需要多分支配置、文本动态拼装的 Rules 对话表和完整酒吧多轮流程，将在第 15、16 章展开。

```java
@Override
public void init(InteractionDialogAPI dialog) {
    this.dialog = dialog;
    dialog.getTextPanel().addPara("Nipher Foundry 的港务频道只回了一句话：近期击败过齿轮工业巡逻队的舰队，不予停靠。");
    dialog.getOptionPanel().addOption("离开", Option.LEAVE);
}

@Override
public void optionSelected(String optionText, Object optionData) {
    if (optionData == Option.LEAVE) {
        dialog.dismiss();
    }
}
```

## 登记插件与幂等规则

在 `GearInModPlugin.onGameLoad(...)` 中注册插件：

```java
sector.registerPlugin(new GearInCampaignPlugin());
```

插件本身不存任何跨存档状态，`isTransient()` 返回 `true` 后每次读档重新注册即可，对玩家热插拔 MOD 更友好；同时 `getId()` 返回的 `"GearIn_campaign_plugin"` 充当唯一去重键 —— 同 ID 插件再次注册时新实例直接覆盖旧实例，不会在列表里叠出两份。

## 完整的生涯插件与对话

`src/data/scripts/gearin/campaign/GearInCampaignPlugin.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/** 齿轮工业的生涯插件：玩家最近击败过齿轮工业的巡逻队时，Foundry 改用拒绝停靠的对话。 */
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
        if (interactionTarget == null || !GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
            return null;
        }
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
            return null;
        }
        return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
    }
}
```

`src/data/scripts/gearin/campaign/GearInDockRefusalDialog.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/** Foundry 的港务频道拒绝停靠：只说明原因，留一个离开的选项。 */
public class GearInDockRefusalDialog implements InteractionDialogPlugin {

    private enum Option {
        LEAVE
    }

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getTextPanel().addPara("Nipher Foundry 的港务频道只回了一句话：近期击败过齿轮工业巡逻队的舰队，不予停靠。");
        dialog.getOptionPanel().addOption("离开", Option.LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.LEAVE) {
            dialog.dismiss();
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

执行 `gradle jar`，重启游戏，**新建一份存档**：

- 正常停靠 Nipher Foundry，弹出原版 Rules 停靠菜单；
- 按 [C14-S2] 击败一支齿轮工业巡逻队后再次靠近 Foundry，弹窗直接变为拒绝停靠提示与 `"离开"` 选项；
- 点击 `"离开"` 后回到大地图；
- 控制台执行 `runcode Global.getSector().getMemoryWithoutUpdate().unset("$GearIn_patrol_grudge")` 清掉标记后再停靠，恢复为原版对话。

## 常见问题

- **敌意标记生效期间停靠依然打开了原版界面**：插件未在 `onGameLoad(...)` 登记，或者 `PickPriority` 低于处理该交互的原版/他方插件。
    - **解决方案**：核对 `sector.registerPlugin(...)` 调用，并将返回优先级设为 `PickPriority.MOD_SPECIFIC`。

- **所有空间站或行星全都变成了拒绝停靠**：`pickInteractionDialogPlugin(...)` 里忘了过滤 `interactionTarget.getId()`。
    - **解决方案**：确认仅在目标 ID 等于 `GearInFoundryGenerator.STATION_ID` 且满足敌意条件时返回对话插件，其余情况返回 `null`。

- **点击 `"离开"` 毫无反应**：`init(...)` 漏存 `this.dialog = dialog`，或 `optionSelected(...)` 比对的对象与 `addOption(...)` 传入的数据不一致。
    - **解决方案**：在 `init(...)` 保存 `dialog` 引用，统一用枚举比较选项。

- **读档后接管失效**：在 `isTransient()` 为 `true` 的前提下把注册放进了 `onNewGame()`。
    - **解决方案**：临时插件统一在 `onGameLoad(...)` 注册。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：全套 `pick...` 扩展方法与 `PickPriority` 枚举等级定义。
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：各方法默认返回 `null` 的标准基类。
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：弹窗生命周期与战斗返回流回调定义。
- `com.fs.starfarer.api.impl.campaign.tutorial.TutorialRespawnDialogPluginImpl`：原版最精简的对话插件实现，可直接对照枚举选项与 `dismiss()` 用法。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：原版核心生涯插件，Rules 对话的起点。
