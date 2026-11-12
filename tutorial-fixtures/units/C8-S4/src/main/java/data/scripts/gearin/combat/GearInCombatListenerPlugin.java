package data.scripts.gearin.combat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 在本场战斗登记伤害监听与修饰，并在收尾时解除。 */
public class GearInCombatListenerPlugin extends BaseEveryFrameCombatPlugin {

    private final GearInDamageListener damageListener = new GearInDamageListener();
    private final GearInDamageDealtModifier dealtModifier = new GearInDamageDealtModifier();
    private CombatEngineAPI engine;
    private boolean registered;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            engine = Global.getCombatEngine();
        }
        CombatListenerManagerAPI manager = engine.getListenerManager();
        if (!registered) {
            if (!manager.hasListenerOfClass(GearInDamageListener.class)) {
                manager.addListener(damageListener);
            }
            if (!manager.hasListenerOfClass(GearInDamageDealtModifier.class)) {
                manager.addListener(dealtModifier);
            }
            registered = true;
        }
        if (engine.isCombatOver()) {
            manager.removeListener(damageListener);
            manager.removeListener(dealtModifier);
            engine.removePlugin(this);
        }
    }
}
