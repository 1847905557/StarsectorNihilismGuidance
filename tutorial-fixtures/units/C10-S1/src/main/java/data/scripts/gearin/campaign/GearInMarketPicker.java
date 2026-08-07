package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * 目标市场选择器：优先按指定 id 取市场；id 为空或查无此市场时回退扫描，
 * 取第一个有开放市场(open_market 子市场)的市场，仍找不到返回 null。
 * 供巡检脚本 GearInMarketInspector 编排时调用。
 */
public class GearInMarketPicker {

    /** 首选市场 id；null 或查无此市场时走回退扫描。 */
    private final String preferredId;

    public GearInMarketPicker(String preferredId) {
        this.preferredId = preferredId;
    }

    /** 返回首选市场；不可用时回退扫描，仍找不到返回 null。 */
    public MarketAPI pick(EconomyAPI economy) {
        MarketAPI market = economy.getMarket(preferredId);
        if (market == null) {
            for (MarketAPI candidate : economy.getMarketsCopy()) {
                if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                    market = candidate;
                    break;
                }
            }
        }
        return market;
    }
}
