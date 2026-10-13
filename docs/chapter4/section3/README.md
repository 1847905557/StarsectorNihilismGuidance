# 为舰船加入外观风格

本节为工匠配置齿轮工业的黄铜色舰船与引擎风格。护盾、损伤、排散、引擎焰和航迹分别读取各自的字段，声音则通过风格中的声音集 ID 连接。我们先使用原版资源完成配置，再按需要接入自有声音与材质。

*本节预期阅读时间：\~10 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 在 hull_styles.json 定义舰船风格；
- 在 engine_styles.json 定义引擎风格；
- 将两类风格分别写入船体和引擎槽；
- 说明颜色、材质、声音集与对象的引用关系；
- 从加载和部署结果定位风格错误。

## 前置知识

- [C1-S1] 的数据、资源目录与合并；
- [C1-S2] 的工匠船体、引擎槽与控制台获取；
- [C4-S2] 的模拟战和装配观察；
- 能够保存 JSON 并读取游戏日志。

## 定义舰船风格

在 MOD 的 `data/config/hull_styles.json` 创建 `GEAR_TECH`。顶层 `style` 使用这个键读取舰船风格，后续同势力的其他船体也可以引用它。

从原版 `LOW_TECH` 复制后修改颜色和资源。**风格统一靠各船体的实际引用**，在文件中写一个名字还需要接到每艘船上。当前以黄铜色系替换护盾、排散与爆炸配色：

```json
{
	"GEAR_TECH":{ # 舰船风格 ID。不得使用文件头保留 ID
		"hyperspaceJitterColor":[255,170,60,200], # 建议保留：舰船在超空间航行时的光芒颜色
		"baseCampaignExplosionColor":[255,150,50,255], # 建议保留：大地图爆炸颜色
		"baseCombatExplosionColor":[255,150,50,255], # 建议保留：战斗爆炸颜色 (闪光)
		"combatExplosionNonFlashColorOverride":[255,180,90,255], # 可选：战斗爆炸的非闪光部分 (更蓬松的爆炸)
		"fighterRepairFlickerColor":[255,170,60,255], # 建议保留：甲板维修战机时冒火花的颜色
		"fighterChipIconColor":[255,200,120,255], # 可选，默认同 fighterRepairFlickerColor：战机 LPC 颜色
		"weaponDisabledExplosionColor":[255,170,60,255], # 建议保留：武器下线冒火花的颜色
		"shieldRingColor":[255,215,140,255], # 建议保留：护盾外圈颜色
		"shieldInnerColor":[255,140,40,70], # 建议保留：护盾内圈颜色
		"fluxVentCoreColor":[255,235,200,255], # 建议保留：V 排 (幅能排放) 核心颜色
		"fluxVentFringeColor":[255,120,0,255], # 建议保留：V 排外圈颜色
		"fluxVentTextureSheet":"graphics/fx/nebula_colorless.png", # 可选：V 排外圈材质
		"fluxVentRadialTexture":"graphics/fx/radial_fx.png", # 可选：V 排核心材质
		"engineLoopSet":"engine_loop", # 引擎循环音效：sounds.json 中的声音集 ID
		"engineAccelerateSet":"engine_accelerate", # 零幅能加速瞬间音效：sounds.json 中的声音集 ID
		"empLoopSet":"emp_loop", # EMP 过载相关循环音效
		"fluxLoopSet":"flux_loop", # V 排循环音效
		"ventFluxSet":"vent_flux", # V 排瞬间音效
		"shieldBurnoutSet":"shield_burnout", # 护盾过载瞬间音效
		"shieldLowerSet":"shield_lower", # 护盾关闭音效
		"shieldRaiseSet":"shield_raise", # 护盾开启音效
		"explosionShipSet":"explosion_ship", # 舰船爆炸音效
		"damageDecalSheet":"graphics/damage/damage_decal_sheet_base.png", # 损伤贴图
		"damageDecalGlowSheet":"graphics/damage/damage_decal_sheet_glow.png", # 带光效的损伤贴图

		# 武器盖板配色：coversColorShiftAmount 为 0~1，越大越接近舰体颜色；
		# coversMatchShipBrightness 让盖板亮度跟随舰体亮度。
		# colorShiftCovers 不参与当前盖板配色。
		"coversColorShiftAmount":0.6,
		"coversMatchShipBrightness":true,

		# 武器盖板贴图 (最小例复用原版材质，可换成自己的盖板贴图)
		"slotCoverSmallTurret":"graphics/weapons/covers/cover_turret_lowtech_small.png",
		"slotCoverSmallHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_small.png",
		"slotCoverMediumTurret":"graphics/weapons/covers/cover_turret_lowtech_medium.png",
		"slotCoverMediumHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_medium.png",
		"slotCoverLargeTurret":"graphics/weapons/covers/cover_turret_lowtech_large.png",
		"slotCoverLargeHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_large.png",

		# D 插覆盖贴图 (舰船安装 D 插件后的船体贴花)
		"dHullOverlayLight":"graphics/damage/dmod_overlay_lowtech_light.png",
		"dHullOverlayMedium":"graphics/damage/dmod_overlay_lowtech_medium.png",
		"dHullOverlayHeavy":"graphics/damage/dmod_overlay_lowtech_heavy.png",
	},
}
```

