# 人物：身份、关系与舰长/军官

本页把人物（`PersonAPI`）讲透：人物对象怎么创建、身份与档案字段怎么读写、关系（`RepLevel` 与 `adjustRelationship`）的数值/档位语义、captain 与 officer 怎么分配、人物在舰队/市场/全局集合里的归属。生涯舰队的成员层与市场人物规则是本页的两块底座，前置知识节列有完整导航。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 人物对象链：`Global.getFactory().createPerson()` / `FactionAPI.createRandomPerson()` → `PersonAPI`（身份、关系、记忆、角色数值、归属）
  - 以及人物对象的默认值（personality `steady`、空姓名、rank/post 默认、faction `neutral`、默认头像、importance `MEDIUM`）；
- 读写人物的身份与档案：姓名/性别、rank/post（经势力解析，回退 `default_ranks.json`）、势力、头像、personality、importance/voice、tags、memory——并区分 `getMemory()`（触发全部插件 `updatePersonFacts`）与 `getMemoryWithoutUpdate()`（纯读取）；
- 说出关系三族：势力对势力 / 势力对玩家（`FactionAPI` 关系族）、人物对玩家（`RelationshipAPI`），以及 `RepLevel` 九档与阈值（0.09 / 0.24 / 0.49 / 0.74）的档位判定；
- 用 `setRelationship` / `adjustRelationship`（含 limit 版本）/ `ensureAtBest` / `ensureAtWorst` 修改关系，说出"数值连续、档位分段、limit 只是单次调整边界、数值恒钳制在 [-1, 1]"四件事；
- 分配 captain：`FleetMemberAPI.setCaptain(person)`（生涯侧）、`ShipAPI.setCaptain(person)`（战斗侧），说出玩家旗舰 `getCaptain()` 返回玩家人物的特例、舰长 personality 决定战斗 AI、舰长个人技能经 `applyPersonalToStats` 作用于舰船统计；
- 分配 officer：`Global.getFactory().createOfficerData(person)` → `FleetDataAPI.addOfficer(...)` → XP/技能/升级（`officerLevelUp` 插件，`officerMaxLevel` 5）→ 再 `setCaptain` 上舰
  - 说出 `makeSkillPicks()` 重掷、`$isMercenary` 佣兵不加 XP 两个陷阱；
- 说出人物持久归属四入口（成员 captain、市场 people + 通信目录、important people、officer roster），并解释"只存在于局部变量的人物不会被保存"；
- 从症状定位十三类典型故障：人物读档后消失、非法 personality 保存时 NPE、`adjustRelationship` 返回值 false、档位与数值不一致、市场有人物但通信目录没有、军官不加 XP、技能选项被重掷、checkout 未归还影响选人、演示中断残留、人物关系与势力关系混用、旗舰舰长读回玩家、`getMemory()` 回调风暴、读档后默认舰长消失。

## 前置知识

本节需要以下前置知识，按主题列出：

- **生涯时钟与脚本注册守卫**（S08-P01）：生涯脚本注册守卫（persistent data `CLASS_MARK` 模式）。
- **舰队成员与舰长**（S08-P03）：`FactoryAPI.createFleetMember` / `FleetDataAPI.addFleetMember`（演示成员创建与入队）、`FleetMemberAPI.getCaptain` / `setCaptain`（本页 captain 的生涯侧入口）、旗舰语义、`getMemberWithCaptain`。
- **市场人物与通信目录**（S08-P04）：`MarketAPI.addPerson` / `removePerson` / `getPeopleCopy`、通信目录 `getCommDirectory().addPerson`（人物在市场侧的归属规则）。
- **工厂入口与阶段判断**（S06-P03）：`Global.getFactory()` 是创建生涯对象的正式途径；`getCurrentState()` 阶段判断。
- **插件生命周期回调与守卫键**（S06-P02）：`GearInModPlugin` 生命周期与 `CLASS_MARK` 门；本页注册块挂进 `onGameLoad`。
- **生涯与战役的区别**（S03-P01）：生涯（campaign）与战役（mission）的区别。

## 术语

按术语约定，本页首次完整讲解以下词（后续页面继续使用）：

- **人物（person）**：API 名保留 `PersonAPI`。生涯里代表一个"人"的对象，承载身份（姓名/性别/rank/post/头像/势力）、记忆、标签、对玩家关系、角色数值与归属（captain、officer、市场、important people）。
- **关系（relationship）**：两两之间的声望数值与档位。势力之间/势力对玩家走 `FactionAPI` 关系族；人物对玩家走 `RelationshipAPI`。数值范围 [-1, 1]，档位由 `RepLevel` 分段。
- **声誉档位（RepLevel）**：API 名保留 `RepLevel`。
  - 九档枚举：`VENGEFUL` / `HOSTILE` / `INHOSPITABLE` / `SUSPICIOUS` / `NEUTRAL` / `FAVORABLE` / `WELCOMING` / `FRIENDLY` / `COOPERATIVE`
  - 正向阈值 0.09 / 0.24 / 0.49 / 0.74，负向对称。
- **captain（舰长）**：舰队成员的指挥官。决定该成员/战斗舰船的统计修正与战斗 AI personality。
- **officer（军官）**：`OfficerDataAPI` 包装的人物，带 XP、等级与技能选择；隶属舰队 roster（`FleetDataAPI` 军官集合），被 `setCaptain` 到某个成员上时指挥该船。

## 文件与 API 链路

### 人物对象与创建入口

创建人物的两个正式入口（`com.fs.starfarer.api.FactoryAPI`）：

| 入口 | 语义 |
|---|---|
| `Global.getFactory().createPerson()` | 创建空白人物（默认值见下表）|
| `FactionAPI.createRandomPerson()`（含 Gender / Random 变体）| 按势力生成随机人物：随机性别（默认各半）、势力肖像、随机姓名（姓名池）、随机 voice（`FactionAPI.createRandomPerson`）|

另有包装入口 `Global.getFactory().createOfficerData(PersonAPI)`——把人物包装成 `OfficerDataAPI`（见"officer"节）。

`SettingsAPI` 也有同签名 `createPerson`（同样返回空白人物），习惯用 `Global.getFactory()`。

人物对象的**默认值**：

| 字段 | 默认值 | 说明 |
|---|---|---|
| id | `"p_" + Misc.genUID()` | 每个实例唯一 |
| personalityId | `"steady"` | 无参构造默认即 `"steady"` |
| name | `FullName("", "", Gender.ANY)` | 空姓名 |
| portraitSprite | `SettingsAPI.getSpriteName("misc", "default_portrait")` | `settings.json` → `graphics/portraits/portrait_generic_grayscale.png` |
| rankId / postId | `Ranks.SPACE_COMMANDER` / `Ranks.POST_FLEET_COMMANDER` | `spaceCommander` / `fleetCommander` |
| factionId | `"neutral"` | |
| importance | `PersonImportance.MEDIUM` | （枚举值 0/0.25/0.5/0.75/1）|
| contactWeight | `1.0F` | |
| relToPlayer | null（延迟创建）| 首次 `getRelToPlayer()` 才创建 |

`isDefault()` = 头像等于默认头像；`isPlayer()` = 人物对象即 `Global.getSector().getPlayerPerson()`；`isAICore()` = `aiCoreId != null`。

玩家人物同样以 `steady` 起步，新档创建时人物势力为 `"player"`。

### 身份与档案（PersonAPI）

