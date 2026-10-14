# 选择生涯插件

与指定实体交互时，可以选择自己的对话；与指定舰队开战时，也可以改用专用战场。`CampaignPlugin` 在这些请求发生时提供候选，本节用它处理 Foundry 和测试巡逻队，再在市场 Memory 读取时生成当前事实，说明选择规则怎样进入实际生涯功能。

*本节预期阅读时间：\~25 分钟。*

*本节预期操作时间：\~50 分钟。*

## 学习目标

完成后，您将能够：

- 使用稳定插件 ID 管理注册替换、读档重登和显式注销；
- 根据目标对象返回 `PluginPick`，并用 `null` 把不匹配对象交给其他插件；
- 使用 `PickPriority` 控制多个非空候选之间的最终选择；
- 在 `updateMarketFacts()` 中使用传入 Memory 写入零过期时间的动态事实。

## 前置知识

- [C7-S1] 的 `BaseModPlugin.onGameLoad()` 与新档、读档入口；
- [C10-S4] 的实体与市场 Memory 读写；
- [C11-S1] 的测试舰队创建和投放；
- [C14-S2] 的回调注册、对象过滤和读档恢复边界。

## 登记本次生涯的选择规则

创建 `src/data/scripts/gearin/campaign/GearInCampaignPlugin.java`，继承 `BaseCampaignPlugin`。基类为全部选择与事实方法准备了默认实现，子类只覆写本次交互、战场和市场事实；其余请求继续返回默认结果。

在 `GearInModPlugin` 添加该类的 import，向已有 `onGameLoad(...)` 追加局部登记，沿用 `sector`：

```java
sector.registerPlugin(new GearInCampaignPlugin());
```

完整类的 `getId()` 返回 `GearIn_campaign_plugin`，`isTransient()` 返回 `true`。选择规则没有需要保存的字段，目标状态留在实体与市场 Memory；每次载入生涯重新创建规则实例。

**相同非空 ID 的登记会替换原插件。** 当前入口重复执行时，注册表仍保留一份实现；游戏随后需要对话或战场，才调用该实例的选择方法。

## 标记 Foundry 与测试巡逻队

对话目标使用 Foundry 的主实体，市场事实使用 Foundry 的 `MarketAPI`，战场目标使用巡逻队。三个对象各保存自己的标记，选择方法读取实际传入对象。

在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 已取得 `market` 后，加入局部片段，并添加 `SectorEntityToken`、`MemoryAPI` 与本节插件的 import：

```java
SectorEntityToken dialogTarget = market.getPrimaryEntity();
dialogTarget.getMemoryWithoutUpdate().set(
        GearInCampaignPlugin.DIALOG_TARGET_KEY, true);

MemoryAPI marketMemory = market.getMemoryWithoutUpdate();
marketMemory.set(GearInCampaignPlugin.MARKET_FACTS_TARGET_KEY, true);
marketMemory.unset(GearInCampaignPlugin.MARKET_READY_KEY);
boolean readyBeforeRefresh = marketMemory.getBoolean(
        GearInCampaignPlugin.MARKET_READY_KEY);
boolean readyAfterRefresh = market.getMemory().getBoolean(
        GearInCampaignPlugin.MARKET_READY_KEY);
LOG.info("GearIn campaign facts: before=" + readyBeforeRefresh
        + " after=" + readyAfterRefresh);
```

在 [C11-S1] 的 `GearInFleetGenerator` 投放前再给当前 `generatedFleet` 写入战场标记，局部片段如下：

```java
generatedFleet.getMemoryWithoutUpdate().set(GearInCampaignPlugin.BATTLE_TARGET_KEY, true);
```

实体标记决定使用专用对话，市场标记决定计算 ready 事实。片段先清除旧 ready 值，再分别通过普通读取与事实刷新取得结果，二者的区别在后文展开。巡逻队则在开战前取得独立战场标记。

## 为标记实体选择交互对话框

玩家开始交互时，`pickInteractionDialogPlugin(...)` 接收当前目标。本例从实体的 `getMemoryWithoutUpdate()` 读取 `$GearIn_dialog_target`，命中后返回专用对话与优先级：

