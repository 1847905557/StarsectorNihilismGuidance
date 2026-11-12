# 详解舰船插件

一个船插可以同时参与数值重建、战斗与生涯，但这些效果并不在同一个时机执行。本节制作装甲训练和舰队维护，分别处理装甲加成与修复、安装说明，以及单舰燃料和舰队传感范围。两个示例将展示回调怎样取得当前对象，效果又怎样保存到正确位置。

_本节预期阅读时间：\~28 分钟。_

_本节预期操作时间：\~35 分钟。_

## 学习目标

完成后，您将能够：

- 按调用对象选择船插效果回调；
- 区分生涯、战斗和装配界面的行为；
- 配置提示文本与 S-mod 效果；
- 根据运行结果定位回调时机错误。

## 前置知识

- [C2-S2] 的 `hull_mods.csv`、基础数值修改、描述占位符和安装限制；
- [C6-S2] 的 JAR 构建与游戏加载；
- [C7-S2] 的 `Global` 服务访问阶段；
- [C8-S1] 的战斗引擎状态与暂停语义；
- [C8-S2] 的 `MutableShipStatsAPI`、modifier ID 与 Dynamic。

## 准备两个船插

装甲训练既提高装甲，也在战斗中修复受损装甲格。普通安装时，两种装甲加成各为 10%，每格每秒修复 4 点；S-mod 将加成提高到各 25%，修复速度提高到每格每秒 8 点。舰队维护则降低安装舰船的燃料消耗，并根据安装数量增加舰队传感范围。

在 `src/data/scripts/gearin/hullmods/` 创建 `GearInArmorTrainingHullMod.java` 和 `GearInFleetMaintenanceHullMod.java`，两类都继承 `BaseHullMod`。注册写入 `data/hullmods/hull_mods.csv`，已有表头时只追加数据行：

```csv
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
装甲训练,GearIn_armor_training,0,,,"defensive, merc, standard",Defenses,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInArmorTrainingHullMod,"护甲和有效护甲分别提高 %s，每个装甲格每秒修复 %s 点。","提高护甲并修复装甲。",S-mod 将两项护甲加成分别提高至 %s，每个装甲格每秒修复 %s 点。,graphics/hullmods/generic_mod.png
舰队维护,GearIn_fleet_maintenance,0,,,"logistics, merc, standard",Logistics,2500,TRUE,,,4,8,12,20,data.scripts.gearin.hullmods.GearInFleetMaintenanceHullMod,"降低舰队成员的燃料消耗 %s。","降低燃料消耗。",,
```

`script` 指向两个完整类名，装甲训练使用原版 `graphics/hullmods/generic_mod.png` 图标。类完成后执行 `gradle jar`，产物中提供 `GearInArmorTrainingHullMod.class` 和 `GearInFleetMaintenanceHullMod.class`，游戏才能按注册行加载它们。

## 让安装收益进入舰船数值

安装装甲训练后，装配面板与战斗中的舰船都应当使用加成。这部分放在 `applyEffectsBeforeShipCreation(...)`：生涯数值重建会调用它，战斗舰船创建前也会调用它。

参数 `hullSize` 提供船体等级，`stats` 提供本次重建的数值对象，`id` 提供船插注册 ID。沿用 [C8-S2] 的来源写法，以这个 ID 记录加成，就能在再次重建时覆盖自己的值。

在 `GearInArmorTrainingHullMod` 中加入局部方法：

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

普通安装使用 `ARMOR_BONUS_PERCENT` 的 10，S-mod 再加上 `SMOD_ADDITIONAL_PERCENT` 的 15。`isSMod(stats)` 同时识别故事点内置与增强内置船插，因此两种安装方式都能进入对应分支。

这里修改了两项属性。`getArmorBonus()` 提高实际装甲容量，`getEffectiveArmorBonus()` 提高伤害计算采用的有效装甲。**两项各加 10%，会共同参与装甲计算。** 满装甲且其他修饰为空时，倍率相乘为 1.21；S-mod 的两项 25% 相乘为 1.5625。损伤和最低装甲条件还会影响实际减伤。

因此 CSV 描述分别说明装甲与有效装甲。只写一句笼统的 "护甲提高 10%"，会把这两项效果压成另一种含义。

## 在安装界面处理互斥

装甲训练不允许装到已有 `unstable_injector` 的舰船上。界面需要先知道是否允许安装，再显示拒绝原因，这两个方法便采用相同条件：

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

[C2-S2] 已经讲过这种安装判断：它由本船插执行，不能替另一个船插规定反向安装条件。本例还在舰体创建后处理已经存在于装配中的不兼容组合。此时 `applyEffectsAfterShipCreation(...)` 提供完整 `ShipAPI`，装配预览舰也会经过这个阶段：

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

界面判断负责拒绝本次安装，创建后的处理负责移除装配中已经存在的 `unstable_injector`。共用数值仍放在前面的重建回调里，否则生涯面板不会取得同样的加成。

