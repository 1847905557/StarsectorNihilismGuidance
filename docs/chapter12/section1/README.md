# 创建人物并设置关系

本节创建人物并设置身份、关系与持久归属。人物会在任务、贸易和通信功能中反复使用，因此示例同时确认舰长与军官的分配方式。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 创建空白或势力随机人物，并设置身份、档案与记忆字段；
- 使用关系数值与 `RepLevel` 读取和限制人物关系；
- 把人物分配为舰长或军官，并观察成员数值变化；
- 将人物挂入能够随存档保存的归属对象；
- 根据界面和日志定位关系、分配与持久化故障。

## 前置知识

- [C4-S1] 的生涯与战役域区别；
- [C7-S1] 的插件生命周期回调与持久数据标记；
- [C7-S2] 的 `Global.getFactory()` 创建入口与阶段语义；
- [C10-S1] 的生涯脚本注册与一次性判断；
- [C10-S3] 的舰队成员、舰长、旗舰与 `FleetDataAPI`；
- [C11-S3] 的市场人物、通信目录与 `MarketAPI.addPerson` / `removePerson` / `getPeopleCopy`。

## 术语

按术语约定，本节首次完整讲解以下词：

- **人物 (person)**：API 名保留 `PersonAPI`。生涯里代表一个 "人" 的对象，承载身份 (姓名/性别/rank/post/头像/势力)、记忆、标签、对玩家关系、角色数值与归属 (captain、officer、市场、important people)。
- **关系 (relationship)**：两两之间的声望数值与档位。势力之间/势力对玩家走 `FactionAPI` 关系族；人物对玩家走 `RelationshipAPI`。数值范围 \[-1, 1\]，档位由 `RepLevel` 分段。
- **声誉档位 (RepLevel)**：API 名保留 `RepLevel`。
    - 九档枚举：`VENGEFUL` / `HOSTILE` / `INHOSPITABLE` / `SUSPICIOUS` / `NEUTRAL` / `FAVORABLE` / `WELCOMING` / `FRIENDLY` / `COOPERATIVE`。
    - 正向阈值 0.09 / 0.24 / 0.49 / 0.74，负向对称。
- **captain(舰长)**：舰队成员的指挥官。决定该成员/战斗舰船的统计修正与战斗 AI personality。
- **officer(军官)**：`OfficerDataAPI` 包装的人物，带 XP、等级与技能选择；隶属舰队 roster(`FleetDataAPI` 军官集合)，被 `setCaptain` 到某个成员上时指挥该船。

## 人物对象与 API 面

### 人物创建入口速查表

创建人物的两个正式入口 (`com.fs.starfarer.api.FactoryAPI`)：

| 入口 | 语义 |
|---|---|
| `Global.getFactory().createPerson()` | 创建空白人物 |
| `FactionAPI.createRandomPerson()`(含 Gender / Random 变体) | 按势力生成随机人物：随机性别默认各半、势力肖像、随机姓名 (姓名池)、随机 voice |

另有包装入口 `Global.getFactory().createOfficerData(PersonAPI)`，把人物包装成 `OfficerDataAPI`。

`SettingsAPI` 也有同签名 `createPerson`(同样返回空白人物)，习惯用 `Global.getFactory()`。

### 人物默认值速查表

人物对象的默认值如下：

| 字段 | 默认值 | 说明 |
|---|---|---|
| id | `"p_" + Misc.genUID()` | 每个实例唯一 |
| personalityId | `"steady"` | 无参构造默认即 `"steady"` |
| name | `FullName("", "", Gender.ANY)` | 空姓名 |
| portraitSprite | `SettingsAPI.getSpriteName("misc", "default_portrait")` | `settings.json` → `graphics/portraits/portrait_generic_grayscale.png` |
| rankId / postId | `Ranks.SPACE_COMMANDER` / `Ranks.POST_FLEET_COMMANDER` | `spaceCommander` / `fleetCommander` |
| factionId | `"neutral"` | — |
| importance | `PersonImportance.MEDIUM` | 枚举值 0/0.25/0.5/0.75/1 |
| contactWeight | `1.0F` | — |
| relToPlayer | null(延迟创建) | 首次 `getRelToPlayer()` 才创建 |

`isDefault()` 表示头像等于默认头像；`isPlayer()` 表示人物对象即 `Global.getSector().getPlayerPerson()`；`isAICore()` 表示 `aiCoreId != null`。

玩家人物同样以 `steady` 起步，新档创建时人物势力为 `"player"`。

### 身份与档案速查表

创建之后的人物只有一组默认值，身份与档案字段按功能分组；`PersonAPI`(`com.fs.starfarer.api.characters.PersonAPI`) 成员按功能分组如下：

| 分组 | 成员 | 语义 |
|---|---|---|
| 身份 | `getId()` / `setId(String)`、`getName()` / `setName(FullName)`、`getNameString()`、`getGender()` / `setGender(Gender)`、`isMale()` / `isFemale()` | id 唯一；姓名是**可变对象** `FullName`(`(first + " " + last).trim()`)；性别挂在姓名上 |
| rank/post | `getRankId()` / `setRankId(String)`、`getPostId()` / `setPostId(String)`、`getRank()` / `getPost()`、`getRankArticle()` / `getPostArticle()` | id 是存储值；显示名经势力解析 |
| 头像 | `getPortraitSprite()` / `setPortraitSprite(String)` | 资源路径字符串 (不校验存在性) |
| 势力 | `getFaction()`、`setFaction(String factionId)` | `getFaction()` 延迟解析势力对象 (按 id 从全局势力表解析)；`setFaction` 只写 id |
| 关系 | `getRelToPlayer()` | 人物对玩家关系 |
| 记忆 | `getMemory()` / `getMemoryWithoutUpdate()` | 记忆读写；`getMemory()` 触发插件回调 |
| 标签 | `hasTag` / `addTag` / `removeTag` / `getTags` / `clearTags` | 运行时标签集合 (`getTags()` 缺省返回空集) |
| personality | `getPersonalityAPI()`、`setPersonality(String)` | 由 `personalities.csv` 注册 |
| importance/voice | `getImportance()` / `setImportance(PersonImportance)`、`setImportanceAndVoice(PersonImportance, Random)`、`getVoice()` / `setVoice(String)` | voice 存储在 memory `$voice`；`setImportanceAndVoice` 只在 voice 未被显式覆写时写 voice |
| AI core | `getAICoreId()` / `setAICoreId(String)`、`isAICore()` | `aiCoreId != null` 即 AI 核心人物 |
| 归属 | `getFleet()` / `setFleet(CampaignFleetAPI)`、`getMarket()` / `setMarket(MarketAPI)` | officer 所在舰队、当前市场 |
| 角色数值 | `getStats()`、`setStats(MutableCharacterStatsAPI)`、`getFleetCommanderStats()` | 等级/XP/技能/故事点；`getFleetCommanderStats()` = `fleet.getCommanderStats()`，无舰队时 null |
| 联系 | `wantsToContactPlayer()` / `incrWantsToContactReasons()` / `decrWantsToContactReasons()`、`getContactWeight()` / `setContactWeight(float)` | 联系人与权重 (`wantsToContactReasonsCount`) |

