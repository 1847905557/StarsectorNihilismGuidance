package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class GearInSalvageTokenPlugin extends BaseSpecialItemPlugin {
    public static final String ITEM_ID = "GearIn_salvage_token";

    @Override
    public boolean hasRightClickAction() {
        return true;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        helper.removeFromClickedStackFirst(1);
        helper.addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, 10f);
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }
}
