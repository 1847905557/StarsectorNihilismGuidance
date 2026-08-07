package data.scripts.gearin.combat;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;

/**
 * 引擎级伤害监听器(结算前修改 + 结算后报告)：由 GearInFlowObserverPlugin.init()
 * 接入 engine.getListenerManager()，每次伤害结算都会回调。本类不改数值 ——
 * 要修改只能走 damage.getModifier() 并返回修改 id(硬规范 6)，修改方法一律返回 null。
 */
public class GearInDamageObserverListener
        implements DamageDealtModifier, DamageTakenModifier, DamageListener {

    private static final Logger LOG = Global.getLogger(GearInDamageObserverListener.class);

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        LOG.info("GearIn dealt param=" + (param == null ? "null" : param.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " base=" + damage.getBaseDamage()
                + " total=" + damage.getDamage()
                + " shieldHit=" + shieldHit);
        return null;
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        LOG.info("GearIn taken param=" + (param == null ? "null" : param.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " base=" + damage.getBaseDamage()
                + " shieldHit=" + shieldHit);
        return null;
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        LOG.info("GearIn damage source=" + (source == null ? "null" : source.getClass().getSimpleName())
                + " target=" + target.getClass().getSimpleName()
                + " hull=" + result.getDamageToHull()
                + " armor=" + result.getTotalDamageToArmor()
                + " shields=" + result.getDamageToShields()
                + " emp=" + result.getEmpDamage());
    }
}
