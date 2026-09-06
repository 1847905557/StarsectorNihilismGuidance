# 注册一个势力

本节为 GearIn 注册一个能够被生涯星区读取的势力身份，做法与全书其他数据文件一致，先复制原版势力文件，再修改需要区分的身份字段。页面只处理注册文件和势力 ID、显示名称、旗帜、配色这几个可视键；其余内容沿用原版，由下一节逐段讲解和替换。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 用 `data/world/factions/factions.csv` 注册一个 faction 文件；
- 复制原版 `hegemony.faction` 并修改势力 ID、显示名称、旗帜与配色；
- 根据启动日志和情报界面确认势力身份已经加载；
- 从路径、必填字段和贴图资源定位加载故障。

## 前置知识

- [C1-S1] 的 MOD 目录、数据文件合并和全局 ID 规则；
- [C1-S2] 的资源路径和贴图加载规则；
- 能够打开生涯情报界面并读取 `starsector.log`。

## 注册势力文件

势力注册由 `factions.csv` 和一个 `.faction` 文件完成。`factions.csv` 提供 faction 文件读取路径，`.faction` 文件则完成势力具体配置。文件名可以与文件内部的 `id` 不同，但教程统一使用 `gear_industry.faction` 和 `gear_industry`，保持对应关系。

在 `data/world/factions/factions.csv` 中写入表头和一行路径：

```csv
faction
data/world/factions/gear_industry.faction
```

不过由于`gear_industry.faction`还不存在，此时启动游戏大概不会产生什么很好的结果。

## 配置身份字段

把原版的 `data/world/factions/hegemony.faction` 复制到 MOD 的 `data/world/factions/gear_industry.faction`。和以往一样，我们先改几行最关键的：

```json {2-7}
{
	id:"gear_industry",
	"color":[180,120,60,255],
	"displayName":"齿轮工业",
	"displayNameWithArticle":"齿轮工业",
	"logo":"graphics/factions/gear_industry.png",
	"crest":"graphics/factions/crest_gear_industry.png",
	"shipNamePrefix":"HSS",
	"shipNameSources":{
		"HEGEMONY":1,
		"BRITISH_NAVY":1,
		"ROMAN":2,
		"GREEK":1,
		"SPACE":1,
		"GENERAL":1,
	},
	"names":{
		"old english":1,
		"modern":1,
		"world":1,
		"future":1,
		"myth":1,
	},

	# variantOverrides restricts hulls to listed variants and adjusts their probability
	"variantOverrides":{
	},

	# multiplier for how often hulls show up in the faction's fleets
	"hullFrequency":{
		"tags":{
			"heg_aux_bp":1,
			"XIV_bp":0.25,
		},
		"hulls":{

		},
	},
	# ships the faction gets access to when importing S&W out-of-faction
	"shipsWhenImporting":{
		"tags":[],
		"hulls":[
		],
	},
	"knownShips":{
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
	"priorityShips":{
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
	"knownFighters":{
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
	"priorityFighters":{
		"tags":[],
		"fighters":[
		],
	},
	"knownWeapons":{
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
	"priorityWeapons":{
		"tags":[],
		"weapons":[
		],
	},
	"knownHullMods":{
		"tags":["standard", "hegemony"],
		"hullMods":[
		],
	},
	"weaponSellFrequency":{
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
	"fighterSellFrequency":{
	},
	"hullmodSellFrequency":{
		"heavyarmor":10,
		"ballistic_rangefinder":10,
		"targetingunit":10,
	},
	"factionDoctrine":{
		"warships":5,
		"carriers":2,
		"phaseShips":0,

		"officerQuality":5,
		"shipQuality":1,
		"numShips":1,

		"shipSize":4,

		"aggression":2,

		"combatFreighterProbability":0.1,      			# instead of some portion of the freighters in a fleet
		"combatFreighterCombatUseFraction":0,  				# as part of the normal combat lineup
		"combatFreighterCombatUseFractionWhenPriority":0,   # as part of normal combat lineup, when marked as priority ship
		"autofitRandomizeProbability":0.1,

		"officerSkillsShuffleProbability":1f,
		"officerSkills":[
			"ballistic_mastery",
			"ordnance_expert",
		],

		"commanderSkillsShuffleProbability":1,
		"commanderSkills":[
			"crew_training",
			"officer_training",

			#"tactical_drills", # keep it to two skills to make sure all high-level commanders get level 6 officers
			#"coordinated_maneuvers",
			#"support_doctrine",
		],
	},
	"shipRoles":{
		"combatSmall":{
			"includeDefault":true,
			"gremlin_Strike":10,
		},
		"combatLarge":{
			"includeDefault":true,
			"grendel_Assault":3,
			"grendel_Support":1,
		},
	},
	"illegalCommodities":[
		"drugs",
		"organs",
		"hand_weapons",
		"ai_cores",
	],
	"music":{
		"theme":"music_hegemony_market_neutral",
		"market_neutral":"music_hegemony_market_neutral",
		"market_hostile":"music_hegemony_market_hostile",
		"market_friendly":"music_hegemony_market_friendly",
		"encounter_neutral":"music_hegemony_encounter_neutral",
		"encounter_hostile":"music_hegemony_encounter_hostile",
		"encounter_friendly":"music_hegemony_encounter_friendly",
	},
	"internalComms":"hegemony_internal",
	#"tariffFraction":0.4,
	#"tollFraction":0.1,
	#"fineFraction":0.25,
	"portraits":{
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
	"ranks":{
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
	"voices":{
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
	"custom":{
		"offersCommissions":true,
		"engagesInHostilities":true,
		"buysAICores":true,
		"AICoreValueMult":1,
		"AICoreRepMult":2,
		"buysSurveyData":true,
		"hostilityImpactOnGrowth":true,
		"caresAboutAtrocities":true,
		"punitiveExpeditionData":{
			"vsCompetitors":false,
			"vsFreePort":false,
			"canBombard":false,
			"territorial":true,
		},
		"simulatorData":{
			"showInSimulator":true,
		},
	},
},
```

