# 详解舰船插件

船插已经能登记、安装和修改数值，接下来把效果放到它真正需要的运行场合。本节制作装甲训练与舰队维护两个船插：一个处理装配提示、S-mod 和战斗修复，另一个处理燃料与整支舰队的传感范围。沿着这两种效果看回调，才容易分清同一个船插怎样参与生涯、装配和战斗。

*本节预期阅读时间：\~28 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 按调用对象选择船插效果回调；
- 区分生涯、战斗和装配界面的行为；
- 配置提示文本与 S-mod 效果；
- 根据运行结果定位回调时机错误。

## 前置知识

- [C2-S2] 的 `hull_mods.csv`、基础数值修改、描述占位符和安装限制；
- [C6-S4] 的 JAR 构建与游戏加载；
- [C7-S2] 的 `Global` 服务访问阶段；
- [C8-S1] 的战斗引擎状态与暂停语义；
- [C8-S4] 的 `MutableShipStatsAPI`、modifier ID 与 Dynamic。

## 编写两个船插示例

本节把前面的回调放进两个不同运行环境。装甲训练船插负责装配提示、S-mod 和战斗表现；舰队维护船插负责生涯成员效果与舰队同步。两行数据都放在 `data/hullmods/hull_mods.csv`，`script` 列分别指向对应完整类。

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
装甲训练,GearIn_armor_training,0,,,"defensive, merc, standard",Defenses,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInArmorTrainingHullMod,"护甲和有效护甲分别提高 %s，每个装甲格每秒修复 %s 点。","提高护甲并修复装甲。",S-mod 将两项护甲加成分别提高至 %s，每个装甲格每秒修复 %s 点。,graphics/hullmods/generic_mod.png
舰队维护,GearIn_fleet_maintenance,0,,,"logistics, merc, standard",Logistics,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInFleetMaintenanceHullMod,"降低舰队成员的燃料消耗 %s。","降低燃料消耗。",,
```

装甲训练直接使用当前 `ShipAPI` 回调，分别展示不兼容船插处理、战斗初始化和装甲修复。舰队维护则把单舰数值写入 `applyEffectsBeforeShipCreation`，把全舰队传感范围写入 `onFleetSync`；两个效果各自使用稳定的 modifier ID。

## 注册与构建

源码放在 `src/data/scripts/gearin/hullmods/`，执行 `gradle jar` 构建。JAR 中应同时包含 `GearInArmorTrainingHullMod.class` 和 `GearInFleetMaintenanceHullMod.class`，否则 CSV 注册值虽然正确，游戏仍然找不到效果类。

## 先分清效果对象与当前舰船

`script` 列连接船插数据与效果类，继承 `BaseHullMod` 后只覆写当前功能需要的方法。下面从数值、战斗和生涯三个场合安排它们，再补上安装与显示。

游戏第一次取得该船插的效果对象时调用 `init(HullModSpecAPI spec)`。`BaseHullMod` 会把 `spec` 保存下来，`isSMod(...)`、`isBuiltIn(...)` 和 `hasSModEffect()` 都从这里读取当前船插的静态配置。**效果类在一个应用会话内复用，当前舰船由回调参数提供。** 临时计算使用局部变量，持续状态放在实际所属的舰船或存档对象上；生涯持久数据可以放入 `SectorAPI.getPersistentData()`。若把第一艘船的计时器直接放进效果类字段，下一艘船收到的就也是这份计时器。

## 在数值重建时修改舰船

先处理安装后就存在的加成。需要同时影响生涯面板和战斗舰船时，使用 `applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id)`。生涯重建会调用它，战斗舰船创建前也会调用它，所以 `MutableShipStatsAPI` 的普通 `MutableStat` 和 Dynamic 修饰都应在这里写入。

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

普通安装的 `bonus` 为 10，S-mod 再加 15，得到 25。`isSMod(stats)` 同时识别故事点内置与增强内置船插，两种分支都在数值重建时写入。

这里有两项加成：`getArmorBonus()` 增加实际装甲容量，`getEffectiveArmorBonus()` 再提高伤害计算采用的有效装甲。**两项各加 10%，并不是最终只加 10%。** 满装甲且其他修饰为空时，普通效果的两项倍率相乘为 1.21；S-mod 时为 1.5625。损伤和最低装甲条件还会影响实际减伤，设计描述也要把两项分开说明。

## 让战斗效果跟随当前舰船

`applyEffectsAfterShipCreation(ShipAPI ship, String id)` 在舰船实体创建后调用，装配界面的预览舰也会经过这个阶段。此时可以读取完整的 `ShipAPI`，处理舰体初始化；共用数值仍在之前的重建回调写入。本例检查不兼容的 `unstable_injector`，装配界面的拒绝条件稍后使用同一判断。

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

舰船第一次加入战斗引擎后调用 `applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id)`。本例在这里显示一次提示，确认舰船已加入当前引擎。同一舰船移除后再加入时，这个回调也只执行首次初始化。

```java
@Override
public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
    CombatEngineAPI engine = Global.getCombatEngine();
    engine.addFloatingText(ship.getLocation(),
            "GearIn armor training", 14f, Color.CYAN,
            ship, 0f, 1f);
}
```

需要每帧读取舰船状态时使用 `advanceInCombat(ShipAPI ship, float amount)`。它在暂停时不调用。本例过滤失效舰船，再遍历装甲网格：普通安装每格每秒修复 4 点，S-mod 每格每秒 8 点，满格时停止增加。

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

## 在生涯中处理单舰与整支舰队

`advanceInCampaign(FleetMemberAPI member, float amount)` 以舰队成员为单位运行。它提供单舰的 `MutableShipStatsAPI`、船体等级和装配，适合处理该成员的持续效果。舰队数据重新同步时也会以 `amount == 0f` 调用，因此需要区分正常时间推进和同步重算。

```java
@Override
public void advanceInCampaign(FleetMemberAPI member, float amount) {
    if (amount <= 0f) {
        LOG.info("GearIn fleet maintenance sync: " + member.getShipName());
    }
}
```

如果效果作用于整支舰队，可以让类同时实现 `HullModFleetEffect`。`withAdvanceInCampaign()` 返回 `true` 时，游戏会调用 `advanceInCampaign(CampaignFleetAPI fleet)`；`withOnFleetSync()` 返回 `true` 时，舰队成员变化或船插变化会调用 `onFleetSync(CampaignFleetAPI fleet)`。**这组回调覆盖所有舰队，包括没有安装当前船插的舰队。** 本例只启用同步回调，根据成员数量写入传感范围；数量为 0 时解除自身来源。每帧全舰队计算的代价由所有舰队共同承担，编者建议先看成员变化是否已经足够触发所需更新。

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
    if (count == 0) {
        fleet.getStats().getSensorRangeMod().unmodify(SENSOR_MOD_ID);
        return;
    }
    fleet.getStats().getSensorRangeMod().modifyFlat(SENSOR_MOD_ID,
            count * SENSOR_RANGE_BONUS);
}
```

