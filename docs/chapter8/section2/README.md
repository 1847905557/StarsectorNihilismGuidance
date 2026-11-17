# 修改舰船数值

舰船的数值并不总是一个简简单单的值。大多数您所关心的数据 —— 特别是那些直觉上就应该 "被设计为可以被 BUFF 类似物影响" 的数据 —— 实际上都是一个有着独特运算规律的 `MutableShipStatsAPI`。

我们似乎已经在之前见过 `MutableShipStatsAPI` 了。但那时候见得不多。之后就会多起来了。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C2-S2] 的船插登记、`getDescriptionParam(...)` 与占位符；
- [C2-S3] 中突击填弹器对射速与幅能消耗的写法；
- [C8-S1] 的 `maintainStatusForPlayerShip(...)`。

## 数值的两种容器

`MutableShipStatsAPI` 上的取值方法返回两类对象，写入方法一样，差别在于基础值由谁保存：

| 类型 | 基础值 | 读取结果 | 典型对象 |
| --- | --- | --- | --- |
| `MutableStat` | 自身保存，`getBaseValue()` 读取 | `getModifiedValue()` | 航速、幅能容量、耗散、射速倍率 |
| `StatBonus` | 不保存，由读取方提供 | `computeEffective(baseValue)` | 武器幅能消耗、射程、伤害加成 |

`StatBonus` 不存基础值，是因为同一项加成要作用在许多不同的对象上。实弹武器幅能消耗的加成只有一份，而每门武器的基础消耗各不相同，游戏计算某门武器时才把这门武器的基础值递给 `computeEffective(...)`。

判断用哪一种，看这个数值属于一艘船还是属于一类武器。一艘船只有一个航速，用 `MutableStat`；一门炮的射程取决于这门炮自己，用 `StatBonus`。

## 三类修饰的叠加规则

两种容器都保存三类修饰，同类先汇总，再按同一个公式算：

```text
结果 = (基础值 + 基础值 × percent 合计 / 100 + flat 合计) × mult 连乘
```

| 方法 | 同类汇总方式 | 作用 |
| --- | --- | --- |
| `modifyFlat(source, value)` | 相加 | 直接加在基础值上 |
| `modifyPercent(source, value)` | 相加 | 按基础值算百分比 |
| `modifyMult(source, value)` | 连乘 | 缩放前面的整体 |

**percent 相加，mult 相乘。** 两个来源各写 `+50%`，合计 `+100%`，结果是基础值的两倍；两个来源各写 `1.5` 倍，结果是 `2.25` 倍。选哪一种，决定了这项效果与其他来源叠加时的强度。原版的射速加成几乎都用 `mult`，[C2-S3] 与本节都沿用 `mult`，比例才和原版一致。

**同一来源 ID、同一类型的修饰只留一份。** 用同一个 ID 再写一次是覆盖，不是新增。这是动态修改能成立的前提：每帧以同一个 ID 写入新值，数值跟着变，不会越叠越多。反过来，ID 每次都不同，例如拼上时间或随机数，修饰表每帧新增一条，几秒后数值就失控了。

`unmodify(source)` 按来源 ID 撤掉自己写过的全部修饰，别人的记录不动。

## 改的时机：创建时与战斗中

`HullModEffect` 上能改数值的地方有两处，选哪个取决于这项修改该在什么时候生效：

| 回调 | 何时调用 | 适合什么 |
| --- | --- | --- |
| `applyEffectsBeforeShipCreation(...)` | 每次重建舰船数值时：装配界面刷新、战斗开始 | 装配时就该确定的效果 |
| `advanceInCombat(ship, amount)` | 战斗中每帧，暂停时不调 | 随战况变化的效果 |

**放在 `applyEffectsBeforeShipCreation(...)` 里的修改会反映到装配界面上。** 玩家在出战前就能看到代价，这是把它和战斗内修改分开的实际理由，不只是机制差异。

