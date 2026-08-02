package data.scripts.gearin.lib;

import java.awt.Color;

/**
 * 设置定义角色：LunaSettings 字段 ID 与代码内默认值，与 data/config/LunaSettings.csv
 * 的 fieldID / defaultValue 列逐字一致（硬规则 1、2）。Header 类型只做 UI 分组，
 * 不参与读取/保存，因此没有对应的 KEY 常量。
 */
public class GearInLunaSettingsDefs {

    /** 本 MOD 的 id（C01-S01 命名契约）：也是 LunaSettings.csv 归属与用户设置文件的键。 */
    public static final String MOD_ID = "gear_in";

    /** LunaLib 的 MOD id（逐字来自其 mod_info.json，大小写敏感）。 */
    public static final String LUNA_LIB_MOD_ID = "lunalib";

    // 设置项 fieldID：与 data/config/LunaSettings.csv 的 fieldID 列逐字一致。
    public static final String KEY_TRAILS = "GearIn_trails_enabled";
    public static final String KEY_MAX_ENTRIES = "GearIn_max_entries";
    public static final String KEY_PULSE_DELAY = "GearIn_pulse_delay";
    public static final String KEY_MARKER_COLOR = "GearIn_marker_color";
    public static final String KEY_LABEL_TEXT = "GearIn_label_text";

    // 代码内默认值：LunaLib 未启用或读取返回 null 时的回退（与 CSV 的 defaultValue 一致）。
    public static final boolean DEFAULT_TRAILS = true;
    public static final int DEFAULT_MAX_ENTRIES = 12;
    public static final float DEFAULT_PULSE_DELAY = 1.5f;
    public static final Color DEFAULT_MARKER_COLOR = new Color(0xf2, 0x2e, 0x6e);
    public static final String DEFAULT_LABEL_TEXT = "GearIn";

    private GearInLunaSettingsDefs() {
        // 工具类：不实例化
    }
}
