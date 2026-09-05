package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/**
 * 完整最小例：经 ListenerManagerAPI 注册的类型化监听器 (typed listener)。
 * SectorAPI.getListenerManager().addListener(listener, isTransient) 注册、removeListener
 * 移除，事件按接口类型派发 (本类为 CurrentLocationChangedListener，玩家当前地点
 * 变化时触发)。注册入口：onGameLoad，用 transient=true 注册 —— transient 监听器
 * 不随存档保存，读档后必须用 hasListenerOfClass 判空后补注册；触发满 reportsLeft
 * 次后自移除，监听器不再需要时必须在合适时机移除，否则事件持续派发。
 */
public class GearInLocationChangeListener implements CurrentLocationChangedListener {

    private static final Logger LOG = Global.getLogger(GearInLocationChangeListener.class);

    /** 演示参数：最多触发多少次后自移除。 */
    private int reportsLeft = 5;

    public GearInLocationChangeListener() {
        // 空构造：注册由调用方执行 (onGameLoad)
    }

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        LOG.info(String.format("GearIn listener: current location changed: %s -> %s",
                locationName(prev), locationName(curr)));

        reportsLeft--;
        if (reportsLeft <= 0) {
            Global.getSector().getListenerManager().removeListener(this);
            LOG.info("GearIn listener: location listener removed itself after 5 reports");
        }
    }

    private static String locationName(LocationAPI location) {
        return location == null ? "null" : location.getName();
    }
}
