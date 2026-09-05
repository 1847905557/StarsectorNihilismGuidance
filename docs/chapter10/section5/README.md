# 修改势力关系与货舱

本节读取并修改势力关系与货舱内容。示例会组合使用两类对象，让关系变化与货物增减在同一段生涯逻辑中生效。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~18 分钟。*

## 学习目标

完成后，您将能够：

- 解释两个服务入口的进入方式：
    - `SectorAPI.getFaction(id)` / `getAllFactions()` / `getPlayerFaction()`；
    - `SectorEntityToken.getCargo()`(玩家舰队 `CampaignFleetAPI.getCargo()`、子市场 `SubmarketAPI.getCargo()`)；
- 区分 `FactionAPI` 的分组 (身份、显示与颜色、关系、资源集合、经济字段、随机人物、doctrine/production/spec) 与关系语义：
    - 全局对表、`-1..1` 钳位、`adjustRelationship` 直接加、`setRelationship` 直写；
    - `isHostileTo` 表示关系等级 ≤ `HOSTILE`，经 `getRelToPlayer()` 包装；
- 解释 `CargoAPI` 的栈模型 (stack 集合 + credits + 容量统计)、四类写入入口 (资源 / 武器 / 战机芯片 / 特殊物品) 与 `removeItems` 的 NULL 占位栈语义，并遵守 `getMothballedShips()` 必须先 `initMothballedShips(factionId)` 的必须规则；
- 从症状定位故障，并沿**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置复核 (沿用本节示例的任务链)。

## 前置知识

- [C7-S1] 的 `modPlugin` 生命周期与持久数据标记；
- [C7-S2] 的 `Global.getSector()` 阶段语义与生涯引擎建立边界；
- [C8-S3] 的普通字段与 `MutableStat` 的区别；
- [C10-S1] 的 `SectorAPI` 星区入口、`onGameLoad` 注册与 `REGISTER_MARK` 判断；
- [C10-S3] 的玩家舰队、成员、`FleetDataAPI.getMothballedShips()` 与 `CampaignFleetAPI.getCargo()`；
- [C11-S3] 的子市场 cargo 创建时机、`getCargoNullOk()` 判空与 `Commodities` 商品 ID；
- 理解 `FactionAPI.getTariffFraction()` 读取势力规格中的普通数值。

## 术语

按术语约定，本节首次完整讲解两个词；API 类名保留源码大小写：

- **势力 (faction)**：API 名保留 `FactionAPI`。一个身份完整的派系对象：id、显示名、颜色、关系表、已知资源集合、随机人物与 `.faction` 规格。玩家自身也是一个势力 (`Factions.PLAYER`)。
- **货物 (cargo)**：API 名保留 `CargoAPI`。实体 (舰队、战利品、子市场、市场) 持有的货物容器：栈集合 + 信用 (credits)+ 容量统计。单个货物条目是 `CargoStackAPI`(栈)。

## 梳理势力货舱关系

### 服务入口速查表

两个入口的进入方式与核心接口先列成总表：

| 服务 | 进入方式 | 核心接口 |
|---|---|---|
| 势力 | `Global.getSector().getFaction(id)` / `getAllFactions()` / `getPlayerFaction()` | `FactionAPI` |
| 货物 | `SectorEntityToken.getCargo()`(玩家舰队 `CampaignFleetAPI.getCargo()` 经 `SectorEntityToken` 实现；子市场 `SubmarketAPI.getCargo()` 见前置知识 [C11-S3]) | `CargoAPI` / `CargoStackAPI` |

### 势力 (FactionAPI)

`FactionAPI`(`com.fs.starfarer.api.campaign.FactionAPI`) 是纯接口，势力集合由引擎统一管理。

### FactionAPI 成员分组速查表

