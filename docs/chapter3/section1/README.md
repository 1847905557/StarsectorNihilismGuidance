# 注册一个势力

到目前为止，齿轮工业的舰船和武器只出现在模拟战和战役里。生涯中没有势力生产、装备它们，玩家也无处购买。要让它们在生涯可用，就得先让它们属于一个势力。

...之类的。不，编者根本不需要介绍势力的重要性，毕竟您大概一开始就是奔着做一个势力 MOD 而来的吧？

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C1-S2] 至 [C1-S5] 的舰船、战机与武器，势力要掌握并使用它们；
- [C1-S3] 的蓝图标签 `GearIn_bp`，势力按它批量导入军备；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，势力文件按它手工修改。

## 复制霸主的势力文件

编者相信您应该已经熟悉复制、粘贴、修改的流程了。

从原版 `data/world/factions/` 复制 `hegemony.faction`，放进 MOD 的同路径，改名为 `gear_industry.faction`。

**不要从空文件开始写势力。** 势力文件有三百多行，缺了必填字段，游戏会在加载时直接报错退出。霸主的文件字段齐全，结构也最规整，拿它做底本最省事。

下面是改好身份字段之后的完整副本。由于势力文件实在过长，不便于以表格形式列出，因此编者以注释形式注解了有意义且适合初学者调整的项目，您可以因情况自行修改。`#` 之后的内容游戏不读取，可以连同注释一起保存：