personality id 必须来自注册表

`personalities.csv` 之外的 id 使 `getPersonalityAPI()` 返回 null(CSV spec 加载期的查询对未知 id 静默返回 null、不报错)。

问题在首次解引用返回的 null personality 时暴露 (`NullPointerException`)。

原版合法 id 恰 5 个：`timid` / `cautious` / `steady` / `aggressive` / `reckless`。

**rank/post 解析**：`getRank()` = `getFaction().getRank(rankId)`；`FactionAPI.getRank` 先查势力 spec 的 ranks 段，查不到回退默认值 (数据源 `data/world/factions/default_ranks.json`)。

`getRankArticle()` / `getPostArticle()` 按显示名首字母是否元音返回 `"an"` / `"a"`。

示例：`spaceCommander` 在 `default_ranks.json` 是 "Commander"，`hegemony.faction` 覆写为同名 "Commander"；`fleetCommander` post 是 "Fleet Commander"。

**记忆**：`getMemory()` 触发全部插件的 `updatePersonFacts(person, memory)` 回调 (与 `MarketAPI.getMemory()` 同族)；纯读取**必须**用 `getMemoryWithoutUpdate()`(同样延迟创建但无回调)。

voice 即 memory `$voice`。

为什么记忆读取要分两条路径

`getMemory()` 先跑一遍全部插件的 `updatePersonFacts` 回调，让各插件在读取前按最新事实刷新记忆，即 "读前更新" 的设计；但高频读取 (如每帧) 也走这条路径就会变成回调风暴，其他插件还可能改写内存值。

纯读取用 `getMemoryWithoutUpdate()`：同样延迟创建记忆对象，但没有回调。

**人物数据文件** (加载链：CSV spec 加载期读入、姓名池)：`data/characters/personalities.csv` (`id,name,desc,bravery` + 任务权重列)

原版 id 为 `timid` / `cautious` / `steady` / `aggressive` / `reckless`，bravery 全为 5。

`data/characters/person_names.csv` (姓名池，usage `f`/`l`，category 如 `fringe`/`future`/`pirate`，随机姓名按势力 spec 的名字段加权)。

`personalities.csv` 的权重列 (`CAPTURE`/`PATROL`/`INTERCEPT`/`ASSAULT` 等) 是舰船 AI 行为权重。

### 关系

身份字段之外，人物之间以及人物与玩家之间还有一套数值体系。关系分三族、两套存储，先看存储与钳制，再看档位与写入方法。

### 存储与钳制

势力关系按 `"<id1>_<id2>"` 键存储，**双向插入同一对象**；读取时延迟创建：默认 `0.0F`，**同势力之间恒 `1.0F`**。

写入时值被钳制在 \[-1, 1\] —— 关系数值不可能越界。

人物对玩家关系存在人物字段 `relToPlayer`(延迟创建)，`RelationshipAPI.setRel` 同样钳制 \[-1 , 1\]。

### RepLevel 档位速查表

`RepLevel`(`com.fs.starfarer.api.campaign.RepLevel`) 九档，正向阈值 `T1=0.09` / `T2=0.24` / `T3=0.49` / `T4=0.74`，负向对称。

| 档位 | `setRelationship(id, RepLevel)` 写入值 |
|---|---|
| `VENGEFUL` | `-0.75` |
| `HOSTILE` | `-0.50` |
| `INHOSPITABLE` | `-0.25` |
| `SUSPICIOUS` | `-0.10` |
| `NEUTRAL` | `0.0` |
| `FAVORABLE` | `0.10` |
| `WELCOMING` | `0.25` |
| `FRIENDLY` | `0.50` |
| `COOPERATIVE` | `0.75` |

档位判定 `getLevelFor(float)`：取整到百分制再与阈值比，**边界含于高位档** (如 0.49 → `WELCOMING`，0.50 → `FRIENDLY`)。

`isPositive()` 表示从 `FAVORABLE` 起 (`isAtWorst(FAVORABLE)`)；`isNegative()` 表示 `SUSPICIOUS` 及以下。

落点公式：正档写入 `min + 0.01`，负档写入 `-(min + 0.01)`，`NEUTRAL` 写入 `0.0`。

### FactionAPI 关系族速查表

`FactionAPI`(`com.fs.starfarer.api.campaign.FactionAPI`) 关系成员：

| 成员 | 语义 |
|---|---|
| `adjustRelationship(String id, float delta)` | 直接加 delta(无 limit，只受 ±1 钳制) |
| `adjustRelationship(String id, float delta, RepLevel limit)` | 带 limit：delta 为 0 直接返回 false；正 delta 上限 = `limit.getMax()`(limit 为负档时 = `-min-0.01`)，负 delta 下限 = `-limit.getMax()`(limit 为正档时 = `min+0.01`)；调整后值仍在限内 → **返回 true 并写入**，仅 delta 为 0 或被 limit 完全拦截 (值未变化) 时返回 false |
| `setRelationship(String id, float newValue)` / `setRelationship(String id, RepLevel level)` | 精确写值 / 按档位落点 |
| `ensureAtBest(String id, RepLevel)` / `ensureAtWorst(String id, RepLevel)` | 当前档位不满足时写入边界值并返回 true |
| `getRelationship(String id)` / `getRelationshipLevel(String)` / `getRepInt(String)` | 值 / 档位 / 百分制整数 (`RepLevel.getRepInt`) |
| `isAtBest` / `isAtWorst`、`isHostileTo(String)` | 档位比较；`isHostileTo` = `isAtBest(HOSTILE)` |
| `getRelToPlayer()` | 玩家视角关系包装 (内部转发 `adjustRelationship` 到自己 id) |

