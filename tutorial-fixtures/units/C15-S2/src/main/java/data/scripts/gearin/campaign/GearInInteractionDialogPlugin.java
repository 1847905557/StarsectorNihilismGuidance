package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Collections;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;

public class GearInInteractionDialogPlugin implements InteractionDialogPlugin {
    private static final String NEXT = "NEXT";
    private static final String REMOVE_TEMP = "REMOVE_TEMP";
    private static final String READ_SELECTOR = "READ_SELECTOR";
    private static final String PERSON_COMPACT = "PERSON_COMPACT";
    private static final String PERSON_HIDE = "PERSON_HIDE";
    private static final String PERSON_SHOW = "PERSON_SHOW";
    private static final String VISUAL_FADE = "VISUAL_FADE";
    private static final String VISUAL_FINISH_FADE = "VISUAL_FINISH_FADE";
    private static final String VISUAL_RESTORE = "VISUAL_RESTORE";
    private static final String CONFIRM = "CONFIRM";
    private static final String SELECTOR = "SELECTOR";
    private static final String LEAVE = "LEAVE";
    private static final String CANCEL = "CANCEL";

    private InteractionDialogAPI dialog;
    private OptionPanelAPI options;
    private TextPanelAPI text;
    private VisualPanelAPI visual;
    private int round;
    private boolean checked;
    private boolean closed;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        options = dialog.getOptionPanel();
        text = dialog.getTextPanel();
        visual = dialog.getVisualPanel();
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target == null) {
            text.addPara("No interaction target is available.");
            close(true);
            return;
        }
        text.setFontInsignia();
        text.setOrbitronMode(false);
        text.addPara("Interaction target: " + target.getId());
        text.addParagraph("The target is shown first; later rounds use fixed player objects.", Color.LIGHT_GRAY);
        if (target.getCustomInteractionDialogImageVisual() != null) {
            visual.showImageVisual(target.getCustomInteractionDialogImageVisual());
        }
        rebuildOptions("Continue to text fonts");
        dialog.setOptionOnEscape("Cancel", CANCEL);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (closed) {
            return;
        }
        if (LEAVE.equals(optionData)) {
            close(false);
            return;
        }
        if (CANCEL.equals(optionData)) {
            close(true);
            return;
        }
        if ("TEMP".equals(optionData)) {
            text.addPara("Renamed display text still has optionData TEMP.");
            return;
        }
        if (REMOVE_TEMP.equals(optionData)) {
            options.removeOption("TEMP");
            options.removeOption(REMOVE_TEMP);
            return;
        }
        if (PERSON_COMPACT.equals(optionData)) {
            visual.showPersonInfo(Global.getSector().getPlayerPerson(), true, true);
            return;
        }
        if (PERSON_HIDE.equals(optionData)) {
            visual.hideFirstPerson();
            return;
        }
        if (PERSON_SHOW.equals(optionData)) {
            visual.showFirstPerson();
            return;
        }
        if (VISUAL_FADE.equals(optionData)) {
            visual.setVisualFade(0.2f, 0.2f);
            visual.fadeVisualOut();
            return;
        }
        if (VISUAL_FINISH_FADE.equals(optionData)) {
            visual.finishFadeFast();
            return;
        }
        if (VISUAL_RESTORE.equals(optionData)) {
            visual.restoreSavedVisual();
            return;
        }
        if (READ_SELECTOR.equals(optionData)) {
            text.addPara("Selected value=" + options.getSelectorValue(SELECTOR));
            return;
        }
        if (CONFIRM.equals(optionData)) {
            if (!checked) {
                checked = true;
                text.addPara("Inspection result recorded once.");
                options.setEnabled(CONFIRM, false);
            }
            return;
        }
        if (!NEXT.equals(optionData) || round >= 9) {
            return;
        }
        round++;
        options.clearOptions();
        switch (round) {
            case 1:
                text.setFontOrbitron();
                text.addPara("Round 1: font and mode selection.", Color.CYAN);
                text.addPara("Orbitron mode = " + text.isOrbitronMode());
                options.addOption("Continue to paragraph writing", NEXT);
                break;
            case 2:
                text.setFontVictor();
                text.addPara("Round 2: addPara and addParagraph overloads.");
                text.addPara("Colored paragraph", Color.YELLOW);
                text.addParagraph("Paragraph alias", Color.GREEN);
                text.addPara("Format %s", Color.WHITE, Color.YELLOW, "value");
                options.addOption("Continue to paragraph editing", NEXT);
                break;
            case 3:
                text.replaceLastParagraph("Round 3: replaceLastParagraph changed this line.");
                text.appendToLastParagraph(" appendToLastParagraph adds text.");
                text.appendToLastParagraph(8, " trimmed prefix remains.");
                text.highlightFirstInLastPara("prefix", Color.YELLOW);
                text.highlightLastInLastPara("remains", Color.GREEN);
                text.highlightInLastPara(Color.CYAN, "appendToLastParagraph", "remains");
                text.setHighlightColorsInLastPara(Color.YELLOW, Color.GREEN);
                text.addImage("illustrations", "space_wreckage");
                text.updateSize();
                options.addOption("Continue to option management", NEXT);
                break;
            case 4:
                text.addPara("Round 4: add, rename, enable and remove options.");
                options.addOption("Temporary option", "TEMP", "Created with a tooltip.");
                options.setOptionText("Renamed option", "TEMP");
                options.setTooltip("TEMP", "Tooltip for the stable optionData key.");
                options.setTooltipHighlights("TEMP", "stable optionData");
                options.setTooltipHighlightColors("TEMP", Color.YELLOW);
                options.setShortcut("TEMP", org.lwjgl.input.Keyboard.KEY_O, false, false, false, false);
                options.setEnabled("TEMP", true);
                options.addOption("Remove the renamed option", REMOVE_TEMP);
                options.addOption("Continue to selector", NEXT);
                break;
            case 5:
                text.addPara("Round 5: selector creation, value and bounds.");
                options.addSelector("Scan range", SELECTOR, Color.CYAN, 220f, 50f, 1f, 5f,
                        ValueDisplayMode.VALUE, "Inspection radius");
                options.setSelectorValue(SELECTOR, 3f);
                text.addPara("Current=" + options.getSelectorValue(SELECTOR)
                        + ", min=" + options.getMinSelectorValue(SELECTOR)
                        + ", max=" + options.getMaxSelectorValue(SELECTOR));
                options.addOption("Read the selector value", READ_SELECTOR);
                options.addOption("Continue to confirmation", NEXT);
                break;
            case 6:
                text.addPara("Round 6: confirmation and saved option state.");
                options.addOption("Record inspection", CONFIRM);
                options.addOptionConfirmation(CONFIRM, "Record this inspection?", "Confirm", "Back");
                options.optionHasConfirmDelegate(CONFIRM);
                options.getOptionDataBeingConfirmed();
                options.getSavedOptionList();
                options.addOption("Continue to player fleet", NEXT);
                break;
            case 7:
                text.addPara("Round 7: show the player's fleet.");
                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                visual.saveCurrentVisual();
                visual.showFleetInfo("Your fleet", playerFleet, null, null);
                options.addOption("Continue to player person", NEXT);
                break;
            case 8:
                text.addPara("Round 8: show the player person and person slots.");
                PersonAPI player = Global.getSector().getPlayerPerson();
                visual.showPersonInfo(player);
                options.addOption("Show compact person view", PERSON_COMPACT);
                options.addOption("Hide the first person", PERSON_HIDE);
                options.addOption("Show the first person", PERSON_SHOW);
                options.addOption("Continue to original illustration", NEXT);
                break;
            case 9:
                text.addPara("Round 9: show, fade and restore an original illustration.");
                visual.showImagePortion("illustrations", "space_wreckage", 640f, 400f,
                        0f, 0f, 480f, 300f);
                options.addOption("Fade the visual out", VISUAL_FADE);
                options.addOption("Finish the fade immediately", VISUAL_FINISH_FADE);
                options.addOption("Restore the saved visual", VISUAL_RESTORE);
                options.addOption("Finish", LEAVE);
                options.addOption("Cancel", CANCEL);
                break;
            default:
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        if (!closed && NEXT.equals(optionData)) {
            options.setTooltip(NEXT, "Advance one tutorial round.");
        }
    }

    @Override
    public void advance(float amount) {
        if (!closed) {
            text.advance(amount);
        }
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return dialog == null ? null : dialog.getInteractionTarget();
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }

    private void rebuildOptions(String nextText) {
        options.clearOptions();
        options.addOption(nextText, NEXT);
        options.addOption("Leave", LEAVE);
        options.addOption("Cancel", CANCEL);
    }

    private void close(boolean cancel) {
        if (closed) {
            return;
        }
        closed = true;
        if (cancel) {
            dialog.dismissAsCancel();
        } else {
            dialog.dismiss();
        }
    }
}
