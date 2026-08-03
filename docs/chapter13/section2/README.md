# Nexerelin 软依赖接入：配置、生成与特遣队

本节讲解可选 MOD 势力争霸（Nexerelin）的接入协议，覆盖四个面：势力配置、生成分支、地面战与特遣队。Nexerelin 是可选的，齿轮工业不声明硬依赖，玩家不装它也能完整游玩；本节在软依赖的通用处理链（检测、类加载隔离与降级边界）之上补齐 Nexerelin 专用接法。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 说出 Nexerelin 势力接入的四个面与各自的注册入口：`data/config/exerelinFactionConfig/mod_factions.csv`（势力发现）、`data/config/exerelinFactionConfig/<factionId>.json`（势力配置）、`data/config/exerelin/corvus_spawnpoints.csv`（出生点）、 `data/config/exerelin/groundBattleDefs.json`（地面战），以及代码侧 `specialForcesNamerClass`（特遣队命名器）与 `SectorManager`（模式判定）；
- 逐条说出势力配置速查表里每个字段的默认值与使用方（`playableFaction` → 新游戏创建（NGC）势力目录、`marketSpawnWeight` → 随机星域程序化生成份额、`startShips*` → 开局舰队包、`specialForcesNamerClass`/`specialForcesMaxFleets` → 特遣队、`groundBattleSettings` → 地面战修正）；
- 复述 `mod_factions.csv` 与单势力 JSON 的读取链（`NexConfig.loadModFactionList` / `NexFactionConfig.loadFactionConfig`，两个入口都是 "按 MOD id 合并" 读取，MOD 只提交自己的行与自己的键），并说出 "漏写 `mod_factions.csv` 行" 的可观察结果；
- 写出生成分支的标准做法：`isModEnabled("nexerelin")` 检测 → `SectorManager.getManager()` null 守卫 → `isCorvusMode()` 分派 Corvus 模式与随机星域模式两条生成路径，并解释为什么 `onNewGame` 阶段必须守卫；
- 为齿轮工业写一份最小配置（出生点行、势力 JSON、`groundBattleDefs.json` 条目、`groundBattleSettings`），并实现一个特遣队命名器类（`GearInSpecialForcesNamer implements SpecialForcesNamer`）；
- 说出命名器的加载链（脚本类加载器按类名实例化 + 至多 25 次唯一性重试）与 "类名写错/签名不匹配/返回重复名" 三个失败形态的可观察结果；
- 从八类典型症状反推配置或代码错误：NGC 不出现、出生点不对、新档生成中断、无依赖崩溃、特遣队名字是 `error`、特遣队重名、地面战配置加载失败、地面战修正无效。

## 前置知识

本节建立在这些既有内容之上：

- 势力注册（C03-S02）：`data/world/factions/factions.csv` 与 `gear_industry.faction`——Nexerelin 配置消费的是**已注册**的势力 id。
- 新建生涯生成时序与本 MOD 的生成入口插件类（C03-S03）：`GearInSystemGenPlugin`，本节的生成分支挂在这里。
- 舰队工厂链（C08-S04）：`FleetParamsV3` 与 `FleetFactoryV3.createFleet`——Nexerelin 的特遣队舰队正是用这条工厂链生成的，本节只消费其结论。
- 软依赖完整处理链（C13-S01）：`isModEnabled(id)` 判据、类存在性判据、引用收敛进适配器、缺依赖降级。
  - 本节的 `GearInNexCompat` 就是该模式在 Nexerelin 上的实例。
- 运行期注册守卫与 MOD 插件回调时机（C08-S01、C06-S02）：`onGameLoad` 与 persistent data 守卫；`onNewGame` 只在新档触发。

## 术语

本节首次完整讲解以下词（API 名保留源码大小写）：

| 词 | 含义 |
|---|---|
| 势力争霸（Nexerelin）| 可选 MOD；`mod_info.json` 的 `id` 为 `nexerelin`。其新档流程接管原版 "新游戏创建"，增加势力选择、随机星域生成、入侵、外交、地面战与特遣队 |
| Corvus 模式（corvus mode）| Nexerelin 的 "原版星域" 模式：保留原版 Corvus 等固定星系，追加各 MOD 固定内容；对应 `SectorManager.isCorvusMode() == true` |
| 随机星域模式 | Nexerelin 的随机生成模式：星区由 `ExerelinProcGen` 程序化生成，势力市场按 `marketSpawnWeight` 分配份额；对应 `isCorvusMode() == false` |
| 新游戏创建（NGC）| Nexerelin 的开局流程界面（源码 `Nex_NGC*` 规则命令族）：选择势力、开局舰队与出生方式 |
| 势力配置（faction config）| 运行期对象 `NexFactionConfig`；数据源是 `exerelinFactionConfig/<factionId>.json`，经 `NexConfig.getFactionConfig(id)` 读取 |
| 生成点（spawn point）| `corvus_spawnpoints.csv` 的一行：势力在 Corvus 模式下的开局出生位置（`faction,system,entityID`）|
| 地面战（ground battle）| Nexerelin 的入侵地面战斗系统（`exerelin.campaign.intel.groundbattle`）；MOD 接入面是 `groundBattleDefs.json` 与势力配置的 `groundBattleSettings` |
| 特遣队（special forces）| Nexerelin 的势力精锐舰队（`SpecialForcesIntel`/`SpecialForcesManager`）；命名器 API 名保留 `SpecialForcesNamer` |

## 接入总览：先定软依赖做法

Nexerelin 的接入全部建立在此前的软依赖做法之上，三条不变：

1. **不声明**：`nexerelin` 不进 `mod_info.json` 的 `dependencies`。
  - 写了就变成硬依赖 —— 玩家不装势力争霸时齿轮工业被启动器拦截，而不是降级运行（硬依赖故障的同款现象）。
  - 成熟实例的 `mod_info.json` 中 `dependencies` 不含 `nexerelin`。
2. **检测**：`Global.getSettings().getModManager().isModEnabled("nexerelin")`——id 必须是对方 `mod_info.json` 的 `id` 字段值，逐字比较、大小写敏感。成熟实例：`NEX()` 工具方法（`isModEnabled("nexerelin")`）同构。
3. **收敛**：`exerelin.*` 的直接引用全部收敛在适配器类 `GearInNexCompat` 的守卫分支内（软依赖适配器模式）。
  - 唯一的例外是本节的特遣队命名器类 —— 它**实现** `exerelin...SpecialForcesNamer` 接口，但只被 Nexerelin 侧按配置里的类名加载，齿轮工业自己的代码从不触碰它（见 "链路五"）。

## 文件与 API 链路

### 链路一：势力配置（数据）——mod_factions.csv 与单势力 JSON

