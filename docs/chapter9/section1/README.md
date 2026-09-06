# 详解舰船插件

本节把 [C2-S2] 的基础船插继续展开，重点放在 `HullModEffect` 方法实际被调用的时机。船插效果会同时参与生涯数值、战斗舰船和装配界面；方法写在哪里，决定它能影响哪个对象。

*本节预期阅读时间：~28 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 按调用对象选择船插效果回调；
- 区分生涯、战斗和装配界面的行为；
- 配置提示文本与 S-mod 效果；
- 根据运行结果定位回调时机错误。

## 前置知识

- [C2-S2] 的 `hull_mods.csv`、基础数值修改、描述占位符和安装限制；
- [C6-S1] 的 Java 构建与 JAR 输出；
- [C7-S2] 的 `Global` 服务访问阶段；
- [C8-S1] 的战斗引擎状态与暂停语义；
- [C8-S4] 的 `MutableShipStatsAPI`、modifier ID 与 Dynamic。

## 初始化效果对象

船插数据行的 `script` 列指向一个实现 `HullModEffect` 的类。继承 `BaseHullMod` 后，类会得到这组方法的默认空实现；类名只需要覆写当前效果实际用到的方法。

游戏第一次取得该船插的效果对象时调用 `init(HullModSpecAPI spec)`。`BaseHullMod` 会把 `spec` 保存下来，`isSMod(...)`、`isBuiltIn(...)` 和 `hasSModEffect()` 都从这里读取当前船插的静态配置。效果类在一个应用会话内复用，因此计时器、计数器和其他跨舰船状态放进方法局部变量或持久化数据，不写成效果类字段。

## 在数值重建时修改舰船

需要同时影响生涯面板和战斗舰船时，使用 `applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id)`。生涯重建会调用它，战斗舰船创建前也会调用它，所以 `MutableShipStatsAPI` 的普通 `MutableStat` 和 Dynamic 修饰都应在这里写入。

`hullSize` 是当前船体等级，`stats` 是这艘船本次重建使用的数值对象，`id` 是船插的注册 ID。使用 `id` 作为 modifier ID，可以让同一船插在重建时覆盖自己的旧值，并与其他船插的来源区分开。

```java
@Override
public void applyEffectsBeforeShipCreation(HullSize hullSize,
        MutableShipStatsAPI stats, String id) {
    float bonus = ARMOR_BONUS_PERCENT;
    if (isSMod(stats)) {
        bonus += SMOD_ADDITIONAL_PERCENT;
    }
    stats.getArmorBonus().modifyPercent(id, bonus);
    stats.getEffectiveArmorBonus().modifyPercent(id, bonus);
}
```

`isSMod(stats)` 会同时识别普通 S-mod 和增强内置船插。S-mod 分支仍写在同一个数值重建回调里，生涯面板、装配界面和战斗创建使用同一组修饰结果。

## 让战斗中的舰船拥有持续效果

`applyEffectsAfterShipCreation(ShipAPI ship, String id)` 在舰船实体创建后调用，装配界面的预览舰也会经过这个阶段。它适合读取完整的 `ShipAPI`、写入一次性的战斗状态或显示初始化结果；生涯侧不会通过它重建 stats。

```java
@Override
public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    if (ship.getVariant().hasHullMod("unstable_injector")) {
        ship.getVariant().removeMod("unstable_injector");
        LOG.info("GearIn armor training: removed incompatible unstable_injector");
    }
    LOG.info("GearIn armor training: ship created " + ship.getName());
}
```

舰船第一次加入战斗引擎后调用 `applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id)`。这个回调只适合本场战斗的一次性初始化，例如在舰船位置显示一次提示；舰船离开后再次加入不会依靠它重新执行同一初始化。

```java
@Override
public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
    CombatEngineAPI engine = Global.getCombatEngine();
    if (engine != null) {
        engine.addFloatingText(ship.getLocation(),
                "GearIn armor training", 14f, Color.CYAN,
                ship, 0f, 1f);
    }
}
```

需要每帧读取舰船状态时使用 `advanceInCombat(ShipAPI ship, float amount)`。它在战斗暂停时不调用，因此持续效果可以在这里检查舰船是否存活，再修复当前装甲网格。

```java
@Override
public void advanceInCombat(ShipAPI ship, float amount) {
    if (!ship.isAlive() || ship.isHulk()) {
        return;
    }
    repairArmor(ship, amount * (isSMod(ship) ? 8f : 4f));
}
```

航母船插需要把效果传给新生成的战机时实现 `applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id)`。`fighter` 是新战机，`ship` 是母舰；回调只处理这次生成需要的状态，不把战机对象保存到效果类字段。

## 在生涯中处理舰队成员

