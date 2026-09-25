# 维护舰队成员状态

本节把一艘舰船当作一个独立的生涯成员来处理。示例先创建成员并把它交给玩家舰队，再从同一个 `FleetMemberAPI` 读取船体、船员、战斗准备度和封存状态，最后保留成员让舰队界面继续显示。这样可以把成员自身的状态与舰队集合的职责分开观察。

*本节预期阅读时间：~26 分钟。*

*本节预期操作时间：~35 分钟。*

## 学习目标

完成后，您将能够：

- 创建一艘拥有稳定生涯身份的 `FleetMemberAPI`，并把它加入指定舰队；
- 读取船体完整度、船员数量、CR、维修状态、封存状态和可部署结果；
- 在修改单舰状态后重新读取结果，并判断是否需要更新成员 stats；
- 解释 `FleetMemberAPI` 与战斗侧 `ShipAPI` 的对象边界。

## 前置知识

- [C11-S2] 的 `FleetDataAPI` 成员归属、旗舰和集合顺序；
- [C11-S3] 的成员 stats 重算与装配替换边界；
- [C11-S5] 的 `ShipVariantAPI` 装配读取；
- Java 对象引用、一次性脚本和 `starsector.log` 的基本用法。

## 从舰队取得成员容器

`CampaignFleetAPI.getFleetData()` 返回当前舰队的成员容器。`FleetDataAPI.addFleetMember(...)` 接受一艘新成员，把它放入成员集合，设置成员的 `FleetDataAPI` 引用，并标记集合需要同步。成员加入之前只有船体和装配等自身数据，加入之后才有舰队指挥官、旗舰和汇总值等上下文。

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

`createFleetMember(...)` 的第二个参数是装配或船团成员的规格 ID。成员 ID 由游戏生成，通常通过 `getId()` 读取；页面示例不改写这个 ID，以免破坏存档或与已有成员冲突。

::: tip 成员归属由 FleetDataAPI 写入
调用 `addFleetMember(...)` 后再读取 `member.getFleetData()`，返回值才会指向当前容器。只把成员保存到 Java 字段而没有加入容器时，舰队界面、汇总值和战斗编队都不会把它当作舰队成员。
:::

## 记录创建前后的身份

创建和加入是两个不同动作。为了在验证时确认动作确实发生，先记录成员加入前的 `getFleetData()`，再记录加入后的容器、成员 ID、船体 ID、装配 ID 和舰队数量。

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

加入操作还会为没有船名的成员生成船名，为没有舰长的成员设置默认舰长，并把成员的旗舰标记清除。需要让成员成为旗舰时，应当通过 `FleetDataAPI.setFlagship(...)` 统一切换，而不是只调用成员自身的布尔 setter。

## 设置旗舰并保留原状态

`FleetDataAPI.setFlagship(member)` 会遍历当前集合，清除其他成员的旗舰状态，再把舰队指挥官设置给新旗舰。这个调用可能改变原旗舰的舰长，因此演示脚本在修改前保存原旗舰引用，恢复时先把原成员设回旗舰，再处理演示成员。

```java
FleetMemberAPI originalFlagship = data.getFleet().getFlagship();
FleetMemberAPI demoMember = GearInFleetMemberBuilder.createMember(
        fleet,
        "wolf_Assault",
        false);

data.setFlagship(demoMember);
LOG.info("temporary flagship=" + data.getFleet().getFlagship().getId());

if (originalFlagship != null && data.getMembersListCopy().contains(originalFlagship)) {
    data.setFlagship(originalFlagship);
}
```

`FleetMemberAPI.setFlagship(true)` 只改变成员自身标志，并且在成员已经属于舰队时可能同步设置舰长。集合级切换能同时维护其他成员的标志和舰队指挥官关系，所以恢复流程也应使用集合级入口。

::: warning 旗舰恢复要先确认成员仍在集合中
如果原旗舰已被移除或已转入另一个 `FleetDataAPI`，直接恢复会留下错误的舰长和旗舰状态。恢复前重新取得成员列表并确认对象仍属于当前舰队。
:::

## 读取船体、船员和装配

`FleetMemberAPI.getStatus()` 返回船体和模块状态，`getCrewComposition()` 返回当前船员组成，`getRepairTracker()` 返回 CR、维修和封存状态，`getVariant()` 返回成员当前装配。它们都属于同一个生涯成员，但含义不同，读取日志时应使用不同字段记录。

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

船员数量使用 `getCrewInt()` 便于日志阅读，参与部署判断的是 `getCrewFraction()`。船体完整度是 `0` 到 `1` 的比例；CR 由 `RepairTrackerAPI.getCR()` 返回，也会受到船员比例影响。维修系统的速率和最近的 CR 事件属于 `RepairTrackerAPI` 的进一步状态，可以在同一个对象上读取，但不应把它们当作船体损伤值。