路径（均在 MOD 的 `data/config` 下；**这些文件 Nexerelin 不会自动创建，全部要自行建立**）：

```text
data/config/exerelinFactionConfig/mod_factions.csv      # 势力发现名单
data/config/exerelinFactionConfig/gear_industry.json    # 单势力配置（文件名 = 势力 id）
```

**`mod_factions.csv`** 只有一列 `faction`，第一行是注释 `# case sensitive!!!`。它声明 "哪些外部势力需要 Nexerelin 接入"：

```csv {3}
faction
# case sensitive!!!
gear_industry
```

读取链（Nexerelin 0.11.3x 官方源码，本地发行 `Starsector\mods\Nexerelin`）：

1. `NexConfig.loadModFactionList()`：`Global.getSettings().getMergedSpreadsheetDataForMod("faction", "data/config/exerelinFactionConfig/mod_factions.csv", "nexerelin")` ——**跨全部已启用 MOD 合并**（master 是 Nexerelin 自己的文件，非 master 文件按 `faction` 列追加/替换，即跨 MOD 合并语义），所以 MOD 只需要提交自己的行。
2. `NexConfig.loadSettings()`：先按 `exerelin_config.json` 的 `builtInFactions` 加载内置势力配置；再遍历 `mod_factions.csv` 名单，对每个**已注册**的势力（`NexUtilsFaction.doesFactionExist(factionId)` 校验 —— 势力必须先经势力注册页的 `factions.csv` 注册）创建 `NexFactionConfig`。
3. `NexFactionConfig.loadFactionConfig()`：`Global.getSettings().getMergedJSONForMod("data/config/exerelinFactionConfig/" + factionId + ".json", "nexerelin")` —— 同样按 MOD id 合并，**对象按键合并**，MOD 只提交自己的键，不要复制 Nexerelin 的同名文件。
4. 运行期读取入口：`NexConfig.getFactionConfig(factionId)`—— 已注册返回对应配置；**未注册返回默认配置（neutral 的配置，`playableFaction=false`），不抛异常**。

::: warning 本链路最值得警惕的是静默失败
`NexConfig.getFactionConfig(factionId)` 对**未注册**势力不抛异常，而是回退默认配置（neutral，`playableFaction=false`）。

漏写 `mod_factions.csv` 名单行、或名单行与势力 id 大小写不符时，单势力 JSON 根本不会进入加载链，所有势力级配置静默失效且日志无任何报错。
:::

**单势力 JSON** 的常用字段速查表（默认值与使用方核对自 `NexFactionConfig` 源码与数据；不在表上的字段属于不常用项目，按需参考 Nexerelin 自带 `exerelinFactionConfig/` 下的文件）：
| 键 | 默认值 | 使用方 / 语义 |
|---|---|---|
| `playableFaction` | `true` | 新游戏创建（NGC）势力目录是否可选（`Nex_FactionDirectoryHelper.getFactionsForDirectory`：`anyPlayable && config.playableFaction`）|
| `corvusCompatible` | `false` | 声明势力按 Corvus 模式布局；成熟实例全部设 `true` 并与生成点一致（见 "规则强度"）|
| `marketSpawnWeight` | `1` | 随机星域模式下该势力程序化生成市场的份额倍率（`ExerelinProcGen` 按此分配）；NGC 权重滑条也读它 |
| `colonyExpeditionChance` | `0` | 势力发起殖民远征的概率倍率（默认 0 = 不发起）|
| `colonyTargetValuator` | `"exerelin.campaign.colony.ColonyTargetValuator"` | 殖民目标估值器类名，按类名实例化（`ColonyManager`）；成品 MOD 常用 `IndustrialColonyTargetValuator` |
| `startRelationships` / `maxRelationships` / `minRelationships` | 空 map | 初始 / 最大 / 最小关系（键 = 势力 id，值 = 关系值）|
| `diplomacyPositiveChance` / `diplomacyNegativeChance` | 空 map | 与特定势力的正面 / 负面外交事件频率倍率 |
| `diplomacyTraits` | 空列表 | 势力外交特性（取值见 Nexerelin 的 `data/config/exerelin/factionTraits.json`）|
| `alignments` | 空 map | 势力倾向（外交联盟模型的倾向值）|
| `startShipsSolo` / `startShipsCombatSmall` / `startShipsCombatLarge` / `startShipsTradeSmall` / `startShipsTradeLarge` / `startShipsExplorerSmall` / `startShipsExplorerLarge` / `startShipsCarrierSmall` / `startShipsCarrierLarge` / `startShipsSuper` / `startShipsGrandFleet` | 空 | 开局舰队包：**二维数组**，内层数组是一支开局舰队的装配 id 列表（`StartFleetType` 逐键解析，`getStartShipTypeIfAvailable`）|
| `startSpecialItems` | 空 | 开局物品（通常 `[["<蓝图包 id>", ""]]` 形式）|
| `defenceStations` | 三档默认空间站 | 空间站设施集合（`[{"ids":[...]}]`）|
| `industrySeeds` | 默认含 `heavyindustry` | 殖民地自动发展时的产业种子（`{"id":..., "mult":...}`）|
| `bonusSeeds` | 空 | 殖民地开局奖励物品（`{"id":..., "count":..., "mult":...}`）|
| `specialForcesMaxFleets` | `2`（非 playable 势力取 0）| 特遣队同时存在的数量上限（`SpecialForcesManager` 读取）|
| `specialForcesSizeMult` / `specialForcesPointMult` | `1` / `1` | 特遣队规模倍率 / 点数积累倍率 |
| `specialForcesNamerClass` | `"exerelin.campaign.intel.specialforces.namer.CommanderNamer"` | 特遣队命名器类名（见 "链路五"）|
| `groundBattleSettings` | 无（null）| 该势力地面战修正（见 "链路四"）|

### 链路二：生成点（数据）——corvus_spawnpoints.csv

路径：`data/config/exerelin/corvus_spawnpoints.csv`。表头三列 `faction,system,entityID`，第二行注释说明各列含义（`#machine name,#human name,#machine name`）：

```csv {3}
faction,system,entityID
#machine name,#human name,#machine name
gear_industry,GearIn,GearIn_planet1
```

读取链：`ExerelinCorvusLocations`（静态块）用 `getMergedSpreadsheetDataForMod("faction", "data/config/exerelin/corvus_spawnpoints.csv", "nexerelin")` 全量合并读取，按 `faction` 列存表；

运行期 `ExerelinCorvusLocations.getFactionSpawnPoint(factionId)` 查询。

使用方：`StartSetupPostTimePass`（新档开局定位）——Corvus 模式下取本势力的生成点，把 `entityID` 交给 `sector.getEntityById(...)` 找到实体后 `sendPlayerFleetToLocation`。

