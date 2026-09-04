# 配置势力舰队

本节从 [C3-S1] 复制出的 hegemony 副本出发，把身份之外的内容逐段讲解并替换为 GearIn 自己的舰船池、装配角色与舰队学说。完成后，齿轮工业能够用自己的舰船组成生涯舰队。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 用 `factionDoctrine` 配置势力的舰队组成、质量与性格倾向，并解释玩家势力编辑器对同一组字段的取值约束；
- 用 `knownShips` / `knownWeapons` / `knownFighters` / `knownHullMods`、`hullFrequency`、`priority*` 配置势力舰船池，并解释 tags 展开与 `pickShip` 的过滤规则；
- 用 `default_ship_roles.json`(或势力文件内的 `shipRoles`) 把装配引入生涯舰队。

## 前置知识

- [C3-S1] 的势力注册、可视身份字段、`gear_industry` 势力 ID 以及对应的势力文件；
- [C1-S2] 的船体登记与 ID 规则；
- [C1-S3] 的战机、联队与航母登记规则；
- [C1-S5] 的武器登记与 tags 规则；
- [C2-S2] 的船插登记规则；
- [C4-S1] 的战役与舰船装配组织方式。

## 详细地配置势力文件

从 [C3-S1] 复制并改完可视身份键的 `gear_industry.faction` 开始。副本中其余段落仍是 hegemony 的内容，本节逐段讲解，并把它们替换为 GearIn 自己的配置。

势力 ID 是 `gear_industry`，以 `齿轮工业` 为显示名，舰船池只使用前面各页已注册的 `GearIn_` 内容，另加两艘原版辅助船。

### 势力注册键

`id` 键是势力在整个生涯里的注册键，市场、舰队与星系实体也都用这个 ID 表示归属，它是后续所有页面引用 GearIn 势力的唯一凭据。

本节沿用 `gear_industry`。`id` 必须全局唯一。

```json
id:"gear_industry",
```

### 势力名称

displayName 系列共四个键，都是势力名的形态变体。`displayName` 是短名，显示在情报界面势力列表、舰队名与对话文本里；`displayNameWithArticle` 是带冠词的短名，在中文里最好与 `displayName` 一致。`displayNameLong` 与 `displayNameLongWithArticle` 是长名形态，用在较长的界面文本里。

长名两键不写时分别回落到 `displayName` 与 `displayNameWithArticle`；本节四个键全部显式设置，中文语境下取值一致，均为 `齿轮工业`。

```json
"displayName":"齿轮工业",
"displayNameWithArticle":"齿轮工业",
"displayNameLong":"齿轮工业",
"displayNameLongWithArticle":"齿轮工业",
```

### 势力颜色

color 系列决定势力在界面里的颜色观感。`color` 是势力主色，星图上的势力色与情报界面的势力标识都取自它。

势力文件还可以提供 `baseUIColor` / `darkUIColor` / `gridUIColor` / `brightUIColor` / `secondaryUIColor` 五个界面色，用于势力相关界面的基础、暗、网格、亮与次级配色，但也可留空让默认自行处理。本节沿用 [C3-S1] 设置的 `color`，界面色族不设。

```json
"color":[180,120,60,255],
```

### 势力旗帜

`logo` 与 `crest` 是势力的两面旗帜。`logo` 大旗显示在情报界面的势力条目里；`crest` 小旗出现在市场、收入、勘测与百科等界面。

本节沿用 [C3-S1] 修改过的 `gear_industry.png` 与 `crest_gear_industry.png`。

```json
"logo":"graphics/factions/gear_industry.png",
"crest":"graphics/factions/crest_gear_industry.png",
```

### 舰船与人物名称

`shipNamePrefix` 与 `shipNameSources` 决定舰船名：引擎为舰船取名时 (`pickRandomShipName`) 用前缀拼上从分类里按权重抽出的词，原版 hegemony 的舰船因此以 `HSS` 起名；本节前缀改为 `GIS`，分类则只使用 `GENERAL` 与 `SPACE`。

`names` 以同样的权重方式决定军官、舰队指挥官与市场联系人的人物名，词典在 `data/characters/person_names.csv`；本节只使用为 `modern` 与 `world` 两类。