## 在装配界面决定能否安装

`isApplicableToShip(ShipAPI ship)` 只回答当前船能否安装，`getUnapplicableReason(ShipAPI ship)` 提供拒绝文本。把允许条件和拒绝原因一起写。本例检测不稳定推进器，已有它时拒绝安装并说明原因：

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
    tooltip.addPara("S-mod 会把每个装甲格的修复速度提高到每秒 8 点。", 10f);
}
```

S-mod 是否有独立效果由 `hasSModEffect()` 决定。`sModDesc` 为空或以 `#` 开头时，`BaseHullMod` 不会显示 S-mod 效果段；有文本时，`hasSModEffectSection(...)` 决定当前界面是否显示，`addSModEffectSection(...)` 负责写入效果内容，`addSModSection(...)` 负责标题和说明。`isSModEffectAPenalty()` 返回 `true` 时，界面会按惩罚样式显示该段。

## 补充装配界面的显示方法

`getTooltipWidth()` 返回船插提示宽度；`getBorderColor()` 和 `getNameColor()` 分别控制边框和名称颜色。`getDisplayCategoryIndex()` 选择显示分类，`getDisplaySortOrder()` 控制分类内排序，`showInRefitScreenModPickerFor(ShipAPI ship)` 决定船插是否出现在当前装配选择器中。

