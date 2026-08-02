package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;

/**
 * 经济更新监听器（EconomyUpdateListener 角色）：记录下一个经济步的第一次
 * commodityUpdated 与整轮 economyUpdated，之后 isEconomyListenerExpired() 返回 true，
 * 引擎在下一个经济步自动从监听器列表移除。
 *
 * 只持有两个 boolean，无外部引用，随存档序列化无副作用。注册入口：必须经
 * EconomyAPI.addUpdateListener() 注册，不能加入 SectorAPI.getListenerManager()。
 */
public class GearInEconomyTickLog implements EconomyUpdateListener {

    private static final Logger LOG = Global.getLogger(GearInEconomyTickLog.class);

    private boolean commodityFired = false;
    private boolean economyFired = false;

    @Override
    public void commodityUpdated(String commodityId) {
        if (!commodityFired) {
            commodityFired = true;
            LOG.info("GearIn economy listener first commodityUpdated: " + commodityId);
        }
    }

    @Override
    public void economyUpdated() {
        if (!economyFired) {
            economyFired = true;
            LOG.info("GearIn economy listener economyUpdated (expiring)");
        }
    }

    @Override
    public boolean isEconomyListenerExpired() {
        return economyFired;
    }
}
