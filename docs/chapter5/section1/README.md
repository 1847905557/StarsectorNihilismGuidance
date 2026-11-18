# 准备并注册音频

游戏里的每一个声音都有一个 ID。武器、战术系统、舰船风格引用的都是这个 ID，不是音频文件本身。

所以这一节只做登记。登记好了，下一节在各处填上 ID 即可。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C4-S3] 的引擎风格，自定义引擎音效要与它配套；
- 能把音频导出为 OGG 的软件，音频文件按它准备；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，`sounds.json` 按它手工修改。

## 将音效与音乐放入正确的位置

音效与音乐都放进 `sounds/` 下的对应子目录里，**格式一律使用 `.ogg`**。

::: tip 约定俗成的文件夹名

**music**：音乐

**sfx_cargo**：各类物品的拿起、放下、激活等音效

**sfx_engines**：引擎声与零幅能加速音效

**sfx_flux**：幅能强制排散的各类音效

**sfx_hullmod**：与船体插件相关的各类音效

**sfx_shield**：护盾开启、关闭和受击等各类音效

**sfx_system**：战术系统开启、循环、关闭、触发式等各类音效

**sfx_weapon**：武器充能、开火等各类音效

**sfx_misc**：一切难以被上述归类的音效

原版目录里也会见到 `sfx_shields`、`sfx_systems` 或按武器类型细分的 `sfx_wpn_guns` 等名字；游戏只认 `sounds.json` 里写的路径，MOD 自己的文件夹保持整套一致即可。

:::

**在战场上有位置的声音导出为单声道，音乐与界面音效导出为双声道。** 开火、爆炸、引擎这类声音会随它离镜头的远近和方向改变左右声道的音量，这只对单声道生效。双声道的开火声在哪里开火听起来都一样，玩家听不出它来自屏幕左边还是右边。

循环播放的声音，例如战术系统的持续声，导出前要反复试听首尾的接缝。接缝处的一声 "咔" 在游戏里会每隔几秒响一次，这只能在素材里修，配置救不回来。

## 配置音乐与音效

音乐与音效都在 `data/config/sounds.json` 里配置。**动手时打开原版同路径的 `sounds.json` 对照，直接复制相近的条目来改会省事得多。**

MOD 的 `sounds.json` 只写自己的条目，游戏会把它与原版的同名文件合并。

### 配置常规音效

常规音效的配置很直接：

```json
    "音效的 id":[
        #路径，文件名
        {"file":"sounds/sfx_systems/mote_attractor_targeted_empty_space_01.ogg","pitch":0.6,"volume":0.7}, # pitch 为音调，volume 为音量
        {"file":"sounds/sfx_systems/mote_attractor_targeted_empty_space_02.ogg","pitch":0.6,"volume":0.7}, # 同样支持多差分，默认随机播放一个
    ],
```

`file` 的路径从 MOD 根目录写起，找不到时游戏会去原版里找。所以**原版的音效文件可以直接拿来用**，换个音调和音量，就是一个新的声音。

本例为都林和突击填弹器各登记一个声音。都林借用原版轻型自动炮的开火声，把音调压低，听起来更沉；突击填弹器的持续声借用原版机动推进器的循环声：

```json
{
	"GearIn_weapon_fire":[
		{"file":"sounds/sfx_wpn_guns/autocannon_fire_01.ogg","pitch":1.1,"volume":0.7},
	],
	"GearIn_system_loop":[
		{"file":"sounds/sfx_systems/maneuvering_jets_loop.ogg","pitch":0.6,"volume":0.4},
	],
}
```

换成自己的素材时，只改 `file`，ID 不动。引用这个 ID 的武器和系统都不用跟着改。编者的习惯是先用原版素材把 ID 登记好、把整套声音在游戏里配齐，再一个个替换成自己的文件，这样每次换素材都能马上听到它在战场上的效果。

### 配置音乐

音乐的格式比音效略微复杂一些：

```json
{
    "music":{ # 注意此处必须再套一层

		"音乐的id":[
			{
				"source":"sounds/music/",
				"volume":0.2, # 音量，1为原声
				"files":[
					"faction_generic_market_01_neutral_var01.ogg", # 文件名
					"faction_generic_market_01_neutral_var02.ogg", # 支持多差分，若不需要则删除某一条即可
				],
				"randomStart":true, # 设为 true 则允许不按列表顺序随机播放
			},
		],

		"另一个音乐的id":[
			{
				"source":"sounds/music/",
				"volume":0.2, # 音量，1为原声
				"files":[
					"faction_generic_market_01_neutral_var01.ogg", # 文件名
					"faction_generic_market_01_neutral_var02.ogg", # 支持多差分，若不需要则删除某一条即可
				],
				"randomStart":true, # 设为 true 则允许不按列表顺序随机播放
			},
		],

		# 可无限按格式扩充...
    },
}
```

