# 生成生涯舰队

世界与势力已经可以读取，现在让一支舰队出现在地图上。本节从市场取得来源，用 `FleetParamsV3` 分配战斗与后勤预算，再交给 `FleetFactoryV3` 挑选成员并投放。先生成普通巡逻队，再沿同一流程指定质量、旗舰与军官；两支舰队分别出现在市场两侧，日志也各自记录请求和实际结果。

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

## 接上新档入口，再配置生成参数

创建 `src/data/scripts/gearin/campaign/GearInFleetGenerator.java` 和 `GearInFleetGeneratorAdvanced.java`。在 [C7-S1] 的 `GearInModPlugin` 添加两个类的 `import`，并加入以下局部方法：

```java
@Override
public void onNewGameAfterEconomyLoad() {
    SectorAPI sector = Global.getSector();
    sector.addTransientScript(new GearInFleetGenerator());
    sector.addTransientScript(new GearInFleetGeneratorAdvanced());
}
```

此时市场已经建立，进入生涯后两个脚本各生成一次并结束。既有星系与势力初始化继续保留在原回调，当前只增加这两次生成。

**`FleetParamsV3` 描述本次请求，工厂返回实际舰队。**参数保存生成条件。势力 ID 和舰队类型决定工厂从哪里挑选内容，各类点数决定希望为不同角色分配多少 FP，来源市场和质量字段决定候选舰船及其最终装配质量。

`FleetFactoryV3.createFleet(params)` 才会消费这些条件并返回 `CampaignFleetAPI`。参数对象仍然可以在调用后用于记录请求规模，但继续修改它不会追溯改变已经生成的舰队。

### 从市场带入势力与质量

市场构造器适合普通生涯舰队。市场提供势力、超空间位置、生成时间戳和质量来源，调用方只需要选择舰队类型、六类点数及质量修正：

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

这支中型巡逻队把 40 FP 分给战斗舰、12 FP 分给货船、6 FP 分给油船，其余角色为 0。最后的 `qualityMod` 为 0，保留市场来源质量。编者在这里留了后勤预算，让地图上的巡逻队确实带有油船与货船；只给战斗预算，则会形成另一种编成。

### 常用构造参数速查表

下表按市场构造器的参数顺序列出基础示例输入。

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

市场构造器会把市场的超空间坐标写入 `locInHyper`，把市场势力写入 `factionId`，保存当前生涯时间戳，并调用 `updateQualityAndProducerFromSourceMarket()` 更新 `quality`。如果先构造参数、再替换来源，使用 `setSource(source, true)` 才会同时重算质量。

::: info 其他构造入口
无参构造器适合逐字段组装；超空间坐标构造器适合没有来源市场的生成；同时接受市场、坐标和显式势力的构造器适合来源市场与生成势力不同的任务。无论选择哪一个入口，调用工厂前都要确认 `factionId`、`fleetType` 和点数已经有效。
:::

### 理解六类点数

**六类点数是目标 FP。** 每一类交给对应角色挑选过程，成员数量与最后消费的点数取决于可用舰船。

- `combatPts` 形成战斗主体；
- `freighterPts` 增加货运能力；
- `tankerPts` 增加燃料能力；
- `transportPts` 增加人员运输能力；
- `linerPts` 选择客轮角色；
- `utilityPts` 选择工业与支援角色。

`getTotalPts()` 只把这六项相加。基础示例的请求总量是 58 FP；实际舰队 FP 要在工厂返回舰队后通过 `getFleetPoints()` 读取。

## 生成一支基础舰队

先完成基础舰队的生成与投放，再展开高级参数。来源市场提供势力与质量，主实体提供投放坐标，地点负责接收返回舰队。

### 取得来源市场

示例优先使用 `jangala`。测试存档没有该市场时，遍历经济中的市场，并选择具有开放市场、地点和主实体的第一项：

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

这里分别读取市场、地点与主实体。市场可能来自不同存档配置，当前选择器检查这组来源；日志写明实际市场 ID，后续前往同一个星系观察。

::: tip 市场不会自动投放舰队
市场构造器只把来源信息写入参数。工厂返回的舰队仍需通过地点的 `spawnFleet(...)` 加入生涯地图。
:::

### 调用工厂并检查结果

