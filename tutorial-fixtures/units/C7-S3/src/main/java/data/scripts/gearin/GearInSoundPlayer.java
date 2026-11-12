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