```json {2-7}
{
	id:"gear_industry", # 势力 ID，市场、舰队、星系实体都用它表示所属势力
	"color":[245,50,30,255], # 势力主色 RGBA，用于星图、情报界面与舰队标识
	"displayName":"齿轮工业", # 势力名称
	"displayNameWithArticle":"齿轮工业", # 带冠词的名称，英文写 the Hegemony；中文与 displayName 相同
	"logo":"graphics/factions/gear_industry.png", # 旗帜贴图，410×256
	"crest":"graphics/factions/crest_gear_industry.png", # 徽章贴图，256×256
	"shipNamePrefix":"HSS", # 舰船名前缀，大写英文字母
	"shipNameSources":{ # 舰船名词库与权重，见 data/strings/ship_names.json
		"HEGEMONY":1,
		"BRITISH_NAVY":1,
		"ROMAN":2,
		"GREEK":1,
		"SPACE":1,
		"GENERAL":1,
	},
	"names":{ # 人名分类与权重，分类见 data/characters/person_names.csv 的 category 列
		"old english":1,
		"modern":1,
		"world":1,
		"future":1,
		"myth":1,
	},

	# variantOverrides restricts hulls to listed variants and adjusts their probability
	"variantOverrides":{ # 限定某船体只用列出的装配，并调整其概率
	},

	# multiplier for how often hulls show up in the faction's fleets
	"hullFrequency":{ # 舰船出现频率倍率，按标签或按船体
		"tags":{
			"heg_aux_bp":1,
			"XIV_bp":0.25,
		},
		"hulls":{

		},
	},
	# ships the faction gets access to when importing S&W out-of-faction
	"shipsWhenImporting":{ # 从势力外进口舰船时可用的舰船
		"tags":[],
		"hulls":[
		],
	},
	"knownShips":{ # 势力掌握的舰船：tags 按蓝图标签整批导入，hulls 逐个列出
		"tags":["heg_aux_bp", "XIV_bp", "hegemony"],
		"hulls":[
			"cerberus",
			"crig",
			"ox",
			"dram",
			"phaeton",
			"prometheus",
			"shepherd",
			"mule",
			"venture",
			"atlas",
			"hermes",
			"mudskipper",
			"nebula",
			"valkyrie",
			"starliner",
			"tarsus",
			"colossus",
			"buffalo_hegemony",

			"hound_hegemony",
			"kite_hegemony",
			"wolf_hegemony",
			"lasher",
			"centurion",
			"gremlin",
			#"vanguard", ? - no, LC

			"condor",
			"enforcer",
			"enforcer_xiv",
			#"manticore", ? - no, LC

			"eagle",
			"dominator",
			"dominator_xiv",
			"eagle_xiv",
			"falcon_xiv",
			"mora",
			"grendel", # 0 phase doctrine, but added to combatLarge elsewhere in this file

			"legion",
			"onslaught",
			"onslaught_xiv",
		],
	},
	# listing ships here will make the faction mostly use them even if other hulls become available
	"priorityShips":{ # 优先舰船：列在这里的船会压过其它已知舰船，没有特殊需要就留空
		"tags":[],
		# capitals are all priority - this makes it so that they will get picked for the "capital" size ship
		# without concern for matching the doctrine, i.e. a Legion will be as likely a pick as an Onslaught
		# despite the Legion being a carrier and the carrier portion of the doctrine being low
		"hulls":[
			"legion",
			"legion_xiv",
			"onslaught",
			"onslaught_xiv",

			# need to be prioritized otherwise the Legion eats any and all carrier points
			"condor",
			"mora",
		],
	},
	"knownFighters":{ # 势力掌握的战机联队
		"tags":["hegemony"],
		"fighters":[
			"talon_wing",
			"broadsword_wing",
			"warthog_wing",
			"piranha_wing",
			"hoplon_wing",
			"longbow_wing",
		],
	},
	"priorityFighters":{ # 优先战机，没有特殊需要就留空
		"tags":[],
		"fighters":[
		],
	},
	"knownWeapons":{ # 势力掌握的武器
		"tags":["hegemony"],
		"weapons":[
			"lightmg",
			"lightdualmg",
			"vulcan",
			"lightac",
			"lightdualac",
			"lightag",
			"lightneedler",
			"railgun",

			"heavymortar",
			"chaingun",
			"heavyac",
			"hveldriver",
			"heavymauler",
			"heavyneedler",
			"flak",
			"dualflak",

			"gauss",
			"hephag",
			"mark9",
			"devastator",
			"multineedler",


			"reaper",
			"atropos",
			"atropos_single",
			"swarmer",
			"annihilator",
			"heatseeker",
			"harpoon",
			"harpoon_single",
			"breach",
			"sabot",
			"sabot_single",

			"pilum",
			"breachpod",
			"harpoonpod",
			"sabotpod",
			"phasecl",
			"typhoon",

			# not mountable on any Hegemony ships (except the Legion XIV, which they don't have immediate access to);
			# still available to buy from them
			"cyclone",
			"hurricane",
			"squall",
			"locust",

			"taclaser",
			"pdlaser",
			"lrpdlaser",
			"ioncannon",
			"gravitonbeam",
			"pulselaser",
			"phasebeam",
		],
	},
	"priorityWeapons":{ # 优先武器，没有特殊需要就留空
		"tags":[],
		"weapons":[
		],
	},
	"knownHullMods":{ # 势力掌握的船插；standard 是原版通用船插标签
		"tags":["standard", "hegemony"],
		"hullMods":[
		],
	},
	"weaponSellFrequency":{ # 势力市场出售武器的频率倍率
		"vulcan":10,
		"lightag":5,
		"lightneedler":10,
		"railgun":10,

		"hveldriver":10,
		"heavymauler":10,
		"heavyneedler":10,
		"flak":5,
		"dualflak":5,

		"gauss":10,
		"hephag":10,
		"multineedler":5,

		"sabot":5,
		"harpoon":5,
		"reaper":5,
		"breachpod":5,
		"harpoonpod":5,
		"sabotpod":5,
	},
	"fighterSellFrequency":{ # 势力市场出售战机的频率倍率
	},
	"hullmodSellFrequency":{ # 势力市场出售船插的频率倍率
		"heavyarmor":10,
		"ballistic_rangefinder":10,
		"targetingunit":10,
	},
	"factionDoctrine":{ # 舰队学说，与玩家自建势力的学说界面对应
		"warships":5, # 常规战舰占比；与下两项合计为 7
		"carriers":2, # 航母占比
		"phaseShips":0, # 相位舰占比

		"officerQuality":5, # 军官质量；与下两项合计为 7
		"shipQuality":1, # 舰船质量，越高 D 插越少
		"numShips":1, # 舰船数量

		"shipSize":4, # 舰船大小倾向，1 到 5，越大越偏向大船

		"aggression":2, # 军官性格倾向，1 到 5，越大越鲁莽

		"combatFreighterProbability":0.1,      				# instead of some portion of the freighters in a fleet
		"combatFreighterCombatUseFraction":0,  				# as part of the normal combat lineup
		"combatFreighterCombatUseFractionWhenPriority":0,   # as part of normal combat lineup, when marked as priority ship
		"autofitRandomizeProbability":0.1, # 自动装配时打乱武器与船插的概率

		"officerSkillsShuffleProbability":1f, # 军官技能打乱概率
		"officerSkills":[ # 军官优先学习的技能
			"ballistic_mastery",
			"ordnance_expert",
		],

		"commanderSkillsShuffleProbability":1, # 指挥官技能打乱概率
		"commanderSkills":[ # 指挥官可用的舰队技能
			"crew_training",
			"officer_training",

			#"tactical_drills", # keep it to two skills to make sure all high-level commanders get level 6 officers
			#"coordinated_maneuvers",
			#"support_doctrine",
		],
	},
	"shipRoles":{ # 在 default_ship_roles.json 之外，为本势力追加的舰船角色装配
		"combatSmall":{
			"includeDefault":true, # true 表示保留默认角色表，只在其上追加
			"gremlin_Strike":10,
		},
		"combatLarge":{
			"includeDefault":true,
			"grendel_Assault":3,
			"grendel_Support":1,
		},
	},
	"illegalCommodities":[ # 违禁商品，ID 见 data/campaign/commodities.csv
		"drugs",
		"organs",
		"hand_weapons",
		"ai_cores",
	],
	"music":{ # 市场与遭遇时播放的音乐
		"theme":"music_hegemony_market_neutral",
		"market_neutral":"music_hegemony_market_neutral",
		"market_hostile":"music_hegemony_market_hostile",
		"market_friendly":"music_hegemony_market_friendly",
		"encounter_neutral":"music_hegemony_encounter_neutral",
		"encounter_hostile":"music_hegemony_encounter_hostile",
		"encounter_friendly":"music_hegemony_encounter_friendly",
	},
	"internalComms":"hegemony_internal", # 势力内部通讯的显示名称 ID
 # 关税，去掉行首 # 才生效；不建议改
	#"tollFraction":0.1,
	#"fineFraction":0.25,
	"portraits":{ # 人物头像，按男女分列
		"standard_male":[
			"graphics/portraits/portrait_hegemony01.png",
			"graphics/portraits/portrait_hegemony02.png",
			"graphics/portraits/portrait_hegemony05.png",
			"graphics/portraits/portrait_hegemony07.png",
			"graphics/portraits/portrait_hegemony09.png",
			"graphics/portraits/portrait_hegemony12.png",
			"graphics/portraits/portrait_hegemony13.png",
			"graphics/portraits/portrait_hegemony15.png",
			"graphics/portraits/portrait_mercenary03.png",
			"graphics/portraits/portrait_mercenary06.png",
			"graphics/portraits/portrait_mercenary08.png",
			"graphics/portraits/portrait15.png",
			"graphics/portraits/portrait33.png",
		],
		"standard_female":[
			"graphics/portraits/portrait_hegemony03.png",
			"graphics/portraits/portrait_hegemony04.png",
			"graphics/portraits/portrait_hegemony08.png",
			"graphics/portraits/portrait_hegemony10.png",
			"graphics/portraits/portrait_hegemony11.png",
			"graphics/portraits/portrait_hegemony14.png",
			"graphics/portraits/portrait_hegemony16.png",
			"graphics/portraits/portrait_hegemony17.png",
			"graphics/portraits/portrait16.png",
			"graphics/portraits/portrait22.png",
			"graphics/portraits/portrait34.png",
			"graphics/portraits/portrait37.png",
			"graphics/portraits/portrait42.png",
		],
	},
	"ranks":{ # 军衔与职位的自定义名称，删去则用原版默认名
		"ranks":{
			"factionLeader":{"name":"High Hegemon"},
			"spaceCommander":{"name":"Commander"},
		},
		"posts":{
			"factionLeader":{"name":"High Hegemon"},
			"patrolCommander":{"name":"Patrol Commander"},
			"fleetCommander":{"name":"Fleet Commander"},
			"baseCommander":{"name":"Base Commander"},
			"militaryAdministrator":{"name":"Military Governor"},
		},
	},
	"voices":{ # 联系人性格类型的权重，按低、中、高重要性分别设置
		"LOW":{
			"soldier":10,
			"faithful":5,
			"spacer":5,
		},
		"MEDIUM":{
			"soldier":10,
			"official":5,
		},
		"HIGH":{
			"soldier":5,
			"official":10,
			"aristo":3,
		},
	},
	"custom":{ # 势力行为开关
		"offersCommissions":true, # 是否提供委任
		"engagesInHostilities":true, # 是否参与势力之间随机爆发的敌对事件
		"buysAICores":true, # 是否收购 AI 核心
		"AICoreValueMult":1, # 收购 AI 核心的价格倍率
		"AICoreRepMult":2, # 收购 AI 核心带来的关系倍率
		"buysSurveyData":true, # 原版代码与规则中均不读取，保留即可
		"hostilityImpactOnGrowth":true, # 当前版本的读取代码已被注释掉，没有效果
		"caresAboutAtrocities":true, # 玩家饱和轰炸时是否降低关系
		"punitiveExpeditionData":{ # 惩戒远征的触发条件
			"vsCompetitors":false, # 是否对商业竞争者发动远征
			"vsFreePort":false, # 是否对自由港发动远征
			"canBombard":false, # 远征时能否轰炸
			"territorial":true, # 是否对建在本势力宣称星系内的玩家殖民地发动远征
		},
		"simulatorData":{ # 模拟战中的显示设置
			"showInSimulator":true,
		},
	},
},
```