**0.11.3x 该使用方只使用 `entityID` 列**；生成点缺失或实体找不到时依次回退 homeworld、随机市场位置，最终回退超空间（关应答机）—— 全程无报错。

### 链路三：生成分支（代码）—— 检测、null 守卫与模式分派

Nexerelin 启用后，新档的星区内容分两种模式，MOD 的固定内容必须按模式分派。标准做法是 "检测 → 守卫 → 分派"：

1. **检测**：`isModEnabled("nexerelin")`。
2. **守卫**：`SectorManager.getManager()` 返回的是 sector persistent data 里的管理器，**未创建时返回 `null`**；`isCorvusMode()` 是实例方法。
  - **必须**先判 `getManager() != null` 再调 `isCorvusMode()`（成熟实例陷阱原文："`SectorManager.getManager()` 在 manager 尚未创建时返回 `null`，紧接着调用 `isCorvusMode()` 会失败"）。
3. **分派**：
   - Corvus 模式：保留原版 Corvus 星系，MOD 固定星系照常生成；玩家出生点由 `corvus_spawnpoints.csv` 绑定。
   - 随机星域模式：星区由 `ExerelinProcGen` 接管，势力市场按 `marketSpawnWeight` 分配；MOD 通常不再生成固定星系，或只生成自己的主星系。

::: caution `isCorvusMode()` 必须带 null 守卫
`SectorManager.getManager()` 的约定是 "未创建时返回 `null`"（sector persistent data 无条目即 null）。

`isCorvusMode()` 只能在 Nexerelin 启用**且** manager 已创建之后调用；任何无守卫的直接调用 —— 包括已废弃的静态 `getCorvusMode()`—— 在 manager 未创建时都会 NPE。

适配器组合判据（启用检测在前、null 守卫在中、模式判定在后）是覆盖一切调用点的唯一安全写法。
:::
::: info 为什么新档 `onNewGame` 阶段也要守卫（时序）
新档流程里 `SectorManager` 的创建点是 Nexerelin 的**入口生成器**——

Nexerelin 的 `settings.json` 把 `newGameCreationEntryPoint` 覆盖为 `exerelin.world.ExerelinNewGameSetup`（"禁止覆盖" 警告针对普通 MOD 重写原版键，Nexerelin 接管该键是其设计）。

`ExerelinNewGameSetup.generate(sector)` 先执行：Corvus 模式跑 `VanillaSystemsGenerator` 生成原版星系，随机星域跑 `ExerelinCoreSystemGenerator`；

随后 `setupPlugins(sector)` → `ExerelinModPlugin.addScripts()` → `sector.addScript(SectorManager.create())`。

入口生成器先于 `generators.csv` 与全部 MOD 的 `onNewGame`（RC8 新档流程）—— 所以新档的 `onNewGame` 阶段 manager **已经存在**。

但 `getManager()` 的约定是 "未创建时返回 `null`"（sector persistent data 无条目即 null）：早于入口生成器的调用点（`onApplicationLoad`、启动期静态块、任何不经过 Nexerelin 新档入口的路径）照样会拿到 null。

成熟实例规范原文 ——"`isCorvusMode()` 应只在 Nexerelin 启用且 manager 已存在后调用"；陷阱原文 ——"`SectorManager.getManager()` 在 manager 尚未创建时返回 `null`，紧接着调用 `isCorvusMode()` 会失败"。

适配器统一守卫（`isNexEnabled()` && `getManager() != null`）是覆盖一切调用点的唯一安全写法。
:::

成熟实例（NEXGenerate 模式，多个 MOD 同构）：生成器类在 `generate(SectorAPI)` 里判 `SectorManager.getManager().isCorvusMode()`，Corvus 模式走完整生成、随机星域模式只生成主星系（也有实例随机星域完全不生成）；

注册在 `ModPlugin.onGameLoad` 的 persistent data 门内 ——`onGameLoad` 时 manager 必然已创建，这也是把生成从 `onNewGame` 挪到 `onGameLoad` 的原因（顺便覆盖旧档）。

齿轮工业沿用既有新建生涯生成入口（`onNewGame`），因此**必须**带 null 守卫。

### 链路四：地面战（数据）——groundBattleDefs.json 与 groundBattleSettings

Nexerelin 的入侵地面战是一个 intel 系统（`exerelin.campaign.intel.groundbattle`）。MOD 的接入面是两处数据，不需要写地面战代码（除非要自定义插件）：

**`data/config/exerelin/groundBattleDefs.json`**—— 按产业/条件/能力/兵种/插件定义地面战参数。

读取链：`GBDataManager.loadDefs()`（静态块， `getMergedJSONForMod("data/config/exerelin/groundBattleDefs.json", "nexerelin")`—— 同样跨 MOD 按键合并）。

顶层键 `industries` / `conditions` / `abilities` / `unitTypes` / `plugins` / `natoAlphabet` 全部硬读取（`getJSONObject`/`getJSONArray`）——

**顶层结构错误时直接抛 `RuntimeException("Failed to load ground battle defs", ex)`**。

`industries` 条目（键 = 产业 id，原版示例 `heavybatteries`/`grounddefenses`/`patrolhq` 等）字段：

| 字段 | 默认 | 语义 |
|---|---|---|
| `strengthMult` | `1` | 该产业的驻军强度倍率 |
| `enemyDropCostMult` | `1` | 敌方空降成本倍率 |
| `enemyBombardmentCostMult` | `1` | 敌方轰炸成本倍率 |
| `dropAttritionFactor` / `dropAttritionMult` | `0` / `1` | 空降损耗系数 / 倍率 |
| `troopCounts` | 空 | 兵种数量：键 = `unitTypes` 里的兵种 id（`militia`/`marine`/`heavy` 等），值 = 数量 |
| `tags` | 空 | 行为标签：`noBombard`（不可轰炸）、`preventBombardment`（阻止轰炸行动）、`resistBombard`（抗轰炸）、`preventBombardmentSuper`/`preventEW` 等 |
| `icon` | 无 | 地面战界面图标路径 |
| `plugin` | 无 | 产业地面战插件类名（原版 `SpaceportPlugin`/`GroundDefensesPlugin`）|

MOD 只需提交自己的键；**不要复制 Nexerelin 的文件**。

原版 `orbitalworks` 没有条目（0.11.3x 核对），本节示例就给它补一条。

**势力配置里的 `groundBattleSettings`**—— 该势力全军的地面战修正。

`NexFactionConfig.loadFactionConfig` 把它按 JSON 对象读成 map；

使用方 `FactionBonusSubplugin`（地面战 intel 每场战斗按势力实例化）识别三个键： `attackMult`（攻击倍率）、`damageTakenMult`（受击倍率）、`moraleDamageTakenMult`（士气损失倍率）；

`plugin` 键可换成自定义子插件类名。

