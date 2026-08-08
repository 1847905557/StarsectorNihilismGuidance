package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponGroupSpec;

/**
 * 角色：武器组生成。演示空装配装武器但无武器组时的
 * hasUnassignedWeapons() == true，以及 autoGenerateWeaponGroups() 按 AI hints / 弧线 /
 * 尺寸重新聚类补组 (同型武器必合并为一组，最多 7 组)。
 *
 * 注册入口：GearInVariantLauncher.register()(跑一次即停)。
 */
public class GearInWeaponGroupBuilder implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInWeaponGroupBuilder.class);

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
            LOG.error("GearIn weapon group builder failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // 1) 空装配 + 武器但无武器组：hasUnassignedWeapons() == true，autoGenerateWeaponGroups() 补组
        ShipVariantAPI fromEmpty = Global.getSettings().getVariant("brawler_Hull").clone();
        fromEmpty.setSource(VariantSource.REFIT);
        fromEmpty.addWeapon("WS 003", "railgun");
        fromEmpty.addWeapon("WS 004", "railgun");
        LOG.info(String.format(
                "GearIn from empty: groups=%d hasUnassignedWeapons=%s",
                fromEmpty.getWeaponGroups().size(), fromEmpty.hasUnassignedWeapons()));
        fromEmpty.autoGenerateWeaponGroups();
        StringBuilder groupSummary = new StringBuilder();
        for (WeaponGroupSpec group : fromEmpty.getWeaponGroups()) {
            groupSummary.append(group.getType()).append('[').append(group.getSlots()).append("] ");
        }
        LOG.info("GearIn from empty groups after autoGenerateWeaponGroups: " + groupSummary);
    }
}