```java
if (interactionTarget == null
        || !interactionTarget.getMemoryWithoutUpdate()
                .getBoolean(DIALOG_TARGET_KEY)) {
    return null;
}
return new PluginPick<>(
        new GearInTargetDialog(interactionTarget),
        PickPriority.MOD_SPECIFIC);
```

**不匹配的请求返回 `null`。** 这样其他生涯插件继续提供自己的实现，当前规则只处理被标记的实体。标记在交互前写入，选择方法读取已有状态；调用 `getMemoryWithoutUpdate()` 也让判断保持在当前 Memory 上。

`GearInTargetDialog` 保存命中的目标，在初始化时显示 ID、标记与市场 ready，再提供关闭选项。Foundry 有市场，因此读取其当前事实；其他无市场目标显示 `false`。选项用稳定 `optionData` 判断，显示文字用于界面。

这个类只展示选择结果怎样进入对话。[C15-S1] 再展开三个面板、选项与关闭过程，本节先让玩家实际打开被选中的实现。

## 为标记舰队选择战场创建插件

战斗建立时，`pickBattleCreationPlugin(...)` 同样接收生涯实体。先限定为 `CampaignFleetAPI`，再检查 `$GearIn_battle_target`，命中后返回 `GearInObjectiveBattleCreationPlugin`：

```java
if (!(opponent instanceof CampaignFleetAPI)
        || !opponent.getMemoryWithoutUpdate().getBoolean(BATTLE_TARGET_KEY)) {
    return null;
}
return new PluginPick<>(
        new GearInObjectiveBattleCreationPlugin(),
        PickPriority.MOD_SPECIFIC);
```

这个战场类继承原版 `BattleCreationPluginImpl`。在 `super.initBattle(...)` 前把 `context.forceObjectivesOnMap` 设为 `true`，让父类创建地图时采用这个条件，双方舰队装入、地图尺寸与部署时间继续沿用父类处理。

`objectivesAllowed` 为 `true` 时，小规模战斗也生成战术目标；为 `false` 时仍按许可跳过。当前只改变这项生成条件。标记在战斗建立前准备，战场已经选定后再改 Memory，对这场已建立的战斗没有追溯作用。

## 比较候选优先级与插件 ID

每个选择请求都会调用当前注册插件的相应方法，收集非空 `PluginPick<T>`。它保存 `plugin` 与 `priority`：没有候选时返回 `null`，一份候选直接采用，多份则选择枚举序号最大的优先级。

### 选择优先级速查表

| 优先级 | 适用范围 | 选择关系 |
| --- | --- | --- |
| `CORE_GENERAL` | 原版通用实现 | 枚举序号最低 |
| `MOD_GENERAL` | MOD 全局替换 | 高于通用原版实现 |
| `CORE_SET` | 原版对象集合规则 | 高于 general 级 |
| `MOD_SET` | MOD 对一组对象的规则 | 高于 `CORE_SET` |
| `CORE_SPECIFIC` | 原版特定遭遇 | 高于 set 级 |
| `MOD_SPECIFIC` | MOD 特定对象或遭遇 | 高于 `CORE_SPECIFIC` |
| `HIGHEST` | 有依据的冲突裁决 | 枚举序号最高 |

本节只处理标记的特定对象，采用 `MOD_SPECIFIC`。下面的局部片段把同一对话包装为 set 与 specific 候选，后者优先：

```java
PluginPick<InteractionDialogPlugin> setPick = new PluginPick<>(
        new GearInTargetDialog(dialogTarget), PickPriority.MOD_SET);
PluginPick<InteractionDialogPlugin> specificPick = new PluginPick<>(
        new GearInTargetDialog(dialogTarget), PickPriority.MOD_SPECIFIC);
```

相同优先级的竞争没有公开稳定的胜者约定，需要唯一结果时，让命中条件互斥或明确区分优先级。编者认为优先级应当表达接管范围：全局替换给特定遭遇保留位置，剧情对象再由专用规则处理。使用 `HIGHEST` 时，当前候选还会压过同一目标上的其他专用规则，因此选择它要有实际冲突与明确的裁决依据。

### ID 决定注册替换

插件 ID 与优先级处理不同关系。ID 在登记时决定是否替换旧对象，优先级在请求时决定采用哪份候选。连续登记同一 ID 的局部实验如下：

