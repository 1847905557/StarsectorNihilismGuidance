# 配置势力舰队

齿轮工业的身份字段已经确定，接下来替换霸主副本中的军备、舰队学说与人物配置。本节先把此前制作的舰船、战机、武器和船插放进势力可用集合，再为工匠准备一个当前可用的装配，登记到舰队角色表中。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 设置舰队组成、质量与性格倾向，并区分文件配置和玩家势力编辑器的取值范围；
- 用 `known*`、`hullFrequency` 与 `priority*` 组织军备和出现权重；
- 为已注册船体准备装配，并通过角色表让势力使用它；
- 根据加载日志与生成结果核对军备、装配和学说。

## 前置知识

- [C3-S1] 的势力注册与 `gear_industry.faction`；
- [C1-S2] 的工匠船体与数据 ID；
- [C1-S3] 的航母、战机、装配和联队；
- [C1-S5] 的武器 ID 与标签；
- [C1-S6] 的工匠内置武器；
- [C2-S2] 的稳定化推进器；
- [C1-S7] 的 CSV 格式与标签所在列。

## 保留身份，调整名称与人物

打开上一节的 `gear_industry.faction`。ID、旗帜和主色继续使用齿轮工业的配置，下面的局部片段补齐两个长名字段：

```json
{
    "id": "gear_industry",
    "displayName": "齿轮工业",
    "displayNameWithArticle": "齿轮工业",
    "displayNameLong": "齿轮工业",
    "displayNameLongWithArticle": "齿轮工业",
    "color": [180, 120, 60, 255]
}
```

普通短名和带冠词的短名已在上一节设置；`displayNameLong` 与 `displayNameLongWithArticle` 用于长名语境，缺省时分别采用对应短名。本例中文环境四项都填 "齿轮工业"。

`logo` 与 `crest` 的显示位置沿用上一节。还有五个可选界面颜色字段：`baseUIColor`、`darkUIColor`、`gridUIColor`、`brightUIColor` 与 `secondaryUIColor`，分别控制基础、暗、网格、亮和次级配色。当前保留默认处理，主色仍为 `[180,120,60,255]`。

`secondaryUIColor` 还用于势力标记的交替配色，`secondarySegments` 控制分段数，默认为 8。

### 舰船名与人物名

将舰船名前缀改为 `GIS`，名字词典选 `GENERAL` 与 `SPACE`；人物名选 `modern` 与 `world`，局部片段如下：

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

每个分类后的数字是抽取权重。`pickRandomShipName` 从所选词典抽取名字，再与舰船名前缀组合；人物名字则来自 `data/characters/person_names.csv` 中的分类。

**名称也是势力风格的一部分。** 编者建议采用相互协调的类别，再添加自己的名字；舰队里的舰船与人物会频繁同时出现，随意混合往往比某个名字单独使用更突兀。

### 头像、头衔与人物声音

`portraits` 按 `standard_male` 和 `standard_female` 提供头像池，本例保留男女各两张原版头像。需要换成自己的图片时，修改对应路径。

玩家创建角色时使用 `player_male` 和 `player_female` 头像池。在 MOD 的 `data/world/factions/player.faction` 中，将自制头像的路径加入 `portraits` 下的这两个列表，即可用于开局选择。

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

`ranks` 提供军衔和职位名称，人物显示时先用势力自定义值，其余使用 `default_ranks.json`。本例把势力领袖的两种称呼都改为 "总工程师"：

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

`voices` 影响人物对话的行文与气质。LOW、MEDIUM、HIGH 对应人物重要度，分类值同样是权重。本例使用 soldier、spacer、official 与 aristo：

```json
"voices":{
	"LOW":{"soldier":5,"spacer":5},
	"MEDIUM":{"official":5,"soldier":5},
	"HIGH":{"official":5,"aristo":2},
},
```

LOW 以 soldier 与 spacer 为主，MEDIUM 以 official 与 soldier 为主，HIGH 以 official 为主。这些都是原版已定义的类型，完整成品中保留对应分类。