**未知键静默忽略**。原版示例：`hegemony.json` 的 `"groundBattleSettings":{"moraleDamageTakenMult":0.9}`。

::: warning 只提交自己的键，不要复制 Nexerelin 的文件
两处数据都是跨 MOD 按键合并读取：复制 Nexerelin 的同名文件会被整体合并，产生不必要的冲突。

`groundBattleSettings` 只识别 `attackMult`/`damageTakenMult`/`moraleDamageTakenMult`/`plugin` 四个键，其余键（含拼写错误）被静默忽略 —— 键名写错时没有任何报错，需要对照可识别键名核对。
:::

### 链路五：特遣队（代码）——specialForcesNamerClass 与 SpecialForcesNamer

特遣队（special forces）是势力的精锐舰队：`SpecialForcesManager` 按点数（受 `specialForcesPointMult`/`specialForcesMaxFleets` 等配置）积累生成，

`SpecialForcesIntel` 用 `FleetParamsV3` + `FleetFactoryV3.createFleet` 造舰（舰队工厂链）、投放后命名。

命名器接入：

1. 势力 JSON 写 `"specialForcesNamerClass":"<您的类全名>"`（默认 `exerelin.campaign.intel.specialforces.namer.CommanderNamer`）。
2. 类**必须**实现接口（0.11.3x 官方源码签名）：

```java
public interface SpecialForcesNamer {
    public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander);
}
```

3. 加载链（`SpecialForcesIntel.pickFleetName`）：`Global.getSettings().getScriptClassLoader().loadClass(className)` + `newInstance()`（**无参构造硬要求**），然后循环调用 `getFleetName(fleet, origin, commander)` **至多 25 次**，直到返回的名字非 `null` 且与既有特遣队不重名（`hasDuplicateName`）；加载或调用抛任何 `Throwable` → ERROR 日志 `Failed to load special forces namer <类名>`，名字回落字面量 `error`。
4. 类名经 JSON 引用、由 Nexerelin 侧加载：齿轮工业自己的代码**从不引用**命名器类 —— 所以它是 "收敛" 规则的唯一例外，不破坏无 Nexerelin 时的启动。

::: tip 命名器返回的名字建议带随机成分
随机编号、后缀等随机成分配合 25 次唯一性重试，多个特遣队基本不重名（原版 `PlanetNamer` 同款结构）；返回确定性名字时，第二个特遣队起重名概率上升，重试耗尽后重复名会被保留。
:::

## 规则强度

**必须/禁止（RC8 + Nexerelin 0.11.3x 仍有效；违反的可观察结果写在括号里）：**

| 规则 | 依据 | 违反后的可观察结果 |
|---|---|---|
| `nexerelin` **禁止**写进 `mod_info.json` 的 `dependencies`（软依赖不声明）| 软依赖语义；成熟实例 `mod_info.json` 实证 | 玩家未装/未启用 Nexerelin 时齿轮工业被启动器拦截（硬依赖语义），失去降级运行能力 |
| `exerelin.*` 直接引用**必须**收敛在适配器（`GearInNexCompat`）守卫分支内；命名器类（实现接口）只许经 JSON 配置被 Nexerelin 加载 | 懒解析与 `Error` 冒泡 | 无 Nexerelin 时引用被解析 → `NoClassDefFoundError` 冒泡，启动或运行中止 |
| `isModEnabled` 的 id **必须**用 `mod_info.json` 的 `id` 字段值 `nexerelin`（逐字比较）| `ModManagerAPI` 逐字比较 | 检测恒 false，Nexerelin 分支永不执行，且日志查不出报错 |
| **必须**在 Nexerelin 启用**且** `SectorManager.getManager() != null` 之后才调 `isCorvusMode()` | 成熟实例规范与陷阱原文；`SectorManager.getManager()` 源码 | 早于 Nexerelin 入口生成器的调用点拿到 null → NPE（无守卫的 `getCorvusMode()` 等写法直接崩）；新档 `onNewGame` 阶段 manager 已由入口生成器创建，但约定允许 null，守卫是唯一安全写法 |
| `mod_factions.csv` **必须**列出势力 id，且与势力 id 逐字一致（文件注释原文 `# case sensitive!!!`）| `NexConfig.loadModFactionList`；`mod_factions.csv` 头注释 | 单势力 JSON 不进入加载链 → `getFactionConfig` 回退默认配置（`playableFaction=false`）→ NGC 列表不出现、所有势力级配置失效，无任何报错 |
| 势力 JSON 路径**必须**是 `data/config/exerelinFactionConfig/<factionId>.json`，文件名 = 势力 id | `NexFactionConfig.loadFactionConfig` | 路径不符 → 合并读取读不到本 MOD 的键 → 字段取默认值，静默 |
| `corvus_spawnpoints.csv` 的 `entityID` **必须**引用目标星系内已存在的实体 id（市场 id、显示名都不行）| `StartSetupPostTimePass` 消费链（`getEntityById`）| 出生点找不到实体 → 回退 homeworld / 随机位置，无报错 |
| `groundBattleDefs.json` 顶层**必须**保持对象结构（`industries`/`conditions`/`abilities`/`unitTypes`/`plugins`/`natoAlphabet`）| `GBDataManager.loadDefs` 硬读取 | 顶层键缺失或类型错 → `RuntimeException("Failed to load ground battle defs")`（静态初始化失败），地面战不可用 |
| `specialForcesNamerClass` 指向的类**必须**实现 `SpecialForcesNamer` 且可无参实例化 | `SpecialForcesIntel.pickFleetName` | 特遣队命名失败：ERROR 日志 `Failed to load special forces namer [...]`，舰队名称为字面量 `error` |
| 命名器返回 `null` 或与既有特遣队重名时会被重试（至多 25 次），重试尽后保留最后一次结果 | `SpecialForcesIntel.pickFleetName` 循环 | 确定性命名且特遣队多时，重名舰队出现（见 "故障分析" 第 6 条）|

**建议：**

- `corvusCompatible` 设 `true` 并与生成点/势力配置保持一致（成熟实例势力 JSON、Nexerelin 全部内置势力文件均设 `true`；字段默认 `false`，0.11.3x 源码中该字段仅经 `NexUtilsFaction.isCorvusCompatible` 辅助读取、无强制使用方）。
- `groundBattleSettings` 只写 Nexerelin 可识别键（`attackMult`/`damageTakenMult`/`moraleDamageTakenMult`/`plugin`，或自定义子插件消费的键）；未知键静默忽略。
- `startShips*` 保持二维数组结构（`getStartShipTypeIfAvailable` 逐内层数组读装配 id）。
- 随机星域模式的固定星系处理二选一：不生成或只生成主星系 —— 与您的势力配置一致即可，没有强制。
- 命名器返回带随机成分的名字（数字/后缀），降低撞重名概率。

