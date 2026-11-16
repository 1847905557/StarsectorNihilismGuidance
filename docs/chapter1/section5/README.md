# 制作火炮、光束与导弹

关于 武器到底是什么、该如何在原版武器库中立足的位置 的解析，哪怕用上整个本教程的篇幅，大概也是不可能写完的。

但好在本教程不需要解释这些。本节一次做出火炮、光束与导弹，覆盖原版武器的基本形态。

本节明显长于此前几节。编者在此假定您已经熟悉了 "复制原版、逐项修改" 的节奏，毕竟您也该掌握了。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C1-S2] 的 CSV 规则与复制原版行的做法，`weapon_data.csv` 沿用同样的流程；
- [C1-S3] 的联队蓝图标签 `GearIn_bp`，武器的 `tags` 沿用它；
- [C1-S2] 的 Console Commands，本节用它把武器加入货舱测试。

## 准备贴图

武器的贴图和舰船略有不同。

**武器按安装的槽位分为 `炮塔` 和 `固定槽位` 两种形态，各需要一套贴图。** 固定槽位的贴图通常更细更长；炮塔武器的旋转中心在贴图中央，固定槽位武器的旋转中心则偏向贴图底部。

**除武器主体外，还有 `炮管`、`光效`、`底座` 三种可选组件。** 炮管在开火时缩退，光效在开火时发亮，底座渲染在主体下方。绝大多数武器至少有炮管或光效中的一种。

本例制作一门带炮管的小型火炮、一门带光效的中型光束，以及一门只有主体的大型导弹：

![都林自动炮的炮塔主体贴图](./GearIn_durin_turret_base.png 'GearIn_durin_turret_base.png')

![都林自动炮的炮塔炮管贴图](./GearIn_durin_turret_recoil.png 'GearIn_durin_turret_recoil.png')

![都林自动炮的固定槽位主体贴图](./GearIn_durin_hardpoint_base.png 'GearIn_durin_hardpoint_base.png')

![都林自动炮的固定槽位炮管贴图](./GearIn_durin_hardpoint_recoil.png 'GearIn_durin_hardpoint_recoil.png')

![乌里光束炮的炮塔主体贴图](./GearIn_uri_turret_base.png 'GearIn_uri_turret_base.png')

![乌里光束炮的炮塔光效贴图](./GearIn_uri_turret_glow.png 'GearIn_uri_turret_glow.png')

![乌里光束炮的固定槽位主体贴图](./GearIn_uri_hardpoint_base.png 'GearIn_uri_hardpoint_base.png')

![乌里光束炮的固定槽位光效贴图](./GearIn_uri_hardpoint_glow.png 'GearIn_uri_hardpoint_glow.png')

![德瓦林发射架的炮塔主体贴图](./GearIn_dwalin_turret_base.png 'GearIn_dwalin_turret_base.png')

![德瓦林发射架的固定槽位主体贴图](./GearIn_dwalin_hardpoint_base.png 'GearIn_dwalin_hardpoint_base.png')

相信您大概也看得出来它们是从哪些原版武器改的。

## 命名并放入贴图

三门武器分别命名为 `都林自动炮 (Durin)`、`乌里光束 (Uri)` 和 `德瓦林 发射架 (Dwalin)`，ID 为 `GearIn_durin`、`GearIn_uri` 和 `GearIn_dwalin`。

贴图文件名在 ID 后依次追加形态和组件：

- 形态：炮塔加 `_turret`，固定槽位加 `_hardpoint`；
- 组件：主体加 `_base`，炮管加 `_recoil`，光效加 `_glow`，底座加 `_under`。

例如都林的炮塔炮管为 `GearIn_durin_turret_recoil.png`。**听上去有些复杂，但见过例子之后只需要不断地复制粘贴即可。** 这套命名是原版的惯例而非强制，游戏按 `.wpn` 中写明的路径读取贴图。

把全部贴图放入 `graphics/weapons/`：

![新武器贴图在武器目录中的位置](./weapons.png 'weapons.png')

## 制作武器文件

