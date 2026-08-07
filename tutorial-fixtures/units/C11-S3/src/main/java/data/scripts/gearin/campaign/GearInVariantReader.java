package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/**
 * 角色：注册表读取。演示 SettingsAPI.getVariant 的注册表读取与来源判定 ——
 * 股票变体 brawler_Assault(STOCK)的身份 / 装配 / OP 摘要、自动空装配 brawler_Hull
 * (HULL 来源，source == HULL、isEmptyHullVariant() == true)与注册表变体总数。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停：首次 advance() 后 isDone() 置 true)。
 */
public class GearInVariantReader implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantReader.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
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
        if (done) {
            return;
        }
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn variant reader failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();

        // 1) 注册表读取：缺失 id 抛 RuntimeException "Ship hull variant [...] not found!"
        //    (变体注册表的 getVariant，注册表未命中时还会回退查询存档变体数据)
        ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");
        GearInVariantLog.logVariant("stock", stock, stats);

        // 2) 每个注册 hull 自动生成 <hullId>_Hull 空装配，source == HULL
        ShipVariantAPI hullVariant = Global.getSettings().getVariant("brawler_Hull");
        LOG.info(String.format(
                "GearIn hull empty variant: id=%s source=%s isStockVariant=%s isEmptyHullVariant=%s displayName=%s",
                hullVariant.getHullVariantId(), hullVariant.getSource(),
                hullVariant.isStockVariant(), hullVariant.isEmptyHullVariant(),
                hullVariant.getDisplayName()));

        // 3) 注册表统计：全部已注册变体 id(数据变体、<hullId>_Hull 空装配与任务变体)
        LOG.info("GearIn registered variant count: " + Global.getSettings().getAllVariantIds().size());
    }
}