把参数交给工厂后，首先检查返回引用，其次检查舰队是否包含成员：

```java
generatedFleet = FleetFactoryV3.createFleet(params);
if (generatedFleet == null || generatedFleet.isEmpty()) {
    LOG.warn("GearIn fleet generator: factory returned an empty fleet");
    return;
}
```

`null` 表示生成过程没有得到舰队对象；`isEmpty()` 表示对象存在，但成员列表为空。后续的旗舰、成员、FP 和投放操作都应放在这两项检查之后。

返回的 `generatedFleet` 用于本次配置和投放，投放后由地点继续管理这支舰队。本例沿引用完成操作，后续也可以从该地点的舰队集合重新取得对象。

### 开启 `transponder` 并投放舰队

工厂创建的对象尚未属于任何地点。示例先开启 `transponder`，再以市场主实体为锚点，把舰队投放到锚点右侧 300 个地图单位处：

```java
generatedFleet.setTransponderOn(true);
location.spawnFleet(
        anchor,
        300f,
        0f,
        generatedFleet);
```

`spawnFleet(anchor, xOffset, yOffset, fleet)` 会把舰队加入地点，并把最终坐标设置为锚点坐标加偏移。投放完成后，`generatedFleet.getContainingLocation()` 应与 `location` 指向同一个地点。

最后记录名称、地点、成员数和实际 FP：

```java
LOG.info("GearIn fleet generator: market=" + source.getId()
        + " name=" + generatedFleet.getName()
        + " members=" + generatedFleet.getFleetData().getNumMembers()
        + " fleetPoints=" + generatedFleet.getFleetPoints()
        + " location=" + location.getId()
        + " alive=" + generatedFleet.isAlive()
        + " transponder=" + generatedFleet.isTransponderOn());
```

先比较成员数、实际 FP 和所属地点，再查看高级生成的旗舰、军官与装配。地图位置和对象数量一致，才能把参数差异对应到正确舰队。

### 验证方案

前置数据：测试存档保留原版核心世界，存在 `Jangala` 或其他具有开放市场、地点和主实体的市场。

1. 使用 `Gradle` `Wrapper` 构建 MOD，并把生成的 JAR 放入测试 MOD。
2. 新建生涯测试存档，进入后推进一帧，触发本节登记的两次生成。
3. 打开来源市场所在星系的地图。
4. 在市场右侧寻找基础生成器创建的舰队。
5. 打开 `starsector.log`，搜索 `GearIn fleet generator`。
6. 核对日志中的市场 ID、舰队名称、成员数、实际 FP、地点 ID、存活状态和 `transponder` 状态。

预期结果：

- 地图中出现一支属于来源市场势力的巡逻舰队；
- 舰队位于市场主实体右侧；
- `transponder` 已开启；
- 成员数大于 0；
- 日志中的 `alive` 为 `true`；
- 日志中的地点 ID 与来源市场所在地点一致。

## 查阅 `FleetParamsV3` 的完整字段

当前技术基线中的 `FleetParamsV3` 公开声明 47 个字段。其中 44 个字段可由调用方配置，`mode`、`banPhaseShipsEtc` 和 `blockFallback` 这 3 个 `transient` 字段只用于工厂内部传递状态。

下面按实际职责查字段。默认值采用字段初始化；市场构造器另行写入来源、坐标、势力、时间戳与质量。完成基础生成后，再按需要查质量、数量或人物字段。

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

::: warning 点数只表示目标预算
候选装配的 FP 是离散值，市场规模倍率、角色候选、舰体限制、数量限制和裁剪都会让实际 FP 偏离请求值。使用 `params.getTotalPts()` 读取请求总量，使用 `fleet.getFleetPoints()` 读取实际结果。
:::

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

### 抽选模式与 学说 速查表

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

### 旗舰和显式装配速查表

| 字段 | 类型 | 默认值 | 生成阶段 | 用途与边界 |
| --- | --- | --- | --- | --- |
| `flagshipVariantId` | `String` | `null` | 旗舰配置 | 用注册表装配 ID 指定旗舰；ID 必须存在 |
| `flagshipVariant` | `ShipVariantAPI` | `null` | 旗舰配置 | 直接提供旗舰装配对象 |
| `onlyRetainFlagship` | `Boolean` | `null` | 结果裁剪 | 只保留旗舰；与数量策略共同核对 |
| `addShips` | `List<String>` | `null` | 成员抽选 | 在常规抽选前加入指定装配 |

