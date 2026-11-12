# 登记 Nexerelin 地面战单位与产业

地面战为市场产业另外读取防御与驻军参数。本节把 Element Works 的产业 ID 接到这份定义，并增加玩家可组建的线列步兵。产业插件根据市场中的实际状态计算效果，单位插件则使用人数与基础强度，配置登记和战斗结果由此分别核对。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 在 `groundBattleDefs.json` 登记产业和地面单位；
- 为产业绑定实际插件类，区分通用插件映射；
- 查询真实产业定义、默认回退与单位记录；
- 在地面战中观察配置的防御与部队结果。

## 前置知识

- [C12-S4] 的 `GearIn_element_works` 与实际市场产业；
- [C19-S1] 的 Nexerelin 编译与运行时接入；
- [C19-S2] 的齿轮工业势力；
- [C17-S1] 的 JSON 字段和合并对象。

## 给已有产业补一份地面战定义

创建 `data/config/exerelin/groundBattleDefs.json`，先保存本节完整 JSON。产业使用 [C12-S4] 已定义的 `GearIn_element_works`，以下为 `industries` 对象中的局部条目：

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

**地面战产业键对应实际市场产业 ID。** 防御基础倍率为 1.35，marine 和 heavy 的驻军贡献分别为 0.5 与 0.25。库把各产业、条件的贡献汇总，再结合市场规模、稳定度、物资与驻军修饰计算数量，所以贡献字段采用的是生成系数。

本例使用 `IndustryForBattlePlugin`。建造中的产业贡献为 0，防御倍率回到 1；中断时贡献按中断系数减少，真正中断的场所也回到防御倍率 1。正常完成以后，插件读取本节基础参数，再应用对应修饰。

AI 核心在两项计算中的条件不同：通用插件给防御倍率应用 beta 的 1.1 或 alpha 的 1.25；驻军的核心与改善加成则另检查产业是否带有地面防御标签。Element Works 的生产属性继续由市场产业脚本处理，地面战按照这里的条件取得自己的结果。

`plugin` 放完整类名，库创建产业战斗对象时实例化它。市场生产继续使用 `GearInElementWorksIndustry`，地面战插件处理的是另一份战斗对象。

## 登记玩家可创建的线列步兵

在 `unitTypes` 对象加入：

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

`type` 采用 `MARINE`，`playerCanCreate` 让创建入口提供这支部队。基础强度由单位规模与 `strength` 1.2 共同计算，实际战斗再应用士气等修饰。人员从 `marines` 取得，`mult` 为 1，每份单位规模使用一份人员。

`crewReplacerJobId` 供人员替代规则读取，`tags` 用于分类，`sortOrder` 为 1.5，调整显示顺序。玩家人员不足以建立所选规模时，创建按钮会被禁用。

单位插件字段叫 `pluginClass`，省略时使用 `GroundUnitPlugin`。本例保持库的普通单位插件，数值与人员输入已经足够表达这支步兵。需要特殊单位行为时，再为 `pluginClass` 指定实际实现。

## 合并定义与通用插件各自读取什么

Nexerelin 从各 MOD 合并同路径 JSON。`industries`、`unitTypes`、`conditions`、`abilities` 与 `plugins` 分别保存不同定义；本例只增加产业与单位，库自己的条件、能力和通用插件继续存在。

通用 `plugins` 对象将短 ID 映射到完整类名。以下局部查询只读取该表：

```java
String className = GBDataManager.getPluginsById().get(pluginId);
```

产业的 `plugin` 则直接读取该产业条目的类名。**通用映射与产业绑定分别核对。**复制一个短 ID 到产业字段时，类加载器仍会把它当类名，因而无法实例化。

## 保存探针，确认真实登记

将完整类保存到 `src/data/scripts/gearin/modcompat/GearInGroundBattleProbe.java`。在 Nexerelin 已启用的生涯中，用 Console Commands 运行：

```text
runcode data.scripts.gearin.modcompat.GearInGroundBattleProbe p = new data.scripts.gearin.modcompat.GearInGroundBattleProbe(); com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn ground: " + p.describeIndustry("GearIn_element_works") + " exact=" + p.hasIndustry("GearIn_element_works") + " unit=" + p.hasUnit("GearIn_line_infantry"));
```

日志记录产业定义的 ID、基础倍率、贡献和插件。`hasIndustry` 比较返回对象的 `industryId`，因为未知产业会取得 `default` 定义；单位查询则在缺失时返回 `null`。两个入口的缺失结果不同，探针按各自含义判断登记。

::: warning 重载定义会追加通用插件列表
`loadDefs()` 清空产业与单位等定义，但会继续追加通用插件列表。重复重载后，新战斗可能实例化多份同类插件；本节通过保存并重启比较配置。
:::

## 完整最小例

JSON 保存到 `data/config/exerelin/groundBattleDefs.json`，两个对象分别增加产业和单位：

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

探针读取登记结果：

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

## 验证方案

前置数据：Nexerelin 与 Console Commands 已启用，测试市场的 Element Works 完成建造并正常运作，基础倍率比较时未安装 AI 核心；玩家拥有足够建立所选规模的陆战队员。