limit 只是 "本次调整的方向边界"

`adjustRelationship(delta, limit)` 的 limit 不是 "目标档位" —— 从 1.00 减 0.5(limit `VENGEFUL`) 落在 0.50(`FRIENDLY`)，而不是 `HOSTILE`。

"设置档位" 用 `setRelationship(id, RepLevel)`，"确保不高于/不低于某档" 用 `ensureAtBest` / `ensureAtWorst`。

### 人物对玩家关系 (RelationshipAPI)

`RelationshipAPI`(`com.fs.starfarer.api.characters.RelationshipAPI`)：`getRel()` / `setRel(float)`、`getLevel()` / `setLevel(RepLevel)`、`adjustRelationship(float delta, RepLevel limit)`

`isAtBest` / `isAtWorst`、`ensureAtBest` / `ensureAtWorst`、`isHostile()`、`getRepInt()`

语义与 `FactionAPI` 关系族一致 (`RelationshipAPI` 是同一套逻辑的孪生实现)。

`RelationshipTarget` 标记目标类型 `FACTION` / `PERSON` / `PLAYER`；`isPlayer()` 判断目标人物 == `Global.getSector().getPlayerPerson()`。

接近零的人物对玩家关系在保存时不落盘，读档后恢复默认值。

### captain 与 officer

关系决定声望，岗位决定行为。接下来把人物放上舰长与军官两个实际岗位。

#### 生涯侧 captain(FleetMemberAPI)

- 默认舰长：成员自带默认舰长 `steady`(前序页面已记录 "自动补默认舰长")。保存压缩时，**steady + 空姓名 + level ≤ 1 的默认舰长不落盘** —— "读档后默认舰长消失" 是正常语义。
- `FleetMemberAPI.getCaptain()`：**玩家旗舰特例** —— 成员是玩家舰队旗舰时返回玩家人物。
    - 否则返回缓存的 `captain` 字段 (null 时延迟创建默认舰长并设势力)。
    - `FleetMemberAPI.setCaptain(PersonAPI)` 写回并触发成员 stats 重算。

玩家旗舰的舰长读回恒是玩家人物

成员是玩家舰队旗舰时 `getCaptain()` 直接返回玩家人物，读路径覆盖写入 —— 把自定义舰长挂到旗舰再读回，读到的仍是玩家人物。自定义舰长请放非旗舰成员 (本节示例即如此)。

- 舰长对成员的影响：`captain.getStats().applyPersonalToStats(stats, hullSize)` 把舰长个人技能应用到成员 `MutableShipStatsAPI`。
- 反查：`FleetDataAPI.getMemberWithCaptain(PersonAPI)` 按同一性找舰长所在成员。
- 舰队指挥官：默认是 `steady` 人物；`CampaignFleetAPI.getCommander()` / `setCommander(PersonAPI)`：
    - 新档 `setPlayerFleet` 把玩家人物同时设为旗舰舰长与舰队指挥官。

#### 战斗侧 captain(ShipAPI)

- 部署链：成员部署为战斗舰船时，`getCaptain()` 与 `getFleetCommander()` 传入战斗侧：
    - `ShipAPI.getCaptain()`(`com.fs.starfarer.api.combat.ShipAPI`) 返回战斗舰船舰长，`getOriginalCaptain()` 返回部署初始舰长，`getFleetCommander()` 返回关联的舰队指挥官。
- **中途换舰长会改统计**：`ShipAPI.setCaptain(PersonAPI)` 先解除旧舰长的个人技能 (含子模块)，再挂新舰长。
- **personality 决定战斗 AI**：战斗舰船的 personality 取舰长 personality id(舰长缺失时用装配的 fallback personality)。

#### officer(OfficerDataAPI)

- 包装与 roster：`Global.getFactory().createOfficerData(person)`(`com.fs.starfarer.api.FactoryAPI`)：
    - → `FleetDataAPI.addOfficer(PersonAPI)` / `addOfficer(OfficerDataAPI)` / `getOfficersCopy()` / `removeOfficer(PersonAPI)`(`com.fs.starfarer.api.campaign.FleetDataAPI`)。
    - `FleetDataAPI`：`addOfficer` 写入 roster 并 `person.setFleet(fleet)`(PersonAPI 版还会把人物势力设为舰队势力)。
    - `removeOfficer` 移出并 `setFleet(null)`。
- `OfficerDataAPI`(`com.fs.starfarer.api.characters.OfficerDataAPI`)：
    - `getPerson()` / `setPerson(PersonAPI)`、`addXP(long[, TextPanelAPI[, boolean]])`、`canLevelUp()`；
    - `makeSkillPicks([Random])`、`getSkillPicks()`、`levelUp(String skillId[, Random])`。
- 升级链 (`OfficerDataAPI`)：
    - `addXP` 走 `settings.json` 的 `officerLevelUp` 插件 (`com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`)。
    - **`$isMercenary` 记忆为 true 的佣兵人物 addXP 直接返回** (`Misc.isMercenary` 读 memory `$isMercenary`，`com.fs.starfarer.api.util.Misc`)。
    - XP 达到下一级阈值 (`TOTAL_XP_PER_LEVEL × officerXPRequiredMult(4)`，`com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`) 且等级 ≤ `officerMaxLevel(5)`(settings) 时可升级。
    - `canLevelUp()` 满足时 `addXP` 自动 `makeSkillPicks()`；`makeSkillPicks()` **每次调用都重掷选项**。
    - `levelUp(skillId)` 等级 +1、技能 +1 级并清空重掷。
- 军官上舰指把 officer 人物 `setCaptain` 到成员上 (原版军官面板的 "命令舰船" 即此语义)。

#### 归属四入口 (持久化边界)

岗位分配完之后，真正要紧的问题是：人物到底随什么保存？四个持久归属入口给出答案。

人物只随 "引用它的持久集合" 保存：