`advanceInCampaign(FleetMemberAPI member, float amount)` 以舰队成员为单位运行。它适合读取单舰的 `MutableShipStatsAPI`、船体等级和变体，处理生涯中的持续效果。舰队数据重新同步时也会以 `amount == 0f` 调用，因此需要区分正常时间推进和同步重算。

```java
@Override
public void advanceInCampaign(FleetMemberAPI member, float amount) {
    if (amount <= 0f) {
        LOG.info("GearIn fleet maintenance sync: " + member.getShipName());
    }
}
```

如果效果作用于整支舰队，可以让类同时实现 `HullModFleetEffect`。`withAdvanceInCampaign()` 返回 `true` 时，游戏会调用 `advanceInCampaign(CampaignFleetAPI fleet)`；`withOnFleetSync()` 返回 `true` 时，舰队成员变化或船插变化会调用 `onFleetSync(CampaignFleetAPI fleet)`。这组回调会覆盖所有符合接口开关的舰队，所以逐帧方法只适合确有必要的全舰队计算。

```java
@Override
public boolean withAdvanceInCampaign() {
    return false;
}

@Override
public boolean withOnFleetSync() {
    return true;
}

@Override
public void onFleetSync(CampaignFleetAPI fleet) {
    int count = 0;
    for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
        if (member.getVariant().hasHullMod(ID)) {
            count++;
        }
    }
    fleet.getSensorRangeMod().modifyFlat(ID, count * SENSOR_RANGE_BONUS);
}
```

## 在装配界面决定能否安装

`isApplicableToShip(ShipAPI ship)` 只回答当前船能否安装，`getUnapplicableReason(ShipAPI ship)` 提供拒绝文本。两者应使用同一判定条件，否则装配界面会出现能安装却显示拒绝，或拒绝时没有原因。

```java
@Override
public boolean isApplicableToShip(ShipAPI ship) {
    return !ship.getVariant().hasHullMod("unstable_injector");
}

@Override
public String getUnapplicableReason(ShipAPI ship) {
    if (ship.getVariant().hasHullMod("unstable_injector")) {
        return "不兼容 不稳定推进器";
    }
    return null;
}
```

`canBeAddedOrRemovedNow(ShipAPI ship, MarketAPI marketOrNull, CoreUITradeMode mode)` 处理当前地点和交易模式是否允许改装，`getCanNotBeInstalledNowReason(...)` 提供对应原因。`ship` 在自动装配时可能为 `null`，方法需要先处理这个输入。

## 让 tooltip 解释效果

`getDescriptionParam(int index, HullSize hullSize)` 和带 `ShipAPI` 的重载返回 `desc` 中 `%s` 的填充值；`getSModDescriptionParam(...)` 为 `sModDesc` 提供同样的参数。静态船插说明适合使用不带 `ShipAPI` 的重载，需要显示当前舰船状态时再使用带舰船参数的版本。

`shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec)` 决定是否保留 CSV 的 `desc` 段。需要把动态结果、安装条件或当前舰船数据放到描述后面时，实现 `addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec)`。

```java
@Override
public boolean shouldAddDescriptionToTooltip(HullSize hullSize,
        ShipAPI ship, boolean isForModSpec) {
    return true;
}

@Override
public void addPostDescriptionSection(TooltipMakerAPI tooltip,
        HullSize hullSize, ShipAPI ship, float width,
        boolean isForModSpec) {
    tooltip.addPara("S-mod 会把战斗中的装甲修复速度提高到每秒 8 点。", 10f);
}
```

S-mod 是否有独立效果由 `hasSModEffect()` 决定。`sModDesc` 为空或以 `#` 开头时，`BaseHullMod` 不会显示 S-mod 效果段；有文本时，`hasSModEffectSection(...)` 决定当前界面是否显示，`addSModEffectSection(...)` 负责写入效果内容，`addSModSection(...)` 负责标题和说明。`isSModEffectAPenalty()` 返回 `true` 时，界面会按惩罚样式显示该段。

## 补充装配界面的显示方法

`getTooltipWidth()` 返回船插提示宽度；`getBorderColor()` 和 `getNameColor()` 分别控制边框和名称颜色。`getDisplayCategoryIndex()` 选择显示分类，`getDisplaySortOrder()` 控制分类内排序，`showInRefitScreenModPickerFor(ShipAPI ship)` 决定船插是否出现在当前装配选择器中。

需要安装物品时，`getRequiredItem()` 返回物品，`addRequiredItemSection(...)` 把需求写入提示。`affectsOPCosts()` 只声明船插是否会影响装配点成本；方法返回值必须与船插实际设计一致。

## 编写两个船插示例

