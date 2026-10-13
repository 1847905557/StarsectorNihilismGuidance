# 选择生涯插件

监听器在事件发生后收到结果，生涯插件则参与 "这次使用哪个实现" 的选择。本节用 Memory 标记给 Foundry 选择专用对话，为测试舰队选择带战术目标的战场，再在市场 Memory 刷新时写入 ready 事实。三个入口各自匹配目标，稳定 ID 与优先级也分别承担登记和选择。

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

## 注册 transient Campaign 插件

`BaseCampaignPlugin` 为 `CampaignPlugin` 的全部 picker 和 facts 更新方法提供默认实现。默认 `getId()` 返回 `null`，默认 `isTransient()` 返回 `true`，picker 返回 `null`，facts 方法不写入状态。子类只覆写当前任务需要的方法，后续游戏版本向接口增加方法时也能继承基类提供的默认实现。

创建 `src/data/scripts/gearin/campaign/GearInCampaignPlugin.java` 及后面的对话、战场类。向现有 `GearInModPlugin.onGameLoad(...)` 追加局部注册，原回调保留：

```java
sector.registerPlugin(new GearInCampaignPlugin());
```

**稳定 ID 用于登记替换与注销。** `getId()` 非空时，再次登记同 ID 会移除旧对象并加入当前实例。

本节插件显式返回 transient。transient 插件不写入存档，每次新档或读档建立生涯状态后，都在 `onGameLoad()` 注册新实例。相同 ID 的替换机制使同一回调被重复执行时，集合中仍只有当前实现。

注册使用方法内已有的 `sector`，并给 MOD 入口添加该类的 import。当前插件显式选择 transient，读档后创建本次实例。

非 transient 插件会随存档保存，适合确实需要保存内部字段的插件，并在 `onNewGame()` 登记。Campaign 插件通常只提供无状态选择规则和 facts 计算，当前无状态选择规则采用 transient，长期状态继续保存到实际对象或 Memory。

## 理解 ID 替换与候选选择

注册替换和 picker 选择是两条不同链。ID 替换发生在 `registerPlugin()` 时，用于决定插件集合中保留哪些对象；picker 选择发生在游戏需要某项服务时，用于比较当前集合中各插件返回的候选。

用当前类连续登记即可观察同 ID 替换。局部片段返回第二次登记的当前实例，后续选择仍由它执行：

```java
sector.registerPlugin(new GearInCampaignPlugin());
sector.registerPlugin(new GearInCampaignPlugin());
```

`getId()` 返回 `null` 时，注册器没有替换键，每次登记都会追加新对象。无 ID 插件仍会参加所有 picker 与 facts 更新，重复登记可能产生多个候选或重复写入，因此 MOD 插件应提供稳定、带 MOD 前缀的非空 ID。

## 返回 PluginPick 或让出目标

游戏需要交互、战场、舰队 AI 或其他可替换服务时，会遍历全部 Campaign 插件，调用对应 picker，并收集非空的 `PluginPick<T>`。没有插件返回候选时，结果为 `null`；只有一个候选时直接使用其 `plugin`；多个候选按 `priority.ordinal()` 排序，取枚举序号最大的一个。

**先匹配目标，再提供候选。**匹配时返回插件实例与优先级；不匹配时返回 `null`，让其他 Campaign 插件继续提供默认或专用实现。需要让其他实现接手时返回 `null`，实际匹配时再创建自己的插件。

### 选择优先级速查表

| 优先级 | 适用范围 | 选择关系 |
| --- | --- | --- |
| `CORE_GENERAL` | 原版通用实现 | 枚举序号最低 |
| `MOD_GENERAL` | MOD 全局替换 | 高于通用原版实现 |
| `CORE_SET` | 原版对象集合规则 | 高于 general 级 |
| `MOD_SET` | MOD 对一组对象的规则 | 高于 `CORE_SET` |
| `CORE_SPECIFIC` | 原版特定遭遇 | 高于 set 级 |
| `MOD_SPECIFIC` | MOD 特定对象或遭遇 | 高于 `CORE_SPECIFIC` |
| `HIGHEST` | 明确解决全局冲突 | 枚举序号最高，使用条件最严格 |

