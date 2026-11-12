# 配置势力舰队

玩家认识一个势力，多半是从星图上一次次遇见它的舰队开始的。船型、武器、交战时的进退，见过几次就会记住。这份印象就来自于它的舰队配置。

当然，还有一个重要原因，就是 faction 文件在上一节根本讲不完。但它不能不讲完。所以编者分了一节出来。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~15 分钟。_

## 导入自己的军备

::: tip bp 的作用

编者曾建议您为您制作的舰船、战机、武器填写一些似乎意义不明的 `前缀名_bp`，在本例为 `GearIn_bp`。

现在它们将产生作用。**在 `known*` 段的 `tags` 里写上 `GearIn_bp`，带这个标签的舰船、战机、武器就会整批导入势力。**

:::

打开 `gear_industry.faction`，把 `knownShips` 到 `knownHullMods` 这一段替换为：

```json
"knownShips":{
	"tags":["base_bp", "GearIn_bp"],
	"hulls":[
		"atlas",
		"prometheus",
		"valkyrie",
	],
},
# listing ships here will make the faction mostly use them even if other hulls become available
"priorityShips":{
	"tags":[],
	"hulls":[
	],
},
"knownFighters":{
	"tags":["base_bp", "GearIn_bp"],
	"fighters":[
		"thunder_wing",
		"hoplon_wing",
	],
},
"priorityFighters":{
	"tags":[],
	"fighters":[
	],
},
"knownWeapons":{
	"tags":["base_bp", "GearIn_bp", "lowtech_bp", "midline_bp", "missile_bp"],
	"weapons":[
		"railgun",
		"dualflak",
		"heavymauler",
		"hveldriver",
		"gauss",
		"mjolnir",
		"breachpod",
		"sabotpod",
		"phasecl",
		"cyclone",
		"hurricane",
	],
},
"priorityWeapons":{
	"tags":[],
	"weapons":[
	],
},
"knownHullMods":{
	"tags":["standard"],
	"hullMods":[
		"GearIn_stable_injector",
	],
},
```

`tags` 按标签整批导入，`hulls`、`fighters`、`weapons`、`hullMods` 逐个列出 ID，两种写法可以混用。

齿轮工业只做了战斗舰船，没有货船、油船和运兵船。舰队的后勤由 `base_bp` 带来的原版民用船承担，`atlas`、`prometheus`、`valkyrie` 再补上大型货船、油船和运兵船。**自己没做的船种，用原版的补齐，不要让势力的舰队缺一整类船。**

::: tip 为势力安排武器和战机

您未必能脱离原版武器库与战机库来形成您势力的军备，因此不建议删除所有武器方面的导入。

:::

齿轮工业自己的武器只有三种，撑不起一支舰队的装配。本例导入了低科、中科与导弹三个通用标签，再逐个补上几门风格相近的动能武器与导弹。

船插的导入方式不同。**原版通用船插的标签是 `standard`，不是 `base_bp`。** 写成 `base_bp` 不会报错，势力只是一个通用船插都不会，舰队里的船除了内置船插什么也装不上。标签写错的后果都是这样：游戏照常运行，集合悄悄变空。以后舰船、武器在势力中凭空少了一批，先去对应的 CSV 核对 `tags` 列里到底写的是什么。

稳定化推进器没有带 `GearIn_bp`，所以在 `hullMods` 里单独列出。

`priority*` 段列出的对象会被优先选用，即使势力还掌握其它选项。没有特殊需要，留空即可。

## 确定舰队学说

把 `factionDoctrine` 段替换为：