本节把前面的回调放进两个不同运行环境。装甲训练船插负责装配提示、S-mod 和战斗表现；舰队维护船插负责生涯成员效果与舰队同步。两行数据都放在 `data/hullmods/hull_mods.csv`，`script` 列分别指向对应完整类。

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
装甲训练,GearIn_armor_training,0,,,"defensive, merc, standard",Defenses,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInArmorTrainingHullMod,"提高有效护甲 %s，战斗中每秒修复装甲 %s 点。","提高有效护甲并修复装甲。",S-mod 将有效护甲加成提高至 %s，每秒修复装甲 %s 点。,graphics/hullmods/generic_mod.png
舰队维护,GearIn_fleet_maintenance,0,,,"logistics, merc, standard",Logistics,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInFleetMaintenanceHullMod,"降低舰队成员的燃料消耗 %s。","降低燃料消耗。",,
```

装甲训练直接使用当前 `ShipAPI` 回调，分别展示不兼容船插处理、战斗初始化和装甲修复。舰队维护则把单舰数值写入 `applyEffectsBeforeShipCreation`，把全舰队传感范围写入 `onFleetSync`；两个效果各自使用稳定的 modifier ID。

## 注册与构建

源码放在 `src/data/scripts/gearin/hullmods/`，构建后执行 `gradle jar`。JAR 中应同时包含 `GearInArmorTrainingHullMod.class` 和 `GearInFleetMaintenanceHullMod.class`，否则 CSV 注册值虽然正确，游戏仍然找不到效果类。

## 验证方案

前置数据：已登记两行 `hull_mods.csv`，测试战役使用 [C4-S1] 的 `GearIn_test`，并已将构建结果放进 MOD 的 `jars/`。

1. 启动游戏并在装配界面安装 "装甲训练"，核对基础描述、S-mod 描述和互斥提示。
2. 进入 `GearIn_test` 战斗，观察舰船加入战斗时的浮动文字；让舰船承受装甲损伤，确认 `advanceInCombat` 在运行时逐渐修复装甲，暂停后修复停止。
3. 在生涯中安装 "舰队维护"，核对单舰燃料消耗；移动舰队成员后重新打开舰队信息，确认传感范围按装有该船插的成员数量更新。
4. 移除 "舰队维护" 并再次触发舰队同步，确认传感范围修饰被清除。
5. 开始第二场战斗，确认上一场的舰船状态和浮动文字没有被复用。

预期结果：

- 两个船插都能从装配选择器中找到，日志没有 `Problem loading class`。
- "装甲训练" 的普通安装和 S-mod 都能显示护甲与修复参数，安装互斥时出现拒绝原因；已有不兼容船插时，`applyEffectsAfterShipCreation` 会移除 `unstable_injector`。
- 战斗中的浮动文字只在舰船加入当前引擎后出现，暂停期间不推进装甲修复。
- "舰队维护" 的燃料修饰来自成员 stats，舰队传感范围随同步后的成员集合变化。
- 第二场战斗重新创建效果状态，日志和画面不会沿用第一场战斗的对象。

## 故障分析

- **生涯面板没有数值变化**：修饰写在只接受战斗实体的回调中。

    - **解决方案**：把共用数值移到 `applyEffectsBeforeShipCreation(...)`，再重建舰队成员 stats。

- **战斗效果没有出现**：初始化或每帧逻辑写进了生涯回调。

    - **解决方案**：使用 `applyEffectsAfterShipAddedToCombatEngine(...)` 处理一次性战斗动作，使用 `advanceInCombat(...)` 处理持续动作。

- **S-mod 提示段缺失**：`sModDesc` 为空或以 `#` 开头，或者参数方法返回值不足。

    - **解决方案**：提供有效的 `sModDesc` 和对应的 `getSModDescriptionParam(...)` 返回值，再重新打开 tooltip。

- **装配界面只显示拒绝却没有原因**：`isApplicableToShip(...)` 和 `getUnapplicableReason(...)` 使用了不同条件。

    - **解决方案**：让两个方法共享同一判定，并在允许安装时返回 `null`。

- **舰队传感范围没有更新**：`withOnFleetSync()` 返回 `false`，或修饰只写进了单舰 stats。

    - **解决方案**：返回 `true`，在 `onFleetSync(...)` 中重新遍历成员，并对没有目标成员的情况调用 `unmodify(...)`。

- **下一场战斗出现上一场的对象状态**：效果类字段保存了舰船或战斗计时器。

    - **解决方案**：从当前回调参数重新取得对象，把跨存档数据放入 `SectorAPI.getPersistentData()`。

## 总结

船插效果类不是单一的战斗脚本。`applyEffectsBeforeShipCreation` 负责数值重建，战斗回调负责舰船实体和持续行为，生涯回调负责舰队成员或整支舰队，装配回调负责安装条件和 tooltip。`sModDesc`、参数方法和 S-mod 判断把同一效果的普通安装与故事点内置状态连接到界面。