**本节只改高亮的六行身份字段，其余一律不动。** 舰船、学说和人物留到下一节逐段替换；看不懂的字段，现在就别碰。

带英文注释的几行是原版自带的说明。`buysSurveyData` 与 `hostilityImpactOnGrowth` 两个开关在当前版本中已经没有代码读取，写成什么都一样，霸主的文件里留着它们，只是没人清理。

## 修改身份字段

`id` 改为 `gear_industry`。市场、舰队、星系实体以后都用这个 ID 表示所属势力，代码里也通过它取得势力。**名称和旗帜可以随时换，ID 一旦被引用就不要再改。** 改了 ID，所有引用它的地方都要跟着改，已有的存档也会出问题。

势力 ID 与 MOD ID `gear_in` 是两回事。一个 MOD 可以注册多个势力，每个势力有自己的 ID；本教程让文件名与势力 ID 保持一致，是为了在引用时一眼就能找到文件。

`displayName` 与 `displayNameWithArticle` 都写 "齿轮工业"。后者用于英文中需要冠词的句子，霸主写的是 `the Hegemony`；中文没有冠词，两项写成一样。

`color` 是势力主色，四个数字依次是红、绿、蓝和不透明度。它出现在星图、情报界面与舰队标识上，玩家在星图上远远看见一支舰队，最先认出的就是颜色。

