# 详解船体插件

看似直观，最反直觉。和几乎所有其它您会在远行星号见过的插件、脚本...之类的东西不同，船体插件是几乎唯一一种全局单例的类。全局单例，也就是所有舰船的插件都共用某些数据。

这就导致，任何一个想往它的类变量里塞点数据的作者很快发现自己陷入莫名其妙的 BUG 之中。

不过，在读过这两句话之后，您现在已经意识到了这个问题。在此之后，您就能试着运用这种从生涯到战斗完全贯穿的强大流程了。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C2-S2] 的稳定化推进器 (`GearInStableInjectorHullMod`)、`hull_mods.csv` 描述占位符与单向安装限制；
- [C8-S2] 的 `MutableShipStatsAPI`、来源 ID 覆盖规则与 `advanceInCombat(...)`；
- [C8-S4] 的 `applyEffectsAfterShipCreation(...)`。

## 全局单实例与回调时机

游戏在加载 `hull_mods.csv` 时，为每个船插的 `script` 类创建一个实例，此后整场游戏会话中所有装有该船插的舰船都复用这同一个对象。**船插类的实例字段因此只能放全局常量，绝不能保存单艘舰船的计时器、层数或标记。** 单舰状态在战斗中存进 `ship.setCustomData(...)` 或每船独立的监听器实例 ([C8-S4]、[C8-S5])，每次回调一律从传入的参数取得当前舰船或舰队。

`HullModEffect` (通常继承 `BaseHullMod`) 的方法按调用时机横跨装配、生涯与战斗：

| 方法 | 调用时机 | 传入的核心对象 | 典型用途 |
| --- | --- | --- | --- |
| `applyEffectsBeforeShipCreation(hullSize, stats, id)` | 每次重建舰船数值：装配刷新、生涯同步、战斗创建舰船前 | `MutableShipStatsAPI` | 静态属性加成、S 插数值加成 |
| `applyEffectsAfterShipCreation(ship, id)` | 舰船实体创建完成之后 (含装配界面预览舰与战场实体) | `ShipAPI` | 挂载战斗监听器、移除互斥船插、调整内置武器 |
| `applyEffectsAfterShipAddedToCombatEngine(ship, id)` | 舰船首次加入当前战斗引擎时 | `ShipAPI` | 仅在进入战场时执行一次的战斗初始化 |
| `advanceInCombat(ship, amount)` | 战斗中每帧调用，暂停时不调 | `ShipAPI` | 随战况实时变化的动态属性或持续效果 |
| `applyEffectsToFighterSpawnedByShip(fighter, ship, id)` | 母舰生成每架战机时 | 战机 `ShipAPI`、母舰 `ShipAPI` | 为航母放出的战机追加属性或脚本 |
| `advanceInCampaign(member, amount)` | 生涯中每帧对装有该船插的舰队成员调用 | `FleetMemberAPI` | 单舰在生涯中的持续后勤逻辑 |

把静态属性修改放在 `applyEffectsBeforeShipCreation(...)` 而不是 `applyEffectsAfterShipCreation(...)`，是因为生涯中的舰队成员 (`FleetMemberAPI`) 并不总是持有 `ShipAPI` 实例。只写在后一个回调里的属性修改，在生涯舰队面板和后勤结算中不会生效。

## 安装限制与双向互斥

[C2-S2] 用 `isApplicableToShip(...)` 与 `getUnapplicableReason(...)` 阻止了在已装有原版不稳定喷射器 (`unstable_injector`) 的舰船上安装稳定化推进器：

```java
@Override
public boolean isApplicableToShip(ShipAPI ship) {
    return ship != null && !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
}

@Override
public String getUnapplicableReason(ShipAPI ship) {
    return "不兼容 不稳定喷射器";
}
```

但这两个方法只管 "能不能装上本船插"。如果玩家先装上稳定化推进器，再去点原版的不稳定喷射器，此时轮到原版脚本的 `isApplicableToShip(...)` 执行判断 —— 原版代码并不认识 MOD 的船插 ID，因此会放行。

