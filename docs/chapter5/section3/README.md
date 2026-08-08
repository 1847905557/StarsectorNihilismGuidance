# 为武器、引擎与界面加入音频

本节把已经准备并注册的音频用于武器、战术系统、界面、环境与音乐。三个声音 ID 会分别进入对应配置或代码入口，并通过一个最小例确认播放结果。

*本节预期阅读时间：~25 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出声音消费层的五个入口族：`.wpn`/`.system` 数据引用、`Global.getSoundPlayer()` 代码调用、hull style 声音集、`terrain.json` 地形循环与 `MusicPlayerPlugin` 音乐选曲；
- 用 `.wpn` 的 `fireSoundOne`/`fireSoundTwo` 把 `GearIn_weapon_fire` 接到武器开火；
- 用 `.system` 的 `loopSound` 或代码 `playLoop` 把 `GearIn_system_loop` 接到战术系统持续音效，说清循环音效 "必须每帧续期、素材首尾无缝" 的语义；
- 用 `playUISound` 播放 UI 音效，并定位 "未注册 ID" 的运行期异常；
- 用自定义 `MusicPlayerPlugin` 或 `playCustomMusic` 把 `GearIn_market_theme` 接入市场音乐；
- 说出地形环境循环的注册与消费机制；
- 从日志与游戏内行为定位五类使用方的常见故障。

## 前置知识

- [C5-S1] 的 OGG 路径、声道、循环边界与响度要求；
- [C5-S2] 的 `sounds.json` 注册、`music` 分类、sound ID 匹配与追加/替换合并规则；
- [C4-S2] 的 hull style 声音字段与声音集分档；
- [C1-S5] 的 `GearIn_durin.wpn` 与 `fireSoundOne`、`fireSoundTwo` 字段；
- [C2-S3] 的战术系统文件、效果脚本类名和脚本路径规则；
- [C7-S1] 的每帧回调与暂停语义；
- [C3-S1] 的测试战役、舰船装配与部署操作。

## 梳理音频播放关系

```text
sounds/.../*.ogg                 资源层 (C5-S1)：三个 OGG
        ↓ 相对于 MOD 根目录的路径
data/config/sounds.json          注册层 (C5-S2)：sound ID → 路径与播放参数
        ↓ sound ID
数据消费者                         代码消费者
  .wpn fireSoundOne/fireSoundTwo   Global.getSoundPlayer().playUISound
  .system useSound/loopSound/        / playSound / playLoop / playCustomMusic
    deactivateSound/outOfUsesSound   (返回 SoundPlayerAPI)
  hull style 9 个 *Set 字段
  terrain.json loopOne..loopFour
  MusicPlayerPlugin 音乐集 ID(music 分类)
```

### 使用方类别速查表

| 使用方类别 | 注册/调用入口 | 运行期使用方 | 本节接入的 ID |
|---|---|---|---|
| 武器开火 | `.wpn` 的 `fireSoundOne`/`fireSoundTwo` | 导弹、实弹与光束武器 | `GearIn_weapon_fire` |
| 武器/导弹位置音效 | 代码 `playSound(id, pitch, volume, loc, vel)` | 位置音效播放器；冲击音播放器 | 代码示例 |
| 战术系统 | `.system` 的 `useSound`/`loopSound`/`deactivateSound`/`outOfUsesSound` | 战斗系统实现 | `GearIn_system_loop` |
| 引擎与舰船风格 | hull style 的 `engineLoopSet`/`engineAccelerateSet` 等 9 个字段 | `ShipEngineControllerAPI`/`ShieldAPI`/`FluxTrackerAPI`/冲击音播放器 | 沿用 [C4-S2] 原版声音集 |
| UI | 代码 `playUISound(id, pitch, volume)`、`playUILoop` | 战役/战斗 UI 各调用点 | `GearIn_weapon_fire`(复用示例)|
| 环境地形 | `data/campaign/terrain.json` 的 `loopOne`..`loopFour`、`musicSuppression`、`custom` | `BaseTerrain.advance` → `playLoop` + `suppressMusic`；地形插件 `stormSound`/`gain`/`gainHF` | 机制说明 |
| 音乐 | `settings.json` 的 `plugins.musicPlugin` 注册选曲插件；代码 `playCustomMusic` | `MusicPlayerPlugin`/`MusicPlayerPluginImpl`、音乐播放器基类 | `GearIn_market_theme` |

## 公共入口：Global.getSoundPlayer()

代码使用方的唯一入口是 `Global.getSoundPlayer()`，返回 `SoundPlayerAPI`(公开 API `com.fs.starfarer.api.SoundPlayerAPI`)：