舰船编辑器也有武器编辑模式，但**编者非常不推荐使用它**，它难以上手且操作不便。找一门与目标相近的原版武器，复制它的 `.wpn` 再修改，要快得多。

原版武器文件位于 `starsector-core/data/weapons/`，MOD 的武器文件放在 `data/weapons/`。

### 火炮：都林自动炮

编者设想的都林是一门发射高爆弹药的近距离速射炮，大致是 `轻型突击炮` 的低配版本。

复制原版 `lightag.wpn`，重命名为 `GearIn_durin.wpn`。修改 `id`、四张贴图路径和开火点，得到：

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
	"projectileSpecId":"GearIn_durin_shot",  # projectile that will be fired
	"fireSoundTwo":"light_assault_gun_fire",
}
```

都林是双管炮，所以开火点从一个变为两个。`turretOffsets` 每两个数字表示一个开火点坐标，`turretAngleOffsets` 每个数字对应一个开火点的角度偏移，两者的个数必须匹配。**坐标原点是武器的旋转中心，第一个数字沿炮口方向为正，第二个数字以贴图左侧为正。**

`projectileSpecId` 在复制时还是 `lightag_shot`，即直接借用轻型突击炮的弹体。上面已经改成了下文才会制作的 `GearIn_durin_shot`；若您按顺序操作，可以先保留原值。

### 光束：乌里光束

编者设想的乌里是一门远距离能量光束，大致是 `引力子束` 的能量伤害版本。

复制原版 `gravitonbeam.wpn`，重命名为 `GearIn_uri.wpn`。除了 `id`、贴图和开火点，再改掉三种光束颜色：

```json
{
	"specClass":"beam",
	"id":"GearIn_uri",
	"type":"ENERGY",
	"size":"MEDIUM",
	"displayArcRadius":550,
	"turretSprite":"graphics/weapons/GearIn_uri_turret_base.png",
	"turretGlowSprite":"graphics/weapons/GearIn_uri_turret_glow.png",
	"hardpointSprite":"graphics/weapons/GearIn_uri_hardpoint_base.png",
	"hardpointGlowSprite":"graphics/weapons/GearIn_uri_hardpoint_glow.png",
	"turretOffsets":[15, 0],
	"turretAngleOffsets":[0],
	"hardpointOffsets":[23, 0],
	"hardpointAngleOffsets":[0],
	"fringeColor":[135,175,135,255],
	"coreColor":[200,255,225,255],
	"glowColor":[25,255,215,255],
	"width":20.0,
	"textureType":ROUGH,
	"textureScrollSpeed":260.0,
	"pixelsPerTexel":5.0,
	"pierceSet":[PROJECTILE_FF,PROJECTILE_NO_FF,PROJECTILE_FIGHTER,MISSILE_FF,MISSILE_NO_FF],
	#"fireSoundOne":"graviton_beam_intro",
	"fireSoundTwo":"graviton_beam_loop",
}
```

::: warning 删去专属特效行

原版文件在 `id` 之后还有一行 `beamEffect`，指向引力子束专属的特效类，用于推开目标并干扰护盾。乌里是普通光束，不需要这个效果，直接删去这一行。

:::

### 导弹：德瓦林 发射架

编者设想的德瓦林是一种远距离持续发射高爆导弹的武器，大致是 `暴风 MLRS` 的高爆版本。

复制原版 `squall.wpn`，重命名为 `GearIn_dwalin.wpn`。德瓦林没有底座，所以删去 `turretUnderSprite` 与 `hardpointUnderSprite` 两行，发射口从五个减为两个：

```json
{
	"id":"GearIn_dwalin",
	"specClass":"projectile",
	"type":"MISSILE",
	"size":"LARGE",
	"turretSprite":"graphics/weapons/GearIn_dwalin_turret_base.png",

	"hardpointSprite":"graphics/weapons/GearIn_dwalin_hardpoint_base.png",

	"hardpointOffsets":[26, 7, 26, -1],
	"turretOffsets":[14, 6, 14, -2],
	#"hardpointAngleOffsets":[0, 0],
	#"turretAngleOffsets":[0, 0],
	"hardpointAngleOffsets":[0, 0],
	"turretAngleOffsets":[0, 0],
	"barrelMode":"ALTERNATING",
	"animationType":"SMOKE",
	"renderHints":[RENDER_LOADED_MISSILES],
	"interruptibleBurst":false,
	"smokeSpec":{"particleSizeMin":10.0,
							 "particleSizeRange":14.0,
							 "cloudParticleCount":3,
							 "cloudDuration":1.5,
							 "cloudRadius":12.0,
							 "blowbackParticleCount":3,
							 "blowbackDuration":0.45,
							 "blowbackLength":20.0,
							 "blowbackSpread":6.0,
							 "particleColor":[190,180,160,95]},
	"projectileSpecId":"GearIn_dwalin_rocket",
	"fireSoundTwo":"squall_fire",
}
```

原版 `squall.wpn` 的 `"cloudRadius":12.0;` 末尾错打成了分号，游戏照样能读。复制时顺手改成逗号即可。

### 常用 .wpn 字段

三份文件里需要理解的字段如下。表中没有的字段，照抄原版即可；需要特殊效果时，去原版中找一门有这种效果的武器，看它多写了什么。

| 字段                                   | 含义                                                                                       |
| -------------------------------------- | ------------------------------------------------------------------------------------------ |
| `id`                                   | 武器 ID，与 `weapon_data.csv` 的 `id` 一致                                                 |
| `specClass`                            | 射弹武器为 `projectile`，光束为 `beam`；导弹也属于 `projectile`                            |
| `type`                                 | 槽位类型：`BALLISTIC`、`ENERGY`、`MISSILE`；装饰武器为 `DECORATIVE`                        |
| `size`                                 | `SMALL`、`MEDIUM` 或 `LARGE`                                                               |
| `turretSprite` 等                      | 各形态各组件的贴图路径，按需填写，没有的组件删去对应行                                     |
| `visualRecoil`                         | 炮管缩退距离，大于 0 时才使用炮管贴图                                                      |
| `renderHints`                          | 渲染方式，如 `RENDER_BARREL_BELOW` 炮管在主体下方、`RENDER_LOADED_MISSILES` 显示装填的导弹 |
| `turretOffsets` 等                     | 炮塔与固定槽位的开火点及其角度偏移                                                         |
| `barrelMode`                           | 多个开火点的射击方式：`LINKED` 齐射、`ALTERNATING` 轮流                                    |
| `animationType`                        | 开火特效：`NONE`、`GLOW`、`MUZZLE_FLASH`、`GLOW_AND_FLASH`、`SMOKE`                        |
| `projectileSpecId`                     | 射弹武器发射的弹体 ID                                                                      |
| `fringeColor`、`coreColor`             | 光束边缘与核心的颜色                                                                       |
| `glowColor`                            | 光效贴图在开火时的颜色                                                                     |
| `width`                                | 光束宽度                                                                                   |
| `textureScrollSpeed`、`pixelsPerTexel` | 光束材质的滚动速度与拉伸程度                                                               |
| `fireSoundOne`                         | 开火前摇或光束渐入时播放一次的声音                                                         |
| `fireSoundTwo`                         | 开火声；光束为持续期间循环播放的声音                                                       |

颜色写作 `[R,G,B,A]`，四个数字均为 0–255。声音字段填的是声音 ID，自制声音的注册见 [C5-S1]。

## 填写武器数据

**能复制粘贴就绝不硬写。** 新建 MOD 的 `data/weapons/weapon_data.csv`，从原版同名文件复制列名行和三门原版武器的行，逐项修改：

```csv
name,id,tier,rarity,base value,range,damage/second,damage/shot,emp,impact,turn rate,OPs,ammo,ammo/sec,reload size,type,energy/shot,energy/second,chargeup,chargedown,burst size,burst delay,min spread,max spread,spread/shot,spread decay/sec,beam speed,proj speed,launch speed,flight time,proj hitpoints,autofireAccBonus,extraArcForAI,hints,tags,groupTag,tech/manufacturer,for weapon tooltip>>,primaryRoleStr,speedStr,trackingStr,turnRateStr,accuracyStr,customPrimary,customPrimaryHL,customAncillary,customAncillaryHL,noDPSInTooltip,number
都林自动炮,GearIn_durin,1,,300,600,,35,,3,40,4,,,,HIGH_EXPLOSIVE,35,,0,0.14,1,,0,5,1,5,,800,,,30,,,,"he6, GearIn_bp",,,,反装甲,,,,,,,,,,3
乌里光束,GearIn_uri,2,,1500,1000,175,,,,15,9,,,,ENERGY,,200,0.1,0.1,,,,,,,2400,,,,,,,,"beam12, energy11, LR, GearIn_bp",,,,压制,,,,,,,,,,21
德瓦林 发射架,GearIn_dwalin,2,,4000,2500,,600,,15,20,20,100,,,HIGH_EXPLOSIVE,0,,0,10,10,0.5,0,0,0,0,,600,100,4.75,300,,,"DO_NOT_AIM, CONSERVE_3, STRIKE, USE_VS_FRIGATES, MISSILE_SPREAD","missile19, strike15, GearIn_bp",,,,压制,非常快,特殊,,,瞄准后失去制导能力。,,,,,16.2
```

**一门武器最重要的特征同样是要有趣。**

都林作为轻型突击炮的低配版本，射程少了 100，装配点也更低。仅凭这点无法抵消射程的劣势，所以它获得了极高的射速。射速提高了持续输出，于是单发伤害从 40 降到 35，限制它对装甲的打击能力；每发幅能从 30 提高到 35，作为高射速与低装配点的代价。都林由此成为一门在近距离持续倾泻弹药的武器，适合压制，也更依赖舰船的幅能性能。

乌里的射程与持续伤害大致相当于两门 `战术激光炮`。它的装配点足以支撑双倍的 DPS，编者又把伤害往上提了一些，再用稍高的幅能作为代价。原版引力子束的 `impact` 与额外说明文本都已删去。

大型导弹并不好设计，要设计得有趣就更难了：发射次数与单发效果必须一起考虑。德瓦林作为高爆版暴风，单发伤害提高到能有效打击装甲，单波发射量和总备弹量则相应降低，与改过的贴图匹配。EMP 保持为空，这有助于提高设计的**纯粹性**。

`tags` 中的原版蓝图标签 (如 `lowtech_bp`) 都换成了 `GearIn_bp`，理由与 [C1-S3] 的联队相同。不希望大量出现的武器和内置武器可以清空 `tags`。

| 列               | 含义                                                                   |
| ---------------- | ---------------------------------------------------------------------- |
| `tier`           | 科技等级，0–3；越高越稀有，购买所需关系越高                            |
| `rarity`         | 抽选权重，留空为默认                                                   |
| `base value`     | 价格                                                                   |
| `range`          | 射程；导弹的这一列只是 AI 理解中的射程                                 |
| `damage/second`  | 光束的每秒伤害                                                         |
| `damage/shot`    | 射弹武器的单发伤害                                                     |
| `emp`            | 射弹为单发 EMP，光束为每秒 EMP                                         |
| `impact`         | 冲击力，影响命中时推动目标的程度                                       |
| `turn rate`      | 武器转速，度/秒                                                        |
| `OPs`            | 装配点数                                                               |
| `ammo`           | 备弹量，留空为无限                                                     |
| `ammo/sec`       | 每秒恢复的弹药，留空为不恢复                                           |
| `reload size`    | 每次恢复的弹药数                                                       |
| `type`           | 伤害类型：`KINETIC`、`HIGH_EXPLOSIVE`、`ENERGY`、`FRAGMENTATION`       |
| `energy/shot`    | 射弹武器每发产生的幅能                                                 |
| `energy/second`  | 光束每秒产生的幅能                                                     |
| `chargeup`       | 射弹为开火前摇，光束为渐入时间                                         |
| `chargedown`     | 射弹为两次开火的间隔，光束为渐出时间                                   |
| `burst size`     | 每轮发射数；光束填写后变为爆发光束，此列为爆发时长                     |
| `burst delay`    | 一轮内每发的间隔；爆发光束为冷却时长                                   |
| `min spread` 等  | `min spread`、`max spread`、`spread/shot`、`spread decay/sec` 控制散布 |
| `beam speed`     | 光束延伸速度                                                           |
| `proj speed`     | 弹速，导弹为最大速度                                                   |
| `launch speed`   | 导弹初速                                                               |
| `flight time`    | 导弹飞行时间；导弹的实际射程约为弹速乘以飞行时间                       |
| `proj hitpoints` | 弹体结构，对导弹有意义                                                 |
| `hints`          | AI 使用提示，见下文                                                    |
| `tags`           | 自动装配优先级与蓝图标签，见下文                                       |
| `primaryRoleStr` | 武器用途的显示文字                                                     |
| `speedStr`       | 导弹速度的显示文字                                                     |
| `trackingStr`    | 导弹制导能力的显示文字                                                 |
| `customPrimary`  | 武器说明框中的额外文字，`customPrimaryHL` 为其中的高亮部分             |

## 制作弹体

射弹武器借用原版弹体固然简单，但大小、颜色都没法自定义。原版弹体位于 `starsector-core/data/weapons/proj/`，MOD 的弹体放在 `data/weapons/proj/`。

### 都林的弹体

编者认为都林的弹体应当是红色的，其余与轻型突击炮的弹体相差不大。复制 `lightag_shot.proj`，重命名为 `GearIn_durin_shot.proj`，修改 `id` 和颜色：

```json
{
	"id":"GearIn_durin_shot",
	"specClass":"projectile",
	"spawnType":"BALLISTIC",
	"collisionClass":"PROJECTILE_FF",
	"collisionClassByFighter":"PROJECTILE_FIGHTER",
	"length":30.0,
	"hitGlowRadius":40,
	"width":5.0,
	"fadeTime":0.2,
	#"fringeColor":[255,125,125,255],
	#"coreColor":[255,200,200,150],
	"fringeColor":[255,70,70,235],
	"coreColor":[255,200,200,180],
	"textureScrollSpeed":64.0,
	"pixelsPerTexel":5.0,
	"bulletSprite":"graphics/missiles/shell_large_yellow.png"
}
```

然后把 `GearIn_durin.wpn` 的 `projectileSpecId` 改为 `GearIn_durin_shot`。

![都林自动炮更换红色弹体后的战斗效果](./bullet_change.png 'bullet_change.png')

原版弹体的颜色大致与伤害类型对应：动能偏白，高爆偏黄，能量偏蓝，破片偏红。都林是高爆武器，用红色是编者有意为之；您的选择则会影响玩家能否一眼认出伤害类型。

| 字段                       | 含义                                                          |
| -------------------------- | ------------------------------------------------------------- |
| `specClass`                | 普通弹体为 `projectile`，导弹为 `missile`                     |
| `spawnType`                | 弹体形态，原版火炮多为 `BALLISTIC`，等离子炮为 `PLASMA`       |
| `collisionClass`           | 碰撞类型，`PROJECTILE_FF` 会误伤友军，`PROJECTILE_NO_FF` 不会 |
| `collisionClassByFighter`  | 战机发射时使用的碰撞类型，一般为 `PROJECTILE_FIGHTER`         |
| `length`、`width`          | 弹体长宽                                                      |
| `hitGlowRadius`            | 命中光效大小                                                  |
| `fringeColor`、`coreColor` | 弹体边缘与核心的颜色                                          |
| `bulletSprite`             | 弹体贴图                                                      |

### 德瓦林的导弹

::: caution 新导弹武器必须使用新的导弹弹体

`weapon_data.csv` 中导弹武器的 `flight time`、`proj hitpoints` 等列会写入它所引用的弹体。德瓦林若与原版暴风共用 `squall_rocket`，两行就会往同一个弹体写数值，后加载的一方覆盖先加载的一方，两门武器的射程和导弹结构随之互相污染。

:::

编者认为暴风的导弹太小，颜色也不合适。复制 `squall_rocket.proj`，重命名为 `GearIn_dwalin_rocket.proj`，修改 `id`、贴图和颜色：

```json
{
	"id":"GearIn_dwalin_rocket",
	"specClass":"missile",
	#"missileType":"ROCKET",
	"missileType":"MISSILE_TWO_STAGE_SECOND_UNGUIDED",
	"sprite":"graphics/missiles/GearIn_dwalin_rocket.png",
	"size":[6,20],
	"center":[3,10],
	"collisionRadius":15,
	"collisionClass":"MISSILE_NO_FF",
	"explosionColor":[255,200,200,235],  # purely visual, will get a white additively blended core on top of this color
	"explosionRadius":85, # purely visual
	"flameoutTime":1, # total time from flameout to full fadeout
	"noEngineGlowTime":0.5, # time spent without the engine glow being rendered
	"fadeTime":0.5, # fadeout duration
	"engineSpec":{"turnAcc":360,
				  "turnRate":180,
				  "acc":1000,
				  "dec":0},
	"engineSlots":[{"id":"ES1",
					"loc":[-9, 0],
				    "style":"CUSTOM",
					"styleSpec":{
						"mode":"QUAD_STRIP", # PARTICLES or QUAD_STRIP, determines which params are used
						"engineColor":[255,200,200,255],
						"contrailDuration":.5,
						"contrailWidthMult":2,
						"contrailWidthAddedFractionAtEnd":2,  # can be negative. makes trail spread out/narrow at the end
						"contrailMinSeg":5, # min segment length, in pixels
						"contrailMaxSpeedMult":.0f,
						"contrailAngularVelocityMult":0.5f,
						"contrailSpawnDistMult":0f, # how far away from engine it starts
						"contrailColor":[75,75,75,150],
						"type":"SMOKE" # GLOW or SMOKE; additive or regular blend mode
					},
				    "width":8.0,
				    "length":50.0,
				    "angle":180.0}]
}
```

原版文件在 `missileType` 之后还有一行 `onFireEffect`，指向暴风专属的开火特效类。德瓦林不需要它，同样删去。

临时赶工做一份导弹贴图，放入 `graphics/missiles/`，这个目录约定俗成用来存放各类弹体贴图：

![德瓦林发射架的导弹弹体贴图](./GearIn_dwalin_rocket.png 'GearIn_dwalin_rocket.png')

最后把 `GearIn_dwalin.wpn` 的 `projectileSpecId` 改为 `GearIn_dwalin_rocket`。

![德瓦林发射架更换导弹弹体后的战斗效果](./missile_change.png 'missile_change.png')

| 字段              | 含义                                                                                                   |
| ----------------- | ------------------------------------------------------------------------------------------------------ |
| `missileType`     | 导弹行为，如 `MISSILE` 制导导弹、`ROCKET` 无制导火箭、`MISSILE_TWO_STAGE_SECOND_UNGUIDED` 先制导后直飞 |
| `sprite`          | 导弹贴图；导弹不读取 `bulletSprite`                                                                    |
| `size`、`center`  | 导弹尺寸与中心，与贴图尺寸不同时会拉伸                                                                 |
| `collisionRadius` | 导弹的碰撞圈                                                                                           |
| `explosionColor`  | 爆炸光效颜色，纯视觉                                                                                   |
| `explosionRadius` | 爆炸光效大小，纯视觉，不影响伤害范围                                                                   |
| `flameoutTime`    | 飞行时间耗尽后，从熄火到完全消失的时长；熄火后导弹仍凭惯性飞行并能造成伤害                             |
| `engineSpec`      | 导弹机动性：`turnAcc` 转向加速度、`turnRate` 转向速度、`acc` 加速度、`dec` 减速度                      |
| `engineSlots`     | 导弹的引擎尾焰                                                                                         |

开火特效与命中特效需要代码，见 [C2-S4] 与 [C8-S3]。

## 武器的 hints 与 tags

`hints` 告诉 AI 如何使用这门武器。常用取值：

| hint                      | 作用                                       |
| ------------------------- | ------------------------------------------ |
| `PD`                      | 点防御武器，自动优先瞄准导弹               |
| `PD_ONLY`                 | 与 `PD` 并用，除非手动操作，否则不瞄准舰船 |
| `ANTI_FTR`                | AI 视其为反战机武器                        |
| `STRIKE`                  | 打击武器，AI 谨慎使用且默认不用于护卫舰    |
| `USE_VS_FRIGATES`         | 与 `STRIKE` 并用，允许用于护卫舰           |
| `DO_NOT_AIM`              | AI 认为它能全向追踪，不考虑射角直接开火    |
| `CONSERVE_1`–`CONSERVE_5` | 有限弹药武器保留相应轮数的弹药             |
| `DO_NOT_CONSERVE`         | 有限弹药武器不保留弹药                     |
| `MISSILE_SPREAD`          | 导弹在接近目标时散开                       |
| `HEATSEEKER`              | 导弹持续追踪目标                           |
| `SYSTEM`                  | 不出现在数据库中，也不会在战后被打捞       |

`tags` 中的 `kinetic`、`he`、`energy`、`missile`、`strike`、`rocket`、`pd`、`beam` 等后接数字，是自动装配的分类与优先级：同类中数字越大越优先，每门武器只写一个这样的标签。`SR` 与 `LR` 让自动装配倾向于在同一艘船上统一使用近程或远程武器，两者互斥。

## 试射

1. 启用齿轮工业和 Console Commands，进入任意生涯存档。
2. 在控制台执行 `Addweapon GearIn_durin`、`Addweapon GearIn_uri` 与 `Addweapon GearIn_dwalin`，并用 `Addship GearIn_artizan` 准备一艘能装下它们的船。工匠的两个中型通用槽可以装都林和乌里；德瓦林是大型武器，需要一艘有大型导弹槽的原版舰船。
3. 在装配界面核对三门武器的射程、伤害、幅能和装配点是否与 CSV 一致。

![都林自动炮在装配界面中的测试结果](./weapon_test_1.png 'weapon_test_1.png')

![乌里光束炮在装配界面中的测试结果](./weapon_test_2.png 'weapon_test_2.png')

![德瓦林发射架在装配界面中的测试结果](./weapon_test_3.png 'weapon_test_3.png')

4. 进入一场战斗，分别持续射击。都林应当两管交替射出红色弹体；乌里射出绿色光束；德瓦林每轮射出 10 枚新的导弹，打空 100 发备弹后不再恢复。

## 常见问题

- **装配界面找不到武器**：`weapon_data.csv` 的行没有被读取，例如行首仍带 `#`，或 `.wpn` 的 `id` 与 CSV 不一致。
    - **解决方案**：统一两处 ID，保存并重启游戏。

