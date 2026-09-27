# 配置 MagicLib 数据赏金

MagicBounty 可以从一份 JSON 记录建立酒吧告示、目标舰队、限时 Intel 和战斗结算。本节用原版舰船与头像配置一项舰队歼灭委托，读者能够沿同一个赏金 ID 检查加载、出现、接受、追踪和结束状态。

*本节预期阅读时间：~32 分钟。*

*本节预期操作时间：~40 分钟。*

## 学习目标

完成后，您将能够：

- 说明 MagicBounty 如何读取、校验并登记赏金记录；
- 用市场、玩家、Memory 与势力关系限制告示板中的可用任务；
- 配置任务文本、奖励、目标人物、舰队和程序化地点；
- 区分不同任务类型、展示精度和目标舰队行为；
- 根据 `ActiveBounty` 阶段和 Memory 键检查任务结果；
- 从日志定位 JSON、势力、装配与地点配置错误。

## 前置知识

- [C15-S3] 理解酒吧事件如何向市场酒吧添加选项；
- [C16-S2] 理解 Rules trigger、`FireBest` 与 Memory 槽位；
- [C16-S5] 理解 `MemoryAPI` 的布尔状态与生涯保存；
- [C18-S1] 已经接入 MagicLib 硬依赖与编译路径；
- 能够编辑严格 JSON，并能从 `starsector.log` 定位稳定 ID。

## 加载赏金数据

MagicLib 固定读取以下路径：

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

`MagicBountyLoader` 先把各 MOD 的根对象合并，再把每个子对象转换成 `MagicBountySpec`。因此 `GearIn_magic_bounty` 不只是便于阅读的名称；它还是活动赏金、完成清单、控制台命令和默认 Memory 键共同使用的身份。不同 MOD 写入相同顶层键时，后读入的数据会替换合并对象中的既有记录，固定前缀可以避免这种不太有礼貌的接管。

`required_mods_id` 接收 MOD ID 数组。数组内每个 MOD 都已启用时，加载器才继续解析这条赏金；缺少任意一项时，记录会被跳过。它适合保护自定义势力、装配和资源引用，但不能替代 `mod_info.json` 中对 MagicLib 的硬依赖声明。

解析完成后，加载器还会校验任务势力、目标势力、舰队组成势力、旗舰装配和预设舰船装配。无法找到必要势力或装配的记录不会进入可用赏金集合。Dev Mode 会在应用加载期间提前执行一次读取，使损坏的 JSON 更早暴露；进入生涯时，MagicLib 再建立赏金协调器、战斗监听器与生涯插件。

MagicLib 已经在自己的 `rules.csv` 中把告示板加入 `AddBarEvents`。市场存在可用赏金时，酒吧出现 "接入当地非正式的赏金告示板。" 选项；选择后由 `ShowMagicBountyBoardCmd` 打开告示板。因此这份数据不需要再配一套 `BaseBarEventCreator`。

::: warning JSON 注释只属于 MagicLib 示例文件

MagicLib 随附的说明模板使用 `#` 注释和尾随逗号，游戏的宽松解析器能够读取它们。本页完整成果使用严格 JSON，便于交给编辑器、格式检查器和其他 JSON 工具共同检查。

:::

## 限定酒吧与玩家条件

一项赏金先通过市场条件，再通过玩家条件。当前成果只允许它出现在规模至少为 3 的独立势力市场：

```json
"trigger_market_id": [],
"trigger_marketFaction_any": ["independent"],
"trigger_marketFaction_alliedWith": false,
"trigger_marketFaction_none": [],
"trigger_marketFaction_enemyWith": false,
"trigger_market_minSize": 3
```

`trigger_market_id` 指定市场 ID。指定市场存在时，它优先于其余市场条件，使赏金固定出现在该市场；采用随机星区时，找不到该 ID 才继续使用势力与规模条件。

