package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;

/** Foundry 的码头检查：玩家货舱里有回火合金时先过这一关，处理完后关闭对话。 */
public class GearInDockInspectionDialog implements InteractionDialogPlugin {

    /** 市场 Memory：近期检查过，期间不再拦截。 */
    public static final String INSPECTED_KEY = "$GearIn_inspected";

    private static final float INSPECTED_DAYS = 30f;

    /** 每件回火合金的罚款。 */
    private static final int FINE_PER_UNIT = 150;

    private enum Option {
        PAY_FINE,
        SURRENDER,
        LEAVE,
        CONTINUE
    }

    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private int quantity;
    private int fine;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        text = dialog.getTextPanel();
        options = dialog.getOptionPanel();
        dialog.getVisualPanel().showImagePortion("illustrations", "cargo_loading", 640, 400, 0, 0, 480, 300);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        quantity = (int) cargo.getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID);
        fine = quantity * FINE_PER_UNIT;

        text.addPara("港务的检查船贴了上来。扫描结果出来得很快：您的货舱里有 " + quantity
                + " 件回火合金，而它们没有一件是从 Foundry 的仓库里买的。");
        text.addPara("检查员的意思很清楚：要么按每件 " + FINE_PER_UNIT + " 星币交罚款，要么把货留下。");
        options.addOption("交罚款 (" + fine + " 星币)", Option.PAY_FINE);
        if (cargo.getCredits().get() < fine) {
            options.setEnabled(Option.PAY_FINE, false);
            options.setTooltip(Option.PAY_FINE, "星币不足。");
        }
        options.addOption("交出回火合金", Option.SURRENDER);
        options.addOption("离开", Option.LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionText != null) {
            dialog.addOptionSelectedText(optionData);
        }
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        switch ((Option) optionData) {
            case PAY_FINE:
                cargo.getCredits().subtract(fine);
                AddRemoveCommodity.addCreditsLossText(fine, text);
                text.addPara("检查员收了钱，在您的货单上盖了章。");
                passInspection();
                break;
            case SURRENDER:
                cargo.removeCommodity(GearInAlloyCargo.COMMODITY_ID, quantity);
                AddRemoveCommodity.addCommodityLossText(GearInAlloyCargo.COMMODITY_ID, quantity, text);
                text.addPara("合金被搬上了检查船，没有人多看您一眼。");
                passInspection();
                break;
            case LEAVE:
                dialog.dismiss();
                break;
            case CONTINUE:
                dialog.dismiss();
                break;
        }
    }

    /** 记下这次检查，换成一个 "继续" 选项。 */
    private void passInspection() {
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        market.getMemoryWithoutUpdate().set(INSPECTED_KEY, true, INSPECTED_DAYS);
        options.clearOptions();
        options.addOption("继续", Option.CONTINUE);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
