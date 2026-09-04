# 为舰船加入外观风格

本节为 `GearIn_artizan` 注册并应用 `GEAR_TECH` 外观风格，使舰船在战斗、大地图和超空间中保持一致的视觉与声音特征。舰船风格与引擎风格分别控制护盾、损伤、武器盖板、引擎火焰和航迹等表现。

*本节预期阅读时间：~10 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/config/hull_styles.json` 中定义自定义舰船风格；
- 在 `data/config/engine_styles.json` 中定义自定义引擎风格；
- 把风格接入 `.ship` 文件 (顶层 `style` 与 `engineSlots` 的 `style`)；
- 解释风格如何被舰船、势力、声音集和材质共同使用；
- 从 `starsector.log` 定位风格相关的加载失败与战斗内错误。

## 前置知识

- [C1-S1] 的 MOD 根目录、全局 ID、`data/config` 与 `sounds/` 目录及同路径文件合并规则；
- [C1-S2] 的 `.ship` 结构、`ship_data.csv` 注册、`GearIn_artizan` 与 `Addship` 使用方法；
- [C4-S2] 的模拟战入口、装配部署和战斗观察方法。

## 设置舰船风格

在 `data/config/hull_styles.json` 中创建 `GEAR_TECH`。`.ship` 顶层的 `style` 会读取这个 ID，因此舰船和势力舰队只要引用同一风格，就会使用相同的视觉配置。

**最稳妥的做法是复制粘贴修改原版的舰船风格** —— 事到如今我们还需要强调这个吗？

本例以 `LOW_TECH` 为底，把配色改成齿轮工业的黄铜色系：

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
		# 注意：旧教程示例里的 colorShiftCovers 键在 RC8 不被读取，写不写都不生效，请勿依赖。
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

保存到 `data/config/hull_styles.json`。同路径文件按对象键合并，因此只需提交 `GEAR_TECH` 条目。

::: warning 舰船风格的 9 个颜色字段：缺键不报错

舰船风格读取器对以下 9 个颜色字段**缺键时不抛异常**，而是静默回退为白色 (读取颜色的工具方法在键缺失时返回 `Color.white`)：`hyperspaceJitterColor`、`baseCampaignExplosionColor`、`baseCombatExplosionColor`、`fighterRepairFlickerColor`、`weaponDisabledExplosionColor`、`shieldRingColor`、`shieldInnerColor`、`fluxVentCoreColor`、`fluxVentFringeColor`。

后果：漏掉某个颜色键，游戏照常加载、**不会**中断，但对应的护盾/爆炸/V 排颜色会变成白色，观感与原版色系不一致且没有任何报错。真正会让 `loadEngineAndHullStyles()` 阶段抛错的是**值格式非法**：颜色数组不足 4 个元素抛 `Color array with id [...] has less than 4 elements`，JSON 语法错误抛对应解析异常 —— 游戏在加载进度条阶段直接失败，无法进入主菜单。所以**请复制原版条目再改数值，不要手写精简版** (漏键虽不报错，但观感会静默劣化)。

:::

::: caution 舰船风格 ID 的保留清单

`hull_styles.json` 文件头声明 `"sounds"`、`"DEFAULT"`、`"FIGHTER"`、`"FRIGATE"`、`"DESTROYER"`、`"CRUISER"`、`"CAPITAL_SHIP"` 这些禁止使用的保留 ID；`engine_styles.json` 保留 `"CUSTOM"`。

违反后的可观察结果：声音集按 "风格 ID 与船体分级名" 共用同一张键表分档 (声音集查找时先用风格 ID、再用 `FRIGATE` 等分级名查同一个表)，风格 ID 取 `FRIGATE`、`DESTROYER` 这类名字会与分级分档键冲突，引擎与护盾声音的档位选择错乱。请用 `GEAR_TECH` 这类带前缀的自定义名。

`CUSTOM` 不得注册。解析器会先查全局注册表；注册后 `styleSpec` 内联分支不再执行。

:::

## 设置引擎风格

在 `data/config/engine_styles.json` 中创建同名的 `GEAR_TECH`。`.ship` 的 `engineSlots[].style` 从这里读取引擎风格，因此引擎槽必须填写这个 ID。本例以 `LOW_TECH` 为底做黄铜色烟雾航迹：

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

保存到 `data/config/engine_styles.json`。

::: caution 引擎风格的必填字段

引擎风格读取器必填：`type`(只能填 `GLOW` 或 `SMOKE`；缺键抛 `Key [type] is required`，枚举之外的值如手误写成 `GLOWW` 抛 `Key [type] has invalid value`)、`contrailMaxSpeedMult`、`contrailAngularVelocityMult`；在 `PARTICLES` 模式下还必填 `contrailParticleSizeMult`、`contrailParticleDuration`。`engineColor` 与 `contrailColor` **不是必填** —— 缺键不抛异常，与舰船风格的颜色同理静默回退为白色。

违反后的可观察结果：缺失/非法必填键会让条目解析失败、游戏在加载阶段整体失败，无法进入主菜单 (`starsector.log` 记录 `Key [...] is required`、`JSONObject[...] not found.` 或 `invalid value`)。

:::

### 光带型航迹 (OMEGA 型)

若您希望做欧米伽型光带航迹，参考 `OMEGA` 条目里多出来的字段：

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

`type`、`contrailMaxSpeedMult`、`contrailAngularVelocityMult` 在两个模式下都必填；`QUAD_STRIP` 模式额外必填 `contrailDuration` 与 `contrailWidthMult`。

`mode` 不是必填 (缺省默认 `PARTICLES`)；`engineColor`、`contrailColor` 缺键不抛异常 (静默回退为白色，与上一节同理)。

省略任何必填键或填错 `type`，后果与上一节相同：加载阶段整体失败。

::: tip 内联引擎风格 (进阶)

引擎槽除了引用 `engine_styles.json` 的 ID，还可以在 `.ship` 的引擎槽里直接内联一整份风格对象：`"style": "CUSTOM"` 配合 `"styleSpec": { ... }`。该分支要求全局注册表中不存在 `CUSTOM`，因此这个保留 ID 不得注册；内联对象同样必须包含全部必填键。普通 MOD 不需要这个功能，按 ID 引用即可。

:::

## 接入舰船

在 `GearIn_artizan.ship` 中，把顶层舰船风格替换为自定义 ID：

```json {1}
  "style": "GEAR_TECH",
