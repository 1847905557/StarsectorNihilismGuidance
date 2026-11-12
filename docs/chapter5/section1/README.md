# 准备 OGG 音频资源

本节准备武器开火、系统循环、界面反馈与市场音乐的 Ogg Vorbis 文件。播放用途先决定声道，资源再按路径放进 MOD；下一节登记声音 ID，随后接到既有武器、系统和市场上。

_本节预期阅读时间：\~6 分钟。_

_本节预期操作时间：\~10 分钟。_

## 学习目标

完成后，您将能够：

- 导出游戏可读取的 Ogg Vorbis；
- 按用途选择声道并组织声音目录；
- 检查格式、采样率与循环接缝；
- 为外部素材保存来源和许可记录。

## 前置知识

- [C1-S1] 的 MOD 目录与资源路径；
- 能够使用音频编辑器导出 Ogg Vorbis；
- 能够播放音频并查看格式属性。

## 先确定声音在哪种场合播放

武器和系统声音通常跟随舰船位置，界面声音作为直接反馈，音乐则连续播放。**位置音效采用单声道，UI 音效和音乐采用双声道**。因此，导出前按实际播放用途确定声道，资源的名称则用于后面的路径登记。

在 MOD 根目录建立 `sounds/`，当前准备四个文件：

### 音频资源速查表

| 文件                             | 用途               | 声道   |
| -------------------------------- | ------------------ | ------ |
| `sounds/GearIn_weapon_fire.ogg`  | 都林开火位置音效   | mono   |
| `sounds/GearIn_system_loop.ogg`  | 突击填弹器持续循环 | mono   |
| `sounds/GearIn_ui_feedback.ogg`  | 进入生涯的界面反馈 | stereo |
| `sounds/GearIn_market_theme.ogg` | 齿轮工业市场音乐   | stereo |

文件名沿用 GearIn_ 前缀，可以继续放进用途子目录。声音 ID 在注册文件中决定，资源路径与 ID 分别填写。例如，市场主题文件名为 GearIn_market_theme.ogg，后面登记的声音 ID 为 GearIn_market_music，两者通过注册条目连接。

## 导出与检查编码

使用音频编辑器导出 Ogg Vorbis，采用 44100 Hz 或 48000 Hz 采样率，按上表选择声道。扩展名保存为 `.ogg`，导出后用播放器和格式属性确认实际编码。

循环资源检查首尾的波形与响度。**系统循环一旦有明显接缝，游戏中会反复出现同一处断点。** 导出前反复播放完整循环，再裁剪或调整首尾；音量可以在注册时调整，接缝则需要在资源本身处理。

资源路径相对于 MOD 根目录，大小写与后面的配置一致。`sounds/` 是原版惯例，播放器实际按注册条目的 file 与 source 读取资源，因此可以按项目规模安排子目录，再将路径写入对应条目。

## 保存素材来源

外部素材记录来源、许可证和修改方式，放在项目文档中。自行录制、公开素材与原版资源都可以作为制作起点，记录按实际来源填写。

后续替换声音时沿用稳定路径和注册 ID，调用代码就可以继续使用当前名称。需要修改文件名时，同时更新登记路径。

## 验证方案

前置数据：四个音频已导出，外部播放器和属性检查工具可用。

1. 检查 sounds 目录，核对四个文件和实际扩展名。
2. 逐个播放，确认声音内容与用途。
3. 核对 Ogg Vorbis 格式、声道与采样率。
4. 反复播放系统循环，比较首尾波形和听感。
5. 保存来源许可记录，再进行声音注册。

预期结果：

- 文件可正常播放，属性显示预期编码和声道。
- 位置资源为单声道，UI 与音乐为双声道。
- 循环首尾连续，重复播放保持预期听感。
- 资源路径与来源记录明确。

## 故障分析

- **播放器无法识别**：扩展名与实际编码不一致，或文件导出不完整。
    - **解决方案**：重新导出 Ogg Vorbis，并核对文件属性。

- **位置音效空间表现异常**：资源使用双声道。
    - **解决方案**：按位置播放用途导出 mono，再核对登记路径。

- **循环有明显断点**：首尾波形或响度不连续。
    - **解决方案**：裁剪与调整接缝，在合适零交叉处导出并重复播放。

- **游戏加载找不到文件**：登记路径与文件目录、大小写不同。
    - **解决方案**：按相对 MOD 根目录的路径核对 file。

## 总结

音频准备先确定用途，再决定声道、编码和循环处理。资源文件保存实际声音，下一节的 sounds.json 将声音 ID 连接到文件，具体使用方继续按 ID 引用。

把资源检查放在注册之前，后面排查就能区分素材本身和游戏接入问题。声音变化最直接的依据仍然是实际播放，文件属性与听感一起核对。

## 建议参考项

- `data/config/sounds.json`：声音 ID 与资源的注册关系。
- `com.fs.starfarer.api.SoundPlayerAPI`：各播放方法的声道与用途约定。

## 注册音频资源

