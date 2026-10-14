# 生成生涯舰队

本节生成两支巡逻队，并将它们放到来源市场两侧。基础舰队说明市场、预算、工厂与地点怎样共同完成生成，高级舰队再指定质量、旗舰和军官。实际成员与最终装备从返回对象取得，生成参数的完整字段留作按需配置的速查。

*本节预期阅读时间：\~30 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `FleetParamsV3` 在舰队生成过程中的职责；
- 为六类舰船预算选择合适的点数；
- 使用市场来源构造参数并调用 `FleetFactoryV3.createFleet(...)`；
- 检查空结果、开启 `transponder`，并把舰队投放到地点；
- 使用质量、旗舰、军官和随机源字段补充高级生成要求；
- 在读取最终装配前执行装配随机化；
- 比较请求 FP、实际 FP 和工厂记录的生成比例。

## 前置知识

- [C10-S1] 的星区与经济入口；
- [C10-S2] 的地点容器、实体位置与归属；
- [C7-S1] 的新档生成与经济建立后的回调；
- 能够构建 Java 17 MOD；
- 能够在 `starsector.log` 中按类名或日志前缀查找输出。

## 准备一次新档生成

本节的两支巡逻队都从来源市场生成，一支采用基础预算，另一支再指定质量、旗舰和军官。创建 `src/data/scripts/gearin/campaign/GearInFleetGenerator.java` 与 `GearInFleetGeneratorAdvanced.java`，在 `GearInModPlugin` 添加两类的 import。

生成需要已经建立的市场，将下面的局部方法加入入口：

```java
@Override
public void onNewGameAfterEconomyLoad() {
    SectorAPI sector = Global.getSector();
    sector.addTransientScript(new GearInFleetGenerator());
    sector.addTransientScript(new GearInFleetGeneratorAdvanced());
}
```

经济加载完成后登记两份一轮脚本，进入生涯时各生成一次。星系与初始势力关系继续由原来的新档回调处理，生成器在这里只使用已经存在的市场和地点。

## 从市场取得生成条件与投放位置

基础生成器优先取得 `jangala`。存档没有这个市场时，遍历当前经济中的市场，选择第一项具有开放市场、地点和主实体的来源。完整类的 `pickSourceMarket()` 承担这个选择，调用方使用如下局部片段：

```java
MarketAPI source = pickSourceMarket();
if (source == null) {
    LOG.warn("GearIn fleet generator: no source market");
    return;
}

LocationAPI location = source.getContainingLocation();
SectorEntityToken anchor = source.getPrimaryEntity();
if (location == null || anchor == null) {
    LOG.warn("GearIn fleet generator: source has no location or anchor");
    return;
}
```

来源市场提供势力和质量条件，`getContainingLocation()` 提供投放地点，`getPrimaryEntity()` 提供坐标锚点。它们在生成和投放中承担不同作用，所以这里分别取得，无法取得时记录原因并结束本次生成。

**市场来源并不会自动让舰队出现在市场附近。** 参数只记录来源条件，工厂创建舰队，地点最后接收它。下面顺着这几个动作完成一支基础巡逻队。

## 分配这支舰队的预算

`FleetParamsV3` 保存本次生成请求。市场构造器带入市场势力、超空间位置、当前生涯时间戳与来源质量，调用方再指定舰队类型、各类点数和质量修正：

```java
FleetParamsV3 params = new FleetParamsV3(
        source,
        FleetTypes.PATROL_MEDIUM,
        40f,
        12f,
        6f,
        0f,
        0f,
        0f,
        0f);
```

这里为战斗舰分配 40 FP，货船 12 FP，油船 6 FP，运输舰、客轮和辅助舰都为 0。`qualityMod` 也为 0，保留来源质量。后勤预算让本例同时生成战斗与物流成员，用于后面的集合观察。

### 市场构造参数速查表