`PersonAPI`（`com.fs.starfarer.api.characters.PersonAPI`）按功能分组：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()` / `setId(String)`、`getName()` / `setName(FullName)`、`getNameString()`、`getGender()` / `setGender(Gender)`、`isMale()` / `isFemale()` | id 唯一；姓名是**可变对象** `FullName`（`(first + " " + last).trim()`）；性别挂在姓名上 |
| rank/post | `getRankId()` / `setRankId(String)`、`getPostId()` / `setPostId(String)`、`getRank()` / `getPost()`、`getRankArticle()` / `getPostArticle()` | id 是存储值；显示名经势力解析（见下）|
| 头像 | `getPortraitSprite()` / `setPortraitSprite(String)` | 资源路径字符串（不校验存在性）|
| 势力 | `getFaction()`、`setFaction(String factionId)` | `getFaction()` 延迟解析势力对象（按 id 从全局势力表解析）；`setFaction` 只写 id |
| 关系 | `getRelToPlayer()` | 人物对玩家关系（见"关系"节）|
| 记忆 | `getMemory()` / `getMemoryWithoutUpdate()` | 见"记忆"小节 |
| 标签 | `hasTag` / `addTag` / `removeTag` / `getTags` / `clearTags` | 运行时标签集合（`getTags()` 缺省返回空集）|
| personality | `getPersonalityAPI()`、`setPersonality(String)` | 由 `personalities.csv` 注册（见"故障分析"第 2 条）|
| importance/voice | `getImportance()` / `setImportance(PersonImportance)`、`setImportanceAndVoice(PersonImportance, Random)`、`getVoice()` / `setVoice(String)` | voice 存储在 memory `$voice`；`setImportanceAndVoice` 只在 voice 未被显式覆写时写 voice |
| AI core | `getAICoreId()` / `setAICoreId(String)`、`isAICore()` | `aiCoreId != null` 即 AI 核心人物 |
| 归属 | `getFleet()` / `setFleet(CampaignFleetAPI)`、`getMarket()` / `setMarket(MarketAPI)` | officer 所在舰队、当前市场（见"归属"小节）|
| 角色数值 | `getStats()`、`setStats(MutableCharacterStatsAPI)`、`getFleetCommanderStats()` | 等级/XP/技能/故事点；`getFleetCommanderStats()` = `fleet.getCommanderStats()`，无舰队时 null |
| 联系 | `wantsToContactPlayer()` / `incrWantsToContactReasons()` / `decrWantsToContactReasons()`、`getContactWeight()` / `setContactWeight(float)` | 联系人与权重（`wantsToContactReasonsCount`）|

::: caution personality id 必须来自注册表

`personalities.csv` 之外的 id 使 `getPersonalityAPI()` 返回 null（CSV spec 加载期的查询对未知 id 静默返回 null、不报错）。

问题延迟到使用点：保存时写档失败（`NullPointerException`）。

原版合法 id 恰 5 个：`timid` / `cautious` / `steady` / `aggressive` / `reckless`。

:::

**rank/post 解析**：`getRank()` = `getFaction().getRank(rankId)`；`FactionAPI.getRank` 先查势力 spec 的 ranks 段，查不到回退默认值（数据源 `data/world/factions/default_ranks.json`）。

`getRankArticle()` / `getPostArticle()` 按显示名首字母是否元音返回 `"an"` / `"a"`。

示例：`spaceCommander` 在 `default_ranks.json` 是 "Commander"， `hegemony.faction` 覆写为同名 "Commander"；`fleetCommander` post 是 "Fleet Commander"。

**记忆**：`getMemory()` 触发全部插件的 `updatePersonFacts(person, memory)` 回调（与 `MarketAPI.getMemory()` 同族）；纯读取**必须**用 `getMemoryWithoutUpdate()`（同样延迟创建但无回调）。

voice 即 memory `$voice`。

::: info 为什么记忆读取要分两条路径

`getMemory()` 先跑一遍全部插件的 `updatePersonFacts` 回调，让各插件在读取前按最新事实刷新记忆——这是"读前更新"的设计；但高频读取（如每帧）也走这条路径就会变成回调风暴，其他插件还可能改写内存值。

纯读取用 `getMemoryWithoutUpdate()`：同样延迟创建记忆对象，但没有回调。

:::

**人物数据文件**（加载链：CSV spec 加载期读入、姓名池）：`data/characters/personalities.csv`（`id,name,desc,bravery` + 12 个任务权重列）

原版 id 恰 5 个：`timid` / `cautious` / `steady` / `aggressive` / `reckless`，bravery 全为 5。

`data/characters/person_names.csv`（姓名池，usage `f`/`l`，category 如 `fringe`/`core`/`pirate`，随机姓名按势力 spec 的名字段加权）。

`personalities.csv` 的权重列（`CAPTURE`/`PATROL`/`INTERCEPT`/`ASSAULT` 等）是舰船 AI 行为权重。

### 关系

#### 存储与钳制

势力关系按 `"<id1>_<id2>"` 键存储，**双向插入同一对象**；读取时延迟创建：默认 `0.0F`，**同势力之间恒 `1.0F` **。

写入时值被钳制在 [-1, 1]——关系数值不可能越界。

人物对玩家关系存在人物字段 `relToPlayer`（延迟创建），`RelationshipAPI.setRel` 同样钳制 [-1 , 1]。

#### RepLevel 档位

`RepLevel`（`com.fs.starfarer.api.campaign.RepLevel`）九档，正向阈值 `T1=0.09` / `T2=0.24` / `T3=0.49` / `T4=0.74`。

档位判定 `getLevelFor(float)`：取整到百分制再与阈值比，**边界含于高位档**（如 0.49 → `WELCOMING`，0.50 → `FRIENDLY`）。

`isPositive()` = 从 `FAVORABLE` 起（`isAtWorst(FAVORABLE)`）；`isNegative()` = `SUSPICIOUS` 及以下。

`setRelationship(id, RepLevel)` 的落点（`FactionAPI`）：

| 档位 | 写入值 |
|---|---|
| `NEUTRAL` | `0.0` |
| 正档（FAVORABLE / WELCOMING / FRIENDLY / COOPERATIVE）| `min + 0.01`（0.10 / 0.25 / 0.50 / 0.75）|
| 负档（SUSPICIOUS / INHOSPITABLE / HOSTILE / VENGEFUL）| `-(min + 0.01)`（-0.10 / -0.25 / -0.50 / -0.75）|

#### FactionAPI 关系族

`FactionAPI`（`com.fs.starfarer.api.campaign.FactionAPI`）关系成员：

| 成员 | 语义 |
|---|---|
| `adjustRelationship(String id, float delta)` | 直接加 delta（无 limit，只受 ±1 钳制）|
| `adjustRelationship(String id, float delta, RepLevel limit)` | 带 limit：delta 为 0 直接返回 false；正 delta 上限 = `limit.getMax()`（limit 为负档时 = `-min-0.01`），负 delta 下限 = `-limit.getMax()`（limit 为正档时 = `min+0.01`）；值未越过限制返回 false |
| `setRelationship(String id, float newValue)` / `setRelationship(String id, RepLevel level)` | 精确写值 / 按档位落点（见上表）|
| `ensureAtBest(String id, RepLevel)` / `ensureAtWorst(String id, RepLevel)` | 当前档位不满足时写入边界值并返回 true |
| `getRelationship(String id)` / `getRelationshipLevel(String)` / `getRepInt(String)` | 值 / 档位 / 百分制整数（`RepLevel.getRepInt`）|
| `isAtBest` / `isAtWorst`、`isHostileTo(String)` | 档位比较；`isHostileTo` = `isAtBest(HOSTILE)` |
| `getRelToPlayer()` | 玩家视角关系包装（内部转发 `adjustRelationship` 到自己 id）|

::: warning limit 只是"本次调整的方向边界"

`adjustRelationship(delta, limit)` 的 limit 不是"目标档位"——从 1.00 减 0.5（limit `VENGEFUL`）落在 0.50（`FRIENDLY`），而不是 `HOSTILE`。

"设置档位"用 `setRelationship(id, RepLevel)`，"确保不低于/不高于某档"用 `ensureAtBest` / `ensureAtWorst`。

:::

#### 人物对玩家关系（RelationshipAPI）

`RelationshipAPI`（`com.fs.starfarer.api.characters.RelationshipAPI`）：`getRel()` / `setRel(float)`、`getLevel()` / `setLevel(RepLevel)`、`adjustRelationship(float delta, RepLevel limit)`

`isAtBest` / `isAtWorst`、`ensureAtBest` / `ensureAtWorst`、`isHostile()`、`getRepInt()`

语义与 `FactionAPI` 关系族一致（`RelationshipAPI` 是同一套逻辑的孪生实现）。

`RelationshipTarget` 标记目标类型 `FACTION` / `PERSON` / `PLAYER`；`isPlayer()` 判断目标人物 == `Global.getSector().getPlayerPerson()`。

接近零的人物对玩家关系在保存时不落盘，读档后恢复默认值。

### captain 与 officer

#### 生涯侧 captain（FleetMemberAPI）

- 默认舰长：成员自带默认舰长 `steady`（前序页面已记录"自动补默认舰长"）。保存压缩时，**steady + 空姓名 + level ≤ 1 的默认舰长不落盘**——"读档后默认舰长消失"是正常语义。
- `FleetMemberAPI.getCaptain()`：**玩家旗舰特例**——成员是玩家舰队旗舰时返回玩家人物。
  - 否则返回缓存的 `captain` 字段（null 时延迟创建默认舰长并设势力）
  - `FleetMemberAPI.setCaptain(PersonAPI)` 写回并触发成员 stats 重算。

::: warning 玩家旗舰的舰长读回恒是玩家人物

成员是玩家舰队旗舰时 `getCaptain()` 直接返回玩家人物，读路径覆盖写入——把自定义舰长挂到旗舰再读回，读到的仍是玩家人物。自定义舰长请放非旗舰成员（本页示例即如此）。

:::

- 舰长对成员的影响：`captain.getStats().applyPersonalToStats(stats, hullSize)` 把舰长个人技能应用到成员 `MutableShipStatsAPI`。
- 反查：`FleetDataAPI.getMemberWithCaptain(PersonAPI)` 按同一性找舰长所在成员。
- 舰队指挥官：默认是 `steady` 人物；`CampaignFleetAPI.getCommander()` / `setCommander(PersonAPI)`
  - 新档 `setPlayerFleet` 把玩家人物同时设为旗舰舰长与舰队指挥官。

#### 战斗侧 captain（ShipAPI）

- 部署链：成员部署为战斗舰船时，`getCaptain()` 与 `getFleetCommander()` 传入战斗侧
  - `ShipAPI.getCaptain()`（`com.fs.starfarer.api.combat.ShipAPI`）返回战斗舰船舰长，`getOriginalCaptain()` 返回部署初始舰长， `getFleetCommander()` 返回关联的舰队指挥官。
- **中途换舰长会改统计**：`ShipAPI.setCaptain(PersonAPI)` 先解除旧舰长的个人技能（含子模块），再挂新舰长。
- **personality 决定战斗 AI**：战斗舰船的 personality 取舰长 personality id（舰长缺失时用装配的 fallback personality）。

#### officer（OfficerDataAPI）

- 包装与 roster：`Global.getFactory().createOfficerData(person)`（`com.fs.starfarer.api.FactoryAPI`）
  - → `FleetDataAPI.addOfficer(PersonAPI)` / `addOfficer(OfficerDataAPI)` / `getOfficersCopy()` / `removeOfficer(PersonAPI)`（`com.fs.starfarer.api.campaign.FleetDataAPI`）
  - `FleetDataAPI`：`addOfficer` 写入 roster 并 `person.setFleet(fleet)`（PersonAPI 版还会把人物势力设为舰队势力）
  - `removeOfficer` 移出并 `setFleet(null)`
- `OfficerDataAPI`（`com.fs.starfarer.api.characters.OfficerDataAPI`）：
  - `getPerson()` / `setPerson(PersonAPI)`、`addXP(long[, TextPanelAPI[, boolean]])`、`canLevelUp()`
  - `makeSkillPicks([Random])`、`getSkillPicks()`、 `levelUp(String skillId[, Random])`
- 升级链（`OfficerDataAPI`）：
  - `addXP` 走 `settings.json` 的 `officerLevelUp` 插件（`com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`）
  - **`$isMercenary` 记忆为 true 的佣兵人物 addXP 直接返回** （`Misc.isMercenary` 读 memory `$isMercenary`，`com.fs.starfarer.api.util.Misc`）
  - XP 达到下一级阈值 （`TOTAL_XP_PER_LEVEL × officerXPRequiredMult(4)`，`com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`）且等级 ≤ `officerMaxLevel(5)`（settings）时可升级
  - `canLevelUp()` 满足时 `addXP` 自动 `makeSkillPicks()`；`makeSkillPicks()` **每次调用都重掷选项**
  - `levelUp(skillId)` 等级 +1、技能 +1 级并清空重掷。
- 军官上舰 = 把 officer 的人物 `setCaptain` 到成员（原版军官面板的"命令舰船"即此语义）。

#### 归属四入口（持久化边界）

人物只随"引用它的持久集合"保存：

1. **成员舰长**：`FleetMemberAPI.setCaptain(person)`（成员在 `FleetDataAPI` 里）；
2. **市场人物**：`MarketAPI.addPerson(person)`（同时写 `person.setMarket(market)`），**通信目录条目必须另行 `getCommDirectory().addPerson(person)` 维护**（`MarketAPI.addPerson` 不会自动建目录条目）；
3. **重要人物**：`SectorAPI.getImportantPeople()` → `ImportantPeopleAPI.addPerson` / `containsPerson` / `removePerson` / `getData(person)`（`com.fs.starfarer.api.characters.ImportantPeopleAPI`）
   - `checkOutPerson(person, reasonId)` / `returnPerson(person, reasonId)`；
4. **officer roster**：`FleetDataAPI.addOfficer`（见上）。

只存在于局部变量、未被任何持久集合引用的人物，帧结束后即被回收——**不会被保存，也不会报错**。

::: warning 市场人物不等于通信目录联系人

`MarketAPI.addPerson` 只把人物放进市场 roster，不会自动建通信目录条目——人物在市场人物列表与存档里，但通信目录/联系人 UI 不出现。

要人物作为联系人可见，必须另行 `getCommDirectory().addPerson(person)` 维护；移除可见联系人时先移 comm entry 再移 roster。

:::

### 注册入口

本页示例注册**必须**发生在生涯引擎建立之后：沿用生涯回调 `onGameLoad(boolean newGame)` + 各自 persistent data 键守卫模式（不要把注册键挂进插件既有的 `CLASS_MARK` 门内）。注册见"完整最小例"的调用点片段。

## 规则强度

本页必须遵守的规则清单（"必须/禁止"，均附违反后的可观察结果）：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 纯读取人物记忆用 `getMemoryWithoutUpdate()`；`getMemory()` 触发全部插件 `updatePersonFacts` | 必须 | 每次调用触发全部插件回调（`PersonAPI.getMemory`），高频读取时回调风暴，其他插件可能改写内存值 |
| 人物要成为市场可见联系人，`MarketAPI.addPerson` 之后必须另行 `getCommDirectory().addPerson(person)` 维护目录条目 | 必须 | 人物在市场人物列表与存档里，但通信目录/联系人 UI 不出现 |
| "设置档位"用 `setRelationship(id, RepLevel)`（或 `ensureAtBest`/`ensureAtWorst`）；`adjustRelationship(delta, limit)` 的 limit 只是单次调整的方向边界 | 必须 | 数值落在限位内但档位与预期不符（1.00 − 0.5 落 0.50 = `FRIENDLY` 而非 `HOSTILE`）|
| `adjustRelationship` 的 delta 为 0 时（三参版）不产生任何修改并返回 false | 必须 | 返回值 false、值不变，误以为调用成功 |
| 关系数值恒被钳制在 [-1, 1]（`RelationshipAPI.setRel` 等写入方法均钳制）| 必须 | 写入越界值后读回被截断（如写 1.5 读回 1.0）|
| personality id 必须来自 `personalities.csv` 注册表（原版 5 个：`timid`/`cautious`/`steady`/`aggressive`/`reckless`）| 必须 | 未知 id 使 `getPersonalityAPI()` 返回 null（CSV spec 加载期的查询静默返回 null），保存时写档失败（`NullPointerException`）|
| `OfficerDataAPI.makeSkillPicks()` 每次调用都会重掷选项；已展示给玩家的可选项必须维持同一对象状态 | 必须 | 玩家看到的可选技能列表与展示不一致 |
| `ImportantPeopleAPI.checkOutPerson(person, reasonId)` 必须配对 `returnPerson`；未归还时同 reason 的按条件选人会跳过该人物 | 必须 | 任务/事件生成时该人物不再被选中（`MarketPostPersonFilter`）|
| `PersonAPI.setMarket(market)` 只写人物字段；市场 roster 必须由 `MarketAPI.addPerson` 维护（反向：`person.setMarket` 不会把人物放进市场列表）| 必须 | 人物字段显示在市场，但市场列表与通信目录查不到 |
| 跨存档保留人物必须挂入至少一个持久集合（成员 captain / 市场 people+comm / important people / officer roster）| 必须 | 读档后人物不存在、无任何报错 |
| 玩家旗舰 `getCaptain()` 恒返回玩家人物（特例），不要把自定义舰长挂到旗舰再读回 | 必须 | 读回结果与写入不一致（`FleetMemberAPI.getCaptain`）|
| 佣兵人物（memory `$isMercenary`）不获得军官 XP | 必须 | `addXP` 后 XP 不变（`OfficerDataAPI.addXP`）|
| 演示/临时人物修改关系后应同帧回滚，且回滚用"快照原值 + `setRelationship`" | 建议 | 中断残留的修改随存档保存 |
| 创建人物优先用 `Global.getFactory().createPerson()` / `FactionAPI.createRandomPerson()`，不依赖实现类构造器 | 建议 | 实现类不在公开 API 面，换版本可能失效 |

## 完整最小例

在既有 MOD 布局与插件生命周期类之上新增 8 个类（不新增资源文件）。原单一演示类按角色拆成 8 个小类：协调器 + 7 个角色小类，各自承担一个职责：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data/
├── src/
│   └── data/scripts/gearin/campaign/
│       ├── GearInPersonScript         # 本页新增（归本页所有）：协调器
│       ├── GearInBaseline             # 基线快照
│       ├── GearInPersonIdentity       # 人物创建与身份
│       ├── GearInPersonRelationship   # 人物对玩家关系
│       ├── GearInPersonRegistration   # 人物注册（归属入口）
│       ├── GearInCaptain              # 舰长
│       ├── GearInOfficer              # 军官
│       └── GearInFactionRelation      # 势力间关系
└── jars/
```