```json
"shipNamePrefix":"GIS",
"shipNameSources":{
	"GENERAL":2,
	"SPACE":1,
},
"names":{
	"modern":2,
	"world":1,
},
```

### 装配覆盖

`variantOverrides` 按装配 ID 覆盖势力实际可用的装配并改写其抽取权重，命中的条目在 `pickShip` 抽取时生效。

本节不锁定具体变体，整段留空。

```json
"variantOverrides":{
},
```

### 舰船出现倍率

`hullFrequency` 按船体设置出现倍率，直接决定每种船在本势力舰队里出现的频繁程度。`tags` 当场展开为所有带该 tag 的已注册船体，`hulls` 按 ID 指定，`0` 表示绝不出现。

本节给 `GearIn_bp` 设 `1`，让前面各页打上该 tag 的舰船以正常频率进入 GearIn 舰队；但实际上即使不明确写出倍率，该出现的舰船也会按默认频率出现。

```json
"hullFrequency":{
	"tags":{
		"GearIn_bp":1,
	},
	"hulls":{
	},
},
```

### 引入外部蓝图

势力引入其他势力的蓝图时，`shipsWhenImporting` 声明此时额外可用的船体，它是一个独立于 `knownShips` 的集合，加载期单独登记。**但一般来说不会大量使用这个功能**。

留空则回退为 `knownShips`；本节不涉及外部蓝图，整段留空。

```json
"shipsWhenImporting":{
	"tags":[],
	"hulls":[],
},
```

### 舰船池

`knownShips` 是势力的舰船池：舰队生成时 `pickShip` 只从池里抽船，舰队里出现什么船完全由池决定。tags 与 ID 直列两种写法可混用，tags 里的 `gear_industry` 是本节定义的势力专属 tag，打在 `ship_data.csv` 的 tags 列即可让对应船体入池。

本节收入前面各页注册的 `GearIn_artizan` 与 `GearIn_furnaceman`，另加原版辅助船 `dram` 与 `valkyrie` 补充后勤运力。`priorityShips` 把所列船体标记为优先，舰队生成会显著偏向它们，会极大地改动势力舰船构成，一般不填。

```json
"knownShips":{
	"tags":["GearIn_bp", "gear_industry"],
	"hulls":[
		"GearIn_artizan",
		"GearIn_furnaceman",
		"dram",
		"valkyrie",
	],
},
"priorityShips":{
	"tags":[],
	"hulls":[],
},
```

::: info 舰船池的加载与抽取细节

加载期，`loadFactionList` 逐块处理 `known*` 与 `priority*`。`tags` 数组里的每个 tag 去对应的注册表找出带该 tag 的对象：`knownShips` 查船体、`knownWeapons` 查武器、`knownFighters` 查联队、`knownHullMods` 查船插；ID 数组则直接入集合。

抽取时，`pickShip` 先过滤再加权。船体必须在 `knownShips` 里 (`knowsShip`)，出现频率再乘上 `hullFrequency` 的倍率；`ShipPickMode` 决定抽取范围，`priority*` 集合在优先模式下先被抽中。

有两个坑要留意。**拼错的 tag 或 ID 不会报错**，只是匹配不到任何对象，池子悄悄变小。排查 "势力缺船" 时，先对照各注册表的 tags 列逐字核对。另外，抽取权重带缓存，运行时改动舰船池后要调 `clearShipRoleCache()`，否则改动不会生效。

:::

### 战机、武器与船插池

`knownFighters`、`knownWeapons` 与 `knownHullMods` 与舰船池同一套语义，分别决定势力舰队的航母携带哪些联队、舰船装备哪些武器、autofit 自动装配可以使用哪些船插。

本节只收入前面各页注册的 `GearIn_slag_wing`、`GearIn_durin`、`GearIn_dwalin` 与 `GearIn_stable_injector`；`knownHullMods` 的 tags 保留原版通用 tag `standard`，让原版通用船插照常入池。三个对应的 `priority*` 段全部留空，且一般来说最好留空。

```json
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
	"tags":["standard", "GearIn_bp"],
	"hullMods":[
		"GearIn_stable_injector",
	],
},
```

### 舰队学说

`factionDoctrine` 决定生涯里势力舰队的编成风格。