| 构造参数 | 本例值 | 生成作用 |
| --- | ---: | --- |
| `source` | 来源市场 | 提供势力、位置、时间戳与质量来源 |
| `fleetType` | `PATROL_MEDIUM` | 说明舰队用途并参与生成配置 |
| `combatPts` | `40f` | 战斗舰目标 FP |
| `freighterPts` | `12f` | 货船目标 FP |
| `tankerPts` | `6f` | 油船目标 FP |
| `transportPts` | `0f` | 运输舰目标 FP |
| `linerPts` | `0f` | 客轮目标 FP |
| `utilityPts` | `0f` | 辅助舰目标 FP |
| `qualityMod` | `0f` | 在来源质量上附加的修正 |

**这些点数是各角色的目标 FP，而非成员数量。** 战斗舰形成主体，货船、油船与运输舰分别补充货运、燃料和人员能力，客轮与辅助角色再提供对应成员。候选舰船的 FP 是离散值，实际组合还受市场倍率、学说与生成限制影响。

`getTotalPts()` 将六项相加，本例请求为 58 FP。工厂返回后再读 `getFleetPoints()`，得到实际舰队规模，两个值无需相等。

## 创建舰队并投放到地图

将参数交给 `FleetFactoryV3.createFleet(...)`，得到 `CampaignFleetAPI`。后续要使用成员与旗舰，先处理没有对象和没有成员这两种结果：

```java
generatedFleet = FleetFactoryV3.createFleet(params);
if (generatedFleet == null || generatedFleet.isEmpty()) {
    LOG.warn("GearIn fleet generator: factory returned an empty fleet");
    return;
}
```

参数描述请求，返回对象保存生成结果。调用后继续修改 `params`，不会追溯改变这支舰队；要改变实际成员或装配，使用后续的舰队 API。

返回舰队尚未属于任何地点。本例开启 transponder，再以市场主实体为锚点，向右偏移 300 个地图单位投放：

```java
generatedFleet.setTransponderOn(true);
location.spawnFleet(
        anchor,
        300f,
        0f,
        generatedFleet);
```

`spawnFleet(...)` 将舰队加入地点，最终位置为锚点坐标加偏移。此后 `getContainingLocation()` 应对应 `location`，地点会继续管理这支地图舰队。

记录本次来源与实际结果：

```java
LOG.info("GearIn fleet generator: market=" + source.getId()
        + " name=" + generatedFleet.getName()
        + " members=" + generatedFleet.getFleetData().getNumMembers()
        + " fleetPoints=" + generatedFleet.getFleetPoints()
        + " location=" + location.getId()
        + " alive=" + generatedFleet.isAlive()
        + " transponder=" + generatedFleet.isTransponderOn());
```

日志中的市场与地点用于找到它，成员数和实际 FP 用于确认生成内容。后面的高级舰队放到市场左侧，两个实例也就可以分别观察。

### 验证方案

前置数据：测试存档保留原版核心世界，存在 `Jangala` 或其他具有开放市场、地点和主实体的市场。

1. 在 MOD 根目录执行 `gradle jar`，保存新档登记，重启游戏并创建测试存档。
2. 进入生涯推进一帧，搜索 `GearIn fleet generator`，按日志中的市场 ID 前往对应星系。
3. 在市场主实体右侧查找基础巡逻队，比较名称、成员数、实际 FP、地点、存活状态与 transponder。

预期结果：

- 基础巡逻队属于来源市场势力，位于市场主实体右侧 300 个地图单位处。
- 成员数大于 0，transponder 开启，日志中的 `alive=true`。
- 日志地点与来源市场所在地点一致，实际 FP 来自生成后的舰队。

## 为另一支舰队指定生成要求

高级生成器仍使用同一个市场，增加战斗与后勤预算，并在调用工厂前填写质量、旗舰、军官与数量条件：

```java
FleetParamsV3 params = new FleetParamsV3(
        source,
        FleetTypes.PATROL_LARGE,
        80f,
        20f,
        12f,
        0f,
        0f,
        0f,
        0f);
params.qualityOverride = 0.8f;
params.averageSMods = 1;
params.flagshipVariantId = "tempest_Attack";
params.officerLevelBonus = 1;
params.maxNumShips = 18;
params.random = new Random(104729L);
params.withOfficers = true;
```

