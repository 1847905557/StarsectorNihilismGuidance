# 舰船风格与引擎风格

*本节预期阅读时间：~5 分钟。*

*本节预期操作时间：~5 分钟。*

## 设置舰船风格

舰船风格文件位于`data/config`文件夹内的`hull_styles.json`，它能控制舰船的护盾颜色、排散颜色、各类音效与贴图材质等。同理，尽可能复制粘贴修改原版的舰船风格，比如`LOW_TECH`：

```json
	"LOW_TECH":{ # 舰船风格 ID，必改
		"hyperspaceJitterColor":[255,125,45,200], # 在大地图时的光芒颜色
		"baseCampaignExplosionColor":[255,125,25,255], # 在大地图爆炸时的颜色
		"baseCombatExplosionColor":[255,125,25,255], # 在战斗中爆炸时的颜色
		"fighterRepairFlickerColor":[255,125,25,255], # 甲板维修战机时冒火花的颜色
		"fighterChipIconColor":[255,200,150,255], # 战机LPC的颜色
		"weaponDisabledExplosionColor":[255,125,25,255], # 武器下线冒火花的颜色
		"shieldRingColor":[255,255,255,255], # 护盾外圈颜色
		"shieldInnerColor":[255,125,125,75], # 护盾内圈颜色
		"fluxVentCoreColor":[255,255,255,255], # V排內圈颜色
		"fluxVentFringeColor":[125,0,155,255], # V排外圈颜色
		"fluxVentTextureSheet":"graphics/fx/nebula_colorless.png", # V排外圈材质
		"fluxVentRadialTexture":"graphics/fx/radial_fx.png", # V排内圈材质
		"engineLoopSet":"engine_loop", # 引擎音效
		"engineAccelerateSet":"engine_accelerate", # 零幅能加速瞬间音效
		"empLoopSet":"emp_loop", # 过载后的随机音效
		"fluxLoopSet":"flux_loop", # V排循环音效
		"ventFluxSet":"vent_flux", # V排瞬间音效
		"shieldBurnoutSet":"shield_burnout", # 过载瞬间音效
		"shieldLowerSet":"shield_lower", # 护盾关闭音效
		"shieldRaiseSet":"shield_raise", # 护盾打开音效
		"explosionShipSet":"explosion_ship", # 舰船爆炸音效
		"damageDecalSheet":"graphics/damage/damage_decal_sheet_base.png", # 损伤贴图
		"damageDecalGlowSheet":"graphics/damage/damage_decal_sheet_glow.png", # 带光效的损伤贴图
		
		"colorShiftCovers":true, # 武器盖板颜色是否自适应舰体，false否 true是
		"coversColorShiftAmount":0,  # 自适应舰体程度，0~1，越大越接近舰体颜色
		"coversMatchShipBrightness":true, # 自适应舰体光效

		# 武器盖板贴图
		"slotCoverSmallTurret":"graphics/weapons/covers/cover_turret_lowtech_small.png",
		"slotCoverSmallHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_small.png",
		"slotCoverMediumTurret":"graphics/weapons/covers/cover_turret_lowtech_medium.png",
		"slotCoverMediumHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_medium.png",
		"slotCoverLargeTurret":"graphics/weapons/covers/cover_turret_lowtech_large.png",
		"slotCoverLargeHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_large.png",

        # D插覆盖贴图
		"dHullOverlayLight":"graphics/damage/dmod_overlay_lowtech_light.png",
		"dHullOverlayMedium":"graphics/damage/dmod_overlay_lowtech_medium.png",
		"dHullOverlayHeavy":"graphics/damage/dmod_overlay_lowtech_heavy.png",
	},
```

将其简单修改后即可填入您 MOD 中的`hull_styles.json`文件，即可开始使用。将您`.ship`文件中型如：

```json
  "style": "LOW_TECH",
```

的位置替换为您自定义的舰船风格，例如本例中的：

```json
  "style": "GEAR_TECH",
```

即可生效。

::: danger 自定义舰船风格的问题

自定义的舰船风格，若没有为它单独做一套引擎音效，**则将导致引擎音效直接采用欧米伽系列舰船的引擎音效**。这不是恶性 BUG，但会导致观感错乱或品质降低。

在后文设置音效的章节中，您可以重新设置一套引擎音效。

:::

## 设置引擎风格

引擎风格文件位于`data/config`文件夹内的`engine_styles.json`，它能控制引擎的火焰颜色、航迹颜色、各类粒子效果参数与材质等。同理，尽可能复制粘贴修改原版的舰船风格，比如`LOW_TECH`：

```json
	"LOW_TECH":{ # 引擎风格 ID，必改
		"engineColor":[255,125,25,255], # 引擎火焰颜色
		"engineCampaignColor":[200,90,70,255], # 引擎火焰在大地图的颜色
		"contrailParticleSizeMult":2, # 引擎航迹大小倍率
		"contrailParticleDuration":3, # 引擎航迹持续时间
		"contrailParticleFinalSizeMult":2.5, # 引擎航迹随延长而缩放倍率
		"contrailMaxSpeedMult":0.5, # 引擎航迹飘出速率
		"contrailAngularVelocityMult":0.5, # 引擎航迹角速度飘出速率
		"contrailColor":[50,50,50,50], # 引擎航迹颜色
		"contrailCampaignColor":[200,90,70,75], # 引擎航迹在大地图的颜色
		"type":"SMOKE" # 引擎航迹的类型，可以是 GLOW 或 SMOKE，前者类似淡光，后者类似烟雾
	},
```

但若您希望做欧米伽型航迹，也可以参考`OMEGA`型引擎里多出来的几个项目，适当插入即可：

```json
		"mode":"QUAD_STRIP", # 将引擎航迹改为明显光带的样式
		"contrailDuration":1.5, # 光带持续时间，对于 QUAD_STRIP 来说该项必须存在
		"contrailWidthMult":0.67, # 光带宽度，对于 QUAD_STRIP 来说该项必须存在
		"contrailWidthAddedFractionAtEnd":-1,  # 光带尾部的宽度，0 为默认，小于 0 为末端窄，大于 0 为末端宽
		"contrailMinSeg":5, # 光带每一小节的渲染长度，不建议改动，对于 QUAD_STRIP 来说该项必须存在
		"contrailMaxSpeedMult":1, # 见上文
		"contrailAngularVelocityMult":0, # 见上文
		"contrailColor":[50,50,255,75], # 见上文

		"glowSprite":"graphics/fx/engineglow32b.png", # 光带材质
		"glowOutline":"graphics/fx/engineflame32b.png", # 光带外部材质
		"omegaMode":true, # 光带会在停止向前时切断，不设为 true 会导致严重视觉错误
		"glowSizeMult":1, # 光带外扩泛光的大小
		"glowAlternateColor":[50,50,255,255], # 光带外扩泛光的末端颜色
```

可见制作欧米伽型航迹并没有什么技术力。

将其简单修改后即可填入您 MOD 中的`engine_styles.json`文件，即可开始使用。在您`.ship`文件中查找`engineSlots`项目，然后将其中的型如：

```json
  "style": "LOW_TECH",
```

的位置替换为您自定义的舰船风格，例如本例中的：

```json
  "style": "GEAR_TECH",
```

即可生效。

::: tip 搭配舰船编辑器

在设置完舰船风格和引擎风格后，可以使用舰船编辑器的`导入 MOD`功能，将您的 MOD 导入到编辑器内。

即可在编辑器内直接选择您自定义的舰船风格和引擎风格。

:::
