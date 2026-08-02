package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * SoundPlayerAPI 用法：播放 UI 音效。
 *
 * 声音 ID GearIn_weapon_fire 由 S05-P01/S05-P02 所有，本页只消费，不新增声音 ID。
 */
public class GearInUiSoundPlayer {

    public static final String SOUND_ID_UI = "GearIn_weapon_fire";

    private GearInUiSoundPlayer() {
        // 工具类：不实例化
    }

    /** UI 音效（S05-P01 规格：素材建议 stereo）。 */
    public static void playFireUISound(float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_UI, 1f, volume);
    }
}