`onlyRetainFlagship` 与 `addShips` 已在数量表中计入 44 个字段总数，这里重复列出是为了完整说明旗舰流程，不重复计数。指定旗舰仍需要战斗预算能够容纳对应舰船；无效 ID、空舰队或预算不足都会使预期旗舰无法成立。

### 工厂内部传递字段速查表

| 字段 | 类型 | 默认值 | 内部职责 |
| --- | --- | --- | --- |
| `mode` | `transient ShipPickMode` | `null` | 保存工厂当前采用的抽选模式 |
| `banPhaseShipsEtc` | `transient boolean` | `false` | 保存特殊舰船过滤状态 |
| `blockFallback` | `transient Boolean` | `null` | 保存候选回退是否被阻止 |

这 3 个字段由 `FleetFactoryV3` 在内部方法之间传递状态。调用方通过公开配置字段表达生成要求，不直接写入它们。

### 字段数量速查表

44 个调用方字段按首次出现的分组统计如下：

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

旗舰分组只计 `flagshipVariantId` 与 `flagshipVariant`。表中为理解流程而重复展示的 `onlyRetainFlagship` 和 `addShips` 已计入舰体限制、数量和裁剪分组。

## 补充高级生成参数

高级生成仍从同一个市场出发。这次增加预算，在调用工厂前设置质量、旗舰与军官；返回后先完成装配随机化，再读取成员的实际装备与来源。

### 覆盖质量、旗舰和军官

高级示例使用更高预算，并在调用工厂前设置代表性高级字段：

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

`qualityOverride` 让本次生成使用明确质量，`averageSMods` 为装配随机化提供平均内置船插目标，`flagshipVariantId` 请求指定旗舰装配。军官等级加成与 `withOfficers` 共同影响人物生成，`maxNumShips` 对最终数量施加上限。

固定种子 `104729L` 用来比较同一数据环境下的结果。**随机种子与候选数据共同决定抽选。** 市场、势力军备或学说变化后，输入已经变化，结果也可能随之变化；复验时把这组条件一起保留。

### 执行装配随机化

工厂可能为舰队挂载 `inflater`，把最终武器、战机和船插的选择延迟到需要完整装配的时刻。高级示例先记录状态，再显式调用 `inflateIfNeeded()`：

```java
boolean inflatedBefore = generatedFleet.isInflated();
boolean inflaterAttachedBefore = generatedFleet.getInflater() != null;
generatedFleet.inflateIfNeeded();

List<FleetMemberAPI> members =
        generatedFleet.getFleetData().getMembersListCopy();
```

**先完成装配随机化，再从成员取得当前装配。** 读取来源、D 插或武器时采用调用后的对象，日志才对应最终结果。

随后遍历成员，统计 `VariantSource.REFIT` 和 D 插数量：

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

`REFIT` 数量用于观察有多少成员持有装配随机化后的独立装配；D 插总数用于观察质量覆盖产生的舰队级结果。这两个数值都是结果观察项，不能单独证明每个成员经历了相同处理。

::: tip 装配随机化完成后重新取得对象
装配随机化可能替换成员持有的装配对象。后续判断必须从舰队成员重新读取 `getVariant()`，避免继续使用调用前保存的装配引用。
:::

### 观察高级生成结果

高级日志同时记录请求 FP、实际 FP、装配随机化前后状态、成员数、`REFIT` 来源、D 插、指挥官和旗舰。读取指挥官前先处理 `null`，读取旗舰时也保留空值分支：

```java
float spawnFPMult = Misc.getSpawnFPMult(generatedFleet);
String commanderName = generatedFleet.getCommander() == null
        ? "none"
        : generatedFleet.getCommander().getNameString();
```

::: tip $spawnFPMult
`FleetFactoryV3.KEY_SPAWN_FP_MULT` 的键值是 `$spawnFPMult`。工厂用它记录实际舰队 FP 与请求 FP 的比例，`Misc.getSpawnFPMult(fleet)` 可用于分析生成规模；本节不把该键扩展为舰队 Memory 索引。
:::

