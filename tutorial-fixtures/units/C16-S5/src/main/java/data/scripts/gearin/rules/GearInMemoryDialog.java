package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/** Opens a Rules dialog after the Memory writer finishes. */
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
        if (Global.getSector() == null || done) {
            return;
        }
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
        PersonAPI person = Global.getSector().getPlayerPerson();
        target.setActivePerson(person);
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), target);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true target=" + target.getId());
            done = true;
        }
    }
}
