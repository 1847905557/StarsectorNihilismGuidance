package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：市场条件 (MarketConditionAPI) 插件。
 *
 * 注册入口：data/campaign/market_conditions.csv 的 script 列 (类名逐字一致)，
 * 由 CSV spec 加载期的 loadMarketConditions 完成 registerScriptClass；条件实例创建时
 * 脚本类加载器的 loadScriptInstance 按类名 newInstance(必须无参可实例化)，随后
 * init(market, condition) 绑定，再进入 apply/advance/unapply 生命周期。
 *
 * 数据所有权：本节新增条件 id {@code GearIn_ore_deposits}，只由本节定义；页面正文
 * 的 CSV 行与本类逐字配套。
 *
 * 行为：apply(id) 以传入的实例 token 为 modifier source 写入两条市场修饰 ——
 *   1. hazard +0.15(MutableStat，getHazard())；
 *   2. accessibility +0.10(StatBonus，getAccessibilityMod()，默认值可由
 *      addCondition(id, param) 的 param 覆盖，setParam 在 apply 之前被调用)。
 * unapply(id) 按同一 source 撤销。修饰 source 用实例 token 而非 spec id，
 * 同 spec id 多实例才不会互相覆盖 ("GearIn_ore_deposits" 是 spec id，
 * 每个实例的 token 形如 "GearIn_ore_deposits_xxxxx"，由
 * MarketConditionAPI.getIdForPluginModifications() 提供)。
 *
 * 存档语义：继承 BaseMarketConditionPlugin，isTransient() = true——plugin 实例
 * 不进存档，读档后按 spec 重建并重新 init，setParam 的数据不重放
 *
 * tooltip：createTooltipAfterDescription 在 spec desc(含 $marketName token) 之后
 * 追加一条本条件的数值说明，演示 tooltip 扩展链路。
 */
public class GearInOreCondition extends BaseMarketConditionPlugin {

    /** 条件 spec id(与 market_conditions.csv 的 id 列逐字一致)。 */
    public static final String ORE_DEPOSITS = "GearIn_ore_deposits";

    /** hazard 平值修饰 (1f = 100%)。 */
    public static final float HAZARD_MOD = 0.15f;

    /** accessibility 平值修饰默认值；addCondition(id, param) 的 param 可覆盖。 */
    public static final float DEFAULT_ACCESSIBILITY_BONUS = 0.10f;

    private float accessibilityBonus = DEFAULT_ACCESSIBILITY_BONUS;

    public GearInOreCondition() {
        // 空构造：脚本类加载器按类名 newInstance，必须无参可实例化
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Number) {
            accessibilityBonus = ((Number) param).floatValue();
        }
    }

    @Override
    public void apply(String id) {
        MarketAPI m = market;
        m.getHazard().modifyFlat(id, HAZARD_MOD, getName());
        m.getAccessibilityMod().modifyFlat(id, accessibilityBonus, getName());
    }

    @Override
    public void unapply(String id) {
        market.getHazard().unmodifyFlat(id);
        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        String hazardPct = "+" + (int) (HAZARD_MOD * 100f) + "%";
        String accPct = "+" + (int) (accessibilityBonus * 100f) + "%";
        tooltip.addPara("%s hazard, %s accessibility (GearIn ore veins)", 10f,
                Misc.getHighlightColor(), hazardPct, accPct);
    }
}
