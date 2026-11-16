package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

/** 回火合金的货舱读写：按剩余空间加入玩家货舱，扣除时不超过现有数量。 */
public final class GearInAlloyCargo {

    public static final String COMMODITY_ID = "GearIn_tempered_alloy";

    private GearInAlloyCargo() {
    }

    /** 加入玩家货舱，返回实际加入的数量；空间不足时只加入放得下的部分。 */
    public static int give(int amount) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        float spacePerUnit = Global.getSettings().getCommoditySpec(COMMODITY_ID).getCargoSpace();
        int fit = amount;
        if (spacePerUnit > 0f) {
            fit = (int) Math.min(amount, cargo.getSpaceLeft() / spacePerUnit);
        }
        if (fit <= 0) {
            return 0;
        }
        cargo.addCommodity(COMMODITY_ID, fit);
        Global.getSector().getCampaignUI().addMessage("获得回火合金 x" + fit);
        return fit;
    }

    /** 从玩家货舱扣除，返回实际扣除的数量。 */
    public static int take(int amount) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int owned = (int) cargo.getCommodityQuantity(COMMODITY_ID);
        int removed = Math.min(amount, owned);
        if (removed <= 0) {
            return 0;
        }
        cargo.removeCommodity(COMMODITY_ID, removed);
        return removed;
    }
}