`warships` / `carriers` / `phaseShips` 是战舰、航母与相位舰的编成比例；`officerQuality` / `shipQuality` / `numShips` 控制军官质量、船体质量与舰队规模倾向；`shipSize` 偏向船型大小，`aggression` 偏向军官性格的激进程度；`officerSkills` 与 `commanderSkills` 给军官和指挥官指定技能。

本节按玩家势力编辑器的取值习惯填写：组内总和 7、单项 1–5。

```json
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
```

::: info 学说的解析与运行时读取

写了 `factionDoctrine` 块，就有六个键不能少：`warships`、`carriers`、`phaseShips`、`officerQuality`、`shipQuality`、`numShips`。少任何一个，加载当场以 `JSONObject["<键>"] not found` 中断。

可选键有缺省值兜底：`shipSize`、`aggression` 与 `fleets` 缺省为 3，`autofitRandomizeProbability` 缺省为 0.25，`strictComposition` 缺省为 false。

加载之后，学说成为 `FactionDoctrineAPI`，运行时可经 `FactionAPI.getDoctrine()` 读写。船体质量贡献按 `(shipQuality - 1) * doctrineFleetQualityPerPoint` 计算，`settings.json` 里这个系数是 0.125。

游戏内的势力编辑器与文件使用同一组字段，编辑器把两组各三条的总和固定为 7、单项 1–5；文件本身并不校验这些范围，越界值照样能加载。

:::

### 违禁商品

`illegalCommodities` 声明在该势力市场属于违禁的商品：市场规格创建时读入这份列表，情报界面的非法货物面板也按势力展示违禁商品的列表。

本节只使用 `drugs` 与 `organs` 两项。

```json
"illegalCommodities":[
	"drugs",
	"organs",
],
```

### 场景音乐

`music` 把势力相关的各场景映射到已注册的音乐 ID，由音乐播放器在对应场景取曲播放。`theme` 是势力主题曲；`market_neutral` / `market_hostile` / `market_friendly` 在访问该势力市场时按关系状态播放；`encounter_neutral` / `encounter_hostile` / `encounter_friendly` 在生涯遭遇该势力舰队时播放。

这些 ID 必须已在 `sounds.json` 注册，本节沿用副本的 `music_hegemony_*` 系列，它们的注册链见 [C5-S2]。

```json
"music":{
	"theme":"music_hegemony_market_neutral",
	"market_neutral":"music_hegemony_market_neutral",
	"market_hostile":"music_hegemony_market_hostile",
	"market_friendly":"music_hegemony_market_friendly",
	"encounter_neutral":"music_hegemony_encounter_neutral",
	"encounter_hostile":"music_hegemony_encounter_hostile",
	"encounter_friendly":"music_hegemony_encounter_friendly",
},
```

### 内部频道

副本携带 `internalComms:"hegemony_internal"`，值指向 `data/campaign/channels.json` 里注册的情报频道 (原版即 "Hegemony Intelligence")。

**但它不被任何游戏系统读取，写入与否完全不影响游戏**。本节保留副本原值，仅仅是出于习惯。

```json
"internalComms":"hegemony_internal",  # 原版只解析不读取，写入与否不影响游戏；沿用原版频道占位
```

### 人物头像池

`portraits` 是随机人物头像池：势力生成军官、舰队指挥官与市场联系人时，按 `standard_male` / `standard_female` 从池里抽取。

本节只使用男女各二的四张原版头像，如需使用自己的头像贴图，自行替换路径即可。

```json
"portraits":{
	"standard_male":[
		"graphics/portraits/portrait_hegemony01.png",
		"graphics/portraits/portrait_mercenary03.png",
	],
	"standard_female":[
		"graphics/portraits/portrait_hegemony03.png",
		"graphics/portraits/portrait16.png",
	],
},
```

### 势力头衔

`ranks` 与 `posts` 改写势力的头衔与职位名，显示在本势力重要人物的名称旁与对话称呼里：解析人物身份时先查势力自己的覆写表，没有覆盖的条目回退 `data/world/factions/default_ranks.json` 的默认值。

本节只象征性地覆写势力领袖，`factionLeader` 的头衔与职位都定为 `总工程师`，其余沿用原版默认。

```json
"ranks":{
	"ranks":{
		"factionLeader":{"name":"总工程师"},
	},
	"posts":{
		"factionLeader":{"name":"总工程师"},
	},
},
```

