# 配置势力舰队与关系

本节在 [C3-S1] 注册的势力身份上加入舰船池、装配角色、舰队学说与初始关系。完成后，齿轮工业能够用自己的舰船组成生涯舰队，并以明确的关系立场进入新星系。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 用 `factionDoctrine` 配置势力的舰队组成、质量与性格倾向，并说出玩家势力编辑器对同一组字段的取值约束；
- 用 `knownShips` / `knownWeapons` / `knownFighters` / `knownHullMods`、`hullFrequency`、`priority*` 配置势力舰船池，并说出 tags 展开与 `pickShip` 过滤的完整流程；
- 用 `default_ship_roles.json`(或势力文件内的 `shipRoles`) 把装配引入生涯舰队；
- 说清势力间关系不是文件数据而是运行时状态：`FactionAPI.setRelationship` / `adjustRelationship`、`RepLevel` 阈值，以及默认 0.0(中立) 的含义。

## 前置知识

- [C3-S1] 的势力注册、身份字段和 `gear_industry` 势力 ID；
- [C1-S2] 的船体登记与 ID 规则；
- [C1-S3] 的战机、联队与航母登记规则；
- [C1-S5] 的武器登记与 tags 规则；
- [C2-S2] 的船插登记规则；
- [C4-S1] 的战役与舰船装配组织方式。

## 术语

- **舰队学说 (doctrine)**：决定势力舰队组成倾向的一组数值。API 名保留：`FactionDoctrineAPI`。
- **舰船池 (ship pool)**：势力 "知道" 的舰船、武器、战机、船插集合 (`knownShips` 等)，是舰队生成与蓝图分配的依据。
- **声望等级 (RepLevel)**：把关系数值 (float，-1 到 1) 映射成的离散等级，从 `VENGEFUL`(复仇) 到 `COOPERATIVE`(合作)。API 名保留源码大小写。

## 文件与 API 关系

### 配置舰船池

1. 加载期 `loadFactionList` 处理每个 `known*` / `priority*` 块：`tags` 数组里的每个 tag，遍历对应注册表 (`knownShips` 遍历全部船体、`knownWeapons` 遍历全部武器、`knownFighters` 遍历全部战机联队、`knownHullMods` 遍历全部船插)，凡对象带任一 tag 即加入集合；`hulls` / `weapons` / `fighters` / `hullMods` 数组则把 ID 直接加入集合。**拼错的 tag 或 ID 不会报错** —— 只是匹配不到任何对象，静默得到一个更小的舰船池。
2. 装配角色的来源有两层。第一层是**全 MOD 共享**的 `data/world/factions/default_ship_roles.json`：这是合并读取的 JSON(`readJSONMerged`)，角色对象按键逐层合并、装配条目按 variant ID 覆盖，因此 **MOD 只提交自己的角色条目，不要整份复制原版文件** (整份复制会用同键条目的副本值覆盖原版值，版本不同步时会留下陈旧配置，但不会删除原版角色)。每个角色是一个对象：`variantId → 权重`，另有三个特殊键：`includeDefault: true`(把原版同名角色的条目复制进来)、`fallback` / `fallback2`(备用角色)。角色条目 (`RoleEntryAPI`) 创建时**立即按 ID 查装配** (`_wing` 结尾的 ID 走联队查找的历史兼容路径，原版数据不使用它)；装配查不到抛 `Ship hull variant [X] not found!`，加载中断。
3. 第二层是势力文件自己的 `shipRoles`(对象结构与 default_ship_roles.json 相同；内部 `doctrine` 键是旧版遗留，本节不展开)。`FactionAPI.pickShip(role, params)` 在选择时优先用势力自己的角色；角色缺失或为空时改用共享的 `default_ship_roles.json`(内部 `com.fs.starfarer.loading.FactionSpec.getDefaultShipRoles()` 静态方法，公开接口不提供该方法)。
4. **舰船池完整流程**：`pickShip` 内部对每个角色条目做过滤 —— 条目装配的船体必须在该势力的 `knownShips` 里 (`knowsShip`)，再乘上 `hullFrequency` 里该船体的倍率；`variantOverrides` 命中的装配按覆盖权重参与。这就是 "势力只出自己舰船池里的船" 的机制。过滤结果按权重随机抽取，`ShipPickMode` 控制范围 (`ALL` / `PRIORITY_ONLY` / `PRIORITY_THEN_ALL` / `IMPORTED`)，`priorityShips` / `priorityWeapons` / `priorityFighters` 在优先级模式下优先被抽取。权重结果带缓存，运行时改动舰船池后要调 `clearShipRoleCache()` 才会反映 (常见陷阱：缓存不会自动失效，改动池后容易漏调)。
5. 其它只读入口：`getNumAvailableForRole(role, mode)`、`getApproximateMaxFPPerFleet(mode)`、`knowsShip / knowsWeapon / knowsFighter / knowsHullMod`。

