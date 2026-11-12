package data.scripts.gearin.combat;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;

public class GearInDamageListener implements DamageListener {

    private static final Logger LOG = Global.getLogger(GearInDamageListener.class);

    @Override
    public void reportDamageApplied(Object source,
            CombatEntityAPI target,
            ApplyDamageResultAPI result) {
        LOG.info("GearIn damage: target=" + target
                + " hull=" + result.getDamageToHull()
                + " armor=" + result.getTotalDamageToArmor()
                + " shields=" + result.getDamageToShields()
                + " emp=" + result.getEmpDamage());
    }
}