## 修改船体并观察维修状态

`FleetMemberStatusAPI.applyHullFractionDamage(...)` 按船体最大值施加比例损伤，`setHullFraction(...)` 直接设置当前完整度，`repairFully()` 恢复船体和装甲状态。修改后应重新调用 getter，因为状态对象的结果才是游戏后续逻辑读取的值。

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

`needsRepairs()` 可能因为装甲、模块或其他受损部件仍然存在而保持 `true`。维修点和受损部件由游戏的维修流程逐步处理；本节只验证船体接口与最终可部署状态，不模拟完整的维修资源消耗。

::: tip 船体完整度不能代替维修完成度
`getHullFraction()` 只描述船体比例。验证维修结果时同时读取 `needsRepairs()` 和 `getRepairTracker().computeRepairednessFraction()`，才能发现装甲或部件仍然受损的情况。
:::

## 修改船员与战斗准备度

`CrewCompositionAPI.setCrew(...)` 修改成员携带的船员数量。`getCrewFraction()` 会把当前船员与成员需要的最低船员数比较，并在 `canBeDeployedForCombat()` 中参与判断。`RepairTrackerAPI.setCR(...)` 直接设置 CR，参数范围是 `0` 到 `1`；读取 `getCR()` 时仍会经过船员比例修正。

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

原版部署判断会先拒绝封存成员，再比较 CR 和船员阈值。当前 `settings.json` 中 `noDeployCRPercent` 为 `0`、`noDeployCrewPercent` 为 `5`，具体阈值仍由游戏设置和成员类型共同决定；示例不把这些设置硬编码到恢复逻辑中。

## 切换封存状态

`RepairTrackerAPI.setMothballed(true)` 把成员置于后勤封存状态。封存成员的 `canBeDeployedForCombat()` 返回 `false`，其 `getMemberStrength()` 也会按封存状态处理。恢复时先设置 `false`，再重新读取可部署结果。

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

设置封存状态会影响成员 stats 和维修行为。原版 `RepairTracker.setMothballed(...)` 在成员关联有效时会调用成员的 stats 更新入口，因此切换后不要继续使用未经重新读取的派生值。

## 让装配替换进入成员状态

成员装配由 `getVariant()` 读取，使用 `setVariant(variant, withRefit, withStatsUpdate)` 写回。第三个参数为 `true` 时会调用 `updateStats()`，让船体、船员最低需求、CR 上限和部署相关数据重新计算。装配对象来自 [C11-S5] 的读取与克隆流程；单舰页面只负责观察替换后的成员状态。

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

`withRefit` 为 `true` 时，原版实现可能依据新旧装配差异产生 CR 事件；需要保持 CR 不变的观察脚本可以传 `false`，再由页面明确记录这个选择。替换后重新读取成员的 stats 和 `getMemberStrength()`，不要保留旧的派生对象引用。

::: tip 装配替换与成员归属是两件事
`setVariant(...)` 只改变单个成员的装配。成员是否属于舰队仍由 `FleetDataAPI` 决定；替换后要在同一个舰队容器中观察成员数量、旗舰和汇总变化。
:::

## 观察战斗转换边界

生涯成员在 `CombatFleetManager` 进入战斗时被转换为战斗侧 `ShipAPI`。转换过程会使用成员装配、舰长、舰队指挥官、CR、船员和船体状态创建战斗对象；封存或不可部署成员会被锁定或排除。战斗中的 `ShipAPI` 只代表当前战斗实例，战斗结束后应重新从生涯舰队取得 `FleetMemberAPI`。

```java
boolean deployable = member.canBeDeployedForCombat();
String campaignId = member.getId();

LOG.info("combat boundary: campaignMember=" + campaignId
        + " deployable=" + deployable
        + " hull=" + member.getHullId()
        + " cr=" + member.getRepairTracker().getCR());
```

本节的 fixture 不主动启动战斗。人工验证时只确认舰队界面能看到成员、战斗部署列表采用当前状态，并在退出战斗后重新读取成员 ID、CR 和船体完整度。不要把战斗侧引用写入 persistent data 或静态字段。

## 完整最小例