## 将自己的军备放入势力集合

此前多次填写的 `GearIn_bp`，到这里终于开始发挥作用。**势力在军备集合的 `tags` 中填写这个标签，会收集对应注册表中带有它的对象。** 也可以在各自的 ID 数组中直接列出单个对象，两种写法可以混用。

### 设置舰船池

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

`knownShips` 使用船体 ID，本例纳入工匠与守炉人，并保留原版 `dram`、`valkyrie` 补充后勤运力。`gear_industry` 在这里还作为自定义标签使用，可以写进船体数据的 tags 列；它在这处配置中承担的是标签用途。

`priorityShips` 中的对象参与优先抽取，会明显改变舰队构成，本例保持空集合。后续按角色挑选装配时，候选船体要属于刚才配置的已知舰船池。

### 设置战机、武器与船插

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
		"lightdualac",
		"phasecl",
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

战机集合使用联队 ID `GearIn_slag_wing`，武器集合使用武器 ID，船插集合使用船插 ID。本例同时保留 `standard` 标签，以便使用原版通用船插。

本节稍后制作的工匠装配使用 `lightdualac` 与 `phasecl`，因此这两种原版武器也加入已知武器。**军备池和实际装配需要一起安排**；完全脱离原版军备当然可以，但要有足够的自制内容支撑舰队。

加载时，标签分别在船体、联队、武器和船插注册表中展开。拼错标签或直接列出的 ID，集合可能会缺少对象而没有明显加载提示，排查时与各表的 tags 和 id 列逐字核对。

运行时，`FactionAPI.pickShip` 先限定已知船体，再结合角色与权重抽取。`ShipPickMode` 影响抽取范围；运行中改变舰船池后，`clearShipRoleCache()` 清理抽取缓存，让新配置进入下一次选择。

## 调整舰船出现频率

`hullFrequency` 为船体提供出现倍率，下面给 `GearIn_bp` 设为 1：

```json
"hullFrequency":{
	"tags":{
		"GearIn_bp":1,
	},
	"hulls":{
	},
},
```

`tags` 按标签收集船体，`hulls` 按具体 ID 调整。1 为正常倍率，0 表示该船体不参与这类抽取。因此，普通候选可以沿用默认频率，需要偏向或排除的船体才单独配置。

`variantOverrides` 以装配 ID 限定可用装配或调整其权重，当前保持空对象。`shipsWhenImporting` 则提供势力引入外部蓝图时使用的独立舰船集合；留空时使用 `knownShips`。两个局部片段如下：

```json
"variantOverrides":{
},
```

```json
"shipsWhenImporting":{
	"tags":[],
	"hulls":[],
},
```

## 确定舰队学说

舰队学说决定组成与风格。**军备池提供可用对象，学说决定它们的编成倾向。** 本例以普通战舰为主，保留一部分航母和相位舰，军官质量较高，船体质量与数量采用较低档位。

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
	"combatFreighterCombatUseFraction":0,              # 舰队生成时把武装货舰并入战斗阵容的权重倍率
	"combatFreighterCombatUseFractionWhenPriority":0,  # 标记为优先时武装货舰当战舰用的权重倍率
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

`warships`、`carriers` 与 `phaseShips` 控制三类舰船比例；`officerQuality`、`shipQuality` 与 `numShips` 控制军官、船体质量和数量倾向。`shipSize` 偏向船型大小，`aggression` 偏向军官性格。`officerSkills` 与 `commanderSkills` 分别指定军官和指挥官技能，ShuffleProbability 控制相应技能选择的随机程度。

两个三项组都按玩家势力编辑器的习惯取总和 7、单项 1–5。**这是本例的设计约定**，文件读取允许其他数值；游戏内编辑器采用该范围，两者的限制分别由各自的使用方式决定。

`combatFreighterProbability` 控制替换为武装货舰的比例，两个 CombatUseFraction 调整它们进入战斗阵容的权重。`autofitRandomizeProbability` 控制装配随机化，本例填 0.1。