`source` 是音乐所在的文件夹，`files` 里写文件名，两者拼起来就是文件路径。原版的音乐都打包在 `sounds/music/music.bin` 里，`source` 也可以直接写这个文件。

本例在上面的 `sounds.json` 中再加一个 `music` 段，为齿轮工业的市场登记一首音乐，暂时借用原版的通用市场曲：

```json
	"music":{
		"GearIn_market_music":[
			{
				"source":"sounds/music/music.bin",
				"volume":0.7,
				"files":[
					"faction_generic_market_01_neutral_var01.ogg",
					"faction_generic_market_01_neutral_var02.ogg",
				],
				"randomStart":true,
			},
		],
	},
```

用自己的曲子时，把 OGG 放进 `sounds/music/`，`source` 改为 `"sounds/music/"`，`files` 改为文件名即可。

### 配置非常规音效

除了常规音效，引擎、护盾、舰船爆炸这类在舰船风格里调用的音效需要按声音集来配：

```json
    # 以引擎声为例
	"engine_loop":{
		"sounds":[
			{"file":"sounds/sfx_engines/engine_01_lotek_00_fighter.ogg","pitch":0.67,"volume":1}, # index:  0
			{"file":"sounds/sfx_engines/engine_01_lotek_01_frigate.ogg","pitch":1,"volume":1},    # index:  1
			{"file":"sounds/sfx_engines/engine_01_lotek_02_destroyer.ogg","pitch":1,"volume":1},  # index:  2
			{"file":"sounds/sfx_engines/engine_01_lotek_03_cruiser.ogg","pitch":1,"volume":1},    # index:  3
			{"file":"sounds/sfx_engines/engine_01_lotek_04_capital.ogg","pitch":1,"volume":1},    # index:  4
			{"file":"sounds/sfx_engines/engine_02_midtek_00_fighter.ogg","pitch":0.67,"volume":1},# index:  5
			{"file":"sounds/sfx_engines/engine_02_midtek_01_frigate.ogg","pitch":1,"volume":1},   # index:  6
			{"file":"sounds/sfx_engines/engine_02_midtek_02_destroyer.ogg","pitch":1,"volume":1}, # index:  7
			{"file":"sounds/sfx_engines/engine_02_midtek_03_cruiser.ogg","pitch":1,"volume":1},   # index:  8
			{"file":"sounds/sfx_engines/engine_02_midtek_04_capital.ogg","pitch":1,"volume":1},   # index:  9
			{"file":"sounds/sfx_engines/engine_03_hitek_00_fighter.ogg","pitch":0.67,"volume":1}, # index: 10
			{"file":"sounds/sfx_engines/engine_03_hitek_01_frigate.ogg","pitch":1,"volume":1},    # index: 11
			{"file":"sounds/sfx_engines/engine_03_hitek_02_destroyer.ogg","pitch":1,"volume":1},  # index: 12
			{"file":"sounds/sfx_engines/engine_03_hitek_03_cruiser.ogg","pitch":1,"volume":1},    # index: 13
			{"file":"sounds/sfx_engines/engine_03_hitek_04_capital.ogg","pitch":1,"volume":1},    # index: 14

			{"file":"sounds/sfx_engines/engine_04_omega_00_fighter.ogg","pitch":1,"volume":0.3},  # index: 15
			{"file":"sounds/sfx_engines/engine_04_omega_01_frigate.ogg","pitch":1,"volume":0.3},  # index: 16
			{"file":"sounds/sfx_engines/engine_04_omega_02_destroyer.ogg","pitch":1,"volume":1},  # index: 17
			{"file":"sounds/sfx_engines/engine_04_omega_03_cruiser.ogg","pitch":1,"volume":1},    # index: 18
			{"file":"sounds/sfx_engines/engine_04_omega_04_capital.ogg","pitch":1,"volume":1},    # index: 19
			],

		# 新条目，基于舰船风格与舰船体积选择不同的音效，并以不同的音调、音量播放
		"LOW_TECH":{"sounds":[0,1,2,3,4], "pitch":1, "volume":0.5},
		"MIDLINE":{"sounds":[5,6,7,8,9], "pitch":1, "volume":0.3},
		"HIGH_TECH":{"sounds":[10,11,12,13,14], "pitch":1, "volume":0.5},
		"OMEGA":{"sounds":[15,16,17,18,19], "pitch":1, "volume":1},
		"FIGHTER":{"sounds":[0,5,10,15], "pitch":1, "volume":0.4},
		"FRIGATE":{"sounds":[1,6,11,16], "pitch":1, "volume":0.7},
		"DESTROYER":{"sounds":[2,7,12,17], "pitch":1, "volume":0.7},
		"CRUISER":{"sounds":[3,8,13,18], "pitch":1, "volume":0.7},
		"CAPITAL_SHIP":{"sounds":[4,9,14,19], "pitch":1, "volume":1},
	},
```

