package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * 完整最小例：市场条件生命周期演示脚本（token、survey、suppression、重算）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（沿用既有模式：每个注册项用各自的键，不挂进插件的
 * CLASS_MARK 门内）。演示脚本在挂载后的第一帧执行一次完整序列并自停
 * （isDone()=true，引擎下一帧自动移除本脚本）：
 *   1. addCondition(GearIn_ore_deposits)：spec id -> 实例 token，plugin 立即 apply，
 *      hazard/accessibility 上升；读取实例 token、plugin 类、planetary、survey 状态、
 *      spec 字段与 tooltip token 替换值；
 *   2. 多实例 + param：addCondition(id, 0.20f) 产生第二个实例，两个 token 相互独立，
 *      修饰按 token 隔离（同 spec id 不互相覆盖）；getFirstCondition 取首个实例；
 *   3. removeCondition(id) 移除同 id 全部实例（不对称性 A），修饰全部撤销；
 *   4. reapplyCondition(token) 单实例重算：unapply + apply，效果先降后升、终值不变；
 *   5. suppressCondition(id) 后 reapplyConditions()：unapply 执行、apply 被跳过，
 *      效果休眠但实例保留；unsuppressCondition(id) 后重算恢复；
 *   6. removeSpecificCondition(token) 只移除一个实例（不对称性 B）；
 *   7. survey 门控：setSurveyed(false) 后重算 -> 效果休眠；setSurveyed(true) 后重算
 *      -> 效果恢复（reapplyConditions 只对 surveyed 且未 suppressed 的条件 apply）；
 *   8. 原版对照（只读）：找一颗条件市场（isPlanetConditionMarketOnly）上
 *      requiresSurveying() 为 true 的条件，观察其 isSurveyed 初始状态。
 *
 * 演示全程不遗留持久状态：全部实例在当帧移除（removeSpecificCondition -> unapply），
 * suppressed 集合复位，hazard/accessibility 回到基线。
 */
