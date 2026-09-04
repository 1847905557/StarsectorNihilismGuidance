# 调用声音播放器

本节集中说明 `Global.getSoundPlayer()` 返回的 `SoundPlayerAPI`。它把 [C5-S2] 注册的声音 ID 播放为界面音效、位置音效、循环音效或音乐。

*本节预期阅读时间：约 12 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

- 选择 UI、位置和循环播放方法；
- 为位置播放提供有效的坐标和速度向量；
- 在持续回调中续期循环声音，并定位未注册 ID。

## 前置知识

- [C5-S1] 的声道和 OGG 文件；
- [C5-S2] 的声音 ID 注册；
- [C6-S3] 的 `Global` 服务阶段。

## 取得播放器

调用 `Global.getSoundPlayer()` 取得 `SoundPlayerAPI`。UI 声音使用 `playUISound(id, pitch, volume)`，位置声音使用 `playSound(id, pitch, volume, loc, vel)`，循环声音使用 `playLoop(id, playingEntity, pitch, volume, loc, vel)`。

UI 资源必须为 stereo；位置资源必须为 mono；`loc` 和 `vel` 必须为非空向量。循环方法必须在每帧持续调用，同一 `playingEntity` 和声音 ID 只保持一个循环实例。

## 播放音乐

`playCustomMusic(id, ...)` 使用 [C5-S2] 的 `music` 分类条目。普通音效 ID 不会被音乐播放器识别；音乐条目必须提供 `source` 和 `file`。

## 组合播放工具类

```java
package data.scripts.gearin;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
public class GearInSoundPlayer {

    public static final String SOUND_ID_SYSTEM_LOOP = "GearIn_system_loop";
    public static final String SOUND_ID_WEAPON_FIRE = "GearIn_weapon_fire";

    private GearInSoundPlayer() {
    }

    public static void playUISound(float pitch, float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_WEAPON_FIRE, pitch, volume);
    }

    public static void playPositionalSound(CombatEntityAPI source) {
        Vector2f loc = source.getLocation();
        Vector2f vel = new Vector2f(source.getVelocity());
        Global.getSoundPlayer().playSound(SOUND_ID_WEAPON_FIRE, 1f, 1f, loc, vel);
    }

    public static void playLoopSound(CombatEngineAPI engine, CombatEntityAPI source) {
        Global.getSoundPlayer().playLoop(
                SOUND_ID_SYSTEM_LOOP, source, 1f, 1f, source.getLocation(), source.getVelocity());
    }
}
```

## 验证方案

1. 在界面调用 `playUISound`，确认 UI 音效播放；
2. 在战斗实体位置调用 `playPositionalSound`，确认声音随位置衰减；
3. 每帧调用 `playLoopSound`，确认循环持续；
4. 停止调用循环方法，确认声音淡出；
5. 传入未注册 ID，查看日志中的加载错误。

预期结果：三种播放方法使用正确声道和参数，循环只在持续调用期间保持，错误 ID 能在日志中定位。

## 故障分析

- **UI 音效没有播放**：ID 未在 `sounds.json` 注册。

    - **解决方案**：补充注册并重启。

- **位置音效抛出空指针**：`loc` 或 `vel` 为空。

    - **解决方案**：传入实体当前位置和速度副本。

- **循环只播放一次**：没有在每帧调用 `playLoop`。

    - **解决方案**：把调用放入持续回调。

- **音乐没有开始**：ID 位于普通音效分类。

    - **解决方案**：移动到 `music` 并补齐 `source`。

## 总结

`SoundPlayerAPI` 通过声音 ID 提供 UI、位置和循环播放。调用位置方法时必须提供非空向量，循环方法必须每帧续期，音乐 ID 必须来自 `music` 分类。

## 建议参考项

- [C5-S1]：Ogg Vorbis、声道和循环资源；
- [C5-S2]：声音定义和 `music` 分类；
- [C6-S3]：`Global` 服务阶段。
