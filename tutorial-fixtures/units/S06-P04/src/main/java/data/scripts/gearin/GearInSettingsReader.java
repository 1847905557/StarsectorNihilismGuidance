package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * settings.json 顶层取值（单参 getFloat / getString）。
 *
 * 本页新增的 settings.json 键（对象键合并，MOD 只提交自己的键）：
 * - GearIn_loop_volume（float）、GearIn_service_name（string）。
 * 单参取值缺键抛 RuntimeException（包装 JSONException），不返回 null。
 */
public class GearInSettingsReader {

    public static final String KEY_LOOP_VOLUME = "GearIn_loop_volume";
    public static final String KEY_SERVICE_NAME = "GearIn_service_name";

    private GearInSettingsReader() {
        // 工具类：不实例化
    }

    /** 单参 getFloat：读 settings.json 顶层键；键缺失抛 RuntimeException。 */
    public static float readLoopVolume() {
        return Global.getSettings().getFloat(KEY_LOOP_VOLUME);
    }

    /** 单参 getString：读 settings.json 顶层键；键缺失抛 RuntimeException，不返回 null。 */
    public static String readServiceName() {
        return Global.getSettings().getString(KEY_SERVICE_NAME);
    }
}