`qualityOverride` 设为 0.8，直接选择本次质量；`averageSMods` 设为 1，提供装配随机化的平均内置船插目标。`flagshipVariantId` 使用已注册的 `tempest_Attack`，工厂在生成出旗舰后将对应装配应用到它。

军官等级额外加 1，`withOfficers=true` 保留人物生成，最终数量以 `maxNumShips=18` 参与常规裁剪。固定随机源使用 `104729L`，便于在相同数据环境中比较结果。

::: warning 固定种子仍依赖输入

市场状态、势力候选与学说都会参与抽选。种子相同而这些输入改变，结果也可能改变；复验时保留相同版本、存档状态与候选数据。

:::

## 取得真正完成的成员装配

舰队已经生成，不代表武器、战机和船插都已完成最后一次选择。工厂可以为它挂载 inflater，将装配随机化延迟到需要完整装备的时刻。

本例在统计高级成员之前，明确完成这一过程：

```java
boolean inflatedBefore = generatedFleet.isInflated();
boolean inflaterAttachedBefore = generatedFleet.getInflater() != null;
generatedFleet.inflateIfNeeded();

List<FleetMemberAPI> members =
        generatedFleet.getFleetData().getMembersListCopy();
```

**编者建议将依赖最终装备的判断放在 `inflateIfNeeded()` 之后。** 生成返回值已经有成员，装配选择却还可能等待处理；调用后从成员重新取得装配，才能使用这次生成的最终武器、来源和 D 插。

然后统计当前装配的 `REFIT` 来源与 D 插：

```java
int refitCount = 0;
int dModCount = 0;
for (FleetMemberAPI member : members) {
    if (member.getVariant().getSource() == VariantSource.REFIT) {
        refitCount++;
    }
    dModCount += DModManager.getNumDMods(member.getVariant());
}
```

`REFIT` 数量描述持有相应独立装配的成员，D 插总数描述这支舰队的结果。它们都是汇总观察项，不能据此认定每名成员经历相同处理。装配随机化也可能替换成员持有的装配引用，后续继续从 `member.getVariant()` 读取。

### 对照请求规模与实际规模

高级日志同时记录请求 FP、实际 FP、装配随机化前后状态与人物信息。指挥官和旗舰可能缺席，记录时保留空值，例如：

```java
float spawnFPMult = Misc.getSpawnFPMult(generatedFleet);
String commanderName = generatedFleet.getCommander() == null
        ? "none"
        : generatedFleet.getCommander().getNameString();
```

`FleetFactoryV3.KEY_SPAWN_FP_MULT` 对应 `$spawnFPMult`，工厂记录实际 FP 与请求 FP 的比例，`Misc.getSpawnFPMult(...)` 读取它。高级示例从 `params.getTotalPts()` 取得请求，从生成舰队取得实际规模，再投放到市场左侧 300 个地图单位。

## 完整最小例

两类保存到前述源码目录，保留经济加载后的登记。来源选择、参数准备、生成与投放在各自一轮脚本中完成。

::: details 完整成品：GearInFleetGenerator

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/** 生成一支常规巡逻舰队并投放到来源市场附近。 */
public class GearInFleetGenerator implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFleetGenerator.class);

    private boolean done;
    private CampaignFleetAPI generatedFleet;

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

        MarketAPI source = pickSourceMarket();
        if (source == null) {
            LOG.warn("GearIn fleet generator: no source market");
            return;
        }

        LocationAPI location = source.getContainingLocation();
        SectorEntityToken anchor = source.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.warn("GearIn fleet generator: source has no location or anchor");
            return;
        }

        FleetParamsV3 params = new FleetParamsV3(
                source,
                FleetTypes.PATROL_MEDIUM,
                40f,
                12f,
                6f,
                0f,
                0f,
                0f,
                0f);

        generatedFleet = FleetFactoryV3.createFleet(params);
        if (generatedFleet == null || generatedFleet.isEmpty()) {
            LOG.warn("GearIn fleet generator: factory returned an empty fleet");
            return;
        }

        generatedFleet.setTransponderOn(true);
        location.spawnFleet(
                anchor,
                300f,
                0f,
                generatedFleet);

        LOG.info("GearIn fleet generator: market=" + source.getId()
                + " name=" + generatedFleet.getName()
                + " members=" + generatedFleet.getFleetData().getNumMembers()
                + " fleetPoints=" + generatedFleet.getFleetPoints()
                + " location=" + location.getId()
                + " alive=" + generatedFleet.isAlive()
                + " transponder=" + generatedFleet.isTransponderOn());
    }

    private MarketAPI pickSourceMarket() {
        MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
        if (jangala != null) {
            return jangala;
        }

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasSubmarket(Submarkets.SUBMARKET_OPEN)
                    && market.getContainingLocation() != null
                    && market.getPrimaryEntity() != null) {
                return market;
            }
        }
        return null;
    }
}

