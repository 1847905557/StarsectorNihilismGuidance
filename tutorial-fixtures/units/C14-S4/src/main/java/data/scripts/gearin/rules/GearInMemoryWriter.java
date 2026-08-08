package data.scripts.gearin.rules;

import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 记忆写入与读回脚本 (EveryFrameScript)：读档首个未暂停帧写入全部记忆键并立即
 * 读回验证 (键常量见 GearInMemoryKeys)。probe 键 (expire 0) 的「写入后立即读回」
 * 必须在同一方法内完成 —— 跨脚本同帧执行顺序未定义，独立读回可能错过其存在状态。
 */
public class GearInMemoryWriter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryWriter.class);
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
        sector.set(GearInMemoryKeys.KEY_TAG, "GearInReady");
        sector.set(GearInMemoryKeys.KEY_COUNTER, 1f, 1000f);
        sector.set(GearInMemoryKeys.KEY_PROBE, true, 0f);
        sector.set(GearInMemoryKeys.KEY_REARM, "armed", 1000f);
        sector.expire(GearInMemoryKeys.KEY_REARM, 30f);
        float rearmedExpire = sector.getExpire(GearInMemoryKeys.KEY_REARM);
        sector.set(GearInMemoryKeys.KEY_REARM, "permanent");
        float clearedExpire = sector.getExpire(GearInMemoryKeys.KEY_REARM);
        sector.set(GearInMemoryKeys.KEY_REQUIRED_CHILD, true);
        sector.set(GearInMemoryKeys.KEY_REQUIRED_PARENT, true);
        sector.addRequired(GearInMemoryKeys.KEY_REQUIRED_PARENT, GearInMemoryKeys.KEY_REQUIRED_CHILD);

        LOG.info("GearInMemoryWriter: 记忆键必须 $ 前缀 (Memory.set 对无 $ 键抛 RuntimeException)");
        LOG.info("GearInMemoryWriter: contains(KEY_TAG)="
                + sector.contains(GearInMemoryKeys.KEY_TAG)
                + " getString=" + sector.getString(GearInMemoryKeys.KEY_TAG)
                + " is(key,\"GearInReady\")=" + sector.is(GearInMemoryKeys.KEY_TAG, "GearInReady"));
        LOG.info("GearInMemoryWriter: getFloat(KEY_COUNTER)="
                + sector.getFloat(GearInMemoryKeys.KEY_COUNTER)
                + " is(key,1f)=" + sector.is(GearInMemoryKeys.KEY_COUNTER, 1f)
                + " between(0,2)=" + sector.between(GearInMemoryKeys.KEY_COUNTER, 0f, 2f)
                + " getExpire=" + sector.getExpire(GearInMemoryKeys.KEY_COUNTER) + "(游戏天)");
        LOG.info("GearInMemoryWriter: KEY_PROBE(expire 0)contains="
                + sector.contains(GearInMemoryKeys.KEY_PROBE)
                + " getBoolean=" + sector.getBoolean(GearInMemoryKeys.KEY_PROBE)
                + " getExpire=" + sector.getExpire(GearInMemoryKeys.KEY_PROBE)
                + "；将在下一个未暂停推进帧被移除");
        LOG.info("GearInMemoryWriter: expire(KEY_REARM, 30f) 重设后 getExpire=" + rearmedExpire
                + "；两参 set 清除未决过期后 getExpire=" + clearedExpire
                + "(-1 = 永不过期)");
        LOG.info("GearInMemoryWriter: required 依赖 getRequired(KEY_REQUIRED_PARENT)="
                + sector.getRequired(GearInMemoryKeys.KEY_REQUIRED_PARENT));

        Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_PLAYER, true);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_FLEET, "fleet-local");
        }
        LOG.info("GearInMemoryWriter: sector 内存键 = " + new TreeSet<>(sector.getKeys()));
        done = true;
    }
}
