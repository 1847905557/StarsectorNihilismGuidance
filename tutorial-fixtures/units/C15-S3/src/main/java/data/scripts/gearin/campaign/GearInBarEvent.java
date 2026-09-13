package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

public class GearInBarEvent extends BaseBarEvent {

    private static final String OPTION_ACCEPT = "GEARIN_BAR_ACCEPT";
    private static final String OPTION_DECLINE = "GEARIN_BAR_DECLINE";
    private static final String OPTION_LEAVE = "GEARIN_BAR_LEAVE";
    private static final String METALS = "metals";
    private static final float METALS_REQUIRED = 100f;
    private static final int CREDIT_REWARD = 5000;

    private boolean rewardGranted;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market != null && market.hasSpaceport() && super.shouldShowAtMarket(market);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        dialog.getTextPanel().addPara(
                "A dockworker offers a credit payment for a shipment of refined metals.");
        dialog.getOptionPanel().addOption("Trade 100 metals for 5000 credits", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        options.clearOptions();
        options.addOption("Provide 100 metals", OPTION_ACCEPT);
        options.addOption("Refuse the trade", OPTION_DECLINE);
        options.addOption("Leave for now", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_ACCEPT.equals(optionData)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            if (cargo.getCommodityQuantity(METALS) < METALS_REQUIRED) {
                text.addPara("You need 100 metals before this trade can be accepted.");
                return;
            }
            if (!rewardGranted) {
                cargo.removeCommodity(METALS, METALS_REQUIRED);
                cargo.getCredits().add(CREDIT_REWARD);
                rewardGranted = true;
                text.addPara("The dockworker takes the metals and pays you 5000 credits.");
                done = true;
                BarEventManager.getInstance().notifyWasInteractedWith(this);
            }
        } else if (OPTION_DECLINE.equals(optionData)) {
            text.addPara("You refuse the trade and leave the dockworker behind.");
            done = true;
            noContinue = true;
            BarEventManager.getInstance().notifyWasInteractedWith(this);
        } else if (OPTION_LEAVE.equals(optionData)) {
            text.addPara("You leave before making a decision.");
            done = true;
            noContinue = true;
        }
    }
}