1. **成员舰长**：`FleetMemberAPI.setCaptain(person)`(成员在 `FleetDataAPI` 里)；
2. **市场人物**：`MarketAPI.addPerson(person)`(同时写 `person.setMarket(market)`)**，通信目录条目必须另行 `getCommDirectory().addPerson(person)` 维护** (`MarketAPI.addPerson` 不会自动建目录条目)；
3. **重要人物**：`SectorAPI.getImportantPeople()` → `ImportantPeopleAPI.addPerson` / `containsPerson` / `removePerson` / `getData(person)`(`com.fs.starfarer.api.characters.ImportantPeopleAPI`)：
    - `checkOutPerson(person, reasonId)` / `returnPerson(person, reasonId)`；
4. **officer roster**：`FleetDataAPI.addOfficer`。

只存在于局部变量、未被任何持久集合引用的人物不会进入存档；没有其它强引用后，才可能由垃圾回收器回收，具体时机不由帧边界保证。

市场人物不等于通信目录联系人

`MarketAPI.addPerson` 只把人物放进市场 roster，不会自动建通信目录条目，人物在市场人物列表与存档里，但通信目录/联系人 UI 不出现。

要人物作为联系人可见，必须另行 `getCommDirectory().addPerson(person)` 维护；移除可见联系人时先移 comm entry 再移 roster。

### 注册入口

归属决定保存，注册决定何时执行。本节示例注册**必须**发生在生涯引擎建立之后：沿用生涯回调 `onGameLoad(boolean newGame)`，用各自的 persistent data 键做一次注册判断 (不要把注册键挂进插件既有的 `CLASS_MARK` 门内)。

API 面里最反直觉的几处已经标了容器警告，下面把必须遵守的约束连同违反后果收进规则强度速查表。

## 规则强度速查表

本节必须遵守的规则清单 ("必须/禁止"，均附违反后的可观察结果)：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 纯读取人物记忆用 `getMemoryWithoutUpdate()`；`getMemory()` 触发全部插件 `updatePersonFacts` | 必须 | 每次调用触发全部插件回调 (`PersonAPI.getMemory`)，高频读取时回调风暴，其他插件可能改写内存值 |
| 人物要成为市场可见联系人，`MarketAPI.addPerson` 之后必须另行 `getCommDirectory().addPerson(person)` 维护目录条目 | 必须 | 人物在市场人物列表与存档里，但通信目录/联系人 UI 不出现 |
| "设置档位" 用 `setRelationship(id, RepLevel)`(或 `ensureAtBest`/`ensureAtWorst`)；`adjustRelationship(delta, limit)` 的 limit 只是单次调整的方向边界 | 必须 | 数值落在限位内但档位与预期不符 (1.00 − 0.5 落 0.50 = `FRIENDLY` 而非 `HOSTILE`) |
| `adjustRelationship` 的 delta 为 0 时 (三参版) 不产生任何修改并返回 false | 必须 | 返回值 false、值不变，误以为调用成功 |
| 关系数值恒被钳制在 \[-1, 1\] (`RelationshipAPI.setRel` 等写入方法均钳制) | 必须 | 写入越界值后读回被截断 (如写 1.5 读回 1.0) |
| personality id 必须来自 `personalities.csv` 注册表 (原版 5 个：`timid`/`cautious`/`steady`/`aggressive`/`reckless`) | 必须 | 未知 id 使 `getPersonalityAPI()` 返回 null，首次解引用时抛 `NullPointerException` |
| `OfficerDataAPI.makeSkillPicks()` 每次调用都会重掷选项；已展示给玩家的可选项必须维持同一对象状态 | 必须 | 玩家看到的可选技能列表与展示不一致 |
| `ImportantPeopleAPI.checkOutPerson(person, reasonId)` 必须配对 `returnPerson`；未归还时同 reason 的按条件选人会跳过该人物 | 必须 | 任务/事件生成时该人物不再被选中 (`MarketPostPersonFilter`) |
| `PersonAPI.setMarket(market)` 只写人物字段；市场 roster 必须由 `MarketAPI.addPerson` 维护 (反向：`person.setMarket` 不会把人物放进市场列表) | 必须 | 人物字段显示在市场，但市场列表与通信目录查不到 |
| 跨存档保留人物必须挂入至少一个持久集合 (成员 captain / 市场 people+comm / important people / officer roster) | 必须 | 读档后人物不存在、无任何报错 |
| 玩家旗舰 `getCaptain()` 恒返回玩家人物 (特例)，不要把自定义舰长挂到旗舰再读回 | 必须 | 读回结果与写入不一致 (`FleetMemberAPI.getCaptain`) |
| 佣兵人物 (memory `$isMercenary`) 不获得军官 XP | 必须 | `addXP` 后 XP 不变 (`OfficerDataAPI.addXP`) |
| 演示/临时人物修改关系后应同帧回滚，且回滚用 "快照原值 + `setRelationship`" | 建议 | 中断残留的修改随存档保存 |
| 创建人物优先用 `Global.getFactory().createPerson()` / `FactionAPI.createRandomPerson()`，不依赖实现类构造器 | 建议 | 实现类不在公开 API 面，换版本可能失效 |

规则只是约束，示例才是落地。下面用 8 个类把上面的 API 与规则串成一条可运行、可同帧回滚的演示链。

## 实现人物关系配置

下面让同一个人物依次获得身份、关系、持久归属和舰长岗位，再用另一人物走军官链，并在末尾回滚势力关系。

### 创建并填写人物身份

这段身份写入位于 `GearInPersonIdentity.run`。

```java
PersonAPI person = Global.getFactory().createPerson();
person.setFaction("hegemony");
person.setName(new FullName("Aster", "Vance", Gender.FEMALE));
person.setRankId(Ranks.SPACE_COMMANDER);
person.setPostId(Ranks.POST_FLEET_COMMANDER);
person.setPersonality("reckless");
person.addTag("GearIn_personLesson");
person.getMemoryWithoutUpdate().set("$GearIn_personLesson", 42);
```

执行后，姓名、势力、rank、post、personality、标签与 memory 均能从同一对象读回。

### 调整人物对玩家关系

这段关系调整位于 `GearInPersonRelationship.run`。

```java
RelationshipAPI relationship = person.getRelToPlayer();
relationship.adjustRelationship(0.3f, null);
relationship.setLevel(RepLevel.COOPERATIVE);
relationship.setLevel(RepLevel.HOSTILE);
relationship.setRel(0f);
```

关系依次经过 0.30、0.75、-0.50，最后精确回到 0。