成员按功能分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()`、`getDisplayName()`、`getDisplayNameWithArticle()`、`getDisplayNameIsOrAre()` | 势力 id(全局唯一，与 `.faction` 文件 id 一致) 与显示名变体 |
| 颜色 | `getColor()`、`getBaseUIColor()`、`getGridUIColor()`、`getDarkUIColor()`、`getSecondaryUIColor()`、`getBrightUIColor()`、`getRelColor(id)` / `getRelColor(RepLevel)` | UI 配色族；`getRelColor` 按关系值混合敌我色 |
| 关系 | `getRelationship(id)`、`getRelationshipLevel(id)`、`setRelationship(id, float)`、`setRelationship(id, RepLevel)`、`adjustRelationship(id, delta)`、`adjustRelationship(id, delta, limit)`、`ensureAtBest`、`ensureAtWorst`、`isAtWorst` / `isAtBest`、`isHostileTo`、`getRelToPlayer()` | 关系全局对表的读写、钳位、直接加/直写与敌对判定 |
| 资源集合 | `getKnownShips()`、`addKnownShip`、`removeKnownShip`、`knowsShip`、`getKnownWeapons()`、`getKnownFighters()`、`getKnownIndustries()`、priority 族、`clearShipRoleCache()` | 已知蓝图与优先资源；`removeKnown*` 永久移除需持久化清单并在读档后重新移除 |
| 经济字段 | `getTariffFraction()`、`getTollFraction()`、`getFineFraction()`、`getInternalCommsChannel()`、`isIllegal(String)`、`getIllegalCommodities()`、`makeCommodityIllegal/Legal` | 势力级税率 (注意：静态值，非 `MutableStat`) 与商品合法性 |
| 随机与显示 | `createRandomPerson()`、`getLogo()`、`getCrest()`、`isShowInIntelTab()` / `setShowInIntelTab`、`getCustom()`、`getCustomBoolean`、`getCustomString`、`getRank`、`getPost`、`pickRandomShipName` | `.faction` 文件数据与随机生成 |
| 舰船选择 | `pickShip(role, params, ...)`、`pickShipAndAddToFleet`、`ShipPickParams` / `ShipPickMode`、`getNumAvailableForRole` | 自动装配舰船选择，本节只给入口 |
| 配置 | `getDoctrine()`、`getProduction()`、`getFactionSpec()`、`getMemory()`、`getMemoryWithoutUpdate()`、`isNeutralFaction()`、`isPlayerFaction()` | doctrine / production / 规格与势力 memory |

**关系语义 (全局对表)**：

1. 关系存储在一张**全局对表**里：键 `idA_idB` 与 `idB_idA` 共享同一个对条目，新对默认 `0.0`(同势力对默认 `1.0`)；写入时把值钳位到 `[-1, 1]`。
    - 因此 `getRelationship(id)` 读到的是 "该势力视角下与目标势力" 的值，读取未建立的对会**用到时才创建**默认 0 的对条目。
2. `getRelationship(id)` 直接返回对表值；`getRelationshipLevel(id)` = `RepLevel.getLevelFor(值)`。
    - `RepLevel` 阈值：`T1=0.09`、`T2=0.24`、`T3=0.49`、`T4=0.74`；`getMin()` 不包含边界、`getMax()` 包含边界。
3. `adjustRelationship(id, delta)` **直接相加**并经 `setValue` 钳位，不触发任何监听；`adjustRelationship(id, delta, limit)` 按 `limit` 等级封顶/封底 (正方向默认 `COOPERATIVE`、负方向默认 `VENGEFUL`)。
    - `setRelationship(id, float)` 直写；`setRelationship(id, RepLevel)` 写到等级边界：`NEUTRAL` 写 0、正级写 `getMin()+0.01`、负级写 `-getMin()-0.01`。
4. `isHostileTo(id)` = `isAtBest(id, HOSTILE)`：关系等级 ≤ `HOSTILE` 即敌对。
5. 玩家视角包装：`getRelToPlayer()` 返回 `RelationshipAPI`(`com.fs.starfarer.api.characters.RelationshipAPI`：`getRel()`、`getLevel()`)；玩家势力对玩家自己按同势力默认取 `1.0`。至少在这张对表里，每个势力与自己永远是满分关系。
6. **玩家声望 action 的正规路径**是 `SectorAPI.adjustPlayerReputation(action, factionId/person)`("This method will pick the highest-priority ReputationActionResponsePlugin and call its handlePlayerReputationAction() method"；原版 `AdjustRep` rulecmd 同款调用，`com.fs.starfarer.api.impl.campaign.rulecmd.AdjustRep`)。
    - `FactionAPI` 的关系写入是**原始写入**，不经过 response plugin 的 delta 计算，也不产生声望监听通知。

### 货物 (CargoAPI)

势力入口管身份与关系，货物的存取则归 `CargoAPI`。与势力不同，货物容器不挂在星区根对象上，而是挂在实体上 —— 玩家舰队、战利品、子市场和市场各自持有自己的容器。

`CargoAPI`(`com.fs.starfarer.api.campaign.CargoAPI`) 是纯接口，结构分三块：

- **栈集合**：`stacks` 字段，`getStacksCopy()` 返回副本；
- **信用**：`credits` 字段，`getCredits()` 返回 `MutableValue`(`com.fs.starfarer.api.util.MutableValue`)：`get()` / `set()` / `add()` / `subtract()`；
- **容量统计**：`maxCapacity` / `maxFuel` / `maxPersonnel` / `spaceUsed`。

### CargoAPI 读写分族速查表

读写分族 (方法均位于 `CargoAPI`)：

| 分族 | 写入 | 读取 | 底层 |
|---|---|---|---|
| 补给 / 燃料 | `addSupplies` / `removeSupplies`、`addFuel` / `removeFuel` | `getSupplies` / `getFuel` | 资源栈 `"supplies"` / `"fuel"`；`getSupplies` 另含 `extraSuppliesUsed` |
| 人员 | `addCrew` / `removeCrew`、`addMarines` / `removeMarines` | `getCrew` / `getMarines` / `getTotalCrew` / `getTotalPersonnel` | 资源栈 `"crew"` / `"marines"`(整数语义；`crew` 写入触发携带舰队 `setSyncNeeded`) |
| 通用商品 | `addCommodity` / `removeCommodity` | `getCommodityQuantity` | `addItems(RESOURCES, id, qty)` |
| 武器 | `addWeapons` / `removeWeapons` | `getNumWeapons` / `getWeapons` | `WEAPONS` 栈 (整数、roundSize) |
| 战机芯片 | `addFighters` / `removeFighters` | `getNumFighters` / `getFighters` | `FIGHTER_CHIP` 栈 |
| 船插 / 特殊物品 | `addHullmods` / `addSpecial` | `getQuantity` | `SPECIAL` 栈 (`SpecialItemData`；船插 data = `new SpecialItemData("modspec", hullModId)`) |
| mothballed ships | `addMothballedShip` | `getMothballedShips` | 读取前必须先调 `initMothballedShips(factionId)`；`addMothballedShip` 自动初始化 |
| 通用汇总 | `addItems` / `removeItems`(返回是否完成) | `getQuantity` / `getStacksCopy` | 栈模型 |

**栈语义 (`CargoStackAPI`，`com.fs.starfarer.api.campaign.CargoStackAPI`)**：`getType()`(`CargoItemType`：`RESOURCES` / `WEAPONS` / `FIGHTER_CHIP` / `SPECIAL` / `NULL`)、`getData()`(通常是 String 资源 id)、`getSize()` / `getFree()` / `getMaxSize()` / `isFull()`、`getCargoSpace()`。

`isNull()`(**空占位栈**，"If true, it's an empty cargo stack. These get created for spacing, a result of the player moving cargo around.")。资源的小数部分会累计 (`addItems` 内部路径)；

**`removeItems` 把减到 1.0 以下的栈原地替换为 `NULL_STACK` 占位**。

回滚后栈数量对不上，不是 bug

"数值回滚了，`getStacksCopy().size()` 却没变回原样" 是 `removeItems` 的占位语义而不是错误：减空的栈被原地替换为 `NULL_STACK` 占位 (`isNull()` 为 true)。空栈不会自己消失，要清掉多出来的占位，必须调 `removeEmptyStacks()`。

**必须遵守的规则 (mothballed ships)**：`getMothballedShips()` 返回的是**可能为 null 的字段**，使用前**必须**先调 `initMothballedShips(factionId)`。

`CargoAPI` "Call initMothballedShips(String factionId) before using this method."；

`addMothballedShip` 内部会自动以 `"independent"` 初始化 (成员创建后置 `setMothballed(true)`)。

使用 `getMothballedShips()` 前必须先初始化

违反必须遵守的规则的直接可观察结果：`getMothballedShips()` 返回 null 解引用即 NPE。而 `addMothballedShip` 内部会自动初始化，不需要您自己先 init，只有**读取**路径要求先行 `initMothballedShips(factionId)`。

**容量统计**：`getMaxCapacity()` / `getMaxFuel()` / `getMaxPersonnel()` / `getSpaceUsed()` / `getSpaceLeft()` / `getFreeCrewSpace()` / `getFreeFuelSpace()`；`updateSpaceUsed()` 重算已用空间。

玩家舰队的容量上限由 `FleetDataAPI` 同步管线按成员求和写入 (前置知识已记录) —— **直接改 cargo 数值不会重算容量**。

## 规则强度

两条链上需要强记的约束集中成下面的清单，每条都附上违反后的可观察结果，验证与排错时直接对照。

### 规则强度速查表

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| `getMothballedShips()` 使用前必须先调 `initMothballedShips(factionId)` | 必须 | `getMothballedShips()` 返回 null 解引用 NPE |
| `removeKnownShip` / `removeKnownWeapon` / `removeKnownFighter` 想永久生效，必须把 "要移除的清单" 持久化并在每次读档重新移除 (`FactionAPI` "...needs to be stored and they need to be re-removed on every game load.") | 必须 | 读档后 `.faction` 定义的蓝图重新回到已知集合 |
| 直接改动 known / priority 集合后调 `clearShipRoleCache()`(`FactionAPI` "Should be called after direct manipulation...") | 建议 | 再次的 role 选择继续使用旧缓存 |
| 玩家声望调整 (任务/事件等 action 语义) 走 `SectorAPI.adjustPlayerReputation` | 建议 | 直接写 `FactionAPI` 关系绕过 `ReputationActionResponsePlugin` 的 delta 计算与监听通知 |
| `CargoAPI.setFreeTransfer` / `isFreeTransfer` 已废弃 (`CargoAPI` `@Deprecated`，"Use SectorEntityToken.setFreeTransfer() instead.") | 事实 | 实体转移费用语义由实体 free transfer 入口表达 |

## 实现势力与货舱服务

两个服务都由一次性脚本驱动，先读取基线，再做一项可逆修改，最后核对回滚结果。以下局部片段分别来自三个协作类，每段都对应一组明确日志。

### 封装一次性执行骨架

这段代码写在 `GearInOneShotScript.advance` 中。基类只负责执行边界，具体势力或货舱动作由子类的 `runDemo` 实现。

```java
@Override
public void advance(float amount) {
	if (executed) {
		return;
	}
	executed = true;
	runDemo();
}
```

生效后，每个子类只执行一次 `runDemo`，随后 `isDone()` 返回 `true`，引擎同帧紧随其后移除脚本。

### 读取三组势力关系

这段代码写在 `GearInFactionServicesScript.runDemo` 开头。从 `SectorAPI` 取得玩家势力和三个固定目标，再同时读取数值、等级与敌对判据。

```java
SectorAPI sector = Global.getSector();
FactionAPI playerFaction = sector.getPlayerFaction();
FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
FactionAPI pirates = sector.getFaction(Factions.PIRATES);
FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);