本节两个 picker 都检查专用 Memory 标记，只处理明确标记的对象，因此使用 `MOD_SPECIFIC`。局部比较使用真实的目标对话类，把同一个输入分别包装为 set 与 specific 候选：

```java
PluginPick<InteractionDialogPlugin> setPick = new PluginPick<>(
        new GearInTargetDialog(dialogTarget), PickPriority.MOD_SET);
PluginPick<InteractionDialogPlugin> specificPick = new PluginPick<>(
        new GearInTargetDialog(dialogTarget), PickPriority.MOD_SPECIFIC);
```

多个候选具有相同优先级时，排序后的先后结果没有公开稳定保证。业务规则需要唯一胜者时，应通过互斥条件或不同优先级建立明确判据。

## 准备三个独立目标

本节使用 Foundry 主实体、测试巡逻队和 Foundry 市场，三个键分别限定交互、战场与 facts。

在 [C12-S1] 的 `GearInEconomyProbe.ProbeListener.economyUpdated()` 中已取得 `market`，加入局部片段：

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

市场片段需要 `SectorEntityToken`、`MemoryAPI` 与本节插件的 import。三个键分别保留各自对象范围，交互标记与战场标记各自选择。`readyBeforeRefresh` 读取当前保存值，`readyAfterRefresh` 则来自 Campaign 插件完成 facts 更新后的 Memory。

## 为标记实体选择交互对话框

交互 picker 接收玩家正在接触的 `SectorEntityToken`。本节使用 `$GearIn_dialog_target` 标记具体实体；判断读取 `getMemoryWithoutUpdate()`，避免只为决定 picker 就触发另一轮 facts 更新。对话会话的完整 API、三个面板和关闭语义由 [C15-S1] 负责，本节只保留 picker 如何把目标交给插件的桥接关系。

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

测试对象需要在玩家开始交互前写入标记。picker 只读取当前 Memory，不会自行创建或修改目标标记。

`GearInTargetDialog` 保存 picker 传入的目标。初始化时显示目标 ID、对话标记和目标市场的 ready fact，并提供关闭选项。目标由已经命中的 picker 提供；没有市场的目标以 `false` 显示 ready，Foundry 则按当前市场 facts 读取。选项分支使用稳定的 `optionData`，显示文本只负责界面呈现。这个类是 picker 返回插件的最小桥接示例；需要完整交互会话时使用 [C15-S1] 的 `GearInConversationDialog`。

## 为标记舰队选择战场创建插件

战场 picker 的参数类型仍是 `SectorEntityToken`，因为战斗对手来自生涯实体。完整实现先用 `instanceof CampaignFleetAPI` 限定对象类型，再读取 `$GearIn_battle_target`。普通行星或带错误标记的其他实体不会进入舰队战场逻辑。

```java
if (!(opponent instanceof CampaignFleetAPI)
        || !opponent.getMemoryWithoutUpdate().getBoolean(BATTLE_TARGET_KEY)) {
    return null;
}
return new PluginPick<>(
        new GearInObjectiveBattleCreationPlugin(),
        PickPriority.MOD_SPECIFIC);
```

测试敌舰队需要在战斗建立前写入独立标记。战斗上下文创建后再修改舰队 Memory，不会追溯替换已经选定的战场插件。

`GearInObjectiveBattleCreationPlugin` 继承原版 `BattleCreationPluginImpl`。它在调用父类前把 `BattleCreationContext.forceObjectivesOnMap` 设为 `true`，父类随后仍负责装入双方舰队、计算地图尺寸、创建地图并设置部署时间。调用顺序不能颠倒，因为父类在创建地图时读取该字段来决定是否生成战术目标。