`applyEffectsAfterShipCreation(...)` 是第三个位置，它在舰船对象创建之后调用，用来挂监听器，见 [C8-S4]。

## 动态修改

`advanceInCombat(ship, amount)` 在战斗中每帧调用，暂停时不调。要在这一帧算出一个新值并盖掉上一帧的，就是这里：

```java
@Override
public void advanceInCombat(ShipAPI ship, float amount) {
    if (!ship.isAlive()) {
        return;
    }
    float bonus = MAX_ROF_BONUS * ship.getFluxLevel();
    ship.getMutableStats().getBallisticRoFMult().modifyMult(SOURCE_ID, 1f + bonus / 100f);
}
```

`getFluxLevel()` 返回 `0` 到 `1` 的幅能比例。每帧以同一个来源 ID 覆盖，数值跟着幅能连续变化。

**动态修改不需要在结束时撤。** 舰船的 `MutableShipStatsAPI` 属于这场战斗里的这艘船，战斗结束随舰船一起丢弃，下一场重新创建。战术系统要写 `unapply(...)`，是因为它在一场战斗里会反复开关；船插从战斗开始到结束一直在，没有 "关闭" 那一刻。

需要在一场战斗中途停止的修改，用 `unmodify(SOURCE_ID)`，什么时候该撤取决于这项效果什么时候结束。判断标准只有一句：**这个修改有没有比舰船本身更早结束的时机。**

## 把当前值显示给玩家

动态值在战斗里不断变，装配界面看不到。玩家座舰上可以维持一行状态：

```java
if (ship == Global.getCombatEngine().getPlayerShip()) {
    String icon = Global.getSettings().getHullModSpec(HULLMOD_ID).getSpriteName();
    Global.getCombatEngine().maintainStatusForPlayerShip(STATUS_KEY, icon, "Overheat forge",
            "ballistic rate of fire +" + (int) bonus + "%", false);
}
```

图标取自船插自己的贴图，`getHullModSpec(...)` 读的是 `hull_mods.csv` 里登记的 `sprite`。状态项的规则与 [C8-S1] 相同：每帧调用才持续显示。

## 过热冶炉：在同一个船插里组合静态与动态修饰

有了静态构建期修饰和战斗内每帧覆盖这两套手段，我们来为齿轮工业写一个叫 "过热冶炉" 的船插，把舰船越接近过载火力越猛的风格体现在数值上：

- 实弹武器的幅能消耗固定提高 10%；
- 实弹武器的射速随当前幅能水平线性提高，满幅能时最高提高 30%。

前一项代价在出战前就是固定的，写进 `applyEffectsBeforeShipCreation(...)` 让玩家在装配界面直接看到；后一项收益随战况实时浮动，写进 `advanceInCombat(...)` 逐帧覆盖。玩家想要吃满射速加成，就得顶着高幅能持续开火，而 [C8-S1] 刚做好的幅能警告正好会在危险线附近提醒什么时候该收手。

## 完整船插

`src/data/scripts/gearin/hullmods/GearInOverheatForgeHullMod.java`：

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/** 过热冶炉：实弹武器幅能消耗固定提高，射速随幅能升高。 */
public class GearInOverheatForgeHullMod extends BaseHullMod {

    public static final String HULLMOD_ID = "GearIn_overheat_forge";
    private static final String SOURCE_ID = "GearIn_overheat_forge_rof";
    private static final String STATUS_KEY = "GearIn_overheat_forge_status";

