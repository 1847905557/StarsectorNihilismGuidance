# 配置 MagicLib 数据赏金

本节用 MagicBounty 配置 "断齿航路"：玩家满足条件后，从情报告示板领取歼灭委托，前往目标地点消灭海盗舰队并取得奖励。一份 JSON 同时提供人物、舰队、地点与任务文本，我们沿出现、领取和结算展开，说明哪些字段控制生成，哪些字段控制玩家实际收到的信息。

_本节预期阅读时间：\~32 分钟。_

_本节预期操作时间：\~40 分钟。_

## 学习目标

完成后，您将能够：

- 说明 MagicBounty 如何读取、校验并登记赏金记录；
- 用市场、玩家、Memory 与势力关系限制告示板中的可用任务；
- 配置任务文本、奖励、目标人物、舰队和程序化地点；
- 区分不同任务类型、展示精度和目标舰队行为；
- 根据 `ActiveBounty` 阶段和 Memory 键检查任务结果；
- 从日志定位 JSON、势力、装配与地点配置错误。

## 前置知识

- [C14-S4] 的情报列表、地图位置与结束延迟；
- [C15-S2] 理解 Rules trigger、`FireBest` 与 Memory 槽位；
- [C15-S5] 理解 `MemoryAPI` 的布尔状态与生涯保存；
- [C18-S1] 已经接入 MagicLib 硬依赖与编译路径；
- 能够编辑严格 JSON，并能从 `starsector.log` 定位稳定 ID。

## 创建赏金记录并让库读取

在 MOD 内创建下面的文件，先保存本节完整成品的 `GearIn_magic_bounty` 记录，再按后续字段调整。MagicLib 固定读取该路径：

```text
data/config/modFiles/magicBounty_data.json
```

游戏会从每个已启用 MOD 读取该文件。文件的根对象保存若干赏金，顶层键就是全局赏金 ID：

```json
{
    "GearIn_magic_bounty": {
        "required_mods_id": ["gear_in"]
    }
}
```

`MagicBountyLoader` 先把各 MOD 的根对象合并，再把每个子对象转换成 `MagicBountySpec`。**`GearIn_magic_bounty` 同时标识配置和活动任务。** 活动赏金、完成清单、控制台命令与默认 Memory 键都使用它。不同 MOD 写入相同顶层键时，后读入的数据会替换既有记录，固定前缀可以避免这种不太有礼貌的接管。

`required_mods_id` 接收 MOD ID 数组。数组内每个 MOD 都已启用时，加载器才继续解析这条赏金；缺少任意一项时，记录会被跳过。它适合保护自定义势力、装配和资源引用，但不能替代 `mod_info.json` 中对 MagicLib 的硬依赖声明。

解析完成后，加载器还会校验任务势力、目标势力、舰队组成势力、旗舰装配和预设舰船装配。无法找到必要势力或装配的记录不会进入可用赏金集合。Dev Mode 会在应用加载期间提前执行一次读取，使损坏的 JSON 更早暴露；进入生涯时，MagicLib 再建立赏金协调器、战斗监听器与生涯插件。

生涯加载时，`MagicBountyCoordinator` 登记 `BountyBoardIntelPlugin`，玩家从情报界面的赏金分类打开告示板。`MagicBountyBoardProvider` 将配置和活动赏金交给列表，`MagicBountyInfo` 检查当前条件并显示可领取项。库的 `bounty_board_enabled` 设置为 `true` 时，这套入口随 MagicLib 建立，本节直接提供数据即可。

::: info 示例文件与编辑器解析

游戏的宽松解析器可以读取 MagicLib 模板里的 `#` 注释和尾逗号。本节成品采用严格 JSON，便于同时交给编辑器与普通 JSON 工具处理；复制字段说明时，把注释放在正文便于查阅。

:::

## 决定玩家何时收到委托

发布市场和目标地点各有自己的条件。本例由规模至少为 3 的独立势力市场提供委托；告示板检查玩家周围 10 光年内是否有符合条件的市场，因此玩家在航行中也能收到任务。对象内的局部市场字段如下：

```json
"trigger_market_id": [],
"trigger_marketFaction_any": ["independent"],
"trigger_marketFaction_alliedWith": false,
"trigger_marketFaction_none": [],
"trigger_marketFaction_enemyWith": false,
"trigger_market_minSize": 3
```

`trigger_market_id` 指定市场 ID，当前检查的市场 ID 命中时直接通过。指定 ID 对应的实体存在时，其他市场不再参与；星区中找不到这些 ID 时，继续用势力与规模条件筛选。告示板还要求符合条件的市场处于上述接收范围内。

市场 ID 分支未直接选定时，先检查 `trigger_market_minSize`。`trigger_marketFaction_none` 排除列出的市场势力，`trigger_marketFaction_enemyWith` 允许与其中一个势力达到 `HOSTILE` 或更差关系的市场势力。其余候选再由 `trigger_marketFaction_any` 选择；`trigger_marketFaction_alliedWith` 为 `true` 时，与允许列表中任意势力达到 `WELCOMING` 或更好关系的市场势力也能通过。本例只使用独立势力身份与规模条件。

