# 选择生涯插件

被齿轮工业记了仇，Foundry 就不该再像平常那样让人随便停靠。可停靠时弹出的对话是原版的，MOD 想换掉它，得先让游戏在打开对话的那一刻来问一声。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C14-S2] 的 `$GearIn_patrol_grudge` 记仇标记，本节按它决定是否拒绝停靠；
- [C12-S1] 的 Foundry 空间站，ID 为 `GearIn_foundry`；
- [C14-S1] 中临时登记与随存档保存的区别，生涯插件同样适用。

## 游戏在哪些时刻来问

玩家点击一个实体、准备开打一场战斗、需要给舰队装配时，游戏会向所有登记过的生涯插件逐个询问：这一次由谁来处理。插件实现 `CampaignPlugin` 接口，每一种询问对应一个 `pick...` 方法，常用的几个：

| 方法 | 询问的内容 |
| ---- | ---------- |
| `pickInteractionDialogPlugin(...)` | 与某个实体交互时用哪个对话 |
| `pickBattleCreationPlugin(...)` | 一场战斗的战场如何生成 |
| `pickBattleAutoresolverPlugin(...)` | 自动结算如何计算 |
| `pickFleetInflater(...)` | 舰队的装配如何随机化 |

**不处理的情况返回 `null`，交给别的插件和原版。** 原版的 `BaseCampaignPlugin` 对每个方法都返回 `null`，继承它，只覆写需要的那一个。

## 优先级

多个插件都想处理同一件事时，按返回的 `PickPriority` 决定。接口注释对每一级都有说明，MOD 常用的是这三级：

| 优先级 | 适用 |
| ------ | ---- |
| `MOD_GENERAL` | 整体替换某一类功能，例如所有行星的交互 |
| `MOD_SET` | 某一类对象，例如所有丛林世界 |
| `MOD_SPECIFIC` | 某个具体对象，例如这一座空间站 |

原版自己的 `CoreCampaignPluginImpl` 用最低的 `CORE_GENERAL` 返回基于 Rules 的对话，几乎所有停靠、交谈都走这一条，MOD 的任何一级都能盖过它。

Foundry 是一个具体的空间站，用 `MOD_SPECIFIC`。注释中特别写明，同一优先级有多个插件时，选中哪一个是不确定的，所以优先级要按实际范围选，不要为了保险一律往高处填。`HIGHEST` 留给整合包解决冲突用。

## 只在记仇时接手

`pickInteractionDialogPlugin(...)` 收到的是玩家正在交互的实体。不是 Foundry，或者齿轮工业最近没有记仇，就返回 `null`：

```java
if (!GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
    return null;
}
if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
    return null;
}
return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
```

记仇标记是 60 天的限时值。60 天之后标记自行消失，插件不再接手，Foundry 又恢复成原版的停靠对话，不需要另外写解除的逻辑。

每次都返回一个新的对话对象。对话插件会在 `init(...)` 里记下这一次的对话窗口，复用旧对象，下一次交互就会拿到上一次的残留状态。

## 拒绝停靠的对话

对话插件实现 `InteractionDialogPlugin`。游戏打开对话时调用 `init(...)`，玩家点击选项时调用 `optionSelected(...)`：

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

`addOption(text, data)` 的第二个参数是选项的数据，玩家选中时原样传回 `optionSelected(...)`。原版的对话插件习惯用一个枚举作选项数据，比对字符串更不容易写错。`dismiss()` 关闭对话，玩家回到星图。

接口里的其余方法这里用不上，空着即可。这段对话只有一句话和一个选项，港务频道本来就没打算多说什么；完整的多轮对话放在后面的对话插件章节。

## 登记插件

在 `GearInModPlugin.onGameLoad(...)` 中加入：

```java
sector.registerPlugin(new GearInCampaignPlugin());
```

插件本身没有需要保存的数据，`isTransient()` 返回 `true`，每次读档登记一份。接口注释也是这样建议的：不需要存数据的插件都应当是临时的，用户中途加入或移除 MOD 时更不容易出问题。

`getId()` 返回一个固定的 ID。ID 相同的插件只会保留一份，新的登记会替换旧的，重复登记不会叠出两个插件。

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
        if (!GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
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

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 正常停靠 Nipher Foundry，出现的是原版的停靠对话；
- 按 [C14-S2] 的方法击败一支齿轮工业的巡逻队，再停靠 Foundry，对话只剩港务频道的那句话和 "离开"；
- 点击 "离开"，对话关闭，回到星图；
- 在控制台执行 `runcode Global.getSector().getMemoryWithoutUpdate().unset("$GearIn_patrol_grudge")` 清除标记，再次停靠，恢复为原版对话。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **记仇期间停靠，仍然是原版对话**：插件没有登记，或者优先级低于原版处理这次交互所用的级别。
    - **解决方案**：核对 `onGameLoad(...)` 中的 `registerPlugin(...)`，并使用 `MOD_SPECIFIC`。

- **所有空间站都变成了拒绝停靠**：没有比对交互目标的 ID。
    - **解决方案**：先确认 `interactionTarget.getId()` 等于 `GearIn_foundry`，其余情况返回 `null`。

- **点击 "离开" 没有反应**：`init(...)` 中没有保存对话窗口，或者 `optionSelected(...)` 比对的选项数据与添加时不同。
    - **解决方案**：在 `init(...)` 中保存 `dialog`，比对时使用同一个枚举值。

- **读档之后插件失效**：临时插件只在 `onNewGame()` 中登记了一次。
    - **解决方案**：登记放进 `onGameLoad(...)`。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：全部 `pick...` 方法，以及 `PickPriority` 每一级的注释。
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：全部方法返回 `null` 的基类。
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：对话插件的接口。
- `com.fs.starfarer.api.impl.campaign.tutorial.TutorialRespawnDialogPluginImpl`：原版最短的对话插件之一，枚举选项与 `dismiss()` 的写法。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：原版自己的生涯插件。
