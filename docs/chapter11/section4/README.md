# 维护舰队成员状态

给玩家一艘舰船时，除了船体和装配，还要决定它的损伤、船员、CR 与封存状态。本节创建一艘狼和一艘锤头鲨，用后者演示损伤、缺员与封存，再把它留在舰队中，比较脚本写入的即时状态与游戏继续整备后的结果。

_本节预期阅读时间：\~26 分钟。_

_本节预期操作时间：\~35 分钟。_

## 学习目标

完成后，您将能够：

- 创建一艘拥有稳定生涯身份的 `FleetMemberAPI`，并把它加入指定舰队；
- 读取船体完整度、船员数量、CR、维修状态、封存状态和可部署结果；
- 在修改单舰状态后重新读取结果，并判断是否需要更新成员 `stats`；
- 解释 `FleetMemberAPI` 与战斗侧 `ShipAPI` 的对象边界。

## 前置知识

- [C11-S2] 的 `FleetDataAPI` 成员归属、旗舰和集合顺序；
- [C11-S3] 的成员 `stats` 重算与装配替换边界；
- Java 对象引用、一次性脚本和 `starsector.log` 的基本用法。

## 创建并保留生涯成员

创建 `src/data/scripts/gearin/campaign/GearInFleetMemberBuilder.java` 与 `GearInFleetMemberStatus.java`。前者提供可复用的成员创建方法，后者调用这个方法创建锤头鲨，再修改它的状态。在 `GearInModPlugin` 添加两类的 `import`，向已有的 `onNewGameAfterTimePass(...)` 追加登记，沿用其中的 `sector`：

```java
sector.addTransientScript(new GearInFleetMemberBuilder());
sector.addTransientScript(new GearInFleetMemberStatus());
```

新档推进完成后，两份脚本各执行一轮。狼采用 `wolf_Assault`，锤头鲨采用 `hammerhead_Elite`，两艘都保留在玩家舰队，便于后面通过界面和战斗继续观察。

成员创建沿用 [C11-S2] 的工厂入口。**创建后还要加入舰队**，舰队界面、汇总与部署编队才会包含它。下面是创建方法的核心操作：

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
FleetDataAPI data = fleet.getFleetData();
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "wolf_Assault");

data.addFleetMember(member);

LOG.info("member id=" + member.getId()
        + " fleet=" + fleet.getName()
        + " count=" + data.getNumMembers());
```

`createFleetMember(...)` 的第二个参数是装配或战机联队的规格 ID；`member.getId()` 是游戏为这个成员生成的独立 ID。本节保留生成的 ID，后续用它追踪同一艘船。

加入后，成员的 `getFleetData()` 指向目标舰队，集合也会标记待同步。记录加入前后的归属，可以把新对象与舰队里的成员对应起来。以下局部片段使用锤头鲨：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "hammerhead_Elite");

LOG.info("before add: id=" + member.getId()
        + " fleetData=" + member.getFleetData());

data.addFleetMember(member);

String variantId = member.getVariant() == null
        ? "<none>"
        : member.getVariant().getHullVariantId();
LOG.info("after add: id=" + member.getId()
        + " hull=" + member.getHullId()
        + " variant=" + variantId
        + " fleetData=" + member.getFleetData()
        + " count=" + data.getNumMembers());
```

成员加入时会补齐缺少的船名与默认舰长，并清除新成员的旗舰标记。完整创建方法另外接受 `makeFlagship` 参数，本次传入 `false`，保持玩家原旗舰。旗舰切换的可选用法在状态说明之后给出。

## 记录修改前的状态

锤头鲨创建后，先取得它的状态对象。损伤从 `getStatus()` 读取，船员从 `getCrewComposition()` 读取，CR、维修速率与封存由 `getRepairTracker()` 管理。下面的局部日志把这些结果记录在同一个成员 ID 下：

```java
FleetMemberStatusAPI status = member.getStatus();
CrewCompositionAPI crewComposition = member.getCrewComposition();
RepairTrackerAPI repairTracker = member.getRepairTracker();
ShipVariantAPI variant = member.getVariant();

float hullFraction = status.getHullFraction();
float crewFraction = member.getCrewFraction();
int crew = crewComposition.getCrewInt();
float cr = repairTracker.getCR();
boolean needsRepairs = status.needsRepairs();
boolean mothballed = repairTracker.isMothballed();
String hullVariantId = variant == null
        ? "<none>"
        : variant.getHullVariantId();

LOG.info("member status: id=" + member.getId()
        + " hull=" + hullFraction
        + " crew=" + crew
        + " crewFraction=" + crewFraction
        + " cr=" + cr
        + " repairs=" + needsRepairs
        + " mothballed=" + mothballed
        + " variant=" + hullVariantId);
```