颜色采用 RGBA，材质字段填写资源路径，声音字段填写 `sounds.json` 的声音集 ID。同路径文件按对象键合并，当前只提交自有 `GEAR_TECH`。

### 颜色与资源读取规则

九个常用颜色字段分别为：hyperspaceJitterColor、baseCampaignExplosionColor、baseCombatExplosionColor、fighterRepairFlickerColor、weaponDisabledExplosionColor、shieldRingColor、shieldInnerColor、fluxVentCoreColor、fluxVentFringeColor。缺少其中一项时，对应表现采用白色；错误数组格式才会导致加载失败。

**复制完整风格后再调整，比较容易保留已有表现。** 游戏能进入主菜单，并不代表每个颜色都符合设计；护盾、爆炸和排散需要分别看。当前 `overloadColor` 沿用缺省的蓝紫色 `(150,150,255)`。

`fighterChipIconColor` 缺省采用 fighterRepairFlickerColor，`combatExplosionNonFlashColorOverride` 可单独调整较蓬松的爆炸部分。盖板用 coversColorShiftAmount 与 coversMatchShipBrightness 调整，colorShiftCovers 不参与当前配置。

### 风格保留键速查表

| 文件 | 保留键与原因 |
|---|---|
| hull_styles.json | sounds、DEFAULT、FIGHTER、FRIGATE、DESTROYER、CRUISER、CAPITAL_SHIP，与声音和船体等级键共用结构 |
| engine_styles.json | CUSTOM，用于引擎槽内联风格 |

自有 ID 必须保持独立，当前沿用 `GEAR_TECH`。使用船体等级名会影响声音档位选择；注册 CUSTOM 会让引擎风格优先查到全局条目，内联分支失去原本的用途。

## 定义引擎风格

在 `data/config/engine_styles.json` 创建同名 `GEAR_TECH`，配置火焰和烟雾航迹：

```json
{
	"GEAR_TECH":{ # 引擎风格 ID，不得使用保留 ID "CUSTOM"
		"engineColor":[255,150,40,255], # 建议保留：引擎火焰颜色
		"engineCampaignColor":[220,110,50,255], # 可选，默认同 engineColor：引擎火焰在大地图的颜色
		"contrailParticleSizeMult":3, # 必填 (PARTICLES 模式)：引擎航迹粒子大小倍率
		"contrailParticleDuration":2, # 必填 (PARTICLES 模式)：航迹粒子持续时间
		"contrailParticleFinalSizeMult":2.5, # 可选，默认 0.5：航迹粒子随延长而放大的倍率
		"contrailMaxSpeedMult":0.5, # 必填：航迹飘出速率
		"contrailAngularVelocityMult":0.3, # 必填：航迹角速度飘出速率
		"contrailColor":[70,60,50,60], # 建议保留：引擎航迹颜色
		"contrailCampaignColor":[220,110,50,70], # 可选，默认同 contrailColor：航迹在大地图的颜色
		"type":"SMOKE" # 必填：GLOW(淡光、加法混合) 或 SMOKE(烟雾、常规混合)
	},
}
```

本例使用 `SMOKE` 的常规混合方式，`GLOW` 使用加法混合。火焰与航迹颜色可以分别配置，大地图颜色不填时采用对应普通颜色。

### 引擎参数读取速查表

| 范围 | 字段与要求 |
|---|---|
| 两种模式共用 | type、contrailMaxSpeedMult、contrailAngularVelocityMult |
| PARTICLES | contrailParticleSizeMult、contrailParticleDuration |
| QUAD_STRIP | contrailDuration、contrailWidthMult |
| mode 缺省 | PARTICLES |
| 颜色缺省 | engineColor、contrailColor 为白色 |
| 粒子最终尺寸缺省 | contrailParticleFinalSizeMult 为 0.5 |

`type` 使用 GLOW 或 SMOKE，`mode` 决定粒子与光带参数。缺少必填值或使用无效枚举，游戏在加载阶段报告对应键的错误。

### 使用光带型航迹