### 播放方法速查表

| 方法 | 用途 | 位置音效 | 硬性要求 |
|---|---|---|---|
| `playUISound(String id, float pitch, float volume)` | 一次性 UI 音效，无位置 | 否 | 素材建议 stereo("UI sound files should be stereo")|
| `playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel)` | 战斗/大地图位置音效 | 是 | 素材**必须为 mono**("Sound file must be *mono* for it to be properly played within the engine")；距监听点超过 2500 不播放 |
| `playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel)` | 位置循环音效 | 是 | **必须每帧调用**，否则循环淡出；`playingEntity` 必填 (同实体同循环只播一个)；素材建议 mono |
| `playLoop(..., float fadeIn, float fadeOut)` | 同上，指定淡入/淡出秒数 | 是 | 同上 |
| `playUILoop(String id, float pitch, float volume)` | UI 循环音效 | 否 | 每帧续期规则同 `playLoop` |
| `playCustomMusic(int fadeOutIfAny, int fadeIn, String musicSetId)` | 手动播放音乐集 | — | 线程安全；淡入/淡出秒数必须为整数；`null` 停止当前曲目 |
| `playCustomMusic(..., boolean looping)` | 同上，指定是否循环 | — | 线程安全；其余约束同上 |
| `setSuspendDefaultMusicPlayback(boolean)` | 阻止默认选曲自动开始新曲目 | — | 与 `playCustomMusic` 配合 |
| `getCurrentMusicId()` | 当前曲目；无播放时返回字符串 `"nothing"` | — | 线程安全 |
| `restartCurrentMusic()` | 重新开始当前音乐 | — | 线程安全 |
| `pauseMusic()` / `pauseCustomMusic()` / `resumeCustomMusic()` | 暂停或恢复音乐 | — | 无公开线程安全保证 |
| `applyLowPassFilter(float gain, float gainHF)` | 低通滤波 (深渊等场景)| — | — |

一次性播放 (`playUISound`/`playSound`) 返回 `SoundAPI` 句柄，可 `stop()`、`setVolume()`、`setPitch()`、`setLocation()`、`isPlaying()`；`playLoop` 不返回句柄，靠每帧续期控制。

`playUISound`/`playSound` 对 `id == null` 直接返回 `null` 不报错，`playLoop` 同样忽略 `null` ID；引用**未注册 ID** 则两类失败 (武器/循环 NPE 与 UI 抛异常，见故障分析)。

## 使用方一：武器开火与命中音

### .wpn 声音字段速查表

武器规格文件 `data/weapons/*.wpn` 加载时读取以下声音相关字段：

| 字段 | 作用 | 默认 |
|---|---|---|
| `fireSoundOne` | 开火前摇 (chargeup) 播放一次；光束武器为光束起始音 | 无 |
| `fireSoundTwo` | 开火音；光束武器为光束持续循环音 | 无 |
| `playFullFireSoundOne` | 光束/连发被中断时是否播完 `fireSoundOne` | false |
| `stopPreviousFireSound` | 连发时先停止上一次开火音 | false |
| `fadePreviousFireSound` | 连发时上一次开火音的淡出秒数 | 0 |
| `noImpactSounds`/`noShieldImpactSounds`/`noNonShieldImpactSounds` | 关闭命中音 | false |