LOG.info("GearIn faction: hegemony="
		+ playerFaction.getRelationship(Factions.HEGEMONY)
		+ " level=" + playerFaction.getRelationshipLevel(Factions.HEGEMONY)
		+ " hostile=" + playerFaction.isHostileTo(Factions.HEGEMONY));
```

生效后，日志同时显示三个势力的关系值、关系等级与敌对状态；同一关系对从任一侧读取结果一致。

### 修改关系后精确回滚

这段代码写在势力关系读取之后。保存原值，以原始关系入口增加 0.05，再用 `setRelationship` 写回原值。

```java
float relBefore = hegemony.getRelationship(Factions.PLAYER);
hegemony.adjustRelationship(Factions.PLAYER, 0.05f);
float relDuring = hegemony.getRelationship(Factions.PLAYER);
hegemony.setRelationship(Factions.PLAYER, relBefore);
float relAfter = hegemony.getRelationship(Factions.PLAYER);
```

生效后，通常得到 `原值 → 原值 + 0.05 → 原值`；接近上限时中间值受 `1.0` 钳制，终值仍与原值相等。

### 记录货舱基线并加入货物

这段代码写在 `GearInCargoServicesScript.runDemo` 开头。先记录三类数量、信用、空间与栈数，再加入不同类型的货物。

```java
CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
float suppliesBefore = cargo.getSupplies();
float fuelBefore = cargo.getFuel();
int marinesBefore = cargo.getMarines();
int stacksBefore = cargo.getStacksCopy().size();

