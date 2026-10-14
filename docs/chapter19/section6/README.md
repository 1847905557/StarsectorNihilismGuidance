# 为 Nexerelin 特遣队添加命名与事件响应

Nexerelin 在生成特遣队时请求候选名称，在入侵或市场转移时派发事件。本节为齿轮工业绑定来源市场与序数命名器，再登记事件记录角色。命名器处理每次请求，监听器区分回合、结束与实际易手，各自使用调用方提供的对象。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

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

| 回调方法 | 触发时机 | 记录内容 |
|---|---|---|
| `reportInvadeLoot` | 旧式入侵战利品结算 | 市场 ID |
| `reportInvasionRound` | 旧式入侵回合结束 | 市场 ID、攻守强度 |
| `reportInvasionFinished` | 旧式入侵整体结束 | 市场 ID、胜负结果 |
| `reportMarketTransfered` | 市场所有权转移 | 市场 ID、新所有者 |

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
