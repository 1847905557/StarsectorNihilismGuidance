package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：模块变体覆盖。演示 stationModules / getModuleVariant / setModuleVariant——
 * setModuleVariant 只接受非 STOCK 来源的覆盖（STOCK/null 都会被当作"移除覆盖"处理）。
 *
 * 注册入口：GearInVariantLauncher.register()（跑一次即停）。
 */
public class GearInModuleOverrider implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInModuleOverrider.class);

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
            LOG.error("GearIn module overrider failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // stationModules 是 slotId -> variantId 映射；getModuleVariant 按槽取模块变体，
        // setModuleVariant 只接受非 STOCK 来源的覆盖（STOCK/null 都会被当作"移除覆盖"处理）
        ShipVariantAPI station = Global.getSettings().getVariant("station1_Standard");
        ShipVariantAPI moduleStock = station.getModuleVariant("WS 009");
        ShipVariantAPI stationCopy = station.clone();
        ShipVariantAPI moduleCopy = moduleStock.clone();
        moduleCopy.setSource(VariantSource.REFIT);
        stationCopy.setModuleVariant("WS 009", moduleCopy);
        LOG.info(String.format(
                "GearIn modules: stationModules=%s module WS 009=%s override=%s overrideSource=%s",
                station.getStationModules(),
                moduleStock.getHullVariantId(),
                stationCopy.getModuleVariant("WS 009").getHullVariantId(),
                stationCopy.getModuleVariant("WS 009").getSource()));
    }
}
