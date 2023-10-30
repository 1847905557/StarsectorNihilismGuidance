# Global/Global 衍生/settings 精确聚焦

*本节预期阅读时间：~10 分钟。*

*本节预期操作时间：~10 分钟。*

## Global

Global 同样也是关键类，且日后将以极高频率出现在您的 import 中。它主要的作用是**提供几个最关键的静态方法，允许对 CombatEngineAPI/SectorAPI/SettingsAPI/SoundPlayerAPI/Logger 进行访问**，可以将其理解为一种重要的中枢。而本章则将对其中的几个简单类进行介绍。

```java
	// 日后会频繁使用的几个关键 getter
	// getFactory 可以无视，它没什么用

	public static Logger getLogger(Class c) {
		Logger log = Logger.getLogger(c);
		return log;
	}

	public static SoundPlayerAPI getSoundPlayer() {
		return soundPlayer;
	}

	public static SettingsAPI getSettings() {
		return settingsAPI;
	}
	
	public static SectorAPI getSector() {
		return sectorAPI;
	}
	
	public static CombatEngineAPI getCombatEngine() {
		return combatEngine;
	}
```

## Logger

Logger 用于在 log 中输出任意一行消息，通常被用于日志记录，也可以用来调试以及检查某些变量的数值是否符合预期。您最常用的形式可能看上去是这样的：

```java
Global.getLogger(this.getClass()).info("任意内容");
```

任何时间，任何地点，传入类，开箱即用。

## SoundPlayerAPI

SoundPlayerAPI 用于播放音效、UI 音效和音乐，以及控制它们的暂停、获取状态等。这个 API 并不是很好介绍，因为**它是少见的带有较完整文档的 API**，且所有常用方法都带有文档。

```java
	// 最常用的三个方法

	/**
	 * UI sound files should be stereo.
	 * @param id
	 * @param pitch
	 * @param volume
	 */
	SoundAPI playUISound(String id, float pitch, float volume);
	
	/**
	 * Sound file must be *mono* for it to be properly played within the engine.
	 * @param id
	 * @param pitch
	 * @param volume
	 * @param loc
	 * @param vel
	 * @return
	 */
	SoundAPI playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel);
	
	/**
	 * Loop a sound. Must be called every frame or the loop will fade out. Should be mono.
	 * 
	 * @param id
	 * @param playingEntity Required. Used to help uniquely identify playing loops. Also used to figure out which loops to not play (i.e., same entity playing multiples of the same loop would only play the one.)
	 * @param pitch
	 * @param volume
	 * @param loc
	 * @param vel
	 */
	void playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel);

	// 例如 Global.getSoundPlayer().playSound(各类参数);
	// 其余不常用方法请自行 ctrl+左键
```

playUISound 和 playSound 有所不同，**前者只支持双声道音效而后者只支持单声道音效**，前者没有播放点而后者则是在 loc 的位置播放并根据方位和距离计算具体声音。vel 不可为 null。

## SettingsAPI

