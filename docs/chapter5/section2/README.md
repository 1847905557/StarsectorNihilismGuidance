# 为游戏对象加入音频

上一节登记的声音，现在填到武器、战术系统、舰船风格与势力上。每一处都只是一个字段，填上 ID 即可。

同样不需要写代码。

---

_本节预期阅读时间：\~6 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C5-S1] 的声音登记，本节填入的 ID 都来自那里；
- [C1-S5] 的 `GearIn_durin.wpn`、[C2-S3] 的 `GearIn_assaultfeed.system`、[C4-S3] 的 `hull_styles.json` 与 [C3-S1] 的 `gear_industry.faction`，本节逐一修改它们。

## 为都林设置开火声

打开 [C1-S5] 的 `GearIn_durin.wpn`，把开火声改为上一节登记的声音：

```json
	"fireSoundTwo":"GearIn_weapon_fire",
```

**`fireSoundTwo` 是每一发开火时的声音，`fireSoundOne` 是开火之前的声音**，例如原版大型能量炮的充能声。原版绝大多数武器只填 `fireSoundTwo`，都林也一样。

## 为突击填弹器设置声音

打开 [C2-S3] 的 `GearIn_assaultfeed.system`，加上持续声：

```json
    "useSound": "system_ammo_feeder",
    "loopSound": "GearIn_system_loop",
    "outOfUsesSound": "gun_out_of_ammo"
```

`useSound` 在激活的一瞬间播放，`loopSound` 在系统持续期间循环播放，结束时淡出。原版还有 `deactivateSound`，在系统结束时播放一次。`outOfUsesSound` 则在充能用完、玩家仍按下按键时播放。

`useSound` 与 `outOfUsesSound` 继续用原版加速填弹器的声音，只给持续阶段换了声音。玩家按下按键时听到的仍是熟悉的上膛声，随后机动推进器的嘶声提示系统正在生效。**一个战术系统的声音，应当让玩家不看屏幕也知道它开了、还开着、关了。**

## 为舰船风格设置引擎声

打开 [C4-S3] 的 `hull_styles.json`，把 `GEAR_TECH` 的引擎声改为上一节的声音集：

```json
		"engineLoopSet":"GearIn_engine_loop", # 引擎音效
```

上一节的声音集里有一行 `GEAR_TECH`，游戏按舰船风格与舰船体积在其中选出对应的引擎声。[C4-S3] 留下的欧米伽引擎声问题到这里就解决了。

舰船风格里其余八个声音字段继续用原版。护盾、排散、过载与爆炸声是玩家判断战况的依据，编者认为它们最好和原版保持一致：玩家听到原版的过载声，就知道那艘船过载了，不需要重新学一遍。

## 为齿轮工业市场设置音乐

打开 [C3-S1] 的 `gear_industry.faction`，修改 `music` 段：

```json
	"music":{ # 市场与遭遇时播放的音乐
		"theme":"GearIn_market_music",
		"market_neutral":"GearIn_market_music",
		"market_hostile":"GearIn_market_music",
		"market_friendly":"GearIn_market_music",
		"encounter_neutral":"music_hegemony_encounter_neutral",
		"encounter_hostile":"music_hegemony_encounter_hostile",
		"encounter_friendly":"music_hegemony_encounter_friendly",
	},
```

`market_*` 三项分别在玩家与齿轮工业关系中立、敌对、友好时进入它的市场播放，`encounter_*` 三项在星图上遇到它的舰队时播放。本例只有一首市场音乐，三种关系都用它；遭遇音乐暂时沿用霸主的。

**势力的音乐写在势力文件里，不需要任何代码。** 有了更多的曲子，再为不同关系分别指定即可。

### 进游戏核对

保存所有文件，重启游戏：

- 在模拟战中让工匠用都林开火，开火声比原版自动炮沉；
- 激活突击填弹器，先听到上膛声，持续期间有嘶声，结束时淡出；
- 驾驶工匠移动，引擎声为低科引擎声；
- 读取存档，进入 Nipher Prime 的市场，播放市场音乐。

## 常见问题

- **都林开火没有声音**：`fireSoundTwo` 的 ID 与 `sounds.json` 中登记的不一致。
    - **解决方案**：核对两处 ID，保存后重启。

- **突击填弹器的持续声不停**：`loopSound` 用的声音文件本身不是循环素材，或首尾有很长的静音。
    - **解决方案**：换成为循环准备的素材，修整首尾后重新导出。

- **齿轮工业的船引擎声仍像欧米伽**：`engineLoopSet` 没有改，或声音集里没有 `GEAR_TECH` 一行。
    - **解决方案**：核对 `hull_styles.json` 与 `sounds.json` 两处，保存后重启。

- **进入市场时报错，或没有音乐**：势力文件 `music` 段中的 ID 不在 `sounds.json` 的 `music` 段里。
    - **解决方案**：核对 ID，确认音乐条目写在 `music` 段内，保存后重启。

## 建议参考项

- `data/weapons/`：原版武器的 `fireSoundOne` 与 `fireSoundTwo` 写法。
- `data/shipsystems/`：原版战术系统的四种声音字段。
- `data/world/factions/`：原版势力的 `music` 段。