### 联络人声音

`voices` 决定本势力随机人物的声音类型。`LOW` / `MEDIUM` / `HIGH` 三档对应人物的重要度档位 (`PersonImportance`)：生成人物时按其重要度进入对应档位的权重器 (`pickVoice`)，抽出的声音类型决定人物的对话行文与气质，可选类型是原版已定义的 spacer、official、soldier、aristo 等。

本节沿用副本的三档结构与取值，LOW 档以 soldier / spacer 为主，MEDIUM 档以 official / soldier 为主，HIGH 档以 official 为主。

```json
"voices":{
	"LOW":{"soldier":5,"spacer":5},
	"MEDIUM":{"official":5,"soldier":5},
	"HIGH":{"official":5,"aristo":2},
},
```

### 势力行为开关

`custom` 是自由键值表，原版各系统按键读取其中的开关，每个键的效果过于冗长，因此只以备注直接写在片段注释里。本例将按适当的方式取值。

```json
"custom":{
	"engageWhenEvenStrength":false,    # 舰队 AI 势均力敌时也主动进攻
	"offersCommissions":false,         # 是否向玩家提供委任任务
	"engagesInHostilities":true,       # 是否参与势力间敌对关系事件
	"buysAICores":false,               # 基地指挥官是否收购玩家 AI 核心
	"buysSurveyData":false,            # 是否收购勘测数据
	"caresAboutAtrocities":false,      # 是否在意暴行并作出关系反应
	"postsNoBounties":false,           # true 即不发布悬赏
	"allowsTransponderOffTrade":false, # 关应答器是否仍可正常交易
	"decentralized":false,             # 影响舰队的骚扰判定与走私类任务的目标
	"pirateBehavior":false,            # 舰队生成与战争模拟按海盗逻辑运作
	"makesPirateBases":false,          # 是否生成海盗基地
	"fightToTheLast":false,            # 战斗中死战不退
	"gaVIPMission":false,              # 特定任务相关，极不建议开启
	"hostilityImpactOnGrowth":true,    # 势力间敌对影响其殖民地增长
	"punitiveExpeditionData":{         # 惩罚性远征
		"vsCompetitors":false,         # 玩家商品份额逼近本势力时触发反竞争远征
		"vsFreePort":false,            # 针对玩家自由港与违禁品销售的反自由港远征
		"canBombard":false,            # 允许远征轰炸殖民地
		"territorial":false,           # 玩家殖民地达到规模门槛后触发领土主张远征
	},
},
```

## 接入舰队装配

faction 文件只决定 "势力知道哪些船"，舰队生成时按角色 (`combatSmall`、`combatMedium`、`combatLarge`、`combatCapital`、`carrierSmall` 等) 从角色表里抽装配。角色表有两个层级：势力文件自己的 `shipRoles` 段优先生效，缺失或为空时改用全 MOD 共享的 `default_ship_roles.json`；本节不写文件内段，统一走共享角色表。

角色表就是共享的 `default_ship_roles.json`：没有它 (或没有本 MOD 的条目)，装配就不会出现在生涯舰队里，势力只能改用原版装配。

在 MOD 中新建 `data/world/factions/default_ship_roles.json`，**只提交自己的角色条目** (这个文件跨来源按角色对象合并；整份复制会覆盖同键条目，版本不同步时留下陈旧配置)：

::: caution 只提交自己的角色条目，禁止整份复制原版文件

`default_ship_roles.json` 跨来源按角色对象合并：整份复制原版文件会用同键条目的副本值覆盖原版值，版本不同步时会留下陈旧配置，但不会删除原版角色。MOD 只提交自己的角色条目。

:::

每个条目是 `装配 ID → 权重`。**查不到装配会当场抛 `Ship hull variant [X] not found!` 并中断加载**。`includeDefault: true` 可把原版同名角色的条目复制进来再叠加自己的条目 (参考原版文件里各角色的写法；本教程示例不使用)。

注意角色表里只放**舰船装配**：航母角色 (`carrierSmall` 等) 的条目是航母船体的装配，战机联队不进角色表 —— 联队经由舰船装配的 `wings` 数组 [C4-S1] 与 `knownFighters` 池进入舰队。

