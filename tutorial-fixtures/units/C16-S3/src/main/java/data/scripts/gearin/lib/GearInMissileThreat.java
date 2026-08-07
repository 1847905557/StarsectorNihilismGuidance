package data.scripts.gearin.lib;

import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public final class GearInMissileThreat {
    public float score(ShipAPI ship, MissileAPI missile) {
        float distance = Math.max(100f, MathUtils.getDistance(ship, missile));
        float payload = missile.getDamageAmount() + missile.getEmpAmount() * 0.5f;
        return payload * 1000f / distance;
    }
}