船体完整度与 CR 都使用 0 到 1 的比例。`getCrewInt()` 返回船员数量，`getCrewFraction()` 返回最低船员需求的满足比例；`getBaseCR()` 保留基础 CR，`getCR()` 则反映船员不足后的有效 CR。这些数值需要分别记录，否则缺员造成的 CR 下降很容易被误认为 `setCR(...)` 没有生效。

维修速率与近期 CR 事件也可以从维修跟踪器读取，损伤本身仍由 `status` 表示。接下来按完整状态脚本的顺序，先施加损伤，再修改船员和 CR，最后切换封存。

## 施加损伤与修复船体

状态脚本用 `applyHullFractionDamage(0.2f)` 施加相当于最大船体 20% 的损伤。需要指定完整度时还可以使用 `setHullFraction(...)`，直接修复则使用 `repairFully()`。下面的局部实验在损伤后立即修复，分别记录两次结果；完整状态脚本会保留损伤供界面观察：

```java
float hullBefore = member.getStatus().getHullFraction();
member.getStatus().applyHullFractionDamage(0.2f);
float hullAfterDamage = member.getStatus().getHullFraction();
boolean needsRepairAfterDamage = member.needsRepairs();

member.getStatus().repairFully();
float hullAfterRepair = member.getStatus().getHullFraction();
boolean needsRepairAfterRepair = member.needsRepairs();

LOG.info("hull transition: before=" + hullBefore
        + " damaged=" + hullAfterDamage
        + " repaired=" + hullAfterRepair
        + " needsRepairs=" + needsRepairAfterDamage
        + "->" + needsRepairAfterRepair);
```

`repairFully()` 恢复可修复的船体、装甲及模块状态。只将船体比例设为 1 时，装甲或模块脱离状态仍可能使 `needsRepairs()` 为 `true`；因此维修结果还要结合 `computeRepairednessFraction()` 读取，它同时考虑船体与装甲。

实际生涯维修继续受维修速率与资源条件影响。本例直接调用状态接口，说明的是损伤如何写入成员；后续消耗与恢复由游戏的维修流程处理。

## 让船员不足反映到 CR

把锤头鲨的船员改为最低需求的一半，再将基础 CR 设置为 0.3。`setCrew(...)` 写入当前船员组成，`setCR(...)` 的值使用 0 到 1 的比例。局部片段如下：

```java
CrewCompositionAPI composition = member.getCrewComposition();
float crewBefore = composition.getCrew();
float crBefore = member.getRepairTracker().getCR();

composition.setCrew(Math.max(1f, member.getMinCrew() * 0.5f));
member.getRepairTracker().setCR(0.3f);

float crewAfter = composition.getCrew();
float crewFractionAfter = member.getCrewFraction();
float crAfter = member.getRepairTracker().getCR();
boolean deployable = member.canBeDeployedForCombat();

LOG.info("readiness transition: crew=" + crewBefore
        + "->" + crewAfter
        + " crewFraction=" + crewFractionAfter
        + " cr=" + crBefore
        + "->" + crAfter
        + " deployable=" + deployable);
```

**基础 CR 为 0.3、船员比例为 0.5 时，有效 CR 为 0.15。** `getCR()` 给出的正是后一个结果，`getBaseCR()` 仍为 0.3。`canBeDeployedForCombat()` 先判断封存，再比较 CR 与船员阈值：`settings.json` 中 `noDeployCRPercent` 为 0、`noDeployCrewPercent` 为 5，读取时转换成比例；战机还要求有效 CR 大于 0。因此半员与低 CR 不等于必然禁止部署，实际布尔结果由该方法读取。

::: warning 单舰船员会在舰队同步时重新分配
玩家舰队从公共货舱向各成员分配船员。本例直接修改的是即时组成，后续同步可能重新填满；长期缺员需要结合舰队货舱中的船员与各成员需求处理。
:::

## 切换封存状态

有些成员需要保留在舰队中，暂时停止使用。`setMothballed(true)` 保存封存前的基础 CR，再将基础 CR 置为 0，部署判断返回 `false`。下面的局部片段先封存，再解除封存：