下面两个类各自承担一个职责。`GearInFleetMemberBuilder` 提供可复用的创建入口并在脚本上下文中执行一次；`GearInFleetMemberStatus` 调用同一个入口，记录状态变化后把演示成员保留在玩家舰队中。

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
        if (fleet == null) {
            throw new IllegalArgumentException("fleet must not be null");
        }
        if (variantId == null || variantId.isEmpty()) {
            throw new IllegalArgumentException("variantId must not be empty");
        }

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
        float crAfter = tracker.getCR();
        float crewAfter = crew.getCrew();
        boolean deployableWhileMothballed = member.canBeDeployedForCombat();

        tracker.setMothballed(false);

        LOG.info("GearIn member status: id=" + member.getId()
                + " hull=" + hullBefore + "->" + hullAfter
                + " crew=" + crewBefore + "->" + crewAfter
                + " crewFraction=" + member.getCrewFraction()
                + " cr=" + crBefore + "->" + crAfter
                + " mothballed=false"
                + " deployableWhileMothballed=" + deployableWhileMothballed
                + " deployable=" + member.canBeDeployedForCombat()
                + " needsRepairs=" + member.needsRepairs()
                + " fleetMembers=" + data.getNumMembers());
    }
}
```

:::

两个脚本都以 `done` 防止同一实例重复创建成员。状态脚本没有移除演示成员，因此重新运行前应在日志和舰队界面确认上一轮成员是否仍然存在；正式 MOD 可以把成员 ID 写入本节专用的 memory，再在下一次执行时复用或清理。

## 验证方案

前置数据：玩家舰队已经存在，并且测试 MOD 的脚本注册入口能在生涯推进时执行一次。

1. 构建 MOD，确认 JAR 包含 `GearInFleetMemberBuilder.class` 和 `GearInFleetMemberStatus.class`。
2. 开始新档或载入测试存档，运行 `GearInFleetMemberBuilder`，在 `starsector.log` 搜索 `GearIn member builder`。
3. 打开舰队界面，按日志中的成员 ID、船体和装配名称定位新成员，确认它属于玩家舰队且未被错误设为旗舰。
4. 运行 `GearInFleetMemberStatus`，记录 `hull`、`crew`、`cr`、`mothballed`、`needsRepairs` 和 `deployable` 字段。
5. 在舰队界面确认演示成员仍然存在，检查船体损伤、CR、船员数量和封存图标；重新读取成员后核对日志值。
6. 在不修改脚本对象引用的情况下进入一次战斗，观察部署列表对不可部署状态的提示；退出战斗后重新取得同一成员 ID。

预期结果：

- 创建日志包含自动生成的成员 ID、`wolf_Assault` 装配和玩家舰队成员数量；
- 状态日志显示船体比例下降、船员比例改变、CR 变为设置值，并且封存期间 `deployableWhileMothballed=false`；
- 释放封存后，最终可部署结果只由当前 CR、船员、船体和其他状态决定；
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

- **装配替换后派生值没有变化**：使用 `setVariant(..., false, false)` 后继续使用旧 stats，或只修改了共享装配对象。
    - **解决方案**：对装配先 `clone()`，写回时传入 `withStatsUpdate=true`，随后调用 `updateStats()` 并重新读取成员派生值。

- **战斗结束后访问对象报错**：把战斗侧 `ShipAPI` 引用保存到跨战斗字段或存档数据。
    - **解决方案**：只保存生涯成员 ID；每次战斗结束后从玩家舰队的 `FleetDataAPI` 重新取得 `FleetMemberAPI`。

- **演示成员重复出现**：一次性脚本实例被重新注册或没有使用持久标记判断已经创建过成员。
    - **解决方案**：为每个脚本保留 `done` 状态，并在正式入口使用专用 memory 记录成员 ID；执行前检查已有成员再决定复用或清理。

## 总结

`FleetMemberAPI` 负责一艘舰船在生涯中的身份、装配、船体、船员、CR、维修和部署状态。`FleetDataAPI` 负责成员是否属于舰队、旗舰切换和集合级同步。创建结果只有在加入 `FleetDataAPI` 后才拥有完整舰队上下文。

读取单舰状态时分别记录船体比例、船员比例、CR、维修完成度和封存状态；修改任一状态后重新读取 `canBeDeployedForCombat()` 与派生 stats。装配替换使用隔离副本并触发 stats 更新，战斗侧 `ShipAPI` 只在当前战斗生命周期内有效。

## 建议参考项

- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员身份、装配、状态、部署和 stats 入口；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员加入、移除、旗舰和集合同步；
- `com.fs.starfarer.api.fleet.FleetMemberStatusAPI`：船体、装甲、部件损伤和维修状态；
- `com.fs.starfarer.api.fleet.CrewCompositionAPI`：船员和海军陆战队数量；
- `com.fs.starfarer.api.fleet.RepairTrackerAPI`：CR、维修事件、封存和恢复速率；
- `com.fs.starfarer.api.combat.ShipAPI`：战斗侧舰船对象及其生命周期；
- `D:/Starsector/starsector-core/data/config/settings.json`：部署阈值配置；
- `Starsector/starsector-core/starsector.log`：核对脚本输出与运行时错误。
