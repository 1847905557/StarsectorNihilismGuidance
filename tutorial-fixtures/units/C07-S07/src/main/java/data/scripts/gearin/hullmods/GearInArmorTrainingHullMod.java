package data.scripts.gearin.hullmods;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：HullMod 生命周期、tooltip、S-mod 与监听器接入。
 *
 * 主题：装甲训练。效果类扩展 BaseHullMod（HullModEffect 的空实现基类）；
 * 效果类是会话级单例（HullModEffect javadoc 原文），本类不持有任何跨船字段——
 * 每艘船自己的状态（周期计时、抵消计数）放在 applyEffectsAfterShipCreation
 * 里注册的监听器实例中（每艘船一个实例，随舰船实体一起销毁）。
 *
 * 生命周期覆盖：
 * - applyEffectsBeforeShipCreation：生涯与战斗都会调用的 stats 入口（C07-S02）；
 *   这里同时演示 S-mod 分支：isSMod(stats) 为 true 时把同一 id 的修饰覆盖为更大值；
 * - applyEffectsAfterShipCreation：战斗侧舰船实体创建后调用（含装配界面预览舰），
 *   注册监听器的正式位置；不承担 stats 修改（API javadoc 原文）；
 * - applyEffectsAfterShipAddedToCombatEngine：每艘船加入战斗引擎只回调一次
 *   （API javadoc 原文 "Only called once."）；
 * - advanceInCombat：战斗每帧调用，暂停时不调用（API javadoc 原文）；
 *   演示每帧幂等写 StatBonus（同 ID 覆盖，不累积，C07-S02 规则 6）；
 * - advanceInCampaign：生涯每帧调用；舰队数据重同步时以 amount=0 调用
 *   （API javadoc 原文），这里只在该时机输出一行作为生命周期证明。
 *
 * tooltip：desc 的 %s 由 getDescriptionParam(0..n) 填充，sModDesc 的 %s
 * 由 getSModDescriptionParam(0..n) 填充（BaseHullMod / 标准 tooltip）。
 */
public class GearInArmorTrainingHullMod extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(GearInArmorTrainingHullMod.class);

    /** 基础效果：有效护甲 +10%（getDescriptionParam(0) 输出同值）。 */
    public static final float ARMOR_BONUS_PERCENT = 10f;
    /** S-mod 额外加成：applyEffects 合计 25%、动态加成上限 15%（getSModDescriptionParam 0/1 输出）。 */
    public static final float SMOD_ADDITIONAL_PERCENT = 15f;
    /** 装甲平均比例高于该值时，监听器抵消即将发生的舰体结构伤害。 */
    public static final float NEGATION_ARMOR_FRACTION = 0.5f;
    /** 动态修饰 ID：与 hullmod id 分开，避免与 applyEffects 的条目互相覆盖。 */
    private static final String DYNAMIC_ID = "GearIn_armor_training_dynamic";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // S-mod 分支：同一 id 同类型只保留一条条目，S-mod 时直接覆盖为合计值（C07-S02 覆盖语义）。
        float bonus = ARMOR_BONUS_PERCENT + (isSMod(stats) ? SMOD_ADDITIONAL_PERCENT : 0f);
        stats.getArmorBonus().modifyPercent(id, bonus);           // 面板/生涯可见的护甲评级
        stats.getEffectiveArmorBonus().modifyPercent(id, bonus);  // 命中结算时的有效护甲
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 每艘船实体一个监听器实例：跨船状态只能放在这里，不能放效果类字段。
        // 装配界面预览舰也会走到这里（舰船显示面板用 createShip(variant, true, ...) 重建显示实体）。
        ship.addListener(new ArmorTrainingListener(ship));
        LOG.info("GearIn armor training: listener attached to " + ship.getName());
    }

    @Override
    public void applyEffectsAfterShipAddedToCombatEngine(ShipAPI ship, String id) {
        // 一次性回调：舰船移除后再加入引擎也不会再次调用（API javadoc 原文）。
        LOG.info("GearIn armor training combat-ready: " + ship.getName() + " (once per engine add)");
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 每帧调用、暂停时不调用。效果类不保存跨船字段，这里只做无状态写入：
        // S-mod 时每帧把"当前装甲平均比例"写成动态修饰。命中结算时装甲网格
        // 以 getEffectiveArmorBonus().computeEffective(...) 读取，因此下一击即生效；同 ID 每帧重复调用是幂等覆盖，不会累积。
        if (!isSMod(ship) || !ship.isAlive() || ship.isHulk()) return;
        float frac = armorFraction(ship.getArmorGrid());
        ship.getMutableStats().getEffectiveArmorBonus()
                .modifyPercent(DYNAMIC_ID, SMOD_ADDITIONAL_PERCENT * frac);
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        // 生涯每帧调用；舰队数据重同步（如舰队界面移动成员）时以 amount=0 调用（API javadoc 原文）。
        // 只在同步时机输出一行，作为"该回调确实被调用"的证明，避免每帧刷屏。
        if (amount <= 0f) {
            LOG.info("GearIn armor training campaign sync: " + member.getShipName()
                    + " variant=" + member.getVariant().getHullVariantId());
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) ARMOR_BONUS_PERCENT + "%";
        if (index == 1) return "" + Math.round(NEGATION_ARMOR_FRACTION * 100f) + "%";
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) (ARMOR_BONUS_PERCENT + SMOD_ADDITIONAL_PERCENT) + "%";
        if (index == 1) return "" + (int) SMOD_ADDITIONAL_PERCENT + "%";
        return null;
    }

    /** 装甲网格平均比例（0..1）：网格总值 /（格子数 × 单格最大值）。 */
    private static float armorFraction(ArmorGridAPI grid) {
        float[][] cells = grid.getGrid();
        float maxCell = grid.getMaxArmorInCell();
        if (cells.length == 0 || maxCell <= 0f) return 0f;
        float total = 0f;
        int count = 0;
        for (float[] row : cells) {
            for (float value : row) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0f : total / (count * maxCell);
    }

    /**
     * 每艘船一个实例（applyEffectsAfterShipCreation 里注册），持有本船的状态。
     * 同时实现两个监听器接口：
     * - HullDamageAboutToBeTakenListener：舰体结构伤害结算前回调，返回 true 即抵消
     *   （API javadoc 原文："if true is returned, the hull damage to be taken is negated."）；
     * - AdvanceableListener：舰船级每帧推进（Ship.advance 仅对 amount>0 派发，即暂停时不调用），
     *   这里用它做 5 秒一次的状态输出——周期计时器是单船状态，合法放在本实例。
     */
    public static class ArmorTrainingListener implements HullDamageAboutToBeTakenListener, AdvanceableListener {

        private final ShipAPI ship;
        private final IntervalUtil reportInterval = new IntervalUtil(5f, 5f);
        private long negations;

        public ArmorTrainingListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float frac = armorFraction(ship.getArmorGrid());
            if (frac <= NEGATION_ARMOR_FRACTION) return false;
            negations++;
            LOG.info("GearIn armor training: hull damage negated amount=" + damageAmount
                    + " armorFraction=" + String.format("%.2f", frac)
                    + " negations=" + negations);
            return true; // 抵消本次舰体结构伤害
        }

        @Override
        public void advance(float amount) {
            reportInterval.advance(amount);
            if (!reportInterval.intervalElapsed()) return;
            float frac = armorFraction(ship.getArmorGrid());
            LOG.info("GearIn armor training tick: " + ship.getName()
                    + " rating=" + ship.getArmorGrid().getArmorRating()
                    + " armorFraction=" + String.format("%.2f", frac)
                    + " negations=" + negations);
        }
    }
}