cargo.addSupplies(100f);
cargo.addFuel(50f);
cargo.addMarines(10);
```

生效后，补给、燃料和陆战队员分别精确增加 100、50 和 10；原本没有对应栈时，栈列表增加相应条目。

### 回滚货物并辨认空占位栈

这段代码写在货物加入之后。移除同量货物，再遍历栈副本统计 `isNull()`，把数量回滚与存储占位分开验证。

```java
cargo.removeSupplies(100f);
cargo.removeFuel(50f);
cargo.removeMarines(10);

int nullStacksAfter = 0;
for (CargoStackAPI stack : cargo.getStacksCopy()) {
	if (stack.isNull()) {
		nullStacksAfter++;
	}
}
```

生效后，三类货物数量精确回到基线；如果本次新增的栈被减空，`nullStacksAfter` 增加，而栈列表长度可能保持不变。

### 分别注册两个服务脚本

这段代码写在既有 `GearInModPlugin.onGameLoad` 中。每个服务使用自己的 persistent data 键，某一项已执行时不会阻止另一项首次注册。

```java
if (!Global.getSector().getPersistentData().containsKey(
		GearInFactionServicesScript.REGISTER_MARK)) {
	Global.getSector().getPersistentData().put(
			GearInFactionServicesScript.REGISTER_MARK, true);
	Global.getSector().addScript(new GearInFactionServicesScript());
}
if (!Global.getSector().getPersistentData().containsKey(
		GearInCargoServicesScript.REGISTER_MARK)) {
	Global.getSector().getPersistentData().put(
			GearInCargoServicesScript.REGISTER_MARK, true);
	Global.getSector().addScript(new GearInCargoServicesScript());
}
```

生效后，首次进入存档出现势力与货舱两组日志；保存并读档后两组日志都不再重复。

## 组装势力货舱实现

两条链的约束已经列全，现在把它们放进同一个可运行的最小例，每个语义都有可观察的落点。您在既有 MOD 工程 (布局与插件入口 `GearInModPlugin`) 之上新增三个类：一个抽象基类与两个服务演示脚本。一文件一角色，不新增任何资源文件；两个脚本在 `onGameLoad` 各自注册，首个 `advance()` 各执行一次演示并自停：

```text {8-10}
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data/scripts/gearin/campaign\
│       ├── GearInOneShotScript          # 本节新增 (抽象基类，不参与注册)
│       ├── GearInFactionServicesScript  # 本节新增 (势力段脚本)
│       └── GearInCargoServicesScript    # 本节新增 (货物段脚本)
└── jars\
```

### GearInOneShotScript(抽象基类)

**职责**：一次性演示脚本骨架 —— 封装 `EveryFrameScript` 三方法 (`isDone` / `runWhilePaused` / `advance`)，首次 `advance()` 执行一次子类实现的 `runDemo()` 后自停。

**绑定注册入口**：不参与任何注册；两个服务演示脚本继承它，各自负责自己的注册。

### GearInFactionServicesScript(势力段脚本)

**职责**：`FactionAPI` 常用入口演示 —— 读取玩家势力身份/颜色与对霸主、海盗、独立势力的关系 (数值、等级、敌对判定、`getRelToPlayer` 包装)；对霸主执行一次 `adjustRelationship(+0.05)` 并立即 `setRelationship` 精确回滚；

读取 tariff/toll/fine、非法商品、已知资源集合规模、`isShowInIntelTab`。

**绑定注册入口**：在 onGameLoad 的 persistent data 判断块内调用 `addScript` 注册，标记键 `REGISTER_MARK = "GearIn_FactionServices"`(与调用点片段逐字一致)。

### GearInCargoServicesScript(货物段脚本)

**职责**：`CargoAPI` 常用入口演示 —— 读取玩家舰队补给/燃料/陆战队员/信用/空间/栈数基线；`addSupplies(100)` / `addFuel(50)` / `addMarines(10)` 后读取中间值，再同量 remove 回滚并读取终值 (验证数值精确复原)；

读取重型机械商品量与栈列表前三条 (`CargoStackAPI` 的 type/data/size/isNull)。

**绑定注册入口**：在 onGameLoad 的 persistent data 判断块内调用 `addScript` 注册，标记键 `REGISTER_MARK = "GearIn_CargoServices"`(与调用点片段逐字一致)。

### 绑定位置一览速查表

| 类 | 角色 | 绑定注册入口 |
|---|---|---|
| `GearInOneShotScript` | 抽象基类：一次性演示脚本骨架 | 不参与注册 |
| `GearInFactionServicesScript` | 势力段演示脚本 | onGameLoad `addScript`，标记键 `GearIn_FactionServices` |
| `GearInCargoServicesScript` | 货物段演示脚本 | onGameLoad `addScript`，标记键 `GearIn_CargoServices` |

### 行为要点

- `extends GearInOneShotScript`：沿用脚本节的一次性演示模式，首帧 `advance()` 执行一次 `runDemo()`，`isDone()` 置位后引擎同帧紧随其后自动移除。
- `REGISTER_MARK`：persistent data 标记键 —— 两个脚本各自独立 (`GearIn_FactionServices` / `GearIn_CargoServices`)。
    - **必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致；不并入既有插件的 `CLASS_MARK` 标记。
- 势力段：`getFaction(id)` / `getPlayerFaction()` 入口；关系写入演示 `adjustRelationship(+0.05)` → `setRelationship(原值)` 同帧精确回滚。
    - 关系写入有 `[-1,1]` 钳位，原值必在 `[-1,1]` 内，回滚必然精确；`getRelToPlayer()` 对玩家势力自己返回 `1.0`(同势力对默认值)。
- 货物段：`addSupplies(100)` / `addFuel(50)` / `addMarines(10)` 后同量 remove 回滚，`supplies/fuel/marines` 终值恒等于初值。
    - `stacks after` = `stacks during`(`removeItems` 留下 NULL 占位栈)，`nullAfter` = 本演示新腾空的栈数 (0–3，取决于演示前是否已有对应栈)。

### 调用点片段

在既有 MOD 插件的 `onGameLoad(boolean newGame)` 内新增本节注册块 (该插件类归前置单元所有，本节只演示调用，不改动那个文件)：

```java
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInFactionServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInFactionServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInFactionServicesScript());
    }
    if (!Global.getSector().getPersistentData().containsKey(GearInCargoServicesScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInCargoServicesScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInCargoServicesScript());
    }