音频文件准备好后，在 sounds.json 中登记 ID、路径、音量与音高。普通音效、船体分档声音集和音乐采用各自的结构；本节提供一份完整配置，下一节将这些 ID 用于都林、突击填弹器、舰船风格和市场。

## 学习目标

完成后，您将能够：

- 创建普通声音、分档声音集和音乐条目；
- 设置 file、source、volume 与 pitch；
- 按同路径合并添加自有内容；
- 用 replace 控制整份数据读取，并定位资源错误。

## 前置知识

- [C5-S1] 的四个音频、声道与目录；
- [C1-S1] 的 MOD 目录和数据合并；
- [C4-S3] 的风格声音集引用；
- 能够编辑 JSON 对象、数组与字符串。

## 登记普通音效

打开或创建 MOD 的 `data/config/sounds.json`，顶层使用声音 ID 作为键。都林开火的局部条目如下：

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

file 指定资源路径，volume 设置音量，pitch 设置音高倍率，1 表示原始值。一个 ID 可以提供多个候选资源；当前数组只有一项，使用刚才准备的开火文件。

系统循环和 UI 反馈同样登记为普通音效，ID 分别为 GearIn_system_loop 与 GearIn_ui_feedback。登记结构相同，资源声道和播放方法仍按各自用途选择，声道已经在上一节确定。

## 登记舰船分档声音集

舰船风格中的 engineLoopSet 与 engineAccelerateSet 读取声音集。同一个声音集先列出资源，再按船体等级选择其中的索引，局部结构如下：

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

sounds 数组第一个资源的索引为 0，各档的 `sounds: [0]` 都引用这一项。本例复用已有循环文件，每档采用相同索引，只将音量分档。以后准备了独立引擎资源，再扩充数组并替换索引即可。

**声音集的档位和资源选择可以分别调整。** 当前配置用于验证登记与引用；实际制作时，素材与各档参数分别依据小型引擎和大型引擎的听感选择。

GearIn_engine_accelerate 采用相同结构，暂用开火文件作为可辨认的瞬时音。它与循环声音集分别登记，完整配置在下面给出。

## 登记市场音乐

音乐条目集中写进顶层的 music 分类，局部片段如下：

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

source 可以是资源目录，也可以是原版使用的音频归档。当前 source 为 sounds，file 为该目录内的文件名；两者组合后指向 sounds/GearIn_market_theme.ogg。音乐 ID 为 GearIn_market_music，实际文件可以保持不同名称，使用方通过这个 ID 取得曲目。

## 选择合并与替换范围

新增 ID 写入自有对象，游戏启动时与其他来源的 sounds.json 合并。**当前只提交本节定义的内容**，原版声音继续由原版提供。

需要替换整份配置时，在 mod_info.json 的 replace 数组中声明 data/config/sounds.json。路径逐字符一致后，该操作改变整份文件的读取范围，**替换内容需要承担相关声音定义**。当前新增声音使用普通合并即可。

## 完整最小例

保存到 `data/config/sounds.json`。已有文件时合入这些自有键，music 里的条目放进已有 music 对象。

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

1. 保存声音配置，确认上一节资源位于对应 file 或 source 目录。
2. 重启游戏，检查 JSON 解析与声音资源加载记录。
3. 核对普通音效、两套分档集和 music 分类中的 ID。
4. 按下一节的具体接入播放声音，再调整某个条目的 volume，保存并重启比较。

预期结果：

- 配置可读取，文件路径指向已准备的 Ogg 资源。
- 普通音效和船体声音集位于顶层，音乐位于 music。
- 使用方能按 ID 取得资源，volume 变化后采用新值。
- 原版与其他自有条目继续按同路径合并读取。

## 故障分析

- **JSON 解析失败**：对象、数组、字符串或逗号不完整。
    - **解决方案**：按编辑器与日志检查错误位置，保存后重启。

- **资源不存在**：file 与 source 组合后的路径或大小写错误。
    - **解决方案**：普通音效按相对 MOD 根目录核对，音乐按 source 目录与 file 核对。

- **音乐条目没有播放**：分类或 source 字段未对应音乐读取结构。
    - **解决方案**：将 ID 登记在 music 对象中，补齐目录与文件。

- **声音集分档不符**：档位索引没有对应 sounds 数组资源。
    - **解决方案**：从 0 开始核对索引和每档 pitch、volume。

- **整文件替换范围不符**：replace 路径不同或替换配置缺少相关定义。
    - **解决方案**：核对目标路径与整份配置的实际职责。

## 总结

声音注册将 ID 连接到资源及播放参数。普通条目提供文件候选，分档集按船体等级选择资源与音量，音乐按 source 读取文件；三种结构分别供对应的使用方读取。

登记完成后，下一节写入对象引用并测试播放。资源、注册与播放分开核对，便能把没有声音的问题定位到相应位置。

## 建议参考项

- `data/config/sounds.json`：原版音效、分档集与音乐结构。
- `mod_info.json`：replace 整文件读取范围。
