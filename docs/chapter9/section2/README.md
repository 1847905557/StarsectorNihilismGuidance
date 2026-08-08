# 用代码建立恒星系

本节用代码建立一个可从超空间进入的恒星系，并在其中放置、查询实体和读取访问状态；该地点容器作为舰队与市场功能的运行空间。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~50 分钟。*

## 学习目标

完成后，您将能够：

- 用 `SectorAPI.createStarSystem` 与 `StarSystemAPI.initStar` 建立恒星系，区分 4/6/7 参数变体，并验证 `autogenerateHyperspaceJumpPoints` 生成的超空间入口；
- 用 `addPlanet` / `addCustomEntity` / `addAsteroidBelt` / `createToken` 创建并放置实体，选择正确的放置方式，解释 `createToken` 的 "初始不在容器" 语义与 token 上 `setFixedLocation` 空实现的陷阱；
- 用地点级与星区级查询读取实体，说出 `getEntityById` / `getEntitiesWithTag` 的覆盖范围、id 匹配规则与 "移除后不再命中"；
- 区分访问状态的更新来源与可观察结果：地点级 `isCurrentLocation` / `activeThisFrame`、实体级 `isInCurrentLocation` / `isInHyperspace` / `isAlive`、星系级 `isEnteredByPlayer` / `getDaysSinceLastPlayerVisit`；
- 编写五个协作类接入既有插件，验证新建生涯、跳入星系、暂停、读档与战斗各阶段的日志输出；
- 从症状定位故障：星系无超空间入口、实体查不到或堆在原点、token 位置不动、`isAlive` 误判、推进中移除星系抛异常。

## 前置知识

- [C3-S6] 的星系创建 API：`createStarSystem`、`initStar`、`addPlanet`、`addCustomEntity` 与 `autogenerateHyperspaceJumpPoints`；
- [C6-S2] 的 `modPlugin` 生命周期回调与持久数据标记；
- [C6-S3] 的 `Global.getSector()` 阶段语义与生涯引擎建立边界；
- [C9-S1] 的引擎级 `EveryFrameScript` 推进、暂停冻结、战斗期边界与一次性注册；
- 理解 `SectorAPI` 作为生涯引擎根对象，并能通过地点和实体查询入口定位对象。

## 术语

按术语约定，以下术语由前序主题首次引出，本节**首次完整讲解**：

- **地点容器 (location)**：API 名保留 `LocationAPI`。星区内部的空间容器，超空间与每个恒星系各是一个地点容器；实体、地形、舰队、脚本挂在容器里，容器自带 id、显示名、memory、persistent data、背景与访问状态。
- **恒星系 (star system)**：API 名保留 `StarSystemAPI`。一类地点容器，特化出恒星、中心、超空间锚点、自动跳跃点、星座等星系级结构。
- **实体 (entity)**：API 名保留 `SectorEntityToken`。挂在容器里的可定位对象 (行星、舰队、跳跃点、custom entity、terrain 实体等)，身份、位置、轨道、tag、memory 都由它携带。
- **访问状态**：本节首次成组讲解 ——"玩家在不在这个地点/实体在不在玩家当前地点/玩家有没有进过这个星系/距离上次访问多久"，对应 `isCurrentLocation` / `isInCurrentLocation` / `isEnteredByPlayer` / `getDaysSinceLastPlayerVisit` 等只读状态。

## 梳理恒星系生成关系

先按职责把 API 分组：容器怎么建、实体怎么放、查询怎么走、状态怎么读。理解这四件事之后，完整最小例只是把这些入口按顺序调用一遍。

### 地点容器模型 (LocationAPI)

`LocationAPI`(`com.fs.starfarer.api.campaign.LocationAPI`) 是纯接口，只有两种容器形态：超空间 (`isHyperspaceMode=true`) 与恒星系 (`StarSystemAPI`，`isHyperspaceMode=false`)。

一个存档恰好一个超空间，创建时被命名为 `"Hyperspace"`。容器模型：

```text
星区 SectorAPI
├── 超空间 Hyperspace(LocationAPI，isHyperspaceMode=true)
│   └── 实体集合：跳跃点、舰队、星门、异常地形、系统锚点...
├── 恒星系 StarSystem×N(StarSystemAPI extends LocationAPI)
│   └── 实体集合：恒星、行星、卫星、空间站、custom entity、terrain 实体、位置 token...
└── 玩家舰队所在的地点 = 当前地点 (current location)
```

### LocationAPI 成员分组速查表

按功能分组，本节涉及的 `LocationAPI` 成员：

| 分组 | 成员 | 语义 |
|---|---|---|
| 标识 | `getId()` / `getName()` / `setName()` | 地点 id = 显示名小写 (恒星系为基名小写，见下)；显示名可改 |
| 实体容器 | `addEntity` / `removeEntity` / `getAllEntities` / `getEntities(Class)` / `getEntityById` / `getEntitiesWithTag` / `getCustomEntitiesWithTag` / `getFleets` / `getPlanets` / `getTerrainCopy` / `getCustomEntities` / `getAsteroids` / `getJumpPoints` / `getGravityWells` | 实体集合与按类别/按 tag 的派生查询 (见 "实体容器查询" 节) |
| 创建入口 | `addPlanet` / `addCustomEntity` / `addTerrain` / `addAsteroidBelt` / `addAsteroid` / `addCorona` / `addRingBand` / `addOrbitalJunk` / `createToken` / `addRadioChatter` / `spawnFleet` | 创建并加入容器 (`createToken` 例外，见 "实体与放置" 节) |
| 脚本 | `addScript` / `removeScript` / `removeScriptsOfClass` / `getScripts` | 地点级每帧脚本，推进节奏跟随地点 (见 "访问状态" 节) |
| 状态 | `isCurrentLocation` / `activeThisFrame` / `isHyperspace` / `isNebula` / `isDeepSpace` / `getLastPlayerVisitTimestamp` / `getDaysSinceLastPlayerVisit` | 访问状态 (见 "访问状态" 节) |
| 数据 | `getPersistentData` / `getMemoryWithoutUpdate` | 地点级持久 Map 与 memory |
| 背景 | `getBackgroundTextureFilename` / `setBackgroundTextureFilename` / `setBackgroundOffset` / `getBackgroundColorShifter` | 背景贴图与偏移 (星系创建主题已用，[C3-S6]) |
| 星系特化 | `isInConstellation` / `getConstellation` | 恒星系实现为星座成员判断，超空间恒为 false / null |

容器关键行为：

1. **`addEntity` / `removeEntity` 就是 "进容器 / 出容器"**：入容器会写实体的 `setContainingLocation`、把实体加入容器的实体集合，并同步星区级 id 索引。
    - 出容器只移除，**不清空** `containingLocation`。
2. **`getAllEntities()` 返回当前实体集合**：`objects.getList(SectorEntityToken.class)`；`getFleets` / `getPlanets` / `getTerrainCopy` / `getCustomEntities` 等是同一仓库按类过滤的派生集合。
    - **这是运行时的活集合** —— 其他代码随时可能添加或移除实体。
3. **`getEntityById(id)`**：先用缓存索引查精确 id，未命中回退线性扫描且**大小写不敏感**；实体已不在容器内时返回 null。
    - 索引每帧重建，并随实体增删增量维护。
4. **`isHyperspace()` = "我就是超空间"**：`this == Global.getSector().getHyperspace()`—— 不是 "内容像超空间"，是对象同一性。
5. **`isCurrentLocation()` = "我是玩家当前地点"**：`this == Global.getSector().getCurrentLocation()`。玩家跳跃/跃迁到达时，引擎调用 `setCurrentLocation` 更新当前地点。
6. **`activeThisFrame()`**：当前地点恒为 `true`；非当前地点只在真正推进的那一帧为 `true`。
    - 引擎对非当前地点**每 60 帧推进一次、步长 ×60**(`activeThisFrame` 只在真正推进的那一帧为 `true`、其余帧为 `false`)。
