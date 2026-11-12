package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;

public class GearInBarHubMission extends HubMissionWithBarEvent {

    public enum Stage {
        GearIn_bar_stage_active,
        GearIn_bar_stage_completed
    }

    public static final Stage STAGE_ACTIVE = Stage.GearIn_bar_stage_active;
    public static final Stage STAGE_COMPLETED = Stage.GearIn_bar_stage_completed;

    public GearInBarHubMission() {
        setMissionId("GearIn_bar_hub");
        setName("GearIn dockside inspection");
        setStartingStage(STAGE_ACTIVE);
        setSuccessStage(STAGE_COMPLETED);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (createdAt == null || !barEvent) {
            return false;
        }
        connectWithDaysElapsed(STAGE_ACTIVE, STAGE_COMPLETED, 1f);
        return true;
    }

    @Override
    public String getStageDescriptionText() {
        return "Current stage: " + getCurrentStage();
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The dockside inspection task is now active.");
        }
    }
}