### 挂入市场与重要人物集合

需要跨存档保留并显示联系人时，把这三行写进注册逻辑。

```java
market.addPerson(person);
market.getCommDirectory().addPerson(person);
sector.getImportantPeople().addPerson(person);
```

市场 roster、通信目录与 important people 都能找到同一人物。

### 把人物设为非旗舰舰长

这段舰长写入位于 `GearInCaptain.run`。

```java
wolf.setCaptain(person);
boolean sameCaptain = wolf.getCaptain() == person;
boolean found = fleetData.getMemberWithCaptain(person) == wolf;
```

两个判据都为 true；该成员不是玩家旗舰，因此不会触发旗舰舰长特例。

### 包装军官并分配舰船

这条军官链位于 `GearInOfficer.run`。

```java
PersonAPI officerPerson = sector.getFaction("hegemony").createRandomPerson();
OfficerDataAPI officer = Global.getFactory().createOfficerData(officerPerson);
fleetData.addOfficer(officer);
officer.addXP(100000);
officer.makeSkillPicks();
if (!officer.getSkillPicks().isEmpty()) {
	officer.levelUp(officer.getSkillPicks().get(0));
}
hammerhead.setCaptain(officerPerson);
```

人物进入军官 roster、舰队引用成立、等级与技能变化，并能从 Hammerhead 反查该舰长。

### 修改势力关系后恢复快照

这段势力关系序列位于 `GearInFactionRelation.run`。

```java
float original = hegemony.getRelationship("tritachyon");
hegemony.setRelationship("tritachyon", 0f);
hegemony.adjustRelationship("tritachyon", 0.15f);
hegemony.setRelationship("tritachyon", RepLevel.HOSTILE);
hegemony.ensureAtWorst("tritachyon", RepLevel.WELCOMING);
hegemony.setRelationship("tritachyon", original);
```

日志展示数值与档位变化，末行与演示前关系值完全一致。

## 组装人物关系实现

在既有 MOD 布局与插件生命周期类之上新增 8 个类 (不新增资源文件)。原单一演示类按角色拆成 8 个小类：协调器 + 7 个角色小类，各自承担一个职责：

```text
mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data/
├── src/
│   └── data/scripts/gearin/campaign/
│       ├── GearInPersonScript         # 本节新增 (归本节所有)：协调器
│       ├── GearInBaseline             # 基线快照
│       ├── GearInPersonIdentity       # 人物创建与身份
│       ├── GearInPersonRelationship   # 人物对玩家关系
│       ├── GearInPersonRegistration   # 人物注册 (归属入口)
│       ├── GearInCaptain              # 舰长
│       ├── GearInOfficer              # 军官
│       └── GearInFactionRelation      # 势力间关系
└── jars/
```

演示脚本在挂载后的第一帧执行**同帧回滚**的序列：基线 → 演示成员 → 人物 A 默认值与身份 → 标签/记忆 → 人物对玩家关系 → 市场与重要人物 → 默认舰长与自定义舰长 → 势力随机人物 → officer 全链 (包装/XP/技能/升级/上舰)→ 势力关系确定性序列 → 清理。

全部修改在当帧回滚，不遗留任何持久状态。

**演示纪律**：演示/临时人物修改后必须同帧回滚，回滚用 "快照原值 + `setRelationship`"；先 `add` 后立刻 `remove` 的配对顺序不要调换 —— 演示帧中途抛异常会留下随存档保存的残留，且 `REGISTER_MARK` 已置位后读档不重放，残留无法自动清理。

### GearInPersonScript(协调器)

一次性演示脚本 (`EveryFrameScript`)：`executed` 置位后 `isDone()` 返回 true，引擎下一帧自动移除。

职责：记录基线 (交由 GearInBaseline)、创建并挂载演示成员 wolf / hammerhead 供本页各角色小类使用、末尾清理恢复初始舰队。

`REGISTER_MARK`(`"GearIn_PersonLesson"`) 是存档持久化的注册标记键，**必须**与调用点片段里 `containsKey` / `put` 用的键逐字一致。

### GearInBaseline

基线快照 (演示前置观察)：记录玩家舰队成员数、玩家势力 id、玩家人物名，并验证玩家旗舰 `getCaptain()` 特例 (旗舰舰长读回玩家人物，`captainIsPlayerPerson=true`)；返回初始成员数，供协调器在清理后对比。

### GearInPersonIdentity

人物创建与身份 (`PersonAPI`)：`Global.getFactory().createPerson()` 创建人物 A 并逐字段读回默认值 (id、`steady`、空名、`spaceCommander`/ `fleetCommander`、`neutral`、默认头像、`MEDIUM`，对应 "人物默认值速查表")；

随后写入姓名/性别/势力/rank/post/personality/importance+voice 并读回 (`rank=Commander`、`post=Fleet Commander` 经势力解析)；

标签与内存写入读取：纯读只用 `getMemoryWithoutUpdate()`(对应规则强度速查表的 "纯读取人物记忆" 条目)。

### GearInPersonRelationship

人物对玩家关系 (`RelationshipAPI` / `RepLevel`)：

- `getRelToPlayer()` 延迟创建 (默认 `rel=0.00 level=NEUTRAL`)；
- `adjustRelationship(0.3f, null)`(无 limit，正向默认封顶 `COOPERATIVE.getMax()` 即 1.0)；
- `setLevel(COOPERATIVE)` 落 `0.75`；
- `setLevel(HOSTILE)` 落 `-0.50`(`min+0.01` / `-min-0.01` 语义)；
- `setRel(0)` 复位。

五步日志覆盖 "数值连续、档位分段"。

### GearInPersonRegistration

人物注册 (归属入口)：市场侧 `addPerson` / `removePerson` 同帧配对 (`person.setMarket` 联动、`getPeopleCopy` 前后核对)；重要人物 `addPerson` / `containsPerson` / `getData` / `removePerson` 同帧配对。

演示归属四入口中的两个 (市场 people + important people)，且不遗留存档。

### GearInCaptain

舰长 (`FleetMemberAPI`)：读取 wolf 的默认舰长 (steady、空名、level 1，正是保存时会被压缩掉的形态)；`setCaptain(personA)` 后验证 `getCaptain() == personA` 同一性 + `getMemberWithCaptain(personA)` 反查。

### GearInOfficer