7. **`getLastPlayerVisitTimestamp()` / `getDaysSinceLastPlayerVisit()`**：地点成为当前地点并在非暂停帧推进时刷新时间戳。
    - **从未访问时返回 `Float.MAX_VALUE`**(与引擎级时钟的 `getElapsedDaysSince(0)` 同一语义)。

### 恒星系结构 (StarSystemAPI)

`StarSystemAPI`(`com.fs.starfarer.api.campaign.StarSystemAPI`) **继承 `LocationAPI`**，是恒星系的容器接口。结构要点：

- **标识**：基名 (`baseName`)+ 显示名。构造时 `setName(baseName + " Star System")`；`getId()` = **基名小写** (区别于默认的 "显示名小写")；`setBaseName` 联动改名。
    - `getNameWithNoType()` / `getNameWithLowercaseType()` 等是去后缀/换写法的显示辅助。
- **查询键**：`SectorAPI.getStarSystem(name)` 用 `optionalUniqueId`(若非 null) 或基名小写匹配，**大小写不敏感**，找不到返回 null。
    - 所以 `setOptionalUniqueId("GearIn_demo")` 后，`getStarSystem("GearIn_demo")` 与 `getStarSystem("gearIn_demo")` 都能命中。
- **恒星与中心**：`getStar()`(主星)、`getSecondary()` / `getTertiary()`(联星)、`getCenter()`(`center` 为 null 时回退主星)、`initNonStarCenter()`(无恒星星系用，建一个 `LocationToken` 当中心)。
- **超空间锚点**：`getHyperspaceAnchor()` / `setHyperspaceAnchor()`。
    - 锚点是一个放在超空间里的 `LocationToken`(tag `"system_anchor"`、memory `$anchor_starSystem` 指向本星系、轨道类型 `UpdateFromHyperspaceLocation` 让锚点跟随星系位置)。
    - 锚点由 `generateAnchorIfNeeded()` 创建 —— **已存在则跳过**。
- **超空间入口**：`autogenerateHyperspaceJumpPoints()` 无参版本等价于 `(false, false)`。
    - 三参版本依据星系内的跳跃点、恒星和行星生成超空间入口。气态巨行星只在第一个参数为 false 时生成 nascent gravity well；传 true 时生成普通跳跃点。最后补 `generateAnchorIfNeeded` 并生成行星条件。
    - 生成结果读 `getAutogeneratedJumpPointsInHyper()` / `getAutogeneratedNascentWellsInHyper()`。
- **其他**：`getType()` / `setType()`(`StarSystemGenerator.StarSystemType`，`isNebula` / `isDeepSpace` 据此)、`getConstellation()` / `isInConstellation()`(星座多于一个星系才算)。
    - `hasPulsar()` / `hasBlackHole()`(按恒星与联星 spec 判定)、`isEnteredByPlayer()`(见 "访问状态" 节)、`getMapGridWidthOverride` / `getMapGridHeightOverride`、`getMaxRadiusInHyperspace`。

### initStar 变体速查表

| 签名 | 超空间坐标 | 超空间锚点 | 其他 |
|---|---|---|---|
| `initStar(id, type, radius, coronaSize)` | 不设置 | 不生成 | 默认日冕参数 `windBurnLevel=10` / `flareProbability=0.25` / `crLossMult=3` |
| `initStar(id, type, radius, coronaSize, windBurn, flare, crLoss)` | 用恒星 id 查 starmap.json 的 `starSystemLocations`(查不到保持 (0,0)，[C3-S6] 知识) | **不生成** | 完整日冕参数；[C3-S6] 的 `GearIn` 星系用此变体 |
| `initStar(id, type, radius, hyperX, hyperY, coronaSize)` | 显式设置 | **自动生成超空间锚点** | 本节示例用此变体；跳跃点仍由 `autogenerateHyperspaceJumpPoints` 生成 |

`initStar` 内部：移除旧星、创建新恒星实体并加入容器、按变体查 starmap 或写坐标、`addCorona`。

玩家可达性必须显式打通

**除带坐标变体外，星系建好时超空间里什么都没有 —— 玩家无法到达**。**必须**调用 `autogenerateHyperspaceJumpPoints`；带超空间坐标的 6 参数 `initStar` 变体只生成超空间锚点，不生成跳跃点 (规则核对见 "规则强度核对" 节)。

### 实体与放置 (SectorEntityToken)

`SectorEntityToken`(`com.fs.starfarer.api.campaign.SectorEntityToken`) 是实体约定：身份 (`getId` / `setId` / `getName` / `setName` / `getFullName`)、归属 (`setFaction` / `getFaction` / `getMarket` / `setMarket`)。

位置与轨道 (`getLocation` / `setLocation` / `setFixedLocation` / `setCircularOrbit` / `setCircularOrbitPointingDown` / `setCircularOrbitWithSpin` / `setOrbit` / `getOrbit` / `getOrbitFocus`)。

容器持有 (`getContainingLocation` / `setContainingLocation` / `isAlive` / `isExpired` / `setExpired`)。

访问状态 (`isInCurrentLocation` / `isInHyperspace` / `getLocationInHyperspace` / `isInOrNearSystem`)、tag 与 memory、脚本挂载 (`addScript` / `removeScript` / `removeScriptsOfClass` / `getScripts`)。

实体级脚本随实体生命周期推进，引擎级脚本主题已述。

容器持有的精确语义：

- **`isAlive()`** = `containingLocation != null` 且该容器的实体集合包含自己。
    - "Returns false if: 1) getContainingLocation() is null, or 2) getContainingLocation() doesn't contain this entity"。
    - **`removeEntity` 后普通实体的 `isAlive()` 立即变 `false`。**
- **`isExpired()` / `setExpired()`**：`isExpired` 为 true 的实体在容器推进时被自动移除 —— 只置 `expired` 不调 `removeEntity` 也能出容器，但延迟到下一帧。
- **`LocationToken`(`createToken` 的返回值) 例外**：`isAlive()` **恒返回 true**(`LocationToken` 实现)，与容器无关 —— 不能用它判断 token 的容器持有。

### 实体放置方式速查表

| 方式 | 行为 |
|---|---|
| `setFixedLocation(x, y)` | **清空轨道** (`setOrbit(null)`) 并写坐标 |
| `setCircularOrbit(focus, angle, orbitRadius, orbitDays)`(含 PointingDown / WithSpin 变体) | 建一个圆形轨道 (`OrbitAPI`，焦点 + 半径 + 周期 + 起始角) |
| `setOrbit(OrbitAPI)` | 自定义轨道；`setOrbit` 会回调 `orbit.setEntity(this)` 并立即 `advance(0)` 定位 |

### 实体创建入口速查表

| 入口 | 返回 | 自动副作用 |
|---|---|---|
| `addPlanet(id, focus, name, type, angle, radius, orbitRadius, orbitDays)` | `PlanetAPI` | `focus` 非 null 且 `radius >= 1` 才设轨道；**非恒星行星自动挂 condition-only 市场** (`Misc.initConditionMarket`，见下) |
| `addCustomEntity(id, name, type, factionId[, radius, spriteWidth, spriteHeight][, pluginParams])` | `CustomCampaignEntityAPI` | `factionId` 缺省 `neutral`；位置默认 (0,0)，**必须显式放置** |
| `addTerrain(terrainId, param)` | `CampaignTerrainAPI` | 直接建 terrain 实体；`param` 按地形类型 (如 `AsteroidBeltParams` / `CoronaParams`) |
| `addAsteroidBelt(focus, num, orbitRadius, width, minOrbitDays, maxOrbitDays)` | `SectorEntityToken` | 内部 `addTerrain("asteroid_belt", ...)` 并围绕 focus 公转 |
| `addAsteroid(radius)` | `AsteroidAPI` | 独立小行星实体 |
| `addCorona(star, extraRadius, windBurn, flare, crLoss)` | `SectorEntityToken` | terrain `"corona"`；`initStar` 内部已调用 |
| `addRingBand(...)` | `RingBandAPI`；terrain 版返回地形实体 | 环带视觉 + 可选地形 |
| `createToken(x, y)` / `createToken(Vector2f)` | `SectorEntityToken` | **不加入容器** ("Not actually added to the location, and doesn't need to be. Can be added via addEntity if it needs to have an orbit.")；需要轨道或被查询时 `addEntity` |

