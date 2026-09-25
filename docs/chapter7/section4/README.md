# 调用声音播放器

本节从已经登记的声音 ID 开始，使用 `Global.getSoundPlayer()` 播放界面、位置、循环和音乐声音。每种调用都对应明确的资源分类、参数和运行阶段，便于在游戏中复现并定位问题。

*本节预期阅读时间：约 18 分钟。*

*本节预期操作时间：约 15 分钟。*

## 学习目标

完成后，您将能够：

- 取得 `SoundPlayerAPI` 并选择适合场景的播放方法；
- 为 UI、位置和循环声音提供正确的 ID、参数、坐标和速度；
- 在持续回调中维护循环声源，并按 `music` 分类播放自定义音乐；
- 根据游戏画面和 `starsector.log` 定位声音资源与调用错误。

## 前置知识

- [C5-S1] 的 OGG 格式、声道和循环资源；
- [C5-S2] 的 `sounds.json` 声音 ID、`music` 分类、`source` 和 `file`；
- [C7-S2] 的 `Global` 服务访问阶段；
- 能够在战斗内触发一次脚本调用并查看 `starsector.log`。

## 取得播放器

`Global.getSoundPlayer()` 返回当前游戏的 `SoundPlayerAPI`。播放器只接受已经在 `sounds.json` 中登记的声音 ID；调用时还必须提供合法的音量和音高，资源路径错误会在加载日志中留下对应错误。

## 播放 UI 音效

界面反馈使用 `playUISound(String id, float pitch, float volume)`。它不接收空间坐标，声音会按 UI 音效播放；`pitch` 控制音高，`volume` 控制音量。`GearIn_weapon_fire` 已在 [C5-S2] 登记，可以直接用于按钮或状态切换反馈：

::: warning 严禁在战斗循环中调用 UI 音效
UI 音效只允许用于界面反馈。它不参与战斗中的位置合成，严禁在战斗循环中随意调用，否则会把本应具有空间位置的声音变成无位置的全局提示，并造成重复播放。
:::

```java
Global.getSoundPlayer().playUISound("GearIn_weapon_fire", 1f, 1f);
```

UI 资源必须使用 **stereo 声道**。传入普通位置音效或未注册 ID 时，界面不会得到预期声音，先核对 ID 和声音条目的 `source`、`file`。

## 播放位置音效

战斗中的开火、碰撞和爆炸需要把声音放在实体位置。调用 `playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel)` 时，`loc` 是声源坐标，`vel` 是当前速度，播放器据此计算空间衰减和移动中的听感。

从 `CombatEntityAPI` 读取两个向量后再调用播放器：

```java
Vector2f loc = source.getLocation();
Vector2f vel = new Vector2f(source.getVelocity());
Global.getSoundPlayer().playSound("GearIn_weapon_fire", 1f, 1f, loc, vel);
```

位置资源必须使用 mono 声道，`loc` 和 `vel` 必须为非空向量。复制速度向量可以避免后续更新实体状态时改变本次调用使用的参数。

## 维持循环音效

系统持续声使用 `playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel)`。`playingEntity` 是循环声源的身份标识；同一声音 ID 和同一对象组合对应一个循环实例，因此持续状态必须始终传入同一个实体对象。

循环调用必须放在每帧或等价的持续回调中，并持续提交当前坐标和速度：

```java
Global.getSoundPlayer().playLoop(
        "GearIn_system_loop", source, 1f, 1f, source.getLocation(), source.getVelocity());
```

当系统停用或实体失去持续条件后停止调用 `playLoop`，该循环声音会自然淡出。每次换用新的 `playingEntity` 都会创建新的声源身份，容易造成重叠播放；`GearIn_system_loop` 的条目也必须与 [C5-S2] 的循环资源配置一致。

## 播放音乐

自定义音乐使用 `playCustomMusic(float fadeIn, float fadeOut, String id, boolean loop, boolean interruptable)`。音乐 ID 必须来自 `sounds.json` 的 `music` 分类，并且条目同时提供有效的 `source` 和 `file`：

```java
Global.getSoundPlayer().playCustomMusic(0.5f, 0.5f, "GearIn_market_music", true, true);
```