## 完整最小例

在既有生成入口页的累计工程上追加（MOD 根目录 `Starsector\mods\GearIndustry`）：

```text
data\
└── config\
    ├── exerelin\
    │   ├── corvus_spawnpoints.csv              # 本节新增：Corvus 模式出生点
    │   └── groundBattleDefs.json               # 本节新增：地面战产业定义
    └── exerelinFactionConfig\
        ├── mod_factions.csv                    # 本节新增：势力发现名单
        └── gear_industry.json                  # 本节新增：势力配置
```

### 数据文件

**`data/config/exerelinFactionConfig/mod_factions.csv`**（只提交自己的行；与 Nexerelin 的文件跨来源合并）：

```csv
faction
# case sensitive!!!
gear_industry
```

**`data/config/exerelin/corvus_spawnpoints.csv`**（`entityID` = 生成入口页生成的第一颗行星的实体 id）：

```csv
faction,system,entityID
#machine name,#human name,#machine name
gear_industry,GearIn,GearIn_planet1
```
**`data/config/exerelinFactionConfig/gear_industry.json`**（只提交自己关心的键；合并读取，不复制 Nexerelin 文件）：

```json
{
	"playableFaction":true,
	"corvusCompatible":true,

	"marketSpawnWeight":1,

	"startRelationships":{
		"hegemony":0.2,
	},

	"colonyExpeditionChance":0,
	"colonyTargetValuator":"exerelin.campaign.colony.IndustrialColonyTargetValuator",

	"startShipsSolo":[
		["tempest_Attack"],
	],
	"startShipsCombatSmall":[
		["medusa_Attack", "wolf_hegemony_CS"],
	],
	"startShipsTradeSmall":[
		["mule_Starting", "hound_hegemony_Standard"],
	],

	"groundBattleSettings":{
		"attackMult":1.05,
	},

	"specialForcesNamerClass":"data.scripts.gearin.modcompat.GearInSpecialForcesNamer",

	"defenceStations":[
		{"ids":["orbitalstation", "battlestation", "starfortress"]},
	],
	"industrySeeds":[
		{"id":"orbitalworks", "mult":0.1},
	],
}
```

**`data/config/exerelin/groundBattleDefs.json`**（原版 `orbitalworks` 无条目，本节补一条 ——`troopCounts` 的键 = `unitTypes` 兵种 id，`icon` 复用 Nexerelin 自带图标路径）：

```json
{
	"industries":{
		"orbitalworks":{
			"strengthMult":1.5,
			"troopCounts":{
				"heavy":0.3,
			},
			"tags":["resistBombard"],
			"icon":"graphics/exerelin/icons/groundbattle/groundefenses.png",
		},
	},
}
```

### GearInNexCompat
```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.world.ExerelinCorvusLocations;

/**
 * Nexerelin 软依赖适配器：把对 exerelin.* 的直接引用全部收敛在本类，
 * 工程里只有本类与特遣队命名器（GearInSpecialForcesNamer，仅被 Nexerelin
 * 按配置类名加载）出现 exerelin.* 类名。
 *
 * 调用链（软依赖组合判据）：
 *   isModEnabled 判定 MOD 启用状态 -> SectorManager.getManager() 判管理器
 *   是否已创建（null 守卫）-> isCorvusMode() 判当前模式。
 * NexConfig.getFactionConfig 读取本 MOD 势力配置（经 mod_factions.csv 注册）；
 * ExerelinCorvusLocations 读取本 MOD 的出生点行（corvus_spawnpoints.csv）。
 * Nexerelin 未启用时全部回落降级值，不抛异常。
 */
public class GearInNexCompat {

    /** Nexerelin 的 MOD id，与 Starsector\mods\Nexerelin\mod_info.json 的 id 字段逐字一致。 */
    public static final String NEX_MOD_ID = "nexerelin";

    /** 本 MOD 势力 id（教程势力章节注册）。 */
    public static final String GEAR_FACTION_ID = "gear_industry";

    private GearInNexCompat() {
    }

    /** 判据一：Nexerelin 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isNexEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    /**
     * 组合判据：Nexerelin 已启用、SectorManager 已创建且处于 Corvus 模式。
     * SectorManager.getManager() 在 manager 尚未创建时返回 null（sector
     * persistent data 中无条目）—— 直接调用 isCorvusMode() 会 NPE；
     * manager 由 Nexerelin 的入口生成器创建，早于它的调用点必须靠这里守卫。
     */
    public static boolean isNexCorvusMode() {
        if (!isNexEnabled()) {
            return false;
        }
        SectorManager manager = SectorManager.getManager();
        return manager != null && manager.isCorvusMode();
    }

    /**
     * 读取本 MOD 势力配置的 marketSpawnWeight（随机星域模式的市场份额倍率）。
     * NexConfig.getFactionConfig 未注册时回退默认配置（neutral），不抛异常 ——
     * "读到的不是自己的配置"是该 API 的静默失败模式（mod_factions.csv 漏写时
     * playableFaction 读回 false）。
     */
    public static float getFactionMarketSpawnWeight(String factionId) {
        if (!isNexEnabled()) {
            return 1f;
        }
        return NexConfig.getFactionConfig(factionId).marketSpawnWeight;
    }

    /**
     * 读取本 MOD 势力在 corvus_spawnpoints.csv 里的生成点实体 id。
     * 漏写行时返回 null—— 玩家出生点回退 homeworld / 随机位置，无报错。
     */
    public static String getFactionSpawnPointEntityId(String factionId) {
        if (!isNexEnabled()) {
            return null;
        }
        ExerelinCorvusLocations.SpawnPointEntry entry = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
        return entry == null ? null : entry.entityId;
    }
}
```

对应关系：

- `isNexEnabled()`：`isModEnabled` 判据，id 用 `nexerelin` 逐字值（软依赖判据语义）。
- `isNexCorvusMode()`：三连环 —— 启用判据在前、`getManager()` null 守卫在中、`isCorvusMode()` 在后。
  - 这是 "生成分支" 的唯一放行条件，也是成熟实例陷阱的对应实现。
- `getFactionMarketSpawnWeight(...)` / `getFactionSpawnPointEntityId(...)`：两个 "读回" 接口，把本节的数据文件（势力 JSON、出生点 CSV）与 Nexerelin 的消费结果连成完整流程：本节的数据文件 → Nexerelin 加载 → 适配器读回。未启用时回落 `1f` / `null`（软依赖降级边界）。
- 全部 `exerelin.*` 引用位于方法体内、守卫分支内：Nexerelin 缺失时符号引用永不解析（懒解析），`NoClassDefFoundError` 不会出现。