token 的两个陷阱 (本节重点)

1. `LocationToken.setFixedLocation(x, y)` 是**空实现** ——token 上调用它坐标不变、无任何报错；写 token 坐标**必须用 `setLocation(x, y)`**(真实生效)。
2. token 不 `addEntity` 就不在实体集合里：`getAllEntities()` 不含它、`getEntityById(token.getId())` 找不到它 —— 但 `isAlive()` 仍返回 `true`(见上)，三个状态互相矛盾是正常现象，别拿 `isAlive` 当容器判据。

`addPlanet` 的自动市场 (`com.fs.starfarer.api.util.Misc.initConditionMarket` 实现)：非恒星行星建 `"market_" + 行星id`、neutral、`setPlanetConditionMarketOnly(true)` 的市场并 `planet.setMarket(market)`。

所以 `addPlanet` 之后 `getMarket()` 立即非 null(这是 "仅条件市场"：只装行星条件、不进经济列表；正式市场流程见 [C10-S1])。

### 实体容器查询

### 实体查询入口速查表

| 查询 | 地点级 (`LocationAPI`) | 星区级 (`SectorAPI`) |
|---|---|---|
| 按 id | `getEntityById(id)`：本容器内；缓存索引精确 id + 回退大小写不敏感扫描；不在容器内返回 null | `getEntityById(id)`：**遍历超空间与所有恒星系** ("Will look for the entity in all LocationAPIs") |
| 按 tag | `getEntitiesWithTag(tag)` / `getCustomEntitiesWithTag(tag)`(后者只遍历 custom entity，更省) | `getEntitiesWithTag(tag)` / `getCustomEntitiesWithTag(tag)`：超空间 + 所有星系拼接 |
| 全部 | `getAllEntities()` / `getEntities(Class)`(返回裸 `List`) | `getAllLocations()`(星系 + 超空间)/ `getStarSystems()`(副本) |
| 分类 | `getFleets` / `getPlanets` / `getTerrainCopy` / `getCustomEntities` / `getAsteroids` / `getJumpPoints` / `getGravityWells` | — |

要点：

"查不到" 未必是实体丢了

自定义 id 用错大小写是常见事故源 ——id 匹配对大小写不敏感 (两级的回退扫描都 `toLowerCase`)，但缓存索引键是精确 id，只有回退扫描才折叠大小写。

另一个来源是 "移除后不再命中"：`removeEntity` 后地点级与星区级 `getEntityById` 都返回 null(索引由 `objectAdded` / `objectRemoved` 维护)。已持有的引用不受影响 —— 移除只改容器，不销毁对象。

- **空间站查询**：`getOrbitalStations()` 已废弃，**必须用 `getEntitiesWithTag(Tags.STATION)`** (`Tags` 在 `com.fs.starfarer.api.impl.campaign.ids.Tags`)。
    - custom entity 充当的空间站同样带 `STATION` tag，只有 tag 查询能一起捞到。
- `getEntityByName` 同样已废弃，按 id 查询必须用 `getEntityById`。

### 访问状态

### 访问状态速查表

| 状态 | API | 更新/置位来源 | 可观察结果 |
|---|---|---|---|
| 我是玩家当前地点 | `LocationAPI.isCurrentLocation()` | `this == sector.getCurrentLocation()`；跳跃/跃迁到达时引擎调用 `setCurrentLocation` | 玩家在星系内时为 `true` |
| 本帧推进了吗 | `LocationAPI.activeThisFrame()` | 当前地点恒 `true`；非当前地点每 60 帧推进的那一帧 `true` | 非当前地点的脚本/日志节奏是 "60 帧一次、`amount` 跳变 ×60" |
| 实体在玩家当前地点吗 | `SectorEntityToken.isInCurrentLocation()` | `containingLocation == currentLocation` | 玩家在星系内时其中实体为 `true`；已移出容器仍能回答 |
| 在超空间吗 | `SectorEntityToken.isInHyperspace()` / `LocationAPI.isHyperspace()` | 实体：containing 地点是超空间 (`isHyperspaceMode`)；地点：对象同一性 | 超空间内实体为 `true` |
| 玩家进过这个星系吗 | `StarSystemAPI.isEnteredByPlayer()` | 玩家舰队跳跃进入星系时置位 (`com.fs.starfarer.api.impl.campaign.CoreScript` 的 `reportFleetJumped` → `markSystemAsEntered`)；新档拥有正式市场的星系在生成期置位 (`com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl.updateKnownPlanets`) | 首次进入后 `true`，随存档保存；使用方是超空间地图对未进入星系的展示 |
| 距上次玩家访问 | `getDaysSinceLastPlayerVisit()` / `getLastPlayerVisitTimestamp()` | 成为当前地点且非暂停帧推进时刷新时间戳 | **从未访问 = `Float.MAX_VALUE`**；进入后 ≈ 0；离开后递增 |
| 还挂在容器里吗 | `SectorEntityToken.isAlive()` | `containingLocation` 非 null 且容器实体集合包含自己 (普通实体)；token 恒 `true` | `removeEntity` 后普通实体为 `false` |

推进节奏决定这些状态 "什么时候会变"(引擎级推进语义已述，这里补地点级)：当前地点每帧 `advance`；非当前地点每 60 帧以 ×60 步长推进。

暂停时地点 `advance` 不执行 (地点级脚本与引擎级脚本一样默认冻结 —— 仅 `runWhilePaused()` 返回 true 的脚本在暂停时继续推进)；战斗期间生涯引擎完全不推进。

为什么地点级脚本与引擎级脚本只差推进节奏

引擎级脚本每帧推进，地点级脚本跟随地点推进 (当前地点每帧、非当前地点 60 帧合一次、`amount` 跳变 ×60)。因此需要 "实时节奏" 的逻辑挂引擎级 (本节示例)，"只在某地点内干活" 的逻辑才挂地点级。

### 文件与注册链路

- **不新增任何数据文件**：演示星系的恒星类型 `"star_white"`、行星类型 `"terran"`、custom entity 类型 `"stable_location"`(`data\config\custom_entities.json` 键)、terrain id `"asteroid_belt"` 全部复用原版资源。
    - 不需要 JSON/CSV/贴图。
- **本节拥有的 ID**：只由本节定义，其他页面只能使用并链接所有者。

### 本节 ID 速查表

| ID | 用途 |
|---|---|
| `GearIn_demo` | 演示星系 `optionalUniqueId`(`SectorAPI.getStarSystem` 查询键) |
| `GearIn_demo_star` | 恒星实体 id |
| `GearIn_demo_planet1` | 行星实体 id |
| `GearIn_demo_beacon` | 信标 custom entity id |
| `GearIn_demo_tag` | 信标 tag |
| `GearIn_LocationScript`(`REGISTER_MARK`) | persistent data 一次性注册键 |

- **注册链路**：`GearInLocationScript`(本节所有)→ 既有插件类 `GearInModPlugin.onGameLoad` 中经 persistent data 一次性注册判断后的 `sector.addScript`(沿用 "每个注册项各自键" 约定)→ 引擎每帧 `advance` → 日志可观察。
    - 星系创建发生在脚本**首帧** (`onGameLoad` 之后，安全)。

