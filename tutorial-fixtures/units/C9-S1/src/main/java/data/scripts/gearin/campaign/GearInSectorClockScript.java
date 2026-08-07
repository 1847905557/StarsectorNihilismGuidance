package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 完整最小例：SectorAPI 注册的生涯每帧脚本（EveryFrameScript）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），注册前用 hasScript() 查重，
 * 并用 sector persistent data 键 REGISTER_MARK 做一次性门控（C6-S2 的 CLASS_MARK 模式）。
 *
 * 行为：每个游戏日跨日时打印一行日志（当前日期、本帧折合的游戏天数、挂载以来经过的游戏天数）；
 * 默认 runWhilePaused() 返回 false：引擎暂停时脚本不推进、生涯时钟也不前进；
 * daysToRun >= 0 时挂载满该天数后 isDone() 返回 true，引擎在下一帧自动从 scripts 列表移除本脚本。
 */
public class GearInSectorClockScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_SectorClockScript";

    private static final Logger LOG = Global.getLogger(GearInSectorClockScript.class);

    /** 跨日判定键（cycle * 10000 + month * 100 + day）；-1 表示尚未初始化。 */
    private int lastDateKey = -1;

    /** 脚本第一次 advance() 时的时钟时间戳；随存档序列化，读档后继续累计。 */
    private long startTimestamp = 0L;

    /** 演示参数：最多运行多少游戏天后自停；< 0 表示常驻不停止。 */
    private int daysToRun = -1;

    /** 演示参数：是否在引擎暂停时仍推进；默认 false（暂停即停表）。 */
    private boolean runWhilePaused = false;

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

    public GearInSectorClockScript() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
    }

    /** 修改运行天数上限；改回 -1 恢复常驻。 */
    public void setDaysToRun(int daysToRun) {
        this.daysToRun = daysToRun;
    }

    /** 修改暂停语义；改为 true 后暂停期间 advance() 仍会被调用。 */
    public void setRunWhilePaused(boolean runWhilePaused) {
        this.runWhilePaused = runWhilePaused;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return runWhilePaused;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        CampaignClockAPI clock = sector.getClock();

        if (startTimestamp == 0L) {
            startTimestamp = clock.getTimestamp();
            LOG.info("GearIn clock script mounted at " + clock.getDateString());
        }

        int dateKey = clock.getCycle() * 10000 + clock.getMonth() * 100 + clock.getDay();
        if (dateKey != lastDateKey) {
            lastDateKey = dateKey;
            LOG.info(String.format(
                    "GearIn clock day change: %s (short=%s) | this frame=%.4f game days | since mount=%.1f game days",
                    clock.getDateString(),
                    clock.getShortDate(),
                    clock.convertToDays(amount),
                    clock.getElapsedDaysSince(startTimestamp)));
        }

        if (daysToRun >= 0 && clock.getElapsedDaysSince(startTimestamp) >= daysToRun) {
            LOG.info("GearIn clock script done: " + daysToRun + " game days elapsed, isDone()=true");
            done = true;
        }
    }
}
