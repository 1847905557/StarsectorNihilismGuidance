package data.scripts.gearin.campaign;

import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.gearin.GearInModPlugin;

/** Nipher 的巡逻活动：列出星系内齿轮工业巡逻队的指挥官与当前任务。玩家接触齿轮工业之前不显示。 */
public class GearInPatrolIntel extends BaseIntelPlugin {

    private static final String SYSTEM_NAME = "Nipher";

    private static final String PRIME_ID = "GearIn_planet1";

    @Override
    public boolean isHidden() {
        return super.isHidden()
                || !Global.getSector().getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY);
    }

    @Override
    protected String getName() {
        return "Nipher 的巡逻活动";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "fleet_log");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(GearInModPlugin.FACTION_ID);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(GearInModPlugin.FACTION_ID);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return Global.getSector().getEntityById(PRIME_ID);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        StarSystemAPI system = Global.getSector().getStarSystem(SYSTEM_NAME);
        if (system == null) {
            info.addPara("这个存档中没有 Nipher 星系。", opad);
            return;
        }
        int count = 0;
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
                continue;
            }
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
                continue;
            }
            count++;
            String commander = fleet.getCommander() != null ? fleet.getCommander().getNameString() : "无名指挥官";
            FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
            String action = assignment != null && assignment.getActionText() != null
                    ? assignment.getActionText() : "待命";
            info.addPara("%s 指挥的巡逻队：%s", opad, Misc.getHighlightColor(), commander, action);
        }
        if (count == 0) {
            info.addPara("Nipher 星系中暂时没有齿轮工业的巡逻队。", opad);
        }
    }
}