```java
sector.registerPlugin(new GearInCampaignPlugin());
sector.registerPlugin(new GearInCampaignPlugin());
```

第二次登记成为当前实例。基类默认 ID 为 `null`，这时每次登记追加对象，所有实例都会参与选择与事实更新；本例显式提供稳定 ID，便于替换和注销。

`isTransient()` 决定插件是否进入存档。基类默认 `true`，本例每次 `onGameLoad()` 重登；确实保存插件字段的实现可以返回 `false`，在新档时登记并随档恢复。长期对象状态放在哪里，要结合功能实际持有的对象决定。

## 在读取 Memory 时更新市场 facts

生涯插件还提供读取时计算的 facts。对象的 `getMemory()` 调用当前插件，传入对象与 Memory；`getMemoryWithoutUpdate()` 则取得已有值。本节的 `updateMarketFacts(...)` 检查市场标记，向传入 `memory` 写入 `$GearIn_campaign_plugin_ready`。

因此准备片段在 `unset(...)` 后，`readyBeforeRefresh` 为 `false`；调用 `market.getMemory()` 后，更新方法写入 ready，`readyAfterRefresh` 为 `true`。对话也用目标市场的 `getMemory()`，显示本次读取后的事实。

### Facts 更新入口速查表

| 更新方法 | 触发对象 | 典型用途 |
| --- | --- | --- |
| `updateEntityFacts(entity, memory)` | 生涯实体 | 位置、标签和交互条件 |
| `updatePersonFacts(person, memory)` | 人物 | 身份、关系与角色状态 |
| `updateFactionFacts(faction, memory)` | 势力 | 势力关系与全局规则 |
| `updateGlobalFacts(memory)` | 星区全局 Memory | 全局进度与环境条件 |
| `updatePlayerFacts(memory)` | 玩家角色数据 | 玩家能力与任务条件 |
| `updateMarketFacts(market, memory)` | 市场 | 规模、产业、管理与市场交互条件 |

其他对象各有对应事实入口，仍使用当前方法收到的对象与 Memory。动态键采用 MOD 前缀，并用 `memory.set(key, value, 0f)` 写本次事实，下一次推进过期，后续读取时重新计算。

::: warning facts 更新中使用传入的 Memory
在 `updateMarketFacts()` 中再次调用 `market.getMemory()`，会递归触发相同更新。当前值和输入条件都从方法参数 `memory` 读取，写入也使用这份对象。
:::

## 注销插件并观察恢复结果

调用 `Global.getSector().unregisterPlugin(GearInCampaignPlugin.PLUGIN_ID)` 按稳定 ID 注销。当前插件退出后续选择与事实刷新，已经打开的对话和已经建立的战斗各自继续原流程。

再次交互时，其他候选重新处理目标。验证 facts 时先从 `getMemoryWithoutUpdate()` 清除 ready，再调用 `getMemory()`，才能确认本插件没有重新写入；现有 Memory 值与插件是否登记分别观察。

## 完整最小例

三个类保存到前述路径，登记和三类标记分别采用上面的当前入口。

::: details 完整成品：GearInCampaignPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 根据对象 Memory 选择交互与战场插件，并在市场 Memory 刷新时写入动态事实。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    public static final String PLUGIN_ID = "GearIn_campaign_plugin";
    public static final String DIALOG_TARGET_KEY = "$GearIn_dialog_target";
    public static final String BATTLE_TARGET_KEY = "$GearIn_battle_target";
    public static final String MARKET_FACTS_TARGET_KEY = "$GearIn_market_facts_target";
    public static final String MARKET_READY_KEY = "$GearIn_campaign_plugin_ready";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(
            SectorEntityToken interactionTarget) {
        if (interactionTarget == null
                || !interactionTarget.getMemoryWithoutUpdate().getBoolean(DIALOG_TARGET_KEY)) {
            return null;
        }
        return new PluginPick<>(
                new GearInTargetDialog(interactionTarget),
                PickPriority.MOD_SPECIFIC);
    }

    @Override
    public PluginPick<BattleCreationPlugin> pickBattleCreationPlugin(
            SectorEntityToken opponent) {
        if (!(opponent instanceof CampaignFleetAPI)
                || !opponent.getMemoryWithoutUpdate().getBoolean(BATTLE_TARGET_KEY)) {
            return null;
        }
        return new PluginPick<>(
                new GearInObjectiveBattleCreationPlugin(),
                PickPriority.MOD_SPECIFIC);
    }

    @Override
    public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
        if (memory.getBoolean(MARKET_FACTS_TARGET_KEY)) {
            memory.set(MARKET_READY_KEY, true, 0f);
        }
    }
}
```

:::

::: details 完整成品：GearInTargetDialog

```java
package data.scripts.gearin.campaign;