`knownHullMods` 启用 tag 过滤时，always-unlocked 船插也会加入集合，不要求命中这些 tag。

::: warning 拼错的 tag 或 ID 只会静默缩小舰船池

`known*` / `priority*` 里的 tag 或 ID 拼错时，加载期不会有任何报错：匹配不到任何对象，舰船池 (或优先级) 只是变小。排查 "势力缺船" 问题时，先对照各注册表的 tags 列逐字核对。

:::

### 配置舰队学说

1. 加载期 `factionDoctrine` 对象 (存在时) 解析为 `FactionDoctrineAPI`：`warships`、`carriers`、`phaseShips`、`officerQuality`、`shipQuality`、`numShips` 六个键用 `getInt` **必读** —— 写了 `factionDoctrine` 块却漏掉其中任何一个，加载中断 (`JSONObject["<键>"] not found`)；`shipSize` / `aggression` / `fleets` 是 `optInt`，默认 3；`autofitRandomizeProbability` 默认 0.25；`strictComposition` 默认 false；`commanderSkills` / `officerSkills` 数组与各自的 `*ShuffleProbability` 可选。
2. 加载后成为 `FactionDoctrineAPI`，运行时可经 `FactionAPI.getDoctrine()` 读写。
3. **玩家势力编辑器与文件是同一组字段**：游戏内玩家自定义势力时用的**势力编辑器界面**把前两组各三条的总和固定为 7、每一条取值 1–5(每行是五档按钮)，`shipSize` / `aggression` 各 1–5 且不受总和约束。势力文件本身不校验这些范围，但建议按编辑器的值域填写，使势力观感与用户习惯一致。
4. 行为使用方 (舰队生成、军官生成) 读取这些值决定编队组成、船型倾向、军官质量与性格分布；`getShipQualityContribution()` 按 `(shipQuality - 1) * doctrineFleetQualityPerPoint` 计算，`settings.json` 里该系数为 0.125。

### 设置势力关系

1. **faction 文件不定义任何势力间关系**。所有关系是运行时状态：关系数值默认 0.0，也就是所有势力两两之间默认中立。
2. 原版的关系表是代码播种的：`SectorGen`(原版生涯生成脚本) 在生成生涯时对每个势力调用 `setRelationship(其它势力ID, 数值或 RepLevel)`，例如 `hegemony.setRelationship(pirates.getId(), RepLevel.HOSTILE)`。
3. MOD 势力的关系同样只能由代码设置：`FactionAPI.setRelationship(id, float)` 或 `setRelationship(id, RepLevel)` 直接写值；`adjustRelationship(id, delta[, RepLevel limit])` 增减并可选钳制到等级边界；`ensureAtBest` / `ensureAtWorst` 把数值推进/钳制到某个等级；`getRelationship(id)` 读 float，`getRelationshipLevel(id)` 读等级。
4. `RepLevel` 的阈值：`T1=0.09`、`T2=0.24`、`T3=0.49`、`T4=0.74`(`getLevelFor(float)` 按正负方向映射为 9 个等级；`getRepInt` 把 float 乘 100 四舍五入为整数显示)。`isHostileTo(id)` 的判据是关系等级为 `HOSTILE` 或更差 (数值 ≤ -0.49)。
5. 把关系代码挂进 MOD 的哪个回调 (`ModPlugin` 的生涯生成钩子)，属于 ModPlugin 主题；本节给出原版写法作参照，并说明可观察结果。

## 完整最小例：配置齿轮工业舰队

### 配置势力文件

从 [C3-S1] 已经能够加载的 `gear_industry.faction` 开始，保留身份字段，再按下面的完整最小例补入舰船池、舰队学说和其它配置。

势力 ID 是 `gear_industry`，以 `齿轮工业` 为显示名，舰船池只使用前面各页已注册的 `GearIn_` 内容，另加两艘原版辅助船。