演示脚本在挂载后的第一帧执行**同帧回滚**的序列：基线 → 演示成员 → 人物 A 默认值与身份 → 标签/记忆 → 人物对玩家关系 → 市场与重要人物 → 默认舰长与自定义舰长 → 势力随机人物 → officer 全链 （包装/XP/技能/升级/上舰）→ 势力关系确定性序列 → 清理。

全部修改在当帧回滚，不遗留任何持久状态。

**演示纪律**：演示/临时人物修改后必须同帧回滚，回滚用"快照原值 + `setRelationship`"（见规则强度表）；先 `add` 后立刻 `remove` 的配对顺序不要调换——演示帧中途抛异常会留下随存档保存的残留，且 `REGISTER_MARK` 已置位后读档不重放，残留无法自动清理（最坏形态见故障分析第 9 条）。

### GearInPersonScript（协调器）

一次性演示脚本（`EveryFrameScript`）：`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除。

职责：记录基线（交由 GearInBaseline）、创建并挂载演示成员 wolf / hammerhead 供后续小节使用、依序调用各角色小类、末尾清理恢复初始舰队。

`REGISTER_MARK`（`"GearIn_PersonLesson"`）是存档持久化守卫键，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 人物专题一次性演示脚本（协调器）：编排各角色小类并负责演示成员与清理，自停脚本。
 * 注册入口：生涯回调 onGameLoad，persistent data 键 REGISTER_MARK 一次性守卫；全程同帧回滚。
 */
public class GearInPersonScript implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_PersonLesson";

    private static final Logger LOG = Global.getLogger(GearInPersonScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInPersonScript() {
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
        run();
    }

    private void run() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = new GearInBaseline().run(sector, fleetData);
        // 演示成员 wolf / hammerhead（供 captain / officer 小节使用）
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        FleetMemberAPI hammerhead = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hammerhead_Elite");
        fleetData.addFleetMember(wolf);
        fleetData.addFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn person lesson members: added wolf=%s hammerhead=%s count=%d",
                wolf.getShipName(), hammerhead.getShipName(), fleetData.getNumMembers()));

        // 人物 A：创建/身份/标签内存 -> 对玩家关系 -> 市场与重要人物注册
        PersonAPI personA = new GearInPersonIdentity().run();
        new GearInPersonRelationship().run(personA);
        new GearInPersonRegistration().run(sector, personA);

        // 舰长；人物 B 与军官；势力关系（同帧回滚）
        new GearInCaptain().run(wolf, personA, fleetData);
        new GearInOfficer().run(sector, hammerhead, fleetData, sector.getPlayerFleet());
        new GearInFactionRelation().run(sector);

        // 清理：恢复初始舰队
        fleetData.removeFleetMember(wolf);
        fleetData.removeFleetMember(hammerhead);
        LOG.info(String.format(
                "GearIn person lesson done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
```

