package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

/**
 * Logger 用法：进程级缓存一份日志器，输出到 starsector.log。
 *
 * 静态工具，可从任何已注册的脚本/插件调用（C06-S03 的四阶段可用性只约束
 * 生涯/战斗对象，logger 全程非 null）。
 */
public class GearInServiceLogger {

    /** 日志器：进程级缓存一份，输出到 starsector.log。 */
    private static final Logger LOG = Global.getLogger(GearInServiceLogger.class);

    private GearInServiceLogger() {
        // 工具类：不实例化
    }

    /** info 级别：记录 Global 衍生服务可用性，供启动日志核对。 */
    public static void logServiceState() {
        LOG.info("GearIn services: settings=" + (Global.getSettings() != null)
                + " soundPlayer=" + (Global.getSoundPlayer() != null));
    }
}
