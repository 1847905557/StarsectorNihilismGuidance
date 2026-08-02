package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 过期与依赖清理验证脚本（EveryFrameScript）：写入脚本落键（KEY_TAG 存在）且
 * expire 0 的 probe 键已被 Memory.advance 移除后，记录移除结果，再 unset 一个
 * required 子键验证父键被递归移除。
 *
 * 时序对齐：probe 每次读档都被写入脚本重新写入并移除，故「tag 存在 && probe
 * 不存在」是稳定的验证时机（同帧执行顺序未定义，用键状态对齐）。
 */
public class GearInMemoryExpiryVerifier implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryExpiryVerifier.class);

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)
                || sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return; // 等写入脚本跑完，且 probe 已被下一个推进帧的 Memory.advance 移除
        }
        LOG.info("GearInMemoryExpiryVerifier: 下一推进帧后 contains(KEY_PROBE)="
                + sector.contains(GearInMemoryKeys.KEY_PROBE)
                + " getString=" + sector.getString(GearInMemoryKeys.KEY_PROBE)
                + "（expire 0 的键已被 Memory.advance 移除）");
        sector.unset(GearInMemoryKeys.KEY_REQUIRED_CHILD);
        LOG.info("GearInMemoryExpiryVerifier: unset(KEY_REQUIRED_CHILD) 后 contains(child)="
                + sector.contains(GearInMemoryKeys.KEY_REQUIRED_CHILD)
                + " contains(parent)=" + sector.contains(GearInMemoryKeys.KEY_REQUIRED_PARENT)
                + "（required 依赖：最后一个 required 键消失时父键被递归移除）");
        done = true;
    }
}
