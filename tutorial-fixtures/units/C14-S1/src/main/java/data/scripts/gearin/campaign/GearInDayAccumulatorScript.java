package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 按游戏天累计的周期脚本：每 1 个游戏日执行一次，累计 30 个游戏日后结束。
 * amount 是上一帧真实秒数，必须经 clock.convertToDays(amount) 换算；
 * daysAccumulator 保留周期余量，while 负责补齐单帧跨越的多个周期。
 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    private float daysSinceMount = 0f;
    private float daysAccumulator = 0f;
    private int routineCount = 0;
    private final float tickEveryDays = 1.0f;
    private final float daysToRun = 30.0f;
    private boolean done = false;

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
        float daysThisFrame = clock.convertToDays(amount);
        daysSinceMount += daysThisFrame;
        daysAccumulator += daysThisFrame;

        while (daysAccumulator >= tickEveryDays) {
            daysAccumulator -= tickEveryDays;
            routineCount++;
            LOG.info(String.format(
                    "GearIn daily: routine #%d at %s | since mount=%.1f game days",
                    routineCount, clock.getShortDate(), daysSinceMount));
        }

        if (daysSinceMount >= daysToRun) {
            LOG.info(String.format(
                    "GearIn daily: done, isDone()=true after %.1f game days", daysSinceMount));
            done = true;
        }
    }
}
