package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.LazyLib;
import org.lazywizard.lazylib.ModUtils;

import com.fs.starfarer.api.Global;

/**
 * 通用工具族的用法片段：LazyLib / ModUtils 的最小消费面。
 *
 * 以库自身常量 LazyLib.MOD_ID 做启用检测（不手写 id 字符串）；
 * isClassPresent 表达 classpath 存在性、isModEnabled 表达启用状态，
 * 两条信息分别回答"LazyLib 开着没"与"这个类能调没"。
 */
public final class GearInLazyRuntimeProbe {

    private static final Logger LOG = Global.getLogger(GearInLazyRuntimeProbe.class);

    private GearInLazyRuntimeProbe() {
        // 工具类：不实例化
    }

    /** 输出库版本、支持的游戏版本与运行时检测结果一行日志。 */
    public static void logInit(int battleCount) {
        LOG.info("GearIn lazy init: version=" + LazyLib.getVersion()
                + " supported=" + LazyLib.getSupportedGameVersion()
                + " modEnabled=" + ModUtils.isModEnabled(LazyLib.MOD_ID)
                + " classPresent=" + ModUtils.isClassPresent("org.lazywizard.lazylib.combat.CombatUtils")
                + " battleCount=" + battleCount);
    }
}