### GearInBaseline

基线快照（演示前置观察）：记录玩家舰队成员数、玩家势力 id、玩家人物名，并验证玩家旗舰 `getCaptain()` 特例（旗舰舰长读回玩家人物，`captainIsPlayerPerson=true`）；返回初始成员数，供协调器在清理后对比。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 基线快照（演示前置观察）：玩家舰队成员数/玩家势力/玩家人物，及玩家旗舰 getCaptain() 特例。
 */
public class GearInBaseline {

    private static final Logger LOG = Global.getLogger(GearInBaseline.class);

    /** 记录基线并返回初始成员数，供协调器在清理后对比。 */
    public int run(SectorAPI sector, FleetDataAPI fleetData) {
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format(
                "GearIn person lesson start: members=%d playerFaction=%s playerPerson=%s",
                initialCount, sector.getPlayerFaction().getId(), sector.getPlayerPerson().getNameString()));
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        FleetMemberAPI flagship = playerFleet.getFlagship();
        boolean flagshipCaptainIsPlayer = flagship != null && flagship.getCaptain() == sector.getPlayerPerson();
        LOG.info(String.format(
                "GearIn person lesson flagship: name=%s captainIsPlayerPerson=%s",
                flagship == null ? "none" : flagship.getShipName(), flagshipCaptainIsPlayer));
        return initialCount;
    }
}
```

### GearInPersonIdentity

人物创建与身份（`PersonAPI`）：`Global.getFactory().createPerson()` 创建人物 A 并逐字段读回默认值（id、`steady`、空名、`spaceCommander`/ `fleetCommander`、`neutral`、默认头像、`MEDIUM`，对应"人物对象与创建入口"的默认值表）；

随后写入姓名/性别/势力/rank/post/personality/importance+voice 并读回（`rank=Commander`、`post=Fleet Commander` 经势力解析）；

标签与内存写入读取——纯读只用 `getMemoryWithoutUpdate()`（对应规则强度第 1 条）。

```java
package data.scripts.gearin.campaign;

import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * 人物创建与身份（PersonAPI）：createPerson 默认值、姓名/性别/势力/rank/post/personality/importance+voice、标签与内存。
 */
public class GearInPersonIdentity {

    private static final Logger LOG = Global.getLogger(GearInPersonIdentity.class);