`id`、`logo`、`displayName`、`displayNameWithArticle`、`names` 和 `portraits` 是必填字段，复制原版文件已经保证了它们全部存在。修改时误删其中任何一个，加载阶段都会在读取对应键时中断。

`crest` 与 `color` 虽然不是必填字段，但不同的颜色和方形徽章有助于在游戏内快速识别。

::: tip 图片规格

一般来说，logo 是一张 宽 410 像素 高 256 像素 的长方形 png 图片，crest 是一张 边长 256 像素 的正方形 png 图片。

:::

### 关键身份字段速查表

| 字段 | 作用 |
|---|---|
| `id` | 唯一指定身份标识 |
| `displayName` | 普通势力名称 |
| `displayNameWithArticle` | 带冠词语境中的名称 |
| `logo` | 主要旗帜 |
| `crest` | 市场、收入、勘测、百科等界面的徽章 |

这些字段用于核对一个势力是否已经具备可被其它生涯对象引用的身份。副本中的舰船池、舰队学说、`shipNamePrefix`、`names` 与 `portraits` 仍是 hegemony 的内容，它们的语义与替换方式在 [C3-S2] 逐段处理。

## 验证方案

前置数据：基于`hegemony.faction` 修改的势力文件已就位，两个旗帜路径能够读取。

1. 启动游戏并等待主菜单完成加载。
2. 在 `starsector.log` 中查找 `Loading gear_industry faction`。
3. 新建或读取生涯，打开情报界面的势力列表。
4. 选择齿轮工业，核对显示名称、颜色和 logo。

预期结果：

- 日志确认 `gear_industry` 势力已经加载，随后没有 faction JSON 或贴图异常；
- 情报界面出现齿轮工业；
- 势力名称、主色和大旗与 faction 文件一致；
- `Global.getSector().getFaction("gear_industry")` 能够取得势力对象。

## 故障分析

- **势力没有出现在情报界面**：`factions.csv` 路径错误、表头不是 `faction` 或注册行以 `#` 开头时，游戏不会读取目标 faction 文件。

    - **解决方案**：核对表头和相对 MOD 根目录的完整路径，重启。

- **加载阶段报告缺少 JSON 字段**：修改时误删了六个必填键之一。

    - **解决方案**：根据异常中的键名补齐字段，重启。

- **加载阶段报告重复 spec**：另一个来源已经注册相同 `id`，例如复制后忘记修改 `id`，副本仍携带 hegemony 的注册键。

    - **解决方案**：为 GearIn 选择全局唯一的势力 ID，并同步后续页面的引用，重启。

- **旗帜或头像无法加载**：资源路径指向不存在的文件，TEXTURE 加载会报告目标路径。

    - **解决方案**：以 MOD 根目录为基准核对文件名和大小写，重启。

## 总结

势力身份由 `factions.csv` 的路径和 faction 文件内部的 `id` 共同建立。复制 hegemony.faction 保证了全部必填键存在，修改后的名称、旗帜与配色提供可以在情报界面核对的显示结果。

后续页面只需引用 `gear_industry`，就能把星系实体、市场和舰队归到同一个势力。下一节会逐段替换副本中剩余的 hegemony 内容，加入 GearIn 自己的舰船池、舰队学说和初始关系。

## 建议参考项

- `data/world/factions/factions.csv`：势力文件注册表；
- `data/world/factions/hegemony.faction`：本节复制的源文件；
- `FactionSpecAPI`：加载后的势力规格；
- `SectorAPI.getFaction(id)`：运行时势力查询入口。