1. 保存定义和探针，构建重启，载入测试生涯并执行正文查询。
2. 打开该市场的入侵地面战，查看 Element Works 与驻军；在玩家部队创建入口选择 GearIn line infantry，观察人数、基础强度与显示位置。
3. 将测试 JSON 的产业键暂时改为大小写不同的名称，重启并查询原 ID；记录默认结果，再恢复 `GearIn_element_works`、重启。
4. 比较正常产业与建造或中断状态下的防御和贡献，结合市场与其他修饰判断最终数值。
5. 用原版通用插件短 ID 调用 `findPlugin(...)`，比较 JSON 的通用映射和产业查询的完整类名。

预期结果：

- 原 ID 的 `exact=true`，`industry=GearIn_element_works`，基础倍率 1.35，驻军贡献包含 marine 0.5 与 heavy 0.25；
- `unit=true`，玩家部队入口显示可创建的线列步兵，并采用其人员与强度定义；
- 未登记原 ID 时 `exact=false`，描述返回 default，而单位查询按自身 ID 进行；
- 地面战按产业当前状态和其他修饰计算实际效果，通用插件与产业类名分别来自各自对象。

## 故障分析

- **产业查询一直显示 default**：定义键与实际市场产业 ID 不一致。
    - **解决方案**：统一 `GearIn_element_works`，重启并比较 `industryId`。

- **产业插件实例化失败**：`plugin` 填入了通用短 ID 或无法加载的类名。
    - **解决方案**：采用本节完整 `IndustryForBattlePlugin` 类名，重新启动。

- **线列步兵没有出现在创建入口**：单位 ID、type、可创建开关或人员资源未对应。
    - **解决方案**：核对 `GearIn_line_infantry` 条目与玩家陆战队员，再打开部队创建。

- **实际防御与 1.35 不同**：当前产业状态、AI 核心或其他修饰参与最终计算。
    - **解决方案**：先以正常完成的产业核对基础定义，再观察各修饰。

- **修改 JSON 后仍读到旧数据**：当前定义表尚未重新加载。
    - **解决方案**：保存并重启后查询，再进入测试战斗核对定义。

## 总结

产业定义沿实际产业 ID 提供防御与驻军参数，玩家单位定义提供类型、人员和强度；两个插件分别使用市场状态与单位状态计算结果。探针确认定义身份和基础字段，战斗再显示各修饰参与后的效果，生产与地面战各自保留自己的职责。

## 建议参考项

- `exerelin.campaign.intel.groundbattle.GBDataManager`：定义合并、产业回退和通用插件映射；
- `exerelin.campaign.intel.groundbattle.GroundUnitDef`：单位字段与查询；
- `exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin`：产业战斗状态、强度与驻军贡献；
- `exerelin.campaign.intel.groundbattle.plugins.GroundUnitPlugin`：普通单位行为；
- `data/config/exerelin/groundBattleDefs.json`：本节定义入口。

## 为 Nexerelin 特遣队添加命名与事件响应

Nexerelin 在生成特遣队时请求候选名称，在入侵或市场转移时派发事件。本节为齿轮工业绑定来源市场与序数命名器，再登记事件记录角色。命名器处理每次请求，监听器区分回合、结束与实际易手，各自使用调用方提供的对象。

## 学习目标

完成后，您将能够：

- 让 Nexerelin 通过配置加载自定义特遣队命名器；
- 处理来源市场缺失和名称重复请求；
- 注册一次瞬态监听器，分别记录入侵回合、结束和市场转移；读档后由生涯初始化入口重新注册。

## 前置知识

- [C19-S2] 的势力配置与可选接入；
- [C14-S2] 的生涯监听器登记与存档；
- [C12-S1] 的市场与所有者；
- [C13-S2] 的舰队指挥官。

## 保存命名器并登记类名

将完整 `GearInSpecialForcesNamer` 保存到 `src/data/scripts/gearin/modcompat/`，在既有 `gear_industry.json` 根对象追加：

```json
"specialForcesNamerClass":"data.scripts.gearin.modcompat.GearInSpecialForcesNamer"
```

生成特遣队时，库用这个完整类名实例化 `SpecialForcesNamer`，再调用 `getFleetName(fleet, origin, commander)`。当前舰队、来源市场与指挥官都由这次请求提供，名称只需按自己的规则返回。

## 从来源市场取得名字主体

`GearInSpecialForcesNamer.getFleetName()` 优先使用 `origin`，缺失时寻找势力市场：

```java
String marketName = market == null ? "GearIn" : market.getName();
```

有 `origin` 时使用来源市场名；缺失时取得该舰队势力的市场列表，使用首个市场；列表也为空时采用 `GearIn`。这个备用名称保持示例可识别，选中的市场则由实际集合顺序决定。

## 给重复请求提供编号候选

`getFleetName()` 的返回处使用带序数后缀的 1 至 33 编号，接受 Nexerelin 的重复检查：

