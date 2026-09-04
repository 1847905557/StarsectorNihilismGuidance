package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 完整最小例：被生涯插件 picker 选中的交互对话框 (InteractionDialogPlugin 角色)。
 *
 * 由 GearInCampaignPlugin.pickInteractionDialogPlugin 在 pick 时现场创建并以
 * PickPriority.MOD_SPECIFIC 返回，证明 "本插件的选择胜出" 与 "facts 已写入"，
 * 提供 "刷新事实" 与 "离开" 两个选项。
 *
 * refreshFact() 读 Global.getSector().getMemory() —— 这次读取本身会触发全插件
 * (含 GearInCampaignPlugin) 的 updateGlobalFacts，因此显示值必然反映
 * "已注册 + facts 生效" 两个事实；这里只读不写，不构成递归。
 */
public class GearInInteractionDialog implements InteractionDialogPlugin {

    private static final String OPT_REFRESH = "refresh";
    private static final String OPT_LEAVE = "leave";

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        SectorEntityToken target = dialog.getInteractionTarget();
        dialog.getTextPanel().addParagraph(
                "GearIn demo dialog: routed by pickInteractionDialogPlugin() "
                        + "with PickPriority.MOD_SPECIFIC. Target: "
                        + (target != null ? target.getName() : "null"));
        refreshFact();
        dialog.setPromptText("GearIn campaign plugin demo");
        showOptions();
    }

    private void refreshFact() {
        boolean fact = Global.getSector().getMemory().getBoolean(GearInCampaignPlugin.DEMO_GLOBAL_FACT);
        dialog.getTextPanel().addParagraph(
                "Global fact [" + GearInCampaignPlugin.DEMO_GLOBAL_FACT + "] = " + fact
                        + " (recomputed by updateGlobalFacts() on every getMemory() call)");
    }

    private void showOptions() {
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Refresh facts", OPT_REFRESH);
        dialog.getOptionPanel().addOption("Leave", OPT_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPT_REFRESH.equals(optionData)) {
            refreshFact();
            showOptions();
        } else if (OPT_LEAVE.equals(optionData)) {
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
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