在不改动原版脚本的前提下实现双向互斥，靠的是 `applyEffectsAfterShipCreation(...)`。玩家在装配界面每次增删船插，游戏都会重建预览舰并调用已装船插的这个方法。既然在安装前拦不住原版船插，那就在它装上去的同一帧直接解决掉被装上来的船插：

```java
@Override
public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    ShipVariantAPI variant = ship.getVariant();
    if (variant.hasHullMod(UNSTABLE_INJECTOR)
            && !ship.getHullSpec().isBuiltInMod(UNSTABLE_INJECTOR)) {
        variant.removeMod(UNSTABLE_INJECTOR);
    }
}
```

判断时加上 `!ship.getHullSpec().isBuiltInMod(UNSTABLE_INJECTOR)` 是为了排除船体天生内置该船插的情况：内置船插属于船体底子，不应被普通改装拔掉。

## 描述参数、S 插与扩展提示

普通描述与 S 插描述分别写在 `hull_mods.csv` 的 `desc` 列与 `sModDesc` 列，各自使用独立的占位符计数 (都从 `0` 开始)，分别由 `getDescriptionParam(index, hullSize)` 与 `getSModDescriptionParam(index, hullSize)` 填入字符串。

要在数值计算时判断当前船插是否已作为 S 插内置，`BaseHullMod` 提供了 `isSMod(MutableShipStatsAPI stats)` 与 `isSMod(ShipAPI ship)` 两个辅助方法：

```java
if (isSMod(stats)) {
    stats.getAcceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
    stats.getDeceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
}
```

这两个方法同时认故事点内置 (S-mod) 与借由船插/技能获得 S 插效果的内置船插。若 `sModDesc` 列留空或以 `#` 开头，装配界面不会显示 S 插效果段；若希望将 S 插效果标红显示为惩罚项，可以覆写 `isSModEffectAPenalty()` 并返回 `true`。

当 CSV 的一段纯文本不够用、需要在提示框下方追加自定义段落或表格时，覆写 `addPostDescriptionSection(...)`：

```java
@Override
public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize,
        ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addPara("额外说明段落。", 10f);
}
```

`BaseHullMod` 的 `shouldAddDescriptionToTooltip(...)` 默认返回 `true`，即保留 CSV 的 `desc` 正文并在其下方追加 `addPostDescriptionSection(...)` 的内容；只有在准备完全由代码接管整段描述时，才将 `shouldAddDescriptionToTooltip(...)` 改为返回 `false`。

### 其他安装与显示方法

`HullModEffect` 还提供了以下按需覆写的控制入口，不需要改动时沿用 `BaseHullMod` 的默认实现即可：

| 控制项 | 方法与说明 |
| --- | --- |
| 当前地点是否允许拆装 | `canBeAddedOrRemovedNow(ShipAPI ship, MarketAPI marketOrNull, CoreUITradeMode mode)` 控制是否允许在当前界面拆装 (如要求必须在拥有船坞的市场)，`getCanNotBeInstalledNowReason(...)` 返回原因；自动装配时 `ship` 可能为 `null` |
| 安装所需特殊物品 | `getRequiredItem()` 返回安装所需的 `CargoStackAPI` 规格，`addRequiredItemSection(...)` 控制对应提示段 |
| 是否出现在选择器 | `showInRefitScreenModPickerFor(ShipAPI ship)` 控制该船插是否对当前舰船显示在改装候选列表中 |
| 影响武器或战机 OP | `affectsOPCosts()` 返回 `true` 时，声明本船插通过监听器修改了武器或战机的装配点数 ([C8-S4]) |
| 提示框外观与排序 | `getTooltipWidth()`、`getBorderColor()`、`getNameColor()` 调整提示框尺寸与配色；`getDisplayCategoryIndex()` 与 `getDisplaySortOrder()` 控制分类与排序 |

