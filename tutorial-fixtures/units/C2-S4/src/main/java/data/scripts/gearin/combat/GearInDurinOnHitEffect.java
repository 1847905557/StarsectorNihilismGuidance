package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

/** 都林命中船体时，按概率追加一次高爆爆炸。 */
public class GearInDurinOnHitEffect implements OnHitEffectPlugin {

    /** 每发命中触发的概率。 */
    private static final float CHANCE = 0.25f;

    /** 追加的高爆伤害。 */
    private static final float BONUS_DAMAGE = 20f;

    private static final Color EXPLOSION_COLOR = new Color(255, 120, 60, 255);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        // 命中护盾、目标不是舰船或未达到触发概率时，直接结束。
        if (shieldHit || !(target instanceof ShipAPI) || Math.random() > CHANCE) {
            return;
        }
        // 命中点的爆炸只是画面，伤害由下一行单独结算。
        engine.spawnExplosion(point, new Vector2f(), EXPLOSION_COLOR, 40f, 0.3f);
        engine.applyDamage(target, point, BONUS_DAMAGE, DamageType.HIGH_EXPLOSIVE, 0f, false, false,
                projectile.getSource());
    }
}
