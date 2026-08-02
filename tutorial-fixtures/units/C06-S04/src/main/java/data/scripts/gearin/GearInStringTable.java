package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * strings.json 分类文本（双参 getString）。
 *
 * 本节新增的 strings.json 键：分类 GearIn_services -> loop_volume_text。
 * 双参取值缺键不抛异常：返回 "Missing string: [...]" 占位文本并打 ERROR 日志。
 */
public class GearInStringTable {

    public static final String STRINGS_CATEGORY = "GearIn_services";
    public static final String STRINGS_KEY = "loop_volume_text";

    private GearInStringTable() {
        // 工具类：不实例化
    }

    /** 双参 getString：读 strings.json 的 分类->键；缺失不抛异常，返回占位文本并打 ERROR 日志。 */
    public static String getLoopVolumeText() {
        return Global.getSettings().getString(STRINGS_CATEGORY, STRINGS_KEY);
    }
}
