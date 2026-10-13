package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.util.IntervalUtil;

/** 记录武器实例的初始化、开火变化和操作后状态。 */
public class GearInWeaponFlowEffect implements EveryFrameWeaponEffectPluginWithAdvanceAfter,
        WeaponEffectPluginWithInit {

    private static final Logger LOG = Global.getLogger(GearInWeaponFlowEffect.class);

    /** 开火状态 (每个武器效果实例各有一份)。 */
    private boolean wasFiring = false;
    private final IntervalUtil weaponReportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(WeaponAPI weapon) {
        LOG.info("GearIn weapon init " + weapon.getId()
                + " spec=" + weapon.getSpec().getWeaponId()
                + " beam=" + weapon.isBeam());
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (weapon.isFiring() && !wasFiring) {
            LOG.info("GearIn weapon " + weapon.getId() + " started firing"
                    + " (before-frame callback)");
        }
        wasFiring = weapon.isFiring();
    }

    @Override
    public void advanceAfter(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 本帧武器操作完成后 (EveryFrameWeaponEffectPluginWithAdvanceAfter)
        weaponReportInterval.advance(amount);
        if (weapon.isFiring() && weaponReportInterval.intervalElapsed()) {
            LOG.info("GearIn weapon " + weapon.getId() + " after-frame:"
                    + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                    + " cooldown=" + weapon.getCooldownRemaining()
                    + " charge=" + weapon.getChargeLevel()
                    + " firing=" + weapon.isFiring());
        }
    }
}
