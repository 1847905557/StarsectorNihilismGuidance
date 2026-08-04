package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * 软依赖适配器（兼容层）：把对可选依赖 LunaLib 的全部直接引用收敛在本类，
 * 工程里只有这一处出现 lunalib.* 类；其余代码只消费本类的静态方法。
 *
 * 调用链：isModEnabled 判定 MOD 启用状态，通过才进入持有 LunaSettings 直接
 * 引用的分支；LunaLib 缺失或设置字段缺失时回落到调用方传入的默认值（降级边界）。
 * 类存在性检测属进阶手段（见本节"进阶"小节），默认不需要。
 */
public class GearInCompatBridge {

    /** LunaLib 的 MOD id，与 Starsector\mods\Lunalib\mod_info.json 的 id 字段逐字一致。 */
    public static final String LUNA_MOD_ID = "lunalib";

    /** 本 MOD 在 data/config/LunaSettings.csv 中登记的设置项 fieldID（文件由 C14-S3 定义）。 */
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private GearInCompatBridge() {
    }

    /** 判据：LunaLib 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    /** 可用性判据：MOD 已启用即认为可选 API 可用（类存在性检测属进阶手段，默认不启用）。 */
    public static boolean isLunaSettingsUsable() {
        return isLunaLibEnabled();
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
