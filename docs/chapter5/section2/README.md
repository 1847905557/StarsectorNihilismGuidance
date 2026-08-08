# 注册音频资源

本节把资源层准备好的三个 OGG 注册为可被数据和代码引用的 sound ID。注册文件必须位于 `data/config/sounds.json`；游戏只从这个固定路径收集声音定义，放在别处的文件不会被读取，使用方的播放请求会因找不到 ID 而失败。

*本节预期阅读时间：~8 分钟。*

*本节预期操作时间：~10 分钟。*

## 学习目标

完成后，您将能够：

- 把三个 OGG 注册为可被数据和代码引用的 sound ID(普通音效与音乐)；
- 说明 sound ID、`file`、`source`、`pitch`、`volume`、`randomStart` 与多差分的含义；
- 解释 MOD 声音定义的合并规则 (追加与替换)；
- 从日志定位解析、合并与路径错误。

## 前置知识

- [C5-S1] 的 Ogg Vorbis 编码、MOD 根目录相对路径、声道与循环边界要求；
- 理解 sound ID 的全局唯一性、大小写匹配和使用方引用关系；
- 理解 JSON 对象、数组、字段合并与 `/` 路径分隔符的写法。

## 梳理音频注册关系

本节处于声音链的注册层：

```text
sounds/.../*.ogg          资源层 (C5-S1)：三个 OGG、路径与规格
        ↓ 相对于 MOD 根目录的路径
data/config/sounds.json   注册层 (本节)：sound ID → OGG 路径与播放参数
        ↓ sound ID
SoundPlayerAPI / .wpn     消费层 (C5-S3)：代码按 ID 播放、数据按 ID 引用
```

代码使用方通过 `Global.getSoundPlayer()` 拿到的 `SoundPlayerAPI` 按 ID 播放；数据使用方用 `.wpn` 的 `fireSoundOne` / `fireSoundTwo` 字段按 ID 引用。

注册层是 ID 与路径之间唯一的一层：ID、路径、使用方三者必须一致，任何一处改了就失效。

## 游戏如何读取声音定义

启动时，游戏收集核心与所有启用 MOD 的 `data/config/sounds.json`，按加载顺序合并成一个声音定义，并预载声音文件 (日志出现 `Loading sound sets`)。结构规则如下：

1. 顶层 `music` 对象是音乐分类：每个键是一个音乐 ID，值是条目数组。
2. 顶层其余键是普通音效 ID，一次性音效与循环音效都按普通条目注册，值同样是条目数组；也可以使用带 `sounds` 数组和 hull size 映射的复合对象 —— 复合对象必须带 `sounds` 数组，缺失时启动解析报错。复合对象的 hull size 分档结构随消费层讲解。
3. 每个条目的 `file`(或音乐条目的 `source` + `file`) 解析为 MOD 根目录下的相对路径，游戏在启动阶段预载这些 OGG；任一文件缺失或损坏都会让启动中止 (可观察结果见故障分析)。
4. `sounds.json` 条目本身没有 `loop` 字段：文件是否循环播放由使用方决定，循环播放的 API 用法在消费层给出。

运行期使用方以 sound ID 引用这些定义：代码通过 `SoundPlayerAPI` 播放 (`playUISound`、`playSound`、`playLoop`、`playCustomMusic` 等)；

数据侧 `.wpn` 武器规格用 `fireSoundOne` / `fireSoundTwo` 字段引用 (原版实例：`"fireSoundTwo":"antimatter_blaster_fire"`，该 ID 同时出现在 `sounds.json` 中)。

## 完整最小 sounds.json

文件：`data/config/sounds.json`(位于 MOD 根目录下)

```json
{
  "music": {
    "GearIn_market_theme": [
      {
        "file": "GearIn_market_theme.ogg",
        "source": "sounds/gear_in/music/",
        "volume": 0.4
      }
    ]
  },
  "GearIn_system_loop": [
    {
      "file": "sounds/gear_in/sfx_system/GearIn_system_loop.ogg",
      "pitch": 1.0,
      "volume": 0.8
    }
  ],
  "GearIn_weapon_fire": [
    {
      "file": "sounds/gear_in/sfx_weapon/GearIn_weapon_fire.ogg",
      "pitch": 1.0,
      "volume": 0.8
    }
  ]
}
```