```json
{
	id:"gear_industry",   # 势力的唯一 ID：注册键是文件里的这个 id，与文件名、CSV 行无关，重复即加载中断
	"color":[180,120,60,255],   # 势力主色 RGBA，用于星图与情报显示
	"displayName":"齿轮工业",
	"displayNameWithArticle":"齿轮工业",   # 带冠词的名称；中文环境下与 displayName 一致
	"logo":"graphics/factions/gear_industry.png",      # 大旗，贴图建议 410×256，入资源队列
	"crest":"graphics/factions/crest_gear_industry.png", # 小旗，贴图建议 256×256，入资源队列
	"shipNamePrefix":"GIS",   # 势力舰船名前缀
	"shipNameSources":{       # 舰船名词典：data/strings/ship_names 的分类 -> 权重
		"GENERAL":2,
		"SPACE":1,
	},
	"names":{                 # 人名词典：data/characters/person_names.csv 的分类 -> 权重
		"modern":2,
		"world":1,
	},
	# variantOverrides 限制个别装配的可用性并改写其权重；本节不展开，留空即可
	"variantOverrides":{
	},
	# 舰船出现倍率：tags 当场展开为所有带该 tag 的已注册船体，hulls 按 ID 指定；0 表示绝不出现
	"hullFrequency":{
		"tags":{
			"GearIn_bp":1,
		},
		"hulls":{
		},
	},
	# 引入其他势力蓝图时的可用舰船；留空则回退为 knownShips
	"shipsWhenImporting":{
		"tags":[],
		"hulls":[],
	},
		"knownShips":{            # 势力舰船池：tags 展开 + hulls 直列
			"tags":["GearIn_bp", "gear_industry"],   # gear_industry 是本节定义的势力专属 tag，可给船体打上
			"hulls":[
				"GearIn_artizan",
				"GearIn_furnaceman",
				"dram",
				"valkyrie",
			],
		},
	# priorityShips 会显著覆盖本势力其它舰船的出现率；无特殊要求留空
	"priorityShips":{
		"tags":[],
		"hulls":[],
	},
	"knownFighters":{
		"tags":["GearIn_bp"],
		"fighters":[
			"GearIn_slag_wing",
		],
	},
	"priorityFighters":{
		"tags":[],
		"fighters":[],
	},
	"knownWeapons":{
		"tags":["GearIn_bp"],
		"weapons":[
			"GearIn_durin",
			"GearIn_dwalin",
		],
	},
	"priorityWeapons":{
		"tags":[],
		"weapons":[],
	},
	"knownHullMods":{
		"tags":["standard", "GearIn_bp"],   # standard 是原版通用船插 tag(见 hull_mods.csv 的 tags 列)
		"hullMods":[
			"GearIn_stable_injector",
		],
	},
	"factionDoctrine":{
		# 组一：warships+carriers+phaseShips 总和建议 7，每一行 1–5(玩家势力编辑器即此约束)
		"warships":4,
		"carriers":2,
		"phaseShips":1,

		# 组二：officerQuality+shipQuality+numShips 总和建议 7，每一行 1–5
		"officerQuality":5,
		"shipQuality":1,
		"numShips":1,

		"shipSize":4,     # 越大越偏向大船，建议 1–5
		"aggression":2,   # 越大越偏向鲁莽军官，建议 1–5

		"combatFreighterProbability":0.1,                  # 用武装货舰替换部分货舰的概率
		"combatFreighterCombatUseFraction":0,              # 舰队损失到低于此比率时才把武装货舰当战舰用
		"combatFreighterCombatUseFractionWhenPriority":0,  # 标记为优先时武装货舰当战舰用的概率
		"autofitRandomizeProbability":0.1,                 # 自动装配打乱程度

		"officerSkillsShuffleProbability":1,
		"officerSkills":[
			"ballistic_mastery",
		],

		"commanderSkillsShuffleProbability":1,
		"commanderSkills":[
			"crew_training",
			"officer_training",
		],
	},
	"illegalCommodities":[    # 违禁品：ID 必须存在于 data/campaign/commodities.csv，否则加载中断
		"drugs",
		"organs",
	],
	"music":{                 # 各场景音乐，值为 sounds.json 已注册的音乐 ID(注册链见 C5-S2)
		"theme":"music_hegemony_market_neutral",
		"market_neutral":"music_hegemony_market_neutral",
		"market_hostile":"music_hegemony_market_hostile",
		"market_friendly":"music_hegemony_market_friendly",
		"encounter_neutral":"music_hegemony_encounter_neutral",
		"encounter_hostile":"music_hegemony_encounter_hostile",
		"encounter_friendly":"music_hegemony_encounter_friendly",
	},
	"internalComms":"hegemony_internal",  # 内部频道 ID，须在 data/campaign/channels.json 中注册 (本节沿用原版频道)
	"portraits":{             # 人物头像池 (键必须存在；数组可省略，条目入资源队列)
		"standard_male":[
			"graphics/portraits/portrait_hegemony01.png",
			"graphics/portraits/portrait_mercenary03.png",
		],
		"standard_female":[
			"graphics/portraits/portrait_hegemony03.png",
			"graphics/portraits/portrait16.png",
		],
	},
	"ranks":{                 # 自定义头衔；不写则回退 data/world/factions/default_ranks.json
		"ranks":{
			"factionLeader":{"name":"总工程师"},
		},
		"posts":{
			"factionLeader":{"name":"总工程师"},
		},
	},
	"voices":{                # 联络人类型权重 (spacer/faithful/pather/business/official/soldier/scientist/villain/aristo)
		"LOW":{"soldier":5,"spacer":5},
		"MEDIUM":{"official":5,"soldier":5},
		"HIGH":{"official":5,"aristo":2},
	},
	"custom":{
		# 本地可核验的消费方只有 engageWhenEvenStrength(舰队 AI：势均力敌时也主动进攻)。
		# 其余键是原版文件的惯例内容 (offersCommissions、buysAICores、caresAboutAtrocities、
		# punitiveExpeditionData、postsNoBounties、allowsTransponderOffTrade、decentralized、
		# pirateBehavior、makesPirateBases、fightToTheLast、gaVIPMission 等)，它们的消费方大多在
		# 本教程可验证切片之外：照原样保留即可，但不要把势力行为的关键逻辑寄托在未核验的键上。
		"engageWhenEvenStrength":false,
		"offersCommissions":false,
		"engagesInHostilities":true,
		"buysAICores":false,
		"buysSurveyData":false,
		"caresAboutAtrocities":false,
		"postsNoBounties":false,
		"allowsTransponderOffTrade":false,
		"decentralized":false,
		"pirateBehavior":false,
		"makesPirateBases":false,
		"fightToTheLast":false,
		"gaVIPMission":false,
		"hostilityImpactOnGrowth":true,
		"punitiveExpeditionData":{
			"vsCompetitors":false,
			"vsFreePort":false,
			"canBombard":false,
			"territorial":false,
		},
	},
}
```