角色条目 (`RoleEntryAPI`) 对 `_wing` 结尾的 ID 有按联队 ID 查找的历史兼容路径，原版数据不使用它，本教程也不建议使用。

想加更多装配 (例如按 [C4-S1] 的导出流程为其它船体做的装配)，按同一格式加行即可。

装配文件进入角色表后，`knownShips` 先限定势力知道的船体，角色表再限定可抽取的装配；`hullFrequency` 与 `priority*` 调整候选权重，`pickShip` 按这些条件返回装配。

势力文件和舰队配置已经就位。[C3-S3] 将创建 MOD 入口类，并在新档生成时为 `gear_industry` 设置初始关系。

## 完整最小例

以下文件是副本中全部段落替换完成后的最终形态。势力文件写入 `data/world/factions/gear_industry.faction`；装配角色表写入 `data/world/factions/default_ship_roles.json`(文件不存在就新建)。

```json
{
	id:"gear_industry",   # 势力的唯一 ID：注册键是文件里的这个 id，与文件名、CSV 行无关，重复即加载中断
	"color":[180,120,60,255],   # 势力主色 RGBA，用于星图与情报显示
	"displayName":"齿轮工业",
	"displayNameWithArticle":"齿轮工业",   # 带冠词的名称；中文环境下与 displayName 一致
	"displayNameLong":"齿轮工业",           # 长名形态：较长界面文本使用；缺省回落 displayName
	"displayNameLongWithArticle":"齿轮工业", # 长名带冠词：缺省回落 displayNameWithArticle
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
	"internalComms":"hegemony_internal",  # 内部频道 ID：原版只解析不读取，写入与否不影响游戏；沿用原版频道占位
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
		"engageWhenEvenStrength":false,    # 舰队 AI 势均力敌时也主动进攻
		"offersCommissions":false,         # 是否向玩家提供委任任务
		"engagesInHostilities":true,       # 是否参与势力间敌对关系事件
		"buysAICores":false,               # 基地指挥官是否收购玩家 AI 核心
		"buysSurveyData":false,            # 是否收购勘测数据
		"caresAboutAtrocities":false,      # 是否在意暴行并作出关系反应
		"postsNoBounties":false,           # true 即不发布悬赏
		"allowsTransponderOffTrade":false, # 关应答器是否仍可正常交易
		"decentralized":false,             # 影响舰队的骚扰判定与走私类任务的目标
		"pirateBehavior":false,            # 舰队生成与战争模拟按海盗逻辑运作
		"makesPirateBases":false,          # 是否生成海盗基地
		"fightToTheLast":false,            # 战斗中死战不退
		"gaVIPMission":false,              # 特定任务相关，极不建议开启
		"hostilityImpactOnGrowth":true,    # 势力间敌对影响其殖民地增长
		"punitiveExpeditionData":{         # 惩罚性远征
			"vsCompetitors":false,         # 玩家商品份额逼近本势力时触发反竞争远征
			"vsFreePort":false,            # 针对玩家自由港与违禁品销售的反自由港远征
			"canBombard":false,            # 允许远征轰炸殖民地
			"territorial":false,           # 玩家殖民地达到规模门槛后触发领土主张远征
		},
	},
}
```

```json
{
	"combatSmall":{
		"GearIn_artizan_combat":10,
	},
}
```

## 势力字段速查表

| 字段 | 作用 |
|---|---|
| `id` | 势力注册键，运行时取对象的凭据 |
| `displayName` 系列 (含 `displayNameLong` / `displayNameLongWithArticle`) | 势力名称的四种形态 |
| `shipNamePrefix` / `shipNameSources` / `names` | 舰船名与人物名 |
| `color` 与 `*UIColor` 界面色族 | 势力主色与界面配色 |
| `logo` / `crest` | 情报界面大旗与各界面小旗 |
| `variantOverrides` | 个别装配的可用性与权重覆盖 |
| `hullFrequency` | 船体出现倍率 |
| `shipsWhenImporting` | 引入外部蓝图时的可用舰船 |
| `knownShips` / `priorityShips` | 舰船池与优先集合 |
| `knownFighters` / `priorityFighters` | 战机联队池 |
| `knownWeapons` / `priorityWeapons` | 武器池 |
| `knownHullMods` | 船插池 |
| `weaponSellFrequency` 等三段 | 原版市场的装备出售频率 |
| `factionDoctrine` | 舰队组成与风格 |
| `illegalCommodities` | 势力违禁品 |
| `music` | 场景音乐 (主题曲/访问市场/遭遇舰队) |
| `internalComms` | 内部频道 |
| `portraits` | 人物头像池 |
| `ranks` | 势力头衔与职位名 |
| `voices` | 人物声音类型权重 (按重要度分档) |
| `custom` | 势力行为开关 (逐键效果见势力行为开关小节的片段注释) |

