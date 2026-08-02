package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：克隆改装。演示 clone() 的深拷贝语义——修改克隆（换武器、改幅能、改船插、
 * 改来源、重新生成武器组）不影响注册表原变体；并给出 stock 与 refit 的 OP 对照
 * （computeOPCost = 武器 OP + 船插 OP + vents + caps；getUnusedOP = 船体 OP - 已用 OP）。
 *
 * 注册入口：GearInVariantLauncher.register()（跑一次即停）。
 */
public class GearInVariantRefitter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantRefitter.class);

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
            LOG.error("GearIn variant refitter failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();
        ShipVariantAPI stock = Global.getSettings().getVariant("brawler_Assault");

        // 1) 克隆后修改装配：clone() 深拷贝全部集合（武器表、武器组逐组、船插、联队、模块变体；船体 spec 共享引用），修改克隆不影响原变体
        ShipVariantAPI refit = stock.clone();
        refit.clearSlot("WS 003");                    // 卸下旧武器
        refit.addWeapon("WS 003", "railgun");         // 装上小实弹（brawler 的 WS 003 是 SMALL UNIVERSAL 硬点）
        refit.setNumFluxVents(14);
        refit.setNumFluxCapacitors(10);
        refit.addMod("hardenedsubsystems");
        refit.removeMod("reinforcedhull");
        refit.setSource(VariantSource.REFIT);
        refit.setVariantDisplayName("GearIn Refit");
        refit.autoGenerateWeaponGroups();             // 按 AI hints/弧线/尺寸重新聚类，最多 7 组
        GearInVariantLog.logVariant("refit clone", refit, stats);

        // 2) 克隆隔离：改克隆后注册表原变体不变
        LOG.info(String.format(
                "GearIn clone isolation: stock WS 003=%s vents=%d caps=%d hasReinforcedHull=%s",
                stock.getWeaponId("WS 003"), stock.getNumFluxVents(), stock.getNumFluxCapacitors(),
                stock.hasHullMod("reinforcedhull")));

        // 3) OP 对照：computeOPCost = 武器 OP + 船插 OP + vents + caps；getUnusedOP = 船体 OP - 已用 OP
        int opStock = stock.computeOPCost(stats);
        int opRefit = refit.computeOPCost(stats);
        int hullOp = stock.getHullSpec().getOrdnancePoints(stats);
        LOG.info(String.format(
                "GearIn OP: stock=%d refit=%d hullOP=%d unusedRefit=%d",
                opStock, opRefit, hullOp, refit.getUnusedOP(stats)));
    }
}