`sounds` 数组里的每个文件按顺序编号，从 0 开始。下面每一行按舰船风格或舰船体积列出可用的编号：一艘低科驱逐舰，在 `LOW_TECH` 一行里是 0 到 4，在 `DESTROYER` 一行里是 2、7、12、17，两行都有的 2 号就是它的引擎声。

**对于自定义舰船风格而言，编者强烈建议设置一套自己的引擎音效**，否则游戏会默认播放欧米伽的引擎声，放在普通舰船上多半与主题不搭。[C4-S3] 的 `GEAR_TECH` 正是这样。

齿轮工业的船都改自低科蓝图，所以本例登记一套只包含低科引擎声的声音集，风格一行写 `GEAR_TECH`：

```json
	"GearIn_engine_loop":{
		"sounds":[
			{"file":"sounds/sfx_engines/engine_01_lotek_00_fighter.ogg","pitch":0.67,"volume":1}, # index: 0
			{"file":"sounds/sfx_engines/engine_01_lotek_01_frigate.ogg","pitch":1,"volume":1},    # index: 1
			{"file":"sounds/sfx_engines/engine_01_lotek_02_destroyer.ogg","pitch":1,"volume":1},  # index: 2
			{"file":"sounds/sfx_engines/engine_01_lotek_03_cruiser.ogg","pitch":1,"volume":1},    # index: 3
			{"file":"sounds/sfx_engines/engine_01_lotek_04_capital.ogg","pitch":1,"volume":1},    # index: 4
		],
		"GEAR_TECH":{"sounds":[0,1,2,3,4], "pitch":0.9, "volume":0.5},
		"FIGHTER":{"sounds":[0], "pitch":1, "volume":0.4},
		"FRIGATE":{"sounds":[1], "pitch":1, "volume":0.7},
		"DESTROYER":{"sounds":[2], "pitch":1, "volume":0.7},
		"CRUISER":{"sounds":[3], "pitch":1, "volume":0.7},
		"CAPITAL_SHIP":{"sounds":[4], "pitch":1, "volume":1},
	},
```

`GEAR_TECH` 一行的音调压到 0.9，让齿轮工业的引擎比原版低科稍沉一些。这套声音集在下一节接到 `GEAR_TECH` 上。

### 进游戏核对

保存 `sounds.json` 并重启游戏。能正常进入主菜单，说明登记的格式与路径都没有问题；声音本身要等下一节接到武器、系统与舰船上才能听到。

## 常见问题

- **启动时报 JSON 解析错误**：括号、逗号或引号不完整。
    - **解决方案**：按日志中的行号检查，保存后重启。

- **启动时报找不到声音文件**：`file` 的路径或大小写与实际文件不一致，或音乐的 `source` 与 `files` 拼起来不是文件的实际位置。
    - **解决方案**：从 MOD 根目录开始逐段核对路径，保存后重启。

- **音乐没有被游戏识别**：音乐条目没有写在 `music` 段内。
    - **解决方案**：把它移进 `music` 段，保存后重启。

- **开火声在哪里都一样响**：音效文件是双声道。
    - **解决方案**：重新导出为单声道。

- **循环声每隔一段时间 "咔" 一下**：素材首尾没有接好。
    - **解决方案**：在音频编辑器中修整首尾后重新导出。

## 建议参考项

- `data/config/sounds.json`：原版全部声音，音效、音乐与舰船风格声音集的写法都能在这里找到。
- `sounds/`：原版音效文件，可以直接引用。
- `sounds/music/music.bin`：原版音乐的打包文件，音乐的登记写法对照 `sounds.json` 中的音乐段。