### 势力文件必守规则速查表

由于 faction 文件很长，无法逐字段列表，上文用注释注解了所有在本节有意义的字段。下面只总结必须遵守的规则 (均来自 spec 加载期解析 faction 文件的实现)：

| 规则 | 违反后的可观察结果 |
|---|---|
| `id` / `logo` / `displayName` / `displayNameWithArticle` / `names` / `portraits` 必须存在 | 加载中断：`JSONObject["<键>"] not found` 异常堆栈，进不了主菜单 |
| `factionDoctrine` 块若存在，`warships` / `carriers` / `phaseShips` / `officerQuality` / `shipQuality` / `numShips` 必须存在 | 加载中断：同样的 JSON 异常 |
| 势力 ID 全局唯一 (含原版与其它 MOD)| 加载中断：`Spec of class [...] with id [X] already exists` |
| `illegalCommodities` 的 ID 必须是商品 ID | 加载中断：`Invalid illegal commodity id: [X]` |
| `logo` / `crest` / 头像贴图路径真实存在 | 资源加载阶段报错 (TEXTURE 无错误备用值)|
| `factions.csv` 表头为 `faction`、行未被 `#` 注释 | 表头拼错：行解析失败，势力不出现；被注释：势力不出现，无报错 |

### 接入舰队装配

faction 文件只决定 "势力知道哪些船"，舰队生成时按角色 (`combatSmall`、`combatMedium`、`combatLarge`、`combatCapital`、`carrierSmall` 等) 从角色表里抽装配。

角色表就是共享的 `default_ship_roles.json`：没有它 (或没有本 MOD 的条目)，装配就不会出现在生涯舰队里，势力只能改用原版装配。

在 MOD 中新建 `data/world/factions/default_ship_roles.json`，**只提交自己的角色条目** (这个文件跨来源按角色对象合并；整份复制会覆盖同键条目，版本不同步时留下陈旧配置)：

