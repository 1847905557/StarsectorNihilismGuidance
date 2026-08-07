package data.scripts.gearin;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;

/**
 * 角色：SoundPlayerAPI 声音播放入口(UI / 位置 / 循环三契约)。
 *
 * 全部方法是静态工具，可从任何已注册的脚本/插件调用；getSoundPlayer() 在
 * 任何阶段都非 null(Global 生命周期规则 1)，因此无需判空。
 *
 * 声音 ID GearIn_system_loop / GearIn_weapon_fire 由 C5-S1 声明、C5-S2 注册，
 * 本单元只消费(完整消费者族谱见 C5-S3)。
 */
public class GearInSoundPlayer {

    public static final String SOUND_ID_SYSTEM_LOOP = "GearIn_system_loop";
    public static final String SOUND_ID_WEAPON_FIRE = "GearIn_weapon_fire";

    private GearInSoundPlayer() {
        // 工具类：不实例化
    }

    /** UI 音效：素材应为双声道(API 注释 "UI sound files should be stereo")。 */
    public static void playUISound(float pitch, float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_WEAPON_FIRE, pitch, volume);
    }

    /** 位置音效：素材必须为 mono；loc 与 vel 都必须非 null，否则在调用点抛 NPE。 */
    public static void playPositionalSound(CombatEntityAPI source) {
        Vector2f loc = source.getLocation();
        Vector2f vel = new Vector2f(source.getVelocity()); // 复制，避免被引擎后续修改
        Global.getSoundPlayer().playSound(SOUND_ID_WEAPON_FIRE, 1f, 1f, loc, vel);
    }

    /** 循环音效：必须每帧调用否则淡出；playingEntity 必填，同实体同循环只播一个。 */
    public static void playLoopSound(CombatEngineAPI engine, CombatEntityAPI source) {
        Global.getSoundPlayer().playLoop(
                SOUND_ID_SYSTEM_LOOP, source, 1f, 1f, source.getLocation(), source.getVelocity());
    }
}
