# 登记 Nexerelin 地面战与特遣队事件

接入 Nexerelin 后，原版产业脚本只管平时生产，一到地面战攻防、特遣队生成或市场易手，控制权就全切进了 Nexerelin 各自独立的配置与监听接口里。

这部分不光涉及静态表的增补，更麻烦的是特遣队还存在多种生成与运行模式 —— 若不在实机里亲眼对照一遍回调表现，只凭表面文档写完监听器，等到入侵战打响时您才会发现，不同模式下触发的根本不是同一条回调线。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~35 分钟。_

## 前置知识

- [C12-S1] 的市场与所有者；
- [C12-S4] 的 `GearIn_element_works` 市场产业；
- [C13-S2] 的舰队指挥官；
- [C14-S2] 的生涯监听器登记与存档；
- [C17-S1] 读取 MOD 内 JSON 文件的合并机制；
- [C19-S1] 的可选依赖检测；
- [C19-S2] 的 `gear_industry` 势力配置。

## 为产业和玩家部队登记地面战定义

新建 `data/config/exerelin/groundBattleDefs.json`。Nexerelin 的 `GBDataManager.loadDefs()` 会按 MOD 合并同路径 JSON 里的 `industries`、`unitTypes`、`conditions`、`abilities` 与 `plugins`，只填要新增的 `industries` 和 `unitTypes` 即可，原库自带内容会继续保留。

先在 `industries` 下用 [C12-S4] 的实际产业 ID `GearIn_element_works` 登记一条战斗定义：

```json
"GearIn_element_works": {
    "strengthMult": 1.35,
    "troopCounts": {
        "marine": 0.5,
        "heavy": 0.25
    },
    "plugin": "exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin"
}
```

这里由市场生产脚本继续走 `GearInElementWorksIndustry`，而地面战另起一套 `IndustryForBattlePlugin`：正常运转时基础防御倍率为 `1.35`、`marine`/`heavy` 驻军系数为 `0.5`/`0.25`；一旦处在建造中 (building)，驻军贡献清零且防御倍率回到 `1`；若产业被中断 (disrupted)，驻军贡献按中断倍率衰减、实际中断达到阈值时防御倍率同样回退到 `1`。AI 核心的规则也分两条线走：防御倍率直接给 `beta_core` 乘 `1.1`、`alpha_core` 乘 `1.25`，而驻军里的核心与改善加成则另行要求产业 spec 带 `grounddefenses` 标签。

再在 `unitTypes` 加入玩家可组建的线列步兵：

```json
"GearIn_line_infantry": {
    "name": "GearIn line infantry",
    "type": "MARINE",
    "playerCanCreate": true,
    "strength": 1.2,
    "personnel": {
        "commodityId": "marines",
        "crewReplacerJobId": "nex_groundBattle_marines",
        "mult": 1
    },
    "tags": ["marine"],
    "sortOrder": 1.5
}
```

注意一个关键字段差异：**产业绑定插件的键叫 `plugin`，且必须写完整类名**(把 `plugins` 表里的短 ID 直接抄进产业 `plugin` 会实例化失败)；**单位指定定制实现时的键则叫 `pluginClass`**，省略时默认实例化 `GroundUnitPlugin`。玩家陆战队员不足以凑齐所选编制时，创建按钮会自动禁用。

## 用探针区分默认回退与通用插件表

将 `GearInGroundBattleProbe` 保存到 `src/data/scripts/gearin/modcompat/`。检查产业是否登记成功时，千万不要只判 `getIndustryDef(id) != null` —— `GBDataManager.getIndustryDef(id)` 在查不到键时会**回退返回 `default` 条目**，因此必须核对返回对象的 `industryId` 是否真的等于目标 ID；而 `GroundUnitDef.getUnitDef(id)` 缺项时会直接返回 `null`。另需留意：`GBDataManager.loadDefs()` 重载时会清空产业与单位映射，却**不会清空通用插件列表 `plugins`**，在运行期重复重载可能导致新地面战多实例化同类插件，改完配置直接重启最稳妥。

在启用了 Nexerelin 的测试生涯跑控制台命令核对登记：

```text
runcode data.scripts.gearin.modcompat.GearInGroundBattleProbe p = new data.scripts.gearin.modcompat.GearInGroundBattleProbe(); com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn ground: " + p.describeIndustry("GearIn_element_works") + " exact=" + p.hasIndustry("GearIn_element_works") + " unit=" + p.hasUnit("GearIn_line_infantry"));
```

## 绑定特遣队命名器与去重策略

把 `GearInSpecialForcesNamer` 保存到同一源码包，并在既有 `gear_industry.json` 根对象补上一行：