玩家条件使用另一组字段：

```json
"trigger_player_minLevel": 5,
"trigger_min_fleet_size": 30,
"trigger_min_days_elapsed": 30,
"trigger_weight_mult": 1,
"trigger_playerRelationship_atLeast": {
    "independent": 0
},
"trigger_playerRelationship_atMost": {}
```

玩家达到 5 级、当前舰队至少为 30 FP、开局经过 30 个生涯日，并且与独立势力达到中立或更好关系后，这条记录才成为候选。`trigger_min_fleet_size` 读取当前玩家舰队的 FP，暂存舰船不在这支舰队中，也就不计入阈值。

`trigger_playerRelationship_atLeast` 与 `trigger_playerRelationship_atMost` 先把数值转成 RepLevel，再分别比较关系档位下限和上限。本例的 0 对应 NEUTRAL，按中立或更好的档位通过。两个对象各自任意一项满足即可，所以可以同时要求玩家受到发布方信任、又与目标势力保持敌对。

情报告示板按可用性列出任务。`trigger_weight_mult` 保存在规格中，权重抽取由 `MagicBountyBarEvent` 使用；本页的列表入口按玩家与附近市场条件筛选，市场规模决定是否通过规模阈值。

### 使用 Memory 组合剧情条件

三组 Memory 条件读取 Sector Memory：

```json
"trigger_memKeys_all": {
    "$GearIn_bounty_unlocked": true
},
"trigger_memKeys_any": {},
"trigger_memKeys_none": {
    "$GearIn_bounty_blocked": true
}
```

`trigger_memKeys_all` 要求每个键都存在且等于指定布尔值，`trigger_memKeys_any` 只要求任意一项符合，`trigger_memKeys_none` 则要求没有任何一项符合。当前完整成果保持三组对象为空，让等级、日期和关系直接控制出现；需要把任务接到剧情阶段时，再由前置事件写入解锁键。

规格还保存发布势力与目标势力的关系区间字段，局部写法如下：

```json
"trigger_giverTargetRelationship_atLeast": -1,
"trigger_giverTargetRelationship_atMost": -0.5
```

这两个字段以 `-99` 到 `99` 为默认范围，载入 `MagicBountySpec`。现行情报告示板的可用性判断使用前面的玩家、市场和 Memory 条件。需要让发布方与目标方的关系决定出现时，由前置剧情判断这段关系，再将结果写入 `trigger_memKeys_all` 使用的解锁键。本例直接使用独立势力发布、海盗舰队为目标，并在舰队字段中设为敌对态度。

## 编写告示板文本

告示板先显示 `job_name`。玩家选中该条目后，`job_description` 提供任务正文，`job_pick_option` 提供接受按钮：

```json
"job_name": "断齿航路",
"job_description": "一支海盗舰队正在袭扰独立航运。发布方要求你歼灭 ==整支舰队==，并确认其旗舰不再构成威胁。目标位于 $system 的 $location 附近，旗舰名为 $shipName。\n完成任务后将支付 $reward 信用点。",
"job_pick_option": "接受歼灭委托"
```

字符串中的 `\n` 产生换行，`\"` 写入双引号，`%%` 显示一个百分号。`==内容==` 把内容加入高亮，`^` 会把紧随其后的文本变量首字母转为大写。

MagicBounty 在描述中替换目标与奖励变量。常用变量包括：

- `$system`、`$constellation` 与 `$location`：目标所在星系、星座和邻近实体；
- `$shipName` 与 `$shipFleet`：旗舰名称以及单舰或舰队称呼；
- `$targetFaction`、`$givingFaction` 与 `$rewardFaction`：目标、发布方和奖励方的显示名称；
- `$reward`：文本替换使用的基础信用点奖励；接受界面显示的最终奖励还会应用 `job_reward_scaling` 并按 100 credits 取整；
- `$name`、`$firstName` 与 `$lastName`：目标指挥官姓名；
- `$heSheThey`、`$himHerThem`、`$hisHerTheir` 等人物代词：根据指挥官性别替换。

目标舰队进入通讯时，`job_comm_reply` 替代普通势力应答；字段设为空字符串时，界面显示未回应通讯，省略字段时则采用目标势力的普通通讯应答。

任务结束后，Intel 分别读取：

```json
"job_intel_success": "目标舰队已被歼灭，独立航运恢复了安全。",
"job_intel_failure": "旗舰以不符合委托要求的方式脱离了处置，任务失败。",
"job_intel_expired": "目标舰队已经离开已知活动区域，委托失效。"
```

`job_intel_failure` 主要对应不允许回收旗舰的任务中，玩家回收旗舰导致的失败。`job_intel_expired` 同时承担接受后超过期限以及目标在玩家介入前结束时的结论文字。

## 设置目标类型、期限与奖励

完整成果要求歼灭整支舰队：