- **武器显示但贴图缺失**：`.wpn` 中的贴图路径或文件名不对。
    - **解决方案**：逐个核对 `turretSprite`、`hardpointSprite` 等路径的大小写和扩展名。

- **加载时报错并指向某个弹体 ID**：`.wpn` 的 `projectileSpecId` 没有对应的 `.proj`，或 `.proj` 的 `id` 写错。
    - **解决方案**：统一两处弹体 ID 并重启。

- **原版暴风的射程或导弹结构变了**：德瓦林仍在使用 `squall_rocket`。
    - **解决方案**：为德瓦林制作独立的 `.proj`，并修改 `projectileSpecId`。

## 建议参考项

- `data/weapons/weapon_data.csv`：原版武器数据，复制样本的来源。
- `data/weapons/lightag.wpn`：都林自动炮的复制来源。
- `data/weapons/gravitonbeam.wpn`：乌里光束的复制来源。
- `data/weapons/squall.wpn`：德瓦林发射架的复制来源。
- `data/weapons/proj/`：原版弹体，寻找特殊弹体形态时的参考。
- `com.fs.starfarer.api.combat.WeaponAPI`：`WeaponType`、`WeaponSize` 等枚举。
- `com.fs.starfarer.api.loading.WeaponSpecAPI`：武器规格可读取的全部属性。