```json
"factionDoctrine":{
	"warships":4, # 常规战舰占比
	"carriers":2, # 航母占比
	"phaseShips":1, # 相位舰占比

	"officerQuality":5, # 军官质量
	"shipQuality":1, # 舰船质量，越低 D 插越多
	"numShips":1, # 舰船数量

	"shipSize":4, # 舰船大小倾向，越大越偏向大船

	"aggression":2, # 军官性格倾向，越大越鲁莽

	"combatFreighterProbability":0.1,      				# instead of some portion of the freighters in a fleet
	"combatFreighterCombatUseFraction":0,  				# as part of the normal combat lineup
	"combatFreighterCombatUseFractionWhenPriority":0,   # as part of normal combat lineup, when marked as priority ship
	"autofitRandomizeProbability":0.1, # 自动装配时打乱武器与船插的概率

	"commanderSkillsShuffleProbability":1, # 指挥官技能打乱概率
	"commanderSkills":[ # 指挥官可用的舰队技能
		"crew_training",
		"officer_training",
		"coordinated_maneuvers",
		"support_doctrine",
	],
},
```

学说与玩家自建势力的学说界面一一对应，**舰船比例三项、质量与数量三项，各自合计为 7，单项取 1 到 5。** 这是界面的限制，文件本身不检查。原版势力都守着这个范围，超出它的值也能读进游戏，效果就只能靠自己测试了。

齿轮工业的取舍写在数字里。工匠是主力，战舰占 4；看炉人与锻件各撑起一部分，航母占 2、相位舰占 1。军官质量拉满，代价是舰船质量和数量都压到最低：舰队船不多，D 插不少，但每条船上都可能坐着一个老练的军官。这是一个工业势力把钱花在人身上的样子，不一定最强，但遇见两次就能认出来。

`shipSize` 与 `aggression` 不在合计里，单独取 1 到 5。`officerSkills` 段在本例中删去，军官按默认规则学习技能。

## 修改舰船名前缀

`shipNamePrefix` 改为 `GIS`，即 Gear Industry Ship。舰船名在遭遇对话与战斗中都会显示为 "GIS 某某号"，前缀是玩家分辨舰队归属的又一个线索。

```json
"shipNamePrefix":"GIS",
```

舰船名词库、人名、头像、军衔与人物声音继续沿用霸主的配置。它们属于势力设定的一部分，等设定定下来再一并修改。

它已经大致完成了，但还需要一些微小的工作。

## 将装配导入生涯

势力知道了自己有哪些船，却还不知道这些船该怎么装。生涯里的舰队按 "舰船角色" 抽取装配：战斗、航母、相位、货船等各有大中小几档，每档列出若干装配 ID 与权重。

同目录下还有一个 `default_ship_roles.json` 文件，它是用于将装配导入生涯的。如果没有设置这个文件，那么您的势力将无法调用您制作的舰船装配。**打开 `default_ship_roles.json`，参考原版同路径下的文件进行修改。**

先给工匠准备一个装配。在 MOD 的 `data/variants/` 创建 `GearIn_artizan_combat.variant`：

```json
{
    "displayName": "Combat",
    "fluxCapacitors": 0,
    "fluxVents": 14,
    "hullId": "GearIn_artizan",
    "hullMods": ["missleracks", "heavyarmor", "fluxbreakers", "autorepair"],
    "permaMods": [],
    "sMods": [],
    "variantId": "GearIn_artizan_combat",
    "goalVariant": true,
    "weaponGroups": [
        {
            "autofire": false,
            "mode": "LINKED",
            "weapons": {
                "WS0003": "railgun",
                "WS0004": "railgun"
            }
        },
        {
            "autofire": false,
            "mode": "ALTERNATING",
            "weapons": {
                "WS0001": "annihilatorpod",
                "WS0002": "annihilatorpod"
            }
        },
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {
                "WS0005": "vulcan",
                "WS0006": "vulcan"
            }
        }
    ]
}
```

装配里的武器与船插都在上面导入的军备之内。舰队生成时，游戏以角色表里的装配为目标重新装配每条船，势力缺少其中某件武器，就换成它掌握的相近武器。**装配用到的武器，势力自己最好都掌握**，否则生涯里的工匠与您设计的样子会差得很远。`goalVariant` 为 `true` 则让这个装配出现在玩家装配界面的自动装配列表中。

