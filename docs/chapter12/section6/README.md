# 实现市场条件

产业、子市场与特殊物品管的是站点怎么造、怎么卖，而在底下托着整座市场环境底数的，则是 `MarketConditionPlugin`。星球气候、矿脉丰度或战区边界，都是作为市场条件挂上市场，统一在后台修正危险度、流通性与本地舰队的装配成色。

这类条件通常用来定义星球气候或星区边界，但在小型工业站上同样管用：往 Nipher Foundry 挂上一条战区前沿状态，用略高的危险度和偏低的流通性换取更高水准的本地舰队装配，工业站作为前线工坊的底色也就从数值上建立起来了。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C1-S7] 的 CSV 表头与双引号转义；
- [C8-S2] 的 `MutableStat` 来源 ID 覆盖与 `unmodify` 规则；
- [C12-S1] 的 `GearInFoundryGenerator`，条件将挂载在 Foundry 市场上。

## 在 market_conditions.csv 登记条件

市场条件的静态配置写在 `data/campaign/market_conditions.csv`，表头共 9 列：

```csv
name,id,tags,planetary,decivRemove,script,desc,icon,order
Warfront,GearIn_warfront,,,,data.scripts.gearin.campaign.GearInWarfrontCondition,"A contested market. Hazard is higher and accessibility lower, but local fleets are better equipped.",graphics/icons/markets/blockade.png,1450
```

- `id` 为 `GearIn_warfront`，`script` 为条件插件完整类名，同样要求保留无参构造方法；
- `planetary` 留空：条件图标会显示在市场界面的右侧 "市场条件" 区域 (设为 `TRUE` 则归入左侧的行星自然地理条件一栏，空间站一般留空)；
- `decivRemove` 留空：如果市场非正常去殖民化，不额外定制移除行为；图标借用原版封锁图标 `blockade.png`。

## 编写条件插件与多实例来源 ID

条件行为类继承 `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`，基类已经存好了当前作用的 `market` 与 `condition` 引用，只需覆写 `apply(String id)` 与 `unapply(String id)`：

```java
@Override
public void apply(String id) {
    String source = getModId();
    market.getHazard().modifyFlat(source, HAZARD_BONUS, getName());
    market.getAccessibilityMod().modifyFlat(source, ACCESSIBILITY_PENALTY, getName());
    market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
            .modifyFlatAlways(source, FLEET_QUALITY_BONUS, getName());
}
```

编写市场条件修饰时，最关键的一点是**不要把来源键硬编码写死成条件规格 ID (如 `"GearIn_warfront"`)**：同一个条件类型有可能带不同参数多次添加，或由同一套模板派生多个实例，写死规格 ID 会导致不同实例在市场上互相踩掉对方的修饰。引擎在调用 `apply(String id)` / `unapply(String id)` 时传入的参数 `id` 与基类方法 `getModId()` 返回的都是当前条件实例自身的唯一标识 (`condition.getIdForPluginModifications()`)，原版条件在两处中选哪一个都可以，但 `apply` 与 `unapply` 必须保持统一。

此外，市场危险度 (`MutableStat`) 与流通性 (`StatBonus`) 直接用 `modifyFlat(...)` / `unmodifyFlat(...)`；而 `Stats.FLEET_QUALITY_MOD` 挂在 `market.getStats().getDynamic()` 上，按原版惯例使用 `modifyFlatAlways(...)` 写入动态修饰。

至于悬停条件图标弹出的说明窗口：`BaseMarketConditionPlugin` 的默认 `createTooltip(...)` 会自动展示 `market_conditions.csv` 里的 `name` 和 `desc`。如果未来希望像原版 `BaseHazardCondition` 那样在文本下方再追加带高亮数字的具体加成明细 (`+15% hazard rating` 等)，可以进一步覆写 `createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded)`；本节为了保持示例精简，先直接沿用 CSV 的正文字段。

## 将条件挂入 Foundry 与持久化语义

在 [C12-S1] 的 `GearInFoundryGenerator.generate(...)` 中，紧跟在 `"population_3"` 之后补入：

```java
market.addCondition("population_3");
market.addCondition("GearIn_warfront");
```

`BaseMarketConditionPlugin` 的 `isTransient()` 默认返回 `true`，意味条件实例自身不直接塞进存档，而是随市场中记录的条件 ID 列表在每次读档时由规格重新建立并自动执行一次 `apply(...)`。对于这种没有运行期独立计时字段的纯配置型条件，沿用默认 `true` 最稳妥。

## 完整的条件插件

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

执行 `gradle jar`，重启游戏，**新建一份存档**，打开 Nipher Foundry 的殖民地面板。

- 在市场条件栏中出现 `Warfront` 的封锁图标，鼠标悬停显示其描述正文；
- 把鼠标移到危险度百分比与流通性百分比数值上查看分解明细：可见 `+15% Warfront` 危险度与 `-5% Warfront` 流通性；
- 保存并重新读档，条件及对应数值修正完整保留；在控制台移除该条件后，三项修正同步消失。

## 常见问题

- **市场界面条件栏里没有看到 `Warfront`**：`GearInFoundryGenerator` 漏掉了 `market.addCondition("GearIn_warfront")`，或 `market_conditions.csv` 9 列错位。
    - **解决方案**：核对 `market_conditions.csv` 列数与 `script` 类名，确认生成器已添加条件。

- **在脚本里调用 `market.removeCondition("GearIn_warfront")` 后，危险度与流通性加成依然残留**：`unapply(...)` 传入的来源 ID 与 `apply(...)` 不一致。
    - **解决方案**：确保写入与清除均使用 `getModId()` (或两处统一使用参数 `id`)。

- **舰队质量修饰没有算进本地舰队生成**：误对 `Stats.FLEET_QUALITY_MOD` 调用了 `modifyFlat(...)` 而非 `modifyFlatAlways(...)`。
    - **解决方案**：`market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)` 应使用 `modifyFlatAlways(...)`。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin`：`apply(...)`、`unapply(...)`、`getModId()` 与 `createTooltipAfterDescription(...)`。
- `com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition`：原版在条件提示框下方追加高亮数值明细的做法。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：`addCondition(...)`、`removeCondition(...)` 与 `getHazard()` / `getAccessibilityMod()`。
- `com.fs.starfarer.api.impl.campaign.ids.Stats`：`FLEET_QUALITY_MOD` 等市场动态统计键名。
- `data/campaign/market_conditions.csv`：原版市场条件的 9 列登记。
