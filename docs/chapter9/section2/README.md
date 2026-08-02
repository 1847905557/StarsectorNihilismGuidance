# 技能：效果作用域、等级与 fleet total

本节把"技能"（skill）讲透：`SkillSpecAPI` 与技能数据的三件套（`skill_data.csv`、`.skill` JSON、效果脚本）、`SkillEffectType` 决定的效果作用域（人物 / 舰队 / 战斗）、技能等级（0 / 1 / 2）与 `requiredSkillLevel` 门槛，以及整备界面（refit）的 fleet total 汇总（`FleetTotalSource` / `FleetTotalItem`）。读完本节，您应能回答"技能怎么注册、效果作用到谁、等级怎么判、数值写进哪里"这四件事。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 技能数据的三件套与注册链：
  - `data/characters/skills/skill_data.csv`（注册行：名称、order、tier、reqPoints、角色类型 flags、tags、icon）
  - `data/characters/skills/<id>.skill`（JSON：`governingAptitude`、`elite`、`scope`、`effectGroups`）
  - 每个 effect 的 `script` 类，经技能加载流程汇成 `SkillSpecAPI`，由 `Global.getSettings().getSkillSpec(id)` 读取
  - **注意：旧资料里的 " skills.json" 在 RC8 不存在**，技能 JSON 按技能拆成 `.skill` 文件；
- 复述 `SkillSpecAPI` / `SkillEffectSpecAPI` 的字段语义，并记住两条必须遵守的注册规则：`.skill` 的 `id` **必须**在 `skill_data.csv` 有行，否则技能被跳过且无报错；effect 的 `type` **必须**与脚本类实现的接口族匹配，否则在效果实例化 / 应用时抛类型异常；
- 说出 `SkillEffectType` 全部 11 个值分别对应哪个效果接口、作用于哪个对象集合、在哪个入口被应用（人物侧 `refreshCharacterStatsEffects`、舰队成员 stats 重建、舰船构造、战机起飞、市场刷新、UI 描述、解锁集合），并解释 `scope` 只是 UI 文本、**禁止**用它判断生效范围；
- 说出技能等级语义：0（未学）/ 1（已学）/ 2（精英，仅 `elite:true`）；UI 加点路径 `increaseSkill` 钳制到 1 或 2，`setSkillLevel` 不钳制
  - 每个 effectGroup 的 `requiredSkillLevel` 是生效门槛，`apply()` 收到的 `level` 参数 = `getSkillLevel(governingSkill)`，不是等级增量
  - `effectBasedOnLevel` 只影响 tooltip 的每级描述渲染；
- 复述 effect id 的组成 `<技能id>_<类型后缀>_<效果序号>`，并把它当作 modifier ID 使用（稳定 ID 规则在技能域的直接延伸），`unapply` 必须用同一个 id；
- 说出 fleet total 管道：效果脚本类实现 `FleetTotalSource` → `getFleetTotals()` 按 `label+value` 去重、按 `sortOrder` 排序 → 整备界面（refit）舰队构成表展示（全库唯一使用方）
  - 并解释"两个技能返回相同 label+value 会合并成一行"；
- 从症状定位典型故障：技能在技能树里不出现、效果完全无效、应用时报类型异常、数值残留、战机加成"看不到"、fleet total 行消失。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- 命名约定（C01-S01）：MOD ID `gear_in`、数据 / 资源 key 前缀 `GearIn_`、包 `data.scripts.gearin.<domain>`、类 `GearIn<Subject>`。
- 编译 JAR 链路与脚本类加载（C06-S01）：`.skill` 的 `script` 类与普通脚本一样按类名解析，`loadSkills` 会对每个 effect class 调 `registerScriptClass`。
- `Global` 生命周期与阶段判断（C06-S03）：读 `Global.getSector().getPlayerStats()` 前要确认处于生涯状态。
- `MutableStat` 的 modifier ID 稳定性（C07-S03）：三规则与 `(base + base*percent/100 + flat) * mult` 公式——本节效果脚本全部沿用：用引擎传入的 effect id 做 modifier id、`unapply` 原样解除。
- `FleetMemberAPI` 与成员 stats（C08-S03）：本节 SHIP / ALL_SHIPS_IN_FLEET 效果的生涯侧落点。
- 人物 / captain / officer（C09-S01）：`MutableCharacterStatsAPI` 挂在人物上，技能等级与刷新入口（`setSkillLevel` / `refreshCharacterStatsEffects`）都在这里；旗舰舰长是玩家人物，所以玩家的 SHIP 技能作用到旗舰。

## 术语

按术语约定，本节首次完整讲解以下词（后续页面继续使用）：

- **技能（skill）**：API 名保留 `SkillSpecAPI`。挂在人物 `MutableCharacterStatsAPI` 上的可学习能力；由 `skill_data.csv` 注册行 + `.skill` JSON + 效果脚本三件套组成。
- **技能等级（skill level）**：0 / 1 / 2 三个值（2 仅精英技能可达）；`requiredSkillLevel` 是每个效果组的生效门槛。
- **效果作用域（effect type / scope）**：API 名保留 `SkillEffectType`。决定每个 effect 作用于哪个对象集合（被驾驶舰船 / 全舰队舰船 / 战机 / 角色数值 / 舰队数值 / 市场 / 描述 / 解锁）；`.skill` 里的 `scope` 字段只是 UI 文本，不是作用域。
- **fleet total**：整备界面（refit）舰队构成表里的一行"舰队汇总"（如原版的 "All ships"、自动化点数行）；由实现 `FleetTotalSource` 的效果脚本提供 `FleetTotalItem`。

## 文件与 API 链路

### 数据三件套与注册入口

RC8 的技能数据在 `data/characters/skills/` 下，**不是**单个 `skills.json`：

::: info 为什么 RC8 没有 skills.json
注册信息（名称、order、tier、reqPoints、icon 等）集中在 `skill_data.csv`，定义信息（aptitude、等级组、效果清单）按技能各自放在独立的 `<id>.skill` 文件里，两者在 CSV spec 加载期经 `loadSkills` 按 id 合并成一个 `SkillSpecAPI`。

旧资料里的单个 "skills.json" 在 RC8 不存在，按旧资料找文件会落空。
:::

| 文件 | 角色 | 关键字段 |
|---|---|---|
| `skill_data.csv` | 注册表：技能必须在此有行 | `id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon` |
| `<id>.skill`（JSON）| 技能定义：aptitude、等级组、效果清单 | `id`、`governingAptitude`、`compressHullmods`（默认 true）、`elite`（默认 false）、`permanent`（默认 false）、`scope` / `scopeStr`、`scope2` / `scopeStr2`、`effectGroups[]` |
| `aptitude_data.csv` | 能力树分组 | `id,name,color,description,order,effect_skill_id,sound1..5,sound_story,elite_overlay*` |
| `data/characters/skills/<id>.skill` 的 effect | 效果清单 | 每项 `type`（`SkillEffectType` 枚举，必填）、`script`（类名，可选）、`hullmods`（对象：船插 id → 解锁等级）、`abilities`（数组）|