```

:::

::: details 完整成品：GearInFleetGeneratorAdvanced

```java
package data.scripts.gearin.campaign;

import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

/** 生成并投放巡逻舰队，记录高级参数与装配随机化结果。 */
public class GearInFleetGeneratorAdvanced implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFleetGeneratorAdvanced.class);

    private boolean done;
    private CampaignFleetAPI generatedFleet;

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

        MarketAPI source = pickSourceMarket();
        if (source == null) {
            LOG.warn("GearIn advanced fleet: no source market");
            return;
        }

        LocationAPI location = source.getContainingLocation();
        SectorEntityToken anchor = source.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.warn("GearIn advanced fleet: source has no location or anchor");
            return;
        }

        FleetParamsV3 params = new FleetParamsV3(
                source,
                FleetTypes.PATROL_LARGE,
                80f,
                20f,
                12f,
                0f,
                0f,
                0f,
                0f);
        params.qualityOverride = 0.8f;
        params.averageSMods = 1;
        params.flagshipVariantId = "tempest_Attack";
        params.officerLevelBonus = 1;
        params.maxNumShips = 18;
        params.random = new Random(104729L);
        params.withOfficers = true;

        generatedFleet = FleetFactoryV3.createFleet(params);
        if (generatedFleet == null || generatedFleet.isEmpty()) {
            LOG.warn("GearIn advanced fleet: factory returned an empty fleet");
            return;
        }

        boolean inflatedBefore = generatedFleet.isInflated();
        boolean inflaterAttachedBefore = generatedFleet.getInflater() != null;
        generatedFleet.inflateIfNeeded();

        List<FleetMemberAPI> members = generatedFleet.getFleetData().getMembersListCopy();
        int refitCount = 0;
        int dModCount = 0;
        for (FleetMemberAPI member : members) {
            if (member.getVariant().getSource() == VariantSource.REFIT) {
                refitCount++;
            }
            dModCount += DModManager.getNumDMods(member.getVariant());
        }

        float spawnFPMult = Misc.getSpawnFPMult(generatedFleet);
        String commanderName = generatedFleet.getCommander() == null
                ? "none"
                : generatedFleet.getCommander().getNameString();

        generatedFleet.setTransponderOn(true);
        location.spawnFleet(
                anchor,
                -300f,
                0f,
                generatedFleet);

        LOG.info("GearIn advanced fleet: market=" + source.getId()
                + " requestedPoints=" + params.getTotalPts()
                + " actualPoints=" + generatedFleet.getFleetPoints()
                + " spawnFPMult=" + spawnFPMult
                + " inflatedBefore=" + inflatedBefore
                + " inflatedAfter=" + generatedFleet.isInflated()
                + " inflaterBefore=" + inflaterAttachedBefore
                + " inflaterAfter=" + (generatedFleet.getInflater() != null)
                + " members=" + members.size()
                + " refitMembers=" + refitCount
                + " dMods=" + dModCount
                + " commander=" + commanderName
                + " flagship=" + (generatedFleet.getFlagship() == null
                        ? "none"
                        : generatedFleet.getFlagship().getHullId())
                + " location=" + location.getId()
                + " alive=" + generatedFleet.isAlive());
    }

    private MarketAPI pickSourceMarket() {
        MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
        if (jangala != null) {
            return jangala;
        }

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasSubmarket(Submarkets.SUBMARKET_OPEN)
                    && market.getContainingLocation() != null
                    && market.getPrimaryEntity() != null) {
                return market;
            }
        }
        return null;
    }
}

