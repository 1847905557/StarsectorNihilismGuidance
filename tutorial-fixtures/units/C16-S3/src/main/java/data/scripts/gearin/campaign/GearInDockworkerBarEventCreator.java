package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/** 码头工人酒吧事件的创建器：交给原版的酒吧事件管理器调度。 */
public class GearInDockworkerBarEventCreator extends BaseBarEventCreator {

    @Override
    public PortsideBarEvent createBarEvent() {
        return new GearInDockworkerBarEvent();
    }
}
