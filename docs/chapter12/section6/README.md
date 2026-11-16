# 实现市场条件

Foundry 的周围在打仗，市场条件里写作 Warfront，交战地带。

它让这座站点更危险、更难进出，作为交换，本地舰队会更像样一些。这对玩家算不上什么好消息，但一座产垄断物资的站点，周围要是风平浪静，反而显得奇怪。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C12-S1] 的 Foundry 市场与 `addCondition(...)`，条件在那里加入；
- [C8-S2] 的 `MutableStat` 与修饰来源 ID；
- [C1-S7] 的 CSV 表头与转义。

## 注册条件

条件的身份登记在 `data/campaign/market_conditions.csv`，表头沿用原版的九列：

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
Warfront,GearIn_warfront,,,,data.scripts.gearin.campaign.GearInWarfrontCondition,"A contested market. Hazard is higher and accessibility lower, but local fleets are better equipped.",graphics/icons/markets/blockade.png,1450
```

`id` 是 `GearIn_warfront`，`script` 是插件的完整类名，必须有无参构造。图标借用原版封锁图标。`planetary` 留空，条件显示在市场条件一栏；设为 `TRUE` 则进入行星条件一栏。`desc` 是悬停时的正文。

**`script` 的类名写错，游戏不会在启动时报错**，症状出现在市场添加条件的那一刻。

## 写效果

插件继承 `BaseMarketConditionPlugin`，在 `apply(...)` 里写入修饰，在 `unapply(...)` 里按同一来源解除：

```java
String source = getModId();
market.getHazard().modifyFlat(source, HAZARD_BONUS, getName());
market.getAccessibilityMod().modifyFlat(source, ACCESSIBILITY_PENALTY, getName());
market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
        .modifyFlatAlways(source, FLEET_QUALITY_BONUS, getName());
```

危险度 +0.15，流通性 -0.05，舰队质量 +0.15。市场更危险、更难进出，换来的是本地舰队的装备更好。玩家为它付出的是通行与安全，得到的是更像样的对手。

**来源 ID 用 `getModId()`，不用传入的参数，也不用规格 ID。** 它返回本实例的标识，同一类条件有多个实例时，各自的修饰互不覆盖。`unapply(...)` 必须用同一个来源，否则条件移除之后，修饰仍留在市场上。

`modifyFlatAlways(...)` 用于舰队质量这类动态项，修饰不受当前值上限影响。其余两项用普通的 `modifyFlat(...)`。

## 加入 Foundry

在 [C12-S1] 的生成器中，紧跟人口条件加入这一行：

```java
market.addCondition("GearIn_warfront");
```

条件没有参数，也没有需要延续的实例状态，插件不必覆写 `isTransient()`：读档时按规格重建即可。

## 完整的条件类

`src/data/scripts/gearin/campaign/GearInWarfrontCondition.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/** Warfront：交战地带的市场条件，危险度上升、流通性下降，但舰队质量提高。 */
public class GearInWarfrontCondition extends BaseMarketConditionPlugin {

    public static final String CONDITION_ID = "GearIn_warfront";

    private static final float HAZARD_BONUS = 0.15f;
    private static final float ACCESSIBILITY_PENALTY = -0.05f;
    private static final float FLEET_QUALITY_BONUS = 0.15f;

    @Override
    public void apply(String id) {
        String source = getModId();
        market.getHazard().modifyFlat(source, HAZARD_BONUS, getName());
        market.getAccessibilityMod().modifyFlat(source, ACCESSIBILITY_PENALTY, getName());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(source, FLEET_QUALITY_BONUS, getName());
    }

    @Override
    public void unapply(String id) {
        String source = getModId();
        market.getHazard().unmodifyFlat(source);
        market.getAccessibilityMod().unmodifyFlat(source);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(source);
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，打开 Nipher Foundry 的市场界面。

- 条件栏中出现 Warfront，图标为封锁图标，悬停显示描述；
- 危险度比不带该条件时高 15 个百分点，流通性低 5 个百分点；
- 保存并重新读档，条件仍在，数值不变。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **条件栏里没有 Warfront**：`market_conditions.csv` 没有加载，或 `script` 的类名写错。
    - **解决方案**：核对 ID 与完整类名，并确认生成器已调用 `addCondition(...)`。

- **移除条件之后修饰仍在**：`unapply(...)` 解除的来源与 `apply(...)` 写入的不一致。
    - **解决方案**：两处都用 `getModId()`。

- **同类条件的效果互相覆盖**：来源写成了规格 ID，多个实例共用同一个来源。
    - **解决方案**：用 `getModId()`，每个实例各有自己的来源。

- **舰队质量没有变化**：写成了 `modifyFlat(...)`，动态项没有按预期生效。
    - **解决方案**：动态项用 `modifyFlatAlways(...)`。

- **旧存档中没有这个条件**：Foundry 只在新档中生成。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`：`apply(...)`、`unapply(...)` 与 `getModId()`。
- `com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition`：原版按规格写入危险度的范式。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：`addCondition(...)`、抑制与重算的方法。
- `com.fs.starfarer.api.impl.campaign.ids.Stats`：舰队质量等动态项的键。
- `data/campaign/market_conditions.csv`：原版条件的登记。