```json
"specialForcesNamerClass": "data.scripts.gearin.modcompat.GearInSpecialForcesNamer"
```

Nexerelin 的 `SpecialForcesIntel` 在选船名时加载这个类名，要求它实现 `SpecialForcesNamer` 且具备公开无参构造 (若加载失败或构造抛异常，整只特遣队名会直接回退成 `"error"`)。进入 `getFleetName(fleet, origin, commander)` 后，按 `origin` → `Misc.getFactionMarkets(fleet.getFaction())` 首个市场 → 固定文本 `"GearIn"` 依次取名称主体，并附上 `1`–`33` 带序数后缀的随机编号 (注意 `11`–`13` 用 `"th"`)。管理器拿到候选串后会与活动特遣队判重，最多连续请求 **25 次**；耗尽 25 次仍重名就直接采用最后一次返回值。

## 用瞬态监听器记录入侵与市场易手

将 `GearInInvasionReporter` 保存到同一源码包，它实现 `exerelin.utilities.InvasionListener`，并借助 `ListenerManager` 的瞬态开关 `true` 只在当次会话内存挂钩，由 `GearInModPlugin.onGameLoad(...)` 在读档后按类判重并补挂 (保留已有初始化)：

```java
if (GearInCompatBridge.isNexerelinEnabled()) {
    GearInInvasionReporter.register();
}
```

写剧情或事件触发器时，务必区分各回调的适用范围：

| 回调方法                 | 触发时机               | 适用前提与要点                      |
| ------------------------ | ---------------------- | ----------------------------------- |
| `reportInvadeLoot`       | 旧式入侵战利品结算     | 仅 `legacyInvasions` 模式触发       |
| `reportInvasionRound`    | 旧式入侵单回合结束     | 仅 `legacyInvasions` 模式触发       |
| `reportInvasionFinished` | 旧式入侵整体结束       | 仅 `legacyInvasions` 触发，含胜负   |
| `reportMarketTransfered` | 市场所有权转移 (注意单 r)| 无论旧式入侵、地面战或转交均触发    |

现代地面战使用的是自己的战斗阶段事件，不会回调上面三条旧式 `Invade/Invasion` 方法；但只要市场真正易手，最终都会走进 `reportMarketTransfered(...)`。判断具体成因时应结合新旧势力、`playerInvolved`、`isCapture` 以及市场当前的玩家行政控制状态。

## 完整最小例

地面战配置保存到 `data/config/exerelin/groundBattleDefs.json`(同时在 `gear_industry.json` 补充前文提到的 `"specialForcesNamerClass"`)：

```json
{
    "industries": {
        "GearIn_element_works": {
            "strengthMult": 1.35,
            "troopCounts": {
                "marine": 0.5,
                "heavy": 0.25
            },
            "plugin": "exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin"
        }
    },
    "unitTypes": {
        "GearIn_line_infantry": {
            "name": "GearIn line infantry",
            "type": "MARINE",
            "playerCanCreate": true,
            "strength": 1.2,
            "personnel": {
                "commodityId": "marines",
                "crewReplacerJobId": "nex_groundBattle_marines",
                "mult": 1
            },
            "tags": ["marine"],
            "sortOrder": 1.5
        }
    }
}
```

三个 Java 角色分别负责地面战定义查询、入侵事件监听与特遣队命名：

::: details 完整成品：GearInGroundBattleProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GroundUnitDef;

/** 查询地面战产业、单位与通用插件的实际登记。 */
public final class GearInGroundBattleProbe {
    public boolean hasIndustry(String industryId) {
        return industryId.equals(GBDataManager.getIndustryDef(industryId).industryId);
    }

    public String describeIndustry(String industryId) {
        GBDataManager.IndustryDef def = GBDataManager.getIndustryDef(industryId);
        return "industry=" + def.industryId + " strengthMult=" + def.strengthMult
                + " troops=" + def.troopCounts + " plugin=" + def.plugin;
    }

    public String findPlugin(String pluginId) {
        return GBDataManager.getPluginsById().get(pluginId);
    }