```json
"job_forFaction": "independent",
"job_deadline": 90,
"job_credit_reward": 120000,
"job_reward_scaling": 0.5,
"job_reputation_reward": 5,
"job_item_reward": {
    "supplies": 50
},
"job_type": "obliteration"
```

`job_forFaction` 是奖励关系所属势力。成功时，玩家获得 5 点独立势力关系；写 0 可以关闭关系奖励。`job_deadline` 为正数时，提供接受后的生涯日期限；0 或负数表示没有期限。

::: warning 期限还由 MagicLib 设置控制
赏金期限默认关闭。启用 LunaLib 后，在其 MagicLib 设置中开启 "启用赏金期限" (`magiclib_enableBountyDeadlines`)，本例的 90 日才会参与过期判断。接受后的独立任务情报显示剩余日数，验证期限时也从这里读取。
:::

基础信用点先由 `job_credit_reward` 给出。`job_reward_scaling` 大于 0 且 `fleet_min_FP` 大于 0 时，MagicLib 根据玩家舰队相对最低目标规模的增长计算额外奖金；0.5 让规模增长只以一半强度进入奖金。最终奖励按 100 credits 取整，并在接受任务时固定下来。

`job_item_reward` 把奖励加入战后战利品。键可以是商品、武器、联队或船插 ID，也可以采用特殊物品与数据的写法，例如 `ship_bp enforcer`；值是发放数量。本例加入 50 单位 `supplies`，它与信用点、势力关系分别结算。

物品由遭遇战上下文发放：目标旗舰从败方成员中移除后，本次战利品才能得到这些内容，回收旗舰导致失败时取消额外物品。`obliteration` 的信用点结算还要等目标整支舰队消失，因此旗舰先被处理的一场战斗可以先取得物品，任务仍继续等待其他目标。

任务类型决定战斗结果如何结束赏金：

### 任务类型速查表

| `job_type`       | 完成要求                               | 旗舰回收关系   |
| ---------------- | -------------------------------------- | -------------- |
| `assassination`  | 使旗舰失去战斗能力                     | 可以回收       |
| `destruction`    | 摧毁旗舰                               | 回收旗舰会失败 |
| `obliteration`   | 摧毁或瘫痪整支目标舰队                 | 可以回收       |
| `neutralization` | 将剩余 FP 降至初始 FP 的三分之一或以下 | 可以回收       |

`neutralisation` 是英式拼写的兼容值，新数据使用 `neutralization`。当前成果采用 `obliteration`，因此只处理旗舰并不足以完成任务。

### 控制告示板展示

```json
"job_show_type": true,
"job_show_captain": true,
"job_show_fleet": "vanilla",
"job_show_distance": "vanillaDistance",
"job_show_arrow": true,
"job_difficultyDescription": "auto"
```

`job_show_type` 显示任务类型与完成要求。`job_show_captain` 显示舰长头像和技能；关闭后列表使用通用灰色人像。`job_difficultyDescription` 由 `MagicBountyBarEvent` 读取，`auto` 用于生成危险程度、`none` 隐藏该行，其他文本原样显示。当前情报告示板通过 `MagicBountyInfo` 绘制类型、奖励、位置和舰队，不读取这项危险程度文字。

`job_show_fleet` 与 `job_show_distance` 使用枚举字符串控制情报精度：

### 告示板显示速查表

| 字段                | 值                            | 告示板结果                                 |
| ------------------- | ----------------------------- | ------------------------------------------ |
| `job_show_fleet`    | `none`                        | 隐藏舰队规模                               |
| `job_show_fleet`    | `text`                        | 给出粗略数量，达到五艘时另列旗舰           |
| `job_show_fleet`    | `flagship` / `flagshipText`   | 显示旗舰，后者附带其余舰船数量             |
| `job_show_fleet`    | `preset` / `presetText`       | 显示旗舰与预设舰船，后者附带其余舰船数量   |
| `job_show_fleet`    | `vanilla`                     | 显示旗舰和另外至多九艘舰船，并说明其余数量 |
| `job_show_fleet`    | `all`                         | 显示整支舰队                               |
| `job_show_distance` | `none`                        | 隐藏详情中的位置文字                       |
| `job_show_distance` | `vague` / `distance`          | 当前告示板使用环境位置描述                 |
| `job_show_distance` | `system`                      | 显示目标星系名称                           |
| `job_show_distance` | `vanilla` / `vanillaDistance` | 当前告示板使用原版风格环境描述             |
| `job_show_distance` | `exact`                       | 显示目标实体和星系的准确名称               |

表中的距离结果用于领取前的告示板。接受后的 `MagicBountyIntel` 还按各枚举区分模糊距离、光年数、星系和环境描述，并用 `job_show_arrow` 控制主地图箭头。当前领取入口将玩家舰队作为 `bountySource`，箭头便从玩家舰队指向任务位置。

::: info 告示板内嵌地图直接显示目标星系
目标位于恒星系时，告示板详情会绘制该星系及玩家指向它的箭头，位置文字另由 `job_show_distance` 控制。编者建议把这张地图纳入情报精度的设计：若任务意在要求玩家寻找星系，仅把文字改得模糊还不足以实现这个取舍。
:::