### GearInSpecialForcesNamer
```java
package data.scripts.gearin.modcompat;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;

/**
 * 特遣队命名器：实现 Nexerelin 的 SpecialForcesNamer 接口。
 * 注册方式：gear_industry.json 的 specialForcesNamerClass 指向本类；
 * 由 Nexerelin 在生成特遣队时经脚本类加载器 loadClass + newInstance
 * （无参构造）后调用 getFleetName，至多重试 25 次直到名字不重复。
 *
 * 命名形如 "1st GearIn Prime Forgeguard"：以起源市场命名，编号随机，
 * 降低与既有特遣队撞名概率；origin 缺失时回退势力首个市场，再回退 "GearIn"。
 * 本类只被 Nexerelin 侧加载，齿轮工业自己的代码从不引用它。
 */
public class GearInSpecialForcesNamer implements SpecialForcesNamer {

    private static final Random RANDOM = new Random();

    @Override
    public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
        MarketAPI market = origin;
        if (market == null) {
            List<MarketAPI> markets = Misc.getFactionMarkets(fleet.getFaction());
            market = markets.isEmpty() ? null : markets.get(0);
        }
        String marketName = market == null ? "GearIn" : market.getName();

        int num = RANDOM.nextInt(33) + 1;
        return num + ordinalSuffix(num) + " " + marketName + " Forgeguard";
    }

    private String ordinalSuffix(int num) {
        if (num >= 11 && num <= 13) {
            return "th";
        }
        switch (num % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }
}
```

对应关系：

- `implements SpecialForcesNamer`：接口签名 `String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander)`（0.11.3x 官方源码）；`origin` 是特遣队起源市场，`fleet` 是刚建好的特遣队舰队（`FleetFactoryV3` 产物，来自舰队工厂链）。
- 名字 = 随机编号（1–33）+ 序数后缀 + 市场名 + `Forgeguard`：随机成分配合 Nexerelin 的 25 次唯一性重试，多个特遣队基本不重名（原版 `PlanetNamer` 同款结构）。
- `Misc.getFactionMarkets(fleet.getFaction())`：`com.fs.starfarer.api.util.Misc` 的公开工具（`FactionAPI` 没有市场列表方法）；`origin`/市场全缺失时回退字面量 `GearIn`（不返回 null—— 返回 null 会被当作 "无效名字" 重试 25 次后保留 null，名字显示异常）。
- 无参构造（默认构造器）：`pickFleetName` 的 `newInstance()` 硬要求。
- 本类引用 `exerelin.*` 是 "收敛" 规则的例外：它实现接口、只被 Nexerelin 按配置加载，齿轮工业自己的代码不 import 它 —— 无 Nexerelin 时本类永远不会被加载。

### 接入片段（在既有生成入口插件中改造 onNewGame）
```java
    // 在既有生成入口插件的 onNewGame() 内新增本节生成分支。
    // 原实现直接调用 new GearInSystemGenerator().generate(Global.getSector())，
    // 本节把它改成按 Nexerelin 模式分派；seedRelations 照旧。
    @Override
    public void onNewGame() {
        if (GearInNexCompat.isNexCorvusMode()) {
            // Corvus 模式：固定星系照常生成；出生点由 corvus_spawnpoints.csv 绑定
            new GearInSystemGenerator().generate(Global.getSector());
            Global.getLogger(GearInSystemGenPlugin.class).info(String.format(
                "GearIn Nexerelin: corvus mode; spawnEntity=%s marketSpawnWeight=%.2f",
                GearInNexCompat.getFactionSpawnPointEntityId(GearInNexCompat.GEAR_FACTION_ID),
                GearInNexCompat.getFactionMarketSpawnWeight(GearInNexCompat.GEAR_FACTION_ID)));
        } else if (GearInNexCompat.isNexEnabled()) {
            // 随机星域模式：星区由 Nexerelin 程序化生成接管（marketSpawnWeight 分配份额），
            // 不生成固定星系
            Global.getLogger(GearInSystemGenPlugin.class).info(
                "GearIn Nexerelin: random sector mode; fixed system skipped, procgen markets only");
        } else {
            // 无 Nexerelin：原版生成，降级运行
            new GearInSystemGenerator().generate(Global.getSector());
        }
        seedRelations(Global.getSector());
    }
```

`GearInSystemGenPlugin` 类归生成入口页所有，本节只演示调用，不改动那个文件；fixture 不含此片段（fixture 只含本节所有的 `GearInNexCompat` 与 `GearInSpecialForcesNamer`）。

### 构建

```text
gradle jar
```