这个实现只改变目标生成判据。当前 `objectivesAllowed` 允许目标的战斗中，带标记舰队即使规模较小也会生成目标；许可为 `false` 时仍按其条件跳过目标。未标记舰队使当前 Campaign 插件返回 `null`，由其他候选选择正常战场实现。

## 在读取 Memory 时更新市场 facts

Campaign 插件除了选择服务，还能在对象 Memory 被请求时更新事实。实体、人物、势力、全局、玩家和市场各自有对应的 `update...Facts()`。引擎取得对象的 `getMemory()` 时遍历当前 Campaign 插件，并把对象与它的 Memory 传给更新方法。

### Facts 更新入口速查表

| 更新方法 | 触发对象 | 典型用途 |
| --- | --- | --- |
| `updateEntityFacts(entity, memory)` | 生涯实体 | 位置、标签和交互条件 |
| `updatePersonFacts(person, memory)` | 人物 | 身份、关系与角色状态 |
| `updateFactionFacts(faction, memory)` | 势力 | 势力关系与全局规则 |
| `updateGlobalFacts(memory)` | 星区全局 Memory | 全局进度与环境条件 |
| `updatePlayerFacts(memory)` | 玩家角色数据 | 玩家能力与任务条件 |
| `updateMarketFacts(market, memory)` | 市场 | 规模、产业、管理与市场交互条件 |

本节只处理带 `$GearIn_market_facts_target` 的市场。更新方法使用引擎传入的 `memory` 检查输入标记，并写入 `$GearIn_campaign_plugin_ready`。facts 键具有 GearIn 前缀，避免与原版或其他 MOD 的规则变量冲突。

准备片段中的第一次读取不会执行 facts 更新，因此 `readyBeforeRefresh` 仍反映当前已保存值。第二次调用 `getMemory()` 会先运行 `updateMarketFacts()`，`readyAfterRefresh` 随后读取到 `true`。对话框也调用目标市场的 `getMemory()`，所以界面显示的 ready 状态来自本次刷新后的事实。

`memory.set(key, value, 0f)` 的零过期时间用于当前刷新产生的事实。更新方法每次 `getMemory()` 都重新计算它，避免长期过期项堆积。更新方法再次调用 `market.getMemory()` 会递归进入相同链，因此当前只操作传入的 `memory`；当前值和输入条件都从传入的 `MemoryAPI` 读取。

## 注销插件并观察恢复结果

插件 ID 也是注销入口。注销后，当前插件不再参与新的 picker 选择和 facts 刷新；已经创建并显示的对话框、已经进入初始化的战斗不会被追溯替换。

调用 `Global.getSector().unregisterPlugin(GearInCampaignPlugin.PLUGIN_ID)` 即可按稳定 ID 注销。

注销后再次与标记实体交互，其他 Campaign 插件会重新竞争该目标。再次读取市场 Memory 时，本插件不再写入 ready 键；若要观察这一点，应先从 `getMemoryWithoutUpdate()` 中 `unset()` 旧键，再调用 `getMemory()`。

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

生涯插件在具体请求里匹配对象并返回候选，稳定 ID 处理登记与注销，优先级处理非空候选之间的选择。Foundry 的对话、巡逻队的战场与市场事实各由自己的标记接入。

编者建议把目标与入口先对应好，再调整优先级。facts 则直接使用本次传入的 Memory，刷新与当前值读取分别安排，选择规则和动态状态就能各自解释结果。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignPlugin`：插件 ID、存档性质、选择优先级、picker 和 facts 更新合同；
- `com.fs.starfarer.api.campaign.BaseCampaignPlugin`：全部 Campaign 插件方法的默认实现；
- `com.fs.starfarer.api.PluginPick`：候选插件与 `PickPriority` 的组合对象；
- `com.fs.starfarer.api.campaign.SectorAPI`：Campaign 插件注册与注销入口；
- `com.fs.starfarer.api.campaign.InteractionDialogPlugin`：交互对话框的初始化、选项和状态接口；
- `com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl`：原版舰队装载、地图创建和部署规则实现；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：目标标记、动态 facts 和过期时间读写。
