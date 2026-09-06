# 为游戏对象加入音频

本节使用 [C5-S2] 已登记的声音 ID，把音频加入武器、战术系统、舰船风格、界面和音乐配置。每种对象在首次配置时说明读取文件和直接观察结果。

*本节预期阅读时间：约 15 分钟。*

*本节预期操作时间：约 20 分钟。*

## 学习目标

- 在武器和战术系统数据中引用声音 ID；
- 配置舰船风格、界面和音乐的声音入口；
- 在游戏内确认播放结果并定位未注册 ID、路径和分类错误。

## 前置知识

- [C5-S1] 的 OGG 文件；
- [C5-S2] 的 `sounds.json` 条目和声音 ID；
- [C4-S3] 的舰船风格文件；
- [C7-S4] 的 `SoundPlayerAPI` 基本调用。

## 配置武器声音

打开武器定义 `.wpn`，把已登记的 ID 写入 `fireSoundOne`、`fireSoundTwo` 等字段。游戏在武器开火时按这些字段查找声音定义，因此 ID 必须与 `sounds.json` 完全一致。

```json
{
  "id": "GearIn_weapon",
  "fireSoundOne": "GearIn_weapon_fire",
  "fireSoundTwo": "GearIn_weapon_fire"
}
```

命中音由武器和冲击音设置共同决定。需要关闭默认冲击音时使用 `.wpn` 的 `noImpactSounds`，自定义位置音效则在 Java 中调用 `Global.getSoundPlayer().playSound(...)`，方法约定见 [C7-S4]。

## 配置战术系统声音

在 `.system` 中填写激活、停用和循环字段。循环声音必须在系统仍处于激活状态时持续调用 `playLoop`，每帧调用会保持声音，停止调用后声音会淡出。

```json
{
  "id": "GearIn_burst_drive",
  "activateSound": "GearIn_weapon_fire",
  "loopSound": "GearIn_system_loop",
  "deactivateSound": "GearIn_weapon_fire"
}
```

如果系统脚本自行播放循环音，调用中必须传入正在播放的实体和非空位置、速度向量。具体 Java 代码放在 [C7-S4] 的播放器专题。

## 配置舰船风格声音

在 `hull_styles.json` 的舰船风格中填写 `engineLoopSet`、`engineAccelerateSet` 等声音集 ID。引擎控制器读取这些字段并在舰船移动时播放对应声音；声音集 ID 必须已经由 [C4-S3] 的资源定义提供。

```json
{
  "id": "GearIn_style",
  "engineLoopSet": "GearIn_engine_loop",
  "engineAccelerateSet": "GearIn_engine_accelerate"
}
```

## 配置界面音效

界面按钮或脚本调用 `Global.getSoundPlayer().playUISound("GearIn_weapon_fire", 1f, 1f)`。UI 音效使用双声道资源，调用只需要声音 ID、音高和音量。

## 配置音乐

音乐 ID 必须注册在 `sounds.json` 的 `music` 分类。选曲插件返回该 ID 后，音乐播放器读取对应的 `source` 和 `file`；普通音效条目不会被音乐播放器识别。

## 验证方案

1. 装配包含 `GearIn_weapon` 的舰船并开火；
2. 激活 `GearIn_burst_drive`，确认激活、循环和停用声音；
3. 在战斗中移动带有 `GearIn_style` 的舰船，确认引擎声音；
4. 打开使用 UI 播放调用的界面；
5. 进入市场并观察 `GearIn_market_theme`；
6. 查看日志并记录缺失 ID 或资源路径错误。

预期结果：五类入口都能找到已登记的声音，循环在持续调用期间保持，音乐从 `music` 分类播放，日志没有声音加载错误。

## 音乐选曲示例

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;

public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {
    @Override
    protected String getMarketMusicSetId(Object param) {
        return "GearIn_market_theme";
    }
}
```

## 战术系统播放示例

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class GearInBurstDriveSystem extends BaseShipSystemScript {

    public static final float SPEED_BONUS = 200f;
    public static final float ACCELERATION_BONUS = 200f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
            stats.getAcceleration().modifyFlat(id, ACCELERATION_BONUS * effectLevel);
        }

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }
        if (effectLevel > 0f) {
            Global.getSoundPlayer().playLoop("GearIn_system_loop", ship, 1f,
                    effectLevel, ship.getLocation(), ship.getVelocity());
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("engine power increased", false);
        }
        return null;
    }
}
```

## 故障分析

- **武器开火没有声音**：`.wpn` 中的 ID 未登记或拼写不同。

    - **解决方案**：对照 `sounds.json` 修正 ID。

- **循环声音只出现一下**：脚本没有每帧调用 `playLoop`。

    - **解决方案**：在系统或战斗插件的持续回调中调用。

- **引擎声音缺失**：舰船风格中的声音集 ID 不存在。

    - **解决方案**：检查 `hull_styles.json` 和声音定义。

- **界面声音空间位置异常**：使用了位置播放方法。

    - **解决方案**：界面调用必须使用 `playUISound`。

- **音乐没有开始**：ID 位于普通根对象而不是 `music` 分类。

    - **解决方案**：移动条目并保留 `source`。

## 总结

声音 ID 在 `sounds.json` 中登记后，武器和战术系统通过数据字段引用，舰船风格读取引擎声音集，界面和脚本通过 `SoundPlayerAPI` 播放，音乐选曲从 `music` 分类取得条目。验证时分别观察开火、系统循环、引擎、界面和市场音乐。

## 建议参考项

- `data/config/sounds.json`：`music` 分类条目的原版形态；
- `data/weapons/`：原版武器定义，`fireSoundOne`、`fireSoundTwo` 字段实例；
- `data/shipsystems/`：原版战术系统定义，`activateSound`、`loopSound`、`deactivateSound` 字段实例；
- `data/config/hull_styles.json`：原版舰船风格，`engineLoopSet`、`engineAccelerateSet` 等引擎声音集字段实例；
- `com.fs.starfarer.api.impl.MusicPlayerPluginImpl`：本节选曲示例继承的原版音乐插件。
