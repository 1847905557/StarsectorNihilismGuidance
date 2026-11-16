package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin.TransferAction;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

/** Foundry 的回火合金仓库：定时补货，只出售、只收购回火合金，不出售舰船。 */
public class GearInAlloyDepotPlugin extends BaseSubmarketPlugin {

    private static final String ALLOY = GearInAlloyCargo.COMMODITY_ID;

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;
    }

    @Override
    public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
        return ALLOY.equals(commodity.getId());
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI commodity) {
        return shouldHaveCommodity(commodity) ? Math.max(20, commodity.getMaxSupply() * 2) : 0;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return !ALLOY.equals(commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (!stack.isCommodityStack()) {
            return true;
        }
        return isIllegalOnSubmarket(stack.getCommodityId(), action);
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "The depot only trades tempered alloy";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        return true;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "The depot does not deal in ships";
    }

    @Override
    public float getTariff() {
        return 0.2f;
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return true;
    }

    @Override
    public boolean showInFleetScreen() {
        return false;
    }
}