## 创建目标人物

目标舰长可以引用已有的重要人物，也可以由数据直接创建。`target_importantPersonId` 填入有效 ID 时，MagicLib 使用该人物；本例创建 Mara Vane，局部字段如下：

```json
"target_first_name": "Mara",
"target_last_name": "Vane",
"target_portrait": "graphics/portraits/portrait_pirate02.png",
"target_gender": "FEMALE",
"target_rank": "spaceCommander",
"target_post": "fleetCommander",
"target_personality": "aggressive"
```

姓名或头像留空时，MagicLib 按目标势力随机生成。`target_portrait` 可以写 `settings.json` 中 `graphics.characters` 的 sprite ID，也可以写完整资源路径。性别接受 `MALE`、`FEMALE` 与 `UNDEFINED`；省略或写其他值时，生成器自行选择。

本例创建 Mara Vane，因此省略 `target_importantPersonId`。引用已有重要人物时，才填写有效 ID；JSON 的 `null` 会被字符串读取入口取成 `"null"`，从而进入重要人物查询。

`target_rank` 与 `target_post` 使用 `Ranks` 中的 ID，分别影响军衔和职位文本。`target_personality` 使用 `timid`、`cautious`、`steady`、`aggressive` 或 `reckless`，并直接影响战斗 AI 行为。

舰长等级与技能使用：

```json
"target_level": 4,
"target_elite_skills": 1,
"target_skill_preference": "ANY",
"target_skills": {
    "combat_endurance": 1,
    "impact_mitigation": 2,
    "missile_specialization": 1
}
```

`target_aiCoreId` 可以写 `gamma_core`、`beta_core` 或 `alpha_core`，生成的指挥官会作为 AI，核心也会进入战利品。`target_level` 控制等级，`target_elite_skills` 覆盖随机精英技能数量；0 表示没有随机精英技能，负数采用默认设置。

`target_skills` 非空时会清除随机技能，再按对象写入指定技能；值 1 表示普通技能，值 2 表示精英技能。当前成果因此会得到三项确定技能。

`target_skill_preference` 的现行加载器只专门识别 `CARRIER` 与 `PHASE`，其余值采用 `GENERIC`。其他武器与防御组合值也进入 `GENERIC` 分支，`ANY` 同样采用这个默认偏好。本例提供显式 `target_skills`，最终技能按指定对象写入。

## 创建目标舰队

目标舰队由势力、旗舰与预设舰船开始：

```json
"fleet_name": "断齿掠袭舰队",
"fleet_faction": "pirates",
"fleet_flagship_variant": "enforcer_d_pirates_Strike",
"fleet_flagship_name": "Broken Promise",
"fleet_flagship_alwaysRecoverable": false,
"fleet_flagship_autofit": false,
"fleet_preset_ships": {
    "lasher_Overdriven": 2,
    "condor_Strike": 1
},
"fleet_preset_autofit": false
```

`fleet_faction` 决定目标舰队在生涯中的势力身份。`fleet_flagship_variant` 可以写一个装配 ID，也可以写装配 ID 数组，让加载器随机选择一项。旗舰字段缺失，或装配既不在游戏设置中、也不在 MagicLib 的可选装配目录中时，整条赏金会被判为无效。

`fleet_flagship_alwaysRecoverable` 强制旗舰进入可回收候选；当前成果关闭它，让原版战后回收规则决定结果。`fleet_flagship_autofit` 为 `false` 时，旗舰保留装配武器，也不会因质量调整自动加入 D-mod 或 S-mod。预设舰船采用同样的 `fleet_preset_autofit` 规则。

第三方 MOD 提供的装配可以放在：

```text
data/config/modFiles/magicBounty_variants/<variantId>.variant
```

这样，相关第三方 MOD 缺失时不会在普通装配加载阶段立即造成错误；赏金仍须在 `required_mods_id` 中声明对应依赖，只有依赖存在时才会尝试生成该目标。

舰队强度与行为继续由以下字段控制：

```json
"fleet_scaling_multiplier": 0.5,
"fleet_min_FP": 80,
"fleet_composition_faction": "pirates",
"fleet_composition_quality": 1,
"fleet_transponder": false,
"fleet_no_retreat": true,
"fleet_behavior": "GUARDED",
"fleet_attitude": "HOSTILE"
```

`fleet_min_FP` 将基础生成规模设为 80 FP。预设舰队不足时，`fleet_composition_faction` 提供补充舰船来源，库将差额交给舰队生成器；最终数量取决于可用舰船与生成结果。`fleet_composition_quality` 为 1 时允许正常质量变化，2 适合需要无 D-mod 舰队的特殊目标。

`fleet_scaling_multiplier` 根据玩家舰队实力提高生成规模，0 表示关闭动态增援。玩家 FP 超过 `fleet_min_FP` 时，超出部分乘以倍率，再加回基础规模；生成目标因此跟随玩家舰队变化。奖励缩放也使用 `fleet_min_FP` 作为比较基准。

