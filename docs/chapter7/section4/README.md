# 调用声音播放器

第五章已经把声音接到武器、系统和市场。本节直接使用 SoundPlayerAPI，讲清 UI、位置、循环与音乐的参数和调用范围，再把常用操作整理为辅助类。资源 ID 继续沿用既有登记，调用代码放到实际触发的位置。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 取得播放器并选择对应方法；
- 提供音高、音量、位置、速度与声源身份；
- 在持续回调中维持循环；
- 播放 music 条目并控制淡入淡出；
- 按资源和触发时机定位错误。

## 前置知识

- [C5-S1] 的声道与循环资源；
- [C5-S2] 的声音 ID、music、source 和 file；
- [C5-S3] 的实际系统播放；
- [C7-S2] 的服务阶段；
- 能够在战斗和生涯回调调用当前方法。

## 取得播放器

`Global.getSoundPlayer()` 返回当前播放服务。先完成第五章注册，再在服务可用的运行回调调用；音效方法使用普通声音 ID，音乐方法使用 music 分类 ID。

### 播放方法速查表

| 方法 | 用途与关键输入 |
|---|---|
| playUISound | UI 反馈，ID、音高、音量；stereo 资源 |
| playSound | 单次位置音效，加位置与速度；mono 资源 |
| playLoop | 持续位置循环，加稳定声源对象；mono 资源 |
| playCustomMusic | 音乐集、整数秒淡入淡出、循环标记 |

## UI 与位置音效

界面反馈沿用 GearIn_ui_feedback：

```java
Global.getSoundPlayer().playUISound("GearIn_ui_feedback", 1f, 1f);
```

它不接收位置。第五章 onGameLoad 提供一次触发，按钮和状态切换也放进相应动作回调。**一次动作对应一次反馈**，持续执行的循环里按每帧播放 UI 声音，会不断触发新的提示。

位置声音使用有效实体的坐标和速度：

```java
Vector2f loc = source.getLocation();
Vector2f vel = new Vector2f(source.getVelocity());
Global.getSoundPlayer().playSound("GearIn_weapon_fire", 1f, 1f, loc, vel);
```

loc 指定声源，vel 提供移动速度，当前用复制向量保存此次参数。资源为 mono，两个向量均要实际存在。调用位置可以在命中、开火或其他单次事件中，避免同一声音每帧重复启动。

## 用稳定对象维持循环

系统效果期间采用：

```java
Global.getSoundPlayer().playLoop(
        "GearIn_system_loop", source, 1f, 1f, source.getLocation(), source.getVelocity());
```

同一 ID 和同一 playingEntity 构成声源身份，每帧调用持续续期，停止调用后淡出。第五章已经用舰船对象维持突击填弹器循环，当前沿用这套关系。

**持续声音需要稳定的身份，位置则每次更新。** 每帧新建声源对象会形成不同循环，可能重叠；实体继续移动时，坐标和速度仍由当前对象提供。

## 播放自定义音乐

music 条目可以直接切换：

```java
Global.getSoundPlayer().playCustomMusic(1, 1, "GearIn_market_music", true);
```

两个整数参数分别为当前曲目淡出与新曲目淡入的秒数，true 为循环播放。传入的 ID 来自 music 分类，source 和 file 由注册条目提供。

直接调用用于当前功能选择曲目，第五章音乐插件则随市场场景返回 ID。需要调试播放器时，可以在一次读档回调中调用当前方法，观察 1 秒切换，再回到市场场景比较原有选择。

## 完整最小例

辅助类保存为 `src/data/scripts/gearin/GearInSoundPlayer.java`，使用方按事件调用方法。

::: details 完整成品：GearInSoundPlayer

```java
package data.scripts.gearin;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;

public class GearInSoundPlayer {

    public static final String SOUND_ID_SYSTEM_LOOP = "GearIn_system_loop";
    public static final String SOUND_ID_WEAPON_FIRE = "GearIn_weapon_fire";
    public static final String SOUND_ID_UI_FEEDBACK = "GearIn_ui_feedback";
    public static final String MUSIC_ID_MARKET = "GearIn_market_music";

    private GearInSoundPlayer() {
    }

    public static void playUISound(float pitch, float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_UI_FEEDBACK, pitch, volume);
    }

    public static void playPositionalSound(CombatEntityAPI source) {
        Vector2f loc = source.getLocation();
        Vector2f vel = new Vector2f(source.getVelocity());
        Global.getSoundPlayer().playSound(SOUND_ID_WEAPON_FIRE, 1f, 1f, loc, vel);
    }

    public static void playLoopSound(CombatEntityAPI source) {
        Global.getSoundPlayer().playLoop(
                SOUND_ID_SYSTEM_LOOP, source, 1f, 1f, source.getLocation(), source.getVelocity());
    }

    public static void playMarketMusic() {
        Global.getSoundPlayer().playCustomMusic(1, 1, MUSIC_ID_MARKET, true);
    }
}
```

:::

循环方法只负责播放，实际持续条件由系统或其他调用方提供。这个类复用常用 ID 与参数，并不为声音另建一套生命周期。

## 验证方案

1. 保存辅助类，确认声音登记和媒体存在，构建 JAR 并重启。
2. 在既有 onGameLoad 调用 playUISound，进入生涯听取一次 UI 反馈。
3. 在 GearInHitLogger.onHit 用传入 projectile 调用 playPositionalSound，比较声源位置与距离。
4. 在突击填弹器持续效果中用同一舰船调用 playLoopSound，结束时停止续期。
5. 在一次生涯调用 playMarketMusic，观察整数秒的淡入淡出与循环。
6. 查看本次日志，按完整 ID 检查注册、分类和资源错误。

预期结果：

- UI 反馈采用 stereo，位置声音随实体坐标和距离表现。
- 循环在持续续期时保持，结束后淡出。
- GearIn_market_music 从 music 分类播放，当前切换采用 1 秒参数。
- 位置与速度向量有效，当前声源身份保持稳定。
- 资源与调用问题可按 ID 和运行阶段定位。

## 故障分析

- **调用发生在播放服务建立前**：当前阶段未提供 SoundPlayer。
    - **解决方案**：在实际运行回调取得服务，核对对应阶段。

- **UI 或位置音效不符**：资源声道、ID 或方法用途不同。
    - **解决方案**：UI 使用 GearIn_ui_feedback，位置使用 GearIn_weapon_fire，按第五章核对资源。

- **位置调用空指针**：实体引用、loc 或 vel 无效。
    - **解决方案**：在有效事件回调中读取当前实体与向量。

- **循环只播放一次或重叠**：续期中断，或每次使用不同声源对象。
    - **解决方案**：持续状态中使用稳定对象调用，结束后停止。

- **音乐未切换**：ID 分类、source／file 或切换参数不对应。
    - **解决方案**：核对 music 条目、路径和整数秒参数，再触发一次调用。

- **日志查不到本次状态**：搜索词或当前日志文件不同。
    - **解决方案**：查看 starsector-core 的当前日志，按完整声音 ID 和 GearIn 搜索。

## 总结

声音播放器的四种入口分别处理界面、位置、持续循环与音乐。声道由用途决定，位置参数由当前对象提供，循环身份保持稳定，音乐按 music 集选择。

播放器负责实际播放，调用方决定何时触发和持续多久。把这两部分与资源注册对应起来，便能区分素材、参数和时机造成的问题。

## 建议参考项

- `com.fs.starfarer.api.SoundPlayerAPI`：播放方法、声道与秒单位参数。
- `data/config/sounds.json`：ID、分类和资源。
- `Starsector/starsector-core/starsector.log`：加载和调用记录。