## 让受损装甲在战斗中恢复

战斗中的持续修复使用 `advanceInCombat(ShipAPI ship, float amount)`。这个回调在暂停时不调用，本例再过滤死亡与残骸状态，按步长计算本次修复量：

```java
@Override
public void advanceInCombat(ShipAPI ship, float amount) {
    if (!ship.isAlive() || ship.isHulk()) {
        return;
    }
    repairArmor(ship, amount * (isSMod(ship) ? 8f : 4f));
}
```

完整类的 `repairArmor(...)` 从当前舰船取得装甲网格，遍历每一个格子，增加本次修复量，并以 `getMaxArmorInCell()` 限制单格上限。写回后调用 `syncWithArmorGridState()`，让损伤外观与网格状态同步。

这里的 4 点或 8 点是**每个装甲格的每秒速率**，修复会同时发生在各个受损格上。它并非整艘船共用的一份修复额度；安排类似效果的数值时，覆盖范围与速率需要一起考虑。

### 舰体创建与加入战斗是两个时机

前面的创建后回调也用于装配预览。要确认舰船已经进入战斗引擎，使用 `applyEffectsAfterShipAddedToCombatEngine(...)`。本例在这里显示一次青色提示：

```java
@Override
public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
    CombatEngineAPI engine = Global.getCombatEngine();
    engine.addFloatingText(ship.getLocation(),
            "GearIn armor training", 14f, Color.CYAN,
            ship, 0f, 1f);
}
```

它只执行首次加入时的初始化。同一舰船移出引擎后再次加入，也不会重复执行，因此适合本例的一次性战斗提示。

航母船插还有 `applyEffectsToFighterSpawnedByShip(...)`。它把新战机 `fighter` 与母舰 `ship` 一同传入，适合处理这次战机生成。本例只记录生成来源，实际战机状态仍保存在战机对象上。

## 让说明准确呈现这些效果

基础描述与 S-mod 描述分别来自 CSV 的 `desc` 和 `sModDesc`。[C2-S2] 的 `%s` 参数方式继续适用：`getDescriptionParam(...)` 返回普通参数，`getSModDescriptionParam(...)` 返回 S-mod 参数。本例分别提供 `10%` 与 `4`、`25%` 与 `8`，与前面的实际效果对应。

这两种方法都有带 `ShipAPI` 的重载。固定数值说明使用不带舰船的版本，需要显示当前舰船状态时才使用舰船参数；基类会保存 `init(HullModSpecAPI spec)` 取得的规格，供安装方式与说明方法使用。

需要追加说明时，保留 CSV 正文，并在其后写入新段落：

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

`shouldAddDescriptionToTooltip(...)` 决定是否保留基础段落，`addPostDescriptionSection(...)` 提供追加内容。动态结果、安装条件或当前舰船数据都可以放在这里，必要的效果说明则保持直接可读。

S-mod 的效果段由 `hasSModEffect()` 判断是否存在：`sModDesc` 为空或以 `#` 开头时，基类不显示这一段。有内容时，`hasSModEffectSection(...)` 判断当前界面是否呈现，`addSModEffectSection(...)` 写效果内容，`addSModSection(...)` 安排标题与说明。`isSModEffectAPenalty()` 返回 `true` 时，使用惩罚样式。

## 舰队维护怎样影响整支舰队

单舰燃料消耗同样在 `applyEffectsBeforeShipCreation(...)` 中修改，完整例使用 `getFuelUseMod().modifyMult(id, 0.9f)`。安装收益因此进入成员数值，生涯中就能读取。

CSV 中的 `%s` 也由这个倍率换算。向 `GearInFleetMaintenanceHullMod` 加入局部方法，索引 0 返回降低的百分比：

```java
@Override
public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) {
        return Math.round((1f - FUEL_USE_MULT) * 100f) + "%";
    }
    return null;
}
```

`FUEL_USE_MULT` 为 `0.9f` 时，说明显示 "降低舰队成员的燃料消耗 10%。"。修改倍率时，数值效果与说明一起变化。

持续处理某名成员时，使用 `advanceInCampaign(FleetMemberAPI member, float amount)`。成员提供自己的 stats、船体等级和装配；舰队同步还会以 `amount == 0f` 调用它，本例专门记录这种调用：

```java
@Override
public void advanceInCampaign(FleetMemberAPI member, float amount) {
    if (amount <= 0f) {
        LOG.info("GearIn fleet maintenance sync: " + member.getShipName());
    }
}
```

传感范围则属于舰队。让类同时实现 `HullModFleetEffect`，在 `onFleetSync(...)` 中统计装有本船插的成员，每艘提供 50 范围，移除最后一艘安装舰后解除自身来源：

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

`withOnFleetSync()` 返回 `true`，让成员组成或船插变化触发更新。需要舰队级持续推进时，则让 `withAdvanceInCampaign()` 返回 `true`，执行 `advanceInCampaign(CampaignFleetAPI fleet)`。

