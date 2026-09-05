package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：战术系统运行态 —— Stats 脚本、状态机、目标锁定与系统 AI。
 * 一个类身兼两个角色，由数据绑定各自实例化一次：Stats 脚本在舰船部署时创建，
 * apply 只在 effectLevel > 0 期间每帧调用，unapply 在 effectLevel 归零边界调用一次；
 * AI 角色随舰船 AI 创建，每帧决策并把验证过的目标写入 flag (有持续时间，消费前再验证)。
 * 两个实例字段互不共享，共享目标逻辑全部走静态方法；玩家舰没有舰船 AI，
 * 目标回退到 ship.getShipTarget()。
 */
public class GearInTargetingSystem extends BaseShipSystemScript implements ShipSystemAIScript {

    private static final Logger LOG = Global.getLogger(GearInTargetingSystem.class);

    /** 系统标称射程 (经 systemRangeBonus 修正前的基础值)。 */
    public static final float RANGE = 800f;
    /** 锁定有效时满 effectLevel 下的最大速度倍率 (1.5 倍)。 */
    public static final float SPEED_MULT = 1.5f;
    /** AI 写入 TARGET_FOR_SHIP_SYSTEM 的 flag 持续时间 (秒)。 */
    public static final float AI_TARGET_FLAG_DURATION = 1.0f;
    /** AI 决策节流区间 (0.2–0.3 秒一次决策)。 */
    private static final float AI_DECISION_MIN = 0.2f;
    private static final float AI_DECISION_MAX = 0.3f;

    // --- AI 角色字段 (ShipSystemAIScript 实例独有) ---
    private CombatEngineAPI engine;
    private ShipAPI aiShip;
    private ShipSystemAPI system;
    private ShipwideAIFlags aiFlags;
    private final IntervalUtil decisionInterval = new IntervalUtil(AI_DECISION_MIN, AI_DECISION_MAX);
    private SystemState lastAISystemState = null;

    // --- Stats 角色字段 (ShipSystemStatsScript 实例独有) ---
    private State lastApplyState = null;

    // --- 共享目标逻辑 (静态，两个角色共用同一语义) ---

    /** 有效射程：基础 RANGE 经 systemRangeBonus 修正。 */
    public static float getRange(ShipAPI source) {
        if (source == null) {
            return RANGE;
        }
        return source.getMutableStats().getSystemRangeBonus().computeEffective(RANGE);
    }

    /** 目标合法性：敌方、存活、非残骸/相位/转移穿梭机、且在有效射程内。 */
    public static boolean isValidTarget(ShipAPI source, ShipAPI target) {
        if (source == null || target == null) {
            return false;
        }
        if (target == source) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        if (target.isHulk()) {
            return false;
        }
        if (target.isPhased()) {
            return false;
        }
        if (target.isShuttlePod()) {
            return false;
        }
        if (target.getOwner() == source.getOwner()) {
            return false;
        }
        Vector2f delta = Vector2f.sub(target.getLocation(), source.getLocation(), new Vector2f());
        return delta.length() <= getRange(source);
    }