    public boolean hasUnit(String unitId) {
        return GroundUnitDef.getUnitDef(unitId) != null;
    }
}
```

:::

::: details 完整成品：GearInInvasionReporter

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.TempDataInvasion;

import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.utilities.InvasionListener;

public final class GearInInvasionReporter implements InvasionListener {
    private static final Logger LOG = Global.getLogger(GearInInvasionReporter.class);

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(GearInInvasionReporter.class)) {
            listeners.addListener(new GearInInvasionReporter(), true);
        }
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market,
            TempDataInvasion data, CargoAPI loot) {
        LOG.info("GearIn invasion loot: " + market.getId());
    }

    @Override
    public void reportInvasionRound(InvasionRoundResult result, CampaignFleetAPI fleet,
            MarketAPI market, float attackerStr, float defenderStr) {
        LOG.info("GearIn invasion round: " + market.getId() + " attacker=" + attackerStr
                + " defender=" + defenderStr);
    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attacker,
            MarketAPI market, float numRounds, boolean success) {
        LOG.info("GearIn invasion finished: " + market.getId() + " success=" + success);
    }

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner,
            FactionAPI oldOwner, boolean playerInvolved, boolean isCapture,
            List<String> factionsToNotify, float repChangeStrength) {
        LOG.info("GearIn market transferred: " + market.getId() + " owner=" + newOwner.getId());
    }
}
```

:::

::: details 完整成品：GearInSpecialForcesNamer

```java
package data.scripts.gearin.modcompat;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;

/** 根据来源市场与随机序数生成特遣队候选名称。 */
public class GearInSpecialForcesNamer implements SpecialForcesNamer {

    private static final Random RANDOM = new Random();

    @Override
    public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
        MarketAPI market = origin;
        if (market == null) {
            List<MarketAPI> markets = Misc.getFactionMarkets(fleet.getFaction());
            market = markets.isEmpty() ? null : markets.get(0);
        }
        String marketName = market == null ? "GearIn" : market.getName();

        int num = RANDOM.nextInt(33) + 1;
        return num + ordinalSuffix(num) + " " + marketName + " Forgeguard";
    }

    private String ordinalSuffix(int num) {
        if (num >= 11 && num <= 13) {
            return "th";
        }
        switch (num % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }
}
```

:::

### 进游戏核对

1. **核对地面战定义与回退行为**：重启载入包含运作中的 Element Works 市场的存档，先跑正文探针命令，确认 `industry=GearIn_element_works strengthMult=1.35 ... exact=true` 与 `unit=true`；随后进入该市场地面战界面，确认 Element Works 加成与部队创建列表中的 `GearIn line infantry`(陆战队员充足时可创建)。若想对比未命中行为，可把 JSON 键名临时改成错误拼写重启再查，`hasIndustry` 应转为 `false` 且 `describeIndustry` 返回 `default`。
2. **核对特遣队命名器**：在存档中生成齿轮工业特遣队，记录 `origin` 存在与 `origin == null` 两种情况下的名称输出 (均为 `1st`–`33rd ... Forgeguard`，无报错回退 `"error"`)。
3. **核对入侵监听器与重复读档**：分别在现代地面战 (易手时应打 `GearIn market transferred`) 与开启 `legacyInvasions` 的独立测试配置下观察 `round` / `finished` / `transferred` 日志，并在重复读档后再触发一次转移，确认只输出一组日志而非多倍打印。

## 常见问题

- **`GearIn_element_works` 一直查出 `default` 或创建界面看不见新步兵**：JSON 键名和实际市场产业 ID 不一致，或 `playerCanCreate`、`type`、陆战队员余量未对上。
    - **解决方案**：核对 `GearIn_element_works` 与 `GearIn_line_infantry` 的字段及玩家陆战队员数量，保存后重启再测。

- **产业战斗插件实例化失败**：把 `plugins` 短 ID 填进了产业条目的 `plugin` 字段。
    - **解决方案**：产业 `plugin` 必须填写完整类路径 (如 `IndustryForBattlePlugin`)，单位定制才使用 `pluginClass`。

- **特遣队名字直接显示成 `"error"`**：`specialForcesNamerClass` 拼写错误、没有实现 `SpecialForcesNamer` 或缺少公开无参构造函数。
    - **解决方案**：检查配置类名、接口实现与构造方法后重启生成特遣队验证。

- **打一场仗输出好几份一模一样的日志**：`register()` 被多个入口反复触发时没走 `hasListenerOfClass`，或者开发期热重载导致旧插件残留。
    - **解决方案**：保留唯一登记入口并在添加前检查 `hasListenerOfClass`，修改配置后采用重启核对。

## 建议参考项

- `exerelin.campaign.intel.groundbattle.GBDataManager`：合并定义、回退规则与通用插件表；
- `exerelin.campaign.intel.groundbattle.GroundUnitDef`：单位字段与查询；
- `exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin`：产业战斗状态、强度与驻军贡献；
- `exerelin.campaign.intel.groundbattle.plugins.GroundUnitPlugin`：普通单位默认行为；
- `exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer`：特遣队命名接口；
- `exerelin.utilities.InvasionListener`：旧式入侵与市场转移回调；
- `data/config/exerelin/groundBattleDefs.json`：地面战配置入口。
