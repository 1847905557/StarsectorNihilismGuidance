package data.scripts.gearin.campaign;

import java.util.Collections;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 显示 Campaign 插件选中的交互目标及其市场事实。
 */
public class GearInTargetDialog implements InteractionDialogPlugin {

    private static final String OPTION_CLOSE = "GearIn_close_campaign_plugin_dialog";

    private final SectorEntityToken target;
    private InteractionDialogAPI dialog;

    public GearInTargetDialog(SectorEntityToken target) {
        this.target = target;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        boolean dialogTarget = target != null
                && target.getMemoryWithoutUpdate()
                        .getBoolean(GearInCampaignPlugin.DIALOG_TARGET_KEY);
        MarketAPI market = target == null ? null : target.getMarket();
        boolean marketReady = market != null
                && market.getMemory().getBoolean(GearInCampaignPlugin.MARKET_READY_KEY);

        dialog.getTextPanel().addPara("GearIn CampaignPlugin 选择了这个交互对话框。");
        dialog.getTextPanel().addPara("目标 ID：" + (target == null ? "null" : target.getId()));
        dialog.getTextPanel().addPara("对话标记：" + dialogTarget);
        dialog.getTextPanel().addPara("市场 ready fact：" + marketReady);
        dialog.getOptionPanel().addOption("关闭", OPTION_CLOSE);
        dialog.setOptionOnEscape("关闭", OPTION_CLOSE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_CLOSE.equals(optionData)) {
            dialog.dismiss();
        }
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
        return target;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }
}