**这两个舰队级入口会处理所有舰队，包括没有安装本船插的舰队。** 编者建议先由这个调用范围决定是否需要逐帧逻辑。本例的收益只随安装数量变化，同步已经提供所需时机；开启持续遍历，只会让全生涯的舰队反复执行同一计算。

## 船插类中的字段属于谁

前面的战斗修复与舰队统计都从参数取得当前对象。这里还有一项重要区别：普通船插效果在应用会话中复用，同一实例会继续处理其他舰船；舰队级效果又有自己的实例入口。

::: warning 单舰状态不能放进共享效果字段

**编者建议将随舰船变化的状态保存在所属舰船上。** 效果类字段不会随每艘安装舰独立建立，单舰与舰队回调的实例字段也不能用来共享进度。临时计算使用局部变量，生涯持久状态可以保存在 `SectorAPI.getPersistentData()`。

:::

例如修复某艘舰船时，网格来自本次 `ship`；计算舰队传感范围时，成员来自本次 `fleet`。保持这种归属，同一个效果类处理下一艘船或另一支舰队时，结果才仍对应当前对象。

## 其他安装与显示入口速查表

下面的入口用于进一步调整改装条件和提示外观，两个完整示例沿用未覆写的方法的基类行为。

| 需要调整的内容       | 方法与生效条件                                                                                                                                   |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 当前地点是否允许改装 | `canBeAddedOrRemovedNow(...)` 接收舰船、市场与 `CoreUITradeMode`；`getCanNotBeInstalledNowReason(...)` 提供原因。自动装配时舰船参数可能为 `null` |
| 提示宽度与颜色       | `getTooltipWidth()` 返回宽度，`getBorderColor()` 与 `getNameColor()` 设置边框与名称颜色                                                          |
| 装配分类与顺序       | `getDisplayCategoryIndex()` 选择分类，`getDisplaySortOrder()` 控制分类内排序                                                                     |
| 是否出现在当前选择器 | `showInRefitScreenModPickerFor(ShipAPI ship)` 判断当前舰船的显示                                                                                 |
| 安装所需物品         | `getRequiredItem()` 返回物品，`addRequiredItemSection(...)` 写入对应提示                                                                         |
| 改变 OP 成本         | `affectsOPCosts()` 声明成本影响；这类效果用于内置船插，普通安装与移除不处理相应成本变化                                                          |

### D 类损伤重排标签速查表

`Derelict Operations` (技能 ID 为 `derelict_contingent`) 通过 `FieldRepairsScript` 重排舰船的 D 类损伤。自制 D 插可以在 `hull_mods.csv` 的 `tags` 列填写下面两个标签，控制它在重排时的处理方式：

| 标签                | 技能重排时的用途          |
| ------------------- | ------------------------- |
| `DO_can_not_add`    | 从新增 D 插的候选中排除   |
| `DO_can_not_remove` | 保留舰船上已有的这类 D 插 |

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
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return Math.round((1f - FUEL_USE_MULT) * 100f) + "%";
        }
        return null;
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
3. 在生涯中安装 "舰队维护"，核对说明中的 `10%` 与单舰燃料消耗；移动舰队成员后重新打开舰队信息，确认传感范围按装有该船插的成员数量更新。
4. 移除 "舰队维护" 并再次触发舰队同步，确认传感范围修饰被清除。
5. 开始第二场战斗，确认提示与修复读取当前舰船；两场使用同一个船插效果类，舰船状态分别计算。

预期结果：

- 两个船插都能从装配选择器中找到，日志没有 `Problem loading class`。
- "装甲训练" 的普通安装和 S-mod 都能显示护甲与修复参数，安装互斥时出现拒绝原因；已有不兼容船插时，`applyEffectsAfterShipCreation` 会移除 `unstable_injector`。
- 战斗中的浮动文字只在舰船加入当前引擎后出现，暂停期间不推进装甲修复。
- "舰队维护" 显示燃料消耗降低 `10%`，成员 stats 使用 `0.9` 倍率，舰队传感范围随同步后的成员集合变化。
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

装甲训练在重建时应用装甲收益，在战斗回调中修复，并在装配界面说明安装与 S-mod 效果。舰队维护则分别修改成员燃料与同步后的全舰队传感范围。

同一个效果对象会继续处理其他舰船，回调参数才提供本次对象。加成写到对应 stats，持续状态保存在所属对象，安装与显示方法再将这些规则交给玩家。

## 建议参考项

- `com.fs.starfarer.api.combat.HullModEffect`：船插效果方法定义；
- `com.fs.starfarer.api.combat.BaseHullMod`：默认实现、S-mod 判定与 tooltip 辅助方法；
- `com.fs.starfarer.api.combat.HullModFleetEffect`：整支舰队的生涯回调；
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：生涯与战斗数值入口；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：单舰成员回调参数；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：整支舰队回调参数；
- `data/hullmods/hull_mods.csv`：船插注册行与描述字段。
