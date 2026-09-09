package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.SubmarketPlugin.PlayerEconomyImpactMode;
import com.fs.starfarer.api.campaign.SubmarketPlugin.TransferAction;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

public class GearInRefineryDepotPlugin extends BaseSubmarketPlugin {
    private static final String[] STOCKPILE_IDS = {
            "metals",
            "rare_metals",
            "heavy_machinery"
    };

    private static final String[] PLAYER_SELL_IDS = {
            "ore",
            "rare_ore",
            "heavy_machinery"
    };

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            getCargo().getMothballedShips().clear();
            addShips(market.getFactionId(), 30f, 0f, 0f, 0f, 0f, 0f,
                    null, 0f, ShipPickMode.PRIORITY_THEN_ALL, null, 2);
            sinceSWUpdate = 0f;
        }
    }

    @Override
    public boolean shouldHaveCommodity(CommodityOnMarketAPI commodity) {
        return contains(STOCKPILE_IDS, commodity.getId());
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI commodity) {
        return shouldHaveCommodity(commodity) ? Math.max(20, commodity.getMaxSupply() * 2) : 0;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) {
            return !contains(PLAYER_SELL_IDS, commodityId);
        }
        return !contains(STOCKPILE_IDS, commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (!stack.isCommodityStack()) {
            return false;
        }
        return isIllegalOnSubmarket(stack.getCommodityId(), action);
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return action == TransferAction.PLAYER_SELL
                ? "Only refinery metals can be sold here"
                : "This depot does not stock that commodity";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_BUY) {
            return !(member.isFrigate() || member.isDestroyer());
        }
        return false;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Only frigates and destroyers are sold by this depot";
    }

    @Override
    public float getTariff() {
        return 0.30f;
    }

    @Override
    public String getBuyVerb() {
        return "Acquire";
    }

    @Override
    public String getSellVerb() {
        return "Refine and sell";
    }

    @Override
    public boolean isFreeTransfer() {
        return false;
    }

    @Override
    public boolean isOpenMarket() {
        return true;
    }

    @Override
    public boolean isBlackMarket() {
        return false;
    }

    @Override
    public boolean isMilitaryMarket() {
        return false;
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return true;
    }

    @Override
    public float getPlayerTradeImpactMult() {
        return 1f;
    }

    @Override
    public PlayerEconomyImpactMode getPlayerEconomyImpactMode() {
        return PlayerEconomyImpactMode.PLAYER_SELL_ONLY;
    }

    @Override
    public boolean showInFleetScreen() {
        return true;
    }

    @Override
    public boolean showInCargoScreen() {
        return true;
    }

    @Override
    public boolean okToUpdateShipsAndWeapons() {
        return super.okToUpdateShipsAndWeapons();
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
