package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 根据对象 Memory 选择交互与战场插件，并在市场 Memory 刷新时写入动态事实。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    public static final String PLUGIN_ID = "GearIn_campaign_plugin";
    public static final String DIALOG_TARGET_KEY = "$GearIn_dialog_target";
    public static final String BATTLE_TARGET_KEY = "$GearIn_battle_target";
    public static final String MARKET_FACTS_TARGET_KEY = "$GearIn_market_facts_target";
    public static final String MARKET_READY_KEY = "$GearIn_campaign_plugin_ready";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(
            SectorEntityToken interactionTarget) {
        if (interactionTarget == null
                || !interactionTarget.getMemoryWithoutUpdate().getBoolean(DIALOG_TARGET_KEY)) {
            return null;
        }
        return new PluginPick<>(
                new GearInTargetDialog(interactionTarget),
                PickPriority.MOD_SPECIFIC);
    }

    @Override
    public PluginPick<BattleCreationPlugin> pickBattleCreationPlugin(
            SectorEntityToken opponent) {
        if (!(opponent instanceof CampaignFleetAPI)
                || !opponent.getMemoryWithoutUpdate().getBoolean(BATTLE_TARGET_KEY)) {
            return null;
        }
        return new PluginPick<>(
                new GearInObjectiveBattleCreationPlugin(),
                PickPriority.MOD_SPECIFIC);
    }

    @Override
    public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
        if (memory.getBoolean(MARKET_FACTS_TARGET_KEY)) {
            memory.set(MARKET_READY_KEY, true, 0f);
        }
    }
}