```

然后把 `engineSlots` 中每个引擎槽的 `style` 也替换为自定义 ID：

```json {7}
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

保存即可生效。顶层 `style` 与引擎槽 `style` 是两个独立的解析路径：前者查 `hull_styles.json`，后者查 `engine_styles.json`，不要混用。

搭配舰船编辑器：在设置完舰船风格和引擎风格后，可以在舰船编辑器的 "导入 MOD" 功能中把 `GearIndustry` 导入，直接在编辑器内选择自定义风格。

### 验证方案

前置数据：`data/config/hull_styles.json`、`data/config/engine_styles.json`，以及已按本节完成接入的 `GearIn_artizan.ship`(顶层与引擎槽 `style` 均指向 `GEAR_TECH`)。

1. 勾选 `gear_in` 启动游戏进入主菜单，确认日志出现 `Loading hull style data` 与 `Loading engine style data`，无 `ERROR`。
2. 用控制台执行 `Addship GearIn_artizan`，并部署进模拟战。
3. 给舰船装推进并全速航行。
4. 开启并关闭护盾。
5. 触发 V 排与过载。
6. 击毁一艘舰船。
7. 退出战斗后进入大地图，观察超空间光芒与舰队爆炸颜色。

预期结果：

- 护盾外圈/内圈为黄铜色，开启与关闭护盾有声音；引擎火焰与航迹为黄铜色烟雾；V 排粒子颜色与纹理符合 `GEAR_TECH`；爆炸颜色为黄铜色系，过载电弧为蓝紫色 (150,150,255)(未设 `overloadColor` 的回退色)。
- 大地图：超空间光芒与爆炸颜色为 `GEAR_TECH` 的取值。
- 声音为原版通用引擎声：风格里的声音字段尚未指向自定义声音集，回落到原版通用音效属预期行为。
- 舰船编辑器导入 `GearIndustry` 后可直接选择 `GEAR_TECH` 风格。

## 配置声音与材质

hull style 是 "声音与材质的总线"：

- 声音：风格里的 9 个 `*Set` 字段都是 `sounds.json` 的声音集 ID。
  - 战斗生成舰船时，`ShipEngineControllerAPI` 用 `engineLoopSet`/`engineAccelerateSet`、`Shield` 用 `shieldRaiseSet`/`shieldLowerSet`、`FluxTrackerAPI` 用 `shieldBurnoutSet`(过载音)、爆炸音效播放器用 `explosionShipSet` 等去取对应的声音集。
- 材质：`damageDecal*`(损伤贴花)、`slotCover*`(武器盖板)、`dHullOverlay*`(D 插覆盖)、`fluxVent*`(V 排粒子)、`glowSprite`/`glowOutline`(光带) 等路径在加载阶段全部进入贴图资源队列。
- 战役：生涯引擎用 `hyperspaceJitterColor` 画超空间光芒，战役爆炸用 `baseCampaignExplosionColor`，战斗爆炸用 `baseCombatExplosionColor`。

