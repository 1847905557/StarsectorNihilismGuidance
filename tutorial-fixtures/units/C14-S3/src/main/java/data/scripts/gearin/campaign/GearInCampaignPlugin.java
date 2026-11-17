package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/** 齿轮工业的生涯插件：玩家最近击败过齿轮工业的巡逻队时，Foundry 改用拒绝停靠的对话。 */
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
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInPatrolGrudgeListener.GRUDGE_KEY)) {
            return null;
        }
        return new PluginPick<InteractionDialogPlugin>(new GearInDockRefusalDialog(), PickPriority.MOD_SPECIFIC);
    }
}
