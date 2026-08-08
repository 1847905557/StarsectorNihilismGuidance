package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;

public final class GearInCompatBridge {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String LUNA_MOD_ID = "lunalib";
    public static final String SETTINGS_OWNER_MOD_ID = "gear_in";
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private static boolean nexMissingReported;
    private static boolean lunaMissingReported;

    private GearInCompatBridge() {
    }

    public static boolean isNexerelinEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    public static boolean getFullGraphicEffect(boolean fallback) {
        if (!isLunaLibEnabled()) {
            if (!lunaMissingReported) {
                lunaMissingReported = true;
                Global.getLogger(GearInCompatBridge.class).info(
                        "GearIn compat: LunaLib unavailable, fallback=" + fallback);
            }
            return fallback;
        }
        return LunaAdapter.getFullGraphicEffect(fallback);
    }

    public static void reportNexerelinFallback() {
        if (!isNexerelinEnabled() && !nexMissingReported) {
            nexMissingReported = true;
            Global.getLogger(GearInCompatBridge.class).info(
                    "GearIn compat: Nexerelin unavailable, strategic integration disabled");
        }
    }

    private static final class LunaAdapter {
        private static boolean getFullGraphicEffect(boolean fallback) {
            Boolean value = lunalib.lunaSettings.LunaSettings.getBoolean(
                    SETTINGS_OWNER_MOD_ID, FULL_GRAPHIC_FIELD);
            return value == null ? fallback : value;
        }
    }
}