原版实例 (`Starsector\starsector-core\data\weapons\`)：
- `railgun.wpn` 的 `"fireSoundTwo":"railgun_fire"`；
- `lightmg.wpn` 注释掉的 `fireSoundOne` 与 `"fireSoundTwo":"light_machinegun_fire"`；
- 光束 `autopulse.wpn` 的 `"fireSoundTwo":"autopulse_laser_fire"`(该 ID 在 `sounds.json` 中注册为 4 个差分条目，同一 OGG 不同 pitch)。

`.wpn` 引用的每个 ID 都必须在 `sounds.json` 有对应条目。

### 运行期使用方

- 武器构造时按 `fireSoundOne`/`fireSoundTwo` 取已注册的对应声音集；
- 实弹/导弹：前摇阶段 `playSoundAtLocation` 播 `fireSoundOne`，开火瞬间播 `fireSoundTwo`；`stopPreviousFireSound`/`fadePreviousFireSound` 控制连发重叠；
- 光束：开火时播一次 `fireSoundOne`(`playFullFireSoundOne` 控制中断行为)，持续期间每帧 `playSoundLoop` 播 `fireSoundTwo`—— 所以光束循环音素材必须满足资源层规定的 "首尾无缝" 规格；
- 无弹药开火时周期性播 `gun_out_of_ammo` 声音集 (导弹与实弹武器都会触发，属原版通用声音集)；
- 命中音由冲击音播放器按伤害类型播 `hit_*`/`collision_*`/`explosion_*` 固定声音集 —— 武器侧只有 `noImpactSounds` 等开关，命中音集本身不能在 `.wpn` 里换。

代码位置音效 (非数据引用)：武器特效脚本、导弹 AI 等用 `Global.getSoundPlayer().playSound(id, pitch, volume, loc, vel)`，把声音放在实体位置并随距离衰减。

### 接入示例

把 `GearIn_durin.wpn`(当前 `fireSoundTwo` 指向原版 `light_assault_gun_fire`) 改一行：

```json
	"fireSoundTwo":"GearIn_weapon_fire",
```

ID 与注册层条目完全一致 (大小写敏感)。开火瞬间播放 `GearIn_weapon_fire`；多门同型炮同时开火各自独立播放 (一次性实例，不互相打断，除非开启 `stopPreviousFireSound`)。

::: caution 必须：.wpn 引用的 sound ID 必须已注册

此前已述：`loadSoundSets` 加载期不校验声音引用，错误延迟到战斗期。

对教程中的弹道/导弹武器，`fireSoundTwo` 引用未注册 ID 时静默无声，`fireSoundOne` 引用未注册 ID 时在前摇播放处抛 `NullPointerException`。光束武器的两个声音字段都没有空值守卫，未注册时会在开火处抛 `NullPointerException`。

ID 大小写不一致同样触发。

:::

## 使用方二：战术系统与持续循环 (playLoop)

### .system 声音字段速查表

战术系统文件 `data/shipsystems/*.system` 有四个声音字段 (公开 API `ShipSystemSpecAPI` 提供 `getUseSound`/`getLoopSound`/`getDeactivateSound`/`getOutOfUsesSound`)：

| 字段 | 时机 |
|---|---|
| `useSound` | 系统激活瞬间一次性 |
| `loopSound` | 系统有效期内 (`IN`/`ACTIVE`/`OUT`) 每帧循环 |
| `deactivateSound` | 系统关闭瞬间一次性 |
| `outOfUsesSound` | 系统无充能/次数试图激活时 |

原版实例：`Starsector\starsector-core\data\shipsystems\burndrive.system` (`useSound`:`system_burn_drive_activate`、`loopSound`:`system_burn_drive_loop`、`deactivateSound`:`system_burn_drive_deactivate`、`outOfUsesSound`:`engine_disabled`)，各 ID 均在 `sounds.json` 注册。

运行期使用方是战斗系统实现：构造时解析四个声音集，有效期内每帧 `playSoundLoop` 播 `loopSound`。

`GearIn_burstdrive.system` 从 `burndrive.system` 复制而来，已经带着这四个字段。把 `loopSound` 指向本单元 ID：

```json
	"useSound":"system_burn_drive_activate",
	"loopSound":"GearIn_system_loop",
	"deactivateSound":"system_burn_drive_deactivate",
	"outOfUsesSound":"engine_disabled",
```

`GearIn_system_loop` 是资源层规格的 "必须首尾无缝" 循环素材 —— 这正是 `loopSound` 需要的形态：系统开启期间被每帧重播，素材有接缝就会每轮咔哒一次。

### 代码使用方：playLoop 与循环语义

任何 "持续音效" 也可由代码播放。`SoundPlayerAPI.playLoop` 的注释与运行期行为说明了四条循环语义：

1. **必须每帧调用，否则淡出。**循环管理器每帧把 "本帧未续期" 的循环加入淡出列表 (默认 0.5 秒淡入/淡出)。
2. **`playingEntity` 必填且用于去重。**同一实体对同一循环只保留一个实例 (循环管理器按所属实体合并去重)。不传或每帧传新对象，会失去 "同实体同循环只播一个" 的保证。
3. **距离衰减与上限。**距监听点超过 2500 不播放；活动循环数达到上限时替换最远的一个。
4. **素材建议 mono**(API 注释)，位置循环在空间中有声像。

代码接入形态 (挂到战术系统效果脚本，完整文件见 "完整最小例")：

```java
ShipAPI ship = (ShipAPI) stats.getEntity();
if (ship != null && effectLevel > 0f) {
    Global.getSoundPlayer().playLoop("GearIn_system_loop", ship, 1f,
            effectLevel, ship.getLocation(), ship.getVelocity());
}
```

数据路 (`.system` 的 `loopSound`) 与代码路 (`playLoop`) 二选一即可，两条路都验证 `GearIn_system_loop` 的完整流程；本节最小例用数据路 (改动最少)，代码路作为同一 ID 的第二种消费形态讲解。

### 引擎与风格声音 (同机制的另一族)

GearIn 舰船已接到 `GEAR_TECH` 风格；风格里 9 个 `*Set` 字段是引擎与护盾声音的声音集 ID。消费链：

- `ShipEngineControllerAPI` 构造时按 `engineLoopSet` 取声音集，按 `getSoundSpec(hullStyle, hullSize)` 分档，每帧按引擎出力 `playSoundLoop`；零幅能推进激活瞬间用 `engineAccelerateSet` 播一次性音。
- `ShieldAPI` 用 `shieldRaiseSet`/`shieldLowerSet`；`FluxTrackerAPI` 用 `shieldBurnoutSet`(过载)、`ventFluxSet`(V 排)、`fluxLoopSet`/`empLoopSet` 分档循环；冲击音播放器用 `explosionShipSet`(舰船爆炸)。

声音集分档依赖复合对象：`sounds.json` 中带 `sounds` 数组 + `FIGHTER`/`FRIGATE`/`DESTROYER`/`CRUISER`/`CAPITAL_SHIP` 映射的条目 (复合对象语法在注册层已讲)。

::: caution 必须：hull style 引用的声音集 ID 必须已注册、OGG 必须存在

hull style 里写入自定义声音集 ID 后，该 ID 必须存在于 `sounds.json`。

违反后的可观察结果：ID 未注册 → 加载期不报错，舰船生成引擎控制器时抛 NPE(`starsector.log` 堆栈指向 `ShipEngineControllerAPI`)；声音集内 OGG 缺失 → 启动中止 (`Sound with filename [...] not found or failed to load`，资源与注册层已述)。

:::

本节消费的三个 ID 不含引擎循环：GearIn 舰船的引擎声沿用原版 `engine_loop` 分档集。若要自定义引擎声，沿用同一模式：注册带分档的复合集 (新 ID 的所有权由声明它的页面持有)→ hull style 字段引用 → `.ogg` 存在。

## 使用方三：UI 声音 (playUISound)

`playUISound(id, pitch, volume)` 播放无位置的 UI 音效，适合按钮、提示、结算等一次性事件。UI 素材建议使用 stereo。

原版调用点遍布战役与战斗 UI：`ui_button_pressed`、`ui_smallaction_yes`/`ui_smallaction_no`、`ui_rep_raise`/`ui_rep_drop`、`ui_char_level_up`、`ui_target_reticle`、`cr_playership_warning`(战斗内对用户舰船警告) 等。

规则命令 `PlaySound`(`com.fs.starfarer.api.impl.campaign.rulecmd.PlaySound`) 可播放任意已注册 ID。

接入示例 —— 在 `GearInBurstDriveSystem.apply` 的系统激活瞬间加一次 UI 音效。这演示 "同一已注册 ID 可被多个使用方引用"：`GearIn_weapon_fire` 已接入 `.wpn`，这里作为 UI 用途再消费一次 (差分池与播放互不影响)：

```java
if (state == ShipSystemStatsScript.State.IN && effectLevel < 0.05f) {
    Global.getSoundPlayer().playUISound("GearIn_weapon_fire", 1f, 1f);
}
```

前摇阶段 `effectLevel` 每帧从 0 爬到 1，`effectLevel < 0.05f` 保证只在激活最初几帧触发一次，不会每帧重播。

::: caution 必须：playUISound 引用的 ID 必须已注册

调用瞬间抛 `RuntimeException("Sound set with id [...] not found")`，不是静默失败，也不是加载期错误。

`playSound`/`playLoop` 对未注册 ID 则是 NPE(声音集查找返回空后解引用)。两类失败都在运行期。

:::

另有内部静音开关：标题界面等场景游戏会静音环境音；静音期间播放调用静默返回且不报错，MOD 无需处理。

## 使用方四：环境与地形循环

大地图的环境音由地形驱动，注册与消费都在数据与核心侧：

- `data/campaign/terrain.json` 每个地形条目可声明 `loopOne`/`loopTwo`/`loopThree`/`loopFour`(声音集 ID)、`musicSuppression`(0~1，音乐压低程度) 与 `custom`(插件自定义参数)。
  - 原版实例：`hyperspace` 条目用 `terrain_hyperspace_deep`/`terrain_hyperspace_abyss`/`terrain_hyperspace_storm` 分深度循环、`custom.stormSound` 放雷击音；
  - `magnetic_field` 用 `terrain_magfield`/`terrain_magstorm`；
  - `slipstream` 用 `terrain_slipstream`/`terrain_slipstream_near`。
  - 公开 API `TerrainSpecAPI` 暴露 `getLoopOne()`..`getLoopFour()` 与 `getMusicSuppression()`。
- 消费链 (公开 API 实现 `com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain`)：地形插件每帧检查用户舰队是否在范围内 (含 100 的额外声音半径)。
  - 对每个 loop 字段调 `Global.getSoundPlayer().playLoop(soundId, fleet, pitch, volume, fleet.getLocation(), Misc.ZERO)`；
  - 同时按 `musicSuppression * 接近度` 调 `Global.getSector().getCampaignUI().suppressMusic(...)` 压低音乐。
- 地形插件可用 `custom` 参数：`HyperspaceTerrainPlugin` 读 `stormSound` 用 `playSound` 播雷击、读 `gain`/`gainHF` 用 `applyLowPassFilter` 做深渊低通 (公开 API 实现 `com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin`)。

MOD 接入环境音的路径是自定义地形类型 (实现 `CampaignTerrainPlugin`，在 `terrain.json` 注册新键并在地图数据中放置地形)，成本较高；本节消费的三个 ID 中也没有环境 ID。

本节只交代机制与相关 API(`playLoop`、`applyLowPassFilter`、`suppressMusic`)，这些 API 也适用于地形类音频。

## 使用方五：音乐

### 自动选曲链

音乐不像音效那样按 ID 直接播放，而是由 "选曲插件" 按游戏状态返回音乐集 ID，播放器再按 ID 从 `music` 分类取条目：

1. **注册选曲插件**：`data/config/settings.json` 的 `plugins` 段默认 `"musicPlugin":"com.fs.starfarer.api.impl.MusicPlayerPluginImpl"`。
   - `Misc.getMusicPlayerPlugin()`(公开 API `com.fs.starfarer.api.util.Misc`) 用 `Global.getSettings().getNewPluginInstance("musicPlugin")` 实例化并缓存为单例。
2. **状态令牌**：`MusicPlayerPlugin` 接口 (公开 API) 定义 `getMusicSetIdForCampaignStateToken(token, param)`、`getMusicSetIdForCombat(engine)`、`getMusicSetIdForTitle()`。
   - `MusicPlayerPluginImpl`(公开 API 实现，可直接继承) 覆盖战役状态：`MARKET`/`ENCOUNTER`/`CAMPAIGN_SYSTEM`/`CAMPAIGN_HYPERSPACE`/`PLANET_SURVEY`/`NO_MUSIC`。
3. **市场音乐决策顺序** (`MusicPlayerPluginImpl.getMarketMusicSetId`)：
   - 市场内存键 `$musicSetIdForMission` → 市场内存键 `$musicSetId` → 市场所属势力的 `music` 映射表 (键 `market_friendly`/`market_hostile`/`market_neutral`，另有 `encounter_*` 与 `music_combat_set` 用于遭遇与战斗)→ 默认 `music_default_market_friendly/hostile/neutral`。
   - 星区与深渊另有 `$musicSetId` 内存键与 `music_campaign*` 默认集。
4. **播放**：各状态音乐播放器把返回的音乐集 ID 交给音乐线程播放；`music` 分类条目支持多曲候选 (`files` + `randomStart`，注册层已述)。

势力 `music` 映射把 `market_*`/`encounter_*` 等键映射到 MOD 自己的音乐 ID，这些 ID 在 `sounds.json` 的 `music` 分类注册 (目录型 `source`)。

原版势力同样如此 (`data/world/factions/hegemony.faction` 的 `music` 映射段)。

### 手动播放

`playCustomMusic(fadeOutIfAny, fadeIn, musicSetId)` 直接播放指定音乐集：淡入/淡出秒数必须为整数，`musicSetId` 传 `null` 停止当前曲目；`setSuspendDefaultMusicPlayback(true)` 阻止默认选曲自动开始新曲目，配合手动播放使用。

未注册的音乐集 ID 在同步调用 `playCustomMusic` 时解析为 `null`，NPE 从调用线程抛出。

后台音乐线程的播放路径会捕获异常并记录 warn。

因此**音乐 ID 必须注册在 `music` 分类** —— 普通音效条目不会被音乐播放器识别 (注册层已述)。

### 接入示例

`GearIn_market_theme` 走 "市场音乐" 语义，最稳的接入是继承原版选曲插件并覆写市场分支 (完整文件见 "完整最小例")：

```java
public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {
    @Override
    protected String getMarketMusicSetId(Object param) {
        return "GearIn_market_theme";
    }
}
```

注册 (`data/config/settings.json`，与 `sounds.json` 同为对象键合并，只提交自己的键)：

```json
{
	"plugins":{
		"musicPlugin":"data.scripts.gearin.campaign.GearInMusicPlayerPlugin"
	}
}
```

::: caution 必须：覆写 musicPlugin 键是全局替换

原版与所有 MOD 共用一个选曲插件单例；您的类不继承 `MusicPlayerPluginImpl` 就会丢掉全部原版选曲逻辑 (默认市场/遭遇/星区/战斗/标题音乐全部失效)。继承它并只覆写需要的方法，未覆写的状态与默认集全部保留。

:::

另一种不改插件的接入：由脚本给市场设置内存键 `$musicSetId`(优先级高于势力映射与默认集)，例如 `market.getMemoryWithoutUpdate().set("$musicSetId", "GearIn_market_theme")`。

## 组装音频播放实现

### 接入位置速查表

三个 ID 各接一个使用方 (文件均位于 MOD 根目录下)：

| ID | 接入文件 | 改动 |
|---|---|---|
| `GearIn_weapon_fire` | `data/weapons/GearIn_durin.wpn` | `fireSoundTwo` 改为 `GearIn_weapon_fire` |
| `GearIn_system_loop` | `data/shipsystems/GearIn_burstdrive.system` | `loopSound` 改为 `GearIn_system_loop` |
| `GearIn_market_theme` | `data/config/settings.json` + 新类 `GearInMusicPlayerPlugin` | `plugins.musicPlugin` 指向新类 |

(可选代码路：`GearInBurstDriveSystem` 中每帧 `playLoop` 播 `GearIn_system_loop`—— 数据路与代码路二选一即可，不要同时开。)

`GearIn_burstdrive.system` 的声音段 (其余字段不动)：

```json
	"useSound":"system_burn_drive_activate",
	"loopSound":"GearIn_system_loop",
	"deactivateSound":"system_burn_drive_deactivate",
	"outOfUsesSound":"engine_disabled",
```

`GearIn_durin.wpn` 完整文件 (沿用此前制作的武器文件，只改一行)：

```json
{
	"id":"GearIn_durin",  # this id must match what's in the spreadsheet
	"specClass":"projectile",
	"type":"BALLISTIC",
	"size":"SMALL",
	"displayArcRadius":400,
	"turretSprite":"graphics/weapons/GearIn_durin_turret_base.png",
	"turretGunSprite":"graphics/weapons/GearIn_durin_turret_recoil.png",
	"hardpointSprite":"graphics/weapons/GearIn_durin_hardpoint_base.png",
	"hardpointGunSprite":"graphics/weapons/GearIn_durin_hardpoint_recoil.png",
	"visualRecoil":5.0,  # the gun sprites are only used if this is non-0
	"renderHints":[RENDER_BARREL_BELOW],
	"turretOffsets":[15, 3, 15, -3],
	"turretAngleOffsets":[0, 0],
	"hardpointOffsets":[8, 3, 8, -3],
	"hardpointAngleOffsets":[0, 0],
	"barrelMode":"ALTERNATING", # or LINKED.  whether barrels fire at the same time or alternate.
	"animationType":"MUZZLE_FLASH",  # NONE, GLOW, MUZZLE_FLASH, SMOKE
	"muzzleFlashSpec":{"length":30.0,   # only used if animationType = MUZZLE_FLASH
					   "spread":16.0,
					   "particleSizeMin":8.0,
					   "particleSizeRange":10.0,
					   "particleDuration":0.35,
					   "particleCount":12,
					  "particleColor":[255,155,105,185]},
	"projectileSpecId":"lightag_shot",  # projectile that will be fired
	"fireSoundTwo":"GearIn_weapon_fire",
}
```

`GearInMusicPlayerPlugin`(完整文件，位于 `data/scripts/gearin/campaign/`)：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;

public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {
    @Override
    protected String getMarketMusicSetId(Object param) {
        return "GearIn_market_theme";
    }
}
```

`data/config/settings.json`(完整文件，与 `sounds.json` 一样按对象键合并，只提交 `plugins.musicPlugin` 一个键，不要复制整份原版)：

```json
{
	"plugins":{
		"musicPlugin":"data.scripts.gearin.campaign.GearInMusicPlayerPlugin"
	}
}
```

可选代码路 ——`GearInBurstDriveSystem`(战术系统效果脚本全文，新增 `playLoop` 调用与 `stats.getEntity()` 取船)：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInBurstDriveSystem extends BaseShipSystemScript {

    public static final float SPEED_BONUS = 200f;
    public static final float ACCELERATION_BONUS = 200f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
            stats.getAcceleration().modifyFlat(id, ACCELERATION_BONUS * effectLevel);
        }

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }
        if (effectLevel > 0f) {
            Global.getSoundPlayer().playLoop("GearIn_system_loop", ship, 1f,
                    effectLevel, ship.getLocation(), ship.getVelocity());
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("engine power increased", false);
        }
        return null;
    }
}
```

`stats.getEntity()` 返回 `CombatEntityAPI`(公开 API `MutableShipStatsAPI.getEntity()`)，对舰船系统就是部署中的船；

`effectLevel > 0` 覆盖 `IN`/`ACTIVE`/`OUT` 的有效期，与 `.system` 数据路的播放窗口一致。

### 验证方案

前置数据：三个 OGG[C5-S1]、`data/config/sounds.json`[C5-S2]、`data/weapons/GearIn_durin.wpn`[C1-S5]、`data/shipsystems/GearIn_burstdrive.system`[C2-S3]、测试战役 `GearIn_test`[C3-S1]、本节的 `settings.json` 与新类 `GearInMusicPlayerPlugin`。

1. 启用 `gear_in` 启动到主菜单，检查 `Starsector\starsector-core\starsector.log` 中的声音注册记录。
2. 进入 `GearIn_test` 测试战役，使用装有 `GearIn_durin` 的舰船开火，听取武器开火音。
3. 激活 `GearIn_burstdrive`，听取系统有效期间的循环音与结束后的淡出；接入 UI 示例时，同时听取激活瞬间的 UI 音。
4. 进入任意生涯市场，听取市场音乐；离开市场后继续听取音乐变化。

预期结果：

- 启动：`starsector.log` 出现 `Loading sound sets`，无 `sounds.json` 解析、合并或 OGG 加载错误；`settings.json` 合并后 `musicPlugin` 指向 `GearInMusicPlayerPlugin`(无日志行，用行为验证)。
- 模拟战 (测试战役 `GearIn_test`，或控制台 `Addship GearIn_artizan` 装配 `GearIn_durin` 后部署)：都林自动炮开火播放 `GearIn_weapon_fire`(不再是原版 `light_assault_gun_fire`)；激活燃烧驱动后持续播放 `GearIn_system_loop`，系统结束后淡出；若加了 UI 示例，激活瞬间播放一次 `GearIn_weapon_fire` 的 UI 音。
- 生涯：进入任意市场，市场音乐切换为 `GearIn_market_theme`；离开市场恢复当前状态的默认音乐。
- 引擎/护盾声音仍为原版声音集 (本节未改风格声音，`GearIn_system_loop` 只服务战术系统)。

## 故障分析

- **开火完全无声，但其他音效正常**：依次核对 `.wpn` 的 `fireSoundTwo`、`sounds.json` 条目和 OGG 路径。三处 ID 区分大小写；修正后重新开火，应能听到注册声音。
- **开火瞬间抛出 NPE**：弹道/导弹武器优先核对 `fireSoundOne`；光束武器核对两个声音字段。同步注册对应 ID，或改回已存在的原版 ID。
- **系统激活时没有循环声**：四个 `.system` 声音字段都有空值守卫；`loopSound` 留空时循环声静默。核对 `loopSound` 与循环素材；两种声音都缺失时，同时核对 `useSound`。
- **系统激活时因声音配置抛出 NPE**：`.system` 的 `loopSound` 指向未注册 ID，系统实现构造时取得了空值。补齐声音注册后重新激活系统。
- **`playLoop` 不响或断断续续**：每帧没有续播、`playingEntity` 不稳定、距离超过 2500 或 `volume <= 0` 都会中断循环。把调用放进每帧回调，并持续传入同一个实体后复验。
- **`playUISound` 报 `Sound set with id [...] not found`**：声音 ID 未注册或大小写不一致。核对注册条目后再次触发 UI 声音。
- **部署舰船时在 `ShipEngineControllerAPI` 抛出 NPE**：hull style 引用了未注册的引擎声音集。本节三个声音 ID 均不用于引擎，出现该堆栈时直接检查风格文件。
- **市场音乐仍是原版**：确认 `settings.json` 的 `plugins.musicPlugin` 已合并为正确类名，类继承 `MusicPlayerPluginImpl`，并检查市场 `$musicSetId` 是否覆盖了选曲结果。修正后重新进入市场。
- **`playCustomMusic` 后没有音乐**：音乐 ID 必须注册在 `music` 分类，普通音效条目不会被音乐播放器识别。补齐分类后使用整数 fade 参数再次播放。
- **`playCustomMusic` 后日志出现 NPE**：音乐 ID 没有解析到有效音乐条目。核对 `music` 分类和 ID 大小写；需要阻止默认音乐立即换回时，再设置 `setSuspendDefaultMusicPlayback(true)`。

## 总结

本节把声音链的最后一层 —— 消费层 —— 补齐：`GearIn_weapon_fire` 接入武器开火 (`.wpn` 数据引用)、`GearIn_system_loop` 接入战术系统 (`.system` 的 `loopSound` 或代码 `playLoop`)、`GearIn_market_theme` 接入市场音乐 (选曲插件)，并交代了引擎声音集、UI 与地形循环其余三类使用方；

三个 ID 与资源与注册层声明逐字一致，完整流程走通。

所有 ID 引用必须指向已注册条目，未注册的后果全部在运行期 (武器/系统 NPE、UI 抛 `Sound set with id not found`、音乐播放 NPE)，加载期不校验引用。

同一套 "注册 → 引用 → 素材存在" 的完整流程可平移到任何新声音 —— 自定义引擎声沿用 "注册分档复合集 → hull style 引用 → `.ogg` 存在"，自定义地形环境音沿用 "`terrain.json` 注册 → 地形插件播放"。

排查无声故障始终从注册存在、路径存在、ID 一致三件事入手，打包 MOD 时保持 `sounds/` 资源、`sounds.json` 条目、数据字段与代码调用四者一致。

## 建议参考项

公开 API(java 包路径)：

- `com.fs.starfarer.api.SoundPlayerAPI`：全部播放方法签名与 mono/stereo、每帧续期、整数 fade、`"nothing"` 返回值等约束。
- `com.fs.starfarer.api.SoundAPI`：一次性播放返回的句柄 (`stop`/`setVolume`/`setPitch`/`setLocation`/`isPlaying`)。
- `com.fs.starfarer.api.Global`：`getSoundPlayer()` 代码使用方入口。
- `com.fs.starfarer.api.MusicPlayerPlugin`：选曲插件接口 (状态令牌方法)。
- `com.fs.starfarer.api.impl.MusicPlayerPluginImpl`：可直接继承的默认选曲实现 (市场/星区/遭遇/深渊决策顺序与默认集)。
- `com.fs.starfarer.api.util.Misc`：`getMusicPlayerPlugin()`(`settings.json` 的 `musicPlugin` 键实例化并缓存单例)。
- `com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain`：地形循环 `playLoop` 与 `suppressMusic` 消费点。
- `com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin`：`stormSound`、`gain`/`gainHF` 与 `applyLowPassFilter`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.PlaySound`：规则命令播放任意已注册 ID。
- `com.fs.starfarer.api.combat.ShipSystemSpecAPI`：`getUseSound`/`getLoopSound`/`getDeactivateSound`/`getOutOfUsesSound`。
- `com.fs.starfarer.api.loading.TerrainSpecAPI`：`getLoopOne()`..`getLoopFour()`、`getMusicSuppression()`。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：`getEntity()`(系统脚本取船)。

原版数据 (位于 `Starsector/starsector-core/data` 下)：

- `weapons/railgun.wpn`、`weapons/lightmg.wpn`、`weapons/autopulse.wpn`：`fireSoundOne`/`fireSoundTwo` 写法实例。
- `shipsystems/burndrive.system`：系统四个声音字段实例。
- `config/sounds.json`：`system_burn_drive_loop`、`autopulse_laser_fire`、`gun_out_of_ammo` 与 `music` 分类条目。
- `campaign/terrain.json`：地形循环字段与 `custom` 参数。
- `config/settings.json`：`plugins.musicPlugin` 注册。
- `world/factions/hegemony.faction`：势力 `music` 映射。

MOD 侧文件 (相对 MOD 根目录)：

- `data/config/settings.json`：`plugins.musicPlugin` 注册 (本节消费配置)。
- `data/scripts/gearin/campaign/GearInMusicPlayerPlugin`：市场音乐选曲插件 (完整例)。
- `data/weapons/GearIn_durin.wpn`：`fireSoundTwo` 武器开火接入。
- `data/shipsystems/GearIn_burstdrive.system`：`loopSound` 战术系统接入。
