package data.scripts.gearin.lib;

import java.awt.Color;

import com.fs.starfarer.api.Global;

/** 按启用状态读取设置，缺失值使用已定义的缺省。 */
public class GearInLunaSettingsReader {

    private GearInLunaSettingsReader() {
    }

    /** 查询 LunaLib 的启用状态。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(GearInLunaSettingsDefs.LUNA_LIB_MOD_ID);
    }

    /** 轨迹特效开关：Boolean 设置，缺省 true。 */
    public static boolean isTrailsEnabled() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_TRAILS;
        }
        Boolean value = LunaAdapter.getBoolean(GearInLunaSettingsDefs.KEY_TRAILS);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_TRAILS;
    }

    /** 面板最大条目数：Int 设置，缺省 12。 */
    public static int getMaxEntries() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
        }
        Integer value = LunaAdapter.getInt(GearInLunaSettingsDefs.KEY_MAX_ENTRIES);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
    }

    /** 脉冲间隔 (秒)：CSV 里是 Double 类型，读取时用 getFloat (从保存的 double 转换)。 */
    public static float getPulseDelay() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
        }
        Float value = LunaAdapter.getFloat(GearInLunaSettingsDefs.KEY_PULSE_DELAY);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
    }

    /** 标记颜色：Color 设置，保存/读取十六进制字符串 (# 前缀可省略)。 */
    public static Color getMarkerColor() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
        }
        Color value = LunaAdapter.getColor(GearInLunaSettingsDefs.KEY_MARKER_COLOR);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
    }

    /** 标签文本：String 设置，按原样读取。 */
    public static String getLabelText() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
        }
        String value = LunaAdapter.getString(GearInLunaSettingsDefs.KEY_LABEL_TEXT);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
    }

    private static final class LunaAdapter {
        private static Boolean getBoolean(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getBoolean(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Integer getInt(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getInt(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Float getFloat(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getFloat(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Color getColor(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getColor(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static String getString(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getString(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }
    }
}
