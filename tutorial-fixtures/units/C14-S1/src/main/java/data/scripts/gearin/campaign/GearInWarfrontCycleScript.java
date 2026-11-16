package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Foundry 周围的战事时起时息：按生涯天数在市场上挂上、撤下 Warfront 条件。脚本随存档保存。 */
public class GearInWarfrontCycleScript implements EveryFrameScript {

    /** 战事持续的生涯天数。 */
    private static final float WAR_DAYS = 60f;

    /** 战事平息的生涯天数。 */
    private static final float CALM_DAYS = 30f;

    /** 当前阶段还剩多少生涯天，随存档保存。新档开局时 Foundry 正处于战事中。 */
    private float daysLeft = WAR_DAYS;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        daysLeft -= sector.getClock().convertToDays(amount);
        if (daysLeft > 0f) {
            return;
        }
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return;
        }
        if (market.hasCondition(GearInWarfrontCondition.CONDITION_ID)) {
            market.removeCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = CALM_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围的战事暂时平息了。");
        } else {
            market.addCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = WAR_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围再度爆发战事。");
        }
    }
}
