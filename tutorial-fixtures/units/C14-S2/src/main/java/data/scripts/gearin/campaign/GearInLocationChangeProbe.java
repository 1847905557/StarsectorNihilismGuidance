package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/** 记录玩家当前地点的切换。 */
public class GearInLocationChangeProbe implements CurrentLocationChangedListener {
    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        Global.getLogger(GearInLocationChangeProbe.class).info(
                "GearIn location changed: from=" + (prev == null ? "none" : prev.getId())
                + " to=" + curr.getId());
    }
}