## 验证方案

前置数据：[C1-S1] 的 `mod_info.json`、[C1-S2] 的船体 (`GearIn_artizan`)、[C1-S3] 的联队与航母 (`GearIn_slag_wing`、`GearIn_furnaceman`)、[C1-S5] 的武器 (`GearIn_durin`、`GearIn_dwalin`)、[C2-S2] 的船插 (`GearIn_stable_injector`)、[C4-S1] 的装配 (`GearIn_artizan_combat` 等)、本节的 `factions.csv`、`gear_industry.faction`、`default_ship_roles.json`。

1. 启动游戏，加载到主菜单，检查 `starsector.log`。
2. 进入生涯 (或读取已有生涯存档)，按 `F` 打开情报。
3. 在星区里遭遇或靠近齿轮工业的巡逻队/贸易队。
4. 与齿轮工业舰队交互，核对舰船、装配和舰队学说表现。

预期结果：

- 启动游戏加载到势力阶段时，`starsector.log` 出现 `Loading factions`、`Loading default_ship_roles.json` 与 `Loading gear_industry faction`，之后没有任何 `ERROR`。
- 进入生涯 (或任意读取生涯的存档) 后，按 `F` 打开情报 (intel)，齿轮工业出现在势力列表里，显示名、颜色、大旗 (logo) 与小旗 (crest) 与 faction 文件一致。
- 齿轮工业生成的巡逻队、贸易队使用 `knownShips` 里的舰船，组成符合 `factionDoctrine`(战舰/航母比例、船型大小、军官质量)；`GearIn_artizan` / `GearIn_furnaceman` 及其在 `default_ship_roles.json` 里的装配出现在其舰队中；`hullFrequency` 里倍率为 0 的船体永不出现。
- 市场蓝图与打捞层面：带 `rare_bp` 的组件不出现在本势力默认池里，符合掉落池规则 (本节不展开市场)。

## 实用势力包 (bp) 与其它生成型 tags 速查表

下表 tag 存在性来自 `data/weapons/weapon_data.csv`、`data/hulls/ship_data.csv`、`data/hulls/wing_data.csv`、`data/hullmods/hull_mods.csv`；"引用它的势力文件" 来自 `data/world/factions/*.faction`。**原版势力文件还引用本表外的生成 tag：`LG_bp`、`LC_bp`、`dweller`、`threat`。**