齿轮工业的 `[245,50,30,255]` 是一个反面例子：它和海盗的红几乎一样，下面的截图里两者上下相隔几行，只能靠旗帜区分。**选色前先打开情报界面看一眼原版势力已经占了哪些颜色。**

`logo` 与 `crest` 是两张贴图，路径相对于 MOD 根目录：

- `graphics/factions/gear_industry.png` 是旗帜，用在情报界面，尺寸 410×256；
- `graphics/factions/crest_gear_industry.png` 是徽章，用在市场、收入、勘测和百科等界面，尺寸 256×256。

`id`、`logo`、`displayName`、`displayNameWithArticle`、`names`、`portraits` 是加载时必须存在的字段。完整副本已经包含它们，只要不误删就不会出问题。

## 登记势力文件

在 MOD 的 `data/world/factions/` 创建 `factions.csv`，写入表头与文件路径：

```csv
faction
data/world/factions/gear_industry.faction
```

**游戏只加载登记过的势力文件。** 文件放对了位置但没有登记，势力不会出现，日志里也不会有任何报错。原版有不少数据都是这样，文件只是内容，登记才让游戏去读；以后做出来的东西在游戏里凭空消失，先查它有没有登记。

### 进游戏核对

保存势力文件、两张贴图与 `factions.csv`，启动游戏，新建一个生涯。按 `F` 打开情报，切到势力页，列表末尾应出现齿轮工业，名称、主色与旗帜与配置一致：

![情报界面中的齿轮工业势力条目](./faction.png 'faction.png')

右侧的违禁商品沿用霸主的配置，描述一栏仍是 `No description... yet`，留到 [C3-S4] 补上。

## 常见问题

- **势力没有出现在情报界面**：`factions.csv` 的表头或路径写错，游戏没有读到势力文件。
    - **解决方案**：确认表头为 `faction`、路径为 `data/world/factions/gear_industry.faction`，保存后重启。

- **启动时报缺少 JSON 字段**：必填字段被误删。
    - **解决方案**：按报错中的字段名，从原版霸主文件补回 `id`、`logo`、`displayName`、`displayNameWithArticle`、`names` 或 `portraits`，保存后重启。

- **启动时报势力重复**：副本的 `id` 仍是 `hegemony`，或其它 MOD 用了相同的 ID。
    - **解决方案**：把 `id` 改为 `gear_industry`，并确认没有其它启用的 MOD 使用这个 ID。

- **旗帜或徽章显示不出来**：贴图路径与实际文件不符。
    - **解决方案**：以 MOD 根目录为基准核对文件名、大小写与目录，保存后重启。

## 建议参考项

- `data/world/factions/hegemony.faction`：本节复制的底本。
- `data/world/factions/factions.csv`：原版势力注册表。
- `com.fs.starfarer.api.impl.campaign.ids.Factions`：原版势力 ID 常量。
