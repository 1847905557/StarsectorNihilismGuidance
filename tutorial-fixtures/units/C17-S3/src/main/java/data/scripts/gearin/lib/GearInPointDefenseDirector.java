package data.scripts.gearin.lib;

import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public final class GearInPointDefenseDirector {
    private final GearInMissileThreat threat = new GearInMissileThreat();

    public MissileAPI choose(WeaponAPI weapon) {
        MissileAPI best = null;
        float bestScore = 0f;
        for (MissileAPI missile : AIUtils.getNearbyEnemyMissiles(weapon.getShip(), 1400f)) {
            if (!WeaponUtils.isWithinArc(missile, weapon)) {
                continue;
            }
            float score = threat.score(weapon.getShip(), missile);
            if (score > bestScore) {
                best = missile;
                bestScore = score;
            }
        }
        if (best != null) {
            WeaponUtils.aimTowardsPoint(weapon, best.getLocation(), Global.getCombatEngine().getElapsedInLastFrame());
        }
        return best;
    }
}
