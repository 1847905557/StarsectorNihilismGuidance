package data.scripts.gearin.rules;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * PersistentData 跨存档计数脚本（EveryFrameScript）：每档一次累加运行计数并记录
 * 上次值 —— 直接可观察 PersistentData 随存档保存与恢复（新档 run 1、读档 run N
 * 递增）。故意不加一次性 REGISTER_MARK 门控：每次读档都重跑才能看到递增；
 * 注册项使用各自的 persistent data 键（C06-S02 纪律）。
 */
public class GearInPersistentDataCounter implements EveryFrameScript {

    /** PersistentData 键：与记忆键相反，不需要 $ 前缀（随存档序列化的普通键）。 */
    public static final String PERSIST_KEY_RUNS = "GearIn_memory_runs";

    private static final Logger LOG = Global.getLogger(GearInPersistentDataCounter.class);

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
        Map<String, Object> persistent = Global.getSector().getPersistentData();
        Object previous = persistent.get(PERSIST_KEY_RUNS);
        int runs = (previous instanceof Number n) ? n.intValue() : 0;
        runs++;
        persistent.put(PERSIST_KEY_RUNS, runs);
        LOG.info("GearInPersistentDataCounter: PersistentData 运行计数 run " + runs
                + "（新档首次 = 1；读档恢复上次值 = " + previous + "）");
        done = true;
    }
}
