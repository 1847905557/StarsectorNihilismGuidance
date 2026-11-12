# 注册一个势力

制作势力，可以先从玩家最容易认出的名称、旗帜与配色开始。这里以霸主的配置为基础制作齿轮工业，完成身份字段与势力登记；至于它实际使用哪些舰船，留到下一节处理。

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~10 分钟。_

## 学习目标

完成后，您将能够：

- 复制原版势力文件，修改 ID、显示名称、旗帜与配色；
- 用 `data/world/factions/factions.csv` 登记势力文件；
- 在启动日志和情报界面确认势力身份；
- 根据路径、必填字段与资源定位加载故障。

## 前置知识

- [C1-S1] 的 MOD 目录、数据合并与全局 ID 规则；
- [C1-S2] 的贴图路径和资源放置方法；
- [C1-S7] 的 CSV 表头、行首注释与文本编码；
- 能够打开生涯情报界面并读取 `starsector.log`。

## 复制原版势力文件

编者相信您应该已经熟悉复制、粘贴、修改的流程了。势力文件同样如此，不过这次的文件相当长。**本节只改身份字段，其余内容保留原版配置**，下一节再逐段调整。

从原版的 `data/world/factions/` 复制 `hegemony.faction`，放进 MOD 的同路径，改名为 `gear_industry.faction`。

用编辑器打开副本，找到开头的 `id`、名称、配色和旗帜字段。下面的局部片段对应本次修改，替换到完整副本的相同位置：

```json
{
    "id": "gear_industry",
    "color": [180, 120, 60, 255],
    "displayName": "齿轮工业",
    "displayNameWithArticle": "齿轮工业",
    "logo": "graphics/factions/gear_industry.png",
    "crest": "graphics/factions/crest_gear_industry.png"
}
```

下面先处理 ID，再修改名称与图片。ID 供后面的生涯对象引用，名称与图片则用于界面显示。

### 确定后续引用的 ID

先把 `id` 改为 `gear_industry`。**星系实体、市场和舰队以后都用这个 ID 表示所属势力**，名称可以按创作需要调整，已经使用的 ID 则要保持独特、稳定，并与引用逐字符一致。

注册表填写文件路径，其他生涯对象引用文件内部的势力 ID。两者可以不同；本教程统一采用 `gear_industry.faction` 与 `gear_industry`，让文件和对象一眼就能对应。

这里的势力 ID 为 `gear_industry`，MOD ID 仍为 `gear_in`。一个 MOD 可以注册多个势力，每个势力使用自己的独立 ID。

### 写入势力名称

把 `displayName` 和 `displayNameWithArticle` 都改为 "齿轮工业"。前者用于普通名称，后者用于需要冠词的语境；中文示例中，两项写成一样即可。

副本中的 `shipNamePrefix`、`names` 和 `portraits` 仍采用霸主的舰船名前缀、人名与头像配置，[C3-S2] 会与舰队配置一起替换。

### 设置势力主色

`color` 使用 RGBA 数组。本例填写 `[180,120,60,255]`，前三个数字决定红、绿、蓝，最后一个数字决定不透明度。

主色用于星图和情报界面的势力标识。这个字段可以省略；为自定义势力选择与原版不同的配色，则有助于玩家识别它的舰队和所属地点。

### 放入旗帜与徽章

准备两张 PNG，并放到 MOD 的 `graphics/factions/`：

- `gear_industry.png`：作为 `logo`，本例采用宽 410 像素、高 256 像素的长方形旗帜。
- `crest_gear_industry.png`：作为 `crest`，本例采用边长 256 像素的方形徽章。

`logo` 用于情报界面的主要旗帜；`crest` 用在市场、收入、勘测与百科等界面。路径都是相对于 MOD 根目录的路径。

::: tip 旗帜制作规格

这两组尺寸是常用制作规格。贴图可以从原版加工，资源路径填写相对于 MOD 根目录的位置，文件名与大小写保持一致。

:::

### 保留副本中的必填内容

`id`、`logo`、`displayName`、`displayNameWithArticle`、`names` 和 `portraits` 是加载时读取的必填字段。完整原版副本已经包含这些键，修改身份时保留它们，随后再按本节的加载日志确认结果。

`crest` 与 `color` 为可选字段，本例同时填写。其余舰船池、学说与人物配置当前采用霸主的值，下一节继续调整。

### 身份字段速查表

