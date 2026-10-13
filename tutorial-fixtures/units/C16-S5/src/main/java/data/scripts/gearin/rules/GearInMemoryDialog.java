package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/** 状态准备完成后打开规则对话，并恢复临时人物。 */
public class GearInMemoryDialog implements EveryFrameScript {

    public static final String INITIAL_TRIGGER = "GearInMemoryDialog";

    private static final Logger LOG = Global.getLogger(GearInMemoryDialog.class);

    private boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.getBoolean(GearInMemoryKeys.KEY_READY)) {
            return;
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market == null || market.getPrimaryEntity() == null) {
            LOG.warn("GearInMemoryDialog: jangala interaction target unavailable");
            done = true;
            return;
        }
        SectorEntityToken target = market.getPrimaryEntity();
        PersonAPI previousPerson = target.getActivePerson();
        PersonAPI person = Global.getSector().getPlayerPerson();
        target.setActivePerson(person);
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        RuleBasedInteractionDialogPluginImpl plugin =
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER) {
                    @Override
                    public void init(InteractionDialogAPI dialog) {
                        super.init(dialog);
                        dialog.setOptionOnEscape("结束", "GearIn_memory_leave");
                    }

                    @Override
                    public void optionSelected(String optionText, Object optionData) {
                        if ("GearIn_memory_leave".equals(optionData)) {
                            target.setActivePerson(previousPerson);
                        }
                        super.optionSelected(optionText, optionData);
                    }
                };
        boolean shown = ui.showInteractionDialog(plugin, target);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true target=" + target.getId());
            done = true;
        } else {
            target.setActivePerson(previousPerson);
        }
    }
}