编译注意事项：适配器与命名器持有 `exerelin.*` 的编译期引用，**编译期**类路径上必须有 Nexerelin 的 `ExerelinCore.jar`；运行期没有则走降级（`isNexEnabled()` 为 false，分支不执行），不影响启动。

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/modcompat/GearInNexCompat.class` 与 `data/scripts/gearin/modcompat/GearInSpecialForcesNamer.class`（可用 `jar tf` 核对）。

### 验证方案（基于本例）

前置数据：势力注册页（C03-S02）的 `gear_industry` 势力、生成入口页（C03-S03）的 `GearInSystemGenPlugin`/`GearInSystemGenerator`（含本节接入片段改造）、本节四个数据文件与两个 Java 类；

本机已安装 Nexerelin 0.11.3x（`Starsector\mods\Nexerelin`）。

操作：

1. fixture 编译：Git Bash 执行 `gradle -p tutorial-fixtures compileC13s02Java --no-daemon`，确认 `BUILD SUCCESSFUL`（退出码 0）。
2. 游戏构建：`gradle jar` 成功；`jar tf jars\GearIndustry.jar` 含两个 `.class`。
3. 降级路径：不勾选 Nexerelin 新档 ——`GearIn` 星系照常出现，无 `GearIn Nexerelin:` 日志、无 ERROR。
4. Corvus 路径：勾选 Nexerelin，新档选 Corvus 模式并选择齿轮工业开局 ——NGC 目录出现齿轮工业；出生在 `GearIn_planet1`；日志出现 `GearIn Nexerelin: corvus mode; spawnEntity=GearIn_planet1 marketSpawnWeight=1.00`。
5. 随机星域路径：新档选随机星域模式（不开 Corvus）—— 日志出现 `random sector mode` 行；无 `GearIn` 星系；齿轮工业市场由程序化生成分布（份额受 `marketSpawnWeight` 影响，可把值改大/改小对比）。
6. 特遣队：持续游玩至齿轮工业特遣队出现 —— 舰队名形如 `1st GearIn Prime Forgeguard`；出现两个以上特遣队时不重名；日志无 `Failed to load special forces namer`。
7. 地面战：入侵齿轮工业市场并触发地面战 ——`orbitalworks` 的 `strengthMult`/`heavy` 兵种/`resistBombard` 生效，`attackMult=1.05` 出现在修正列表；日志无 `Failed to load ground battle defs`。
8. 故障注入（逐项做完恢复）：(a) 删 `mod_factions.csv` 的 `gear_industry` 行，重启新档：NGC 无齿轮工业、无报错（对应故障 1）；(b) 把 `corvus_spawnpoints.csv` 的 `entityID` 改成 `GearIn_planet1` 的显示名或市场 id：出生点回退、无报错（故障 2）；(c) 把 `isNexCorvusMode()` 换成无守卫的 `SectorManager.getManager().isCorvusMode()`：新档生成 NPE 中断（故障 3，改回恢复）；(d) 把 `specialForcesNamerClass` 改成不存在的类：特遣队名字为 `error` + ERROR 日志（故障 5）；(e) 把命名器返回固定字符串：两个特遣队后重名（故障 6，改回恢复）；(f) 把 `groundBattleDefs.json` 的 `industries` 改成数组：地面战配置加载失败（故障 7，改回恢复）。

通过信号：操作 1–7 全部符合；操作 8 的注入与恢复都符合预期。

清理：恢复全部注入；发布前确认 `mod_info.json` 的 `dependencies` 不含 `nexerelin`；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。
## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行：

    ```text
    gradle -p tutorial-fixtures compileC13s02Java --no-daemon
    ```

    以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **无 Nexerelin（降级路径）**：启动器不勾选 Nexerelin，新档生成照旧 ——`GearIn` 星系出现（原生成入口路径），日志无 `GearIn Nexerelin:` 行、无 `ERROR`；`GearInSpecialForcesNamer` 类不被加载（名字不出现在任何日志）。
3. **有 Nexerelin + Corvus 模式**：NGC 界面势力目录出现 "齿轮工业"（`playableFaction=true`）；选择齿轮工业开局，玩家出生在 `GearIn_planet1`（`corvus_spawnpoints.csv`）；新档日志出现：

    ```text
    GearIn Nexerelin: corvus mode; spawnEntity=GearIn_planet1 marketSpawnWeight=1.00
    ```

    且 `GearIn` 星系照常存在。
4. **有 Nexerelin + 随机星域模式**：新档日志出现 `GearIn Nexerelin: random sector mode; fixed system skipped, procgen markets only`；`GearIn` 星系不存在（预期行为）；齿轮工业的市场按 `marketSpawnWeight=1` 参与程序化生成分配。
5. **特遣队**：玩到齿轮工业的特遣队出现（势力情报界面）—— 舰队名形如 `1st GearIn Prime Forgeguard`（命名器输出，`origin` = 起源市场名）；多个特遣队不重名；`starsector.log` 无 `Failed to load special forces namer`。
6. **地面战**：入侵齿轮工业市场并进入地面战时，`orbitalworks` 产业条目生效（`strengthMult=1.5`、`heavy 0.3` 兵种、`resistBombard` 标签在对应产业上可见），势力修正 `attackMult=1.05` 出现在地面战修正列表；日志无 `Failed to load ground battle defs`。

静态自查：四个数据文件的路径与键、`specialForcesNamerClass` 类名、`GEAR_FACTION_ID` 与势力注册页的势力 id、`GearIn_planet1` 与生成入口页的实体 id 逐字一致；

Java 源码为 UTF-8 无 BOM、CRLF 换行；没有把工作单元编号写进任何运行时名称（类名、包名、配置键、日志前缀均不含工作单元编号变体）。

## 实例驱动故障分析

1. **NGC 势力目录里没有齿轮工业**。
  - 触发：`mod_factions.csv` 漏写 `gear_industry` 行、大小写不符（文件注释 `# case sensitive!!!`），或势力未在 `factions.csv` 注册（势力注册页）。
  - 可观察：`NexConfig.getFactionConfig("gear_industry")` 回退默认配置（neutral，`playableFaction=false`）——NGC 不出现、 `marketSpawnWeight` 等全部势力配置失效，**无任何报错**（成熟实例陷阱原文："mod_factions.csv 遗漏势力时，单势力 JSON 不会进入 Nexerelin 外部势力发现链路"）。
  - 修正：核对名单行与势力 id 逐字一致；确认势力已注册。
2. **选择了齿轮工业开局，但出生点不对（在随机位置/超空间）**。
  - 触发：`corvus_spawnpoints.csv` 漏写行，或 `entityID` 写成了市场 id / 行星显示名 / 不存在的 id。
  - 可观察：`getFactionSpawnPoint` 返回 null 或 `getEntityById` 找不到 → 依次回退 homeworld、随机市场、超空间，无报错。
  - 修正：`entityID` 用实体 id（生成入口页的 `GearIn_planet1`）。
3. **NPE：`SectorManager.getManager()` 返回 null 后直接调 `isCorvusMode()`**。
  - 触发：在早于 Nexerelin 入口生成器的调用点（`onApplicationLoad`、启动期静态块、任何未走 Nexerelin 新档入口的路径）直接 `SectorManager.getManager().isCorvusMode()`；或用了已废弃的静态 `SectorManager.getCorvusMode()`（同样无守卫）。
  - 可观察：manager 尚未创建（persistent data 无条目）→ NPE。注意新档 `onNewGame` 阶段 manager 已由 Nexerelin 入口生成器创建（`ExerelinNewGameSetup.generate` → `setupPlugins` → `addScripts`），但 `getManager()` 的约定允许返回 null—— 守卫是唯一安全写法。
  - 修正：一律经 `GearInNexCompat.isNexCorvusMode()`（启用判据 + null 守卫在前）。
4. **无 Nexerelin 时启动/运行崩溃，`NoClassDefFoundError: exerelin/...`**。
  - 触发：`exerelin.*` 引用泄漏到适配器之外 —— 字段初始化、静态块、构造器或未检测的方法体（如直接把 `NexConfig` 用在 `GearInSystemGenerator` 里）。
  - 可观察：`Error` 冒泡（脚本类加载器加载脚本实例时只包装 `Exception`），启动或运行中止。
  - 修正：全工程搜索 `exerelin` 引用，除 `GearInNexCompat` 与命名器外一律移除。
5. **特遣队舰队名显示为 `error`，日志有 `Failed to load special forces namer [...]`**。
  - 触发：`specialForcesNamerClass` 类名拼错/包名错、类没实现 `SpecialForcesNamer`、没有无参构造、`getFleetName` 抛异常。
  - 可观察：`pickFleetName` catch `Throwable` → ERROR 日志 + 名字回落字面量 `error`（成熟实例陷阱原文："实现方法签名不匹配时，特遣队命名会在接口调用时失败"）。
  - 修正：类名与配置逐字一致；实现接口；保留无参构造。