军官 (`OfficerDataAPI`)：

- `hegemony.createRandomPerson()` 势力随机人物 (随机姓名/肖像/voice，personality 恒 `steady`)→ `createOfficerData` → `addOfficer`(`personB.getFleet() == playerFleet` 联动)；
- `addXP(100000)`(`officerMaxLevel=5` 内不封顶)→ `canLevelUp()`(≥ 2 级阈值 48000)；
- `makeSkillPicks()`(4 个候选)→ `levelUp(pick)`(level 2、技能 1 级)；
- `hammerhead.setCaptain(personB)` 军官上舰 → `removeOfficer`(`getFleet()` 回 null)。

### GearInFactionRelation

势力间关系 (`FactionAPI` 关系族，hegemony vs tritachyon)：确定性序列 + 同帧回滚：

- `setRelationship(0)` 归一；
- `adjustRelationship(+0.15)` 落 `0.15 FAVORABLE`；
- `adjustRelationship(+0.9, COOPERATIVE)` 封顶 `1.00 changed=true`；
- `setRelationship(HOSTILE)` 落 `-0.50`、`isHostileTo=true`；
- `adjustRelationship(-0.2, VENGEFUL)` 落 `-0.70` **但档位仍 HOSTILE**(数值连续、档位分段)；
- `ensureAtWorst(WELCOMING)` 落 `0.25 ensured=true`(抬高关系用 `ensureAtWorst`；`ensureAtBest` 只拉低不抬高，此刻调用它返回 false、值不变)；
- `setRelationship(原值)` 精确恢复。

**协作关系**：协调器持有共享对象 (人物 A、wolf、hammerhead)，角色小类以 `run(...)` 参数接收 (包内协作，不跨单元)，保持 "同一人物对象贯穿身份 → 关系 → 注册 → 舰长" 的演示语义；

日志前缀统一为 `GearIn person lesson`；人物 A 的标签与 memory key 为 `GearIn_personLesson` / `$GearIn_personLesson`。

调用点 (工程片段，方法所在类归插件生命周期页所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(插件生命周期页所有)onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInPersonScript.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInPersonScript.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInPersonScript());
    }
```

构建命令 (在 `mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类 (可用 `jar tf` 核对)。

演示链已经落地，下面按步骤编译、进档并核对日志与界面。

## 关键故障实验：人物创建成功但任务和通信找不到他

人物只保存在局部变量，没有加入重要人物或市场目录。

**局部故障片段：**

```java
// 故障片段
PersonAPI person = Global.getFactory().createPerson();
```

**局部修正片段：**

```java
// 修正片段
market.addPerson(person);
market.getCommDirectory().addPerson(person);
sector.getImportantPeople().addPerson(person);
```

实际症状：同一会话内局部变量可用，读档后人物消失；只加入市场 roster 时，通信目录仍看不到联系人。

原因：人物只随持久集合保存，通信目录又是独立于市场 roster 的可见联系人集合。

复验：保存并读档后，市场 `getPeopleCopy()`、通信目录和 important people 均应命中同一人物 ID；清理时按相反顺序移除三个归属。

## 验证方案

前置数据：`gear_in` MOD 布局与插件生命周期类 ([C6-S1] / [C7-S1]，含本节调用点片段)、本节 8 个类、生涯时钟脚本 (非必需)。

1. 游戏构建：在 `mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类。
2. 新建生涯：确认 `starsector.log` 按顺序出现全部 `GearIn person lesson` 日志行：
    - 含 `start` / `flagship` / `members` / `personA defaults` / `personA identity` / `tagsMemory` / 五条 `relToPlayer` / `market` / `importantPeople`。
    - 含 `captain default` / `captain set` / `personB random` / `officer add` / `officer xp` / `officer levelUp` / `officer captain` / `officer remove` / 七条 `factionRel` / `done`。
    - 关键核对：`captainIsPlayerPerson=true`、`personA defaults` 六项默认值、`rank=Commander`、`post=Fleet Commander`、`relToPlayer` 五步 0.00→0.30→0.75→-0.50→0.00。
    - `factionRel` 序列 0.00→0.15→1.00→-0.50→-0.70→0.25→原值、`done` 行成员数与 `start` 一致。
    - 观察完整日志序列与数值变化。
3. 界面核对：演示日志出现时打开舰队界面 (F 键)，确认多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。
4. 只执行一次：等待若干游戏日后日志无新增 `GearIn person lesson` 行；保存并读档后仍无新增 (`REGISTER_MARK` 门已关)；读档后势力关系面板中 hegemony 与 tritachyon 的数值与演示前一致 (同帧回滚)。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInPersonScript.class` 等 8 个类。
- **新建生涯 (首次进入存档后第一帧)**：`starsector.log` 依次出现。

    ```text
    GearIn person lesson start: members=<N> playerFaction=player playerPerson=<玩家名> GearIn person lesson flagship: name=<旗舰船名> captainIsPlayerPerson=true GearIn person lesson members: added wolf=<船名> hammerhead=<船名> count=<N+2> GearIn person lesson personA defaults: id=p_<uid> personality=steady name='' rankId=spaceCommander postId=fleetCommander faction=neutral portraitIsDefault=true importance=MEDIUM GearIn person lesson personA identity: name=Aster Vance female=true rank=Commander post=Fleet Commander personality=reckless faction=hegemony voice=<soldier|official|aristo 之一> GearIn person lesson tagsMemory: hasTag=true tags=1 memory=42 GearIn person lesson relToPlayer default: rel=0.00 level=NEUTRAL hostile=false GearIn person lesson relToPlayer adjust: rel=0.30 level=WELCOMING GearIn person lesson relToPlayer cooperative: rel=0.75 level=COOPERATIVE GearIn person lesson relToPlayer hostile: rel=-0.50 level=HOSTILE hostile=true GearIn person lesson relToPlayer reset: rel=0.00 level=NEUTRAL GearIn person lesson market: id=jangala inAfterAdd=true personMarketLinked=true inAfterRemove=false GearIn person lesson importantPeople: containsAfterAdd=true dataLinked=true containsAfterRemove=false GearIn person lesson captain default: personality=steady name='' level=1 GearIn person lesson captain set: sameCaptain=true memberWithCaptainFound=true GearIn person lesson personB random: name=<随机名> female=<true|false> faction=hegemony personality=steady portraitIsDefault=false GearIn person lesson officer add: count=<M+1> fleetLinked=true GearIn person lesson officer xp: xp=<X> -> <X+100000> canLevelUp=true picks=4 GearIn person lesson officer levelUp: level=2 skill=<技能id> skillLevel=1.0 GearIn person lesson officer captain: sameCaptain=true memberWithCaptainFound=true officerInRoster=true GearIn person lesson officer remove: count=<M> fleetLinked=true GearIn person lesson factionRel start: original=<O> normalized=0.00 level=NEUTRAL GearIn person lesson factionRel adjust: value=0.15 level=FAVORABLE GearIn person lesson factionRel cap: value=1.00 level=COOPERATIVE changed=true GearIn person lesson factionRel hostile: value=-0.50 level=HOSTILE hostileTo=true GearIn person lesson factionRel vengefulCap: value=-0.70 level=HOSTILE GearIn person lesson factionRel ensure: value=0.25 level=WELCOMING ensured=true GearIn person lesson factionRel restore: value=<O> level=<原档位> GearIn person lesson done: members=<N> (initial <N>)
    ```

    其中 `<N>` 是初始成员数、`<M>` 是初始军官数、`<O>` 是存档里 hegemony 对 tritachyon 的关系原值、`<X>` 是人物 B 初始 XP(新档为 0)，均以实际日志为准；`voice` 是 hegemony `HIGH` 声音池三选一；`picks=4` 是新档典型值 (以日志为准，≥1)；`skill` 是重掷后的第一个军官技能候选。关系序列各行的数值/档位是**确定性**的：`0.15→FAVORABLE`、`1.00→COOPERATIVE changed=true`、`-0.50→HOSTILE hostileTo=true`、`-0.70 档位仍 HOSTILE`、`0.25→WELCOMING ensured=true`、`restore` 与原值一致。演示只执行一次，之后无任何 `GearIn person lesson` 日志。
