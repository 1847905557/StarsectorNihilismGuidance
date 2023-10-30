# 设置音效与音乐

*本节预期阅读时间：~5 分钟。*

*本节预期操作时间：~10 分钟。*

## 将音效与音乐放入正确的位置

音效与音乐应当放入`sounds`文件夹内的各个子目录内。**音效与音乐应当是`.ogg`格式**。

::: tip 约定俗成的文件夹名

**music**：音乐



**sfx_cargo**：各类物品的拿起、放下、激活等音效

**sfx_engines**：引擎声与零幅能加速音效

**sfx_flux**：幅能强制排散的各类音效

**sfx_hullmod**：与舰船插件相关的各类音效

**sfx_shield**：护盾开启、关闭和受击等各类音效

**sfx_system**：战术系统开启、循环、关闭、触发式等各类音效

**sfx_weapon**：武器充能、开火等等各类音效



**sfx_misc**：一切难以被上述归类的音效

:::

## 配置音乐与音效

对音乐与音效的配置在`data/config/sounds.json`文件内完成。**在配置它们的时候，应当同时打开原版同目录下的该文件进行参考，且便于复制粘贴**。

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

### 配置常规音效

常规音效的配置则简单许多：

```json
    "音效的 id":[ 
        #路径，文件名
        {"file":"sounds/sfx_systems/mote_attractor_targeted_empty_space_01.ogg","pitch":0.6,"volume":0.7}, # pitch 为音调，volume 为音量
        {"file":"sounds/sfx_systems/mote_attractor_targeted_empty_space_02.ogg","pitch":0.6,"volume":0.7}, # 同样支持多差分，默认随机播放一个
    ],
```

### 配置非常规音效

除了常规音效之外，存在一些与舰船相关的非常规音效，具体为引擎声、护盾声、舰船爆炸声等在舰船风格中调用的音效。

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

**对于自定义舰船风格而言，编者强烈建议设置一套自定义引擎音效**，否则它将默认播放欧米伽类型音效，产生大概并不符合主题的实际效果。
