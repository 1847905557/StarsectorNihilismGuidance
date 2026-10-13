# 注册音频资源

音频文件准备好后，在 sounds.json 中登记ID、路径、音量与音高。普通音效、船体分档声音集和音乐采用各自的结构；本节提供一份完整配置，下一节把这些ID接到都林、突击填弹器、舰船风格和市场。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 创建普通声音、分档声音集和音乐条目；
- 设置file、source、volume与pitch；
- 按同路径合并添加自有内容；
- 用replace控制整份数据读取，并定位资源错误。

## 前置知识

- [C5-S1] 的四个音频、声道与目录；
- [C1-S1] 的MOD目录和数据合并；
- [C4-S3] 的风格声音集引用；
- 能够编辑JSON对象、数组与字符串。

## 登记普通音效

打开或创建 MOD 的 `data/config/sounds.json`，顶层使用声音ID作为键。都林开火的局部条目如下：

```json
{
    "GearIn_weapon_fire": [
        {
            "file": "sounds/GearIn_weapon_fire.ogg",
            "volume": 1,
            "pitch": 1
        }
    ]
}
```

file为资源路径，volume为音量，pitch为音高倍率；1表示原始值。一个ID可以提供多个候选资源，当前先用单个文件完成接入。

系统循环和UI反馈同样登记为普通音效，ID分别为GearIn_system_loop与GearIn_ui_feedback。它们的资源声道已经按上一节确定，播放方法仍按各自用途选择。

## 登记舰船分档声音集

舰船风格中的engineLoopSet与engineAccelerateSet读取声音集。先列出资源，再按船体等级选择索引，局部结构如下：

```json
{
    "GearIn_engine_loop": {
        "sounds": [
            {"file": "sounds/GearIn_system_loop.ogg", "pitch": 1, "volume": 1}
        ],
        "FIGHTER": {"sounds": [0], "pitch": 1, "volume": 0.2},
        "FRIGATE": {"sounds": [0], "pitch": 1, "volume": 0.3},
        "DESTROYER": {"sounds": [0], "pitch": 1, "volume": 0.4},
        "CRUISER": {"sounds": [0], "pitch": 1, "volume": 0.5},
        "CAPITAL_SHIP": {"sounds": [0], "pitch": 1, "volume": 0.6}
    }
}
```

sounds数组第一个资源的索引为0。本例复用已有循环文件，每档采用相同索引，音量分档。以后准备了独立引擎资源，再扩充数组并替换索引即可。

**声音集的档位和资源选择可以分别调整。** 本节先验证登记与引用；编者建议实际制作时根据小型引擎和大型引擎的听感，再选择适合的素材与分档。

GearIn_engine_accelerate采用相同结构，暂用开火文件作为可辨认的瞬时音。完整配置在下面给出。

## 登记市场音乐

音乐写进顶层的music分类，局部片段如下：

```json
{
    "music": {
        "GearIn_market_music": [
            {
                "source": "sounds",
                "file": "GearIn_market_theme.ogg",
                "volume": 0.8,
                "pitch": 1
            }
        ]
    }
}
```

source可以是资源目录，也可以是原版使用的音频归档。当前source为sounds，file为目录内的文件名；组合后指向sounds/GearIn_market_theme.ogg。音乐ID为GearIn_market_music，实际文件可以保持不同名称。

## 选择合并与替换范围

新增ID写入自有对象，游戏启动时与其他来源的sounds.json合并。**当前只提交本节定义的内容**，原版声音继续由原版提供。

需要替换整份配置时，在mod_info.json的replace数组中声明data/config/sounds.json。路径逐字符一致后，该操作改变整份文件的读取范围，替换内容需要承担相关声音定义。当前新增声音使用普通合并即可。

## 完整最小例

保存到 `data/config/sounds.json`。已有文件时合入这些自有键，music里的条目放进已有music对象。

```json
{
    "GearIn_weapon_fire": [
        {
            "file": "sounds/GearIn_weapon_fire.ogg",
            "volume": 1,
            "pitch": 1
        }
    ],
    "GearIn_system_loop": [
        {
            "file": "sounds/GearIn_system_loop.ogg",
            "volume": 0.8,
            "pitch": 1
        }
    ],
    "GearIn_ui_feedback": [
        {
            "file": "sounds/GearIn_ui_feedback.ogg",
            "volume": 0.8,
            "pitch": 1
        }
    ],
    "GearIn_engine_loop": {
        "sounds": [
            {
                "file": "sounds/GearIn_system_loop.ogg",
                "pitch": 1,
                "volume": 1
            }
        ],
        "FIGHTER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.2
        },
        "FRIGATE": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.3
        },
        "DESTROYER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.4
        },
        "CRUISER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.5
        },
        "CAPITAL_SHIP": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.6
        }
    },
    "GearIn_engine_accelerate": {
        "sounds": [
            {
                "file": "sounds/GearIn_weapon_fire.ogg",
                "pitch": 1,
                "volume": 1
            }
        ],
        "FIGHTER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.2
        },
        "FRIGATE": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.3
        },
        "DESTROYER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.4
        },
        "CRUISER": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.5
        },
        "CAPITAL_SHIP": {
            "sounds": [
                0
            ],
            "pitch": 1,
            "volume": 0.6
        }
    },
    "music": {
        "GearIn_market_music": [
            {
                "source": "sounds",
                "file": "GearIn_market_theme.ogg",
                "volume": 0.8,
                "pitch": 1
            }
        ]
    }
}
```

## 验证方案

1. 保存声音配置，确认上一节资源位于对应file或source目录。
2. 重启游戏，检查JSON解析与声音资源加载记录。
3. 核对普通音效、两套分档集和music分类中的ID。
4. 按下一节的具体接入播放声音，再调整某个条目的volume，保存并重启比较。

预期结果：

- 配置可读取，文件路径指向已准备的Ogg资源。
- 普通音效和船体声音集位于顶层，音乐位于music。
- 使用方能按ID取得资源，volume变化后采用新值。
- 原版与其他自有条目继续按同路径合并读取。

## 故障分析

- **JSON解析失败**：对象、数组、字符串或逗号不完整。
    - **解决方案**：按编辑器与日志检查错误位置，保存后重启。

- **资源不存在**：file与source组合后的路径或大小写错误。
    - **解决方案**：普通音效按相对MOD根目录核对，音乐按source目录与file核对。

- **音乐条目没有播放**：分类或source字段未对应音乐读取结构。
    - **解决方案**：把ID登记在music对象中，补齐目录与文件。

- **声音集分档不符**：档位索引没有对应sounds数组资源。
    - **解决方案**：从0开始核对索引和每档pitch、volume。

- **整文件替换范围不符**：replace路径不同或替换配置缺少相关定义。
    - **解决方案**：核对目标路径与整份配置的实际职责。

## 总结

声音注册把ID连接到资源及播放参数。普通条目提供文件候选，分档集按船体等级选择资源与音量，音乐按source读取文件；三种结构分别服务实际使用方。

登记完成后，下一节写入对象引用并测试播放。资源、注册与播放分开核对，便能把没有声音的问题定位到相应位置。

## 建议参考项

- `data/config/sounds.json`：原版音效、分档集与音乐结构。
- `mod_info.json`：replace整文件读取范围。