### 废船舰队技能的 D 插重排标签

原版技能 "废船舰队" (`derelict_contingent`) 会通过 `FieldRepairsScript` 重排舰船上的 D 类损伤船插。若自制了带 `dmod` 标签的损伤船插，可以在 `hull_mods.csv` 的 `tags` 列中加入以下标签来控制重排行为：

| 标签 | 作用 |
| --- | --- |
| `DO_can_not_add` | 禁止在重排时将该 D 插随机加到舰船上 |
| `DO_can_not_remove` | 禁止在重排时移除舰船上已有的该 D 插 |

## 舰队级效果：HullModFleetEffect

`MutableShipStatsAPI` 只能改单艘舰船的属性；如果船插要修改**整支舰队**的传感范围、探测距离或最大航速，需要让船插类额外实现 `com.fs.starfarer.api.combat.HullModFleetEffect` 接口 (`BaseHullMod` 只实现了 `HullModEffect`，并未包含 `HullModFleetEffect` 的默认实现，因此这几个接口方法都要在类里写全)：

```java
public interface HullModFleetEffect {
	void advanceInCampaign(CampaignFleetAPI fleet);
	void onFleetSync(CampaignFleetAPI fleet);
	boolean withAdvanceInCampaign();
	boolean withOnFleetSync();
}
```

两个布尔方法相当于开关：

- `withOnFleetSync()` 返回 `true` 时，每当舰队增减舰船、封存或解封舰船、或在装配界面增删船插触发舰队同步时，游戏调用一次 `onFleetSync(CampaignFleetAPI fleet)`；
- `withAdvanceInCampaign()` 返回 `true` 时，游戏在生涯中每帧调用 `advanceInCampaign(CampaignFleetAPI fleet)`。

**一旦打开这两个开关，生涯中的每一支舰队都会调用它们，包括根本没有安装本船插的 AI 舰队。** 因此凡是只随 "装有本船插的舰船数量" 变化的舰队属性加成，一律只开 `withOnFleetSync()`、把 `withAdvanceInCampaign()` 设为 `false`，不要在每帧回调里反复遍历全星域所有舰队的成员列表。

在 `onFleetSync(CampaignFleetAPI fleet)` 中编写舰队加成时，有三处细节不能漏：

1. **排除封存舰船**：通过 `!member.isMothballed()` 过滤已封存 (`mothballed`) 的舰船，否则玩家拖着一排封存空壳也能白拿舰队加成；
2. **设置叠加数量上限**：避免玩家靠塞满廉价护卫舰把舰队属性叠穿；
3. **数量归零时主动撤除修饰**：当最后一艘装有该船插的舰船被拆掉、封存或移出舰队时，`count` 变为 `0`，此时必须调用 `unmodifyFlat(SENSOR_SOURCE_ID)` 撤掉之前写进舰队属性表的修饰，否则旧加成会一直残留在舰队上。

## 补完稳定化推进器：S 插与双向互斥

我们先回到 [C2-S2] 的稳定化推进器 (`GearInStableInjectorHullMod`)，把当时留下的空白补齐：

- 作为 S 插内置时，舰船的加速度与减速度额外提高 25%；
- 在 `applyEffectsAfterShipCreation(...)` 中移除非内置的 `unstable_injector`，补全与不稳定喷射器的双向互斥。

`src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java`：

