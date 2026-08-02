package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 A：日历跨日检测。dateKey = cycle * 10000 + month * 100 + day，
 * 把日历日期折叠为整数键，与上次比较，变化即"新的一天"；每个游戏日恰好触发一次
 * （首帧只初始化不打印）。单帧跨多个游戏日（加速、新档推进）时一帧只报一次——
 * 日历事件一帧至多一次是正常行为，需要逐日补触发的逻辑请改用天数累加器
 * （本单元 GearInDayAccumulatorScript）。runWhilePaused() 返回 false；字段只有
 * int，可随存档序列化（XStream 存档边界），脚本随存档保存、读档后继续。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），注册前用 persistent data
 * 键 REGISTER_MARK 做一次性门控（C06-S02 的 CLASS_MARK 模式），键与调用点片段逐字一致。
 */
public class GearInCalendarDayScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CalendarDayScript";

    private static final Logger LOG = Global.getLogger(GearInCalendarDayScript.class);

    /** 跨日判定键（cycle * 10000 + month * 100 + day）；-1 表示尚未初始化。 */
    private int lastDateKey = -1;

    @Override
    public boolean isDone() {
        return false; // 常驻脚本：日历事件逐日触发，永不结束
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        // 本模式只看日历日期，不直接用 amount（时间换算与逐日补触发见 GearInDayAccumulatorScript）。
        int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
        if (dateKey != lastDateKey) {
            if (lastDateKey != -1) {
                LOG.info(String.format("GearIn calendar: new day %s", clock.getShortDate()));
            }
            lastDateKey = dateKey;
        }
    }
}