## 实现恒星系生成器

先让生成器具备幂等入口，再逐层加入星体、信标、位置 token 和访问状态。以下都是局部片段；每段均标明插入位置，完成信号出现后再继续。

### 复用已经存在的星系

插入位置：`GearInLocationSystemBuilder.getOrCreate`。先按本节拥有的 `optionalUniqueId` 查询，命中时直接返回，避免读档或旧档补装脚本时重复生成同名星系。

```java
public static StarSystemAPI getOrCreate(SectorAPI sector) {
	StarSystemAPI system = sector.getStarSystem(SYSTEM_UNIQUE_ID);
	if (system != null) {
		LOG.info("GearIn loc: demo system already exists, reusing id=" + system.getId());
		return system;
	}
	return create(sector);
}
```

完成信号：首次运行进入 `create`；读档或再次调用时只出现 `demo system already exists` 日志，星区中仍只有一个演示星系。

### 填入星体与信标

插入位置：`GearInLocationSystemBuilder.create`，紧接 `createStarSystem` 与 `setOptionalUniqueId`。带超空间坐标的 `initStar` 同时定位星系并创建锚点；行星和 custom entity 则分别进入星体集合与自定义实体集合。

```java
PlanetAPI star = system.initStar(STAR_ID, "star_white", 600f, 7500f, 8500f, 400f);
PlanetAPI planet = system.addPlanet(
		PLANET_ID, star, "GearIn Demo Prime", "terran", 55, 150, 3200, 120);

SectorEntityToken beacon = system.addCustomEntity(
		BEACON_ID, "GearIn 演示信标", "stable_location", "neutral");
beacon.setLocation(1400f, 900f);
beacon.addTag(BEACON_TAG);
system.addAsteroidBelt(planet, 40, 600, 100, 120, 160);
```

完成信号：星系位置为 `(7500, 8500)`，信标能按 `BEACON_ID` 查回且位于 `(1400, 900)`，周期摘要中的 `planets=2`、`terrains=2`、`custom=1`。

### 把位置 token 加入容器

插入位置：`GearInTokenCreator.create`。`createToken` 只创建对象；需要让地点查询持有它时，还要设置坐标并显式调用 `addEntity`。

```java
SectorEntityToken token = system.createToken(0f, 0f);
LOG.info("GearIn loc: token before add | inList="
		+ system.getAllEntities().contains(token));
token.setLocation(-2000f, -1500f);
system.addEntity(token);
LOG.info("GearIn loc: token after add | byId="
		+ (system.getEntityById(token.getId()) != null));
```

完成信号：两条日志依次给出 `inList=false` 与 `byId=true`；这也证明 token 的创建和容器持有是两个独立动作。

### 在脚本首帧装配协作对象

插入位置：`GearInLocationScript.setup`。星系创建或复用后立即取回本节信标；缺失时终止本脚本后续逻辑，避免每帧重复抛出空引用异常。

```java
demoSystem = GearInLocationSystemBuilder.getOrCreate(Global.getSector());
beacon = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);
if (beacon == null) {
	LOG.error("GearIn loc: beacon not found in demo system, demos disabled");
	broken = true;
	return;
}
accessStateLogger = new GearInAccessStateLogger(demoSystem, beacon);
containerCheck = new GearInContainerCheck(demoSystem, beacon);
setupDone = true;
```

完成信号：正常存档只装配一次；信标缺失的存档只记录一条明确错误，后续帧直接退出。

### 记录地点变化与周期状态

插入位置：`GearInLocationScript.advance` 的 `setup` 判断之后。地点变化日志负责捕捉进入事件，计时器则把高频状态压缩为每 10 秒一条摘要。

```java
LocationAPI current = Global.getSector().getCurrentLocation();
if (accessStateLogger.logLocationChangeIfChanged(current) && current == demoSystem) {
	accessStateLogger.logEnteredDemoSystem();
}

sinceLastLog += amount;
if (sinceLastLog >= LOG_INTERVAL) {
	sinceLastLog = 0f;
	accessStateLogger.logStateSummary();
}
```

完成信号：跨地点时只出现一次变化日志；未暂停的生涯中约每 10 个真实秒出现一条 `GearIn loc: state`。

### 核验移除与重新加入

插入位置：`GearInContainerCheck.runRemoveAddCheck`。保存移除前状态，执行一次移除和重新加入，再分别查询容器与星区索引。

```java
boolean aliveBefore = beacon.isAlive();
SectorEntityToken byIdBefore = demoSystem.getEntityById(
		GearInLocationSystemBuilder.BEACON_ID);

demoSystem.removeEntity(beacon);
boolean aliveAfterRemove = beacon.isAlive();
SectorEntityToken byIdAfterRemove = demoSystem.getEntityById(
		GearInLocationSystemBuilder.BEACON_ID);

demoSystem.addEntity(beacon);
boolean aliveAfterReadd = beacon.isAlive();
SectorEntityToken byIdAfterReadd = demoSystem.getEntityById(
		GearInLocationSystemBuilder.BEACON_ID);
```

完成信号：普通实体的 `isAlive` 与按 ID 查询均呈现 `true → false → true`，信标重新出现在星系实体集合中。

## 组装星系生成实现

