package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/**
 * 完整最小例：经 ListenerManagerAPI 注册的类型化监听器（typed listener）。
 *
 * 与 GearInCampaignListener（Sector 级 CampaignEventListener 通道）不同，本类走的是
 * 第二条通道：SectorAPI.getListenerManager().addListener(listener, isTransient) 注册、
 * removeListener(this) 移除，事件由 ListenerUtil 按接口类型派发
 * （本类对应 CurrentLocationChangedListener，派发点在生涯引擎的 advance
 * 检测到当前地点变化时）。
 *
 * 注册入口：onGameLoad（调用点片段见页面正文），用 transient=true 注册——
 * transient 监听器不随存档保存（ListenerManagerAPI 的 transientListeners 仓库读档时
 * 重建为空），因此读档后必须重新注册；调用点用 hasListenerOfClass 判空
 * 再补注册即可覆盖旧档。
 *
 * 演示点：
 * 1. reportCurrentLocationChanged(prev, curr)：玩家当前地点变化（进出超空间、进入星系）时触发；
 * 2. 自移除：触发满 reportsLeft 次后 removeListener(this) 并打日志——监听器在不再需要时
 *    必须在合适时机移除，否则事件持续派发（硬规则）；
 * 3. 全部字段可随存档序列化；本类字段不涉及瞬态句柄。
 */
public class GearInLocationChangeListener implements CurrentLocationChangedListener {

    private static final Logger LOG = Global.getLogger(GearInLocationChangeListener.class);

    /** 演示参数：最多触发多少次后自移除。 */
    private int reportsLeft = 5;

    public GearInLocationChangeListener() {
        // 空构造：注册由调用方执行（onGameLoad，见页面调用点片段）
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