### 学说读取规则速查表

| 内容 | 规则 |
|---|---|
| 必填整数 | warships、carriers、phaseShips、officerQuality、shipQuality、numShips |
| shipSize、aggression、fleets | 缺省为 3 |
| autofitRandomizeProbability | 缺省为 0.25 |
| strictComposition | 缺省为 false |
| 船体质量贡献 | `(shipQuality - 1) * doctrineFleetQualityPerPoint` |
| 当前质量系数 | `settings.json` 中为 0.125 |
| 运行时对象 | `FactionAPI.getDoctrine()` 返回 `FactionDoctrineAPI` |

配置了学说对象后，六个必填整数都需要存在；缺少时会出现对应的 JSON 键错误。设置完整后再进入游戏比较实际编成。

## 准备当前可用的舰船装配

舰船池已经配置，接下来还需要指定舰船采用的装配。舰队使用的是装配 ID，战机制作时已经接触过 `.variant`，本节沿用同一结构，为工匠创建 `data/variants/GearIn_artizan_faction.variant`：

```json
{
    "displayName": "Faction",
    "fluxCapacitors": 0,
    "fluxVents": 0,
    "hullId": "GearIn_artizan",
    "hullMods": [],
    "variantId": "GearIn_artizan_faction",
    "weaponGroups": [
        {
            "autofire": false,
            "mode": "LINKED",
            "weapons": {
                "WS0005": "lightdualac",
                "WS0006": "lightdualac"
            }
        },
        {
            "autofire": true,
            "mode": "ALTERNATING",
            "weapons": {
                "WS0001": "phasecl",
                "WS0002": "phasecl"
            }
        }
    ]
}
```

`hullId` 指向工匠，`variantId` 定义本节的装配 ID。两门双联轻型自动炮填入 WS0005、WS0006，两门相位长矛填入 WS0001、WS0002。槽位 ID 按 [C1-S2] 的实际船体检查，[C1-S6] 的内置都林仍由船体提供。

这是当前用于角色登记的装配。后续 [C4-S1] 会通过测试战役导出更多配置，再按相同方法添加到角色表；航母联队则写在航母装配的 `wings` 数组中，并从 `knownFighters` 取得可用联队。

## 登记舰队角色

创建 `data/world/factions/default_ship_roles.json`，把工匠装配放进 `combatSmall`：

```json
{
    "combatSmall": {
        "GearIn_artizan_faction": 10
    }
}
```

每个角色保存 "装配 ID → 权重"，将刚才的设计纳入对应类别的候选。战斗角色还有 combatMedium、combatLarge、combatCapital，航母角色包括 carrierSmall 等。生成代码按角色名选择该类装配。

**这个共享文件只写 MOD 自己添加的条目。** 同路径数据按角色对象合并，同键条目会被新值覆盖；保留自己的记录便于维护。势力文件也可以设置独立 `shipRoles`，有自己的角色内容时优先使用，缺失或为空时使用共享角色表。

`includeDefault: true` 会把同名角色的默认条目纳入独立角色配置。当前走共享表，直接追加自有装配即可。登记时需要已经存在对应装配，错误 ID 会在加载阶段产生 `Ship hull variant [X] not found!`。

角色查询也有将 `_wing` 后缀按联队解析的行为；本例使用舰船装配来定义舰队角色，航母联队由装配的 `wings` 提供。

## 设置市场、音乐与行为字段

### 违禁商品与音乐

`illegalCommodities` 决定势力市场的违禁品，情报面板也显示这一列表。本例填写 `drugs` 和 `organs`，ID 取自 `data/campaign/commodities.csv`。

```json
"illegalCommodities":[
	"drugs",
	"organs",
],
```

`music` 把场景对应到音乐 ID。主题、市场中立／敌对／友好、舰队遭遇中立／敌对／友好分别读取下面的键：

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

