package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 每日/周期脚本模式 B：天数累加器。amount 是上一帧真实秒数，必须经
 * clock.convertToDays(amount) 换算成游戏天 (10 真实秒 = 1 游戏日)；
 * 余数跨帧保留；单帧跨多个游戏日 (加速、新档推进) 时 while 逐日补触发；
 * daysToRun >= 0 时累计满该游戏天数后 isDone() 返回 true，引擎在置位同一次推进中
 * 自动移除；全部字段可随存档序列化 (XStream 存档边界)，读档后继续。
 * 注册入口：onGameLoad 的 persistent data 门内 (REGISTER_MARK 一次性门控，C6-S2 的 CLASS_MARK 模式)。
 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    /** 存档持久化标记：本脚本是否已在该存档注册过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_DayAccumulatorScript";
    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    /** 挂载以来的游戏天数 (精确累加，保留小数)；随存档序列化，读档后继续累计。 */
    private float daysSinceMount = 0f;
    /** 例程周期余数：累计满 tickEveryDays 个游戏天触发一次每日例程。 */
    private float daysAccumulator = 0f;
    /** 已执行的每日例程次数 (跨存档累计)。 */
    private int routineCount = 0;
    /** 演示参数：每多少个游戏天执行一次例程；默认 1(每日一次)。 */
    private int tickEveryDays = 1;
    /** 演示参数：最多运行多少游戏天后自停；< 0 表示常驻不停止。 */
    private int daysToRun = -1;
    /** 完成标记：isDone() 返回 true 后引擎在置位同一次推进中自动移除本脚本。 */
    private boolean done = false;
    /** 修改例程周期；周期至少为 1 个游戏天。 */
    public void setTickEveryDays(int tickEveryDays) {
        this.tickEveryDays = Math.max(1, tickEveryDays);
    }
    /** 修改运行天数上限；改回 -1 恢复常驻。 */
    public void setDaysToRun(int daysToRun) {
        this.daysToRun = daysToRun;
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

        // amount 是上一帧真实秒数；经时钟换算才是本帧的游戏天数 (10 真实秒 = 1 游戏日)。
        float daysThisFrame = clock.convertToDays(amount);
        daysSinceMount += daysThisFrame;
        daysAccumulator += daysThisFrame;

        // while 而非 if：单帧跨多个游戏日时逐日补触发；余数保留在 daysAccumulator 里继续累计。
        while (daysAccumulator >= tickEveryDays) {
            daysAccumulator -= tickEveryDays;
            routineCount++;
            LOG.info(String.format(
                    "GearIn daily: routine #%d at %s | since mount=%.1f game days",
                    routineCount, clock.getShortDate(), daysSinceMount));
        }

        if (daysToRun >= 0 && daysSinceMount >= daysToRun) {
            LOG.info(String.format(
                    "GearIn daily: done, isDone()=true after %.1f game days", daysSinceMount));
            done = true;
        }
    }
}