```java
int num = RANDOM.nextInt(33) + 1;
return num + ordinalSuffix(num) + " " + marketName + " Forgeguard";
```

编号在 1 到 33 之间，11、12、13 使用 th，其余按末位选择 st、nd、rd 或 th。库将候选与活动特遣队名称比较，非空且未重复时接受，最多请求 25 次；次数耗尽以后仍采用最后返回值。因此，随机序数提供候选，去重重试也有明确上限。

## 登记监听器并记录事件

保存完整 `GearInInvasionReporter` 到同一源码目录。它记录市场 ID、回合强度、胜负和新所有者，局部结束日志为：

```java
LOG.info("GearIn invasion finished: " + market.getId() + " success=" + success);
```

`register()` 再用瞬态监听器把回调接入场景：

```java
ListenerManagerAPI listeners = Global.getSector().getListenerManager();
if (!listeners.hasListenerOfClass(GearInInvasionReporter.class)) {
    listeners.addListener(new GearInInvasionReporter(), true);
}
```

在既有 `GearInModPlugin.onGameLoad(...)` 追加实际调用，并导入桥与监听器：

```java
if (GearInCompatBridge.isNexerelinEnabled()) {
    GearInInvasionReporter.register();
}
```

Nexerelin 派发这组事件时，从 `ListenerManager` 取得 `InvasionListener`，所以记录角色登记到这个管理器。布尔值 `true` 选择瞬态列表，同一生涯重复调用按类去重，读档后由 `onGameLoad(...)` 重新建立实例。

loot、round、finished 用于 `legacyInvasions` 选择的旧式入侵；当前地面战使用自己的战斗事件，实际易手仍会派发 `reportMarketTransfered`。失败的入侵可以结束，而市场所有者保持原状。

编者建议让剧情条件明确指向战斗结局或实际势力归属变化。`reportInvasionFinished` 的名称无法替代它适用的入侵模式；市场转移又可能来自征服或转交。处理转移时，用新旧势力、`playerInvolved` 与 `isCapture` 判断当前事件；行政管理权由市场的玩家拥有状态另外表示。

### 入侵回调职责速查表

| 回调方法                 | 触发时机           | 记录内容          |
| ------------------------ | ------------------ | ----------------- |
| `reportInvadeLoot`       | 旧式入侵战利品结算 | 市场 ID           |
| `reportInvasionRound`    | 旧式入侵回合结束   | 市场 ID、攻守强度 |
| `reportInvasionFinished` | 旧式入侵整体结束   | 市场 ID、胜负结果 |
| `reportMarketTransfered` | 市场所有权转移     | 市场 ID、新所有者 |

## 完整最小例

两个角色分别承担事件记录与名称生成：

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

## 验证方案

前置数据：Nexerelin 与 Console Commands 已启用，准备能发生特遣队生成与市场转移的测试生涯；旧式回合实验使用 `legacyInvasions` 开启的独立测试配置，完成后恢复原设置。

1. 保存两个类、命名字段和生涯登记调用，构建重启，载入测试生涯。
2. 重复读档并调用登记入口，核对当前监听器按类保持一个实例。
3. 生成 GearIn 特遣队，记录来源市场存在和缺失两种名称。
4. 完成旧式入侵，分别观察回合与结束；成功易手时核对市场转移，失败时核对所有者保持。再用当前地面战成功转移市场，观察独立 transfer 日志。

预期结果：

- 生成特遣队时进入 `GearInSpecialForcesNamer`；
- 正常与缺失 origin 都返回非空名称；
- 连续请求得到 1–33 范围的序数候选，重复由库重试；
- 每次旧式结束回调产生一条 finished，实际易手回调产生一条 transferred；当前地面战的市场转移也进入对应回调；
- 重启或重复初始化不会增加瞬态监听器副本；读档后再次注册仍只保留一个实例。

## 故障分析

- **特遣队名称变为 "error"**：配置类无法实例化，通常缺少公开无参构造或没有实现 `SpecialForcesNamer`。
    - **解决方案**：修正配置类，重启并生成 GearIn 特遣队。

- **特遣队名称频繁重复**：编号范围只提供候选，管理器仍可能重复请求。
    - **解决方案**：把来源市场和序数同时纳入名称，并保留去重重试。

- **`origin` 为 null 时命名器崩溃**：代码没有市场和固定文本两级备用值。
    - **解决方案**：先检查 origin，再查询势力市场，最后回退到 `GearIn`。

- **一次入侵产生重复日志**：`register()` 从多个入口加入了同类监听器。
    - **解决方案**：保留唯一生涯初始化入口或注册前检查现有实例。

## 总结

命名器从请求中的来源对象组织候选，库执行有限次数的去重重试。监听器则登记到 Nexerelin 实际派发使用的管理器，按适用模式记录入侵阶段，并独立记录市场转移；名称、战斗结局与所有权变化由此各有自己的入口。

## 建议参考项

- `exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer`：特遣队命名接口；
- `exerelin.utilities.InvasionListener`：入侵与市场转移回调；
- `specialForcesNamerClass`：势力配置中的命名器入口。