```java
RepairTrackerAPI tracker = member.getRepairTracker();
boolean mothballedBefore = tracker.isMothballed();

tracker.setMothballed(true);
boolean mothballedAfter = tracker.isMothballed();
boolean deployableWhileMothballed = member.canBeDeployedForCombat();

tracker.setMothballed(false);
boolean deployableAfterRelease = member.canBeDeployedForCombat();

LOG.info("mothball transition: before=" + mothballedBefore
        + " after=" + mothballedAfter
        + " deployableWhileMothballed=" + deployableWhileMothballed
        + " deployableAfterRelease=" + deployableAfterRelease);
```

解除封存时，跟踪器恢复封存前保存的基础 CR，再按当前船员比例计算有效 CR。完整脚本先设置 0.3，封存期间读取 0，解除后基础值回到 0.3。封存切换还会更新成员 `stats` 并标记舰队待同步，因此解除后的界面结果继续受船员重新分配与生涯整备影响。

`getMemberStrength()` 也受封存与 CR 等状态影响。比较当前强度时，在状态更新后重新读取，避免保留切换前的数值。

## 装配与旗舰对成员状态的影响

### 更换装配后刷新派生数值

本节的损伤与封存操作直接作用于成员状态；装配替换还会改变这些状态所依赖的数值。沿 [C11-S3] 的方式克隆当前装配，用 `setVariant(..., false, true)` 写回并重算。下面的局部片段只修改显示名，再记录部署 CR 消耗与成员强度，[C11-S5] 继续修改实际装备：

```java
ShipVariantAPI replacement = member.getVariant().clone();
replacement.setVariantDisplayName("GearIn status check");

member.setVariant(
        replacement,
        false,
        true);

member.updateStats();
LOG.info("variant applied: id=" + member.getId()
        + " variant=" + member.getVariant().getHullVariantId()
        + " deployCost=" + member.getDeployCost()
        + " memberStrength=" + member.getMemberStrength());
```

`withRefit=true` 会按新旧装配差异处理改装 CR 影响，这里传入 `false`，让 CR 比较集中在当前状态操作上。第三个参数 `true` 触发 `updateStats()`，最低船员需求、CR 上限等派生值按新装配重算，后续 `stats` 也重新取得。

装配替换作用于这个成员，舰队归属仍由 `FleetDataAPI` 管理。重算后在原舰队重新读取数量、旗舰和汇总，才能继续对应同一个成员。

### 可选：让新成员临时成为旗舰

创建方法的 `makeFlagship=true` 调用集合级 `setFlagship(...)`，清除其他成员的旗舰标记，并将指挥官交给新旗舰。沿 [C11-S2] 的恢复方法，同时保存原旗舰与舰长。以下局部片段另创建一艘演示狼，并在同一次调用中切换和恢复：

```java
FleetMemberAPI originalFlagship = fleet.getFlagship();
FleetMemberAPI demoMember = GearInFleetMemberBuilder.createMember(
        fleet,
        "wolf_Assault",
        false);
Map<FleetMemberAPI, PersonAPI> captains = new LinkedHashMap<>();
for (FleetMemberAPI member : data.getMembersListCopy()) {
    captains.put(member, member.getCaptain());
}
data.setFlagship(demoMember);
LOG.info("temporary flagship=" + fleet.getFlagship().getId());
data.setFlagship(originalFlagship);
for (Map.Entry<FleetMemberAPI, PersonAPI> entry : captains.entrySet()) {
    entry.getKey().setCaptain(entry.getValue());
}
```

这段需要 `Map`、`LinkedHashMap` 与 `PersonAPI`，恢复完成后新增的演示狼仍留在舰队。`FleetMemberAPI.setFlagship(true)` 只处理当前成员的标记，并可能设置其舰长；集合级入口才同时维护其他成员。因此本例切换和恢复都调用 `FleetDataAPI`。

::: warning 跨生涯时间恢复旗舰要确认原成员的归属
原旗舰已经损失或转入另一支舰队时，原引用不能直接恢复为当前舰队旗舰。恢复前从当前名单确认其归属，再决定旗舰与舰长安排。
:::

## 从生涯成员进入战斗

成员保留在舰队后，可以继续验证战斗中的结果。部署时，游戏根据它的装配、舰长、指挥官、CR、船员和损伤创建战斗侧 `ShipAPI`。封存与不可部署状态会限制控制或部署，进入战斗前先记录当前成员：

```java
boolean deployable = member.canBeDeployedForCombat();
String campaignId = member.getId();

LOG.info("combat boundary: campaignMember=" + campaignId
        + " deployable=" + deployable
        + " hull=" + member.getHullId()
        + " cr=" + member.getRepairTracker().getCR());
```