音乐必须放在根对象的 `music` 分类中，否则音乐播放器不会识别它。普通音效直接以 sound ID 作为根对象字段。音乐条目使用 `source` 加 `file`；普通音效条目直接写 MOD 根目录相对路径。

::: caution 必须：路径一律使用 / 分隔符
不要使用 Windows 反斜杠 —— 反斜杠路径会破坏跨平台路径语义，后果同样是 OGG 定位失败。
:::

### 验证方案

1. 把本例 JSON 保存为 MOD 根目录下的 `data/config/sounds.json`，并确认前置知识中的三个 OGG 已按目录树就位。
2. 启用 MOD，启动游戏到主菜单后退出。
3. 打开 `Starsector/starsector-core/starsector.log`，查看音频加载记录。

预期结果：

- 前置知识中的三个 OGG 已按目录树就位。

## 字段与多差分

### 注册字段速查表

| 字段 | 作用 | 规则强度 |
|---|---|---|
| sound ID | 使用方引用的键 | 必须全局唯一，且与使用方完全一致 (大小写敏感)|
| `file` | OGG 文件名或路径 | 必须指向实际文件，大小写必须一致；普通音效条目写 MOD 根目录相对路径，音乐条目可只写文件名 (与 `source` 拼接)|
| `source` | 音乐文件所在目录或容器 | 音乐条目必须提供；与 `file`(或 `files` 中的文件名) 拼接后必须能定位 OGG。原版使用 `sounds/music/music.bin` 容器，成熟 MOD 实例使用目录 (如 `"sounds/music/"`)|
| `pitch` | 音高倍率 | 可选；`1` 为原始音高 (原版取值约 `0.5` 到 `2.0`)|
| `volume` | 此条目的音量倍率 | 可选；建议从小于等于 `1` 开始试听 |
| `randomStart` | 音乐是否随机起点 | 可选；只在使用 `files` 数组的条目中生效 (与 `file` 同时出现时被忽略)；原版仅在 `music` 分类条目中使用 |
| `files` | 音乐候选文件数组 | 可选；与 `source` 配合一次声明多个曲目，与 `file` 二选一 (原版对部分音乐 ID 使用，如 `music_default_market_neutral`)|

同一 ID 的数组可放多个条目，使用方播放时从中选择，这就是差分。差分文件必须是同一事件的可互换版本，并应保持相近响度：

```json
{
  "GearIn_weapon_fire": [
    {"file": "sounds/gear_in/sfx_weapon/GearIn_weapon_fire_01.ogg", "pitch": 1.0, "volume": 0.8},
    {"file": "sounds/gear_in/sfx_weapon/GearIn_weapon_fire_02.ogg", "pitch": 1.0, "volume": 0.8}
  ]
}
```

::: warning 差分文件需要单独准备
上面的 `_01`、`_02` 两个文件不属于资源层声明的三个 OGG，需要额外准备，并与 `GearIn_weapon_fire.ogg` 保持同一语义和相近响度。
:::

## 合并边界

::: info 为什么要合并
游戏启动时把核心与所有启用 MOD 的声音定义合并成同一个注册表，因此每个 MOD 只需提交自己的增量条目，而不必维护一份完整文件。
:::

合并规则：

- 同一 sound ID 的条目数组按加载顺序追加，同一个 ID 可以同时拥有多个 MOD 的条目。
- ID 以 `music_` 开头 (包括 `music` 分类内的条目) 时是整组替换：后加载者清空先加载者的条目。
- 复合对象中的标量字段 (如 `pitch`、`volume`) 以后加载者为准；`sounds` 数组仍按追加处理。
- 同一 ID 在一侧是数组、另一侧是复合对象时，合并阶段直接抛错。日志出现 `Value for key %s must be a JSON array` 或 `must be a JSON object`(`%s` 是实际键名)，游戏无法完成启动。

因此您的文件只应声明 GearIn 拥有的条目，不应复制整份原版 `sounds.json`。

