package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;

/** Foundry 酒吧里的码头工人：私下低价卖出一小批回火合金。只在 Foundry 出现。 */
public class GearInDockworkerBarEvent extends BaseBarEvent {

    private static final int QUANTITY = 10;

    private static final int UNIT_PRICE = 100;

    private enum Option {
        BUY,
        DECLINE
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) {
            return false;
        }
        return GearInFoundryGenerator.MARKET_ID.equals(market.getId());
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara("角落里有个穿着码头工装的人，面前的杯子早就空了，眼睛却一直往门口瞟。");
        dialog.getOptionPanel().addOption("走过去坐下", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        int total = QUANTITY * UNIT_PRICE;
        text.addPara("他压低声音：仓库清点时 \"多出来\" " + QUANTITY + " 件回火合金，每件 " + UNIT_PRICE
                + " 星币，一共 " + total + "。不走仓库的账，也不开单据。");
        options.addOption("买下来", Option.BUY);
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < total) {
            options.setEnabled(Option.BUY, false);
            options.setTooltip(Option.BUY, "星币不足。");
        }
        options.addOption("不感兴趣", Option.DECLINE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.BUY) {
            int bought = GearInAlloyCargo.give(QUANTITY);
            if (bought <= 0) {
                text.addPara("他看了一眼您的货单：货舱已经满了，这笔生意改天再说。");
                done = true;
                return;
            }
            int cost = bought * UNIT_PRICE;
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.getCredits().subtract(cost);
            AddRemoveCommodity.addCreditsLossText(cost, text);
            text.addPara("货会在您离港前装上船。他临走前提醒了一句：带着这些东西回 Foundry 的时候，记得自己想好怎么跟港务解释。");
            BarEventManager.getInstance().notifyWasInteractedWith(this);
        } else {
            text.addPara("他耸耸肩，重新盯着门口。");
        }
        done = true;
    }
}