部署列表反映当时的状态，退出战斗后从生涯舰队重新取得同一成员 ID，读取 CR 与船体完整度。生涯成员跟随存档，`ShipAPI` 对应本场战斗实例；跨战斗功能保存成员身份，再从当前舰队取得对象。

## 完整最小例

创建类提供可复用的 `createMember(...)`，状态类用同一入口创建锤头鲨。保存两类并保留前面的新档登记。

::: details 完整成品：GearInFleetMemberBuilder

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 创建一艘生涯舰队成员，并可选择把它设为旗舰。
 */
public class GearInFleetMemberBuilder implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberBuilder";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberBuilder.class);

    private static final String VARIANT_ID = "wolf_Assault";

    private boolean done;

    /**
     * 创建成员、加入舰队并返回创建结果。
     *
     * @param fleet 目标舰队
     * @param variantId 船体装配 ID
     * @param makeFlagship 是否把新成员设为旗舰
     * @return 已加入目标舰队的成员
     */
    public static FleetMemberAPI createMember(
            CampaignFleetAPI fleet,
            String variantId,
            boolean makeFlagship) {
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                variantId);
        data.addFleetMember(member);

        if (makeFlagship) {
            data.setFlagship(member);
        }

        return member;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = createMember(
                fleet,
                VARIANT_ID,
                false);

        LOG.info("GearIn member builder: id=" + member.getId()
                + " hull=" + member.getHullId()
                + " variant=" + member.getVariant().getHullVariantId()
                + " fleet=" + fleet.getName()
                + " members=" + fleet.getFleetData().getNumMembers()
                + " flagship=" + member.isFlagship());
    }
}
```

:::

::: details 完整成品：GearInFleetMemberStatus

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;

/**
 * 修改并记录一艘生涯成员的状态，随后保留成员供舰队界面观察。
 */
public class GearInFleetMemberStatus implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberStatus";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberStatus.class);

    private static final String VARIANT_ID = "hammerhead_Elite";

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = GearInFleetMemberBuilder.createMember(
                fleet,
                VARIANT_ID,
                false);

        FleetMemberStatusAPI status = member.getStatus();
        CrewCompositionAPI crew = member.getCrewComposition();
        RepairTrackerAPI tracker = member.getRepairTracker();

        float hullBefore = status.getHullFraction();
        float crBefore = tracker.getCR();
        float crewBefore = crew.getCrew();

        status.applyHullFractionDamage(0.2f);
        crew.setCrew(Math.max(1f, member.getMinCrew() * 0.5f));
        tracker.setCR(0.3f);
        tracker.setMothballed(true);

        float hullAfter = status.getHullFraction();
        float crWhileMothballed = tracker.getCR();
        float crewAfter = crew.getCrew();
        boolean deployableWhileMothballed = member.canBeDeployedForCombat();

        tracker.setMothballed(false);
        float crAfterRelease = tracker.getCR();
        float baseCrAfterRelease = tracker.getBaseCR();

        LOG.info("GearIn member status: id=" + member.getId()
                + " hull=" + hullBefore + "->" + hullAfter
                + " crew=" + crewBefore + "->" + crewAfter
                + " crewFraction=" + member.getCrewFraction()
                + " cr=" + crBefore + "->" + crAfterRelease
                + " crWhileMothballed=" + crWhileMothballed
                + " baseCrAfterRelease=" + baseCrAfterRelease
                + " mothballed=false"
                + " deployableWhileMothballed=" + deployableWhileMothballed
                + " deployable=" + member.canBeDeployedForCombat()
                + " needsRepairs=" + member.needsRepairs()
                + " fleetMembers=" + data.getNumMembers());
    }
}
```

:::

两个脚本执行后结束，成员继续保留在玩家舰队。后续功能通过已保存的成员 ID 从当前集合取得对象，再修改它的状态；重新调用创建入口会得到另一艘船和另一个 ID。

## 验证方案

前置数据：按本章新档入口测试，玩家舰队已建立，两个脚本在新档推进后登记。

1. 构建 MOD，确认 JAR 包含 `GearInFleetMemberBuilder.class` 和 `GearInFleetMemberStatus.class`。
2. 开始新档，等待两份脚本执行，在 `starsector.log` 搜索 `GearIn member builder` 与 `GearIn member status`。
3. 打开舰队界面，按日志中的成员 ID、船体和装配名称定位新成员，确认它属于玩家舰队且未被错误设为旗舰。
4. 在状态日志中记录 `hull`、`crew`、`cr`、`mothballed`、`needsRepairs` 和 `deployable` 字段，比较封存期间与解除后的值。
5. 在舰队界面确认锤头鲨保留、最终解除封存，比较船体和 CR；船员组成按货舱重新分配，维修与 CR 也会随生涯时间继续推进。
6. 在不修改脚本对象引用的情况下进入一次战斗，观察部署列表对不可部署状态的提示；退出战斗后重新取得同一成员 ID。