`fleet_transponder` 控制应答器，`fleet_no_retreat` 阻止敌方撤退。`fleet_behavior` 接受 `PASSIVE`、`GUARDED`、`AGGRESSIVE` 与 `ROAMING`，分别形成被动环绕、警戒环绕、守卫地点和星系巡逻。`fleet_attitude` 接受 `HOSTILE`、`NEUTRAL` 与 `DEFAULT`；`DEFAULT` 采用目标势力对玩家的实际态度。本例固定 `HOSTILE`，确保玩家到达后能够进入目标战斗。

`fleet_musicSetId` 可以指定战斗音乐集合 ID，该 ID 还要存在于 `sounds.json` 的音乐数据中。采用默认音乐时省略字段，字符串读取入口会把 JSON 的 `null` 取成 `"null"` 并尝试播放这个集合。本例的人类舰长也省略可选的 `target_aiCoreId`。

## 选择目标地点

固定实体、市场势力和程序化地点具有明确优先级。`location_entitiesID` 首先尝试直接按 `SectorEntityToken` ID 定位目标；找不到固定实体时，才继续检查其余选择：

```json
"location_entitiesID": [],
"location_marketFactions": [],
"location_distance": "CLOSE",
"location_themes": [
    "procgen_no_theme",
    "theme_interesting_minor"
],
"location_themes_blacklist": [
    "theme_already_occupied",
    "theme_already_colonized",
    "theme_hidden",
    "no_pulsar_blackhole"
],
"location_entities": [
    "planet",
    "gas_giant",
    "jump_point",
    "gate",
    "station"
],
"location_prioritizeUnexplored": true,
"location_defaultToAnyEntity": true
```

`location_marketFactions` 指定一个或多个市场势力。找到对应市场后，它会优先于距离、theme 和实体类型。当前成果保持两组固定来源为空，让它兼容普通星区与随机星区。

`location_distance` 接受 `CORE`、`CLOSE` 与 `FAR`，也可以留空忽略距离偏好。这些范围按距星区中心的距离划分，再按偏好顺序搜索；它们与发布市场的 10 光年接收范围分别计算。`location_themes` 选择星系主题，`location_themes_blacklist` 排除已占用、已殖民、隐藏或具有脉冲星与黑洞风险的地点。同一 theme 同时出现在允许列表与黑名单时，允许列表优先。

`location_entities` 决定舰队最终环绕哪类实体。常用值包括 `planet`、`gas_giant`、`jump_point`、`gate`、`station`、`wreck`、`debris`、`stable_location`、`comm_relay`、`nav_buoy` 与 `sensor_array`。

`location_prioritizeUnexplored` 在当前距离分组中先尝试未访问星系，再尝试已访问星系。缺少指定实体时，`location_defaultToAnyEntity` 为 `true` 会在当前分组选择任意实体；为 `false` 时继续搜索下一距离分组。允许任意实体且没有符合 theme 的星系时，候选也会扩大后再应用黑名单。全部搜索失败且黑名单含 `theme_hidden` 时，库还会移除这一项再搜索一次，因此固定隐藏地点应由有效实体 ID 明确指定。

## 追踪 ActiveBounty 与 Memory

告示板筛选通过时，`MagicBountyCoordinator` 为记录创建 `ActiveBounty`，准备目标人物、舰队与地点，阶段为 `NotAccepted`。玩家按下接受按钮后进入 `Accepted`：目标舰队放入地点，独立任务 Intel 登记，接受时间、来源玩家舰队与奖励数值一并保存。

正向状态如下：

```text
NotAccepted
→ Accepted
→ Succeeded
```

`job_memKey` 把结果写入 Sector Memory：

```json
"job_memKey": "$GearIn_magic_bounty"
```

**接受与结算分别留下可读取的任务状态。** 接受时，`$GearIn_magic_bounty` 被写为 `false`。任务成功后，它变为 `true`，同时写入 `$GearIn_magic_bounty_succeeded = true`。后续 Rules 据此响应结局，加载器也用基础键识别已经领取的记录。

其他结束阶段分别表示：

- `FailedSalvagedFlagship`：任务要求摧毁旗舰，但玩家回收了它；基础键与 `_failed` 后缀写为 `true`；
- `ExpiredAfterAccepting`：玩家接受任务后超过 `job_deadline`；基础键与 `_expired` 后缀写为 `true`；
- `Dismissed`：对尚未接受的活动对象调用永久忽略结果，该赏金退出候选；
- `ExpiredWithoutAccepting`：开启期限后，活动对象生成至今超过未接受期限；对象清理后可以重新生成；
- `EndedWithoutPlayerInvolvement`：其他舰队或外部事件先解决了目标；基础键与 `_expired` 后缀写为 `true`。

未接受期限使用 `bounty_boardRefreshTimePerMarketInDays`，默认 90 日，从活动对象创建时算起。接受后重新使用 `job_deadline` 与接受时间判断过期。两种计时都受 "启用赏金期限" 设置控制，活动对象也按当前阶段选择相应期限。

