# 为舰船加入外观风格

舰船风格和引擎风格决定护盾、排散、引擎焰和航迹的颜色。玩家在混战里分辨敌我，很多时候看的就是这几处颜色。

这一节几乎没有技术含量。但它的回报会体现在齿轮工业的每一艘船上。

---

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C1-S2] 的舰船风格 `style` 字段，舰船按它取护盾与引擎颜色；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，风格文件按它手工修改。

## 设置舰船风格

舰船风格文件位于 `data/config` 文件夹内的 `hull_styles.json`，它能控制舰船的护盾颜色、排散颜色、各类音效与贴图材质等。与前面一样，从原版复制一套相近的舰船风格来改最省事，比如 `LOW_TECH`。齿轮工业的船都改自低科蓝图，所以本例在它的基础上改出了 `GEAR_TECH`：

```json
{
	"GEAR_TECH":{ # 舰船风格 ID，必改
		"hyperspaceJitterColor":[255,125,45,200], # 在大地图时的光芒颜色
		"baseCampaignExplosionColor":[255,125,25,255], # 在大地图爆炸时的颜色
		"baseCombatExplosionColor":[255,125,25,255], # 在战斗中爆炸时的颜色
		"fighterRepairFlickerColor":[255,125,25,255], # 甲板维修战机时冒火花的颜色
		"fighterChipIconColor":[255,200,150,255], # 战机 LPC 的颜色
		"weaponDisabledExplosionColor":[255,125,25,255], # 武器下线冒火花的颜色
		"shieldRingColor":[255,255,255,255], # 护盾外圈颜色
		"shieldInnerColor":[255,155,125,75], # 护盾内圈颜色
		"fluxVentCoreColor":[255,255,255,255], # V 排内圈颜色
		"fluxVentFringeColor":[125,0,155,255], # V 排外圈颜色
		"fluxVentTextureSheet":"graphics/fx/nebula_colorless.png", # V 排外圈材质
		"fluxVentRadialTexture":"graphics/fx/radial_fx.png", # V 排内圈材质
		"engineLoopSet":"engine_loop", # 引擎音效
		"engineAccelerateSet":"engine_accelerate", # 零幅能加速瞬间音效
		"empLoopSet":"emp_loop", # 过载后的随机音效
		"fluxLoopSet":"flux_loop", # V 排循环音效
		"ventFluxSet":"vent_flux", # V 排瞬间音效
		"shieldBurnoutSet":"shield_burnout", # 过载瞬间音效
		"shieldLowerSet":"shield_lower", # 护盾关闭音效
		"shieldRaiseSet":"shield_raise", # 护盾打开音效
		"explosionShipSet":"explosion_ship", # 舰船爆炸音效
		"damageDecalSheet":"graphics/damage/damage_decal_sheet_base.png", # 损伤贴图
		"damageDecalGlowSheet":"graphics/damage/damage_decal_sheet_glow.png", # 带光效的损伤贴图

		"coversColorShiftAmount":0, # 武器盖板颜色自适应舰体的程度，0~1，越大越接近舰体颜色
		"coversMatchShipBrightness":true, # 武器盖板亮度是否跟随舰体

		# 武器盖板贴图
		"slotCoverSmallTurret":"graphics/weapons/covers/cover_turret_lowtech_small.png",
		"slotCoverSmallHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_small.png",
		"slotCoverMediumTurret":"graphics/weapons/covers/cover_turret_lowtech_medium.png",
		"slotCoverMediumHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_medium.png",
		"slotCoverLargeTurret":"graphics/weapons/covers/cover_turret_lowtech_large.png",
		"slotCoverLargeHardpoint":"graphics/weapons/covers/cover_hardpoint_lowtech_large.png",

		# D 插覆盖贴图
		"dHullOverlayLight":"graphics/damage/dmod_overlay_lowtech_light.png",
		"dHullOverlayMedium":"graphics/damage/dmod_overlay_lowtech_medium.png",
		"dHullOverlayHeavy":"graphics/damage/dmod_overlay_lowtech_heavy.png",
	},
}
```

颜色都是 RGBA 四个数，最后一个是不透明度。改动集中在护盾内圈和几处火花，其余沿用低科。**一个势力的外观靠少数几处颜色就够鲜明了**，护盾、引擎焰和航迹是玩家看得最多的三处，先改这三处，其余等真的看出违和再动。

原版的 `colorShiftCovers` 在 0.98.5 已不再参与盖板配色，复制时可以删掉。

::: warning 保留 ID

`hull_styles.json` 中的 `sounds`、`DEFAULT`、`FIGHTER`、`FRIGATE`、`DESTROYER`、`CRUISER` 与 `CAPITAL_SHIP` 是保留键，不要用作自己的风格 ID。

:::

改好后写入 MOD 的 `data/config/hull_styles.json`。接着打开 `.ship` 文件，把其中的：

```json
  "style": "LOW_TECH",
```

改为：

```json
  "style": "GEAR_TECH",
```

齿轮工业的每一艘船都要改这一行，战机也不例外。

::: warning 自定义舰船风格的问题

自定义的舰船风格，若没有为它单独做一套引擎音效，**则将导致引擎音效直接采用欧米伽系列舰船的引擎音效**。这不是恶性 BUG，但会导致观感错乱或品质降低。

在后文设置音效的章节中，您可以重新设置一套引擎音效，见 [C5-S1]。