预期结果：

- 创建日志包含自动生成的成员 ID、`wolf_Assault` 装配和玩家舰队成员数量；
- 即时状态日志显示船体与船员变化，封存期间有效 CR 为 0、`deployableWhileMothballed=false`；解除后基础 CR 为 0.3，有效 CR 按当时船员比例计算；
- 解除封存后，`canBeDeployedForCombat()` 按当前 CR 与船员阈值返回结果；船体损伤另由损伤和维修字段记录；
- 演示成员保留在玩家舰队中，舰队界面和日志使用同一个成员 ID；
- 战斗结束后重新取得 `FleetMemberAPI`，没有依赖战斗侧 `ShipAPI` 的旧引用。

## 故障分析

- **日志显示成员已创建，但舰队界面没有成员**：创建了 `FleetMemberAPI`，却没有把它加入目标 `FleetDataAPI`。
    - **解决方案**：确认 `data.addFleetMember(member)` 在读取成员数量和设置旗舰之前执行，并核对 `member.getFleetData() == data`。

- **新成员意外成为旗舰**：调用了 `data.setFlagship(member)`，或直接设置成员旗舰标志后没有恢复原旗舰。
    - **解决方案**：默认创建时传入 `makeFlagship=false`，需要切换时保存原旗舰，使用集合级 `setFlagship(...)` 完成切换和恢复。

- **船体完整度恢复但仍然显示维修中**：装甲、模块或其他部件仍有损伤，`getHullFraction()` 没有覆盖这些状态。
    - **解决方案**：同时读取 `needsRepairs()` 与 `computeRepairednessFraction()`，通过游戏维修流程处理剩余损伤。

- **CR 数值与预期不同**：读取的是 `getBaseCR()`、`getCR()` 或船员比例修正前后的不同阶段，或者把百分数当作 `0` 到 `1` 的小数传入。
    - **解决方案**：记录 `getCR()`、`getBaseCR()` 和 `getCrewFraction()`，调用 `setCR(0.3f)` 这类小数值，并在修改后重新读取。

- **成员封存后仍尝试部署**：只检查船体状态，没有检查 `isMothballed()` 或 `canBeDeployedForCombat()`。
    - **解决方案**：在部署入口先读取 `canBeDeployedForCombat()`，将封存、CR、船员和维修状态分别写入日志。

- **装配替换后派生值没有变化**：使用 `setVariant(..., false, false)` 后继续使用旧 `stats`，或只修改了共享装配对象。
    - **解决方案**：对装配先 `clone()`，写回时传入 `withStatsUpdate=true`，随后调用 `updateStats()` 并重新读取成员派生值。

- **战斗结束后访问对象报错**：把战斗侧 `ShipAPI` 引用保存到跨战斗字段或存档数据。
    - **解决方案**：只保存生涯成员 ID；每次战斗结束后从玩家舰队的 `FleetDataAPI` 重新取得 `FleetMemberAPI`。

- **演示成员重复出现**：一次性脚本实例被重新注册或没有使用持久标记判断已经创建过成员。
    - **解决方案**：为每个脚本保留 `done` 状态，并在正式入口使用专用 Memory 记录成员 ID；执行前检查已有成员再决定复用或清理。

## 总结

一艘生涯舰船由成员保存身份与当前状态，加入 `FleetDataAPI` 后才参与舰队上下文。损伤、船员、CR 与封存各有入口，也相互影响：缺员降低有效 CR，封存保存并清零基础 CR，同步与生涯推进继续改变整备结果。

脚本的即时日志与稍后打开的界面可能处于不同阶段。本节保留两艘成员，用 ID 对应日志、界面和战斗；状态发生变化时，就能沿着同一艘船确认变化的原因。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份、装配、状态、部署和 `stats` 入口；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员加入、移除、旗舰和集合同步；
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体、装甲、部件损伤和维修状态；
- `com.fs.starfarer.api.fleet.CrewCompositionAPI`：船员和海军陆战队数量；
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR、维修事件、封存和恢复速率；
- `com.fs.starfarer.api.combat.ShipAPI`：战斗侧舰船对象及其生命周期；
- `data/config/settings.json`：部署阈值配置；
- `Starsector/starsector-core/starsector.log`：核对脚本输出与运行时错误。
