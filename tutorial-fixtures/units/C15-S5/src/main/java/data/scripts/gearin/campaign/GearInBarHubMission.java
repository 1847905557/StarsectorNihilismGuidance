package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;

public class GearInBarHubMission extends HubMissionWithBarEvent {

    public static final String STAGE_ACTIVE = "GearIn_bar_stage_active";
    public static final String STAGE_COMPLETED = "GearIn_bar_stage_completed";

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
        setStageOnDaysElapsed(STAGE_COMPLETED, 1f);
        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The dockside inspection task is now active.");
        }
    }
}