import java.util.Collections;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 显示 Campaign 插件选中的交互目标及其市场事实。
 */
public class GearInTargetDialog implements InteractionDialogPlugin {

    private static final String OPTION_CLOSE = "GearIn_close_campaign_plugin_dialog";

    private final SectorEntityToken target;
    private InteractionDialogAPI dialog;

    public GearInTargetDialog(SectorEntityToken target) {
        this.target = target;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        boolean dialogTarget = target.getMemoryWithoutUpdate()
                .getBoolean(GearInCampaignPlugin.DIALOG_TARGET_KEY);
        MarketAPI market = target.getMarket();
        boolean marketReady = market != null
                && market.getMemory().getBoolean(GearInCampaignPlugin.MARKET_READY_KEY);

        dialog.getTextPanel().addPara("GearIn CampaignPlugin 选择了这个交互对话框。");
        dialog.getTextPanel().addPara("目标 ID：" + target.getId());
        dialog.getTextPanel().addPara("对话标记：" + dialogTarget);
        dialog.getTextPanel().addPara("市场 ready fact：" + marketReady);
        dialog.getOptionPanel().addOption("关闭", OPTION_CLOSE);
        dialog.setOptionOnEscape("关闭", OPTION_CLOSE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_CLOSE.equals(optionData)) {
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
        return target;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }
}
```

:::

::: details 完整成品：GearInObjectiveBattleCreationPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * 沿用原版战场创建流程，并为匹配的战斗强制生成战术目标。
 */
public class GearInObjectiveBattleCreationPlugin extends BattleCreationPluginImpl {

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI api) {
        context.forceObjectivesOnMap = true;
        super.initBattle(context, api);
    }
}
```

:::

## 验证方案

前置数据：已启用包含 [C14-S3] 三个完整类的测试 MOD；准备一个可交互实体、一支可开战的敌舰队和一个市场，并能够在三者 Memory 中写入测试标记。

1. 编译 `GearInCampaignPlugin`、`GearInTargetDialog` 和 `GearInObjectiveBattleCreationPlugin`。
2. 在 `onGameLoad()` 注册 `GearInCampaignPlugin`，核对 `getId()` 返回 `GearIn_campaign_plugin` 且 `isTransient()` 返回 `true`。
3. 在同一次 `onGameLoad(...)` 使用前面的两次同 ID 登记，比较交互与 facts 行为仍执行一份当前实现。
4. 给交互实体写入 `$GearIn_dialog_target` 并开始交互，核对对话框显示的目标 ID、标记和市场 ready 状态。
5. 与未标记实体交互，确认当前插件返回 `null`，并观察其他插件提供的交互结果。
6. 给测试敌舰队写入 `$GearIn_battle_target` 并开战，观察小规模战场中的战术目标。
7. 与未标记敌舰队开战，确认战场采用其他 Campaign 插件提供的规则。
8. 给市场写入 `$GearIn_market_facts_target`，依次读取 `getMemoryWithoutUpdate()` 和 `getMemory()`，比较 ready 键。
9. 阅读 set 与 specific 的局部候选，按优先级表比较最终选择；实际完整插件采用 `MOD_SPECIFIC`，匹配与未匹配对象各自观察。
10. 保存并读档，确认 `onGameLoad()` 重新登记 transient 插件，插件集合中只有一个 `GearIn_campaign_plugin`。
11. 调用 `unregisterPlugin(GearInCampaignPlugin.PLUGIN_ID)`，再次测试对话与战场选择。
12. 注销后从市场 Memory 清除 ready 键，再调用 `market.getMemory()`，确认本插件不再写入该事实。

预期结果：

- 三个 Java 类编译完成，加载期没有类缺失或方法签名错误；
- 相同非空 ID 的新插件替换旧实例，集合中只有当前实例；
- 标记实体使用 `GearInTargetDialog`，对话框显示正确目标 ID、对话标记和市场 ready 状态；
- 未标记实体使当前 picker 返回 `null`，其他插件仍可提供交互；
- 标记敌舰队使用 `GearInObjectiveBattleCreationPlugin`，战场生成战术目标；
- 未标记敌舰队不使用本节战场插件；
- `getMemoryWithoutUpdate()` 不触发 ready 写入，`getMemory()` 刷新后 ready 为 `true`；
- `MOD_SPECIFIC` 候选高于 `MOD_SET` 候选并成为最终结果；
- 读档后 transient 插件重新登记，稳定 ID 避免重复实例；
- 注销后本插件停止参与新的对话、战场和市场 facts 更新。

## 故障分析

- **读档后所有自定义选择都消失**：插件返回 transient，但 `onGameLoad()` 没有重新登记实例。
    - **解决方案**：每次 `onGameLoad()` 调用 `registerPlugin(new GearInCampaignPlugin())`。

- **相同插件 ID 的两个实现没有并存**：注册器把非空 ID 作为替换键，新实例登记前会移除旧实例。
    - **解决方案**：需要竞争的插件使用不同稳定 ID；同一功能的新版本继续使用相同 ID 完成替换。

- **每次读档后 picker 调用次数增加**：`getId()` 返回 `null`，每次登记都向集合追加新实例。
    - **解决方案**：让 `getId()` 始终返回 `GearIn_campaign_plugin`。

- **未标记实体也打开自定义对话框**：交互 picker 在条件不匹配时仍返回了 `PluginPick`。
    - **解决方案**：先判空并检查 `$GearIn_dialog_target`，任一条件不成立时返回 `null`。

- **`MOD_SPECIFIC` 候选没有获选**：另一个插件返回了枚举序号更高的优先级，或本插件因过滤条件返回 `null`。
    - **解决方案**：记录每个候选的命中条件和 `PickPriority`，核对目标 Memory 与全部非空候选。

- **标记敌舰队的小规模战斗没有战术目标**：战场插件在父类创建地图之后才修改 `forceObjectivesOnMap`。
    - **解决方案**：在 `super.initBattle(context, api)` 之前设置 `context.forceObjectivesOnMap = true`。

- **读取 `getMemoryWithoutUpdate()` 后 ready 仍为 `false`**：该入口只返回当前 Memory，不执行 Campaign 插件的 facts 更新。
    - **解决方案**：需要刷新事实时调用 `market.getMemory()`，再从返回对象读取 ready 键。

- **读取市场 Memory 时发生递归调用**：`updateMarketFacts()` 内再次调用了 `market.getMemory()`。
    - **解决方案**：只读取和写入方法参数 `memory`，需要市场属性时直接使用参数 `market`。

- **动态 facts 在 Memory 中形成长期过期项**：更新方法为每次读取都重算的键设置了正数期限。
    - **解决方案**：使用 `memory.set(MARKET_READY_KEY, true, 0f)` 写入当前刷新事实。

- **调用注销后插件仍在参与选择**：传给 `unregisterPlugin()` 的字符串与插件 `getId()` 不一致。
    - **解决方案**：注销时统一使用 `GearInCampaignPlugin.PLUGIN_ID`。

## 总结

Foundry 的交互与巡逻队战场在实际请求中取得专用候选，目标标记决定命中范围，优先级决定候选竞争。稳定插件 ID 负责登记替换与注销，选择结果再进入对话或地图创建。

市场 facts 在读取 Memory 时更新，使用传入对象计算当前值。长期目标状态与读取时事实分别保存，注册、匹配与刷新各自明确，生涯插件就能与其他功能共同处理当前请求。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：插件 ID、存档性质、选择优先级、picker 和 facts 更新合同；
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：全部 Campaign 插件方法的默认实现；
- `com.fs.starfarer.api.PluginPick`：候选插件与 `PickPriority` 的组合对象；
- `com.fs.starfarer.api.campaign.SectorAPI`：Campaign 插件注册与注销入口；
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互对话框的初始化、选项和状态接口；
- `com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl`：原版舰队装载、地图创建和部署规则实现；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标标记、动态 facts 和过期时间读写。