    /** 创建人物并写入身份与附属数据，返回该人物供后续角色小节使用。 */
    public PersonAPI run() {
        PersonAPI personA = Global.getFactory().createPerson();
        LOG.info(String.format(
                "GearIn person lesson personA defaults: id=%s personality=%s name='%s' rankId=%s postId=%s faction=%s portraitIsDefault=%s importance=%s",
                personA.getId(), personA.getPersonalityAPI().getId(), personA.getNameString(),
                personA.getRankId(), personA.getPostId(), personA.getFaction().getId(),
                personA.isDefault(), personA.getImportance()));

        personA.setFaction("hegemony");
        personA.setName(new FullName("Aster", "Vance", Gender.FEMALE));
        personA.setRankId(Ranks.SPACE_COMMANDER);
        personA.setPostId(Ranks.POST_FLEET_COMMANDER);
        personA.setPersonality("reckless");
        personA.setImportanceAndVoice(PersonImportance.HIGH, new Random());
        LOG.info(String.format(
                "GearIn person lesson personA identity: name=%s female=%s rank=%s post=%s personality=%s faction=%s voice=%s",
                personA.getNameString(), personA.isFemale(), personA.getRank(), personA.getPost(),
                personA.getPersonalityAPI().getId(), personA.getFaction().getId(), personA.getVoice()));

        personA.addTag("GearIn_personLesson");
        personA.getMemoryWithoutUpdate().set("$GearIn_personLesson", 42);
        LOG.info(String.format(
                "GearIn person lesson tagsMemory: hasTag=%s tags=%d memory=%d",
                personA.hasTag("GearIn_personLesson"), personA.getTags().size(),
                personA.getMemoryWithoutUpdate().getInt("$GearIn_personLesson")));
        return personA;
    }
}
```

### GearInPersonRelationship

人物对玩家关系（`RelationshipAPI` / `RepLevel`）：
- `getRelToPlayer()` 延迟创建（默认 `rel=0.00 level=NEUTRAL`）；
- `adjustRelationship(0.3f, null)`（无 limit，正向默认封顶 `COOPERATIVE.getMax()` 即 1.0）；
- `setLevel(COOPERATIVE)` 落 `0.75`；
- `setLevel(HOSTILE)` 落 `-0.50`（`min+0.01` / `-min-0.01` 语义）；
- `setRel(0)` 复位。

五步日志覆盖"数值连续、档位分段"。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

/**
 * 人物对玩家关系（RelationshipAPI / RepLevel）：延迟默认 -> adjustRelationship -> setLevel -> setRel 复位。
 */
public class GearInPersonRelationship {

    private static final Logger LOG = Global.getLogger(GearInPersonRelationship.class);

    public void run(PersonAPI personA) {
        RelationshipAPI relToPlayer = personA.getRelToPlayer();
        LOG.info(String.format(
                "GearIn person lesson relToPlayer default: rel=%.2f level=%s hostile=%s",
                relToPlayer.getRel(), relToPlayer.getLevel(), relToPlayer.isHostile()));
        relToPlayer.adjustRelationship(0.3f, null);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer adjust: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
        relToPlayer.setLevel(RepLevel.COOPERATIVE);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer cooperative: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
        relToPlayer.setLevel(RepLevel.HOSTILE);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer hostile: rel=%.2f level=%s hostile=%s",
                relToPlayer.getRel(), relToPlayer.getLevel(), relToPlayer.isHostile()));
        relToPlayer.setRel(0f);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer reset: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
    }
}
```

### GearInPersonRegistration

人物注册（归属入口）：市场侧 `addPerson` / `removePerson` 同帧配对（`person.setMarket` 联动、`getPeopleCopy` 前后核对）；重要人物 `addPerson` / `containsPerson` / `getData` / `removePerson` 同帧配对。

演示归属四入口中的两个（市场 people + important people），且不遗留存档。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI.PersonDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 人物注册（归属入口）：市场通讯录 addPerson/removePerson 双向交接，重要人物 addPerson/contains/getData/removePerson。
 */
public class GearInPersonRegistration {

    private static final Logger LOG = Global.getLogger(GearInPersonRegistration.class);

    public void run(SectorAPI sector, PersonAPI personA) {
        EconomyAPI economy = sector.getEconomy();
        MarketAPI market = economy.getMarket("jangala");
        if (market == null) {
            for (MarketAPI candidate : economy.getMarketsCopy()) {
                if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                    market = candidate;
                    break;
                }
            }
        }
        if (market != null) {
            market.addPerson(personA);
            boolean inMarketAfterAdd = market.getPeopleCopy().contains(personA);
            boolean personMarketLinked = personA.getMarket() == market;
            market.removePerson(personA);
            boolean inMarketAfterRemove = market.getPeopleCopy().contains(personA);
            LOG.info(String.format(
                    "GearIn person lesson market: id=%s inAfterAdd=%s personMarketLinked=%s inAfterRemove=%s",
                    market.getId(), inMarketAfterAdd, personMarketLinked, inMarketAfterRemove));
        } else {
            LOG.info("GearIn person lesson market: none found, skipped");
        }

        sector.getImportantPeople().addPerson(personA);
        boolean importantAfterAdd = sector.getImportantPeople().containsPerson(personA);
        PersonDataAPI personData = sector.getImportantPeople().getData(personA);
        boolean personDataLinked = personData != null && personData.getPerson() == personA;
        sector.getImportantPeople().removePerson(personA);
        boolean importantAfterRemove = sector.getImportantPeople().containsPerson(personA);
        LOG.info(String.format(
                "GearIn person lesson importantPeople: containsAfterAdd=%s dataLinked=%s containsAfterRemove=%s",
                importantAfterAdd, personDataLinked, importantAfterRemove));
    }
}
```

### GearInCaptain

舰长（`FleetMemberAPI`）：读取 wolf 的默认舰长（steady、空名、level 1，正是保存时会被压缩掉的形态）；`setCaptain(personA)` 后验证 `getCaptain() == personA` 同一性 + `getMemberWithCaptain(personA)` 反查。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 舰长（captain）：默认舰长读取（steady/空名/level 1）、setCaptain 交接与 getMemberWithCaptain 反查。
 */
public class GearInCaptain {

    private static final Logger LOG = Global.getLogger(GearInCaptain.class);

    public void run(FleetMemberAPI wolf, PersonAPI personA, FleetDataAPI fleetData) {
        PersonAPI defaultCaptain = wolf.getCaptain();
        LOG.info(String.format(
                "GearIn person lesson captain default: personality=%s name='%s' level=%d",
                defaultCaptain.getPersonalityAPI().getId(), defaultCaptain.getNameString(),
                defaultCaptain.getStats().getLevel()));
        wolf.setCaptain(personA);
        LOG.info(String.format(
                "GearIn person lesson captain set: sameCaptain=%s memberWithCaptainFound=%s",
                wolf.getCaptain() == personA, fleetData.getMemberWithCaptain(personA) == wolf));
    }
}
```

### GearInOfficer

军官（`OfficerDataAPI`）：
- `hegemony.createRandomPerson()` 势力随机人物（随机姓名/肖像/voice，personality 恒 `steady`）→ `createOfficerData` → `addOfficer`（`personB.getFleet() == playerFleet` 联动）；
- `addXP(100000)`（`officerMaxLevel=5` 内不封顶）→ `canLevelUp()`（≥ 2 级阈值 48000）；
- `makeSkillPicks()`（4 个候选）→ `levelUp(pick)`（level 2、技能 1 级）；
- `hammerhead.setCaptain(personB)` 军官上舰 → `removeOfficer`（`getFleet()` 回 null）。

```java
package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 军官（OfficerDataAPI）：势力随机人物 -> 包装入 roster -> XP/技能候选/升级 -> 上舰 -> 移除。
 */
public class GearInOfficer {

    private static final Logger LOG = Global.getLogger(GearInOfficer.class);

    public void run(SectorAPI sector, FleetMemberAPI hammerhead, FleetDataAPI fleetData,
                    CampaignFleetAPI playerFleet) {
        PersonAPI personB = sector.getFaction("hegemony").createRandomPerson();
        LOG.info(String.format(
                "GearIn person lesson personB random: name=%s female=%s faction=%s personality=%s portraitIsDefault=%s",
                personB.getNameString(), personB.isFemale(), personB.getFaction().getId(),
                personB.getPersonalityAPI().getId(), personB.isDefault()));

        OfficerDataAPI officer = Global.getFactory().createOfficerData(personB);
        fleetData.addOfficer(officer);
        LOG.info(String.format(
                "GearIn person lesson officer add: count=%d fleetLinked=%s",
                fleetData.getOfficersCopy().size(), personB.getFleet() == playerFleet));

        long xpBefore = personB.getStats().getXP();
        officer.addXP(100000);
        boolean canLevelUp = officer.canLevelUp();
        officer.makeSkillPicks();
        List<String> picks = officer.getSkillPicks();
        LOG.info(String.format(
                "GearIn person lesson officer xp: xp=%d -> %d canLevelUp=%s picks=%d",
                xpBefore, personB.getStats().getXP(), canLevelUp, picks.size()));
        if (!picks.isEmpty()) {
            String pick = picks.get(0);
            officer.levelUp(pick);
            LOG.info(String.format(
                    "GearIn person lesson officer levelUp: level=%d skill=%s skillLevel=%s",
                    personB.getStats().getLevel(), pick, personB.getStats().getSkillLevel(pick)));
        }

        hammerhead.setCaptain(personB);
        LOG.info(String.format(
                "GearIn person lesson officer captain: sameCaptain=%s memberWithCaptainFound=%s officerInRoster=%s",
                hammerhead.getCaptain() == personB,
                fleetData.getMemberWithCaptain(personB) == hammerhead,
                fleetData.getOfficersCopy().contains(officer)));

        fleetData.removeOfficer(personB);
        LOG.info(String.format(
                "GearIn person lesson officer remove: count=%d fleetLinked=%s",
                fleetData.getOfficersCopy().size(), personB.getFleet() == null));
    }
}
```