:::

## 设置引擎风格

引擎风格写在 `data/config/engine_styles.json` 里，控制引擎的火焰颜色、航迹颜色、粒子参数与材质等。这里同样从原版 `LOW_TECH` 复制一份来改：

```json
{
	"GEAR_TECH":{ # 引擎风格 ID，必改
		"engineColor":[255,155,25,255], # 引擎火焰颜色
		"engineCampaignColor":[200,120,70,255], # 引擎火焰在大地图的颜色
		"contrailParticleSizeMult":2, # 引擎航迹大小倍率
		"contrailParticleDuration":3, # 引擎航迹持续时间
		"contrailParticleFinalSizeMult":2.5, # 引擎航迹随延长而缩放倍率
		"contrailMaxSpeedMult":0.5, # 引擎航迹飘出速率
		"contrailAngularVelocityMult":0.5, # 引擎航迹角速度飘出速率
		"contrailColor":[50,50,50,50], # 引擎航迹颜色
		"contrailCampaignColor":[200,120,70,75], # 引擎航迹在大地图的颜色
		"type":"SMOKE" # 引擎航迹的类型，可以是 GLOW 或 SMOKE，前者类似淡光，后者类似烟雾
	},
}
```

**引擎风格与舰船风格是两份独立的登记**，同名只是为了好记。`engine_styles.json` 中的 `CUSTOM` 同样是保留键。

但若您希望做欧米伽型航迹，也可以参考 `OMEGA` 型引擎里多出来的几个项目，适当插入即可：

```json
		"mode":"QUAD_STRIP", # 将引擎航迹改为明显光带的样式
		"contrailDuration":1.5, # 光带持续时间，对于 QUAD_STRIP 来说该项必须存在
		"contrailWidthMult":0.67, # 光带宽度，对于 QUAD_STRIP 来说该项必须存在
		"contrailWidthAddedFractionAtEnd":-1, # 光带尾部的宽度，小于 0 为末端窄，大于 0 为末端宽
		"contrailMinSeg":5, # 光带每一小节的渲染长度，不建议改动
		"contrailMaxSpeedMult":1, # 见上文
		"contrailAngularVelocityMult":0, # 见上文
		"contrailColor":[50,50,255,75], # 见上文

		"glowSprite":"graphics/fx/engineglow32b.png", # 光带材质
		"glowOutline":"graphics/fx/engineflame32b.png", # 光带外部材质
		"omegaMode":true, # 光带会在停止向前时切断，不设为 true 会导致严重视觉错误
		"glowSizeMult":1, # 光带外扩泛光的大小
		"glowAlternateColor":[50,50,255,255], # 光带外扩泛光的末端颜色
```

可见制作欧米伽型航迹并没有什么技术力。这也是为什么您总能看到它被滥用。

改好后写入 MOD 的 `data/config/engine_styles.json`。随后在 `.ship` 文件的 `engineSlots` 里找到各引擎槽的：

```json
  "style": "LOW_TECH",
```

逐一改为：

```json
  "style": "GEAR_TECH",
```

每个引擎槽都有自己的 `style`，一艘船有几个引擎槽就改几处。

::: tip 搭配舰船编辑器

写好风格文件后，在舰船编辑器里使用 `导入 MOD` 功能导入当前 MOD，就能直接在下拉列表里选用自定义的舰船和引擎风格。

:::

### 进游戏核对

保存所有文件并重启游戏，在模拟战或测试战役中部署工匠：

- 开盾，护盾内圈是偏暖的橙色；
- 推进时引擎焰为橙色，身后拖出灰色烟雾航迹；
- 打满幅能再 V 排，排散外圈为紫色；
- 回到生涯，在超空间中航行时舰队周围的光芒为橙色。

## 常见问题

- **加载时报 `JSONObject["style"] not found.`**：`.ship` 文件顶层缺少 `style`。
    - **解决方案**：补上 `"style": "GEAR_TECH"`，保存后重启。

- **加载时报空指针，日志指向 `.ship`**：`.ship` 的 `style` 与 `hull_styles.json` 中的 ID 不一致。
    - **解决方案**：让两处 ID 逐字一致，保存后重启。

- **游戏能进主菜单，部署舰船时报空指针**：引擎槽的 `style` 指向了 `engine_styles.json` 中不存在的 ID，或舰船风格的某个音效字段指向了 `sounds.json` 中不存在的声音集。
    - **解决方案**：核对引擎槽的 `style` 与九个音效字段，保存后重启。

- **加载时报 `Key [...] is required` 或 `invalid value`**：引擎风格缺少必填字段，或 `type` 不是 `GLOW` 或 `SMOKE`。改为 `QUAD_STRIP` 却漏了 `contrailDuration`、`contrailWidthMult` 也是这个错误。
    - **解决方案**：按报错补齐字段，保存后重启。

- **护盾或爆炸变成了纯白色**：舰船风格漏了对应的颜色字段。这类缺失不会报错。
    - **解决方案**：从原版风格补回缺失的颜色字段。

## 建议参考项

- `data/config/hull_styles.json`：原版舰船风格，护盾与排散颜色的字段写法样本。
- `data/config/engine_styles.json`：原版引擎风格，`OMEGA` 是光带型航迹的范例。
- `data/config/sounds.json`：原版声音登记，引擎声音集的写法样本。