```

### 构建命令

在 `Starsector/mods/GearIndustry/` 执行：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含三个类的 `.class` 条目 (可用 `jar tf` 核对)。

### 关键故障实验：读取封存舰船时抛出空引用异常

货舱已经存在，但它的封存舰船集合尚未初始化。

**局部故障片段：**

```java
// 故障片段
CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
int storedShips = cargo.getMothballedShips().getNumMembers();
```

**局部修正片段：**

```java {3}
// 修正片段
CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
int storedShips = cargo.getMothballedShips().getNumMembers();
```

实际症状：部分货舱在第一行读取时返回 null，随后的 `getNumMembers()` 抛出 `NullPointerException`；普通商品、信用和容量读取仍然正常。

原因：mothballed ships 是货舱中的延迟初始化字段，`getMothballedShips()` 的接口契约要求调用方先提供势力 ID 完成初始化。

复验：在新档与旧档各执行一次修正片段，`storedShips` 应返回非负整数；重复调用 `initMothballedShips` 不应清空既有封存舰船。

## 验证方案

1. **游戏构建**：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含三个类的 `.class` 条目。
2. **新建生涯 (首次进入存档后第一帧)**：确认 `starsector.log` 按顺序出现两个脚本的日志行 (势力段 ×4、货物段 ×2 组)，且**没有**旧复合演示的收尾 `done` 行。
    - 观察 `write-rollback` 行的初值、中间值与终值。
3. **读档**：保存后读档，确认无任何 `GearIn campaign services` 日志 (两个 `REGISTER_MARK` 标记均已置位)。
4. **参数演示 (可选)**：把势力段 `adjustRelationship(Factions.PLAYER, 0.05f)` 的 delta 改成 `0.2f` 重新构建：`write-rollback` 行中间值比初值高 `0.20`，终值仍与初值相等 (回滚精确性验证)。

预期结果：

- **关系/货物核对**：`write-rollback` 行的第二段恒比第一段高 `0.05`(起始关系接近 `1.0` 时被钳位则例外，新档霸主关系不会)，第三段与第一段相等。
- **固定值**：`drugsIllegal=true`、`illegalCount=4`(原版 `player.faction` 声明 4 种非法商品)、`relToPlayer=1.00(Cooperative)`(玩家势力对玩家自己按同势力默认取 `1.0`)。
- **执行次数**：日志段的顺序与调用点片段的注册顺序一致 (势力 → 货物)，两个脚本各自只执行一次。
- **读档**：不出现任何 `GearIn campaign services` 日志 (两个 `REGISTER_MARK` 标记均已置位、脚本不再注册)。
- **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。
- **其余数值**：以实际存档为准。

清理：把 `adjustRelationship(Factions.PLAYER, 0.2f)` 恢复为 `adjustRelationship(Factions.PLAYER, 0.05f)`。

## 故障分析

- **调用 `getMothballedShips()` 时抛出 NPE**：该字段尚未初始化。

    - **解决方案**：读取前调用 `initMothballedShips(factionId)`。

- **货物数量已经回滚，但栈数量仍然偏多**：减空的栈会留下 `NULL_STACK` 占位。

    - **解决方案**：数量读取 `getSupplies()` 或 `getFuel()`，需要清理占位时调用 `removeEmptyStacks()`。

- **直接修改关系后没有声望提示**：`adjustRelationship` 是原始写入，不触发玩家声望监听。

    - **解决方案**：玩家声望变化使用 `SectorAPI.adjustPlayerReputation`。

- **关系数值超出预期**：直接写入会把结果钳位到 `[-1,1]`。

    - **解决方案**：记录原值并精确回滚。

- **读档后移除的蓝图重新出现**：`removeKnownShip` 的移除清单不随存档保存。

    - **解决方案**：将清单写入 persistent data，每次读档重新移除并调用 `clearShipRoleCache()`。

- **演示日志出现但游戏内没有效果**：注册点或操作目标错误。

    - **解决方案**：将注册放入 `onGameLoad` 的持久标记块，并在写入后读取同一对象。

- **演示脚本只执行一段**：某个 `addScript` 块缺失，或 `REGISTER_MARK` 与调用点不一致。

    - **解决方案**：恢复注册块并统一键名。

## 总结

本节把两个服务入口讲成两条链，并在示例里组合使用。势力链从 `SectorAPI.getFaction(id)` / `getPlayerFaction()` 进入 `FactionAPI`，关系读写经过全局对表 (双向共享、`-1..1` 钳位、对条目用到时才建)；货物链从 `SectorEntityToken.getCargo()` 进入 `CargoAPI`，读写按分族进行，栈是存储细节而不是数量真相。

排错时先记住两条判断。关系写入是**原始写入** (无监听、钳位 `[-1,1]`)，玩家声望 action 走 `adjustPlayerReputation`；`getMothballedShips()` 必须先 `initMothballedShips`，`getStacksCopy()` 含 NULL 占位栈。本节演示的 "写入后立即精确回滚" 写法，让验证可以在不遗留任何持久状态的前提下完成。

脚本注册标记与 `Global` 阶段语义是本节底座；`REGISTER_MARK` 注册模式沿用既有脚本节 "每个注册项用各自的键" 的惯例。本节只覆盖两个入口的常用高频面；制作情报与交互对话框的方法见 [C10-S6]。

## 建议参考项

- `com.fs.starfarer.api.Global`，全局入口：`getSector()`、`getLogger()`。
- `com.fs.starfarer.api.campaign.SectorAPI`：生涯引擎入口。
    - 关系与货物入口都能从这里进入：`getFaction` / `getAllFactions` / `getPlayerFaction` / `getPlayerFleet` / `adjustPlayerReputation` / `getPersistentData` / `addScript`。
- `com.fs.starfarer.api.campaign.FactionAPI`，势力接口：身份、颜色、关系读写、已知资源集合、经济字段与 doctrine/production/spec。
- `com.fs.starfarer.api.characters.RelationshipAPI`，关系值包装：`getRel()` / `getLevel()`。
- `com.fs.starfarer.api.campaign.CargoAPI`，货物容器：分族读写、容量统计、`getStacksCopy()`、`removeEmptyStacks()`。
- `com.fs.starfarer.api.campaign.CargoStackAPI`，货物单栈：类型、数据、大小与 `isNull()` 占位判定。
- `com.fs.starfarer.api.util.MutableValue`，credits 的可变值容器：`get` / `set` / `add` / `subtract`。
- `com.fs.starfarer.api.campaign.RepLevel`，关系等级枚举与阈值：`getLevelFor`、`getMin` / `getMax` 边界语义。
- `com.fs.starfarer.api.campaign.SectorEntityToken`、`CampaignFleetAPI`：`getCargo()` 货物入口；
- `com.fs.starfarer.api.util.Misc`：文本与高亮颜色常量 (`getTextColor` / `getGrayColor` 等)。
- `com.fs.starfarer.api.impl.campaign.ids.Factions`、`Commodities`，id 常量：势力 / 商品。
- `com.fs.starfarer.api.impl.campaign.rulecmd.AdjustRep`，原版声望 action rulecmd：`adjustPlayerReputation` 的原版调用参考。
- `data/scripts/gearin/campaign/`：本节三个示例类所在目录 (MOD 内相对路径)。

## 完整最小例

::: details 完整成品：GearInOneShotScript

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * 一次性演示脚本基类：挂载后第一次 advance() 执行一次子类实现的 runDemo() 并自停
 * (isDone() 返回 true，引擎同帧紧随其后自动从脚本列表移除本脚本)。
 *
 * 两个服务演示脚本 (势力 / 货物) 都继承本类，各自只实现 runDemo()，
 * 避免每个演示类重复 EveryFrameScript 的骨架方法。本类是抽象基类，不参与任何注册；
 * 各子类的注册在 onGameLoad 的 persistent data 门内各自执行 (见页面调用点片段)。
 */
public abstract class GearInOneShotScript implements EveryFrameScript {

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎同帧紧随其后自动移除本脚本。 */
    private boolean executed = false;

    public GearInOneShotScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runDemo();
    }

    /** 子类实现一次演示：只在第一次 advance() 时被调用，全部行为在一个调用内完成。 */
    protected abstract void runDemo();
}
```

