package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 完整最小例：监听器生命周期脚本（EveryFrameScript 角色）。
 *
 * 与 GearInCampaignListener（监听器角色）配对：原组合类拆分出的脚本半身。
 * 注册入口：onGameLoad 门控块内与监听器一同注册（sector.addScript(this)）。
 * 职责：按游戏天累计挂载时长，daysToLive >= 0 且到期时显式
 * sector.removeListener(listener) 并自停——isDone() 只清理脚本列表，
 * 监听器注册表必须单独移除，否则事件继续派发且实例随存档序列化（硬规则）。
 */
public class GearInListenerLifecycleScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInListenerLifecycleScript.class);

    /** 受管监听器：到期时由本脚本显式移除。 */
    private GearInCampaignListener listener;

    /** 挂载以来的游戏天数（精确累加，保留小数）；随存档序列化，读档后继续累计。 */
    private float daysActive = 0f;

    /** 演示参数：挂载满多少游戏天后自停并移除监听器；< 0 表示常驻不停止。 */
    private int daysToLive = -1;

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

    public GearInListenerLifecycleScript(GearInCampaignListener listener) {
        this.listener = listener;
    }

    /** 修改自停天数；改回 -1 恢复常驻。 */
    public void setDaysToLive(int daysToLive) {
        this.daysToLive = daysToLive;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        // amount 是上一帧真实秒数；经时钟换算才是本帧的游戏天数（10 真实秒 = 1 游戏日）。
        daysActive += clock.convertToDays(amount);

        if (daysToLive >= 0 && daysActive >= daysToLive) {
            done = true;
            // 硬规则：脚本结束不等于监听器移除。isDone() 只清理脚本列表，
            // 监听器注册表必须显式 removeListener，否则事件继续派发。
            sector.removeListener(listener);
            LOG.info(String.format(
                    "GearIn listener: removed itself after %.1f game days (isDone()=true)", daysActive));
        }
    }
}