```json
{
	"combatSmall":{
		"GearIn_artizan_combat":10,
	},
}
```

::: caution 只提交自己的角色条目，禁止整份复制原版文件

`default_ship_roles.json` 跨来源按角色对象合并：整份复制原版文件会用同键条目的副本值覆盖原版值，版本不同步时会留下陈旧配置，但不会删除原版角色。MOD 只提交自己的角色条目。

:::

每个条目是 `装配 ID → 权重`。**查不到装配会当场抛 `Ship hull variant [X] not found!` 并中断加载**。`includeDefault: true` 可把原版同名角色的条目复制进来再叠加自己的条目 (参考原版文件里各角色的写法；本例不需要)。

注意角色表里只放**舰船装配**：航母角色 (`carrierSmall` 等) 的条目是航母船体的装配，战机联队不进角色表 —— 联队经由舰船装配的 `wings` 数组 [C4-S1] 与 `knownFighters` 池进入舰队。

角色条目 (`RoleEntryAPI`) 对 `_wing` 结尾的 ID 有按联队 ID 查找的历史兼容路径，原版数据不使用它，本教程也不建议使用。

想加更多装配 (例如按 [C4-S1] 的导出流程为其它船体做的装配)，按同一格式加行即可。

把装配文件放好之后，势力舰船池就形成了完整流程：`knownShips`(势力知道什么)∩ 角色表 (能抽什么)→ 权重由 `hullFrequency` 与 `priority*` 调节 → `pickShip` 抽取。

### 设置运行时关系

势力文件无法声明关系。参照原版 `SectorGen` 的写法 (原版数据脚本，关系播种的权威示例)：

```java
// 摘自原版 SectorGen 脚本的写法，用于本教程说明关系 API
FactionAPI gear = Global.getSector().getFaction("gear_industry");
FactionAPI pirates = Global.getSector().getFaction("pirates");
FactionAPI hegemony = Global.getSector().getFaction("hegemony");

gear.setRelationship(pirates.getId(), RepLevel.HOSTILE);        // 直接设到等级区间
gear.setRelationship(hegemony.getId(), 0.25f);                  // 或直接写数值
gear.adjustRelationship(pirates.getId(), -0.1f, RepLevel.HOSTILE); // 增减并钳制到等级
```

把这段代码挂进 MOD 的哪个回调 (`ModPlugin` 的生涯生成钩子)，属于 ModPlugin 主题，本节不展开。不设置任何关系的可观察结果：所有势力对这个新势力都是 0.0 中立 —— 包括那些看起来应该敌对的势力，且没有任何报错。

::: warning 关系是运行时状态，不是文件数据

faction 文件不定义任何势力间关系，所有关系都是运行时数值，只能由代码设置。不设置的可观察结果：所有势力对新势力默认 0.0 中立 (该敌对的也中立)，且没有任何报错。

:::

### 验证方案

前置数据：[C1-S1] 的 `mod_info.json`、[C1-S2] 的船体 (`GearIn_artizan`)、[C1-S3] 的联队与航母 (`GearIn_slag_wing`、`GearIn_furnaceman`)、[C1-S5] 的武器 (`GearIn_durin`、`GearIn_dwalin`)、[C2-S2] 的船插 (`GearIn_stable_injector`)、[C4-S1] 的装配 (`GearIn_artizan_combat` 等)、本节的 `factions.csv`、`gear_industry.faction`、`default_ship_roles.json`。

1. 启动游戏，加载到主菜单，检查 `Starsector\starsector-core\starsector.log`。
2. 进入生涯 (或读取已有生涯存档)，按 `F` 打开情报。
3. 在星区里遭遇或靠近齿轮工业的巡逻队/贸易队。
4. (可选) 与齿轮工业的市场互动 (若有其殖民地，本节未建殖民地时可跳过)。

预期结果：

- 启动游戏加载到势力阶段时，`starsector.log` 出现 `Loading factions`、`Loading default_ship_roles.json` 与 `Loading gear_industry faction`，之后没有任何 `ERROR`。
- 进入生涯 (或任意读取生涯的存档) 后，按 `F` 打开情报 (intel)，齿轮工业出现在势力列表里，显示名、颜色、大旗 (logo) 与小旗 (crest) 与 faction 文件一致。
- 齿轮工业生成的巡逻队、贸易队使用 `knownShips` 里的舰船，组成符合 `factionDoctrine`(战舰/航母比例、船型大小、军官质量)；`GearIn_artizan` / `GearIn_furnaceman` 及其在 `default_ship_roles.json` 里的装配出现在其舰队中；`hullFrequency` 里倍率为 0 的船体永不出现。
- 未设置关系时，齿轮工业与所有势力的关系都是 0.0 中立；按第四步设置后，情报里对应关系显示为设定值 (例如与海盗 `HOSTILE`)，且海盗对该势力同样敌视 (关系是对称存储、按对读取的)。
- 市场蓝图与打捞层面：带 `rare_bp` 的组件不出现在本势力默认池里，符合掉落池规则 (本节不展开市场)。

