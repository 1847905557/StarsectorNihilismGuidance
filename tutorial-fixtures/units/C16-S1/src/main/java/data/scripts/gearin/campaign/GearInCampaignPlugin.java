package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 齿轮工业的生涯插件：敌意标记生效时 Foundry 拒绝停靠；货舱里有回火合金时，先过码头检查。 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId() {
        return "GearIn_campaign_plugin";
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget == null || !GearInFoundryGenerator.STATION_ID.equals(interactionTarget.getId())) {
            return null;
        }
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
            return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
        }
        MarketAPI market = interactionTarget.getMarket();
        boolean carrying = Global.getSector().getPlayerFleet().getCargo()
                .getCommodityQuantity(GearInAlloyCargo.COMMODITY_ID) > 0f;
        if (carrying && market != null
                && !market.getMemoryWithoutUpdate().getBoolean(GearInDockInspectionDialog.INSPECTED_KEY)) {
            return new PluginPick<InteractionDialogPlugin>(new GearInDockInspectionDialog(), PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}