:::

::: details 完整成品：GearInFactionServicesScript

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例 (势力段)：FactionAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家势力身份/颜色与对霸主/海盗/独立势力的关系 (数值、等级、敌对判定、getRelToPlayer
 * 包装)；对霸主 adjustRelationship(+0.05) 后立即 setRelationship 精确回滚 ("写入后必须回滚"
 * 基线写法)；读取 tariff/toll/fine、非法商品、已知资源集合规模、isShowInIntelTab。不遗留持久状态。
 */
public class GearInFactionServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_FactionServices";

    private static final Logger LOG = Global.getLogger(GearInFactionServicesScript.class);

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        FactionAPI playerFaction = sector.getPlayerFaction();
        FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
        FactionAPI pirates = sector.getFaction(Factions.PIRATES);
        FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);

        LOG.info(String.format(
                "GearIn campaign services faction: player=%s(%s) baseColor=%s darkColor=%s | hegemony=%s pirates=%s",
                playerFaction.getId(), playerFaction.getDisplayNameWithArticle(),
                playerFaction.getBaseUIColor(), playerFaction.getDarkUIColor(),
                hegemony.getDisplayName(), pirates.getDisplayName()));

        LOG.info(String.format(
                "GearIn campaign services faction relations: hegemony=%s(%s) hostile=%s | pirates=%s(%s) hostile=%s | independent=%s(%s) hostile=%s",
                fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                playerFaction.isHostileTo(Factions.HEGEMONY),
                fmt(playerFaction.getRelationship(Factions.PIRATES)),
                playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                playerFaction.isHostileTo(Factions.PIRATES),
                fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName(),
                playerFaction.isHostileTo(Factions.INDEPENDENT)));

        // 关系写入并精确回滚：adjustRelationship 直接加、setRelationship 直写原值 (势力管理器钳位 [-1,1])
        float relBefore = hegemony.getRelationship(Factions.PLAYER);
        hegemony.adjustRelationship(Factions.PLAYER, 0.05f);
        float relDuring = hegemony.getRelationship(Factions.PLAYER);
        hegemony.setRelationship(Factions.PLAYER, relBefore);
        float relAfter = hegemony.getRelationship(Factions.PLAYER);
        LOG.info(String.format(
                "GearIn campaign services faction rel write-rollback: %s -> %s -> %s (rolled back)",
                fmt(relBefore), fmt(relDuring), fmt(relAfter)));
        LOG.info(String.format(
                "GearIn campaign services faction misc: relToPlayer=%s(%s) tariff=%.3f toll=%.3f fine=%.3f drugsIllegal=%s illegalCount=%d knownShips=%d knownWeapons=%d knownFighters=%d showInIntelTab=%s",
                fmt(playerFaction.getRelToPlayer().getRel()),
                playerFaction.getRelToPlayer().getLevel().getDisplayName(),
                playerFaction.getTariffFraction(), playerFaction.getTollFraction(), playerFaction.getFineFraction(),
                playerFaction.isIllegal(Commodities.DRUGS), playerFaction.getIllegalCommodities().size(),
                playerFaction.getKnownShips().size(),
                playerFaction.getKnownWeapons().size(),
                playerFaction.getKnownFighters().size(),
                playerFaction.isShowInIntelTab()));
    }
}
```

:::

::: details 完整成品：GearInCargoServicesScript

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * 完整最小例 (货物段)：CargoAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家舰队补给/燃料/陆战队员/信用/空间/栈数基线；addSupplies(100)/addFuel(50)/addMarines(10)
 * 后读取中间值，再 remove 同量回滚并读取终值 (验证数值精确复原)；读取重型机械商品量与栈列表
 * 前几条 (CargoStackAPI 的 type/data/size/isNull)。不遗留持久状态。
 */
public class GearInCargoServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CargoServices";

    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        CargoAPI cargo = sector.getPlayerFleet().getCargo();
        float suppliesBefore = cargo.getSupplies();
        float fuelBefore = cargo.getFuel();
        int marinesBefore = cargo.getMarines();
        float creditsBefore = cargo.getCredits().get();
        float spaceBefore = cargo.getSpaceUsed();
        float capacityBefore = cargo.getMaxCapacity();
        int stacksBefore = cargo.getStacksCopy().size();
        float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);

        cargo.addSupplies(100f);
        cargo.addFuel(50f);
        cargo.addMarines(10);
        int stacksDuring = cargo.getStacksCopy().size();
        float suppliesDuring = cargo.getSupplies();
        float fuelDuring = cargo.getFuel();
        int marinesDuring = cargo.getMarines();

        cargo.removeSupplies(100f);
        cargo.removeFuel(50f);
        cargo.removeMarines(10);
        int stacksAfter = cargo.getStacksCopy().size();
        int nullStacksAfter = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isNull()) {
                nullStacksAfter++;
            }
        }
        float suppliesAfter = cargo.getSupplies();
        float fuelAfter = cargo.getFuel();
        int marinesAfter = cargo.getMarines();

        LOG.info(String.format(
                "GearIn campaign services cargo write-rollback: supplies %.0f -> %.0f -> %.0f | fuel %.0f -> %.0f -> %.0f | marines %d -> %d -> %d | machinery %.0f | credits %.0f | space %.0f/%.0f",
                suppliesBefore, suppliesDuring, suppliesAfter, fuelBefore, fuelDuring, fuelAfter,
                marinesBefore, marinesDuring, marinesAfter, machineryBefore, creditsBefore, spaceBefore, capacityBefore));
        LOG.info(String.format("GearIn campaign services cargo stacks: before=%d during=%d after=%d nullAfter=%d",
                stacksBefore, stacksDuring, stacksAfter, nullStacksAfter));

        // 栈列表前三条 (CargoStackAPI：type / data / size / isNull)
        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        int stacksShown = Math.min(3, stacks.size());
        for (int i = 0; i < stacksShown; i++) {
            CargoStackAPI stack = stacks.get(i);
            LOG.info(String.format("GearIn campaign services cargo stack[%d]: type=%s data=%s size=%.0f null=%s",
                    i, stack.getType(), stack.getData(), stack.getSize(), stack.isNull()));
        }
    }
}
```

:::