前面四组 API 已经讲完，现在把它们按任务顺序写进五个协作类。示例在既有 MOD 布局与插件类 `GearInModPlugin` 之上新增 5 个协作类 (不新增任何资源、不新增任何注册项)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\campaign\
│       ├── GearInSectorClockScript      # 引擎级脚本主题所有
│       ├── GearInSystemGenerator        # 星系创建主题所有
│       ├── GearInLocationScript         # 本节新增 (编排)
│       ├── GearInLocationSystemBuilder  # 本节新增 (星系创建)
│       ├── GearInTokenCreator           # 本节新增 (token 语义)
│       ├── GearInAccessStateLogger      # 本节新增 (访问状态日志)
│       └── GearInContainerCheck         # 本节新增 (容器核验)
└── jars\
```

五个类一文件一角色：脚本类承担编排，其余四个角色类由脚本装配或调用 (仅包内协作，无跨单元依赖)，绑定位置如下。

### GearInLocationScript(编排)

职责：生涯每帧脚本 ——`REGISTER_MARK` 一次性注册判断、首帧装配辅助角色、每帧驱动 "地点变化检测 / 首次进入核对 / 周期摘要"。

**绑定位置：`onGameLoad` 中经 persistent data 一次性注册判断后调用 `sector.addScript`**(见下方调用点片段)；这是本节唯一的注册入口，其余四个类由它装配或调用。

### GearInLocationSystemBuilder(星系创建)

职责：演示星系的创建与复用 —— 存在则 `getStarSystem` 复用，否则创建恒星、行星、信标、小行星带并调用 `autogenerateHyperspaceJumpPoints` 打通超空间入口。

**绑定位置：被 `GearInLocationScript.setup` 调用 (静态入口 `getOrCreate`)**。本节拥有的常量 (`SYSTEM_UNIQUE_ID` / `STAR_ID` / `PLANET_ID` / `BEACON_ID` / `BEACON_TAG`) 都定义在这里。

### GearInTokenCreator(token 语义)

职责：位置 token 的创建与容器语义演示 ——`createToken` 初始不在实体集合里，需要轨道或被按 id 查询时先 `addEntity` 加入容器。**绑定位置：被 `GearInLocationSystemBuilder` 在填充星系时调用 (静态入口 `create`)**。

### GearInAccessStateLogger(访问状态日志)

职责：访问状态六件套的可观察输出，三条日志 ——location 变化、进入演示星系、周期摘要；`formatDays` 把 `Float.MAX_VALUE` 显示为 `never`。

**绑定位置：由 `GearInLocationScript.setup` 构造 (持有星系与信标引用)，`advance` 每帧驱动**。

### GearInContainerCheck(容器核对)

职责：实体容器持有语义核对 —— 对信标执行一次 `removeEntity` / `addEntity`，记录 `isAlive()` 与 `getEntityById()` 的前后变化 (true→false→true 全链路)。

**绑定位置：由 `GearInLocationScript.setup` 构造，玩家首次进入演示星系时执行一次 (`enterDone` 判断，读档不重放)**。

对应关系：

- `GearInLocationScript implements EveryFrameScript`：三个方法全来自接口；`isDone()` 恒 `false`(常驻)、`runWhilePaused()` 恒 `false`(暂停冻结，引擎级脚本语义)。
    - `setup()` 首帧执行：`getOrCreate` 命中 (读档场景) 走 "复用" 分支，未命中 (新档) 走创建分支 —— 这就是 "旧档只补实体、不重复建星系" 的幂等做法。
- `REGISTER_MARK`：persistent data 一次性注册键 (`"GearIn_LocationScript"`)，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。
- `GearInLocationSystemBuilder`：星系创建与复用 (`SYSTEM_UNIQUE_ID` / 实体 ID 常量都在这里)。
    - `system.getId()` 实际是基名小写 (`"gearin demo"`)，`getStarSystem(SYSTEM_UNIQUE_ID)` 按 `optionalUniqueId` 命中 —— 两条路径都可观察。
- `GearInTokenCreator`：token 两行日志 `inList before add=false` 与 `isAlive before add=true` 并存 —— 正是 "token 不在容器但 `isAlive` 恒 `true`" 的教学点。
- `GearInAccessStateLogger`：访问状态六件套的可观察输出 (location 变化 / 进入演示星系 / 周期摘要三条日志)；`formatDays` 把 `Float.MAX_VALUE` 显示为 `never`。
- `GearInContainerCheck`：`removeEntity` → `isAlive=false` / 两处 `getEntityById` 都 null → `addEntity` 恢复 —— 容器持有语义形成完整流程。
- `REGISTER_MARK` 注册判断 + `SectorAPI.getStarSystem` 幂等检查：读档不会重复建星系 (脚本字段随存档序列化，`setupDone` 保持 `true` 根本不重跑 `setup`；即使字段丢失，`getStarSystem` 也能捞回已有星系)。

五个类只做包内协作，不跨单元依赖；日志前缀统一 `GearIn loc`—— 全部 10 条日志可观察信号与拆分前逐字一致，时序固定为 token created → token added → created system。

调用点 (工程片段 —— 方法所在类为既有插件类，本节只演示调用，不改动既有文件)：

```java
    // GearInModPlugin(既有插件类)onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门 (沿用既有约定)。
    if (!Global.getSector().getPersistentData().containsKey(GearInLocationScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInLocationScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInLocationScript());
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInLocationScript.class`(可用 `jar tf` 核对，五个类都应出现)。

## 规则强度核对

以下规则中为 "必须/禁止"，本节全部用硬措辞并写明违反后的可观察结果；未做任何软化。

### 硬性规则速查表

| 规则 | 依据 | 违反后的可观察结果 |
|---|---|---|
| **必须**调用 `autogenerateHyperspaceJumpPoints`；6 参数 `initStar` 只生成锚点 | `StarSystemAPI.initStar` 与内部实现 | 星系存在但超空间无跳跃点；4/7 参数变体还会使 `getHyperspaceAnchor()` 为 null，6 参数变体的锚点仍非 null |
| token 写坐标**必须**用 `setLocation`(其 `setFixedLocation` 为空实现，调用无效) | (`LocationToken.setFixedLocation` 空实现) | token 坐标不变、无任何报错，位置静默错误 |
| 空间站查询**必须**用 `getEntitiesWithTag(Tags.STATION)`(`getOrbitalStations()` 已废弃)；按 id 查询**必须**用 `getEntityById`(`getEntityByName()` 已废弃) | `LocationAPI` 废弃标注 | 用废弃方法仍能跑，但拿不到 custom entity 充当的空间站、且未来 API 行为不保证 |
| 脚本/advance 内移除星系**必须**用 `removeStarSystemNextFrame` | `SectorAPI.removeStarSystemNextFrame` | `removeStarSystem` 在遍历中调用抛 `ConcurrentModificationException` |
| 一次性注册**必须**经 persistent data 判断、每个注册项各自键 | 既有插件生命周期与引擎级脚本主题的约定 (延续) | 每次读档重复注册，脚本实例叠加 (日志成双、行为叠加) |
| 脚本字段**必须**只放可序列化状态 (星系/实体引用可以，引擎单例不行) | 引擎级脚本主题的约定 (延续) | 存档/读档异常或读档后引用失效 |

## 关键故障实验：恒星系创建成功但舰队无法进入

星体、信标和超空间锚点都已生成，但创建方法在生成跳跃点之前返回。

**局部故障片段：**

```java
// GearInLocationSystemBuilder.create 中过早返回
GearInTokenCreator.create(system);
return system;
```

**局部修正片段：**

```java
// 完成实体填充后、return system 之前生成入口
GearInTokenCreator.create(system);
system.autogenerateHyperspaceJumpPoints(true, false);
return system;
```

实际症状：`getStarSystem(SYSTEM_UNIQUE_ID)` 能查到星系，`getHyperspaceAnchor()` 也为非 null，但 `getAutogeneratedJumpPointsInHyper()` 为空，超空间地图没有可进入的跳跃点。

原因：6 参数 `initStar` 负责超空间定位与锚点，`autogenerateHyperspaceJumpPoints` 才负责生成恒星系内外成对的跳跃入口。

复验：新档首帧日志应显示 `anchor=true | hyperJumpPoints=1`；再从超空间地图定位 `GearIn Demo Star System`，通过入口跳入并观察 `demo system entered` 日志。

## 验证方案

前置数据：既有 MOD 布局与插件类 ([C6-S1] / [C6-S2]，含本节调用点片段)、本节五个示例类、累计工程的引擎级时钟脚本 [C9-S1] 与星系生成器 (非必需)。

1. 游戏构建：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含五个类的 class 条目。
2. 新建生涯：确认 `starsector.log` 首帧依次出现两条 token 日志与 `GearIn loc: created system ... | anchor=true | hyperJumpPoints=1 | hyperLoc=(7500, 8500)`。
    - 观察 token 创建、添加、系统创建与周期状态日志的先后顺序。
3. 超空间地图：确认存在 `GearIn Demo Star System` 跳跃点；未进入时摘要 `daysSinceVisit=never`、`enteredByPlayer=false`。
4. 跳入演示星系：确认 `player location change | ... -> gearin demo [...isCurrentLocation=true, activeThisFrame=true]`、`demo system entered | isEnteredByPlayer=true ...` 与一次性 remove/add 核对日志。
    - 核对 alive/byId 全流程 true→false→true；星系内周期摘要 `demoSystem.isCurrentLocation=true ... daysSinceVisit=0.0`；地图上信标出现在 (1400, 900) 附近。
5. 暂停：星系内按空格暂停 20 秒以上 —— 无新日志；恢复后继续。
6. 读档：保存后读档 —— 无 `created system`、无重复注册、无重复星系；周期摘要从存档时刻继续；再次进入演示星系不重复执行 remove/add 核对。
7. 战斗：进入任意遭遇战 —— 战斗期间无 `GearIn loc` 日志；回生涯后继续。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInLocationScript.class`。
- **新建生涯 (脚本首帧)**：`starsector.log` 依次出现两条 token 日志与一条创建日志：

    ```text
    GearIn loc: token created | id=<十六进制id> | inList before add=false | isAlive before add=true GearIn loc: token added | inList after add=true | byId found=true GearIn loc: created system id=gearin demo name=GearIn Demo Star System | star=GearIn Demo planet=GearIn Demo Prime | anchor=true | hyperJumpPoints=1 | hyperLoc=(7500, 8500)
    ```

    随后一帧出现 `GearIn loc: player location change | first observed: <玩家出生地点 id>`。说明：`hyperJumpPoints=1` 来自 autogen 逻辑 (恒星 → 1 个超空间跳跃点；非气态巨行星 → nascent gravity well 而不是跳跃点)；`anchor=true` 证明 6 参数 `initStar` 自动生成了超空间锚点。
- **周期摘要**：此后约每 10 真实秒一条 (未暂停)：

    ```text
    GearIn loc: state | current=<当前地点名> (id=<id>) | demoSystem.isCurrentLocation=false activeThisFrame=<不定> enteredByPlayer=false daysSinceVisit=never | entities=6 planets=2 terrains=2 custom=1 | beacon isAlive=true inList=true
    ```

    实体计数构成：恒星 + 行星 + 信标 + 小行星带 + 位置 token + 日冕地形 = 6；`planets=2`(恒星与行星都是 `PlanetAPI`)、`terrains=2`(日冕 + 小行星带)、`custom=1`(信标)。若计数不同，先排查其他 MOD 是否向新建星系追加了实体。
- **跳跃进入演示星系**：在超空间地图飞到 `GearIn Demo Star System` 跳跃点跳入，日志出现：

    ```text
    GearIn loc: player location change | <旧地点 id> -> gearin demo [name=GearIn Demo Star System, isHyperspace=false, isCurrentLocation=true, activeThisFrame=true] GearIn loc: demo system entered | isEnteredByPlayer=true | daysSinceLastVisit=<never 或 ≈0.0> | beacon.isInCurrentLocation=true | beacon.isAlive=true
    ```

    `isEnteredByPlayer=true`：玩家舰队跳跃进入时置位 (`CoreScript.reportFleetJumped` → `markSystemAsEntered`)；`daysSinceLastVisit` 由 `never`(从未访问) 变为接近 `0.0`(成为当前地点后逐帧刷新时间戳)。玩家在星系内时周期摘要变为 `demoSystem.isCurrentLocation=true activeThisFrame=true daysSinceVisit=0.0`。
- **移除/重新加入演示 (首次进入时只执行一次)**：

    ```text
    GearIn loc: enter demo system first time - remove/add demo GearIn loc: beacon | alive before=true | byId before=true | alive after remove=false | byId after remove=false | sector byId after remove=false | inList after remove=false | alive after re-add=true | byId after re-add=true
    ```

    `removeEntity` 后普通实体 `isAlive()` 立即变 `false`、地点级与星区级 `getEntityById` 都返回 null；`addEntity` 后全部恢复。
- **暂停 (空格)**：周期摘要与地点变化日志全部停止 (引擎级脚本默认暂停冻结)；恢复后继续。
- **读档**：不出现 `created system`(`setupDone` 随存档序列化，不重跑 `setup`)；无重复注册 (`REGISTER_MARK` 已置位)；周期摘要从存档时刻继续，星系与实体原样恢复。若在旧档首次启用本 MOD(注册判断未置位、脚本字段为空)，首帧走 "复用" 分支打出 `GearIn loc: demo system already exists, reusing id=gearin demo`—— 星系幂等复用，不会重复创建。
- **战斗**：进入战斗期间无任何 `GearIn loc` 日志 (生涯引擎冻结)；回生涯后继续。
- 启动与游玩期间没有由 `gear_in` 引起的 `ERROR`。

## 故障分析

- **超空间里没有入口，玩家无法到达**：6 参数 `initStar` 只生成锚点，不生成跳跃点。调用 `autogenerateHyperspaceJumpPoints(true, false)`，随后在超空间地图核对入口。
- **实体查得到但地图上不显示**：`addCustomEntity` 后没有设置位置，实体仍在 (0,0)。创建后立即调用 `setFixedLocation` 或 `setCircularOrbit`。
- **token 调用 `setFixedLocation` 后完全不动**：`LocationToken.setFixedLocation` 是空实现。使用 `setLocation` 写坐标；需要轨道时先 `addEntity`，再调用 `setCircularOrbit`。
- **`isAlive()` 对 token 始终返回 true**：token 的实现不反映容器归属。访问状态使用 `isInCurrentLocation()` 或 `getContainingLocation()`；普通实体的容器持有才使用 `isAlive()`。
- **`getEntityById` 返回 null，但实体仍然存在**：查询 ID 大小写不一致，或代码复用了过期结果。ID 使用常量并逐字一致；每次需要时重新查询。
- **删除星系时抛出 `ConcurrentModificationException`**：脚本在遍历 `getStarSystems()` 时直接调用了 `removeStarSystem`。改用 `removeStarSystemNextFrame`，让引擎在遍历结束后处理删除。
- **地点级判据让所有实体都像在当前地点**：`location.isCurrentLocation()` 只判断地点本身。判断实体时改用 `entity.isInCurrentLocation()`。
- **离开星系后脚本日志变慢且 `amount` 跳变**：非当前地点每 60 帧合并推进一次。实时逻辑挂在引擎级脚本，地点级脚本只处理该地点内的行为。
- **读档后脚本中的地点引用行为错乱**：字段保存了瞬态的当前地点或 UI 引用。只保存持久对象与可序列化标量，引擎入口每次通过 `Global.getSector()` 获取。
- **战斗期间实体和地点状态停止变化**：战斗会暂停生涯推进，因此访问状态和 `GearIn loc` 日志同时冻结。返回生涯后应自动恢复。

## 总结

本节把地点容器讲成一条链：**建容器** (`createStarSystem` → `initStar` 选变体 → 填实体 → `autogenerateHyperspaceJumpPoints` 开入口)，**装实体** (`addPlanet` / `addCustomEntity` / `addTerrain` / `addAsteroidBelt` / `createToken`+`addEntity`，用 `setFixedLocation` / `setCircularOrbit` / `setOrbit` 放置)，**查实体** (地点级与星区级查询的覆盖范围、id 匹配、移除后不命中)，**读状态** (访问状态的更新来源与可观察结果)。

记忆要点：

- 超空间与恒星系都是 `LocationAPI`；`getAllLocations()` = 星系列表 + 超空间；地点 id = 显示名小写 (恒星系为基名小写)。
- `SectorAPI.getStarSystem(name)` 匹配 `optionalUniqueId` 或基名小写，大小写不敏感；找不到返回 null。
- `initStar` 三变体：4/7 参数**不生成超空间锚点**，6 参数 (带超空间坐标) **只生成锚点**；玩家可达性必须靠 `autogenerateHyperspaceJumpPoints`。
- `createToken` 不加入容器；token 的 `setFixedLocation` 是空实现 (**必须用 `setLocation`**)；token 的 `isAlive()` 恒 `true`—— 与普通实体 (`isAlive` = 容器持有) 不同。
- 访问状态分三层：地点层 (`isCurrentLocation` / `activeThisFrame` / 访问时间戳)、实体层 (`isInCurrentLocation` / `isInHyperspace` / `isAlive`)、星系层 (`isEnteredByPlayer` / `getDaysSinceLastPlayerVisit`)；`getDaysSinceLastPlayerVisit` 从未访问 = `Float.MAX_VALUE`。
- 非当前地点每 60 帧合步推进 (`amount` ×60)：实时节奏挂引擎级脚本，地点内逻辑才挂地点级脚本。
- 脚本内删星系必须 `removeStarSystemNextFrame`；废弃入口 (`getEntityByName` / `getOrbitalStations`) 必须用 `getEntityById` / `Tags.STATION` 替代。

从逻辑上看，本节建立的容器与访问状态模型是实体子系统的共同基础：舰队成员、市场等实体级主题都在这条链上继续延伸；引擎级脚本的一次性注册判断与生涯阶段语义则贯穿始终。

## 建议参考项

- Java API(包路径)：
    - `com.fs.starfarer.api.campaign.LocationAPI`—— 地点容器接口：实体容器、创建入口 (`createToken` 不加入容器)、脚本、访问状态与背景。
    - `com.fs.starfarer.api.campaign.StarSystemAPI`—— 恒星系接口：`initStar` 变体、超空间锚点、`autogenerateHyperspaceJumpPoints`、`isEnteredByPlayer`。
    - `com.fs.starfarer.api.campaign.SectorEntityToken`—— 实体约定：身份、归属、位置与轨道、容器持有 (`isAlive` / `isExpired`)、访问状态、tag 与 memory。
    - `com.fs.starfarer.api.campaign.SectorAPI`—— 星区入口：`createStarSystem` / `getStarSystem` / 全地点实体查询 / `removeStarSystemNextFrame`。
    - `com.fs.starfarer.api.campaign.CampaignTerrainAPI`——terrain 实体约定 (`getPlugin` / `getType` / `setRadius`)。
    - `com.fs.starfarer.api.EveryFrameScript`—— 引擎级每帧脚本约定 (`isDone` / `runWhilePaused` / `advance`)。
    - `com.fs.starfarer.api.impl.campaign.ids.Tags`—— 原版 tag 常量 (空间站查询用 `STATION`)。
    - `com.fs.starfarer.api.util.Misc`——`initConditionMarket`(`addPlanet` 自动挂 condition-only 市场)、`genUID`。
- 原版数据 (以游戏安装根为基准)：
    - `data/config/custom_entities.json`——custom entity 类型键 (本例复用 `stable_location`)。
    - `data/campaign/starmap.json`——`starSystemLocations` 星系坐标查询 (`initStar` 坐标变体使用)。
- 文档与示例 (以仓库根为基准)：
    - `docs/chapter9/section1/README.md`—— 引擎级脚本一次性注册判断与推进语义 (本节沿用)。

## 完整最小例

::: details 完整成品：GearInLocationScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 完整最小例：LocationAPI / StarSystemAPI 实体容器与访问状态 (日志前缀 "GearIn loc")。
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，REGISTER_MARK 一次性门控
 * (C6-S2 的 CLASS_MARK 模式)。编排：星系创建/复用、访问状态记录、容器核验与
 * 周期摘要，分别委托给 GearInLocationSystemBuilder / GearInAccessStateLogger / GearInContainerCheck。
 */
public class GearInLocationScript implements EveryFrameScript {
	/** 存档持久化标记：本脚本是否已在该存档注册过 (随存档保存)。 */
	public static final String REGISTER_MARK = "GearIn_LocationScript";
	private static final Logger LOG = Global.getLogger(GearInLocationScript.class);

	/** 状态摘要日志的最小间隔 (真实秒)。 */
	private static final float LOG_INTERVAL = 10f;

	private boolean setupDone = false;
	private boolean broken = false;
	private boolean enterDone = false;
	private float sinceLastLog = 0f;
	/** 随存档序列化的引用：演示星系与实体 (存档图内持久对象，可安全放脚本字段)。 */
	private StarSystemAPI demoSystem;
	private SectorEntityToken beacon;
	private GearInAccessStateLogger accessStateLogger;
	private GearInContainerCheck containerCheck;

	public GearInLocationScript() {
		// 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		if (!setupDone) {
			setup();
			if (broken) {
				return;
			}
		}

		LocationAPI current = Global.getSector().getCurrentLocation();
		if (accessStateLogger.logLocationChangeIfChanged(current) && current == demoSystem) {
			accessStateLogger.logEnteredDemoSystem();
		}
		if (!enterDone && demoSystem.isCurrentLocation()) {
			enterDone = true;
			containerCheck.runRemoveAddCheck();
		}

		sinceLastLog += amount;
		if (sinceLastLog >= LOG_INTERVAL) {
			sinceLastLog = 0f;
			accessStateLogger.logStateSummary();
		}
	}

	/** 首帧准备：演示星系复用或创建，取回信标引用，装配辅助角色。 */
	private void setup() {
		demoSystem = GearInLocationSystemBuilder.getOrCreate(Global.getSector());
		beacon = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		if (beacon == null) {
			LOG.error("GearIn loc: beacon not found in demo system, demos disabled");
			broken = true;
			return;
		}
		accessStateLogger = new GearInAccessStateLogger(demoSystem, beacon);
		containerCheck = new GearInContainerCheck(demoSystem, beacon);
		setupDone = true;
	}
}
```

:::

::: details 完整成品：GearInLocationSystemBuilder

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：演示星系创建 (LocationAPI / StarSystemAPI 的实体填充)，被
 * GearInLocationScript.setup 调用：星系已存在则复用，否则创建并记录日志。
 */
public class GearInLocationSystemBuilder {
	/** 演示星系查询键：SectorAPI.getStarSystem 用 optionalUniqueId 或基名小写匹配。 */
	public static final String SYSTEM_UNIQUE_ID = "GearIn_demo";
	/** 本单元专属实体 ID(与 C3-S6 的 GearIn 星系既有 ID 互不冲突)。 */
	public static final String STAR_ID = "GearIn_demo_star";
	public static final String PLANET_ID = "GearIn_demo_planet1";
	public static final String BEACON_ID = "GearIn_demo_beacon";
	public static final String BEACON_TAG = "GearIn_demo_tag";
	private static final Logger LOG = Global.getLogger(GearInLocationSystemBuilder.class);

	private GearInLocationSystemBuilder() {
		// 工具类：实例化由调用方承担 (静态入口 getOrCreate)
	}

	/** 演示星系存在则复用，否则创建；返回可用的星系。 */
	public static StarSystemAPI getOrCreate(SectorAPI sector) {
		StarSystemAPI system = sector.getStarSystem(SYSTEM_UNIQUE_ID);
		if (system != null) {
			LOG.info("GearIn loc: demo system already exists, reusing id=" + system.getId());
			return system;
		}
		return create(sector);
	}

	/** 创建演示星系并填充实体，返回创建结果。 */
	private static StarSystemAPI create(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("GearIn Demo");
		system.setOptionalUniqueId(SYSTEM_UNIQUE_ID);

		// 6 参数 initStar：同时把星系定位到超空间坐标 (7500, 8500)，并自动生成超空间锚点；
		// 7 参数 initStar 不生成锚点、不设坐标 (只查 starmap.json 的 starSystemLocations)。
		PlanetAPI star = system.initStar(STAR_ID, "star_white", 600f, 7500f, 8500f, 400f);

		// addPlanet(id, 轨道焦点, 显示名, 类型id, 轨道角, 半径, 轨道半径, 轨道周期天数)；
		// 非恒星行星会被自动挂一个 condition-only 市场 (Misc.initConditionMarket)，
		// 因此 addPlanet 之后 getMarket() 立即非 null(正式市场流程归 C10-S1)。
		PlanetAPI planet = system.addPlanet(PLANET_ID, star, "GearIn Demo Prime", "terran", 55, 150, 3200, 120);

		// 自定义实体：type 是 data/config/custom_entities.json 里的键 ("stable_location" 为原版键)，
		// factionId 缺省为 neutral；创建后实体位于 (0,0)，必须显式放置。
		SectorEntityToken beacon = system.addCustomEntity(BEACON_ID, "GearIn 演示信标", "stable_location", "neutral");
		beacon.setLocation(1400f, 900f);
		beacon.addTag(BEACON_TAG);

		// 小行星带 = terrain 实体 (默认 terrain id "asteroid_belt")，围绕行星
		system.addAsteroidBelt(planet, 40, 600, 100, 120, 160);

		// 位置 token 的创建与容器语义见 GearInTokenCreator
		GearInTokenCreator.create(system);

		// 必须调用；initStar 的带坐标变体只生成超空间锚点，不生成跳跃点
		system.autogenerateHyperspaceJumpPoints(true, false);

		LOG.info(String.format(
				"GearIn loc: created system id=%s name=%s | star=%s planet=%s | anchor=%s | hyperJumpPoints=%d | hyperLoc=(%.0f, %.0f)",
				system.getId(),
				system.getName(),
				star.getName(),
				planet.getName(),
				system.getHyperspaceAnchor() != null,
				system.getAutogeneratedJumpPointsInHyper().size(),
				system.getLocation().x,
				system.getLocation().y));
		return system;
	}
}
```