- **游戏内舰队界面 (F 键)**：演示期间 (日志可见时) 舰队界面多出 `wolf_Assault` 与 `hammerhead_Elite` 两艘船；演示结束后界面恢复初始构成。
- **读档**：不出现任何 `GearIn person lesson` 日志 (`REGISTER_MARK` 门已关、脚本不再注册)；玩家舰队与演示前一致；`hegemony` 与 `tritachyon` 的关系面板数值与演示前一致 (同帧回滚生效)。
- **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR` 日志。

## 故障分析

- **读档后人物消失**：人物只保存在脚本局部变量中，没有进入持久集合。

    - **解决方案**：将人物挂入成员 captain、市场 people 与 comm、important people 或 officer roster。

- **使用人格 API 时出现 `NullPointerException`**：`setPersonality` 使用了 `personalities.csv` 中不存在的 ID。

    - **解决方案**：改用已注册的 personality ID，确认 `getPersonalityAPI()` 返回非 null 后再调用其方法。

- **`adjustRelationship` 返回 `false`**：三参版的 delta 为 `0`，或调整方向被 limit 拦截，因此关系值没有变化。

    - **解决方案**：设置档位使用 `setRelationship`，限制上界或下界使用 `ensureAtBest`、`ensureAtWorst`。

- **关系档位没有随数值变化**：关系值尚未越过 `RepLevel` 的分段边界。

    - **解决方案**：需要确定档位时直接传入 `RepLevel` 调用 `setRelationship`。

- **市场人物没有出现在通信目录**：`market.addPerson` 只加入市场 roster。

    - **解决方案**：再调用 `market.getCommDirectory().addPerson`。

- **军官获得的 XP 始终为零**：人物 memory 中的 `$isMercenary` 为 true，`OfficerDataAPI.addXP` 会直接返回。

    - **解决方案**：解除佣兵标记或改用普通军官。

- **军官技能选项发生变化**：展示 `getSkillPicks()` 后又调用了 `makeSkillPicks()`，旧选项被清空并重掷。

    - **解决方案**：只在生成阶段调用一次。

- **任务始终选不到指定人物**：人物以相同 reason 被 checkout 后没有归还，筛选器会拒绝再次选取。

    - **解决方案**：调用 `returnPerson(person, reasonId)`，再运行一次选人流程。

- **演示人物在读档后仍然存在**：异常发生在 `addPerson` 与 `removePerson` 之间，而 `REGISTER_MARK` 已阻止演示重放。

    - **解决方案**：补齐同帧清理并清除该标记。

- **人物好感与势力声望不一致**：代码混用了人物的 `RelationshipAPI` 与势力的 `FactionAPI` 关系。

    - **解决方案**：按界面对象选择对应接口。

- **旗舰舰长始终显示为玩家**：`FleetMemberAPI.getCaptain()` 对玩家旗舰固定返回玩家人物。

    - **解决方案**：将自定义舰长放到非旗舰成员。

- **读取 memory 时回调数量异常**：每帧调用 `person.getMemory()` 会触发人物事实更新插件。

    - **解决方案**：纯读取改用 `getMemoryWithoutUpdate()`。

- **读档后默认舰长对象发生变化**：未显式设置的低等级默认舰长不会作为稳定人物保存。

    - **解决方案**：需要稳定身份时调用 `setCaptain` 写入自定义人物。

## 总结

本节把人物走通为四条链。创建链 (空白或势力随机 → 默认值 → 身份写入)、关系链 (数值连续、档位分段、limit 只是单次调整边界、数值恒钳制在 \[-1, 1\])、captain 链 (生涯侧与战斗侧，玩家旗舰恒返回玩家人物)、officer 链 (包装 → roster → XP/升级 → 上舰)。人物默认值表是调试基准，人物持久化依赖四个归属入口。

排错时先问 "人物是否挂在持久集合"：局部变量人物不保存也不报错，这是多数 "消失" 类问题的共同起点；关系类问题先分清人物侧与势力侧，再对照关系四件事判断。`getMemory()` 触发全插件回调、`makeSkillPicks()` 重掷选项、默认舰长保存时压缩，这三个 "每次都会发生" 的陷阱值得记牢。

人物对象链到这里已经走通完整流程。人物是任务、贸易与通信系统的共同实体 —— 任务与事件的选人规则建立在 important people 的 checkout 机制上，市场侧的联系人体系建立在人物归属之上。遇到 "造一个人、调整它与玩家或势力的关系、把它挂进某个持久集合" 的需求，都可以回到本节的四条链上定位。

## 建议参考项

- `com.fs.starfarer.api.characters.PersonAPI`：人物接口全貌 —— 身份、rank/post、头像、势力、记忆、标签、personality、importance/voice、AI core、归属、角色数值与联系。
- `com.fs.starfarer.api.FactoryAPI`：人物创建入口 (`createPerson` / `createOfficerData`)。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力关系族 (`adjustRelationship` 两版 / `setRelationship` 两版 / `ensureAtBest` / `ensureAtWorst` / `getRelationshipLevel` / `isHostileTo`) 与 `createRandomPerson`。
- `com.fs.starfarer.api.campaign.RepLevel`：九档声誉档位枚举与阈值判定 (`getLevelFor` / `getMin` / `getMax` / `isAtWorst` / `isPositive` / `isNegative`)。
- `com.fs.starfarer.api.characters.RelationshipAPI`：人物对玩家关系 (`getRel` / `setRel` / `getLevel` / `setLevel` / `adjustRelationship` / `ensureAtBest` / `ensureAtWorst` / `isHostile`)。
- `com.fs.starfarer.api.characters.OfficerDataAPI`：军官包装 (`getPerson` / `setPerson` / `addXP` / `canLevelUp` / `makeSkillPicks` / `getSkillPicks` / `levelUp`)。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：角色数值 (`getLevel` / `getXP` / `getSkillLevel` / `increaseSkill` / `refreshCharacterStatsEffects`)。
- `com.fs.starfarer.api.characters.ImportantPeopleAPI`：重要人物集合 (`addPerson` / `containsPerson` / `removePerson` / `getData` / `checkOutPerson` / `returnPerson`)。
- `com.fs.starfarer.api.characters.MarketPostPersonFilter`：按条件选人的 checkout 过滤。
- `com.fs.starfarer.api.characters.FullName`：姓名对象与 `Gender` 枚举。
- `com.fs.starfarer.api.campaign.PersonImportance`：重要性五档与 `getValue()`。
- `com.fs.starfarer.api.campaign.SectorAPI`：`getEconomy` / `getPlayerPerson` / `getImportantPeople` / `getPlayerFaction` / `getFaction`。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：舰队指挥官 (`getCommander` / `setCommander` / `getCommanderStats`) 与 `getFlagship`。
- `com.fs.starfarer.api.campaign.FleetDataAPI`：军官集合 (`getOfficersCopy` / `addOfficer` / `removeOfficer`) 与 `getMemberWithCaptain`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：成员舰长 (`getCaptain` / `setCaptain` / `getShipName`)。
- `com.fs.starfarer.api.combat.ShipAPI`：战斗侧舰长 (`getCaptain` / `getOriginalCaptain` / `setCaptain` / `getFleetCommander`)。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场人物 (`getCommDirectory` / `addPerson` / `removePerson` / `getPeopleCopy`)。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：rank/post 常量 (`SPACE_COMMANDER` / `POST_FLEET_COMMANDER` 等)。
- `com.fs.starfarer.api.impl.campaign.OfficerLevelupPluginImpl`：`officerLevelUp` 插件实现 (XP 阈值、每级候选)。
- `com.fs.starfarer.api.plugins.OfficerLevelupPlugin`：军官升级插件接口。
- `com.fs.starfarer.api.util.Misc`：`isMercenary`(读 memory `$isMercenary`)。
- `data/characters/personalities.csv`：personality 注册表 (原版 id 与任务权重列)。
- `data/characters/person_names.csv`：姓名池 (usage `f`/`l`、category)。
- `data/world/factions/default_ranks.json`：rank/post 显示名的默认来源 (`spaceCommander` / `fleetCommander`)。
- `data/world/factions/hegemony.faction`：`ranks` 段覆写与 `voices` 声音池 (`LOW` / `MEDIUM` / `HIGH`)。
- `data/config/settings.json`：`default_portrait`、`officerLevelUp` 插件、`officerXPRequiredMult`、`officerMaxLevel`。

## 完整最小例

::: details 完整成品：GearInPersonScript

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
 * 人物专题一次性演示脚本 (协调器)：编排各角色小类并负责演示成员与清理，自停脚本。
 * 注册入口：生涯回调 onGameLoad，persistent data 键 REGISTER_MARK 一次性守卫；全程同帧回滚。
 */
public class GearInPersonScript implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_PersonLesson";

    private static final Logger LOG = Global.getLogger(GearInPersonScript.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInPersonScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内)
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
        // 演示成员 wolf / hammerhead(供 captain / officer 小节使用)
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

        // 舰长；人物 B 与军官；势力关系 (同帧回滚)
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

:::

::: details 完整成品：GearInBaseline

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 基线快照 (演示前置观察)：玩家舰队成员数/玩家势力/玩家人物，及玩家旗舰 getCaptain() 特例。
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

:::

::: details 完整成品：GearInPersonIdentity

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
 * 人物创建与身份 (PersonAPI)：createPerson 默认值、姓名/性别/势力/rank/post/personality/importance+voice、标签与内存。
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

:::

::: details 完整成品：GearInPersonRelationship

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

/**
 * 人物对玩家关系 (RelationshipAPI / RepLevel)：延迟默认 -> adjustRelationship -> setLevel -> setRel 复位。
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

:::

::: details 完整成品：GearInPersonRegistration

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
 * 人物注册 (归属入口)：市场通讯录 addPerson/removePerson 双向交接，重要人物 addPerson/contains/getData/removePerson。
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

:::

::: details 完整成品：GearInCaptain

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 舰长 (captain)：默认舰长读取 (steady/空名/level 1)、setCaptain 交接与 getMemberWithCaptain 反查。
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

:::

::: details 完整成品：GearInOfficer

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
 * 军官 (OfficerDataAPI)：势力随机人物 -> 包装入 roster -> XP/技能候选/升级 -> 上舰 -> 移除。
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

:::

::: details 完整成品：GearInFactionRelation

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 势力间关系 (FactionAPI 关系族)：setRelationship / adjustRelationship / ensureAtWorst 确定性序列 + 同帧回滚。
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
        boolean ensured = hegemony.ensureAtWorst("tritachyon", RepLevel.WELCOMING);
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

:::
