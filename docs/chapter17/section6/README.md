# 为 Nexerelin 特遣队添加命名与事件响应

特遣队名称提供势力风味，入侵监听器则让 GearIn 对市场易手作出反应。本节用配置加载命名器，以来源市场生成唯一候选名，并记录每轮入侵与最终转移。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 让 Nexerelin 通过配置加载 GearIn 特遣队命名器；
- 处理来源市场缺失和名称重复请求；
- 注册一次瞬态监听器，分别记录入侵回合、结束和市场转移；读档后由生涯初始化入口重新注册。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 让特遣队与入侵事件留下可追踪标识

GearIn 特遣队显示 "编号 + 来源市场 + Forgeguard"；入侵 GearIn 市场时日志记录双方强度、结果和新所有者。来源市场缺失时仍能生成可读名称。

## 文件与 API 链路

`specialForcesNamerClass` 指向 `SpecialForcesNamer` 实现；Nexerelin 可能为去重重复请求名称；`InvasionListener` 提供战利品、回合、结束和市场转移四类回调。监听器要通过 `ListenerManager` 注册，并在持久化场景中只注册一次。

### 在势力配置登记命名器

**插入位置：`gear_industry.json` 的 `specialForcesNamerClass`。** 让 Nexerelin 经脚本类加载器创建实现。

```json
"specialForcesNamerClass":"data.scripts.gearin.modcompat.GearInSpecialForcesNamer"
```

完成信号：生成特遣队时进入 `GearInSpecialForcesNamer`，而不是使用默认命名器。

### 从来源市场生成名字主体

**插入位置：`GearInSpecialForcesNamer.getFleetName()`。** 优先使用 `origin`，缺失时寻找势力市场。

```java
String marketName = market == null ? "GearIn" : market.getName();
```

完成信号：正常来源显示市场名；来源为 `null` 时仍返回非空名称。

### 加入可重复请求的编号

**插入位置：`getFleetName()` 返回处。** 使用带序数后缀的 1 至 33 编号，接受 Nexerelin 的重复检查。

```java
int num = RANDOM.nextInt(33) + 1;
return num + ordinalSuffix(num) + " " + marketName + " Forgeguard";
```

完成信号：连续请求产生可读的候选名；重复仍由 Nexerelin 的重试逻辑处理。

### 记录入侵结果和市场转移

**插入位置：`GearInInvasionReporter` 的四个回调和 `register()`。** 保存市场 ID、回合强度、胜负和新所有者，并用瞬态监听器接入场景。

```java
LOG.info("GearIn invasion finished: " + market.getId() + " success=" + success);
```

注册片段：

```java
ListenerManagerAPI listeners = Global.getSector().getListenerManager();
if (!listeners.hasListenerOfClass(GearInInvasionReporter.class)) {
    listeners.addListener(new GearInInvasionReporter(), true);
}
```

完成信号：重复调用注册入口仍只有一个监听器实例；每次入侵结束只出现一条 `finished` 与 `transfer` 日志。

### 入侵回调职责速查表

| 回调方法 | 触发时机 | 记录内容 |
|---|---|---|
| `reportInvadeLoot` | 入侵战利品结算 | 市场 ID |
| `reportInvasionRound` | 每个入侵回合结束 | 市场 ID、攻守强度 |
| `reportInvasionFinished` | 入侵整体结束 | 市场 ID、胜负结果 |
| `reportMarketTransfered` | 市场所有权转移 | 市场 ID、新所有者 |

## 排查关键故障

下面是**局部故障片段**：

```java
public class GearInSpecialForcesNamer {
    public GearInSpecialForcesNamer(String prefix) {}
}
```

实际症状：特遣队照常生成，但使用 Nexerelin 默认名称，日志出现类实例化失败。

原因：配置加载要求公开无参构造，并且类必须实现 `SpecialForcesNamer`；带参数构造器无法由脚本类加载器直接创建。

下面是**局部修正片段**：

```java
public class GearInSpecialForcesNamer implements SpecialForcesNamer {
    public GearInSpecialForcesNamer() {}
}
```

复验：重启游戏后生成一支 GearIn 特遣队，确认名称包含 `Forgeguard`，并在日志中看到监听器只注册一次。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

## 验证方案

1. 构建示例 MOD，确认命名器和监听器都能编译。
2. 在生涯初始化阶段调用 `GearInInvasionReporter.register()`，并在读档后的初始化入口再次调用。
3. 生成 GearIn 特遣队，记录来源市场存在和缺失两种名称。
4. 完成一轮入侵，按回合、结束和市场转移顺序核对日志。

预期结果：

- 生成特遣队时进入 `GearInSpecialForcesNamer`；
- 正常与缺失 origin 都返回非空名称；
- 连续请求能得到不同编号候选；
- 每次入侵结束只出现一条 `finished` 与 `transfer` 日志；
- 重启或重复初始化不会增加瞬态监听器副本；读档后再次注册仍只保留一个实例。

## 故障分析

- **特遣队使用 Nexerelin 默认名称**：配置类无法实例化，通常缺少公开无参构造或没有实现 `SpecialForcesNamer`。修正后重启并生成 GearIn 特遣队，确认名称包含 `Forgeguard`。
- **特遣队名称频繁重复**：编号范围只提供候选，管理器仍可能重复请求。把来源市场和序数同时纳入名称，并保留去重重试；连续生成后核对名称分布。
- **`origin` 为 null 时命名器崩溃**：代码没有市场和固定文本两级备用值。先检查 origin，再查询势力市场，最后回退到 `GearIn`；用无来源舰队复验。
- **一次入侵产生重复日志**：`register()` 从多个入口加入了同类监听器。保留唯一生涯初始化入口或注册前检查现有实例，再触发一次入侵确认每阶段只有一条日志。

## 总结

命名器必须能被配置加载、能处理 `origin` 缺失，也要接受重复请求。事件监听器按事件阶段记录不同事实；每次生涯初始化只幂等注册一个瞬态监听器，读档后重新注册，日志才会与一次入侵对应。

## 建议参考项

- `SpecialForcesNamer`：特遣队命名接口；
- `InvasionListener`：入侵与市场转移回调；
- `specialForcesNamerClass`：势力配置中的命名器入口。
::: details 完整成品：GearInInvasionReporter

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
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

/**
 * 特遣队命名器：实现 Nexerelin 的 SpecialForcesNamer 接口。
 * 注册方式：gear_industry.json 的 specialForcesNamerClass 指向本类；
 * 由 Nexerelin 在生成特遣队时经脚本类加载器 loadClass + newInstance
 * (无参构造) 后调用 getFleetName，至多重试 25 次直到名字不重复。
 *
 * 命名形如 "1st GearIn Prime Forgeguard"：以起源市场命名，编号随机，
 * 降低与既有特遣队撞名概率；origin 缺失时回退势力首个市场，再回退 "GearIn"。
 * 本类只被 Nexerelin 侧加载，齿轮工业自己的代码从不引用它。
 */
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