这里沿用原版已登记的 `music_hegemony_*`；自定义音乐的登记由 [C5-S2] 讲解。

`internalComms` 保留原版的 `hegemony_internal`，指向 channels.json 的原版情报频道。当前原版会解析这个字段，具体游戏行为并不使用它，本节沿用副本值。

### 自定义行为开关

`custom` 保存各系统读取的行为参数，按照当前设计替换为：

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

这组配置决定委任、收购、关系事件、走私交易、基地与远征等行为。参数旁的注释说明当前用途，调整时以所需行为为依据，再修改对应的开关。

完整文件还可以用 `weaponSellFrequency`、`fighterSellFrequency` 和 `hullmodSellFrequency` 调整原版市场的装备出售频率，它们与舰队出现频率分别服务于对应位置。

## 完整最小例

势力配置写入 `data/world/factions/gear_industry.faction`，角色表采用上面的内容，工匠装配保存到 `data/variants/GearIn_artizan_faction.variant`。`factions.csv` 继续登记 [C3-S1] 的路径。

::: details 完整势力配置：gear_industry.faction

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
			"lightdualac",
			"phasecl",
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
		"combatFreighterCombatUseFraction":0,              # 舰队生成时把武装货舰并入战斗阵容的权重倍率
		"combatFreighterCombatUseFractionWhenPriority":0,  # 标记为优先时武装货舰加入战斗阵容的权重倍率
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

:::

## 势力字段速查表

| 字段 | 作用 |
|---|---|
| `id` | 势力注册键，运行时取对象的凭据 |
| `displayName` 系列 (含 `displayNameLong` / `displayNameLongWithArticle`) | 势力名称的四种形态 |
| `shipNamePrefix` / `shipNameSources` / `names` | 舰船名与人物名 |
| `color` 与 `*UIColor` 界面色族 | 势力主色与界面配色 |
| `secondarySegments` | 标记的次级颜色分段数，默认 8 |
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
| `portraits.player_male` / `portraits.player_female` | 玩家新建角色的头像池 |
| `ranks` | 势力头衔与职位名 |
| `voices` | 人物声音类型权重 (按重要度分档) |
| `custom` | 势力行为开关 |
| `custom.noBountyForDefeating` | 击败该势力舰队时，雇佣协议和星系悬赏结算跳过该舰队，默认 false |

## 生成型标签速查表

标签位于船体、武器、联队与船插各自的 CSV 中，对应集合按对象类别收集。原版势力还使用 LG_bp、LC_bp、dweller 和 threat 等标签。

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

`GearIn_bp` 用于本教程自有军备，写入 CSV 的 tags 后，再在势力 `known*` 的 tags 中引用。按明确的对象集合使用标签，后续增加军备时可以沿用同一个入口。

## 验证方案

前置数据：已启用 Console Commands，以便生成当前势力测试舰队。

1. 保存势力文件、工匠装配与角色表，重启游戏，检查 factions、default_ship_roles 和装配的加载记录。
2. 进入生涯，打开情报中的齿轮工业，核对名称、旗帜、颜色与违禁品。
3. 执行 `SpawnFleet gear_industry 60`，观察玩家附近的测试舰队，检查工匠及其装备。
4. 多次生成测试舰队，核对军备集合、装配与出现权重；Console Commands 的该命令用于军备和装配接入检查。
5. 本章星系和市场建立后，再观察原版生成的该势力舰队，比较舰队学说的组成、船型、军官和质量倾向。

预期结果：

- 势力与装配加载完成，角色表能读取 `GearIn_artizan_faction`。
- 情报中的名称、旗帜、颜色和违禁品与配置一致。
- 测试舰队可以使用已知池中的工匠及登记装配。
- 标签、直接 ID、角色与权重共同参与候选选择，频率为 0 的船体不进入该候选抽取。
- 原版生成的舰队按学说组织；测试命令的编成采用其自身生成参数。
- 市场与打捞使用各自的标签筛选，按 rare_bp、restricted 和对应掉落标签核对。