| 字段                     | 当前作用与填写方式                                |
| ------------------------ | ------------------------------------------------- |
| `id`                     | 后续生涯对象使用的势力 ID，本例为 `gear_industry` |
| `displayName`            | 普通显示名称，本例为 "齿轮工业"                   |
| `displayNameWithArticle` | 带冠词语境的名称，中文示例与普通名一致            |
| `color`                  | 主色 RGBA，本例为 `[180,120,60,255]`              |
| `logo`                   | 情报界面主要旗帜的资源路径                        |
| `crest`                  | 市场、收入、勘测与百科等界面的徽章路径            |
| `names`                  | 加载需要的人名分类配置，当前保留霸主副本          |
| `portraits`              | 加载需要的头像配置，当前保留霸主副本              |

## 登记势力文件

副本和图片保存好后，在 MOD 的 `data/world/factions/` 创建 `factions.csv`。已有这个文件时，在其中追加路径即可：

```csv
faction
data/world/factions/gear_industry.faction
```

表头是 `faction`，数据行写文件路径。游戏先沿这条路径读取 `.faction`，再用文件里的 `id` 注册势力，刚才修改的名称和旗帜才有机会出现在界面中。

按 [C1-S7] 的 CSV 规则保存，确认注册行有效，然后启动游戏核对结果。

## 完整最小例

`factions.csv` 使用上面的表头与路径。下面是保留霸主配置、改好身份字段的完整副本，写入 `data/world/factions/gear_industry.faction`，并准备对应的两张旗帜图片。

::: details 完整势力文件：gear_industry.faction

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

:::

## 验证方案

1. 保存势力文件、旗帜图片与注册表，启动游戏并等待主菜单加载完成。
2. 打开 `starsector.log`，搜索 `Loading gear_industry faction`，检查随后是否有 JSON 字段或资源加载错误。
3. 新建或读取生涯，按 `F` 打开情报，再进入势力列表。
4. 选择齿轮工业，核对名称、主色与旗帜。

预期结果：

- 日志出现 `Loading gear_industry faction`，势力加载完成。
- 势力列表出现 "齿轮工业"，名称、主色和旗帜与当前配置一致。
- 后续代码可以通过 `Global.getSector().getFaction("gear_industry")` 取得该势力。

观察势力列表中的齿轮工业条目与右侧名称：

![情报界面中的齿轮工业势力条目](./faction.png 'faction.png')

图中展示了齿轮工业在势力列表中的位置与名称。实际配色按当前 `color` 核对，势力描述由 [C3-S5] 补充。

## 故障分析

- **势力没有出现在情报界面**：`factions.csv` 表头、路径或行首注释错误，游戏没有读取目标文件。
    - **解决方案**：确认表头为 `faction`，登记路径为 `data/world/factions/gear_industry.faction`，保存后重启。

- **加载时报缺少 JSON 字段**：副本中的六个必填键被误删。
    - **解决方案**：按异常中的键名补齐 `id`、`logo`、`displayName`、`displayNameWithArticle`、`names` 或 `portraits`，保存后重启。

- **加载时报重复 spec**：其他来源已经注册了相同势力 ID，或副本仍使用 `hegemony`。
    - **解决方案**：将副本 ID 改为 `gear_industry`，核对它在所有启用的 MOD 中唯一，并同步引用。

- **旗帜或头像加载失败**：资源路径指向不存在的文件，日志报告对应 TEXTURE 路径。
    - **解决方案**：以 MOD 根目录为基准检查文件名、大小写与目录，保存后重启。

## 总结

现在齿轮工业已经有了可被游戏识别的势力身份：注册表指向文件，文件里的 ID 供生涯对象引用，名称、颜色和旗帜供玩家辨认。

不过，它的舰船池与学说仍然来自霸主。名称和旗帜已经换好，军备却还很熟悉；齿轮工业的特色要继续落实到自己的舰船与学说上。下一节调整这部分，[C3-S3] 再通过 MOD 入口设置初始关系。

## 建议参考项

- `data/world/factions/factions.csv`：登记势力文件路径的注册表。
- `data/world/factions/hegemony.faction`：本节复制的完整势力配置。
- `com.fs.starfarer.api.campaign.FactionSpecAPI`：加载后的势力规格。
- `com.fs.starfarer.api.campaign.SectorAPI`：`getFaction(id)` 提供生涯势力查询。