要让声音生效，先在 `sounds.json` 注册声音集，再让 hull style 的 `*Set` 字段引用该 ID，并确认声音集内的 `.ogg` 文件真实存在。

最小例引用了原版声音集 (`engine_loop` 等)，注册与资源引用都由原版提供，所以零资产也能出声。

在 `data/config/sounds.json` 中注册声音集。`sounds` 数组列出音频文件，再按船体等级选择对应声音：

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

保存为 `data/config/sounds.json`，`.ogg` 文件放入 MOD 的 `sounds/` 目录 (路径相对 MOD 根目录)，再把 hull style 的 `engineLoopSet` 改为 `"GearIn_engine_loop"`。

`engineAccelerateSet`、`fluxLoopSet`、`ventFluxSet`、`shieldRaiseSet`、`shieldLowerSet` 等按同一模式各注册一套 (本节不给具体音频资产，请用您自己的录音或音效素材，名称保持 `GearIn_<subject>` 前缀)。

::: caution 自定义风格的声音：必须遵守

**若没有为自定义 hull style 注册并引用自定义声音集，风格会静默回落到原版通用声音**：舰船风格数据对 9 个声音字段都有默认值 (`engine_loop`、`engine_accelerate`、`emp_loop`、`flux_loop`、`vent_flux`、`shield_burnout`、`shield_lower`、`shield_raise`、`explosion_ship`)，所有原版风格共享这一套。这不是报错，但引擎声、护盾声会听上去与原版一致，观感与自定义配色不一致。

一旦风格里写入了自定义声音集 ID(例如 `"engineLoopSet": "GearIn_engine_loop"`)，该 ID **必须**存在于 `sounds.json`。违反后的可观察结果：加载阶段不报错，但舰船进入战斗、创建引擎控制器时抛 `NullPointerException`(`starsector.log` 记录指向引擎控制器实现的构造链)，舰船无法正常部署；声音集里注册的 `.ogg` 路径不存在时，加载阶段直接失败 (声音资源加载任务报错)，游戏无法进入主菜单。

:::

### 验证方案

前置数据：`data/config/sounds.json` 中的 `GearIn_engine_loop` 声音集与 `sounds/` 下对应的 `.ogg` 文件，hull style 的 `engineLoopSet` 已指向 `GearIn_engine_loop`。

1. 勾选 `gear_in` 启动游戏进入主菜单，确认声音资源加载阶段没有报错。
2. 用控制台执行 `Addship GearIn_artizan`，部署进模拟战并全速航行。
3. 开启与关闭护盾，触发 V 排与过载。

预期结果：

- 引擎循环声来自 `GearIn_engine_loop`，不同分级舰船的音量按声音集内的分档变化。
- 护盾、V 排与过载声音仍为原版通用音效 (对应的 `*Set` 字段尚未改为自定义 ID，属预期行为)。
- 部署与战斗全程不出现引擎控制器或 `Shield` 的 `NullPointerException`，日志无新增 `ERROR`。

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

    - **解决方案**：按 "声音与材质的完整关联流程" 注册并引用自定义 ID，重新部署。

## 总结

`.ship` 顶层 `style` 从 `hull_styles.json` 读取舰船风格，`engineSlots[].style` 从 `engine_styles.json` 读取引擎风格；两个文件在加载早期按对象键合并，势力通过各舰船的风格引用获得统一观感。

hull style 是声音与材质的总线 —— 9 个声音字段指向 `sounds.json` 的声音集，材质字段指向贴图资源，任何一环缺失都会带来可定位的失败 (加载期崩溃或战斗期 NPE)。

最小例可以直接复用原版声音集；使用自定义声音时，必须完成声音集注册、风格引用和 `.ogg` 文件检查。

自定义视觉与声音数据都遵循注册、引用和资源检查的关系。新增舰船变体、势力配色或音效时，按对应文件补齐这三项，加载期与战斗期的排查方法即可复用。

## 建议参考项

- `data/config/hull_styles.json`：舰船风格定义，字段全集与保留 ID 清单见文件头注释。
- `data/config/engine_styles.json`：引擎风格定义，含 `OMEGA` 光带参数与保留 ID `CUSTOM`。
- `data/config/sounds.json`：声音集注册与按船体分级的分档结构。
- `data/hulls/GearIn_artizan.ship`：顶层 `style` 与 `engineSlots[].style` 的接入位置。
- `Starsector/starsector-core/starsector.log`：风格加载与战斗期错误的排查入口。
