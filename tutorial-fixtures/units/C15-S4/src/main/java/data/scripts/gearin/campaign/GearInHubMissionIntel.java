package data.scripts.gearin.campaign;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInHubMissionIntel extends BaseIntelPlugin {

    private GearInHubMission mission;

    public GearInHubMissionIntel(GearInHubMission mission) {
        this.mission = mission;
    }

    @Override
    public String getName() {
        return "GearIn inspection task";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "important_accepted_mission");
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>(super.getIntelTags(map));
        tags.add(Tags.INTEL_MISSIONS);
        return tags;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        info.addPara("Current stage: " + getStageText(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("Current stage: " + getStageText(), 0f);
    }

    private String getStageText() {
        return mission == null ? "unavailable" : String.valueOf(mission.getCurrentStage());
    }
}
