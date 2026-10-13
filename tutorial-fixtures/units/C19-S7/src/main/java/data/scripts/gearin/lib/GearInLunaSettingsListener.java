package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/** 登记设置变更通知，并记录本 MOD 当前生效值。 */
public class GearInLunaSettingsListener {
    private static final Logger LOG = Global.getLogger(GearInLunaSettingsListener.class);

    private GearInLunaSettingsListener() {
    }

    public static void registerListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        LunaAdapter.register();
    }

    public static void unregisterListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        LunaAdapter.unregister();
    }

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

    private static final class LunaAdapter {
        private static LunaSettingsListener registered;

        private static void register() {
            if (registered != null && LunaSettings.hasSettingsListenerOfClass(registered.getClass())) {
                return;
            }
            registered = new LunaSettingsListener() {
                @Override
                public void settingsChanged(String modId) {
                    if (!GearInLunaSettingsDefs.MOD_ID.equals(modId)) {
                        return;
                    }
                    LOG.info("GearIn luna settings: reloaded");
                    logCurrentValues();
                }
            };
            LunaSettings.addSettingsListener(registered);
            LOG.info("GearIn luna settings: listener registered");
        }

        private static void unregister() {
            if (registered != null) {
                LunaSettings.removeSettingsListener(registered);
                registered = null;
            }
        }
    }
}