音乐调用负责选择和切换曲目，UI 或位置音效 ID 不能替代音乐条目。验证音乐时要单独核对分类、路径、淡入淡出参数和是否允许打断，避免把普通音效加载成功误判为音乐可播放。

## 完整最小例

`GearInSoundPlayer` 把播放器访问和常用参数集中到一个可复用的辅助类。入口类只提供全局服务，其他功能代码按需要调用这些方法。

::: details 完整成品：GearInSoundPlayer

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
:::

## 验证方案

前置数据：[C5-S2] 的 `GearIn_weapon_fire`、`GearIn_system_loop` 和 `music` 分类条目已登记；本节 `GearInSoundPlayer` 已按 [C6-S1] 至 [C6-S4] 构建进 JAR。

1. 启动游戏并进入能够触发脚本的界面，调用 `playUISound("GearIn_weapon_fire", 1f, 1f)`，观察 UI 音效是否播放。
2. 在战斗实体当前位置调用 `playPositionalSound`，移动实体并改变距离，观察声音是否随位置衰减。
3. 在系统持续激活的每帧回调中调用 `playLoopSound`，观察 `GearIn_system_loop` 是否连续播放。
4. 结束系统状态并停止调用循环方法，观察循环声音是否淡出且不会继续产生新的声源。
5. 使用 `playCustomMusic(0.5f, 0.5f, "GearIn_market_music", true, true)` 播放 `music` 条目，观察曲目是否按循环和淡入淡出参数切换。
6. 重启游戏后打开 `Starsector/starsector-core/starsector.log`，搜索 `GearIn_weapon_fire`、`GearIn_system_loop` 和 `GearIn_market_music`，核对资源加载与调用错误。

预期结果：

- UI 音效在界面触发时播放，位置音效随实体位置变化；
- 循环声音只在持续续期期间保持，停止续期后淡出；
- `music` 分类的曲目能够播放，未注册 ID、错误分类或错误路径在日志中可定位；
- 代码使用非空位置和速度向量，音量与音高保持在预期范围。

## 故障分析

- **播放器调用没有反应**：`Global.getSoundPlayer()` 在当前阶段返回不可用服务，或调用发生在服务初始化之前。
    - **解决方案**：把调用移到 [C7-S2] 说明的可用阶段，并在日志中确认播放器服务已经初始化。

- **UI 音效没有播放**：声音 ID 未登记，或资源条目的 `source`、`file` 路径无效。
    - **解决方案**：核对 `sounds.json` 中的 `GearIn_weapon_fire`、资源路径和文件名，再重启游戏。

- **位置音效抛出空指针**：`loc` 或 `vel` 为空，或者实体已经失去有效的运行时状态。
    - **解决方案**：从有效的 `CombatEntityAPI` 读取坐标和速度，并在调用前检查实体对象。

- **循环声音只播放一次**：持续状态中没有使用同一个 `playingEntity` 每帧调用 `playLoop`。
    - **解决方案**：把调用放入持续回调，保持实体对象和声音 ID 不变，状态结束后再停止续期。

- **音乐没有开始**：ID 位于普通音效分类，或音乐条目缺少 `source`、`file`。
    - **解决方案**：将条目放入 `music` 分类，核对路径和淡入淡出参数，再搜索加载日志。

- **日志中找不到声音错误**：查看了错误日志文件或使用了与资源 ID 不同的搜索词。
    - **解决方案**：打开 `Starsector/starsector-core/starsector.log`，使用完整声音 ID 和 `GearIn` 前缀搜索。

## 总结

本节将已登记的声音 ID 分别用于界面、空间位置、持续循环和音乐播放。UI 调用只需要音高和音量，位置调用还必须提供坐标与速度，循环调用必须用稳定的声源对象持续续期，音乐调用则读取 `music` 分类。遇到没有声音、重复声源或曲目无法切换时，先核对分类和参数，再按完整 ID 搜索 `starsector.log`。

## 建议参考项

- `com.fs.starfarer.api.SoundPlayerAPI`：UI、位置、循环和自定义音乐的播放器方法；
- `data/config/sounds.json`：`music` 分类与 `source`、`file` 的原版对照；
- `Starsector/starsector-core/starsector.log`：核对资源加载与调用错误的运行日志。
