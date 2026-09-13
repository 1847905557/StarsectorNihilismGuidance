package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

public class GearInBarEventCreator extends BaseBarEventCreator {

    @Override
    public PortsideBarEvent createBarEvent() {
        return new GearInBarEvent();
    }

    @Override
    public float getBarEventFrequencyWeight() {
        return 10f;
    }

    @Override
    public float getBarEventActiveDuration() {
        return 30f;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 30f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 60f;
    }
}