| tag | 引用它的原版势力文件 | 实际意义 |
|---|---|---|
| `base_bp` | 海盗、卢德左径、derelict、余晖、欧米伽、玩家势力 | 通用默认池 tag，构成上述势力的舰船/武器池主体；玩家势力的 known\* 也引用它 |
| `lowtech_bp` / `midline_bp` / `hightech_bp` / `missile_bp` | 海盗、卢德左径、余晖 | 科技线 tag。注意：**不是所有对应科技的组件都在其中**；多数正规势力 (霸主、英仙座联盟、辛达强权、卢德教会等) 不再依赖这些 tag，而是在 `hulls` 数组里显式列出舰船 |
| `pirate_bp` | 海盗 | 海盗改造型 (D 插变体) 蓝图 tag |
| `LP_bp` | 卢德左径 | 左径安超 (LP) 型改造包，只有卢德左径拥有它 |
| `XIV_bp` / `heg_aux_bp` | 霸主 | 第十四军团 (XIV) / 霸主辅助军 (A) 型改造包，只有霸主拥有它 |
| `hegemony` | 霸主 | 霸主势力专属 tag |
| `ind` | 非势力团体、拾荒者 | 非势力团体 tag (拾荒者的文件也用它) |
| `persean` | 英仙座联盟 | 英仙座联盟势力 tag |
| `tritachyon` | 速子科技 | 速子科技势力 tag |
| `luddic_church` | 卢德教会 | 卢德教会势力 tag |
| `luddic_path` | 卢德左径 | 卢德左径势力 tag |
| `lions_guard` | 狮心守卫 (文件也引用辛达强权 tag) | 狮心守卫势力 tag，辛达强权的少量特殊舰队使用 |
| `sindrian_diktat` | 辛达强权、狮心守卫 | 辛达强权势力 tag |
| `pirates` | 海盗 | 海盗势力 tag |
| `derelict` | derelict | 遗弃船 (绿粽子) 势力 tag |
| `remnant` | 余晖 | 余晖势力 tag |
| `omega` | 欧米伽 | 欧米伽势力 tag |
| `merc` | mercenary | 佣兵系 tag |
| `rare_bp` | 无 (所有势力默认不拥有) | 稀有蓝图 tag：`campaign/procgen/drop_groups.csv` 的蓝图掉落池只挑带 `rare_bp` 的组件，因此它决定组件能否在野外掉落/挖坟得出 |
| `restricted` | 无 | 限制级：默认不出现在数据库与掉落 (掉落池显式排除 `!restricted`)，适合隐藏数据 |
| `no_drop` | 无 | 不会在战后掉落 (掉落池显式排除) |
| `no_drop_salvage` | 无 | 不会在残骸区打捞得到 (掉落池显式排除) |
| `no_sell` / `no_dealer` | 无 | 不出售或不被联络人任务提供 |
| `hist1t` / `hist2t` / `hist3t` | 无 | 与历史学家相关的蓝图筛选 tag |
| `standard` | 11 个势力文件 | 通用船插 tag：`knownHullMods` 用它作为基础船插的默认来源，等价于武器侧的 `base_bp` |

船体的 tag 打在 `ship_data.csv` 的 `tags` 列，武器打在 `weapon_data.csv` 的 `tags` 列，战机联队打在 `wing_data.csv`，船插打在 `hull_mods.csv` 的 `tags` 列 ([C1-S2] / [C1-S5] / [C2-S2] 各自的注册页)。

打出 `GearIn_bp` 后，本节 `knownShips` 等块里写上 `"GearIn_bp"` 即自动导入。

::: info 为什么例子里的 tags 与旧教程不同

旧版教程示例给 `knownShips` 写了 `base_bp`、`lowtech_bp` 等一串 tag。

这些 tag 仍然存在，但霸主等正规势力已经在 `hulls` 数组里显式列出全部舰船；势力专属 tag(`hegemony`、`GearIn_bp`、`gear_industry`) 仍然是舰队池的推荐入口。

**给自家舰船打上专属 tag 并从 tags 导入，永远比逐个列举船体更省事。**

:::

## 故障分析

- **势力完全没有出现**：`factions.csv` 缺失、表头不是 `faction`、行首带 `#`、路径错误，或 faction 文件缺少 `id` 时，日志不会出现 `Loading gear_industry faction`。

    - **解决方案**：确认 CSV 第一行是 `faction`，第二行是相对 MOD 根目录的完整路径，以 UTF-8 无 BOM 保存并重启。

- **游戏加载中断，进不了主菜单**：faction JSON 语法错误，必填字段或 `factionDoctrine` 的整数键缺失，或 `illegalCommodities` 引用了无效商品 ID 时，`Loading factions` 后会抛出 `JSONException` 或 `RuntimeException`。

    - **解决方案**：补齐字段并从 `data/campaign/commodities.csv` 选取有效 ID，重启。

- **势力 ID 与其他来源冲突**：两份 faction 文件产生相同 `id` 时，加载会出现 `Spec of class [...] with id [X] already exists`。

    - **解决方案**：改用全局唯一 ID，并对照 `data/world/factions/factions.csv`，重启。

- **势力舰队里没有自己的船**：`default_ship_roles.json` 缺失或为空时 `pickShip` 会改用共享默认角色表并抽取原版装配；装配 ID 拼错或目标装配尚未注册时不会回退 —— 角色表在创建阶段立即校验，抛 `Ship hull variant [X] not found!` 中断加载。联队由航母装配的 `wings` 数组提供。

    - **解决方案**：只提交自己的舰船装配角色并核对 `data/variants` 中的 ID，重新生成舰队。