## 实用势力包 (bp) 与其它生成型 tags 速查表

下表 tag 存在性来自 `data/weapons/weapon_data.csv`、`data/hulls/ship_data.csv`、`data/hulls/wing_data.csv`、`data/hullmods/hull_mods.csv`；"引用它的势力文件" 来自 `data/world/factions/*.faction`。**不在本表上的数据均为与势力包 (bp) 和生成均无关的 tags。**

| tag | 原版数据中存在性 | 引用它的原版势力文件 | 实际意义 |
|---|---|---|---|
| `base_bp` | 是 (20 船体、16 武器、2 联队)| 海盗、卢德左径、derelict、余晖、欧米伽、玩家势力 | 通用默认池 tag，构成上述势力的舰船/武器池主体；玩家势力的 known* 也引用它 |
| `lowtech_bp` / `midline_bp` / `hightech_bp` / `missile_bp` | 是 (低科/中科/高科/导弹各若干)| 海盗、卢德左径、余晖 | 科技线 tag。注意：**不是所有对应科技的组件都在其中**；RC8 的多数正规势力 (霸主、自由联盟、英仙座联盟、辛达强权、卢德教会等) 不再依赖这些 tag，而是在 `hulls` 数组里显式列出舰船 |
| `pirate_bp` | 是 | 海盗 | 海盗改造型 (D 插变体) 蓝图 tag |
| `LP_bp` | 是 | 卢德左径 | 左径安超 (LP) 型改造包，只有卢德左径拥有它 |
| `XIV_bp` / `heg_aux_bp` | 否 (不在数据 CSV，只作势力内 tag)| 霸主 | 第十四军团 (XIV) / 霸主辅助军 (A) 型改造包，只有霸主拥有它 |
| `hegemony` | 是 | 霸主 | 霸主势力专属 tag |
| `ind` / `independent` | 是 | 自由联盟、mercenary、拾荒者 | 自由联盟势力 tag(拾荒者的文件也用它)|
| `persean` | 是 | 英仙座联盟 | 英仙座联盟势力 tag |
| `tritachyon` | 是 | 速子科技 | 速子科技势力 tag |
| `luddic_church` | 是 | 卢德教会 | 卢德教会势力 tag |
| `luddic_path` | 是 | 卢德左径 | 卢德左径势力 tag |
| `lions_guard` | 是 | 狮心守卫 (文件也引用辛达强权 tag)| 狮心守卫势力 tag，辛达强权的少量特殊舰队使用 |
| `sindrian_diktat` | 是 | 辛达强权、狮心守卫 | 辛达强权势力 tag |
| `pirates` | 是 | 海盗 | 海盗势力 tag |
| `derelict` | 是 | derelict | 遗弃船 (绿粽子) 势力 tag |
| `remnant` | 是 | 余晖 | 余晖势力 tag |
| `omega` | 是 | 欧米伽 | 欧米伽势力 tag |
| `merc` | 是 (大量武器与船插带此 tag，如 armoredweapons)| mercenary | 佣兵系 tag，自由联盟的佣兵舰队使用；使用方在生涯舰队生成代码 |
| `rare_bp` | 是 (82 处)| 无 (所有势力默认不拥有)| 稀有蓝图 tag：`campaign/procgen/drop_groups.csv` 的蓝图掉落池只挑带 `rare_bp` 的组件，因此它决定组件能否在野外掉落/挖坟得出 |
| `restricted` | 是 (42 处)| 无 | 限制级：默认不出现在数据库与掉落 (掉落池显式排除 `!restricted`)，适合隐藏数据 |
| `no_drop` | 是 | 无 | 不会在战后掉落 (掉落池显式排除)|
| `no_drop_salvage` | 是 | 无 | 不会在残骸区打捞得到 (掉落池显式排除)|
| `no_sell` / `no_dealer` | 是 | 无 | 不出售 / 不被联络人任务提供；使用方在生涯市场代码 |
| `hist1t` / `hist2t` / `hist3t` | 是 (20/3/2 处)| 无 | 与历史学家相关的蓝图筛选 tag，池内稀有度依次降低 (使用方在生涯代码)|
| `standard` | 是 (船插 tags 列大量使用，如 armoredweapons)| 11 个势力文件 | 通用船插 tag：`knownHullMods` 用它作为基础船插的默认来源，等价于武器侧的 `base_bp` |