完成、失败或过期只会结算一次。结束后的 Intel 先显示对应结论，再在自身结束延迟到达后退出活动列表；coordinator 随后把赏金 ID 放入已完成清单。

## 接入 Rules trigger

`job_pick_script` 与 `job_conclusion_script` 保存的是 Rules trigger，而不是 Java 类名：

```json
"job_pick_script": "GearInMagicBountyAccepted",
"job_conclusion_script": "GearInMagicBountyConcluded"
```

接受任务并写入基础 Memory 后，MagicLib 对 `job_pick_script` 调用 `FireBest`。结算分支写完 `_succeeded`、`_failed` 或 `_expired` 后，再触发 `job_conclusion_script`。当前存在交互对话时，trigger 使用该 dialog 与插件提供的 `memoryMap`；没有对话时，MagicLib 只提供 LOCAL Memory 后执行规则。下列 `$global` 条件用于已有交互对话且提供 GLOBAL 槽位的场景；无对话结算中，需要由 Rules 命令从 `Global.getSector().getMemoryWithoutUpdate()` 读取结局，并采用适合无 dialog 的动作。

对应规则可以直接读取结局键：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_magic_bounty_success,GearInMagicBountyConcluded,$global.GearIn_magic_bounty_succeeded,,,,任务成功后的附加规则
GearIn_magic_bounty_expired,GearInMagicBountyConcluded,$global.GearIn_magic_bounty_expired,,,,任务过期后的附加规则
```

这两条是条件匹配的局部规则，说明写在 notes，实际附加动作填写 script。完整赏金省略两个脚本字段，先由 JSON 完成接受与结算。需要追加奖励、解锁市场或推进剧情时，再把 trigger 和对应规则同时加入 MOD。

## 使用既有舰队

`existing_target_memkey` 把赏金目标改为一支已经存在的生涯舰队：

```json
"existing_target_memkey": "$GearIn_existing_bounty_target"
```

MagicLib 会扫描各星系舰队，寻找自身 Memory 含有该键的对象。找到后，这支舰队提供目标、当前位置和舰队组成，JSON 中随后那些人物、舰队与地点生成字段不再负责创建新目标。

这条创建路径需要舰队已经存在、带有标记并具有当前 assignment，目标位置从该 assignment 取得。它使世界生成的固定敌人能够交给活动赏金处理 Intel、期限与结算。

::: warning 既有舰队字段需要专门接入
当前 `MagicBountyInfo.shouldShow()` 在找到该标记舰队时会过滤候选，`createActiveBounty(...)` 又需要找到它才能建立活动对象。因此现行情报告示板不能单靠这个字段领取既有舰队任务。此类功能需要自有接受入口调用协调器和 `acceptBounty(...)`；本节完整数据采用生成新舰队的路径。
:::

## 字段兼容速查表

| 字段或值                      | 当前含义                                     | 新数据写法                                         |
| ----------------------------- | -------------------------------------------- | -------------------------------------------------- |
| `fleet_flagship_recoverable`  | 旗舰强制可回收的兼容字段                     | `fleet_flagship_alwaysRecoverable`                 |
| `fleet_min_DP`                | 最低舰队规模的兼容备用值                     | `fleet_min_FP`                                     |
| `location_marketIDs`          | 固定 `SectorEntityToken` ID 列表的可识别别名 | `location_entitiesID`                              |
| `neutralisation`              | `neutralization` 的拼写兼容值                | `neutralization`                                   |
| `location_defaultToAnySystem` | 当前加载器不会把该值送入 `MagicBountySpec`   | 使用 `location_defaultToAnyEntity` 与 theme 黑名单 |

## 完整最小例

在 GearIn MOD 中创建 `data/config/modFiles/magicBounty_data.json`。这条记录使用原版势力、头像和装配，满足条件后出现在情报告示板：

```json
{
    "GearIn_magic_bounty": {
        "required_mods_id": [
            "gear_in"
        ],
        "trigger_market_id": [],
        "trigger_marketFaction_any": [
            "independent"
        ],
        "trigger_marketFaction_alliedWith": false,
        "trigger_marketFaction_none": [],
        "trigger_marketFaction_enemyWith": false,
        "trigger_market_minSize": 3,
        "trigger_player_minLevel": 5,
        "trigger_min_fleet_size": 30,
        "trigger_min_days_elapsed": 30,
        "trigger_weight_mult": 1,
        "trigger_memKeys_all": {},
        "trigger_memKeys_any": {},
        "trigger_memKeys_none": {},
        "trigger_playerRelationship_atLeast": {
            "independent": 0
        },
        "trigger_playerRelationship_atMost": {},
        "job_name": "断齿航路",
        "job_description": "一支海盗舰队正在袭扰独立航运。发布方要求你歼灭 ==整支舰队==，并确认其旗舰不再构成威胁。目标位于 $system 的 $location 附近，旗舰名为 $shipName。\n完成任务后将支付 $reward 信用点。",
        "job_comm_reply": "你来得正好。我们正缺一艘可以挂在战利品清单上的船。",
        "job_intel_success": "目标舰队已被歼灭，独立航运恢复了安全。",
        "job_intel_failure": "旗舰以不符合委托要求的方式脱离了处置，任务失败。",
        "job_intel_expired": "目标舰队已经离开已知活动区域，委托失效。",
        "job_forFaction": "independent",
        "job_deadline": 90,
        "job_credit_reward": 120000,
        "job_reward_scaling": 0.5,
        "job_reputation_reward": 5,
        "job_item_reward": {
            "supplies": 50
        },
        "job_type": "obliteration",
        "job_show_type": true,
        "job_show_captain": true,
        "job_show_fleet": "vanilla",
        "job_show_distance": "vanillaDistance",
        "job_show_arrow": true,
        "job_difficultyDescription": "auto",
        "job_pick_option": "接受歼灭委托",
        "job_memKey": "$GearIn_magic_bounty",
        "target_first_name": "Mara",
        "target_last_name": "Vane",
        "target_portrait": "graphics/portraits/portrait_pirate02.png",
        "target_gender": "FEMALE",
        "target_rank": "spaceCommander",
        "target_post": "fleetCommander",
        "target_personality": "aggressive",
        "target_level": 4,
        "target_elite_skills": 1,
        "target_skill_preference": "ANY",
        "target_skills": {
            "combat_endurance": 1,
            "impact_mitigation": 2,
            "missile_specialization": 1
        },
        "fleet_name": "断齿掠袭舰队",
        "fleet_faction": "pirates",
        "fleet_flagship_variant": "enforcer_d_pirates_Strike",
        "fleet_flagship_name": "Broken Promise",
        "fleet_flagship_alwaysRecoverable": false,
        "fleet_flagship_autofit": false,
        "fleet_preset_ships": {
            "lasher_Overdriven": 2,
            "condor_Strike": 1
        },
        "fleet_preset_autofit": false,
        "fleet_scaling_multiplier": 0.5,
        "fleet_min_FP": 80,
        "fleet_composition_faction": "pirates",
        "fleet_composition_quality": 1,
        "fleet_transponder": false,
        "fleet_no_retreat": true,
        "fleet_behavior": "GUARDED",
        "fleet_attitude": "HOSTILE",
        "location_entitiesID": [],
        "location_marketFactions": [],
        "location_distance": "CLOSE",
        "location_themes": [
            "procgen_no_theme",
            "theme_interesting_minor"
        ],
        "location_themes_blacklist": [
            "theme_already_occupied",
            "theme_already_colonized",
            "theme_hidden",
            "no_pulsar_blackhole"
        ],
        "location_entities": [
            "planet",
            "gas_giant",
            "jump_point",
            "gate",
            "station"
        ],
        "location_prioritizeUnexplored": true,
        "location_defaultToAnyEntity": true
    }
}
```

## 验证方案

前置数据：启用 Console Commands 和 LunaLib，在 LunaLib 的 MagicLib 设置中开启 "启用赏金期限"；准备接受前的测试存档，能够推进至少 90 个生涯日。MagicLib 的 `bounty_board_enabled` 为 `true`。

1. 保存赏金文件，开启 Dev Mode，重启游戏并检查 `starsector.log` 中 `GearIn_magic_bounty : VALID`；在控制台执行 `MagicLib_ListBounties GearIn_magic_bounty` 确认已载入记录。
2. 在玩家低于 5 级、开局不足 30 日或舰队不足 30 FP 时，打开情报界面赏金分类中的告示板，确认该任务尚未出现。
3. 满足等级、日期、舰队和关系条件，前往规模至少为 3 的独立势力市场周围 10 光年内，再打开情报告示板。
4. 选择 "断齿航路"，核对 Mara Vane、任务类型、舰队预览、位置文字、内嵌地图、奖励与接受按钮。
5. 接受委托，打开新增的任务 Intel，确认 90 日剩余期限与地图箭头；在控制台执行 `runcode org.lazywizard.console.Console.showMessage(String.valueOf(com.fs.starfarer.api.Global.getSector().getMemoryWithoutUpdate().get("$GearIn_magic_bounty")));`，确认值为 `false`。
6. 前往 Intel 标记地点，核对旗舰、两艘鞭挞者、一艘秃鹫号、补充舰船和敌对行为。
7. 歼灭整支舰队，检查信用点、独立势力关系、50 单位补给、成功文本和 `_succeeded` Memory 键。
8. 载入接受前的测试存档，再次领取任务并等待超过 90 日，检查过期文本和 `_expired` Memory 键。
9. 载入接受前的测试存档，打开告示板并选中任务，保持尚未接受；在控制台执行 `runcode org.magiclib.bounty.MagicBountyCoordinator.getInstance().getActiveBounty("GearIn_magic_bounty").endBounty(new org.magiclib.bounty.ActiveBounty.BountyResult.DismissedPermanently());`，重新打开告示板，确认该任务退出候选。
10. 载入接受前的测试存档，打开告示板让任务生成，保持尚未接受，推进超过默认的 90 日，再打开告示板，确认旧活动对象过期清理后可以重新生成。
11. 分别把 `fleet_faction` 与旗舰装配改成不存在的 ID，重启游戏并记录对应校验日志，然后恢复有效值。
12. 破坏 JSON 逗号或引号，重启游戏并记录加载错误，然后恢复严格 JSON。

预期结果：

- 有效数据在日志中完成读取与校验，告示板只在全部市场和玩家条件成立时提供任务；
- 告示板显示 Mara Vane、舰队歼灭目标、原版风格舰队预览、环境位置文字与目标星系地图；
- 接受后生成目标舰队和 Intel，基础 Memory 键为 `false`；
- 目标舰队包含指定旗舰与三艘预设舰船，并按 80 FP 基础规模与玩家舰队缩放请求补充舰船；
- 成功后基础 Memory 键与 `_succeeded` 为 `true`，玩家获得信用点、关系与补给奖励；
- 开启期限后，接受超过 90 日进入过期状态并写入 `_expired`；
- 显式永久忽略后任务退出候选，未接受而自然过期的活动对象可以再次生成；
- 无效势力或装配会使记录退出可用赏金集合，损坏 JSON 会产生明确的加载错误。

## 故障分析

- **无对话结算时附加规则没有命中**：调用只提供 LOCAL Memory，`$global` 条件没有对应的 GLOBAL 槽位。
    - **解决方案**：在 Rules 命令中从 `Global.getSector().getMemoryWithoutUpdate()` 读取结果，并让该命令处理无 dialog 的结算动作。

- **情报告示板没有目标任务**：记录没有通过附近市场、玩家或依赖条件，或者校验阶段已经移除了无效赏金。
    - **解决方案**：确认 `GearIn_magic_bounty : VALID`，核对玩家周围 10 光年内的市场势力与规模，以及等级、日期、舰队 FP、关系和 Memory 条件。

- **90 日后任务仍在运行**：MagicLib 的赏金期限设置保持关闭。
    - **解决方案**：启用 LunaLib，在其 MagicLib 设置中开启 "启用赏金期限"，重新进入生涯，并从接受后的任务 Intel 读取剩余时间。

- **日志提示旗舰或预设装配缺失**：字段使用了 hull ID、文件名或不存在的 variant ID。
    - **解决方案**：把 `fleet_flagship_variant` 和 `fleet_preset_ships` 的键改成已登记的装配 ID；可选第三方装配放入 MagicBounty 专用装配目录并声明依赖 MOD。

- **任务出现了，但找不到目标地点**：允许的 theme、黑名单、距离与实体类型没有产生共同候选。
    - **解决方案**：保留至少一种可用 theme 和实体类型，并开启 `location_defaultToAnyEntity`；固定地点任务直接使用有效的 `location_entitiesID`。

- **任务完成后没有额外 Rules 行为**：`job_pick_script` 或 `job_conclusion_script` 写成了 Java 类名，或者同名 trigger 没有匹配规则。
    - **解决方案**：字段填写 Rules trigger，并为该 trigger 添加能够由 `FireBest` 命中的规则。

- **信用点奖励没有随舰队规模变化**：`job_reward_scaling` 或 `fleet_min_FP` 没有正值，缩放计算保持基础奖励。
    - **解决方案**：同时设置正数缩放倍率与最低 FP，并用不同规模的玩家舰队比较接受界面中的最终奖励。

## 总结

"断齿航路" 按玩家和附近发布市场的条件出现在情报告示板，活动对象准备人物、舰队与地点，接受后投放目标并建立任务 Intel。战斗监听器按任务类型结算，Memory 保存结果，后续剧情就能据此继续。

排错时应沿相同顺序检查：先确认 JSON 被读取并通过 ID 校验，再确认市场与玩家条件，最后检查地点和结算状态。告示板没有出现通常发生在前两处，接受后找不到目标则应从舰队或地点配置继续追查。

## 建议参考项

- `data/config/modFiles/magicBounty_data_example.json`：MagicBounty 字段说明与文本变量示例；
- `org.magiclib.bounty.MagicBountyLoader`：读取、解析和校验赏金记录；
- `org.magiclib.bounty.MagicBountySpec`：赏金字段在运行时的数据对象；
- `org.magiclib.bounty.intel.BountyBoardIntelPlugin`：情报告示板与可用任务通知；
- `org.magiclib.bounty.intel.MagicBountyInfo`：玩家与附近市场筛选，以及领取前的详情绘制；
- `org.magiclib.bounty.MagicBountyCoordinator`：创建目标、登记告示板并维护活动赏金；
- `org.magiclib.bounty.ActiveBounty`：接受、期限、Intel、Memory 与结算阶段；
- `Starsector/starsector-core/starsector.log`：定位 JSON、势力、装配与地点错误。