需要物品才能安装时，`getRequiredItem()` 返回需求物品，`addRequiredItemSection(...)` 写入提示。`affectsOPCosts()` 声明船插会影响装配点成本；这类成本变化用于内置船插，普通安装与移除不会处理相应成本变化。

## 完整最小例

按前面的源码路径保存两个类，再构建 JAR。

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
        engine.addFloatingText(ship.getLocation(),
                "GearIn armor training", 14f, Color.CYAN,
                ship, 0f, 1f);
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
        tooltip.addPara("S-mod 会把每个装甲格的修复速度提高到每秒 8 点。", 10f);
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
            fleet.getStats().getSensorRangeMod().unmodify(SENSOR_MOD_ID);
            return;
        }
        fleet.getStats().getSensorRangeMod().modifyFlat(SENSOR_MOD_ID,
                count * SENSOR_RANGE_BONUS);
    }
}
```

:::

## 验证方案

前置数据：已登记两行 `hull_mods.csv`，测试战役使用 [C4-S1] 的 `GearIn_test`，并已将构建结果放进 MOD 的 `jars/`。

1. 启动游戏并在装配界面安装 "装甲训练"，核对基础描述、S-mod 描述和互斥提示。
2. 进入 `GearIn_test` 战斗，观察舰船加入战斗时的浮动文字；让舰船承受装甲损伤，确认 `advanceInCombat` 在运行时逐渐修复装甲，暂停后修复停止。
3. 在生涯中安装 "舰队维护"，核对单舰燃料消耗；移动舰队成员后重新打开舰队信息，确认传感范围按装有该船插的成员数量更新。
4. 移除 "舰队维护" 并再次触发舰队同步，确认传感范围修饰被清除。
5. 开始第二场战斗，确认提示与修复读取当前舰船；两场使用同一个船插效果类，舰船状态分别计算。

预期结果：

- 两个船插都能从装配选择器中找到，日志没有 `Problem loading class`。
- "装甲训练" 的普通安装和 S-mod 都能显示护甲与修复参数，安装互斥时出现拒绝原因；已有不兼容船插时，`applyEffectsAfterShipCreation` 会移除 `unstable_injector`。
- 战斗中的浮动文字只在舰船加入当前引擎后出现，暂停期间不推进装甲修复。
- "舰队维护" 的燃料修饰来自成员 stats，舰队传感范围随同步后的成员集合变化。
- 第二场战斗通过当前舰船参数重新执行初始化，效果类继续复用，船体状态与浮动文字属于当前战斗。

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

这两个船插分别把战斗修复与生涯维护放到对应回调，共用数值在重建时写入，当前舰船和成员从参数取得。安装条件、描述参数与 S-mod 分支把这些效果接到装配界面。

选方法时先看它收到什么对象，再看什么时候调用。尤其要记住效果类的共享范围：当前船的状态有自己的归属，回调参数变化后，效果也跟着处理下一艘船。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插效果方法定义；
- `com.fs.starfarer.api.combat.BaseHullMod`：默认实现、S-mod 判定与 tooltip 辅助方法；
- `com.fs.starfarer.api.combat.HullModFleetEffect`：整支舰队的生涯回调；
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：生涯与战斗数值入口；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：单舰成员回调参数；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：整支舰队回调参数；
- `data/hullmods/hull_mods.csv`：船插注册行与描述字段。