需要欧米伽型航迹时，可以在同一文件中增加下列局部条目：

```json
		"GEAR_TECH_OMEGA":{ # 光带型风格：mode 为 QUAD_STRIP
			"mode":"QUAD_STRIP", # 粒子模式或光带模式，决定读取哪一组参数
			"contrailDuration":1.5, # 必填 (QUAD_STRIP 模式)：光带持续时间
			"contrailWidthMult":0.67, # 必填 (QUAD_STRIP 模式)：光带宽度
			"contrailWidthAddedFractionAtEnd":-1, # 可选，字段缺失时默认 0.5：显式 0 表示不附加末端宽度，小于 0 末端窄，大于 0 末端宽
			"contrailMinSeg":5, # 可选，默认 5：光带每一小节的渲染长度
			"contrailMaxSpeedMult":1, # 必填：航迹飘出速率
			"contrailAngularVelocityMult":0, # 必填：航迹角速度飘出速率
			"contrailColor":[50,50,255,75], # 建议保留：引擎航迹颜色

			"glowSprite":"graphics/fx/engineglow32b.png", # 光带材质
			"glowOutline":"graphics/fx/engineflame32b.png", # 光带外部材质
			"omegaMode":true, # 建议开启：光带会在停止加速时切断
			"glowSizeMult":1, # 光带外扩泛光的大小
			"glowAlternateColor":[50,50,255,255], # 光带外扩泛光的末端颜色

			"engineColor":[150,50,255,255], # 建议保留
			"type":"GLOW" # 必填
		},
```

`QUAD_STRIP` 使用连续光带，duration 与 width 控制持续和宽度。末端附加宽度缺省为 0.5，显式 0 保持原宽，小于 0 收窄，大于 0 加宽；minSeg 缺省为 5。

`glowSprite`、`glowOutline` 提供材质，glowSizeMult 与 glowAlternateColor 调整泛光。`omegaMode` 让停止加速时切断航迹。当前工匠使用普通 GEAR_TECH，此条目作为可选的光带配置。

引擎槽也能用 `style: "CUSTOM"` 与 `styleSpec` 内联完整风格。这个方式依赖 CUSTOM 保持为保留键，内联对象同样需要该模式的完整字段。普通制作采用文件中的 ID 引用即可。

## 接到工匠船体

在 `GearIn_artizan.ship` 顶层改为：

```json
  "style": "GEAR_TECH",
```

再把每个引擎槽的 `style` 改为：

```json
  "engineSlots": [
    {
      "location": [ 12.0, -30.0 ],
      "width": 14,
      "length": 20,
      "angle": 0,
      "style": "GEAR_TECH",
      "contrailSize": 128
    }
  ],
```

**顶层 style 与引擎槽 style 分别查询两份风格文件。** 本例两者同名，仍需分别登记。保存后，可以通过舰船编辑器的 "导入 MOD" 加载齿轮工业，直接选择自定义风格。

## 验证方案

1. 保存两份风格文件和工匠船体，重启游戏，核对 hull style 与 engine style 的加载记录。
2. 获取工匠并进入模拟战，移动舰船，观察火焰和烟雾航迹。
3. 开关护盾、排散、过载并击毁测试目标，比较对应配色与声音。
4. 回到生涯观察舰队在超空间的光芒与爆炸表现。
5. 在编辑器导入 MOD，核对自定义风格可选项。

预期结果：

- 护盾、排散、战斗爆炸、引擎与航迹采用当前黄铜色配置。
- 过载电弧使用缺省蓝紫色，超空间光芒与生涯爆炸采用对应字段。
- 声音使用原版通用声音集，资源由原版提供。
- 编辑器可以按自定义 ID 选择风格。

## 接入自定义声音与材质

舰船风格中的九个声音字段分别对应引擎、零幅能加速、EMP、幅能、排散、护盾和爆炸声音集。引擎控制器、护盾与幅能对象在各自的运行阶段读取这些 ID。

材质路径包括 damageDecal、slotCover、dHullOverlay、fluxVent，以及光带的 glowSprite 与 glowOutline。资源在加载时登记，随后供对应表现读取。

当前最小例使用原版声音。需要自定义引擎声时，在 `sounds.json` 中加入：

