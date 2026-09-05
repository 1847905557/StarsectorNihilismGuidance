package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战斗插件中的 MutableShipStatsAPI 与 DynamicStatsAPI。
 *
 * 本插件由战斗上下文注册，只在本场战斗内取得并使用舰船 stats：
 * - advance() 先检查暂停和玩家舰对象，再取得 MutableShipStatsAPI；
 * - 普通 MutableStat 用 getModifiedValue() 读取当前战斗数值；
 * - DynamicStatsAPI.getMod() 返回 StatBonus，必须用 computeEffective(base) 读取结果；
 * - 每帧重复写入复用稳定 ID，结束时用同一 ID 清理。
 */
public class GearInShipStatsProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInShipStatsProbe.class);

    private static final String MOD_SPEED = "GearIn_ship_stats_probe_speed";
    private static final String MOD_DYNAMIC = "GearIn_ship_stats_probe_dynamic";
    private static final float REMOVE_AT = 10f;

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn stats probe init: engine ready");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) {
            return;
        }
        MutableShipStatsAPI stats = ship.getMutableStats();

        float t = engine.getTotalElapsedTime(false);
        if (t < REMOVE_AT) {
            applyModifiers(stats);
        } else {
            unapplyModifiers(stats);
        }

        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logStats(ship, stats);
        }
    }

    /** 战斗运行期重复写入普通 MutableStat 和 Dynamic StatBonus。 */
    private void applyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyFlat(MOD_SPEED, 20f);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                .modifyFlat(MOD_DYNAMIC, 1f);
    }

    /** 使用相同 ID 清理普通 stat 与 Dynamic。 */
    private void unapplyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().unmodify(MOD_SPEED);
        stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).unmodify(MOD_DYNAMIC);
    }

    /** 周期报告普通 MutableStat 与 Dynamic 的读取结果。 */
    private void logStats(ShipAPI ship, MutableShipStatsAPI stats) {
        MutableStat maxSpeed = stats.getMaxSpeed();
        DynamicStatsAPI dynamic = stats.getDynamic();
        StatBonus hullmodLimit = dynamic.getMod(Stats.MAX_PERMANENT_HULLMODS_MOD);
        LOG.info("GearIn stats read: hull=" + ship.getHullSpec().getHullId()
                + " maxSpeed base=" + maxSpeed.getBaseValue()
                + " modified=" + maxSpeed.getModifiedValue()
                + " flat=" + maxSpeed.getFlatMod()
                + " unmodified=" + maxSpeed.isUnmodified()
                + " dynamicMaxHullmods=" + hullmodLimit.computeEffective(2f)
                + " dynamicEntries=" + dynamic.getMods().size());
    }
}