```

:::

## 观察高级舰队

### 验证方案

前置数据：前面的新档已执行两份生成脚本，基础巡逻队验证完成。

1. 在市场主实体左侧查找高级舰队，搜索 `GearIn advanced fleet`。
2. 比较 `requestedPoints`、`actualPoints` 与 `spawnFPMult`，确认比例来自工厂返回结果。
3. 比较 `inflatedBefore`、`inflatedAfter`、inflater 状态，以及 `members`、`refitMembers` 和 `dMods`。
4. 读取日志中的指挥官与旗舰，查看成员装配，将装备与来源对应到同一高级舰队。

预期结果：

- 高级舰队位于市场主实体左侧 300 个地图单位，两支示例舰队同时保留在地图上。
- `inflatedAfter=true`，成员装配与 D 插统计在装配随机化后取得。
- 日志保留请求 FP、实际 FP、比例、指挥官和旗舰，能够对应生成结果。

## 根据需要配置其他生成字段

基础与高级示例已经完成，其他要求从下列字段选择。`FleetParamsV3` 公开声明 47 个字段，其中 44 个用于调用方配置，另 3 个 transient 字段供工厂传递状态。

默认值采用字段初始化。市场构造器另外写入来源、位置、势力、时间戳与质量；如果替换来源，`setSource(source, true)` 会同时重算质量，直接给 `source` 赋值则由调用方安排。

无参构造器适合逐字段组装，超空间坐标构造器适合没有市场来源的任务，同时接受市场、坐标与显式势力的构造器适合来源与生成势力不同的任务。调用工厂前，生成势力、舰队类型与预算要对应当前要求。

### 来源、势力与质量速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `source` | `MarketAPI` | `null` | 参数准备 | 来源市场；直接赋值后要自行决定是否重算质量 |
| `locInHyper` | `Vector2f` | `null` | 参数准备 | 超空间位置；无市场来源时用于建立附近环境 |
| `quality` | `float` | `0f` | 参数准备 | 当前来源质量；市场构造器会更新 |
| `factionId` | `String` | `null` | 成员抽选 | 生成势力 ID；工厂需要取得对应势力 |
| `fleetType` | `String` | `null` | 舰队初始化 | 舰队用途，如巡逻、贸易或袭击 |
| `factionOverride` | `FactionAPI` | `null` | 成员抽选 | 临时替换势力对象，可携带不同 学说 或候选集合 |
| `qualityMod` | `float` | `0f` | 质量计算 | 在来源质量上附加修正 |
| `qualityOverride` | `Float` | `null` | 质量计算 | 直接指定质量；非空时作为明确覆盖 |
| `averageSMods` | `Integer` | `null` | 装配随机化 | 指定平均内置船插数量 |
| `timestamp` | `Long` | `null` | 内容可用性判断 | 市场构造器写入当前时间戳；影响按时间判断的已知内容 |
| `allWeapons` | `Boolean` | `null` | 装配随机化 | 放宽武器选择范围；会改变最终装配候选 |

### 六类点数速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `combatPts` | `float` | `0f` | 成员抽选 | 战斗舰目标 FP |
| `freighterPts` | `float` | `0f` | 成员抽选 | 货船目标 FP |
| `tankerPts` | `float` | `0f` | 成员抽选 | 油船目标 FP |
| `transportPts` | `float` | `0f` | 成员抽选 | 运输舰目标 FP |
| `linerPts` | `float` | `0f` | 成员抽选 | 客轮目标 FP |
| `utilityPts` | `float` | `0f` | 成员抽选 | 辅助舰目标 FP |

### 舰体限制、数量和裁剪速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `maxShipSize` | `int` | `1000` | 成员抽选 | 最大舰体级别；常用级别为 0 至 4 |
| `minShipSize` | `int` | `0` | 战斗舰抽选 | 战斗舰最小级别；剩余 FP 仍可能加入更小舰船 |
| `ignoreMarketFleetSizeMult` | `Boolean` | `null` | 预算调整 | 跳过市场舰队规模倍率 |
| `onlyApplyFleetSizeToCombatShips` | `Boolean` | `null` | 预算调整 | 只对战斗舰预算应用规模倍率 |
| `doNotPrune` | `Boolean` | `null` | 结果裁剪 | 跳过常规裁剪，结果可能更大 |
| `doNotAddShipsBeforePruning` | `Boolean` | `null` | 结果裁剪 | 裁剪前不补充成员 |
| `maxNumShips` | `Integer` | `null` | 结果裁剪 | 限制本次生成的最终舰船数量 |
| `onlyRetainFlagship` | `Boolean` | `null` | 结果裁剪 | 最终只保留旗舰 |
| `treatCombatFreighterSettingAsFraction` | `Boolean` | `null` | 角色预算 | 按比例解释 学说 的战斗货船设置 |
| `addShips` | `List<String>` | `null` | 成员抽选 | 优先加入指定装配 ID，再消费其他预算 |

### 抽选模式与学说速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `modeOverride` | `ShipPickMode` | `null` | 成员抽选 | 覆盖势力提供的舰船抽选模式 |
| `forceAllowPhaseShipsEtc` | `Boolean` | `null` | 候选过滤 | 放宽相位舰等特殊候选限制 |
| `doctrineOverride` | `FactionDoctrineAPI` | `null` | 预算与抽选 | 替换势力 学说；影响舰体比例、军官与候选 |
| `random` | `Random` | `null` | 整个生成过程 | 提供随机源；固定种子便于在同一数据环境中复验 |

### 军官、指挥官与 AI 核心速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `withOfficers` | `boolean` | `true` | 人物生成 | 控制是否生成军官 |
| `officerLevelBonus` | `int` | `0` | 人物生成 | 增加生成军官的等级 |
| `officerNumberBonus` | `int` | `0` | 人物生成 | 增加生成军官的数量 |
| `maxOfficersToAdd` | `Integer` | `null` | 人物生成 | 限制本次新增军官数 |
| `officerNumberMult` | `float` | `1f` | 人物生成 | 缩放计算出的军官数量 |
| `officerLevelLimit` | `int` | `0` | 人物生成 | 覆盖军官等级上限；0 表示不覆盖 |
| `commanderLevelLimit` | `int` | `0` | 人物生成 | 限制新指挥官等级；0 使用生成逻辑 |
| `commander` | `PersonAPI` | `null` | 人物生成 | 直接提供现有指挥官 |
| `noCommanderSkills` | `Boolean` | `null` | 人物生成 | 跳过指挥官技能生成 |
| `aiCores` | `OfficerQuality` | `null` | 人物生成 | 为适用势力指定 AI 核心质量 |
| `doNotIntegrateAICores` | `boolean` | `false` | 人物生成 | 保持 AI 核心未整合状态 |

### 旗舰与显式装配速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `flagshipVariantId` | `String` | `null` | 旗舰配置 | 用注册表装配 ID 指定旗舰；ID 必须存在 |
| `flagshipVariant` | `ShipVariantAPI` | `null` | 旗舰配置 | 直接提供旗舰装配对象 |
| `onlyRetainFlagship` | `Boolean` | `null` | 结果裁剪 | 只保留旗舰；与数量策略共同核对 |
| `addShips` | `List<String>` | `null` | 成员抽选 | 在常规抽选前加入指定装配 |

`flagshipVariantId` 与 `flagshipVariant` 用于配置已生成的旗舰。指定 ID 时，装配必须存在，返回舰队也需要有可用旗舰；它们并不直接在候选预算中购买这艘舰船。`onlyRetainFlagship` 与 `addShips` 在数量表中已经计入，重复列出用于说明相关生成要求。

### 工厂传递状态速查表

| 字段 | 类型 | 默认值 | 内部职责 |
| --- | --- | --- | --- |
| `mode` | `transient ShipPickMode` | `null` | 保存工厂当前采用的抽选模式 |
| `banPhaseShipsEtc` | `transient boolean` | `false` | 保存特殊舰船过滤状态 |
| `blockFallback` | `transient Boolean` | `null` | 保存候选回退是否被阻止 |

`mode`、`banPhaseShipsEtc` 与 `blockFallback` 在工厂方法之间传递状态。调用方通过前面的配置字段表达要求，保留它们的内部用途。

### 字段数量速查表

| 分组 | 字段数 |
| --- | ---: |
| 来源、势力与质量 | 11 |
| 六类点数 | 6 |
| 舰体限制、数量和裁剪 | 10 |
| 抽选模式与 学说 | 4 |
| 军官、指挥官与 AI 核心 | 11 |
| 旗舰专属字段 | 2 |
| **调用方字段合计** | **44** |
| 工厂内部传递字段 | 3 |
| **公开字段总计** | **47** |

旗舰专属字段只计 `flagshipVariantId` 与 `flagshipVariant`，重复展示的 `onlyRetainFlagship` 与 `addShips` 已计入数量组。各表合起来覆盖调用方字段与工厂传递字段。

## 故障分析

- **日志显示来源市场不可用**：测试存档中没有 `Jangala`，且经济中没有同时具备开放市场、地点和主实体的市场。
    - **解决方案**：改用当前存档中存在的市场，并在构造参数前分别检查 `getContainingLocation()` 与 `getPrimaryEntity()`。

- **工厂返回 `null` 或空舰队**：势力、舰队类型、预算与候选内容没有形成有效结果。
    - **解决方案**：先使用存在的势力、标准 `FleetTypes` 常量和正数战斗预算，确认基础生成成立后再增加限制。

- **实际 FP 与请求 FP 不相等**：角色候选、离散舰船 FP、市场倍率、数量限制或裁剪改变了结果。
    - **解决方案**：同时记录 `getTotalPts()`、`getFleetPoints()` 与 `$spawnFPMult`，按参数表逐项检查规模相关字段。

- **指定旗舰没有出现**：装配 ID 无效，或生成结果没有可供替换装配的旗舰。
    - **解决方案**：核对 `flagshipVariantId` 与注册装配，检查返回舰队和当前旗舰，再读取应用后的装配。

- **装配随机化后的统计仍为空**：统计发生在 `inflateIfNeeded()` 之前，或代码继续使用调用前保存的成员与装配引用。
    - **解决方案**：先执行装配随机化，再从 `FleetDataAPI` 重新取得成员，并逐个重新读取装配。

- **固定种子仍产生不同结果**：市场状态、势力候选、学说 或游戏数据发生变化。
    - **解决方案**：在相同版本和相同存档状态下比较固定种子结果，并把候选数据变化作为输入变化记录。

- **地图中找不到舰队**：工厂只创建了对象，舰队尚未投放到地点。
    - **解决方案**：调用 `location.spawnFleet(...)`，随后核对 `getContainingLocation()` 和舰队坐标。

- **`$spawnFPMult` 缺失或结果异常**：读取发生在工厂生成前，或使用了错误的 Memory 值类型。
    - **解决方案**：只在 `createFleet(...)` 成功后通过 `Misc.getSpawnFPMult(fleet)` 读取浮点结果。

## 总结

两支巡逻队已经从市场条件与角色预算生成，并由地点投放到地图。高级参数参与质量、人物与旗舰配置，装配随机化完成后再提供最终装备。

请求 FP 说明生成要求，实际 FP 与成员说明结果。参数不会代替返回舰队，工厂也不会代替地点投放，后续修改继续从已经生成的对象展开。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`：舰队生成参数与字段定义；
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：舰队工厂和生成规模记录；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：生成后的舰队状态；
- `com.fs.starfarer.api.campaign.LocationAPI`：舰队投放入口；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：装配随机化后的成员读取；
- `com.fs.starfarer.api.loading.VariantSource`：装配来源枚举；
- `Starsector/starsector-core/starsector.log`：地图投放与生成结果日志。
