package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.util.IntervalUtil;

/** 玩家的传感器第一次捕捉到灰烬信标时，在信标自己的 Memory 中记下。 */
public class GearInBeaconDiscoveryScript implements EveryFrameScript {

    /** 信标 Memory：玩家已经发现这座信标。 */
    public static final String READY_KEY = "$GearIn_entity_ready";

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        SectorEntityToken beacon = findBeacon();
        return beacon != null && beacon.getMemoryWithoutUpdate().getBoolean(READY_KEY);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        SectorEntityToken beacon = findBeacon();
        if (beacon == null) {
            return;
        }
        if (!beacon.hasSensorProfile()) {
            beacon.setSensorProfile(1f);
        }
        VisibilityLevel level = beacon.getVisibilityLevelToPlayerFleet();
        if (level != VisibilityLevel.NONE) {
            beacon.getMemoryWithoutUpdate().set(READY_KEY, true);
        }
    }

    private SectorEntityToken findBeacon() {
        return Global.getSector().getEntityById(GearInAshenVergeGenerator.BEACON_ID);
    }
}
