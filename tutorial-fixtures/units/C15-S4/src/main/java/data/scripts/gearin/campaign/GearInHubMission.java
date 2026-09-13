package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;

public class GearInHubMission extends BaseHubMission {

    public static final String STAGE_INVESTIGATE = "GearIn_stage_investigate";
    public static final String STAGE_REPORT = "GearIn_stage_report";
    public static final String STAGE_COMPLETED = "GearIn_stage_completed";
    public static final String STAGE_FAILED = "GearIn_stage_failed";
    public static final String STAGE_ABANDONED = "GearIn_stage_abandoned";
    public static final String TARGET_COMPLETE_KEY = "$GearIn_hub_target_complete";

    private SectorEntityToken target;
    private boolean rewardGranted;
    private boolean missionEnded;

    public GearInHubMission(SectorEntityToken target) {
        this.target = target;
        setMissionId("GearIn_hub_mission");
        setName("GearIn field inspection");
        setStartingStage(STAGE_INVESTIGATE);
        setSuccessStage(STAGE_COMPLETED);
        setFailureStage(STAGE_FAILED);
        setAbandonStage(STAGE_ABANDONED);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (target == null) {
            return false;
        }
        setStageOnMemoryFlag(STAGE_REPORT, target, TARGET_COMPLETE_KEY);
        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            dialog.getTextPanel().addPara("The inspection task is now active.");
        }
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        if (!rewardGranted) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(50000);
            rewardGranted = true;
        }
        missionEnded = true;
    }

    @Override
    protected void endFailureImpl(InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap) {
        missionEnded = true;
    }

    @Override
    protected void endAbandonImpl() {
        missionEnded = true;
    }

    public boolean isRewardGranted() {
        return rewardGranted;
    }

    public boolean isMissionEnded() {
        return missionEnded;
    }
}