### GearInFactionRelation

势力间关系（`FactionAPI` 关系族，hegemony vs tritachyon）：确定性序列 + 同帧回滚——
- `setRelationship(0)` 归一；
- `adjustRelationship(+0.15)` 落 `0.15 FAVORABLE`；
- `adjustRelationship(+0.9, COOPERATIVE)` 封顶 `1.00 changed=true`；
- `setRelationship(HOSTILE)` 落 `-0.50`、`isHostileTo=true`；
- `adjustRelationship(-0.2, VENGEFUL)` 落 `-0.70` **但档位仍 HOSTILE**（数值连续、档位分段）；
- `ensureAtBest(WELCOMING)` 落 `0.49 ensured=true`；
- `setRelationship(原值)` 精确恢复。

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 势力间关系（FactionAPI 关系族）：setRelationship / adjustRelationship / ensureAtBest 确定性序列 + 同帧回滚。
 */
public class GearInFactionRelation {

    private static final Logger LOG = Global.getLogger(GearInFactionRelation.class);

    public void run(SectorAPI sector) {
        FactionAPI hegemony = sector.getFaction("hegemony");
        FactionAPI tritachyon = sector.getFaction("tritachyon");
        float originalRel = hegemony.getRelationship("tritachyon");
        hegemony.setRelationship("tritachyon", 0f);
        LOG.info(String.format(
                "GearIn person lesson factionRel start: original=%.2f normalized=%.2f level=%s",
                originalRel, hegemony.getRelationship("tritachyon"),
                hegemony.getRelationshipLevel("tritachyon")));
        hegemony.adjustRelationship("tritachyon", 0.15f);
        LOG.info(String.format(
                "GearIn person lesson factionRel adjust: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
        boolean cappedChanged = hegemony.adjustRelationship("tritachyon", 0.9f, RepLevel.COOPERATIVE);
        LOG.info(String.format(
                "GearIn person lesson factionRel cap: value=%.2f level=%s changed=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"), cappedChanged));
        hegemony.setRelationship("tritachyon", RepLevel.HOSTILE);
        LOG.info(String.format(
                "GearIn person lesson factionRel hostile: value=%.2f level=%s hostileTo=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"),
                hegemony.isHostileTo("tritachyon")));
        hegemony.adjustRelationship("tritachyon", -0.2f, RepLevel.VENGEFUL);
        LOG.info(String.format(
                "GearIn person lesson factionRel vengefulCap: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
        boolean ensured = hegemony.ensureAtBest("tritachyon", RepLevel.WELCOMING);
        LOG.info(String.format(
                "GearIn person lesson factionRel ensure: value=%.2f level=%s ensured=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"), ensured));
        hegemony.setRelationship("tritachyon", originalRel);
        LOG.info(String.format(
                "GearIn person lesson factionRel restore: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
    }
}
```

**协作关系**：协调器持有共享对象（人物 A、wolf、hammerhead），角色小类以 `run(...)` 参数接收（包内协作，不跨单元），保持"同一人物对象贯穿身份 → 关系 → 注册 → 舰长"的演示语义；

日志前缀统一为 `GearIn person lesson`；人物 A 的标签与 memory key 为 `GearIn_personLesson` / `$GearIn_personLesson`。

调用点（工程片段——方法所在类归插件生命周期页所有，本页只演示调用，不改动那些文件）：

```java
    // GearInModPlugin（插件生命周期页所有）onGameLoad(boolean newGame) 内新增本页注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInPersonScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInPersonScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInPersonScript());
    }
```

构建命令（在 `mods/GearIndustry/` 执行）：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类（可用 `jar tf` 核对）。

本页源码与 fixture 逐字一致（fixture 位于 `tutorial-fixtures/units/S09-P01/src/main/java/data/scripts/gearin/campaign/`），fixture 编译验收命令见"预期结果"。

### 验证方案（基于本例）

- **前置数据**：`gear_in` MOD 布局与插件生命周期类（S06-P01 / S06-P02，含本页调用点片段）、本页 8 个类（与 fixture 同源）、生涯时钟脚本（S08-P01，非必需）。
- **操作**：
  1. fixture 编译（验收 J）：在 Git Bash 执行 `gradle -p tutorial-fixtures compileS09p01Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
  2. 游戏构建：在 `mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类。
  3. 新建生涯：确认 `starsector.log` 按"预期结果"第 3 条顺序出现全部 `GearIn person lesson` 日志行：
     - 含 `start` / `flagship` / `members` / `personA defaults` / `personA identity` / `tagsMemory` / 五条 `relToPlayer` / `market` / `importantPeople`
     - 含 `captain default` / `captain set` / `personB random` / `officer add` / `officer xp` / `officer levelUp` / `officer captain` / `officer remove` / 七条 `factionRel` / `done`
     - 关键核对：`captainIsPlayerPerson=true`、 `personA defaults` 六项默认值、`rank=Commander`、`post=Fleet Commander`、`relToPlayer` 五步 0.00→0.30→0.75→-0.50→0.00
     - `factionRel` 序列 0.00→0.15→1.00→-0.50→-0.70→0.49→原值、`done` 行成员数与 `start` 一致
     - 全程无 `gear_in` 引起的 `ERROR`。
  4. 界面核对：演示日志出现时打开舰队界面（F 键），确认多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成（"预期结果"第 4 条）。
  5. 只执行一次：等待若干游戏日后日志无新增 `GearIn person lesson` 行；保存并读档后仍无新增（`REGISTER_MARK` 门已关）；读档后势力关系面板中 hegemony 与 tritachyon 的数值与演示前一致（同帧回滚，"预期结果"第 5 条）。
  6. 故障注入 1：注释掉调用点片段的 `addScript` 行，重启新档：无任何 `GearIn person lesson` 日志（对应"实例驱动故障分析"第 1 条的前半——注册缺失）；改回后确认恢复。
  7. 故障注入 2：把 `GearInPersonIdentity` 里的 `personA.setPersonality("reckless")` 改为 `"bogus"`，重启新档后保存：`starsector.log` 出现保存时的 `NullPointerException`（对应"实例驱动故障分析"第 2 条）；改回后确认恢复。
    8. 故障注入 3：注释掉 `GearInFactionRelation` 里的 `setRelationship("tritachyon", originalRel)` 恢复行，重启新档并读档
       - 势力关系面板中 hegemony 对 tritachyon 的数值停在 `0.49`（`ensureAtBest` 的残留），证明回滚必要（对应规则强度表"演示同帧回滚"与"实例驱动故障分析"第 9 条）
       - 改回后确认恢复。
- **通过信号**：操作 1–5 全部符合；操作 6–8 的注入与恢复都符合预期。
- **清理**：取消勾选测试 MOD；确认没有对共享文件（如 fixture 构建脚本）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileS09p01Java --no-daemon
```
以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类。
3. **新建生涯（首次进入存档后第一帧）**：`starsector.log` 依次出现
```text
GearIn person lesson start: members=<N> playerFaction=player playerPerson=<玩家名> GearIn person lesson flagship: name=<旗舰船名> captainIsPlayerPerson=true GearIn person lesson members: added wolf=<船名> hammerhead=<船名> count=<N+2> GearIn person lesson personA defaults: id=p_<uid> personality=steady name='' rankId=spaceCommander postId=fleetCommander faction=neutral portraitIsDefault=true importance=MEDIUM GearIn person lesson personA identity: name=Aster Vance female=true rank=Commander post=Fleet Commander personality=reckless faction=hegemony voice=<soldier|official|aristo 之一> GearIn person lesson tagsMemory: hasTag=true tags=1 memory=42 GearIn person lesson relToPlayer default: rel=0.00 level=NEUTRAL hostile=false GearIn person lesson relToPlayer adjust: rel=0.30 level=WELCOMING GearIn person lesson relToPlayer cooperative: rel=0.75 level=COOPERATIVE GearIn person lesson relToPlayer hostile: rel=-0.50 level=HOSTILE hostile=true GearIn person lesson relToPlayer reset: rel=0.00 level=NEUTRAL GearIn person lesson market: id=jangala inAfterAdd=true personMarketLinked=true inAfterRemove=false GearIn person lesson importantPeople: containsAfterAdd=true dataLinked=true containsAfterRemove=false GearIn person lesson captain default: personality=steady name='' level=1 GearIn person lesson captain set: sameCaptain=true memberWithCaptainFound=true GearIn person lesson personB random: name=<随机名> female=<true|false> faction=hegemony personality=steady portraitIsDefault=false GearIn person lesson officer add: count=<M+1> fleetLinked=true GearIn person lesson officer xp: xp=<X> -> <X+100000> canLevelUp=true picks=4 GearIn person lesson officer levelUp: level=2 skill=<技能id> skillLevel=1.0 GearIn person lesson officer captain: sameCaptain=true memberWithCaptainFound=true officerInRoster=true GearIn person lesson officer remove: count=<M> fleetLinked=true GearIn person lesson factionRel start: original=<O> normalized=0.00 level=NEUTRAL GearIn person lesson factionRel adjust: value=0.15 level=FAVORABLE GearIn person lesson factionRel cap: value=1.00 level=COOPERATIVE changed=true GearIn person lesson factionRel hostile: value=-0.50 level=HOSTILE hostileTo=true GearIn person lesson factionRel vengefulCap: value=-0.70 level=HOSTILE GearIn person lesson factionRel ensure: value=0.49 level=WELCOMING ensured=true GearIn person lesson factionRel restore: value=<O> level=<原档位> GearIn person lesson done: members=<N> (initial <N>)
```
其中 `<N>` 是初始成员数、`<M>` 是初始军官数、`<O>` 是存档里 hegemony 对 tritachyon 的关系原值、`<X>` 是人物 B 初始 XP（新档为 0），均以实际日志为准；`voice` 是 hegemony `HIGH` 声音池三选一；`picks=4` 是新档典型值（以日志为准，≥1）；`skill` 是重掷后的第一个军官技能候选。关系序列各行的数值/档位是**确定性**的： `0.15→FAVORABLE`、`1.00→COOPERATIVE changed=true`、`-0.50→HOSTILE hostileTo=true`、`-0.70 档位仍 HOSTILE`、 `0.49→WELCOMING ensured=true`、`restore` 与原值一致。演示只执行一次，之后无任何 `GearIn person lesson` 日志。
4. **游戏内舰队界面（F 键）**：演示期间（日志可见时）舰队界面多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。
5. **读档**：不出现任何 `GearIn person lesson` 日志（`REGISTER_MARK` 门已关、脚本不再注册）；玩家舰队与演示前一致；`hegemony` 与 `tritachyon` 的关系面板数值与演示前一致（同帧回滚生效）。
6. **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。

静态自查：全部 8 个类源码与 fixture 逐字一致（包括 import 顺序）；`REGISTER_MARK` 与调用点片段的键逐字一致；没有把工作单元编号写进任何运行时名称（类名、包名、persistent data 键、tag、memory key 均不含 S09-P01 变体）；示例只消费既有 GearIn ID 与原版 ID（无新增资源 ID）。

## 实例驱动故障分析

1. **人物"凭空消失"（读档后不存在）**。触发：人物只存在于演示/事件脚本的局部变量里，没挂进任何持久集合。可观察：同会话内一切正常（人物对象仍可达），保存并读档后人物不见了，无任何报错。修正：跨存档人物必须挂入至少一个持久集合（成员 captain / 市场 people+comm / important people / officer roster，见"归属四入口"）。
  2. **非法 personality id → 保存时 NPE**。触发：`setPersonality("bogus")`（`personalities.csv` 没有的 id）
     - 可观察：`getPersonalityAPI()` 返回 null；保存时写档失败（`NullPointerException`）
     - 修正：只用注册表内的 id（原版 5 个：`timid`/`cautious`/`steady`/`aggressive`/`reckless`）；CSV spec 加载期的查询对未知 id 静默返回 null、不报错，所以问题延迟到使用点。
3. **`adjustRelationship` 没效果（返回值 false、值不变）**。触发：`adjustRelationship(id, 0f, level)` 想"确认档位"，或 delta 方向与当前值方向相反且被 limit 挡住。可观察：返回值 false，关系值不变（三参版 delta 为 0 直接返回 false，`FactionAPI.adjustRelationship`）。
   - 修正："设档位"用 `setRelationship(id, RepLevel)`；"确保档位"用 `ensureAtBest` / `ensureAtWorst`。
4. **档位与数值对不上**。触发：`setRelationship(id, HOSTILE)` 落 `-0.50` 后 `adjustRelationship(id, -0.2f, VENGEFUL)`，期待档位"更敌对"。可观察：值变 `-0.70`，但档位仍是 `HOSTILE`（`-0.70` 未越过 0.74 边界；`RepLevel.getLevelFor` 按百分制整数分段）。
   - 修正：理解"数值连续、档位分段"；档位跳变点只在 0.09 / 0.24 / 0.49 / 0.74（负向对称）。
5. **市场有人物但通信目录/联系人界面查不到**。触发：只 `market.addPerson(person)`。可观察：`getPeopleCopy()` 包含该人物，通信目录没有条目（`MarketAPI.addPerson` 不自动建目录条目；前序页面已记录同一规则）。
   - 修正：`market.getCommDirectory().addPerson(person)` 另行维护；移除可见联系人时先移 comm entry 再移 roster。
6. **军官/佣兵不加 XP**。触发：给 memory `$isMercenary` 为 true 的人物 `addXP`。可观察：XP 不变（`OfficerDataAPI.addXP` 直接返回）。修正：确认人物不是佣兵（`com.fs.starfarer.api.util.Misc.isMercenary` 读 `$isMercenary`）；解除佣兵标记再给 XP。
7. **军官技能选项被重掷**。触发：玩家已看到 `getSkillPicks()` 列表后再次调用 `makeSkillPicks()`。可观察：列表内容变化，与展示给玩家的选项不一致（`OfficerDataAPI.makeSkillPicks`：清空后重掷）。修正：只在需要生成选项时调用一次；展示期间维持同一 `OfficerDataAPI` 对象状态。
  8. **checkout 未归还影响按条件选人**。触发：`checkOutPerson(person, "permanent_staff")` 后不配对 `returnPerson`
     - 可观察：`ImportantPeopleAPI.getPerson(...)` / `getMatching(...)` 按同 reason 过滤时该人物不再被选中（`MarketPostPersonFilter.accept` → `canCheckOutPerson` 拒绝），任务/事件生成总是选不到它
     - 修正：用完必须 `returnPerson(person, reasonId)`。
9. **演示中断残留**。触发：演示帧中途抛异常（例如把市场段挪到重要人物段之后且市场查询失败），`addPerson` 与 `removePerson` 未配对。可观察：人物残留在 important people / 市场列表里并**随存档保存**；`REGISTER_MARK` 已置位，读档不重放、残留无法自动清理。
   - 修正：保持同帧配对顺序（先 add 后立刻 remove）；演示类不要随意调换段序；失败重试可手动清 `REGISTER_MARK` 键。
10. **人物对玩家关系与势力对玩家关系混用**。触发：用 `person.getRelToPlayer()` 推断势力声望，或用 `faction.getRelationship("player")` 推断人物好感。可观察：两者是不同存储（人物字段 `relToPlayer` 与势力侧的关系存储是两套），数值互不同步。
    - 修正：人物→`RelationshipAPI`；势力→`FactionAPI` 关系族（`getRelToPlayer()` 是势力侧包装，目标仍是势力关系）。
11. **给玩家旗舰换舰长读回还是玩家**。触发：`playerFleet.getFlagship().setCaptain(myPerson)` 后读 `getCaptain()`。可观察：读回仍是玩家人物（旗舰特例覆盖读路径，`FleetMemberAPI.getCaptain`）。修正：自定义舰长放非旗舰成员（本页演示即如此）；旗舰身份由玩家人物固定。
12. **`getMemory()` 高频调用导致插件回调风暴**。触发：每帧用 `person.getMemory()` 读记忆。可观察：每次调用触发全部插件的 `updatePersonFacts`（`PersonAPI.getMemory`），日志量/性能异常，其他插件可能改写内存值。
    - 修正：纯读取用 `getMemoryWithoutUpdate()`（本页 `GearInPersonIdentity` 即此写法）。
13. **读档后"默认舰长没了"**。触发：以为每个成员都恒定有一个舰长对象。可观察：`steady + 空姓名 + level ≤ 1` 的默认舰长在保存时被压缩（不落盘），读档后 `getCaptain()` 延迟重建新默认舰长——每次读档默认舰长都是新对象。
    - 修正：需要稳定身份就显式 `setCaptain` 自定义人物（会随成员保存）。

## 总结

本页把人物讲成四条链：

- **创建链**：`Global.getFactory().createPerson()`（空白）/ `FactionAPI.createRandomPerson()`（势力随机）→ `PersonAPI` 默认值（`steady`、空名、`spaceCommander`/`fleetCommander`、`neutral`、默认头像、`MEDIUM`）
  - 身份写入（姓名/性别/rank/post/势力/头像 /personality/importance+voice/tags/memory）
  - rank/post 显示名经势力解析、回退 `default_ranks.json`；记忆读取必须分 `getMemory()`（触发全插件回调）与 `getMemoryWithoutUpdate()`（纯读）。
- **关系链**：数值存储（势力间关系延迟创建、默认 0、同势力 1、恒钳 [-1,1]；人物对玩家 `relToPlayer` 延迟创建）
  - 写入族（`setRelationship` 按档位落点 / `adjustRelationship` 加减与 limit 边界 / `ensureAtBest` / `ensureAtWorst`）
  - 读取族 （`getRelationship` / `getRelationshipLevel` / `isHostileTo` / `getRelToPlayer`）
  - `RepLevel` 九档阈值 0.09 / 0.24 / 0.49 / 0.74，边界含于高位档。
- **captain 链**：`FleetMemberAPI.setCaptain(person)`（生涯侧，成员 stats 重算；默认舰长 `steady` 保存时压缩；**玩家旗舰特例返回玩家人物**）
  - 部署时 captain 与 fleet commander 传入战斗舰船 → `ShipAPI.getCaptain()` / `getOriginalCaptain()` / `setCaptain()`（战斗侧换舰长即换统计与 AI personality）。
- **officer 链**：`createOfficerData(person)` → `FleetDataAPI.addOfficer`（roster + `person.setFleet`）
  - `addXP`（`officerLevelUp` 插件；佣兵不加 XP）→ `canLevelUp` / `makeSkillPicks`（每次重掷）→ `levelUp(skillId)` → `setCaptain` 上舰 → `removeOfficer`。

记忆要点：

- 人物默认值表是调试基准：`personality=steady`、`name=''`、`rankId=spaceCommander`、`postId=fleetCommander`、`faction=neutral`、`portraitIsDefault=true`、`importance=MEDIUM`。
- 关系四件事：数值连续、档位分段、limit 只是单次调整边界、数值恒钳 [-1, 1]；"设档位"用 `setRelationship(id, RepLevel)`。
- 持久归属四入口（成员 captain / 市场 people+comm / important people / officer roster）；局部变量人物不保存、不报错。
- 三个"每次都会发生"的陷阱：`getMemory()` 触发全插件回调、`makeSkillPicks()` 重掷、默认舰长保存时压缩。
- 玩家旗舰的 captain 是玩家人物（特例），不要试图覆盖读回。

人物对象链到这里已经走通完整流程。

顺着它继续向前：人物是任务、贸易与通信系统的共同实体——任务与事件的选人规则建立在 important people 的 checkout 机制上，市场侧的联系人体系建立在人物归属之上。

本节的身份、关系与归属语义是这些使用端的共同底座，后续遇到"造一个人、调整它与玩家或势力的关系、把它挂进某个持久集合"的需求时，都可以回到本节的四条链上定位。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物接口全貌——身份、rank/post、头像、势力、记忆、标签、personality、importance/voice、AI core、归属、角色数值与联系。
- `com.fs.starfarer.api.FactoryAPI`：人物创建入口（`createPerson` / `createOfficerData`）。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力关系族（`adjustRelationship` 两版 / `setRelationship` 两版 / `ensureAtBest` / `ensureAtWorst` / `getRelationshipLevel` / `isHostileTo`）与 `createRandomPerson`。
- `com.fs.starfarer.api.campaign.RepLevel`：九档声誉档位枚举与阈值判定（`getLevelFor` / `getMin` / `getMax` / `isAtWorst` / `isPositive` / `isNegative`）。
- `com.fs.starfarer.api.characters.RelationshipAPI`：人物对玩家关系（`getRel` / `setRel` / `getLevel` / `setLevel` / `adjustRelationship` / `ensureAtBest` / `ensureAtWorst` / `isHostile`）。
- `com.fs.starfarer.api.characters.OfficerDataAPI`：军官包装（`getPerson` / `setPerson` / `addXP` / `canLevelUp` / `makeSkillPicks` / `getSkillPicks` / `levelUp`）。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：角色数值（`getLevel` / `getXP` / `getSkillLevel` / `increaseSkill` / `refreshCharacterStatsEffects`）。
- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：重要人物集合（`addPerson` / `containsPerson` / `removePerson` / `getData` / `checkOutPerson` / `returnPerson`）。
- `com.fs.starfarer.api.characters.MarketPostPersonFilter`：按条件选人的 checkout 过滤。
- `com.fs.starfarer.api.characters.FullName`：姓名对象与 `Gender` 枚举。
- `com.fs.starfarer.api.campaign.PersonImportance`：重要性五档与 `getValue()`。
- `com.fs.starfarer.api.campaign.SectorAPI`：`getEconomy` / `getPlayerPerson` / `getImportantPeople` / `getPlayerFaction` / `getFaction`。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队指挥官（`getCommander` / `setCommander` / `getCommanderStats`）与 `getFlagship`。
- `com.fs.starfarer.api.campaign.FleetDataAPI`：军官集合（`getOfficersCopy` / `addOfficer` / `removeOfficer`）与 `getMemberWithCaptain`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员舰长（`getCaptain` / `setCaptain` / `getShipName`）。
- `com.fs.starfarer.api.combat.ShipAPI`：战斗侧舰长（`getCaptain` / `getOriginalCaptain` / `setCaptain` / `getFleetCommander`）。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场人物（`getCommDirectory` / `addPerson` / `removePerson` / `getPeopleCopy`）。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：rank/post 常量（`SPACE_COMMANDER` / `POST_FLEET_COMMANDER` 等）。
- `com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`：`officerLevelUp` 插件实现（XP 阈值、每级候选）。
- `com.fs.starfarer.api.plugins.OfficerLevelupPlugin`：军官升级插件接口。
- `com.fs.starfarer.api.util.Misc`：`isMercenary`（读 memory `$isMercenary`）。
- `data/characters/personalities.csv`：personality 注册表（原版 5 个 id、任务权重列）。
- `data/characters/person_names.csv`：姓名池（usage `f`/`l`、category）。
- `data/world/factions/default_ranks.json`：rank/post 显示名的默认来源（`spaceCommander` / `fleetCommander`）。
- `data/world/factions/hegemony.faction`：`ranks` 段覆写与 `voices` 声音池（`LOW` / `MEDIUM` / `HIGH`）。
- `data/config/settings.json`：`default_portrait`、`officerLevelUp` 插件、`officerXPRequiredMult`、`officerMaxLevel`。
- `tutorial-fixtures/units/S09-P01/src/main/java/data/scripts/gearin/campaign/`：本页 8 个类的 fixture 源。
- `tutorial-fixtures/build.gradle`：`compileS09p01Java` 编译任务（source set `s09p01` 含本单元）。
