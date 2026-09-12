package data.scripts.gearin.campaign;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GearInFleetActivityIntel extends BaseIntelPlugin {
    public static final String TARGET_MEMORY_KEY = "$GearIn_intel_target";
    public static final float ACTIVITY_DAYS = 30.0f;

    private CampaignFleetAPI fleet;
    private float elapsedDays;
    private String lastLocationId;

    public GearInFleetActivityIntel(CampaignFleetAPI fleet) {
        this.fleet = fleet;
        if (fleet != null) {
            setPostingLocation(fleet);
            lastLocationId = getLocationId(fleet.getContainingLocation());
        }
        setImportant(true);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (fleet == null || !fleet.isAlive()) {
            finish("fleet unavailable");
            return;
        }

        elapsedDays += Global.getSector().getClock().convertToDays(amount);

        LocationAPI location = fleet.getContainingLocation();
        String locationId = getLocationId(location);
        if (locationId != null && !locationId.equals(lastLocationId)) {
            lastLocationId = locationId;
            sendUpdateIfPlayerHasIntel("moved to " + locationId, false);
        }

        if (elapsedDays >= ACTIVITY_DAYS) {
            finish("activity expired");
        }
    }

    private void finish(String reason) {
        if (isEnding() || isEnded()) {
            return;
        }
        sendUpdateIfPlayerHasIntel(reason, false);
        endAfterDelay();
    }

    private String getLocationId(LocationAPI location) {
        return location == null ? null : location.getId();
    }

    @Override
    public String getName() {
        return "GearIn fleet activity";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "fleet_log");
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>(super.getIntelTags(map));
        tags.add(Tags.INTEL_FLEET_LOG);
        return tags;
    }

    @Override
    public CampaignFleetAPI getMapLocation(SectorMapAPI map) {
        return fleet != null && fleet.isAlive() ? fleet : null;
    }

    @Override
    public float getTimeRemainingFraction() {
        if (isEnding() || isEnded()) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, 1f - elapsedDays / ACTIVITY_DAYS));
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        info.addPara("Fleet ID: " + getFleetId(), 3f);
        info.addPara("Location: " + getLocationText(), 3f);
        info.addPara(getStatusText(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(getStatusText(), 0f);
        info.addPara("Fleet ID: " + getFleetId(), 3f);
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI description = panel.createUIElement(width, height, true);
        description.addPara(getName(), 0f);
        description.addPara("This Intel follows a marked campaign fleet and updates as its location changes.", 10f);
        description.addPara("Fleet ID: " + getFleetId(), 3f);
        description.addPara("Location: " + getLocationText(), 3f);
        description.addPara(getStatusText(), 3f);
        panel.addUIElement(description).inTL(0, 0);
    }

    private String getFleetId() {
        return fleet == null ? "<none>" : fleet.getId();
    }

    private String getLocationText() {
        LocationAPI location = fleet == null ? null : fleet.getContainingLocation();
        return location == null ? "unknown" : location.getId();
    }

    private String getStatusText() {
        if (isEnded()) {
            return "Activity ended.";
        }
        if (isEnding()) {
            return "Activity ended; removing this entry after the delay.";
        }
        return "Remaining days: " + Math.max(0, Math.round(ACTIVITY_DAYS - elapsedDays));
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        Global.getLogger(GearInFleetActivityIntel.class).info("GearIn fleet activity is ending: " + getFleetId());
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getLogger(GearInFleetActivityIntel.class).info("GearIn fleet activity ended: " + getFleetId());
    }
}
