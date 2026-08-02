package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * 软依赖适配器（兼容层）：把对可选依赖 LunaLib 的全部直接引用收敛在本类，
 * 工程里只有这一处出现 lunalib.* 类；其余代码只消费本类的静态方法。
 *
 * 调用链：isModEnabled 判定 MOD 启用状态，Class.forName 判定 API 类是否在
 * 脚本类路径上，两者都通过才进入持有 LunaSettings 直接引用的分支；LunaLib
 * 缺失或设置字段缺失时回落到调用方传入的默认值（降级边界）。
 */
public class GearInCompatBridge {

    /** LunaLib 的 MOD id，与 Starsector\mods\Lunalib\mod_info.json 的 id 字段逐字一致。 */
    public static final String LUNA_MOD_ID = "lunalib";

    /** 可选 API 类全名（类存在性检测用，不直接 import 也能探测）。 */
    public static final String LUNA_SETTINGS_CLASS = "lunalib.lunaSettings.LunaSettings";

    /** 本 MOD 在 data/config/LunaSettings.csv 中登记的设置项 fieldID。 */
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private GearInCompatBridge() {
    }

    /** 判据一：LunaLib 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    /** 判据二：LunaSettings 类是否在脚本类路径上；缺类返回 false，不抛异常。 */
    public static boolean isLunaSettingsClassPresent() {
        try {
            Class.forName(LUNA_SETTINGS_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 组合判据：MOD 已启用且 API 类在类路径上，此时才能安全调用 LunaSettings。 */
    public static boolean isLunaSettingsUsable() {
        return isLunaLibEnabled() && isLunaSettingsClassPresent();
    }

    /**
     * 适配入口（降级边界）：LunaLib 可用时读取 GearIn_fullGraphicEffect 设置，
     * 否则返回调用方传入的默认值。LunaSettings.getBoolean 返回可空 Boolean，
     * 字段缺失时 LunaLib 记录 ERROR 日志并返回 null，这里同样回落到默认值。
     */
    public static boolean getFullGraphicEffect(boolean fallback) {
        if (!isLunaSettingsUsable()) {
            Global.getLogger(GearInCompatBridge.class).info(
                "GearIn compat: LunaLib unavailable, degrading to fallback=" + fallback);
            return fallback;
        }
        Boolean value = LunaSettings.getBoolean(LUNA_MOD_ID, FULL_GRAPHIC_FIELD);
        if (value == null) {
            Global.getLogger(GearInCompatBridge.class).info(
                "GearIn compat: field [" + FULL_GRAPHIC_FIELD + "] missing in LunaSettings, fallback=" + fallback);
            return fallback;
        }
        return value;
    }
}