6. **多个特遣队重名**。
  - 触发：命名器返回确定性名字（如固定市场名 + 固定后缀）。
  - 可观察：第二个特遣队起，`hasDuplicateName` 连续为真，25 次重试用尽后保留重复名。
  - 修正：名字带随机成分（本节示例的随机编号 + 序数后缀）。
7. **地面战配置加载失败，日志含 `Failed to load ground battle defs`**。
  - 触发：`groundBattleDefs.json` 顶层结构错误 ——`industries` 写成数组、顶层键缺失或类型错误。
  - 可观察：`GBDataManager` 静态初始化抛 `RuntimeException`（`loadDefs` 硬读取），地面战系统不可用。
  - 修正：保持对象结构，只提交自己的键。
8. **地面战修正无效（`attackMult` 等不生效）**。
  - 触发：`groundBattleSettings` 里的键名写错（如 `attackMult` 写成 `attack_mult`），或写进了别的 JSON 层级。
  - 可观察：未知键被静默忽略（`FactionBonusSubplugin` 只按 `attackMult`/`damageTakenMult`/`moraleDamageTakenMult`/`plugin` 读取），无报错。
  - 修正：对照可识别键名；自定义键必须配合自定义 `plugin` 子插件消费。
9. **随机星域模式里找不到固定星系，误以为生成失败**。
  - 触发：对模式分支语义不清楚。
  - 可观察：这是本节示例的**预期行为**（`marketSpawnWeight` 份额 + 程序化生成接管）；日志有 `random sector mode` 行。
  - 修正：理解分支；若需要固定星系，改用 "随机星域也生成主星系" 的变体。
10. **`corvusCompatible` 设为 false 后 "无法继续兼容" 的旧说法不成立**。
  - 触发：按旧资料把 `corvusCompatible` 当成硬开关。
  - 可观察：0.11.3x 源码中该字段仅经 `NexUtilsFaction.isCorvusCompatible` 辅助读取，没有内置强制使用方；成熟实例全部设 `true`。
  - 修正：设 `true` 并与生成点/势力配置保持一致（建议级，见 "规则强度"）。
11. **同时启用两套生成（Nexerelin 分支与既有原实现都在跑）**。
  - 触发：`onNewGame` 里既有本节分支又保留旧的直接调用。
  - 可观察：Corvus 模式下星系/实体被生成两遍，实体 id 冲突静默绑定到后注册者（既有生成入口故障分析的同款语义）。
  - 修正：分支互斥（if / else if / else 三路只走一路）。

## 总结

Nexerelin 接入可以收成一张四链路图：

- **势力配置（数据）**：`mod_factions.csv`（发现名单，`# case sensitive!!!`）→ 单势力 JSON（`exerelinFactionConfig/<factionId>.json`，按 MOD id 合并）→ `NexConfig.getFactionConfig(id)` 运行期读取。漏写名单行 = 全部配置静默回退默认。
- **生成分支（代码）**：`isModEnabled("nexerelin")` → `SectorManager.getManager()` null 守卫 → `isCorvusMode()` 分派。
  - Corvus 模式保留固定星系 + 出生点由 `corvus_spawnpoints.csv` 绑定；随机星域模式由 `ExerelinProcGen` 接管、`marketSpawnWeight` 分配份额。
  - manager 由 Nexerelin 的入口生成器创建，`getManager()` 约定允许 null，守卫必须存在。
- **地面战（数据）**：`groundBattleDefs.json` 的 `industries` 条目（`strengthMult`/`troopCounts`/`tags`/`icon`/`plugin`）+ 势力配置的 `groundBattleSettings`（`attackMult`/`damageTakenMult`/`moraleDamageTakenMult`/`plugin`）。结构错误 = `Failed to load ground battle defs`；未知键静默。
- **特遣队（代码）**：`specialForcesNamerClass` 指向实现 `SpecialForcesNamer` 的无参类，Nexerelin 经脚本类加载器实例化并至多 25 次重试去重；失败形态是名字 `error` + ERROR 日志。命名器是 "引用收敛" 的唯一例外（由对方加载、经 JSON 引用）。

四条链路共用同一个软依赖框架：不声明 `dependencies`、`isModEnabled` 检测、引用收敛进 `GearInNexCompat`、缺依赖降级且降级可观测。

这个框架是通用的：任何可选 MOD 的接入都可以按「检测 → 收敛 → 降级」三段复刻，并与硬依赖声明（启动器拦截）形成可选 / 必需两种接入强度的完整对照 —— 接入面越靠数据（CSV / JSON），缺依赖时的降级越容易，这正是势力配置、生成点与地面战三条数据链路比生成分支、特遣队两条代码链路更 "轻" 的原因。

## 建议参考项

- `com.fs.starfarer.api.Global` / `com.fs.starfarer.api.SettingsAPI` / `com.fs.starfarer.api.ModManagerAPI`—— 启用检测入口（`getModManager` / `isModEnabled` 逐字比较）
- `com.fs.starfarer.api.util.Misc`——`getFactionMarkets(FactionAPI)` 等公开工具
- `com.fs.starfarer.api.campaign.CampaignFleetAPI` / `com.fs.starfarer.api.campaign.econ.MarketAPI` / `com.fs.starfarer.api.characters.PersonAPI`—— 命名器接口三参
- `exerelin.campaign.SectorManager`——`getManager()` 可空约定与 `isCorvusMode()` 模式判定
- `exerelin.utilities.NexConfig` / `exerelin.utilities.NexFactionConfig`—— 势力配置加载与运行期读取（`getFactionConfig`）
- `exerelin.world.ExerelinCorvusLocations`—— 出生点查询（`getFactionSpawnPoint`）
- `exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer`—— 特遣队命名接口
- `exerelin.campaign.intel.specialforces.SpecialForcesIntel`—— 命名器加载链（类名实例化 + 至多 25 次去重重试）
- `exerelin.campaign.intel.groundbattle.GBDataManager`—— 地面战定义加载
- `exerelin.campaign.intel.groundbattle.plugins.FactionBonusSubplugin`——`groundBattleSettings` 可识别键的使用方
- `data/config/exerelinFactionConfig/mod_factions.csv`—— 势力发现名单（唯一列 `faction`，注释 `# case sensitive!!!`）
- `data/config/exerelinFactionConfig/gear_industry.json`—— 单势力配置（文件名 = 势力 id）
- `data/config/exerelin/corvus_spawnpoints.csv`——Corvus 模式出生点（`faction,system,entityID`）
- `data/config/exerelin/groundBattleDefs.json`—— 地面战产业定义
- `data/scripts/gearin/modcompat/GearInNexCompat`—— 本节适配器类（`exerelin.*` 引用收敛点）
- `data/scripts/gearin/modcompat/GearInSpecialForcesNamer`—— 本节命名器类（仅被 Nexerelin 按配置类名加载）
