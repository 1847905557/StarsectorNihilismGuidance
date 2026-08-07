package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/**
 * 完整最小例：产业(Industry)注册、供需、升级、AI core 与 disruption 演示。
 *
 * 注册入口：industries.csv 一行(plugin 列 = 本类全名，id = INDUSTRY_ID)。市场侧由
 * MarketAPI.addIndustry / instantiateIndustry 消费；演示安装点片段见页面正文
 * (GearInModPlugin 的 onGameLoad，persistent data 键 REGISTER_MARK 一次性门控)。
 *
 * 行为：
 *   1. apply() 声明供需：需求 SUPPLIES(训练耗材)与 CREW(教官)，供给 CREW(结业船员)；
 *      supplies 缺货时按缺口削减船员产出(applyDeficitToProduction)。
 *   2. 收入/维护由 spec(income=0, upkeep=2)经 applyIncomeAndUpkeep 写入。
 *   3. AI core：alpha 额外给市场 +1 稳定度(applyAlphaCoreModifiers / applyNoAICoreModifiers
 *      成对)；供需/维护侧走 BaseIndustry 默认(alpha: 产量+1、需求-1、维护×0.75)。
 *   4. disruption：notifyDisrupted / disruptionFinished 打日志；setDisrupted 由外部触发
 *      (突袭、AI 审查、Luddic Path)，中断期间 isFunctional()=false、建设暂停。
 *
 * 演示全程可清理：removeIndustry(GearInTrainingIndustry.INDUSTRY_ID, null, false) 即返还
 * AI core 并撤销全部修饰。
 */
public class GearInTrainingIndustry extends BaseIndustry {

    /** 产业 id：与 industries.csv 的 id 列逐字一致。 */
    public static final String INDUSTRY_ID = "GearIn_training_industry";

    /** 存档持久化标记：演示安装是否已在该存档执行过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_TrainingIndustry";

    /** industries.csv upgrade 列目标(原版产业 id，本节只消费不定义)。 */
    public static final String UPGRADE_TARGET_ID = "militarybase";

    private static final Logger LOG = Global.getLogger(GearInTrainingIndustry.class);

    @Override
    public void apply() {
        // 顺序：updateSupplyAndDemandModifiers -> updateIncomeAndUpkeep ->
        // applyAICoreModifiers -> applyImproveModifiers(BaseIndustry.apply(boolean))
        super.apply(true);

        int size = market.getSize();

        demand(Commodities.SUPPLIES, size, "Training operations");
        demand(Commodities.CREW, Math.max(1, size / 2), "Instructors");
        supply(Commodities.CREW, Math.max(1, size - 2), "Trained crew");

        // supplies 缺口会削减船员产出(只对已声明供给的商品生效)
        Pair<String, Integer> deficit = getMaxDeficit(Commodities.SUPPLIES);
        applyDeficitToProduction(1, deficit, Commodities.CREW);

        if (!isFunctional()) {
            supply.clear();
        }

        LOG.info(String.format(
                "GearIn training industry applied: market=%s size=%d supplyCrew=%d demandSupplies=%d demandCrew=%d income=%d upkeep=%d aiCore=%s disrupted=%s functional=%s",
                market.getName(), size,
                (int) getSupply(Commodities.CREW).getQuantity().getModifiedValue(),
                (int) getDemand(Commodities.SUPPLIES).getQuantity().getModifiedValue(),
                (int) getDemand(Commodities.CREW).getQuantity().getModifiedValue(),
                (int) getIncome().getModifiedValue(),
                (int) getUpkeep().getModifiedValue(),
                aiCoreId == null ? "none" : aiCoreId,
                isDisrupted(), isFunctional()));
    }

    @Override
    public void unapply() {
        super.unapply(); // 先 applyNoAICoreModifiers，再撤销改良/移民/特殊物品
    }

    // ---- AI core：自定义修饰必须成对(apply / unapply 对称) ----

    @Override
    protected void applyAlphaCoreModifiers() {
        market.getStability().modifyFlat(getModId(2), 1,
                "Alpha core (" + getNameForModifier() + ")");
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getStability().unmodifyFlat(getModId(2));
    }

    // ---- disruption 钩子(默认空实现，这里只打日志观察) ----

    @Override
    protected void notifyDisrupted() {
        LOG.info(String.format(
                "GearIn training industry disrupted: market=%s days=%d key=%s",
                market.getName(), (int) getDisruptedDays(), getDisruptedKey()));
    }

    @Override
    protected void disruptionFinished() {
        LOG.info("GearIn training industry disruption finished: market=" + market.getName());
    }

    /**
     * 观察助手：把目标市场里本产业的状态打一条日志(含供需明细)。
     * 供调用点、控制台与排障复用；市场无该产业时打印 not present。
     */
    public static void logState(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("GearIn training industry state: not present at " + market.getName());
            return;
        }

        StringBuilder supplyDesc = new StringBuilder();
        for (MutableCommodityQuantity curr : industry.getAllSupply()) {
            if (supplyDesc.length() > 0) supplyDesc.append(", ");
            supplyDesc.append(curr.getCommodityId())
                    .append("=")
                    .append((int) curr.getQuantity().getModifiedValue());
        }
        StringBuilder demandDesc = new StringBuilder();
        for (MutableCommodityQuantity curr : industry.getAllDemand()) {
            if (demandDesc.length() > 0) demandDesc.append(", ");
            demandDesc.append(curr.getCommodityId())
                    .append("=")
                    .append((int) curr.getQuantity().getModifiedValue());
        }

        LOG.info(String.format(
                "GearIn training industry state: market=%s id=%s supply=[%s] demand=[%s] income=%d upkeep=%d buildProgress=%.1f/%s building=%s upgrading=%s functional=%s disrupted=%s aiCore=%s upgrade=%s",
                market.getName(), industry.getId(), supplyDesc, demandDesc,
                (int) industry.getIncome().getModifiedValue(),
                (int) industry.getUpkeep().getModifiedValue(),
                industry.getBuildOrUpgradeProgress() * industry.getBuildTime(),
                industry.getBuildTime(),
                industry.isBuilding(), industry.isUpgrading(), industry.isFunctional(),
                industry.isDisrupted(),
                industry.getAICoreId() == null ? "none" : industry.getAICoreId(),
                industry.getSpec().getUpgrade()));
    }

    /**
     * 探针助手：对目标市场里本产业施放 10 天 disruption 并立即清除(useMax=false 覆盖语义)，
     * 打印 前后/期间 三态。只用于验证 setDisrupted / isDisrupted / getDisruptedDays 链路。
     */
    public static void runDisruptionProbe(MarketAPI market) {
        Industry industry = market.getIndustry(INDUSTRY_ID);
        if (industry == null) {
            LOG.info("GearIn training industry disruption probe: not present at " + market.getName());
            return;
        }
        LOG.info(String.format(
                "GearIn training industry disruption probe: before disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
        industry.setDisrupted(10f, false);
        LOG.info(String.format(
                "GearIn training industry disruption probe: during disrupted=%s days=%.1f functional=%s progress=%s",
                industry.isDisrupted(), industry.getDisruptedDays(),
                industry.isFunctional(), industry.getBuildOrUpgradeProgressText()));
        industry.setDisrupted(0f);
        LOG.info(String.format(
                "GearIn training industry disruption probe: after disrupted=%s days=%.1f",
                industry.isDisrupted(), industry.getDisruptedDays()));
    }
}