高级舰队被投放到市场主实体左侧 300 个地图单位处，与基础舰队分开。日志中的 `requestedPoints` 来自 `params.getTotalPts()`，`actualPoints` 来自生成后的舰队，`spawnFPMult` 则是工厂保存的比例结果。

## 完整最小例

两个类保存到前述路径，保留经济建立后的登记。基础与高级舰队分别生成、投放和记录。

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

前置数据：基础生成器验证已完成，同一测试进程与测试存档继续保留。

1. 在同一测试进程中确认第二个脚本已经执行。
2. 在市场左侧寻找高级生成器创建的舰队。
3. 搜索 `GearIn advanced fleet` 日志。
4. 比较 `requestedPoints` 与 `actualPoints`。
5. 核对 `spawnFPMult` 是否与两者的比例关系一致。
6. 查看 `inflatedBefore`、`inflatedAfter` 和 `inflater` 状态。
7. 核对 `members`、`refitMembers`、`dMods`、`commander` 和 `flagship`。
8. 在舰队界面查看旗舰和成员装配，确认日志描述与界面结果一致。

预期结果：

- 高级舰队位于市场主实体左侧；
- `inflatedAfter` 为 `true`；
- 日志包含请求 FP、实际 FP 与 `spawnFPMult`；
- 指挥官和旗舰字段能够定位生成结果；
- `REFIT` 来源与 D 插统计来自装配随机化后的成员；
- 两支示例舰队同时保留在地图上。

## 故障分析

- **日志显示来源市场不可用**：测试存档中没有 `Jangala`，且经济中没有同时具备开放市场、地点和主实体的市场。
    - **解决方案**：改用当前存档中存在的市场，并在构造参数前分别检查 `getContainingLocation()` 与 `getPrimaryEntity()`。

- **工厂返回 `null` 或空舰队**：势力、舰队类型、预算与候选内容没有形成有效结果。
    - **解决方案**：先使用存在的势力、标准 `FleetTypes` 常量和正数战斗预算，确认基础生成成立后再增加限制。

- **实际 FP 与请求 FP 不相等**：角色候选、离散舰船 FP、市场倍率、数量限制或裁剪改变了结果。
    - **解决方案**：同时记录 `getTotalPts()`、`getFleetPoints()` 与 `$spawnFPMult`，按参数表逐项检查规模相关字段。

- **指定旗舰没有出现**：装配 ID 无效、预算不足以容纳该舰船，或生成结果为空。
    - **解决方案**：核对 `flagshipVariantId`，提高战斗预算，并在读取旗舰前检查生成结果。

- **装配随机化后的统计仍为空**：统计发生在 `inflateIfNeeded()` 之前，或代码继续使用调用前保存的成员与装配引用。
    - **解决方案**：先执行装配随机化，再从 `FleetDataAPI` 重新取得成员，并逐个重新读取装配。

- **固定种子仍产生不同结果**：市场状态、势力候选、学说 或游戏数据发生变化。
    - **解决方案**：在相同版本和相同存档状态下比较固定种子结果，并把候选数据变化作为输入变化记录。

- **地图中找不到舰队**：工厂只创建了对象，舰队尚未投放到地点。
    - **解决方案**：调用 `location.spawnFleet(...)`，随后核对 `getContainingLocation()` 和舰队坐标。

- **`$spawnFPMult` 缺失或结果异常**：读取发生在工厂生成前，或使用了错误的 Memory 值类型。
    - **解决方案**：只在 `createFleet(...)` 成功后通过 `Misc.getSpawnFPMult(fleet)` 读取浮点结果。

## 总结

两支舰队沿同一生成过程取得来源、构造请求并投放，高级示例再指定质量、旗舰、军官和随机源，装配随机化后读取当前成员结果。

FP 是目标预算，实际舰队由候选与限制共同形成。编者建议把请求规模和地图上的实际舰队一起看；一串参数能够解释设计，成员与装配才说明最后生成了什么。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3`：舰队生成参数与字段定义；
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：舰队工厂和生成规模记录；
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：生成后的舰队状态；
- `com.fs.starfarer.api.campaign.LocationAPI`：舰队投放入口；
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：装配随机化后的成员读取；
- `com.fs.starfarer.api.loading.VariantSource`：装配来源枚举；
- `Starsector/starsector-core/starsector.log`：地图投放与生成结果日志。