如何给自家组件打 tag：船体在 `ship_data.csv` 的 `tags` 列、武器在 `weapon_data.csv` 的 `tags` 列、战机联队在 `wing_data.csv`、船插在 `hull_mods.csv` 的 `tags` 列 ([C1-S2] / [C1-S5] / [C2-S2] 各自的注册页)。

打出 `GearIn_bp` 后，本节 `knownShips` 等块里写上 `"GearIn_bp"` 即自动导入。

::: info 为什么例子里的 tags 与旧教程不同

旧版教程示例给 `knownShips` 写了 `base_bp`、`lowtech_bp` 等一串 tag。

这些 tag 仍然存在，但霸主等正规势力已经在 `hulls` 数组里显式列出全部舰船；势力专属 tag(`hegemony`、`GearIn_bp`、`gear_industry`) 仍然是舰队池的推荐入口。

**给自家舰船打上专属 tag 并从 tags 导入，永远比逐个列举船体更省事。**

:::

## 故障分析

- **势力完全没有出现**：`factions.csv` 缺失、表头不是 `faction`、行首带 `#`、路径错误，或 faction 文件缺少 `id` 时，日志不会出现 `Loading gear_industry faction`。确认 CSV 第一行是 `faction`，第二行是相对 MOD 根目录的完整路径，并以 UTF-8 无 BOM 保存；重启后日志应加载该势力。
- **游戏加载中断，进不了主菜单**：faction JSON 语法错误，必填字段或 `factionDoctrine` 的整数键缺失，或 `illegalCommodities` 引用了无效商品 ID 时，`Loading factions` 后会抛出 `JSONException` 或 `RuntimeException`。补齐字段并从 `data/campaign/commodities.csv` 选取有效 ID，重启后应进入主菜单。
- **势力 ID 与其他来源冲突**：两份 faction 文件产生相同 `id` 时，加载会出现 `Spec of class [...] with id [X] already exists`。改用全局唯一 ID，并对照 `data/world/factions/factions.csv`；重启后重复 spec 异常应消失。
- **势力舰队里没有自己的船**：`default_ship_roles.json` 缺失、装配 ID 拼错，或目标装配尚未注册时，`pickShip` 会改用共享默认角色表并抽取原版装配。只提交自己的舰船装配角色并核对 `data/variants` 中的 ID；重新生成舰队后应出现 GearIn 船只，联队继续由航母装配的 `wings` 数组提供。
- **加载中断并报告 `Ship hull variant [X] not found!`**：`default_ship_roles.json` 或 faction `shipRoles` 中的 ID 无法解析时，`RoleEntryAPI` 会在创建阶段立即校验；以 `_wing` 结尾的 ID 按联队查询。删除无效条目或改成已注册 variant、wing ID，重启后应完成角色表加载。
- **某些势力舰船始终不出现**：`hullFrequency` 为 0、船体不在 `knownShips`，或它在普通池中被概率选择淹没时，生成结果会静默缺少该船。把倍率设为大于 0，核对 `knownShips.tags` 与 `ship_data.csv` 的 tags，必要时加入 `priorityShips`；重复生成后应能观察到目标船体。
- **应当敌对的势力保持中立**：关系设置代码没有接入生涯回调时，新势力会保持 0.0 的 `RepLevel.NEUTRAL`，巡逻队相遇也不会交战。把关系设置接入 MOD 的生涯生成回调，并用 `isHostileTo` 核对 `HOSTILE` 或更差的等级 (≤ -0.49)；重新创建新档后双方应按关系行动。
- **加载阶段报告势力贴图错误**：`logo`、`crest` 或头像路径指向不存在的文件时，TEXTURE 资源加载会直接失败。以 MOD 根目录为基准，把 410×256 和 256×256 PNG 放入 `graphics/factions/` 并核对文件名；重启后贴图应正常加载。
- **情报中的势力颜色不正确**：`color` 数组少于 4 个元素时，加载会抛出 `Color array with id [color] has less than 4 elements`。补齐 RGBA 四项并重启，情报界面应显示目标颜色。
- **情报中的势力名称不正确**：`displayName` 与 `displayNameWithArticle` 不一致时，界面会选用不同的冠词变体。中文环境将两个字段写成同一字符串，重启后各界面的势力名称应一致。
- **势力蓝图或打捞池缺少组件**：`drop_groups.csv` 选择 `rare_bp` 并排除 `no_drop` 和 `restricted`，标签组合错误会让组件静默离开掉落池。可掉落组件使用 `rare_bp`，受限组件使用 `restricted`；重新生成掉落后核对目标组件是否出现。