`trigger_marketFaction_any` 允许列表内势力的市场。`trigger_marketFaction_alliedWith` 为 `true` 时，与列表中任意势力关系至少达到 `WELCOMING` 的市场势力也可以发布任务。`trigger_marketFaction_none` 列出的市场势力始终被排除；启用 `trigger_marketFaction_enemyWith` 后，还会接受与排除列表中任意一个势力处于 `HOSTILE` 的市场势力。市场规模最后由 `trigger_market_minSize` 检查。

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

玩家达到 5 级、舰队实力至少为 30 FP、开局经过 30 个游戏日，并且与独立势力关系不低于 0 后，这条记录才成为候选。`trigger_min_fleet_size` 检查玩家舰队的舰队点数合计；它用于避免把舰船暂存起来后，以一支临时缩小的舰队领取低强度任务。

`trigger_playerRelationship_atLeast` 与 `trigger_playerRelationship_atMost` 分别设置关系下限和上限。两个对象各自任意一项满足即可，所以可以同时要求玩家受到发布方信任、又与目标势力保持敌对。

`trigger_weight_mult` 只在符合条件的赏金竞争有限告示板栏位时生效。市场规模 3 时，MagicLib 默认提供一个栏位；市场每增加一级，栏位也增加一个。候选权重相加后随机抽取，倍率越大，被选中的机会越高。

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

发布势力与目标势力之间还可以设置关系区间：

```json
"trigger_giverTargetRelationship_atLeast": -1,
"trigger_giverTargetRelationship_atMost": -0.5
```

这两个字段检查 `job_forFaction` 与 `fleet_faction` 之间的关系。它们适合要求双方至少敌对，避免友好势力发布一项逻辑上莫名其妙的互相歼灭委托。加载器的开放默认范围为 `-99` 到 `99`；当前成果省略两项，使用实际势力关系与敌对舰队态度。

## 编写告示板文本

告示板先显示 `job_name`。读者选中该条目后，`job_description` 提供任务正文，`job_pick_option` 提供接受按钮：

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

`job_forFaction` 是奖励关系所属势力。成功时，玩家获得 5 点独立势力关系；写 0 可以关闭关系奖励。`job_deadline` 从接受任务时开始计算，正数表示游戏日数，0 或负数表示没有期限。

基础信用点先由 `job_credit_reward` 给出。`job_reward_scaling` 大于 0 且 `fleet_min_FP` 大于 0 时，MagicLib 根据玩家舰队相对最低目标规模的增长计算额外奖金；0.5 让规模增长只以一半强度进入奖金。最终奖励按 100 credits 取整，并在接受任务时固定下来。

`job_item_reward` 把奖励加入战后战利品。键可以是商品 ID，也可以采用 Console Commands 的特殊物品写法，例如 `ship_bp enforcer`；值是发放数量。本例加入 50 单位 `supplies`，它与信用点、势力关系分别结算。

任务类型决定战斗结果如何结束赏金：

### 任务类型速查表

| `job_type` | 完成要求 | 旗舰回收关系 |
|---|---|---|
| `assassination` | 使旗舰失去战斗能力 | 可以回收 |
| `destruction` | 摧毁旗舰 | 回收旗舰会失败 |
| `obliteration` | 摧毁或瘫痪整支目标舰队 | 可以回收 |
| `neutralization` | 摧毁或瘫痪目标舰队的三分之二 | 可以回收 |

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

`job_show_type` 显示任务的完成要求。`job_show_captain` 显示舰长头像；关闭后，告示板显示通用灰色人像。`job_difficultyDescription` 写 `auto` 时由 MagicLib 根据目标舰队生成危险程度，写 `none` 时隐藏该行，其他文本会原样显示。

`job_show_fleet` 与 `job_show_distance` 使用枚举字符串控制情报精度：

### 告示板显示速查表

