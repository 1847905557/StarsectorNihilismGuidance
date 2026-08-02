package data.scripts.gearin;

import java.io.IOException;

import com.fs.starfarer.api.Global;

/**
 * common 文件读写：<saves>/common/GearIn_services_state.data。
 *
 * 缺失返回空串（含 .data.inprogress 回退）；单次写入上限 1MB（超出抛
 * RuntimeException），且存在跨文件写入总量上限。
 */
public class GearInCommonStateStore {

    public static final String COMMON_FILE = "GearIn_services_state";

    private GearInCommonStateStore() {
        // 工具类：不实例化
    }

    /** common 文件读取：文件缺失返回空串，不抛异常。 */
    public static String readCommonState() throws IOException {
        return Global.getSettings().readTextFileFromCommon(COMMON_FILE);
    }

    /** common 文件写入：单次上限 1MB（超出抛 RuntimeException）。 */
    public static void writeCommonState(String state) throws IOException {
        Global.getSettings().writeTextFileToCommon(COMMON_FILE, state);
    }
}