## 附：数据 ID 归属 (本节定义的 ID)

本节新定义的 ID 只有一个：势力 ID `gear_industry`(小写) 与势力专属 tag `gear_industry`(可在 `ship_data.csv` 的 tags 列使用)。

`GearIn_bp` 由前面各页 ([C1-S2]、[C1-S3]、[C1-S4]、[C1-S5] 与 [C2-S2] 的注册惯例) 沿用，本节只消费。其余引用均为既有 ID。

## 总结

本节在已经注册的势力身份上接通舰船池、角色表、舰队学说和运行时关系。`FactionAPI.pickShip` 只从势力已知的船体和可用角色中选择装配，`hullFrequency` 与优先集合继续改变抽取权重；

舰船池由 `known*` / `priority*` 的 tags 展开、角色表 (势力 `shipRoles` 优先、空则改用共享 `default_ship_roles.json`) 与 `pickShip` 的过滤加权组成完整流程，`factionDoctrine` 与玩家势力编辑器使用同一组字段。

关系侧：**关系不是文件数据**，是运行时数值 (默认 0.0 中立)，由代码经 `setRelationship` / `adjustRelationship` 播种，`RepLevel` 负责数值与等级的映射。

调试时先核对舰船池是否包含目标船体、装配是否进入角色表、学说字段是否完整，以及关系代码是否在新档生成时执行。

逻辑上，关系的代码播种需要一个运行时挂载点 (`ModPlugin` 的生涯生成钩子)，挂载点与星系生成同属一个主题 —— 那是势力注册之后的自然落点：本节的势力与关系 API 就绪后，相关代码即可在那里搬入生效。

## 建议参考项

- `com.fs.starfarer.api.Global` —— 全局入口：`getSector().getFaction(id)` 取势力运行时对象。
- `com.fs.starfarer.api.campaign.FactionAPI` —— 势力运行时接口：关系读写、舰船池查询与角色抽取 (`pickShip` 等)。
- `com.fs.starfarer.api.campaign.FactionDoctrineAPI` —— 舰队学说运行时接口；
- `com.fs.starfarer.api.campaign.FactionSpecAPI` —— 势力 spec 只读接口 (`knownShips`、`hullFrequency` 等)；共享默认角色由内部 `FactionSpec.getDefaultShipRoles()` 提供，公开接口不包含该方法。
- `com.fs.starfarer.api.campaign.RepLevel` —— 关系等级枚举与阈值 (`T1..T4` = 0.09 / 0.24 / 0.49 / 0.74)。
- `data/world/factions/factions.csv` —— 势力注册表 (按行合并，表头 `faction`)。
- `data/world/factions/gear_industry.faction` —— 本节示例势力文件 (完整字段形态)；
- `data/world/factions/default_ship_roles.json` —— 共享装配角色表 (跨来源合并读取)；
- `data/world/factions/hegemony.faction` —— 原版势力文件结构参考；
- `SectorGen`(原版生涯生成脚本类)—— 关系播种的权威示例写法。
- `data/campaign/econ/economy.json` —— `defaultTariff`(取值为 0.3)；
- `data/campaign/commodities.csv` —— `illegalCommodities` 的 ID 校验对象；
- `data/campaign/channels.json` —— 内部频道注册表 (`hegemony_internal`)；
- `data/campaign/procgen/drop_groups.csv` —— 蓝图掉落池 (`rare_bp` / `restricted` 使用方)。
- `data/config/settings.json` —— `doctrineFleetQualityPerPoint`(取值为 0.125)；
- `data/hulls/ship_data.csv` / `data/weapons/weapon_data.csv` / `data/hulls/wing_data.csv` / `data/hullmods/hull_mods.csv` —— 各组件注册表 (tags 列与 tag 存在性)。