| 字段 | 值 | 告示板结果 |
|---|---|---|
| `job_show_fleet` | `none` | 隐藏舰队规模 |
| `job_show_fleet` | `text` | 只显示舰船数量描述 |
| `job_show_fleet` | `flagship` / `flagshipText` | 显示旗舰，后者附带其余舰船数量 |
| `job_show_fleet` | `preset` / `presetText` | 显示旗舰与预设舰船，后者附带其余舰船数量 |
| `job_show_fleet` | `vanilla` | 显示旗舰和最大的九艘舰船，并说明其余数量 |
| `job_show_fleet` | `all` | 显示整支舰队 |
| `job_show_distance` | `none` | 隐藏位置距离 |
| `job_show_distance` | `vague` | 只给出核心区附近等模糊描述 |
| `job_show_distance` | `distance` | 显示距玩家的大致光年数 |
| `job_show_distance` | `system` | 显示目标星系名称 |
| `job_show_distance` | `vanilla` / `vanillaDistance` | 显示原版风格环境描述，后者再附加光年数 |
| `job_show_distance` | `exact` | 显示目标实体和星系的准确名称 |

`job_show_arrow` 在 Intel 地图上绘制从接受地点指向目标地点的箭头。精确位置已经直接显示时，箭头只是视觉辅助；模糊任务大量使用它，会让所谓模糊显得颇有行政手续感。

## 创建目标人物

目标舰长可以引用已有的重要人物，也可以由数据直接创建。使用 `target_importantPersonId` 时，MagicLib 取得该人物并忽略其余人物生成字段：

```json
"target_importantPersonId": null,
"target_first_name": "Mara",
"target_last_name": "Vane",
"target_portrait": "graphics/portraits/portrait_pirate02.png",
"target_gender": "FEMALE",
"target_rank": "spaceCommander",
"target_post": "fleetCommander",
"target_personality": "aggressive"
```

姓名或头像留空时，MagicLib 按目标势力随机生成。`target_portrait` 可以写 `settings.json` 中 `graphics.characters` 的 sprite ID，也可以写完整资源路径。性别接受 `MALE`、`FEMALE` 与 `UNDEFINED`；省略或写其他值时，生成器自行选择。

`target_rank` 与 `target_post` 使用 `Ranks` 中的 ID，分别影响军衔和职位文本。`target_personality` 使用 `timid`、`cautious`、`steady`、`aggressive` 或 `reckless`，并直接影响战斗 AI 行为。

舰长等级与技能使用：

```json
"target_aiCoreId": null,
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

`target_skill_preference` 的现行加载器只专门识别 `CARRIER` 与 `PHASE`，其余值采用 `GENERIC`。MagicLib 随附模板列出的旧式武器与防御组合不会进入当前 `SkillPickPreference` 分支；新数据只使用 `ANY` 也不会产生额外偏好，显式 `target_skills` 仍然决定本例技能。

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
"fleet_attitude": "HOSTILE",
"fleet_musicSetId": null
```

`fleet_min_FP` 保证目标至少达到 80 FP。预设舰队不足时，`fleet_composition_faction` 提供补充舰船来源；`fleet_composition_quality` 为 1 时允许正常质量变化，2 适合需要无 D-mod 舰队的特殊目标。

`fleet_scaling_multiplier` 根据玩家舰队实力继续增加目标舰船，0 表示关闭动态增援。缩放与最低 FP 同时存在时，最低值先保证基础威胁，倍率再响应玩家舰队规模；这也为奖励缩放提供计算基准。

`fleet_transponder` 控制应答器，`fleet_no_retreat` 阻止敌方撤退。`fleet_behavior` 接受 `PASSIVE`、`GUARDED`、`AGGRESSIVE` 与 `ROAMING`，分别形成被动环绕、警戒环绕、守卫地点和星系巡逻。`fleet_attitude` 接受 `HOSTILE`、`NEUTRAL` 与 `DEFAULT`；`DEFAULT` 采用目标势力对玩家的实际态度。本例固定 `HOSTILE`，确保玩家到达后能够进入目标战斗。