```java
package data.scripts.gearin.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/** 稳定化推进器：按船体级别提高最高航速；S 插后提高加速度；与不稳定喷射器双向互斥。 */
public class GearInStableInjectorHullMod extends BaseHullMod {

    /** 原版不稳定喷射器的 id，用于互斥检查。 */
    private static final String UNSTABLE_INJECTOR = "unstable_injector";

    /** S 插后加速度与减速度的提高百分比。 */
    private static final float SMOD_ACCELERATION_BONUS = 25f;

    /** 船体级别与航速加成的对应表。 */
    private static final Map SPEED_BONUS = new HashMap();

    static {
        SPEED_BONUS.put(HullSize.FRIGATE, 15f);
        SPEED_BONUS.put(HullSize.DESTROYER, 10f);
        SPEED_BONUS.put(HullSize.CRUISER, 5f);
        SPEED_BONUS.put(HullSize.CAPITAL_SHIP, 5f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BONUS.get(hullSize));
        if (isSMod(stats)) {
            stats.getAcceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
            stats.getDeceleration().modifyPercent(id, SMOD_ACCELERATION_BONUS);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShipVariantAPI variant = ship.getVariant();
        if (variant.hasHullMod(UNSTABLE_INJECTOR)
                && !ship.getHullSpec().isBuiltInMod(UNSTABLE_INJECTOR)) {
            variant.removeMod(UNSTABLE_INJECTOR);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.FRIGATE)).intValue();
        }
        if (index == 1) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.DESTROYER)).intValue();
        }
        if (index == 2) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CRUISER)).intValue();
        }
        if (index == 3) {
            return "" + ((Float) SPEED_BONUS.get(HullSize.CAPITAL_SHIP)).intValue();
        }
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) SMOD_ACCELERATION_BONUS + "%";
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && !ship.getVariant().hasHullMod(UNSTABLE_INJECTOR);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "不兼容 不稳定喷射器";
    }
}
```

## 舰队维护：单舰燃料与全舰队传感范围

第二个船插 "舰队维护" (`GearInFleetMaintenanceHullMod`) 同时展示单舰后勤修改与 `HullModFleetEffect` 舰队级汇总：

- 本舰燃料消耗降低 10% (`applyEffectsBeforeShipCreation` 中乘以 `0.9f`)；
- 舰队中每有一艘未封存且装有本船插的舰船，整支舰队的传感范围提高 50，最多计入 3 艘 (`onFleetSync` 中修改 `fleet.getSensorRangeMod()`)。

`src/data/scripts/gearin/hullmods/GearInFleetMaintenanceHullMod.java`：

```java
package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** 舰队维护：降低本舰燃料消耗，并按安装数量提高舰队传感范围。 */
public class GearInFleetMaintenanceHullMod extends BaseHullMod implements HullModFleetEffect {

    public static final String HULLMOD_ID = "GearIn_fleet_maintenance";

    /** 舰队传感范围修饰的来源 ID。 */
    private static final String SENSOR_SOURCE_ID = "GearIn_fleet_maintenance_sensor";

    /** 本舰燃料消耗倍率。 */
    private static final float FUEL_USE_MULT = 0.9f;

    /** 每艘舰船提供的传感范围。 */
    private static final float SENSOR_BONUS_PER_SHIP = 50f;

    /** 最多计入的舰船数。 */
    private static final int MAX_COUNTED_SHIPS = 3;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFuelUseMod().modifyMult(id, FUEL_USE_MULT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return Math.round((1f - FUEL_USE_MULT) * 100f) + "%";
        }
        if (index == 1) {
            return "" + (int) SENSOR_BONUS_PER_SHIP;
        }
        if (index == 2) {
            return "" + MAX_COUNTED_SHIPS;
        }
        return null;
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return false;
    }

    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
    }

    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        int count = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isMothballed() && member.getVariant().hasHullMod(HULLMOD_ID)) {
                count++;
            }
        }
        count = Math.min(count, MAX_COUNTED_SHIPS);
        if (count <= 0) {
            fleet.getSensorRangeMod().unmodifyFlat(SENSOR_SOURCE_ID);
        } else {
            fleet.getSensorRangeMod().modifyFlat(SENSOR_SOURCE_ID, count * SENSOR_BONUS_PER_SHIP, "舰队维护");
        }
    }
}
```

`fleet.getSensorRangeMod().modifyFlat(...)` 的第三个参数 `"舰队维护"` 是玩家将鼠标悬停在生涯界面左下角传感范围数值上时看到的来源说明。

## 登记船插