    /**
     * 读取 AI 写入的目标：TARGET_FOR_SHIP_SYSTEM 的 custom 数据。
     * 玩家舰没有舰船 AI，本类 AI 角色也不会运行，flag 无人写入 —— 返回 null。
     */
    public static ShipAPI readAITarget(ShipAPI source) {
        if (source == null || source.getShipAI() == null) {
            return null;
        }
        ShipwideAIFlags flags = source.getAIFlags();
        if (flags == null || !flags.hasFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM)) {
            return null;
        }
        Object data = flags.getCustom(AIFlags.TARGET_FOR_SHIP_SYSTEM);
        return data instanceof ShipAPI ? (ShipAPI) data : null;
    }

    /** 把验证通过的目标写入 TARGET_FOR_SHIP_SYSTEM(AI 角色调用 ship.useSystem() 前使用)。 */
    public static boolean writeAITarget(ShipAPI source, ShipAPI target) {
        if (source == null || source.getShipAI() == null) {
            return false;
        }
        ShipwideAIFlags flags = source.getAIFlags();
        if (flags == null || !isValidTarget(source, target)) {
            return false;
        }
        flags.setFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM, AI_TARGET_FLAG_DURATION, target);
        return true;
    }

    /** 目标解析：AI flag 优先，回退舰船当前目标 (getShipTarget())，全部经过再验证。 */
    public static ShipAPI pickTarget(ShipAPI source) {
        if (source == null) {
            return null;
        }
        ShipAPI target = readAITarget(source);
        if (isValidTarget(source, target)) {
            return target;
        }
        target = source.getShipTarget();
        if (isValidTarget(source, target)) {
            return target;
        }
        return null;
    }

    // --- Stats 脚本角色 (ShipSystemStatsScriptAdvanced) ---

    /**
     * IN/ACTIVE 期间每帧调用：锁定有效时按 effectLevel 施加最大速度倍率，
     * 锁定失效或进入 OUT 立即撤销 —— 目标是 "启动侧再验证，验证不过不加成"。
     */
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }
        if (lastApplyState != state) {
            LOG.info("GearIn targeting stats: state " + lastApplyState + " -> " + state
                    + " effectLevel=" + effectLevel);
            lastApplyState = state;
        }
        boolean locked = isValidTarget(ship, pickTarget(ship));
        if (state == ShipSystemStatsScript.State.OUT || !locked) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyMult(id, 1f + (SPEED_MULT - 1f) * effectLevel);
        }
    }

    /** effectLevel 归零边界 (进入 COOLDOWN/IDLE、舰船撤退或解体) 由引擎调用一次。 */
    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        LOG.info("GearIn targeting stats: unapply effect (id=" + id + ")");
    }

    /** 仅玩家舰：IN/ACTIVE 期间显示目标锁定状态 (无目标时按减益样式显示)。 */
    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index != 0) {
            return null;
        }
        if (state != State.IN && state != State.ACTIVE) {
            return null;
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        ShipAPI player = engine == null ? null : engine.getPlayerShip();
        boolean locked = player != null && isValidTarget(player, pickTarget(player));
        return new StatusData(locked ? "target locked" : "no target", !locked);
    }

    /** 可用性许可：激活中始终可用；待机时必须有有效锁定目标，否则按下 U 不激活。 */
    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (system.isActive()) {
            return true;
        }
        return isValidTarget(ship, pickTarget(ship));
    }

    /** 信息栏文字：返回 null 时 UI 使用默认 "READY" / "ACTIVE"。 */
    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        }
        if (system.getState() != SystemState.IDLE) {
            return null;
        }
        return isValidTarget(ship, pickTarget(ship)) ? "READY" : "NO TARGET";
    }

    // --- AI 脚本角色 (ShipSystemAIScript) ---

    /** AI 实例创建时调用一次：缓存引用并随机化决策节流相位。 */
    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.aiShip = ship;
        this.system = system;
        this.aiFlags = flags;
        this.engine = engine;
        this.decisionInterval.randomize();
        LOG.info("GearIn targeting AI init: ship=" + ship.getHullSpec().getHullId()
                + " system=" + system.getId());
    }

    /**
     * 每帧被舰船 AI 调用：守卫链 (暂停/实体/存活/CR/冷却/弹药)→ 决策节流 →
     * 目标补全与再验证 → 写 flag(激活期间每 0.2–0.3 秒刷新，flag 只有 1 秒寿命)→
     * 过载/高幅能守卫 → ship.useSystem()。
     */
    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) {
            return; // 暂停守卫：暂停时 AI 决策不推进
        }
        logStateTransition();

        if (!engine.isEntityInPlay(aiShip) || !aiShip.isAlive()) {
            return;
        }
        if (aiShip.getCurrentCR() <= 0f) {
            return;
        }
        if (system.getCooldownRemaining() > 0f) {
            return; // 冷却中 (COOLDOWN 状态)
        }
        if (system.isOutOfAmmo()) {
            return;
        }

        decisionInterval.advance(amount);
        if (!decisionInterval.intervalElapsed()) {
            return; // 决策节流
        }

        // 目标补全与再验证：参数 target 优先 (舰船 AI 传入，不可见时为 null)，回退舰船当前目标
        ShipAPI lock = target;
        if (lock == null) {
            lock = aiShip.getShipTarget();
        }
        if (!isValidTarget(aiShip, lock)) {
            clearAITarget();
            return;
        }
        writeAITarget(aiShip, lock);

        if (system.isActive()) {
            return; // 激活中：只刷新锁定 (flag 1 秒过期，不刷新 ACTIVE 后半段会丢锁)，不重复激活
        }
        if (aiShip.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }
        if (aiShip.getFluxLevel() > 0.9f) {
            return; // 高幅能不启动
        }
        LOG.info("GearIn targeting AI: lock " + lock.getHullSpec().getHullId()
                + " range=" + getRange(aiShip));
        aiShip.useSystem(); // ShipAPI.useSystem() -> giveCommand(USE_SYSTEM)(玩家按 F 与 AI 同一入口)
    }

    /** 清除过期的目标 flag(目标失效后下一轮决策不再复用旧锁)。 */
    private void clearAITarget() {
        if (aiFlags != null) {
            aiFlags.removeFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM);
        }
    }

    /** AI 视角的运行时状态机跳变日志：完整五态 (含 COOLDOWN/IDLE，Stats 角色看不到这两态)。 */
    private void logStateTransition() {
        if (system == null) {
            return;
        }
        SystemState current = system.getState();
        if (current != lastAISystemState) {
            LOG.info("GearIn targeting AI: state " + lastAISystemState + " -> " + current
                    + " effectLevel=" + system.getEffectLevel()
                    + " cooldownRemaining=" + system.getCooldownRemaining());
            lastAISystemState = current;
        }
    }
}
