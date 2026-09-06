package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：市场条件 (MarketConditionAPI) 插件。
 * 注册入口：data/campaign/market_conditions.csv 的 script 列 (类名逐字一致)，条件实例
 * 由脚本类加载器按类名 newInstance 并 init 绑定。apply(id) 以传入的实例 token 为
 * modifier source 写入 hazard +0.15 与 accessibility +0.10 (param 可覆盖)，unapply(id)
 * 按同一 source 撤销；token 而非 spec id 做 source，同 spec 多实例才不互相覆盖。
 * plugin 实例不进存档 (transient)，读档后按 spec 重建重新 init；tooltip 在 spec desc
 * 之后追加本条件的数值说明。
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