`effectGroups[]` 每组的字段：`governingSkill`（可选，**默认 = 本技能 id**）、`name`（可选，如 "Elite"）、`effectBasedOnLevel`（可选，**默认 true**）、`requiredSkillLevel`（可选，**默认 1**）、`effects[]`。

注册链（CSV spec 加载期 `loadSkills`）：

```text
skill_data.csv（按 id 读行，收集全部注册 id）
  + aptitude_data.csv 的 effect_skill_id（能力树自身的"技能"）
  → 遍历 data/characters/skills/*.skill：
       SkillSpecAPI(readJSON) → id 已注册？"Skipping skill [...], was already loaded" 跳过
                          → id 不在 skill_data.csv？"Skipping loading skill [...], it's not listed in skill_data.csv." 跳过
                          → 否则 putSpec 注册，并把每个 effect 的 script 类 registerScriptClass
  → 回头按 skill_data.csv 行补写 order / tier / reqPoints / 角色类型 flags / tags / icon / name / description / author
```

::: warning 技能被静默跳过时不报错
两个"跳过"分支都只写日志、**不报错**——技能在技能树里不出现且没有任何 ERROR 时，先到日志里查这两行，再动手改代码。
:::

### 效果接口族（按作用域）

所有效果接口都继承 `LevelBasedEffect`（`getEffectDescription(float)` / `getEffectPerLevelDescription()` / `getScopeDescription()`）。`SkillEffectType` 与接口、作用对象、应用入口的对应：

| `SkillEffectType` | 效果接口 | 作用对象 | 应用入口 |
|---|---|---|---|
| `SHIP` | `ShipSkillEffect` `apply(stats, hullSize, id, level)` | 被驾驶舰船（舰长 / 指挥官个人技能作用到其旗舰）| 生涯 `FleetMemberAPI.updateStats` 的 `captain.getStats().applyPersonalToStats`；战斗侧舰船构造时 `setCaptain` → `applyPersonalToShip` |
| `ALL_SHIPS_IN_FLEET` | `ShipSkillEffect` | 舰队全部非战机舰船 | `FleetMemberAPI.updateStats`、舰船对象创建（`createShip`）的 `applyFleetwideToStats`、`FleetDataAPI.syncIfNeeded`（指挥官回退链）、tooltip 渲染 |
| `SHIP_FIGHTERS` | `ShipSkillEffect` | 被驾驶舰船起飞的本舰战机 | 战斗起飞 `FighterLaunchBayAPI`（母舰舰长 stats）|
| `ALL_FIGHTERS_IN_FLEET` | `ShipSkillEffect` | 舰队全部战机 | 战斗起飞 `FighterLaunchBayAPI`（舰队指挥官 → 舰队数据指挥官 → 默认指挥官回退链）|
| `CHARACTER_STATS` | `CharacterStatsSkillEffect` `apply(stats, id, level)` | 角色自身 `MutableCharacterStatsAPI` | `MutableCharacterStatsAPI.refreshCharacterStatsEffects` |
| `FLEET` | `FleetStatsSkillEffect` `apply(stats, id, level)` | 该角色指挥舰队的 `MutableFleetStatsAPI` | `MutableCharacterStatsAPI.refreshCharacterStatsEffects` |
| `GOVERNED_OUTPOST` | `MarketSkillEffect` `apply(market, id, level)` | 治理的殖民地市场 | `refreshGovernedOutpostEffects` |
| `ALL_OUTPOSTS` | `MarketSkillEffect` | 玩家全部殖民地市场 | `refreshAllOutpostsEffectsForPlayerOutposts` / `refreshAllOutpostsEffects` |
| `DESCRIPTION` | `DescriptionSkillEffect` | 技能面板 / tooltip 文本行 | 纯 UI，无数值写入 |
| `HULLMOD_UNLOCK` / `ABILITY_UNLOCK` | 无脚本接口（数据声明）| 解锁集合 | effect 内 `hullmods` / `abilities` 键；使用方 `getUnlockedHullMods(level)`、`MutableCharacterStatsAPI.getGrantedAbilityIds` / `isHullModAvailable` |

::: warning 生效范围只看 type，别信 scope
`ALL_SHIPS_IN_FLEET` 与 `SHIP` 的**接口相同**（都是 `ShipSkillEffect`），作用对象集合完全由 `type` 决定——这是本节最容易被 `scope` 字段误导的一点： `scope` 只是 UI 文本，写 `"scope":"FLEET"` 不会把 SHIP 效果变成全舰队加成。
:::

### 等级管道

- 技能等级只有 0（未学）/ 1（已学）/ 2（精英）。UI 加点走 `increaseSkill` / `decreaseSkill`，**钳制**到 1（非精英）或 2（`elite:true`）（`MutableCharacterStatsAPI`）；`setSkillLevel(id, level)` 不钳制，直接写任意 float 并触发 `refreshCharacterStatsEffects`。
- 每个 effectGroup 的 `requiredSkillLevel`（默认 1）是门槛：`getSkillLevel(governingSkill) >= requiredSkillLevel` 的效果才进入应用列表（如 `getShipEffects` 的过滤）。
- `apply()` 收到的 `level` 参数 = `getSkillLevel(governingSkill)`，**不是**等级增量，也不会逐帧变化。
- `effectBasedOnLevel`（默认 true）只被 UI 消费：true 时标准 tooltip 追加渲染 `getEffectPerLevelDescription()`。
- 引擎组合 effect id 并传入 `apply` / `unapply`：`<技能id>_<类型后缀>_<效果序号>`
  - 后缀：`SHIP→_ship_`、`ALL_SHIPS_IN_FLEET→_ships_`、`SHIP_FIGHTERS→_ship_fighters_`、`ALL_FIGHTERS_IN_FLEET→_all_fighters_`
  - `CHARACTER_STATS 与 FLEET 都是 _stats_`、 `GOVERNED_OUTPOST→_GO_`、`ALL_OUTPOSTS→_AO_`
  - 序号是效果在本技能**平铺效果列表**（跨 effectGroups 累计）中的下标（`MutableCharacterStatsAPI`）
  - 本节示例 `GearIn_gunnery` 的 Level1（SHIP）拿到的 id 是 `GearIn_gunnery_ship_1`。

::: caution effect id 必须原样用作 modifier id
引擎传入的 effect id（本节示例 `GearIn_gunnery_ship_1`）**必须**原样用作 modifier id，`unapply` **必须**用同一个 id 解除；**禁止**自造 id 或拼接字符串。违反的后果：数值残留、解除静默失效、重放多次后数值膨胀，且全程没有任何报错。
:::