:::

::: details 完整成品：GearInTokenCreator

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：位置 token 创建 (LocationAPI.createToken 语义演示)。
 * 被 GearInLocationSystemBuilder 在填充星系时调用：createToken 初始不在实体集合里
 * (javadoc 原文)，需要轨道或被按 id 查询时，必须先 addEntity 加入容器。
 */
public class GearInTokenCreator {

	private static final Logger LOG = Global.getLogger(GearInTokenCreator.class);

	private GearInTokenCreator() {
		// 工具类：实例化由调用方承担 (静态入口 create)
	}

	/** 创建并加入位置 token，记录 createToken 语义 (初始不在实体集合中)。 */
	public static void create(StarSystemAPI system) {
		SectorEntityToken token = system.createToken(0f, 0f);
		LOG.info(String.format(
				"GearIn loc: token created | id=%s | inList before add=%s | isAlive before add=%s",
				token.getId(),
				system.getAllEntities().contains(token),
				token.isAlive()));
		token.setLocation(-2000f, -1500f);
		system.addEntity(token);
		LOG.info(String.format(
				"GearIn loc: token added | inList after add=%s | byId found=%s",
				system.getAllEntities().contains(token),
				system.getEntityById(token.getId()) != null));
	}
}
```

:::

::: details 完整成品：GearInAccessStateLogger

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：访问状态记录 (isCurrentLocation / activeThisFrame / isInCurrentLocation /
 * isAlive / isEnteredByPlayer / getDaysSinceLastPlayerVisit)，三条日志
 * (location 变化、进入演示星系、周期摘要) 由 GearInLocationScript 调用。
 */
public class GearInAccessStateLogger {
	private static final Logger LOG = Global.getLogger(GearInAccessStateLogger.class);
	private final StarSystemAPI demoSystem;
	private final SectorEntityToken beacon;
	private String lastPlayerLocationId = null;

	public GearInAccessStateLogger(StarSystemAPI demoSystem, SectorEntityToken beacon) {
		this.demoSystem = demoSystem;
		this.beacon = beacon;
	}

	/** 玩家所在 location 变化时记录访问状态；返回是否发生了变化。 */
	public boolean logLocationChangeIfChanged(LocationAPI current) {
		String currentId = current == null ? null : current.getId();
		if (lastPlayerLocationId != null && lastPlayerLocationId.equals(currentId)) {
			return false;
		}
		String oldId = lastPlayerLocationId;
		lastPlayerLocationId = currentId;
		StringBuilder sb = new StringBuilder("GearIn loc: player location change");
		if (oldId == null) {
			sb.append(" | first observed: ");
		} else {
			sb.append(" | ").append(oldId).append(" -> ");
		}
		sb.append(currentId);
		if (current != null) {
			sb.append(" [name=").append(current.getName())
					.append(", isHyperspace=").append(current.isHyperspace())
					.append(", isCurrentLocation=").append(current.isCurrentLocation())
					.append(", activeThisFrame=").append(current.activeThisFrame())
					.append("]");
		}
		LOG.info(sb.toString());
		return true;
	}

	/** 玩家刚进入演示星系：记录访问状态与实体状态。 */
	public void logEnteredDemoSystem() {
		LOG.info(String.format(
				"GearIn loc: demo system entered | isEnteredByPlayer=%s | daysSinceLastVisit=%s | beacon.isInCurrentLocation=%s | beacon.isAlive=%s",
				demoSystem.isEnteredByPlayer(),
				formatDays(demoSystem.getDaysSinceLastPlayerVisit()),
				beacon.isInCurrentLocation(),
				beacon.isAlive()));
	}

	/** 周期状态摘要：演示星系的实体集合与访问状态。 */
	public void logStateSummary() {
		LocationAPI current = Global.getSector().getCurrentLocation();
		List<SectorEntityToken> entities = demoSystem.getAllEntities();
		LOG.info(String.format(
				"GearIn loc: state | current=%s (id=%s) | demoSystem.isCurrentLocation=%s activeThisFrame=%s enteredByPlayer=%s daysSinceVisit=%s | entities=%d planets=%d terrains=%d custom=%d | beacon isAlive=%s inList=%s",
				current == null ? "null" : current.getName(),
				current == null ? "null" : current.getId(),
				demoSystem.isCurrentLocation(),
				demoSystem.activeThisFrame(),
				demoSystem.isEnteredByPlayer(),
				formatDays(demoSystem.getDaysSinceLastPlayerVisit()),
				entities.size(),
				demoSystem.getPlanets().size(),
				demoSystem.getTerrainCopy().size(),
				demoSystem.getCustomEntities().size(),
				beacon.isAlive(),
				entities.contains(beacon)));
	}

	/** Float.MAX_VALUE 表示从未访问过 (getDaysSinceLastPlayerVisit 语义)。 */
	private static String formatDays(float days) {
		if (days >= Float.MAX_VALUE) {
			return "never";
		}
		return String.format("%.1f", days);
	}
}
```

