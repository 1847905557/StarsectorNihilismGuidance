package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：ShipAPI / MutableShipStatsAPI / MutableStat 与 modifier ID。
 *
 * 本插件由战斗上下文注册 (C8-S1 的插件模型)，只在本场战斗内有效：
 * - 引擎引用在 init() 中保存，只在本场战斗使用；禁止跨战斗缓存 (C7-S2)；
 * - advance() 在暂停时仍会被调用，状态推进前必须先检查 engine.isPaused()(C8-S1)；
 * - getPlayerShip() 在玩家未部署时返回 "Command Shuttle" 假舰 (C8-S2)，
 *   写操作前用 isShuttlePod() 排除，避免把修饰加到假舰上；
 * - MutableShipStatsAPI 是舰船 stats 的入口：三个 MutableStat 修饰表
 *   (flat / percent / mult) 各以 modifier ID 为键；同 ID 同类型调用是幂等覆盖，
 *   每帧重复应用是安全姿势 (引擎自己的 zero_flux_boost 同款)；
 * - modifier ID 必须稳定唯一：本节所有修饰 ID 是进程内常量，属本节专属命名空间。
 */
public class GearInShipStatsProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInShipStatsProbe.class);

    /** 稳定 modifier ID(本节专属命名空间，进程内不再变化；命名契约见 C1-S1)。 */
    private static final String MOD_SPEED_PERCENT = "GearIn_ship_stats_probe_speed_percent";
    private static final String MOD_SPEED_FLAT = "GearIn_ship_stats_probe_speed_flat";
    private static final String MOD_SPEED_MULT = "GearIn_ship_stats_probe_speed_mult";
    private static final String MOD_VOLATILE_PREFIX = "GearIn_ship_stats_probe_volatile_";

    /**
     * 故障注入开关 (对应 "数值与修饰条目持续增长" 故障)：
     * true 时每帧用自增 ID 追加一条 percent 修饰，观察 percentMods 只增不减、
     * modified 值持续漂移、unmodify 之后残留条目清不掉。
     */
    private static final boolean PROBE_VOLATILE_ID_FAULT = false;

    /** 战斗推进时间 (getTotalElapsedTime(false)，暂停不计) 的三个阶段分界。 */
    private static final float APPLY_AT = 2f;      // t=2s 起施加三种修饰 (每帧重复，幂等)
    private static final float OVERWRITE_AT = 12f; // t=12s 起把 percent 覆盖为 -20(同 ID 不同值)
    private static final float UNMODIFY_AT = 22f;  // t=22s 起全部解除

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);
    private long volatileCounter = 0L;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn stats probe init: engine ready");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫：暂停时 advance 仍会被调用，状态不得推进 (C8-S1)
        }
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) {
            return; // 部署阶段拿到的 "Command Shuttle" 假舰不做写操作 (C8-S2)
        }
        MutableShipStatsAPI stats = ship.getMutableStats();

        float t = engine.getTotalElapsedTime(false);
        if (t >= UNMODIFY_AT) {
            unapplyModifiers(stats);
        } else if (t >= OVERWRITE_AT) {
            overwriteModifiers(stats);
        } else if (t >= APPLY_AT) {
            applyModifiers(stats);
        }

        if (PROBE_VOLATILE_ID_FAULT) {
            stats.getMaxSpeed().modifyPercent(MOD_VOLATILE_PREFIX + volatileCounter++, 0.01f);
        }

        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logStats(ship, stats);
        }
    }

    /** 施加三种修饰：同一稳定 ID 每帧重复调用，同类型同值 → 仅刷新 desc，条目不新增。 */
    private void applyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyPercent(MOD_SPEED_PERCENT, 25f);
        stats.getMaxSpeed().modifyFlat(MOD_SPEED_FLAT, 10f);
        stats.getMaxSpeed().modifyMult(MOD_SPEED_MULT, 1.5f);
    }

    /** 覆盖阶段：同一 ID 不同值 → 替换旧条目，条数不变。 */
    private void overwriteModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyPercent(MOD_SPEED_PERCENT, -20f);
    }

    /** 解除阶段：unmodify(source) 同时清除 flat / percent / mult 三类条目。 */
    private void unapplyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().unmodify(MOD_SPEED_PERCENT);
        stats.getMaxSpeed().unmodify(MOD_SPEED_FLAT);
        stats.getMaxSpeed().unmodify(MOD_SPEED_MULT);
    }

    /** 周期报告：maxSpeed 的 base / 最终值 / 三类修饰合计与条目数，以及 flux 两个 stat 的 base / 最终值。 */
    private void logStats(ShipAPI ship, MutableShipStatsAPI stats) {
        MutableStat maxSpeed = stats.getMaxSpeed();
        LOG.info("GearIn stats read: hull=" + ship.getHullSpec().getHullId()
                + " maxSpeed base=" + maxSpeed.getBaseValue()
                + " modified=" + maxSpeed.getModifiedValue()
                + " flat=" + maxSpeed.getFlatMod()
                + " percent=" + maxSpeed.getPercentMod()
                + " mult=" + maxSpeed.getMult()
                + " entries flat=" + maxSpeed.getFlatMods().size()
                + " percent=" + maxSpeed.getPercentMods().size()
                + " mult=" + maxSpeed.getMultMods().size()
                + " unmodified=" + maxSpeed.isUnmodified());
        LOG.info("GearIn stats flux: capacity base=" + stats.getFluxCapacity().getBaseValue()
                + " modified=" + stats.getFluxCapacity().getModifiedValue()
                + " dissipation base=" + stats.getFluxDissipation().getBaseValue()
                + " modified=" + stats.getFluxDissipation().getModifiedValue());
    }
}