### fleet total 管道

- 接口：`FleetTotalSource.getFleetTotalItem()` → `FleetTotalItem`（字段 `label` / `value` / `valueColor` / `labelColor` / `sortOrder` / `tooltipCreator`）。
- 汇总：`getFleetTotals()`遍历已学技能的全部效果，凡 `level >= requiredSkillLevel` 且脚本实例 `instanceof FleetTotalSource`，就取 `getFleetTotalItem()`
  - **按 `label + "_" + value` 去重**（两个技能 / 效果返回相同 label+value 只保留一行），按 `sortOrder` 升序排成舰队构成表的行数据（附 `skillLevels`，供 tooltip 列出贡献技能）。
- 使用方：整备界面（refit）的舰队构成表——界面把 `getPlayerStats().getFleetTotals()` 渲染成构成表行，`tooltipCreator` 挂可展开 tooltip；全库对 `getFleetTotals()` 的消费点只有这一处（技能面板 tooltip 不消费它）。
- 原版聚合做法：`BaseSkillEffectDescription.getOPTotal()` / `getCombatOPTotal()` / `getAutomatedPointsTotal()`（`com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription`）
  - 对舰队成员逐艘收集贡献（`FleetMemberPointContrib`）再汇总
  - 本节示例只演示最小管道（静态值 + 自定义 tooltip）。

### 读取入口（使用方）

- `Global.getSettings().getSkillSpec(String skillId)`（`com.fs.starfarer.api.SettingsAPI`）：读 `SkillSpecAPI`；未注册的 id 由 CSV spec 加载期静默返回 null。
- `Global.getSettings().getSortedSkillIds()`：按 order 排序的技能 id 列表（技能树 UI 用）。
    - `MutableCharacterStatsAPI`（`com.fs.starfarer.api.characters.MutableCharacterStatsAPI`）的读写方法：
      - `getSkillLevel(id)` / `setSkillLevel(id, level)` / `increaseSkill` / `hasSkill` / `refreshCharacterStatsEffects()`
    - 角色的技能状态与刷新入口。

## 规则强度

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| `.skill` 的 `id` **必须**在 `skill_data.csv` 有对应行 | 必须 | 加载期日志 `Skipping loading skill [...], it's not listed in skill_data.csv.`，技能不注册、技能树不出现、`getSkillSpec(id)` 返回 null，**无任何 ERROR** |
| 技能 id **必须**全局唯一（跨 MOD）| 必须 | 后加载的同 id 技能被跳过（日志 `Skipping skill [...], was already loaded`），本 MOD 的版本静默失效 |
| effect 的 `type` **必须**与脚本类实现的接口族匹配（SHIP → `ShipSkillEffect`、FLEET → `FleetStatsSkillEffect`、CHARACTER_STATS → `CharacterStatsSkillEffect`、市场 → `MarketSkillEffect`、DESCRIPTION → `DescriptionSkillEffect`）| 必须 | 效果实例化时 `getAsXxxEffect()` 强转抛 `ClassCastException`（如舰船 stats 重放 / 起飞 / tooltip 渲染处），游戏画面报错或加载失败 |
| 效果脚本类 **必须**无参可实例化（不写构造器或显式无参构造器；脚本类加载流程 `loadScriptInstance` 用 `newInstance()`）| 必须 | `InstantiationException` 包装为 `RuntimeException "Problem loading class [<类名>]"`，在效果被实例化的时刻（不是加载期）抛出 |
| 效果脚本 **必须**把引擎传入的 `id` 原样用作 modifier id，`unapply` 用同一个 id 解除 | 必须 | 数值残留 / 累计或解除不生效（稳定 ID 规则在技能域的直接应用，前置知识已引用；引擎给的 id 形如 `GearIn_gunnery_ship_1`）|
| 生效范围**必须**按 `SkillEffectType` 判断；`.skill` 的 `scope` / `scopeStr` 只是 UI 文本 | 必须 | 把 `scope:FLEET` 的 SHIP 效果当"全舰队加成"排查，实际它只作用于被驾驶舰船，舰队其它船"没变化" |
| 战机效果（SHIP_FIGHTERS / ALL_FIGHTERS_IN_FLEET）只在战斗起飞时应用 | 必须 | 在生涯侧读战机 stats 期望已生效，读不到；战机加成"部署前看不到"属正常语义 |
| 非精英技能（`elite` 缺省 / false）经 UI 加点最高到 1 级，`requiredSkillLevel:2` 的效果组永不激活 | 必须 | 2 级门槛的效果"不生效"；精英加成必须配 `"elite":true` |
| fleet total 的 `label` + `value` 必须能区分不同的汇总行 | 必须 | 两个技能返回相同 label+value 时 `getFleetTotals()` 去重合并成一行，其中一行的汇总"消失" |
| 技能 `icon` 列 **必须**指向可加载的纹理 | 必须 | `loadSkills` 末尾 `queueResource(TEXTURE, ...)` 排队纹理，缺失时资源加载阶段报错（TEXTURE 无 try/catch）|
| 技能 id 使用 MOD 专属前缀（`GearIn_`），数据 id、`.skill` 文件名、脚本类名三处一致 | 建议 | 撞原版 / 其它 MOD id 时被先加载者覆盖，行为随 MOD 启用顺序漂移 |
| 效果脚本在 `.skill` 里按"外层类 `$` 嵌套类"书写类名（如 `...GearInGunnerySkill$Level1`）| 建议 | 类名拼写错误 → 效果实例化时 `Problem loading class` |

强度调整记录：本节为新建页面，无迁移原文，硬措辞全部来自 RC8 权威来源（CSV spec 加载期 `loadSkills` 的两条跳过日志、`SkillEffectSpecAPI` 的强转、脚本类加载流程 `loadScriptInstance` 的 `newInstance` 与包装异常、`MutableCharacterStatsAPI` 的等级钳制与去重），未对任何来源做软化。

违反后的可观察结果均已在上面列出。

## 完整最小例

