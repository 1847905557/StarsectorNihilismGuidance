package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例(对话框插件)：演示交互对话框(InteractionDialogPlugin)。
 * 绑定位置：GearInDialogServicesScript 经 CampaignUIAPI.showInteractionDialog(plugin,
 * target) 打开(target 传 null)。optionSelected 的 optionText 是显示文本、optionData 是
 * addOption 传入的 id，分支判断必须用 optionData；未使用的回调保留空实现。
 */
public class GearInServicesDialog implements InteractionDialogPlugin {

    public static final String OPTION_FACTION = "faction";
    public static final String OPTION_CARGO = "cargo";
    public static final String OPTION_CLOSE = "close";

    private static final Logger LOG = Global.getLogger(GearInServicesDialog.class);

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        String target = dialog.getInteractionTarget() == null ? "null(本演示未绑定交互目标)"
                : dialog.getInteractionTarget().getName();
        dialog.getTextPanel().addPara("GearIn 生涯服务速览：交互对话框演示。交互目标=" + target
                + "。选择下方选项查看对应服务信息，或选择关闭结束对话框。");
        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption("查看势力关系", OPTION_FACTION, "显示玩家与霸主、海盗、独立势力的关系数值与等级");
        options.addOption("查看舰队货物", OPTION_CARGO, "显示玩家舰队补给、燃料、船员与信用状态");
        options.addOption("关闭演示", OPTION_CLOSE, "关闭本演示对话框");
        dialog.setOptionOnEscape("关闭演示", OPTION_CLOSE);
        LOG.info("GearIn campaign services dialog init: interactionTarget=" + target);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_FACTION.equals(optionData)) {
            FactionAPI playerFaction = Global.getSector().getPlayerFaction();
            dialog.getTextPanel().addPara(String.format(
                    "势力关系：霸主 %s(%s) 海盗 %s(%s) 独立 %s(%s)",
                    fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                    playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.PIRATES)),
                    playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                    fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                    playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_FACTION);
        } else if (OPTION_CARGO.equals(optionData)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            dialog.getTextPanel().addPara(String.format(
                    "舰队货物：补给 %.0f 燃料 %.0f 船员 %d 陆战队员 %d 信用 %.0f 已用空间 %.0f/%.0f",
                    cargo.getSupplies(), cargo.getFuel(), cargo.getCrew(), cargo.getMarines(),
                    cargo.getCredits().get(), cargo.getSpaceUsed(), cargo.getMaxCapacity()));
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CARGO);
        } else if (OPTION_CLOSE.equals(optionData)) {
            LOG.info("GearIn campaign services dialog option selected: " + OPTION_CLOSE);
            dialog.dismiss();
        }
    }

    // 未使用的回调保留空实现(InteractionDialogPlugin 要求全部实现)。
    @Override public void optionMousedOver(String optionText, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }
}
