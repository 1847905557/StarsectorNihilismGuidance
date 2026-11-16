package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import data.scripts.gearin.GearInModPlugin;

/** 玩家与齿轮工业接触满 30 天后，关系至少提升到友好，并让势力学会雄鹰的设计图。 */
public class GearInContactRewardScript implements EveryFrameScript {

    /** 存档公共数据：接触后倒计时的起点时间戳。 */
    public static final String START_KEY = "GearIn_contact_reward_start";

    /** 存档公共数据：奖励已发放。 */
    public static final String REWARDED_KEY = "GearIn_contact_rewarded";

    private static final float DELAY_DAYS = 30f;
    private static final String LEARNED_HULL = "eagle";

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        return Global.getSector().getPersistentData().containsKey(REWARDED_KEY);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        SectorAPI sector = Global.getSector();
        if (!sector.getMemory().getBoolean(GearInContactScript.CONTACTED_KEY)) {
            return;
        }
        Map<String, Object> data = sector.getPersistentData();
        if (!data.containsKey(START_KEY)) {
            data.put(START_KEY, sector.getClock().getTimestamp());
            return;
        }
        long start = (Long) data.get(START_KEY);
        if (sector.getClock().getElapsedDaysSince(start) < DELAY_DAYS) {
            return;
        }

        FactionAPI gear = sector.getFaction(GearInModPlugin.FACTION_ID);
        gear.ensureAtWorst(sector.getPlayerFaction().getId(), RepLevel.FRIENDLY);
        if (!gear.knowsShip(LEARNED_HULL)) {
            gear.addKnownShip(LEARNED_HULL, true);
        }
        data.put(REWARDED_KEY, true);
    }
}