:::

::: details 完整成品：GearInContainerCheck

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：实体容器持有语义核验 (isAlive / getEntityById 与容器成员关系)。
 * 被 GearInLocationScript 在玩家首次进入演示星系时调用一次：
 * 对信标执行 removeEntity / addEntity，记录 isAlive() 与 getEntityById() 的前后变化。
 */
public class GearInContainerCheck {

	private static final Logger LOG = Global.getLogger(GearInContainerCheck.class);

	private final StarSystemAPI demoSystem;
	private final SectorEntityToken beacon;

	public GearInContainerCheck(StarSystemAPI demoSystem, SectorEntityToken beacon) {
		this.demoSystem = demoSystem;
		this.beacon = beacon;
	}

	/** 移除/重新加入核验：只执行一次，展示 isAlive 与 getEntityById 的容器持有语义。 */
	public void runRemoveAddCheck() {
		LOG.info("GearIn loc: enter demo system first time - remove/add demo");
		boolean aliveBefore = beacon.isAlive();
		SectorEntityToken byIdBefore = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);

		demoSystem.removeEntity(beacon);
		boolean aliveAfterRemove = beacon.isAlive();
		SectorEntityToken byIdAfterRemove = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		SectorEntityToken byIdSectorAfterRemove = Global.getSector().getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		boolean listContainsAfterRemove = demoSystem.getAllEntities().contains(beacon);

		demoSystem.addEntity(beacon);
		boolean aliveAfterReadd = beacon.isAlive();
		SectorEntityToken byIdAfterReadd = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);

		LOG.info(String.format(
				"GearIn loc: beacon | alive before=%s | byId before=%s | alive after remove=%s | byId after remove=%s | sector byId after remove=%s | inList after remove=%s | alive after re-add=%s | byId after re-add=%s",
				aliveBefore,
				byIdBefore != null,
				aliveAfterRemove,
				byIdAfterRemove != null,
				byIdSectorAfterRemove != null,
				listContainsAfterRemove,
				aliveAfterReadd,
				byIdAfterReadd != null));
	}
}
```

:::
