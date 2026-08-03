package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 战斗运行态周期报告器：由 GearInFlowObserverPlugin 按 3 秒节奏调用，
 * 打印玩家舰武器、射弹 / 导弹集合与光束实体的运行态。只做观察，
 * 不修改任何游戏数值。
 */
public class GearInStateReport {

    private static final Logger LOG = Global.getLogger(GearInStateReport.class);

    /** 武器运行态：弹药、冷却、充能、开火与光束实体数。 */
    public static void logWeapons(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip(); // 战斗内永不 null（C07-S02）；部署前是转移穿梭机假舰
        for (WeaponAPI weapon : player.getAllWeapons()) {
            LOG.info("GearIn weapon " + weapon.getId()
                    + " type=" + weapon.getType() + " size=" + weapon.getSize()
                    + " range=" + weapon.getRange()
                    + " charge=" + weapon.getChargeLevel()
                    + " cooldown=" + weapon.getCooldownRemaining() + "/" + weapon.getCooldown()
                    + " ammo=" + weapon.getAmmo() + "/" + weapon.getMaxAmmo()
                    + " firing=" + weapon.isFiring()
                    + " beam=" + weapon.isBeam()
                    + " beams=" + weapon.getBeams().size()
                    + " disabled=" + weapon.isDisabled());
        }
    }

    /** 射弹集合与导弹子集：getProjectiles() 已包含导弹（API javadoc "Includes missiles."），不要叠加统计。 */
    public static void logProjectiles(CombatEngineAPI engine) {
        List<DamagingProjectileAPI> projectiles = engine.getProjectiles();
        List<MissileAPI> missiles = engine.getMissiles();
        LOG.info("GearIn projectiles: total=" + projectiles.size()
                + " missiles=" + missiles.size()
                + " (missiles are included in total)");
        for (MissileAPI missile : missiles) {
            LOG.info("GearIn missile " + missile.getProjectileSpecId()
                    + " armed=" + missile.isArmed()
                    + " armingTime=" + missile.getArmingTime()
                    + " guided=" + missile.isGuided()
                    + " flare=" + missile.isFlare()
                    + " fizzling=" + missile.isFizzling()
                    + " fading=" + missile.isFading()
                    + " flight=" + missile.getFlightTime() + "/" + missile.getMaxFlightTime()
                    + " maxRange=" + missile.getMaxRange()
                    + " empRes=" + missile.getEmpResistance()
                    + " mine=" + missile.isMine());
        }
    }

    /** 光束实体：只在开火 / 充能期间存在；伤害是 DPS 语义，按 0.1 秒间隔结算。 */
    public static void logBeams(CombatEngineAPI engine) {
        for (BeamAPI beam : engine.getBeams()) {
            WeaponAPI weapon = beam.getWeapon();
            DamageAPI damage = beam.getDamage();
            LOG.info("GearIn beam weapon=" + (weapon == null ? "null" : weapon.getId())
                    + " from=(" + beam.getFrom().x + "," + beam.getFrom().y + ")"
                    + " length=" + beam.getLength()
                    + " dps=" + damage.isDps()
                    + " dmg=" + damage.getDamage()
                    + " brightness=" + beam.getBrightness()
                    + " didDamageThisFrame=" + beam.didDamageThisFrame()
                    + " target=" + (beam.getDamageTarget() == null ? "null" : beam.getDamageTarget().getClass().getSimpleName()));
        }
    }
}