public class GearInConditionLifecycleScript implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_ConditionLifecycleScript";

    private static final Logger LOG = Global.getLogger(GearInConditionLifecycleScript.class);

    /** 演示目标市场 id；默认 "jangala"（原版核心世界，必存在）。 */
    private String targetMarketId = "jangala";

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInConditionLifecycleScript() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
    }

    /** 修改演示目标市场；默认 "jangala"。 */
    public void setTargetMarketId(String targetMarketId) {
        this.targetMarketId = targetMarketId;
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runLifecycle();
    }

    private void runLifecycle() {
        SectorAPI sector = Global.getSector();
        MarketAPI market = sector.getEconomy().getMarket(targetMarketId);
        if (market == null) {
            LOG.info("GearIn condition lifecycle abort: market " + targetMarketId + " not found");
            return;
        }

        LOG.info(String.format(
                "GearIn condition lifecycle start: market=%s conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getId(), market.getConditions().size(),
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 1. addCondition：spec id -> 实例 token，plugin 立即 apply ---
        String token = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        MarketConditionAPI mc = market.getSpecificCondition(token);
        MarketConditionSpecAPI spec = mc.getSpec();
        MarketConditionPlugin plugin = mc.getPlugin();
        LOG.info(String.format(
                "GearIn condition lifecycle add: id=%s token=%s plugin=%s planetary=%s surveyed=%s reqSurvey=%s genSpec=%s specOrder=%.0f icon=%s",
                mc.getId(), mc.getIdForPluginModifications(),
                plugin.getClass().getSimpleName(), mc.isPlanetary(), mc.isSurveyed(),
                mc.requiresSurveying(), mc.getGenSpec() == null ? "null" : "present",
                spec.getOrder(), spec.getIcon()));
        LOG.info(String.format(
                "GearIn condition lifecycle add: desc=%s", spec.getDesc()));

        float hazardAfterAdd = market.getHazard().getModifiedValue();
        float accFlatAfterAdd = market.getAccessibilityMod().getFlatBonus();
        MutableStat.StatMod hazardMod = market.getHazard().getFlatStatMod(token);
        MutableStat.StatMod accMod = market.getAccessibilityMod().getFlatBonus(token);
        LOG.info(String.format(
                "GearIn condition lifecycle applied: hazard=%.2f hazardModSource=%s hazardModValue=%s accessibilityFlat=%.2f accModValue=%s",
                hazardAfterAdd,
                hazardMod == null ? "null" : hazardMod.getSource(),
                hazardMod == null ? "null" : String.format("%.2f", hazardMod.getValue()),
                accFlatAfterAdd,
                accMod == null ? "null" : String.format("%.2f", accMod.getValue())));

        Map<String, String> tokens = plugin.getTokenReplacements();
        LOG.info(String.format(
                "GearIn condition lifecycle tokens: $marketName=%s $marketFaction=%s",
                tokens.get("$marketName"), tokens.get("$marketFaction")));

        // --- 2. 多实例 + param：token 独立、修饰按 token 隔离 ---
        String token2 = market.addCondition(GearInOreCondition.ORE_DEPOSITS, 0.20f);
        MarketConditionAPI first = market.getFirstCondition(GearInOreCondition.ORE_DEPOSITS);
        LOG.info(String.format(
                "GearIn condition lifecycle multi: token1=%s token2=%s distinct=%s firstConditionIsToken1=%s hasCondition=%s hasSpecific=%s",
                token, token2, !token.equals(token2),
                first != null && token.equals(first.getIdForPluginModifications()),
                market.hasCondition(GearInOreCondition.ORE_DEPOSITS),
                market.hasSpecificCondition(token)));
        MutableStat.StatMod accMod1 = market.getAccessibilityMod().getFlatBonus(token);
        MutableStat.StatMod accMod2 = market.getAccessibilityMod().getFlatBonus(token2);
        LOG.info(String.format(
                "GearIn condition lifecycle multi: hazard=%.2f accessibilityFlat=%.2f accMod1=%s accMod2=%s",
                market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus(),
                accMod1 == null ? "null" : String.format("%.2f", accMod1.getValue()),
                accMod2 == null ? "null" : String.format("%.2f", accMod2.getValue())));

        // --- 3. removeCondition(id)：同 id 全部实例一起移除（不对称性 A） ---
        market.removeCondition(GearInOreCondition.ORE_DEPOSITS);
        LOG.info(String.format(
                "GearIn condition lifecycle removeAll: conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 4. reapplyCondition(token)：单实例重算 = unapply + apply ---
        String token3 = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        float hazardBeforeReapply = market.getHazard().getModifiedValue();
        market.reapplyCondition(token3);
        float hazardAfterReapply = market.getHazard().getModifiedValue();
        LOG.info(String.format(
                "GearIn condition lifecycle reapplyOne: hazardBefore=%.2f hazardAfter=%.2f unchanged=%s",
                hazardBeforeReapply, hazardAfterReapply, hazardBeforeReapply == hazardAfterReapply));

        // --- 5. suppression：suppress 本身不改效果，下次重算才生效 ---
        market.suppressCondition(GearInOreCondition.ORE_DEPOSITS);
        float hazardSuppressedBeforeReapply = market.getHazard().getModifiedValue();
        market.reapplyConditions();
        float hazardSuppressedAfterReapply = market.getHazard().getModifiedValue();
        LOG.info(String.format(
                "GearIn condition lifecycle suppress: suppressed=%s conditions=%d hazardBeforeReapply=%.2f hazardAfterReapply=%.2f (effect dormant)",
                market.isConditionSuppressed(GearInOreCondition.ORE_DEPOSITS),
                market.getConditions().size(),
                hazardSuppressedBeforeReapply, hazardSuppressedAfterReapply));
        market.unsuppressCondition(GearInOreCondition.ORE_DEPOSITS);
        market.reapplyConditions();
        LOG.info(String.format(
                "GearIn condition lifecycle unsuppress: suppressed=%s hazard=%.2f (effect restored)",
                market.isConditionSuppressed(GearInOreCondition.ORE_DEPOSITS),
                market.getHazard().getModifiedValue()));

        // --- 6. removeSpecificCondition(token)：只移除一个实例（不对称性 B） ---
        market.removeSpecificCondition(token3);
        LOG.info(String.format(
                "GearIn condition lifecycle removeOne: conditions=%d hazard=%.2f accessibilityFlat=%.2f",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));

        // --- 7. survey 门控：重算只对 surveyed 的条件 apply ---
        String token4 = market.addCondition(GearInOreCondition.ORE_DEPOSITS);
        MarketConditionAPI mc4 = market.getSpecificCondition(token4);
        float hazardAfterAdd4 = market.getHazard().getModifiedValue();
        mc4.setSurveyed(false);
        market.reapplyConditions();
        float hazardUnsurveyed = market.getHazard().getModifiedValue();
        mc4.setSurveyed(true);
        market.reapplyConditions();
        float hazardSurveyed = market.getHazard().getModifiedValue();
        market.removeSpecificCondition(token4);
        LOG.info(String.format(
                "GearIn condition lifecycle surveyGate: afterAdd=%.2f afterUnsurveyedReapply=%.2f afterSurveyedReapply=%.2f",
                hazardAfterAdd4, hazardUnsurveyed, hazardSurveyed));

        // --- 8. 原版对照（只读）：找一颗条件市场上 reqSurvey 条件的初始状态 ---
        boolean found = false;
        for (MarketAPI candidate : sector.getEconomy().getMarketsCopy()) {
            if (!candidate.isPlanetConditionMarketOnly()) {
                continue;
            }
            for (MarketConditionAPI c : candidate.getConditions()) {
                if (c.requiresSurveying()) {
                    LOG.info(String.format(
                            "GearIn condition lifecycle vanillaSurvey: market=%s surveyLevel=%s condition=%s surveyed=%s",
                            candidate.getId(), String.valueOf(candidate.getSurveyLevel()),
                            c.getId(), c.isSurveyed()));
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            LOG.info("GearIn condition lifecycle vanillaSurvey: none found (all condition-only markets surveyed)");
        }

        LOG.info(String.format(
                "GearIn condition lifecycle final: conditions=%d hazard=%.2f accessibilityFlat=%.2f (baseline restored)",
                market.getConditions().size(), market.getHazard().getModifiedValue(),
                market.getAccessibilityMod().getFlatBonus()));
        LOG.info("GearIn condition lifecycle done");
    }
}
