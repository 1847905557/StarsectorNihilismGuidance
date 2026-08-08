package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * 市场巡检脚本 (EveryFrameScript 角色)：挂载后第一次 advance() 执行一次市场/子市场/
 * 经济对象巡检序列并自停 (isDone()=true，引擎下一帧自动移除本脚本)。注册入口：生涯回调
 * onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控
 * (沿用既有模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 */
public class GearInMarketInspector implements EveryFrameScript {

    /** 存档持久化标记：本巡检是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_MarketInspector";

    private static final Logger LOG = Global.getLogger(GearInMarketInspector.class);

    /** 巡检目标市场 id；默认 "jangala"(原版核心世界，必存在)。 */
    private String targetMarketId = "jangala";

    /** 巡检是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInMarketInspector() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    /** 修改巡检目标市场；改回 null 时回退到 "第一个有开放市场的市场"。 */
    public void setTargetMarketId(String targetMarketId) {
        this.targetMarketId = targetMarketId;
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runInspection();
    }

    private void runInspection() {
        EconomyAPI economy = Global.getSector().getEconomy();

        // --- 经济基线 ---
        LOG.info(String.format(
                "GearIn market inspection start: markets=%d commodities=%d",
                economy.getNumMarkets(), economy.getAllCommodityIds().size()));

        MarketAPI market = new GearInMarketPicker(targetMarketId).pick(economy);
        if (market == null) {
            LOG.info("GearIn market inspection abort: no open-market market found");
            return;
        }

        GearInMarketStructureLog.log(market, economy);
        GearInSubmarketLog.log(market);
        GearInCommodityLog.log(market);
        GearInTariffProbe.log(market);

        economy.addUpdateListener(new GearInEconomyTickLog());
        LOG.info("GearIn market inspection registered economy update listener (self-expires)");
        LOG.info("GearIn market inspection done");
    }
}
