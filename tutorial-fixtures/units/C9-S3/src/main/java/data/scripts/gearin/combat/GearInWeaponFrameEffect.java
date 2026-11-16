package data.scripts.gearin.combat;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/** 旋转测试炮的转子：连续开火时转速逐渐升高，停火后逐渐降低，完全停下后回到第 0 帧。 */
public class GearInWeaponFrameEffect implements EveryFrameWeaponEffectPlugin {

    /** 转速最高时的帧率。 */
    private static final float MAX_FRAME_RATE = 24f;

    /** 从静止加速到最高转速所需的开火时间，单位秒。 */
    private static final float SPIN_UP_TIME = 1.5f;

    /** 从最高转速减速到静止所需的时间，单位秒。 */
    private static final float SPIN_DOWN_TIME = 2f;

    /** 当前转速比例，0 为静止，1 为最高转速。每把武器各有一个实例。 */
    private float spin = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }
        AnimationAPI animation = weapon.getAnimation();
        if (animation == null) {
            return;
        }
        if (weapon.isFiring()) {
            spin = Math.min(1f, spin + amount / SPIN_UP_TIME);
        } else {
            spin = Math.max(0f, spin - amount / SPIN_DOWN_TIME);
        }
        if (spin <= 0f) {
            animation.pause();
            animation.setFrame(0);
        } else {
            animation.setFrameRate(MAX_FRAME_RATE * spin);
            animation.play();
        }
    }
}