判断一个方法是否适合当前需求时，先确认它收到的是 `MutableShipStatsAPI`、`ShipAPI`、`FleetMemberAPI`、`CampaignFleetAPI` 还是 tooltip 对象，再检查该对象何时被创建、何时会重新计算。回调对象、状态归属和可观察结果一致时，船插才能在生涯、战斗和装配界面保持同一套设计。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插效果方法定义；
- `com.fs.starfarer.api.combat.BaseHullMod`：默认实现、S-mod 判定与 tooltip 辅助方法；
- `com.fs.starfarer.api.combat.HullModFleetEffect`：整支舰队的生涯回调；
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：生涯与战斗数值入口；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：单舰成员回调参数；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：整支舰队回调参数；
- `data/hullmods/hull_mods.csv`：船插注册行与描述字段。

## 完整最小例

::: details 完整成品：GearInArmorTrainingHullMod

```java
package data.scripts.gearin.hullmods;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInArmorTrainingHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInArmorTrainingHullMod.class);
    private static final float ARMOR_BONUS_PERCENT = 10f;
    private static final float SMOD_ADDITIONAL_PERCENT = 15f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize,
            MutableShipStatsAPI stats, String id) {
        float bonus = ARMOR_BONUS_PERCENT;
        if (isSMod(stats)) {
            bonus += SMOD_ADDITIONAL_PERCENT;
        }
        stats.getArmorBonus().modifyPercent(id, bonus);
        stats.getEffectiveArmorBonus().modifyPercent(id, bonus);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getVariant().hasHullMod("unstable_injector")) {
            ship.getVariant().removeMod("unstable_injector");
            LOG.info("GearIn armor training: removed incompatible unstable_injector");
        }
        LOG.info("GearIn armor training: ship created " + ship.getName());
    }

    @Override
    public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) {
            engine.addFloatingText(ship.getLocation(),
                    "GearIn armor training", 14f, Color.CYAN,
                    ship, 0f, 1f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive() || ship.isHulk()) {
            return;
        }
        repairArmor(ship, amount * (isSMod(ship) ? 8f : 4f));
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter,
            ShipAPI ship, String id) {
        LOG.info("GearIn armor training: fighter spawned by " + ship.getName());
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().hasHullMod("unstable_injector");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getVariant().hasHullMod("unstable_injector")) {
            return "不兼容 不稳定推进器";
        }
        return null;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) ARMOR_BONUS_PERCENT + "%";
        }
        if (index == 1) {
            return "4";
        }
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return (int) (ARMOR_BONUS_PERCENT + SMOD_ADDITIONAL_PERCENT) + "%";
        }
        if (index == 1) {
            return "8";
        }
        return null;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize,
            ShipAPI ship, boolean isForModSpec) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip,
            HullSize hullSize, ShipAPI ship, float width,
            boolean isForModSpec) {
        tooltip.addPara("S-mod 会把战斗中的装甲修复速度提高到每秒 8 点。", 10f);
    }

    private static void repairArmor(ShipAPI ship, float amount) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float[][] values = grid.getGrid();
        float maxCell = grid.getMaxArmorInCell();
        for (int x = 0; x < values.length; x++) {
            for (int y = 0; y < values[x].length; y++) {
                float repaired = Math.min(maxCell, values[x][y] + amount);
                grid.setArmorValue(x, y, repaired);
            }
        }
        ship.syncWithArmorGridState();
    }
}
```

:::

::: details 完整成品：GearInFleetMaintenanceHullMod

```java
package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class GearInFleetMaintenanceHullMod extends BaseHullMod
        implements HullModFleetEffect {

    public static final String ID = "GearIn_fleet_maintenance";
    private static final String SENSOR_MOD_ID = "GearIn_fleet_maintenance_sensor";
    private static final float FUEL_USE_MULT = 0.9f;
    private static final float SENSOR_RANGE_BONUS = 50f;
    private static final Logger LOG = Global.getLogger(GearInFleetMaintenanceHullMod.class);

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize,
            MutableShipStatsAPI stats, String id) {
        stats.getFuelUseMod().modifyMult(id, FUEL_USE_MULT);
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (amount <= 0f) {
            LOG.info("GearIn fleet maintenance sync: " + member.getShipName());
        }
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return false;
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
    }

    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        int count = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(ID)) {
                count++;
            }
        }
        if (count == 0) {
            fleet.getSensorRangeMod().unmodify(SENSOR_MOD_ID);
            return;
        }
        fleet.getSensorRangeMod().modifyFlat(SENSOR_MOD_ID,
                count * SENSOR_RANGE_BONUS);
    }
}
```

:::