::: caution 禁止：复用原版或其他 MOD 的 sound ID
复用属于全局冲突：普通 ID 会让既有使用方的差分池混入您的素材，`music_` 前缀 ID 会整组覆盖原版音乐。
:::

成熟 MOD 实例采用语义前缀 ID；音乐集中在 `music`，引擎与系统循环声音使用带 `sounds` 数组和 hull size 映射的复合对象。复合对象的完整讲解在消费层结合使用方示例给出。

## 静态验证与预期结果

逐条核对：

- 三个 ID 与资源层单元 [C5-S1] 声明一致且全局唯一；
- 所有 `file`/`source` 拼接路径与资源层单元 [C5-S1] 的目录树逐项一致且文件存在；
- 文件为 Ogg Vorbis；
- 音乐条目带 `source`；
- JSON 花括号与数组闭合 (自检方法见下)；
- 路径使用 `/`；
- 使用方计划使用完全相同的大小写。

::: tip 用严格 JSON 校验
`#` 注释与尾逗号是原版文件自带的宽松写法，自己的文件不要依赖它们，用支持严格 JSON 的编辑器或解析器校验。
:::

本节结束后游戏能加载三个 sound ID，启动日志 (`Starsector/starsector-core/starsector.log`) 出现 `Loading sound sets`，且没有 `sounds.json` 解析、合并或 OGG 加载错误。它们仍需要武器、系统、代码或音乐字段消费才会播放。

## 故障分析

- **启动阶段出现 JSON 解析异常**：花括号、逗号或字符串引号错误会阻止注册。先恢复本节最小对象，再逐项增加字段；重启后日志应越过该 JSON 文件。
- **启动阶段出现 `Value for key %s must be a JSON array` 或 `must be a JSON object`**：同一 ID 被其他 MOD 或原版注册为不同结构，一侧是数组，另一侧是复合对象。改用 GearIn 专属 ID，或让两侧保持同构；重启后不应再出现结构冲突。
- **启动中止并出现 `Sound with filename [...] not found or failed to load`**：普通音效或音乐条目的路径无法定位 OGG。按 MOD 根目录逐段核对目录名、大小写和扩展名；修正后启动应完成声音加载。
- **运行期出现 `Sound set with id [...] not found`**：使用方引用了未注册 ID，或大小写不同。同步注册表和使用方的 ID，重新触发播放后日志应找到声音。
- **音乐不播放但普通音效正常**：音乐条目没有放入 `music`，因此会按普通音效注册；裸 `file` 会按 MOD 根目录相对路径寻找，音乐播放器也不会识别该 ID。把条目放入 `music` 并确认场景确实引用该 ID，进入对应场景后应开始播放。
- **播放音量异常**：素材响度与 `volume` 共同作用，源文件削波时只降低注册音量仍会失真。回源工程降低增益并重新导出，再用注册层做小幅调整，播放结果应无削波。

## 总结

声音注册链是 sound ID → `sounds.json` 条目 → OGG 路径；音乐还多一层 `music` 分类。MOD 声音定义按追加/替换规则合并，因此 ID 必须保持全局唯一。

注册完成之后，这三个 ID 才成为可被数据字段与播放 API 引用的资源；声音链的下一环是消费侧，把 ID 接入武器、引擎、UI、环境和音乐使用方，循环、位置与音量语义在那里兑现。

## 建议参考项

- `data/config/sounds.json`：声音注册文件 (MOD 根目录下的固定路径)。
- `Starsector/starsector-core/data/config/sounds.json`：原版声音定义，结构与字段参考 (含 `#` 注释、尾逗号与 `music.bin` 容器写法)。
- `Starsector/starsector-core/sounds`：原版 OGG 目录 (`music`、`sfx_systems`、`sfx_wpn_guns` 等)。
- `Starsector/starsector-core/data/weapons`：`.wpn` 的 `fireSoundOne`/`fireSoundTwo` 数据引用示例。
- `com.fs.starfarer.api.SoundPlayerAPI`：声音播放 API(`playUISound`、`playSound`、`playLoop`、`playCustomMusic`)。
