package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 武器逐帧效果（.wpn everyFrameEffect 注册列，每把武器一个实例）。
 * 构造时 init(weapon)；之后每帧在本帧"转向 / 开火之前"回调 advance、操作完成后
 * 回调 advanceAfter。演示 AnimationAPI 程序化控制贴图动画（play / pause / setFrame）。
 */
public class GearInWeaponFrameEffect implements WeaponEffectPluginWithInit,
        EveryFrameWeaponEffectPluginWithAdvanceAfter {
    private static final Logger LOG = Global.getLogger(GearInWeaponFrameEffect.class);
    private int lastReportedFrame = -1; // 帧变化日志去重
    private boolean wasFiring = false; // 开火边沿记录
    private boolean idleFrameApplied = false; // 闲置帧只强制一次
    private final IntervalUtil afterReportInterval = new IntervalUtil(1f, 1f); // 操作后日志节流

    @Override
    public void init(WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation(); // 非动画武器返回 null（硬规范 4）
        LOG.info("GearIn weapon fx init " + weapon.getId()
                + " beam=" + weapon.isBeam()
                + " animated=" + (animation != null)
                + (animation != null ? " frames=" + animation.getNumFrames() + " rate=" + animation.getFrameRate() : ""));
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧转向 / 开火等操作之前；闲置且冷却完成时冻结回 0 帧（BlinkerEffect
        // 同款姿势），否则恢复播放（play() 覆盖 alwaysAnimate:false 的数据默认）。
        AnimationAPI animation = weapon.getAnimation();
        if (animation != null) {
            boolean idle = weapon.getChargeLevel() <= 0f
                    && weapon.getCooldownRemaining() <= 0f
                    && !weapon.isFiring();
            if (idle) {
                animation.pause();
                if (!idleFrameApplied) {
                    animation.setFrame(0);
                    idleFrameApplied = true;
                    LOG.info("GearIn weapon fx " + weapon.getId() + " idle -> frame 0 (programmatic)");
                }
            } else {
                animation.play();
                idleFrameApplied = false;
            }
            if (animation.getFrame() != lastReportedFrame) {
                LOG.info("GearIn weapon fx " + weapon.getId()
                        + " frame=" + animation.getFrame() + "/" + animation.getNumFrames()
                        + " firing=" + weapon.isFiring()
                        + " charge=" + weapon.getChargeLevel()
                        + " cooldown=" + weapon.getCooldownRemaining());
                lastReportedFrame = animation.getFrame();
            }
        }
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon fx " + weapon.getId() + " started firing (before-frame callback)");
        }
        wasFiring = weapon.isFiring();
    }
    @Override
    public void advanceAfter(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧操作完成后（WithAdvanceAfter）：弹药扣减已生效，可观察操作后状态。
        if (weapon.isFiring()) {
            afterReportInterval.advance(amount);
            if (afterReportInterval.intervalElapsed()) {
                LOG.info("GearIn weapon fx " + weapon.getId() + " after-frame:"
                        + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                        + " cooldown=" + weapon.getCooldownRemaining());
            }
        }
    }
}