```json
{
	"GearIn_engine_loop":{ # 声音集 ID，供 hull style 的 engineLoopSet 引用
		"sounds":[
			{"file":"sounds/sfx_engines/GearIn_engine_fighter.ogg","pitch":1,"volume":1}, # index: 0
			{"file":"sounds/sfx_engines/GearIn_engine_frigate.ogg","pitch":1,"volume":1}, # index: 1
			{"file":"sounds/sfx_engines/GearIn_engine_destroyer.ogg","pitch":1,"volume":1}, # index: 2
			{"file":"sounds/sfx_engines/GearIn_engine_cruiser.ogg","pitch":1,"volume":1}, # index: 3
			{"file":"sounds/sfx_engines/GearIn_engine_capital.ogg","pitch":1,"volume":1} # index: 4
		],
		"FIGHTER":{"sounds":[0], "pitch":1, "volume":0.2},
		"FRIGATE":{"sounds":[1], "pitch":1, "volume":0.3},
		"DESTROYER":{"sounds":[2], "pitch":1, "volume":0.4},
		"CRUISER":{"sounds":[3], "pitch":1, "volume":0.5},
		"CAPITAL_SHIP":{"sounds":[4], "pitch":1, "volume":0.6}
	},
}
```

音频放到 MOD 的 `sounds/` 下，路径对应每个 file，船体等级按声音数组索引选择。把舰船风格的 `engineLoopSet` 改为 `GearIn_engine_loop`，该声音集就用于循环引擎声。其余加速、护盾和排散声音沿同一结构处理，音频准备与播放由第五章继续讲解。

**声音集 ID 与音频文件都需要存在。** 风格缺省时使用原版通用集；填写了不存在的自定义集，部署时可能在对应声音对象创建处产生空引用；音频资源路径错误则在加载阶段报错。

## 验证方案

前置数据：已准备声音集中的 OGG 文件，并登记 GearIn_engine_loop、更新 engineLoopSet。

1. 保存声音配置与资源，重启游戏，核对声音加载阶段。
2. 在模拟战中移动工匠，比较新的循环引擎声。
3. 用其他船体等级重复观察分档音量，再检查护盾与排散声音。

预期结果：

- 引擎循环播放自有音频，等级分档按配置选择音量。
- 其他声音字段沿用原版值，护盾、排散和过载仍使用原版声音。
- 舰船可以正常部署，加载和播放过程对应有效资源。

## 故障分析

- **加载进度条阶段出现风格键或颜色数组错误**：`engine_styles.json` 缺少必填键、颜色数组不足 4 个元素，或 `type` 不是 `GLOW`、`SMOKE` 时，日志会出现 `Key [...] is required`、`invalid value` 或颜色数组错误。`hull_styles.json` 缺少颜色字段时会回退为白色，非法值和 JSON 语法才会中断加载。
    - **解决方案**：补齐字段后重启。

- **加载阶段失败且日志指向 `.ship`**：顶层 `style` 缺失会抛出 `JSONObject["style"] not found.`，引用不存在的 hull style 时会在读取护盾颜色处抛 NPE。
    - **解决方案**：让 `.ship` 的 `style` 与 `hull_styles.json` ID 逐字一致，重启。

- **`Addship` 部署时抛 NPE 且舰船无法入场**：`engineSlots` 的 `style` 指向未注册的 engine style，战斗实体生成时会取得 null。
    - **解决方案**：改用已注册 ID 或补齐 `engine_styles.json` 条目，再次部署。

- **部署时 NPE 堆栈指向引擎控制器构造链或 `Shield`**：hull style 引用了 `sounds.json` 中不存在的声音集 ID。
    - **解决方案**：注册对应声音集或改用现有 ID，重新部署。

- **启动时声音资源加载失败**：`sounds.json` 中的 `.ogg` 路径不存在，或大小写与文件不一致。
    - **解决方案**：核对 `file` 相对 MOD 根目录的路径，重启。

- **势力舰队生成时出现风格加载错误**：舰队装配引用的 `.ship` 可能带有损坏的风格或声音集关系。
    - **解决方案**：沿 `.ship`、风格 ID、声音集 ID 逐层核对，重新生成舰队。

- **护盾和引擎声音仍像原版风格**：未引用自定义声音集时，hull style 会使用原版通用声音。
    - **解决方案**：核对 sounds.json 的声音集与风格引用，重新部署。

## 总结

舰船风格负责护盾、损伤、排散、声音与相关材质，引擎风格负责火焰和航迹。两者通过船体的不同 style 字段引用，可以复用同一套势力外观，也可以为特殊船体单独配置。

调整表现时，把注册键、字段、资源和运行位置对应起来。编者建议先用原版资源确认自己的配色和模式，再替换材质与声音，这样每次变化的原因都比较明确。

## 建议参考项

- `data/config/hull_styles.json`：舰船风格字段与保留键。
- `data/config/engine_styles.json`：粒子、光带与 CUSTOM 内联风格。
- `data/config/sounds.json`：声音集和船体等级分档。
- `Starsector/starsector-core/starsector.log`：风格加载、部署与播放错误。