`fleet_musicSetId` 可以指定战斗音乐集合 ID。该 ID 还要存在于 `sounds.json` 的音乐数据中；当前成果保持 `null`，使用目标势力或游戏默认音乐。

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

`location_distance` 接受 `CORE`、`CLOSE` 与 `FAR`，也可以留空忽略距离偏好。`location_themes` 决定允许的星系主题；`location_themes_blacklist` 排除已经占用、已经殖民、隐藏或具有脉冲星与黑洞风险的地点。同一 theme 同时出现在允许列表与黑名单时，允许列表优先。

`location_entities` 决定舰队最终环绕哪类实体。常用值包括 `planet`、`gas_giant`、`jump_point`、`gate`、`station`、`wreck`、`debris`、`stable_location`、`comm_relay`、`nav_buoy` 与 `sensor_array`。

`location_prioritizeUnexplored` 优先选择玩家尚未访问的星系，但不会越过距离要求。找不到同时满足实体类型、theme 和距离的目标时，`location_defaultToAnyEntity` 为 `true` 会改选非黑名单星系中的任意实体；为 `false` 时则放宽距离并继续寻找合适实体。

## 追踪 ActiveBounty 与 Memory

候选记录进入告示板后，MagicBountyCoordinator 可以先为它创建 `ActiveBounty`，此时阶段是 `NotAccepted`。玩家按下接受选项后，赏金进入 `Accepted`：目标舰队被放入选定地点，Intel 加入情报界面，接受市场和奖励数值一并保存。

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

接受时，`$GearIn_magic_bounty` 被写为 `false`。任务成功后，它变为 `true`，同时写入 `$GearIn_magic_bounty_succeeded = true`。这些键既能让后续 Rules 响应结局，也能防止加载新数据时把已领取赏金当成一条全新记录。

其他结束阶段分别表示：

- `FailedSalvagedFlagship`：任务要求摧毁旗舰，但玩家回收了它；基础键与 `_failed` 后缀写为 `true`；
- `ExpiredAfterAccepting`：玩家接受任务后超过 `job_deadline`；基础键与 `_expired` 后缀写为 `true`；
- `Dismissed`：玩家在告示板永久忽略尚未接受的任务；该赏金不再提供；
- `ExpiredWithoutAccepting`：已生成的告示在市场刷新周期内无人接受；活动对象结束后可以重新生成；
- `EndedWithoutPlayerInvolvement`：其他舰队或外部事件先解决了目标；基础键与 `_expired` 后缀写为 `true`。

告示板刷新周期与接受后的任务期限是两个时间。前者控制尚未领取的候选何时让出市场栏位，后者由 `job_deadline` 控制玩家已经领取的 Intel 何时失败。

完成、失败或过期只会结算一次。结束后的 Intel 先显示对应结论，再在自身结束延迟到达后退出活动列表；coordinator 随后把赏金 ID 放入已完成清单。

## 接入 Rules trigger

`job_pick_script` 与 `job_conclusion_script` 保存的是 Rules trigger，而不是 Java 类名：

```json
"job_pick_script": "GearInMagicBountyAccepted",
"job_conclusion_script": "GearInMagicBountyConcluded"
```

接受任务并写入基础 Memory 后，MagicLib 对 `job_pick_script` 调用 `FireBest`。结算分支写完 `_succeeded`、`_failed` 或 `_expired` 后，再触发 `job_conclusion_script`。当前存在交互对话时，trigger 共用该 dialog 与插件提供的 `memoryMap`；没有对话时，MagicLib 建立最小的 LOCAL Memory 后执行规则。