然后在 MOD 的 `data/world/factions/` 创建 `default_ship_roles.json`，把它登记到工匠所属的角色：

```json
{
    "combatMedium": {
        "GearIn_artizan_combat": 5
    }
}
```

工匠是驱逐舰，属于中型，登记在 `combatMedium`。航母角色是 `carrierSmall` 到 `carrierLarge`，相位舰是 `phaseSmall` 到 `phaseCapital`，其余角色名可以对照原版同路径文件。

**这个文件只写 MOD 自己的条目。** 游戏会把它与原版的同名文件合并，没有必要把原版的条目复制一份。角色表是所有势力共用的，但势力只会从自己掌握的船体中挑选，因此工匠的装配只会出现在 `knownShips` 包含工匠的势力舰队里。

看炉人和锻件的装配留到 [C4-S1] 在装配界面中制作，导出后按同样的方式登记到 `carrierMedium` 与 `phaseSmall`。

### 进游戏核对

保存所有文件，启动游戏并读取一个生涯存档。打开控制台，输入 `SpawnFleet gear_industry 60`，在附近生成一支齿轮工业的舰队。靠近舰队查看成员：

- 工匠出现在舰队中，名称以 `GIS` 开头；
- 工匠的装备与 `GearIn_artizan_combat` 大体一致，D 插与少量随机替换属于正常现象；
- 舰队中有原版民用船负责后勤。

多生成几次，舰队构成应大致符合学说的比例。

## 常用 bp 标签速查表

**不在本表上的标签均与势力导入和生成无关。** 标签写在舰船、联队、武器、船插各自 CSV 的 `tags` 列。

| 标签                          | 实际意义                                                                                  |
| ----------------------------- | ----------------------------------------------------------------------------------------- |
| base_bp                       | 玩家初始势力包，绝大多数势力都拥有                                                        |
| lowtech_bp                    | 低科势力包，**不是所有低科武器/舰船都在其中**                                             |
| midline_bp                    | 中科势力包，**不是所有中科武器/舰船都在其中**                                             |
| hightech_bp                   | 高科势力包，**不是所有高科武器/舰船都在其中**                                             |
| missile_bp                    | 导弹武器包                                                                                |
| standard                      | 原版通用船插，只用于 `knownHullMods`                                                      |
| hegemony、tritachyon 等       | 各原版势力的专属包，名称与势力 ID 相近                                                    |
| rare_bp                       | 稀有包，所有势力都默认不拥有它。它不控制势力生成，而是让带有它的组件可在野外打捞或探索得到 |
| restricted                    | 限制级，默认不出现在数据库与绝大多数场合，适合用于隐藏数据                                |
| no_drop、no_drop_salvage      | 不会在战后掉落；不会在残骸中打捞得到                                                      |
| no_sell                       | 不会在市场出售                                                                            |
| no_dealer                     | 不会由联络人任务提供                                                                      |
| hist1t、hist2t、hist3t        | 与历史学家相关，带有它的组件会被历史学家提供蓝图位置，稀有度依次升高                      |

## 常见问题

- **启动时报 `Ship hull variant [...] not found`**：`default_ship_roles.json` 中的装配 ID 不存在。
    - **解决方案**：核对 `.variant` 中的 `variantId` 与角色表中写的是否完全一致，保存后重启。

- **舰队里的船没有任何船插**：`knownHullMods` 的 `tags` 写成了 `base_bp`，或干脆留空。
    - **解决方案**：改为 `"tags":["standard"]`，保存后重启，重新生成舰队。

- **舰队里从不出现工匠**：工匠的船体不在 `knownShips` 中，或它的装配没有登记到角色表。
    - **解决方案**：确认 `ship_data.csv` 中工匠的 `tags` 带有 `GearIn_bp`，并确认 `GearIn_artizan_combat` 已登记在 `combatMedium`。

- **舰队里的船空着很多武器槽**：势力掌握的武器太少，自动装配找不到可替换的武器。
    - **解决方案**：在 `knownWeapons` 中补回原版通用标签或常用武器。
