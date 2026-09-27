package data.scripts.gearin.campaign;

import java.util.Collections;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

public class GearInConversationDialog implements InteractionDialogPlugin {

    private static final String TARGET_KEY = "$GearIn_dialog_target";
    private static final String OPTION_CHECK = "CHECK";
    private static final String OPTION_LEAVE = "LEAVE";
    private static final String OPTION_CANCEL = "CANCEL";

    private InteractionDialogAPI dialog;
    private boolean checked;
    private boolean closed;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();
        if (dialog == null || target == null) {
            if (dialog != null) {
                dialog.dismissAsCancel();
            }
            return;
        }

        TextPanelAPI text = dialog.getTextPanel();
        if (!target.getMemoryWithoutUpdate().getBoolean(TARGET_KEY)) {
            text.addPara("This target has no GearIn dialog marker.");
            dialog.dismissAsCancel();
            return;
        }

        text.addPara("Target ID: " + target.getId());
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Continue inspection", OPTION_CHECK);
        options.addOption("Leave", OPTION_LEAVE);
        options.addOption("Cancel", OPTION_CANCEL);
        dialog.setOptionOnEscape("Cancel", OPTION_CANCEL);
        VisualPanelAPI visual = dialog.getVisualPanel();
        if (target.getCustomInteractionDialogImageVisual() != null) {
            visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
        } else {
            dialog.hideVisualPanel();
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (closed || dialog == null) {
            return;
        }
        if (OPTION_CHECK.equals(optionData)) {
            if (!checked) {
                checked = true;
                dialog.getTextPanel().addPara("The target responds to the inspection.");
            }
        } else if (OPTION_LEAVE.equals(optionData)) {
            closed = true;
            dialog.dismiss();
        } else if (OPTION_CANCEL.equals(optionData)) {
            closed = true;
            dialog.dismissAsCancel();
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
        if (closed || dialog == null) {
            return;
        }
        dialog.getTextPanel().addPara("The inspection continues after Engagement.");
    }

    @Override
    public Object getContext() {
        return dialog == null ? null : dialog.getInteractionTarget();
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }

}