对应规则可以直接读取结局键：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_magic_bounty_success,GearInMagicBountyConcluded,$global.GearIn_magic_bounty_succeeded,,,任务成功后的附加规则,
GearIn_magic_bounty_expired,GearInMagicBountyConcluded,$global.GearIn_magic_bounty_expired,,,任务过期后的附加规则,
```

完整成果省略两个脚本字段，使任务只依赖 JSON 自身完成接受与结算。需要追加奖励、解锁市场或推进剧情时，再把 trigger 和对应规则同时加入 MOD。

## 使用既有舰队

`existing_target_memkey` 把赏金目标改为一支已经存在的生涯舰队：

```json
"existing_target_memkey": "$GearIn_existing_bounty_target"
```

MagicLib 会扫描各星系舰队，寻找自身 Memory 含有该键的对象。找到后，这支舰队提供目标、当前位置和舰队组成，JSON 中随后那些人物、舰队与地点生成字段不再负责创建新目标。

该舰队必须在赏金进入告示板前已经存在并带有标记；找不到匹配舰队时，任务不会成为可用候选。这个入口适合把世界生成脚本中的固定敌人交给 MagicBounty 管理 Intel、期限和结算。

## 字段兼容速查表

| 字段或值 | 当前含义 | 新数据写法 |
|---|---|---|
| `fleet_flagship_recoverable` | 旗舰强制可回收的兼容字段 | `fleet_flagship_alwaysRecoverable` |
| `fleet_min_DP` | 最低舰队规模的兼容备用值 | `fleet_min_FP` |
| `location_marketIDs` | 固定 `SectorEntityToken` ID 列表的历史名称 | `location_entitiesID` |
| `neutralisation` | `neutralization` 的拼写兼容值 | `neutralization` |
| `location_defaultToAnySystem` | 当前加载器不会把该值送入 `MagicBountySpec` | 使用 `location_defaultToAnyEntity` 与 theme 黑名单 |

## 完整最小例

在 GearIn MOD 中创建 `data/config/modFiles/magicBounty_data.json`。这条记录使用原版势力、头像和装配，满足条件后由 MagicLib 自动加入酒吧赏金告示板：

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
        "target_importantPersonId": null,
        "target_first_name": "Mara",
        "target_last_name": "Vane",
        "target_portrait": "graphics/portraits/portrait_pirate02.png",
        "target_gender": "FEMALE",
        "target_rank": "spaceCommander",
        "target_post": "fleetCommander",
        "target_personality": "aggressive",
        "target_aiCoreId": null,
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
        "fleet_musicSetId": null,
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

前置数据：启用 MagicLib、GearIn 与 Console Commands，使用一份可以推进至少 90 个游戏日的测试存档。

1. 开启 Dev Mode，启动游戏并检查 `starsector.log` 中 `GearIn_magic_bounty` 的读取与 `VALID` 日志。
2. 在玩家低于 5 级、开局不足 30 日或舰队实力不足 30 FP 时进入独立势力酒吧，确认该任务不在告示板候选中。
3. 满足等级、日期、舰队和关系条件，进入规模至少为 3 的独立势力市场，打开非正式赏金告示板。
4. 选择 "断齿航路"，核对舰长、任务类型、舰队预览、距离、90 日期限、奖励与接受选项。
5. 接受委托，打开 Intel 并用 Console Commands 检查 `$GearIn_magic_bounty` 已写为 `false`。
6. 前往 Intel 标记地点，核对旗舰、两艘鞭挞者、一艘秃鹫号、补充舰船和敌对行为。
7. 歼灭整支舰队，检查信用点、独立势力关系、50 单位补给、成功文本和 `_succeeded` Memory 键。
8. 载入接受前的测试存档，再次领取任务并等待超过 90 日，检查过期文本和 `_expired` Memory 键。
9. 载入接受前的测试存档，在告示板永久忽略该任务，经过市场刷新后确认它不再出现。
10. 载入接受前的测试存档，让尚未接受的任务经过告示板刷新周期，确认该候选能够重新生成。
11. 分别把 `fleet_faction` 与旗舰装配改成不存在的 ID，重启游戏并记录对应校验日志，然后恢复有效值。
12. 破坏 JSON 逗号或引号，重启游戏并记录加载错误，然后恢复严格 JSON。

预期结果：

- 有效数据在日志中完成读取与校验，告示板只在全部市场和玩家条件成立时提供任务；
- 告示板显示 Mara Vane、舰队歼灭目标、原版风格舰队预览、距离与地图箭头；
- 接受后生成目标舰队和 Intel，基础 Memory 键为 `false`；
- 目标舰队至少达到 80 FP，并包含指定旗舰与三艘预设舰船；
- 成功后基础 Memory 键与 `_succeeded` 为 `true`，玩家获得信用点、关系与补给奖励；
- 接受后超过 90 日会进入过期状态并写入 `_expired`；
- 永久忽略的赏金不会重新提供，未接受而自然刷新的赏金可以再次生成；
- 无效势力或装配会使记录退出可用赏金集合，损坏 JSON 会产生明确的加载错误。

## 故障分析

- **酒吧始终没有赏金告示板**：记录没有通过市场、玩家或依赖条件，或者校验阶段已经移除了无效赏金。
    - **解决方案**：先从日志确认 `GearIn_magic_bounty : VALID`，再逐项核对市场势力与规模、玩家等级、日期、舰队 FP 和关系条件。

- **日志提示旗舰或预设装配缺失**：字段使用了 hull ID、文件名或不存在的 variant ID。
    - **解决方案**：把 `fleet_flagship_variant` 和 `fleet_preset_ships` 的键改成已登记的装配 ID；可选第三方装配放入 MagicBounty 专用装配目录并声明依赖 MOD。

- **任务出现了，但找不到目标地点**：允许的 theme、黑名单、距离与实体类型没有产生共同候选。
    - **解决方案**：保留至少一种可用 theme 和实体类型，并开启 `location_defaultToAnyEntity`；固定地点任务直接使用有效的 `location_entitiesID`。

- **任务完成后没有额外 Rules 行为**：`job_pick_script` 或 `job_conclusion_script` 写成了 Java 类名，或者同名 trigger 没有匹配规则。
    - **解决方案**：字段填写 Rules trigger，并为该 trigger 添加能够由 `FireBest` 命中的规则。

- **信用点奖励没有随舰队规模变化**：`job_reward_scaling` 或 `fleet_min_FP` 没有正值，缩放计算保持基础奖励。
    - **解决方案**：同时设置正数缩放倍率与最低 FP，并用不同规模的玩家舰队比较接受界面中的最终奖励。

## 总结

MagicBounty 用一个稳定 ID 把数据加载、酒吧筛选、目标生成、Intel 和战斗结算连成完整流程。出现条件决定玩家何时看到任务，人物、舰队与地点字段决定接受后生成什么，`ActiveBounty` 阶段和 Memory 键则把结果交给后续剧情。

排错时应沿相同顺序检查：先确认 JSON 被读取并通过 ID 校验，再确认市场与玩家条件，最后检查地点和结算状态。告示板没有出现通常发生在前两处，接受后找不到目标则应从舰队或地点配置继续追查。

## 建议参考项

- `data/config/modFiles/magicBounty_data_example.json`：MagicBounty 字段说明与文本变量示例；
- `org.magiclib.bounty.MagicBountyLoader`：读取、解析和校验赏金记录；
- `org.magiclib.bounty.MagicBountySpec`：赏金字段在运行时的数据对象；
- `org.magiclib.bounty.MagicBountyCoordinator`：筛选市场候选、创建目标并维护活动赏金；
- `org.magiclib.bounty.ActiveBounty`：接受、期限、Intel、Memory 与结算阶段；
- `Starsector/starsector-core/starsector.log`：定位 JSON、势力、装配与地点错误。
