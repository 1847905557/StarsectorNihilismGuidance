package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;

public class GearInWeaponFrameEffect implements WeaponEffectPluginWithInit,
        EveryFrameWeaponEffectPlugin {
    private static final Logger LOG = Global.getLogger(GearInWeaponFrameEffect.class);
    private int lastReportedFrame = -1;
    private boolean idleFrameApplied = false;
    private boolean wasFiring = false;

    @Override
    public void init(WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation();
        if (animation == null) {
            LOG.warn("GearIn weapon animation unavailable: " + weapon.getId());
            return;
        }
        LOG.info("GearIn weapon animation: " + weapon.getId()
                + " frames=" + animation.getNumFrames()
                + " rate=" + animation.getFrameRate());
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        AnimationAPI animation = weapon.getAnimation();
        if (animation != null) {
            boolean active = weapon.isFiring()
                    || weapon.getChargeLevel() > 0f
                    || weapon.getCooldownRemaining() > 0f;
            if (active) {
                animation.play();
                idleFrameApplied = false;
            } else {
                animation.pause();
                if (!idleFrameApplied) {
                    animation.setFrame(0);
                    idleFrameApplied = true;
                    LOG.info("GearIn weapon animation idle: " + weapon.getId());
                }
            }
            if (animation.getFrame() != lastReportedFrame) {
                LOG.info("GearIn weapon animation frame: " + weapon.getId()
                        + " frame=" + animation.getFrame()
                        + "/" + animation.getNumFrames());
                lastReportedFrame = animation.getFrame();
            }
        }
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon animation firing: " + weapon.getId());
        }
        wasFiring = weapon.isFiring();
    }
}