## 故障分析

- **势力没有加载**：注册表路径、表头或行首注释错误。
    - **解决方案**：核对 faction 表头与相对 MOD 根目录的文件路径，保存后重启。

- **加载时报 JSON 错误**：必填字段、学说整数或数据结构缺失。
    - **解决方案**：按日志键名检查字段，保留六项学说整数和完整身份数据。

- **加载时报重复势力 ID**：其他已启用来源使用相同 ID。
    - **解决方案**：核对势力注册值及后续引用，保留唯一身份。

- **角色表报告 Ship hull variant [X] not found!**：装配尚未保存，或角色条目使用不同 ID。
    - **解决方案**：核对 `.variant` 中的 `variantId`，再登记同一值。

- **舰队缺少某种自制舰船**：船体不在 knownShips，装配未进入可用角色，或出现倍率为 0。
    - **解决方案**：依次核对注册表、标签、装配和角色，调整权重后重新生成。

- **运行时修改军备后抽取结果保持原样**：角色缓存仍保存原来的候选。
    - **解决方案**：通过 `clearShipRoleCache()` 清理缓存，再进行下一次抽取。

- **资源加载报 TEXTURE 错误**：旗帜或头像路径没有对应文件。
    - **解决方案**：以 MOD 根目录为基准核对图片目录、名称与大小写。

- **势力颜色或名称不符**：RGBA 项数错误，或名称的语境变体不同。
    - **解决方案**：填写四项颜色值，并在中文配置中统一对应名称字段。

- **蓝图或打捞结果不符**：组件标签没有满足对应掉落池的筛选。
    - **解决方案**：按 drop_groups.csv 核对 rare_bp、restricted、no_drop 与其他相关标签。

## 总结

势力的军备集合、角色装配与学说共同决定舰队配置。标签收集对象，装配指定具体装备，角色为生成提供候选，出现倍率与优先集合再调整权重。

本节把霸主副本改为齿轮工业的配置，并提供了可以立即登记的工匠装配。接下来通过 MOD 入口设置关系、创建星系和市场，这些军备与人物配置会继续用于生涯内容。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局入口，`getSector().getFaction(id)` 取势力运行时对象。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力运行时接口，负责舰船池查询与角色抽取 (`pickShip` 等)；
- `com.fs.starfarer.api.campaign.FactionDoctrineAPI`：舰队学说运行时接口；
- `com.fs.starfarer.api.campaign.FactionSpecAPI`：势力 spec 只读接口 (`knownShips`、`hullFrequency` 等)；共享默认角色由引擎内部提供，公开接口不包含对应方法。
- `data/world/factions/factions.csv`：势力注册表 (按行合并，表头 `faction`)。
- `data/world/factions/default_ship_roles.json`：共享装配角色表 (跨来源合并读取)；
- `data/world/factions/hegemony.faction`：原版势力文件结构参考；
- `data/campaign/econ/economy.json`：`defaultTariff`(取值为 0.3)；
- `data/campaign/commodities.csv`：`illegalCommodities` 的 ID 校验对象；
- `data/campaign/channels.json`：内部频道注册表 (`hegemony_internal`)；
- `data/campaign/procgen/drop_groups.csv`：蓝图掉落池 (`rare_bp` / `restricted` 使用方)。
- `data/config/settings.json`：`doctrineFleetQualityPerPoint`(取值为 0.125)；
- `data/hulls/ship_data.csv`：舰船注册表，按 `tags` 列核对 tag 存在性；
- `data/weapons/weapon_data.csv`：武器注册表，按 `tags` 列核对 tag 存在性；
- `data/hulls/wing_data.csv`：战机联队注册表，按 `tags` 列核对 tag 存在性；
- `data/hullmods/hull_mods.csv`：船插注册表，按 `tags` 列核对 tag 存在性。
- `Starsector/mods/Console Commands/data/console/commands.csv`：`SpawnFleet` 参数及测试适用范围。
