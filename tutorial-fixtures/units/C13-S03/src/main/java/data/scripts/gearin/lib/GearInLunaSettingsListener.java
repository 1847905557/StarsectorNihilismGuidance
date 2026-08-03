package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/**
 * 变更通知角色：注册/注销 LunaSettings 监听器，在设置界面保存后按本 MOD 过滤并
 * 重读输出当前生效值。addSettingsListener 不去重，注册前必须用
 * hasSettingsListenerOfClass 幂等查询（重复注册会让同一次保存触发多次回调）。
 *
 * 监听器实例在门控通过之后才创建 —— 类加载路径不触碰 lunalib.**，LunaLib 未启用
 * 时（C12-S01 场景）调用本类不会抛 NoClassDefFoundError，门控纪律见硬规则 7、10。
 */
public class GearInLunaSettingsListener {

    private static final Logger LOG = Global.getLogger(GearInLunaSettingsListener.class);
    private static LunaSettingsListener registered;

    private GearInLunaSettingsListener() {
        // 工具类：不实例化
    }

    /**
     * 注册（幂等）：LunaLib 未启用时跳过并写 warn。注册入口见页面正文的接入片段
     * （GearInModPlugin.onGameLoad 一行）；监听器实例在门控之后才创建。
     */
    public static void registerListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            LOG.warn("GearIn luna settings: LunaLib not enabled, listener not registered");
            return;
        }
        if (registered != null && LunaSettings.hasSettingsListenerOfClass(registered.getClass())) {
            LOG.info("GearIn luna settings: listener already registered");
            return;
        }
        registered = new LunaSettingsListener() {
            @Override
            public void settingsChanged(String modId) {
                if (!GearInLunaSettingsDefs.MOD_ID.equals(modId)) {
                    return; // 其他 MOD 的保存动作不影响本 MOD 的缓存
                }
                LOG.info("GearIn luna settings: reloaded");
                logCurrentValues();
            }
        };
        LunaSettings.addSettingsListener(registered);
        LOG.info("GearIn luna settings: listener registered");
    }

    /** 取消注册（测试清理或卸载场景）。 */
    public static void unregisterListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        if (registered != null) {
            LunaSettings.removeSettingsListener(registered);
            registered = null;
        }
    }

    /** 输出当前生效值：用于对照设置界面与用户设置文件（进档时可调用一次）。 */
    public static void logCurrentValues() {
        LOG.info("GearIn luna settings: trails=" + GearInLunaSettingsReader.isTrailsEnabled()
                + " maxEntries=" + GearInLunaSettingsReader.getMaxEntries()
                + " pulseDelay=" + GearInLunaSettingsReader.getPulseDelay()
                + " markerColor=" + toHex(GearInLunaSettingsReader.getMarkerColor())
                + " labelText=" + GearInLunaSettingsReader.getLabelText());
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
