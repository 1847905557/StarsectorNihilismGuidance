# 调用声音播放器

声音资源登记之后，播放时机由功能代码决定。界面反馈在动作发生时触发，武器音效跟随事件，系统循环则随持续状态维持。本节使用 `SoundPlayerAPI` 实现这些调用，沿用第五章的声音 ID，并将常用操作整理为辅助类。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~15 分钟。_

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

## 选择与用途对应的播放入口

在运行回调中通过 `Global.getSoundPlayer()` 取得播放器。资源与 ID 已在第五章登记，下面使用普通声音条目播放音效，使用 `music` 分类条目播放音乐。选择方法时，同时考虑声道和触发方式：

### 播放方法速查表

| 方法            | 用途与关键输入                          |
| --------------- | --------------------------------------- |
| playUISound     | UI 反馈，ID、音高、音量；stereo 资源    |
| playSound       | 单次位置音效，加位置与速度；mono 资源   |
| playLoop        | 持续位置循环，加稳定声源对象；mono 资源 |
| playCustomMusic | 音乐集、整数秒淡入淡出、循环标记        |

## 在一次动作中播放音效

进入生涯的反馈使用 `GearIn_ui_feedback`，放在既有 `onGameLoad(...)` 中调用：

```java
Global.getSoundPlayer().playUISound("GearIn_ui_feedback", 1f, 1f);
```

UI 方法不接收位置，采用 stereo 资源。按钮点击和状态切换也可以在对应动作中调用。**每次 `playUISound(...)` 都会触发一次声音**，因此持续回调中每帧执行它，会不断启动新的提示。

武器开火、弹体命中等发生在场景中的事件，则使用位置音效。下面的局部片段从有效的 `source` 取得位置和速度，播放 `GearIn_weapon_fire`：

```java
Vector2f loc = source.getLocation();
Vector2f vel = new Vector2f(source.getVelocity());
Global.getSoundPlayer().playSound("GearIn_weapon_fire", 1f, 1f, loc, vel);
```

`loc` 提供本次声源坐标，`vel` 保存复制出的移动速度。位置音效使用 mono 资源，传入的两个向量都需要有效。放在开火或命中回调时，一次事件启动一次音效，声音的位置也对应产生它的实体。

## 在持续效果中维持循环

突击填弹器的循环声音需要在效果期间持续存在。使用 `playLoop(...)`，在持续回调中反复调用：

```java
Global.getSoundPlayer().playLoop(
        "GearIn_system_loop", source, 1f, 1f, source.getLocation(), source.getVelocity());
```

播放器用声音 ID 与 `playingEntity` 识别同一个循环。后续每帧调用为它续期，停止调用后淡出。本例用同一艘舰船作为 `source`，位置和速度随舰船更新，声音身份则保持不变。

::: warning 循环身份由对象决定

每帧创建新的声源对象，会被识别为不同循环，可能产生重叠。位置改变时仍使用原来的 `playingEntity`，只更新本次坐标和速度。

:::

这种播放方式与单次音效不同。系统结束时停止续期即可，循环的开始和结束条件仍由战术系统的状态分支决定。

## 切换已登记的音乐

音乐使用 `music` 分类中的 `GearIn_market_music`。在生涯调用中切换到这个条目：

```java
Global.getSoundPlayer().playCustomMusic(1, 1, "GearIn_market_music", true);
```

前两个整数分别指定当前曲目的淡出秒数与新曲目的淡入秒数，最后的 `true` 指定循环播放。本例两种过渡都为 1 秒，实际音频由注册条目的 `source` 和 `file` 提供。

第五章的音乐插件在市场场景中返回 ID，这里则由代码直接选择曲目。调试时可以在一次读档回调中调用，确认切换后再回到市场比较原有选择；资源登记相同，负责触发的代码不同。

## 完整最小例

保存为 `src/data/scripts/gearin/GearInSoundPlayer.java`。类中集中保留声音 ID 与常用参数，使用方在前面的事件或持续回调中调用：

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

辅助类调用播放器，系统与事件代码决定何时使用它。特别是 `playLoopSound(...)`，仍要在效果期间连续调用；仅调用一次辅助方法，不会建立一套自动推进的循环逻辑。

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

单次音效随动作触发，循环靠持续调用维持，音乐按分类 ID 切换。播放器读取注册资源，位置与速度来自当前实体，循环身份由稳定的声源对象提供。

辅助类只是复用这些调用。声音是否出现、持续多久，仍由实际功能的事件与状态决定，验证时也就沿资源、参数和调用时机逐项定位。

## 建议参考项

- `com.fs.starfarer.api.SoundPlayerAPI`：播放方法、声道与秒单位参数。
- `data/config/sounds.json`：ID、分类和资源。
- `Starsector/starsector-core/starsector.log`：加载和调用记录。