    private static final float FLUX_COST_PENALTY = 10f;
    private static final float MAX_ROF_BONUS = 30f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponFluxCostMod().modifyPercent(id, FLUX_COST_PENALTY);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive()) {
            return;
        }
        float bonus = MAX_ROF_BONUS * ship.getFluxLevel();
        ship.getMutableStats().getBallisticRoFMult().modifyMult(SOURCE_ID, 1f + bonus / 100f);

        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == engine.getPlayerShip()) {
            String icon = Global.getSettings().getHullModSpec(HULLMOD_ID).getSpriteName();
            engine.maintainStatusForPlayerShip(STATUS_KEY, icon, "Overheat forge",
                    "ballistic rate of fire +" + (int) bonus + "%", false);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) FLUX_COST_PENALTY + "%";
        }
        if (index == 1) {
            return (int) MAX_ROF_BONUS + "%";
        }
        return null;
    }
}
```

`HULLMOD_ID` 声明为 `public`，其他类需要判断舰船是否装有本船插时可以直接引用它。

本节的 `GearInOverheatForgeHullMod` 先写到这里；到了 [C8-S5]，我们还会往它的 `advanceInCombat(...)` 里补入挂载舰底热量光环的逻辑。

## 登记船插

在 `data/hullmods/hull_mods.csv` 中追加一行：

```csv
过热冶炉,GearIn_overheat_forge,0,,,"offensive, merc",武器,3000,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInOverheatForgeHullMod,"实弹武器的幅能消耗提高 {%s}。实弹武器的射速随幅能水平提高，满幅能时最多提高 {%s}。",实弹武器射速随幅能提高。,,graphics/hullmods/safety_overrides.png
```

图标直接引用原版安全协议超驰的贴图 `graphics/hullmods/safety_overrides.png`。游戏读取 `sprite` 路径时，MOD 中找不到的文件会回到原版目录查找，所以引用原版贴图不需要复制文件。代价是装配界面中两个船插的图标相同，正式发布前应当换成自己的图标。

再把它装到工匠的装配上。`data/variants/GearIn_artizan_combat.variant` 的 `hullMods` 中加入一项：

```json
    "hullMods": [
        "missleracks",
        "heavyarmor",
        "fluxbreakers",
        "autorepair",
        "GearIn_overheat_forge"
    ],
```

工匠是驱逐舰，过热冶炉占用 10 点装配点数。两门都林是内置武器，不占装配点数，加上过热冶炉之后共用 91 点，仍在工匠的 95 点之内。

### 进游戏核对

执行 `gradle jar`，重启游戏。

- 在舰队界面查看工匠，装配中出现过热冶炉，描述中两个数字高亮显示为 `10%` 与 `30%`。
- 在装配界面中把鼠标移到实弹武器上，幅能消耗比拆下船插时高出 10%。
- 进入测试战役，操控工匠。左下角状态列表出现 `Overheat forge` 一项。开火让幅能上升，加成从 `+0%` 逐渐增大，满幅能时接近 `+30%`，排散后回落。

## 常见问题

- **射速越来越快，远超 30%**：每帧使用了不同的来源 ID，或用 `getModifiedValue()` 读出结果再写回基础值。
    - **解决方案**：每帧使用同一个固定的来源 ID 写入修饰，不修改基础值。

- **装配界面中幅能消耗没有变化，战斗中却有效果**：静态修改写在了 `advanceInCombat(...)` 中。
    - **解决方案**：装配时就应确定的效果放进 `applyEffectsBeforeShipCreation(...)`。

- **状态项图标显示为白色方块**：`hull_mods.csv` 中 `sprite` 的路径与实际文件不符。
    - **解决方案**：核对 `sprite` 列，确认文件位于 MOD 或原版的对应路径。

## 建议参考项

- `com.fs.starfarer.api.combat.MutableStat`：基础值、三类修饰与 `getModifiedValue()`。
- `com.fs.starfarer.api.combat.StatBonus`：`computeEffective(baseValue)` 的计算公式。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：舰船全部可修改数值的入口。
- `com.fs.starfarer.api.combat.HullModEffect`：`applyEffectsBeforeShipCreation(...)` 与 `advanceInCombat(...)` 的调用时机。
- `com.fs.starfarer.api.impl.hullmods.EscortPackage`：原版护航套件，在 `advanceInCombat(...)` 中按战场状况动态写入加成。
