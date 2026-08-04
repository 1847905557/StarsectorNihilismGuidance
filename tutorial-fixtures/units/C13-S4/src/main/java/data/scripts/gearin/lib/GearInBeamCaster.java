package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

/** 伪光束（MagicFakeBeam）：每 BEAM_INTERVAL 秒向最近可见敌舰发射一条伪光束。
 * 伤害瞬时结算，持续视觉时间不等于持续伤害时间；光束不会被中途物体截断；
 * HIGH_EXPLOSIVE 对装甲全额伤害（原版有折减），本类用 ENERGY 规避。 */
public class GearInBeamCaster {
    private static final Logger LOG = Global.getLogger(GearInBeamCaster.class);
    private static final float BEAM_INTERVAL = 2f; // 光束发射间隔（秒）；伤害瞬时，视觉时长由 modSettings.json 控制

    private final GearInMagicSettings settings;
    private CombatEngineAPI engine;
    private float beamCharge; // 0..1：充能进度，HUD 状态条显示它
    private int beamCount;

    public GearInBeamCaster(GearInMagicSettings settings) {
        this.settings = settings;
    }

    /** 战斗 init 时注入引擎（由接线类调用）。 */
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    /** 每帧推进充能，满 1 发射并清零；只在非暂停推进（暂停守卫由接线类负责）。 */
    public void advance(float amount) {
        beamCharge += amount / BEAM_INTERVAL;
        if (beamCharge >= 1f) {
            beamCharge = 0f;
            fireBeam();
        }
    }

    public float getBeamCharge() { return beamCharge; }
    public int getBeamCount() { return beamCount; }

    /** 向最近可见敌舰发射一条伪光束：视觉立即生成，伤害在调用内瞬时结算。 */
    private void fireBeam() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        ShipAPI enemy = AIUtils.getNearestEnemy(player); // @Nullable：必须判空（C13-S2 同规）
        if (enemy == null) {
            LOG.info("GearIn magic beam: no enemy in sight");
            return;
        }
        Vector2f from = player.getLocation();
        float angle = VectorUtils.getAngle(from, enemy.getLocation());
        MagicFakeBeam.spawnFakeBeam(
                engine,
                from,
                settings.beamRange,
                angle,
                settings.beamWidth,
                settings.beamFull,
                settings.beamFading,
                settings.beamImpactSize,
                settings.beamCoreColor,
                settings.beamFringeColor,
                settings.beamDamage,
                DamageType.ENERGY,
                settings.beamEmp,
                player);
        beamCount++;
        LOG.info("GearIn magic beam: #" + beamCount + " damage=" + settings.beamDamage
                + " emp=" + settings.beamEmp + " width=" + settings.beamWidth
                + " range=" + settings.beamRange + " tags=" + settings.beamTags);
    }
}
