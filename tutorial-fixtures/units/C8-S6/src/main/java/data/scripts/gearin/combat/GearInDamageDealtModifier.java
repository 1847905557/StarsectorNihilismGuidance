package data.scripts.gearin.combat;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;

public class GearInDamageDealtModifier implements DamageDealtModifier {

    @Override
    public String modifyDamageDealt(Object param,
                                    CombatEntityAPI target,
                                    DamageAPI damage,
                                    Vector2f point,
                                    boolean shieldHit) {
        damage.getModifier().modifyPercent("GearIn_dealt", 10f);
        return "GearIn_dealt";
    }
}