- **加载中断并报告 `Ship hull variant [X] not found!`**：`default_ship_roles.json` 或 faction `shipRoles` 中的 ID 无法解析时，`RoleEntryAPI` 会在创建阶段立即校验；以 `_wing` 结尾的 ID 按联队查询。

    - **解决方案**：删除无效条目或改成已注册 variant、wing ID，重启。

- **某些势力舰船始终不出现**：`hullFrequency` 为 0、船体不在 `knownShips`，或它在普通池中被概率选择淹没时，生成结果会静默缺少该船。

    - **解决方案**：把倍率设为大于 0，核对 `knownShips.tags` 与 `ship_data.csv` 的 tags，必要时加入 `priorityShips`，重复生成。

- **加载阶段报告势力贴图错误**：`logo`、`crest` 或头像路径指向不存在的文件时，TEXTURE 资源加载会直接失败。

    - **解决方案**：以 MOD 根目录为基准，把 410×256 和 256×256 PNG 放入 `graphics/factions/` 并核对文件名，重启。

- **情报中的势力颜色不正确**：`color` 数组少于 4 个元素时，加载会抛出 `Color array with id [color] has less than 4 elements`。

    - **解决方案**：补齐 RGBA 四项并重启。

- **情报中的势力名称不正确**：`displayName` 与 `displayNameWithArticle` 不一致时，界面会选用不同的冠词变体。

    - **解决方案**：中文环境将两个字段写成同一字符串，重启。

- **势力蓝图或打捞池缺少组件**：`drop_groups.csv` 选择 `rare_bp` 并排除 `no_drop` 和 `restricted`，标签组合错误会让组件静默离开掉落池。

    - **解决方案**：可掉落组件使用 `rare_bp`，受限组件使用 `restricted`，重新生成掉落。

## 总结

本节把 [C3-S1] 副本中剩余的 hegemony 段落逐段替换为 GearIn 自己的配置，接通舰船池、角色表和舰队学说。`FactionAPI.pickShip` 只从势力已知的船体和可用角色中选择装配，`hullFrequency` 与优先集合继续改变抽取权重。

`known*` / `priority*` 的 tags 会展开为候选集合，角色表决定装配可用性，`pickShip` 再按过滤结果和权重抽取；势力 `shipRoles` 为空时使用共享 `default_ship_roles.json`，`factionDoctrine` 与玩家势力编辑器读取同一组学说字段。

调试时先核对舰船池是否包含目标船体、装配是否进入角色表，以及学说字段是否完整。势力的运行时关系由 [C3-S3] 的 MOD 入口类在新档生成时设置。

## 建议参考项

- `com.fs.starfarer.api.Global`，全局入口：`getSector().getFaction(id)` 取势力运行时对象。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力运行时接口，负责舰船池查询与角色抽取 (`pickShip` 等)；
- `com.fs.starfarer.api.campaign.FactionDoctrineAPI`：舰队学说运行时接口；
- `com.fs.starfarer.api.campaign.FactionSpecAPI`：势力 spec 只读接口 (`knownShips`、`hullFrequency` 等)；共享默认角色由引擎内部提供，公开接口不包含对应方法。
- `data/world/factions/factions.csv`：势力注册表 (按行合并，表头 `faction`)。
- `data/world/factions/gear_industry.faction`：本节示例势力文件 (完整字段形态)；
- `data/world/factions/default_ship_roles.json`：共享装配角色表 (跨来源合并读取)；
- `data/world/factions/hegemony.faction`：原版势力文件结构参考；
- `data/campaign/econ/economy.json`：`defaultTariff`(取值为 0.3)；
- `data/campaign/commodities.csv`：`illegalCommodities` 的 ID 校验对象；
- `data/campaign/channels.json`：内部频道注册表 (`hegemony_internal`)；
- `data/campaign/procgen/drop_groups.csv`：蓝图掉落池 (`rare_bp` / `restricted` 使用方)。
- `data/config/settings.json`：`doctrineFleetQualityPerPoint`(取值为 0.125)；
- `data/hulls/ship_data.csv` / `data/weapons/weapon_data.csv` / `data/hulls/wing_data.csv` / `data/hullmods/hull_mods.csv`：各组件注册表 (tags 列与 tag 存在性)。