在既有 MOD 布局（前置知识所列的编译链路）之上新增一个技能：两个数据文件 + 一个效果脚本类（本节所有，id `GearIn_gunnery`）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   └── characters\skills\
│       ├── GearIn_gunnery.skill          # 本节新增
│       └── skill_data.csv                # 新建（表头 + 本节行；若已有该文件则把行追加进去）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin               # C06-S02 所有；本节不改动
│       └── campaign\
│           └── GearInGunnerySkill        # 本节新增
└── jars\
```

### 数据文件：data\characters\skills\GearIn_gunnery.skill

```json
{
	"id":"GearIn_gunnery",
	"governingAptitude":"combat",
	"compressHullmods":false,
	"elite":true,
	"scope":"PILOTED_SHIP",
	"effectGroups":[
		{
			"name":"Level 1",
			"requiredSkillLevel":1,
			"effectBasedOnLevel":false,
			"effects":[
				{"type":"DESCRIPTION", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$Level0"},
				{"type":"SHIP", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$Level1"},
				{"type":"SHIP_FIGHTERS", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$LevelShipFighters"},
				{"type":"FLEET", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$LevelFleet"},
				{"type":"CHARACTER_STATS", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$LevelStats"},
			]
		},
		{
			"name":"Elite",
			"requiredSkillLevel":2,
			"effectBasedOnLevel":false,
			"effects":[
				{"type":"SHIP", "script":"data.scripts.gearin.campaign.GearInGunnerySkill$Level1A"},
			]
		},
	],
}
```

要点：

- `elite:true` 允许技能到 2 级；第 2 个 effectGroup（`requiredSkillLevel:2`，`name:"Elite"`）在 2 级才激活——与 `gunnery_implants.skill` 同款结构。
- 五个 effect 平铺后的序号：Level0=0、Level1=1、LevelShipFighters=2、LevelFleet=3、LevelStats=4、Level1A=5。由此引擎组合的 effect id：
  - `GearIn_gunnery_ship_1`、`GearIn_gunnery_ship_fighters_2`、`GearIn_gunnery_stats_3`（FLEET）、 `GearIn_gunnery_stats_4`（CHARACTER_STATS）、`GearIn_gunnery_ship_5`（精英）
  - 效果脚本必须原样使用。
- `governingSkill` 未写 → 默认本技能 id：所有效果的等级判据都是 `GearIn_gunnery` 自己的等级。

### 数据文件：data\characters\skills\skill_data.csv（表头 + 本节行）

```csv
id,name,order,tier,reqPoints,reqPointsPerExtraSkill,description,author,combat officer,admiral,admin,tags,icon,
GearIn_gunnery,Gunnery,1025,2,1,0,"Precision gunnery drills: ballistic and energy weapon damage up, autofire accuracy up, fighter top speed up, fleet burn up, command points up.","GearIndustry Weapons Division",TRUE,,,,graphics/icons/skills/GearIn_gunnery.png,
```

要点：

- `id` 与 `.skill` 的 `id` 逐字一致；`tier:2`（战斗系 2 阶）、`reqPoints:1`（消耗 1 技能点）。
- `combat officer` 列 `TRUE`：军官也可以学。
- `icon` 指向本 MOD 图标资源。**本单元无 PNG 资产**：冒烟时请临时把 icon 列指向原版 `graphics/icons/skills/gunnery_implants.png`（资源加载期会排队校验纹理，缺失会导致加载失败）；生产 MOD 必须自备图标（资产不在本单元范围）。

### GearInGunnerySkill

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.characters.FleetStatsSkillEffect;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：SkillSpecAPI、效果作用域、技能等级与 fleet total。
 *
 * 本类只是效果脚本的容器（原版 GunneryImplants 同款做法）：
 * 数据侧的 .skill 文件与 skill_data.csv 行把每个 effect 的 type 分派到下面的嵌套类。
 *
 * 作用域（effect type 决定作用对象集合，与脚本类实现哪个接口一一对应）：
 * - DESCRIPTION   -> Level0           技能面板与 tooltip 的文本行；
 * - SHIP          -> Level1 / Level1A 被驾驶（该角色指挥）的舰船 stats；
 * - SHIP_FIGHTERS -> LevelShipFighters 由被驾驶舰船起飞的本舰战机 stats（起飞时应用）；
 * - FLEET         -> LevelFleet       该角色指挥舰队的 MutableFleetStatsAPI；
 * - CHARACTER_STATS -> LevelStats     角色自身 MutableCharacterStatsAPI。
 *
 * 等级语义：
 * - 技能等级只有 0（未学）/ 1（已学）/ 2（精英，仅 elite:true 的技能）；
 *   UI 加点路径 increaseSkill 会钳制到 1 或 2，setSkillLevel 不钳制；
 * - 每个 effectGroup 的 requiredSkillLevel 是生效门槛：getSkillLevel(governingSkill) >= requiredSkillLevel
 *   才进入 apply 列表；默认 governingSkill = 本技能 id；
 * - apply() 收到的 level 参数 = getSkillLevel(governingSkill)，不是等级增量；
 * - 引擎按 <技能id>_<类型后缀>_<效果序号> 组合 effect id 并传入 apply，
 *   效果类必须原样用它做 MutableStat / StatBonus 的 modifier id（稳定 ID 规则，见前置页）。
 *
 * fleet total：
 * - 本技能 Level1 同时实现 FleetTotalSource：整备界面（refit）的舰队构成表会调用
 *   getFleetTotalItem() 展示一行汇总；同一脚本实例按需被重新实例化，效果类必须无参可实例化。
 */
public class GearInGunnerySkill {

    private static final Logger LOG = Global.getLogger(GearInGunnerySkill.class);

    /** 数据侧技能 id（skill_data.csv 行 + GearIn_gunnery.skill 的 id 字段，三者必须逐字一致）。 */
    public static final String SKILL_ID = "GearIn_gunnery";

    /** 数值常量：与 .skill 的 effectGroups、skill_data.csv 行、页面描述文本保持一致。 */
    public static final float DAMAGE_BONUS = 15f;
    public static final float DAMAGE_BONUS_ELITE = 5f;
    public static final float AIM_BONUS = 100f;
    public static final float FIGHTER_SPEED_BONUS = 10f;
    public static final float FLEET_BURN_BONUS = 2f;
    public static final float COMMAND_POINTS_BONUS = 1f;

    /** DESCRIPTION：纯文本行；数值写入由其它 effect type 承担。 */
    public static class Level0 implements DescriptionSkillEffect {
        @Override
        public Color getTextColor() {
            return null;
        }

        @Override
        public String getString() {
            return "Precision gunnery drills: ballistic and energy weapon damage up by 15%, "
                    + "autofire accuracy up by 100%, fighters' top speed up by 10%, "
                    + "fleet burn up by 2, and command points up by 1.";
        }

        @Override
        public String[] getHighlights() {
            return new String[] {"15%", "100%", "10%", "2", "1"};
        }

        @Override
        public Color[] getHighlightColors() {
            Color h = Misc.getHighlightColor();
            return new Color[] {h, h, h, h, h};
        }
    }

    /** SHIP：被驾驶舰船（角色为舰长 / 指挥官时作用于其旗舰），同时提供 fleet total 行。 */
    public static class Level1 extends BaseSkillEffectDescription implements ShipSkillEffect, FleetTotalSource {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS);
            stats.getEnergyWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS);
            stats.getAutofireAimAccuracy().modifyFlat(id, AIM_BONUS * 0.01f);
            LOG.info(String.format("GearIn gunnery ship apply: id=%s level=%.1f hull=%s damage=+%.0f%% aim=+%.0f%%",
                    id, level, hullSize, DAMAGE_BONUS, AIM_BONUS));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getAutofireAimAccuracy().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) DAMAGE_BONUS + "% ballistic and energy weapon damage, "
                    + "+" + (int) AIM_BONUS + "% autofire accuracy";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }

        /** fleet total：整备界面（refit）的舰队构成表按 label+value 去重后展示这一行。 */
        @Override
        public FleetTotalItem getFleetTotalItem() {
            FleetTotalItem item = new FleetTotalItem();
            item.label = "Gunnery damage bonus";
            item.value = "+" + (int) DAMAGE_BONUS + "%";
            item.sortOrder = 50;
            item.tooltipCreator = new TooltipCreator() {
                @Override
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }

                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Ballistic and energy weapon damage bonus granted to the piloted ship "
                            + "while this skill is learned.", 0f);
                }
            };
            return item;
        }
    }

    /** SHIP（精英组）：技能到 2 级（精英）后追加的加成。 */
    public static class Level1A extends BaseSkillEffectDescription implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS_ELITE);
            stats.getEnergyWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS_ELITE);
            LOG.info(String.format("GearIn gunnery elite apply: id=%s level=%.1f hull=%s damage=+%.0f%%",
                    id, level, hullSize, DAMAGE_BONUS_ELITE));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) DAMAGE_BONUS_ELITE + "% ballistic and energy weapon damage";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    /** SHIP_FIGHTERS：本舰（被驾驶舰船）起飞的战机，起飞时由 FighterLaunchBayAPI 应用。 */
    public static class LevelShipFighters extends BaseSkillEffectDescription implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxSpeed().modifyPercent(id, FIGHTER_SPEED_BONUS);
            LOG.info(String.format("GearIn gunnery fighters apply: id=%s level=%.1f hull=%s speed=+%.0f%%",
                    id, level, hullSize, FIGHTER_SPEED_BONUS));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxSpeed().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) FIGHTER_SPEED_BONUS + "% top speed for fighters launched by the piloted ship";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.SHIP_FIGHTERS;
        }
    }

    /** FLEET：角色指挥舰队的 MutableFleetStatsAPI，由 refreshCharacterStatsEffects 统一重放。 */
    public static class LevelFleet extends BaseSkillEffectDescription implements FleetStatsSkillEffect {

        @Override
        public void apply(MutableFleetStatsAPI stats, String id, float level) {
            stats.getFleetwideMaxBurnMod().modifyFlat(id, FLEET_BURN_BONUS);
            LOG.info(String.format("GearIn gunnery fleet apply: id=%s level=%.1f burn=+%.0f",
                    id, level, FLEET_BURN_BONUS));
        }

        @Override
        public void unapply(MutableFleetStatsAPI stats, String id) {
            stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) FLEET_BURN_BONUS + " fleet burn level";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }
    }

    /** CHARACTER_STATS：角色自身数值，由 refreshCharacterStatsEffects 统一重放。 */
    public static class LevelStats extends BaseSkillEffectDescription implements CharacterStatsSkillEffect {

        @Override
        public void apply(MutableCharacterStatsAPI stats, String id, float level) {
            stats.getCommandPoints().modifyFlat(id, COMMAND_POINTS_BONUS);
            LOG.info(String.format("GearIn gunnery stats apply: id=%s level=%.1f commandPoints=+%.0f",
                    id, level, COMMAND_POINTS_BONUS));
        }

        @Override
        public void unapply(MutableCharacterStatsAPI stats, String id) {
            stats.getCommandPoints().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) COMMAND_POINTS_BONUS + " command point";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }
    }
}
```

对应关系：

- 外层类只是容器：嵌套类名（`Level0` / `Level1` / `Level1A` / `LevelShipFighters` / `LevelFleet` / `LevelStats`）与 `.skill` 的 `script` 值（`GearInGunnerySkill$Level1` 等）逐字对应——原版 `GunneryImplants` 同款做法。
- `Level1 implements ShipSkillEffect, FleetTotalSource`：同一个脚本类被两处消费——`SHIP` 效果重放时经 `getAsShipEffect()` 强转，`getFleetTotals()` 时经 `getAsFleetTotalSource()` 检查 `instanceof`
  - 两处都是**每次按类名重新实例化**（脚本类加载流程 `loadScriptInstance` → `newInstance()`），效果类不能有带参构造器。
- `apply` 里的 modifier id 全部用传入的 `id`：`modifyPercent(id, ...)` / `modifyFlat(id, ...)`，`unapply` 用同一个 `id` 调 `unmodify(id)` / `unmodifyFlat(id)`——稳定 ID 规则的直接应用（规则见前置知识；引擎每次重放传入的 id 逐字相同）。
- `Level1.getFleetTotalItem()` 返回 `label="Gunnery damage bonus"`、`value="+15%"`、`sortOrder=50` 与自定义 tooltip：这是 fleet total 行的最小完整形态；`TOOLTIP_WIDTH` 继承自 `BaseSkillEffectDescription`（450）。
- `LevelFleet` / `LevelStats` 的 `getScopeDescription()` 返回 `FLEET`：这只是 tooltip 的"作用范围"文本；实际生效对象由 `type`（FLEET / CHARACTER_STATS）决定。
- 五个 `apply` 各打一行 `GearIn gunnery ...` 日志：`id` 与 `level` 直接可见，是"效果作用域 + 等级"的现场表现。
- `getEffectPerLevelDescription()` 返回 null：`.skill` 里 `effectBasedOnLevel:false`，tooltip 不渲染每级描述行（本节不演示 per-level 描述）。

### 消费与调用点（工程片段——方法所在类归 C06-S02 所有，本节只演示调用，不改动那些文件）

```java
    // 读取（消费者，任何生涯代码）：
    SkillSpecAPI spec = Global.getSettings().getSkillSpec("GearIn_gunnery");
    if (spec == null) {
        LOG.info("GearIn skill spec missing - check skill_data.csv registration");
    }

    // 学习（脚本侧临时钩子；正式路径是玩家在技能树里花技能点，走 increaseSkill）：
    Global.getSector().getPlayerStats().setSkillLevel("GearIn_gunnery", 1f);
    // 精英：
    Global.getSector().getPlayerStats().setSkillLevel("GearIn_gunnery", 2f);
```

学习入口小结（`MutableCharacterStatsAPI`）：`setSkillLevel` 与 `increaseSkill` 都会触发 `refreshCharacterStatsEffects`；FLEET 与 CHARACTER_STATS 效果在刷新时**立即**重放。

SHIP 族效果要等成员 stats 重建（`FleetMemberAPI.updateStats`，开舰队界面 / 舰队同步触发）或舰船构造 / 战机起飞。

构建命令（在 `Starsector\mods\GearIndustry\` 执行，编译链路见前置知识）：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/campaign/GearInGunnerySkill.class` 及六个内部类（可用 `jar tf` 核对）：

- `$Level0` / `$Level1` / `$Level1A` / `$LevelShipFighters` / `$LevelFleet` / `$LevelStats`

本节代码块与磁盘 fixture 逐字一致 （fixture 路径见「建议参考项」），fixture 编译验收命令见「预期结果」。

### GearIn_gunnery 完整流程（验收 D 静态交叉核对）

| 环节 | 内容 | 一致性要求 |
|---|---|---|
| 注册行 | `skill_data.csv` 的 `id=GearIn_gunnery`（order 1025、tier 2、reqPoints 1、combat officer TRUE）| 与 `.skill` 的 `id` 逐字一致 |
| 技能定义 | `GearIn_gunnery.skill`：`governingAptitude=combat`、`elite=true`、两个 effectGroups | id 一致；脚本类名与 Java 类逐字一致 |
| 效果脚本 | `data.scripts.gearin.campaign.GearInGunnerySkill$Level0/Level1/Level1A/LevelShipFighters/LevelFleet/LevelStats` | 六个类名与 `.skill` 六个 `script` 值一致；`type` 与接口族匹配 |
| 消费入口 | CSV spec 加载期 `loadSkills` 注册 → 玩家 `setSkillLevel` / UI 加点 → `refreshCharacterStatsEffects` / `FleetMemberAPI.updateStats` / 战斗舰船构造 / `FighterLaunchBayAPI` / `getFleetTotals` | 日志行、技能树行、burn、指挥点数、fleet total 行 |
| 可观察结果 | 日志 `GearIn gunnery ... apply`、技能树 "Gunnery" 行、整备界面 "Gunnery damage bonus +15%" 行 | 见「预期结果」|

### 验证方案（基于本例）

前置数据：既有 MOD 布局与编译链路（前置知识所列）、`GearIn_gunnery.skill`、`skill_data.csv` 行、`GearInGunnerySkill` 源码（含调用点片段中的临时钩子）。

操作步骤：

1. **fixture 编译**：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC09s02Java --no-daemon`，确认以 `BUILD SUCCESSFUL` 结束（退出码 0）。
  2. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/campaign/GearInGunnerySkill.class` 与六个 `$` 内部类
     - 冒烟期间把 `skill_data.csv` 的 icon 列指向原版 `graphics/icons/skills/gunnery_implants.png`。
  3. **启动与注册**：新建生涯（或读档），`Starsector/starsector-core/starsector.log` 出现 `Loading skill [data/characters/skills/GearIn_gunnery.skill]`
     - 无 `Skipping skill` / `Skipping loading skill`，无 `gear_in` 引起的 `ERROR`。
  4. **等级 1**：临时钩子 `setSkillLevel("GearIn_gunnery", 1f)`（放在 `onGameLoad` 内）
     - 日志立即出现 `GearIn gunnery stats apply: id=GearIn_gunnery_stats_4 level=1.0 commandPoints=+1` 与 `GearIn gunnery fleet apply: id=GearIn_gunnery_stats_3 level=1.0 burn=+2`
     - 开舰队界面（F 键）后出现 `GearIn gunnery ship apply: id=GearIn_gunnery_ship_1 level=1.0 ... damage=+15% aim=+100%`
     - 战斗内载机起飞时出现 `GearIn gunnery fighters apply: id=GearIn_gunnery_ship_fighters_2 ... speed=+10%`。
5. **界面**：C 键技能树战斗系出现 "Gunnery" 行与 DESCRIPTION 文本；R 键整备界面舰队构成表出现 `Gunnery damage bonus +15%` 行，悬停显示自定义 tooltip；舰队面板 burn 提升 2（最慢成员 burn ≥ 8 时）；战斗部署界面指挥点数 +1。
6. **等级 2 / 精英**：钩子改为 `setSkillLevel("GearIn_gunnery", 2f)`，日志追加 `GearIn gunnery elite apply: id=GearIn_gunnery_ship_5 level=2.0 ... damage=+5%`。
7. **解除**：`setSkillLevel("GearIn_gunnery", 0f)` 或 `decreaseSkill` 后新日志停止，burn / 指挥点数 / fleet total 行恢复基线，数值无残留。
  8. **故障注入 1**：把 `skill_data.csv` 里的 `GearIn_gunnery` 行临时删掉再启动：日志出现 `Skipping loading skill [data/characters/skills/GearIn_gunnery.skill], it's not listed in skill_data.csv.`
     - 技能树无 "Gunnery" 行（对应故障分析 1）；改回后确认恢复。
9. **故障注入 2**：把 `.skill` 里 Level1 的 `type` 临时改成 `"FLEET"` 再启动并学习：stats 重放时抛 `ClassCastException`（对应故障分析 3）；改回后确认恢复。
10. **故障注入 3**：让另一个效果返回与 Level1 相同的 `label` + `value`，整备界面只显示一行（对应故障分析 9）；改回后确认恢复。

通过信号：操作 1–7 全部符合；操作 8–10 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures` 构建配置等）做过修改。

::: tip 日志核对技巧
在 `Starsector/starsector-core/starsector.log` 中按 `GearIn gunnery` 前缀检索即可定位本技能的全部 apply 日志行；出现 `ERROR` 时优先查看 `gear_in` 包相关的堆栈。
:::

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text
gradle -p tutorial-fixtures compileC09s02Java --no-daemon
``` 以 `BUILD SUCCESSFUL` 结束（退出码 0）。
  2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/campaign/GearInGunnerySkill.class` 与六个 `$` 内部类
     - 启动游戏：加载期无 `gear_in` 引起的 ERROR（注意 icon 列在冒烟期间指向原版图标，见「完整最小例」）。
3. **注册确认**：`starsector.log` 出现 `Loading skill [data/characters/skills/GearIn_gunnery.skill]`（`loadSkills` 的加载日志），**没有** `Skipping skill [...]` 或 `Skipping loading skill [...]` 两行。
4. **学习技能（临时钩子 `setSkillLevel("GearIn_gunnery", 1f)`，见调用点片段）**：日志立即出现
```text
GearIn gunnery stats apply: id=GearIn_gunnery_stats_4 level=1.0 commandPoints=+1 GearIn gunnery fleet apply: id=GearIn_gunnery_stats_3 level=1.0 burn=+2
``` （FLEET 与 CHARACTER_STATS 在 `refreshCharacterStatsEffects` 内即时重放；`_stats_3` / `_stats_4` 与"等级管道"一节的 effect id 组合规则一致。）
5. **stats 重建后 SHIP 效果**：打开舰队界面（F 键，触发成员 stats 重建）或进入一场战斗部署：日志出现
```text
GearIn gunnery ship apply: id=GearIn_gunnery_ship_1 level=1.0 hull=CRUISER damage=+15% aim=+100%
``` （hull 以实际玩家旗舰为准）。战斗内派出战机的载机起飞时出现 `GearIn gunnery fighters apply: id=GearIn_gunnery_ship_fighters_2 ... speed=+10%`。
6. **2 级（精英）**：钩子改为 `setSkillLevel("GearIn_gunnery", 2f)` 后，日志追加 `GearIn gunnery elite apply: id=GearIn_gunnery_ship_5 level=2.0 hull=... damage=+5%`。
7. **技能树（C 键）**：战斗系出现 "Gunnery" 技能行（技能名来自 `skill_data.csv` 的 `name` 列）；tooltip 渲染 `DESCRIPTION` 文本行与各效果的 `getEffectDescription`。fleet total 行不在技能树里，见第 8 条。
8. **整备界面（R 键 refit）**：舰队构成表出现 `Gunnery damage bonus +15%` 行，鼠标悬停显示自定义 tooltip 文本（`getFleetTotals` → 构成表行）。
9. **数值可观察**：舰队面板 burn 提升 2 点（当舰队最慢成员 burn ≥ 8 时）；战斗部署界面指挥点数 +1；玩家旗舰武器伤害倍率提升（`GearIn_artizan` 等装配里实弹 / 能量武器 +15%，精英 +5%），瞄准误差降低。
10. **解除**：`setSkillLevel("GearIn_gunnery", 0f)` 或 `decreaseSkill` 后日志不再出现新 apply 行，burn / 指挥点数 / fleet total 行恢复基线；`unapply` 用同一 id 解除，数值无残留。

静态自查：
- `GearInGunnerySkill` 源码与 fixture 逐字一致（包括 import 顺序与注释）；
- `.skill`、`skill_data.csv`、fixture 资源三处 `GearIn_gunnery` id 一致；
- 没有把工作单元编号写进任何运行时名称（技能 id、类名、包名、常量、日志前缀均不含 C09-S02 变体）；
- 示例只消费既有 GearIn ID 与原版 ID（无新增资源 ID，图标例外见「完整最小例」）。

## 实例驱动故障分析

  1. **技能在技能树里不出现且无任何 ERROR**。触发：写了 `GearIn_gunnery.skill` 但 `skill_data.csv` 忘了加行（或 `id` 拼写不一致）
     - 可观察：加载期日志 `Skipping loading skill [data/characters/skills/GearIn_gunnery.skill], it's not listed in skill_data.csv.`； `getSkillSpec("GearIn_gunnery")` 返回 null
     - 修正：补注册行，三处 id 逐字一致（规则强度第 1 条）。
2. **技能 id 与原版 / 其它 MOD 撞车，自己的版本静默失效**。触发：用了 `gunnery_implants` 之类的通用 id。可观察：加载期日志 `Skipping skill [<id>], was already loaded`（后加载者被跳过），技能显示的是别人的定义。修正：MOD 专属前缀（`GearIn_`），规则强度第 2 条。
  3. **type 与脚本类接口不匹配 → 类型异常**。触发：`.skill` 里 `{"type":"SHIP", "script":"...$LevelFleet"}`（LevelFleet 实现的是 `FleetStatsSkillEffect`）
     - 可观察：`getAsShipEffect()` 强转抛 `ClassCastException`——在 stats 重放 / 起飞 / tooltip 的实例化点报错，而不是加载期
     - 修正：type 与接口族一一对应（规则强度第 3 条）。
  4. **效果类没有无参构造器（或类名拼错）→ "Problem loading class"**。触发：`Level1` 写了 `Level1(float x)` 构造器，或 `script` 类名与包路径不符
       - 可观察：脚本类加载流程 `loadScriptInstance` 的 `newInstance()` 抛 `InstantiationException` / `ClassNotFoundException`
       - 包装为 `RuntimeException "Problem loading class [data.scripts.gearin.campaign.GearInGunnerySkill$Level1]"`
       - 在效果首次被实例化时抛出
     - 修正：只留无参构造器；类名逐字核对（规则强度第 4 条）。
  5. **modifier id 不按引擎传入值使用，解除不生效**。触发：`apply` 里自己写死 `modifyPercent("GearIn_damage", ...)`，`unapply` 用 `unmodify("GearIn_damage")`
     - 可观察：第一次重放后数值残留——引擎重放顺序是"先 unapply 再 apply"，而引擎传入的 id 是 `GearIn_gunnery_ship_1`，与自造 id 对不上，旧条目永远清不掉；多个角色 / 存档叠加后数值膨胀
     - 修正：`apply` / `unapply` 都用传入的 `id`（规则强度第 5 条；稳定 ID 规则见前置知识）。
6. **把 `scope` 当生效范围，效果"没作用到预期对象"**。触发：`.skill` 写 `"scope":"FLEET"` 但 `type` 是 `SHIP`，期待全舰队加成。可观察：只有玩家旗舰（被驾驶舰船）吃到加成，舰队其它船毫无变化；tooltip 的作用范围文本却是 FLEET——文本与行为不一致。修正：生效范围只看 `type`（规则强度第 6 条）。
7. **战机加成"看不到"**。触发：学了技能后在生涯侧读战机 stats 找 `SHIP_FIGHTERS` 效果。可观察：生涯侧战机没有 stats 实体（战机 stats 在战斗内生成），`FighterLaunchBayAPI` 只在起飞时应用；战前看不到属于正常语义。修正：在战斗内观察（规则强度第 7 条）。
8. **精英加成永不生效**。触发：`.skill` 写了 `requiredSkillLevel:2` 的组但没写 `"elite":true`。可观察：UI 加点最多到 1 级（`increaseSkill` 钳制），`level` 恒为 1，2 级门槛组永不进入应用列表。修正：配 `"elite":true`（规则强度第 8 条）。
9. **fleet total 行消失**。触发：两个技能的效果返回相同的 `label` + `value`。可观察：`getFleetTotals()` 按 `label_value` 去重，两行合并成一行，其中一个技能的汇总"消失"（`MutableCharacterStatsAPI`）。修正：label 或 value 区分（规则强度第 9 条）。
  10. **加了技能但 SHIP 效果日志迟迟不出现**。触发：只执行 `setSkillLevel`，不开舰队界面也不部署。可观察：`GearIn gunnery stats apply` / `fleet apply` 立即出现，`ship apply` 要等 `FleetMemberAPI.updateStats` 重建（开舰队界面 / 舰队同步）或战斗舰船构造（部署）。
      - 修正：理解 FLEET / CHARACTER_STATS 即时、SHIP 族随 stats 重建生效的时序（「消费与调用点」小节）。
11. **icon 缺失导致启动失败**。触发：`icon` 列指向不存在的 PNG。可观察：`loadSkills` 末尾 `queueResource(TEXTURE, ...)` 排队纹理，资源加载阶段报错（TEXTURE 无 try/catch），游戏无法正常启动。修正：icon 指向真实存在的纹理；本单元冒烟期间先复用原版图标（规则强度第 10 条）。

## 总结

- **数据三件套**：`skill_data.csv`（注册行：name / order / tier / reqPoints / 角色类型 flags / tags / icon）+ `<id>.skill`（JSON：`governingAptitude` / `elite` / `scope` / `effectGroups`）+ effect 的 `script` 类
  - CSV spec 加载期的 `loadSkills` 汇成 `SkillSpecAPI`，`getSkillSpec(id)` 读取。**RC8 没有 "skills.json"**，技能 JSON 按技能拆成 `.skill` 文件。
- **必须遵守的注册规则**：`.skill` 的 id 必须在 `skill_data.csv` 有行（否则跳过、无报错）；id 全局唯一（撞 id 后加载者被跳过）；effect 的 `type` 必须与接口族匹配（否则强转异常）；脚本类必须无参可实例化（否则 `Problem loading class`）。
- **作用域由 type 决定**：`SHIP` / `ALL_SHIPS_IN_FLEET` / `SHIP_FIGHTERS` / `ALL_FIGHTERS_IN_FLEET` → `ShipSkillEffect`（生涯 `FleetMemberAPI.updateStats`、战斗侧舰船对象创建、起飞 `FighterLaunchBayAPI`）
  - `CHARACTER_STATS` → `CharacterStatsSkillEffect`；`FLEET` → `FleetStatsSkillEffect`（都经 `refreshCharacterStatsEffects` 重放）
  - `GOVERNED_OUTPOST` / `ALL_OUTPOSTS` → `MarketSkillEffect`；`DESCRIPTION` → 纯文本；`HULLMOD_UNLOCK` / `ABILITY_UNLOCK` → 解锁集合
  - `scope` 只是 UI 文本。
- **等级语义**：0 / 1 / 2（2 仅 `elite:true`）；`increaseSkill` 钳制、`setSkillLevel` 不钳制；`requiredSkillLevel` 是门槛；`apply` 的 `level` = `getSkillLevel(governingSkill)`；`effectBasedOnLevel` 只影响 tooltip。
- **effect id 即 modifier id**：`<技能id>_<类型后缀>_<效果序号>`（FLEET 与 CHARACTER_STATS 都是 `_stats_`）；`apply` / `unapply` 必须原样使用（稳定 ID 规则延伸，见前置知识）。
- **fleet total**：效果类实现 `FleetTotalSource.getFleetTotalItem()` → `getFleetTotals()` 按 `label+value` 去重、`sortOrder` 排序 → 整备界面舰队构成表展示；`BaseSkillEffectDescription` 提供原版聚合做法（OP / 自动化点数）。
- **时序**：FLEET / CHARACTER_STATS 在 `refreshCharacterStatsEffects` 即时重放；SHIP 族随成员 stats 重建 / 舰船构造 / 战机起飞应用。

本节建立的"type → 作用域 → 应用入口"模型，逻辑上的延续在生涯数值链的下游：装配侧（`ShipVariantAPI` 代表的舰船装配与 stats 数值来源）与"人物 → 成员 → 装配 → stats"的完整交接——本节的 SHIP / ALL_SHIPS_IN_FLEET 效果正是其中"人物技能到达成员 stats"的关键一跳，这套模型会在生涯域数值链路里被反复消费。

## 建议参考项

- `com.fs.starfarer.api.characters.SkillSpecAPI`：技能规格对象（`getSkillSpec(id)` 的返回类型）；其内部类型 `SkillEffectSpecAPI` 承载 `getType` / `getAsXxxEffect` / `getRequiredSkillLevel` / `getUnlockedHullMods` 等效果字段访问。
- `com.fs.starfarer.api.characters.SkillEffectType`：11 个效果类型枚举
  - `SHIP` / `ALL_SHIPS_IN_FLEET` / `SHIP_FIGHTERS` / `ALL_FIGHTERS_IN_FLEET` / `CHARACTER_STATS` / `FLEET`
  - `HULLMOD_UNLOCK` / `ABILITY_UNLOCK` / `GOVERNED_OUTPOST` / `ALL_OUTPOSTS` / `DESCRIPTION`。
- `com.fs.starfarer.api.characters.ShipSkillEffect` / `FleetStatsSkillEffect` / `CharacterStatsSkillEffect` / `MarketSkillEffect` / `DescriptionSkillEffect`：五个效果接口族，`apply` / `unapply` 的签名所在。
- `com.fs.starfarer.api.characters.LevelBasedEffect`：效果接口的共同父接口，`getEffectDescription` / `getEffectPerLevelDescription` / `getScopeDescription` 与 `ScopeDescription` 枚举。
- `com.fs.starfarer.api.characters.FleetTotalSource` / `FleetTotalItem`：fleet total 行的提供接口与数据对象（`label` / `value` / `sortOrder` / `tooltipCreator`）。
- `com.fs.starfarer.api.characters.MutableCharacterStatsAPI`：`getSkillLevel` / `setSkillLevel` / `increaseSkill` / `hasSkill` / `refreshCharacterStatsEffects` / `getCommandPoints` 所在。
- `com.fs.starfarer.api.fleet.MutableFleetStatsAPI`：`getFleetwideMaxBurnMod` 等舰队 stats 访问器。
- `com.fs.starfarer.api.SettingsAPI`：`getSkillSpec` / `getSortedSkillIds` 读取入口。
- `com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription`：示例各效果类继承的基类（`TOOLTIP_WIDTH` 与 fleet total 聚合做法）。
- `data/characters/skills/skill_data.csv`：技能注册表（id / order / tier / reqPoints / 角色 flags / tags / icon）。
- `data/characters/skills/GearIn_gunnery.skill`：本节技能定义（aptitude / 等级组 / 效果清单）。
- `data/characters/skills/aptitude_data.csv`：能力树分组表（`effect_skill_id` 列）。
- `data/characters/skills/gunnery_implants.skill`：原版 SHIP + 精英组样例（本节示例的蓝本）。
- `tutorial-fixtures/units/C09-S02/src/main/java/data/scripts/gearin/campaign`：`GearInGunnerySkill` 源码目录（页面代码块的磁盘对照）。
- `tutorial-fixtures/units/C09-S02/src/main/resources/data/characters/skills`：`GearIn_gunnery.skill` 与 `skill_data.csv` 数据 fixture。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录（构建命令执行位置）。
- `Starsector/starsector-core/starsector.log`：游戏运行日志（注册与 apply 日志的核对位置）。
