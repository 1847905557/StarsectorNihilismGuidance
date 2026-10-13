package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/** 每个生涯日记录一次，累计三十日后结束并保留周期余量。 */
public class GearInDayAccumulatorScript implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInDayAccumulatorScript.class);

    private double daysSinceMount;
    private double daysAccumulator;
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
        double daysThisFrame = Math.min(clock.convertToDays(amount), daysToRun - daysSinceMount);
        daysSinceMount += daysThisFrame;
        daysAccumulator = daysSinceMount - routineCount * tickEveryDays;

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