更新 `data/hullmods/hull_mods.csv` 中 `GearIn_stable_injector` 的那一行 (补上 `sModDesc` 列)，并追加 `GearIn_fleet_maintenance`：

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc",引擎,2500,TRUE,,,5,10,15,25,data.scripts.gearin.hullmods.GearInStableInjectorHullMod,"根据船体级别，提高 {%s}/{%s}/{%s}/{%s} 的最高航速。不兼容 不稳定喷射器。",提高航速。,加速度与减速度提高 {%s}。,graphics/hullmods/GearIn_stable_injector.png
舰队维护,GearIn_fleet_maintenance,0,,,merc,后勤,3000,TRUE,,,3,6,9,15,data.scripts.gearin.hullmods.GearInFleetMaintenanceHullMod,"燃料消耗降低 {%s}。舰队中每有一艘装有本船插的舰船，舰队传感范围提高 {%s}，最多计入 {%s} 艘。",降低燃料消耗，提高舰队传感范围。,,graphics/hullmods/high_res_sensors.png
```

舰队维护的图标暂时借用原版高精度传感器的 `graphics/hullmods/high_res_sensors.png`。

### 进游戏核对

执行 `gradle jar`，重启游戏，进入生涯存档。

- 在装配界面查看稳定化推进器，提示框下方出现 S 插效果段，显示 "加速度与减速度提高 25%。"；用故事点将其内置后，舰船加速度与减速度提高。
- 先给舰船装上不稳定喷射器，此时稳定化推进器灰显并提示 "不兼容 不稳定喷射器"；反过来先装稳定化推进器、再点击安装不稳定喷射器，不稳定喷射器会在装上的瞬间被自动移除。
- 给舰队中的一艘舰船装上舰队维护，单舰燃料消耗降低 10%；将鼠标移到生涯界面左下角的传感范围数值上，明细中出现 `+50 舰队维护`。
- 给最多 3 艘未封存舰船装上舰队维护，传感范围加成依次升至 `+150`，第 4 艘起不再继续增加；将装有该船插的舰船全部封存或拆下船插后，`+50 舰队维护` 条目消失。

## 常见问题

- **S 插提示段没有显示，或其中的 `{%s}` 显示为 `null`**：`hull_mods.csv` 的 `sModDesc` 列留空，或没有覆写 `getSModDescriptionParam(...)`。
    - **解决方案**：核对 `sModDesc` 列位置，并确认 `getSModDescriptionParam(...)` 按从 `0` 开始的下标返回对应字符串。

- **拆下所有舰队维护船插后，舰队传感范围仍然保留 `+50`**：`onFleetSync(...)` 在计数为 `0` 时直接返回，没有调用 `unmodifyFlat(...)`。
    - **解决方案**：当 `count <= 0` 时调用 `fleet.getSensorRangeMod().unmodifyFlat(SENSOR_SOURCE_ID)` 清除先前写入的修饰。

- **生涯面板上看不到单舰燃料消耗变化**：将属性修改写在了 `applyEffectsAfterShipCreation(...)` 中。
    - **解决方案**：单舰静态属性一律写在 `applyEffectsBeforeShipCreation(...)` 中。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插全部生命周期、安装判断与提示框方法定义。
- `com.fs.starfarer.api.combat.BaseHullMod`：默认实现、`isSMod(...)` 判定与提示框辅助方法。
- `com.fs.starfarer.api.combat.HullModFleetEffect`：`onFleetSync(...)` 与 `advanceInCampaign(...)` 舰队级接口。
- `data.hullmods.UnstableInjector`：原版不稳定喷射器实现 (`data/hullmods/UnstableInjector.java`)。
- `com.fs.starfarer.api.impl.campaign.skills.FieldRepairsScript`：废船舰队技能对 `DO_can_not_add` 与 `DO_can_not_remove` 标签的处理。
- `data/hullmods/hull_mods.csv`：原版船插的 `sModDesc` 列与标签写法。
