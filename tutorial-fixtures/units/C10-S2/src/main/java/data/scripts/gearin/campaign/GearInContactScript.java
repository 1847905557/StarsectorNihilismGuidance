package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** 玩家首次靠近 Nipher Prime 时，记录与齿轮工业的接触。 */
public class GearInContactScript implements EveryFrameScript {

    /** 全局 Memory：玩家是否已与齿轮工业接触，永不过期。 */
    public static final String CONTACTED_KEY = "$GearIn_contacted";

    /** 全局 Memory：最近接触过，30 天后自动消失。 */
    public static final String RECENT_KEY = "$GearIn_contact_recent";

    /** 市场 Memory：接触发生时的时间戳。 */
    public static final String TIMESTAMP_KEY = "$GearIn_contact_timestamp";

    private static final String MARKET_ID = "GearIn_planet1";

    /** 判定为接触的距离，从行星表面起算。 */
    private static final float CONTACT_DISTANCE = 1500f;

    private static final float RECENT_DAYS = 30f;

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(CONTACTED_KEY);
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
        MarketAPI market = sector.getEconomy().getMarket(MARKET_ID);
        if (market == null || market.getPrimaryEntity() == null) {
            return;
        }
        SectorEntityToken planet = market.getPrimaryEntity();
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() != planet.getContainingLocation()) {
            return;
        }
        if (Misc.getDistance(fleet, planet) > planet.getRadius() + CONTACT_DISTANCE) {
            return;
        }

        sector.getMemoryWithoutUpdate().set(CONTACTED_KEY, true);
        sector.getMemoryWithoutUpdate().set(RECENT_KEY, true, RECENT_DAYS);
        market.getMemoryWithoutUpdate().set(TIMESTAMP_KEY, sector.getClock().getTimestamp());
        Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
    }
}
